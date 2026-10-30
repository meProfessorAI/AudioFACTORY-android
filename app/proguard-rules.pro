# Add project specific ProGuard rules here.

# Preserve SourceFile and LineNumberTable for clean deobfuscated stack trace symbolication in Google Play Console
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep WebView JavaScript interfaces and annotations
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Compose platform views
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }

# Keep data models
-keepclassmembers class com.audiofactory.app.data.** { *; }
