package com.audiofactory.app.bridge

import android.webkit.JavascriptInterface
import com.audiofactory.app.BuildConfig
import com.audiofactory.app.service.AudioFactoryDownloadManager

/**
 * Native JavaScript interface exposed to the Audio Factory web client.
 * Handles client-side audio blob export downloads and native environment detection.
 * All authentication, sessions, and data operations are handled directly by the web app and Vercel backend.
 */
class AudioFactoryNativeBridge(
    private val downloadManager: AudioFactoryDownloadManager
) {
    /**
     * Client-side AudioContext blob download bridge for audio generation and exports.
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
    fun getNativeAppVersion(): String = BuildConfig.VERSION_NAME
}
