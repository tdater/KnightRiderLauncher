package com.aiconsole.knightriderlauncher.data

/**
 * One feature release's highlights, shown once after an update. [level] counts feature
 * releases across both editions (their version numbers differ), so the notes are shared.
 */
data class ReleaseNote(val level: Int, val items: List<String>)

/** Pure "what's new" bookkeeping (unit-tested). Newest release first. */
object ReleaseNotes {

    val ALL = listOf(
        ReleaseNote(
            3,
            listOf(
                "System check in KITT Config: every permission and setting KITT needs, with a fix button for each.",
                "Send a problem report to your phone with a QR code, or share it.",
                "KITT warns when it's no longer the home screen, and finds you the nearest gas station when fuel runs low.",
                "Parking spot saved when you switch off; scan it to your phone from the Trips screen.",
                "Speed limit on the map, with an optional warning when you're over it.",
                "GM tire pressures (community-sourced; not every GM vehicle answers).",
                "KITT comments on hard stops, cold revs and long idling, and grades every drive.",
                "KARR, the evil-twin personality. Optional KITT sound effects.",
                "A night theme that switches at sunset, the full-screen Pursuit dashboard, and Maps with KITT's side panel.",
                "Big-text reading for AI answers, a mini player for the stream window, and per-site data saver.",
                "Automatic weekly backups that restore into either edition.",
            )
        ),
        ReleaseNote(2, listOf("\"Hey KITT\" no longer listens over your music.")),
        ReleaseNote(
            1,
            listOf(
                "A stuck web page is shut down with a Reload button instead of freezing the launcher.",
                "Updates install properly, with progress and clear messages.",
                "Lite mode, Screen size, and Savage KITT.",
            )
        ),
    )

    val latest: Int get() = ALL.maxOf { it.level }

    /**
     * Notes newer than [lastSeen]. Nothing on a fresh install ([updated] false): the driver
     * hasn't missed anything.
     */
    fun since(lastSeen: Int, updated: Boolean): List<ReleaseNote> = when {
        !updated -> emptyList()
        // Updated from a build older than this feature: just the latest release.
        lastSeen <= 0 -> ALL.filter { it.level == latest }
        else -> ALL.filter { it.level > lastSeen }
    }
}

/** Pure weekly-backup schedule (unit-tested). */
object AutoBackupRules {
    const val EVERY_MS = 7 * 86_400_000L
    const val KEEP = 4

    fun due(now: Long, lastBackup: Long): Boolean = lastBackup <= 0 || now - lastBackup >= EVERY_MS

    /** Backup file names (sortable by date) to delete, keeping the newest [KEEP]. */
    fun toPrune(names: List<String>): List<String> = names.sortedDescending().drop(KEEP)

    fun fileName(edition: String, date: String): String = "kitt-backup-$edition-$date.json"
}
