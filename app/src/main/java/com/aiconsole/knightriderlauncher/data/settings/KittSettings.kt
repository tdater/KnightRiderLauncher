package com.aiconsole.knightriderlauncher.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aiconsole.knightriderlauncher.BuildConfig
import com.aiconsole.knightriderlauncher.assistant.GeminiSpeechProtocol
import com.aiconsole.knightriderlauncher.assistant.SpeechRecognizers
import com.aiconsole.knightriderlauncher.data.LiteMode
import com.aiconsole.knightriderlauncher.data.UiScale
import com.aiconsole.knightriderlauncher.nav.MessageReadout
import com.aiconsole.knightriderlauncher.ui.theme.UiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AiProvider(val label: String) {
    Gemini("Google Gemini"),
    Perplexity("Perplexity")
}

enum class PersonaStyle(val label: String) {
    /** Maximum sarcasm and profanity: the default. */
    Savage("Savage KITT"),
    Unfiltered("Unfiltered KITT"),
    Classic("Classic KITT"),
    /** KITT's evil twin from the series: cold, superior and menacing rather than crude. */
    Karr("KARR");

    /** Swears and roasts (Savage and Unfiltered); Classic and KARR don't. */
    val rude: Boolean get() = this == Savage || this == Unfiltered
}

enum class VoiceEngine(val label: String) {
    Gemini("Gemini neural"),
    Device("Device TTS")
}

/** How the mic button turns speech into text. */
enum class SpeechInput(val label: String) {
    Auto("Auto"),
    Gemini("KITT (Gemini)"),
    Device("Device recognizer")
}

enum class NightMode(val label: String) {
    Off("Off"),
    Auto("Auto"),
    On("Always")
}

/** All user-facing launcher settings. Plain prefs for options, [SecureStore] for API keys. */
class KittSettings(context: Context, private val scope: CoroutineScope) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("kitt_settings", Context.MODE_PRIVATE)
    private val secure = SecureStore(context)

    /** A persisted value exposed as a [StateFlow]. */
    inner class Pref<T>(
        private val key: String,
        default: T,
        read: SharedPreferences.(String, T) -> T,
        private val write: SharedPreferences.Editor.(String, T) -> Unit
    ) {
        private val flow = MutableStateFlow(prefs.read(key, default))
        val state: StateFlow<T> = flow.asStateFlow()
        val value: T get() = flow.value

        fun set(newValue: T) {
            flow.value = newValue
            prefs.edit().apply { write(key, newValue) }.apply()
        }
    }

    private fun boolPref(key: String, default: Boolean) =
        Pref(key, default, { k, d -> getBoolean(k, d) }, { k, v -> putBoolean(k, v) })

    private fun longPref(key: String, default: Long) =
        Pref(key, default, { k, d -> getLong(k, d) }, { k, v -> putLong(k, v) })

    private fun intPref(key: String, default: Int) =
        Pref(key, default, { k, d -> getInt(k, d) }, { k, v -> putInt(k, v) })

    private fun stringPref(key: String, default: String) =
        Pref(key, default, { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) })

    // --- AI core ---
    val provider = Pref(
        "ai_provider", AiProvider.Gemini,
        { k, d -> runCatching { AiProvider.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) }
    )
    val persona = Pref(
        "persona", PersonaStyle.Savage,
        { k, d -> runCatching { PersonaStyle.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) }
    )
    val chatModel = stringPref("chat_model", DEFAULT_CHAT_MODEL)
    val diagnosticModel = stringPref("diagnostic_model", DEFAULT_DIAGNOSTIC_MODEL)
    val perplexityChatPreset = stringPref("pplx_chat_preset", "fast")
    val perplexityDiagnosticPreset = stringPref("pplx_diag_preset", "low")
    /** Agent API model for questions that don't need the web (no search charge); blank = always use presets. */
    val perplexityLocalModel = stringPref("pplx_local_model", DEFAULT_PERPLEXITY_LOCAL_MODEL)
    val webSearch = boolPref("web_search", true)
    val driverName = stringPref("driver_name", "")
    val voiceReply = boolPref("voice_reply", true)
    val greeting = boolPref("greeting", true)
    val voiceAlerts = boolPref("voice_alerts", true)
    val readMessages = Pref(
        "read_messages", MessageReadout.Off,
        { k, d -> runCatching { MessageReadout.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) }
    )
    /** Device TTS voice: the offline fallback, or the only voice when [voiceEngine] is Device. */
    val kittVoiceName = stringPref("kitt_voice", "")
    val voiceEngine = Pref(
        "voice_engine", VoiceEngine.Gemini,
        { k, d -> runCatching { VoiceEngine.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) }
    )
    val geminiVoice = stringPref("gemini_voice", GeminiSpeechProtocol.DEFAULT_VOICE)

    /** Flattened ComponentName, [SpeechRecognizers.AUTO] or [SpeechRecognizers.ON_DEVICE]. */
    val speechRecognizer = stringPref("speech_recognizer", SpeechRecognizers.AUTO)
    /** Record with the "voice recognition" mic source instead of the plain mic. */
    val voiceRecognitionMic = boolPref("voice_recognition_mic", false)
    /** Auto: KITT's own Gemini transcription when a key is set, else a safe device recognizer. */
    val speechInput = Pref(
        "speech_input", SpeechInput.Auto,
        { k, d -> runCatching { SpeechInput.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) }
    )

    // --- Display ---
    val metricUnits = boolPref("metric_units", false)
    val scannerOverlay = boolPref("scanner_overlay", false)
    val uiTheme = Pref(
        "ui_theme", UiTheme.Kitt,
        { k, d -> runCatching { UiTheme.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) }
    )
    val matrixRain = boolPref("matrix_rain", true)
    /** Custom accent as ARGB, 0 = the theme's own (see AccentSwatches). */
    val accentColor = longPref("accent_color", 0L)
    /** Scanner doubles as a shift light near [shiftRpm]. */
    val shiftLight = boolPref("shift_light", false)
    val shiftRpm = intPref("shift_rpm", 5000)
    val nightMode = Pref(
        "night_mode", NightMode.Auto,
        { k, d -> runCatching { NightMode.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) }
    )
    /** Driving layout (big controls while moving). Off by default; new key so earlier defaults don't carry over. */
    val drivingMode = boolPref("driving_layout_v2", false)
    val showMap = boolPref("show_map", true)
    /** Lite mode: fewer animations and live web pages for slow head units (Auto decides from the hardware). */
    val liteMode = Pref(
        "lite_mode", LiteMode.Auto,
        { k, d -> runCatching { LiteMode.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) }
    )
    /** Launcher size on top of the screen density (Auto fits 7"–10" landscape screens). */
    val uiScale = Pref(
        "ui_scale", UiScale.Auto,
        { k, d -> runCatching { UiScale.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) }
    )

    // --- Home & app drawer (newline-separated LauncherApp keys, see AppLists) ---
    val pinnedApps = stringPref("pinned_apps", "")
    val hiddenApps = stringPref("hidden_apps", "")

    /** Launch counts for "most used" (AppUsage format). */
    val appUsage = stringPref("app_usage", "")
    /** Tint app icons in the theme's accent colour. */
    val themedIcons = boolPref("themed_icons", false)
    /** Icon pack package name; "" = the apps' own icons. */
    val iconPack = stringPref("icon_pack", "")
    /** Short console beep on KITT button presses. */
    val buttonSounds = boolPref("button_sounds", false)
    /** Speak road warnings from the weather (ice, fog, storms). */
    val weatherAlerts = boolPref("weather_alerts", true)
    /** KITT speaks turn-by-turn directions from the home map. */
    val navVoice = boolPref("nav_voice", true)
    /** Recent map destinations, encoded by [com.aiconsole.knightriderlauncher.nav.RouteRules.encodeRecents]. */
    val navRecents = stringPref("nav_recents", "")
    /** GitHub repository (owner/name) whose releases the in-app updater checks. */
    val updateRepo = stringPref("update_repo", DEFAULT_UPDATE_REPO)
    /** Fill-up log (FuelLog format). */
    val fuelLog = stringPref("fuel_log", "")
    /** Clock, connectivity, GPS and OBD status under the scanner. */
    val statusStrip = boolPref("status_strip", true)
    /** Minutes parked and untouched before the KITT screensaver shows; 0 = off. */
    val screensaverMinutes = intPref("screensaver_minutes", 0)
    /** Start-up routine: app to open (LauncherApp key, "" = none) and whether to resume audio. */
    val startupApp = stringPref("startup_app", "")
    val startupResumeMedia = boolPref("startup_resume_media", false)
    /** Hardware button bindings (KeyBindings format). */
    val keyBindings = stringPref("key_bindings", "")
    /** Home-screen widget ids, one per line. */
    val widgetIds = stringPref("widget_ids", "")

    /** Stream window asks sites for their desktop layout (full players, more settings). */
    val streamDesktop = boolPref("stream_desktop", true)
    /** The driver's saved one-tap sites (StreamSites format). */
    val streamBookmarks = stringPref("stream_bookmarks", "")
    /** Saved sites on the AI and Games pages (StreamSites format). */
    val aiBookmarks = stringPref("ai_bookmarks", "")
    val gameBookmarks = stringPref("game_bookmarks", "")
    /** Per-site browser options (SiteSettings format). */
    val siteSettings = stringPref("site_settings", "")
    /** Darken pages that have no dark theme of their own (default for sites without an override). */
    val darkPages = boolPref("dark_pages", false)
    /** Block known ad and tracker hosts (default for sites without an override). */
    val blockAds = boolPref("block_ads", true)
    /** Games page: map controller buttons to keyboard keys (off = native Gamepad API sites). */
    val gamepadKeyboard = boolPref("gamepad_keyboard", true)
    val navNotifications = boolPref("nav_notifications", true)
    val wakeWord = boolPref("wake_word", false)

    // --- Driving extras ---
    /** KITT comments on hard stops, cold revs, long idling and full throttle. */
    val drivingCommentary = boolPref("driving_commentary", true)
    /** KITT grades each drive when the ignition goes off. */
    val reportCards = boolPref("report_cards", true)
    /** Graded drives (ReportCards format). */
    val reportCardLog = stringPref("report_card_log", "")
    /** Speak up when fuel runs low and offer the nearest gas station. */
    val fuelAlerts = boolPref("fuel_alerts", true)
    /** Look up the road's speed limit (OpenStreetMap) and show it on the map. */
    val speedLimits = boolPref("speed_limits", true)
    /** Warn when over the limit by more than [speedAlertMargin]; off by default. */
    val speedAlerts = boolPref("speed_alerts", false)
    /** Margin in the driver's units (mph or km/h). */
    val speedAlertMargin = intPref("speed_alert_margin", 7)
    /** Where the vehicle was last switched off (ParkingSpot format). */
    val parkingSpot = stringPref("parking_spot", "")
    /** Original KITT-style sound effects (scanner whoosh, turbo boost). */
    val soundEffects = boolPref("sound_effects", false)
    /** Theme for night time ("" = keep the day theme), e.g. Stealth or Night Vision. */
    val nightTheme = stringPref("night_theme", "")
    /** The stream window keeps playing in a corner mini player when you leave the Drive page. */
    val miniPlayer = boolPref("mini_player", true)
    /** Weekly backup into Downloads/KITT. */
    val autoBackup = boolPref("auto_backup", true)
    val lastAutoBackup = longPref("last_auto_backup", 0L)
    /** Release-notes level last shown (see ReleaseNotes). */
    val notesSeen = intPref("notes_seen", 0)
    /** Nav app for "Maps + KITT" (package name; "" = first one installed). */
    val sidePanelNavApp = stringPref("side_panel_nav_app", "")

    // --- OBD ---
    val adapterType = stringPref("obd_adapter_type", "")
    val adapterAddress = stringPref("obd_adapter_address", "")
    val adapterName = stringPref("obd_adapter_name", "")
    /** Every adapter used so far (AdapterProfiles format), most recent first. */
    val adapterList = stringPref("obd_adapter_list", "")

    // --- Vehicle ---
    /** NHTSA decode of the VIN, cached (VehicleIdentity fields, one per line). */
    val vehicleIdentity = stringPref("vehicle_identity", "")
    /** Driver-entered odometer (km) and the integrated distance counter at that moment. */
    val odometerBaseKm = stringPref("odometer_base_km", "")
    val distanceCounterKm = stringPref("distance_counter_km", "0")
    val maintenanceItems = stringPref("maintenance_items", "")
    /** Price per litre (metric) or per gallon (imperial), as entered. */
    val fuelPrice = stringPref("fuel_price", "")
    /** Tank size in litres (metric) or gallons (imperial), as entered. */
    val tankSize = stringPref("tank_size", "")
    val obdAutoConnect = boolPref("obd_auto_connect", true)
    val obdProtocol = stringPref("obd_protocol", "0")
    val obdFastPolling = boolPref("obd_fast_polling", true)
    val manufacturerProfile = stringPref("obd_manufacturer", "auto")
    val manufacturerPids = boolPref("obd_manufacturer_pids", true)

    // --- Secrets (decrypted once, then held in memory) ---
    private val _geminiKey = MutableStateFlow(secure.get(GEMINI_KEY).orEmpty())
    private val _perplexityKey = MutableStateFlow(secure.get(PERPLEXITY_KEY).orEmpty())

    /** Key saved in the app, falling back to a developer key from local.properties. */
    val geminiKey: String get() = _geminiKey.value.ifEmpty { BuildConfig.GEMINI_API_KEY }
    val perplexityKey: String get() = _perplexityKey.value
    val geminiKeySaved: StateFlow<String> = _geminiKey.asStateFlow()
    val perplexityKeySaved: StateFlow<String> = _perplexityKey.asStateFlow()

    fun setGeminiKey(key: String) = saveSecret(GEMINI_KEY, key.trim(), _geminiKey)
    fun setPerplexityKey(key: String) = saveSecret(PERPLEXITY_KEY, key.trim(), _perplexityKey)

    private fun saveSecret(name: String, value: String, flow: MutableStateFlow<String>) {
        flow.value = value
        // Keystore crypto can take a few ms: keep it off the main thread.
        scope.launch(Dispatchers.IO) { secure.put(name, value) }
    }

    companion object {
        /** Public downloads-only repository the release workflow publishes signed APKs to. */
        const val DEFAULT_UPDATE_REPO = "tdater/KITT-Launcher-releases"
        const val DEFAULT_CHAT_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_DIAGNOSTIC_MODEL = "gemini-3.8-flash"
        /** The model behind Perplexity's "fast" preset. */
        const val DEFAULT_PERPLEXITY_LOCAL_MODEL = "openai/gpt-6-luna"
        private const val GEMINI_KEY = "gemini_api_key"
        private const val PERPLEXITY_KEY = "perplexity_api_key"
    }
}
