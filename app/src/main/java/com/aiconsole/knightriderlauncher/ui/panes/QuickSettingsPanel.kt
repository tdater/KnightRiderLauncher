package com.aiconsole.knightriderlauncher.ui.panes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.data.settings.NightMode
import com.aiconsole.knightriderlauncher.ui.components.ChoiceRow
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.SegmentedSlider
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittCharcoal
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.LedGreenDim
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.scanner.SidePanelService
import kotlin.math.roundToInt

/**
 * Pull-down quick settings (tap the status strip): media volume, screen brightness, night mode
 * and shortcuts to Bluetooth / Wi-Fi, since head units often hide the system shade.
 */
@Composable
fun QuickSettingsPanel(onClose: () -> Unit, modifier: Modifier = Modifier, onPursuit: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1 }
    var volume by remember { mutableFloatStateOf((audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) / maxVolume.toFloat()) }
    var canWrite by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var brightness by remember { mutableFloatStateOf(currentBrightness(context)) }
    val night by app.settings.nightMode.state.collectAsState()

    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(KittCharcoal)
            .border(1.dp, KittBezel, MaterialTheme.shapes.medium)
            // Taps inside the panel don't fall through to the home screen.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("QUICK SETTINGS", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = ScannerRed)
            KittButton("Close", onClose, Modifier.height(36.dp).width(88.dp), DashGrey)
        }
        Label("Volume ${(volume * 100).roundToInt()}%")
        SegmentedSlider(
            value = volume,
            onValueChange = { v ->
                volume = v
                audio?.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVolume).roundToInt(), 0)
            },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            segments = maxVolume.coerceAtMost(30),
            activeColor = LedGreen,
            inactiveColor = LedGreenDim
        )
        Label("Brightness")
        if (canWrite) {
            SegmentedSlider(
                value = brightness,
                onValueChange = { v ->
                    brightness = v
                    runCatching {
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v * 255).roundToInt().coerceIn(5, 255))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp)
            )
        } else {
            KittButton(
                "Allow brightness control",
                {
                    open(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri()))
                    canWrite = Settings.System.canWrite(context)
                },
                Modifier.fillMaxWidth().height(44.dp),
                DashAmber
            )
        }
        var panelMessage by remember { mutableStateOf<String?>(null) }
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Pursuit mode", onPursuit, Modifier.weight(1f).fillMaxHeight(), ScannerRed, active = true)
            KittButton(
                "Maps + KITT",
                { panelMessage = SidePanelService.startWithMaps(context, app.settings.sidePanelNavApp.value) },
                Modifier.weight(1f).fillMaxHeight(),
                LedGreen,
                active = true
            )
        }
        panelMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DashAmber) }
        Label("Night mode")
        ChoiceRow(NightMode.entries, night, { it.label }, { app.settings.nightMode.set(it) })
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Bluetooth", { open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
            KittButton(
                "Wi-Fi",
                {
                    open(
                        context,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
                    )
                },
                Modifier.weight(1f).fillMaxHeight(),
                DashAmber
            )
            KittButton("Sound", { open(context, Intent(Settings.ACTION_SOUND_SETTINGS)) }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
            KittButton("Settings", { open(context, Intent(Settings.ACTION_SETTINGS)) }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = DashGrey)
}

private fun currentBrightness(context: Context): Float =
    runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
