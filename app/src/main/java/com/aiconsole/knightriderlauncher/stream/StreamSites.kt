package com.aiconsole.knightriderlauncher.stream

import com.aiconsole.knightriderlauncher.browser.HomeSite

/** A one-tap site in the stream window's picker. */
data class StreamSite(val label: String, val url: String)

/**
 * Built-in picks are licensed services that are free to watch or listen to (ad-supported,
 * library card, or free tier) and play in a browser. The driver's own saved sites are
 * stored separately and can be anything they choose.
 */
object StreamSites {

    /** The stream window's home page tiles, by category (the tags are its filter chips). */
    val HOME: List<HomeSite> = listOf(
        HomeSite("Fandango at Home", "https://athome.fandango.com/", "Free movies with ads, plus rentals (formerly Vudu)", 0xFF1565C0, listOf("Movies & TV")),
        HomeSite("Tubi", "https://tubitv.com/", "Thousands of free movies and shows", 0xFFE65100, listOf("Movies & TV")),
        HomeSite("Pluto TV", "https://pluto.tv/", "Hundreds of free live channels and on-demand", 0xFF283593, listOf("Live TV", "Movies & TV")),
        HomeSite("Plex", "https://watch.plex.tv/", "Free movies, shows and live TV", 0xFFF9A825, listOf("Movies & TV", "Live TV")),
        HomeSite("Roku Channel", "https://therokuchannel.roku.com/", "Free movies, shows and live TV", 0xFF6A1B9A, listOf("Movies & TV", "Live TV")),
        HomeSite("Xumo Play", "https://play.xumo.com/", "Free live channels and movies", 0xFF00838F, listOf("Live TV")),
        HomeSite("Crackle", "https://www.crackle.com/", "Free Hollywood movies and series", 0xFFC62828, listOf("Movies & TV")),
        HomeSite("Fawesome", "https://fawesome.tv/", "Free movies and TV", 0xFF2E7D32, listOf("Movies & TV")),
        HomeSite("Samsung TV Plus", "https://www.samsungtvplus.com/", "Free live channels", 0xFF0D47A1, listOf("Live TV")),
        HomeSite("Sling Freestream", "https://watch.sling.com/", "Free live channels and on-demand", 0xFF1E88E5, listOf("Live TV")),
        HomeSite("Shout! TV", "https://www.shoutfactorytv.com/", "Cult classics, cartoons and TV", 0xFFAD1457, listOf("Movies & TV", "Classics")),
        HomeSite("RetroCrush", "https://www.retrocrush.tv/", "Classic anime, free", 0xFFD84315, listOf("Classics")),
        HomeSite("YouTube", "https://www.youtube.com/", "Videos, music and live streams", 0xFFC62828, listOf("Video")),
        HomeSite("YouTube Movies", "https://www.youtube.com/feed/storefront", "Free-with-ads films on YouTube", 0xFFB71C1C, listOf("Movies & TV")),
        HomeSite("Kanopy", "https://www.kanopy.com/", "Free with your library card", 0xFF37474F, listOf("Library card")),
        HomeSite("Hoopla", "https://www.hoopladigital.com/", "Free with your library card", 0xFF0277BD, listOf("Library card")),
        HomeSite("PBS", "https://www.pbs.org/video/", "Documentaries, drama and news", 0xFF1A237E, listOf("Movies & TV")),
        HomeSite("Twitch", "https://www.twitch.tv/", "Live streams", 0xFF6A1B9A, listOf("Video", "Live TV")),
        HomeSite("Dailymotion", "https://www.dailymotion.com/", "Videos and news", 0xFF0D47A1, listOf("Video")),
        HomeSite("Vimeo", "https://vimeo.com/", "Independent films and shorts", 0xFF00838F, listOf("Video")),
        HomeSite("Internet Archive", "https://archive.org/details/movies", "Public-domain films and classics", 0xFF424242, listOf("Classics")),
        HomeSite("YT Music", "https://music.youtube.com/", "Music, playlists and radio", 0xFFC62828, listOf("Music")),
        HomeSite("Spotify", "https://open.spotify.com/", "Music and podcasts", 0xFF2E7D32, listOf("Music")),
        HomeSite("SoundCloud", "https://soundcloud.com/", "Music and mixes", 0xFFE65100, listOf("Music")),
        HomeSite("Pandora", "https://www.pandora.com/", "Personal radio", 0xFF1565C0, listOf("Music", "Radio")),
        HomeSite("iHeartRadio", "https://www.iheart.com/", "Live radio and podcasts", 0xFFC62828, listOf("Radio")),
        HomeSite("TuneIn", "https://tunein.com/", "Live radio, sports and news", 0xFF00695C, listOf("Radio")),
        HomeSite("Amazon Music", "https://music.amazon.com/", "Music (free tier)", 0xFF00838F, listOf("Music")),
        HomeSite("Deezer", "https://www.deezer.com/", "Music", 0xFF6A1B9A, listOf("Music")),
        HomeSite("Audiomack", "https://audiomack.com/", "Hip-hop and new music", 0xFFF9A825, listOf("Music")),
        HomeSite("Mixcloud", "https://www.mixcloud.com/", "DJ mixes and radio shows", 0xFF283593, listOf("Music")),
        HomeSite("Bandcamp", "https://bandcamp.com/", "Independent artists", 0xFF0277BD, listOf("Music")),
        HomeSite("AccuRadio", "https://www.accuradio.com/", "Free curated radio channels", 0xFFAD1457, listOf("Radio")),
        HomeSite("Live365", "https://live365.com/", "Internet radio stations", 0xFF37474F, listOf("Radio")),
        HomeSite("SomaFM", "https://somafm.com/", "Listener-supported radio", 0xFF5D4037, listOf("Radio")),
        HomeSite("Radio Garden", "https://radio.garden/", "Live radio from around the world", 0xFF2E7D32, listOf("Radio")),
        HomeSite("Jamendo", "https://www.jamendo.com/", "Free independent music", 0xFF558B2F, listOf("Music")),
        HomeSite("Free Music Archive", "https://freemusicarchive.org/", "Free, legal downloads", 0xFF424242, listOf("Music"))
    )

    private val LISTEN = setOf("Music", "Radio")

    val video: List<StreamSite> = HOME.filter { s -> s.tags.none { it in LISTEN } }.map { StreamSite(it.name, it.url) }
    val audio: List<StreamSite> = HOME.filter { s -> s.tags.any { it in LISTEN } }.map { StreamSite(it.name, it.url) }

    /** Saved sites, one per line: label|url. */
    fun decode(text: String): List<StreamSite> = text.lines().mapNotNull { line ->
        val i = line.lastIndexOf('|')
        if (i <= 0) return@mapNotNull null
        val url = StreamUrls.normalize(line.substring(i + 1)) ?: return@mapNotNull null
        StreamSite(line.substring(0, i).trim().ifEmpty { StreamUrls.titleOf(url) }, url)
    }.distinctBy { it.url }

    fun encode(sites: List<StreamSite>): String =
        sites.distinctBy { it.url }.joinToString("\n") { "${it.label.replace("|", "/").replace("\n", " ").take(40)}|${it.url}" }
}
