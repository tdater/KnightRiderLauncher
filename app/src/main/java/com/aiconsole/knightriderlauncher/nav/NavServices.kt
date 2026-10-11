package com.aiconsole.knightriderlauncher.nav

import com.aiconsole.knightriderlauncher.assistant.assistantHttpClient
import com.aiconsole.knightriderlauncher.assistant.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/**
 * Free OpenStreetMap services, no API key: place search through Photon (komoot), driving routes
 * through OSRM (the FOSSGIS server that openstreetmap.org uses, falling back to the OSRM demo
 * server). Both are shared, fair-use services: one driver searching and routing is fine.
 */
class NavServices {

    private val http by lazy { assistantHttpClient() }

    /** Places matching [query], nearest first when [near] is known; [osmTag] filters by kind (e.g. amenity:fuel). */
    suspend fun search(query: String, near: LatLon?, osmTag: String? = null): List<Place> {
        val url = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "10")
            .addQueryParameter("lang", "en")
            .apply {
                near?.let {
                    addQueryParameter("lat", "%.5f".format(Locale.US, it.lat))
                    addQueryParameter("lon", "%.5f".format(Locale.US, it.lon))
                }
                osmTag?.let { addQueryParameter("osm_tag", it) }
            }
            .build()
        return places(JSONObject(get(url)))
    }

    /** A readable address for a point ("123 Main Street, Dallas"), or null. */
    suspend fun reverse(at: LatLon): String? {
        val url = "https://photon.komoot.io/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("lat", "%.6f".format(Locale.US, at.lat))
            .addQueryParameter("lon", "%.6f".format(Locale.US, at.lon))
            .addQueryParameter("lang", "en")
            .build()
        return places(JSONObject(get(url))).firstOrNull()?.let { listOf(it.name, it.detail).filter { s -> s.isNotBlank() }.joinToString(", ") }
    }

    private fun places(root: JSONObject): List<Place> {
        val features = root.optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val f = features.optJSONObject(i) ?: return@mapNotNull null
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val p = f.optJSONObject("properties") ?: JSONObject()
            fun prop(key: String) = p.optString(key).takeIf { it.isNotBlank() }
            val street = listOfNotNull(prop("housenumber"), prop("street")).joinToString(" ").takeIf { it.isNotBlank() }
            val name = prop("name") ?: street ?: return@mapNotNull null
            val detail = listOfNotNull(street.takeIf { it != name }, prop("city") ?: prop("county"), prop("state")).joinToString(", ")
            Place(name, detail, LatLon(coords.optDouble(1), coords.optDouble(0)))
        }.distinctBy { it.name to it.detail }
    }

    /** A driving route, or throws [IOException] when no server could route it. */
    suspend fun route(from: LatLon, to: LatLon): Route {
        val path = "%.6f,%.6f;%.6f,%.6f".format(Locale.US, from.lon, from.lat, to.lon, to.lat)
        var last: IOException? = null
        for (base in ROUTERS) {
            val url = "$base$path".toHttpUrl().newBuilder()
                .addQueryParameter("overview", "full")
                .addQueryParameter("geometries", "polyline6")
                .addQueryParameter("steps", "true")
                .build()
            try {
                return parseRoute(JSONObject(get(url)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                last = e
            } catch (e: JSONException) {
                last = IOException("Unreadable route", e)
            }
        }
        throw last ?: IOException("No route")
    }

    private fun parseRoute(root: JSONObject): Route {
        if (root.optString("code") != "Ok") throw IOException(root.optString("message").ifBlank { "No route found" })
        val route = root.getJSONArray("routes").getJSONObject(0)
        val points = RouteRules.decodePolyline(route.getString("geometry"), 6)
        val steps = ArrayList<RouteStep>()
        val legs = route.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val list = legs.getJSONObject(l).getJSONArray("steps")
            for (s in 0 until list.length()) {
                val step = list.getJSONObject(s)
                val m = step.getJSONObject("maneuver")
                val loc = m.getJSONArray("location")
                steps += RouteStep(
                    type = m.optString("type"),
                    modifier = m.optString("modifier").takeIf { it.isNotBlank() },
                    name = step.optString("name"),
                    ref = step.optString("ref").takeIf { it.isNotBlank() },
                    exit = if (m.has("exit")) m.optInt("exit") else null,
                    location = LatLon(loc.getDouble(1), loc.getDouble(0)),
                    distanceM = step.optDouble("distance", 0.0),
                    durationS = step.optDouble("duration", 0.0)
                )
            }
        }
        return Route(points, steps, route.optDouble("distance", 0.0), route.optDouble("duration", 0.0))
    }

    private suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).await().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body.string()
        }
    }

    private companion object {
        const val USER_AGENT = "KnightRiderLauncher/1.0 (personal car head unit)"
        val ROUTERS = listOf(
            "https://routing.openstreetmap.de/routed-car/route/v1/driving/",
            "https://router.project-osrm.org/route/v1/driving/"
        )
    }
}
