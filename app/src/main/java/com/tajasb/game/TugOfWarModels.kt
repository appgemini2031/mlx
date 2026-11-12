package com.tajasb.game

/**
 * Daftar 6 Mode Game Interaktif Live untuk TikTok Live (Mode Game Seluler 2-HP & VCam Overlay).
 */
enum class InteractiveGameMode(
    val id: String,
    val icon: String,
    val title: String,
    val shortTitle: String,
    val description: String,
    val ruleSummary: String
) {
    TUG_OF_WAR(
        id = "TUG_OF_WAR",
        icon = "⚔️",
        title = "Tarik Tambang Duel Kubu",
        shortTitle = "Tarik Tambang",
        description = "Dua kubu penonton beradu tarik tali tambang dari tengah (50%-50%) menuju garis kemenangan.",
        ruleSummary = "Tap, Chat Tim, & Gift menarik simpul tambang ke kubu pilihanmu!"
    ),
    SPEED_RACE(
        id = "SPEED_RACE",
        icon = "🏎️",
        title = "Balap Mobil / Roket Sultan",
        shortTitle = "Balap Mobil",
        description = "Balapan 2 jalur cepat dari garis START (0%) ke FINISH (100%). Gift menyalakan NOS Turbo Boost!",
        ruleSummary = "Dorong mobil kubumu dari 0% sampai menyentuh Garis FINISH 100% lebih dulu!"
    ),
    BOSS_RAID(
        id = "BOSS_RAID",
        icon = "🐉",
        title = "Raid Boss Monster Raksasa",
        shortTitle = "Raid Boss",
        description = "Keroyok HP Boss Naga Raksasa hingga 0 HP! Kubu dengan Total Damage tertinggi & Last Hit menang.",
        ruleSummary = "Serang Boss bersama-sama! Rebut gelar MVP Top Damage & Last Hit saat Boss tumbang!"
    ),
    BALLOON_POP(
        id = "BALLOON_POP",
        icon = "🎈",
        title = "Pompa Balon / Bom Waktu",
        shortTitle = "Pompa Balon",
        description = "Dorong balon bertekanan tinggi ke kubu lawan sampai membesar kritis (100%) dan MELEDAK!",
        ruleSummary = "Pompa & dorong bom balon ke arah lawan sampai meledak BOOM!"
    ),
    BOXING_KO(
        id = "BOXING_KO",
        icon = "🥊",
        title = "Tinju / Adu Pukul K.O.",
        shortTitle = "Tinju K.O.",
        description = "Pertarungan 1 lawan 1 dengan HP Bar 100%. Serangan kubumu menguras darah petarung lawan hingga K.O.!",
        ruleSummary = "Kuras HP Bar petarung lawan dari 100% menjadi 0% untuk menang K.O.!"
    ),
    TURF_WAR(
        id = "TURF_WAR",
        icon = "🏰",
        title = "Perang Wilayah / Kuasai Layar",
        shortTitle = "Perang Wilayah",
        description = "Perebutan 20 blok benteng wilayah kristal. Kuasai 100% petak peta layar untuk menang!",
        ruleSummary = "Rebut seluruh blok wilayah lawan hingga 100% peta dikuasai timmu!"
    );

    companion object {
        fun fromId(id: String?): InteractiveGameMode {
            if (id.isNullOrBlank()) return TUG_OF_WAR
            return entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: TUG_OF_WAR
        }
    }
}

/**
 * TugOfWarModels:
 * Model data dan state untuk 6 Game Interaktif Live.
 * Mendukung 2 mode tayangan sekaligus:
 * 1. Mode Game Seluler 2-HP (Full-Screen Clean Arena Display di Device 2 + Remote Kontroler di Device 1)
 * 2. Mode Overlay Kamera Virtual (VCam)
 */
data class TugOfWarState(
    val isEnabled: Boolean = false,
    val isArenaDisplayActive: Boolean = false, // True saat Layar Penuh Mode Game Seluler dibuka di Device 2
    val gameMode: InteractiveGameMode = InteractiveGameMode.TUG_OF_WAR,
    val teamAName: String = "COWOK",
    val teamBName: String = "CEWEK",
    val teamAColorHex: String = "#0284C7", // Cyan / Biru Maskulin
    val teamBColorHex: String = "#E11D48", // Rose / Pink Feminin
    val ropePosition: Float = 0.0f,        // -pointsToWin (Tim A menang) s/d +pointsToWin (Tim B menang)
    val teamARacePoints: Float = 0.0f,     // Progres independen Tim A (0 s/d pointsToWin) untuk Balap/Raid/Boxing
    val teamBRacePoints: Float = 0.0f,     // Progres independen Tim B (0 s/d pointsToWin) untuk Balap/Raid/Boxing
    val pointsToWin: Float = 500.0f,       // Poin yang dibutuhkan untuk mencapai batas kemenangan
    val teamAWins: Int = 0,                // Jumlah ronde yang dimenangkan Tim A
    val teamBWins: Int = 0,                // Jumlah ronde yang dimenangkan Tim B
    val currentRound: Int = 1,             // Ronde saat ini
    val likeWeight: Float = 1.0f,          // 1 Like = +1 poin
    val chatWeight: Float = 10.0f,         // 1 Chat kata kunci tim = +10 poin
    val giftMultiplier: Float = 10.0f,     // 1 Koin TikTok = +10 poin tarikan
    val lastActionText: String = "Menunggu interaksi penonton...",
    val lastActionTeam: String = "",       // "A" atau "B"
    val recentFeed: List<String> = emptyList(),
    val lastWinnerTeam: String = "",
    val winnerCelebrationUntilMs: Long = 0L,
    val topPullerTeamA: String = "-",
    val topPullerTeamB: String = "-",
    val topPullerPointsA: Long = 0L,
    val topPullerPointsB: Long = 0L,
    // Posisi & Transformasi di Kamera Virtual
    val posXpct: Float = 0.50f,            // Rata tengah horizontal
    val posYpct: Float = 0.28f,            // Atas tengah layar siaran
    val overlayScale: Float = 1.0f,
    val overlayRotation: Int = 0,
    val isMirrored: Boolean = false,
    val isHubActive: Boolean = false,
    val isAutoTraffic: Boolean = false
) {
    val isGameActive: Boolean
        get() = isEnabled || isArenaDisplayActive

    val isCelebrating: Boolean
        get() = System.currentTimeMillis() < winnerCelebrationUntilMs

    val safeTargetPoints: Float
        get() = pointsToWin.coerceAtLeast(1f)

    val normalizedProgress: Float
        get() = ((ropePosition / safeTargetPoints).coerceIn(-1.0f, 1.0f) + 1.0f) / 2.0f // 0.0f (Tim A 100%) .. 1.0f (Tim B 100%)

    val teamAPercent: Int
        get() = when (gameMode) {
            InteractiveGameMode.SPEED_RACE -> ((teamARacePoints / safeTargetPoints) * 100f).toInt().coerceIn(0, 100)
            InteractiveGameMode.BOXING_KO -> (((safeTargetPoints - teamBRacePoints) / safeTargetPoints) * 100f).toInt().coerceIn(0, 100)
            InteractiveGameMode.BOSS_RAID -> {
                val totalDmg = (teamARacePoints + teamBRacePoints).coerceAtLeast(1f)
                if (teamARacePoints == 0f && teamBRacePoints == 0f) 50
                else ((teamARacePoints / totalDmg) * 100f).toInt().coerceIn(0, 100)
            }
            else -> ((1.0f - normalizedProgress) * 100).toInt().coerceIn(0, 100)
        }

    val teamBPercent: Int
        get() = when (gameMode) {
            InteractiveGameMode.SPEED_RACE -> ((teamBRacePoints / safeTargetPoints) * 100f).toInt().coerceIn(0, 100)
            InteractiveGameMode.BOXING_KO -> (((safeTargetPoints - teamARacePoints) / safeTargetPoints) * 100f).toInt().coerceIn(0, 100)
            InteractiveGameMode.BOSS_RAID -> {
                if (teamARacePoints == 0f && teamBRacePoints == 0f) 50
                else (100 - teamAPercent).coerceIn(0, 100)
            }
            else -> (normalizedProgress * 100).toInt().coerceIn(0, 100)
        }

    val bossRemainingHp: Int
        get() = (safeTargetPoints - (teamARacePoints + teamBRacePoints)).toInt().coerceAtLeast(0)

    val bossHpPercent: Int
        get() = ((bossRemainingHp.toFloat() / safeTargetPoints) * 100f).toInt().coerceIn(0, 100)
}
