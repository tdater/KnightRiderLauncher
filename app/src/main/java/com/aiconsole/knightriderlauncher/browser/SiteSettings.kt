package com.aiconsole.knightriderlauncher.browser

/** Per-site browser options, Hermit-style. Null fields follow the global defaults. */
data class SiteConfig(
    val host: String,
    val desktop: Boolean? = null,
    val dark: Boolean? = null,
    val blockAds: Boolean? = null,
    val textZoom: Int? = null,
    /** Data saver: no images, and media waits for a tap (for phone-hotspot data plans). */
    val dataSaver: Boolean? = null
)

/** Plain-Kotlin storage for [SiteConfig]s: host|desktop|dark|ads|zoom|saver per line ("" = default; older 5-field lines still read). */
object SiteSettings {

    /** Sites whose own players break when third-party ad hosts are blocked. */
    private val NEVER_BLOCK = listOf("youtube.com", "youtu.be", "music.youtube.com")

    fun keyFor(host: String?): String = host?.lowercase()?.removePrefix("www.")?.removePrefix("m.").orEmpty()

    fun decode(text: String): Map<String, SiteConfig> = text.lines().mapNotNull { line ->
        val p = line.split('|')
        if (p.size !in 5..6 || p[0].isBlank()) return@mapNotNull null
        fun flag(s: String) = when (s) { "1" -> true; "0" -> false; else -> null }
        SiteConfig(p[0], flag(p[1]), flag(p[2]), flag(p[3]), p[4].toIntOrNull(), p.getOrNull(5)?.let(::flag))
    }.associateBy { it.host }

    fun encode(sites: Map<String, SiteConfig>): String = sites.values.sortedBy { it.host }.joinToString("\n") { c ->
        fun flag(b: Boolean?) = when (b) { true -> "1"; false -> "0"; null -> "" }
        "${c.host}|${flag(c.desktop)}|${flag(c.dark)}|${flag(c.blockAds)}|${c.textZoom ?: ""}|${flag(c.dataSaver)}"
    }

    fun update(sites: Map<String, SiteConfig>, host: String, change: (SiteConfig) -> SiteConfig): Map<String, SiteConfig> {
        val key = keyFor(host)
        if (key.isEmpty()) return sites
        return sites + (key to change(sites[key] ?: SiteConfig(key)))
    }

    /**
     * Google's sign-in pages refuse the desktop user agent inside an embedded browser ("This
     * browser or app may not be secure") but accept the WebView's own mobile one, so these hosts
     * always load as mobile. The site the driver came from keeps its own setting.
     */
    fun forcesMobile(host: String?): Boolean {
        val h = keyFor(host)
        return h.startsWith("accounts.google.") || h == "accounts.youtube.com" || h == "myaccount.google.com"
    }

    /**
     * Web apps whose desktop version is far too heavy for head-unit hardware (YouTube Music's
     * desktop player stalled and reloaded) but whose mobile site works: mobile unless the driver
     * picked Desktop for that site.
     */
    fun prefersMobile(host: String?): Boolean = keyFor(host) == "music.youtube.com"

    fun blockAllowed(host: String?): Boolean {
        val h = keyFor(host)
        return NEVER_BLOCK.none { h == it || h.endsWith(".$it") }
    }
}
