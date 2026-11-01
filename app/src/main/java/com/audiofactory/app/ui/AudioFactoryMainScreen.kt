package com.audiofactory.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.audiofactory.app.data.NetworkConnectivityObserver
import com.audiofactory.app.data.WebErrorType
import com.audiofactory.app.data.WebUiState
import com.audiofactory.app.policy.AudioFactoryWebPolicy
import com.audiofactory.app.service.AudioFactoryDownloadManager
import com.audiofactory.app.ui.components.AboutDialog
import com.audiofactory.app.ui.components.AudioLoadingIndicator
import com.audiofactory.app.ui.components.ErrorAndOfflineView
import com.audiofactory.app.ui.components.ExitConfirmDialog
import com.audiofactory.app.ui.components.MicrophoneRationaleDialog
import com.audiofactory.app.ui.theme.StudioBackground
import com.audiofactory.app.ui.theme.StudioBlue
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AudioFactoryMainScreen(
    webView: WebView,
    downloadManager: AudioFactoryDownloadManager,
    connectivityObserver: NetworkConnectivityObserver,
    onOpenFilePicker: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Unit,
    onRequestRecordAudioPermission: (() -> Unit) -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var uiState by remember { mutableStateOf(WebUiState()) }
    var initialLoadCompleted by remember { mutableStateOf(false) }
    var activePermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }

    // Listen for live network changes
    LaunchedEffect(Unit) {
        connectivityObserver.isConnected.collectLatest { isConnected ->
            val wasOffline = !uiState.isNetworkAvailable
            uiState = uiState.copy(
                isNetworkAvailable = isConnected,
                error = if (!isConnected) WebErrorType.Offline else if (uiState.error is WebErrorType.Offline) null else uiState.error
            )
            // Auto reload when regaining connection
            if (wasOffline && isConnected && uiState.error is WebErrorType.Offline) {
                uiState = uiState.copy(error = null)
                webView.reload()
            }
        }
    }

    // Android Hardware Back button handling
    BackHandler {
        if (uiState.showAboutDialog) {
            uiState = uiState.copy(showAboutDialog = false)
        } else if (uiState.showExitDialog) {
            uiState = uiState.copy(showExitDialog = false)
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            // Show exit confirmation so creators don't accidentally close studio
            uiState = uiState.copy(showExitDialog = true)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = StudioBackground,
        contentWindowInsets = WindowInsets.systemBars,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(StudioBackground)
        ) {
            // WebView Layer - Full-bleed native container for Vercel web application
            AudioFactoryWebView(
                webView = webView,
                downloadManager = downloadManager,
                callback = object : WebViewHostCallback {
                    override fun onProgressChanged(progress: Int) {
                        uiState = uiState.copy(progress = progress)
                    }

                    override fun onLoadingStateChanged(isLoading: Boolean) {
                        uiState = uiState.copy(isLoading = isLoading)
                        if (!isLoading && uiState.error == null) {
                            initialLoadCompleted = true
                        }
                    }

                    override fun onTitleChanged(title: String) {
                        uiState = uiState.copy(title = title)
                    }

                    override fun onNavigationStateChanged(
                        canGoBack: Boolean,
                        canGoForward: Boolean,
                        url: String
                    ) {
                        uiState = uiState.copy(
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            currentUrl = url
                        )
                    }

                    override fun onErrorReceived(errorType: WebErrorType?) {
                        uiState = uiState.copy(error = errorType)
                    }

                    override fun onShowFileChooser(
                        filePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: WebChromeClient.FileChooserParams
                    ): Boolean {
                        onOpenFilePicker(filePathCallback, fileChooserParams)
                        return true
                    }

                    override fun onMicrophonePermissionRequested(request: PermissionRequest) {
                        activePermissionRequest = request
                        uiState = uiState.copy(pendingMicrophoneRequest = true)
                    }

                    override fun onExternalUrl(url: String) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Minimalist subtle loading line at the top during web page transitions
            if (uiState.isLoading && uiState.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { uiState.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.TopCenter),
                    color = StudioBlue,
                    trackColor = Color.Transparent
                )
            }

            // Initial loading splash screen
            AnimatedVisibility(
                visible = !initialLoadCompleted && uiState.error == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                AudioLoadingIndicator(progress = uiState.progress)
            }

            // Error or Offline state overlay
            val activeError = uiState.error
            if (activeError != null) {
                ErrorAndOfflineView(
                    errorType = activeError,
                    canGoBack = uiState.canGoBack,
                    onRetry = {
                        uiState = uiState.copy(error = null)
                        webView.reload()
                    },
                    onOpenNetworkSettings = {
                        try {
                            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    },
                    onGoBack = {
                        uiState = uiState.copy(error = null)
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            webView.loadUrl(AudioFactoryWebPolicy.START_URL)
                        }
                    }
                )
            }
        }
    }

    // Exit confirmation dialog
    if (uiState.showExitDialog) {
        ExitConfirmDialog(
            onConfirm = {
                uiState = uiState.copy(showExitDialog = false)
                onExitApp()
            },
            onDismiss = {
                uiState = uiState.copy(showExitDialog = false)
            }
        )
    }

    // About Dialog
    if (uiState.showAboutDialog) {
        AboutDialog(
            onDismiss = {
                uiState = uiState.copy(showAboutDialog = false)
            }
        )
    }

    // Microphone Permission Rationale Dialog
    if (uiState.pendingMicrophoneRequest) {
        MicrophoneRationaleDialog(
            onGrant = {
                uiState = uiState.copy(pendingMicrophoneRequest = false)
                onRequestRecordAudioPermission {
                    activePermissionRequest?.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    activePermissionRequest = null
                }
            },
            onDeny = {
                uiState = uiState.copy(pendingMicrophoneRequest = false)
                activePermissionRequest?.deny()
                activePermissionRequest = null
            }
        )
    }
}
