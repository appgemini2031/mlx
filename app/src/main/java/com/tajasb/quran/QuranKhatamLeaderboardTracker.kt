package com.tajasb.quran

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Data model for a Quran Khatam contributor's accumulated achievements across 4 categories:
 * - Al-Qur'an (Juz / Khataman selesai)
 * - Surat selesai
 * - Ayat selesai
 * - Huruf yang ditulis
 */
data class QuranContributorStats(
    val senderName: String,
    var avatarUrl: String = "",
    var b64Avatar: String = "",
    var juzCompleted: Int = 0,
    var surahCompleted: Int = 0,
    var ayahCompleted: Int = 0,
    var lettersWritten: Long = 0L,
    var lastUpdateMs: Long = System.currentTimeMillis()
)

/**
 * QuranKhatamLeaderboardTracker:
 * Thread-safe permanent data store and leaderboard ranker for the 4 Quran Khatam categories:
 * 1. Top 3 Penulis Al-Qur'an (Juz / Khatam selesai)
 * 2. Top 3 Penulis Surat (Surat selesai)
 * 3. Top 3 Penulis Ayat (Ayat selesai)
 * 4. Top 3 Penulis Huruf (Huruf ditulis)
 *
 * Persisted in SharedPreferences so contributor achievements remain permanent across live sessions.
 */
class QuranKhatamLeaderboardTracker private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("quran_leaderboard_prefs", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pendingSaveJob: Job? = null
    private val saveLock = Any()

    private val contributorsBySender = ConcurrentHashMap<String, QuranContributorStats>()

    init {
        loadPersistedStats()
    }

    private fun loadPersistedStats() {
        try {
            val jsonStr = prefs.getString(KEY_CONTRIBUTORS_JSON, null) ?: return
            val jsonArr = JSONArray(jsonStr)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isBlank()) continue
                val key = name.lowercase(Locale.ROOT)
                val stats = QuranContributorStats(
                    senderName = name,
                    avatarUrl = obj.optString("avatarUrl", ""),
                    b64Avatar = obj.optString("b64Avatar", ""),
                    juzCompleted = obj.optInt("juzCompleted", 0),
                    surahCompleted = obj.optInt("surahCompleted", 0),
                    ayahCompleted = obj.optInt("ayahCompleted", 0),
                    lettersWritten = obj.optLong("lettersWritten", 0L),
                    lastUpdateMs = obj.optLong("lastUpdateMs", System.currentTimeMillis())
                )
                contributorsBySender[key] = stats
            }
            Log.i(TAG, "Loaded ${contributorsBySender.size} Quran Khatam contributors from storage.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load Quran leaderboard contributors: ${t.message}")
        }
    }

    fun schedulePersist() {
        synchronized(saveLock) {
            if (pendingSaveJob?.isActive == true) return
            pendingSaveJob = scope.launch {
                delay(300L)
                persistNow()
            }
        }
    }

    fun persistNow() {
        try {
            val jsonArr = JSONArray()
            for (item in contributorsBySender.values) {
                val obj = JSONObject().apply {
                    put("name", item.senderName)
                    if (item.avatarUrl.isNotBlank()) put("avatarUrl", item.avatarUrl)
                    if (item.b64Avatar.isNotBlank() && item.b64Avatar.length <= 4096) put("b64Avatar", item.b64Avatar)
                    put("juzCompleted", item.juzCompleted)
                    put("surahCompleted", item.surahCompleted)
                    put("ayahCompleted", item.ayahCompleted)
                    put("lettersWritten", item.lettersWritten)
                    put("lastUpdateMs", item.lastUpdateMs)
                }
                jsonArr.put(obj)
            }
            prefs.edit().putString(KEY_CONTRIBUTORS_JSON, jsonArr.toString()).apply()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to persist Quran leaderboard: ${t.message}")
        }
    }

    private fun getOrCreate(senderName: String, avatarUrl: String = "", b64Avatar: String = ""): QuranContributorStats {
        val cleanName = senderName.trim().ifBlank { "Penonton" }
        val key = cleanName.lowercase(Locale.ROOT)
        return contributorsBySender.compute(key) { _, existing ->
            if (existing == null) {
                QuranContributorStats(
                    senderName = cleanName,
                    avatarUrl = avatarUrl,
                    b64Avatar = b64Avatar,
                    lastUpdateMs = System.currentTimeMillis()
                )
            } else {
                if (avatarUrl.isNotBlank()) existing.avatarUrl = avatarUrl
                if (b64Avatar.isNotBlank()) existing.b64Avatar = b64Avatar
                existing
            }
        }!!
    }

    fun recordLetters(senderName: String, count: Int, avatarUrl: String = "", b64Avatar: String = "") {
        if (count <= 0) return
        val stats = getOrCreate(senderName, avatarUrl, b64Avatar)
        synchronized(stats) {
            stats.lettersWritten += count
            stats.lastUpdateMs = System.currentTimeMillis()
        }
        schedulePersist()
    }

    fun recordAyahCompleted(senderName: String, avatarUrl: String = "", b64Avatar: String = "") {
        val stats = getOrCreate(senderName, avatarUrl, b64Avatar)
        synchronized(stats) {
            stats.ayahCompleted += 1
            stats.lastUpdateMs = System.currentTimeMillis()
        }
        schedulePersist()
    }

    fun recordSurahCompleted(senderName: String, avatarUrl: String = "", b64Avatar: String = "") {
        val stats = getOrCreate(senderName, avatarUrl, b64Avatar)
        synchronized(stats) {
            stats.surahCompleted += 1
            stats.lastUpdateMs = System.currentTimeMillis()
        }
        schedulePersist()
    }

    fun recordJuzCompleted(senderName: String, avatarUrl: String = "", b64Avatar: String = "") {
        val stats = getOrCreate(senderName, avatarUrl, b64Avatar)
        synchronized(stats) {
            stats.juzCompleted += 1
            stats.lastUpdateMs = System.currentTimeMillis()
        }
        schedulePersist()
    }

    fun getTop3Quran(): List<QuranContributorStats> {
        return contributorsBySender.values
            .filter { it.juzCompleted > 0 || it.surahCompleted > 0 }
            .sortedWith(
                compareByDescending<QuranContributorStats> { it.juzCompleted }
                    .thenByDescending { it.surahCompleted }
                    .thenByDescending { it.ayahCompleted }
                    .thenByDescending { it.lettersWritten }
                    .thenByDescending { it.lastUpdateMs }
            )
            .take(3)
    }

    fun getTop3Surah(): List<QuranContributorStats> {
        return contributorsBySender.values
            .filter { it.surahCompleted > 0 }
            .sortedWith(
                compareByDescending<QuranContributorStats> { it.surahCompleted }
                    .thenByDescending { it.ayahCompleted }
                    .thenByDescending { it.lettersWritten }
                    .thenByDescending { it.lastUpdateMs }
            )
            .take(3)
    }

    fun getTop3Ayah(): List<QuranContributorStats> {
        return contributorsBySender.values
            .filter { it.ayahCompleted > 0 }
            .sortedWith(
                compareByDescending<QuranContributorStats> { it.ayahCompleted }
                    .thenByDescending { it.lettersWritten }
                    .thenByDescending { it.lastUpdateMs }
            )
            .take(3)
    }

    fun getTop3Letters(): List<QuranContributorStats> {
        return contributorsBySender.values
            .filter { it.lettersWritten > 0 }
            .sortedWith(
                compareByDescending<QuranContributorStats> { it.lettersWritten }
                    .thenByDescending { it.lastUpdateMs }
            )
            .take(3)
    }

    /**
     * Builds a single unified 4-category leaderboard payload string:
     * Format: "QURAN:::item1;;item2;;item3|||SURAH:::item1;;...|||AYAH:::...|||HURUF:::..."
     * Where item is "Name~ScoreLabel~b64Avatar"
     */
    fun buildUnified4CategoryPayload(
        showQuran: Boolean = true,
        showSurah: Boolean = true,
        showAyah: Boolean = true,
        showLetters: Boolean = true
    ): String {
        val nf = NumberFormat.getIntegerInstance(Locale.GERMANY) // e.g. 1.250

        fun formatItems(list: List<QuranContributorStats>, type: String): String {
            if (list.isEmpty()) return ""
            return list.joinToString(";;") { item ->
                val safeName = item.senderName.replace("~", " ").replace(";", " ").replace("|", " ").replace(":", " ").trim()
                val score = when (type) {
                    "QURAN" -> when {
                        item.juzCompleted > 0 -> "${item.juzCompleted} Juz"
                        item.surahCompleted > 0 -> "${item.surahCompleted} Surat"
                        else -> "1 Juz"
                    }
                    "SURAH" -> "${item.surahCompleted} Surat"
                    "AYAH" -> "${item.ayahCompleted} Ayat"
                    else -> "${nf.format(item.lettersWritten)} Huruf"
                }
                val safeB64 = if (item.b64Avatar.length <= 4096) item.b64Avatar else ""
                "$safeName~$score~$safeB64"
            }
        }

        val quranStr = if (showQuran) "QURAN:::" + formatItems(getTop3Quran(), "QURAN") else ""
        val surahStr = if (showSurah) "SURAH:::" + formatItems(getTop3Surah(), "SURAH") else ""
        val ayahStr = if (showAyah) "AYAH:::" + formatItems(getTop3Ayah(), "AYAH") else ""
        val lettersStr = if (showLetters) "HURUF:::" + formatItems(getTop3Letters(), "HURUF") else ""

        return listOf(quranStr, surahStr, ayahStr, lettersStr)
            .filter { it.isNotBlank() }
            .joinToString("|||")
    }

    fun resetAllData() {
        contributorsBySender.clear()
        prefs.edit().remove(KEY_CONTRIBUTORS_JSON).apply()
        Log.i(TAG, "Quran Khatam Leaderboard data has been reset.")
    }

    companion object {
        private const val TAG = "QuranKhatamTracker"
        private const val KEY_CONTRIBUTORS_JSON = "key_contributors_json"

        @Volatile
        private var instance: QuranKhatamLeaderboardTracker? = null

        fun getInstance(context: Context): QuranKhatamLeaderboardTracker {
            return instance ?: synchronized(this) {
                instance ?: QuranKhatamLeaderboardTracker(context.applicationContext ?: context).also { instance = it }
            }
        }

        fun peekInstance(): QuranKhatamLeaderboardTracker? = instance
    }
}
