# Audio Factory — Android Production Application

Production-quality native Android client wrapping and integrating with the live [Audio Factory](https://audiofactory.vercel.app) web application.

---

## 1. Project Specifications

| Attribute | Value |
| :--- | :--- |
| **Application Name** | Audio Factory |
| **Release Version** | 1.1.0 |
| **Version Code** | 3 |
| **Namespace** | `com.audiofactory.app` |
| **Application ID** | `com.audiofactory.app` |
| **Production Target URL** | `https://audiofactory.vercel.app` |
| **Minimum SDK** | API 24 (Android 7.0 Nougat) |
| **Target SDK** | API 36 (Android 16) |
| **UI Framework** | Jetpack Compose + Material Design 3 |
| **JDK Version** | Java 21 |
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

## 3. Building & Releasing Locally

### Debug Build
```bash
./gradlew assembleDebug
```

### Production Release Build (APK & AAB)
```bash
export KEYSTORE_PATH="/path/to/upload-keystore.jks"
export STORE_PASSWORD="your-store-password"
export KEY_PASSWORD="your-key-password"
export KEY_ALIAS="upload"

./gradlew assembleRelease bundleRelease --stacktrace
```

---

## 4. GitHub Actions CI/CD Pipeline

Two automated GitHub Actions workflows are configured in `.github/workflows/`:

### 1. CI Validation Workflow (`.github/workflows/build.yml`)
- **Triggers**: On pull request or push to `main`/`master`.
- **Function**: Executes unit tests, compiles debug APK, and validates code integrity. Never uses production release signing secrets.

### 2. Release Pipeline Workflow (`.github/workflows/release.yml`)
- **Triggers**: On pushing version tags (`v*`), release publication, or via manual `workflow_dispatch`.
- **Function**:
  - Restores and verifies `KEYSTORE_BASE64` signing material.
  - Executes unit tests.
  - Builds production `AudioFactory-V1.1.0.apk` and `AudioFactory-V1.1.0.aab`.
  - Performs `apksigner` and `jarsigner` signature verification.
  - Verifies package metadata (`com.audiofactory.app`, `versionCode=2`, `versionName=1.1.0`) via `aapt`.
  - Generates `SHA256SUMS.txt`.
  - Automatically publishes a GitHub Release with versioned artifacts and installation/upload instructions.
  - Cleans up temporary signing files from the runner in an `always()` step.

### Configured GitHub Repository Secrets
- `KEYSTORE_BASE64`: Base64-encoded JKS signing keystore.
- `STORE_PASSWORD`: Keystore password.
- `KEY_PASSWORD`: Private key password.
- `KEY_ALIAS`: Key alias (`"upload"`).
