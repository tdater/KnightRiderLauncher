package com.aiconsole.knightriderlauncher.assistant

import android.util.Log
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.data.trip.ReportCards
import com.aiconsole.knightriderlauncher.data.trip.TripMetricDao
import com.aiconsole.knightriderlauncher.data.trip.TripSummarizer
import com.aiconsole.knightriderlauncher.nav.FuelRules
import com.aiconsole.knightriderlauncher.nav.GpsTracker
import com.aiconsole.knightriderlauncher.nav.LatLon
import com.aiconsole.knightriderlauncher.nav.ParkingSpot
import com.aiconsole.knightriderlauncher.nav.RouteNavigator
import com.aiconsole.knightriderlauncher.nav.SpeedLimitService
import com.aiconsole.knightriderlauncher.nav.SpeedLimits
import com.aiconsole.knightriderlauncher.obd.DrivingEvent
import com.aiconsole.knightriderlauncher.obd.DrivingMonitor
import com.aiconsole.knightriderlauncher.obd.ObdManager
import com.aiconsole.knightriderlauncher.obd.ObdState
import com.aiconsole.knightriderlauncher.obd.PidCatalog
import com.aiconsole.knightriderlauncher.ui.components.SoundFx
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * KITT riding along: comments on the driving, warns about low fuel and the speed limit,
 * remembers where the vehicle was switched off, and grades each drive. Everything reads
 * existing OBD and GPS data; nothing is ever sent to the vehicle.
 */
class DriveAssistant(
    private val obd: ObdManager,
    private val gps: GpsTracker,
    private val navigator: RouteNavigator,
    private val speedLimits: SpeedLimitService,
    private val alerts: KittAlerts,
    private val settings: KittSettings,
    private val dao: TripMetricDao,
    private val rangeKm: () -> Double?,
    private val scope: CoroutineScope
) {
    private val monitor = DrivingMonitor()

    /** When this drive started (the OBD link came up), for Back to the Future's "last time departed". */
    val departedAt = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    private var driveStart = 0L
    private var fuelWarned = false
    private var overSince = 0L

    fun start() {
        // Fuel level is needed for the low-fuel warning even when no gauge shows it.
        obd.watch(WATCH_KEY, setOf(PidCatalog.FUEL_LEVEL, PidCatalog.COOLANT, PidCatalog.THROTTLE))
        scope.launch(Dispatchers.Main) {
            var connected = false
            obd.state.collect { state ->
                val now = System.currentTimeMillis()
                if (state is ObdState.Connected && !connected) {
                    connected = true
                    driveStart = now
                    departedAt.value = now
                    fuelWarned = false
                    monitor.reset()
                } else if (state !is ObdState.Connected && connected) {
                    connected = false
                    // A link that was up for a couple of minutes and went away: the ignition went off.
                    if (now - driveStart >= MIN_DRIVE_MS) endOfDrive(driveStart, monitor.hardBrakes, monitor.coldRevs)
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            obd.readings.collect { readings ->
                val now = System.currentTimeMillis()
                fun fresh(id: String) = readings[id]?.takeIf { now - it.timestamp < 5_000 }?.value
                val speed = fresh(PidCatalog.SPEED)
                val events = monitor.feed(now, speed, fresh(PidCatalog.RPM), fresh(PidCatalog.COOLANT), fresh(PidCatalog.THROTTLE))
                events.forEach { event ->
                    if (event == DrivingEvent.FullThrottle) SoundFx.turboBoost()
                    if (settings.drivingCommentary.value) alerts.drivingEvent(event)
                }
                if (settings.fuelAlerts.value && !fuelWarned) {
                    val level = fresh(PidCatalog.FUEL_LEVEL)
                    if (FuelRules.isLow(level, rangeKm())) {
                        fuelWarned = true
                        alerts.lowFuel(level)
                    }
                }
            }
        }
        // Speed limit warnings: GPS speed works with or without OBD.
        scope.launch(Dispatchers.Main) {
            gps.fix.collect { fix ->
                val limit = speedLimits.limitKmh.value
                val speed = obd.readings.value[PidCatalog.SPEED]?.takeIf { System.currentTimeMillis() - it.timestamp < 5_000 }?.value ?: fix?.speedKmh
                val metric = settings.metricUnits.value
                if (!settings.speedAlerts.value || limit == null || speed == null ||
                    !SpeedLimits.isOver(speed, limit, settings.speedAlertMargin.value, metric)
                ) {
                    overSince = 0L
                    return@collect
                }
                val now = System.currentTimeMillis()
                if (overSince == 0L) overSince = now
                // Only a sustained overspeed, not a moment passing someone.
                if (now - overSince >= OVER_HOLD_MS) alerts.speedOver(SpeedLimits.display(limit, metric), if (metric) "kilometers per hour" else "miles per hour")
            }
        }
    }

    /** The ignition went off: remember where, and grade the drive. */
    private fun endOfDrive(start: Long, hardBrakes: Int, coldRevs: Int) {
        val position = gps.position()
        scope.launch {
            if (position != null) {
                val spot = ParkingSpot(position.first, position.second, System.currentTimeMillis())
                settings.parkingSpot.set(spot.encode())
                navigator.addressOf(LatLon(spot.lat, spot.lon))?.let { address ->
                    settings.parkingSpot.set(spot.copy(address = address).encode())
                }
            }
            if (!settings.reportCards.value) return@launch
            try {
                val (trip, avg) = withContext(Dispatchers.IO) {
                    val month = dao.samplesSince(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))
                    val trips = TripSummarizer.trips(month)
                    val economies = trips.mapNotNull { it.litersPer100Km }
                    trips.lastOrNull { it.end >= start } to economies.takeIf { it.size >= 3 }?.average()
                }
                trip ?: return@launch
                val card = ReportCards.grade(trip, hardBrakes, coldRevs, avg) ?: return@launch
                settings.reportCardLog.set(ReportCards.encode(ReportCards.add(ReportCards.decode(settings.reportCardLog.value), card)))
                withContext(Dispatchers.Main) { alerts.reportCard(ReportCards.summary(card, settings.metricUnits.value), card.grade) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't grade the drive", e)
            }
        }
    }

    private companion object {
        const val TAG = "DriveAssistant"
        const val WATCH_KEY = "drive_assistant"
        const val MIN_DRIVE_MS = 2 * 60_000L
        const val OVER_HOLD_MS = 6_000L
    }
}
