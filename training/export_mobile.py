import torch
import torch.nn as nn

print("=======================================")
print("📱 Exporting Hackathon Model for Mobile")
print("=======================================")

# Re-define the custom architecture
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

# Vocab size was 5002 (5000 top words + PAD + UNK)
VOCAB_SIZE = 5002
EMBED_DIM = 100
HIDDEN_DIM = 64
OUTPUT_DIM = 1

model = BiLSTMClassifier(VOCAB_SIZE, EMBED_DIM, HIDDEN_DIM, OUTPUT_DIM)

# Load the trained weights
print("Loading weights from 'hackathon_bilstm_model.pt'...")
# Map location to CPU to ensure it exports safely for any device
model.load_state_dict(torch.load("hackathon_bilstm_model.pt", map_location=torch.device('cpu')))
model.eval()

# Create dummy input: batch size 1, sequence length 50
dummy_input = torch.randint(0, VOCAB_SIZE, (1, 50), dtype=torch.long)

# 1. Export to PyTorch Mobile (TorchScript Lite)
print("\n[1/2] Tracing model to TorchScript (.ptl) for PyTorch Mobile Android...")
from torch.utils.mobile_optimizer import optimize_for_mobile

traced_script_module = torch.jit.trace(model, dummy_input)
traced_script_module.save("hackathon_bilstm_mobile.pt")
print("✅ Saved to 'hackathon_bilstm_mobile.pt'")

# 2. Export to ONNX
print("\n[2/2] Exporting model to ONNX (.onnx) format...")
torch.onnx.export(
    model, 
    dummy_input, 
    "hackathon_bilstm.onnx",
    export_params=True,
    opset_version=14,
    do_constant_folding=True,
    input_names=['input_ids'],
    output_names=['scam_probability'],
    dynamic_axes={
        'input_ids': {0: 'batch_size', 1: 'sequence_length'},
        'scam_probability': {0: 'batch_size'}
    }
)
print("✅ Saved to 'hackathon_bilstm.onnx'")
print("\n🎉 Mobile export complete! Android team can now load the .ptl or .onnx file directly.")
