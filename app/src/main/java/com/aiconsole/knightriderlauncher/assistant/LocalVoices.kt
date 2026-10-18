package com.aiconsole.knightriderlauncher.assistant

/** A downloadable voice for the on-device neural engine (sherpa-onnx). */
data class LocalVoice(
    val id: String,
    val label: String,
    /** Archive name in sherpa-onnx's `tts-models` GitHub release (without .tar.bz2). */
    val archive: String,
    val sizeMb: Int,
    /** Named speakers for multi-speaker voices; empty for single-voice packs. */
    val speakers: List<String> = emptyList(),
    val defaultSpeaker: Int = 0,
    /** Studio-quality but heavier: slower to start a sentence on modest head units. */
    val heavy: Boolean = false
) {
    val url: String get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archive.tar.bz2"
}

/**
 * The on-device voices KITT can download: free, open neural voices (Piper and Kokoro, via
 * sherpa-onnx) that run entirely on the head unit, so KITT sounds natural with no API key and
 * no internet once a voice is downloaded. Plain Kotlin (unit-tested).
 */
object LocalVoices {

    val ALL: List<LocalVoice> = listOf(
        LocalVoice("ryan", "Ryan · US male, clear (recommended)", "vits-piper-en_US-ryan-medium-int8", 20),
        LocalVoice("joe", "Joe · US male, relaxed", "vits-piper-en_US-joe-medium-int8", 20),
        LocalVoice("norman", "Norman · US male, mature", "vits-piper-en_US-norman-medium-int8", 20),
        LocalVoice("bryce", "Bryce · US male, young", "vits-piper-en_US-bryce-medium-int8", 19),
        LocalVoice("john", "John · US male", "vits-piper-en_US-john-medium-int8", 19),
        LocalVoice("lessac", "Lessac · US female", "vits-piper-en_US-lessac-medium-int8", 19),
        LocalVoice("amy", "Amy · US female", "vits-piper-en_US-amy-medium-int8", 20),
        LocalVoice("kristin", "Kristin · US female", "vits-piper-en_US-kristin-medium-int8", 19),
        LocalVoice("alan", "Alan · British male", "vits-piper-en_GB-alan-medium-int8", 20),
        LocalVoice("northern", "Northern English male", "vits-piper-en_GB-northern_english_male-medium-int8", 20),
        LocalVoice("cori", "Cori · British female", "vits-piper-en_GB-cori-medium-int8", 19),
        LocalVoice(
            "kokoro", "Kokoro · 11 studio voices (best quality, slower)", "kokoro-int8-en-v0_19", 98,
            speakers = listOf(
                "Adult female (US)", "Bella (US)", "Nicole (US)", "Sarah (US)", "Sky (US)", "Adam (US male)",
                "Michael (US male)", "Emma (UK)", "Isabella (UK)", "George (UK male)", "Lewis (UK male)"
            ),
            defaultSpeaker = 6,
            heavy = true
        )
    )

    val DEFAULT: LocalVoice = ALL.first()

    fun byId(id: String?): LocalVoice = ALL.firstOrNull { it.id == id } ?: DEFAULT

    /** The speaker to use: the driver's pick if the voice has that many speakers, else its default. */
    fun speaker(voice: LocalVoice, picked: Int): Int =
        if (voice.speakers.isNotEmpty() && picked in voice.speakers.indices) picked else voice.defaultSpeaker

    /**
     * A safe relative path inside an archive: "a/b.onnx" stays, anything absolute or climbing
     * out ("../x", "/etc/x") is refused, so a bad archive can't write outside the voice folder.
     */
    fun safeEntry(name: String): String? {
        val clean = name.replace('\\', '/').removePrefix("./")
        if (clean.isEmpty() || clean.startsWith("/") || clean.split('/').any { it == ".." }) return null
        return clean
    }

    /** "vits-piper-…/en_US-ryan-medium.onnx" → "en_US-ryan-medium.onnx": drops the archive's top folder. */
    fun stripTop(path: String): String? = path.substringAfter('/', "").takeIf { it.isNotEmpty() }
}
