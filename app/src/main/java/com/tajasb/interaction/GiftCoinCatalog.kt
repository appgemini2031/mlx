package com.tajasb.interaction

data class GiftCatalogEntry(
    val primaryName: String,
    val coinCost: Int,
    val platform: String,
    val emoji: String,
    val aliases: List<String> = emptyList()
) {
    val allNormalizedNames: Set<String> by lazy {
        buildSet {
            add(GiftCoinCatalog.normalizeName(primaryName))
            aliases.forEach { add(GiftCoinCatalog.normalizeName(it)) }
        }.filter { it.isNotEmpty() }.toSet()
    }
}

/**
 * Database terpusat nama Gift & nilai koin/beans (TikTok Live & Bigo Live).
 * Mendukung pencocokan tepat antar-bahasa (mis. "Mawar" <-> "Rose") sekaligus membedakan
 * berbagai gift yang memiliki nilai koin sama (mis. 1 koin: Mawar, Kopi, Es Krim, Bebek, Heart Me, dll.).
 */
object GiftCoinCatalog {

    val entries: List<GiftCatalogEntry> = listOf(
        // === TIKTOK 1 KOIN ===
        GiftCatalogEntry("Mawar", 1, "TIKTOK", "🌹", listOf("Rose")),
        GiftCatalogEntry("TikTok", 1, "TIKTOK", "🎵", listOf("TikTok Logo")),
        GiftCatalogEntry("Heart Me", 1, "TIKTOK", "💖", listOf("HeartMe", "Heart Me Gift")),
        GiftCatalogEntry("Kopi", 1, "TIKTOK", "☕", listOf("Coffee")),
        GiftCatalogEntry("Es Krim", 1, "TIKTOK", "🍦", listOf("Ice Cream", "Es Krim Kerucut", "Ice Cream Cone")),
        GiftCatalogEntry("Bebek", 1, "TIKTOK", "🦆", listOf("Duck")),
        GiftCatalogEntry("Hati", 1, "TIKTOK", "❤️", listOf("Heart", "Love", "Cinta")),
        GiftCatalogEntry("GG", 1, "TIKTOK", "🎮", listOf("Good Game")),
        GiftCatalogEntry("Stik Cahaya", 1, "TIKTOK", "✨", listOf("Glow Stick")),
        GiftCatalogEntry("Cium", 1, "TIKTOK", "💋", listOf("Kiss", "Ciuman")),
        GiftCatalogEntry("Sepak Bola", 1, "TIKTOK", "⚽", listOf("Football", "Soccer")),
        GiftCatalogEntry("Bola Tenis", 1, "TIKTOK", "🎾", listOf("Tennis")),

        // === TIKTOK 5 KOIN ===
        GiftCatalogEntry("Jari Hati", 5, "TIKTOK", "🫰", listOf("Finger Heart")),
        GiftCatalogEntry("Panda", 5, "TIKTOK", "🐼"),
        GiftCatalogEntry("Tepuk Tangan", 5, "TIKTOK", "👏", listOf("Applause")),
        GiftCatalogEntry("Cabai", 5, "TIKTOK", "🌶️", listOf("Chili")),

        // === TIKTOK 10 KOIN ===
        GiftCatalogEntry("Permen", 10, "TIKTOK", "🍭", listOf("Lollipop", "Candy")),
        GiftCatalogEntry("Bunga Kering", 10, "TIKTOK", "💐", listOf("Dried Flower")),
        GiftCatalogEntry("Origami", 10, "TIKTOK", "🦢"),
        GiftCatalogEntry("Rosa", 10, "TIKTOK", "🌺"),
        GiftCatalogEntry("Kalung Sahabat", 10, "TIKTOK", "📿", listOf("Friendship Necklace")),
        GiftCatalogEntry("Labu", 10, "TIKTOK", "🎃", listOf("Pumpkin")),

        // === TIKTOK 20 - 99 KOIN ===
        GiftCatalogEntry("Parfum", 20, "TIKTOK", "🧴", listOf("Perfume")),
        GiftCatalogEntry("Tiny Diny", 20, "TIKTOK", "🦖"),
        GiftCatalogEntry("Donat", 30, "TIKTOK", "🍩", listOf("Doughnut", "Donut")),
        GiftCatalogEntry("Topi dan Kumis", 99, "TIKTOK", "🎩", listOf("Cap and Mustache", "Topi", "Cap")),
        GiftCatalogEntry("Mahkota", 99, "TIKTOK", "👑", listOf("Crown", "Little Crown")),
        GiftCatalogEntry("Bintang", 99, "TIKTOK", "⭐", listOf("Star")),
        GiftCatalogEntry("Bangau Kertas", 99, "TIKTOK", "🕊️", listOf("Paper Crane")),

        // === TIKTOK 100 - 999 KOIN ===
        GiftCatalogEntry("Hand Heart", 100, "TIKTOK", "🫶", listOf("Hati Tangan")),
        GiftCatalogEntry("Konfeti", 100, "TIKTOK", "🎊", listOf("Confetti")),
        GiftCatalogEntry("Mishka Bear", 100, "TIKTOK", "🧸", listOf("Beruang Mishka")),
        GiftCatalogEntry("Kacamata", 199, "TIKTOK", "🕶️", listOf("Sunglasses")),
        GiftCatalogEntry("Corgi", 299, "TIKTOK", "🐶"),
        GiftCatalogEntry("Sarung Tinju", 299, "TIKTOK", "🥊", listOf("Boxing Gloves")),
        GiftCatalogEntry("Nasi Tumpeng", 300, "TIKTOK", "🍚", listOf("Tumpeng")),
        GiftCatalogEntry("Mic", 399, "TIKTOK", "🎤", listOf("Microphone")),
        GiftCatalogEntry("Gitar", 500, "TIKTOK", "🎸", listOf("Guitar")),
        GiftCatalogEntry("Angsa", 699, "TIKTOK", "🦢", listOf("Swan")),
        GiftCatalogEntry("Kereta Api", 899, "TIKTOK", "🚂", listOf("Kereta", "Train")),

        // === TIKTOK 1000+ KOIN ===
        GiftCatalogEntry("Galaxy", 1000, "TIKTOK", "🌌"),
        GiftCatalogEntry("Bintang Jatuh", 1000, "TIKTOK", "🌠", listOf("Shooting Star")),
        GiftCatalogEntry("Komet", 1000, "TIKTOK", "☄️", listOf("Comet")),
        GiftCatalogEntry("Motor Balap", 1000, "TIKTOK", "🏍️", listOf("Motorcycle")),
        GiftCatalogEntry("Kembang Api", 1088, "TIKTOK", "🎆", listOf("Fireworks")),
        GiftCatalogEntry(
            "Paus Menyelam",
            2150,
            "TIKTOK",
            "🐋",
            listOf("Paus", "Diving Whale", "Whale", "Paus Terbang", "Flying Whale", "Paus Samudra", "Ocean Whale")
        ),
        GiftCatalogEntry("Pesawat Jet", 4888, "TIKTOK", "🛩️", listOf("Jet", "Private Jet")),
        GiftCatalogEntry("Helikopter", 4999, "TIKTOK", "🚁", listOf("Helicopter")),
        GiftCatalogEntry("Moon", 5000, "TIKTOK", "🌕", listOf("Bulan")),
        GiftCatalogEntry("Mobil Balap", 7000, "TIKTOK", "🏎️", listOf("Sports Car")),
        GiftCatalogEntry("Yacht", 9888, "TIKTOK", "🛥️", listOf("Kapal Pesiar")),
        GiftCatalogEntry("Garuda", 9999, "TIKTOK", "🦅"),
        GiftCatalogEntry("Matahari", 10000, "TIKTOK", "☀️", listOf("Sun", "Interstellar")),
        GiftCatalogEntry("Falcon", 10999, "TIKTOK", "🦅", listOf("Elang")),
        GiftCatalogEntry("Planet", 15000, "TIKTOK", "🪐"),
        GiftCatalogEntry("Kastil Fantasi", 20000, "TIKTOK", "🏰", listOf("Fantasy Castle", "Castle")),
        GiftCatalogEntry("Pesawat", 20000, "TIKTOK", "✈️", listOf("Airplane")),
        GiftCatalogEntry("Singa", 29999, "TIKTOK", "🦁", listOf("Lion", "Supercar")),
        GiftCatalogEntry("TikTok Universe", 34999, "TIKTOK", "🚀", listOf("Universe")),

        // === BIGO LIVE ===
        GiftCatalogEntry("Bigo Rose", 1, "BIGO", "🌹", listOf("Rose Bigo")),
        GiftCatalogEntry("Bigo Kiss", 1, "BIGO", "💋", listOf("Kiss Bigo")),
        GiftCatalogEntry("Bigo Coffee", 5, "BIGO", "☕", listOf("Coffee Bigo")),
        GiftCatalogEntry("Lucky Star", 10, "BIGO", "🌟", listOf("Bintang Bigo")),
        GiftCatalogEntry("Bell", 10, "BIGO", "🔔", listOf("Lonceng")),
        GiftCatalogEntry("Diamond Ring", 100, "BIGO", "💍", listOf("Cincin", "Ring")),
        GiftCatalogEntry("Love Boat", 500, "BIGO", "🚤", listOf("Perahu Cinta")),
        GiftCatalogEntry("Supercar Bigo", 3000, "BIGO", "🏎️", listOf("Super Car")),
        GiftCatalogEntry("Rocket", 5000, "BIGO", "🚀", listOf("Roket")),
        GiftCatalogEntry("Dragon", 9999, "BIGO", "🐉", listOf("Naga")),
        GiftCatalogEntry("Pegasus", 20000, "BIGO", "🦄", listOf("Kuda Terbang"))
    )

    val coinTierBuckets: List<String> = listOf(
        "Semua",
        "1 💎",
        "5-10 💎",
        "20-99 💎",
        "100-999 💎",
        "1000+ 💎",
        "BIGO"
    )

    fun normalizeName(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder(raw.length)
        var lastWasSpace = true
        for (ch in raw.lowercase()) {
            when {
                ch.isLetterOrDigit() -> {
                    sb.append(ch)
                    lastWasSpace = false
                }
                ch.isWhitespace() || ch == '_' || ch == '-' -> {
                    if (!lastWasSpace) {
                        sb.append(' ')
                        lastWasSpace = true
                    }
                }
            }
        }
        return sb.toString().trim()
    }

    fun findEntriesForName(name: String, platform: String? = null): List<GiftCatalogEntry> {
        val norm = normalizeName(name)
        if (norm.isEmpty()) return emptyList()
        return entries.filter { entry ->
            val platOk = platform == null || entry.platform.equals(platform, ignoreCase = true)
            platOk && norm in entry.allNormalizedNames
        }
    }

    fun findEntry(name: String, platform: String? = null): GiftCatalogEntry? {
        return findEntriesForName(name, platform).firstOrNull()
            ?: findEntriesForName(name, null).firstOrNull()
    }

    fun resolveCoinCost(giftName: String, platform: String = "TIKTOK"): Int? {
        val firstToken = giftName.split(',', '|')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return null
        findEntriesForName(firstToken, platform).firstOrNull()?.let { return it.coinCost }
        findEntriesForName(firstToken, null).firstOrNull()?.let { return it.coinCost }
        val clean = normalizeName(firstToken)
        if (clean.isEmpty()) return null
        return entries
            .filter { platform.isBlank() || it.platform.equals(platform, ignoreCase = true) }
            .sortedByDescending { it.primaryName.length }
            .firstOrNull { entry ->
                entry.allNormalizedNames.any { alias -> alias.length >= 4 && clean.contains(alias) }
            }
            ?.coinCost
    }

    /**
     * Mengecek apakah [incomingGiftName] cocok dengan [configuredTarget] (bisa dipisah koma/pipe).
     * - Jika [configuredTarget] kosong -> cocok semua gift (wildcard).
     * - Jika token ada di katalog -> cocokkan HANYA terhadap nama utama & sinonim resmi entri tersebut
     *   (sehingga "Heart" tidak salah memicu "Heart Me" atau "Hand Heart", dan "Mawar" cocok dengan "Rose"
     *   tapi tidak cocok dengan "Kopi" walau sama-sama 1 koin).
     * - Jika token kustom tidak ada di katalog -> fallback pencocokan teks aman (exact atau contains >= 3 char).
     */
    fun matchesGiftName(configuredTarget: String, incomingGiftName: String): Boolean {
        val targetClean = configuredTarget.trim()
        if (targetClean.isEmpty()) return true
        val incomingNorm = normalizeName(incomingGiftName)
        if (incomingNorm.isEmpty()) return false

        val tokens = targetClean.split(',', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true

        for (token in tokens) {
            val tokenNorm = normalizeName(token)
            if (tokenNorm.isEmpty()) continue
            if (incomingNorm == tokenNorm) return true

            val matchedEntries = findEntriesForName(token, null)
            if (matchedEntries.isNotEmpty()) {
                val allowedNames = matchedEntries.flatMap { it.allNormalizedNames }.toSet()
                if (incomingNorm in allowedNames) return true
            } else {
                // Gift kustom baru dari TikTok/Bigo yang belum masuk katalog
                if (tokenNorm.length >= 3 && incomingNorm.contains(tokenNorm)) return true
            }
        }
        return false
    }

    fun firstGiftNameForTest(configuredTarget: String): String {
        val first = configuredTarget.split(',', '|')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
        return first ?: "Mawar"
    }

    fun filterEntries(
        query: String = "",
        tierBucket: String = "Semua"
    ): List<GiftCatalogEntry> {
        val qNorm = normalizeName(query)
        return entries.filter { entry ->
            val tierOk = when (tierBucket) {
                "1 💎" -> entry.platform == "TIKTOK" && entry.coinCost == 1
                "5-10 💎" -> entry.platform == "TIKTOK" && entry.coinCost in 5..10
                "20-99 💎" -> entry.platform == "TIKTOK" && entry.coinCost in 20..99
                "100-999 💎" -> entry.platform == "TIKTOK" && entry.coinCost in 100..999
                "1000+ 💎" -> entry.platform == "TIKTOK" && entry.coinCost >= 1000
                "BIGO" -> entry.platform == "BIGO"
                else -> true
            }
            if (!tierOk) return@filter false
            if (qNorm.isEmpty()) return@filter true
            entry.allNormalizedNames.any { it.contains(qNorm) } ||
                entry.coinCost.toString().contains(qNorm)
        }
    }

    data class GiftUsageConflict(
        val conflictingRuleId: String,
        val conflictingRuleName: String,
        val conflictingGiftName: String
    )

    private fun isGiftSource(source: UniversalTriggerSource): Boolean =
        source == UniversalTriggerSource.GIFT || source == UniversalTriggerSource.GIFT_QUANTITY

    fun giftsOverlap(firstTarget: String, secondTarget: String): Boolean {
        val t1 = firstTarget.split(',', '|').map { it.trim() }.filter { it.isNotEmpty() }
        val t2 = secondTarget.split(',', '|').map { it.trim() }.filter { it.isNotEmpty() }
        if (t1.isEmpty() || t2.isEmpty()) return false
        for (a in t1) {
            for (b in t2) {
                if (matchesGiftName(a, b) || matchesGiftName(b, a)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Mencari konsep/rule lain yang sudah memakai gift yang sama (termasuk alias bahasa).
     */
    fun findConflictingRules(
        currentRuleId: String?,
        currentSource: UniversalTriggerSource,
        targetGiftName: String,
        allRules: List<UniversalTriggerRule>
    ): List<GiftUsageConflict> {
        if (!isGiftSource(currentSource) || targetGiftName.isBlank()) return emptyList()
        return allRules.mapNotNull { other ->
            if (other.id == currentRuleId) return@mapNotNull null
            if (!isGiftSource(other.triggerSource)) return@mapNotNull null
            if (other.targetGiftName.isBlank()) return@mapNotNull null
            if (giftsOverlap(targetGiftName, other.targetGiftName)) {
                GiftUsageConflict(
                    conflictingRuleId = other.id,
                    conflictingRuleName = other.triggerName.ifBlank { other.triggerSource.displayName },
                    conflictingGiftName = other.targetGiftName
                )
            } else {
                null
            }
        }
    }

    fun findUsedByRuleNames(
        giftPrimaryName: String,
        currentRuleId: String?,
        allRules: List<UniversalTriggerRule>
    ): List<String> {
        if (giftPrimaryName.isBlank()) return emptyList()
        return allRules.mapNotNull { other ->
            if (other.id == currentRuleId) return@mapNotNull null
            if (!isGiftSource(other.triggerSource) || other.targetGiftName.isBlank()) return@mapNotNull null
            if (giftsOverlap(giftPrimaryName, other.targetGiftName)) {
                other.triggerName.ifBlank { other.triggerSource.displayName }
            } else {
                null
            }
        }
    }
}
