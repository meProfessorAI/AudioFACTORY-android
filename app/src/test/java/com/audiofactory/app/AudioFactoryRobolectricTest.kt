package com.audiofactory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.audiofactory.app.data.WebErrorType
import com.audiofactory.app.data.WebUiState
import com.audiofactory.app.service.AudioFactoryDownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioFactoryRobolectricTest {

    @Test
    fun `application name is Audio Factory`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Audio Factory", appName)
    }

    @Test
    fun `web ui initial state is configured correctly`() {
        val state = WebUiState()
        assertTrue(state.isLoading)
        assertEquals(0, state.progress)
        assertEquals("https://audiofactory.vercel.app", state.currentUrl)
        assertFalse(state.showExitDialog)
        assertFalse(state.showAboutDialog)
    }

    @Test
    fun `error state diagnostics map accurately`() {
        val offlineError = WebErrorType.Offline
        val dnsError = WebErrorType.DnsError("audiofactory.vercel.app")
        val httpError = WebErrorType.HttpError(503, "Service Unavailable")

        assertEquals("audiofactory.vercel.app", dnsError.host)
        assertEquals(503, httpError.statusCode)
        assertNotNull(offlineError)
    }

    @Test
    fun `download manager initializes without errors`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AudioFactoryDownloadManager(context)
        assertNotNull(manager)
    }
}
