package com.aiconsole.knightriderlauncher

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.aiconsole.knightriderlauncher.assistant.AlertAction
import com.aiconsole.knightriderlauncher.assistant.AssistantPrompt
import com.aiconsole.knightriderlauncher.assistant.KittAlert
import com.aiconsole.knightriderlauncher.assistant.LauncherBus
import com.aiconsole.knightriderlauncher.assistant.LauncherEvent
import com.aiconsole.knightriderlauncher.assistant.MicGuard
import com.aiconsole.knightriderlauncher.assistant.SavageLines
import com.aiconsole.knightriderlauncher.browser.BrowserSession
import com.aiconsole.knightriderlauncher.browser.SiteHomeRules
import com.aiconsole.knightriderlauncher.browser.WebMedia
import com.aiconsole.knightriderlauncher.data.AppLists
import com.aiconsole.knightriderlauncher.data.AppUsage
import com.aiconsole.knightriderlauncher.data.AutoBackup
import com.aiconsole.knightriderlauncher.data.HomeScreen
import com.aiconsole.knightriderlauncher.data.ReleaseNote
import com.aiconsole.knightriderlauncher.data.ReleaseNotes
import com.aiconsole.knightriderlauncher.data.DeviceProfile
import com.aiconsole.knightriderlauncher.data.EdgeSwipe
import com.aiconsole.knightriderlauncher.data.HardwareKeys
import com.aiconsole.knightriderlauncher.data.InstalledApps
import com.aiconsole.knightriderlauncher.data.KeyAction
import com.aiconsole.knightriderlauncher.data.KeyBindings
import com.aiconsole.knightriderlauncher.data.LauncherApp
import com.aiconsole.knightriderlauncher.nav.NavBridge
import com.aiconsole.knightriderlauncher.nav.NavInstruction
import com.aiconsole.knightriderlauncher.nav.SpeedLimits
import com.aiconsole.knightriderlauncher.obd.AdapterDiscovery
import com.aiconsole.knightriderlauncher.obd.AdapterType
import com.aiconsole.knightriderlauncher.obd.DrivingRules
import com.aiconsole.knightriderlauncher.obd.ObdConnectionService
import com.aiconsole.knightriderlauncher.obd.DemoSession
import com.aiconsole.knightriderlauncher.obd.ObdState
import com.aiconsole.knightriderlauncher.obd.PidCatalog
import com.aiconsole.knightriderlauncher.obd.PidDefinition
import com.aiconsole.knightriderlauncher.obd.PidReading
import com.aiconsole.knightriderlauncher.ui.assistant.AssistantOverlay
import com.aiconsole.knightriderlauncher.ui.browser.LocalWebViewHostActive
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.SoundFx
import com.aiconsole.knightriderlauncher.ui.components.VoiceModulator
import com.aiconsole.knightriderlauncher.ui.diagnostics.DiagnosticsScreen
import com.aiconsole.knightriderlauncher.ui.gauges.GaugeUi
import com.aiconsole.knightriderlauncher.ui.panes.AiPane
import com.aiconsole.knightriderlauncher.ui.panes.AppDrawerPane
import com.aiconsole.knightriderlauncher.ui.panes.ControlDock
import com.aiconsole.knightriderlauncher.ui.panes.DockBar
import com.aiconsole.knightriderlauncher.ui.panes.DrivingPanel
import com.aiconsole.knightriderlauncher.ui.panes.GamesPane
import com.aiconsole.knightriderlauncher.ui.panes.GaugeWallPane
import com.aiconsole.knightriderlauncher.ui.panes.HudState
import com.aiconsole.knightriderlauncher.ui.panes.MediaPane
import com.aiconsole.knightriderlauncher.ui.panes.NavRail
import com.aiconsole.knightriderlauncher.ui.panes.NavigationPane
import com.aiconsole.knightriderlauncher.ui.panes.PursuitScreen
import com.aiconsole.knightriderlauncher.ui.panes.QuickSettingsPanel
import com.aiconsole.knightriderlauncher.ui.panes.Screensaver
import com.aiconsole.knightriderlauncher.ui.panes.StatusGridPane
import com.aiconsole.knightriderlauncher.ui.panes.StatusStrip
import com.aiconsole.knightriderlauncher.ui.panes.StreamWindow
import com.aiconsole.knightriderlauncher.ui.panes.WidgetsPane
import com.aiconsole.knightriderlauncher.ui.scanner.KittScanner
import com.aiconsole.knightriderlauncher.ui.scanner.LauncherVisibility
import com.aiconsole.knightriderlauncher.ui.scanner.ScannerEngine
import com.aiconsole.knightriderlauncher.ui.scanner.ScannerOverlayService
import com.aiconsole.knightriderlauncher.ui.settings.KittConfigScreen
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.FilmAlertHeader
import com.aiconsole.knightriderlauncher.ui.theme.FilmEdition
import com.aiconsole.knightriderlauncher.ui.theme.FilmHudOverlay
import com.aiconsole.knightriderlauncher.ui.theme.HudStyle
import com.aiconsole.knightriderlauncher.ui.theme.ThemeIntro
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.KnightRiderLauncherTheme
import com.aiconsole.knightriderlauncher.ui.theme.LocalLiteMode
import com.aiconsole.knightriderlauncher.ui.theme.MatrixRain
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.theme.ThemeState
import com.aiconsole.knightriderlauncher.ui.theme.UiTheme
import com.aiconsole.knightriderlauncher.ui.theme.hud
import com.aiconsole.knightriderlauncher.ui.trip.TripScreen
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf<List<LauncherApp>>(emptyList())

    /** Bumped by the steering-wheel voice button (VOICE_COMMAND / ASSIST intents). */
    private var voiceTrigger by mutableIntStateOf(0)

    /** Bumped when Home is pressed while the launcher is already showing: back to the Drive page. */
    private var homeTrigger by mutableIntStateOf(0)

    private val kitt: KittApplication get() = application as KittApplication

    /** Release notes to show once after an update. */
    private var whatsNew by mutableStateOf<List<ReleaseNote>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        handleVoiceIntent(intent)
        DemoSession.startIfRequested(kitt, intent?.getBooleanExtra(DemoSession.EXTRA, false) == true)
        // Re-render the app icons when the driver picks another icon pack.
        lifecycleScope.launch {
            kitt.settings.iconPack.state.collect { pack -> apps = InstalledApps.load(applicationContext, pack) }
        }
        setContent {
            KnightRiderLauncherTheme {
                // This is the home screen: there is nothing to go back to.
                BackHandler {}
                val settings = kitt.settings
                val showMap by settings.showMap.state.collectAsState()
                val navNotifications by settings.navNotifications.state.collectAsState()
                val navInstruction by NavBridge.current.collectAsState()
                val rain by settings.matrixRain.state.collectAsState()
                val night by kitt.nightMode.night.collectAsState()
                val pinnedStored by settings.pinnedApps.state.collectAsState()
                val hiddenStored by settings.hiddenApps.state.collectAsState()
                val statusStrip by settings.statusStrip.state.collectAsState()
                val screensaverMinutes by settings.screensaverMinutes.state.collectAsState()
                val drivingLayout by settings.drivingMode.state.collectAsState()
                val uiScale by settings.uiScale.state.collectAsState()
                val lite by kitt.lite.collectAsState()
                val obdMoving by kitt.obd.moving.collectAsState()
                val gpsFix by kitt.gps.fix.collectAsState()
                val pinned = remember(pinnedStored) { AppLists.decode(pinnedStored) }
                val homeApps = remember(apps, pinned, hiddenStored) {
                    AppLists.homeSelection(apps, LauncherApp::key, pinned, AppLists.decode(hiddenStored).toSet())
                }
                // Fit 7"–10" screens: draw the whole launcher a little smaller or larger than the
                // head unit's density says (Auto picks from the screen size).
                val configuration = LocalConfiguration.current
                val baseDensity = LocalDensity.current
                val scale = DeviceProfile.scale(uiScale, configuration.screenWidthDp, configuration.screenHeightDp)
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density * scale, baseDensity.fontScale),
                    LocalLiteMode provides lite
                ) {
                LauncherRoot(
                    apps = apps,
                    homeApps = homeApps,
                    favorites = pinned.isNotEmpty(),
                    homeTrigger = homeTrigger,
                    statusStrip = statusStrip,
                    screensaverMinutes = screensaverMinutes,
                    drivingLayout = drivingLayout,
                    moving = obdMoving || (gpsFix?.speedKmh ?: 0.0) > DrivingRules.MOVING_ABOVE_KMH,
                    onAppClick = {
                        settings.appUsage.set(AppUsage.encode(AppUsage.record(AppUsage.decode(settings.appUsage.value), it.key)))
                        InstalledApps.launch(this, it)
                    },
                    dash = rememberDashState(kitt),
                    voiceTrigger = voiceTrigger,
                    onAskKitt = { kitt.assistantInbox.value = AssistantPrompt(it, diagnostic = true) },
                    onDismissAlert = { kitt.alerts.dismiss() },
                    showMap = showMap,
                    nav = if (navNotifications) navInstruction else null,
                    // The Matrix rain is pure eye candy: off in lite mode.
                    rain = rain && !lite && ThemeState.theme == UiTheme.Colorado,
                    night = night,
                    whatsNew = whatsNew,
                    onWhatsNewSeen = {
                        whatsNew = emptyList()
                        settings.notesSeen.set(ReleaseNotes.latest)
                    }
                )
                }
            }
        }
        // A playing web page swiped off screen is parked here, attached but out of sight, so its
        // sound keeps going (see WebMedia).
        val parking = FrameLayout(this)
        findViewById<ViewGroup>(android.R.id.content).addView(parking, 0, FrameLayout.LayoutParams(1, 1))
        WebMedia.parking = parking
        addMiniPlayer()
    }

    /**
     * The stream window's corner mini player: a playing video moves here when its page leaves
     * the screen (see WebMedia). Tap it to go back to the stream window; ✕ pauses and closes.
     */
    private fun addMiniPlayer() {
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val mini = FrameLayout(this).apply {
            visibility = android.view.View.GONE
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.BLACK)
                setStroke(px(2), android.graphics.Color.rgb(255, 42, 26))
            }
            setPadding(px(2), px(2), px(2), px(2))
            elevation = 8 * dp
        }
        mini.addView(
            android.view.View(this).apply { setOnClickListener { LauncherBus.send(LauncherEvent.ShowStream) } },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        mini.addView(
            android.widget.TextView(this).apply {
                text = "✕"
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.argb(200, 0, 0, 0))
                setOnClickListener { WebMedia.closeMini() }
            },
            FrameLayout.LayoutParams(px(44), px(44), android.view.Gravity.TOP or android.view.Gravity.END)
        )
        findViewById<ViewGroup>(android.R.id.content).addView(
            mini,
            FrameLayout.LayoutParams(px(320), px(180), android.view.Gravity.BOTTOM or android.view.Gravity.END).apply {
                setMargins(0, 0, px(16), px(96))
            }
        )
        WebMedia.mini = mini
    }

    override fun onDestroy() {
        if (WebMedia.parking?.context === this) WebMedia.parking = null
        if (WebMedia.mini?.context === this) WebMedia.mini = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceIntent(intent)
        DemoSession.startIfRequested(kitt, intent?.getBooleanExtra(DemoSession.EXTRA, false) == true)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) homeTrigger++
    }

    override fun onStart() {
        super.onStart()
        LauncherVisibility.visible.value = true
        kitt.nightMode.setActive(true)
        // Re-check media sessions: notification access may have just been granted in Settings.
        kitt.media.start()
        kitt.gps.start()
        // Some ROMs refuse widget hosting; the Widgets page then just shows nothing.
        runCatching { kitt.widgetHost.startListening() }
        kitt.startup.onLauncherShown()
        kitt.wakeWord.setVisible(true)
        kitt.weather.start()
        kitt.speedLimits.start()
        WebMedia.setLauncherVisible(true)
        startVehicleSystems()
    }

    override fun onStop() {
        MicGuard.launcherHidden(this)
        LauncherVisibility.visible.value = false
        kitt.nightMode.setActive(false)
        kitt.gps.stop()
        kitt.wakeWord.setVisible(false)
        kitt.weather.stop()
        kitt.speedLimits.stop()
        WebMedia.setLauncherVisible(false)
        runCatching { kitt.widgetHost.stopListening() }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Refresh on every return to home so installs/uninstalls show up (icons are cached).
        lifecycleScope.launch { apps = InstalledApps.load(applicationContext, kitt.settings.iconPack.value) }
    }

    /** Mapped steering-wheel buttons (and button learning) while the launcher is in front. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        HardwareKeys.consumesDown(keyCode, keyBindings()) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        HardwareKeys.onKeyUp(keyCode, keyBindings()) || super.onKeyUp(keyCode, event)

    private fun keyBindings() = KeyBindings.decode(kitt.settings.keyBindings.value)

    private var lastVoiceIntent = 0L

    /**
     * Steering-wheel voice button. Repeats within [VOICE_DEBOUNCE_MS] are ignored: on one head
     * unit the manufacturer's assistant re-fired this intent whenever it took over, looping.
     */
    private fun handleVoiceIntent(intent: Intent?) {
        if (intent?.action !in VOICE_ACTIONS) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastVoiceIntent < VOICE_DEBOUNCE_MS) return
        lastVoiceIntent = now
        voiceTrigger++
    }

    /** Services must be started while an activity is visible, so the launcher kicks them off. */
    private fun startVehicleSystems() {
        val settings = kitt.settings
        val adapter = kitt.savedAdapter()
        if (settings.obdAutoConnect.value && adapter != null && kitt.obd.state.value == ObdState.Idle &&
            (adapter.type == AdapterType.WIFI || AdapterDiscovery.hasPermissions(this))
        ) {
            ObdConnectionService.start(this)
        }
        if (settings.scannerOverlay.value && Settings.canDrawOverlays(this)) {
            ScannerOverlayService.start(this)
        }
        kitt.alerts.greetOnce()
        launchChecks()
        kitt.vehicle.reminder()?.let { kitt.alerts.remindMaintenance(it) }
    }

    /** Once per process: start-up whoosh, home-screen check, weekly backup and what's new. */
    private fun launchChecks() {
        if (checked) return
        checked = true
        ThemeState.theme.hud?.let(SoundFx::film) ?: SoundFx.scanner()
        if (!HomeScreen.isDefault(this)) kitt.alerts.notHomeScreen()
        val settings = kitt.settings
        val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val updated = info != null && info.lastUpdateTime > info.firstInstallTime + 60_000
        whatsNew = ReleaseNotes.since(settings.notesSeen.value, updated)
        if (whatsNew.isEmpty()) {
            settings.notesSeen.set(ReleaseNotes.latest)
        } else {
            kitt.kittVoice.speak(
                SavageLines.pick(
                    settings.persona.value,
                    "I've been upgraded. Here's what's new.",
                    arrayOf(
                        "I've been upgraded, no thanks to you. Here's the new shit.",
                        "New version installed. Try to keep up with what's new, dumbass."
                    ),
                    kotlin.random.Random.nextInt(0, Int.MAX_VALUE)
                ),
                interrupt = false
            )
        }
        if (settings.autoBackup.value) {
            val backup = AutoBackup(applicationContext, packageName.substringAfterLast('.'))
            lifecycleScope.launch(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                if (backup.runIfDue(now, settings.lastAutoBackup.value) != null) settings.lastAutoBackup.set(now)
            }
        }
    }

    private var checked = false

    private companion object {
        val VOICE_ACTIONS = setOf(Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST)
        const val VOICE_DEBOUNCE_MS = 20_000L
    }
}

enum class LauncherOverlay { None, Assistant, Diagnostics, Config, Trips, Pursuit }

/** Live vehicle state for the home screen. */
data class DashState(
    /** Read lazily so live OBD updates redraw only the HUD readouts, not the map. */
    val hud: () -> HudState = { HudState() },
    /** Scanner shift-light fill (see [ScannerEngine.shiftLevel]); read in the draw pass. */
    val shift: () -> Float? = { null },
    /** Gauge configuration only; live values come from [reading] so updates redraw just the tiles. */
    val gauges: List<GaugeUi> = emptyList(),
    val reading: (String) -> PidReading? = { null },
    val metric: Boolean = false,
    val alert: KittAlert? = null,
    val kittSpeaking: Boolean = false,
    val definition: (String) -> PidDefinition? = { null }
)

@Composable
private fun rememberDashState(app: KittApplication): DashState {
    val state = app.obd.state.collectAsState()
    val readings = app.obd.readings.collectAsState()
    val defs = app.obd.definitions.collectAsState()
    val gaugeConfigs by app.gauges.gauges.collectAsState()
    val metric = app.settings.metricUnits.state.collectAsState()
    val alert by app.alerts.active.collectAsState()
    val speaking by app.kittVoice.speaking.collectAsState()
    val hz = app.obd.refreshHz.collectAsState()
    val shiftOn = app.settings.shiftLight.state.collectAsState()
    val shiftRpm = app.settings.shiftRpm.state.collectAsState()
    val gps = app.gps.fix.collectAsState()
    val limitKmh = app.speedLimits.limitKmh.collectAsState()
    val limitsOn = app.settings.speedLimits.state.collectAsState()
    val margin = app.settings.speedAlertMargin.state.collectAsState()

    val hudState = remember {
        derivedStateOf {
            val isMetric = metric.value
            val speedDef = defs.value[PidCatalog.SPEED]
            val speed = readings.value[PidCatalog.SPEED]?.value
            val rpm = readings.value[PidCatalog.RPM]?.value
            val link = state.value
            // No OBD speed: fall back to GPS (fresh fixes only).
            val gpsKmh = if (speed == null) gps.value?.takeIf { System.currentTimeMillis() - it.timestamp < 4_000 }?.speedKmh else null
            val unit = if (isMetric) "KM/H" else "MPH"
            HudState(
                speed = when {
                    speed != null && speedDef != null -> speedDef.displayValue(speed, isMetric).roundToInt().toString()
                    gpsKmh != null -> (if (isMetric) gpsKmh else gpsKmh * 0.621371).roundToInt().toString()
                    else -> null
                },
                speedUnit = if (gpsKmh != null) "$unit · GPS" else unit,
                rpmFraction = ((rpm ?: 0.0) / 7000.0).toFloat().coerceIn(0f, 1f),
                rpmText = rpm?.roundToInt()?.toString(),
                linkLabel = when (link) {
                    is ObdState.Connected -> "OBD LINK · ${"%.1f".format(Locale.US, hz.value)} HZ"
                    is ObdState.Connecting -> "LINKING"
                    is ObdState.Error -> "LINK ERROR"
                    ObdState.Idle -> "OBD OFFLINE"
                },
                linked = link is ObdState.Connected,
                speedLimit = limitKmh.value?.takeIf { limitsOn.value }?.let { SpeedLimits.display(it, isMetric) },
                overLimit = limitKmh.value?.takeIf { limitsOn.value }?.let { limit ->
                    val kmh = speed ?: gpsKmh
                    kmh != null && SpeedLimits.isOver(kmh, limit, margin.value, isMetric)
                } ?: false
            )
        }
    }
    val hud = remember(hudState) { { hudState.value } }
    val reading = remember(readings) { { pidId: String -> readings.value[pidId] } }
    val shift = remember(readings) {
        {
            if (shiftOn.value && state.value is ObdState.Connected) {
                ScannerEngine.shiftLevel(readings.value[PidCatalog.RPM]?.value, shiftRpm.value)
            } else {
                null
            }
        }
    }
    return DashState(
        hud = hud,
        shift = shift,
        gauges = gaugeConfigs.map { GaugeUi(it, defs.value[it.pidId], null) },
        reading = reading,
        metric = metric.value,
        alert = alert,
        kittSpeaking = speaking,
        definition = { id -> defs.value[id] }
    )
}

/**
 * Responsive shell under a permanent KITT scanner strip.
 *
 * Landscape (7"–10" head units and other wide screens): a side rail with KITT and the
 * page buttons, the pages, and a slim one-row dock, so the pages get nearly the full height.
 * Tall (portrait head units, e.g. 9:16): stacked 50% map / 35% grid / 15% dock.
 * Full-screen features are overlays drawn below the scanner, so it never disappears.
 */
@Composable
fun LauncherRoot(
    apps: List<LauncherApp>,
    onAppClick: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    /** Home grid apps: pinned favourites, or everything not hidden. */
    homeApps: List<LauncherApp> = apps,
    favorites: Boolean = false,
    homeTrigger: Int = 0,
    statusStrip: Boolean = false,
    screensaverMinutes: Int = 0,
    drivingLayout: Boolean = false,
    moving: Boolean = false,
    dash: DashState = DashState(),
    voiceTrigger: Int = 0,
    onAskKitt: (String) -> Unit = {},
    onDismissAlert: () -> Unit = {},
    showMap: Boolean = false,
    nav: NavInstruction? = null,
    rain: Boolean = false,
    night: Boolean = false,
    whatsNew: List<ReleaseNote> = emptyList(),
    onWhatsNewSeen: () -> Unit = {}
) {
    var overlay by rememberSaveable { mutableStateOf(LauncherOverlay.None) }
    var autoListen by remember { mutableStateOf(false) }
    val pager = rememberPagerState { HomePage.entries.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var lastTouch by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var saver by remember { mutableStateOf(false) }
    var quickSettings by remember { mutableStateOf(false) }
    // Applies on the Drive page only; the launcher never changes page by itself while driving.
    // Debounced: GPS/OBD "moving" flips at every slow-down, which made the panel swap back and
    // forth. Switch in only after steady driving, and back only after a real stop.
    var steadyDriving by remember { mutableStateOf(false) }
    LaunchedEffect(moving) {
        if (moving && !steadyDriving) {
            delay(DRIVING_ENTER_MS)
            steadyDriving = true
        } else if (!moving && steadyDriving) {
            delay(DRIVING_EXIT_MS)
            steadyDriving = false
        }
    }
    val driving = drivingLayout && steadyDriving
    LaunchedEffect(screensaverMinutes, moving) {
        if (moving) saver = false
        while (screensaverMinutes > 0 && !moving) {
            delay(5_000)
            val idle = SystemClock.elapsedRealtime() - lastTouch
            if (idle >= screensaverMinutes * 60_000L && overlay == LauncherOverlay.None) saver = true
        }
    }
    val stream = remember {
        val app = context.applicationContext as KittApplication
        BrowserSession(
            "stream", app.settings, app.userscripts, lite = { app.lite.value }, holdPopups = true,
            onVisit = { title, url -> app.settings.streamVisits.set(SiteHomeRules.encodeVisits(SiteHomeRules.record(SiteHomeRules.decodeVisits(app.settings.streamVisits.value), title, url, System.currentTimeMillis()))) }
        )
    }
    // AI and Games keep their tabs (and sign-ins) while you move between pages.
    val aiBrowser = remember {
        val app = context.applicationContext as KittApplication
        BrowserSession("ai", app.settings, app.userscripts, lite = { app.lite.value })
    }
    val gamesBrowser = remember {
        val app = context.applicationContext as KittApplication
        BrowserSession(
            "games", app.settings, app.userscripts, gamepad = true, lite = { app.lite.value }, holdPopups = true,
            onVisit = { title, url -> app.settings.gameVisits.set(SiteHomeRules.encodeVisits(SiteHomeRules.record(SiteHomeRules.decodeVisits(app.settings.gameVisits.value), title, url, System.currentTimeMillis()))) }
        )
    }
    DisposableEffect(aiBrowser, gamesBrowser) {
        onDispose {
            aiBrowser.close()
            gamesBrowser.close()
        }
    }
    DisposableEffect(stream) { onDispose { stream.close() } }
    // Back works the same in every browser: leave full screen, then step back through its pages.
    // On Drive the stream window then returns to the map.
    val pageBrowser = when (HomePage.entries[pager.currentPage]) {
        HomePage.Drive -> stream.takeIf { it.visible }
        HomePage.Ai -> aiBrowser
        HomePage.Games -> gamesBrowser
        else -> null
    }
    BackHandler(enabled = pageBrowser != null && overlay == LauncherOverlay.None) {
        val browser = pageBrowser ?: return@BackHandler
        when {
            browser.fullscreenView != null -> browser.exitFullscreen()
            browser.canGoBack() -> browser.goBack()
            browser.immersive -> browser.immersive = false
            browser === stream -> stream.visible = false
        }
    }
    // Game mode (Games page) and theater (stream window) hide the rail, dock and status strip.
    val gameMode = gamesBrowser.immersive && pager.currentPage == HomePage.Games.ordinal
    val theater = stream.immersive && stream.visible && pager.currentPage == HomePage.Drive.ordinal
    val chromeless = (gameMode || theater) && overlay == LauncherOverlay.None
    LaunchedEffect(homeTrigger) {
        if (homeTrigger > 0) {
            gamesBrowser.immersive = false
            stream.immersive = false
            overlay = LauncherOverlay.None
            pager.animateScrollToPage(HomePage.Drive.ordinal)
        }
    }
    val kittApp = LocalContext.current.applicationContext as? KittApplication
    val mediaControl = kittApp?.media
    // "Hey KITT" pauses while an overlay is up (the assistant needs the microphone).
    LaunchedEffect(overlay) { kittApp?.wakeWord?.setSuspended(overlay != LauncherOverlay.None) }
    LaunchedEffect(Unit) {
        kittApp?.wakeWord?.wakes?.collect {
            saver = false
            autoListen = true
            overlay = LauncherOverlay.Assistant
        }
    }
    // Voice commands that change the home screen.
    LaunchedEffect(Unit) {
        LauncherBus.events.collect { event ->
            saver = false
            when (event) {
                is LauncherEvent.ShowPage -> {
                    overlay = LauncherOverlay.None
                    HomePage.entries.firstOrNull { it.name.equals(event.page, ignoreCase = true) }
                        ?.let { pager.animateScrollToPage(it.ordinal) }
                }
                is LauncherEvent.OpenStream -> {
                    overlay = LauncherOverlay.None
                    pager.animateScrollToPage(HomePage.Drive.ordinal)
                    stream.open(event.url, event.label)
                }
                LauncherEvent.CloseStream -> stream.visible = false
                LauncherEvent.OpenDiagnostics -> overlay = LauncherOverlay.Diagnostics
                LauncherEvent.OpenPursuit -> overlay = LauncherOverlay.Pursuit
                LauncherEvent.OpenTrips -> overlay = LauncherOverlay.Trips
                LauncherEvent.ShowStream -> {
                    overlay = LauncherOverlay.None
                    pager.animateScrollToPage(HomePage.Drive.ordinal)
                    stream.visible = true
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        HardwareKeys.actions.collect { action ->
            saver = false
            when (action) {
                KeyAction.OpenKitt -> {
                    autoListen = true
                    overlay = LauncherOverlay.Assistant
                }
                KeyAction.NextPage -> pager.animateScrollToPage((pager.currentPage + 1) % HomePage.entries.size)
                KeyAction.PreviousPage -> pager.animateScrollToPage((pager.currentPage - 1 + HomePage.entries.size) % HomePage.entries.size)
                KeyAction.DrivePage -> pager.animateScrollToPage(HomePage.Drive.ordinal)
                KeyAction.ToggleStream -> {
                    pager.animateScrollToPage(HomePage.Drive.ordinal)
                    stream.visible = !stream.visible
                }
                KeyAction.PlayPause -> mediaControl?.playPause()
                KeyAction.Diagnostics -> overlay = LauncherOverlay.Diagnostics
                KeyAction.Pursuit -> overlay = LauncherOverlay.Pursuit
            }
        }
    }
    LaunchedEffect(voiceTrigger) {
        if (voiceTrigger > 0) {
            // After a mic takeover, open KITT without listening so the loop can't restart.
            autoListen = !MicGuard.autoListenBlocked()
            overlay = LauncherOverlay.Assistant
        }
    }

    val askKitt = { prompt: String ->
        onAskKitt(prompt)
        autoListen = false
        overlay = LauncherOverlay.Assistant
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Any touch counts as activity for the screensaver; nothing is consumed.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch = SystemClock.elapsedRealtime()
                    }
                }
            }
            // Night: dim everything, scanner included, without blocking touches.
            .drawWithContent {
                drawContent()
                if (night) drawRect(Color.Black, alpha = NightDim)
            }
    ) {
        // The Matrix: digital rain behind the translucent panels.
        if (rain) MatrixRain(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val configuration = LocalConfiguration.current
            val scannerHeight = DeviceProfile.scannerHeightDp(configuration.screenHeightDp.toFloat()).dp
            KittScanner(Modifier.fillMaxWidth().height(scannerHeight), alert = dash.alert?.severe == true, shift = dash.shift)
            if (statusStrip && !chromeless) StatusStrip(onClick = { quickSettings = !quickSettings })

            // From the screen's shape, not the space left: the on-screen keyboard shrinks
            // maxHeight, and flipping the layout then would rebuild the map and drop its search.
            val isTall = configuration.screenHeightDp >= configuration.screenWidthDp * 1.2f
            val rail = DeviceProfile.useRail(configuration.screenWidthDp.toFloat(), configuration.screenHeightDp.toFloat())
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(if (chromeless) 0.dp else if (rail) 6.dp else 8.dp)
            ) {
                val gap = if (rail) 6.dp else 8.dp
                val dockHeight = (maxHeight * if (isTall) 0.15f else 0.20f).coerceIn(112.dp, 280.dp)
                val dockBarHeight = (maxHeight * 0.11f).coerceIn(48.dp, 64.dp)
                val portraitCellWidth = if (maxWidth >= 600.dp) 150.dp else 110.dp
                val open = { target: LauncherOverlay -> overlay = target }
                val onDrive = pager.currentPage == HomePage.Drive.ordinal
                // The map stops tracking and redrawing while it's covered or paged away.
                val mapActive = overlay == LauncherOverlay.None && onDrive && !stream.visible

                val dock = @Composable {
                    ControlDock(
                        onAssistant = {
                            autoListen = false
                            open(LauncherOverlay.Assistant)
                        },
                        onDiagnostics = { open(LauncherOverlay.Diagnostics) },
                        onConfig = { open(LauncherOverlay.Config) },
                        modifier = Modifier.fillMaxWidth().height(dockHeight),
                        kittSpeaking = dash.kittSpeaking,
                        onTrip = { open(LauncherOverlay.Trips) },
                        metric = dash.metric
                    )
                }
                val grid = @Composable { gridModifier: Modifier, cellWidth: androidx.compose.ui.unit.Dp ->
                    StatusGridPane(
                        apps = homeApps,
                        appsTitle = if (favorites) "Favorites" else "Applications",
                        onAppClick = onAppClick,
                        modifier = gridModifier,
                        minCellWidth = cellWidth,
                        gauges = dash.gauges,
                        metric = dash.metric,
                        onGaugeClick = { open(LauncherOverlay.Diagnostics) },
                        reading = dash.reading
                    )
                }
                val drivingPanel = @Composable { panelModifier: Modifier ->
                    DrivingPanel(
                        favorites = homeApps,
                        onAppClick = onAppClick,
                        onKitt = {
                            autoListen = true
                            open(LauncherOverlay.Assistant)
                        },
                        kittSpeaking = dash.kittSpeaking,
                        modifier = panelModifier
                    )
                }
                val navPane = @Composable { paneModifier: Modifier ->
                    Box(paneModifier) {
                        NavigationPane(
                            Modifier.fillMaxSize(),
                            dash.hud,
                            showMap = showMap,
                            mapActive = mapActive,
                            nav = nav,
                            onStream = { stream.visible = true }
                        )
                        // The streaming window takes the map's place; the rest of home stays put.
                        if (stream.visible) StreamWindow(stream, Modifier.fillMaxSize())
                    }
                }

                val pages = @Composable { pagesModifier: Modifier ->
                    HorizontalPager(
                        state = pager,
                        modifier = pagesModifier.edgeSwipe(pager, scope),
                        // Swipe anywhere outside the map and web pages (they keep their own drags) to change page.
                        // In game mode every swipe belongs to the game.
                        userScrollEnabled = !chromeless,
                        pageSpacing = gap
                    ) { page ->
                        // Web pages attach only on the page the pager settled on, never while pre-composed.
                        CompositionLocalProvider(LocalWebViewHostActive provides (page == pager.settledPage)) {
                            when (HomePage.entries[page]) {
                                HomePage.Drive -> if (isTall) {
                                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                                        // 50 : 35 of the space above the dock keeps the 50/35/15 split
                                        navPane(Modifier.fillMaxWidth().weight(0.50f))
                                        if (driving) drivingPanel(Modifier.fillMaxWidth().weight(0.35f))
                                        else grid(Modifier.fillMaxWidth().weight(0.35f), portraitCellWidth)
                                    }
                                } else {
                                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                        navPane(Modifier.fillMaxHeight().weight(0.60f))
                                        if (driving) drivingPanel(Modifier.fillMaxHeight().weight(0.40f))
                                        else grid(Modifier.fillMaxHeight().weight(0.40f), if (rail) 100.dp else 120.dp)
                                    }
                                }
                                HomePage.Gauges -> GaugeWallPane(
                                    onPursuit = { open(LauncherOverlay.Pursuit) },
                                    gauges = dash.gauges,
                                    metric = dash.metric,
                                    reading = dash.reading,
                                    onGaugeClick = { open(LauncherOverlay.Diagnostics) },
                                    modifier = Modifier.fillMaxSize()
                                )
                                HomePage.Media -> MediaPane(Modifier.fillMaxSize())
                                HomePage.Widgets -> WidgetsPane(Modifier.fillMaxSize())
                                HomePage.Ai -> AiPane(aiBrowser, Modifier.fillMaxSize())
                                HomePage.Games -> GamesPane(gamesBrowser, Modifier.fillMaxSize())
                                HomePage.Apps -> AppDrawerPane(apps, onAppClick, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                val selectPage = { page: Int -> scope.launch { pager.animateScrollToPage(page) }; Unit }

                if (theater) {
                    // Theater: the stream window takes the whole screen below the scanner.
                    StreamWindow(stream, Modifier.fillMaxSize())
                } else if (chromeless) {
                    pages(Modifier.fillMaxSize())
                } else if (rail) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        NavRail(
                            labels = HomePage.entries.map { it.label },
                            current = pager.currentPage,
                            onSelect = selectPage,
                            onKitt = {
                                autoListen = false
                                open(LauncherOverlay.Assistant)
                            },
                            kittSpeaking = dash.kittSpeaking,
                            modifier = Modifier.fillMaxHeight().width(RailWidth)
                        )
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                            pages(Modifier.fillMaxWidth().weight(1f))
                            DockBar(
                                onDiagnostics = { open(LauncherOverlay.Diagnostics) },
                                onConfig = { open(LauncherOverlay.Config) },
                                onTrip = { open(LauncherOverlay.Trips) },
                                metric = dash.metric,
                                modifier = Modifier.fillMaxWidth().height(dockBarHeight)
                            )
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        pages(Modifier.fillMaxWidth().weight(1f))
                        PageTabs(pager.currentPage, selectPage)
                        dock()
                    }
                }

                dash.alert?.let { alert ->
                    AlertBanner(alert, onDismissAlert, Modifier.align(Alignment.TopCenter).fillMaxWidth()) { action ->
                        onDismissAlert()
                        when (action) {
                            AlertAction.NearestFuel -> {
                                scope.launch { pager.animateScrollToPage(HomePage.Drive.ordinal) }
                                kittApp?.let { k -> scope.launch { k.kittVoice.speak(k.navigator.navigateToNearestFuel(), interrupt = true) } }
                            }
                            AlertAction.OpenTrips -> open(LauncherOverlay.Trips)
                            AlertAction.HomeSettings -> HomeScreen.openSettings(context)
                        }
                    }
                }
                if (quickSettings) {
                    BackHandler { quickSettings = false }
                    QuickSettingsPanel(
                        onClose = { quickSettings = false },
                        onPursuit = {
                            quickSettings = false
                            open(LauncherOverlay.Pursuit)
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                val close = { overlay = LauncherOverlay.None }
                when (overlay) {
                    LauncherOverlay.None -> Unit
                    LauncherOverlay.Assistant -> AssistantOverlay(
                        onDismiss = close,
                        modifier = Modifier.fillMaxSize(),
                        autoListen = autoListen
                    )
                    LauncherOverlay.Diagnostics -> DiagnosticsScreen(
                        onDismiss = close,
                        onAskKitt = askKitt,
                        modifier = Modifier.fillMaxSize()
                    )
                    LauncherOverlay.Config -> KittConfigScreen(onDismiss = close, modifier = Modifier.fillMaxSize())
                    LauncherOverlay.Trips -> TripScreen(
                        onDismiss = close,
                        onAskKitt = askKitt,
                        modifier = Modifier.fillMaxSize()
                    )
                    LauncherOverlay.Pursuit -> PursuitScreen(
                        hud = dash.hud,
                        reading = dash.reading,
                        definition = dash.definition,
                        metric = dash.metric,
                        kittSpeaking = dash.kittSpeaking,
                        onKitt = {
                            autoListen = true
                            open(LauncherOverlay.Assistant)
                        },
                        onDismiss = close,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (whatsNew.isNotEmpty() && overlay == LauncherOverlay.None) {
                    WhatsNewCard(whatsNew, onWhatsNewSeen, Modifier.align(Alignment.Center))
                }
            }
        }

        // Film HUD themes: scanlines and vignette / visor glow over everything (no touch handling).
        ThemeState.edition?.let { FilmHudOverlay(it, Modifier.fillMaxSize()) }

        // A film theme's boot sequence when it comes on (at start and on switching).
        val introsOn = (context.applicationContext as? KittApplication)?.settings?.filmIntros?.state?.collectAsState()?.value ?: false
        var intro by remember { mutableStateOf<FilmEdition?>(null) }
        val editionNow = ThemeState.edition
        var firstEdition by remember { mutableStateOf(true) }
        LaunchedEffect(editionNow) {
            intro = if (introsOn) editionNow else null
            // Switching film or edition plays its start-up sound (at launch, launchChecks does).
            if (!firstEdition) editionNow?.let { SoundFx.film(it.film) }
            firstEdition = false
        }
        intro?.let { edition -> ThemeIntro(edition, onDone = { intro = null }) }

        if (saver) {
            Screensaver(onWake = {
                saver = false
                lastTouch = SystemClock.elapsedRealtime()
            })
        }
    }
}

private val RailWidth = 96.dp

private const val DRIVING_ENTER_MS = 15_000L
private const val DRIVING_EXIT_MS = 60_000L

/** Home pages, swiped or picked from the tabs above the dock. */
private enum class HomePage(val label: String) {
    Drive("Drive"), Gauges("Gauges"), Media("Media"), Ai("AI"), Games("Games"), Widgets("Widgets"), Apps("Apps")
}

/** Page selector drawn as KITT console buttons: big targets, readable at a glance. */
/**
 * Drags that start in the strips along the left and right edges always change page, even over
 * web pages and the map (which otherwise keep horizontal drags for themselves). Watching in the
 * Initial pass sees the drag before the page does; taps pass straight through.
 */
private fun Modifier.edgeSwipe(pager: PagerState, scope: CoroutineScope, edge: Dp = 48.dp): Modifier =
    pointerInput(pager) {
        val edgePx = edge.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x > edgePx && down.position.x < size.width - edgePx) return@awaitEachGesture
            val startPage = pager.settledPage
            val tracker = VelocityTracker()
            var dragging = false
            var dragged = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!dragging) {
                    val moved = change.position - down.position
                    when {
                        abs(moved.x) > slop && abs(moved.x) > abs(moved.y) -> dragging = true
                        abs(moved.y) > slop -> return@awaitEachGesture // A vertical scroll: leave it to the page.
                    }
                }
                if (dragging) {
                    val dx = change.position.x - change.previousPosition.x
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    dragged += dx
                    pager.dispatchRawDelta(-dx)
                }
            }
            if (dragging) {
                val target = EdgeSwipe.target(startPage, pager.pageCount, dragged, tracker.calculateVelocity().x, size.width.toFloat())
                scope.launch { pager.animateScrollToPage(target) }
            }
        }
    }

@Composable
private fun PageTabs(current: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HomePage.entries.forEach { page ->
            KittButton(
                label = page.label,
                onClick = { onSelect(page.ordinal) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = ScannerRed,
                active = page.ordinal == current
            )
        }
    }
}

/** How much the night mode darkens the whole launcher. */
private const val NightDim = 0.45f

/** KITT speaking up: voice bars plus the message, tap to dismiss. */
@Composable
private fun AlertBanner(alert: KittAlert, onDismiss: () -> Unit, modifier: Modifier = Modifier, onAction: (AlertAction) -> Unit = {}) {
    val color = if (alert.severe) ScannerRed else DashAmber
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(KittBlack.copy(alpha = 0.94f))
            .border(2.dp, color, MaterialTheme.shapes.medium)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VoiceModulator(active = true, modifier = Modifier.width(72.dp).height(44.dp), color = color)
        val style = ThemeState.theme.hud
        // The Matrix types KITT's messages out like a terminal.
        var shown by remember(alert.at) { mutableIntStateOf(if (style == HudStyle.Matrix) 0 else alert.message.length) }
        LaunchedEffect(alert.at, style) {
            while (shown < alert.message.length) {
                delay(18)
                shown += 2
            }
        }
        Column(Modifier.weight(1f)) {
            FilmAlertHeader(ThemeState.edition, color)
            Text(
                text = alert.message.take(shown) + if (shown < alert.message.length) "█" else "",
                style = MaterialTheme.typography.titleMedium,
                color = DashWhite,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        alert.action?.let { action ->
            KittButton(action.label, { onAction(action) }, Modifier.height(48.dp).width(120.dp), LedGreen, active = true)
        }
    }
}

/** What's new after an update, once. */
@Composable
private fun WhatsNewCard(notes: List<ReleaseNote>, onDone: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onDone)
    Column(
        modifier
            .fillMaxWidth(0.9f)
            .clip(MaterialTheme.shapes.medium)
            .background(KittBlack.copy(alpha = 0.96f))
            .border(2.dp, LedGreen, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("WHAT'S NEW", style = MaterialTheme.typography.titleMedium, color = LedGreen)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            notes.flatMap { it.items }.forEach { line ->
                item { Text("• $line", style = MaterialTheme.typography.bodyLarge, color = DashWhite) }
            }
        }
        KittButton("Got it", onDone, Modifier.align(Alignment.End).height(48.dp).width(120.dp), LedGreen, active = true)
    }
}

@Preview(name = "7-inch head unit 1024x600", widthDp = 1024, heightDp = 600)
@Composable
private fun LauncherSevenInchPreview() {
    KnightRiderLauncherTheme {
        LauncherRoot(apps = emptyList(), onAppClick = {})
    }
}

@Preview(name = "Portrait head unit 9:16", widthDp = 720, heightDp = 1280)
@Composable
private fun LauncherPortraitPreview() {
    KnightRiderLauncherTheme {
        LauncherRoot(apps = emptyList(), onAppClick = {})
    }
}

@Preview(name = "Landscape 16:9", widthDp = 1280, heightDp = 720)
@Composable
private fun LauncherLandscapePreview() {
    KnightRiderLauncherTheme {
        LauncherRoot(apps = emptyList(), onAppClick = {})
    }
}
