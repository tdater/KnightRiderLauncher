package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.assistant.WakeWordRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordRulesTest {

    private fun listen(mediaPlaying: Boolean = false, enabled: Boolean = true, kittSpeaking: Boolean = false) =
        WakeWordRules.shouldListen(
            enabled = enabled,
            visible = true,
            suspended = false,
            kittSpeaking = kittSpeaking,
            micBlocked = false,
            mediaPlaying = mediaPlaying,
            micPermission = true
        )

    @Test
    fun neverListensOverMusic() {
        assertTrue(listen())
        assertFalse(listen(mediaPlaying = true))
    }

    @Test
    fun staysOffWhenDisabledOrKittIsTalking() {
        assertFalse(listen(enabled = false))
        assertFalse(listen(kittSpeaking = true))
    }

    @Test
    fun quietSessionsRestartCalmlyAndErrorsBackOff() {
        assertEquals(1_000L, WakeWordRules.restartDelayMs(error = false, failures = 0))
        assertEquals(500L, WakeWordRules.restartDelayMs(error = true, failures = 1))
        assertEquals(15_000L, WakeWordRules.restartDelayMs(error = true, failures = 100))
    }
}
