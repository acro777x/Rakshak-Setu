import os
import torch
import numpy as np
from datasets import Dataset
from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification,
    TrainingArguments,
    Trainer
)
import evaluate

print("=======================================")
print("🚀 Starting Scam Intent Text Classifier")
print("=======================================")

# Device setup
if torch.backends.mps.is_available():
    device = "mps"
elif torch.cuda.is_available():
    device = "cuda"
else:
    device = "cpu"
print(f"Using device: {device}\n")

# 1. Dummy Hinglish Dataset (Scam vs Safe)
# 1 = Scam, 0 = Safe
data = {
    "text": [
        "sir apka credit card block ho gaya hai otp dijiye",
        "hi manish let's meet for coffee tomorrow",
        "customs department se bol raha hu apke package me illegal items hain",
        "mummy main market ja raha hu sabji lane",
        "apka bank account freeze ho jayega agar apne KYC update nahi kiya",
        "happy birthday bhai party kab de raha hai",
        "you have won a lottery of 10 lakh rupees click this link",
        "please find the attached quarterly report for review",
        "digital arrest warrant issue hua hai apke khilaf police bhej raha hu",
        "kya kal meeting 10 baje hai?",
        "your amazon package delivery failed update address immediately",
        "kal raat ka dinner bahut acha tha",
        "police station se inspector bol raha hu account verify karo",
        "cbi se call hai aadhar card number batao warna jail hogi",
        "can we reschedule our call to next week?",
        "sir loan approve ho gaya hai processing fee bhej do 5000",
        "good morning sir aj ka schedule kya hai",
        "atm card expire ho raha hai pin confirm karo",
        "mere bhai ki shadi me zaroor aana",
        "electricity bill pending hai light kat jayegi bsnl app download karo"
    ],
    "label": [1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 0, 1]
}

# Augment the data by duplicating it to create a slightly larger training set for the demo
expanded_data = {"text": data["text"] * 10, "label": data["label"] * 10}
dataset = Dataset.from_dict(expanded_data)

# Split train and test
dataset = dataset.train_test_split(test_size=0.2)
train_dataset = dataset["train"]
eval_dataset = dataset["test"]

print(f"Train samples: {len(train_dataset)}")
print(f"Test samples:  {len(eval_dataset)}")

# 2. Tokenizer & Model setup
MODEL_ID = "distilbert-base-multilingual-cased"
print(f"\nLoading Tokenizer and Base Model: {MODEL_ID}...")
tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
model = AutoModelForSequenceClassification.from_pretrained(MODEL_ID, num_labels=2)
model.to(device)

def tokenize_function(examples):
    return tokenizer(examples["text"], padding="max_length", truncation=True, max_length=64)

print("\nTokenizing data...")
tokenized_train = train_dataset.map(tokenize_function, batched=True)
tokenized_eval = eval_dataset.map(tokenize_function, batched=True)

# 3. Metrics
accuracy_metric = evaluate.load("accuracy")
f1_metric = evaluate.load("f1")

def compute_metrics(eval_pred):
    logits, labels = eval_pred
    predictions = np.argmax(logits, axis=-1)
    acc = accuracy_metric.compute(predictions=predictions, references=labels)
    f1 = f1_metric.compute(predictions=predictions, references=labels)
    return {"accuracy": acc["accuracy"], "f1": f1["f1"]}

# 4. Training Arguments
OUTPUT_DIR = "scam-text-classifier"
training_args = TrainingArguments(
    output_dir=OUTPUT_DIR,
    eval_strategy="epoch",
    learning_rate=2e-5,
    per_device_train_batch_size=16,
    per_device_eval_batch_size=16,
    num_train_epochs=5,
    weight_decay=0.01,
    report_to=["none"],
    logging_steps=5,
    save_strategy="no"
)

trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=tokenized_train,
    eval_dataset=tokenized_eval,
    compute_metrics=compute_metrics,
)

# 5. Execute Training
print("\n🔥 Commencing Local Training on Mac MPS (GPU)...")
trainer.train()

# 6. Evaluation / Testing
print("\n🧪 Running Final Evaluation on Test Set...")
eval_results = trainer.evaluate()
print("=======================================")
print(f"Test Accuracy : {eval_results['eval_accuracy'] * 100:.2f}%")
print(f"Test F1 Score : {eval_results['eval_f1'] * 100:.2f}%")
print("=======================================")

# 7. Save
trainer.save_model(OUTPUT_DIR)
tokenizer.save_pretrained(OUTPUT_DIR)
print(f"\nModel and tokenizer saved to {OUTPUT_DIR}")
print("Training and Testing Complete!")
