package com.aiconsole.knightriderlauncher.browser

/**
 * Network ad blocking by host (from RetroTV). Only sub-resources and pop-up/redirect targets are
 * checked; a page the driver opens is never blocked, and the hosts game and video players load
 * from (archive.org, emulator CDNs, script CDNs) are on an allow-list so a rule can't break them.
 */
object AdBlock {

    private val HOSTS = setOf(
        // Ad networks and exchanges
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "pagead2.googlesyndication.com", "googletagservices.com", "amazon-adsystem.com", "adnxs.com",
        "adsrvr.org", "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "rubiconproject.com",
        "pubmatic.com", "openx.net", "casalemedia.com", "smartadserver.com", "moatads.com", "yieldmo.com",
        "media.net", "33across.com", "sharethrough.com", "teads.tv", "zedo.com", "revcontent.com", "mgid.com",
        "adform.net", "adition.com", "advertising.com", "bidswitch.net", "contextweb.com", "districtm.io",
        "emxdgt.com", "gumgum.com", "indexww.com", "lijit.com", "sovrn.com", "spotxchange.com", "springserve.com",
        "triplelift.com", "undertone.com", "yieldlab.net", "adcolony.com", "applovin.com", "unityads.unity3d.com",
        "vungle.com", "inmobi.com", "ezoic.net", "ezoic.com", "ezodn.com", "adthrive.com", "mediavine.com",
        "raptive.com", "carbonads.net", "buysellads.com", "infolinks.com", "kargo.com", "nativo.com",
        "primis.tech", "connatix.com", "vidazoo.com", "aniview.com", "playwire.com", "venatus.com",
        "adinplay.com", "aditude.io", "freestar.com", "pbxai.com", "snigelweb.com", "setupad.net",
        "a-mo.net", "adpushup.com", "admixer.net", "adskeeper.com", "adtelligent.com", "betweendigital.com",
        "imasdk.googleapis.com",
        // Pop-under, redirect and push-spam networks
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "adsterra.com",
        "adsterratech.com", "exoclick.com", "exosrv.com", "juicyads.com", "hilltopads.net", "hilltopads.com",
        "clickadu.com", "adcash.com", "admaven.com", "ad-maven.com", "galaksion.com", "monetag.com",
        "richpush.co", "pushground.com", "evadav.com", "rollerads.com", "trafficstars.com", "tsyndicate.com",
        "trafficjunky.net", "plugrush.com", "a-ads.com", "coinzilla.com", "bitmedia.io", "onclckmn.com",
        "onclickads.net", "onclickalgo.com", "onclkds.com", "clickaine.com", "popmyads.com", "poptm.com",
        "ad-score.com", "dolohen.com", "highperformanceformat.com", "highcpmgate.com", "profitablegatecpm.com",
        "effectiveratecpm.com", "displaycontentnetwork.com", "realsrv.com", "pemsrv.com", "mnaspm.com",
        "notifpush.com", "pushnami.com", "pushwoosh.com", "pushengage.com", "izooto.com", "aclib.net",
        "acscdn.com", "grop.net", "wingsmob.com", "intergient.com", "cpmstar.com",
        // Trackers and analytics
        "google-analytics.com", "googletagmanager.com", "scorecardresearch.com", "quantserve.com",
        "quantcount.com", "hotjar.com", "mixpanel.com", "segment.io", "branch.io", "chartbeat.com",
        "newrelic.com", "nr-data.net", "onesignal.com", "facebook.net", "ads-twitter.com", "ads.linkedin.com",
        "bat.bing.com", "clarity.ms", "mc.yandex.ru", "adsco.re", "histats.com", "statcounter.com",
        "addthis.com", "sharethis.com", "crwdcntrl.net", "demdex.net", "everesttech.net", "agkn.com",
        "bluekai.com", "rlcdn.com", "tapad.com", "adsafeprotected.com", "doubleverify.com", "iasds01.com",
        "cookielaw.org", "fundingchoicesmessages.google.com"
    )

    /** Hosts that are never blocked: game libraries, emulator code and the CDNs pages load from. */
    private val ALLOW = setOf(
        "archive.org", "us.archive.org", "emulatorjs.org", "emulatorjs.com", "cdn.emulatorjs.org",
        "js-dos.com", "dos.zone", "jsdelivr.net", "unpkg.com", "cdnjs.cloudflare.com", "gstatic.com",
        "github.io", "githubusercontent.com", "webrcade.com", "greasyfork.org"
    )

    fun isAllowed(host: String?): Boolean = matches(host, ALLOW)

    fun isBlocked(host: String?): Boolean {
        if (host == null || isAllowed(host)) return false
        return matches(host, HOSTS)
    }

    private fun matches(host: String?, set: Set<String>): Boolean {
        var candidate = host?.lowercase()?.trimEnd('.') ?: return false
        while (true) {
            if (candidate in set) return true
            val dot = candidate.indexOf('.')
            if (dot < 0 || dot == candidate.lastIndexOf('.')) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    /**
     * Element-hiding CSS: common ad containers only. Selectors deliberately avoid anything
     * generic (`.banner`, `iframe`) that game and video sites reuse for the player itself.
     */
    const val COSMETIC_CSS = """
ins.adsbygoogle, .adsbygoogle, [id^="google_ads_iframe"], [id^="div-gpt-ad"], [data-google-query-id],
iframe[src*="doubleclick.net"], iframe[src*="googlesyndication"], iframe[src*="exoclick"],
iframe[src*="adsterra"], iframe[src*="juicyads"], iframe[src*="popads"], iframe[src*="a-ads.com"],
[id^="taboola-"], .trc_related_container, .OUTBRAIN, [data-widget-id][class*="outbrain"],
.ezoic-ad, [id^="ezoic-pub-ad"], .adthrive-ad, .mv-ad-box, [class^="ad-container"], [id^="ad-container"],
.ad-slot, .ad-unit, .ad-banner, .ads-banner, #ad-banner, .advertisement, .sponsored-ads, .adBanner
{ display: none !important; }
"""
}

/** Bookmarks in the Netscape HTML format every browser exports and imports. */
object Bookmarks {

    private val LINK = Regex("""<a\s[^>]*href\s*=\s*"([^"]+)"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG = Regex("<[^>]+>")

    /** (title, url) pairs for every http(s) link, de-duplicated by URL. */
    fun parse(html: String): List<Pair<String, String>> = LINK.findAll(html)
        .mapNotNull { m ->
            val url = unescape(m.groupValues[1].trim())
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return@mapNotNull null
            val title = unescape(m.groupValues[2].replace(TAG, "").trim()).ifEmpty { url }
            title to url
        }
        .distinctBy { it.second }
        .toList()

    fun export(items: List<Pair<String, String>>): String = buildString {
        appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        appendLine("<TITLE>Bookmarks</TITLE>")
        appendLine("<H1>KITT Launcher</H1>")
        appendLine("<DL><p>")
        items.forEach { (title, url) -> appendLine("    <DT><A HREF=\"${escape(url)}\">${escape(title)}</A>") }
        appendLine("</DL><p>")
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'")
    private fun escape(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}

/** Which WebView builds are too old for heavy sites like YouTube Music. */
object WebViewVersion {
    /** Below this Chromium major version, modern web apps are noticeably slow or broken. */
    const val RECOMMENDED_MAJOR = 120

    fun major(versionName: String?): Int? = versionName?.substringBefore('.')?.toIntOrNull()

    fun isOutdated(versionName: String?): Boolean = major(versionName)?.let { it < RECOMMENDED_MAJOR } ?: false
}

/** JavaScript helpers the browser toolbar runs on the current page. */
object PageScripts {

    /**
     * Removes overlays that trap the page (cookie walls, sign-up modals, "open in app" banners):
     * fixed/sticky elements covering a large part of the screen or sitting on top, then restores
     * scrolling that the overlay disabled.
     */
    const val CLOSE_POPUPS = """
(function () {
  var removed = 0, vw = innerWidth, vh = innerHeight;
  document.querySelectorAll('body *').forEach(function (el) {
    var cs = getComputedStyle(el);
    if (cs.position !== 'fixed' && cs.position !== 'sticky') return;
    var r = el.getBoundingClientRect();
    var big = r.width * r.height > vw * vh * 0.25;
    var onTop = parseInt(cs.zIndex || '0', 10) >= 100;
    var dialog = el.getAttribute('role') === 'dialog' || el.getAttribute('aria-modal') === 'true';
    if ((big && onTop) || dialog) { el.remove(); removed++; }
  });
  [document.documentElement, document.body].forEach(function (n) {
    if (!n) return;
    n.style.setProperty('overflow', 'auto', 'important');
    n.style.setProperty('position', 'static', 'important');
    n.classList.remove('modal-open', 'no-scroll', 'noscroll', 'overflow-hidden');
  });
  return removed;
})();
"""

    /**
     * Scrolls the page by [fraction] of a screen. Chat sites (Gemini, Claude, ChatGPT) scroll an
     * inner panel rather than the window, so it scrolls the scrollable box under the middle of the
     * view, else the window if it scrolls, else the largest scrollable box.
     */
    fun scrollBy(fraction: Double): String = """
(function () {
  var f = $fraction;
  function scrolls(el) {
    var y = getComputedStyle(el).overflowY;
    return (y === 'auto' || y === 'scroll' || y === 'overlay') && el.scrollHeight > el.clientHeight + 4;
  }
  var target = null;
  var el = document.elementFromPoint(innerWidth / 2, innerHeight / 2);
  while (el && el !== document.body && el !== document.documentElement) {
    if (scrolls(el)) { target = el; break; }
    el = el.parentElement;
  }
  var root = document.scrollingElement || document.documentElement;
  if (!target && root.scrollHeight <= root.clientHeight + 4) {
    var area = 0;
    document.querySelectorAll('body *').forEach(function (e) {
      if (!scrolls(e)) return;
      var r = e.getBoundingClientRect();
      if (r.width * r.height > area) { area = r.width * r.height; target = e; }
    });
  }
  if (target) target.scrollBy({top: target.clientHeight * f, behavior: 'smooth'});
  else window.scrollBy({top: innerHeight * f, behavior: 'smooth'});
})();
"""

    /** Finds an install link on a userscript page (Greasy Fork, OpenUserJS, GitHub raw…). */
    const val FIND_USERSCRIPT = """
(function () {
  var a = document.querySelector('a.install-link[href]') ||
          Array.prototype.find.call(document.querySelectorAll('a[href]'), function (x) { return /\.user\.js(\?|#|$)/i.test(x.href); });
  return a ? a.href : '';
})();
"""

    /**
     * Injected at document start in every frame: tells the app (through the `KittMedia` bridge)
     * whenever this frame starts or stops making sound. Each frame reports under its own id so a
     * paused page doesn't hide a playing embed.
     */
    const val MEDIA_WATCH = """
(function () {
  if (window.__kittMedia || typeof KittMedia === 'undefined') return;
  window.__kittMedia = true;
  var id = Math.random().toString(36).slice(2), last = null;
  function report() {
    try {
      var playing = Array.prototype.some.call(document.querySelectorAll('video,audio'), function (m) {
        return !m.paused && !m.ended && !m.muted && m.volume > 0;
      });
      if (playing !== last) { last = playing; KittMedia.state(id, playing); }
    } catch (e) {}
  }
  ['play', 'playing', 'pause', 'ended', 'volumechange', 'emptied', 'abort'].forEach(function (t) {
    document.addEventListener(t, report, true);
  });
  window.addEventListener('pagehide', function () { if (last) KittMedia.state(id, false); });
})();
"""

    /** Pauses everything playing in the page (and same-origin frames), marking it for [RESUME_MEDIA]. */
    const val PAUSE_MEDIA = """
(function () {
  function pause(d) {
    try {
      d.querySelectorAll('video,audio').forEach(function (m) { if (!m.paused) { m.dataset.kittPaused = '1'; m.pause(); } });
      d.querySelectorAll('iframe').forEach(function (f) { try { pause(f.contentDocument); } catch (e) {} });
    } catch (e) {}
  }
  pause(document);
})();
"""

    /** Plays again whatever [PAUSE_MEDIA] paused. */
    const val RESUME_MEDIA = """
(function () {
  function resume(d) {
    try {
      d.querySelectorAll('video,audio').forEach(function (m) {
        if (m.dataset.kittPaused) { delete m.dataset.kittPaused; var p = m.play(); if (p && p.catch) p.catch(function () {}); }
      });
      d.querySelectorAll('iframe').forEach(function (f) { try { resume(f.contentDocument); } catch (e) {} });
    } catch (e) {}
  }
  resume(document);
})();
"""

    /** Selected text, or the page's readable text as a fallback (for reading aloud). */
    const val SELECTION_OR_TEXT = "(function(){var s=String(getSelection()||'').trim();return s||document.body.innerText.slice(0,4000);})();"

    /** Hides common ad containers ([AdBlock.COSMETIC_CSS]). Cosmetic only; the network blocker does the real work. */
    val COSMETIC: String = """
(function () {
  if (document.getElementById('__kitt_cosmetic')) return;
  var s = document.createElement('style'); s.id = '__kitt_cosmetic';
  s.textContent = ${jsString(AdBlock.COSMETIC_CSS)};
  (document.head || document.documentElement).appendChild(s);
})();
"""

    /**
     * "Focus game" / "Focus video" (from RetroTV): blows the biggest game or video surface
     * (canvas, emulator iframe, embed, video) up to fill the page, keeping its aspect ratio, or
     * restores it when run again. Works without the tap a site's own fullscreen button needs.
     * Returns "on", "off" or "none".
     */
    const val FOCUS_GAME = """
(function () {
  var on = document.getElementById('__kitt_focus');
  if (on) {
    on.remove();
    var t = document.querySelector('[data-kitt-focus]');
    if (t) t.removeAttribute('data-kitt-focus');
    return 'off';
  }
  var best = null, area = 0;
  var list = document.querySelectorAll('canvas, iframe, embed, object, video, ruffle-player, ruffle-embed, #emulator, #game, #game-container, .emulator, [id*="emulator"], [id*="game"], [id*="player"]');
  for (var i = 0; i < list.length; i++) {
    var r = list[i].getBoundingClientRect();
    var a = r.width * r.height;
    if (r.width < 160 || r.height < 120) continue;
    var tag = list[i].tagName;
    if (tag === 'IFRAME' && /ads|doubleclick|syndication/.test(list[i].src || '')) continue;
    if (tag === 'CANVAS' || tag === 'IFRAME' || tag === 'EMBED' || tag === 'OBJECT' || tag === 'VIDEO' || tag.indexOf('RUFFLE') === 0) a *= 1.5;
    if (a > area) { area = a; best = list[i]; }
  }
  if (!best) return 'none';
  best.setAttribute('data-kitt-focus', '1');
  var s = document.createElement('style'); s.id = '__kitt_focus';
  s.textContent = 'html,body{overflow:hidden!important}' +
    '[data-kitt-focus]{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;' +
    'max-width:none!important;max-height:none!important;margin:0!important;border:0!important;z-index:2147483647!important;' +
    'background:#000!important;object-fit:contain!important;transform:none!important}';
  document.head.appendChild(s);
  window.scrollTo(0, 0);
  try { window.dispatchEvent(new Event('resize')); } catch (e) {}
  return 'on';
})();
"""

    fun jsString(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                ' ' -> append("\\u2028")
                ' ' -> append("\\u2029")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
