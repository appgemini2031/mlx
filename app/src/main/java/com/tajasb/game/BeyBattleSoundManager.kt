package com.tajasb.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sin

/**
 * BeyBattleSoundManager:
 * Mesin Audio Sintetis Realtime & Announcer Komentator Suara untuk BeyBattle Royale.
 *
 * Menghasilkan:
 * 1. Sound FX Benturan Logam Nyaring (Clang / Metal Hit) via AudioTrack PCM sintetis
 * 2. Sound FX Ledakan Bass & Dentuman Meteor (Sub-Bass Boom)
 * 3. Sound FX Sinar Laser & Pusaran Vortex (Sci-Fi Zap)
 * 4. Voice Announcer Komentator Bersemangat via Android Text-To-Speech (TTS):
 *    "FIRST BLOOD!", "DOUBLE K.O!", "RAMPAGE!", "BOSS SPAWNED!", "VICTORY ROYALE!"
 */
class BeyBattleSoundManager private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "BeySoundManager"
        private const val SAMPLE_RATE = 22050

        @Volatile
        private var instance: BeyBattleSoundManager? = null

        fun getInstance(context: Context): BeyBattleSoundManager {
            return instance ?: synchronized(this) {
                instance ?: BeyBattleSoundManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Pengaturan Host (Terpisah SFX, TTS, Volume) & Gerbang Anti-Bocor
    @Volatile private var isPlaybackAllowed: Boolean = false
    @Volatile var isSoundEnabled: Boolean = true
    @Volatile var isSfxEnabled: Boolean = true
    @Volatile var isAnnouncerEnabled: Boolean = true
    @Volatile var masterVolume: Float = 0.85f
    private val playbackGeneration = AtomicLong(0L)

    // Text To Speech untuk Announcer Komentator
    private var tts: TextToSpeech? = null
    @Volatile private var isTtsReady = false
    private val speechQueue = ConcurrentLinkedQueue<String>()

    // Track Pool Aktif (Maks 4 kanal simultan agar AudioFlinger tidak macet saat live ramai)
    private val activeTracks = java.util.concurrent.CopyOnWriteArrayList<AudioTrack>()

    // Audio Buffers Sintetis Pre-Calculated (Zero Latency Playback)
    private val metalClangPcm = generateMetalClangPcm()
    private val sawShredPcm = generateSawShredPcm()
    private val bassBoomPcm = generateBassBoomPcm()
    private val laserZapPcm = generateLaserZapPcm()
    private val meteorBlastPcm = generateMeteorBlastPcm()
    @Volatile private var lastShredPlayMs = 0L
    @Volatile private var lastClangPlayMs = 0L

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Throwable) {
            Log.e(TAG, "Gagal inisialisasi TTS: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale.US)
            if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                tts?.setPitch(0.82f)      // Nada bass berat ala komentator arena gulat / e-sports
                tts?.setSpeechRate(1.20f)  // Cepat & bersemangat
                // Mainkan antrean jika masih aktif
                if (isPlaybackAllowed && isSoundEnabled && isAnnouncerEnabled) {
                    while (speechQueue.isNotEmpty()) {
                        speechQueue.poll()?.let { speakInternal(it) }
                    }
                } else {
                    speechQueue.clear()
                }
            }
        }
    }

    // =========================================================================
    // KONTROL PENGHENTIAN INSTAN (ANTI-BOCOR SUARA)
    // =========================================================================

    fun setPlaybackAllowed(allowed: Boolean) {
        isPlaybackAllowed = allowed
        if (!allowed) {
            stopAll()
        }
    }

    fun stopAnnouncer() {
        playbackGeneration.incrementAndGet()
        speechQueue.clear()
        try {
            tts?.stop()
        } catch (_: Throwable) {}
    }

    fun stopSfx() {
        playbackGeneration.incrementAndGet()
        val snapshot = activeTracks.toList()
        activeTracks.clear()
        for (t in snapshot) {
            try {
                t.pause()
                t.flush()
                t.stop()
                t.release()
            } catch (_: Throwable) {}
        }
    }

    fun stopAll() {
        stopAnnouncer()
        stopSfx()
    }

    // =========================================================================
    // VOICE ANNOUNCER KOMENTATOR (TTS)
    // =========================================================================

    fun announceFirstBlood(killerName: String) {
        announce("First Blood! $killerName takes the lead!")
    }

    fun announceDoubleKo() {
        announce("Double K.O! Brutal collision!")
    }

    fun announceRampage(killerName: String, koCount: Int) {
        announce("Rampage! $killerName on a streak of $koCount!")
    }

    fun announceMeteor() {
        announce("Warning! Meteor Strike incoming!")
    }

    fun announceBlizzard() {
        announce("Blizzard freeze activated!")
    }

    fun announceBossSpawn() {
        announce("Warning! Titan Boss has entered the arena! Attack together!")
    }

    fun announceBossDefeated(heroName: String) {
        announce("Titan Boss defeated by $heroName!")
    }

    fun announceVictory(championName: String) {
        announce("Victory Royale! $championName is the Champion!")
    }

    fun announce(text: String) {
        if (!isPlaybackAllowed || !isSoundEnabled || !isAnnouncerEnabled || masterVolume <= 0.01f) return
        if (isTtsReady) {
            speakInternal(text)
        } else {
            if (speechQueue.size < 2) {
                speechQueue.offer(text)
            }
        }
    }

    private fun speakInternal(text: String) {
        if (!isPlaybackAllowed || !isSoundEnabled || !isAnnouncerEnabled || masterVolume <= 0.01f) return
        val gen = playbackGeneration.get()
        scope.launch(Dispatchers.Main) {
            try {
                if (!isPlaybackAllowed || gen != playbackGeneration.get() || !isSoundEnabled || !isAnnouncerEnabled) {
                    return@launch
                }
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, masterVolume.coerceIn(0f, 1f))
                }
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "announcer_${System.currentTimeMillis()}")
            } catch (_: Throwable) {}
        }
    }

    // =========================================================================
    // SOUND FX SINTETIS (AUDIO TRACK PCM)
    // =========================================================================

    /**
     * Memainkan suara benturan logam antar gangsing ("CLANG!")
     */
    fun playMetalClang(intensity: Float = 1.0f) {
        if (!isSoundEnabled || !isSfxEnabled || masterVolume <= 0.01f) return
        val now = System.currentTimeMillis()
        if (now - lastClangPlayMs < 85L) return
        lastClangPlayMs = now
        playPcmAsync(metalClangPcm, volumeMultiplier = (intensity.coerceIn(0.5f, 1.5f) * masterVolume))
    }

    /**
     * Memainkan suara gesekan bilah gergaji berkecepatan tinggi ("SHRRD!")
     */
    fun playSawShred(intensity: Float = 1.0f) {
        if (!isSoundEnabled || !isSfxEnabled || masterVolume <= 0.01f) return
        val now = System.currentTimeMillis()
        if (now - lastShredPlayMs < 85L) return
        lastShredPlayMs = now
        playPcmAsync(sawShredPcm, volumeMultiplier = (intensity.coerceIn(0.45f, 1.3f) * masterVolume * 0.85f))
    }

    /**
     * Memainkan suara ledakan bass hantaman keras atau Ring-Out ("BOOM!")
     */
    fun playBassBoom(intensity: Float = 1.0f) {
        if (!isSoundEnabled || !isSfxEnabled || masterVolume <= 0.01f) return
        playPcmAsync(bassBoomPcm, volumeMultiplier = (intensity.coerceIn(0.6f, 1.8f) * masterVolume))
    }

    /**
     * Memainkan suara laser atau sengatan petir ("ZAP!")
     */
    fun playLaserZap() {
        if (!isSoundEnabled || !isSfxEnabled || masterVolume <= 0.01f) return
        playPcmAsync(laserZapPcm, volumeMultiplier = masterVolume * 0.85f)
    }

    /**
     * Memainkan suara meteor jatuh dan meledak ("CRASH BOOM!")
     */
    fun playMeteorImpact() {
        if (!isSoundEnabled || !isSfxEnabled || masterVolume <= 0.01f) return
        playPcmAsync(meteorBlastPcm, volumeMultiplier = masterVolume * 1.2f)
    }

    private fun playPcmAsync(pcmData: ShortArray, volumeMultiplier: Float) {
        if (!isPlaybackAllowed || !isSoundEnabled || !isSfxEnabled || masterVolume <= 0.01f) return
        if (activeTracks.size >= 4) return
        val gen = playbackGeneration.get()
        scope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            try {
                if (!isPlaybackAllowed || gen != playbackGeneration.get() || !isSoundEnabled || !isSfxEnabled) {
                    return@launch
                }
                val bufferSize = pcmData.size * 2
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                activeTracks.add(track)
                track.write(pcmData, 0, pcmData.size)
                track.setVolume((volumeMultiplier * 0.8f).coerceIn(0.0f, 1.0f))
                track.play()

                val durationMs = (pcmData.size * 1000L) / SAMPLE_RATE + 50L
                kotlinx.coroutines.delay(durationMs)
            } catch (_: Throwable) {
            } finally {
                if (track != null) {
                    activeTracks.remove(track)
                    try {
                        track.stop()
                    } catch (_: Throwable) {}
                    try {
                        track.release()
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    // =========================================================================
    // GENERATOR GELOMBANG PCM SINTETIS (ZERO ASSET DEPENDENCY)
    // =========================================================================

    private fun generateMetalClangPcm(): ShortArray {
        // Nada logam nyaring: gabungan frekuensi tinggi (1200 Hz, 2400 Hz, 3800 Hz) dengan decay cepat
        val numSamples = (SAMPLE_RATE * 0.18).toInt() // 180 ms
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val decay = Math.exp(-t * 22.0) // Redaman sangat cepat khas logam
            val wave = (
                sin(2.0 * Math.PI * 1450.0 * t) * 0.5 +
                sin(2.0 * Math.PI * 2850.0 * t) * 0.3 +
                sin(2.0 * Math.PI * 4200.0 * t) * 0.2
            ) * decay
            pcm[i] = (wave * 30000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    private fun generateSawShredPcm(): ShortArray {
        // Suara gesekan gergaji cepat: modulasi amplitudo sawtooth 95ms dengan harmonik tajam
        val numSamples = (SAMPLE_RATE * 0.095).toInt()
        val pcm = ShortArray(numSamples)
        val rand = java.util.Random(77)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = kotlin.math.sin((t / 0.095) * Math.PI)
            val grindMod = 0.5 + 0.5 * sin(2.0 * Math.PI * 65.0 * t)
            val sawWave = (
                sin(2.0 * Math.PI * 720.0 * t) * 0.45 +
                sin(2.0 * Math.PI * 1440.0 * t) * 0.30 +
                (rand.nextDouble() * 2.0 - 1.0) * 0.25
            ) * env * grindMod
            pcm[i] = (sawWave * 28000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    private fun generateBassBoomPcm(): ShortArray {
        // Ledakan bass bergemuruh: pitch turun dari 180 Hz ke 45 Hz dengan distorsi lembut
        val numSamples = (SAMPLE_RATE * 0.32).toInt() // 320 ms
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 160.0 * Math.exp(-t * 8.0) + 40.0
            val decay = Math.exp(-t * 7.0)
            val wave = sin(2.0 * Math.PI * freq * t) * decay
            // Soft clipping untuk rasa getaran bass tebal
            val clipped = Math.tanh(wave * 2.2)
            pcm[i] = (clipped * 31500).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    private fun generateLaserZapPcm(): ShortArray {
        // Suara tembakan laser sci-fi: sweep cepat dari 2800 Hz turun ke 350 Hz
        val numSamples = (SAMPLE_RATE * 0.12).toInt() // 120 ms
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = (t / 0.12).coerceIn(0.0, 1.0)
            val freq = 2600.0 * (1.0 - progress * 0.85)
            val decay = 1.0 - progress
            val wave = sin(2.0 * Math.PI * freq * t) * decay
            pcm[i] = (wave * 28000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    private fun generateMeteorBlastPcm(): ShortArray {
        // Hantaman gempa meteor raksasa: durasi 480 ms dengan noise rumble dan bass gemuruh
        val numSamples = (SAMPLE_RATE * 0.48).toInt()
        val pcm = ShortArray(numSamples)
        val rand = java.util.Random(42)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val decay = Math.exp(-t * 5.5)
            val bass = sin(2.0 * Math.PI * (110.0 * Math.exp(-t * 6.0) + 35.0) * t) * 0.7
            val rumbleNoise = (rand.nextDouble() * 2.0 - 1.0) * 0.3 * Math.exp(-t * 12.0)
            val total = (bass + rumbleNoise) * decay
            pcm[i] = (total * 32000).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {}
    }
}
