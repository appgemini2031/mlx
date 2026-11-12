package com.tajasb.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.UserManager
import android.provider.DocumentsContract
import android.util.Log
import com.tajasb.model.AudioItem
import com.tajasb.model.AutoNextMode
import com.tajasb.model.BridgeCyclePhase
import com.tajasb.model.HookEngineType
import com.tajasb.model.MediaMetadata
import com.tajasb.model.ResolutionPreset
import com.tajasb.model.ScaleMode
import com.tajasb.model.TargetAppInfo
import com.tajasb.model.VcamConfig
import com.tajasb.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class VcamRepository(internal val context: Context) {

    companion object {
        private const val TAG = "VcamRepository"
        @Volatile private var INSTANCE: VcamRepository? = null

        @Volatile var lastGlobalPlaybackTimeMs: Long = 0L

        const val MAKKAH_LIVE_STREAM_1 = "http://m.live.net.sa:1935/live/quran/playlist.m3u8"
        const val MAKKAH_LIVE_STREAM_2 = "https://cdn-globecast.akamaized.net/live/eds/saudi_quran/hls_roku/index.m3u8"
        const val MAKKAH_LIVE_STREAM_3 = "http://m.live.net.sa:1935/live/sunnah/playlist.m3u8"

        fun getInstance(context: Context): VcamRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VcamRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val prefs =
        context.getSharedPreferences("vcam_settings", Context.MODE_PRIVATE)

    @Volatile
    private var lastInternalConfigUpdateMs: Long = 0L

    @Volatile
    private var isInternalPersistenceInProgress: Boolean = false

    private val prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "key_playlist_version") return@OnSharedPreferenceChangeListener
        if (isInternalPersistenceInProgress || (System.currentTimeMillis() - lastInternalConfigUpdateMs) < 2500L) {
            return@OnSharedPreferenceChangeListener
        }
        val updated = loadConfig()
        if (_config.value != updated) {
            _config.value = updated
        }
    }

    internal val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<VcamConfig> = _config.asStateFlow()

    private val _targetApps = MutableStateFlow<List<TargetAppInfo>>(emptyList())
    val targetApps: StateFlow<List<TargetAppInfo>> = _targetApps.asStateFlow()

    val musicPlayer = com.tajasb.audio.StandaloneMusicPlayer.getInstance(context)

    private val _isMusicAutoPausedWaitingForMute = MutableStateFlow(false)
    val isMusicAutoPausedWaitingForMute: StateFlow<Boolean> = _isMusicAutoPausedWaitingForMute.asStateFlow()

    private val _videoVolume = MutableStateFlow(prefs.getFloat("key_video_volume", 1.0f))
    val videoVolume: StateFlow<Float> = _videoVolume.asStateFlow()

    private val _pendingVideoIndex = MutableStateFlow<Int?>(null)
    val pendingVideoIndex: StateFlow<Int?> = _pendingVideoIndex.asStateFlow()

    private val _playbackStepVersion = MutableStateFlow(0L)
    val playbackStepVersion: StateFlow<Long> = _playbackStepVersion.asStateFlow()

    private val _realVideoPositionMs = MutableStateFlow(0L)
    val realVideoPositionMs: StateFlow<Long> = _realVideoPositionMs.asStateFlow()

    private val _realVideoDurationMs = MutableStateFlow(0L)
    val realVideoDurationMs: StateFlow<Long> = _realVideoDurationMs.asStateFlow()

    private val _lastRealPlaybackTime = MutableStateFlow(0L)
    val lastRealPlaybackTime: StateFlow<Long> = _lastRealPlaybackTime.asStateFlow()

    private val _lastPreviewPlaybackTime = MutableStateFlow(0L)
    val lastPreviewPlaybackTime: StateFlow<Long> = _lastPreviewPlaybackTime.asStateFlow()

    fun reportPreviewPlaybackProgress(positionMs: Long, durationMs: Long) {
        val now = System.currentTimeMillis()
        _lastPreviewPlaybackTime.value = now
        if ((now - _lastRealPlaybackTime.value) >= 2500L) {
            _realVideoPositionMs.value = positionMs.coerceAtLeast(0L)
            if (durationMs > 0L) {
                _realVideoDurationMs.value = durationMs
            }
        }
    }

    private val _videoSeekEvent = MutableStateFlow<Pair<Long, Long>?>(null)
    val videoSeekEvent: StateFlow<Pair<Long, Long>?> = _videoSeekEvent.asStateFlow()

    private val _aiWatermarkReport = MutableStateFlow<AiWatermarkReport?>(null)
    val aiWatermarkReport: StateFlow<AiWatermarkReport?> = _aiWatermarkReport.asStateFlow()

    private val _isScanningAiWatermark = MutableStateFlow(false)
    val isScanningAiWatermark: StateFlow<Boolean> = _isScanningAiWatermark.asStateFlow()

    private val _isCleaningAiWatermark = MutableStateFlow(false)
    val isCleaningAiWatermark: StateFlow<Boolean> = _isCleaningAiWatermark.asStateFlow()

    suspend fun scanActiveVideoAiWatermark(pathOverride: String? = null): AiWatermarkReport? = withContext(Dispatchers.IO) {
        if (!_config.value.enableAiDetectionModule) {
            _aiWatermarkReport.value = null
            return@withContext null
        }
        val targetPath = pathOverride?.takeIf { it.isNotBlank() }
            ?: _config.value.currentVideo?.path?.takeIf { it.isNotBlank() }
            ?: _config.value.videoPath
        if (targetPath.isBlank()) {
            _aiWatermarkReport.value = null
            return@withContext null
        }
        _isScanningAiWatermark.value = true
        try {
            val report = AiWatermarkInspector.scanVideoFile(targetPath)
            _aiWatermarkReport.value = report
            report
        } finally {
            _isScanningAiWatermark.value = false
        }
    }

    suspend fun toggleAiDetectionModule(enabled: Boolean? = null): Boolean {
        val next = enabled ?: !_config.value.enableAiDetectionModule
        updateConfig { it.copy(enableAiDetectionModule = next) }
        if (!next) {
            _aiWatermarkReport.value = null
        } else {
            repoScope.launch(Dispatchers.IO) {
                scanActiveVideoAiWatermark()
            }
        }
        return next
    }

    suspend fun toggleQuranAudioModule(enabled: Boolean? = null): Boolean {
        val next = enabled ?: !_config.value.enableQuranAudioModule
        updateConfig { it.copy(enableQuranAudioModule = next) }
        return next
    }

    suspend fun setLiveStreamSource(active: Boolean, url: String? = null, title: String? = null): Boolean {
        val finalUrl = url?.takeIf { it.isNotBlank() } ?: _config.value.liveStreamUrl
        val finalTitle = title?.takeIf { it.isNotBlank() } ?: _config.value.liveStreamTitle
        updateConfig {
            it.copy(
                isLiveStreamSourceActive = active,
                liveStreamUrl = finalUrl,
                liveStreamTitle = finalTitle,
                liveStreamStatus = if (active) "ACTIVE" else "STANDBY"
            )
        }
        writeHookConfigFiles(_config.value)
        broadcastControlEvent(
            action = "SET_LIVE_STREAM_SOURCE",
            extraSource = "LIVE_STREAM_RELAY"
        )
        return active
    }

    suspend fun toggleLiveStreamSource(): Boolean {
        val next = !_config.value.isLiveStreamSourceActive
        return setLiveStreamSource(next)
    }

    suspend fun refreshLiveStream() {
        writeHookConfigFiles(_config.value)
        broadcastControlEvent(
            action = "RELOAD_MEDIA",
            extraSource = "LIVE_STREAM_RELAY"
        )
    }

    fun adjustMakkahPan(deltaX: Float, deltaY: Float) {
        val curX = _config.value.sessionDnaPanX
        val curY = _config.value.sessionDnaPanY
        val zoom = _config.value.sessionDnaCustomZoom.coerceAtLeast(1.05f)
        val newX = ((curX + deltaX) * 100).toInt() / 100f
        val newY = ((curY + deltaY) * 100).toInt() / 100f
        setSessionDnaFraming(zoom = zoom, panX = newX, panY = newY, registerToHistory = false, enableDnaZoom = true)
    }

    fun resetMakkahFraming() {
        updateConfigSync {
            it.copy(
                zoomFactor = 1.0f,
                sessionDnaCustomZoom = 1.05f,
                sessionDnaPanX = 0.0f,
                sessionDnaPanY = 0.0f,
                scaleMode = com.tajasb.model.ScaleMode.CROP
            )
        }
        broadcastControlEvent(action = "UPDATE_CONFIG")
    }

    suspend fun toggleDnaModule(enabled: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val next = enabled ?: !_config.value.enableDnaModule
        updateConfig {
            it.copy(enableDnaModule = next)
        }
        withContext(Dispatchers.Main) {
            musicPlayer.setSessionDnaEnabled(next)
            musicPlayer.setSessionDna(_config.value.sessionDnaIndex, _config.value.sessionDnaSeed)
        }
        broadcastSessionDnaEvent(_config.value.sessionDnaIndex, _config.value.sessionDnaSeed, next)
        next
    }

    suspend fun toggleDnaZoom(enabled: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val next = enabled ?: !_config.value.isDnaZoomEnabled
        updateConfig { it.copy(isDnaZoomEnabled = next) }
        broadcastControlEvent(action = "SET_SESSION_DNA_FRAMING")
        next
    }

    fun setDnaZoomEnabled(enabled: Boolean) {
        updateConfigSync { it.copy(isDnaZoomEnabled = enabled) }
        broadcastControlEvent(action = "SET_SESSION_DNA_FRAMING")
    }

    suspend fun cleanActiveVideoAiWatermark(): AiWatermarkCleanResult = withContext(Dispatchers.IO) {
        if (!_config.value.enableAiDetectionModule) {
            return@withContext AiWatermarkCleanResult(
                success = false,
                cleanedFilePath = null,
                bytesRemoved = 0L,
                message = "⚠️ Modul Deteksi Kode AI sedang nonaktif (Mode Eksperimental/Dev).",
                updatedReport = null
            )
        }
        val currentCfg = _config.value
        val activeIdx = currentCfg.currentVideoIndex.coerceIn(0, (currentCfg.playlist.size - 1).coerceAtLeast(0))
        val activeItem = currentCfg.playlist.getOrNull(activeIdx)
        val targetPath = activeItem?.path?.takeIf { it.isNotBlank() } ?: currentCfg.videoPath
        if (targetPath.isBlank() || !File(targetPath).exists()) {
            return@withContext AiWatermarkCleanResult(
                success = false,
                cleanedFilePath = null,
                bytesRemoved = 0L,
                message = "⚠️ Pilih video terlebih dahulu sebelum membersihkan kode AI.",
                updatedReport = null
            )
        }

        _isCleaningAiWatermark.value = true
        try {
            val outDir = getInternalCamera1Directory()
            val cleanRes = AiWatermarkInspector.cleanVideoMetadataAndRemux(
                context = context,
                sourcePath = targetPath,
                outputDir = outDir
            )
            if (cleanRes.success && !cleanRes.cleanedFilePath.isNullOrBlank()) {
                val cleanedFile = File(cleanRes.cleanedFilePath)
                val updatedList = currentCfg.playlist.mapIndexed { idx, item ->
                    if (idx == activeIdx || item.path == targetPath) {
                        val baseCleanName = if (item.name.contains("[Clean-AI]", true)) {
                            item.name
                        } else {
                            "${item.name.substringBeforeLast(".")} [Clean-AI].mp4"
                        }
                        item.copy(
                            name = baseCleanName,
                            path = cleanedFile.absolutePath,
                            sizeBytes = cleanedFile.length()
                        )
                    } else {
                        item
                    }
                }

                // Also ensure Real-Time Anti-Hash Visual Shader + Audio Anti-Hash are enabled
                val nextAntiHash = if (currentCfg.antiHashMode == com.tajasb.model.AntiHashIntensity.OFF) {
                    com.tajasb.model.AntiHashIntensity.MEDIUM
                } else {
                    currentCfg.antiHashMode
                }

                updateConfig {
                    it.copy(
                        playlist = updatedList,
                        videoPath = cleanedFile.absolutePath,
                        antiHashMode = nextAntiHash,
                        isAudioAntiHashEnabled = true
                    )
                }
                rotateToNextSessionDna("ai_watermark_clean")
                bumpPlaylistVersion()
                bumpPlaybackStepVersion()

                val refreshedCfg = _config.value
                writeHookConfigFiles(refreshedCfg)
                try {
                    copyToAllLocations(cleanedFile, "virtual.mp4")
                } catch (_: Exception) {}

                broadcastControlEvent(
                    action = "SELECT_VIDEO",
                    videoPath = cleanedFile.absolutePath,
                    isVideoPaused = false,
                    isVideoAudioMuted = refreshedCfg.isVideoAudioMuted,
                    extraSource = "AI_WATERMARK_CLEANED",
                    extraPurgeCache = true
                )

                _aiWatermarkReport.value = cleanRes.updatedReport
            }
            cleanRes
        } finally {
            _isCleaningAiWatermark.value = false
        }
    }

    fun setVideoVolume(vol: Float) {
        val safe = vol.coerceIn(0f, 1f)
        _videoVolume.value = safe
        prefs.edit().putFloat("key_video_volume", safe).apply()
        broadcastControlEvent(action = "UPDATE_CONFIG", videoVolume = safe)
    }

    fun seekVideo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        _realVideoPositionMs.value = safePos
        _videoSeekEvent.value = Pair(safePos, System.currentTimeMillis())
        try {
            com.tajasb.infinity.IfsManager.getInstance(context).onVideoSeeked(safePos)
        } catch (_: Throwable) {}
        broadcastControlEvent(action = "SEEK_VIDEO", videoSeekMs = safePos)
    }

    @Volatile
    var playlistVersion: Long = prefs.getLong("key_playlist_version", System.currentTimeMillis())
        private set

    fun bumpPlaylistVersion() {
        val ver = System.currentTimeMillis()
        lastInternalConfigUpdateMs = ver
        playlistVersion = ver
        prefs.edit().putLong("key_playlist_version", ver).apply()
    }

    @Volatile
    private var lastNextVideoTimeMs: Long = 0L

    @Volatile
    var lastVideoSwitchTimestampMs: Long = 0L

    @Volatile
    private var wasMusicAutoPausedByVideo = false

    // Non-repeating Shuffle Cycle (delegated to VideoPlaylistRepository.ShuffleDeckManager)
    private val shuffleDeckManager = VideoPlaylistRepository.ShuffleDeckManager()

    fun resetShuffleDeck() = shuffleDeckManager.reset()

    private fun getNextShuffledIndex(candidateIndices: List<Int>, currentIdx: Int): Int =
        shuffleDeckManager.getNextShuffledIndex(candidateIndices, currentIdx)

    private fun getPrevShuffledIndex(candidateIndices: List<Int>, currentIdx: Int): Int =
        shuffleDeckManager.getPrevShuffledIndex(candidateIndices, currentIdx)

    private fun onVideoSelectionChanged(selectedIndex: Int, isMuted: Boolean) {
        shuffleDeckManager.removeIndex(selectedIndex)
        // Handle music pause / resume asynchronously so Binder IPC / Main Thread is NEVER blocked
        repoScope.launch(Dispatchers.Main) {
            try {
                if (_config.value.isIndependentAudioEnabled || _config.value.isDualAudioMixingEnabled) {
                    // Mode Audio Mandiri / Dual Audio: Audio MP3 berjalan mandiri tanpa auto-pause oleh video
                    wasMusicAutoPausedByVideo = false
                    _isMusicAutoPausedWaitingForMute.value = false
                    return@launch
                }
                val isIfsActive = try {
                    com.tajasb.infinity.IfsManager.getInstance(context).isIfsActive.value
                } catch (_: Throwable) { false } || _config.value.isIfsMode
                if (isIfsActive) {
                    // Dalam mode IFS, kontrol audio dikelola penuh oleh sistem AX + SR
                    return@launch
                }
                if (!isMuted) {
                    if (musicPlayer.isPlaying.value) {
                        wasMusicAutoPausedByVideo = true
                        _isMusicAutoPausedWaitingForMute.value = true
                        musicPlayer.pause()
                        updateConfig { it.copy(isMp3AudioPaused = true) }
                        broadcastControlEvent(action = "TOGGLE_PAUSE_MP3_AUDIO", isMp3AudioPaused = true)
                    }
                } else {
                    if (wasMusicAutoPausedByVideo) {
                        wasMusicAutoPausedByVideo = false
                        _isMusicAutoPausedWaitingForMute.value = false
                        playAudio()
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    init {
        if (!prefs.getBoolean("key_vcam_defaults_migrated_v3_sepuh", false)) {
            prefs.edit()
                .putFloat("floating_alpha", 0.86f)
                .putBoolean("mod_prayer_comment", false)
                .putBoolean("mod_auto_tap", false)
                .putBoolean("mod_osd", false)
                .putBoolean("mod_sales_notif", true)
                .putBoolean("mod_sticker", true)
                .putBoolean("mod_watermark", true)
                .putBoolean("mod_mp3_music", true)
                .putBoolean("master_spill_mode", false)
                .putBoolean("auto_spill_comments", false)
                .putBoolean("bridge_cycle_mode", false)
                .putBoolean("key_vcam_defaults_migrated_v3_sepuh", true)
                .apply()
            _config.value = loadConfig()
        }
        if (!prefs.getBoolean("key_vcam_defaults_all_off_except_music_v1", false)) {
            prefs.edit()
                .putBoolean("mod_sales_notif", false)
                .putBoolean("mod_sticker", false)
                .putBoolean("mod_watermark", false)
                .putBoolean("mod_auto_time", false)
                .putBoolean("mod_prayer_comment", false)
                .putBoolean("mod_quran_khatam", false)
                .putBoolean("mod_auto_tap", false)
                .putBoolean("mod_osd", false)
                .putBoolean("mod_quran_audio", false)
                .putBoolean("mod_dna", false)
                .putBoolean("mod_ai_detection", false)
                .putBoolean("mod_makkah_relay", false)
                .putBoolean("mod_live_event_broker", false)
                .putBoolean("mod_scan_inspector", false)
                .putBoolean("mod_mp3_music", true)
                .putBoolean("key_vcam_defaults_all_off_except_music_v1", true)
                .apply()
            _config.value = loadConfig()
        }
        if (!prefs.getBoolean("key_vcam_anti_pl_v4_migrated", false)) {
            prefs.edit()
                .putString("anti_hash_mode", com.tajasb.model.AntiHashIntensity.OFF.name)
                .putBoolean("audio_anti_hash_enabled", false)
                .putBoolean("key_vcam_anti_pl_v4_migrated", true)
                .apply()
            _config.value = loadConfig()
        }
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        ensureDefaultFolders()
        syncInitialMediaState()

        // Bersihkan ID Tipe 3 yang sudah tidak ada di daftar audio agar tidak pernah nyangkut di "Menyiapkan jeda acak..."
        val validAudioIds = _config.value.audioPlaylist.map { it.id }.toSet()
        val cleanedL3Ids = _config.value.audioLayer3Ids.filter { it in validAudioIds }.toSet()
        if (cleanedL3Ids.size != _config.value.audioLayer3Ids.size) {
            prefs.edit().putStringSet("audio_layer3_ids", cleanedL3Ids).apply()
            _config.value = _config.value.copy(audioLayer3Ids = cleanedL3Ids)
        }

        // Pasang callback playlist (termasuk allAudioPlaylist untuk Tipe 3) SEBELUM updateLayer3Config
        musicPlayer.setPlaylistCallbacks(
            playlist = { _config.value.type1Playlist },
            currentIndex = {
                val t1List = _config.value.type1Playlist
                val cur = _config.value.currentAudio
                val found = t1List.indexOfFirst { it.id == cur?.id }
                if (found >= 0) found else 0
            },
            onTrackChange = { newIdx ->
                val t1List = _config.value.type1Playlist
                val item = t1List.getOrNull(newIdx)
                if (item != null) {
                    val fullIdx = _config.value.audioPlaylist.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                    repoScope.launch {
                        updateConfig {
                            it.copy(
                                currentAudioIndex = fullIdx,
                                audioPath = item.path,
                                audioName = item.name,
                                audioUri = item.uri
                            )
                        }
                    }
                }
            },
            allAudioPlaylist = { _config.value.audioPlaylist }
        )

        musicPlayer.setAudioAntiHash(
            _config.value.isAudioAntiHashEnabled,
            _config.value.audioAntiHashMode,
            _config.value.audioAntiHashPercent
        )
        musicPlayer.setSessionDna(
            _config.value.sessionDnaIndex,
            _config.value.sessionDnaSeed,
            enabled = _config.value.enableDnaModule
        )
        musicPlayer.setVoiceEffect(_config.value.voiceEffect)
        musicPlayer.setIndependentAudioMode(_config.value.isIndependentAudioEnabled)
        musicPlayer.setHumanVolumeFluctuation(
            _config.value.isHumanVolumeFluctuationEnabled,
            _config.value.humanFluctuationIntensity
        )
        val initialL2Track = _config.value.currentAudioLayer2
        if (initialL2Track != null) {
            musicPlayer.setLayer2Track(initialL2Track, autoStartIfActive = false)
        }
        musicPlayer.setVolumeLayer2(_config.value.audioLayer2Volume)
        musicPlayer.setMutedLayer2(_config.value.isAudioLayer2Muted)
        musicPlayer.setLoopModeLayer2(_config.value.isAudioLayer2Looping)
        musicPlayer.setLayer2Active(_config.value.isAudioLayer2Enabled, autoStartIfLayer1Running = false)
        musicPlayer.updateLayer3Config(
            enabled = _config.value.isAudioLayer3Enabled,
            ringtoneIds = _config.value.audioLayer3Ids,
            volume = _config.value.audioLayer3Volume,
            isMuted = _config.value.isAudioLayer3Muted,
            minIntervalSec = _config.value.audioLayer3MinIntervalSec,
            maxIntervalSec = _config.value.audioLayer3MaxIntervalSec
        )
        try {
            com.tajasb.stiker.CustomStikerManager.getInstance(context)
            com.tajasb.stiker.CustomWatermarkManager.getInstance(context)
            com.tajasb.notif.BuyerNotificationManager.getInstance(context)
            com.tajasb.notif.AutoTimeCapsuleManager.getInstance(context)
        } catch (_: Throwable) {}

        try {
            val videoCompletedReceiver = object : BroadcastReceiver() {
                private var lastAutoNextTime = 0L
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "com.tajasb.action.VIDEO_COMPLETED") {
                        val now = System.currentTimeMillis()
                        if (now - lastAutoNextTime < 700L) {
                            return
                        }
                        lastAutoNextTime = now

                        val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(context ?: this@VcamRepository.context) } catch (_: Throwable) { null }
                        val isIfsActive = ifsMgr?.isIfsActive?.value == true || _config.value.isIfsMode

                        if ((isIfsActive || _config.value.autoNextEnabled || _pendingVideoIndex.value != null || _config.value.isMasterSpillMode || _config.value.isBridgeCycleMode) && _config.value.playlist.isNotEmpty()) {
                            if (isIfsActive) {
                                Log.i("VcamRepository", "♾️ [IFS] Video selesai diterima dari hook! Memajukan siklus Infinity System...")
                                ifsMgr?.onVideoCompleted()
                                return
                            }
                            if (_config.value.isBridgeCycleMode) {
                                Log.i("VcamRepository", "🔀 [BRIDGE-CYCLE] Video selesai diterima dari hook! Memajukan fase siklus...")
                                repoScope.launch {
                                    advanceBridgeCycle(broadcastToHook = true)
                                }
                                return
                            }
                            val extraSource = intent.getStringExtra("extra_source")
                            val targetIndex = intent.getIntExtra("extra_current_index", -1)
                            if (extraSource == "SEAMLESS_PRELOAD") {
                                if (targetIndex >= 0 && targetIndex in _config.value.playlist.indices && targetIndex != _config.value.currentVideoIndex) {
                                    repoScope.launch {
                                        syncVideoIndexWithoutBroadcast(targetIndex)
                                    }
                                }
                                return
                            }
                            if (_config.value.isMasterSpillMode) {
                                Log.i("VcamRepository", "🎯 [MASTER-LOOP] Video selesai diterima dari hook! Memajukan sistem Master Loop...")
                                repoScope.launch {
                                    advanceMasterSpill(broadcastToHook = true)
                                }
                                return
                            }
                            if (targetIndex >= 0 && targetIndex == _config.value.currentVideoIndex) {
                                // Already synchronized by ContentProvider synchronous call
                                return
                            }
                            Log.i("VcamRepository", "Received VIDEO_COMPLETED broadcast from hook (targetIndex=$targetIndex, mode=${_config.value.autoNextMode.name}). Advancing playlist...")
                            repoScope.launch {
                                if (targetIndex >= 0 && targetIndex in _config.value.playlist.indices) {
                                    syncVideoIndexWithoutBroadcast(targetIndex, broadcastPreload = false)
                                } else {
                                    nextVideo(_config.value.autoNextMode, broadcastToHook = false)
                                }
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter("com.tajasb.action.VIDEO_COMPLETED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(videoCompletedReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(videoCompletedReceiver, filter)
            }

            val playbackPositionReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "com.tajasb.action.PLAYBACK_POSITION") {
                        val posMs = intent.getLongExtra("extra_position_ms", 0L)
                        val durMs = intent.getLongExtra("extra_duration_ms", 0L)

                        _realVideoPositionMs.value = posMs
                        if (durMs > 0L) {
                            _realVideoDurationMs.value = durMs
                        }
                        _lastRealPlaybackTime.value = System.currentTimeMillis()
                        lastGlobalPlaybackTimeMs = System.currentTimeMillis()
                        notifyCameraStreamHeartbeat()
                    }
                }
            }
            val posFilter = IntentFilter("com.tajasb.action.PLAYBACK_POSITION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(playbackPositionReceiver, posFilter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(playbackPositionReceiver, posFilter)
            }
        } catch (_: Exception) {}
    }

    fun isXposedModuleActive(): Boolean {
        return try {
            isHookedIndicator()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Target hook method for LSPosed/EdXposed module to replace and return true
     */
    fun isHookedIndicator(): Boolean {
        return false
    }

    fun getCamera1Directory(): File {
        val dir = context.getExternalFilesDir("Camera1") ?: getInternalCamera1Directory()
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val nomedia = File(dir, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile()
        } catch (_: Exception) {}
        return dir
    }

    fun getInternalCamera1Directory(): File {
        val dir = File(context.filesDir, "Camera1")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        try {
            val nomedia = File(dir, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile()
        } catch (_: Exception) {}
        return dir
    }

    private fun ensureDefaultFolders() {
        try { getCamera1Directory() } catch (_: Exception) {}
        try { getInternalCamera1Directory() } catch (_: Exception) {}
        repoScope.launch(Dispatchers.IO) {
            try {
                val internalVault = ConceptBackupManager.getInstance(context).getInternalHiddenVaultDir()
                MediaFileStorageHelper.purgePublicGalleryDuplicates(
                    context = context,
                    internalCamera1Dir = getInternalCamera1Directory(),
                    internalVaultDir = internalVault
                )
            } catch (_: Exception) {}
        }
    }

    suspend fun cleanGalleryDuplicates(): GalleryDuplicateCleanupReport = withContext(Dispatchers.IO) {
        val conceptMgr = ConceptBackupManager.getInstance(context)
        val pubReport = MediaFileStorageHelper.purgePublicGalleryDuplicates(
            context = context,
            internalCamera1Dir = getInternalCamera1Directory(),
            internalVaultDir = conceptMgr.getInternalHiddenVaultDir()
        )
        val vaultReport = conceptMgr.deduplicateAndCleanVault()
        GalleryDuplicateCleanupReport(
            deletedFilesCount = pubReport.deletedFilesCount + vaultReport.mergedDuplicateCount + vaultReport.deletedOrphanCount,
            freedBytes = pubReport.freedBytes + vaultReport.freedBytes,
            migratedVaultFilesCount = pubReport.migratedVaultFilesCount,
            cleanedFoldersCount = pubReport.cleanedFoldersCount
        )
    }

    private fun loadPlaylistFromPrefs(): List<VideoItem> =
        VideoPlaylistRepository.loadPlaylistFromPrefs(prefs)

    private fun serializePlaylistToJson(list: List<VideoItem>): String =
        VideoPlaylistRepository.serializePlaylistToJson(list)

    private fun serializeAudioPlaylistToJson(list: List<com.tajasb.model.AudioItem>): String =
        AudioPlaylistRepository.serializeAudioPlaylistJson(list)

    private fun savePlaylistToPrefs(list: List<VideoItem>) =
        VideoPlaylistRepository.savePlaylistToPrefs(prefs, list)

    private fun loadAudioPlaylistFromPrefs(): List<com.tajasb.model.AudioItem> =
        AudioPlaylistRepository.parseAudioPlaylistJson(prefs.getString("audio_playlist_json", "") ?: "")

    private fun saveAudioPlaylistToPrefs(list: List<com.tajasb.model.AudioItem>) {
        prefs.edit().putString("audio_playlist_json", AudioPlaylistRepository.serializeAudioPlaylistJson(list)).apply()
    }

    private fun loadConfig(): VcamConfig {
        val scaleModeName = prefs.getString("scale_mode", ScaleMode.CROP.name) ?: ScaleMode.CROP.name
        val resPresetName = prefs.getString("force_res", ResolutionPreset.AUTO.name) ?: ResolutionPreset.AUTO.name
        val hookEngineName = prefs.getString("hook_engine", HookEngineType.HYBRID.name) ?: HookEngineType.HYBRID.name
        val switchModeName = prefs.getString("video_switch_mode", com.tajasb.model.VideoSwitchMode.INSTANT.name) ?: com.tajasb.model.VideoSwitchMode.INSTANT.name
        val targetSet = prefs.getStringSet("target_packages", null) ?: emptySet()
        val playlist = loadPlaylistFromPrefs()
        val currentIndex = prefs.getInt("current_video_index", 0).coerceIn(0, (playlist.size - 1).coerceAtLeast(0))

        val currentPath = if (playlist.isNotEmpty() && currentIndex in playlist.indices) {
            playlist[currentIndex].path
        } else {
            prefs.getString("video_path", "") ?: ""
        }

        val audioPlaylist = loadAudioPlaylistFromPrefs()
        val currentAudioIndex = prefs.getInt("current_audio_index", 0).coerceIn(0, (audioPlaylist.size - 1).coerceAtLeast(0))

        val currentAudioPath = if (audioPlaylist.isNotEmpty() && currentAudioIndex in audioPlaylist.indices) {
            audioPlaylist[currentAudioIndex].path
        } else {
            prefs.getString("audio_path", "") ?: ""
        }
        val currentAudioName = if (audioPlaylist.isNotEmpty() && currentAudioIndex in audioPlaylist.indices) {
            audioPlaylist[currentAudioIndex].name
        } else {
            prefs.getString("audio_name", "") ?: ""
        }

        return VcamConfig(
            isMasterEnabled = prefs.getBoolean("master_enabled", true),
            videoPath = currentPath,
            videoUri = prefs.getString("video_uri", null),
            audioPath = currentAudioPath,
            audioUri = prefs.getString("audio_uri", null),
            audioName = currentAudioName,
            playlist = playlist,
            currentVideoIndex = currentIndex,
            audioPlaylist = audioPlaylist,
            currentAudioIndex = currentAudioIndex,
            rotation = prefs.getInt("rotation", 0),
            isMirrored = prefs.getBoolean("mirrored", false),
            isVerticalFlipped = prefs.getBoolean("vertical_flipped", false),
            zoomFactor = prefs.getFloat("zoom_factor", 1.0f).coerceIn(0.5f, 3.0f),
            scaleMode = try { ScaleMode.valueOf(scaleModeName) } catch (_: Exception) { ScaleMode.CROP },
            audioEnabled = prefs.getBoolean("audio_enabled", true),
            loopPlayback = prefs.getBoolean("loop_playback", true),
            autoNextEnabled = prefs.getBoolean("auto_next_enabled", true),
            autoNextMode = try {
                AutoNextMode.valueOf(prefs.getString("auto_next_mode", AutoNextMode.SEQUENTIAL.name) ?: AutoNextMode.SEQUENTIAL.name)
            } catch (_: Exception) { AutoNextMode.SEQUENTIAL },
            autoNextLeadSeconds = (prefs.getInt("auto_next_lead_ms", prefs.getInt("auto_next_lead_seconds", 2) * 1000) / 1000).coerceIn(1, 15),
            autoNextLeadMs = prefs.getInt("auto_next_lead_ms", prefs.getInt("auto_next_lead_seconds", 2) * 1000).coerceIn(50, 15000),
            forceResolution = try { ResolutionPreset.valueOf(resPresetName) } catch (_: Exception) { ResolutionPreset.AUTO },
            customWidth = prefs.getInt("custom_w", 1280),
            customHeight = prefs.getInt("custom_h", 720),
            fps = prefs.getInt("fps", 30),
            targetPackages = HashSet(targetSet),
            primaryTargetPackage = prefs.getString("primary_target_package", "") ?: "",
            isFloatingControlEnabled = prefs.getBoolean("floating_enabled", false),
            floatingAlpha = prefs.getFloat("floating_alpha", 0.86f).coerceIn(0.50f, 1.0f),
            isVideoPaused = prefs.getBoolean("video_paused", false),
            isVideoAudioMuted = if (playlist.isNotEmpty() && currentIndex in playlist.indices) {
                playlist[currentIndex].isMuted
            } else {
                prefs.getBoolean("video_audio_muted", false)
            },
            isMp3AudioPaused = prefs.getBoolean("mp3_audio_paused", true),
            hookEngine = try { HookEngineType.valueOf(hookEngineName) } catch (_: Exception) { HookEngineType.HYBRID },
            videoSwitchMode = try { com.tajasb.model.VideoSwitchMode.valueOf(switchModeName) } catch (_: Exception) { com.tajasb.model.VideoSwitchMode.INSTANT },
            isLightweightMode = prefs.getBoolean("lightweight_mode", false),
            isDebugOsdInVirtualCameraEnabled = prefs.getBoolean("debug_osd_enabled", false),
            isMasterSpillMode = prefs.getBoolean("master_spill_mode", false),
            masterVideoId = prefs.getString("master_video_id", null),
            masterSpillWaitFinish = prefs.getBoolean("master_spill_wait_finish", true),
            activeSpillVideoId = prefs.getString("active_spill_video_id", null),
            autoSpillFromComments = prefs.getBoolean("auto_spill_comments", false),
            isBridgeCycleMode = prefs.getBoolean("bridge_cycle_mode", false),
            isIfsMode = prefs.getBoolean("is_ifs_mode", false) || prefs.getBoolean("ifs_enabled", false),
            bridge1VideoId = prefs.getString("bridge1_video_id", null),
            bridge2VideoId = prefs.getString("bridge2_video_id", null),
            currentBridgePhase = try {
                BridgeCyclePhase.valueOf(prefs.getString("current_bridge_phase", BridgeCyclePhase.OFF.name) ?: BridgeCyclePhase.OFF.name)
            } catch (_: Exception) { BridgeCyclePhase.OFF },
            pendingBridgeContentIndex = if (prefs.contains("pending_bridge_content_idx")) prefs.getInt("pending_bridge_content_idx", -1).takeIf { it >= 0 } else null,
            bridgeContentQueue = prefs.getString("bridge_content_queue", "")?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
            enableSalesNotifModule = prefs.getBoolean("mod_sales_notif", false),
            enableStickerModule = prefs.getBoolean("mod_sticker", false),
            enableWatermarkModule = prefs.getBoolean("mod_watermark", false),
            enableAutoTimeModule = prefs.getBoolean("mod_auto_time", false),
            enablePrayerCommentModule = prefs.getBoolean("mod_prayer_comment", false),
            enableQuranKhatamModule = prefs.getBoolean("mod_quran_khatam", false),
            enableAutoTapModule = prefs.getBoolean("mod_auto_tap", false),
            enableMp3MusicModule = prefs.getBoolean("mod_mp3_music", true),
            enableOsdModule = prefs.getBoolean("mod_osd", false),
            enableQuranAudioModule = prefs.getBoolean("mod_quran_audio", false),
            enableMakkahRelayModule = prefs.getBoolean("mod_makkah_relay", false),
            enableLiveEventBrokerModule = prefs.getBoolean("mod_live_event_broker", true),
            enableScanInspectorModule = prefs.getBoolean("mod_scan_inspector", false),
            isLiveFocusVpnEnabled = prefs.getBoolean("live_focus_vpn_enabled", false),
            isAutoRamCleanEnabled = prefs.getBoolean("auto_ram_clean_enabled", true),
            fpsGovernorLimit = prefs.getInt("fps_governor_limit", 30),
            lastFreedRamMb = prefs.getLong("last_freed_ram_mb", 0L),
            lastRamCleanTimestamp = prefs.getLong("last_ram_clean_time", 0L),
            ramCleanWhitelistPackages = prefs.getStringSet("ram_clean_whitelist", emptySet()) ?: emptySet(),
            vpnBypassPackages = prefs.getStringSet("vpn_bypass_packages", emptySet()) ?: emptySet(),
            blockedAdPackages = prefs.getStringSet("blocked_ad_packages", emptySet()) ?: emptySet(),
            isAdBlockerActive = prefs.getBoolean("ad_blocker_active", false),
            adBlockerAutoFreeze = prefs.getBoolean("ad_blocker_auto_freeze", true),
            antiHashMode = try {
                com.tajasb.model.AntiHashIntensity.valueOf(prefs.getString("anti_hash_mode", com.tajasb.model.AntiHashIntensity.OFF.name) ?: com.tajasb.model.AntiHashIntensity.OFF.name)
            } catch (_: Exception) { com.tajasb.model.AntiHashIntensity.OFF },
            isSafeCodecGuardActive = prefs.getBoolean("safe_codec_guard_active", true),
            isAudioAntiHashEnabled = prefs.getBoolean("audio_anti_hash_enabled", false),
            audioAntiHashMode = try {
                com.tajasb.model.AudioAntiHashIntensity.valueOf(prefs.getString("audio_anti_hash_mode", com.tajasb.model.AudioAntiHashIntensity.MEDIUM.name) ?: com.tajasb.model.AudioAntiHashIntensity.MEDIUM.name)
            } catch (_: Exception) { com.tajasb.model.AudioAntiHashIntensity.MEDIUM },
            voiceEffect = try {
                com.tajasb.model.VoiceEffect.valueOf(prefs.getString("voice_effect", com.tajasb.model.VoiceEffect.NORMAL.name) ?: com.tajasb.model.VoiceEffect.NORMAL.name)
            } catch (_: Exception) { com.tajasb.model.VoiceEffect.NORMAL },
            isAutoRocketRecoveryEnabled = prefs.getBoolean("auto_rocket_recovery_enabled", true),
            autoRecoveryStallThresholdSec = prefs.getInt("auto_recovery_stall_threshold_sec", 4).coerceIn(2, 15),
            autoRecoveryCount = prefs.getInt("auto_recovery_count", 0),
            lastAutoRecoveryTimestamp = prefs.getLong("last_auto_recovery_timestamp", 0L),
            isPeriodicRamCleanEnabled = prefs.getBoolean("periodic_ram_clean_enabled", true),
            periodicRamCleanIntervalMinutes = prefs.getInt("periodic_ram_clean_interval_min", 30).coerceIn(5, 120),
            sessionDnaIndex = prefs.getInt("session_dna_index", 1).coerceAtLeast(1),
            sessionDnaSeed = prefs.getLong("session_dna_seed", 1001L),
            isDualAudioMixingEnabled = prefs.getBoolean("dual_audio_mixing_enabled", true),
            isIndependentAudioEnabled = prefs.getBoolean("independent_audio_enabled", false),
            isHumanVolumeFluctuationEnabled = prefs.getBoolean("human_volume_fluctuation_enabled", false),
            humanFluctuationIntensity = prefs.getFloat("human_fluctuation_intensity", 0.20f),
            isAudioLayer2Enabled = prefs.getBoolean("audio_layer2_enabled", false),
            audioLayer2Id = prefs.getString("audio_layer2_id", null),
            audioLayer2Path = prefs.getString("audio_layer2_path", "") ?: "",
            audioLayer2Name = prefs.getString("audio_layer2_name", "") ?: "",
            audioLayer2Volume = prefs.getFloat("audio_layer2_volume", 0.70f),
            isAudioLayer2Muted = prefs.getBoolean("audio_layer2_muted", false),
            isAudioLayer2Looping = prefs.getBoolean("audio_layer2_looping", true),
            isAudioLayer3Enabled = prefs.getBoolean("audio_layer3_enabled", false),
            audioLayer3Ids = prefs.getStringSet("audio_layer3_ids", emptySet()) ?: emptySet(),
            audioLayer3Volume = prefs.getFloat("audio_layer3_volume", 0.85f),
            isAudioLayer3Muted = prefs.getBoolean("audio_layer3_muted", false),
            audioLayer3MinIntervalSec = prefs.getInt("audio_layer3_min_interval_sec", 10),
            audioLayer3MaxIntervalSec = prefs.getInt("audio_layer3_max_interval_sec", 25),
            enableDnaModule = prefs.getBoolean("mod_dna", false),
            enableAiDetectionModule = prefs.getBoolean("mod_ai_detection", false),
            videoAntiHashPercent = prefs.getFloat("video_anti_hash_percent", 1.5f),
            audioAntiHashPercent = prefs.getFloat("audio_anti_hash_percent", 2.0f),
            isAudioLayer2SyncAntiHash = prefs.getBoolean("audio_layer2_sync_anti_hash", true),
            isDnaZoomEnabled = prefs.getBoolean("dna_zoom_enabled", false),
            sessionDnaCustomZoom = prefs.getFloat("session_dna_custom_zoom", 1.08f),
            sessionDnaPanX = prefs.getFloat("session_dna_pan_x", 0.0f),
            sessionDnaPanY = prefs.getFloat("session_dna_pan_y", 0.0f),
            usedDnaZoomHistory = prefs.getStringSet("used_dna_zoom_history", emptySet()) ?: emptySet(),
            isLiveStreamSourceActive = prefs.getBoolean("live_stream_source_active", false),
            liveStreamUrl = prefs.getString("live_stream_url", "https://makkahlive.net/makkahlive.aspx") ?: "https://makkahlive.net/makkahlive.aspx",
            liveStreamTitle = prefs.getString("live_stream_title", "Makkah Live 24 Jam (Masjidil Haram)") ?: "Makkah Live 24 Jam (Masjidil Haram)",
            liveStreamStatus = prefs.getString("live_stream_status", "STANDBY") ?: "STANDBY"
        )
    }

    private fun persistConfigToPrefsAndHooks(newConfig: VcamConfig, writeHooks: Boolean = true) {
        isInternalPersistenceInProgress = true
        lastInternalConfigUpdateMs = System.currentTimeMillis()
        try {
            prefs.edit().apply {
                putBoolean("master_enabled", newConfig.isMasterEnabled)
                putString("video_path", newConfig.videoPath)
                putString("video_uri", newConfig.videoUri)
                putString("audio_path", newConfig.audioPath)
                putString("audio_uri", newConfig.audioUri)
                putString("audio_name", newConfig.audioName)
                putInt("current_video_index", newConfig.currentVideoIndex)
                putInt("current_audio_index", newConfig.currentAudioIndex)
                putInt("rotation", newConfig.rotation)
                putBoolean("mirrored", newConfig.isMirrored)
                putBoolean("vertical_flipped", newConfig.isVerticalFlipped)
                putFloat("zoom_factor", newConfig.zoomFactor)
                putString("scale_mode", newConfig.scaleMode.name)
                putBoolean("audio_enabled", newConfig.audioEnabled)
                putBoolean("loop_playback", newConfig.loopPlayback)
                putBoolean("auto_next_enabled", newConfig.autoNextEnabled)
                putString("auto_next_mode", newConfig.autoNextMode.name)
                putString("video_switch_mode", newConfig.videoSwitchMode.name)
                putInt("auto_next_lead_seconds", newConfig.autoNextLeadSeconds)
                putInt("auto_next_lead_ms", newConfig.autoNextLeadMs)
                putString("force_res", newConfig.forceResolution.name)
                putInt("custom_w", newConfig.customWidth)
                putInt("custom_h", newConfig.customHeight)
                putInt("fps", newConfig.fps)
                putStringSet("target_packages", HashSet(newConfig.targetPackages))
                putString("primary_target_package", newConfig.primaryTargetPackage)
                putBoolean("floating_enabled", newConfig.isFloatingControlEnabled)
                putFloat("floating_alpha", newConfig.floatingAlpha)
                putBoolean("video_paused", newConfig.isVideoPaused)
                putBoolean("video_audio_muted", newConfig.isVideoAudioMuted)
                putBoolean("mp3_audio_paused", newConfig.isMp3AudioPaused)
                putBoolean("lightweight_mode", newConfig.isLightweightMode)
                putBoolean("debug_osd_enabled", newConfig.isDebugOsdInVirtualCameraEnabled)
                putBoolean("master_spill_mode", newConfig.isMasterSpillMode)
                putString("master_video_id", newConfig.masterVideoId)
                putBoolean("master_spill_wait_finish", newConfig.masterSpillWaitFinish)
                putString("active_spill_video_id", newConfig.activeSpillVideoId)
                putBoolean("auto_spill_comments", newConfig.autoSpillFromComments)
                putBoolean("bridge_cycle_mode", newConfig.isBridgeCycleMode)
                putBoolean("is_ifs_mode", newConfig.isIfsMode)
                putString("bridge1_video_id", newConfig.bridge1VideoId)
                putString("bridge2_video_id", newConfig.bridge2VideoId)
                putString("current_bridge_phase", newConfig.currentBridgePhase.name)
                if (newConfig.pendingBridgeContentIndex != null) {
                    putInt("pending_bridge_content_idx", newConfig.pendingBridgeContentIndex)
                } else {
                    remove("pending_bridge_content_idx")
                }
                putString("bridge_content_queue", newConfig.bridgeContentQueue.joinToString(","))
                putBoolean("mod_sales_notif", newConfig.enableSalesNotifModule)
                putBoolean("mod_sticker", newConfig.enableStickerModule)
                putBoolean("mod_watermark", newConfig.enableWatermarkModule)
                putBoolean("mod_auto_time", newConfig.enableAutoTimeModule)
                putBoolean("mod_prayer_comment", newConfig.enablePrayerCommentModule)
                putBoolean("mod_quran_khatam", newConfig.enableQuranKhatamModule)
                putBoolean("mod_auto_tap", newConfig.enableAutoTapModule)
                putBoolean("mod_mp3_music", newConfig.enableMp3MusicModule)
                putBoolean("mod_osd", newConfig.enableOsdModule)
                putBoolean("mod_quran_audio", newConfig.enableQuranAudioModule)
                putBoolean("mod_makkah_relay", newConfig.enableMakkahRelayModule)
                putBoolean("mod_live_event_broker", newConfig.enableLiveEventBrokerModule)
                putBoolean("mod_scan_inspector", newConfig.enableScanInspectorModule)
                putBoolean("live_focus_vpn_enabled", newConfig.isLiveFocusVpnEnabled)
                putBoolean("auto_ram_clean_enabled", newConfig.isAutoRamCleanEnabled)
                putInt("fps_governor_limit", newConfig.fpsGovernorLimit)
                putLong("last_freed_ram_mb", newConfig.lastFreedRamMb)
                putLong("last_ram_clean_time", newConfig.lastRamCleanTimestamp)
                putStringSet("ram_clean_whitelist", HashSet(newConfig.ramCleanWhitelistPackages))
                putStringSet("vpn_bypass_packages", HashSet(newConfig.vpnBypassPackages))
                putStringSet("blocked_ad_packages", HashSet(newConfig.blockedAdPackages))
                putBoolean("ad_blocker_active", newConfig.isAdBlockerActive)
                putBoolean("ad_blocker_auto_freeze", newConfig.adBlockerAutoFreeze)
                putString("anti_hash_mode", newConfig.antiHashMode.name)
                putBoolean("safe_codec_guard_active", newConfig.isSafeCodecGuardActive)
                putBoolean("audio_anti_hash_enabled", newConfig.isAudioAntiHashEnabled)
                putString("audio_anti_hash_mode", newConfig.audioAntiHashMode.name)
                putString("voice_effect", newConfig.voiceEffect.name)
                putBoolean("auto_rocket_recovery_enabled", newConfig.isAutoRocketRecoveryEnabled)
                putInt("auto_recovery_stall_threshold_sec", newConfig.autoRecoveryStallThresholdSec)
                putInt("auto_recovery_count", newConfig.autoRecoveryCount)
                putLong("last_auto_recovery_timestamp", newConfig.lastAutoRecoveryTimestamp)
                putBoolean("periodic_ram_clean_enabled", newConfig.isPeriodicRamCleanEnabled)
                putInt("periodic_ram_clean_interval_min", newConfig.periodicRamCleanIntervalMinutes)
                putInt("session_dna_index", newConfig.sessionDnaIndex.coerceAtLeast(1))
                putLong("session_dna_seed", newConfig.sessionDnaSeed)
                putBoolean("dual_audio_mixing_enabled", newConfig.isDualAudioMixingEnabled)
                putBoolean("independent_audio_enabled", newConfig.isIndependentAudioEnabled)
                putBoolean("human_volume_fluctuation_enabled", newConfig.isHumanVolumeFluctuationEnabled)
                putFloat("human_fluctuation_intensity", newConfig.humanFluctuationIntensity)
                putBoolean("audio_layer2_enabled", newConfig.isAudioLayer2Enabled)
                putString("audio_layer2_id", newConfig.audioLayer2Id)
                putString("audio_layer2_path", newConfig.audioLayer2Path)
                putString("audio_layer2_name", newConfig.audioLayer2Name)
                putFloat("audio_layer2_volume", newConfig.audioLayer2Volume)
                putBoolean("audio_layer2_muted", newConfig.isAudioLayer2Muted)
                putBoolean("audio_layer2_looping", newConfig.isAudioLayer2Looping)
                putBoolean("audio_layer3_enabled", newConfig.isAudioLayer3Enabled)
                putStringSet("audio_layer3_ids", newConfig.audioLayer3Ids)
                putFloat("audio_layer3_volume", newConfig.audioLayer3Volume)
                putBoolean("audio_layer3_muted", newConfig.isAudioLayer3Muted)
                putInt("audio_layer3_min_interval_sec", newConfig.audioLayer3MinIntervalSec)
                putInt("audio_layer3_max_interval_sec", newConfig.audioLayer3MaxIntervalSec)
                putBoolean("mod_dna", newConfig.enableDnaModule)
                putBoolean("mod_ai_detection", newConfig.enableAiDetectionModule)
                putFloat("video_anti_hash_percent", newConfig.videoAntiHashPercent)
                putFloat("audio_anti_hash_percent", newConfig.audioAntiHashPercent)
                putBoolean("audio_layer2_sync_anti_hash", newConfig.isAudioLayer2SyncAntiHash)
                putBoolean("dna_zoom_enabled", newConfig.isDnaZoomEnabled)
                putFloat("session_dna_custom_zoom", newConfig.sessionDnaCustomZoom)
                putFloat("session_dna_pan_x", newConfig.sessionDnaPanX)
                putFloat("session_dna_pan_y", newConfig.sessionDnaPanY)
                putStringSet("used_dna_zoom_history", newConfig.usedDnaZoomHistory)
                putBoolean("live_stream_source_active", newConfig.isLiveStreamSourceActive)
                putString("live_stream_url", newConfig.liveStreamUrl)
                putString("live_stream_title", newConfig.liveStreamTitle)
                putString("live_stream_status", newConfig.liveStreamStatus)
                putString("hook_engine", newConfig.hookEngine.name)
                putString("playlist_json", serializePlaylistToJson(newConfig.playlist))
                putString("audio_playlist_json", serializeAudioPlaylistToJson(newConfig.audioPlaylist))
            }.apply()

            if (writeHooks) {
                writeHookConfigFiles(newConfig)
            }
        } finally {
            lastInternalConfigUpdateMs = System.currentTimeMillis()
            isInternalPersistenceInProgress = false
        }
    }

    /**
     * Updates [_config] synchronously on the calling thread to prevent UI flicker,
     * and persists to SharedPreferences + hook config files in the background.
     */
    fun updateConfigSync(writeHooks: Boolean = true, update: (VcamConfig) -> VcamConfig): VcamConfig {
        lastInternalConfigUpdateMs = System.currentTimeMillis()
        val newConfig = synchronized(this) {
            val updated = update(_config.value)
            _config.value = updated
            updated
        }
        repoScope.launch(Dispatchers.IO) {
            persistConfigToPrefsAndHooks(newConfig, writeHooks = writeHooks)
        }
        return newConfig
    }

    suspend fun updateConfig(update: (VcamConfig) -> VcamConfig) {
        lastInternalConfigUpdateMs = System.currentTimeMillis()
        val newConfig = synchronized(this) {
            val updated = update(_config.value)
            _config.value = updated
            updated
        }
        withContext(Dispatchers.IO) {
            persistConfigToPrefsAndHooks(newConfig, writeHooks = true)
        }
    }

    fun bumpPlaybackStepVersion(): Long {
        val next = _playbackStepVersion.value + 1L
        _playbackStepVersion.value = next
        return next
    }

    /**
     * Broadcasts control events for real-time IPC updates without requiring app restarts
     */
    fun broadcastControlEvent(
        action: String = "UPDATE_CONFIG",
        rotation: Int = _config.value.rotation,
        isMirrored: Boolean = _config.value.isMirrored,
        isVerticalFlipped: Boolean = _config.value.isVerticalFlipped,
        zoomFactor: Float = _config.value.zoomFactor,
        isVideoPaused: Boolean = _config.value.isVideoPaused,
        isVideoAudioMuted: Boolean = _config.value.isVideoAudioMuted,
        isMp3AudioPaused: Boolean = _config.value.isMp3AudioPaused,
        audioEnabled: Boolean = _config.value.audioEnabled,
        isMasterEnabled: Boolean = _config.value.isMasterEnabled,
        videoPath: String = _config.value.videoPath,
        audioPath: String = _config.value.audioPath,
        scaleMode: String = _config.value.scaleMode.name,
        autoNextEnabled: Boolean = _config.value.autoNextEnabled,
        autoNextMode: String = _config.value.autoNextMode.name,
        autoNextLeadSeconds: Int = _config.value.autoNextLeadSeconds,
        autoNextLeadMs: Int = _config.value.autoNextLeadMs,
        videoVolume: Float = _videoVolume.value,
        videoSeekMs: Long = -1L,
        videoSwitchMode: String = _config.value.videoSwitchMode.name,
        isLightweightMode: Boolean = _config.value.isLightweightMode,
        extraSource: String? = null,
        extraPurgeCache: Boolean = false,
        videoName: String? = null
    ) {
        try {
            val intent = Intent("com.tajasb.action.CONTROL_EVENT").apply {
                putExtra("extra_action", action)
                if (extraSource != null) {
                    putExtra("extra_source", extraSource)
                }
                putExtra("extra_purge_cache", extraPurgeCache)
                putExtra("extra_video_volume", videoVolume)
                if (videoSeekMs >= 0L) {
                    putExtra("extra_video_seek", videoSeekMs)
                }
                putExtra("extra_rotation", rotation)
                putExtra("extra_mirror", isMirrored)
                putExtra("extra_flip_v", isVerticalFlipped)
                putExtra("extra_zoom_factor", zoomFactor)
                putExtra("extra_pause_video", isVideoPaused)
                putExtra("extra_mute_video_audio", isVideoAudioMuted)
                putExtra("extra_pause_mp3_audio", isMp3AudioPaused)
                putExtra("extra_pause_audio", isMp3AudioPaused)
                putExtra("extra_pause", isVideoPaused)
                putExtra("extra_muted", !audioEnabled)
                putExtra("extra_master", isMasterEnabled)
                putExtra("extra_video_path", videoPath)
                putExtra("extra_audio_path", audioPath)
                putExtra("extra_scale_mode", scaleMode)
                putExtra("extra_auto_next_enabled", autoNextEnabled)
                putExtra("extra_auto_next_mode", autoNextMode)
                putExtra("extra_video_switch_mode", videoSwitchMode)
                putExtra("extra_auto_next_lead_seconds", autoNextLeadSeconds)
                putExtra("extra_auto_next_lead_ms", autoNextLeadMs)
                putExtra("extra_primary_target", _config.value.primaryTargetPackage)
                putExtra("extra_current_index", _config.value.currentVideoIndex)
                val effVidName = videoName?.takeIf { it.isNotBlank() } ?: (_config.value.currentVideo?.name ?: "")
                putExtra("extra_video_name", effVidName)
                putExtra("extra_lightweight_mode", isLightweightMode)
                putExtra("extra_debug_osd_enabled", _config.value.isDebugOsdInVirtualCameraEnabled)
                putExtra("extra_anti_hash_mode", _config.value.antiHashMode.name)
                putExtra("extra_video_anti_hash_percent", _config.value.videoAntiHashPercent)
                putExtra("extra_audio_anti_hash_enabled", _config.value.isAudioAntiHashEnabled)
                putExtra("extra_audio_anti_hash_mode", _config.value.audioAntiHashMode.name)
                putExtra("extra_audio_anti_hash_percent", _config.value.audioAntiHashPercent)
                putExtra("extra_audio_layer2_sync_anti_hash", _config.value.isAudioLayer2SyncAntiHash)
                putExtra("extra_enable_dna_module", _config.value.enableDnaModule)
                putExtra("extra_dna_zoom_enabled", _config.value.isDnaZoomEnabled)
                putExtra("extra_session_dna_index", _config.value.sessionDnaIndex)
                putExtra("extra_session_dna_seed", _config.value.sessionDnaSeed)
                putExtra("extra_session_dna_custom_zoom", _config.value.sessionDnaCustomZoom)
                putExtra("extra_session_dna_pan_x", _config.value.sessionDnaPanX)
                putExtra("extra_session_dna_pan_y", _config.value.sessionDnaPanY)
                putExtra("extra_fps", _config.value.fps)
                val pIdx = _pendingVideoIndex.value
                val pBridgeContentIdx = _config.value.pendingBridgeContentIndex
                if (pIdx != null) {
                    putExtra("extra_pending_index", pIdx)
                    putExtra("extra_pending_name", _config.value.playlist.getOrNull(pIdx)?.name ?: "")
                    putExtra("extra_pending_path", _config.value.playlist.getOrNull(pIdx)?.path ?: "")
                } else if (_config.value.isBridgeCycleMode && pBridgeContentIdx != null) {
                    putExtra("extra_pending_index", pBridgeContentIdx)
                    putExtra("extra_pending_name", _config.value.playlist.getOrNull(pBridgeContentIdx)?.name ?: "")
                    putExtra("extra_pending_path", _config.value.playlist.getOrNull(pBridgeContentIdx)?.path ?: "")
                }
                val hasBridgeQueue = _config.value.bridgeContentQueue.isNotEmpty()
                val hasBridgePending = (pBridgeContentIdx != null) || hasBridgeQueue
                putExtra("extra_bridge_has_pending", hasBridgePending)
                putExtra("extra_pending_bridge_content_index", pBridgeContentIdx ?: -1)
                putExtra("extra_bridge_queue_size", _config.value.bridgeContentQueue.size)
                putExtra("extra_master_spill_mode", _config.value.isMasterSpillMode)
                putExtra("extra_master_video_id", _config.value.masterVideoId ?: "")
                putExtra("extra_master_index", getMasterVideoIndex())
                putExtra("extra_active_spill_id", _config.value.activeSpillVideoId ?: "")
                putExtra("extra_master_spill_wait_finish", _config.value.masterSpillWaitFinish)
                putExtra("extra_is_spill_playing", _config.value.isCurrentPlayingSpill)
                putExtra("extra_bridge_cycle_mode", _config.value.isBridgeCycleMode)
                putExtra("extra_bridge_phase", _config.value.currentBridgePhase.name)
                putExtra("extra_bridge1_index", _config.value.bridge1VideoIndex)
                putExtra("extra_bridge2_index", _config.value.bridge2VideoIndex)
                putExtra("extra_live_stream_active", _config.value.isLiveStreamSourceActive)
                putExtra("extra_live_stream_url", _config.value.liveStreamUrl)
                putExtra("extra_live_stream_title", _config.value.liveStreamTitle)

                val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(context) } catch (_: Throwable) { null }
                val isIfs = ifsMgr?.isIfsActive?.value == true || _config.value.isIfsMode
                val isIfsLooping = ifsMgr?.isCurrentPhaseLooping() ?: false
                putExtra("extra_ifs_mode", isIfs)
                putExtra("extra_ifs_looping", isIfsLooping)
                putExtra("extra_ifs_phase", ifsMgr?.ifsState?.value?.activeSubPhase?.name ?: "ABB")
                putExtra("extra_ifs_vt", ifsMgr?.ifsState?.value?.activeVtNumber ?: 1)

                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }

            val targets = (listOf(
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.ss.android.ugc.live",
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.lite",
                "com.instagram.android",
                "com.whatsapp",
                "com.whatsapp.w4b"
            ) + _config.value.targetPackages).distinct()

            for (tp in targets) {
                try {
                    val targeted = Intent(intent).apply { setPackage(tp) }
                    context.sendBroadcast(targeted)
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    suspend fun setRotation(rotation: Int) {
        val safeRot = when (rotation) {
            90, 180, 270 -> rotation
            else -> 0
        }
        updateConfig { it.copy(rotation = safeRot) }
        broadcastControlEvent(action = "CYCLE_ROTATION", rotation = safeRot)
    }

    suspend fun cycleRotation(): Int {
        val nextRot = (_config.value.rotation + 90) % 360
        updateConfig { it.copy(rotation = nextRot) }
        broadcastControlEvent(action = "CYCLE_ROTATION", rotation = nextRot)
        return nextRot
    }

    suspend fun toggleMirror(): Boolean {
        val nextMirror = !_config.value.isMirrored
        updateConfig { it.copy(isMirrored = nextMirror) }
        broadcastControlEvent(action = "MIRROR", isMirrored = nextMirror)
        return nextMirror
    }

    suspend fun toggleFlipVertical(): Boolean {
        val nextFlip = !_config.value.isVerticalFlipped
        updateConfig { it.copy(isVerticalFlipped = nextFlip) }
        broadcastControlEvent(action = "FLIP_VERTICAL", isVerticalFlipped = nextFlip)
        return nextFlip
    }

    suspend fun setZoomFactor(zoom: Float): Float {
        val safeZoom = (Math.round(zoom * 100f) / 100f).coerceIn(0.5f, 3.0f)
        updateConfig { it.copy(zoomFactor = safeZoom) }
        broadcastControlEvent(action = "ZOOM", zoomFactor = safeZoom)
        return safeZoom
    }

    suspend fun adjustZoom(delta: Float): Float {
        val current = _config.value.zoomFactor
        return setZoomFactor(current + delta)
    }

    suspend fun toggleLightweightMode(enabled: Boolean? = null): Boolean {
        val nextMode = enabled ?: !_config.value.isLightweightMode
        updateConfig { it.copy(isLightweightMode = nextMode) }
        broadcastControlEvent(action = "TOGGLE_LIGHTWEIGHT_MODE", isLightweightMode = nextMode)
        return nextMode
    }

    suspend fun toggleDebugOsdInVirtualCamera(enabled: Boolean? = null): Boolean {
        val nextMode = enabled ?: !_config.value.isDebugOsdInVirtualCameraEnabled
        updateConfig { it.copy(isDebugOsdInVirtualCameraEnabled = nextMode) }
        broadcastControlEvent(action = "TOGGLE_DEBUG_OSD", isLightweightMode = _config.value.isLightweightMode)
        try {
            val directIntent = Intent("com.tajasb.action.TOGGLE_DEBUG_OSD").apply {
                putExtra("extra_debug_osd_enabled", nextMode)
            }
            val targets = (listOf(
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.ss.android.ugc.live",
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.lite",
                "com.instagram.android",
                "com.whatsapp",
                "com.whatsapp.w4b"
            ) + _config.value.targetPackages).distinct()
            for (tp in targets) {
                try {
                    context.sendBroadcast(Intent(directIntent).apply { setPackage(tp) })
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        return nextMode
    }

    suspend fun togglePauseVideo(): Boolean {
        val nextPause = !_config.value.isVideoPaused
        updateConfig { it.copy(isVideoPaused = nextPause) }
        broadcastControlEvent(action = "TOGGLE_PAUSE_VIDEO", isVideoPaused = nextPause)
        return nextPause
    }

    suspend fun setAntiHashMode(mode: com.tajasb.model.AntiHashIntensity) = withContext(Dispatchers.IO) {
        val syncedPct = mode.defaultPercent
        updateConfig {
            it.copy(
                antiHashMode = mode,
                videoAntiHashPercent = syncedPct
            )
        }
        broadcastControlEvent(action = "SET_ANTI_HASH")
        try {
            val intent = Intent("com.tajasb.action.SET_ANTI_HASH").apply {
                putExtra("extra_anti_hash_mode", mode.name)
                putExtra("extra_video_anti_hash_percent", syncedPct)
            }
            context.sendBroadcast(intent)
            val targets = (listOfNotNull(
                _config.value.primaryTargetPackage.takeIf { it.isNotBlank() },
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill"
            ) + _config.value.targetPackages).distinct()
            for (tp in targets) {
                try {
                    context.sendBroadcast(Intent(intent).apply { setPackage(tp) })
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    suspend fun toggleAudioAntiHash(enabled: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val next = enabled ?: !_config.value.isAudioAntiHashEnabled
        updateConfig { it.copy(isAudioAntiHashEnabled = next) }
        withContext(Dispatchers.Main) {
            musicPlayer.setAudioAntiHash(next, _config.value.audioAntiHashMode)
        }
        broadcastAudioAntiHashEvent(next, _config.value.audioAntiHashMode)
        next
    }

    suspend fun setAudioAntiHashMode(mode: com.tajasb.model.AudioAntiHashIntensity) = withContext(Dispatchers.IO) {
        updateConfig { it.copy(audioAntiHashMode = mode) }
        withContext(Dispatchers.Main) {
            musicPlayer.setAudioAntiHash(_config.value.isAudioAntiHashEnabled, mode)
        }
        broadcastAudioAntiHashEvent(_config.value.isAudioAntiHashEnabled, mode)
    }

    private fun broadcastAudioAntiHashEvent(enabled: Boolean, mode: com.tajasb.model.AudioAntiHashIntensity) {
        broadcastControlEvent(action = "SET_AUDIO_ANTI_HASH")
        try {
            val intent = Intent("com.tajasb.action.SET_AUDIO_ANTI_HASH").apply {
                putExtra("extra_audio_anti_hash_enabled", enabled)
                putExtra("extra_audio_anti_hash_mode", mode.name)
            }
            context.sendBroadcast(intent)
            val targets = (listOfNotNull(
                _config.value.primaryTargetPackage.takeIf { it.isNotBlank() },
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill"
            ) + _config.value.targetPackages).distinct()
            for (tp in targets) {
                try {
                    context.sendBroadcast(Intent(intent).apply { setPackage(tp) })
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    fun adjustVideoAntiHashPercent(delta: Float) {
        val cur = if (_config.value.antiHashMode == com.tajasb.model.AntiHashIntensity.OFF) {
            0.0f
        } else {
            _config.value.videoAntiHashPercent
        }
        val next = (cur + delta).coerceIn(0.0f, 5.0f)
        val rounded = kotlin.math.round(next * 10f) / 10f
        setVideoAntiHashPercent(rounded)
    }

    fun setVideoAntiHashPercent(pct: Float) {
        val clamped = (kotlin.math.round(pct.coerceIn(0.0f, 5.0f) * 10f) / 10f)
        val syncedMode = com.tajasb.model.AntiHashIntensity.fromPercent(clamped)
        updateConfigSync {
            it.copy(
                videoAntiHashPercent = clamped,
                antiHashMode = syncedMode
            )
        }
        broadcastControlEvent(action = "SET_VIDEO_ANTI_HASH_PERCENT")
        try {
            val intent = Intent("com.tajasb.action.SET_ANTI_HASH").apply {
                putExtra("extra_anti_hash_mode", syncedMode.name)
                putExtra("extra_video_anti_hash_percent", clamped)
            }
            context.sendBroadcast(intent)
        } catch (_: Throwable) {}
    }

    fun adjustAudioAntiHashPercent(delta: Float) {
        val cur = _config.value.audioAntiHashPercent
        val next = (cur + delta).coerceIn(0.1f, 5.0f)
        val rounded = kotlin.math.round(next * 10f) / 10f
        setAudioAntiHashPercent(rounded)
    }

    fun setAudioAntiHashPercent(pct: Float) {
        val clamped = (kotlin.math.round(pct.coerceIn(0.1f, 5.0f) * 10f) / 10f)
        updateConfigSync { it.copy(audioAntiHashPercent = clamped) }
        musicPlayer.setAudioAntiHashPercent(clamped)
        broadcastControlEvent(action = "SET_AUDIO_ANTI_HASH_PERCENT")
    }

    fun setAudioLayer2SyncAntiHash(enabled: Boolean) {
        updateConfigSync { it.copy(isAudioLayer2SyncAntiHash = enabled) }
        musicPlayer.setAudioLayer2SyncAntiHash(enabled)
        broadcastControlEvent(action = "SET_AUDIO_LAYER_2_SYNC_ANTI_HASH")
    }

    fun setSessionDnaFraming(
        zoom: Float,
        panX: Float,
        panY: Float,
        registerToHistory: Boolean = false,
        enableDnaZoom: Boolean = true
    ) {
        val safeZ = (kotlin.math.round(zoom.coerceIn(1.04f, 2.50f) * 100f) / 100f)
        val maxSafePan = com.tajasb.model.FramingGuardMath.calculateSafePanLimit(safeZ)
        val safePanX = panX.coerceIn(-maxSafePan, maxSafePan)
        val safePanY = panY.coerceIn(-maxSafePan, maxSafePan)
        updateConfigSync { cfg ->
            val nextHistory = if (registerToHistory) {
                val tag = String.format(java.util.Locale.US, "%.2fx", safeZ)
                cfg.usedDnaZoomHistory + tag
            } else cfg.usedDnaZoomHistory
            cfg.copy(
                isDnaZoomEnabled = if (enableDnaZoom) true else cfg.isDnaZoomEnabled,
                sessionDnaCustomZoom = safeZ,
                sessionDnaPanX = safePanX,
                sessionDnaPanY = safePanY,
                usedDnaZoomHistory = nextHistory
            )
        }
        broadcastControlEvent(action = "SET_SESSION_DNA_FRAMING")
    }

    fun isZoomAlreadyUsed(zoom: Float): Boolean {
        val history = _config.value.usedDnaZoomHistory
        if (history.isEmpty()) return false
        val formatted = String.format(java.util.Locale.US, "%.2fx", zoom)
        if (history.contains(formatted)) return true
        for (item in history) {
            val num = item.removeSuffix("x").toFloatOrNull()
            if (num != null && kotlin.math.abs(num - zoom) < 0.009f) {
                return true
            }
        }
        return false
    }

    fun registerCurrentZoomToHistory() {
        val z = _config.value.sessionDnaCustomZoom
        val formatted = String.format(java.util.Locale.US, "%.2fx", z)
        updateConfigSync { it.copy(usedDnaZoomHistory = it.usedDnaZoomHistory + formatted) }
    }

    fun clearZoomHistory() {
        updateConfigSync { it.copy(usedDnaZoomHistory = emptySet()) }
    }

    fun generateNextUniqueZoom(
        minZoom: Float = 1.05f,
        maxZoom: Float = 1.40f,
        stepZoom: Float = 0.02f
    ): Float {
        val prevZoom = _config.value.sessionDnaCustomZoom
        val wasDnaZoomActive = _config.value.isDnaZoomEnabled || _config.value.usedDnaZoomHistory.isNotEmpty()
        if (wasDnaZoomActive && prevZoom >= 1.04f) {
            val prevTag = String.format(java.util.Locale.US, "%.2fx", prevZoom)
            updateConfigSync(writeHooks = false) { it.copy(usedDnaZoomHistory = it.usedDnaZoomHistory + prevTag) }
        }

        val candidates = mutableListOf<Float>()
        var z = minZoom
        while (z <= maxZoom + 0.001f) {
            val rounded = (kotlin.math.round(z * 100f) / 100f)
            candidates.add(rounded)
            z += stepZoom
        }
        val unused = candidates.filter { cand ->
            !isZoomAlreadyUsed(cand) && kotlin.math.abs(cand - prevZoom) >= 0.009f
        }
        val chosen = if (unused.isNotEmpty()) {
            unused.random()
        } else {
            val shifted = candidates
                .map { (kotlin.math.round((it + 0.01f) * 100f) / 100f) }
                .filter { !isZoomAlreadyUsed(it) && kotlin.math.abs(it - prevZoom) >= 0.009f }
            if (shifted.isNotEmpty()) {
                shifted.random()
            } else {
                candidates.filter { kotlin.math.abs(it - prevZoom) >= 0.009f }.ifEmpty { candidates }.random()
            }
        }
        // Pastikan chosen tidak tercatat di history saat sedang aktif agar tidak memicu false-positive duplikat
        val chosenTag = String.format(java.util.Locale.US, "%.2fx", chosen)
        if (_config.value.usedDnaZoomHistory.contains(chosenTag)) {
            updateConfigSync(writeHooks = false) { it.copy(usedDnaZoomHistory = it.usedDnaZoomHistory - chosenTag) }
        }
        val maxSafePan = com.tajasb.model.FramingGuardMath.calculateSafePanLimit(chosen)
        val rndX = ((Math.random().toFloat() * 2f - 1f) * maxSafePan * 0.70f)
        val rndY = ((Math.random().toFloat() * 2f - 1f) * maxSafePan * 0.70f)
        setSessionDnaFraming(chosen, rndX, rndY, registerToHistory = false, enableDnaZoom = true)
        return chosen
    }

    private val sv4AntiDetekHistory = java.util.Collections.synchronizedList(mutableListOf<com.tajasb.model.Sv4AntiDetekSnapshot>())

    fun getSv4AntiDetekHistoryInLast2m(): List<com.tajasb.model.Sv4AntiDetekSnapshot> {
        val now = System.currentTimeMillis()
        synchronized(sv4AntiDetekHistory) {
            sv4AntiDetekHistory.removeAll { (now - it.timestampMs) >= 120_000L }
            return sv4AntiDetekHistory.toList()
        }
    }

    fun clearSv4AntiDetekHistory() {
        synchronized(sv4AntiDetekHistory) {
            sv4AntiDetekHistory.clear()
        }
    }

    /**
     * Memutar dan menerapkan konfigurasi Anti-Deteksi (Visual Zoom/Framing & Akustik Suara)
     * khusus mode SV4, dengan jaminan 100% TIDAK PERNAH SAMA dalam rentang waktu 2 menit (120 detik).
     */
    fun applySv4TwoMinuteAntiDetekRotation(reason: String = "sv4_advance"): com.tajasb.model.Sv4AntiDetekSnapshot {
        val now = System.currentTimeMillis()
        // 1. Bersihkan riwayat yang usianya sudah melewati 2 menit (120_000 ms)
        synchronized(sv4AntiDetekHistory) {
            sv4AntiDetekHistory.removeAll { (now - it.timestampMs) >= 120_000L }
        }

        // 2. Tentukan Session DNA baru (kuadran, palet warna, gamma, kontras, warmth, arah cahaya & frekuensi DCT)
        val recentDnaIndices = synchronized(sv4AntiDetekHistory) { sv4AntiDetekHistory.map { it.dnaIndex }.toSet() }
        var nextIndex = (_config.value.sessionDnaIndex + 1).let { if (it > 999) 1 else it }
        var dnaAttempts = 0
        while (recentDnaIndices.contains(nextIndex) && dnaAttempts < 16) {
            nextIndex = (nextIndex + 1).let { if (it > 999) 1 else it }
            dnaAttempts++
        }
        val nextSeed = (now xor (nextIndex.toLong() * 0x9E3779B97F4A7C15UL.toLong())) and 0x7FFFFFFFFFFFFFFFL

        // 3. Tentukan Zoom Unik yang berbeda minimal 0.02x dari semua zoom dalam 2 menit terakhir
        val recentZooms = synchronized(sv4AntiDetekHistory) { sv4AntiDetekHistory.map { it.zoom } }
        val candidateZooms = mutableListOf<Float>()
        var z = 1.05f
        while (z <= 1.40f + 0.001f) {
            val rounded = (kotlin.math.round(z * 100f) / 100f)
            candidateZooms.add(rounded)
            z += 0.02f
        }
        val validZooms = candidateZooms.filter { cand ->
            recentZooms.none { kotlin.math.abs(it - cand) < 0.019f }
        }.ifEmpty {
            val shifted = candidateZooms.map { it + 0.01f }.filter { cand ->
                recentZooms.none { kotlin.math.abs(it - cand) < 0.009f }
            }
            if (shifted.isNotEmpty()) shifted else candidateZooms
        }
        val chosenZoom = validZooms.random()

        // 4. Tentukan Posisi Framing (Pan X / Pan Y) dalam batas aman Safe Framing Envelope Guard (bebas celah hitam 100%)
        val maxSafePan = com.tajasb.model.FramingGuardMath.calculateSafePanLimit(chosenZoom)
        val recentPans = synchronized(sv4AntiDetekHistory) { sv4AntiDetekHistory.map { Pair(it.panX, it.panY) } }
        var chosenPanX = 0.0f
        var chosenPanY = 0.0f
        var panAttempts = 0
        while (panAttempts < 12) {
            val rndX = ((Math.random().toFloat() * 2f - 1f) * maxSafePan * 0.72f)
            val rndY = ((Math.random().toFloat() * 2f - 1f) * maxSafePan * 0.72f)
            val isDiff = recentPans.none { (px, py) ->
                kotlin.math.abs(px - rndX) < 0.008f && kotlin.math.abs(py - rndY) < 0.008f
            }
            if (isDiff || panAttempts >= 11) {
                chosenPanX = rndX
                chosenPanY = rndY
                break
            }
            panAttempts++
        }

        // 5. Update konfigurasi secara sinkron (Hormati saklar masing-masing modul: jangan nyalakan paksa jika OFF!)
        val updatedCfg = updateConfigSync { cur ->
            val dnaOn = cur.enableDnaModule
            val zoomOn = cur.isDnaZoomEnabled
            val prevTag = String.format(java.util.Locale.US, "%.2fx", cur.sessionDnaCustomZoom)
            val chosenTag = String.format(java.util.Locale.US, "%.2fx", chosenZoom)
            cur.copy(
                sessionDnaIndex = if (dnaOn) nextIndex else cur.sessionDnaIndex,
                sessionDnaSeed = if (dnaOn) nextSeed else cur.sessionDnaSeed,
                sessionDnaCustomZoom = if (zoomOn) chosenZoom else cur.sessionDnaCustomZoom,
                sessionDnaPanX = if (zoomOn) chosenPanX else cur.sessionDnaPanX,
                sessionDnaPanY = if (zoomOn) chosenPanY else cur.sessionDnaPanY,
                usedDnaZoomHistory = if (zoomOn) ((cur.usedDnaZoomHistory + prevTag) - chosenTag) else cur.usedDnaZoomHistory
            )
        }
        val profile = updatedCfg.activeSessionDna

        // 6. Terapkan DNA baru ke pemutar musik latar HANYA jika Saklar DNA Sesi aktif
        try {
            musicPlayer.setSessionDna(
                updatedCfg.sessionDnaIndex,
                updatedCfg.sessionDnaSeed,
                enabled = updatedCfg.enableDnaModule
            )
        } catch (_: Throwable) {}

        // 7. Siarkan event ke Hook & GlVideoRenderer
        broadcastControlEvent(action = "SET_SESSION_DNA")
        broadcastControlEvent(action = "SET_SESSION_DNA_FRAMING")
        try {
            val intent = android.content.Intent("com.tajasb.action.SET_SESSION_DNA").apply {
                putExtra("extra_session_dna_index", updatedCfg.sessionDnaIndex)
                putExtra("extra_session_dna_seed", updatedCfg.sessionDnaSeed)
                putExtra("extra_enable_dna_module", updatedCfg.enableDnaModule)
                putExtra("extra_is_dna_zoom_enabled", updatedCfg.isDnaZoomEnabled)
                putExtra("extra_session_dna_custom_zoom", updatedCfg.sessionDnaCustomZoom)
                putExtra("extra_session_dna_pan_x", updatedCfg.sessionDnaPanX)
                putExtra("extra_session_dna_pan_y", updatedCfg.sessionDnaPanY)
            }
            context.sendBroadcast(intent)
            val targets = (listOfNotNull(
                _config.value.primaryTargetPackage.takeIf { it.isNotBlank() },
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.ss.android.ugc.aweme"
            ) + _config.value.targetPackages).distinct()
            for (pkg in targets) {
                try {
                    val pkgIntent = android.content.Intent("com.tajasb.action.SET_SESSION_DNA").apply {
                        setPackage(pkg)
                        putExtra("extra_session_dna_index", nextIndex)
                        putExtra("extra_session_dna_seed", nextSeed)
                        putExtra("extra_session_dna_custom_zoom", chosenZoom)
                        putExtra("extra_session_dna_pan_x", chosenPanX)
                        putExtra("extra_session_dna_pan_y", chosenPanY)
                    }
                    context.sendBroadcast(pkgIntent)
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        val snapshot = com.tajasb.model.Sv4AntiDetekSnapshot(
            zoom = chosenZoom,
            panX = chosenPanX,
            panY = chosenPanY,
            dnaIndex = nextIndex,
            sessionSeed = nextSeed,
            pitchCenterRatio = profile.pitchCenterRatio,
            speedCenterRatio = profile.speedCenterRatio,
            timestampMs = now
        )
        synchronized(sv4AntiDetekHistory) {
            sv4AntiDetekHistory.add(snapshot)
        }

        android.util.Log.i(
            TAG,
            "🛡️ [SV4-2MIN-GUARD] Anti-Deteksi Baru Diterapkan ($reason): Zoom ${chosenZoom}x, Pan(${String.format(java.util.Locale.US, "%.3f, %.3f", chosenPanX, chosenPanY)}), DNA #${nextIndex} (${profile.quadrantLabel}), Pitch ${(profile.pitchCenterRatio - 1f) * 100f}%, Tempo ${(profile.speedCenterRatio - 1f) * 100f}%"
        )
        return snapshot
    }

    suspend fun setVoiceEffect(effect: com.tajasb.model.VoiceEffect) = withContext(Dispatchers.IO) {
        updateConfig { it.copy(voiceEffect = effect) }
        withContext(Dispatchers.Main) {
            musicPlayer.setVoiceEffect(effect)
        }
        broadcastVoiceEffectEvent(effect)
    }

    fun setDualAudioMixing(enabled: Boolean) {
        repoScope.launch {
            updateConfig { it.copy(isDualAudioMixingEnabled = enabled) }
            if (enabled) {
                wasMusicAutoPausedByVideo = false
                _isMusicAutoPausedWaitingForMute.value = false
            }
        }
    }

    fun toggleDualAudioMixing(): Boolean {
        val next = !_config.value.isDualAudioMixingEnabled
        setDualAudioMixing(next)
        return next
    }

    fun setIndependentAudio(enabled: Boolean) {
        repoScope.launch {
            updateConfig { it.copy(isIndependentAudioEnabled = enabled) }
            musicPlayer.setIndependentAudioMode(enabled)
            if (enabled) {
                wasMusicAutoPausedByVideo = false
                _isMusicAutoPausedWaitingForMute.value = false
            }
            try {
                val intent = Intent("com.tajasb.action.SET_INDEPENDENT_AUDIO").apply {
                    putExtra("extra_independent_audio_enabled", enabled)
                }
                context.sendBroadcast(intent)
            } catch (_: Throwable) {}
        }
    }

    fun toggleIndependentAudio(): Boolean {
        val next = !_config.value.isIndependentAudioEnabled
        setIndependentAudio(next)
        return next
    }

    // --- Audio Tipe 2: Mandiri & Berdiri Sendiri Dengan Loopingnya ---

    fun setAudioLayer2Active(enabled: Boolean) {
        repoScope.launch {
            updateConfig { it.copy(isAudioLayer2Enabled = enabled) }
            val curTrack = _config.value.currentAudioLayer2
            if (curTrack != null && musicPlayer.currentTrackLayer2.value == null) {
                musicPlayer.setLayer2Track(curTrack, autoStartIfActive = false)
            }
            musicPlayer.setLayer2Active(enabled, autoStart = true)
            try {
                val intent = Intent("com.tajasb.action.SET_AUDIO_LAYER_2").apply {
                    putExtra("extra_audio_layer2_enabled", enabled)
                }
                context.sendBroadcast(intent)
            } catch (_: Throwable) {}
        }
    }

    fun toggleAudioLayer2(): Boolean {
        val next = !_config.value.isAudioLayer2Enabled
        setAudioLayer2Active(next)
        return next
    }

    fun selectAudioLayer2Track(item: com.tajasb.model.AudioItem) {
        repoScope.launch {
            updateConfig {
                it.copy(
                    audioLayer2Id = item.id,
                    audioLayer2Path = item.path,
                    audioLayer2Name = item.name,
                    isAudioLayer2Enabled = true
                )
            }
            // 1. Keluarkan secara otomatis dari susunan SV4 Audio
            try {
                val ifsMgr = com.tajasb.infinity.IfsManager.getInstance(context)
                ifsMgr.removeSv4AudioL(item.id)
            } catch (_: Throwable) {}

            // 2. Cegah tabrakan/distorsi: jika Tipe 1 memutar lagu yang sama, alihkan Tipe 1 ke trek lain
            val curT1 = musicPlayer.currentTrack.value ?: _config.value.currentAudio
            if (curT1?.id == item.id || (item.path.isNotBlank() && curT1?.path == item.path)) {
                val t1List = _config.value.type1Playlist
                val nextT1 = t1List.firstOrNull { it.id != item.id }
                if (nextT1 != null) {
                    val fullIdx = _config.value.audioPlaylist.indexOfFirst { it.id == nextT1.id }.coerceAtLeast(0)
                    updateConfig {
                        it.copy(
                            currentAudioIndex = fullIdx,
                            audioPath = nextT1.path,
                            audioName = nextT1.name
                        )
                    }
                    if (musicPlayer.isPlaying.value) {
                        musicPlayer.playAudioItem(nextT1, startPlaying = true)
                    }
                } else {
                    musicPlayer.stop(clearTrackState = false)
                }
            }

            // 3. Putar Tipe 2 secara mandiri dengan looping bawaannya
            musicPlayer.setLayer2Track(item, autoStartIfActive = true)
        }
    }

    fun clearAudioLayer2Track() {
        repoScope.launch {
            updateConfig {
                it.copy(
                    audioLayer2Id = null,
                    audioLayer2Path = "",
                    audioLayer2Name = "",
                    isAudioLayer2Enabled = false
                )
            }
            musicPlayer.stopLayer2(clearTrackState = true)
        }
    }

    fun setAudioLayer2Volume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        repoScope.launch {
            updateConfig { it.copy(audioLayer2Volume = clamped) }
            musicPlayer.setVolumeLayer2(clamped)
        }
    }

    fun toggleAudioLayer2Mute(): Boolean {
        val nextMuted = !_config.value.isAudioLayer2Muted
        repoScope.launch {
            updateConfig { it.copy(isAudioLayer2Muted = nextMuted) }
            musicPlayer.setMutedLayer2(nextMuted)
        }
        return nextMuted
    }

    fun toggleAudioLayer2Loop(): Boolean {
        val nextLoop = !_config.value.isAudioLayer2Looping
        repoScope.launch {
            updateConfig { it.copy(isAudioLayer2Looping = nextLoop) }
            musicPlayer.setLoopModeLayer2(nextLoop)
        }
        return nextLoop
    }

    fun playLayer2() {
        val l2Track = _config.value.currentAudioLayer2
        if (l2Track != null && musicPlayer.currentTrackLayer2.value == null) {
            musicPlayer.playAudioItemLayer2(l2Track, startPlaying = true)
        } else {
            musicPlayer.playLayer2()
        }
    }

    fun pauseLayer2() {
        musicPlayer.pauseLayer2()
    }

    fun seekAudioLayer2(posMs: Long) {
        musicPlayer.seekToLayer2(posMs)
    }

    fun toggleLayer2PlayPause(): Boolean {
        return musicPlayer.toggleLayer2PlayPause()
    }

    // --- Audio Tipe 3: Ringtone Acak Dinamis ---

    fun setAudioLayer3Active(enabled: Boolean) {
        repoScope.launch {
            updateConfig { it.copy(isAudioLayer3Enabled = enabled) }
            musicPlayer.setLayer3Active(enabled)
        }
    }

    fun toggleAudioLayer3(): Boolean {
        val next = !_config.value.isAudioLayer3Enabled
        setAudioLayer3Active(next)
        return next
    }

    fun toggleAudioLayer3RingtoneId(ringtoneId: String) {
        repoScope.launch {
            val curSet = _config.value.audioLayer3Ids.toMutableSet()
            if (curSet.contains(ringtoneId)) {
                curSet.remove(ringtoneId)
            } else {
                curSet.add(ringtoneId)
            }
            updateConfig { it.copy(audioLayer3Ids = curSet) }
            musicPlayer.setLayer3RingtoneIds(curSet)
        }
    }

    fun setAudioLayer3RingtoneIds(ids: Set<String>) {
        repoScope.launch {
            updateConfig { it.copy(audioLayer3Ids = ids) }
            musicPlayer.setLayer3RingtoneIds(ids)
        }
    }

    fun setAudioLayer3Volume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        repoScope.launch {
            updateConfig { it.copy(audioLayer3Volume = clamped) }
            musicPlayer.setVolumeLayer3(clamped)
        }
    }

    fun toggleAudioLayer3Mute(): Boolean {
        val nextMuted = !_config.value.isAudioLayer3Muted
        repoScope.launch {
            updateConfig { it.copy(isAudioLayer3Muted = nextMuted) }
            musicPlayer.setMutedLayer3(nextMuted)
        }
        return nextMuted
    }

    fun setAudioLayer3IntervalRange(minSec: Int, maxSec: Int) {
        val safeMin = minSec.coerceIn(5, 600)
        val safeMax = maxSec.coerceAtLeast(safeMin + 5).coerceIn(10, 1800)
        repoScope.launch {
            updateConfig {
                it.copy(
                    audioLayer3MinIntervalSec = safeMin,
                    audioLayer3MaxIntervalSec = safeMax
                )
            }
            musicPlayer.setLayer3IntervalRange(safeMin, safeMax)
        }
    }

    fun triggerTestLayer3Ringtone(item: AudioItem? = null) {
        musicPlayer.triggerTestRingtone(item)
    }

    fun setHumanVolumeFluctuation(enabled: Boolean, intensity: Float = _config.value.humanFluctuationIntensity) {
        repoScope.launch {
            val clamped = intensity.coerceIn(0.05f, 0.40f)
            updateConfig {
                it.copy(
                    isHumanVolumeFluctuationEnabled = enabled,
                    humanFluctuationIntensity = clamped
                )
            }
            musicPlayer.setHumanVolumeFluctuation(enabled, clamped)
            try {
                val intent = Intent("com.tajasb.action.SET_HUMAN_VOLUME_FLUCTUATION").apply {
                    putExtra("extra_human_volume_fluctuation_enabled", enabled)
                    putExtra("extra_human_fluctuation_intensity", clamped)
                }
                context.sendBroadcast(intent)
            } catch (_: Throwable) {}
        }
    }

    fun toggleHumanVolumeFluctuation(): Boolean {
        val next = !_config.value.isHumanVolumeFluctuationEnabled
        setHumanVolumeFluctuation(next, _config.value.humanFluctuationIntensity)
        return next
    }

    fun setHumanFluctuationIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.05f, 0.40f)
        repoScope.launch {
            updateConfig { it.copy(humanFluctuationIntensity = clamped) }
            musicPlayer.setHumanFluctuationIntensity(clamped)
        }
    }

    private fun broadcastVoiceEffectEvent(effect: com.tajasb.model.VoiceEffect) {
        broadcastControlEvent(action = "SET_VOICE_EFFECT")
        try {
            val intent = Intent("com.tajasb.action.SET_VOICE_EFFECT").apply {
                putExtra("extra_voice_effect", effect.name)
            }
            context.sendBroadcast(intent)
            val targets = (listOfNotNull(
                _config.value.primaryTargetPackage.takeIf { it.isNotBlank() },
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill"
            ) + _config.value.targetPackages).distinct()
            for (tp in targets) {
                try {
                    context.sendBroadcast(Intent(intent).apply { setPackage(tp) })
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    @Volatile
    private var lastSessionRotationMs: Long = 0L

    @Volatile
    private var isCameraStreamActive: Boolean = false

    @Volatile
    private var currentCameraSessionStartMs: Long = 0L

    @Volatile
    private var pendingSessionDnaRotationOnNextOpen: Boolean = false

    private val sessionLifecycleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingSessionCloseRunnable: Runnable? = null

    /**
     * Dipanggil saat kamera virtual mulai dibuka pada sesi baru (playVideoOnSurface).
     * Mengembalikan Pair(SessionDnaProfile aktif, apakah baru saja dirotasi untuk sesi baru).
     */
    @Synchronized
    fun onCameraSessionStarted(callerPackage: String? = null): Pair<com.tajasb.model.SessionDnaProfile, Boolean> {
        pendingSessionCloseRunnable?.let { sessionLifecycleHandler.removeCallbacks(it) }
        pendingSessionCloseRunnable = null

        val now = System.currentTimeMillis()
        val prevBeat = prefs.getLong("last_camera_stream_heartbeat_ms", 0L)
        val prevStop = prefs.getLong("last_camera_stream_stop_ms", 0L)
        val isPendingNext = pendingSessionDnaRotationOnNextOpen || prefs.getBoolean("pending_session_dna_rotation", false)
        val wasDisconnectedAfterStream = prevBeat > 0L && (now - prevBeat) >= 6_000L

        var didRotate = false
        if (_config.value.enableDnaModule && ((isPendingNext && (prevStop == 0L || (now - prevStop) >= 1_200L)) || wasDisconnectedAfterStream)) {
            pendingSessionDnaRotationOnNextOpen = false
            prefs.edit().putBoolean("pending_session_dna_rotation", false).apply()
            if ((now - lastSessionRotationMs) > 2_500L) {
                Log.i(TAG, "🧬 [AUTO-DNA] Masuk sesi Live baru (${callerPackage ?: "target"}) -> Otomatis memutar ke DNA Sesi berikutnya!")
                rotateToNextSessionDnaSync(reason = "auto_masuk_live_baru")
                didRotate = true
            }
        }

        isCameraStreamActive = true
        currentCameraSessionStartMs = now
        prefs.edit().putLong("last_camera_stream_heartbeat_ms", now).apply()
        return Pair(_config.value.activeSessionDna, didRotate)
    }

    /**
     * Dipanggil saat user keluar dari Live (kamera ditutup / stopVideoPlayback).
     * Setelah kamera benar-benar tertutup selama 2.5 detik (bukan sekadar kedip resolusi internal),
     * otomatis menyiapkan / memutar DNA Sesi ke Sesi berikutnya (#2, #3, #4, dst).
     */
    @Synchronized
    fun onCameraSessionStopped(activeDurationMs: Long = 0L, reason: String = "camera_closed") {
        val now = System.currentTimeMillis()
        val effectiveDurationMs = when {
            activeDurationMs > 0L -> activeDurationMs
            currentCameraSessionStartMs > 0L -> (now - currentCameraSessionStartMs).coerceAtLeast(0L)
            else -> 4_000L
        }
        isCameraStreamActive = false
        currentCameraSessionStartMs = 0L

        if (!_config.value.enableDnaModule) {
            return
        }

        // Hanya hitung sebagai akhir Sesi Live jika kamera sempat aktif minimal 3 detik (bukan probe awal <1 detik)
        if (effectiveDurationMs < 3_000L) {
            return
        }

        pendingSessionDnaRotationOnNextOpen = true
        prefs.edit()
            .putBoolean("pending_session_dna_rotation", true)
            .putLong("last_camera_stream_stop_ms", now)
            .apply()

        pendingSessionCloseRunnable?.let { sessionLifecycleHandler.removeCallbacks(it) }
        val runnable = Runnable {
            synchronized(this@VcamRepository) {
                if (_config.value.enableDnaModule && !isCameraStreamActive && (pendingSessionDnaRotationOnNextOpen || prefs.getBoolean("pending_session_dna_rotation", false))) {
                    pendingSessionDnaRotationOnNextOpen = false
                    prefs.edit().putBoolean("pending_session_dna_rotation", false).apply()
                    if ((System.currentTimeMillis() - lastSessionRotationMs) > 2_500L) {
                        val nextDna = rotateToNextSessionDnaSync(reason = "auto_keluar_live")
                        Log.i(TAG, "🧬 [AUTO-DNA] Sesi Live selesai ($reason, durasi=${effectiveDurationMs / 1000}s) -> Siap di ${nextDna.badgeText} untuk Live berikutnya!")
                    }
                }
            }
        }
        pendingSessionCloseRunnable = runnable
        sessionLifecycleHandler.postDelayed(runnable, 2_500L)
    }

    /**
     * Dipanggil hanya saat renderer kamera virtual benar-benar sedang aktif merender frame.
     */
    fun notifyCameraStreamHeartbeat(): Boolean {
        val now = System.currentTimeMillis()
        val prevBeat = prefs.getLong("last_camera_stream_heartbeat_ms", 0L)
        prefs.edit().putLong("last_camera_stream_heartbeat_ms", now).apply()

        // Jika sebelumnya sesi sempat mati > 6 detik (mis. aplikasi target ditutup paksa lalu dibuka lagi)
        if (_config.value.enableDnaModule && prevBeat > 0L && (now - prevBeat) >= 6_000L && (now - lastSessionRotationMs) > 3_000L) {
            pendingSessionDnaRotationOnNextOpen = false
            prefs.edit().putBoolean("pending_session_dna_rotation", false).apply()
            val gapSec = (now - prevBeat) / 1000L
            Log.i(TAG, "🧬 [AUTO-DNA] Terdeteksi sesi kamera baru setelah jeda ${gapSec}s -> Otomatis memutar ke DNA Sesi berikutnya!")
            isCameraStreamActive = true
            if (currentCameraSessionStartMs == 0L) currentCameraSessionStartMs = now
            rotateToNextSessionDnaSync(reason = "auto_reopen_${gapSec}s")
            return true
        }
        isCameraStreamActive = true
        if (currentCameraSessionStartMs == 0L) currentCameraSessionStartMs = now
        return false
    }

    fun rotateToNextSessionDnaSync(reason: String = "manual"): com.tajasb.model.SessionDnaProfile {
        if (!_config.value.enableDnaModule) {
            Log.i(TAG, "🧬 [DNA-SESI] Saklar DNA Sesi sedang OFF, abaikan rotasi ($reason)")
            return _config.value.activeSessionDna
        }
        val now = System.currentTimeMillis()
        lastSessionRotationMs = now
        pendingSessionDnaRotationOnNextOpen = false
        val nextIndex = (_config.value.sessionDnaIndex + 1).let { if (it > 999) 1 else it }
        val nextSeed = (now xor (nextIndex.toLong() * 0x9E3779B97F4A7C15UL.toLong())) and 0x7FFFFFFFFFFFFFFFL
        prefs.edit()
            .putInt("session_dna_index", nextIndex)
            .putLong("session_dna_seed", nextSeed)
            .putBoolean("pending_session_dna_rotation", false)
            .putLong("last_camera_stream_heartbeat_ms", now)
            .apply()
        val updatedCfg = updateConfigSync {
            it.copy(
                sessionDnaIndex = nextIndex,
                sessionDnaSeed = nextSeed
            )
        }
        val profile = updatedCfg.activeSessionDna
        try {
            musicPlayer.setSessionDna(nextIndex, nextSeed, enabled = updatedCfg.enableDnaModule)
        } catch (_: Throwable) {}
        broadcastSessionDnaEvent(nextIndex, nextSeed, updatedCfg.enableDnaModule)
        Log.i(TAG, "🧬 [DNA-SESI] Diputar ke ${profile.badgeText} (${profile.quadrantLabel}, reason=$reason) | ${profile.summaryText}")
        return profile
    }

    suspend fun rotateToNextSessionDna(reason: String = "manual"): com.tajasb.model.SessionDnaProfile = withContext(Dispatchers.IO) {
        rotateToNextSessionDnaSync(reason)
    }

    private fun broadcastSessionDnaEvent(
        sessionIndex: Int,
        sessionSeed: Long,
        enableDnaModule: Boolean = _config.value.enableDnaModule
    ) {
        broadcastControlEvent(action = "SET_SESSION_DNA")
        try {
            val intent = Intent("com.tajasb.action.SET_SESSION_DNA").apply {
                putExtra("extra_session_dna_index", sessionIndex)
                putExtra("extra_session_dna_seed", sessionSeed)
                putExtra("extra_enable_dna_module", enableDnaModule)
            }
            context.sendBroadcast(intent)
            val targets = (listOfNotNull(
                _config.value.primaryTargetPackage.takeIf { it.isNotBlank() },
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.ss.android.ugc.live",
                "com.ss.android.ugc.aweme"
            ) + _config.value.targetPackages).distinct()
            for (tp in targets) {
                try {
                    context.sendBroadcast(Intent(intent).apply { setPackage(tp) })
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    suspend fun toggleSafeCodecGuard(enabled: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val next = enabled ?: !_config.value.isSafeCodecGuardActive
        updateConfig { it.copy(isSafeCodecGuardActive = next) }
        next
    }

    suspend fun toggleVideoMute(index: Int): Boolean = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (index !in playlist.indices) return@withContext false
        val item = playlist[index]
        val newMuted = !item.isMuted
        val updatedPlaylist = playlist.mapIndexed { i, v ->
            if (i == index) v.copy(isMuted = newMuted) else v
        }

        val isCurrent = (index == _config.value.currentVideoIndex)
        if (isCurrent) {
            updateConfig {
                it.copy(
                    playlist = updatedPlaylist,
                    isVideoAudioMuted = newMuted
                )
            }
            broadcastControlEvent(action = "TOGGLE_MUTE_VIDEO_AUDIO", isVideoAudioMuted = newMuted)

            val isIfsActive = try {
                com.tajasb.infinity.IfsManager.getInstance(context).isIfsActive.value
            } catch (_: Throwable) { false } || _config.value.isIfsMode
            if (!isIfsActive && !_config.value.isDualAudioMixingEnabled && !_config.value.isIndependentAudioEnabled) {
                if (!newMuted) {
                    // Video is unmuted (plays sound) -> auto-pause music if playing
                    if (musicPlayer.isPlaying.value) {
                        wasMusicAutoPausedByVideo = true
                        _isMusicAutoPausedWaitingForMute.value = true
                        withContext(Dispatchers.Main) {
                            musicPlayer.pause()
                        }
                        updateConfig { it.copy(isMp3AudioPaused = true) }
                        broadcastControlEvent(action = "TOGGLE_PAUSE_MP3_AUDIO", isMp3AudioPaused = true)
                    }
                } else {
                    // Video is muted -> resume music if it was auto-paused
                    if (wasMusicAutoPausedByVideo) {
                        wasMusicAutoPausedByVideo = false
                        _isMusicAutoPausedWaitingForMute.value = false
                        playAudio()
                    }
                }
            }
        } else {
            updateConfig {
                it.copy(playlist = updatedPlaylist)
            }
        }
        newMuted
    }

    suspend fun toggleVideoEnable(index: Int): Boolean = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (index !in playlist.indices) return@withContext false
        val item = playlist[index]
        val newEnabled = !item.isEnabled
        val updatedPlaylist = playlist.mapIndexed { i, v ->
            if (i == index) v.copy(isEnabled = newEnabled) else v
        }

        updateConfig {
            it.copy(playlist = updatedPlaylist)
        }

        // If currently active video was toggled OFF, automatically switch to next enabled video if available
        if (!newEnabled && index == _config.value.currentVideoIndex) {
            val enabledIndices = updatedPlaylist.indices.filter { updatedPlaylist[it].isEnabled }
            if (enabledIndices.isNotEmpty()) {
                val nextIdx = enabledIndices.firstOrNull { it > index } ?: enabledIndices.first()
                selectVideoByIndex(nextIdx)
            }
        }
        writeHookConfigFiles(_config.value)
        newEnabled
    }

    suspend fun setVideoEnable(index: Int, isEnabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (index !in playlist.indices) return@withContext false
        val currentItem = playlist[index]
        if (currentItem.isEnabled == isEnabled) return@withContext isEnabled
        toggleVideoEnable(index)
    }

    suspend fun toggleAllVideosEnabled(): Boolean = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return@withContext false
        val anyEnabled = playlist.any { it.isEnabled }
        val targetEnabled = !anyEnabled
        val updatedPlaylist = playlist.map { it.copy(isEnabled = targetEnabled) }
        updateConfig {
            it.copy(playlist = updatedPlaylist)
        }
        writeHookConfigFiles(_config.value)
        targetEnabled
    }

    suspend fun setAllVideosEnabled(isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return@withContext
        val updatedPlaylist = playlist.map { it.copy(isEnabled = isEnabled) }
        updateConfig {
            it.copy(playlist = updatedPlaylist)
        }
        writeHookConfigFiles(_config.value)
    }

    suspend fun toggleAllVideosMute(): Boolean = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        val isMutedCurrently = if (playlist.isNotEmpty()) {
            playlist.all { it.isMuted }
        } else {
            _config.value.isVideoAudioMuted
        }
        val targetMuted = !isMutedCurrently

        val updatedPlaylist = playlist.map { it.copy(isMuted = targetMuted) }
        updateConfig {
            it.copy(
                playlist = updatedPlaylist,
                isVideoAudioMuted = targetMuted
            )
        }
        writeHookConfigFiles(_config.value)
        broadcastControlEvent(action = "TOGGLE_MUTE_VIDEO_AUDIO", isVideoAudioMuted = targetMuted)

        val isIfsActive = try {
            com.tajasb.infinity.IfsManager.getInstance(context).isIfsActive.value
        } catch (_: Throwable) { false } || _config.value.isIfsMode
        if (!isIfsActive && !_config.value.isDualAudioMixingEnabled && !_config.value.isIndependentAudioEnabled) {
            if (!targetMuted && musicPlayer.isPlaying.value) {
                wasMusicAutoPausedByVideo = true
                _isMusicAutoPausedWaitingForMute.value = true
                withContext(Dispatchers.Main) {
                    musicPlayer.pause()
                }
                updateConfig { it.copy(isMp3AudioPaused = true) }
                broadcastControlEvent(action = "TOGGLE_PAUSE_MP3_AUDIO", isMp3AudioPaused = true)
            } else if (targetMuted && wasMusicAutoPausedByVideo) {
                wasMusicAutoPausedByVideo = false
                _isMusicAutoPausedWaitingForMute.value = false
                playAudio()
            }
        }

        targetMuted
    }

    suspend fun setAllVideosMuted(isMuted: Boolean) = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        val updatedPlaylist = playlist.map { it.copy(isMuted = isMuted) }
        updateConfig {
            it.copy(
                playlist = updatedPlaylist,
                isVideoAudioMuted = isMuted
            )
        }
        writeHookConfigFiles(_config.value)
        broadcastControlEvent(action = "TOGGLE_MUTE_VIDEO_AUDIO", isVideoAudioMuted = isMuted)
    }

    suspend fun toggleVideoAudio(): Boolean {
        return toggleAllVideosMute()
    }

    suspend fun toggleMp3Audio(): Boolean {
        val willPlay = !musicPlayer.isPlaying.value
        wasMusicAutoPausedByVideo = false
        _isMusicAutoPausedWaitingForMute.value = false
        if (willPlay) {
            val cur = _config.value.currentAudio
            if (musicPlayer.currentTrack.value == null && cur != null) {
                musicPlayer.playAudioItem(cur, startPlaying = true)
            } else {
                musicPlayer.play()
            }
        } else {
            musicPlayer.pause()
        }
        val isPaused = !willPlay
        updateConfig { it.copy(isMp3AudioPaused = isPaused) }
        broadcastControlEvent(action = "TOGGLE_PAUSE_MP3_AUDIO", isMp3AudioPaused = isPaused)
        return isPaused
    }

    fun playAudio() {
        wasMusicAutoPausedByVideo = false
        _isMusicAutoPausedWaitingForMute.value = false
        val current = _config.value.currentAudio
        if (musicPlayer.currentTrack.value == null && current != null) {
            musicPlayer.playAudioItem(current, startPlaying = true)
        } else {
            musicPlayer.play()
        }
        repoScope.launch {
            updateConfig { it.copy(isMp3AudioPaused = false) }
            broadcastControlEvent(action = "TOGGLE_PAUSE_MP3_AUDIO", isMp3AudioPaused = false)
        }
    }

    fun pauseAudio() {
        wasMusicAutoPausedByVideo = false
        _isMusicAutoPausedWaitingForMute.value = false
        musicPlayer.pause()
        repoScope.launch {
            updateConfig { it.copy(isMp3AudioPaused = true) }
            broadcastControlEvent(action = "TOGGLE_PAUSE_MP3_AUDIO", isMp3AudioPaused = true)
        }
    }

    fun seekAudio(positionMs: Long) {
        musicPlayer.seekTo(positionMs)
    }

    fun setAudioVolume(vol: Float) {
        musicPlayer.setVolume(vol)
    }

    fun toggleAudioLoop(): Boolean {
        return musicPlayer.toggleLoopMode()
    }

    suspend fun togglePauseAudio(): Boolean {
        return toggleMp3Audio()
    }

    suspend fun togglePause(): Boolean {
        return togglePauseVideo()
    }

    suspend fun toggleAudio(): Boolean {
        val nextAudio = !_config.value.audioEnabled
        updateConfig { it.copy(audioEnabled = nextAudio) }
        broadcastControlEvent(action = "AUDIO", audioEnabled = nextAudio)
        return nextAudio
    }

    suspend fun setFloatingAlpha(alpha: Float) {
        // Batas minimal 50% (0.50f) dan maksimal 100% (1.0f) agar tidak hilang dari layar
        val clamped = alpha.coerceIn(0.50f, 1.0f)
        updateConfig { it.copy(floatingAlpha = clamped) }
    }

    suspend fun setScaleMode(mode: ScaleMode) {
        updateConfig { it.copy(scaleMode = mode) }
        broadcastControlEvent(action = "SCALE", scaleMode = mode.name)
    }

    suspend fun cycleScaleMode(): ScaleMode {
        val nextMode = when (_config.value.scaleMode) {
            ScaleMode.CROP -> ScaleMode.FIT
            ScaleMode.FIT -> ScaleMode.STRETCH
            ScaleMode.STRETCH -> ScaleMode.CROP
        }
        updateConfig { it.copy(scaleMode = nextMode) }
        broadcastControlEvent(action = "SCALE", scaleMode = nextMode.name)
        return nextMode
    }

    suspend fun setFps(fps: Int): Int {
        val safe = fps.coerceIn(15, 60)
        updateConfig { it.copy(fps = safe) }
        broadcastControlEvent(action = "FPS", extraSource = "$safe")
        return safe
    }

    suspend fun cycleFps(): Int {
        val next = when (_config.value.fps) {
            15 -> 24
            24 -> 30
            30 -> 60
            60 -> 15
            else -> 30
        }
        return setFps(next)
    }

    // ==========================================
    // MULTI-VIDEO PLAYLIST MANAGEMENT
    // ==========================================

    suspend fun addVideos(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var addedCount = 0
        val newItems = mutableListOf<VideoItem>()
        val conceptMgr = ConceptBackupManager.getInstance(context)
        val existingActivePaths = _config.value.playlist.map {
            try { File(it.path).canonicalPath } catch (_: Exception) { it.path }
        }.toMutableSet()

        for (uri in uris) {
            try {
                val fileName = getFileName(uri) ?: "video_${System.currentTimeMillis()}_${addedCount + 1}.mp4"
                val cleanName = if (fileName.endsWith(".mp4", true)) fileName else "$fileName.mp4"
                val storedPair = conceptMgr.storeOrReuseVaultMediaFile(
                    vaultDir = getInternalCamera1Directory(),
                    uri = uri,
                    rawFileName = cleanName,
                    isAudio = false
                ) ?: continue

                val destFile = storedPair.first
                val displayCleanName = storedPair.second
                val canonPath = try { destFile.canonicalPath } catch (_: Exception) { destFile.absolutePath }

                // Cegah duplikat di dalam playlist aktif yang sama
                if (existingActivePaths.contains(canonPath) || existingActivePaths.contains(destFile.absolutePath)) {
                    addedCount++
                    continue
                }

                if (destFile.exists() && destFile.length() > 0L) {
                    val savedMeta = conceptMgr.findSavedVideoMetadataByPath(destFile.absolutePath)
                    val meta = retrieveMediaMetadata(destFile.absolutePath)
                    val item = VideoItem(
                        id = savedMeta?.id ?: UUID.randomUUID().toString(),
                        name = displayCleanName,
                        path = destFile.absolutePath,
                        uri = uri.toString(),
                        durationMs = meta?.durationMs ?: savedMeta?.durationMs ?: 0L,
                        sizeBytes = destFile.length(),
                        width = meta?.width ?: savedMeta?.width ?: 1080,
                        height = meta?.height ?: savedMeta?.height ?: 1920,
                        spillKeyword = savedMeta?.spillKeyword ?: ""
                    )
                    newItems.add(item)
                    existingActivePaths.add(canonPath)
                    addedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (newItems.isNotEmpty()) {
            val cleanExisting = _config.value.playlist.filterNot { isRollingVsItem(it) }
            val updatedPlaylist = cleanExisting + newItems
            // Automatically switch active video to the newly added video item
            val newIndex = (updatedPlaylist.size - newItems.size).coerceIn(0, updatedPlaylist.size - 1)
            val activeItem = updatedPlaylist[newIndex]

            val updatedConfig = _config.value.copy(
                playlist = updatedPlaylist,
                currentVideoIndex = newIndex,
                videoPath = activeItem.path,
                videoUri = activeItem.uri,
                isVideoAudioMuted = activeItem.isMuted,
                isVideoPaused = false
            )
            updateConfig { updatedConfig }
            bumpPlaylistVersion()
            bumpPlaybackStepVersion()

            try {
                com.tajasb.infinity.IfsManager.getInstance(context).onMediaPlaylistChanged(
                    newVideos = updatedPlaylist,
                    newAudios = updatedConfig.audioPlaylist,
                    broadcastIfActive = true
                )
            } catch (_: Throwable) {}

            val finalCfg = _config.value
            val finalActive = finalCfg.currentVideo ?: activeItem

            // Tulis hook config files lengkap (playlist.txt, video_0..N-1.mp4) dan salin virtual.mp4 sebelum broadcast
            writeHookConfigFiles(finalCfg)
            try {
                copyToAllLocations(File(finalActive.path), "virtual.mp4")
            } catch (_: Exception) {}

            // Broadcast immediately to virtual camera hooks with cache purge so hook starts fresh with new videos
            broadcastControlEvent(
                action = "SELECT_VIDEO",
                videoPath = finalActive.path,
                isVideoPaused = false,
                isVideoAudioMuted = finalActive.isMuted,
                extraSource = "VIDEO_ADDED",
                extraPurgeCache = true
            )

            // Sync target sandboxes, scan AI watermark, and auto-checkpoint asynchronously
            repoScope.launch(Dispatchers.IO) {
                try {
                    scanActiveVideoAiWatermark(finalActive.path)
                    syncToTargetApps(_config.value.targetPackages)
                    ConceptBackupManager.getInstance(context).scheduleAutoCheckpoint()
                } catch (_: Exception) {}
            }
        }
        addedCount
    }

    suspend fun removeVideo(id: String) = withContext(Dispatchers.IO) {
        val currentList = _config.value.playlist
        val indexToRemove = currentList.indexOfFirst { it.id == id }
        if (indexToRemove == -1) return@withContext

        val itemToRemove = currentList[indexToRemove]
        val conceptMgr = ConceptBackupManager.getInstance(context)
        val isProtectedBySavedConcept = conceptMgr.isMediaPathProtectedByAnyConcept(itemToRemove.path)

        // Hanya hapus file fisik di Vault jika TIDAK dipakai oleh Konsep Backup mana pun
        if (!isProtectedBySavedConcept) {
            try { File(itemToRemove.path).delete() } catch (_: Exception) {}
        }

        val internalVaultCanon = try { getInternalCamera1Directory().canonicalPath } catch (_: Exception) { "" }
        val allHookDirs = getAllHookDirectories()
        for (dir in allHookDirs) {
            try {
                val dirCanon = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
                // Jangan hapus dari direktori internal jika file dilindungi oleh Konsep Backup
                if (isProtectedBySavedConcept && dirCanon == internalVaultCanon) continue
                val fName = File(itemToRemove.path).name
                val copyInDir = File(dir, fName)
                if (copyInDir.exists()) copyInDir.delete()
            } catch (_: Exception) {}
        }

        val updatedList = currentList.filter { it.id != id && !isRollingVsItem(it) }
        val newIndex = if (updatedList.isEmpty()) 0 else {
            when {
                _config.value.currentVideoIndex > indexToRemove -> (_config.value.currentVideoIndex - 1).coerceIn(0, updatedList.size - 1)
                _config.value.currentVideoIndex == indexToRemove -> indexToRemove.coerceIn(0, updatedList.size - 1)
                else -> _config.value.currentVideoIndex.coerceIn(0, updatedList.size - 1)
            }
        }

        val activeItem = if (updatedList.isNotEmpty()) updatedList[newIndex] else null
        val activePath = activeItem?.path ?: ""
        val activeMuted = activeItem?.isMuted ?: false

        // Delete any indexed files beyond new playlist size (e.g. video_2.mp4 when size is now 2)
        MediaFileStorageHelper.cleanupStaleIndexedVideos(allHookDirs, updatedList.size)

        // Clean up orphaned video files not in updatedList - PROTECT all remaining items & all saved Concept Backups!
        val validPaths = updatedList.flatMap { item ->
            val f = File(item.path)
            listOf(item.path, f.absolutePath, try { f.canonicalPath } catch (_: Exception) { item.path })
        }.toSet() + conceptMgr.getAllProtectedMediaPaths()
        val validFileNames = updatedList.map { File(it.path).name }.toSet() + conceptMgr.getAllProtectedMediaFileNames()
        MediaFileStorageHelper.cleanupOrphanedMediaFiles(
            hookDirs = allHookDirs,
            validMediaPaths = validPaths,
            validFileNames = validFileNames,
            validCount = updatedList.size
        )

        val updatedConfig = _config.value.copy(
            playlist = updatedList,
            currentVideoIndex = newIndex,
            videoPath = activePath,
            videoUri = activeItem?.uri,
            isVideoAudioMuted = activeMuted,
            isVideoPaused = false,
            masterVideoId = if (_config.value.masterVideoId == id) updatedList.firstOrNull()?.id else _config.value.masterVideoId,
            bridge1VideoId = if (_config.value.bridge1VideoId == id) null else _config.value.bridge1VideoId,
            bridge2VideoId = if (_config.value.bridge2VideoId == id) null else _config.value.bridge2VideoId,
            activeSpillVideoId = if (_config.value.activeSpillVideoId == id) null else _config.value.activeSpillVideoId,
            pendingVideoIndex = null,
            bridgeContentQueue = emptyList()
        )

        updateConfig { updatedConfig }
        bumpPlaylistVersion()
        bumpPlaybackStepVersion()

        try {
            val ifsMgr = com.tajasb.infinity.IfsManager.getInstance(context)
            ifsMgr.clearVideoFromAllSlots(id)
            ifsMgr.onMediaPlaylistChanged(
                newVideos = updatedList,
                newAudios = updatedConfig.audioPlaylist,
                broadcastIfActive = true
            )
        } catch (_: Throwable) {}

        val finalCfg = _config.value
        val finalActivePath = finalCfg.videoPath
        val finalActiveMuted = finalCfg.isVideoAudioMuted

        // Write updated hook config files (updates playlist.txt, current_index.txt, video_0..N-1.mp4)
        writeHookConfigFiles(finalCfg)

        if (finalActivePath.isNotEmpty() && File(finalActivePath).exists()) {
            copyToAllLocations(File(finalActivePath), "virtual.mp4")
            syncToTargetApps(finalCfg.targetPackages)
            broadcastControlEvent(
                action = "SELECT_VIDEO",
                videoPath = finalActivePath,
                isVideoPaused = false,
                isVideoAudioMuted = finalActiveMuted,
                extraSource = "VIDEO_REMOVED",
                extraPurgeCache = true
            )
        } else {
            cleanupStaleVirtualVideo()
            syncToTargetApps(finalCfg.targetPackages)
            broadcastControlEvent(
                action = "RELOAD_MEDIA",
                videoPath = "",
                extraSource = "ALL_VIDEOS_REMOVED",
                extraPurgeCache = true
            )
        }
        conceptMgr.onMediaRemovedFromActivePlaylist(listOf(itemToRemove.path))
    }

    suspend fun removeAllVideos() = withContext(Dispatchers.IO) {
        val currentList = _config.value.playlist
        val removedPaths = currentList.map { it.path }
        val conceptMgr = ConceptBackupManager.getInstance(context)
        currentList.forEach { item ->
            // Tetap simpan aman file fisik jika masih dipakai di Konsep Backup lain
            if (!conceptMgr.isMediaPathProtectedByAnyConcept(item.path)) {
                try { File(item.path).delete() } catch (_: Exception) {}
            }
        }
        val allHookDirs = getAllHookDirectories()
        MediaFileStorageHelper.cleanupStaleIndexedVideos(allHookDirs, 0)
        cleanupStaleVirtualVideo()
        MediaFileStorageHelper.cleanupOrphanedMediaFiles(
            hookDirs = allHookDirs,
            validMediaPaths = conceptMgr.getAllProtectedMediaPaths(),
            validFileNames = conceptMgr.getAllProtectedMediaFileNames(),
            validCount = 0
        )

        val updatedConfig = _config.value.copy(
            playlist = emptyList(),
            currentVideoIndex = 0,
            videoPath = "",
            videoUri = null,
            masterVideoId = null,
            bridge1VideoId = null,
            bridge2VideoId = null,
            activeSpillVideoId = null,
            pendingVideoIndex = null,
            bridgeContentQueue = emptyList()
        )
        updateConfig { updatedConfig }
        bumpPlaylistVersion()
        bumpPlaybackStepVersion()

        try {
            val ifsMgr = com.tajasb.infinity.IfsManager.getInstance(context)
            ifsMgr.resetAndPurgeAllRollingFilesForProjectSwitch()
            currentList.forEach { ifsMgr.clearVideoFromAllSlots(it.id) }
            ifsMgr.onMediaPlaylistChanged(
                newVideos = emptyList(),
                newAudios = updatedConfig.audioPlaylist,
                broadcastIfActive = false
            )
        } catch (_: Throwable) {}

        writeHookConfigFiles(updatedConfig)
        syncToTargetApps(updatedConfig.targetPackages)
        broadcastControlEvent(
            action = "RELOAD_MEDIA",
            videoPath = "",
            extraSource = "ALL_VIDEOS_REMOVED",
            extraPurgeCache = true
        )
        conceptMgr.onMediaRemovedFromActivePlaylist(removedPaths)
    }

    suspend fun queueCustomVideo(index: Int): VideoItem? = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return@withContext null
        val safeIndex = index.coerceIn(0, playlist.size - 1)
        val selected = playlist[safeIndex]

        _pendingVideoIndex.value = safeIndex
        updateConfig { it.copy(pendingVideoIndex = safeIndex) }

        try {
            copyToAllLocations(File(selected.path), "video_$safeIndex.mp4")
        } catch (_: Exception) {}

        broadcastControlEvent(
            action = "QUEUE_VIDEO",
            videoPath = selected.path,
            isVideoAudioMuted = selected.isMuted,
            extraSource = "CUSTOM_QUEUE"
        )
        selected
    }

    suspend fun cancelQueuedVideo() = withContext(Dispatchers.IO) {
        _pendingVideoIndex.value = null
        updateConfig {
            it.copy(
                pendingVideoIndex = null,
                pendingBridgeContentIndex = null,
                bridgeContentQueue = emptyList()
            )
        }
        broadcastControlEvent(
            action = "CANCEL_QUEUED_VIDEO",
            videoPath = ""
        )
    }

    fun selectVideoByIndexSync(index: Int, broadcastToHook: Boolean = true): VideoItem? {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return null
        val safeIndex = index.coerceIn(0, playlist.size - 1)
        val selected = playlist[safeIndex]
        val isMuted = selected.isMuted

        val now = System.currentTimeMillis()
        lastVideoSwitchTimestampMs = now
        _pendingVideoIndex.value = null

        val masterIdx = getMasterVideoIndex()
        val isMaster = (safeIndex == masterIdx)
        val newSpillId = if (!isMaster && _config.value.isMasterSpillMode) selected.id else null

        updateConfigSync {
            it.copy(
                currentVideoIndex = safeIndex,
                pendingVideoIndex = null,
                activeSpillVideoId = newSpillId,
                videoPath = selected.path,
                videoUri = selected.uri,
                isVideoAudioMuted = isMuted,
                isVideoPaused = false
            )
        }
        bumpPlaybackStepVersion()

        if (broadcastToHook) {
            broadcastControlEvent(
                action = "SELECT_VIDEO",
                videoPath = selected.path,
                isVideoPaused = false,
                isVideoAudioMuted = isMuted
            )
        }

        repoScope.launch(Dispatchers.IO) {
            try {
                scanActiveVideoAiWatermark(selected.path)
                copyToAllLocations(File(selected.path), "virtual.mp4")
            } catch (_: Exception) {}
        }

        onVideoSelectionChanged(safeIndex, isMuted)
        return selected
    }

    suspend fun selectVideoByIndex(index: Int): VideoItem? = withContext(Dispatchers.IO) {
        selectVideoByIndexSync(index, broadcastToHook = true)
    }

    fun syncVideoIndexWithoutBroadcastSync(index: Int, broadcastPreload: Boolean = false): VideoItem? {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return null
        val safeIndex = index.coerceIn(0, playlist.size - 1)
        val selected = playlist[safeIndex]
        val isMuted = selected.isMuted

        val now = System.currentTimeMillis()
        lastVideoSwitchTimestampMs = now
        _pendingVideoIndex.value = null

        val masterIdx = getMasterVideoIndex()
        val isMaster = (safeIndex == masterIdx)
        val newSpillId = if (!isMaster && _config.value.isMasterSpillMode) selected.id else null

        updateConfigSync {
            it.copy(
                currentVideoIndex = safeIndex,
                pendingVideoIndex = null,
                activeSpillVideoId = newSpillId,
                videoPath = selected.path,
                videoUri = selected.uri,
                isVideoAudioMuted = isMuted,
                isVideoPaused = false
            )
        }
        bumpPlaybackStepVersion()

        // Copy virtual.mp4 in background so ContentProvider IPC / UI returns immediately without disk lag
        repoScope.launch(Dispatchers.IO) {
            try {
                copyToAllLocations(File(selected.path), "virtual.mp4")
            } catch (_: Exception) {}
        }

        // Non-blocking audio pause / resume
        onVideoSelectionChanged(safeIndex, isMuted)

        if (broadcastPreload) {
            broadcastControlEvent(
                action = "SELECT_VIDEO",
                videoPath = selected.path,
                isVideoAudioMuted = isMuted,
                extraSource = "SEAMLESS_PRELOAD"
            )
        }

        return selected
    }

    suspend fun syncVideoIndexWithoutBroadcast(index: Int, broadcastPreload: Boolean = false): VideoItem? =
        syncVideoIndexWithoutBroadcastSync(index, broadcastPreload)

    fun nextVideoSync(
        mode: AutoNextMode = _config.value.autoNextMode,
        forceSequential: Boolean = false,
        broadcastToHook: Boolean = true
    ): VideoItem? {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return null

        val now = System.currentTimeMillis()
        val debounceMs = if (forceSequential) 180L else 350L
        if (now - lastNextVideoTimeMs < debounceMs) {
            Log.d("VcamRepository", "nextVideo: Mengabaikan pemanggilan ganda dalam ${debounceMs}ms")
            return _config.value.currentVideo
        }
        lastNextVideoTimeMs = now

        // -------------------------------------------------------------
        // SAKLAR INFINITY SYSTEM (IFS) - PRIORITAS UTAMA SISTEM INDEPENDEN
        // -------------------------------------------------------------
        val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(context) } catch (_: Throwable) { null }
        val isIfs = ifsMgr?.isIfsActive?.value == true || _config.value.isIfsMode
        if (isIfs) {
            Log.i("VcamRepository", "♾️ [IFS] nextVideo diarahkan ke alur Infinity System (forceAdvance=$forceSequential)")
            val nextItem = ifsMgr?.onVideoCompleted(
                forceAdvanceFromBl = forceSequential,
                broadcastToHook = broadcastToHook
            )
            if (nextItem != null) {
                return nextItem
            }
            return _config.value.currentVideo
        }

        // -------------------------------------------------------------
        // SAKLAR SIKLUS BRIDGE (Bridge I -> Master Loop -> Bridge II -> Konten -> Bridge I -> Master Loop)
        // -------------------------------------------------------------
        if (_config.value.isBridgeCycleMode) {
            return advanceBridgeCycleSync(broadcastToHook = broadcastToHook)
        }

        // Master Loop & Spill mandiri: Hanya tangani otomatis jika bukan pemanggilan navigasi manual forceSequential
        if (_config.value.isMasterSpillMode && !forceSequential) {
            return advanceMasterSpillSync(broadcastToHook = broadcastToHook)
        }

        // Konsumsi antrean video (Mode Tunggu Selesai / WAIT_FINISH) jika ada
        val pending = _pendingVideoIndex.value
        if (pending != null && pending in playlist.indices) {
            _pendingVideoIndex.value = null
            return if (broadcastToHook) {
                selectVideoByIndexSync(pending, broadcastToHook = true)
            } else {
                syncVideoIndexWithoutBroadcastSync(pending, broadcastPreload = false)
            }
        }

        val enabledIndices = playlist.indices.filter { playlist[it].isEnabled }
        val candidateIndices = if (enabledIndices.isNotEmpty()) enabledIndices else playlist.indices.toList()
        val curIdx = _config.value.currentVideoIndex

        val effectiveMode = if (forceSequential) AutoNextMode.SEQUENTIAL else mode
        val nextIndex = when (effectiveMode) {
            AutoNextMode.SINGLE_LOOP -> if (forceSequential) {
                val higher = candidateIndices.filter { it > curIdx }
                if (higher.isNotEmpty()) higher.first() else candidateIndices.first()
            } else curIdx.coerceIn(0, playlist.lastIndex)
            AutoNextMode.RANDOM -> getNextShuffledIndex(candidateIndices, curIdx)
            else -> {
                val higher = candidateIndices.filter { it > curIdx }
                if (higher.isNotEmpty()) higher.first() else candidateIndices.first()
            }
        }
        return if (broadcastToHook) {
            selectVideoByIndexSync(nextIndex, broadcastToHook = true)
        } else {
            syncVideoIndexWithoutBroadcastSync(nextIndex, broadcastPreload = false)
        }
    }

    suspend fun nextVideo(
        mode: AutoNextMode = _config.value.autoNextMode,
        forceSequential: Boolean = false,
        broadcastToHook: Boolean = true
    ): VideoItem? = nextVideoSync(mode, forceSequential, broadcastToHook)

    suspend fun toggleAutoNext(enabled: Boolean? = null): Boolean {
        val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(context) } catch (_: Throwable) { null }
        if (ifsMgr?.isIfsActive?.value == true || _config.value.isIfsMode) {
            Log.w("VcamRepository", "toggleAutoNext ditolak: Infinity System (IFS) sedang aktif. Matikan IFS terlebih dahulu jika ingin menggunakan loop standar.")
            return false
        }
        val next = enabled ?: !_config.value.autoNextEnabled
        updateConfig { it.copy(autoNextEnabled = next) }
        broadcastControlEvent(action = "UPDATE_CONFIG", autoNextEnabled = next)
        return next
    }

    suspend fun setAutoNextMode(mode: AutoNextMode) {
        val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(context) } catch (_: Throwable) { null }
        if (ifsMgr?.isIfsActive?.value == true || _config.value.isIfsMode) {
            Log.w("VcamRepository", "setAutoNextMode ditolak: Infinity System (IFS) sedang aktif.")
            return
        }
        if (mode == AutoNextMode.RANDOM) {
            resetShuffleDeck()
        }
        updateConfig { it.copy(autoNextMode = mode) }
        broadcastControlEvent(action = "UPDATE_CONFIG", autoNextMode = mode.name)
    }

    suspend fun setAutoNextLeadSeconds(seconds: Int) {
        val sec = seconds.coerceIn(1, 15)
        val ms = sec * 1000
        updateConfig { it.copy(autoNextLeadSeconds = sec, autoNextLeadMs = ms) }
        broadcastControlEvent(action = "UPDATE_CONFIG", autoNextLeadSeconds = sec, autoNextLeadMs = ms)
    }

    suspend fun setAutoNextLeadMs(ms: Int) {
        val safeMs = ms.coerceIn(50, 15000)
        val sec = (safeMs / 1000).coerceAtLeast(1)
        updateConfig { it.copy(autoNextLeadMs = safeMs, autoNextLeadSeconds = sec) }
        broadcastControlEvent(action = "UPDATE_CONFIG", autoNextLeadMs = safeMs, autoNextLeadSeconds = sec)
    }

    suspend fun setVideoSwitchMode(mode: com.tajasb.model.VideoSwitchMode) {
        updateConfig { it.copy(videoSwitchMode = mode) }
        broadcastControlEvent(action = "UPDATE_CONFIG", videoSwitchMode = mode.name, extraSource = "SWITCH_MODE_${mode.name}")
    }

    suspend fun toggleVideoSwitchMode(): com.tajasb.model.VideoSwitchMode {
        val current = _config.value.videoSwitchMode
        val next = if (current == com.tajasb.model.VideoSwitchMode.INSTANT) com.tajasb.model.VideoSwitchMode.WAIT_FINISH else com.tajasb.model.VideoSwitchMode.INSTANT
        setVideoSwitchMode(next)
        return next
    }

    suspend fun queueNextVideo(): VideoItem? = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return@withContext null
        val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(context) } catch (_: Throwable) { null }
        val isIfs = ifsMgr?.isIfsActive?.value == true || _config.value.isIfsMode
        if (isIfs) {
            return@withContext ifsMgr?.onVideoCompleted(forceAdvanceFromBl = true, broadcastToHook = true) ?: _config.value.currentVideo
        }
        val enabledIndices = playlist.indices.filter { playlist[it].isEnabled }
        val candidateIndices = if (enabledIndices.isNotEmpty()) enabledIndices else playlist.indices.toList()
        val curIdx = _config.value.currentVideoIndex
        val nextIndex = when (_config.value.autoNextMode) {
            AutoNextMode.RANDOM -> shuffleDeckManager.peekNextShuffledIndex(candidateIndices, curIdx)
            else -> {
                val higher = candidateIndices.filter { it > curIdx }
                if (higher.isNotEmpty()) higher.first() else candidateIndices.first()
            }
        }
        queueCustomVideo(nextIndex)
    }

    suspend fun queuePrevVideo(): VideoItem? = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return@withContext null

        val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(context) } catch (_: Throwable) { null }
        if (ifsMgr?.isIfsActive?.value == true || _config.value.isIfsMode) {
            return@withContext _config.value.currentVideo
        }

        val enabledIndices = playlist.indices.filter { playlist[it].isEnabled }
        val candidateIndices = if (enabledIndices.isNotEmpty()) enabledIndices else playlist.indices.toList()
        val curIdx = _config.value.currentVideoIndex
        val lower = candidateIndices.filter { it < curIdx }
        val prevIndex = if (lower.isNotEmpty()) lower.last() else candidateIndices.last()
        queueCustomVideo(prevIndex)
    }

    suspend fun prevVideo(): VideoItem? = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return@withContext null

        val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(context) } catch (_: Throwable) { null }
        val isIfs = ifsMgr?.isIfsActive?.value == true || _config.value.isIfsMode
        if (isIfs) {
            Log.i("VcamRepository", "♾️ [IFS] prevVideo: Mengulang video aktif dari 0ms agar tidak merusak alur siklus IFS")
            seekVideo(0L)
            return@withContext _config.value.currentVideo
        }

        if (_config.value.isMasterSpillMode && !_config.value.isCurrentPlayingMaster) {
            return@withContext returnToMasterVideo(immediate = true)
        }

        val enabledIndices = playlist.indices.filter { playlist[it].isEnabled }
        val candidateIndices = if (enabledIndices.isNotEmpty()) enabledIndices else playlist.indices.toList()
        val curIdx = _config.value.currentVideoIndex

        val prevIndex = if (_config.value.autoNextMode == AutoNextMode.RANDOM) {
            getPrevShuffledIndex(candidateIndices, curIdx)
        } else {
            val lower = candidateIndices.filter { it < curIdx }
            if (lower.isNotEmpty()) lower.last() else candidateIndices.last()
        }
        selectVideoByIndex(prevIndex)
    }

    data class NextVideoPrediction(
        val nextIndex: Int,
        val nextItem: VideoItem?,
        val isSameSlotLoop: Boolean,
        val modeLabel: String
    )

    fun peekNextVideoDetailed(mode: AutoNextMode = _config.value.autoNextMode): NextVideoPrediction {
        val cfg = _config.value
        val playlist = cfg.playlist
        if (playlist.isEmpty()) return NextVideoPrediction(-1, null, false, "EMPTY")
        val curIdx = cfg.currentVideoIndex.coerceIn(0, playlist.lastIndex)

        // 1. Mode Infinity System (IFS: SV1, SV2, SV3)
        val ifsManager = try { com.tajasb.infinity.IfsManager.getInstance(context) } catch (_: Throwable) { null }
        val isIfs = (ifsManager?.isIfsActive?.value == true) || cfg.isIfsMode
        if (isIfs && ifsManager != null) {
            val (ifsNextItem, isSameSlotLoop) = ifsManager.peekNextPhaseVideo()
            if (ifsNextItem != null) {
                val resolvedIdx = playlist.indexOfFirst { it.id == ifsNextItem.id }.takeIf { it >= 0 } ?: curIdx
                return NextVideoPrediction(
                    nextIndex = resolvedIdx,
                    nextItem = ifsNextItem,
                    isSameSlotLoop = isSameSlotLoop,
                    modeLabel = "IFS_${ifsManager.videoMode.value.name}"
                )
            }
        }

        // 2. Mode Siklus Bridge (BRIDGE_1 -> MASTER_LOOP -> BRIDGE_2 -> CONTENT_VIDEO -> BRIDGE_1)
        if (cfg.isBridgeCycleMode) {
            val bridge1Idx = cfg.bridge1VideoIndex.coerceIn(0, playlist.lastIndex)
            val masterIdx = cfg.masterVideoIndex.coerceIn(0, playlist.lastIndex)
            val bridge2Idx = cfg.bridge2VideoIndex.coerceIn(0, playlist.lastIndex)
            val curPhase = cfg.currentBridgePhase
            val effectivePending = cfg.pendingBridgeContentIndex ?: cfg.bridgeContentQueue.firstOrNull()

            val (nextIdx, isSameLoop) = when (curPhase) {
                BridgeCyclePhase.BRIDGE_1 -> Pair(masterIdx, false)
                BridgeCyclePhase.MASTER_LOOP -> {
                    if (effectivePending != null && effectivePending in playlist.indices) {
                        Pair(bridge2Idx, false)
                    } else {
                        Pair(masterIdx, true)
                    }
                }
                BridgeCyclePhase.BRIDGE_2 -> {
                    val targetContentIdx = if (effectivePending != null && effectivePending in playlist.indices) {
                        effectivePending
                    } else {
                        cfg.contentVideos.firstOrNull()?.first ?: masterIdx
                    }
                    Pair(targetContentIdx.coerceIn(0, playlist.lastIndex), false)
                }
                BridgeCyclePhase.CONTENT_VIDEO, BridgeCyclePhase.OFF -> Pair(bridge1Idx, false)
            }
            return NextVideoPrediction(
                nextIndex = nextIdx,
                nextItem = playlist.getOrNull(nextIdx),
                isSameSlotLoop = isSameLoop,
                modeLabel = "BRIDGE_${curPhase.name}"
            )
        }

        // 3. Mode Master & Spill (Master Loop <-> Spill 1x)
        if (cfg.isMasterSpillMode) {
            val masterIdx = getMasterVideoIndex().coerceIn(0, playlist.lastIndex)
            val pending = _pendingVideoIndex.value
            return if (pending != null && pending in playlist.indices) {
                NextVideoPrediction(
                    nextIndex = pending,
                    nextItem = playlist.getOrNull(pending),
                    isSameSlotLoop = (pending == curIdx),
                    modeLabel = "MASTER_TO_SPILL"
                )
            } else if (curIdx != masterIdx || cfg.activeSpillVideoId != null) {
                NextVideoPrediction(
                    nextIndex = masterIdx,
                    nextItem = playlist.getOrNull(masterIdx),
                    isSameSlotLoop = false,
                    modeLabel = "SPILL_TO_MASTER"
                )
            } else {
                NextVideoPrediction(
                    nextIndex = masterIdx,
                    nextItem = playlist.getOrNull(masterIdx),
                    isSameSlotLoop = true,
                    modeLabel = "MASTER_LOOP"
                )
            }
        }

        // 4. Mode Standar (Urut / Acak / Loop 1 Video + Antrean Manual)
        val pending = _pendingVideoIndex.value
        if (pending != null && pending in playlist.indices) {
            return NextVideoPrediction(
                nextIndex = pending,
                nextItem = playlist[pending],
                isSameSlotLoop = (pending == curIdx),
                modeLabel = "QUEUED_MANUAL"
            )
        }

        val enabledIndices = playlist.indices.filter { playlist[it].isEnabled }
        val candidateIndices = if (enabledIndices.isNotEmpty()) enabledIndices else playlist.indices.toList()

        val nextIndex = when (mode) {
            AutoNextMode.SINGLE_LOOP -> curIdx
            AutoNextMode.RANDOM -> shuffleDeckManager.peekNextShuffledIndex(candidateIndices, curIdx)
            else -> {
                val higher = candidateIndices.filter { it > curIdx }
                if (higher.isNotEmpty()) higher.first() else candidateIndices.first()
            }
        }
        val isSameLoop = (mode == AutoNextMode.SINGLE_LOOP) || (nextIndex == curIdx && candidateIndices.size <= 1)
        return NextVideoPrediction(
            nextIndex = nextIndex,
            nextItem = playlist.getOrNull(nextIndex),
            isSameSlotLoop = isSameLoop,
            modeLabel = "STANDARD_${mode.name}"
        )
    }

    fun peekNextVideo(mode: AutoNextMode = _config.value.autoNextMode): Pair<Int, VideoItem?> {
        val pred = peekNextVideoDetailed(mode)
        return Pair(pred.nextIndex, pred.nextItem)
    }

    /**
     * Menggabungkan semua video aktif di daftar putar menjadi 1 file MP4 utuh berkesinambungan (Mode CapCut)
     * dan langsung mengaktifkannya dalam Mode Standar (Loop 1 Video Gabungan) tanpa jeda transisi.
     */
    suspend fun stitchPlaylistToSingleVideo(
        onProgress: ((Int, String) -> Unit)? = null
    ): com.tajasb.engine.SmartVideoStitcher.StitchResult = withContext(Dispatchers.IO) {
        val currentList = _config.value.playlist
        val enabledItems = currentList.filter { it.isEnabled && !it.name.startsWith("Gabungan_Seamless_", ignoreCase = true) }
        val sourceItems = if (enabledItems.size >= 2) {
            enabledItems
        } else {
            currentList.filter { !it.name.startsWith("Gabungan_Seamless_", ignoreCase = true) }
        }

        if (sourceItems.size < 2) {
            return@withContext com.tajasb.engine.SmartVideoStitcher.StitchResult(
                success = false,
                outputFile = null,
                message = "Minimal dibutuhkan 2 video pendek di daftar putar untuk digabungkan."
            )
        }

        val inputFiles = sourceItems.map { File(it.path) }
        val allMuted = sourceItems.all { it.isMuted } || _config.value.isVideoAudioMuted
        val outDir = getInternalCamera1Directory()
        val outFileName = "Gabungan_Seamless_${sourceItems.size}Video_${System.currentTimeMillis() % 100000}.mp4"
        val outFile = File(outDir, outFileName)

        val result = com.tajasb.engine.SmartVideoStitcher.stitchVideos(
            context = context,
            inputFiles = inputFiles,
            outputFile = outFile,
            includeAudioIfCompatible = !allMuted,
            onProgress = onProgress
        )

        if (result.success && result.outputFile != null && result.outputFile.exists()) {
            val destFile = result.outputFile
            val meta = retrieveMediaMetadata(destFile.absolutePath)
            val displayTitle = "Gabungan_Seamless_${result.mergedCount}Video.mp4"
            val newItem = VideoItem(
                id = UUID.randomUUID().toString(),
                name = displayTitle,
                path = destFile.absolutePath,
                uri = Uri.fromFile(destFile).toString(),
                durationMs = meta?.durationMs ?: result.totalDurationMs,
                sizeBytes = destFile.length(),
                width = meta?.width ?: sourceItems.first().width,
                height = meta?.height ?: sourceItems.first().height,
                isMuted = allMuted,
                isEnabled = true
            )

            // Hapus file gabungan lama jika ada agar tidak menumpuk di memori, dan nonaktifkan klip pendek penyusunnya
            // agar pemutaran fokus 100% pada 1 Video Gabungan Utuh (klip asli tetap tersimpan di daftar bila ingin dibuka lagi)
            val cleanedList = currentList.filter { old ->
                val isOldStitched = old.name.startsWith("Gabungan_Seamless_", ignoreCase = true)
                if (isOldStitched) {
                    try { File(old.path).delete() } catch (_: Throwable) {}
                }
                !isOldStitched
            }.map { item ->
                item.copy(isEnabled = false)
            }

            val updatedPlaylist = listOf(newItem) + cleanedList
            val updatedConfig = _config.value.copy(
                playlist = updatedPlaylist,
                currentVideoIndex = 0,
                videoPath = newItem.path,
                videoUri = newItem.uri,
                isVideoAudioMuted = newItem.isMuted,
                isVideoPaused = false,
                isIfsMode = false,
                isBridgeCycleMode = false,
                isMasterSpillMode = false,
                autoNextEnabled = true,
                autoNextMode = AutoNextMode.SINGLE_LOOP
            )
            updateConfig { updatedConfig }
            bumpPlaylistVersion()

            writeHookConfigFiles(updatedConfig)
            try {
                copyToAllLocations(destFile, "virtual.mp4")
                syncToTargetApps(updatedConfig.targetPackages)
            } catch (_: Throwable) {}

            broadcastControlEvent(
                action = "SELECT_VIDEO",
                videoPath = newItem.path,
                isVideoPaused = false,
                isVideoAudioMuted = newItem.isMuted,
                autoNextEnabled = true,
                autoNextMode = AutoNextMode.SINGLE_LOOP.name,
                extraSource = "SMART_VIDEO_STITCHER",
                extraPurgeCache = true
            )
        }
        result
    }

    // ==========================================
    // MASTER & SPILL (LIVE STREAMING) MANAGEMENT
    // ==========================================

    fun getMasterVideoIndex(): Int =
        VideoPlaylistRepository.getMasterVideoIndex(_config.value.playlist, _config.value.masterVideoId)

    suspend fun setMasterVideo(id: String) = assignVideoRole(id, com.tajasb.model.VideoRole.MASTER_LOOP)

    /**
     * Atomically switches to Standard Mode (Urut / Loop / Acak) on the calling thread
     * with zero competing coroutines or UI flicker.
     */
    fun activateStandardModeSync(mode: AutoNextMode? = null): AutoNextMode {
        val targetMode = mode ?: _config.value.autoNextMode
        try {
            com.tajasb.infinity.IfsManager.getInstance(context).disableIfsFromExternalModeSwitch()
        } catch (_: Throwable) {}
        if (targetMode == AutoNextMode.RANDOM) {
            resetShuffleDeck()
        }
        _pendingVideoIndex.value = null
        var switchedTargetItem: VideoItem? = null
        updateConfigSync { cur ->
            val cleanList = cur.playlist.filterNot { isRollingVsItem(it) }
            val safeIdx = if (cleanList.isNotEmpty()) {
                val validCurIdx = cleanList.indexOfFirst { it.path == cur.videoPath }.takeIf { it >= 0 }
                    ?: cur.currentVideoIndex.coerceIn(0, cleanList.lastIndex)
                if (cleanList[validCurIdx].isEnabled) {
                    validCurIdx
                } else {
                    cleanList.indexOfFirst { it.isEnabled }.takeIf { it >= 0 } ?: validCurIdx
                }
            } else 0
            val activeItem = cleanList.getOrNull(safeIdx)
            if (activeItem != null && (activeItem.path != cur.videoPath || cleanList.size != cur.playlist.size)) {
                switchedTargetItem = activeItem
            }
            cur.copy(
                playlist = cleanList,
                currentVideoIndex = safeIdx,
                videoPath = activeItem?.path ?: "",
                videoUri = activeItem?.uri ?: "",
                isVideoAudioMuted = activeItem?.isMuted ?: cur.isVideoAudioMuted,
                isIfsMode = false,
                isBridgeCycleMode = false,
                isMasterSpillMode = false,
                activeSpillVideoId = null,
                pendingVideoIndex = null,
                autoNextEnabled = true,
                autoNextMode = targetMode,
                isVideoPaused = false
            )
        }
        bumpPlaybackStepVersion()
        val finalSwitched = switchedTargetItem
        if (finalSwitched != null) {
            val safeIdx = _config.value.currentVideoIndex
            repoScope.launch(Dispatchers.IO) {
                writeHookConfigFiles(_config.value)
                try {
                    copyToAllLocations(File(finalSwitched.path), "virtual.mp4")
                } catch (_: Throwable) {}
            }
            broadcastControlEvent(
                action = "SELECT_VIDEO",
                videoPath = finalSwitched.path,
                isVideoPaused = false,
                isVideoAudioMuted = finalSwitched.isMuted,
                autoNextEnabled = true,
                autoNextMode = targetMode.name,
                extraSource = "ACTIVATE_STANDARD_${targetMode.name}"
            )
        } else {
            broadcastControlEvent(
                action = "UPDATE_CONFIG",
                autoNextEnabled = true,
                autoNextMode = targetMode.name,
                extraSource = "ACTIVATE_STANDARD_${targetMode.name}"
            )
        }
        return targetMode
    }

    /**
     * Atomically updates VcamConfig for IFS Mode without triggering any counter-toggles.
     */
    fun applyIfsModeStateSync(enabled: Boolean, activeVideo: VideoItem? = null) {
        _pendingVideoIndex.value = null
        val playlist = _config.value.playlist
        val matchedIdx = if (activeVideo != null) {
            playlist.indexOfFirst { it.id == activeVideo.id }.takeIf { it >= 0 }
        } else null

        updateConfigSync { cur ->
            val targetIdx = matchedIdx ?: cur.currentVideoIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
            val targetItem = activeVideo ?: playlist.getOrNull(targetIdx)
            cur.copy(
                isIfsMode = enabled,
                isBridgeCycleMode = false,
                isMasterSpillMode = if (enabled) false else cur.isMasterSpillMode,
                activeSpillVideoId = if (enabled) null else cur.activeSpillVideoId,
                pendingVideoIndex = null,
                autoNextEnabled = !enabled,
                currentVideoIndex = if (enabled && targetItem != null) targetIdx else cur.currentVideoIndex,
                videoPath = if (enabled && targetItem != null) targetItem.path else cur.videoPath,
                videoUri = if (enabled && targetItem != null) targetItem.uri else cur.videoUri,
                isVideoAudioMuted = if (enabled && targetItem != null) targetItem.isMuted else cur.isVideoAudioMuted,
                isVideoPaused = false
            )
        }
        bumpPlaybackStepVersion()

        if (enabled && activeVideo != null) {
            broadcastControlEvent(
                action = "SELECT_VIDEO",
                videoPath = activeVideo.path,
                isVideoPaused = false,
                isVideoAudioMuted = activeVideo.isMuted,
                autoNextEnabled = false,
                extraSource = "IFS_ENABLE"
            )
            repoScope.launch(Dispatchers.IO) {
                try {
                    copyToAllLocations(File(activeVideo.path), "virtual.mp4")
                    syncToTargetApps(_config.value.targetPackages)
                } catch (_: Exception) {}
            }
            val safeIdx = matchedIdx ?: _config.value.currentVideoIndex
            onVideoSelectionChanged(safeIdx, activeVideo.isMuted)
        } else {
            broadcastControlEvent(
                action = "UPDATE_CONFIG",
                autoNextEnabled = !enabled,
                extraSource = if (enabled) "IFS_ENABLE" else "IFS_DISABLE"
            )
        }
    }

    fun isRollingVsItem(item: VideoItem): Boolean {
        return item.id.startsWith("vs_rolling_") || item.path.contains("vs_rolling_")
    }

    /**
     * Membersihkan semua item sementara vs_rolling_* dari Daftar Video (playlist) ketika keluar dari SV4/IFS,
     * sehingga hanya video sumber asli (L1, L2, L3, L4...) yang tersisa di daftar.
     */
    fun cleanupAllRollingVsFromPlaylistSync() {
        val currentList = _config.value.playlist
        if (currentList.none { isRollingVsItem(it) }) return
        val sourceOnlyList = currentList.filterNot { isRollingVsItem(it) }
        val firstSource = sourceOnlyList.firstOrNull()
        _pendingVideoIndex.value = null
        updateConfigSync { cur ->
            cur.copy(
                playlist = sourceOnlyList,
                currentVideoIndex = 0,
                pendingVideoIndex = null,
                videoPath = firstSource?.path ?: cur.videoPath,
                videoUri = firstSource?.uri ?: cur.videoUri,
                isVideoAudioMuted = firstSource?.isMuted ?: cur.isVideoAudioMuted
            )
        }
        bumpPlaylistVersion()
        repoScope.launch(Dispatchers.IO) {
            try { writeHookConfigFiles(_config.value) } catch (_: Throwable) {}
        }
    }

    /**
     * Memasukkan VS1 yang sudah digabungkan ke dalam Daftar Video (di urutan #1 di atas L1..Ln)
     * dan langsung memutarnya (Play VS1).
     */
    fun selectRollingVsVideoSync(vsVideo: VideoItem, broadcastToHook: Boolean = true): VideoItem {
        val now = System.currentTimeMillis()
        lastVideoSwitchTimestampMs = now
        _pendingVideoIndex.value = null

        val sourceOnly = _config.value.playlist.filterNot { isRollingVsItem(it) }
        val activeVsItem = vsVideo.copy(isEnabled = true)
        val newPlaylist = listOf(activeVsItem) + sourceOnly

        val updatedCfg = updateConfigSync { cur ->
            cur.copy(
                playlist = newPlaylist,
                currentVideoIndex = 0,
                isIfsMode = true,
                isBridgeCycleMode = false,
                isMasterSpillMode = false,
                activeSpillVideoId = null,
                pendingVideoIndex = null,
                autoNextEnabled = false,
                videoPath = activeVsItem.path,
                videoUri = activeVsItem.uri,
                isVideoAudioMuted = activeVsItem.isMuted,
                isVideoPaused = false
            )
        }
        bumpPlaylistVersion()
        bumpPlaybackStepVersion()

        if (broadcastToHook) {
            broadcastControlEvent(
                action = "SELECT_VIDEO",
                videoPath = activeVsItem.path,
                videoName = activeVsItem.name,
                isVideoPaused = false,
                isVideoAudioMuted = activeVsItem.isMuted,
                autoNextEnabled = false,
                extraSource = "IFS_SV4_VS_PLAY"
            )
        }

        repoScope.launch(Dispatchers.IO) {
            try {
                writeHookConfigFiles(updatedCfg)
                val f = File(activeVsItem.path)
                if (f.exists() && f.length() > 0L) {
                    copyToAllLocations(f, "virtual.mp4")
                }
            } catch (_: Exception) {}
        }

        onVideoSelectionChanged(0, activeVsItem.isMuted)
        try {
            applySv4TwoMinuteAntiDetekRotation(reason = "sv4_select_active_vs")
        } catch (_: Throwable) {}
        return activeVsItem
    }

    /**
     * Memasukkan VS berikutnya (mis. VS2) yang selesai digabungkan di background ke dalam Daftar Video
     * tepat di bawah VS aktif (urutan #2 dengan status ANTRE/SIAP) selagi VS1 sedang di-play.
     */
    fun insertNextQueuedVsIntoPlaylistSync(nextVsVideo: VideoItem): Int {
        val currentList = _config.value.playlist
        val activePlayingVs = currentList.firstOrNull { isRollingVsItem(it) && it.id != nextVsVideo.id }
        val sourceOnly = currentList.filterNot { isRollingVsItem(it) }
        val queuedItem = nextVsVideo.copy(isEnabled = true)

        val newPlaylist = if (activePlayingVs != null) {
            listOf(activePlayingVs, queuedItem) + sourceOnly
        } else {
            listOf(queuedItem) + sourceOnly
        }
        val queuedIndex = if (activePlayingVs != null) 1 else 0
        _pendingVideoIndex.value = queuedIndex

        updateConfigSync(writeHooks = false) { cur ->
            cur.copy(
                playlist = newPlaylist,
                currentVideoIndex = 0,
                pendingVideoIndex = queuedIndex
            )
        }
        return queuedIndex
    }

    /**
     * Dipanggil saat VS aktif (mis. VS1) selesai di-play:
     * 1. Langsung memutar VS berikutnya (VS2) yang sudah ada di daftar.
     * 2. Menghapus penggabungan VS lama (VS1) dari Daftar Video (playlist).
     */
    fun advanceToNextVsAndRemovePreviousSync(
        nextVsVideo: VideoItem,
        previousVsId: String?,
        broadcastToHook: Boolean = true
    ): VideoItem {
        val now = System.currentTimeMillis()
        lastVideoSwitchTimestampMs = now
        _pendingVideoIndex.value = null

        val sourceOnly = _config.value.playlist.filterNot {
            isRollingVsItem(it) || (previousVsId != null && it.id == previousVsId)
        }
        val promotedVsItem = nextVsVideo.copy(isEnabled = true)
        val newPlaylist = listOf(promotedVsItem) + sourceOnly

        val updatedCfg = updateConfigSync { cur ->
            cur.copy(
                playlist = newPlaylist,
                currentVideoIndex = 0,
                pendingVideoIndex = null,
                isIfsMode = true,
                isBridgeCycleMode = false,
                isMasterSpillMode = false,
                activeSpillVideoId = null,
                autoNextEnabled = false,
                videoPath = promotedVsItem.path,
                videoUri = promotedVsItem.uri,
                isVideoAudioMuted = promotedVsItem.isMuted,
                isVideoPaused = false
            )
        }
        bumpPlaylistVersion()
        bumpPlaybackStepVersion()

        if (broadcastToHook) {
            broadcastControlEvent(
                action = "SELECT_VIDEO",
                videoPath = promotedVsItem.path,
                videoName = promotedVsItem.name,
                isVideoPaused = false,
                isVideoAudioMuted = promotedVsItem.isMuted,
                autoNextEnabled = false,
                extraSource = "IFS_SV4_VS_ADVANCE"
            )
        }

        repoScope.launch(Dispatchers.IO) {
            try {
                writeHookConfigFiles(updatedCfg)
                val f = File(promotedVsItem.path)
                if (f.exists() && f.length() > 0L) {
                    copyToAllLocations(f, "virtual.mp4")
                }
            } catch (_: Exception) {}
        }

        onVideoSelectionChanged(0, promotedVsItem.isMuted)
        try {
            applySv4TwoMinuteAntiDetekRotation(reason = "sv4_advance_next_vs")
        } catch (_: Throwable) {}
        return promotedVsItem
    }

    // =========================================================================
    // SV4 AUDIO (MODE VS AUDIO — VARIASI SUSUNAN AUDIO VSA1, VSA2..n)
    // =========================================================================

    fun isRollingVsAudioItem(item: AudioItem): Boolean {
        return item.id.startsWith("vs_audio_rolling_") || item.path.contains("vs_audio_rolling_")
    }

    fun cleanupAllRollingVsAudioFromPlaylistSync() {
        val currentList = _config.value.audioPlaylist
        if (currentList.none { isRollingVsAudioItem(it) }) return
        val sourceOnlyList = currentList.filterNot { isRollingVsAudioItem(it) }
        val firstSource = sourceOnlyList.firstOrNull()
        updateConfigSync(writeHooks = false) { cur ->
            cur.copy(
                audioPlaylist = sourceOnlyList,
                currentAudioIndex = 0,
                audioPath = firstSource?.path ?: "",
                audioUri = firstSource?.uri,
                audioName = firstSource?.name ?: ""
            )
        }
    }

    fun updateAudioPlaylistAfterBatchEditSync(updatedPlaylist: List<AudioItem>) {
        val safeIdx = _config.value.currentAudioIndex.coerceIn(0, (updatedPlaylist.size - 1).coerceAtLeast(0))
        val activeItem = updatedPlaylist.getOrNull(safeIdx)
        updateConfigSync { cur ->
            cur.copy(
                audioPlaylist = updatedPlaylist,
                currentAudioIndex = safeIdx,
                audioPath = activeItem?.path ?: "",
                audioUri = activeItem?.uri,
                audioName = activeItem?.name ?: ""
            )
        }
        try {
            com.tajasb.infinity.IfsManager.getInstance(context).onMediaPlaylistChanged(
                newVideos = _config.value.playlist,
                newAudios = updatedPlaylist,
                broadcastIfActive = false
            )
        } catch (_: Throwable) {}
    }

    fun selectRollingVsAudioSync(vsAudio: AudioItem, startPlaying: Boolean = true): AudioItem {
        val sourceOnly = _config.value.audioPlaylist.filterNot { isRollingVsAudioItem(it) }
        val activeVsAudio = vsAudio.copy(isEnabled = true)
        val newAudioPlaylist = listOf(activeVsAudio) + sourceOnly

        updateConfigSync(writeHooks = false) { cur ->
            cur.copy(
                audioPlaylist = newAudioPlaylist,
                currentAudioIndex = 0,
                audioPath = activeVsAudio.path,
                audioUri = activeVsAudio.uri,
                audioName = activeVsAudio.name,
                isMp3AudioPaused = !startPlaying
            )
        }

        if (!_config.value.isIndependentAudioEnabled) {
            repoScope.launch(Dispatchers.Main) {
                try {
                    musicPlayer.playAudioItem(activeVsAudio, startPlaying = startPlaying, startPositionMs = 0L)
                } catch (_: Throwable) {}
            }
        }
        return activeVsAudio
    }

    fun insertNextQueuedVsAudioIntoPlaylistSync(nextVsAudio: AudioItem): Int {
        val currentList = _config.value.audioPlaylist
        val activePlayingVs = currentList.firstOrNull { isRollingVsAudioItem(it) && it.id != nextVsAudio.id }
        val sourceOnly = currentList.filterNot { isRollingVsAudioItem(it) }
        val queuedItem = nextVsAudio.copy(isEnabled = true)

        val newAudioPlaylist = if (activePlayingVs != null) {
            listOf(activePlayingVs, queuedItem) + sourceOnly
        } else {
            listOf(queuedItem) + sourceOnly
        }
        val queuedIndex = if (activePlayingVs != null) 1 else 0

        updateConfigSync(writeHooks = false) { cur ->
            cur.copy(
                audioPlaylist = newAudioPlaylist,
                currentAudioIndex = 0
            )
        }
        return queuedIndex
    }

    fun advanceToNextVsAudioAndRemovePreviousSync(
        nextVsAudio: AudioItem,
        previousVsId: String?,
        startPlaying: Boolean = true
    ): AudioItem {
        val sourceOnly = _config.value.audioPlaylist.filterNot {
            isRollingVsAudioItem(it) || (previousVsId != null && it.id == previousVsId)
        }
        val promotedVsAudio = nextVsAudio.copy(isEnabled = true)
        val newAudioPlaylist = listOf(promotedVsAudio) + sourceOnly

        updateConfigSync(writeHooks = false) { cur ->
            cur.copy(
                audioPlaylist = newAudioPlaylist,
                currentAudioIndex = 0,
                audioPath = promotedVsAudio.path,
                audioUri = promotedVsAudio.uri,
                audioName = promotedVsAudio.name,
                isMp3AudioPaused = !startPlaying
            )
        }

        if (!_config.value.isIndependentAudioEnabled) {
            repoScope.launch(Dispatchers.Main) {
                try {
                    musicPlayer.playAudioItem(promotedVsAudio, startPlaying = startPlaying, startPositionMs = 0L)
                } catch (_: Throwable) {}
            }
        }
        return promotedVsAudio
    }

    /**
     * Atomically switches Master & Spill mode on or off without race conditions.
     */
    fun activateMasterSpillModeSync(enabled: Boolean = true): Boolean {
        if (enabled) {
            try {
                com.tajasb.infinity.IfsManager.getInstance(context).disableIfsFromExternalModeSwitch()
            } catch (_: Throwable) {}
            val masterIdx = getMasterVideoIndex()
            val playlist = _config.value.playlist
            val safeMasterIdx = if (playlist.isNotEmpty()) masterIdx.coerceIn(0, playlist.size - 1) else 0
            val masterItem = playlist.getOrNull(safeMasterIdx)
            _pendingVideoIndex.value = null
            updateConfigSync {
                it.copy(
                    isMasterSpillMode = true,
                    isBridgeCycleMode = false,
                    isIfsMode = false,
                    activeSpillVideoId = null,
                    pendingVideoIndex = null,
                    autoNextEnabled = false,
                    currentVideoIndex = if (masterItem != null) safeMasterIdx else it.currentVideoIndex,
                    videoPath = masterItem?.path ?: it.videoPath,
                    videoUri = masterItem?.uri ?: it.videoUri,
                    isVideoAudioMuted = masterItem?.isMuted ?: it.isVideoAudioMuted,
                    isVideoPaused = false
                )
            }
            bumpPlaybackStepVersion()
            if (masterItem != null) {
                broadcastControlEvent(
                    action = "SELECT_VIDEO",
                    videoPath = masterItem.path,
                    isVideoPaused = false,
                    isVideoAudioMuted = masterItem.isMuted,
                    autoNextEnabled = false,
                    extraSource = "ACTIVATE_MASTER_SPILL"
                )
                repoScope.launch(Dispatchers.IO) {
                    try {
                        copyToAllLocations(File(masterItem.path), "virtual.mp4")
                        syncToTargetApps(_config.value.targetPackages)
                    } catch (_: Exception) {}
                }
                onVideoSelectionChanged(safeMasterIdx, masterItem.isMuted)
            } else {
                broadcastControlEvent(action = "UPDATE_CONFIG", extraSource = "ACTIVATE_MASTER_SPILL")
            }
            return true
        } else {
            activateStandardModeSync()
            return false
        }
    }

    suspend fun toggleMasterSpillMode(enabled: Boolean? = null): Boolean {
        val next = enabled ?: !_config.value.isMasterSpillMode
        return activateMasterSpillModeSync(next)
    }

    /**
     * advanceMasterSpill:
     * Sistem siklus mandiri untuk Master Loop & Spill.
     * Tidak terikat dan tidak dipengaruhi oleh mode Urut / Acak / Loop generic.
     */
    fun advanceMasterSpillSync(broadcastToHook: Boolean = true): VideoItem? {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return null
        val masterIdx = getMasterVideoIndex()
        val curIdx = _config.value.currentVideoIndex
        val pending = _pendingVideoIndex.value

        // 1. Jika ada antrean video spill yang menunggu (misal dari komentar atau klik "Habiskan Master Dulu"):
        if (pending != null && pending in playlist.indices) {
            _pendingVideoIndex.value = null
            val targetItem = playlist[pending]
            val newSpillId = if (pending != masterIdx) targetItem.id else null
            updateConfigSync { it.copy(pendingVideoIndex = null, activeSpillVideoId = newSpillId) }
            Log.i("VcamRepository", "🎯 [MASTER-LOOP] Master loop selesai! Memutar video spill antrean #$pending (${targetItem.name})")
            return if (broadcastToHook) selectVideoByIndexSync(pending, broadcastToHook = true) else syncVideoIndexWithoutBroadcastSync(pending, broadcastPreload = false)
        }

        // 2. Jika video yang barusan selesai adalah Video Spill -> Otomatis kembali ke Video Master!
        if (curIdx != masterIdx || _config.value.activeSpillVideoId != null) {
            Log.i("VcamRepository", "🎯 [MASTER-LOOP] Video spill #$curIdx selesai! Otomatis kembali ke Video Master #$masterIdx...")
            updateConfigSync { it.copy(activeSpillVideoId = null) }
            return if (broadcastToHook) selectVideoByIndexSync(masterIdx, broadcastToHook = true) else syncVideoIndexWithoutBroadcastSync(masterIdx, broadcastPreload = false)
        }

        // 3. Jika video yang barusan selesai adalah Video Master (dan tidak ada antrean spill) -> Lanjutkan Master Loop!
        Log.i("VcamRepository", "⭐ [MASTER-LOOP] Loop Master Video #$masterIdx selesai, melanjutkan putaran Master Loop...")
        return if (broadcastToHook) selectVideoByIndexSync(masterIdx, broadcastToHook = true) else syncVideoIndexWithoutBroadcastSync(masterIdx, broadcastPreload = false)
    }

    suspend fun advanceMasterSpill(broadcastToHook: Boolean = true): VideoItem? =
        advanceMasterSpillSync(broadcastToHook)

    suspend fun setMasterSpillWaitFinish(waitFinish: Boolean) {
        updateConfig { it.copy(masterSpillWaitFinish = waitFinish) }
        broadcastControlEvent(action = "UPDATE_CONFIG")
    }

    suspend fun toggleAutoSpillFromComments(enabled: Boolean? = null): Boolean {
        val next = enabled ?: !_config.value.autoSpillFromComments
        updateConfig { it.copy(autoSpillFromComments = next) }
        return next
    }

    suspend fun setVideoSpillKeyword(id: String, keyword: String) = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        val updated = playlist.map {
            if (it.id == id) it.copy(spillKeyword = keyword.trim()) else it
        }
        updateConfig { it.copy(playlist = updated) }
    }

    suspend fun updateSpillKeyword(id: String, keyword: String) = setVideoSpillKeyword(id, keyword)

    suspend fun triggerSpillVideo(indexOrId: Any, immediate: Boolean = false): VideoItem? = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return@withContext null

        val targetIndex = when (indexOrId) {
            is Int -> indexOrId.coerceIn(0, playlist.size - 1)
            is String -> {
                val byId = playlist.indexOfFirst { it.id == indexOrId }
                if (byId >= 0) byId else {
                    val match = findMatchingSpillVideo(indexOrId)
                    match?.first ?: 0
                }
            }
            else -> 0
        }

        val targetItem = playlist.getOrNull(targetIndex) ?: return@withContext null
        val masterIndex = getMasterVideoIndex()
        val curIndex = _config.value.currentVideoIndex

        if (targetIndex == masterIndex) {
            return@withContext returnToMasterVideo(immediate = true)
        }

        val isExplicitWaitFinish = if (_config.value.isMasterSpillMode) {
            _config.value.masterSpillWaitFinish
        } else {
            _config.value.videoSwitchMode == com.tajasb.model.VideoSwitchMode.WAIT_FINISH && _config.value.masterSpillWaitFinish
        }
        val shouldWaitFinish = if (immediate) false else isExplicitWaitFinish

        if (curIndex == masterIndex && shouldWaitFinish) {
            // "habiskan dulu video master, lalu pindah merah"
            queueCustomVideo(targetIndex)
            updateConfig { it.copy(activeSpillVideoId = targetItem.id) }
            targetItem
        } else {
            // Immediate transition
            cancelQueuedVideo()
            updateConfig { it.copy(activeSpillVideoId = targetItem.id) }
            selectVideoByIndex(targetIndex)
        }
    }

    suspend fun returnToMasterVideo(immediate: Boolean = true): VideoItem? = withContext(Dispatchers.IO) {
        val masterIdx = getMasterVideoIndex()
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return@withContext null
        val masterItem = playlist.getOrNull(masterIdx) ?: return@withContext null

        cancelQueuedVideo()
        updateConfig { it.copy(activeSpillVideoId = null) }

        if (immediate) {
            selectVideoByIndex(masterIdx)
        } else {
            queueCustomVideo(masterIdx)
            masterItem
        }
    }

    suspend fun toggleBridgeCycleMode(enabled: Boolean? = null): Boolean {
        val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(context) } catch (_: Throwable) { null }
        val currentIfs = ifsMgr?.isIfsActive?.value == true || _config.value.isIfsMode || _config.value.isBridgeCycleMode
        val next = enabled ?: !currentIfs
        ifsMgr?.setIfsEnabled(next)
        return next
    }

    fun advanceBridgeCycleSync(broadcastToHook: Boolean = true): VideoItem? {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return null
        val cfg = _config.value
        val bridge1Idx = cfg.bridge1VideoIndex
        val masterIdx = cfg.masterVideoIndex
        val bridge2Idx = cfg.bridge2VideoIndex
        val curPhase = cfg.currentBridgePhase
        val pendingContent = cfg.pendingBridgeContentIndex
        val queue = cfg.bridgeContentQueue

        var nextPendingContent = pendingContent
        var nextQueue = queue

        val (nextPhase, nextIdx) = when (curPhase) {
            BridgeCyclePhase.BRIDGE_1 -> {
                if (nextPendingContent == null && nextQueue.isNotEmpty()) {
                    nextPendingContent = nextQueue.first()
                    nextQueue = nextQueue.drop(1)
                }
                Pair(BridgeCyclePhase.MASTER_LOOP, masterIdx)
            }
            BridgeCyclePhase.MASTER_LOOP -> {
                val effectivePending = nextPendingContent ?: nextQueue.firstOrNull()
                if (effectivePending != null && effectivePending in playlist.indices) {
                    if (nextPendingContent == null && nextQueue.isNotEmpty()) {
                        nextPendingContent = nextQueue.first()
                        nextQueue = nextQueue.drop(1)
                    }
                    Pair(BridgeCyclePhase.BRIDGE_2, bridge2Idx)
                } else {
                    Pair(BridgeCyclePhase.MASTER_LOOP, masterIdx)
                }
            }
            BridgeCyclePhase.BRIDGE_2 -> {
                val targetContentIdx = if (nextPendingContent != null && nextPendingContent in playlist.indices) {
                    nextPendingContent!!
                } else if (nextQueue.isNotEmpty()) {
                    val popped = nextQueue.first()
                    nextQueue = nextQueue.drop(1)
                    popped
                } else {
                    val contentCandidates = cfg.contentVideos.map { it.first }
                    contentCandidates.firstOrNull() ?: masterIdx
                }
                nextPendingContent = null
                Pair(BridgeCyclePhase.CONTENT_VIDEO, targetContentIdx)
            }
            BridgeCyclePhase.CONTENT_VIDEO -> {
                Pair(BridgeCyclePhase.BRIDGE_1, bridge1Idx)
            }
            BridgeCyclePhase.OFF -> {
                Pair(BridgeCyclePhase.BRIDGE_1, bridge1Idx)
            }
        }

        updateConfigSync {
            it.copy(
                isBridgeCycleMode = true,
                currentBridgePhase = nextPhase,
                pendingBridgeContentIndex = nextPendingContent,
                bridgeContentQueue = nextQueue
            )
        }

        Log.i(TAG, "🔀 [BRIDGE-CYCLE] Transisi fase: ${curPhase.name} ➡️ ${nextPhase.name}, Target Video #${nextIdx + 1}: ${playlist.getOrNull(nextIdx)?.name} (Pending: $nextPendingContent, Antrean: ${nextQueue.size})")

        return if (broadcastToHook) selectVideoByIndexSync(nextIdx, broadcastToHook = true) else syncVideoIndexWithoutBroadcastSync(nextIdx, broadcastPreload = false)
    }

    suspend fun advanceBridgeCycle(broadcastToHook: Boolean = true): VideoItem? =
        advanceBridgeCycleSync(broadcastToHook = broadcastToHook)

    suspend fun assignVideoRole(videoId: String, role: com.tajasb.model.VideoRole) = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return@withContext
        val target = playlist.firstOrNull { it.id == videoId } ?: return@withContext

        var b1Id = _config.value.bridge1VideoId
        var mId = _config.value.masterVideoId
        var b2Id = _config.value.bridge2VideoId

        when (role) {
            com.tajasb.model.VideoRole.BRIDGE_1 -> {
                b1Id = videoId
                if (mId == videoId) mId = null
                if (b2Id == videoId) b2Id = null
            }
            com.tajasb.model.VideoRole.MASTER_LOOP -> {
                mId = videoId
                if (b1Id == videoId) b1Id = null
                if (b2Id == videoId) b2Id = null
            }
            com.tajasb.model.VideoRole.BRIDGE_2 -> {
                b2Id = videoId
                if (b1Id == videoId) b1Id = null
                if (mId == videoId) mId = null
            }
            com.tajasb.model.VideoRole.CONTENT -> {
                if (b1Id == videoId) b1Id = null
                if (mId == videoId) mId = null
                if (b2Id == videoId) b2Id = null
            }
        }

        val updatedPlaylist = playlist.map {
            it.copy(
                isBridge1 = (it.id == b1Id),
                isMaster = (it.id == mId),
                isBridge2 = (it.id == b2Id)
            )
        }

        updateConfig {
            it.copy(
                playlist = updatedPlaylist,
                bridge1VideoId = b1Id,
                masterVideoId = mId,
                bridge2VideoId = b2Id
            )
        }
        broadcastControlEvent(action = "UPDATE_CONFIG")
    }

    suspend fun setBridge1Video(id: String) = assignVideoRole(id, com.tajasb.model.VideoRole.BRIDGE_1)

    suspend fun setBridge2Video(id: String) = assignVideoRole(id, com.tajasb.model.VideoRole.BRIDGE_2)

    suspend fun triggerBridgeContentVideo(indexOrId: Any, immediate: Boolean = false): VideoItem? = withContext(Dispatchers.IO) {
        val playlist = _config.value.playlist
        if (playlist.isEmpty()) return@withContext null
        val targetIndex = when (indexOrId) {
            is Int -> indexOrId.coerceIn(0, playlist.size - 1)
            is String -> {
                val byId = playlist.indexOfFirst { it.id == indexOrId }
                if (byId >= 0) byId else {
                    val excluded = setOf(_config.value.bridge1VideoIndex, _config.value.masterVideoIndex, _config.value.bridge2VideoIndex)
                    val match = findMatchingSpillVideo(indexOrId, excluded)
                    match?.first ?: 0
                }
            }
            else -> 0
        }
        val targetItem = playlist.getOrNull(targetIndex) ?: return@withContext null
        val b2Idx = _config.value.bridge2VideoIndex
        val curPhase = _config.value.currentBridgePhase

        if (immediate) {
            // Mode Tes Siklus Langsung: Potong Master Loop seketika ke Bridge II -> Konten
            updateConfig {
                it.copy(
                    isBridgeCycleMode = true,
                    currentBridgePhase = BridgeCyclePhase.BRIDGE_2,
                    pendingBridgeContentIndex = targetIndex
                )
            }
            Log.i(TAG, "⚡ [BRIDGE-CYCLE] Tes Siklus Langsung (Immediate): Memotong langsung ke Bridge II (Video #$b2Idx) ➡️ Konten #${targetIndex + 1} (${targetItem.name})")
            selectVideoByIndex(b2Idx)
            return@withContext targetItem
        }

        // Ketika sedang di MASTER_LOOP (atau sedang memutar video master):
        // Tunggu habis Master Loop lalu pindah ke Bridge II.
        val isMasterPlaying = (_config.value.currentVideoIndex == _config.value.masterVideoIndex)
        if (curPhase == BridgeCyclePhase.MASTER_LOOP || isMasterPlaying) {
            val curPending = _config.value.pendingBridgeContentIndex
            val curQueue = _config.value.bridgeContentQueue

            if (curPending == null) {
                updateConfig {
                    it.copy(
                        isBridgeCycleMode = true,
                        currentBridgePhase = BridgeCyclePhase.MASTER_LOOP,
                        pendingBridgeContentIndex = targetIndex
                    )
                }
                Log.i(TAG, "🔀 [BRIDGE-CYCLE] Komen masuk saat Master Loop: Menunggu Master Loop selesai ➡️ Bridge II ➡️ Konten #${targetIndex + 1} (${targetItem.name})")
            } else {
                // Sudah ada target yang menunggu Master Loop selesai, masukkan ke antrean berurutan (FIFO)
                val updatedQueue = curQueue + targetIndex
                updateConfig {
                    it.copy(
                        isBridgeCycleMode = true,
                        bridgeContentQueue = updatedQueue
                    )
                }
                Log.i(TAG, "🔀 [BRIDGE-CYCLE] Komen masuk saat Master Loop: Ditambahkan ke antrean #${updatedQueue.size}: Konten #${targetIndex + 1} (${targetItem.name})")
            }

            // Kirim event agar native loop di GlVideoRenderer segera dimatikan sehingga video master menyelesaikan putarannya dan memicu onVideoCompleted
            broadcastControlEvent(
                action = "BRIDGE_QUEUE_CONTENT",
                extraSource = "BRIDGE_QUEUE"
            )
        } else if (curPhase == BridgeCyclePhase.OFF) {
            // Jika mode OFF, mulai Bridge II lalu konten
            updateConfig {
                it.copy(
                    isBridgeCycleMode = true,
                    currentBridgePhase = BridgeCyclePhase.BRIDGE_2,
                    pendingBridgeContentIndex = targetIndex
                )
            }
            Log.i(TAG, "🔀 [BRIDGE-CYCLE] Komen memicu dari OFF: BRIDGE II (Video #$b2Idx) ➡️ Konten #${targetIndex + 1} (${targetItem.name})")
            selectVideoByIndex(b2Idx)
        } else {
            // Sedang di fase BRIDGE_2, CONTENT_VIDEO, atau BRIDGE_1:
            // Masukkan ke antrean berurutan
            val curPending = _config.value.pendingBridgeContentIndex
            if (curPending == null && curPhase == BridgeCyclePhase.BRIDGE_2) {
                updateConfig {
                    it.copy(
                        isBridgeCycleMode = true,
                        pendingBridgeContentIndex = targetIndex
                    )
                }
                Log.i(TAG, "🔀 [BRIDGE-CYCLE] Konten #${targetIndex + 1} (${targetItem.name}) disetel sebagai target Bridge II")
            } else {
                val updatedQueue = _config.value.bridgeContentQueue + targetIndex
                updateConfig {
                    it.copy(
                        isBridgeCycleMode = true,
                        bridgeContentQueue = updatedQueue
                    )
                }
                Log.i(TAG, "🔀 [BRIDGE-CYCLE] Konten #${targetIndex + 1} (${targetItem.name}) diantrikan di belakang (Antrean #${updatedQueue.size})")
            }
            broadcastControlEvent(action = "UPDATE_CONFIG")
        }
        targetItem
    }

    fun findMatchingSpillVideo(rawText: String, excluded: Set<Int> = emptySet()): Pair<Int, VideoItem>? =
        VideoPlaylistRepository.findMatchingSpillVideo(
            playlist = _config.value.playlist,
            masterIdx = getMasterVideoIndex(),
            rawText = rawText,
            excludedIndices = excluded
        )

    suspend fun moveVideoUp(index: Int): Boolean = withContext(Dispatchers.IO) {
        val list = _config.value.playlist.toMutableList()
        if (index <= 0 || index >= list.size) return@withContext false
        val curActiveId = _config.value.currentVideo?.id
        val item = list.removeAt(index)
        list.add(index - 1, item)
        val newActiveIndex = if (curActiveId != null) {
            val found = list.indexOfFirst { it.id == curActiveId }
            if (found != -1) found else _config.value.currentVideoIndex
        } else _config.value.currentVideoIndex

        updateConfig {
            it.copy(
                playlist = list,
                currentVideoIndex = newActiveIndex
            )
        }
        bumpPlaylistVersion()
        writeHookConfigFiles(_config.value)
        repoScope.launch(Dispatchers.IO) {
            try { syncToTargetApps(_config.value.targetPackages) } catch (_: Exception) {}
        }
        broadcastControlEvent(
            action = "UPDATE_CONFIG",
            extraSource = "VIDEO_REORDERED",
            extraPurgeCache = true
        )
        true
    }

    suspend fun moveVideoDown(index: Int): Boolean = withContext(Dispatchers.IO) {
        val list = _config.value.playlist.toMutableList()
        if (index < 0 || index >= list.size - 1) return@withContext false
        val curActiveId = _config.value.currentVideo?.id
        val item = list.removeAt(index)
        list.add(index + 1, item)
        val newActiveIndex = if (curActiveId != null) {
            val found = list.indexOfFirst { it.id == curActiveId }
            if (found != -1) found else _config.value.currentVideoIndex
        } else _config.value.currentVideoIndex

        updateConfig {
            it.copy(
                playlist = list,
                currentVideoIndex = newActiveIndex
            )
        }
        bumpPlaylistVersion()
        writeHookConfigFiles(_config.value)
        repoScope.launch(Dispatchers.IO) {
            try { syncToTargetApps(_config.value.targetPackages) } catch (_: Exception) {}
        }
        broadcastControlEvent(
            action = "UPDATE_CONFIG",
            extraSource = "VIDEO_REORDERED",
            extraPurgeCache = true
        )
        true
    }

    suspend fun renameVideo(id: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val rawClean = newName.trim()
        if (rawClean.isEmpty()) return@withContext false
        val currentList = _config.value.playlist
        val targetItem = currentList.find { it.id == id } ?: return@withContext false
        val oldFile = File(targetItem.path)
        val oldName = targetItem.name

        // Determine extension: keep existing file extension if user didn't specify one
        val oldExt = if (oldFile.extension.isNotBlank()) oldFile.extension else "mp4"
        val cleanName = if (rawClean.contains(".")) rawClean else "$rawClean.$oldExt"
        val safeFileName = cleanName.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        var newPath = targetItem.path
        var fileRenamed = false
        val pathsToScan = mutableListOf<String>()

        if (oldFile.exists()) {
            pathsToScan.add(oldFile.absolutePath)
            val parentDir = oldFile.parentFile ?: getInternalCamera1Directory()
            val targetFile = File(parentDir, safeFileName)

            if (targetFile.canonicalPath != oldFile.canonicalPath) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val ok = oldFile.renameTo(targetFile)
                if (ok) {
                    newPath = targetFile.absolutePath
                    fileRenamed = true
                } else {
                    try {
                        oldFile.copyTo(targetFile, overwrite = true)
                        oldFile.delete()
                        newPath = targetFile.absolutePath
                        fileRenamed = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                newPath = targetFile.absolutePath
                fileRenamed = true
            }
            pathsToScan.add(newPath)
        }

        // Try renaming original document uri if provided by file picker
        targetItem.uri?.let { uriStr ->
            try {
                val uri = Uri.parse(uriStr)
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.renameDocument(context.contentResolver, uri, safeFileName)
                }
            } catch (_: Exception) {}
        }

        // Trigger MediaScanner so Android File Manager immediately updates cached file list
        try {
            if (pathsToScan.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    context,
                    pathsToScan.distinct().toTypedArray(),
                    null
                ) { path, _ ->
                    com.tajasb.debug.VcamDebugLogger.d("VcamRepo", "MediaScanner scanned: $path")
                }
            }
        } catch (_: Exception) {}

        val list = currentList.map { item ->
            if (item.id == id) item.copy(name = safeFileName, path = newPath) else item
        }
        val isCurrentActive = _config.value.currentVideo?.id == id
        val updatedVideoPath = if (isCurrentActive) newPath else _config.value.videoPath

        updateConfig {
            it.copy(
                playlist = list,
                videoPath = updatedVideoPath
            )
        }

        val logMsg = if (fileRenamed) {
            "✏️ [RENAME_VIDEO] Berkas asli berhasil diubah namanya: \"$oldName\" -> \"$safeFileName\" (Aktif: $isCurrentActive)"
        } else {
            "✏️ [RENAME_VIDEO] Nama video playlist diperbarui: \"$oldName\" -> \"$safeFileName\" (Aktif: $isCurrentActive)"
        }
        com.tajasb.debug.VcamDebugLogger.i("VcamRepo", logMsg)

        // If currently active video was renamed, sync virtual.mp4 & broadcast to hook
        if (isCurrentActive) {
            val newFileObj = File(newPath)
            if (newFileObj.exists()) {
                copyToAllLocations(newFileObj, "virtual.mp4")
            }
            broadcastControlEvent(
                action = "RENAME_VIDEO",
                videoPath = updatedVideoPath
            )
        }
        true
    }

    // ==========================================
    // MULTI-AUDIO PLAYLIST MANAGEMENT
    // ==========================================

    suspend fun addAudios(uris: List<Uri>): Int =
        AudioPlaylistRepository.addAudios(this, uris)

    suspend fun removeAudioItem(id: String) =
        AudioPlaylistRepository.removeAudioItem(this, id)

    suspend fun selectAudioByIndex(index: Int, autoPlay: Boolean = true): com.tajasb.model.AudioItem? =
        AudioPlaylistRepository.selectAudioByIndex(this, index, autoPlay)

    suspend fun nextAudio(): com.tajasb.model.AudioItem? =
        AudioPlaylistRepository.nextAudio(this)

    suspend fun prevAudio(): com.tajasb.model.AudioItem? =
        AudioPlaylistRepository.prevAudio(this)

    suspend fun moveAudioUp(index: Int): Boolean =
        AudioPlaylistRepository.moveAudioUp(this, index)

    suspend fun moveAudioDown(index: Int): Boolean =
        AudioPlaylistRepository.moveAudioDown(this, index)

    suspend fun toggleAudioMute(index: Int): Boolean =
        AudioPlaylistRepository.toggleAudioMute(this, index)

    suspend fun toggleAudioEnable(index: Int): Boolean =
        AudioPlaylistRepository.toggleAudioEnable(this, index)

    suspend fun toggleAllAudioMute(): Boolean =
        AudioPlaylistRepository.toggleAllAudioMute(this)

    suspend fun toggleAllAudioEnabled(): Boolean =
        AudioPlaylistRepository.toggleAllAudioEnabled(this)

    suspend fun renameAudio(id: String, newName: String): Boolean =
        AudioPlaylistRepository.renameAudio(this, id, newName)

    internal fun writeHookConfigFiles(config: VcamConfig) =
        MediaFileStorageHelper.writeHookConfigFiles(
            config = config,
            camera1Dir = getCamera1Directory(),
            internalCamera1Dir = getInternalCamera1Directory()
        )

    suspend fun importVideoUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val count = addVideos(listOf(uri))
        count > 0
    }

    suspend fun importAudioUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val count = addAudios(listOf(uri))
        count > 0
    }

    suspend fun removeAudio() = withContext(Dispatchers.IO) {
        val currentAudios = _config.value.audioPlaylist
        val removedPaths = currentAudios.map { it.path }
        val conceptMgr = ConceptBackupManager.getInstance(context)
        currentAudios.forEach { item ->
            if (!conceptMgr.isMediaPathProtectedByAnyConcept(item.path)) {
                try { File(item.path).delete() } catch (_: Exception) {}
            }
        }
        withContext(Dispatchers.Main) {
            musicPlayer.resetSessionAudioState()
        }
        try {
            cleanupStaleAudioFiles()
        } catch (_: Exception) {}

        updateConfig {
            it.copy(
                audioPath = "",
                audioUri = null,
                audioName = "",
                audioPlaylist = emptyList(),
                currentAudioIndex = 0,
                isMp3AudioPaused = false
            )
        }
        try {
            val ifsMgr = com.tajasb.infinity.IfsManager.getInstance(context)
            currentAudios.forEach { ifsMgr.clearAudioRole(it.id) }
            ifsMgr.onMediaPlaylistChanged(
                newVideos = _config.value.playlist,
                newAudios = emptyList(),
                broadcastIfActive = false
            )
        } catch (_: Throwable) {}
        writeHookConfigFiles(_config.value)
        broadcastControlEvent(
            action = "RELOAD_AUDIO",
            audioPath = "",
            videoPath = _config.value.videoPath
        )
        conceptMgr.onMediaRemovedFromActivePlaylist(removedPaths)
    }

    fun getAllHookDirectories(): List<File> =
        MediaFileStorageHelper.getAllHookDirectories(
            context = context,
            internalCamera1Dir = getInternalCamera1Directory(),
            targetPackages = _config.value.targetPackages
        )

    fun cleanupStaleVirtualVideo() =
        MediaFileStorageHelper.cleanupStaleVirtualVideo(getAllHookDirectories())

    fun cleanupStaleAudioFiles() =
        MediaFileStorageHelper.cleanupStaleAudioFiles(getAllHookDirectories())

    internal fun copyToAllLocations(source: File, targetName: String) =
        MediaFileStorageHelper.copyToAllLocations(getAllHookDirectories(), source, targetName)

    suspend fun syncToTargetApps(packages: Set<String>) = withContext(Dispatchers.IO) {
        MediaFileStorageHelper.syncToTargetApps(
            internalDir = getInternalCamera1Directory(),
            config = _config.value,
            packages = packages
        )
    }

    private fun syncInitialMediaState() {
        try {
            val conceptMgr = ConceptBackupManager.getInstance(context)
            val allHookDirs = getAllHookDirectories()
            val validPlaylist = _config.value.playlist
            MediaFileStorageHelper.cleanupStaleIndexedVideos(allHookDirs, validPlaylist.size)
            val validPaths = validPlaylist.flatMap { item ->
                val f = File(item.path)
                listOf(item.path, f.absolutePath, try { f.canonicalPath } catch (_: Exception) { item.path })
            }.toSet() + conceptMgr.getAllProtectedMediaPaths()
            val validFileNames = validPlaylist.map { File(it.path).name }.toSet() + conceptMgr.getAllProtectedMediaFileNames()
            MediaFileStorageHelper.cleanupOrphanedMediaFiles(
                hookDirs = allHookDirs,
                validMediaPaths = validPaths,
                validFileNames = validFileNames,
                validCount = validPlaylist.size
            )

            val activeVideo = _config.value.currentVideo
            if (activeVideo != null && File(activeVideo.path).exists() && validPlaylist.isNotEmpty()) {
                copyToAllLocations(File(activeVideo.path), "virtual.mp4")
            } else {
                cleanupStaleVirtualVideo()
            }

            val activeAudio = _config.value.currentAudio
            if (activeAudio == null || !File(activeAudio.path).exists() || _config.value.audioPlaylist.isEmpty()) {
                cleanupStaleAudioFiles()
            }

            writeHookConfigFiles(_config.value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generateTestVideoMp4(outputFile: File, width: Int = 1280, height: Int = 720, label: String = "SMPTE") =
        MediaFileStorageHelper.generateTestVideoMp4(outputFile, width, height, label)

    suspend fun generateAndApplyTestPattern(type: String): String = withContext(Dispatchers.IO) {
        val fileName = "test_${type.lowercase()}_${System.currentTimeMillis().toString().takeLast(6)}.mp4"
        val targetFile = File(getInternalCamera1Directory(), fileName)
        val label = when (type) {
            "SMPTE" -> "SMPTE 720P"
            "FHD" -> "FHD 1080P"
            "PORTRAIT" -> "PORTRAIT 9:16"
            else -> "SMPTE 720P"
        }
        val (w, h) = when (type) {
            "SMPTE" -> 1280 to 720
            "FHD" -> 1920 to 1080
            "PORTRAIT" -> 720 to 1280
            else -> 1280 to 720
        }

        generateTestVideoMp4(targetFile, w, h, label)

        val newItem = VideoItem(
            id = UUID.randomUUID().toString(),
            name = "Pola Uji $label.mp4",
            path = targetFile.absolutePath,
            durationMs = 2000L,
            sizeBytes = targetFile.length(),
            width = w,
            height = h
        )

        val updatedPlaylist = _config.value.playlist + newItem
        val newIndex = updatedPlaylist.size - 1

        copyToAllLocations(targetFile, "virtual.mp4")
        syncToTargetApps(_config.value.targetPackages)

        updateConfig {
            it.copy(
                playlist = updatedPlaylist,
                currentVideoIndex = newIndex,
                videoPath = targetFile.absolutePath
            )
        }
        broadcastControlEvent(action = "RELOAD_MEDIA", videoPath = targetFile.absolutePath)
        "Video uji $label ditambahkan & diaktifkan!"
    }

    suspend fun loadInstalledApps(): List<TargetAppInfo> = withContext(Dispatchers.IO) {
        val sortedList = PerAppConfigRepository.loadInstalledApps(
            context = context,
            prefs = prefs,
            currentTargetPackages = _config.value.targetPackages,
            onFirstInitPackages = { defaultSet ->
                updateConfig { it.copy(targetPackages = HashSet(defaultSet)) }
            }
        )
        _targetApps.value = sortedList
        sortedList
    }

    suspend fun addCustomTargetPackage(pkgOrName: String) = withContext(Dispatchers.IO) {
        val clean = pkgOrName.trim()
        if (clean.isBlank()) return@withContext
        val currentSet = HashSet(_config.value.targetPackages)
        val currentList = _targetApps.value.toMutableList()
        val existingIndex = currentList.indexOfFirst {
            it.packageName.equals(clean, ignoreCase = true) || it.appName.equals(clean, ignoreCase = true)
        }
        val targetPkg = if (existingIndex >= 0) currentList[existingIndex].packageName else clean
        currentSet.add(targetPkg)

        if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            currentList[existingIndex] = existing.copy(isSelected = true)
        } else {
            currentList.add(
                0,
                TargetAppInfo(
                    packageName = clean,
                    appName = clean,
                    isSelected = true,
                    isSystemApp = false,
                    category = "Target Kloning / Kustom"
                )
            )
        }
        _targetApps.value = currentList
        updateConfig {
            val primary = if (it.primaryTargetPackage.isBlank()) targetPkg else it.primaryTargetPackage
            it.copy(targetPackages = currentSet, primaryTargetPackage = primary)
        }
        broadcastControlEvent(action = "UPDATE_CONFIG")
        syncToTargetApps(currentSet)
    }

    suspend fun setTargetPackages(packages: Set<String>) = withContext(Dispatchers.IO) {
        val setCopy = HashSet(packages)
        val curPrimary = _config.value.primaryTargetPackage
        val newPrimary = if (setCopy.contains(curPrimary)) curPrimary else setCopy.firstOrNull() ?: ""
        _targetApps.value = _targetApps.value.map {
            it.copy(isSelected = setCopy.contains(it.packageName))
        }
        updateConfig { it.copy(targetPackages = setCopy, primaryTargetPackage = newPrimary) }
        syncToTargetApps(setCopy)
    }

    suspend fun setPrimaryTargetPackage(packageName: String) = withContext(Dispatchers.IO) {
        val currentSet = HashSet(_config.value.targetPackages)
        if (packageName.isNotBlank()) {
            currentSet.add(packageName)
        }
        _targetApps.value = _targetApps.value.map {
            it.copy(isSelected = currentSet.contains(it.packageName))
        }
        updateConfig { it.copy(targetPackages = currentSet, primaryTargetPackage = packageName) }
        broadcastControlEvent(action = "UPDATE_CONFIG")
        syncToTargetApps(currentSet)
    }

    suspend fun toggleTargetApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val currentSet = HashSet(_config.value.targetPackages)
        var primaryPkg = _config.value.primaryTargetPackage
        val isNowSelected = if (currentSet.contains(packageName)) {
            currentSet.remove(packageName)
            if (primaryPkg == packageName) {
                primaryPkg = currentSet.firstOrNull() ?: ""
            }
            false
        } else {
            currentSet.add(packageName)
            if (primaryPkg.isBlank()) {
                primaryPkg = packageName
            }
            true
        }
        _targetApps.value = _targetApps.value.map {
            if (it.packageName == packageName) it.copy(isSelected = isNowSelected) else it
        }
        updateConfig { it.copy(targetPackages = currentSet, primaryTargetPackage = primaryPkg) }
        syncToTargetApps(currentSet)
        isNowSelected
    }

    fun retrieveMediaMetadata(path: String): MediaMetadata? =
        MediaFileStorageHelper.retrieveMediaMetadata(path)

    internal fun getFileName(uri: Uri): String? =
        MediaFileStorageHelper.getFileName(context, uri)

    // =========================================================================
    // MODUL OPTIMASI PERANGKAT & FOKUS JARINGAN (BOOSTER LIVE)
    // =========================================================================

    suspend fun toggleLiveFocusVpn(enabled: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val next = enabled ?: !_config.value.isLiveFocusVpnEnabled
        updateConfig { it.copy(isLiveFocusVpnEnabled = next) }
        if (next) {
            com.tajasb.optimizer.LiveFocusVpnService.start(context)
        } else {
            com.tajasb.optimizer.LiveFocusVpnService.stop(context)
        }
        broadcastControlEvent(action = "UPDATE_CONFIG")
        next
    }

    suspend fun toggleAutoRamClean(enabled: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val next = enabled ?: !_config.value.isAutoRamCleanEnabled
        updateConfig { it.copy(isAutoRamCleanEnabled = next) }
        next
    }

    suspend fun setFpsGovernorLimit(fps: Int) = withContext(Dispatchers.IO) {
        val safeFps = fps.coerceIn(15, 60)
        updateConfig { it.copy(fpsGovernorLimit = safeFps, fps = safeFps) }
        broadcastControlEvent(action = "UPDATE_CONFIG")
    }

    suspend fun triggerTurboRamClean(): com.tajasb.optimizer.CleanResult = withContext(Dispatchers.IO) {
        com.tajasb.optimizer.DeviceOptimizationManager.triggerTurboClean(context)
    }

    fun getDeviceMemoryStats(): com.tajasb.optimizer.DeviceMemoryStats {
        return com.tajasb.optimizer.DeviceOptimizationManager.getMemoryStats(context)
    }

    suspend fun updateRamCleanWhitelist(packages: Set<String>) = withContext(Dispatchers.IO) {
        updateConfig { it.copy(ramCleanWhitelistPackages = packages) }
        broadcastControlEvent(action = "UPDATE_CONFIG")
    }

    suspend fun updateVpnBypassPackages(packages: Set<String>) = withContext(Dispatchers.IO) {
        updateConfig { it.copy(vpnBypassPackages = packages) }
        if (_config.value.isLiveFocusVpnEnabled || _config.value.isAdBlockerActive) {
            com.tajasb.optimizer.LiveFocusVpnService.stop(context)
            kotlinx.coroutines.delay(200)
            com.tajasb.optimizer.LiveFocusVpnService.start(context)
        }
        broadcastControlEvent(action = "UPDATE_CONFIG")
    }

    suspend fun addBlockedAdPackage(packageName: String): String = withContext(Dispatchers.IO) {
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) return@withContext "Nama paket aplikasi tidak boleh kosong."
        val current = _config.value.blockedAdPackages
        val updated = current + trimmed
        updateConfig { it.copy(blockedAdPackages = updated) }
        
        // Eksekusi pemutusan data & pembekuan
        val msg = com.tajasb.optimizer.AppAdBlockerManager.blockAppNetworkAndFreeze(context, trimmed)
        
        // Refresh VPN Firewall jika sedang aktif
        if (_config.value.isAdBlockerActive || _config.value.isLiveFocusVpnEnabled) {
            com.tajasb.optimizer.LiveFocusVpnService.stop(context)
            kotlinx.coroutines.delay(200)
            com.tajasb.optimizer.LiveFocusVpnService.start(context)
        }
        broadcastControlEvent(action = "UPDATE_CONFIG")
        msg
    }

    suspend fun removeBlockedAdPackage(packageName: String): String = withContext(Dispatchers.IO) {
        val trimmed = packageName.trim()
        val current = _config.value.blockedAdPackages
        val updated = current - trimmed
        updateConfig { it.copy(blockedAdPackages = updated) }
        
        val msg = com.tajasb.optimizer.AppAdBlockerManager.unblockAppNetworkAndUnfreeze(context, trimmed)
        
        if (_config.value.isAdBlockerActive || _config.value.isLiveFocusVpnEnabled) {
            com.tajasb.optimizer.LiveFocusVpnService.stop(context)
            if (updated.isNotEmpty() || _config.value.isLiveFocusVpnEnabled) {
                kotlinx.coroutines.delay(200)
                com.tajasb.optimizer.LiveFocusVpnService.start(context)
            }
        }
        broadcastControlEvent(action = "UPDATE_CONFIG")
        msg
    }

    suspend fun toggleAdBlocker(enabled: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val next = enabled ?: !_config.value.isAdBlockerActive
        updateConfig { it.copy(isAdBlockerActive = next) }
        
        if (next) {
            for (pkg in _config.value.blockedAdPackages) {
                com.tajasb.optimizer.AppAdBlockerManager.blockAppNetworkAndFreeze(context, pkg)
            }
            if (_config.value.blockedAdPackages.isNotEmpty()) {
                com.tajasb.optimizer.LiveFocusVpnService.start(context)
            }
        } else {
            // Jika Live Focus VPN tidak aktif, matikan VPN sepenuhnya
            if (!_config.value.isLiveFocusVpnEnabled) {
                com.tajasb.optimizer.LiveFocusVpnService.stop(context)
            } else {
                // Restart LiveFocus VPN agar aturan blokir ad dicabut
                com.tajasb.optimizer.LiveFocusVpnService.stop(context)
                kotlinx.coroutines.delay(200)
                com.tajasb.optimizer.LiveFocusVpnService.start(context)
            }
        }
        broadcastControlEvent(action = "UPDATE_CONFIG")
        next
    }

    suspend fun toggleAdBlockerAutoFreeze(enabled: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val next = enabled ?: !_config.value.adBlockerAutoFreeze
        updateConfig { it.copy(adBlockerAutoFreeze = next) }
        broadcastControlEvent(action = "UPDATE_CONFIG")
        next
    }

    suspend fun forceStopBlockedApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        com.tajasb.optimizer.AppAdBlockerManager.forceStopApp(context, packageName)
    }

    suspend fun suspendBlockedApp(packageName: String, suspend: Boolean): Boolean = withContext(Dispatchers.IO) {
        com.tajasb.optimizer.AppAdBlockerManager.suspendApp(packageName, suspend)
    }

    suspend fun toggleAutoRocketRecovery(enabled: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val next = enabled ?: !_config.value.isAutoRocketRecoveryEnabled
        updateConfig { it.copy(isAutoRocketRecoveryEnabled = next) }
        broadcastControlEvent(action = "UPDATE_CONFIG")
        next
    }

    suspend fun setAutoRecoveryStallThresholdSec(sec: Int) = withContext(Dispatchers.IO) {
        val safeSec = sec.coerceIn(2, 15)
        updateConfig { it.copy(autoRecoveryStallThresholdSec = safeSec) }
        broadcastControlEvent(action = "UPDATE_CONFIG")
    }

    suspend fun togglePeriodicRamClean(enabled: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val next = enabled ?: !_config.value.isPeriodicRamCleanEnabled
        updateConfig { it.copy(isPeriodicRamCleanEnabled = next) }
        broadcastControlEvent(action = "UPDATE_CONFIG")
        next
    }

    suspend fun setPeriodicRamCleanIntervalMinutes(minutes: Int) = withContext(Dispatchers.IO) {
        val safeMin = minutes.coerceIn(5, 120)
        updateConfig { it.copy(periodicRamCleanIntervalMinutes = safeMin) }
        broadcastControlEvent(action = "UPDATE_CONFIG")
    }

    suspend fun recordAutoRecovery(): Int = withContext(Dispatchers.IO) {
        val newCount = _config.value.autoRecoveryCount + 1
        updateConfig {
            it.copy(
                autoRecoveryCount = newCount,
                lastAutoRecoveryTimestamp = System.currentTimeMillis()
            )
        }
        newCount
    }

    suspend fun resetAutoRecoveryCount() = withContext(Dispatchers.IO) {
        updateConfig { it.copy(autoRecoveryCount = 0) }
    }
}
