package com.tajasb.rtmp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.tajasb.data.VcamRepository
import com.tajasb.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RtmpStreamEngine private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "tajasb_rtmp_prefs"
        private const val KEY_ACTIVE_CONFIG = "key_active_rtmp_config"
        private const val KEY_SAVED_PROFILES = "key_saved_rtmp_profiles"

        @Volatile
        private var INSTANCE: RtmpStreamEngine? = null

        fun getInstance(context: Context): RtmpStreamEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RtmpStreamEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<RtmpConfig> = _config.asStateFlow()

    private val _savedProfiles = MutableStateFlow(loadSavedProfiles())
    val savedProfiles: StateFlow<List<RtmpSavedProfile>> = _savedProfiles.asStateFlow()

    private val _telemetry = MutableStateFlow(RtmpTelemetry())
    val telemetry: StateFlow<RtmpTelemetry> = _telemetry.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(
        listOf("[SIAP] Mesin Broadcaster RTMP/RTMPS (YT • FB • IG • TikTok) siap digunakan.")
    )
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var streamJob: Job? = null
    private var activeClient: RtmpProtocolClient? = null

    private fun loadConfig(): RtmpConfig {
        val raw = prefs.getString(KEY_ACTIVE_CONFIG, null) ?: return RtmpConfig()
        return try {
            val cfg = RtmpConfig.fromJson(JSONObject(raw))
            if (cfg.platform == RtmpPlatformPreset.INSTAGRAM && cfg.serverUrl.contains("sin1-1")) {
                cfg.copy(serverUrl = RtmpPlatformPreset.INSTAGRAM.defaultServerUrl)
            } else {
                cfg
            }
        } catch (_: Throwable) {
            RtmpConfig()
        }
    }

    private fun persistConfig(newConfig: RtmpConfig) {
        _config.value = newConfig
        prefs.edit().putString(KEY_ACTIVE_CONFIG, newConfig.toJson().toString()).apply()
    }

    private fun loadSavedProfiles(): List<RtmpSavedProfile> {
        val raw = prefs.getString(KEY_SAVED_PROFILES, null)
        return RtmpSavedProfile.listFromJsonString(raw)
    }

    private fun persistProfiles(list: List<RtmpSavedProfile>) {
        _savedProfiles.value = list
        prefs.edit().putString(KEY_SAVED_PROFILES, RtmpSavedProfile.listToJsonString(list)).apply()
    }

    fun selectPlatform(platform: RtmpPlatformPreset) {
        val current = _config.value
        val shouldReplaceUrl = current.serverUrl.isBlank() ||
            RtmpPlatformPreset.entries.any { it.defaultServerUrl == current.serverUrl || (it.backupServerUrl != null && it.backupServerUrl == current.serverUrl) }
        val updatedUrl = if (shouldReplaceUrl) platform.defaultServerUrl else current.serverUrl
        persistConfig(current.copy(platform = platform, serverUrl = updatedUrl))
        appendLog("Platform dipilih: ${platform.badgeIcon} ${platform.label} (${updatedUrl})")
    }

    fun updateServerUrl(url: String) {
        persistConfig(_config.value.copy(serverUrl = url))
    }

    fun updateStreamKey(key: String) {
        persistConfig(_config.value.copy(streamKey = key))
    }

    fun updateBitratePreset(preset: RtmpBitratePreset) {
        persistConfig(_config.value.copy(bitratePreset = preset))
        appendLog("Kualitas stream diatur ke ${preset.label}")
    }

    fun updateOrientation(isPortrait: Boolean) {
        persistConfig(_config.value.copy(isPortrait = isPortrait))
    }

    fun updateAutoReconnect(enabled: Boolean) {
        persistConfig(_config.value.copy(autoReconnect = enabled))
    }

    fun updateSyncPlaylistAndSpill(enabled: Boolean) {
        persistConfig(_config.value.copy(syncPlaylistAndSpill = enabled))
    }

    fun updateApplyAntiHashMetadata(enabled: Boolean) {
        persistConfig(_config.value.copy(applyAntiHashMetadata = enabled))
    }

    fun updateLiveTranscoderEnabled(enabled: Boolean) {
        persistConfig(_config.value.copy(isLiveTranscoderEnabled = enabled))
        appendLog(if (enabled) "Live Transcoder 2s Keyframe diaktifkan (Standar Facebook/IG/TikTok)." else "Live Transcoder dinonaktifkan (Mode Passthrough).")
    }

    fun saveCurrentProfile(customName: String): RtmpSavedProfile {
        val cfg = _config.value
        val cleanName = customName.trim().ifEmpty {
            "${cfg.platform.label} (${SimpleDateFormat("HH:mm", Locale.US).format(Date())})"
        }
        val profile = RtmpSavedProfile(
            id = UUID.randomUUID().toString(),
            profileName = cleanName,
            config = cfg
        )
        val updated = listOf(profile) + _savedProfiles.value.filterNot { it.profileName.equals(cleanName, ignoreCase = true) }
        persistProfiles(updated.take(15))
        appendLog("Profil RTMP '$cleanName' disimpan.")
        return profile
    }

    fun loadProfile(profile: RtmpSavedProfile) {
        persistConfig(profile.config)
        appendLog("Profil '${profile.profileName}' dimuat (${profile.config.platform.label}).")
    }

    fun deleteProfile(profileId: String) {
        val updated = _savedProfiles.value.filterNot { it.id == profileId }
        persistProfiles(updated)
        appendLog("Profil RTMP dihapus.")
    }

    fun clearLogs() {
        _logs.value = listOf("[INFO] Log RTMP dibersihkan.")
    }

    fun appendLog(message: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = "[$timeStr] $message"
        val current = _logs.value
        // Log terbaru selalu diletakkan paling atas (index 0) agar user tidak perlu scroll ke bawah
        _logs.value = (listOf(entry) + current).take(100)
    }

    /**
     * Tests server reachability and RTMP v3 handshake without starting a live broadcast.
     */
    fun testServerConnection(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (_telemetry.value.isBroadcasting) {
            onResult(false, "Siaran sedang berlangsung.")
            return
        }
        val cfg = _config.value
        val parsed = RtmpProtocolClient.parseEndpoint(cfg.serverUrl, cfg.streamKey)
        if (parsed.isFailure) {
            val err = parsed.exceptionOrNull()?.message ?: "URL RTMP tidak valid"
            appendLog("❌ Gagal validasi URL: $err")
            _telemetry.value = _telemetry.value.copy(
                state = RtmpStreamState.ERROR,
                statusMessage = err,
                lastError = err
            )
            onResult(false, err)
            return
        }

        val endpoint = parsed.getOrThrow()
        engineScope.launch {
            _telemetry.value = _telemetry.value.copy(
                state = RtmpStreamState.TESTING,
                statusMessage = "Menguji koneksi ke ${endpoint.host}:${endpoint.port}...",
                connectedHost = "${endpoint.host}:${endpoint.port}",
                lastError = null
            )
            appendLog("🔍 Menguji jangkauan server ${endpoint.scheme.uppercase()}://${endpoint.host}:${endpoint.port}...")

            val client = RtmpProtocolClient()
            val result = client.testServerReachability(endpoint, timeoutMs = 6000)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val msg = result.getOrNull() ?: "Server aktif"
                    appendLog("✅ $msg")
                    _telemetry.value = _telemetry.value.copy(
                        state = RtmpStreamState.IDLE,
                        statusMessage = msg,
                        lastError = null
                    )
                    onResult(true, msg)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Gagal menghubungi server RTMP"
                    appendLog("⚠️ Tes koneksi gagal: $err")
                    _telemetry.value = _telemetry.value.copy(
                        state = RtmpStreamState.ERROR,
                        statusMessage = "Gagal terhubung: $err",
                        lastError = err
                    )
                    onResult(false, err)
                }
            }
        }
    }

    /**
     * Starts live RTMP/RTMPS broadcast using active videos from VcamRepository.
     */
    fun startBroadcast(onFeedback: (String) -> Unit = {}) {
        if (_telemetry.value.isBroadcasting) {
            onFeedback("Siaran RTMP sudah berjalan.")
            return
        }

        val cfg = _config.value
        val parsed = RtmpProtocolClient.parseEndpoint(cfg.serverUrl, cfg.streamKey)
        if (parsed.isFailure) {
            val msg = parsed.exceptionOrNull()?.message ?: "URL RTMP tidak valid"
            appendLog("❌ $msg")
            _telemetry.value = _telemetry.value.copy(
                state = RtmpStreamState.ERROR,
                statusMessage = msg,
                lastError = msg
            )
            onFeedback(msg)
            return
        }

        val endpoint = parsed.getOrThrow()
        if (endpoint.streamKey.isBlank()) {
            val msg = "Stream Key wajib diisi sebelum memulai Live ${cfg.platform.label}."
            appendLog("❌ $msg")
            _telemetry.value = _telemetry.value.copy(
                state = RtmpStreamState.ERROR,
                statusMessage = msg,
                lastError = msg
            )
            onFeedback(msg)
            return
        }

        val repo = VcamRepository.getInstance(context)
        val vcamCfg = repo.config.value
        val activePlaylist = vcamCfg.playlist.filter { it.isEnabled }
        if (activePlaylist.isEmpty()) {
            val msg = "Playlist video masih kosong! Tambahkan minimal 1 video MP4 di tab Video."
            appendLog("❌ $msg")
            _telemetry.value = _telemetry.value.copy(
                state = RtmpStreamState.ERROR,
                statusMessage = msg,
                lastError = msg
            )
            onFeedback(msg)
            return
        }

        streamJob?.cancel()
        streamJob = engineScope.launch {
            runBroadcastLoop(endpoint, cfg, repo)
        }
        onFeedback("Memulai siaran Live RTMP ke ${cfg.platform.label}...")
    }

    /**
     * Stops the active RTMP broadcast cleanly.
     */
    fun stopBroadcast(onFeedback: (String) -> Unit = {}) {
        engineScope.launch {
            try {
                activeClient?.closeQuietly()
                activeClient = null
                streamJob?.cancelAndJoin()
            } catch (_: Throwable) {}
            streamJob = null
            appendLog("⏹️ Siaran Live RTMP dihentikan oleh pengguna.")
            _telemetry.value = RtmpTelemetry(
                state = RtmpStreamState.IDLE,
                statusMessage = "Siaran dihentikan. Siap untuk sesi Live berikutnya."
            )
            withContext(Dispatchers.Main) {
                onFeedback("Siaran Live RTMP dihentikan.")
            }
        }
    }

    private suspend fun CoroutineScope.runBroadcastLoop(
        endpoint: ParsedRtmpEndpoint,
        initialConfig: RtmpConfig,
        repo: VcamRepository
    ) {
        var reconnectAttempt = 0
        val maxReconnectAttempts = if (initialConfig.autoReconnect) 8 else 0
        val startWallClockMs = System.currentTimeMillis()
        var globalStreamTimestampMs = 0L
        var totalFramesSent = 0L

        while (isActive) {
            val cfg = _config.value
            val client = RtmpProtocolClient()
            activeClient = client

            val antiHashEncoderTag = if (cfg.applyAntiHashMetadata) {
                val dna = repo.config.value.activeSessionDna
                "VCamStudio-${dna.codeName}-${(System.currentTimeMillis() % 9000) + 1000}"
            } else {
                "Lavf60.16.100"
            }

            appendLog("📡 Menghubungkan ke ${endpoint.scheme.uppercase()}://${endpoint.host}:${endpoint.port}/${endpoint.app}...")

            val connectResult = client.connectAndPublish(
                endpoint = endpoint,
                width = cfg.targetWidth,
                height = cfg.targetHeight,
                videoBitrateKbps = cfg.bitratePreset.videoBitrateKbps,
                fps = cfg.bitratePreset.fps,
                audioBitrateKbps = cfg.bitratePreset.audioBitrateKbps,
                antiHashTag = antiHashEncoderTag,
                onStageUpdate = { st, msg ->
                    _telemetry.value = _telemetry.value.copy(
                        state = st,
                        statusMessage = msg,
                        connectedHost = "${endpoint.host}:${endpoint.port}",
                        reconnectCount = reconnectAttempt
                    )
                }
            )

            if (connectResult.isFailure) {
                val errMsg = connectResult.exceptionOrNull()?.message ?: "Koneksi terputus"
                appendLog("⚠️ Gagal handshake/publish: $errMsg")
                client.closeQuietly()

                if (cfg.autoReconnect && reconnectAttempt < maxReconnectAttempts && isActive) {
                    reconnectAttempt++
                    val waitSec = (reconnectAttempt * 2).coerceAtMost(10)
                    _telemetry.value = _telemetry.value.copy(
                        state = RtmpStreamState.RECONNECTING,
                        statusMessage = "Mencoba sambung ulang ($reconnectAttempt/$maxReconnectAttempts) dalam ${waitSec}d...",
                        reconnectCount = reconnectAttempt,
                        lastError = errMsg
                    )
                    appendLog("🔄 Auto-Reconnect #$reconnectAttempt dalam ${waitSec} detik...")
                    delay(waitSec * 1000L)
                    continue
                } else {
                    _telemetry.value = _telemetry.value.copy(
                        state = RtmpStreamState.ERROR,
                        statusMessage = "Gagal menyambung ke server: $errMsg",
                        lastError = errMsg
                    )
                    break
                }
            }

            // Connected & publishing!
            reconnectAttempt = 0
            appendLog("🟢 LIVE ON AIR! Mengalirkan paket H.264/AAC ke ${cfg.platform.label} (${cfg.bitratePreset.shortBadge}).")

            val mediaPipeline = RtmpLiveMediaPipeline(
                context = context,
                repo = repo,
                client = client,
                config = cfg,
                onLog = { appendLog(it) }
            )
            resolveActiveVideoItem(repo)?.let { mediaPipeline.setActiveVideo(it) }
            mediaPipeline.startAudioPipeline(baseTimestampMs = globalStreamTimestampMs)

            try {
                while (isActive) {
                    val currentVideo = resolveActiveVideoItem(repo)
                    if (currentVideo == null) {
                        appendLog("⚠️ Tidak ada video aktif di playlist. Menunggu video...")
                        delay(1000L)
                        continue
                    }

                    appendLog("🎬 Memutar sumber ke RTMP: ${currentVideo.name}")
                    mediaPipeline.setActiveVideo(currentVideo)
                    _telemetry.value = _telemetry.value.copy(
                        state = RtmpStreamState.LIVE,
                        activeVideoName = currentVideo.name,
                        statusMessage = "LIVE ke ${cfg.platform.label} • ${currentVideo.name}",
                        keyframeIntervalSec = if (cfg.isLiveTranscoderEnabled) 2.0f else 7.5f,
                        audioSourceDescription = mediaPipeline.activeAudioSourceDesc
                    )

                    val nextOffset = if (cfg.isLiveTranscoderEnabled) {
                        val transcodeOffset = mediaPipeline.transcodeVideoFileWith2sKeyframe(
                            videoItem = currentVideo,
                            baseTimestampMs = globalStreamTimestampMs,
                            startWallClockMs = startWallClockMs,
                            initialFramesSent = totalFramesSent,
                            onFrameCountUpdate = { totalFramesSent = it }
                        )
                        if (transcodeOffset > globalStreamTimestampMs) {
                            transcodeOffset
                        } else {
                            streamSingleVideoFile(
                                client = client,
                                videoItem = currentVideo,
                                repo = repo,
                                baseTimestampMs = globalStreamTimestampMs,
                                startWallClockMs = startWallClockMs,
                                initialFramesSent = totalFramesSent,
                                onFrameCountUpdate = { totalFramesSent = it },
                                isAudioHandledExternally = true
                            )
                        }
                    } else {
                        streamSingleVideoFile(
                            client = client,
                            videoItem = currentVideo,
                            repo = repo,
                            baseTimestampMs = globalStreamTimestampMs,
                            startWallClockMs = startWallClockMs,
                            initialFramesSent = totalFramesSent,
                            onFrameCountUpdate = { totalFramesSent = it },
                            isAudioHandledExternally = true
                        )
                    }
                    globalStreamTimestampMs = nextOffset

                    // Advance playlist if auto-next or syncPlaylistAndSpill is enabled
                    if (_config.value.syncPlaylistAndSpill && isActive) {
                        repo.nextVideo()
                        delay(40L)
                    }
                }
            } catch (streamErr: Exception) {
                if (!isActive) break
                val msg = streamErr.message ?: "Aliran paket RTMP terputus"
                appendLog("⚠️ Gangguan jaringan saat Live: $msg")
                client.closeQuietly()

                if (_config.value.autoReconnect && reconnectAttempt < maxReconnectAttempts) {
                    reconnectAttempt++
                    _telemetry.value = _telemetry.value.copy(
                        state = RtmpStreamState.RECONNECTING,
                        statusMessage = "Koneksi terputus, menyambung ulang ($reconnectAttempt/$maxReconnectAttempts)...",
                        reconnectCount = reconnectAttempt,
                        lastError = msg
                    )
                    delay(2500L)
                    continue
                } else {
                    _telemetry.value = _telemetry.value.copy(
                        state = RtmpStreamState.ERROR,
                        statusMessage = "Siaran terhenti: $msg",
                        lastError = msg
                    )
                    break
                }
            } finally {
                mediaPipeline.stop()
                client.closeQuietly()
            }
        }
    }

    private fun resolveActiveVideoItem(repo: VcamRepository): VideoItem? {
        val vcamCfg = repo.config.value
        val current = vcamCfg.currentVideo
        if (current != null && current.isEnabled) {
            return current
        }
        return vcamCfg.playlist.firstOrNull { it.isEnabled }
    }

    private suspend fun CoroutineScope.streamSingleVideoFile(
        client: RtmpProtocolClient,
        videoItem: VideoItem,
        repo: VcamRepository,
        baseTimestampMs: Long,
        startWallClockMs: Long,
        initialFramesSent: Long,
        onFrameCountUpdate: (Long) -> Unit,
        isAudioHandledExternally: Boolean = false
    ): Long {
        val extractor = MediaExtractor()
        var framesCount = initialFramesSent
        var lastTimestampInFileMs = 0L

        try {
            val pathOrUri = videoItem.path.ifBlank { videoItem.uri ?: "" }
            val resolvedFile = when {
                pathOrUri.startsWith("/") -> File(pathOrUri)
                pathOrUri.startsWith("file://") -> File(Uri.parse(pathOrUri).path ?: "")
                else -> null
            }

            if (resolvedFile != null && resolvedFile.exists()) {
                extractor.setDataSource(resolvedFile.absolutePath)
            } else {
                extractor.setDataSource(context, Uri.parse(pathOrUri), null)
            }

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    videoFormat = fmt
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i
                    audioFormat = fmt
                }
            }

            if (videoTrackIndex >= 0) {
                extractor.selectTrack(videoTrackIndex)
                videoFormat?.let { fmt ->
                    val sps = fmt.getByteBuffer("csd-0")?.toByteArray()
                    val pps = fmt.getByteBuffer("csd-1")?.toByteArray()
                    if (sps != null && pps != null) {
                        client.sendAvcSequenceHeader(sps, pps, baseTimestampMs)
                    }
                }
            }

            if (!isAudioHandledExternally && audioTrackIndex >= 0 && !repo.config.value.isVideoAudioMuted) {
                extractor.selectTrack(audioTrackIndex)
                audioFormat?.let { fmt ->
                    val asc = fmt.getByteBuffer("csd-0")?.toByteArray() ?: ByteArray(0)
                    client.sendAacSequenceHeader(asc, baseTimestampMs)
                }
            }

            val sampleBuffer = ByteBuffer.allocate(512 * 1024)
            var filePlaybackStartWallMs = System.currentTimeMillis()
            var pauseStartWallMs = 0L
            var bytesAtLastTelemetry = client.totalBytesSent
            var lastTelemetryWallMs = System.currentTimeMillis()
            var framesInCurrentSecond = 0

            while (isActive) {
                // Check if user switched to another video in playlist/Spill/IFS
                if (_config.value.syncPlaylistAndSpill) {
                    val latestActive = resolveActiveVideoItem(repo)
                    if (latestActive != null && latestActive.id != videoItem.id) {
                        appendLog("⚡ Transisi mulus RTMP ke video baru: ${latestActive.name}")
                        break
                    }
                }

                // Respect Live Pause from Tajasb controls
                if (repo.config.value.isVideoPaused) {
                    if (pauseStartWallMs == 0L) {
                        pauseStartWallMs = System.currentTimeMillis()
                    }
                    delay(80L)
                    continue
                } else if (pauseStartWallMs > 0L) {
                    val pausedDuration = System.currentTimeMillis() - pauseStartWallMs
                    filePlaybackStartWallMs += pausedDuration
                    pauseStartWallMs = 0L
                }

                val trackIdx = extractor.sampleTrackIndex
                if (trackIdx < 0) {
                    // End of video file reached
                    break
                }

                sampleBuffer.clear()
                val sampleSize = extractor.readSampleData(sampleBuffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                val sampleTimeMs = sampleTimeUs / 1000L
                lastTimestampInFileMs = maxOf(lastTimestampInFileMs, sampleTimeMs)
                val streamTsMs = baseTimestampMs + sampleTimeMs

                val flags = extractor.sampleFlags
                val isKeyFrame = (flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0

                val frameBytes = ByteArray(sampleSize)
                sampleBuffer.position(0)
                sampleBuffer.get(frameBytes, 0, sampleSize)

                if (trackIdx == videoTrackIndex) {
                    client.sendH264Frame(frameBytes, isKeyFrame, streamTsMs)
                    framesCount++
                    framesInCurrentSecond++
                    onFrameCountUpdate(framesCount)
                } else if (!isAudioHandledExternally && trackIdx == audioTrackIndex && !repo.config.value.isVideoAudioMuted) {
                    client.sendAacFrame(frameBytes, streamTsMs)
                }

                extractor.advance()

                // Real-time pacing so we don't flood the RTMP ingest buffer faster than 1x playback
                val elapsedWallMs = System.currentTimeMillis() - filePlaybackStartWallMs
                val aheadMs = sampleTimeMs - elapsedWallMs
                if (aheadMs > 8L) {
                    delay(aheadMs.coerceAtMost(80L))
                }

                // Update telemetry every ~1000ms
                val nowMs = System.currentTimeMillis()
                val deltaWallMs = nowMs - lastTelemetryWallMs
                if (deltaWallMs >= 1000L) {
                    val deltaBytes = (client.totalBytesSent - bytesAtLastTelemetry).coerceAtLeast(0L)
                    val measuredKbps = ((deltaBytes * 8L) / deltaWallMs).toInt()
                    val effectiveKbps = if (measuredKbps > 0) measuredKbps else _config.value.bitratePreset.videoBitrateKbps
                    val effectiveFps = ((framesInCurrentSecond * 1000L) / deltaWallMs).toInt()
                        .coerceIn(1, _config.value.bitratePreset.fps)

                    _telemetry.value = _telemetry.value.copy(
                        state = RtmpStreamState.LIVE,
                        uptimeSeconds = (nowMs - startWallClockMs) / 1000L,
                        currentBitrateKbps = effectiveKbps,
                        currentFps = effectiveFps,
                        totalBytesSent = client.totalBytesSent,
                        framesSent = framesCount,
                        activeVideoName = videoItem.name
                    )

                    bytesAtLastTelemetry = client.totalBytesSent
                    lastTelemetryWallMs = nowMs
                    framesInCurrentSecond = 0
                }
            }
        } finally {
            try {
                extractor.release()
            } catch (_: Throwable) {}
        }

        return baseTimestampMs + lastTimestampInFileMs + 33L
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val dup = this.duplicate()
        val bytes = ByteArray(dup.remaining())
        dup.get(bytes)
        return bytes
    }
}
