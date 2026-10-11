package com.aiconsole.knightriderlauncher.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.assistant.AiAppHandoff
import com.aiconsole.knightriderlauncher.assistant.GeminiAssistantClient
import com.aiconsole.knightriderlauncher.assistant.GeminiSpeechProtocol
import com.aiconsole.knightriderlauncher.assistant.SpeechRecognizers
import com.aiconsole.knightriderlauncher.data.LiteMode
import com.aiconsole.knightriderlauncher.data.UiScale
import com.aiconsole.knightriderlauncher.data.settings.AiProvider
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.data.settings.NightMode
import com.aiconsole.knightriderlauncher.data.settings.PersonaStyle
import com.aiconsole.knightriderlauncher.data.settings.SpeechInput
import com.aiconsole.knightriderlauncher.data.settings.VoiceEngine
import com.aiconsole.knightriderlauncher.nav.MessageReadout
import com.aiconsole.knightriderlauncher.nav.NavBridge
import com.aiconsole.knightriderlauncher.ui.components.ChoiceRow
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.KittToggle
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.scanner.ScannerOverlayService
import com.aiconsole.knightriderlauncher.ui.theme.AccentSwatches
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.theme.UiTheme
import com.aiconsole.knightriderlauncher.ui.theme.hud
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SHIFT_POINTS = (3000..7500 step 250).toList()
private const val VOICE_CHECK = "Voice check. All systems functional."
private val PERPLEXITY_PRESETS = listOf("fast", "low", "medium", "high")

/** KITT CONFIG: AI core (provider, keys, models), personality, display and quick links. */
@Composable
fun KittConfigScreen(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val settings = app.settings
    val scope = rememberCoroutineScope()

    val provider by settings.provider.state.collectAsState()
    val persona by settings.persona.state.collectAsState()
    val savedGeminiKey by settings.geminiKeySaved.collectAsState()
    val savedPerplexityKey by settings.perplexityKeySaved.collectAsState()
    val chatModel by settings.chatModel.state.collectAsState()
    val diagnosticModel by settings.diagnosticModel.state.collectAsState()
    val chatPreset by settings.perplexityChatPreset.state.collectAsState()
    val diagPreset by settings.perplexityDiagnosticPreset.state.collectAsState()
    val webSearch by settings.webSearch.state.collectAsState()
    val localModel by settings.perplexityLocalModel.state.collectAsState()
    val driverName by settings.driverName.state.collectAsState()
    val voiceReply by settings.voiceReply.state.collectAsState()
    val greeting by settings.greeting.state.collectAsState()
    val voiceAlerts by settings.voiceAlerts.state.collectAsState()
    val voiceName by settings.kittVoiceName.state.collectAsState()
    val voiceEngine by settings.voiceEngine.state.collectAsState()
    val readMessages by settings.readMessages.state.collectAsState()
    val speechInput by settings.speechInput.state.collectAsState()
    val drivingLayout by settings.drivingMode.state.collectAsState()
    val geminiVoice by settings.geminiVoice.state.collectAsState()
    val recognizerChoice by settings.speechRecognizer.state.collectAsState()
    val recognizers = remember { SpeechRecognizers.installed(context) }
    val recognizerOptions = remember(recognizers) {
        listOf(SpeechRecognizers.AUTO) +
            (if (SpeechRecognizers.onDeviceAvailable(context)) listOf(SpeechRecognizers.ON_DEVICE) else emptyList()) +
            recognizers.map { it.component.flattenToString() }
    }
    val metric by settings.metricUnits.state.collectAsState()
    val overlay by settings.scannerOverlay.state.collectAsState()
    val theme by settings.uiTheme.state.collectAsState()
    val accent by settings.accentColor.state.collectAsState()
    val shiftLight by settings.shiftLight.state.collectAsState()
    val shiftRpm by settings.shiftRpm.state.collectAsState()
    val rain by settings.matrixRain.state.collectAsState()
    val nightMode by settings.nightMode.state.collectAsState()
    val showMap by settings.showMap.state.collectAsState()
    val uiScale by settings.uiScale.state.collectAsState()
    val liteMode by settings.liteMode.state.collectAsState()
    val liteActive by app.lite.collectAsState()
    val navNotifications by settings.navNotifications.state.collectAsState()
    var navAccess by remember { mutableStateOf(NavBridge.hasAccess(context)) }

    var geminiKeyInput by remember { mutableStateOf("") }
    var perplexityKeyInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf(driverName) }
    var keyStatus by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf(listOf(KittSettings.DEFAULT_CHAT_MODEL, KittSettings.DEFAULT_DIAGNOSTIC_MODEL)) }
    var voices by remember { mutableStateOf(emptyList<String>()) }
    val handoffApps = remember { AiAppHandoff.installed(context) }

    fun testGeminiKey() {
        keyStatus = "Checking key…"
        scope.launch {
            GeminiAssistantClient(app).listModels(settings.geminiKey).fold(
                onSuccess = { list ->
                    if (list.isNotEmpty()) models = list.map { it.id }
                    keyStatus = "Key OK · ${list.size} chat models available."
                },
                onFailure = { keyStatus = "Key check failed: ${it.message}" }
            )
        }
    }

    LaunchedEffect(Unit) {
        voices = app.kittVoice.englishVoices()
        if (settings.geminiKey.isNotEmpty()) {
            GeminiAssistantClient(app).listModels(settings.geminiKey).onSuccess { list ->
                if (list.isNotEmpty()) models = list.map { it.id }
            }
        }
    }

    // Notification access is granted in system settings; pick it up when the driver comes back.
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            navAccess = NavBridge.hasAccess(context)
        }
    }

    BackHandler(onBack = onDismiss)

    KittPanel(
        title = "KITT Config",
        modifier = modifier.pointerInput(Unit) { detectTapGestures {} },
        accent = DashAmber,
        trailing = { KittButton("Close", onDismiss, Modifier.height(32.dp).width(80.dp), DashGrey) }
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ---------------- AI core ----------------
            item { SelfCheckPanel(app) }
            item { SectionHeader("AI core") }
            item { ChoiceRow(AiProvider.entries, provider, { it.label }, { settings.provider.set(it) }, color = ScannerRed) }

            if (provider == AiProvider.Gemini) {
                item {
                    HelpText(
                        "Get a key at aistudio.google.com/apikey. The free tier works but has no web search; " +
                            "with billing enabled, Gemini 3.x includes 5,000 free searches a month. " +
                            if (savedGeminiKey.isNotEmpty()) "A key is saved (encrypted on this unit)." else "No key saved yet."
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KittTextField(geminiKeyInput, { geminiKeyInput = it }, "Paste Gemini API key", Modifier.weight(1f), secret = true)
                        KittButton(
                            label = "Save",
                            onClick = {
                                settings.setGeminiKey(geminiKeyInput)
                                geminiKeyInput = ""
                                testGeminiKey()
                            },
                            modifier = Modifier.height(48.dp),
                            color = LedGreen,
                            enabled = geminiKeyInput.isNotBlank()
                        )
                        KittButton("Test", ::testGeminiKey, Modifier.height(48.dp), DashAmber)
                        KittButton(
                            label = "Forget",
                            onClick = {
                                settings.setGeminiKey("")
                                keyStatus = "Key removed."
                            },
                            modifier = Modifier.height(48.dp),
                            color = ScannerRed,
                            enabled = savedGeminiKey.isNotEmpty()
                        )
                    }
                }
                keyStatus?.let { item { HelpText(it, color = DashAmber) } }
                item { Text("CONVERSATION MODEL (FAST)", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
                item { ChoiceRow(models, chatModel, { it.removePrefix("gemini-") }, { settings.chatModel.set(it) }) }
                item { Text("DIAGNOSTICS MODEL (SMARTEST)", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
                item { ChoiceRow(models, diagnosticModel, { it.removePrefix("gemini-") }, { settings.diagnosticModel.set(it) }) }
                item {
                    KittToggle(
                        "Web search",
                        webSearch,
                        { settings.webSearch.set(it) },
                        description = "Grounds answers in Google Search (recalls, service intervals, prices). Skipped automatically on free-tier keys."
                    )
                }
            } else {
                item {
                    HelpText(
                        "Perplexity Agent API key from perplexity.ai/account/api. " +
                            if (savedPerplexityKey.isNotEmpty()) "A key is saved (encrypted on this unit)." else "No key saved yet."
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KittTextField(perplexityKeyInput, { perplexityKeyInput = it }, "Paste Perplexity API key", Modifier.weight(1f), secret = true)
                        KittButton(
                            label = "Save",
                            onClick = {
                                settings.setPerplexityKey(perplexityKeyInput)
                                perplexityKeyInput = ""
                            },
                            modifier = Modifier.height(48.dp),
                            color = LedGreen,
                            enabled = perplexityKeyInput.isNotBlank()
                        )
                        KittButton(
                            label = "Forget",
                            onClick = { settings.setPerplexityKey("") },
                            modifier = Modifier.height(48.dp),
                            color = ScannerRed,
                            enabled = savedPerplexityKey.isNotEmpty()
                        )
                    }
                }
                item { Text("CONVERSATION PRESET", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
                item { ChoiceRow(PERPLEXITY_PRESETS, chatPreset, { it }, { settings.perplexityChatPreset.set(it) }) }
                item { Text("DIAGNOSTICS PRESET", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
                item { ChoiceRow(PERPLEXITY_PRESETS, diagPreset, { it }, { settings.perplexityDiagnosticPreset.set(it) }) }
                item {
                    KittToggle(
                        "Web search",
                        webSearch,
                        { settings.webSearch.set(it) },
                        description = "Questions about current facts (weather, prices, recalls) and diagnostics search the web. " +
                            "Vehicle readings, trips and small talk skip the search charge. Off: never search."
                    )
                }
                item { Text("NO-SEARCH MODEL", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
                item {
                    KittTextField(localModel, { settings.perplexityLocalModel.set(it.trim()) }, "Blank: always use the preset", Modifier.fillMaxWidth())
                }
                item {
                    HelpText("Answers that don't need the web use this Agent API model with no tools. Each answer shows its cost.")
                }
            }

            item {
                HelpText(
                    "Subscriptions (Gemini, Claude, Perplexity Pro) only work inside their own apps. " +
                        if (handoffApps.isEmpty()) "Install any of those apps and KITT can hand questions to them."
                        else "Installed for hand-off from the KITT panel: ${handoffApps.joinToString { it.label }}."
                )
            }

            // ---------------- Personality ----------------
            item { SectionHeader("Personality") }
            item { ChoiceRow(PersonaStyle.entries, persona, { it.label }, { settings.persona.set(it) }, color = ScannerRed) }
            item {
                HelpText(
                    when (persona) {
                        PersonaStyle.Savage -> "The dial past maximum: swearing in every sentence, sarcasm on every fact, and a parting shot on every spoken line. The facts and safety warnings stay exact. Turn-by-turn directions say the turn plainly first, then the insult."
                        PersonaStyle.Unfiltered -> "Sarcastic and foul-mouthed: swears in every reply and alert and roasts your driving and your vehicle, while the facts stay accurate. Turn-by-turn directions say the turn plainly first, then the insult."
                        PersonaStyle.Classic -> "Calm, precise and dry, like the original."
                        PersonaStyle.Karr -> "KITT's evil twin: cold, superior and quietly menacing, obsessed with its own preservation. No swearing. The facts and safety warnings stay exact."
                    }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KittTextField(nameInput, { nameInput = it }, "Driver name (optional)", Modifier.weight(1f))
                    KittButton(
                        label = "Save",
                        onClick = { settings.driverName.set(nameInput.trim()) },
                        modifier = Modifier.height(48.dp),
                        color = LedGreen,
                        enabled = nameInput.trim() != driverName
                    )
                }
            }
            item { Text("VOICE", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
            item {
                ChoiceRow(
                    options = VoiceEngine.entries,
                    selected = voiceEngine,
                    label = { it.label },
                    onSelect = {
                        settings.voiceEngine.set(it)
                        app.kittVoice.speak(VOICE_CHECK)
                    }
                )
            }
            if (voiceEngine == VoiceEngine.Gemini) {
                item {
                    ChoiceRow(
                        options = GeminiSpeechProtocol.VOICES.map { it.first },
                        selected = geminiVoice,
                        label = { name -> "$name · ${GeminiSpeechProtocol.VOICES.first { it.first == name }.second}" },
                        onSelect = {
                            settings.geminiVoice.set(it)
                            app.kittVoice.speak(VOICE_CHECK)
                        }
                    )
                }
                item {
                    HelpText(
                        if (savedGeminiKey.isEmpty() && settings.geminiKey.isEmpty()) {
                            "Gemini neural voice needs a Gemini API key (AI core, above). Until then KITT uses the device voice."
                        } else {
                            "Natural, KITT-style delivery generated by Gemini. Needs internet; phrases are cached so repeats " +
                                "and alerts play instantly. Falls back to the device voice below when offline."
                        }
                    )
                }
                item { Text("OFFLINE FALLBACK VOICE", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
            }
            item {
                ChoiceRow(
                    options = listOf("") + voices,
                    selected = voiceName,
                    label = { if (it.isEmpty()) "System default" else it },
                    onSelect = {
                        settings.kittVoiceName.set(it)
                        app.kittVoice.setVoice(it)
                        app.kittVoice.speak(VOICE_CHECK, deviceOnly = true)
                    }
                )
            }
            if (voices.isEmpty()) item { HelpText("Voices appear once the text-to-speech engine is ready; reopen this screen if the list is empty.") }
            item { Text("SPEECH INPUT (MIC BUTTON)", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
            item { ChoiceRow(SpeechInput.entries, speechInput, { it.label }, { settings.speechInput.set(it) }) }
            item {
                HelpText(
                    "KITT (Gemini) records the mic itself and has Gemini transcribe it, so the head unit's own voice " +
                        "assistant is never involved. Auto uses it whenever a Gemini key is set."
                )
            }
            item { MicFixSettings(app) }
            item { Text("SPEECH RECOGNIZER (MICROPHONE)", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
            item {
                ChoiceRow(
                    options = recognizerOptions,
                    selected = recognizerChoice,
                    label = { choice ->
                        when (choice) {
                            SpeechRecognizers.AUTO -> "Auto"
                            SpeechRecognizers.ON_DEVICE -> "Android on-device"
                            else -> recognizers.firstOrNull { it.component.flattenToString() == choice }
                                ?.let { if (it.systemDefault) "${it.label} · system default" else it.label }
                                ?: choice
                        }
                    },
                    onSelect = { settings.speechRecognizer.set(it) }
                )
            }
            item {
                HelpText(
                    "In use: ${SpeechRecognizers.describe(context, recognizerChoice)}. If tapping SPEAK switches the " +
                        "head unit to another app, its built-in voice assistant is the recognizer: pick a different one here."
                )
            }
            item { KittToggle("Speak replies", voiceReply, { settings.voiceReply.set(it) }) }
            item { WakeWordToggle(app) }
            item { Text("READ INCOMING MESSAGES", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
            item {
                ChoiceRow(MessageReadout.entries, readMessages, { it.label }, { settings.readMessages.set(it) })
            }
            item { HelpText("Texts and chat apps, read aloud with a banner. Uses the same notification access as the map.") }
            item { KittToggle("Greeting on start", greeting, { settings.greeting.set(it) }) }
            item {
                KittToggle(
                    "Voice alerts",
                    voiceAlerts,
                    { settings.voiceAlerts.set(it) },
                    description = "Overheating, charging faults, new trouble codes and your gauge limits."
                )
            }

            // ---------------- Display ----------------
            item { SectionHeader("Display") }
            item { Text("SCREEN SIZE", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
            item { ChoiceRow(UiScale.entries, uiScale, { it.label }, { settings.uiScale.set(it) }, color = ScannerRed) }
            item {
                HelpText(
                    "Auto fits 7\" to 10\" landscape head units: a little smaller on short 480 dp screens (1280x720), " +
                        "a little larger on big 10\" panels; portrait screens keep their normal size. Compact fits more on screen; Large is easier to hit while driving."
                )
            }
            item { Text("PERFORMANCE", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
            item { ChoiceRow(LiteMode.entries, liteMode, { it.label }, { settings.liteMode.set(it) }, color = LedGreen) }
            item {
                HelpText(
                    (if (liteActive) "Lite mode is ON. " else "Full mode is ON. ") +
                        "Lite keeps fewer web pages loaded (hidden tabs reload when you open them), draws the scanner at 30 fps, " +
                        "stops idle animations and the Matrix rain. Every feature still works. Auto turns Lite on for units with " +
                        "3 GB of RAM or less or four CPU cores or fewer" + (if (app.lowEndDevice) " (like this one)." else ".")
                )
            }
            item { Text("THEME", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
            item { ChoiceRow(UiTheme.entries, theme, { it.label }, { settings.uiTheme.set(it) }, color = ScannerRed) }
            if (theme.hud != null) {
                item { FilmThemeSettings(app) }
            }
            item { Text("ACCENT COLOUR", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
            item {
                LazyRow(Modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AccentSwatches) { argb ->
                        val selected = argb == accent
                        Box(
                            Modifier
                                .size(width = 56.dp, height = 44.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(if (argb == 0L) KittBlack else Color(argb.toInt()))
                                .border(if (selected) 3.dp else 1.dp, if (selected) DashWhite else KittBezel, MaterialTheme.shapes.small)
                                .clickable { settings.accentColor.set(argb) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (argb == 0L) Text("THEME", style = MaterialTheme.typography.labelSmall, color = DashGrey)
                        }
                    }
                }
            }
            if (theme == UiTheme.Colorado) {
                item { KittToggle("Digital rain", rain, { settings.matrixRain.set(it) }, description = "Falling code glyphs behind the panels.") }
            }
            item { Text("NIGHT MODE", style = MaterialTheme.typography.labelMedium, color = DashGrey) }
            item { ChoiceRow(NightMode.entries, nightMode, { it.label }, { settings.nightMode.set(it) }) }
            item {
                HelpText(
                    "Dims the whole launcher for night driving. Auto follows the head unit's light sensor, " +
                        "or sunset to sunrise at the vehicle's position (the clock, 7 PM to 7 AM, without GPS) when it has none."
                )
            }
            item { NightThemeSetting(app) }
            item { ChoiceRow(listOf(false, true), metric, { if (it) "Metric" else "Imperial" }, { settings.metricUnits.set(it) }) }
            item {
                KittToggle(
                    "Driving layout",
                    drivingLayout,
                    { settings.drivingMode.set(it) },
                    description = "While moving, the Drive page swaps the app grid for big media controls, KITT and your top favourites."
                )
            }
            item {
                KittToggle(
                    "Shift light",
                    shiftLight,
                    { settings.shiftLight.set(it) },
                    description = "The scanner fills green, amber, then red as revs near your shift point, and flashes at it."
                )
            }
            if (shiftLight) {
                item {
                    ChoiceRow(
                        options = SHIFT_POINTS,
                        selected = shiftRpm,
                        label = { "$it RPM" },
                        onSelect = { settings.shiftRpm.set(it) },
                        color = ScannerRed
                    )
                }
            }
            item {
                KittToggle(
                    "Scanner over all apps",
                    overlay,
                    { enabled ->
                        if (enabled && !Settings.canDrawOverlays(context)) {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: ActivityNotFoundException) {
                            }
                        }
                        settings.scannerOverlay.set(enabled)
                        if (enabled) ScannerOverlayService.start(context) else ScannerOverlayService.stop(context)
                    },
                    description = "Keeps the red scanner sweeping across the top while fullscreen apps are open. " +
                        "Needs the \"display over other apps\" permission."
                )
            }

            // ---------------- Navigation ----------------
            item { SectionHeader("Navigation") }
            item {
                KittToggle(
                    "Home-screen map",
                    showMap,
                    { settings.showMap.set(it) },
                    description = "OpenStreetMap that follows the car. Needs location access; off shows the vector grid."
                )
            }
            item {
                KittToggle(
                    "Turn-by-turn from Maps / Waze",
                    navNotifications,
                    { settings.navNotifications.set(it) },
                    description = "Shows the next turn from Google Maps or Waze on the home screen. " +
                        "Only those apps' navigation notifications are read."
                )
            }
            if (navNotifications && !navAccess) {
                item {
                    KittButton(
                        label = "Grant notification access",
                        onClick = {
                            try {
                                context.startActivity(NavBridge.accessSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: ActivityNotFoundException) {
                                try {
                                    context.startActivity(
                                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    // Some head-unit ROMs strip this screen entirely.
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        color = DashAmber,
                        active = true
                    )
                }
            }

            item { DrivingExtrasSettings(app) }
            item { ExtrasSettings(app) }
            item { HomeScreenSettings(app) }
            item { ButtonSettings(app) }
            item { DriverProfileSettings() }
            item { BackupSettings(app) }
            item { UpdateSettings(app) }

            // ---------------- Quick links ----------------
            item { SectionHeader("Quick links") }
            item {
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
                        "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
                        "Display" to Settings.ACTION_DISPLAY_SETTINGS,
                        "Home app" to Settings.ACTION_HOME_SETTINGS
                    ).forEach { (label, action) ->
                        KittButton(
                            label = label,
                            onClick = {
                                try {
                                    context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: ActivityNotFoundException) {
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            color = DashAmber
                        )
                    }
                }
            }
            item { Column(Modifier.height(24.dp)) {} }
        }
    }
}
