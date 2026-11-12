package com.tajasb.hook

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * YuvFrameConverter:
 * Modular utility for extracting virtual video frames, applying rotation/mirror matrices,
 * and converting ARGB_8888 Bitmaps into NV21/YUV420SP byte arrays for Camera1 preview callbacks.
 */
object YuvFrameConverter {

    fun extractVideoFrameBitmap(videoFile: File?): Bitmap? {
        val video = videoFile ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.absolutePath)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    fun transformBitmap(source: Bitmap, rotation: Int, isMirrored: Boolean): Bitmap {
        if (rotation == 0 && !isMirrored) return source
        val matrix = Matrix()
        if (rotation != 0) {
            matrix.postRotate(rotation.toFloat())
        }
        if (isMirrored) {
            matrix.postScale(-1f, 1f)
        }
        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (_: Throwable) {
            source
        }
    }

    fun bitmapToNv21(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }
        scaled.getPixels(argb, 0, width, 0, 0, width, height)

        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = argb[j * width + i]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff

                val y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                    val v = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
