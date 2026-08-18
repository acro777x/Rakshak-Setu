import os
import torch
import numpy as np
from datasets import load_dataset, Audio
from transformers import (
    ASTFeatureExtractor,
    ASTForAudioClassification,
    TrainingArguments,
    Trainer
)
import evaluate

# ==========================================
# 1. Configuration
# ==========================================
MODEL_ID = "MIT/ast-finetuned-audioset-10-10-0.4593"
DATASET_ID = "crema-d" # Note: In a real scenario, we might need a custom mapping or a direct huggingface hub dataset like 'xhaida/CREMA-D'
OUTPUT_DIR = "emotion-stress-classifier"
NUM_LABELS = 2
LABEL_MAPPING = {"Neutral": 0, "High-Stress": 1}

# Set up device
if torch.backends.mps.is_available():
    device = "mps"
elif torch.cuda.is_available():
    device = "cuda"
else:
    device = "cpu"
print(f"Using device: {device}")

# ==========================================
# 2. Load Feature Extractor
# ==========================================
feature_extractor = ASTFeatureExtractor.from_pretrained(MODEL_ID)

# ==========================================
# 3. Dummy Dataset (For 90-min Sprint Demo)
# ==========================================
# Because CREMA-D might be gated or large, we simulate the dataset loading
# In production, replace this with `load_dataset("some_emotion_dataset")`
print("Loading emotion dataset...")
def generate_dummy_data(num_samples=100):
    import datasets
    def gen():
        for _ in range(num_samples):
            # 1 second of random noise simulating speech at 16kHz
            audio_array = np.random.randn(16000).astype(np.float32) 
            label = np.random.randint(0, 2)
            yield {"audio": {"array": audio_array, "sampling_rate": 16000}, "label": label}
    return datasets.Dataset.from_generator(gen)

dataset = generate_dummy_data(100) # Small subset for speed

def preprocess_function(examples):
    audio_arrays = [x["array"] for x in examples["audio"]]
    inputs = feature_extractor(audio_arrays, sampling_rate=16000, return_tensors="pt")
    # Convert labels to tensor
    inputs["labels"] = examples["label"]
    return inputs

print("Preprocessing dataset...")
encoded_dataset = dataset.map(preprocess_function, batched=True, remove_columns=["audio"])

# Split into train/val
split = encoded_dataset.train_test_split(test_size=0.2)
train_dataset = split["train"]
eval_dataset = split["test"]

# ==========================================
# 4. Model Loading
# ==========================================
print("Loading AST Base Model...")
# We ignore mismatched sizes because we are changing the classification head from AudioSet (527 classes) to our 2 classes
model = ASTForAudioClassification.from_pretrained(
    MODEL_ID, 
    num_labels=NUM_LABELS,
    ignore_mismatched_sizes=True
)
model.to(device)

# ==========================================
# 5. Training Setup
# ==========================================
accuracy_metric = evaluate.load("accuracy")

def compute_metrics(eval_pred):
    predictions = np.argmax(eval_pred.predictions, axis=1)
    return accuracy_metric.compute(predictions=predictions, references=eval_pred.label_ids)

training_args = TrainingArguments(
    output_dir=OUTPUT_DIR,
    evaluation_strategy="epoch",
    save_strategy="epoch",
    learning_rate=3e-5,
    per_device_train_batch_size=8,
    gradient_accumulation_steps=2,
    per_device_eval_batch_size=8,
    num_train_epochs=3, # Fast demo training
    warmup_ratio=0.1,
    logging_steps=5,
    load_best_model_at_end=True,
    metric_for_best_model="accuracy",
    report_to=["none"]
)

trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=train_dataset,
    eval_dataset=eval_dataset,
    tokenizer=feature_extractor,
    compute_metrics=compute_metrics,
)

# ==========================================
# 6. Start Training
# ==========================================
print("Starting Emotion Classifier training...")
trainer.train()

print("Saving fine-tuned emotion model...")
trainer.save_model(OUTPUT_DIR)
print(f"Model successfully saved to {OUTPUT_DIR}")
