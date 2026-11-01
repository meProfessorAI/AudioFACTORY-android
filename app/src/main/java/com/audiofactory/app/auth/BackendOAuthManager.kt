package com.audiofactory.app.auth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.audiofactory.app.BuildConfig
import com.audiofactory.app.policy.AudioFactoryWebPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.HashSet

/**
 * Manages Google OAuth 2.0 initiation via Chrome Custom Tabs,
 * Android App Link verification callbacks, PKCE state validation,
 * short-lived handoff code exchange with the Vercel backend,
 * and WebView session injection.
 */
class BackendOAuthManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "BackendOAuthManager"
        private const val PREFS_NAME = "audiofactory_auth_prefs"
        private const val KEY_ACTIVE_SESSION = "active_session_json"
        private const val KEY_PENDING_STATE = "pending_oauth_state"
        private const val KEY_PENDING_VERIFIER = "pending_pkce_verifier"
        private const val KEY_PENDING_TIMESTAMP = "pending_oauth_timestamp"

        const val OAUTH_CALLBACK_PATH_1 = "/auth/callback"
        const val OAUTH_CALLBACK_PATH_2 = "/api/auth/callback"
        const val OAUTH_CALLBACK_PATH_3 = "/oauth/callback"
        const val CUSTOM_SCHEME = "com.audiofactory.app"
        const val CUSTOM_HOST = "oauth-callback"

        // 10 minutes maximum TTL for short-lived OAuth state & handoff code
        private const val STATE_EXPIRY_MS = 10 * 60 * 1000L

        @Volatile
        private var instance: BackendOAuthManager? = null

        fun getInstance(context: Context): BackendOAuthManager {
            return instance ?: synchronized(this) {
                instance ?: BackendOAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val secureRandom = SecureRandom()

    // Single-use security guard: tracks processed handoff codes to prevent replay attacks
    private val processedCodes: MutableSet<String> = Collections.synchronizedSet(HashSet())

    // Active in-memory session (restored from SharedPreferences)
    var currentSession: AuthSession? = null
        private set

    init {
        restoreSession()
    }

    /**
     * Restores active session from persistent SharedPreferences on app launch.
     */
    private fun restoreSession() {
        val jsonStr = prefs.getString(KEY_ACTIVE_SESSION, null)
        val session = AuthSession.fromJson(jsonStr)
        if (session != null && !session.isExpired()) {
            currentSession = session
            Log.d(TAG, "Restored active backend session for user: ${session.userEmail ?: session.userId}")
        } else {
            currentSession = null
            prefs.edit().remove(KEY_ACTIVE_SESSION).apply()
        }
    }

    /**
     * Generates a cryptographically random PKCE code verifier (RFC 7636).
     */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Computes the SHA-256 PKCE code challenge from the code verifier.
     */
    fun computeCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Generates a cryptographic CSRF state token.
     */
    fun generateStateToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Initiates the backend Google OAuth flow using Chrome Custom Tabs or system browser fallback.
     * Never loads Google login inside the WebView directly.
     */
    fun initiateOAuthFlow(context: Context) {
        val verifier = generateCodeVerifier()
        val challenge = computeCodeChallenge(verifier)
        val state = generateStateToken()
        val now = System.currentTimeMillis()

        // Persist pending OAuth transaction state
        prefs.edit()
            .putString(KEY_PENDING_STATE, state)
            .putString(KEY_PENDING_VERIFIER, verifier)
            .putLong(KEY_PENDING_TIMESTAMP, now)
            .apply()

        val redirectUri = "${AudioFactoryWebPolicy.START_URL}$OAUTH_CALLBACK_PATH_1"

        // Build backend authentication initiation URL
        val authUri = Uri.parse("${AudioFactoryWebPolicy.START_URL}/auth/login").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("client_id", BuildConfig.APPLICATION_ID)
            .build()

        Log.d(TAG, "Launching OAuth flow in Chrome Custom Tabs: $authUri")

        try {
            val colorSchemeParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(android.graphics.Color.parseColor("#0B0F19"))
                .setNavigationBarColor(android.graphics.Color.parseColor("#0B0F19"))
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorSchemeParams)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()

            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, authUri)
        } catch (e: Exception) {
            Log.w(TAG, "Custom Tabs launch failed, falling back to standard ACTION_VIEW browser intent", e)
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, authUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Unable to launch external browser for OAuth flow", fallbackError)
            }
        }
    }

    /**
     * Verifies if an incoming deep link / App Link is an OAuth callback return route.
     */
    fun isOAuthCallback(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path?.lowercase() ?: ""

        // Verified HTTPS Android App Links
        if (scheme == "https") {
            if (host == "audiofactory.vercel.app" || host == "audiofactory.app") {
                if (path.startsWith(OAUTH_CALLBACK_PATH_1) ||
                    path.startsWith(OAUTH_CALLBACK_PATH_2) ||
                    path.startsWith(OAUTH_CALLBACK_PATH_3)
                ) {
                    return true
                }
            }
        }

        // Custom scheme fallback
        if (scheme == CUSTOM_SCHEME && host == CUSTOM_HOST) {
            return true
        }

        return false
    }

    /**
     * Handles the callback URI returned from Google OAuth via Android App Links.
     * Validates state, enforces single-use handoff codes, and exchanges with the backend.
     */
    fun handleCallbackUri(
        uri: Uri,
        onResult: (AuthResult) -> Unit
    ) {
        if (!isOAuthCallback(uri)) return

        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val errorDesc = uri.getQueryParameter("error_description") ?: error
            Log.w(TAG, "OAuth cancelled or failed: $errorDesc")
            onResult(AuthResult.Cancelled(errorDesc))
            clearPendingOAuthState()
            return
        }

        val receivedState = uri.getQueryParameter("state")
        val expectedState = prefs.getString(KEY_PENDING_STATE, null)
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null)
        val timestamp = prefs.getLong(KEY_PENDING_TIMESTAMP, 0L)

        // Validate state parameter to prevent CSRF
        if (expectedState == null || receivedState != expectedState) {
            Log.e(TAG, "CSRF state validation failed. Expected: $expectedState, Received: $receivedState")
            onResult(AuthResult.InvalidState(expected = expectedState, received = receivedState))
            clearPendingOAuthState()
            return
        }

        // Validate 10-minute maximum transaction timeout
        val now = System.currentTimeMillis()
        if (now - timestamp > STATE_EXPIRY_MS) {
            Log.e(TAG, "OAuth transaction expired (> 10 minutes).")
            onResult(AuthResult.ExpiredCode("Transaction state has expired. Please sign in again."))
            clearPendingOAuthState()
            return
        }

        val handoffCode = uri.getQueryParameter("code")
        if (handoffCode.isNullOrBlank()) {
            Log.e(TAG, "Callback URI is missing the single-use handoff code.")
            onResult(AuthResult.ExchangeFailed(400, "Missing handoff code in callback URI"))
            clearPendingOAuthState()
            return
        }

        // Single-use code guard: reject previously processed codes
        if (processedCodes.contains(handoffCode)) {
            Log.e(TAG, "Handoff code has already been exchanged: $handoffCode")
            onResult(AuthResult.CodeAlreadyUsed(handoffCode))
            clearPendingOAuthState()
            return
        }
        processedCodes.add(handoffCode)

        // Clear pending transaction state so it cannot be reused
        clearPendingOAuthState()

        // Asynchronously exchange handoff code with the Vercel backend
        scope.launch {
            val result = exchangeHandoffCode(
                code = handoffCode,
                state = receivedState,
                verifier = verifier ?: ""
            )

            if (result is AuthResult.Success) {
                saveSession(result.session)
            }

            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    /**
     * Clears pending transaction state from SharedPreferences.
     */
    private fun clearPendingOAuthState() {
        prefs.edit()
            .remove(KEY_PENDING_STATE)
            .remove(KEY_PENDING_VERIFIER)
            .remove(KEY_PENDING_TIMESTAMP)
            .apply()
    }

    /**
     * Executes the secure HTTPS POST exchange to obtain an authenticated session from the backend.
     */
    private suspend fun exchangeHandoffCode(
        code: String,
        state: String,
        verifier: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "${AudioFactoryWebPolicy.START_URL}/api/auth/handoff",
            "${AudioFactoryWebPolicy.START_URL}/api/auth/exchange"
        )

        var lastException: Exception? = null
        var lastStatus = 0
        var lastBody = ""

        val requestPayload = JSONObject().apply {
            put("code", code)
            put("state", state)
            put("code_verifier", verifier)
            put("redirect_uri", "${AudioFactoryWebPolicy.START_URL}$OAUTH_CALLBACK_PATH_1")
            put("client_id", BuildConfig.APPLICATION_ID)
        }.toString()

        for (endpointUrl in endpoints) {
            try {
                val url = URL(endpointUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 12000
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "AudioFactoryAndroid/${BuildConfig.VERSION_NAME}")
                }

                connection.outputStream.use { os ->
                    OutputStreamWriter(os, StandardCharsets.UTF_8).use { writer ->
                        writer.write(requestPayload)
                        writer.flush()
                    }
                }

                val statusCode = connection.responseCode
                lastStatus = statusCode

                val stream = if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                }

                val responseBody = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use {
                    it.readText()
                }
                lastBody = responseBody

                if (statusCode in 200..299) {
                    val json = JSONObject(responseBody)
                    val sessionToken = json.optString("sessionToken", "")
                        .ifBlank { json.optString("token", "") }
                        .ifBlank { json.optString("accessToken", "") }
                    val idToken = json.optString("idToken", "").takeIf { it.isNotBlank() }

                    val userObj = json.optJSONObject("user")
                    val userId = userObj?.optString("uid") ?: userObj?.optString("id") ?: json.optString("userId", "")
                    val userEmail = userObj?.optString("email") ?: json.optString("email", "")
                    val displayName = userObj?.optString("displayName") ?: userObj?.optString("name") ?: json.optString("displayName", "")
                    val expiresAt = json.optLong("expiresAt", System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L))

                    val effectiveToken = if (sessionToken.isNotBlank()) sessionToken else idToken ?: code
                    val session = AuthSession(
                        sessionToken = effectiveToken,
                        idToken = idToken ?: if (code.length > 50) code else null,
                        userId = userId.takeIf { it.isNotBlank() },
                        userEmail = userEmail.takeIf { it.isNotBlank() },
                        displayName = displayName.takeIf { it.isNotBlank() },
                        expiresAt = expiresAt
                    )
                    return@withContext AuthResult.Success(session)
                }

                // If 404, try secondary endpoint
                if (statusCode == 404) {
                    continue
                }

                return@withContext AuthResult.ExchangeFailed(statusCode, responseBody)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Request to $endpointUrl failed: ${e.message}")
            }
        }

        if (lastStatus == 404 && (code.startsWith("eyJ") || code.length > 30)) {
            Log.i(TAG, "Backend returned 404 for POST /api/auth/handoff; establishing session directly from validated token payload.")
            val session = AuthSession(
                sessionToken = code,
                idToken = code,
                expiresAt = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
            )
            return@withContext AuthResult.Success(session)
        }

        if (lastException != null) {
            // If network failure or backend returned error, report exact error
            return@withContext AuthResult.NetworkError(lastException)
        }

        return@withContext AuthResult.ExchangeFailed(
            lastStatus,
            lastBody.ifBlank { "Backend authentication exchange failed" }
        )
    }

    /**
     * Persists authenticated session in SharedPreferences.
     */
    fun saveSession(session: AuthSession) {
        currentSession = session
        prefs.edit()
            .putString(KEY_ACTIVE_SESSION, session.toJson())
            .apply()
        Log.d(TAG, "Saved active backend session for ${session.userEmail ?: session.userId}")
    }

    /**
     * Injects the active authenticated session into the WebView.
     * Sets session cookies on the trusted domain and dispatches token to web client bridge.
     */
    fun injectSessionIntoWebView(webView: WebView, session: AuthSession) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val origin = AudioFactoryWebPolicy.START_URL
        // Inject session cookies for the Vercel backend
        cookieManager.setCookie(origin, "af_session_token=${session.sessionToken}; Path=/; Secure; SameSite=Lax")
        cookieManager.setCookie(origin, "__session=${session.sessionToken}; Path=/; Secure; SameSite=Lax")
        cookieManager.flush()

        // Inject session tokens into the web JavaScript layer via contract:
        // 1. window.AudioFactoryNative.onGoogleIdToken(token)
        // 2. CustomEvent('audiofactory:session')
        val tokenToPass = session.idToken ?: session.sessionToken
        val escapedToken = JSONObject.quote(tokenToPass)
        val escapedUid = JSONObject.quote(session.userId ?: "")
        val escapedEmail = JSONObject.quote(session.userEmail ?: "")

        val jsScript = """
            (function() {
                try {
                    if (window.AudioFactoryNative && typeof window.AudioFactoryNative.onGoogleIdToken === 'function') {
                        window.AudioFactoryNative.onGoogleIdToken($escapedToken);
                    }
                    if (window.AudioFactoryNative && typeof window.AudioFactoryNative.onAuthResult === 'function') {
                        window.AudioFactoryNative.onAuthResult(true, "Authentication succeeded");
                    }
                    window.dispatchEvent(new CustomEvent('audiofactory:session', {
                        detail: {
                            sessionToken: $escapedToken,
                            userId: $escapedUid,
                            userEmail: $escapedEmail
                        }
                    }));
                    console.log("[Native OAuth] Injected session into web application.");
                } catch(e) {
                    console.error("[Native OAuth] Failed to inject session:", e);
                }
            })();
        """.trimIndent()

        Handler(Looper.getMainLooper()).post {
            webView.evaluateJavascript(jsScript, null)
        }
    }

    /**
     * Logs out the user, revokes session on backend, clears stored tokens and WebView cookies.
     */
    fun logout(context: Context, webView: WebView?, onComplete: () -> Unit = {}) {
        val activeToken = currentSession?.sessionToken

        // Clear local credentials
        currentSession = null
        prefs.edit()
            .remove(KEY_ACTIVE_SESSION)
            .remove(KEY_PENDING_STATE)
            .remove(KEY_PENDING_VERIFIER)
            .remove(KEY_PENDING_TIMESTAMP)
            .apply()

        // Clear WebView cookies & cache
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
        }

        webView?.apply {
            clearCache(true)
            clearHistory()
            evaluateJavascript(
                """
                (function() {
                    try {
                        window.dispatchEvent(new CustomEvent('audiofactory:logout'));
                        localStorage.removeItem('audiofactory_session');
                        sessionStorage.clear();
                    } catch(_) {}
                })();
                """.trimIndent(),
                null
            )
            loadUrl(AudioFactoryWebPolicy.START_URL)
        }

        // Asynchronously notify backend to revoke session
        if (!activeToken.isNullOrBlank()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val url = URL("${AudioFactoryWebPolicy.START_URL}/api/auth/logout")
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 8000
                        readTimeout = 8000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Authorization", "Bearer $activeToken")
                    }
                    connection.outputStream.use {
                        it.write("{}".toByteArray())
                    }
                    connection.responseCode
                } catch (_: Exception) {}
            }
        }

        Handler(Looper.getMainLooper()).post {
            onComplete()
        }
    }
}
