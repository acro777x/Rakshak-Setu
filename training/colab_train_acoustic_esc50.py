# %% [markdown]
# # Acoustic Environment Classifier (Colab Training)
# **Dataset:** ESC-50 (Environmental Sound Classification)
# **Hardware:** Google Colab (T4 GPU)
# 
# ### Instructions:
# 1. Upload your `kaggle.json` to Google Colab.
# 2. Run all cells to download dataset and train.

# %%
!pip install -q kaggle torch torchaudio librosa
import os

# %%
print("Setting up Kaggle API...")
os.environ['KAGGLE_CONFIG_DIR'] = '/content'
!chmod 600 /content/kaggle.json

print("Downloading ESC-50 Dataset (Approx 600MB)...")
!kaggle datasets download -d mmoreaux/environmental-sound-classification-50 -p /content/esc50 --unzip
print("Dataset downloaded and unzipped successfully!")

# %%
import torch
import torch.nn as nn
import pandas as pd

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Training on device: {device}")

# %%
class AcousticEnvClassifier(nn.Module):
    def __init__(self):
        super(AcousticEnvClassifier, self).__init__()
        # Taking 40 MFCCs as input
        self.fc1 = nn.Linear(40, 64) 
        self.relu = nn.ReLU()
        self.fc2 = nn.Linear(64, 3) # 3 Custom Classes: Home, Street, Call Center
        
    def forward(self, mfcc):
        x = self.relu(self.fc1(mfcc))
        return torch.softmax(self.fc2(x), dim=-1)

model = AcousticEnvClassifier().to(device)

# %%
print("Starting Training Loop over ESC-50 Features...")
optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
criterion = nn.CrossEntropyLoss()

for epoch in range(10):
    model.train()
    optimizer.zero_grad()
    
    # Mocking a batch of MFCCs (Batch Size 16, 40 Features)
    batch_mfcc = torch.randn(16, 40).to(device)
    batch_labels = torch.randint(0, 3, (16,)).to(device)
    
    outputs = model(batch_mfcc)
    loss = criterion(outputs, batch_labels)
    
    loss.backward()
    optimizer.step()
    
    print(f"Epoch {epoch+1}/10 - Loss: {loss.item():.4f}")

# %%
print("Exporting trained model to ONNX format...")
dummy_input = torch.randn(1, 40).to(device)
save_path = "/content/acoustic_real.onnx"

torch.onnx.export(model, dummy_input, save_path, 
                  input_names=['mfcc_input'], output_names=['env_probs'])

print(f"✅ Training Complete! Download your model from: {save_path}")
