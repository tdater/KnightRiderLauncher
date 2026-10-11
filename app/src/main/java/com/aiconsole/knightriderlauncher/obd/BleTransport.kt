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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
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

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var ready: CompletableDeferred<Unit>? = null
    private var pendingWrite: CompletableDeferred<Boolean>? = null
    private val writeLock = Mutex()
    private var chunkSize = 20

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> if (!g.requestMtu(185)) g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val error = IOException("Adapter disconnected (status $status)")
                    ready?.completeExceptionally(error)
                    pendingWrite?.complete(false)
                    incoming.close(error)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) chunkSize = (mtu - 3).coerceAtLeast(20)
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val (notify, write) = findCharacteristics(g) ?: run {
                ready?.completeExceptionally(IOException("No OBD serial service found on this BLE device"))
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
                ready?.completeExceptionally(IOException("Couldn't enable adapter notifications ($status)"))
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

    override suspend fun connect() {
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IOException("This head unit has no Bluetooth")
        if (!adapter.isEnabled) throw IOException("Bluetooth is off")
        val device = try {
            adapter.getRemoteDevice(BluetoothAddress.normalize(address) ?: address)
        } catch (e: IllegalArgumentException) {
            throw IOException("\"$address\" isn't a Bluetooth address (it looks like 00:1D:A5:68:98:8A)", e)
        }
        val deferred = CompletableDeferred<Unit>()
        ready = deferred
        gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        try {
            withTimeout(15_000) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            close()
            throw IOException("BLE adapter didn't respond. Is it powered and in range?", e)
        } catch (e: CancellationException) {
            close()
            throw e
        } catch (e: IOException) {
            close()
            throw e
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
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: SecurityException) {
        }
        gatt = null
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
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val KNOWN_SERVICES: List<UUID> = listOf(
            "0000fff0-0000-1000-8000-00805f9b34fb",
            "0000ffe0-0000-1000-8000-00805f9b34fb",
            "000018f0-0000-1000-8000-00805f9b34fb",
            "e7810a71-73ae-499d-8c15-faa9aef0c3f2"
        ).map(UUID::fromString)
    }
}
