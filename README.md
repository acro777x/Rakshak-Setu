# RAKSHAK SETU (रक्षक सेतु)
### On-Device Telecom Scam Interceptor & Rapid Recovery Accelerator

---

## 🛡️ Executive Summary

Rakshak Setu analyzes native post-call recordings **100% on-device** (Whisper ASR → MiniLM Semantic Embeddings → Paper 3 A4 Voting), fires high-priority full-screen alerts within seconds of hang-up, auto-builds government-compliant legal evidence dossiers (NCRP, Chakshu, 1930 Helpline, and Bank Freeze templates), and protects users offline without cloud dependency.

---

## 📥 Installation

### Option 1: Direct APK Download (Recommended)
1. Download **`RakshakSetu-v1.1.0.apk`** from [Releases](../../releases/latest) or from the repository `apk/` directory.
2. Open the APK with your phone's File Manager / Downloads app.
3. Tap **"Install"** (Allow *"Install from unknown sources"* if prompted).

### Option 2: Install via ADB
```bash
adb install -r apk/RakshakSetu-v1.1.0.apk
```

### Option 3: Build & Sign from Source (Windows)
```bash
git clone https://github.com/acro777x/Rakshak-Setu.git
cd Rakshak-Setu\RakshakSetu
.\build-release-apk.bat
```

---

## 🔧 Troubleshooting Manual Installation

| Problem | Cause | Solution |
|---|---|---|
| **"App not installed" / Conflict** | Previous version with different signature | `adb uninstall com.rakshaksetu.app` then install the new APK |
| **"Blocked by Play Protect"** | Google Play Protect warning on sideload | Tap *"Install anyway"* |
| **Unknown Sources Blocked** | File manager lacks installation permission | Settings → Apps → (Your File Manager) → *"Install unknown apps"* → Allow |
| **Android 14/15 Compatibility** | Strict V2/V3 signature requirement | Our APK is pre-signed with V2+V3 schemes and 4-byte zipaligned |

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

- **Compiler:** Android Gradle Plugin 8.5.0 + Kotlin 2.0.0
- **UI Toolkit:** Jetpack Compose Material3
- **Unit Tests:** 35 Unit Tests across 6 suites (0 Failures)
- **Real-Device Verification:** Verified on **TECNO LJ8 (Android 15)**