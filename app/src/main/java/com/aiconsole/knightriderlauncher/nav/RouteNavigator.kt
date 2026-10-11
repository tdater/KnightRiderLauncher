package com.aiconsole.knightriderlauncher.nav

import com.aiconsole.knightriderlauncher.assistant.TextToSpeechHelper
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

enum class NavStatus { Idle, Routing, Preview, Navigating, Arrived, Error }

data class NavState(
    val status: NavStatus = NavStatus.Idle,
    val destination: Place? = null,
    val route: Route? = null,
    val progress: RouteProgress? = null,
    val message: String? = null
) {
    val nextStep: RouteStep? get() = progress?.let { p -> route?.steps?.getOrNull(p.nextStep) }

    /** Time left, scaled from the route's estimate by the distance left. */
    val remainingS: Double?
        get() {
            val r = route ?: return null
            val p = progress ?: return r.durationS
            return if (r.distanceM > 0) r.durationS * p.remainingM / r.distanceM else 0.0
        }
}

/**
 * Turn-by-turn navigation on the home map: routes from the car's position, follows GPS fixes
 * along the route, speaks each maneuver twice (early, then at the turn), recalculates after
 * leaving the route, and announces arrival. Runs while the launcher is on screen (GPS is
 * only on then). Main thread.
 */
class RouteNavigator(
    private val gps: GpsTracker,
    private val services: NavServices,
    private val settings: KittSettings,
    private val voice: TextToSpeechHelper,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(NavState())
    val state: StateFlow<NavState> = _state.asStateFlow()

    private var routing: Job? = null
    private var following: Job? = null
    private var cumulative = DoubleArray(0)
    private var stepPoints = IntArray(0)
    private var lastIndex = 0
    private val spoken = HashMap<Int, Int>()
    private var offRouteFixes = 0
    private var lastReroute = 0L

    val active: Boolean get() = _state.value.status == NavStatus.Navigating

    fun recents(): List<Place> = RouteRules.decodeRecents(settings.navRecents.value)

    suspend fun position(): LatLon? = withContext(Dispatchers.Main) { gps.position()?.let { LatLon(it.first, it.second) } }

    /** Places for the search box; throws [IOException] when offline. */
    suspend fun search(query: String): List<Place> = try {
        services.search(query, position())
    } catch (e: JSONException) {
        throw IOException("Unreadable search results", e)
    }

    /** Shows the route to [place] for the driver to start (or hand to another app). */
    fun preview(place: Place) {
        routing?.cancel()
        routing = scope.launch(Dispatchers.Main) { computeRoute(place) }
    }

    /**
     * Starts guidance on the previewed route and returns the route announcement, spoken here
     * unless [speak] is false (the assistant speaks its own reply).
     */
    fun start(speak: Boolean = true): String? {
        val s = _state.value
        val route = s.route ?: return null
        val place = s.destination ?: return null
        if (s.status != NavStatus.Preview) return null
        settings.navRecents.set(RouteRules.encodeRecents(RouteRules.remember(recents(), place)))
        spoken.clear()
        offRouteFixes = 0
        lastIndex = 0
        _state.value = s.copy(status = NavStatus.Navigating, message = null)
        val line = "Route set to ${place.name}: ${RouteRules.spokenDistance(route.distanceM, metric())}, about ${RouteRules.duration(route.durationS)}."
        if (speak) say(RouteRules.Speech.Start, line)
        follow()
        return line
    }

    /** Voice command: find [query], route there and start. Returns what KITT says. */
    suspend fun navigateTo(query: String): String {
        val place = try {
            search(RouteRules.destinationQuery(query)).firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return "I can't search for places without a data connection."
        } ?: return "I couldn't find $query."
        routing?.cancel()
        computeRoute(place)
        if (_state.value.status != NavStatus.Preview) return _state.value.message ?: "I couldn't find a route to ${place.name}."
        return start(speak = false) ?: "I couldn't start the route to ${place.name}."
    }

    /**
     * Routes to the nearest gas station (OpenStreetMap amenity=fuel) and starts guidance.
     * Returns what KITT says.
     */
    suspend fun navigateToNearestFuel(): String {
        val here = position() ?: return "I need a GPS position to find a gas station."
        val place = try {
            services.search("gas station", here, osmTag = "amenity:fuel")
                .minByOrNull { RouteRules.distanceM(here, it.location) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return "I can't search for gas stations without a data connection."
        } catch (_: JSONException) {
            return "The map search sent back something unreadable. Try again in a moment."
        } ?: return "I couldn't find a gas station nearby."
        routing?.cancel()
        computeRoute(place)
        if (_state.value.status != NavStatus.Preview) return _state.value.message ?: "I couldn't find a route to ${place.name}."
        return start(speak = false) ?: "I couldn't start the route to ${place.name}."
    }

    /** Routes to [spot] (where the vehicle was parked) and shows the preview. */
    fun previewTo(name: String, at: LatLon) = preview(Place(name, "", at))

    /** Street address of [at], or null offline. */
    suspend fun addressOf(at: LatLon): String? = try {
        services.reverse(at)
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        null
    } catch (_: JSONException) {
        null
    }

    fun stop() {
        routing?.cancel()
        following?.cancel()
        _state.value = NavState()
    }

    private suspend fun computeRoute(place: Place) {
        following?.cancel()
        _state.value = NavState(NavStatus.Routing, destination = place)
        val from = position()
        if (from == null) {
            _state.value = NavState(NavStatus.Error, place, message = "Waiting for a GPS position. Try again in a moment.")
            return
        }
        try {
            val route = services.route(from, place.location)
            setRoute(route)
            _state.value = NavState(NavStatus.Preview, place, route, RouteRules.progress(route, cumulative, stepPoints, from, 0))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            _state.value = NavState(NavStatus.Error, place, message = "No route to ${place.name}: ${e.message ?: "routing service unavailable"}.")
        }
    }

    private fun setRoute(route: Route) {
        cumulative = RouteRules.cumulative(route.points)
        stepPoints = RouteRules.stepPoints(route)
        lastIndex = 0
    }

    private fun follow() {
        following?.cancel()
        following = scope.launch(Dispatchers.Main) {
            gps.fix.filterNotNull().collect { fix ->
                val s = _state.value
                val route = s.route ?: return@collect
                if (s.status != NavStatus.Navigating) return@collect
                val here = LatLon(fix.lat, fix.lon)
                val p = RouteRules.progress(route, cumulative, stepPoints, here, lastIndex)
                lastIndex = p.pointIndex
                val destination = s.destination
                if (p.remainingM < ARRIVED_M || (destination != null && RouteRules.distanceM(here, destination.location) < ARRIVED_M)) {
                    arrive(s)
                    return@collect
                }
                _state.value = s.copy(progress = p)
                prompt(route, p, fix.speedKmh ?: 0.0)
                checkOffRoute(p, here, s)
            }
        }
    }

    private fun prompt(route: Route, p: RouteProgress, speedKmh: Double) {
        val step = route.steps.getOrNull(p.nextStep) ?: return
        if (step.type == "arrive") return // Arrival has its own announcement.
        val level = RouteRules.promptLevel(p.toNextM, speedKmh) ?: return
        if ((spoken[p.nextStep] ?: 0) >= level) return
        spoken[p.nextStep] = level
        val instruction = RouteRules.instruction(step)
        if (level == 1) say(RouteRules.Speech.Early, "In ${RouteRules.spokenDistance(p.toNextM, metric())}, ${instruction.replaceFirstChar { it.lowercase() }}.")
        else say(RouteRules.Speech.Now, "$instruction.")
    }

    private fun checkOffRoute(p: RouteProgress, here: LatLon, s: NavState) {
        offRouteFixes = if (RouteRules.isOffRoute(p.offRouteM)) offRouteFixes + 1 else 0
        val now = System.currentTimeMillis()
        if (offRouteFixes < OFF_ROUTE_FIXES || now - lastReroute < REROUTE_GAP_MS) return
        val place = s.destination ?: return
        lastReroute = now
        offRouteFixes = 0
        say(RouteRules.Speech.Reroute, "Recalculating.")
        routing?.cancel()
        routing = scope.launch(Dispatchers.Main) {
            try {
                val route = services.route(here, place.location)
                setRoute(route)
                spoken.clear()
                if (_state.value.status == NavStatus.Navigating) _state.value = _state.value.copy(route = route, progress = null)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Keep guiding on the old route; the next off-route check tries again.
            }
        }
    }

    private fun arrive(s: NavState) {
        following?.cancel()
        _state.value = s.copy(status = NavStatus.Arrived)
        say(RouteRules.Speech.Arrive, "You have arrived" + (s.destination?.let { " at ${it.name}" } ?: "") + ".")
        scope.launch(Dispatchers.Main) {
            delay(ARRIVED_SHOW_MS)
            if (_state.value.status == NavStatus.Arrived) _state.value = NavState()
        }
    }

    private fun say(kind: RouteRules.Speech, plain: String) {
        if (settings.navVoice.value) voice.speak(RouteRules.spoken(kind, plain, unfiltered(), Random.nextInt()), interrupt = false)
    }

    private fun unfiltered() = settings.persona.value.rude

    private fun metric() = settings.metricUnits.value

    private companion object {
        const val ARRIVED_M = 30.0
        const val OFF_ROUTE_FIXES = 3
        const val REROUTE_GAP_MS = 20_000L
        const val ARRIVED_SHOW_MS = 10_000L
    }
}
