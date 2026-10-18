package com.aiconsole.knightriderlauncher.ui.diagnostics

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.obd.AdapterConfig
import com.aiconsole.knightriderlauncher.obd.AdapterDiscovery
import androidx.compose.ui.Alignment
import com.aiconsole.knightriderlauncher.obd.BluetoothAddress
import com.aiconsole.knightriderlauncher.obd.AdapterType
import com.aiconsole.knightriderlauncher.obd.ManufacturerProfiles
import com.aiconsole.knightriderlauncher.obd.ObdConnectionService
import com.aiconsole.knightriderlauncher.obd.ObdState
import com.aiconsole.knightriderlauncher.ui.components.ChoiceRow
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.KittToggle
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val PROTOCOLS = listOf(
    "0" to "Auto",
    "6" to "CAN 11/500",
    "7" to "CAN 29/500",
    "8" to "CAN 11/250",
    "9" to "CAN 29/250",
    "1" to "J1850 PWM",
    "2" to "J1850 VPW",
    "3" to "ISO 9141",
    "4" to "KWP slow",
    "5" to "KWP fast"
)

/** Adapter selection, connection control, vehicle identity and manufacturer options. */
@Composable
fun LinkTab(app: KittApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by app.obd.state.collectAsState()
    val hz by app.obd.refreshHz.collectAsState()
    val profile by app.obd.profile.collectAsState()
    val manufacturerChoice by app.settings.manufacturerProfile.state.collectAsState()
    val protocol by app.settings.obdProtocol.state.collectAsState()
    val fastPolling by app.settings.obdFastPolling.state.collectAsState()
    val autoConnect by app.settings.obdAutoConnect.state.collectAsState()
    var saved by remember { mutableStateOf(app.savedAdapter()) }
    var hasPermission by remember { mutableStateOf(AdapterDiscovery.hasPermissions(context)) }
    var paired by remember { mutableStateOf(if (hasPermission) AdapterDiscovery.pairedDevices(context) else emptyList()) }
    val bleFound = remember { mutableStateListOf<AdapterConfig>() }
    val classicFound = remember { mutableStateListOf<AdapterConfig>() }
    var scanning by remember { mutableStateOf<Job?>(null) }
    var report by remember { mutableStateOf(AdapterDiscovery.report(context)) }
    val scanProblems = remember { mutableStateListOf<String>() }
    var scanSummary by remember { mutableStateOf<String?>(null) }
    var showUnnamed by remember { mutableStateOf(false) }
    var manualAddress by remember { mutableStateOf("") }
    var wifiAddress by remember { mutableStateOf("192.168.0.10:35000") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = AdapterDiscovery.hasPermissions(context)
        if (hasPermission) paired = AdapterDiscovery.pairedDevices(context)
        report = AdapterDiscovery.report(context)
    }

    fun requestPermissions() {
        val extra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
        permissionLauncher.launch(AdapterDiscovery.requiredPermissions() + extra)
    }

    fun choose(config: AdapterConfig) {
        app.saveAdapter(config)
        saved = config
        if (state !is ObdState.Idle) ObdConnectionService.start(context) // Reconnect to the new adapter.
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionHeader("Vehicle link", color = LedGreen) }
        item {
            val info = (state as? ObdState.Connected)?.info
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val (status, color) = when (val s = state) {
                    is ObdState.Connected -> "CONNECTED" to LedGreen
                    is ObdState.Connecting -> s.stage.uppercase() to DashAmber
                    is ObdState.Error -> "${s.message} · retry in ${s.retryInSeconds}s" to AlertRed
                    ObdState.Idle -> "NOT CONNECTED" to DashGrey
                }
                Text(status, style = MaterialTheme.typography.titleMedium, color = color)
                Text("Adapter: ${saved?.let { "${it.name} (${it.type.label})" } ?: "none selected"}", style = MaterialTheme.typography.bodyMedium, color = DashWhite)
                if (info != null) {
                    Text("ELM: ${info.elmVersion} · ${info.protocol}", style = MaterialTheme.typography.bodyMedium, color = DashWhite)
                    Text("VIN: ${info.vin ?: "not reported"}", style = MaterialTheme.typography.bodyMedium, color = DashWhite)
                    Text(
                        "Detected: ${info.detectedProfile?.name ?: "unknown make"} · ${info.supportedPidCount} PIDs · " +
                            "%.1f Hz".format(java.util.Locale.US, hz),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DashWhite
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KittButton(
                    label = "Connect",
                    onClick = {
                        if (!hasPermission && saved?.type != AdapterType.WIFI) requestPermissions()
                        else ObdConnectionService.start(context)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = LedGreen,
                    active = true,
                    enabled = saved != null
                )
                KittButton(
                    label = "Disconnect",
                    onClick = { ObdConnectionService.stop(context) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = AlertRed,
                    enabled = state !is ObdState.Idle
                )
            }
        }
        item { KittToggle("Auto-connect", autoConnect, { app.settings.obdAutoConnect.set(it) }, description = "Reconnect whenever the launcher starts.") }

        val savedList = app.savedAdapters()
        if (savedList.size > 1 || (savedList.size == 1 && savedList[0].address != saved?.address)) {
            item { SectionHeader("Saved adapters", color = DashAmber) }
            item { HelpText("If the first one doesn't answer, the launcher tries the next on each retry.") }
            items(savedList, key = { "s_${it.address}" }) { adapter ->
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KittButton(
                        label = "${adapter.name} · ${adapter.type.label}",
                        onClick = { choose(adapter) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = LedGreen,
                        active = saved?.address == adapter.address
                    )
                    KittButton(
                        label = "Forget",
                        onClick = {
                            app.forgetAdapter(adapter)
                            saved = app.savedAdapter()
                        },
                        modifier = Modifier.width(96.dp).fillMaxHeight(),
                        color = AlertRed
                    )
                }
            }
        }

        item { SectionHeader("Bluetooth adapters", color = DashAmber) }
        // Layer 1 first: what Android's Bluetooth looks like to apps on this head unit.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                report.lines().forEach { line ->
                    val bad = line.contains("NOT") || line.contains("OFF") || line.contains("missing") || line.startsWith("Location is off")
                    Text(line, style = MaterialTheme.typography.bodySmall, color = if (bad) AlertRed else DashGrey)
                }
            }
        }
        if (!hasPermission) {
            item {
                KittButton("Allow Bluetooth access", ::requestPermissions, Modifier.fillMaxWidth().height(44.dp), DashAmber, active = true)
            }
        } else {
            item {
                HelpText(
                    "Scan to find your adapter; it doesn't need pairing in Android settings first. " +
                        "If Android asks for a PIN when connecting, it's usually 1234 or 0000."
                )
            }
            if (AdapterDiscovery.scanNeedsLocationEnabled(context)) {
                item { HelpText("Turn on Location: this Android version finds no Bluetooth devices while it's off.", color = AlertRed) }
            }
            items(paired, key = { "p_${it.address}" }) { device ->
                KittButton(
                    label = "${device.name}${if (AdapterDiscovery.isLikelyObd(device.name)) "  · OBD" else ""}",
                    onClick = { choose(device) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    color = LedGreen,
                    active = saved?.address == device.address
                )
            }
            item {
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KittButton(
                        label = if (scanning != null) "Scanning…" else "Scan for adapters",
                        onClick = {
                            bleFound.clear()
                            classicFound.clear()
                            scanProblems.clear()
                            scanSummary = null
                            report = AdapterDiscovery.report(context)
                            paired = AdapterDiscovery.pairedDevices(context)
                            scanning = scope.launch {
                                // One after the other: many head-unit radios refuse a BLE scan
                                // while Classic discovery runs (the scan failed at once).
                                withTimeoutOrNull(8_000) {
                                    AdapterDiscovery.scanBle(context) { scanProblems += it }.collect { found ->
                                        if (bleFound.none { it.address == found.address }) bleFound += found
                                    }
                                }
                                withTimeoutOrNull(15_000) {
                                    AdapterDiscovery.scanClassic(context) { scanProblems += it }.collect { found ->
                                        if (found.type == AdapterType.BLE) {
                                            // Discovery also sees BLE devices: the fallback when the BLE scan is refused.
                                            if (bleFound.none { it.address == found.address }) bleFound += found
                                            return@collect
                                        }
                                        val known = classicFound.any { it.address == found.address } ||
                                            paired.any { it.address == found.address }
                                        if (!known) classicFound += found
                                    }
                                }
                                val unnamed = (classicFound + bleFound).count { AdapterDiscovery.isUnnamed(it.name) }
                                scanSummary = "Scan finished: ${classicFound.size} Bluetooth Classic and ${bleFound.size} BLE devices seen" +
                                    (if (unnamed > 0) " ($unnamed without a name)." else ".") +
                                    if (classicFound.isEmpty() && bleFound.isEmpty()) " Nothing at all usually means this head unit's Bluetooth isn't open to apps; see below." else ""
                                app.obd.logScan(scanSummary.orEmpty() + scanProblems.joinToString(" ", prefix = " "), ok = classicFound.isNotEmpty() || bleFound.isNotEmpty())
                                scanning = null
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = DashAmber,
                        enabled = scanning == null
                    )
                    KittButton(
                        label = "Pair new device",
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: ActivityNotFoundException) {
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = DashAmber
                    )
                }
            }
            scanSummary?.let { item { HelpText(it, color = DashAmber) } }
            items(scanProblems.toList(), key = { "e_$it" }) { HelpText(it, color = AlertRed) }
            items(classicFound.filter { showUnnamed || !AdapterDiscovery.isUnnamed(it.name) }.sortedByDescending { AdapterDiscovery.isLikelyObd(it.name) }, key = { "c_${it.address}" }) { device ->
                KittButton(
                    label = "BT · ${device.name}${if (AdapterDiscovery.isLikelyObd(device.name)) "  · OBD" else ""}",
                    onClick = { choose(device) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    color = LedGreen,
                    active = saved?.address == device.address
                )
            }
            items(bleFound.filter { showUnnamed || !AdapterDiscovery.isUnnamed(it.name) }.sortedByDescending { AdapterDiscovery.isLikelyObd(it.name) }, key = { "b_${it.address}" }) { device ->
                KittButton(
                    label = "BLE · ${device.name}${if (AdapterDiscovery.isLikelyObd(device.name)) "  · OBD" else ""}",
                    onClick = { choose(device) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    color = LedGreen,
                    active = saved?.address == device.address
                )
            }
            val unnamedCount = (classicFound + bleFound).count { AdapterDiscovery.isUnnamed(it.name) }
            if (unnamedCount > 0) {
                item {
                    KittButton(
                        if (showUnnamed) "Hide devices without a name" else "Show $unnamedCount devices without a name",
                        { showUnnamed = !showUnnamed },
                        Modifier.fillMaxWidth().height(40.dp),
                        DashGrey
                    )
                }
            }

            // When the head unit hides the adapter from every list: connect straight to its address.
            item { SectionHeader("Connect by address", color = DashAmber) }
            item {
                HelpText(
                    "If the adapter never shows up, find its address with a Bluetooth scanner app on your phone " +
                        "(for example nRF Connect) while it's plugged in with the ignition on, and enter it here. " +
                        "Veepeak OBDCheck BLE is a BLE adapter: choose BLE. Most ELM327 dongles that ask for a PIN, " +
                        "like the OBDAi, are Classic."
                )
            }
            item {
                val normalized = BluetoothAddress.normalize(manualAddress)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    KittTextField(manualAddress, { manualAddress = it }, "00:1D:A5:68:98:8A", Modifier.weight(1f))
                    KittButton(
                        "Classic",
                        { normalized?.let { choose(AdapterConfig(AdapterType.CLASSIC, it, "OBD $it")) } },
                        Modifier.height(48.dp).width(96.dp),
                        LedGreen,
                        enabled = normalized != null
                    )
                    KittButton(
                        "BLE",
                        { normalized?.let { choose(AdapterConfig(AdapterType.BLE, it, "OBD BLE $it")) } },
                        Modifier.height(48.dp).width(80.dp),
                        LedGreen,
                        enabled = normalized != null
                    )
                }
            }
            if (manualAddress.isNotBlank() && BluetoothAddress.normalize(manualAddress) == null) {
                item { HelpText("That isn't a Bluetooth address yet: six pairs of 0-9 / A-F, like 00:1D:A5:68:98:8A.", color = AlertRed) }
            }
            item {
                HelpText(
                    "Still nothing? Many head units run Bluetooth through their own phone app, separate from Android. " +
                        "Pair the adapter there (set that app's PIN to 1234 or 0000 first), then come back and tap Scan. " +
                        "If Android Bluetooth shows as not present above, a Wi-Fi OBD adapter is the dependable choice."
                )
            }
        }

        item { SectionHeader("Wi-Fi adapter", color = DashAmber) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KittTextField(wifiAddress, { wifiAddress = it }, "192.168.0.10:35000", Modifier.weight(1f))
                KittButton(
                    label = "Use Wi-Fi",
                    onClick = { choose(AdapterConfig(AdapterType.WIFI, wifiAddress.trim(), "Wi-Fi ELM327")) },
                    modifier = Modifier.height(48.dp),
                    color = LedGreen,
                    active = saved?.type == AdapterType.WIFI
                )
            }
        }

        item { SectionHeader("Manufacturer", color = DashAmber) }
        item {
            ChoiceRow(
                options = listOf(ManufacturerProfiles.AUTO) + ManufacturerProfiles.all.map { it.id },
                selected = manufacturerChoice,
                label = { id ->
                    if (id == ManufacturerProfiles.AUTO) "Auto (VIN)" else ManufacturerProfiles.byId(id)?.name?.substringBefore(" (") ?: id
                },
                onSelect = { app.settings.manufacturerProfile.set(it) }
            )
        }
        item {
            HelpText("Active: ${profile.name}. ECUs: ${profile.ecus.joinToString { "${it.name} ${it.header}" }}")
        }

        item { SectionHeader("Protocol & speed", color = DashAmber) }
        item {
            ChoiceRow(PROTOCOLS.map { it.first }, protocol, { code -> PROTOCOLS.first { it.first == code }.second }, {
                app.settings.obdProtocol.set(it)
            })
        }
        item {
            KittToggle(
                "Fast multi-PID polling",
                fastPolling,
                { app.settings.obdFastPolling.set(it) },
                description = "Up to 6 sensors per request on CAN cars. Turn off if values freeze on a clone adapter."
            )
        }
        item { HelpText("Protocol and speed changes apply on the next connection.") }
    }
}
