package com.tajasb.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import com.tajasb.model.AudioItem
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Engine Penggabung Audio Fisik (SV4 Audio — Mode VS Rolling JIT):
 *
 * Merakit bahan audio sumber (LA1, LA2, LA3...LAn) sesuai resep susunan & jumlah putaran
 * (misal: LA2(2x) + LA1(1x) + LA3(2x)) menjadi 1 file audio fisik utuh (.m4a atau .wav fallback)
 * dengan timestamp yang mengalir mulus tanpa jeda dan siap dihapus otomatis setelah selesai diputar.
 */
object SmartAudioStitcher {
    private const val TAG = "SmartAudioStitcher"

    private const val TARGET_SAMPLE_RATE = 44100
    private const val TARGET_CHANNELS = 2
    private const val TARGET_BITRATE = 128_000
    private const val TIMEOUT_US = 10_000L

    data class AudioSegmentSpec(
        val audio: AudioItem,
        val label: String,
        val loops: Int
    )

    data class StitchResult(
        val outputFile: File,
        val durationMs: Long,
        val segmentCount: Int,
        val formulaText: String
    )

    /**
     * Merakit variasi susunan audio (VSA) ke dalam 1 file fisik.
     */
    fun stitchAudioVariation(
        context: Context,
        vsNumber: Int,
        segments: List<AudioSegmentSpec>,
        onProgress: ((Int, String) -> Unit)? = null
    ): StitchResult? {
        if (segments.isEmpty()) return null

        val outDir = getRollingVsAudioDir(context)
        if (!outDir.exists()) outDir.mkdirs()

        val normCacheDir = File(outDir, "norm_cache")
        if (!normCacheDir.exists()) normCacheDir.mkdirs()

        val formulaText = segments.joinToString(" + ") { "${it.label}(${it.loops}x)" }
        onProgress?.invoke(5, "Menyiapkan bahan audio VSA$vsNumber...")

        // 1. Normalisasi setiap file sumber unik ke AAC .m4a (44.1kHz, 2ch)
        val uniqueAudios = segments.map { it.audio }.distinctBy { it.path }
        val normalizedMap = mutableMapOf<String, File>()

        for ((idx, item) in uniqueAudios.withIndex()) {
            val srcFile = File(item.path)
            if (!srcFile.exists() || srcFile.length() < 128L) continue

            val progressBase = 8 + ((idx * 42) / max(1, uniqueAudios.size))
            onProgress?.invoke(progressBase, "Menstandarkan bahan ${idx + 1}/${uniqueAudios.size}...")

            val normFile = ensureNormalizedAudio(srcFile, normCacheDir)
            if (normFile != null && normFile.exists() && normFile.length() > 128L) {
                normalizedMap[item.path] = normFile
            }
        }

        if (normalizedMap.isEmpty()) {
            Log.w(TAG, "Tidak ada file audio sumber yang berhasil dinormalisasi untuk VSA$vsNumber")
            return null
        }

        // Susun urutan file segmen sesuai loop
        val expandedFiles = mutableListOf<File>()
        for (seg in segments) {
            val norm = normalizedMap[seg.audio.path] ?: continue
            val loopCount = seg.loops.coerceIn(1, 10)
            repeat(loopCount) {
                expandedFiles.add(norm)
            }
        }

        if (expandedFiles.isEmpty()) return null

        val targetM4a = File(outDir, "vs_audio_rolling_${vsNumber}.m4a")
        val tempM4a = File(outDir, "vs_audio_rolling_${vsNumber}_tmp.m4a")
        if (tempM4a.exists()) tempM4a.delete()

        onProgress?.invoke(55, "Menggabungkan ${expandedFiles.size} segmen ke VSA$vsNumber...")

        val muxSuccess = concatenateNormalizedAacFiles(expandedFiles, tempM4a) { segIdx, totalSegs ->
            val pct = 55 + ((segIdx * 40) / max(1, totalSegs))
            onProgress?.invoke(pct.coerceIn(55, 95), "Merakit VSA$vsNumber ($segIdx/$totalSegs)...")
        }

        if (muxSuccess && tempM4a.exists() && tempM4a.length() > 256L) {
            if (targetM4a.exists()) targetM4a.delete()
            val renamed = tempM4a.renameTo(targetM4a)
            val finalFile = if (renamed) targetM4a else tempM4a
            val totalDurationMs = probeDurationMs(finalFile).coerceAtLeast(1000L)
            onProgress?.invoke(100, "VSA$vsNumber Siap Play")
            return StitchResult(
                outputFile = finalFile,
                durationMs = totalDurationMs,
                segmentCount = expandedFiles.size,
                formulaText = formulaText
            )
        } else {
            if (tempM4a.exists()) tempM4a.delete()
            // Fallback ke penggabungan WAV PCM jika perangkat gagal muxing AAC
            val targetWav = File(outDir, "vs_audio_rolling_${vsNumber}.wav")
            val wavSuccess = fallbackStitchToWav(segments, targetWav) { pct ->
                onProgress?.invoke(pct, "Merakit VSA$vsNumber (Mode Kompatibilitas)...")
            }
            if (wavSuccess && targetWav.exists() && targetWav.length() > 512L) {
                val totalDurationMs = probeDurationMs(targetWav).coerceAtLeast(1000L)
                onProgress?.invoke(100, "VSA$vsNumber Siap Play")
                return StitchResult(
                    outputFile = targetWav,
                    durationMs = totalDurationMs,
                    segmentCount = expandedFiles.size,
                    formulaText = formulaText
                )
            }
        }

        return null
    }

    fun getRollingVsAudioDir(context: Context): File {
        val base = File(context.filesDir, "Camera1/vs_audio_rolling")
        if (!base.exists()) base.mkdirs()
        return base
    }

    fun cleanupOldRollingVsAudioFiles(context: Context, keepVsNumbers: Set<Int>): Int {
        var deletedCount = 0
        try {
            val dir = getRollingVsAudioDir(context)
            val files = dir.listFiles() ?: return 0
            for (f in files) {
                if (f.isDirectory) continue
                val name = f.name
                if (!name.startsWith("vs_audio_rolling_")) continue
                if (name.endsWith("_tmp.m4a") || name.endsWith("_tmp.wav")) {
                    // Jangan hapus file temp yang baru dibuat < 45 detik lalu
                    if (System.currentTimeMillis() - f.lastModified() > 45_000L) {
                        if (f.delete()) deletedCount++
                    }
                    continue
                }
                val numStr = name.removePrefix("vs_audio_rolling_")
                    .substringBefore(".")
                    .substringBefore("_")
                val vsNum = numStr.toIntOrNull()
                if (vsNum != null && !keepVsNumbers.contains(vsNum)) {
                    if (f.delete()) {
                        deletedCount++
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membersihkan file VSA lama: ${e.message}")
        }
        return deletedCount
    }

    fun clearAllRollingVsAudioFiles(context: Context): Int {
        return cleanupOldRollingVsAudioFiles(context, emptySet())
    }

    /**
     * Memastikan file audio sumber telah dinormalisasi menjadi AAC 44100Hz 2ch di dalam container .m4a
     * agar proses penggabungan (concatenation) berjalan instan & bebas glitch.
     */
    private fun ensureNormalizedAudio(sourceFile: File, cacheDir: File): File? {
        val cacheKey = "${sourceFile.nameWithoutExtension}_${sourceFile.length()}_${sourceFile.lastModified()}"
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val cachedFile = File(cacheDir, "norm_${cacheKey}.m4a")
        if (cachedFile.exists() && cachedFile.length() > 256L) {
            return cachedFile
        }

        val tempFile = File(cacheDir, "norm_${cacheKey}_tmp.m4a")
        if (tempFile.exists()) tempFile.delete()

        val transcoded = transcodeToStandardAacM4a(sourceFile, tempFile)
        if (transcoded && tempFile.exists() && tempFile.length() > 256L) {
            if (cachedFile.exists()) cachedFile.delete()
            return if (tempFile.renameTo(cachedFile)) cachedFile else tempFile
        } else {
            if (tempFile.exists()) tempFile.delete()
        }
        return null
    }

    /**
     * Decode file audio sumber (MP3/WAV/M4A/OGG/AAC) ke PCM lalu encode ke AAC 44.1kHz Stereo .m4a.
     */
    private fun transcodeToStandardAacM4a(inputFile: File, outputFile: File): Boolean {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) return false
            extractor.selectTrack(audioTrackIndex)

            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val srcSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else TARGET_SAMPLE_RATE
            val srcChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else TARGET_CHANNELS

            // Jika sudah AAC 44100Hz 2ch, bisa langsung remux cepat
            if (inputMime == MediaFormat.MIMETYPE_AUDIO_AAC &&
                srcSampleRate == TARGET_SAMPLE_RATE &&
                srcChannels == TARGET_CHANNELS
            ) {
                if (fastRemuxSingleAacTrack(extractor, inputFormat, outputFile)) {
                    return true
                }
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

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
            var muxerTrackIndex = -1

            val decBufferInfo = MediaCodec.BufferInfo()
            val encBufferInfo = MediaCodec.BufferInfo()

            var extractorDone = false
            var decoderDone = false
            var encoderDone = false

            var decodedSampleRate = srcSampleRate.coerceAtLeast(8000)
            var decodedChannels = srcChannels.coerceIn(1, 6)
            var totalFramesSentToEncoder = 0L

            val pendingPcmQueue = ArrayDeque<ByteArray>()

            while (!encoderDone) {
                // 1. Feed extractor -> decoder
                if (!extractorDone) {
                    val inBufIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inBufIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inBufIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inBufIdx, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                extractorDone = true
                            } else {
                                val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                                decoder.queueInputBuffer(inBufIdx, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // 2. Drain decoder -> konversi PCM ke 44.1kHz Stereo -> masukkan ke antrean
                while (!decoderDone && pendingPcmQueue.size < 16) {
                    val outBufIdx = decoder.dequeueOutputBuffer(decBufferInfo, TIMEOUT_US)
                    when {
                        outBufIdx >= 0 -> {
                            val outBuf = decoder.getOutputBuffer(outBufIdx)
                            if (outBuf != null && decBufferInfo.size > 0) {
                                outBuf.position(decBufferInfo.offset)
                                outBuf.limit(decBufferInfo.offset + decBufferInfo.size)
                                val rawPcm = ByteArray(decBufferInfo.size)
                                outBuf.get(rawPcm)

                                val convertedPcm = convertPcm16ToTargetStereo44k(
                                    rawPcm,
                                    decodedSampleRate,
                                    decodedChannels
                                )
                                if (convertedPcm.isNotEmpty()) {
                                    pendingPcmQueue.addLast(convertedPcm)
                                }
                            }
                            decoder.releaseOutputBuffer(outBufIdx, false)
                            if ((decBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                decoderDone = true
                                break
                            }
                        }
                        outBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFmt = decoder.outputFormat
                            if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                decodedSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                decodedChannels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                        else -> break
                    }
                }

                // 3. Feed pendingPcmQueue -> encoder
                while (pendingPcmQueue.isNotEmpty()) {
                    val encInIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (encInIdx < 0) break
                    val encInBuf = encoder.getInputBuffer(encInIdx) ?: break
                    encInBuf.clear()

                    val chunk = pendingPcmQueue.removeFirst()
                    val copyLen = min(chunk.size, encInBuf.remaining())
                    encInBuf.put(chunk, 0, copyLen)

                    if (copyLen < chunk.size) {
                        val leftover = chunk.copyOfRange(copyLen, chunk.size)
                        pendingPcmQueue.addFirst(leftover)
                    }

                    // 1 frame stereo 16-bit = 4 byte (2 byte kiri + 2 byte kanan)
                    val framesInChunk = copyLen / 4
                    val ptsUs = (totalFramesSentToEncoder * 1_000_000L) / TARGET_SAMPLE_RATE
                    totalFramesSentToEncoder += framesInChunk

                    encoder.queueInputBuffer(encInIdx, 0, copyLen, ptsUs, 0)
                }

                if (decoderDone && pendingPcmQueue.isEmpty()) {
                    val encInIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (encInIdx >= 0) {
                        val ptsUs = (totalFramesSentToEncoder * 1_000_000L) / TARGET_SAMPLE_RATE
                        encoder.queueInputBuffer(
                            encInIdx, 0, 0, ptsUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                    }
                }

                // 4. Drain encoder -> muxer
                while (true) {
                    val encOutIdx = encoder.dequeueOutputBuffer(encBufferInfo, TIMEOUT_US)
                    when {
                        encOutIdx >= 0 -> {
                            val encOutBuf = encoder.getOutputBuffer(encOutIdx)
                            val isCodecConfig = (encBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (!isCodecConfig && encOutBuf != null && encBufferInfo.size > 0 && muxerStarted) {
                                encOutBuf.position(encBufferInfo.offset)
                                encOutBuf.limit(encBufferInfo.offset + encBufferInfo.size)
                                muxer.writeSampleData(muxerTrackIndex, encOutBuf, encBufferInfo)
                            }
                            encoder.releaseOutputBuffer(encOutIdx, false)
                            if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encoderDone = true
                                break
                            }
                        }
                        encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        else -> break
                    }
                }
            }

            return muxerStarted && outputFile.exists() && outputFile.length() > 256L
        } catch (e: Exception) {
            Log.w(TAG, "Gagal transcode audio ${inputFile.name}: ${e.message}")
            return false
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    private fun fastRemuxSingleAacTrack(
        extractor: MediaExtractor,
        trackFormat: MediaFormat,
        outputFile: File
    ): Boolean {
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val dstTrack = muxer.addTrack(trackFormat)
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(131072)
            val info = MediaCodec.BufferInfo()
            var lastPtsUs = 0L

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val pts = extractor.sampleTime
                val safePts = if (pts >= lastPtsUs) pts else lastPtsUs + 23219L
                lastPtsUs = safePts

                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = safePts
                info.flags = extractor.sampleFlags

                muxer.writeSampleData(dstTrack, buffer, info)
                extractor.advance()
            }
            return outputFile.exists() && outputFile.length() > 256L
        } catch (e: Exception) {
            return false
        } finally {
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Menggabungkan deretan file AAC .m4a yang sudah terstandarisasi (44.1kHz Stereo)
     * menjadi 1 file .m4a utuh dengan timestamp monotonik tanpa re-encoding ulang.
     */
    private fun concatenateNormalizedAacFiles(
        segmentFiles: List<File>,
        outputFile: File,
        onSegmentProgress: ((Int, Int) -> Unit)? = null
    ): Boolean {
        if (segmentFiles.isEmpty()) return false
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var dstTrackIndex = -1

        try {
            val buffer = ByteBuffer.allocate(131072)
            val bufferInfo = MediaCodec.BufferInfo()
            var cumulativeOffsetUs = 0L
            val frameDurationUs = (1024L * 1_000_000L) / TARGET_SAMPLE_RATE // ~23,219 us per AAC frame

            for ((index, segFile) in segmentFiles.withIndex()) {
                onSegmentProgress?.invoke(index + 1, segmentFiles.size)
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(segFile.absolutePath)
                    var audioTrack = -1
                    var format: MediaFormat? = null
                    for (t in 0 until extractor.trackCount) {
                        val fmt = extractor.getTrackFormat(t)
                        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            audioTrack = t
                            format = fmt
                            break
                        }
                    }

                    if (audioTrack < 0 || format == null) continue
                    extractor.selectTrack(audioTrack)

                    if (!muxerStarted) {
                        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        dstTrackIndex = muxer.addTrack(format)
                        muxer.start()
                        muxerStarted = true
                    }

                    var firstSamplePtsInSegment = -1L
                    var lastWrittenPtsInSegment = cumulativeOffsetUs

                    while (true) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        val rawPts = extractor.sampleTime.coerceAtLeast(0L)
                        if (firstSamplePtsInSegment < 0L) {
                            firstSamplePtsInSegment = rawPts
                        }
                        val relativePts = (rawPts - firstSamplePtsInSegment).coerceAtLeast(0L)
                        var targetPts = cumulativeOffsetUs + relativePts
                        if (targetPts <= lastWrittenPtsInSegment && lastWrittenPtsInSegment > 0L) {
                            targetPts = lastWrittenPtsInSegment + frameDurationUs
                        }
                        lastWrittenPtsInSegment = targetPts

                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = targetPts
                        bufferInfo.flags = extractor.sampleFlags

                        muxer?.writeSampleData(dstTrackIndex, buffer, bufferInfo)
                        extractor.advance()
                    }

                    cumulativeOffsetUs = lastWrittenPtsInSegment + frameDurationUs
                } finally {
                    try { extractor.release() } catch (_: Exception) {}
                }
            }

            return muxerStarted && outputFile.exists() && outputFile.length() > 256L
        } catch (e: Exception) {
            Log.e(TAG, "Gagal concatenate file AAC: ${e.message}", e)
            return false
        } finally {
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Konversi array byte PCM 16-bit Little Endian ke 44100Hz Stereo (2 channel)
     * agar semua bahan audio punya format identik sebelum digabung.
     */
    private fun convertPcm16ToTargetStereo44k(
        rawBytes: ByteArray,
        srcSampleRate: Int,
        srcChannels: Int
    ): ByteArray {
        if (rawBytes.size < 2) return ByteArray(0)
        val totalShorts = rawBytes.size / 2
        val safeChannels = srcChannels.coerceAtLeast(1)
        val srcFrames = totalShorts / safeChannels
        if (srcFrames <= 0) return ByteArray(0)

        val shortBuf = ByteBuffer.wrap(rawBytes, 0, srcFrames * safeChannels * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        if (srcSampleRate == TARGET_SAMPLE_RATE && safeChannels == TARGET_CHANNELS) {
            return rawBytes.copyOf(srcFrames * 4)
        }

        val dstFrames = max(1, ((srcFrames.toLong() * TARGET_SAMPLE_RATE) / srcSampleRate.coerceAtLeast(1)).toInt())
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

    /**
     * Fallback penggabungan ke WAV PCM 16-bit jika hardware AAC encoder bermasalah.
     */
    private fun fallbackStitchToWav(
        segments: List<AudioSegmentSpec>,
        outputWavFile: File,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean {
        var raf: RandomAccessFile? = null
        try {
            if (outputWavFile.exists()) outputWavFile.delete()
            FileOutputStream(outputWavFile).use { fos ->
                // Header placeholder 44 bytes
                fos.write(ByteArray(44))
            }
            raf = RandomAccessFile(outputWavFile, "rw")
            raf.seek(44L)

            var totalPcmBytes = 0L
            for ((idx, seg) in segments.withIndex()) {
                val pct = 60 + ((idx * 35) / max(1, segments.size))
                onProgress?.invoke(pct)
                val pcmFile = decodeAudioToTempPcm(seg.audio.path, outputWavFile.parentFile) ?: continue
                try {
                    val loops = seg.loops.coerceIn(1, 10)
                    val copyBuf = ByteArray(65536)
                    repeat(loops) {
                        pcmFile.inputStream().use { fis ->
                            while (true) {
                                val read = fis.read(copyBuf)
                                if (read <= 0) break
                                raf.write(copyBuf, 0, read)
                                totalPcmBytes += read
                            }
                        }
                    }
                } finally {
                    pcmFile.delete()
                }
            }

            if (totalPcmBytes <= 0L) return false
            writeWavHeader(raf, totalPcmBytes, TARGET_SAMPLE_RATE, TARGET_CHANNELS, 16)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Fallback WAV gagal: ${e.message}")
            return false
        } finally {
            try { raf?.close() } catch (_: Exception) {}
        }
    }

    private fun decodeAudioToTempPcm(filePath: String, dir: File?): File? {
        val tempPcm = File(dir, "tmp_pcm_${System.nanoTime()}.pcm")
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(filePath)
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
            if (trackIdx < 0 || fmt == null) return null
            extractor.selectTrack(trackIdx)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            var sr = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else TARGET_SAMPLE_RATE
            var ch = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else TARGET_CHANNELS

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(fmt, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false

            FileOutputStream(tempPcm).use { fos ->
                while (!outDone) {
                    if (!inDone) {
                        val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inIdx >= 0) {
                            val buf = decoder.getInputBuffer(inIdx)
                            if (buf != null) {
                                val sz = extractor.readSampleData(buf, 0)
                                if (sz < 0) {
                                    decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inDone = true
                                } else {
                                    decoder.queueInputBuffer(inIdx, 0, sz, extractor.sampleTime.coerceAtLeast(0L), 0)
                                    extractor.advance()
                                }
                            }
                        }
                    }
                    val outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        outIdx >= 0 -> {
                            val outBuf = decoder.getOutputBuffer(outIdx)
                            if (outBuf != null && info.size > 0) {
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                val raw = ByteArray(info.size)
                                outBuf.get(raw)
                                val conv = convertPcm16ToTargetStereo44k(raw, sr, ch)
                                if (conv.isNotEmpty()) fos.write(conv)
                            }
                            decoder.releaseOutputBuffer(outIdx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outDone = true
                            }
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val nf = decoder.outputFormat
                            if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sr = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }
            return if (tempPcm.exists() && tempPcm.length() > 0L) tempPcm else null
        } catch (_: Exception) {
            tempPcm.delete()
            return null
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    private fun writeWavHeader(
        raf: RandomAccessFile,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36L
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen.toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort()) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate.toInt())
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(totalAudioLen.toInt())

        raf.seek(0L)
        raf.write(header.array())
    }

    fun probeDurationMs(file: File): Long {
        if (!file.exists()) return 0L
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val dur = durStr?.toLongOrNull() ?: 0L
            if (dur > 0L) return dur
        } catch (_: Exception) {
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
        return 0L
    }
}
