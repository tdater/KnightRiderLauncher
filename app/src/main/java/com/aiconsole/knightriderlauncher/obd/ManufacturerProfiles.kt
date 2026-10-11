package com.aiconsole.knightriderlauncher.obd

/** An addressable control module on the CAN bus (request header; ECU replies on header + 8). */
data class EcuDefinition(val name: String, val header: String)

/**
 * Manufacturer options: which ECUs to address beyond the engine, extended (Mode 21/22)
 * PIDs, and VIN world-manufacturer-identifier prefixes for auto-detection.
 */
data class ManufacturerProfile(
    val id: String,
    val name: String,
    val wmiPrefixes: List<String>,
    val ecus: List<EcuDefinition>,
    val pids: List<PidDefinition> = emptyList(),
    val notes: String = ""
)

object ManufacturerProfiles {

    const val AUTO = "auto"

    private val ENGINE = EcuDefinition("Engine (PCM/ECM)", "7E0")
    private val TRANS_7E1 = EcuDefinition("Transmission (TCM)", "7E1")

    private fun extended(
        id: String,
        name: String,
        short: String,
        command: String,
        header: String,
        formula: String,
        quantity: Quantity,
        min: Double,
        max: Double,
        note: String
    ) = PidDefinition(
        id = id,
        name = name,
        shortName = short,
        command = command,
        formula = formula,
        quantity = quantity,
        min = min,
        max = max,
        bytes = FormulaEvaluator.bytesUsed(formula),
        header = header,
        source = PidSource.MANUFACTURER,
        note = note
    )

    private fun tirePressure(id: String, name: String, short: String, command: String) = extended(
        id, name, short, command, "241", "((A*256)+B)/16", Quantity.PRESSURE, 0.0, 450.0,
        "Community-sourced (OBDb, GM body control module, verified on a 2022 Chevrolet Blazer). " +
            "Older GM vehicles may not answer; shows no reading then."
    )

    val generic = ManufacturerProfile(
        id = "generic",
        name = "Generic OBD-II",
        wmiPrefixes = emptyList(),
        ecus = listOf(ENGINE, TRANS_7E1),
        notes = "Standard emissions data only. Works on any 1996+ (US) / 2001+ (EU petrol) vehicle."
    )

    val all: List<ManufacturerProfile> = listOf(
        generic,
        ManufacturerProfile(
            id = "gm",
            name = "GM (Chevrolet, GMC, Buick, Cadillac)",
            wmiPrefixes = listOf("1G", "2G", "3G", "KL", "6G", "W06"),
            ecus = listOf(ENGINE, EcuDefinition("Transmission (TCM)", "7E2")),
            pids = listOf(
                extended(
                    "gm_trans_temp", "Transmission fluid temperature", "Trans temp",
                    "221940", "7E2", "A-40", Quantity.TEMPERATURE, -40.0, 150.0,
                    "Community-sourced (GM Mode 22). Some models answer on the broadcast header instead of 7E2."
                ),
                // Tire pressures from the body control module. Source: OBDb (github.com/OBDb), verified
                // on a 2022 Chevrolet Blazer (228-248 kPa). The engine module's 22 248E-2491 reads are
                // NOT used: OBDb reports no GM vehicle answering them.
                tirePressure("gm_tpms_lf", "Tire pressure, left front", "Tire LF", "225005"),
                tirePressure("gm_tpms_rf", "Tire pressure, right front", "Tire RF", "225006"),
                tirePressure("gm_tpms_rr", "Tire pressure, right rear", "Tire RR", "225007"),
                tirePressure("gm_tpms_lr", "Tire pressure, left rear", "Tire LR", "225008")
            ),
            notes = "GM transmissions (6L80/8L90/10L80 and others) usually answer on header 7E2."
        ),
        ManufacturerProfile(
            id = "ford",
            name = "Ford / Lincoln",
            wmiPrefixes = listOf("1F", "2F", "3F", "1L", "5L", "MAJ", "WF0", "NM0", "1ZV", "6FP"),
            ecus = listOf(ENGINE, TRANS_7E1),
            pids = listOf(
                extended(
                    "ford_trans_temp", "Transmission fluid temperature", "Trans temp",
                    "221E1C", "7E1", "((signed(A)*256)+B)/16", Quantity.TEMPERATURE, -40.0, 150.0,
                    "Community-sourced; the formula varies by transmission. Check it against a cold-start reading."
                )
            ),
            notes = "Ford PCM on 7E0, TCM on 7E1. Mode 22 PIDs; formulas vary between 6R80, 10R80 and 6F transmissions."
        ),
        ManufacturerProfile(
            id = "toyota",
            name = "Toyota / Lexus",
            wmiPrefixes = listOf("JT", "4T", "5T", "2T", "6T1", "MR0", "NMT", "SB1"),
            ecus = listOf(ENGINE, TRANS_7E1),
            notes = "ATF temperature PIDs (e.g. 2182 on 7E1) use different formulas by model year. Add yours under PIDs, Custom."
        ),
        ManufacturerProfile(
            id = "stellantis",
            name = "Chrysler / Dodge / Jeep / Ram",
            wmiPrefixes = listOf("1C", "2C", "3C", "1J", "3D", "ZAC", "ZFA"),
            ecus = listOf(ENGINE, TRANS_7E1)
        ),
        ManufacturerProfile(
            id = "honda",
            name = "Honda / Acura",
            wmiPrefixes = listOf("JH", "1H", "2H", "5J", "19X", "5FN", "7FA", "SHH"),
            ecus = listOf(ENGINE, TRANS_7E1)
        ),
        ManufacturerProfile(
            id = "nissan",
            name = "Nissan / Infiniti",
            wmiPrefixes = listOf("JN", "1N", "3N", "5N1", "5N3", "SJN"),
            ecus = listOf(ENGINE, TRANS_7E1)
        ),
        ManufacturerProfile(
            id = "hyundai",
            name = "Hyundai / Kia / Genesis",
            wmiPrefixes = listOf("KM", "5NP", "KN", "5XY", "5XX", "3KP", "U5Y"),
            ecus = listOf(ENGINE, TRANS_7E1)
        ),
        ManufacturerProfile(
            id = "vag",
            name = "Volkswagen / Audi / Škoda / SEAT",
            wmiPrefixes = listOf("WVW", "WV1", "WV2", "WAU", "WA1", "3VW", "1VW", "TMB", "VSS", "WUA"),
            ecus = listOf(ENGINE, TRANS_7E1)
        ),
        ManufacturerProfile(
            id = "bmw",
            name = "BMW / MINI",
            wmiPrefixes = listOf("WBA", "WBS", "WBY", "5UX", "5YM", "WMW", "4US"),
            ecus = listOf(ENGINE, TRANS_7E1),
            notes = "BMW exposes most data through proprietary diagnostics; generic PIDs are limited."
        ),
        ManufacturerProfile(
            id = "mercedes",
            name = "Mercedes-Benz",
            wmiPrefixes = listOf("WDD", "WDB", "WDC", "WDF", "W1K", "W1N", "W1V", "4JG", "55S"),
            ecus = listOf(ENGINE, TRANS_7E1)
        ),
        ManufacturerProfile(
            id = "subaru",
            name = "Subaru",
            wmiPrefixes = listOf("JF", "4S3", "4S4", "4S6"),
            ecus = listOf(ENGINE, TRANS_7E1)
        ),
        ManufacturerProfile(
            id = "mazda",
            name = "Mazda",
            wmiPrefixes = listOf("JM", "3MZ", "4F2"),
            ecus = listOf(ENGINE, TRANS_7E1)
        )
    )

    fun byId(id: String): ManufacturerProfile? = all.firstOrNull { it.id == id }

    /** Longest matching WMI prefix wins (e.g. 5NP is Hyundai, 5N1 is Nissan). */
    fun detect(vin: String?): ManufacturerProfile? {
        val code = vin?.uppercase() ?: return null
        return all.flatMap { profile -> profile.wmiPrefixes.map { it to profile } }
            .filter { (prefix, _) -> code.startsWith(prefix) }
            .maxByOrNull { (prefix, _) -> prefix.length }
            ?.second
    }
}
