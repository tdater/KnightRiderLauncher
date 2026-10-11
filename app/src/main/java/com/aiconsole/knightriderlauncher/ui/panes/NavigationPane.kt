package com.aiconsole.knightriderlauncher.ui.panes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.nav.NavInstruction
import com.aiconsole.knightriderlauncher.nav.NavStatus
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.components.LedBarGauge
import com.aiconsole.knightriderlauncher.ui.gauges.SegmentDisplay
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.LocalLiteMode
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed

/** Live driving readouts overlaid on the navigation pane, KITT-dash style. */
data class HudState(
    val speed: String? = null,
    val speedUnit: String = "MPH",
    val rpmFraction: Float = 0f,
    val rpmText: String? = null,
    val linkLabel: String = "OBD OFFLINE",
    val linked: Boolean = false,
    /** The road's speed limit in the driver's units (OpenStreetMap), when known. */
    val speedLimit: Int? = null,
    /** Over the limit by more than the driver's margin. */
    val overLimit: Boolean = false
)

/**
 * Top pane: a themed OpenStreetMap that follows the car (or the 80s vector grid when the map
 * is off or location isn't allowed) with destination search and KITT's own spoken turn-by-turn
 * route, the next turn from Google Maps / Waze otherwise, and KITT's digital speedometer and
 * bar-graph tach on top.
 *
 * [hud] is read inside the small readout composables only, so live OBD updates redraw the
 * readouts without recomposing the map.
 */
@Composable
fun NavigationPane(
    modifier: Modifier = Modifier,
    hud: () -> HudState = { HudState() },
    showMap: Boolean = false,
    mapActive: Boolean = true,
    nav: NavInstruction? = null,
    /** Opens the streaming window over this pane; null hides the button. */
    onStream: (() -> Unit)? = null
) {
    val context = LocalContext.current
    fun hasLocation() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var locationGranted by remember { mutableStateOf(hasLocation()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationGranted = hasLocation()
    }
    val mapController = remember { NavMapController() }
    val mapOn = showMap && locationGranted
    val app = context.applicationContext as KittApplication
    val navigator = app.navigator
    val route by navigator.state.collectAsState()
    val metric by app.settings.metricUnits.state.collectAsState()
    var searching by remember { mutableStateOf(false) }
    val guiding = route.status == NavStatus.Navigating || route.status == NavStatus.Arrived

    // Preview shows the whole route; guidance follows the car again.
    LaunchedEffect(route.status, route.route, mapOn) {
        if (!mapOn) return@LaunchedEffect
        when (route.status) {
            NavStatus.Preview -> route.route?.let { mapController.showWhole(it.points) }
            NavStatus.Navigating -> mapController.recenter()
            else -> Unit
        }
    }

    KittPanel(
        title = "Navigation",
        modifier = modifier,
        accent = ScannerRed,
        trailing = { LinkIndicator(hud) }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
                .background(KittBlack)
        ) {
            if (mapOn) {
                NavMap(
                    ScannerRed,
                    mapActive,
                    mapController,
                    Modifier.fillMaxSize(),
                    route = route.route.takeIf { route.status == NavStatus.Preview || route.status == NavStatus.Navigating },
                    routeColor = DashAmber
                )
            } else {
                NavigationVisualizer(Modifier.fillMaxSize())
            }

            Column(
                Modifier.align(Alignment.TopStart).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when {
                    guiding -> RouteBanner(route, metric)
                    nav != null -> TurnBanner(nav)
                    showMap && !locationGranted -> KittButton(
                        label = "Enable map (location)",
                        onClick = {
                            permission.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        },
                        modifier = Modifier.height(40.dp),
                        color = ScannerRed,
                        active = true
                    )
                    !showMap -> Text("MAP MODULE OFFLINE", style = MaterialTheme.typography.labelSmall, color = DashGrey)
                }
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onStream != null) {
                    KittButton("Stream", onStream, Modifier.height(36.dp).width(84.dp), ScannerRed, active = true)
                }
                if (mapOn && route.status == NavStatus.Idle) {
                    KittButton("Search", { searching = true }, Modifier.height(36.dp).width(84.dp), ScannerRed, active = true)
                }
                if (mapOn) {
                    KittButton("Center", mapController::recenter, Modifier.height(36.dp).width(84.dp), ScannerRed)
                }
            }

            SpeedReadout(hud, Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 30.dp))
            TachBar(hud, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))

            if (route.status != NavStatus.Idle && route.status != NavStatus.Arrived) {
                // Left of the speedometer, above the tach.
                RouteFooter(route, metric, navigator, Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 170.dp, bottom = 40.dp))
            }
            if (searching) {
                RouteSearchPanel(
                    navigator,
                    metric,
                    onPick = { place ->
                        searching = false
                        navigator.preview(place)
                    },
                    onClose = { searching = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun LinkIndicator(hud: () -> HudState) {
    val state = hud()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (state.linked) LedGreen else DashGrey)
        )
        Text(state.linkLabel, style = MaterialTheme.typography.labelMedium, color = if (state.linked) LedGreen else DashGrey)
    }
}

/** Digital speedometer. */
@Composable
private fun SpeedReadout(hud: () -> HudState, modifier: Modifier = Modifier) {
    val state = hud()
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        state.speedLimit?.let { SpeedLimitSign(it, state.overLimit) }
        Column(horizontalAlignment = Alignment.End) {
            SegmentDisplay(
                text = state.speed ?: "--",
                modifier = Modifier.width(150.dp).height(72.dp),
                color = if (state.overLimit) ScannerRed else DashAmber,
                cells = 3
            )
            Text(state.speedUnit, style = MaterialTheme.typography.labelMedium, color = DashAmber)
        }
    }
}

/** The road's limit as a small sign; it turns red while the driver is over it. */
@Composable
private fun SpeedLimitSign(limit: Int, over: Boolean) {
    val shape = MaterialTheme.shapes.small
    Column(
        Modifier
            .width(54.dp)
            .clip(shape)
            .background(KittBlack.copy(alpha = 0.85f))
            .border(2.dp, if (over) ScannerRed else DashWhite, shape)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("LIMIT", style = MaterialTheme.typography.labelSmall, color = if (over) ScannerRed else DashWhite)
        Text(limit.toString(), style = MaterialTheme.typography.titleLarge, color = if (over) ScannerRed else DashWhite)
    }
}

/** Bar-graph tachometer along the bottom edge. */
@Composable
private fun TachBar(hud: () -> HudState, modifier: Modifier = Modifier) {
    val state = hud()
    Column(modifier) {
        LedBarGauge(
            level = state.rpmFraction,
            modifier = Modifier.fillMaxWidth().height(12.dp),
            segments = 32
        )
        Text(
            text = state.rpmText?.let { "$it RPM" } ?: "RPM",
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = DashGrey
        )
    }
}

/** Next maneuver relayed from the navigation app's notification. */
@Composable
private fun TurnBanner(nav: NavInstruction) {
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier
            .widthIn(max = 420.dp)
            .clip(shape)
            .background(KittBlack.copy(alpha = 0.88f))
            .border(1.dp, ScannerRed, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        nav.maneuver?.let { bitmap ->
            Image(
                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                colorFilter = ColorFilter.tint(ScannerRed)
            )
        }
        Column {
            Text(nav.primary, style = MaterialTheme.typography.titleMedium, color = DashWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
            nav.secondary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = DashAmber, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOfNotNull(nav.eta, nav.app.uppercase()).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = DashGrey,
                maxLines = 1
            )
        }
    }
}

/** 80s vector-graphics perspective grid with a route line and pulsing position marker. */
@Composable
private fun NavigationVisualizer(modifier: Modifier = Modifier) {
    // Lite mode: the marker holds still instead of pulsing every frame.
    val pulse = if (LocalLiteMode.current) 0.5f else navPulse()
    Canvas(modifier) {
        val horizon = size.height * 0.3f
        val vanishingPoint = Offset(size.width / 2, horizon)
        val gridColor = ScannerRed.copy(alpha = 0.22f)
        val stroke = 1.dp.toPx()

        val rays = 14
        for (i in -rays..rays) {
            val x = size.width / 2 + i * size.width / rays
            drawLine(gridColor, vanishingPoint, Offset(x, size.height), stroke)
        }
        val rows = 12
        for (row in 1..rows) {
            val t = row.toFloat() / rows
            val y = horizon + (size.height - horizon) * t * t
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), stroke)
        }
        drawLine(ScannerRed.copy(alpha = 0.6f), Offset(0f, horizon), Offset(size.width, horizon), 1.5.dp.toPx())

        val route = Path().apply {
            moveTo(size.width * 0.5f, size.height)
            cubicTo(
                size.width * 0.5f, size.height * 0.75f,
                size.width * 0.64f, size.height * 0.55f,
                size.width * 0.56f, horizon
            )
        }
        drawPath(route, DashAmber.copy(alpha = 0.3f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
        drawPath(route, DashAmber, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

        val marker = Offset(size.width * 0.5f, size.height * 0.8f)
        drawCircle(
            color = ScannerRed.copy(alpha = (1f - pulse) * 0.6f),
            radius = 12.dp.toPx() + pulse * 28.dp.toPx(),
            center = marker,
            style = Stroke(2.dp.toPx())
        )
        val arrow = Path().apply {
            moveTo(marker.x, marker.y - 14.dp.toPx())
            lineTo(marker.x - 10.dp.toPx(), marker.y + 10.dp.toPx())
            lineTo(marker.x, marker.y + 4.dp.toPx())
            lineTo(marker.x + 10.dp.toPx(), marker.y + 10.dp.toPx())
            close()
        }
        drawPath(arrow, ScannerRed)
    }
}

@Composable
private fun navPulse(): Float {
    val transition = rememberInfiniteTransition(label = "nav")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
        label = "navPulse"
    )
    return pulse
}
