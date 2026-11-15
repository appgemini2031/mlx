package com.tajasb.interaction

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tajasb.debug.VcamDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

sealed class SkillCooldownScanResult {
    data object Ready : SkillCooldownScanResult()
    data class OnCooldown(val secondsRemaining: Double, val rawText: String) : SkillCooldownScanResult()
    data class RateLimited(val retryAfterMs: Long) : SkillCooldownScanResult()
    data class Unavailable(val reason: String) : SkillCooldownScanResult()
}

object SkillCooldownDetector {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun inspectSkillArea(
        centerNormX: Float,
        centerNormY: Float,
        widthRatio: Float,
        heightRatio: Float,
        offsetX: Float,
        offsetY: Float
    ): SkillCooldownScanResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return SkillCooldownScanResult.Unavailable("Android 11+ dibutuhkan untuk scan layar")
        }
        val svc = MlbbGestureAccessibilityService.currentInstance()
            ?: return SkillCooldownScanResult.Unavailable("Layanan Aksesibilitas belum aktif")

        val adjX = (centerNormX + offsetX).coerceIn(0.02f, 0.98f)
        val adjY = (centerNormY + offsetY).coerceIn(0.02f, 0.98f)
        val capture = svc.captureScreenRegionResult(
            centerNormX = adjX,
            centerNormY = adjY,
            widthRatio = widthRatio,
            heightRatio = heightRatio
        )
        val crop = when (capture) {
            is ScreenCaptureResult.Success -> capture.bitmap
            is ScreenCaptureResult.RateLimited -> return SkillCooldownScanResult.RateLimited(capture.waitMs)
            is ScreenCaptureResult.Error -> return SkillCooldownScanResult.Unavailable(capture.reason)
        }

        return try {
            // Pass 1: Isolasi angka terang (putih/kuning MLBB) jadi hitam pekat di atas latar putih dengan padding
            val (isolated, hasBrightHighlight) = withContext(Dispatchers.Default) {
                prepareBrightDigitsOnWhiteBitmapWithStats(crop)
            }
            try {
                if (hasBrightHighlight) {
                    val raw1 = recognizeText(isolated)
                    val sec1 = CooldownOcrParser.extractCooldownSeconds(raw1)
                    if (sec1 != null && sec1 > 0.05) {
                        return SkillCooldownScanResult.OnCooldown(sec1, raw1.trim())
                    }
                }
            } finally {
                if (!isolated.isRecycled) isolated.recycle()
            }

            // Pass 2: Upscaled high-contrast padded grayscale
            val contrast = withContext(Dispatchers.Default) { prepareHighContrastPaddedBitmap(crop) }
            try {
                val raw2 = recognizeText(contrast)
                val sec2 = CooldownOcrParser.extractCooldownSeconds(raw2)
                if (sec2 != null && sec2 > 0.05) {
                    return SkillCooldownScanResult.OnCooldown(sec2, raw2.trim())
                }
            } finally {
                if (!contrast.isRecycled) contrast.recycle()
            }

            // Pass 3: Raw upscaled padded crop hanya jika ada variasi terang/highlight pada gambar
            if (hasBrightHighlight) {
                val cleanPad = withContext(Dispatchers.Default) { prepareRawPaddedBitmap(crop) }
                try {
                    val raw3 = recognizeText(cleanPad)
                    val sec3 = CooldownOcrParser.extractCooldownSeconds(raw3)
                    if (sec3 != null && sec3 > 0.05) {
                        return SkillCooldownScanResult.OnCooldown(sec3, raw3.trim())
                    }
                } finally {
                    if (!cleanPad.isRecycled) cleanPad.recycle()
                }
            }

            SkillCooldownScanResult.Ready
        } catch (t: Throwable) {
            VcamDebugLogger.e("CooldownScan", "OCR scan failed: ${t.message}")
            SkillCooldownScanResult.Unavailable("Error OCR: ${t.message}")
        } finally {
            if (!crop.isRecycled) crop.recycle()
        }
    }

    /**
     * Angka cooldown di Mobile Legends berwarna putih / kuning cerah di atas ikon skill yang digelapkan.
     * Mengisolasi piksel terang menjadi hitam pekat di atas kanvas putih berpadding meningkatkan akurasi ML Kit drastis.
     * Sekaligus mengembalikan apakah ada cukup piksel terang/kuning di crop agar Pass OCR tidak sia-sia.
     */
    private fun prepareBrightDigitsOnWhiteBitmapWithStats(src: Bitmap): Pair<Bitmap, Boolean> {
        val scale = if (src.width < 120 || src.height < 120) 4 else 3
        val scaledW = (src.width * scale).coerceAtLeast(160)
        val scaledH = (src.height * scale).coerceAtLeast(160)
        val pad = 28
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val out = Bitmap.createBitmap(scaledW + pad * 2, scaledH + pad * 2, Bitmap.Config.ARGB_8888)
        out.eraseColor(android.graphics.Color.WHITE)

        val pixels = IntArray(scaledW * scaledH)
        scaled.getPixels(pixels, 0, scaledW, 0, 0, scaledW, scaledH)
        var brightCount = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luma = (r * 30 + g * 59 + b * 11) / 100
            val isBrightWhite = luma >= 170
            val isBrightGold = r >= 180 && g >= 145 && b <= 150
            if (isBrightWhite || isBrightGold) {
                brightCount++
                pixels[i] = android.graphics.Color.BLACK
            } else {
                pixels[i] = android.graphics.Color.WHITE
            }
        }
        out.setPixels(pixels, 0, scaledW, pad, pad, scaledW, scaledH)
        if (scaled !== src && !scaled.isRecycled) scaled.recycle()
        return out to (brightCount >= 8)
    }

    private fun prepareHighContrastPaddedBitmap(src: Bitmap): Bitmap {
        val scale = if (src.width < 120 || src.height < 120) 3 else 2
        val targetW = (src.width * scale).coerceAtLeast(140)
        val targetH = (src.height * scale).coerceAtLeast(140)
        val pad = 24
        val out = Bitmap.createBitmap(targetW + pad * 2, targetH + pad * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.BLACK)
        val cm = ColorMatrix(
            floatArrayOf(
                1.5f, 0f, 0f, 0f, -30f,
                0f, 1.5f, 0f, 0f, -30f,
                0f, 0f, 1.5f, 0f, -30f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(
            src,
            android.graphics.Rect(0, 0, src.width, src.height),
            android.graphics.Rect(pad, pad, pad + targetW, pad + targetH),
            paint
        )
        return out
    }

    private fun prepareRawPaddedBitmap(src: Bitmap): Bitmap {
        val scale = if (src.width < 120 || src.height < 120) 3 else 2
        val targetW = (src.width * scale).coerceAtLeast(140)
        val targetH = (src.height * scale).coerceAtLeast(140)
        val pad = 24
        val out = Bitmap.createBitmap(targetW + pad * 2, targetH + pad * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.DKGRAY)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            src,
            android.graphics.Rect(0, 0, src.width, src.height),
            android.graphics.Rect(pad, pad, pad + targetW, pad + targetH),
            paint
        )
        return out
    }

    private suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result.text ?: "")
            }
            .addOnFailureListener { _ ->
                if (cont.isActive) cont.resume("")
            }
    }
}
