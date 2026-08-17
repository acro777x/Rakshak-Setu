# RAKSHAK SETU (रक्षक सेतु)
### On-Device Telecom Scam Interceptor & Rapid Recovery Accelerator

---

## 🛡️ Executive Summary

Rakshak Setu analyzes native post-call recordings **100% on-device** (Whisper ASR → MiniLM Semantic Embeddings → Paper 3 A4 Voting), fires high-priority full-screen alerts within seconds of hang-up, auto-builds government-compliant legal evidence dossiers (NCRP, Chakshu, 1930 Helpline, and Bank Freeze templates), and protects users offline without cloud dependency.

---

## 📥 Installation & Downloads

### 📱 Choose Your Architecture
Rakshak Setu uses ABI Split APKs to provide the fastest, smallest, and most battery-efficient binary for your device:

1. **For 95%+ Modern Phones (Samsung, Xiaomi, Realme, Vivo, Tecno, OnePlus, Pixel 2020+):**
   - Download **[`RakshakSetu-v1.2.0-arm64-v8a.apk`](apk/RakshakSetu-v1.2.0-arm64-v8a.apk)**
2. **For Older 32-Bit Phones:**
   - Download **[`RakshakSetu-v1.2.0-armeabi-v7a.apk`](apk/RakshakSetu-v1.2.0-armeabi-v7a.apk)**

---

## 🔧 Golden Rule for Testers (Avoid "App Not Installed")

If installing manually from WhatsApp, Chrome, or File Manager:
1. **Uninstall any previous version** of Rakshak Setu from your phone.
2. Go to **Settings → Apps → Special App Access → Install Unknown Apps** → Allow it for your browser/file manager.
3. Open and install **`RakshakSetu-v1.2.0-arm64-v8a.apk`**.

---

### Option 2: Install via ADB (Developer)
```bash
adb install -r apk/RakshakSetu-v1.2.0-arm64-v8a.apk
```

### Option 3: Build & Sign Split APKs from Source (Windows)
```bash
git clone https://github.com/acro777x/Rakshak-Setu.git
cd Rakshak-Setu\RakshakSetu
.\build-release-apk.bat
```

---

## 🏗️ System Architecture

```
[ Incoming / Outgoing Call ]
            │
            ▼
[ Call Ends: IDLE State ]
            │
            ▼
[ On-Device AI Engine (Whisper + Semantic Voting) ]
            │
            ▼
[ DetectionResult JSON Contract ]
            │
            ├──────────────────────────┬──────────────────────────┐
            ▼                          ▼                          ▼
[ High-Priority Red Alert ]  [ NCRP Evidence Dossier ]  [ Multi-OEM Battery Guard ]
   - 1930 Emergency Dial        - Statement Generator      - 60s Max WakeLock
   - Bank Freeze Email          - Flagged Keywords View    - WorkManager Sync
   - Chakshu Portal Open        - Clipboard Auto-Copy      - LRU Memory Cache
```

---

## 🧪 Build & Test Verification

- **Package Validation:** `android:extractNativeLibs="true"` (No Package Manager crash)
- **Signature Schemes:** V1 (Jar) + V2 (APK v2) + V3 (APK v3) + Zipalign 4-byte
- **Compiler:** Android Gradle Plugin 8.5.0 + Kotlin 2.0.0 + Jetpack Compose Material3
- **Unit Tests:** 35 Unit Tests across 6 suites (0 Failures)
- **Real-Device Verification:** Verified on **TECNO LJ8 (Android 15)**