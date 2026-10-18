package com.aiconsole.knightriderlauncher.data

import kotlin.math.min

/** Lite mode trades eye candy for a lighter load on slow head units. */
enum class LiteMode(val label: String) {
    Auto("Auto"),
    On("Lite"),
    Off("Full")
}

/** How large the launcher draws, on top of the head unit's own density. */
enum class UiScale(val label: String, val factor: Float?) {
    Auto("Auto", null),
    Compact("Compact", 0.85f),
    Normal("Normal", 1f),
    Large("Large", 1.15f)
}

/**
 * Pure rules for fitting the launcher to 7"–10" landscape head units and their hardware
 * (plain Kotlin so they're unit-tested). The Android side reads RAM, cores and screen size and
 * passes them in.
 */
object DeviceProfile {

    /**
     * Cheap head units: Android's low-RAM flag, 3 GB or less, or four cores or fewer
     * (the common 1–2 GB / quad-core 7" units).
     */
    fun isLowEnd(totalRamMb: Long, lowRamFlag: Boolean, cores: Int): Boolean =
        lowRamFlag || totalRamMb in 1..LOW_RAM_MB || cores in 1..4

    fun lite(mode: LiteMode, lowEnd: Boolean): Boolean = when (mode) {
        LiteMode.On -> true
        LiteMode.Off -> false
        LiteMode.Auto -> lowEnd
    }

    /**
     * The layout is designed for a landscape screen about 600 dp tall (1024×600 at mdpi, the
     * classic 7"–10" head unit). Shorter screens (1280×720 at hdpi is 480 dp) draw slightly
     * smaller so everything fits without scrolling; tall 10" panels draw slightly larger.
     * Portrait screens keep the density Android gives them (their layout was tuned on it).
     */
    fun autoScale(widthDp: Int, heightDp: Int): Float {
        if (heightDp > widthDp) return 1f
        val shortSide = min(widthDp, heightDp)
        return when {
            shortSide <= 0 -> 1f
            shortSide < 400 -> 0.8f
            shortSide < 520 -> 0.9f
            shortSide >= 800 -> 1.1f
            else -> 1f
        }
    }

    fun scale(setting: UiScale, widthDp: Int, heightDp: Int): Float = setting.factor ?: autoScale(widthDp, heightDp)

    /** Short landscape screens get the side rail and slim dock instead of the stacked dock. */
    fun useRail(widthDp: Float, heightDp: Float): Boolean = widthDp >= heightDp * 1.2f

    /** Scanner strip height: thinner on short screens, where every row counts. */
    fun scannerHeightDp(heightDp: Float): Int = when {
        heightDp < 520f -> 12
        heightDp < 720f -> 16
        else -> 22
    }

    /**
     * WebViews kept alive across every browser (stream, AI, Games). Hidden tabs past this are
     * discarded (the page reloads when shown again); a tab that's playing sound never is.
     */
    fun liveWebViews(lite: Boolean): Int = if (lite) 2 else 5

    /**
     * Minimum time between scanner frames: ~30 fps in lite mode, the display's rate otherwise,
     * and ~20 fps while a web page is on screen: each launcher frame makes the WebView composite
     * again, which took GPU and CPU time from the page (YouTube Music) on head units.
     */
    fun scannerFrameNanos(lite: Boolean, webPageShown: Boolean = false): Long = when {
        webPageShown -> 50_000_000L
        lite -> 33_000_000L
        else -> 0L
    }

    /**
     * Which tabs to discard: hidden, silent tabs with a WebView, oldest-used first, beyond
     * [keep] live ones (0 frees every hidden one). [visible] and [playing] tabs are never chosen.
     */
    fun <T> discardable(
        live: List<T>,
        lastUsed: (T) -> Long,
        visible: (T) -> Boolean,
        playing: (T) -> Boolean,
        keep: Int
    ): List<T> {
        val candidates = live.filter { !visible(it) && !playing(it) }.sortedBy(lastUsed)
        val excess = live.size - keep.coerceAtLeast(0)
        return if (excess <= 0) emptyList() else candidates.take(excess)
    }

    private const val LOW_RAM_MB = 3_072L
}
