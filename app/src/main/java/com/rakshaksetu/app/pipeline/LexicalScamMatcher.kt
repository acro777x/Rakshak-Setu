package com.rakshaksetu.app.pipeline

/**
 * Precision-calibrated lexical similarity between a transcript and a scam phrase,
 * used as the semantic-matching fallback while the ONNX MiniLM encoder has not
 * been downloaded yet.
 *
 * Score composition:
 *   containment = |tokens(transcript) ∩ tokens(phrase)| / |tokens(phrase)|
 *   coverage    = |∩| / |tokens(transcript)|
 *   raw         = 0.75*containment + 0.25*coverage   (rewards phrase recall, resists
 *                                                   long-transcript dilution)
 *   final       = raw^(1/1.6)                        (calibration spread: 0.65→0.77,
 *                                                   0.72→0.82 so near-complete script
 *                                                   matches cross the 0.80 vote gate)
 */
object LexicalScamMatcher {

    private val SPLIT_REGEX = Regex("[^\\p{L}\\p{N}]+")

    fun tokenize(text: String): List<String> =
        text.lowercase().split(SPLIT_REGEX).filter { it.length >= 2 }

    fun score(transcript: String, phrase: String): Float {
        val tTokens = tokenize(transcript).toSet()
        val pTokens = tokenize(phrase).toSet()
        if (tTokens.isEmpty() || pTokens.isEmpty()) return 0f

        val overlap = tTokens.intersect(pTokens).size.toFloat()
        if (overlap == 0f) return 0f

        val containment = overlap / pTokens.size
        val coverage = overlap / tTokens.size
        val raw = 0.75f * containment + 0.25f * coverage
        return Math.pow(raw.toDouble(), 1.0 / 1.6).toFloat().coerceIn(0f, 1f)
    }
}
