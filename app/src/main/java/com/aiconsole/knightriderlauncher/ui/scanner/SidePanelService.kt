package com.aiconsole.knightriderlauncher.ui.scanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.MainActivity
import com.aiconsole.knightriderlauncher.R
import com.aiconsole.knightriderlauncher.nav.SpeedLimits
import com.aiconsole.knightriderlauncher.obd.ObdManager
import com.aiconsole.knightriderlauncher.obd.PidCatalog
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * "Maps + KITT": KITT's essentials in a narrow panel over a full-screen navigation app, since a
 * home-screen launcher can't share the screen with another app in split-screen. On a landscape
 * screen it runs down the right edge, on a portrait one along the bottom: speed, the road's
 * limit, revs, coolant and fuel, media keys, a KITT button and a close button. It hides while
 * the launcher itself is showing.
 */
class SidePanelService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var panel: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "KITT side panel", NotificationManager.IMPORTANCE_MIN))
        }
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
        val landscape = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
        val view = build(landscape)
        val params = WindowManager.LayoutParams(
            if (landscape) dp(150) else WindowManager.LayoutParams.MATCH_PARENT,
            if (landscape) WindowManager.LayoutParams.MATCH_PARENT else dp(104),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = if (landscape) Gravity.END or Gravity.TOP else Gravity.BOTTOM }
        getSystemService(WindowManager::class.java).addView(view, params)
        panel = view
        scope.launch { LauncherVisibility.visible.collect { shown -> view.visibility = if (shown) View.GONE else View.VISIBLE } }
    }

    override fun onDestroy() {
        panel?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        panel = null
        scope.cancel()
        super.onDestroy()
    }

    private fun build(landscape: Boolean): View {
        val app = application as KittApplication
        val root = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 12, 10, 10))
                setStroke(dp(1), Color.argb(255, 120, 20, 14))
            }
        }
        val speed = text(40f, AMBER, bold = true)
        val unit = text(12f, GREY)
        val limit = text(14f, Color.WHITE)
        val rpm = text(14f, AMBER)
        val coolant = text(14f, AMBER)
        val fuel = text(14f, AMBER)
        val readouts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            listOf(speed, unit, limit, rpm, coolant, fuel).forEach(::addView)
        }
        root.addView(readouts, LinearLayout.LayoutParams(if (landscape) -1 else 0, if (landscape) 0 else -1, 1f))
        val buttons = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            addView(button("◀◀") { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) })
            addView(button("▶ ❚❚") { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) })
            addView(button("▶▶") { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) })
            addView(button("KITT", RED) {
                val intent = Intent(Intent.ACTION_VOICE_COMMAND).setClass(this@SidePanelService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(intent) }
            })
            addView(button("✕", GREY) { stopSelf() })
        }
        root.addView(buttons, LinearLayout.LayoutParams(if (landscape) -1 else -2, -2))

        scope.launch {
            while (isActive) {
                val metric = app.settings.metricUnits.value
                val now = System.currentTimeMillis()
                fun fresh(id: String) = app.obd.readings.value[id]?.takeIf { now - it.timestamp < 5_000 }?.value
                val kmh = fresh(PidCatalog.SPEED) ?: app.gps.freshSpeedKmh()
                speed.text = kmh?.let { (if (metric) it else it * 0.621371).roundToInt().toString() } ?: "--"
                unit.text = if (metric) "KM/H" else "MPH"
                limit.text = app.speedLimits.limitKmh.value?.let { "LIMIT ${SpeedLimits.display(it, metric)}" }.orEmpty()
                rpm.text = fresh(PidCatalog.RPM)?.let { "RPM ${it.roundToInt()}" } ?: "RPM --"
                coolant.text = format(app, PidCatalog.COOLANT, "TEMP", metric)
                fuel.text = format(app, PidCatalog.FUEL_LEVEL, "FUEL", metric)
                delay(1_000)
            }
        }
        return root
    }

    private fun format(app: KittApplication, id: String, label: String, metric: Boolean): String {
        val def = app.obd.definitions.value[id] ?: return "$label --"
        val value = app.obd.readings.value[id]?.takeIf { System.currentTimeMillis() - it.timestamp < 10_000 }?.value ?: return "$label --"
        return "$label ${ObdManager.formatValue(def.displayValue(value, metric), def)}${def.unit(metric)}"
    }

    private fun mediaKey(code: Int) {
        val audio = getSystemService(AudioManager::class.java) ?: return
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun text(sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
        maxLines = 1
    }

    private fun button(label: String, color: Int = AMBER, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        gravity = Gravity.CENTER
        minHeight = dp(46)
        minWidth = dp(56)
        background = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(dp(1), color)
            cornerRadius = dp(4).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kitt)
            .setContentTitle("KITT side panel")
            .setContentText("Showing KITT next to your navigation app")
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "kitt_side_panel"
        private const val NOTIFICATION_ID = 7003
        private val AMBER = Color.rgb(255, 176, 0)
        private val RED = Color.rgb(255, 42, 26)
        private val GREY = Color.rgb(163, 152, 148)

        /** Navigation apps tried in order when the driver hasn't picked one. */
        private val NAV_APPS = listOf("com.google.android.apps.maps", "com.waze", "net.osmand", "net.osmand.plus", "com.sygic.aura", "com.here.app.maps")

        /**
         * Opens the navigation app and KITT's panel beside it. Returns null on success, or what
         * the driver needs to do (overlay permission, no navigation app).
         */
        fun startWithMaps(context: Context, preferred: String): String? {
            if (!Settings.canDrawOverlays(context)) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                return "Allow \"display over other apps\" for KITT, then try again."
            }
            val pm = context.packageManager
            val launch = (listOf(preferred).filter { it.isNotBlank() } + NAV_APPS).firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
                ?: return "No navigation app found. Install Google Maps, Waze or OsmAnd."
            ContextCompat.startForegroundService(context, Intent(context, SidePanelService::class.java))
            return try {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                null
            } catch (_: ActivityNotFoundException) {
                "Couldn't open the navigation app."
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SidePanelService::class.java))
        }
    }
}
