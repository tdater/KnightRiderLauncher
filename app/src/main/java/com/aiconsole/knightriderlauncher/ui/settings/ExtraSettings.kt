package com.aiconsole.knightriderlauncher.ui.settings

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.assistant.MicGuard
import com.aiconsole.knightriderlauncher.browser.WebViewVersion
import com.aiconsole.knightriderlauncher.data.HomeScreen
import com.aiconsole.knightriderlauncher.data.SelfUpdate
import com.aiconsole.knightriderlauncher.data.Versions
import com.aiconsole.knightriderlauncher.nav.NavBridge
import com.aiconsole.knightriderlauncher.obd.AdapterDiscovery
import com.aiconsole.knightriderlauncher.ui.components.ChoiceRow
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittToggle
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.theme.UiTheme
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CheckLevel { Ok, Warn, Info }

private data class Check(val label: String, val detail: String, val level: CheckLevel, val fixLabel: String? = null, val fix: (() -> Unit)? = null)

/**
 * System check: everything KITT depends on, each with a one-tap fix. Re-checked every couple of
 * seconds, so it updates as soon as the driver comes back from a system screen.
 */
@Composable
fun SelfCheckPanel(app: KittApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var update by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            tick++
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val lite by app.lite.collectAsState()
    val webView = remember { runCatching { WebViewCompat.getCurrentWebViewPackage(context)?.versionName }.getOrNull() }
    val installed = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }

    val ram = remember {
        val info = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
        info.totalMem / (1024 * 1024 * 1024.0)
    }
    // Reading tick here re-runs every check every couple of seconds.
    val checks = if (tick < 0) emptyList() else buildList {
        add(
            if (HomeScreen.isDefault(context)) Check("Home screen", "KITT is the home screen.", CheckLevel.Ok)
            else Check("Home screen", "Another launcher is the home screen.", CheckLevel.Warn, "Fix") { HomeScreen.openSettings(context) }
        )
        add(
            if (granted(Manifest.permission.RECORD_AUDIO)) Check("Microphone", "Allowed.", CheckLevel.Ok)
            else Check("Microphone", "Needed to talk to KITT.", CheckLevel.Warn, "Allow") { permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
        )
        add(
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) Check("Location", "Allowed.", CheckLevel.Ok)
            else Check("Location", "Needed for the map, speed limits and parking spot.", CheckLevel.Warn, "Allow") {
                permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        )
        add(
            if (app.gps.gpsEnabled()) Check("GPS", "On.", CheckLevel.Ok)
            else Check("GPS", "Location is switched off.", CheckLevel.Warn, "Settings") { open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        )
        add(
            if (AdapterDiscovery.hasPermissions(context)) Check("Bluetooth (OBD)", "Allowed.", CheckLevel.Ok)
            else Check("Bluetooth (OBD)", "Needed for Bluetooth OBD adapters.", CheckLevel.Warn, "Allow") { permissions.launch(AdapterDiscovery.requiredPermissions()) }
        )
        add(
            if (NavBridge.hasAccess(context)) Check("Notification access", "Now playing, map turns and messages work.", CheckLevel.Ok)
            else Check("Notification access", "Needed for now playing, Maps/Waze turns and reading messages.", CheckLevel.Warn, "Grant") { open(NavBridge.accessSettingsIntent(context)) }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                if (granted(Manifest.permission.POST_NOTIFICATIONS)) Check("Notifications", "Allowed.", CheckLevel.Ok)
                else Check("Notifications", "Lets the OBD link and scanner keep running in the background.", CheckLevel.Warn, "Allow") {
                    permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            )
        }
        add(
            if (Settings.canDrawOverlays(context)) Check("Display over other apps", "Allowed.", CheckLevel.Ok)
            else Check("Display over other apps", "Needed for the scanner over other apps and Maps + KITT.", CheckLevel.Info, "Allow") {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
            }
        )
        val updater = SelfUpdate(context)
        add(
            if (updater.canInstall()) Check("Install updates", "Allowed.", CheckLevel.Ok)
            else Check("Install updates", "Needed for KITT Config → Updates.", CheckLevel.Warn, "Allow") { open(updater.installPermissionIntent()) }
        )
        add(
            if (MicGuard.hasUsageAccess(context)) Check("Usage access", "Allowed (identifies apps that grab the microphone).", CheckLevel.Ok)
            else Check("Usage access", "Optional: names the app that takes over the microphone.", CheckLevel.Info, "Grant") { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        )
        add(
            if (app.settings.geminiKey.isNotEmpty() || app.settings.perplexityKey.isNotEmpty()) Check("AI key", "Saved.", CheckLevel.Ok)
            else Check("AI key", "KITT's answers need a Gemini or Perplexity key (AI core, above).", CheckLevel.Warn)
        )
        add(
            if (WebViewVersion.isOutdated(webView)) Check("Web browser engine", "Android System WebView $webView is old and slow.", CheckLevel.Warn, "Update") {
                open(Intent(Intent.ACTION_VIEW, "market://details?id=com.google.android.webview".toUri()))
            } else Check("Web browser engine", "WebView ${webView ?: "unknown"}.", CheckLevel.Ok)
        )
        add(Check("Performance", "%.1f GB RAM, %d cores, %s mode.".format(ram, Runtime.getRuntime().availableProcessors(), if (lite) "Lite" else "Full"), CheckLevel.Info))
        add(Check("Version", update ?: "Installed $installed. Tap Check for updates.", if (update?.startsWith("Version") == true) CheckLevel.Warn else CheckLevel.Info, "Check") {
            scope.launch {
                update = "Checking…"
                update = try {
                    val latest = updater.latest(app.settings.updateRepo.value)
                    if (Versions.isNewer(latest.tag, installed)) "Version ${latest.tag} is available: install it under Updates below." else "Up to date ($installed)."
                } catch (e: IOException) {
                    "Couldn't check: ${e.message}"
                }
            }
        })
    }
    val problems = checks.count { it.level == CheckLevel.Warn }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("System check", color = if (problems == 0) LedGreen else DashAmber)
        HelpText(if (problems == 0) "Everything KITT needs is in place." else "$problems thing${if (problems == 1) "" else "s"} to fix. Each has a button.")
        checks.forEach { check ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape).background(
                        when (check.level) {
                            CheckLevel.Ok -> LedGreen
                            CheckLevel.Warn -> DashAmber
                            CheckLevel.Info -> DashGrey
                        }
                    )
                )
                Column(Modifier.weight(1f)) {
                    Text(check.label.uppercase(), style = MaterialTheme.typography.labelLarge, color = DashWhite)
                    Text(check.detail, style = MaterialTheme.typography.bodySmall, color = DashGrey)
                }
                if (check.fix != null && check.fixLabel != null && check.level != CheckLevel.Ok) {
                    KittButton(check.fixLabel, check.fix, Modifier.height(40.dp).width(96.dp), if (check.level == CheckLevel.Warn) DashAmber else DashGrey, active = check.level == CheckLevel.Warn)
                }
            }
        }
    }
}

/** Driving extras: commentary, report cards, fuel and speed warnings, and sound effects. */
@Composable
fun DrivingExtrasSettings(app: KittApplication) {
    val s = app.settings
    val commentary by s.drivingCommentary.state.collectAsState()
    val cards by s.reportCards.state.collectAsState()
    val fuel by s.fuelAlerts.state.collectAsState()
    val limits by s.speedLimits.state.collectAsState()
    val alerts by s.speedAlerts.state.collectAsState()
    val margin by s.speedAlertMargin.state.collectAsState()
    val sounds by s.soundEffects.state.collectAsState()
    val metric by s.metricUnits.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Driving")
        KittToggle("Driving commentary", commentary, { s.drivingCommentary.set(it) }, description = "KITT speaks up about hard stops, revving a cold engine, long idling and full throttle.")
        KittToggle("Report card", cards, { s.reportCards.set(it) }, description = "KITT grades each drive when you switch off; past grades are on the Trips screen.")
        KittToggle("Low fuel warning", fuel, { s.fuelAlerts.set(it) }, description = "Below 12% (or about 37 miles of range), KITT offers the nearest gas station.")
        KittToggle("Speed limits", limits, { s.speedLimits.set(it) }, description = "Shows the road's limit on the map, from OpenStreetMap (not every road has one). Needs data.")
        KittToggle("Speed warning", alerts, { s.speedAlerts.set(it) }, description = "KITT warns when you stay over the limit by more than the margin below.")
        if (alerts) {
            val unit = if (metric) "km/h" else "mph"
            ChoiceRow(listOf(3, 5, 7, 10, 15), margin, { "+$it $unit" }, { s.speedAlertMargin.set(it) })
        }
        KittToggle("Sound effects", sounds, { s.soundEffects.set(it) }, description = "A scanner whoosh when the launcher starts and a turbo-boost whine at full throttle (original sounds).")
    }
}

/** Night theme: switch to another theme while it's dark (light sensor, or sunset to sunrise). */
@Composable
fun NightThemeSetting(app: KittApplication) {
    val name by app.settings.nightTheme.state.collectAsState()
    val current = runCatching { UiTheme.valueOf(name) }.getOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("NIGHT THEME", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        ChoiceRow(listOf<UiTheme?>(null) + UiTheme.entries, current, { it?.label ?: "Same as day" }, { app.settings.nightTheme.set(it?.name.orEmpty()) }, color = ScannerRed)
        HelpText("Switches while night mode says it's dark: the light sensor, or sunset to sunrise at the vehicle's position. Night mode must be Auto or Always.")
    }
}

/** Browser and housekeeping extras: mini player and weekly backups. */
@Composable
fun ExtrasSettings(app: KittApplication) {
    val s = app.settings
    val mini by s.miniPlayer.state.collectAsState()
    val backup by s.autoBackup.state.collectAsState()
    val last by s.lastAutoBackup.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Extras")
        KittToggle("Stream mini player", mini, { s.miniPlayer.set(it) }, description = "A video playing in the stream window keeps going in a corner when you leave the Drive page. Tap it to go back.")
        KittToggle(
            "Weekly backup",
            backup,
            { s.autoBackup.set(it) },
            description = "Saves your setup to Downloads/KITT every week (the last four are kept). Restore it into either KITT Launcher or KITT Launcher.2 with Restore below." +
                if (last > 0) " Last: " + java.text.DateFormat.getDateInstance().format(java.util.Date(last)) + "." else ""
        )
    }
}

/** Report a problem → phone: a QR code to download it over Wi-Fi, or the share sheet. */
@Composable
fun ReportToPhoneButton(app: KittApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val share = remember { com.aiconsole.knightriderlauncher.data.ReportShare(context, app.appScope) }
    var open by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf<String?>(null) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    KittButton(
        "Send report to phone",
        {
            open = true
            link = null
            status = "Preparing the report…"
            scope.launch {
                val written = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { share.writeReport(app.obd) }.getOrNull() }
                file = written
                if (written == null) {
                    status = "Couldn't write the report."
                    return@launch
                }
                link = share.serve(written)
                status = if (link == null) "This head unit isn't on Wi-Fi or a hotspot, so there's no code to scan. Use Share instead." else null
            }
        },
        Modifier.fillMaxWidth().height(48.dp),
        ScannerRed
    )
    if (open) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {
            open = false
            share.stop()
        }) {
            Column(
                Modifier.clip(MaterialTheme.shapes.medium).background(com.aiconsole.knightriderlauncher.ui.theme.KittCharcoal).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("SEND REPORT TO PHONE", style = MaterialTheme.typography.titleMedium, color = ScannerRed)
                link?.let { url ->
                    com.aiconsole.knightriderlauncher.ui.components.QrCode(url, Modifier.size(220.dp))
                    HelpText("Scan with your phone's camera. The phone must be on the same Wi-Fi or hotspot as the head unit. The link works for 10 minutes.")
                    Text(url, style = MaterialTheme.typography.bodySmall, color = DashGrey)
                }
                status?.let { HelpText(it, color = DashAmber) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KittButton("Share instead", {
                        file?.let { f -> runCatching { context.startActivity(share.shareIntent(f)) } }
                    }, Modifier.height(44.dp).width(140.dp), DashAmber, enabled = file != null)
                    KittButton("Close", {
                        open = false
                        share.stop()
                    }, Modifier.height(44.dp).width(100.dp), DashGrey)
                }
            }
        }
    }
}
