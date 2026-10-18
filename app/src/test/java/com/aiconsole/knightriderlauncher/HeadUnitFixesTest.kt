package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.browser.RendererRules
import com.aiconsole.knightriderlauncher.browser.SiteSettings
import com.aiconsole.knightriderlauncher.data.DeviceProfile
import com.aiconsole.knightriderlauncher.obd.BluetoothErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadUnitFixesTest {

    @Test
    fun scanFailuresExplainTheCode() {
        assertTrue(BluetoothErrors.scanFailure(2).contains("error 2"))
        assertTrue(BluetoothErrors.scanFailure(2).contains("scan slots"))
        assertTrue(BluetoothErrors.scanFailure(4).contains("doesn't offer BLE"))
        assertTrue(BluetoothErrors.scanFailure(99).contains("unknown"))
    }

    @Test
    fun gattStatusNamesTheCommonFailure() {
        assertTrue(BluetoothErrors.gattStatus(133).startsWith("status 133"))
        assertTrue(BluetoothErrors.gattStatus(133).contains("couldn't open"))
        assertEquals("status 7", BluetoothErrors.gattStatus(7))
    }

    @Test
    fun slowPageIsLeftToFinishButAHungOneIsStopped() {
        // First report (~5 s busy), launcher fine: a heavy page loading, not a hang.
        assertFalse(RendererRules.shouldTerminate(unresponsiveForMs = 5_000, launcherStalledMs = 500))
        assertFalse(RendererRules.shouldTerminate(unresponsiveForMs = 15_000, launcherStalledMs = 900))
        assertTrue(RendererRules.shouldTerminate(unresponsiveForMs = 20_000, launcherStalledMs = 0))
        // The launcher itself is stuck behind the page: stop it at once.
        assertTrue(RendererRules.shouldTerminate(unresponsiveForMs = 5_000, launcherStalledMs = 3_000))
    }

    @Test
    fun youTubeMusicPrefersItsMobileSite() {
        assertTrue(SiteSettings.prefersMobile("music.youtube.com"))
        assertFalse(SiteSettings.prefersMobile("www.youtube.com"))
        assertFalse(SiteSettings.prefersMobile(null))
    }

    @Test
    fun scannerSlowsBesideAWebPage() {
        assertEquals(50_000_000L, DeviceProfile.scannerFrameNanos(lite = false, webPageShown = true))
        assertEquals(50_000_000L, DeviceProfile.scannerFrameNanos(lite = true, webPageShown = true))
        assertEquals(0L, DeviceProfile.scannerFrameNanos(lite = false, webPageShown = false))
    }
}
