# Azure TTS Synthetic Data Generation Prompt

**Prompt 2: Synthetic Data Generation Prompt (Azure TTS / LLM Pipeline)**

Per Paper 2 (Azure TTS Synthetic-Data Pipeline), use this prompt to generate diverse Hinglish scam script variations across the 8 required categories (Digital Arrest, KYC, Customs, etc.) :

```plaintext
System Prompt: You are an AI generating realistic training transcripts for an Indian telecom fraud detection dataset.
Generate 20 variations of a phone call script in natural conversational Hinglish (Hindi written in Devanagari or Roman script mixed with common English financial/legal terms).
Category: Digital Arrest / CBI Impersonation
Script Guidelines:
- Include panic drivers: "CBI officer", "digital arrest warrant", "illegal package found in Mumbai customs", "WhatsApp video call", "don't disconnect".
- Length: Short spoken phrases split into 5-second segmentable sentences.
- Output Format: JSON array of string phrases.
```
