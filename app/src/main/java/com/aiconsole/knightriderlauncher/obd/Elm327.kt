package com.aiconsole.knightriderlauncher.obd

import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

class ElmException(message: String) : IOException(message)

data class ElmInfo(val version: String, val protocolNumber: Int, val protocolName: String) {
    /** ISO 15765-4 CAN (6-9) or user CAN (A-C): enables headers, multi-PID and Mode 0A. */
    val isCan: Boolean get() = protocolNumber in 6..12
}

/**
 * ELM327 command layer. Every exchange is "command + CR" and ends at the '>' prompt.
 * Callers group related commands (header switch + request) with [exclusive] so the
 * polling loop and on-demand requests (codes, PID tests) never interleave on the wire.
 */
class Elm327(private val transport: ObdTransport) {

    private val lock = Mutex()
    private val buffer = StringBuilder()
    private var currentHeader: String? = null

    /** Header used for normal Mode 01 polling; "7E0" (engine, physical) once CAN is up. */
    var defaultHeader: String? = null
        internal set

    inner class Link internal constructor() {
        suspend fun send(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String = sendRaw(command, timeoutMs)

        suspend fun useHeader(header: String) {
            if (header != currentHeader) {
                sendRaw("ATSH$header", DEFAULT_TIMEOUT_MS)
                currentHeader = header
            }
        }

        suspend fun useDefaultHeader() {
            defaultHeader?.let { useHeader(it) }
        }

        fun setDefaultHeader(header: String) {
            defaultHeader = header
        }
    }

    private val link = Link()

    suspend fun <T> exclusive(block: suspend Link.() -> T): T = lock.withLock { link.block() }

    /** Resets the adapter, applies fast settings, and lets it find the car's protocol. */
    suspend fun initialize(protocol: String): ElmInfo = exclusive {
        // Many clones drop bytes sent the instant the link opens, and some greet with a stale
        // prompt: settle, throw away whatever arrived, and give the reset a second chance.
        delay(LINK_SETTLE_MS)
        while (transport.incoming.tryReceive().isSuccess) Unit
        buffer.setLength(0)
        try {
            send("ATZ", 5_000) // Reset: prints the "ELM327 vX.X" banner.
        } catch (_: ElmException) {
            send("ATZ", 5_000)
        }
        val version = send("ATI").lines().lastOrNull { it.isNotBlank() }?.trim() ?: "ELM327"
        // Echo off, linefeeds off, spaces off, headers off, adaptive timing, protocol.
        for (command in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP$protocol")) {
            send(command)
        }
        val probe = send("0100", 15_000) // Triggers the protocol search on first use.
        if (ObdResponseParser.errorOf(probe) != null) {
            throw ElmException("The car's computer didn't answer. Turn the ignition on and try again.")
        }
        val number = send("ATDPN").trim().removePrefix("A").toIntOrNull(16) ?: 0
        val name = send("ATDP").trim().removePrefix("AUTO, ")
        ElmInfo(version, number, name)
    }

    private suspend fun sendRaw(command: String, timeoutMs: Long): String {
        transport.write("$command\r".toByteArray(Charsets.US_ASCII))
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            val prompt = buffer.indexOf(">")
            if (prompt >= 0) {
                val raw = buffer.substring(0, prompt)
                buffer.delete(0, prompt + 1)
                return clean(raw, command)
            }
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000
            if (remainingMs <= 0) {
                buffer.setLength(0)
                throw ElmException("Adapter timed out on $command")
            }
            val chunk = try {
                withTimeoutOrNull(remainingMs) { transport.incoming.receive() }
            } catch (_: ClosedReceiveChannelException) {
                throw IOException("Adapter connection lost")
            } ?: continue
            buffer.append(String(chunk, Charsets.US_ASCII))
        }
    }

    private fun clean(raw: String, command: String): String =
        raw.replace("\u0000", "")
            .split('\r', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(command, ignoreCase = true) }
            .joinToString("\n")

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 2_000L
        const val LINK_SETTLE_MS = 300L
    }
}
