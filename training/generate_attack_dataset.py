import json
import random
import os

print("=======================================")
print("🧬 Generating Social Engineering Attack Patterns Dataset")
print("=======================================")

patterns = {
    "Urgency_Fear": [
        "Your bank account has been locked. Click here to verify your identity.",
        "URGENT: Your recent payment of $900 failed. Please update your billing info immediately.",
        "Your credit card has been suspended due to suspicious activity. Call us now.",
        "Final notice: Pay your overdue taxes or an arrest warrant will be issued.",
        "We detected a login from an unknown device. Reply with OTP to block it."
    ],
    "Greed_Lottery": [
        "Congratulations! You won $10,000 in the national lottery. Send a small fee to claim.",
        "You have been selected for a free iPhone 15! Click this link to provide shipping details.",
        "Earn $500 a day working from home. No experience needed. Sign up here.",
        "Your crypto wallet has received 2.5 BTC. Login to withdraw your funds now.",
        "Exclusive offer: Get 90% off all branded items. Only for the next 10 minutes!"
    ],
    "Authority_Impersonation": [
        "This is Officer Smith from the local police. We have a warrant for your arrest.",
        "Hello, this is HR. Please review the attached termination document.",
        "Message from the CEO: I am in a meeting, please buy $500 in gift cards and send the codes.",
        "IRS Alert: You owe back taxes. Pay immediately to avoid legal action.",
        "This is Amazon Support. Your package is held at customs. Pay the clearance fee."
    ],
    "Tech_Support": [
        "Microsoft Windows Alert: Your computer is infected with a Trojan. Call 1-800-XXX-XXXX.",
        "Your antivirus subscription has expired. Click here to renew and protect your PC.",
        "Apple Support: Your iCloud has been compromised. Log in to secure it.",
        "We detected unusual activity on your network. Download this tool to scan your router.",
        "Your email storage is full. Please click here to upgrade your quota."
    ]
}

# Generate synthetic dataset by mixing and matching variations
dataset = []
for _ in range(500):
    for category, texts in patterns.items():
        base_text = random.choice(texts)
        # Add slight variations (e.g. random numbers, names, links)
        if "1-800" in base_text:
            base_text = base_text.replace("1-800-XXX-XXXX", f"1-800-{random.randint(100,999)}-{random.randint(1000,9999)}")
        if "$500" in base_text:
            base_text = base_text.replace("$500", f"${random.randint(50,1000)}")
            
        dataset.append({
            "text": base_text.lower(),
            "label": category
        })

random.shuffle(dataset)

# Save to JSON
output_file = "training/attack_patterns.json"
with open(output_file, 'w') as f:
    json.dump(dataset, f, indent=4)

print(f"✅ Generated {len(dataset)} synthetic examples.")
print(f"✅ Saved to {output_file}")
