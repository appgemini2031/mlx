package com.tajasb.rtmp

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class RtmpPlatformPreset(
    val id: String,
    val label: String,
    val badgeIcon: String,
    val defaultServerUrl: String,
    val backupServerUrl: String? = null,
    val helperTip: String
) {
    YOUTUBE(
        id = "YOUTUBE",
        label = "YouTube",
        badgeIcon = "🔴",
        defaultServerUrl = "rtmp://a.rtmp.youtube.com/live2",
        backupServerUrl = "rtmp://b.rtmp.youtube.com/live2?backup=1",
        helperTip = "Buka YouTube Studio > Go Live > Salin 'Stream Key' (Tersedia Server Utama & Cadangan)."
    ),
    FACEBOOK(
        id = "FACEBOOK",
        label = "Facebook",
        badgeIcon = "🔵",
        defaultServerUrl = "rtmps://live-api-s.facebook.com:443/rtmp/",
        backupServerUrl = null,
        helperTip = "Buka Facebook Live Producer > Pilih 'Streaming Software' > Salin 'Stream Key'."
    ),
    INSTAGRAM(
        id = "INSTAGRAM",
        label = "Instagram",
        badgeIcon = "🟣",
        defaultServerUrl = "rtmps://edgetee-upload-cgk2-1.xx.fbcdn.net:443/rtmp/",
        backupServerUrl = null,
        helperTip = "Buka Instagram di Web > Buat > Siaran Langsung > Salin Stream Key (Server default CGK Jakarta otomatis terisi)."
    ),
    TIKTOK(
        id = "TIKTOK",
        label = "TikTok",
        badgeIcon = "⚫",
        defaultServerUrl = "rtmp://push-rtmp-l11-va01.tiktokcdn.com/game/",
        backupServerUrl = null,
        helperTip = "Gunakan Server URL & Stream Key dari akses RTMP TikTok Live Studio / Agensi."
    ),
    CUSTOM(
        id = "CUSTOM",
        label = "Custom RTMP",
        badgeIcon = "🟠",
        defaultServerUrl = "rtmp://",
        backupServerUrl = null,
        helperTip = "Mendukung semua server rtmp:// (port 1935) maupun rtmps:// SSL (port 443)."
    );

    companion object {
        fun fromId(id: String): RtmpPlatformPreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: YOUTUBE
        }
    }
}

enum class RtmpBitratePreset(
    val id: String,
    val label: String,
    val shortBadge: String,
    val videoBitrateKbps: Int,
    val audioBitrateKbps: Int,
    val width: Int,
    val height: Int,
    val fps: Int
) {
    LOW_1500(
        id = "LOW_1500",
        label = "Hemat (1500 kbps • 480p)",
        shortBadge = "1.5 Mbps • 480p",
        videoBitrateKbps = 1500,
        audioBitrateKbps = 96,
        width = 848,
        height = 480,
        fps = 24
    ),
    BALANCED_2500(
        id = "BALANCED_2500",
        label = "Stabil (2500 kbps • 720p HD)",
        shortBadge = "2.5 Mbps • 720p",
        videoBitrateKbps = 2500,
        audioBitrateKbps = 128,
        width = 1280,
        height = 720,
        fps = 30
    ),
    HIGH_4500(
        id = "HIGH_4500",
        label = "Tajam (4500 kbps • 1080p FHD)",
        shortBadge = "4.5 Mbps • 1080p",
        videoBitrateKbps = 4500,
        audioBitrateKbps = 160,
        width = 1920,
        height = 1080,
        fps = 30
    ),
    ULTRA_6000(
        id = "ULTRA_6000",
        label = "Ultra (6000 kbps • 1080p 60FPS)",
        shortBadge = "6.0 Mbps • 60FPS",
        videoBitrateKbps = 6000,
        audioBitrateKbps = 192,
        width = 1920,
        height = 1080,
        fps = 60
    );

    companion object {
        fun fromId(id: String): RtmpBitratePreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BALANCED_2500
        }
    }
}

enum class RtmpStreamState(val label: String) {
    IDLE("SIAGA"),
    TESTING("TES SERVER"),
    CONNECTING("MENYAMBUNGKAN"),
    HANDSHAKING("HANDSHAKE RTMP"),
    LIVE("LIVE ON AIR"),
    RECONNECTING("RECONNECTING"),
    ERROR("GANGGUAN")
}

data class RtmpConfig(
    val platform: RtmpPlatformPreset = RtmpPlatformPreset.YOUTUBE,
    val serverUrl: String = RtmpPlatformPreset.YOUTUBE.defaultServerUrl,
    val streamKey: String = "",
    val bitratePreset: RtmpBitratePreset = RtmpBitratePreset.BALANCED_2500,
    val isPortrait: Boolean = true,
    val autoReconnect: Boolean = true,
    val syncPlaylistAndSpill: Boolean = true,
    val applyAntiHashMetadata: Boolean = true,
    val isLiveTranscoderEnabled: Boolean = true,
    val forceKeyframeIntervalSec: Int = 2
) {
    val targetWidth: Int
        get() = if (isPortrait) minOf(bitratePreset.width, bitratePreset.height) else maxOf(bitratePreset.width, bitratePreset.height)

    val targetHeight: Int
        get() = if (isPortrait) maxOf(bitratePreset.width, bitratePreset.height) else minOf(bitratePreset.width, bitratePreset.height)

    fun buildFullEndpointUrl(): String {
        val cleanServer = serverUrl.trim().trimEnd('/')
        val cleanKey = streamKey.trim().trimStart('/')
        return if (cleanKey.isEmpty()) cleanServer else "$cleanServer/$cleanKey"
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("platform", platform.id)
            put("serverUrl", serverUrl)
            put("streamKey", streamKey)
            put("bitratePreset", bitratePreset.id)
            put("isPortrait", isPortrait)
            put("autoReconnect", autoReconnect)
            put("syncPlaylistAndSpill", syncPlaylistAndSpill)
            put("applyAntiHashMetadata", applyAntiHashMetadata)
            put("isLiveTranscoderEnabled", isLiveTranscoderEnabled)
            put("forceKeyframeIntervalSec", forceKeyframeIntervalSec)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): RtmpConfig {
            val platform = RtmpPlatformPreset.fromId(obj.optString("platform", RtmpPlatformPreset.YOUTUBE.id))
            return RtmpConfig(
                platform = platform,
                serverUrl = obj.optString("serverUrl", platform.defaultServerUrl),
                streamKey = obj.optString("streamKey", ""),
                bitratePreset = RtmpBitratePreset.fromId(obj.optString("bitratePreset", RtmpBitratePreset.BALANCED_2500.id)),
                isPortrait = obj.optBoolean("isPortrait", true),
                autoReconnect = obj.optBoolean("autoReconnect", true),
                syncPlaylistAndSpill = obj.optBoolean("syncPlaylistAndSpill", true),
                applyAntiHashMetadata = obj.optBoolean("applyAntiHashMetadata", true),
                isLiveTranscoderEnabled = obj.optBoolean("isLiveTranscoderEnabled", true),
                forceKeyframeIntervalSec = obj.optInt("forceKeyframeIntervalSec", 2)
            )
        }
    }
}

data class RtmpSavedProfile(
    val id: String = UUID.randomUUID().toString(),
    val profileName: String,
    val config: RtmpConfig,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("profileName", profileName)
            put("config", config.toJson())
            put("updatedAtMs", updatedAtMs)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): RtmpSavedProfile {
            return RtmpSavedProfile(
                id = obj.optString("id", UUID.randomUUID().toString()),
                profileName = obj.optString("profileName", "Profil RTMP"),
                config = obj.optJSONObject("config")?.let { RtmpConfig.fromJson(it) } ?: RtmpConfig(),
                updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis())
            )
        }

        fun listToJsonString(profiles: List<RtmpSavedProfile>): String {
            val arr = JSONArray()
            profiles.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJsonString(raw: String?): List<RtmpSavedProfile> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { add(fromJson(it)) }
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }
}

data class RtmpTelemetry(
    val state: RtmpStreamState = RtmpStreamState.IDLE,
    val statusMessage: String = "Siap melakukan streaming ke YouTube, Facebook, Instagram, atau TikTok.",
    val uptimeSeconds: Long = 0L,
    val currentBitrateKbps: Int = 0,
    val currentFps: Int = 0,
    val totalBytesSent: Long = 0L,
    val framesSent: Long = 0L,
    val reconnectCount: Int = 0,
    val activeVideoName: String = "-",
    val connectedHost: String = "-",
    val lastError: String? = null,
    val keyframeIntervalSec: Float = 2.0f,
    val audioSourceDescription: String = "Otomatis"
) {
    val isBroadcasting: Boolean
        get() = state == RtmpStreamState.CONNECTING ||
            state == RtmpStreamState.HANDSHAKING ||
            state == RtmpStreamState.LIVE ||
            state == RtmpStreamState.RECONNECTING
}
