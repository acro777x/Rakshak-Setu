import os
import urllib.request
import zipfile
import pandas as pd
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score
import time

print("=======================================")
print("🚀 Training BiLSTM Model on Mac GPU (MPS)")
print("=======================================")

# 1. DOWNLOAD DATASET DIRECTLY (Bypassing Kaggle Auth for automation)
# We are downloading the exact SMS Spam Collection Dataset used on Kaggle from the UCI repository
data_url = "https://archive.ics.uci.edu/ml/machine-learning-databases/00228/smsspamcollection.zip"
data_zip = "training/smsspamcollection.zip"
data_csv = "training/SMSSpamCollection"

if not os.path.exists(data_csv):
    print("Downloading dataset...")
    urllib.request.urlretrieve(data_url, data_zip)
    with zipfile.ZipFile(data_zip, 'r') as zip_ref:
        zip_ref.extractall("training/")
    print("Dataset downloaded and extracted.")

# 2. LOAD DATASET
print("Loading dataset...")
df = pd.read_csv(data_csv, sep='\t', names=['label', 'text'])
df['label'] = df['label'].map({'ham': 0, 'spam': 1})

texts = df['text'].values
labels = df['label'].values

# 3. TEXT PREPROCESSING & TOKENIZATION
vocab = {}
def tokenize(text):
    words = text.lower().split()
    tokens = []
    for w in words:
        if w not in vocab:
            vocab[w] = len(vocab) + 1 # 0 is for padding
        tokens.append(vocab[w])
    return tokens

print("Tokenizing...")
sequences = [tokenize(str(t)) for t in texts]
max_len = 50
padded_seqs = np.zeros((len(sequences), max_len), dtype=int)
for i, seq in enumerate(sequences):
    length = min(len(seq), max_len)
    padded_seqs[i, :length] = seq[:length]

X_train, X_test, y_train, y_test = train_test_split(padded_seqs, labels, test_size=0.2, random_state=42)

train_data = TensorDataset(torch.tensor(X_train), torch.tensor(y_train, dtype=torch.float32))
test_data = TensorDataset(torch.tensor(X_test), torch.tensor(y_test, dtype=torch.float32))

train_loader = DataLoader(train_data, batch_size=64, shuffle=True)
test_loader = DataLoader(test_data, batch_size=64)

# 4. DEFINE BiLSTM MODEL (As per research paper)
class BiLSTMClassifier(nn.Module):
    def __init__(self, vocab_size, embed_dim, hidden_dim):
        super(BiLSTMClassifier, self).__init__()
        self.embedding = nn.Embedding(vocab_size, embed_dim, padding_idx=0)
        self.lstm = nn.LSTM(embed_dim, hidden_dim, batch_first=True, bidirectional=True)
        self.fc = nn.Linear(hidden_dim * 2, 1)
        self.sigmoid = nn.Sigmoid()

    def forward(self, x):
        embedded = self.embedding(x)
        lstm_out, (hidden, cell) = self.lstm(embedded)
        hidden_cat = torch.cat((hidden[-2,:,:], hidden[-1,:,:]), dim=1)
        out = self.fc(hidden_cat)
        return self.sigmoid(out)

# 5. INITIALIZE DEVICE (Mac GPU - MPS)
if torch.backends.mps.is_available():
    device = torch.device('mps')
    print("✅ Mac GPU (MPS) is available and will be used.")
elif torch.cuda.is_available():
    device = torch.device('cuda')
    print("✅ CUDA GPU is available.")
else:
    device = torch.device('cpu')
    print("⚠️ GPU not found. Using CPU.")

vocab_size = len(vocab) + 1
model = BiLSTMClassifier(vocab_size, 64, 128).to(device)
criterion = nn.BCELoss()
optimizer = torch.optim.Adam(model.parameters(), lr=0.001)

# 6. TRAIN MODEL
epochs = 5
print(f"\nStarting training for {epochs} epochs...")
for epoch in range(epochs):
    model.train()
    total_loss = 0
    start_time = time.time()
    for inputs, targets in train_loader:
        inputs, targets = inputs.to(device), targets.to(device)
        optimizer.zero_grad()
        outputs = model(inputs).squeeze()
        loss = criterion(outputs, targets)
        loss.backward()
        optimizer.step()
        total_loss += loss.item()
    elapsed_time = time.time() - start_time
    print(f"Epoch {epoch+1}/{epochs} | Loss: {total_loss/len(train_loader):.4f} | Time: {elapsed_time:.2f}s")

# 7. EVALUATE
print("\nEvaluating model...")
model.eval()
all_preds, all_targets = [], []
with torch.no_grad():
    for inputs, targets in test_loader:
        inputs = inputs.to(device)
        outputs = model(inputs).squeeze()
        preds = (outputs >= 0.5).cpu().numpy()
        all_preds.extend(preds)
        all_targets.extend(targets.numpy())

print("\nValidation Accuracy:", accuracy_score(all_targets, all_preds))
print("Classification Report:\n", classification_report(all_targets, all_preds))

# 8. SAVE MODEL (PyTorch and ONNX)
print("\nSaving models...")
torch.save(model.state_dict(), 'training/kaggle_bilstm.pt')

dummy_input = torch.zeros(1, max_len, dtype=torch.long).to(device)
torch.onnx.export(model, dummy_input, "training/kaggle_bilstm.onnx", 
                  input_names=['input'], output_names=['output'],
                  dynamic_axes={'input': {0: 'batch_size'}, 'output': {0: 'batch_size'}})

print("✅ Models saved as 'training/kaggle_bilstm.pt' and 'training/kaggle_bilstm.onnx'!")
