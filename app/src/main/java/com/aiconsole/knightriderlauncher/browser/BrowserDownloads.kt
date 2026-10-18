package com.aiconsole.knightriderlauncher.browser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.util.concurrent.ConcurrentHashMap

/**
 * Files a page offers for download (emulator apps, homebrew games, anything else) go to
 * Downloads/KITT through Android's download manager, with the page's cookies so sign-in-only
 * downloads work. A downloaded APK offers to install when it finishes (Android asks the driver).
 */
object BrowserDownloads {

    private const val APK = "application/vnd.android.package-archive"
    private val apks = ConcurrentHashMap<Long, String>()

    @Volatile
    private var watching = false

    /** Starts the download; returns a line for the toolbar. */
    fun enqueue(context: Context, url: String, userAgent: String?, disposition: String?, mime: String?): String {
        if (!url.startsWith("http")) return "This kind of download can't be saved here (${url.substringBefore(':')} link)."
        val name = URLUtil.guessFileName(url, disposition, mime)
        val manager = context.getSystemService(DownloadManager::class.java) ?: return "Downloads aren't available on this head unit."
        val apk = name.endsWith(".apk", ignoreCase = true) || mime == APK
        val request = DownloadManager.Request(url.toUri()).apply {
            if (apk) setMimeType(APK) else mime?.takeIf { it.isNotBlank() }?.let(::setMimeType)
            userAgent?.let { addRequestHeader("User-Agent", it) }
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            setTitle(name)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            // Android 10+ lets apps write to Downloads without a storage permission.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "KITT/$name")
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
        return "Downloading $name to Downloads/KITT" + if (apk) ". It offers to install when it's done." else "."
    }

    private fun watch(app: Context) {
        if (watching) return
        watching = true
        // The download manager is another app, so its broadcast needs an exported receiver.
        ContextCompat.registerReceiver(app, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            apks.remove(id) ?: return
            val uri = context.getSystemService(DownloadManager::class.java)?.getUriForDownloadedFile(id) ?: return
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }.onFailure { Log.w("BrowserDownloads", "Couldn't open the installer", it) }
        }
    }
}
