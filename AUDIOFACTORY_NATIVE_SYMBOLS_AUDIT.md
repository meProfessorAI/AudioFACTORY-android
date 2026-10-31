# AudioFACTORY Android — Native Debug Symbols Root-Cause Investigation & Audit (`v2.0.0`, Version Code `7`)

## 1. Executive Summary

- **Application**: AudioFACTORY (`com.audiofactory.app`)
- **Version Name**: `2.0.0`
- **Version Code**: `7` (incremented from previously uploaded version code `6`)
- **Toolchain**: AGP `9.1.1`, Kotlin `2.2.10`, Gradle `9.3.1`, Java `21`, Compile SDK `36.1`, Target SDK `36`, Min SDK `24`
- **Google Play Console Warning Investigated**:
  > *"This App Bundle contains native code, and you've not uploaded debug symbols."*

### Definitive Root-Cause Finding
AudioFACTORY contains **zero application-owned C/C++ or JNI source code**. However, the release Android App Bundle (`app-release.aab`) packages **8 prebuilt native `.so` shared libraries** across **4 ABIs** (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) originating from two transitive Google AndroidX Jetpack libraries:
1. **`libandroidx.graphics.path.so`** — introduced by **`androidx.graphics:graphics-path:1.0.1`** (transitive dependency of `androidx.compose.ui:ui-graphics:1.7.0` via `androidx.compose:compose-bom:2024.09.00`).
2. **`libdatastore_shared_counter.so`** — introduced by **`androidx.datastore:datastore-core-android:1.1.7`** (transitive dependency of `com.google.firebase:firebase-common:22.2.0` via `firebase-auth:24.2.0` and `firebase-firestore:26.5.0`).

When executing `gradle :app:bundleRelease --info --stacktrace` with `ndk { debugSymbolLevel = "FULL" }`, AGP 9.1.1's `:app:extractReleaseNativeDebugMetadata` and `:app:extractReleaseNativeSymbolTables` tasks emit the exact diagnostic for all 8 libraries:
```text
Unable to extract native debug metadata from .../lib/<abi>/libandroidx.graphics.path.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from .../lib/<abi>/libdatastore_shared_counter.so because the native debug metadata has already been stripped.
```
And `:app:mergeReleaseNativeDebugMetadata` finishes with `NO-SOURCE`.

Direct binary ELF section header analysis of the `.so` files inside `app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/` (prior to AGP stripping) and verification of Google's Maven repository (`https://dl.google.com/android/maven2/`) confirm:
1. Both `graphics-path-1.0.1.aar` and `datastore-core-release.aar` (`1.1.7`) are published by **Google AndroidX** with their `.so` files **already stripped of `.symtab`, `.strtab`, and all DWARF `.debug_*` sections prior to Maven publication**.
2. Google Maven does **not** publish separate `-debug-symbols.zip` or unstripped `.so` variants in `graphics-path-1.0.1.module` or `datastore-core-android-1.1.7.module`.
3. Because AGP cannot reconstruct stripped DWARF/ELF symbol tables that were never present in the upstream AARs, neither `BUNDLE-METADATA/com.android.tools.build.debugsymbols/` inside the AAB nor `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip` is generated.
4. Per official Android documentation and strict compliance rules, stripped `.so` binaries must **never** be repackaged into a fake `native-debug-symbols.zip`. The Play Console warning is a benign, non-blocking informational warning triggered whenever an AAB contains pre-stripped third-party `.so` files.

---

## 2. Diagnostic Release Build Output (`--info --stacktrace`)

Executed diagnostic tasks:
```bash
gradle :app:stripReleaseDebugSymbols :app:extractReleaseNativeDebugMetadata :app:extractReleaseNativeSymbolTables :app:mergeReleaseNativeDebugMetadata --rerun-tasks --info --stacktrace
```

### Captured AGP 9.1.1 Task Output
```text
> Task :app:extractReleaseNativeDebugMetadata
Build cache key for task ':app:extractReleaseNativeDebugMetadata' is 0119be2362eb0a630205e8116dd67441
Task ':app:extractReleaseNativeDebugMetadata' is not up-to-date because:
  Executed with '--rerun-tasks'.
C/C++: android.ndkVersion from module build.gradle is [28.2.13676358]
C/C++: android.ndkPath from module build.gradle is not set
C/C++: ndk.dir in local.properties is not set
C/C++: Not considering ANDROID_NDK_HOME because support was removed after deprecation period.
C/C++: sdkFolder is /opt/android/sdk
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/libandroidx.graphics.path.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/libdatastore_shared_counter.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/armeabi-v7a/libandroidx.graphics.path.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/armeabi-v7a/libdatastore_shared_counter.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/x86/libandroidx.graphics.path.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/x86/libdatastore_shared_counter.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/x86_64/libandroidx.graphics.path.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/x86_64/libdatastore_shared_counter.so because the native debug metadata has already been stripped.

> Task :app:extractReleaseNativeSymbolTables
Build cache key for task ':app:extractReleaseNativeSymbolTables' is e39f8826ae1278c8d1070d836628c414
Task ':app:extractReleaseNativeSymbolTables' is not up-to-date because:
  Executed with '--rerun-tasks'.
C/C++: android.ndkVersion from module build.gradle is [28.2.13676358]
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/libandroidx.graphics.path.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/libdatastore_shared_counter.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/armeabi-v7a/libandroidx.graphics.path.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/armeabi-v7a/libdatastore_shared_counter.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/x86/libandroidx.graphics.path.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/x86/libdatastore_shared_counter.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/x86_64/libandroidx.graphics.path.so because the native debug metadata has already been stripped.
Unable to extract native debug metadata from /app/applet/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/x86_64/libdatastore_shared_counter.so because the native debug metadata has already been stripped.

> Task :app:mergeReleaseNativeDebugMetadata NO-SOURCE
Skipping task ':app:mergeReleaseNativeDebugMetadata' as it has no source files and no previous output files.
```

---

## 3. Complete Native `.so` Inventory & ELF Inspection

Every native `.so` library packaged in `app/build/outputs/bundle/release/app-release.aab` was extracted and inspected at the ELF header and section-header level:

| AAB Entry Path | ABI | Architecture | File Size | ELF BuildID (`NT_GNU_BUILD_ID` SHA-1) | Originating Artifact | Publisher | Prebuilt? | `.symtab` Present? | `.debug_*` Present? | `.dynsym` / `.eh_frame` Present? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `base/lib/arm64-v8a/libandroidx.graphics.path.so` | `arm64-v8a` | 64-bit ARM aarch64 | 10,096 B | `a7846ae06d6bf6cf63fa7c5e056b5de493c71567` | `androidx.graphics:graphics-path:1.0.1` | Google (AndroidX) | Yes | **No** (Stripped) | **No** (Stripped) | Yes (`.dynsym` 216B, `.eh_frame` 148B) |
| `base/lib/armeabi-v7a/libandroidx.graphics.path.so` | `armeabi-v7a` | 32-bit ARM EABI5 | 7,252 B | `fa98a997cdd9b43f190a4bd20f3dad441b4062f0` | `androidx.graphics:graphics-path:1.0.1` | Google (AndroidX) | Yes | **No** (Stripped) | **No** (Stripped) | Yes |
| `base/lib/x86/libandroidx.graphics.path.so` | `x86` | 32-bit Intel 80386 | 9,284 B | `56b3f97a7efb05fc89674cf68f6c27847a93860b` | `androidx.graphics:graphics-path:1.0.1` | Google (AndroidX) | Yes | **No** (Stripped) | **No** (Stripped) | Yes |
| `base/lib/x86_64/libandroidx.graphics.path.so` | `x86_64` | 64-bit x86-64 | 10,760 B | `f3e82b0a78f4c567cca77fe6c132199d806987d1` | `androidx.graphics:graphics-path:1.0.1` | Google (AndroidX) | Yes | **No** (Stripped) | **No** (Stripped) | Yes |
| `base/lib/arm64-v8a/libdatastore_shared_counter.so` | `arm64-v8a` | 64-bit ARM aarch64 | 7,112 B | `17db37bd6770ac00dd2d1d2828839fd23a7959a3` | `androidx.datastore:datastore-core-android:1.1.7` | Google (AndroidX) | Yes | **No** (Stripped) | **No** (Stripped) | Yes |
| `base/lib/armeabi-v7a/libdatastore_shared_counter.so` | `armeabi-v7a` | 32-bit ARM EABI5 | 4,416 B | `c2c541cc4337a0fc28271329ef238d6bddf6d70d` | `androidx.datastore:datastore-core-android:1.1.7` | Google (AndroidX) | Yes | **No** (Stripped) | **No** (Stripped) | Yes |
| `base/lib/x86/libdatastore_shared_counter.so` | `x86` | 32-bit Intel 80386 | 5,148 B | `cf60dc0a5664876a021857e4948f6798ee0ca150` | `androidx.datastore:datastore-core-android:1.1.7` | Google (AndroidX) | Yes | **No** (Stripped) | **No** (Stripped) | Yes |
| `base/lib/x86_64/libdatastore_shared_counter.so` | `x86_64` | 64-bit x86-64 | 6,224 B | `c6ef0802be059f5f59b23f1205d040dbc5b1ccad` | `androidx.datastore:datastore-core-android:1.1.7` | Google (AndroidX) | Yes | **No** (Stripped) | **No** (Stripped) | Yes |

### ELF Section Headers & Exported `.dynstr` Symbols
- **`libandroidx.graphics.path.so` (`arm64-v8a`)**:
  - ELF Sections (23 total): `.note.android.ident`, `.note.gnu.build-id`, `.dynsym`, `.gnu.version`, `.gnu.version_d`, `.gnu.version_r`, `.gnu.hash`, `.hash`, `.dynstr`, `.rela.dyn`, `.rela.plt`, `.rodata`, `.eh_frame_hdr`, `.eh_frame`, `.text`, `.plt`, `.data.rel.ro`, `.fini_array`, `.dynamic`, `.got.plt`, `.bss`, `.comment`, `.shstrtab`.
  - `.dynstr` Exported/Imported Symbols: `JNI_OnLoad`, `__cxa_finalize`, `__cxa_atexit`, `__stack_chk_fail`, `__system_property_get`, `atoi`, `malloc`, `free`.
- **`libdatastore_shared_counter.so` (`arm64-v8a`)**:
  - `.dynstr` Exported JNI & Mangled C++ Symbols:
    - `Java_androidx_datastore_core_NativeSharedCounter_nativeTruncateFile` (`_ZN9datastore12TruncateFileEi`)
    - `Java_androidx_datastore_core_NativeSharedCounter_nativeCreateSharedCounter` (`_ZN9datastore19CreateSharedCounterEiPPv`)
    - `Java_androidx_datastore_core_NativeSharedCounter_nativeGetCounterValue` (`_ZN9datastore15GetCounterValueEPNSt6__ndk16atomicIjEE`)
    - `Java_androidx_datastore_core_NativeSharedCounter_nativeIncrementAndGetCounterValue` (`_ZN9datastore27IncrementAndGetCounterValueEPNSt6__ndk16atomicIjEE`)
    - `_Z16ThrowIoExceptionP7_JNIEnvPKc`

---

## 4. Resolved Native Dependency Tree (`releaseRuntimeClasspath`)

Executed:
```bash
gradle :app:dependencies --configuration releaseRuntimeClasspath
gradle :app:dependencyInsight --configuration releaseRuntimeClasspath --dependency androidx.graphics:graphics-path
gradle :app:dependencyInsight --configuration releaseRuntimeClasspath --dependency androidx.datastore:datastore-core-android
```

### Path 1: `androidx.graphics:graphics-path:1.0.1`
```text
androidx.graphics:graphics-path:1.0.1
\--- androidx.compose.ui:ui-graphics-android:1.7.0
     +--- androidx.compose:compose-bom:2024.09.00
     |    \--- releaseRuntimeClasspath
     \--- androidx.compose.ui:ui-graphics:1.7.0
          +--- releaseRuntimeClasspath (requested androidx.compose.ui:ui-graphics)
          +--- androidx.compose.material3:material3-android:1.3.0
          +--- androidx.compose.animation:animation-core-android:1.7.0
          \--- androidx.compose.ui:ui-android:1.7.0
```

### Path 2: `androidx.datastore:datastore-core-android:1.1.7`
Note: Even though `libs.androidx.datastore.preferences` is commented out in `app/build.gradle.kts`, `com.google.firebase:firebase-common:22.2.0` (pulled in by `firebase-auth:24.2.0` and `firebase-firestore:26.5.0` via `firebase-bom:34.17.0`) requires `androidx.datastore:datastore-preferences:1.1.7`:
```text
androidx.datastore:datastore-core-android:1.1.7
\--- androidx.datastore:datastore-core:1.1.7
     \--- androidx.datastore:datastore-android:1.1.7
          \--- androidx.datastore:datastore:1.1.7
               \--- androidx.datastore:datastore-preferences-android:1.1.7
                    \--- androidx.datastore:datastore-preferences:1.1.7
                         \--- com.google.firebase:firebase-common:22.2.0
                              +--- com.google.firebase:firebase-bom:34.17.0
                              +--- com.google.firebase:firebase-auth:24.2.0
                              \--- com.google.firebase:firebase-firestore:26.5.0
```

### Upstream Publisher Maven Metadata Verification (`dl.google.com/android/maven2`)
Inspection of Google's published Gradle Module Metadata confirms neither artifact publishes unstripped native debug symbols:
- **`https://dl.google.com/android/maven2/androidx/graphics/graphics-path/1.0.1/graphics-path-1.0.1.module`**:
  - `releaseVariantReleaseApiPublication` → `graphics-path-1.0.1.aar`
  - `releaseVariantReleaseRuntimePublication` → `graphics-path-1.0.1.aar`
  - `sourcesElements` → `graphics-path-1.0.1-sources.jar`
  - `libraryVersionMetadata` → `graphics-path-1.0.1-versionMetadata.json`
- **`https://dl.google.com/android/maven2/androidx/datastore/datastore-core-android/1.1.7/datastore-core-android-1.1.7.module`**:
  - `releaseApiElements-published` → `datastore-core-release.aar`
  - `releaseRuntimeElements-published` → `datastore-core-release.aar`
  - `releaseSourcesElements-published` → `datastore-core-android-1.1.7-sources.jar`

---

## 5. AAB Archive & Metadata Inspection Report

Inspected `app/build/outputs/bundle/release/app-release.aab` (`AudioFactory-release-v2.0.0-v7.aab`):

| AAB Archive Path | Size (Bytes) | Description |
| :--- | :--- | :--- |
| `base/manifest/AndroidManifest.xml` | 17,137 | Compiled Protobuf Manifest (`package="com.audiofactory.app"`, `versionCode=7`, `versionName="2.0.0"`, no `AD_ID`) |
| `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` | 30,124,715 | Full R8 deobfuscation mapping file automatically embedded in AAB |
| `BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb` | 15,833 | Play Console dependency metadata protobuf |
| `BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties` | 56 | AGP metadata properties |
| `BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof` | 5,535 | ART Baseline Profile |
| `BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm` | 832 | ART Baseline Profile Metadata |
| `BUNDLE-METADATA/com.android.tools/r8.json` | 1,479 | R8 compiler metadata |
| `BUNDLE-METADATA/com.android.tools.build.debugsymbols/` | *Absent* | Not generated because `:app:mergeReleaseNativeDebugMetadata` had `NO-SOURCE` (all 8 third-party `.so` files are pre-stripped upstream) |
| `base/lib/<abi>/libandroidx.graphics.path.so` | 4 files | Pre-stripped AndroidX Graphics Path JNI library |
| `base/lib/<abi>/libdatastore_shared_counter.so` | 4 files | Pre-stripped AndroidX DataStore Shared Counter JNI library |

---

## 6. Mandatory Final Diagnosis Per Native Library

| Native Library | ABI | Originating Dependency | Definitive Status | Explanation |
| :--- | :--- | :--- | :--- | :--- |
| `libandroidx.graphics.path.so` | `arm64-v8a` | `androidx.graphics:graphics-path:1.0.1` | **`THIRD-PARTY SYMBOLS STRIPPED` / `SYMBOLS NOT AVAILABLE`** | Prebuilt inside Google AndroidX AAR with `.symtab` and `.debug_*` stripped prior to Maven publication; Google does not publish a symbol archive on Google Maven. |
| `libandroidx.graphics.path.so` | `armeabi-v7a` | `androidx.graphics:graphics-path:1.0.1` | **`THIRD-PARTY SYMBOLS STRIPPED` / `SYMBOLS NOT AVAILABLE`** | Prebuilt inside Google AndroidX AAR with `.symtab` and `.debug_*` stripped prior to Maven publication; Google does not publish a symbol archive on Google Maven. |
| `libandroidx.graphics.path.so` | `x86` | `androidx.graphics:graphics-path:1.0.1` | **`THIRD-PARTY SYMBOLS STRIPPED` / `SYMBOLS NOT AVAILABLE`** | Prebuilt inside Google AndroidX AAR with `.symtab` and `.debug_*` stripped prior to Maven publication; Google does not publish a symbol archive on Google Maven. |
| `libandroidx.graphics.path.so` | `x86_64` | `androidx.graphics:graphics-path:1.0.1` | **`THIRD-PARTY SYMBOLS STRIPPED` / `SYMBOLS NOT AVAILABLE`** | Prebuilt inside Google AndroidX AAR with `.symtab` and `.debug_*` stripped prior to Maven publication; Google does not publish a symbol archive on Google Maven. |
| `libdatastore_shared_counter.so` | `arm64-v8a` | `androidx.datastore:datastore-core-android:1.1.7` | **`THIRD-PARTY SYMBOLS STRIPPED` / `SYMBOLS NOT AVAILABLE`** | Prebuilt inside Google AndroidX AAR with `.symtab` and `.debug_*` stripped prior to Maven publication (dynamic `.dynsym` retains exported JNI function names); Google does not publish a symbol archive on Google Maven. |
| `libdatastore_shared_counter.so` | `armeabi-v7a` | `androidx.datastore:datastore-core-android:1.1.7` | **`THIRD-PARTY SYMBOLS STRIPPED` / `SYMBOLS NOT AVAILABLE`** | Prebuilt inside Google AndroidX AAR with `.symtab` and `.debug_*` stripped prior to Maven publication; Google does not publish a symbol archive on Google Maven. |
| `libdatastore_shared_counter.so` | `x86` | `androidx.datastore:datastore-core-android:1.1.7` | **`THIRD-PARTY SYMBOLS STRIPPED` / `SYMBOLS NOT AVAILABLE`** | Prebuilt inside Google AndroidX AAR with `.symtab` and `.debug_*` stripped prior to Maven publication; Google does not publish a symbol archive on Google Maven. |
| `libdatastore_shared_counter.so` | `x86_64` | `androidx.datastore:datastore-core-android:1.1.7` | **`THIRD-PARTY SYMBOLS STRIPPED` / `SYMBOLS NOT AVAILABLE`** | Prebuilt inside Google AndroidX AAR with `.symtab` and `.debug_*` stripped prior to Maven publication; Google does not publish a symbol archive on Google Maven. |
| *Application-Owned Native Libraries* | *All ABIs* | `com.audiofactory.app` | **`NOT APPLICABLE`** | AudioFACTORY is a 100% Kotlin/Compose application with zero first-party C/C++/JNI source files. |
