package com.aiconsole.knightriderlauncher.data

import android.content.Context
import android.os.Build
import android.os.Process
import com.aiconsole.knightriderlauncher.obd.ObdManager
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash capture and the "Report a problem" file: device and app details, recent crashes, the
 * OBD connection log, non-secret settings and this app's own recent log output. Built for a
 * head unit where adb isn't available. API keys live in a separate encrypted store and are
 * never included.
 */
object ProblemReport {

    private const val MAX_CRASHES = 5

    private fun crashDir(context: Context) = File(context.filesDir, "crashes")

    /** Saves uncaught exceptions to a file before the process dies, then lets Android handle them. */
    fun installCrashHandler(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val dir = crashDir(appContext).apply { mkdirs() }
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                File(dir, "crash-${System.currentTimeMillis()}.txt").writeText("Thread: ${thread.name}\n$trace")
                dir.listFiles()?.sortedByDescending { it.name }?.drop(MAX_CRASHES)?.forEach { it.delete() }
            } catch (_: Throwable) {
                // Never let crash logging hide the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Saves a main-thread freeze (see FreezeWatchdog) next to the crashes. */
    fun recordFreeze(context: Context, details: String) {
        try {
            val dir = crashDir(context.applicationContext).apply { mkdirs() }
            File(dir, "crash-${System.currentTimeMillis()}.txt").writeText("FREEZE (launcher not responding)\n$details")
            dir.listFiles()?.sortedByDescending { it.name }?.drop(MAX_CRASHES)?.forEach { it.delete() }
        } catch (_: Throwable) {
            // Best effort only.
        }
    }

    /**
     * Writes the report section by section, flushing as it goes, so a failure in one part (or the
     * slow log read) can never leave an empty file.
     */
    fun write(context: Context, obd: ObdManager, out: OutputStream) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
        fun section(title: String, body: StringBuilder.() -> Unit) {
            val text = try {
                buildString(body)
            } catch (e: Throwable) {
                "(section failed: ${e.javaClass.simpleName}: ${e.message})\n"
            }
            writer.write("== $title ==\n")
            writer.write(text)
            writer.write("\n")
            writer.flush()
        }
        section("KITT LAUNCHER PROBLEM REPORT") {
            appendLine("Created: ${stamp.format(Date())}")
            val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            appendLine("App: ${context.packageName} ${pkg?.versionName ?: "?"}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Build: ${Build.FINGERPRINT}")
            val webView = runCatching { androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
            appendLine("WebView: ${webView?.packageName ?: "?"} ${webView?.versionName ?: "?"}")
        }
        section("Microphone and speech") {
            val prefs = context.getSharedPreferences("kitt_settings", Context.MODE_PRIVATE)
            appendLine("Speech input: ${prefs.getString("speech_input", "Auto")}, recognizer choice: ${prefs.getString("speech_recognizer", "").orEmpty().ifEmpty { "Auto" }}")
            appendLine("Resolved recognizer: ${com.aiconsole.knightriderlauncher.assistant.SpeechRecognizers.describe(context, prefs.getString("speech_recognizer", "").orEmpty())}")
            appendLine("System default recognizer: ${com.aiconsole.knightriderlauncher.assistant.SpeechRecognizers.systemDefault(context)?.flattenToString() ?: "none"}")
            appendLine("Installed recognizers: " + com.aiconsole.knightriderlauncher.assistant.SpeechRecognizers.installed(context).joinToString { it.component.flattenToString() })
            appendLine("Default assistant: " + (android.provider.Settings.Secure.getString(context.contentResolver, "assistant") ?: "none"))
            appendLine("Usage access: ${com.aiconsole.knightriderlauncher.assistant.MicGuard.hasUsageAccess(context)}")
            appendLine("Last mic takeover: ${com.aiconsole.knightriderlauncher.assistant.MicGuard.lastRecorded(context)?.let { "${stamp.format(Date(it.atMillis))} by ${it.packageName ?: "unknown"}" } ?: "none"}")
        }
        section("OBD") {
            appendLine("State: ${obd.state.value}")
            obd.linkEvents.value.takeLast(80).forEach { e ->
                appendLine("${stamp.format(Date(e.timestamp))} ${e.layer.label} ${if (e.ok) "ok" else "FAIL"}: ${e.message}")
            }
        }
        section("Live readings") {
            val defs = obd.definitions.value
            val now = System.currentTimeMillis()
            val readings = obd.readings.value
            if (readings.isEmpty()) appendLine("None.")
            readings.toSortedMap().forEach { (id, r) ->
                val raw = obd.rawDataOf(id)?.let { " raw [$it]" }.orEmpty()
                appendLine("$id ${defs[id]?.name ?: ""} = ${"%.2f".format(Locale.US, r.value)} (${(now - r.timestamp) / 1000}s ago)$raw")
            }
        }
        section("Recent crashes") {
            val crashes = crashDir(context).listFiles()?.sortedByDescending { it.name }.orEmpty()
            if (crashes.isEmpty()) appendLine("None recorded.")
            crashes.forEach { f ->
                val at = f.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull()
                appendLine("-- ${at?.let { stamp.format(Date(it)) } ?: f.name}")
                appendLine(f.readText().take(8_000))
            }
        }
        section("Settings (no API keys)") {
            context.getSharedPreferences("kitt_settings", Context.MODE_PRIVATE).all.toSortedMap().forEach { (k, v) ->
                appendLine("$k = ${v.toString().replace('\n', ' ').take(200)}")
            }
        }
        section("App log (this app only)") { append(appLog()) }
    }

    /** This process's recent logcat output; apps may read their own logs without extra permission. */
    private fun appLog(): String = try {
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "3000", "--pid=${Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        // Skip the renderer's slow-frame spam so the useful lines fit.
        val output = process.inputStream.bufferedReader().useLines { lines ->
            lines.filterNot { "Davey! duration=" in it }.joinToString("\n")
        }
        process.waitFor() // logcat -d exits once it has dumped; the output is already read.
        output.ifBlank { "(empty)\n" }
    } catch (e: IOException) {
        "(couldn't read the log: ${e.message})\n"
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        "(interrupted)\n"
    }
}
