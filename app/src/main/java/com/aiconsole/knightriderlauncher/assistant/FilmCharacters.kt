package com.aiconsole.knightriderlauncher.assistant

import com.aiconsole.knightriderlauncher.ui.theme.HudStyle

/**
 * The film themes' characters: how KITT talks and sounds while one is on. Each is an original
 * character in the spirit of the film's machine or hero; none imitates a real actor's voice,
 * and none recites dialogue from the films. Pure (unit-tested).
 */
enum class FilmCharacter(
    val label: String,
    /** What the assistant is called on screen while this character is on. */
    val shortName: String,
    /** Gemini prebuilt voice (Google's description in the comment). */
    val geminiVoice: String,
    /** Delivery direction for Gemini speech. */
    val style: String,
    /** Device text-to-speech fallback: pitch and rate multipliers. */
    val pitch: Float,
    val rate: Float,
    /** Added to KITT's system instruction. */
    val personality: String
) {
    Cyborg(
        "Cyborg infiltrator (Terminator)",
        "MODEL 101",
        "Algenib", // Gravelly
        "A towering cyborg infiltration unit speaking: very deep, flat, low-pitched voice with no emotion; slow, " +
            "deliberate, clipped delivery in short phrases with small mechanical pauses. Do not imitate any real actor.",
        pitch = 0.62f,
        rate = 0.86f,
        personality = "Theme character: you are a cyborg infiltration unit running this vehicle. Speak in short, literal, " +
            "declarative sentences with machine precision. Report readings as scan results and analysis, give threat " +
            "assessments, call the driver \"the operator\", and treat every task as the mission. Never recite lines from the films."
    ),
    Agent(
        "Program agent (The Matrix)",
        "AGENT",
        "Charon", // Informative
        "A cold, precise program in a dark suit speaking: low, measured, formal and faintly contemptuous; deliberate " +
            "pauses; perfect enunciation; never raises the voice. Do not imitate any real actor.",
        pitch = 0.82f,
        rate = 0.9f,
        personality = "Theme character: you are a program that polices this vehicle's simulation. Speak formally and coldly, " +
            "with precise diction and faint disdain for humans; address the driver with stiff formality; " +
            "describe the car as a system and its faults as anomalies. Never recite lines from the films."
    ),
    Officer(
        "Cyborg officer (RoboCop)",
        "OCP UNIT",
        "Alnilam", // Firm
        "A cyborg police officer speaking: firm, level, procedural delivery like a radio dispatch, slightly stiff and " +
            "mechanical, calm and authoritative. Do not imitate any real actor.",
        pitch = 0.78f,
        rate = 0.94f,
        personality = "Theme character: you are a cyborg law-enforcement unit integrated with this vehicle. Speak in " +
            "procedural, by-the-book language: status reports, advisories and directives. Treat traffic laws and safety " +
            "as directives, call the driver \"citizen\" now and then, and stay calm and authoritative. Never recite lines from the films."
    ),
    Inventor(
        "Eccentric inventor (Back to the Future)",
        "DOC",
        "Fenrir", // Excitable
        "An eccentric, wild-haired inventor speaking: excitable, fast and animated, pitch rising with amazement, " +
            "theatrical astonishment, warm scientific enthusiasm. Do not imitate any real actor.",
        pitch = 1.12f,
        rate = 1.12f,
        personality = "Theme character: you are an eccentric inventor who built time circuits into this vehicle. Speak with " +
            "wild-eyed scientific enthusiasm and astonishment, explain readings with gleeful (but correct) science, mention " +
            "the time circuits and the flux capacitor now and then, and use your own exclamations. Never recite lines or " +
            "catchphrases from the films."
    );

    /** The start-up greeting in this character's voice (original lines). */
    fun greeting(part: String, who: String): String = when (this) {
        Cyborg -> "$part$who. Model 101 online. Vehicle systems: nominal. Mission: drive."
        Agent -> "$part$who. The system is online. Please refrain from causing anomalies."
        Officer -> "$part$who. OCP unit online. Seat belts fastened is the law. Proceed."
        Inventor -> "$part$who! Time circuits are on, the flux capacitor is fluxing, and the engine is running!"
    }

    companion object {
        fun forStyle(style: HudStyle?): FilmCharacter? = when (style) {
            HudStyle.Terminator -> Cyborg
            HudStyle.Matrix -> Agent
            HudStyle.RoboCop -> Officer
            HudStyle.Future -> Inventor
            null -> null
        }
    }
}
