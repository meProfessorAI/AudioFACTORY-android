# Audio Factory — Privacy & Release Readiness Checklist

This document tracks all pre-release verifications for publishing Audio Factory on Google Play.

---

## 1. Google Play Policy Compliance

- [x] **App Title Compliance**: `Audio Factory` is 13 characters (< 30 characters), no prohibited words (e.g. "Free", "Best", "#1").
- [x] **Zero-Permission Media & Storage**: No broad storage permissions (`READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`). Scoped storage and system file pickers are used exclusively.
- [x] **HTTPS Enforcement**: Network security config strictly disables cleartext traffic (`cleartextTrafficPermitted="false"`).
- [x] **Origin Isolation**: Navigation is restricted to trusted first-party domains (`audiofactory.vercel.app`, `audiofactory.app`).
- [x] **SSL / TLS Integrity**: SSL error handlers cancel connections upon certificate anomalies; no certificate bypass.
- [x] **Sensitive Permission Safeguard**: Microphone (`RECORD_AUDIO`) is gated behind user rationale dialog and only granted when the user explicitly triggers voice recording.
- [x] **Target SDK**: Configured for `targetSdk = 36` (Android 16), compliant with latest Google Play API requirements.
- [x] **No Dynamic Code Loading**: No external `.dex`, `.jar`, or remote scripts loaded outside sandboxed WebView context.

---

## 2. Technical Quality & Robustness

- [x] **Back Navigation**: Hardware back button pops WebView navigation stack before prompting the exit confirmation dialog.
- [x] **State Persistence & Reconnection**: Real-time network callback detects offline events and triggers auto-reconnect on recovery.
- [x] **File Chooser Callbacks**: All file chooser cancellations return `null` to `filePathCallback` to prevent UI freezing.
- [x] **Renderer Crash Handling**: `onRenderProcessGone` handled safely, preventing native crashes if web rendering engine runs out of memory.
- [x] **Resource Cleanup**: Proper lifecycle teardown of WebView instances, receivers, and callback listeners in `onDestroy`.
- [x] **Adaptive Launcher Icon**: Custom high-resolution vector and raster icons configured across all density buckets (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) plus circular masks.

---

## 3. Production Keystore Setup (Google Play Console)

To generate the final `.aab` for production upload:
```bash
export KEYSTORE_PATH="/path/to/your/upload-keystore.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"

gradle :app:bundleRelease
```
The resulting upload artifact will be located at:
`app/build/outputs/bundle/release/app-release.aab`
