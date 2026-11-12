package com.tajasb.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.tajasb.MainHook
import com.tajasb.debug.LogLevel
import java.io.File
import java.util.Locale

/**
 * HookBroadcastReceiver:
 * Modular IPC BroadcastReceiver registered inside target camera apps to handle live
 * rotation, mirror, pause, video/audio switching, seek, and overlay banner events.
 */
object HookBroadcastReceiver {
    private const val TAG = "TajasbCam"

    private val ipcIoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val switchGeneration = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile internal var lastSwitchEventTime = 0L
    @Volatile private var lastSwitchAction = ""
    @Volatile internal var lastSwitchIndex = -1
    @Volatile private var lastSwitchVideoPath = ""
    @Volatile private var lastBannerEventKey = ""
    @Volatile private var lastBannerEventTime = 0L

    fun createAndRegister(ctx: Context): Boolean {
        return try {
            MainHook.syncLiveConfigFromProviderOrFiles(ctx.packageName)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    handleBroadcast(context, intent)
                }
            }

            val filter = IntentFilter().apply {
                addAction("com.tajasb.action.CONTROL_EVENT")
                addAction("com.tajasb.action.UPDATE_CONFIG")
                addAction("com.tajasb.action.SEEK_VIDEO")
                addAction("com.tajasb.action.CYCLE_ROTATION")
                addAction("com.tajasb.action.TOGGLE_MIRROR")
                addAction("com.tajasb.action.TOGGLE_FLIP_VERTICAL")
                addAction("com.tajasb.action.TOGGLE_PAUSE")
                addAction("com.tajasb.action.TOGGLE_PAUSE_VIDEO")
                addAction("com.tajasb.action.TOGGLE_MUTE_VIDEO_AUDIO")
                addAction("com.tajasb.action.TOGGLE_PAUSE_MP3_AUDIO")
                addAction("com.tajasb.action.TOGGLE_PAUSE_AUDIO")
                addAction("com.tajasb.action.TOGGLE_AUDIO")
                addAction("com.tajasb.action.SET_SCALE_MODE")
                addAction("com.tajasb.action.TOGGLE_LIGHTWEIGHT_MODE")
                addAction("com.tajasb.action.TOGGLE_DEBUG_OSD")
                addAction("com.tajasb.action.SET_AUTO_NEXT")
                addAction("com.tajasb.action.CYCLE_MEDIA")
                addAction("com.tajasb.action.RELOAD_MEDIA")
                addAction("com.tajasb.action.NEXT_VIDEO")
                addAction("com.tajasb.action.PREV_VIDEO")
                addAction("com.tajasb.action.SELECT_VIDEO")
                addAction("com.tajasb.action.QUEUE_VIDEO")
                addAction("com.tajasb.action.CANCEL_QUEUED_VIDEO")
                addAction("com.tajasb.action.SELECT_AUDIO")
                addAction("com.tajasb.action.NEXT_AUDIO")
                addAction("com.tajasb.action.PREV_AUDIO")
                addAction("com.tajasb.action.RELOAD_AUDIO")
                addAction("com.tajasb.action.SHOW_OVERLAY_BANNER")
                addAction("com.tajasb.action.HIDE_OVERLAY_BANNER")
                addAction("com.tajasb.action.STOP_PLAYBACK")
                addAction("com.tajasb.action.STOP_SERVICE")
                addAction("com.tajasb.action.STOP_ALL_AUDIO")
                addAction("com.tajasb.action.SET_ANTI_HASH")
                addAction("com.tajasb.action.SET_AUDIO_ANTI_HASH")
                addAction("com.tajasb.action.SET_SESSION_DNA")
                addAction("com.tajasb.action.SET_VOICE_EFFECT")
                addAction("com.tajasb.action.FORCE_STOP")
                addAction("com.tajasb.action.REMOTE_KILL")
                addAction("com.tajasb.ACTION_REMOTE_KILL")
                addAction("com.tajasb.action.REMOTE_DECOY")
                addAction("com.tajasb.action.DECOY_APP_FREEZE")
                addAction("com.tajasb.action.IFS_QUEUE_TRIGGER")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, filter)
            }
            Log.i(TAG, "Control broadcast receiver successfully registered in target app")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register control receiver: ${t.message}")
            false
        }
    }

    private fun handleBroadcast(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "MainHook received broadcast: $action")
        MainHook.lastLiveBroadcastTime = System.currentTimeMillis()
        when (action) {
            "com.tajasb.action.CONTROL_EVENT", "com.tajasb.action.UPDATE_CONFIG" -> {
                val rot = intent.getIntExtra("extra_rotation", MainHook.liveRotation)
                val mir = intent.getBooleanExtra("extra_mirror", MainHook.liveMirror)
                val flipV = intent.getBooleanExtra("extra_flip_v", MainHook.liveFlipV)
                val zoomFactor = intent.getFloatExtra("extra_zoom_factor", MainHook.liveZoomFactor)
                val pause = intent.getBooleanExtra("extra_pause_video", intent.getBooleanExtra("extra_pause", MainHook.livePause))
                val muteVideoAudio = intent.getBooleanExtra("extra_mute_video_audio", MainHook.liveVideoAudioMuted)
                val pauseMp3Audio = intent.getBooleanExtra("extra_pause_mp3_audio", intent.getBooleanExtra("extra_pause_audio", MainHook.liveMp3AudioPaused))
                val muted = intent.getBooleanExtra("extra_muted", MainHook.liveMuted)
                val master = intent.getBooleanExtra("extra_master", MainHook.liveMasterEnabled)
                val scaleName = intent.getStringExtra("extra_scale_mode")
                val scale = if (scaleName != null) {
                    try { com.tajasb.model.ScaleMode.valueOf(scaleName) } catch (_: Exception) { MainHook.liveScaleMode }
                } else MainHook.liveScaleMode
                val autoNext = intent.getBooleanExtra("extra_auto_next_enabled", MainHook.liveAutoNextEnabled)
                val autoNextModeStr = intent.getStringExtra("extra_auto_next_mode")
                val autoNextModeVal = if (!autoNextModeStr.isNullOrBlank()) {
                    try { com.tajasb.model.AutoNextMode.valueOf(autoNextModeStr) } catch (_: Exception) { MainHook.liveAutoNextMode }
                } else MainHook.liveAutoNextMode
                val autoNextLeadMs = intent.getIntExtra("extra_auto_next_lead_ms", -1)
                val autoNextLeadSec = intent.getIntExtra("extra_auto_next_lead_seconds", MainHook.liveAutoNextLeadSeconds)
                val effectiveLeadMs = if (autoNextLeadMs > 0) autoNextLeadMs else (autoNextLeadSec * 1000)
                MainHook.liveAutoNextLeadMs = effectiveLeadMs
                MainHook.liveAutoNextLeadSeconds = (effectiveLeadMs / 1000).coerceAtLeast(1)
                val videoVol = intent.getFloatExtra("extra_video_volume", MainHook.liveVideoVolume)
                MainHook.liveVideoVolume = videoVol
                val seekMs = intent.getLongExtra("extra_video_seek", -1L)
                if (seekMs >= 0L) {
                    MainHook.seekActivePlayer(seekMs)
                }
                val lightweightMode = intent.getBooleanExtra("extra_lightweight_mode", MainHook.liveLightweightMode)
                val targetFps = intent.getIntExtra("extra_fps", MainHook.liveFps)
                val debugOsd = intent.getBooleanExtra("extra_debug_osd_enabled", MainHook.liveDebugOsdEnabled)
                MainHook.liveDebugOsdEnabled = debugOsd
                MainHook.getActiveGlRenderer()?.setDebugOsdEnabled(debugOsd)

                MainHook.liveMasterSpillMode = intent.getBooleanExtra("extra_master_spill_mode", MainHook.liveMasterSpillMode)
                MainHook.liveMasterIndex = intent.getIntExtra("extra_master_index", MainHook.liveMasterIndex)
                MainHook.liveMasterVideoId = intent.getStringExtra("extra_master_video_id") ?: MainHook.liveMasterVideoId
                MainHook.liveIsSpillPlaying = intent.getBooleanExtra("extra_is_spill_playing", MainHook.liveIsSpillPlaying)
                MainHook.liveBridgeCycleMode = intent.getBooleanExtra("extra_bridge_cycle_mode", MainHook.liveBridgeCycleMode)
                MainHook.liveBridgePhase = intent.getStringExtra("extra_bridge_phase") ?: MainHook.liveBridgePhase
                MainHook.liveBridge1Index = intent.getIntExtra("extra_bridge1_index", MainHook.liveBridge1Index)
                MainHook.liveBridge2Index = intent.getIntExtra("extra_bridge2_index", MainHook.liveBridge2Index)

                MainHook.liveIfsMode = intent.getBooleanExtra("extra_ifs_mode", MainHook.liveIfsMode)
                MainHook.liveIfsLooping = intent.getBooleanExtra("extra_ifs_looping", MainHook.liveIfsLooping)
                MainHook.liveIfsPhase = intent.getStringExtra("extra_ifs_phase") ?: MainHook.liveIfsPhase
                MainHook.liveIfsVt = intent.getIntExtra("extra_ifs_vt", MainHook.liveIfsVt)
                if (MainHook.liveIfsMode) {
                    MainHook.liveAutoNextEnabled = false
                    MainHook.getActiveGlRenderer()?.setLooping(MainHook.liveIfsLooping)
                }

                val antiHashStr = intent.getStringExtra("extra_anti_hash_mode")
                if (!antiHashStr.isNullOrBlank()) {
                    try {
                        val mode = com.tajasb.model.AntiHashIntensity.valueOf(antiHashStr)
                        MainHook.liveAntiHashMode = mode
                        MainHook.getActiveGlRenderer()?.setAntiHashMode(mode)
                    } catch (_: Throwable) {}
                }

                val vidAntiHashPct = intent.getFloatExtra("extra_video_anti_hash_percent", -1.0f)
                if (vidAntiHashPct >= 0.0f) {
                    MainHook.liveVideoAntiHashPercent = vidAntiHashPct
                    if (MainHook.liveAntiHashMode != com.tajasb.model.AntiHashIntensity.OFF) {
                        MainHook.getActiveGlRenderer()?.setVideoAntiHashPercent(vidAntiHashPct)
                    }
                }

                val audioAntiHashEnabled = intent.getBooleanExtra("extra_audio_anti_hash_enabled", MainHook.liveAudioAntiHashEnabled)
                val audioAntiHashStr = intent.getStringExtra("extra_audio_anti_hash_mode")
                if (!audioAntiHashStr.isNullOrBlank()) {
                    try {
                        MainHook.liveAudioAntiHashMode = com.tajasb.model.AudioAntiHashIntensity.valueOf(audioAntiHashStr)
                    } catch (_: Throwable) {}
                }
                MainHook.liveAudioAntiHashEnabled = audioAntiHashEnabled
                MainHook.getActiveGlRenderer()?.setAudioAntiHash(MainHook.liveAudioAntiHashEnabled, MainHook.liveAudioAntiHashMode)

                val dnaEnabled = intent.getBooleanExtra("extra_enable_dna_module", MainHook.liveSessionDnaEnabled)
                val dnaIdx = intent.getIntExtra("extra_session_dna_index", MainHook.liveSessionDnaIndex).coerceAtLeast(1)
                val dnaSeed = intent.getLongExtra("extra_session_dna_seed", MainHook.liveSessionDnaSeed)
                if (dnaEnabled != MainHook.liveSessionDnaEnabled || dnaIdx != MainHook.liveSessionDnaIndex || dnaSeed != MainHook.liveSessionDnaSeed) {
                    MainHook.liveSessionDnaEnabled = dnaEnabled
                    MainHook.liveSessionDnaIndex = dnaIdx
                    MainHook.liveSessionDnaSeed = dnaSeed
                    MainHook.getActiveGlRenderer()?.setSessionDna(dnaIdx, dnaSeed, dnaEnabled)
                }

                val audAntiHashPct = intent.getFloatExtra("extra_audio_anti_hash_percent", -1.0f)
                if (audAntiHashPct > 0.0f) {
                    MainHook.getActiveGlRenderer()?.setAudioAntiHashPercent(audAntiHashPct)
                }

                val dnaZoomEnabled = intent.getBooleanExtra("extra_dna_zoom_enabled", MainHook.liveDnaZoomEnabled)
                val dnaCustomZoom = intent.getFloatExtra("extra_session_dna_custom_zoom", MainHook.liveSessionDnaCustomZoom)
                val panX = intent.getFloatExtra("extra_session_dna_pan_x", MainHook.liveSessionDnaPanX)
                val panY = intent.getFloatExtra("extra_session_dna_pan_y", MainHook.liveSessionDnaPanY)
                MainHook.liveDnaZoomEnabled = dnaZoomEnabled
                if (dnaCustomZoom > 1.0f) {
                    MainHook.liveSessionDnaCustomZoom = dnaCustomZoom
                    MainHook.liveSessionDnaPanX = panX
                    MainHook.liveSessionDnaPanY = panY
                    MainHook.getActiveGlRenderer()?.setSessionDnaFraming(dnaCustomZoom, panX, panY, dnaZoomEnabled)
                } else {
                    MainHook.getActiveGlRenderer()?.setDnaZoomEnabled(dnaZoomEnabled)
                }

                val extraVideoName = intent.getStringExtra("extra_video_name") ?: intent.getStringExtra("extra_video_title")
                val extraAction = intent.getStringExtra("extra_action")
                val extraSource = intent.getStringExtra("extra_source")

                if (extraAction == "RENAME_VIDEO" && !extraVideoName.isNullOrBlank()) {
                    MainHook.liveVideoDisplayName = extraVideoName
                    MainHook.getActiveGlRenderer()?.updateVideoDisplayName(extraVideoName)
                    MainHook.hookLog(LogLevel.INFO, "IPC", "✏️ [RENAME] Nama video aktif disinkronkan: \"$extraVideoName\"")
                }

                MainHook.applyLiveConfig(
                    rotation = rot,
                    mirror = mir,
                    flipV = flipV,
                    pause = pause,
                    muteVideoAudio = muteVideoAudio,
                    pauseMp3Audio = pauseMp3Audio,
                    muted = muted,
                    master = master,
                    scaleMode = scale,
                    autoNextEnabled = autoNext,
                    autoNextMode = autoNextModeVal,
                    videoVolume = videoVol,
                    lightweightMode = lightweightMode,
                    fps = targetFps,
                    zoomFactor = zoomFactor
                )

                val hasBridgePending = intent.getIntExtra("extra_pending_index", -1) >= 0 ||
                                       intent.getIntExtra("extra_pending_bridge_content_index", -1) >= 0 ||
                                       intent.getBooleanExtra("extra_bridge_has_pending", false) ||
                                       (intent.getIntExtra("extra_bridge_queue_size", 0) > 0)

                val isLiveRelayActive = intent.getBooleanExtra("extra_live_stream_active", MainHook.liveLiveStreamActive)
                val liveRelayUrl = intent.getStringExtra("extra_live_stream_url") ?: MainHook.liveLiveStreamUrl
                MainHook.liveLiveStreamActive = isLiveRelayActive
                val forceRelayReload = (extraAction == "RELOAD_MEDIA" || extraSource == "LIVE_STREAM_RELAY" || extraAction == "SET_LIVE_STREAM_SOURCE")
                MainHook.getActiveGlRenderer()?.setLiveStreamSource(
                    url = if (isLiveRelayActive && liveRelayUrl.isNotBlank()) liveRelayUrl else null,
                    forceReload = forceRelayReload
                )

                if (extraAction == "BRIDGE_QUEUE_CONTENT" || (MainHook.liveBridgeCycleMode && hasBridgePending)) {
                    MainHook.liveBridgeHasPending = true
                } else if (MainHook.liveBridgeCycleMode && !hasBridgePending && MainHook.liveBridgePhase == "MASTER_LOOP") {
                    MainHook.liveBridgeHasPending = false
                }

                if (MainHook.liveBridgeCycleMode && MainHook.liveBridgePhase == "MASTER_LOOP") {
                    val isPending = MainHook.liveBridgeHasPending || hasBridgePending
                    MainHook.getActiveGlRenderer()?.setLooping(!isPending)
                    Log.i(TAG, "MainHook UPDATE_CONFIG: Bridge Master Loop setLooping=${!isPending} (isPending=$isPending)")
                }

                if (extraAction == "SEEK_VIDEO") {
                    val actSeekMs = intent.getLongExtra("extra_video_seek", 0L)
                    MainHook.seekActivePlayer(actSeekMs)
                }
                if (extraAction == "QUEUE_VIDEO") {
                    val targetIndex = intent.getIntExtra("extra_pending_index", -1)
                    val videoPath = intent.getStringExtra("extra_video_path")
                    MainHook.livePendingVideoIndex = if (targetIndex >= 0) targetIndex else null
                    MainHook.livePendingVideoPath = videoPath
                    // Disable native looping on active player so it doesn't loop forever, and immediately pre-decode standby video
                    MainHook.getActiveGlRenderer()?.setLooping(false)
                    MainHook.prefetchNextVideo(context?.packageName)
                    Log.i(TAG, "MainHook: Custom video queued & standby pre-decoding started: index=${MainHook.livePendingVideoIndex}, path=${MainHook.livePendingVideoPath}")
                }
                if (extraAction == "BRIDGE_QUEUE_CONTENT") {
                    // For Bridge Cycle, disable looping on the master player and pre-decode Bridge II into standby slot immediately
                    MainHook.livePendingVideoIndex = null
                    MainHook.livePendingVideoPath = null
                    MainHook.liveBridgeHasPending = true
                    MainHook.getActiveGlRenderer()?.setLooping(false)
                    MainHook.prefetchNextVideo(context?.packageName)
                    Log.i(TAG, "MainHook: Bridge Cycle content queued. Disabled native loop on Master player & pre-decoding Bridge II into standby slot.")
                }
                if (extraAction == "IFS_QUEUE_TRIGGER") {
                    MainHook.livePendingVideoIndex = null
                    MainHook.livePendingVideoPath = null
                    MainHook.liveIfsLooping = false
                    MainHook.getActiveGlRenderer()?.setLooping(false)
                    MainHook.prefetchNextVideo(context?.packageName)
                    Log.i(TAG, "MainHook: IFS trigger queued during loop. Disabled native loop & pre-decoding next IFS phase into standby slot.")
                }
                if (extraAction == "CANCEL_QUEUED_VIDEO") {
                    MainHook.livePendingVideoIndex = null
                    MainHook.livePendingVideoPath = null
                    MainHook.liveBridgeHasPending = false
                    MainHook.releasePreloadedPlayer()
                    if (MainHook.liveBridgeCycleMode && MainHook.liveBridgePhase == "MASTER_LOOP") {
                        MainHook.getActiveGlRenderer()?.setLooping(true)
                        Log.i(TAG, "MainHook: Restored native loop on Master player after queue cancellation.")
                    }
                    Log.i(TAG, "MainHook: Cancelled queued video transition")
                }
                val isVideoSwitch = extraAction in listOf("SELECT_VIDEO", "NEXT_VIDEO", "PREV_VIDEO", "RELOAD_MEDIA", "CYCLE_MEDIA")
                if (isVideoSwitch && extraSource != "SEAMLESS_PRELOAD" && extraSource != "CUSTOM_QUEUE") {
                    val targetIndex = intent.getIntExtra("extra_current_index", -1)
                    val videoPath = intent.getStringExtra("extra_video_path")
                    val purgeCache = intent.getBooleanExtra("extra_purge_cache", false) || (extraAction == "RELOAD_MEDIA")
                    val now = android.os.SystemClock.uptimeMillis()

                    val targetContext = context ?: MainHook.appContext
                    if (purgeCache) {
                        MainHook.purgeCachedVideoFiles(targetContext)
                    }

                    val curIdx = MainHook.liveCurrentVideoIndex
                    val activeRenderer = MainHook.getActiveGlRenderer()
                    val isSameIndex = (targetIndex >= 0 && (targetIndex == lastSwitchIndex || targetIndex == curIdx || targetIndex == activeRenderer?.currentPlaylistIndex))
                    val isNewRollingVsOrPath = (!videoPath.isNullOrBlank() && (videoPath != lastSwitchVideoPath || videoPath.contains("vs_rolling_")))

                    val isIdenticalDuplicate = !purgeCache && extraAction != "RELOAD_MEDIA" &&
                        !isNewRollingVsOrPath &&
                        isSameIndex && (now - lastSwitchEventTime < 1200L)
                    if (isIdenticalDuplicate) {
                        Log.d(TAG, "MainHook: Ignored redundant video switch broadcast for index $targetIndex within 1200ms debounce window (lastSwitchIndex=$lastSwitchIndex, curIdx=$curIdx, elapsed=${now - lastSwitchEventTime}ms)")
                    } else {
                        lastSwitchEventTime = now
                        lastSwitchAction = extraAction ?: ""
                        lastSwitchIndex = targetIndex
                        if (!videoPath.isNullOrBlank()) {
                            lastSwitchVideoPath = videoPath
                        }
                        if (targetIndex >= 0) {
                            MainHook.liveCurrentVideoIndex = targetIndex
                            activeRenderer?.currentPlaylistIndex = targetIndex
                        }
                        if (!extraVideoName.isNullOrBlank()) {
                            MainHook.liveVideoDisplayName = extraVideoName
                        }

                        val myGen = switchGeneration.incrementAndGet()
                        ipcIoExecutor.execute {
                            try {
                                if (myGen != switchGeneration.get()) return@execute
                                val ioStart = android.os.SystemClock.uptimeMillis()
                                val directFile = if (!videoPath.isNullOrBlank()) File(videoPath) else null
                                val directName = directFile?.name ?: ""
                                val isRollingVs = directName.startsWith("vs_rolling_") && directName.endsWith(".mp4")
                                val targetFile = if (directFile != null && directFile.exists() && directFile.canRead() && directFile.length() > 0L) {
                                    directFile
                                } else {
                                    val segment = when {
                                        isRollingVs -> directName
                                        targetIndex >= 0 -> "video_$targetIndex.mp4"
                                        else -> "virtual.mp4"
                                    }
                                    val shouldForceRefresh = purgeCache || (!isRollingVs && targetIndex < 0)
                                    MainHook.fetchVirtualVideoFromProvider(targetContext, forceRefresh = shouldForceRefresh, targetSegment = segment)
                                        ?: MainHook.getVirtualVideoFile(targetContext?.packageName, forceRefresh = true)
                                }

                                if (myGen != switchGeneration.get()) {
                                    Log.d(TAG, "MainHook: Aborting stale async video switch (gen=$myGen < current=${switchGeneration.get()})")
                                    return@execute
                                }

                                if (targetFile != null && targetFile.exists() && targetFile.length() > 0L) {
                                    val ioDuration = android.os.SystemClock.uptimeMillis() - ioStart
                                    lastSwitchEventTime = android.os.SystemClock.uptimeMillis()
                                    val sizeMb = String.format(Locale.US, "%.1f", (targetFile.length() / (1024f * 1024f)))
                                    val effName = if (MainHook.liveVideoDisplayName.isNotBlank()) MainHook.liveVideoDisplayName else targetFile.name
                                    MainHook.hookLog(LogLevel.INFO, TAG, "⚡ [IO] Video $effName ($sizeMb MB) siap dalam ${ioDuration}ms via Background Thread.")
                                    MainHook.updateLastVideoTracking(targetFile.absolutePath, targetFile.lastModified())
                                    if (!videoPath.isNullOrBlank()) {
                                        MainHook.updateLastVideoPathOnly(videoPath)
                                    }
                                    MainHook.getActiveGlRenderer()?.switchVideoFile(targetFile, effName, forceSwitch = purgeCache)
                                    val masterIdx = intent.getIntExtra("extra_master_index", -1)
                                    val isBridgeCycle = intent.getBooleanExtra("extra_bridge_cycle_mode", MainHook.liveBridgeCycleMode)
                                    val bridgePhase = intent.getStringExtra("extra_bridge_phase") ?: MainHook.liveBridgePhase
                                    val isMasterSpill = intent.getBooleanExtra("extra_master_spill_mode", MainHook.liveMasterSpillMode)

                                    if (MainHook.liveIfsMode) {
                                        MainHook.getActiveGlRenderer()?.setLooping(MainHook.liveIfsLooping)
                                        Log.i(TAG, "IFS active: switched video $effName looping=${MainHook.liveIfsLooping} (phase=${MainHook.liveIfsPhase}, vt=${MainHook.liveIfsVt})")
                                    } else if (isBridgeCycle) {
                                        val hasPending = intent.getIntExtra("extra_pending_index", -1) >= 0 ||
                                                         intent.getIntExtra("extra_pending_bridge_content_index", -1) >= 0 ||
                                                         intent.getBooleanExtra("extra_bridge_has_pending", false)
                                        val isTargetMaster = (targetIndex == masterIdx) || (bridgePhase == "MASTER_LOOP")
                                        val isLooping = isTargetMaster && !hasPending
                                        MainHook.getActiveGlRenderer()?.setLooping(isLooping)
                                        Log.i(TAG, "BridgeCycle active: switched video $effName looping=$isLooping (phase=$bridgePhase, targetIdx=$targetIndex, masterIdx=$masterIdx, hasPending=$hasPending)")
                                    } else if (isMasterSpill) {
                                        MainHook.getActiveGlRenderer()?.setLooping(false)
                                        Log.i(TAG, "MasterSpill active: switched video $effName looping=false (managed by MasterSpill cycle, targetIdx=$targetIndex, masterIdx=$masterIdx)")
                                    }
                                    Log.i(TAG, "Switched video asynchronously to: $effName (index=$targetIndex)")
                                } else {
                                    MainHook.hookLog(LogLevel.WARN, TAG, "⚠️ [IO] File video untuk index $targetIndex tidak ditemukan atau telah dihapus!")
                                    if (videoPath.isNullOrBlank() || extraAction == "RELOAD_MEDIA") {
                                        MainHook.getActiveGlRenderer()?.setPaused(true)
                                    }
                                }
                            } catch (t: Throwable) {
                                MainHook.hookLog(LogLevel.ERROR, TAG, "🚨 [IO] Async video switch error: ${t.message}", t)
                                Log.e(TAG, "Async video switch error: ${t.message}", t)
                            }
                        }
                    }
                } else if (isVideoSwitch && extraSource == "SEAMLESS_PRELOAD") {
                    val videoPath = intent.getStringExtra("extra_video_path")
                    if (!videoPath.isNullOrBlank()) {
                        MainHook.updateLastVideoPathOnly(videoPath)
                    }
                }

                val videoSeekMs = intent.getLongExtra("extra_video_seek", -1L)
                if (videoSeekMs >= 0L) {
                    MainHook.getActiveGlRenderer()?.seekTo(videoSeekMs.toInt())
                    MainHook.hookLog(LogLevel.INFO, "IPC", "Seek video diproses: ${videoSeekMs}ms")
                }
                if (!extraAction.isNullOrBlank()) {
                    MainHook.hookLog(LogLevel.DEBUG, "IPC", "CONTROL_EVENT: $extraAction (rot=${MainHook.liveRotation}, pause=${MainHook.livePause})")
                }

                val isAudioSwitch = extraAction in listOf("SELECT_AUDIO", "NEXT_AUDIO", "PREV_AUDIO", "RELOAD_AUDIO")
                if (isAudioSwitch) {
                    val audioPath = intent.getStringExtra("extra_audio_path")
                    val targetAudio = if (!audioPath.isNullOrBlank() && File(audioPath).exists() && File(audioPath).canRead()) {
                        File(audioPath)
                    } else {
                        MainHook.getVirtualAudioFile(context?.packageName)
                    }
                    MainHook.getActiveGlRenderer()?.setAudioFile(targetAudio)
                }

                if (extraAction == "SHOW_OVERLAY_BANNER") {
                    injectOverlayFromIntent(intent)
                } else if (extraAction == "HIDE_OVERLAY_BANNER") {
                    val bannerType = intent.getStringExtra("extra_banner_type") ?: ""
                    when (bannerType) {
                        "STICKER" -> MainHook.getActiveGlRenderer()?.hideSticker()
                        "WATERMARK" -> MainHook.getActiveGlRenderer()?.hideWatermark()
                        "AUTO_TIME" -> MainHook.getActiveGlRenderer()?.hideAutoTime()
                        "QURAN_KHATAM" -> MainHook.getActiveGlRenderer()?.hideQuranOverlay()
                        "QURAN_STATS" -> MainHook.getActiveGlRenderer()?.hideQuranStatsOverlay()
                        "EVENT_STATS" -> MainHook.getActiveGlRenderer()?.hideEventStatsOverlay()
                        "TOP_LIKERS" -> MainHook.getActiveGlRenderer()?.hideTopLikersOverlay()
                        "EVENT_LIKE" -> MainHook.getActiveGlRenderer()?.hideLikeOverlay()
                        "EVENT_GIFT" -> MainHook.getActiveGlRenderer()?.hideGiftOverlay()
                        "ALL" -> MainHook.getActiveGlRenderer()?.hideAllOverlays()
                        else -> MainHook.getActiveGlRenderer()?.hideOverlayBanner()
                    }
                }
            }
            "com.tajasb.action.SEEK_VIDEO" -> {
                val seekMs = intent.getLongExtra("extra_video_seek", intent.getLongExtra("extra_position_ms", 0L))
                MainHook.getActiveGlRenderer()?.seekTo(seekMs.toInt())
                Log.i(TAG, "MainHook seek video to ${seekMs}ms")
            }
            "com.tajasb.action.CYCLE_ROTATION" -> {
                val rot = intent.getIntExtra("extra_rotation", (MainHook.liveRotation + 90) % 360)
                MainHook.applyLiveConfig(rotation = rot)
            }
            "com.tajasb.action.TOGGLE_MIRROR" -> {
                val mir = intent.getBooleanExtra("extra_mirror", !MainHook.liveMirror)
                MainHook.applyLiveConfig(mirror = mir)
            }
            "com.tajasb.action.TOGGLE_FLIP_VERTICAL" -> {
                val flipV = intent.getBooleanExtra("extra_flip_v", !MainHook.liveFlipV)
                MainHook.applyLiveConfig(flipV = flipV)
            }
            "com.tajasb.action.ZOOM", "com.tajasb.action.SET_ZOOM" -> {
                val zoom = intent.getFloatExtra("extra_zoom_factor", MainHook.liveZoomFactor)
                MainHook.applyLiveConfig(zoomFactor = zoom)
            }
            "com.tajasb.action.TOGGLE_PAUSE", "com.tajasb.action.TOGGLE_PAUSE_VIDEO" -> {
                val pause = intent.getBooleanExtra("extra_pause_video", intent.getBooleanExtra("extra_pause", !MainHook.livePause))
                MainHook.applyLiveConfig(pause = pause)
            }
            "com.tajasb.action.TOGGLE_MUTE_VIDEO_AUDIO" -> {
                val muteVideoAudio = intent.getBooleanExtra("extra_mute_video_audio", !MainHook.liveVideoAudioMuted)
                MainHook.applyLiveConfig(muteVideoAudio = muteVideoAudio)
            }
            "com.tajasb.action.TOGGLE_PAUSE_MP3_AUDIO", "com.tajasb.action.TOGGLE_PAUSE_AUDIO" -> {
                val pauseMp3 = intent.getBooleanExtra("extra_pause_mp3_audio", intent.getBooleanExtra("extra_pause_audio", !MainHook.liveMp3AudioPaused))
                MainHook.applyLiveConfig(pauseMp3Audio = pauseMp3)
            }
            "com.tajasb.action.TOGGLE_AUDIO" -> {
                val muted = intent.getBooleanExtra("extra_muted", !MainHook.liveMuted)
                MainHook.applyLiveConfig(muted = muted)
            }
            "com.tajasb.action.SET_SCALE_MODE" -> {
                val scaleName = intent.getStringExtra("extra_scale_mode") ?: "CROP"
                val scale = try { com.tajasb.model.ScaleMode.valueOf(scaleName) } catch (_: Exception) { com.tajasb.model.ScaleMode.CROP }
                MainHook.applyLiveConfig(scaleMode = scale)
            }
            "com.tajasb.action.TOGGLE_LIGHTWEIGHT_MODE" -> {
                val light = intent.getBooleanExtra("extra_lightweight_mode", !MainHook.liveLightweightMode)
                MainHook.applyLiveConfig(lightweightMode = light)
            }
            "com.tajasb.action.TOGGLE_DEBUG_OSD" -> {
                val debugOsd = intent.getBooleanExtra("extra_debug_osd_enabled", !MainHook.liveDebugOsdEnabled)
                MainHook.liveDebugOsdEnabled = debugOsd
                MainHook.getActiveGlRenderer()?.setDebugOsdEnabled(debugOsd)
                Log.i(TAG, "MainHook debug OSD toggled: $debugOsd")
            }
            "com.tajasb.action.SET_ANTI_HASH" -> {
                val modeStr = intent.getStringExtra("extra_anti_hash_mode") ?: "OFF"
                val mode = try { com.tajasb.model.AntiHashIntensity.valueOf(modeStr) } catch (_: Throwable) { com.tajasb.model.AntiHashIntensity.OFF }
                MainHook.liveAntiHashMode = mode
                MainHook.getActiveGlRenderer()?.setAntiHashMode(mode)
                val pct = intent.getFloatExtra("extra_video_anti_hash_percent", -1.0f)
                if (pct >= 0.0f) {
                    MainHook.liveVideoAntiHashPercent = pct
                    if (mode != com.tajasb.model.AntiHashIntensity.OFF) {
                        MainHook.getActiveGlRenderer()?.setVideoAntiHashPercent(pct)
                    }
                }
                MainHook.hookLog(LogLevel.INFO, TAG, "🛡️ [ANTI-HASH] Mode Anti-Deteksi Server TikTok disetel: ${mode.name} (${MainHook.liveVideoAntiHashPercent}%)")
            }
            "com.tajasb.action.SET_AUDIO_ANTI_HASH" -> {
                val enabled = intent.getBooleanExtra("extra_audio_anti_hash_enabled", true)
                val modeStr = intent.getStringExtra("extra_audio_anti_hash_mode") ?: "MEDIUM"
                val mode = try { com.tajasb.model.AudioAntiHashIntensity.valueOf(modeStr) } catch (_: Throwable) { com.tajasb.model.AudioAntiHashIntensity.MEDIUM }
                MainHook.liveAudioAntiHashEnabled = enabled
                MainHook.liveAudioAntiHashMode = mode
                MainHook.getActiveGlRenderer()?.setAudioAntiHash(enabled, mode)
                MainHook.hookLog(LogLevel.INFO, TAG, "🛡️ [AUDIO ANTI-DETEKSI] Audio Anti-Deteksi disetel: enabled=$enabled, mode=${mode.displayName}")
            }
            "com.tajasb.action.SET_SESSION_DNA" -> {
                val dnaEnabled = intent.getBooleanExtra("extra_enable_dna_module", MainHook.liveSessionDnaEnabled)
                val dnaIdx = intent.getIntExtra("extra_session_dna_index", MainHook.liveSessionDnaIndex).coerceAtLeast(1)
                val dnaSeed = intent.getLongExtra("extra_session_dna_seed", MainHook.liveSessionDnaSeed)
                MainHook.liveSessionDnaEnabled = dnaEnabled
                MainHook.liveSessionDnaIndex = dnaIdx
                MainHook.liveSessionDnaSeed = dnaSeed
                MainHook.getActiveGlRenderer()?.setSessionDna(dnaIdx, dnaSeed, dnaEnabled)
                val dna = com.tajasb.model.SessionDnaProfile.forSession(dnaIdx, dnaSeed)
                MainHook.hookLog(LogLevel.INFO, TAG, "🧬 [SESSION-DNA] Mutasi DNA Lintas Sesi (${if (dnaEnabled) "AKTIF" else "SIAGA"}): ${dna.badgeText} (${dna.quadrantLabel}) | ${dna.summaryText}")
            }
            "com.tajasb.action.SET_VOICE_EFFECT" -> {
                val effectStr = intent.getStringExtra("extra_voice_effect") ?: "NORMAL"
                val effect = try { com.tajasb.model.VoiceEffect.valueOf(effectStr) } catch (_: Throwable) { com.tajasb.model.VoiceEffect.NORMAL }
                MainHook.getActiveGlRenderer()?.setVoiceEffect(effect)
                MainHook.hookLog(LogLevel.INFO, TAG, "🎭 [VOICE-EFFECT] Efek Suara disetel: ${effect.label} (${effect.pitchMultiplier}x pitch)")
            }
            "com.tajasb.action.CYCLE_MEDIA", "com.tajasb.action.RELOAD_MEDIA", "com.tajasb.action.NEXT_VIDEO", "com.tajasb.action.PREV_VIDEO", "com.tajasb.action.SELECT_VIDEO" -> {
                val muteVideoAudio = intent.getBooleanExtra("extra_mute_video_audio", MainHook.liveVideoAudioMuted)
                MainHook.applyLiveConfig(muteVideoAudio = muteVideoAudio)
                val now = android.os.SystemClock.uptimeMillis()
                val isReload = (intent.action == "com.tajasb.action.RELOAD_MEDIA")
                val debounceWindow = if (isReload) 850L else 950L
                if (now - lastSwitchEventTime < debounceWindow) {
                    Log.d(TAG, "MainHook: Mengabaikan broadcast sekunder (${intent.action}) untuk transisi video yang sudah diproses")
                } else {
                    lastSwitchEventTime = now
                    val targetContext = context ?: MainHook.appContext
                    val myGen = switchGeneration.incrementAndGet()
                    ipcIoExecutor.execute {
                        try {
                            if (myGen != switchGeneration.get()) return@execute
                            val curIdx = MainHook.liveCurrentVideoIndex
                            val segment = if (!isReload && curIdx >= 0) "video_$curIdx.mp4" else "virtual.mp4"
                            val targetFile = MainHook.fetchVirtualVideoFromProvider(targetContext, forceRefresh = isReload, targetSegment = segment)
                                ?: MainHook.getVirtualVideoFile(targetContext?.packageName, forceRefresh = true)
                            if (myGen != switchGeneration.get()) return@execute
                            if (targetFile != null && targetFile.exists() && targetFile.length() > 0) {
                                lastSwitchEventTime = android.os.SystemClock.uptimeMillis()
                                MainHook.updateLastVideoTracking(targetFile.absolutePath, targetFile.lastModified())
                                MainHook.getActiveGlRenderer()?.switchVideoFile(targetFile)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
            "com.tajasb.action.SET_AUTO_NEXT" -> {
                val autoNext = intent.getBooleanExtra("extra_auto_next_enabled", !MainHook.liveAutoNextEnabled)
                val modeStr = intent.getStringExtra("extra_auto_next_mode")
                val mode = if (!modeStr.isNullOrBlank()) {
                    try { com.tajasb.model.AutoNextMode.valueOf(modeStr) } catch (_: Exception) { MainHook.liveAutoNextMode }
                } else MainHook.liveAutoNextMode
                MainHook.applyLiveConfig(autoNextEnabled = autoNext, autoNextMode = mode)
            }
            "com.tajasb.action.SELECT_AUDIO", "com.tajasb.action.NEXT_AUDIO", "com.tajasb.action.PREV_AUDIO", "com.tajasb.action.RELOAD_AUDIO" -> {
                val audioPath = intent.getStringExtra("extra_audio_path")
                val targetAudio = if (!audioPath.isNullOrBlank() && File(audioPath).exists() && File(audioPath).canRead()) {
                    File(audioPath)
                } else {
                    MainHook.getVirtualAudioFile(context?.packageName)
                }
                MainHook.getActiveGlRenderer()?.setAudioFile(targetAudio)
            }
            "com.tajasb.action.SHOW_OVERLAY_BANNER" -> {
                val senderName = intent.getStringExtra("extra_sender_name") ?: "Penonton"
                val content = intent.getStringExtra("extra_content") ?: ""
                val bannerType = intent.getStringExtra("extra_banner_type") ?: "COMMENT"
                val customPrefix = intent.getStringExtra("extra_custom_prefix") ?: ""
                val formatMode = intent.getStringExtra("extra_format_mode") ?: "PREFIX_AND_COMMENT"
                val durationMs = intent.getLongExtra("extra_duration_ms", 4000L)
                val posXpct = intent.getFloatExtra("extra_pos_x_pct", 0.50f)
                val posYpct = intent.getFloatExtra("extra_pos_y_pct", 0.35f)
                val bannerRotation = intent.getIntExtra("extra_banner_rotation", 0)
                val bannerScale = intent.getFloatExtra("extra_banner_scale", 1.0f)
                val isBannerMirrored = intent.getBooleanExtra("extra_banner_mirrored", false)
                val dispStyleExtra = intent.getStringExtra("extra_stiker_display_style") ?: "WITH_BLOCK"
                val runTextExtra = intent.getBooleanExtra("extra_is_running_text", false)
                val runSpeedExtra = intent.getFloatExtra("extra_running_text_speed", 1.0f)
                val runDirExtra = intent.getStringExtra("extra_running_text_direction") ?: "LEFT"
                val stikerOutWidthExtra = intent.getIntExtra("extra_stiker_outline_width", 2)
                val stikerOutColorExtra = intent.getStringExtra("extra_stiker_outline_color") ?: "#FFFFFF"
                val capAlphaExtra = intent.getFloatExtra("extra_capsule_alpha", 0.75f)
                val fontSizeExtra = intent.getFloatExtra("extra_font_size_sp", 16.0f)
                val capPadHExtra = intent.getIntExtra("extra_capsule_padding_h", 27)
                val capPadVExtra = intent.getIntExtra("extra_capsule_padding_v", 2)

                val eventKey = "$senderName|$content|$bannerType|$durationMs|$customPrefix|$formatMode|$dispStyleExtra|$runTextExtra|$runSpeedExtra|$runDirExtra|$posXpct|$posYpct|$bannerRotation|$bannerScale|$isBannerMirrored|$stikerOutWidthExtra|$stikerOutColorExtra|$capAlphaExtra|$fontSizeExtra|$capPadHExtra|$capPadVExtra"
                val now = System.currentTimeMillis()
                val dedupWindowMs = if (
                    bannerType == "STICKER" ||
                    bannerType == "WATERMARK" ||
                    bannerType == "AUTO_TIME" ||
                    bannerType == "QURAN_KHATAM" ||
                    bannerType == "QURAN_STATS" ||
                    bannerType == "EVENT_STATS" ||
                    bannerType == "TOP_LIKERS" ||
                    bannerType == "AVATAR_BUBBLE" ||
                    bannerType == "TUG_OF_WAR"
                ) 30L else 450L
                if (eventKey == lastBannerEventKey && (now - lastBannerEventTime) < dedupWindowMs) {
                    Log.d(TAG, "MainHook: Skipping duplicate SHOW_OVERLAY_BANNER event")
                } else {
                    lastBannerEventKey = eventKey
                    lastBannerEventTime = now
                    injectOverlayFromIntent(intent)
                }
            }
            "com.tajasb.action.HIDE_OVERLAY_BANNER" -> {
                val bannerType = intent.getStringExtra("extra_banner_type") ?: ""
                when (bannerType) {
                    "STICKER" -> {
                        MainHook.getActiveGlRenderer()?.hideSticker()
                        Log.i(TAG, "MainHook hid sticker in virtual camera")
                    }
                    "WATERMARK" -> {
                        MainHook.getActiveGlRenderer()?.hideWatermark()
                        Log.i(TAG, "MainHook hid watermark in virtual camera")
                    }
                    "AUTO_TIME" -> {
                        MainHook.getActiveGlRenderer()?.hideAutoTime()
                        Log.i(TAG, "MainHook hid auto time capsule in virtual camera")
                    }
                    "QURAN_KHATAM" -> {
                        MainHook.getActiveGlRenderer()?.hideQuranOverlay()
                        Log.i(TAG, "MainHook hid quran overlay in virtual camera")
                    }
                    "QURAN_STATS" -> {
                        MainHook.getActiveGlRenderer()?.hideQuranStatsOverlay()
                        Log.i(TAG, "MainHook hid quran stats overlay in virtual camera")
                    }
                    "TUG_OF_WAR" -> {
                        MainHook.getActiveGlRenderer()?.hideTugOfWarOverlay()
                        Log.i(TAG, "MainHook hid tug of war overlay in virtual camera")
                    }
                    "EVENT_STATS" -> {
                        MainHook.getActiveGlRenderer()?.hideEventStatsOverlay()
                        Log.i(TAG, "MainHook hid event stats overlay in virtual camera")
                    }
                    "TOP_LIKERS" -> {
                        MainHook.getActiveGlRenderer()?.hideTopLikersOverlay()
                        Log.i(TAG, "MainHook hid top likers overlay in virtual camera")
                    }
                    "EVENT_LIKE" -> {
                        MainHook.getActiveGlRenderer()?.hideLikeOverlay()
                        Log.i(TAG, "MainHook hid like overlay in virtual camera")
                    }
                    "EVENT_GIFT" -> {
                        MainHook.getActiveGlRenderer()?.hideGiftOverlay()
                        Log.i(TAG, "MainHook hid gift overlay in virtual camera")
                    }
                    "AVATAR_BUBBLE" -> {
                        MainHook.getActiveGlRenderer()?.hideAvatarBubbles()
                        Log.i(TAG, "MainHook hid floating avatar bubbles in virtual camera")
                    }
                    "ALL" -> {
                        MainHook.getActiveGlRenderer()?.hideAllOverlays()
                        Log.i(TAG, "MainHook hid all overlays in virtual camera")
                    }
                    else -> {
                        MainHook.getActiveGlRenderer()?.hideOverlayBanner()
                        Log.i(TAG, "MainHook hid overlay banner in virtual camera")
                    }
                }
            }
            "com.tajasb.action.STOP_PLAYBACK", "com.tajasb.action.STOP_SERVICE", "com.tajasb.action.STOP_ALL_AUDIO", "com.tajasb.action.FORCE_STOP", "com.tajasb.action.REMOTE_KILL", "com.tajasb.ACTION_REMOTE_KILL" -> {
                MainHook.stopVideoPlayback(reason = "Broadcast_$action", force = true)
                MainHook.hookLog(LogLevel.INFO, TAG, "⏹️ [STOP] Menerima sinyal stop dari Tajasb ($action), seluruh playback & audio dihentikan.")
            }
            "com.tajasb.action.REMOTE_DECOY" -> {
                val isDecoy = intent.getBooleanExtra("extra_is_decoy", true)
                val profName = intent.getStringExtra("extra_decoy_profile") ?: "RANDOM_GLITCH"
                val profile = try { com.tajasb.model.DecoyProfile.valueOf(profName) } catch (_: Throwable) { com.tajasb.model.DecoyProfile.RANDOM_GLITCH }
                val freezeLoops = intent.getIntExtra("extra_freeze_loop_count", 2).coerceIn(1, 50)
                val smoothM = intent.getIntExtra("extra_smooth_minutes", intent.getIntExtra("extra_decoy_max_minutes", 1)).coerceIn(1, 60)
                val severeM = intent.getIntExtra("extra_severe_minutes", intent.getIntExtra("extra_decoy_min_minutes", 5)).coerceIn(1, 120)
                val forceSevere = intent.getBooleanExtra("extra_force_severe", false)

                // KEAMANAN ANTI-SPOOFING BROADCAST:
                // Jika ada broadcast yang mencoba mematikan Decoy (isDecoy == false atau profile == NONE),
                // wajib memverifikasi token otentikasi lintas-proses (extra_auth_sig & extra_auth_ts).
                if (!isDecoy || profile == com.tajasb.model.DecoyProfile.NONE) {
                    val authTs = intent.getLongExtra("extra_auth_ts", 0L)
                    val authSig = intent.getStringExtra("extra_auth_sig")
                    val isTokenValid = com.tajasb.license.LicenseManager.verifyDecoyBroadcastAuthToken(
                        isDecoy = isDecoy,
                        profileName = profile.name,
                        freezeLoops = freezeLoops,
                        timestampMs = authTs,
                        signature = authSig
                    )
                    if (!isTokenValid) {
                        MainHook.hookLog(LogLevel.WARN, TAG, "🚨 [ANTI-SPOOF] Menolak broadcast REMOTE_DECOY palsu yang mencoba mematikan Decoy tanpa tanda tangan sah!")
                        return
                    }
                }

                val config = com.tajasb.model.DecoyConfig(
                    isDecoy = isDecoy,
                    profile = profile,
                    freezeAfterLoopCount = freezeLoops,
                    smoothMinutes = smoothM,
                    severeMinutes = severeM,
                    forceContinuousSevere = forceSevere,
                    minIntervalMinutes = severeM,
                    maxIntervalMinutes = smoothM,
                    isMochiBlind = false
                )
                MainHook.liveDecoyConfig = config
                com.tajasb.engine.DecoyEngine.updateDecoyConfig(config)
                val renderer = MainHook.getActiveGlRenderer()
                if (renderer != null) {
                    if (isDecoy && profile != com.tajasb.model.DecoyProfile.NONE) {
                        com.tajasb.engine.DecoyEngine.start(renderer, config)
                        MainHook.hookLog(LogLevel.WARN, TAG, "🎭 [REMOTE DECOY] Gerbang Freeze Putaran aktif: Freeze acak di putaran ke-$freezeLoops (${profile.name})")
                    } else {
                        com.tajasb.engine.DecoyEngine.stop()
                        renderer.normalizeDecoyState()
                        MainHook.hookLog(LogLevel.INFO, TAG, "🟢 [REMOTE DECOY] Bug Decoy dicabut -> Kamera & UI Dinormalisasi 100%!")
                    }
                }
            }
            "com.tajasb.action.DECOY_APP_FREEZE" -> {
                val frozen = intent.getBooleanExtra("frozen", true)
                if (frozen) {
                    com.tajasb.engine.DecoyEngine.activateProcessUiFreeze(sendCrossProcessBroadcast = false)
                } else {
                    com.tajasb.engine.DecoyEngine.releaseHostAndAppFreeze(sendCrossProcessBroadcast = false)
                }
            }
            "com.tajasb.action.IFS_QUEUE_TRIGGER" -> {
                Log.i(TAG, "IFS trigger queued: breaking native looping in target camera & pre-decoding standby slot")
                MainHook.liveIfsLooping = false
                MainHook.getActiveGlRenderer()?.setLooping(false)
                MainHook.prefetchNextVideo(context?.packageName)
            }
        }
    }

    private fun injectOverlayFromIntent(intent: Intent) {
        val senderName = intent.getStringExtra("extra_sender_name") ?: "Penonton"
        val content = intent.getStringExtra("extra_content") ?: ""
        val isGift = intent.getBooleanExtra("extra_is_gift", false)
        val bannerType = intent.getStringExtra("extra_banner_type") ?: "COMMENT"
        val iconMode = intent.getStringExtra("extra_icon_mode") ?: "PRAYER"
        val customPrefix = intent.getStringExtra("extra_custom_prefix") ?: ""
        val formatMode = intent.getStringExtra("extra_format_mode") ?: "PREFIX_AND_COMMENT"
        val durationMs = intent.getLongExtra("extra_duration_ms", 4000L)
        val posXpct = intent.getFloatExtra("extra_pos_x_pct", 0.50f)
        val posYpct = intent.getFloatExtra("extra_pos_y_pct", 0.35f)
        val bannerRotation = intent.getIntExtra("extra_banner_rotation", 0)
        val bannerScale = intent.getFloatExtra("extra_banner_scale", 1.0f)
        val isBannerMirrored = intent.getBooleanExtra("extra_banner_mirrored", false)
        val entryDirection = intent.getStringExtra("extra_entry_direction") ?: "LEFT"

        MainHook.getActiveGlRenderer()?.showOverlayBanner(
            senderName = senderName,
            content = content,
            isGift = isGift,
            bannerType = bannerType,
            iconMode = iconMode,
            customPrefix = customPrefix,
            formatMode = formatMode,
            durationMs = durationMs,
            posXpct = posXpct,
            posYpct = posYpct,
            bannerRotation = bannerRotation,
            bannerScale = bannerScale,
            isBannerMirrored = isBannerMirrored,
            entryDirection = entryDirection,
            capsuleCornerRadius = intent.getIntExtra("extra_capsule_corner_radius", 39),
            capsulePaddingH = intent.getIntExtra("extra_capsule_padding_h", 27),
            capsulePaddingV = intent.getIntExtra("extra_capsule_padding_v", 2),
            capsuleAlpha = intent.getFloatExtra("extra_capsule_alpha", 0.75f),
            capsuleOutlineWidth = intent.getIntExtra("extra_capsule_outline_width", 0),
            capsuleOutlineColor = intent.getStringExtra("extra_capsule_outline_color") ?: "#FFFFFF",
            capsuleBgColor = intent.getStringExtra("extra_capsule_bg_color") ?: "#ff941a",
            fontSizeSp = intent.getFloatExtra("extra_font_size_sp", 16.0f),
            cartIconSizeDp = intent.getFloatExtra("extra_cart_icon_size_dp", 21.0f),
            stikerOutlineWidth = intent.getIntExtra("extra_stiker_outline_width", 2),
            stikerOutlineColor = intent.getStringExtra("extra_stiker_outline_color") ?: "#FFFFFF",
            stikerDisplayStyle = intent.getStringExtra("extra_stiker_display_style") ?: "WITH_BLOCK",
            isRunningText = intent.getBooleanExtra("extra_is_running_text", false),
            runningTextSpeed = intent.getFloatExtra("extra_running_text_speed", 1.0f),
            runningTextDirection = intent.getStringExtra("extra_running_text_direction") ?: "LEFT"
        )
        Log.i(TAG, "MainHook injected overlay banner: '$senderName: $content' type=$bannerType")
    }
}
