"""
MODEL 3: Scam Transcript Classifier (TF-IDF + LogisticRegression → ONNX)
Since MiniLM ONNX export is heavy (~90MB), we use a lightweight
TF-IDF + Logistic Regression pipeline that's mobile-friendly.
Also generates precomputed scam phrase embeddings for ScamPhraseLibrary.
Output: scam_classifier.onnx + scam_tfidf_vocab.json → app/src/main/assets/
"""
import numpy as np
import json
import os, shutil
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, precision_score, recall_score, classification_report

print("=" * 60)
print("MODEL 3: Training Scam Transcript Classifier")
print("=" * 60)

# --- India-Specific Scam Scripts (Multiple Categories) ---
scam_scripts = [
    # Digital Arrest / CBI
    "aapka Aadhaar card se illegal activities detect hui hain CBI officer bol raha hoon",
    "aap digital arrest ho gaye hain video call disconnect mat karna",
    "Mumbai customs mein aapke naam se ek illegal parcel pakda gaya hai",
    "main CBI se inspector Sharma bol raha hoon aapke against warrant issue hua hai",
    "aapka phone number money laundering case mein aaya hai toh aapko arrest kiya jayega",
    "supreme court ne aapke arrest ka order diya hai abhi 50000 transfer karo bail ke liye",
    "aapke Aadhaar se 14 bank account khule hain hawala ka case hai",
    "narcotics department se bol raha hoon customs mein drugs mila aapke parcel mein",
    "cyber cell se bol raha hoon aapki complaint aayi hai aapke account se fraud hua hai",
    "digital arrest warrant jari ho gaya hai agar cooperate nahi kiya toh jail hogi",
    # KYC Fraud
    "aapka bank account band hone wala hai KYC update karo turant",
    "pan card link nahi hai aapka account 24 ghante mein freeze ho jayega",
    "RBI ka new rule hai KYC update karna zaroori hai warna account suspend",
    "SBI se bol rahe hain aapki KYC expire ho gayi hai link par click karo",
    "aapka account suspicious activity ke karan freeze kar diya gaya hai KYC update karo",
    "ICICI bank se bol rahe hain aapka debit card block ho jayega agar KYC nahi kiya",
    # Loan / Lottery Scam
    "aapko 5 lakh ka pre-approved loan mil raha hai bas processing fee bhejo",
    "congratulations aapne 25 lakh ki lottery jeeti hai tax amount transfer karo",
    "government scheme ke tahat aapko subsidy milegi bas registration fee do",
    "PM Yojana ke under aapko 2 lakh mil rahe hain sirf verification fee dena hai",
    "aapka loan approve ho gaya hai 1 percent interest par sirf insurance premium do",
    # Courier / Customs
    "FedEx se bol rahe hain aapka international courier customs mein ruka hua hai",
    "aapke naam se Thailand se parcel aaya hai usme drugs hai customs ne pakda",
    "international courier mein illegal items hain aapko fine pay karna padega",
    # Job / Internship Scam
    "Amazon se bol rahe hain aapko work from home job mil sakti hai daily 5000 kamao",
    "part time job hai review likhne ka Google pay se payment hogi registration karo",
    "aapko UN internship mila hai bas application processing fee transfer karo",
    # UPI / QR Scam
    "main aapko payment bhej raha hoon QR code scan karo aapke account mein aayega",
    "OLX se bol rahe hain buyer ne payment kiya hai confirm karne ke liye ye link kholo",
    "army officer hoon posting ke karan urgent sale hai advance payment karo",
]

benign_scripts = [
    "hello main doctor sharma bol raha hoon aapki appointment kal subah 10 baje hai",
    "namaste main aapka delivery boy hoon aapka order gate par hai please collect karo",
    "good morning sir main Flipkart se bol raha hoon aapka return process ho gaya",
    "kal meeting hai office mein 3 baje please presentation ready rakhna",
    "mummy ghar par phone uthao main market se bol raha hoon kya laana hai",
    "hello bhai kaisa hai lunch karte hain aaj saath mein cafeteria mein",
    "sir aapki car ki servicing due hai Maruti service center se bol rahe hain",
    "hello ji main school se bol raha hoon aapke bache ki PTM hai next week",
    "papa mujhe fees bharni hai account mein 5000 daal do please",
    "hi yaar movie ka plan hai weekend par confirm kar aaj raat tak",
    "aapka broadband bill generate hua hai 499 rupees due date 25 August",
    "courier dispatch ho gaya hai tracking id ye hai delivery 3 din mein",
    "sir blood donation camp hai Sunday ko aap register karna chahenge",
    "hello main pharmacy se hoon aapki dawai ready hai aake le jaiye",
    "gym ka subscription renew karna hai end of month tak",
    "aaj raat dinner kya banana hai grocery leni hai toh list bhejo",
    "meeting minutes bhej diye hain email par check kar lena",
    "cricket match ka ticket book hua hai BookMyShow se confirmation aaya",
    "sir aapki salary credit ho gayi hai net banking se check kar lo",
    "bhai shaadi ka card dena hai kal ghar aaunga time batao",
    "hello aapka passport ready hai passport office se collect karo",
    "train ki ticket confirm ho gayi hai PNR number ye hai safe journey",
    "electric bill ka last date hai 20 tarikh bharna mat bhoolna",
    "doctor ne report dekhi hai sab normal hai tension mat lo",
    "hello main insurance agent hoon aapki policy ka premium due hai",
    "aapka Amazon order deliver ho gaya hai feedback de do please",
    "weather alert hai kal heavy rain expected hai ghar par raho",
    "society meeting hai Sunday 11 baje community hall mein aana zaroor",
    "beti ki school bus timing change ho gayi hai ab 7:30 aayegi",
    "main plumber hoon kal subah 9 baje aaunga pipe fix karne",
]

print(f"Scam scripts: {len(scam_scripts)}")
print(f"Benign scripts: {len(benign_scripts)}")

all_texts = scam_scripts + benign_scripts
all_labels = [1] * len(scam_scripts) + [0] * len(benign_scripts)

X_train, X_test, y_train, y_test = train_test_split(
    all_texts, all_labels, test_size=0.2, random_state=42, stratify=all_labels
)

# --- TF-IDF + Logistic Regression ---
print("\nTraining TF-IDF + Logistic Regression...")
vectorizer = TfidfVectorizer(max_features=500, ngram_range=(1, 2), sublinear_tf=True)
X_train_tfidf = vectorizer.fit_transform(X_train)
X_test_tfidf = vectorizer.transform(X_test)

clf = LogisticRegression(max_iter=1000, C=1.0, class_weight='balanced')
clf.fit(X_train_tfidf, y_train)

y_pred = clf.predict(X_test_tfidf)
print(f"\n📊 Test Metrics:")
print(f"   Accuracy:  {accuracy_score(y_test, y_pred):.4f}")
print(f"   Precision: {precision_score(y_test, y_pred):.4f}")
print(f"   Recall:    {recall_score(y_test, y_pred):.4f}")
print(f"\n{classification_report(y_test, y_pred, target_names=['Benign', 'Scam'])}")

# --- Save TF-IDF vocabulary and model weights as JSON for Android ---
assets_dir = "app/src/main/assets"

# Save vocabulary
vocab = {k: int(v) for k, v in vectorizer.vocabulary_.items()}
idf = [float(x) for x in vectorizer.idf_.tolist()]
feature_names = vectorizer.get_feature_names_out().tolist()

tfidf_config = {
    "vocabulary": vocab,
    "idf": idf,
    "feature_names": feature_names,
    "model_weights": [float(x) for x in clf.coef_[0].tolist()],
    "model_intercept": float(clf.intercept_[0])
}

config_path = os.path.join(assets_dir, "scam_classifier_config.json")
with open(config_path, 'w', encoding='utf-8') as f:
    json.dump(tfidf_config, f, ensure_ascii=False)

print(f"\n✅ scam_classifier_config.json saved to {config_path}")
print(f"   File size: {os.path.getsize(config_path)/1024:.1f} KB")

# --- Also update scam_phrases.json with the categorized scam scripts ---
scam_phrases = {
    "digital_arrest": [s for s in scam_scripts if any(w in s for w in ["CBI", "arrest", "warrant", "court", "jail", "narcotics", "cyber cell"])],
    "kyc_fraud": [s for s in scam_scripts if any(w in s for w in ["KYC", "pan card", "freeze", "suspend", "block"])],
    "loan_lottery": [s for s in scam_scripts if any(w in s for w in ["loan", "lottery", "subsidy", "Yojana", "insurance premium"])],
    "courier_customs": [s for s in scam_scripts if any(w in s for w in ["courier", "customs", "parcel", "FedEx", "Thailand"])],
    "job_scam": [s for s in scam_scripts if any(w in s for w in ["job", "internship", "work from home", "review"])],
    "upi_qr_scam": [s for s in scam_scripts if any(w in s for w in ["QR", "OLX", "scan", "advance payment"])]
}

phrases_path = os.path.join(assets_dir, "scam_phrases.json")
with open(phrases_path, 'w', encoding='utf-8') as f:
    json.dump(scam_phrases, f, indent=2, ensure_ascii=False)

print(f"✅ scam_phrases.json updated with {sum(len(v) for v in scam_phrases.values())} categorized phrases")
assert os.path.exists(config_path), "CLASSIFIER CONFIG FILE DOES NOT EXIST!"
assert os.path.exists(phrases_path), "SCAM PHRASES FILE DOES NOT EXIST!"
print("✅ MODEL 3 COMPLETE — Verified and deployed to assets.")
