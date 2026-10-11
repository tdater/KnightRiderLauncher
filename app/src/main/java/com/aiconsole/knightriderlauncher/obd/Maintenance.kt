package com.aiconsole.knightriderlauncher.obd

/** A recurring service item, tracked by odometer. Distances in km. */
data class MaintenanceItem(val name: String, val intervalKm: Double, val lastDoneKm: Double) {
    fun dueAtKm(): Double = lastDoneKm + intervalKm
    fun remainingKm(odometerKm: Double): Double = dueAtKm() - odometerKm
    fun status(odometerKm: Double): Status {
        val left = remainingKm(odometerKm)
        return when {
            left <= 0 -> Status.Overdue
            left <= intervalKm * 0.1 -> Status.DueSoon
            else -> Status.Ok
        }
    }

    enum class Status { Ok, DueSoon, Overdue }
}

/** Plain-Kotlin storage format and defaults for maintenance items. */
object Maintenance {

    private const val MI = 1.609344

    /** Common intervals for a gasoline vehicle; the driver edits them. */
    fun defaults(odometerKm: Double): List<MaintenanceItem> = listOf(
        MaintenanceItem("Oil change", 5_000 * MI, odometerKm),
        MaintenanceItem("Tire rotation", 6_000 * MI, odometerKm),
        MaintenanceItem("Engine air filter", 15_000 * MI, odometerKm),
        MaintenanceItem("Cabin air filter", 15_000 * MI, odometerKm)
    )

    /** One item per line: name|intervalKm|lastDoneKm. Names can't contain '|'. */
    fun encode(items: List<MaintenanceItem>): String =
        items.joinToString("\n") { "${it.name.replace("|", "/")}|${it.intervalKm}|${it.lastDoneKm}" }

    fun decode(text: String): List<MaintenanceItem> = text.lines().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 3) return@mapNotNull null
        val interval = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        val last = parts[2].toDoubleOrNull() ?: return@mapNotNull null
        MaintenanceItem(parts[0].trim().ifEmpty { return@mapNotNull null }, interval, last)
    }

    /** Spoken/context summary of what needs attention, or null if nothing does. */
    fun reminder(items: List<MaintenanceItem>, odometerKm: Double, metric: Boolean): String? {
        val due = items.filter { it.status(odometerKm) != MaintenanceItem.Status.Ok }
        if (due.isEmpty()) return null
        return due.joinToString("; ") { item ->
            val left = item.remainingKm(odometerKm)
            val distance = kotlin.math.abs(if (metric) left else left / MI).toInt()
            val unit = if (metric) "km" else "miles"
            if (left <= 0) "${item.name} overdue by $distance $unit" else "${item.name} due in $distance $unit"
        }
    }
}

/** Fuel range and cost from tank size, level and average economy. */
object FuelMath {
    /** Range in km from [levelPercent] of a [tankLiters] tank at [litersPer100Km]. */
    fun rangeKm(levelPercent: Double, tankLiters: Double, litersPer100Km: Double): Double? {
        if (tankLiters <= 0 || litersPer100Km <= 0) return null
        return levelPercent.coerceIn(0.0, 100.0) / 100 * tankLiters / litersPer100Km * 100
    }

    fun cost(liters: Double, pricePerLiter: Double): Double = liters * pricePerLiter

    const val LITERS_PER_GALLON = 3.785411784
}
