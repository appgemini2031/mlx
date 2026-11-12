package com.tajasb.livecomment

object LiveCommentParser {

    private val SYSTEM_JOIN_PATTERNS = listOf(
        "sudah bergabung", "telah bergabung", "bergabung", "joined the live", "joined"
    )

    private val SYSTEM_SHARE_PATTERNS = listOf(
        "membagikan live", "membagikan", "shared the live", "shared", "berbagi live"
    )

    private val SYSTEM_FOLLOW_PATTERNS = listOf(
        "mengikuti host", "mengikuti sang kreator", "mengikuti", "followed the host", "followed"
    )

    private val TIKTOK_ASSISTANT_PATTERNS = listOf(
        "memberi tahu orang-orang bahwa kamu sedang live",
        "memberi tahu orang-orang",
        "selagi penonton bergabung",
        "terus semangat! energimu",
        "terus semangat",
        "semua fan di fan club",
        "cintai aku unik",
        "ajak temanmu",
        "kami sedang memberi tahu"
    )

    private val PRODUCT_MENU_PATTERNS = listOf(
        "batal sematkan", "sematkan", "sembunyikan harga",
        "gmv teratribusi", "penonton saat ini", "jumlah klik pada produk",
        "flash sale", "giveaway live", "billboard", "berakhir dalam",
        "terjual", "diskon 18%", "dapatkan 18% diskon", "gratis ongkir",
        "pengembalian barang gratis", "harga normal", "beli lokal"
    )

    private val UI_NOISE_PATTERNS = listOf(
        "geser ke kiri", "geser ke kanan", "koneksi bagus", "koneksi lambat",
        "liga d", "liga c", "liga b", "liga a", "liga ",
        "tambahkan komentar", "kirim komentar", "peringkat harian", "peringkat",
        "tajasb", "cuancame", "virtual camera", "transparansi", "disematkan",
        "jangan tampilkan", "selama 10 menit", "10 menit", "undang kreator",
        "live match", "sebagai tamu", "sesi co-host", "bergabung sebagai",
        "undang", "batalkan", "konfirmasi", "kejar lead", "ikuti", "bagikan",
        "fan's choice", "galeri 0/", "galeri "
    )

    private val GIFT_KEYWORDS = listOf(
        "mengirim mawar", "mengirim gift", "mengirim", "sent a rose", "sent a gift", "sent a", "gift", "mawar"
    )

    fun isIgnoredSystemMessage(rawText: String): Boolean {
        return getIgnoredReason(rawText) != null
    }

    fun getIgnoredReason(rawText: String): String? {
        val t = rawText.trim().lowercase()
        if (t.length < 2) return "Teks terlalu pendek (< 2 karakter)"

        // Check TikTok Live Assistant bot prompts
        if (TIKTOK_ASSISTANT_PATTERNS.any { t.contains(it) }) return "Pesan asisten live TikTok"

        // Check Product / Bottom-sheet texts
        if (PRODUCT_MENU_PATTERNS.any { t.contains(it) }) return "Teks elemen menu/pin produk"

        // Check system join/share/follow
        if (SYSTEM_JOIN_PATTERNS.any { t.contains(it) }) return "Pesan sistem penonton bergabung"
        if (SYSTEM_SHARE_PATTERNS.any { t.contains(it) }) return "Pesan sistem berbagi live"
        if (SYSTEM_FOLLOW_PATTERNS.any { t.contains(it) }) return "Pesan sistem mengikuti host"
        if (UI_NOISE_PATTERNS.any { t.contains(it) }) return "Teks elemen UI aplikasi / tombol"

        // Ignore pure emoji / punctuation lines with no actual letters/words
        val hasLetterOrDigit = t.any { it.isLetterOrDigit() }
        if (!hasLetterOrDigit) return "Hanya simbol atau emotikon murni"

        return null
    }

    fun isGiftText(rawText: String): Boolean {
        val t = rawText.trim().lowercase()
        return GIFT_KEYWORDS.any { t.contains(it) }
    }

    /**
     * Cleans badge prefixes like "No. 1", "5", "Lv. 3", "Top 1", "synk", "[△ 11]" from username.
     */
    fun cleanUsername(rawName: String): String {
        var clean = rawName.trim()
        clean = clean.replace("(?i)No\\.\\s*\\d+".toRegex(), "")
        clean = clean.replace("(?i)Top\\s*\\d+".toRegex(), "")
        clean = clean.replace("(?i)Lv\\.\\s*\\d+".toRegex(), "")
        clean = clean.replace("^[△▲▽▼♡♥★☆]+\\s*\\d*\\s*".toRegex(), "")
        clean = clean.replace("(?i)\\b(synk|team|fan)\\b".toRegex(), "")
        clean = clean.replace("^\\d+\\s+".toRegex(), "")
        clean = clean.trim()
        return if (clean.isBlank()) "Penonton" else clean
    }

    /**
     * Parses a list of strings extracted from a single comment row container.
     */
    fun parseFromContainerTexts(texts: List<String>): LiveCommentItem? {
        if (texts.isEmpty()) return null

        val trimmed = texts.map { it.trim() }.filter { it.isNotBlank() }
        if (trimmed.isEmpty()) return null

        if (trimmed.size == 1) {
            return parseSingleString(trimmed[0])
        }

        // If the first element is a badge like "Lv. 5", "Top 1", "synk", remove it from head
        val contentList = trimmed.toMutableList()
        while (contentList.size > 2 && (
            contentList[0].matches("(?i)^(no\\.\\s*\\d+|top\\s*\\d+|lv\\.\\s*\\d+|synk|team|fan)$".toRegex()) ||
            contentList[0].matches("^[△▲▽▼♡♥★☆]+\\s*\\d*$".toRegex()) ||
            contentList[0].matches("^\\d{1,2}$".toRegex())
        )) {
            contentList.removeAt(0)
        }

        val rawName = contentList[0]
        val commentBody = contentList.subList(1, contentList.size).joinToString(" ").trim()

        if (isIgnoredSystemMessage(rawName) || isIgnoredSystemMessage(commentBody)) {
            return null
        }

        val isGift = isGiftText(commentBody)
        val cleanName = cleanUsername(rawName)

        return LiveCommentItem(
            senderName = cleanName,
            content = commentBody,
            isGift = isGift
        )
    }

    /**
     * Parses a single combined string, such as:
     * "bundasuana: ya Allah aku mohon padamu bukaa..."
     * or "Dino 10: inchallah 🙏"
     */
    fun parseSingleString(raw: String): LiveCommentItem? {
        val text = raw.trim()
        if (isIgnoredSystemMessage(text)) return null

        val isGift = isGiftText(text)

        // Try colon separator "Username: Comment"
        if (text.contains(":")) {
            val parts = text.split(":", limit = 2)
            val name = cleanUsername(parts[0])
            val body = parts[1].trim()
            if (body.isNotBlank() && !isIgnoredSystemMessage(body)) {
                return LiveCommentItem(
                    senderName = name,
                    content = body,
                    isGift = isGift
                )
            }
        }

        // Try newline separator "Username\nComment"
        if (text.contains("\n")) {
            val parts = text.split("\n", limit = 2)
            val name = cleanUsername(parts[0])
            val body = parts[1].trim()
            if (body.isNotBlank() && !isIgnoredSystemMessage(body)) {
                return LiveCommentItem(
                    senderName = name,
                    content = body,
                    isGift = isGift
                )
            }
        }

        // Try first word as username if text has at least 3 words
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size >= 3) {
            val name = cleanUsername(words[0])
            val body = words.subList(1, words.size).joinToString(" ")
            if (!isIgnoredSystemMessage(body)) {
                return LiveCommentItem(
                    senderName = name,
                    content = body,
                    isGift = isGift
                )
            }
        }

        // Fallback for standalone comment (termasuk "1", "2", "spill", dll)
        if (text.isNotEmpty() && !isIgnoredSystemMessage(text)) {
            return LiveCommentItem(
                senderName = "Penonton",
                content = text,
                isGift = isGift
            )
        }

        return null
    }

    val KNOWN_COMMENT_ID_PATTERNS = listOf(
        "comment", "chat_msg", "message_content", "live_comment", "content_text", "comment_text", "tv_comment"
    )

    fun isLikelyCommentViewId(viewId: String?): Boolean {
        if (viewId.isNullOrBlank()) return false
        val lower = viewId.lowercase()
        return KNOWN_COMMENT_ID_PATTERNS.any { lower.contains(it) }
    }

    /**
     * Parsing langsung untuk pasangan 2 node anak (Node Nama & Node Pesan).
     * Mencegah kegagalan parsing bila TikTok merombak format delimiter teks string tunggal.
     */
    fun parseFromNodePair(rawName: String, rawContent: String): LiveCommentItem? {
        val trimmedName = rawName.trim()
        val trimmedContent = rawContent.trim()
        if (trimmedContent.isBlank()) return null
        if (isIgnoredSystemMessage(trimmedName) || isIgnoredSystemMessage(trimmedContent)) return null
        val cleanName = cleanUsername(trimmedName)
        val isGift = isGiftText(trimmedContent)
        return LiveCommentItem(
            senderName = cleanName,
            content = trimmedContent,
            isGift = isGift
        )
    }
}
