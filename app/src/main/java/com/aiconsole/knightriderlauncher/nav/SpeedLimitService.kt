package com.aiconsole.knightriderlauncher.nav

import android.util.Log
import com.aiconsole.knightriderlauncher.assistant.await
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

/**
 * The speed limit of the road the vehicle is on, from OpenStreetMap through the public Overpass
 * API (no key). Looks up only while the launcher shows, the vehicle is moving and the setting
 * is on, at most every [LOOKUP_GAP_MS] or [LOOKUP_DISTANCE_M]. Roads without a mapped limit
 * show nothing rather than a guess.
 */
class SpeedLimitService(
    private val gps: GpsTracker,
    private val settings: KittSettings,
    private val scope: CoroutineScope
) {
    private val _limitKmh = MutableStateFlow<Double?>(null)

    /** The current road's limit in km/h, or null when unknown. */
    val limitKmh: StateFlow<Double?> = _limitKmh.asStateFlow()

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    }
    private var job: Job? = null
    private var lastAt: LatLon? = null
    private var lastLookup = 0L

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(POLL_MS)
                if (!settings.speedLimits.value) {
                    _limitKmh.value = null
                    continue
                }
                val fix = gps.fix.value ?: continue
                if (System.currentTimeMillis() - fix.timestamp > 5_000 || (fix.speedKmh ?: 0.0) < MOVING_KMH) continue
                val here = LatLon(fix.lat, fix.lon)
                val now = System.currentTimeMillis()
                val moved = lastAt?.let { RouteRules.distanceM(it, here) } ?: Double.MAX_VALUE
                if (now - lastLookup < LOOKUP_GAP_MS && moved < LOOKUP_DISTANCE_M) continue
                lastLookup = now
                lastAt = here
                _limitKmh.value = try {
                    lookUp(here)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Log.i(TAG, "Speed limit lookup failed: ${e.message}")
                    _limitKmh.value // Keep the last known limit through a dropout.
                } catch (e: JSONException) {
                    null
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun lookUp(at: LatLon): Double? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("data", SpeedLimits.overpassQuery(at.lat, at.lon)).build()
        val request = Request.Builder().url(OVERPASS).post(body).header("User-Agent", "KITTLauncher (personal car head unit)").build()
        val text = http.newCall(request).await().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body.string()
        }
        val elements = JSONObject(text).optJSONArray("elements") ?: return@withContext null
        (0 until elements.length()).firstNotNullOfOrNull { i ->
            SpeedLimits.parseKmh(elements.optJSONObject(i)?.optJSONObject("tags")?.optString("maxspeed"))
        }
    }

    private companion object {
        const val TAG = "SpeedLimits"
        const val OVERPASS = "https://overpass-api.de/api/interpreter"
        const val POLL_MS = 3_000L
        const val LOOKUP_GAP_MS = 20_000L
        const val LOOKUP_DISTANCE_M = 350.0
        const val MOVING_KMH = 10.0
    }
}
