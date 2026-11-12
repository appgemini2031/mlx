package com.tajasb.game

/**
 * KingOfThroneModels:
 * Model data dan state untuk Game Interaktif Ke-2: "👑 Raja Singgasana (King of the Hill / Rebutan Kursi Sultan)".
 * Mendukung 2 mode tayangan sekaligus:
 * 1. Mode Game Seluler 2-HP (Full-Screen Clean Arena 60 FPS di Device 2 + Remote Kontroler di Device 1)
 * 2. Mode Overlay Kamera Virtual (VCam)
 */
data class ThroneChallengerEntry(
    val name: String,
    val points: Long,
    val avatarBase64: String = ""
)

data class KingOfThroneState(
    val isEnabled: Boolean = false,                  // Default OFF untuk VCam Overlay
    val isArenaDisplayActive: Boolean = false,       // True saat Layar Penuh Arena Raja Singgasana dibuka di Device 2
    val throneTitle: String = "RAJA SINGGASANA",     // Gelar takhta (bisa diubah: SULTAN UTAMA, PENGUASA ROOM, RATU LIVE, dll.)
    val currentKingName: String = "",                // Kosong berarti Singgasana belum diduduki
    val currentKingAvatarBase64: String = "",        // Foto profil Raja saat ini (Base64 thumbnail)
    val currentKingScore: Long = 0L,                 // Total poin akumulasi Raja saat ini
    val reignStartedAtMs: Long = 0L,                 // Waktu mulai menduduki takhta
    val throneShieldHp: Float = 0.0f,                // Sisa Perisai / HP Singgasana saat ini
    val maxThroneShieldHp: Float = 250.0f,           // Kapasitas penuh Perisai Singgasana saat ini
    val baseShieldOnUsurp: Float = 250.0f,           // Perisai awal setiap kali Raja baru naik takhta
    val topChallengers: List<ThroneChallengerEntry> = emptyList(), // Daftar Top 3 Penantang Kudeta
    val longestReignName: String = "-",              // Rekor Raja terlama di sesi ini
    val longestReignSec: Long = 0L,                  // Durasi rekor terlama (detik)
    val highestKingName: String = "-",               // Rekor Sultan dengan poin tertinggi
    val highestKingScore: Long = 0L,
    val totalUsurpCount: Int = 0,                    // Jumlah kudeta yang terjadi pada ronde ini
    val currentRound: Int = 1,
    val likeWeight: Float = 1.0f,                    // 1 Tap Like = +1 poin
    val chatWeight: Float = 15.0f,                   // 1 Chat = +15 poin
    val giftMultiplier: Float = 25.0f,               // 1 Koin Gift = +25 poin
    val lastActionText: String = "Singgasana masih kosong! Tap Like, Komen, atau Gift untuk naik takhta!",
    val lastActionType: String = "IDLE",             // "IDLE", "DEFEND", "ATTACK", "USURP"
    val lastUsurperName: String = "",
    val lastDethronedName: String = "",
    val usurpCelebrationUntilMs: Long = 0L,
    // Posisi & Transformasi di Kamera Virtual (VCam Overlay)
    val posXpct: Float = 0.50f,
    val posYpct: Float = 0.28f,
    val overlayScale: Float = 1.0f,
    val overlayRotation: Int = 0,
    val isMirrored: Boolean = false,
    val isHubActive: Boolean = false,
    val isAutoTraffic: Boolean = false
) {
    val isGameActive: Boolean
        get() = isEnabled || isArenaDisplayActive

    val hasKing: Boolean
        get() = currentKingName.isNotBlank()

    val isCelebratingUsurp: Boolean
        get() = System.currentTimeMillis() < usurpCelebrationUntilMs && lastUsurperName.isNotBlank()

    val shieldRatio: Float
        get() = if (!hasKing || maxThroneShieldHp <= 0f) 0f else (throneShieldHp / maxThroneShieldHp).coerceIn(0f, 1f)

    val shieldPercent: Int
        get() = (shieldRatio * 100f).toInt().coerceIn(0, 100)

    val coupProgressPercent: Int
        get() = if (!hasKing) 0 else (100 - shieldPercent).coerceIn(0, 100)
}
