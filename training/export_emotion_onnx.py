import os
import torch
from transformers import ASTForAudioClassification, ASTFeatureExtractor

LORA_DIR = "emotion-stress-classifier"
ONNX_OUTPUT = "emotion_classifier_int8.onnx"

print(f"Loading fine-tuned emotion model from {LORA_DIR}...")
try:
    model = ASTForAudioClassification.from_pretrained(LORA_DIR)
    feature_extractor = ASTFeatureExtractor.from_pretrained(LORA_DIR)
except Exception as e:
    print(f"Error loading model: {e}")
    print("Please make sure you have run train_emotion_classifier.py first!")
    exit(1)

model.eval()

# Create a dummy audio input that matches AST expected input size
# AST expects a 2D spectrogram tensor. The feature extractor handles the conversion from 1D audio to 2D spec.
# Let's create 1 second of dummy audio
print("Preparing dummy input for tracing...")
import numpy as np
dummy_audio = np.random.randn(16000).astype(np.float32)
inputs = feature_extractor(dummy_audio, sampling_rate=16000, return_tensors="pt")
dummy_input = inputs["input_values"]

print(f"Exporting model to ONNX: {ONNX_OUTPUT}...")
# Export the model
with torch.no_grad():
    torch.onnx.export(
        model,
        (dummy_input,),
        ONNX_OUTPUT,
        export_params=True,
        opset_version=14,
        do_constant_folding=True,
        input_names=["input_values"],
        output_names=["logits"],
        dynamic_axes={
            "input_values": {0: "batch_size", 1: "sequence_length", 2: "frequency_bins"},
            "logits": {0: "batch_size"}
        }
    )

print("ONNX Export Successful!")
print("Optional: To fully optimize for Android, run ONNX Runtime quantization on this file to convert it to INT8.")
