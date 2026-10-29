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

    /**
     * Checks if a given URI belongs to the official Audio Factory domain.
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
}
