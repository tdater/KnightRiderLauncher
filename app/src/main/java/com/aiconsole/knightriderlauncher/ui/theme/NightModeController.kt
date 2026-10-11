package com.aiconsole.knightriderlauncher.ui.theme

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.data.settings.NightMode
import com.aiconsole.knightriderlauncher.nav.SunTimes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Decides whether the dash should be dimmed for night driving. In Auto it follows the head
 * unit's ambient light sensor (with hysteresis so headlights and street lamps don't flicker
 * it), or the sun (sunset to sunrise at the vehicle's position; the clock, 19:00–07:00,
 * without a position) on units without one. The sensor is only registered while
 * the launcher is on screen.
 */
class NightModeController(
    context: Context,
    settings: KittSettings,
    scope: CoroutineScope,
    /** Where the vehicle is, for sunset and sunrise; the clock is the fallback. */
    private val position: () -> Pair<Double, Double>? = { null }
) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val dark = MutableStateFlow(isNightByClock())
    private var listening = false

    val night: StateFlow<Boolean> = combine(settings.nightMode.state, dark) { mode, isDark ->
        when (mode) {
            NightMode.Off -> false
            NightMode.On -> true
            NightMode.Auto -> isDark
        }
    }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        if (lightSensor == null) {
            scope.launch {
                while (true) {
                    dark.value = isNightByClock()
                    delay(60_000)
                }
            }
        }
    }

    /** Called from the activity's onStart/onStop. */
    fun setActive(active: Boolean) {
        val sensor = lightSensor ?: return
        if (active && !listening) {
            listening = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
        } else if (!active && listening) {
            sensorManager?.unregisterListener(this)
            listening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val lux = event.values.firstOrNull() ?: return
        dark.value = NightModeRules.nextDark(dark.value, lux)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun isNightByClock(): Boolean {
        val now = System.currentTimeMillis()
        position()?.let { (lat, lon) -> SunTimes.isNight(now, lat, lon)?.let { return it } }
        return NightModeRules.isNightHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }
}

/** Pure thresholds, unit-tested. */
object NightModeRules {
    const val DARK_BELOW_LUX = 8f
    const val LIGHT_ABOVE_LUX = 40f

    fun nextDark(wasDark: Boolean, lux: Float): Boolean = when {
        lux < DARK_BELOW_LUX -> true
        lux > LIGHT_ABOVE_LUX -> false
        else -> wasDark
    }

    fun isNightHour(hour: Int): Boolean = hour >= 19 || hour < 7
}
