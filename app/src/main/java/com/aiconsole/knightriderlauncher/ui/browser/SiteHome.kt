package com.aiconsole.knightriderlauncher.ui.browser

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.browser.Bookmarks
import com.aiconsole.knightriderlauncher.browser.BrowserSession
import com.aiconsole.knightriderlauncher.browser.HomeSite
import com.aiconsole.knightriderlauncher.browser.SiteHomeRules
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.stream.StreamSite
import com.aiconsole.knightriderlauncher.stream.StreamSites
import com.aiconsole.knightriderlauncher.stream.StreamUrls
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.DashYellow
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** The stored state one home page uses: saved sites, favourites and recent pages. */
class HomePrefs(val bookmarks: KittSettings.Pref<String>, val favorites: KittSettings.Pref<String>, val visits: KittSettings.Pref<String>)

/**
 * A browser's home page in RetroTV's style, made for a touch screen: one box to search the tiles
 * or go to any address, filter chips (all, favourites, my sites, categories), a "continue" row
 * with the latest page on each site, and big colour tiles with a star to favourite them. The
 * driver's saved sites are tiles too, with bookmark import/export. [sections] adds more tile
 * groups below (emulator apps, game sources).
 */
@Composable
fun SiteHome(
    session: BrowserSession,
    sites: List<HomeSite>,
    prefs: HomePrefs,
    continueTitle: String,
    searchHint: String,
    help: String,
    sections: List<Pair<String, List<HomeSite>>> = emptyList()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SiteHomeRules.ALL) }
    var editing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val stored by prefs.bookmarks.state.collectAsState()
    val favText by prefs.favorites.state.collectAsState()
    val visitText by prefs.visits.state.collectAsState()
    val mine = StreamSites.decode(stored)
    val favorites = SiteHomeRules.decodeFavorites(favText)
    val visits = SiteHomeRules.decodeVisits(visitText).distinctBy { it.url }
    val target = StreamUrls.normalize(query)
    val mineTiles = mine.map { HomeSite(it.label, it.url, StreamUrls.titleOf(it.url), 0xFF455A64, listOf(SiteHomeRules.MINE)) }
    val all = (sites + mineTiles).distinctBy { it.url }
    val chips = listOf(SiteHomeRules.ALL, SiteHomeRules.FAVORITES, SiteHomeRules.MINE) + SiteHomeRules.tags(sites)
    val shown = SiteHomeRules.filter(all, filter, favorites, query)
    val open = { url: String, title: String -> session.open(url, title) }
    val star = { url: String -> prefs.favorites.set(SiteHomeRules.toggleFavorite(prefs.favorites.value, url)) }

    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                val html = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: throw IOException("Couldn't open the file")
                }
                val found = Bookmarks.parse(html).map { (title, url) -> StreamSite(title.take(40), url) }
                prefs.bookmarks.set(StreamSites.encode(StreamSites.decode(prefs.bookmarks.value) + found))
                "Imported ${found.size} bookmarks."
            } catch (e: IOException) {
                "Import failed: ${e.message}"
            } catch (e: SecurityException) {
                "Import failed: ${e.message}"
            }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(Bookmarks.export(mine.map { s -> s.label to s.url }).toByteArray()) }
                        ?: throw IOException("Couldn't open the file")
                }
                "Exported ${mine.size} bookmarks."
            } catch (e: IOException) {
                "Export failed: ${e.message}"
            } catch (e: SecurityException) {
                "Export failed: ${e.message}"
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(176.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KittTextField(query, { query = it }, searchHint, Modifier.weight(1f), onDone = { target?.let { open(it, StreamUrls.titleOf(it)) } })
                KittButton("Go", { target?.let { open(it, StreamUrls.titleOf(it)) } }, Modifier.height(48.dp).width(64.dp), ScannerRed, active = target != null, enabled = target != null)
                KittButton(
                    "★ Save",
                    {
                        target?.let {
                            prefs.bookmarks.set(StreamSites.encode(StreamSites.decode(prefs.bookmarks.value) + StreamSite(StreamUrls.titleOf(it), it)))
                            message = "Saved ${StreamUrls.titleOf(it)} to My sites."
                            query = ""
                        }
                    },
                    Modifier.height(48.dp).width(96.dp),
                    LedGreen,
                    enabled = target != null
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(Modifier.height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(chips) { chip ->
                    KittButton(chip, { filter = chip; editing = false }, Modifier.widthIn(min = 64.dp).height(40.dp), ScannerRed, active = chip == filter)
                }
            }
        }
        message?.let { m -> item(span = { GridItemSpan(maxLineSpan) }) { HelpText(m, color = DashAmber) } }
        if (visits.isNotEmpty() && filter == SiteHomeRules.ALL && query.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(continueTitle, Modifier.weight(1f), color = LedGreen)
                        KittButton("Clear", { prefs.visits.set("") }, Modifier.height(32.dp).width(72.dp), DashGrey)
                    }
                    LazyRow(Modifier.height(56.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(visits, key = { it.url }) { v ->
                            Column(
                                Modifier
                                    .width(200.dp)
                                    .height(56.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(KittBlack)
                                    .border(1.dp, LedGreen.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                                    .clickable { open(v.url, v.title) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("▶ ${v.title}", style = MaterialTheme.typography.labelLarge, color = DashWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(v.host, style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        if (filter == SiteHomeRules.MINE) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HelpText(
                        if (mine.isEmpty()) "Save any site: type its address above and tap ★ SAVE, or tap ★ SAVE in the toolbar while you're on it."
                        else "Your saved sites.",
                        Modifier.weight(1f)
                    )
                    KittButton("Import bookmarks", { import.launch(arrayOf("text/html", "*/*")) }, Modifier.height(32.dp), DashAmber)
                    KittButton("Export", { export.launch("kitt-bookmarks.html") }, Modifier.height(32.dp), DashAmber, enabled = mine.isNotEmpty())
                    if (mine.isNotEmpty()) KittButton(if (editing) "Done" else "Edit", { editing = !editing }, Modifier.height(32.dp).width(72.dp), DashAmber, active = editing)
                }
            }
        }
        if (shown.isEmpty() && filter != SiteHomeRules.MINE) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HelpText(if (filter == SiteHomeRules.FAVORITES) "Tap ☆ on a tile to make it a favourite." else "Nothing matches. Type a full address and tap GO to open it.")
            }
        }
        items(shown, key = { "t_${it.url}" }) { site ->
            val saved = SiteHomeRules.MINE in site.tags
            if (editing && saved) {
                KittButton("✕ ${site.name}", { prefs.bookmarks.set(StreamSites.encode(mine.filterNot { it.url == site.url })) }, Modifier.height(92.dp), AlertRed)
            } else {
                SiteTile(site, site.url in favorites, { open(site.url, site.name) }, { star(site.url) })
            }
        }
        if (filter == SiteHomeRules.ALL && query.isBlank()) {
            sections.forEach { (title, group) ->
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(title, color = ScannerRed) }
                items(group, key = { "s_${title}_${it.url}" }) { site -> SiteTile(site, site.url in favorites, { open(site.url, site.name) }, { star(site.url) }) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { HelpText(help) }
    }
}

/** One site: its colour washing down into the panel black, name, description and a favourite star. */
@Composable
private fun SiteTile(site: HomeSite, favorite: Boolean, onOpen: () -> Unit, onStar: () -> Unit) {
    val color = Color(site.color.toInt())
    val shape = MaterialTheme.shapes.medium
    Box(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.75f), KittBlack)))
            .border(1.dp, if (favorite) DashYellow else KittBezel, shape)
            .clickable(onClick = onOpen)
    ) {
        Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 34.dp, top = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(site.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(site.description, style = MaterialTheme.typography.bodySmall, color = DashWhite.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (site.tags.isNotEmpty()) {
                Text(site.tags.joinToString(" · ").uppercase(), style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clickable(onClick = onStar),
            contentAlignment = Alignment.Center
        ) {
            Text(if (favorite) "★" else "☆", style = MaterialTheme.typography.titleLarge, color = if (favorite) DashYellow else DashWhite.copy(alpha = 0.7f))
        }
    }
}
