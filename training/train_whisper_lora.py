import os
import torch
from dataclasses import dataclass
from typing import Any, Dict, List, Union
from datasets import load_dataset, Audio
from transformers import (
    WhisperFeatureExtractor,
    WhisperTokenizer,
    WhisperProcessor,
    WhisperForConditionalGeneration,
    Seq2SeqTrainingArguments,
    Seq2SeqTrainer
)
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
import evaluate

# ==========================================
# 1. Configuration & Setup
# ==========================================
MODEL_ID = "openai/whisper-tiny"
DATASET_ID = "google/fleurs"
LANGUAGE = "hi"
TASK = "transcribe"
OUTPUT_DIR = "whisper-tiny-hinglish-lora"

# Set up device (MPS for Mac, CUDA for Nvidia, fallback CPU)
if torch.backends.mps.is_available():
    device = "mps"
elif torch.cuda.is_available():
    device = "cuda"
else:
    device = "cpu"

print(f"Using device: {device}")

# ==========================================
# 2. Load Processor, Tokenizer, Extractor
# ==========================================
feature_extractor = WhisperFeatureExtractor.from_pretrained(MODEL_ID)
tokenizer = WhisperTokenizer.from_pretrained(MODEL_ID, language="Hindi", task=TASK)
processor = WhisperProcessor.from_pretrained(MODEL_ID, language="Hindi", task=TASK)

# ==========================================
# 3. Load & Preprocess Dataset (Fleurs Hindi)
# ==========================================
print("Loading dataset...")
# Load a very small subset for fast 90-min training proof of concept
dataset = load_dataset(DATASET_ID, "hi_in", split="train[:200]")

# Resample to 16kHz
dataset = dataset.cast_column("audio", Audio(sampling_rate=16000))

def prepare_dataset(batch):
    audio = batch["audio"]
    # compute log-Mel input features
    batch["input_features"] = feature_extractor(audio["array"], sampling_rate=audio["sampling_rate"]).input_features[0]
    # encode target text to label ids
    batch["labels"] = tokenizer(batch["raw_transcription"]).input_ids
    return batch

print("Preprocessing dataset...")
encoded_dataset = dataset.map(prepare_dataset, remove_columns=dataset.column_names, num_proc=1)

# ==========================================
# 4. Data Collator
# ==========================================
@dataclass
class DataCollatorSpeechSeq2SeqWithPadding:
    processor: Any
    def __call__(self, features: List[Dict[str, Union[List[int], torch.Tensor]]]) -> Dict[str, torch.Tensor]:
        input_features = [{"input_features": feature["input_features"]} for feature in features]
        batch = self.processor.feature_extractor.pad(input_features, return_tensors="pt")

        label_features = [{"input_ids": feature["labels"]} for feature in features]
        labels_batch = self.processor.tokenizer.pad(label_features, return_tensors="pt")

        labels = labels_batch["input_ids"].masked_fill(labels_batch.attention_mask.ne(1), -100)
        # remove bos token if present
        if (labels[:, 0] == self.processor.tokenizer.bos_token_id).all().cpu().item():
            labels = labels[:, 1:]

        batch["labels"] = labels
        return batch

data_collator = DataCollatorSpeechSeq2SeqWithPadding(processor=processor)

# ==========================================
# 5. Model Loading & PEFT (LoRA) Setup
# ==========================================
print("Loading base model...")
model = WhisperForConditionalGeneration.from_pretrained(MODEL_ID)

# Disable caching for gradient checkpointing
model.config.use_cache = False
model.generate = lambda *args, **kwargs: super(WhisperForConditionalGeneration, model).generate(*args, **kwargs, use_cache=True)

# Define LoRA config targeting attention blocks
config = LoraConfig(
    r=8,
    lora_alpha=16,
    target_modules=["q_proj", "v_proj"],
    lora_dropout=0.05,
    bias="none",
)
model = get_peft_model(model, config)
model.print_trainable_parameters()

# Move model to device
model.to(device)

# ==========================================
# 6. Training Arguments & Trainer
# ==========================================
training_args = Seq2SeqTrainingArguments(
    output_dir=OUTPUT_DIR,
    per_device_train_batch_size=8,
    gradient_accumulation_steps=1,
    learning_rate=1e-3,
    warmup_steps=10,
    max_steps=50, # Extremely short run just to prove pipeline works locally in <90 mins
    gradient_checkpointing=True,
    fp16=False, # MPS does not fully support mixed precision fp16 the same way CUDA does, keeping standard float32
    evaluation_strategy="no",
    save_strategy="no",
    save_total_limit=1,
    predict_with_generate=True,
    generation_max_length=225,
    logging_steps=10,
    report_to=["none"],
    remove_unused_columns=False,
    label_names=["labels"]
)

trainer = Seq2SeqTrainer(
    args=training_args,
    model=model,
    train_dataset=encoded_dataset,
    data_collator=data_collator,
    tokenizer=processor.feature_extractor,
)

# ==========================================
# 7. Start Training & Save
# ==========================================
print("Starting training...")
trainer.train()

print("Saving final model...")
model.save_pretrained(OUTPUT_DIR)
processor.save_pretrained(OUTPUT_DIR)
print(f"Training complete. LoRA adapter saved to {OUTPUT_DIR}")
