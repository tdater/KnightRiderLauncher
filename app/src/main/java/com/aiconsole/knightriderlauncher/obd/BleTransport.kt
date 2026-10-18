package com.aiconsole.knightriderlauncher.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth LE ELM327 adapters (Vgate iCar Pro BLE, OBDLink CX, Veepeak BLE and similar).
 * Finds the serial-style service by known UUIDs, falling back to any notify + write pair.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is checked by the UI before connecting.
class BleTransport(context: Context, private val address: String) : ObdTransport {

    private val appContext = context.applicationContext
    override val incoming = Channel<ByteArray>(Channel.UNLIMITED)

    @Volatile private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    @Volatile private var ready: CompletableDeferred<Unit>? = null
    private var pendingWrite: CompletableDeferred<Boolean>? = null
    private val writeLock = Mutex()
    private var chunkSize = 20

    /** Services were requested on this link (after the MTU answer, or without it if none comes). */
    @Volatile private var discovering = false

    /** The link finished setting up; a disconnect from here on ends the session. */
    @Volatile private var linked = false
    private val main = Handler(Looper.getMainLooper())

    private fun discover(g: BluetoothGatt) {
        if (discovering) return
        discovering = true
        if (!g.discoverServices()) ready?.completeExceptionally(IOException("the head unit refused to read the adapter's services"))
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) return // A late callback from an earlier attempt.
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    // Some stacks accept the MTU request and never answer it: discover anyway.
                    if (g.requestMtu(185)) main.postDelayed({ if (g === gatt) discover(g) }, MTU_WAIT_MS) else discover(g)
                }
                newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS -> {
                    val error = IOException("adapter disconnected (${BluetoothErrors.gattStatus(status)})")
                    ready?.completeExceptionally(error)
                    pendingWrite?.complete(false)
                    // Only a live session ends here; a failed attempt leaves the channel for the next one.
                    if (linked) incoming.close(error)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) chunkSize = (mtu - 3).coerceAtLeast(20)
            discover(g)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                ready?.completeExceptionally(IOException("couldn't read the adapter's services (${BluetoothErrors.gattStatus(status)})"))
                return
            }
            val (notify, write) = findCharacteristics(g) ?: run {
                ready?.completeExceptionally(IOException("no OBD serial service found on this BLE device"))
                return
            }
            writeChar = write
            g.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(CCCD) ?: run {
                ready?.complete(Unit)
                return
            }
            val value = if (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, value)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                ready?.complete(Unit)
            } else {
                ready?.completeExceptionally(IOException("couldn't enable adapter notifications ($status)"))
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            incoming.trySend(value.copyOf())
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            c.value?.let { incoming.trySend(it.copyOf()) }
        }
    }

    /**
     * Head-unit BLE stacks are much pickier than phones, so this tries several ways in:
     * 1) when Android has never seen the adapter, scan for it first (connecting to an unknown
     * address often fails with GATT error 133 because the stack doesn't know its address type);
     * 2) a direct connection; 3) a background ("auto") connection, which some stacks need;
     * 4) on Android 13+, the address as a random BLE address in case it isn't a public one.
     * A pause between attempts lets the stack release the last one.
     */
    override suspend fun connect() {
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IOException("This head unit has no Bluetooth")
        if (!adapter.isEnabled) throw IOException("Bluetooth is off")
        val normalized = BluetoothAddress.normalize(address)
            ?: throw IOException("\"$address\" isn't a Bluetooth address (it looks like 00:1D:A5:68:98:8A)")
        var device = adapter.getRemoteDevice(normalized)
        val unknown = runCatching { device.type == BluetoothDevice.DEVICE_TYPE_UNKNOWN }.getOrDefault(true)
        if (unknown) AdapterDiscovery.findBle(appContext, normalized, FIND_MS)?.let { device = it }
        val attempts = buildList {
            add(Triple("direct", device, false))
            add(Triple("auto", device, true))
            if (unknown && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Triple("random address", adapter.getRemoteLeDevice(normalized, BluetoothDevice.ADDRESS_TYPE_RANDOM), false))
            }
        }
        val failures = mutableListOf<String>()
        for ((index, attempt) in attempts.withIndex()) {
            val (name, target, auto) = attempt
            if (index > 0) delay(RETRY_GAP_MS)
            try {
                connectOnce(target, auto)
                linked = true
                return
            } catch (e: IOException) {
                failures += "$name: ${e.message}"
            }
        }
        throw IOException(
            "BLE adapter didn't connect (${failures.joinToString("; ")}). Check it's plugged in with the ignition on, " +
                "not connected to a phone or another app, and that its address was entered with BLE."
        )
    }

    private suspend fun connectOnce(device: BluetoothDevice, auto: Boolean) {
        val deferred = CompletableDeferred<Unit>()
        ready = deferred
        discovering = false
        try {
            gatt = device.connectGatt(appContext, auto, callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw IOException("the head unit refused to open a BLE link")
            withTimeout(if (auto) AUTO_TIMEOUT_MS else DIRECT_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            closeGatt()
            throw IOException("no answer", e)
        } catch (e: CancellationException) {
            closeGatt()
            throw e
        } catch (e: IOException) {
            closeGatt()
            throw e
        } catch (e: SecurityException) {
            closeGatt()
            throw IOException("Bluetooth permission refused", e)
        }
    }

    private fun closeGatt() {
        val g = gatt
        gatt = null
        try {
            g?.disconnect()
            g?.close()
        } catch (_: SecurityException) {
        }
    }

    override suspend fun write(data: ByteArray) = writeLock.withLock {
        val g = gatt ?: throw IOException("Not connected")
        val c = writeChar ?: throw IOException("Not connected")
        val withResponse = c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == 0
        val type = if (withResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        var offset = 0
        while (offset < data.size) {
            val chunk = data.copyOfRange(offset, minOf(data.size, offset + chunkSize))
            val ack = CompletableDeferred<Boolean>()
            pendingWrite = ack
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, chunk, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = type
                @Suppress("DEPRECATION")
                c.value = chunk
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
            if (!started) throw IOException("BLE write rejected")
            // Wait for the stack's acknowledgement before sending the next chunk.
            if (withTimeoutOrNull(2_000) { ack.await() } == false) throw IOException("BLE write failed")
            offset += chunk.size
        }
    }

    override fun close() {
        closeGatt()
        incoming.close()
    }

    private fun findCharacteristics(g: BluetoothGatt): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        // Known serial services first, then any service with notify + write characteristics.
        val ordered = g.services.sortedBy { service ->
            KNOWN_SERVICES.indexOf(service.uuid).let { if (it < 0) Int.MAX_VALUE else it }
        }
        for (service in ordered) {
            val chars = service.characteristics
            val notify = chars.firstOrNull {
                it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            }
            val write = chars.firstOrNull {
                it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
            if (notify != null && write != null) return notify to write
        }
        return null
    }

    private companion object {
        const val FIND_MS = 6_000L
        const val MTU_WAIT_MS = 1_500L
        const val RETRY_GAP_MS = 1_000L
        const val DIRECT_TIMEOUT_MS = 12_000L
        const val AUTO_TIMEOUT_MS = 20_000L
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val KNOWN_SERVICES: List<UUID> = listOf(
            "0000fff0-0000-1000-8000-00805f9b34fb",
            "0000ffe0-0000-1000-8000-00805f9b34fb",
            "000018f0-0000-1000-8000-00805f9b34fb",
            "e7810a71-73ae-499d-8c15-faa9aef0c3f2"
        ).map(UUID::fromString)
    }
}
