package com.audiofactory.app

import com.audiofactory.app.policy.AudioFactoryWebPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFactoryWebPolicyTest {

    @Test
    fun `trusted origin returns true for official audio factory domains`() {
        val prodUrl = "https://audiofactory.vercel.app"
        val prodSubUrl = "https://audiofactory.vercel.app/studio/generate"
        val altUrl = "https://audiofactory.app"

        assertTrue(AudioFactoryWebPolicy.isTrustedOrigin(prodUrl))
        assertTrue(AudioFactoryWebPolicy.isTrustedOrigin(prodSubUrl))
        assertTrue(AudioFactoryWebPolicy.isTrustedOrigin(altUrl))
    }

    @Test
    fun `trusted origin rejects third-party and malicious domains`() {
        val phishingUrl = "https://audiofactory.vercel.app.attacker.com"
        val evilUrl = "https://evil-audio.com"
        val googleUrl = "https://accounts.google.com"

        assertFalse(AudioFactoryWebPolicy.isTrustedOrigin(phishingUrl))
        assertFalse(AudioFactoryWebPolicy.isTrustedOrigin(evilUrl))
        assertFalse(AudioFactoryWebPolicy.isTrustedOrigin(googleUrl))
    }

    @Test
    fun `auth host recognizes firebase auth and google oauth urls`() {
        val firebaseAuthUrl = "https://audiofactory-eia.firebaseapp.com/__/auth/handler?state=AMbdmDnrTVFRw86U8axVqYNKUxhjZLqGrVf6VE6aVZ5&code=4/0AUn6Hd"
        val googleOAuthUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=123"
        val googleApisUrl = "https://oauth2.googleapis.com/token"

        assertTrue(AudioFactoryWebPolicy.isAuthHost(firebaseAuthUrl))
        assertTrue(AudioFactoryWebPolicy.isAuthHost(googleOAuthUrl))
        assertTrue(AudioFactoryWebPolicy.isAuthHost(googleApisUrl))

        val randomUrl = "https://example.com"
        assertFalse(AudioFactoryWebPolicy.isAuthHost(randomUrl))
    }

    @Test
    fun `isAllowedInWebView permits both application origin and authentication flows`() {
        val appUrl = "https://audiofactory.vercel.app/pricing"
        val authUrl = "https://audiofactory-eia.firebaseapp.com/__/auth/handler"
        val googleUrl = "https://accounts.google.com"
        val untrustedUrl = "https://untrusted-external-site.com"

        assertTrue(AudioFactoryWebPolicy.isAllowedInWebView(appUrl))
        assertTrue(AudioFactoryWebPolicy.isAllowedInWebView(authUrl))
        assertTrue(AudioFactoryWebPolicy.isAllowedInWebView(googleUrl))
        assertFalse(AudioFactoryWebPolicy.isAllowedInWebView(untrustedUrl))
    }

    @Test
    fun `external action schemes identified correctly`() {
        val mailUrl = "mailto:support@audiofactory.app"
        val telUrl = "tel:+1234567890"
        val webUrl = "https://audiofactory.vercel.app"

        assertTrue(AudioFactoryWebPolicy.isExternalActionScheme(mailUrl))
        assertTrue(AudioFactoryWebPolicy.isExternalActionScheme(telUrl))
        assertFalse(AudioFactoryWebPolicy.isExternalActionScheme(webUrl))
    }

    @Test
    fun `disallowed schemes are rejected by safe scheme check`() {
        val jsUrl = "javascript:alert(1)"
        val fileUrl = "file:///data/data/com.audiofactory.app"
        val contentUrl = "content://settings/system"

        assertFalse(AudioFactoryWebPolicy.isSafeScheme(jsUrl))
        assertFalse(AudioFactoryWebPolicy.isSafeScheme(fileUrl))
        assertFalse(AudioFactoryWebPolicy.isSafeScheme(contentUrl))
    }

    @Test
    fun `start url points to production Audio Factory deployment`() {
        assertEquals("https://audiofactory.vercel.app", AudioFactoryWebPolicy.START_URL)
    }
}
