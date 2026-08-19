import hashlib
import json
import uuid
import datetime

print("==================================================")
print("⚖️ Forensic Evidence Packager (NCRP 1930 Support)")
print("==================================================")

def generate_file_hash(filepath):
    """ Generate SHA-256 hash of a file for cryptographic evidence preservation. """
    hasher = hashlib.sha256()
    try:
        with open(filepath, 'rb') as f:
            buf = f.read()
            hasher.update(buf)
        return hasher.hexdigest()
    except Exception as e:
        return str(e)

def create_evidence_package(caller_number, audio_path, transcript, extracted_intel):
    """ Creates a JSON package ready for Cybercrime portal upload. """
    case_id = f"CYBER-{uuid.uuid4().hex[:8].upper()}"
    timestamp = datetime.datetime.now().isoformat()
    
    audio_hash = generate_file_hash(audio_path)
    
    # Hash the transcript as well
    transcript_hash = hashlib.sha256(transcript.encode('utf-8')).hexdigest()
    
    report = {
        "CaseID": case_id,
        "Timestamp": timestamp,
        "SuspectNumber": caller_number,
        "ExtractedLeads": extracted_intel,
        "Evidence": {
            "AudioFile": audio_path,
            "AudioSHA256": audio_hash,
            "Transcript": transcript,
            "TranscriptSHA256": transcript_hash
        },
        "Disclaimer": "This evidence package is cryptographically hashed to preserve chain of custody for 1930 reporting."
    }
    
    report_path = f"training/Evidence_{case_id}.json"
    with open(report_path, 'w') as f:
        json.dump(report, f, indent=4)
        
    print(f"✅ Cryptographic Evidence Package generated: {report_path}")
    return report_path

# Example Usage Test
if __name__ == "__main__":
    dummy_audio = "training/dummy_audio.wav"
    with open(dummy_audio, "wb") as f: f.write(b"dummy data")
    
    create_evidence_package(
        caller_number="+919876543210",
        audio_path=dummy_audio,
        transcript="Hello, please transfer 5000 to fraud@upi immediately. I am from CBI.",
        extracted_intel={"upi_ids": ["fraud@upi"], "orgs": ["CBI"]}
    )
