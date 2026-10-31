# AudioFACTORY Android — v2.0.0 (Version Code 6) Release Engineering & Compliance Audit Report

## 1. Executive Summary

This report documents the comprehensive release engineering, security, dependency, and Google Play compliance audit for **AudioFACTORY Android v2.0.0 (`versionCode = 6`)**, package ID `com.audiofactory.app`.

### Key Outcomes
- **Version Configuration**: Incremented `versionCode` from `5` (previously uploaded to Google Play Console) to **`6`** while preserving `versionName = "2.0.0"`, `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36.1`, AGP `9.1.1`, Kotlin `2.2.10`, Gradle `9.3.1`, and Java `21`.
- **Issue A (Google Play Advertising ID Warning)**: Audited all source manifests, merged debug/release manifests, packaged AAB manifests, and the entire `releaseRuntimeClasspath` dependency graph. Confirmed that **neither AudioFACTORY nor any of its transitive dependencies uses the Google Play Advertising ID** (`com.google.android.gms.permission.AD_ID` is absent from all manifests and no AdMob, Firebase Analytics, or `play-services-ads-identifier` SDK is included). In compliance with Google Play Least-Privilege policy, `AD_ID` was **not** added to `AndroidManifest.xml`; instead, the Google Play Console **Advertising ID** declaration under **App content** must be updated from **"Yes"** to **"No"**.
- **Issue B & C (Native Debug Symbols Warning & Native Dependency Audit)**: Extracted and inspected all 8 native `.so` binaries inside `app-release.aab` (`libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` across `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`). Traced their exact transitive origins to `androidx.graphics:graphics-path:1.0.1` (via Jetpack Compose `ui-graphics:1.7.0`) and `androidx.datastore:datastore-core-android:1.1.7` (via `com.google.firebase:firebase-common:22.2.0`). Verified via ELF section header inspection that Google publishes both AndroidX AARs to Google Maven with their `.so` binaries **already stripped** (`has_debug=False`, no `.debug_*` or `.symtab` sections). Because no unstripped DWARF debugging symbols exist in the upstream Google AARs, AGP's `extractReleaseNativeSymbolTables` / `mergeReleaseNativeDebugMetadata` tasks legitimately report `NO-SOURCE`. No fabricated or stripped `.so` archive was created.
- **Issue D & E (Gradle Build & GitHub Actions CI/CD)**: Configured Kotlin JVM target compatibility, out-of-process compiler execution strategy for CI runner stability, `.gitignore` secret exclusions (`app/google-services.json`, keystores, staging directories), and hardened `.github/workflows/build.yml` to verify AAB signatures (rejecting fallback debug certificates), assert `versionCode = 6` and `versionName = "2.0.0"` inside the compiled AAB protobuf manifest, and upload structured artifacts (`AudioFactory-debug.apk`, `AudioFactory-release-v2.0.0-v6.aab`, `mapping.txt`, test reports, and validation reports).

---

## 2. Original Problems

1. **Google Play Console Advertising ID Error/Warning**:
   > *"Your advertising ID declaration says that your app uses advertising ID. A manifest file in one of your active artifacts doesn't include the `com.google.android.gms.permission.AD_ID` permission."*
2. **Google Play Console Native Debug Symbols Warning**:
   > *"This App Bundle contains native code, and you've not uploaded debug symbols."*
3. **Version Code Collision**:
   Version code `5` (`2.0.0`) was already uploaded to Google Play Console and cannot be reused for subsequent uploads.
4. **CI/CD & Runtime Version Alignment**:
   - Hardcoded version strings in `AudioFactoryNativeBridge.kt` (`"1.1.0"`), `AudioFactoryWebView.kt` (`"1.1"`), and `AudioFactoryDialogs.kt` (`"Version 1.0 (Build 1)"`) were out of sync with `BuildConfig`.
   - `.gitignore` needed explicit exclusion for `app/google-services.json` and `/ci-artifacts/`.
   - `.github/workflows/build.yml` needed automated AAB protobuf manifest verification (`versionCode = 6`, `versionName = "2.0.0"`), anti-debug-certificate verification for release bundles, and dedicated artifact uploads for `mapping.txt` and validation reports.

---

## 3. Root Cause of Each Problem

| Problem | Root Cause |
| :--- | :--- |
| **Advertising ID Mismatch** | The Google Play Console **Advertising ID** declaration was set to **"Yes"** during initial Play Console setup, whereas the app only uses `firebase-auth`, `firebase-firestore`, and `androidx.credentials` (none of which use `AD_ID` or include `play-services-ads-identifier` / `firebase-analytics`). |
| **Missing Native Debug Symbols** | The release AAB contains two native libraries (`libandroidx.graphics.path.so` from `androidx.graphics:graphics-path:1.0.1` and `libdatastore_shared_counter.so` from `androidx.datastore:datastore-core-android:1.1.7`). Both are prebuilt C++ libraries shipped inside Google's official AndroidX AARs with ELF `.debug_*` and `.symtab` sections **already stripped by Google prior to Maven publication**. Consequently, `ndk { debugSymbolLevel = "FULL" }` has no DWARF debug sections to extract. |
| **Version Code 5 Rejection** | Google Play Console requires strictly monotonically increasing `versionCode` integers for every uploaded artifact. |
| **CI/CD Kotlin Compilation Failure** | In-process Kotlin compilation (`kotlin.compiler.execution.strategy=in-process`) without explicit `kotlin.compilerOptions.jvmTarget = JvmTarget.JVM_11` caused worker failures on JDK 21 runners. |

---

## 4. Files Inspected

- `build.gradle.kts` (Root)
- `settings.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradle/wrapper/gradle-wrapper.properties`
- `.gitignore` & `app/.gitignore`
- `.env.example`
- `metadata.json`
- `.github/workflows/build.yml`
- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/google-services.json`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/network_security_config.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/audiofactory/app/AudioFactoryApp.kt`
- `app/src/main/java/com/audiofactory/app/MainActivity.kt`
- `app/src/main/java/com/audiofactory/app/auth/AuthManager.kt`
- `app/src/main/java/com/audiofactory/app/bridge/AudioFactoryNativeBridge.kt`
- `app/src/main/java/com/audiofactory/app/data/NetworkConnectivityObserver.kt`
- `app/src/main/java/com/audiofactory/app/data/WebState.kt`
- `app/src/main/java/com/audiofactory/app/policy/AudioFactoryWebPolicy.kt`
- `app/src/main/java/com/audiofactory/app/service/AudioFactoryDownloadManager.kt`
- `app/src/main/java/com/audiofactory/app/ui/AudioFactoryMainScreen.kt`
- `app/src/main/java/com/audiofactory/app/ui/AudioFactoryWebView.kt`
- `app/src/main/java/com/audiofactory/app/ui/components/AudioFactoryDialogs.kt`
- `app/src/test/java/com/audiofactory/app/AudioFactoryRobolectricTest.kt`
- `app/src/test/java/com/audiofactory/app/AudioFactoryWebPolicyTest.kt`
- `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`
- `app/build/outputs/bundle/release/app-release.aab`
- `app/build/outputs/mapping/release/mapping.txt`

---

## 5. Files Modified

| File | Summary of Modification |
| :--- | :--- |
| `app/build.gradle.kts` | Set `versionCode = 6`, `versionName = "2.0.0"`, preserved `ndk { debugSymbolLevel = "FULL" }`, and configured `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }`. |
| `gradle.properties` | Set `kotlin.compiler.execution.strategy=out-of-process` for reliable JDK 21 CI runner process isolation. |
| `gradle/libs.versions.toml` | Added `kotlin-android` plugin reference entry in `[plugins]` catalog. |
| `.gitignore` | Added `app/google-services.json` and `/ci-artifacts/` while preserving all keystore and signing exclusions. |
| `.github/workflows/build.yml` | Added compiled AAB protobuf manifest verification (`versionCode=6`, `versionName=2.0.0`, package ID, absence of `AD_ID`), anti-debug-keystore signature enforcement, R8 `mapping.txt` artifact upload, conditional native debug symbols upload, and build validation report upload. |
| `app/src/main/java/com/audiofactory/app/bridge/AudioFactoryNativeBridge.kt` | Updated `getNativeAppVersion()` to return `BuildConfig.VERSION_NAME` (`"2.0.0"`). |
| `app/src/main/java/com/audiofactory/app/ui/AudioFactoryWebView.kt` | Updated custom User-Agent suffix to use `BuildConfig.VERSION_NAME` (`AudioFactoryAndroid/2.0.0`). |
| `app/src/main/java/com/audiofactory/app/ui/components/AudioFactoryDialogs.kt` | Updated `AboutDialog` version label to display `Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})`. |
| `app/src/test/java/com/audiofactory/app/AudioFactoryRobolectricTest.kt` | Added unit test asserting `BuildConfig.APPLICATION_ID == "com.audiofactory.app"`, `BuildConfig.VERSION_CODE == 6`, and `BuildConfig.VERSION_NAME == "2.0.0"`. |
| `AUDIOFACTORY_V2_RELEASE_AUDIT.md` | Created this comprehensive release engineering and compliance audit report. |

---

## 6. Advertising ID Investigation (Issue A)

### Permissions Present in Merged Release Manifest & AAB
Inspection of `app/src/main/AndroidManifest.xml`, `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`, and `base/manifest/AndroidManifest.xml` inside `app-release.aab` confirmed the exact set of `<uses-permission>` declarations:
1. `android.permission.INTERNET` (source manifest)
2. `android.permission.ACCESS_NETWORK_STATE` (source manifest)
3. `android.permission.RECORD_AUDIO` (source manifest)
4. `android.permission.MODIFY_AUDIO_SETTINGS` (source manifest)
5. `android.permission.WRITE_EXTERNAL_STORAGE` (`android:maxSdkVersion="28"`, source manifest)
6. `android.permission.USE_BIOMETRIC` (merged from `androidx.biometric:biometric:1.1.0` via `androidx.credentials:credentials:1.5.0`)
7. `android.permission.USE_FINGERPRINT` (merged from `androidx.biometric:biometric:1.1.0`)
8. `com.google.android.providers.gsf.permission.READ_GSERVICES` (merged from `com.google.android.gms:play-services-basement:18.9.0`)
9. `com.audiofactory.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (merged from `androidx.core:core:1.18.0`)

### Conclusion on `com.google.android.gms.permission.AD_ID`
- **Is `AD_ID` present?** No.
- **Does any dependency access Advertising ID?** No. The `releaseRuntimeClasspath` contains zero advertising, analytics, attribution, or measurement SDKs (`firebase-analytics`, `play-services-ads`, and `play-services-ads-identifier` are not used).
- **Compliance Action**: Do **not** add `com.google.android.gms.permission.AD_ID` to `AndroidManifest.xml`. In Google Play Console, navigate to **Policy and programs → App content → Advertising ID** and change the declaration to **"No"** (the app does not use Advertising ID).

---

## 7. Native Library Inventory (Issue B)

Inspection of `app/build/outputs/bundle/release/app-release.aab` revealed **8 native `.so` files** across **4 ABIs**:

| Path in Release AAB | ABI | Size (Bytes) | ELF BuildID (SHA-1) | Originating Dependency | Prebuilt or Compiled | ELF Debug Sections (`.debug_*` / `.symtab`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `base/lib/arm64-v8a/libandroidx.graphics.path.so` | `arm64-v8a` | 10,096 | `a7846ae06d6bf6cf63fa7c5e056b5de493c71567` | `androidx.graphics:graphics-path:1.0.1` | Prebuilt (Google Maven AAR) | None (**Stripped upstream**) |
| `base/lib/armeabi-v7a/libandroidx.graphics.path.so` | `armeabi-v7a` | 7,252 | `fa98a997cdd9b43f190a4bd20f3dad441b4062f0` | `androidx.graphics:graphics-path:1.0.1` | Prebuilt (Google Maven AAR) | None (**Stripped upstream**) |
| `base/lib/x86/libandroidx.graphics.path.so` | `x86` | 9,284 | `56b3f97a7efb05fc89674cf68f6c27847a93860b` | `androidx.graphics:graphics-path:1.0.1` | Prebuilt (Google Maven AAR) | None (**Stripped upstream**) |
| `base/lib/x86_64/libandroidx.graphics.path.so` | `x86_64` | 10,760 | `f3e82b0a78f4c567cca77fe6c132199d806987d1` | `androidx.graphics:graphics-path:1.0.1` | Prebuilt (Google Maven AAR) | None (**Stripped upstream**) |
| `base/lib/arm64-v8a/libdatastore_shared_counter.so` | `arm64-v8a` | 7,112 | `17db37bd6770ac00dd2d1d2828839fd23a7959a3` | `androidx.datastore:datastore-core-android:1.1.7` | Prebuilt (Google Maven AAR) | None (**Stripped upstream**) |
| `base/lib/armeabi-v7a/libdatastore_shared_counter.so` | `armeabi-v7a` | 4,416 | `c2c541cc4337a0fc28271329ef238d6bddf6d70d` | `androidx.datastore:datastore-core-android:1.1.7` | Prebuilt (Google Maven AAR) | None (**Stripped upstream**) |
| `base/lib/x86/libdatastore_shared_counter.so` | `x86` | 5,148 | `cf60dc0a5664876a021857e4948f6798ee0ca150` | `androidx.datastore:datastore-core-android:1.1.7` | Prebuilt (Google Maven AAR) | None (**Stripped upstream**) |
| `base/lib/x86_64/libdatastore_shared_counter.so` | `x86_64` | 6,224 | `c6ef0802be059f5f59b23f1205d040dbc5b1ccad` | `androidx.datastore:datastore-core-android:1.1.7` | Prebuilt (Google Maven AAR) | None (**Stripped upstream**) |

---

## 8. Native Dependency Tree (Issue C)

### 1. `libandroidx.graphics.path.so` (`androidx.graphics:graphics-path:1.0.1`)
```text
releaseRuntimeClasspath
\--- androidx.compose.ui:ui-graphics:1.7.0 (via androidx.compose:compose-bom:2024.09.00)
     \--- androidx.compose.ui:ui-graphics-android:1.7.0
          \--- androidx.graphics:graphics-path:1.0.1
```

### 2. `libdatastore_shared_counter.so` (`androidx.datastore:datastore-core-android:1.1.7`)
Even though `libs.androidx.datastore.preferences` is commented out in `app/build.gradle.kts`, Firebase's core SDK (`firebase-common`) transitively depends on `androidx.datastore:datastore-preferences:1.1.7`:
```text
releaseRuntimeClasspath
+--- com.google.firebase:firebase-auth:24.2.0 (via firebase-bom:34.17.0)
|    \--- com.google.firebase:firebase-common:22.2.0
|         \--- androidx.datastore:datastore-preferences:1.1.7
|              \--- androidx.datastore:datastore-preferences-android:1.1.7
|                   \--- androidx.datastore:datastore:1.1.7
|                        \--- androidx.datastore:datastore-android:1.1.7
|                             \--- androidx.datastore:datastore-core:1.1.7
|                                  \--- androidx.datastore:datastore-core-android:1.1.7
\--- com.google.firebase:firebase-firestore:26.5.0 (via firebase-bom:34.17.0)
     \--- com.google.firebase:firebase-common:22.2.0 (...)
```

### Version Catalog Audit (`gradle/libs.versions.toml`)
- **Active in `releaseRuntimeClasspath`**:
  - `androidx.compose:compose-bom:2024.09.00` (`ui`, `ui-graphics`, `material3`, `material-icons-core`, `material-icons-extended`)
  - `com.google.firebase:firebase-bom:34.17.0` (`firebase-auth:24.2.0`, `firebase-firestore:26.5.0`)
  - `androidx.credentials:credentials:1.5.0` & `credentials-play-services-auth:1.5.0`
  - `com.google.android.libraries.identity.googleid:googleid:1.1.1`
  - `androidx.webkit:webkit:1.12.1`
  - `androidx.core:core-ktx:1.18.0`, `androidx.activity:activity-compose:1.10.1`, `androidx.lifecycle:*:2.8.7`, `kotlinx-coroutines-*:1.10.2`
- **Commented Out (Not in `releaseRuntimeClasspath`)**:
  - CameraX `1.5.0`, Play Services Location `21.3.0`, Room `2.7.0`, Retrofit/OkHttp/Moshi, Coil `2.7.0`, Navigation Compose `2.8.9`, Accompanist Permissions `0.37.3`.

---

## 9. Native Symbols Generation Results (Issue B)

- **Configuration**: `ndk { debugSymbolLevel = "FULL" }` is active in `app/build.gradle.kts` under `buildTypes.release`.
- **Execution**: During `gradle bundleRelease`, `:app:extractReleaseNativeSymbolTables` is `UP-TO-DATE` and `:app:mergeReleaseNativeDebugMetadata` reports `NO-SOURCE`.
- **Technical Reason**: ELF section analysis of `app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/**.so` (prior to stripping) confirmed that all 8 `.so` files contain only `.dynsym`, `.dynstr`, `.eh_frame`, and `.eh_frame_hdr`, and **zero `.debug_*` or `.symtab` sections**. Because the upstream publisher (**Google AndroidX / Jetpack**) strips `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` before publishing `androidx.graphics:graphics-path:1.0.1` and `androidx.datastore:datastore-core-android:1.1.7` to Google's Maven repository, original DWARF debugging symbols are not distributed to downstream app builds.
- **Compliance Policy**: In accordance with strict engineering rules, no archive of stripped `.so` files was fabricated. The Google Play Console notice *"This App Bundle contains native code, and you've not uploaded debug symbols"* is a benign, non-blocking informational warning when using Jetpack Compose and Firebase/DataStore.

---

## 10. Gradle Configuration Changes (Issue D)

- **`app/build.gradle.kts`**:
  - `compileSdk { version = release(36) { minorApiLevel = 1 } }`
  - `minSdk = 24`, `targetSdk = 36`
  - `versionCode = 6`, `versionName = "2.0.0"`
  - `isMinifyEnabled = true`, `isShrinkResources = true`, `isCrunchPngs = true`
  - `ndk { debugSymbolLevel = "FULL" }`
  - `compileOptions` set to `JavaVersion.VERSION_11`
  - `kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }`
  - Preserved `signingConfigs` (`release` and `debugConfig`) and `buildTypes` assignments.
- **`gradle.properties`**:
  - Configured `kotlin.compiler.execution.strategy=out-of-process` to prevent in-process worker crashes on JDK 21 CI runners.

---

## 11. GitHub Actions Changes (Issue E)

Updated `.github/workflows/build.yml`:
1. Uses `actions/checkout@v4`, `actions/setup-java@v5` (Temurin JDK 21), `android-actions/setup-android@v4` (`platform-tools platforms;android-36.1 build-tools;36.0.0` with `accept-android-sdk-licenses: true`), and `gradle/actions/setup-gradle@v4`.
2. Restores `app/google-services.json` from `GOOGLE_SERVICES_JSON_BASE64` and validates `project_id == "audiofactory-eia"`, `package_name == "com.audiofactory.app"`, and presence of the Type-3 Web OAuth client.
3. Restores `my-upload-key.jks` from `KEYSTORE_BASE64` and verifies credentials (`STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`) using `keytool -list` before building.
4. Executes `./gradlew testDebugUnitTest --stacktrace` and `./gradlew --no-daemon assembleDebug bundleRelease --stacktrace`.
5. Verifies `app-debug.apk` with `apksigner` and `app-release.aab` with `jarsigner -verify -verbose -certs`, failing the build if `CN=Android Debug` appears in the release AAB certificate chain.
6. Verifies the compiled protobuf manifest (`base/manifest/AndroidManifest.xml`) inside `app-release.aab` for `com.audiofactory.app`, `versionCode = 6`, `versionName = "2.0.0"`, absence of `AD_ID`, and presence of `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`.
7. Uploads distinct artifacts:
   - `AudioFACTORY-Android-Builds` (`AudioFactory-debug.apk`, `AudioFactory-release-v2.0.0-v6.aab`, `AudioFactory-release.aab`)
   - `AudioFACTORY-R8-Mapping` (`mapping.txt`)
   - `AudioFACTORY-Native-Debug-Symbols` (if `native-debug-symbols.zip` is generated)
   - `AudioFACTORY-Build-Validation-Report` (`build-validation-report.md`, `AUDIOFACTORY_V2_RELEASE_AUDIT.md`)
   - `AudioFACTORY-Test-Reports` (`app/build/reports/tests/`)
8. Cleans up `app/google-services.json`, `my-upload-key.jks`, and temporary verification logs in an `if: always()` step.

---

## 12. Firebase & Google Authentication Validation (Issue F)

- **Firebase Project ID**: `audiofactory-eia` (Project Number `312442501608`)
- **Android Package Name**: `com.audiofactory.app` (`mobilesdk_app_id`: `1:312442501608:android:9680b0eba4a58e2d71cc5d`)
- **Web OAuth 2.0 Client ID (Type 3)**: `312442501608-b763vhutie4ilujl3itl7ujdig7u645b.apps.googleusercontent.com` (automatically injected into `R.string.default_web_client_id` by `com.google.gms.google-services` plugin and used by `AuthManager.kt` via `GetGoogleIdOption` and `GetSignInWithGoogleOption`).
- **Important Requirement for Google Play App Signing**:
  When Google Play App Signing re-signs the uploaded AAB for distribution to end users, both the **Upload key certificate fingerprints (SHA-1 & SHA-256)** and the **Google Play App Signing key certificate fingerprints (SHA-1 & SHA-256)** (found in **Google Play Console → Test and release → Setup → App signing**) must be registered in **Firebase Console → Project settings → Your apps → `com.audiofactory.app` → SHA certificate fingerprints**, and an updated `google-services.json` should be stored in `GOOGLE_SERVICES_JSON_BASE64`.

---

## 13. Security Audit (Issue G)

- **Exported Components**: Only `com.audiofactory.app.MainActivity` is exported (`android:exported="true"`) with `MAIN/LAUNCHER` and HTTPS deep-link `<intent-filter>` entries for `audiofactory.vercel.app`, `audiofactory.app`, and `audiofactory-eia.firebaseapp.com`. All merged Firebase, Credential Manager, and AndroidX Startup providers/services have `android:exported="false"` (or require Google Play Services signature permissions).
- **WebView Trusted Origins (`AudioFactoryWebPolicy.kt`)**:
  - `TRUSTED_HOSTS` strictly restricts first-party navigation, microphone access (`PermissionRequest.RESOURCE_AUDIO_CAPTURE`), and Google ID token injection (`AuthManager.sendTokenToWeb`) to `audiofactory.vercel.app`, `audiofactory.app`, `audiofactory-eia.firebaseapp.com`, and `audiofactory-eia.web.app`.
  - Disallowed schemes (`javascript:`, `file:`, `content:`) are blocked.
  - `allowFileAccess = false`, `allowContentAccess = false`, `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`.
  - `onReceivedSslError` strictly calls `handler?.cancel()` without bypass.
  - `WebView.setWebContentsDebuggingEnabled` is strictly gated on `BuildConfig.DEBUG`.
- **Network Security Config (`network_security_config.xml`)**: Disallows cleartext traffic (`cleartextTrafficPermitted="false"`).
- **ProGuard / R8 (`proguard-rules.pro`)**: Preserves `SourceFile,LineNumberTable` for Play Console stack trace deobfuscation and keeps `@JavascriptInterface` methods on `AudioFactoryNativeBridge`.

---

## 14. Unit Test Results

Executed `./gradlew testDebugUnitTest --stacktrace`:
- `AudioFactoryRobolectricTest`:
  - `web ui initial state is configured correctly` — **PASSED**
  - `error state diagnostics map accurately` — **PASSED**
  - `download manager initializes without errors` — **PASSED**
  - `release build config matches v2_0_0 version code 6 source of truth` — **PASSED**
- `AudioFactoryWebPolicyTest`:
  - `trusted origin returns true for official audio factory domains` — **PASSED**
  - `trusted origin rejects third-party and malicious domains` — **PASSED**
  - `external action schemes identified correctly` — **PASSED**
  - `disallowed schemes are rejected by safe scheme check` — **PASSED**
  - `start url points to production Audio Factory deployment` — **PASSED**

---

## 15. Release Build & R8 Mapping Results

Executed `./gradlew assembleDebug bundleRelease --stacktrace`:
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk` (staged to `release-artifacts/AudioFactory-debug.apk`)
- **Release AAB**: `app/build/outputs/bundle/release/app-release.aab` (staged to `release-artifacts/AudioFactory-release-v2.0.0-v6.aab`)
- **R8 Mapping**: `app/build/outputs/mapping/release/mapping.txt` (~30.1 MB, also embedded inside the AAB at `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`)

---

## 16. Signing Validation

- **Local Container Environment**: Because the production upload keystore (`my-upload-key.jks` / `KEYSTORE_BASE64`) is stored exclusively in GitHub Actions Secrets for security, the local container build signs `app-release.aab` with the container's local keystore (`SHA-1: 86:12:10:F4:98:E6:9E:4A:18:4C:35:F6:F0:71:35:B7:6E:C6:FF:09`).
- **GitHub Actions CI Environment**: `.github/workflows/build.yml` restores `my-upload-key.jks` from `secrets.KEYSTORE_BASE64`, passes `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, and `KEY_ALIAS` to `./gradlew bundleRelease`, verifies the signature with `jarsigner -verify -verbose -certs`, and explicitly fails if `CN=Android Debug` is detected.

---

## 17. Version Code and Version Name Validation

Verified directly inside `app/build/outputs/bundle/release/app-release.aab` (`base/manifest/AndroidManifest.xml` compiled protobuf):
- `package`: `"com.audiofactory.app"`
- `android:versionCode`: `6` (`versionCode\x1a\x016`)
- `android:versionName`: `"2.0.0"` (`versionName\x1a\x052.0.0`)

---

## 18. Remaining Issues & Limitations

1. **Google Play Console Advertising ID Declaration (External Console Action Required)**:
   Must be updated from **"Yes"** to **"No"** in Google Play Console (**Policy and programs → App content → Advertising ID**).
2. **Pre-Stripped Upstream AndroidX Native Libraries (Informational Only)**:
   `libandroidx.graphics.path.so` (`androidx.graphics:graphics-path:1.0.1`) and `libdatastore_shared_counter.so` (`androidx.datastore:datastore-core-android:1.1.7`) are pre-stripped by Google before Maven publication. Play Console's missing native debug symbols warning is non-blocking and can be safely acknowledged during release rollout.
3. **Production Upload Key Signing in GitHub Actions**:
   Use the `AudioFactory-release-v2.0.0-v6.aab` artifact produced by the **AudioFACTORY CI Build and Test Validation** GitHub Actions workflow (which signs with `secrets.KEYSTORE_BASE64`) when uploading to Google Play Console.

---

## 19. Exact Google Play Console Submission Steps

1. **Run & Download from GitHub Actions**:
   - Push the current branch to GitHub (`main` / `master`) or trigger **AudioFACTORY CI Build and Test Validation** via `workflow_dispatch`.
   - Download **`AudioFACTORY-Android-Builds`** and extract **`AudioFactory-release-v2.0.0-v6.aab`**.
   - (Optional) Download **`AudioFACTORY-R8-Mapping`** (`mapping.txt`), though `proguard.map` is already bundled inside `AudioFactory-release-v2.0.0-v6.aab` under `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`.
2. **Update Advertising ID Declaration in Play Console**:
   - Open **Google Play Console** → select **AudioFACTORY**.
   - In the left navigation menu, go to **Policy and programs → App content**.
   - Locate **Advertising ID** (under *Completed* or *Needs attention*) and click **Manage**.
   - Select **"No"** for *"Does your app use advertising ID?"* and click **Save**.
3. **Upload Version Code 6 AAB to Internal Testing**:
   - Navigate to **Test and release → Testing → Internal testing**.
   - Click **Create new release** (or **Edit release** if a draft exists; remove version code 5 from the draft if present).
   - Under **App bundles**, upload **`AudioFactory-release-v2.0.0-v6.aab`**.
   - Verify that Play Console displays **`6 (2.0.0)`**.
4. **Verify Deobfuscation & Symbol Status**:
   - Click the three-dot menu next to bundle `6 (2.0.0)` → **App bundle explorer** → **Downloads**.
   - Confirm that **ReTrace mapping file (`mapping.txt`)** is already attached automatically from the bundle metadata (if not, upload `mapping.txt` manually).
   - Note that no `native-debug-symbols.zip` upload is needed because the only `.so` files in the bundle are Google's pre-stripped AndroidX `graphics-path` and `datastore` libraries.
5. **Review Warnings & Start Internal Testing Rollout**:
   - Click **Next** to go to the **Preview and confirm** screen.
   - Confirm that the **Advertising ID** error is gone.
   - Acknowledge the informational notice regarding native debug symbols (if shown as a collapsible message) and click **Save and publish** / **Start rollout to Internal testing**.
6. **Verify Google Sign-In on Play-Distributed Build**:
   - In **Test and release → Setup → App signing**, copy the **App signing key certificate** `SHA-1` and `SHA-256` fingerprints.
   - Ensure both the **App signing key** and **Upload key** `SHA-1` and `SHA-256` fingerprints are added in **Firebase Console → Project settings → Your apps (`com.audiofactory.app`)**.
   - Install the build via the Internal Testing opt-in link on a physical Android device and verify WebView studio loading, Google Sign-In via Credential Manager, microphone recording, and audio export.
