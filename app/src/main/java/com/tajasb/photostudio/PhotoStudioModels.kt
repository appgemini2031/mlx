package com.tajasb.photostudio

enum class PhotoAnimEffect(
    val id: String,
    val title: String,
    val icon: String,
    val description: String
) {
    NONE(
        id = "NONE",
        title = "Tanpa Efek",
        icon = "🖼️",
        description = "Foto Ken Burns Zoom Halus saja"
    ),
    RAIN(
        id = "RAIN",
        title = "Efek Hujan",
        icon = "🌧️",
        description = "Rintik hujan malam sinematik + ripple bawah"
    ),
    SNOW(
        id = "SNOW",
        title = "Efek Salju",
        icon = "❄️",
        description = "Butiran salju turun lembut dengan goyangan horizontal"
    ),
    SHOOTING_STAR(
        id = "SHOOTING_STAR",
        title = "Efek Bintang Jatuh",
        icon = "🌠",
        description = "Langit berbintang berkedip + meteor jatuh berjejak cahaya"
    ),
    RAIN_SHOOTING_STAR(
        id = "RAIN_SHOOTING_STAR",
        title = "Hujan + Bintang Jatuh",
        icon = "🌌",
        description = "Kombinasi suasana malam berbintang & hujan"
    ),
    SAKURA(
        id = "SAKURA",
        title = "Kelopak Sakura",
        icon = "🌸",
        description = "Kelopak bunga sakura merah muda berjatuhan lembut"
    ),
    FIRE_EMBERS(
        id = "FIRE_EMBERS",
        title = "Bara Api / Fire Embers",
        icon = "🔥",
        description = "Percikan bara api menyala naik dari bawah ke atas"
    ),
    FIREFLIES(
        id = "FIREFLIES",
        title = "Kunang-Kunang Hutan",
        icon = "✨",
        description = "Cahaya partikel kuning-hijau bersinar berkedip melayang"
    ),
    THUNDERSTORM(
        id = "THUNDERSTORM",
        title = "Badai Petir & Hujan",
        icon = "⛈️",
        description = "Hujan deras + kilatan cahaya petir dramatis berkala"
    ),
    FLOATING_HEARTS(
        id = "FLOATING_HEARTS",
        title = "Hati Melayang",
        icon = "💖",
        description = "Partikel hati merah/pink melayang naik dengan lembut"
    ),
    AURORA(
        id = "AURORA",
        title = "Kabut Aurora",
        icon = "🌈",
        description = "Gelombang cahaya aurora hijau-ungu-biru sinematik di langit"
    ),
    AUTUMN_LEAVES(
        id = "AUTUMN_LEAVES",
        title = "Daun Gugur / Autumn",
        icon = "🍂",
        description = "Daun musim gugur oranye-emas berjatuhan"
    ),
    GOLD_BOKEH(
        id = "GOLD_BOKEH",
        title = "Cahaya Bokeh Emas",
        icon = "🟡",
        description = "Partikel cahaya bokeh emas mewah melayang lembut"
    ),
    NEON_PULSE_FRAME(
        id = "NEON_PULSE_FRAME",
        title = "Bingkai Neon Pulse",
        icon = "💠",
        description = "Garis tepi bingkai cahaya cyan-magenta berdenyut futuristik"
    );

    companion object {
        fun fromId(id: String): PhotoAnimEffect {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: RAIN
        }
    }
}

enum class ImageMotionSpeed(
    val id: String,
    val label: String,
    val shortLabel: String,
    val multiplier: Float
) {
    SLOW_05("SLOW_05", "0.5x (Lambat)", "0.5x Lambat", 0.5f),
    NORMAL_10("NORMAL_10", "1.0x (Normal)", "1.0x Normal", 1.0f),
    FAST_15("FAST_15", "1.5x (Cepat)", "1.5x Cepat", 1.5f),
    TURBO_20("TURBO_20", "2.0x (Kilat)", "2.0x Kilat", 2.0f),
    SUPER_30("SUPER_30", "3.0x (Super)", "3.0x Super", 3.0f);

    companion object {
        fun fromId(id: String): ImageMotionSpeed {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NORMAL_10
        }
    }
}

enum class EffectAnimSpeed(
    val id: String,
    val label: String,
    val multiplier: Float
) {
    SLOW_05("SLOW_05", "0.5x", 0.5f),
    NORMAL_10("NORMAL_10", "1.0x", 1.0f),
    FAST_15("FAST_15", "1.5x", 1.5f),
    TURBO_20("TURBO_20", "2.0x", 2.0f);

    companion object {
        fun fromId(id: String): EffectAnimSpeed {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NORMAL_10
        }
    }
}

enum class OutputDurationPreset(
    val label: String,
    val seconds: Int
) {
    SEC_5("5d", 5),
    SEC_8("8d", 8),
    SEC_10("10d", 10),
    SEC_15("15d", 15),
    SEC_30("30d", 30),
    SEC_60("60d", 60);

    companion object {
        fun fromSeconds(sec: Int): OutputDurationPreset {
            return entries.firstOrNull { it.seconds == sec } ?: SEC_10
        }
    }
}

data class SelectedStudioPhoto(
    val id: String,
    val uriString: String,
    val displayName: String
)

data class PhotoStudioState(
    val isExpanded: Boolean = false,
    val selectedPhotos: List<SelectedStudioPhoto> = emptyList(),
    val selectedEffect: PhotoAnimEffect = PhotoAnimEffect.RAIN,
    val downloadedEffectIds: Set<String> = setOf(PhotoAnimEffect.NONE.id, PhotoAnimEffect.RAIN.id),
    val imageSpeed: ImageMotionSpeed = ImageMotionSpeed.NORMAL_10,
    val effectSpeed: EffectAnimSpeed = EffectAnimSpeed.NORMAL_10,
    val durationPreset: OutputDurationPreset = OutputDurationPreset.SEC_10,
    val isRendering: Boolean = false,
    val renderProgressPercent: Int = 0,
    val renderedFrames: Int = 0,
    val totalFrames: Int = 0,
    val statusMessage: String = "Pilih 1 atau banyak foto lalu gabungkan dengan efek animasi ke MP4 VCam.",
    val lastRenderedMp4Path: String? = null,
    val lastSavedPublicPath: String? = null,
    val savedPresetSummary: String? = null
)
