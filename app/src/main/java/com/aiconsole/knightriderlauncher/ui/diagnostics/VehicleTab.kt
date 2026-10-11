package com.aiconsole.knightriderlauncher.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.obd.FillUp
import com.aiconsole.knightriderlauncher.obd.FuelLog
import com.aiconsole.knightriderlauncher.obd.FuelMath
import com.aiconsole.knightriderlauncher.obd.MaintenanceItem
import com.aiconsole.knightriderlauncher.obd.ObdState
import com.aiconsole.knightriderlauncher.obd.PidCatalog
import com.aiconsole.knightriderlauncher.obd.VinDecoder
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.trip.rememberRecentTrips
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val KM_PER_MI = 1.609344

/** Vehicle identity, battery and charging, fuel range and cost, and maintenance reminders. */
@Composable
fun VehicleTab(app: KittApplication) {
    val metric by app.settings.metricUnits.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { IdentityCard(app) }
        item { BatteryCard(app) }
        item { FuelCard(app, metric) }
        item { FuelLogCard(app, metric) }
        item { MaintenanceCard(app, metric) }
    }
}

@Composable
private fun Card(title: String, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(KittBlack)
            .border(1.dp, color.copy(alpha = 0.6f), MaterialTheme.shapes.small)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SectionHeader(title, color = color)
        content()
    }
}

@Composable
private fun Line(label: String, value: String, color: Color = DashWhite) {
    Row(Modifier.fillMaxWidth()) {
        Text(label.uppercase(), Modifier.width(150.dp), style = MaterialTheme.typography.labelMedium, color = DashGrey)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun IdentityCard(app: KittApplication) {
    val scope = rememberCoroutineScope()
    val state by app.obd.state.collectAsState()
    val identity by app.vehicle.identity.collectAsState()
    val vin = (state as? ObdState.Connected)?.info?.vin ?: identity?.vin
    var message by remember { mutableStateOf<String?>(null) }
    val basics = vin?.let(VinDecoder::basics)

    Card("Vehicle", LedGreen) {
        Line("VIN", vin ?: "Not read yet (connect to the vehicle)")
        basics?.let {
            Line("Model year", it.modelYear?.toString() ?: "?")
            Line("Built in", it.region)
            it.checkDigitValid?.let { ok -> Line("Check digit", if (ok) "Valid" else "Mismatch: VIN may be misread", if (ok) LedGreen else AlertRed) }
        }
        identity?.takeIf { it.vin == vin }?.let { id ->
            Line("Vehicle", id.summary())
            id.engine?.let { Line("Engine", it) }
            id.drive?.let { Line("Drive", it) }
            id.fuel?.let { Line("Fuel", it) }
            id.body?.let { Line("Body", it) }
        }
        KittButton(
            label = if (identity?.vin == vin) "Refresh from NHTSA" else "Decode VIN (NHTSA)",
            onClick = {
                val v = vin ?: return@KittButton
                message = "Looking up…"
                scope.launch {
                    message = app.vehicle.lookUp(v).fold({ null }, { "Lookup failed: ${it.message}" })
                }
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            color = LedGreen,
            enabled = vin != null && basics != null
        )
        message?.let { HelpText(it) }
    }
}

@Composable
private fun BatteryCard(app: KittApplication) {
    // Re-evaluate every few seconds from the voltage/RPM history.
    var report by remember { mutableStateOf(app.vehicle.battery()) }
    LaunchedEffect(Unit) {
        while (true) {
            report = app.vehicle.battery()
            delay(3_000)
        }
    }
    val r = report
    Card("Battery & charging", if (r.warnings.isEmpty()) LedGreen else AlertRed) {
        fun v(x: Double?) = x?.let { "%.2f V".format(Locale.US, it) } ?: "--"
        Line("Engine off", "${v(r.restingVolts)}  ${r.restingState.orEmpty()}")
        Line("Last start", "${v(r.crankingMinVolts)}  ${r.crankingState.orEmpty()}")
        Line("Engine running", "${v(r.chargingVolts)}  ${r.chargingState.orEmpty()}")
        HelpText(
            "From the engine computer's supply voltage (or the adapter's reading if the vehicle doesn't report it) over the last 5 minutes. For the engine-off and start readings, " +
                "connect before starting the engine. Readings at the OBD port run a little below the battery terminals."
        )
    }
}

@Composable
private fun FuelCard(app: KittApplication, metric: Boolean) {
    val price by app.settings.fuelPrice.state.collectAsState()
    val tank by app.settings.tankSize.state.collectAsState()
    val readings by app.obd.displayReadings.collectAsState()
    val trips = rememberRecentTrips(app.tripDatabase.tripMetricDao(), days = 30).orEmpty()
    val liters = trips.sumOf { it.fuelLiters ?: 0.0 }
    val km = trips.filter { it.fuelLiters != null }.sumOf { it.distanceKm }
    val avgL100 = if (km >= 1) liters / km * 100 else null
    val range = readings.let { app.vehicle.rangeKm(avgL100) }
    val pricePerLiter = price.toDoubleOrNull()?.let { if (metric) it else it / FuelMath.LITERS_PER_GALLON }
    val volume = if (metric) "L" else "gal"

    Card("Fuel", DashAmber) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittTextField(
                price,
                { app.settings.fuelPrice.set(it.filter { c -> c.isDigit() || c == '.' }) },
                "Price per $volume",
                Modifier.weight(1f),
                keyboardType = KeyboardType.Decimal
            )
            KittTextField(
                tank,
                { app.settings.tankSize.set(it.filter { c -> c.isDigit() || c == '.' }) },
                "Tank size ($volume)",
                Modifier.weight(1f),
                keyboardType = KeyboardType.Decimal
            )
        }
        Line("Fuel level", readings[PidCatalog.FUEL_LEVEL]?.value?.let { "%.0f %%".format(Locale.US, it) } ?: "--")
        Line(
            "Avg economy (30 d)",
            avgL100?.let { if (metric) "%.1f L/100 km".format(Locale.US, it) else "%.1f mpg".format(Locale.US, 235.215 / it) } ?: "--"
        )
        Line("Range", range?.let { if (metric) "%.0f km".format(Locale.US, it) else "%.0f mi".format(Locale.US, it / KM_PER_MI) } ?: "--", LedGreen)
        Line(
            "Fuel cost (30 d)",
            pricePerLiter?.takeIf { liters > 0 }?.let { "%.2f".format(Locale.US, FuelMath.cost(liters, it)) } ?: "--"
        )
        HelpText("Range needs the fuel level sensor, your tank size, and some logged trips with fuel data.")
    }
}

@Composable
private fun MaintenanceCard(app: KittApplication, metric: Boolean) {
    val items by app.vehicle.maintenance.collectAsState()
    val base by app.settings.odometerBaseKm.state.collectAsState()
    val readings by app.obd.displayReadings.collectAsState()
    var odometerInput by remember { mutableStateOf("") }
    val odometer = remember(base, readings) { app.vehicle.odometerKm() }
    val unit = if (metric) "km" else "mi"
    fun show(km: Double) = "%,.0f %s".format(Locale.US, if (metric) km else km / KM_PER_MI, unit)

    Card("Maintenance", DashAmber) {
        Line("Odometer", odometer?.let(::show) ?: "Enter your current reading")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KittTextField(odometerInput, { odometerInput = it.filter(Char::isDigit) }, "Current odometer ($unit)", Modifier.weight(1f), keyboardType = KeyboardType.Number)
            KittButton(
                "Set",
                {
                    odometerInput.toDoubleOrNull()?.let { app.vehicle.setOdometer(if (metric) it else it * KM_PER_MI) }
                    odometerInput = ""
                },
                Modifier.height(48.dp).width(72.dp),
                LedGreen,
                enabled = odometerInput.isNotEmpty()
            )
        }
        if (odometer != null) {
            items.forEach { item ->
                val status = item.status(odometer)
                val color = when (status) {
                    MaintenanceItem.Status.Ok -> LedGreen
                    MaintenanceItem.Status.DueSoon -> DashAmber
                    MaintenanceItem.Status.Overdue -> AlertRed
                }
                val left = item.remainingKm(odometer)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name.uppercase(), style = MaterialTheme.typography.labelLarge, color = color)
                        Text(
                            (if (left <= 0) "Overdue by " else "Due in ") + show(kotlin.math.abs(left)) + " · every " + show(item.intervalKm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = DashGrey
                        )
                    }
                    KittButton("Done", { app.vehicle.markDone(item) }, Modifier.height(40.dp).width(80.dp), color, active = status != MaintenanceItem.Status.Ok)
                }
            }
        }
        HelpText(
            "Uses the ECU odometer when the vehicle reports it; otherwise your reading plus distance driven with the adapter " +
                "connected. KITT reminds you when something is due."
        )
    }
}

/** Fill-up log: real economy full-to-full, and cost per distance. */
@Composable
private fun FuelLogCard(app: KittApplication, metric: Boolean) {
    val stored by app.settings.fuelLog.state.collectAsState()
    val fills = FuelLog.decode(stored)
    val economy = FuelLog.economy(fills)
    var odometer by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(true) }
    val unit = if (metric) "km" else "mi"
    val volume = if (metric) "L" else "gal"
    fun toKm(x: Double) = if (metric) x else x * KM_PER_MI
    fun toLiters(x: Double) = if (metric) x else x * FuelMath.LITERS_PER_GALLON
    fun econ(l100: Double) = if (metric) "%.1f L/100 km".format(Locale.US, l100) else "%.1f mpg".format(Locale.US, 235.215 / l100)

    Card("Fuel log", DashAmber) {
        val current = app.vehicle.odometerKm()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittTextField(
                odometer,
                { odometer = it.filter(Char::isDigit) },
                current?.let { "Odometer (%.0f)".format(Locale.US, if (metric) it else it / KM_PER_MI) } ?: "Odometer ($unit)",
                Modifier.weight(1f),
                keyboardType = KeyboardType.Number
            )
            KittTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Fuel ($volume)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
            KittTextField(cost, { cost = it.filter { c -> c.isDigit() || c == '.' } }, "Total cost", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton(if (full) "Filled to full" else "Partial fill", { full = !full }, Modifier.weight(1f).height(44.dp), LedGreen, active = full)
            KittButton(
                "Add fill-up",
                {
                    val odo = odometer.toDoubleOrNull()?.let(::toKm) ?: current
                    val liters = amount.toDoubleOrNull()?.let(::toLiters)
                    if (odo != null && liters != null && liters > 0) {
                        app.settings.fuelLog.set(FuelLog.encode(fills + FillUp(System.currentTimeMillis(), odo, liters, cost.toDoubleOrNull(), full)))
                        odometer = ""
                        amount = ""
                        cost = ""
                    }
                },
                Modifier.weight(1f).height(44.dp),
                DashAmber,
                active = true,
                enabled = amount.toDoubleOrNull() != null && (odometer.isNotEmpty() || current != null)
            )
        }
        FuelLog.average(economy)?.let { Line("Measured average", econ(it), LedGreen) }
        FuelLog.costPerKm(fills)?.let { perKm -> Line("Cost per $unit", "%.3f".format(Locale.US, if (metric) perKm else perKm * KM_PER_MI)) }
        fills.takeLast(6).reversed().forEach { f ->
            val e = economy.firstOrNull { it.endedAt == f.timestamp }
            Line(
                java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(f.timestamp)),
                "%.1f %s at %,.0f %s%s%s".format(
                    Locale.US,
                    if (metric) f.liters else f.liters / FuelMath.LITERS_PER_GALLON, volume,
                    if (metric) f.odometerKm else f.odometerKm / KM_PER_MI, unit,
                    if (f.full) "" else " (partial)",
                    e?.let { " · " + econ(it.litersPer100Km) } ?: ""
                )
            )
        }
        if (fills.isNotEmpty()) {
            KittButton(
                "Remove last entry",
                { app.settings.fuelLog.set(FuelLog.encode(fills.dropLast(1))) },
                Modifier.fillMaxWidth().height(40.dp),
                AlertRed
            )
        }
        HelpText("Economy is measured between full fill-ups, including partial fills in between. Leave odometer empty to use the current estimate.")
    }
}
