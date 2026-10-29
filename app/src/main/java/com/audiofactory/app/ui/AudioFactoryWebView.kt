package com.audiofactory.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
}

class AudioFactoryNativeBridge(
    private val downloadManager: AudioFactoryDownloadManager
) {
    @JavascriptInterface
    fun downloadBlobAudio(base64Data: String, mimeType: String) {
        downloadManager.handleDataUrlDownload(base64Data, mimeType)
    }
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

                    // Strict Security
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    // Cache and performance
                    cacheMode = WebSettings.LOAD_DEFAULT

                    // Custom user agent suffix
                    val baseUa = userAgentString
                    userAgentString = "$baseUa AudioFactoryAndroid/1.0"
                }

                // Cookies
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                // Add safe JS bridge for client-side AudioContext blob downloads
                addJavascriptInterface(
                    AudioFactoryNativeBridge(downloadManager),
                    "AudioFactoryBridge"
                )

                // Download Listener
                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                    downloadManager.enqueueDownload(url, userAgent, contentDisposition, mimetype)
                }

                // WebChromeClient
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

                        // Security: Only allow audio capture requests strictly originating from trusted Audio Factory origin
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

                // WebViewClient
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return false

                        // Check if trusted first-party domain
                        if (AudioFactoryWebPolicy.isTrustedOrigin(uri)) {
                            return false // Let WebView handle it
                        }

                        // Check external action schemes
                        if (AudioFactoryWebPolicy.isExternalActionScheme(uri)) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                            return true
                        }

                        // External links opened in system browser for security
                        if (uri.scheme == "https" || uri.scheme == "http") {
                            callback.onExternalUrl(uri.toString())
                            return true
                        }

                        // Block all other untrusted schemes (javascript:, file:, content:, etc.)
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

                        // Inject helper for blob downloads generated in JS
                        view?.evaluateJavascript(
                            """
                            (function() {
                                if (window._afBlobInjected) return;
                                window._afBlobInjected = true;
                                const originalCreateObjectURL = URL.createObjectURL;
                                URL.createObjectURL = function(blob) {
                                    const url = originalCreateObjectURL.call(URL, blob);
                                    return url;
                                };
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
                        // Only handle main frame navigation errors
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
                        handler?.cancel()
                        val primaryError = error?.primaryError ?: -1
                        callback.onErrorReceived(WebErrorType.SslError(primaryError))
                        callback.onLoadingStateChanged(false)
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?
                    ): Boolean {
                        // Handle renderer crash recovery gracefully
                        callback.onErrorReceived(WebErrorType.RendererCrash)
                        callback.onLoadingStateChanged(false)
                        return true // App survives renderer termination
                    }
                }
            }
        }
    )
}
