package com.aiconsole.knightriderlauncher.obd

import android.content.Context
import android.util.Log
import com.aiconsole.knightriderlauncher.assistant.VehicleContext
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.data.trip.TripMetricLog
import com.aiconsole.knightriderlauncher.data.trip.TripMetricRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

sealed interface ObdState {
    data object Idle : ObdState
    data class Connecting(val stage: String) : ObdState
    data class Connected(val info: ObdSessionInfo) : ObdState
    data class Error(val message: String, val retryInSeconds: Int) : ObdState
}

data class ObdSessionInfo(
    val adapterName: String,
    val elmVersion: String,
    val protocol: String,
    val isCan: Boolean,
    val vin: String?,
    val detectedProfile: ManufacturerProfile?,
    val supportedPidCount: Int
)

data class PidReading(val value: Double, val timestamp: Long)

/** Sensor snapshot the ECU stored when [dtc] set (Mode 02, frame 0). */
data class FreezeFrame(val dtc: String, val values: List<FreezeFrameValue>, val readAt: Long)

data class FreezeFrameValue(val def: PidDefinition, val value: Double)

/** Pure driving-state thresholds (speed in km/h), unit-tested. */
object DrivingRules {
    const val MOVING_ABOVE_KMH = 8.0
    const val STOPPED_BELOW_KMH = 3.0

    fun nextMoving(wasMoving: Boolean, speedKmh: Double?): Boolean = when {
        speedKmh == null -> false
        speedKmh > MOVING_ABOVE_KMH -> true
        speedKmh < STOPPED_BELOW_KMH -> false
        else -> wasMoving
    }
}

data class DtcReport(
    val stored: List<String>,
    val pending: List<String>,
    val permanent: List<String>,
    val milOn: Boolean,
    val readAt: Long,
    /** Emissions monitors from the same Mode 01 PID 01 reply; null if the ECU didn't answer. */
    val readiness: Readiness? = null
) {
    val all: List<String> get() = (stored + pending + permanent).distinct()
}

/**
 * Owns the OBD session: connect → init ELM327 → discover supported PIDs → read VIN and
 * detect the manufacturer → poll continuously, with automatic reconnect and backoff.
 *
 * Polling is demand-driven (only PIDs something is watching), tiered (fast PIDs every
 * cycle, slow ones every [SLOW_EVERY] cycles), and batched on CAN (up to 6 Mode 01 PIDs per
 * request) with the ELM "expected responses" suffix to skip adapter timeouts.
 */
class ObdManager(
    private val context: Context,
    private val settings: KittSettings,
    private val recorder: TripMetricRecorder,
    customPids: CustomPidStore,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<ObdState>(ObdState.Idle)
    val state: StateFlow<ObdState> = _state.asStateFlow()

    private val _readings = MutableStateFlow<Map<String, PidReading>>(emptyMap())
    val readings: StateFlow<Map<String, PidReading>> = _readings.asStateFlow()

    /** Last raw data bytes (hex) behind each standard reading, for problem reports. */
    private val rawData = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun rawDataOf(id: String): String? = rawData[id]

    /**
     * [readings] sampled at [DISPLAY_INTERVAL_MS] for the UI. The poll loop can run well above
     * 10 Hz on CAN; redrawing faster than that only costs frames. Logging and alerts keep
     * using the full-rate [readings].
     */
    @OptIn(FlowPreview::class)
    val displayReadings: StateFlow<Map<String, PidReading>> =
        _readings.sample(DISPLAY_INTERVAL_MS).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** True while the vehicle is moving, for driving-mode UI limits. Hysteresis avoids flicker. */
    val moving: StateFlow<Boolean> = _readings
        .map { it[PidCatalog.SPEED]?.value }
        .scan(false) { was, speed -> DrivingRules.nextMoving(was, speed) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _dtcs = MutableStateFlow<DtcReport?>(null)
    val dtcs: StateFlow<DtcReport?> = _dtcs.asStateFlow()

    private val _freezeFrame = MutableStateFlow<FreezeFrame?>(null)
    val freezeFrame: StateFlow<FreezeFrame?> = _freezeFrame.asStateFlow()

    /** Full polling cycles per second, like Car Scanner's refresh indicator. */
    private val _refreshHz = MutableStateFlow(0f)
    val refreshHz: StateFlow<Float> = _refreshHz.asStateFlow()

    private val _supportedMode01 = MutableStateFlow<Set<Int>>(emptySet())
    private val _unavailable = MutableStateFlow<Set<String>>(emptySet())
    private val _detected = MutableStateFlow<ManufacturerProfile?>(null)

    /** Active manufacturer profile: the user's choice, or auto-detected from the VIN. */
    val profile: StateFlow<ManufacturerProfile> =
        combine(settings.manufacturerProfile.state, _detected) { choice, detected ->
            if (choice == ManufacturerProfiles.AUTO) detected ?: ManufacturerProfiles.generic
            else ManufacturerProfiles.byId(choice) ?: ManufacturerProfiles.generic
        }.stateIn(scope, SharingStarted.Eagerly, ManufacturerProfiles.generic)

    /** Every PID definition the app knows about right now, by id. */
    val definitions: StateFlow<Map<String, PidDefinition>> =
        combine(profile, customPids.pids, settings.manufacturerPids.state) { p, custom, useMfr ->
            (PidCatalog.standard + PidCatalog.adapterVoltage + (if (useMfr) p.pids else emptyList()) + custom)
                .associateBy { it.id }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** PIDs worth offering in pickers: standard ones filtered to what this car supports once known. */
    val available: StateFlow<List<PidDefinition>> =
        combine(definitions, _supportedMode01, _unavailable) { defs, supported, dead ->
            defs.values.filter { def ->
                def.id !in dead && (supported.isEmpty() || def.mode01Pid?.let { it in supported } ?: true)
            }.sortedWith(compareBy<PidDefinition> { it.source.ordinal }.thenBy { it.name })
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val watchers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    private val log = ConnectionLog()
    private val _linkEvents = MutableStateFlow<List<LinkEvent>>(emptyList())

    /** Connection steps and failures, tagged by layer (transport, adapter, ELM init, ECU, decode). */
    val linkEvents: StateFlow<List<LinkEvent>> = _linkEvents.asStateFlow()

    /** An adapter scan's outcome, for the connection log and problem reports. */
    fun logScan(message: String, ok: Boolean) = logEvent(LinkLayer.Transport, message, ok)

    private fun logEvent(layer: LinkLayer, message: String, ok: Boolean = true) {
        log.add(LinkEvent(System.currentTimeMillis(), layer, message, ok))
        _linkEvents.value = log.snapshot()
    }

    @Volatile
    private var elm: Elm327? = null

    @Volatile
    private var isCan = false

    /** Which layer a failure right now belongs to, for the connection log. */
    @Volatile
    private var stageLayer = LinkLayer.Transport

    private var session: Job? = null

    /** Registers the PIDs a screen needs polled; replaces that screen's previous set. */
    fun watch(key: String, pidIds: Set<String>) = watchers.update { it + (key to pidIds) }

    fun unwatch(key: String) = watchers.update { it - key }

    fun connect(config: AdapterConfig) = connect(listOf(config))

    /**
     * Debug builds only (see [DemoSession]): a simulated, clearly labelled session for
     * screenshots and demos. Nothing is sent to any adapter or vehicle; a real connect ends it.
     */
    @Volatile
    private var demoReport: DtcReport? = null

    internal fun startDemo(info: ObdSessionInfo, report: DtcReport, frame: () -> Map<String, Double>) {
        session?.cancel()
        demoReport = report
        _state.value = ObdState.Connected(info)
        _dtcs.value = report
        session = scope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                _readings.update { old -> old + frame().mapValues { PidReading(it.value, now) } }
                _refreshHz.value = 8f
                delay(250)
            }
        }
    }

    /** Connects to the first adapter in [configs] that answers, rotating through them on failure. */
    fun connect(configs: List<AdapterConfig>) {
        if (configs.isEmpty()) return
        demoReport = null
        val previous = session
        previous?.cancel()
        session = scope.launch(Dispatchers.IO) {
            // Let the old session hang up first: two links dialling one adapter both fail.
            previous?.join()
            runWithReconnect(configs)
        }
    }

    fun disconnect() {
        demoReport = null
        session?.cancel()
        session = null
        elm = null
        _state.value = ObdState.Idle
        _readings.value = emptyMap()
        _refreshHz.value = 0f
    }

    suspend fun readCodes(): Result<DtcReport> {
        demoReport?.let { if (_state.value is ObdState.Connected && elm == null) return Result.success(it) }
        val link = elm ?: return Result.failure(IOException("OBD adapter not connected"))
        return try {
            Result.success(readCodesInternal(link))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /** Re-reads the freeze frame on demand (it's also read automatically with stored codes). */
    suspend fun readFreezeFrame(): Result<FreezeFrame?> {
        val link = elm ?: return Result.failure(IOException("OBD adapter not connected"))
        return try {
            Result.success(readFreezeFrameInternal(link))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /** Mode 04: clears codes and freeze frame, and resets readiness monitors. */
    suspend fun clearCodes(): Result<DtcReport> {
        val link = elm ?: return Result.failure(IOException("OBD adapter not connected"))
        return try {
            val raw = link.exclusive {
                if (isCan) useHeader(BROADCAST_HEADER)
                val response = send("04", 5_000)
                useDefaultHeader()
                response
            }
            ObdResponseParser.errorOf(raw)?.let { return Result.failure(IOException("Clear failed: $it")) }
            Result.success(readCodesInternal(link))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    data class AdapterDetails(val assessment: AdapterAssessment, val voltage: Double?, val raw: String)

    /** Identification (ATI, AT@1, STI) and supply voltage; nothing is written to the adapter. */
    suspend fun adapterDetails(): Result<AdapterDetails> = withLink { link ->
        link.exclusive {
            val ati = send("ATI").trim()
            val desc = send("AT@1").trim()
            val sti = send("STI").trim()
            val volts = send("ATRV").trim()
            AdapterDetails(
                CloneCheck.assess(ati, desc, sti),
                ObdResponseParser.voltage(volts),
                "ATI: $ati\nAT@1: $desc\nSTI: $sti\nATRV: $volts"
            )
        }
    }

    data class ModuleCodes(val module: VehicleModule, val stored: List<String>, val pending: List<String>)

    /** Which control modules answer on CAN, then each one's stored and pending codes. Read-only. */
    suspend fun scanModules(): Result<List<ModuleCodes>> = withLink { link ->
        if (!isCan) throw IOException("Module scan needs a CAN vehicle (2008+ in the US).")
        link.exclusive {
            useHeader(BROADCAST_HEADER)
            send("ATH1")
            val raw = try {
                send("0100", 3_000)
            } finally {
                send("ATH0")
            }
            val modules = ModuleScan.responders(raw)
            val result = modules.map { module ->
                val header = module.requestHeader
                if (header == null) {
                    ModuleCodes(module, emptyList(), emptyList())
                } else {
                    useHeader(header)
                    fun parse(r: String, service: Int) = ObdResponseParser.dtcs(ObdResponseParser.messages(r), service, true)
                    ModuleCodes(module, parse(send("03", 3_000), 0x43), parse(send("07", 3_000), 0x47))
                }
            }
            useDefaultHeader()
            logEvent(LinkLayer.Ecu, "Module scan: ${modules.size} modules answered")
            result
        }
    }

    /** Mode 06 on-board monitor results from the engine ECU (CAN only). Read-only. */
    suspend fun readMonitorTests(): Result<List<MonitorTest>> = withLink { link ->
        if (!isCan) throw IOException("Mode 06 decoding here needs a CAN vehicle.")
        link.exclusive {
            useDefaultHeader()
            val supported = mutableSetOf<Int>()
            var base = 0x00
            while (base <= 0xE0) {
                val found = Mode06.supportedMids(ObdResponseParser.messages(send("06%02X".format(Locale.US, base), 2_000)), base)
                supported += found
                if (base + 0x20 !in found) break
                base += 0x20
            }
            supported.filter { it % 0x20 != 0 }.sorted().flatMap { mid ->
                Mode06.results(ObdResponseParser.messages(send("06%02X".format(Locale.US, mid), 2_000)))
            }
        }
    }

    /** Sends one console command if [ConsoleSafety] allows it; returns the raw reply. */
    suspend fun console(command: String): Result<String> {
        ConsoleSafety.check(command)?.let { return Result.failure(IOException(it)) }
        return withLink { link ->
            link.exclusive {
                val cmd = command.trim().replace(" ", "").uppercase()
                if (!cmd.startsWith("AT") && !cmd.startsWith("ST")) useDefaultHeader()
                send(cmd, 5_000)
            }
        }
    }

    private suspend fun <T> withLink(block: suspend (Elm327) -> T): Result<T> {
        val link = elm ?: return Result.failure(IOException("OBD adapter not connected"))
        return try {
            Result.success(block(link))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /** One-off request for the custom PID editor: raw response plus the decoded value. */
    suspend fun testPid(def: PidDefinition): String {
        val link = elm ?: return "Not connected"
        return try {
            val raw = link.exclusive {
                if (def.header != null) useHeader(def.header) else useDefaultHeader()
                val response = send(def.command, 2_000)
                useDefaultHeader()
                response
            }
            val data = decodeData(def, raw) ?: return "Raw: ${raw.replace('\n', ' ')}\nNo matching data"
            val hex = data.joinToString(" ") { "%02X".format(Locale.US, it) }
            val value = FormulaEvaluator.evaluate(def.formula, data)
            "Data: $hex\nValue: ${"%.2f".format(Locale.US, value)} ${def.unit(true)}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            "Error: ${e.message}"
        } catch (e: FormulaEvaluator.FormulaException) {
            "Formula error: ${e.message}"
        }
    }

    /** Current vehicle state for the AI assistant, in the driver's display units. */
    fun vehicleContext(): VehicleContext {
        val current = _state.value
        val metric = settings.metricUnits.value
        val defs = definitions.value
        val now = System.currentTimeMillis()
        val lines = _readings.value
            .filter { now - it.value.timestamp < STALE_MS }
            .mapNotNull { (id, reading) ->
                val def = defs[id] ?: return@mapNotNull null
                val value = def.displayValue(reading.value, metric)
                "${def.name}: ${formatValue(value, def)} ${def.unit(metric)}".trim()
            }
        val info = (current as? ObdState.Connected)?.info
        val dtc = _dtcs.value
        val frame = _freezeFrame.value
        return VehicleContext(
            linkStatus = when (current) {
                is ObdState.Connected -> "connected (${info?.protocol})"
                is ObdState.Connecting -> "connecting"
                is ObdState.Error -> "error: ${current.message}"
                ObdState.Idle -> "not connected"
            },
            vehicle = info?.let { i ->
                listOfNotNull(profile.value.name.takeIf { profile.value.id != "generic" }, i.vin?.let { "VIN $it" })
                    .joinToString(", ").ifEmpty { null }
            },
            liveReadings = lines,
            storedCodes = dtc?.stored.orEmpty() + dtc?.permanent.orEmpty(),
            pendingCodes = dtc?.pending.orEmpty(),
            freezeFrameCode = frame?.dtc,
            freezeFrame = frame?.values.orEmpty().map { (def, value) ->
                "${def.name}: ${formatValue(def.displayValue(value, metric), def)} ${def.unit(metric)}".trim()
            },
            readiness = dtc?.readiness?.let { r ->
                if (r.incomplete.isEmpty()) {
                    "all ${r.monitors.size} supported monitors complete"
                } else {
                    "${r.incomplete.size} of ${r.monitors.size} incomplete: ${r.incomplete.joinToString { it.name }}"
                }
            }
        )
    }

    // ---------------------------------------------------------------------------------------

    private suspend fun runWithReconnect(configs: List<AdapterConfig>) {
        var attempt = 0
        var index = 0
        while (currentCoroutineContext().isActive) {
            val config = configs[index % configs.size]
            try {
                runSession(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "OBD session ended", e)
                logEvent(stageLayer, "${config.name}: ${e.message ?: e.javaClass.simpleName}", ok = false)
                attempt++
                index++ // Next time, try the next saved adapter.
                val wait = (3 * attempt).coerceAtMost(30)
                _state.value = ObdState.Error(e.message ?: e.javaClass.simpleName, wait)
                _readings.value = emptyMap()
                _refreshHz.value = 0f
                delay(wait * 1_000L)
                continue
            }
            attempt = 0
        }
    }

    private suspend fun runSession(saved: AdapterConfig) {
        val type = AdapterDiscovery.transportFor(context, saved)
        val config = saved.copy(type = type)
        _state.value = ObdState.Connecting("Opening ${config.type.label} link to ${config.name}")
        stageLayer = LinkLayer.Transport
        if (type != saved.type) {
            logEvent(LinkLayer.Transport, "${config.name} was saved as ${saved.type.label}, but Android knows it as ${type.label}: using ${type.label}")
        }
        logEvent(LinkLayer.Transport, "Opening ${config.type.label} link to ${config.name}")
        val transport = createTransport(context, config)
        try {
            transport.connect()
            logEvent(LinkLayer.Adapter, "Connected to ${config.name}")
            stageLayer = LinkLayer.ElmInit
            val link = Elm327(transport)
            _state.value = ObdState.Connecting("Initializing adapter")
            val init = link.initialize(settings.obdProtocol.value)
            isCan = init.isCan
            logEvent(LinkLayer.ElmInit, "${init.version} · ${init.protocolName}")
            stageLayer = LinkLayer.Ecu

            _state.value = ObdState.Connecting("Reading vehicle systems")
            _unavailable.value = emptySet()
            val supported = link.exclusive { discoverSupported(this, init.isCan) }
            _supportedMode01.value = supported
            val vin = runCatching { link.exclusive { ObdResponseParser.vin(ObdResponseParser.messages(send("0902", 5_000))) } }
                .getOrNull()
            _detected.value = ManufacturerProfiles.detect(vin)
            logEvent(LinkLayer.Ecu, "ECU answered: ${supported.size} PIDs supported" + (vin?.let { ", VIN $it" } ?: ""))
            stageLayer = LinkLayer.Decode

            elm = link
            _state.value = ObdState.Connected(
                ObdSessionInfo(config.name, init.version, init.protocolName, init.isCan, vin, _detected.value, supported.size)
            )
            runCatching { readCodesInternal(link) }
            pollLoop(link)
        } finally {
            elm = null
            transport.close()
        }
    }

    private suspend fun discoverSupported(link: Elm327.Link, can: Boolean): Set<Int> {
        if (can) {
            // Address the engine ECU directly: faster, and avoids mixed replies from other modules.
            link.setDefaultHeader(ENGINE_HEADER)
            link.useHeader(ENGINE_HEADER)
        }
        var result = readSupportedBitmaps(link)
        if (result.isEmpty() && can) {
            link.setDefaultHeader(BROADCAST_HEADER)
            link.useHeader(BROADCAST_HEADER)
            result = readSupportedBitmaps(link)
        }
        return result
    }

    private suspend fun readSupportedBitmaps(link: Elm327.Link): Set<Int> {
        val all = mutableSetOf<Int>()
        var base = 0x00
        while (base <= 0xC0) {
            val raw = link.send("01%02X".format(Locale.US, base), 3_000)
            val found = ObdResponseParser.supportedPids(ObdResponseParser.messages(raw), base)
            all += found
            if (base + 0x20 !in found) break
            base += 0x20
        }
        return all
    }

    private suspend fun readCodesInternal(link: Elm327): DtcReport {
        val report = link.exclusive {
            // Broadcast so every emissions-related module reports its codes.
            if (isCan) useHeader(BROADCAST_HEADER)
            fun parse(raw: String, service: Int) =
                ObdResponseParser.dtcs(ObdResponseParser.messages(raw), service, isCan)
            val stored = parse(send("03", 4_000), 0x43)
            val pending = parse(send("07", 4_000), 0x47)
            val permanent = if (isCan) parse(send("0A", 4_000), 0x4A) else emptyList()
            val status = ObdResponseParser.mode01Values(
                ObdResponseParser.messages(send("0101", 2_000)), PidCatalog.mode01ByteCounts
            )[0x01]
            useDefaultHeader()
            DtcReport(
                stored = stored,
                pending = pending,
                permanent = permanent,
                milOn = status?.let { it[0].toInt() and 0x80 != 0 } ?: stored.isNotEmpty(),
                readAt = System.currentTimeMillis(),
                readiness = status?.let(ReadinessDecoder::decode)
            )
        }
        _dtcs.value = report
        if (report.stored.isEmpty()) {
            _freezeFrame.value = null
        } else {
            try {
                readFreezeFrameInternal(link)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Freeze frame read failed", e) // Codes still count; the frame is a bonus.
            }
        }
        return report
    }

    /** Mode 02 frame 0: the DTC that stored it, then every supported standard PID in it. */
    private suspend fun readFreezeFrameInternal(link: Elm327): FreezeFrame? {
        val frame = link.exclusive {
            useDefaultHeader()
            fun hex(pid: Int) = "02%02X00".format(Locale.US, pid)
            val dtc = ObdResponseParser.freezeFrameDtc(ObdResponseParser.messages(send(hex(0x02), 2_000)))
                ?: return@exclusive null
            val supported = mutableSetOf<Int>()
            var base = 0x00
            while (base <= 0xC0) {
                val found = ObdResponseParser.freezeFrameSupported(ObdResponseParser.messages(send(hex(base), 2_000)), base)
                supported += found
                if (base + 0x20 !in found) break
                base += 0x20
            }
            // ECUs that don't report the Mode 02 bitmap still answer the core set.
            val defs = PidCatalog.standard.filter { def ->
                val pid = def.mode01Pid ?: return@filter false
                if (supported.isEmpty()) def.id in FREEZE_FRAME_CORE else pid in supported
            }
            val values = defs.mapNotNull { def ->
                val pid = def.mode01Pid ?: return@mapNotNull null
                val data = ObdResponseParser.freezeFrameData(ObdResponseParser.messages(send(hex(pid), 1_500)), pid)
                    ?: return@mapNotNull null
                val bytes = if (def.bytes > 0 && data.size > def.bytes) data.copyOfRange(0, def.bytes) else data
                evaluate(def, bytes)?.let { FreezeFrameValue(def, it) }
            }
            FreezeFrame(dtc, values, System.currentTimeMillis())
        }
        _freezeFrame.value = frame
        return frame
    }

    private suspend fun pollLoop(link: Elm327) {
        var cycle = 0L
        var multiPid = settings.obdFastPolling.value && isCan
        var countHint = true
        val failures = HashMap<String, Int>()
        var windowStart = System.currentTimeMillis()
        var windowCycles = 0
        var lastLog = 0L
        var lastMilCheck = System.currentTimeMillis()
        var lastMilCount = _dtcs.value?.stored?.size ?: 0

        fun fail(def: PidDefinition) {
            val count = (failures[def.id] ?: 0) + 1
            failures[def.id] = count
            if (count >= MAX_FAILURES) _unavailable.update { it + def.id }
        }

        while (currentCoroutineContext().isActive) {
            val defs = definitions.value
            val supported = _supportedMode01.value
            val dead = _unavailable.value
            val wanted = (watchers.value.values.flatten() + ALWAYS_POLLED).toSet()
                .mapNotNull { defs[it] }
                .filter { it.id !in dead }
                .filter { def -> def.mode01Pid?.let { supported.isEmpty() || it in supported } ?: true }
                .filter { isCan || it.header == null }
            val due = wanted.filter { it.fast || cycle % SLOW_EVERY == 0L }
            if (due.isEmpty()) {
                delay(250)
                cycle++
                continue
            }

            val now = System.currentTimeMillis()
            val results = HashMap<String, Double>()

            // 1) Standard Mode 01 on the engine ECU, batched on CAN.
            val standard = due.filter { it.mode01Pid != null }
            val groups = if (multiPid) standard.chunked(6) else standard.map { listOf(it) }
            for (group in groups) {
                val command = "01" + group.joinToString("") { "%02X".format(Locale.US, it.mode01Pid) }
                var raw = link.exclusive {
                    useDefaultHeader()
                    send(if (group.size == 1 && countHint && isCan) command + "1" else command, 1_000)
                }
                if (raw.trim() == "?" && countHint) {
                    countHint = false // Old/clone adapter without the response-count suffix.
                    raw = link.exclusive { send(command, 1_000) }
                }
                val values = ObdResponseParser.mode01Values(ObdResponseParser.messages(raw), PidCatalog.mode01ByteCounts)
                if (group.size > 1 && values.size < (group.size + 1) / 2) {
                    multiPid = false // ECU doesn't handle multi-PID requests well; go single.
                }
                for (def in group) {
                    val data = values[def.mode01Pid]
                    val value = data?.let { evaluate(def, it) }
                    if (data != null) rawData[def.id] = data.joinToString(" ") { "%02X".format(Locale.US, it.toInt() and 0xFF) }
                    if (value != null) {
                        results[def.id] = value
                        failures.remove(def.id)
                    } else if (!multiPid || group.size == 1) {
                        fail(def)
                    }
                }
            }

            // 2) Adapter battery voltage.
            if (due.any { it.id == PidCatalog.ADAPTER_VOLTAGE }) {
                link.exclusive { ObdResponseParser.voltage(send("ATRV", 1_000)) }
                    ?.let { results[PidCatalog.ADAPTER_VOLTAGE] = it }
            }

            // 3) Manufacturer and custom PIDs, each on its own ECU header.
            for (def in due.filter { it.mode01Pid == null && it.source != PidSource.ADAPTER }) {
                val raw = link.exclusive {
                    if (def.header != null) useHeader(def.header) else useDefaultHeader()
                    send(def.command, 1_500)
                }
                val value = decodeData(def, raw)?.let { evaluate(def, it) }
                if (value != null) {
                    results[def.id] = value
                    failures.remove(def.id)
                } else {
                    fail(def)
                }
            }
            if (due.any { it.header != null }) link.exclusive { useDefaultHeader() }

            if (results.isNotEmpty()) {
                _readings.update { old -> old + results.mapValues { PidReading(it.value, now) } }
            }

            // Refresh-rate indicator.
            windowCycles++
            val elapsed = now - windowStart
            if (elapsed >= 2_000) {
                _refreshHz.value = windowCycles * 1000f / elapsed
                windowStart = now
                windowCycles = 0
            }

            // 1 Hz trip logging into Room.
            if (now - lastLog >= 1_000) {
                lastLog = now
                logTrip(now)
            }

            // Cheap MIL/code-count check every 30 s; full code read only when it changes.
            if (now - lastMilCheck >= 30_000) {
                lastMilCheck = now
                val status = link.exclusive {
                    useDefaultHeader()
                    ObdResponseParser.mode01Values(ObdResponseParser.messages(send("0101", 1_000)), PidCatalog.mode01ByteCounts)[0x01]
                }
                val count = status?.let { it[0].toInt() and 0x7F }
                if (count != null && count != lastMilCount) {
                    lastMilCount = count
                    runCatching { readCodesInternal(link) }
                }
            }
            cycle++
        }
    }

    private fun logTrip(now: Long) {
        val r = _readings.value
        fun fresh(id: String) = r[id]?.takeIf { now - it.timestamp < STALE_MS }?.value
        val rpm = fresh(PidCatalog.RPM) ?: return
        val speed = fresh(PidCatalog.SPEED) ?: return
        val coolant = fresh(PidCatalog.COOLANT) ?: return
        val throttle = fresh(PidCatalog.THROTTLE) ?: 0.0
        val fuel = PidCatalog.fuelRateLph(fresh(PidCatalog.FUEL_RATE), fresh(PidCatalog.MAF))
        recorder.record(
            TripMetricLog(
                timestamp = now,
                rpm = rpm.roundToInt(),
                speed = speed.roundToInt(),
                coolantTemp = coolant.roundToInt(),
                throttlePosition = throttle.toFloat(),
                fuelRate = fuel?.toFloat()
            ),
            faultCodes = _dtcs.value?.all.orEmpty()
        )
    }

    private fun decodeData(def: PidDefinition, raw: String): ByteArray? {
        if (def.command.startsWith("AT")) return null
        val request = ObdResponseParser.hexBytes(def.command)
        return ObdResponseParser.serviceData(ObdResponseParser.messages(raw), request)
    }

    private fun evaluate(def: PidDefinition, data: ByteArray): Double? = try {
        FormulaEvaluator.evaluate(def.formula, data).takeIf { it.isFinite() }
    } catch (_: FormulaEvaluator.FormulaException) {
        null
    }

    companion object {
        private const val TAG = "ObdManager"
        const val ENGINE_HEADER = "7E0"
        const val BROADCAST_HEADER = "7DF"
        private const val SLOW_EVERY = 5L
        private const val MAX_FAILURES = 5
        private const val STALE_MS = 5_000L
        const val DISPLAY_INTERVAL_MS = 100L

        private val FREEZE_FRAME_CORE = setOf(
            PidCatalog.RPM, PidCatalog.SPEED, PidCatalog.COOLANT, PidCatalog.ENGINE_LOAD, PidCatalog.THROTTLE,
            PidCatalog.INTAKE_TEMP, "01_06", "01_07", "01_0B"
        )

        /** Needed for logging, the HUD and KITT's alerts regardless of what's on screen. */
        val ALWAYS_POLLED = setOf(
            PidCatalog.RPM, PidCatalog.SPEED, PidCatalog.COOLANT, PidCatalog.THROTTLE, PidCatalog.ADAPTER_VOLTAGE,
            // ECU supply voltage: accurate battery/charging reading for alerts and the status strip.
            PidCatalog.MODULE_VOLTAGE,
            // Trip fuel economy; unsupported ones are skipped by the supported-PID filter.
            PidCatalog.FUEL_RATE, PidCatalog.MAF
        )

        fun formatValue(value: Double, def: PidDefinition): String {
            val span = def.max - def.min
            return when {
                span <= 2 -> "%.2f".format(Locale.US, value)
                span <= 30 || def.quantity == com.aiconsole.knightriderlauncher.obd.Quantity.VOLTAGE ->
                    "%.1f".format(Locale.US, value)
                else -> value.roundToInt().toString()
            }
        }
    }
}
