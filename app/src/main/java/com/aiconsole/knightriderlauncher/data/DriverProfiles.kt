package com.aiconsole.knightriderlauncher.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Which settings belong to a driver and which to the vehicle. Plain Kotlin. */
object ProfileRules {

    /** Vehicle-level keys every driver shares: adapters, identity, odometer, service, fuel, widgets. */
    private val SHARED_PREFIXES = listOf("obd_", "vehicle_", "odometer_", "distance_", "maintenance_", "fuel_", "tank_", "widget_")

    fun isPersonal(key: String): Boolean = SHARED_PREFIXES.none { key.startsWith(it) }

    /** File-safe profile name, or null if nothing usable is left. */
    fun sanitize(name: String): String? =
        name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").replace(Regex("\\s+"), " ").take(24).takeIf { it.isNotBlank() }
}

/**
 * Driver profiles: each keeps its own look, KITT persona and voice, gauges, favourites and
 * saved sites. Switching saves the current driver's setup, loads the other's, and restarts
 * the launcher so everything picks it up.
 */
class DriverProfiles(private val context: Context) {

    private val dir = File(context.filesDir, "profiles")
    private val meta = context.getSharedPreferences("kitt_profiles", Context.MODE_PRIVATE)

    val active: String? get() = meta.getString(ACTIVE, null)

    fun names(): List<String> = dir.listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension }.sorted()

    /** Saves the current setup as [name] and makes it the active profile. */
    fun saveCurrent(name: String) {
        dir.mkdirs()
        val root = JSONObject()
            .put(SETTINGS, LauncherBackup.snapshot(context, SETTINGS, ProfileRules::isPersonal))
            .put(GAUGES, LauncherBackup.snapshot(context, GAUGES) { true })
        File(dir, "$name.json").writeText(root.toString())
        meta.edit { putString(ACTIVE, name) }
    }

    /** Saves the active profile, then loads [name]. The caller restarts the launcher. */
    fun switchTo(name: String) {
        active?.let(::saveCurrent)
        val root = try {
            JSONObject(File(dir, "$name.json").readText())
        } catch (e: JSONException) {
            throw IOException("Profile $name is damaged", e)
        }
        LauncherBackup.restore(context, SETTINGS, root.getJSONObject(SETTINGS), ProfileRules::isPersonal)
        LauncherBackup.restore(context, GAUGES, root.getJSONObject(GAUGES)) { true }
        // Written synchronously: the launcher restarts right after a switch.
        meta.edit(commit = true) { putString(ACTIVE, name) }
    }

    fun delete(name: String) {
        File(dir, "$name.json").delete()
        if (active == name) meta.edit { remove(ACTIVE) }
    }

    private companion object {
        const val ACTIVE = "active"
        const val SETTINGS = "kitt_settings"
        const val GAUGES = "kitt_gauges"
    }
}
