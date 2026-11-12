package com.tajasb.optimizer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.tajasb.model.VideoCodecRisk
import java.io.File

/**
 * VideoCodecGuard:
 * Melindungi perangkat dengan spesifikasi RAM menengah/terbatas (~2600MB) dari crash atau drop frame
 * akibat file video dengan bitrate ekstrem, resolusi 4K/2K, atau frame rate 60fps.
 *
 * Menginspeksi video secara pre-flight sebelum diputar oleh OpenGL pipeline dan memberikan
 * peringatan serta rekomendasi keselamatan memori.
 */
object VideoCodecGuard {

    private const val TAG = "VideoCodecGuard"

    fun inspectVideoFile(file: File?, availableRamMb: Long = 2600L): VideoCodecRisk {
        if (file == null || !file.exists() || file.length() == 0L) {
            return VideoCodecRisk(
                level = VideoCodecRisk.RiskLevel.SAFE,
                width = 0,
                height = 0,
                fps = 30,
                bitrateBps = 0L,
                isHighRamRisk = false,
                warningMessage = "Berkas video kosong atau belum dipilih."
            )
        }

        var width = 1280
        var height = 720
        var fps = 30
        var bitrate = 0L
        var mimeType = "video/mp4"

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)

            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val bStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val mStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val fStr = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                } else null
            } catch (_: Throwable) { null }

            width = wStr?.toIntOrNull() ?: 1280
            height = hStr?.toIntOrNull() ?: 720
            bitrate = bStr?.toLongOrNull() ?: 0L
            mimeType = mStr ?: "video/mp4"
            fps = fStr?.toFloatOrNull()?.toInt() ?: 30

            retriever.release()
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal menginspeksi metadata video ${file.name}: ${t.message}")
        }

        val maxDim = maxOf(width, height)
        val is4Kor2K = maxDim > 2000 // 2K/4K
        val isVeryHighFps = fps >= 55 // 60fps
        val isHeavyBitrate = bitrate > 16_000_000L // > 16Mbps

        val (level, message, isHighRisk) = when {
            is4Kor2K -> {
                Triple(
                    VideoCodecRisk.RiskLevel.DANGER,
                    "⚠️ Peringatan Resolusi Tinggi (${width}x${height}): Video melebihi Full HD (1080p). Pada perangkat RAM ~2600MB, decoder hardware berisiko kehabisan memory buffer (OMX/C2 error). Sangat disarankan convert ke 1080p 30fps.",
                    true
                )
            }
            isVeryHighFps || isHeavyBitrate -> {
                val bitrateMb = if (bitrate > 0) String.format(java.util.Locale.US, "%.1f", bitrate / 1_000_000.0) else "Tinggi"
                Triple(
                    VideoCodecRisk.RiskLevel.WARNING,
                    "⚡ Peringatan Beban Pemrosesan: Video ${fps}fps ($bitrateMb Mbps). Stream live kontinu berpotensi meningkatkan suhu HP. Mode Codec Guard aktif akan membatasi rendering ke 30fps stabil.",
                    false
                )
            }
            else -> {
                Triple(
                    VideoCodecRisk.RiskLevel.SAFE,
                    "✅ Profil Aman: ${width}x${height} (${fps}fps). Sangat optimal untuk live streaming stabil tanpa overheat atau drop frame.",
                    false
                )
            }
        }

        return VideoCodecRisk(
            level = level,
            width = width,
            height = height,
            fps = fps,
            bitrateBps = bitrate,
            isHighRamRisk = isHighRisk,
            warningMessage = message
        )
    }

    /**
     * Memeriksa apakah video memerlukan auto-downscale atau frame rate limiter demi kestabilan RAM 2600MB.
     */
    fun shouldEnforceSafeFps(risk: VideoCodecRisk, availableRamMb: Long): Boolean {
        if (risk.level == VideoCodecRisk.RiskLevel.DANGER) return true
        if (risk.level == VideoCodecRisk.RiskLevel.WARNING && availableRamMb < 700L) return true
        return false
    }
}
