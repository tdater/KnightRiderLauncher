package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.assistant.FilmCharacter
import com.aiconsole.knightriderlauncher.assistant.KittPersona
import com.aiconsole.knightriderlauncher.data.settings.PersonaStyle
import com.aiconsole.knightriderlauncher.ui.theme.FilmHudText
import com.aiconsole.knightriderlauncher.ui.theme.HudStyle
import com.aiconsole.knightriderlauncher.ui.theme.TimeCircuits
import com.aiconsole.knightriderlauncher.ui.theme.UiTheme
import com.aiconsole.knightriderlauncher.ui.theme.hud
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmThemesTest {

    @Test
    fun everyFilmThemeHasACharacterAndText() {
        assertEquals(HudStyle.Future, UiTheme.BackToTheFuture.hud)
        HudStyle.entries.forEach { style ->
            val c = FilmCharacter.forStyle(style)!!
            assertTrue(c.style.contains("Do not imitate any real actor"))
            assertTrue(c.personality.contains("Never recite"))
            assertTrue(FilmHudText.intro(style).isNotEmpty())
            assertTrue(FilmHudText.lines(style).isNotEmpty())
            assertTrue(c.greeting("Good morning", ", Travis").startsWith("Good morning, Travis"))
        }
        assertNull(FilmCharacter.forStyle(null))
    }

    @Test
    fun characterLayersOnTopOfThePersona() {
        val p = KittPersona.systemInstruction(PersonaStyle.Savage, "", false, "OBD: not connected", FilmCharacter.Cyborg)
        assertTrue(p.contains("cyborg infiltration unit"))
        assertTrue(p.contains("Savage mode")) // The crude persona still applies.
        assertTrue(p.contains("Accuracy is non-negotiable"))
        assertFalse(KittPersona.systemInstruction(PersonaStyle.Classic, "", false, "x").contains("Theme character"))
    }

    @Test
    fun timeCircuitsFormatLikeTheFilm() {
        // 26 Oct 1985, 01:21: the film's famous departure (in UTC here).
        val utc = TimeZone.getTimeZone("UTC")
        val row = TimeCircuits.row(499_137_660_000L, utc) // 1985-10-26 01:21 UTC
        assertEquals("OCT", row.month)
        assertEquals("26 1985 0121", row.digits)
    }
}
