package com.tajasb.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.tajasb.data.VcamRepository
import com.tajasb.engine.SmartAudioStitcher
import com.tajasb.model.AudioItem
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Konfigurasi Edit Audio Massal (Batch Audio Editor) sebelum SV4 Audio dijalankan.
 * Mendukung:
 * - Potong Awal (Trim Start) & Potong Akhir (Trim End)
 * - Penguat Volume / Gain Boost (50% s/d 200%)
 * - Kecepatan / Tempo Audio (0.85x s/d 1.25x)
 * - Fade-In & Fade-Out Halus
 * - Tes / Preview instan pada HANYA 1 Audio Sampel sebelum diterapkan massal ke seluruh trek LA1..LAn.
 */
data class BatchAudioEditConfig(
    val trimStartSec: Float = 0f,
    val trimEndSec: Float = 0f,
    val volumeGainPercent: Int = 100,
    val speedMultiplier: Float = 1.0f,
    val fadeInSec: Float = 0f,
    val fadeOutSec: Float = 0f,
    val voiceEffect: com.tajasb.model.VoiceEffect = com.tajasb.model.VoiceEffect.NORMAL
) {
    val isIdentity: Boolean
        get() = trimStartSec <= 0.01f &&
            trimEndSec <= 0.01f &&
            volumeGainPercent == 100 &&
            abs(speedMultiplier - 1.0f) <= 0.01f &&
            fadeInSec <= 0.01f &&
            fadeOutSec <= 0.01f &&
            voiceEffect == com.tajasb.model.VoiceEffect.NORMAL

    val isDefault: Boolean
        get() = isIdentity

    fun summaryBadge(): String = toSummaryString()

    fun toSummaryString(): String {
        val parts = mutableListOf<String>()
        if (voiceEffect != com.tajasb.model.VoiceEffect.NORMAL) {
            parts.add("${voiceEffect.icon} ${voiceEffect.label}")
        }
        if (trimStartSec > 0.01f) parts.add("Potong Awal -${formatSec(trimStartSec)}d")
        if (trimEndSec > 0.01f) parts.add("Potong Akhir -${formatSec(trimEndSec)}d")
        if (volumeGainPercent != 100) parts.add("Vol ${volumeGainPercent}%")
        if (abs(speedMultiplier - 1.0f) > 0.01f) parts.add("Tempo ${String.format(java.util.Locale.US, "%.2fx", speedMultiplier)}")
        if (fadeInSec > 0.01f) parts.add("FadeIn ${formatSec(fadeInSec)}d")
        if (fadeOutSec > 0.01f) parts.add("FadeOut ${formatSec(fadeOutSec)}d")
        return if (parts.isEmpty()) "Standar (Tanpa Perubahan)" else parts.joinToString(" • ")
    }

    private fun formatSec(sec: Float): String {
        return if (sec % 1f == 0f) "${sec.toInt()}" else String.format(java.util.Locale.US, "%.1f", sec)
    }
}

object BatchAudioProcessor {
    private const val TAG = "BatchAudioProcessor"
    private const val TARGET_SAMPLE_RATE = 44100
    private const val TARGET_CHANNELS = 2
    private const val TARGET_BITRATE = 128_000
    private const val TIMEOUT_US = 10_000L

    data class SingleTestResult(
        val success: Boolean,
        val previewItem: AudioItem? = null,
        val originalDurationMs: Long = 0L,
        val editedDurationMs: Long = 0L,
        val message: String
    )

    data class BatchApplyResult(
        val successCount: Int,
        val totalCount: Int,
        val message: String
    )

    private fun getBackupDir(context: Context): File {
        val dir = File(context.filesDir, "Camera1/audio_orig_backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getPreviewDir(context: Context): File {
        val dir = File(context.cacheDir, "audio_batch_preview")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Memastikan file asli sebelum edit massal disimpan cadangannya (1x per ID audio),
     * sehingga pengguna bisa mengedit ulang dari file mentah asli kapan saja tanpa kehilangan durasi yang sudah terpotong.
     */
    private fun ensureOriginalBackup(context: Context, item: AudioItem): File {
        val srcFile = File(item.path)
        val backupDir = getBackupDir(context)
        val safeId = item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val ext = srcFile.extension.ifBlank { "m4a" }
        val backupFile = File(backupDir, "orig_${safeId}.$ext")
        if (!backupFile.exists() || backupFile.length() < 128L) {
            if (srcFile.exists() && srcFile.length() >= 128L) {
                try {
                    srcFile.copyTo(backupFile, overwrite = true)
                } catch (e: Throwable) {
                    Log.w(TAG, "Gagal menyalin backup audio asli: ${e.message}")
                    return srcFile
                }
            } else {
                return srcFile
            }
        }
        return if (backupFile.exists() && backupFile.length() >= 128L) backupFile else srcFile
    }

    fun hasAnyOriginalBackups(
        context: Context,
        items: List<AudioItem>,
        isExcluded: ((AudioItem) -> Boolean)? = null
    ): Boolean {
        val backupDir = getBackupDir(context)
        if (!backupDir.exists()) return false
        return items.any { item ->
            if (isExcluded?.invoke(item) == true) return@any false
            val safeId = item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            backupDir.listFiles()?.any { it.name.startsWith("orig_${safeId}.") && it.length() > 128L } == true
        }
    }

    /**
     * Memulihkan seluruh trek audio ke file mentah asli sebelum edit massal.
     * Hanya memulihkan Audio Tipe 1 (Audio Tipe 2 & Tipe 3 tidak diikutkan).
     */
    fun restoreOriginalAudios(repo: VcamRepository): Int {
        val context = repo.context
        val backupDir = getBackupDir(context)
        if (!backupDir.exists()) return 0

        val currentList = repo.config.value.audioPlaylist.toMutableList()
        val isExcluded: (AudioItem) -> Boolean = { repo.config.value.isExcludedFromType1(it) }
        var restored = 0

        for (i in currentList.indices) {
            val item = currentList[i]
            if (item.id.startsWith("vs_audio_rolling_") || isExcluded(item)) continue
            val safeId = item.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val backupFile = backupDir.listFiles()?.firstOrNull {
                it.name.startsWith("orig_${safeId}.") && it.length() > 128L
            } ?: continue

            try {
                val targetFile = File(item.path)
                backupFile.copyTo(targetFile, overwrite = true)
                val durMs = SmartAudioStitcher.probeDurationMs(targetFile).takeIf { it > 0L } ?: item.durationMs
                currentList[i] = item.copy(
                    durationMs = durMs,
                    sizeBytes = targetFile.length()
                )
                restored++
            } catch (e: Throwable) {
                Log.w(TAG, "Gagal memulihkan audio asli ${item.name}: ${e.message}")
            }
        }

        if (restored > 0) {
            clearNormalizedStitchCache(context)
            repo.updateAudioPlaylistAfterBatchEditSync(currentList)
        }
        return restored
    }

    /**
     * TES 1 AUDIO SAMPEL:
     * Menerapkan pengaturan edit massal HANYA pada 1 file audio sampel yang dipilih pengguna,
     * sehingga pengguna bisa langsung mendengar hasil potong awal/akhir, volume, tempo, & fade
     * secara instan tanpa perlu memproses semua file terlebih dahulu.
     */
    fun processSingleAudioForTest(
        context: Context,
        sampleItem: AudioItem,
        config: BatchAudioEditConfig,
        onProgress: ((Int, String) -> Unit)? = null
    ): SingleTestResult {
        val rawSourceFile = ensureOriginalBackup(context, sampleItem)
        if (!rawSourceFile.exists() || rawSourceFile.length() < 128L) {
            return SingleTestResult(
                success = false,
                message = "⚠️ File audio sampel tidak ditemukan: ${sampleItem.name}"
            )
        }

        val origDurationMs = SmartAudioStitcher.probeDurationMs(rawSourceFile)
            .takeIf { it > 0L } ?: sampleItem.durationMs.coerceAtLeast(1000L)

        if (origDurationMs > 15 * 60 * 1000L) {
            return SingleTestResult(
                success = false,
                message = "⚠️ Audio terlalu panjang (${origDurationMs / 60000} menit). Edit massal hanya mendukung lagu musik/BGM durasi < 15 menit."
            )
        }

        val previewDir = getPreviewDir(context)
        previewDir.listFiles()?.forEach {
            if (it.name.startsWith("test_sample_")) {
                try { it.delete() } catch (_: Throwable) {}
            }
        }

        val outM4a = File(previewDir, "test_sample_${System.currentTimeMillis()}.m4a")
        val outWav = File(previewDir, "test_sample_${System.currentTimeMillis()}.wav")

        onProgress?.invoke(15, "Memproses sampel: ${sampleItem.name}...")

        val finalFile = renderEditedAudioFile(
            inputFile = rawSourceFile,
            outputM4a = outM4a,
            fallbackWav = outWav,
            config = config,
            onProgress = { pct ->
                onProgress?.invoke(pct, "Merender tes 1 audio (${sampleItem.name})...")
            }
        )

        if (finalFile == null || !finalFile.exists() || finalFile.length() < 256L) {
            return SingleTestResult(
                success = false,
                message = "⚠️ Gagal memproses tes audio (Pastikan durasi potong tidak melebihi total durasi lagu)"
            )
        }

        val editedDurationMs = SmartAudioStitcher.probeDurationMs(finalFile)
            .takeIf { it > 0L } ?: max(1000L, origDurationMs)

        val previewItem = AudioItem(
            id = "batch_test_preview_${sampleItem.id}",
            name = "🎧 [TES SAMPEL] ${sampleItem.name}",
            path = finalFile.absolutePath,
            uri = android.net.Uri.fromFile(finalFile).toString(),
            durationMs = editedDurationMs,
            sizeBytes = finalFile.length(),
            isMuted = false,
            isEnabled = true
        )

        return SingleTestResult(
            success = true,
            previewItem = previewItem,
            originalDurationMs = origDurationMs,
            editedDurationMs = editedDurationMs,
            message = "🎧 Memutar Tes 1 Audio: ${sampleItem.name} (${origDurationMs / 1000}s ➔ ${editedDurationMs / 1000}s • ${config.toSummaryString()})"
        )
    }

    /**
     * TERAPKAN EDIT MASSAL KE SEMUA AUDIO:
     * Memproses seluruh trek sumber (LA1..LAn) di daftar putar menggunakan parameter yang sudah dites.
     * PENTING: Audio Tipe 2 (Latar Mandiri) dan Audio Tipe 3 (Ringtone Acak) TIDAK diikutkan.
     */
    fun applyBatchEditToAllAudios(
        repo: VcamRepository,
        config: BatchAudioEditConfig,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ): BatchApplyResult {
        val context = repo.context
        val fullPlaylist = repo.config.value.audioPlaylist.toMutableList()
        val isExcluded: (AudioItem) -> Boolean = { repo.config.value.isExcludedFromType1(it) }
        val sourceIndices = fullPlaylist.indices.filter { idx ->
            val item = fullPlaylist[idx]
            !repo.isRollingVsAudioItem(item) && !isExcluded(item)
        }

        if (sourceIndices.isEmpty()) {
            return BatchApplyResult(
                successCount = 0,
                totalCount = 0,
                message = "⚠️ Belum ada trek Audio Tipe 1 di daftar musik untuk diedit massal (Tipe 2 & 3 otomatis dikecualikan)"
            )
        }

        val workDir = getPreviewDir(context)
        var successCount = 0
        val total = sourceIndices.size

        for ((stepIdx, playlistIdx) in sourceIndices.withIndex()) {
            val item = fullPlaylist[playlistIdx]
            onProgress?.invoke(
                stepIdx + 1,
                total,
                "Mengedit LA${stepIdx + 1} (${stepIdx + 1}/$total): ${item.name}"
            )

            val rawSourceFile = ensureOriginalBackup(context, item)
            if (!rawSourceFile.exists() || rawSourceFile.length() < 128L) continue

            val tempM4a = File(workDir, "batch_out_${stepIdx}_${System.nanoTime()}.m4a")
            val tempWav = File(workDir, "batch_out_${stepIdx}_${System.nanoTime()}.wav")

            val rendered = renderEditedAudioFile(
                inputFile = rawSourceFile,
                outputM4a = tempM4a,
                fallbackWav = tempWav,
                config = config
            )

            if (rendered != null && rendered.exists() && rendered.length() > 256L) {
                try {
                    val currentTarget = File(item.path)
                    val newExt = rendered.extension.ifBlank { "m4a" }
                    val targetDir = currentTarget.parentFile ?: repo.getInternalCamera1Directory()
                    if (!targetDir.exists()) targetDir.mkdirs()

                    val finalTarget = if (currentTarget.extension.equals(newExt, ignoreCase = true)) {
                        currentTarget
                    } else {
                        File(targetDir, "${currentTarget.nameWithoutExtension}.$newExt")
                    }

                    rendered.copyTo(finalTarget, overwrite = true)
                    rendered.delete()
                    if (finalTarget.absolutePath != currentTarget.absolutePath && currentTarget.exists()) {
                        try { currentTarget.delete() } catch (_: Throwable) {}
                    }

                    // Bersihkan nama tampilan dari tag edit sebelumnya jika ada, lalu perbarui metadata
                    val cleanBaseName = item.name.replace(Regex("^✨\\s*"), "")
                    val newDurationMs = SmartAudioStitcher.probeDurationMs(finalTarget)
                        .takeIf { it > 0L } ?: item.durationMs

                    fullPlaylist[playlistIdx] = item.copy(
                        name = if (config.isIdentity) cleanBaseName else "✨ $cleanBaseName",
                        path = finalTarget.absolutePath,
                        uri = android.net.Uri.fromFile(finalTarget).toString(),
                        durationMs = newDurationMs,
                        sizeBytes = finalTarget.length()
                    )
                    successCount++
                } catch (e: Throwable) {
                    Log.e(TAG, "Gagal menyimpan hasil edit massal untuk ${item.name}: ${e.message}")
                    try { rendered.delete() } catch (_: Throwable) {}
                }
            } else {
                try { tempM4a.delete() } catch (_: Throwable) {}
                try { tempWav.delete() } catch (_: Throwable) {}
            }
        }

        if (successCount > 0) {
            clearNormalizedStitchCache(context)
            repo.updateAudioPlaylistAfterBatchEditSync(fullPlaylist)
        }

        val msg = if (successCount > 0) {
            "✅ Edit Audio Massal Selesai ($successCount/$total Trek Diperbarui • ${config.toSummaryString()}). Siap dirakit ke SV4 Audio!"
        } else {
            "⚠️ Gagal menerapkan edit massal. Periksa durasi potong agar tidak melebihi panjang lagu."
        }
        return BatchApplyResult(
            successCount = successCount,
            totalCount = total,
            message = msg
        )
    }

    private fun clearNormalizedStitchCache(context: Context) {
        try {
            val outDir = SmartAudioStitcher.getRollingVsAudioDir(context)
            val normCacheDir = File(outDir, "norm_cache")
            if (normCacheDir.exists()) {
                normCacheDir.listFiles()?.forEach { f ->
                    try { f.delete() } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Merender 1 file audio dengan:
     * 1. Decode ke PCM 16-bit 44.1kHz Stereo (dengan pemotongan awal & akhir yang presisi)
     * 2. Penyesuaian Tempo/Kecepatan, Volume Gain (dengan soft-limiter anti-pecah), dan Fade-In / Fade-Out
     * 3. Encode ke AAC .m4a (atau fallback ke WAV PCM jika encoder perangkat bermasalah).
     */
    private fun renderEditedAudioFile(
        inputFile: File,
        outputM4a: File,
        fallbackWav: File,
        config: BatchAudioEditConfig,
        onProgress: ((Int) -> Unit)? = null
    ): File? {
        val rawPcmFile = File(outputM4a.parentFile, "tmp_edit_pcm_${System.nanoTime()}.pcm")
        try {
            val totalDurationMs = SmartAudioStitcher.probeDurationMs(inputFile).coerceAtLeast(0L)
            if (totalDurationMs > 15 * 60 * 1000L) {
                Log.w(TAG, "Audio file terlalu panjang ($totalDurationMs ms), melewati edit massal.")
                return null
            }
            val trimStartUs = (config.trimStartSec.coerceAtLeast(0f) * 1_000_000f).toLong()
            val trimEndUs = (config.trimEndSec.coerceAtLeast(0f) * 1_000_000f).toLong()
            val totalDurationUs = totalDurationMs * 1000L

            val effectiveEndUs = if (totalDurationUs > 0L) {
                val candidateEnd = totalDurationUs - trimEndUs
                // Pastikan minimal tersisa 0.8 detik audio jika pengguna memotong terlalu panjang
                if (candidateEnd > trimStartUs + 800_000L) {
                    candidateEnd
                } else {
                    max(trimStartUs + 800_000L, totalDurationUs)
                }
            } else {
                Long.MAX_VALUE
            }

            val effectiveStartUs = if (totalDurationUs > 0L && trimStartUs >= totalDurationUs - 500_000L) {
                max(0L, totalDurationUs - 1_000_000L)
            } else {
                trimStartUs
            }

            onProgress?.invoke(25)
            val decodedBytes = decodeTrimmedStereo44kPcm(
                inputFile = inputFile,
                outputPcmFile = rawPcmFile,
                startUs = effectiveStartUs,
                endUs = effectiveEndUs,
                speedMultiplier = config.speedMultiplier.coerceIn(0.85f, 1.25f)
            )
            if (decodedBytes <= 0L || !rawPcmFile.exists()) {
                return null
            }

            onProgress?.invoke(55)
            // Terapkan Volume Gain + Fade-In + Fade-Out secara in-place pada file PCM
            applyGainAndFadeToPcmFile(
                pcmFile = rawPcmFile,
                totalPcmBytes = decodedBytes,
                volumeGainPercent = config.volumeGainPercent.coerceIn(50, 200),
                fadeInSec = config.fadeInSec.coerceIn(0f, 5f),
                fadeOutSec = config.fadeOutSec.coerceIn(0f, 5f)
            )

            // Terapkan Efek Suara DSP (Kamar, Rumah, Echo, Bass Boost, dsb.)
            applyVoiceEffectDspToPcmFile(
                pcmFile = rawPcmFile,
                totalPcmBytes = decodedBytes,
                effect = config.voiceEffect
            )

            onProgress?.invoke(75)
            // Encode PCM ke AAC .m4a
            if (encodePcmFileToAacM4a(rawPcmFile, outputM4a)) {
                onProgress?.invoke(100)
                return outputM4a
            }

            // Fallback ke WAV jika perangkat gagal encode AAC
            if (writePcmFileToWav(rawPcmFile, decodedBytes, fallbackWav)) {
                onProgress?.invoke(100)
                return fallbackWav
            }
            return null
        } catch (e: Throwable) {
            Log.e(TAG, "Gagal merender audio edit ${inputFile.name}: ${e.message}", e)
            return null
        } finally {
            try {
                if (rawPcmFile.exists()) rawPcmFile.delete()
            } catch (_: Throwable) {}
        }
    }

    private fun decodeTrimmedStereo44kPcm(
        inputFile: File,
        outputPcmFile: File,
        startUs: Long,
        endUs: Long,
        speedMultiplier: Float
    ): Long {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var trackIdx = -1
            var fmt: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIdx = i
                    fmt = f
                    break
                }
            }
            if (trackIdx < 0 || fmt == null) return 0L
            extractor.selectTrack(trackIdx)

            if (startUs > 250_000L) {
                extractor.seekTo(max(0L, startUs - 150_000L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return 0L
            var srcRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else TARGET_SAMPLE_RATE
            var srcChannels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else TARGET_CHANNELS

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(fmt, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var totalWrittenBytes = 0L

            FileOutputStream(outputPcmFile).use { fos ->
                while (!outDone) {
                    if (!inDone) {
                        val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inIdx >= 0) {
                            val inBuf = decoder.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                val sampleSize = extractor.readSampleData(inBuf, 0)
                                val sampleTimeUs = extractor.sampleTime
                                if (sampleSize < 0 || (endUs != Long.MAX_VALUE && sampleTimeUs > endUs)) {
                                    decoder.queueInputBuffer(
                                        inIdx, 0, 0, 0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inDone = true
                                } else {
                                    decoder.queueInputBuffer(
                                        inIdx, 0, sampleSize,
                                        sampleTimeUs.coerceAtLeast(0L), 0
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        outIdx >= 0 -> {
                            val outBuf = decoder.getOutputBuffer(outIdx)
                            val ptsUs = info.presentationTimeUs
                            if (outBuf != null && info.size > 0 && ptsUs >= startUs && ptsUs <= endUs) {
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                val raw = ByteArray(info.size)
                                outBuf.get(raw)
                                val conv = resampleAndConvertPcm16(
                                    rawBytes = raw,
                                    srcSampleRate = srcRate,
                                    srcChannels = srcChannels,
                                    speedMultiplier = speedMultiplier
                                )
                                if (conv.isNotEmpty()) {
                                    fos.write(conv)
                                    totalWrittenBytes += conv.size
                                }
                            }
                            decoder.releaseOutputBuffer(outIdx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ||
                                (endUs != Long.MAX_VALUE && ptsUs > endUs)
                            ) {
                                outDone = true
                            }
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val nf = decoder.outputFormat
                            if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                srcRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                srcChannels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                    }
                }
            }
            return totalWrittenBytes
        } catch (e: Throwable) {
            Log.w(TAG, "Gagal decode trimmed PCM: ${e.message}")
            return 0L
        } finally {
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            try { extractor?.release() } catch (_: Throwable) {}
        }
    }

    private fun resampleAndConvertPcm16(
        rawBytes: ByteArray,
        srcSampleRate: Int,
        srcChannels: Int,
        speedMultiplier: Float
    ): ByteArray {
        if (rawBytes.size < 2) return ByteArray(0)
        val totalShorts = rawBytes.size / 2
        val safeChannels = srcChannels.coerceAtLeast(1)
        val srcFrames = totalShorts / safeChannels
        if (srcFrames <= 0) return ByteArray(0)

        val shortBuf = ByteBuffer.wrap(rawBytes, 0, srcFrames * safeChannels * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val safeSpeed = speedMultiplier.coerceIn(0.85f, 1.25f)
        if (srcSampleRate == TARGET_SAMPLE_RATE && safeChannels == TARGET_CHANNELS && abs(safeSpeed - 1.0f) <= 0.01f) {
            return rawBytes.copyOf(srcFrames * 4)
        }

        val effectiveRateRatio = TARGET_SAMPLE_RATE.toDouble() / (srcSampleRate.coerceAtLeast(1).toDouble() * safeSpeed.toDouble())
        val dstFrames = max(1, (srcFrames.toDouble() * effectiveRateRatio).roundToInt())
        val outBytes = ByteArray(dstFrames * 4)
        val outShortBuf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until dstFrames) {
            val srcFrameIdx = ((i.toLong() * srcFrames) / dstFrames).toInt().coerceIn(0, srcFrames - 1)
            val baseIdx = srcFrameIdx * safeChannels
            val left = shortBuf.get(baseIdx)
            val right = if (safeChannels >= 2) shortBuf.get(baseIdx + 1) else left
            outShortBuf.put(left)
            outShortBuf.put(right)
        }
        return outBytes
    }

    private fun applyGainAndFadeToPcmFile(
        pcmFile: File,
        totalPcmBytes: Long,
        volumeGainPercent: Int,
        fadeInSec: Float,
        fadeOutSec: Float
    ) {
        if (volumeGainPercent == 100 && fadeInSec <= 0.01f && fadeOutSec <= 0.01f) return
        val totalFrames = totalPcmBytes / 4L // 16-bit stereo = 4 bytes/frame
        if (totalFrames <= 0L) return

        val fadeInFrames = (fadeInSec * TARGET_SAMPLE_RATE).toLong().coerceAtLeast(0L)
        val fadeOutFrames = (fadeOutSec * TARGET_SAMPLE_RATE).toLong().coerceAtLeast(0L)
        val baseGain = volumeGainPercent / 100.0f

        RandomAccessFile(pcmFile, "rw").use { raf ->
            val chunkBytes = 65536 // 16384 frames per chunk
            val buf = ByteArray(chunkBytes)
            var currentBytePos = 0L
            var currentFrameIdx = 0L

            while (currentBytePos < totalPcmBytes) {
                raf.seek(currentBytePos)
                val read = raf.read(buf)
                if (read <= 0) break
                val validBytes = read - (read % 4)
                val framesInChunk = validBytes / 4
                val bb = ByteBuffer.wrap(buf, 0, validBytes).order(ByteOrder.LITTLE_ENDIAN)

                for (f in 0 until framesInChunk) {
                    val globalFrame = currentFrameIdx + f
                    var mult = baseGain
                    if (fadeInFrames > 0L && globalFrame < fadeInFrames) {
                        mult *= (globalFrame.toFloat() / fadeInFrames.toFloat()).coerceIn(0f, 1f)
                    }
                    val framesFromEnd = (totalFrames - 1L - globalFrame).coerceAtLeast(0L)
                    if (fadeOutFrames > 0L && framesFromEnd < fadeOutFrames) {
                        mult *= (framesFromEnd.toFloat() / fadeOutFrames.toFloat()).coerceIn(0f, 1f)
                    }

                    val offset = f * 4
                    val leftIn = bb.getShort(offset).toInt()
                    val rightIn = bb.getShort(offset + 2).toInt()

                    val leftOut = softClipSample((leftIn * mult).roundToInt())
                    val rightOut = softClipSample((rightIn * mult).roundToInt())

                    bb.putShort(offset, leftOut)
                    bb.putShort(offset + 2, rightOut)
                }

                raf.seek(currentBytePos)
                raf.write(buf, 0, validBytes)
                currentBytePos += validBytes
                currentFrameIdx += framesInChunk
            }
        }
    }

    private fun applyVoiceEffectDspToPcmFile(
        pcmFile: File,
        totalPcmBytes: Long,
        effect: com.tajasb.model.VoiceEffect
    ) {
        if (effect == com.tajasb.model.VoiceEffect.NORMAL) return
        val totalFrames = totalPcmBytes / 4L
        if (totalFrames <= 0L) return

        try {
            when (effect) {
                com.tajasb.model.VoiceEffect.ROOM -> {
                    // Reverb Kamar: Delay pendek (~28ms) feedback lembut 0.22f
                    applyDelayEchoToPcm(pcmFile, totalPcmBytes, delayMs = 28, feedback = 0.22f, wetMix = 0.25f)
                }
                com.tajasb.model.VoiceEffect.HOUSE -> {
                    // Reverb Rumah/Aula: Delay ruangan lega (~75ms) feedback 0.38f
                    applyDelayEchoToPcm(pcmFile, totalPcmBytes, delayMs = 75, feedback = 0.38f, wetMix = 0.35f)
                }
                com.tajasb.model.VoiceEffect.ECHO -> {
                    // Echo / Gema: Delay ritmis berulang (~220ms) feedback 0.45f
                    applyDelayEchoToPcm(pcmFile, totalPcmBytes, delayMs = 220, feedback = 0.45f, wetMix = 0.38f)
                }
                com.tajasb.model.VoiceEffect.BASS_BOOST -> {
                    // Bass Boost: Penguat komponen frekuensi rendah
                    applyBassBoostToPcm(pcmFile, totalPcmBytes, boostMultiplier = 1.6f)
                }
                com.tajasb.model.VoiceEffect.MAN, com.tajasb.model.VoiceEffect.ROBOT -> {
                    // Pewarnaan nada rendah untuk karakter pria / robot
                    applyBassBoostToPcm(pcmFile, totalPcmBytes, boostMultiplier = 1.35f)
                }
                else -> {
                    // WOMAN, CHIPMUNK: nada tinggi dihandle oleh speed/pitch
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Gagal menerapkan filter DSP ${effect.label}: ${e.message}")
        }
    }

    private fun applyDelayEchoToPcm(
        pcmFile: File,
        totalPcmBytes: Long,
        delayMs: Int,
        feedback: Float,
        wetMix: Float
    ) {
        val delayFrames = ((delayMs * TARGET_SAMPLE_RATE) / 1000).coerceIn(100, 22050)
        val historyL = FloatArray(delayFrames)
        val historyR = FloatArray(delayFrames)
        var cursor = 0

        RandomAccessFile(pcmFile, "rw").use { raf ->
            val chunkBytes = 16384 // 4096 frames
            val buf = ByteArray(chunkBytes)
            var currentBytePos = 0L

            while (currentBytePos < totalPcmBytes) {
                raf.seek(currentBytePos)
                val read = raf.read(buf)
                if (read <= 0) break
                val validBytes = read - (read % 4)
                val framesInChunk = validBytes / 4
                val bb = ByteBuffer.wrap(buf, 0, validBytes).order(ByteOrder.LITTLE_ENDIAN)

                for (f in 0 until framesInChunk) {
                    val offset = f * 4
                    val inL = bb.getShort(offset).toFloat()
                    val inR = bb.getShort(offset + 2).toFloat()

                    val delayedL = historyL[cursor]
                    val delayedR = historyR[cursor]

                    val dryMix = 1.0f - (wetMix * 0.35f)
                    val outL = softClipSample((inL * dryMix + delayedL * wetMix).roundToInt())
                    val outR = softClipSample((inR * dryMix + delayedR * wetMix).roundToInt())

                    historyL[cursor] = inL + delayedL * feedback
                    historyR[cursor] = inR + delayedR * feedback

                    cursor = (cursor + 1) % delayFrames

                    bb.putShort(offset, outL)
                    bb.putShort(offset + 2, outR)
                }

                raf.seek(currentBytePos)
                raf.write(buf, 0, validBytes)
                currentBytePos += validBytes
            }
        }
    }

    private fun applyBassBoostToPcm(
        pcmFile: File,
        totalPcmBytes: Long,
        boostMultiplier: Float
    ) {
        // Single-pole IIR low-pass filter untuk memisahkan dan mengangkat bass
        // Cutoff frekuensi ~220Hz di 44.1kHz -> alpha ~= 0.03f
        val alpha = 0.035f
        var lowL = 0f
        var lowR = 0f
        val boostGain = (boostMultiplier - 1.0f).coerceAtLeast(0f)

        RandomAccessFile(pcmFile, "rw").use { raf ->
            val chunkBytes = 16384
            val buf = ByteArray(chunkBytes)
            var currentBytePos = 0L

            while (currentBytePos < totalPcmBytes) {
                raf.seek(currentBytePos)
                val read = raf.read(buf)
                if (read <= 0) break
                val validBytes = read - (read % 4)
                val framesInChunk = validBytes / 4
                val bb = ByteBuffer.wrap(buf, 0, validBytes).order(ByteOrder.LITTLE_ENDIAN)

                for (f in 0 until framesInChunk) {
                    val offset = f * 4
                    val inL = bb.getShort(offset).toFloat()
                    val inR = bb.getShort(offset + 2).toFloat()

                    lowL += alpha * (inL - lowL)
                    lowR += alpha * (inR - lowR)

                    val outL = softClipSample((inL + lowL * boostGain).roundToInt())
                    val outR = softClipSample((inR + lowR * boostGain).roundToInt())

                    bb.putShort(offset, outL)
                    bb.putShort(offset + 2, outR)
                }

                raf.seek(currentBytePos)
                raf.write(buf, 0, validBytes)
                currentBytePos += validBytes
            }
        }
    }

    private fun softClipSample(value: Int): Short {
        return when {
            value > 32767 -> 32767.toShort()
            value < -32768 -> (-32768).toShort()
            else -> value.toShort()
        }
    }

    private fun encodePcmFileToAacM4a(pcmFile: File, outputFile: File): Boolean {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            if (outputFile.exists()) outputFile.delete()
            val outputFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                TARGET_SAMPLE_RATE,
                TARGET_CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIdx = -1
            val encInfo = MediaCodec.BufferInfo()

            var inputDone = false
            var outputDone = false
            var totalFramesSent = 0L
            val readBuf = ByteArray(16384)

            pcmFile.inputStream().use { fis ->
                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inIdx >= 0) {
                            val inBuf = encoder.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                inBuf.clear()
                                val maxRead = min(readBuf.size, inBuf.remaining()) - (min(readBuf.size, inBuf.remaining()) % 4)
                                val read = fis.read(readBuf, 0, maxRead.coerceAtLeast(4))
                                if (read <= 0) {
                                    val ptsUs = (totalFramesSent * 1_000_000L) / TARGET_SAMPLE_RATE
                                    encoder.queueInputBuffer(
                                        inIdx, 0, 0, ptsUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    val alignedRead = read - (read % 4)
                                    if (alignedRead > 0) {
                                        inBuf.put(readBuf, 0, alignedRead)
                                        val ptsUs = (totalFramesSent * 1_000_000L) / TARGET_SAMPLE_RATE
                                        totalFramesSent += (alignedRead / 4)
                                        encoder.queueInputBuffer(inIdx, 0, alignedRead, ptsUs, 0)
                                    } else {
                                        encoder.queueInputBuffer(inIdx, 0, 0, 0L, 0)
                                    }
                                }
                            }
                        }
                    }

                    while (true) {
                        val outIdx = encoder.dequeueOutputBuffer(encInfo, TIMEOUT_US)
                        when {
                            outIdx >= 0 -> {
                                val outBuf = encoder.getOutputBuffer(outIdx)
                                val isConfig = (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                                if (!isConfig && outBuf != null && encInfo.size > 0 && muxerStarted) {
                                    outBuf.position(encInfo.offset)
                                    outBuf.limit(encInfo.offset + encInfo.size)
                                    muxer.writeSampleData(muxerTrackIdx, outBuf, encInfo)
                                }
                                encoder.releaseOutputBuffer(outIdx, false)
                                if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputDone = true
                                    break
                                }
                            }
                            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                if (!muxerStarted) {
                                    muxerTrackIdx = muxer.addTrack(encoder.outputFormat)
                                    muxer.start()
                                    muxerStarted = true
                                }
                            }
                            else -> break
                        }
                    }
                }
            }
            return muxerStarted && outputFile.exists() && outputFile.length() > 256L
        } catch (e: Throwable) {
            Log.w(TAG, "Encode AAC gagal, fallback ke WAV: ${e.message}")
            return false
        } finally {
            try { encoder?.stop() } catch (_: Throwable) {}
            try { encoder?.release() } catch (_: Throwable) {}
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
        }
    }

    private fun writePcmFileToWav(pcmFile: File, totalPcmBytes: Long, wavFile: File): Boolean {
        return try {
            if (wavFile.exists()) wavFile.delete()
            FileOutputStream(wavFile).use { fos ->
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                val totalDataLen = totalPcmBytes + 36L
                val byteRate = (TARGET_SAMPLE_RATE * TARGET_CHANNELS * 16 / 8)
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt(totalDataLen.toInt())
                header.put("WAVE".toByteArray(Charsets.US_ASCII))
                header.put("fmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16)
                header.putShort(1.toShort())
                header.putShort(TARGET_CHANNELS.toShort())
                header.putInt(TARGET_SAMPLE_RATE)
                header.putInt(byteRate)
                header.putShort((TARGET_CHANNELS * 16 / 8).toShort())
                header.putShort(16.toShort())
                header.put("data".toByteArray(Charsets.US_ASCII))
                header.putInt(totalPcmBytes.toInt())
                fos.write(header.array())

                val copyBuf = ByteArray(65536)
                pcmFile.inputStream().use { fis ->
                    while (true) {
                        val read = fis.read(copyBuf)
                        if (read <= 0) break
                        fos.write(copyBuf, 0, read)
                    }
                }
            }
            wavFile.exists() && wavFile.length() > 512L
        } catch (e: Throwable) {
            false
        }
    }
}
