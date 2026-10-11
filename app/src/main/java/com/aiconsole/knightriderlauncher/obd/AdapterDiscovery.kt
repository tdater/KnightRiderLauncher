package com.aiconsole.knightriderlauncher.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Pure Bluetooth address handling (unit-tested). */
object BluetoothAddress {
    /**
     * "00:1d:a5:68:98:8a", "00-1D-A5-68-98-8A" or "001DA568988A" → "00:1D:A5:68:98:8A";
     * null when it isn't a Bluetooth address.
     */
    fun normalize(text: String): String? {
        val hex = text.trim().uppercase().filter { it.isLetterOrDigit() }
        if (hex.length != 12 || hex.any { it !in "0123456789ABCDEF" }) return null
        return hex.chunked(2).joinToString(":")
    }
}

/** What Android's Bluetooth looks like on this head unit: the first layer of any OBD problem. */
data class BluetoothReport(
    val present: Boolean,
    val enabled: Boolean,
    val leSupported: Boolean,
    val name: String?,
    val paired: List<String>,
    val permissions: Boolean,
    val locationOffBlocksScan: Boolean
) {
    fun lines(): List<String> = buildList {
        add(
            if (present) "Android Bluetooth: present"
            else "Android Bluetooth: NOT PRESENT. This head unit doesn't give apps its Bluetooth (often a separate phone module); use a Wi-Fi adapter."
        )
        if (present) {
            add(if (enabled) "Bluetooth: on" + (name?.let { " (\"$it\")" } ?: "") else "Bluetooth: OFF. Turn it on in Android settings.")
            add(if (leSupported) "Bluetooth Low Energy: supported" else "Bluetooth Low Energy: NOT supported, so BLE adapters (like the Veepeak) can't work here")
            add(if (paired.isEmpty()) "Paired devices apps can see: none" else "Paired devices apps can see: " + paired.joinToString())
        }
        add(if (permissions) "App permission: granted" else "App permission: missing (tap Allow Bluetooth access)")
        if (locationOffBlocksScan) add("Location is off: this Android version finds no devices while it's off.")
    }
}

/** Finds OBD adapters: paired Bluetooth devices, nearby Classic devices and nearby BLE devices. */
object AdapterDiscovery {

    @SuppressLint("MissingPermission")
    fun report(context: Context): BluetoothReport {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val permissions = hasPermissions(context)
        return BluetoothReport(
            present = adapter != null,
            enabled = adapter?.isEnabled == true,
            leSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            name = if (permissions) runCatching { adapter?.name }.getOrNull() else null,
            paired = if (permissions) runCatching { adapter?.bondedDevices.orEmpty().map { it.name ?: it.address } }.getOrDefault(emptyList()) else emptyList(),
            permissions = permissions,
            locationOffBlocksScan = scanNeedsLocationEnabled(context)
        )
    }

    /** Runtime permissions needed to connect (and scan for BLE) on this Android version. */
    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasPermissions(context: Context): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<AdapterConfig> = try {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
            .map { AdapterConfig(AdapterType.CLASSIC, it.address, it.name ?: it.address) }
            // Likely OBD dongles first.
            .sortedByDescending { OBD_NAME.containsMatchIn(it.name) }
    } catch (_: SecurityException) {
        emptyList()
    }

    /**
     * Emits nearby Bluetooth Classic devices with a name as they're found, paired or not, the way
     * Torque and Car Scanner list adapters. Connecting to an unpaired one lets Android pair it
     * (showing a PIN prompt if the dongle asks). Collect with a timeout; inquiry runs ~12 s.
     */
    @SuppressLint("MissingPermission")
    fun scanClassic(context: Context, onProblem: (String) -> Unit = {}): Flow<AdapterConfig> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            onProblem(if (adapter == null) "Classic scan: no Bluetooth adapter available to apps." else "Classic scan: Bluetooth is off.")
            close()
            return@callbackFlow
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            ?: return
                        val info = try {
                            device.type to (device.name ?: intent.getStringExtra(BluetoothDevice.EXTRA_NAME))
                        } catch (_: SecurityException) {
                            return
                        }
                        // Inquiry can report BLE-only devices too; those belong to the BLE transport.
                        if (info.first == BluetoothDevice.DEVICE_TYPE_LE) return
                        // Unnamed devices are kept (listed last): some dongles send no name to inquiry.
                        trySend(AdapterConfig(AdapterType.CLASSIC, device.address, info.second ?: "$UNNAMED ${device.address}"))
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // System broadcasts still arrive with NOT_EXPORTED.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val started = try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (_: SecurityException) {
            false
        }
        if (!started) {
            onProblem("Classic scan: Android refused to start device discovery (the head unit may keep Bluetooth for its own phone app).")
            close()
        }
        awaitClose {
            try {
                adapter.cancelDiscovery()
            } catch (_: SecurityException) {
            }
            context.unregisterReceiver(receiver)
        }
    }

    /**
     * Android 10-11 return no Classic or BLE scan results while Location is switched off,
     * even with the permission granted. Android 12+ scans don't depend on it.
     */
    fun scanNeedsLocationEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return false
        val location = context.getSystemService(LocationManager::class.java) ?: return false
        return !LocationManagerCompat.isLocationEnabled(location)
    }

    /** Emits BLE devices as they're found (unnamed ones labelled by address). Collect with a timeout. */
    @SuppressLint("MissingPermission")
    fun scanBle(context: Context, onProblem: (String) -> Unit = {}): Flow<AdapterConfig> = callbackFlow {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            onProblem("BLE scan: not available (Bluetooth off, or no Bluetooth Low Energy for apps on this unit).")
            close()
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = try {
                    result.device.name
                } catch (_: SecurityException) {
                    null
                } ?: result.scanRecord?.deviceName ?: "$UNNAMED ${result.device.address}"
                trySend(AdapterConfig(AdapterType.BLE, result.device.address, name))
            }

            override fun onScanFailed(errorCode: Int) {
                onProblem("BLE scan failed (Android error $errorCode). Toggle Bluetooth off and on, then scan again.")
                close()
            }
        }
        try {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(null, settings, callback)
        } catch (_: SecurityException) {
            onProblem("BLE scan: permission refused.")
            close()
        }
        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
            } catch (_: IllegalStateException) {
                // Bluetooth turned off mid-scan.
            }
        }
    }

    fun isLikelyObd(name: String): Boolean = OBD_NAME.containsMatchIn(name)

    fun isUnnamed(name: String): Boolean = name.startsWith(UNNAMED)

    private const val UNNAMED = "Unnamed"

    private val OBD_NAME = Regex("obd|elm|vlink|vgate|icar|obdlink|veepeak|konnwei|carista|v-link|kiwi", RegexOption.IGNORE_CASE)
}
