from flask import Flask, request, jsonify
import spacy
import networkx as nx
import re

app = Flask(__name__)

# Load a small NLP model for Named Entity Recognition (simulated for UPI/Names)
# In production: spacy.load("en_core_web_sm")
try:
    nlp = spacy.load("en_core_web_sm")
except:
    nlp = None

# In-memory Graph Database (TGRAPP inspired) to cluster scam campaigns
# Nodes: Phone Numbers, UPI IDs, Bank Accounts
# Edges: Connected if they appeared in the same scam transcript
G = nx.Graph()

print("==================================================")
print("🕸️ Centralized Threat Intelligence Platform (TIP)")
print("==================================================")

def extract_entities(text):
    """ Extract UPI IDs, Bank Accounts, Names, and URLs using Regex and NLP. """
    entities = {
        "upi_ids": re.findall(r'[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}', text),
        "phone_numbers": re.findall(r'\b\d{10}\b', text),
        "urls": re.findall(r'(https?://[^\s]+)', text)
    }
    
    if nlp:
        doc = nlp(text)
        entities["names"] = [ent.text for ent in doc.ents if ent.label_ == "PERSON"]
        entities["orgs"] = [ent.text for ent in doc.ents if ent.label_ == "ORG"]
    else:
        entities["names"] = []
        entities["orgs"] = []
        
    return entities

@app.route('/report_scam', methods=['POST'])
def report_scam():
    data = request.json
    caller_phone = data.get('caller_phone', 'unknown')
    transcript = data.get('transcript', '')
    
    # Extract Intelligence
    extracted = extract_entities(transcript)
    
    # Build Graph Relationships
    G.add_node(caller_phone, type='phone')
    for upi in extracted['upi_ids']:
        G.add_node(upi, type='upi')
        G.add_edge(caller_phone, upi)
        
    for url in extracted['urls']:
        G.add_node(url, type='url')
        G.add_edge(caller_phone, url)
        
    # Calculate Campaign Risk Score (Graph Centrality)
    try:
        centrality = nx.degree_centrality(G)
        risk_score = centrality.get(caller_phone, 0.1) * 10 # Scaled
    except:
        risk_score = 0.5
        
    response = {
        "status": "Scam Indexed Successfully",
        "extracted_intelligence": extracted,
        "campaign_risk_score": round(min(risk_score, 1.0), 2),
        "cluster_size": len(nx.node_connected_component(G, caller_phone)) if caller_phone in G else 1
    }
    
    print(f"🚨 New Threat Indexed from {caller_phone}. Cluster size: {response['cluster_size']}")
    return jsonify(response)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001)
