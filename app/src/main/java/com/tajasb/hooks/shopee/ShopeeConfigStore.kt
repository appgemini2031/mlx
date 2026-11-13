package com.tajasb.hooks.shopee

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Konfigurasi VCam terisolasi khusus Shopee (Live & Video), termasuk versi resmi
 * (`com.shopee.id`, `com.shopee.app`, dll.) maupun APK kloning paket acak
 * (mis. `vhgh.wzia.dppd.apts.ohqa.qbgo`).
 *
 * Disimpan terpisah di SharedPreferences "vcam_config_shopee" milik proses Tajasb
 * dan diekspos ke proses Shopee hanya melalui VcamContentProvider (/shopee/...).
 */
data class ShopeeVcamConfig(
    val enabled: Boolean = true,
    val videoPath: String = "",
    val audioPath: String = "",
    val rotation: Int = 0,
    val isMirrored: Boolean = false,
    val isVerticalFlipped: Boolean = false,
    val zoomFactor: Float = 1.0f,
    val scaleMode: String = "CROP",
    val isPaused: Boolean = false,
    val isMuted: Boolean = false,
    val isVideoAudioMuted: Boolean = false,
    val isMp3AudioPaused: Boolean = true,
    val fps: Int = 30,
    val bypassScannerAndKyc: Boolean = true,
    val onlyInLiveOrVideoActivity: Boolean = false,
    val customClonedPackages: String = "",
    val version: Long = 1L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("videoPath", videoPath)
        put("audioPath", audioPath)
        put("rotation", rotation)
        put("isMirrored", isMirrored)
        put("isVerticalFlipped", isVerticalFlipped)
        put("zoomFactor", zoomFactor.toDouble())
        put("scaleMode", scaleMode)
        put("isPaused", isPaused)
        put("isMuted", isMuted)
        put("isVideoAudioMuted", isVideoAudioMuted)
        put("isMp3AudioPaused", isMp3AudioPaused)
        put("fps", fps)
        put("bypassScannerAndKyc", bypassScannerAndKyc)
        put("onlyInLiveOrVideoActivity", onlyInLiveOrVideoActivity)
        put("customClonedPackages", customClonedPackages)
        put("version", version)
    }

    companion object {
        fun fromJson(json: JSONObject): ShopeeVcamConfig {
            return ShopeeVcamConfig(
                enabled = json.optBoolean("enabled", true),
                videoPath = json.optString("videoPath", ""),
                audioPath = json.optString("audioPath", ""),
                rotation = json.optInt("rotation", 0),
                isMirrored = json.optBoolean("isMirrored", false),
                isVerticalFlipped = json.optBoolean("isVerticalFlipped", false),
                zoomFactor = json.optDouble("zoomFactor", 1.0).toFloat().coerceIn(0.3f, 5.0f),
                scaleMode = json.optString("scaleMode", "CROP"),
                isPaused = json.optBoolean("isPaused", false),
                isMuted = json.optBoolean("isMuted", false),
                isVideoAudioMuted = json.optBoolean("isVideoAudioMuted", false),
                isMp3AudioPaused = json.optBoolean("isMp3AudioPaused", true),
                fps = json.optInt("fps", 30).coerceIn(15, 60),
                bypassScannerAndKyc = json.optBoolean("bypassScannerAndKyc", true),
                onlyInLiveOrVideoActivity = json.optBoolean("onlyInLiveOrVideoActivity", false),
                customClonedPackages = json.optString("customClonedPackages", ""),
                version = json.optLong("version", 1L)
            )
        }
    }
}

/**
 * Pengelola state & penyimpanan konfigurasi Shopee di sisi aplikasi controller (com.tajasb).
 * DILARANG dipanggil langsung dari dalam proses Shopee karena perbedaan sandbox UID;
 * proses Shopee wajib membaca melalui VcamContentProvider (content://com.tajasb.provider/shopee/config).
 */
object ShopeeConfigStore {
    const val TAG = "Tajasb-Shopee"
    const val PREFS_NAME = "vcam_config_shopee"
    const val ACTION_SHOPEE_CMD = "com.tajasb.ACTION_SHOPEE_CMD"
    const val SHOPEE_MEDIA_DIR = "shopee_vcam"

    val SHOPEE_PACKAGES = listOf(
        "com.shopee.id",
        "com.shopee.app",
        "com.shopee.my",
        "com.shopee.sg",
        "com.shopee.ph",
        "com.shopee.th",
        "com.shopee.vn",
        "com.shopee.tw",
        "com.shopee.br",
        "vhgh.wzia.dppd.apts.ohqa.qbgo"
    )

    private val discoveredShopeePackages = java.util.concurrent.CopyOnWriteArraySet<String>()

    fun registerDiscoveredPackage(pkg: String?) {
        if (!pkg.isNullOrBlank()) {
            discoveredShopeePackages.add(pkg.trim())
        }
    }

    private const val KEY_ENABLED = "shopee_enabled"
    private const val KEY_VIDEO_PATH = "shopee_video_path"
    private const val KEY_AUDIO_PATH = "shopee_audio_path"
    private const val KEY_ROTATION = "shopee_rotation"
    private const val KEY_MIRRORED = "shopee_is_mirrored"
    private const val KEY_FLIP_V = "shopee_is_vertical_flipped"
    private const val KEY_ZOOM = "shopee_zoom_factor"
    private const val KEY_SCALE_MODE = "shopee_scale_mode"
    private const val KEY_PAUSED = "shopee_is_paused"
    private const val KEY_MUTED = "shopee_is_muted"
    private const val KEY_VIDEO_AUDIO_MUTED = "shopee_is_video_audio_muted"
    private const val KEY_MP3_PAUSED = "shopee_is_mp3_paused"
    private const val KEY_FPS = "shopee_fps"
    private const val KEY_BYPASS_SCANNER_KYC = "shopee_bypass_scanner_kyc"
    private const val KEY_ONLY_LIVE_VIDEO = "shopee_only_live_video_activity"
    private const val KEY_CUSTOM_CLONED_PKGS = "shopee_custom_cloned_pkgs"
    private const val KEY_VERSION = "shopee_version"

    fun parseCustomClonedPackages(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',', ';', '\n', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains('.') }
            .toSet()
    }

    fun getConfig(context: Context): ShopeeVcamConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ShopeeVcamConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            videoPath = prefs.getString(KEY_VIDEO_PATH, "") ?: "",
            audioPath = prefs.getString(KEY_AUDIO_PATH, "") ?: "",
            rotation = prefs.getInt(KEY_ROTATION, 0),
            isMirrored = prefs.getBoolean(KEY_MIRRORED, false),
            isVerticalFlipped = prefs.getBoolean(KEY_FLIP_V, false),
            zoomFactor = prefs.getFloat(KEY_ZOOM, 1.0f).coerceIn(0.3f, 5.0f),
            scaleMode = prefs.getString(KEY_SCALE_MODE, "CROP") ?: "CROP",
            isPaused = prefs.getBoolean(KEY_PAUSED, false),
            isMuted = prefs.getBoolean(KEY_MUTED, false),
            isVideoAudioMuted = prefs.getBoolean(KEY_VIDEO_AUDIO_MUTED, false),
            isMp3AudioPaused = prefs.getBoolean(KEY_MP3_PAUSED, true),
            fps = prefs.getInt(KEY_FPS, 30).coerceIn(15, 60),
            bypassScannerAndKyc = prefs.getBoolean(KEY_BYPASS_SCANNER_KYC, true),
            onlyInLiveOrVideoActivity = prefs.getBoolean(KEY_ONLY_LIVE_VIDEO, false),
            customClonedPackages = prefs.getString(KEY_CUSTOM_CLONED_PKGS, "") ?: "",
            version = prefs.getLong(KEY_VERSION, 1L)
        )
    }

    fun saveConfig(context: Context, config: ShopeeVcamConfig, broadcastUpdate: Boolean = true): ShopeeVcamConfig {
        val updated = config.copy(version = System.currentTimeMillis())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, updated.enabled)
            .putString(KEY_VIDEO_PATH, updated.videoPath)
            .putString(KEY_AUDIO_PATH, updated.audioPath)
            .putInt(KEY_ROTATION, updated.rotation)
            .putBoolean(KEY_MIRRORED, updated.isMirrored)
            .putBoolean(KEY_FLIP_V, updated.isVerticalFlipped)
            .putFloat(KEY_ZOOM, updated.zoomFactor)
            .putString(KEY_SCALE_MODE, updated.scaleMode)
            .putBoolean(KEY_PAUSED, updated.isPaused)
            .putBoolean(KEY_MUTED, updated.isMuted)
            .putBoolean(KEY_VIDEO_AUDIO_MUTED, updated.isVideoAudioMuted)
            .putBoolean(KEY_MP3_PAUSED, updated.isMp3AudioPaused)
            .putInt(KEY_FPS, updated.fps)
            .putBoolean(KEY_BYPASS_SCANNER_KYC, updated.bypassScannerAndKyc)
            .putBoolean(KEY_ONLY_LIVE_VIDEO, updated.onlyInLiveOrVideoActivity)
            .putString(KEY_CUSTOM_CLONED_PKGS, updated.customClonedPackages)
            .putLong(KEY_VERSION, updated.version)
            .apply()

        writeConfigJsonFile(context, updated)
        if (broadcastUpdate) {
            sendCommandBroadcast(context, "UPDATE_CONFIG", updated)
        }
        return updated
    }

    fun getEffectiveConfig(context: Context): ShopeeVcamConfig {
        val local = getConfig(context)
        val hasDedicatedVideo = local.videoPath.isNotBlank() ||
            File(getMediaDirectory(context), "virtual.mp4").let { it.exists() && it.length() > 0L }
        if (hasDedicatedVideo) {
            return local
        }

        // Fallback read-only ke video aktif di VcamRepository jika override Shopee belum disetel
        return try {
            val repo = com.tajasb.data.VcamRepository.getInstance(context)
            val cfg = repo.config.value
            val activeVideoPath = cfg.currentVideo?.path?.takeIf { it.isNotBlank() } ?: cfg.videoPath
            val activeAudioPath = cfg.currentAudio?.path?.takeIf { it.isNotBlank() } ?: cfg.audioPath
            val resolvedFile = if (activeVideoPath.isNotBlank()) File(activeVideoPath) else null
            val fallbackPath = if (resolvedFile != null && resolvedFile.exists() && resolvedFile.length() > 0L) {
                resolvedFile.absolutePath
            } else {
                File(context.filesDir, "Camera1/virtual.mp4")
                    .takeIf { it.exists() && it.length() > 0L }
                    ?.absolutePath ?: activeVideoPath
            }

            local.copy(
                enabled = local.enabled,
                videoPath = fallbackPath,
                audioPath = activeAudioPath,
                rotation = cfg.rotation,
                isMirrored = cfg.isMirrored,
                isVerticalFlipped = cfg.isVerticalFlipped,
                zoomFactor = cfg.zoomFactor.coerceIn(0.3f, 5.0f),
                scaleMode = cfg.scaleMode.name,
                isPaused = cfg.isVideoPaused,
                isMuted = !cfg.audioEnabled,
                isVideoAudioMuted = cfg.currentVideo?.isMuted ?: cfg.isVideoAudioMuted,
                isMp3AudioPaused = cfg.isMp3AudioPaused,
                fps = cfg.fps.coerceIn(15, 60),
                version = maxOf(local.version, repo.playlistVersion)
            )
        } catch (_: Throwable) {
            local
        }
    }

    fun getMediaDirectory(context: Context): File {
        val dir = File(context.filesDir, SHOPEE_MEDIA_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun resolveShopeeVideoFile(context: Context): File? {
        val cfg = getConfig(context)
        if (cfg.videoPath.isNotBlank()) {
            val f = File(cfg.videoPath)
            if (f.exists() && f.canRead() && f.length() > 0L) {
                return f
            }
        }
        val dedicated = File(getMediaDirectory(context), "virtual.mp4")
        if (dedicated.exists() && dedicated.canRead() && dedicated.length() > 0L) {
            return dedicated
        }

        // Fallback read-only ke video aktif di VcamRepository / Camera1 internal vault
        try {
            val repo = com.tajasb.data.VcamRepository.getInstance(context)
            val repoCfg = repo.config.value
            val repoPath = repoCfg.currentVideo?.path?.takeIf { it.isNotBlank() } ?: repoCfg.videoPath
            if (repoPath.isNotBlank()) {
                val rf = File(repoPath)
                if (rf.exists() && rf.canRead() && rf.length() > 0L) {
                    return rf
                }
            }
        } catch (_: Throwable) {}

        val internalVirtual = File(context.filesDir, "Camera1/virtual.mp4")
        if (internalVirtual.exists() && internalVirtual.canRead() && internalVirtual.length() > 0L) {
            return internalVirtual
        }
        return null
    }

    fun resolveShopeeAudioFile(context: Context): File? {
        val cfg = getConfig(context)
        if (cfg.audioPath.isNotBlank()) {
            val f = File(cfg.audioPath)
            if (f.exists() && f.canRead() && f.length() > 0L) {
                return f
            }
        }
        val names = listOf("audio.mp3", "audio.wav", "audio.m4a", "audio.aac", "audio.ogg")
        val dir = getMediaDirectory(context)
        for (name in names) {
            val f = File(dir, name)
            if (f.exists() && f.canRead() && f.length() > 0L) {
                return f
            }
        }

        try {
            val repo = com.tajasb.data.VcamRepository.getInstance(context)
            val repoCfg = repo.config.value
            val repoAudio = repoCfg.currentAudio?.path?.takeIf { it.isNotBlank() } ?: repoCfg.audioPath
            if (repoAudio.isNotBlank()) {
                val af = File(repoAudio)
                if (af.exists() && af.canRead() && af.length() > 0L) {
                    return af
                }
            }
        } catch (_: Throwable) {}

        val internalCamDir = File(context.filesDir, "Camera1")
        for (name in names) {
            val f = File(internalCamDir, name)
            if (f.exists() && f.canRead() && f.length() > 0L) {
                return f
            }
        }
        return null
    }

    fun getOrCreateConfigJsonFile(context: Context): File {
        val cfg = getEffectiveConfig(context)
        return writeConfigJsonFile(context, cfg)
    }

    private fun writeConfigJsonFile(context: Context, config: ShopeeVcamConfig): File {
        val dir = getMediaDirectory(context)
        val outFile = File(dir, "config.json")
        val tmpFile = File(dir, "config.json.tmp")
        try {
            tmpFile.writeText(config.toJson().toString())
            if (outFile.exists()) {
                outFile.delete()
            }
            tmpFile.renameTo(outFile)
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal menulis config.json Shopee: ${t.message}")
        }
        return outFile
    }

    fun sendCommandBroadcast(context: Context, command: String, config: ShopeeVcamConfig = getConfig(context)) {
        val customPkgs = parseCustomClonedPackages(config.customClonedPackages)
        val targets = (SHOPEE_PACKAGES + discoveredShopeePackages + customPkgs).distinct()
        for (pkg in targets) {
            try {
                val intent = Intent(ACTION_SHOPEE_CMD).apply {
                    setPackage(pkg)
                    putExtra("extra_shopee_cmd", command)
                    putExtra("extra_enabled", config.enabled)
                    putExtra("extra_video_path", config.videoPath)
                    putExtra("extra_audio_path", config.audioPath)
                    putExtra("extra_rotation", config.rotation)
                    putExtra("extra_mirror", config.isMirrored)
                    putExtra("extra_flip_v", config.isVerticalFlipped)
                    putExtra("extra_zoom_factor", config.zoomFactor)
                    putExtra("extra_scale_mode", config.scaleMode)
                    putExtra("extra_paused", config.isPaused)
                    putExtra("extra_muted", config.isMuted)
                    putExtra("extra_video_audio_muted", config.isVideoAudioMuted)
                    putExtra("extra_mp3_paused", config.isMp3AudioPaused)
                    putExtra("extra_fps", config.fps)
                    putExtra("extra_bypass_scanner_kyc", config.bypassScannerAndKyc)
                    putExtra("extra_only_live_video", config.onlyInLiveOrVideoActivity)
                    putExtra("extra_custom_cloned_pkgs", config.customClonedPackages)
                    putExtra("extra_version", config.version)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(intent)
            } catch (t: Throwable) {
                Log.w(TAG, "Gagal kirim broadcast Shopee ke $pkg: ${t.message}")
            }
        }
    }
}
