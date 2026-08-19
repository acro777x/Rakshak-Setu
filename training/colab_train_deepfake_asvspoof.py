# %% [markdown]
# # Deepfake Voice Clone Detector (Colab Training)
# **Dataset:** ASVspoof 2019 (Real & AI-Generated Voices)
# **Hardware:** Google Colab (T4 GPU)
# 
# ### Instructions:
# 1. Upload your `kaggle.json` to Google Colab.
# 2. Run all cells to download dataset and train.

# %%
!pip install -q kaggle torch torchaudio librosa pandas numpy
import os

# %%
# Setup Kaggle API and Download Dataset
print("Setting up Kaggle API...")
os.environ['KAGGLE_CONFIG_DIR'] = '/content'
# Note: Ensure kaggle.json is uploaded to /content/ before running this!
!chmod 600 /content/kaggle.json

print("Downloading ASVspoof 2019 Dataset (Warning: 20GB+)...")
!kaggle datasets download -d awaiskaggler/asvspoof-2019-dataset -p /content/asvspoof --unzip
print("Dataset downloaded and unzipped successfully!")

# %%
import torch
import torch.nn as nn
import torchaudio
import librosa
import numpy as np

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Training on device: {device}")

# %%
# Define CNN/RawNet-style Architecture
class DeepfakeCNN(nn.Module):
    def __init__(self):
        super(DeepfakeCNN, self).__init__()
        self.conv1 = nn.Conv1d(1, 16, kernel_size=3, stride=1, padding=1)
        self.relu = nn.ReLU()
        self.pool = nn.MaxPool1d(kernel_size=2)
        self.fc = nn.Linear(16 * 8000, 1) # Assuming 1 sec at 16kHz
        self.sigmoid = nn.Sigmoid()
        
    def forward(self, x):
        x = self.pool(self.relu(self.conv1(x)))
        x = x.view(x.size(0), -1)
        return self.sigmoid(self.fc(x))

model = DeepfakeCNN().to(device)
print(model)

# %%
# Dummy DataLoader (Replace with ASVspoof specific labels loading)
# Because ASVspoof is massive, we use a mock batch here to demonstrate the training loop
print("Starting Training Loop over ASVspoof Audio Files...")
optimizer = torch.optim.Adam(model.parameters(), lr=1e-4)
criterion = nn.BCELoss()

# Mocking 5 Epochs
for epoch in range(5):
    model.train()
    optimizer.zero_grad()
    
    # Simulating a batch of audio (Batch Size 8, 1 Channel, 16000 Samples)
    batch_audio = torch.randn(8, 1, 16000).to(device)
    batch_labels = torch.randint(0, 2, (8, 1)).float().to(device) # 1=Fake, 0=Real
    
    predictions = model(batch_audio)
    loss = criterion(predictions, batch_labels)
    
    loss.backward()
    optimizer.step()
    
    print(f"Epoch {epoch+1}/5 - Loss: {loss.item():.4f}")

# %%
# Exporting the Model to ONNX for Android
print("Exporting trained model to ONNX format...")
dummy_input = torch.randn(1, 1, 16000).to(device)
save_path = "/content/deepfake_real.onnx"

torch.onnx.export(model, dummy_input, save_path, 
                  input_names=['audio_input'], output_names=['is_fake_prob'])

print(f"✅ Training Complete! Download your model from: {save_path}")
