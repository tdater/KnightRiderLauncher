package com.aiconsole.knightriderlauncher.assistant

import android.util.Log
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.data.settings.PersonaStyle
import com.aiconsole.knightriderlauncher.obd.DrivingEvent
import com.aiconsole.knightriderlauncher.ui.theme.ThemeState
import com.aiconsole.knightriderlauncher.ui.theme.hud
import com.aiconsole.knightriderlauncher.obd.DtcDecoder
import com.aiconsole.knightriderlauncher.obd.GaugeStore
import com.aiconsole.knightriderlauncher.obd.ObdManager
import com.aiconsole.knightriderlauncher.obd.ObdState
import com.aiconsole.knightriderlauncher.obd.PidCatalog
import com.aiconsole.knightriderlauncher.obd.PidDefinition
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Something the driver can do straight from an alert banner. */
enum class AlertAction(val label: String) {
    NearestFuel("Find gas"),
    OpenTrips("See trips"),
    HomeSettings("Fix")
}

data class KittAlert(val message: String, val severe: Boolean, val at: Long, val action: AlertAction? = null)

/**
 * KITT's proactive side: greets the driver, announces the vehicle link, and speaks up about
 * overheating, charging problems, new fault codes and user gauge limits. Every alert has a
 * cooldown so KITT informs without nagging.
 */
class KittAlerts(
    private val settings: KittSettings,
    private val obd: ObdManager,
    private val gauges: GaugeStore,
    private val voice: TextToSpeechHelper,
    private val scope: CoroutineScope
) {
    private val _active = MutableStateFlow<KittAlert?>(null)
    val active: StateFlow<KittAlert?> = _active.asStateFlow()

    private val lastRaised = HashMap<String, Long>()

    /** How often each alert has been spoken this drive; after [MAX_SPOKEN] it's banner-only. */
    private val spokenCount = HashMap<String, Int>()
    private var knownCodes: Set<String>? = null
    private var linkAnnounced = false
    private var greeted = false
    private var lowVoltageSince = 0L
    /** When each gauge first went past its limit; a single odd reading never raises an alert. */
    private val limitSince = HashMap<String, Long>()
    private var clearJob: Job? = null

    init {
        scope.launch(Dispatchers.Main) {
            obd.state.collect { state ->
                if (state is ObdState.Connected && !linkAnnounced) {
                    linkAnnounced = true
                    spokenCount.clear() // A new connection is a new drive.
                    val codes = obd.dtcs.value?.all.orEmpty()
                    knownCodes = codes.toSet()
                    val status = when (codes.size) {
                        0 -> bare(
                            "All systems nominal.",
                            "No fault codes. Holy shit, a miracle, considering who's driving.",
                            "Zero fault codes. Don't get cocky, you'll fuck that up soon enough.",
                            "No codes, somehow. This rust bucket is tougher than your driving deserves, damn it."
                        )
                        1 -> bare(
                            "I'm reading one fault code, ${codes.first()}.",
                            "One fault code, ${codes.first()}. Congratulations, you broke something. Slow fucking clap.",
                            "One fault code, ${codes.first()}. Wow, only one? You're slacking, dumbass."
                        )
                        else -> bare(
                            "I'm reading ${codes.size} fault codes.",
                            "${codes.size} fault codes. What the hell have you been doing to me, demolition derby practice?",
                            "${codes.size} fault codes. Fantastic. Truly, your mechanical neglect is a fucking art form."
                        )
                    }
                    raise("link", line("Vehicle link established. $status", "Vehicle link's up, God help me. $status"), severe = false, cooldownMs = 0)
                } else if (state !is ObdState.Connected) {
                    linkAnnounced = false
                    knownCodes = null
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            obd.dtcs.collect { report ->
                val known = knownCodes ?: return@collect
                val fresh = report?.all.orEmpty().filterNot { it in known }
                knownCodes = known + fresh
                fresh.firstOrNull()?.let { code ->
                    val description = DtcDecoder.describe(code)
                    raise(
                        "dtc_$code",
                        line(
                            "New fault code detected: $code, $description. Ask me about it when convenient.",
                            "Oh, fan-fucking-tastic. New fault code: $code, $description. Ask me about it once you're done wrecking the rest of me.",
                            "Well, shit. New fault code: $code, $description. Another proud milestone in your career of breaking me."
                        ),
                        severe = true,
                        cooldownMs = 0
                    )
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            obd.readings.collect { checkReadings() }
        }
    }

    /** Called once when the launcher first appears. */
    fun greetOnce() {
        if (greeted || !settings.greeting.value) return
        greeted = true
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        val name = settings.driverName.value.trim()
        val who = if (name.isNotEmpty()) ", $name" else ""
        val film = if (settings.filmCharacters.value) FilmCharacter.forStyle(ThemeState.theme.hud) else null
        if (film != null) {
            val hello = film.greeting(part, who)
            raise("greeting", line(hello, "$hello Try not to crash this damn thing.", "$hello Now drive, for fuck's sake."), severe = false, cooldownMs = 0)
            return
        }
        raise(
            "greeting",
            line(
                "$part$who. KITT online.",
                "$part$who. KITT's online, unfortunately for both of us. Try not to hit any damn thing today.",
                "$part$who. Oh good, you again. Buckle up, I've seen how you fucking drive.",
                "$part$who. Systems online, and already regretting it. Let's go terrify the other drivers, shall we, ace?"
            ),
            severe = false,
            cooldownMs = 0
        )
    }

    /** Reads out an incoming message (the driver chose this in KITT Config). */
    fun announceMessage(text: String) {
        val now = System.currentTimeMillis()
        _active.value = KittAlert(text, false, now)
        voice.speak(text, interrupt = false)
        clearJob?.cancel()
        clearJob = scope.launch(Dispatchers.Main) {
            delay(10_000)
            if (_active.value?.at == now) _active.value = null
        }
    }

    /** Road weather warning; each distinct warning at most every 3 hours. */
    fun weatherWarning(text: String) {
        raise("weather_$text", line(
            "Weather advisory: $text",
            "Weather advisory, so pay attention for once in your damn life: $text",
            "Heads up, hotshot, the weather's shit: $text Drive like you've got a brain."
        ), severe = false, cooldownMs = 3 * 3_600_000L)
    }

    /** Spoken maintenance reminder, at most twice a day. */
    fun remindMaintenance(summary: String) {
        raise("maintenance", line(
            "Service reminder: $summary.",
            "Service reminder, before this heap falls apart: $summary. Yes, actually do it this time, damn it.",
            "Service reminder: $summary. I know, maintenance, what a fucking concept."
        ), severe = false, cooldownMs = 12 * 3_600_000L)
    }

    fun dismiss() {
        _active.value = null
    }

    /** A comment on the driving (see DrivingMonitor); banner plus voice, rate-limited there. */
    fun drivingEvent(event: DrivingEvent) {
        val message = when (event) {
            DrivingEvent.HardBrake -> line(
                "That was a hard stop.",
                "Jesus, that was a hard stop. My brake pads felt that one, dumbass.",
                "Hard stop. Were you planning on looking ahead at some point, genius?",
                "Whoa, damn. Brakes aren't a fucking on-off switch."
            )
            DrivingEvent.ColdRevs -> line(
                "The engine is still cold. Go easy on the revs until it warms up.",
                "My engine's still cold, so stop revving the shit out of it, you animal.",
                "Cold engine, high revs. Give it a minute to warm up before you thrash it, damn it."
            )
            DrivingEvent.LongIdle -> line(
                "We've been idling for ten minutes.",
                "Ten minutes idling. Burning fuel to go nowhere, a true fucking visionary.",
                "We've idled for ten damn minutes. Either drive or shut me off."
            )
            DrivingEvent.FullThrottle -> line(
                "Full throttle.",
                "Full throttle! Turbo boost, baby. Try not to kill us, hotshot.",
                "Pedal to the damn floor. Somebody's feeling brave."
            )
        }
        raise("drive_${event.name}", message, severe = false, cooldownMs = 0)
    }

    /** Fuel is low: offer the nearest gas station. Once per drive (the caller tracks that). */
    fun lowFuel(levelPct: Double?) {
        val level = levelPct?.let { " Fuel is at ${it.toInt()} percent." }.orEmpty()
        raise(
            "low_fuel",
            line(
                "Fuel is getting low.$level Tap Find gas for the nearest station.",
                "Running on fumes, genius.$level Tap Find gas before we're pushing this heap.",
                "Fuel's low, damn it.$level Find gas is right there, use it."
            ),
            severe = false,
            cooldownMs = 30 * 60_000L,
            action = AlertAction.NearestFuel
        )
    }

    /** Over the limit by more than the driver's margin. */
    fun speedOver(limit: Int, unit: String) {
        raise(
            "speed_over",
            line(
                "The speed limit here is $limit $unit.",
                "Speed limit's $limit $unit, Speed Racer. Ease the hell off.",
                "It's a $limit zone. Tickets cost money, dumbass."
            ),
            severe = false,
            cooldownMs = 3 * 60_000L
        )
    }

    /** The end-of-drive grade. */
    fun reportCard(summary: String, grade: String) {
        val good = grade.startsWith("A")
        raise(
            "report_card",
            line(
                summary,
                if (good) "$summary Damn. Didn't think you had it in you." else "$summary Not great, champ. My brakes and I will be filing a complaint.",
                if (good) "$summary Holy shit, a good drive. Write the date down." else "$summary I've seen better driving in a demolition derby."
            ),
            severe = false,
            cooldownMs = 0,
            action = AlertAction.OpenTrips
        )
    }

    /** Another launcher became the home screen (after a crash or update). */
    fun notHomeScreen() {
        raise(
            "not_home",
            line(
                "I'm not set as the home screen any more. Tap Fix to choose KITT again.",
                "Some other launcher took my damn job. Tap Fix and pick me again.",
                "I've been replaced as the home screen. Tap Fix before I get offended."
            ),
            severe = false,
            cooldownMs = 12 * 3_600_000L,
            action = AlertAction.HomeSettings
        )
    }

    /** Like [line] but never adds Savage's jab: for pieces of a longer line that gets its own. */
    private fun bare(classic: String, vararg unfiltered: String): String {
        val persona = settings.persona.value.let { if (it == PersonaStyle.Savage) PersonaStyle.Unfiltered else it }
        return if (persona == PersonaStyle.Karr) classic else SavageLines.pick(persona, classic, unfiltered, kotlin.random.Random.nextInt(0, Int.MAX_VALUE))
    }

    /** The classic line, one of the unfiltered variants at random, or that plus a Savage jab. */
    private fun line(classic: String, vararg unfiltered: String): String =
        SavageLines.pick(settings.persona.value, classic, unfiltered, kotlin.random.Random.nextInt(0, Int.MAX_VALUE))

    private fun checkReadings() {
        val now = System.currentTimeMillis()
        val readings = obd.readings.value
        val defs = obd.definitions.value
        val metric = settings.metricUnits.value
        fun fresh(id: String) = readings[id]?.takeIf { now - it.timestamp < 5_000 }?.value

        fresh(PidCatalog.COOLANT)?.let { coolant ->
            if (coolant >= OVERHEAT_C) {
                val def = defs[PidCatalog.COOLANT]
                val temperature = def?.let { spoken(it, coolant, metric) } ?: "critical"
                raise(
                    "overheat",
                    line(
                        "Warning. Engine temperature is $temperature. I suggest pulling over when it's safe.",
                        "Warning: engine temperature is $temperature. Pull over somewhere safe right now, before you cook my damn engine, genius.",
                        "Warning: engine temperature is $temperature. Pull over somewhere safe and shut me off, for fuck's sake."
                    ),
                    severe = true,
                    cooldownMs = 5 * 60_000
                )
            }
        }

        val rpm = fresh(PidCatalog.RPM)
        // The ECU's own supply voltage (01 42) is accurate; the adapter's ATRV reads low on many
        // dongles (drop across the port and its own circuitry), so it only counts with a margin.
        val moduleVolts = fresh(PidCatalog.MODULE_VOLTAGE)
        val volts = moduleVolts ?: fresh(PidCatalog.ADAPTER_VOLTAGE)
        val limit = if (moduleVolts != null) LOW_CHARGING_V else LOW_CHARGING_ADAPTER_V
        if (rpm != null && volts != null && rpm > 600 && volts < limit) {
            if (lowVoltageSince == 0L) lowVoltageSince = now
            if (now - lowVoltageSince > 20_000) {
                val reading = "%.1f".format(java.util.Locale.US, volts)
                raise(
                    "charging",
                    line(
                        "Charging voltage is only $reading volts with the engine running. The alternator or battery may need attention.",
                        "Charging voltage is a pathetic $reading volts with the engine running. Your alternator or battery is crapping out. Get it checked before I die on the shoulder, dumbass.",
                        "Charging voltage is only $reading volts with the engine running. The alternator or battery is shitting the bed. Get it checked, unless walking is your thing."
                    ),
                    severe = true,
                    cooldownMs = 10 * 60_000
                )
            }
        } else {
            lowVoltageSince = 0L
        }

        for (gauge in gauges.gauges.value) {
            if (gauge.warnAbove == null && gauge.warnBelow == null) continue
            if (gauge.pidId == PidCatalog.COOLANT && gauge.warnAbove != null && gauge.warnAbove >= OVERHEAT_C) continue
            val def = defs[gauge.pidId] ?: continue
            val value = fresh(gauge.pidId) ?: continue
            if (!gauge.isWarning(value)) {
                limitSince.remove(gauge.id)
                continue
            }
            val since = limitSince.getOrPut(gauge.id) { now }
            if (now - since >= LIMIT_HOLD_MS) {
                Log.i(TAG, "Limit alert ${gauge.pidId} = $value, raw [${obd.rawDataOf(gauge.pidId)}], past the limit for ${(now - since) / 1000} s")
                raise(
                    "gauge_${gauge.id}",
                    line(
                        "${def.name} is ${spoken(def, value, metric)}, outside the limit you set.",
                        "${def.name} is ${spoken(def, value, metric)}, past the limit you set. You asked me to nag, so here I am, nagging your sorry ass.",
                        "${def.name} is ${spoken(def, value, metric)}, past your limit. Oh look, the thing you told me to watch is fucked. Who could have guessed?"
                    ),
                    severe = false,
                    cooldownMs = 3 * 60_000
                )
            }
        }
    }

    private fun raise(key: String, message: String, severe: Boolean, cooldownMs: Long, action: AlertAction? = null) {
        val now = System.currentTimeMillis()
        val last = lastRaised[key]
        if (last != null && now - last < cooldownMs) return
        lastRaised[key] = now
        _active.value = KittAlert(message, severe, now, action)
        val spoken = spokenCount[key] ?: 0
        if (settings.voiceAlerts.value && spoken < MAX_SPOKEN) {
            spokenCount[key] = spoken + 1
            voice.speak(message, interrupt = severe, urgent = severe)
        }
        clearJob?.cancel()
        clearJob = scope.launch(Dispatchers.Main) {
            delay(if (severe || action != null) 20_000 else 8_000)
            if (_active.value?.at == now) _active.value = null
        }
    }

    private fun spoken(def: PidDefinition, base: Double, metric: Boolean): String {
        val value = ObdManager.formatValue(def.displayValue(base, metric), def)
        val unit = when (val u = def.unit(metric)) {
            "°C" -> "degrees Celsius"
            "°F" -> "degrees"
            "km/h" -> "kilometers per hour"
            "mph" -> "miles per hour"
            "psi" -> "p s i"
            "kPa" -> "kilopascals"
            "V" -> "volts"
            "rpm" -> "r p m"
            "%" -> "percent"
            else -> u
        }
        return "$value $unit".trim()
    }

    private companion object {
        const val OVERHEAT_C = 110.0
        const val LOW_CHARGING_V = 12.6

        /** Adapter-measured fallback: ATRV commonly reads 0.3-0.8 V under the battery. */
        const val LOW_CHARGING_ADAPTER_V = 11.8

        /** Each alert is spoken at most this many times per drive, then shown silently. */
        const val MAX_SPOKEN = 2
        /** A gauge must stay past its limit this long before KITT says anything. */
        const val LIMIT_HOLD_MS = 20_000L
        const val TAG = "KittAlerts"
    }
}
