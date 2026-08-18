import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, Dataset
import numpy as np
from datasets import load_dataset
from collections import Counter
import re

print("=======================================")
print("🏆 Hackathon AI: Custom From-Scratch Model")
print("=======================================")

# Device setup
if torch.backends.mps.is_available():
    device = torch.device("mps")
elif torch.cuda.is_available():
    device = torch.device("cuda")
else:
    device = torch.device("cpu")
print(f"Using device: {device}\n")

# ==========================================
# 1. Dataset Loading & Preprocessing
# ==========================================
print("Loading Public Enron Spam Dataset (30k+ records)...")
dataset = load_dataset("SetFit/enron_spam")
train_data = dataset["train"]["text"]
train_labels = dataset["train"]["label"]

# Tokenization and Vocabulary building (No Pre-trained Tokenizers)
def clean_text(text):
    text = text.lower()
    text = re.sub(r'[^a-z0-9\s]', '', text)
    return text.split()

print("Building Custom Vocabulary from scratch...")
word_counts = Counter()
for text in train_data:
    word_counts.update(clean_text(text))

# Keep top 5000 words
vocab = {word: i + 2 for i, (word, _) in enumerate(word_counts.most_common(5000))}
vocab["<PAD>"] = 0
vocab["<UNK>"] = 1

def encode_text(text, max_len=50):
    tokens = clean_text(text)
    encoded = [vocab.get(word, vocab["<UNK>"]) for word in tokens]
    if len(encoded) < max_len:
        encoded += [vocab["<PAD>"]] * (max_len - len(encoded))
    else:
        encoded = encoded[:max_len]
    return encoded

class ScamDataset(Dataset):
    def __init__(self, texts, labels):
        self.texts = [encode_text(t) for t in texts]
        self.labels = labels

    def __len__(self):
        return len(self.labels)

    def __getitem__(self, idx):
        return torch.tensor(self.texts[idx]), torch.tensor(self.labels[idx], dtype=torch.float32)

# Split 80/20 manually
split_idx = int(len(train_data) * 0.8)
train_dataset = ScamDataset(train_data[:split_idx], train_labels[:split_idx])
test_dataset = ScamDataset(train_data[split_idx:], train_labels[split_idx:])

train_loader = DataLoader(train_dataset, batch_size=64, shuffle=True)
test_loader = DataLoader(test_dataset, batch_size=64, shuffle=False)

# ==========================================
# 2. Custom Neural Network Architecture
# ==========================================
class BiLSTMClassifier(nn.Module):
    def __init__(self, vocab_size, embed_dim, hidden_dim, output_dim):
        super(BiLSTMClassifier, self).__init__()
        self.embedding = nn.Embedding(vocab_size, embed_dim, padding_idx=0)
        self.lstm = nn.LSTM(embed_dim, hidden_dim, bidirectional=True, batch_first=True)
        self.fc1 = nn.Linear(hidden_dim * 2, 32)
        self.relu = nn.ReLU()
        self.fc2 = nn.Linear(32, output_dim)
        self.sigmoid = nn.Sigmoid()

    def forward(self, text):
        embedded = self.embedding(text)
        _, (hidden, _) = self.lstm(embedded)
        # Concat the final forward and backward hidden states
        hidden_cat = torch.cat((hidden[-2,:,:], hidden[-1,:,:]), dim=1)
        out = self.relu(self.fc1(hidden_cat))
        return self.sigmoid(self.fc2(out)).squeeze()

print("\nInitializing Custom BiLSTM Architecture...")
model = BiLSTMClassifier(vocab_size=len(vocab), embed_dim=100, hidden_dim=64, output_dim=1)
model = model.to(device)

criterion = nn.BCELoss()
optimizer = optim.Adam(model.parameters(), lr=0.001)

# ==========================================
# 3. Custom Training Loop (No Shortcuts)
# ==========================================
print("\n🔥 Starting Custom Training Loop (From Scratch)...")
epochs = 5

for epoch in range(epochs):
    model.train()
    total_loss = 0
    correct = 0
    total = 0
    
    for texts, labels in train_loader:
        texts, labels = texts.to(device), labels.to(device)
        
        optimizer.zero_grad()
        predictions = model(texts)
        loss = criterion(predictions, labels)
        
        loss.backward()
        optimizer.step()
        
        total_loss += loss.item()
        
        # Calculate training accuracy
        predicted_classes = (predictions > 0.5).float()
        correct += (predicted_classes == labels).sum().item()
        total += labels.size(0)
        
    train_acc = 100 * correct / total
    print(f"Epoch {epoch+1}/{epochs} | Loss: {total_loss/len(train_loader):.4f} | Train Acc: {train_acc:.2f}%")

# ==========================================
# 4. Evaluation
# ==========================================
print("\n🧪 Evaluating Custom Model on Test Set...")
model.eval()
correct = 0
total = 0
true_positives = 0
predicted_positives = 0
actual_positives = 0

with torch.no_grad():
    for texts, labels in test_loader:
        texts, labels = texts.to(device), labels.to(device)
        predictions = model(texts)
        predicted_classes = (predictions > 0.5).float()
        
        correct += (predicted_classes == labels).sum().item()
        total += labels.size(0)
        
        # For Precision/Recall logic
        true_positives += ((predicted_classes == 1) & (labels == 1)).sum().item()
        predicted_positives += (predicted_classes == 1).sum().item()
        actual_positives += (labels == 1).sum().item()

accuracy = 100 * correct / total
precision = true_positives / (predicted_positives + 1e-8)
recall = true_positives / (actual_positives + 1e-8)
f1 = 2 * (precision * recall) / (precision + recall + 1e-8)

print("=======================================")
print(f"🏆 Final Hackathon Model Metrics")
print(f"Accuracy : {accuracy:.2f}%")
print(f"Precision: {precision*100:.2f}%")
print(f"Recall   : {recall*100:.2f}%")
print(f"F1 Score : {f1*100:.2f}%")
print("=======================================")

# Save Model
torch.save(model.state_dict(), "hackathon_bilstm_model.pt")
print("Model successfully saved as 'hackathon_bilstm_model.pt'")
