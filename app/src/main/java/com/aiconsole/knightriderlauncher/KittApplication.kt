package com.aiconsole.knightriderlauncher

import android.app.ActivityManager
import android.app.Application
import android.appwidget.AppWidgetHost
import android.database.SQLException
import android.util.Log
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import com.aiconsole.knightriderlauncher.assistant.AssistantPrompt
import com.aiconsole.knightriderlauncher.assistant.AssistantRepository
import com.aiconsole.knightriderlauncher.assistant.DriveAssistant
import com.aiconsole.knightriderlauncher.assistant.GeminiAssistantClient
import com.aiconsole.knightriderlauncher.assistant.KittAlerts
import com.aiconsole.knightriderlauncher.assistant.PerplexityAssistantClient
import com.aiconsole.knightriderlauncher.assistant.TextToSpeechHelper
import com.aiconsole.knightriderlauncher.assistant.WakeWordListener
import com.aiconsole.knightriderlauncher.browser.WebMedia
import com.aiconsole.knightriderlauncher.data.DeviceProfile
import com.aiconsole.knightriderlauncher.data.FreezeWatchdog
import com.aiconsole.knightriderlauncher.data.ProblemReport
import com.aiconsole.knightriderlauncher.data.StartupRoutine
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.data.trip.TripDatabase
import com.aiconsole.knightriderlauncher.data.trip.TripMetricRecorder
import com.aiconsole.knightriderlauncher.media.MediaBridge
import com.aiconsole.knightriderlauncher.nav.GpsTracker
import com.aiconsole.knightriderlauncher.nav.NavServices
import com.aiconsole.knightriderlauncher.nav.RouteNavigator
import com.aiconsole.knightriderlauncher.nav.SpeedLimitService
import com.aiconsole.knightriderlauncher.nav.WeatherService
import com.aiconsole.knightriderlauncher.obd.AdapterConfig
import com.aiconsole.knightriderlauncher.obd.AdapterProfiles
import com.aiconsole.knightriderlauncher.obd.AdapterType
import com.aiconsole.knightriderlauncher.obd.CustomPidStore
import com.aiconsole.knightriderlauncher.obd.DataLogger
import com.aiconsole.knightriderlauncher.obd.GaugeStore
import com.aiconsole.knightriderlauncher.obd.ObdManager
import com.aiconsole.knightriderlauncher.obd.PidHistory
import com.aiconsole.knightriderlauncher.obd.VehicleService
import com.aiconsole.knightriderlauncher.stream.UserscriptStore
import com.aiconsole.knightriderlauncher.ui.components.SoundFx
import com.aiconsole.knightriderlauncher.ui.components.UiSounds
import com.aiconsole.knightriderlauncher.ui.theme.NightModeController
import com.aiconsole.knightriderlauncher.ui.theme.ThemeState
import com.aiconsole.knightriderlauncher.ui.theme.UiTheme
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds app-wide singletons. The OBD session and KITT's voice outlive any single screen. */
class KittApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: KittSettings by lazy { KittSettings(this, appScope) }

    val tripDatabase: TripDatabase by lazy { TripDatabase.create(this) }

    val tripRecorder: TripMetricRecorder by lazy {
        TripMetricRecorder(tripDatabase.tripMetricDao(), appScope)
    }

    val gauges: GaugeStore by lazy { GaugeStore(this) }

    val customPids: CustomPidStore by lazy { CustomPidStore(this) }

    val obd: ObdManager by lazy { ObdManager(this, settings, tripRecorder, customPids, appScope) }

    /** Now-playing from the active media session (needs notification access). */
    val media: MediaBridge by lazy { MediaBridge(this) }

    /** VIN identity, odometer, maintenance, fuel range and battery health. */
    val vehicle: VehicleService by lazy { VehicleService(obd, settings, pidHistory, appScope) }

    /** GPS speed/heading while the launcher shows: speedometer fallback and compass. */
    val gps: GpsTracker by lazy { GpsTracker(this) }

    /** Hosts home-screen widgets on the Widgets page. */
    val widgetHost: AppWidgetHost by lazy { AppWidgetHost(this, WIDGET_HOST_ID) }

    val startup: StartupRoutine by lazy { StartupRoutine(this, settings, media, appScope) }

    /** Weather at the vehicle and spoken road warnings. */
    val weather: WeatherService by lazy { WeatherService(gps, alerts, settings, appScope) }

    /** Place search and spoken turn-by-turn directions on the home map. */
    val navigator: RouteNavigator by lazy { RouteNavigator(gps, NavServices(), settings, kittVoice, appScope) }

    /** "Hey KITT" listener, active while the launcher is in front and idle. */
    val wakeWord: WakeWordListener by lazy { WakeWordListener(this, settings, kittVoice, appScope) }

    /** Userscripts for the home-screen stream window. */
    val userscripts: UserscriptStore by lazy { UserscriptStore(this) }

    /** Last few minutes of every polled sensor, for the live graphs. */
    val pidHistory = PidHistory()

    val dataLogger: DataLogger by lazy { DataLogger(this, obd, settings, appScope) }

    /** KITT's one voice, shared by the assistant and alerts. */
    val kittVoice: TextToSpeechHelper by lazy { TextToSpeechHelper(this, settings, appScope) }

    val alerts: KittAlerts by lazy { KittAlerts(settings, obd, gauges, kittVoice, appScope) }

    val assistantRepository: AssistantRepository by lazy {
        AssistantRepository(
            settings = settings,
            gemini = GeminiAssistantClient(this),
            perplexity = PerplexityAssistantClient(this),
            dao = tripDatabase.tripMetricDao(),
            recorder = tripRecorder,
            vehicleContext = {
                val identity = vehicle.identity.value
                obd.vehicleContext().let { c ->
                    c.copy(
                        vehicle = listOfNotNull(identity?.summary(), identity?.engine, c.vehicle).joinToString(", ").ifEmpty { null },
                        maintenance = vehicle.reminder(),
                        battery = vehicle.battery().warnings.joinToString("; ").ifEmpty { null }
                    )
                }
            }
        )
    }

    /** Questions queued for KITT from other screens; the assistant consumes and clears them. */
    val assistantInbox = MutableStateFlow<AssistantPrompt?>(null)

    /** Night dimming (light sensor or clock); the activity turns the sensor on while visible. */
    val nightMode: NightModeController by lazy { NightModeController(this, settings, appScope) { gps.position() } }

    /** The road's speed limit from OpenStreetMap, while the launcher shows. */
    val speedLimits: SpeedLimitService by lazy { SpeedLimitService(gps, settings, appScope) }

    /** Driving commentary, low-fuel and speed warnings, parking spot and report cards. */
    val driveAssistant: DriveAssistant by lazy {
        DriveAssistant(obd, gps, navigator, speedLimits, alerts, settings, tripDatabase.tripMetricDao(), {
            vehicle.rangeKm(null)
        }, appScope)
    }

    /** Cheap head-unit hardware (little RAM or few cores): lite mode's Auto setting turns on. */
    val lowEndDevice: Boolean by lazy {
        val manager = getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { manager?.getMemoryInfo(it) }
        DeviceProfile.isLowEnd(
            totalRamMb = memory.totalMem / (1024 * 1024),
            lowRamFlag = manager?.isLowRamDevice == true,
            cores = Runtime.getRuntime().availableProcessors()
        )
    }

    /** Lite mode in effect: the driver's choice, or Auto from [lowEndDevice]. */
    val lite: StateFlow<Boolean> by lazy {
        settings.liteMode.state
            .map { DeviceProfile.lite(it, lowEndDevice) }
            .stateIn(appScope, SharingStarted.Eagerly, DeviceProfile.lite(settings.liteMode.value, lowEndDevice))
    }

    /** Every saved adapter, the preferred one first; connection rotates through them. */
    fun savedAdapters(): List<AdapterConfig> =
        (listOfNotNull(savedAdapter()) + AdapterProfiles.decode(settings.adapterList.value)).distinctBy { it.address }

    fun forgetAdapter(config: AdapterConfig) {
        settings.adapterList.set(AdapterProfiles.encode(AdapterProfiles.decode(settings.adapterList.value).filter { it.address != config.address }))
        if (savedAdapter()?.address == config.address) {
            val next = AdapterProfiles.decode(settings.adapterList.value).firstOrNull()
            settings.adapterType.set(next?.type?.name.orEmpty())
            settings.adapterAddress.set(next?.address.orEmpty())
            settings.adapterName.set(next?.name.orEmpty())
        }
    }

    fun savedAdapter(): AdapterConfig? {
        val type = runCatching { AdapterType.valueOf(settings.adapterType.value) }.getOrNull() ?: return null
        val address = settings.adapterAddress.value.ifEmpty { return null }
        return AdapterConfig(type, address, settings.adapterName.value.ifEmpty { address })
    }

    fun saveAdapter(config: AdapterConfig) {
        settings.adapterList.set(
            AdapterProfiles.encode(listOf(config) + AdapterProfiles.decode(settings.adapterList.value).filter { it.address != config.address })
        )
        settings.adapterType.set(config.type.name)
        settings.adapterAddress.set(config.address)
        settings.adapterName.set(config.name)
    }

    override fun onCreate() {
        super.onCreate()
        ProblemReport.installCrashHandler(this)
        FreezeWatchdog.start(this)
        // The home pager pre-composes neighbouring pages in pieces across frames. With a WebView
        // (AndroidView) on those pages that crashed the launcher on the head unit ("LayoutNode
        // should be attached to an owner"), so prefetch composes whole pages at once instead.
        @OptIn(ExperimentalFoundationApi::class)
        ComposeFoundationFlags.isPausableCompositionInPrefetchEnabled = false
        WebMedia.init(this)
        // Apply the saved look before the first frame, then follow changes from KITT Config.
        ThemeState.theme = settings.uiTheme.value
        // The night theme (if one is chosen) takes over while night mode says it's dark.
        appScope.launch(Dispatchers.Main) {
            combine(settings.uiTheme.state, settings.nightTheme.state, nightMode.night) { day, nightName, dark ->
                val nightTheme = runCatching { UiTheme.valueOf(nightName) }.getOrNull()
                if (dark && nightTheme != null) nightTheme else day
            }.collect { ThemeState.theme = it }
        }
        appScope.launch { settings.soundEffects.state.collect { SoundFx.enabled = it } }
        appScope.launch(Dispatchers.Main) {
            settings.accentColor.state.collect { argb ->
                ThemeState.accentOverride = if (argb == 0L) null else Color(argb.toInt())
            }
        }
        appScope.launch {
            obd.readings.collect { readings -> readings.forEach { (id, reading) -> pidHistory.add(id, reading) } }
        }
        // Dashboard gauges are always polled while connected, on screen or not (alerts need them).
        appScope.launch {
            gauges.gauges.collect { list -> obd.watch("dashboard", list.map { it.pidId }.toSet()) }
        }
        startup.register()
        appScope.launch { settings.buttonSounds.state.collect { UiSounds.enabled = it } }
        appScope.launch(Dispatchers.Main) {
            vehicle.start()
            driveAssistant.start()
        }
        appScope.launch(Dispatchers.IO) {
            try {
                val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
                tripDatabase.tripMetricDao().deleteOlderThan(cutoff)
            } catch (e: SQLException) {
                Log.w("KittApplication", "Trip log pruning failed", e)
            }
        }
    }

    /** Android is short of memory: hidden web pages go first (they reload when shown again). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        WebMedia.trimMemory(level)
    }

    private companion object {
        const val WIDGET_HOST_ID = 0x4B17
        const val RETENTION_DAYS = 90L
    }
}
