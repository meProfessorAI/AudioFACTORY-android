package com.audiofactory.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.audiofactory.app.bridge.AudioFactoryNativeBridge
import com.audiofactory.app.data.WebErrorType
import com.audiofactory.app.policy.AudioFactoryWebPolicy
import com.audiofactory.app.service.AudioFactoryDownloadManager

interface WebViewHostCallback {
    fun onProgressChanged(progress: Int)
    fun onLoadingStateChanged(isLoading: Boolean)
    fun onTitleChanged(title: String)
    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean, url: String)
    fun onErrorReceived(errorType: WebErrorType?)
    fun onShowFileChooser(filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: WebChromeClient.FileChooserParams): Boolean
    fun onMicrophonePermissionRequested(request: PermissionRequest)
    fun onExternalUrl(url: String)
    fun onRequestGoogleSignIn()
    fun onAuthStatusReceived(status: String, message: String?)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AudioFactoryWebView(
    webView: WebView,
    callback: WebViewHostCallback,
    downloadManager: AudioFactoryDownloadManager,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            webView.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Configure WebView Security & Media Settings
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false

                    // Strict Security Constraints
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    // Storage and Cache performance
                    cacheMode = WebSettings.LOAD_DEFAULT

                    // Custom user agent suffix identifying native Android app
                    val baseUa = userAgentString
                    if (!baseUa.contains("AudioFactoryAndroid")) {
                        userAgentString = "$baseUa AudioFactoryAndroid/1.1"
                    }
                }

                // Cookie Management
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                // Register native JS bridges for React application integration
                val nativeBridge = AudioFactoryNativeBridge(
                    downloadManager = downloadManager,
                    onRequestGoogleSignIn = {
                        callback.onRequestGoogleSignIn()
                    },
                    onAuthStatusReceived = { status, message ->
                        callback.onAuthStatusReceived(status, message)
                    }
                )

                addJavascriptInterface(nativeBridge, "AudioFactoryNative")
                addJavascriptInterface(nativeBridge, "AudioFactoryBridge")

                // Download Listener for direct file links
                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                    downloadManager.enqueueDownload(url, userAgent, contentDisposition, mimetype)
                }

                // WebChromeClient configuration
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        callback.onProgressChanged(newProgress)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        if (!title.isNullOrBlank()) {
                            callback.onTitleChanged(title)
                        }
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        return if (filePathCallback != null && fileChooserParams != null) {
                            callback.onShowFileChooser(filePathCallback, fileChooserParams)
                        } else {
                            false
                        }
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        if (request == null) return
                        val originUri = Uri.parse(request.origin.toString())

                        // Security: Only allow audio capture from trusted Audio Factory origins
                        if (AudioFactoryWebPolicy.isTrustedOrigin(originUri)) {
                            val hasAudioCapture = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                            if (hasAudioCapture) {
                                callback.onMicrophonePermissionRequested(request)
                                return
                            }
                        }
                        request.deny()
                    }
                }

                // WebViewClient configuration
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return false

                        // Trusted first-party origin: let WebView handle internal navigation
                        if (AudioFactoryWebPolicy.isTrustedOrigin(uri)) {
                            return false
                        }

                        // System action schemes (mailto, tel, sms)
                        if (AudioFactoryWebPolicy.isExternalActionScheme(uri)) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                            return true
                        }

                        // External HTTPS links opened safely in external system browser
                        if (uri.scheme == "https" || uri.scheme == "http") {
                            callback.onExternalUrl(uri.toString())
                            return true
                        }

                        // Block untrusted schemes (file:, javascript:, content:, etc.)
                        return true
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        callback.onLoadingStateChanged(true)
                        callback.onErrorReceived(null)
                        callback.onNavigationStateChanged(
                            canGoBack = canGoBack(),
                            canGoForward = canGoForward(),
                            url = url ?: AudioFactoryWebPolicy.START_URL
                        )
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        callback.onLoadingStateChanged(false)
                        callback.onNavigationStateChanged(
                            canGoBack = canGoBack(),
                            canGoForward = canGoForward(),
                            url = url ?: AudioFactoryWebPolicy.START_URL
                        )

                        // Inject bridge bridge initialization & blob download hooks
                        view?.evaluateJavascript(
                            """
                            (function() {
                                if (window._audioFactoryBridgeInjected) return;
                                window._audioFactoryBridgeInjected = true;
                                
                                if (!window.AudioFactoryNative && window.AudioFactoryBridge) {
                                    window.AudioFactoryNative = window.AudioFactoryBridge;
                                }
                                
                                console.log("[AudioFactoryNative] Bridge active and ready.");
                            })();
                            """.trimIndent(),
                            null
                        )
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            val description = error?.description?.toString() ?: "Connection error"
                            val errorCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                error?.errorCode ?: -1
                            } else {
                                -1
                            }

                            val errorType = when (errorCode) {
                                ERROR_HOST_LOOKUP -> WebErrorType.DnsError(request.url.host ?: "audiofactory.vercel.app")
                                ERROR_CONNECT, ERROR_TIMEOUT -> WebErrorType.Offline
                                else -> WebErrorType.Unknown(description)
                            }
                            callback.onErrorReceived(errorType)
                            callback.onLoadingStateChanged(false)
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.isForMainFrame == true) {
                            val statusCode = errorResponse?.statusCode ?: 500
                            if (statusCode >= 400) {
                                callback.onErrorReceived(
                                    WebErrorType.HttpError(
                                        statusCode,
                                        errorResponse?.reasonPhrase ?: "HTTP $statusCode"
                                    )
                                )
                                callback.onLoadingStateChanged(false)
                            }
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        // CRITICAL SECURITY: Never bypass SSL certificate errors
                        Log.e("AudioFactoryWebView", "SSL Error received: ${error?.primaryError}. Cancelling navigation.")
                        handler?.cancel()
                        val primaryError = error?.primaryError ?: -1
                        callback.onErrorReceived(WebErrorType.SslError(primaryError))
                        callback.onLoadingStateChanged(false)
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?
                    ): Boolean {
                        callback.onErrorReceived(WebErrorType.RendererCrash)
                        callback.onLoadingStateChanged(false)
                        return true
                    }
                }
            }
        }
    )
}
