package com.aiconsole.knightriderlauncher.browser

/** A page the driver was on: "Continue playing" / "Continue watching" on the browser home pages. */
data class Visit(val title: String, val url: String, val at: Long) {
    val host: String get() = SiteHomeRules.host(url)
}

/** Plain-Kotlin rules for the game and stream home pages (unit-tested). */
object SiteHomeRules {

    const val MAX_VISITS = 12

    /** "https://www.retrogames.cc/nes-games/x.html" -> "retrogames.cc". */
    fun host(url: String): String = url.substringAfter("://", url).substringBefore('/').substringBefore('?')
        .substringBefore('#').substringAfterLast('@').substringBefore(':').lowercase().removePrefix("www.")

    /** The registrable site of a URL: "games.example.co.uk" -> "example.co.uk", "m.site.com" -> "site.com". */
    fun site(url: String): String {
        val parts = host(url).split('.').filter { it.isNotEmpty() }
        if (parts.size < 2) return parts.joinToString(".")
        val countryPair = parts.size >= 3 && parts.last().length == 2 && parts[parts.size - 2].length <= 3
        return parts.takeLast(if (countryPair) 3 else 2).joinToString(".")
    }

    fun sameSite(a: String, b: String): Boolean = site(a) == site(b)

    /** Sign-in pop-ups need their opener page, so pop-up holding lets them through. */
    private val SIGN_IN = listOf("accounts.google.com", "accounts.youtube.com", "appleid.apple.com", "facebook.com", "login.microsoftonline.com", "login.live.com", "discord.com", "github.com", "id.twitch.tv")

    fun isSignIn(url: String): Boolean = host(url).let { h -> SIGN_IN.any { h == it || h.endsWith(".$it") } }

    /** The latest page on each site, newest first: a new page on a site replaces its older one. */
    fun record(visits: List<Visit>, title: String?, url: String, at: Long, max: Int = MAX_VISITS): List<Visit> {
        if (!url.startsWith("http")) return visits
        val visit = Visit(title?.trim().orEmpty().ifEmpty { host(url) }.take(60), url, at)
        return (listOf(visit) + visits.filter { it.host != visit.host }).take(max)
    }

    /** One per line: at|url|title. */
    fun encodeVisits(visits: List<Visit>): String =
        visits.joinToString("\n") { "${it.at}|${it.url.replace("|", "%7C")}|${it.title.replace("\n", " ")}" }

    fun decodeVisits(text: String): List<Visit> = text.lines().mapNotNull { line ->
        val parts = line.split('|', limit = 3)
        if (parts.size != 3) return@mapNotNull null
        val at = parts[0].toLongOrNull() ?: return@mapNotNull null
        if (!parts[1].startsWith("http")) return@mapNotNull null
        Visit(parts[2], parts[1], at)
    }

    /** Favourite sites, one URL per line. */
    fun decodeFavorites(text: String): Set<String> = text.lines().map { it.trim() }.filter { it.startsWith("http") }.toSet()

    fun toggleFavorite(text: String, url: String): String {
        val now = decodeFavorites(text)
        return (if (url in now) now - url else now + url).sorted().joinToString("\n")
    }

    /** The filter chips for a set of sites: All, Favourites, Recent, My sites, then every tag in order of first use. */
    fun tags(sites: List<HomeSite>): List<String> = sites.flatMap { it.tags }.distinct()

    /** Sites shown under [filter]; [query] matches name, description or tag. */
    fun filter(sites: List<HomeSite>, filter: String, favorites: Set<String>, query: String): List<HomeSite> {
        val q = query.trim().lowercase()
        return sites.filter { s ->
            when (filter) {
                ALL -> true
                FAVORITES -> s.url in favorites
                else -> filter in s.tags
            }
        }.filter { s ->
            q.isEmpty() || s.name.lowercase().contains(q) || s.description.lowercase().contains(q) || s.tags.any { it.lowercase().contains(q) }
        }.sortedByDescending { it.url in favorites }
    }

    const val ALL = "All"
    const val FAVORITES = "★ Favourites"
    const val MINE = "My sites"
}
