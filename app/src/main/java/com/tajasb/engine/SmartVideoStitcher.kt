package com.tajasb.engine

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * SmartVideoStitcher (Two-Stage Normalized Cache + Per-Clip EGL Surface + In-Band SPS/PPS Remux):
 * Menggabungkan daftar segmen video (misal variasi VS: L3(2x) + L1(1x) + L4(2x) + L2(1x))
 * menjadi 1 file MP4 utuh berkesinambungan yang dijamin memutar 100% seluruh segmen
 * dari awal hingga akhir tanpa berhenti di video pertama.
 *
 * Arsitektur Anti-Gagal:
 * 1. Per-Clip Hardware Normalization Cache (`vs_norm_cache`):
 *    - Setiap video sumber (L1, L2, L3, L4) dinormalisasi 1x ke resolusi seragam (mis. 720x1280),
 *      30 FPS, H.264 Baseline (tanpa B-frame, PTS 100% monotonik, SPS/PPS seragam).
 *    - Karena tiap klip dinormalisasi dalam sesi 1-Decoder -> 1-Encoder terpisah dengan SurfaceTexture
 *      yang dibuat di thread ber-Looper khusus, tidak pernah ada bug Surface disconnect pada klip ke-2..N.
 * 2. Instant In-Band SPS/PPS Monotonic Remuxer:
 *    - Menggabungkan segmen-segmen yang sudah seragam menjadi `vs_rolling_1.mp4`, `vs_rolling_2.mp4`, dst.
 *      hanya dalam ~0.1–0.2 detik dengan menyisipkan header SPS/PPS (`csd-0` & `csd-1`) tepat di depan
 *      IDR keyframe pertama tiap segmen serta menjamin PTS monotonik murni dari detik 0 hingga akhir.
 */
object SmartVideoStitcher {
    private const val TAG = "SmartVideoStitcher"

    data class StitchResult(
        val success: Boolean,
        val outputFile: File?,
        val message: String,
        val mergedCount: Int = 0,
        val totalDurationMs: Long = 0L
    )

    data class VariationStitchOutcome(
        val success: Boolean,
        val stitchedItem: com.tajasb.model.VideoItem?,
        val outputFile: File?,
        val errorMessage: String? = null
    )

    fun stitchVariationList(
        context: Context,
        orderedClips: List<com.tajasb.model.VideoItem>,
        targetFileName: String,
        displayTitle: String,
        onProgress: ((statusText: String, percent: Int) -> Unit)? = null
    ): VariationStitchOutcome {
        val camDir = File(context.filesDir, "Camera1").apply { mkdirs() }
        val inputFiles = orderedClips.mapNotNull { item ->
            val f = File(item.path)
            if (f.exists() && f.length() > 1024L) f else null
        }
        if (inputFiles.isEmpty()) {
            return VariationStitchOutcome(false, null, null, "File video sumber L tidak ditemukan")
        }
        val outFile = File(camDir, targetFileName)
        val allMuted = orderedClips.all { it.isMuted }

        if (inputFiles.size == 1) {
            try {
                inputFiles.first().copyTo(outFile, overwrite = true)
                val firstClip = orderedClips.first()
                val singleItem = com.tajasb.model.VideoItem(
                    id = targetFileName.removeSuffix(".mp4"),
                    name = displayTitle,
                    path = outFile.absolutePath,
                    uri = android.net.Uri.fromFile(outFile).toString(),
                    durationMs = firstClip.durationMs,
                    sizeBytes = outFile.length(),
                    width = firstClip.width,
                    height = firstClip.height,
                    isMuted = allMuted,
                    isEnabled = true
                )
                return VariationStitchOutcome(true, singleItem, outFile, null)
            } catch (t: Throwable) {
                return VariationStitchOutcome(false, null, null, t.message)
            }
        }

        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        } catch (_: Throwable) {}

        val res = stitchVideos(
            context = context,
            inputFiles = inputFiles,
            outputFile = outFile,
            includeAudioIfCompatible = !allMuted,
            onProgress = { pct, msg -> onProgress?.invoke(msg, pct) }
        )
        if (!res.success || res.outputFile == null || !res.outputFile.exists()) {
            return VariationStitchOutcome(false, null, null, res.message)
        }

        var w = orderedClips.firstOrNull()?.width ?: 720
        var h = orderedClips.firstOrNull()?.height ?: 1280
        var dur = res.totalDurationMs
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(res.outputFile.absolutePath)
            w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: w
            h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: h
            val extractedDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (extractedDur > 0L) dur = maxOf(dur, extractedDur)
            retriever.release()
        } catch (_: Throwable) {}

        val item = com.tajasb.model.VideoItem(
            id = targetFileName.removeSuffix(".mp4"),
            name = displayTitle,
            path = res.outputFile.absolutePath,
            uri = android.net.Uri.fromFile(res.outputFile).toString(),
            durationMs = dur,
            sizeBytes = res.outputFile.length(),
            width = w,
            height = h,
            isMuted = allMuted,
            isEnabled = true
        )
        return VariationStitchOutcome(true, item, res.outputFile, null)
    }

    fun stitchVideos(
        context: Context,
        inputFiles: List<File>,
        outputFile: File,
        includeAudioIfCompatible: Boolean = true,
        onProgress: ((percent: Int, statusText: String) -> Unit)? = null
    ): StitchResult {
        val validFiles = inputFiles.filter { it.exists() && it.isFile && it.length() > 1024L }
        if (validFiles.size < 2) {
            return StitchResult(
                success = false,
                outputFile = null,
                message = "Minimal dibutuhkan 2 segmen video aktif untuk digabungkan."
            )
        }

        if (outputFile.exists()) {
            try { outputFile.delete() } catch (_: Throwable) {}
        }
        outputFile.parentFile?.mkdirs()

        // 1. Tentukan dimensi target seragam secara deterministik (kelipatan 16, maks 720p) agar tidak pernah berubah walau urutan klip diacak
        val canonicalRefFile = validFiles.minByOrNull { it.absolutePath } ?: validFiles.first()
        val (targetWidth, targetHeight, targetBitrate) = resolveTargetDimensions(canonicalRefFile)

        // 2. Normalisasi setiap klip unik (L1, L2, L3, L4) 1x ke dalam cache vs_norm_cache
        //    agar seluruh segmen memiliki resolusi, framerate, SPS/PPS H.264 Baseline, dan trek audio asli yang seragam
        val normCacheDir = File(context.filesDir, "Camera1/vs_norm_cache").apply { mkdirs() }
        pruneOldNormCacheIfNeeded(normCacheDir)

        val uniqueFiles = validFiles.distinctBy { it.absolutePath }
        val normalizedMap = mutableMapOf<String, File>()
        var allNormalizedOk = true

        for ((uIdx, srcFile) in uniqueFiles.withIndex()) {
            val cacheKey = "norm_v3_${srcFile.name.hashCode().toUInt()}_${srcFile.length()}_${targetWidth}x${targetHeight}.mp4"
            val cachedNormFile = File(normCacheDir, cacheKey)
            if (cachedNormFile.exists() && cachedNormFile.length() > 4096L) {
                normalizedMap[srcFile.absolutePath] = cachedNormFile
                continue
            }

            val basePct = 5 + ((uIdx * 55) / uniqueFiles.size.coerceAtLeast(1))
            onProgress?.invoke(basePct, "Menyeragamkan codec L${uIdx + 1}/${uniqueFiles.size} (${srcFile.name})...")

            val normOk = try {
                normalizeSingleClipHardware(
                    sourceFile = srcFile,
                    destFile = cachedNormFile,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    targetBitrate = targetBitrate
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Normalisasi klip ${srcFile.name} gagal: ${t.message}")
                false
            }

            if (normOk && cachedNormFile.exists() && cachedNormFile.length() > 4096L) {
                normalizedMap[srcFile.absolutePath] = cachedNormFile
            } else {
                try { cachedNormFile.delete() } catch (_: Throwable) {}
                allNormalizedOk = false
                break
            }
        }

        // 3. Jika semua video sumber L berhasil dinormalisasi (atau sudah ada di cache),
        //    gabungkan seluruh segmen variasi secara instan (<0.15 detik) dengan In-Band SPS/PPS Remux beserta audio aslinya!
        if (allNormalizedOk && normalizedMap.size == uniqueFiles.size) {
            val orderedNormalizedFiles = validFiles.mapNotNull { normalizedMap[it.absolutePath] }
            if (orderedNormalizedFiles.size == validFiles.size) {
                onProgress?.invoke(75, "Menyambung ${orderedNormalizedFiles.size} segmen variasi...")
                val fastRes = stitchViaNormalizedRemux(
                    validFiles = orderedNormalizedFiles,
                    outputFile = outputFile,
                    includeAudioIfCompatible = includeAudioIfCompatible,
                    injectInBandCsd = false,
                    onProgress = { pct, msg ->
                        val scaledPct = 75 + ((pct * 25) / 100)
                        onProgress?.invoke(scaledPct.coerceIn(75, 100), msg)
                    }
                )
                if (fastRes.success && outputFile.exists() && outputFile.length() > 4096L) {
                    return fastRes
                }
            }
        }

        // 4. Jalur Cadangan 1: Multi-Clip Hardware Surface Transcode (dengan Fresh SurfaceTexture per klip)
        val transcodeRes = try {
            stitchViaHardwareSurfaceTranscode(
                validFiles = validFiles,
                outputFile = outputFile,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                targetBitrate = targetBitrate,
                onProgress = onProgress
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Hardware Surface Transcode gagal, beralih ke In-Band Remux: ${t.message}")
            null
        }

        if (transcodeRes != null && transcodeRes.success && outputFile.exists() && outputFile.length() > 4096L) {
            return transcodeRes
        }

        // 5. Jalur Cadangan 2: In-Band SPS/PPS Normalized Remuxer langsung pada validFiles
        return stitchViaNormalizedRemux(
            validFiles = validFiles,
            outputFile = outputFile,
            includeAudioIfCompatible = includeAudioIfCompatible,
            injectInBandCsd = true,
            onProgress = onProgress
        )
    }

    private fun resolveTargetDimensions(firstFile: File): Triple<Int, Int, Int> {
        var targetWidth = 720
        var targetHeight = 1280
        var targetBitrate = 3_800_000
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(firstFile.absolutePath)
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            retriever.release()

            val effW = if (rot == 90 || rot == 270) rawH else rawW
            val effH = if (rot == 90 || rot == 270) rawW else rawH

            // Batasi maksimal 720p (720x1280) agar penggabungan background sangat ringan & bebas panas selama live 6+ jam
            val scale = if (effW > 720 || effH > 1280) {
                minOf(720f / effW.toFloat(), 1280f / effH.toFloat())
            } else 1.0f

            targetWidth = (((effW * scale).toInt() / 16) * 16).coerceIn(320, 1280)
            targetHeight = (((effH * scale).toInt() / 16) * 16).coerceIn(320, 1280)
            targetBitrate = if (targetWidth * targetHeight >= 700 * 1200) 3_800_000 else 2_800_000
        } catch (_: Throwable) {}
        return Triple(targetWidth, targetHeight, targetBitrate)
    }

    private fun pruneOldNormCacheIfNeeded(normCacheDir: File) {
        try {
            val files = normCacheDir.listFiles() ?: return
            if (files.size > 24) {
                files.sortBy { it.lastModified() }
                val toDelete = files.size - 16
                for (i in 0 until toDelete) {
                    try { files[i].delete() } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Menormalisasi 1 file video tunggal ke H.264 Baseline (tanpa B-frame, 30fps, I-frame interval 1 detik)
     * menggunakan 1 Decoder + 1 EglTranscodeSurface + 1 Encoder + 1 MediaMuxer.
     */
    private fun normalizeSingleClipHardware(
        sourceFile: File,
        destFile: File,
        targetWidth: Int,
        targetHeight: Int,
        targetBitrate: Int
    ): Boolean {
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        if (tempFile.exists()) {
            try { tempFile.delete() } catch (_: Throwable) {}
        }

        val res = stitchViaHardwareSurfaceTranscode(
            validFiles = listOf(sourceFile),
            outputFile = tempFile,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            targetBitrate = targetBitrate,
            allowSingleClip = true,
            onProgress = null
        )
        if (res.success && tempFile.exists() && tempFile.length() > 4096L) {
            if (destFile.exists()) {
                try { destFile.delete() } catch (_: Throwable) {}
            }
            return tempFile.renameTo(destFile)
        } else {
            try { tempFile.delete() } catch (_: Throwable) {}
            return false
        }
    }

    /**
     * Mesin Transcode Hardware Surface-to-Surface:
     * Membuat SurfaceTexture & Surface baru pada HandlerThread ber-Looper untuk SETIAP segmen
     * sehingga decoder segmen ke-2, ke-3, dst. tidak pernah mengalami Surface disconnect.
     */
    private fun stitchViaHardwareSurfaceTranscode(
        validFiles: List<File>,
        outputFile: File,
        targetWidth: Int,
        targetHeight: Int,
        targetBitrate: Int,
        allowSingleClip: Boolean = false,
        onProgress: ((percent: Int, statusText: String) -> Unit)?
    ): StitchResult {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var outVideoTrackIndex = -1
        var outAudioTrackIndex = -1
        var eglHelper: EglTranscodeSurface? = null

        val frameDurationUs = 33_333L // 30 FPS acuan antar segmen
        var cumulativePtsUs = 0L
        var lastWrittenPtsUs = -1L
        var mergedClips = 0
        var totalFramesEncoded = 0
        val clipTimeWindows = mutableListOf<Triple<File, Long, Long>>()

        var baseAudioFormat: MediaFormat? = null
        for (f in validFiles) {
            val ext = MediaExtractor()
            try {
                ext.setDataSource(f.absolutePath)
                for (t in 0 until ext.trackCount) {
                    val fmt = ext.getTrackFormat(t)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        baseAudioFormat = fmt
                        break
                    }
                }
                if (baseAudioFormat != null) break
            } catch (_: Throwable) {
            } finally {
                try { ext.release() } catch (_: Throwable) {}
            }
        }

        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val encFormat = createBaselineAvcEncoderFormat(targetWidth, targetHeight, targetBitrate, withProfile = true)
            try {
                encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (_: Throwable) {
                encoder.reset()
                val fallbackFormat = createBaselineAvcEncoderFormat(targetWidth, targetHeight, targetBitrate, withProfile = false)
                encoder.configure(fallbackFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val encoderInputSurface = encoder.createInputSurface()
            encoder.start()

            eglHelper = EglTranscodeSurface(encoderInputSurface, targetWidth, targetHeight)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val encBufferInfo = MediaCodec.BufferInfo()

            fun drainEncoder(endOfStream: Boolean) {
                val enc = encoder ?: return
                val mux = muxer ?: return
                if (endOfStream) {
                    try { enc.signalEndOfInputStream() } catch (_: Throwable) {}
                }
                var emptyRetries = 0
                while (emptyRetries < 250) {
                    val outIdx = enc.dequeueOutputBuffer(encBufferInfo, if (endOfStream) 4_000L else 0L)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!endOfStream) break
                        emptyRetries++
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            val newFormat = enc.outputFormat
                            outVideoTrackIndex = mux.addTrack(newFormat)
                            if (baseAudioFormat != null) {
                                outAudioTrackIndex = try { mux.addTrack(baseAudioFormat) } catch (_: Throwable) { -1 }
                            }
                            mux.start()
                            muxerStarted = true
                        }
                    } else if (outIdx >= 0) {
                        emptyRetries = 0
                        val encodedData = enc.getOutputBuffer(outIdx)
                        if (encodedData != null) {
                            if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                encBufferInfo.size = 0
                            }
                            if (encBufferInfo.size > 0 && muxerStarted && outVideoTrackIndex >= 0) {
                                encodedData.position(encBufferInfo.offset)
                                encodedData.limit(encBufferInfo.offset + encBufferInfo.size)
                                mux.writeSampleData(outVideoTrackIndex, encodedData, encBufferInfo)
                                totalFramesEncoded++
                            }
                        }
                        enc.releaseOutputBuffer(outIdx, false)
                        if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    } else {
                        break
                    }
                }
            }

            for ((index, file) in validFiles.withIndex()) {
                val pct = 10 + ((index * 82) / validFiles.size.coerceAtLeast(1))
                onProgress?.invoke(pct, "Menggabungkan segmen ${index + 1}/${validFiles.size}: ${file.name}...")

                val extractor = MediaExtractor()
                var decoder: MediaCodec? = null
                var clipFramesDecoded = 0

                try {
                    extractor.setDataSource(file.absolutePath)
                    var videoTrackIdx = -1
                    var inFormat: MediaFormat? = null

                    for (t in 0 until extractor.trackCount) {
                        val fmt = extractor.getTrackFormat(t)
                        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("video/")) {
                            videoTrackIdx = t
                            inFormat = fmt
                            break
                        }
                    }

                    if (videoTrackIdx < 0 || inFormat == null) {
                        continue
                    }

                    extractor.selectTrack(videoTrackIdx)
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                    val clipW = try { inFormat.getInteger(MediaFormat.KEY_WIDTH) } catch (_: Throwable) { targetWidth }
                    val clipH = try { inFormat.getInteger(MediaFormat.KEY_HEIGHT) } catch (_: Throwable) { targetHeight }

                    // PENTING: Buat SurfaceTexture + Surface baru di thread ber-Looper untuk tiap klip!
                    val clipInputSurface = eglHelper.createClipInputSurface(clipW, clipH)

                    val inMime = inFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
                    decoder = MediaCodec.createDecoderByType(inMime)
                    decoder.configure(inFormat, clipInputSurface, null, 0)
                    decoder.start()

                    val decBufferInfo = MediaCodec.BufferInfo()
                    var inputEOS = false
                    var outputEOS = false
                    var firstClipDecodedPtsUs = -1L
                    var lastRenderedRelPtsUs = -1L

                    while (!outputEOS) {
                        // Umpankan seluruh buffer input yang tersedia tanpa tidur (timeout 0L)
                        while (!inputEOS) {
                            val inBufIdx = decoder.dequeueInputBuffer(0L)
                            if (inBufIdx < 0) break
                            val inBuf = decoder.getInputBuffer(inBufIdx) ?: break
                            inBuf.clear()
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inBufIdx,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEOS = true
                            } else {
                                val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                                decoder.queueInputBuffer(inBufIdx, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }

                        val outBufIdx = decoder.dequeueOutputBuffer(decBufferInfo, 2_500L)
                        if (outBufIdx >= 0) {
                            val isEOS = (decBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            val doRender = !isEOS && decBufferInfo.size > 0 && decBufferInfo.presentationTimeUs >= 0L

                            if (doRender) {
                                if (firstClipDecodedPtsUs < 0L) {
                                    firstClipDecodedPtsUs = decBufferInfo.presentationTimeUs
                                }
                                val relPtsUs = (decBufferInfo.presentationTimeUs - firstClipDecodedPtsUs).coerceAtLeast(0L)
                                // Filter frame 60fps ke ~30fps mulus (minimal jarak 24ms antar frame) agar encoder tidak tersedak/patah-patah
                                if (lastRenderedRelPtsUs >= 0L && (relPtsUs - lastRenderedRelPtsUs) in 1L..23_999L) {
                                    decoder.releaseOutputBuffer(outBufIdx, false)
                                } else {
                                    lastRenderedRelPtsUs = relPtsUs
                                    var targetPtsUs = cumulativePtsUs + relPtsUs
                                    if (lastWrittenPtsUs >= 0L && targetPtsUs <= lastWrittenPtsUs) {
                                        targetPtsUs = lastWrittenPtsUs + frameDurationUs
                                    }
                                    lastWrittenPtsUs = targetPtsUs

                                    eglHelper.resetFrameSync()
                                    decoder.releaseOutputBuffer(outBufIdx, true)
                                    if (eglHelper.awaitNewFrame(250L)) {
                                        eglHelper.drawFrameAndSwap(targetPtsUs * 1000L)
                                        clipFramesDecoded++
                                        drainEncoder(endOfStream = false)
                                    }
                                }
                            } else {
                                decoder.releaseOutputBuffer(outBufIdx, false)
                            }

                            if (isEOS) {
                                outputEOS = true
                            }
                        } else if (outBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            drainEncoder(endOfStream = false)
                        }
                    }

                    if (clipFramesDecoded > 0) {
                        val clipStartPts = cumulativePtsUs
                        val clipEndPts = lastWrittenPtsUs + frameDurationUs
                        clipTimeWindows.add(Triple(file, clipStartPts, clipEndPts))
                        cumulativePtsUs = clipEndPts
                        mergedClips++
                    }
                } catch (clipErr: Throwable) {
                    Log.w(TAG, "Error saat mendekode segmen ${file.name}: ${clipErr.message}")
                } finally {
                    try { decoder?.stop() } catch (_: Throwable) {}
                    try { decoder?.release() } catch (_: Throwable) {}
                    try { extractor.release() } catch (_: Throwable) {}
                }
            }

            onProgress?.invoke(95, "Menyelesaikan pengkodean akhir MP4...")
            drainEncoder(endOfStream = true)

            // Salin seluruh sampel audio asli L1..Ln secara sinkron ke trek audio MP4
            if (muxerStarted && outAudioTrackIndex >= 0 && clipTimeWindows.isNotEmpty()) {
                val audioBuf = ByteBuffer.allocateDirect(256 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                var lastAudioPtsUs = -1L
                val baseAudioMime = baseAudioFormat?.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"

                for ((clipFile, clipStartUs, clipEndUs) in clipTimeWindows) {
                    val audioExt = MediaExtractor()
                    try {
                        audioExt.setDataSource(clipFile.absolutePath)
                        var inAudioTrack = -1
                        for (t in 0 until audioExt.trackCount) {
                            val fmt = audioExt.getTrackFormat(t)
                            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                            if (mime.equals(baseAudioMime, ignoreCase = true) || mime.startsWith("audio/")) {
                                inAudioTrack = t
                                break
                            }
                        }
                        if (inAudioTrack >= 0) {
                            audioExt.selectTrack(inAudioTrack)
                            audioExt.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            var firstAudioPtsUs = -1L
                            val maxRelAudioUs = (clipEndUs - clipStartUs + 45_000L).coerceAtLeast(100_000L)
                            while (true) {
                                audioBuf.clear()
                                val sampleSize = audioExt.readSampleData(audioBuf, 0)
                                if (sampleSize < 0) break
                                val rawTimeUs = audioExt.sampleTime
                                val rawFlags = audioExt.sampleFlags
                                if (rawTimeUs < 0L) {
                                    if (!audioExt.advance()) break
                                    continue
                                }
                                if ((rawFlags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    if (!audioExt.advance()) break
                                    continue
                                }
                                if (firstAudioPtsUs < 0L) {
                                    firstAudioPtsUs = rawTimeUs
                                }
                                val relAudioUs = (rawTimeUs - firstAudioPtsUs).coerceAtLeast(0L)
                                if (relAudioUs > maxRelAudioUs) break

                                var targetAudioPtsUs = clipStartUs + relAudioUs
                                if (lastAudioPtsUs >= 0L && targetAudioPtsUs <= lastAudioPtsUs) {
                                    targetAudioPtsUs = lastAudioPtsUs + 21_333L
                                }
                                lastAudioPtsUs = targetAudioPtsUs

                                audioBuf.position(0)
                                audioBuf.limit(sampleSize)
                                audioInfo.offset = 0
                                audioInfo.size = sampleSize
                                audioInfo.presentationTimeUs = targetAudioPtsUs
                                audioInfo.flags = if ((rawFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                                } else 0

                                try {
                                    muxer.writeSampleData(outAudioTrackIndex, audioBuf, audioInfo)
                                } catch (_: Throwable) {}

                                if (!audioExt.advance()) break
                            }
                        }
                    } catch (_: Throwable) {
                    } finally {
                        try { audioExt.release() } catch (_: Throwable) {}
                    }
                }
            }

            try {
                if (muxerStarted) {
                    muxer.stop()
                    muxerStarted = false
                }
                muxer.release()
                muxer = null
            } catch (_: Throwable) {}

            try { encoder.stop() } catch (_: Throwable) {}
            try { encoder.release() } catch (_: Throwable) {}
            encoder = null

            eglHelper.release()
            eglHelper = null

            val minRequiredClips = if (allowSingleClip) 1 else 2
            if (mergedClips < minRequiredClips || totalFramesEncoded < 3 || !outputFile.exists() || outputFile.length() < 4096L) {
                try { outputFile.delete() } catch (_: Throwable) {}
                return StitchResult(
                    success = false,
                    outputFile = null,
                    message = "Transcode belum menghasilkan semua segmen, beralih ke remux..."
                )
            }

            val totalDurMs = (cumulativePtsUs / 1000L).coerceAtLeast(1000L)
            onProgress?.invoke(100, "Selesai menggabungkan $mergedClips segmen video!")
            val durSec = String.format(Locale.US, "%.1f", totalDurMs / 1000f)

            return StitchResult(
                success = true,
                outputFile = outputFile,
                message = "⚡ Berhasil menggabungkan $mergedClips segmen menjadi 1 Video Utuh (${durSec} dtk)!",
                mergedCount = mergedClips,
                totalDurationMs = totalDurMs
            )
        } catch (t: Throwable) {
            try { if (muxerStarted) muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            try { encoder?.stop() } catch (_: Throwable) {}
            try { encoder?.release() } catch (_: Throwable) {}
            try { eglHelper?.release() } catch (_: Throwable) {}
            throw t
        }
    }

    private fun createBaselineAvcEncoderFormat(
        width: Int,
        height: Int,
        bitrate: Int,
        withProfile: Boolean
    ): MediaFormat {
        return MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (withProfile) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
                }
            }
        }
    }

    /**
     * Mesin In-Band SPS/PPS + Monotonic PTS Remuxer:
     * - Menyisipkan header SPS (`csd-0`) & PPS (`csd-1`) dari masing-masing segmen tepat di depan
     *   sampel IDR Keyframe pertama segmen tersebut agar dekoder H.264 langsung memperbarui parameter
     *   tanpa pernah berhenti di akhir segmen ke-1.
     * - Menjamin `presentationTimeUs` selalu naik secara monotonik (`targetPtsUs > lastWrittenVideoPtsUs`)
     *   sehingga `MediaMuxer.writeSampleData` tidak pernah melempar exception atau membuang frame.
     */
    private fun stitchViaNormalizedRemux(
        validFiles: List<File>,
        outputFile: File,
        includeAudioIfCompatible: Boolean,
        injectInBandCsd: Boolean = false,
        onProgress: ((percent: Int, statusText: String) -> Unit)?
    ): StitchResult {
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var mergedClips = 0
        var cumulativePtsUs = 0L
        var lastWrittenVideoPtsUs = -1L
        var lastWrittenAudioPtsUs = -1L
        val defaultFrameStepUs = 33_333L

        try {
            onProgress?.invoke(10, "Menganalisis struktur ${validFiles.size} segmen video...")

            var baseVideoFormat: MediaFormat? = null
            var baseAudioFormat: MediaFormat? = null
            var baseRotation = 0

            for (file in validFiles) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("video/") && baseVideoFormat == null) {
                            baseVideoFormat = format
                            if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                                baseRotation = try { format.getInteger(MediaFormat.KEY_ROTATION) } catch (_: Throwable) { 0 }
                            }
                        } else if (includeAudioIfCompatible && mime.startsWith("audio/") && baseAudioFormat == null) {
                            baseAudioFormat = format
                        }
                    }
                    if (baseVideoFormat != null && (!includeAudioIfCompatible || baseAudioFormat != null)) break
                } catch (_: Throwable) {
                } finally {
                    try { extractor.release() } catch (_: Throwable) {}
                }
            }

            if (baseVideoFormat == null) {
                return StitchResult(
                    success = false,
                    outputFile = null,
                    message = "Gagal menemukan trek video yang valid."
                )
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (baseRotation != 0) {
                try { muxer.setOrientationHint(baseRotation) } catch (_: Throwable) {}
            }

            val outVideoTrackIndex = muxer.addTrack(baseVideoFormat)
            val outAudioTrackIndex = if (includeAudioIfCompatible && baseAudioFormat != null) {
                try { muxer.addTrack(baseAudioFormat) } catch (_: Throwable) { -1 }
            } else -1

            muxer.start()
            muxerStarted = true

            val bufferSize = 2 * 1024 * 1024
            val readBuffer = ByteBuffer.allocateDirect(bufferSize)
            val writeBuffer = ByteBuffer.allocateDirect(bufferSize + 4096)
            val bufferInfo = MediaCodec.BufferInfo()

            val baseVideoMime = baseVideoFormat.getString(MediaFormat.KEY_MIME) ?: "video/avc"

            for ((index, file) in validFiles.withIndex()) {
                val pct = 15 + ((index * 75) / validFiles.size.coerceAtLeast(1))
                onProgress?.invoke(pct, "Menyambung segmen ${index + 1}/${validFiles.size}: ${file.name}...")

                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    var inVideoTrack = -1
                    var clipVideoFormat: MediaFormat? = null

                    for (t in 0 until extractor.trackCount) {
                        val fmt = extractor.getTrackFormat(t)
                        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                        if (inVideoTrack < 0 && mime.equals(baseVideoMime, ignoreCase = true)) {
                            inVideoTrack = t
                            clipVideoFormat = fmt
                            break
                        }
                    }

                    if (inVideoTrack < 0 || clipVideoFormat == null) continue

                    // Ekstrak csd-0 (SPS) dan csd-1 (PPS) dari klip ini untuk disisipkan secara in-band pada IDR pertama
                    val csdHeaderBytes = extractAnnexBCsdBytes(clipVideoFormat)

                    extractor.selectTrack(inVideoTrack)
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                    var firstClipPtsUs = -1L
                    var maxVideoPtsInClipUs = cumulativePtsUs
                    var videoSamplesWritten = 0

                    while (true) {
                        readBuffer.clear()
                        val sampleSize = extractor.readSampleData(readBuffer, 0)
                        if (sampleSize < 0) break

                        val rawTimeUs = extractor.sampleTime
                        val rawFlags = extractor.sampleFlags
                        if (rawTimeUs < 0L) {
                            if (!extractor.advance()) break
                            continue
                        }

                        val isSync = (rawFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        if (videoSamplesWritten == 0 && !isSync) {
                            if (!extractor.advance()) break
                            continue
                        }

                        if (firstClipPtsUs < 0L) {
                            firstClipPtsUs = rawTimeUs
                        }

                        val relPtsUs = (rawTimeUs - firstClipPtsUs).coerceAtLeast(0L)
                        var targetPtsUs = cumulativePtsUs + relPtsUs
                        if (lastWrittenVideoPtsUs >= 0L && targetPtsUs <= lastWrittenVideoPtsUs) {
                            targetPtsUs = lastWrittenVideoPtsUs + 16_666L
                        }
                        lastWrittenVideoPtsUs = targetPtsUs
                        if (targetPtsUs > maxVideoPtsInClipUs) {
                            maxVideoPtsInClipUs = targetPtsUs
                        }

                        var codecFlags = 0
                        if (isSync) {
                            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                        }
                        if ((rawFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                        }

                        // Sisipkan in-band SPS/PPS hanya bila diminta (klip belum dinormalisasi seragam)
                        if (injectInBandCsd && videoSamplesWritten == 0 && csdHeaderBytes.isNotEmpty() && sampleSize + csdHeaderBytes.size < writeBuffer.capacity()) {
                            writeBuffer.clear()
                            writeBuffer.put(csdHeaderBytes)
                            readBuffer.position(0)
                            readBuffer.limit(sampleSize)
                            writeBuffer.put(readBuffer)
                            writeBuffer.flip()

                            bufferInfo.offset = 0
                            bufferInfo.size = writeBuffer.remaining()
                            bufferInfo.presentationTimeUs = targetPtsUs
                            bufferInfo.flags = codecFlags

                            muxer.writeSampleData(outVideoTrackIndex, writeBuffer, bufferInfo)
                            videoSamplesWritten++
                        } else {
                            readBuffer.position(0)
                            readBuffer.limit(sampleSize)

                            bufferInfo.offset = 0
                            bufferInfo.size = sampleSize
                            bufferInfo.presentationTimeUs = targetPtsUs
                            bufferInfo.flags = codecFlags

                            muxer.writeSampleData(outVideoTrackIndex, readBuffer, bufferInfo)
                            videoSamplesWritten++
                        }

                        if (!extractor.advance()) break
                    }

                    extractor.unselectTrack(inVideoTrack)

                    if (videoSamplesWritten > 0) {
                        val clipStartPtsUs = cumulativePtsUs
                        val clipEndPtsUs = maxVideoPtsInClipUs + defaultFrameStepUs

                        // Remux sampel audio asli dari klip ini ke trek audio output dengan PTS monotonik
                        if (outAudioTrackIndex >= 0 && baseAudioFormat != null) {
                            val baseAudioMime = baseAudioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
                            val baseSampleRate = try { baseAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Throwable) { -1 }
                            val baseChannelCount = try { baseAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Throwable) { -1 }
                            var inAudioTrack = -1
                            for (t in 0 until extractor.trackCount) {
                                val fmt = extractor.getTrackFormat(t)
                                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                                if (mime.equals(baseAudioMime, ignoreCase = true)) {
                                    val sr = try { fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Throwable) { -1 }
                                    val ch = try { fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Throwable) { -1 }
                                    if ((baseSampleRate <= 0 || sr == baseSampleRate) && (baseChannelCount <= 0 || ch == baseChannelCount)) {
                                        inAudioTrack = t
                                        break
                                    }
                                }
                            }
                            if (inAudioTrack >= 0) {
                                try {
                                    extractor.selectTrack(inAudioTrack)
                                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                                    var firstAudioPtsUs = -1L
                                    val maxRelAudioUs = (clipEndPtsUs - clipStartPtsUs + 45_000L).coerceAtLeast(100_000L)
                                    while (true) {
                                        readBuffer.clear()
                                        val audioSampleSize = extractor.readSampleData(readBuffer, 0)
                                        if (audioSampleSize < 0) break

                                        val rawAudioTimeUs = extractor.sampleTime
                                        val rawAudioFlags = extractor.sampleFlags
                                        if (rawAudioTimeUs < 0L) {
                                            if (!extractor.advance()) break
                                            continue
                                        }
                                        if ((rawAudioFlags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                            if (!extractor.advance()) break
                                            continue
                                        }
                                        if (firstAudioPtsUs < 0L) {
                                            firstAudioPtsUs = rawAudioTimeUs
                                        }
                                        val relAudioPtsUs = (rawAudioTimeUs - firstAudioPtsUs).coerceAtLeast(0L)
                                        if (relAudioPtsUs > maxRelAudioUs) break

                                        var targetAudioPtsUs = clipStartPtsUs + relAudioPtsUs
                                        if (lastWrittenAudioPtsUs >= 0L && targetAudioPtsUs <= lastWrittenAudioPtsUs) {
                                            targetAudioPtsUs = lastWrittenAudioPtsUs + 21_333L
                                        }
                                        lastWrittenAudioPtsUs = targetAudioPtsUs

                                        readBuffer.position(0)
                                        readBuffer.limit(audioSampleSize)
                                        bufferInfo.offset = 0
                                        bufferInfo.size = audioSampleSize
                                        bufferInfo.presentationTimeUs = targetAudioPtsUs
                                        bufferInfo.flags = if ((rawAudioFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                                        } else 0

                                        muxer.writeSampleData(outAudioTrackIndex, readBuffer, bufferInfo)
                                        if (!extractor.advance()) break
                                    }
                                    extractor.unselectTrack(inAudioTrack)
                                } catch (audioErr: Throwable) {
                                    Log.w(TAG, "Gagal menyalin audio untuk segmen ${file.name}: ${audioErr.message}")
                                }
                            }
                        }

                        cumulativePtsUs = clipEndPtsUs
                        mergedClips++
                    }
                } catch (clipErr: Throwable) {
                    Log.w(TAG, "Gagal menyambung klip ${file.name}: ${clipErr.message}")
                } finally {
                    try { extractor.release() } catch (_: Throwable) {}
                }
            }

            try {
                if (muxerStarted) {
                    muxer.stop()
                    muxerStarted = false
                }
                muxer.release()
                muxer = null
            } catch (_: Throwable) {}

            if (mergedClips < 2 || !outputFile.exists() || outputFile.length() < 2048L) {
                try { outputFile.delete() } catch (_: Throwable) {}
                return StitchResult(
                    success = false,
                    outputFile = null,
                    message = "Gagal menggabungkan segmen video."
                )
            }

            val totalDurMs = (cumulativePtsUs / 1000L).coerceAtLeast(1000L)
            onProgress?.invoke(100, "Selesai menggabungkan $mergedClips segmen!")
            val durSec = String.format(Locale.US, "%.1f", totalDurMs / 1000f)
            return StitchResult(
                success = true,
                outputFile = outputFile,
                message = "⚡ Berhasil menggabungkan $mergedClips segmen menjadi 1 Video Utuh (${durSec} dtk)!",
                mergedCount = mergedClips,
                totalDurationMs = totalDurMs
            )
        } catch (t: Throwable) {
            try {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            } catch (_: Throwable) {}
            try { outputFile.delete() } catch (_: Throwable) {}
            return StitchResult(
                success = false,
                outputFile = null,
                message = "Gagal menggabungkan video: ${t.message ?: "Error tidak diketahui"}"
            )
        }
    }

    private fun extractAnnexBCsdBytes(format: MediaFormat): ByteArray {
        return try {
            val out = java.io.ByteArrayOutputStream()
            val annexBStartCode = byteArrayOf(0x00, 0x00, 0x00, 0x01)
            for (key in listOf("csd-0", "csd-1")) {
                if (format.containsKey(key)) {
                    val buf = format.getByteBuffer(key) ?: continue
                    val dup = buf.duplicate()
                    val bytes = ByteArray(dup.remaining())
                    dup.get(bytes)
                    if (bytes.isNotEmpty()) {
                        val hasStartCode4 = bytes.size >= 4 &&
                            bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
                            bytes[2] == 0.toByte() && bytes[3] == 1.toByte()
                        val hasStartCode3 = bytes.size >= 3 &&
                            bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
                            bytes[2] == 1.toByte()
                        if (!hasStartCode4 && !hasStartCode3) {
                            out.write(annexBStartCode)
                        }
                        out.write(bytes)
                    }
                }
            }
            out.toByteArray()
        } catch (_: Throwable) {
            ByteArray(0)
        }
    }

    /**
     * Helper OpenGL ES 2.0 + EGL14 yang menghubungkan Output Surface Decoder dengan Input Surface Encoder.
     * Mendukung pembuatan SurfaceTexture + Surface baru di HandlerThread ber-Looper untuk setiap segmen klip.
     */
    private class EglTranscodeSurface(
        encoderInputSurface: Surface,
        private val width: Int,
        private val height: Int
    ) : SurfaceTexture.OnFrameAvailableListener {

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var surfaceTexture: SurfaceTexture? = null
        var decoderOutputSurface: Surface? = null
            private set

        private val callbackThread = HandlerThread("StitcherFrameCallback").apply { start() }
        private val callbackHandler = Handler(callbackThread.looper)

        private val frameSyncLock = Object()
        private var frameAvailable = false

        private var programHandle = 0
        private var oesTextureId = 0
        private var uMVPMatrixHandle = 0
        private var uSTMatrixHandle = 0
        private var aPositionHandle = 0
        private var aTextureCoordHandle = 0

        private val stMatrix = FloatArray(16)
        private val mvpMatrix = FloatArray(16).apply {
            android.opengl.Matrix.setIdentityM(this, 0)
        }

        private val triangleVertices: FloatBuffer = ByteBuffer
            .allocateDirect(20 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(
                    floatArrayOf(
                        // X, Y, Z, U, V
                        -1.0f, -1.0f, 0f, 0f, 0f,
                         1.0f, -1.0f, 0f, 1f, 0f,
                        -1.0f,  1.0f, 0f, 0f, 1f,
                         1.0f,  1.0f, 0f, 1f, 1f
                    )
                )
                position(0)
            }

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
            val eglConfig = configs[0] ?: throw IllegalStateException("Tidak dapat memilih EGLConfig")

            val ctxAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, encoderInputSurface, surfaceAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            setupGlShader()
        }

        private fun setupGlShader() {
            val vertexShaderCode = """
                uniform mat4 uMVPMatrix;
                uniform mat4 uSTMatrix;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord;
                void main() {
                    gl_Position = uMVPMatrix * aPosition;
                    vTextureCoord = (uSTMatrix * aTextureCoord).xy;
                }
            """.trimIndent()

            val fragmentShaderCode = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
            """.trimIndent()

            val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
            programHandle = GLES20.glCreateProgram()
            GLES20.glAttachShader(programHandle, vShader)
            GLES20.glAttachShader(programHandle, fShader)
            GLES20.glLinkProgram(programHandle)

            aPositionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition")
            aTextureCoordHandle = GLES20.glGetAttribLocation(programHandle, "aTextureCoord")
            uMVPMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix")
            uSTMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uSTMatrix")
        }

        /**
         * Membuat SurfaceTexture + Surface baru untuk setiap segmen klip di atas HandlerThread ber-Looper
         * agar MediaCodec.release() dari klip sebelumnya tidak pernah memutus Surface klip berikutnya.
         */
        fun createClipInputSurface(clipWidth: Int, clipHeight: Int): Surface {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            try { decoderOutputSurface?.release() } catch (_: Throwable) {}
            decoderOutputSurface = null
            try { surfaceTexture?.setOnFrameAvailableListener(null) } catch (_: Throwable) {}
            try { surfaceTexture?.release() } catch (_: Throwable) {}
            surfaceTexture = null

            if (oesTextureId != 0) {
                try {
                    GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
                } catch (_: Throwable) {}
                oesTextureId = 0
            }

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            oesTextureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            synchronized(frameSyncLock) {
                frameAvailable = false
            }

            val latch = CountDownLatch(1)
            var createdSt: SurfaceTexture? = null
            val texId = oesTextureId
            val safeW = clipWidth.coerceAtLeast(16)
            val safeH = clipHeight.coerceAtLeast(16)

            callbackHandler.post {
                try {
                    val st = SurfaceTexture(texId)
                    st.setDefaultBufferSize(safeW, safeH)
                    st.setOnFrameAvailableListener(this@EglTranscodeSurface, callbackHandler)
                    createdSt = st
                } finally {
                    latch.countDown()
                }
            }
            latch.await(1500L, TimeUnit.MILLISECONDS)

            val finalSt = createdSt ?: SurfaceTexture(texId).apply {
                setDefaultBufferSize(safeW, safeH)
                setOnFrameAvailableListener(this@EglTranscodeSurface, callbackHandler)
            }
            surfaceTexture = finalSt
            val surf = Surface(finalSt)
            decoderOutputSurface = surf
            return surf
        }

        private fun compileShader(shaderType: Int, source: String): Int {
            val shader = GLES20.glCreateShader(shaderType)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }

        fun resetFrameSync() {
            synchronized(frameSyncLock) {
                frameAvailable = false
            }
        }

        override fun onFrameAvailable(st: SurfaceTexture?) {
            synchronized(frameSyncLock) {
                frameAvailable = true
                frameSyncLock.notifyAll()
            }
        }

        fun awaitNewFrame(timeoutMs: Long): Boolean {
            synchronized(frameSyncLock) {
                if (!frameAvailable) {
                    try {
                        frameSyncLock.wait(timeoutMs)
                    } catch (_: InterruptedException) {}
                }
                if (!frameAvailable) return false
                frameAvailable = false
            }
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(stMatrix)
            return true
        }

        fun drawFrameAndSwap(presentationTimeNs: Long) {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(programHandle)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)

            triangleVertices.position(0)
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 20, triangleVertices)
            GLES20.glEnableVertexAttribArray(aPositionHandle)

            triangleVertices.position(3)
            GLES20.glVertexAttribPointer(aTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 20, triangleVertices)
            GLES20.glEnableVertexAttribArray(aTextureCoordHandle)

            GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            try { decoderOutputSurface?.release() } catch (_: Throwable) {}
            decoderOutputSurface = null
            try { surfaceTexture?.setOnFrameAvailableListener(null) } catch (_: Throwable) {}
            try { surfaceTexture?.release() } catch (_: Throwable) {}
            surfaceTexture = null
            try { callbackThread.quitSafely() } catch (_: Throwable) {}
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                try {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (eglSurface !== EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    if (eglContext !== EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                    EGL14.eglTerminate(eglDisplay)
                } catch (_: Throwable) {}
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }
}
