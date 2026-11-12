package com.tajasb.game

/**
 * BeyBladeModels:
 * Model data, enum tier hadiah koin, dan state untuk Game Interaktif:
 * "🌀 BeyBattle Royale (Arena Gangsing Tempur TikTok Live)".
 *
 * Mendukung 2 mode penayangan:
 * 1. Layar Penuh Device 2 (Full-Screen 60 FPS Arena di HP ke-2)
 * 2. Overlay Kamera Virtual VCam (Piringan stadium transparan di video siaran)
 */

enum class BeyBladeGiftTier(
    val tierLevel: Int,
    val title: String,
    val scaleMultiplier: Float,
    val auraColorHex: String,
    val baseMaxHp: Float = 100f,
    val damageReduction: Float = 0.0f,
    val isImmuneToTrapdoors: Boolean = false,
    val isImmuneToWeakRingOut: Boolean = false
) {
    TIER_0_FREE(0, "Reguler Top", 1.0f, "#06B6D4", 100f, 0.0f, false, false),
    TIER_1_MINI(1, "Spin Boost", 1.15f, "#3B82F6", 250f, 0.15f, false, false),
    TIER_2_TITAN(2, "Titan Inferno", 1.40f, "#EF4444", 600f, 0.30f, false, false),
    TIER_3_THUNDER(3, "Mega Thunder", 1.75f, "#A855F7", 1200f, 0.50f, true, false),
    TIER_4_GALAXY(4, "Galaxy Emperor", 2.20f, "#FACC15", 2500f, 0.70f, true, true);

    companion object {
        fun fromCoins(coins: Long): BeyBladeGiftTier {
            return when {
                coins >= 500L -> TIER_4_GALAXY
                coins >= 100L -> TIER_3_THUNDER
                coins >= 10L -> TIER_2_TITAN
                coins >= 1L -> TIER_1_MINI
                else -> TIER_0_FREE
            }
        }
    }
}

enum class ArenaBoundaryMode { WALL_BOUNCE, RING_OUT }

enum class ParticleKind { SPARK, SHATTER, LIGHTNING }

data class BeyBladePlayer(
    val id: String,
    val name: String,
    var avatarUrl: String = "",
    var avatarBase64: String = "",
    var x: Float = 0f,                         // Posisi relatif terhadap radius arena (-1.0f .. 1.0f)
    var y: Float = 0f,
    var vx: Float = 0f,                        // Kecepatan vektor
    var vy: Float = 0f,
    var rotationDeg: Float = 0f,               // Sudut visual putaran bilah
    var rpm: Float = 1200f,                    // Kecepatan putaran
    var hp: Float = 100f,                      // Stamina putaran (0..100)
    var maxHp: Float = 100f,
    var tier: BeyBladeGiftTier = BeyBladeGiftTier.TIER_0_FREE,
    var shieldDurationSec: Float = 0f,
    var shockwaveUntilMs: Long = 0L,
    var turboDashUntilMs: Long = 0L,
    var koCount: Int = 0,                      // Jumlah lawan yang berhasil dikeluarkan dari ring
    var isEliminated: Boolean = false,
    var eliminatedReason: String = "",         // "RING_OUT", "SPIN_OUT", atau "FALL_OUT"
    val colorHex: String = "#06B6D4",
    var lastAction: String = "",
    var coinsContributed: Long = 0L,
    var likesContributed: Long = 0L,
    var chatsContributed: Long = 0L,
    var lastHitByPlayerId: String = "",
    var lastHitTimeMs: Long = 0L,
    var avatarBitmap: android.graphics.Bitmap? = null,
    var isBoss: Boolean = false,
    var frozenUntilMs: Long = 0L,
    var meteorTargetUntilMs: Long = 0L,
    var likeSizeBonus: Float = 0f,             // Pertumbuhan ukuran fisik dari Tap Like (0f..maxCap)
    var maxLikeSizeCap: Float = 0.35f,         // Batas maksimal pertumbuhan dari Tap Like (dapat disetting host)
    var hitFlashUntilMs: Long = 0L,            // Kilatan putih saat kena benturan bilah
    var isDefendingChampion: Boolean = false,  // Mahkota emas juara bertahan ronde lalu
    var trailX1: Float = 0f,
    var trailY1: Float = 0f,
    var trailX2: Float = 0f,
    var trailY2: Float = 0f
) {
    val isBounty: Boolean
        get() = koCount >= 3

    val isShielded: Boolean
        get() = shieldDurationSec > 0f

    val isTurboDashing: Boolean
        get() = System.currentTimeMillis() < turboDashUntilMs

    val isShockwaving: Boolean
        get() = System.currentTimeMillis() < shockwaveUntilMs

    val isFrozen: Boolean
        get() = System.currentTimeMillis() < frozenUntilMs

    val isHitFlashing: Boolean
        get() = System.currentTimeMillis() < hitFlashUntilMs

    val effectiveRadius: Float
        get() = if (isBoss) 0.36f else (0.12f * tier.scaleMultiplier * (1f + likeSizeBonus.coerceIn(0f, maxLikeSizeCap.coerceIn(0f, 0.50f))))
}

enum class HazardTrapState { CLOSED, WARNING, OPEN }

data class TrapdoorZone(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float = 0.18f,
    var state: HazardTrapState = HazardTrapState.CLOSED
)

data class ActiveMeteor(
    val id: Long,
    val targetX: Float,
    val targetY: Float,
    var altitude: Float = 1.0f,                 // 1.0f turun ke 0f (hantam tanah)
    var hasImpacted: Boolean = false,
    val spawnedAtMs: Long = System.currentTimeMillis()
)

data class SparkParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val colorHex: String,
    var lifeRemainingSec: Float = 0.35f,
    val maxLifeSec: Float = 0.35f,
    val sizeDp: Float = 3.0f,
    val kind: ParticleKind = ParticleKind.SPARK
)

data class FloatingDamageText(
    val id: Long,
    var x: Float,
    var y: Float,
    val text: String,
    val colorHex: String,
    val isCrit: Boolean = false,
    var lifeRemainingSec: Float = 0.75f,
    val maxLifeSec: Float = 0.75f
)

data class KillFeedEntry(
    val id: Long,
    val killerName: String,
    val victimName: String,
    val method: String,                        // "SHRED", "RING_OUT", "TRAP", "BOSS"
    val createdAtMs: Long = System.currentTimeMillis()
)

data class BeyBattleState(
    val isEnabled: Boolean = false,                  // VCam Overlay Aktif/Mati (Default OFF)
    val isArenaDisplayActive: Boolean = false,       // Layar Penuh Device 2 Aktif/Mati
    val arenaTitle: String = "BEYBATTLE ROYALE",     // Judul Arena
    val currentRound: Int = 1,
    val players: List<BeyBladePlayer> = emptyList(),
    val eliminatedPlayers: List<BeyBladePlayer> = emptyList(),
    val lastChampion: BeyBladePlayer? = null,
    val celebrationUntilMs: Long = 0L,
    val isDrawEnding: Boolean = false,               // Ronde berakhir seri (0 bertahan) menunggu auto-reset
    val lastEventBanner: String = "Menunggu pemain masuk arena...",
    val lastEventType: String = "IDLE",             // "IDLE", "SPAWN", "DASH", "LIKE", "GIFT", "KO", "CHAMPION", "DRAW", "BOSS_SPAWN", "METEOR", "BLIZZARD"
    val totalEliminationsInRound: Int = 0,
    val roundStartedAtMs: Long = 0L,
    val posXpct: Float = 0.50f,                     // Posisi X di VCam Overlay (0..1)
    val posYpct: Float = 0.50f,                     // Posisi Y di VCam Overlay (0..1)
    val overlayScale: Float = 1.0f,                 // Skala Zoom VCam Overlay (0.5..2.5)
    val overlayRotation: Int = 0,
    val isMirrored: Boolean = false,
    val arenaWidthScale: Float = 1.0f,              // Pengali Lebar Arena / L (0.4f .. 1.8f)
    val arenaHeightScale: Float = 1.0f,             // Pengali Panjang Arena / P (0.4f .. 1.8f)
    val arenaShape: String = "CIRCLE",              // "CIRCLE" (Bulat/Oval) atau "RECTANGLE" (Kotak/Persegi)
    val boundaryMode: ArenaBoundaryMode = ArenaBoundaryMode.WALL_BOUNCE, // Mode batas arena (Pantul + Shrinking Zone vs Ring-Out Klasik)
    val shrinkFactor: Float = 1.0f,                 // 1.0f menyusut hingga minShrinkFactor saat Sudden Death
    val arenaBgType: String = "CYBER_GRID",         // "CYBER_GRID", "LAVA_INFERNO", "DEEP_GALAXY", "NEON_CIRCUIT", "METAL_STADIUM", "SOLID_DARK", "CUSTOM_IMAGE"
    val customBgPath: String = "",                  // Path file gambar background kustom dari galeri
    val isHazardsEnabled: Boolean = true,           // Master Rintangan Aktif
    val isVortexEnabled: Boolean = true,            // Rintangan Vortex Pusat
    val isLaserEnabled: Boolean = true,             // Rintangan Laser Berputar
    val isTrapdoorsEnabled: Boolean = true,         // Rintangan 4 Trapdoor Maut
    val bossMaxHp: Float = 3000f,                   // HP Maksimal Titan Boss (1000..10000)
    val isSoundEnabled: Boolean = true,             // Master Audio Aktif
    val isSfxEnabled: Boolean = true,               // Saklar Terpisah SFX Benturan & Bilah
    val isAnnouncerEnabled: Boolean = true,         // Saklar Terpisah Suara Komentator TTS
    val masterVolume: Float = 0.85f,                // Slider Volume Master (0.0..1.0)
    val autoResetEnabled: Boolean = true,           // Auto-Reset Otomatis Saat Ronde Selesai
    val celebrationDurationSec: Int = 4,            // Jeda Selebrasi Sebelum Auto-Reset (1..15 detik)
    val autoSpawnBotsEnabled: Boolean = true,       // Auto-Isi Bot Jika Pemain Kurang dari Target Minimal
    val minBotTargetCount: Int = 4,                 // Minimal Pemain/Bot Aktif di Arena (2..12)
    val maxActivePlayers: Int = 25,                 // Batas Maksimal Pemain di Arena (5..40)
    val passiveHpDecayPerSec: Float = 0.45f,        // Peluruhan Stamina Pasif per Detik (0.0..3.0)
    val bounceMultiplier: Float = 1.0f,             // Pengali Kecepatan & Pantulan (0.5..2.0)
    val damageMultiplier: Float = 1.0f,             // Pengali Damage Gesekan Bilah (0.5..2.5)
    val maxLikeSizeBonus: Float = 0.35f,            // Batas Maksimal Pembesaran Tap Like (0.0..0.50)
    val shrinkStartSec: Int = 20,                   // Waktu Mulai Zona Menyusut (5..60 detik)
    val minShrinkFactor: Float = 0.52f,             // Batas Ukuran Minimal Zona Menyusut (0.35..0.90)
    val showKillFeed: Boolean = true,               // Visibilitas Kill Feed
    val showEventTicker: Boolean = true,            // Visibilitas Banner Event Ticker
    val showRoundBadge: Boolean = true,             // Visibilitas Badge Info Ronde
    val isBossModeActive: Boolean = false,          // Mode Titan Boss Raksasa Aktif
    val bossPlayer: BeyBladePlayer? = null,         // Data Titan Boss Raksasa
    val vortexPullIntensity: Float = 0f,            // 0..1 intensitas hisap vortex pusat
    val laserAngleDeg: Float = 0f,                  // Sudut putaran sinar laser aktif
    val isLaserActive: Boolean = false,             // Apakah sinar laser sedang menembak
    val trapdoors: List<TrapdoorZone> = emptyList(),
    val activeMeteors: List<ActiveMeteor> = emptyList(),
    val killFeed: List<KillFeedEntry> = emptyList(),
    val topHunterId: String? = null,
    val blizzardUntilMs: Long = 0L,                 // Durasi badai es aktif
    val screenShakeIntensity: Float = 0f,           // 0..1 getaran layar saat ledakan keras
    val isScreenShakeEnabled: Boolean = true,        // Saklar Master Goyang Layar (Screen Shake)
    val screenShakeMultiplier: Float = 1.0f,         // Pengali Kekuatan Goyang Layar (0.2..1.5)
    val retainGiftersAcrossRounds: Boolean = true,   // Retensi Gifter & Survivor Tetap Masuk Ronde Baru
    val feverUntilMs: Long = 0L,                     // Durasi mode fever aktif (kecepatan x2)
    val currentFeverLikes: Int = 0,                  // Like terkumpul menuju target fever
    val feverTargetLikes: Int = 300,                 // Target like kolektif penonton (default 300)
    val isFinalClashActive: Boolean = false,         // Slow-motion duel 1 vs 1 penentu juara
    val showLeaderboardOverlay: Boolean = false,     // Tampilkan overlay Top Sultan & Hunter di arena
    val defendingChampionName: String? = null,       // Juara bertahan ronde sebelumnya
    val lastAnnouncerVoiceText: String = ""         // Teks suara komentator announcer
) {
    val isGameActive: Boolean
        get() = isEnabled || isArenaDisplayActive

    val isBlizzardActive: Boolean
        get() = System.currentTimeMillis() < blizzardUntilMs

    val isFeverActive: Boolean
        get() = System.currentTimeMillis() < feverUntilMs

    val activePlayers: List<BeyBladePlayer>
        get() = players.filter { !it.isEliminated }

    val activePlayersCount: Int
        get() = activePlayers.size

    val isCelebratingChampion: Boolean
        get() = System.currentTimeMillis() < celebrationUntilMs && lastChampion != null

    val isRoundEnding: Boolean
        get() = celebrationUntilMs > 0L && (lastChampion != null || isDrawEnding)

    val topKoHunter: BeyBladePlayer?
        get() = (players + eliminatedPlayers).maxByOrNull { it.koCount }

    val topSultans: List<BeyBladePlayer>
        get() = (players + eliminatedPlayers).filter { it.coinsContributed > 0 }.sortedByDescending { it.coinsContributed }.take(3)

    val topHunters: List<BeyBladePlayer>
        get() = (players + eliminatedPlayers).filter { it.koCount > 0 }.sortedByDescending { it.koCount }.take(3)
}

/**
 * Helper pembuat & cache foto profil melingkar untuk setiap gangsing penonton.
 */
object BeyBladeAvatarHelper {
    private val avatarCache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()

    fun getCircularAvatar(
        name: String,
        avatarBase64: String = "",
        colorHex: String = "#06B6D4",
        targetSizePx: Int = 96
    ): android.graphics.Bitmap {
        val cacheKey = "${name}_${avatarBase64.hashCode()}_${targetSizePx}"
        avatarCache[cacheKey]?.let { return it }

        val bmp = if (avatarBase64.isNotBlank()) {
            try {
                val bytes = android.util.Base64.decode(avatarBase64, android.util.Base64.DEFAULT)
                val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded != null) {
                    maskCircular(decoded, targetSizePx)
                } else {
                    generateInitialAvatar(name, colorHex, targetSizePx)
                }
            } catch (_: Throwable) {
                generateInitialAvatar(name, colorHex, targetSizePx)
            }
        } else {
            generateInitialAvatar(name, colorHex, targetSizePx)
        }

        avatarCache[cacheKey] = bmp
        return bmp
    }

    fun maskCircular(src: android.graphics.Bitmap, size: Int): android.graphics.Bitmap {
        val output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.Rect(0, 0, size, size)

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, null, rect, paint)
        return output
    }

    fun generateInitialAvatar(name: String, colorHex: String, size: Int): android.graphics.Bitmap {
        val output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        // Lingkaran luar dengan warna neon identitas gangsing
        val baseColor = try {
            android.graphics.Color.parseColor(colorHex)
        } catch (_: Throwable) {
            android.graphics.Color.parseColor("#06B6D4")
        }
        paint.color = baseColor
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Lingkaran dalam gelap
        paint.color = android.graphics.Color.parseColor("#0F172A")
        canvas.drawCircle(size / 2f, size / 2f, size * 0.44f, paint)

        // Inisial Nama (1 atau 2 huruf kapital tebal)
        val cleanName = name.trim().ifBlank { "P" }
        val initial = if (cleanName.length >= 2) cleanName.take(2).uppercase() else cleanName.take(1).uppercase()
        paint.color = android.graphics.Color.WHITE
        paint.textSize = size * 0.38f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = android.graphics.Paint.Align.CENTER
        val yOffset = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(initial, size / 2f, yOffset, paint)

        return output
    }
}

