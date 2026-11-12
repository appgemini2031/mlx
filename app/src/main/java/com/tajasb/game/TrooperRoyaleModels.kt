package com.tajasb.game

/**
 * TrooperRoyaleModels:
 * Model data, konfigurasi senjata, sistem peluru & ledakan, serta Top 5 kustomisasi
 * untuk Game Interaktif: "🎖️ Mini Trooper Royale (Perang Prajurit Chibi TikTok Live)".
 */

enum class TrooperWeaponTier(
    val tierLevel: Int,
    val title: String,
    val damage: Float,
    val bulletColorHex: String,
    val bulletSpeed: Float,
    val fireRateMultiplier: Float
) {
    TIER_0_PISTOL(0, "Pistol", 10f, "#FACC15", 0.035f, 1.0f),
    TIER_1_SHOTGUN(1, "Shotgun", 28f, "#3B82F6", 0.040f, 1.2f),
    TIER_2_BAZOOKA(2, "Bazooka Rocket", 65f, "#EF4444", 0.045f, 1.5f),
    TIER_3_TANK(3, "Baja Tank", 130f, "#10B981", 0.050f, 2.0f),
    TIER_4_AIRSTRIKE(4, "Orbital Strike", 260f, "#A855F7", 0.065f, 3.0f);

    companion object {
        fun fromCoins(coins: Long): TrooperWeaponTier {
            return when {
                coins >= 500L -> TIER_4_AIRSTRIKE
                coins >= 100L -> TIER_3_TANK
                coins >= 10L -> TIER_2_BAZOOKA
                coins >= 1L -> TIER_1_SHOTGUN
                else -> TIER_0_PISTOL
            }
        }
    }
}

data class TrooperPlayer(
    val id: String,
    val name: String,
    var avatarUrl: String = "",
    var avatarBase64: String = "",
    var x: Float = 0.5f,                       // Posisi relatif di dalam arena (0.0f .. 1.0f)
    var y: Float = 0.5f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var angleDeg: Float = 0f,                  // Arah pandang & moncong senjata (derajat)
    var targetAngleDeg: Float = 0f,
    var hp: Float = 100f,
    var maxHp: Float = 100f,
    var shieldHp: Float = 0f,
    var weaponTier: TrooperWeaponTier = TrooperWeaponTier.TIER_0_PISTOL,
    var isDodgeRolling: Boolean = false,
    var dodgeRollUntilMs: Long = 0L,
    var kills: Int = 0,
    var score: Long = 0L,
    var coinsContributed: Long = 0L,
    var isEliminated: Boolean = false,
    var eliminatedReason: String = "",
    val colorHex: String = "#06B6D4",
    var lastActionText: String = "",
    var isBot: Boolean = false,
    var lastShotTimeMs: Long = 0L,
    var ammoCount: Int = 12
) {
    val isAlive: Boolean get() = !isEliminated && hp > 0f
}

data class TrooperBullet(
    val id: Long,
    val ownerId: String,
    val ownerName: String,
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val damage: Float,
    val colorHex: String,
    val radius: Float = 0.012f,
    val isRocket: Boolean = false,
    val isAirstrike: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class TrooperExplosion(
    val id: Long,
    val x: Float,
    val y: Float,
    var currentRadius: Float = 0.01f,
    val maxRadius: Float = 0.08f,
    val colorHex: String = "#F97316",
    var alpha: Float = 1.0f,
    val labelText: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

data class Top5DisplayConfig(
    val isVisible: Boolean = true,
    val posXpct: Float = 0.03f,                 // Posisi relatif layar X (0.0f .. 0.90f)
    val posYpct: Float = 0.12f,                 // Posisi relatif layar Y (0.0f .. 0.85f)
    val scale: Float = 1.0f,                    // Skala zoom (0.5f .. 2.0f)
    val isHorizontal: Boolean = false,          // true: Horizontal (1 baris), false: Vertikal (1 kolom)
    val showAvatar: Boolean = true,             // Tampilkan/sembunyikan foto profil
    val showName: Boolean = true,               // Tampilkan/sembunyikan nama penonton
    val showKills: Boolean = true,              // Tampilkan/sembunyikan jumlah kills
    val showScore: Boolean = true               // Tampilkan/sembunyikan skor akumulasi
)

data class Top5LeaderboardEntry(
    val rank: Int,
    val name: String,
    val avatarBase64: String,
    val kills: Int,
    val score: Long,
    val weaponTitle: String,
    val colorHex: String,
    val isAlive: Boolean = true
)

data class TrooperRoyaleState(
    val isEnabled: Boolean = false,             // Status VCam Overlay aktif
    val isArenaDisplayActive: Boolean = false,  // Status Layar Penuh Device 2 aktif
    val isGameRunning: Boolean = false,         // Game loop pertempuran aktif
    val currentRound: Int = 1,
    val roundTimeLeftSec: Int = 120,            // Durasi ronde (120 detik)
    val safeZoneCenterX: Float = 0.5f,
    val safeZoneCenterY: Float = 0.5f,
    val safeZoneRadius: Float = 0.48f,          // Radius lingkaran aman (0.48f menyusut ke 0.12f)
    val totalAliveCount: Int = 0,
    val totalPlayerCount: Int = 0,
    val winnerPlayerName: String = "",
    val winnerAvatarBase64: String = "",
    val winnerKills: Int = 0,
    val winnerWeaponTitle: String = "",
    val celebrationUntilMs: Long = 0L,
    val lastEventMessage: String = "Tap Like = Tembak | Chat = Hindar | Gift = Senjata Super",
    val top5List: List<Top5LeaderboardEntry> = emptyList(),
    val top5Config: Top5DisplayConfig = Top5DisplayConfig(),
    // Koordinat untuk Overlay VCam
    val posXpct: Float = 0.50f,
    val posYpct: Float = 0.35f,
    val overlayScale: Float = 1.0f,
    val soundEnabled: Boolean = true
) {
    val isGameActive: Boolean
        get() = isEnabled || isArenaDisplayActive

    val isCelebratingVictory: Boolean
        get() = System.currentTimeMillis() < celebrationUntilMs && winnerPlayerName.isNotBlank()

    val isSafeZoneActive: Boolean
        get() = isGameActive && isGameRunning && safeZoneRadius < 0.45f
}
