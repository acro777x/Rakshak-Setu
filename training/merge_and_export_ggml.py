import os
import torch
from transformers import WhisperForConditionalGeneration, WhisperProcessor
from peft import PeftModel

MODEL_ID = "openai/whisper-tiny"
LORA_DIR = "whisper-tiny-hinglish-lora"
MERGED_DIR = "whisper-tiny-hinglish-merged"

print(f"Loading base model: {MODEL_ID}")
base_model = WhisperForConditionalGeneration.from_pretrained(MODEL_ID)

print(f"Loading LoRA weights from: {LORA_DIR}")
peft_model = PeftModel.from_pretrained(base_model, LORA_DIR)

print("Merging LoRA weights with base model...")
merged_model = peft_model.merge_and_unload()

print(f"Saving merged model to: {MERGED_DIR}")
merged_model.save_pretrained(MERGED_DIR)

# Save the processor as well
processor = WhisperProcessor.from_pretrained(LORA_DIR)
processor.save_pretrained(MERGED_DIR)

print("Model successfully merged!")
print("Next step: Run whisper.cpp's convert-pt-to-ggml.py on the merged directory to create the Android .bin file.")
