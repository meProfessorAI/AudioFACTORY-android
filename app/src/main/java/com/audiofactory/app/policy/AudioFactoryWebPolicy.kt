package com.audiofactory.app.policy

import android.net.Uri

object AudioFactoryWebPolicy {
    const val START_URL = "https://audiofactory.vercel.app"
    const val PRIVACY_URL = "https://audiofactory.vercel.app/privacy"
    const val TERMS_URL = "https://audiofactory.vercel.app/terms"

    val TRUSTED_HOSTS = setOf(
        "audiofactory.vercel.app",
        "audiofactory.app",
        "audiofactory-eia.firebaseapp.com",
        "audiofactory-eia.web.app"
    )

    /**
     * Checks if a given URI belongs to the official Audio Factory production domain.
     */
    fun isTrustedOrigin(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && scheme != "http") return false
        val host = uri.host?.lowercase() ?: return false

        return TRUSTED_HOSTS.any { trusted ->
            host == trusted || host.endsWith(".$trusted")
        }
    }

    /**
     * Determines whether an intent/URL should be handled by external browser or app.
     */
    fun isExternalActionScheme(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme in setOf("mailto", "tel", "sms")
    }

    /**
     * Validates if a scheme is strictly allowed for safe navigation.
     */
    fun isSafeScheme(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme in setOf("https", "http", "mailto", "tel")
    }

    /**
     * Detects Google OAuth authentication endpoints that MUST NOT load inside WebView.
     */
    fun isGoogleAuthUrl(uri: Uri?): Boolean {
        if (uri == null) return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path?.lowercase() ?: ""

        if (host == "accounts.google.com" || host.endsWith(".google.com") && host.startsWith("accounts.")) {
            return true
        }

        if (path.contains("/o/oauth2/") || path.contains("/signin/oauth")) {
            return true
        }

        return false
    }

    /**
     * Detects backend authentication initiation routes.
     */
    fun isBackendAuthInitiation(uri: Uri?): Boolean {
        if (uri == null) return false
        val path = uri.path?.lowercase() ?: ""
        val provider = uri.getQueryParameter("provider")?.lowercase()

        return (path.startsWith("/auth/login") || path.startsWith("/api/auth/google") || path.startsWith("/api/auth/signin"))
                && (provider == "google" || path.contains("google"))
    }
}
