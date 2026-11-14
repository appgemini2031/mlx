package com.tajasb.interaction

/**
 * Pure JVM helper for parsing MLBB skill cooldown countdown digits recognized via OCR.
 *
 * When a skill in Mobile Legends is READY/ACTIVE, no countdown digits appear on top of the button.
 * When a skill is on COOLDOWN, Mobile Legends displays the remaining seconds (e.g. "12", "5", "3.4", "0.8").
 */
object CooldownOcrParser {

    private val numberRegex = Regex("""\b(\d{1,3}(?:[.,]\d{1,2})?)\b""")

    /**
     * Extracts remaining cooldown seconds from raw OCR text, or returns null if the skill appears ready
     * (no valid cooldown countdown found).
     */
    fun extractCooldownSeconds(rawText: String?): Double? {
        if (rawText.isNullOrBlank()) return null
        val cleaned = rawText
            .replace('O', '0')
            .replace('o', '0')
            .replace('S', '5')
            .replace("s", "")
            .replace("sec", "", ignoreCase = true)
            .replace("detik", "", ignoreCase = true)
            .trim()

        val candidates = numberRegex.findAll(cleaned)
            .mapNotNull { match ->
                val rawToken = match.groupValues[1].replace(',', '.')
                val valNum = rawToken.toDoubleOrNull() ?: return@mapNotNull null
                if (valNum in 0.1..180.0) valNum else null
            }
            .toList()

        if (candidates.isEmpty()) return null
        // Usually MLBB shows single remaining seconds; prefer first plausible positive countdown
        return candidates.first()
    }
}
