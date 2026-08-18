import csv
import json
import os
import argparse

# Define the base Rakshak Setu structure and categories
BASE_CORPUS = {
    "version": 1,
    "categories": [
        {"id": "digital_arrest", "label": "Digital Arrest / Fake CBI", "phrases": []},
        {"id": "kyc_fraud", "label": "KYC / Account Freeze", "phrases": []},
        {"id": "courier_customs", "label": "Courier / Customs", "phrases": []},
        {"id": "tech_support", "label": "Fake Tech Support", "phrases": []},
        {"id": "lottery_refund", "label": "Lottery / EMI Refund", "phrases": []},
        {"id": "family_emergency", "label": "Voice-Clone Family Emergency", "phrases": []},
        {"id": "job_scam", "label": "Fake Job / Task Scam", "phrases": []},
        {"id": "qr_scam", "label": "QR Code / Collect Request", "phrases": []}
    ]
}

# Keyword mappings to guess the category from a Kaggle CSV transcript row
KEYWORD_MAPPINGS = {
    "digital_arrest": ["cbi", "arrest", "warrant", "police", "supreme court", "narcotics", "department"],
    "kyc_fraud": ["kyc", "block", "suspend", "expire", "aadhar link", "verify"],
    "courier_customs": ["parcel", "customs", "fedex", "drugs", "illegal", "duty", "passport"],
    "tech_support": ["anydesk", "teamviewer", "screen share", "virus", "hack", "remote access"],
    "lottery_refund": ["lottery", "kbc", "refund", "cashback", "prize", "inaam", "tax pay"],
    "family_emergency": ["accident", "hospital", "admit", "emergency", "urgent", "police ne pakad"],
    "job_scam": ["part-time", "task", "youtube", "like", "telegram", "review", "commission"],
    "qr_scam": ["qr scan", "collect request", "pin daloge", "pay button", "advance payment send"]
}

def determine_category(transcript_text):
    text_lower = transcript_text.lower()
    for cat_id, keywords in KEYWORD_MAPPINGS.items():
        for kw in keywords:
            if kw in text_lower:
                return cat_id
    return None

def process_kaggle_csv(input_csv_path, text_column_name, label_column_name, scam_label_value):
    corpus = BASE_CORPUS.copy()
    categories_dict = {cat["id"]: cat["phrases"] for cat in corpus["categories"]}
    
    total_added = 0
    
    with open(input_csv_path, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        
        if text_column_name not in reader.fieldnames:
            print(f"Error: Column '{text_column_name}' not found in CSV.")
            return corpus
            
        for row in reader:
            # Check if this row is actually marked as a scam in the dataset
            is_scam = True
            if label_column_name and label_column_name in row:
                if str(row[label_column_name]).strip() != str(scam_label_value):
                    is_scam = False
            
            if not is_scam:
                continue
                
            transcript = row[text_column_name].strip()
            if not transcript:
                continue
                
            cat_id = determine_category(transcript)
            if cat_id:
                # To keep it lightweight, we extract snippets instead of full huge transcripts
                # In a real NLP pipeline, you'd extract exactly the sentence containing the keyword
                # Here we just take the first 150 chars as the 'phrase' for embedding.
                snippet = transcript[:150]
                if snippet not in categories_dict[cat_id]:
                    categories_dict[cat_id].append(snippet)
                    total_added += 1
                    
    print(f"Successfully processed Kaggle dataset. Added {total_added} new scam phrases.")
    return corpus

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Convert Kaggle scam call CSV into Rakshak Setu scam_phrases.json")
    parser.add_argument("--input", required=True, help="Path to the downloaded Kaggle CSV file")
    parser.add_argument("--output", default="../app/src/main/assets/scam_phrases.json", help="Output JSON path")
    parser.add_argument("--text_col", required=True, help="Name of the column containing the transcript text")
    parser.add_argument("--label_col", default=None, help="Name of the column containing the scam/normal label (optional)")
    parser.add_argument("--scam_val", default="1", help="The value in label_col that indicates a scam (default: 1)")
    
    args = parser.parse_args()
    
    if not os.path.exists(args.input):
        print(f"Error: Input file {args.input} does not exist.")
        exit(1)
        
    final_corpus = process_kaggle_csv(args.input, args.text_col, args.label_col, args.scam_val)
    
    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    with open(args.output, 'w', encoding='utf-8') as f:
        json.dump(final_corpus, f, indent=2, ensure_ascii=False)
        
    print(f"Corpus saved to {args.output}")
