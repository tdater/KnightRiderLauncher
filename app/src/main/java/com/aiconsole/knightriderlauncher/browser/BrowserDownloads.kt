package com.aiconsole.knightriderlauncher.browser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.util.Log
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * A file a page offered for download. Nothing is saved until the driver taps SAVE: any page or
 * ad frame can offer one. [cookies] come from the tab's own profile, so sign-in-only files work.
 */
data class PendingDownload(
    val url: String,
    val userAgent: String?,
    val disposition: String?,
    val mime: String?,
    val cookies: String?
) {
    val name: String get() = URLUtil.guessFileName(url, disposition, mime)
}

/** A downloaded APK waiting for the driver to tap INSTALL. */
data class ReadyApk(val id: Long, val name: String)

/**
 * Files a page offers for download (emulator apps, homebrew games, anything else) go to
 * Downloads/KITT through Android's download manager. A finished APK is offered on the browser bar;
 * the installer opens only when the driver taps INSTALL (and Android asks again).
 */
object BrowserDownloads {

    private const val APK = "application/vnd.android.package-archive"
    private val apks = ConcurrentHashMap<Long, String>()

    private val _readyApk = MutableStateFlow<ReadyApk?>(null)
    val readyApk: StateFlow<ReadyApk?> = _readyApk.asStateFlow()

    @Volatile
    private var watching = false

    /** Starts the download; returns a line for the toolbar. */
    fun enqueue(context: Context, download: PendingDownload): String {
        val url = download.url
        if (!url.startsWith("http")) return "This kind of download can't be saved here (${url.substringBefore(':')} link)."
        val name = download.name
        val manager = context.getSystemService(DownloadManager::class.java) ?: return "Downloads aren't available on this head unit."
        val apk = name.endsWith(".apk", ignoreCase = true) || download.mime == APK
        // Android 10+ lets apps write to Downloads without a storage permission; older units get
        // the launcher's own Download folder, which isn't cleared like the download cache.
        val shared = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val request = DownloadManager.Request(url.toUri()).apply {
            if (apk) setMimeType(APK) else download.mime?.takeIf { it.isNotBlank() }?.let(::setMimeType)
            download.userAgent?.let { addRequestHeader("User-Agent", it) }
            download.cookies?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Cookie", it) }
            setTitle(name)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            if (shared) setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "KITT/$name")
            else setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "KITT/$name")
        }
        val id = try {
            manager.enqueue(request)
        } catch (e: Exception) {
            Log.w("BrowserDownloads", "Download refused", e)
            return "Download failed: ${e.message ?: e.javaClass.simpleName}"
        }
        if (apk) {
            apks[id] = name
            watch(context.applicationContext)
        }
        val where = if (shared) "Downloads/KITT" else "the launcher's folder (Android/data/${context.packageName}/files/Download/KITT)"
        return "Downloading $name to $where" + if (apk) ". INSTALL appears here when it's done." else "."
    }

    /** Opens Android's installer for a finished APK (the driver tapped INSTALL). */
    fun install(context: Context, apk: ReadyApk) {
        _readyApk.value = null
        val uri = context.getSystemService(DownloadManager::class.java)?.getUriForDownloadedFile(apk.id) ?: return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }.onFailure { Log.w("BrowserDownloads", "Couldn't open the installer", it) }
    }

    fun dismissApk() {
        _readyApk.value = null
    }

    private fun watch(app: Context) {
        if (watching) return
        watching = true
        // The download manager is another app, so its broadcast needs an exported receiver. It
        // only ever flags one of our own downloads as finished; it opens nothing.
        ContextCompat.registerReceiver(app, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            val name = apks.remove(id) ?: return
            val manager = context.getSystemService(DownloadManager::class.java) ?: return
            // Only a download that actually finished (not a failed or cancelled one).
            val ok = runCatching {
                manager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                    c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                } ?: false
            }.getOrDefault(false)
            if (ok) _readyApk.value = ReadyApk(id, name)
        }
    }
}
