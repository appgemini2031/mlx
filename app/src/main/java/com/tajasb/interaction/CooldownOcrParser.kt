package com.tajasb.interaction

/**
 * Pure JVM helper for parsing MLBB skill cooldown countdown digits recognized via OCR.
 *
 * When a skill in Mobile Legends is READY/ACTIVE, no countdown digits appear on top of the button.
 * When a skill is on COOLDOWN, Mobile Legends displays the remaining seconds (e.g. "12", "5", "3.4", "0.8").
 */
object CooldownOcrParser {

    private val numberRegex = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)""")
    private val whitespaceRegex = Regex("""\s+""")

    /**
     * Extracts remaining cooldown seconds from raw OCR text, or returns null if the skill appears ready
     * (no valid cooldown countdown found).
     */
    fun extractCooldownSeconds(rawText: String?): Double? {
        if (rawText.isNullOrBlank()) return null
        val trimmed = rawText.trim()
        if (trimmed.equals("READY", ignoreCase = true) ||
            trimmed.equals("SIAP", ignoreCase = true) ||
            trimmed.equals("ACTIVE", ignoreCase = true)
        ) {
            return null
        }

        // Normalize token-by-token so UI words aren't mangled into fake numbers
        val tokens = trimmed
            .replace("detik", " ", ignoreCase = true)
            .replace("sec", " ", ignoreCase = true)
            .split(whitespaceRegex)
            .filter { it.isNotBlank() }

        val candidates = mutableListOf<Double>()
        var prevUpper = ""
        for (rawTok in tokens) {
            val tok = rawTok.trim().trim(':', ';', '(', ')', '[', ']')
            if (tok.isEmpty()) continue
            val tokUpper = tok.uppercase()
            if (prevUpper == "LV" || prevUpper == "LVL" || prevUpper == "LEVEL") {
                prevUpper = tokUpper
                continue
            }
            val stripped = tok.removeSuffix("s").removeSuffix("S")
                .removeSuffix("d").removeSuffix("D")
            if (stripped.isEmpty()) {
                // Single "S" or "s" from OCR misread of 5
                if (tok.equals("S", ignoreCase = false)) {
                    candidates += 5.0
                }
                prevUpper = tokUpper
                continue
            }

            // Skip clearly alphabetic non-digit words (>=2 letters with no digits)
            val digitCount = stripped.count { it.isDigit() }
            val alphaCount = stripped.count { it.isLetter() }
            if (digitCount == 0 && alphaCount >= 2) {
                prevUpper = tokUpper
                continue
            }
            prevUpper = tokUpper

            val mapped = buildString(stripped.length) {
                for (ch in stripped) {
                    when (ch) {
                        'O', 'o', 'D', 'Q' -> append('0')
                        'S' -> append('5')
                        'I', 'l', '|' -> append('1')
                        'Z', 'z' -> append('2')
                        'B' -> append('8')
                        ',' -> append('.')
                        else -> append(ch)
                    }
                }
            }
            for (m in numberRegex.findAll(mapped)) {
                val valNum = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
                if (valNum in 0.1..180.0) {
                    candidates += valNum
                }
            }
        }

        if (candidates.isEmpty()) return null
        return candidates.first()
    }
}
