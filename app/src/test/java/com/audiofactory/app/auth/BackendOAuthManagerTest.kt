package com.audiofactory.app.auth

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.audiofactory.app.policy.AudioFactoryWebPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class BackendOAuthManagerTest {

    private lateinit var context: Context
    private lateinit var oauthManager: BackendOAuthManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        oauthManager = BackendOAuthManager.getInstance(context)
    }

    @Test
    fun `pkce generation satisfies rfc 7636 sha256 specs`() {
        val verifier = oauthManager.generateCodeVerifier()
        assertNotNull(verifier)
        assertTrue(verifier.length >= 43)

        val challenge = oauthManager.computeCodeChallenge(verifier)
        assertNotNull(challenge)
        assertTrue(challenge.isNotBlank())
        assertFalse(challenge.contains("+"))
        assertFalse(challenge.contains("/"))
        assertFalse(challenge.contains("="))
    }

    @Test
    fun `csrf state token generation generates unique cryptographic nonces`() {
        val state1 = oauthManager.generateStateToken()
        val state2 = oauthManager.generateStateToken()

        assertNotNull(state1)
        assertNotNull(state2)
        assertTrue(state1 != state2)
    }

    @Test
    fun `android app links return routes are recognized accurately`() {
        val link1 = Uri.parse("https://audiofactory.vercel.app/auth/callback?code=abc&state=xyz")
        val link2 = Uri.parse("https://audiofactory.vercel.app/api/auth/callback?code=abc&state=xyz")
        val link3 = Uri.parse("https://audiofactory.vercel.app/oauth/callback?code=abc&state=xyz")
        val linkFallback = Uri.parse("com.audiofactory.app://oauth-callback?code=abc&state=xyz")
        val regularLink = Uri.parse("https://audiofactory.vercel.app/dashboard")
        val untrustedLink = Uri.parse("https://malicious-domain.com/auth/callback")

        assertTrue(oauthManager.isOAuthCallback(link1))
        assertTrue(oauthManager.isOAuthCallback(link2))
        assertTrue(oauthManager.isOAuthCallback(link3))
        assertTrue(oauthManager.isOAuthCallback(linkFallback))
        assertFalse(oauthManager.isOAuthCallback(regularLink))
        assertFalse(oauthManager.isOAuthCallback(untrustedLink))
    }

    @Test
    fun `oauth callback with cancelled error returns cancelled result`() {
        val uri = Uri.parse("https://audiofactory.vercel.app/auth/callback?error=access_denied&error_description=User%20denied%20access")

        var result: AuthResult? = null
        oauthManager.handleCallbackUri(uri) {
            result = it
        }

        assertTrue(result is AuthResult.Cancelled)
        assertEquals("User denied access", (result as AuthResult.Cancelled).reason)
    }

    @Test
    fun `oauth callback with invalid state returns invalid state error`() {
        val uri = Uri.parse("https://audiofactory.vercel.app/auth/callback?code=test-code&state=forged-state-token")

        var result: AuthResult? = null
        oauthManager.handleCallbackUri(uri) {
            result = it
        }

        assertTrue(result is AuthResult.InvalidState)
    }

    @Test
    fun `auth session model serializes and deserializes accurately`() {
        val originalSession = AuthSession(
            sessionToken = "af_sess_998877",
            idToken = "google_id_token_12345",
            userId = "usr_42",
            userEmail = "creator@audiofactory.app",
            displayName = "Audio Creator",
            expiresAt = System.currentTimeMillis() + 3600000
        )

        val json = originalSession.toJson()
        val parsed = AuthSession.fromJson(json)

        assertNotNull(parsed)
        assertEquals(originalSession.sessionToken, parsed?.sessionToken)
        assertEquals(originalSession.idToken, parsed?.idToken)
        assertEquals(originalSession.userId, parsed?.userId)
        assertEquals(originalSession.userEmail, parsed?.userEmail)
        assertEquals(originalSession.displayName, parsed?.displayName)
        assertFalse(parsed!!.isExpired())
    }

    @Test
    fun `expired session correctly evaluates isExpired`() {
        val expiredSession = AuthSession(
            sessionToken = "af_sess_old",
            expiresAt = System.currentTimeMillis() - 1000
        )
        assertTrue(expiredSession.isExpired())

        val activeSession = AuthSession(
            sessionToken = "af_sess_active",
            expiresAt = System.currentTimeMillis() + 100000
        )
        assertFalse(activeSession.isExpired())
    }

    @Test
    fun `session persistence and logout correctly resets state`() {
        val session = AuthSession(
            sessionToken = "test_persistence_token",
            userEmail = "test@audiofactory.app",
            expiresAt = System.currentTimeMillis() + 86400000
        )

        oauthManager.saveSession(session)
        assertEquals(session.sessionToken, oauthManager.currentSession?.sessionToken)

        val latch = CountDownLatch(1)
        oauthManager.logout(context, null) {
            latch.countDown()
        }
        latch.await(3, TimeUnit.SECONDS)

        assertNull(oauthManager.currentSession)
    }

    @Test
    fun `web policy isolates google oauth urls and trusted origins`() {
        val googleOAuth = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth?client_id=123")
        val backendLogin = Uri.parse("https://audiofactory.vercel.app/auth/login?provider=google")
        val trustedDashboard = Uri.parse("https://audiofactory.vercel.app/app")
        val untrusted = Uri.parse("https://external-phishing-site.com/login")

        assertTrue(AudioFactoryWebPolicy.isGoogleAuthUrl(googleOAuth))
        assertTrue(AudioFactoryWebPolicy.isBackendAuthInitiation(backendLogin))
        assertTrue(AudioFactoryWebPolicy.isTrustedOrigin(trustedDashboard))
        assertFalse(AudioFactoryWebPolicy.isTrustedOrigin(untrusted))
    }
}
