package com.aiconsole.knightriderlauncher.obd

/** Something about the driving worth a comment from KITT. */
enum class DrivingEvent {
    /** Speed fell hard in about a second. */
    HardBrake,

    /** High revs before the engine has warmed up. */
    ColdRevs,

    /** Parked with the engine running for a long time. */
    LongIdle,

    /** Pedal to the floor (KITT's "turbo boost"). */
    FullThrottle
}

/**
 * Pure detector for [DrivingEvent]s from the 1 Hz-ish OBD readings (unit-tested). Feed it each
 * new sample; it returns the events that just happened, each type rate-limited so KITT
 * comments without nagging.
 */
class DrivingMonitor(
    private val cooldownMs: Long = COOLDOWN_MS
) {
    private var lastSpeed: Double? = null
    private var lastSpeedAt = 0L
    private var idleSince: Long? = null
    private var idleReported = false
    private var throttleHigh = false
    private val lastFired = HashMap<DrivingEvent, Long>()

    /** Hard stops so far this drive (for the report card). */
    var hardBrakes = 0
        private set

    /** Cold-engine high revs so far this drive. */
    var coldRevs = 0
        private set

    fun reset() {
        lastSpeed = null
        idleSince = null
        idleReported = false
        throttleHigh = false
        hardBrakes = 0
        coldRevs = 0
        lastFired.clear()
    }

    fun feed(now: Long, speedKmh: Double?, rpm: Double?, coolantC: Double?, throttlePct: Double?): List<DrivingEvent> {
        val events = ArrayList<DrivingEvent>(1)

        // Hard braking: a drop of HARD_BRAKE_KMH_PER_S or more, measured over 0.5–2.5 s.
        if (speedKmh != null) {
            val prev = lastSpeed
            val dtS = (now - lastSpeedAt) / 1000.0
            if (prev != null && dtS in 0.5..2.5 && (prev - speedKmh) / dtS >= HARD_BRAKE_KMH_PER_S && prev >= 20) {
                hardBrakes++ // Every one counts for the report card; KITT only comments now and then.
                if (fire(DrivingEvent.HardBrake, now)) events += DrivingEvent.HardBrake
            }
            lastSpeed = speedKmh
            lastSpeedAt = now
        }

        // Cold revs: engine under 50 °C and over 3,500 rpm.
        if (coolantC != null && rpm != null && coolantC < COLD_ENGINE_C && rpm > COLD_REV_RPM) {
            if (fire(DrivingEvent.ColdRevs, now)) {
                coldRevs++
                events += DrivingEvent.ColdRevs
            }
        }

        // Long idle: stopped with the engine running for 10 minutes.
        if (speedKmh != null && rpm != null && speedKmh < 1 && rpm > 300) {
            val since = idleSince ?: now.also { idleSince = it }
            if (!idleReported && now - since >= LONG_IDLE_MS) {
                idleReported = true
                events += DrivingEvent.LongIdle
            }
        } else if (speedKmh != null && speedKmh >= 5) {
            idleSince = null
            idleReported = false
        }

        // Full throttle: once each time the pedal goes past 90 % (re-arms below 60 %).
        if (throttlePct != null) {
            if (!throttleHigh && throttlePct >= FULL_THROTTLE_PCT) {
                throttleHigh = true
                if (fire(DrivingEvent.FullThrottle, now)) events += DrivingEvent.FullThrottle
            } else if (throttlePct < 60) {
                throttleHigh = false
            }
        }

        return events
    }

    private fun fire(event: DrivingEvent, now: Long): Boolean {
        val last = lastFired[event]
        if (last != null && now - last < cooldownMs) return false
        lastFired[event] = now
        return true
    }

    companion object {
        const val COOLDOWN_MS = 5 * 60_000L
        const val HARD_BRAKE_KMH_PER_S = 14.0
        const val COLD_ENGINE_C = 50.0
        const val COLD_REV_RPM = 3_500.0
        const val LONG_IDLE_MS = 10 * 60_000L
        const val FULL_THROTTLE_PCT = 90.0
    }
}
