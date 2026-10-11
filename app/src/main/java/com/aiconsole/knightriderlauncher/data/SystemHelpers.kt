package com.aiconsole.knightriderlauncher.data

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Whether this launcher is the home screen, and the way back if it isn't. */
object HomeScreen {

    fun isDefault(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** The system's default-home picker (falls back to the general app settings). */
    fun openSettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
}

/**
 * Weekly backup into Downloads/KITT (the same file format as Backup in KITT Config, so it
 * restores into either edition). Android 10+ writes through MediaStore with no permission;
 * older versions use the app's own external folder.
 */
class AutoBackup(private val context: Context, private val edition: String) {

    /** Writes a backup if one is due; returns the file's description, or null. Call off the main thread. */
    fun runIfDue(now: Long, last: Long): String? {
        if (!AutoBackupRules.due(now, last)) return null
        val name = AutoBackupRules.fileName(edition, SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now)))
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) writeMediaStore(name) else writeLegacy(name)
        } catch (e: IOException) {
            Log.w(TAG, "Automatic backup failed", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Automatic backup not allowed", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeMediaStore(name: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/KITT")
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create $name")
        resolver.openOutputStream(uri)?.use { LauncherBackup(context).export(it) } ?: throw IOException("Couldn't write $name")
        // Keep the newest few of this edition's own backups (MediaStore only lists our own files).
        val prefix = "kitt-backup-$edition-"
        val existing = mutableListOf<Pair<String, Long>>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/KITT%"),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val display = c.getString(1) ?: continue
                if (display.startsWith(prefix)) existing += display to c.getLong(0)
            }
        }
        val prune = AutoBackupRules.toPrune(existing.map { it.first }).toSet()
        existing.filter { it.first in prune }.forEach { (_, id) ->
            runCatching { resolver.delete(android.content.ContentUris.withAppendedId(collection, id), null, null) }
        }
        return "Downloads/KITT/$name"
    }

    private fun writeLegacy(name: String): String {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "KITT").apply { mkdirs() }
        File(dir, name).outputStream().use { LauncherBackup(context).export(it) }
        val prefix = "kitt-backup-$edition-"
        val names = dir.list()?.filter { it.startsWith(prefix) }.orEmpty()
        AutoBackupRules.toPrune(names).forEach { File(dir, it).delete() }
        return File(dir, name).path
    }

    private companion object {
        const val TAG = "AutoBackup"
    }
}
