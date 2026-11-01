package com.audiofactory.app.auth

import org.json.JSONObject

/**
 * Represents an authenticated session established with the Audio Factory Vercel backend.
 */
data class AuthSession(
    val sessionToken: String,
    val idToken: String? = null,
    val userId: String? = null,
    val userEmail: String? = null,
    val displayName: String? = null,
    val expiresAt: Long = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L) // 7 days default
) {
    /**
     * Checks if this session has reached its expiry timestamp.
     */
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt

    /**
     * Serializes session to JSON string for local storage.
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("sessionToken", sessionToken)
            put("idToken", idToken ?: "")
            put("userId", userId ?: "")
            put("userEmail", userEmail ?: "")
            put("displayName", displayName ?: "")
            put("expiresAt", expiresAt)
        }.toString()
    }

    companion object {
        /**
         * Deserializes session from stored JSON string.
         */
        fun fromJson(jsonStr: String?): AuthSession? {
            if (jsonStr.isNullOrBlank()) return null
            return try {
                val json = JSONObject(jsonStr)
                val token = json.optString("sessionToken", "")
                if (token.isBlank()) return null
                AuthSession(
                    sessionToken = token,
                    idToken = json.optString("idToken").takeIf { it.isNotBlank() },
                    userId = json.optString("userId").takeIf { it.isNotBlank() },
                    userEmail = json.optString("userEmail").takeIf { it.isNotBlank() },
                    displayName = json.optString("displayName").takeIf { it.isNotBlank() },
                    expiresAt = json.optLong("expiresAt", 0L)
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Result sealed hierarchy for backend OAuth callback and handoff exchange.
 */
sealed class AuthResult {
    data class Success(val session: AuthSession) : AuthResult()
    data class Cancelled(val reason: String = "User cancelled authentication") : AuthResult()
    data class InvalidState(val expected: String?, val received: String?) : AuthResult()
    data class ExpiredCode(val message: String = "Authentication handoff code has expired") : AuthResult()
    data class CodeAlreadyUsed(val code: String) : AuthResult()
    data class ExchangeFailed(val statusCode: Int, val message: String) : AuthResult()
    data class NetworkError(val error: Throwable) : AuthResult()
}
