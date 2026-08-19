import torch
import torch.nn as nn

print("==================================================")
print("🎧 Training Acoustic Environment Classifier (Mock)")
print("==================================================")

# Classifies if background noise is Home, Street, or Scam Call Center
class AcousticEnvClassifier(nn.Module):
    def __init__(self):
        super(AcousticEnvClassifier, self).__init__()
        # Simple Linear classifier over Mel-Frequency Cepstral Coefficients (MFCC)
        self.fc1 = nn.Linear(40, 16) 
        self.fc2 = nn.Linear(16, 3) # 3 classes
        
    def forward(self, mfcc):
        x = torch.relu(self.fc1(mfcc))
        return torch.softmax(self.fc2(x), dim=-1)

model = AcousticEnvClassifier()
print("Simulating training on environmental sound datasets...")

dummy_input = torch.randn(1, 40)
save_path = "training/acoustic_env.onnx"
torch.onnx.export(model, dummy_input, save_path, 
                  input_names=['mfcc_input'], output_names=['env_probs'])

print(f"✅ Acoustic Environment model saved to {save_path}")
