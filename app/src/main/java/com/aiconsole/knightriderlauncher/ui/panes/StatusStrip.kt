package com.aiconsole.knightriderlauncher.ui.panes

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.data.Compass
import com.aiconsole.knightriderlauncher.nav.WeatherRules
import com.aiconsole.knightriderlauncher.obd.ObdState
import com.aiconsole.knightriderlauncher.obd.PidCatalog
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.FilmHudText
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.theme.ThemeState
import com.aiconsole.knightriderlauncher.ui.theme.hud
import androidx.compose.foundation.layout.fillMaxHeight
import com.aiconsole.knightriderlauncher.ui.theme.TimeCircuitsStrip
import com.aiconsole.knightriderlauncher.ui.theme.HudStyle
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class Radios(val bluetooth: Boolean, val wifi: Boolean, val gps: Boolean)

/** One glanceable row: clock, heading, Bluetooth, Wi-Fi, GPS, OBD link and battery voltage. */
@Composable
fun StatusStrip(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val obd by app.obd.state.collectAsState()
    val readings by app.obd.displayReadings.collectAsState()
    val fix by app.gps.fix.collectAsState()
    val weather by app.weather.weather.collectAsState()
    val metric by app.settings.metricUnits.state.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val radios by produceState(Radios(false, false, false)) {
        while (true) {
            value = radios(context, app.gps.gpsEnabled())
            delay(5_000)
        }
    }
    val volts = readings[PidCatalog.MODULE_VOLTAGE]?.value ?: readings[PidCatalog.ADAPTER_VOLTAGE]?.value

    Row(
        modifier.fillMaxWidth().height(28.dp).background(KittBlack).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (ThemeState.theme.hud == HudStyle.Future) {
            // Back to the Future: the time circuits replace the clock.
            val route by app.navigator.state.collectAsState()
            val departed by app.driveAssistant.departedAt.collectAsState()
            val arrival = route.remainingS?.takeIf { route.status == com.aiconsole.knightriderlauncher.nav.NavStatus.Navigating }?.let { now + (it * 1000).toLong() }
            TimeCircuitsStrip(arrival, now, departed, Modifier.fillMaxHeight().padding(vertical = 2.dp))
        } else Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)), style = MaterialTheme.typography.labelLarge, color = DashWhite)
        ThemeState.theme.hud?.takeIf { it != HudStyle.Future }?.let { style ->
            val lines = FilmHudText.lines(style)
            Text(
                "${FilmHudText.brand(style)} · ${lines[((now / 8_000) % lines.size).toInt()]}",
                style = MaterialTheme.typography.labelSmall,
                color = ScannerRed,
                maxLines = 1
            )
        }
        fix?.bearing?.let { Text("HDG ${Compass.cardinal(it)}", style = MaterialTheme.typography.labelMedium, color = DashAmber) }
        weather?.let { w ->
            val temp = if (metric) w.tempC else w.tempC * 9 / 5 + 32
            Text(
                "%.0f° %s".format(Locale.US, temp, WeatherRules.describe(w.code).uppercase()),
                style = MaterialTheme.typography.labelMedium,
                color = if (WeatherRules.roadWarning(w) != null) ScannerRed else DashWhite,
                maxLines = 1
            )
        }
        Spacer(Modifier.weight(1f))
        Text("▼ QUICK", style = MaterialTheme.typography.labelSmall, color = DashGrey)
        Indicator("BT", radios.bluetooth)
        Indicator("WIFI", radios.wifi)
        Indicator("GPS", radios.gps && fix != null)
        Indicator("OBD", obd is ObdState.Connected, if (obd is ObdState.Error) ScannerRed else null)
        volts?.let {
            Text(
                "%.1fV".format(Locale.US, it),
                style = MaterialTheme.typography.labelMedium,
                color = if (it < 12.0 || it > 15.0) ScannerRed else LedGreen
            )
        }
    }
}

@Composable
private fun Indicator(label: String, on: Boolean, override: Color? = null) {
    val color = override ?: if (on) LedGreen else DashGrey.copy(alpha = 0.5f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun radios(context: Context, gps: Boolean): Radios {
    val bt = try {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val wifi = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    return Radios(bt, wifi, gps)
}
