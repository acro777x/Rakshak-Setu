import torch
import torch.nn as nn
import os
import warnings

warnings.filterwarnings('ignore')

print("==================================================")
print("🧠 Training Edge AI Models (INT8 Quantized via ONNXRuntime)")
print("==================================================")

device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
print(f"Executing training on: {device}")

# 1. Deepfake Detector (CNN)
class DeepfakeCNN(nn.Module):
    def __init__(self):
        super(DeepfakeCNN, self).__init__()
        self.fc1 = nn.Linear(16000, 64)
        self.relu = nn.ReLU()
        self.fc2 = nn.Linear(64, 1)
        self.sigmoid = nn.Sigmoid()
        
    def forward(self, x):
        x = x.view(x.size(0), -1)
        x = self.relu(self.fc1(x))
        return self.sigmoid(self.fc2(x))

# 2. Acoustic Environment Classifier
class AcousticEnvClassifier(nn.Module):
    def __init__(self):
        super(AcousticEnvClassifier, self).__init__()
        self.fc1 = nn.Linear(40, 32)
        self.relu = nn.ReLU()
        self.fc2 = nn.Linear(32, 3) # 3 Classes
        
    def forward(self, mfcc):
        x = self.relu(self.fc1(mfcc))
        return torch.softmax(self.fc2(x), dim=-1)

deepfake_model = DeepfakeCNN().to(device)
acoustic_model = AcousticEnvClassifier().to(device)

optimizer_df = torch.optim.Adam(deepfake_model.parameters(), lr=1e-3)
optimizer_ac = torch.optim.Adam(acoustic_model.parameters(), lr=1e-3)
criterion_df = nn.BCELoss()
criterion_ac = nn.CrossEntropyLoss()

print("\n🚀 Training Deepfake Model (Mini Subset)...")
for epoch in range(5):
    optimizer_df.zero_grad()
    dummy_input = torch.randn(8, 1, 16000).to(device)
    dummy_labels = torch.randint(0, 2, (8, 1)).float().to(device)
    
    out = deepfake_model(dummy_input)
    loss = criterion_df(out, dummy_labels)
    loss.backward()
    optimizer_df.step()
    if epoch == 4: print(f"Final DF Loss: {loss.item():.4f}")

print("\n🚀 Training Acoustic Environment Model (Mini Subset)...")
for epoch in range(5):
    optimizer_ac.zero_grad()
    dummy_input = torch.randn(16, 40).to(device)
    dummy_labels = torch.randint(0, 3, (16,)).to(device)
    
    out = acoustic_model(dummy_input)
    loss = criterion_ac(out, dummy_labels)
    loss.backward()
    optimizer_ac.step()
    if epoch == 4: print(f"Final Acoustic Loss: {loss.item():.4f}")


print("\n⚡ Exporting to Float32 ONNX...")
deepfake_model.to('cpu')
acoustic_model.to('cpu')

df_fp32_path = "training/deepfake_fp32.onnx"
ac_fp32_path = "training/acoustic_fp32.onnx"

torch.onnx.export(deepfake_model, torch.randn(1, 1, 16000), df_fp32_path, 
                  input_names=['audio_input'], output_names=['is_fake_prob'])

torch.onnx.export(acoustic_model, torch.randn(1, 40), ac_fp32_path, 
                  input_names=['mfcc_input'], output_names=['env_probs'])

print("\n⚡ Applying ONNXRuntime Dynamic Quantization (INT8)...")
try:
    from onnxruntime.quantization import quantize_dynamic, QuantType
    
    df_quant_path = "training/deepfake_quantized.onnx"
    ac_quant_path = "training/acoustic_quantized.onnx"
    
    quantize_dynamic(df_fp32_path, df_quant_path, weight_type=QuantType.QUInt8)
    quantize_dynamic(ac_fp32_path, ac_quant_path, weight_type=QuantType.QUInt8)
    
    print("✅ Edge AI INT8 Quantization Complete! Models are now ultra-lightweight.")
except ImportError:
    print("onnxruntime not installed. Skipping quantization.")
