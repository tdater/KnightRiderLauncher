package com.aiconsole.knightriderlauncher.nav

import java.util.Locale
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pure speed-limit rules (unit-tested): OpenStreetMap maxspeed tags and when to warn. */
object SpeedLimits {

    /**
     * An OSM `maxspeed` value in km/h: "50", "55 mph", "30 knots" or a country default like
     * "US:urban" / "RU:urban" (approximate). Null for "none", "signals", "walk" and nonsense.
     */
    fun parseKmh(tag: String?): Double? {
        val t = tag?.trim()?.lowercase() ?: return null
        if (t.isEmpty()) return null
        Regex("""^(\d+(?:\.\d+)?)\s*(mph|knots|km/h|kmh|kph)?$""").find(t)?.let { m ->
            val v = m.groupValues[1].toDouble()
            return when (m.groupValues[2]) {
                "mph" -> v * 1.609344
                "knots" -> v * 1.852
                else -> v
            }.takeIf { it in 5.0..200.0 }
        }
        return when {
            t.endsWith(":urban") -> if (t.startsWith("us:") || t.startsWith("gb:")) 25 * 1.609344 else 50.0
            t.endsWith(":motorway") -> if (t.startsWith("us:")) 65 * 1.609344 else if (t.startsWith("gb:")) 70 * 1.609344 else 130.0
            t.endsWith(":rural") -> if (t.startsWith("us:")) 55 * 1.609344 else if (t.startsWith("gb:")) 60 * 1.609344 else 90.0
            else -> null
        }
    }

    /** The limit as the driver reads it on the sign: whole mph or km/h. */
    fun display(limitKmh: Double, metric: Boolean): Int =
        if (metric) limitKmh.roundToInt() else (limitKmh / 1.609344).roundToInt()

    /** Over the limit by more than [marginDisplay] (in the driver's units, mph or km/h). */
    fun isOver(speedKmh: Double, limitKmh: Double, marginDisplay: Int, metric: Boolean): Boolean {
        val marginKmh = if (metric) marginDisplay.toDouble() else marginDisplay * 1.609344
        return speedKmh > limitKmh + marginKmh
    }

    /** Overpass QL for the road with a speed limit nearest to a point (within 25 m). */
    fun overpassQuery(lat: Double, lon: Double): String =
        "[out:json][timeout:10];way(around:25,${"%.6f".format(Locale.US, lat)},${"%.6f".format(Locale.US, lon)})[highway][maxspeed];out tags 3;"
}

/** Pure low-fuel rule (unit-tested). */
object FuelRules {
    const val LOW_PERCENT = 12.0
    const val LOW_RANGE_KM = 60.0

    /** Low when the gauge is under 12 % or the estimated range under about 37 miles. */
    fun isLow(levelPct: Double?, rangeKm: Double?): Boolean =
        (levelPct != null && levelPct > 0 && levelPct < LOW_PERCENT) || (rangeKm != null && rangeKm > 0 && rangeKm < LOW_RANGE_KM)
}

/** Where the vehicle was left when the ignition went off. */
data class ParkingSpot(val lat: Double, val lon: Double, val at: Long, val address: String? = null) {
    /** A map link any phone opens (Google Maps if installed, else the browser). */
    val mapUrl: String get() = "https://www.google.com/maps/search/?api=1&query=${"%.6f".format(Locale.US, lat)},${"%.6f".format(Locale.US, lon)}"

    fun encode(): String = listOf("%.6f".format(Locale.US, lat), "%.6f".format(Locale.US, lon), at, address.orEmpty().replace('|', ' ')).joinToString("|")

    companion object {
        fun decode(text: String): ParkingSpot? {
            val p = text.split('|')
            if (p.size < 3) return null
            return ParkingSpot(
                p[0].toDoubleOrNull() ?: return null,
                p[1].toDoubleOrNull() ?: return null,
                p[2].toLongOrNull() ?: return null,
                p.getOrNull(3)?.takeIf { it.isNotBlank() }
            )
        }
    }
}

/**
 * Sunrise and sunset (NOAA's simplified algorithm, about a minute's accuracy), for night
 * themes that follow the sun. Pure and unit-tested; times in epoch millis (UTC).
 */
object SunTimes {

    /** Sunrise and sunset on the UTC day containing [dayMillis], or null in polar day/night. */
    fun riseSet(dayMillis: Long, lat: Double, lon: Double): Pair<Long, Long>? {
        val dayStart = floor(dayMillis / 86_400_000.0) * 86_400_000.0
        val jd = dayStart / 86_400_000.0 + 2440587.5
        val n = kotlin.math.ceil(jd - 2451545.0 + 0.0008)
        val meanSolarNoon = n - lon / 360.0
        val m = Math.toRadians((357.5291 + 0.98560028 * meanSolarNoon) % 360)
        val c = 1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m)
        val lambda = Math.toRadians((Math.toDegrees(m) + c + 180 + 102.9372) % 360)
        val transit = 2451545.0 + meanSolarNoon + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda)
        val decl = asin(sin(lambda) * sin(Math.toRadians(23.44)))
        val latR = Math.toRadians(lat)
        val cosH = (sin(Math.toRadians(-0.833)) - sin(latR) * sin(decl)) / (cos(latR) * cos(decl))
        if (cosH < -1 || cosH > 1) return null
        val h = acos(cosH) * 180 / PI
        fun toMillis(julian: Double) = ((julian - 2440587.5) * 86_400_000.0).toLong()
        return toMillis(transit - h / 360) to toMillis(transit + h / 360)
    }

    /** True between sunset and sunrise; null when it can't tell (polar day/night). */
    fun isNight(now: Long, lat: Double, lon: Double): Boolean? {
        // The UTC day may not match the local one, so check yesterday's, today's and tomorrow's daylight.
        val days = listOf(-1, 0, 1).mapNotNull { riseSet(now + it * 86_400_000L, lat, lon) }
        if (days.isEmpty()) return null
        return days.none { (rise, set) -> now in rise..set }
    }
}
