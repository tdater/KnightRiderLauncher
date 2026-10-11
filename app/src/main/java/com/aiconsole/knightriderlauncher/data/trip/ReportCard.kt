package com.aiconsole.knightriderlauncher.data.trip

import java.util.Locale
import kotlin.math.roundToInt

/** KITT's grade for one drive. */
data class ReportCard(
    val tripStart: Long,
    val grade: String,
    val score: Int,
    val distanceKm: Double,
    val hardBrakes: Int,
    val coldRevs: Int,
    val idlePct: Int,
    /** This trip's economy against the 30-day average, in percent (+ = better); null without fuel data. */
    val economyVsAvgPct: Int?
)

/**
 * Pure end-of-drive grading and storage (unit-tested). Starts at 100 and loses points for hard
 * stops, cold-engine revs, lots of idling and economy well below the driver's own average.
 */
object ReportCards {

    /** Drives shorter than this aren't graded (a move across the car park). */
    const val MIN_DISTANCE_KM = 2.0
    private const val KEEP = 30

    fun grade(trip: TripSummary, hardBrakes: Int, coldRevs: Int, avgLitersPer100Km: Double?): ReportCard? {
        if (trip.distanceKm < MIN_DISTANCE_KM) return null
        val idlePct = if (trip.durationMs > 0) (trip.idleMs * 100 / trip.durationMs).toInt() else 0
        val vsAvg = trip.litersPer100Km?.let { mine ->
            avgLitersPer100Km?.takeIf { it > 0 }?.let { avg -> ((avg - mine) / avg * 100).roundToInt() }
        }
        var score = 100
        score -= hardBrakes * 8
        score -= coldRevs * 10
        score -= ((idlePct - 20).coerceAtLeast(0) / 2)
        vsAvg?.let { if (it < -5) score -= ((-it - 5) / 2).coerceAtMost(20) else if (it > 5) score += 5 }
        score = score.coerceIn(0, 100)
        return ReportCard(trip.start, letter(score), score, trip.distanceKm, hardBrakes, coldRevs, idlePct, vsAvg)
    }

    fun letter(score: Int): String = when {
        score >= 97 -> "A+"
        score >= 90 -> "A"
        score >= 80 -> "B"
        score >= 70 -> "C"
        score >= 60 -> "D"
        else -> "F"
    }

    /** The plain facts, one sentence, for the screen and KITT's voice. */
    fun summary(card: ReportCard, metric: Boolean): String = buildString {
        val dist = if (metric) "%.1f km".format(Locale.US, card.distanceKm) else "%.1f miles".format(Locale.US, card.distanceKm * 0.621371)
        append("Grade ${card.grade} for $dist: ")
        val parts = mutableListOf<String>()
        parts += when (card.hardBrakes) {
            0 -> "no hard stops"
            1 -> "1 hard stop"
            else -> "${card.hardBrakes} hard stops"
        }
        if (card.coldRevs > 0) parts += "revved it cold ${if (card.coldRevs == 1) "once" else "${card.coldRevs} times"}"
        parts += "${card.idlePct}% idling"
        card.economyVsAvgPct?.let { parts += if (it >= 0) "economy $it% better than your average" else "economy ${-it}% worse than your average" }
        append(parts.joinToString(", "))
        append('.')
    }

    fun encode(cards: List<ReportCard>): String = cards.takeLast(KEEP).joinToString("\n") { c ->
        listOf(c.tripStart, c.grade, c.score, "%.3f".format(Locale.US, c.distanceKm), c.hardBrakes, c.coldRevs, c.idlePct, c.economyVsAvgPct ?: "")
            .joinToString("|")
    }

    fun decode(text: String): List<ReportCard> = text.lines().mapNotNull { line ->
        val p = line.split('|')
        if (p.size != 8) return@mapNotNull null
        ReportCard(
            tripStart = p[0].toLongOrNull() ?: return@mapNotNull null,
            grade = p[1],
            score = p[2].toIntOrNull() ?: return@mapNotNull null,
            distanceKm = p[3].toDoubleOrNull() ?: return@mapNotNull null,
            hardBrakes = p[4].toIntOrNull() ?: 0,
            coldRevs = p[5].toIntOrNull() ?: 0,
            idlePct = p[6].toIntOrNull() ?: 0,
            economyVsAvgPct = p[7].toIntOrNull()
        )
    }

    /** Adds [card], replacing one already stored for the same trip. */
    fun add(cards: List<ReportCard>, card: ReportCard): List<ReportCard> =
        (cards.filter { it.tripStart != card.tripStart } + card).sortedBy { it.tripStart }.takeLast(KEEP)
}
