package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.assistant.KittPersona
import com.aiconsole.knightriderlauncher.assistant.SavageLines
import com.aiconsole.knightriderlauncher.data.settings.PersonaStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KittPersonaTest {

    private fun prompt(style: PersonaStyle) = KittPersona.systemInstruction(style, "", webSearch = false, vehicleContext = "OBD: not connected")

    @Test
    fun unfilteredSwearsAndRoastsButStaysAccurate() {
        val p = prompt(PersonaStyle.Unfiltered)
        assertTrue(p.contains("Every reply contains real profanity"))
        assertTrue(p.contains("Roast the vehicle"))
        assertTrue(p.contains("driving skills"))
        assertTrue(p.contains("Accuracy is non-negotiable"))
        assertTrue(p.contains("never make up a reading"))
        assertTrue(p.contains("no slurs"))
        assertFalse(p.contains("Savage mode"))
    }

    @Test
    fun savageTurnsItUpButKeepsEveryRule() {
        val p = prompt(PersonaStyle.Savage)
        // Everything Unfiltered has, plus the escalation.
        assertTrue(p.contains("Every reply contains real profanity"))
        assertTrue(p.contains("Savage mode"))
        assertTrue(p.contains("Swear in every sentence"))
        assertTrue(p.contains("parting shot"))
        // The rules that never bend are still there.
        assertTrue(p.contains("Accuracy is non-negotiable"))
        assertTrue(p.contains("never make up a reading"))
        assertTrue(p.contains("3-sentence limit still holds"))
        assertTrue(p.contains("leads with the plain warning"))
        assertTrue(p.contains("no slurs"))
    }

    @Test
    fun savageIsTheDefaultAndOnlyClassicIsPolite() {
        assertEquals(PersonaStyle.Savage, PersonaStyle.entries.first())
        assertTrue(PersonaStyle.Savage.rude)
        assertTrue(PersonaStyle.Unfiltered.rude)
        assertFalse(PersonaStyle.Classic.rude)
    }

    @Test
    fun classicStaysPolite() {
        val p = prompt(PersonaStyle.Classic)
        assertFalse(p.contains("profanity"))
        assertTrue(p.contains("Accuracy is non-negotiable"))
    }

    @Test
    fun spokenLinesKeepTheFactsFirst() {
        val rude = arrayOf("Coolant is 230 degrees, damn it.", "Coolant's 230 degrees, shit.")
        for (roll in listOf(-7, 0, 1, 2, 13, Int.MAX_VALUE)) {
            assertEquals("Coolant is 230 degrees.", SavageLines.pick(PersonaStyle.Classic, "Coolant is 230 degrees.", rude, roll))
            assertTrue(SavageLines.pick(PersonaStyle.Unfiltered, "x", rude, roll) in rude)
            val savage = SavageLines.pick(PersonaStyle.Savage, "x", rude, roll)
            assertTrue(savage, rude.any { savage.startsWith("$it ") })
        }
        // No rude variants given: fall back to the classic line.
        assertEquals("Paused.", SavageLines.pick(PersonaStyle.Savage, "Paused.", emptyArray(), 3))
    }
}
