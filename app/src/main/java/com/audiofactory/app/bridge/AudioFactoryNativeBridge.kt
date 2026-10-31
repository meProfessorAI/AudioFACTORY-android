package com.audiofactory.app.bridge

import android.webkit.JavascriptInterface
import com.audiofactory.app.service.AudioFactoryDownloadManager

class AudioFactoryNativeBridge(
    private val downloadManager: AudioFactoryDownloadManager,
    private val onRequestGoogleSignIn: () -> Unit,
    private val onAuthStatusReceived: (status: String, message: String?) -> Unit
) {
    /**
     * Web application invokes this method when user taps Google Sign-In inside the web app.
     */
    @JavascriptInterface
    fun requestGoogleSignIn() {
        onRequestGoogleSignIn()
    }

    /**
     * Web application invokes this method to send authentication outcome back to native.
     */
    @JavascriptInterface
    fun onAuthStatus(status: String, message: String?) {
        onAuthStatusReceived(status, message)
    }

    /**
     * Client-side AudioContext blob download bridge.
     */
    @JavascriptInterface
    fun downloadBlobAudio(base64Data: String, mimeType: String) {
        downloadManager.handleDataUrlDownload(base64Data, mimeType)
    }

    /**
     * Allows Web code to query if it is running inside the official Android Native App.
     */
    @JavascriptInterface
    fun isNativeApp(): Boolean = true

    /**
     * Returns native app build version.
     */
    @JavascriptInterface
    fun getNativeAppVersion(): String = "1.1.0"
}
