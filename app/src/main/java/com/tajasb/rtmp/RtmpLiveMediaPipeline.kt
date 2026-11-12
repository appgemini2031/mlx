package com.tajasb.rtmp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.tajasb.audio.StandaloneMusicPlayer
import com.tajasb.data.VcamRepository
import com.tajasb.model.AudioItem
import com.tajasb.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance real-time media pipeline for RTMP streaming:
 * 1. Live Video Transcoder: Forces 2.0-second keyframe interval (GOP = 2s, 0.5 fps),
 *    aligns dimensions to multiples of 16, preserving source aspect ratio without green artifacts.
 * 2. Live Audio Mixer & AAC Encoder: Streams audio from MP3/Quran (even when video is muted!)
 *    and mixes video audio + MP3 audio together when dual audio is enabled.
 */
class RtmpLiveMediaPipeline(
    private val context: Context,
    private val repo: VcamRepository,
    private val client: RtmpProtocolClient,
    private val config: RtmpConfig,
    private val onLog: (String) -> Unit
) {
    companion object {
        private const val TAG = "RtmpLiveMediaPipeline"
        private const val TIMEOUT_US = 8_000L
        private const val TARGET_SAMPLE_RATE = 44100
        private const val TARGET_CHANNELS = 2
        private const val BYTES_PER_SAMPLE = 2
        private const val SAMPLES_PER_FRAME = 1024
        private const val PCM_FRAME_BYTES = SAMPLES_PER_FRAME * TARGET_CHANNELS * BYTES_PER_SAMPLE // 4096 bytes
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var audioJob: Job? = null
    @Volatile private var isRunning = false

    @Volatile var activeAudioSourceDesc = "Otomatis"
        private set

    @Volatile private var currentActiveVideo: VideoItem? = null

    fun setActiveVideo(videoItem: VideoItem) {
        currentActiveVideo = videoItem
    }

    /**
     * Starts the audio streaming & mixing pipeline concurrently with video streaming.
     */
    fun startAudioPipeline(baseTimestampMs: Long) {
        if (isRunning) return
        isRunning = true

        audioJob = scope.launch {
            runAudioLoop(baseTimestampMs)
        }
    }

    fun stop() {
        isRunning = false
        audioJob?.cancel()
        audioJob = null
    }

    /**
     * Decodes and encodes audio from active sources (MP3 Quran player and/or Video audio).
     * Enforces real-time wall-clock pacing (~23.2ms per AAC frame) to prevent RTMP buffer flooding or mute by server.
     */
    private suspend fun runAudioLoop(baseTimestampMs: Long) {
        val musicPlayer = StandaloneMusicPlayer.getInstance(context)
        var aacEncoder: MediaCodec? = null
        var aacEncoderStarted = false

        try {
            // Setup AAC-LC Encoder (44100Hz, Stereo, 128 kbps)
            val encFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                TARGET_SAMPLE_RATE,
                TARGET_CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitratePreset.audioBitrateKbps * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            try {
                aacEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                    configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
                aacEncoderStarted = true
            } catch (t: Throwable) {
                Log.e(TAG, "Gagal inisialisasi AAC encoder hardware: ${t.message}", t)
                onLog("⚠️ Gagal inisialisasi AAC encoder hardware: ${t.message}")
            }

            // Send initial sequence header fallback
            val defaultAsc = byteArrayOf(0x12.toByte(), 0x10.toByte())
            client.sendAacSequenceHeader(defaultAsc, baseTimestampMs)

            var audioSamplePtsMs = 0.0
            var startWallClockMs = System.currentTimeMillis()
            val encBufferInfo = MediaCodec.BufferInfo()

            val pcmByteBuffer = ByteBuffer.allocateDirect(PCM_FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            val mixShorts = ShortArray(SAMPLES_PER_FRAME * TARGET_CHANNELS)
            val mp3Shorts = ShortArray(SAMPLES_PER_FRAME * TARGET_CHANNELS)
            val videoShorts = ShortArray(SAMPLES_PER_FRAME * TARGET_CHANNELS)
            val layer2Shorts = ShortArray(SAMPLES_PER_FRAME * TARGET_CHANNELS)
            val layer3Shorts = ShortArray(SAMPLES_PER_FRAME * TARGET_CHANNELS)

            var lastMp3Source: String? = null
            var mp3Decoder: AudioTrackDecoder? = null

            var lastVideoSource: String? = null
            var videoDecoder: AudioTrackDecoder? = null

            var lastLayer2Source: String? = null
            var layer2Decoder: AudioTrackDecoder? = null

            var lastLayer3Source: String? = null
            var layer3Decoder: AudioTrackDecoder? = null

            var audioPauseStartWallMs = 0L

            while (isRunning && scope.isActive) {
                // If video is paused, halt audio stream synchronously so sound does not play alone on FB
                val isVideoPaused = repo.config.value.isVideoPaused
                if (isVideoPaused) {
                    if (audioPauseStartWallMs == 0L) {
                        audioPauseStartWallMs = System.currentTimeMillis()
                    }
                    delay(80L)
                    continue
                } else if (audioPauseStartWallMs > 0L) {
                    val pausedDuration = System.currentTimeMillis() - audioPauseStartWallMs
                    startWallClockMs += pausedDuration
                    audioPauseStartWallMs = 0L
                }

                // 1. Resolve Tipe 1 (MP3 / Musik / Quran)
                val isMp3Muted = musicPlayer.isMuted.value || repo.config.value.isMp3AudioMuted
                val mp3Vol = if (isMp3Muted) 0f else musicPlayer.volume.value
                val isMusicPlaying = musicPlayer.isPlaying.value
                val activeAudioItem = musicPlayer.currentTrack.value
                    ?: repo.config.value.currentAudio
                    ?: repo.config.value.type1Playlist.firstOrNull { it.isEnabled }

                val mp3PathOrUri = if (isMusicPlaying && !isMp3Muted && mp3Vol > 0f) {
                    val raw = activeAudioItem?.path?.ifBlank { activeAudioItem.uri }
                    raw?.takeIf { it.isNotBlank() }
                } else null

                // 2. Resolve Video audio
                val isVideoMuted = repo.config.value.isVideoAudioMuted
                val videoVol = if (isVideoMuted) 0f else repo.videoVolume.value
                val videoPathOrUri = if (!isVideoMuted && videoVol > 0f && !isVideoPaused) {
                    val v = currentActiveVideo ?: repo.config.value.currentVideo
                    val raw = v?.path?.ifBlank { v.uri }
                    raw?.takeIf { it.isNotBlank() }
                } else null

                // 3. Resolve Tipe 2 (Secondary Audio Layer / Ambience)
                val isLayer2Active = musicPlayer.isLayer2Active.value || repo.config.value.isAudioLayer2Enabled
                val isLayer2Playing = musicPlayer.isPlayingLayer2.value
                val l2Track = musicPlayer.currentTrackLayer2.value ?: repo.config.value.currentAudioLayer2
                val isL2Muted = musicPlayer.isMutedLayer2.value || repo.config.value.isAudioLayer2Muted || (l2Track?.isMuted == true)
                val l2Vol = if (isL2Muted) 0f else musicPlayer.volumeLayer2.value
                val l2PathOrUri = if (isLayer2Active && isLayer2Playing && !isL2Muted && l2Vol > 0f) {
                    val raw = l2Track?.path?.ifBlank { l2Track.uri } ?: repo.config.value.audioLayer2Path
                    raw.takeIf { it.isNotBlank() }
                } else null

                // 4. Resolve Tipe 3 (Ringtone Acak Dinamis)
                val isLayer3Active = musicPlayer.isLayer3Active.value || repo.config.value.isAudioLayer3Enabled
                val isLayer3Playing = musicPlayer.isPlayingLayer3.value
                val l3Track = musicPlayer.currentTrackLayer3.value
                val isL3Muted = musicPlayer.isMutedLayer3.value || repo.config.value.isAudioLayer3Muted || (l3Track?.isMuted == true)
                val l3Vol = if (isL3Muted) 0f else musicPlayer.volumeLayer3.value
                val l3PathOrUri = if (isLayer3Active && isLayer3Playing && !isL3Muted && l3Vol > 0f) {
                    val raw = l3Track?.path?.ifBlank { l3Track.uri }
                    raw?.takeIf { it.isNotBlank() }
                } else null

                // 5. Update decoders lifecycle
                if (mp3PathOrUri != lastMp3Source) {
                    mp3Decoder?.release()
                    mp3Decoder = null
                    lastMp3Source = mp3PathOrUri
                    if (mp3PathOrUri != null) {
                        mp3Decoder = AudioTrackDecoder(context, mp3PathOrUri, loop = true)
                        onLog("🔊 RTMP Audio: Mengalirkan Tipe 1 dari ${resolveSourceName(mp3PathOrUri)}")
                    }
                }

                if (videoPathOrUri != lastVideoSource) {
                    videoDecoder?.release()
                    videoDecoder = null
                    lastVideoSource = videoPathOrUri
                    if (videoPathOrUri != null) {
                        videoDecoder = AudioTrackDecoder(context, videoPathOrUri, loop = true)
                    }
                }

                if (l2PathOrUri != lastLayer2Source) {
                    layer2Decoder?.release()
                    layer2Decoder = null
                    lastLayer2Source = l2PathOrUri
                    if (l2PathOrUri != null) {
                        val isLoop = musicPlayer.isLoopingLayer2.value
                        layer2Decoder = AudioTrackDecoder(context, l2PathOrUri, loop = isLoop)
                        onLog("🎧 RTMP Audio: Mengalirkan Tipe 2 (Latar) dari ${resolveSourceName(l2PathOrUri)}")
                    }
                }

                if (l3PathOrUri != lastLayer3Source) {
                    layer3Decoder?.release()
                    layer3Decoder = null
                    lastLayer3Source = l3PathOrUri
                    if (l3PathOrUri != null) {
                        layer3Decoder = AudioTrackDecoder(context, l3PathOrUri, loop = false)
                        onLog("🔔 RTMP Audio: Mengalirkan Tipe 3 (Ringtone) dari ${resolveSourceName(l3PathOrUri)}")
                    }
                }

                // 6. Read PCM samples from active decoders
                val hasMp3 = mp3Decoder != null
                val hasVideo = videoDecoder != null
                val hasLayer2 = layer2Decoder != null
                val hasLayer3 = layer3Decoder != null

                if (hasMp3) {
                    mp3Shorts.fill(0)
                    mp3Decoder!!.readStereoSamples(mp3Shorts, 0, SAMPLES_PER_FRAME)
                }
                if (hasVideo) {
                    videoShorts.fill(0)
                    videoDecoder!!.readStereoSamples(videoShorts, 0, SAMPLES_PER_FRAME)
                }
                if (hasLayer2) {
                    layer2Shorts.fill(0)
                    val read = layer2Decoder!!.readStereoSamples(layer2Shorts, 0, SAMPLES_PER_FRAME)
                    if (read == 0 && !musicPlayer.isLoopingLayer2.value) {
                        layer2Decoder?.release()
                        layer2Decoder = null
                        lastLayer2Source = null
                    }
                }
                if (hasLayer3) {
                    layer3Shorts.fill(0)
                    val read = layer3Decoder!!.readStereoSamples(layer3Shorts, 0, SAMPLES_PER_FRAME)
                    if (read == 0) {
                        layer3Decoder?.release()
                        layer3Decoder = null
                        lastLayer3Source = null
                    }
                }

                // Update active audio source description
                val activeParts = mutableListOf<String>()
                if (hasVideo) activeParts.add("Video")
                if (hasMp3) activeParts.add("Tipe 1")
                if (hasLayer2) activeParts.add("Tipe 2")
                if (hasLayer3) activeParts.add("Tipe 3")
                activeAudioSourceDesc = if (activeParts.isNotEmpty()) {
                    activeParts.joinToString(" + ")
                } else {
                    "Hening (Comfort Silence)"
                }

                // 7. Mix samples into mixShorts with soft clipping
                mixShorts.fill(0)
                val totalFrames = SAMPLES_PER_FRAME * TARGET_CHANNELS
                val activeCount = (if (hasVideo) 1 else 0) + (if (hasMp3) 1 else 0) + (if (hasLayer2) 1 else 0) + (if (hasLayer3) 1 else 0)

                if (activeCount > 0) {
                    for (i in 0 until totalFrames) {
                        var sum = 0f
                        if (hasVideo) sum += videoShorts[i] * videoVol
                        if (hasMp3) sum += mp3Shorts[i] * mp3Vol
                        if (hasLayer2) sum += layer2Shorts[i] * l2Vol
                        if (hasLayer3) sum += layer3Shorts[i] * l3Vol

                        mixShorts[i] = sum.toInt().coerceIn(-32768, 32767).toShort()
                    }
                }

                // 8. Feed AAC Encoder
                val enc = aacEncoder
                if (enc != null && aacEncoderStarted) {
                    val inIdx = enc.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = enc.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            pcmByteBuffer.clear()
                            pcmByteBuffer.asShortBuffer().put(mixShorts)
                            pcmByteBuffer.position(0)
                            pcmByteBuffer.limit(PCM_FRAME_BYTES)
                            inBuf.put(pcmByteBuffer)

                            val ptsUs = ((baseTimestampMs + audioSamplePtsMs) * 1000.0).toLong().coerceAtLeast(0L)
                            enc.queueInputBuffer(inIdx, 0, PCM_FRAME_BYTES, ptsUs, 0)
                        }
                    }

                    // 9. Drain AAC Encoder
                    var outIdx = enc.dequeueOutputBuffer(encBufferInfo, TIMEOUT_US)
                    while (outIdx >= 0) {
                        val outBuf = enc.getOutputBuffer(outIdx)
                        if (outBuf != null) {
                            val aacBytes = ByteArray(encBufferInfo.size)
                            outBuf.position(encBufferInfo.offset)
                            outBuf.get(aacBytes, 0, encBufferInfo.size)

                            if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                client.sendAacSequenceHeader(aacBytes, baseTimestampMs)
                            } else if (encBufferInfo.size > 0) {
                                val streamTs = encBufferInfo.presentationTimeUs / 1000L
                                client.sendAacFrame(aacBytes, streamTs)
                            }
                        }
                        enc.releaseOutputBuffer(outIdx, false)
                        outIdx = enc.dequeueOutputBuffer(encBufferInfo, 0L)
                    }
                }

                // Advance timestamp by ~23.2199ms (1024 samples / 44100 Hz)
                audioSamplePtsMs += (1024.0 * 1000.0 / TARGET_SAMPLE_RATE.toDouble())

                // 10. Real-time Wall Clock Pacing
                val targetWallMs = startWallClockMs + audioSamplePtsMs.toLong()
                val nowWallMs = System.currentTimeMillis()
                val sleepMs = targetWallMs - nowWallMs
                if (sleepMs > 4L) {
                    delay(sleepMs.coerceAtMost(45L))
                }
            }

            mp3Decoder?.release()
            videoDecoder?.release()
            layer2Decoder?.release()
            layer3Decoder?.release()
        } catch (t: Throwable) {
            Log.e(TAG, "Audio loop terhenti: ${t.message}", t)
        } finally {
            try {
                if (aacEncoderStarted) aacEncoder?.stop()
                aacEncoder?.release()
            } catch (_: Throwable) {}
        }
    }

    private fun resolveSourceName(pathOrUri: String): String {
        return try {
            val f = File(pathOrUri)
            if (f.exists()) f.name else Uri.parse(pathOrUri).lastPathSegment ?: pathOrUri
        } catch (_: Throwable) {
            pathOrUri
        }
    }

    /**
     * Transcodes video in real time enforcing a strict keyframe interval of 2.0 seconds (0.5 fps),
     * aligning dimensions to multiples of 16 to eliminate green bottom artifacts.
     */
    suspend fun transcodeVideoFileWith2sKeyframe(
        videoItem: VideoItem,
        baseTimestampMs: Long,
        startWallClockMs: Long,
        initialFramesSent: Long,
        onFrameCountUpdate: (Long) -> Unit
    ): Long {
        currentActiveVideo = videoItem
        val pathOrUri = videoItem.path.ifBlank { videoItem.uri ?: "" }
        val resolvedFile = when {
            pathOrUri.startsWith("/") -> File(pathOrUri)
            pathOrUri.startsWith("file://") -> File(Uri.parse(pathOrUri).path ?: "")
            else -> null
        }

        val extractor = MediaExtractor()
        if (resolvedFile != null && resolvedFile.exists()) {
            extractor.setDataSource(resolvedFile.absolutePath)
        } else {
            extractor.setDataSource(context, Uri.parse(pathOrUri), null)
        }

        var videoTrackIdx = -1
        var videoFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                videoTrackIdx = i
                videoFormat = fmt
                break
            }
        }

        if (videoTrackIdx < 0 || videoFormat == null) {
            extractor.release()
            return baseTimestampMs
        }

        extractor.selectTrack(videoTrackIdx)

        val rawSrcW = try { videoFormat.getInteger(MediaFormat.KEY_WIDTH) } catch (_: Throwable) { config.targetWidth }
        val rawSrcH = try { videoFormat.getInteger(MediaFormat.KEY_HEIGHT) } catch (_: Throwable) { config.targetHeight }

        // Scale down if larger than target dimension, but keep source aspect ratio
        val maxDim = maxOf(config.targetWidth, config.targetHeight)
        val scale = if (maxOf(rawSrcW, rawSrcH) > maxDim) {
            maxDim.toFloat() / maxOf(rawSrcW, rawSrcH)
        } else {
            1.0f
        }

        // Strictly align width and height to multiples of 16 (eliminates YUV hardware padding green lines)
        val targetW = (((rawSrcW * scale).toInt() / 16) * 16).coerceAtLeast(320)
        val targetH = (((rawSrcH * scale).toInt() / 16) * 16).coerceAtLeast(320)

        val targetFps = config.bitratePreset.fps.coerceIn(15, 60)
        val targetBitrate = config.bitratePreset.videoBitrateKbps * 1000
        val keyframeIntervalSec = config.forceKeyframeIntervalSec.coerceIn(1, 4) // 2s default

        var encoder: MediaCodec? = null
        var decoder: MediaCodec? = null
        var encoderSurface: Surface? = null
        var framesCount = initialFramesSent
        var lastStreamTsMs = baseTimestampMs
        var playbackStartWallMs = System.currentTimeMillis()
        var pauseStartWallMs = 0L

        try {
            // Configure H.264 Encoder with KEY_I_FRAME_INTERVAL = 2s (Facebook Live Standard)
            val encFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetW, targetH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyframeIntervalSec) // 2 DETIK KEYFRAME (0.5 fps)
                try {
                    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                    setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
                } catch (_: Throwable) {}
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoderSurface = createInputSurface()
                start()
            }

            // Configure Video Decoder outputting directly to Encoder's Input Surface (Hardware zero-copy)
            val decMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
            decoder = MediaCodec.createDecoderByType(decMime).apply {
                configure(videoFormat, encoderSurface, null, 0)
                start()
            }

            onLog("🚀 Live Transcoder Aktif: ${targetW}x${targetH} • Keyframe dikunci ${keyframeIntervalSec}s (0.5 fps) untuk Facebook/IG Live.")

            val decBufferInfo = MediaCodec.BufferInfo()
            val encBufferInfo = MediaCodec.BufferInfo()
            var extractorDone = false
            var decoderDone = false
            var firstFramePtsUs = -1L

            while (scope.isActive && !decoderDone) {
                // 1. Detect if user switched to another video (e.g. clicked Next or Spill)
                val activeVid = repo.config.value.currentVideo ?: repo.config.value.playlist.getOrNull(repo.config.value.currentVideoIndex)
                if (activeVid != null && activeVid.id != videoItem.id) {
                    onLog("⚡ Transisi mulus RTMP ke video baru: ${activeVid.name}")
                    break
                }

                // 2. Respect Live Pause and maintain wall-clock sync
                if (repo.config.value.isVideoPaused) {
                    if (pauseStartWallMs == 0L) {
                        pauseStartWallMs = System.currentTimeMillis()
                    }
                    delay(80L)
                    continue
                } else if (pauseStartWallMs > 0L) {
                    val pausedDuration = System.currentTimeMillis() - pauseStartWallMs
                    playbackStartWallMs += pausedDuration
                    pauseStartWallMs = 0L
                }

                // Feed Extractor -> Decoder
                if (!extractorDone) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                extractorDone = true
                            } else {
                                val ptsUs = extractor.sampleTime.coerceAtLeast(0L)
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, ptsUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Decoder -> Render to Encoder Surface with precise PTS
                val decOutIdx = decoder.dequeueOutputBuffer(decBufferInfo, TIMEOUT_US)
                if (decOutIdx >= 0) {
                    val isEos = (decBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    if (isEos) {
                        decoderDone = true
                        encoder.signalEndOfInputStream()
                    } else {
                        if (firstFramePtsUs < 0L) {
                            firstFramePtsUs = decBufferInfo.presentationTimeUs
                        }
                        // Render to surface with nanosecond PTS to avoid buffer tearing
                        decoder.releaseOutputBuffer(decOutIdx, decBufferInfo.presentationTimeUs * 1000L)
                    }
                }

                // Drain Encoder -> Send to RTMP
                var encOutIdx = encoder.dequeueOutputBuffer(encBufferInfo, TIMEOUT_US)
                while (encOutIdx >= 0) {
                    val outBuf = encoder.getOutputBuffer(encOutIdx)
                    if (outBuf != null) {
                        val naluBytes = ByteArray(encBufferInfo.size)
                        outBuf.position(encBufferInfo.offset)
                        outBuf.get(naluBytes, 0, encBufferInfo.size)

                        val isConfig = (encBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        val isKey = (encBufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                        if (isConfig) {
                            extractAndSendSpsPps(naluBytes, baseTimestampMs)
                        } else if (encBufferInfo.size > 0) {
                            val frameRelMs = if (firstFramePtsUs >= 0L) {
                                ((encBufferInfo.presentationTimeUs - firstFramePtsUs) / 1000L).coerceAtLeast(0L)
                            } else {
                                (encBufferInfo.presentationTimeUs / 1000L).coerceAtLeast(0L)
                            }

                            val streamTs = baseTimestampMs + frameRelMs
                            lastStreamTsMs = maxOf(lastStreamTsMs, streamTs)

                            client.sendH264Frame(naluBytes, isKey, streamTs)
                            framesCount++
                            onFrameCountUpdate(framesCount)

                            // Real-time pacing
                            val elapsedWallMs = System.currentTimeMillis() - playbackStartWallMs
                            val aheadMs = frameRelMs - elapsedWallMs
                            if (aheadMs > 6L) {
                                delay(aheadMs.coerceAtMost(60L))
                            }
                        }
                    }

                    encoder.releaseOutputBuffer(encOutIdx, false)
                    encOutIdx = encoder.dequeueOutputBuffer(encBufferInfo, 0L)
                }
            }

            return lastStreamTsMs
        } catch (t: Throwable) {
            Log.e(TAG, "Transcoder error, fallback to passthrough: ${t.message}", t)
            onLog("⚠️ Live Transcoder terhenti (${t.message}). Menggunakan passthrough...")
            return baseTimestampMs
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Throwable) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Throwable) {}
            try { encoderSurface?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    private fun extractAndSendSpsPps(configBytes: ByteArray, timestampMs: Long) {
        val nalus = client.splitAnnexBNalus(configBytes)
        var sps: ByteArray? = null
        var pps: ByteArray? = null

        for (nalu in nalus) {
            if (nalu.isNotEmpty()) {
                val type = nalu[0].toInt() and 0x1F
                if (type == 7) sps = nalu
                if (type == 8) pps = nalu
            }
        }

        if (sps != null && pps != null) {
            client.sendAvcSequenceHeader(sps, pps, timestampMs)
        }
    }

    /**
     * Helper decoder to extract and decode 16-bit PCM stereo audio samples from any media file or URI.
     */
    private class AudioTrackDecoder(
        private val context: Context,
        private val pathOrUri: String,
        private val loop: Boolean = true
    ) {
        private var extractor: MediaExtractor? = null
        private var decoder: MediaCodec? = null
        private var isInitialized = false
        private var isEos = false
        private var srcChannels = 2
        private val bufferInfo = MediaCodec.BufferInfo()
        private var currentOutBuffer: ByteBuffer? = null
        private var currentOutIndex = -1

        init {
            try {
                val ext = MediaExtractor()
                val file = File(pathOrUri)
                if (file.exists() && file.canRead()) {
                    ext.setDataSource(file.absolutePath)
                } else {
                    val uri = Uri.parse(pathOrUri)
                    ext.setDataSource(context, uri, null)
                }

                var audioTrackIdx = -1
                var audioFormat: MediaFormat? = null
                for (i in 0 until ext.trackCount) {
                    val fmt = ext.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIdx = i
                        audioFormat = fmt
                        break
                    }
                }

                if (audioTrackIdx >= 0 && audioFormat != null) {
                    ext.selectTrack(audioTrackIdx)
                    val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
                    val dec = MediaCodec.createDecoderByType(mime)
                    dec.configure(audioFormat, null, null, 0)
                    dec.start()
                    extractor = ext
                    decoder = dec
                    srcChannels = try { audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Throwable) { 2 }
                    isInitialized = true
                } else {
                    ext.release()
                }
            } catch (e: Throwable) {
                release()
            }
        }

        /**
         * Reads up to [maxStereoSamples] 16-bit stereo sample pairs into [outSamples] starting at [offset].
         * Returns the count of stereo sample pairs read.
         */
        fun readStereoSamples(outSamples: ShortArray, offset: Int, maxStereoSamples: Int): Int {
            if (!isInitialized || decoder == null || extractor == null) return 0
            val dec = decoder ?: return 0
            val ext = extractor ?: return 0

            var samplesRead = 0
            val timeoutUs = 2000L

            while (samplesRead < maxStereoSamples) {
                val buf = currentOutBuffer
                if (buf != null && buf.hasRemaining()) {
                    if (srcChannels == 1) {
                        // Mono -> Stereo expansion
                        while (buf.remaining() >= 2 && samplesRead < maxStereoSamples) {
                            val mono = buf.short
                            outSamples[(offset + samplesRead) * 2] = mono
                            outSamples[(offset + samplesRead) * 2 + 1] = mono
                            samplesRead++
                        }
                    } else {
                        // Stereo direct copy
                        while (buf.remaining() >= 4 && samplesRead < maxStereoSamples) {
                            outSamples[(offset + samplesRead) * 2] = buf.short
                            outSamples[(offset + samplesRead) * 2 + 1] = buf.short
                            samplesRead++
                        }
                    }

                    if (!buf.hasRemaining()) {
                        if (currentOutIndex >= 0) {
                            dec.releaseOutputBuffer(currentOutIndex, false)
                            currentOutIndex = -1
                        }
                        currentOutBuffer = null
                    }
                    if (samplesRead >= maxStereoSamples) break
                }

                // Feed input buffer
                if (!isEos) {
                    val inIdx = dec.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = dec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            val bytesRead = ext.readSampleData(inBuf, 0)
                            if (bytesRead < 0) {
                                if (loop) {
                                    ext.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                                    dec.queueInputBuffer(inIdx, 0, 0, 0L, 0)
                                } else {
                                    dec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    isEos = true
                                }
                            } else {
                                val ptsUs = ext.sampleTime.coerceAtLeast(0L)
                                dec.queueInputBuffer(inIdx, 0, bytesRead, ptsUs, 0)
                                ext.advance()
                            }
                        }
                    }
                }

                // Dequeue next output buffer
                val outIdx = dec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIdx >= 0) {
                    currentOutIndex = outIdx
                    val outBuf = dec.getOutputBuffer(outIdx)?.order(ByteOrder.LITTLE_ENDIAN)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        currentOutBuffer = outBuf
                    } else {
                        dec.releaseOutputBuffer(outIdx, false)
                        currentOutIndex = -1
                        currentOutBuffer = null
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFmt = dec.outputFormat
                    srcChannels = try { newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Throwable) { srcChannels }
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                }
            }

            return samplesRead
        }

        fun release() {
            isInitialized = false
            try {
                if (currentOutIndex >= 0) {
                    decoder?.releaseOutputBuffer(currentOutIndex, false)
                    currentOutIndex = -1
                }
                currentOutBuffer = null
                decoder?.stop()
                decoder?.release()
            } catch (_: Throwable) {}
            try {
                extractor?.release()
            } catch (_: Throwable) {}
            decoder = null
            extractor = null
        }
    }
}
