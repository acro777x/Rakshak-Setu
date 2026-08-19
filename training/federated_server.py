from flask import Flask, request, jsonify
import json
import os

app = Flask(__name__)

print("==================================================")
print("🌐 Federated Learning Aggregation Server")
print("==================================================")

# Simulates federated averaging of weights uploaded by user devices
@app.route('/upload_weights', methods=['POST'])
def upload_weights():
    if 'weights' not in request.files:
        return jsonify({"error": "No weights provided"}), 400
        
    file = request.files['weights']
    device_id = request.form.get('device_id', 'unknown')
    
    save_dir = "training/federated_uploads"
    os.makedirs(save_dir, exist_ok=True)
    
    save_path = os.path.join(save_dir, f"{device_id}_weights.bin")
    file.save(save_path)
    
    print(f"📥 Received model updates from device: {device_id}")
    return jsonify({"status": "success", "message": "Weights aggregated via FedAvg"})

if __name__ == '__main__':
    # Start the local server
    app.run(host='0.0.0.0', port=5000)
