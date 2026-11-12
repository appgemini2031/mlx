package com.tajasb.game

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tajasb.eventrelay.ViewerAvatarCache
import com.tajasb.notif.OverlayBannerBroadcaster
import com.tajasb.quran.InteractiveQuranKhatamEngine
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * InteractiveBeyBattleEngine (Brutal Chaos & Live-Hardened Edition):
 * Mesin Fisika 2D 60 FPS untuk Game Interaktif "🌀 BeyBattle Royale (Gangsing Tempur TikTok Live)".
 *
 * Fitur & Perbaikan Stabilitas Live:
 * - Thread-safe `stateLock` untuk melindungi seluruh mutasi daftar pemain/partikel saat live ramai.
 * - Loop fisika 60 FPS terlindung `try/catch` agar tidak pernah mati mendadak.
 * - Guard aktif (`isGameActive`) + `soundManager.stopAll()` mencegah kebocoran suara di luar game.
 * - Penanganan seri (0 bertahan) & Auto-Reset terjamin dengan pengisian bot opsional.
 * - Seluruh parameter fisika, audio (SFX/TTS/Volume), rintangan, auto-reset, dan HUD dapat diatur Host.
 */
class InteractiveBeyBattleEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BeyBattleEngine"
        private const val PREFS_NAME = "bey_battle_storage_v1"

        private val DEFAULT_BOT_NAMES = listOf(
            "TopGamer", "SultanLive", "NinjaBlade", "RatuSpin", "MegaPro",
            "CyberTop", "BlazeSpin", "StormGear", "NeonSaw", "VortexKing",
            "IronClaw", "TurboAce"
        )

        private val COLOR_PALETTE = listOf(
            "#06B6D4", // Neon Cyan
            "#EF4444", // Crimson Red
            "#10B981", // Emerald Green
            "#F59E0B", // Amber Gold
            "#8B5CF6", // Electric Purple
            "#EC4899", // Neon Pink
            "#3B82F6", // Royal Blue
            "#14B8A6"  // Teal
        )

        @Volatile
        private var instance: InteractiveBeyBattleEngine? = null

        fun peekInstance(): InteractiveBeyBattleEngine? = instance

        fun getInstance(context: Context): InteractiveBeyBattleEngine {
            return instance ?: synchronized(this) {
                instance ?: InteractiveBeyBattleEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateLock = Any()

    private val _state = MutableStateFlow(BeyBattleState())
    val state: StateFlow<BeyBattleState> = _state.asStateFlow()

    // Daftar pemain, partikel, teks damage melayang & kill feed (dilindungi stateLock)
    private val playersList = mutableListOf<BeyBladePlayer>()
    private val eliminatedList = mutableListOf<BeyBladePlayer>()
    private val particlesList = mutableListOf<SparkParticle>()
    private val floatingTextsList = mutableListOf<FloatingDamageText>()
    private val killFeedList = mutableListOf<KillFeedEntry>()

    // Rintangan & Jurus Ulti
    private val trapdoorsList = mutableListOf(
        TrapdoorZone(1, -0.45f, -0.45f, 0.16f, HazardTrapState.CLOSED),
        TrapdoorZone(2, 0.45f, -0.45f, 0.16f, HazardTrapState.CLOSED),
        TrapdoorZone(3, -0.45f, 0.45f, 0.16f, HazardTrapState.CLOSED),
        TrapdoorZone(4, 0.45f, 0.45f, 0.16f, HazardTrapState.CLOSED)
    )
    private val meteorsList = mutableListOf<ActiveMeteor>()
    @Volatile private var titanBoss: BeyBladePlayer? = null

    // Cache avatar base64
    private val avatarBase64Cache = ConcurrentHashMap<String, String>()

    // Audio & Announcer terpadu berbasis AudioTrack PCM
    private val soundManager by lazy { BeyBattleSoundManager.getInstance(context) }

    private var physicsJob: Job? = null
    private var broadcastJob: Job? = null
    @Volatile private var isPendingBroadcast = false
    private var lastBroadcastTime = 0L
    private var lastStatePublishMs = 0L
    private var lastEliminationTimeMs = 0L
    private var laserAngleDeg = 0f
    private var screenShake = 0f
    private var currentShrinkFactor = 1.0f

    fun getActivePlayersSnapshot(): List<BeyBladePlayer> = synchronized(stateLock) {
        playersList.filter { !it.isEliminated }
    }

    fun getAllPlayersSnapshot(): List<BeyBladePlayer> = synchronized(stateLock) {
        playersList.toList()
    }

    fun getParticlesSnapshot(): List<SparkParticle> = synchronized(stateLock) {
        particlesList.toList()
    }

    fun getFloatingTextsSnapshot(): List<FloatingDamageText> = synchronized(stateLock) {
        floatingTextsList.toList()
    }

    fun getTrapdoorsSnapshot(): List<TrapdoorZone> = synchronized(stateLock) {
        trapdoorsList.toList()
    }

    fun getMeteorsSnapshot(): List<ActiveMeteor> = synchronized(stateLock) {
        meteorsList.toList()
    }

    fun getBossSnapshot(): BeyBladePlayer? = titanBoss

    init {
        loadPersistedState()
        startPhysicsLoop()
    }

    private fun loadPersistedState() {
        if (!prefs.getBoolean("key_default_bey_off_v1", false)) {
            prefs.edit()
                .putBoolean("is_enabled", false)
                .putBoolean("key_default_bey_off_v1", true)
                .apply()
        }
        val enabled = prefs.getBoolean("is_enabled", false)
        val round = prefs.getInt("current_round", 1).coerceAtLeast(1)
        val posX = prefs.getFloat("pos_x_pct", 0.50f).coerceIn(0.05f, 0.95f)
        val posY = prefs.getFloat("pos_y_pct", 0.50f).coerceIn(0.05f, 0.95f)
        val scale = prefs.getFloat("overlay_scale", 1.0f).coerceIn(0.5f, 2.5f)
        val rot = prefs.getInt("overlay_rotation", 0)
        val mir = prefs.getBoolean("is_mirrored", false)
        val widthScale = prefs.getFloat("arena_width_scale", 1.0f).coerceIn(0.4f, 1.8f)
        val heightScale = prefs.getFloat("arena_height_scale", 1.0f).coerceIn(0.4f, 1.8f)
        val arenaShape = prefs.getString("arena_shape", "CIRCLE") ?: "CIRCLE"
        val boundaryModeName = prefs.getString("boundary_mode", ArenaBoundaryMode.WALL_BOUNCE.name) ?: ArenaBoundaryMode.WALL_BOUNCE.name
        val boundaryMode = try { ArenaBoundaryMode.valueOf(boundaryModeName) } catch (_: Throwable) { ArenaBoundaryMode.WALL_BOUNCE }
        val arenaBgType = prefs.getString("arena_bg_type", "CYBER_GRID") ?: "CYBER_GRID"
        val customBgPath = prefs.getString("custom_bg_path", "") ?: ""

        // Rintangan & Boss
        val hazardsEnabled = prefs.getBoolean("hazards_enabled", true)
        val vortexEnabled = prefs.getBoolean("vortex_enabled", true)
        val laserEnabled = prefs.getBoolean("laser_enabled", true)
        val trapdoorsEnabled = prefs.getBoolean("trapdoors_enabled", true)
        val bossMaxHp = prefs.getFloat("boss_max_hp", 3000f).coerceIn(1000f, 10000f)

        // Audio terpisah
        val soundEnabled = prefs.getBoolean("sound_enabled", true)
        val sfxEnabled = prefs.getBoolean("sfx_enabled", true)
        val announcerEnabled = prefs.getBoolean("announcer_enabled", true)
        val masterVolume = prefs.getFloat("master_volume", 0.85f).coerceIn(0f, 1f)

        // Aturan Auto-Reset & Bot
        val autoResetEnabled = prefs.getBoolean("auto_reset_enabled", true)
        val celebrationDurationSec = prefs.getInt("celebration_duration_sec", 4).coerceIn(1, 15)
        val autoSpawnBotsEnabled = prefs.getBoolean("auto_spawn_bots_enabled", true)
        val minBotTargetCount = prefs.getInt("min_bot_target_count", 4).coerceIn(2, 12)
        val maxActivePlayers = prefs.getInt("max_active_players", 25).coerceIn(5, 40)

        // Parameter Fisika
        val passiveHpDecayPerSec = prefs.getFloat("passive_hp_decay", 0.45f).coerceIn(0f, 3.0f)
        val bounceMultiplier = prefs.getFloat("bounce_multiplier", 1.0f).coerceIn(0.5f, 2.0f)
        val damageMultiplier = prefs.getFloat("damage_multiplier", 1.0f).coerceIn(0.5f, 2.5f)
        val maxLikeSizeBonus = prefs.getFloat("max_like_size_bonus", 0.35f).coerceIn(0f, 0.50f)
        val shrinkStartSec = prefs.getInt("shrink_start_sec", 20).coerceIn(5, 60)
        val minShrinkFactor = prefs.getFloat("min_shrink_factor", 0.52f).coerceIn(0.35f, 0.90f)

        // Goyang Layar & Retensi Gifter
        val isScreenShakeEnabled = prefs.getBoolean("is_screen_shake_enabled", true)
        val screenShakeMultiplier = prefs.getFloat("screen_shake_multiplier", 1.0f).coerceIn(0.2f, 1.5f)
        val retainGiftersAcrossRounds = prefs.getBoolean("retain_gifters_across_rounds", true)

        // Visibilitas HUD
        val showKillFeed = prefs.getBoolean("show_kill_feed", true)
        val showEventTicker = prefs.getBoolean("show_event_ticker", true)
        val showRoundBadge = prefs.getBoolean("show_round_badge", true)

        soundManager.isSoundEnabled = soundEnabled
        soundManager.isSfxEnabled = sfxEnabled
        soundManager.isAnnouncerEnabled = announcerEnabled
        soundManager.masterVolume = masterVolume

        val initialState = BeyBattleState(
            isEnabled = enabled,
            isArenaDisplayActive = false,
            currentRound = round,
            roundStartedAtMs = System.currentTimeMillis(),
            posXpct = posX,
            posYpct = posY,
            overlayScale = scale,
            overlayRotation = rot,
            isMirrored = mir,
            arenaWidthScale = widthScale,
            arenaHeightScale = heightScale,
            arenaShape = arenaShape,
            boundaryMode = boundaryMode,
            shrinkFactor = 1.0f,
            arenaBgType = arenaBgType,
            customBgPath = customBgPath,
            isHazardsEnabled = hazardsEnabled,
            isVortexEnabled = vortexEnabled,
            isLaserEnabled = laserEnabled,
            isTrapdoorsEnabled = trapdoorsEnabled,
            bossMaxHp = bossMaxHp,
            isSoundEnabled = soundEnabled,
            isSfxEnabled = sfxEnabled,
            isAnnouncerEnabled = announcerEnabled,
            masterVolume = masterVolume,
            autoResetEnabled = autoResetEnabled,
            celebrationDurationSec = celebrationDurationSec,
            autoSpawnBotsEnabled = autoSpawnBotsEnabled,
            minBotTargetCount = minBotTargetCount,
            maxActivePlayers = maxActivePlayers,
            passiveHpDecayPerSec = passiveHpDecayPerSec,
            bounceMultiplier = bounceMultiplier,
            damageMultiplier = damageMultiplier,
            maxLikeSizeBonus = maxLikeSizeBonus,
            shrinkStartSec = shrinkStartSec,
            minShrinkFactor = minShrinkFactor,
            isScreenShakeEnabled = isScreenShakeEnabled,
            screenShakeMultiplier = screenShakeMultiplier,
            retainGiftersAcrossRounds = retainGiftersAcrossRounds,
            showKillFeed = showKillFeed,
            showEventTicker = showEventTicker,
            showRoundBadge = showRoundBadge,
            trapdoors = trapdoorsList.toList()
        )
        _state.value = initialState
        soundManager.setPlaybackAllowed(initialState.isGameActive)
    }

    // =========================================================================
    // AUDIO & SOUND FX (TERPISAH SFX, TTS, VOLUME + ANTI-BOCOR)
    // =========================================================================

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
        soundManager.isSoundEnabled = enabled
        if (!enabled) {
            soundManager.stopAll()
        }
        _state.value = _state.value.copy(isSoundEnabled = enabled)
    }

    fun setAudioSettings(sfxEnabled: Boolean, announcerEnabled: Boolean, masterVolume: Float) {
        val clampedVol = masterVolume.coerceIn(0f, 1f)
        val masterSoundOn = sfxEnabled || announcerEnabled
        prefs.edit()
            .putBoolean("sound_enabled", masterSoundOn)
            .putBoolean("sfx_enabled", sfxEnabled)
            .putBoolean("announcer_enabled", announcerEnabled)
            .putFloat("master_volume", clampedVol)
            .apply()

        soundManager.isSoundEnabled = masterSoundOn
        soundManager.isSfxEnabled = sfxEnabled
        soundManager.isAnnouncerEnabled = announcerEnabled
        soundManager.masterVolume = clampedVol

        if (!sfxEnabled) soundManager.stopSfx()
        if (!announcerEnabled) soundManager.stopAnnouncer()
        if (!masterSoundOn || clampedVol <= 0.01f) soundManager.stopAll()

        _state.value = _state.value.copy(
            isSoundEnabled = masterSoundOn,
            isSfxEnabled = sfxEnabled,
            isAnnouncerEnabled = announcerEnabled,
            masterVolume = clampedVol
        )
    }

    fun playAnnouncerVoice(text: String) {
        val cur = _state.value
        if (!cur.isGameActive || !cur.isSoundEnabled || !cur.isAnnouncerEnabled || text.isBlank()) return
        _state.value = cur.copy(lastAnnouncerVoiceText = text)
        soundManager.announce(text)
    }

    fun playClangSound(isHeavy: Boolean) {
        val cur = _state.value
        if (!cur.isGameActive || !cur.isSoundEnabled || !cur.isSfxEnabled) return
        if (isHeavy) {
            soundManager.playMetalClang(1.3f)
        } else {
            soundManager.playSawShred(0.95f)
        }
    }

    fun playExplosionSound() {
        val cur = _state.value
        if (!cur.isGameActive || !cur.isSoundEnabled || !cur.isSfxEnabled) return
        soundManager.playBassBoom(1.3f)
    }

    // =========================================================================
    // KONTROL AKTIVASI & PUSAT PENGATURAN FLEXIBLE
    // =========================================================================

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            InteractiveTugOfWarEngine.peekInstance()?.setEnabled(false)
            InteractiveKingOfThroneEngine.peekInstance()?.setEnabled(false, propagateRemote = false)
            InteractiveQuranKhatamEngine.peekInstance()?.setEnabled(false)
            InteractiveTrooperRoyaleEngine.peekInstance()?.setEnabled(false)
        }
        prefs.edit().putBoolean("is_enabled", enabled).apply()
        val updated = _state.value.copy(isEnabled = enabled)
        _state.value = updated
        soundManager.setPlaybackAllowed(updated.isGameActive)
        triggerStateBroadcast()
        if (enabled) {
            ensureBotsAtStartIfNeeded()
        }
    }

    fun setArenaDisplayActive(active: Boolean) {
        if (active) {
            InteractiveTugOfWarEngine.peekInstance()?.setArenaDisplayActive(false)
            InteractiveKingOfThroneEngine.peekInstance()?.setArenaDisplayActive(false)
            InteractiveTrooperRoyaleEngine.peekInstance()?.setArenaDisplayActive(false)
        }
        val updated = _state.value.copy(isArenaDisplayActive = active)
        _state.value = updated
        soundManager.setPlaybackAllowed(updated.isGameActive)
        if (updated.isGameActive) {
            ensureBotsAtStartIfNeeded()
        }
    }

    fun setHazardsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("hazards_enabled", enabled).apply()
        _state.value = _state.value.copy(isHazardsEnabled = enabled)
    }

    fun setIndividualHazards(
        vortex: Boolean,
        laser: Boolean,
        trapdoors: Boolean,
        bossMaxHp: Float
    ) {
        val clampedBossHp = bossMaxHp.coerceIn(1000f, 10000f)
        prefs.edit()
            .putBoolean("vortex_enabled", vortex)
            .putBoolean("laser_enabled", laser)
            .putBoolean("trapdoors_enabled", trapdoors)
            .putFloat("boss_max_hp", clampedBossHp)
            .apply()
        _state.value = _state.value.copy(
            isVortexEnabled = vortex,
            isLaserEnabled = laser,
            isTrapdoorsEnabled = trapdoors,
            bossMaxHp = clampedBossHp
        )
    }

    fun setAutoResetConfig(enabled: Boolean, celebrationSec: Int) {
        val clampedSec = celebrationSec.coerceIn(1, 15)
        prefs.edit()
            .putBoolean("auto_reset_enabled", enabled)
            .putInt("celebration_duration_sec", clampedSec)
            .apply()
        _state.value = _state.value.copy(
            autoResetEnabled = enabled,
            celebrationDurationSec = clampedSec
        )
    }

    fun setAutoBotConfig(enabled: Boolean, minBots: Int, maxPlayers: Int) {
        val clampedMin = minBots.coerceIn(2, 12)
        val clampedMax = maxPlayers.coerceIn(5, 40).coerceAtLeast(clampedMin)
        prefs.edit()
            .putBoolean("auto_spawn_bots_enabled", enabled)
            .putInt("min_bot_target_count", clampedMin)
            .putInt("max_active_players", clampedMax)
            .apply()
        _state.value = _state.value.copy(
            autoSpawnBotsEnabled = enabled,
            minBotTargetCount = clampedMin,
            maxActivePlayers = clampedMax
        )
    }

    fun setScreenShakeConfig(enabled: Boolean, multiplier: Float) {
        val clampedMul = multiplier.coerceIn(0.2f, 1.5f)
        prefs.edit()
            .putBoolean("is_screen_shake_enabled", enabled)
            .putFloat("screen_shake_multiplier", clampedMul)
            .apply()
        _state.value = _state.value.copy(
            isScreenShakeEnabled = enabled,
            screenShakeMultiplier = clampedMul
        )
    }

    fun setRetainGiftersConfig(retain: Boolean) {
        prefs.edit().putBoolean("retain_gifters_across_rounds", retain).apply()
        _state.value = _state.value.copy(retainGiftersAcrossRounds = retain)
    }

    fun setPhysicsConfig(
        bounceMultiplier: Float,
        damageMultiplier: Float,
        passiveDecay: Float,
        maxLikeBonus: Float,
        shrinkStartSec: Int,
        minShrinkFactor: Float
    ) {
        val cBounce = bounceMultiplier.coerceIn(0.5f, 2.0f)
        val cDamage = damageMultiplier.coerceIn(0.5f, 2.5f)
        val cDecay = passiveDecay.coerceIn(0f, 3.0f)
        val cLike = maxLikeBonus.coerceIn(0f, 0.50f)
        val cShrinkStart = shrinkStartSec.coerceIn(5, 60)
        val cMinShrink = minShrinkFactor.coerceIn(0.35f, 0.90f)

        prefs.edit()
            .putFloat("bounce_multiplier", cBounce)
            .putFloat("damage_multiplier", cDamage)
            .putFloat("passive_hp_decay", cDecay)
            .putFloat("max_like_size_bonus", cLike)
            .putInt("shrink_start_sec", cShrinkStart)
            .putFloat("min_shrink_factor", cMinShrink)
            .apply()

        synchronized(stateLock) {
            for (p in playersList) {
                p.maxLikeSizeCap = cLike
                if (p.likeSizeBonus > cLike) {
                    p.likeSizeBonus = cLike
                }
            }
        }

        _state.value = _state.value.copy(
            bounceMultiplier = cBounce,
            damageMultiplier = cDamage,
            passiveHpDecayPerSec = cDecay,
            maxLikeSizeBonus = cLike,
            shrinkStartSec = cShrinkStart,
            minShrinkFactor = cMinShrink
        )
    }

    fun setHudVisibility(showKillFeed: Boolean, showEventTicker: Boolean, showRoundBadge: Boolean) {
        prefs.edit()
            .putBoolean("show_kill_feed", showKillFeed)
            .putBoolean("show_event_ticker", showEventTicker)
            .putBoolean("show_round_badge", showRoundBadge)
            .apply()
        _state.value = _state.value.copy(
            showKillFeed = showKillFeed,
            showEventTicker = showEventTicker,
            showRoundBadge = showRoundBadge
        )
    }

    fun setTransform(posXpct: Float, posYpct: Float, scale: Float) {
        val clampedX = posXpct.coerceIn(0.05f, 0.95f)
        val clampedY = posYpct.coerceIn(0.05f, 0.95f)
        val clampedScale = scale.coerceIn(0.5f, 2.5f)
        prefs.edit()
            .putFloat("pos_x_pct", clampedX)
            .putFloat("pos_y_pct", clampedY)
            .putFloat("overlay_scale", clampedScale)
            .apply()
        _state.value = _state.value.copy(
            posXpct = clampedX,
            posYpct = clampedY,
            overlayScale = clampedScale
        )
        triggerStateBroadcast()
    }

    fun setArenaDimensions(widthScale: Float, heightScale: Float) {
        val clampedW = widthScale.coerceIn(0.4f, 1.8f)
        val clampedH = heightScale.coerceIn(0.4f, 1.8f)
        prefs.edit()
            .putFloat("arena_width_scale", clampedW)
            .putFloat("arena_height_scale", clampedH)
            .apply()
        _state.value = _state.value.copy(
            arenaWidthScale = clampedW,
            arenaHeightScale = clampedH
        )
        triggerStateBroadcast()
    }

    fun setArenaPreset(preset: String) {
        when (preset) {
            "1:1" -> setArenaDimensions(1.0f, 1.0f)
            "4:3" -> setArenaDimensions(1.25f, 0.95f)
            "16:9" -> setArenaDimensions(1.45f, 0.82f)
            "3:4" -> setArenaDimensions(0.95f, 1.25f)
            "9:16" -> setArenaDimensions(0.85f, 1.50f)
            else -> setArenaDimensions(1.0f, 1.0f)
        }
    }

    fun setArenaShape(shape: String) {
        val cleanShape = if (shape.equals("RECTANGLE", ignoreCase = true) || shape.equals("KOTAK", ignoreCase = true)) {
            "RECTANGLE"
        } else {
            "CIRCLE"
        }
        prefs.edit().putString("arena_shape", cleanShape).apply()
        _state.value = _state.value.copy(arenaShape = cleanShape)
        triggerStateBroadcast()
    }

    fun setBoundaryMode(mode: ArenaBoundaryMode) {
        prefs.edit().putString("boundary_mode", mode.name).apply()
        synchronized(stateLock) {
            if (mode == ArenaBoundaryMode.RING_OUT) {
                currentShrinkFactor = 1.0f
            }
        }
        _state.value = _state.value.copy(
            boundaryMode = mode,
            shrinkFactor = currentShrinkFactor
        )
        triggerStateBroadcast()
    }

    fun setArenaBackground(bgType: String, customPath: String = "") {
        prefs.edit()
            .putString("arena_bg_type", bgType)
            .putString("custom_bg_path", customPath)
            .apply()
        _state.value = _state.value.copy(
            arenaBgType = bgType,
            customBgPath = customPath
        )
    }

    fun saveCustomBackground(inputStream: java.io.InputStream): Boolean {
        return try {
            val file = java.io.File(context.filesDir, "beybattle_custom_bg.png")
            val outputStream = java.io.FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            setArenaBackground("CUSTOM_IMAGE", file.absolutePath)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Gagal menyimpan background kustom: ${e.message}")
            false
        }
    }

    // =========================================================================
    // TITAN BOSS BATTLE CO-OP
    // =========================================================================

    fun spawnTitanBoss() {
        val cur = _state.value
        val bossHp = cur.bossMaxHp.coerceIn(1000f, 10000f)
        val boss = BeyBladePlayer(
            id = "boss_goliath",
            name = "👹 TITAN GOLIATH",
            colorHex = "#FF1E1E",
            hp = bossHp,
            maxHp = bossHp,
            isBoss = true,
            rpm = 4000f,
            lastAction = "MENGAUM DI TENGAH ARENA!"
        )
        boss.avatarBitmap = BeyBladeAvatarHelper.getCircularAvatar(
            name = "BOSS",
            colorHex = "#DC2626",
            targetSizePx = 140
        )
        synchronized(stateLock) {
            titanBoss = boss
            screenShake = 0.8f
        }
        playExplosionSound()
        playAnnouncerVoice("Titan Boss Goliath has spawned! Destroy the Titan!")

        _state.value = _state.value.copy(
            isBossModeActive = true,
            bossPlayer = boss,
            screenShakeIntensity = 0.8f,
            lastEventBanner = "🚨 TITAN GOLIATH BANGKIT! Seluruh penonton serang serempak!",
            lastEventType = "BOSS_SPAWN"
        )
        triggerStateBroadcast()
    }

    fun setBossModeActive(active: Boolean) {
        if (active) {
            spawnTitanBoss()
        } else {
            synchronized(stateLock) {
                titanBoss = null
            }
            _state.value = _state.value.copy(isBossModeActive = false, bossPlayer = null)
        }
    }

    // =========================================================================
    // JURUS ULTIMATE TIKTOK GIFT
    // =========================================================================

    fun triggerMeteorStrike(targetX: Float? = null, targetY: Float? = null, callerName: String = "Sultan") {
        val cur = _state.value
        if (!cur.isGameActive) return

        val meteor: ActiveMeteor
        val snapshotMeteors: List<ActiveMeteor>
        synchronized(stateLock) {
            val activePlayers = playersList.filter { !it.isEliminated }
            val tx = targetX ?: activePlayers.randomOrNull()?.x ?: Random.nextDouble(-0.5, 0.5).toFloat()
            val ty = targetY ?: activePlayers.randomOrNull()?.y ?: Random.nextDouble(-0.5, 0.5).toFloat()
            meteor = ActiveMeteor(
                id = System.currentTimeMillis() + Random.nextLong(1000),
                targetX = tx,
                targetY = ty,
                altitude = 1.0f
            )
            meteorsList.add(meteor)
            snapshotMeteors = meteorsList.toList()
        }
        playAnnouncerVoice("Meteor Strike Incoming!")

        _state.value = _state.value.copy(
            lastEventBanner = "☄️ ${callerName} memanggil METEOR STRIKE ke arah (${String.format("%.1f", meteor.targetX)}, ${String.format("%.1f", meteor.targetY)})!",
            lastEventType = "METEOR",
            activeMeteors = snapshotMeteors
        )
    }

    fun triggerBlizzard(durationMs: Long = 3500L, callerName: String = "Mega") {
        val cur = _state.value
        if (!cur.isGameActive) return
        val until = System.currentTimeMillis() + durationMs
        synchronized(stateLock) {
            for (p in playersList) {
                p.frozenUntilMs = until
            }
        }
        playAnnouncerVoice("Blizzard Freeze!")
        playClangSound(isHeavy = false)

        _state.value = _state.value.copy(
            blizzardUntilMs = until,
            lastEventBanner = "❄️ ${callerName} memanggil BADAI SALJU! Seluruh arena membeku!",
            lastEventType = "BLIZZARD"
        )
    }

    fun triggerChainLightning(callerName: String = "Titan") {
        val cur = _state.value
        if (!cur.isGameActive) return
        synchronized(stateLock) {
            val active = playersList.filter { !it.isEliminated }
            for (p in active) {
                p.hp = (p.hp - 15f * cur.damageMultiplier).coerceAtLeast(0f)
                p.vx += Random.nextDouble(-0.4, 0.4).toFloat()
                p.vy += Random.nextDouble(-0.4, 0.4).toFloat()
                spawnSparksLocked(p.x, p.y, "#A855F7")
            }
        }
        playClangSound(isHeavy = true)
        _state.value = _state.value.copy(
            lastEventBanner = "⚡ ${callerName} memancarkan CHAIN LIGHTNING ke seluruh lawan!",
            lastEventType = "LIGHTNING"
        )
    }

    // =========================================================================
    // LOOP FISIKA 2D 60 FPS (TAHAN BANTING SAAT LIVE RAME)
    // =========================================================================

    private fun startPhysicsLoop() {
        physicsJob?.cancel()
        physicsJob = scope.launch {
            var lastTimeMs = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                val dt = ((now - lastTimeMs) / 1000f).coerceIn(0.005f, 0.05f)
                lastTimeMs = now

                if (_state.value.isGameActive) {
                    try {
                        updatePhysics(dt, now)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Physics loop recovered from error: ${t.message}", t)
                    }
                }

                delay(16L) // ~60 FPS
            }
        }
    }

    private fun updatePhysics(rawDt: Float, now: Long) {
        val soundCues = mutableListOf<() -> Unit>()
        var shouldPublish = false
        var shouldBroadcast = false
        var shouldResetRoundNext: Int? = null
        var pendingChampion: BeyBladePlayer? = null
        var pendingDraw = false

        val curState = _state.value

        // Deteksi Slow-Motion Final Duel 1 vs 1 (ketika 2 pemain tersisa dengan darah kritis)
        val activeSnap = synchronized(stateLock) { playersList.filter { !it.isEliminated } }
        val isFinalClash = activeSnap.size == 2 && activeSnap[0].hp < 40f && activeSnap[1].hp < 40f &&
                curState.bossPlayer == null && !curState.isRoundEnding
        val dt = if (isFinalClash) rawDt * 0.50f else rawDt

        synchronized(stateLock) {
            // Decay screen shake
            if (screenShake > 0f) {
                screenShake = (screenShake - 2.0f * dt).coerceAtLeast(0f)
            }

            // Selebrasi Juara / Seri Menunggu Auto-Reset
            if (curState.isRoundEnding) {
                curState.lastChampion?.let { champ ->
                    champ.rotationDeg = (champ.rotationDeg + 720f * dt) % 360f
                    champ.x *= (1f - 2.0f * dt)
                    champ.y *= (1f - 2.0f * dt)
                }
                if (now >= curState.celebrationUntilMs && curState.autoResetEnabled) {
                    shouldResetRoundNext = curState.currentRound + 1
                }
                shouldPublish = true
                // Lepas stateLock sebelum reset/publish
            } else {
                val activePlayers = playersList.filter { !it.isEliminated }
                val curW = curState.arenaWidthScale
                val curH = curState.arenaHeightScale
                val isBlizzard = now < curState.blizzardUntilMs

                // Hitung Shrinking Danger Zone pada mode WALL_BOUNCE
                if (curState.boundaryMode == ArenaBoundaryMode.WALL_BOUNCE && activePlayers.size > 1) {
                    val startedAt = if (curState.roundStartedAtMs > 0L) curState.roundStartedAtMs else now
                    val elapsedSec = ((now - startedAt) / 1000f).coerceAtLeast(0f)
                    val shrinkStart = curState.shrinkStartSec.toFloat()
                    val minShrink = curState.minShrinkFactor.coerceIn(0.35f, 0.90f)
                    currentShrinkFactor = if (elapsedSec > shrinkStart) {
                        (1.0f - (elapsedSec - shrinkStart) * 0.011f).coerceIn(minShrink, 1.0f)
                    } else {
                        1.0f
                    }
                } else {
                    currentShrinkFactor = 1.0f
                }
                val effW = curW * currentShrinkFactor
                val effH = curH * currentShrinkFactor

                // Update partikel percikan & pecahan K.O
                if (particlesList.isNotEmpty()) {
                    val it = particlesList.iterator()
                    while (it.hasNext()) {
                        val sp = it.next()
                        sp.x += sp.vx * dt
                        sp.y += sp.vy * dt
                        sp.vx *= (1f - 2.2f * dt)
                        sp.vy *= (1f - 2.2f * dt)
                        sp.lifeRemainingSec -= dt
                        if (sp.lifeRemainingSec <= 0f) {
                            it.remove()
                        }
                    }
                }

                // Update angka damage / teks melayang
                if (floatingTextsList.isNotEmpty()) {
                    val it = floatingTextsList.iterator()
                    while (it.hasNext()) {
                        val ft = it.next()
                        ft.y -= 0.16f * dt
                        ft.lifeRemainingSec -= dt
                        if (ft.lifeRemainingSec <= 0f) {
                            it.remove()
                        }
                    }
                }

                // Bersihkan kill feed lebih dari 5.5 detik
                if (killFeedList.isNotEmpty()) {
                    killFeedList.removeAll { now - it.createdAtMs > 5500L }
                }

                // 1. UPDATE RINTANGAN DINAMIS INDIVIDUAL
                val hazardsMaster = curState.isHazardsEnabled
                val vortexEnabled = hazardsMaster && curState.isVortexEnabled
                val laserEnabled = hazardsMaster && curState.isLaserEnabled
                val trapdoorsEnabled = hazardsMaster && curState.isTrapdoorsEnabled

                var vortexPull = 0f
                if (vortexEnabled) {
                    vortexPull = (sin(now / 1000.0).toFloat() * 0.18f + 0.12f).coerceIn(0.02f, 0.30f)
                }

                if (laserEnabled) {
                    laserAngleDeg = (laserAngleDeg + 75f * dt) % 360f
                }

                if (trapdoorsEnabled) {
                    val trapPhase = (now % 10000L)
                    val currentTrapState = when {
                        trapPhase < 5000L -> HazardTrapState.CLOSED
                        trapPhase < 7500L -> HazardTrapState.WARNING
                        else -> HazardTrapState.OPEN
                    }
                    for (td in trapdoorsList) {
                        td.state = currentTrapState
                    }
                } else {
                    for (td in trapdoorsList) {
                        td.state = HazardTrapState.CLOSED
                    }
                }

                // D. Update Active Meteors
                if (meteorsList.isNotEmpty()) {
                    val mIt = meteorsList.iterator()
                    while (mIt.hasNext()) {
                        val m = mIt.next()
                        m.altitude -= 1.4f * dt
                        if (m.altitude <= 0f && !m.hasImpacted) {
                            m.hasImpacted = true
                            mIt.remove()
                            screenShake = 1.0f
                            soundCues.add {
                                if (_state.value.isSoundEnabled && _state.value.isSfxEnabled) {
                                    soundManager.playMeteorImpact()
                                }
                            }
                            spawnSparksLocked(m.targetX, m.targetY, "#FF4500", count = 12, kind = ParticleKind.SHATTER)
                            spawnFloatingTextLocked(m.targetX, m.targetY, "METEOR!", "#FACC15", isCrit = true)

                            for (p in activePlayers) {
                                val dx = p.x - m.targetX
                                val dy = p.y - m.targetY
                                val d = hypot(dx, dy)
                                if (d < 0.65f && d > 0.001f) {
                                    val force = (1f - d / 0.65f) * 2.8f * curState.bounceMultiplier
                                    p.vx += (dx / d) * force
                                    p.vy += (dy / d) * force
                                    val meteorDmg = 25f * curState.damageMultiplier
                                    p.hp = (p.hp - meteorDmg).coerceAtLeast(0f)
                                    p.hitFlashUntilMs = now + 110L
                                    spawnFloatingTextLocked(p.x, p.y - p.effectiveRadius, "-${meteorDmg.toInt()}", "#EF4444", isCrit = true)
                                }
                            }
                        }
                    }
                }

                // 2. UPDATE POSISI PEMAIN INDIVIDUAL
                val laserRad = Math.toRadians(laserAngleDeg.toDouble()).toFloat()
                val laserNx = -sin(laserRad)
                val laserNy = cos(laserRad)

                for (p in activePlayers) {
                    if (p.isEliminated) continue
                    val frozen = p.isFrozen || isBlizzard
                    val frictionMultiplier = if (frozen) 0.85f else 0.30f
                    val dtEff = if (frozen) dt * 0.35f else dt

                    // Jejak gerak
                    p.trailX2 = p.trailX1
                    p.trailY2 = p.trailY1
                    p.trailX1 = p.x
                    p.trailY1 = p.y

                    p.x += p.vx * dtEff
                    p.y += p.vy * dtEff
                    p.vx *= (1f - frictionMultiplier * dt)
                    p.vy *= (1f - frictionMultiplier * dt)

                    // Dorongan putar mikro otomatis agar gangsing tetap aktif bertarung
                    val curSpeed = hypot(p.vx, p.vy)
                    if (!frozen && curSpeed < 0.14f * curState.bounceMultiplier) {
                        val driftAngle = (p.rotationDeg * 0.01745f) + (p.id.hashCode() % 7)
                        val driftBoost = 0.18f * curState.bounceMultiplier
                        p.vx += cos(driftAngle) * driftBoost * dt
                        p.vy += sin(driftAngle) * driftBoost * dt
                    }

                    // Tarikan Vortex Hisap ke tengah
                    val dist = hypot(p.x, p.y)
                    if (dist > 0.04f && vortexEnabled) {
                        p.vx -= (p.x / dist) * vortexPull * dt
                        p.vy -= (p.y / dist) * vortexPull * dt
                    }

                    // Kena Sinar Laser Berputar?
                    if (laserEnabled && dist < 0.95f * currentShrinkFactor) {
                        val distToLaser = kotlin.math.abs(p.x * laserNx + p.y * laserNy)
                        if (distToLaser < (p.effectiveRadius + 0.035f)) {
                            p.hp = (p.hp - 18f * curState.damageMultiplier * dt).coerceAtLeast(0f)
                            val radialSpeed = 0.55f * curState.bounceMultiplier
                            p.vx += (p.x / (dist.coerceAtLeast(0.1f))) * radialSpeed * dt
                            p.vy += (p.y / (dist.coerceAtLeast(0.1f))) * radialSpeed * dt
                            p.hitFlashUntilMs = now + 70L
                            spawnSparksLocked(p.x, p.y, "#06B6D4", count = 2, kind = ParticleKind.LIGHTNING)
                            soundCues.add {
                                if (_state.value.isSoundEnabled && _state.value.isSfxEnabled) {
                                    soundManager.playLaserZap()
                                }
                            }
                        }
                    }

                    // Cek Masuk Trapdoor Maut Terbuka (FALL_OUT)
                    if (trapdoorsEnabled) {
                        for (td in trapdoorsList) {
                            if (td.state == HazardTrapState.OPEN) {
                                val dTrap = hypot(p.x - td.x, p.y - td.y)
                                if (dTrap < td.radius * 0.85f && !p.isEliminated) {
                                    if (p.tier.isImmuneToTrapdoors) {
                                        // Gifter Tier 3 / Tier 4 (Thunder/Galaxy) kebal jatuh trapdoor
                                        p.hp = (p.hp - 10f * dt * curState.damageMultiplier).coerceAtLeast(1f)
                                        p.vx = (p.vx + (p.x - td.x) * 2.8f).coerceIn(-2.5f, 2.5f)
                                        p.vy = (p.vy + (p.y - td.y) * 2.8f).coerceIn(-2.5f, 2.5f)
                                        spawnSparksLocked(p.x, p.y, p.tier.auraColorHex, count = 3)
                                    } else {
                                        eliminatePlayerLocked(p, "FALL_OUT", killer = null, now = now, soundCues = soundCues)
                                        shouldBroadcast = true
                                        break
                                    }
                                }
                            }
                        }
                        if (p.isEliminated) continue
                    }

                    // Rotasi gangsing (Mode Fever memberikan boost rotasi ekstra)
                    val isFever = now < curState.feverUntilMs
                    val feverMultiplier = if (isFever) 1.55f else 1.0f
                    val spinSpeed = (p.rpm / 60f) * 360f * feverMultiplier
                    p.rotationDeg = (p.rotationDeg + spinSpeed * dt) % 360f

                    // Peluruhan HP pasif yang dapat disetting host
                    val baseDecay = curState.passiveHpDecayPerSec
                    val decay = if (p.tier == BeyBladeGiftTier.TIER_0_FREE) baseDecay else baseDecay * (1f - p.tier.damageReduction * 0.6f)
                    p.hp = (p.hp - decay * dt).coerceAtLeast(0f)
                    p.rpm = (500f + (p.hp / p.maxHp.coerceAtLeast(1f)) * 2500f)

                    if (p.shieldDurationSec > 0f) {
                        p.shieldDurationSec = (p.shieldDurationSec - dt).coerceAtLeast(0f)
                    }

                    // Cek Eliminasi Habis Putaran (SPIN OUT / SHREDDED)
                    if (p.hp <= 0f && !p.isEliminated) {
                        val lastAttacker = if (now - p.lastHitTimeMs < 3500L) {
                            playersList.firstOrNull { it.id == p.lastHitByPlayerId }
                        } else null
                        eliminatePlayerLocked(p, "SPIN_OUT", killer = lastAttacker, now = now, soundCues = soundCues)
                        shouldBroadcast = true
                        continue
                    }

                    // Cek Batas Arena: WALL_BOUNCE vs RING_OUT
                    val wallRestitution = (0.78f + 0.10f * curState.bounceMultiplier).coerceIn(0.65f, 0.96f)
                    val minRingOutHp = if (p.tier.tierLevel >= 2) 5f else 18f
                    val ringOutSpeedThreshold = 2.45f * (1f + p.tier.scaleMultiplier * 0.40f)

                    if (curState.boundaryMode == ArenaBoundaryMode.WALL_BOUNCE) {
                        val pRad = p.effectiveRadius * 0.85f
                        if (curState.arenaShape.equals("RECTANGLE", ignoreCase = true)) {
                            val limitX = (effW - pRad).coerceAtLeast(0.15f)
                            val limitY = (effH - pRad).coerceAtLeast(0.15f)
                            val hitX = kotlin.math.abs(p.x) > limitX
                            val hitY = kotlin.math.abs(p.y) > limitY
                            if (hitX || hitY) {
                                val impactSpeed = hypot(p.vx, p.vy)
                                if (!p.tier.isImmuneToWeakRingOut && (impactSpeed > ringOutSpeedThreshold || p.hp < minRingOutHp) && now - p.lastHitTimeMs < 2500L) {
                                    val killer = playersList.firstOrNull { it.id == p.lastHitByPlayerId }
                                    eliminatePlayerLocked(p, "RING_OUT", killer, now, soundCues)
                                    shouldBroadcast = true
                                } else {
                                    if (hitX) {
                                        p.x = p.x.coerceIn(-limitX, limitX)
                                        p.vx = -p.vx * wallRestitution
                                    }
                                    if (hitY) {
                                        p.y = p.y.coerceIn(-limitY, limitY)
                                        p.vy = -p.vy * wallRestitution
                                    }
                                    val wallDmg = (3.0f * curState.damageMultiplier * (1f - p.tier.damageReduction)).coerceAtLeast(0.5f)
                                    p.hp = (p.hp - wallDmg).coerceAtLeast(0f)
                                    spawnSparksLocked(p.x, p.y, "#38BDF8", count = 3)
                                    soundCues.add {
                                        if (_state.value.isSoundEnabled && _state.value.isSfxEnabled) {
                                            soundManager.playSawShred(0.65f)
                                        }
                                    }
                                }
                            }
                        } else {
                            val normLimit = (1.0f - (pRad / minOf(effW, effH).coerceAtLeast(0.25f))).coerceAtLeast(0.35f)
                            val normDist = hypot(p.x / effW, p.y / effH)
                            if (normDist > normLimit && normDist > 0.001f) {
                                val impactSpeed = hypot(p.vx, p.vy)
                                if (!p.tier.isImmuneToWeakRingOut && (impactSpeed > ringOutSpeedThreshold || p.hp < minRingOutHp) && now - p.lastHitTimeMs < 2500L) {
                                    val killer = playersList.firstOrNull { it.id == p.lastHitByPlayerId }
                                    eliminatePlayerLocked(p, "RING_OUT", killer, now, soundCues)
                                    shouldBroadcast = true
                                } else {
                                    val nx = (p.x / effW) / normDist
                                    val ny = (p.y / effH) / normDist
                                    p.x = nx * normLimit * effW
                                    p.y = ny * normLimit * effH
                                    val vDotN = p.vx * nx + p.vy * ny
                                    if (vDotN > 0f) {
                                        p.vx = (p.vx - (1f + wallRestitution) * vDotN * nx)
                                        p.vy = (p.vy - (1f + wallRestitution) * vDotN * ny)
                                    }
                                    val wallDmg = (3.0f * curState.damageMultiplier * (1f - p.tier.damageReduction)).coerceAtLeast(0.5f)
                                    p.hp = (p.hp - wallDmg).coerceAtLeast(0f)
                                    spawnSparksLocked(p.x, p.y, "#38BDF8", count = 3)
                                    soundCues.add {
                                        if (_state.value.isSoundEnabled && _state.value.isSfxEnabled) {
                                            soundManager.playSawShred(0.65f)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val isRingOut = if (curState.arenaShape.equals("RECTANGLE", ignoreCase = true)) {
                            kotlin.math.abs(p.x) > curW * 1.05f || kotlin.math.abs(p.y) > curH * 1.05f
                        } else {
                            hypot(p.x / curW, p.y / curH) > 1.05f
                        }
                        if (isRingOut && !p.isEliminated) {
                            if (p.tier.isImmuneToWeakRingOut && p.hp > 10f) {
                                // Galaxy Emperor terselamatkan oleh gravitasinya sendiri
                                p.x = p.x.coerceIn(-curW * 0.95f, curW * 0.95f)
                                p.y = p.y.coerceIn(-curH * 0.95f, curH * 0.95f)
                                p.vx = -p.vx * 0.7f
                                p.vy = -p.vy * 0.7f
                                p.hp = (p.hp - 15f).coerceAtLeast(1f)
                                spawnSparksLocked(p.x, p.y, "#FACC15", count = 5)
                            } else {
                                val killer = if (now - p.lastHitTimeMs < 3500L) {
                                    playersList.firstOrNull { it.id == p.lastHitByPlayerId }
                                } else null
                                eliminatePlayerLocked(p, "RING_OUT", killer, now, soundCues)
                                shouldBroadcast = true
                            }
                        }
                    }
                }

                // 3. UPDATE TITAN BOSS GOLIATH (Jika Aktif)
                titanBoss?.let { boss ->
                    if (!boss.isEliminated) {
                        boss.rotationDeg = (boss.rotationDeg + 850f * dt) % 360f
                        boss.x *= (1f - 0.5f * dt)
                        boss.y *= (1f - 0.5f * dt)

                        for (p in activePlayers) {
                            if (p.isEliminated) continue
                            val dx = p.x - boss.x
                            val dy = p.y - boss.y
                            val dist = hypot(dx, dy)
                            val minDist = p.effectiveRadius + boss.effectiveRadius

                            if (dist < minDist && dist > 0.001f) {
                                val nx = dx / dist
                                val ny = dy / dist
                                p.vx += nx * 2.2f * curState.bounceMultiplier
                                p.vy += ny * 2.2f * curState.bounceMultiplier
                                val bossHitDmg = 18f * curState.damageMultiplier
                                p.hp = (p.hp - bossHitDmg).coerceAtLeast(0f)
                                p.hitFlashUntilMs = now + 100L
                                boss.hp = (boss.hp - 10f * curState.damageMultiplier).coerceAtLeast(0f)
                                screenShake = 0.6f
                                soundCues.add {
                                    if (_state.value.isSoundEnabled && _state.value.isSfxEnabled) {
                                        soundManager.playMetalClang(1.2f)
                                    }
                                }
                                spawnSparksLocked(p.x, p.y, "#EF4444", count = 6)
                                spawnFloatingTextLocked(p.x, p.y - p.effectiveRadius, "-${bossHitDmg.toInt()}", "#EF4444", isCrit = true)

                                if (boss.hp <= 0f) {
                                    defeatTitanBossLocked(p, soundCues)
                                    shouldBroadcast = true
                                    break
                                }
                            }
                        }
                    }
                }

                // 4. TABRAKAN SHREDDING ANTAR GANGSING
                val count = activePlayers.size
                for (i in 0 until count) {
                    val p1 = activePlayers[i]
                    if (p1.isEliminated) continue

                    for (j in (i + 1) until count) {
                        val p2 = activePlayers[j]
                        if (p2.isEliminated) continue

                        val dx = p2.x - p1.x
                        val dy = p2.y - p1.y
                        val dist = hypot(dx, dy)
                        val minDist = p1.effectiveRadius + p2.effectiveRadius

                        if (dist < minDist && dist > 0.001f) {
                            val nx = dx / dist
                            val ny = dy / dist

                            val rvx = p2.vx - p1.vx
                            val rvy = p2.vy - p1.vy
                            val velAlongNormal = rvx * nx + rvy * ny

                            if (velAlongNormal < 0f) {
                                val m1 = p1.tier.scaleMultiplier * (1f + p1.likeSizeBonus)
                                val m2 = p2.tier.scaleMultiplier * (1f + p2.likeSizeBonus)
                                val restitution = (1.55f * curState.bounceMultiplier).coerceIn(0.6f, 2.5f)
                                val impulse = -(1f + restitution) * velAlongNormal / (1f / m1 + 1f / m2)

                                val bounceKick = (1.35f * curState.bounceMultiplier).coerceIn(0.6f, 2.4f)
                                p1.vx -= (impulse / m1) * nx * bounceKick
                                p1.vy -= (impulse / m1) * ny * bounceKick
                                p2.vx += (impulse / m2) * nx * bounceKick
                                p2.vy += (impulse / m2) * ny * bounceKick

                                val impactForce = (-velAlongNormal * 15f).coerceAtLeast(6.5f)
                                val isHeavy = impactForce > 13f
                                if (isHeavy) {
                                    soundCues.add {
                                        if (_state.value.isSoundEnabled && _state.value.isSfxEnabled) {
                                            soundManager.playMetalClang(1.25f)
                                        }
                                    }
                                    screenShake = (screenShake + 0.32f).coerceAtMost(0.9f)
                                } else {
                                    soundCues.add {
                                        if (_state.value.isSoundEnabled && _state.value.isSfxEnabled) {
                                            soundManager.playSawShred(0.95f)
                                        }
                                    }
                                }

                                val armor1 = (1f - p1.tier.damageReduction).coerceIn(0.2f, 1.0f)
                                val armor2 = (1f - p2.tier.damageReduction).coerceIn(0.2f, 1.0f)
                                val dmg1 = (impactForce * curState.damageMultiplier * (m2 / m1) * (if (p1.isShielded) 0.12f else 1.0f) * armor1).coerceIn(1f, 55f)
                                val dmg2 = (impactForce * curState.damageMultiplier * (m1 / m2) * (if (p2.isShielded) 0.12f else 1.0f) * armor2).coerceIn(1f, 55f)

                                p1.hp = (p1.hp - dmg1).coerceAtLeast(0f)
                                p2.hp = (p2.hp - dmg2).coerceAtLeast(0f)
                                p1.hitFlashUntilMs = now + 85L
                                p2.hitFlashUntilMs = now + 85L

                                p1.lastHitByPlayerId = p2.id
                                p1.lastHitTimeMs = now
                                p2.lastHitByPlayerId = p1.id
                                p2.lastHitTimeMs = now

                                val sparkX = (p1.x + p2.x) / 2f
                                val sparkY = (p1.y + p2.y) / 2f
                                spawnSparksLocked(sparkX, sparkY, if (isHeavy) "#FACC15" else p1.colorHex, count = if (isHeavy) 8 else 5)

                                val dmgColor1 = if (isHeavy) "#FACC15" else "#F87171"
                                val dmgColor2 = if (isHeavy) "#FACC15" else "#FB923C"
                                spawnFloatingTextLocked(p1.x, p1.y - p1.effectiveRadius, "-${dmg1.toInt()}", dmgColor1, isCrit = isHeavy)
                                spawnFloatingTextLocked(p2.x, p2.y - p2.effectiveRadius, "-${dmg2.toInt()}", dmgColor2, isCrit = isHeavy)
                            }

                            // Koreksi overlap posisi
                            val overlap = 0.5f * (minDist - dist)
                            p1.x -= overlap * nx
                            p1.y -= overlap * ny
                            p2.x += overlap * nx
                            p2.y += overlap * ny
                        }
                    }
                }

                // 5. PENENTUAN JUARA ATAU SERI (ANTI-MACET SAAT RONDE SELESAI)
                val remainingActive = playersList.filter { !it.isEliminated }
                if (titanBoss == null) {
                    if (remainingActive.size == 1 && (playersList.size > 1 || curState.totalEliminationsInRound > 0)) {
                        pendingChampion = remainingActive.first()
                    } else if (remainingActive.isEmpty() && (playersList.isNotEmpty() || curState.totalEliminationsInRound > 0)) {
                        pendingDraw = true
                    }
                }

                if (pendingChampion == null && !pendingDraw) {
                    shouldPublish = (now - lastStatePublishMs >= 250L) || (remainingActive.size != curState.activePlayersCount)
                    if (shouldPublish) {
                        lastStatePublishMs = now
                    }
                }
            }
        } // End synchronized(stateLock)

        // Eksekusi sound cues di luar lock
        if (_state.value.isGameActive) {
            for (cue in soundCues) {
                try { cue() } catch (_: Throwable) {}
            }
        }

        if (shouldResetRoundNext != null) {
            resetRoundInternal(nextRound = shouldResetRoundNext!!)
            return
        }
        if (pendingChampion != null) {
            declareChampion(pendingChampion!!, now)
            return
        }
        if (pendingDraw) {
            declareDraw(now)
            return
        }

        if (shouldPublish) {
            publishStateUpdate()
        }
        if (shouldBroadcast) {
            triggerStateBroadcast()
        }
    }

    private fun eliminatePlayerLocked(
        player: BeyBladePlayer,
        reason: String,
        killer: BeyBladePlayer?,
        now: Long,
        soundCues: MutableList<() -> Unit>
    ) {
        player.isEliminated = true
        player.eliminatedReason = reason
        player.vx = 0f
        player.vy = 0f
        eliminatedList.add(player)

        spawnSparksLocked(player.x, player.y, player.colorHex, count = 12, kind = ParticleKind.SHATTER)
        spawnFloatingTextLocked(player.x, player.y, "K.O!", "#EF4444", isCrit = true)
        screenShake = (screenShake + 0.45f).coerceAtMost(0.95f)
        soundCues.add {
            if (_state.value.isSoundEnabled && _state.value.isSfxEnabled) {
                soundManager.playBassBoom(1.1f)
            }
        }

        val totalKOs = _state.value.totalEliminationsInRound + 1
        val killerDisplay = if (killer != null && killer.id != player.id) killer.name else when (reason) {
            "FALL_OUT" -> "Trapdoor"
            "RING_OUT" -> "Arena"
            else -> "Shred"
        }
        killFeedList.add(
            KillFeedEntry(
                id = now + Random.nextLong(1000),
                killerName = killerDisplay.take(10),
                victimName = player.name.take(10),
                method = reason,
                createdAtMs = now
            )
        )
        while (killFeedList.size > 3) {
            killFeedList.removeAt(0)
        }

        val bannerText: String
        if (killer != null && killer.id != player.id) {
            killer.koCount++
            if (player.isBounty) {
                killer.koCount += 1 // Bonus KO buronan
                soundCues.add { playAnnouncerVoice("Bounty Claimed! Target terminated!") }
                bannerText = "🎯 BURONAN TUMBANG! ${killer.name} mengeliminasi ${player.name} & raih bonus KO! (${killer.koCount} K.O)"
            } else {
                bannerText = "💥 ${killer.name} mencabik ${player.name}! (${killer.koCount} K.O)"
                if (killer.koCount == 3) {
                    val kName = killer.name
                    soundCues.add { playAnnouncerVoice("Rampage! $kName is on a roll! Bounty placed!") }
                } else if (killer.koCount >= 5) {
                    soundCues.add { playAnnouncerVoice("Dominating!") }
                }
            }
        } else {
            bannerText = when (reason) {
                "FALL_OUT" -> "🕳️ ${player.name} jatuh ke lubang jebakan maut!"
                "RING_OUT" -> "💨 ${player.name} terpental keluar ring arena!"
                else -> "🌀 Bilah ${player.name} hancur kehabisan stamina!"
            }
        }

        if (reason == "FALL_OUT") {
            soundCues.add { playAnnouncerVoice("Into the void!") }
        } else if (totalKOs == 1) {
            soundCues.add { playAnnouncerVoice("First Blood!") }
        } else if (now - lastEliminationTimeMs < 1600L) {
            soundCues.add { playAnnouncerVoice("Double K.O!") }
        }
        lastEliminationTimeMs = now

        _state.value = _state.value.copy(
            totalEliminationsInRound = totalKOs,
            killFeed = killFeedList.toList(),
            lastEventBanner = bannerText,
            lastEventType = "KO"
        )
    }

    private fun defeatTitanBossLocked(slayer: BeyBladePlayer, soundCues: MutableList<() -> Unit>) {
        val boss = titanBoss ?: return
        boss.isEliminated = true
        titanBoss = null
        slayer.koCount += 5
        screenShake = 1.0f
        soundCues.add {
            val s = _state.value
            if (s.isGameActive && s.isSoundEnabled && s.isSfxEnabled) {
                soundManager.playMeteorImpact()
            }
            playAnnouncerVoice("Titan Slain! Live audience victory!")
        }
        spawnSparksLocked(boss.x, boss.y, "#FACC15", count = 16, kind = ParticleKind.SHATTER)
        spawnFloatingTextLocked(boss.x, boss.y, "BOSS K.O!", "#FACC15", isCrit = true)

        _state.value = _state.value.copy(
            bossPlayer = null,
            isBossModeActive = false,
            screenShakeIntensity = 1.0f,
            lastEventBanner = "🏆 TITAN GOLIATH DIKALAHKAN! Diselesaikan oleh ${slayer.name}!",
            lastEventType = "BOSS_DEFEAT"
        )
    }

    private fun declareChampion(champion: BeyBladePlayer, now: Long) {
        synchronized(stateLock) {
            champion.vx = 0f
            champion.vy = 0f
            champion.x = 0f
            champion.y = 0f
            champion.hp = champion.maxHp
            champion.rpm = 3000f
        }

        val cur = _state.value
        val celebMs = cur.celebrationDurationSec.coerceIn(1, 15) * 1000L
        val victoryBanner = "👑 VICTORY ROYALE: ${champion.name} JUARA ARENA! (${champion.koCount} K.O)"
        playAnnouncerVoice("Victory Royale! ${champion.name} wins the match!")
        playClangSound(isHeavy = true)

        _state.value = cur.copy(
            players = getAllPlayersSnapshot(),
            eliminatedPlayers = synchronized(stateLock) { eliminatedList.toList() },
            lastChampion = champion,
            defendingChampionName = champion.name,
            isDrawEnding = false,
            celebrationUntilMs = now + celebMs,
            lastEventBanner = victoryBanner,
            lastEventType = "CHAMPION"
        )
        triggerStateBroadcast()
    }

    private fun declareDraw(now: Long) {
        val cur = _state.value
        val drawDelayMs = cur.celebrationDurationSec.coerceIn(1, 3) * 1000L
        playAnnouncerVoice("Double knockout! Preparing next round!")
        _state.value = cur.copy(
            players = getAllPlayersSnapshot(),
            eliminatedPlayers = synchronized(stateLock) { eliminatedList.toList() },
            lastChampion = null,
            isDrawEnding = true,
            celebrationUntilMs = now + drawDelayMs,
            lastEventBanner = "⚔️ SERI TOTAL! Semua bilah tumbang — menyiapkan ronde baru...",
            lastEventType = "DRAW"
        )
        triggerStateBroadcast()
    }

    // =========================================================================
    // EVENT TIKTOK LIVE DARI LIVE EVENT RECEIVER
    // =========================================================================

    fun onJoinEvent(name: String, avatarUrl: String = "") {
        if (!_state.value.isGameActive) return
        val cleanName = name.trim().ifBlank { "Penonton" }
        scope.launch {
            if (!_state.value.isGameActive) return@launch
            var avatarTarget: BeyBladePlayer? = null
            synchronized(stateLock) {
                val p = spawnOrGetPlayerLocked(cleanName, avatarUrl, actionText = "bergabung ke arena")
                if (avatarUrl.isNotBlank() && (p.avatarBase64.isBlank() || p.avatarUrl != avatarUrl)) {
                    p.avatarUrl = avatarUrl
                    avatarTarget = p
                }
            }
            avatarTarget?.let { fetchAvatarAsync(it) }
        }
    }

    fun onLikeEvent(name: String, count: Int, avatarUrl: String = "") {
        if (!_state.value.isGameActive) return
        val cleanName = name.trim().ifBlank { "Penonton" }
        scope.launch {
            if (!_state.value.isGameActive) return@launch
            var avatarTarget: BeyBladePlayer? = null
            var bossSlayer: BeyBladePlayer? = null
            val soundCues = mutableListOf<() -> Unit>()

            synchronized(stateLock) {
                val cur = _state.value
                val p = spawnOrGetPlayerLocked(cleanName, avatarUrl, actionText = "Tap Like +${count}")
                if (avatarUrl.isNotBlank() && (p.avatarBase64.isBlank() || p.avatarUrl != avatarUrl)) {
                    p.avatarUrl = avatarUrl
                    avatarTarget = p
                }

                p.likesContributed += count
                p.hp = (p.hp + count * 1.5f).coerceAtMost(p.maxHp)
                p.rpm = (500f + (p.hp / p.maxHp.coerceAtLeast(1f)) * 2500f)
                p.maxLikeSizeCap = cur.maxLikeSizeBonus
                val prevBonus = p.likeSizeBonus
                p.likeSizeBonus = (p.likeSizeBonus + (count * 0.006f)).coerceAtMost(cur.maxLikeSizeBonus)

                titanBoss?.let { boss ->
                    if (!boss.isEliminated) {
                        boss.hp = (boss.hp - count * 1.2f * cur.damageMultiplier).coerceAtLeast(0f)
                        spawnSparksLocked(boss.x, boss.y, "#FF5555")
                        if (boss.hp <= 0f) {
                            bossSlayer = p
                            defeatTitanBossLocked(p, soundCues)
                        }
                    }
                }

                val dist = hypot(p.x, p.y)
                if (dist > 0.05f) {
                    p.vx -= (p.x / dist) * 0.12f
                    p.vy -= (p.y / dist) * 0.12f
                }
                spawnSparksLocked(p.x, p.y, "#FACC15", count = 5)
                if (count >= 5 || (p.likeSizeBonus - prevBonus) >= 0.025f) {
                    spawnFloatingTextLocked(p.x, p.y - p.effectiveRadius, "+SIZE & HP!", "#10B981")
                }
            }

            avatarTarget?.let { fetchAvatarAsync(it) }
            for (cue in soundCues) {
                try { cue() } catch (_: Throwable) {}
            }

            val curState = _state.value
            var newFeverLikes = curState.currentFeverLikes + count
            var newFeverUntil = curState.feverUntilMs
            var feverTriggered = false

            if (newFeverLikes >= curState.feverTargetLikes) {
                newFeverLikes = 0
                newFeverUntil = System.currentTimeMillis() + 15000L
                feverTriggered = true
                playAnnouncerVoice("Fever Overdrive! Maximum speed!")
                if (curState.isSoundEnabled && curState.isSfxEnabled) {
                    soundManager.playBassBoom(1.3f)
                }
            }

            val banner = if (feverTriggered) {
                "⚡🔥 FEVER OVERDRIVE AKTIF! Seluruh bilah berputar 2x lebih buas!"
            } else if (bossSlayer != null) {
                curState.lastEventBanner
            } else {
                "⚡ ${cleanName} Like +${count}! (Fever: ${newFeverLikes}/${curState.feverTargetLikes})"
            }

            _state.value = curState.copy(
                currentFeverLikes = newFeverLikes,
                feverUntilMs = newFeverUntil,
                lastEventBanner = banner,
                lastEventType = if (feverTriggered) "FEVER" else if (bossSlayer != null) curState.lastEventType else "LIKE"
            )
            triggerStateBroadcast()
        }
    }

    fun onChatEvent(name: String, text: String, avatarUrl: String = "") {
        if (!_state.value.isGameActive) return
        val cleanName = name.trim().ifBlank { "Penonton" }
        scope.launch {
            if (!_state.value.isGameActive) return@launch
            var avatarTarget: BeyBladePlayer? = null
            var bossSlayer: BeyBladePlayer? = null
            val soundCues = mutableListOf<() -> Unit>()

            synchronized(stateLock) {
                val cur = _state.value
                val p = spawnOrGetPlayerLocked(cleanName, avatarUrl, actionText = text)
                if (avatarUrl.isNotBlank() && (p.avatarBase64.isBlank() || p.avatarUrl != avatarUrl)) {
                    p.avatarUrl = avatarUrl
                    avatarTarget = p
                }

                p.chatsContributed++
                p.turboDashUntilMs = System.currentTimeMillis() + 850L

                val upper = text.uppercase()
                val isViralWord = upper.contains("GAS") || upper.contains("TABRAK") || upper.contains("BOOM") ||
                        upper.contains("DOR") || upper.contains("RATA") || upper.contains("ULTI") ||
                        upper.contains("HOKI") || upper.contains("SERBU") || upper.contains("SULTAN")

                val boss = titanBoss
                if (boss != null && !boss.isEliminated) {
                    val dx = boss.x - p.x
                    val dy = boss.y - p.y
                    val dist = hypot(dx, dy)
                    if (dist > 0.01f) {
                        val dashSpeed = (if (isViralWord) 1.75f else 1.45f) * cur.bounceMultiplier
                        p.vx = (dx / dist) * dashSpeed
                        p.vy = (dy / dist) * dashSpeed
                    }
                    val dmg = if (isViralWord) 25f else 15f
                    boss.hp = (boss.hp - dmg * cur.damageMultiplier).coerceAtLeast(0f)
                    if (boss.hp <= 0f) {
                        bossSlayer = p
                        defeatTitanBossLocked(p, soundCues)
                    }
                } else {
                    val opponents = playersList.filter { it.id != p.id && !it.isEliminated }
                    val target = opponents.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
                    if (target != null) {
                        val dx = target.x - p.x
                        val dy = target.y - p.y
                        val dist = hypot(dx, dy)
                        if (dist > 0.01f) {
                            val dashSpeed = (if (isViralWord) 1.65f else 1.35f) * cur.bounceMultiplier
                            p.vx = (dx / dist) * dashSpeed
                            p.vy = (dy / dist) * dashSpeed
                        }
                    } else {
                        val baseSpeed = if (isViralWord) 1.2 else 0.8
                        p.vx = Random.nextDouble(-baseSpeed, baseSpeed).toFloat() * cur.bounceMultiplier
                        p.vy = Random.nextDouble(-baseSpeed, baseSpeed).toFloat() * cur.bounceMultiplier
                    }
                }
            }

            avatarTarget?.let { fetchAvatarAsync(it) }
            for (cue in soundCues) {
                try { cue() } catch (_: Throwable) {}
            }
            if (bossSlayer == null) {
                val banner = "💬 ${cleanName}: \"${text.take(15)}\" ${if (text.uppercase().contains("GAS") || text.uppercase().contains("TABRAK") || text.uppercase().contains("ULTI")) "🔥 SUPER DASH!" else "meluncurkan turbo dash!"}"
                _state.value = _state.value.copy(
                    lastEventBanner = banner,
                    lastEventType = "DASH"
                )
            }
            triggerStateBroadcast()
        }
    }

    fun onGiftEvent(name: String, giftName: String, count: Int, coins: Long, avatarUrl: String = "") {
        if (!_state.value.isGameActive) return
        val cleanName = name.trim().ifBlank { "Penonton" }
        val totalCoins = if (coins > 0) coins else (count * 1L)

        scope.launch {
            if (!_state.value.isGameActive) return@launch
            var avatarTarget: BeyBladePlayer? = null
            val soundCues = mutableListOf<() -> Unit>()
            var ultAction: (() -> Unit)? = null
            val newTier = BeyBladeGiftTier.fromCoins(totalCoins)

            synchronized(stateLock) {
                val cur = _state.value
                val p = spawnOrGetPlayerLocked(cleanName, avatarUrl, actionText = "Kirim ${giftName} x${count}")
                if (avatarUrl.isNotBlank() && (p.avatarBase64.isBlank() || p.avatarUrl != avatarUrl)) {
                    p.avatarUrl = avatarUrl
                    avatarTarget = p
                }

                p.coinsContributed += totalCoins
                if (newTier.tierLevel >= p.tier.tierLevel) {
                    p.tier = newTier
                }
                // Set darah tebal bertingkat berdasarkan tier koin!
                p.maxHp = p.tier.baseMaxHp
                p.hp = p.maxHp
                p.rpm = 3000f

                titanBoss?.let { boss ->
                    if (!boss.isEliminated) {
                        val bossDmg = (totalCoins * 14f * cur.damageMultiplier).coerceAtLeast(30f)
                        boss.hp = (boss.hp - bossDmg).coerceAtLeast(0f)
                        spawnSparksLocked(boss.x, boss.y, "#FFDF00")
                        if (boss.hp <= 0f) {
                            defeatTitanBossLocked(p, soundCues)
                        }
                    }
                }

                when (newTier) {
                    BeyBladeGiftTier.TIER_1_MINI -> {
                        p.shieldDurationSec = 6.0f
                    }
                    BeyBladeGiftTier.TIER_2_TITAN -> {
                        p.shieldDurationSec = 10.0f
                        p.shockwaveUntilMs = System.currentTimeMillis() + 1500L
                        applyShockwaveLocked(p, pushRadius = 0.65f, pushForce = 1.4f * cur.bounceMultiplier)
                        ultAction = { triggerChainLightning(callerName = cleanName) }
                    }
                    BeyBladeGiftTier.TIER_3_THUNDER -> {
                        p.shieldDurationSec = 16.0f
                        p.shockwaveUntilMs = System.currentTimeMillis() + 2200L
                        applyShockwaveLocked(p, pushRadius = 0.85f, pushForce = 2.0f * cur.bounceMultiplier)
                        ultAction = { triggerBlizzard(durationMs = 3500L, callerName = cleanName) }
                    }
                    BeyBladeGiftTier.TIER_4_GALAXY -> {
                        p.shieldDurationSec = 25.0f
                        p.shockwaveUntilMs = System.currentTimeMillis() + 3500L
                        applyShockwaveLocked(p, pushRadius = 1.2f, pushForce = 2.8f * cur.bounceMultiplier)
                        ultAction = { triggerMeteorStrike(callerName = cleanName) }
                    }
                    else -> {}
                }

                spawnSparksLocked(p.x, p.y, newTier.auraColorHex, count = 10, kind = ParticleKind.LIGHTNING)
                spawnFloatingTextLocked(p.x, p.y - p.effectiveRadius, newTier.title.uppercase(), newTier.auraColorHex, isCrit = true)
            }

            avatarTarget?.let { fetchAvatarAsync(it) }
            for (cue in soundCues) {
                try { cue() } catch (_: Throwable) {}
            }
            ultAction?.invoke()

            _state.value = _state.value.copy(
                lastEventBanner = "🎁 ${cleanName} kirim ${giftName} x${count} (${totalCoins} Koin) -> ${newTier.title} AKTIF!",
                lastEventType = "GIFT"
            )
            triggerStateBroadcast()
        }
    }

    private fun applyShockwaveLocked(centerPlayer: BeyBladePlayer, pushRadius: Float, pushForce: Float) {
        val now = System.currentTimeMillis()
        val opponents = playersList.filter { it.id != centerPlayer.id && !it.isEliminated }
        for (opp in opponents) {
            val dx = opp.x - centerPlayer.x
            val dy = opp.y - centerPlayer.y
            val dist = hypot(dx, dy)
            if (dist < pushRadius && dist > 0.001f) {
                val nx = dx / dist
                val ny = dy / dist
                val falloff = (1f - (dist / pushRadius)).coerceIn(0.2f, 1f)
                opp.vx += nx * pushForce * falloff
                opp.vy += ny * pushForce * falloff
                opp.hp = (opp.hp - 18f * _state.value.damageMultiplier * falloff).coerceAtLeast(0f)
                opp.lastHitByPlayerId = centerPlayer.id
                opp.lastHitTimeMs = now
            }
        }
    }

    /**
     * Harus dipanggil dalam `synchronized(stateLock)`!
     */
    private fun spawnOrGetPlayerLocked(name: String, avatarUrl: String, actionText: String): BeyBladePlayer {
        val cur = _state.value
        val existing = playersList.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            if (existing.isEliminated) {
                existing.isEliminated = false
                existing.eliminatedReason = ""
                existing.maxHp = existing.tier.baseMaxHp
                existing.hp = existing.maxHp
                existing.rpm = 1500f + (existing.tier.tierLevel * 300f)
                existing.maxLikeSizeCap = cur.maxLikeSizeBonus
                resetPlayerPositionLocked(existing)
            }
            existing.lastAction = actionText
            return existing
        }

        // Kelola kapasitas maksimal pemain dengan aman (Proteksi mutlak pemain Gifter)
        val maxCap = cur.maxActivePlayers.coerceIn(5, 40)
        while (playersList.size >= maxCap) {
            // Prioritas 1: Hapus bot tereliminasi
            val botEliminated = playersList.firstOrNull { it.isEliminated && it.name.startsWith("Bot") }
            if (botEliminated != null) {
                playersList.remove(botEliminated)
                continue
            }
            // Prioritas 2: Hapus pemain gratisan (0 koin) tereliminasi
            val freeEliminated = playersList.firstOrNull { it.isEliminated && it.coinsContributed == 0L }
            if (freeEliminated != null) {
                playersList.remove(freeEliminated)
                continue
            }
            // Prioritas 3: Hapus bot aktif
            val botActive = playersList.firstOrNull { it.name.startsWith("Bot") }
            if (botActive != null) {
                playersList.remove(botActive)
                continue
            }
            // Prioritas 4: Hapus pemain non-gifter dengan likes terendah
            val freeActive = playersList.filter { it.coinsContributed == 0L }.minByOrNull { it.likesContributed }
            if (freeActive != null) {
                playersList.remove(freeActive)
                continue
            }
            // Jika semua di arena sudah menyumbang gift koin: buang kontributor terendah
            val candidate = playersList.minByOrNull { it.coinsContributed }
            if (candidate != null) {
                playersList.remove(candidate)
            } else {
                break
            }
        }

        val color = COLOR_PALETTE[playersList.size % COLOR_PALETTE.size]
        val cachedB64 = avatarBase64Cache[name] ?: ""
        val newPlayer = BeyBladePlayer(
            id = "user_${name.hashCode()}",
            name = name,
            avatarUrl = avatarUrl,
            avatarBase64 = cachedB64,
            colorHex = color,
            lastAction = actionText,
            maxLikeSizeCap = cur.maxLikeSizeBonus
        )
        newPlayer.avatarBitmap = BeyBladeAvatarHelper.getCircularAvatar(
            name = name,
            avatarBase64 = cachedB64,
            colorHex = color
        )
        resetPlayerPositionLocked(newPlayer)
        playersList.add(newPlayer)
        return newPlayer
    }

    private fun fetchAvatarAsync(player: BeyBladePlayer) {
        val cleanUrl = player.avatarUrl.trim()
        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) return

        scope.launch(Dispatchers.IO) {
            try {
                val b64 = ViewerAvatarCache.getOrFetchCompactBase64(cleanUrl)
                if (b64.isNotBlank()) {
                    avatarBase64Cache[player.name] = b64
                    val bmp = BeyBladeAvatarHelper.getCircularAvatar(
                        name = player.name,
                        avatarBase64 = b64,
                        colorHex = player.colorHex
                    )
                    synchronized(stateLock) {
                        player.avatarBase64 = b64
                        player.avatarBitmap = bmp
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun resetPlayerPositionLocked(p: BeyBladePlayer) {
        val curW = _state.value.arenaWidthScale
        val curH = _state.value.arenaHeightScale
        if (_state.value.arenaShape.equals("RECTANGLE", ignoreCase = true)) {
            p.x = Random.nextDouble(-0.70, 0.70).toFloat() * curW
            p.y = Random.nextDouble(-0.70, 0.70).toFloat() * curH
            p.vx = -p.x * 0.15f + Random.nextDouble(-0.08, 0.08).toFloat()
            p.vy = -p.y * 0.15f + Random.nextDouble(-0.08, 0.08).toFloat()
        } else {
            val angle = Random.nextDouble(0.0, 2.0 * Math.PI).toFloat()
            val radius = Random.nextDouble(0.35, 0.75).toFloat()
            p.x = cos(angle) * radius * curW
            p.y = sin(angle) * radius * curH
            p.vx = -cos(angle) * 0.15f + Random.nextDouble(-0.08, 0.08).toFloat()
            p.vy = -sin(angle) * 0.15f + Random.nextDouble(-0.08, 0.08).toFloat()
        }
    }

    private fun spawnSparksLocked(
        x: Float,
        y: Float,
        colorHex: String,
        count: Int = 5,
        kind: ParticleKind = ParticleKind.SPARK
    ) {
        val maxSpeed = if (kind == ParticleKind.SHATTER) 1.35f else 0.85f
        val life = if (kind == ParticleKind.SHATTER) 0.55f else 0.35f
        val size = if (kind == ParticleKind.SHATTER) 5.0f else 3.2f
        for (k in 0 until count) {
            val angle = Random.nextDouble(0.0, 2.0 * Math.PI).toFloat()
            val speed = Random.nextDouble(0.25, maxSpeed.toDouble()).toFloat()
            particlesList.add(
                SparkParticle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    colorHex = colorHex,
                    lifeRemainingSec = life,
                    maxLifeSec = life,
                    sizeDp = size,
                    kind = kind
                )
            )
        }
        while (particlesList.size > 80) {
            particlesList.removeAt(0)
        }
    }

    private fun spawnFloatingTextLocked(
        x: Float,
        y: Float,
        text: String,
        colorHex: String,
        isCrit: Boolean = false
    ) {
        floatingTextsList.add(
            FloatingDamageText(
                id = System.currentTimeMillis() + Random.nextLong(10000),
                x = x + Random.nextDouble(-0.04, 0.04).toFloat(),
                y = y,
                text = text,
                colorHex = colorHex,
                isCrit = isCrit
            )
        )
        while (floatingTextsList.size > 20) {
            floatingTextsList.removeAt(0)
        }
    }

    // =========================================================================
    // SIMULATOR MANDIRI & KONTROL HOST
    // =========================================================================

    fun spawnBot(name: String, tier: BeyBladeGiftTier = BeyBladeGiftTier.TIER_0_FREE) {
        scope.launch {
            synchronized(stateLock) {
                val p = spawnOrGetPlayerLocked(name, avatarUrl = "", actionText = "Bot Arena Masuk")
                p.tier = tier
                if (tier != BeyBladeGiftTier.TIER_0_FREE) {
                    p.hp = p.maxHp
                    p.shieldDurationSec = 5f
                }
            }
            publishStateUpdate()
            triggerStateBroadcast()
        }
    }

    fun spawnInitialBotsIfEmpty() {
        ensureBotsAtStartIfNeeded()
    }

    private fun ensureBotsAtStartIfNeeded() {
        val cur = _state.value
        if (!cur.autoSpawnBotsEnabled) return
        var addedAny = false
        synchronized(stateLock) {
            if (playersList.isEmpty()) {
                val target = cur.minBotTargetCount.coerceIn(2, 12)
                for (i in 0 until target) {
                    val botName = DEFAULT_BOT_NAMES.getOrElse(i) { "Bot_${i + 1}" }
                    spawnOrGetPlayerLocked(botName, avatarUrl = "", actionText = "Bot Arena Masuk")
                }
                addedAny = true
            }
        }
        if (addedAny) {
            publishStateUpdate()
            triggerStateBroadcast()
        }
    }

    fun resetRound() {
        resetRoundInternal(nextRound = _state.value.currentRound + 1)
    }

    private fun resetRoundInternal(nextRound: Int) {
        val cur = _state.value
        val snapshotPlayers: List<BeyBladePlayer>
        synchronized(stateLock) {
            eliminatedList.clear()
            particlesList.clear()
            floatingTextsList.clear()
            killFeedList.clear()
            meteorsList.clear()
            titanBoss = null
            currentShrinkFactor = 1.0f

            val defendingName = cur.lastChampion?.name ?: cur.defendingChampionName
            for (p in playersList) {
                p.isEliminated = false
                p.eliminatedReason = ""
                p.isDefendingChampion = (defendingName != null && p.name.equals(defendingName, ignoreCase = true))
                // Pertahankan darah tebal sesuai tier gift masing-masing
                p.maxHp = p.tier.baseMaxHp
                p.hp = p.maxHp
                p.rpm = 1500f + (p.tier.tierLevel * 300f)
                // Beri perisai pelindung awal ronde untuk pemain yang pernah gift atau juara bertahan
                val isVip = p.tier.tierLevel > 0 || p.isDefendingChampion
                p.shieldDurationSec = if (isVip) (4.0f + p.tier.tierLevel * 2f) else 0f
                p.turboDashUntilMs = 0L
                p.shockwaveUntilMs = 0L
                p.frozenUntilMs = 0L
                p.hitFlashUntilMs = 0L
                p.likeSizeBonus = 0f
                p.maxLikeSizeCap = cur.maxLikeSizeBonus
                resetPlayerPositionLocked(p)
            }

            // Pastikan arena langsung terisi minimal bot jika jumlah pemain kurang
            if (cur.autoSpawnBotsEnabled) {
                val minTarget = cur.minBotTargetCount.coerceIn(2, 12)
                var idx = 0
                while (playersList.size < minTarget && idx < DEFAULT_BOT_NAMES.size + 12) {
                    val botName = DEFAULT_BOT_NAMES.getOrNull(idx) ?: "BotLive_${idx + 1}"
                    if (playersList.none { it.name.equals(botName, ignoreCase = true) }) {
                        spawnOrGetPlayerLocked(botName, avatarUrl = "", actionText = "Bot Ronde $nextRound")
                    }
                    idx++
                }
            }

            snapshotPlayers = playersList.toList()
        }

        prefs.edit().putInt("current_round", nextRound).apply()
        lastStatePublishMs = System.currentTimeMillis()
        val gifterCount = snapshotPlayers.count { it.tier.tierLevel > 0 }
        val defendingName = _state.value.lastChampion?.name ?: _state.value.defendingChampionName
        val startBanner = if (defendingName != null) {
            "🔥 Ronde ${nextRound} Dimulai! Juara Bertahan 👑 ${defendingName} & ${snapshotPlayers.size} Petarung Siap Duel!"
        } else if (gifterCount > 0) {
            "🔥 Ronde ${nextRound} Dimulai! ${snapshotPlayers.size} Petarung (${gifterCount} Gifter Bertahan) Siap Tempur!"
        } else {
            "🔥 Ronde ${nextRound} Dimulai! ${snapshotPlayers.size} Petarung Bertahan Siap Tempur!"
        }
        _state.value = _state.value.copy(
            currentRound = nextRound,
            players = snapshotPlayers,
            eliminatedPlayers = emptyList(),
            defendingChampionName = defendingName,
            trapdoors = getTrapdoorsSnapshot(),
            activeMeteors = emptyList(),
            killFeed = emptyList(),
            topHunterId = null,
            totalEliminationsInRound = 0,
            roundStartedAtMs = System.currentTimeMillis(),
            shrinkFactor = 1.0f,
            isBossModeActive = false,
            bossPlayer = null,
            blizzardUntilMs = 0L,
            lastChampion = null,
            isDrawEnding = false,
            celebrationUntilMs = 0L,
            screenShakeIntensity = 0f,
            lastEventBanner = startBanner,
            lastEventType = "ROUND_START"
        )
        playAnnouncerVoice("Round $nextRound! Let the battle begin!")
        triggerStateBroadcast()
    }

    fun clearArena() {
        synchronized(stateLock) {
            playersList.clear()
            eliminatedList.clear()
            particlesList.clear()
            floatingTextsList.clear()
            killFeedList.clear()
            meteorsList.clear()
            titanBoss = null
            currentShrinkFactor = 1.0f
        }
        _state.value = _state.value.copy(
            players = emptyList(),
            eliminatedPlayers = emptyList(),
            activeMeteors = emptyList(),
            killFeed = emptyList(),
            topHunterId = null,
            shrinkFactor = 1.0f,
            totalEliminationsInRound = 0,
            lastChampion = null,
            isDrawEnding = false,
            celebrationUntilMs = 0L,
            isBossModeActive = false,
            bossPlayer = null,
            lastEventBanner = "Arena telah dibersihkan oleh Host.",
            lastEventType = "IDLE"
        )
        triggerStateBroadcast()
    }

    fun triggerMeteorRain(count: Int = 4) {
        scope.launch {
            synchronized(stateLock) {
                for (i in 0 until count) {
                    val targetX = Random.nextDouble(-0.7, 0.7).toFloat()
                    val targetY = Random.nextDouble(-0.7, 0.7).toFloat()
                    meteorsList.add(
                        ActiveMeteor(
                            id = System.currentTimeMillis() + i * 180L,
                            targetX = targetX,
                            targetY = targetY,
                            altitude = 1.0f + (i * 0.28f)
                        )
                    )
                }
            }
            playAnnouncerVoice("Meteor Strike incoming!")
            _state.value = _state.value.copy(
                lastEventBanner = "☄️ HUJAN METEOR DITURUNKAN OLEH HOST!",
                lastEventType = "METEOR",
                activeMeteors = getMeteorsSnapshot()
            )
            triggerStateBroadcast()
        }
    }

    fun triggerHostBlizzard() {
        triggerBlizzard(durationMs = 5000L, callerName = "Host")
    }

    fun toggleLeaderboardOverlay() {
        _state.value = _state.value.copy(showLeaderboardOverlay = !_state.value.showLeaderboardOverlay)
    }

    private fun publishStateUpdate() {
        val snapPlayers: List<BeyBladePlayer>
        val snapEliminated: List<BeyBladePlayer>
        val snapTrapdoors: List<TrapdoorZone>
        val snapMeteors: List<ActiveMeteor>
        val snapKillFeed: List<KillFeedEntry>
        val topId: String?
        val snapShrink: Float
        val snapBoss: BeyBladePlayer?
        val snapLaser: Float
        val snapShake: Float

        synchronized(stateLock) {
            snapPlayers = playersList.toList()
            snapEliminated = eliminatedList.toList()
            snapTrapdoors = trapdoorsList.toList()
            snapMeteors = meteorsList.toList()
            snapKillFeed = killFeedList.toList()
            topId = snapPlayers.filter { !it.isEliminated && it.koCount > 0 }.maxByOrNull { it.koCount }?.id
            snapShrink = currentShrinkFactor
            snapBoss = titanBoss
            snapLaser = laserAngleDeg
            snapShake = screenShake
        }

        val isFinalClash = snapPlayers.filter { !it.isEliminated }.let {
            it.size == 2 && it[0].hp < 40f && it[1].hp < 40f && snapBoss == null
        }

        _state.value = _state.value.copy(
            players = snapPlayers,
            eliminatedPlayers = snapEliminated,
            trapdoors = snapTrapdoors,
            activeMeteors = snapMeteors,
            killFeed = snapKillFeed,
            topHunterId = topId,
            shrinkFactor = snapShrink,
            bossPlayer = snapBoss,
            isBossModeActive = snapBoss != null,
            laserAngleDeg = snapLaser,
            screenShakeIntensity = snapShake,
            isFinalClashActive = isFinalClash
        )
    }

    // =========================================================================
    // BROADCAST KE VCAM OVERLAY
    // =========================================================================

    private fun triggerStateBroadcast() {
        if (!_state.value.isEnabled) return
        if (isPendingBroadcast) return
        val now = System.currentTimeMillis()
        val elapsed = now - lastBroadcastTime
        if (elapsed >= 500L) {
            doBroadcast()
        } else {
            isPendingBroadcast = true
            broadcastJob?.cancel()
            broadcastJob = scope.launch {
                delay(500L - elapsed)
                doBroadcast()
            }
        }
    }

    private fun doBroadcast() {
        isPendingBroadcast = false
        lastBroadcastTime = System.currentTimeMillis()
        val cur = _state.value

        val champName = cur.lastChampion?.name ?: "-"
        val hunter = cur.topKoHunter
        val hunterText = if (hunter != null && hunter.koCount > 0) "${hunter.name} (${hunter.koCount} KO)" else "-"
        val bannerPayload = "BEYBATTLE|${cur.activePlayersCount}|${champName}|${cur.lastEventBanner}|${cur.currentRound}|${hunterText}"

        try {
            if (cur.isEnabled) {
                OverlayBannerBroadcaster.broadcastShow(
                    context = context,
                    senderName = "TUG_OF_WAR",
                    content = bannerPayload,
                    isGift = false,
                    bannerType = "TUG_OF_WAR",
                    iconMode = "NONE",
                    customPrefix = "",
                    formatMode = "PREFIX_AND_COMMENT",
                    durationMs = 86400000L,
                    posXpct = cur.posXpct,
                    posYpct = cur.posYpct,
                    rotation = cur.overlayRotation,
                    scale = cur.overlayScale,
                    isMirrored = cur.isMirrored,
                    entryDirection = "FADE",
                    capsuleAlpha = 1.0f
                )
            } else {
                OverlayBannerBroadcaster.broadcastHide(context, bannerType = "TUG_OF_WAR")
            }
        } catch (_: Throwable) {}
    }
}
