package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.assistant.FilmCharacter
import com.aiconsole.knightriderlauncher.ui.components.FilmSounds
import com.aiconsole.knightriderlauncher.ui.theme.FilmChoice
import com.aiconsole.knightriderlauncher.ui.theme.FilmChoices
import com.aiconsole.knightriderlauncher.ui.theme.FilmEdition
import com.aiconsole.knightriderlauncher.ui.theme.FilmMoments
import com.aiconsole.knightriderlauncher.ui.theme.HudStyle
import com.aiconsole.knightriderlauncher.ui.theme.TerminatorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmEditionsTest {

    @Test
    fun everyFilmHasThreeEditionsAndThreeCharacters() {
        HudStyle.entries.forEach { film ->
            assertEquals(3, FilmEdition.forFilm(film).size)
            assertEquals(3, FilmCharacter.forFilm(film).size)
            FilmEdition.forFilm(film).forEach { e ->
                assertEquals(film, e.character.film)
                assertTrue(e.lines.isNotEmpty() && e.intro.isNotEmpty())
                assertTrue(e.brand.isNotBlank() && e.alertTitle.isNotBlank() && e.blurb.isNotBlank())
            }
        }
        // The original look stays the first edition.
        assertSame(TerminatorPalette, FilmEdition.default(HudStyle.Terminator).palette)
        assertEquals(FilmCharacter.forStyle(HudStyle.Matrix), FilmEdition.default(HudStyle.Matrix).character)
    }

    @Test
    fun everyCharacterIsOriginal() {
        FilmCharacter.entries.forEach { c ->
            assertTrue(c.name, c.style.contains("Do not imitate any real actor"))
            assertTrue(c.name, c.personality.contains("ever recite"))
            assertTrue(c.name, c.greeting("Good evening", ", Travis").startsWith("Good evening, Travis"))
            assertTrue(c.pitch in 0.5f..1.5f && c.rate in 0.5f..1.5f)
        }
        assertEquals(FilmCharacter.entries.size, FilmCharacter.entries.map { it.shortName }.toSet().size)
    }

    @Test
    fun choicesRoundTripAndDefault() {
        val empty = FilmChoices.decode("")
        assertEquals(FilmEdition.T800, FilmChoices.edition(empty, HudStyle.Terminator))
        assertEquals(FilmCharacter.Cyborg, FilmChoices.character(empty, HudStyle.Terminator))

        var choices = FilmChoices.withEdition(empty, FilmEdition.T1000)
        assertEquals(FilmCharacter.Liquid, FilmChoices.character(choices, HudStyle.Terminator)) // The edition's own.
        choices = FilmChoices.withCharacter(choices, HudStyle.Terminator, FilmCharacter.Resistance)
        choices = FilmChoices.withEdition(choices, FilmEdition.Zion)
        val decoded = FilmChoices.decode(FilmChoices.encode(choices))
        assertEquals(FilmChoice(FilmEdition.T1000, FilmCharacter.Resistance), decoded[HudStyle.Terminator])
        assertEquals(FilmEdition.Zion, FilmChoices.edition(decoded, HudStyle.Matrix))
        assertEquals(FilmCharacter.Captain, FilmChoices.character(decoded, HudStyle.Matrix))
        // Switching edition keeps the driver's character pick.
        assertEquals(FilmCharacter.Resistance, FilmChoices.character(FilmChoices.withEdition(decoded, FilmEdition.FutureWar), HudStyle.Terminator))
    }

    @Test
    fun badOrMismatchedChoicesFallBack() {
        val decoded = FilmChoices.decode("Terminator=Zion:Agent\nNope=T800:\nRoboCop=Ed209:Bogus")
        assertEquals(FilmEdition.T800, decoded[HudStyle.Terminator]?.edition) // Zion isn't a Terminator edition.
        assertNull(decoded[HudStyle.Terminator]?.character) // Nor is the Agent its character.
        assertEquals(FilmCharacter.Enforcer, FilmChoices.character(decoded, HudStyle.RoboCop))
        assertNull(FilmChoices.withCharacter(decoded, HudStyle.RoboCop, FilmCharacter.Kid)[HudStyle.RoboCop]?.character)
    }

    @Test
    fun eightyEightOnlyOnTheWayUp() {
        assertTrue(FilmMoments.reached88(87.5, 88.0))
        assertFalse(FilmMoments.reached88(88.0, 90.0))
        assertFalse(FilmMoments.reached88(90.0, 87.0))
        assertFalse(FilmMoments.reached88(null, 88.0))
    }

    @Test
    fun filmSoundsAreShortAndAudible() {
        HudStyle.entries.forEach { film ->
            val pcm = FilmSounds.forFilm(film, 22_050)
            assertTrue(film.name, pcm.size in 22_050 / 2..22_050 * 2)
            assertTrue(film.name, pcm.maxOf { kotlin.math.abs(it.toInt()) } > 2_000)
        }
    }
}
