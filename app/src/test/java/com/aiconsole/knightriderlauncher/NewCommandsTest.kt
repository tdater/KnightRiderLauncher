package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.assistant.KittPersona
import com.aiconsole.knightriderlauncher.assistant.SavageLines
import com.aiconsole.knightriderlauncher.assistant.VoiceCommand
import com.aiconsole.knightriderlauncher.assistant.VoiceCommands
import com.aiconsole.knightriderlauncher.browser.SiteConfig
import com.aiconsole.knightriderlauncher.browser.SiteSettings
import com.aiconsole.knightriderlauncher.data.settings.PersonaStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewCommandsTest {

    @Test
    fun fuelParkingAndPursuitCommands() {
        listOf("find gas", "Hey KITT, find the nearest gas station", "navigate to the nearest gas station", "I need gas", "where's the closest fuel")
            .forEach { assertEquals(it, VoiceCommand.NearestFuel, VoiceCommands.parse(it)) }
        listOf("where did I park", "where's my truck", "where is my car", "where did I leave the vehicle")
            .forEach { assertEquals(it, VoiceCommand.WhereParked, VoiceCommands.parse(it)) }
        listOf("pursuit mode", "engage pursuit mode", "KITT, pursuit")
            .forEach { assertEquals(it, VoiceCommand.Pursuit, VoiceCommands.parse(it)) }
        // Ordinary navigation still works.
        assertEquals(VoiceCommand.Navigate("walmart"), VoiceCommands.parse("navigate to walmart"))
    }

    @Test
    fun karrIsMenacingNotProfane() {
        val p = KittPersona.systemInstruction(PersonaStyle.Karr, "", webSearch = false, vehicleContext = "OBD: not connected")
        assertTrue(p.contains("You are KARR"))
        assertTrue(p.contains("No profanity"))
        assertTrue(p.contains("Accuracy is non-negotiable"))
        assertFalse(PersonaStyle.Karr.rude)
        val line = SavageLines.pick(PersonaStyle.Karr, "Coolant is 90 degrees.", arrayOf("rude"), 3)
        assertTrue(line.startsWith("Coolant is 90 degrees. "))
    }

    @Test
    fun dataSaverIsStoredPerSiteAndOldLinesStillRead() {
        val sites = SiteSettings.update(emptyMap(), "www.youtube.com") { it.copy(dataSaver = true) }
        val decoded = SiteSettings.decode(SiteSettings.encode(sites))
        assertEquals(true, decoded["youtube.com"]?.dataSaver)
        // A line saved by an older version (5 fields) still reads, data saver unset.
        assertEquals(SiteConfig("claude.ai", false, null, null, null, null), SiteSettings.decode("claude.ai|0|||")["claude.ai"])
    }
}

class BluetoothAddressTest {
    @org.junit.Test
    fun normalizesAddresses() {
        val n = com.aiconsole.knightriderlauncher.obd.BluetoothAddress::normalize
        org.junit.Assert.assertEquals("00:1D:A5:68:98:8A", n("00:1d:a5:68:98:8a"))
        org.junit.Assert.assertEquals("00:1D:A5:68:98:8A", n(" 00-1D-A5-68-98-8A "))
        org.junit.Assert.assertEquals("00:1D:A5:68:98:8A", n("001DA568988A"))
        org.junit.Assert.assertNull(n("00:1D:A5:68:98"))
        org.junit.Assert.assertNull(n("GG:1D:A5:68:98:8A"))
    }
}
