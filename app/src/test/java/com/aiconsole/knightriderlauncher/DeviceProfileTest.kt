package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.data.DeviceProfile
import com.aiconsole.knightriderlauncher.data.LiteMode
import com.aiconsole.knightriderlauncher.data.UiScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {

    @Test
    fun cheapHeadUnitsAreLowEnd() {
        assertTrue(DeviceProfile.isLowEnd(totalRamMb = 2_048, lowRamFlag = false, cores = 8))
        assertTrue(DeviceProfile.isLowEnd(totalRamMb = 8_192, lowRamFlag = false, cores = 4))
        assertTrue(DeviceProfile.isLowEnd(totalRamMb = 8_192, lowRamFlag = true, cores = 8))
        assertFalse(DeviceProfile.isLowEnd(totalRamMb = 4_096, lowRamFlag = false, cores = 8))
        // Unknown values (0) don't count against the unit.
        assertFalse(DeviceProfile.isLowEnd(totalRamMb = 0, lowRamFlag = false, cores = 0))
    }

    @Test
    fun liteFollowsTheDriverOrTheHardware() {
        assertTrue(DeviceProfile.lite(LiteMode.On, lowEnd = false))
        assertFalse(DeviceProfile.lite(LiteMode.Off, lowEnd = true))
        assertTrue(DeviceProfile.lite(LiteMode.Auto, lowEnd = true))
        assertFalse(DeviceProfile.lite(LiteMode.Auto, lowEnd = false))
    }

    @Test
    fun autoScaleFitsSevenToTenInchScreens() {
        assertEquals(1f, DeviceProfile.autoScale(1024, 600), 0f) // 7"/9"/10" 1024x600 mdpi
        assertEquals(0.9f, DeviceProfile.autoScale(853, 480), 0f) // 1280x720 hdpi
        assertEquals(0.8f, DeviceProfile.autoScale(640, 360), 0f)
        assertEquals(1.1f, DeviceProfile.autoScale(1280, 800), 0f) // 10" 1280x800 mdpi
        assertEquals(1f, DeviceProfile.autoScale(0, 0), 0f)
        assertEquals(1f, DeviceProfile.autoScale(800, 1280), 0f) // portrait keeps its density
        assertEquals(0.85f, DeviceProfile.scale(UiScale.Compact, 1024, 600), 0f)
        assertEquals(0.9f, DeviceProfile.scale(UiScale.Auto, 853, 480), 0f)
    }

    @Test
    fun railOnlyOnLandscape() {
        assertTrue(DeviceProfile.useRail(1024f, 600f))
        assertTrue(DeviceProfile.useRail(1920f / 1.5f, 720f / 1.5f))
        assertFalse(DeviceProfile.useRail(720f, 1280f))
        assertFalse(DeviceProfile.useRail(800f, 760f))
    }

    @Test
    fun scannerShrinksOnShortScreens() {
        assertEquals(12, DeviceProfile.scannerHeightDp(480f))
        assertEquals(16, DeviceProfile.scannerHeightDp(600f))
        assertEquals(22, DeviceProfile.scannerHeightDp(1280f))
    }

    private data class Tab(val id: Int, val used: Long, val visible: Boolean = false, val playing: Boolean = false)

    private fun discard(tabs: List<Tab>, keep: Int) =
        DeviceProfile.discardable(tabs, { it.used }, { it.visible }, { it.playing }, keep).map { it.id }

    @Test
    fun discardsOldestHiddenSilentTabsFirst() {
        val tabs = listOf(Tab(1, used = 30), Tab(2, used = 10), Tab(3, used = 50, visible = true), Tab(4, used = 20))
        assertEquals(listOf(2, 4), discard(tabs, keep = 2))
        assertEquals(emptyList<Int>(), discard(tabs, keep = 4))
        assertEquals(listOf(2, 4, 1), discard(tabs, keep = 0))
    }

    @Test
    fun neverDiscardsVisibleOrPlayingTabs() {
        val tabs = listOf(Tab(1, used = 1, playing = true), Tab(2, used = 2, visible = true), Tab(3, used = 3))
        assertEquals(listOf(3), discard(tabs, keep = 0))
        assertEquals(emptyList<Int>(), discard(tabs.take(2), keep = 0))
    }

    @Test
    fun liteKeepsFewerPagesAndFrames() {
        assertTrue(DeviceProfile.liveWebViews(lite = true) < DeviceProfile.liveWebViews(lite = false))
        assertEquals(0L, DeviceProfile.scannerFrameNanos(lite = false))
        assertTrue(DeviceProfile.scannerFrameNanos(lite = true) in 30_000_000L..40_000_000L)
    }
}
