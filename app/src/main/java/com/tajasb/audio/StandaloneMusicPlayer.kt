package com.tajasb.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import com.tajasb.model.AudioAntiHashIntensity
import com.tajasb.model.AudioItem
import com.tajasb.model.VoiceEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

/**
 * Standalone Music Player for Tajasb.
 * Plays audio independently through the device's audio output (speaker/headphones)
 * like a standard music player, without injecting into virtual camera hooks.
 */
class StandaloneMusicPlayer private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "StandaloneMusicPlayer"

        @Volatile
        private var INSTANCE: StandaloneMusicPlayer? = null

        fun getInstance(context: Context): StandaloneMusicPlayer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StandaloneMusicPlayer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    @Volatile var isPreparing: Boolean = false
        private set
    @Volatile private var sessionGeneration: Long = 0L

    @Volatile var isAudioAntiHashEnabled: Boolean = true
        private set
    @Volatile var audioAntiHashMode: AudioAntiHashIntensity = AudioAntiHashIntensity.MEDIUM
        private set
    @Volatile var audioAntiHashPercent: Float = 2.0f
        private set
    @Volatile var isAudioLayer2SyncAntiHash: Boolean = true
        private set
    @Volatile var isSessionDnaEnabled: Boolean = false
        private set
    @Volatile var currentSessionDna: com.tajasb.model.SessionDnaProfile =
        com.tajasb.model.SessionDnaProfile.forSession(1, 1001L)
        private set
    private var driftStep: Float = 0f
    private var lastDriftTimeMs: Long = 0L
    @Volatile var trackEntropyBias: Float = 0.004f
        private set
    private var trackPlayCounter: Int = 0

    fun setSessionDnaEnabled(enabled: Boolean) {
        if (isSessionDnaEnabled == enabled) return
        isSessionDnaEnabled = enabled
        applyAudioAntiHash()
        Log.i(TAG, "🧬 [DNA-SESI] StandaloneMusicPlayer Saklar DNA Sesi: ${if (enabled) "AKTIF" else "NONAKTIF"}")
    }

    fun setSessionDna(sessionIndex: Int, sessionSeed: Long, enabled: Boolean = isSessionDnaEnabled) {
        isSessionDnaEnabled = enabled
        val nextDna = com.tajasb.model.SessionDnaProfile.forSession(sessionIndex, sessionSeed)
        currentSessionDna = nextDna
        if (enabled) {
            driftStep += 2.39f + (nextDna.slotIndex * 0.77f)
            rollTrackAcousticEntropy(nextDna.codeName)
        } else {
            trackEntropyBias = 0f
        }
        applyAudioAntiHash()
        Log.i(TAG, "🧬 [DNA-SESI] StandaloneMusicPlayer ${if (enabled) "AKTIF (${nextDna.badgeText})" else "NONAKTIF (OFF)"}")
    }

    fun rollTrackAcousticEntropy(seedKey: String = "") {
        trackPlayCounter++
        if (!isSessionDnaEnabled) {
            val rnd = java.util.Random(System.currentTimeMillis() xor (trackPlayCounter.toLong() * 0x9E3779B97F4A7C15UL.toLong()) xor seedKey.hashCode().toLong())
            val rawBias = (rnd.nextFloat() - 0.5f) * 0.010f
            trackEntropyBias = if (kotlin.math.abs(rawBias) < 0.002f) 0.0030f else rawBias
            driftStep += 1.15f + rnd.nextFloat() * 0.85f
            return
        }
        val dna = currentSessionDna
        val rnd = java.util.Random(System.currentTimeMillis() xor dna.sessionSeed xor (trackPlayCounter.toLong() * 0x9E3779B97F4A7C15UL.toLong()) xor seedKey.hashCode().toLong())
        val rawBias = (rnd.nextFloat() - 0.5f) * 0.012f
        trackEntropyBias = if (kotlin.math.abs(rawBias) < 0.002f) 0.0035f else rawBias
        driftStep += 1.23f + rnd.nextFloat() * 0.95f
    }

    init {
        try {
            val filter = android.content.IntentFilter().apply {
                addAction("com.tajasb.action.STOP_ALL_AUDIO")
                addAction("com.tajasb.action.STOP_PLAYBACK")
                addAction("com.tajasb.action.SET_AUDIO_ANTI_HASH")
                addAction("com.tajasb.action.SET_SESSION_DNA")
                addAction("com.tajasb.action.SET_VOICE_EFFECT")
                addAction("com.tajasb.action.SET_INDEPENDENT_AUDIO")
                addAction("com.tajasb.action.SET_HUMAN_VOLUME_FLUCTUATION")
                addAction("com.tajasb.action.SET_AUDIO_LAYER_2")
            }
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val act = intent?.action
                    Log.i(TAG, "Received broadcast in StandaloneMusicPlayer: $act")
                    when (act) {
                        "com.tajasb.action.SET_AUDIO_ANTI_HASH" -> {
                            val enabled = intent.getBooleanExtra("extra_audio_anti_hash_enabled", isAudioAntiHashEnabled)
                            val modeStr = intent.getStringExtra("extra_audio_anti_hash_mode")
                            val mode = if (modeStr != null) {
                                try { AudioAntiHashIntensity.valueOf(modeStr) } catch (_: Throwable) { audioAntiHashMode }
                            } else audioAntiHashMode
                            setAudioAntiHash(enabled, mode)
                        }
                        "com.tajasb.action.SET_SESSION_DNA" -> {
                            val sIdx = intent.getIntExtra("extra_session_dna_index", currentSessionDna.sessionIndex)
                            val sSeed = intent.getLongExtra("extra_session_dna_seed", currentSessionDna.sessionSeed)
                            val sEnabled = intent.getBooleanExtra("extra_enable_dna_module", isSessionDnaEnabled)
                            setSessionDna(sIdx, sSeed, sEnabled)
                        }
                        "com.tajasb.action.SET_VOICE_EFFECT" -> {
                            val effectStr = intent.getStringExtra("extra_voice_effect")
                            val effect = if (effectStr != null) {
                                try { VoiceEffect.valueOf(effectStr) } catch (_: Throwable) { _currentVoiceEffect.value }
                            } else _currentVoiceEffect.value
                            setVoiceEffect(effect)
                        }
                        "com.tajasb.action.SET_INDEPENDENT_AUDIO" -> {
                            val enabled = intent.getBooleanExtra("extra_independent_audio_enabled", _isIndependentAudioMode.value)
                            setIndependentAudioMode(enabled)
                        }
                        "com.tajasb.action.SET_HUMAN_VOLUME_FLUCTUATION" -> {
                            val enabled = intent.getBooleanExtra("extra_human_volume_fluctuation_enabled", _isHumanVolumeFluctuationEnabled.value)
                            val intensity = intent.getFloatExtra("extra_human_fluctuation_intensity", _humanFluctuationIntensity.value)
                            setHumanVolumeFluctuation(enabled, intensity)
                        }
                        "com.tajasb.action.SET_AUDIO_LAYER_2" -> {
                            val enabled = intent.getBooleanExtra("extra_audio_layer2_enabled", _isLayer2Active.value)
                            setLayer2Active(enabled)
                        }
                        else -> {
                            stopAll()
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
        } catch (_: Throwable) {}
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrack = MutableStateFlow<AudioItem?>(null)
    val currentTrack: StateFlow<AudioItem?> = _currentTrack.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isLooping = MutableStateFlow(false) // Default: Sequential Auto-Next Playlist Mode
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    // --- Infinity System (IFS) Audio X (AX) + Sistem Random (SR) Support ---
    private val _isSrMode = MutableStateFlow(true) // Sistem Random (SR) default ON
    val isSrMode: StateFlow<Boolean> = _isSrMode.asStateFlow()

    private val _isPlayingTriggeredAx = MutableStateFlow(false)
    val isPlayingTriggeredAx: StateFlow<Boolean> = _isPlayingTriggeredAx.asStateFlow()

    private val _activeTriggerAxNumber = MutableStateFlow<Int?>(null)
    val activeTriggerAxNumber: StateFlow<Int?> = _activeTriggerAxNumber.asStateFlow()

    private val _activeTriggerAxLabel = MutableStateFlow<String?>(null)
    val activeTriggerAxLabel: StateFlow<String?> = _activeTriggerAxLabel.asStateFlow()

    private var pausedSrTrack: AudioItem? = null
    private var pausedSrPositionMs: Long = 0L
    private val _pausedSrInfo = MutableStateFlow<Pair<String, Long>?>(null)
    val pausedSrInfo: StateFlow<Pair<String, Long>?> = _pausedSrInfo.asStateFlow()

    private val srShuffleDeck = mutableListOf<Int>()
    private var onTriggeredAxCompletedListener: (() -> Unit)? = null
    private var srTrackFilter: ((AudioItem) -> Boolean)? = null
    private var sv4AudioCompletionCallback: (() -> Boolean)? = null
    private var onAudioSeekedCallback: ((Long) -> Unit)? = null

    fun setSv4AudioCompletionCallback(callback: (() -> Boolean)?) {
        this.sv4AudioCompletionCallback = callback
    }

    fun setOnAudioSeekedCallback(callback: ((Long) -> Unit)?) {
        this.onAudioSeekedCallback = callback
    }

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    // --- Audio Tipe 2: Bebas / Tidak Terikat Mode (Secondary Audio Layer) ---
    private var mediaPlayerLayer2: MediaPlayer? = null
    private var progressJobLayer2: Job? = null
    @Volatile var isPreparingLayer2: Boolean = false
        private set
    @Volatile private var sessionGenerationLayer2: Long = 0L

    private val _isLayer2Active = MutableStateFlow(false)
    val isLayer2Active: StateFlow<Boolean> = _isLayer2Active.asStateFlow()

    private val _currentTrackLayer2 = MutableStateFlow<AudioItem?>(null)
    val currentTrackLayer2: StateFlow<AudioItem?> = _currentTrackLayer2.asStateFlow()

    private val _isPlayingLayer2 = MutableStateFlow(false)
    val isPlayingLayer2: StateFlow<Boolean> = _isPlayingLayer2.asStateFlow()

    private val _currentPositionMsLayer2 = MutableStateFlow(0L)
    val currentPositionMsLayer2: StateFlow<Long> = _currentPositionMsLayer2.asStateFlow()

    private val _durationMsLayer2 = MutableStateFlow(0L)
    val durationMsLayer2: StateFlow<Long> = _durationMsLayer2.asStateFlow()

    private val _volumeLayer2 = MutableStateFlow(0.70f)
    val volumeLayer2: StateFlow<Float> = _volumeLayer2.asStateFlow()

    private val _isMutedLayer2 = MutableStateFlow(false)
    val isMutedLayer2: StateFlow<Boolean> = _isMutedLayer2.asStateFlow()

    private val _isLoopingLayer2 = MutableStateFlow(true) // Default true: continuous independent loop
    val isLoopingLayer2: StateFlow<Boolean> = _isLoopingLayer2.asStateFlow()

    // --- Audio Tipe 3: Dynamic Random Intermittent Ringtone Layer ---
    private var mediaPlayerLayer3: MediaPlayer? = null
    private var ringtoneSchedulerJob: Job? = null
    @Volatile private var sessionGenerationLayer3: Long = 0L

    private val _isLayer3Active = MutableStateFlow(false)
    val isLayer3Active: StateFlow<Boolean> = _isLayer3Active.asStateFlow()

    private val _isPlayingLayer3 = MutableStateFlow(false)
    val isPlayingLayer3: StateFlow<Boolean> = _isPlayingLayer3.asStateFlow()

    private val _currentTrackLayer3 = MutableStateFlow<AudioItem?>(null)
    val currentTrackLayer3: StateFlow<AudioItem?> = _currentTrackLayer3.asStateFlow()

    private val _volumeLayer3 = MutableStateFlow(0.85f)
    val volumeLayer3: StateFlow<Float> = _volumeLayer3.asStateFlow()

    private val _isMutedLayer3 = MutableStateFlow(false)
    val isMutedLayer3: StateFlow<Boolean> = _isMutedLayer3.asStateFlow()

    private val _nextRingtoneEtaSec = MutableStateFlow<Int?>(null)
    val nextRingtoneEtaSec: StateFlow<Int?> = _nextRingtoneEtaSec.asStateFlow()

    private val _layer3RingtoneIds = MutableStateFlow<Set<String>>(emptySet())
    val layer3RingtoneIds: StateFlow<Set<String>> = _layer3RingtoneIds.asStateFlow()

    @Volatile var layer3MinIntervalSec: Int = 30
    @Volatile var layer3MaxIntervalSec: Int = 120
    @Volatile private var lastRingtoneIntervalMs: Long = 0L
    @Volatile private var lastRingtoneItemId: String? = null

    private val _isIndependentAudioMode = MutableStateFlow(false)
    val isIndependentAudioMode: StateFlow<Boolean> = _isIndependentAudioMode.asStateFlow()

    private val _isHumanVolumeFluctuationEnabled = MutableStateFlow(false)
    val isHumanVolumeFluctuationEnabled: StateFlow<Boolean> = _isHumanVolumeFluctuationEnabled.asStateFlow()

    private val _humanFluctuationIntensity = MutableStateFlow(0.20f)
    val humanFluctuationIntensity: StateFlow<Float> = _humanFluctuationIntensity.asStateFlow()

    private val _liveDynamicVolumeRatio = MutableStateFlow(1.0f)
    val liveDynamicVolumeRatio: StateFlow<Float> = _liveDynamicVolumeRatio.asStateFlow()

    private var volumeFluctuationJob: Job? = null

    private val _isAudioStalledOrError = MutableStateFlow(false)
    val isAudioStalledOrError: StateFlow<Boolean> = _isAudioStalledOrError.asStateFlow()

    private val _lastAudioErrorReason = MutableStateFlow<String?>(null)
    val lastAudioErrorReason: StateFlow<String?> = _lastAudioErrorReason.asStateFlow()

    // Realtime Audio Editor (Plus-Minus Stepper in Floating Window)
    private val _realtimeTempo = MutableStateFlow(1.0f)
    val realtimeTempo: StateFlow<Float> = _realtimeTempo.asStateFlow()

    private val _realtimeFinePitch = MutableStateFlow(0.0f)
    val realtimeFinePitch: StateFlow<Float> = _realtimeFinePitch.asStateFlow()

    private val _realtimeSkipIntroSec = MutableStateFlow(0)
    val realtimeSkipIntroSec: StateFlow<Int> = _realtimeSkipIntroSec.asStateFlow()

    private val _realtimeCutOutroSec = MutableStateFlow(0)
    val realtimeCutOutroSec: StateFlow<Int> = _realtimeCutOutroSec.asStateFlow()

    fun setRealtimeTempo(tempo: Float) {
        val clamped = (kotlin.math.round(tempo * 100f) / 100f).coerceIn(0.70f, 1.40f)
        _realtimeTempo.value = clamped
        applyAudioAntiHash()
        Log.i(TAG, "🎛️ [REALTIME-EDIT] Tempo disetel: ${clamped}x")
    }

    fun adjustRealtimeTempo(delta: Float) {
        setRealtimeTempo(_realtimeTempo.value + delta)
    }

    fun setRealtimeFinePitch(finePitch: Float) {
        val clamped = (kotlin.math.round(finePitch * 100f) / 100f).coerceIn(-0.15f, 0.15f)
        _realtimeFinePitch.value = clamped
        applyAudioAntiHash()
        Log.i(TAG, "🎛️ [REALTIME-EDIT] Fine Pitch disetel: ${(clamped * 100).toInt()}%")
    }

    fun adjustRealtimeFinePitch(delta: Float) {
        setRealtimeFinePitch(_realtimeFinePitch.value + delta)
    }

    fun setRealtimeSkipIntroSec(sec: Int) {
        val clamped = sec.coerceIn(0, 30)
        _realtimeSkipIntroSec.value = clamped
        Log.i(TAG, "🎛️ [REALTIME-EDIT] Skip Intro disetel: ${clamped}s")
    }

    fun adjustRealtimeSkipIntroSec(delta: Int) {
        setRealtimeSkipIntroSec(_realtimeSkipIntroSec.value + delta)
    }

    fun setRealtimeCutOutroSec(sec: Int) {
        val clamped = sec.coerceIn(0, 30)
        _realtimeCutOutroSec.value = clamped
        Log.i(TAG, "🎛️ [REALTIME-EDIT] Cut Outro disetel: ${clamped}s")
    }

    fun adjustRealtimeCutOutroSec(delta: Int) {
        setRealtimeCutOutroSec(_realtimeCutOutroSec.value + delta)
    }

    fun resetRealtimeAudioEdits() {
        _realtimeTempo.value = 1.0f
        _realtimeFinePitch.value = 0.0f
        _realtimeSkipIntroSec.value = 0
        _realtimeCutOutroSec.value = 0
        applyAudioAntiHash()
        Log.i(TAG, "🎛️ [REALTIME-EDIT] Seluruh edit audio direset ke normal!")
    }

    @Volatile
    var lastAudioAdvanceTimeMs: Long = System.currentTimeMillis()
        private set

    private var playlistProvider: (() -> List<AudioItem>)? = null
    private var allAudioPlaylistProvider: (() -> List<AudioItem>)? = null
    private var currentIndexProvider: (() -> Int)? = null
    private var onTrackChangeCallback: ((Int) -> Unit)? = null

    fun setPlaylistCallbacks(
        playlist: () -> List<AudioItem>,
        currentIndex: () -> Int,
        onTrackChange: (Int) -> Unit,
        allAudioPlaylist: (() -> List<AudioItem>)? = null
    ) {
        this.playlistProvider = playlist
        this.allAudioPlaylistProvider = allAudioPlaylist ?: playlist
        this.currentIndexProvider = currentIndex
        this.onTrackChangeCallback = onTrackChange
    }

    fun setSrTrackFilter(filter: ((AudioItem) -> Boolean)?) {
        this.srTrackFilter = filter
        srShuffleDeck.clear()
    }

    fun setSrMode(enabled: Boolean) {
        _isSrMode.value = enabled
        if (enabled) {
            srShuffleDeck.clear()
        }
    }

    @Synchronized
    fun playAudioItem(item: AudioItem, startPlaying: Boolean = true, startPositionMs: Long = 0L) {
        try {
            // Proteksi Mode Audio Mandiri: Jika audio mandiri aktif dan sistem mencoba memutar audio rolling VS, tolak
            if (_isIndependentAudioMode.value && (item.id.startsWith("vs_audio_rolling_") || item.path.contains("vs_audio_rolling_"))) {
                Log.i(TAG, "🎧 [AUDIO-INDEPENDENT] Mengabaikan pergantian audio VS '${item.name}' karena Mode Audio Mandiri aktif!")
                return
            }

            val current = _currentTrack.value
            val currentMp = mediaPlayer
            // Anti-Duplikasi: Jika trek yang diminta sama persis dan sedang berputar / menyiapkan, jangan buat player baru
            if (current?.id == item.id && currentMp != null && (isPreparing || _isPlaying.value)) {
                if (startPlaying && !currentMp.isPlaying) {
                    try { currentMp.start() } catch (_: Throwable) {}
                    _isPlaying.value = true
                    startProgressTracker()
                    if (_isHumanVolumeFluctuationEnabled.value) {
                        startVolumeFluctuationLoop()
                    }
                }
                return
            }

            val currentGen = ++sessionGeneration
            isPreparing = true
            releasePlayer()
            rollTrackAcousticEntropy(item.name)

            val file = File(item.path)
            if (!file.exists() || file.length() == 0L) {
                Log.w(TAG, "Audio file not found or empty: ${item.path}")
                isPreparing = false
                _isPlaying.value = false
                return
            }

            // Request Audio Focus to ensure STREAM_MUSIC is active and not silenced by OS
            try {
                val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .build()
                    audioManager?.requestAudioFocus(focusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                }
            } catch (_: Throwable) {}

            val player = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                }

                // Load data source safely
                try {
                    setDataSource(file.absolutePath)
                } catch (_: Throwable) {
                    try {
                        val fis = FileInputStream(file)
                        setDataSource(fis.fd)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Cannot set data source for audio: ${e.message}")
                    }
                }

                val isItemMuted = item.isMuted || _isMuted.value
                val vol = if (isItemMuted) 0f else _volume.value
                setVolume(vol, vol)
                // Keep native looping false so setOnCompletionListener always fires for track advancement
                isLooping = false

                setOnPreparedListener { mp ->
                    // Validasi generasi: Jika player ini sudah usang karena ada pemanggilan lagu baru, matikan segera!
                    if (currentGen != sessionGeneration || mp != mediaPlayer) {
                        Log.w(TAG, "Membungkam MediaPlayer lama (gen=$currentGen vs aktif=$sessionGeneration)")
                        try { mp.setVolume(0f, 0f) } catch (_: Throwable) {}
                        try { mp.stop() } catch (_: Throwable) {}
                        try { mp.reset() } catch (_: Throwable) {}
                        try { mp.release() } catch (_: Throwable) {}
                        return@setOnPreparedListener
                    }
                    isPreparing = false
                    _durationMs.value = mp.duration.toLong().coerceAtLeast(0L)
                    applyEffectiveVolume()
                    if (startPositionMs > 0L) {
                        try {
                            mp.seekTo(startPositionMs.toInt())
                            _currentPositionMs.value = startPositionMs
                        } catch (_: Throwable) {}
                    } else if (_realtimeSkipIntroSec.value > 0) {
                        val skipMs = (_realtimeSkipIntroSec.value * 1000L).coerceAtMost(maxOf(0L, _durationMs.value - 2000L))
                        if (skipMs > 0L) {
                            try {
                                mp.seekTo(skipMs.toInt())
                                _currentPositionMs.value = skipMs
                            } catch (_: Throwable) {}
                        }
                    }
                    if (startPlaying) {
                        mp.start()
                        applyAudioAntiHash()
                        _isPlaying.value = true
                        _isAudioStalledOrError.value = false
                        _lastAudioErrorReason.value = null
                        lastAudioAdvanceTimeMs = System.currentTimeMillis()
                        startProgressTracker()
                        if (_isHumanVolumeFluctuationEnabled.value) {
                            startVolumeFluctuationLoop()
                        }
                    } else {
                        _isPlaying.value = false
                    }
                }

                setOnCompletionListener { mp ->
                    if (currentGen != sessionGeneration || mp != mediaPlayer) {
                        return@setOnCompletionListener
                    }
                    scope.launch(Dispatchers.Main) {
                        handleTrackCompletion()
                    }
                }

                setOnErrorListener { mp, what, extra ->
                    if (currentGen != sessionGeneration || mp != mediaPlayer) {
                        return@setOnErrorListener true
                    }
                    isPreparing = false
                    if (what == -38) return@setOnErrorListener true
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    val resumePos = _currentPositionMs.value
                    _isAudioStalledOrError.value = true
                    _lastAudioErrorReason.value = "Hardware/Decoder Audio terhambat (kode $what/$extra)"
                    stopProgressTracker()
                    scope.launch(Dispatchers.Main) {
                        delay(350L)
                        val cur = _currentTrack.value
                        if (cur != null && sessionGeneration == currentGen) {
                            Log.i(TAG, "🔄 Auto-healing StandaloneMusicPlayer dari posisi ${resumePos}ms setelah error $what/$extra")
                            playAudioItem(cur, startPlaying = true, startPositionMs = resumePos)
                        } else {
                            _isPlaying.value = false
                        }
                    }
                    true
                }

                prepareAsync()
            }

            mediaPlayer = player
            _currentTrack.value = item
            _currentPositionMs.value = 0L

        } catch (e: Throwable) {
            isPreparing = false
            Log.e(TAG, "Failed to play audio: ${e.message}", e)
            _isPlaying.value = false
        }
    }

    @Synchronized
    fun play() {
        val player = mediaPlayer
        if (player != null) {
            try {
                if (!player.isPlaying) {
                    player.start()
                    applyAudioAntiHash()
                    _isPlaying.value = true
                    startProgressTracker()
                    if (_isHumanVolumeFluctuationEnabled.value) {
                        startVolumeFluctuationLoop()
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to resume playback: ${e.message}")
            }
        } else {
            val list = playlistProvider?.invoke() ?: emptyList()
            val idx = currentIndexProvider?.invoke() ?: 0
            val current = _currentTrack.value ?: (if (list.isNotEmpty() && idx in list.indices) list[idx] else list.firstOrNull())
            if (current != null) {
                playAudioItem(current, startPlaying = true)
            }
        }
    }

    @Synchronized
    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                }
            }
            _isPlaying.value = false
            stopProgressTracker()
            stopVolumeFluctuationLoop()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to pause playback: ${e.message}")
        }
    }

    @Synchronized
    fun pauseAll() {
        pause()
        pauseLayer2()
        stopRingtoneScheduler()
        stopLayer3(clearTrackState = false)
    }

    @Synchronized
    fun togglePlayPause(): Boolean {
        if (_isPlaying.value) {
            pause()
            return false
        } else {
            play()
            return true
        }
    }

    @Synchronized
    fun stop(clearTrackState: Boolean = false) {
        try {
            releasePlayer()
            _isPlaying.value = false
            _currentPositionMs.value = 0L
            stopProgressTracker()
            stopVolumeFluctuationLoop()
            if (clearTrackState) {
                _currentTrack.value = null
                _durationMs.value = 0L
                pausedSrTrack = null
                pausedSrPositionMs = 0L
                _pausedSrInfo.value = null
                _isPlayingTriggeredAx.value = false
                _activeTriggerAxNumber.value = null
                _activeTriggerAxLabel.value = null
                onTriggeredAxCompletedListener = null
                srShuffleDeck.clear()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to stop playback: ${e.message}")
        }
    }

    @Synchronized
    fun stopAll(clearTrackState: Boolean = false) {
        sessionGeneration++
        sessionGenerationLayer2++
        sessionGenerationLayer3++
        stop(clearTrackState)
        stopLayer2(clearTrackState)
        stopRingtoneScheduler()
        stopLayer3(clearTrackState)
    }

    @Synchronized
    fun resetSessionAudioState() {
        // Hanya reset Tipe 1! Tipe 2 berdiri sendiri dan tidak boleh di-wipe oleh reset sesi/mode
        stop(clearTrackState = true)
    }

    @Synchronized
    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.let {
                val dur = try { it.duration } catch (_: Throwable) { 0 }
                val safePos = if (dur > 200) positionMs.toInt().coerceIn(0, dur - 100) else positionMs.toInt().coerceAtLeast(0)
                it.seekTo(safePos)
                _currentPositionMs.value = safePos.toLong()
                onAudioSeekedCallback?.invoke(safePos.toLong())
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to seek: ${e.message}")
        }
    }

    @Synchronized
    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        applyEffectiveVolume()
    }

    @Synchronized
    fun applyEffectiveVolume() {
        val cur = _currentTrack.value
        val isItemMuted = _isMuted.value || (cur?.isMuted == true)
        val dynamicRatio = if (_isHumanVolumeFluctuationEnabled.value) _liveDynamicVolumeRatio.value else 1.0f
        val duckFactor = if (_isPlayingLayer3.value) 0.35f else 1.0f
        val vol = if (isItemMuted) 0f else (_volume.value * dynamicRatio * duckFactor).coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(vol, vol)
        } catch (_: Throwable) {}
    }

    fun setIndependentAudioMode(enabled: Boolean) {
        _isIndependentAudioMode.value = enabled
        Log.i(TAG, "🎧 [AUDIO-INDEPENDENT] Mode Audio Mandiri: enabled=$enabled")
    }

    fun setHumanVolumeFluctuation(enabled: Boolean, intensity: Float = _humanFluctuationIntensity.value) {
        _isHumanVolumeFluctuationEnabled.value = enabled
        _humanFluctuationIntensity.value = intensity.coerceIn(0.05f, 0.40f)
        if (enabled && _isPlaying.value) {
            startVolumeFluctuationLoop()
        } else {
            stopVolumeFluctuationLoop()
        }
        Log.i(TAG, "🎙️ [HUMAN-MIC-VOLUME] Dinamika volume manusiawi: enabled=$enabled, intensity=${_humanFluctuationIntensity.value}")
    }

    fun setHumanFluctuationIntensity(intensity: Float) {
        _humanFluctuationIntensity.value = intensity.coerceIn(0.05f, 0.40f)
        Log.i(TAG, "🎙️ [HUMAN-MIC-VOLUME] Intensitas fluktuasi diubah ke: ${_humanFluctuationIntensity.value}")
    }

    private fun startVolumeFluctuationLoop() {
        volumeFluctuationJob?.cancel()
        volumeFluctuationJob = scope.launch(Dispatchers.Main) {
            var currentRatio = _liveDynamicVolumeRatio.value
            var targetRatio = 1.0f
            var nextTargetChangeTime = System.currentTimeMillis()
            val random = java.util.Random()

            while (isActive && _isPlaying.value && _isHumanVolumeFluctuationEnabled.value) {
                val now = System.currentTimeMillis()
                if (now >= nextTargetChangeTime) {
                    val intensity = _humanFluctuationIntensity.value
                    // Meniru pergerakan alami pembicara: kadang menoleh / condong / menjauh sedikit dari mic
                    val dip = random.nextFloat() * intensity
                    targetRatio = (1.0f - dip).coerceIn(1.0f - intensity, 1.0f)
                    val holdDurationMs = 2500L + (random.nextFloat() * 3200L).toLong()
                    nextTargetChangeTime = now + holdDurationMs
                }

                // Interpolasi mulus (~0.085f) per 100ms agar telinga mendengar transisi yang sangat organik tanpa loncatan kasar
                val stepDiff = (targetRatio - currentRatio) * 0.085f
                currentRatio += stepDiff
                _liveDynamicVolumeRatio.value = currentRatio

                applyEffectiveVolume()

                delay(100L)
            }

            _liveDynamicVolumeRatio.value = 1.0f
            applyEffectiveVolume()
        }
    }

    private fun stopVolumeFluctuationLoop() {
        volumeFluctuationJob?.cancel()
        volumeFluctuationJob = null
        _liveDynamicVolumeRatio.value = 1.0f
        applyEffectiveVolume()
    }

    @Synchronized
    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        applyEffectiveVolume()
    }

    @Synchronized
    fun setLoopMode(enabled: Boolean) {
        _isLooping.value = enabled
        try {
            mediaPlayer?.isLooping = false // Keep native looping false so completion listener handles repeat/advance cleanly
        } catch (_: Throwable) {}
    }

    @Synchronized
    fun toggleLoopMode(): Boolean {
        val newLoop = !_isLooping.value
        setLoopMode(newLoop)
        return newLoop
    }

    fun next(): Boolean {
        val list = playlistProvider?.invoke() ?: return false
        if (list.isEmpty()) return false
        val enabledIndices = list.indices.filter { list[it].isEnabled }
        if (enabledIndices.isEmpty()) {
            stop()
            return false
        }
        val currentIdx = currentIndexProvider?.invoke() ?: 0
        val nextIdx = enabledIndices.firstOrNull { it > currentIdx } ?: enabledIndices.first()
        onTrackChangeCallback?.invoke(nextIdx)
        playAudioItem(list[nextIdx], startPlaying = true)
        return true
    }

    fun prev(): Boolean {
        val list = playlistProvider?.invoke() ?: return false
        if (list.isEmpty()) return false
        val enabledIndices = list.indices.filter { list[it].isEnabled }
        if (enabledIndices.isEmpty()) {
            stop()
            return false
        }
        val currentIdx = currentIndexProvider?.invoke() ?: 0
        val prevIdx = enabledIndices.lastOrNull { it < currentIdx } ?: enabledIndices.last()
        onTrackChangeCallback?.invoke(prevIdx)
        playAudioItem(list[prevIdx], startPlaying = true)
        return true
    }

    private fun handleTrackCompletion() {
        if (_isPlayingTriggeredAx.value) {
            Log.i(TAG, "Audio trigger AX selesai, panggil listener dan resume SR detik terakhir")
            val cb = onTriggeredAxCompletedListener
            onTriggeredAxCompletedListener = null
            cb?.invoke()
            resumeSrFromPausedPosition()
            return
        }

        val curTrack = _currentTrack.value
        if (!_isIndependentAudioMode.value && curTrack != null && (curTrack.id.startsWith("vs_audio_rolling_") || curTrack.path.contains("vs_audio_rolling_"))) {
            val handledBySv4Audio = try { sv4AudioCompletionCallback?.invoke() == true } catch (_: Throwable) { false }
            if (handledBySv4Audio) {
                return
            }
        }

        val list = playlistProvider?.invoke() ?: emptyList()
        if (list.isEmpty()) {
            _isPlaying.value = false
            _currentPositionMs.value = 0L
            stopProgressTracker()
            return
        }

        val enabledList = list.filter { it.isEnabled }
        if (enabledList.isEmpty()) {
            _isPlaying.value = false
            _currentPositionMs.value = 0L
            stopProgressTracker()
            return
        }

        if (_isLooping.value) {
            // Repeat Single Song Mode
            val cur = _currentTrack.value
            if (cur != null && cur.isEnabled) {
                playAudioItem(cur, startPlaying = true)
            } else {
                next()
            }
        } else if (_isSrMode.value) {
            // Sistem Random (SR) Non-repeating Shuffle
            playNextSrRandomTrack()
        } else {
            // Auto-Next Sequential Playlist Mode
            next()
        }
    }

    @Synchronized
    fun playTriggeredAx(
        item: AudioItem,
        axNumber: Int,
        axLabel: String = if (axNumber == 0) "AX-VTM" else "AX$axNumber",
        onComplete: (() -> Unit)? = null
    ) {
        if (_isIndependentAudioMode.value) {
            Log.i(TAG, "🎧 [AUDIO-INDEPENDENT] Mengabaikan triggered AX $axLabel karena Mode Audio Mandiri aktif!")
            return
        }
        try {
            // Jika sedang bukan mode AX (sedang di rotasi SR), simpan posisi detik terakhir SR
            if (!_isPlayingTriggeredAx.value) {
                val curTrack = _currentTrack.value
                val curPos = try {
                    mediaPlayer?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: _currentPositionMs.value
                } catch (_: Throwable) {
                    _currentPositionMs.value
                }
                if (curTrack != null && curTrack.id != item.id) {
                    pausedSrTrack = curTrack
                    pausedSrPositionMs = curPos
                    _pausedSrInfo.value = Pair(curTrack.name, pausedSrPositionMs)
                    Log.i(TAG, "⏸️ [IFS-SR] Pause SR '${curTrack.name}' pada detik ${pausedSrPositionMs / 1000}s (${pausedSrPositionMs}ms), mulai putar $axLabel (${item.name})")
                }
            }
            _isPlayingTriggeredAx.value = true
            _activeTriggerAxNumber.value = axNumber
            _activeTriggerAxLabel.value = axLabel
            this.onTriggeredAxCompletedListener = onComplete

            playAudioItem(item, startPlaying = true, startPositionMs = 0L)
        } catch (e: Throwable) {
            Log.e(TAG, "Gagal memutar triggered $axLabel: ${e.message}")
        }
    }

    @Synchronized
    fun resumeSrFromPausedPosition() {
        val list = playlistProvider?.invoke() ?: emptyList()
        val validIds = list.map { it.id }.toSet()
        val targetTrack = pausedSrTrack?.takeIf { validIds.contains(it.id) && File(it.path).exists() }
        val targetPos = pausedSrPositionMs
        Log.i(TAG, "▶️ [IFS-SR] Melanjutkan trek SR '${targetTrack?.name}' dari detik ${targetPos / 1000}s (${targetPos}ms)")

        _isPlayingTriggeredAx.value = false
        _activeTriggerAxNumber.value = null
        _activeTriggerAxLabel.value = null
        pausedSrTrack = null
        pausedSrPositionMs = 0L
        _pausedSrInfo.value = null

        if (targetTrack != null) {
            playAudioItem(targetTrack, startPlaying = true, startPositionMs = targetPos)
        } else if (_isSrMode.value) {
            playNextSrRandomTrack()
        } else {
            next()
        }
    }

    /**
     * Memastikan pemutar musik MP3 aktif sesuai mode pilihan pengguna (Urut, Acak/SR, atau Loop 1 Lagu)
     * ketika tidak ada trigger AX terkait VT atau saat memulai Preview / siklus IFS.
     */
    @Synchronized
    fun ensureSrPlaying(): Boolean {
        if (_isIndependentAudioMode.value && _isPlaying.value && mediaPlayer != null) {
            return true
        }
        val list = playlistProvider?.invoke() ?: emptyList()
        if (list.isEmpty()) {
            stop(clearTrackState = true)
            return false
        }
        val validIds = list.map { it.id }.toSet()

        // Bersihkan trek jeda SR atau trek aktif jika sudah dihapus dari playlist baru
        if (pausedSrTrack != null && !validIds.contains(pausedSrTrack!!.id)) {
            pausedSrTrack = null
            pausedSrPositionMs = 0L
            _pausedSrInfo.value = null
        }
        if (_currentTrack.value != null && !validIds.contains(_currentTrack.value!!.id)) {
            stop(clearTrackState = true)
        }

        // Jika sebelumnya sempat tertahan di status AX tetapi sudah berhenti, kembalikan ke lagu sebelumnya
        if (_isPlayingTriggeredAx.value) {
            if (_isPlaying.value && mediaPlayer != null) {
                return true
            }
            resumeSrFromPausedPosition()
            return _isPlaying.value
        }

        // Jika sudah sedang berputar normal, biarkan lanjut tanpa restart
        if (_isPlaying.value && mediaPlayer != null) {
            return true
        }

        // Jika ada posisi lagu yang sempat dijeda oleh AX, lanjutkan dari detik terakhir
        if (pausedSrTrack != null) {
            resumeSrFromPausedPosition()
            return true
        }

        val cur = _currentTrack.value
        val filter = srTrackFilter
        if (cur != null && validIds.contains(cur.id) && cur.isEnabled && (filter == null || filter(cur) || !_isSrMode.value)) {
            if (mediaPlayer != null) {
                play()
            } else {
                playAudioItem(cur, startPlaying = true)
            }
            return true
        }

        if (!_isSrMode.value) {
            val idx = (currentIndexProvider?.invoke() ?: 0).coerceIn(0, list.size - 1)
            val chosen = list.getOrNull(idx)?.takeIf { it.isEnabled && !it.id.startsWith("vs_audio_rolling_") }
                ?: list.firstOrNull { it.isEnabled && !it.id.startsWith("vs_audio_rolling_") }
                ?: list.firstOrNull()
            if (chosen != null) {
                val chosenIdx = list.indexOfFirst { it.id == chosen.id }
                if (chosenIdx >= 0) onTrackChangeCallback?.invoke(chosenIdx)
                playAudioItem(chosen, startPlaying = true, startPositionMs = 0L)
                return true
            }
        }

        playNextSrRandomTrack()
        return true
    }

    @Synchronized
    fun playNextSrRandomTrack() {
        val list = playlistProvider?.invoke() ?: emptyList()
        val filter = srTrackFilter
        val srIndices = list.indices.filter { idx ->
            val item = list[idx]
            item.isEnabled && (filter == null || filter(item))
        }
        val enabledIndices = if (srIndices.isNotEmpty()) srIndices else list.indices.filter { list[it].isEnabled }
        if (enabledIndices.isEmpty()) {
            stop()
            return
        }
        val curIdx = currentIndexProvider?.invoke() ?: 0
        srShuffleDeck.retainAll(enabledIndices.toSet())
        if (srShuffleDeck.isEmpty()) {
            val freshDeck = enabledIndices.filter { it != curIdx }.shuffled().toMutableList()
            if (freshDeck.isEmpty()) {
                freshDeck.addAll(enabledIndices.shuffled())
            }
            srShuffleDeck.addAll(freshDeck)
        }
        val nextIdx = if (srShuffleDeck.isNotEmpty()) srShuffleDeck.removeAt(0) else enabledIndices.first()
        onTrackChangeCallback?.invoke(nextIdx)
        playAudioItem(list[nextIdx], startPlaying = true, startPositionMs = 0L)
    }

    fun setAudioAntiHash(enabled: Boolean, mode: AudioAntiHashIntensity, customPercent: Float = audioAntiHashPercent) {
        this.isAudioAntiHashEnabled = enabled
        this.audioAntiHashMode = mode
        this.audioAntiHashPercent = customPercent.coerceIn(0.1f, 5.0f)
        applyAudioAntiHash()
        Log.i(TAG, "StandaloneMusicPlayer Audio Anti-Deteksi: enabled=$enabled, mode=${mode.displayName}, pct=${audioAntiHashPercent}%")
    }

    fun setAudioAntiHashPercent(pct: Float) {
        this.audioAntiHashPercent = pct.coerceIn(0.1f, 5.0f)
        applyAudioAntiHash()
    }

    fun setAudioLayer2SyncAntiHash(enabled: Boolean) {
        this.isAudioLayer2SyncAntiHash = enabled
        applyAudioAntiHash()
    }

    fun calculateDriftSpeed(mode: AudioAntiHashIntensity = audioAntiHashMode): Float {
        val step = driftStep.toDouble()
        val aperiodic = (kotlin.math.sin(step) * 0.62 + kotlin.math.cos(step * 0.6180339887) * 0.38).toFloat()
        val sessionSpeedBase = if (isSessionDnaEnabled) currentSessionDna.speedCenterRatio else 1.0f
        val pctFactor = (audioAntiHashPercent / 2.0f).coerceIn(0.1f, 2.5f)
        val baseAperiodic = when (mode) {
            AudioAntiHashIntensity.LOW -> 0.0050f
            AudioAntiHashIntensity.MEDIUM -> 0.0085f
            AudioAntiHashIntensity.AGGRESSIVE -> 0.0135f
        }
        val effAperiodic = baseAperiodic * pctFactor
        val maxDeviation = 0.015f * pctFactor
        return (sessionSpeedBase + (trackEntropyBias * pctFactor) + aperiodic * effAperiodic)
            .coerceIn(sessionSpeedBase - maxDeviation, sessionSpeedBase + maxDeviation)
    }

    fun calculateDynamicPitch(mode: AudioAntiHashIntensity = audioAntiHashMode): Float {
        val step = driftStep.toDouble()
        val pitchWave = (kotlin.math.sin(step * 0.73) * 0.55 + kotlin.math.cos(step * 1.19) * 0.45).toFloat()
        val dnaPitchOffset = if (isSessionDnaEnabled) (currentSessionDna.pitchCenterRatio - 1.0f) else 0.0f
        val pctFactor = (audioAntiHashPercent / 2.0f).coerceIn(0.1f, 2.5f)
        val modeScale = when (mode) {
            AudioAntiHashIntensity.LOW -> 0.72f * pctFactor
            AudioAntiHashIntensity.MEDIUM -> 1.00f * pctFactor
            AudioAntiHashIntensity.AGGRESSIVE -> 1.25f * pctFactor
        }
        val sessionPitchCenter = 1.0f + (dnaPitchOffset * modeScale)
        val microPitchDelta = (pitchWave * 0.0065f * pctFactor)
        return (sessionPitchCenter + microPitchDelta).coerceIn(0.950f, 1.050f)
    }

    private val _currentVoiceEffect = MutableStateFlow(VoiceEffect.NORMAL)
    val currentVoiceEffect: StateFlow<VoiceEffect> = _currentVoiceEffect.asStateFlow()

    fun setVoiceEffect(effect: VoiceEffect) {
        _currentVoiceEffect.value = effect
        applyAudioAntiHash()
        Log.i(TAG, "🎭 [VOICE-EFFECT] StandaloneMusicPlayer Efek Suara disetel: ${effect.label} (${effect.pitchMultiplier}x pitch)")
    }

    private var activePresetReverb: android.media.audiofx.PresetReverb? = null
    private var activeBassBoost: android.media.audiofx.BassBoost? = null
    private var currentAudioSessionId: Int = 0

    private fun releaseAudioFx() {
        try {
            activePresetReverb?.enabled = false
            activePresetReverb?.release()
        } catch (_: Throwable) {}
        activePresetReverb = null

        try {
            activeBassBoost?.enabled = false
            activeBassBoost?.release()
        } catch (_: Throwable) {}
        activeBassBoost = null
        currentAudioSessionId = 0
    }

    private fun applyVoiceAudioFx(player: MediaPlayer, effect: VoiceEffect) {
        try {
            val sessionId = player.audioSessionId
            if (sessionId <= 0) return

            if (currentAudioSessionId != sessionId) {
                releaseAudioFx()
                currentAudioSessionId = sessionId
            }

            when (effect) {
                VoiceEffect.ROOM -> {
                    try { activeBassBoost?.enabled = false } catch (_: Throwable) {}
                    if (activePresetReverb == null) {
                        activePresetReverb = android.media.audiofx.PresetReverb(0, sessionId)
                    }
                    activePresetReverb?.preset = android.media.audiofx.PresetReverb.PRESET_SMALLROOM
                    activePresetReverb?.enabled = true
                }
                VoiceEffect.HOUSE -> {
                    try { activeBassBoost?.enabled = false } catch (_: Throwable) {}
                    if (activePresetReverb == null) {
                        activePresetReverb = android.media.audiofx.PresetReverb(0, sessionId)
                    }
                    activePresetReverb?.preset = android.media.audiofx.PresetReverb.PRESET_LARGEROOM
                    activePresetReverb?.enabled = true
                }
                VoiceEffect.ECHO -> {
                    try { activeBassBoost?.enabled = false } catch (_: Throwable) {}
                    if (activePresetReverb == null) {
                        activePresetReverb = android.media.audiofx.PresetReverb(0, sessionId)
                    }
                    activePresetReverb?.preset = android.media.audiofx.PresetReverb.PRESET_PLATE
                    activePresetReverb?.enabled = true
                }
                VoiceEffect.BASS_BOOST -> {
                    try { activePresetReverb?.enabled = false } catch (_: Throwable) {}
                    if (activeBassBoost == null) {
                        activeBassBoost = android.media.audiofx.BassBoost(0, sessionId)
                    }
                    activeBassBoost?.setStrength(1000.toShort())
                    activeBassBoost?.enabled = true
                }
                else -> {
                    try { activePresetReverb?.enabled = false } catch (_: Throwable) {}
                    try { activeBassBoost?.enabled = false } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "AudioFx tidak didukung oleh hardware/audio session: ${e.message}")
        }
    }

    fun applyAudioAntiHash() {
        val player = mediaPlayer ?: return
        applyVoiceAudioFx(player, _currentVoiceEffect.value)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val effect = _currentVoiceEffect.value
                val isVoiceEffectActive = effect != VoiceEffect.NORMAL

                val rawBasePitch = if (!isVoiceEffectActive) 1.0f else effect.pitchMultiplier
                val effectivePitchMult = (1.0f + _realtimeFinePitch.value).coerceIn(0.70f, 1.40f)
                val targetPitch = (rawBasePitch * effectivePitchMult).coerceIn(0.5f, 2.0f)
                val baseSpeed = if (isAudioAntiHashEnabled) calculateDriftSpeed(audioAntiHashMode) else 1.0f
                val targetSpeed = (baseSpeed * effect.speedMultiplier * _realtimeTempo.value).coerceIn(0.6f, 1.6f)
                val hasModifications = isVoiceEffectActive || kotlin.math.abs(_realtimeFinePitch.value) > 0.005f || kotlin.math.abs(_realtimeTempo.value - 1.0f) > 0.005f

                // Jika Normal dan speed mendekati 1.0x dan tanpa modifikasi: JANGAN SENTUH playbackParams agar stream audio 100% loss-free & lantang
                if (!hasModifications && kotlin.math.abs(targetSpeed - 1.0f) < 0.005f) {
                    val curParams = try { player.playbackParams } catch (_: Throwable) { null }
                    if (curParams != null && (curParams.pitch != 1.0f || curParams.speed != 1.0f)) {
                        try {
                            player.playbackParams = PlaybackParams().apply {
                                pitch = 1.0f
                                speed = 1.0f
                            }
                        } catch (_: Throwable) {}
                    }
                } else if (player.isPlaying) {
                    try {
                        val currentParams = try { player.playbackParams } catch (_: Throwable) { null }
                        val curPitch = currentParams?.pitch ?: 1.0f
                        val curSpeed = currentParams?.speed ?: 1.0f

                        if (kotlin.math.abs(curPitch - targetPitch) > 0.0005f || kotlin.math.abs(curSpeed - targetSpeed) > 0.0005f) {
                            val newParams = (currentParams ?: PlaybackParams()).apply {
                                pitch = targetPitch
                                speed = targetSpeed
                            }
                            player.playbackParams = newParams
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Hardware tidak mendukung pergeseran pitch, fallback ke suara asli: ${t.message}")
                        try {
                            player.playbackParams = PlaybackParams().apply { pitch = 1.0f; speed = 1.0f }
                        } catch (_: Throwable) {}
                    }
                }

                // Sinkronisasi Audio Tipe 2 ke Tipe 1 (Opsional)
                val mp2 = mediaPlayerLayer2
                if (mp2 != null) {
                    try {
                        if (isAudioLayer2SyncAntiHash && _isPlayingLayer2.value) {
                            val curParams2 = try { mp2.playbackParams } catch (_: Throwable) { null }
                            val curPitch2 = curParams2?.pitch ?: 1.0f
                            val curSpeed2 = curParams2?.speed ?: 1.0f
                            if (kotlin.math.abs(curPitch2 - targetPitch) > 0.0005f || kotlin.math.abs(curSpeed2 - targetSpeed) > 0.0005f) {
                                mp2.playbackParams = PlaybackParams().apply {
                                    pitch = targetPitch
                                    speed = targetSpeed
                                }
                            }
                        } else {
                            val curParams2 = try { mp2.playbackParams } catch (_: Throwable) { null }
                            if (curParams2 != null && (curParams2.pitch != 1.0f || curParams2.speed != 1.0f)) {
                                mp2.playbackParams = PlaybackParams().apply {
                                    pitch = 1.0f
                                    speed = 1.0f
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "applyAudioAntiHash error: ${e.message}")
        }
    }

    fun forceRecoverAudioOutput() {
        _isMuted.value = false
        if (_volume.value < 0.25f) {
            _volume.value = 0.85f
        }
        _isAudioStalledOrError.value = false
        _lastAudioErrorReason.value = null
        val cur = _currentTrack.value ?: playlistProvider?.invoke()?.firstOrNull()
        val pos = _currentPositionMs.value
        if (cur != null) {
            playAudioItem(cur.copy(isMuted = false), startPlaying = true, startPositionMs = pos)
        } else {
            play()
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            var lastPos = -1L
            var stuckTicks = 0
            while (isActive && _isPlaying.value) {
                try {
                    val mp = mediaPlayer
                    if (mp != null) {
                        val playingNow = try { mp.isPlaying } catch (_: Throwable) { false }
                        val posNow = try { mp.currentPosition.toLong() } catch (_: Throwable) { lastPos }
                        val durNow = try { mp.duration.toLong() } catch (_: Throwable) { _durationMs.value }
                        if (PlayingAndAdvancing(playingNow, posNow, lastPos)) {
                            _currentPositionMs.value = posNow
                            lastPos = posNow
                            stuckTicks = 0
                            lastAudioAdvanceTimeMs = System.currentTimeMillis()
                            if (_isAudioStalledOrError.value) {
                                _isAudioStalledOrError.value = false
                                _lastAudioErrorReason.value = null
                            }
                            val now = System.currentTimeMillis()
                            if (isAudioAntiHashEnabled && now - lastDriftTimeMs > 2200L) {
                                lastDriftTimeMs = now
                                driftStep += 0.418f
                                applyAudioAntiHash()
                            }
                            val cutOutroMs = (_realtimeCutOutroSec.value * 1000L)
                            if (cutOutroMs > 0L && durNow > 3000L && posNow >= (durNow - cutOutroMs)) {
                                Log.i(TAG, "✂️ [REALTIME-EDIT] Cut Outro tercapai ($posNow >= ${durNow - cutOutroMs}), loncat ke trek berikutnya")
                                handleTrackCompletion()
                                break
                            }
                        } else if (durNow > 1500L && posNow >= durNow - 500L) {
                            // Near EOF, let completion listener handle it
                            stuckTicks = 0
                        } else {
                            if (!playingNow && _isPlaying.value) {
                                stuckTicks++
                                if (stuckTicks >= 4) {
                                    stuckTicks = 0
                                    try {
                                        mp.start()
                                        val isItemMuted = (_currentTrack.value?.isMuted == true) || _isMuted.value
                                        val vol = if (isItemMuted) 0f else _volume.value
                                        mp.setVolume(vol, vol)
                                    } catch (_: Throwable) {}
                                }
                            } else {
                                stuckTicks = 0
                            }
                        }
                    }
                } catch (_: Throwable) {}
                delay(400)
            }
        }
    }

    private fun PlayingAndAdvancing(isPlaying: Boolean, currentPos: Long, lastPos: Long): Boolean {
        return isPlaying && currentPos != lastPos && currentPos >= 0L
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun releasePlayer() {
        stopProgressTracker()
        releaseAudioFx()
        try {
            mediaPlayer?.apply {
                try { setOnPreparedListener(null) } catch (_: Throwable) {}
                try { setOnCompletionListener(null) } catch (_: Throwable) {}
                try { setOnErrorListener(null) } catch (_: Throwable) {}
                try { setOnSeekCompleteListener(null) } catch (_: Throwable) {}
                try { setVolume(0f, 0f) } catch (_: Throwable) {}
                try { stop() } catch (_: Throwable) {}
                try { reset() } catch (_: Throwable) {}
                try { release() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        mediaPlayer = null
    }

    // =========================================================================
    // AUDIO TIPE 2: MANDIRI & BERDIRI SENDIRI DENGAN LOOPINGNYA
    // Berjalan mandiri tanpa terhubung ke mode manapun (Urut, Acak, Loop, atau SV4).
    // =========================================================================

    fun setLayer2Active(enabled: Boolean, autoStart: Boolean = true, autoStartIfLayer1Running: Boolean = autoStart) {
        val shouldStart = autoStart || autoStartIfLayer1Running
        _isLayer2Active.value = enabled
        Log.i(TAG, "🎧 [TIPE-2] Status Tipe 2 (Mandiri): enabled=$enabled")
        if (enabled) {
            if (shouldStart && !_isPlayingLayer2.value && _currentTrackLayer2.value != null) {
                playLayer2()
            }
        } else {
            if (_isPlayingLayer2.value) {
                pauseLayer2()
            }
        }
    }

    fun setLayer2Track(item: AudioItem?, autoStartIfActive: Boolean = false) {
        _currentTrackLayer2.value = item
        if (item != null) {
            playAudioItemLayer2(item, startPlaying = autoStartIfActive && _isLayer2Active.value)
        } else {
            stopLayer2(clearTrackState = true)
        }
    }

    @Synchronized
    fun playAudioItemLayer2(item: AudioItem, startPlaying: Boolean = true, startPositionMs: Long = 0L) {
        try {
            val current = _currentTrackLayer2.value
            val currentMp = mediaPlayerLayer2
            if (current?.id == item.id && currentMp != null) {
                if (startPlaying) {
                    try {
                        if (startPositionMs > 0L) {
                            currentMp.seekTo(startPositionMs.toInt())
                            _currentPositionMsLayer2.value = startPositionMs
                        }
                        currentMp.start()
                        applyEffectiveVolumeLayer2()
                        _isPlayingLayer2.value = true
                        startProgressTrackerLayer2()
                    } catch (_: Throwable) {
                        // Jika resume gagal, lanjutkan ke pembuatan player baru di bawah
                    }
                }
                return
            }

            val currentGen = ++sessionGenerationLayer2
            isPreparingLayer2 = true
            releasePlayerLayer2()

            val file = File(item.path)
            if (!file.exists() || file.length() == 0L) {
                Log.w(TAG, "[TIPE-2] File audio tidak ditemukan atau kosong: ${item.path}")
                isPreparingLayer2 = false
                _isPlayingLayer2.value = false
                return
            }

            val player = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                }

                try {
                    setDataSource(file.absolutePath)
                } catch (_: Throwable) {
                    val fis = FileInputStream(file)
                    setDataSource(fis.fd)
                }

                val isItemMuted = item.isMuted || _isMutedLayer2.value
                val vol = if (isItemMuted) 0f else _volumeLayer2.value
                setVolume(vol, vol)

                setOnPreparedListener { mp ->
                    if (currentGen != sessionGenerationLayer2 || mp != mediaPlayerLayer2) {
                        try { mp.setVolume(0f, 0f) } catch (_: Throwable) {}
                        try { mp.stop() } catch (_: Throwable) {}
                        try { mp.reset() } catch (_: Throwable) {}
                        try { mp.release() } catch (_: Throwable) {}
                        return@setOnPreparedListener
                    }
                    isPreparingLayer2 = false
                    _durationMsLayer2.value = mp.duration.toLong().coerceAtLeast(0L)
                    applyEffectiveVolumeLayer2()
                    mp.isLooping = _isLoopingLayer2.value
                    if (startPositionMs > 0L) {
                        try {
                            mp.seekTo(startPositionMs.toInt())
                            _currentPositionMsLayer2.value = startPositionMs
                        } catch (_: Throwable) {}
                    }
                    if (startPlaying) {
                        try {
                            mp.start()
                            _isPlayingLayer2.value = true
                            applyAudioAntiHash()
                            startProgressTrackerLayer2()
                        } catch (e: Throwable) {
                            Log.e(TAG, "[TIPE-2] Start player error: ${e.message}")
                            _isPlayingLayer2.value = false
                        }
                    } else {
                        _isPlayingLayer2.value = false
                    }
                }

                setOnCompletionListener { mp ->
                    if (currentGen != sessionGenerationLayer2 || mp != mediaPlayerLayer2) return@setOnCompletionListener
                    // Tipe 2: Berdiri sendiri dengan looping mandirinya
                    if (_isLoopingLayer2.value) {
                        try {
                            mp.seekTo(0)
                            mp.start()
                            _isPlayingLayer2.value = true
                            _currentPositionMsLayer2.value = 0L
                        } catch (e: Throwable) {
                            Log.w(TAG, "[TIPE-2] Loop restart error, fallback re-prepare: ${e.message}")
                            _currentTrackLayer2.value?.let { playAudioItemLayer2(it, startPlaying = true, startPositionMs = 0L) }
                        }
                    } else {
                        _isPlayingLayer2.value = false
                        _currentPositionMsLayer2.value = 0L
                        stopProgressTrackerLayer2()
                    }
                }

                setOnErrorListener { mp, what, extra ->
                    if (currentGen != sessionGenerationLayer2 || mp != mediaPlayerLayer2) return@setOnErrorListener true
                    isPreparingLayer2 = false
                    Log.e(TAG, "[TIPE-2] MediaPlayer error: what=$what, extra=$extra")
                    stopProgressTrackerLayer2()
                    _isPlayingLayer2.value = false
                    true
                }

                prepareAsync()
            }

            mediaPlayerLayer2 = player
            _currentTrackLayer2.value = item
            _currentPositionMsLayer2.value = 0L

        } catch (e: Throwable) {
            isPreparingLayer2 = false
            Log.e(TAG, "[TIPE-2] Gagal memutar audio: ${e.message}", e)
            _isPlayingLayer2.value = false
        }
    }

    @Synchronized
    fun playLayer2() {
        val player = mediaPlayerLayer2
        if (player != null) {
            try {
                if (!player.isPlaying) {
                    player.start()
                    _isPlayingLayer2.value = true
                    startProgressTrackerLayer2()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "[LAYER-2] Gagal resume playback: ${e.message}")
            }
        } else {
            val track = _currentTrackLayer2.value
            if (track != null) {
                playAudioItemLayer2(track, startPlaying = true)
            }
        }
    }

    @Synchronized
    fun pauseLayer2() {
        try {
            mediaPlayerLayer2?.let {
                if (it.isPlaying) it.pause()
            }
            _isPlayingLayer2.value = false
            stopProgressTrackerLayer2()
        } catch (e: Throwable) {
            Log.e(TAG, "[LAYER-2] Gagal pause: ${e.message}")
        }
    }

    @Synchronized
    fun stopLayer2(clearTrackState: Boolean = false) {
        try {
            releasePlayerLayer2()
            _isPlayingLayer2.value = false
            _currentPositionMsLayer2.value = 0L
            stopProgressTrackerLayer2()
            if (clearTrackState) {
                _currentTrackLayer2.value = null
                _durationMsLayer2.value = 0L
            }
        } catch (e: Throwable) {
            Log.e(TAG, "[LAYER-2] Gagal stop: ${e.message}")
        }
    }

    fun seekToLayer2(positionMs: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                mediaPlayerLayer2?.let { mp ->
                    val dur = try { mp.duration } catch (_: Throwable) { 0 }
                    val safePos = if (dur > 200) positionMs.toInt().coerceIn(0, dur - 100) else positionMs.toInt().coerceAtLeast(0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mp.seekTo(safePos.toLong(), MediaPlayer.SEEK_CLOSEST)
                    } else {
                        mp.seekTo(safePos)
                    }
                    _currentPositionMsLayer2.value = safePos.toLong()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "[LAYER-2] Gagal seek: ${e.message}")
            }
        }
    }

    @Synchronized
    fun setVolumeLayer2(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volumeLayer2.value = clamped
        applyEffectiveVolumeLayer2()
    }

    @Synchronized
    fun applyEffectiveVolumeLayer2() {
        val cur = _currentTrackLayer2.value
        val isItemMuted = _isMutedLayer2.value || (cur?.isMuted == true)
        val vol = if (isItemMuted) 0f else _volumeLayer2.value
        try {
            mediaPlayerLayer2?.setVolume(vol, vol)
        } catch (_: Throwable) {}
    }

    @Synchronized
    fun setMutedLayer2(muted: Boolean) {
        _isMutedLayer2.value = muted
        applyEffectiveVolumeLayer2()
    }

    @Synchronized
    fun setLoopModeLayer2(enabled: Boolean) {
        _isLoopingLayer2.value = enabled
        try {
            mediaPlayerLayer2?.isLooping = enabled
        } catch (_: Throwable) {}
    }

    @Synchronized
    fun toggleLayer2LoopMode(): Boolean {
        val next = !_isLoopingLayer2.value
        setLoopModeLayer2(next)
        return next
    }

    @Synchronized
    fun toggleLayer2PlayPause(): Boolean {
        return if (_isPlayingLayer2.value) {
            pauseLayer2()
            false
        } else {
            playLayer2()
            true
        }
    }

    private fun startProgressTrackerLayer2() {
        stopProgressTrackerLayer2()
        progressJobLayer2 = scope.launch {
            while (isActive && _isPlayingLayer2.value) {
                try {
                    val mp = mediaPlayerLayer2
                    if (mp != null) {
                        val posNow = try { mp.currentPosition.toLong() } catch (_: Throwable) { 0L }
                        val durNow = try { mp.duration.toLong() } catch (_: Throwable) { _durationMsLayer2.value }
                        if (posNow >= 0L) {
                            _currentPositionMsLayer2.value = posNow
                        }
                        if (durNow > 0L) {
                            _durationMsLayer2.value = durNow
                        }
                    }
                } catch (_: Throwable) {}
                delay(400)
            }
        }
    }

    private fun stopProgressTrackerLayer2() {
        progressJobLayer2?.cancel()
        progressJobLayer2 = null
    }

    private fun releasePlayerLayer2() {
        stopProgressTrackerLayer2()
        val player = mediaPlayerLayer2
        mediaPlayerLayer2 = null
        if (player != null) {
            try {
                try { player.setOnPreparedListener(null) } catch (_: Throwable) {}
                try { player.setOnCompletionListener(null) } catch (_: Throwable) {}
                try { player.setOnErrorListener(null) } catch (_: Throwable) {}
                try { player.setOnSeekCompleteListener(null) } catch (_: Throwable) {}
                try { player.setVolume(0f, 0f) } catch (_: Throwable) {}
                try { player.stop() } catch (_: Throwable) {}
                try { player.reset() } catch (_: Throwable) {}
                try { player.release() } catch (_: Throwable) {}
            } catch (_: Throwable) {}
        }
    }

    // =========================================================================
    // AUDIO TIPE 3: RINGTONE ACAK DINAMIS (NON-COLLIDING RANDOM INTERVAL LAYER)
    // Berisi ringtone/SFX acak yang muncul dengan interval acak dinamis,
    // tidak pernah menempati slot waktu yang sama persis, dan dapat memilih
    // beberapa ringtone tanpa bertabrakan.
    // =========================================================================

    fun updateLayer3Config(
        enabled: Boolean,
        ringtoneIds: Set<String>,
        volume: Float = _volumeLayer3.value,
        isMuted: Boolean = _isMutedLayer3.value,
        minIntervalSec: Int = layer3MinIntervalSec,
        maxIntervalSec: Int = layer3MaxIntervalSec
    ) {
        _isLayer3Active.value = enabled
        _layer3RingtoneIds.value = ringtoneIds
        _volumeLayer3.value = volume.coerceIn(0f, 1f)
        _isMutedLayer3.value = isMuted
        val safeMin = minIntervalSec.coerceIn(5, 600)
        val safeMax = maxIntervalSec.coerceAtLeast(safeMin + 5).coerceIn(10, 1800)
        layer3MinIntervalSec = safeMin
        layer3MaxIntervalSec = safeMax
        applyEffectiveVolumeLayer3()

        if (enabled) {
            startRingtoneScheduler(playFirstQuickly = true)
        } else {
            stopRingtoneScheduler()
            stopLayer3(clearTrackState = false)
        }
    }

    fun setLayer3Active(enabled: Boolean) {
        _isLayer3Active.value = enabled
        if (enabled) {
            startRingtoneScheduler(playFirstQuickly = true)
        } else {
            stopRingtoneScheduler()
            stopLayer3(clearTrackState = false)
        }
    }

    fun setLayer3RingtoneIds(ids: Set<String>) {
        _layer3RingtoneIds.value = ids
        if (_isLayer3Active.value) {
            if (ringtoneSchedulerJob == null || ringtoneSchedulerJob?.isActive != true) {
                startRingtoneScheduler(playFirstQuickly = false)
            }
        } else {
            stopRingtoneScheduler()
            stopLayer3(clearTrackState = false)
        }
    }

    fun setLayer3IntervalRange(minSec: Int, maxSec: Int) {
        val safeMin = minSec.coerceIn(3, 600)
        val safeMax = maxSec.coerceAtLeast(safeMin + 2).coerceIn(5, 1800)
        layer3MinIntervalSec = safeMin
        layer3MaxIntervalSec = safeMax
    }

    fun setVolumeLayer3(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volumeLayer3.value = clamped
        applyEffectiveVolumeLayer3()
    }

    fun setMutedLayer3(muted: Boolean) {
        _isMutedLayer3.value = muted
        applyEffectiveVolumeLayer3()
    }

    fun toggleMutedLayer3(): Boolean {
        val next = !_isMutedLayer3.value
        setMutedLayer3(next)
        return next
    }

    @Synchronized
    fun applyEffectiveVolumeLayer3() {
        val vol = if (_isMutedLayer3.value) 0f else _volumeLayer3.value
        try {
            mediaPlayerLayer3?.setVolume(vol, vol)
        } catch (_: Throwable) {}
    }

    /**
     * Menghitung interval acak berikutnya secara dinamis dengan jitter mikro dan aturan non-collision:
     * Menjamin jeda baru tidak pernah berada di slot waktu yang sama dengan jeda sebelumnya (|Δt_baru - Δt_lama| >= 2000ms).
     */
    fun calculateNextNonCollidingIntervalMs(
        minSec: Int = layer3MinIntervalSec,
        maxSec: Int = layer3MaxIntervalSec,
        lastIntervalMs: Long = lastRingtoneIntervalMs
    ): Long {
        val safeMinSec = minSec.coerceAtLeast(3)
        val safeMaxSec = maxSec.coerceAtLeast(safeMinSec + 2)
        val minMs = safeMinSec * 1000L
        val maxMs = safeMaxSec * 1000L
        val range = (maxMs - minMs).coerceAtLeast(500L)
        val random = java.util.concurrent.ThreadLocalRandom.current()

        var attempts = 0
        var candidateMs: Long
        do {
            val base = minMs + (random.nextDouble() * range).toLong()
            val microJitterMs = (random.nextDouble() * 2000.0 - 1000.0).toLong() // ±1 detik mikro-jitter
            candidateMs = (base + microJitterMs).coerceIn(minMs, maxMs)
            attempts++
        } while (attempts < 10 && lastIntervalMs > 0L && kotlin.math.abs(candidateMs - lastIntervalMs) < 2000L)

        return candidateMs
    }

    @Volatile
    private var isPreparingLayer3: Boolean = false

    private fun resolveAllAudioForLayer3(): List<AudioItem> {
        val fullList = allAudioPlaylistProvider?.invoke() ?: emptyList()
        if (fullList.isNotEmpty()) return fullList
        return playlistProvider?.invoke() ?: emptyList()
    }

    private fun startRingtoneScheduler(playFirstQuickly: Boolean = true) {
        stopRingtoneScheduler()
        ringtoneSchedulerJob = scope.launch(Dispatchers.Main) {
            var isFirstRun = playFirstQuickly
            while (isActive && _isLayer3Active.value) {
                val poolIds = _layer3RingtoneIds.value
                // PENTING: Tipe 3 HANYA boleh berjalan jika pengguna secara eksplisit telah memilih ringtone!
                if (poolIds.isEmpty()) {
                    _nextRingtoneEtaSec.value = null
                    delay(500L)
                    continue
                }

                val allAudio = resolveAllAudioForLayer3()
                val candidateItems = allAudio.filter { poolIds.contains(it.id) && File(it.path).exists() }
                if (candidateItems.isEmpty()) {
                    _nextRingtoneEtaSec.value = null
                    delay(500L)
                    continue
                }

                val nextIntervalMs = if (isFirstRun) {
                    isFirstRun = false
                    // Bunyikan cepat saat pertama kali dinyalakan (2 detik) jika ringtone sudah dipilih
                    2000L
                } else {
                    calculateNextNonCollidingIntervalMs()
                }
                lastRingtoneIntervalMs = nextIntervalMs

                val targetTimeMs = System.currentTimeMillis() + nextIntervalMs
                while (isActive && _isLayer3Active.value && System.currentTimeMillis() < targetTimeMs) {
                    // Jika pool dikosongkan di tengah jalan, batalkan hitung mundur
                    if (_layer3RingtoneIds.value.isEmpty()) break
                    val remainingSec = ((targetTimeMs - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(1L).toInt()
                    _nextRingtoneEtaSec.value = remainingSec
                    delay(350L)
                }

                if (!isActive || !_isLayer3Active.value || _layer3RingtoneIds.value.isEmpty()) continue

                // Trigger ringtone acak khusus dari pool yang dipilih
                _nextRingtoneEtaSec.value = 0
                playRandomRingtoneFromPool()

                // Tunggu sebentar agar prepareAsync mulai berjalan
                var prepWaitMs = 0L
                while (isActive && isPreparingLayer3 && prepWaitMs < 4000L) {
                    delay(100L)
                    prepWaitMs += 100L
                }
                if (isPreparingLayer3 && !_isPlayingLayer3.value) {
                    isPreparingLayer3 = false
                }

                // Tunggu hingga ringtone selesai sebelum menjadwalkan jeda berikutnya
                while (isActive && _isPlayingLayer3.value) {
                    delay(250L)
                }
            }
            _nextRingtoneEtaSec.value = null
        }
    }

    private fun stopRingtoneScheduler() {
        ringtoneSchedulerJob?.cancel()
        ringtoneSchedulerJob = null
        _nextRingtoneEtaSec.value = null
    }

    fun playRandomRingtoneFromPool() {
        val poolIds = _layer3RingtoneIds.value
        if (poolIds.isEmpty()) return // Ketat: jika pool kosong, JANGAN comot lagu BGM!

        val allAudio = resolveAllAudioForLayer3()
        val candidateItems = allAudio.filter { poolIds.contains(it.id) && File(it.path).exists() }
        if (candidateItems.isEmpty()) return // Ketat: jika tidak ada di pool, JANGAN comot allAudio!

        // Pilih acak dinamis dengan anti-repeat berturut-turut jika ada >= 2 item
        val chosenItem = if (candidateItems.size > 1 && lastRingtoneItemId != null) {
            val filtered = candidateItems.filter { it.id != lastRingtoneItemId }
            if (filtered.isNotEmpty()) filtered.random() else candidateItems.random()
        } else {
            candidateItems.random()
        }

        lastRingtoneItemId = chosenItem.id
        playRingtoneTrack(chosenItem)
    }

    fun triggerTestRingtone(item: AudioItem? = null) {
        val poolIds = _layer3RingtoneIds.value
        val allAudio = resolveAllAudioForLayer3()
        val trackToPlay = item ?: allAudio.firstOrNull { poolIds.contains(it.id) && File(it.path).exists() }
        if (trackToPlay != null) {
            // Unmute layer 3 if it was muted so user can hear the test
            if (_isMutedLayer3.value) {
                setMutedLayer3(false)
            }
            if (_volumeLayer3.value < 0.20f) {
                setVolumeLayer3(0.85f)
            }
            playRingtoneTrack(trackToPlay)
        } else {
            Log.w(TAG, "[TIPE-3] Belum ada ringtone dipilih untuk dites sebagai Tipe 3")
        }
    }

    @Synchronized
    fun playRingtoneTrack(item: AudioItem) {
        try {
            val file = File(item.path)
            if (!file.exists() || file.length() == 0L) {
                Log.w(TAG, "[TIPE-3] File ringtone tidak ditemukan atau kosong: ${item.path}")
                isPreparingLayer3 = false
                _isPlayingLayer3.value = false
                return
            }

            // Pastikan AudioFocus STREAM_MUSIC aktif agar suara Tipe 3 langsung terdengar meski Tipe 1 sedang mati
            try {
                val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .build()
                    audioManager?.requestAudioFocus(focusRequest)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                }
            } catch (_: Throwable) {}

            releasePlayerLayer3()
            val currentGen = ++sessionGenerationLayer3
            _currentTrackLayer3.value = item
            isPreparingLayer3 = true

            val player = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                }

                setOnPreparedListener { mp ->
                    if (sessionGenerationLayer3 != currentGen) {
                        isPreparingLayer3 = false
                        try { mp.release() } catch (_: Throwable) {}
                        return@setOnPreparedListener
                    }
                    isPreparingLayer3 = false
                    try {
                        applyEffectiveVolumeLayer3()
                        mp.isLooping = false
                        mp.start()
                        _isPlayingLayer3.value = true
                        applyEffectiveVolume() // Ducking BGM Tipe 1 agar suara tidak bertabrakan/bocor
                        Log.i(TAG, "🔔 [TIPE-3] Memutar ringtone acak: ${item.name}")
                    } catch (e: Throwable) {
                        Log.e(TAG, "[TIPE-3] Gagal start ringtone: ${e.message}")
                        _isPlayingLayer3.value = false
                        applyEffectiveVolume()
                    }
                }
                setOnCompletionListener {
                    isPreparingLayer3 = false
                    _isPlayingLayer3.value = false
                    applyEffectiveVolume() // Pulihkan volume BGM Tipe 1
                    Log.i(TAG, "🔔 [TIPE-3] Selesai ringtone: ${item.name}")
                    releasePlayerLayer3()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "[TIPE-3] Error ringtone ($what, $extra)")
                    isPreparingLayer3 = false
                    _isPlayingLayer3.value = false
                    applyEffectiveVolume()
                    releasePlayerLayer3()
                    true
                }

                try {
                    setDataSource(file.absolutePath)
                } catch (_: Throwable) {
                    FileInputStream(file).use { fis ->
                        setDataSource(fis.fd)
                    }
                }
            }

            mediaPlayerLayer3 = player
            player.prepareAsync()
        } catch (e: Throwable) {
            Log.e(TAG, "[TIPE-3] Exception playRingtoneTrack: ${e.message}")
            isPreparingLayer3 = false
            _isPlayingLayer3.value = false
        }
    }

    @Synchronized
    fun stopLayer3(clearTrackState: Boolean = false) {
        try {
            isPreparingLayer3 = false
            releasePlayerLayer3()
            _isPlayingLayer3.value = false
            applyEffectiveVolume()
            if (clearTrackState) {
                _currentTrackLayer3.value = null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "[LAYER-3] Gagal stop: ${e.message}")
        }
    }

    private fun releasePlayerLayer3() {
        val player = mediaPlayerLayer3
        mediaPlayerLayer3 = null
        if (player != null) {
            try {
                try { player.setOnPreparedListener(null) } catch (_: Throwable) {}
                try { player.setOnCompletionListener(null) } catch (_: Throwable) {}
                try { player.setOnErrorListener(null) } catch (_: Throwable) {}
                try { player.setVolume(0f, 0f) } catch (_: Throwable) {}
                try { player.stop() } catch (_: Throwable) {}
                try { player.reset() } catch (_: Throwable) {}
                try { player.release() } catch (_: Throwable) {}
            } catch (_: Throwable) {}
        }
    }
}
