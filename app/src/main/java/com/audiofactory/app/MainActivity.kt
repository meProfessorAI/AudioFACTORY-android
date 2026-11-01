package com.audiofactory.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.audiofactory.app.auth.AuthResult
import com.audiofactory.app.auth.BackendOAuthManager
import com.audiofactory.app.data.NetworkConnectivityObserver
import com.audiofactory.app.policy.AudioFactoryWebPolicy
import com.audiofactory.app.service.AudioFactoryDownloadManager
import com.audiofactory.app.ui.AudioFactoryMainScreen
import com.audiofactory.app.ui.theme.AudioFactoryTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var sharedWebView: WebView? = null
    private var pendingFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var onPermissionGrantedCallback: (() -> Unit)? = null
    private lateinit var oauthManager: BackendOAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oauthManager = BackendOAuthManager.getInstance(this)
        enableEdgeToEdge()

        val webView = WebView(this).also {
            sharedWebView = it
        }

        val downloadManager = AudioFactoryDownloadManager(this)
        val connectivityObserver = NetworkConnectivityObserver(this)

        // Load the production Audio Factory URL
        webView.loadUrl(AudioFactoryWebPolicy.START_URL)

        // Process incoming intent for verified HTTPS App Links or OAuth callback
        handleIncomingIntent(intent)

        setContent {
            AudioFactoryTheme {
                // File Chooser Launcher (supports single & multiple audio/document files)
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val callback = pendingFilePathCallback
                    pendingFilePathCallback = null

                    if (callback == null) return@rememberLauncherForActivityResult

                    if (result.resultCode == RESULT_OK && result.data != null) {
                        val data = result.data
                        val clipData = data?.clipData
                        val uriList = mutableListOf<Uri>()

                        if (clipData != null) {
                            for (i in 0 until clipData.itemCount) {
                                clipData.getItemAt(i).uri?.let { uriList.add(it) }
                            }
                        } else {
                            data?.data?.let { uriList.add(it) }
                        }

                        if (uriList.isNotEmpty()) {
                            callback.onReceiveValue(uriList.toTypedArray())
                        } else {
                            callback.onReceiveValue(null)
                        }
                    } else {
                        // User cancelled file selection
                        callback.onReceiveValue(null)
                    }
                }

                // Microphone runtime permission launcher
                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    val onGranted = onPermissionGrantedCallback
                    onPermissionGrantedCallback = null

                    if (isGranted) {
                        onGranted?.invoke()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Microphone permission is required to record audio samples.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                AudioFactoryMainScreen(
                    webView = webView,
                    downloadManager = downloadManager,
                    connectivityObserver = connectivityObserver,
                    onStartOAuth = {
                        oauthManager.initiateOAuthFlow(this@MainActivity)
                    },
                    onSignOut = {
                        oauthManager.logout(this@MainActivity, sharedWebView) {
                            Toast.makeText(this@MainActivity, "Signed out", Toast.LENGTH_SHORT).show()
                        }
                    },
                    getSessionJsonProvider = {
                        oauthManager.currentSession?.toJson()
                    },
                    onOpenFilePicker = { callback, params ->
                        // Cancel any pending callback first
                        pendingFilePathCallback?.onReceiveValue(null)
                        pendingFilePathCallback = callback

                        try {
                            val intent = params.createIntent().apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            filePickerLauncher.launch(intent)
                        } catch (e: Exception) {
                            // Fallback to standard file/audio picker intent
                            try {
                                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "audio/*"
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                }
                                filePickerLauncher.launch(
                                    Intent.createChooser(fallbackIntent, "Select Audio Sample")
                                )
                            } catch (_: Exception) {
                                pendingFilePathCallback?.onReceiveValue(null)
                                pendingFilePathCallback = null
                            }
                        }
                    },
                    onRequestRecordAudioPermission = { onGranted ->
                        val hasMic = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasMic) {
                            onGranted()
                        } else {
                            onPermissionGrantedCallback = onGranted
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onExitApp = {
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        Log.d(TAG, "handleIncomingIntent received URI: $uri")

        if (oauthManager.isOAuthCallback(uri)) {
            oauthManager.handleCallbackUri(uri) { result ->
                when (result) {
                    is AuthResult.Success -> {
                        sharedWebView?.let { webView ->
                            oauthManager.injectSessionIntoWebView(webView, result.session)
                        }
                        val userIdentifier = result.session.userEmail ?: result.session.displayName ?: "Creator"
                        Toast.makeText(this, "Signed in as $userIdentifier", Toast.LENGTH_SHORT).show()
                    }
                    is AuthResult.Cancelled -> {
                        Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show()
                    }
                    is AuthResult.InvalidState -> {
                        Toast.makeText(this, "Security error: invalid OAuth state parameter", Toast.LENGTH_LONG).show()
                    }
                    is AuthResult.ExpiredCode -> {
                        Toast.makeText(this, "Authentication session expired. Please try again.", Toast.LENGTH_LONG).show()
                    }
                    is AuthResult.CodeAlreadyUsed -> {
                        Log.w(TAG, "Ignoring duplicate or replayed handoff code: ${result.code}")
                    }
                    is AuthResult.ExchangeFailed -> {
                        Toast.makeText(this, "Authentication exchange error (${result.statusCode})", Toast.LENGTH_LONG).show()
                    }
                    is AuthResult.NetworkError -> {
                        Toast.makeText(this, "Network connection error during sign-in", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sharedWebView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        sharedWebView?.onPause()
    }

    override fun onDestroy() {
        // Pending callbacks safety
        pendingFilePathCallback?.onReceiveValue(null)
        pendingFilePathCallback = null

        sharedWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        sharedWebView = null
        super.onDestroy()
    }
}
