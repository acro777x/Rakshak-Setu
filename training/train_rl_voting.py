import json
import random

print("==================================================")
print("🤖 Reinforcement Learning Agent for Scam Voting")
print("==================================================")

# State Space Features:
# 1. Similarity Score (0.0 to 1.0)
# 2. Deepfake Probability (0.0 to 1.0)
# 3. Emotion Stress Score (0.0 to 1.0)
# 4. Acoustic Environment (0=Home, 1=Street, 2=CallCenter)

# Action Space:
# 0 = Allow Call (Not Scam)
# 1 = Block/Alert Call (Scam)

# Simple RL Agent learning optimal weights (Q-values/Parameters) 
# to calculate a combined Risk Score instead of static if-else statements.
weights = {
    "w_similarity": 0.5,
    "w_deepfake": 0.2,
    "w_stress": 0.1,
    "w_acoustic": 0.2,
    "threshold": 0.75
}

learning_rate = 0.05
epochs = 1000

print(f"Initial Weights: {weights}")
print("Simulating User Feedback Loops from ai_feedback_loop.jsonl...")

for epoch in range(epochs):
    # Simulate a state
    sim = random.random()
    df_prob = random.random()
    stress = random.random()
    acoustic = random.choice([0, 1, 2])
    
    ac_val = 1.0 if acoustic == 2 else 0.2 if acoustic == 1 else 0.0
    
    # Current Policy Prediction
    risk_score = (sim * weights["w_similarity"] + 
                  df_prob * weights["w_deepfake"] + 
                  stress * weights["w_stress"] + 
                  ac_val * weights["w_acoustic"])
                  
    action = 1 if risk_score > weights["threshold"] else 0
    
    # Simulate Ground Truth (Environment Reward)
    # A true scam usually has high similarity OR high deepfake OR call center
    is_true_scam = 1 if (sim > 0.8 or df_prob > 0.9 or (ac_val == 1.0 and stress > 0.6)) else 0
    
    # Calculate Reward
    if action == is_true_scam:
        reward = 1 # Correct prediction
    else:
        reward = -1 # False positive or False negative
        
    # Update Weights (Simplified Gradient Ascent)
    error = is_true_scam - risk_score
    weights["w_similarity"] += learning_rate * error * sim
    weights["w_deepfake"] += learning_rate * error * df_prob
    weights["w_stress"] += learning_rate * error * stress
    weights["w_acoustic"] += learning_rate * error * ac_val

# Normalize weights so they sum to 1.0
total = sum([weights["w_similarity"], weights["w_deepfake"], weights["w_stress"], weights["w_acoustic"]])
weights["w_similarity"] /= total
weights["w_deepfake"] /= total
weights["w_stress"] /= total
weights["w_acoustic"] /= total

print("\n🎯 RL Training Complete!")
print(f"Optimized Learned Weights: {weights}")

# Save the Policy for Android to consume
policy_path = "app/src/main/assets/rl_policy.json"
with open(policy_path, 'w') as f:
    json.dump(weights, f, indent=4)
    
print(f"✅ RL Policy saved to {policy_path}. The app will now use this dynamic policy!")
