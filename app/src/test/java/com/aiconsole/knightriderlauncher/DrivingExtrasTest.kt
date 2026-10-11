package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.data.AutoBackupRules
import com.aiconsole.knightriderlauncher.data.ReleaseNotes
import com.aiconsole.knightriderlauncher.data.ReportShareRules
import com.aiconsole.knightriderlauncher.data.trip.ReportCards
import com.aiconsole.knightriderlauncher.data.trip.TripSummary
import com.aiconsole.knightriderlauncher.nav.FuelRules
import com.aiconsole.knightriderlauncher.nav.ParkingSpot
import com.aiconsole.knightriderlauncher.nav.SpeedLimits
import com.aiconsole.knightriderlauncher.nav.SunTimes
import com.aiconsole.knightriderlauncher.obd.DrivingEvent
import com.aiconsole.knightriderlauncher.obd.DrivingMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingExtrasTest {

    // ---- DrivingMonitor ----

    @Test
    fun hardBrakeIsDetectedAndCounted() {
        val m = DrivingMonitor()
        assertTrue(m.feed(0, 80.0, 2000.0, 90.0, 10.0).isEmpty())
        assertEquals(listOf(DrivingEvent.HardBrake), m.feed(1_000, 60.0, 1500.0, 90.0, 0.0))
        assertEquals(1, m.hardBrakes)
        // A gentle stop isn't one.
        val gentle = DrivingMonitor()
        gentle.feed(0, 50.0, 1500.0, 90.0, 0.0)
        assertTrue(gentle.feed(1_000, 44.0, 1200.0, 90.0, 0.0).isEmpty())
    }

    @Test
    fun commentsAreRateLimitedButEveryHardStopCounts() {
        val m = DrivingMonitor(cooldownMs = 60_000)
        m.feed(0, 80.0, null, null, null)
        assertEquals(listOf(DrivingEvent.HardBrake), m.feed(1_000, 60.0, null, null, null))
        m.feed(10_000, 80.0, null, null, null)
        assertTrue(m.feed(11_000, 60.0, null, null, null).isEmpty())
        assertEquals(2, m.hardBrakes)
    }

    @Test
    fun coldRevsIdleAndFullThrottle() {
        val m = DrivingMonitor()
        assertEquals(listOf(DrivingEvent.ColdRevs), m.feed(0, 30.0, 4000.0, 30.0, 20.0))
        assertTrue(m.feed(1_000, 30.0, 4000.0, 80.0, 20.0).isEmpty()) // Warm now.

        val idle = DrivingMonitor()
        idle.feed(0, 0.0, 700.0, 90.0, 0.0)
        assertTrue(idle.feed(9 * 60_000L, 0.0, 700.0, 90.0, 0.0).isEmpty())
        assertEquals(listOf(DrivingEvent.LongIdle), idle.feed(10 * 60_000L, 0.0, 700.0, 90.0, 0.0))
        assertTrue(idle.feed(11 * 60_000L, 0.0, 700.0, 90.0, 0.0).isEmpty()) // Once per stop.

        val floor = DrivingMonitor()
        assertEquals(listOf(DrivingEvent.FullThrottle), floor.feed(0, 40.0, 3000.0, 90.0, 95.0))
        assertTrue(floor.feed(1_000, 50.0, 4000.0, 90.0, 97.0).isEmpty())
    }

    // ---- Report cards ----

    private fun trip(km: Double, idleMin: Long = 2, fuelL: Double? = null) =
        TripSummary(0, 20 * 60_000L, km, (20 - idleMin) * 60_000L, idleMin * 60_000L, 100, fuelL)

    @Test
    fun cleanDriveGetsAnA() {
        val card = ReportCards.grade(trip(15.0), hardBrakes = 0, coldRevs = 0, avgLitersPer100Km = null)!!
        assertEquals("A+", card.grade)
        assertTrue(ReportCards.summary(card, metric = true).contains("no hard stops"))
    }

    @Test
    fun badHabitsCostPoints() {
        val card = ReportCards.grade(trip(15.0, idleMin = 12, fuelL = 2.4), hardBrakes = 3, coldRevs = 1, avgLitersPer100Km = 10.0)!!
        assertTrue(card.score < 70)
        assertEquals(-60, card.economyVsAvgPct) // 16 L/100 km against a 10 L/100 km average.
        assertTrue(ReportCards.summary(card, metric = false).contains("3 hard stops"))
    }

    @Test
    fun shortHopsAreNotGradedAndCardsRoundTrip() {
        assertNull(ReportCards.grade(trip(1.0), 0, 0, null))
        val card = ReportCards.grade(trip(15.0), 1, 0, null)!!
        val again = ReportCards.add(ReportCards.add(emptyList(), card), card.copy(grade = "B"))
        assertEquals(1, again.size)
        assertEquals(again, ReportCards.decode(ReportCards.encode(again)))
    }

    // ---- Speed limits, fuel, parking ----

    @Test
    fun parsesOsmSpeedLimits() {
        assertEquals(50.0, SpeedLimits.parseKmh("50")!!, 0.01)
        assertEquals(88.51, SpeedLimits.parseKmh("55 mph")!!, 0.01)
        assertEquals(40.23, SpeedLimits.parseKmh("US:urban")!!, 0.01)
        assertNull(SpeedLimits.parseKmh("none"))
        assertNull(SpeedLimits.parseKmh("signals"))
        assertEquals(55, SpeedLimits.display(88.51, metric = false))
    }

    @Test
    fun overTheLimitOnlyPastTheMargin() {
        val limit = SpeedLimits.parseKmh("55 mph")!!
        assertFalse(SpeedLimits.isOver(61 * 1.609344, limit, marginDisplay = 7, metric = false))
        assertTrue(SpeedLimits.isOver(63 * 1.609344, limit, marginDisplay = 7, metric = false))
        assertTrue(SpeedLimits.overpassQuery(32.7, -96.8).contains("maxspeed"))
    }

    @Test
    fun lowFuel() {
        assertTrue(FuelRules.isLow(10.0, null))
        assertTrue(FuelRules.isLow(30.0, 40.0))
        assertFalse(FuelRules.isLow(40.0, 300.0))
        assertFalse(FuelRules.isLow(null, null))
    }

    @Test
    fun parkingSpotRoundTrips() {
        val spot = ParkingSpot(32.7767, -96.797, 1_700_000_000_000, "City Hall Plaza, Dallas")
        assertEquals(spot, ParkingSpot.decode(spot.encode()))
        assertTrue(spot.mapUrl.contains("32.776700,-96.797000"))
        assertNull(ParkingSpot.decode("garbage"))
    }

    // ---- Sunrise and sunset ----

    @Test
    fun dallasSunriseAndSunsetInJune() {
        // 2026-06-21, Dallas: sunrise about 11:20 UTC, sunset about 01:38 UTC the next day.
        val day = 1_782_000_000_000L // 2026-06-21 00:00 UTC area
        val (rise, set) = SunTimes.riseSet(day, 32.7767, -96.797)!!
        val riseHourUtc = (rise / 3_600_000.0) % 24
        val setHourUtc = (set / 3_600_000.0) % 24
        assertEquals(11.3, riseHourUtc, 0.4)
        assertEquals(1.6, setHourUtc, 0.4)
        assertEquals(false, SunTimes.isNight(rise + 3_600_000, 32.7767, -96.797))
        assertEquals(true, SunTimes.isNight(set + 3_600_000, 32.7767, -96.797))
        assertNotNull(SunTimes.isNight(day, 32.7767, -96.797))
    }

    // ---- Housekeeping ----

    @Test
    fun releaseNotesOnlyAfterAnUpdate() {
        assertTrue(ReleaseNotes.since(0, updated = false).isEmpty())
        assertEquals(listOf(ReleaseNotes.latest), ReleaseNotes.since(0, updated = true).map { it.level })
        assertTrue(ReleaseNotes.since(ReleaseNotes.latest, updated = true).isEmpty())
        assertEquals(listOf(ReleaseNotes.latest), ReleaseNotes.since(ReleaseNotes.latest - 1, updated = true).map { it.level })
    }

    @Test
    fun weeklyBackupsKeepTheNewestFour() {
        assertTrue(AutoBackupRules.due(1_000, 0))
        assertFalse(AutoBackupRules.due(AutoBackupRules.EVERY_MS, 1))
        assertTrue(AutoBackupRules.due(AutoBackupRules.EVERY_MS + 1, 1))
        val names = (1..6).map { AutoBackupRules.fileName("kittlauncher2", "2026010$it") }
        assertEquals(listOf(names[1], names[0]), AutoBackupRules.toPrune(names))
    }

    @Test
    fun reportServerPicksTheWifiAddressAndOnlyAnswersGets() {
        val ip = ReportShareRules.pickAddress(listOf("rmnet0" to "10.64.1.2", "wlan0" to "192.168.43.17", "lo" to "127.0.0.1"))
        assertEquals("192.168.43.17", ip)
        assertNull(ReportShareRules.pickAddress(listOf("rmnet0" to "100.64.1.2")))
        assertEquals("/abc/report.txt", ReportShareRules.requestPath("GET /abc/report.txt HTTP/1.1"))
        assertNull(ReportShareRules.requestPath("POST /abc HTTP/1.1"))
        assertTrue(ReportShareRules.httpHeader("200 OK", "text/plain", 5, "r.txt").contains("Content-Length: 5\r\n"))
    }
}
