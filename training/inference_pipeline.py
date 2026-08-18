import time
import random

print("=======================================")
print("🚀 Rakshak-Setu: End-to-End Inference Demo")
print("=======================================")

def run_mock_pipeline():
    print("\n[Step 1] Intercepting Audio Call...")
    time.sleep(1)
    print("📞 Call from: +91 9876543210")
    print("⏱️  Duration: 14s")
    
    print("\n[Step 2] Processing Voice via Whisper LoRA...")
    time.sleep(1.5)
    transcribed_text = "urgent please send the bank account password and otp immediately to unlock your funds"
    print(f"📝 Transcribed Text: '{transcribed_text}'")

    print("\n[Step 3] Analyzing Voice Tone (AST Emotion Classifier)...")
    time.sleep(1)
    emotion_score = random.uniform(85.0, 95.0)
    print(f"🎙️ Tone: HIGH STRESS / URGENCY (Confidence: {emotion_score:.1f}%)")

    print("\n[Step 4] Running Custom BiLSTM Spam Intent Analysis...")
    time.sleep(1.5)
    # Using the pre-calculated probability from our explain_model.py
    scam_prob = 94.57 
    print(f"🚨 Scam Probability: {scam_prob:.2f}%")

    if scam_prob > 80.0:
        print("\n[Step 5] Triggering Explainable AI (XAI)...")
        time.sleep(1)
        print("🔍 Key Risk Indicators found in text:")
        print("   🔴 +0.050 : urgent")
        print("   🔴 +0.036 : bank")
        print("   🔴 +0.036 : account")
        print("   🔴 +0.019 : immediately")
        
        print("\n=======================================")
        print("⛔ ACTION: CALL BLOCKED & REPORTED!")
        print("=======================================")
    else:
        print("\n✅ ACTION: Call Safe.")

if __name__ == "__main__":
    run_mock_pipeline()
