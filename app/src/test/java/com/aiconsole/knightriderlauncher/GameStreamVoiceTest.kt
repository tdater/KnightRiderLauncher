package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.assistant.LocalVoices
import com.aiconsole.knightriderlauncher.browser.AdBlock
import com.aiconsole.knightriderlauncher.browser.ControllerMode
import com.aiconsole.knightriderlauncher.browser.GameSites
import com.aiconsole.knightriderlauncher.browser.Gamepad
import com.aiconsole.knightriderlauncher.browser.SiteHomeRules
import com.aiconsole.knightriderlauncher.browser.Surrogates
import com.aiconsole.knightriderlauncher.stream.StreamSites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStreamVoiceTest {

    @Test
    fun visitsKeepTheLatestPagePerSite() {
        var visits = SiteHomeRules.record(emptyList(), "Mario", "https://www.retrogames.cc/nes/mario.html", 1)
        visits = SiteHomeRules.record(visits, "Tubi", "https://tubitv.com/movies/1", 2)
        visits = SiteHomeRules.record(visits, "Zelda", "https://retrogames.cc/nes/zelda.html", 3)
        assertEquals(listOf("Zelda", "Tubi"), visits.map { it.title })
        assertEquals(visits, SiteHomeRules.decodeVisits(SiteHomeRules.encodeVisits(visits)))
        assertEquals(visits, SiteHomeRules.record(visits, "x", "about:blank", 4))
        val many = (1..30).fold(emptyList<com.aiconsole.knightriderlauncher.browser.Visit>()) { v, i -> SiteHomeRules.record(v, "s$i", "https://site$i.com/", i.toLong()) }
        assertEquals(SiteHomeRules.MAX_VISITS, many.size)
    }

    @Test
    fun favouritesToggleAndSortFirst() {
        val fav = SiteHomeRules.toggleFavorite("", "https://gam.onl/")
        assertEquals(setOf("https://gam.onl/"), SiteHomeRules.decodeFavorites(fav))
        assertEquals(emptySet<String>(), SiteHomeRules.decodeFavorites(SiteHomeRules.toggleFavorite(fav, "https://gam.onl/")))
        val shown = SiteHomeRules.filter(GameSites.ALL, SiteHomeRules.ALL, SiteHomeRules.decodeFavorites(fav), "")
        assertEquals("https://gam.onl/", shown.first().url)
        assertEquals(listOf("https://gam.onl/"), SiteHomeRules.filter(GameSites.ALL, SiteHomeRules.FAVORITES, setOf("https://gam.onl/"), "").map { it.url })
        assertTrue(SiteHomeRules.filter(GameSites.ALL, "DOS", emptySet(), "").all { "DOS" in it.tags })
        assertTrue(SiteHomeRules.filter(GameSites.ALL, SiteHomeRules.ALL, emptySet(), "arcade").isNotEmpty())
    }

    @Test
    fun sameSiteAndSignIn() {
        assertTrue(SiteHomeRules.sameSite("https://www.retrogames.cc/a", "https://cdn.retrogames.cc/b"))
        assertFalse(SiteHomeRules.sameSite("https://www.retrogames.cc/a", "https://ads.example.com/"))
        assertTrue(SiteHomeRules.sameSite("https://games.example.co.uk/", "https://www.example.co.uk/x"))
        assertTrue(SiteHomeRules.isSignIn("https://accounts.google.com/o/oauth2"))
        assertFalse(SiteHomeRules.isSignIn("https://popads.net/"))
    }

    @Test
    fun catalogsAreHttpsAndUnique() {
        listOf(GameSites.ALL, GameSites.EMULATORS, GameSites.GAME_SOURCES, StreamSites.HOME).forEach { list ->
            assertTrue(list.all { it.url.startsWith("https://") })
            assertEquals(list.size, list.map { it.url }.distinct().size)
        }
        assertTrue(StreamSites.HOME.any { it.url == "https://athome.fandango.com/" })
        assertEquals(StreamSites.HOME.size, StreamSites.video.size + StreamSites.audio.size)
    }

    @Test
    fun controllerModesPickKeysOrGamepad() {
        assertTrue(ControllerMode.Auto.sendsKeys(pagePolls = false))
        assertFalse(ControllerMode.Auto.sendsKeys(pagePolls = true))
        assertFalse(ControllerMode.Gamepad.sendsKeys(false))
        assertTrue(ControllerMode.Both.sendsKeys(true) && ControllerMode.Both.feedsGamepad())
        assertFalse(ControllerMode.Keyboard.feedsGamepad())
        assertEquals(ControllerMode.Auto, ControllerMode.parse("nonsense"))
        assertEquals(ControllerMode.Auto, ControllerMode.Both.next())
        assertEquals(0, Gamepad.standardIndex(Gamepad.BUTTON_A))
        assertEquals(12, Gamepad.standardIndex(Gamepad.DPAD_UP))
        assertEquals(-1, Gamepad.standardIndex(4))
    }

    @Test
    fun adBlockSparesGameHostsAndStandsInForAdScripts() {
        assertFalse(AdBlock.isBlocked("cdn.emulatorjs.org"))
        assertFalse(AdBlock.isBlocked("archive.org"))
        assertTrue(AdBlock.isBlocked("a.exoclick.com"))
        assertNotNull(Surrogates.find("pagead2.googlesyndication.com", "/pagead/js/adsbygoogle.js"))
        assertNull(Surrogates.find("popads.net", "/x.js"))
        assertTrue(Surrogates.looksLikeScript("/tag/js/gpt.js", null))
    }

    @Test
    fun voicesAndArchivePaths() {
        assertEquals("ryan", LocalVoices.byId("nope").id)
        assertEquals(LocalVoices.ALL.size, LocalVoices.ALL.map { it.id }.distinct().size)
        assertTrue(LocalVoices.ALL.all { it.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/") })
        val kokoro = LocalVoices.byId("kokoro")
        assertEquals(6, LocalVoices.speaker(kokoro, 99))
        assertEquals(2, LocalVoices.speaker(kokoro, 2))
        assertEquals(0, LocalVoices.speaker(LocalVoices.DEFAULT, 5))
        assertEquals("a/b.onnx", LocalVoices.safeEntry("./a/b.onnx"))
        assertNull(LocalVoices.safeEntry("../evil"))
        assertNull(LocalVoices.safeEntry("/etc/passwd"))
        assertEquals("en_US-ryan-medium.onnx", LocalVoices.stripTop("vits-piper-en_US-ryan-medium-int8/en_US-ryan-medium.onnx"))
        assertNull(LocalVoices.stripTop("vits-piper-en_US-ryan-medium-int8/"))
    }
}
