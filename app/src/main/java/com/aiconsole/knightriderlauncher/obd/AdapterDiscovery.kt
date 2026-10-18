package com.aiconsole.knightriderlauncher.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

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

/** Plain-language Android Bluetooth error codes (unit-tested). */
object BluetoothErrors {
    /** android.bluetooth.le.ScanCallback.SCAN_FAILED_* codes. */
    fun scanFailure(code: Int): String = "BLE scan refused by Android (error $code: " + when (code) {
        1 -> "a scan was already running"
        2 -> "the Bluetooth stack has no free scan slots, usually left behind by another app. Turning Bluetooth off and on clears it"
        3 -> "internal Bluetooth error"
        4 -> "this head unit doesn't offer BLE scanning to apps"
        5 -> "out of Bluetooth hardware resources"
        6 -> "scanning too often; wait 30 seconds"
        else -> "unknown"
    } + ")."

    /** android.bluetooth.BluetoothGatt status codes seen when a BLE link drops or won't open. */
    fun gattStatus(status: Int): String = when (status) {
        0 -> "status 0"
        8 -> "status 8, link timed out"
        19 -> "status 19, the adapter closed the link"
        22 -> "status 22, the head unit closed the link"
        62 -> "status 62, the adapter didn't answer"
        133 -> "status 133, the head unit's Bluetooth couldn't open the link"
        else -> "status $status"
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
                        // Discovery reports BLE-only devices too. They're kept as BLE: on head units
                        // whose BLE scanner is refused, this is the only way to find a BLE adapter.
                        val type = if (info.first == BluetoothDevice.DEVICE_TYPE_LE) AdapterType.BLE else AdapterType.CLASSIC
                        // Unnamed devices are kept (listed last): some dongles send no name to inquiry.
                        trySend(AdapterConfig(type, device.address, info.second ?: "$UNNAMED ${device.address}"))
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
    fun scanBle(context: Context, onProblem: (String) -> Unit = {}): Flow<AdapterConfig> =
        scanBleDevices(context, null, onProblem).map { (device, name) ->
            AdapterConfig(AdapterType.BLE, device.address, name ?: "$UNNAMED ${device.address}")
        }

    /**
     * Looks for one BLE device for up to [timeoutMs]. Connecting by address to a BLE device the
     * Bluetooth stack has never seen often fails (it doesn't know the address type), so the BLE
     * transport looks first; the scanned device carries the right type. Null if not seen.
     */
    suspend fun findBle(context: Context, address: String, timeoutMs: Long): BluetoothDevice? =
        withTimeoutOrNull(timeoutMs) { scanBleDevices(context, address).firstOrNull()?.first }

    /**
     * BLE scan that tries every way a head-unit stack may accept: the modern scanner at low
     * latency, then balanced, then the old pre-Lollipop API. Some units refuse the first.
     */
    @SuppressLint("MissingPermission")
    private fun scanBleDevices(
        context: Context,
        onlyAddress: String?,
        onProblem: (String) -> Unit = {}
    ): Flow<Pair<BluetoothDevice, String?>> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (adapter == null || scanner == null) {
            onProblem("BLE scan: not available (Bluetooth off, or no Bluetooth Low Energy for apps on this unit).")
            close()
            return@callbackFlow
        }
        fun found(device: BluetoothDevice, recordName: String?) {
            if (onlyAddress != null && !device.address.equals(onlyAddress, ignoreCase = true)) return
            val name = try {
                device.name
            } catch (_: SecurityException) {
                null
            } ?: recordName
            trySend(device to name)
        }

        @Suppress("DEPRECATION")
        val legacy = BluetoothAdapter.LeScanCallback { device, _, _ -> found(device, null) }
        var legacyRunning = false
        val modes = ArrayDeque(listOf(ScanSettings.SCAN_MODE_LOW_LATENCY, ScanSettings.SCAN_MODE_BALANCED))
        lateinit var callback: ScanCallback

        fun startNext(lastError: Int?) {
            val mode = modes.removeFirstOrNull()
            if (mode != null) {
                try {
                    val filters = onlyAddress?.let { listOf(ScanFilter.Builder().setDeviceAddress(it.uppercase()).build()) }
                    scanner.startScan(filters, ScanSettings.Builder().setScanMode(mode).build(), callback)
                } catch (_: SecurityException) {
                    onProblem("BLE scan: permission refused.")
                    close()
                } catch (_: IllegalArgumentException) {
                    startNext(lastError)
                }
                return
            }
            legacyRunning = try {
                @Suppress("DEPRECATION")
                adapter.startLeScan(legacy)
            } catch (_: SecurityException) {
                false
            }
            if (!legacyRunning) {
                onProblem(BluetoothErrors.scanFailure(lastError ?: 0))
                close()
            }
        }

        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = found(result.device, result.scanRecord?.deviceName)

            override fun onBatchScanResults(results: MutableList<ScanResult>) =
                results.forEach { found(it.device, it.scanRecord?.deviceName) }

            override fun onScanFailed(errorCode: Int) {
                try {
                    scanner.stopScan(this)
                } catch (_: Exception) {
                }
                startNext(errorCode)
            }
        }
        startNext(null)
        awaitClose {
            try {
                scanner.stopScan(callback)
                @Suppress("DEPRECATION")
                if (legacyRunning) adapter.stopLeScan(legacy)
            } catch (_: SecurityException) {
            } catch (_: IllegalStateException) {
                // Bluetooth turned off mid-scan.
            }
        }
    }

    /**
     * The transport [config] really needs: a device Android already knows as BLE-only can't take
     * a Classic connection (and the reverse), whichever button was tapped when it was entered.
     */
    @SuppressLint("MissingPermission")
    fun transportFor(context: Context, config: AdapterConfig): AdapterType {
        if (config.type == AdapterType.WIFI) return config.type
        val address = BluetoothAddress.normalize(config.address) ?: return config.type
        val type = try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)?.type
        } catch (_: Exception) {
            null
        }
        return when (type) {
            BluetoothDevice.DEVICE_TYPE_LE -> AdapterType.BLE
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> AdapterType.CLASSIC
            else -> config.type
        }
    }

    fun isLikelyObd(name: String): Boolean = OBD_NAME.containsMatchIn(name)

    fun isUnnamed(name: String): Boolean = name.startsWith(UNNAMED)

    private const val UNNAMED = "Unnamed"

    private val OBD_NAME = Regex("obd|elm|vlink|vgate|icar|obdlink|veepeak|konnwei|carista|v-link|kiwi", RegexOption.IGNORE_CASE)
}
