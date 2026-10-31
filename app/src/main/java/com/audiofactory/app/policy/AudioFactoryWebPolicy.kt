package com.audiofactory.app.policy

import android.net.Uri

object AudioFactoryWebPolicy {
    const val START_URL = "https://audiofactory.vercel.app"
    const val PRIVACY_URL = "https://audiofactory.vercel.app/privacy"
    const val TERMS_URL = "https://audiofactory.vercel.app/terms"

    private val TRUSTED_HOSTS = setOf(
        "audiofactory.vercel.app",
        "audiofactory.app"
    )

    private val AUTH_HOSTS = setOf(
        "audiofactory-eia.firebaseapp.com",
        "audiofactory-eia.web.app",
        "firebaseapp.com",
        "web.app",
        "accounts.google.com",
        "accounts.youtube.com",
        "oauth2.googleapis.com",
        "apis.google.com",
        "ssl.gstatic.com",
        "www.gstatic.com",
        "identitytoolkit.googleapis.com",
        "securetoken.googleapis.com",
        "google.com",
        "myaccount.google.com",
        "github.com",
        "appleid.apple.com"
    )

    /**
     * Checks if a given URI belongs to the official Audio Factory domain.
     * Strictly used for device hardware and microphone permission gating.
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

    fun isTrustedOrigin(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val javaUri = java.net.URI(url)
            val scheme = javaUri.scheme?.lowercase() ?: return false
            if (scheme != "https" && scheme != "http") return false
            val host = javaUri.host?.lowercase() ?: return false
            TRUSTED_HOSTS.any { trusted ->
                host == trusted || host.endsWith(".$trusted")
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if a given URI belongs to Firebase Authentication or Google OAuth flow.
     */
    fun isAuthHost(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && scheme != "http") return false
        val host = uri.host?.lowercase() ?: return false

        return AUTH_HOSTS.any { authHost ->
            host == authHost || host.endsWith(".$authHost")
        }
    }

    fun isAuthHost(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val javaUri = java.net.URI(url)
            val scheme = javaUri.scheme?.lowercase() ?: return false
            if (scheme != "https" && scheme != "http") return false
            val host = javaUri.host?.lowercase() ?: return false
            AUTH_HOSTS.any { authHost ->
                host == authHost || host.endsWith(".$authHost")
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if a URI should be permitted to load inside the app WebView
     * (First-party application screens + Firebase Authentication & Google OAuth redirects).
     */
    fun isAllowedInWebView(uri: Uri?): Boolean {
        return isTrustedOrigin(uri) || isAuthHost(uri)
    }

    fun isAllowedInWebView(url: String?): Boolean {
        return isTrustedOrigin(url) || isAuthHost(url)
    }

    /**
     * Determines whether an intent/URL should be handled by external browser or app.
     */
    fun isExternalActionScheme(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme in setOf("mailto", "tel", "sms")
    }

    fun isExternalActionScheme(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val scheme = url.substringBefore(':', "").lowercase()
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

    fun isSafeScheme(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val scheme = url.substringBefore(':', "").lowercase()
        return scheme in setOf("https", "http", "mailto", "tel")
    }
}
