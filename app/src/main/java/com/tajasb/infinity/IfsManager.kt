package com.tajasb.infinity

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tajasb.audio.StandaloneMusicPlayer
import com.tajasb.data.VcamRepository
import com.tajasb.engine.SmartVideoStitcher
import com.tajasb.model.AudioItem
import com.tajasb.model.VideoItem
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * IfsManager (Production Edition):
 * Pengelola utama Infinity System (IFS) yang menggantikan Mode Bridge lama sepenuhnya:
 * 1. System Video SV1: VTM (vtNumber=0, 3 Video A+B+A) + VT1, VT2, VT3..n (vtNumber=1..n, 1 Video Konten)
 * 2. System Video SV2: Tanpa VTM — Semua VT1, VT2, VT3..n (vtNumber=1..n, Wajib 3 Video A+B+A)
 * 3. System Video SV3: 1 Video VL (Loop Acak Min..Max) ↔ Grup VB Acak
 * 4. System Video SV4 (Mode VS): Variasi Susunan L1..Ln + Loop Dinamis 1..2x Rolling JIT + Auto-Delete
 * 5. System Audio X (AX + SR): Pemilihan lagu AX per VTM/VT & penandaan lagu SR di Tab Musik,
 *    dengan Pause SR detik terakhir -> Play AX 100% -> Resume SR dari detik terakhir.
 * 6. System Scan Komentar (SC) dengan antrean FIFO.
 */
class IfsManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "IfsManager"
        private const val PREFS_NAME = "ifs_settings_v2"
        private const val KEY_IFS_ENABLED = "key_ifs_enabled"
        private const val KEY_IFS_VIDEO_MODE = "key_ifs_video_mode"
        private const val KEY_IFS_VTM_JSON = "key_ifs_vtm_json"
        private const val KEY_IFS_PACKAGES_JSON = "key_ifs_packages_json"
        private const val KEY_IFS_SR_AUDIO_IDS = "key_ifs_sr_audio_ids"
        private const val KEY_IFS_SC_ENABLED = "key_ifs_sc_enabled"
        private const val KEY_IFS_SV3_VL_VIDEO_ID = "key_ifs_sv3_vl_video_id"
        private const val KEY_IFS_SV3_VL_MIN_LOOPS = "key_ifs_sv3_vl_min_loops"
        private const val KEY_IFS_SV3_VL_MAX_LOOPS = "key_ifs_sv3_vl_max_loops"
        private const val KEY_IFS_SV3_VB_VIDEO_IDS_JSON = "key_ifs_sv3_vb_video_ids_json"
        private const val KEY_IFS_SV4_L_VIDEO_IDS_JSON = "key_ifs_sv4_l_video_ids_json"
        private const val KEY_IFS_SV4_MIN_LOOPS = "key_ifs_sv4_min_loops"
        private const val KEY_IFS_SV4_MAX_LOOPS = "key_ifs_sv4_max_loops"
        private const val KEY_IFS_SV4_VS_COUNTER = "key_ifs_sv4_vs_counter"
        private const val KEY_IFS_SV4_AUTO_DELETED_COUNT = "key_ifs_sv4_auto_deleted_count"
        private const val KEY_IFS_SV4_LAST_FORMULA = "key_ifs_sv4_last_formula"
        private const val KEY_IFS_SV4_CONFIG_SIG = "key_ifs_sv4_config_sig"
        private const val KEY_IFS_SV4_CURRENT_RECIPE_JSON = "key_ifs_sv4_current_recipe_json"
        private const val KEY_IFS_SV4_CURRENT_VIDEO_JSON = "key_ifs_sv4_current_video_json"
        private const val KEY_IFS_SV4_NEXT_RECIPE_JSON = "key_ifs_sv4_next_recipe_json"
        private const val KEY_IFS_SV4_NEXT_VIDEO_JSON = "key_ifs_sv4_next_video_json"
        private const val KEY_IFS_SV4_USED_FORMULAS_JSON = "key_ifs_sv4_used_formulas_json"
        private const val KEY_IFS_SV4_VIDEO_RUNNING = "key_ifs_sv4_video_running"
        // Konstanta Persistensi SV4 Audio (Mode VS Audio — VSA1, VSA2..n)
        private const val KEY_IFS_AUDIO_MODE = "key_ifs_audio_mode"
        private const val KEY_IFS_SV4_AUDIO_RUNNING = "key_ifs_sv4_audio_running"
        private const val KEY_IFS_SV4_AUDIO_L_IDS_JSON = "key_ifs_sv4_audio_l_ids_json"
        private const val KEY_IFS_SV4_AUDIO_MIN_LOOPS = "key_ifs_sv4_audio_min_loops"
        private const val KEY_IFS_SV4_AUDIO_MAX_LOOPS = "key_ifs_sv4_audio_max_loops"
        private const val KEY_IFS_SV4_AUDIO_VS_COUNTER = "key_ifs_sv4_audio_vs_counter"
        private const val KEY_IFS_SV4_AUDIO_AUTO_DELETED_COUNT = "key_ifs_sv4_audio_auto_deleted_count"
        private const val KEY_IFS_SV4_AUDIO_LAST_FORMULA = "key_ifs_sv4_audio_last_formula"
        private const val KEY_IFS_SV4_AUDIO_CONFIG_SIG = "key_ifs_sv4_audio_config_sig"
        private const val KEY_IFS_SV4_AUDIO_CURRENT_RECIPE_JSON = "key_ifs_sv4_audio_current_recipe_json"
        private const val KEY_IFS_SV4_AUDIO_CURRENT_ITEM_JSON = "key_ifs_sv4_audio_current_item_json"
        private const val KEY_IFS_SV4_AUDIO_NEXT_RECIPE_JSON = "key_ifs_sv4_audio_next_recipe_json"
        private const val KEY_IFS_SV4_AUDIO_NEXT_ITEM_JSON = "key_ifs_sv4_audio_next_item_json"
        private const val KEY_IFS_SV4_AUDIO_USED_FORMULAS_JSON = "key_ifs_sv4_audio_used_formulas_json"
        // Jendela Guard 2 Menit (120 detik) untuk pencegahan kembar koreografi, suara, dan anti-deteksi di SV4
        const val SV4_GUARD_WINDOW_MS = 120_000L

        @Volatile
        private var INSTANCE: IfsManager? = null

        fun getInstance(context: Context): IfsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IfsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val sv4Scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val musicPlayer = StandaloneMusicPlayer.getInstance(appContext)

    private val _isIfsActive = MutableStateFlow(prefs.getBoolean(KEY_IFS_ENABLED, false))
    val isIfsActive: StateFlow<Boolean> = _isIfsActive.asStateFlow()

    // Saklar ON/OFF Scan Komentar (SC) — default OFF karena masih tahap pengembangan & ada Host Live sementara
    private val _isScEnabled = MutableStateFlow(prefs.getBoolean(KEY_IFS_SC_ENABLED, false))
    val isScEnabled: StateFlow<Boolean> = _isScEnabled.asStateFlow()

    private val _videoMode = MutableStateFlow(loadSavedMode())
    val videoMode: StateFlow<IfsVideoMode> = _videoMode.asStateFlow()

    // Paket Master VTM khusus untuk Mode SV1 (vtNumber = 0)
    private val _vtmPackage = MutableStateFlow(loadSavedVtmPackage())
    val vtmPackage: StateFlow<IfsVtPackage> = _vtmPackage.asStateFlow()

    // Daftar Paket VT1, VT2, VT3..n (vtNumber selalu urut 1, 2, 3..n tanpa bolong)
    private val _vtPackages = MutableStateFlow(loadSavedVtPackages())
    val vtPackages: StateFlow<List<IfsVtPackage>> = _vtPackages.asStateFlow()

    // Konfigurasi Khusus Mode SV3: 1 Video VL (Loop Acak Min..Max) & Grup VB (VB1, VB2, dst. Acak Non-Repeat)
    private val _sv3VlVideoId = MutableStateFlow(loadSavedSv3VlVideoId())
    val sv3VlVideoId: StateFlow<String?> = _sv3VlVideoId.asStateFlow()

    private val _sv3VlMinLoops = MutableStateFlow(prefs.getInt(KEY_IFS_SV3_VL_MIN_LOOPS, 2).coerceIn(1, 50))
    val sv3VlMinLoops: StateFlow<Int> = _sv3VlMinLoops.asStateFlow()

    private val _sv3VlMaxLoops = MutableStateFlow(
        prefs.getInt(KEY_IFS_SV3_VL_MAX_LOOPS, 5).coerceIn(_sv3VlMinLoops.value, 50)
    )
    val sv3VlMaxLoops: StateFlow<Int> = _sv3VlMaxLoops.asStateFlow()

    private val _sv3VbVideoIds = MutableStateFlow(loadSavedSv3VbVideoIds())
    val sv3VbVideoIds: StateFlow<List<String>> = _sv3VbVideoIds.asStateFlow()

    // Dua Mesin Shuffle Deck Non-Repeating khusus SV3 (sama seperti logika acak di Mode Standar)
    private val sv3VlLoopCountDeck = com.tajasb.data.VideoPlaylistRepository.ShuffleDeckManager()
    private val sv3VbGroupDeck = com.tajasb.data.VideoPlaylistRepository.ShuffleDeckManager()
    @Volatile private var lastPickedSv3VlLoops: Int = -1
    @Volatile private var lastPickedSv3VbIdx: Int = -1

    // Konfigurasi Khusus Mode SV4 (Mode VS — Variasi Susunan L1, L2, L3, L4..n + Loop Dinamis 1-2x)
    private val _sv4LVideoIds = MutableStateFlow(loadSavedSv4LVideoIds())
    val sv4LVideoIds: StateFlow<List<String>> = _sv4LVideoIds.asStateFlow()

    private val _sv4MinLoops = MutableStateFlow(prefs.getInt(KEY_IFS_SV4_MIN_LOOPS, 1).coerceIn(1, 10))
    val sv4MinLoops: StateFlow<Int> = _sv4MinLoops.asStateFlow()

    private val _sv4MaxLoops = MutableStateFlow(
        prefs.getInt(KEY_IFS_SV4_MAX_LOOPS, 2).coerceIn(_sv4MinLoops.value, 10)
    )
    val sv4MaxLoops: StateFlow<Int> = _sv4MaxLoops.asStateFlow()

    data class VsSegmentSpec(
        val lNumber: Int,
        val videoId: String,
        val loopCount: Int
    )

    data class VsVariationRecipe(
        val vsNumber: Int,
        val segments: List<VsSegmentSpec>,
        val formulaText: String
    )

    data class Sv4ChoreographyRecord(
        val vsNumber: Int,
        val formula: String,
        val signature: String,
        val firstVideoId: String,
        val lastVideoId: String,
        val timestampMs: Long
    )

    data class Sv4AudioChoreographyRecord(
        val vsNumber: Int,
        val formula: String,
        val signature: String,
        val firstAudioId: String,
        val lastAudioId: String,
        val timestampMs: Long
    )

    private val sv4ChoreographyHistory = java.util.Collections.synchronizedList(mutableListOf<Sv4ChoreographyRecord>())
    private val sv4AudioChoreographyHistory = java.util.Collections.synchronizedList(mutableListOf<Sv4AudioChoreographyRecord>())

    fun getSv4ChoreographyHistoryInLast2m(): List<Sv4ChoreographyRecord> {
        val now = System.currentTimeMillis()
        synchronized(sv4ChoreographyHistory) {
            sv4ChoreographyHistory.removeAll { (now - it.timestampMs) >= SV4_GUARD_WINDOW_MS }
            return sv4ChoreographyHistory.toList()
        }
    }

    fun getSv4AudioChoreographyHistoryInLast2m(): List<Sv4AudioChoreographyRecord> {
        val now = System.currentTimeMillis()
        synchronized(sv4AudioChoreographyHistory) {
            sv4AudioChoreographyHistory.removeAll { (now - it.timestampMs) >= SV4_GUARD_WINDOW_MS }
            return sv4AudioChoreographyHistory.toList()
        }
    }

    fun clearSv4GuardHistories() {
        synchronized(sv4ChoreographyHistory) { sv4ChoreographyHistory.clear() }
        synchronized(sv4AudioChoreographyHistory) { sv4AudioChoreographyHistory.clear() }
    }

    private val _sv4VideoRunning = MutableStateFlow(prefs.getBoolean(KEY_IFS_SV4_VIDEO_RUNNING, false))
    val sv4VideoRunning: StateFlow<Boolean> = _sv4VideoRunning.asStateFlow()

    @Volatile private var sv4VsCounter: Int = prefs.getInt(KEY_IFS_SV4_VS_COUNTER, 0).coerceAtLeast(0)
    @Volatile private var sv4CurrentRecipe: VsVariationRecipe? = null
    @Volatile private var sv4CurrentVideoItem: VideoItem? = null
    @Volatile private var sv4CurrentFile: File? = null
    @Volatile private var sv4NextRecipe: VsVariationRecipe? = null
    @Volatile private var sv4NextVideoItem: VideoItem? = null
    @Volatile private var sv4NextFile: File? = null
    @Volatile private var sv4StitchJob: kotlinx.coroutines.Job? = null
    @Volatile private var sv4PlaybackWatchdogJob: kotlinx.coroutines.Job? = null
    @Volatile private var sv4WatchdogPlayedMs: Long = 0L
    @Volatile private var sv4IsInitialWarmupActive: Boolean = false
    @Volatile private var sv4WaitingForNextReady: Boolean = false
    @Volatile private var sv4LastPromotedTimeMs: Long = 0L
    @Volatile private var sv4AutoDeletedFilesCount: Int = prefs.getInt(KEY_IFS_SV4_AUTO_DELETED_COUNT, 0).coerceAtLeast(0)
    @Volatile private var lastSv4FormulaSignature: String = prefs.getString(KEY_IFS_SV4_LAST_FORMULA, "") ?: ""
    @Volatile private var sv4ConfigSignature: String = prefs.getString(KEY_IFS_SV4_CONFIG_SIG, "") ?: ""
    private val sv4UsedFormulas = java.util.Collections.synchronizedSet(java.util.LinkedHashSet<String>())
    private val manuallyRemovedSv4LIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Konfigurasi Khusus Mode SV4 Audio (Mode VS Audio — Variasi Susunan LA1, LA2, LA3..n + Loop Dinamis 1-2x)
    private val _audioMode = MutableStateFlow(loadSavedAudioMode())
    val audioMode: StateFlow<IfsAudioMode> = _audioMode.asStateFlow()

    private val _sv4AudioRunning = MutableStateFlow(prefs.getBoolean(KEY_IFS_SV4_AUDIO_RUNNING, false))
    val sv4AudioRunning: StateFlow<Boolean> = _sv4AudioRunning.asStateFlow()

    private val _sv4AudioLIds = MutableStateFlow(loadSavedSv4AudioLIds())
    val sv4AudioLIds: StateFlow<List<String>> = _sv4AudioLIds.asStateFlow()

    private val _sv4AudioMinLoops = MutableStateFlow(prefs.getInt(KEY_IFS_SV4_AUDIO_MIN_LOOPS, 1).coerceIn(1, 10))
    val sv4AudioMinLoops: StateFlow<Int> = _sv4AudioMinLoops.asStateFlow()

    private val _sv4AudioMaxLoops = MutableStateFlow(
        prefs.getInt(KEY_IFS_SV4_AUDIO_MAX_LOOPS, 2).coerceIn(_sv4AudioMinLoops.value, 10)
    )
    val sv4AudioMaxLoops: StateFlow<Int> = _sv4AudioMaxLoops.asStateFlow()

    @Volatile private var sv4AudioVsCounter: Int = prefs.getInt(KEY_IFS_SV4_AUDIO_VS_COUNTER, 0).coerceAtLeast(0)
    @Volatile private var sv4AudioCurrentRecipe: VsVariationRecipe? = null
    @Volatile private var sv4AudioCurrentItem: AudioItem? = null
    @Volatile private var sv4AudioCurrentFile: File? = null
    @Volatile private var sv4AudioNextRecipe: VsVariationRecipe? = null
    @Volatile private var sv4AudioNextItem: AudioItem? = null
    @Volatile private var sv4AudioNextFile: File? = null
    @Volatile private var sv4AudioStitchJob: kotlinx.coroutines.Job? = null
    @Volatile private var sv4AudioWatchdogJob: kotlinx.coroutines.Job? = null
    @Volatile private var sv4AudioWatchdogPlayedMs: Long = 0L
    @Volatile private var sv4AudioIsInitialWarmupActive: Boolean = false
    @Volatile private var sv4AudioWaitingForNextReady: Boolean = false
    @Volatile private var sv4AudioLastPromotedTimeMs: Long = 0L
    @Volatile private var sv4AudioAutoDeletedFilesCount: Int = prefs.getInt(KEY_IFS_SV4_AUDIO_AUTO_DELETED_COUNT, 0).coerceAtLeast(0)
    @Volatile private var lastSv4AudioFormulaSignature: String = prefs.getString(KEY_IFS_SV4_AUDIO_LAST_FORMULA, "") ?: ""
    @Volatile private var sv4AudioConfigSignature: String = prefs.getString(KEY_IFS_SV4_AUDIO_CONFIG_SIG, "") ?: ""
    private val sv4AudioUsedFormulas = java.util.Collections.synchronizedSet(java.util.LinkedHashSet<String>())
    private val manuallyRemovedSv4AudioLIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var lastAudioAdvanceTimeMs: Long = 0L

    // Himpunan ID Audio yang ditandai sebagai SR (Musik Latar Sistem Random)
    private val _srAudioIds = MutableStateFlow(loadSavedSrAudioIds())
    val srAudioIds: StateFlow<Set<String>> = _srAudioIds.asStateFlow()

    private val _ifsState = MutableStateFlow(
        IfsState(
            isEnabled = _isIfsActive.value,
            isScEnabled = _isScEnabled.value,
            videoMode = _videoMode.value,
            activeVtNumber = when (_videoMode.value) {
                IfsVideoMode.SV4 -> sv4VsCounter.coerceAtLeast(1)
                IfsVideoMode.SV2 -> 1
                else -> 0
            },
            activeSubPhase = when (_videoMode.value) {
                IfsVideoMode.SV3 -> IfsSubPhase.VL
                IfsVideoMode.SV4 -> IfsSubPhase.VS
                else -> IfsSubPhase.ABB
            },
            statusMessage = "IFS Standby",
            sv3CurrentVlLoop = 1,
            sv3TargetVlLoops = _sv3VlMinLoops.value,
            sv3ActiveVbNumber = 1,
            sv3VbRemainingInCycle = _sv3VbVideoIds.value.size,
            sv3VbTotalCount = _sv3VbVideoIds.value.size,
            sv4VideoRunning = _sv4VideoRunning.value,
            sv4CurrentVsNumber = sv4VsCounter.coerceAtLeast(1),
            sv4NextVsNumber = (sv4VsCounter.coerceAtLeast(1)) + 1,
            sv4AutoDeletedCount = sv4AutoDeletedFilesCount,
            sv4TotalLCount = _sv4LVideoIds.value.size,
            sv4MinLoops = _sv4MinLoops.value,
            sv4MaxLoops = _sv4MaxLoops.value,
            audioMode = _audioMode.value,
            sv4AudioRunning = _sv4AudioRunning.value,
            sv4AudioCurrentVsNumber = sv4AudioVsCounter.coerceAtLeast(1),
            sv4AudioNextVsNumber = (sv4AudioVsCounter.coerceAtLeast(1)) + 1,
            sv4AudioAutoDeletedCount = sv4AudioAutoDeletedFilesCount,
            sv4AudioTotalLCount = _sv4AudioLIds.value.size,
            sv4AudioMinLoops = _sv4AudioMinLoops.value,
            sv4AudioMaxLoops = _sv4AudioMaxLoops.value
        )
    )
    val ifsState: StateFlow<IfsState> = _ifsState.asStateFlow()

    @Volatile
    private var lastAdvanceTimeMs: Long = 0L

    init {
        loadSavedSv4RollingState()
        loadSavedSv4AudioRollingState()

        // Konfigurasikan filter SR pada StandaloneMusicPlayer agar lagu AX & file VSA tidak ikut terputar acak di SR
        musicPlayer.setSrTrackFilter { audioItem ->
            !audioItem.id.startsWith("vs_audio_rolling_") && isTrackInSr(audioItem.id)
        }
        applyAudioModeToMusicPlayer(_audioMode.value)
        musicPlayer.setSv4AudioCompletionCallback {
            if (_audioMode.value == IfsAudioMode.SV4_VS) {
                onSv4AudioCompleted(forceAdvance = false) != null
            } else {
                false
            }
        }
        musicPlayer.setOnAudioSeekedCallback { posMs ->
            onAudioSeeked(posMs)
        }

        scope.launch {
            musicPlayer.pausedSrInfo.collect { info ->
                _ifsState.value = _ifsState.value.copy(
                    pausedSrTrackName = info?.first,
                    pausedSrPositionMs = info?.second ?: 0L
                )
            }
        }
        scope.launch {
            musicPlayer.isPlayingTriggeredAx.collect { isAx ->
                _ifsState.value = _ifsState.value.copy(
                    isPlayingTriggeredAx = isAx,
                    isSrPlaying = musicPlayer.isPlaying.value && !isAx
                )
            }
        }
        scope.launch {
            musicPlayer.isPlaying.collect { playing ->
                _ifsState.value = _ifsState.value.copy(
                    isSrPlaying = playing && !musicPlayer.isPlayingTriggeredAx.value
                )
            }
        }
        scope.launch {
            musicPlayer.currentTrack.collect { track ->
                _ifsState.value = _ifsState.value.copy(
                    currentAudioTrackName = track?.name
                )
            }
        }
        scope.launch {
            musicPlayer.activeTriggerAxNumber.collect { axNum ->
                _ifsState.value = _ifsState.value.copy(activeTriggerAxNumber = axNum)
            }
        }
        scope.launch {
            musicPlayer.activeTriggerAxLabel.collect { axLbl ->
                _ifsState.value = _ifsState.value.copy(activeTriggerAxLabel = axLbl)
            }
        }

        if (_isIfsActive.value && _videoMode.value == IfsVideoMode.SV4 && _sv4VideoRunning.value) {
            scope.launch {
                kotlinx.coroutines.delay(160L)
                if (_isIfsActive.value && _videoMode.value == IfsVideoMode.SV4 && _sv4VideoRunning.value) {
                    startSv4RollingCycle(resetCounter = false)
                }
            }
        }

        if (_audioMode.value == IfsAudioMode.SV4_VS && _sv4AudioRunning.value) {
            scope.launch {
                kotlinx.coroutines.delay(220L)
                if (_audioMode.value == IfsAudioMode.SV4_VS && _sv4AudioRunning.value) {
                    startSv4AudioRollingCycle(resetCounter = false)
                }
            }
        }
    }

    private fun loadSavedAudioMode(): IfsAudioMode {
        val raw = prefs.getString(KEY_IFS_AUDIO_MODE, IfsAudioMode.SR_AX.name)
        return try {
            IfsAudioMode.valueOf(raw ?: IfsAudioMode.SR_AX.name)
        } catch (_: Throwable) {
            IfsAudioMode.SR_AX
        }
    }

    private fun loadSavedSv4AudioLIds(): List<String> {
        val raw = prefs.getString(KEY_IFS_SV4_AUDIO_L_IDS_JSON, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val id = arr.optString(i, "")
                if (id.isNotBlank() && id != "null" && !id.startsWith("vs_audio_rolling_") && !list.contains(id)) {
                    list.add(id)
                }
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun loadSavedMode(): IfsVideoMode {
        val raw = prefs.getString(KEY_IFS_VIDEO_MODE, IfsVideoMode.SV1.name)
        return try {
            IfsVideoMode.valueOf(raw ?: IfsVideoMode.SV1.name)
        } catch (_: Throwable) {
            IfsVideoMode.SV1
        }
    }

    private fun loadSavedSv3VlVideoId(): String? {
        return prefs.getString(KEY_IFS_SV3_VL_VIDEO_ID, null)?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun loadSavedSv3VbVideoIds(): List<String> {
        val raw = prefs.getString(KEY_IFS_SV3_VB_VIDEO_IDS_JSON, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val id = arr.optString(i, "")
                if (id.isNotBlank() && id != "null" && !list.contains(id)) {
                    list.add(id)
                }
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun loadSavedSv4LVideoIds(): List<String> {
        val raw = prefs.getString(KEY_IFS_SV4_L_VIDEO_IDS_JSON, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val id = arr.optString(i, "")
                if (id.isNotBlank() && id != "null" && !list.contains(id)) {
                    list.add(id)
                }
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun drawNextSv3VlLoopCount(): Int {
        val minL = _sv3VlMinLoops.value.coerceIn(1, 50)
        val maxL = _sv3VlMaxLoops.value.coerceIn(minL, 50)
        val candidates = (minL..maxL).toList()
        val picked = sv3VlLoopCountDeck.getNextShuffledIndex(candidates, lastPickedSv3VlLoops)
        lastPickedSv3VlLoops = picked
        return picked
    }

    private fun drawNextSv3VbIndex(): Int {
        val vbList = _sv3VbVideoIds.value
        if (vbList.isEmpty()) return 0
        val candidates = vbList.indices.toList()
        val pickedIdx = sv3VbGroupDeck.getNextShuffledIndex(candidates, lastPickedSv3VbIdx)
        lastPickedSv3VbIdx = pickedIdx
        return pickedIdx
    }

    private fun getRemainingSv3VbInCycle(): Int {
        val vbList = _sv3VbVideoIds.value
        if (vbList.isEmpty()) return 0
        val rem = sv3VbGroupDeck.remainingInCurrentCycle(vbList.indices.toList())
        return if (rem == 0 && lastPickedSv3VbIdx == -1) vbList.size else rem
    }

    private fun loadSavedVtmPackage(): IfsVtPackage {
        val rawJson = prefs.getString(KEY_IFS_VTM_JSON, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(rawJson)
                return parsePackageJson(obj, 0)
            } catch (e: Throwable) {
                Log.w(TAG, "Gagal memuat JSON ABA: ${e.message}")
            }
        }
        return IfsVtPackage(
            vtNumber = 0,
            title = "ABA (ABB + BL + BBT)",
            triggerKeyword = "aba, master, standby",
            linkedAxNumber = 0
        )
    }

    private fun loadSavedVtPackages(): List<IfsVtPackage> {
        val rawJson = prefs.getString(KEY_IFS_PACKAGES_JSON, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(rawJson)
                val list = mutableListOf<IfsVtPackage>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pkg = parsePackageJson(obj, i + 1)
                    if (pkg.vtNumber > 0) {
                        list.add(pkg)
                    }
                }
                if (list.isNotEmpty()) {
                    return renumberVtList(list)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Gagal memuat JSON paket VT: ${e.message}")
            }
        }
        return createDefaultVtPackages()
    }

    private fun parsePackageJson(obj: JSONObject, fallbackNum: Int): IfsVtPackage {
        val vtNum = obj.optInt("vtNumber", fallbackNum)
        return IfsVtPackage(
            vtNumber = vtNum,
            title = if (vtNum == 0) "ABA (ABB + BL + BBT)" else "VT$vtNum",
            abbVideoId = obj.optString("abbVideoId", "").takeIf { it.isNotBlank() && it != "null" },
            blVideoId = obj.optString("blVideoId", "").takeIf { it.isNotBlank() && it != "null" },
            bbtVideoId = obj.optString("bbtVideoId", "").takeIf { it.isNotBlank() && it != "null" },
            triggerKeyword = obj.optString("triggerKeyword", ""),
            linkedAudioId = obj.optString("linkedAudioId", "").takeIf { it.isNotBlank() && it != "null" },
            linkedAxNumber = vtNum
        )
    }

    private fun createDefaultVtPackages(): List<IfsVtPackage> {
        return listOf(
            IfsVtPackage(
                vtNumber = 1,
                title = "VT1",
                triggerKeyword = "1, vt1, spill 1, etalase 1",
                linkedAxNumber = 1
            ),
            IfsVtPackage(
                vtNumber = 2,
                title = "VT2",
                triggerKeyword = "2, vt2, spill 2, etalase 2",
                linkedAxNumber = 2
            ),
            IfsVtPackage(
                vtNumber = 3,
                title = "VT3",
                triggerKeyword = "3, vt3, spill 3, etalase 3",
                linkedAxNumber = 3
            )
        )
    }

    private fun renumberVtList(list: List<IfsVtPackage>): List<IfsVtPackage> {
        return list.mapIndexed { index, item ->
            val num = index + 1
            item.copy(
                vtNumber = num,
                title = "VT$num",
                linkedAxNumber = num
            )
        }
    }

    private fun loadSavedSrAudioIds(): Set<String> {
        return prefs.getStringSet(KEY_IFS_SR_AUDIO_IDS, emptySet())?.toSet() ?: emptySet()
    }

    private fun recipeToJson(recipe: VsVariationRecipe): JSONObject {
        return JSONObject().apply {
            put("vsNumber", recipe.vsNumber)
            put("formulaText", recipe.formulaText)
            val segArr = JSONArray()
            for (seg in recipe.segments) {
                segArr.put(
                    JSONObject().apply {
                        put("lNumber", seg.lNumber)
                        put("videoId", seg.videoId)
                        put("loopCount", seg.loopCount)
                    }
                )
            }
            put("segments", segArr)
        }
    }

    private fun parseRecipeJson(raw: String?): VsVariationRecipe? {
        if (raw.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(raw)
            parseRecipeJsonObject(obj)
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseRecipeJsonObject(obj: JSONObject?): VsVariationRecipe? {
        if (obj == null) return null
        return try {
            val vsNum = obj.optInt("vsNumber", 0)
            if (vsNum <= 0) return null
            val formulaText = obj.optString("formulaText", "")
            val segArr = obj.optJSONArray("segments")
            val segments = mutableListOf<VsSegmentSpec>()
            if (segArr != null) {
                for (i in 0 until segArr.length()) {
                    val sObj = segArr.optJSONObject(i) ?: continue
                    val lNum = sObj.optInt("lNumber", i + 1)
                    val vidId = sObj.optString("videoId", "")
                    val loops = sObj.optInt("loopCount", 1).coerceIn(1, 10)
                    if (vidId.isNotBlank()) {
                        segments.add(VsSegmentSpec(lNumber = lNum, videoId = vidId, loopCount = loops))
                    }
                }
            }
            if (segments.isEmpty() && formulaText.isBlank()) return null
            VsVariationRecipe(
                vsNumber = vsNum,
                segments = segments,
                formulaText = formulaText.ifBlank {
                    segments.joinToString(" + ") { "L${it.lNumber}(${it.loopCount}x)" }
                }
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun rollingVideoItemToJson(item: VideoItem): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("name", item.name)
            put("path", item.path)
            put("uri", item.uri ?: "")
            put("durationMs", item.durationMs)
            put("sizeBytes", item.sizeBytes)
            put("width", item.width)
            put("height", item.height)
            put("isMuted", item.isMuted)
        }
    }

    private fun parseRollingVideoItemJson(raw: String?): VideoItem? {
        if (raw.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(raw)
            val id = obj.optString("id", "")
            val path = obj.optString("path", "")
            if (id.isBlank() || path.isBlank()) return null
            val f = File(path)
            if (!f.exists() || f.length() <= 0L) return null
            VideoItem(
                id = id,
                name = obj.optString("name", id),
                path = f.absolutePath,
                uri = obj.optString("uri", "").ifBlank { android.net.Uri.fromFile(f).toString() },
                durationMs = obj.optLong("durationMs", 4000L),
                sizeBytes = f.length(),
                width = obj.optInt("width", 720),
                height = obj.optInt("height", 1280),
                isMuted = obj.optBoolean("isMuted", false),
                isEnabled = true
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Menyimpan state progres SV4 Mode VS (nomor VS saat ini, resep aktif, resep berikutnya,
     * file MP4 gabungan di disk, serta riwayat formula variasi yang sudah pernah tayang)
     * ke SharedPreferences agar keluar APK atau update APK di VS berapa pun (mis. VS1000)
     * tetap melanjutkan dari posisi tersebut tanpa reset ke VS1/VS2.
     */
    private fun saveSv4RollingStateToPrefs() {
        try {
            val usedArr = JSONArray()
            synchronized(sv4UsedFormulas) {
                for (f in sv4UsedFormulas) {
                    if (f.isNotBlank()) usedArr.put(f)
                }
            }
            prefs.edit()
                .putInt(KEY_IFS_SV4_VS_COUNTER, sv4VsCounter)
                .putInt(KEY_IFS_SV4_AUTO_DELETED_COUNT, sv4AutoDeletedFilesCount)
                .putString(KEY_IFS_SV4_LAST_FORMULA, lastSv4FormulaSignature)
                .putString(KEY_IFS_SV4_CONFIG_SIG, sv4ConfigSignature)
                .putString(KEY_IFS_SV4_CURRENT_RECIPE_JSON, sv4CurrentRecipe?.let { recipeToJson(it).toString() } ?: "")
                .putString(KEY_IFS_SV4_CURRENT_VIDEO_JSON, sv4CurrentVideoItem?.let { rollingVideoItemToJson(it).toString() } ?: "")
                .putString(KEY_IFS_SV4_NEXT_RECIPE_JSON, sv4NextRecipe?.let { recipeToJson(it).toString() } ?: "")
                .putString(KEY_IFS_SV4_NEXT_VIDEO_JSON, sv4NextVideoItem?.let { rollingVideoItemToJson(it).toString() } ?: "")
                .putString(KEY_IFS_SV4_USED_FORMULAS_JSON, usedArr.toString())
                .apply()
        } catch (e: Throwable) {
            Log.w(TAG, "Gagal menyimpan state rolling SV4: ${e.message}")
        }
    }

    /**
     * Memuat kembali state progres SV4 Mode VS dari SharedPreferences (termasuk fallback pemulihan
     * otomatis dari playlist_json / folder Camera1 saat baru saja update APK).
     */
    private fun loadSavedSv4RollingState() {
        try {
            sv4VsCounter = prefs.getInt(KEY_IFS_SV4_VS_COUNTER, 0).coerceAtLeast(0)
            sv4AutoDeletedFilesCount = prefs.getInt(KEY_IFS_SV4_AUTO_DELETED_COUNT, 0).coerceAtLeast(0)
            lastSv4FormulaSignature = prefs.getString(KEY_IFS_SV4_LAST_FORMULA, "") ?: ""
            sv4ConfigSignature = prefs.getString(KEY_IFS_SV4_CONFIG_SIG, "") ?: ""

            val usedRaw = prefs.getString(KEY_IFS_SV4_USED_FORMULAS_JSON, null)
            if (!usedRaw.isNullOrBlank()) {
                val arr = JSONArray(usedRaw)
                synchronized(sv4UsedFormulas) {
                    sv4UsedFormulas.clear()
                    for (i in 0 until arr.length()) {
                        val f = arr.optString(i, "")
                        if (f.isNotBlank()) sv4UsedFormulas.add(f)
                    }
                }
            }

            sv4CurrentRecipe = parseRecipeJson(prefs.getString(KEY_IFS_SV4_CURRENT_RECIPE_JSON, null))
            sv4CurrentVideoItem = parseRollingVideoItemJson(prefs.getString(KEY_IFS_SV4_CURRENT_VIDEO_JSON, null))
            sv4CurrentFile = sv4CurrentVideoItem?.let { File(it.path).takeIf { f -> f.exists() && f.length() > 0L } }

            sv4NextRecipe = parseRecipeJson(prefs.getString(KEY_IFS_SV4_NEXT_RECIPE_JSON, null))
            sv4NextVideoItem = parseRollingVideoItemJson(prefs.getString(KEY_IFS_SV4_NEXT_VIDEO_JSON, null))
            sv4NextFile = sv4NextVideoItem?.let { File(it.path).takeIf { f -> f.exists() && f.length() > 0L } }

            // Fallback Pemulihan saat pengguna baru saja Update APK dari versi sebelumnya:
            // Periksa playlist_json di vcam_settings serta file vs_rolling_<N>.mp4 di folder Camera1
            if (sv4VsCounter <= 0 || sv4CurrentRecipe == null) {
                recoverSv4StateFromExistingPlaylistOrDisk()
            }

            val minL = _sv4MinLoops.value.coerceAtLeast(1)
            val maxL = _sv4MaxLoops.value.coerceAtLeast(minL)
            if (sv4CurrentRecipe != null && (
                    sv4CurrentRecipe!!.segments.any { it.loopCount !in minL..maxL } ||
                        (minL == 1 && maxL == 1 && _sv4LVideoIds.value.isNotEmpty() && sv4CurrentRecipe!!.segments.size > _sv4LVideoIds.value.size)
                    )
            ) {
                sv4CurrentRecipe = null
                sv4CurrentVideoItem = null
                sv4CurrentFile = null
            }
            if (sv4NextRecipe != null && (
                    sv4NextRecipe!!.segments.any { it.loopCount !in minL..maxL } ||
                        (minL == 1 && maxL == 1 && _sv4LVideoIds.value.isNotEmpty() && sv4NextRecipe!!.segments.size > _sv4LVideoIds.value.size)
                    )
            ) {
                sv4NextRecipe = null
                sv4NextVideoItem = null
                sv4NextFile = null
            }

            val curNum = (sv4CurrentRecipe?.vsNumber ?: sv4VsCounter).coerceAtLeast(1)
            val curFormula = sv4CurrentRecipe?.formulaText ?: ""
            val nextNum = sv4NextRecipe?.vsNumber?.takeIf { it > curNum } ?: (curNum + 1)
            val nextFormula = sv4NextRecipe?.formulaText ?: ""
            val isNextReady = sv4NextVideoItem != null && sv4NextFile != null && sv4NextFile!!.exists()
            sv4VsCounter = maxOf(sv4VsCounter, if (sv4NextRecipe != null) nextNum else curNum)

            if (curFormula.isNotBlank()) {
                synchronized(sv4UsedFormulas) { sv4UsedFormulas.add(curFormula) }
            }
            if (nextFormula.isNotBlank()) {
                synchronized(sv4UsedFormulas) { sv4UsedFormulas.add(nextFormula) }
            }

            val curState = _ifsState.value
            _ifsState.value = curState.copy(
                activeVtNumber = if (_videoMode.value == IfsVideoMode.SV4) curNum else curState.activeVtNumber,
                currentVideoId = if (_videoMode.value == IfsVideoMode.SV4) (sv4CurrentVideoItem?.id ?: curState.currentVideoId) else curState.currentVideoId,
                currentVideoName = if (_videoMode.value == IfsVideoMode.SV4) (sv4CurrentVideoItem?.name ?: curState.currentVideoName) else curState.currentVideoName,
                sv4CurrentVsNumber = curNum,
                sv4ActiveFormula = curFormula,
                sv4NextVsNumber = nextNum,
                sv4NextFormula = nextFormula,
                sv4NextReady = isNextReady,
                sv4NextStitchPercent = if (isNextReady) 100 else 0,
                sv4AutoDeletedCount = sv4AutoDeletedFilesCount,
                statusMessage = if (_videoMode.value == IfsVideoMode.SV4 && curFormula.isNotBlank()) {
                    if (isNextReady) {
                        "🎬 Play VS$curNum [$curFormula] • ✅ VS$nextNum [$nextFormula] Siap di Daftar (#2)!"
                    } else {
                        "🎬 Play VS$curNum [$curFormula] • ⏳ Melanjutkan VS$nextNum..."
                    }
                } else curState.statusMessage
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Gagal memuat state rolling SV4: ${e.message}")
        }
    }

    /**
     * Memulihkan nomor VS dan file vs_rolling_<N>.mp4 yang masih ada di vcam_settings / folder Camera1
     * apabila aplikasi baru saja di-update dari APK lama yang belum menyimpan KEY_IFS_SV4_VS_COUNTER.
     */
    private fun recoverSv4StateFromExistingPlaylistOrDisk() {
        try {
            val vcamSp = appContext.getSharedPreferences("vcam_settings", Context.MODE_PRIVATE)
            val plJson = vcamSp.getString("playlist_json", null)
            val regex = Regex("""vs_rolling_(\d+)""")
            val formulaRegex = Regex("""\]\s*(.+)$""")

            data class RecoveredVsEntry(
                val vsNum: Int,
                val item: VideoItem,
                val file: File,
                val formula: String
            )

            val recoveredList = mutableListOf<RecoveredVsEntry>()
            val sourceIdByLNum = _sv4LVideoIds.value.mapIndexed { idx, id -> (idx + 1) to id }.toMap()

            fun parseSegmentsFromFormula(formula: String): List<VsSegmentSpec> {
                val segRegex = Regex("""L(\d+)\((\d+)x\)""")
                return segRegex.findAll(formula).mapNotNull { m ->
                    val lNum = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    val loops = m.groupValues[2].toIntOrNull()?.coerceIn(1, 10) ?: 1
                    val vidId = sourceIdByLNum[lNum] ?: ""
                    VsSegmentSpec(lNumber = lNum, videoId = vidId, loopCount = loops)
                }.toList()
            }

            if (!plJson.isNullOrBlank()) {
                val arr = JSONArray(plJson)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id", "")
                    val path = o.optString("path", "")
                    val name = o.optString("name", "")
                    val match = regex.find(id) ?: regex.find(path) ?: continue
                    val vsNum = match.groupValues[1].toIntOrNull() ?: continue
                    val f = File(path)
                    if (vsNum > 0 && f.exists() && f.length() > 0L) {
                        val extractedFormula = formulaRegex.find(name)?.groupValues?.getOrNull(1)?.trim() ?: ""
                        val item = VideoItem(
                            id = "vs_rolling_$vsNum",
                            name = name.ifBlank { "🎬 [VS$vsNum] $extractedFormula" },
                            path = f.absolutePath,
                            uri = android.net.Uri.fromFile(f).toString(),
                            durationMs = o.optLong("durationMs", 6000L),
                            sizeBytes = f.length(),
                            width = o.optInt("width", 720),
                            height = o.optInt("height", 1280),
                            isMuted = o.optBoolean("isMuted", false),
                            isEnabled = true
                        )
                        recoveredList.add(RecoveredVsEntry(vsNum, item, f, extractedFormula))
                    }
                }
            }

            if (recoveredList.isEmpty()) {
                val camDir = File(appContext.filesDir, "Camera1")
                val files = camDir.listFiles() ?: emptyArray()
                for (f in files) {
                    val m = Regex("""^vs_rolling_(\d+)\.mp4$""").find(f.name) ?: continue
                    val vsNum = m.groupValues[1].toIntOrNull() ?: continue
                    if (vsNum > 0 && f.exists() && f.length() > 0L) {
                        val item = VideoItem(
                            id = "vs_rolling_$vsNum",
                            name = "🎬 [VS$vsNum] Variasi Susunan #$vsNum",
                            path = f.absolutePath,
                            uri = android.net.Uri.fromFile(f).toString(),
                            durationMs = 8000L,
                            sizeBytes = f.length(),
                            width = 720,
                            height = 1280,
                            isMuted = false,
                            isEnabled = true
                        )
                        recoveredList.add(RecoveredVsEntry(vsNum, item, f, "Variasi #$vsNum"))
                    }
                }
            }

            if (recoveredList.isNotEmpty()) {
                val sorted = recoveredList.sortedBy { it.vsNum }
                val activeEntry = sorted.first()
                sv4CurrentVideoItem = activeEntry.item
                sv4CurrentFile = activeEntry.file
                sv4CurrentRecipe = VsVariationRecipe(
                    vsNumber = activeEntry.vsNum,
                    segments = parseSegmentsFromFormula(activeEntry.formula),
                    formulaText = activeEntry.formula.ifBlank { "Variasi #${activeEntry.vsNum}" }
                )
                sv4VsCounter = maxOf(sv4VsCounter, activeEntry.vsNum)
                if (sorted.size >= 2) {
                    val queuedEntry = sorted[1]
                    sv4NextVideoItem = queuedEntry.item
                    sv4NextFile = queuedEntry.file
                    sv4NextRecipe = VsVariationRecipe(
                        vsNumber = queuedEntry.vsNum,
                        segments = parseSegmentsFromFormula(queuedEntry.formula),
                        formulaText = queuedEntry.formula.ifBlank { "Variasi #${queuedEntry.vsNum}" }
                    )
                    sv4VsCounter = maxOf(sv4VsCounter, queuedEntry.vsNum)
                }
                saveSv4RollingStateToPrefs()
                Log.i(TAG, "♻️ [SV4-RECOVERY] Berhasil memulihkan progres VS dari disk: aktif=VS${activeEntry.vsNum}, counter=$sv4VsCounter")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Gagal melakukan recovery vs_rolling dari disk: ${e.message}")
        }
    }

    @Volatile
    private var sv4LastSourceSignature: String = ""
    @Volatile
    private var sv4AudioLastSourceSignature: String = ""

    private fun isRecipeValidForCurrentL(
        recipe: VsVariationRecipe?,
        effectiveLIds: List<String>,
        minLoops: Int = _sv4MinLoops.value,
        maxLoops: Int = _sv4MaxLoops.value
    ): Boolean {
        if (recipe == null || effectiveLIds.isEmpty() || recipe.segments.isEmpty()) return false
        val safeMin = minLoops.coerceAtLeast(1)
        val safeMax = maxLoops.coerceAtLeast(safeMin)
        val validSet = effectiveLIds.toSet()
        if (!recipe.segments.all { validSet.contains(it.videoId) && it.loopCount in safeMin..safeMax }) {
            return false
        }
        if (safeMin == 1 && safeMax == 1 && recipe.segments.size > effectiveLIds.size) {
            return false
        }
        return true
    }

    /**
     * Menghentikan seluruh background stitcher, watchdog timer SV4 (Video & Audio),
     * serta membungkam semua layer StandaloneMusicPlayer ketika APK ditutup agar tidak ada suara bocor.
     */
    fun stopAllBackgroundPlaybackOnAppExit() {
        try { sv4StitchJob?.cancel() } catch (_: Throwable) {}
        sv4StitchJob = null
        try { sv4PlaybackWatchdogJob?.cancel() } catch (_: Throwable) {}
        sv4PlaybackWatchdogJob = null
        try { sv4AudioStitchJob?.cancel() } catch (_: Throwable) {}
        sv4AudioStitchJob = null
        try { sv4AudioWatchdogJob?.cancel() } catch (_: Throwable) {}
        sv4AudioWatchdogJob = null
        sv4IsInitialWarmupActive = false
        sv4WaitingForNextReady = false
        sv4AudioIsInitialWarmupActive = false
        sv4AudioWaitingForNextReady = false
        try {
            musicPlayer.stopAll(clearTrackState = false)
        } catch (_: Throwable) {}
    }

    /**
     * Menghapus secara sinkron seluruh file fisik rolling sementara (`vs_rolling_*.mp4` & `vs_audio_rolling_*.m4a`)
     * serta me-reset state in-memory ketika berganti Proyek/Konsep atau menghapus seluruh video,
     * sehingga video dari Proyek 1 tidak pernah bocor/tampil di Proyek 2.
     */
    fun resetAndPurgeAllRollingFilesForProjectSwitch() {
        try { sv4StitchJob?.cancel() } catch (_: Throwable) {}
        try { sv4PlaybackWatchdogJob?.cancel() } catch (_: Throwable) {}
        try { sv4AudioStitchJob?.cancel() } catch (_: Throwable) {}
        try { sv4AudioWatchdogJob?.cancel() } catch (_: Throwable) {}

        sv4IsInitialWarmupActive = false
        sv4WaitingForNextReady = false
        sv4CurrentRecipe = null
        sv4CurrentVideoItem = null
        sv4CurrentFile = null
        sv4NextRecipe = null
        sv4NextVideoItem = null
        sv4NextFile = null
        sv4LastSourceSignature = ""

        sv4AudioIsInitialWarmupActive = false
        sv4AudioWaitingForNextReady = false
        sv4AudioCurrentRecipe = null
        sv4AudioCurrentItem = null
        sv4AudioCurrentFile = null
        sv4AudioNextRecipe = null
        sv4AudioNextItem = null
        sv4AudioNextFile = null
        sv4AudioLastSourceSignature = ""
        _sv4VideoRunning.value = false
        _sv4AudioRunning.value = false

        try {
            val camDir = File(appContext.filesDir, "Camera1")
            if (camDir.exists()) {
                camDir.listFiles()?.forEach { f ->
                    if (f.name.startsWith("vs_rolling_") ||
                        f.name.startsWith("vs_audio_rolling_") ||
                        f.name.startsWith(".tmp_vs_")
                    ) {
                        try { f.delete() } catch (_: Throwable) {}
                    }
                }
            }
            val workDir = File(appContext.cacheDir, "vs_audio_work")
            if (workDir.exists()) {
                workDir.listFiles()?.forEach { f ->
                    try { f.delete() } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}

        saveSv4RollingStateToPrefs()
        saveSv4AudioRollingStateToPrefs()
    }

    private fun saveAllToPrefs() {
        try {
            val vtmObj = packageToJson(_vtmPackage.value)
            val arr = JSONArray()
            for (item in _vtPackages.value) {
                arr.put(packageToJson(item))
            }
            val vbArr = JSONArray()
            for (id in _sv3VbVideoIds.value) {
                vbArr.put(id)
            }
            val sv4LArr = JSONArray()
            for (id in _sv4LVideoIds.value) {
                sv4LArr.put(id)
            }
            val sv4AudioLArr = JSONArray()
            for (id in _sv4AudioLIds.value) {
                sv4AudioLArr.put(id)
            }
            prefs.edit()
                .putString(KEY_IFS_VTM_JSON, vtmObj.toString())
                .putString(KEY_IFS_PACKAGES_JSON, arr.toString())
                .putStringSet(KEY_IFS_SR_AUDIO_IDS, _srAudioIds.value)
                .putString(KEY_IFS_SV3_VL_VIDEO_ID, _sv3VlVideoId.value ?: "")
                .putInt(KEY_IFS_SV3_VL_MIN_LOOPS, _sv3VlMinLoops.value)
                .putInt(KEY_IFS_SV3_VL_MAX_LOOPS, _sv3VlMaxLoops.value)
                .putString(KEY_IFS_SV3_VB_VIDEO_IDS_JSON, vbArr.toString())
                .putString(KEY_IFS_SV4_L_VIDEO_IDS_JSON, sv4LArr.toString())
                .putInt(KEY_IFS_SV4_MIN_LOOPS, _sv4MinLoops.value)
                .putInt(KEY_IFS_SV4_MAX_LOOPS, _sv4MaxLoops.value)
                .putBoolean(KEY_IFS_SV4_VIDEO_RUNNING, _sv4VideoRunning.value)
                .putString(KEY_IFS_AUDIO_MODE, _audioMode.value.name)
                .putBoolean(KEY_IFS_SV4_AUDIO_RUNNING, _sv4AudioRunning.value)
                .putString(KEY_IFS_SV4_AUDIO_L_IDS_JSON, sv4AudioLArr.toString())
                .putInt(KEY_IFS_SV4_AUDIO_MIN_LOOPS, _sv4AudioMinLoops.value)
                .putInt(KEY_IFS_SV4_AUDIO_MAX_LOOPS, _sv4AudioMaxLoops.value)
                .apply()
            saveSv4RollingStateToPrefs()
            saveSv4AudioRollingStateToPrefs()
        } catch (e: Throwable) {
            Log.e(TAG, "Gagal menyimpan konfigurasi IFS: ${e.message}")
        }
    }

    private fun packageToJson(item: IfsVtPackage): JSONObject {
        return JSONObject().apply {
            put("vtNumber", item.vtNumber)
            put("title", item.title)
            put("abbVideoId", item.abbVideoId ?: "")
            put("blVideoId", item.blVideoId ?: "")
            put("bbtVideoId", item.bbtVideoId ?: "")
            put("triggerKeyword", item.triggerKeyword)
            put("linkedAudioId", item.linkedAudioId ?: "")
            put("linkedAxNumber", item.linkedAxNumber)
        }
    }

    /**
     * Mengekspor seluruh pengaturan Infinity System (SV1/SV2/SV3/SV4, VTM, VT1..n, VL/VB, VS, AX, SR, SC) ke JSONObject
     * untuk fitur Backup & Restore Konsep 1-Klik.
     */
    fun exportToJson(): JSONObject {
        return JSONObject().apply {
            put("isEnabled", _isIfsActive.value)
            put("isScEnabled", _isScEnabled.value)
            put("videoMode", _videoMode.value.name)
            put("activeVtNumber", _ifsState.value.activeVtNumber)
            put("activeSubPhase", _ifsState.value.activeSubPhase.name)
            put("vtmPackage", packageToJson(_vtmPackage.value))
            val vtArr = JSONArray()
            _vtPackages.value.forEach { vtArr.put(packageToJson(it)) }
            put("vtPackages", vtArr)
            val srArr = JSONArray()
            _srAudioIds.value.forEach { srArr.put(it) }
            put("srAudioIds", srArr)
            put("sv3VlVideoId", _sv3VlVideoId.value ?: "")
            put("sv3VlMinLoops", _sv3VlMinLoops.value)
            put("sv3VlMaxLoops", _sv3VlMaxLoops.value)
            val vbArr = JSONArray()
            _sv3VbVideoIds.value.forEach { vbArr.put(it) }
            put("sv3VbVideoIds", vbArr)
            val sv4Arr = JSONArray()
            _sv4LVideoIds.value.forEach { sv4Arr.put(it) }
            put("sv4LVideoIds", sv4Arr)
            put("sv4MinLoops", _sv4MinLoops.value)
            put("sv4MaxLoops", _sv4MaxLoops.value)
            put("sv4VsCounter", sv4VsCounter)
            put("sv4AutoDeletedFilesCount", sv4AutoDeletedFilesCount)
            put("sv4LastFormulaSignature", lastSv4FormulaSignature)
            sv4CurrentRecipe?.let { put("sv4CurrentRecipe", recipeToJson(it)) }
            sv4NextRecipe?.let { put("sv4NextRecipe", recipeToJson(it)) }
            val usedArr = JSONArray()
            synchronized(sv4UsedFormulas) {
                sv4UsedFormulas.forEach { if (it.isNotBlank()) usedArr.put(it) }
            }
            put("sv4UsedFormulas", usedArr)
            put("sv4VideoRunning", _sv4VideoRunning.value)
            put("audioMode", _audioMode.value.name)
            put("sv4AudioRunning", _sv4AudioRunning.value)
            val sv4AudioArr = JSONArray()
            _sv4AudioLIds.value.forEach { sv4AudioArr.put(it) }
            put("sv4AudioLIds", sv4AudioArr)
            put("sv4AudioMinLoops", _sv4AudioMinLoops.value)
            put("sv4AudioMaxLoops", _sv4AudioMaxLoops.value)
            put("sv4AudioVsCounter", sv4AudioVsCounter)
            put("sv4AudioAutoDeletedFilesCount", sv4AudioAutoDeletedFilesCount)
            put("sv4AudioLastFormulaSignature", lastSv4AudioFormulaSignature)
            sv4AudioCurrentRecipe?.let { put("sv4AudioCurrentRecipe", recipeToJson(it)) }
            sv4AudioNextRecipe?.let { put("sv4AudioNextRecipe", recipeToJson(it)) }
        }
    }

    /**
     * Memulihkan seluruh pengaturan Infinity System dari JSONObject (1-Click Concept Restore).
     */
    fun importFromJson(obj: JSONObject) {
        try {
            // Bersihkan tuntas file rolling sementara & state dari Proyek sebelumnya agar tidak pernah nyangkut!
            resetAndPurgeAllRollingFilesForProjectSwitch()
            manuallyRemovedSv4LIds.clear()
            manuallyRemovedSv4AudioLIds.clear()
            _sv4LVideoIds.value = emptyList()
            _sv3VbVideoIds.value = emptyList()
            _sv3VlVideoId.value = null
            _srAudioIds.value = emptySet()
            _sv4AudioLIds.value = emptyList()
            synchronized(sv4UsedFormulas) { sv4UsedFormulas.clear() }
            synchronized(sv4AudioUsedFormulas) { sv4AudioUsedFormulas.clear() }

            val enabled = obj.optBoolean("isEnabled", false)
            val scEnabled = obj.optBoolean("isScEnabled", false)
            val modeName = obj.optString("videoMode", IfsVideoMode.SV1.name)
            val mode = try { IfsVideoMode.valueOf(modeName) } catch (_: Throwable) { IfsVideoMode.SV1 }
            val audioModeName = obj.optString("audioMode", IfsAudioMode.SR_AX.name)
            val aMode = try { IfsAudioMode.valueOf(audioModeName) } catch (_: Throwable) { IfsAudioMode.SR_AX }
            val videoRunning = obj.optBoolean("sv4VideoRunning", false) && mode == IfsVideoMode.SV4
            val audioRunning = obj.optBoolean("sv4AudioRunning", false) && aMode == IfsAudioMode.SV4_VS

            obj.optJSONObject("vtmPackage")?.let { vtmObj ->
                _vtmPackage.value = parsePackageJson(vtmObj, 0)
            }

            obj.optJSONArray("vtPackages")?.let { arr ->
                val list = mutableListOf<IfsVtPackage>()
                for (i in 0 until arr.length()) {
                    val itemObj = arr.optJSONObject(i) ?: continue
                    val pkg = parsePackageJson(itemObj, i + 1)
                    if (pkg.vtNumber > 0) list.add(pkg)
                }
                if (list.isNotEmpty()) {
                    _vtPackages.value = renumberVtList(list)
                }
            }

            obj.optJSONArray("srAudioIds")?.let { srArr ->
                val set = mutableSetOf<String>()
                for (i in 0 until srArr.length()) {
                    val id = srArr.optString(i, "")
                    if (id.isNotBlank()) set.add(id)
                }
                _srAudioIds.value = set
            }

            if (obj.has("sv3VlVideoId")) {
                _sv3VlVideoId.value = obj.optString("sv3VlVideoId", "").takeIf { it.isNotBlank() && it != "null" }
            }
            if (obj.has("sv3VlMinLoops") || obj.has("sv3VlMaxLoops")) {
                val minL = obj.optInt("sv3VlMinLoops", _sv3VlMinLoops.value).coerceIn(1, 50)
                val maxL = obj.optInt("sv3VlMaxLoops", _sv3VlMaxLoops.value).coerceIn(minL, 50)
                _sv3VlMinLoops.value = minL
                _sv3VlMaxLoops.value = maxL
            }
            obj.optJSONArray("sv3VbVideoIds")?.let { vbArr ->
                val vbList = mutableListOf<String>()
                for (i in 0 until vbArr.length()) {
                    val id = vbArr.optString(i, "")
                    if (id.isNotBlank() && id != "null" && !vbList.contains(id)) {
                        vbList.add(id)
                    }
                }
                _sv3VbVideoIds.value = vbList
            }
            obj.optJSONArray("sv4LVideoIds")?.let { sv4Arr ->
                val lList = mutableListOf<String>()
                for (i in 0 until sv4Arr.length()) {
                    val id = sv4Arr.optString(i, "")
                    if (id.isNotBlank() && id != "null" && !lList.contains(id)) {
                        lList.add(id)
                    }
                }
                _sv4LVideoIds.value = lList
            }
            if (obj.has("sv4MinLoops") || obj.has("sv4MaxLoops")) {
                val minL = obj.optInt("sv4MinLoops", _sv4MinLoops.value).coerceIn(1, 10)
                val maxL = obj.optInt("sv4MaxLoops", _sv4MaxLoops.value).coerceIn(minL, 10)
                _sv4MinLoops.value = minL
                _sv4MaxLoops.value = maxL
            }
            sv4VsCounter = obj.optInt("sv4VsCounter", 0).coerceAtLeast(0)
            sv4AutoDeletedFilesCount = obj.optInt("sv4AutoDeletedFilesCount", 0).coerceAtLeast(0)
            lastSv4FormulaSignature = obj.optString("sv4LastFormulaSignature", "")
            sv4CurrentRecipe = obj.optJSONObject("sv4CurrentRecipe")?.let { parseRecipeJsonObject(it) }
            sv4NextRecipe = obj.optJSONObject("sv4NextRecipe")?.let { parseRecipeJsonObject(it) }
            obj.optJSONArray("sv4UsedFormulas")?.let { usedArr ->
                synchronized(sv4UsedFormulas) {
                    for (i in 0 until usedArr.length()) {
                        val f = usedArr.optString(i, "")
                        if (f.isNotBlank()) sv4UsedFormulas.add(f)
                    }
                }
            }

            obj.optJSONArray("sv4AudioLIds")?.let { sv4AArr ->
                val laList = mutableListOf<String>()
                for (i in 0 until sv4AArr.length()) {
                    val id = sv4AArr.optString(i, "")
                    if (id.isNotBlank() && id != "null" && !laList.contains(id)) {
                        laList.add(id)
                    }
                }
                _sv4AudioLIds.value = laList
            }
            if (obj.has("sv4AudioMinLoops") || obj.has("sv4AudioMaxLoops")) {
                val minLA = obj.optInt("sv4AudioMinLoops", _sv4AudioMinLoops.value).coerceIn(1, 10)
                val maxLA = obj.optInt("sv4AudioMaxLoops", _sv4AudioMaxLoops.value).coerceIn(minLA, 10)
                _sv4AudioMinLoops.value = minLA
                _sv4AudioMaxLoops.value = maxLA
            }
            sv4AudioVsCounter = obj.optInt("sv4AudioVsCounter", 0).coerceAtLeast(0)
            sv4AudioAutoDeletedFilesCount = obj.optInt("sv4AudioAutoDeletedFilesCount", 0).coerceAtLeast(0)
            lastSv4AudioFormulaSignature = obj.optString("sv4AudioLastFormulaSignature", "")
            sv4AudioCurrentRecipe = obj.optJSONObject("sv4AudioCurrentRecipe")?.let { parseRecipeJsonObject(it) }
            sv4AudioNextRecipe = obj.optJSONObject("sv4AudioNextRecipe")?.let { parseRecipeJsonObject(it) }

            sv3VlLoopCountDeck.reset()
            sv3VbGroupDeck.reset()
            lastPickedSv3VlLoops = -1
            lastPickedSv3VbIdx = -1

            _videoMode.value = mode
            _audioMode.value = aMode
            _sv4VideoRunning.value = videoRunning
            _sv4AudioRunning.value = audioRunning
            _isScEnabled.value = scEnabled
            _isIfsActive.value = enabled

            prefs.edit()
                .putBoolean(KEY_IFS_ENABLED, enabled)
                .putBoolean(KEY_IFS_SC_ENABLED, scEnabled)
                .putString(KEY_IFS_VIDEO_MODE, mode.name)
                .putBoolean(KEY_IFS_SV4_VIDEO_RUNNING, videoRunning)
                .putString(KEY_IFS_AUDIO_MODE, aMode.name)
                .putBoolean(KEY_IFS_SV4_AUDIO_RUNNING, audioRunning)
                .apply()

            saveAllToPrefs()

            val startVt = if (mode == IfsVideoMode.SV2) 1 else 0
            val defaultPhase = when (mode) {
                IfsVideoMode.SV3 -> IfsSubPhase.VL
                IfsVideoMode.SV4 -> IfsSubPhase.VS
                else -> IfsSubPhase.ABB
            }
            val targetVt = obj.optInt("activeVtNumber", startVt)
            val subPhaseName = obj.optString("activeSubPhase", defaultPhase.name)
            val subPhase = try { IfsSubPhase.valueOf(subPhaseName) } catch (_: Throwable) { defaultPhase }

            if (enabled) {
                ensureDefaultVideoSlotsPopulated()
                if (mode == IfsVideoMode.SV4) {
                    if (videoRunning) {
                        startSv4RollingCycle(resetCounter = false)
                    } else {
                        _ifsState.value = _ifsState.value.copy(
                            isEnabled = true,
                            isScEnabled = scEnabled,
                            videoMode = mode,
                            audioMode = aMode,
                            sv4VideoRunning = false,
                            sv4AudioRunning = audioRunning,
                            sv4TotalLCount = _sv4LVideoIds.value.size,
                            sv4AudioTotalLCount = _sv4AudioLIds.value.size,
                            statusMessage = "🛠️ Mode Persiapan SV4 Video: Edit/Tambah/Optimasi Video lalu tekan '▶️ MULAI SV4'"
                        )
                    }
                } else {
                    val effPhase = if (mode == IfsVideoMode.SV3 && subPhase != IfsSubPhase.VL && subPhase != IfsSubPhase.VB) {
                        IfsSubPhase.VL
                    } else subPhase
                    val targetLoops = drawNextSv3VlLoopCount()
                    val nextVideo = resolveVideoItem(targetVt, effPhase) ?: resolveVideoItem(startVt, defaultPhase)
                    _ifsState.value = _ifsState.value.copy(
                        isEnabled = true,
                        isScEnabled = scEnabled,
                        videoMode = mode,
                        audioMode = aMode,
                        sv4VideoRunning = false,
                        sv4AudioRunning = audioRunning,
                        activeVtNumber = targetVt,
                        activeSubPhase = effPhase,
                        pendingTargetVtNumber = null,
                        queue = emptyList(),
                        currentVideoId = nextVideo?.id,
                        currentVideoName = nextVideo?.name,
                        statusMessage = "⚡ Konsep IFS Dipulihkan (${mode.badge})",
                        phaseStepVersion = _ifsState.value.phaseStepVersion + 1L,
                        sv3CurrentVlLoop = 1,
                        sv3TargetVlLoops = targetLoops,
                        sv3ActiveVbNumber = 1,
                        sv3VbRemainingInCycle = getRemainingSv3VbInCycle(),
                        sv3VbTotalCount = _sv3VbVideoIds.value.size,
                        sv4TotalLCount = _sv4LVideoIds.value.size,
                        sv4AudioTotalLCount = _sv4AudioLIds.value.size
                    )
                    applyAudioModeToMusicPlayer(aMode)
                    try {
                        val repo = VcamRepository.getInstance(appContext)
                        repo.applyIfsModeStateSync(enabled = true, activeVideo = nextVideo)
                    } catch (_: Throwable) {}
                    if (aMode == IfsAudioMode.SV4_VS) {
                        if (audioRunning) {
                            startSv4AudioRollingCycle(resetCounter = false)
                        }
                    } else if (mode == IfsVideoMode.SV3) {
                        musicPlayer.ensureSrPlaying()
                    } else {
                        playLinkedAxForVt(targetVt)
                    }
                }
            } else {
                disableIfsFromExternalModeSwitch()
                _ifsState.value = _ifsState.value.copy(
                    isScEnabled = scEnabled,
                    videoMode = mode,
                    audioMode = aMode,
                    sv4VideoRunning = false,
                    sv4AudioRunning = audioRunning,
                    sv3VbRemainingInCycle = getRemainingSv3VbInCycle(),
                    sv3VbTotalCount = _sv3VbVideoIds.value.size,
                    sv4TotalLCount = _sv4LVideoIds.value.size,
                    sv4AudioTotalLCount = _sv4AudioLIds.value.size
                )
                if (aMode == IfsAudioMode.SV4_VS && audioRunning) {
                    startSv4AudioRollingCycle(resetCounter = false)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Gagal memulihkan state IFS dari JSON: ${e.message}", e)
        }
    }

    /**
     * Mengembalikan daftar seluruh paket yang tampil pada mode aktif saat ini:
     * - SV1: [VTM (0), VT1 (1), VT2 (2), ...]
     * - SV2: [VT1 (1), VT2 (2), ...]
     * - SV3 / SV4: Menggunakan kartu tersendiri.
     */
    fun getVisiblePackages(mode: IfsVideoMode = _videoMode.value): List<IfsVtPackage> {
        return when (mode) {
            IfsVideoMode.SV1 -> listOf(_vtmPackage.value) + _vtPackages.value
            IfsVideoMode.SV2 -> _vtPackages.value
            IfsVideoMode.SV3, IfsVideoMode.SV4 -> emptyList()
        }
    }

    fun getPackageByNumber(vtNumber: Int): IfsVtPackage? {
        return if (vtNumber == 0) {
            _vtmPackage.value
        } else {
            _vtPackages.value.firstOrNull { it.vtNumber == vtNumber }
        }
    }

    fun disableIfsFromExternalModeSwitch() {
        cancelAndCleanupSv4RollingFiles()
        _sv4VideoRunning.value = false
        _isIfsActive.value = false
        prefs.edit()
            .putBoolean(KEY_IFS_ENABLED, false)
            .putBoolean(KEY_IFS_SV4_VIDEO_RUNNING, false)
            .apply()
        musicPlayer.setSrMode(false)
        _ifsState.value = _ifsState.value.copy(
            isEnabled = false,
            sv4VideoRunning = false,
            pendingTargetVtNumber = null,
            queue = emptyList(),
            sv4IsStitchingNext = false,
            sv4NextReady = false,
            statusMessage = "Infinity System NONAKTIF",
            phaseStepVersion = _ifsState.value.phaseStepVersion + 1L
        )
    }

    /**
     * Memulai proses peracikan & pemutaran SV4 Video (Mode VS) secara eksplisit setelah pengguna
     * selesai menambah/mengedit/mengoptimasi video bahan sumber L1..Ln.
     */
    fun startSv4VideoEngine(resetCounter: Boolean = false) {
        if (!_isIfsActive.value) {
            _isIfsActive.value = true
        }
        if (_videoMode.value != IfsVideoMode.SV4) {
            _videoMode.value = IfsVideoMode.SV4
        }
        _sv4VideoRunning.value = true
        prefs.edit()
            .putBoolean(KEY_IFS_ENABLED, true)
            .putString(KEY_IFS_VIDEO_MODE, IfsVideoMode.SV4.name)
            .putBoolean(KEY_IFS_SV4_VIDEO_RUNNING, true)
            .apply()
        _ifsState.value = _ifsState.value.copy(
            isEnabled = true,
            videoMode = IfsVideoMode.SV4,
            sv4VideoRunning = true
        )
        startSv4RollingCycle(resetCounter = resetCounter)
    }

    /**
     * Menghentikan sementara SV4 Video (Mode VS) untuk kembali ke Mode Persiapan/Edit Materi:
     * Membuka kembali daftar video sumber L1..Ln agar pengguna bebas menambah video, mengedit, atau optimasi.
     */
    fun stopSv4VideoEngineForEdit() {
        _sv4VideoRunning.value = false
        prefs.edit().putBoolean(KEY_IFS_SV4_VIDEO_RUNNING, false).apply()
        cancelAndCleanupSv4RollingFiles()
        _ifsState.value = _ifsState.value.copy(
            sv4VideoRunning = false,
            sv4IsStitchingNext = false,
            sv4NextReady = false,
            sv4TotalLCount = _sv4LVideoIds.value.size,
            statusMessage = "🛠️ Mode Persiapan & Edit SV4 Video • Silakan tambah/edit/optimasi video lalu tekan '▶️ MULAI SV4'"
        )
    }

    /**
     * Memulai proses peracikan & pemutaran SV4 Audio (Mode VS Audio) secara eksplisit setelah pengguna
     * selesai menambah audio / upload ZIP / melakukan Edit Audio Massal.
     */
    fun startSv4AudioEngine(resetCounter: Boolean = false) {
        if (_audioMode.value != IfsAudioMode.SV4_VS) {
            _audioMode.value = IfsAudioMode.SV4_VS
        }
        _sv4AudioRunning.value = true
        prefs.edit()
            .putString(KEY_IFS_AUDIO_MODE, IfsAudioMode.SV4_VS.name)
            .putBoolean(KEY_IFS_SV4_AUDIO_RUNNING, true)
            .apply()
        _ifsState.value = _ifsState.value.copy(
            audioMode = IfsAudioMode.SV4_VS,
            sv4AudioRunning = true
        )
        startSv4AudioRollingCycle(resetCounter = resetCounter)
    }

    /**
     * Menghentikan sementara SV4 Audio (Mode VS Audio) untuk kembali ke Mode Persiapan & Edit Audio Massal:
     * Membuka kembali daftar trek sumber LA1..LAn agar pengguna bebas tambah audio/ZIP atau edit massal.
     */
    fun stopSv4AudioEngineForEdit() {
        _sv4AudioRunning.value = false
        prefs.edit().putBoolean(KEY_IFS_SV4_AUDIO_RUNNING, false).apply()
        cancelAndCleanupSv4AudioRollingFiles()
        try {
            musicPlayer.pause()
        } catch (_: Throwable) {}
        _ifsState.value = _ifsState.value.copy(
            audioMode = _audioMode.value,
            sv4AudioRunning = false,
            sv4AudioIsStitchingNext = false,
            sv4AudioNextReady = false,
            sv4AudioTotalLCount = _sv4AudioLIds.value.size,
            sv4AudioStatusMessage = "🛠️ Mode Persiapan & Edit Audio SV4 • Silakan tambah audio/ZIP atau Edit Massal lalu tekan '▶️ MULAI SV4 AUDIO'"
        )
    }

    fun setIfsEnabled(enabled: Boolean) {
        _isIfsActive.value = enabled
        prefs.edit().putBoolean(KEY_IFS_ENABLED, enabled).apply()
        if (enabled) {
            ensureDefaultVideoSlotsPopulated()
            val mode = _videoMode.value
            if (mode == IfsVideoMode.SV4) {
                if (_sv4VideoRunning.value) {
                    startSv4RollingCycle()
                } else {
                    val prepVideo = resolveVideoItem(0, IfsSubPhase.VS)
                    _ifsState.value = _ifsState.value.copy(
                        isEnabled = true,
                        videoMode = IfsVideoMode.SV4,
                        activeSubPhase = IfsSubPhase.VS,
                        currentVideoId = prepVideo?.id,
                        currentVideoName = prepVideo?.name,
                        sv4VideoRunning = false,
                        sv4TotalLCount = _sv4LVideoIds.value.size,
                        phaseStepVersion = _ifsState.value.phaseStepVersion + 1L,
                        statusMessage = "🛠️ Mode Persiapan SV4 Video: Edit/Tambah/Optimasi Video lalu tekan '▶️ MULAI SV4'"
                    )
                    try {
                        VcamRepository.getInstance(appContext).applyIfsModeStateSync(enabled = true, activeVideo = prepVideo)
                    } catch (_: Throwable) {}
                }
            } else if (mode == IfsVideoMode.SV3) {
                _sv4VideoRunning.value = false
                prefs.edit().putBoolean(KEY_IFS_SV4_VIDEO_RUNNING, false).apply()
                cancelAndCleanupSv4RollingFiles()
                sv3VlLoopCountDeck.reset()
                sv3VbGroupDeck.reset()
                lastPickedSv3VlLoops = -1
                lastPickedSv3VbIdx = -1
                val targetLoops = drawNextSv3VlLoopCount()
                val vbTotal = _sv3VbVideoIds.value.size
                val nextVideo = resolveVideoItem(0, IfsSubPhase.VL)
                _ifsState.value = _ifsState.value.copy(
                    isEnabled = true,
                    videoMode = mode,
                    activeVtNumber = 0,
                    activeSubPhase = IfsSubPhase.VL,
                    pendingTargetVtNumber = null,
                    queue = emptyList(),
                    currentVideoId = nextVideo?.id,
                    currentVideoName = nextVideo?.name,
                    statusMessage = "▶️ SV3 • VL Putaran 1/$targetLoops (Acak ${_sv3VlMinLoops.value}..${_sv3VlMaxLoops.value}x ➡️ Grup VB)",
                    phaseStepVersion = _ifsState.value.phaseStepVersion + 1L,
                    sv3CurrentVlLoop = 1,
                    sv3TargetVlLoops = targetLoops,
                    sv3ActiveVbNumber = 1,
                    sv3VbRemainingInCycle = vbTotal,
                    sv3VbTotalCount = vbTotal,
                    sv4VideoRunning = false
                )
                applyAudioModeToMusicPlayer(_audioMode.value)
                try {
                    val repo = VcamRepository.getInstance(appContext)
                    repo.applyIfsModeStateSync(enabled = true, activeVideo = nextVideo)
                    scope.launch(Dispatchers.IO) {
                        val dirs = listOf(repo.getCamera1Directory(), repo.getInternalCamera1Directory())
                        for (d in dirs) {
                            try {
                                val autoNextFile = java.io.File(d, "auto_next.txt")
                                autoNextFile.writeText("OFF")
                                autoNextFile.setReadable(true, false)
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
                musicPlayer.ensureSrPlaying()
            } else {
                _sv4VideoRunning.value = false
                prefs.edit().putBoolean(KEY_IFS_SV4_VIDEO_RUNNING, false).apply()
                cancelAndCleanupSv4RollingFiles()
                val startVt = if (mode == IfsVideoMode.SV1) 0 else 1
                val nextVideo = resolveVideoItem(startVt, IfsSubPhase.ABB)
                _ifsState.value = _ifsState.value.copy(
                    isEnabled = true,
                    videoMode = mode,
                    activeVtNumber = startVt,
                    activeSubPhase = IfsSubPhase.ABB,
                    pendingTargetVtNumber = null,
                    queue = emptyList(),
                    currentVideoId = nextVideo?.id,
                    currentVideoName = nextVideo?.name,
                    statusMessage = if (mode == IfsVideoMode.SV1) "▶️ ABA • A (ABB) Dimulai" else "▶️ PM1 • A (ABB) Dimulai",
                    phaseStepVersion = _ifsState.value.phaseStepVersion + 1L,
                    sv4VideoRunning = false
                )
                applyAudioModeToMusicPlayer(_audioMode.value)
                try {
                    val repo = VcamRepository.getInstance(appContext)
                    repo.applyIfsModeStateSync(enabled = true, activeVideo = nextVideo)
                    scope.launch(Dispatchers.IO) {
                        val dirs = listOf(repo.getCamera1Directory(), repo.getInternalCamera1Directory())
                        for (d in dirs) {
                            try {
                                val autoNextFile = java.io.File(d, "auto_next.txt")
                                autoNextFile.writeText("OFF")
                                autoNextFile.setReadable(true, false)
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
                playLinkedAxForVt(startVt)
            }
        } else {
            disableIfsFromExternalModeSwitch()
            try {
                val repo = VcamRepository.getInstance(appContext)
                repo.applyIfsModeStateSync(enabled = false, activeVideo = null)
            } catch (_: Throwable) {}
        }
    }

    fun setVideoMode(mode: IfsVideoMode) {
        if (_videoMode.value == mode && _isIfsActive.value) {
            return
        }
        if (_videoMode.value == IfsVideoMode.SV4 && mode != IfsVideoMode.SV4) {
            _sv4VideoRunning.value = false
            prefs.edit().putBoolean(KEY_IFS_SV4_VIDEO_RUNNING, false).apply()
            cancelAndCleanupSv4RollingFiles()
        }
        _videoMode.value = mode
        prefs.edit().putString(KEY_IFS_VIDEO_MODE, mode.name).apply()
        if (!_isIfsActive.value) {
            _isIfsActive.value = true
            prefs.edit().putBoolean(KEY_IFS_ENABLED, true).apply()
        }
        ensureDefaultVideoSlotsPopulated()
        if (mode == IfsVideoMode.SV4) {
            // Jangan langsung menyalakan peracikan SV4 saat baru memilih mode SV4!
            // Masuk ke tahap Persiapan & Edit dulu agar pengguna bisa tambah/edit/optimasi video.
            _sv4VideoRunning.value = false
            prefs.edit().putBoolean(KEY_IFS_SV4_VIDEO_RUNNING, false).apply()
            cancelAndCleanupSv4RollingFiles()
            val prepVideo = resolveVideoItem(0, IfsSubPhase.VS)
            _ifsState.value = _ifsState.value.copy(
                isEnabled = _isIfsActive.value,
                videoMode = IfsVideoMode.SV4,
                activeSubPhase = IfsSubPhase.VS,
                currentVideoId = prepVideo?.id,
                currentVideoName = prepVideo?.name,
                sv4VideoRunning = false,
                sv4IsStitchingNext = false,
                sv4NextReady = false,
                sv4TotalLCount = _sv4LVideoIds.value.size,
                phaseStepVersion = _ifsState.value.phaseStepVersion + 1L,
                statusMessage = "🛠️ Mode Persiapan SV4 Video: Edit/Tambah/Optimasi Video lalu tekan '▶️ MULAI SV4'"
            )
            try {
                VcamRepository.getInstance(appContext).applyIfsModeStateSync(enabled = _isIfsActive.value, activeVideo = prepVideo)
            } catch (_: Throwable) {}
            return
        }
        val startVt = if (mode == IfsVideoMode.SV2) 1 else 0
        val startPhase = when (mode) {
            IfsVideoMode.SV3 -> IfsSubPhase.VL
            IfsVideoMode.SV4 -> IfsSubPhase.VS
            else -> IfsSubPhase.ABB
        }
        val nextVideo = resolveVideoItem(startVt, startPhase)
        if (mode == IfsVideoMode.SV3) {
            sv3VlLoopCountDeck.reset()
            sv3VbGroupDeck.reset()
            lastPickedSv3VlLoops = -1
            lastPickedSv3VbIdx = -1
        }
        val initialTargetLoops = if (mode == IfsVideoMode.SV3) drawNextSv3VlLoopCount() else _ifsState.value.sv3TargetVlLoops
        _ifsState.value = _ifsState.value.copy(
            isEnabled = _isIfsActive.value,
            videoMode = mode,
            activeVtNumber = startVt,
            activeSubPhase = startPhase,
            pendingTargetVtNumber = null,
            queue = emptyList(),
            currentVideoId = nextVideo?.id,
            currentVideoName = nextVideo?.name,
            statusMessage = "Mode berganti ke ${mode.title}",
            phaseStepVersion = _ifsState.value.phaseStepVersion + 1L,
            sv3CurrentVlLoop = 1,
            sv3TargetVlLoops = initialTargetLoops,
            sv3ActiveVbNumber = 1,
            sv3VbRemainingInCycle = _sv3VbVideoIds.value.size,
            sv3VbTotalCount = _sv3VbVideoIds.value.size,
            sv4TotalLCount = _sv4LVideoIds.value.size
        )
        if (_isIfsActive.value) {
            resetCycleToBeginning()
        }
    }

    fun resetCycleToBeginning() {
        ensureDefaultVideoSlotsPopulated()
        val mode = _videoMode.value
        if (mode == IfsVideoMode.SV4) {
            startSv4VideoEngine(resetCounter = true)
            return
        }
        if (mode == IfsVideoMode.SV3) {
            sv3VlLoopCountDeck.reset()
            sv3VbGroupDeck.reset()
            lastPickedSv3VlLoops = -1
            lastPickedSv3VbIdx = -1
            val targetLoops = drawNextSv3VlLoopCount()
            val vbTotal = _sv3VbVideoIds.value.size
            val nextVideo = resolveVideoItem(0, IfsSubPhase.VL)
            _ifsState.value = _ifsState.value.copy(
                isEnabled = _isIfsActive.value,
                videoMode = mode,
                activeVtNumber = 0,
                activeSubPhase = IfsSubPhase.VL,
                pendingTargetVtNumber = null,
                queue = emptyList(),
                currentVideoId = nextVideo?.id,
                currentVideoName = nextVideo?.name,
                statusMessage = "▶️ SV3 • VL Putaran 1/$targetLoops (Target Acak ${targetLoops}x ➡️ Grup VB)",
                phaseStepVersion = _ifsState.value.phaseStepVersion + 1L,
                sv3CurrentVlLoop = 1,
                sv3TargetVlLoops = targetLoops,
                sv3ActiveVbNumber = 1,
                sv3VbRemainingInCycle = vbTotal,
                sv3VbTotalCount = vbTotal
            )
            applyAudioModeToMusicPlayer(_audioMode.value)
            syncCurrentVideoToRepository(broadcastToHook = true, bumpPhaseVersion = false)
            musicPlayer.ensureSrPlaying()
            return
        }

        val startVt = if (mode == IfsVideoMode.SV1) 0 else 1
        val nextVideo = resolveVideoItem(startVt, IfsSubPhase.ABB)
        _ifsState.value = _ifsState.value.copy(
            isEnabled = _isIfsActive.value,
            videoMode = mode,
            activeVtNumber = startVt,
            activeSubPhase = IfsSubPhase.ABB,
            pendingTargetVtNumber = null,
            queue = emptyList(),
            currentVideoId = nextVideo?.id,
            currentVideoName = nextVideo?.name,
            statusMessage = if (mode == IfsVideoMode.SV1) "▶️ ABA • A (ABB) Dimulai" else "▶️ PM1 • A (ABB) Dimulai",
            phaseStepVersion = _ifsState.value.phaseStepVersion + 1L
        )
        applyAudioModeToMusicPlayer(_audioMode.value)
        syncCurrentVideoToRepository(broadcastToHook = true, bumpPhaseVersion = false)
        playLinkedAxForVt(startVt)
    }

    // =========================================================================
    // MANAJEMEN KHUSUS MODE SV3 (1 VIDEO VL + RENTANG LOOP ACAK + GRUP VB)
    // =========================================================================

    fun setSv3VlVideo(videoId: String?) {
        _sv3VlVideoId.value = videoId
        saveAllToPrefs()
        if (_isIfsActive.value && _videoMode.value == IfsVideoMode.SV3 && _ifsState.value.activeSubPhase == IfsSubPhase.VL) {
            syncCurrentVideoToRepository(broadcastToHook = true)
        }
    }

    fun setSv3VlLoopRange(minLoops: Int, maxLoops: Int) {
        val safeMin = minLoops.coerceIn(1, 50)
        val safeMax = maxLoops.coerceIn(safeMin, 50)
        _sv3VlMinLoops.value = safeMin
        _sv3VlMaxLoops.value = safeMax
        sv3VlLoopCountDeck.reset()
        lastPickedSv3VlLoops = -1
        saveAllToPrefs()

        val curState = _ifsState.value
        val newTarget = drawNextSv3VlLoopCount()
        val adjustedCurrentLoop = curState.sv3CurrentVlLoop.coerceAtMost(newTarget)
        _ifsState.value = curState.copy(
            sv3CurrentVlLoop = adjustedCurrentLoop,
            sv3TargetVlLoops = newTarget,
            statusMessage = if (curState.videoMode == IfsVideoMode.SV3 && curState.activeSubPhase == IfsSubPhase.VL) {
                "🔁 SV3 • VL Putaran $adjustedCurrentLoop/$newTarget (Rentang Acak $safeMin..$safeMax)"
            } else curState.statusMessage
        )
    }

    fun setSv3VbVideoIds(videoIds: List<String>) {
        val cleanList = videoIds.filter { it.isNotBlank() }.distinct()
        _sv3VbVideoIds.value = cleanList
        sv3VbGroupDeck.reset()
        lastPickedSv3VbIdx = -1
        saveAllToPrefs()

        val curState = _ifsState.value
        val validActiveVb = curState.sv3ActiveVbNumber.coerceIn(1, cleanList.size.coerceAtLeast(1))
        _ifsState.value = curState.copy(
            sv3ActiveVbNumber = validActiveVb,
            sv3VbRemainingInCycle = cleanList.size,
            sv3VbTotalCount = cleanList.size
        )
        if (_isIfsActive.value && _videoMode.value == IfsVideoMode.SV3 && _ifsState.value.activeSubPhase == IfsSubPhase.VB) {
            syncCurrentVideoToRepository(broadcastToHook = true)
        }
    }

    fun toggleSv3VbVideo(videoId: String): Boolean {
        val cur = _sv3VbVideoIds.value.toMutableList()
        val added = if (cur.contains(videoId)) {
            cur.remove(videoId)
            false
        } else {
            cur.add(videoId)
            true
        }
        setSv3VbVideoIds(cur)
        return added
    }

    fun removeSv3VbVideo(videoId: String) {
        val cur = _sv3VbVideoIds.value.filter { it != videoId }
        setSv3VbVideoIds(cur)
    }

    // =========================================================================
    // MANAJEMEN MODE SV4 — MODE VS (VARIASI SUSUNAN ROLLING JIT + AUTO-DELETE)
    // =========================================================================

    fun setSv4LVideoIds(videoIds: List<String>) {
        val cleanList = videoIds.filter { it.isNotBlank() && !it.startsWith("vs_rolling_") }.distinct()
        try {
            val repo = VcamRepository.getInstance(appContext)
            val allSourceIds = repo.config.value.playlist
                .filterNot { repo.isRollingVsItem(it) }
                .map { it.id }
            manuallyRemovedSv4LIds.removeAll(cleanList.toSet())
            if (cleanList.isNotEmpty()) {
                val deselected = allSourceIds.filterNot { cleanList.contains(it) }
                manuallyRemovedSv4LIds.addAll(deselected)
            }
        } catch (_: Throwable) {}
        _sv4LVideoIds.value = cleanList
        saveAllToPrefs()
        _ifsState.value = _ifsState.value.copy(
            sv4TotalLCount = cleanList.size,
            sv4MinLoops = _sv4MinLoops.value,
            sv4MaxLoops = _sv4MaxLoops.value
        )
        if (_isIfsActive.value && _videoMode.value == IfsVideoMode.SV4 && _sv4VideoRunning.value) {
            startSv4RollingCycle(resetCounter = false)
        }
    }

    fun toggleSv4LVideo(videoId: String): Boolean {
        val cur = _sv4LVideoIds.value.toMutableList()
        val added = if (cur.contains(videoId)) {
            cur.remove(videoId)
            manuallyRemovedSv4LIds.add(videoId)
            false
        } else {
            cur.add(videoId)
            manuallyRemovedSv4LIds.remove(videoId)
            true
        }
        setSv4LVideoIds(cur)
        return added
    }

    fun removeSv4LVideo(videoId: String) {
        manuallyRemovedSv4LIds.add(videoId)
        val cur = _sv4LVideoIds.value.filter { it != videoId }
        setSv4LVideoIds(cur)
    }

    fun setSv4LoopRange(minLoops: Int, maxLoops: Int) {
        val safeMin = minLoops.coerceIn(1, 10)
        val safeMax = maxLoops.coerceIn(safeMin, 10)
        _sv4MinLoops.value = safeMin
        _sv4MaxLoops.value = safeMax
        saveAllToPrefs()

        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null }
        val sourcePlaylist = repo?.config?.value?.playlist?.filterNot { repo.isRollingVsItem(it) }.orEmpty()
        val configuredLIds = _sv4LVideoIds.value.filter { id -> sourcePlaylist.any { it.id == id } }
        val effectiveLIds = if (configuredLIds.isNotEmpty()) {
            configuredLIds
        } else {
            sourcePlaylist.filter { it.isEnabled }.map { it.id }.ifEmpty { sourcePlaylist.map { it.id } }
        }

        val curValid = isRecipeValidForCurrentL(sv4CurrentRecipe, effectiveLIds, safeMin, safeMax)
        val nextValid = isRecipeValidForCurrentL(sv4NextRecipe, effectiveLIds, safeMin, safeMax)

        if (!curValid) {
            sv4CurrentRecipe = null
            sv4CurrentVideoItem = null
            sv4CurrentFile = null
            lastSv4FormulaSignature = ""
        }
        if (!nextValid) {
            sv4NextRecipe = null
            sv4NextVideoItem = null
            sv4NextFile = null
        }

        val rangeLabel = if (safeMin == safeMax) "${safeMin}x" else "${safeMin}x..${safeMax}x"

        if (_videoMode.value == IfsVideoMode.SV4 && _sv4VideoRunning.value) {
            if (!curValid || !nextValid) {
                startSv4RollingCycle(resetCounter = false)
                return
            }
        }

        if (sv4CurrentRecipe == null && effectiveLIds.isNotEmpty()) {
            val previewNum = (sv4VsCounter.takeIf { it > 0 } ?: _ifsState.value.sv4CurrentVsNumber).coerceAtLeast(1)
            sv4CurrentRecipe = generateVsVariationRecipe(previewNum)
        }
        saveSv4RollingStateToPrefs()

        val curState = _ifsState.value
        _ifsState.value = curState.copy(
            sv4MinLoops = safeMin,
            sv4MaxLoops = safeMax,
            sv4ActiveFormula = sv4CurrentRecipe?.formulaText ?: "",
            sv4NextFormula = sv4NextRecipe?.formulaText ?: "",
            sv4NextReady = sv4NextVideoItem != null && sv4NextFile?.exists() == true,
            statusMessage = if (curState.videoMode == IfsVideoMode.SV4) {
                "🧬 SV4 Mode VS • Loop L diatur ke $rangeLabel"
            } else curState.statusMessage
        )
    }

    /**
     * Menghasilkan resep Variasi Susunan baru (VS1, VS2, VS3, dst.):
     * - Mengacak urutan L1, L2, L3, L4... (permutasi penuh dari daftar L)
     * - Mengacak jumlah looping setiap L (antara sv4MinLoops s/d sv4MaxLoops, default 1..2x)
     * - Mengombinasikan susunan inti N segmen dan susunan diperluas (N+1 segmen dengan penutup non-berulang)
     *   sehingga menghasilkan N! * K^N * (1 + (N-1)*K) variasi unik (misal 2.688 variasi pada 4 video loop 1-2x).
     * - Menjamin formula baru tidak kembar dengan formula sebelumnya.
     */
    private fun generateVsVariationRecipe(vsNumber: Int): VsVariationRecipe? {
        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null } ?: return null
        val sourcePlaylist = repo.config.value.playlist.filterNot { repo.isRollingVsItem(it) }
        if (sourcePlaylist.isEmpty()) return null

        val configuredLIds = _sv4LVideoIds.value.filter { id -> sourcePlaylist.any { it.id == id } }
        val effectiveLIds = if (configuredLIds.isNotEmpty()) {
            configuredLIds
        } else {
            sourcePlaylist.filter { it.isEnabled }.map { it.id }.ifEmpty { sourcePlaylist.map { it.id } }
        }
        if (effectiveLIds.isEmpty()) return null

        val minL = _sv4MinLoops.value.coerceAtLeast(1)
        val maxL = _sv4MaxLoops.value.coerceAtLeast(minL)
        val loopChoices = (maxL - minL + 1).coerceAtLeast(1)

        val currentConfigSig = "${effectiveLIds.joinToString(",")}|${minL}..${maxL}"
        if (sv4ConfigSignature.isNotBlank() && sv4ConfigSignature != currentConfigSig) {
            synchronized(sv4UsedFormulas) {
                sv4UsedFormulas.clear()
            }
        }
        sv4ConfigSignature = currentConfigSig

        val totalPossibleVariations = Sv4VariationCalculator.calculateMetrics(
            lCount = effectiveLIds.size,
            minLoops = minL,
            maxLoops = maxL
        ).totalVariations.coerceAtLeast(1L)

        synchronized(sv4UsedFormulas) {
            if (sv4UsedFormulas.size.toLong() >= totalPossibleVariations) {
                sv4UsedFormulas.clear()
            }
        }

        var chosenSegments: List<VsSegmentSpec> = emptyList()
        var chosenFormula = ""

        val now = System.currentTimeMillis()
        synchronized(sv4ChoreographyHistory) {
            sv4ChoreographyHistory.removeAll { (now - it.timestampMs) >= SV4_GUARD_WINDOW_MS }
        }
        val recent2mFormulas = synchronized(sv4ChoreographyHistory) { sv4ChoreographyHistory.map { it.formula }.toSet() }
        val recent2mSignatures = synchronized(sv4ChoreographyHistory) { sv4ChoreographyHistory.map { it.signature }.toSet() }

        val prevLastVideoId = (sv4NextRecipe ?: sv4CurrentRecipe)?.segments?.lastOrNull()?.videoId

        // Coba hingga 128 kali agar susunan + kombinasi loop tidak pernah mengulang koreografi dalam rentang 2 menit (120 detik)
        for (attempt in 0 until 128) {
            val rng = java.util.Random(System.nanoTime() xor (attempt.toLong() * 131L) xor (vsNumber.toLong() * 997L))
            val indices = effectiveLIds.indices.toMutableList()
            if (indices.size > 1) {
                indices.shuffle(rng)
                if (prevLastVideoId != null && effectiveLIds[indices[0]] == prevLastVideoId) {
                    val tmp = indices[0]
                    indices[0] = indices[1]
                    indices[1] = tmp
                }
            }
            val segs = indices.map { idx ->
                val lNum = idx + 1
                val vidId = effectiveLIds[idx]
                val loops = if (minL == maxL) {
                    minL
                } else {
                    minL + rng.nextInt(loopChoices)
                }
                VsSegmentSpec(
                    lNumber = lNum,
                    videoId = vidId,
                    loopCount = loops
                )
            }.toMutableList()

            // Variasi susunan diperluas (+1 segmen penutup dinamis hanya jika maxL > 1; jika diatur 1x murni maka setiap L diputar 1x tanpa pengulangan ekor)
            if (effectiveLIds.size >= 2 && maxL > 1) {
                val totalWeight = 1 + (effectiveLIds.size - 1) * loopChoices
                if (rng.nextInt(totalWeight) != 0) {
                    val lastIdx = indices.last()
                    val tailCandidates = effectiveLIds.indices.filter { it != lastIdx }
                    if (tailCandidates.isNotEmpty()) {
                        val tailIdx = tailCandidates[rng.nextInt(tailCandidates.size)]
                        val tailLoops = if (minL == maxL) minL else minL + rng.nextInt(loopChoices)
                        segs.add(
                            VsSegmentSpec(
                                lNumber = tailIdx + 1,
                                videoId = effectiveLIds[tailIdx],
                                loopCount = tailLoops
                            )
                        )
                    }
                }
            }

            // Jika hanya 1 video: variasikan loop count agar tidak sama dengan koreografi 2 menit terakhir
            if (effectiveLIds.size == 1 && minL < maxL) {
                val lastSig = synchronized(sv4ChoreographyHistory) { sv4ChoreographyHistory.lastOrNull()?.signature }
                val lastLoopCount = lastSig?.split(":")?.getOrNull(1)?.toIntOrNull()
                if (lastLoopCount != null) {
                    val alternatedLoop = if (lastLoopCount == minL) maxL else minL
                    segs[0] = segs[0].copy(loopCount = alternatedLoop)
                }
            }

            val formula = segs.joinToString(" + ") { "L${it.lNumber}(${it.loopCount}x)" }
            val signature = segs.joinToString(";") { "${it.videoId}:${it.loopCount}" }
            chosenSegments = segs
            chosenFormula = formula

            val isUsedIn2m = recent2mFormulas.contains(formula) || recent2mSignatures.contains(signature)
            val isAlreadyUsed = synchronized(sv4UsedFormulas) { sv4UsedFormulas.contains(formula) }

            // Guard 2 Menit: Wajib tidak kembar dalam rentang waktu 2 menit terakhir (120 detik)
            if (!isUsedIn2m && ((!isAlreadyUsed && formula != lastSv4FormulaSignature) || totalPossibleVariations <= recent2mFormulas.size)) {
                break
            }
            if (!isUsedIn2m && attempt >= 32) {
                // Jika formula siklus lama habis, prioritaskan syarat utama: BEBAS KEMBAR 2 MENIT!
                break
            }
        }

        val chosenSignature = chosenSegments.joinToString(";") { "${it.videoId}:${it.loopCount}" }
        val chRecord = Sv4ChoreographyRecord(
            vsNumber = vsNumber,
            formula = chosenFormula,
            signature = chosenSignature,
            firstVideoId = chosenSegments.firstOrNull()?.videoId ?: "",
            lastVideoId = chosenSegments.lastOrNull()?.videoId ?: "",
            timestampMs = now
        )
        synchronized(sv4ChoreographyHistory) {
            sv4ChoreographyHistory.add(chRecord)
        }

        lastSv4FormulaSignature = chosenFormula
        synchronized(sv4UsedFormulas) {
            if (sv4UsedFormulas.size >= 6000) {
                val first = sv4UsedFormulas.firstOrNull()
                if (first != null) sv4UsedFormulas.remove(first)
            }
            sv4UsedFormulas.add(chosenFormula)
        }
        saveSv4RollingStateToPrefs()

        Log.i(TAG, "🛡️ [SV4-2MIN-GUARD] Koreografi VS$vsNumber Digenerate: '$chosenFormula' (Riwayat 2m aktif: ${getSv4ChoreographyHistoryInLast2m().size} variasi)")

        return VsVariationRecipe(
            vsNumber = vsNumber,
            segments = chosenSegments,
            formulaText = chosenFormula
        )
    }

    /**
     * Mengekspansi resep VS menjadi daftar VideoItem berurutan untuk digabungkan oleh SmartVideoStitcher.
     * Contoh: L2(2x) + L1(1x) -> [Video_L2, Video_L2, Video_L1]
     */
    private fun expandRecipeToClipList(recipe: VsVariationRecipe): List<VideoItem> {
        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null } ?: return emptyList()
        val sourcePlaylist = repo.config.value.playlist.filterNot { repo.isRollingVsItem(it) }
        val expanded = mutableListOf<VideoItem>()
        for (seg in recipe.segments) {
            val item = sourcePlaylist.firstOrNull { it.id == seg.videoId }
                ?: _sv4LVideoIds.value.getOrNull(seg.lNumber - 1)?.let { lId -> sourcePlaylist.firstOrNull { it.id == lId } }
                ?: continue
            repeat(seg.loopCount.coerceIn(1, 10)) {
                expanded.add(item)
            }
        }
        return expanded
    }

    /**
     * Memulai atau melanjutkan siklus Rolling JIT Stitcher untuk Mode SV4 (Mode VS):
     * - Jika [resetCounter] == false (mis. saat keluar/buka ulang APK atau update APK di VS1000):
     *   1. Jika file VS aktif (mis. vs_rolling_1000.mp4) masih ada di disk, langsung gunakan kembali tanpa mereset ke VS1/VS2!
     *   2. Jika file VS berikutnya (mis. vs_rolling_1001.mp4) juga sudah ada di disk, langsung masukkan sebagai #2 SIAP.
     *   3. Jika VS berikutnya belum selesai digabung saat keluar/update APK, tetap putar VS1000 dan lanjutkan penggabungan VS1001 di background.
     *   4. Bahkan jika file sementara MP4 sempat terhapus oleh sistem, tetap lanjutkan dari nomor VS terakhir yang tersimpan (mis. VS1000 -> VS1001).
     * - Jika [resetCounter] == true (pengguna menekan tombol ⚡ Rakit Ulang secara manual):
     *   Mereset counter kembali ke VS1 & VS2.
     */
    fun startSv4RollingCycle(resetCounter: Boolean = false) {
        if (!_isIfsActive.value) {
            _isIfsActive.value = true
            prefs.edit().putBoolean(KEY_IFS_ENABLED, true).apply()
        }
        ensureDefaultVideoSlotsPopulated()

        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null } ?: return
        val sourcePlaylist = repo.config.value.playlist.filterNot { repo.isRollingVsItem(it) }
        if (sourcePlaylist.isEmpty()) {
            sv4StitchJob?.cancel()
            sv4StitchJob = null
            sv4PlaybackWatchdogJob?.cancel()
            sv4PlaybackWatchdogJob = null
            _ifsState.value = _ifsState.value.copy(
                isEnabled = _isIfsActive.value,
                videoMode = IfsVideoMode.SV4,
                activeSubPhase = IfsSubPhase.VS,
                statusMessage = "⚠️ SV4 Mode VS: Tambahkan minimal 2 video L1..Ln di Daftar Video terlebih dahulu",
                sv4TotalLCount = 0
            )
            return
        }

        if (_sv4LVideoIds.value.isEmpty()) {
            _sv4LVideoIds.value = sourcePlaylist.map { it.id }
            saveAllToPrefs()
        }

        val configuredLIds = _sv4LVideoIds.value.filter { id -> sourcePlaylist.any { it.id == id } }
        val effectiveLIds = if (configuredLIds.isNotEmpty()) {
            configuredLIds
        } else {
            sourcePlaylist.filter { it.isEnabled }.map { it.id }.ifEmpty { sourcePlaylist.map { it.id } }
        }

        // JALUR RESUME TANPA RESET (Saat Keluar/Masuk APK, Update APK, atau Sinkronisasi Ulang)
        if (!resetCounter) {
            val existingCurRecipe = sv4CurrentRecipe
            val existingCurItem = sv4CurrentVideoItem
            val existingCurFile = sv4CurrentFile ?: existingCurItem?.let { File(it.path) }

            if (existingCurRecipe != null &&
                existingCurItem != null &&
                existingCurFile != null &&
                existingCurFile.exists() &&
                existingCurFile.length() > 0L &&
                isRecipeValidForCurrentL(existingCurRecipe, effectiveLIds)
            ) {
                sv4CurrentFile = existingCurFile
                sv4IsInitialWarmupActive = false
                sv4WaitingForNextReady = false
                sv4LastPromotedTimeMs = System.currentTimeMillis()

                if (_audioMode.value != IfsAudioMode.SV4_VS) {
                    applyAudioModeToMusicPlayer(_audioMode.value)
                }
                try {
                    repo.selectRollingVsVideoSync(vsVideo = existingCurItem, broadcastToHook = true)
                    scope.launch(Dispatchers.IO) {
                        val dirs = listOf(repo.getCamera1Directory(), repo.getInternalCamera1Directory())
                        for (d in dirs) {
                            try {
                                val autoNextFile = java.io.File(d, "auto_next.txt")
                                autoNextFile.writeText("OFF")
                                autoNextFile.setReadable(true, false)
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
                if (_audioMode.value == IfsAudioMode.SV4_VS) {
                    ensureAudioActiveInPreview()
                } else if (!repo.config.value.isMp3AudioPaused) {
                    musicPlayer.ensureSrPlaying()
                }

                if (sv4PlaybackWatchdogJob?.isActive != true) {
                    scheduleSv4PlaybackWatchdog(existingCurItem)
                }

                val existingNextRecipe = sv4NextRecipe
                val existingNextItem = sv4NextVideoItem
                val existingNextFile = sv4NextFile ?: existingNextItem?.let { File(it.path) }

                if (existingNextRecipe != null &&
                    existingNextItem != null &&
                    existingNextFile != null &&
                    existingNextFile.exists() &&
                    existingNextFile.length() > 0L &&
                    existingNextRecipe.vsNumber > existingCurRecipe.vsNumber &&
                    isRecipeValidForCurrentL(existingNextRecipe, effectiveLIds)
                ) {
                    // VS aktif (mis. VS1000) & VS berikutnya (mis. VS1001) KEDUANYA masih utuh di penyimpanan!
                    sv4NextFile = existingNextFile
                    sv4VsCounter = maxOf(sv4VsCounter, existingNextRecipe.vsNumber)
                    try {
                        repo.insertNextQueuedVsIntoPlaylistSync(existingNextItem)
                    } catch (_: Throwable) {}

                    _ifsState.value = _ifsState.value.copy(
                        isEnabled = true,
                        videoMode = IfsVideoMode.SV4,
                        activeVtNumber = existingCurRecipe.vsNumber,
                        activeSubPhase = IfsSubPhase.VS,
                        currentVideoId = existingCurItem.id,
                        currentVideoName = existingCurItem.name,
                        sv4CurrentVsNumber = existingCurRecipe.vsNumber,
                        sv4ActiveFormula = existingCurRecipe.formulaText,
                        sv4NextVsNumber = existingNextRecipe.vsNumber,
                        sv4NextFormula = existingNextRecipe.formulaText,
                        sv4IsStitchingNext = false,
                        sv4NextStitchPercent = 100,
                        sv4NextReady = true,
                        sv4AutoDeletedCount = sv4AutoDeletedFilesCount,
                        sv4TotalLCount = _sv4LVideoIds.value.size,
                        statusMessage = "🎬 Play VS${existingCurRecipe.vsNumber} [${existingCurRecipe.formulaText}] • ✅ VS${existingNextRecipe.vsNumber} [${existingNextRecipe.formulaText}] Siap di Daftar (#2)!"
                    )
                    saveSv4RollingStateToPrefs()
                    return
                } else {
                    // VS aktif (mis. VS1000) masih utuh di disk, sedangkan VS berikutnya (VS1001) belum selesai digabung saat keluar/update APK
                    val nextVsNum = existingNextRecipe?.vsNumber?.takeIf { it > existingCurRecipe.vsNumber }
                        ?: (existingCurRecipe.vsNumber + 1)
                    sv4VsCounter = maxOf(sv4VsCounter, nextVsNum)
                    val nextRec = if (existingNextRecipe != null &&
                        existingNextRecipe.vsNumber == nextVsNum &&
                        isRecipeValidForCurrentL(existingNextRecipe, effectiveLIds) &&
                        existingNextRecipe.segments.isNotEmpty()
                    ) {
                        existingNextRecipe
                    } else {
                        generateVsVariationRecipe(nextVsNum)
                    }
                    sv4NextRecipe = nextRec
                    sv4NextVideoItem = null
                    sv4NextFile = null

                    _ifsState.value = _ifsState.value.copy(
                        isEnabled = true,
                        videoMode = IfsVideoMode.SV4,
                        activeVtNumber = existingCurRecipe.vsNumber,
                        activeSubPhase = IfsSubPhase.VS,
                        currentVideoId = existingCurItem.id,
                        currentVideoName = existingCurItem.name,
                        sv4CurrentVsNumber = existingCurRecipe.vsNumber,
                        sv4ActiveFormula = existingCurRecipe.formulaText,
                        sv4NextVsNumber = nextVsNum,
                        sv4NextFormula = nextRec?.formulaText ?: "",
                        sv4IsStitchingNext = nextRec != null,
                        sv4NextStitchPercent = 0,
                        sv4NextReady = false,
                        sv4AutoDeletedCount = sv4AutoDeletedFilesCount,
                        sv4TotalLCount = _sv4LVideoIds.value.size,
                        statusMessage = "🎬 Play VS${existingCurRecipe.vsNumber} [${existingCurRecipe.formulaText}] • ⚙️ Melanjutkan Gabung VS$nextVsNum di Latar..."
                    )
                    saveSv4RollingStateToPrefs()
                    if (nextRec != null && sv4StitchJob?.isActive != true) {
                        launchNextVsBackgroundStitch(nextRec)
                    }
                    return
                }
            }
        }

        sv4StitchJob?.cancel()
        sv4StitchJob = null
        sv4PlaybackWatchdogJob?.cancel()
        sv4PlaybackWatchdogJob = null
        sv4IsInitialWarmupActive = false
        sv4WaitingForNextReady = false

        repo.cleanupAllRollingVsFromPlaylistSync()

        if (resetCounter) {
            sv4VsCounter = 0
            sv4AutoDeletedFilesCount = 0
            lastSv4FormulaSignature = ""
            synchronized(sv4UsedFormulas) {
                sv4UsedFormulas.clear()
            }
            sv4CurrentRecipe = null
            sv4CurrentVideoItem = null
            sv4CurrentFile = null
            sv4NextRecipe = null
            sv4NextVideoItem = null
            sv4NextFile = null
            saveSv4RollingStateToPrefs()
            cleanupAllRollingVsFilesExcept(keepFileNames = emptySet())
        }

        // Jika bukan reset manual (resetCounter == false), lanjutkan dari nomor VS terakhir yang tersimpan (mis. VS1000)!
        val vs1Num = if (resetCounter) {
            ++sv4VsCounter
        } else {
            val savedActiveNum = sv4CurrentRecipe?.vsNumber ?: sv4VsCounter
            if (savedActiveNum > 0) {
                sv4VsCounter = maxOf(sv4VsCounter, savedActiveNum)
                savedActiveNum
            } else {
                ++sv4VsCounter
            }
        }

        val recipe1 = if (!resetCounter &&
            sv4CurrentRecipe != null &&
            sv4CurrentRecipe!!.vsNumber == vs1Num &&
            sv4CurrentRecipe!!.segments.isNotEmpty() &&
            isRecipeValidForCurrentL(sv4CurrentRecipe, effectiveLIds)
        ) {
            sv4CurrentRecipe!!
        } else {
            generateVsVariationRecipe(vs1Num) ?: return
        }

        val clips1 = expandRecipeToClipList(recipe1)
        if (clips1.isEmpty()) return

        sv4IsInitialWarmupActive = true
        sv4WaitingForNextReady = false
        sv4CurrentRecipe = recipe1
        sv4CurrentVideoItem = null
        sv4CurrentFile = null
        sv4NextRecipe = null
        sv4NextVideoItem = null
        sv4NextFile = null
        saveSv4RollingStateToPrefs()

        // Segera aktifkan mode IFS di VcamRepository dan matikan auto_next Standar tanpa mereset video 2x!
        if (_audioMode.value != IfsAudioMode.SV4_VS) {
            applyAudioModeToMusicPlayer(_audioMode.value)
        }
        try {
            repo.applyIfsModeStateSync(enabled = true, activeVideo = null)
            scope.launch(Dispatchers.IO) {
                val dirs = listOf(repo.getCamera1Directory(), repo.getInternalCamera1Directory())
                for (d in dirs) {
                    try {
                        val autoNextFile = java.io.File(d, "auto_next.txt")
                        autoNextFile.writeText("OFF")
                        autoNextFile.setReadable(true, false)
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
        if (_audioMode.value == IfsAudioMode.SV4_VS) {
            ensureAudioActiveInPreview()
        } else if (!repo.config.value.isMp3AudioPaused) {
            musicPlayer.ensureSrPlaying()
        }

        _ifsState.value = _ifsState.value.copy(
            isEnabled = _isIfsActive.value,
            videoMode = IfsVideoMode.SV4,
            activeVtNumber = vs1Num,
            activeSubPhase = IfsSubPhase.VS,
            currentVideoId = "",
            currentVideoName = "⏳ Merakit [VS$vs1Num] ${recipe1.formulaText}...",
            statusMessage = "⚡ Menggabungkan VS$vs1Num [${recipe1.formulaText}] ke Daftar...",
            sv4CurrentVsNumber = vs1Num,
            sv4ActiveFormula = recipe1.formulaText,
            sv4NextVsNumber = vs1Num + 1,
            sv4NextFormula = "Menunggu VS$vs1Num selesai digabung...",
            sv4IsStitchingNext = true,
            sv4NextStitchPercent = 0,
            sv4NextReady = false,
            sv4AutoDeletedCount = sv4AutoDeletedFilesCount,
            sv4TotalLCount = _sv4LVideoIds.value.size
        )

        sv4StitchJob = sv4Scope.launch {
            val targetFile1 = "vs_rolling_${vs1Num}.mp4"
            val result1 = SmartVideoStitcher.stitchVariationList(
                context = appContext,
                orderedClips = clips1,
                targetFileName = targetFile1,
                displayTitle = "🎬 [VS$vs1Num] ${recipe1.formulaText}",
                onProgress = { status, pct ->
                    if (isActive && _videoMode.value == IfsVideoMode.SV4) {
                        _ifsState.value = _ifsState.value.copy(
                            sv4IsStitchingNext = true,
                            sv4NextStitchPercent = pct,
                            statusMessage = "⚙️ Gabung VS$vs1Num [${recipe1.formulaText}] • $status ($pct%)"
                        )
                    }
                }
            )

            if (!isActive || !_isIfsActive.value || _videoMode.value != IfsVideoMode.SV4) return@launch

            if (result1.success && result1.stitchedItem != null && result1.outputFile != null) {
                sv4IsInitialWarmupActive = false
                sv4WaitingForNextReady = false
                sv4CurrentRecipe = recipe1
                sv4CurrentVideoItem = result1.stitchedItem
                sv4CurrentFile = result1.outputFile
                sv4LastPromotedTimeMs = System.currentTimeMillis()

                // Masukkan VS ke dalam Daftar Video (#1 AKTIF) & langsung Play!
                try {
                    VcamRepository.getInstance(appContext).selectRollingVsVideoSync(
                        vsVideo = result1.stitchedItem,
                        broadcastToHook = true
                    )
                } catch (_: Throwable) {}

                scheduleSv4PlaybackWatchdog(result1.stitchedItem)

                val vs2Num = maxOf(sv4VsCounter + 1, vs1Num + 1)
                sv4VsCounter = vs2Num
                val recipe2 = generateVsVariationRecipe(vs2Num)
                sv4NextRecipe = recipe2
                saveSv4RollingStateToPrefs()

                val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null }
                val curCfg = repo?.config?.value
                _ifsState.value = _ifsState.value.copy(
                    isEnabled = true,
                    activeVtNumber = vs1Num,
                    activeSubPhase = IfsSubPhase.VS,
                    currentVideoId = result1.stitchedItem.id,
                    currentVideoName = result1.stitchedItem.name,
                    sv4CurrentVsNumber = vs1Num,
                    sv4ActiveFormula = recipe1.formulaText,
                    sv4NextVsNumber = vs2Num,
                    sv4NextFormula = recipe2?.formulaText ?: "",
                    sv4IsStitchingNext = recipe2 != null,
                    sv4NextStitchPercent = 0,
                    sv4NextReady = false,
                    sv4TwoMinuteGuardActive = true,
                    sv4GuardChoreographyCount2m = getSv4ChoreographyHistoryInLast2m().size,
                    sv4GuardAudioCount2m = getSv4AudioChoreographyHistoryInLast2m().size,
                    sv4GuardCurrentZoom = if (curCfg?.isDnaZoomEnabled == true) curCfg.sessionDnaCustomZoom else 1.00f,
                    sv4GuardCurrentDnaBadge = if (curCfg?.enableDnaModule == true) curCfg.activeSessionDna.badgeText else "OFF",
                    statusMessage = "🎬 Play VS$vs1Num [${recipe1.formulaText}] • 🛡️ Guard 2m: Bebas Kembar • ⚙️ Merakit VS$vs2Num...",
                    phaseStepVersion = _ifsState.value.phaseStepVersion + 1L
                )

                // Sambil play VS aktif, beri jeda 450ms agar MediaPlayer memutar awal VS tanpa gangguan I/O, lalu gabungkan VS berikutnya di background!
                if (recipe2 != null) {
                    kotlinx.coroutines.delay(450L)
                    stitchNextVsInternal(recipe2)
                }
            } else {
                sv4IsInitialWarmupActive = false
                Log.w(TAG, "⚠️ Gagal menggabungkan VS$vs1Num: ${result1.errorMessage}")
                _ifsState.value = _ifsState.value.copy(
                    sv4IsStitchingNext = false,
                    statusMessage = "⚠️ Gagal merakit VS$vs1Num: ${result1.errorMessage ?: "Error"}"
                )
            }
        }
    }

    /**
     * Dipanggil saat pengguna menggeser seekbar atau menekan tombol ⏩ -3d Akhir.
     * Menyesuaikan timer watchdog SV4 agar langsung sinkron dengan sisa durasi nyata!
     */
    fun onVideoSeeked(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        sv4WatchdogPlayedMs = safePos
        sv4LastPromotedTimeMs = (System.currentTimeMillis() - safePos).coerceAtLeast(1L)
        val curItem = sv4CurrentVideoItem
        if (_isIfsActive.value && _videoMode.value == IfsVideoMode.SV4 && curItem != null) {
            if (sv4PlaybackWatchdogJob?.isActive != true) {
                scheduleSv4PlaybackWatchdog(curItem, initialPlayedMs = sv4WatchdogPlayedMs)
            }
        }
    }

    /**
     * Watchdog durasi otomatis untuk setiap video gabungan VS yang sedang aktif diputar.
     * Menjamin bahwa meskipun MediaPlayer di pratinjau tertutup atau tidak memancarkan OnCompletionListener
     * pada frame terakhir hasil muxer, transisi otomatis VS(k) -> VS(k+1) tetap berjalan 100% tanpa klik manual.
     */
    private fun scheduleSv4PlaybackWatchdog(activeItem: VideoItem, initialPlayedMs: Long = 0L) {
        sv4PlaybackWatchdogJob?.cancel()
        val expectedDurationMs = activeItem.durationMs.coerceIn(2_000L, 3_600_000L)
        val targetItemId = activeItem.id
        val targetItemPath = activeItem.path
        sv4WatchdogPlayedMs = initialPlayedMs.coerceIn(0L, expectedDurationMs)
        sv4PlaybackWatchdogJob = sv4Scope.launch {
            val stepMs = 200L
            while (isActive && _isIfsActive.value && _videoMode.value == IfsVideoMode.SV4 && _sv4VideoRunning.value) {
                kotlinx.coroutines.delay(stepMs)
                if (sv4CurrentVideoItem?.id != targetItemId) {
                    return@launch
                }
                val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null }
                val isPaused = repo?.config?.value?.isVideoPaused ?: false
                if (!isPaused) {
                    val now = System.currentTimeMillis()
                    val isLiveHookReporting = repo != null && (now - repo.lastRealPlaybackTime.value) < 3000L
                    val isPreviewReporting = repo != null && (now - repo.lastPreviewPlaybackTime.value) < 3000L
                    val realPos = repo?.realVideoPositionMs?.value ?: 0L
                    val realDur = repo?.realVideoDurationMs?.value ?: 0L
                    val effectiveDurMs = if (realDur > 2_000L) realDur else expectedDurationMs

                    if (isLiveHookReporting && realPos > 0L) {
                        sv4WatchdogPlayedMs = realPos
                    } else if (isPreviewReporting && realPos > 0L) {
                        sv4WatchdogPlayedMs = realPos
                    } else if (!isLiveHookReporting && !isPreviewReporting) {
                        sv4WatchdogPlayedMs += stepMs
                    }

                    // KRUSIAL: Jika Hook/Kamera sedang aktif memutar video (isLiveHookReporting),
                    // JANGAN PERNAH memotong video di tengah jalan! Biarkan OnCompletionListener asli yang memicu transisi.
                    // Watchdog hanya bertindak sebagai penyelamat jika kamera/player sama sekali tidak melapor (>3.5 detik mati)
                    if (!isLiveHookReporting) {
                        val triggerThresholdMs = effectiveDurMs + 1500L
                        if (sv4WatchdogPlayedMs >= triggerThresholdMs) {
                            if (sv4CurrentVideoItem?.id == targetItemId && _isIfsActive.value && _videoMode.value == IfsVideoMode.SV4 && _sv4VideoRunning.value) {
                                Log.i(TAG, "⏱️ [SV4-AUTO-WATCHDOG] Durasi $targetItemId (${effectiveDurMs}ms) selesai tanpa hook aktif, lanjut ke VS berikutnya!")
                                onVideoCompleted(
                                    forceAdvanceFromBl = false,
                                    broadcastToHook = true,
                                    completedVideoPath = targetItemPath
                                )
                            }
                            if (sv4CurrentVideoItem?.id != targetItemId) {
                                return@launch
                            } else {
                                sv4WatchdogPlayedMs = (effectiveDurMs - 200L).coerceAtLeast(0L)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Menggabungkan variasi berikutnya (VS2, VS3, dst.) di background selagi VS saat ini sedang diputar.
     */
    private fun launchNextVsBackgroundStitch(nextRecipe: VsVariationRecipe) {
        sv4StitchJob?.cancel()
        sv4StitchJob = sv4Scope.launch {
            if (!sv4WaitingForNextReady) {
                kotlinx.coroutines.delay(450L)
            }
            stitchNextVsInternal(nextRecipe)
        }
    }

    private suspend fun stitchNextVsInternal(nextRecipe: VsVariationRecipe) {
        val clips = expandRecipeToClipList(nextRecipe)
        if (clips.isEmpty()) return

        val curVs = sv4CurrentRecipe?.vsNumber ?: _ifsState.value.sv4CurrentVsNumber.coerceAtLeast(1)
        val curFormula = sv4CurrentRecipe?.formulaText ?: _ifsState.value.sv4ActiveFormula
        sv4NextRecipe = nextRecipe
        saveSv4RollingStateToPrefs()

        _ifsState.value = _ifsState.value.copy(
            sv4NextVsNumber = nextRecipe.vsNumber,
            sv4NextFormula = nextRecipe.formulaText,
            sv4IsStitchingNext = true,
            sv4NextStitchPercent = 0,
            sv4NextReady = false,
            statusMessage = "🎬 Play VS$curVs [$curFormula] • ⚙️ Menggabungkan VS${nextRecipe.vsNumber} [${nextRecipe.formulaText}]..."
        )

        val targetFileName = "vs_rolling_${nextRecipe.vsNumber}.mp4"
        val res = SmartVideoStitcher.stitchVariationList(
            context = appContext,
            orderedClips = clips,
            targetFileName = targetFileName,
            displayTitle = "⏳ [VS${nextRecipe.vsNumber} Siap] ${nextRecipe.formulaText}",
            onProgress = { _, pct ->
                if (_videoMode.value == IfsVideoMode.SV4) {
                    _ifsState.value = _ifsState.value.copy(
                        sv4IsStitchingNext = true,
                        sv4NextStitchPercent = pct,
                        statusMessage = "🎬 Play VS$curVs [$curFormula] • ⚙️ Gabung VS${nextRecipe.vsNumber} ($pct%)"
                    )
                }
            }
        )

        if (!_isIfsActive.value || _videoMode.value != IfsVideoMode.SV4) return

        if (res.success && res.stitchedItem != null && res.outputFile != null) {
            sv4NextRecipe = nextRecipe
            sv4NextVideoItem = res.stitchedItem
            sv4NextFile = res.outputFile
            saveSv4RollingStateToPrefs()

            // Masukkan VS(k+1) yang sudah selesai digabung ke dalam Daftar Video (#2 ANTRE) selagi VS(k) masih di-play!
            try {
                VcamRepository.getInstance(appContext).insertNextQueuedVsIntoPlaylistSync(res.stitchedItem)
            } catch (_: Throwable) {}

            _ifsState.value = _ifsState.value.copy(
                sv4NextVsNumber = nextRecipe.vsNumber,
                sv4NextFormula = nextRecipe.formulaText,
                sv4IsStitchingNext = false,
                sv4NextStitchPercent = 100,
                sv4NextReady = true,
                statusMessage = "🎬 Play VS$curVs [$curFormula] • ✅ VS${nextRecipe.vsNumber} [${nextRecipe.formulaText}] Masuk Daftar (#2)!"
            )

            // Beritahu Dual-Slot Engine untuk pre-decode Frame #1 dari VS berikutnya di slot standby!
            try {
                VcamRepository.getInstance(appContext).broadcastControlEvent(
                    action = "IFS_QUEUE_TRIGGER",
                    extraSource = "SV4_NEXT_VS_READY"
                )
            } catch (_: Throwable) {}

            // KRUSIAL: Jika VS saat ini (misal VS2) sudah lebih dulu selesai diputar selagi VS(k+1) (misal VS3)
            // baru saja merampungkan proses penggabungan, LANGSUNG promosikan dan Play VS(k+1) otomatis
            // tanpa menunggu klik manual!
            if (sv4WaitingForNextReady) {
                sv4WaitingForNextReady = false
                Log.i(TAG, "🚀 [SV4-AUTO-PROMOTE] VS${nextRecipe.vsNumber} selesai digabung saat VS$curVs sudah selesai, langsung Play VS${nextRecipe.vsNumber} otomatis!")
                onVideoCompleted(forceAdvanceFromBl = true, broadcastToHook = true)
            }
        } else {
            _ifsState.value = _ifsState.value.copy(
                sv4IsStitchingNext = false,
                sv4NextReady = false,
                statusMessage = "🎬 Play VS$curVs [$curFormula] • ⚠️ Gagal gabung VS${nextRecipe.vsNumber}, mencoba variasi baru..."
            )
            // Jika gagal merakit variasi ini, jangan berhenti! Langsung coba rakit variasi berikutnya secara otomatis
            kotlinx.coroutines.delay(500L)
            if (_isIfsActive.value && _videoMode.value == IfsVideoMode.SV4 && sv4NextVideoItem == null) {
                val retryNum = maxOf(sv4VsCounter + 1, nextRecipe.vsNumber + 1)
                sv4VsCounter = retryNum
                val retryRecipe = generateVsVariationRecipe(retryNum)
                if (retryRecipe != null) {
                    stitchNextVsInternal(retryRecipe)
                }
            }
        }
    }

    /**
     * Membatalkan proses penggabungan SV4, menghapus entri VS dari Daftar Video,
     * dan membersihkan file sementara vs_rolling_*.mp4 tanpa menghapus nomor counter progres VS.
     */
    fun cancelAndCleanupSv4RollingFiles() {
        sv4StitchJob?.cancel()
        sv4StitchJob = null
        sv4PlaybackWatchdogJob?.cancel()
        sv4PlaybackWatchdogJob = null
        sv4IsInitialWarmupActive = false
        sv4WaitingForNextReady = false
        sv4CurrentVideoItem = null
        sv4CurrentFile = null
        sv4NextVideoItem = null
        sv4NextFile = null
        saveSv4RollingStateToPrefs()
        try {
            VcamRepository.getInstance(appContext).cleanupAllRollingVsFromPlaylistSync()
        } catch (_: Throwable) {}
        cleanupAllRollingVsFilesExcept(keepFileNames = emptySet())
    }

    private fun cleanupAllRollingVsFilesExcept(keepFileNames: Set<String>) {
        sv4Scope.launch {
            try {
                val camDir = File(appContext.filesDir, "Camera1")
                val files = camDir.listFiles() ?: return@launch
                for (f in files) {
                    if (f.name.startsWith("vs_rolling_") && f.name.endsWith(".mp4") && !keepFileNames.contains(f.name)) {
                        f.delete()
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Membersihkan ID video & audio usang yang sudah dihapus dari playlist.
     * Jika seluruh slot pada paket aktif kosong sedangkan playlist memiliki video baru,
     * otomatis mengisi slot default dari video sesi baru agar tidak pernah memutar video sesi lama.
     */
    fun ensureDefaultVideoSlotsPopulated() {
        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null } ?: return
        val playlist = repo.config.value.playlist
        val sourcePlaylist = playlist.filterNot { repo.isRollingVsItem(it) }
        val audioList = repo.config.value.audioPlaylist
        val validIds = sourcePlaylist.map { it.id }.toSet()
        val validAudioIds = audioList.map { it.id }.toSet()

        var changed = false
        val vtm = _vtmPackage.value
        val cleanVtmAbb = vtm.abbVideoId?.takeIf { validIds.contains(it) }
        val cleanVtmBl = vtm.blVideoId?.takeIf { validIds.contains(it) }
        val cleanVtmBbt = vtm.bbtVideoId?.takeIf { validIds.contains(it) }
        val cleanVtmAudio = vtm.linkedAudioId?.takeIf { validAudioIds.contains(it) }
        if (cleanVtmAbb != vtm.abbVideoId || cleanVtmBl != vtm.blVideoId || cleanVtmBbt != vtm.bbtVideoId || cleanVtmAudio != vtm.linkedAudioId) {
            _vtmPackage.value = vtm.copy(
                abbVideoId = cleanVtmAbb,
                blVideoId = cleanVtmBl,
                bbtVideoId = cleanVtmBbt,
                linkedAudioId = cleanVtmAudio
            )
            changed = true
        }

        val updatedVt = _vtPackages.value.map { pkg ->
            val cleanAbb = pkg.abbVideoId?.takeIf { validIds.contains(it) }
            val cleanBl = pkg.blVideoId?.takeIf { validIds.contains(it) }
            val cleanBbt = pkg.bbtVideoId?.takeIf { validIds.contains(it) }
            val cleanAudio = pkg.linkedAudioId?.takeIf { validAudioIds.contains(it) }
            if (cleanAbb != pkg.abbVideoId || cleanBl != pkg.blVideoId || cleanBbt != pkg.bbtVideoId || cleanAudio != pkg.linkedAudioId) {
                changed = true
                pkg.copy(
                    abbVideoId = cleanAbb,
                    blVideoId = cleanBl,
                    bbtVideoId = cleanBbt,
                    linkedAudioId = cleanAudio
                )
            } else pkg
        }
        if (changed) {
            _vtPackages.value = updatedVt
        }

        val cleanSv3Vl = _sv3VlVideoId.value?.takeIf { validIds.contains(it) }
        if (cleanSv3Vl != _sv3VlVideoId.value) {
            _sv3VlVideoId.value = cleanSv3Vl
            changed = true
        }
        val cleanSv3Vb = _sv3VbVideoIds.value.filter { validIds.contains(it) }
        if (cleanSv3Vb.size != _sv3VbVideoIds.value.size) {
            _sv3VbVideoIds.value = cleanSv3Vb
            sv3VbGroupDeck.reset()
            lastPickedSv3VbIdx = -1
            changed = true
        }

        if (sourcePlaylist.isNotEmpty()) {
            val curVtm = _vtmPackage.value
            if (curVtm.abbVideoId == null && curVtm.blVideoId == null && curVtm.bbtVideoId == null) {
                val defaultAbb = sourcePlaylist.getOrNull(0)?.id
                val defaultBl = (sourcePlaylist.getOrNull(1) ?: sourcePlaylist.getOrNull(0))?.id
                val defaultBbt = (sourcePlaylist.getOrNull(2) ?: sourcePlaylist.getOrNull(1) ?: sourcePlaylist.getOrNull(0))?.id
                _vtmPackage.value = curVtm.copy(
                    abbVideoId = defaultAbb,
                    blVideoId = defaultBl,
                    bbtVideoId = defaultBbt
                )
                changed = true
            }

            val curVtList = _vtPackages.value
            if (curVtList.all { it.abbVideoId == null && it.blVideoId == null && it.bbtVideoId == null }) {
                _vtPackages.value = curVtList.mapIndexed { idx, pkg ->
                    val baseItem = sourcePlaylist.getOrNull(idx % sourcePlaylist.size) ?: sourcePlaylist.first()
                    val blItem = sourcePlaylist.getOrNull((idx + 1) % sourcePlaylist.size) ?: baseItem
                    val bbtItem = sourcePlaylist.getOrNull((idx + 2) % sourcePlaylist.size) ?: blItem
                    pkg.copy(
                        abbVideoId = baseItem.id,
                        blVideoId = blItem.id,
                        bbtVideoId = bbtItem.id
                    )
                }
                changed = true
            }

            if (_sv3VlVideoId.value == null) {
                _sv3VlVideoId.value = sourcePlaylist.first().id
                changed = true
            }
            if (_sv3VbVideoIds.value.isEmpty()) {
                val defaultVbIds = if (sourcePlaylist.size > 1) {
                    sourcePlaylist.drop(1).map { it.id }
                } else {
                    listOf(sourcePlaylist.first().id)
                }
                _sv3VbVideoIds.value = defaultVbIds
                sv3VbGroupDeck.reset()
                lastPickedSv3VbIdx = -1
                changed = true
            }
        }

        if (sourcePlaylist.isEmpty()) {
            manuallyRemovedSv4LIds.clear()
        } else {
            manuallyRemovedSv4LIds.retainAll(validIds)
        }
        val cleanSv4L = _sv4LVideoIds.value.filter { validIds.contains(it) }.toMutableList()
        // Otomatis sertakan video yang baru diupload pengguna ke daftar L kecuali pengguna pernah melepasnya secara manual
        for (item in sourcePlaylist) {
            if (!cleanSv4L.contains(item.id) && !manuallyRemovedSv4LIds.contains(item.id)) {
                cleanSv4L.add(item.id)
            }
        }
        if (cleanSv4L.isEmpty() && sourcePlaylist.isNotEmpty()) {
            cleanSv4L.addAll(sourcePlaylist.map { it.id })
        }
        if (cleanSv4L != _sv4LVideoIds.value) {
            _sv4LVideoIds.value = cleanSv4L
            changed = true
        }

        val sourceAudios = audioList.filterNot { repo.isRollingVsAudioItem(it) }
        val validSourceAudioIds = sourceAudios.map { it.id }.toSet()

        if (sourceAudios.isEmpty()) {
            manuallyRemovedSv4AudioLIds.clear()
        } else {
            manuallyRemovedSv4AudioLIds.retainAll(validSourceAudioIds)
        }
        val cleanSv4AudioL = _sv4AudioLIds.value.filter { validSourceAudioIds.contains(it) }.toMutableList()
        for (aItem in sourceAudios) {
            if (!cleanSv4AudioL.contains(aItem.id) && !manuallyRemovedSv4AudioLIds.contains(aItem.id)) {
                cleanSv4AudioL.add(aItem.id)
            }
        }
        if (cleanSv4AudioL.isEmpty() && sourceAudios.isNotEmpty()) {
            cleanSv4AudioL.addAll(sourceAudios.map { it.id })
        }
        if (cleanSv4AudioL != _sv4AudioLIds.value) {
            _sv4AudioLIds.value = cleanSv4AudioL
            changed = true
        }

        val cleanSrAudios = _srAudioIds.value.filter { validSourceAudioIds.contains(it) }.toSet()
        if (cleanSrAudios.size != _srAudioIds.value.size) {
            _srAudioIds.value = cleanSrAudios
            musicPlayer.setSrTrackFilter { !it.id.startsWith("vs_audio_rolling_") && isTrackInSr(it.id) }
            changed = true
        }

        if (changed) {
            saveAllToPrefs()
        }
    }

    /**
     * Dipanggil setiap kali pengguna menambah/menghapus video atau audio di sesi aktif.
     * Menjamin tidak ada referensi ID video/audio dari sesi sebelumnya yang tersangkut di IFS.
     */
    fun onMediaPlaylistChanged(
        newVideos: List<VideoItem>,
        newAudios: List<AudioItem>,
        broadcastIfActive: Boolean = true
    ) {
        ensureDefaultVideoSlotsPopulated()
        val sourceVideos = newVideos.filterNot { it.id.startsWith("vs_rolling_") }
        val sourceAudios = newAudios.filterNot { it.id.startsWith("vs_audio_rolling_") }
        val validVideoIds = sourceVideos.map { it.id }.toSet()
        val validAudioIds = sourceAudios.map { it.id }.toSet()

        val newVideoSig = sourceVideos.joinToString("|") { "${it.id}:${it.path}:${it.sizeBytes}" }
        val videoSourceChanged = (sv4LastSourceSignature.isNotBlank() && sv4LastSourceSignature != newVideoSig) ||
            sourceVideos.isEmpty() ||
            (sv4CurrentRecipe != null && !isRecipeValidForCurrentL(sv4CurrentRecipe, _sv4LVideoIds.value.ifEmpty { sourceVideos.map { it.id } }))
        sv4LastSourceSignature = newVideoSig

        if (videoSourceChanged && (sv4CurrentVideoItem != null || sv4NextVideoItem != null || sv4CurrentFile != null || sv4NextFile != null)) {
            sv4CurrentRecipe = null
            sv4NextRecipe = null
            cancelAndCleanupSv4RollingFiles()
        }

        val newAudioSig = sourceAudios.joinToString("|") { "${it.id}:${it.path}:${it.sizeBytes}" }
        val audioSourceChanged = (sv4AudioLastSourceSignature.isNotBlank() && sv4AudioLastSourceSignature != newAudioSig) ||
            sourceAudios.isEmpty() ||
            (sv4AudioCurrentRecipe != null && !isRecipeValidForCurrentL(
                sv4AudioCurrentRecipe,
                _sv4AudioLIds.value.ifEmpty { sourceAudios.map { it.id } },
                _sv4AudioMinLoops.value,
                _sv4AudioMaxLoops.value
            ))
        sv4AudioLastSourceSignature = newAudioSig

        if (audioSourceChanged && (sv4AudioCurrentItem != null || sv4AudioNextItem != null || sv4AudioCurrentFile != null || sv4AudioNextFile != null)) {
            sv4AudioCurrentRecipe = null
            sv4AudioNextRecipe = null
            cancelAndCleanupSv4AudioRollingFiles()
        }

        val curState = _ifsState.value
        val resolved = if (sourceVideos.isNotEmpty()) {
            resolveVideoItem(curState.activeVtNumber, curState.activeSubPhase)
        } else null

        val cleanQueue = if (sourceVideos.isEmpty()) emptyList() else curState.queue
        val cleanPending = if (sourceVideos.isEmpty()) null else curState.pendingTargetVtNumber
        val vbCount = _sv3VbVideoIds.value.size
        val effectiveLCount = _sv4LVideoIds.value.count { validVideoIds.contains(it) }.let {
            if (it > 0) it else sourceVideos.size
        }
        val effectiveAudioLCount = _sv4AudioLIds.value.count { validAudioIds.contains(it) }.let {
            if (it > 0) it else sourceAudios.size
        }

        _ifsState.value = curState.copy(
            currentVideoId = resolved?.id,
            currentVideoName = resolved?.name,
            pendingTargetVtNumber = cleanPending,
            queue = cleanQueue,
            sv3VbRemainingInCycle = getRemainingSv3VbInCycle(),
            sv3VbTotalCount = vbCount,
            sv4TotalLCount = effectiveLCount,
            sv4MinLoops = _sv4MinLoops.value,
            sv4MaxLoops = _sv4MaxLoops.value,
            audioMode = _audioMode.value,
            sv4AudioTotalLCount = effectiveAudioLCount,
            sv4AudioMinLoops = _sv4AudioMinLoops.value,
            sv4AudioMaxLoops = _sv4AudioMaxLoops.value
        )

        if (_isIfsActive.value) {
            if (_videoMode.value == IfsVideoMode.SV4) {
                if (_sv4VideoRunning.value && sourceVideos.isNotEmpty() && (videoSourceChanged || sv4CurrentVideoItem == null || sv4CurrentFile?.exists() != true)) {
                    startSv4RollingCycle(resetCounter = false)
                }
            } else if ((broadcastIfActive || videoSourceChanged) && resolved != null && validVideoIds.contains(resolved.id)) {
                syncCurrentVideoToRepository(broadcastToHook = true, bumpPhaseVersion = true)
            }
        }
        if (_audioMode.value == IfsAudioMode.SV4_VS && _sv4AudioRunning.value && sourceAudios.isNotEmpty()) {
            if (audioSourceChanged || sv4AudioCurrentItem == null || sv4AudioCurrentFile?.exists() != true) {
                startSv4AudioRollingCycle(resetCounter = false)
            }
        }
    }

    fun addVtPackage(initialVideoId: String? = null): Int {
        val current = _vtPackages.value.toMutableList()
        val nextNumber = current.size + 1
        current.add(
            IfsVtPackage(
                vtNumber = nextNumber,
                title = "VT$nextNumber",
                abbVideoId = initialVideoId,
                blVideoId = initialVideoId,
                bbtVideoId = initialVideoId,
                triggerKeyword = "$nextNumber, vt$nextNumber, spill $nextNumber, etalase $nextNumber",
                linkedAxNumber = nextNumber
            )
        )
        val renumbered = renumberVtList(current)
        _vtPackages.value = renumbered
        saveAllToPrefs()
        return nextNumber
    }

    fun deleteVtPackage(vtNumber: Int) {
        if (vtNumber <= 0) return // VTM tidak bisa dihapus
        val current = _vtPackages.value
        if (current.size <= 1) return // Minimal harus ada VT1
        val filtered = current.filter { it.vtNumber != vtNumber }
        val renumbered = renumberVtList(filtered)
        _vtPackages.value = renumbered
        saveAllToPrefs()

        // Pastikan activeVtNumber tetap valid setelah renumbering
        val curActive = _ifsState.value.activeVtNumber
        if (curActive > renumbered.size) {
            _ifsState.value = _ifsState.value.copy(activeVtNumber = renumbered.size)
            syncCurrentVideoToRepository(broadcastToHook = true)
        }
    }

    fun updateVtPackage(updated: IfsVtPackage) {
        if (updated.vtNumber == 0) {
            _vtmPackage.value = updated.copy(title = "ABA (ABB + BL + BBT)", linkedAxNumber = 0)
        } else {
            _vtPackages.value = _vtPackages.value.map {
                if (it.vtNumber == updated.vtNumber) updated.copy(title = "VT${updated.vtNumber}", linkedAxNumber = updated.vtNumber) else it
            }
        }
        saveAllToPrefs()
    }

    fun setSlotVideo(vtNumber: Int, phase: IfsSubPhase, videoId: String?) {
        if (vtNumber == 0) {
            val cur = _vtmPackage.value
            _vtmPackage.value = when (phase) {
                IfsSubPhase.ABB -> cur.copy(abbVideoId = videoId)
                IfsSubPhase.BL, IfsSubPhase.CONTENT, IfsSubPhase.VL, IfsSubPhase.VB, IfsSubPhase.VS -> cur.copy(blVideoId = videoId)
                IfsSubPhase.BBT -> cur.copy(bbtVideoId = videoId)
            }
        } else {
            _vtPackages.value = _vtPackages.value.map { pkg ->
                if (pkg.vtNumber == vtNumber) {
                    if (_videoMode.value == IfsVideoMode.SV1) {
                        // Pada SV1, VT1..n adalah 1 video tunggal (disimpan di blVideoId).
                        // Jika dilepas (null), bersihkan juga abb/bbt jika sebelumnya berisi ID yang sama.
                        val prevBl = pkg.blVideoId
                        pkg.copy(
                            blVideoId = videoId,
                            abbVideoId = if (videoId == null && pkg.abbVideoId == prevBl) null else pkg.abbVideoId,
                            bbtVideoId = if (videoId == null && pkg.bbtVideoId == prevBl) null else pkg.bbtVideoId
                        )
                    } else {
                        when (phase) {
                            IfsSubPhase.ABB -> pkg.copy(abbVideoId = videoId)
                            IfsSubPhase.BL, IfsSubPhase.CONTENT, IfsSubPhase.VL, IfsSubPhase.VB, IfsSubPhase.VS -> pkg.copy(blVideoId = videoId)
                            IfsSubPhase.BBT -> pkg.copy(bbtVideoId = videoId)
                        }
                    }
                } else pkg
            }
        }
        saveAllToPrefs()
        if (_isIfsActive.value) {
            syncCurrentVideoToRepository(broadcastToHook = true)
        }
    }

    /**
     * Melepas [videoId] dari seluruh slot VTM, VT1..n, VL/VB SV3, maupun L1..Ln SV4 sekaligus.
     */
    fun clearVideoFromAllSlots(videoId: String) {
        val curVtm = _vtmPackage.value
        _vtmPackage.value = curVtm.copy(
            abbVideoId = curVtm.abbVideoId.takeUnless { it == videoId },
            blVideoId = curVtm.blVideoId.takeUnless { it == videoId },
            bbtVideoId = curVtm.bbtVideoId.takeUnless { it == videoId }
        )
        _vtPackages.value = _vtPackages.value.map { pkg ->
            pkg.copy(
                abbVideoId = pkg.abbVideoId.takeUnless { it == videoId },
                blVideoId = pkg.blVideoId.takeUnless { it == videoId },
                bbtVideoId = pkg.bbtVideoId.takeUnless { it == videoId }
            )
        }
        if (_sv3VlVideoId.value == videoId) {
            _sv3VlVideoId.value = null
        }
        if (_sv3VbVideoIds.value.contains(videoId)) {
            _sv3VbVideoIds.value = _sv3VbVideoIds.value.filter { it != videoId }
            sv3VbGroupDeck.reset()
            lastPickedSv3VbIdx = -1
        }
        if (_sv4LVideoIds.value.contains(videoId)) {
            _sv4LVideoIds.value = _sv4LVideoIds.value.filter { it != videoId }
        }
        saveAllToPrefs()
        if (_isIfsActive.value) {
            syncCurrentVideoToRepository(broadcastToHook = true)
        }
    }

    /**
     * Mengembalikan daftar label slot IFS yang menggunakan [videoId] pada mode saat ini.
     * Digunakan untuk menampilkan badge peran langsung pada setiap kartu video di Tab Video.
     */
    fun getVideoSlotRoles(videoId: String, mode: IfsVideoMode = _videoMode.value): List<String> {
        val roles = mutableListOf<String>()
        when (mode) {
            IfsVideoMode.SV1 -> {
                val aba = _vtmPackage.value
                if (aba.abbVideoId == videoId) roles.add("ABA • A (ABB)")
                if (aba.blVideoId == videoId) roles.add("ABA • B (BL)")
                if (aba.bbtVideoId == videoId) roles.add("ABA • A (BBT)")
                for (pkg in _vtPackages.value) {
                    if (pkg.blVideoId == videoId) {
                        roles.add("VT${pkg.vtNumber}")
                    }
                }
            }
            IfsVideoMode.SV2 -> {
                for (pkg in _vtPackages.value) {
                    if (pkg.abbVideoId == videoId) roles.add("PM${pkg.vtNumber} • A (ABB)")
                    if (pkg.blVideoId == videoId) roles.add("PM${pkg.vtNumber} • B (BL)")
                    if (pkg.bbtVideoId == videoId) roles.add("PM${pkg.vtNumber} • A (BBT)")
                }
            }
            IfsVideoMode.SV3 -> {
                if (_sv3VlVideoId.value == videoId) {
                    val vlMin = _sv3VlMinLoops.value
                    val vlMax = _sv3VlMaxLoops.value
                    val vlLoopText = if (vlMin == vlMax) "${vlMin}x" else "${vlMin}..${vlMax}x"
                    roles.add("👑 VL ($vlLoopText)")
                }
                val vbIdx = _sv3VbVideoIds.value.indexOf(videoId)
                if (vbIdx >= 0) {
                    roles.add("🌉 VB${vbIdx + 1}")
                }
            }
            IfsVideoMode.SV4 -> {
                if (sv4CurrentVideoItem?.id == videoId) {
                    val curNum = sv4CurrentRecipe?.vsNumber ?: _ifsState.value.sv4CurrentVsNumber
                    roles.add("🎬 SEDANG PLAY (VS$curNum)")
                } else if (sv4NextVideoItem?.id == videoId) {
                    val nextNum = sv4NextRecipe?.vsNumber ?: _ifsState.value.sv4NextVsNumber
                    roles.add("⏳ SIAP DI DAFTAR (VS$nextNum)")
                } else {
                    val lIdx = _sv4LVideoIds.value.indexOf(videoId)
                    if (lIdx >= 0) {
                        val sMin = _sv4MinLoops.value
                        val sMax = _sv4MaxLoops.value
                        val sLoopText = if (sMin == sMax) "${sMin}x" else "${sMin}..${sMax}x"
                        roles.add("🧬 Sumber L${lIdx + 1} ($sLoopText)")
                    }
                }
            }
        }
        return roles
    }

    // =========================================================================
    // MANAJEMEN AUDIO TERKAIT (AX) & SISTEM RANDOM (SR)
    // =========================================================================

    fun setLinkedAudio(vtNumber: Int, audioId: String?) {
        if (vtNumber == 0) {
            _vtmPackage.value = _vtmPackage.value.copy(linkedAudioId = audioId)
        } else {
            _vtPackages.value = _vtPackages.value.map {
                if (it.vtNumber == vtNumber) it.copy(linkedAudioId = audioId) else it
            }
        }
        // Jika lagu ditetapkan sebagai AX, hapus dari SR eksplisit agar tidak terputar acak di background
        if (!audioId.isNullOrBlank()) {
            _srAudioIds.value = _srAudioIds.value - audioId
        }
        saveAllToPrefs()
        musicPlayer.setSrTrackFilter { isTrackInSr(it.id) }
    }

    fun toggleSrAudio(audioId: String) {
        val current = _srAudioIds.value.toMutableSet()
        if (current.contains(audioId)) {
            current.remove(audioId)
        } else {
            current.add(audioId)
        }
        _srAudioIds.value = current
        saveAllToPrefs()
        musicPlayer.setSrTrackFilter { isTrackInSr(it.id) }
    }

    fun setAudioAsSr(audioId: String) {
        // Lepas dari AX jika sebelumnya terikat
        if (_vtmPackage.value.linkedAudioId == audioId) {
            _vtmPackage.value = _vtmPackage.value.copy(linkedAudioId = null)
        }
        _vtPackages.value = _vtPackages.value.map {
            if (it.linkedAudioId == audioId) it.copy(linkedAudioId = null) else it
        }
        _srAudioIds.value = _srAudioIds.value + audioId
        saveAllToPrefs()
        musicPlayer.setSrTrackFilter { isTrackInSr(it.id) }
    }

    fun clearAudioRole(audioId: String) {
        if (_vtmPackage.value.linkedAudioId == audioId) {
            _vtmPackage.value = _vtmPackage.value.copy(linkedAudioId = null)
        }
        _vtPackages.value = _vtPackages.value.map {
            if (it.linkedAudioId == audioId) it.copy(linkedAudioId = null) else it
        }
        _srAudioIds.value = _srAudioIds.value - audioId
        if (_sv4AudioLIds.value.contains(audioId)) {
            _sv4AudioLIds.value = _sv4AudioLIds.value.filter { it != audioId }
        }
        saveAllToPrefs()
        musicPlayer.setSrTrackFilter { !it.id.startsWith("vs_audio_rolling_") && isTrackInSr(it.id) }
    }

    fun isTrackAssignedAsAx(audioId: String): Boolean {
        if (_vtmPackage.value.linkedAudioId == audioId) return true
        return _vtPackages.value.any { it.linkedAudioId == audioId }
    }

    fun isTrackInSr(audioId: String): Boolean {
        if (audioId.startsWith("vs_audio_rolling_")) return false
        if (_srAudioIds.value.contains(audioId)) return true
        if (isTrackAssignedAsAx(audioId)) return false
        // Jika pengguna belum menandai lagu SR secara manual, semua lagu yang bukan AX otomatis masuk SR
        return _srAudioIds.value.isEmpty()
    }

    fun getAudioRolesForTrack(audioId: String): List<String> {
        val roles = mutableListOf<String>()
        if (_audioMode.value == IfsAudioMode.SV4_VS) {
            if (sv4AudioCurrentItem?.id == audioId) {
                val curNum = sv4AudioCurrentRecipe?.vsNumber ?: _ifsState.value.sv4AudioCurrentVsNumber
                roles.add("🎵 SEDANG PLAY (VSA$curNum)")
                return roles
            } else if (sv4AudioNextItem?.id == audioId) {
                val nextNum = sv4AudioNextRecipe?.vsNumber ?: _ifsState.value.sv4AudioNextVsNumber
                roles.add("⏳ SIAP DI DAFTAR (VSA$nextNum)")
                return roles
            } else {
                val laIdx = _sv4AudioLIds.value.indexOf(audioId)
                if (laIdx >= 0) {
                    val aMin = _sv4AudioMinLoops.value
                    val aMax = _sv4AudioMaxLoops.value
                    val aLoopText = if (aMin == aMax) "${aMin}x" else "${aMin}..${aMax}x"
                    roles.add("🧬 Bahan LA${laIdx + 1} ($aLoopText)")
                }
            }
        }
        val mode = _videoMode.value
        if (mode == IfsVideoMode.SV1) {
            for (pkg in _vtPackages.value) {
                if (pkg.linkedAudioId == audioId) {
                    roles.add("🔊 AX${pkg.vtNumber} (VT${pkg.vtNumber})")
                }
            }
        } else if (mode == IfsVideoMode.SV2) {
            for (pkg in _vtPackages.value) {
                if (pkg.linkedAudioId == audioId) {
                    roles.add("🔊 AX${pkg.vtNumber} (PM${pkg.vtNumber})")
                }
            }
        }
        if (_audioMode.value != IfsAudioMode.SV4_VS &&
            (_srAudioIds.value.contains(audioId) || (roles.isEmpty() && _srAudioIds.value.isEmpty()))
        ) {
            roles.add("🎵 SR (Musik Latar)")
        }
        return roles
    }

    // =========================================================================
    // MANAJEMEN SV4 AUDIO — MODE VS AUDIO (VSA1, VSA2..n ROLLING JIT + AUTO-DELETE)
    // =========================================================================

    private fun audioRecipeToJson(recipe: VsVariationRecipe): JSONObject {
        return JSONObject().apply {
            put("vsNumber", recipe.vsNumber)
            put("formulaText", recipe.formulaText)
            val segArr = JSONArray()
            for (seg in recipe.segments) {
                segArr.put(
                    JSONObject().apply {
                        put("lNumber", seg.lNumber)
                        put("videoId", seg.videoId)
                        put("loopCount", seg.loopCount)
                    }
                )
            }
            put("segments", segArr)
        }
    }

    private fun rollingAudioItemToJson(item: AudioItem): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("name", item.name)
            put("path", item.path)
            put("uri", item.uri ?: "")
            put("durationMs", item.durationMs)
            put("sizeBytes", item.sizeBytes)
            put("isMuted", item.isMuted)
        }
    }

    private fun parseRollingAudioItemJson(raw: String?): AudioItem? {
        if (raw.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(raw)
            val id = obj.optString("id", "")
            val path = obj.optString("path", "")
            if (id.isBlank() || path.isBlank()) return null
            val f = File(path)
            if (!f.exists() || f.length() <= 0L) return null
            AudioItem(
                id = id,
                name = obj.optString("name", id),
                path = f.absolutePath,
                uri = obj.optString("uri", "").ifBlank { android.net.Uri.fromFile(f).toString() },
                durationMs = obj.optLong("durationMs", 15_000L),
                sizeBytes = f.length(),
                isMuted = obj.optBoolean("isMuted", false),
                isEnabled = true
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun saveSv4AudioRollingStateToPrefs() {
        try {
            val usedArr = JSONArray()
            synchronized(sv4AudioUsedFormulas) {
                for (f in sv4AudioUsedFormulas) {
                    if (f.isNotBlank()) usedArr.put(f)
                }
            }
            prefs.edit()
                .putString(KEY_IFS_AUDIO_MODE, _audioMode.value.name)
                .putInt(KEY_IFS_SV4_AUDIO_VS_COUNTER, sv4AudioVsCounter)
                .putInt(KEY_IFS_SV4_AUDIO_AUTO_DELETED_COUNT, sv4AudioAutoDeletedFilesCount)
                .putString(KEY_IFS_SV4_AUDIO_LAST_FORMULA, lastSv4AudioFormulaSignature)
                .putString(KEY_IFS_SV4_AUDIO_CONFIG_SIG, sv4AudioConfigSignature)
                .putString(KEY_IFS_SV4_AUDIO_CURRENT_RECIPE_JSON, sv4AudioCurrentRecipe?.let { audioRecipeToJson(it).toString() } ?: "")
                .putString(KEY_IFS_SV4_AUDIO_CURRENT_ITEM_JSON, sv4AudioCurrentItem?.let { rollingAudioItemToJson(it).toString() } ?: "")
                .putString(KEY_IFS_SV4_AUDIO_NEXT_RECIPE_JSON, sv4AudioNextRecipe?.let { audioRecipeToJson(it).toString() } ?: "")
                .putString(KEY_IFS_SV4_AUDIO_NEXT_ITEM_JSON, sv4AudioNextItem?.let { rollingAudioItemToJson(it).toString() } ?: "")
                .putString(KEY_IFS_SV4_AUDIO_USED_FORMULAS_JSON, usedArr.toString())
                .apply()
        } catch (e: Throwable) {
            Log.w(TAG, "Gagal menyimpan state rolling SV4 Audio: ${e.message}")
        }
    }

    private fun loadSavedSv4AudioRollingState() {
        try {
            sv4AudioVsCounter = prefs.getInt(KEY_IFS_SV4_AUDIO_VS_COUNTER, 0).coerceAtLeast(0)
            sv4AudioAutoDeletedFilesCount = prefs.getInt(KEY_IFS_SV4_AUDIO_AUTO_DELETED_COUNT, 0).coerceAtLeast(0)
            lastSv4AudioFormulaSignature = prefs.getString(KEY_IFS_SV4_AUDIO_LAST_FORMULA, "") ?: ""
            sv4AudioConfigSignature = prefs.getString(KEY_IFS_SV4_AUDIO_CONFIG_SIG, "") ?: ""

            val usedRaw = prefs.getString(KEY_IFS_SV4_AUDIO_USED_FORMULAS_JSON, null)
            if (!usedRaw.isNullOrBlank()) {
                val arr = JSONArray(usedRaw)
                synchronized(sv4AudioUsedFormulas) {
                    sv4AudioUsedFormulas.clear()
                    for (i in 0 until arr.length()) {
                        val f = arr.optString(i, "")
                        if (f.isNotBlank()) sv4AudioUsedFormulas.add(f)
                    }
                }
            }

            sv4AudioCurrentRecipe = parseRecipeJson(prefs.getString(KEY_IFS_SV4_AUDIO_CURRENT_RECIPE_JSON, null))
            sv4AudioCurrentItem = parseRollingAudioItemJson(prefs.getString(KEY_IFS_SV4_AUDIO_CURRENT_ITEM_JSON, null))
            sv4AudioCurrentFile = sv4AudioCurrentItem?.let { File(it.path).takeIf { f -> f.exists() && f.length() > 0L } }

            sv4AudioNextRecipe = parseRecipeJson(prefs.getString(KEY_IFS_SV4_AUDIO_NEXT_RECIPE_JSON, null))
            sv4AudioNextItem = parseRollingAudioItemJson(prefs.getString(KEY_IFS_SV4_AUDIO_NEXT_ITEM_JSON, null))
            sv4AudioNextFile = sv4AudioNextItem?.let { File(it.path).takeIf { f -> f.exists() && f.length() > 0L } }

            val minA = _sv4AudioMinLoops.value.coerceAtLeast(1)
            val maxA = _sv4AudioMaxLoops.value.coerceAtLeast(minA)
            if (sv4AudioCurrentRecipe != null && (
                    sv4AudioCurrentRecipe!!.segments.any { it.loopCount !in minA..maxA } ||
                        (minA == 1 && maxA == 1 && _sv4AudioLIds.value.isNotEmpty() && sv4AudioCurrentRecipe!!.segments.size > _sv4AudioLIds.value.size)
                    )
            ) {
                sv4AudioCurrentRecipe = null
                sv4AudioCurrentItem = null
                sv4AudioCurrentFile = null
            }
            if (sv4AudioNextRecipe != null && (
                    sv4AudioNextRecipe!!.segments.any { it.loopCount !in minA..maxA } ||
                        (minA == 1 && maxA == 1 && _sv4AudioLIds.value.isNotEmpty() && sv4AudioNextRecipe!!.segments.size > _sv4AudioLIds.value.size)
                    )
            ) {
                sv4AudioNextRecipe = null
                sv4AudioNextItem = null
                sv4AudioNextFile = null
            }

            val curNum = (sv4AudioCurrentRecipe?.vsNumber ?: sv4AudioVsCounter).coerceAtLeast(1)
            val curFormula = sv4AudioCurrentRecipe?.formulaText ?: ""
            val nextNum = sv4AudioNextRecipe?.vsNumber?.takeIf { it > curNum } ?: (curNum + 1)
            val nextFormula = sv4AudioNextRecipe?.formulaText ?: ""
            val isNextReady = sv4AudioNextItem != null && sv4AudioNextFile != null && sv4AudioNextFile!!.exists()
            sv4AudioVsCounter = maxOf(sv4AudioVsCounter, if (sv4AudioNextRecipe != null) nextNum else curNum)

            if (curFormula.isNotBlank()) {
                synchronized(sv4AudioUsedFormulas) { sv4AudioUsedFormulas.add(curFormula) }
            }
            if (nextFormula.isNotBlank()) {
                synchronized(sv4AudioUsedFormulas) { sv4AudioUsedFormulas.add(nextFormula) }
            }

            val curState = _ifsState.value
            _ifsState.value = curState.copy(
                audioMode = _audioMode.value,
                sv4AudioCurrentVsNumber = curNum,
                sv4AudioActiveFormula = curFormula,
                sv4AudioNextVsNumber = nextNum,
                sv4AudioNextFormula = nextFormula,
                sv4AudioNextReady = isNextReady,
                sv4AudioNextStitchPercent = if (isNextReady) 100 else 0,
                sv4AudioAutoDeletedCount = sv4AudioAutoDeletedFilesCount,
                sv4AudioTotalLCount = _sv4AudioLIds.value.size,
                sv4AudioMinLoops = _sv4AudioMinLoops.value,
                sv4AudioMaxLoops = _sv4AudioMaxLoops.value,
                sv4AudioStatusMessage = if (_audioMode.value == IfsAudioMode.SV4_VS && curFormula.isNotBlank()) {
                    if (isNextReady) {
                        "🎵 Play VSA$curNum [$curFormula] • ✅ VSA$nextNum [$nextFormula] Siap (#2)!"
                    } else {
                        "🎵 Play VSA$curNum [$curFormula] • ⏳ Melanjutkan VSA$nextNum..."
                    }
                } else curState.sv4AudioStatusMessage
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Gagal memuat state rolling SV4 Audio: ${e.message}")
        }
    }

    fun applyAudioModeToMusicPlayer(mode: IfsAudioMode = _audioMode.value) {
        when (mode) {
            IfsAudioMode.SEQUENTIAL -> {
                musicPlayer.setLoopMode(false)
                musicPlayer.setSrMode(false)
            }
            IfsAudioMode.SR_AX -> {
                musicPlayer.setLoopMode(false)
                musicPlayer.setSrMode(true)
            }
            IfsAudioMode.SINGLE_LOOP -> {
                musicPlayer.setLoopMode(true)
                musicPlayer.setSrMode(false)
            }
            IfsAudioMode.SV4_VS -> {
                musicPlayer.setLoopMode(false)
                musicPlayer.setSrMode(false)
            }
        }
    }

    /**
     * Berpindah cepat antar mode MP3 standar (🔁 Urut -> 🔀 Acak/SR -> 🔂 Loop 1x -> 🔁 Urut).
     * Jika sedang berada di SV4 Audio, langsung beralih ke 🔁 Urut agar pengguna bebas memilih lagu.
     */
    fun cycleStandardMp3AudioMode(): IfsAudioMode {
        val nextMode = when (_audioMode.value) {
            IfsAudioMode.SEQUENTIAL -> IfsAudioMode.SR_AX
            IfsAudioMode.SR_AX -> IfsAudioMode.SINGLE_LOOP
            IfsAudioMode.SINGLE_LOOP -> IfsAudioMode.SEQUENTIAL
            IfsAudioMode.SV4_VS -> IfsAudioMode.SEQUENTIAL
        }
        setAudioMode(nextMode)
        return nextMode
    }

    fun setAudioMode(mode: IfsAudioMode) {
        if (_audioMode.value == mode) {
            applyAudioModeToMusicPlayer(mode)
            return
        }
        if (_audioMode.value == IfsAudioMode.SV4_VS && mode != IfsAudioMode.SV4_VS) {
            _sv4AudioRunning.value = false
            prefs.edit().putBoolean(KEY_IFS_SV4_AUDIO_RUNNING, false).apply()
            cancelAndCleanupSv4AudioRollingFiles()
        }
        _audioMode.value = mode
        prefs.edit().putString(KEY_IFS_AUDIO_MODE, mode.name).apply()
        saveAllToPrefs()
        applyAudioModeToMusicPlayer(mode)

        if (mode == IfsAudioMode.SV4_VS) {
            // Jangan langsung menyalakan peracikan SV4 Audio saat baru memilih mode!
            // Masuk ke tahap Persiapan & Edit dulu agar pengguna bisa tambah audio/ZIP atau Edit Audio Massal.
            _sv4AudioRunning.value = false
            prefs.edit().putBoolean(KEY_IFS_SV4_AUDIO_RUNNING, false).apply()
            cancelAndCleanupSv4AudioRollingFiles()
            _ifsState.value = _ifsState.value.copy(
                audioMode = mode,
                sv4AudioRunning = false,
                sv4AudioIsStitchingNext = false,
                sv4AudioNextReady = false,
                sv4AudioTotalLCount = _sv4AudioLIds.value.size,
                sv4AudioStatusMessage = "🛠️ Mode Persiapan & Edit SV4 Audio: Tambah Audio/ZIP atau Edit Massal lalu tekan '▶️ MULAI SV4 AUDIO'"
            )
        } else {
            val statusMsg = when (mode) {
                IfsAudioMode.SEQUENTIAL -> "🔁 Mode Audio MP3: Putar Urut (1→2→3) Aktif"
                IfsAudioMode.SINGLE_LOOP -> "🔂 Mode Audio MP3: Loop 1 Lagu Aktif"
                else -> "🔀 Mode Audio MP3: Acak / SR & AX Aktif"
            }
            _ifsState.value = _ifsState.value.copy(
                audioMode = mode,
                sv4AudioRunning = false,
                sv4AudioStatusMessage = statusMsg
            )
            val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null }
            if (repo?.config?.value?.isMp3AudioPaused != true) {
                musicPlayer.ensureSrPlaying()
            }
        }
    }

    fun setSv4AudioLIds(audioIds: List<String>) {
        val cleanList = audioIds.filter { it.isNotBlank() && !it.startsWith("vs_audio_rolling_") }.distinct()
        try {
            val repo = VcamRepository.getInstance(appContext)
            val allSourceIds = repo.config.value.audioPlaylist
                .filterNot { repo.isRollingVsAudioItem(it) }
                .map { it.id }
            manuallyRemovedSv4AudioLIds.removeAll(cleanList.toSet())
            if (cleanList.isNotEmpty()) {
                val deselected = allSourceIds.filterNot { cleanList.contains(it) }
                manuallyRemovedSv4AudioLIds.addAll(deselected)
            }
        } catch (_: Throwable) {}
        _sv4AudioLIds.value = cleanList
        saveAllToPrefs()
        _ifsState.value = _ifsState.value.copy(
            sv4AudioTotalLCount = cleanList.size,
            sv4AudioMinLoops = _sv4AudioMinLoops.value,
            sv4AudioMaxLoops = _sv4AudioMaxLoops.value
        )
        if (_audioMode.value == IfsAudioMode.SV4_VS && _sv4AudioRunning.value) {
            startSv4AudioRollingCycle(resetCounter = false)
        }
    }

    fun toggleSv4AudioL(audioId: String): Boolean {
        val cur = _sv4AudioLIds.value.toMutableList()
        val added = if (cur.contains(audioId)) {
            cur.remove(audioId)
            manuallyRemovedSv4AudioLIds.add(audioId)
            false
        } else {
            cur.add(audioId)
            manuallyRemovedSv4AudioLIds.remove(audioId)
            true
        }
        setSv4AudioLIds(cur)
        return added
    }

    fun removeSv4AudioL(audioId: String) {
        manuallyRemovedSv4AudioLIds.add(audioId)
        val cur = _sv4AudioLIds.value.filter { it != audioId }
        setSv4AudioLIds(cur)
    }

    fun setSv4AudioLoopRange(minLoops: Int, maxLoops: Int) {
        val safeMin = minLoops.coerceIn(1, 10)
        val safeMax = maxLoops.coerceIn(safeMin, 10)
        _sv4AudioMinLoops.value = safeMin
        _sv4AudioMaxLoops.value = safeMax
        saveAllToPrefs()

        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null }
        val l2Id = repo?.config?.value?.audioLayer2Id.orEmpty()
        val l2Path = repo?.config?.value?.audioLayer2Path.orEmpty()
        val sourcePlaylist = repo?.config?.value?.audioPlaylist?.filterNot {
            repo.isRollingVsAudioItem(it) || it.id == l2Id || (l2Path.isNotBlank() && it.path == l2Path)
        }.orEmpty()
        val configuredLIds = _sv4AudioLIds.value.filter { id -> sourcePlaylist.any { it.id == id } && id != l2Id }
        val effectiveLIds = if (configuredLIds.isNotEmpty()) {
            configuredLIds
        } else {
            sourcePlaylist.filter { it.isEnabled }.map { it.id }.ifEmpty { sourcePlaylist.map { it.id } }
        }

        val curValid = isRecipeValidForCurrentL(sv4AudioCurrentRecipe, effectiveLIds, safeMin, safeMax)
        val nextValid = isRecipeValidForCurrentL(sv4AudioNextRecipe, effectiveLIds, safeMin, safeMax)

        if (!curValid) {
            sv4AudioCurrentRecipe = null
            sv4AudioCurrentItem = null
            sv4AudioCurrentFile = null
            lastSv4AudioFormulaSignature = ""
        }
        if (!nextValid) {
            sv4AudioNextRecipe = null
            sv4AudioNextItem = null
            sv4AudioNextFile = null
        }

        val rangeLabel = if (safeMin == safeMax) "${safeMin}x" else "${safeMin}x..${safeMax}x"

        if (_audioMode.value == IfsAudioMode.SV4_VS && _sv4AudioRunning.value) {
            if (!curValid || !nextValid) {
                startSv4AudioRollingCycle(resetCounter = false)
                return
            }
        }

        if (sv4AudioCurrentRecipe == null && effectiveLIds.isNotEmpty()) {
            val previewNum = (sv4AudioVsCounter.takeIf { it > 0 } ?: _ifsState.value.sv4AudioCurrentVsNumber).coerceAtLeast(1)
            sv4AudioCurrentRecipe = generateVsAudioVariationRecipe(previewNum)
        }
        saveSv4AudioRollingStateToPrefs()

        val curState = _ifsState.value
        _ifsState.value = curState.copy(
            sv4AudioMinLoops = safeMin,
            sv4AudioMaxLoops = safeMax,
            sv4AudioActiveFormula = sv4AudioCurrentRecipe?.formulaText ?: "",
            sv4AudioNextFormula = sv4AudioNextRecipe?.formulaText ?: "",
            sv4AudioNextReady = sv4AudioNextItem != null && sv4AudioNextFile?.exists() == true,
            sv4AudioStatusMessage = "🧬 SV4 Audio • Loop LA diatur ke $rangeLabel"
        )
    }

    fun rebuildSv4AudioVariationsNow() {
        startSv4AudioEngine(resetCounter = true)
    }

    private fun generateVsAudioVariationRecipe(vsNumber: Int): VsVariationRecipe? {
        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null } ?: return null
        val l2Id = repo.config.value.audioLayer2Id
        val l2Path = repo.config.value.audioLayer2Path
        val sourcePlaylist = repo.config.value.audioPlaylist.filterNot {
            repo.isRollingVsAudioItem(it) || it.id == l2Id || (l2Path.isNotBlank() && it.path == l2Path)
        }
        if (sourcePlaylist.isEmpty()) return null

        val configuredLIds = _sv4AudioLIds.value.filter { id -> sourcePlaylist.any { it.id == id } && id != l2Id }
        val effectiveLIds = if (configuredLIds.isNotEmpty()) {
            configuredLIds
        } else {
            sourcePlaylist.filter { it.isEnabled }.map { it.id }.ifEmpty { sourcePlaylist.map { it.id } }
        }
        if (effectiveLIds.isEmpty()) return null

        val minL = _sv4AudioMinLoops.value.coerceAtLeast(1)
        val maxL = _sv4AudioMaxLoops.value.coerceAtLeast(minL)
        val loopChoices = (maxL - minL + 1).coerceAtLeast(1)

        val currentConfigSig = "${effectiveLIds.joinToString(",")}|${minL}..${maxL}"
        if (sv4AudioConfigSignature.isNotBlank() && sv4AudioConfigSignature != currentConfigSig) {
            synchronized(sv4AudioUsedFormulas) {
                sv4AudioUsedFormulas.clear()
            }
        }
        sv4AudioConfigSignature = currentConfigSig

        val totalPossibleVariations = Sv4VariationCalculator.calculateTotalVariations(
            lCount = effectiveLIds.size,
            minLoops = minL,
            maxLoops = maxL
        ).coerceAtLeast(1L)

        synchronized(sv4AudioUsedFormulas) {
            if (sv4AudioUsedFormulas.size.toLong() >= totalPossibleVariations) {
                sv4AudioUsedFormulas.clear()
            }
        }

        var chosenSegments: List<VsSegmentSpec> = emptyList()
        var chosenFormula = ""

        val now = System.currentTimeMillis()
        synchronized(sv4AudioChoreographyHistory) {
            sv4AudioChoreographyHistory.removeAll { (now - it.timestampMs) >= SV4_GUARD_WINDOW_MS }
        }
        val recent2mAudioFormulas = synchronized(sv4AudioChoreographyHistory) { sv4AudioChoreographyHistory.map { it.formula }.toSet() }
        val recent2mAudioSignatures = synchronized(sv4AudioChoreographyHistory) { sv4AudioChoreographyHistory.map { it.signature }.toSet() }

        val prevLastAudioId = (sv4AudioNextRecipe ?: sv4AudioCurrentRecipe)?.segments?.lastOrNull()?.videoId

        for (attempt in 0 until 128) {
            val rng = java.util.Random(System.nanoTime() xor (attempt.toLong() * 173L) xor (vsNumber.toLong() * 991L))
            val indices = effectiveLIds.indices.toMutableList()
            if (indices.size > 1) {
                indices.shuffle(rng)
                if (prevLastAudioId != null && effectiveLIds[indices[0]] == prevLastAudioId) {
                    val tmp = indices[0]
                    indices[0] = indices[1]
                    indices[1] = tmp
                }
            }
            val segs = indices.map { idx ->
                val lNum = idx + 1
                val audId = effectiveLIds[idx]
                val loops = if (minL == maxL) minL else minL + rng.nextInt(loopChoices)
                VsSegmentSpec(
                    lNumber = lNum,
                    videoId = audId,
                    loopCount = loops
                )
            }.toMutableList()

            if (effectiveLIds.size >= 2 && maxL > 1) {
                val totalWeight = 1 + (effectiveLIds.size - 1) * loopChoices
                if (rng.nextInt(totalWeight) != 0) {
                    val lastIdx = indices.last()
                    val tailCandidates = effectiveLIds.indices.filter { it != lastIdx }
                    if (tailCandidates.isNotEmpty()) {
                        val tailIdx = tailCandidates[rng.nextInt(tailCandidates.size)]
                        val tailLoops = if (minL == maxL) minL else minL + rng.nextInt(loopChoices)
                        segs.add(
                            VsSegmentSpec(
                                lNumber = tailIdx + 1,
                                videoId = effectiveLIds[tailIdx],
                                loopCount = tailLoops
                            )
                        )
                    }
                }
            }

            if (effectiveLIds.size == 1 && minL < maxL) {
                val lastSig = synchronized(sv4AudioChoreographyHistory) { sv4AudioChoreographyHistory.lastOrNull()?.signature }
                val lastLoopCount = lastSig?.split(":")?.getOrNull(1)?.toIntOrNull()
                if (lastLoopCount != null) {
                    val alternatedLoop = if (lastLoopCount == minL) maxL else minL
                    segs[0] = segs[0].copy(loopCount = alternatedLoop)
                }
            }

            val formula = segs.joinToString(" + ") { "LA${it.lNumber}(${it.loopCount}x)" }
            val signature = segs.joinToString(";") { "${it.videoId}:${it.loopCount}" }
            chosenSegments = segs
            chosenFormula = formula

            val isUsedIn2m = recent2mAudioFormulas.contains(formula) || recent2mAudioSignatures.contains(signature)
            val isAlreadyUsed = synchronized(sv4AudioUsedFormulas) { sv4AudioUsedFormulas.contains(formula) }

            // Guard 2 Menit Audio: Wajib tidak kembar dalam rentang waktu 2 menit terakhir (120 detik)
            if (!isUsedIn2m && ((!isAlreadyUsed && formula != lastSv4AudioFormulaSignature) || totalPossibleVariations <= recent2mAudioFormulas.size)) {
                break
            }
            if (!isUsedIn2m && attempt >= 32) {
                break
            }
        }

        val chosenSig = chosenSegments.joinToString(";") { "${it.videoId}:${it.loopCount}" }
        val audioRecord = Sv4AudioChoreographyRecord(
            vsNumber = vsNumber,
            formula = chosenFormula,
            signature = chosenSig,
            firstAudioId = chosenSegments.firstOrNull()?.videoId ?: "",
            lastAudioId = chosenSegments.lastOrNull()?.videoId ?: "",
            timestampMs = now
        )
        synchronized(sv4AudioChoreographyHistory) {
            sv4AudioChoreographyHistory.add(audioRecord)
        }

        lastSv4AudioFormulaSignature = chosenFormula
        synchronized(sv4AudioUsedFormulas) {
            if (sv4AudioUsedFormulas.size >= 6000) {
                val first = sv4AudioUsedFormulas.firstOrNull()
                if (first != null) sv4AudioUsedFormulas.remove(first)
            }
            sv4AudioUsedFormulas.add(chosenFormula)
        }
        saveSv4AudioRollingStateToPrefs()

        Log.i(TAG, "🛡️ [SV4-2MIN-GUARD] Audio VSA$vsNumber Digenerate: '$chosenFormula' (Riwayat 2m aktif: ${getSv4AudioChoreographyHistoryInLast2m().size} variasi)")

        return VsVariationRecipe(
            vsNumber = vsNumber,
            segments = chosenSegments,
            formulaText = chosenFormula
        )
    }

    private fun expandAudioRecipeToSpecs(recipe: VsVariationRecipe): List<com.tajasb.engine.SmartAudioStitcher.AudioSegmentSpec> {
        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null } ?: return emptyList()
        val l2Id = repo.config.value.audioLayer2Id
        val l2Path = repo.config.value.audioLayer2Path
        val sourcePlaylist = repo.config.value.audioPlaylist.filterNot {
            repo.isRollingVsAudioItem(it) || it.id == l2Id || (l2Path.isNotBlank() && it.path == l2Path)
        }
        val specs = mutableListOf<com.tajasb.engine.SmartAudioStitcher.AudioSegmentSpec>()
        for (seg in recipe.segments) {
            val item = sourcePlaylist.firstOrNull { it.id == seg.videoId }
                ?: _sv4AudioLIds.value.getOrNull(seg.lNumber - 1)?.let { lId -> sourcePlaylist.firstOrNull { it.id == lId } }
                ?: continue
            specs.add(
                com.tajasb.engine.SmartAudioStitcher.AudioSegmentSpec(
                    audio = item,
                    label = "LA${seg.lNumber}",
                    loops = seg.loopCount.coerceIn(1, 10)
                )
            )
        }
        return specs
    }

    fun startSv4AudioRollingCycle(resetCounter: Boolean = false) {
        ensureDefaultVideoSlotsPopulated()
        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null } ?: return
        val l2Id = repo.config.value.audioLayer2Id
        val l2Path = repo.config.value.audioLayer2Path
        val sourceAudios = repo.config.value.audioPlaylist.filterNot {
            repo.isRollingVsAudioItem(it) || it.id == l2Id || (l2Path.isNotBlank() && it.path == l2Path)
        }
        if (sourceAudios.isEmpty()) {
            sv4AudioStitchJob?.cancel()
            sv4AudioStitchJob = null
            sv4AudioWatchdogJob?.cancel()
            sv4AudioWatchdogJob = null
            _ifsState.value = _ifsState.value.copy(
                audioMode = _audioMode.value,
                sv4AudioTotalLCount = 0,
                sv4AudioIsStitchingNext = false,
                sv4AudioNextReady = false,
                sv4AudioStatusMessage = "⚠️ SV4 Audio: Tambahkan minimal 2 trek audio LA1..LAn di Daftar Musik terlebih dahulu"
            )
            return
        }

        if (_sv4AudioLIds.value.isEmpty()) {
            _sv4AudioLIds.value = sourceAudios.map { it.id }
            saveAllToPrefs()
        }

        val configuredLIds = _sv4AudioLIds.value.filter { id -> sourceAudios.any { it.id == id } }
        val effectiveLIds = if (configuredLIds.isNotEmpty()) {
            configuredLIds
        } else {
            sourceAudios.filter { it.isEnabled }.map { it.id }.ifEmpty { sourceAudios.map { it.id } }
        }

        if (!resetCounter) {
            val existingCurRecipe = sv4AudioCurrentRecipe
            val existingCurItem = sv4AudioCurrentItem
            val existingCurFile = sv4AudioCurrentFile ?: existingCurItem?.let { File(it.path) }

            if (existingCurRecipe != null &&
                existingCurItem != null &&
                existingCurFile != null &&
                existingCurFile.exists() &&
                existingCurFile.length() > 0L &&
                isRecipeValidForCurrentL(existingCurRecipe, effectiveLIds, _sv4AudioMinLoops.value, _sv4AudioMaxLoops.value)
            ) {
                sv4AudioCurrentFile = existingCurFile
                sv4AudioIsInitialWarmupActive = false
                sv4AudioWaitingForNextReady = false
                sv4AudioLastPromotedTimeMs = System.currentTimeMillis()

                try {
                    repo.selectRollingVsAudioSync(vsAudio = existingCurItem, startPlaying = true)
                } catch (_: Throwable) {}

                if (sv4AudioWatchdogJob?.isActive != true) {
                    scheduleSv4AudioPlaybackWatchdog(existingCurItem)
                }

                val existingNextRecipe = sv4AudioNextRecipe
                val existingNextItem = sv4AudioNextItem
                val existingNextFile = sv4AudioNextFile ?: existingNextItem?.let { File(it.path) }

                if (existingNextRecipe != null &&
                    existingNextItem != null &&
                    existingNextFile != null &&
                    existingNextFile.exists() &&
                    existingNextFile.length() > 0L &&
                    existingNextRecipe.vsNumber > existingCurRecipe.vsNumber &&
                    isRecipeValidForCurrentL(existingNextRecipe, effectiveLIds, _sv4AudioMinLoops.value, _sv4AudioMaxLoops.value)
                ) {
                    sv4AudioNextFile = existingNextFile
                    sv4AudioVsCounter = maxOf(sv4AudioVsCounter, existingNextRecipe.vsNumber)
                    try {
                        repo.insertNextQueuedVsAudioIntoPlaylistSync(existingNextItem)
                    } catch (_: Throwable) {}

                    _ifsState.value = _ifsState.value.copy(
                        audioMode = _audioMode.value,
                        sv4AudioCurrentVsNumber = existingCurRecipe.vsNumber,
                        sv4AudioActiveFormula = existingCurRecipe.formulaText,
                        sv4AudioNextVsNumber = existingNextRecipe.vsNumber,
                        sv4AudioNextFormula = existingNextRecipe.formulaText,
                        sv4AudioIsStitchingNext = false,
                        sv4AudioNextStitchPercent = 100,
                        sv4AudioNextReady = true,
                        sv4AudioAutoDeletedCount = sv4AudioAutoDeletedFilesCount,
                        sv4AudioTotalLCount = _sv4AudioLIds.value.size,
                        sv4AudioStatusMessage = "🎵 Play VSA${existingCurRecipe.vsNumber} [${existingCurRecipe.formulaText}] • ✅ VSA${existingNextRecipe.vsNumber} [${existingNextRecipe.formulaText}] Siap (#2)!"
                    )
                    saveSv4AudioRollingStateToPrefs()
                    return
                } else {
                    val nextVsNum = existingNextRecipe?.vsNumber?.takeIf { it > existingCurRecipe.vsNumber }
                        ?: (existingCurRecipe.vsNumber + 1)
                    sv4AudioVsCounter = maxOf(sv4AudioVsCounter, nextVsNum)
                    val nextRec = if (existingNextRecipe != null &&
                        existingNextRecipe.vsNumber == nextVsNum &&
                        isRecipeValidForCurrentL(existingNextRecipe, effectiveLIds, _sv4AudioMinLoops.value, _sv4AudioMaxLoops.value) &&
                        existingNextRecipe.segments.isNotEmpty()
                    ) {
                        existingNextRecipe
                    } else {
                        generateVsAudioVariationRecipe(nextVsNum)
                    }
                    sv4AudioNextRecipe = nextRec
                    sv4AudioNextItem = null
                    sv4AudioNextFile = null

                    _ifsState.value = _ifsState.value.copy(
                        audioMode = _audioMode.value,
                        sv4AudioCurrentVsNumber = existingCurRecipe.vsNumber,
                        sv4AudioActiveFormula = existingCurRecipe.formulaText,
                        sv4AudioNextVsNumber = nextVsNum,
                        sv4AudioNextFormula = nextRec?.formulaText ?: "",
                        sv4AudioIsStitchingNext = nextRec != null,
                        sv4AudioNextStitchPercent = 0,
                        sv4AudioNextReady = false,
                        sv4AudioAutoDeletedCount = sv4AudioAutoDeletedFilesCount,
                        sv4AudioTotalLCount = _sv4AudioLIds.value.size,
                        sv4AudioStatusMessage = "🎵 Play VSA${existingCurRecipe.vsNumber} [${existingCurRecipe.formulaText}] • ⚙️ Melanjutkan Gabung VSA$nextVsNum..."
                    )
                    saveSv4AudioRollingStateToPrefs()
                    if (nextRec != null && sv4AudioStitchJob?.isActive != true) {
                        launchNextVsAudioBackgroundStitch(nextRec)
                    }
                    return
                }
            }
        }

        sv4AudioStitchJob?.cancel()
        sv4AudioStitchJob = null
        sv4AudioWatchdogJob?.cancel()
        sv4AudioWatchdogJob = null
        sv4AudioIsInitialWarmupActive = false
        sv4AudioWaitingForNextReady = false

        repo.cleanupAllRollingVsAudioFromPlaylistSync()

        if (resetCounter) {
            sv4AudioVsCounter = 0
            sv4AudioAutoDeletedFilesCount = 0
            lastSv4AudioFormulaSignature = ""
            synchronized(sv4AudioUsedFormulas) {
                sv4AudioUsedFormulas.clear()
            }
            sv4AudioCurrentRecipe = null
            sv4AudioCurrentItem = null
            sv4AudioCurrentFile = null
            sv4AudioNextRecipe = null
            sv4AudioNextItem = null
            sv4AudioNextFile = null
            saveSv4AudioRollingStateToPrefs()
            sv4Scope.launch {
                com.tajasb.engine.SmartAudioStitcher.clearAllRollingVsAudioFiles(appContext)
            }
        }

        val vsa1Num = if (resetCounter) {
            ++sv4AudioVsCounter
        } else {
            val savedActiveNum = sv4AudioCurrentRecipe?.vsNumber ?: sv4AudioVsCounter
            if (savedActiveNum > 0) {
                sv4AudioVsCounter = maxOf(sv4AudioVsCounter, savedActiveNum)
                savedActiveNum
            } else {
                ++sv4AudioVsCounter
            }
        }

        val recipe1 = if (!resetCounter &&
            sv4AudioCurrentRecipe != null &&
            sv4AudioCurrentRecipe!!.vsNumber == vsa1Num &&
            sv4AudioCurrentRecipe!!.segments.isNotEmpty() &&
            isRecipeValidForCurrentL(sv4AudioCurrentRecipe, effectiveLIds, _sv4AudioMinLoops.value, _sv4AudioMaxLoops.value)
        ) {
            sv4AudioCurrentRecipe!!
        } else {
            generateVsAudioVariationRecipe(vsa1Num) ?: return
        }

        val specs1 = expandAudioRecipeToSpecs(recipe1)
        if (specs1.isEmpty()) return

        sv4AudioIsInitialWarmupActive = true
        sv4AudioWaitingForNextReady = false
        sv4AudioCurrentRecipe = recipe1
        sv4AudioCurrentItem = null
        sv4AudioCurrentFile = null
        sv4AudioNextRecipe = null
        sv4AudioNextItem = null
        sv4AudioNextFile = null
        saveSv4AudioRollingStateToPrefs()

        _ifsState.value = _ifsState.value.copy(
            audioMode = _audioMode.value,
            sv4AudioCurrentVsNumber = vsa1Num,
            sv4AudioActiveFormula = recipe1.formulaText,
            sv4AudioNextVsNumber = vsa1Num + 1,
            sv4AudioNextFormula = "Menunggu VSA$vsa1Num selesai digabung...",
            sv4AudioIsStitchingNext = true,
            sv4AudioNextStitchPercent = 0,
            sv4AudioNextReady = false,
            sv4AudioAutoDeletedCount = sv4AudioAutoDeletedFilesCount,
            sv4AudioTotalLCount = _sv4AudioLIds.value.size,
            sv4AudioStatusMessage = "⚡ Menggabungkan VSA$vsa1Num [${recipe1.formulaText}] ke Daftar Musik..."
        )

        sv4AudioStitchJob = sv4Scope.launch {
            val res1 = com.tajasb.engine.SmartAudioStitcher.stitchAudioVariation(
                context = appContext,
                vsNumber = vsa1Num,
                segments = specs1,
                onProgress = { pct, status ->
                    if (isActive && _audioMode.value == IfsAudioMode.SV4_VS) {
                        _ifsState.value = _ifsState.value.copy(
                            sv4AudioIsStitchingNext = true,
                            sv4AudioNextStitchPercent = pct,
                            sv4AudioStatusMessage = "⚙️ Gabung VSA$vsa1Num [${recipe1.formulaText}] • $status ($pct%)"
                        )
                    }
                }
            )

            if (!isActive || _audioMode.value != IfsAudioMode.SV4_VS) return@launch

            if (res1 != null && res1.outputFile.exists() && res1.outputFile.length() > 0L) {
                sv4AudioIsInitialWarmupActive = false
                sv4AudioWaitingForNextReady = false
                val stitchedItem1 = AudioItem(
                    id = "vs_audio_rolling_$vsa1Num",
                    name = "🎵 [VSA$vsa1Num] ${recipe1.formulaText}",
                    path = res1.outputFile.absolutePath,
                    uri = android.net.Uri.fromFile(res1.outputFile).toString(),
                    durationMs = res1.durationMs,
                    sizeBytes = res1.outputFile.length(),
                    isMuted = false,
                    isEnabled = true
                )
                sv4AudioCurrentRecipe = recipe1
                sv4AudioCurrentItem = stitchedItem1
                sv4AudioCurrentFile = res1.outputFile
                sv4AudioLastPromotedTimeMs = System.currentTimeMillis()

                try {
                    VcamRepository.getInstance(appContext).selectRollingVsAudioSync(
                        vsAudio = stitchedItem1,
                        startPlaying = true
                    )
                } catch (_: Throwable) {}

                scheduleSv4AudioPlaybackWatchdog(stitchedItem1)

                val vsa2Num = maxOf(sv4AudioVsCounter + 1, vsa1Num + 1)
                sv4AudioVsCounter = vsa2Num
                val recipe2 = generateVsAudioVariationRecipe(vsa2Num)
                sv4AudioNextRecipe = recipe2
                saveSv4AudioRollingStateToPrefs()

                _ifsState.value = _ifsState.value.copy(
                    audioMode = _audioMode.value,
                    sv4AudioCurrentVsNumber = vsa1Num,
                    sv4AudioActiveFormula = recipe1.formulaText,
                    sv4AudioNextVsNumber = vsa2Num,
                    sv4AudioNextFormula = recipe2?.formulaText ?: "",
                    sv4AudioIsStitchingNext = recipe2 != null,
                    sv4AudioNextStitchPercent = 0,
                    sv4AudioNextReady = false,
                    sv4AudioStatusMessage = "🎵 Play VSA$vsa1Num [${recipe1.formulaText}] • ⚙️ Merakit VSA$vsa2Num di Latar..."
                )

                if (recipe2 != null) {
                    kotlinx.coroutines.delay(400L)
                    stitchNextVsAudioInternal(recipe2)
                }
            } else {
                sv4AudioIsInitialWarmupActive = false
                _ifsState.value = _ifsState.value.copy(
                    sv4AudioIsStitchingNext = false,
                    sv4AudioStatusMessage = "⚠️ Gagal merakit VSA$vsa1Num"
                )
            }
        }
    }

    fun onAudioSeeked(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        sv4AudioWatchdogPlayedMs = safePos
        sv4AudioLastPromotedTimeMs = (System.currentTimeMillis() - safePos).coerceAtLeast(1L)
        val curItem = sv4AudioCurrentItem
        if (_audioMode.value == IfsAudioMode.SV4_VS && curItem != null) {
            if (sv4AudioWatchdogJob?.isActive != true) {
                scheduleSv4AudioPlaybackWatchdog(curItem, initialPlayedMs = sv4AudioWatchdogPlayedMs)
            }
        }
    }

    private fun scheduleSv4AudioPlaybackWatchdog(activeItem: AudioItem, initialPlayedMs: Long = 0L) {
        sv4AudioWatchdogJob?.cancel()
        val expectedDurationMs = activeItem.durationMs.coerceIn(2_000L, 3_600_000L)
        val targetItemId = activeItem.id
        sv4AudioWatchdogPlayedMs = initialPlayedMs.coerceIn(0L, expectedDurationMs)
        sv4AudioWatchdogJob = sv4Scope.launch {
            val stepMs = 250L
            val graceMs = (expectedDurationMs * 6L / 100L).coerceIn(1_800L, 4_500L)
            val triggerThresholdMs = expectedDurationMs + graceMs
            while (isActive && _audioMode.value == IfsAudioMode.SV4_VS) {
                kotlinx.coroutines.delay(stepMs)
                if (sv4AudioCurrentItem?.id != targetItemId) {
                    return@launch
                }
                if (musicPlayer.isPlaying.value) {
                    val realPos = musicPlayer.currentPositionMs.value
                    if (musicPlayer.currentTrack.value?.id == targetItemId && realPos > 0L && realPos <= expectedDurationMs + 10_000L) {
                        sv4AudioWatchdogPlayedMs = realPos
                    } else {
                        sv4AudioWatchdogPlayedMs += stepMs
                    }
                    if (sv4AudioWatchdogPlayedMs >= triggerThresholdMs) {
                        if (sv4AudioCurrentItem?.id == targetItemId && _audioMode.value == IfsAudioMode.SV4_VS) {
                            Log.i(TAG, "⏱️ [SV4-AUDIO-WATCHDOG] Durasi $targetItemId (${expectedDurationMs}ms) selesai, otomatis lanjut ke VSA berikutnya!")
                            onSv4AudioCompleted(forceAdvance = false)
                        }
                        return@launch
                    }
                }
            }
        }
    }

    private fun launchNextVsAudioBackgroundStitch(nextRecipe: VsVariationRecipe) {
        sv4AudioStitchJob?.cancel()
        sv4AudioStitchJob = sv4Scope.launch {
            if (!sv4AudioWaitingForNextReady) {
                kotlinx.coroutines.delay(400L)
            }
            stitchNextVsAudioInternal(nextRecipe)
        }
    }

    private suspend fun stitchNextVsAudioInternal(nextRecipe: VsVariationRecipe) {
        val specs = expandAudioRecipeToSpecs(nextRecipe)
        if (specs.isEmpty()) return

        val curVsa = sv4AudioCurrentRecipe?.vsNumber ?: _ifsState.value.sv4AudioCurrentVsNumber.coerceAtLeast(1)
        val curFormula = sv4AudioCurrentRecipe?.formulaText ?: _ifsState.value.sv4AudioActiveFormula
        sv4AudioNextRecipe = nextRecipe
        saveSv4AudioRollingStateToPrefs()

        _ifsState.value = _ifsState.value.copy(
            sv4AudioNextVsNumber = nextRecipe.vsNumber,
            sv4AudioNextFormula = nextRecipe.formulaText,
            sv4AudioIsStitchingNext = true,
            sv4AudioNextStitchPercent = 0,
            sv4AudioNextReady = false,
            sv4AudioStatusMessage = "🎵 Play VSA$curVsa [$curFormula] • ⚙️ Menggabungkan VSA${nextRecipe.vsNumber} [${nextRecipe.formulaText}]..."
        )

        val res = com.tajasb.engine.SmartAudioStitcher.stitchAudioVariation(
            context = appContext,
            vsNumber = nextRecipe.vsNumber,
            segments = specs,
            onProgress = { pct, _ ->
                if (_audioMode.value == IfsAudioMode.SV4_VS) {
                    _ifsState.value = _ifsState.value.copy(
                        sv4AudioIsStitchingNext = true,
                        sv4AudioNextStitchPercent = pct,
                        sv4AudioStatusMessage = "🎵 Play VSA$curVsa [$curFormula] • ⚙️ Gabung VSA${nextRecipe.vsNumber} ($pct%)"
                    )
                }
            }
        )

        if (_audioMode.value != IfsAudioMode.SV4_VS) return

        if (res != null && res.outputFile.exists() && res.outputFile.length() > 0L) {
            val nextItem = AudioItem(
                id = "vs_audio_rolling_${nextRecipe.vsNumber}",
                name = "⏳ [VSA${nextRecipe.vsNumber} Siap] ${nextRecipe.formulaText}",
                path = res.outputFile.absolutePath,
                uri = android.net.Uri.fromFile(res.outputFile).toString(),
                durationMs = res.durationMs,
                sizeBytes = res.outputFile.length(),
                isMuted = false,
                isEnabled = true
            )
            sv4AudioNextRecipe = nextRecipe
            sv4AudioNextItem = nextItem
            sv4AudioNextFile = res.outputFile
            saveSv4AudioRollingStateToPrefs()

            try {
                VcamRepository.getInstance(appContext).insertNextQueuedVsAudioIntoPlaylistSync(nextItem)
            } catch (_: Throwable) {}

            _ifsState.value = _ifsState.value.copy(
                sv4AudioNextVsNumber = nextRecipe.vsNumber,
                sv4AudioNextFormula = nextRecipe.formulaText,
                sv4AudioIsStitchingNext = false,
                sv4AudioNextStitchPercent = 100,
                sv4AudioNextReady = true,
                sv4AudioStatusMessage = "🎵 Play VSA$curVsa [$curFormula] • ✅ VSA${nextRecipe.vsNumber} [${nextRecipe.formulaText}] Masuk Daftar (#2)!"
            )

            if (sv4AudioWaitingForNextReady) {
                sv4AudioWaitingForNextReady = false
                Log.i(TAG, "🚀 [SV4-AUDIO-PROMOTE] VSA${nextRecipe.vsNumber} selesai digabung saat VSA$curVsa sudah selesai, langsung Play VSA${nextRecipe.vsNumber}!")
                onSv4AudioCompleted(forceAdvance = true)
            }
        } else {
            _ifsState.value = _ifsState.value.copy(
                sv4AudioIsStitchingNext = false,
                sv4AudioNextReady = false,
                sv4AudioStatusMessage = "🎵 Play VSA$curVsa [$curFormula] • ⚠️ Gagal gabung VSA${nextRecipe.vsNumber}, mencoba variasi baru..."
            )
            kotlinx.coroutines.delay(500L)
            if (_audioMode.value == IfsAudioMode.SV4_VS && sv4AudioNextItem == null) {
                val retryNum = maxOf(sv4AudioVsCounter + 1, nextRecipe.vsNumber + 1)
                sv4AudioVsCounter = retryNum
                val retryRecipe = generateVsAudioVariationRecipe(retryNum)
                if (retryRecipe != null) {
                    stitchNextVsAudioInternal(retryRecipe)
                }
            }
        }
    }

    @Synchronized
    fun onSv4AudioCompleted(forceAdvance: Boolean = false): AudioItem? {
        if (_audioMode.value != IfsAudioMode.SV4_VS) return null
        val now = System.currentTimeMillis()
        val debounceMs = if (sv4AudioNextItem != null) 80L else 220L
        if (!forceAdvance && now - lastAudioAdvanceTimeMs < debounceMs) {
            return sv4AudioCurrentItem
        }
        val curAudioDurMs = sv4AudioCurrentItem?.durationMs ?: 4000L
        val minAudioPlayGuardMs = (curAudioDurMs * 55L / 100L).coerceIn(1_500L, 4_500L)
        if (!forceAdvance && sv4AudioLastPromotedTimeMs > 0L && (now - sv4AudioLastPromotedTimeMs) < minAudioPlayGuardMs) {
            Log.d(TAG, "onSv4AudioCompleted: Mengabaikan sinyal completion duplikat/prematur (${now - sv4AudioLastPromotedTimeMs}ms < ${minAudioPlayGuardMs}ms) untuk ${sv4AudioCurrentItem?.name}")
            return sv4AudioCurrentItem
        }
        lastAudioAdvanceTimeMs = now

        val curState = _ifsState.value
        val readyNextItem = sv4AudioNextItem
        val readyNextRecipe = sv4AudioNextRecipe
        val readyNextFile = sv4AudioNextFile

        if (readyNextItem != null && readyNextRecipe != null && readyNextFile != null && readyNextFile.exists()) {
            sv4AudioWaitingForNextReady = false
            val previousVsItem = sv4AudioCurrentItem
            val previousVsFile = sv4AudioCurrentFile
            val previousVsNum = sv4AudioCurrentRecipe?.vsNumber ?: curState.sv4AudioCurrentVsNumber

            val promotedNextItem = readyNextItem.copy(
                name = "🎵 [VSA${readyNextRecipe.vsNumber}] ${readyNextRecipe.formulaText}"
            )

            sv4AudioCurrentRecipe = readyNextRecipe
            sv4AudioCurrentItem = promotedNextItem
            sv4AudioCurrentFile = readyNextFile
            sv4AudioNextRecipe = null
            sv4AudioNextItem = null
            sv4AudioNextFile = null
            sv4AudioLastPromotedTimeMs = now

            try {
                VcamRepository.getInstance(appContext).advanceToNextVsAudioAndRemovePreviousSync(
                    nextVsAudio = promotedNextItem,
                    previousVsId = previousVsItem?.id?.takeIf { it.startsWith("vs_audio_rolling_") },
                    startPlaying = true
                )
            } catch (_: Throwable) {}

            scheduleSv4AudioPlaybackWatchdog(promotedNextItem)

            if (previousVsFile != null && previousVsFile.absolutePath != readyNextFile.absolutePath) {
                sv4AudioAutoDeletedFilesCount += 1
                val keepNums = setOf(readyNextRecipe.vsNumber)
                sv4Scope.launch {
                    kotlinx.coroutines.delay(500L)
                    try {
                        if (previousVsFile.exists()) {
                            previousVsFile.delete()
                        }
                        com.tajasb.engine.SmartAudioStitcher.cleanupOldRollingVsAudioFiles(appContext, keepNums)
                    } catch (_: Throwable) {}
                }
            }

            val upcomingVsNum = maxOf(sv4AudioVsCounter + 1, readyNextRecipe.vsNumber + 1)
            sv4AudioVsCounter = upcomingVsNum
            val upcomingRecipe = generateVsAudioVariationRecipe(upcomingVsNum)
            sv4AudioNextRecipe = upcomingRecipe
            saveSv4AudioRollingStateToPrefs()

            _ifsState.value = curState.copy(
                sv4AudioCurrentVsNumber = readyNextRecipe.vsNumber,
                sv4AudioActiveFormula = readyNextRecipe.formulaText,
                sv4AudioNextVsNumber = upcomingVsNum,
                sv4AudioNextFormula = upcomingRecipe?.formulaText ?: "",
                sv4AudioIsStitchingNext = upcomingRecipe != null,
                sv4AudioNextStitchPercent = 0,
                sv4AudioNextReady = false,
                sv4AudioAutoDeletedCount = sv4AudioAutoDeletedFilesCount,
                sv4AudioTotalLCount = _sv4AudioLIds.value.size,
                sv4AudioStatusMessage = "🎵 Play VSA${readyNextRecipe.vsNumber} [${readyNextRecipe.formulaText}] • 🗑️ VSA$previousVsNum Dihapus • ⚙️ Gabung VSA$upcomingVsNum..."
            )

            if (upcomingRecipe != null) {
                launchNextVsAudioBackgroundStitch(upcomingRecipe)
            }
            return promotedNextItem
        } else {
            sv4AudioWaitingForNextReady = true
            val curItem = sv4AudioCurrentItem
            if (sv4AudioStitchJob?.isActive != true) {
                val baseActiveNum = maxOf(
                    sv4AudioCurrentRecipe?.vsNumber ?: 0,
                    curState.sv4AudioCurrentVsNumber,
                    sv4AudioVsCounter
                ).coerceAtLeast(1)
                val nextNum = sv4AudioNextRecipe?.vsNumber?.takeIf { it > baseActiveNum } ?: (baseActiveNum + 1)
                sv4AudioVsCounter = maxOf(sv4AudioVsCounter, nextNum)
                val nextRec = sv4AudioNextRecipe?.takeIf { it.vsNumber == nextNum && it.segments.isNotEmpty() }
                    ?: generateVsAudioVariationRecipe(nextNum)
                sv4AudioNextRecipe = nextRec
                saveSv4AudioRollingStateToPrefs()
                if (nextRec != null) {
                    launchNextVsAudioBackgroundStitch(nextRec)
                }
            }
            if (curItem != null) {
                sv4AudioLastPromotedTimeMs = now
                scheduleSv4AudioPlaybackWatchdog(curItem)
                scope.launch(Dispatchers.Main) {
                    try {
                        musicPlayer.playAudioItem(curItem, startPlaying = true, startPositionMs = 0L)
                    } catch (_: Throwable) {}
                }
            }
            return curItem
        }
    }

    fun cancelAndCleanupSv4AudioRollingFiles() {
        sv4AudioStitchJob?.cancel()
        sv4AudioStitchJob = null
        sv4AudioWatchdogJob?.cancel()
        sv4AudioWatchdogJob = null
        sv4AudioIsInitialWarmupActive = false
        sv4AudioWaitingForNextReady = false
        sv4AudioCurrentItem = null
        sv4AudioCurrentFile = null
        sv4AudioNextItem = null
        sv4AudioNextFile = null
        saveSv4AudioRollingStateToPrefs()
        try {
            VcamRepository.getInstance(appContext).cleanupAllRollingVsAudioFromPlaylistSync()
        } catch (_: Throwable) {}
        sv4Scope.launch {
            com.tajasb.engine.SmartAudioStitcher.clearAllRollingVsAudioFiles(appContext)
        }
    }

    fun resolveLinkedAudioItem(vtNumber: Int): AudioItem? {
        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null } ?: return null
        val audioList = repo.config.value.audioPlaylist
        if (audioList.isEmpty()) return null

        val pkg = getPackageByNumber(vtNumber)
        val explicitId = pkg?.linkedAudioId
        if (!explicitId.isNullOrBlank()) {
            val matched = audioList.firstOrNull { it.id == explicitId && it.isEnabled }
                ?: audioList.firstOrNull { it.id == explicitId }
            if (matched != null) return matched
        }
        return null
    }

    fun playLinkedAxForVt(vtNumber: Int) {
        if (_audioMode.value == IfsAudioMode.SV4_VS) {
            if (!musicPlayer.isPlaying.value && sv4AudioCurrentItem != null) {
                musicPlayer.playAudioItem(sv4AudioCurrentItem!!, startPlaying = true)
            } else if (sv4AudioCurrentItem == null && sv4AudioStitchJob?.isActive != true) {
                startSv4AudioRollingCycle(resetCounter = false)
            }
            return
        }
        if (vtNumber == 0 || _videoMode.value == IfsVideoMode.SV3 || _videoMode.value == IfsVideoMode.SV4) {
            // ABA pada SV1 serta seluruh mode SV3 & SV4 memutar musik latar SR secara kontinyu
            val srStarted = musicPlayer.ensureSrPlaying()
            Log.i(TAG, "🎵 Standby: Sistem Random (SR) aktif=$srStarted")
            return
        }
        val targetAudio = resolveLinkedAudioItem(vtNumber)
        val mode = _videoMode.value
        val axLabel = "AX$vtNumber"
        val targetName = if (mode == IfsVideoMode.SV2) "PM$vtNumber" else "VT$vtNumber"
        if (targetAudio == null) {
            val srStarted = musicPlayer.ensureSrPlaying()
            Log.i(TAG, "🎵 [$axLabel] Tidak ada trigger MP3 terkait $targetName -> Sistem Random (SR) aktif=$srStarted")
            return
        }
        musicPlayer.playTriggeredAx(targetAudio, vtNumber, axLabel) {
            Log.i(TAG, "✅ Audio $axLabel (${targetAudio.name}) selesai diputar 100%. Melanjutkan SR dari detik terakhir...")
        }
    }

    /**
     * Dipanggil saat halaman Preview dibuka atau playlist audio berubah dalam mode IFS:
     * memastikan audio AX terkait (jika ada), SV4 Audio (VSA), atau Sistem Random (SR) langsung aktif berputar.
     */
    fun ensureAudioActiveInPreview() {
        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null }
        val audioList = repo?.config?.value?.audioPlaylist ?: emptyList()
        if (audioList.isEmpty()) return

        if (_audioMode.value == IfsAudioMode.SV4_VS) {
            if (_sv4AudioRunning.value && !musicPlayer.isPlaying.value && !musicPlayer.isPreparing) {
                val curVsa = sv4AudioCurrentItem
                if (curVsa != null && File(curVsa.path).exists()) {
                    musicPlayer.playAudioItem(curVsa, startPlaying = true)
                } else if (sv4AudioStitchJob?.isActive != true) {
                    startSv4AudioRollingCycle(resetCounter = false)
                }
            }
            return
        }

        val isEffectiveIfs = _isIfsActive.value || (repo?.config?.value?.isIfsMode == true)
        if (!isEffectiveIfs) return

        musicPlayer.setSrTrackFilter { !it.id.startsWith("vs_audio_rolling_") && isTrackInSr(it.id) }
        applyAudioModeToMusicPlayer(_audioMode.value)
        if (!musicPlayer.isPlaying.value && !musicPlayer.isPreparing && repo?.config?.value?.isMp3AudioPaused != true) {
            val curState = _ifsState.value
            val curVt = curState.activeVtNumber
            val hasLinked = resolveLinkedAudioItem(curVt) != null
            val isAxEntryPhase = (curState.activeSubPhase == IfsSubPhase.CONTENT) ||
                (_videoMode.value == IfsVideoMode.SV2 && curState.activeSubPhase == IfsSubPhase.ABB)
            if (hasLinked && isAxEntryPhase && _videoMode.value != IfsVideoMode.SV3 && _videoMode.value != IfsVideoMode.SV4) {
                playLinkedAxForVt(curVt)
            } else {
                musicPlayer.ensureSrPlaying()
            }
        }
    }

    // =========================================================================
    // TRIGGER & SIKLUS STATE MACHINE IFS (SV1, SV2, SV3, & SV4)
    // =========================================================================

    /**
     * Trigger manual dari host live atau hasil scan komentar penonton (SC).
     * Pada SV1: targetVtNumber = 0 (VTM) atau 1, 2, 3..n (VT1, VT2, VT3..n)
     * Pada SV2: targetVtNumber = 1, 2, 3..n (VT1, VT2, VT3..n)
     * Pada SV3 & SV4: Murni rotasi otomatis (jika dipanggil manual akan melompat ke fase/variasi berikutnya).
     */
    fun triggerVt(targetVtNumber: Int, senderName: String = "Host Live", commentText: String = "Manual"): Boolean {
        if (!_isIfsActive.value) {
            setIfsEnabled(true)
        }
        val mode = _videoMode.value
        if (mode == IfsVideoMode.SV3 || mode == IfsVideoMode.SV4) {
            advancePhaseManually()
            return true
        }
        val curState = _ifsState.value
        val curVt = curState.activeVtNumber
        val curPhase = curState.activeSubPhase

        Log.i(TAG, "🎯 [IFS-TRIGGER] Masuk trigger target=$targetVtNumber dari $senderName ('$commentText') | Mode=${mode.name}, Posisi=${if (curVt == 0) "ABA" else if (mode == IfsVideoMode.SV2) "PM$curVt" else "VT$curVt"} ${curPhase.name}")

        if (mode == IfsVideoMode.SV1) {
            // ATURAN SV1:
            // targetVtNumber 0 adalah ABA (standby default).
            // Jika dipanggil (misalnya dari tombol reset), kembalikan ke ABA ABB dan pastikan SR aktif:
            if (targetVtNumber == 0) {
                _ifsState.value = curState.copy(
                    activeVtNumber = 0,
                    activeSubPhase = IfsSubPhase.ABB,
                    pendingTargetVtNumber = null,
                    statusMessage = "🔄 Kembali ke ABA Standby • A (ABB)"
                )
                musicPlayer.ensureSrPlaying()
                syncCurrentVideoToRepository(broadcastToHook = true)
                return true
            }

            // 2. Jika terpicu VT1, VT2, VT3..n (targetVtNumber >= 1):
            if (targetVtNumber >= 1) {
                if (curVt == 0 && curPhase == IfsSubPhase.BL && curState.pendingTargetVtNumber == null) {
                    // Sedang looping di ABA BL: siapkan target transisi ke ABA BBT setelah putaran BL selesai
                    _ifsState.value = curState.copy(
                        pendingTargetVtNumber = targetVtNumber,
                        statusMessage = "⏳ Menunggu putaran ABA BL selesai ➡️ ABA BBT ➡️ VT$targetVtNumber"
                    )
                    try {
                        val repo = VcamRepository.getInstance(appContext)
                        repo.broadcastControlEvent(
                            action = "IFS_QUEUE_TRIGGER",
                            extraSource = "IFS_TRIGGER"
                        )
                    } catch (_: Throwable) {}
                    return true
                } else {
                    // Sedang di fase transisi (ABB / BBT / CONTENT) -> Masukkan ke antrean FIFO
                    val updatedQueue = curState.queue + IfsQueueItem(
                        targetVtNumber = targetVtNumber,
                        senderName = senderName,
                        commentText = commentText
                    )
                    _ifsState.value = curState.copy(
                        queue = updatedQueue,
                        statusMessage = "📋 Antre VT$targetVtNumber (#${updatedQueue.size} di antrean FIFO)"
                    )
                    return true
                }
            }
        } else {
            // ATURAN SV2 (Semua PM1..n 3 Video A+B+A: ABB, BL, BBT):
            val validTarget = targetVtNumber.coerceAtLeast(1)
            // 1. Jika terpicu PM yang sama saat sedang berada di BL PM tersebut:
            //    "Langsung play AX terkait dan JANGAN ubah BL PM tersebut"
            if (validTarget == curVt && curPhase == IfsSubPhase.BL) {
                playLinkedAxForVt(curVt)
                _ifsState.value = curState.copy(statusMessage = "🔊 Play AX-PM$curVt di PM$curVt BL (Video tetap fokus looping)")
                return true
            }

            // 2. Jika terpicu PM lain:
            if (validTarget != curVt) {
                if (curPhase == IfsSubPhase.BL && curState.pendingTargetVtNumber == null) {
                    _ifsState.value = curState.copy(
                        pendingTargetVtNumber = validTarget,
                        statusMessage = "⏳ Menunggu BL PM$curVt habis ➡️ BBT PM$curVt ➡️ ABB PM$validTarget"
                    )
                    try {
                        val repo = VcamRepository.getInstance(appContext)
                        repo.broadcastControlEvent(
                            action = "IFS_QUEUE_TRIGGER",
                            extraSource = "IFS_TRIGGER"
                        )
                    } catch (_: Throwable) {}
                    return true
                } else {
                    val updatedQueue = curState.queue + IfsQueueItem(
                        targetVtNumber = validTarget,
                        senderName = senderName,
                        commentText = commentText
                    )
                    _ifsState.value = curState.copy(
                        queue = updatedQueue,
                        statusMessage = "📋 Antre PM$validTarget (#${updatedQueue.size} di antrean FIFO)"
                    )
                    return true
                }
            }
        }
        return false
    }

    /**
     * Memajukan siklus secara paksa 1 langkah (untuk tombol Maju Fase / Tes Siklus).
     */
    fun advancePhaseManually(): VideoItem? {
        return onVideoCompleted(forceAdvanceFromBl = true, broadcastToHook = true)
    }

    /**
     * Dipanggil otomatis saat satu video mencapai 100% durasi selesai.
     * Menentukan fase berikutnya sesuai aturan ketat SV1 / SV2 / SV3 / SV4 dan menyinkronkan ke Repository + Hook.
     */
    @Synchronized
    fun onVideoCompleted(
        forceAdvanceFromBl: Boolean = false,
        broadcastToHook: Boolean = true,
        completedVideoPath: String? = null
    ): VideoItem? {
        if (!_isIfsActive.value) return null
        val mode = _videoMode.value
        val now = System.currentTimeMillis()
        // Untuk SV4 yang sudah memiliki VS berikutnya siap di antrean, izinkan transisi langsung tanpa tertahan debounce
        val debounceWindowMs = if (mode == IfsVideoMode.SV4 && sv4NextVideoItem != null) 80L else 420L
        if (!forceAdvanceFromBl && now - lastAdvanceTimeMs < debounceWindowMs) {
            return if (mode == IfsVideoMode.SV4) {
                sv4CurrentVideoItem ?: resolveVideoItem(_ifsState.value.activeVtNumber, IfsSubPhase.VS)
            } else {
                resolveVideoItem(_ifsState.value.activeVtNumber, _ifsState.value.activeSubPhase)
            }
        }

        val curState = _ifsState.value

        // =========================================================================
        // LOGIKA SIKLUS OTOMATIS MODE SV4 — MODE VS (ROLLING JIT STITCH + AUTO-DELETE)
        // =========================================================================
        if (mode == IfsVideoMode.SV4) {
            // Jika masih dalam Mode Persiapan SV4 (belum klik MULAI SV4), ulangi pratinjau tanpa memicu peracikan VS di background!
            if (!_sv4VideoRunning.value && !forceAdvanceFromBl) {
                val prepItem = resolveVideoItem(0, IfsSubPhase.VS)
                _ifsState.value = curState.copy(
                    phaseStepVersion = curState.phaseStepVersion + 1L
                )
                try {
                    VcamRepository.getInstance(appContext).bumpPlaybackStepVersion()
                } catch (_: Throwable) {}
                return prepItem
            }

            // Pastikan musik latar selalu aktif sesuai mode audio pilihan pengguna jika tidak dijeda manual
            if (!musicPlayer.isPlayingTriggeredAx.value && !musicPlayer.isPlaying.value) {
                val isManuallyPaused = try { VcamRepository.getInstance(appContext).config.value.isMp3AudioPaused } catch (_: Throwable) { false }
                if (!isManuallyPaused) {
                    if (_audioMode.value == IfsAudioMode.SV4_VS) {
                        ensureAudioActiveInPreview()
                    } else {
                        applyAudioModeToMusicPlayer(_audioMode.value)
                        musicPlayer.ensureSrPlaying()
                    }
                }
            }

            // Jika masih dalam tahap jembatan awal selagi VS1 pertama kali digabung, ulangi jembatan tanpa menaikkan counter VS
            if (sv4IsInitialWarmupActive && !forceAdvanceFromBl) {
                _ifsState.value = curState.copy(
                    phaseStepVersion = curState.phaseStepVersion + 1L
                )
                try {
                    VcamRepository.getInstance(appContext).bumpPlaybackStepVersion()
                } catch (_: Throwable) {}
                return sv4CurrentVideoItem
            }

            // Proteksi Anti-Loncatan #1: Jika ada sinyal completion terlambat dari file VS sebelumnya yang sudah digantikan, abaikan!
            if (!forceAdvanceFromBl && !completedVideoPath.isNullOrBlank()) {
                val reportedName = File(completedVideoPath).name.removePrefix("cached_")
                val activeName = sv4CurrentFile?.name ?: sv4CurrentVideoItem?.path?.let { File(it).name }
                if (reportedName.startsWith("vs_rolling_") && !activeName.isNullOrBlank() && reportedName != activeName) {
                    Log.d(TAG, "onVideoCompleted(SV4): Mengabaikan sinyal completion usang dari $reportedName (aktif saat ini: $activeName)")
                    return sv4CurrentVideoItem
                }
            }

            // Proteksi Anti-Loncatan #2: Tolak sinyal completion ganda/prematur saat VS baru saja dipromosikan (< 4000ms)
            // atau jika Hook sedang memutar VS dan posisinya belum mencapai akhir video (> 95% durasi)
            val minVsPlayGuardMs = 4000L
            if (!forceAdvanceFromBl && sv4LastPromotedTimeMs > 0L && (now - sv4LastPromotedTimeMs) < minVsPlayGuardMs) {
                Log.d(TAG, "onVideoCompleted(SV4): Mengabaikan sinyal completion prematur (${now - sv4LastPromotedTimeMs}ms < ${minVsPlayGuardMs}ms) untuk ${sv4CurrentVideoItem?.name}")
                return sv4CurrentVideoItem
            }

            val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null }
            val isLiveHookReporting = repo != null && (now - repo.lastRealPlaybackTime.value) < 3000L
            if (!forceAdvanceFromBl && isLiveHookReporting) {
                val realPos = repo?.realVideoPositionMs?.value ?: 0L
                val realDur = repo?.realVideoDurationMs?.value ?: 0L
                val expectedDur = sv4CurrentVideoItem?.durationMs ?: 0L
                val effDur = if (realDur > 2000L) realDur else expectedDur
                if (effDur > 4000L && realPos > 0L && realPos < (effDur - 1200L)) {
                    Log.d(TAG, "onVideoCompleted(SV4): Mengabaikan completion prematur di detik ${realPos}ms/${effDur}ms selagi hook aktif memutar!")
                    return sv4CurrentVideoItem
                }
            }
            lastAdvanceTimeMs = now

            val readyNextItem = sv4NextVideoItem
            val readyNextRecipe = sv4NextRecipe
            val readyNextFile = sv4NextFile

            if (readyNextItem != null && readyNextRecipe != null && readyNextFile != null && readyNextFile.exists()) {
                // VS berikutnya (VS2, VS3, VS4, dst.) sudah siap di Daftar Video!
                sv4WaitingForNextReady = false
                val previousVsItem = sv4CurrentVideoItem
                val previousVsFile = sv4CurrentFile
                val previousVsNum = sv4CurrentRecipe?.vsNumber ?: curState.sv4CurrentVsNumber

                val promotedNextItem = readyNextItem.copy(
                    name = "🎬 [VS${readyNextRecipe.vsNumber}] ${readyNextRecipe.formulaText}"
                )

                // 1. Pindahkan VS(k+1) menjadi VS Aktif (ON-AIR)
                sv4CurrentRecipe = readyNextRecipe
                sv4CurrentVideoItem = promotedNextItem
                sv4CurrentFile = readyNextFile
                sv4NextRecipe = null
                sv4NextVideoItem = null
                sv4NextFile = null
                sv4LastPromotedTimeMs = now

                // 2. Promosikan VS(k+1) ke #1 AKTIF di Daftar Video, hapus VS(k) dari Daftar Video, & langsung Play VS(k+1)!
                try {
                    VcamRepository.getInstance(appContext).advanceToNextVsAndRemovePreviousSync(
                        nextVsVideo = promotedNextItem,
                        previousVsId = previousVsItem?.id?.takeIf { it.startsWith("vs_rolling_") },
                        broadcastToHook = broadcastToHook
                    )
                } catch (_: Throwable) {}

                // Jadwalkan watchdog durasi untuk VS(k+1) yang baru mulai tayang
                scheduleSv4PlaybackWatchdog(promotedNextItem)

                // 3. Hapus file penggabungan VS lama (VS(k)) dari penyimpanan HP agar memori telepon tidak penuh!
                if (previousVsFile != null && previousVsFile.absolutePath != readyNextFile.absolutePath) {
                    sv4AutoDeletedFilesCount += 1
                    val deletedCountSnapshot = sv4AutoDeletedFilesCount
                    val keepSet = setOf(readyNextFile.name)
                    sv4Scope.launch {
                        kotlinx.coroutines.delay(650L)
                        try {
                            if (previousVsFile.exists()) {
                                val deleted = previousVsFile.delete()
                                Log.i(TAG, "🗑️ [SV4-AUTO-CLEANUP] File gabungan lama VS$previousVsNum (${previousVsFile.name}) dihapus=$deleted (Total dihapus: $deletedCountSnapshot)")
                            }
                            cleanupAllRollingVsFilesExcept(keepFileNames = keepSet)
                        } catch (_: Throwable) {}
                    }
                }

                // 4. Siapkan resep variasi berikutnya lagi (VS(k+2)) untuk digabungkan di background selagi VS(k+1) tayang
                val upcomingVsNum = maxOf(sv4VsCounter + 1, readyNextRecipe.vsNumber + 1)
                sv4VsCounter = upcomingVsNum
                val upcomingRecipe = generateVsVariationRecipe(upcomingVsNum)
                sv4NextRecipe = upcomingRecipe
                saveSv4RollingStateToPrefs()

                val repoRef = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null }
                val curCfgRef = repoRef?.config?.value
                _ifsState.value = curState.copy(
                    activeVtNumber = readyNextRecipe.vsNumber,
                    activeSubPhase = IfsSubPhase.VS,
                    currentVideoId = promotedNextItem.id,
                    currentVideoName = promotedNextItem.name,
                    sv4CurrentVsNumber = readyNextRecipe.vsNumber,
                    sv4ActiveFormula = readyNextRecipe.formulaText,
                    sv4NextVsNumber = upcomingVsNum,
                    sv4NextFormula = upcomingRecipe?.formulaText ?: "",
                    sv4IsStitchingNext = upcomingRecipe != null,
                    sv4NextStitchPercent = 0,
                    sv4NextReady = false,
                    sv4AutoDeletedCount = sv4AutoDeletedFilesCount,
                    sv4TotalLCount = _sv4LVideoIds.value.size,
                    sv4TwoMinuteGuardActive = true,
                    sv4GuardChoreographyCount2m = getSv4ChoreographyHistoryInLast2m().size,
                    sv4GuardAudioCount2m = getSv4AudioChoreographyHistoryInLast2m().size,
                    sv4GuardCurrentZoom = if (curCfgRef?.isDnaZoomEnabled == true) curCfgRef.sessionDnaCustomZoom else 1.00f,
                    sv4GuardCurrentDnaBadge = if (curCfgRef?.enableDnaModule == true) curCfgRef.activeSessionDna.badgeText else "OFF",
                    statusMessage = "🎬 Play VS${readyNextRecipe.vsNumber} [${readyNextRecipe.formulaText}] • 🛡️ Guard 2m: Bebas Kembar • 🗑️ VS$previousVsNum Dihapus • ⚙️ Gabung VS$upcomingVsNum...",
                    phaseStepVersion = curState.phaseStepVersion + 1L
                )

                if (upcomingRecipe != null) {
                    launchNextVsBackgroundStitch(upcomingRecipe)
                }
                return promotedNextItem
            } else {
                // VS(k) telah selesai diputar namun VS(k+1) masih sedang menyelesaikan penggabungan di background.
                // Tandai sv4WaitingForNextReady = true agar begitu stitchNextVsInternal selesai (dalam hitungan milidetik/detik),
                // VS(k+1) LANGSUNG dipromosikan dan di-play otomatis tanpa menunggu klik manual!
                sv4WaitingForNextReady = true
                val curItem = sv4CurrentVideoItem ?: resolveVideoItem(curState.activeVtNumber, IfsSubPhase.VS)
                if (sv4StitchJob?.isActive != true) {
                    val baseActiveNum = maxOf(
                        sv4CurrentRecipe?.vsNumber ?: 0,
                        curState.sv4CurrentVsNumber,
                        sv4VsCounter
                    ).coerceAtLeast(1)
                    val nextNum = sv4NextRecipe?.vsNumber?.takeIf { it > baseActiveNum } ?: (baseActiveNum + 1)
                    sv4VsCounter = maxOf(sv4VsCounter, nextNum)
                    val nextRec = sv4NextRecipe?.takeIf { it.vsNumber == nextNum && it.segments.isNotEmpty() }
                        ?: generateVsVariationRecipe(nextNum)
                    sv4NextRecipe = nextRec
                    saveSv4RollingStateToPrefs()
                    if (nextRec != null) {
                        launchNextVsBackgroundStitch(nextRec)
                    }
                }
                if (curItem != null) {
                    scheduleSv4PlaybackWatchdog(curItem)
                }
                _ifsState.value = curState.copy(
                    phaseStepVersion = curState.phaseStepVersion + 1L
                )
                try {
                    VcamRepository.getInstance(appContext).bumpPlaybackStepVersion()
                } catch (_: Throwable) {}
                return curItem
            }
        }
        lastAdvanceTimeMs = now

        // =========================================================================
        // LOGIKA SIKLUS OTOMATIS MODE SV3 (1 VIDEO VL LOOP ACAK <-> GRUP VB ACAK)
        // =========================================================================
        if (mode == IfsVideoMode.SV3) {
            val curPhase = curState.activeSubPhase
            val vbList = _sv3VbVideoIds.value
            val vbTotal = vbList.size

            // Pastikan musik latar selalu aktif sesuai mode audio pilihan pengguna jika tidak dijeda manual
            if (!musicPlayer.isPlayingTriggeredAx.value && !musicPlayer.isPlaying.value) {
                val isManuallyPaused = try { VcamRepository.getInstance(appContext).config.value.isMp3AudioPaused } catch (_: Throwable) { false }
                if (!isManuallyPaused) {
                    if (_audioMode.value == IfsAudioMode.SV4_VS) {
                        ensureAudioActiveInPreview()
                    } else {
                        applyAudioModeToMusicPlayer(_audioMode.value)
                        musicPlayer.ensureSrPlaying()
                    }
                }
            }

            if (curPhase != IfsSubPhase.VB) {
                // Sedang berada di fase VL (Video Looping Utama)
                val curLoop = curState.sv3CurrentVlLoop.coerceAtLeast(1)
                val targetLoops = curState.sv3TargetVlLoops.coerceAtLeast(1)

                if (!forceAdvanceFromBl && curLoop < targetLoops) {
                    // Masih harus menyelesaikan sisa putaran acak VL sebelum ke VB (Zero-Reload In-Place Loop)
                    val nextLoop = curLoop + 1
                    val vlVideo = resolveVideoItem(0, IfsSubPhase.VL)
                    val remVb = getRemainingSv3VbInCycle()
                    val msg = if (nextLoop == targetLoops && vbTotal > 0) {
                        "🔁 SV3 • VL Putaran Terakhir ($nextLoop/$targetLoops) ➡️ Habis ini ke Grup VB Acak"
                    } else {
                        "🔁 SV3 • VL Putaran $nextLoop/$targetLoops (Target Acak ${targetLoops}x)"
                    }
                    _ifsState.value = curState.copy(
                        activeVtNumber = 0,
                        activeSubPhase = IfsSubPhase.VL,
                        currentVideoId = vlVideo?.id,
                        currentVideoName = vlVideo?.name,
                        statusMessage = msg,
                        phaseStepVersion = curState.phaseStepVersion + 1L,
                        sv3CurrentVlLoop = nextLoop,
                        sv3TargetVlLoops = targetLoops,
                        sv3VbRemainingInCycle = remVb,
                        sv3VbTotalCount = vbTotal
                    )
                    try {
                        val repo = VcamRepository.getInstance(appContext)
                        if (vlVideo != null && repo.config.value.videoPath != vlVideo.path) {
                            syncCurrentVideoToRepository(broadcastToHook = broadcastToHook, bumpPhaseVersion = false)
                        } else {
                            repo.bumpPlaybackStepVersion()
                        }
                    } catch (_: Throwable) {}
                    // Jika sudah memasuki putaran terakhir VL (nextLoop == targetLoops), beri sinyal ke Hook untuk pre-decode VB berikutnya di slot pasif!
                    if (nextLoop == targetLoops && vbTotal > 0) {
                        try {
                            VcamRepository.getInstance(appContext).broadcastControlEvent(
                                action = "IFS_QUEUE_TRIGGER",
                                extraSource = "SV3_FINAL_VL_LOOP_PREWARM"
                            )
                        } catch (_: Throwable) {}
                    }
                    return vlVideo
                } else {
                    // Putaran acak VL sudah habis! Masuk ke 1 video dari Grup VB secara acak non-repeating (1x per siklus)
                    if (vbTotal > 0) {
                        val pickedVbIdx = drawNextSv3VbIndex()
                        val vbNumber = pickedVbIdx + 1
                        val remAfterPick = sv3VbGroupDeck.remainingInCurrentCycle(vbList.indices.toList())
                        val nextStateTemp = curState.copy(
                            activeVtNumber = vbNumber,
                            activeSubPhase = IfsSubPhase.VB,
                            sv3ActiveVbNumber = vbNumber,
                            sv3VbRemainingInCycle = remAfterPick,
                            sv3VbTotalCount = vbTotal
                        )
                        _ifsState.value = nextStateTemp
                        val vbVideo = resolveVideoItem(vbNumber, IfsSubPhase.VB)
                        _ifsState.value = nextStateTemp.copy(
                            currentVideoId = vbVideo?.id,
                            currentVideoName = vbVideo?.name,
                            statusMessage = "🌉 SV3 • VB$vbNumber Tayang 1x ➡️ Balik VL (Sisa Siklus VB: $remAfterPick/$vbTotal)",
                            phaseStepVersion = curState.phaseStepVersion + 1L
                        )
                        syncCurrentVideoToRepository(broadcastToHook = broadcastToHook, bumpPhaseVersion = false)
                        return vbVideo
                    } else {
                        // Jika Grup VB masih kosong, langsung undi jumlah putaran acak baru untuk VL
                        val newTargetLoops = drawNextSv3VlLoopCount()
                        val vlVideo = resolveVideoItem(0, IfsSubPhase.VL)
                        _ifsState.value = curState.copy(
                            activeVtNumber = 0,
                            activeSubPhase = IfsSubPhase.VL,
                            currentVideoId = vlVideo?.id,
                            currentVideoName = vlVideo?.name,
                            statusMessage = "🔁 SV3 • VL Siklus Baru Putaran 1/$newTargetLoops (Grup VB belum diisi)",
                            phaseStepVersion = curState.phaseStepVersion + 1L,
                            sv3CurrentVlLoop = 1,
                            sv3TargetVlLoops = newTargetLoops,
                            sv3VbRemainingInCycle = 0,
                            sv3VbTotalCount = 0
                        )
                        try {
                            VcamRepository.getInstance(appContext).bumpPlaybackStepVersion()
                        } catch (_: Throwable) {}
                        return vlVideo
                    }
                }
            } else {
                // Sedang berada di fase VB dan video VB sudah habis 1x -> Kembali ke VL dengan jumlah looping acak baru!
                val newTargetLoops = drawNextSv3VlLoopCount()
                val remVb = getRemainingSv3VbInCycle()
                val nextStateTemp = curState.copy(
                    activeVtNumber = 0,
                    activeSubPhase = IfsSubPhase.VL,
                    sv3CurrentVlLoop = 1,
                    sv3TargetVlLoops = newTargetLoops,
                    sv3VbRemainingInCycle = remVb,
                    sv3VbTotalCount = vbTotal
                )
                _ifsState.value = nextStateTemp
                val vlVideo = resolveVideoItem(0, IfsSubPhase.VL)
                _ifsState.value = nextStateTemp.copy(
                    currentVideoId = vlVideo?.id,
                    currentVideoName = vlVideo?.name,
                    statusMessage = "🔁 SV3 • Kembali ke VL Putaran 1/$newTargetLoops (Target Acak ${newTargetLoops}x ➡️ VB)",
                    phaseStepVersion = curState.phaseStepVersion + 1L
                )
                syncCurrentVideoToRepository(broadcastToHook = broadcastToHook, bumpPhaseVersion = false)
                return vlVideo
            }
        }

        val curVtNum = curState.activeVtNumber
        val curPhase = curState.activeSubPhase
        var nextPendingTarget = curState.pendingTargetVtNumber
        var nextQueue = curState.queue

        val curLabel = if (curVtNum == 0) "ABA" else if (mode == IfsVideoMode.SV2) "PM$curVtNum" else "VT$curVtNum"
        Log.i(TAG, "🔄 [IFS-CYCLE] onVideoCompleted: Mode=${mode.name}, Paket=$curLabel, Fase=${curPhase.name}, Pending=$nextPendingTarget, Queue=${nextQueue.size}")

        var nextVtNum = curVtNum
        var nextPhase = curPhase
        var statusMsg = curState.statusMessage

        if (mode == IfsVideoMode.SV1) {
            when (curPhase) {
                IfsSubPhase.ABB -> {
                    // Dari ABA A (ABB) wajib masuk ke ABA B (BL)
                    nextVtNum = 0
                    nextPhase = IfsSubPhase.BL
                    // Cek apakah ada antrean FIFO yang sudah menunggu
                    if (nextPendingTarget == null && nextQueue.isNotEmpty()) {
                        nextPendingTarget = nextQueue.first().targetVtNumber
                        nextQueue = nextQueue.drop(1)
                        statusMsg = "🔁 ABA • B (BL) 1 Putaran ➡️ Siap ke BBT (VT$nextPendingTarget)"
                    } else {
                        statusMsg = "🔁 ABA • B (BL) Looping Standby"
                    }
                }
                IfsSubPhase.BL -> {
                    val effectiveTarget = nextPendingTarget
                        ?: nextQueue.firstOrNull()?.targetVtNumber
                        ?: if (forceAdvanceFromBl) 1 else null

                    if (effectiveTarget != null && effectiveTarget >= 1) {
                        if (nextPendingTarget == null && nextQueue.isNotEmpty()) {
                            nextQueue = nextQueue.drop(1)
                        }
                        nextPendingTarget = effectiveTarget
                        nextVtNum = 0
                        nextPhase = IfsSubPhase.BBT
                        statusMsg = "🌉 ABA • A (BBT) Transisi Menuju VT$effectiveTarget"
                    } else {
                        // Tetap looping di ABA BL
                        nextVtNum = 0
                        nextPhase = IfsSubPhase.BL
                        statusMsg = "🔁 ABA • B (BL) Looping Standby"
                    }
                }
                IfsSubPhase.BBT -> {
                    // Setelah ABA BBT habis 100%, masuk ke VT1..n (CONTENT) dan mainkan AX terkait (atau SR jika tidak ada AX)
                    val target = (nextPendingTarget ?: 1).coerceIn(1, _vtPackages.value.size.coerceAtLeast(1))
                    nextVtNum = target
                    nextPhase = IfsSubPhase.CONTENT
                    nextPendingTarget = null

                    val hasAx = resolveLinkedAudioItem(target) != null
                    playLinkedAxForVt(target)
                    statusMsg = if (hasAx) {
                        "🎬 Memutar VT$target (🔊 AX$target Aktif)"
                    } else {
                        "🎬 Memutar VT$target (🎵 SR Aktif)"
                    }
                }
                IfsSubPhase.CONTENT, IfsSubPhase.VL, IfsSubPhase.VB, IfsSubPhase.VS -> {
                    // Setelah VT1..n selesai 100%, wajib kembali ke ABA A (ABB) & pastikan SR/AX-ABA berputar
                    nextVtNum = 0
                    nextPhase = IfsSubPhase.ABB
                    playLinkedAxForVt(0)
                    statusMsg = "🌉 Selesai VT$curVtNum ➡️ Kembali ke ABA • A (ABB)"
                }
            }
        } else {
            // MODE SV2 (Semua PM1..n memiliki 3 Video A+B+A)
            val safeCurVt = curVtNum.coerceIn(1, _vtPackages.value.size.coerceAtLeast(1))
            when (curPhase) {
                IfsSubPhase.ABB -> {
                    nextVtNum = safeCurVt
                    nextPhase = IfsSubPhase.BL
                    if (nextPendingTarget == null && nextQueue.isNotEmpty()) {
                        nextPendingTarget = nextQueue.first().targetVtNumber
                        nextQueue = nextQueue.drop(1)
                        statusMsg = "🔁 PM$nextVtNum • B (BL) ➡️ Antrean Berikutnya PM$nextPendingTarget"
                    } else {
                        statusMsg = "🔁 PM$nextVtNum • B (BL) Fokus Looping"
                    }
                }
                IfsSubPhase.BL -> {
                    val fallbackNextVt = if (_vtPackages.value.size > 1) (safeCurVt % _vtPackages.value.size) + 1 else 1
                    val effectiveTarget = nextPendingTarget
                        ?: nextQueue.firstOrNull()?.targetVtNumber
                        ?: if (forceAdvanceFromBl) fallbackNextVt else null

                    if (effectiveTarget != null && (effectiveTarget != safeCurVt || forceAdvanceFromBl)) {
                        if (nextPendingTarget == null && nextQueue.isNotEmpty()) {
                            nextQueue = nextQueue.drop(1)
                        }
                        nextPendingTarget = effectiveTarget
                        nextVtNum = safeCurVt
                        nextPhase = IfsSubPhase.BBT
                        statusMsg = "🌉 PM$safeCurVt • A (BBT) Transisi Menuju PM$effectiveTarget"
                    } else {
                        nextVtNum = safeCurVt
                        nextPhase = IfsSubPhase.BL
                        statusMsg = "🔁 PM$safeCurVt • B (BL) Fokus Looping"
                    }
                }
                IfsSubPhase.BBT -> {
                    val target = (nextPendingTarget ?: ((safeCurVt % _vtPackages.value.size.coerceAtLeast(1)) + 1))
                        .coerceIn(1, _vtPackages.value.size.coerceAtLeast(1))
                    nextVtNum = target
                    nextPhase = IfsSubPhase.ABB
                    nextPendingTarget = null

                    // Pada SV2: PAUSE SR & PLAY AX tujuan (atau pastikan SR aktif jika tanpa AX) tepat ketika ABB PM tujuan dimulai!
                    val hasAx = resolveLinkedAudioItem(target) != null
                    playLinkedAxForVt(target)
                    statusMsg = if (hasAx) {
                        "✨ Masuk PM$target • A (ABB) (🔊 AX-PM$target Aktif)"
                    } else {
                        "✨ Masuk PM$target • A (ABB) (🎵 SR Aktif)"
                    }
                }
                IfsSubPhase.CONTENT, IfsSubPhase.VL, IfsSubPhase.VB, IfsSubPhase.VS -> {
                    nextVtNum = safeCurVt
                    nextPhase = IfsSubPhase.BL
                    statusMsg = "🔁 PM$safeCurVt • B (BL) Fokus Looping"
                }
            }
        }

        // Pastikan musik latar selalu aktif jika tidak ada AX yang sedang diputar
        if (!musicPlayer.isPlayingTriggeredAx.value && !musicPlayer.isPlaying.value) {
            if (_audioMode.value == IfsAudioMode.SV4_VS) {
                ensureAudioActiveInPreview()
            } else {
                musicPlayer.ensureSrPlaying()
            }
        }

        val isSameBlLoop = (curVtNum == nextVtNum && curPhase == IfsSubPhase.BL && nextPhase == IfsSubPhase.BL && !forceAdvanceFromBl)
        val nextVideo = resolveVideoItem(nextVtNum, nextPhase)
        _ifsState.value = curState.copy(
            activeVtNumber = nextVtNum,
            activeSubPhase = nextPhase,
            pendingTargetVtNumber = nextPendingTarget,
            queue = nextQueue,
            currentVideoId = nextVideo?.id,
            currentVideoName = nextVideo?.name,
            statusMessage = statusMsg,
            phaseStepVersion = curState.phaseStepVersion + 1L
        )

        if (!isSameBlLoop) {
            syncCurrentVideoToRepository(broadcastToHook = broadcastToHook, bumpPhaseVersion = false)
        } else {
            try {
                VcamRepository.getInstance(appContext).bumpPlaybackStepVersion()
            } catch (_: Throwable) {}
        }
        return nextVideo
    }

    /**
     * Memprediksi video fase berikutnya secara deterministik tanpa mengubah state dek atau antrean:
     * Mengembalikan Pair(nextVideoItem, isSameSlotLoop).
     * Digunakan oleh True Pre-Decoded Dual-Slot Engine agar Frame #1 video fase berikutnya sudah didekode
     * di GPU VRAM sebelum video aktif selesai.
     */
    fun peekNextPhaseVideo(): Pair<VideoItem?, Boolean> {
        if (!_isIfsActive.value) return Pair(null, false)
        val mode = _videoMode.value
        val curState = _ifsState.value
        val curPhase = curState.activeSubPhase

        if (mode == IfsVideoMode.SV4) {
            val readyNext = sv4NextVideoItem
            val readyFile = sv4NextFile
            return if (readyNext != null && readyFile != null && readyFile.exists()) {
                Pair(readyNext, false)
            } else {
                Pair(sv4CurrentVideoItem, true)
            }
        }

        if (mode == IfsVideoMode.SV3) {
            val vbList = _sv3VbVideoIds.value
            return if (curPhase != IfsSubPhase.VB) {
                val curLoop = curState.sv3CurrentVlLoop.coerceAtLeast(1)
                val targetLoops = curState.sv3TargetVlLoops.coerceAtLeast(1)
                if (curLoop < targetLoops || vbList.isEmpty()) {
                    // Masih harus mengulang VL di slot yang sama
                    Pair(resolveVideoItem(0, IfsSubPhase.VL), true)
                } else {
                    // Putaran terakhir VL (curLoop >= targetLoops): intip VB berikutnya dari dek tanpa mengonsumsinya
                    val peekedVbIdx = sv3VbGroupDeck.peekNextShuffledIndex(vbList.indices.toList(), lastPickedSv3VbIdx)
                    val vbNumber = peekedVbIdx + 1
                    Pair(resolveVideoItem(vbNumber, IfsSubPhase.VB), false)
                }
            } else {
                // Sedang di VB -> berikutnya pasti kembali ke VL
                Pair(resolveVideoItem(0, IfsSubPhase.VL), false)
            }
        }

        if (mode == IfsVideoMode.SV1) {
            return when (curPhase) {
                IfsSubPhase.ABB -> Pair(resolveVideoItem(0, IfsSubPhase.BL), false)
                IfsSubPhase.BL -> {
                    val effectiveTarget = curState.pendingTargetVtNumber ?: curState.queue.firstOrNull()?.targetVtNumber
                    if (effectiveTarget != null && effectiveTarget >= 1) {
                        Pair(resolveVideoItem(0, IfsSubPhase.BBT), false)
                    } else {
                        Pair(resolveVideoItem(0, IfsSubPhase.BL), true)
                    }
                }
                IfsSubPhase.BBT -> {
                    val target = (curState.pendingTargetVtNumber ?: curState.queue.firstOrNull()?.targetVtNumber ?: 1)
                        .coerceIn(1, _vtPackages.value.size.coerceAtLeast(1))
                    Pair(resolveVideoItem(target, IfsSubPhase.CONTENT), false)
                }
                IfsSubPhase.CONTENT, IfsSubPhase.VL, IfsSubPhase.VB, IfsSubPhase.VS -> {
                    Pair(resolveVideoItem(0, IfsSubPhase.ABB), false)
                }
            }
        }

        // MODE SV2 (Semua PM1..n memiliki 3 Video A+B+A)
        val safeCurVt = curState.activeVtNumber.coerceIn(1, _vtPackages.value.size.coerceAtLeast(1))
        return when (curPhase) {
            IfsSubPhase.ABB -> Pair(resolveVideoItem(safeCurVt, IfsSubPhase.BL), false)
            IfsSubPhase.BL -> {
                val effectiveTarget = curState.pendingTargetVtNumber ?: curState.queue.firstOrNull()?.targetVtNumber
                if (effectiveTarget != null && effectiveTarget != safeCurVt) {
                    Pair(resolveVideoItem(safeCurVt, IfsSubPhase.BBT), false)
                } else {
                    Pair(resolveVideoItem(safeCurVt, IfsSubPhase.BL), true)
                }
            }
            IfsSubPhase.BBT -> {
                val target = (curState.pendingTargetVtNumber ?: curState.queue.firstOrNull()?.targetVtNumber ?: ((safeCurVt % _vtPackages.value.size.coerceAtLeast(1)) + 1))
                    .coerceIn(1, _vtPackages.value.size.coerceAtLeast(1))
                Pair(resolveVideoItem(target, IfsSubPhase.ABB), false)
            }
            IfsSubPhase.CONTENT, IfsSubPhase.VL, IfsSubPhase.VB, IfsSubPhase.VS -> {
                Pair(resolveVideoItem(safeCurVt, IfsSubPhase.BL), false)
            }
        }
    }

    fun resolveVideoItem(vtNumber: Int, phase: IfsSubPhase): VideoItem? {
        if (_videoMode.value == IfsVideoMode.SV4 || phase == IfsSubPhase.VS) {
            val activeVs = sv4CurrentVideoItem
            if (activeVs != null) return activeVs
        }

        val repo = try { VcamRepository.getInstance(appContext) } catch (_: Throwable) { null }
        val playlist = repo?.config?.value?.playlist ?: emptyList()
        if (playlist.isEmpty()) return null

        if (_videoMode.value == IfsVideoMode.SV4 || phase == IfsSubPhase.VS) {
            val sourcePlaylist = playlist.filterNot { it.id.startsWith("vs_rolling_") }
            val firstLId = _sv4LVideoIds.value.firstOrNull()
            if (!firstLId.isNullOrBlank()) {
                val matched = sourcePlaylist.firstOrNull { it.id == firstLId }
                if (matched != null) return matched
            }
            return sourcePlaylist.firstOrNull() ?: playlist.firstOrNull()
        }

        if (_videoMode.value == IfsVideoMode.SV3 || phase == IfsSubPhase.VL || phase == IfsSubPhase.VB) {
            if (phase == IfsSubPhase.VB) {
                val vbIds = _sv3VbVideoIds.value
                val vbIdx = (if (vtNumber >= 1) vtNumber - 1 else (_ifsState.value.sv3ActiveVbNumber - 1))
                    .coerceIn(0, (vbIds.size - 1).coerceAtLeast(0))
                val targetVbId = vbIds.getOrNull(vbIdx)
                if (!targetVbId.isNullOrBlank()) {
                    val matched = playlist.firstOrNull { it.id == targetVbId }
                    if (matched != null) return matched
                }
                // Fallback ke VB lain yang valid di grup VB
                for (id in vbIds) {
                    val matched = playlist.firstOrNull { it.id == id }
                    if (matched != null) return matched
                }
            }
            // Resolusi VL (1 Video Utama SV3)
            val vlId = _sv3VlVideoId.value
            if (!vlId.isNullOrBlank()) {
                val matched = playlist.firstOrNull { it.id == vlId }
                if (matched != null) return matched
            }
            return playlist.firstOrNull()
        }

        val pkg = getPackageByNumber(vtNumber) ?: _vtmPackage.value
        val candidateIds = when (phase) {
            IfsSubPhase.ABB -> listOfNotNull(pkg.abbVideoId, pkg.blVideoId, pkg.bbtVideoId)
            IfsSubPhase.BL, IfsSubPhase.CONTENT, IfsSubPhase.VL, IfsSubPhase.VB, IfsSubPhase.VS -> listOfNotNull(pkg.blVideoId, pkg.abbVideoId, pkg.bbtVideoId)
            IfsSubPhase.BBT -> listOfNotNull(pkg.bbtVideoId, pkg.blVideoId, pkg.abbVideoId)
        }

        for (id in candidateIds) {
            if (id.isNotBlank()) {
                val matched = playlist.firstOrNull { it.id == id }
                if (matched != null) return matched
            }
        }

        // Fallback berdasarkan urutan indeks paket agar tidak semua lari ke video ke-1
        val fallbackIdx = if (vtNumber <= 0) 0 else vtNumber.coerceAtMost(playlist.lastIndex)
        return playlist.getOrNull(fallbackIdx) ?: playlist.firstOrNull()
    }

    fun syncCurrentVideoToRepository(broadcastToHook: Boolean = true, bumpPhaseVersion: Boolean = true) {
        val curState = _ifsState.value
        val item = resolveVideoItem(curState.activeVtNumber, curState.activeSubPhase)
        if (item == null) {
            _ifsState.value = _ifsState.value.copy(
                currentVideoId = null,
                currentVideoName = null,
                phaseStepVersion = if (bumpPhaseVersion) curState.phaseStepVersion + 1L else curState.phaseStepVersion
            )
            return
        }
        _ifsState.value = _ifsState.value.copy(
            currentVideoId = item.id,
            currentVideoName = item.name,
            phaseStepVersion = if (bumpPhaseVersion) curState.phaseStepVersion + 1L else curState.phaseStepVersion
        )
        try {
            val repo = VcamRepository.getInstance(appContext)
            if (_videoMode.value == IfsVideoMode.SV4 && item.id.startsWith("vs_rolling_")) {
                repo.selectRollingVsVideoSync(item, broadcastToHook = broadcastToHook)
            } else {
                val idx = repo.config.value.playlist.indexOfFirst { it.id == item.id }
                if (idx >= 0) {
                    repo.selectVideoByIndexSync(idx, broadcastToHook = broadcastToHook)
                }
            }
        } catch (_: Throwable) {}
    }

    fun isCurrentPhaseLooping(): Boolean {
        val curState = _ifsState.value
        if (curState.videoMode == IfsVideoMode.SV4) {
            // Di Mode Persiapan SV4 (belum klik MULAI SV4), biarkan video pratinjau looping normal
            return !_sv4VideoRunning.value
        }
        if (curState.videoMode == IfsVideoMode.SV3) {
            // Di SV3, perpindahan putaran VL <-> VB dikendalikan penuh oleh onVideoCompleted
            return false
        }
        if (curState.activeSubPhase != IfsSubPhase.BL) return false
        return curState.pendingTargetVtNumber == null && curState.queue.isEmpty()
    }

    /**
     * Mengatur saklar ON/OFF Scan Komentar (SC) pada fitur IFS.
     * Jika OFF, IFS berjalan dalam Mode Host Live (trigger manual).
     */
    fun setScEnabled(enabled: Boolean) {
        _isScEnabled.value = enabled
        prefs.edit().putBoolean(KEY_IFS_SC_ENABLED, enabled).apply()
        _ifsState.value = _ifsState.value.copy(
            isScEnabled = enabled,
            queue = if (!enabled) emptyList() else _ifsState.value.queue
        )
    }

    /**
     * Memproses teks komentar penonton hasil scan (SC) untuk mencocokkan kata kunci VTM / VT1..n.
     */
    fun onCommentDetected(senderName: String, text: String): Boolean {
        if (!_isIfsActive.value || !_isScEnabled.value || _videoMode.value == IfsVideoMode.SV3 || _videoMode.value == IfsVideoMode.SV4) return false
        val cleanText = text.lowercase().trim()
        if (cleanText.isBlank()) return false

        val visiblePackages = getVisiblePackages(_videoMode.value)
        for (pkg in visiblePackages) {
            val keywords = pkg.triggerKeyword.split(Regex("[,;/|]"))
                .map { it.lowercase().trim() }
                .filter { it.isNotBlank() }

            for (kw in keywords) {
                if (cleanText == kw || cleanText.contains(kw)) {
                    Log.i(TAG, "💬 [IFS-SC] Komentar '$cleanText' dari '$senderName' COCOK dengan ${pkg.shortCode} (Keyword: '$kw')")
                    triggerVt(pkg.vtNumber, senderName, text)
                    return true
                }
            }
        }
        return false
    }
}
