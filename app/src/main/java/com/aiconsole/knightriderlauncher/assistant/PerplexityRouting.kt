package com.aiconsole.knightriderlauncher.assistant

/**
 * Decides whether a Perplexity request needs web grounding. Every Agent API preset carries
 * web_search and its instructions favour searching before answering, so each question would pay
 * for at least one search. Questions KITT can answer from the vehicle data it already sends (live
 * readings, codes, trips) or plain conversation go to a model-only call instead and cost tokens
 * only. When in doubt it searches: accuracy beats saving a fraction of a cent.
 */
object PerplexityRouting {

    /** Anything time-sensitive, local, commercial or factual about the outside world. */
    private val NEEDS_WEB = Regex(
        "\\b(news|weather|forecast|rain|snow|storm|traffic|price|prices|cost|costs|cheap|cheapest|buy|sale|deal|near me|nearby|closest|" +
            "open now|hours|today|tonight|tomorrow|this week|latest|current|recent|update|released?|recall|recalls|tsb|bulletin|" +
            "score|scores|game|stock|who won|search|look up|lookup|google|where is|directions|restaurant|gas station|hotel|event|" +
            "law|legal|regulation|20[0-9]{2})\\b",
        RegexOption.IGNORE_CASE
    )

    /** Questions about the vehicle's own live data, which arrives in the vehicle context. */
    private val VEHICLE_DATA = Regex(
        "\\b(my|the|current)\\s+(speed|rpm|revs|coolant|temp|temperature|battery|voltage|fuel( level)?|boost|load|throttle|" +
            "trans(mission)? temp|oil|codes?|trip|drive|mileage|mpg|economy|range|odometer|maintenance|tires?)\\b|" +
            "\\bhow (am i|was (that|my) drive)\\b|\\b(any|read my|do i have) (codes|warnings)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Greetings, thanks and banter. */
    private val CHAT = Regex(
        "^\\s*(hi|hey|hello|yo|thanks|thank you|good (morning|afternoon|evening|night)|how are you|what'?s up|tell me a joke|joke|" +
            "who are you|what are you|you there)\\b",
        RegexOption.IGNORE_CASE
    )

    fun needsWeb(prompt: String, diagnostic: Boolean, webSearchEnabled: Boolean): Boolean = when {
        !webSearchEnabled -> false
        NEEDS_WEB.containsMatchIn(prompt) -> true
        diagnostic -> true // Fault diagnosis benefits from TSBs, recalls and known fixes.
        VEHICLE_DATA.containsMatchIn(prompt) || CHAT.containsMatchIn(prompt) -> false
        else -> true
    }
}
