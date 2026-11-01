package com.audiofactory.app.bridge

import android.webkit.JavascriptInterface
import com.audiofactory.app.BuildConfig
import com.audiofactory.app.auth.BackendOAuthManager
import com.audiofactory.app.service.AudioFactoryDownloadManager

/**
 * Native JavaScript interface exposed to the Audio Factory web client as `AudioFactoryNative` and `AudioFactoryBridge`.
 * Handles:
 * - Client-side audio blob export downloads
 * - Native environment and version detection
 * - Google OAuth initiation via Chrome Custom Tabs
 * - Logout and session revocation
 * - Auth session state querying
 */
class AudioFactoryNativeBridge(
    private val downloadManager: AudioFactoryDownloadManager,
    private val onStartOAuth: () -> Unit,
    private val onSignOut: () -> Unit,
    private val getSessionJsonProvider: () -> String?
) {
    /**
     * Client-side AudioContext blob download bridge for audio generation and exports.
     */
    @JavascriptInterface
    fun downloadBlobAudio(base64Data: String, mimeType: String) {
        downloadManager.handleDataUrlDownload(base64Data, mimeType)
    }

    /**
     * Web code query to check if running inside official Android Native App.
     */
    @JavascriptInterface
    fun isNativeApp(): Boolean = true

    /**
     * Returns native app build version.
     */
    @JavascriptInterface
    fun getNativeAppVersion(): String = BuildConfig.VERSION_NAME

    /**
     * Initiates Google Sign-In through Chrome Custom Tabs and the Vercel backend.
     * Web app invokes this contract when user taps "Sign In with Google".
     */
    @JavascriptInterface
    fun startGoogleSignIn() {
        onStartOAuth()
    }

    /**
     * Alternative bridge signature for Google Sign-In initiation.
     */
    @JavascriptInterface
    fun requestGoogleSignIn() {
        onStartOAuth()
    }

    /**
     * Initiates logout / session revocation from the web layer.
     */
    @JavascriptInterface
    fun logout() {
        onSignOut()
    }

    /**
     * Alternative bridge signature for sign out.
     */
    @JavascriptInterface
    fun signOut() {
        onSignOut()
    }

    /**
     * Returns the serialized JSON of the active authenticated session if present.
     */
    @JavascriptInterface
    fun getSessionJson(): String? {
        return getSessionJsonProvider()
    }

    /**
     * Callback invoked by web application when an auth operation completes.
     */
    @JavascriptInterface
    fun onAuthResult(success: Boolean, message: String?) {
        // Logged or handled by host
    }
}
