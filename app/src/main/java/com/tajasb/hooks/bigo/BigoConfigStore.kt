package com.tajasb.hooks.bigo

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Konfigurasi VCam terisolasi khusus BIGO Live (sg.bigo.live & sg.bigo.lite).
 * Disimpan terpisah di SharedPreferences "vcam_config_bigo" milik proses Tajasb
 * dan diekspos ke proses BIGO hanya melalui VcamContentProvider (/bigo/...).
 */
data class BigoVcamConfig(
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
        put("version", version)
    }

    companion object {
        fun fromJson(json: JSONObject): BigoVcamConfig {
            return BigoVcamConfig(
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
                version = json.optLong("version", 1L)
            )
        }
    }
}

/**
 * Pengelola state & penyimpanan konfigurasi BIGO di sisi aplikasi controller (com.tajasb).
 * DILARANG dipanggil langsung dari dalam proses sg.bigo.live karena perbedaan sandbox UID;
 * proses BIGO wajib membaca melalui VcamContentProvider (content://com.tajasb.provider/bigo/config).
 */
object BigoConfigStore {
    const val TAG = "Tajasb-BIGO"
    const val PREFS_NAME = "vcam_config_bigo"
    const val ACTION_BIGO_CMD = "com.tajasb.ACTION_BIGO_CMD"
    const val BIGO_MEDIA_DIR = "bigo_vcam"

    val BIGO_PACKAGES = listOf(
        "sg.bigo.live",
        "sg.bigo.lite"
    )

    private val discoveredBigoPackages = java.util.concurrent.CopyOnWriteArraySet<String>()

    fun registerDiscoveredPackage(pkg: String?) {
        if (!pkg.isNullOrBlank()) {
            discoveredBigoPackages.add(pkg)
        }
    }

    private const val KEY_ENABLED = "bigo_enabled"
    private const val KEY_VIDEO_PATH = "bigo_video_path"
    private const val KEY_AUDIO_PATH = "bigo_audio_path"
    private const val KEY_ROTATION = "bigo_rotation"
    private const val KEY_MIRRORED = "bigo_is_mirrored"
    private const val KEY_FLIP_V = "bigo_is_vertical_flipped"
    private const val KEY_ZOOM = "bigo_zoom_factor"
    private const val KEY_SCALE_MODE = "bigo_scale_mode"
    private const val KEY_PAUSED = "bigo_is_paused"
    private const val KEY_MUTED = "bigo_is_muted"
    private const val KEY_VIDEO_AUDIO_MUTED = "bigo_is_video_audio_muted"
    private const val KEY_MP3_PAUSED = "bigo_is_mp3_paused"
    private const val KEY_FPS = "bigo_fps"
    private const val KEY_VERSION = "bigo_version"

    fun getConfig(context: Context): BigoVcamConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return BigoVcamConfig(
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
            version = prefs.getLong(KEY_VERSION, 1L)
        )
    }

    fun saveConfig(context: Context, config: BigoVcamConfig, broadcastUpdate: Boolean = true): BigoVcamConfig {
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
            .putLong(KEY_VERSION, updated.version)
            .apply()

        writeConfigJsonFile(context, updated)
        if (broadcastUpdate) {
            sendCommandBroadcast(context, "UPDATE_CONFIG", updated)
        }
        return updated
    }

    fun getEffectiveConfig(context: Context): BigoVcamConfig {
        val local = getConfig(context)
        val hasDedicatedVideo = local.videoPath.isNotBlank() ||
            File(getMediaDirectory(context), "virtual.mp4").let { it.exists() && it.length() > 0L }
        if (hasDedicatedVideo) {
            return local
        }

        // Jika belum ada override khusus di vcam_config_bigo, cerminkan konfigurasi aktif
        // dari VcamRepository (read-only) agar video & kontrol Tajasb langsung bekerja di BIGO.
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
        val dir = File(context.filesDir, BIGO_MEDIA_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun resolveBigoVideoFile(context: Context): File? {
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

    fun resolveBigoAudioFile(context: Context): File? {
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

        // Fallback read-only ke audio aktif di VcamRepository / Camera1 internal vault
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

    private fun writeConfigJsonFile(context: Context, config: BigoVcamConfig): File {
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
            Log.w(TAG, "Gagal menulis config.json BIGO: ${t.message}")
        }
        return outFile
    }

    fun sendCommandBroadcast(context: Context, command: String, config: BigoVcamConfig = getConfig(context)) {
        val targets = (BIGO_PACKAGES + discoveredBigoPackages).distinct()
        for (pkg in targets) {
            try {
                val intent = Intent(ACTION_BIGO_CMD).apply {
                    setPackage(pkg)
                    putExtra("extra_bigo_cmd", command)
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
                    putExtra("extra_version", config.version)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(intent)
            } catch (t: Throwable) {
                Log.w(TAG, "Gagal kirim broadcast BIGO ke $pkg: ${t.message}")
            }
        }
    }
}
