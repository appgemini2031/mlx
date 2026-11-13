package com.tajasb.quran

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * QuranPerAyahAudioPlayer:
 * Pemutar Audio Murottal Imam Per-Ayat (SSSAAA.mp3) dengan:
 * 1. Disk Cache Lokal Otomatis (quran_per_ayah_cache/<imamId>/<SSS><AAA>.mp3)
 * 2. Background Preloader untuk Ayat N+1 dan Ayat N+2 (transisi antar-ayat tanpa jeda buffering)
 * 3. Ticker ~30 FPS (33ms) yang menyinkronkan progres suara Imam (0.0f..1.0f) ke highlight huruf emas
 * 4. Estimator Durasi Tartil Cerdas (Fallback Offline/Low-Signal) yang menghitung panjang huruf + madd + tasydid
 *    agar pergerakan huruf emas dan gulir naik 3-Zona Aman tetap berjalan mulus meskipun tanpa sinyal internet.
 */
class QuranPerAyahAudioPlayer(private val context: Context) {

    companion object {
        private const val TICK_INTERVAL_MS = 33L // ~30 FPS untuk highlight huruf emas & gulir mulus
        private const val CONNECT_TIMEOUT_MS = 6500
        private const val READ_TIMEOUT_MS = 12000

        /**
         * Menghitung estimasi durasi bacaan tartil Imam (dalam milidetik) berdasarkan jumlah
         * grapheme huruf Arab, tanda madd (panjang), dan tasydid/ghunnah.
         */
        fun estimateTartilDurationMs(arabicText: String, graphemeCount: Int): Long {
            val safeGraphemes = graphemeCount.coerceAtLeast(1)
            var maddAndGhunnahBonusMs = 0L
            for (ch in arabicText) {
                when (ch.code) {
                    0x0653, 0x0670, 0x0622 -> maddAndGhunnahBonusMs += 180L // Maddah / Dagger Alif / Alif Madd
                    0x0651 -> maddAndGhunnahBonusMs += 140L // Shaddah / Tasydid (Ghunnah)
                    0x0627, 0x0648, 0x064A -> maddAndGhunnahBonusMs += 45L  // Huruf Madd potensial (Alif, Waw, Ya)
                }
            }
            val baseMs = 950L + (safeGraphemes * 155L) + maddAndGhunnahBonusMs
            return baseMs.coerceIn(2600L, 48000L)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = ConcurrentHashMap<String, Boolean>()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    @Volatile
    private var activeSessionToken: Long = 0L

    private fun getCacheRootDir(): File {
        val dir = File(context.filesDir, "quran_per_ayah_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCachedAyahFile(imam: QuranImam, surahNumber: Int, ayahNumber: Int): File {
        val imamDir = File(getCacheRootDir(), imam.id)
        if (!imamDir.exists()) imamDir.mkdirs()
        return File(imamDir, imam.buildPerAyahCacheFileName(surahNumber, ayahNumber))
    }

    /**
     * Memulai pemutaran audio Imam untuk ayat tertentu dan memicu preloading untuk 2 ayat berikutnya.
     */
    fun playAyah(
        imam: QuranImam,
        surahNumber: Int,
        ayahNumber: Int,
        arabicText: String,
        graphemeCount: Int,
        initialRatio: Float = 0f,
        onProgressRatio: (Float) -> Unit,
        onAyahCompleted: () -> Unit
    ) {
        stopPlaybackInternal()
        val token = ++activeSessionToken

        // Preload 2 ayat berikutnya di background agar saat ayat ini selesai dan naik ke atas,
        // ayat berikutnya di Zona Aman Tengah langsung berbunyi tanpa jeda.
        preloadUpcomingAyahs(imam, surahNumber, ayahNumber)

        playbackJob = scope.launch {
            val cachedFile = withContext(Dispatchers.IO) {
                ensureAyahAudioCached(imam, surahNumber, ayahNumber)
            }
            if (token != activeSessionToken || !isActive) return@launch

            if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 512L) {
                val started = startMediaPlayerWithTicker(
                    token = token,
                    audioFile = cachedFile,
                    initialRatio = initialRatio,
                    onProgressRatio = onProgressRatio,
                    onAyahCompleted = onAyahCompleted
                )
                if (started) return@launch
            }

            // Fallback Tartil Timer: jika perangkat sedang offline atau server audio sibuk,
            // jalankan simulasi ketukan tartil alami agar highlight huruf emas & gulir 3-Zona Aman tetap berjalan mulus.
            runTartilFallbackTicker(
                token = token,
                arabicText = arabicText,
                graphemeCount = graphemeCount,
                initialRatio = initialRatio,
                onProgressRatio = onProgressRatio,
                onAyahCompleted = onAyahCompleted
            )
        }
    }

    private suspend fun startMediaPlayerWithTicker(
        token: Long,
        audioFile: File,
        initialRatio: Float,
        onProgressRatio: (Float) -> Unit,
        onAyahCompleted: () -> Unit
    ): Boolean {
        val mp = MediaPlayer()
        val prepared = try {
            withContext(Dispatchers.IO) {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mp.setDataSource(audioFile.absolutePath)
                mp.prepare()
                true
            }
        } catch (_: Throwable) {
            try { mp.release() } catch (_: Throwable) {}
            false
        }

        if (!prepared || token != activeSessionToken) {
            try { mp.release() } catch (_: Throwable) {}
            return false
        }

        mediaPlayer = mp
        val totalDuration = mp.duration.coerceAtLeast(1200)
        if (initialRatio > 0.02f && initialRatio < 0.92f) {
            try {
                mp.seekTo((totalDuration * initialRatio).toInt())
            } catch (_: Throwable) {}
        }

        var completedFlag = false
        mp.setOnCompletionListener {
            completedFlag = true
        }
        mp.setOnErrorListener { _, _, _ ->
            completedFlag = true
            true
        }

        try {
            mp.start()
        } catch (_: Throwable) {
            stopPlaybackInternal()
            return false
        }

        val startWallMs = System.currentTimeMillis()
        while (token == activeSessionToken && !completedFlag) {
            val pos = try {
                if (mp.isPlaying) mp.currentPosition else totalDuration
            } catch (_: Throwable) {
                totalDuration
            }
            // Gunakan kurva progres sedikit mendahului akhir file (94% durasi audio) karena
            // akhir file MP3 murottal biasanya memiliki 250-400ms gema/hening waqaf.
            val effectiveTextDuration = (totalDuration * 0.94f).coerceAtLeast(800f)
            val ratio = (pos.toFloat() / effectiveTextDuration).coerceIn(0f, 1f)
            onProgressRatio(ratio)

            if (!mp.isPlaying && (System.currentTimeMillis() - startWallMs) > 350L) {
                break
            }
            delay(TICK_INTERVAL_MS)
        }

        if (token == activeSessionToken) {
            onProgressRatio(1.0f)
            stopPlaybackInternal()
            onAyahCompleted()
        }
        return true
    }

    private suspend fun runTartilFallbackTicker(
        token: Long,
        arabicText: String,
        graphemeCount: Int,
        initialRatio: Float,
        onProgressRatio: (Float) -> Unit,
        onAyahCompleted: () -> Unit
    ) {
        val estDurationMs = estimateTartilDurationMs(arabicText, graphemeCount)
        val safeInit = initialRatio.coerceIn(0f, 0.95f)
        val startMs = System.currentTimeMillis() - (estDurationMs * safeInit).toLong()

        while (token == activeSessionToken) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
            val ratio = (elapsed.toFloat() / estDurationMs.toFloat()).coerceIn(0f, 1f)
            onProgressRatio(ratio)
            if (ratio >= 1.0f) break
            delay(TICK_INTERVAL_MS)
        }

        if (token == activeSessionToken) {
            delay(180L) // Jeda waqaf singkat sebelum gulir naik
            if (token == activeSessionToken) {
                onAyahCompleted()
            }
        }
    }

    private fun preloadUpcomingAyahs(imam: QuranImam, currentSurah: Int, currentAyah: Int) {
        ioScope.launch {
            val (nextS1, nextA1) = QuranTextCatalog.getNextCuratedCoord(currentSurah, currentAyah)
            ensureAyahAudioCached(imam, nextS1, nextA1)
            val (nextS2, nextA2) = QuranTextCatalog.getNextCuratedCoord(nextS1, nextA1)
            ensureAyahAudioCached(imam, nextS2, nextA2)
        }
    }

    private fun ensureAyahAudioCached(imam: QuranImam, surahNumber: Int, ayahNumber: Int): File? {
        val targetFile = getCachedAyahFile(imam, surahNumber, ayahNumber)
        if (targetFile.exists() && targetFile.length() > 512L) {
            return targetFile
        }
        val key = "${imam.id}_${surahNumber}_${ayahNumber}"
        if (activeDownloads.putIfAbsent(key, true) != null) {
            // Tunggu sejenak bila unduhan ayat yang sama sedang berjalan
            var waited = 0
            while (activeDownloads.containsKey(key) && waited < 4000) {
                try { Thread.sleep(80L) } catch (_: Throwable) { break }
                waited += 80
            }
            return if (targetFile.exists() && targetFile.length() > 512L) targetFile else null
        }

        try {
            val primaryUrl = imam.buildPerAyahAudioUrl(surahNumber, ayahNumber, useFallback = false)
            if (downloadUrlToFile(primaryUrl, targetFile)) {
                return targetFile
            }
            val fallbackUrl = imam.buildPerAyahAudioUrl(surahNumber, ayahNumber, useFallback = true)
            if (fallbackUrl != primaryUrl && downloadUrlToFile(fallbackUrl, targetFile)) {
                return targetFile
            }
            return null
        } finally {
            activeDownloads.remove(key)
        }
    }

    private fun downloadUrlToFile(urlString: String, targetFile: File): Boolean {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "TajasbQuranPerAyah/2.0")
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                return false
            }
            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                    }
                    output.flush()
                }
            }
            if (tempFile.exists() && tempFile.length() > 512L) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                true
            } else {
                tempFile.delete()
                false
            }
        } catch (_: Throwable) {
            try { tempFile.delete() } catch (_: Throwable) {}
            false
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }

    fun stopPlayback() {
        activeSessionToken++
        stopPlaybackInternal()
    }

    private fun stopPlaybackInternal() {
        playbackJob?.cancel()
        playbackJob = null
        val mp = mediaPlayer
        mediaPlayer = null
        if (mp != null) {
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: Throwable) {}
            try {
                mp.release()
            } catch (_: Throwable) {}
        }
    }
}
