package com.audiofactory.app

import com.audiofactory.app.data.WebErrorType
import com.audiofactory.app.data.WebUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFactoryStateUnitTest {

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
}
