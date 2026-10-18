package com.aiconsole.knightriderlauncher.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.concurrent.thread

enum class AdapterType(val label: String) {
    CLASSIC("Bluetooth"),
    BLE("Bluetooth LE"),
    WIFI("Wi-Fi")
}

/** A saved OBD adapter. For Wi-Fi, [address] is "host:port". */
data class AdapterConfig(val type: AdapterType, val address: String, val name: String)

/** Byte pipe to an ELM327-compatible adapter. Incoming data arrives on [incoming]. */
interface ObdTransport : Closeable {
    val incoming: Channel<ByteArray>
    suspend fun connect()
    suspend fun write(data: ByteArray)
}

fun createTransport(context: Context, config: AdapterConfig): ObdTransport = when (config.type) {
    AdapterType.CLASSIC -> ClassicBluetoothTransport(context, config.address)
    AdapterType.BLE -> BleTransport(context, config.address)
    AdapterType.WIFI -> WifiTransport(config.address)
}

/** Shared plumbing for stream-based transports: a daemon thread pumps the input stream. */
abstract class StreamTransport : ObdTransport {
    final override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var output: OutputStream? = null

    protected fun startReading(input: InputStream, output: OutputStream) {
        this.output = output
        thread(name = "obd-reader", isDaemon = true) {
            val buffer = ByteArray(1024)
            try {
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n > 0) incoming.trySend(buffer.copyOf(n))
                }
                incoming.close(IOException("Adapter closed the connection"))
            } catch (e: IOException) {
                incoming.close(e)
            }
        }
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val out = output ?: throw IOException("Not connected")
        out.write(data)
        out.flush()
    }
}

/** Bluetooth Classic SPP: the vast majority of ELM327 dongles. */
class ClassicBluetoothTransport(context: Context, private val address: String) : StreamTransport() {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission") // Checked by the UI before connecting.
    override suspend fun connect() = withContext(Dispatchers.IO) {
        val bt = adapter ?: throw IOException("This head unit has no Bluetooth")
        if (!bt.isEnabled) throw IOException("Bluetooth is off")
        try {
            bt.cancelDiscovery() // Discovery slows RFCOMM connects dramatically.
        } catch (_: SecurityException) {
            // Needs BLUETOOTH_SCAN on Android 12+; harmless to skip.
        }
        val device = try {
            bt.getRemoteDevice(BluetoothAddress.normalize(address) ?: address)
        } catch (e: IllegalArgumentException) {
            throw IOException("\"$address\" isn't a Bluetooth address (it looks like 00:1D:A5:68:98:8A)", e)
        }
        // Head-unit Bluetooth stacks differ a lot from phones, so try every way in:
        // 1) secure SPP via SDP, 2) insecure SPP (modules that can't do authenticated links),
        // 3) raw RFCOMM channel 1, secure then insecure (clones with broken SDP records).
        val attempts: List<Pair<String, () -> BluetoothSocket>> = listOf(
            "secure SPP" to { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            "insecure SPP" to { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            "RFCOMM channel 1" to { channelSocket(device, "createRfcommSocket") },
            "insecure RFCOMM channel 1" to { channelSocket(device, "createInsecureRfcommSocket") }
        )
        val failures = mutableListOf<String>()
        var connected: BluetoothSocket? = null
        for ((index, attempt) in attempts.withIndex()) {
            val (name, open) = attempt
            // A failed RFCOMM attempt takes a moment to release the link; trying again at once
            // fails on many head-unit stacks even when the next method would work.
            if (index > 0) delay(RETRY_GAP_MS)
            var candidate: BluetoothSocket? = null
            try {
                candidate = open()
                connectCancellable(candidate)
                connected = candidate
                break
            } catch (e: CancellationException) {
                runCatching { candidate?.close() }
                throw e
            } catch (e: Exception) {
                runCatching { candidate?.close() }
                failures += "$name: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        if (connected == null) {
            throw IOException(
                "Couldn't reach the adapter (${failures.joinToString("; ")}). Check it's plugged in with the ignition on " +
                    "and not held by another OBD app (close Torque or Car Scanner, since an adapter only takes one connection). " +
                    "If Android asked for a PIN, try 1234 or 0000. Unplugging the adapter for a few seconds resets it."
            )
        }
        socket = connected
        startReading(connected.inputStream, connected.outputStream)
    }

    override fun close() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        incoming.close()
    }

    private fun channelSocket(device: BluetoothDevice, method: String): BluetoothSocket =
        device.javaClass.getMethod(method, Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket

    /**
     * BluetoothSocket.connect() blocks for up to ~12 s and ignores coroutine cancellation, so a
     * cancelled attempt (driver tapped Connect again, picked another adapter) used to keep
     * dialling the adapter alongside the new one and both failed. Closing the socket on
     * cancellation aborts it at once.
     */
    @SuppressLint("MissingPermission") // Checked by the UI before connecting.
    private suspend fun connectCancellable(socket: BluetoothSocket) = suspendCancellableCoroutine<Unit> { cont ->
        cont.invokeOnCancellation { runCatching { socket.close() } }
        thread(name = "obd-connect", isDaemon = true) {
            try {
                socket.connect()
                cont.resume(Unit)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val RETRY_GAP_MS = 600L
    }
}

/** Wi-Fi ELM327 adapters (usually 192.168.0.10:35000 on the adapter's own hotspot). */
class WifiTransport(private val address: String) : StreamTransport() {
    private var socket: Socket? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val host = address.substringBefore(':').ifEmpty { "192.168.0.10" }
        val port = address.substringAfter(':', "35000").toIntOrNull() ?: 35000
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, port), 5_000)
            s.tcpNoDelay = true
        } catch (e: IOException) {
            s.close()
            throw IOException("Couldn't reach $host:$port. Join the adapter's Wi-Fi network first.", e)
        }
        socket = s
        startReading(s.getInputStream(), s.getOutputStream())
    }

    override fun close() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        incoming.close()
    }
}
