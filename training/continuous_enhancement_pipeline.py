# Continuous Model Enhancement Pipeline
import os, json, re, shutil
import numpy as np
from datetime import datetime
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, precision_score, recall_score, confusion_matrix

FROZEN_HOLDOUT_PATH = "training/frozen_holdout_set.json"
FEEDBACK_LOG_PATH = "training/ai_feedback_loop.jsonl"
ASSETS_DIR = "app/src/main/assets"
CONFIG_FILE = os.path.join(ASSETS_DIR, "scam_classifier_config.json")
PHRASES_FILE = os.path.join(ASSETS_DIR, "scam_phrases.json")

def get_or_create_frozen_holdout():
    if os.path.exists(FROZEN_HOLDOUT_PATH):
        print(f"Loading existing FROZEN holdout dataset from {FROZEN_HOLDOUT_PATH}")
        with open(FROZEN_HOLDOUT_PATH, "r", encoding="utf-8") as f:
            return json.load(f)

    print("Creating FROZEN holdout dataset for the first time...")
    holdout_scams = [
        "cbi court warrant digital arrest criminal case against aadhaar number",
        "customs department mumbai parcel seized containing illegal drugs pay fine",
        "kyc update pan card expired bank account debit card blocked within 24 hours",
        "pre-approved loan 10 lakh 1 percent interest pay processing fee immediately",
        "lottery winner 50 lakh ka kbc draw deposit tax amount in private account",
        "part time work from home review write daily 5000 earn telegram join",
        "olx advance payment qr code scan receive money into your bank",
        "electricity power disconnection tonight pay bill via link immediately",
        "trai mobile number disconnect in 2 hours illegal harassment complaints",
        "credit card reward points expiring redeem cash transfer to wallet",
        "mumbai crime branch inspector bol raha hoon video call digital arrest",
        "courier fedex ruka hua hai custom duty pay karo warna police aayegi",
        "sbi yono account login blocked update kyc documents click link",
        "pm mudra loan approval letter verification charges transfer karo",
        "instagram investment double money within 30 minutes send money upi"
    ]

    holdout_benigns = [
        "hello mummy market se kya lana hai dahi ya doodh",
        "good morning sir aapka flipkart delivery order gate par aa gaya",
        "kal meeting hai 3 baje office mein presentation ready rakhna",
        "doctor sharma ka clinic se bol rahe hain appointment 5 baje hai",
        "bhai shaadi ka card dene aaunga kal sham ko ghar par rehna",
        "cricket match ki ticket book ho gayi hai weekend ka plan done hai",
        "society annual maintenance bill receipt email par send kar di hai",
        "school bus timing subah saat baje ho gayi hai bachon ko bhej dena",
        "salary account mein credit ho gayi hai statement check kar lo",
        "car servicing complete ho gayi hai maruti workshop se collect kar lo",
        "train ticket confirm ho gaya hai pnr status chart prepared hai",
        "pharmacy se bol rahe hain aapki regular medicine arrange ho gayi hai",
        "lunch break mein cafeteria chalte hain saath mein",
        "electricity bill payment successful transaction reference number noted",
        "air conditioner filter clean kar diya hai technician charges 300"
    ]

    holdout = {
        "created_at": datetime.now().isoformat(),
        "scams": holdout_scams,
        "benigns": holdout_benigns
    }

    with open(FROZEN_HOLDOUT_PATH, "w", encoding="utf-8") as f:
        json.dump(holdout, f, indent=2, ensure_ascii=False)
    
    print(f"Frozen holdout saved with {len(holdout_scams)} scams and {len(holdout_benigns)} benign samples.")
    return holdout

def sanitize_transcript(text):
    text = re.sub(r"\b\+?\d{10,12}\b", "[PHONE_REDACTED]", text)
    text = re.sub(r"[\w.-]+@[\w.-]+", "[UPI_REDACTED]", text)
    text = re.sub(r"\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}\b", "[CARD_REDACTED]", text)
    return text.strip()

class RetrainingTriggerEngine:
    VOLUME_THRESHOLD = 20
    FPR_DRIFT_THRESHOLD = 0.08
    FNR_DRIFT_THRESHOLD = 0.05

    @classmethod
    def evaluate(cls, feedback_records):
        total_records = len(feedback_records)
        if total_records == 0:
            return False, "NO_DATA", {}

        if total_records >= cls.VOLUME_THRESHOLD:
            return True, "VOLUME_TRIGGER", {
                "count": total_records,
                "threshold": cls.VOLUME_THRESHOLD
            }

        window = feedback_records[-100:] if len(feedback_records) > 100 else feedback_records
        false_alarms = sum(1 for r in window if r.get("is_scam_predicted") == True and r.get("is_scam_actual") == False)
        missed_scams = sum(1 for r in window if r.get("is_scam_predicted") == False and r.get("is_scam_actual") == True)
        total_benigns = sum(1 for r in window if r.get("is_scam_actual") == False)
        total_scams = sum(1 for r in window if r.get("is_scam_actual") == True)

        fpr = (false_alarms / total_benigns) if total_benigns > 0 else 0.0
        fnr = (missed_scams / total_scams) if total_scams > 0 else 0.0

        if fpr > cls.FPR_DRIFT_THRESHOLD:
            return True, "FPR_DRIFT_TRIGGER", {
                "rolling_fpr": fpr,
                "threshold": cls.FPR_DRIFT_THRESHOLD,
                "window_size": len(window)
            }
        
        if fnr > cls.FNR_DRIFT_THRESHOLD:
            return True, "FNR_DRIFT_TRIGGER", {
                "rolling_fnr": fnr,
                "threshold": cls.FNR_DRIFT_THRESHOLD,
                "window_size": len(window)
            }

        return False, "BELOW_THRESHOLDS", {"count": total_records, "fpr": fpr, "fnr": fnr}

def evaluate_model_on_holdout(vectorizer, model, holdout):
    X_scams = holdout["scams"]
    X_benign = holdout["benigns"]
    X_all = X_scams + X_benign
    y_true = [1] * len(X_scams) + [0] * len(X_benign)

    X_vec = vectorizer.transform(X_all)
    y_pred = model.predict(X_vec)

    benign_vec = vectorizer.transform(X_benign)
    benign_pred = model.predict(benign_vec)
    fpr_benign = float(np.mean(benign_pred == 1))

    return {
        "accuracy": float(accuracy_score(y_true, y_pred)),
        "precision": float(precision_score(y_true, y_pred, zero_division=0)),
        "recall": float(recall_score(y_true, y_pred, zero_division=0)),
        "fpr_benign": fpr_benign,
        "total_test_samples": len(y_true)
    }

def run_continuous_enhancement_cycle(simulated_feedback=None):
    print("=" * 70)
    print("CONTINUOUS MODEL ENHANCEMENT PIPELINE - EXECUTION CYCLE")
    print("=" * 70)

    holdout = get_or_create_frozen_holdout()

    feedback_records = []
    if os.path.exists(FEEDBACK_LOG_PATH):
        with open(FEEDBACK_LOG_PATH, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    try:
                        feedback_records.append(json.loads(line))
                    except:
                        pass

    if simulated_feedback:
        feedback_records.extend(simulated_feedback)

    print(f"Total Ingested Feedback Records: {len(feedback_records)}")

    is_triggered, trigger_type, trigger_details = RetrainingTriggerEngine.evaluate(feedback_records)
    print(f"Trigger Status: is_triggered={is_triggered}, trigger_type={trigger_type}")
    print(f"Details: {trigger_details}")

    if not is_triggered:
        print("Retraining thresholds not met. Standing by.")
        return

    print("\nTrigger Threshold Met! Starting Retraining & Validation Gate...")

    base_scams = [
        "aapka Aadhaar card se illegal activities detect hui hain CBI officer bol raha hoon",
        "aap digital arrest ho gaye hain video call disconnect mat karna",
        "Mumbai customs mein aapke naam se ek illegal parcel pakda gaya hai",
        "main CBI se inspector Sharma bol raha hoon aapke against warrant issue hua hai",
        "aapka phone number money laundering case mein aaya hai toh aapko arrest kiya jayega",
        "supreme court ne aapke arrest ka order diya hai abhi transfer karo bail ke liye",
        "aapka bank account band hone wala hai KYC update karo turant",
        "pan card link nahi hai aapka account 24 ghante mein freeze ho jayega",
        "RBI ka new rule hai KYC update karna zaroori hai warna account suspend",
        "SBI se bol rahe hain aapki KYC expire ho gayi hai link par click karo",
        "aapko pre-approved loan mil raha hai bas processing fee bhejo",
        "lottery jeeti hai tax amount transfer karo",
        "FedEx se bol rahe hain aapka international courier customs mein ruka hua hai",
        "Amazon se work from home job daily 5000 kamao telegram task",
        "QR code scan karo aapke account mein advance payment aayega"
    ]

    base_benigns = [
        "hello main doctor sharma bol raha hoon aapki appointment kal subah 10 baje hai",
        "namaste main aapka delivery boy hoon aapka order gate par hai",
        "good morning sir main Flipkart se bol raha hoon aapka return process ho gaya",
        "kal meeting hai office mein 3 baje presentation ready rakhna",
        "mummy ghar par phone uthao main market se bol raha hoon",
        "hello bhai lunch karte hain aaj cafeteria mein",
        "sir aapki car ki servicing due hai Maruti service center",
        "school se bol raha hoon aapke bache ki PTM hai next week",
        "papa fees bharni hai account mein online send kar do",
        "cricket match ticket book ho gaya weekend par confirm",
        "broadband bill generate hua hai due date 25 August",
        "courier dispatch ho gaya hai tracking id message par hai",
        "blood donation camp hai Sunday ko register karna hai",
        "pharmacy se dawai ready hai aake le jaiye",
        "gym subscription renew karna hai end of month"
    ]

    augmented_scams = list(base_scams)
    augmented_benigns = list(base_benigns)

    new_scam_patterns = []
    for r in feedback_records:
        sanitized_text = sanitize_transcript(r.get("transcript", ""))
        if not sanitized_text:
            continue
        if r.get("is_scam_actual") == True:
            augmented_scams.append(sanitized_text)
            new_scam_patterns.append(sanitized_text)
        elif r.get("is_scam_actual") == False:
            augmented_benigns.append(sanitized_text)

    print(f"Augmented Dataset: {len(augmented_scams)} scams (+{len(augmented_scams)-len(base_scams)} new), {len(augmented_benigns)} benigns (+{len(augmented_benigns)-len(base_benigns)} new)")

    # 1. Evaluate Baseline Model
    base_vec = TfidfVectorizer(max_features=500, ngram_range=(1, 2), sublinear_tf=True)
    X_base = base_scams + base_benigns
    y_base = [1]*len(base_scams) + [0]*len(base_benigns)
    X_base_tfidf = base_vec.fit_transform(X_base)
    prod_model = LogisticRegression(max_iter=1000, C=1.0, class_weight="balanced")
    prod_model.fit(X_base_tfidf, y_base)

    prod_metrics = evaluate_model_on_holdout(base_vec, prod_model, holdout)

    # 2. Train Candidate Model
    cand_vec = TfidfVectorizer(max_features=600, ngram_range=(1, 2), sublinear_tf=True)
    X_aug = augmented_scams + augmented_benigns
    y_aug = [1]*len(augmented_scams) + [0]*len(augmented_benigns)
    X_aug_tfidf = cand_vec.fit_transform(X_aug)
    cand_model = LogisticRegression(max_iter=1000, C=1.0, class_weight="balanced")
    cand_model.fit(X_aug_tfidf, y_aug)

    cand_metrics = evaluate_model_on_holdout(cand_vec, cand_model, holdout)

    print("\n" + "=" * 70)
    print("FROZEN HOLDOUT BENCHMARK: PRODUCTION VS. CANDIDATE COMPARISON")
    print("=" * 70)
    print(f"{'Metric':<25} | {'Production Model':<18} | {'Candidate Model':<18} | {'Improvement':<12}")
    print("-" * 70)
    for k in ["accuracy", "precision", "recall", "fpr_benign"]:
        prod_val = prod_metrics[k]
        cand_val = cand_metrics[k]
        diff = cand_val - prod_val
        diff_str = f"{diff:+.4f}" if k != "fpr_benign" else f"{diff:+.4f} (lower is better)"
        print(f"{k:<25} | {prod_val:<18.4f} | {cand_val:<18.4f} | {diff_str:<12}")

    # Promotion Gate
    passed_gate = (
        cand_metrics["accuracy"] >= prod_metrics["accuracy"] and
        cand_metrics["fpr_benign"] <= max(prod_metrics["fpr_benign"], 0.05) and
        cand_metrics["recall"] >= prod_metrics["recall"]
    )

    print("-" * 70)
    if passed_gate:
        print("PROMOTION GATE: PASSED! Promoting Candidate Model to Production.")

        backup_file = os.path.join(ASSETS_DIR, "scam_classifier_config_v1.bak.json")
        if os.path.exists(CONFIG_FILE):
            shutil.copy2(CONFIG_FILE, backup_file)
            print(f"Production backup saved to {backup_file} (Rollback safe)")

        new_vocab = {k: int(v) for k, v in cand_vec.vocabulary_.items()}
        new_config = {
            "version": "v2.0-continuous-enhancement",
            "updated_at": datetime.now().isoformat(),
            "trigger_reason": trigger_type,
            "vocabulary": new_vocab,
            "idf": [float(x) for x in cand_vec.idf_.tolist()],
            "feature_names": cand_vec.get_feature_names_out().tolist(),
            "model_weights": [float(x) for x in cand_model.coef_[0].tolist()],
            "model_intercept": float(cand_model.intercept_[0]),
            "frozen_holdout_metrics": cand_metrics
        }

        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(new_config, f, ensure_ascii=False)
        print(f"New model deployed to {CONFIG_FILE}")

        if os.path.exists(PHRASES_FILE) and new_scam_patterns:
            try:
                with open(PHRASES_FILE, "r", encoding="utf-8") as f:
                    phrases = json.load(f)
                if "emerging_threats" not in phrases:
                    phrases["emerging_threats"] = []
                for p in new_scam_patterns[:10]:
                    if p not in phrases["emerging_threats"]:
                        phrases["emerging_threats"].append(p)
                with open(PHRASES_FILE, "w", encoding="utf-8") as f:
                    json.dump(phrases, f, indent=2, ensure_ascii=False)
                print(f"Updated {PHRASES_FILE} with emerging threat patterns.")
            except Exception as e:
                print(f"Notice updating phrases: {e}")
    else:
        print("PROMOTION GATE: FAILED. Candidate model degraded benchmark metrics.")

    return prod_metrics, cand_metrics, passed_gate

if __name__ == "__main__":
    simulated_corrections = [
        {"call_id": "c101", "is_scam_predicted": False, "is_scam_actual": True, "transcript": "TRAI notification aapka sim card illegal messaging ke liye block ho raha hai turant verify karo"},
        {"call_id": "c102", "is_scam_predicted": False, "is_scam_actual": True, "transcript": "Electricity board power cut scheduled today at 9pm pay pending electricity bill link click karo"},
        {"call_id": "c103", "is_scam_predicted": False, "is_scam_actual": True, "transcript": "Instagram task earn daily 3000 rupees like videos and earn money transfer upi"},
        {"call_id": "c104", "is_scam_predicted": False, "is_scam_actual": True, "transcript": "Customs officer courier package contains narcotics parcel seized at airport pay penalty"},
        {"call_id": "c105", "is_scam_predicted": False, "is_scam_actual": True, "transcript": "Telecom department digital arrest warrant issued supreme court order transfer security deposit"},
        {"call_id": "c106", "is_scam_predicted": False, "is_scam_actual": True, "transcript": "KBC lucky draw winner 25 lakh claim prize pay government tax in advance"},
        {"call_id": "c107", "is_scam_predicted": False, "is_scam_actual": True, "transcript": "Credit card rewards reward points conversion cash bonus transfer bank account details"},
        {"call_id": "c108", "is_scam_predicted": False, "is_scam_actual": True, "transcript": "Stock market tips VIP telegram group guaranteed 500 percent profit invest money now"},
        {"call_id": "c109", "is_scam_predicted": False, "is_scam_actual": True, "transcript": "Indane gas subsidy pending update bank passbook details via link"},
        {"call_id": "c110", "is_scam_predicted": False, "is_scam_actual": True, "transcript": "Aadhaar biometrics locked verify biometric update pay nominal fee at portal"},
        {"call_id": "c111", "is_scam_predicted": True, "is_scam_actual": False, "transcript": "Doctor clinic se bol rahe hain appointment cancel ho gaya kal subah aana"},
        {"call_id": "c112", "is_scam_predicted": True, "is_scam_actual": False, "transcript": "Aapka gas cylinder delivery boy gate par hai payment online kar do"},
        {"call_id": "c113", "is_scam_predicted": True, "is_scam_actual": False, "transcript": "Car wash shop se bol rahe hain car ready hai collect kar lo"},
        {"call_id": "c114", "is_scam_predicted": True, "is_scam_actual": False, "transcript": "Gym trainer bol raha hoon kal morning session time 6am change hua hai"},
        {"call_id": "c115", "is_scam_predicted": True, "is_scam_actual": False, "transcript": "Society guard gate entry pass check karne aaya hai"}
    ] * 2

    run_continuous_enhancement_cycle(simulated_corrections)
