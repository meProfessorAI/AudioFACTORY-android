package com.audiofactory.app.auth

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.audiofactory.app.R
import com.audiofactory.app.policy.AudioFactoryWebPolicy
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: FirebaseUser, val isAnonymous: Boolean) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthManager(
    private val context: Context
) {
    private val auth: FirebaseAuth = Firebase.auth
    private val credentialManager = CredentialManager.create(context)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Track Firebase Auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _authState.value = AuthState.Authenticated(user, user.isAnonymous)
            } else {
                _authState.value = AuthState.Idle
            }
        }
    }

    /**
     * Attempts silent auto-login on startup using Credential Manager if previous session exists.
     */
    fun attemptAutoSignIn(
        webView: WebView?,
        scope: CoroutineScope
    ) {
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.isAnonymous) {
            Log.d(TAG, "Already signed in as: ${currentUser.email ?: currentUser.uid}")
            return
        }

        val serverClientId = getWebClientId() ?: return

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    processGoogleIdToken(idToken, webView)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Silent auto sign-in skipped: ${e.message}")
            }
        }
    }

    /**
     * Performs interactive Google Sign-In using Credential Manager.
     */
    fun signInWithGoogle(
        activity: Activity,
        webView: WebView?,
        scope: CoroutineScope,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val serverClientId = getWebClientId()
        if (serverClientId.isNullOrBlank()) {
            val err = "Missing Google Web Client ID configuration."
            Log.e(TAG, err)
            onError?.invoke(err)
            _authState.value = AuthState.Error(err)
            return
        }

        _authState.value = AuthState.Loading

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    val success = processGoogleIdToken(idToken, webView)
                    if (success) {
                        onSuccess?.invoke()
                    } else {
                        onError?.invoke("Failed to process Google sign-in credentials.")
                    }
                } else {
                    val err = "Unsupported credential response type"
                    Log.e(TAG, err)
                    _authState.value = AuthState.Error(err)
                    onError?.invoke(err)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}")
                _authState.value = AuthState.Idle
                sendAuthStatusToWeb(webView, "cancelled", "User cancelled Google Sign-In")
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential Manager error: ${e.message}", e)
                val msg = e.localizedMessage ?: "Sign in failed"
                _authState.value = AuthState.Error(msg)
                onError?.invoke(msg)
                sendAuthStatusToWeb(webView, "failed", msg)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during Google Sign-In", e)
                val msg = e.localizedMessage ?: "Sign in failed"
                _authState.value = AuthState.Error(msg)
                onError?.invoke(msg)
                sendAuthStatusToWeb(webView, "failed", msg)
            }
        }
    }

    /**
     * Authenticates Firebase Auth with Google ID Token, handles account linking for anonymous users,
     * and forwards the ID token to the React web application via the JS bridge.
     */
    private suspend fun processGoogleIdToken(
        idToken: String,
        webView: WebView?
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val googleCredential = GoogleAuthProvider.getCredential(idToken, null)
                val currentUser = auth.currentUser

                if (currentUser != null && currentUser.isAnonymous) {
                    Log.d(TAG, "Attempting to link anonymous user (${currentUser.uid}) with Google credential...")
                    try {
                        val linkResult = currentUser.linkWithCredential(googleCredential).await()
                        val linkedUser = linkResult.user
                        Log.d(TAG, "Account successfully linked! UID: ${linkedUser?.uid}")
                    } catch (e: FirebaseAuthUserCollisionException) {
                        Log.w(TAG, "Google account already exists. Signing into existing Google account...")
                        auth.signInWithCredential(googleCredential).await()
                    } catch (e: Exception) {
                        Log.w(TAG, "Link failed (${e.message}), signing in directly with Google credential...")
                        auth.signInWithCredential(googleCredential).await()
                    }
                } else if (currentUser == null || currentUser.isAnonymous) {
                    auth.signInWithCredential(googleCredential).await()
                }

                val finalUser = auth.currentUser
                Log.d(TAG, "Authenticated user UID: ${finalUser?.uid}, email: ${finalUser?.email}")

                withContext(Dispatchers.Main) {
                    if (finalUser != null) {
                        _authState.value = AuthState.Authenticated(finalUser, finalUser.isAnonymous)
                    }
                    if (webView != null) {
                        sendTokenToWeb(webView, idToken)
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to authenticate Firebase with Google credential: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.Error(e.localizedMessage ?: "Authentication failed")
                    if (webView != null) {
                        sendAuthStatusToWeb(webView, "failed", e.localizedMessage ?: "Auth failed")
                    }
                }
                false
            }
        }
    }

    /**
     * Invokes JavaScript bridge function window.AudioFactoryNative.onGoogleIdToken(idToken)
     * strictly verifying the active origin is trusted.
     */
    fun sendTokenToWeb(webView: WebView, idToken: String) {
        val currentUrl = webView.url ?: ""
        val uri = Uri.parse(currentUrl)

        if (!AudioFactoryWebPolicy.isTrustedOrigin(uri)) {
            Log.e(TAG, "Security Guard: Refusing to pass Google ID token to untrusted origin: $currentUrl")
            return
        }

        val escapedToken = idToken.replace("\\", "\\\\").replace("'", "\\'")
        val jsScript = """
            (function() {
                try {
                    console.log("[NativeBridge] Delivering Google ID Token to web application...");
                    if (window.AudioFactoryNative && typeof window.AudioFactoryNative.onGoogleIdToken === 'function') {
                        window.AudioFactoryNative.onGoogleIdToken('$escapedToken');
                    } else if (window.AudioFactoryBridge && typeof window.AudioFactoryBridge.onGoogleIdToken === 'function') {
                        window.AudioFactoryBridge.onGoogleIdToken('$escapedToken');
                    } else if (typeof window.onGoogleIdToken === 'function') {
                        window.onGoogleIdToken('$escapedToken');
                    } else {
                        console.warn("[NativeBridge] window.AudioFactoryNative.onGoogleIdToken is not defined on web page.");
                    }
                } catch (e) {
                    console.error("[NativeBridge] Exception delivering ID token: ", e);
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsScript) { result ->
            Log.d(TAG, "Evaluated ID token injection script. Result: $result")
        }
    }

    /**
     * Sends auth status notification back to Web.
     */
    fun sendAuthStatusToWeb(webView: WebView?, status: String, message: String?) {
        if (webView == null) return
        val currentUrl = webView.url ?: ""
        if (!AudioFactoryWebPolicy.isTrustedOrigin(Uri.parse(currentUrl))) return

        val safeMsg = (message ?: "").replace("\\", "\\\\").replace("'", "\\'")
        val jsScript = """
            (function() {
                if (window.AudioFactoryNative && typeof window.AudioFactoryNative.onAuthStatus === 'function') {
                    window.AudioFactoryNative.onAuthStatus('$status', '$safeMsg');
                } else if (window.AudioFactoryBridge && typeof window.AudioFactoryBridge.onAuthStatus === 'function') {
                    window.AudioFactoryBridge.onAuthStatus('$status', '$safeMsg');
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsScript, null)
    }

    /**
     * Signs out of Firebase Auth and clears Credential Manager session.
     */
    fun signOut(scope: CoroutineScope, onComplete: (() -> Unit)? = null) {
        auth.signOut()
        _authState.value = AuthState.Idle
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing credential state: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    private fun getWebClientId(): String? {
        return try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.e(TAG, "Resource default_web_client_id not found in strings.xml", e)
            null
        }
    }

    companion object {
        private const val TAG = "AudioFactoryAuth"
    }
}
