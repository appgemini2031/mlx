package com.tajasb.eventrelay

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveEventPreferences private constructor(private val appContext: Context) {

    companion object {
        private const val PREFS_NAME = "tajasb_live_event_broker_prefs"

        private const val KEY_SERVER_HOST = "key_server_host"
        private const val KEY_SERVER_PORT = "key_server_port"
        private const val KEY_AUTO_CONNECT = "key_auto_connect"
        private const val KEY_ACTIVE_PLATFORM = "key_active_platform"
        private const val KEY_TARGET_USERNAME = "key_target_username"
        private const val KEY_BACKUP_USERNAMES = "key_backup_usernames"
        private const val KEY_TARGET_BIGO_ID = "key_target_bigo_id"
        private const val KEY_BACKUP_BIGO_IDS = "key_backup_bigo_ids"

        private const val KEY_ENABLE_COMMENT = "key_enable_comment"
        private const val KEY_ENABLE_GIFT = "key_enable_gift"
        private const val KEY_ENABLE_LIKE = "key_enable_like"
        private const val KEY_ENABLE_JOIN = "key_enable_join"
        private const val KEY_ENABLE_FOLLOW = "key_enable_follow"
        private const val KEY_ENABLE_SHARE = "key_enable_share"
        private const val KEY_ENABLE_VIEWER_COUNT = "key_enable_viewer_count"
        private const val KEY_ENABLE_SHOP_ORDER = "key_enable_shop_order"
        private const val KEY_ENABLE_PIN_PRODUCT = "key_enable_pin_product"

        private const val KEY_BANNER_DURATION_MS = "key_banner_duration_ms"
        private const val KEY_CUSTOM_DOA_PREFIX = "key_custom_doa_prefix"

        private const val KEY_SEPARATE_OVERLAYS = "key_separate_overlays"
        private const val KEY_LIKE_POS_X = "key_like_pos_x"
        private const val KEY_LIKE_POS_Y = "key_like_pos_y"
        private const val KEY_LIKE_SCALE = "key_like_scale"
        private const val KEY_LIKE_FONT_SIZE = "key_like_font_size"

        private const val KEY_GIFT_POS_X = "key_gift_pos_x"
        private const val KEY_GIFT_POS_Y = "key_gift_pos_y"
        private const val KEY_GIFT_SCALE = "key_gift_scale"
        private const val KEY_GIFT_FONT_SIZE = "key_gift_font_size"

        private const val KEY_COMMENT_POS_X = "key_comment_pos_x"
        private const val KEY_COMMENT_POS_Y = "key_comment_pos_y"
        private const val KEY_COMMENT_SCALE = "key_comment_scale"
        private const val KEY_COMMENT_FONT_SIZE = "key_comment_font_size"

        private const val KEY_ENABLE_EVENT_STATS = "key_enable_event_stats"
        private const val KEY_EVENT_STATS_POS_X = "key_event_stats_pos_x"
        private const val KEY_EVENT_STATS_POS_Y = "key_event_stats_pos_y"
        private const val KEY_EVENT_STATS_SCALE = "key_event_stats_scale"
        private const val KEY_EVENT_STATS_FONT_SIZE = "key_event_stats_font_size"
        private const val KEY_EVENT_STATS_WIDTH_PCT = "key_event_stats_width_pct"

        private const val KEY_ENABLE_AVATAR_BUBBLE = "key_enable_avatar_bubble"
        private const val KEY_AVATAR_ON_CHAT = "key_avatar_on_chat"
        private const val KEY_AVATAR_ON_GIFT = "key_avatar_on_gift"
        private const val KEY_AVATAR_ON_LIKE = "key_avatar_on_like"
        private const val KEY_AVATAR_ON_JOIN = "key_avatar_on_join"
        private const val KEY_AVATAR_ON_FOLLOW = "key_avatar_on_follow"
        private const val KEY_AVATAR_ON_SHARE = "key_avatar_on_share"
        private const val KEY_AVATAR_SIZE_SCALE = "key_avatar_size_scale"
        private const val KEY_AVATAR_SIZE_CHAT = "key_avatar_size_chat"
        private const val KEY_AVATAR_SIZE_GIFT = "key_avatar_size_gift"
        private const val KEY_AVATAR_SIZE_LIKE = "key_avatar_size_like"
        private const val KEY_AVATAR_SIZE_JOIN = "key_avatar_size_join"
        private const val KEY_AVATAR_SIZE_FOLLOW = "key_avatar_size_follow"
        private const val KEY_AVATAR_SIZE_SHARE = "key_avatar_size_share"
        private const val KEY_AVATAR_FLIGHT_DURATION_MS = "key_avatar_flight_duration_ms"
        private const val KEY_AVATAR_DURATION_CHAT = "key_avatar_duration_chat"
        private const val KEY_AVATAR_DURATION_GIFT = "key_avatar_duration_gift"
        private const val KEY_AVATAR_DURATION_LIKE = "key_avatar_duration_like"
        private const val KEY_AVATAR_DURATION_JOIN = "key_avatar_duration_join"
        private const val KEY_AVATAR_DURATION_FOLLOW = "key_avatar_duration_follow"
        private const val KEY_AVATAR_DURATION_SHARE = "key_avatar_duration_share"
        private const val KEY_AVATAR_SPAWN_ZONE = "key_avatar_spawn_zone"

        private const val KEY_SHOW_DIAMONDS_IN_ANALYTICS = "key_show_diamonds_in_analytics"
        private const val KEY_ENABLE_TOP_LIKERS = "key_enable_top_likers"
        private const val KEY_TOP_LIKERS_POS_X = "key_top_likers_pos_x"
        private const val KEY_TOP_LIKERS_POS_Y = "key_top_likers_pos_y"
        private const val KEY_TOP_LIKERS_SCALE = "key_top_likers_scale"
        private const val KEY_TOP_LIKERS_FONT_SIZE = "key_top_likers_font_size"
        private const val KEY_TOP_LIKERS_LATIN_FONT_SIZE = "key_top_likers_latin_font_size"
        private const val KEY_TOP_LIKERS_WIDTH_PCT = "key_top_likers_width_pct"
        private const val KEY_TOP_LIKERS_HEIGHT_PAD_V = "key_top_likers_height_pad_v"
        private const val KEY_TOP_LIKERS_ORIENTATION = "key_top_likers_orientation"
        private const val KEY_TOP_GIFT_ORIENTATION = "key_top_gift_orientation"
        private const val KEY_TOP_GIFT_AVATAR_SCALE = "key_top_gift_avatar_scale"

        private const val KEY_SHOW_TOP_QURAN = "key_show_top_quran"
        private const val KEY_SHOW_TOP_SURAH = "key_show_top_surah"
        private const val KEY_SHOW_TOP_AYAH = "key_show_top_ayah"
        private const val KEY_SHOW_TOP_HURUF = "key_show_top_huruf"

        private const val KEY_TARGET_GIFT_NAME = "key_target_gift_name"
        private const val KEY_TARGET_GIFT_COINS = "key_target_gift_coins"
        private const val KEY_COIN_RATE_IDR = "key_coin_rate_idr"
        private const val KEY_TARGET_BIGO_GIFT_NAME = "key_target_bigo_gift_name"
        private const val KEY_TARGET_BIGO_GIFT_BEANS = "key_target_bigo_gift_beans"
        private const val KEY_BIGO_BEAN_RATE_IDR = "key_bigo_bean_rate_idr"

        @Volatile
        private var instance: LiveEventPreferences? = null

        fun getInstance(context: Context): LiveEventPreferences {
            return instance ?: synchronized(this) {
                instance ?: LiveEventPreferences(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<LiveEventConfig> = _config.asStateFlow()

    private fun loadConfig(): LiveEventConfig {
        if (!prefs.getBoolean("key_event_frame_proportional_v2", false)) {
            prefs.edit()
                .putFloat(KEY_LIKE_POS_X, 0.50f)
                .putFloat(KEY_LIKE_POS_Y, 0.78f)
                .putFloat(KEY_LIKE_SCALE, 1.0f)
                .putFloat(KEY_LIKE_FONT_SIZE, 16.0f)
                .putFloat(KEY_GIFT_POS_X, 0.50f)
                .putFloat(KEY_GIFT_POS_Y, 0.26f)
                .putFloat(KEY_GIFT_SCALE, 1.0f)
                .putFloat(KEY_GIFT_FONT_SIZE, 17.0f)
                .putFloat(KEY_COMMENT_POS_X, 0.50f)
                .putFloat(KEY_COMMENT_POS_Y, 0.50f)
                .putFloat(KEY_COMMENT_SCALE, 1.0f)
                .putFloat(KEY_COMMENT_FONT_SIZE, 16.5f)
                .putFloat(KEY_EVENT_STATS_POS_X, 0.50f)
                .putFloat(KEY_EVENT_STATS_POS_Y, 0.08f)
                .putFloat(KEY_EVENT_STATS_SCALE, 1.0f)
                .putFloat(KEY_EVENT_STATS_FONT_SIZE, 13.5f)
                .putFloat(KEY_EVENT_STATS_WIDTH_PCT, 0.98f)
                .putBoolean("key_event_frame_proportional_v2", true)
                .apply()
        }
        val baseAvatarScale = prefs.getFloat(KEY_AVATAR_SIZE_SCALE, 1.0f)
        val targetUser = prefs.getString(KEY_TARGET_USERNAME, "") ?: ""
        val backupUsersRaw = prefs.getString(KEY_BACKUP_USERNAMES, "") ?: ""
        val backupUsers = if (backupUsersRaw.isNotBlank()) {
            backupUsersRaw.split(",").map { it.trim().removePrefix("@") }.filter { it.isNotBlank() }
        } else {
            if (targetUser.isNotBlank()) listOf(targetUser) else emptyList()
        }
        val targetBigo = prefs.getString(KEY_TARGET_BIGO_ID, "") ?: ""
        val backupBigoRaw = prefs.getString(KEY_BACKUP_BIGO_IDS, "") ?: ""
        val backupBigo = if (backupBigoRaw.isNotBlank()) {
            backupBigoRaw.split(",").map { it.trim().removePrefix("@") }.filter { it.isNotBlank() }
        } else {
            if (targetBigo.isNotBlank()) listOf(targetBigo) else emptyList()
        }
        val activePlat = prefs.getString(KEY_ACTIVE_PLATFORM, "TIKTOK") ?: "TIKTOK"
        return LiveEventConfig(
            serverHost = prefs.getString(KEY_SERVER_HOST, "192.168.43.1") ?: "192.168.43.1",
            serverPort = prefs.getInt(KEY_SERVER_PORT, 8765),
            autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, true),
            activePlatform = activePlat,
            targetUsername = targetUser,
            backupUsernames = backupUsers,
            targetBigoId = targetBigo,
            backupBigoIds = backupBigo,
            enableComment = prefs.getBoolean(KEY_ENABLE_COMMENT, true),
            enableGift = prefs.getBoolean(KEY_ENABLE_GIFT, true),
            enableLike = prefs.getBoolean(KEY_ENABLE_LIKE, true),
            enableJoin = prefs.getBoolean(KEY_ENABLE_JOIN, true),
            enableFollow = prefs.getBoolean(KEY_ENABLE_FOLLOW, true),
            enableShare = prefs.getBoolean(KEY_ENABLE_SHARE, true),
            enableViewerCount = prefs.getBoolean(KEY_ENABLE_VIEWER_COUNT, true),
            enableShopOrder = prefs.getBoolean(KEY_ENABLE_SHOP_ORDER, true),
            enablePinProduct = prefs.getBoolean(KEY_ENABLE_PIN_PRODUCT, true),
            bannerDurationMs = prefs.getLong(KEY_BANNER_DURATION_MS, 4000L),
            customDoaPrefix = prefs.getString(KEY_CUSTOM_DOA_PREFIX, "Doa") ?: "Doa",
            separateOverlays = prefs.getBoolean(KEY_SEPARATE_OVERLAYS, true),
            likePosXpct = prefs.getFloat(KEY_LIKE_POS_X, 0.50f),
            likePosYpct = prefs.getFloat(KEY_LIKE_POS_Y, 0.78f),
            likeScale = prefs.getFloat(KEY_LIKE_SCALE, 1.0f),
            likeFontSizeSp = prefs.getFloat(KEY_LIKE_FONT_SIZE, 16.0f),
            giftPosXpct = prefs.getFloat(KEY_GIFT_POS_X, 0.50f),
            giftPosYpct = prefs.getFloat(KEY_GIFT_POS_Y, 0.26f),
            giftScale = prefs.getFloat(KEY_GIFT_SCALE, 1.0f),
            giftFontSizeSp = prefs.getFloat(KEY_GIFT_FONT_SIZE, 17.0f),
            commentPosXpct = prefs.getFloat(KEY_COMMENT_POS_X, 0.50f),
            commentPosYpct = prefs.getFloat(KEY_COMMENT_POS_Y, 0.50f),
            commentScale = prefs.getFloat(KEY_COMMENT_SCALE, 1.0f),
            commentFontSizeSp = prefs.getFloat(KEY_COMMENT_FONT_SIZE, 16.5f),
            enableEventStatsOverlay = prefs.getBoolean(KEY_ENABLE_EVENT_STATS, false),
            showDiamondsInAnalytics = prefs.getBoolean(KEY_SHOW_DIAMONDS_IN_ANALYTICS, false),
            eventStatsPosXpct = prefs.getFloat(KEY_EVENT_STATS_POS_X, 0.50f),
            eventStatsPosYpct = prefs.getFloat(KEY_EVENT_STATS_POS_Y, 0.08f),
            eventStatsScale = prefs.getFloat(KEY_EVENT_STATS_SCALE, 1.0f),
            eventStatsFontSizeSp = prefs.getFloat(KEY_EVENT_STATS_FONT_SIZE, 13.5f),
            eventStatsWidthPct = prefs.getFloat(KEY_EVENT_STATS_WIDTH_PCT, 0.98f),
            enableAvatarBubble = prefs.getBoolean(KEY_ENABLE_AVATAR_BUBBLE, true),
            avatarOnChat = prefs.getBoolean(KEY_AVATAR_ON_CHAT, true),
            avatarOnGift = prefs.getBoolean(KEY_AVATAR_ON_GIFT, true),
            avatarOnLike = prefs.getBoolean(KEY_AVATAR_ON_LIKE, true),
            avatarOnJoin = prefs.getBoolean(KEY_AVATAR_ON_JOIN, true),
            avatarOnFollow = prefs.getBoolean(KEY_AVATAR_ON_FOLLOW, true),
            avatarOnShare = prefs.getBoolean(KEY_AVATAR_ON_SHARE, true),
            avatarSizeScale = baseAvatarScale,
            avatarSizeChat = prefs.getFloat(KEY_AVATAR_SIZE_CHAT, baseAvatarScale),
            avatarSizeGift = prefs.getFloat(KEY_AVATAR_SIZE_GIFT, (baseAvatarScale * 1.30f).coerceIn(0.6f, 1.85f)),
            avatarSizeLike = prefs.getFloat(KEY_AVATAR_SIZE_LIKE, (baseAvatarScale * 0.85f).coerceIn(0.6f, 1.85f)),
            avatarSizeJoin = prefs.getFloat(KEY_AVATAR_SIZE_JOIN, (baseAvatarScale * 0.95f).coerceIn(0.6f, 1.85f)),
            avatarSizeFollow = prefs.getFloat(KEY_AVATAR_SIZE_FOLLOW, (baseAvatarScale * 1.15f).coerceIn(0.6f, 1.85f)),
            avatarSizeShare = prefs.getFloat(KEY_AVATAR_SIZE_SHARE, (baseAvatarScale * 1.10f).coerceIn(0.6f, 1.85f)),
            avatarFlightDurationMs = prefs.getLong(KEY_AVATAR_FLIGHT_DURATION_MS, 4200L),
            avatarDurationChat = prefs.getLong(KEY_AVATAR_DURATION_CHAT, prefs.getLong(KEY_AVATAR_FLIGHT_DURATION_MS, 4200L)),
            avatarDurationGift = prefs.getLong(KEY_AVATAR_DURATION_GIFT, 5200L),
            avatarDurationLike = prefs.getLong(KEY_AVATAR_DURATION_LIKE, 3200L),
            avatarDurationJoin = prefs.getLong(KEY_AVATAR_DURATION_JOIN, 3800L),
            avatarDurationFollow = prefs.getLong(KEY_AVATAR_DURATION_FOLLOW, 4500L),
            avatarDurationShare = prefs.getLong(KEY_AVATAR_DURATION_SHARE, 4200L),
            avatarSpawnZone = prefs.getString(KEY_AVATAR_SPAWN_ZONE, "SPREAD") ?: "SPREAD",
            enableTopLikersOverlay = prefs.getBoolean(KEY_ENABLE_TOP_LIKERS, false),
            topLikersPosXpct = prefs.getFloat(KEY_TOP_LIKERS_POS_X, 0.50f),
            topLikersPosYpct = prefs.getFloat(KEY_TOP_LIKERS_POS_Y, 0.85f),
            topLikersScale = prefs.getFloat(KEY_TOP_LIKERS_SCALE, 1.0f),
            topLikersFontSizeSp = prefs.getFloat(KEY_TOP_LIKERS_FONT_SIZE, 11.5f),
            topLikersLatinFontSizeSp = prefs.getFloat(KEY_TOP_LIKERS_LATIN_FONT_SIZE, 11.5f),
            topLikersWidthPct = prefs.getFloat(KEY_TOP_LIKERS_WIDTH_PCT, 0.98f),
            topLikersHeightPadV = prefs.getInt(KEY_TOP_LIKERS_HEIGHT_PAD_V, 8),
            topLikersOrientation = prefs.getString(KEY_TOP_LIKERS_ORIENTATION, "HORIZONTAL") ?: "HORIZONTAL",
            topGiftOrientation = prefs.getString(KEY_TOP_GIFT_ORIENTATION, "HORIZONTAL") ?: "HORIZONTAL",
            topGiftAvatarScale = prefs.getFloat(KEY_TOP_GIFT_AVATAR_SCALE, 1.0f).coerceIn(0.5f, 2.2f),
            showTopQuran = prefs.getBoolean(KEY_SHOW_TOP_QURAN, true),
            showTopSurah = prefs.getBoolean(KEY_SHOW_TOP_SURAH, true),
            showTopAyah = prefs.getBoolean(KEY_SHOW_TOP_AYAH, true),
            showTopHuruf = prefs.getBoolean(KEY_SHOW_TOP_HURUF, true),
            targetGiftName = prefs.getString(KEY_TARGET_GIFT_NAME, "Paus") ?: "Paus",
            targetGiftCoins = prefs.getInt(KEY_TARGET_GIFT_COINS, 2150).coerceAtLeast(1),
            coinRateIdr = prefs.getInt(KEY_COIN_RATE_IDR, 80).coerceIn(10, 1000),
            targetBigoGiftName = prefs.getString(KEY_TARGET_BIGO_GIFT_NAME, "Supercar") ?: "Supercar",
            targetBigoGiftBeans = prefs.getInt(KEY_TARGET_BIGO_GIFT_BEANS, 3000).coerceAtLeast(1),
            bigoBeanRateIdr = prefs.getInt(KEY_BIGO_BEAN_RATE_IDR, 80).coerceIn(10, 1000)
        )
    }

    fun updateServerAddress(host: String, port: Int = 8765) {
        val cleanHost = host.trim().removePrefix("ws://").removePrefix("http://").split(":")[0]
        prefs.edit()
            .putString(KEY_SERVER_HOST, cleanHost)
            .putInt(KEY_SERVER_PORT, port)
            .apply()
        _config.value = _config.value.copy(serverHost = cleanHost, serverPort = port)
    }

    fun setAutoConnect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
        _config.value = _config.value.copy(autoConnect = enabled)
    }

    fun setTargetUsername(username: String) {
        val clean = username.trim().removePrefix("@")
        prefs.edit().putString(KEY_TARGET_USERNAME, clean).apply()
        _config.value = _config.value.copy(targetUsername = clean)
    }

    fun addBackupUsername(username: String): Boolean {
        val clean = username.trim().removePrefix("@")
        if (clean.isBlank()) return false
        val current = _config.value.backupUsernames.toMutableList()
        if (!current.contains(clean)) {
            current.add(clean)
            prefs.edit().putString(KEY_BACKUP_USERNAMES, current.joinToString(",")).apply()
            _config.value = _config.value.copy(backupUsernames = current)
            return true
        }
        return false
    }

    fun removeBackupUsername(username: String) {
        val clean = username.trim().removePrefix("@")
        val current = _config.value.backupUsernames.toMutableList()
        if (current.remove(clean)) {
            prefs.edit().putString(KEY_BACKUP_USERNAMES, current.joinToString(",")).apply()
            _config.value = _config.value.copy(backupUsernames = current)
        }
    }

    fun setEnableComment(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_COMMENT, enabled).apply()
        val updated = _config.value.copy(enableComment = enabled)
        _config.value = updated
        if (!enabled) {
            LiveCommentManager_clearIfPossible()
            if (updated.separateOverlays || !updated.enableGift) {
                com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(appContext, "COMMENT")
            }
        }
    }

    private fun LiveCommentManager_clearIfPossible() {
    }

    fun setEnableGift(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_GIFT, enabled).apply()
        val updated = _config.value.copy(enableGift = enabled)
        _config.value = updated
        if (!enabled) {
            com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(appContext, "EVENT_GIFT")
            if (!updated.separateOverlays && !updated.enableComment) {
                com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(appContext, "COMMENT")
            }
        }
    }

    private fun hideSharedLikeBannerIfNoneEnabled(cfg: LiveEventConfig) {
        if (!cfg.enableLike && !cfg.enableJoin && !cfg.enableFollow && !cfg.enableShare) {
            com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(appContext, "EVENT_LIKE")
            if (!cfg.separateOverlays && !cfg.enableComment && !cfg.enableGift) {
                com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(appContext, "COMMENT")
            }
        }
    }

    fun setEnableLike(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_LIKE, enabled).apply()
        val updated = _config.value.copy(enableLike = enabled)
        _config.value = updated
        if (!enabled) {
            hideSharedLikeBannerIfNoneEnabled(updated)
        }
    }

    fun setEnableJoin(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_JOIN, enabled).apply()
        val updated = _config.value.copy(enableJoin = enabled)
        _config.value = updated
        if (!enabled) {
            hideSharedLikeBannerIfNoneEnabled(updated)
        }
    }

    fun setEnableFollow(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_FOLLOW, enabled).apply()
        val updated = _config.value.copy(enableFollow = enabled)
        _config.value = updated
        if (!enabled) {
            hideSharedLikeBannerIfNoneEnabled(updated)
        }
    }

    fun setEnableShare(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_SHARE, enabled).apply()
        val updated = _config.value.copy(enableShare = enabled)
        _config.value = updated
        if (!enabled) {
            hideSharedLikeBannerIfNoneEnabled(updated)
        }
    }

    fun setEnableShopOrder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_SHOP_ORDER, enabled).apply()
        _config.value = _config.value.copy(enableShopOrder = enabled)
    }

    fun setEnablePinProduct(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_PIN_PRODUCT, enabled).apply()
        _config.value = _config.value.copy(enablePinProduct = enabled)
    }

    fun setEnableViewerCount(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_VIEWER_COUNT, enabled).apply()
        _config.value = _config.value.copy(enableViewerCount = enabled)
    }

    fun setBannerDurationMs(durationMs: Long) {
        prefs.edit().putLong(KEY_BANNER_DURATION_MS, durationMs).apply()
        _config.value = _config.value.copy(bannerDurationMs = durationMs)
    }

    fun setSeparateOverlays(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEPARATE_OVERLAYS, enabled).apply()
        _config.value = _config.value.copy(separateOverlays = enabled)
    }

    private fun previewLikeOverlayIfActive() {
        val c = _config.value
        if (!c.enableLike) return
        com.tajasb.notif.OverlayBannerBroadcaster.broadcastShow(
            context = appContext,
            senderName = "Sahabat Live",
            content = "Mengetuk Like +10 ❤️",
            isGift = false,
            bannerType = if (c.separateOverlays) "EVENT_LIKE" else "COMMENT",
            iconMode = "PRAYER",
            customPrefix = "Like",
            durationMs = c.likeDurationMs,
            posXpct = c.likePosXpct,
            posYpct = c.likePosYpct,
            scale = c.likeScale,
            fontSizeSp = c.likeFontSizeSp
        )
    }

    private fun previewGiftOverlayIfActive() {
        val c = _config.value
        if (!c.enableGift) return
        com.tajasb.notif.OverlayBannerBroadcaster.broadcastShow(
            context = appContext,
            senderName = "Sultan Makkah",
            content = "Mengirim Mawar x10 • 10 💎",
            isGift = true,
            bannerType = if (c.separateOverlays) "EVENT_GIFT" else "COMMENT",
            iconMode = "SHOPPING",
            customPrefix = c.customGiftPrefix,
            durationMs = c.giftDurationMs,
            posXpct = c.giftPosXpct,
            posYpct = c.giftPosYpct,
            scale = c.giftScale,
            fontSizeSp = c.giftFontSizeSp
        )
    }

    fun setLikeOverlayPos(xPct: Float, yPct: Float) {
        val sx = xPct.coerceIn(0.02f, 0.98f)
        val sy = yPct.coerceIn(0.02f, 0.98f)
        prefs.edit().putFloat(KEY_LIKE_POS_X, sx).putFloat(KEY_LIKE_POS_Y, sy).apply()
        _config.value = _config.value.copy(likePosXpct = sx, likePosYpct = sy)
        previewLikeOverlayIfActive()
    }

    fun setLikeOverlayScale(scale: Float) {
        val s = scale.coerceIn(0.4f, 2.5f)
        prefs.edit().putFloat(KEY_LIKE_SCALE, s).apply()
        _config.value = _config.value.copy(likeScale = s)
        previewLikeOverlayIfActive()
    }

    fun setLikeFontSizeSp(sp: Float) {
        val s = sp.coerceIn(8f, 28f)
        prefs.edit().putFloat(KEY_LIKE_FONT_SIZE, s).apply()
        _config.value = _config.value.copy(likeFontSizeSp = s)
        previewLikeOverlayIfActive()
    }

    fun setGiftOverlayPos(xPct: Float, yPct: Float) {
        val sx = xPct.coerceIn(0.02f, 0.98f)
        val sy = yPct.coerceIn(0.02f, 0.98f)
        prefs.edit().putFloat(KEY_GIFT_POS_X, sx).putFloat(KEY_GIFT_POS_Y, sy).apply()
        _config.value = _config.value.copy(giftPosXpct = sx, giftPosYpct = sy)
        previewGiftOverlayIfActive()
    }

    fun setGiftOverlayScale(scale: Float) {
        val s = scale.coerceIn(0.4f, 2.5f)
        prefs.edit().putFloat(KEY_GIFT_SCALE, s).apply()
        _config.value = _config.value.copy(giftScale = s)
        previewGiftOverlayIfActive()
    }

    fun setGiftFontSizeSp(sp: Float) {
        val s = sp.coerceIn(8f, 28f)
        prefs.edit().putFloat(KEY_GIFT_FONT_SIZE, s).apply()
        _config.value = _config.value.copy(giftFontSizeSp = s)
        previewGiftOverlayIfActive()
    }

    fun setLikePos(xPct: Float, yPct: Float) = setLikeOverlayPos(xPct, yPct)
    fun setGiftPos(xPct: Float, yPct: Float) = setGiftOverlayPos(xPct, yPct)

    fun setEnableEventStatsOverlay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_EVENT_STATS, enabled).apply()
        _config.value = _config.value.copy(enableEventStatsOverlay = enabled)
        if (enabled) {
            LiveEventReceiver.getInstance(appContext).refreshEventStatsIfEnabled()
        } else {
            com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(appContext, "EVENT_STATS")
        }
    }

    fun setEventStatsPos(xPct: Float, yPct: Float) {
        val sx = xPct.coerceIn(0.02f, 0.98f)
        val sy = yPct.coerceIn(0.02f, 0.98f)
        prefs.edit().putFloat(KEY_EVENT_STATS_POS_X, sx).putFloat(KEY_EVENT_STATS_POS_Y, sy).apply()
        _config.value = _config.value.copy(eventStatsPosXpct = sx, eventStatsPosYpct = sy)
        LiveEventReceiver.getInstance(appContext).refreshEventStatsIfEnabled()
    }

    fun setEventStatsScale(scale: Float) {
        val s = scale.coerceIn(0.4f, 2.5f)
        prefs.edit().putFloat(KEY_EVENT_STATS_SCALE, s).apply()
        _config.value = _config.value.copy(eventStatsScale = s)
        LiveEventReceiver.getInstance(appContext).refreshEventStatsIfEnabled()
    }

    fun setEventStatsFontSizeSp(sp: Float) {
        val s = sp.coerceIn(8f, 24f)
        prefs.edit().putFloat(KEY_EVENT_STATS_FONT_SIZE, s).apply()
        _config.value = _config.value.copy(eventStatsFontSizeSp = s)
        LiveEventReceiver.getInstance(appContext).refreshEventStatsIfEnabled()
    }

    fun setEventStatsWidthPct(pct: Float) {
        val s = pct.coerceIn(0.40f, 0.98f)
        prefs.edit().putFloat(KEY_EVENT_STATS_WIDTH_PCT, s).apply()
        _config.value = _config.value.copy(eventStatsWidthPct = s)
        LiveEventReceiver.getInstance(appContext).refreshEventStatsIfEnabled()
    }

    fun setEnableTopLikersOverlay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_TOP_LIKERS, enabled).apply()
        _config.value = _config.value.copy(enableTopLikersOverlay = enabled)
        if (enabled) {
            LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
        } else {
            com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(appContext, "TOP_LIKERS")
        }
    }

    fun setShowDiamondsInAnalytics(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DIAMONDS_IN_ANALYTICS, show).apply()
        _config.value = _config.value.copy(showDiamondsInAnalytics = show)
        LiveEventReceiver.getInstance(appContext).refreshEventStatsIfEnabled()
    }

    fun toggleShowDiamondsInAnalytics(): Boolean {
        val next = !_config.value.showDiamondsInAnalytics
        setShowDiamondsInAnalytics(next)
        return next
    }

    fun setTopLikersPos(xPct: Float, yPct: Float) {
        val sx = xPct.coerceIn(-1.50f, 2.50f)
        val sy = yPct.coerceIn(-1.50f, 2.50f)
        prefs.edit().putFloat(KEY_TOP_LIKERS_POS_X, sx).putFloat(KEY_TOP_LIKERS_POS_Y, sy).apply()
        _config.value = _config.value.copy(topLikersPosXpct = sx, topLikersPosYpct = sy)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun adjustTopLikersPos(dx: Float, dy: Float) {
        val c = _config.value
        setTopLikersPos(c.topLikersPosXpct + dx, c.topLikersPosYpct + dy)
    }

    fun setTopLikersScale(scale: Float) {
        val s = scale.coerceAtLeast(0.05f)
        prefs.edit().putFloat(KEY_TOP_LIKERS_SCALE, s).apply()
        _config.value = _config.value.copy(topLikersScale = s)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun adjustTopLikersScale(delta: Float) {
        setTopLikersScale(_config.value.topLikersScale + delta)
    }

    fun setTopLikersFontSizeSp(sp: Float) {
        val s = sp.coerceAtLeast(3f)
        prefs.edit().putFloat(KEY_TOP_LIKERS_FONT_SIZE, s).apply()
        _config.value = _config.value.copy(topLikersFontSizeSp = s)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun adjustTopLikersFontSizeSp(delta: Float) {
        setTopLikersFontSizeSp(_config.value.topLikersFontSizeSp + delta)
    }

    fun setTopLikersLatinFontSizeSp(sp: Float) {
        val s = sp.coerceAtLeast(3f)
        prefs.edit().putFloat(KEY_TOP_LIKERS_LATIN_FONT_SIZE, s).apply()
        _config.value = _config.value.copy(topLikersLatinFontSizeSp = s)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun adjustTopLikersLatinFontSizeSp(delta: Float) {
        setTopLikersLatinFontSizeSp(_config.value.topLikersLatinFontSizeSp + delta)
    }

    fun setTopLikersWidthPct(pct: Float) {
        val p = pct.coerceAtLeast(0.10f)
        prefs.edit().putFloat(KEY_TOP_LIKERS_WIDTH_PCT, p).apply()
        _config.value = _config.value.copy(topLikersWidthPct = p)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun adjustTopLikersWidthPct(delta: Float) {
        setTopLikersWidthPct(_config.value.topLikersWidthPct + delta)
    }

    fun setTopLikersHeightPadV(pad: Int) {
        val p = pad.coerceAtLeast(0)
        prefs.edit().putInt(KEY_TOP_LIKERS_HEIGHT_PAD_V, p).apply()
        _config.value = _config.value.copy(topLikersHeightPadV = p)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun adjustTopLikersHeightPadV(delta: Int) {
        setTopLikersHeightPadV(_config.value.topLikersHeightPadV + delta)
    }

    fun setTopLikersOrientation(orientation: String) {
        val clean = if (orientation.equals("VERTICAL", ignoreCase = true)) "VERTICAL" else "HORIZONTAL"
        prefs.edit().putString(KEY_TOP_LIKERS_ORIENTATION, clean).apply()
        _config.value = _config.value.copy(topLikersOrientation = clean)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun toggleTopLikersOrientation(): String {
        val next = if (_config.value.topLikersOrientation.equals("VERTICAL", ignoreCase = true)) {
            "HORIZONTAL"
        } else {
            "VERTICAL"
        }
        setTopLikersOrientation(next)
        return next
    }

    fun setTopGiftOrientation(orientation: String) {
        val clean = if (orientation.equals("VERTICAL", ignoreCase = true)) "VERTICAL" else "HORIZONTAL"
        prefs.edit().putString(KEY_TOP_GIFT_ORIENTATION, clean).apply()
        _config.value = _config.value.copy(topGiftOrientation = clean)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun toggleTopGiftOrientation(): String {
        val next = if (_config.value.topGiftOrientation.equals("VERTICAL", ignoreCase = true)) {
            "HORIZONTAL"
        } else {
            "VERTICAL"
        }
        setTopGiftOrientation(next)
        return next
    }

    fun setShowTopQuran(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_TOP_QURAN, show).apply()
        _config.value = _config.value.copy(showTopQuran = show)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun toggleShowTopQuran(): Boolean {
        val next = !_config.value.showTopQuran
        setShowTopQuran(next)
        return next
    }

    fun setShowTopSurah(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_TOP_SURAH, show).apply()
        _config.value = _config.value.copy(showTopSurah = show)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun toggleShowTopSurah(): Boolean {
        val next = !_config.value.showTopSurah
        setShowTopSurah(next)
        return next
    }

    fun setShowTopAyah(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_TOP_AYAH, show).apply()
        _config.value = _config.value.copy(showTopAyah = show)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun toggleShowTopAyah(): Boolean {
        val next = !_config.value.showTopAyah
        setShowTopAyah(next)
        return next
    }

    fun setShowTopHuruf(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_TOP_HURUF, show).apply()
        _config.value = _config.value.copy(showTopHuruf = show)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun toggleShowTopHuruf(): Boolean {
        val next = !_config.value.showTopHuruf
        setShowTopHuruf(next)
        return next
    }

    fun setTopGiftAvatarScale(scale: Float) {
        val s = scale.coerceIn(0.5f, 2.2f)
        prefs.edit().putFloat(KEY_TOP_GIFT_AVATAR_SCALE, s).apply()
        _config.value = _config.value.copy(topGiftAvatarScale = s)
        LiveEventReceiver.getInstance(appContext).refreshTopLikersIfEnabled()
    }

    fun adjustTopGiftAvatarScale(delta: Float): Float {
        val next = (_config.value.topGiftAvatarScale + delta).coerceIn(0.5f, 2.2f)
        setTopGiftAvatarScale(next)
        return next
    }

    fun setEnableAvatarBubble(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_AVATAR_BUBBLE, enabled).apply()
        _config.value = _config.value.copy(enableAvatarBubble = enabled)
        if (!enabled) {
            com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(appContext, "AVATAR_BUBBLE")
        }
    }

    fun setAvatarOnChat(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AVATAR_ON_CHAT, enabled).apply()
        _config.value = _config.value.copy(avatarOnChat = enabled)
    }

    fun setAvatarOnGift(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AVATAR_ON_GIFT, enabled).apply()
        _config.value = _config.value.copy(avatarOnGift = enabled)
    }

    fun setAvatarOnLike(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AVATAR_ON_LIKE, enabled).apply()
        _config.value = _config.value.copy(avatarOnLike = enabled)
    }

    fun setAvatarOnJoin(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AVATAR_ON_JOIN, enabled).apply()
        _config.value = _config.value.copy(avatarOnJoin = enabled)
    }

    fun setAvatarOnFollow(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AVATAR_ON_FOLLOW, enabled).apply()
        _config.value = _config.value.copy(avatarOnFollow = enabled)
    }

    fun setAvatarOnShare(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AVATAR_ON_SHARE, enabled).apply()
        _config.value = _config.value.copy(avatarOnShare = enabled)
    }

    fun setAvatarSizeScale(scale: Float) {
        val s = scale.coerceIn(0.20f, 4.00f)
        prefs.edit()
            .putFloat(KEY_AVATAR_SIZE_SCALE, s)
            .putFloat(KEY_AVATAR_SIZE_CHAT, s)
            .putFloat(KEY_AVATAR_SIZE_GIFT, (s * 1.25f).coerceIn(0.20f, 4.50f))
            .putFloat(KEY_AVATAR_SIZE_LIKE, (s * 0.85f).coerceIn(0.20f, 4.00f))
            .putFloat(KEY_AVATAR_SIZE_JOIN, (s * 0.95f).coerceIn(0.20f, 4.00f))
            .putFloat(KEY_AVATAR_SIZE_FOLLOW, (s * 1.15f).coerceIn(0.20f, 4.00f))
            .putFloat(KEY_AVATAR_SIZE_SHARE, (s * 1.10f).coerceIn(0.20f, 4.00f))
            .apply()
        _config.value = _config.value.copy(
            avatarSizeScale = s,
            avatarSizeChat = s,
            avatarSizeGift = (s * 1.25f).coerceIn(0.20f, 4.50f),
            avatarSizeLike = (s * 0.85f).coerceIn(0.20f, 4.00f),
            avatarSizeJoin = (s * 0.95f).coerceIn(0.20f, 4.00f),
            avatarSizeFollow = (s * 1.15f).coerceIn(0.20f, 4.00f),
            avatarSizeShare = (s * 1.10f).coerceIn(0.20f, 4.00f)
        )
    }

    fun setAvatarSizeForEvent(type: LiveEventType, scale: Float) {
        val s = scale.coerceIn(0.20f, 4.50f)
        val c = _config.value
        when (type) {
            LiveEventType.CHAT -> {
                prefs.edit().putFloat(KEY_AVATAR_SIZE_CHAT, s).apply()
                _config.value = c.copy(avatarSizeChat = s)
            }
            LiveEventType.GIFT -> {
                prefs.edit().putFloat(KEY_AVATAR_SIZE_GIFT, s).apply()
                _config.value = c.copy(avatarSizeGift = s)
            }
            LiveEventType.LIKE -> {
                prefs.edit().putFloat(KEY_AVATAR_SIZE_LIKE, s).apply()
                _config.value = c.copy(avatarSizeLike = s)
            }
            LiveEventType.MEMBER -> {
                prefs.edit().putFloat(KEY_AVATAR_SIZE_JOIN, s).apply()
                _config.value = c.copy(avatarSizeJoin = s)
            }
            LiveEventType.FOLLOW -> {
                prefs.edit().putFloat(KEY_AVATAR_SIZE_FOLLOW, s).apply()
                _config.value = c.copy(avatarSizeFollow = s)
            }
            LiveEventType.SHARE -> {
                prefs.edit().putFloat(KEY_AVATAR_SIZE_SHARE, s).apply()
                _config.value = c.copy(avatarSizeShare = s)
            }
            else -> setAvatarSizeScale(s)
        }
    }

    fun setAvatarFlightDurationMs(durationMs: Long) {
        val d = durationMs.coerceIn(800L, 20000L)
        prefs.edit()
            .putLong(KEY_AVATAR_FLIGHT_DURATION_MS, d)
            .putLong(KEY_AVATAR_DURATION_CHAT, d)
            .putLong(KEY_AVATAR_DURATION_GIFT, d)
            .putLong(KEY_AVATAR_DURATION_LIKE, d)
            .putLong(KEY_AVATAR_DURATION_JOIN, d)
            .putLong(KEY_AVATAR_DURATION_FOLLOW, d)
            .putLong(KEY_AVATAR_DURATION_SHARE, d)
            .apply()
        _config.value = _config.value.copy(
            avatarFlightDurationMs = d,
            avatarDurationChat = d,
            avatarDurationGift = d,
            avatarDurationLike = d,
            avatarDurationJoin = d,
            avatarDurationFollow = d,
            avatarDurationShare = d
        )
    }

    fun setAvatarDurationForEvent(type: LiveEventType, durationMs: Long) {
        val d = durationMs.coerceIn(800L, 20000L)
        val c = _config.value
        when (type) {
            LiveEventType.CHAT -> {
                prefs.edit().putLong(KEY_AVATAR_DURATION_CHAT, d).apply()
                _config.value = c.copy(avatarDurationChat = d)
            }
            LiveEventType.GIFT -> {
                prefs.edit().putLong(KEY_AVATAR_DURATION_GIFT, d).apply()
                _config.value = c.copy(avatarDurationGift = d)
            }
            LiveEventType.LIKE -> {
                prefs.edit().putLong(KEY_AVATAR_DURATION_LIKE, d).apply()
                _config.value = c.copy(avatarDurationLike = d)
            }
            LiveEventType.MEMBER -> {
                prefs.edit().putLong(KEY_AVATAR_DURATION_JOIN, d).apply()
                _config.value = c.copy(avatarDurationJoin = d)
            }
            LiveEventType.FOLLOW -> {
                prefs.edit().putLong(KEY_AVATAR_DURATION_FOLLOW, d).apply()
                _config.value = c.copy(avatarDurationFollow = d)
            }
            LiveEventType.SHARE -> {
                prefs.edit().putLong(KEY_AVATAR_DURATION_SHARE, d).apply()
                _config.value = c.copy(avatarDurationShare = d)
            }
            else -> setAvatarFlightDurationMs(d)
        }
    }

    fun setAvatarSpawnZone(zone: String) {
        val clean = when (zone.uppercase()) {
            "LEFT", "CENTER", "RIGHT", "SPREAD" -> zone.uppercase()
            else -> "SPREAD"
        }
        prefs.edit().putString(KEY_AVATAR_SPAWN_ZONE, clean).apply()
        _config.value = _config.value.copy(avatarSpawnZone = clean)
    }

    fun setTargetGift(name: String, coins: Int) {
        val safeName = name.trim().ifBlank { "Paus" }
        val safeCoins = coins.coerceAtLeast(1)
        prefs.edit()
            .putString(KEY_TARGET_GIFT_NAME, safeName)
            .putInt(KEY_TARGET_GIFT_COINS, safeCoins)
            .apply()
        _config.value = _config.value.copy(targetGiftName = safeName, targetGiftCoins = safeCoins)
    }

    fun setCoinRateIdr(rate: Int) {
        val safeRate = rate.coerceIn(10, 1000)
        prefs.edit().putInt(KEY_COIN_RATE_IDR, safeRate).apply()
        _config.value = _config.value.copy(coinRateIdr = safeRate)
    }

    fun setActivePlatform(platform: String) {
        val clean = if (platform.equals("BIGO", ignoreCase = true)) "BIGO" else "TIKTOK"
        prefs.edit().putString(KEY_ACTIVE_PLATFORM, clean).apply()
        _config.value = _config.value.copy(activePlatform = clean)
    }

    fun setTargetBigoId(bigoId: String) {
        val clean = bigoId.trim().removePrefix("@")
        prefs.edit().putString(KEY_TARGET_BIGO_ID, clean).apply()
        _config.value = _config.value.copy(targetBigoId = clean)
    }

    fun addBackupBigoId(bigoId: String): Boolean {
        val clean = bigoId.trim().removePrefix("@")
        if (clean.isBlank()) return false
        val current = _config.value.backupBigoIds.toMutableList()
        if (!current.contains(clean)) {
            current.add(clean)
            prefs.edit().putString(KEY_BACKUP_BIGO_IDS, current.joinToString(",")).apply()
            _config.value = _config.value.copy(backupBigoIds = current)
            return true
        }
        return false
    }

    fun removeBackupBigoId(bigoId: String) {
        val clean = bigoId.trim().removePrefix("@")
        val current = _config.value.backupBigoIds.toMutableList()
        if (current.remove(clean)) {
            prefs.edit().putString(KEY_BACKUP_BIGO_IDS, current.joinToString(",")).apply()
            _config.value = _config.value.copy(backupBigoIds = current)
        }
    }

    fun setTargetBigoGift(name: String, beans: Int) {
        val safeName = name.trim().ifBlank { "Supercar" }
        val safeBeans = beans.coerceAtLeast(1)
        prefs.edit()
            .putString(KEY_TARGET_BIGO_GIFT_NAME, safeName)
            .putInt(KEY_TARGET_BIGO_GIFT_BEANS, safeBeans)
            .apply()
        _config.value = _config.value.copy(targetBigoGiftName = safeName, targetBigoGiftBeans = safeBeans)
    }

    fun setBigoBeanRateIdr(rate: Int) {
        val safeRate = rate.coerceIn(10, 1000)
        prefs.edit().putInt(KEY_BIGO_BEAN_RATE_IDR, safeRate).apply()
        _config.value = _config.value.copy(bigoBeanRateIdr = safeRate)
    }

    fun importFullConfig(cfg: LiveEventConfig) {
        val safePlat = if (cfg.activePlatform.equals("BIGO", ignoreCase = true)) "BIGO" else "TIKTOK"
        val safeAvatarZone = when (cfg.avatarSpawnZone.uppercase()) {
            "LEFT", "CENTER", "RIGHT", "SPREAD" -> cfg.avatarSpawnZone.uppercase()
            else -> "SPREAD"
        }
        val safeLikersOrient = if (cfg.topLikersOrientation.equals("VERTICAL", true)) "VERTICAL" else "HORIZONTAL"
        val safeGiftOrient = if (cfg.topGiftOrientation.equals("VERTICAL", true)) "VERTICAL" else "HORIZONTAL"

        prefs.edit()
            .putString(KEY_SERVER_HOST, cfg.serverHost.trim())
            .putInt(KEY_SERVER_PORT, cfg.serverPort.coerceIn(1, 65535))
            .putBoolean(KEY_AUTO_CONNECT, cfg.autoConnect)
            .putString(KEY_ACTIVE_PLATFORM, safePlat)
            .putString(KEY_TARGET_USERNAME, cfg.targetUsername.trim().removePrefix("@"))
            .putString(KEY_BACKUP_USERNAMES, cfg.backupUsernames.joinToString(","))
            .putString(KEY_TARGET_BIGO_ID, cfg.targetBigoId.trim().removePrefix("@"))
            .putString(KEY_BACKUP_BIGO_IDS, cfg.backupBigoIds.joinToString(","))
            .putBoolean(KEY_ENABLE_COMMENT, cfg.enableComment)
            .putBoolean(KEY_ENABLE_GIFT, cfg.enableGift)
            .putBoolean(KEY_ENABLE_LIKE, cfg.enableLike)
            .putBoolean(KEY_ENABLE_JOIN, cfg.enableJoin)
            .putBoolean(KEY_ENABLE_FOLLOW, cfg.enableFollow)
            .putBoolean(KEY_ENABLE_SHARE, cfg.enableShare)
            .putBoolean(KEY_ENABLE_VIEWER_COUNT, cfg.enableViewerCount)
            .putBoolean(KEY_ENABLE_SHOP_ORDER, cfg.enableShopOrder)
            .putBoolean(KEY_ENABLE_PIN_PRODUCT, cfg.enablePinProduct)
            .putLong(KEY_BANNER_DURATION_MS, cfg.bannerDurationMs.coerceIn(500L, 30000L))
            .putString(KEY_CUSTOM_DOA_PREFIX, cfg.customDoaPrefix)
            .putBoolean(KEY_SEPARATE_OVERLAYS, cfg.separateOverlays)
            .putFloat(KEY_LIKE_POS_X, cfg.likePosXpct.coerceIn(0.02f, 0.98f))
            .putFloat(KEY_LIKE_POS_Y, cfg.likePosYpct.coerceIn(0.02f, 0.98f))
            .putFloat(KEY_LIKE_SCALE, cfg.likeScale.coerceIn(0.4f, 2.5f))
            .putFloat(KEY_LIKE_FONT_SIZE, cfg.likeFontSizeSp.coerceIn(8f, 28f))
            .putFloat(KEY_GIFT_POS_X, cfg.giftPosXpct.coerceIn(0.02f, 0.98f))
            .putFloat(KEY_GIFT_POS_Y, cfg.giftPosYpct.coerceIn(0.02f, 0.98f))
            .putFloat(KEY_GIFT_SCALE, cfg.giftScale.coerceIn(0.4f, 2.5f))
            .putFloat(KEY_GIFT_FONT_SIZE, cfg.giftFontSizeSp.coerceIn(8f, 28f))
            .putFloat(KEY_COMMENT_POS_X, cfg.commentPosXpct.coerceIn(0.02f, 0.98f))
            .putFloat(KEY_COMMENT_POS_Y, cfg.commentPosYpct.coerceIn(0.02f, 0.98f))
            .putFloat(KEY_COMMENT_SCALE, cfg.commentScale.coerceIn(0.4f, 2.5f))
            .putFloat(KEY_COMMENT_FONT_SIZE, cfg.commentFontSizeSp.coerceIn(8f, 28f))
            .putBoolean(KEY_ENABLE_EVENT_STATS, cfg.enableEventStatsOverlay)
            .putBoolean(KEY_SHOW_DIAMONDS_IN_ANALYTICS, cfg.showDiamondsInAnalytics)
            .putFloat(KEY_EVENT_STATS_POS_X, cfg.eventStatsPosXpct.coerceIn(0.02f, 0.98f))
            .putFloat(KEY_EVENT_STATS_POS_Y, cfg.eventStatsPosYpct.coerceIn(0.02f, 0.98f))
            .putFloat(KEY_EVENT_STATS_SCALE, cfg.eventStatsScale.coerceIn(0.4f, 2.5f))
            .putFloat(KEY_EVENT_STATS_FONT_SIZE, cfg.eventStatsFontSizeSp.coerceIn(8f, 24f))
            .putFloat(KEY_EVENT_STATS_WIDTH_PCT, cfg.eventStatsWidthPct.coerceIn(0.40f, 0.98f))
            .putBoolean(KEY_ENABLE_AVATAR_BUBBLE, cfg.enableAvatarBubble)
            .putBoolean(KEY_AVATAR_ON_CHAT, cfg.avatarOnChat)
            .putBoolean(KEY_AVATAR_ON_GIFT, cfg.avatarOnGift)
            .putBoolean(KEY_AVATAR_ON_LIKE, cfg.avatarOnLike)
            .putBoolean(KEY_AVATAR_ON_JOIN, cfg.avatarOnJoin)
            .putBoolean(KEY_AVATAR_ON_FOLLOW, cfg.avatarOnFollow)
            .putBoolean(KEY_AVATAR_ON_SHARE, cfg.avatarOnShare)
            .putFloat(KEY_AVATAR_SIZE_SCALE, cfg.avatarSizeScale.coerceIn(0.2f, 4.5f))
            .putFloat(KEY_AVATAR_SIZE_CHAT, cfg.avatarSizeChat.coerceIn(0.2f, 4.5f))
            .putFloat(KEY_AVATAR_SIZE_GIFT, cfg.avatarSizeGift.coerceIn(0.2f, 4.5f))
            .putFloat(KEY_AVATAR_SIZE_LIKE, cfg.avatarSizeLike.coerceIn(0.2f, 4.5f))
            .putFloat(KEY_AVATAR_SIZE_JOIN, cfg.avatarSizeJoin.coerceIn(0.2f, 4.5f))
            .putFloat(KEY_AVATAR_SIZE_FOLLOW, cfg.avatarSizeFollow.coerceIn(0.2f, 4.5f))
            .putFloat(KEY_AVATAR_SIZE_SHARE, cfg.avatarSizeShare.coerceIn(0.2f, 4.5f))
            .putLong(KEY_AVATAR_FLIGHT_DURATION_MS, cfg.avatarFlightDurationMs.coerceIn(800L, 20000L))
            .putLong(KEY_AVATAR_DURATION_CHAT, cfg.avatarDurationChat.coerceIn(800L, 20000L))
            .putLong(KEY_AVATAR_DURATION_GIFT, cfg.avatarDurationGift.coerceIn(800L, 20000L))
            .putLong(KEY_AVATAR_DURATION_LIKE, cfg.avatarDurationLike.coerceIn(800L, 20000L))
            .putLong(KEY_AVATAR_DURATION_JOIN, cfg.avatarDurationJoin.coerceIn(800L, 20000L))
            .putLong(KEY_AVATAR_DURATION_FOLLOW, cfg.avatarDurationFollow.coerceIn(800L, 20000L))
            .putLong(KEY_AVATAR_DURATION_SHARE, cfg.avatarDurationShare.coerceIn(800L, 20000L))
            .putString(KEY_AVATAR_SPAWN_ZONE, safeAvatarZone)
            .putBoolean(KEY_ENABLE_TOP_LIKERS, cfg.enableTopLikersOverlay)
            .putFloat(KEY_TOP_LIKERS_POS_X, cfg.topLikersPosXpct.coerceIn(-1.5f, 2.5f))
            .putFloat(KEY_TOP_LIKERS_POS_Y, cfg.topLikersPosYpct.coerceIn(-1.5f, 2.5f))
            .putFloat(KEY_TOP_LIKERS_SCALE, cfg.topLikersScale.coerceAtLeast(0.05f))
            .putFloat(KEY_TOP_LIKERS_FONT_SIZE, cfg.topLikersFontSizeSp.coerceAtLeast(3f))
            .putFloat(KEY_TOP_LIKERS_LATIN_FONT_SIZE, cfg.topLikersLatinFontSizeSp.coerceAtLeast(3f))
            .putFloat(KEY_TOP_LIKERS_WIDTH_PCT, cfg.topLikersWidthPct.coerceAtLeast(0.10f))
            .putInt(KEY_TOP_LIKERS_HEIGHT_PAD_V, cfg.topLikersHeightPadV.coerceAtLeast(0))
            .putString(KEY_TOP_LIKERS_ORIENTATION, safeLikersOrient)
            .putString(KEY_TOP_GIFT_ORIENTATION, safeGiftOrient)
            .putFloat(KEY_TOP_GIFT_AVATAR_SCALE, cfg.topGiftAvatarScale.coerceIn(0.5f, 2.2f))
            .putBoolean(KEY_SHOW_TOP_QURAN, cfg.showTopQuran)
            .putBoolean(KEY_SHOW_TOP_SURAH, cfg.showTopSurah)
            .putBoolean(KEY_SHOW_TOP_AYAH, cfg.showTopAyah)
            .putBoolean(KEY_SHOW_TOP_HURUF, cfg.showTopHuruf)
            .putString(KEY_TARGET_GIFT_NAME, cfg.targetGiftName.ifBlank { "Paus" })
            .putInt(KEY_TARGET_GIFT_COINS, cfg.targetGiftCoins.coerceAtLeast(1))
            .putInt(KEY_COIN_RATE_IDR, cfg.coinRateIdr.coerceIn(10, 1000))
            .putString(KEY_TARGET_BIGO_GIFT_NAME, cfg.targetBigoGiftName.ifBlank { "Supercar" })
            .putInt(KEY_TARGET_BIGO_GIFT_BEANS, cfg.targetBigoGiftBeans.coerceAtLeast(1))
            .putInt(KEY_BIGO_BEAN_RATE_IDR, cfg.bigoBeanRateIdr.coerceIn(10, 1000))
            .putBoolean("key_event_frame_proportional_v2", true)
            .apply()

        _config.value = loadConfig().copy(
            customGiftPrefix = cfg.customGiftPrefix,
            customJoinPrefix = cfg.customJoinPrefix,
            customFollowPrefix = cfg.customFollowPrefix,
            customSharePrefix = cfg.customSharePrefix,
            likeDurationMs = cfg.likeDurationMs,
            giftDurationMs = cfg.giftDurationMs,
            commentDurationMs = cfg.commentDurationMs
        )
        runCatching {
            val recv = LiveEventReceiver.getInstance(appContext)
            if (_config.value.enableEventStatsOverlay) recv.refreshEventStatsIfEnabled()
            else com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(appContext, "EVENT_STATS")
            if (_config.value.enableTopLikersOverlay) recv.refreshTopLikersIfEnabled()
            else com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(appContext, "TOP_LIKERS")
        }
    }
}
