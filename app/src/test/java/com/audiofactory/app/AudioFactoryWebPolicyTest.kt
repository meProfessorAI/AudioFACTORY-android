package com.audiofactory.app

import android.net.Uri
import com.audiofactory.app.policy.AudioFactoryWebPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioFactoryWebPolicyTest {

    @Test
    fun `trusted origin returns true for official audio factory domains`() {
        val prodUri = Uri.parse("https://audiofactory.vercel.app")
        val prodSubUri = Uri.parse("https://audiofactory.vercel.app/studio/generate")
        val altUri = Uri.parse("https://audiofactory.app")

        assertTrue(AudioFactoryWebPolicy.isTrustedOrigin(prodUri))
        assertTrue(AudioFactoryWebPolicy.isTrustedOrigin(prodSubUri))
        assertTrue(AudioFactoryWebPolicy.isTrustedOrigin(altUri))
    }

    @Test
    fun `trusted origin rejects third-party and malicious domains`() {
        val phishingUri = Uri.parse("https://audiofactory.vercel.app.attacker.com")
        val evilUri = Uri.parse("https://evil-audio.com")
        val googleUri = Uri.parse("https://accounts.google.com")

        assertFalse(AudioFactoryWebPolicy.isTrustedOrigin(phishingUri))
        assertFalse(AudioFactoryWebPolicy.isTrustedOrigin(evilUri))
        assertFalse(AudioFactoryWebPolicy.isTrustedOrigin(googleUri))
    }

    @Test
    fun `external action schemes identified correctly`() {
        val mailUri = Uri.parse("mailto:support@audiofactory.app")
        val telUri = Uri.parse("tel:+1234567890")
        val webUri = Uri.parse("https://audiofactory.vercel.app")

        assertTrue(AudioFactoryWebPolicy.isExternalActionScheme(mailUri))
        assertTrue(AudioFactoryWebPolicy.isExternalActionScheme(telUri))
        assertFalse(AudioFactoryWebPolicy.isExternalActionScheme(webUri))
    }

    @Test
    fun `disallowed schemes are rejected by safe scheme check`() {
        val jsUri = Uri.parse("javascript:alert(1)")
        val fileUri = Uri.parse("file:///data/data/com.audiofactory.app")
        val contentUri = Uri.parse("content://settings/system")

        assertFalse(AudioFactoryWebPolicy.isSafeScheme(jsUri))
        assertFalse(AudioFactoryWebPolicy.isSafeScheme(fileUri))
        assertFalse(AudioFactoryWebPolicy.isSafeScheme(contentUri))
    }

    @Test
    fun `start url points to production Audio Factory deployment`() {
        assertEquals("https://audiofactory.vercel.app", AudioFactoryWebPolicy.START_URL)
    }
}
