package com.tajasb.eventrelay

import java.util.UUID

enum class LiveEventType {
    CHAT,          // Komentar & Doa penonton
    GIFT,          // Hadiah / Saweran
    LIKE,          // Tap like
    MEMBER,        // Penonton baru masuk / join
    FOLLOW,        // Follower baru
    SHARE,         // Membagikan siaran
    SHOP_ORDER,    // 🛍️ Event Jualan: Pembeli Checkout / Order Keranjang Kuning
    PIN_PRODUCT,   // 📌 Event Jualan: Host Pin Produk / Etalase
    ROOM_STATS,    // Statistik penonton aktif realtime
    BROKER_STATUS  // Heartbeat / status broker
}

enum class LivePlatform {
    TIKTOK,
    BIGO
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class LiveEventItem(
    val id: String = UUID.randomUUID().toString(),
    val type: LiveEventType,
    val platform: String = "TIKTOK", // "TIKTOK" atau "BIGO"
    val timestamp: Long = System.currentTimeMillis(),
    val targetUser: String = "",
    val senderName: String = "",
    val nickname: String = "",
    val text: String = "",
    val giftName: String = "",
    val giftCount: Int = 1,
    val diamondCount: Int = 1,
    val likeCount: Int = 1,
    val totalLikes: Int = 0,
    val viewerCount: Int = 0,
    val avatarUrl: String = "",
    // Field Tambahan Khusus Event Jualan (TikTok Shop / E-Commerce)
    val productName: String = "",
    val productPrice: String = "",
    val productQuantity: Int = 1,
    val productId: String = ""
)

data class LiveEventConfig(
    val serverHost: String = "192.168.43.1",
    val serverPort: Int = 8765,
    val autoConnect: Boolean = true,
    val activePlatform: String = "TIKTOK", // "TIKTOK" atau "BIGO"
    val targetUsername: String = "",
    val backupUsernames: List<String> = emptyList(),
    val targetBigoId: String = "",
    val backupBigoIds: List<String> = emptyList(),
    // Saklar Mandiri Setiap Event (User Controlled)
    val enableComment: Boolean = true,      // 💬 Saklar Komentar & Doa
    val enableGift: Boolean = true,         // 🎁 Saklar Gift & Saweran
    val enableLike: Boolean = true,         // ❤️ Saklar Tap Like
    val enableJoin: Boolean = true,         // 👋 Saklar Sambutan Join
    val enableFollow: Boolean = true,       // ➕ Saklar Follow Baru
    val enableShare: Boolean = true,        // 🔄 Saklar Share Live
    val enableViewerCount: Boolean = true,  // 👥 Saklar Indikator Penonton
    val enableShopOrder: Boolean = true,    // 🛍️ Saklar Jualan: Pembeli Checkout
    val enablePinProduct: Boolean = true,   // 📌 Saklar Jualan: Host Pin Produk
    // Konfigurasi Tambahan
    val bannerDurationMs: Long = 4000L,
    val customDoaPrefix: String = "Doa",
    val customGiftPrefix: String = "Saweran",
    val customJoinPrefix: String = "Ahlan",
    val customFollowPrefix: String = "Syukron",
    val customSharePrefix: String = "Share",
    // Pengaturan Pemisahan Overlay (Default: Tengah Frame & Rata Kiri-Kanan Proporsional)
    val separateOverlays: Boolean = true,
    val likePosXpct: Float = 0.50f,
    val likePosYpct: Float = 0.78f,
    val likeScale: Float = 1.0f,
    val likeDurationMs: Long = 2400L,
    val likeFontSizeSp: Float = 16.0f,
    val giftPosXpct: Float = 0.50f,
    val giftPosYpct: Float = 0.26f,
    val giftScale: Float = 1.0f,
    val giftDurationMs: Long = 4200L,
    val giftFontSizeSp: Float = 17.0f,
    val commentPosXpct: Float = 0.50f,
    val commentPosYpct: Float = 0.50f,
    val commentScale: Float = 1.0f,
    val commentDurationMs: Long = 3800L,
    val commentFontSizeSp: Float = 16.5f,
    // Analitik Overlay Event Real-Time (Default: Tengah Atas & Rata Full Kiri-Kanan Frame)
    val enableEventStatsOverlay: Boolean = false,
    val showDiamondsInAnalytics: Boolean = false, // Tutup diamond secara default!
    val eventStatsPosXpct: Float = 0.50f,
    val eventStatsPosYpct: Float = 0.08f,
    val eventStatsScale: Float = 1.0f,
    val eventStatsFontSizeSp: Float = 13.5f,
    val eventStatsWidthPct: Float = 0.98f,
    // 🫧 Animasi Foto Profil Penonton Bulat Melayang (Floating Viewer Avatar Bubbles)
    val enableAvatarBubble: Boolean = true,
    val avatarOnChat: Boolean = true,
    val avatarOnGift: Boolean = true,
    val avatarOnLike: Boolean = true,
    val avatarOnJoin: Boolean = true,
    val avatarOnFollow: Boolean = true,
    val avatarOnShare: Boolean = true,
    val avatarSizeScale: Float = 1.0f,
    val avatarSizeChat: Float = 1.0f,
    val avatarSizeGift: Float = 1.30f,
    val avatarSizeLike: Float = 0.85f,
    val avatarSizeJoin: Float = 0.95f,
    val avatarSizeFollow: Float = 1.15f,
    val avatarSizeShare: Float = 1.10f,
    val avatarFlightDurationMs: Long = 4200L,
    val avatarDurationChat: Long = 4200L,
    val avatarDurationGift: Long = 5200L,
    val avatarDurationLike: Long = 3200L,
    val avatarDurationJoin: Long = 3800L,
    val avatarDurationFollow: Long = 4500L,
    val avatarDurationShare: Long = 4200L,
    val avatarSpawnZone: String = "SPREAD", // "SPREAD", "LEFT", "CENTER", "RIGHT"
    // ❤️ Overlay Baru: Top 10 Tap-Tap Love Dinamis (Foto Profil & Jumlah Tap - Bisa Horizontal / Vertikal)
    val enableTopLikersOverlay: Boolean = false,
    val topLikersPosXpct: Float = 0.50f,
    val topLikersPosYpct: Float = 0.85f,
    val topLikersScale: Float = 1.0f,
    val topLikersFontSizeSp: Float = 11.5f,
    val topLikersLatinFontSizeSp: Float = 11.5f, // Font latin nama & info
    val topLikersWidthPct: Float = 0.98f, // Lebar kartu Top Koin & Tap
    val topLikersHeightPadV: Int = 8, // Padding vertikal / tinggi kartu Top Koin & Tap
    val topLikersOrientation: String = "HORIZONTAL", // "HORIZONTAL" atau "VERTICAL"
    val topGiftOrientation: String = "HORIZONTAL", // "HORIZONTAL" (3 Kolom) atau "VERTICAL" (3 Baris)
    val topGiftAvatarScale: Float = 1.0f, // Skala ukuran custom foto profil Top 3 Gift Koin (0.5x .. 2.2x)
    // 🕌 4 Kategori Peringkat Penulis Khataman Al-Qur'an (Tampil Bersamaan, Dukung Horizontal & Vertikal)
    val showTopQuran: Boolean = true,
    val showTopSurah: Boolean = true,
    val showTopAyah: Boolean = true,
    val showTopHuruf: Boolean = true,
    // 🎯 Target Gift & Konversi Koin (Khusus Host di Floating Control & CLI - TIDAK dirender ke kamera virtual)
    val targetGiftName: String = "Paus",
    val targetGiftCoins: Int = 2150,
    val coinRateIdr: Int = 80, // Default rate pencairan creator Rp 80/diamond (~$0.005/koin)
    val targetBigoGiftName: String = "Supercar",
    val targetBigoGiftBeans: Int = 3000,
    val bigoBeanRateIdr: Int = 80 // Default rate pencairan creator Rp 80/bean
) {
    fun getAvatarScaleForEvent(type: LiveEventType): Float {
        val raw = when (type) {
            LiveEventType.CHAT -> avatarSizeChat
            LiveEventType.GIFT -> avatarSizeGift
            LiveEventType.LIKE -> avatarSizeLike
            LiveEventType.MEMBER -> avatarSizeJoin
            LiveEventType.FOLLOW -> avatarSizeFollow
            LiveEventType.SHARE -> avatarSizeShare
            else -> avatarSizeScale
        }
        return raw.coerceIn(0.20f, 4.50f)
    }

    fun getAvatarDurationForEvent(type: LiveEventType): Long {
        val raw = when (type) {
            LiveEventType.CHAT -> avatarDurationChat
            LiveEventType.GIFT -> avatarDurationGift
            LiveEventType.LIKE -> avatarDurationLike
            LiveEventType.MEMBER -> avatarDurationJoin
            LiveEventType.FOLLOW -> avatarDurationFollow
            LiveEventType.SHARE -> avatarDurationShare
            else -> avatarFlightDurationMs
        }
        return raw.coerceIn(800L, 20000L)
    }
}

/**
 * Model sesi streak multi-metode untuk pelacakan saweran/combo real-time.
 */
data class GiftStreakSession(
    val groupId: String = "",
    val lastCount: Int = 0,
    val lastUpdateMs: Long = 0L,
    val isCompleted: Boolean = false
)
