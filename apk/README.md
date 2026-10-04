# `apk/` — Prebuilt APKs

APKs are committed here so team members can sideload and test without a full
Gradle build. **Read this before distributing any of them.**

---

## ⚠️ Signing status — none of these are production builds

Every APK in this folder is signed with the **public Android debug keystore**
(`CN=Android Debug`). That certificate ships inside every Android SDK.

This means **none of these files may be distributed** — not on Play, not on a
website, not to end users.

Because Android matches updates by **signing certificate rather than any
secret**, anyone who produces an APK with the identical certificate can install
it *over* this app silently, with no prompt. This app requests `RECORD_AUDIO`,
`READ_CALL_LOG` and `SEND_SMS`, and is aimed at elderly users — an update-hijack
here is a surveillance and financial-fraud vector.

`assembleRelease` therefore **refuses to run** unless a private keystore is
configured:

```powershell
$env:RAKSHAK_KEYSTORE          = "C:\keys\rakshak-release.keystore"
$env:RAKSHAK_KEYSTORE_PASSWORD = "<storepass>"
$env:RAKSHAK_KEY_ALIAS        = "<alias>"
$env:RAKSHAK_KEY_PASSWORD      = "<keypass>"
.\gradlew.bat :app:assembleRelease
```

The previously-shipped release APKs (v1.1.0, v1.2.0, v2.0, v2.1 `-Release`) were
**also** debug-signed before that guard existed. They are retained only for
archival comparison and must not be circulated.

---

## Build types

| Suffix | What it is | Size (v2.1) |
|---|---|---|
| `-Benchmark` | **Recommended for testing.** R8-shrunk, resource-shrunk, debug-signed. Same code as release, realistic size, no keystore needed. | **25.7 MB** arm64 |
| `-Debug` | Unminified, full symbols, very large. Use only when debugging or reading stack traces. | 45 MB arm64 / 88.9 MB universal |
| `-Release` | Minified and optimised. **Requires a private keystore** — will not build without one. | ~22 MB arm64 |

Prefer `-Benchmark` for demos and device testing. It is **71 % smaller** than the
matching debug build (25.7 MB vs 88.9 MB universal) because R8 obfuscation,
resource shrinking and dropping to two ABIs are enabled.

---

## Choosing an ABI

| Your device | File |
|---|---|
| Most modern Android phones (2017+) | `arm64-v8a` |
| Older / budget phones | `armeabi-v7a` |
| Unsure | `Universal` (larger, works everywhere) |

Universal APKs carry `arm64-v8a` **and** `armeabi-v7a`. Emulator ABIs
(`x86`, `x86_64`) are **excluded** from builds — they added ~34 MB each and only
serve emulators. If you need an x86_64 emulator build, pass
`-Pandroid.injected.build.abi=x86_64`.

---

## Versioning

Version comes from `app/build.gradle.kts` (`versionName = "2.2.0"`,
`versionCode = 6`). Filenames follow:

```
RakshakSetu-v<version>-<abi>-<BuildType>.apk
```

When rebuilding an existing filename, git will show it as *modified* rather
than *added* — that is expected and correct, and it keeps the folder from
filling up with near-duplicates.

---

## v2.2.0 — the current release

**Download `RakshakSetu-v2.2-arm64-v8a-Benchmark.apk` (25.9 MB)** unless you have
a reason not to.

### What changed vs v2.1

Three screens previously looked like scanners but did not inspect anything. They
are now real:

| Screen | Was | Now |
|---|---|---|
| Link & Phishing Checker | substring match on `kyc`/`otp`/`apk`; printed a **fabricated** "SSL Certificate: Valid TLS 1.3" without opening a socket | strict URL parse → 5-layer rule engine → live feeds (OpenPhish, urlscan.io, Google Safe Browsing) |
| QR Code Scanner | **never used the camera**; URL was hardcoded to `sancharsaathi.gov.in` and always reported "Verified Safe" | CameraX + ZXing decode, then payload classification (UPI collect / payment deep-link / URL / Wi-Fi) |
| Call Security | showed a progress bar then returned a **canned voice-clone result for every file**, including benign recordings | decodes the real audio and runs the actual on-device pipeline |

The Call Security bug was the most serious: it reported "94 % voice clone" for
any file a user picked. That fabricated result could also reach the **police
reporting dossier**, so all synthetic fallbacks were removed from production
code. Scenario fixtures still exist in `debug/FakePipelineEmitter.kt` for unit
tests, but are no longer reachable from the UI.

### New permissions

`CAMERA` was added for QR scanning. It is optional (`required="false"`), so the
app still installs on devices without a camera — only the QR scanner is affected.

### Verified vs unverified

Please read this before quoting the release:

| | |
|---|---|
| 258 unit tests pass | verified |
| URL + QR engines compile and package into the APK | verified |
| Live feed reachability (OpenPhish, urlscan.io) | verified 2026-10-02 |
| QR decode on physical hardware | **not verified** — no device available at build time |
| Two-device live calls | **not verified** — needs two devices + the signalling relay |
| Google Safe Browsing | **not verified** — requires an API key |

Test on a real device before presenting these APKs as working.