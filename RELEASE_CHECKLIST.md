# Audio Factory V1.1.0 — Privacy & Release Readiness Checklist

This document tracks pre-release verifications for publishing Audio Factory V1.1.0 (versionCode 2) on Google Play.

---

## 1. Google Play Policy & Version Verification

- [x] **Package ID & Namespace**: `com.audiofactory.app`
- [x] **Version Name**: `1.1.0`
- [x] **Version Code**: `2`
- [x] **App Title Compliance**: `Audio Factory` (13 characters, no promotional claims or emojis).
- [x] **Zero-Permission Media & Storage**: No `READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`. Modern Scoped Storage and system file pickers are used exclusively.
- [x] **HTTPS Enforcement**: Network security config strictly disables cleartext traffic (`cleartextTrafficPermitted="false"`).
- [x] **Origin Isolation**: Navigation restricted to first-party origins (`audiofactory.vercel.app`, `audiofactory.app`).
- [x] **SSL / TLS Integrity**: SSL error handler cancels connections upon any certificate anomaly.
- [x] **Microphone Rationale**: `RECORD_AUDIO` gated behind contextual dialog before requesting system permissions.
- [x] **Target SDK**: Configured for `targetSdk = 36` (Android 16), compliant with Google Play Console mandates.

---

## 2. Technical Quality & Robustness

- [x] **Back Navigation**: Hardware back button pops WebView navigation stack before showing exit confirmation.
- [x] **State Persistence**: Network observer detects connectivity loss and triggers automatic reconnection upon recovery.
- [x] **File Chooser Safety**: WebChromeClient cancellations return null callbacks to prevent UI thread locks.
- [x] **Renderer Crash Recovery**: `onRenderProcessGone` handles web process crashes without killing the native app.
- [x] **Signature Verification**: Both `AudioFactory-V1.1.0.apk` and `AudioFactory-V1.1.0.aab` verified with `apksigner` and `jarsigner`.
- [x] **Checksum Integrity**: SHA256 checksums generated and attached to release assets.

---

## 3. GitHub Actions Release Automation

- [x] **Secrets Configured**: `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`.
- [x] **Keystore Security**: Decoded to `$RUNNER_TEMP/release.jks` with `chmod 600` and purged in `always()` cleanup step.
- [x] **Release Workflow**: `.github/workflows/release.yml` triggers on tag `v*` and automatically creates GitHub Releases.
