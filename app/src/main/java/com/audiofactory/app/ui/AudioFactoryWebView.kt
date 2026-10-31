package com.audiofactory.app.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.Gravity
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
import android.widget.LinearLayout
import android.widget.TextView
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

/**
 * Returns a sanitized User-Agent matching standard Chrome Mobile.
 * Google OAuth explicitly blocks WebViews if 'Version/X.X' or '; wv' is present ('403: disallowed_useragent').
 */
fun getSanitizedUserAgent(baseUa: String): String {
    return baseUa
        .replace("; wv", "")
        .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
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

                val sanitizedUa = getSanitizedUserAgent(settings.userAgentString)

                // Configure WebView Security & Authentication Settings
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false

                    // Support popups for Google Sign-In and OAuth (Firebase Auth signInWithPopup)
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true

                    // Security
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    // Cache and performance
                    cacheMode = WebSettings.LOAD_DEFAULT

                    // Use sanitized mobile Chrome UA so Google OAuth does not block with 403 disallowed_useragent
                    userAgentString = sanitizedUa
                }

                // Enable first-party and third-party cookies for Firebase Auth & cross-origin OAuth tokens
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                // Safe JS bridge for client-side AudioContext blob downloads
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

                    // Handle OAuth and Firebase Auth popups (signInWithPopup)
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        if (resultMsg == null) return false
                        val dialogContext = view?.context ?: return false

                        // Create popup WebView with identical auth & cookie settings
                        val popupWebView = WebView(dialogContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                setSupportMultipleWindows(true)
                                javaScriptCanOpenWindowsAutomatically = true
                                userAgentString = sanitizedUa
                            }
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                        }

                        val dialog = Dialog(
                            dialogContext,
                            android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen
                        )

                        // Top bar for modal with clear dismissal affordance
                        val density = dialogContext.resources.displayMetrics.density
                        val rootLayout = LinearLayout(dialogContext).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(Color.parseColor("#121620"))
                        }

                        val topBar = LinearLayout(dialogContext).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                (52 * density).toInt()
                            )
                            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
                            gravity = Gravity.CENTER_VERTICAL
                            setBackgroundColor(Color.parseColor("#1A202C"))
                        }

                        val titleView = TextView(dialogContext).apply {
                            text = "Audio Factory Sign In"
                            setTextColor(Color.WHITE)
                            textSize = 16f
                            typeface = Typeface.DEFAULT_BOLD
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }

                        val closeButton = TextView(dialogContext).apply {
                            text = "✕"
                            setTextColor(Color.parseColor("#CBD5E1"))
                            textSize = 22f
                            setPadding((16 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                            setOnClickListener {
                                dialog.dismiss()
                            }
                        }

                        topBar.addView(titleView)
                        topBar.addView(closeButton)
                        rootLayout.addView(topBar)
                        rootLayout.addView(
                            popupWebView,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                0,
                                1f
                            )
                        )

                        dialog.setContentView(rootLayout)

                        popupWebView.webChromeClient = object : WebChromeClient() {
                            override fun onCloseWindow(window: WebView?) {
                                try {
                                    dialog.dismiss()
                                } catch (_: Exception) {}
                            }
                        }

                        popupWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val uri = request?.url ?: return false

                                // Allow trusted domains, Firebase Auth and Google OAuth inside the popup
                                if (AudioFactoryWebPolicy.isAllowedInWebView(uri)) {
                                    return false
                                }

                                if (AudioFactoryWebPolicy.isExternalActionScheme(uri)) {
                                    try {
                                        dialogContext.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    } catch (_: Exception) {}
                                    return true
                                }

                                if (uri.scheme == "https" || uri.scheme == "http") {
                                    callback.onExternalUrl(uri.toString())
                                    return true
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                CookieManager.getInstance().flush()
                            }
                        }

                        dialog.setOnDismissListener {
                            popupWebView.destroy()
                        }

                        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                        transport.webView = popupWebView
                        resultMsg.sendToTarget()
                        dialog.show()
                        return true
                    }
                }

                // Main WebViewClient
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return false

                        // Check if allowed inside the WebView (Audio Factory origin or Firebase/Google Auth)
                        if (AudioFactoryWebPolicy.isAllowedInWebView(uri)) {
                            return false // Let WebView handle it!
                        }

                        // Check external action schemes (mailto, tel, sms)
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

                        // Persist cookies after redirects and authentication
                        CookieManager.getInstance().flush()

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
