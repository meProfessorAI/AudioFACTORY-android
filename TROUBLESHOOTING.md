# Audio Factory V1.1.0 — Release Troubleshooting Guide

This guide details common CI/CD, build, signing, and verification issues along with their resolutions for the Audio Factory Android release pipeline.

---

## 1. Google Play Console Warnings (Deobfuscation & Native Debug Symbols)

### Issue
When uploading `AudioFactory-V1.1.0.aab` to Google Play Console, warnings appear:
1. *Warning: There is no deobfuscation file associated with this App Bundle...*
2. *Warning: This App Bundle contains native code, and you've not uploaded debug symbols...*

### Cause & Resolution
Both warnings are fully resolved in `app/build.gradle.kts`:

1. **Deobfuscation File (`proguard.map`)**:
   R8 code shrinking and resource shrinking are enabled for release builds (`isMinifyEnabled = true`, `isShrinkResources = true`). AGP automatically generates `mapping.txt` and embeds it directly inside the AAB bundle at `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`. Google Play Console automatically detects this map during AAB processing to deobfuscate stack traces.

2. **Native Debug Symbols (`SYMBOL_TABLE`)**:
   Android Gradle Plugin is configured with `ndk { debugSymbolLevel = "SYMBOL_TABLE" }` in `buildTypes.release`. Native debug symbol tables for C/C++ libraries (e.g., `libandroidx.graphics.path.so`) are automatically packaged inside the App Bundle under `BUNDLE-METADATA/com.android.tools.build.debugsymbols/` for ANR and crash analysis.

---

## 2. Missing or Invalid Signing Secrets in GitHub Actions

### Issue
The release workflow fails at step `Restore & Validate Release Signing Keystore` with:
```text
CRITICAL: Required release signing secrets (KEYSTORE_BASE64, STORE_PASSWORD, KEY_PASSWORD) are missing in GitHub repository settings.
```

### Cause
The GitHub repository secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, or `KEY_ALIAS` have not been set in the repository settings.

### Resolution
1. Generate or locate your upload keystore (`my-upload-key.jks`).
2. Encode the `.jks` file to Base64:
   ```bash
   base64 -w 0 my-upload-key.jks > keystore_base64.txt
   ```
3. Open GitHub Repository **Settings > Secrets and variables > Actions**.
4. Add the following repository secrets:
   - `KEYSTORE_BASE64`: Content of `keystore_base64.txt`
   - `STORE_PASSWORD`: Your keystore password
   - `KEY_PASSWORD`: Your private key password
   - `KEY_ALIAS`: Your key alias (default `"upload"`)
5. Re-run the GitHub Actions release workflow.

---

## 3. Keystore Password or Key Alias Mismatch

### Issue
Gradle build fails during `./gradlew assembleRelease` or `./gradlew bundleRelease` with:
```text
Execution failed for task ':app:validateSigningRelease'.
> Keystore file '/home/runner/work/.../release.jks' not found or wrong password / alias.
```

### Cause
The provided `STORE_PASSWORD`, `KEY_PASSWORD`, or `KEY_ALIAS` does not match the entries inside `KEYSTORE_BASE64`.

### Resolution
Verify your keystore locally using `keytool`:
```bash
keytool -list -v -keystore my-upload-key.jks -storepass YOUR_STORE_PASSWORD
```
Ensure `KEY_ALIAS` matches the alias name printed by `keytool` (e.g. `upload`). Update the secret in GitHub Settings accordingly.

---

## 4. APK / AAB Signature Verification Failure

### Issue
The workflow step `Verify APK & AAB Signatures and Metadata` fails during `apksigner` or `jarsigner` verification:
```text
DOES NOT VERIFY
ERROR: APK is not signed
```

### Cause
The output artifact was either unaligned or compiled without a valid `signingConfig` assignment in `app/build.gradle.kts`.

### Resolution
Check `app/build.gradle.kts` under `buildTypes.release`:
```kotlin
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```
Ensure both `assembleRelease` and `bundleRelease` tasks run with the environment variables `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, and `KEY_ALIAS` populated.

---

## 5. Package ID or Version Mismatch

### Issue
Verification fails with:
```text
Package ID mismatch! Expected com.audiofactory.app
```

### Cause
`applicationId`, `versionCode`, or `versionName` in `app/build.gradle.kts` does not match `1.1.0` (versionCode `2`).

### Resolution
In `app/build.gradle.kts`, ensure:
```kotlin
defaultConfig {
    applicationId = "com.audiofactory.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.1.0"
}
```

---

## 6. Permission Denied on `./gradlew`

### Issue
GitHub Actions runner fails with `bash: ./gradlew: Permission denied`.

### Resolution
Ensure `gradlew` has executable permissions committed in Git:
```bash
chmod +x gradlew
git add gradlew
git commit -m "chmod +x gradlew"
```
The workflows also explicitly execute `chmod +x ./gradlew` as a safeguard before running Gradle tasks.
