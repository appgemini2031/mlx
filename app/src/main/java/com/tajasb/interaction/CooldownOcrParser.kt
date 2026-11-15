package com.tajasb.interaction

data class OcrTextCandidate(
    val text: String,
    val normalizedDistFromCenter: Double
)

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
     * Selects the most plausible cooldown number among spatially located OCR candidates.
     * Center-located countdown digits (especially decimals or realistic cooldowns <= 120s) beat
     * corner noise such as Mana cost (60/80/100) or skill level badges.
     */
    fun extractBestCooldownSeconds(
        candidates: List<OcrTextCandidate>,
        fallbackRawText: String? = null
    ): Double? {
        data class ScoredCandidate(val seconds: Double, val score: Double, val dist: Double)
        val scored = mutableListOf<ScoredCandidate>()

        for (cand in candidates) {
            val sec = extractCooldownSeconds(cand.text) ?: continue
            val dist = cand.normalizedDistFromCenter.coerceIn(0.0, 1.5)
            // Skor lebih tinggi jika dekat pusat tombol skill
            var score = 100.0 - (dist * 95.0)
            // Angka desimal (mis. 3.4 atau 0.8) hampir pasti hitung mundur cooldown MLBB
            if (cand.text.contains('.') || cand.text.contains(',')) {
                score += 18.0
            }
            // Angka di pinggiran crop dengan nilai >= 40 seringkali biaya Mana (60, 80, 100)
            if (dist > 0.48 && sec >= 40.0 && sec % 5.0 == 0.0) {
                score -= 45.0
            }
            // Cooldown wajar 0.1..99 detik diprioritaskan daripada angka > 100
            if (sec > 99.0) {
                score -= 25.0
            }
            scored += ScoredCandidate(seconds = sec, score = score, dist = dist)
        }

        if (scored.isNotEmpty()) {
            return scored.maxByOrNull { it.score }?.seconds
        }
        return extractCooldownSeconds(fallbackRawText)
    }

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

            // Skip clearly alphabetic non-digit words (>=2 letters with no digits),
            // unless the token is a distorted decimal countdown like "S.O" -> "5.0".
            val digitCount = stripped.count { it.isDigit() }
            val alphaCount = stripped.count { it.isLetter() }
            val hasDecimalPunct = '.' in stripped || ',' in stripped
            val allLookalikeLetters = stripped.all { ch ->
                !ch.isLetter() || ch in charArrayOf('O', 'o', 'D', 'Q', 'S', 'I', 'l', 'Z', 'z', 'B')
            }
            val isDistortedDecimal = hasDecimalPunct && allLookalikeLetters && stripped.length <= 6
            if (digitCount == 0 && alphaCount >= 2 && !isDistortedDecimal) {
                prevUpper = tokUpper
                continue
            }
            prevUpper = tokUpper

            val mapped = buildString(stripped.length) {
                for (ch in stripped) {
                    when (ch) {
                        'O', 'o', 'D', 'Q' -> append('0')
                        'S' -> append('5')
                        'I', 'l', '|', '!' -> append('1')
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
        return candidates.firstOrNull { it <= 99.0 } ?: candidates.first()
    }
}
