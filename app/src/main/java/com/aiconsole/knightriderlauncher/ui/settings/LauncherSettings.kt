package com.aiconsole.knightriderlauncher.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.MainActivity
import com.aiconsole.knightriderlauncher.assistant.MicGuard
import com.aiconsole.knightriderlauncher.data.DriverProfiles
import com.aiconsole.knightriderlauncher.data.HardwareKeys
import com.aiconsole.knightriderlauncher.data.IconPacks
import com.aiconsole.knightriderlauncher.data.InstalledApps
import com.aiconsole.knightriderlauncher.data.KeyAction
import com.aiconsole.knightriderlauncher.data.KeyBindings
import com.aiconsole.knightriderlauncher.data.LauncherApp
import com.aiconsole.knightriderlauncher.data.LauncherBackup
import com.aiconsole.knightriderlauncher.data.ProblemReport
import com.aiconsole.knightriderlauncher.data.ProfileRules
import com.aiconsole.knightriderlauncher.data.ReleaseInfo
import com.aiconsole.knightriderlauncher.data.SelfUpdate
import com.aiconsole.knightriderlauncher.data.UpdateRules
import com.aiconsole.knightriderlauncher.data.UpdateState
import com.aiconsole.knightriderlauncher.data.Versions
import com.aiconsole.knightriderlauncher.ui.components.ChoiceRow
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.KittToggle
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import java.io.IOException
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SCREENSAVER_OPTIONS = listOf(0, 2, 5, 10, 20)

/** Home screen extras: status strip, themed icons, screensaver and the start-up routine. */
@Composable
fun HomeScreenSettings(app: KittApplication) {
    val settings = app.settings
    val context = LocalContext.current
    val strip by settings.statusStrip.state.collectAsState()
    val themedIcons by settings.themedIcons.state.collectAsState()
    val sounds by settings.buttonSounds.state.collectAsState()
    val weatherAlerts by settings.weatherAlerts.state.collectAsState()
    val navVoice by settings.navVoice.state.collectAsState()
    val saver by settings.screensaverMinutes.state.collectAsState()
    val startupApp by settings.startupApp.state.collectAsState()
    val resume by settings.startupResumeMedia.state.collectAsState()
    val apps by produceState(emptyList<LauncherApp>()) { value = InstalledApps.load(context, settings.iconPack.value) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Home screen")
        KittToggle("Status strip", strip, { settings.statusStrip.set(it) }, description = "Clock, heading, Bluetooth, Wi-Fi, GPS, OBD and battery voltage under the scanner.")
        KittToggle("Themed app icons", themedIcons, { settings.themedIcons.set(it) }, description = "Recolour every app icon in the theme's accent.")
        Text("ICON PACK", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        val packs = remember { IconPacks.installed(context) }
        val pack by settings.iconPack.state.collectAsState()
        ChoiceRow(
            options = listOf("") + packs.map { it.packageName },
            selected = pack,
            label = { p -> if (p.isEmpty()) "App icons" else packs.firstOrNull { it.packageName == p }?.label ?: p },
            onSelect = { settings.iconPack.set(it) }
        )
        if (packs.isEmpty()) HelpText("Install any ADW/Nova-compatible icon pack from the Play Store and it appears here.")
        KittToggle("Button sounds", sounds, { settings.buttonSounds.set(it) }, description = "A short console blip on every KITT button press.")
        KittToggle(
            "Weather warnings",
            weatherAlerts,
            { settings.weatherAlerts.set(it) },
            description = "KITT warns about ice, freezing rain, fog, storms and strong wind (Open-Meteo, no account needed). Needs location."
        )
        KittToggle(
            "Spoken directions",
            navVoice,
            { settings.navVoice.set(it) },
            description = "KITT reads out each turn when you navigate from the home map (OpenStreetMap, no account needed)."
        )
        Text("SCREENSAVER WHEN PARKED", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        ChoiceRow(SCREENSAVER_OPTIONS, saver, { if (it == 0) "Off" else "$it min" }, { settings.screensaverMinutes.set(it) })
        Text("ON START OR WAKE", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        ChoiceRow(
            options = listOf("") + apps.map { it.key },
            selected = startupApp,
            label = { key -> if (key.isEmpty()) "Open nothing" else apps.firstOrNull { it.key == key }?.label ?: key },
            onSelect = { settings.startupApp.set(it) }
        )
        KittToggle("Resume audio", resume, { settings.startupResumeMedia.set(it) }, description = "Press play on whatever was last playing.")
        HelpText("Runs when the launcher starts and when the screen wakes after 5+ minutes off (ignition on).")
    }
}

/** Map steering-wheel or other hardware buttons to launcher actions. */
@Composable
fun ButtonSettings(app: KittApplication) {
    val settings = app.settings
    val stored by settings.keyBindings.state.collectAsState()
    val bindings = KeyBindings.decode(stored)
    val learning by HardwareKeys.learning.collectAsState()
    val learned by HardwareKeys.learned.collectAsState()
    var assigning by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(learned) { learned?.let { assigning = it } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Steering-wheel buttons")
        bindings.forEach { (code, action) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")} → ${action.label}", Modifier.weight(1f), color = DashWhite)
                KittButton("Remove", { settings.keyBindings.set(KeyBindings.encode(bindings - code)) }, Modifier.height(40.dp).width(96.dp), AlertRed)
            }
        }
        val pending = assigning
        when {
            learning -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PRESS THE BUTTON NOW…", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = ScannerRed)
                KittButton("Cancel", { HardwareKeys.cancelLearning() }, Modifier.height(40.dp).width(96.dp), DashGrey)
            }
            pending != null -> {
                Text("${android.view.KeyEvent.keyCodeToString(pending).removePrefix("KEYCODE_")}: CHOOSE AN ACTION", style = MaterialTheme.typography.labelLarge, color = DashAmber)
                ChoiceRow(KeyAction.entries, null, { it.label }, { action ->
                    settings.keyBindings.set(KeyBindings.encode(bindings + (pending to action)))
                    assigning = null
                }, color = LedGreen)
            }
            else -> KittButton("Learn a button", { HardwareKeys.startLearning() }, Modifier.fillMaxWidth().height(44.dp), LedGreen, active = true)
        }
        HelpText(
            "Works while the launcher is in front, for buttons the head unit sends as normal key presses. " +
                "Media keys often go straight to the playing app instead."
        )
    }
}

/** "Hey KITT": asks for the microphone first if needed. */
@Composable
fun WakeWordToggle(app: KittApplication) {
    val context = LocalContext.current
    val on by app.settings.wakeWord.state.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.settings.wakeWord.set(true)
    }
    KittToggle(
        "\"Hey KITT\" wake word",
        on,
        { enable ->
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (enable && !granted) permission.launch(Manifest.permission.RECORD_AUDIO) else app.settings.wakeWord.set(enable)
        },
        description = "Listens while the home screen is showing and nothing is playing (it pauses during music, " +
            "which drowns it out anyway; use the steering-wheel voice button or KITT then). Uses the speech recognizer " +
            "below; some chime each time they restart, and a network recognizer uses data. Say \"Hey KITT\", then your " +
            "question or command."
    )
}

/** Per-driver setups; switching restarts the launcher with the other driver's settings. */
@Composable
fun DriverProfileSettings() {
    val context = LocalContext.current
    val profiles = remember { DriverProfiles(context) }
    var names by remember { mutableStateOf(profiles.names()) }
    var active by remember { mutableStateOf(profiles.active) }
    var newName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Driver profiles")
        Text("ACTIVE: ${active ?: "none saved yet"}".uppercase(), style = MaterialTheme.typography.labelLarge, color = LedGreen)
        names.forEach { name ->
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KittButton(
                    if (name == active) "$name (active)" else "Switch to $name",
                    {
                        if (name != active) {
                            try {
                                profiles.switchTo(name)
                                restartLauncher(context)
                            } catch (e: IOException) {
                                message = e.message
                            }
                        }
                    },
                    Modifier.weight(1f).fillMaxHeight(),
                    LedGreen,
                    active = name == active
                )
                KittButton("Delete", {
                    profiles.delete(name)
                    names = profiles.names()
                    active = profiles.active
                }, Modifier.width(96.dp).fillMaxHeight(), AlertRed)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KittTextField(newName, { newName = it }, "Driver name", Modifier.weight(1f))
            KittButton(
                "Save current as",
                {
                    ProfileRules.sanitize(newName)?.let {
                        profiles.saveCurrent(it)
                        names = profiles.names()
                        active = profiles.active
                        newName = ""
                        message = "Saved profile $it."
                    }
                },
                Modifier.height(48.dp),
                LedGreen,
                enabled = ProfileRules.sanitize(newName) != null
            )
        }
        HelpText(
            message ?: ("Each profile keeps its own theme, KITT persona and voice, gauges, favourites and saved sites. " +
                "The truck's adapter, VIN, odometer, maintenance and fuel log are shared. Switching restarts the launcher.")
        )
    }
}

/** Relaunches the launcher so settings read at start pick up new values. */
private fun restartLauncher(context: android.content.Context) {
    context.startActivity(
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    exitProcess(0)
}

/** Check the project's GitHub releases and install a newer signed build. */
@Composable
fun UpdateSettings(app: KittApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { SelfUpdate(context) }
    val repo by app.settings.updateRepo.state.collectAsState()
    val state by SelfUpdate.state.collectAsState()
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val current = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
    val working = state is UpdateState.Downloading || state is UpdateState.Installing

    fun install(r: ReleaseInfo) {
        message = null
        // App scope: closing KITT Config mustn't cancel a download halfway.
        app.appScope.launch { updater.install(r) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Updates")
        Text("INSTALLED: $current", style = MaterialTheme.typography.labelLarge, color = DashWhite)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KittTextField(repo, { app.settings.updateRepo.set(it.trim()) }, "GitHub repo, e.g. tdater/KITT-Launcher-releases", Modifier.weight(1f))
            KittButton(
                if (checking) "Checking…" else "Check",
                {
                    checking = true
                    SelfUpdate.reset()
                    scope.launch {
                        message = try {
                            val latest = updater.latest(repo)
                            release = latest.takeIf { Versions.isNewer(it.tag, current) }
                            if (release == null) "Up to date (latest is ${latest.tag})." else "Version ${latest.tag} is available."
                        } catch (e: IOException) {
                            release = null
                            e.message
                        }
                        checking = false
                    }
                },
                Modifier.height(48.dp).width(104.dp),
                LedGreen,
                enabled = !checking && !working && Versions.isValidRepo(repo)
            )
        }
        release?.let { r ->
            KittButton(
                when (val s = state) {
                    is UpdateState.Downloading -> "Downloading ${r.tag}… ${s.percent?.let { "$it%" }.orEmpty()}"
                    UpdateState.Installing -> "Installing ${r.tag}…"
                    else -> "Install ${r.tag}"
                },
                { install(r) },
                Modifier.fillMaxWidth().height(48.dp),
                ScannerRed,
                active = true,
                enabled = !working
            )
        }
        when (val s = state) {
            UpdateState.NeedsPermission -> {
                HelpText("Android needs your OK for KITT Launcher to install updates. Allow it on the next screen, come back, and tap Install again.", color = DashAmber)
                KittButton(
                    "Allow installs from KITT Launcher",
                    { runCatching { context.startActivity(updater.installPermissionIntent()) } },
                    Modifier.fillMaxWidth().height(48.dp),
                    DashAmber,
                    active = true
                )
            }
            is UpdateState.Done -> {
                HelpText(s.message, color = if (s.success) LedGreen else DashAmber)
                if (!s.success && s.message == UpdateRules.DIFFERENT_KEY) {
                    KittButton(
                        "Open the download page",
                        {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://github.com/$repo/releases/latest".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        Modifier.fillMaxWidth().height(48.dp),
                        DashAmber
                    )
                }
            }
            UpdateState.Installing -> HelpText("Confirm the install if Android asks. The launcher restarts after updating.")
            else -> Unit
        }
        HelpText(
            message ?: ("Installs signed releases built by the project's GitHub workflow. Android only accepts an update " +
                "signed with the same key as the installed app, so switch to a CI-built release once; after that, " +
                "updates install from here. Android asks you to allow installs from the launcher the first time.")
        )
    }
}

/**
 * Microphone fix: for head units that jump to their own radio/climate app when another app opens
 * the microphone. Names the app that took over, links to its settings and the default-assistant
 * setting, and gives ADB commands that silence it.
 */
@Composable
fun MicFixSettings(app: KittApplication) {
    val context = LocalContext.current
    val last by MicGuard.last.collectAsState()
    val vrMic by app.settings.voiceRecognitionMic.state.collectAsState()
    var usage by remember { mutableStateOf(MicGuard.hasUsageAccess(context)) }
    var copied by remember { mutableStateOf(false) }
    val pkg = last?.packageName
    fun open(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: android.content.ActivityNotFoundException) {
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Microphone fix")
        Text(
            when {
                last == null -> "NO TAKEOVER DETECTED YET"
                pkg != null -> "LAST TAKEOVER: $pkg"
                else -> "TAKEOVER DETECTED (ALLOW USAGE ACCESS TO NAME THE APP)"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (last == null) LedGreen else ScannerRed
        )
        HelpText(
            "If tapping the mic jumps to the radio/climate app: 1) set KITT's speech input to KITT (Gemini) above; " +
                "2) set the system Default digital assistant to None; 3) if it still happens, silence that app's microphone " +
                "with the ADB commands below. KITT stops listening by itself the moment a takeover is detected."
        )
        KittToggle(
            "Use voice-recognition mic source",
            vrMic,
            { app.settings.voiceRecognitionMic.set(it) },
            description = "Off (recommended) records from the plain microphone, which head units are less likely to hand to their own voice system."
        )
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KittButton("Default assistant", { open(Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)) }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
            KittButton(
                if (usage) "Usage access ✓" else "Allow usage access",
                {
                    open(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    usage = MicGuard.hasUsageAccess(context)
                },
                Modifier.weight(1f).fillMaxHeight(),
                LedGreen,
                active = usage
            )
            KittButton(
                "Open ${pkg?.substringAfterLast('.') ?: "app"} settings",
                { pkg?.let { open(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$it".toUri())) } },
                Modifier.weight(1f).fillMaxHeight(),
                DashAmber,
                enabled = pkg != null
            )
        }
        Text(MicGuard.adbFix(pkg), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = DashWhite)
        if (pkg == null) {
            HelpText("Find the package right after a takeover with: ${MicGuard.FIND_APP_COMMAND}")
        }
        KittButton(
            if (copied) "Copied" else "Copy ADB commands",
            {
                val text = MicGuard.adbFix(pkg) + "\n# find the app: " + MicGuard.FIND_APP_COMMAND
                context.getSystemService(android.content.ClipboardManager::class.java)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("KITT mic fix", text))
                copied = true
            },
            Modifier.fillMaxWidth().height(44.dp),
            LedGreen
        )
        HelpText(
            "ADB without a cable: head unit Settings → About → tap Build number 7 times, then Developer options → " +
                "Wireless debugging → Pair device with pairing code; on the PC run adb pair <ip:port>, then adb connect <ip:port>."
        )
    }
}

/** Save or restore settings, gauges, custom PIDs and userscripts. */
@Composable
fun BackupSettings(app: KittApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val backup = remember { LauncherBackup(context) }
    val report = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        message = "Writing the report…"
        // App scope, not this screen's: closing KITT Config mustn't cancel the write.
        app.appScope.launch(Dispatchers.Main) {
            message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { ProblemReport.write(context, app.obd, it) }
                        ?: throw IOException("Couldn't open the file")
                }
                "Report saved. Send me the file."
            } catch (e: IOException) {
                "Report failed: ${e.message}"
            } catch (e: SecurityException) {
                "Report failed: ${e.message}"
            }
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use(backup::export) ?: throw IOException("Couldn't open the file")
                }
                "Backup saved."
            } catch (e: IOException) {
                "Backup failed: ${e.message}"
            } catch (e: SecurityException) {
                "Backup failed: ${e.message}"
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(backup::import) ?: throw IOException("Couldn't open the file")
                }
                // Settings are read once at start: relaunch so everything picks up the restored values.
                restartLauncher(context)
            } catch (e: IOException) {
                message = "Restore failed: ${e.message}"
            } catch (e: SecurityException) {
                message = "Restore failed: ${e.message}"
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Backup")
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Save backup", { export.launch("kitt-launcher-backup.json") }, Modifier.weight(1f).fillMaxHeight(), LedGreen, active = true)
            KittButton("Restore…", { import.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
        }
        KittButton(
            "Report a problem",
            { report.launch("kitt-report-${System.currentTimeMillis() / 1000}.txt") },
            Modifier.fillMaxWidth().height(48.dp),
            ScannerRed
        )
        HelpText(
            message ?: ("Report a problem saves a text file with recent crashes, the OBD connection log, settings (no API keys) " +
                "and this app's log. " +
                "Save backup saves settings, gauges, custom PIDs and userscripts to a file (USB stick, Downloads…). " +
                "API keys aren't included; re-enter them after restoring. Restoring restarts the launcher.")
        )
    }
}
