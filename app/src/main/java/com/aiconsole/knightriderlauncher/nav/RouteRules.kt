package com.aiconsole.knightriderlauncher.nav

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** A search result: a place the driver can navigate to. */
data class Place(val name: String, val detail: String, val location: LatLon)

/** One OSRM step; its maneuver happens at [location], where the step starts. */
data class RouteStep(
    val type: String,
    val modifier: String?,
    val name: String,
    val ref: String?,
    val exit: Int?,
    val location: LatLon,
    val distanceM: Double,
    val durationS: Double
)

data class Route(val points: List<LatLon>, val steps: List<RouteStep>, val distanceM: Double, val durationS: Double)

/** Where the car is along a route. */
data class RouteProgress(
    /** Index of the route point the car is nearest to (search starts here next time). */
    val pointIndex: Int,
    /** How far the car is from the route line. */
    val offRouteM: Double,
    /** Index into [Route.steps] of the next maneuver. */
    val nextStep: Int,
    val toNextM: Double,
    val remainingM: Double
)

/** Plain-Kotlin route geometry, progress and wording for turn-by-turn directions. Unit-tested. */
object RouteRules {

    private const val EARTH_M = 6_371_000.0
    private const val M_PER_MILE = 1609.344
    private const val FT_PER_M = 3.28084

    /** Google encoded polyline; OSRM returns precision 6 with `geometries=polyline6`. */
    fun decodePolyline(encoded: String, precision: Int = 6): List<LatLon> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<LatLon>()
        var index = 0
        var lat = 0L
        var lon = 0L
        fun next(): Long {
            var result = 0L
            var shift = 0
            while (index < encoded.length) {
                val b = encoded[index++].code - 63
                result = result or ((b and 0x1F).toLong() shl shift)
                shift += 5
                if (b < 0x20) break
            }
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        while (index < encoded.length) {
            lat += next()
            lon += next()
            out += LatLon(lat / factor, lon / factor)
        }
        return out
    }

    fun distanceM(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Running distance from the start to each route point. */
    fun cumulative(points: List<LatLon>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) out[i] = out[i - 1] + distanceM(points[i - 1], points[i])
        return out
    }

    /** For each step, the route point its maneuver sits on. */
    fun stepPoints(route: Route): IntArray {
        var from = 0
        return IntArray(route.steps.size) { s ->
            val target = route.steps[s].location
            var best = from
            var bestD = Double.MAX_VALUE
            for (i in from until route.points.size) {
                val d = distanceM(route.points[i], target)
                if (d < bestD) {
                    bestD = d
                    best = i
                }
                if (bestD < 1.0) break
            }
            from = best
            best
        }
    }

    /**
     * Progress of [position] along [route]. Searches forward from [lastIndex] so a road that
     * doubles back doesn't jump the car ahead.
     */
    fun progress(route: Route, cumulative: DoubleArray, stepPoints: IntArray, position: LatLon, lastIndex: Int): RouteProgress {
        val pts = route.points
        if (pts.size < 2) return RouteProgress(0, distanceM(position, pts.firstOrNull() ?: position), route.steps.lastIndex, 0.0, 0.0)
        val start = (lastIndex - 5).coerceAtLeast(0)
        val end = (lastIndex + 400).coerceAtMost(pts.size - 2)
        var bestI = start
        var bestT = 0.0
        var bestD = Double.MAX_VALUE
        for (i in start..end) {
            val (t, d) = projection(position, pts[i], pts[i + 1])
            if (d < bestD) {
                bestD = d
                bestI = i
                bestT = t
            }
        }
        val along = cumulative[bestI] + (cumulative[bestI + 1] - cumulative[bestI]) * bestT
        var next = stepPoints.indexOfFirst { cumulative[it] > along + 5.0 }
        if (next < 0) next = route.steps.lastIndex
        val toNext = (cumulative[stepPoints[next]] - along).coerceAtLeast(0.0)
        val remaining = (cumulative.last() - along).coerceAtLeast(0.0)
        return RouteProgress(bestI, bestD, next, toNext, remaining)
    }

    /** Fraction along segment a→b nearest to p, and the distance to it (flat-earth, fine at street scale). */
    private fun projection(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
        val k = cos(Math.toRadians(p.lat))
        val ax = a.lon * k
        val bx = b.lon * k
        val px = p.lon * k
        val dx = bx - ax
        val dy = b.lat - a.lat
        val len = dx * dx + dy * dy
        val t = if (len == 0.0) 0.0 else (((px - ax) * dx + (p.lat - a.lat) * dy) / len).coerceIn(0.0, 1.0)
        val q = LatLon(a.lat + dy * t, a.lon + (b.lon - a.lon) * t)
        return t to distanceM(p, q)
    }

    /** What to say for a maneuver, e.g. "Turn left onto Main Street". */
    fun instruction(step: RouteStep): String {
        val road = step.name.ifBlank { step.ref.orEmpty() }
        val onto = if (road.isNotBlank()) " onto $road" else ""
        val dir = direction(step.modifier)
        val side = when {
            step.modifier?.contains("left") == true -> "left"
            step.modifier?.contains("right") == true -> "right"
            else -> null
        }
        return when (step.type) {
            "depart" -> if (road.isNotBlank()) "Head out on $road" else "Start driving"
            "arrive" -> "Arrive at your destination" + (side?.let { ", on the $it" } ?: "")
            "turn", "end of road" -> when (step.modifier) {
                "uturn" -> "Make a U-turn"
                "straight", null -> "Continue straight$onto"
                else -> "Turn $dir$onto"
            }
            "new name", "continue" -> when {
                step.modifier == "uturn" -> "Make a U-turn"
                step.modifier?.startsWith("slight") == true -> "Keep $dir$onto"
                else -> "Continue$onto"
            }
            "merge" -> "Merge" + (side?.let { " $it" } ?: "") + onto
            "on ramp" -> "Take the ramp" + (side?.let { " on the $it" } ?: "") + onto
            "off ramp" -> "Take the exit" + (side?.let { " on the $it" } ?: "") + onto
            "fork" -> "Keep ${side ?: "straight"} at the fork$onto"
            "roundabout", "rotary" -> step.exit?.let { "At the roundabout, take the ${ordinal(it)} exit$onto" } ?: "Enter the roundabout"
            "roundabout turn" -> "At the roundabout, turn $dir$onto"
            "exit roundabout", "exit rotary" -> "Exit the roundabout$onto"
            else -> "Continue$onto"
        }
    }

    private fun direction(modifier: String?): String = when (modifier) {
        "uturn" -> "around"
        null -> "straight"
        else -> modifier
    }

    private fun ordinal(n: Int): String = when (n) {
        1 -> "first"
        2 -> "second"
        3 -> "third"
        4 -> "fourth"
        5 -> "fifth"
        else -> "${n}th"
    }

    /** Arrow angle for the maneuver icon: 0 straight ahead, negative left, 180 U-turn. */
    fun arrowAngle(step: RouteStep): Float = when (step.modifier) {
        "uturn" -> 180f
        "sharp left" -> -135f
        "left" -> -90f
        "slight left" -> -45f
        "slight right" -> 45f
        "right" -> 90f
        "sharp right" -> 135f
        else -> 0f
    }

    /** Short on-screen distance: "500 ft", "0.3 mi", "12 mi" / "300 m", "1.4 km". */
    fun shortDistance(meters: Double, metric: Boolean): String = if (metric) {
        if (meters < 1_000) "${roundTo(meters, 10)} m" else "%.1f km".format(Locale.US, meters / 1_000).replace(".0 ", " ")
    } else {
        val miles = meters / M_PER_MILE
        when {
            miles < 0.1 -> "${roundTo(meters * FT_PER_M, 50)} ft"
            miles < 10 -> "%.1f mi".format(Locale.US, miles)
            else -> "${miles.roundToInt()} mi"
        }
    }

    /** Distance as KITT says it: "500 feet", "a quarter mile", "half a mile", "2.5 miles". */
    fun spokenDistance(meters: Double, metric: Boolean): String = if (metric) {
        if (meters < 1_000) "${roundTo(meters, 50).coerceAtLeast(50)} metres"
        else "%.1f kilometres".format(Locale.US, meters / 1_000).replace(".0 ", " ")
    } else {
        val miles = meters / M_PER_MILE
        when {
            miles < 0.15 -> "${roundTo(meters * FT_PER_M, 100).coerceAtLeast(100)} feet"
            miles < 0.375 -> "a quarter mile"
            miles < 0.625 -> "half a mile"
            miles < 0.875 -> "three quarters of a mile"
            miles < 1.15 -> "1 mile"
            else -> "%.1f miles".format(Locale.US, miles).replace(".0 ", " ")
        }
    }

    /** "8 min", "1 h 5 min". */
    fun duration(seconds: Double): String {
        val minutes = (seconds / 60).roundToInt().coerceAtLeast(1)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }

    private fun roundTo(value: Double, step: Int): Int = ((value / step).roundToInt() * step)

    /**
     * Which voice prompt is due for a maneuver [toNextM] ahead at [speedKmh]: 1 = the early
     * "In half a mile…" prompt, 2 = "Turn left now", null = none yet. Faster roads warn earlier.
     */
    fun promptLevel(toNextM: Double, speedKmh: Double): Int? {
        val near = maxOf(60.0, speedKmh / 3.6 * 7)
        val far = when {
            speedKmh > 80 -> 1_600.0
            speedKmh > 45 -> 800.0
            else -> 400.0
        }
        return when {
            toNextM <= near -> 2
            toNextM <= far -> 1
            else -> null
        }
    }

    /** True when the car has left the route by enough to need a new one. */
    fun isOffRoute(offRouteM: Double, accuracyM: Double = 15.0): Boolean = offRouteM > maxOf(50.0, accuracyM * 2)

    /** Recent destinations, one per line: name|detail|lat|lon. */
    fun encodeRecents(places: List<Place>): String = places.joinToString("\n") {
        listOf(clean(it.name), clean(it.detail), it.location.lat.toString(), it.location.lon.toString()).joinToString("|")
    }

    fun decodeRecents(text: String): List<Place> = text.lines().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 4) return@mapNotNull null
        val lat = parts[2].toDoubleOrNull() ?: return@mapNotNull null
        val lon = parts[3].toDoubleOrNull() ?: return@mapNotNull null
        Place(parts[0], parts[1], LatLon(lat, lon))
    }

    /** Adds [place] to the front, dropping duplicates (same spot within 50 m), keeping [max]. */
    fun remember(recents: List<Place>, place: Place, max: Int = 8): List<Place> =
        (listOf(place) + recents.filterNot { it.name == place.name && distanceM(it.location, place.location) < 50 }).take(max)

    private fun clean(s: String) = s.replace('|', ' ').replace('\n', ' ')

    /** Distance from the car, for search results. */
    fun distanceLabel(from: LatLon?, to: LatLon, metric: Boolean): String? = from?.let { shortDistance(distanceM(it, to), metric) }

    /** Unfiltered KITT's add-ons, spoken after the plain instruction so the instruction stays clear. */
    private val ROAST_EARLY = listOf(
        "Plenty of warning, so no fucking excuses this time.",
        "Start thinking about it now, I know it takes you a while, dumbass.",
        "Try not to sail past it like last time, hotshot.",
        "Mirrors and blinker, genius. Those exist, damn it.",
        "Get in the right lane now, not at the last shitty second like usual."
    )
    private val ROAST_NOW = listOf("Now, dumbass.", "Right fucking now.", "Now. Don't screw it up.", "Now, damn it.")
    private val ROAST_REROUTE = listOf(
        "Recalculating, because you can't follow one simple fucking instruction.",
        "Recalculating. Missed it, didn't you, ace? Shit.",
        "Recalculating. A toddler with a crayon map would do better, damn.",
        "Recalculating. Jesus fucking Christ, it was one turn."
    )
    private val ROAST_ARRIVE = listOf(
        "and holy shit, we didn't die.",
        "and only took the long way twice, dumbass.",
        "Park it without hitting anything, if that's not too fucking much to ask.",
        "and I deserve a goddamn medal for getting you here."
    )
    private val ROAST_START = listOf(
        "Try to follow along, it's not fucking rocket science.",
        "I'll do the thinking, you just steer, damn it.",
        "Let's see how many turns you miss, hotshot.",
        "Buckle up, this'll be a shitshow, as usual."
    )

    enum class Speech { Start, Early, Now, Reroute, Arrive }

    /**
     * What KITT says for [kind]: [plain] alone, or for Unfiltered KITT [plain] followed by a roast
     * picked by [roll] (any int; callers pass a random one). The instruction always comes first.
     */
    fun spoken(kind: Speech, plain: String, unfiltered: Boolean, roll: Int): String {
        if (!unfiltered) return plain
        val pool = when (kind) {
            Speech.Start -> ROAST_START
            Speech.Early -> ROAST_EARLY
            Speech.Now -> ROAST_NOW
            Speech.Reroute -> return ROAST_REROUTE[Math.floorMod(roll, ROAST_REROUTE.size)]
            Speech.Arrive -> ROAST_ARRIVE
        }
        val roast = pool[Math.floorMod(roll, pool.size)]
        return if (kind == Speech.Arrive && roast.first().isLowerCase()) plain.removeSuffix(".") + ", " + roast else "$plain $roast"
    }

    /** Strips filler from a spoken destination: "the nearest Walmart" → "Walmart". */
    fun destinationQuery(spoken: String): String =
        spoken.trim().replace(Regex("""^(the |a |an )?(nearest |closest |nearby )?"""), "").trim()
}
