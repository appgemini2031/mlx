package com.tajasb.model

data class CapsuleSettings(
    val backgroundColorHex: String = "#1E293B",
    val transparency: Float = 0.85f, // 0.0 to 1.0 (opacity)
    val outlineThicknessDp: Float = 2.0f, // 0 to 8 dp
    val outlineColorHex: String = "#8B5CF6",
    val cornerRadiusDp: Float = 40.0f // Up to 60dp for complete pill roundness
)

data class StickerSettings(
    val linesCount: Int = 3, // 2, 3, 4, 5
    val line1: String = "✨ PROMO SPESIAL LIVE ✨",
    val line2: String = "🔥 DISKON HINGGA 70% HARI INI",
    val line3: String = "⚡ GRATIS ONGKIR SE-INDONESIA",
    val line4: String = "🎁 GIFT MENARIK TIAP CHECKOUT",
    val line5: String = "🛒 KLIK KERANJANG KUNING SEKARANG!",
    val textColorHex: String = "#FFFFFF",
    val textSizeSp: Float = 16.0f,
    val outlineThicknessDp: Float = 2.0f,
    val outlineColorHex: String = "#000000",
    val backgroundColorHex: String = "#7C3AED",
    val backgroundAlpha: Float = 0.90f
)

data class BuyerNotifSettings(
    val buyerName: String = "Siti Rahma",
    val productName: String = "Paket Skincare Glowing Viral",
    val timeAgo: String = "Baru saja checkout",
    val fontSizeSp: Float = 14.0f, // Ukuran font terpisah
    val cartIconSizeDp: Float = 28.0f, // Ukuran keranjang terpisah
    val cartIconColorHex: String = "#F59E0B",
    val backgroundColorHex: String = "#0F172A",
    val backgroundAlpha: Float = 0.88f
)

data class PinCommentSettings(
    val username: String = "@host_official",
    val commentText: String = "Bismillah open order, yuk tap-tap layar dan jangan lupa follow!",
    val badgeText: String = "📌 PIN KREATIF"
)

data class LiveStreamSettings(
    val isLiveActive: Boolean = false,
    val streamUrl: String = "https://makkahlive.net/en/live/makkah",
    val streamTitle: String = "Makkah Live 24 Jam - Masjidil Haram"
)

data class ActiveModulesState(
    val isVideoActive: Boolean = false,
    val isMp3Active: Boolean = false,
    val isPinActive: Boolean = false,
    val isAutoTapActive: Boolean = false,
    val isLiveMakkahActive: Boolean = false
)

enum class ScaleMode(val label: String) {
    CROP("CROP"),
    FIT("FIT"),
    STRETCH("STRETCH")
}

enum class ResolutionPreset(val label: String, val width: Int = 1280, val height: Int = 720) {
    AUTO("Auto", 0, 0),
    HD_720P("720p HD", 1280, 720),
    FHD_1080P("1080p Full HD", 1920, 1080),
    PORTRAIT_1080P("1080x1920 Portrait", 1080, 1920),
    QHD_2K("2K QHD", 2560, 1440),
    UHD_4K("4K UHD", 3840, 2160),
    STRIDE_XIAOMI("Xiaomi Stride 1088", 1088, 1920),
    SD_480P("480p SD", 854, 480),
    CUSTOM("Kustom", 1280, 720)
}

enum class HookEngineType(val label: String) {
    HYBRID("Hybrid Universal"),
    AUTO("Auto Detect"),
    VCAM_LEGACY("VCam Legacy"),
    DIRECT("Direct Camera Hook"),
    CAMERA1_LEGACY("Camera1 Legacy"),
    CAMERA2_PIPELINE("Camera2 Pipeline")
}

enum class VideoSwitchMode(val label: String) {
    INSTANT("Instan (Tanpa Jeda)"),
    SEAMLESS("Seamless (Mulus)"),
    RESTART("Restart dari Awal"),
    WAIT_FINISH("Tunggu Selesai")
}

enum class AutoNextMode(val label: String) {
    SEQUENTIAL("Berurutan"),
    RANDOM("Acak"),
    SINGLE_LOOP("Loop Tunggal")
}

enum class AntiHashIntensity(val label: String, val glIntensity: Float, val badge: String, val defaultPercent: Float) {
    OFF("Nonaktif", 0.0f, "OFF", 0.0f),
    LOW("Ringan (0.8%)", 0.008f, "LOW", 0.8f),
    MEDIUM("Seimbang (1.5%)", 0.015f, "MED", 1.5f),
    AGGRESSIVE("Agresif (2.5%)", 0.025f, "MAX", 2.5f);

    companion object {
        fun fromPercent(percent: Float): AntiHashIntensity {
            return when {
                percent <= 0.05f -> OFF
                percent <= 1.15f -> LOW
                percent <= 2.0f -> MEDIUM
                else -> AGGRESSIVE
            }
        }
    }
}

enum class AudioAntiHashIntensity(val displayName: String, val pitchRatio: Float, val tempoDriftPercent: Float) {
    LOW("Rendah (±1%)", 1.010f, 0.005f),
    MEDIUM("Sedang (±2%)", 1.020f, 0.010f),
    AGGRESSIVE("Agresif (±3%)", 1.030f, 0.015f)
}

/**
 * Efek Suara (Voice Effects) untuk Mengubah Karakter Vokal Audio (Man / Woman / dsb.)
 * Terintegrasi ke Pemutar Musik Standalone, Player Virtual Camera, & Edit Audio Massal.
 */
enum class VoiceEffect(
    val label: String,
    val icon: String,
    val pitchMultiplier: Float,
    val speedMultiplier: Float = 1.0f,
    val description: String
) {
    NORMAL("Normal", "🎙️", 1.0f, 1.0f, "Suara asli tanpa efek perubahan nada"),
    ROOM("Kamar", "🛏️", 1.0f, 1.0f, "Akustik kamar tidur / studio intim yang hangat & jernih"),
    HOUSE("Rumah", "🏠", 1.0f, 1.0f, "Akustik ruang tamu luas / aula rumah dengan resonansi lega"),
    ECHO("Echo", "📢", 1.0f, 1.0f, "Efek gema delay bertahap yang dinamis & lapang"),
    BASS_BOOST("Bass", "🔊", 0.95f, 1.0f, "Penguat frekuensi rendah / bass punchy & bertenaga"),
    MAN("Man (Cowok)", "👨", 0.82f, 1.0f, "Suara pria/cowok dengan nada berat, maskulin, & berwibawa (-3.5 semitone)"),
    WOMAN("Woman (Cewek)", "👩", 1.25f, 1.0f, "Suara wanita/cewek dengan nada halus, nyaring, & feminin (+3.5 semitone)"),
    CHIPMUNK("Chipmunk", "🐿️", 1.48f, 1.0f, "Suara kartun lucu melengking ala anak-anak (+6 semitone)"),
    ROBOT("Robot", "🤖", 0.70f, 1.0f, "Suara robotik dengan nada rendah berat (-6 semitone)")
}

/**
 * Profil DNA Lintas-Sesi (Multi-Session Anti-Detection Profile):
 * Memastikan Sesi 1, Sesi 2, Sesi 3, Sesi 4, dst. memiliki titik tengah (centroid)
 * geometri framing, rasio aspek mikro, suhu warna/gamma/kontras, arah cahaya ruangan,
 * serta frekuensi dasar suara (pitch & speed) yang berbeda total agar lolos audit 15-30 menit TikTok.
 */
data class SessionDnaProfile(
    val sessionIndex: Int,
    val slotCode: String,
    val slotLabel: String,
    val anchorOffsetX: Float,
    val anchorOffsetY: Float,
    val baseZoom: Float,
    val aspectWarpX: Float,
    val aspectWarpY: Float,
    val baseTiltDeg: Float,
    val gamma: Float,
    val contrast: Float,
    val warmth: Float,
    val tint: Float,
    val dctFreqX: Float,
    val dctFreqY: Float,
    val lightDirX: Float,
    val lightDirY: Float,
    val pitchCenterRatio: Float,
    val speedCenterRatio: Float,
    val initialSeekOffsetMs: Int,
    val initialWavePhase: Double,
    val sessionSeed: Long = 1001L
) {
    val slotIndex: Int
        get() = ((sessionIndex - 1) % 8 + 8) % 8

    val codeName: String
        get() = slotCode

    val quadrantLabel: String
        get() = slotLabel

    val badgeText: String
        get() = "Sesi #$sessionIndex ($slotCode)"

    val summaryText: String
        get() = String.format(
            java.util.Locale.US,
            "Zoom +%.1f%% • Pitch %+.1f%% • Tempo %+.1f%%",
            (baseZoom - 1.0f) * 100f,
            (pitchCenterRatio - 1.0f) * 100f,
            (speedCenterRatio - 1.0f) * 100f
        )

    companion object {
        fun forSession(sessionIndex: Int, sessionSeed: Long = 0L): SessionDnaProfile {
            val safeIdx = sessionIndex.coerceAtLeast(1)
            val slot = ((safeIdx - 1) % 8 + 8) % 8
            val effSeed = if (sessionSeed != 0L) sessionSeed else (1000L + safeIdx)
            val rnd = java.util.Random(effSeed xor (safeIdx.toLong() * 0x9E3779B97F4A7C15UL.toLong()))
            val fineX = (rnd.nextFloat() - 0.5f) * 0.0016f
            val fineY = (rnd.nextFloat() - 0.5f) * 0.0016f
            val finePitch = (rnd.nextFloat() - 0.5f) * 0.0035f
            val fineSpeed = (rnd.nextFloat() - 0.5f) * 0.0025f
            val phaseSeed = (safeIdx * 1789.37) + (rnd.nextDouble() * 419.0)

            return when (slot) {
                0 -> SessionDnaProfile(
                    sessionIndex = safeIdx,
                    slotCode = "DNA-A",
                    slotLabel = "Kuadran A • Warm Studio",
                    anchorOffsetX = -0.0055f + fineX,
                    anchorOffsetY = 0.0045f + fineY,
                    baseZoom = 1.014f,
                    aspectWarpX = 1.015f,
                    aspectWarpY = 1.000f,
                    baseTiltDeg = 0.18f,
                    gamma = 0.985f,
                    contrast = 1.018f,
                    warmth = 0.011f,
                    tint = -0.004f,
                    dctFreqX = 8.71f,
                    dctFreqY = 10.33f,
                    lightDirX = -0.60f,
                    lightDirY = 0.60f,
                    pitchCenterRatio = 1.016f + finePitch,
                    speedCenterRatio = 1.006f + fineSpeed,
                    initialSeekOffsetMs = 0,
                    initialWavePhase = phaseSeed,
                    sessionSeed = effSeed
                )
                1 -> SessionDnaProfile(
                    sessionIndex = safeIdx,
                    slotCode = "DNA-B",
                    slotLabel = "Kuadran B • Cool Crisp",
                    anchorOffsetX = 0.0062f + fineX,
                    anchorOffsetY = -0.0052f + fineY,
                    baseZoom = 1.027f,
                    aspectWarpX = 1.000f,
                    aspectWarpY = 1.019f,
                    baseTiltDeg = -0.24f,
                    gamma = 1.022f,
                    contrast = 0.984f,
                    warmth = -0.012f,
                    tint = 0.006f,
                    dctFreqX = 11.47f,
                    dctFreqY = 7.89f,
                    lightDirX = 0.70f,
                    lightDirY = -0.50f,
                    pitchCenterRatio = 0.981f + finePitch,
                    speedCenterRatio = 0.991f + fineSpeed,
                    initialSeekOffsetMs = 1450,
                    initialWavePhase = phaseSeed,
                    sessionSeed = effSeed
                )
                2 -> SessionDnaProfile(
                    sessionIndex = safeIdx,
                    slotCode = "DNA-C",
                    slotLabel = "Kuadran C • Soft Daylight",
                    anchorOffsetX = 0.0050f + fineX,
                    anchorOffsetY = 0.0058f + fineY,
                    baseZoom = 1.021f,
                    aspectWarpX = 1.022f,
                    aspectWarpY = 0.996f,
                    baseTiltDeg = 0.29f,
                    gamma = 0.972f,
                    contrast = 1.026f,
                    warmth = 0.006f,
                    tint = 0.009f,
                    dctFreqX = 7.43f,
                    dctFreqY = 12.61f,
                    lightDirX = 0.50f,
                    lightDirY = 0.70f,
                    pitchCenterRatio = 1.029f + finePitch,
                    speedCenterRatio = 0.994f + fineSpeed,
                    initialSeekOffsetMs = 2400,
                    initialWavePhase = phaseSeed,
                    sessionSeed = effSeed
                )
                3 -> SessionDnaProfile(
                    sessionIndex = safeIdx,
                    slotCode = "DNA-D",
                    slotLabel = "Kuadran D • Cinema Vivid",
                    anchorOffsetX = -0.0064f + fineX,
                    anchorOffsetY = -0.0048f + fineY,
                    baseZoom = 1.032f,
                    aspectWarpX = 0.995f,
                    aspectWarpY = 1.023f,
                    baseTiltDeg = -0.19f,
                    gamma = 1.031f,
                    contrast = 1.014f,
                    warmth = -0.008f,
                    tint = -0.009f,
                    dctFreqX = 13.19f,
                    dctFreqY = 9.17f,
                    lightDirX = -0.70f,
                    lightDirY = -0.60f,
                    pitchCenterRatio = 0.972f + finePitch,
                    speedCenterRatio = 1.012f + fineSpeed,
                    initialSeekOffsetMs = 950,
                    initialWavePhase = phaseSeed,
                    sessionSeed = effSeed
                )
                4 -> SessionDnaProfile(
                    sessionIndex = safeIdx,
                    slotCode = "DNA-E",
                    slotLabel = "Poros Atas • Golden Tone",
                    anchorOffsetX = -0.0022f + fineX,
                    anchorOffsetY = 0.0072f + fineY,
                    baseZoom = 1.018f,
                    aspectWarpX = 1.018f,
                    aspectWarpY = 1.004f,
                    baseTiltDeg = -0.31f,
                    gamma = 0.991f,
                    contrast = 0.979f,
                    warmth = 0.014f,
                    tint = 0.004f,
                    dctFreqX = 10.13f,
                    dctFreqY = 14.07f,
                    lightDirX = 0.0f,
                    lightDirY = 0.85f,
                    pitchCenterRatio = 1.023f + finePitch,
                    speedCenterRatio = 1.015f + fineSpeed,
                    initialSeekOffsetMs = 1900,
                    initialWavePhase = phaseSeed,
                    sessionSeed = effSeed
                )
                5 -> SessionDnaProfile(
                    sessionIndex = safeIdx,
                    slotCode = "DNA-F",
                    slotLabel = "Poros Bawah • Crystal",
                    anchorOffsetX = 0.0028f + fineX,
                    anchorOffsetY = -0.0070f + fineY,
                    baseZoom = 1.029f,
                    aspectWarpX = 1.003f,
                    aspectWarpY = 1.021f,
                    baseTiltDeg = 0.25f,
                    gamma = 1.016f,
                    contrast = 1.029f,
                    warmth = -0.014f,
                    tint = -0.005f,
                    dctFreqX = 14.53f,
                    dctFreqY = 8.29f,
                    lightDirX = 0.0f,
                    lightDirY = -0.85f,
                    pitchCenterRatio = 0.986f + finePitch,
                    speedCenterRatio = 0.987f + fineSpeed,
                    initialSeekOffsetMs = 2850,
                    initialWavePhase = phaseSeed,
                    sessionSeed = effSeed
                )
                6 -> SessionDnaProfile(
                    sessionIndex = safeIdx,
                    slotCode = "DNA-G",
                    slotLabel = "Poros Kiri • Velvet",
                    anchorOffsetX = -0.0074f + fineX,
                    anchorOffsetY = 0.0018f + fineY,
                    baseZoom = 1.024f,
                    aspectWarpX = 1.024f,
                    aspectWarpY = 1.002f,
                    baseTiltDeg = 0.15f,
                    gamma = 0.978f,
                    contrast = 0.988f,
                    warmth = 0.008f,
                    tint = -0.011f,
                    dctFreqX = 6.89f,
                    dctFreqY = 11.71f,
                    lightDirX = -0.85f,
                    lightDirY = 0.15f,
                    pitchCenterRatio = 1.034f + finePitch,
                    speedCenterRatio = 1.003f + fineSpeed,
                    initialSeekOffsetMs = 1200,
                    initialWavePhase = phaseSeed,
                    sessionSeed = effSeed
                )
                else -> SessionDnaProfile(
                    sessionIndex = safeIdx,
                    slotCode = "DNA-H",
                    slotLabel = "Poros Kanan • Pure",
                    anchorOffsetX = 0.0074f + fineX,
                    anchorOffsetY = -0.0019f + fineY,
                    baseZoom = 1.034f,
                    aspectWarpX = 1.002f,
                    aspectWarpY = 1.025f,
                    baseTiltDeg = -0.27f,
                    gamma = 1.026f,
                    contrast = 1.021f,
                    warmth = -0.006f,
                    tint = 0.011f,
                    dctFreqX = 12.31f,
                    dctFreqY = 6.77f,
                    lightDirX = 0.85f,
                    lightDirY = -0.15f,
                    pitchCenterRatio = 0.976f + finePitch,
                    speedCenterRatio = 1.018f + fineSpeed,
                    initialSeekOffsetMs = 2150,
                    initialWavePhase = phaseSeed,
                    sessionSeed = effSeed
                )
            }
        }
    }
}

data class VideoCodecRisk(
    val level: RiskLevel,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateBps: Long,
    val isHighRamRisk: Boolean,
    val warningMessage: String
) {
    enum class RiskLevel { SAFE, WARNING, DANGER }
}

enum class VideoRole(val label: String, val shortLabel: String, val colorHex: String) {
    BRIDGE_1("Bridge I (Intro / Selesai Konten)", "Bridge I", "#38BDF8"),
    MASTER_LOOP("Master Loop (Standby Looping)", "Master Loop", "#F59E0B"),
    BRIDGE_2("Bridge II (Transisi Komen)", "Bridge II", "#E879F9"),
    CONTENT("Video Konten (Pemicu Komen)", "Konten", "#34D399")
}

enum class BridgeCyclePhase(val label: String, val badge: String) {
    OFF("Nonaktif", "OFF"),
    BRIDGE_1("Bridge I (Intro / Selesai Konten)", "Bridge I"),
    MASTER_LOOP("Master Loop (Standby Looping)", "Master Loop"),
    BRIDGE_2("Bridge II (Transisi Komen)", "Bridge II"),
    CONTENT_VIDEO("Video Konten (Demo / Spill)", "Konten")
}

data class VideoItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val uri: String? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val width: Int = 1280,
    val height: Int = 720,
    val isMuted: Boolean = false,
    val isEnabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
    val isMaster: Boolean = false,
    val isBridge1: Boolean = false,
    val isBridge2: Boolean = false,
    val spillKeyword: String = ""
)

data class AudioItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val uri: String? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val isMuted: Boolean = false,
    val isEnabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

data class TargetAppInfo(
    val packageName: String,
    val appName: String,
    val isSelected: Boolean = false,
    val isSystemApp: Boolean = false,
    val category: String = "Lainnya"
)

data class MediaMetadata(
    val title: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String
)

enum class DecoyProfile(val label: String, val badge: String) {
    NONE("Normal 100% (Bebas Bug Decoy / VIP)", "OWNER"),
    RANDOM_GLITCH("Freeze Dinamis Acak 1–N Putaran", "FREEZE"),
    MOCHI_BLIND("Freeze Dinamis (1–N Putaran)", "FREEZE"),
    LAG_ONLY("Freeze Dinamis Acak 1–N Putaran", "FREEZE"),
    AUDIO_ONLY("Freeze Dinamis + Audio Mati (1–N Putaran)", "FREEZE"),
    GPU_CORRUPT("Freeze Dinamis Layar (1–N Putaran)", "FREEZE"),
    FLIP_ZOOM("Freeze Dinamis Kamera (1–N Putaran)", "FREEZE")
}

data class DecoyConfig(
    val isDecoy: Boolean = false,
    val profile: DecoyProfile = DecoyProfile.NONE,
    val freezeAfterLoopCount: Int = 4,
    val smoothMinutes: Int = 1,
    val severeMinutes: Int = 5,
    val forceContinuousSevere: Boolean = false,
    val minIntervalMinutes: Int = 5,
    val maxIntervalMinutes: Int = 1,
    val minIntervalSeconds: Int = 4,
    val maxIntervalSeconds: Int = 9,
    val isMochiBlind: Boolean = false
)

data class LicenseInfo(
    val deviceId: String,
    val refNo: String,
    val planName: String,
    val expiryTimestamp: Long,
    val activationKey: String,
    val activatedAt: Long
) {
    val isLifetime: Boolean get() = expiryTimestamp == 0L || expiryTimestamp > 4102444800000L
    val isExpired: Boolean get() = !isLifetime && System.currentTimeMillis() > expiryTimestamp
    val remainingDays: Long get() {
        if (isLifetime) return 9999L
        val diff = expiryTimestamp - System.currentTimeMillis()
        if (diff <= 0) return 0L
        return ((diff + 86_399_999L) / 86_400_000L).coerceAtLeast(1L)
    }

    val remainingTimeFormatted: String
        get() {
            if (isLifetime) return "Permanen (Selamanya)"
            val diff = expiryTimestamp - System.currentTimeMillis()
            if (diff <= 0) return "Kedaluwarsa"
            val days = diff / 86_400_000L
            val hours = (diff % 86_400_000L) / 3_600_000L
            val mins = (diff % 3_600_000L) / 60_000L
            return when {
                days > 0 -> "${days} Hari ${hours} Jam"
                hours > 0 -> "${hours} Jam ${mins} Menit"
                else -> "${mins.coerceAtLeast(1)} Menit"
            }
        }

    /**
     * Checks if this license key explicitly carries the VIP Clean / No-Decoy bypass flag
     * granted by the Owner to selected trusted users.
     */
    val isVipDecoyBypassedKey: Boolean
        get() {
            val upper = planName.uppercase()
            return upper.contains("NODEC") || upper.contains("VIPCLEAN") || upper.contains("OWNER")
        }

    val displayPlanName: String
        get() {
            var base = planName
            val idx = base.indexOf("_DEC")
            if (idx >= 0) {
                base = base.substring(0, idx)
            }
            base = base
                .replace("_NODEC", "")
                .replace("_VIPCLEAN", "")
                .replace("_SEC_V2", "")
                .replace("_V2", "")
                .replace("_SEC", "")
            return when {
                base.contains("LIFETIME") -> "LIFETIME PRO"
                base.startsWith("PRO_") && base.endsWith("H") -> {
                    val hrs = base.removePrefix("PRO_").removeSuffix("H")
                    "PRO $hrs Jam"
                }
                base.startsWith("PRO_") -> {
                    val days = base.removePrefix("PRO_").removeSuffix("D")
                    "PRO $days Hari"
                }
                else -> base
            }
        }

    val decoyConfig: DecoyConfig
        get() {
            val upper = planName.uppercase()
            val hasDecoyTag = (upper.contains("DEC") || upper.contains("DECOY")) && !upper.contains("NODEC")
            if (!hasDecoyTag || isVipDecoyBypassedKey) {
                return DecoyConfig(isDecoy = false, profile = DecoyProfile.NONE, isMochiBlind = false)
            }
            val profile = when {
                upper.contains("MOCHI") || upper.contains("BLIND") -> DecoyProfile.MOCHI_BLIND
                upper.contains("LAG") -> DecoyProfile.LAG_ONLY
                upper.contains("AUD") -> DecoyProfile.AUDIO_ONLY
                upper.contains("GPU") || upper.contains("GRN") -> DecoyProfile.GPU_CORRUPT
                upper.contains("FLP") || upper.contains("FLIP") -> DecoyProfile.FLIP_ZOOM
                else -> DecoyProfile.RANDOM_GLITCH
            }
            var severeM = 5
            var smoothM = 1
            val regex = Regex("""(?:DEC|DECOY)(?:_[A-Z]+)?_(\d+)_(\d+)""")
            val match = regex.find(upper)
            if (match != null) {
                val p1 = match.groupValues[1].toIntOrNull() ?: 5
                val p2 = match.groupValues[2].toIntOrNull() ?: 1
                severeM = p1.coerceAtLeast(1)
                smoothM = p2.coerceAtLeast(1)
            }
            return DecoyConfig(
                isDecoy = true,
                profile = profile,
                smoothMinutes = smoothM,
                severeMinutes = severeM,
                forceContinuousSevere = false,
                minIntervalMinutes = severeM,
                maxIntervalMinutes = smoothM,
                minIntervalSeconds = 4,
                maxIntervalSeconds = 9,
                isMochiBlind = false
            )
        }
}

sealed class LicenseStatus {
    object Unlicensed : LicenseStatus()
    data class Active(val license: LicenseInfo) : LicenseStatus()
    data class Expired(val license: LicenseInfo) : LicenseStatus()
    data class Invalid(val reason: String) : LicenseStatus()
}

sealed class ActivationResult {
    data class Success(val license: LicenseInfo, val message: String) : ActivationResult()
    data class Error(val message: String) : ActivationResult()
}

data class RentalRecord(
    val deviceId: String,
    val refNo: String,
    val durationDays: Int,
    val createdAt: Long,
    val expiryTimestamp: Long,
    val activationKey: String
)

data class VcamConfig(
    val isMasterEnabled: Boolean = true,
    val videoPath: String = "",
    val videoUri: String? = null,
    val audioPath: String = "",
    val audioUri: String? = null,
    val audioName: String = "",
    val playlist: List<VideoItem> = emptyList(),
    val currentVideoIndex: Int = 0,
    val audioPlaylist: List<AudioItem> = emptyList(),
    val currentAudioIndex: Int = 0,
    val rotation: Int = 0,
    val isMirrored: Boolean = false,
    val isVerticalFlipped: Boolean = false,
    val scaleMode: ScaleMode = ScaleMode.CROP,
    val zoomFactor: Float = 1.0f,
    val audioEnabled: Boolean = true,
    val loopPlayback: Boolean = true,
    val autoNextEnabled: Boolean = true,
    val autoNextMode: AutoNextMode = AutoNextMode.SEQUENTIAL,
    val autoNextLeadSeconds: Int = 2,
    val autoNextLeadMs: Int = 2000,
    val forceResolution: ResolutionPreset = ResolutionPreset.AUTO,
    val customWidth: Int = 1280,
    val customHeight: Int = 720,
    val fps: Int = 30,
    val targetPackages: Set<String> = emptySet(),
    val primaryTargetPackage: String = "",
    val isFloatingControlEnabled: Boolean = false,
    val floatingAlpha: Float = 0.86f,
    val isVideoPaused: Boolean = false,
    val isVideoAudioMuted: Boolean = false,
    val isMp3AudioPaused: Boolean = true,
    val isMp3AudioMuted: Boolean = false,
    val hookEngine: HookEngineType = HookEngineType.HYBRID,
    val videoSwitchMode: VideoSwitchMode = VideoSwitchMode.INSTANT,
    val isLightweightMode: Boolean = false,
    val isDebugOsdInVirtualCameraEnabled: Boolean = false,
    val pendingVideoIndex: Int? = null,
    val isMasterSpillMode: Boolean = false,
    val masterVideoId: String? = null,
    val masterSpillWaitFinish: Boolean = true,
    val activeSpillVideoId: String? = null,
    val autoSpillFromComments: Boolean = false,
    val isBridgeCycleMode: Boolean = false,
    val isIfsMode: Boolean = false,
    val bridge1VideoId: String? = null,
    val bridge2VideoId: String? = null,
    val currentBridgePhase: BridgeCyclePhase = BridgeCyclePhase.OFF,
    val pendingBridgeContentIndex: Int? = null,
    val bridgeContentQueue: List<Int> = emptyList(),
    val enableSalesNotifModule: Boolean = false,
    val enableStickerModule: Boolean = false,
    val enableWatermarkModule: Boolean = false,
    val enableAutoTimeModule: Boolean = false,
    val enablePrayerCommentModule: Boolean = false,
    val enableQuranKhatamModule: Boolean = false,
    val enableAutoTapModule: Boolean = false,
    val enableMp3MusicModule: Boolean = true,
    val enableOsdModule: Boolean = false,
    val enableQuranAudioModule: Boolean = false,
    val enableMakkahRelayModule: Boolean = false,
    val enableLiveEventBrokerModule: Boolean = true,
    val enableScanInspectorModule: Boolean = false,
    val isLiveFocusVpnEnabled: Boolean = false,
    val isAutoRamCleanEnabled: Boolean = true,
    val fpsGovernorLimit: Int = 30,
    val lastFreedRamMb: Long = 0L,
    val lastRamCleanTimestamp: Long = 0L,
    val ramCleanWhitelistPackages: Set<String> = emptySet(),
    val vpnBypassPackages: Set<String> = emptySet(),
    val blockedAdPackages: Set<String> = emptySet(),
    val isAdBlockerActive: Boolean = false,
    val adBlockerAutoFreeze: Boolean = true,
    val antiHashMode: AntiHashIntensity = AntiHashIntensity.OFF,
    val isSafeCodecGuardActive: Boolean = true,
    val isAudioAntiHashEnabled: Boolean = false,
    val audioAntiHashMode: AudioAntiHashIntensity = AudioAntiHashIntensity.MEDIUM,
    val voiceEffect: VoiceEffect = VoiceEffect.NORMAL,
    val isAutoRocketRecoveryEnabled: Boolean = true,
    val autoRecoveryStallThresholdSec: Int = 4,
    val autoRecoveryCount: Int = 0,
    val lastAutoRecoveryTimestamp: Long = 0L,
    val isPeriodicRamCleanEnabled: Boolean = true,
    val periodicRamCleanIntervalMinutes: Int = 30,
    val sessionDnaIndex: Int = 1,
    val sessionDnaSeed: Long = 1001L,
    val isDualAudioMixingEnabled: Boolean = true,
    val isIndependentAudioEnabled: Boolean = false,
    val isHumanVolumeFluctuationEnabled: Boolean = false,
    val humanFluctuationIntensity: Float = 0.20f,
    val isAudioLayer2Enabled: Boolean = false,
    val audioLayer2Id: String? = null,
    val audioLayer2Path: String = "",
    val audioLayer2Name: String = "",
    val audioLayer2Volume: Float = 0.70f,
    val isAudioLayer2Muted: Boolean = false,
    val isAudioLayer2Looping: Boolean = true,
    val isAudioLayer3Enabled: Boolean = false,
    val audioLayer3Ids: Set<String> = emptySet(),
    val audioLayer3Volume: Float = 0.80f,
    val isAudioLayer3Muted: Boolean = false,
    val audioLayer3MinIntervalSec: Int = 10,
    val audioLayer3MaxIntervalSec: Int = 25,
    val enableDnaModule: Boolean = false,
    val enableAiDetectionModule: Boolean = false,
    val videoAntiHashPercent: Float = 1.5f,
    val audioAntiHashPercent: Float = 2.0f,
    val isAudioLayer2SyncAntiHash: Boolean = true,
    val isDnaZoomEnabled: Boolean = false,
    val sessionDnaCustomZoom: Float = 1.08f,
    val sessionDnaPanX: Float = 0.0f,
    val sessionDnaPanY: Float = 0.0f,
    val usedDnaZoomHistory: Set<String> = emptySet(),
    val isLiveStreamSourceActive: Boolean = false,
    val liveStreamUrl: String = "https://makkahlive.net/makkahlive.aspx",
    val liveStreamTitle: String = "Makkah Live 24 Jam (Masjidil Haram)",
    val liveStreamStatus: String = "STANDBY"
) {
    val maxSafePan: Float
        get() = FramingGuardMath.calculateSafePanLimit(sessionDnaCustomZoom)
    val activeSessionDna: SessionDnaProfile
        get() = SessionDnaProfile.forSession(sessionDnaIndex, sessionDnaSeed)
    val currentVideo: VideoItem?
        get() = if (playlist.isNotEmpty() && currentVideoIndex in playlist.indices) playlist[currentVideoIndex] else null

    fun isExcludedFromType1(item: AudioItem): Boolean {
        return item.id == audioLayer2Id ||
               (audioLayer2Path.isNotBlank() && item.path == audioLayer2Path) ||
               item.id in audioLayer3Ids
    }

    val type1Playlist: List<AudioItem>
        get() = audioPlaylist.filterNot { isExcludedFromType1(it) }

    val layer3RingtoneList: List<AudioItem>
        get() = audioPlaylist.filter { it.id in audioLayer3Ids }

    val currentAudio: AudioItem?
        get() {
            val list = type1Playlist
            if (list.isEmpty()) return null
            return if (currentAudioIndex in list.indices) list[currentAudioIndex] else list.firstOrNull()
        }

    val currentAudioLayer2: AudioItem?
        get() {
            if (!audioLayer2Id.isNullOrBlank()) {
                val found = audioPlaylist.firstOrNull { it.id == audioLayer2Id }
                if (found != null) return found
            }
            if (audioLayer2Path.isNotBlank()) {
                val foundByPath = audioPlaylist.firstOrNull { it.path == audioLayer2Path }
                if (foundByPath != null) return foundByPath
                return AudioItem(
                    id = "layer2_custom",
                    name = audioLayer2Name.ifBlank { "Audio Tipe 2" },
                    path = audioLayer2Path
                )
            }
            return null
        }

    val isPaused: Boolean
        get() = isVideoPaused

    val isAudioPaused: Boolean
        get() = isMp3AudioPaused

    val masterVideoIndex: Int
        get() {
            if (playlist.isEmpty()) return 0
            if (!masterVideoId.isNullOrBlank()) {
                val idx = playlist.indexOfFirst { it.id == masterVideoId }
                if (idx >= 0) return idx
            }
            val explicit = playlist.indexOfFirst { it.isMaster }
            if (explicit >= 0) return explicit
            // Auto-detect by name containing "master"
            val byName = playlist.indexOfFirst { it.name.lowercase().contains("master") }
            if (byName >= 0) return byName
            return if (playlist.size > 1) 1 else 0
        }

    val masterVideo: VideoItem?
        get() {
            if (playlist.isEmpty()) return null
            return playlist.getOrNull(masterVideoIndex) ?: playlist.firstOrNull()
        }

    val bridge1VideoIndex: Int
        get() {
            if (playlist.isEmpty()) return 0
            if (!bridge1VideoId.isNullOrBlank()) {
                val idx = playlist.indexOfFirst { it.id == bridge1VideoId }
                if (idx >= 0) return idx
            }
            val explicit = playlist.indexOfFirst { it.isBridge1 }
            if (explicit >= 0) return explicit
            val byName = playlist.indexOfFirst { item ->
                val n = item.name.lowercase()
                n.contains("bridge 1") || n.contains("bridge i") || n.contains("bridge1") || n.contains("b1") || n.contains("intro")
            }
            if (byName >= 0) return byName
            return 0
        }

    val bridge1Video: VideoItem?
        get() {
            if (playlist.isEmpty()) return null
            return playlist.getOrNull(bridge1VideoIndex) ?: playlist.firstOrNull()
        }

    val bridge2VideoIndex: Int
        get() {
            if (playlist.isEmpty()) return 0
            if (!bridge2VideoId.isNullOrBlank()) {
                val idx = playlist.indexOfFirst { it.id == bridge2VideoId }
                if (idx >= 0) return idx
            }
            val explicit = playlist.indexOfFirst { it.isBridge2 }
            if (explicit >= 0) return explicit
            val byName = playlist.indexOfFirst { item ->
                val n = item.name.lowercase()
                n.contains("bridge 2") || n.contains("bridge ii") || n.contains("bridge2") || n.contains("b2") || n.contains("respon")
            }
            if (byName >= 0) return byName
            val b1 = bridge1VideoIndex
            val m = masterVideoIndex
            val other = playlist.indices.firstOrNull { it != b1 && it != m }
            return other ?: (if (playlist.size > 2) 2 else if (playlist.size > 1) 1 else 0)
        }

    val bridge2Video: VideoItem?
        get() {
            if (playlist.isEmpty()) return null
            return playlist.getOrNull(bridge2VideoIndex) ?: playlist.firstOrNull()
        }

    val contentVideos: List<Pair<Int, VideoItem>>
        get() {
            val b1 = bridge1VideoIndex
            val m = masterVideoIndex
            val b2 = bridge2VideoIndex
            return playlist.mapIndexed { idx, item -> Pair(idx, item) }
                .filter { (idx, _) -> idx != b1 && idx != m && idx != b2 }
        }

    val isCurrentPlayingMaster: Boolean
        get() {
            val cur = currentVideo ?: return false
            val m = masterVideo ?: return false
            return cur.id == m.id
        }

    val isCurrentPlayingSpill: Boolean
        get() = isMasterSpillMode && playlist.isNotEmpty() && !isCurrentPlayingMaster

    fun getVideoRole(videoId: String): VideoRole {
        val b1Id = bridge1Video?.id
        val mId = masterVideo?.id
        val b2Id = bridge2Video?.id
        return when (videoId) {
            b1Id -> VideoRole.BRIDGE_1
            mId -> VideoRole.MASTER_LOOP
            b2Id -> VideoRole.BRIDGE_2
            else -> VideoRole.CONTENT
        }
    }

    val isBridgeCycleLoopingNative: Boolean
        get() {
            if (!isBridgeCycleMode) return false
            // Only Master Loop with NO pending trigger loops natively!
            return currentBridgePhase == BridgeCyclePhase.MASTER_LOOP && pendingBridgeContentIndex == null && bridgeContentQueue.isEmpty()
        }

    val isMasterSpillLoopingNative: Boolean
        get() {
            if (!isMasterSpillMode) return false
            // Hanya video Master tanpa antrean spill yang boleh loop secara seamless
            return isCurrentPlayingMaster && pendingVideoIndex == null
        }
}

/**
 * Hasil kalkulasi variasi framing sesi unik bebas celah hitam.
 */
data class FramingCalculatorResult(
    val zoomLevelsCount: Int,
    val panPositionsPerZoomAvg: Int,
    val totalUniqueSessions: Int,
    val safePanLimitMin: Float,
    val safePanLimitMax: Float
)

/**
 * Rumus matematis Safe Framing Envelope Guard:
 * Mencegah 100% tepi atau sudut hitam (hitam overlap) ketika video digeser (Pan X / Pan Y).
 */
object FramingGuardMath {
    /**
     * Menghitung batas pergeseran maksimum (Pan X/Y) yang dijamin 100% bebas dari celah/sudut hitam.
     * Rumus: maxPan = (Zoom - 1.0) * 0.85f (faktor keamanan 85% untuk rotasi mikro & breathing).
     */
    fun calculateSafePanLimit(zoom: Float): Float {
        val effZ = zoom.coerceAtLeast(1.0f)
        return ((effZ - 1.0f) * 0.85f).coerceAtLeast(0.0f)
    }

    /**
     * Menghitung kombinasi sesi framing unik yang sah dan bebas dari sudut hitam.
     */
    fun calculateCombinations(
        minZoom: Float = 1.05f,
        maxZoom: Float = 1.40f,
        stepZoom: Float = 0.02f,
        stepPan: Float = 0.015f
    ): FramingCalculatorResult {
        val safeMinZ = minZoom.coerceAtLeast(1.04f)
        val safeMaxZ = maxZoom.coerceAtLeast(safeMinZ)
        val safeStepZ = stepZoom.coerceAtLeast(0.005f)
        val safeStepP = stepPan.coerceAtLeast(0.005f)

        var zoomSteps = 0
        var totalCombos = 0
        var minLimit = Float.MAX_VALUE
        var maxLimit = 0.0f

        var currentZ = safeMinZ
        while (currentZ <= safeMaxZ + 0.0001f) {
            zoomSteps++
            val limit = calculateSafePanLimit(currentZ)
            if (limit < minLimit) minLimit = limit
            if (limit > maxLimit) maxLimit = limit

            val positionsPerAxis = ((limit * 2.0f) / safeStepP).toInt().coerceAtLeast(1)
            totalCombos += (positionsPerAxis * positionsPerAxis)
            currentZ += safeStepZ
        }

        val avgPan = if (zoomSteps > 0) totalCombos / zoomSteps else 1
        return FramingCalculatorResult(
            zoomLevelsCount = zoomSteps,
            panPositionsPerZoomAvg = avgPan,
            totalUniqueSessions = totalCombos.coerceAtLeast(1),
            safePanLimitMin = if (minLimit == Float.MAX_VALUE) 0.0f else minLimit,
            safePanLimitMax = maxLimit
        )
    }
}

/**
 * Snapshot parameter Anti-Deteksi (Visual & Akustik) khusus Mode SV4.
 * Dijaga ketat agar dalam rentang waktu 2 menit (120 detik) tidak ada profil yang kembar/berulang.
 */
data class Sv4AntiDetekSnapshot(
    val zoom: Float,
    val panX: Float,
    val panY: Float,
    val dnaIndex: Int,
    val sessionSeed: Long,
    val pitchCenterRatio: Float,
    val speedCenterRatio: Float,
    val timestampMs: Long
)

