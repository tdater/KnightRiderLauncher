package com.aiconsole.knightriderlauncher.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.aiconsole.knightriderlauncher.assistant.await
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

/** A published release and its APK. */
data class ReleaseInfo(val tag: String, val apkUrl: String, val notes: String, val size: Long = 0L)

/** Plain-Kotlin version comparison for update checks ("v1.0.42" vs "1.0.37"). */
object Versions {
    fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V").split('.', '-', '+').mapNotNull { it.toIntOrNull() }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun isValidRepo(repo: String): Boolean = repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))
}

/** Pure rules for the updater (unit-tested): checking a downloaded APK and wording installer results. */
object UpdateRules {

    // PackageInstaller.STATUS_* values (copied so the JVM tests don't need the Android framework).
    const val STATUS_PENDING_USER_ACTION = -1
    const val STATUS_SUCCESS = 0
    const val STATUS_FAILURE = 1
    const val STATUS_FAILURE_BLOCKED = 2
    const val STATUS_FAILURE_ABORTED = 3
    const val STATUS_FAILURE_INVALID = 4
    const val STATUS_FAILURE_CONFLICT = 5
    const val STATUS_FAILURE_STORAGE = 6
    const val STATUS_FAILURE_INCOMPATIBLE = 7

    const val DIFFERENT_KEY =
        "This head unit has a build signed with a different key (a debug or locally built APK), so Android won't " +
            "update it in place. Save your setup with Backup, uninstall KITT Launcher once, then install the release APK " +
            "from the download page. After that, updates install from here."

    /** Why a downloaded APK can't update the installed app, or null when it can. */
    fun apkProblem(
        installedPackage: String,
        apkPackage: String?,
        installedVersionCode: Long,
        apkVersionCode: Long,
        sameSigner: Boolean?
    ): String? = when {
        apkPackage == null -> "The download isn't a valid APK (it may be incomplete). Try again."
        apkPackage != installedPackage -> "The release APK is for a different app ($apkPackage)."
        sameSigner == false -> DIFFERENT_KEY
        apkVersionCode < installedVersionCode ->
            "The release (build $apkVersionCode) is older than the installed build ($installedVersionCode); Android doesn't allow downgrades."
        else -> null
    }

    /** What to tell the driver for a final installer [status] ([systemMessage] is Android's own detail). */
    fun statusMessage(status: Int, systemMessage: String?): String {
        val detail = systemMessage?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return when (status) {
            STATUS_SUCCESS -> "Updated. The launcher restarts now."
            STATUS_FAILURE_ABORTED -> "Install cancelled."
            STATUS_FAILURE_BLOCKED -> "This head unit blocked the install$detail. Allow installs from unknown apps for KITT Launcher in Settings."
            STATUS_FAILURE_CONFLICT, STATUS_FAILURE_INCOMPATIBLE ->
                if (systemMessage?.contains("version", ignoreCase = true) == true && systemMessage.contains("downgrade", ignoreCase = true)) {
                    "Android refused a downgrade$detail."
                } else {
                    DIFFERENT_KEY
                }
            STATUS_FAILURE_STORAGE -> "Not enough storage to install the update$detail."
            STATUS_FAILURE_INVALID -> "The downloaded APK is damaged$detail. Try again."
            else -> "Update failed$detail."
        }
    }
}

/** Where an update stands, for the Updates panel. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloading(val percent: Int?) : UpdateState
    data object Installing : UpdateState
    data object NeedsPermission : UpdateState
    data class Done(val message: String, val success: Boolean) : UpdateState
}

/**
 * Checks the project's GitHub Releases for a newer signed APK and installs it through Android's
 * package installer. Android only accepts an update signed with the same key as the installed
 * app, so the downloaded APK is checked first and every installer result is reported (a silent
 * failure is what made "Install" look dead before). On Android 12+, once the launcher has
 * installed itself, later updates can go through without a confirmation screen.
 */
class SelfUpdate(context: Context) {

    private val context = context.applicationContext

    // Not the assistant's client: its 60-second call timeout cut off a 15 MB download over a
    // phone hotspot. Generous read timeout, no overall limit.
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    suspend fun latest(repo: String): ReleaseInfo = withContext(Dispatchers.IO) {
        if (!Versions.isValidRepo(repo)) throw IOException("Enter the repository as owner/name")
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(request).await().use { r ->
            if (r.code == 404) throw IOException("No releases published yet")
            if (r.code == 403) throw IOException("GitHub is rate-limiting this network; try again in a few minutes")
            if (!r.isSuccessful) throw IOException("GitHub returned HTTP ${r.code}")
            r.body.string()
        }
        try {
            val json = JSONObject(body)
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?: throw IOException("The latest release has no APK")
            ReleaseInfo(json.getString("tag_name"), apk.getString("browser_download_url"), json.optString("body"), apk.optLong("size"))
        } catch (e: JSONException) {
            throw IOException("Unexpected response from GitHub", e)
        }
    }

    /** Android 8+: the driver must let the launcher install apps once. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** The system screen where the driver allows installs from the launcher. */
    fun installPermissionIntent(): Intent =
        // Only shown on Android 8+ (canInstall is always true below that).
        Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Downloads [release], checks it can replace this build, and hands it to the package installer. */
    suspend fun install(release: ReleaseInfo) {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission
            return
        }
        try {
            val apk = download(release)
            verify(apk)?.let { problem ->
                _state.value = UpdateState.Done(problem, success = false)
                return
            }
            _state.value = UpdateState.Installing
            commit(apk)
        } catch (e: IOException) {
            Log.w(TAG, "Update failed", e)
            _state.value = UpdateState.Done("Update failed: ${e.message ?: e.javaClass.simpleName}", success = false)
        }
    }

    private suspend fun download(release: ReleaseInfo): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        _state.value = UpdateState.Downloading(0)
        http.newCall(Request.Builder().url(release.apkUrl).build()).await().use { r ->
            if (!r.isSuccessful) throw IOException("Download failed: HTTP ${r.code}")
            val total = r.body.contentLength().takeIf { it > 0 } ?: release.size.takeIf { it > 0 }
            file.outputStream().use { out ->
                r.body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        val percent = total?.let { (done * 100 / it).toInt().coerceIn(0, 100) }
                        if (percent != lastPercent) {
                            lastPercent = percent ?: -1
                            _state.value = UpdateState.Downloading(percent)
                        }
                    }
                    if (total != null && done < total) throw IOException("Download was cut off (${done / 1024} of ${total / 1024} KB)")
                }
            }
        }
        file
    }

    /** Null when the APK can update this app, otherwise the reason it can't. */
    @Suppress("DEPRECATION")
    private fun verify(apk: File): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
        archive?.applicationInfo?.let {
            it.sourceDir = apk.path
            it.publicSourceDir = apk.path
        }
        return UpdateRules.apkProblem(
            installedPackage = context.packageName,
            apkPackage = archive?.packageName,
            installedVersionCode = PackageInfoCompat.getLongVersionCode(installed),
            apkVersionCode = archive?.let(PackageInfoCompat::getLongVersionCode) ?: 0L,
            sameSigner = archive?.let { signers(it) }?.takeIf { it.isNotEmpty() }?.let { it == signers(installed) }
        )
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().map { sig -> digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    private suspend fun commit(apk: File) = withContext(Dispatchers.IO) {
        ensureReceiver(context)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Android 12+: no confirmation once this launcher installed the current build itself.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("kitt-update.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val intent = PendingIntent.getBroadcast(context, sessionId, Intent(ACTION).setPackage(context.packageName), flags)
                session.commit(intent.intentSender)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            runCatching { installer.abandonSession(sessionId) }
            throw IOException("The installer refused the update: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "SelfUpdate"
        private const val ACTION = "com.aiconsole.knightriderlauncher.UPDATE_STATUS"

        private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)

        /** Shared, so the result still shows if the Updates panel was reopened meanwhile. */
        val state: StateFlow<UpdateState> = _state.asStateFlow()

        fun reset() {
            _state.value = UpdateState.Idle
        }

        private var receiverRegistered = false

        /** The installer reports back here: show its confirmation screen, or the final result. */
        @Synchronized
        private fun ensureReceiver(context: Context) {
            if (receiverRegistered) return
            receiverRegistered = true
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    Log.i(TAG, "Installer status $status: $message")
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                        if (confirm == null) {
                            _state.value = UpdateState.Done(UpdateRules.statusMessage(PackageInstaller.STATUS_FAILURE, message), success = false)
                            return
                        }
                        try {
                            c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: RuntimeException) {
                            Log.w(TAG, "Couldn't show the install confirmation", e)
                            _state.value = UpdateState.Done("Android wouldn't show the install confirmation: ${e.message}", success = false)
                        }
                        return
                    }
                    _state.value = UpdateState.Done(UpdateRules.statusMessage(status, message), success = status == PackageInstaller.STATUS_SUCCESS)
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }
}
