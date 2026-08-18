import torch
import torch.nn as nn
from captum.attr import IntegratedGradients
from datasets import load_dataset
from collections import Counter
import re

print("=======================================")
print("🧠 Explainable AI (XAI) - Spam Intent Analysis")
print("=======================================")

# 1. Rebuild Vocabulary (Deterministic from dataset)
print("Loading dataset to rebuild vocabulary...")
dataset = load_dataset("SetFit/enron_spam")
train_data = dataset["train"]["text"]

def clean_text(text):
    text = text.lower()
    text = re.sub(r'[^a-z0-9\s]', '', text)
    return text.split()

word_counts = Counter()
for text in train_data:
    word_counts.update(clean_text(text))

vocab = {word: i + 2 for i, (word, _) in enumerate(word_counts.most_common(5000))}
vocab["<PAD>"] = 0
vocab["<UNK>"] = 1
idx_to_word = {v: k for k, v in vocab.items()}

# 2. Define Model
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
        hidden_cat = torch.cat((hidden[-2,:,:], hidden[-1,:,:]), dim=1)
        out = self.relu(self.fc1(hidden_cat))
        return self.sigmoid(self.fc2(out)).squeeze()

VOCAB_SIZE = 5002
EMBED_DIM = 100
HIDDEN_DIM = 64
OUTPUT_DIM = 1

model = BiLSTMClassifier(VOCAB_SIZE, EMBED_DIM, HIDDEN_DIM, OUTPUT_DIM)
model.load_state_dict(torch.load("hackathon_bilstm_model.pt", map_location='cpu'))
model.eval()

# Helper for wrapping model to accept embeddings directly for Captum
class EmbeddingWrapper(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model
    def forward(self, embedded):
        _, (hidden, _) = self.model.lstm(embedded)
        hidden_cat = torch.cat((hidden[-2,:,:], hidden[-1,:,:]), dim=1)
        out = self.model.relu(self.model.fc1(hidden_cat))
        return self.model.sigmoid(self.model.fc2(out))

wrapped_model = EmbeddingWrapper(model)
ig = IntegratedGradients(wrapped_model)

# 3. Test on a Scam Message
sample_message = "urgent please send the bank account password and otp immediately to unlock your funds"
print(f"\n📩 Analyzing Message: '{sample_message}'\n")

tokens = clean_text(sample_message)
encoded = [vocab.get(w, vocab["<UNK>"]) for w in tokens]
input_tensor = torch.tensor([encoded], dtype=torch.long)

# Get the prediction
with torch.no_grad():
    prob = model(input_tensor).item()
print(f"🚨 Scam Probability: {prob*100:.2f}%")

# 4. Explain with Captum
input_embeddings = model.embedding(input_tensor)
input_embeddings.requires_grad_()

# Baseline (zeros)
baseline = torch.zeros_like(input_embeddings)

attributions, delta = ig.attribute(input_embeddings, baseline, return_convergence_delta=True)
# Summarize attributions per word by summing over embedding dimension
attributions_sum = attributions.sum(dim=2).squeeze().detach().numpy()

print("\n🔍 Word-level Scam Intent Breakdown:")
for word, attr in zip(tokens, attributions_sum):
    if attr > 0:
        print(f"   🔴 +{attr:.3f} : {word} (Increases Scam Probability)")
    else:
        print(f"   🟢 {attr:.3f} : {word} (Safe / Neutral)")

print("\n💡 Hackathon Note: Judges love this! It proves your model isn't a black box.")
