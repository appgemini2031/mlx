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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class SkillCooldownScanResult {
    data object Ready : SkillCooldownScanResult()
    data class OnCooldown(val secondsRemaining: Double, val rawText: String) : SkillCooldownScanResult()
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
        val crop = svc.captureScreenRegionBitmap(
            centerNormX = adjX,
            centerNormY = adjY,
            widthRatio = widthRatio,
            heightRatio = heightRatio
        ) ?: return SkillCooldownScanResult.Unavailable("Gagal menangkap area layar skill")

        return try {
            val upscaled = prepareHighContrastBitmap(crop)
            try {
                val rawText = recognizeText(upscaled)
                val sec = CooldownOcrParser.extractCooldownSeconds(rawText)
                if (sec != null && sec > 0.05) {
                    SkillCooldownScanResult.OnCooldown(sec, rawText.trim())
                } else {
                    SkillCooldownScanResult.Ready
                }
            } finally {
                if (upscaled !== crop && !upscaled.isRecycled) {
                    upscaled.recycle()
                }
            }
        } catch (t: Throwable) {
            VcamDebugLogger.e("CooldownScan", "OCR scan failed: ${t.message}")
            SkillCooldownScanResult.Unavailable("Error OCR: ${t.message}")
        } finally {
            if (!crop.isRecycled) crop.recycle()
        }
    }

    private fun prepareHighContrastBitmap(src: Bitmap): Bitmap {
        val scale = if (src.width < 160 || src.height < 160) 3 else 2
        val targetW = (src.width * scale).coerceAtLeast(120)
        val targetH = (src.height * scale).coerceAtLeast(120)
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = ColorMatrix(
            floatArrayOf(
                1.6f, 0f, 0f, 0f, -45f,
                0f, 1.6f, 0f, 0f, -45f,
                0f, 0f, 1.6f, 0f, -45f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(
            src,
            android.graphics.Rect(0, 0, src.width, src.height),
            android.graphics.Rect(0, 0, targetW, targetH),
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
