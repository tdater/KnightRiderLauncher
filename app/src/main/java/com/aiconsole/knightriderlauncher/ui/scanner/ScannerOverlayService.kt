package com.aiconsole.knightriderlauncher.ui.scanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.R
import com.aiconsole.knightriderlauncher.data.DeviceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Whether the launcher itself is on screen (then it draws its own scanner). */
object LauncherVisibility {
    val visible = MutableStateFlow(false)
}

/**
 * Draws the KITT scanner across the top of the screen over every other app, so the car
 * never loses its signature light. The window is not touchable and kept at 80% opacity,
 * the ceiling Android 12+ allows for touches to pass through to the app underneath.
 */
class ScannerOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var view: ScannerStripView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val strip = ScannerStripView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (STRIP_HEIGHT_DP * resources.displayMetrics.density).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            alpha = 0.8f
        }
        getSystemService(WindowManager::class.java).addView(strip, params)
        view = strip

        scope.launch {
            LauncherVisibility.visible.collect { launcherShown ->
                strip.visibility = if (launcherShown) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        view?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        view = null
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kitt)
            .setContentTitle("KITT scanner active")
            .setContentText("Showing the scanner over other apps")
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Scanner overlay", NotificationManager.IMPORTANCE_MIN)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "kitt_scanner"
        private const val NOTIFICATION_ID = 7002
        const val STRIP_HEIGHT_DP = 10

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                ContextCompat.startForegroundService(context, Intent(context, ScannerOverlayService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScannerOverlayService::class.java))
        }
    }
}

/** Plain View renderer for the overlay window (no Compose runtime needed outside an Activity). */
private class ScannerStripView(context: Context) : View(context) {
    private val lite: Boolean get() = (context.applicationContext as? KittApplication)?.lite?.value == true
    private val engine = ScannerEngine()
    private val lens = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 30, 30) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val levels = engine.step(System.nanoTime())
        val lamps = engine.lamps
        val gap = height * 0.22f
        val lampWidth = (width - gap * (lamps + 1)) / lamps
        val lampHeight = height * 0.6f
        val top = (height - lampHeight) / 2
        for (i in 0 until lamps) {
            val x = gap + i * (lampWidth + gap)
            rect.set(x, top, x + lampWidth, top + lampHeight)
            canvas.drawRoundRect(rect, 3f, 3f, lens)
            val level = levels[i]
            if (level < 0.02f) continue
            glow.color = Color.argb((110 * level).toInt(), 255, 30, 30)
            canvas.drawRect(x - lampWidth * 0.2f, 0f, x + lampWidth * 1.2f, height.toFloat(), glow)
            core.color = Color.argb((255 * level).toInt(), 255, (30 + 120 * level).toInt(), (30 + 90 * level).toInt())
            canvas.drawRoundRect(rect, 3f, 3f, core)
        }
        if (visibility == VISIBLE) nextFrame()
    }

    /** Lite mode draws at ~30 fps over other apps; otherwise at the display's rate. */
    private fun nextFrame() {
        val frameMs = DeviceProfile.scannerFrameNanos(lite) / 1_000_000L
        if (frameMs > 0) postInvalidateDelayed(frameMs) else postInvalidateOnAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }
}
