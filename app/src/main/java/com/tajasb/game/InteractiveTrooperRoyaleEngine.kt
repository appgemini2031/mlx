package com.tajasb.game

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.tajasb.eventrelay.LiveEventReceiver
import com.tajasb.eventrelay.ViewerAvatarCache
import com.tajasb.notif.OverlayBannerBroadcaster
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * InteractiveTrooperRoyaleEngine:
 * Engine Fisika 2D 60 FPS untuk Game Interaktif:
 * "🎖️ Mini Trooper Royale (Perang Prajurit Chibi TikTok Live)".
 *
 * Fitur Utama:
 * 1. Pertempuran Battle Royale: Prajurit Chibi saling tembak hingga sisa 1 juara (Last Man Standing).
 * 2. Pancingan Gratisan: Tap-tap Like = Tembak peluru standar, Chat = Manuver Dodge Roll & burst 3 peluru.
 * 3. Saweran Gift Berjenjang: Memicu Shotgun, Bazooka Rocket (Splash Damage), Baja Tank, & Serangan Udara Airstrike.
 * 4. Papan Top 5 Ultra-Fleksibel: Posisi (X/Y), ukuran skala, visibilitas foto profil, dan nama dapat digeser & disetel bebas oleh host.
 * 5. Safe Zone Menyusut: Lingkaran gas memaksa duel klimaks seru dengan durasi ronde 90-120 detik.
 */
class InteractiveTrooperRoyaleEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TrooperRoyaleEngine"
        private const val PREFS_NAME = "trooper_royale_storage_v1"
        private const val MAX_PLAYERS = 30
        private const val CELEBRATION_DURATION_MS = 8000L

        private val COLOR_PALETTE = listOf(
            "#06B6D4", "#EF4444", "#10B981", "#F59E0B",
            "#8B5CF6", "#EC4899", "#3B82F6", "#14B8A6"
        )

        private val BOT_NAMES = listOf(
            "Rambo_Kecil", "Sniper_Santai", "Komando_Live", "Kapten_Chibi",
            "Jagoan_Neon", "Prajurit_Baja", "Sultan_Keren", "Bocil_Bazooka"
        )

        @Volatile
        private var instance: InteractiveTrooperRoyaleEngine? = null

        fun peekInstance(): InteractiveTrooperRoyaleEngine? = instance

        fun getInstance(context: Context): InteractiveTrooperRoyaleEngine {
            return instance ?: synchronized(this) {
                instance ?: InteractiveTrooperRoyaleEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(TrooperRoyaleState())
    val state: StateFlow<TrooperRoyaleState> = _state.asStateFlow()

    private val playersList = CopyOnWriteArrayList<TrooperPlayer>()
    private val bulletsList = CopyOnWriteArrayList<TrooperBullet>()
    private val explosionsList = CopyOnWriteArrayList<TrooperExplosion>()
    private val avatarBase64Cache = ConcurrentHashMap<String, String>()

    private val bulletIdGenerator = AtomicLong(1L)
    private val explosionIdGenerator = AtomicLong(1L)
    private val sessionGeneration = AtomicLong(0L)

    private var toneGenerator: ToneGenerator? = null
    private var gameJob: Job? = null
    private var broadcastJob: Job? = null
    private var lastBroadcastTime = 0L
    private var lastStatePublishMs = 0L
    private var lastShotToneMs = 0L

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
        } catch (_: Throwable) {}

        loadPersistedConfig()
        startLoop()
    }

    private fun loadPersistedConfig() {
        val posX = prefs.getFloat("top5_pos_x", 0.03f)
        val posY = prefs.getFloat("top5_pos_y", 0.12f)
        val scale = prefs.getFloat("top5_scale", 1.0f)
        val isHorizontal = prefs.getBoolean("top5_horizontal", false)
        val showAvatar = prefs.getBoolean("top5_show_avatar", true)
        val showName = prefs.getBoolean("top5_show_name", true)
        val showKills = prefs.getBoolean("top5_show_kills", true)
        val showScore = prefs.getBoolean("top5_show_score", true)
        val isVisible = prefs.getBoolean("top5_visible", true)
        val sound = prefs.getBoolean("sound_enabled", true)

        val cfg = Top5DisplayConfig(
            isVisible = isVisible,
            posXpct = posX,
            posYpct = posY,
            scale = scale,
            isHorizontal = isHorizontal,
            showAvatar = showAvatar,
            showName = showName,
            showKills = showKills,
            showScore = showScore
        )

        _state.value = _state.value.copy(
            top5Config = cfg,
            soundEnabled = sound
        )
    }

    private fun savePersistedConfig(cfg: Top5DisplayConfig) {
        prefs.edit().apply {
            putFloat("top5_pos_x", cfg.posXpct)
            putFloat("top5_pos_y", cfg.posYpct)
            putFloat("top5_scale", cfg.scale)
            putBoolean("top5_horizontal", cfg.isHorizontal)
            putBoolean("top5_show_avatar", cfg.showAvatar)
            putBoolean("top5_show_name", cfg.showName)
            putBoolean("top5_show_kills", cfg.showKills)
            putBoolean("top5_show_score", cfg.showScore)
            putBoolean("top5_visible", cfg.isVisible)
            apply()
        }
    }

    fun getPlayersSnapshot(): List<TrooperPlayer> = playersList.toList()
    fun getBulletsSnapshot(): List<TrooperBullet> = bulletsList.toList()
    fun getExplosionsSnapshot(): List<TrooperExplosion> = explosionsList.toList()

    // =========================================================================
    // GAME LOOP 60 FPS
    // =========================================================================

    private fun startLoop() {
        gameJob?.cancel()
        gameJob = scope.launch {
            var lastTickTime = System.currentTimeMillis()
            var roundTimerAccumulator = 0L

            while (isActive) {
                val now = System.currentTimeMillis()
                val deltaMs = (now - lastTickTime).coerceIn(5L, 50L)
                lastTickTime = now

                val currentState = _state.value
                if (currentState.isGameActive && currentState.isGameRunning) {
                    roundTimerAccumulator += deltaMs
                    if (roundTimerAccumulator >= 1000L) {
                        roundTimerAccumulator = 0L
                        tickRoundTimer()
                    }

                    updateSafeZone(deltaMs)
                    updateBullets(deltaMs)
                    updatePlayersPhysics(deltaMs, now)
                    updateExplosions(deltaMs)
                    checkWinCondition(now)
                }

                if (now - lastStatePublishMs >= 33L) {
                    lastStatePublishMs = now
                    publishStateSnapshot()
                }

                delay(16L)
            }
        }
    }

    private fun tickRoundTimer() {
        val s = _state.value
        val newTime = (s.roundTimeLeftSec - 1).coerceAtLeast(0)
        _state.value = s.copy(roundTimeLeftSec = newTime)

        if (newTime <= 0) {
            handleTimeUpSuddenDeath()
        }
    }

    private fun updateSafeZone(deltaMs: Long) {
        val s = _state.value
        val shrinkTarget = 0.14f
        val currentRadius = s.safeZoneRadius
        if (currentRadius > shrinkTarget) {
            val shrinkRate = (0.48f - shrinkTarget) / 100f // Menyusut dalam 100 detik
            val deltaSec = deltaMs / 1000f
            val nextRadius = (currentRadius - shrinkRate * deltaSec).coerceAtLeast(shrinkTarget)
            _state.value = s.copy(safeZoneRadius = nextRadius)
        }
    }

    private fun updateBullets(deltaMs: Long) {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<TrooperBullet>()
        val deltaSec = deltaMs / 1000f

        for (bullet in bulletsList) {
            bullet.x += bullet.vx * (deltaSec * 60f)
            bullet.y += bullet.vy * (deltaSec * 60f)

            // Cek keluar batas arena
            if (bullet.x < 0.02f || bullet.x > 0.98f || bullet.y < 0.02f || bullet.y > 0.98f || now - bullet.createdAtMs > 4000L) {
                toRemove.add(bullet)
                continue
            }

            // Cek tabrakan dengan prajurit lawan
            val hitRadius = if (bullet.isRocket) 0.08f else 0.038f
            for (player in playersList) {
                if (!player.isAlive || player.id == bullet.ownerId) continue

                val dist = hypot(bullet.x - player.x, bullet.y - player.y)
                if (dist < hitRadius) {
                    toRemove.add(bullet)
                    applyBulletDamage(bullet, player)
                    break
                }
            }
        }

        if (toRemove.isNotEmpty()) {
            bulletsList.removeAll(toRemove)
        }
    }

    private fun applyBulletDamage(bullet: TrooperBullet, victim: TrooperPlayer) {
        val now = System.currentTimeMillis()
        if (victim.isDodgeRolling && now < victim.dodgeRollUntilMs) {
            // Hindar sukses!
            spawnExplosion(victim.x, victim.y, 0.03f, "#38BDF8", "MISS!")
            return
        }

        var dmg = bullet.damage
        if (victim.shieldHp > 0f) {
            if (victim.shieldHp >= dmg) {
                victim.shieldHp -= dmg
                dmg = 0f
            } else {
                dmg -= victim.shieldHp
                victim.shieldHp = 0f
            }
        }

        victim.hp = (victim.hp - dmg).coerceAtLeast(0f)

        // Efek ledakan visual
        val expColor = if (bullet.isRocket) "#EF4444" else bullet.colorHex
        val expLabel = if (dmg > 50f) "BOOM! -${dmg.toInt()}" else "-${dmg.toInt()}"
        spawnExplosion(victim.x, victim.y, if (bullet.isRocket) 0.07f else 0.035f, expColor, expLabel)

        if (victim.hp <= 0f && !victim.isEliminated) {
            victim.isEliminated = true
            victim.eliminatedReason = "Eliminasi oleh ${bullet.ownerName}"

            // Beri kredit kill ke penembak
            val shooter = playersList.firstOrNull { it.id == bullet.ownerId }
            if (shooter != null) {
                shooter.kills += 1
                shooter.score += 250L
                shooter.lastActionText = "Mengeliminasi ${victim.name}!"
            }

            spawnExplosion(victim.x, victim.y, 0.09f, "#EF4444", "K.O! ${victim.name}")
            playToneSound(ToneGenerator.TONE_PROP_BEEP2)
        }
    }

    private fun updatePlayersPhysics(deltaMs: Long, now: Long) {
        val s = _state.value
        val safeR = s.safeZoneRadius
        val safeCx = s.safeZoneCenterX
        val safeCy = s.safeZoneCenterY
        val deltaSec = deltaMs / 1000f

        val aliveList = playersList.filter { it.isAlive }

        for (player in aliveList) {
            // Pergerakan cerdas: bergerak perlahan ke arah dalam safe zone jika di luar
            val distToCenter = hypot(player.x - safeCx, player.y - safeCy)
            if (distToCenter > safeR) {
                // Di luar safe zone -> kena damage racun gas berkala
                player.hp = (player.hp - (4f * deltaSec)).coerceAtLeast(0f)
                if (player.hp <= 0f) {
                    player.isEliminated = true
                    player.eliminatedReason = "Terjebak gas racun"
                    spawnExplosion(player.x, player.y, 0.05f, "#A855F7", "GAS POISON!")
                    continue
                }

                // Dorong kembali ke safe zone
                val angleBack = atan2(safeCy - player.y, safeCx - player.x)
                player.vx += cos(angleBack) * 0.005f
                player.vy += sin(angleBack) * 0.005f
            } else if (player.isBot) {
                // Bot berkeliaran acak
                if (Random.nextFloat() < 0.03f) {
                    val randAngle = Random.nextFloat() * 6.283f
                    player.vx = cos(randAngle) * 0.008f
                    player.vy = sin(randAngle) * 0.008f
                }
            }

            // Perlambatan gesekan
            player.vx *= 0.92f
            player.vy *= 0.92f

            player.x = (player.x + player.vx).coerceIn(0.05f, 0.95f)
            player.y = (player.y + player.vy).coerceIn(0.05f, 0.95f)

            // Hadapkan moncong senjata ke musuh terdekat
            val nearestEnemy = aliveList.filter { it.id != player.id }
                .minByOrNull { hypot(it.x - player.x, it.y - player.y) }

            if (nearestEnemy != null) {
                val dx = nearestEnemy.x - player.x
                val dy = nearestEnemy.y - player.y
                player.angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

                // Bot sesekali menembak
                if (player.isBot && now - player.lastShotTimeMs > 2500L && Random.nextFloat() < 0.05f) {
                    player.lastShotTimeMs = now
                    fireBulletFromPlayer(player, nearestEnemy.x, nearestEnemy.y)
                }
            }
        }
    }

    private fun updateExplosions(deltaMs: Long) {
        val deltaSec = deltaMs / 1000f
        val toRemove = mutableListOf<TrooperExplosion>()

        for (exp in explosionsList) {
            exp.currentRadius += exp.maxRadius * (deltaSec * 3f)
            exp.alpha -= (deltaSec * 2.2f)

            if (exp.alpha <= 0.05f || exp.currentRadius >= exp.maxRadius) {
                toRemove.add(exp)
            }
        }

        if (toRemove.isNotEmpty()) {
            explosionsList.removeAll(toRemove)
        }
    }

    private fun checkWinCondition(now: Long) {
        val s = _state.value
        if (s.isCelebratingVictory) return

        val alive = playersList.filter { it.isAlive }
        if (playersList.size >= 2 && alive.size == 1) {
            val winner = alive.first()
            _state.value = s.copy(
                winnerPlayerName = winner.name,
                winnerAvatarBase64 = winner.avatarBase64,
                winnerKills = winner.kills,
                winnerWeaponTitle = winner.weaponTier.title,
                celebrationUntilMs = now + CELEBRATION_DURATION_MS,
                lastEventMessage = "🏆 JUARA 1: ${winner.name} (${winner.kills} KILLS)!"
            )

            winner.score += 1000L
            spawnExplosion(winner.x, winner.y, 0.15f, "#FACC15", "VICTORY ROYALE!")
            playToneSound(ToneGenerator.TONE_CDMA_HIGH_L)
            triggerOverlayBroadcast()
        }
    }

    private fun handleTimeUpSuddenDeath() {
        val alive = playersList.filter { it.isAlive }
        if (alive.isNotEmpty()) {
            val best = alive.maxByOrNull { it.hp + (it.kills * 100f) } ?: alive.first()
            _state.value = _state.value.copy(
                winnerPlayerName = best.name,
                winnerAvatarBase64 = best.avatarBase64,
                winnerKills = best.kills,
                winnerWeaponTitle = best.weaponTier.title,
                celebrationUntilMs = System.currentTimeMillis() + CELEBRATION_DURATION_MS,
                lastEventMessage = "⏱️ WAKTU HABIS! JUARA JURI: ${best.name}!"
            )
            triggerOverlayBroadcast()
        }
    }

    private fun publishStateSnapshot() {
        val all = playersList.toList()
        val aliveCount = all.count { it.isAlive }

        // Hitung Top 5
        val top5 = all.sortedWith(
            compareByDescending<TrooperPlayer> { it.kills }
                .thenByDescending { it.score }
                .thenByDescending { it.coinsContributed }
        ).take(5).mapIndexed { idx, p ->
            Top5LeaderboardEntry(
                rank = idx + 1,
                name = p.name,
                avatarBase64 = p.avatarBase64,
                kills = p.kills,
                score = p.score,
                weaponTitle = p.weaponTier.title,
                colorHex = p.colorHex,
                isAlive = p.isAlive
            )
        }

        _state.value = _state.value.copy(
            totalAliveCount = aliveCount,
            totalPlayerCount = all.size,
            top5List = top5
        )
    }

    private fun spawnExplosion(x: Float, y: Float, maxR: Float, colorHex: String, label: String) {
        val exp = TrooperExplosion(
            id = explosionIdGenerator.getAndIncrement(),
            x = x,
            y = y,
            maxRadius = maxR,
            colorHex = colorHex,
            labelText = label
        )
        explosionsList.add(exp)
    }

    private fun playToneSound(toneType: Int) {
        val s = _state.value
        if (!s.isGameActive || !s.soundEnabled) return
        try {
            toneGenerator?.startTone(toneType, 120)
        } catch (_: Throwable) {}
    }

    private fun stopAllToneSounds() {
        try {
            toneGenerator?.stopTone()
        } catch (_: Throwable) {}
    }

    // =========================================================================
    // TEMBAK & AMUNISI
    // =========================================================================

    private fun fireBulletFromPlayer(player: TrooperPlayer, targetX: Float, targetY: Float) {
        val dx = targetX - player.x
        val dy = targetY - player.y
        val dist = hypot(dx, dy).coerceAtLeast(0.001f)
        val dirX = dx / dist
        val dirY = dy / dist

        val weapon = player.weaponTier
        val speed = weapon.bulletSpeed

        if (weapon == TrooperWeaponTier.TIER_1_SHOTGUN) {
            // Shotgun: 3 sebaran peluru
            for (angleOffset in listOf(-0.18f, 0f, 0.18f)) {
                val cosO = cos(angleOffset)
                val sinO = sin(angleOffset)
                val sVx = (dirX * cosO - dirY * sinO) * speed
                val sVy = (dirX * sinO + dirY * cosO) * speed

                bulletsList.add(
                    TrooperBullet(
                        id = bulletIdGenerator.getAndIncrement(),
                        ownerId = player.id,
                        ownerName = player.name,
                        x = player.x + (dirX * 0.035f),
                        y = player.y + (dirY * 0.035f),
                        vx = sVx,
                        vy = sVy,
                        damage = weapon.damage,
                        colorHex = weapon.bulletColorHex
                    )
                )
            }
        } else if (weapon == TrooperWeaponTier.TIER_2_BAZOOKA) {
            // Bazooka: Roket berapi cepat
            bulletsList.add(
                TrooperBullet(
                    id = bulletIdGenerator.getAndIncrement(),
                    ownerId = player.id,
                    ownerName = player.name,
                    x = player.x + (dirX * 0.04f),
                    y = player.y + (dirY * 0.04f),
                    vx = dirX * speed,
                    vy = dirY * speed,
                    damage = weapon.damage,
                    colorHex = weapon.bulletColorHex,
                    radius = 0.022f,
                    isRocket = true
                )
            )
        } else {
            // Pistol / Tank / Airstrike
            bulletsList.add(
                TrooperBullet(
                    id = bulletIdGenerator.getAndIncrement(),
                    ownerId = player.id,
                    ownerName = player.name,
                    x = player.x + (dirX * 0.035f),
                    y = player.y + (dirY * 0.035f),
                    vx = dirX * speed,
                    vy = dirY * speed,
                    damage = weapon.damage,
                    colorHex = weapon.bulletColorHex,
                    radius = if (weapon == TrooperWeaponTier.TIER_3_TANK) 0.024f else 0.014f,
                    isAirstrike = weapon == TrooperWeaponTier.TIER_4_AIRSTRIKE
                )
            )
        }

        val now = System.currentTimeMillis()
        if (now - lastShotToneMs > 150L) {
            lastShotToneMs = now
            playToneSound(ToneGenerator.TONE_PROP_PROMPT)
        }
    }

    // =========================================================================
    // INGESTION EVENT LIVE TIKTOK (LIKE, CHAT, GIFT, JOIN)
    // =========================================================================

    fun onLikeEvent(senderName: String, likeCount: Int, avatarUrl: String) {
        if (!_state.value.isGameActive) return
        val cleanName = senderName.trim().ifBlank { "Prajurit_Live" }
        val player = getOrCreatePlayer(cleanName, avatarUrl)
        if (!player.isAlive) return

        player.score += (likeCount * 5L)
        player.lastActionText = "Menembak! (+${likeCount} Like)"

        // Cari musuh terdekat untuk ditembak
        val enemies = playersList.filter { it.isAlive && it.id != player.id }
        val target = enemies.minByOrNull { hypot(it.x - player.x, it.y - player.y) }

        if (target != null) {
            fireBulletFromPlayer(player, target.x, target.y)
        } else {
            // Tembak ke depan sesuai angle
            val rad = Math.toRadians(player.angleDeg.toDouble()).toFloat()
            fireBulletFromPlayer(player, player.x + cos(rad), player.y + sin(rad))
        }

        _state.value = _state.value.copy(
            lastEventMessage = "❤️ ${cleanName} menembak (+${likeCount} Like)!"
        )
        triggerOverlayBroadcast()
    }

    fun onChatEvent(senderName: String, msgText: String, avatarUrl: String) {
        if (!_state.value.isGameActive) return
        val cleanName = senderName.trim().ifBlank { "Prajurit_Live" }
        val player = getOrCreatePlayer(cleanName, avatarUrl)
        if (!player.isAlive) return

        val now = System.currentTimeMillis()
        player.isDodgeRolling = true
        player.dodgeRollUntilMs = now + 1200L
        player.score += 20L
        player.lastActionText = "DODGE ROLL! (Hindar)"

        // Manuver loncat ke samping
        val dodgeAngle = Random.nextFloat() * 6.283f
        player.vx = cos(dodgeAngle) * 0.025f
        player.vy = sin(dodgeAngle) * 0.025f

        // Tembak 3 peluru burst
        val enemies = playersList.filter { it.isAlive && it.id != player.id }
        val target = enemies.minByOrNull { hypot(it.x - player.x, it.y - player.y) }
        val tx = target?.x ?: (player.x + 0.1f)
        val ty = target?.y ?: (player.y + 0.1f)
        val gen = sessionGeneration.get()

        scope.launch {
            repeat(3) {
                if (gen != sessionGeneration.get() || !_state.value.isGameActive || !_state.value.isGameRunning) {
                    return@launch
                }
                if (player.isAlive) {
                    fireBulletFromPlayer(player, tx, ty)
                    delay(120L)
                }
            }
        }

        _state.value = _state.value.copy(
            lastEventMessage = "💬 ${cleanName} dodge roll & burst tembak!"
        )
        triggerOverlayBroadcast()
    }

    fun onGiftEvent(senderName: String, giftName: String, deltaGifts: Int, deltaCoins: Long, avatarUrl: String) {
        if (!_state.value.isGameActive) return
        val cleanName = senderName.trim().ifBlank { "Sultan_Live" }
        val player = getOrCreatePlayer(cleanName, avatarUrl)
        player.coinsContributed += deltaCoins
        player.score += (deltaCoins * 50L)

        // Hidupkan kembali jika tereliminasi (Revive Sultan)
        if (!player.isAlive) {
            player.isEliminated = false
            player.hp = 100f
            player.x = 0.5f + (Random.nextFloat() * 0.3f - 0.15f)
            player.y = 0.5f + (Random.nextFloat() * 0.3f - 0.15f)
            spawnExplosion(player.x, player.y, 0.12f, "#10B981", "REVIVE SULTAN!")
        }

        val newTier = TrooperWeaponTier.fromCoins(deltaCoins)
        if (newTier.tierLevel >= player.weaponTier.tierLevel) {
            player.weaponTier = newTier
        }

        // Beri Shield HP tambahan
        val shieldBonus = (deltaCoins * 2f).coerceIn(30f, 300f)
        player.shieldHp += shieldBonus

        player.lastActionText = "DROP SENJATA: ${newTier.title} (+${shieldBonus.toInt()} Shield)!"
        spawnExplosion(player.x, player.y, 0.10f, newTier.bulletColorHex, newTier.title)

        // Jika Gift besar (Airstrike / Nuke / Tank)
        if (deltaCoins >= 100L) {
            triggerAirstrikeEvent(player)
        }

        _state.value = _state.value.copy(
            lastEventMessage = "🎁 SULTAN ${cleanName} MENDROP ${newTier.title} (${deltaCoins} Koin)!"
        )
        playToneSound(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD)
        triggerOverlayBroadcast()
    }

    private fun triggerAirstrikeEvent(player: TrooperPlayer) {
        if (!_state.value.isGameActive) return
        val gen = sessionGeneration.get()
        scope.launch {
            repeat(5) {
                if (gen != sessionGeneration.get() || !_state.value.isGameActive || !_state.value.isGameRunning) {
                    return@launch
                }
                val rx = 0.15f + Random.nextFloat() * 0.70f
                val ry = 0.15f + Random.nextFloat() * 0.70f
                spawnExplosion(rx, ry, 0.12f, "#EF4444", "AIR STRIKE!")

                // Damage area ke semua musuh di dekat titik bom
                for (victim in playersList) {
                    if (victim.isAlive && victim.id != player.id) {
                        val d = hypot(victim.x - rx, victim.y - ry)
                        if (d < 0.15f) {
                            victim.hp = (victim.hp - 45f).coerceAtLeast(0f)
                            if (victim.hp <= 0f && !victim.isEliminated) {
                                victim.isEliminated = true
                                victim.eliminatedReason = "Hancur oleh Serangan Udara ${player.name}"
                                player.kills += 1
                                player.score += 300L
                            }
                        }
                    }
                }
                delay(200L)
            }
        }
    }

    fun onJoinEvent(senderName: String, avatarUrl: String) {
        if (!_state.value.isGameActive) return
        val cleanName = senderName.trim().ifBlank { "Prajurit_Baru" }
        getOrCreatePlayer(cleanName, avatarUrl)
    }

    private fun getOrCreatePlayer(name: String, avatarUrl: String): TrooperPlayer {
        val existing = playersList.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            if (avatarUrl.isNotBlank() && existing.avatarUrl.isBlank()) {
                existing.avatarUrl = avatarUrl
                fetchAvatarBase64Async(existing)
            }
            return existing
        }

        // Batasi maksimal pemain
        if (playersList.size >= MAX_PLAYERS) {
            val eliminated = playersList.firstOrNull { !it.isAlive }
            if (eliminated != null) {
                playersList.remove(eliminated)
            }
        }

        val safeCx = _state.value.safeZoneCenterX
        val safeCy = _state.value.safeZoneCenterY
        val safeR = (_state.value.safeZoneRadius * 0.8f).coerceAtLeast(0.1f)

        val randAngle = Random.nextFloat() * 6.283f
        val randDist = Random.nextFloat() * safeR
        val spawnX = (safeCx + cos(randAngle) * randDist).coerceIn(0.1f, 0.9f)
        val spawnY = (safeCy + sin(randAngle) * randDist).coerceIn(0.1f, 0.9f)

        val color = COLOR_PALETTE[Random.nextInt(COLOR_PALETTE.size)]

        val newPlayer = TrooperPlayer(
            id = "trooper_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}",
            name = name,
            avatarUrl = avatarUrl,
            x = spawnX,
            y = spawnY,
            colorHex = color,
            lastActionText = "Bergabung ke Medan Tempur!"
        )

        playersList.add(newPlayer)
        fetchAvatarBase64Async(newPlayer)
        spawnExplosion(spawnX, spawnY, 0.04f, "#06B6D4", "PARACHUTE DROP!")
        return newPlayer
    }

    private fun fetchAvatarBase64Async(player: TrooperPlayer) {
        if (player.avatarUrl.isBlank()) return
        val cached = avatarBase64Cache[player.avatarUrl]
        if (!cached.isNullOrBlank()) {
            player.avatarBase64 = cached
            return
        }

        scope.launch(Dispatchers.IO) {
            val base64 = ViewerAvatarCache.getOrFetchCompactBase64(player.avatarUrl)
            if (base64.isNotBlank()) {
                player.avatarBase64 = base64
                avatarBase64Cache[player.avatarUrl] = base64
            }
        }
    }

    // =========================================================================
    // KUSTOMISASI TOP 5 (FLEKSIBEL POSISI, UKURAN, ELEMEN)
    // =========================================================================

    fun setTop5Position(xPct: Float, yPct: Float) {
        val clampedX = xPct.coerceIn(0.0f, 0.88f)
        val clampedY = yPct.coerceIn(0.0f, 0.85f)
        val currentCfg = _state.value.top5Config.copy(posXpct = clampedX, posYpct = clampedY)
        _state.value = _state.value.copy(top5Config = currentCfg)
        savePersistedConfig(currentCfg)
        triggerOverlayBroadcast()
    }

    fun setTop5Scale(scale: Float) {
        val clampedScale = scale.coerceIn(0.5f, 2.0f)
        val currentCfg = _state.value.top5Config.copy(scale = clampedScale)
        _state.value = _state.value.copy(top5Config = currentCfg)
        savePersistedConfig(currentCfg)
        triggerOverlayBroadcast()
    }

    fun setTop5Orientation(isHorizontal: Boolean) {
        val currentCfg = _state.value.top5Config.copy(isHorizontal = isHorizontal)
        _state.value = _state.value.copy(top5Config = currentCfg)
        savePersistedConfig(currentCfg)
        triggerOverlayBroadcast()
    }

    fun setTop5ElementVisibility(showAvatar: Boolean, showName: Boolean, showKills: Boolean, showScore: Boolean) {
        val currentCfg = _state.value.top5Config.copy(
            showAvatar = showAvatar,
            showName = showName,
            showKills = showKills,
            showScore = showScore
        )
        _state.value = _state.value.copy(top5Config = currentCfg)
        savePersistedConfig(currentCfg)
        triggerOverlayBroadcast()
    }

    fun setTop5Visible(visible: Boolean) {
        val currentCfg = _state.value.top5Config.copy(isVisible = visible)
        _state.value = _state.value.copy(top5Config = currentCfg)
        savePersistedConfig(currentCfg)
        triggerOverlayBroadcast()
    }

    // =========================================================================
    // KONTROL UMUM & SIMULASI
    // =========================================================================

    fun toggleGameRunning() {
        val cur = _state.value
        val running = if (cur.isGameActive) !cur.isGameRunning else false
        _state.value = cur.copy(isGameRunning = running)
        if (!running) {
            sessionGeneration.incrementAndGet()
            stopAllToneSounds()
        }
    }

    fun resetGame() {
        bulletsList.clear()
        explosionsList.clear()

        // Reset HP semua pemain atau buat ronde baru
        for (player in playersList) {
            player.hp = 100f
            player.shieldHp = 0f
            player.isEliminated = false
            player.weaponTier = TrooperWeaponTier.TIER_0_PISTOL
            player.x = 0.5f + (Random.nextFloat() * 0.4f - 0.2f)
            player.y = 0.5f + (Random.nextFloat() * 0.4f - 0.2f)
        }

        _state.value = _state.value.copy(
            currentRound = _state.value.currentRound + 1,
            roundTimeLeftSec = 120,
            safeZoneRadius = 0.48f,
            winnerPlayerName = "",
            celebrationUntilMs = 0L,
            lastEventMessage = "Ronde baru dimulai! Bersiap menembak!"
        )
        triggerOverlayBroadcast()
    }

    fun spawnBotTroopers(count: Int = 5) {
        repeat(count) {
            val name = BOT_NAMES[Random.nextInt(BOT_NAMES.size)] + "_${Random.nextInt(10, 99)}"
            val p = getOrCreatePlayer(name, "")
            p.isBot = true
        }
        val cur = _state.value
        _state.value = cur.copy(
            isGameRunning = if (cur.isGameActive) true else false,
            lastEventMessage = "+${count} Prajurit Bot dimasukkan ke Arena!"
        )
        triggerOverlayBroadcast()
    }

    fun simulateGiftTest() {
        val randomPlayer = playersList.filter { it.isAlive }.randomOrNull() ?: getOrCreatePlayer("Sultan_Tester", "")
        val sampleGifts = listOf(
            Triple("Mawar", 1, 1L),
            Triple("Parfum", 1, 20L),
            Triple("Panda", 1, 100L),
            Triple("Singa", 1, 500L)
        )
        val picked = sampleGifts.random()
        onGiftEvent(randomPlayer.name, picked.first, picked.second, picked.third, randomPlayer.avatarUrl)
    }

    fun setArenaDisplayActive(active: Boolean) {
        if (active) {
            InteractiveTugOfWarEngine.peekInstance()?.setArenaDisplayActive(false)
            InteractiveKingOfThroneEngine.peekInstance()?.setArenaDisplayActive(false)
            InteractiveBeyBattleEngine.peekInstance()?.setArenaDisplayActive(false)
        }
        val stillActive = active || _state.value.isEnabled
        if (!stillActive) {
            sessionGeneration.incrementAndGet()
            stopAllToneSounds()
        }
        _state.value = _state.value.copy(
            isArenaDisplayActive = active,
            isGameRunning = if (active) true else if (stillActive) _state.value.isGameRunning else false
        )
        if (active) {
            // Pastikan minimal ada pemain
            if (playersList.isEmpty()) {
                spawnBotTroopers(6)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            InteractiveTugOfWarEngine.peekInstance()?.setEnabled(false, propagateRemote = false)
            InteractiveKingOfThroneEngine.peekInstance()?.setEnabled(false, propagateRemote = false)
            InteractiveBeyBattleEngine.peekInstance()?.setEnabled(false)
        }
        val stillActive = enabled || _state.value.isArenaDisplayActive
        if (!stillActive) {
            sessionGeneration.incrementAndGet()
            stopAllToneSounds()
        }
        _state.value = _state.value.copy(
            isEnabled = enabled,
            isGameRunning = if (enabled) true else if (stillActive) _state.value.isGameRunning else false
        )
        triggerOverlayBroadcast(force = !enabled)
    }

    fun setSoundEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(soundEnabled = enabled)
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
        if (!enabled) {
            stopAllToneSounds()
        }
    }

    private fun triggerOverlayBroadcast(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastBroadcastTime < 200L) return
        lastBroadcastTime = now

        broadcastJob?.cancel()
        broadcastJob = scope.launch {
            try {
                val s = _state.value
                if (s.isEnabled) {
                    val payload = buildOverlayPayload(s)
                    OverlayBannerBroadcaster.broadcastShow(
                        context = context,
                        senderName = "TUG_OF_WAR",
                        content = payload,
                        isGift = false,
                        bannerType = "TUG_OF_WAR",
                        customPrefix = "",
                        durationMs = 8000L,
                        posXpct = s.posXpct,
                        posYpct = s.posYpct,
                        scale = s.overlayScale,
                        fontSizeSp = 12f,
                        rotation = 0,
                        isMirrored = false,
                        entryDirection = "FADE",
                        capsuleAlpha = 1.0f
                    )
                } else {
                    OverlayBannerBroadcaster.broadcastHide(context, bannerType = "TUG_OF_WAR")
                }
            } catch (_: Throwable) {}
        }
    }

    private fun buildOverlayPayload(s: TrooperRoyaleState): String {
        // Format payload untuk OverlayBannerBitmapGenerator:
        // TROOPER_ROYALE|round|alive|total|safeR|lastMsg|winner|top1Name|top1Kills|posX|posY|scale
        val top1 = s.top5List.firstOrNull()
        return "TROOPER_ROYALE|${s.currentRound}|${s.totalAliveCount}|${s.totalPlayerCount}|" +
                "${s.safeZoneRadius}|${s.lastEventMessage}|${s.winnerPlayerName}|" +
                "${top1?.name ?: "-"}|${top1?.kills ?: 0}|${s.top5Config.posXpct}|${s.top5Config.posYpct}|${s.top5Config.scale}"
    }
}
