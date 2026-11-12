package com.tajasb.data

import android.content.SharedPreferences
import com.tajasb.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * VideoPlaylistRepository:
 * Modular helper for video playlist JSON serialization/deserialization,
 * Fisher-Yates non-repeating shuffle deck, and Master/Spill keyword matching.
 */
object VideoPlaylistRepository {

    class ShuffleDeckManager {
        private val shuffleDeck = mutableListOf<Int>()
        private val randomPlayedHistory = mutableListOf<Int>()
        private val shuffleLock = Any()

        fun reset() = synchronized(shuffleLock) {
            shuffleDeck.clear()
            randomPlayedHistory.clear()
        }

        fun removeIndex(selectedIndex: Int) = synchronized(shuffleLock) {
            shuffleDeck.remove(selectedIndex)
        }

        private fun ensureDeckPopulatedLocked(candidateIndices: List<Int>, currentIdx: Int) {
            shuffleDeck.retainAll(candidateIndices.toSet())
            if (shuffleDeck.isEmpty()) {
                val remaining = candidateIndices.filter { it != currentIdx }
                val newCycle = if (remaining.isNotEmpty()) {
                    remaining.shuffled().toMutableList()
                } else {
                    candidateIndices.shuffled().toMutableList()
                }
                shuffleDeck.addAll(newCycle)
            } else if (shuffleDeck.size > 1 && shuffleDeck.first() == currentIdx) {
                val lastIdx = shuffleDeck.lastIndex
                val tmp = shuffleDeck[0]
                shuffleDeck[0] = shuffleDeck[lastIdx]
                shuffleDeck[lastIdx] = tmp
            }
        }

        fun getNextShuffledIndex(candidateIndices: List<Int>, currentIdx: Int): Int = synchronized(shuffleLock) {
            if (candidateIndices.isEmpty()) return currentIdx
            if (candidateIndices.size == 1) return candidateIndices[0]

            ensureDeckPopulatedLocked(candidateIndices, currentIdx)

            val nextIdx = shuffleDeck.removeAt(0)
            randomPlayedHistory.add(currentIdx)
            if (randomPlayedHistory.size > 50) {
                randomPlayedHistory.removeAt(0)
            }
            nextIdx
        }

        fun getPrevShuffledIndex(candidateIndices: List<Int>, currentIdx: Int): Int = synchronized(shuffleLock) {
            while (randomPlayedHistory.isNotEmpty()) {
                val last = randomPlayedHistory.removeAt(randomPlayedHistory.size - 1)
                if (last in candidateIndices && last != currentIdx) {
                    return last
                }
            }
            val lower = candidateIndices.filter { it < currentIdx }
            return if (lower.isNotEmpty()) lower.last() else candidateIndices.last()
        }

        fun peekNextShuffledIndex(candidateIndices: List<Int>, currentIdx: Int): Int = synchronized(shuffleLock) {
            if (candidateIndices.isEmpty()) return currentIdx
            if (candidateIndices.size == 1) return candidateIndices[0]
            ensureDeckPopulatedLocked(candidateIndices, currentIdx)
            return shuffleDeck.firstOrNull() ?: candidateIndices.firstOrNull { it != currentIdx } ?: currentIdx
        }

        fun remainingInCurrentCycle(candidateIndices: List<Int>): Int = synchronized(shuffleLock) {
            shuffleDeck.retainAll(candidateIndices.toSet())
            shuffleDeck.size
        }
    }

    fun loadPlaylistFromPrefs(prefs: SharedPreferences): List<VideoItem> {
        val rawJson = prefs.getString("playlist_json", null) ?: return emptyList()
        return parsePlaylistFromJson(rawJson)
    }

    fun parsePlaylistFromJson(rawJson: String): List<VideoItem> {
        if (rawJson.isBlank()) return emptyList()
        val list = mutableListOf<VideoItem>()
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VideoItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Video #${i + 1}"),
                        path = obj.optString("path", ""),
                        uri = if (obj.has("uri") && !obj.isNull("uri")) obj.getString("uri") else null,
                        durationMs = obj.optLong("durationMs", 0L),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        width = obj.optInt("width", 1280),
                        height = obj.optInt("height", 720),
                        isMuted = obj.optBoolean("isMuted", false),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                        isMaster = obj.optBoolean("isMaster", false),
                        isBridge1 = obj.optBoolean("isBridge1", false),
                        isBridge2 = obj.optBoolean("isBridge2", false),
                        spillKeyword = obj.optString("spillKeyword", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun serializePlaylistToJson(list: List<VideoItem>): String {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("path", item.path)
                put("uri", item.uri)
                put("durationMs", item.durationMs)
                put("sizeBytes", item.sizeBytes)
                put("width", item.width)
                put("height", item.height)
                put("isMuted", item.isMuted)
                put("isEnabled", item.isEnabled)
                put("addedAt", item.addedAt)
                put("isMaster", item.isMaster)
                put("isBridge1", item.isBridge1)
                put("isBridge2", item.isBridge2)
                put("spillKeyword", item.spillKeyword)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun savePlaylistToPrefs(prefs: SharedPreferences, list: List<VideoItem>) {
        try {
            prefs.edit().putString("playlist_json", serializePlaylistToJson(list)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getMasterVideoIndex(playlist: List<VideoItem>, masterVideoId: String?): Int {
        if (playlist.isEmpty()) return 0
        if (!masterVideoId.isNullOrBlank()) {
            val idx = playlist.indexOfFirst { it.id == masterVideoId }
            if (idx >= 0) return idx
        }
        val explicitMasterIdx = playlist.indexOfFirst { it.isMaster }
        if (explicitMasterIdx >= 0) return explicitMasterIdx
        return 0
    }

    fun findMatchingSpillVideo(
        playlist: List<VideoItem>,
        masterIdx: Int,
        rawText: String,
        excludedIndices: Set<Int> = emptySet()
    ): Pair<Int, VideoItem>? {
        if (playlist.isEmpty()) return null
        val lower = rawText.lowercase().trim()
        if (lower.isEmpty()) return null

        val cleanQuery = lower
            .replace(Regex("""\b(tolong|dong|kak|kakak|min|admin|bang|sis|gan|pls|please|coba|mau|pengen|liat|lihat|tunjukin|buka|cek)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val noSpillQuery = cleanQuery
            .replace(Regex("""\b(spill|spil|spi)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val effectiveExcluded = (excludedIndices + masterIdx).toSet()

        // 1. Prioritas Utama: Pencocokan Kata Kunci Langsung (Explicit Keyword Match)
        // Mendukung multi-kata kunci yang dipisah koma, titik koma, atau garis miring (misal: "hitam, black", "spill 1, 1")
        playlist.forEachIndexed { index, item ->
            if (!effectiveExcluded.contains(index)) {
                val keywords = item.spillKeyword.split(Regex("[,;/|]"))
                    .map { it.lowercase().trim() }
                    .filter { it.isNotBlank() }

                for (kw in keywords) {
                    if (lower.contains(kw) || cleanQuery.contains(kw) || (noSpillQuery.isNotBlank() && noSpillQuery.contains(kw))) {
                        return Pair(index, item)
                    }
                    if (noSpillQuery.length >= 2 && kw.contains(noSpillQuery)) {
                        return Pair(index, item)
                    }
                }
            }
        }

        // 2. Pencocokan Angka / Nomor / Etalase (misal "1", "no 1", "no. 1", "nomor 2", "etalase 3", "spill 1", "spil 2", "#1")
        val digitMatch = Regex("""\b(?:no\.?|nomor|etalase|etls|spill|spil|ke|produk|prd|#)?\s*#?\s*([0-9]{1,3})\b""").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\b([0-9]{1,3})\b""").find(noSpillQuery)?.groupValues?.get(1)?.toIntOrNull()
        if (digitMatch != null) {
            val digitStr = digitMatch.toString()
            // a. Cek apakah ada video yang memiliki kata kunci angka ini atau nama diawali angka ini
            playlist.forEachIndexed { index, item ->
                if (!effectiveExcluded.contains(index)) {
                    val keywords = item.spillKeyword.split(Regex("[,;/|]"))
                        .map { it.lowercase().trim() }
                    if (keywords.contains(digitStr) || item.name.startsWith(digitStr)) {
                        return Pair(index, item)
                    }
                }
            }
            // b. Cek berdasarkan indeks urutan ke-(digitMatch) dari daftar video konten kandidat (1-based index)
            val contentCandidates = playlist.indices.filter { !effectiveExcluded.contains(it) }
            val targetCandidateIdx = digitMatch - 1
            if (targetCandidateIdx in contentCandidates.indices) {
                val foundIdx = contentCandidates[targetCandidateIdx]
                return Pair(foundIdx, playlist[foundIdx])
            }
        }

        // 3. Pencocokan Berdasarkan Nama Video (Fallback)
        val queryForName = if (noSpillQuery.isNotBlank()) noSpillQuery else cleanQuery
        if (queryForName.isNotBlank()) {
            playlist.forEachIndexed { index, item ->
                if (!effectiveExcluded.contains(index)) {
                    val itemName = item.name.lowercase().removeSuffix(".mp4").replace(Regex("""[_\-.]"""), " ")
                    if (itemName.contains(queryForName) || queryForName.contains(itemName)) {
                        return Pair(index, item)
                    }
                    val words = queryForName.split(" ").filter { it.length >= 2 }
                    for (w in words) {
                        if (itemName.contains(w)) {
                            return Pair(index, item)
                        }
                    }
                }
            }
        }
        return null
    }
}
