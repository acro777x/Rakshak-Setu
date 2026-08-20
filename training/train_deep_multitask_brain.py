# Deep Multi-Task Scam Intent & Vector Neural Network (Rakshak DeepBrain)
import os
import json
import time
import re
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, f1_score

print("=" * 70)
print("Training Rakshak DeepBrain: Multi-Head Attention BiLSTM on Mac GPU")
print("=" * 70)

device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
print(f"Using Compute Device: {device}")

# Category Mappings
CATEGORY_MAP = {
    "Benign": 0,
    "Digital_Arrest": 1,
    "KYC_Fraud": 2,
    "Courier_Customs": 3,
    "Tech_Support": 4,
    "Lottery_Refund": 5,
    "Family_Emergency": 6,
    "Job_Task_Scam": 7,
    "UPI_QR_Scam": 8
}
ID_TO_CAT = {v: k for k, v in CATEGORY_MAP.items()}

texts = []
scam_labels = []
cat_labels = []

# Load SMS Spam Collection
sms_path = "training/SMSSpamCollection"
if os.path.exists(sms_path):
    df_sms = pd.read_csv(sms_path, sep="\t", names=["label", "text"])
    for _, row in df_sms.iterrows():
        t = str(row["text"]).strip()
        is_s = 1 if row["label"] == "spam" else 0
        texts.append(t)
        scam_labels.append(is_s)
        cat_labels.append(5 if is_s == 1 else 0)
    print(f"Loaded {len(df_sms)} SMS records.")

# Load Attack Patterns
atk_path = "training/attack_patterns.json"
if os.path.exists(atk_path):
    with open(atk_path, "r") as f:
        atk_data = json.load(f)
    for item in atk_data:
        t = str(item["text"]).strip()
        lbl = item["label"]
        cat_idx = 1
        if "Tech" in lbl: cat_idx = 4
        elif "Greed" in lbl or "Lottery" in lbl: cat_idx = 5
        elif "Urgency" in lbl: cat_idx = 2
        elif "Authority" in lbl: cat_idx = 1
        texts.append(t)
        scam_labels.append(1)
        cat_labels.append(cat_idx)
    print(f"Loaded {len(atk_data)} attack pattern records.")

# Indian Hinglish & English Conversational Scam Scripts
indian_scam_templates = [
    # Digital Arrest
    ("CBI officer inspector Sharma speaking aapka Aadhaar card money laundering crime mein use hua hai supreme court arrest warrant issued video call par digital arrest ho", 1, 1),
    ("Narcotics Control Bureau Mumbai customs mein aapke parcel se drugs pakda gaya hai arrest se bachne ke liye security bond jama karo", 1, 1),
    ("Cyber crime cell Delhi headquarter se bol raha hoon aapke naam par 14 bank account khule hain hawala transaction detect hua hai", 1, 1),
    ("Supreme Court judge bench ne aapke against arrest order pass kiya hai digital arrest disconnect mat karo warna police ghar bhej rahe hain", 1, 1),
    ("Telecom regulatory authority TRAI aapka mobile number illegal blackmailing ke liye use ho raha hai 2 ghante mein sim block arrest warrant", 1, 1),
    
    # KYC Fraud
    ("SBI YONO account update mandatory request aapka debit card block ho jayega turant pan card aadhar update link par click karo", 1, 2),
    ("HDFC bank notification your netbanking has been suspended due to pending KYC verification click to unblock immediately", 1, 2),
    ("Paytm wallet KYC expired today your balance will be frozen send OTP to reactivate wallet immediately", 1, 2),
    ("ICICI bank alert suspicious transaction detected your account is on hold verify credentials to secure funds", 1, 2),
    ("Axis bank KYC compliance update required as per RBI guidelines submit your aadhar card number and biometric OTP", 1, 2),
    
    # Courier / Customs
    ("FedEx international courier parcel detained at Mumbai airport customs duty and clearing charges pending pay now", 1, 3),
    ("DHL courier shipment from Thailand seized containing illegal passports and contraband transfer clearance penalty", 1, 3),
    ("Customs office notice your overseas parcel has gold bullion exceeding limit transfer tax to custom officer account", 1, 3),
    ("India Post speed post parcel delivery failed address mismatch update address and pay 5 rupees redelivery fee", 1, 3),
    
    # Tech Support / AnyDesk
    ("Microsoft security alert your computer Windows defender found trojan virus download AnyDesk app for engineer to fix", 1, 4),
    ("Google security team someone accessed your Gmail account from Russia download QuickSupport to scan device", 1, 4),
    ("Router security compromised cyber attack detected install remote support tool to protect banking credentials", 1, 4),
    
    # Lottery / Refund / Loan
    ("Congratulations you have won 25 Lakh in KBC Jio Lucky Draw deposit 15000 government tax fee in SBI account", 1, 5),
    ("PM Mudra loan approved 5 lakh loan at 1 percent annual interest pay registration processing fee 3500 immediately", 1, 5),
    ("Income tax refund 45000 approved credit to your bank account verify bank details and enter debit card PIN to receive", 1, 5),
    ("Electricity bill payment discount 50 percent cash back claim prize now before offer expires", 1, 5),
    
    # Family Emergency / Voice Clone
    ("Papa main police station mein hoon mera accident ho gaya hai police wale 50000 maang rahe hain turant Google Pay karo", 1, 6),
    ("Mummy main hospital mein hoon dost ka major surgery hai doctor emergency advance deposit maang rahe hain jaldi bhejo", 1, 6),
    ("Bhai mere lawyer ko bail ke liye urgent payment chahiye police ne galat case mein pakda hai abhi transfer kar", 1, 6),
    
    # Job / Task Scam
    ("Amazon part time work from home opportunity daily earn 3000 to 5000 by reviewing YouTube videos join Telegram", 1, 7),
    ("Google Maps review task daily payout 4500 rupees per day work 2 hours no investment needed register now", 1, 7),
    ("Instagram influencer follow and like task deposit 2000 receive 2800 profit within 15 minutes guaranteed return", 1, 7),
    
    # UPI / QR Scam
    ("Main aapke OLX product ka advance payment bhej raha hoon QR code scan karke UPI pin daalo paise receive ho jayenge", 1, 8),
    ("Army officer posting transfer selling household furniture send 5000 token advance via PhonePe", 1, 8),
    ("Refund department send money request approved accept collect request on Google Pay to credit amount", 1, 8),

    # Benign Dialogues (Negative class)
    ("Hello mummy main market se bol raha hoon sabzi aur doodh le liya hai ghar aa raha hoon", 0, 0),
    ("Good morning sir Flipkart delivery boy bol raha hoon aapka parcel leke gate par hoon", 0, 0),
    ("Kal office mein subah 10 baje quarterly review meeting hai presentation slides ready rakhna", 0, 0),
    ("Doctor Sharma ke clinic se call hai aapki appointment kal 4 baje scheduled hai", 0, 0),
    ("Bhai cricket match ka score kya chal raha hai sham ko saath mein dinner karte hain", 0, 0),
    ("Aapki car service complete ho gayi hai Maruti workshop se gaadi collect kar sakte hain", 0, 0),
    ("Electricity bill successfully paid transaction ID note kar lijiye confirmation SMS bhej diya hai", 0, 0),
    ("School van driver bol raha hoon heavy traffic ke karan 15 minute late hoga bachon ko bata dijiye", 0, 0),
    ("Train ticket status confirmed hai PNR status update check kar liya chart prepared hai", 0, 0),
    ("Hello sir blood donation camp organized hai Sunday ko kya aap participate karna chahenge", 0, 0)
]

print("Enriching dataset with conversational Hinglish & English scam permutations...")
np.random.seed(42)
prefixes = ["Urgent alert: ", "Important notice: ", "Dear customer: ", "Attention: ", "Official statement: ", ""]
suffixes = [" Respond immediately.", " Do not ignore.", " Action required now.", " Call back immediately.", ""]

for text, is_s, cat in indian_scam_templates:
    for _ in range(80):
        pre = np.random.choice(prefixes)
        suf = np.random.choice(suffixes)
        words = text.split()
        if len(words) > 5 and np.random.rand() > 0.5:
            idx = np.random.randint(0, len(words)-1)
            words[idx], words[idx+1] = words[idx+1], words[idx]
        aug_text = pre + " ".join(words) + suf
        texts.append(aug_text)
        scam_labels.append(is_s)
        cat_labels.append(cat)

print(f"Total Combined Training Dataset: {len(texts)} samples")

# Vocabulary & Tokenizer
vocab = {"<PAD>": 0, "<UNK>": 1, "<CLS>": 2, "<SEP>": 3}
for text in texts:
    clean_words = re.findall(r"\w+", text.lower())
    for w in clean_words:
        if w not in vocab and len(vocab) < 15000:
            vocab[w] = len(vocab)

print(f"Vocabulary Size: {len(vocab)} tokens")

MAX_LEN = 64
def encode_text(t):
    clean_words = re.findall(r"\w+", str(t).lower())
    ids = [vocab.get(w, vocab["<UNK>"]) for w in clean_words[:MAX_LEN]]
    if len(ids) < MAX_LEN:
        ids += [vocab["<PAD>"]] * (MAX_LEN - len(ids))
    return ids

X = np.array([encode_text(t) for t in texts], dtype=np.int64)
y_scam = np.array(scam_labels, dtype=np.float32)
y_cat = np.array(cat_labels, dtype=np.int64)

X_train, X_val, y_scam_tr, y_scam_val, y_cat_tr, y_cat_val = train_test_split(
    X, y_scam, y_cat, test_size=0.15, random_state=42, stratify=y_cat
)

print(f"Train samples: {len(X_train)} | Validation samples: {len(X_val)}")

class ScamCorpusDataset(Dataset):
    def __init__(self, X, y_scam, y_cat):
        self.X = torch.tensor(X, dtype=torch.long)
        self.y_scam = torch.tensor(y_scam, dtype=torch.float32).unsqueeze(1)
        self.y_cat = torch.tensor(y_cat, dtype=torch.long)
    def __len__(self):
        return len(self.X)
    def __getitem__(self, idx):
        return self.X[idx], self.y_scam[idx], self.y_cat[idx]

train_loader = DataLoader(ScamCorpusDataset(X_train, y_scam_tr, y_cat_tr), batch_size=64, shuffle=True)
val_loader = DataLoader(ScamCorpusDataset(X_val, y_scam_val, y_cat_val), batch_size=128, shuffle=False)

# Multi-Head Attention + BiLSTM
class MultiHeadSelfAttention(nn.Module):
    def __init__(self, embed_dim, num_heads):
        super().__init__()
        self.embed_dim = embed_dim
        self.num_heads = num_heads
        self.head_dim = embed_dim // num_heads
        assert self.head_dim * num_heads == embed_dim
        self.q_linear = nn.Linear(embed_dim, embed_dim)
        self.k_linear = nn.Linear(embed_dim, embed_dim)
        self.v_linear = nn.Linear(embed_dim, embed_dim)
        self.out_linear = nn.Linear(embed_dim, embed_dim)

    def forward(self, x):
        b, s, e = x.size()
        q = self.q_linear(x).view(b, s, self.num_heads, self.head_dim).transpose(1, 2)
        k = self.k_linear(x).view(b, s, self.num_heads, self.head_dim).transpose(1, 2)
        v = self.v_linear(x).view(b, s, self.num_heads, self.head_dim).transpose(1, 2)

        scores = torch.matmul(q, k.transpose(-2, -1)) / np.sqrt(self.head_dim)
        attn = F.softmax(scores, dim=-1)
        context = torch.matmul(attn, v)
        context = context.transpose(1, 2).contiguous().view(b, s, e)
        return self.out_linear(context)

class RakshakDeepBrain(nn.Module):
    def __init__(self, vocab_size, embed_dim=128, hidden_dim=128, num_categories=9):
        super().__init__()
        self.embedding = nn.Embedding(vocab_size, embed_dim, padding_idx=0)
        self.conv1 = nn.Conv1d(embed_dim, embed_dim, kernel_size=3, padding=1)
        self.conv2 = nn.Conv1d(embed_dim, embed_dim, kernel_size=5, padding=2)
        self.attn = MultiHeadSelfAttention(embed_dim, num_heads=4)
        self.lstm = nn.LSTM(embed_dim, hidden_dim, num_layers=2, batch_first=True, bidirectional=True, dropout=0.2)
        
        self.fc_shared = nn.Sequential(
            nn.Linear(hidden_dim * 2 + embed_dim, 128),
            nn.LayerNorm(128),
            nn.GELU(),
            nn.Dropout(0.3)
        )
        
        self.scam_head = nn.Sequential(
            nn.Linear(128, 32),
            nn.ReLU(),
            nn.Linear(32, 1),
            nn.Sigmoid()
        )
        
        self.cat_head = nn.Sequential(
            nn.Linear(128, 64),
            nn.ReLU(),
            nn.Linear(64, num_categories)
        )

    def forward(self, x):
        emb = self.embedding(x)
        conv_in = emb.transpose(1, 2)
        c1 = F.gelu(self.conv1(conv_in))
        c2 = F.gelu(self.conv2(conv_in))
        conv_out = (c1 + c2).transpose(1, 2)
        
        attn_out = self.attn(conv_out)
        lstm_out, _ = self.lstm(attn_out)
        
        lstm_pool = torch.mean(lstm_out, dim=1)
        attn_pool = torch.mean(attn_out, dim=1)
        combined = torch.cat([lstm_pool, attn_pool], dim=1)
        
        feat = self.fc_shared(combined)
        scam_prob = self.scam_head(feat)
        cat_logits = self.cat_head(feat)
        return scam_prob, cat_logits

model = RakshakDeepBrain(len(vocab)).to(device)
total_params = sum(p.numel() for p in model.parameters())
print(f"Total Model Parameters: {total_params:,}")

criterion_scam = nn.BCELoss()
criterion_cat = nn.CrossEntropyLoss()
optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3, weight_decay=1e-4)
scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=18, eta_min=1e-5)

EPOCHS = 18
print(f"Starting Deep Training for {EPOCHS} Epochs on {device}...")
start_time = time.time()

for epoch in range(1, EPOCHS + 1):
    ep_start = time.time()
    model.train()
    total_loss = 0.0
    
    for batch_x, batch_scam, batch_cat in train_loader:
        batch_x = batch_x.to(device)
        batch_scam = batch_scam.to(device)
        batch_cat = batch_cat.to(device)
        
        optimizer.zero_grad()
        out_scam, out_cat = model(batch_x)
        
        loss_s = criterion_scam(out_scam, batch_scam)
        loss_c = criterion_cat(out_cat, batch_cat)
        loss = loss_s + 0.5 * loss_c
        
        loss.backward()
        nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
        optimizer.step()
        total_loss += loss.item() * len(batch_x)
        
    scheduler.step()
    train_loss = total_loss / len(X_train)
    
    model.eval()
    val_preds_scam = []
    val_preds_cat = []
    val_trues_scam = []
    val_trues_cat = []
    
    with torch.no_grad():
        for batch_x, batch_scam, batch_cat in val_loader:
            batch_x = batch_x.to(device)
            out_scam, out_cat = model(batch_x)
            
            val_preds_scam.extend((out_scam.cpu().numpy() > 0.5).astype(int).flatten())
            val_preds_cat.extend(torch.argmax(out_cat, dim=1).cpu().numpy().flatten())
            val_trues_scam.extend(batch_scam.numpy().flatten())
            val_trues_cat.extend(batch_cat.numpy().flatten())
            
    val_acc_scam = accuracy_score(val_trues_scam, val_preds_scam)
    val_f1_scam = f1_score(val_trues_scam, val_preds_scam)
    val_acc_cat = accuracy_score(val_trues_cat, val_preds_cat)
    
    ep_duration = time.time() - ep_start
    elapsed_total = (time.time() - start_time) / 60.0
    
    print(f"Epoch {epoch:02d}/{EPOCHS} [{ep_duration:.1f}s | Elapsed: {elapsed_total:.1f}m] | Loss: {train_loss:.4f} | Scam Acc: {val_acc_scam*100:.2f}% | F1: {val_f1_scam*100:.2f}% | Category Acc: {val_acc_cat*100:.2f}%")

total_training_time = (time.time() - start_time) / 60.0
print(f"Training Finished in {total_training_time:.2f} minutes!")

# Export to ONNX
print("Exporting Rakshak DeepBrain to ONNX format...")
model.eval()
model_cpu = model.to("cpu")

dummy_input = torch.zeros((1, MAX_LEN), dtype=torch.long)
onnx_path = "training/rakshak_deepbrain.onnx"
asset_onnx_path = "app/src/main/assets/rakshak_deepbrain.onnx"

torch.onnx.export(
    model_cpu,
    dummy_input,
    onnx_path,
    input_names=["token_ids"],
    output_names=["scam_probability", "category_logits"],
    opset_version=14,
    dynamic_axes={
        "token_ids": {0: "batch_size", 1: "seq_len"},
        "scam_probability": {0: "batch_size"},
        "category_logits": {0: "batch_size"}
    }
)

import onnx
m = onnx.load(onnx_path)
onnx.save(m, asset_onnx_path, save_as_external_data=False)

file_size_mb = os.path.getsize(asset_onnx_path) / (1024 * 1024)
print(f"Self-contained ONNX saved to {asset_onnx_path} ({file_size_mb:.2f} MB)")

vocab_json_path = "app/src/main/assets/deepbrain_vocab.json"
with open(vocab_json_path, "w", encoding="utf-8") as f:
    json.dump({
        "vocab": vocab,
        "max_len": MAX_LEN,
        "categories": ID_TO_CAT
    }, f, ensure_ascii=False)
print(f"DeepBrain vocabulary saved to {vocab_json_path}")

# Verify ONNX Runtime Parity
import onnxruntime as ort
sess = ort.InferenceSession(asset_onnx_path)

test_sentences = [
    "CBI officer Sharma arrest warrant issued supreme court transfer money now",
    "Hello mummy main ghar aa raha hoon sham ko dinner banate hain",
    "FedEx courier parcel seized with illegal narcotics pay customs penalty",
    "Congratulations KBC prize winner deposit tax fee immediately"
]

print("=" * 70)
print("LIVE PREDICTION BENCHMARK WITH ONNX RUNTIME:")
print("=" * 70)

for sent in test_sentences:
    inp_ids = np.array([encode_text(sent)], dtype=np.int64)
    ort_outs = sess.run(None, {"token_ids": inp_ids})
    scam_p = float(ort_outs[0][0][0])
    cat_id = int(np.argmax(ort_outs[1][0]))
    cat_name = ID_TO_CAT.get(cat_id, "Unknown")
    verdict = "SCAM ALERT" if scam_p > 0.5 else "SAFE CALL"
    print(f"Input: {sent[:60]}...")
    print(f"  -> Verdict: {verdict} | Risk: {scam_p*100:.1f}% | Vector: {cat_name}")
