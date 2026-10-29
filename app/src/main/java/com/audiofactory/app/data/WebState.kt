package com.audiofactory.app.data

sealed interface WebErrorType {
    object Offline : WebErrorType
    data class DnsError(val host: String) : WebErrorType
    data class SslError(val primaryError: Int) : WebErrorType
    data class HttpError(val statusCode: Int, val description: String) : WebErrorType
    object RendererCrash : WebErrorType
    data class Unknown(val description: String) : WebErrorType
}

data class WebUiState(
    val isLoading: Boolean = true,
    val progress: Int = 0,
    val error: WebErrorType? = null,
    val isNetworkAvailable: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val currentUrl: String = "https://audiofactory.vercel.app",
    val title: String = "Audio Factory",
    val isSecure: Boolean = true,
    val showExitDialog: Boolean = false,
    val showAboutDialog: Boolean = false,
    val pendingMicrophoneRequest: Boolean = false,
    val statusMessage: String? = null
)
