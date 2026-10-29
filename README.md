# Audio Factory — Android Production Application

Production-quality native Android client wrapping and integrating with the live [Audio Factory](https://audiofactory.vercel.app) web application.

---

## 1. Project Specifications

| Attribute | Value |
| :--- | :--- |
| **Application Name** | Audio Factory |
| **Namespace** | `com.audiofactory.app` |
| **Application ID** | `com.audiofactory.app` |
| **Production Target URL** | `https://audiofactory.vercel.app` |
| **Minimum SDK** | API 24 (Android 7.0 Nougat) |
| **Target SDK** | API 36 (Android 16) |
| **UI Framework** | Jetpack Compose + Material Design 3 |
| **Rendering Engine** | Android WebView with strict origin confinement |
| **Architecture** | MVVM / Unidirectional State Flow with lifecycle awareness |

---

## 2. Technical Architecture & Components

The application is structured into isolated, maintainable components:

1. **Application Entry & Lifecycle (`MainActivity.kt`, `AudioFactoryApp.kt`)**:
   - Manages Android edge-to-edge window insets (`enableEdgeToEdge()`).
   - Handles modern Activity Result contracts for file selection and audio permissions.
   - Cleans up WebView allocations, listeners, and history safely on `onDestroy()`.
   - Supports background pausing/resuming of audio processing.

2. **WebView Host & Security Controller (`AudioFactoryWebView.kt`, `AudioFactoryWebPolicy.kt`)**:
   - Confines navigation strictly to first-party origins (`audiofactory.vercel.app`, `audiofactory.app`).
   - Disables file scheme access (`allowFileAccess = false`, `allowContentAccess = false`).
   - Rejects mixed content (`MIXED_CONTENT_NEVER_ALLOW`).
   - Enforces strict TLS: cancels connection upon any SSL/certificate mismatch.
   - Routes external action schemes (`mailto:`, `tel:`) to validated system intents.
   - Handles WebView renderer process recovery (`onRenderProcessGone`) without crashing the application.

3. **Audio & Media Permissions (`AudioFactoryMainScreen.kt`, `AudioFactoryDialogs.kt`)**:
   - Contextual runtime permission request for `Manifest.permission.RECORD_AUDIO`.
   - Explains microphone usage for voice cloning samples prior to requesting system dialog.
   - Media playback configured without requiring synthetic user gesture delays (`mediaPlaybackRequiresUserGesture = false`).

4. **File Upload & Audio Downloads (`AudioFactoryDownloadManager.kt`)**:
   - File input integration via `WebChromeClient.onShowFileChooser`.
   - Supports single and multi-file selection of audio (`audio/*`) and document assets.
   - Handles user cancellation gracefully without locking WebView callbacks.
   - Downloads exported `.wav`, `.mp3`, and audio files directly to the public `Downloads` directory via Android's `DownloadManager` or Scoped Storage (`MediaStore.Downloads`).
   - Supports Base64/Data URLs and client-side blob exports.

5. **Connectivity & Offline Recovery (`NetworkConnectivityObserver.kt`, `ErrorAndOfflineView.kt`)**:
   - Real-time network state monitoring via `ConnectivityManager.NetworkCallback`.
   - Polished native error recovery screen with diagnostics for offline state, DNS failures, and HTTP errors.
   - Auto-reconnect and manual retry actions.

6. **Branded Visuals & M3 Design System**:
   - Custom adaptive launcher icons with dark studio palette and soundwave equalizer.
   - Animated studio loading indicator with pulsing equalizer bars.
   - Dark studio color palette (`#0A0C10` background, `#13161F` surface, `#4285F4` electric blue accent).

---

## 3. Building & Releasing

### Debug Build
```bash
gradle :app:assembleDebug
```

### Release Android App Bundle (.aab)
```bash
gradle :app:bundleRelease
```

### Keystore Configuration
For Google Play production uploads, configure your signing credentials via environment variables:
- `KEYSTORE_PATH`: Path to your `.jks` release keystore
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key alias password

---

## 4. GitHub Actions CI/CD Automation

Two automated GitHub Actions workflows are included in `.github/workflows/`:

### 1. Build Workflow (`.github/workflows/build.yml`)
- **Triggers**: On push or pull request to `main`/`master`, or via manual trigger (`workflow_dispatch`).
- **Jobs**:
  - Sets up JDK 21 and Gradle cache.
  - Executes unit and Robolectric tests (`./gradlew testDebugUnitTest`).
  - Assembles Debug APK (`./gradlew assembleDebug`).
  - Packages Android App Bundle (`./gradlew bundleDebug` or `./gradlew bundleRelease`).
  - Publishes `audio-factory-debug-apk` and `audio-factory-app-bundle` as downloadable build artifacts (retained for 30 days).

### 2. Release Workflow (`.github/workflows/release.yml`)
- **Triggers**: When pushing tags starting with `v*` (e.g. `v1.0.0`) or via manual trigger.
- **Jobs**:
  - Compiles the APK and production AAB.
  - Generates SHA256 integrity checksums.
  - Publishes a formal GitHub Release with downloadable APK and AAB attachments.

### GitHub Repository Secrets (Optional for Production Release Signing)
To enable production signed `.aab` bundles in GitHub Actions, configure these repository secrets under **Settings > Secrets and variables > Actions**:
- `KEYSTORE_BASE64`: Base64 string of your `.jks` or `.keystore` file (`base64 -w 0 my-upload-key.jks`).
- `STORE_PASSWORD`: Password for the keystore.
- `KEY_PASSWORD`: Password for the key alias.
*(If secrets are not provided, the workflows automatically build and package signed debug bundles without failing).*

