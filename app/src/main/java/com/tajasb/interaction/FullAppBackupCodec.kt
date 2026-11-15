package com.tajasb.interaction

import android.content.Context
import com.tajasb.eventrelay.LiveEventConfig
import com.tajasb.eventrelay.LiveEventPreferences
import org.json.JSONArray
import org.json.JSONObject

data class FullAppBackupPayload(
    val schemaVersion: Int = 1,
    val exportedAtMs: Long = System.currentTimeMillis(),
    val isEngineEnabled: Boolean = true,
    val isMlbbMatchActive: Boolean = false,
    val blockTouchDuringChallenge: Boolean = true,
    val isDevice2SimpleMode: Boolean = true,
    val globalDelayBeforeMs: Long = 0L,
    val globalDelayAfterMs: Long = 0L,
    val globalDelayExcludeCooldownRules: Boolean = true,
    val gameCoverAreas: List<GameCoverArea> = emptyList(),
    val activeSceneName: String = "SCENE_UTAMA",
    val floatingConfig: FloatingWindowConfig = FloatingWindowConfig(),
    val rules: List<UniversalTriggerRule> = emptyList(),
    val presets: List<UniversalPreset> = emptyList(),
    val liveEventConfig: LiveEventConfig = LiveEventConfig()
)

object FullAppBackupJsonCodec {

    fun encode(payload: FullAppBackupPayload): String {
        return JSONObject().apply {
            put("type", "StreamBattleFullBackup")
            put("schemaVersion", payload.schemaVersion)
            put("exportedAtMs", payload.exportedAtMs)
            put("universal", JSONObject().apply {
                put("isEngineEnabled", payload.isEngineEnabled)
                put("isMlbbMatchActive", payload.isMlbbMatchActive)
                put("blockTouchDuringChallenge", payload.blockTouchDuringChallenge)
                put("isDevice2SimpleMode", payload.isDevice2SimpleMode)
                put("globalDelayBeforeMs", payload.globalDelayBeforeMs)
                put("globalDelayAfterMs", payload.globalDelayAfterMs)
                put("globalDelayExcludeCooldownRules", payload.globalDelayExcludeCooldownRules)
                put("gameCoverAreas", JSONArray().apply {
                    payload.gameCoverAreas.forEach { put(UniversalInteractionJsonCodec.coverAreaToJson(it)) }
                })
                put("activeSceneName", payload.activeSceneName)
                put("floatingConfig", JSONObject(UniversalInteractionJsonCodec.encodeFloatingConfig(payload.floatingConfig)))
                put("rules", JSONArray().apply {
                    payload.rules.forEach { put(UniversalInteractionJsonCodec.ruleToJson(it)) }
                })
                put("presets", JSONArray().apply {
                    payload.presets.forEach { put(UniversalInteractionJsonCodec.presetToJson(it)) }
                })
            })
            put("liveEvent", liveEventConfigToJson(payload.liveEventConfig))
        }.toString(2)
    }

    fun decode(rawJson: String): Result<FullAppBackupPayload> {
        val trimmed = rawJson.removePrefix("\uFEFF").trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("File/teks JSON backup kosong."))
        }
        return try {
            val root = JSONObject(trimmed)
            val univ = root.optJSONObject("universal")
            if (univ == null && !root.has("rules") && !root.has("liveEvent")) {
                return Result.failure(IllegalArgumentException("Format JSON bukan backup pengaturan StreamBattle/MLx."))
            }
            val uObj = univ ?: root
            val rulesArr = uObj.optJSONArray("rules")
            val rules = if (rulesArr != null) {
                (0 until rulesArr.length()).mapNotNull { i ->
                    rulesArr.optJSONObject(i)?.let { UniversalInteractionJsonCodec.jsonToRule(it, i) }
                }
            } else emptyList()

            val presetsArr = uObj.optJSONArray("presets")
            val presets = if (presetsArr != null) {
                (0 until presetsArr.length()).mapNotNull { i ->
                    presetsArr.optJSONObject(i)?.let { UniversalInteractionJsonCodec.jsonToPreset(it) }
                }
            } else emptyList()

            val fcObj = uObj.optJSONObject("floatingConfig")
            val fc = if (fcObj != null) {
                UniversalInteractionJsonCodec.decodeFloatingConfig(fcObj.toString())
            } else {
                FloatingWindowConfig()
            }

            val coverArr = uObj.optJSONArray("gameCoverAreas")
            val coverAreas = if (coverArr != null) {
                (0 until coverArr.length()).mapNotNull { i ->
                    coverArr.optJSONObject(i)?.let { UniversalInteractionJsonCodec.jsonToCoverArea(it) }
                }
            } else emptyList()

            val leObj = root.optJSONObject("liveEvent")
            val liveCfg = if (leObj != null) jsonToLiveEventConfig(leObj) else LiveEventConfig()

            Result.success(
                FullAppBackupPayload(
                    schemaVersion = root.optInt("schemaVersion", 1),
                    exportedAtMs = root.optLong("exportedAtMs", System.currentTimeMillis()),
                    isEngineEnabled = uObj.optBoolean("isEngineEnabled", true),
                    isMlbbMatchActive = uObj.optBoolean("isMlbbMatchActive", false),
                    blockTouchDuringChallenge = uObj.optBoolean("blockTouchDuringChallenge", true),
                    isDevice2SimpleMode = uObj.optBoolean("isDevice2SimpleMode", true),
                    globalDelayBeforeMs = uObj.optLong("globalDelayBeforeMs", 0L).coerceIn(0L, 60_000L),
                    globalDelayAfterMs = uObj.optLong("globalDelayAfterMs", 0L).coerceIn(0L, 60_000L),
                    globalDelayExcludeCooldownRules = uObj.optBoolean("globalDelayExcludeCooldownRules", true),
                    gameCoverAreas = coverAreas,
                    activeSceneName = uObj.optString("activeSceneName", "SCENE_UTAMA").ifBlank { "SCENE_UTAMA" },
                    floatingConfig = fc,
                    rules = rules,
                    presets = presets,
                    liveEventConfig = liveCfg
                )
            )
        } catch (t: Throwable) {
            Result.failure(IllegalArgumentException("JSON tidak valid: ${t.message}"))
        }
    }

    fun liveEventConfigToJson(c: LiveEventConfig): JSONObject = JSONObject().apply {
        put("serverHost", c.serverHost)
        put("serverPort", c.serverPort)
        put("autoConnect", c.autoConnect)
        put("activePlatform", c.activePlatform)
        put("targetUsername", c.targetUsername)
        put("backupUsernames", JSONArray(c.backupUsernames))
        put("targetBigoId", c.targetBigoId)
        put("backupBigoIds", JSONArray(c.backupBigoIds))
        put("enableComment", c.enableComment)
        put("enableGift", c.enableGift)
        put("enableLike", c.enableLike)
        put("enableJoin", c.enableJoin)
        put("enableFollow", c.enableFollow)
        put("enableShare", c.enableShare)
        put("enableViewerCount", c.enableViewerCount)
        put("enableShopOrder", c.enableShopOrder)
        put("enablePinProduct", c.enablePinProduct)
        put("bannerDurationMs", c.bannerDurationMs)
        put("customDoaPrefix", c.customDoaPrefix)
        put("customGiftPrefix", c.customGiftPrefix)
        put("customJoinPrefix", c.customJoinPrefix)
        put("customFollowPrefix", c.customFollowPrefix)
        put("customSharePrefix", c.customSharePrefix)
        put("separateOverlays", c.separateOverlays)
        put("likePosXpct", c.likePosXpct.toDouble())
        put("likePosYpct", c.likePosYpct.toDouble())
        put("likeScale", c.likeScale.toDouble())
        put("likeDurationMs", c.likeDurationMs)
        put("likeFontSizeSp", c.likeFontSizeSp.toDouble())
        put("giftPosXpct", c.giftPosXpct.toDouble())
        put("giftPosYpct", c.giftPosYpct.toDouble())
        put("giftScale", c.giftScale.toDouble())
        put("giftDurationMs", c.giftDurationMs)
        put("giftFontSizeSp", c.giftFontSizeSp.toDouble())
        put("commentPosXpct", c.commentPosXpct.toDouble())
        put("commentPosYpct", c.commentPosYpct.toDouble())
        put("commentScale", c.commentScale.toDouble())
        put("commentDurationMs", c.commentDurationMs)
        put("commentFontSizeSp", c.commentFontSizeSp.toDouble())
        put("enableEventStatsOverlay", c.enableEventStatsOverlay)
        put("showDiamondsInAnalytics", c.showDiamondsInAnalytics)
        put("eventStatsPosXpct", c.eventStatsPosXpct.toDouble())
        put("eventStatsPosYpct", c.eventStatsPosYpct.toDouble())
        put("eventStatsScale", c.eventStatsScale.toDouble())
        put("eventStatsFontSizeSp", c.eventStatsFontSizeSp.toDouble())
        put("eventStatsWidthPct", c.eventStatsWidthPct.toDouble())
        put("enableAvatarBubble", c.enableAvatarBubble)
        put("avatarOnChat", c.avatarOnChat)
        put("avatarOnGift", c.avatarOnGift)
        put("avatarOnLike", c.avatarOnLike)
        put("avatarOnJoin", c.avatarOnJoin)
        put("avatarOnFollow", c.avatarOnFollow)
        put("avatarOnShare", c.avatarOnShare)
        put("avatarSizeScale", c.avatarSizeScale.toDouble())
        put("avatarSizeChat", c.avatarSizeChat.toDouble())
        put("avatarSizeGift", c.avatarSizeGift.toDouble())
        put("avatarSizeLike", c.avatarSizeLike.toDouble())
        put("avatarSizeJoin", c.avatarSizeJoin.toDouble())
        put("avatarSizeFollow", c.avatarSizeFollow.toDouble())
        put("avatarSizeShare", c.avatarSizeShare.toDouble())
        put("avatarFlightDurationMs", c.avatarFlightDurationMs)
        put("avatarDurationChat", c.avatarDurationChat)
        put("avatarDurationGift", c.avatarDurationGift)
        put("avatarDurationLike", c.avatarDurationLike)
        put("avatarDurationJoin", c.avatarDurationJoin)
        put("avatarDurationFollow", c.avatarDurationFollow)
        put("avatarDurationShare", c.avatarDurationShare)
        put("avatarSpawnZone", c.avatarSpawnZone)
        put("enableTopLikersOverlay", c.enableTopLikersOverlay)
        put("topLikersPosXpct", c.topLikersPosXpct.toDouble())
        put("topLikersPosYpct", c.topLikersPosYpct.toDouble())
        put("topLikersScale", c.topLikersScale.toDouble())
        put("topLikersFontSizeSp", c.topLikersFontSizeSp.toDouble())
        put("topLikersLatinFontSizeSp", c.topLikersLatinFontSizeSp.toDouble())
        put("topLikersWidthPct", c.topLikersWidthPct.toDouble())
        put("topLikersHeightPadV", c.topLikersHeightPadV)
        put("topLikersOrientation", c.topLikersOrientation)
        put("topGiftOrientation", c.topGiftOrientation)
        put("topGiftAvatarScale", c.topGiftAvatarScale.toDouble())
        put("showTopQuran", c.showTopQuran)
        put("showTopSurah", c.showTopSurah)
        put("showTopAyah", c.showTopAyah)
        put("showTopHuruf", c.showTopHuruf)
        put("targetGiftName", c.targetGiftName)
        put("targetGiftCoins", c.targetGiftCoins)
        put("coinRateIdr", c.coinRateIdr)
        put("targetBigoGiftName", c.targetBigoGiftName)
        put("targetBigoGiftBeans", c.targetBigoGiftBeans)
        put("bigoBeanRateIdr", c.bigoBeanRateIdr)
    }

    fun jsonToLiveEventConfig(o: JSONObject): LiveEventConfig {
        fun strList(key: String): List<String> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                arr.optString(i, "").trim().takeIf { it.isNotBlank() }
            }
        }
        return LiveEventConfig(
            serverHost = o.optString("serverHost", "192.168.43.1"),
            serverPort = o.optInt("serverPort", 8765),
            autoConnect = o.optBoolean("autoConnect", true),
            activePlatform = o.optString("activePlatform", "TIKTOK"),
            targetUsername = o.optString("targetUsername", ""),
            backupUsernames = strList("backupUsernames"),
            targetBigoId = o.optString("targetBigoId", ""),
            backupBigoIds = strList("backupBigoIds"),
            enableComment = o.optBoolean("enableComment", true),
            enableGift = o.optBoolean("enableGift", true),
            enableLike = o.optBoolean("enableLike", true),
            enableJoin = o.optBoolean("enableJoin", true),
            enableFollow = o.optBoolean("enableFollow", true),
            enableShare = o.optBoolean("enableShare", true),
            enableViewerCount = o.optBoolean("enableViewerCount", true),
            enableShopOrder = o.optBoolean("enableShopOrder", true),
            enablePinProduct = o.optBoolean("enablePinProduct", true),
            bannerDurationMs = o.optLong("bannerDurationMs", 4000L),
            customDoaPrefix = o.optString("customDoaPrefix", "Doa"),
            customGiftPrefix = o.optString("customGiftPrefix", "Saweran"),
            customJoinPrefix = o.optString("customJoinPrefix", "Ahlan"),
            customFollowPrefix = o.optString("customFollowPrefix", "Syukron"),
            customSharePrefix = o.optString("customSharePrefix", "Share"),
            separateOverlays = o.optBoolean("separateOverlays", true),
            likePosXpct = o.optDouble("likePosXpct", 0.50).toFloat(),
            likePosYpct = o.optDouble("likePosYpct", 0.78).toFloat(),
            likeScale = o.optDouble("likeScale", 1.0).toFloat(),
            likeDurationMs = o.optLong("likeDurationMs", 2400L),
            likeFontSizeSp = o.optDouble("likeFontSizeSp", 16.0).toFloat(),
            giftPosXpct = o.optDouble("giftPosXpct", 0.50).toFloat(),
            giftPosYpct = o.optDouble("giftPosYpct", 0.26).toFloat(),
            giftScale = o.optDouble("giftScale", 1.0).toFloat(),
            giftDurationMs = o.optLong("giftDurationMs", 4200L),
            giftFontSizeSp = o.optDouble("giftFontSizeSp", 17.0).toFloat(),
            commentPosXpct = o.optDouble("commentPosXpct", 0.50).toFloat(),
            commentPosYpct = o.optDouble("commentPosYpct", 0.50).toFloat(),
            commentScale = o.optDouble("commentScale", 1.0).toFloat(),
            commentDurationMs = o.optLong("commentDurationMs", 3800L),
            commentFontSizeSp = o.optDouble("commentFontSizeSp", 16.5).toFloat(),
            enableEventStatsOverlay = o.optBoolean("enableEventStatsOverlay", false),
            showDiamondsInAnalytics = o.optBoolean("showDiamondsInAnalytics", false),
            eventStatsPosXpct = o.optDouble("eventStatsPosXpct", 0.50).toFloat(),
            eventStatsPosYpct = o.optDouble("eventStatsPosYpct", 0.08).toFloat(),
            eventStatsScale = o.optDouble("eventStatsScale", 1.0).toFloat(),
            eventStatsFontSizeSp = o.optDouble("eventStatsFontSizeSp", 13.5).toFloat(),
            eventStatsWidthPct = o.optDouble("eventStatsWidthPct", 0.98).toFloat(),
            enableAvatarBubble = o.optBoolean("enableAvatarBubble", true),
            avatarOnChat = o.optBoolean("avatarOnChat", true),
            avatarOnGift = o.optBoolean("avatarOnGift", true),
            avatarOnLike = o.optBoolean("avatarOnLike", true),
            avatarOnJoin = o.optBoolean("avatarOnJoin", true),
            avatarOnFollow = o.optBoolean("avatarOnFollow", true),
            avatarOnShare = o.optBoolean("avatarOnShare", true),
            avatarSizeScale = o.optDouble("avatarSizeScale", 1.0).toFloat(),
            avatarSizeChat = o.optDouble("avatarSizeChat", 1.0).toFloat(),
            avatarSizeGift = o.optDouble("avatarSizeGift", 1.30).toFloat(),
            avatarSizeLike = o.optDouble("avatarSizeLike", 0.85).toFloat(),
            avatarSizeJoin = o.optDouble("avatarSizeJoin", 0.95).toFloat(),
            avatarSizeFollow = o.optDouble("avatarSizeFollow", 1.15).toFloat(),
            avatarSizeShare = o.optDouble("avatarSizeShare", 1.10).toFloat(),
            avatarFlightDurationMs = o.optLong("avatarFlightDurationMs", 4200L),
            avatarDurationChat = o.optLong("avatarDurationChat", 4200L),
            avatarDurationGift = o.optLong("avatarDurationGift", 5200L),
            avatarDurationLike = o.optLong("avatarDurationLike", 3200L),
            avatarDurationJoin = o.optLong("avatarDurationJoin", 3800L),
            avatarDurationFollow = o.optLong("avatarDurationFollow", 4500L),
            avatarDurationShare = o.optLong("avatarDurationShare", 4200L),
            avatarSpawnZone = o.optString("avatarSpawnZone", "SPREAD"),
            enableTopLikersOverlay = o.optBoolean("enableTopLikersOverlay", false),
            topLikersPosXpct = o.optDouble("topLikersPosXpct", 0.50).toFloat(),
            topLikersPosYpct = o.optDouble("topLikersPosYpct", 0.85).toFloat(),
            topLikersScale = o.optDouble("topLikersScale", 1.0).toFloat(),
            topLikersFontSizeSp = o.optDouble("topLikersFontSizeSp", 11.5).toFloat(),
            topLikersLatinFontSizeSp = o.optDouble("topLikersLatinFontSizeSp", 11.5).toFloat(),
            topLikersWidthPct = o.optDouble("topLikersWidthPct", 0.98).toFloat(),
            topLikersHeightPadV = o.optInt("topLikersHeightPadV", 8),
            topLikersOrientation = o.optString("topLikersOrientation", "HORIZONTAL"),
            topGiftOrientation = o.optString("topGiftOrientation", "HORIZONTAL"),
            topGiftAvatarScale = o.optDouble("topGiftAvatarScale", 1.0).toFloat(),
            showTopQuran = o.optBoolean("showTopQuran", true),
            showTopSurah = o.optBoolean("showTopSurah", true),
            showTopAyah = o.optBoolean("showTopAyah", true),
            showTopHuruf = o.optBoolean("showTopHuruf", true),
            targetGiftName = o.optString("targetGiftName", "Paus"),
            targetGiftCoins = o.optInt("targetGiftCoins", 2150),
            coinRateIdr = o.optInt("coinRateIdr", 80),
            targetBigoGiftName = o.optString("targetBigoGiftName", "Supercar"),
            targetBigoGiftBeans = o.optInt("targetBigoGiftBeans", 3000),
            bigoBeanRateIdr = o.optInt("bigoBeanRateIdr", 80)
        )
    }
}

object FullAppBackupManager {
    fun exportFullBackupJson(context: Context): String {
        val engine = UniversalTriggerEngine.getInstance(context)
        val livePrefs = LiveEventPreferences.getInstance(context)
        val u = engine.uiState.value
        val payload = FullAppBackupPayload(
            isEngineEnabled = u.isEngineEnabled,
            isMlbbMatchActive = u.isMlbbMatchActive,
            blockTouchDuringChallenge = u.blockTouchDuringChallenge,
            isDevice2SimpleMode = u.isDevice2SimpleMode,
            globalDelayBeforeMs = u.globalDelayBeforeMs,
            globalDelayAfterMs = u.globalDelayAfterMs,
            globalDelayExcludeCooldownRules = u.globalDelayExcludeCooldownRules,
            gameCoverAreas = u.gameCoverAreas,
            activeSceneName = u.activeSceneName,
            floatingConfig = u.floatingConfig,
            rules = u.rules,
            presets = u.presets,
            liveEventConfig = livePrefs.config.value
        )
        return FullAppBackupJsonCodec.encode(payload)
    }

    fun importFullBackupJson(context: Context, rawJson: String): Result<String> {
        val decoded = FullAppBackupJsonCodec.decode(rawJson).getOrElse {
            return Result.failure(it)
        }
        val engine = UniversalTriggerEngine.getInstance(context)
        val livePrefs = LiveEventPreferences.getInstance(context)
        engine.restoreFullUniversalState(decoded)
        livePrefs.importFullConfig(decoded.liveEventConfig)
        return Result.success(
            "Restore sukses: ${decoded.rules.size} rule, ${decoded.presets.size} preset, & seluruh pengaturan Live tersinkron."
        )
    }
}
