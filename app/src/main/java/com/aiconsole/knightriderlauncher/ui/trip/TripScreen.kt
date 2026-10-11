package com.aiconsole.knightriderlauncher.ui.trip

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.data.trip.ReportCard
import com.aiconsole.knightriderlauncher.data.trip.ReportCards
import com.aiconsole.knightriderlauncher.data.trip.TripCsv
import com.aiconsole.knightriderlauncher.data.trip.TripMetricDao
import com.aiconsole.knightriderlauncher.data.trip.TripSummarizer
import com.aiconsole.knightriderlauncher.data.trip.TripSummary
import com.aiconsole.knightriderlauncher.nav.LatLon
import com.aiconsole.knightriderlauncher.nav.ParkingSpot
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.components.QrCode
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.gauges.SegmentDisplay
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Trips from the last [days] days, newest first. Refreshes every 15 s while shown. */
@Composable
fun rememberRecentTrips(dao: TripMetricDao, days: Long = 7): List<TripSummary>? {
    val trips by produceState<List<TripSummary>?>(null, dao, days) {
        while (true) {
            value = loadTrips(dao, days)
            delay(15_000)
        }
    }
    return trips
}

private suspend fun loadTrips(dao: TripMetricDao, days: Long): List<TripSummary> = try {
    val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)
    val samples = withContext(Dispatchers.IO) { dao.samplesSince(since) }
    withContext(Dispatchers.Default) { TripSummarizer.trips(samples).reversed() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    emptyList()
}

/** Trip computer: the latest drive in big LED readouts, recent drives below. */
@Composable
fun TripScreen(onDismiss: () -> Unit, onAskKitt: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val metric by app.settings.metricUnits.state.collectAsState()
    val trips = rememberRecentTrips(app.tripDatabase.tripMetricDao())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            exportMessage = try {
                val all = loadTrips(app.tripDatabase.tripMetricDao(), EXPORT_DAYS)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(TripCsv.build(all, metric).toByteArray()) }
                        ?: throw java.io.IOException("Couldn't open the file")
                }
                "Exported ${all.size} trips."
            } catch (e: java.io.IOException) {
                "Export failed: ${e.message}"
            } catch (e: SecurityException) {
                "Export failed: ${e.message}"
            }
        }
    }

    BackHandler(onBack = onDismiss)

    KittPanel(
        title = "Trip Computer",
        modifier = modifier.pointerInput(Unit) { detectTapGestures {} },
        accent = DashAmber,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KittButton("Export CSV", { export.launch("kitt-trips.csv") }, Modifier.height(32.dp), DashAmber)
                KittButton("Close", onDismiss, Modifier.height(32.dp).width(80.dp), DashGrey)
            }
        }
    ) {
        val latest = trips?.firstOrNull()
        val spotText by app.settings.parkingSpot.state.collectAsState()
        val cardLog by app.settings.reportCardLog.state.collectAsState()
        val spot = remember(spotText) { ParkingSpot.decode(spotText) }
        val cards = remember(cardLog) { ReportCards.decode(cardLog).reversed() }
        // One scrolling list, so short landscape screens reach everything.
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exportMessage?.let { item { HelpText(it) } }
            when {
                trips == null -> item { HelpText("Reading the trip log…") }
                latest == null -> item {
                    HelpText(
                        "No drives logged yet. Trips are recorded automatically while the OBD adapter is connected " +
                            "and the engine is running."
                    )
                }
                else -> {
                    item { SectionHeader("Last drive · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(latest.start))}", color = DashAmber) }
                    item { TripReadouts(latest, metric) }
                    item {
                        KittButton(
                            label = "KITT, how was that drive?",
                            onClick = {
                                onAskKitt(
                                    "Here's my last drive: ${TripSummarizer.describe(latest, metric)} " +
                                        "How was that drive? Comment on efficiency and my driving, and anything worth checking."
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            color = ScannerRed,
                            active = true
                        )
                    }
                }
            }
            spot?.let { parked -> item { ParkingCard(parked, app) } }
            if (cards.isNotEmpty()) {
                item { SectionHeader("Report cards", color = DashGrey) }
                items(cards.take(10), key = { "card${it.tripStart}" }) { card -> ReportCardRow(card, metric) }
            }
            if (!trips.isNullOrEmpty() && trips.size > 1) {
                item { SectionHeader("Earlier this week", color = DashGrey) }
                items(trips.drop(1), key = { it.start }) { trip -> TripRow(trip, metric) }
            }
        }
    }
}

/** Where the vehicle was switched off, with a code that puts it on the driver's phone. */
@Composable
private fun ParkingCard(spot: ParkingSpot, app: KittApplication) {
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, KittBezel, shape).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QrCode(spot.mapUrl, Modifier.size(120.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("PARKED", style = MaterialTheme.typography.labelLarge, color = LedGreen)
            Text(spot.address ?: "%.5f, %.5f".format(Locale.US, spot.lat, spot.lon), style = MaterialTheme.typography.bodyLarge, color = DashWhite)
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(spot.at)), style = MaterialTheme.typography.bodySmall, color = DashGrey)
            Text("Scan with your phone to keep the spot in your maps app.", style = MaterialTheme.typography.bodySmall, color = DashGrey)
            KittButton(
                "Route here",
                { app.navigator.previewTo("Parking spot", LatLon(spot.lat, spot.lon)) },
                Modifier.height(40.dp).width(140.dp),
                DashAmber
            )
        }
    }
}

@Composable
private fun ReportCardRow(card: ReportCard, metric: Boolean) {
    val color = when {
        card.grade.startsWith("A") -> LedGreen
        card.grade.startsWith("B") || card.grade.startsWith("C") -> DashAmber
        else -> ScannerRed
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(card.grade, Modifier.width(48.dp), style = MaterialTheme.typography.headlineSmall, color = color)
        Column(Modifier.weight(1f)) {
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(card.tripStart)), style = MaterialTheme.typography.labelMedium, color = DashGrey)
            Text(ReportCards.summary(card, metric).substringAfter(": "), style = MaterialTheme.typography.bodyMedium, color = DashWhite)
        }
    }
}

@Composable
private fun TripReadouts(trip: TripSummary, metric: Boolean) {
    val distance = if (metric) trip.distanceKm else trip.distanceKm * 0.621371
    val minutes = trip.durationMs / 60_000.0
    val cells = listOf(
        Readout("Distance", "%.1f".format(Locale.US, distance), if (metric) "KM" else "MI", DashAmber),
        if (minutes < 100) Readout("Time", minutes.roundToInt().toString(), "MIN", DashAmber)
        else Readout("Time", "%.1f".format(Locale.US, minutes / 60), "HR", DashAmber),
        Readout(
            "Avg speed",
            (if (metric) trip.avgMovingSpeedKmh else trip.avgMovingSpeedKmh * 0.621371).roundToInt().toString(),
            if (metric) "KM/H" else "MPH",
            LedGreen
        ),
        Readout(
            "Top speed",
            (if (metric) trip.maxSpeedKmh.toDouble() else trip.maxSpeedKmh * 0.621371).roundToInt().toString(),
            if (metric) "KM/H" else "MPH",
            LedGreen
        ),
        Readout(
            "Economy",
            (if (metric) trip.litersPer100Km else trip.mpg)?.let { "%.1f".format(Locale.US, it) } ?: "--",
            if (metric) "L/100KM" else "MPG",
            ScannerRed
        ),
        Readout("Idle", (trip.idleMs / 60_000).toString(), "MIN", DashGrey)
    )
    cells.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { ReadoutCell(it, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

private data class Readout(val label: String, val value: String, val unit: String, val color: Color)

@Composable
private fun ReadoutCell(readout: Readout, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .clip(shape)
            .background(KittBlack)
            .border(1.dp, KittBezel, shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(readout.label.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = DashGrey)
            Text(readout.unit, style = MaterialTheme.typography.labelSmall, color = DashGrey)
        }
        SegmentDisplay(readout.value, Modifier.fillMaxWidth().weight(1f), readout.color, cells = 4)
    }
}

@Composable
private fun TripRow(trip: TripSummary, metric: Boolean) {
    val shape = MaterialTheme.shapes.small
    val distance = if (metric) "%.1f km".format(Locale.US, trip.distanceKm) else "%.1f mi".format(Locale.US, trip.distanceKm * 0.621371)
    val economy = (if (metric) trip.litersPer100Km?.let { "%.1f L/100km".format(Locale.US, it) } else trip.mpg?.let { "%.1f mpg".format(Locale.US, it) })
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KittBlack)
            .border(1.dp, KittBezel, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(trip.start)),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = DashWhite
        )
        Text(distance, style = MaterialTheme.typography.bodyMedium, color = DashAmber)
        Text(TripSummarizer.duration(trip.durationMs), style = MaterialTheme.typography.bodyMedium, color = DashGrey)
        Text(economy ?: "--", style = MaterialTheme.typography.bodyMedium, color = LedGreen)
    }
}

/** Trip log retention is 90 days; export all of it. */
private const val EXPORT_DAYS = 90L
