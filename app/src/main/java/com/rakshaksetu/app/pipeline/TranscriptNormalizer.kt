package com.rakshaksetu.app.pipeline

/**
 * Fixes common Vosk Hindi ASR transcription errors via fuzzy dictionary.
 * Runs between ASR output and intent/phrase matching to improve downstream accuracy.
 */
object TranscriptNormalizer {

    private val corrections = mapOf(
        // Banking terms
        "अकउंट" to "अकाउंट",
        "अकउन्ट" to "अकाउंट",
        "एकाउंट" to "अकाउंट",
        "एकाउन्ट" to "अकाउंट",
        "केवीसी" to "केवाईसी",
        "के वी सी" to "केवाईसी",
        "कवाईसी" to "केवाईसी",
        "ओतीपी" to "ओटीपी",
        "ओ टी पी" to "ओटीपी",
        "यूपीयाई" to "यूपीआई",
        "यू पी आई" to "यूपीआई",
        "यूपीय" to "यूपीआई",
        "आइ एफ एस सी" to "आईएफएससी",
        "नेफ्ट" to "एनईएफटी",
        "आर टी जी एस" to "आरटीजीएस",
        "एम पिन" to "एमपिन",
        "एम पीन" to "एमपिन",
        "क्रेडीट" to "क्रेडिट",
        "डेबीट" to "डेबिट",
        "ट्रान्सफर" to "ट्रांसफर",
        "ट्रान्सफ़र" to "ट्रांसफर",

        // Authority terms
        "सीबीयाई" to "सीबीआई",
        "सी बी आई" to "सीबीआई",
        "सीबीय" to "सीबीआई",
        "ट्राय" to "ट्राई",
        "टी आर ए आई" to "ट्राई",
        "आरबीय" to "आरबीआई",
        "आर बी आई" to "आरबीआई",
        "एनसीआरपी" to "एनसीआरपी",
        "एन सी आर पी" to "एनसीआरपी",
        "एफआईआर" to "एफआईआर",
        "एफ आई आर" to "एफआईआर",
        "इन्फोर्समेन्ट" to "इंफोर्समेंट",
        "एन्फोर्समेन्ट" to "इंफोर्समेंट",
        "नारकोटीक्स" to "नारकोटिक्स",
        "इन्स्पेक्टर" to "इंस्पेक्टर",
        "कमिश्नर" to "कमिश्नर",

        // Scam terms
        "एनीडेस्क" to "एनीडेस्क",
        "एनी डेस्क" to "एनीडेस्क",
        "टीम व्यूवर" to "टीमव्यूअर",
        "टीमव्यूवर" to "टीमव्यूअर",
        "डिजीटल" to "डिजिटल",
        "अरेस्ट" to "अरेस्ट",
        "एरेस्ट" to "अरेस्ट",
        "वारन्ट" to "वारंट",
        "वार्रण्ट" to "वारंट",
        "जुर्माना" to "जुर्माना",
        "पेनल्टी" to "पेनल्टी",
        "वेरीफिकेशन" to "वेरिफिकेशन",
        "वेरीफ़िकेशन" to "वेरिफिकेशन",
        "फ़्रीज" to "फ्रीज",
        "ब्लाक" to "ब्लॉक",
        "ब्लौक" to "ब्लॉक",

        // Common errors
        "रुपये" to "रुपये",
        "रूपये" to "रुपये",
        "लाख" to "लाख",
        "लाक" to "लाख",
        "करोड़" to "करोड़",
        "करोड" to "करोड़",
        "मोबाइल" to "मोबाइल",
        "मोबाईल" to "मोबाइल",
        "नम्बर" to "नंबर",
        "नम्बेर" to "नंबर",
        "सिम" to "सिम",
        "सीम" to "सिम",

        // English terms commonly garbled
        "accont" to "account",
        "acount" to "account",
        "verfication" to "verification",
        "varification" to "verification",
        "trasfer" to "transfer",
        "tranfer" to "transfer",
        "arresst" to "arrest",
        "warrent" to "warrant",
        "warant" to "warrant",
        "penality" to "penalty",
        "compliant" to "complaint",
        "freez" to "freeze",
        "bloack" to "block"
    )

    /**
     * Apply all known corrections to transcript text.
     * Case-insensitive matching for English terms.
     */
    fun normalize(transcript: String): String {
        var result = transcript
        for ((wrong, correct) in corrections) {
            result = result.replace(wrong, correct)
        }
        return result
    }
}
