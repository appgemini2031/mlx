package com.tajasb.interaction

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tajasb.debug.VcamDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

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
            // Pass 1: Isolasi adaptif angka terang/kuning MLBB jadi hitam pekat di atas latar putih + penebalan tipis
            val (isolated, hasBrightHighlight) = withContext(Dispatchers.Default) {
                prepareAdaptiveDigitsOnWhiteBitmapWithStats(crop)
            }
            try {
                if (hasBrightHighlight) {
                    val res1 = recognizeTextResult(isolated)
                    val sec1 = extractSpatialCooldown(res1, isolated.width, isolated.height)
                    if (sec1 != null && sec1 > 0.05) {
                        return SkillCooldownScanResult.OnCooldown(sec1, (res1?.text ?: "").trim())
                    }
                }
            } finally {
                if (!isolated.isRecycled) isolated.recycle()
            }

            // Pass 2: Fokus tengah 74% (potong tepi angka mana/level) + inversi grayscale kontras tinggi
            val centerInverted = withContext(Dispatchers.Default) {
                prepareCenterInvertedGrayBitmap(crop)
            }
            try {
                val res2 = recognizeTextResult(centerInverted)
                val sec2 = extractSpatialCooldown(res2, centerInverted.width, centerInverted.height)
                if (sec2 != null && sec2 > 0.05) {
                    return SkillCooldownScanResult.OnCooldown(sec2, (res2?.text ?: "").trim())
                }
            } finally {
                if (!centerInverted.isRecycled) centerInverted.recycle()
            }

            // Pass 3: Upscaled high-contrast padded grayscale penuh
            val contrast = withContext(Dispatchers.Default) { prepareHighContrastPaddedBitmap(crop) }
            try {
                val res3 = recognizeTextResult(contrast)
                val sec3 = extractSpatialCooldown(res3, contrast.width, contrast.height)
                if (sec3 != null && sec3 > 0.05) {
                    return SkillCooldownScanResult.OnCooldown(sec3, (res3?.text ?: "").trim())
                }
            } finally {
                if (!contrast.isRecycled) contrast.recycle()
            }

            // Pass 4: Raw upscaled padded crop bila masih ada variasi terang/highlight pada tombol
            if (hasBrightHighlight) {
                val cleanPad = withContext(Dispatchers.Default) { prepareRawPaddedBitmap(crop) }
                try {
                    val res4 = recognizeTextResult(cleanPad)
                    val sec4 = extractSpatialCooldown(res4, cleanPad.width, cleanPad.height)
                    if (sec4 != null && sec4 > 0.05) {
                        return SkillCooldownScanResult.OnCooldown(sec4, (res4?.text ?: "").trim())
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

    private fun extractSpatialCooldown(result: Text?, bitmapW: Int, bitmapH: Int): Double? {
        if (result == null) return null
        val cx = bitmapW / 2.0
        val cy = bitmapH / 2.0
        val maxDist = hypot(cx, cy).coerceAtLeast(1.0)

        val candidates = mutableListOf<OcrTextCandidate>()
        for (block in result.textBlocks) {
            if (block.lines.isEmpty()) {
                val box = block.boundingBox
                val dist = if (box != null) {
                    hypot(box.exactCenterX() - cx, box.exactCenterY() - cy) / maxDist
                } else 0.5
                candidates += OcrTextCandidate(block.text, dist)
            } else {
                for (line in block.lines) {
                    val box = line.boundingBox ?: block.boundingBox
                    val dist = if (box != null) {
                        hypot(box.exactCenterX() - cx, box.exactCenterY() - cy) / maxDist
                    } else 0.5
                    candidates += OcrTextCandidate(line.text, dist)
                }
            }
        }
        return CooldownOcrParser.extractBestCooldownSeconds(candidates, result.text)
    }

    /**
     * Isolasi piksel angka putih/kuning di atas ikon skill yang digelapkan dengan ambang luma adaptif
     * (menangani layar redup & tombol skill berwarna cerah) serta penebalan stroke tipis agar angka "1"/"4"/"0.8" jelas.
     */
    private fun prepareAdaptiveDigitsOnWhiteBitmapWithStats(src: Bitmap): Pair<Bitmap, Boolean> {
        val scale = if (src.width < 120 || src.height < 120) 4 else 3
        val scaledW = (src.width * scale).coerceAtLeast(160)
        val scaledH = (src.height * scale).coerceAtLeast(160)
        val pad = 34
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val out = Bitmap.createBitmap(scaledW + pad * 2, scaledH + pad * 2, Bitmap.Config.ARGB_8888)
        out.eraseColor(android.graphics.Color.WHITE)

        val pixels = IntArray(scaledW * scaledH)
        scaled.getPixels(pixels, 0, scaledW, 0, 0, scaledW, scaledH)

        // Hitung statistik luma pada zona tengah 70% untuk menentukan threshold adaptif
        val xStart = (scaledW * 0.15f).toInt()
        val xEnd = (scaledW * 0.85f).toInt().coerceAtLeast(xStart + 1)
        val yStart = (scaledH * 0.15f).toInt()
        val yEnd = (scaledH * 0.85f).toInt().coerceAtLeast(yStart + 1)
        var lumaSum = 0L
        var centerSamples = 0
        var maxCenterLuma = 0
        for (y in yStart until yEnd step 2) {
            val rowOffset = y * scaledW
            for (x in xStart until xEnd step 2) {
                val c = pixels[rowOffset + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val luma = (r * 30 + g * 59 + b * 11) / 100
                lumaSum += luma
                centerSamples++
                if (luma > maxCenterLuma) maxCenterLuma = luma
            }
        }
        val meanLuma = if (centerSamples > 0) (lumaSum / centerSamples).toInt() else 90
        val adaptiveWhiteThreshold = max(124, min(172, ((meanLuma + maxCenterLuma) / 2) - 4))

        val isForeground = BooleanArray(pixels.size)
        var brightCount = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luma = (r * 30 + g * 59 + b * 11) / 100
            val maxChannel = max(r, max(g, b))
            val minChannel = min(r, min(g, b))
            val chroma = maxChannel - minChannel
            // Angka putih/abu terang netral (bukan aura skill ber-saturasi tinggi)
            val isNeutralBright = luma >= adaptiveWhiteThreshold && (chroma <= 55 || luma >= max(168, maxCenterLuma - 32))
            // Angka kuning/emas countdown skill
            val isBrightGold = r >= 145 && g >= 120 && b <= 155 && (r - b) >= 26
            if (isNeutralBright || isBrightGold) {
                brightCount++
                isForeground[i] = true
            }
        }

        // Penebalan 4-tetangga ringan bila crop awal kecil agar angka 1-digit tipis tidak hilang
        val dilated = BooleanArray(pixels.size)
        for (y in 0 until scaledH) {
            val row = y * scaledW
            for (x in 0 until scaledW) {
                val idx = row + x
                if (isForeground[idx]) {
                    dilated[idx] = true
                    if (x > 0) dilated[idx - 1] = true
                    if (x + 1 < scaledW) dilated[idx + 1] = true
                    if (y > 0) dilated[idx - scaledW] = true
                    if (y + 1 < scaledH) dilated[idx + scaledW] = true
                }
            }
        }

        for (i in pixels.indices) {
            pixels[i] = if (dilated[i]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        out.setPixels(pixels, 0, scaledW, pad, pad, scaledW, scaledH)
        if (scaled !== src && !scaled.isRecycled) scaled.recycle()
        return out to (brightCount >= 4)
    }

    /**
     * Potong 74% area tengah tombol skill untuk membuang gangguan teks biaya Mana / pinggiran cincin skill,
     * lalu inversi warna agar angka terang berubah menjadi gelap di atas kanvas terang.
     */
    private fun prepareCenterInvertedGrayBitmap(src: Bitmap): Bitmap {
        val cw = (src.width * 0.74f).toInt().coerceIn(24, src.width)
        val ch = (src.height * 0.74f).toInt().coerceIn(24, src.height)
        val cLeft = ((src.width - cw) / 2).coerceAtLeast(0)
        val cTop = ((src.height - ch) / 2).coerceAtLeast(0)
        val scale = if (cw < 100 || ch < 100) 4 else 3
        val targetW = (cw * scale).coerceAtLeast(160)
        val targetH = (ch * scale).coerceAtLeast(160)
        val pad = 32
        val out = Bitmap.createBitmap(targetW + pad * 2, targetH + pad * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)

        // Grayscale + inversi + boost kontras: luma tinggi menjadi hitam pekat di latar putih
        val gray = ColorMatrix().apply { setSaturation(0f) }
        val invertContrast = ColorMatrix(
            floatArrayOf(
                -1.85f, 0f, 0f, 0f, 340f,
                0f, -1.85f, 0f, 0f, 340f,
                0f, 0f, -1.85f, 0f, 340f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        gray.postConcat(invertContrast)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(gray)
        }
        canvas.drawBitmap(
            src,
            android.graphics.Rect(cLeft, cTop, cLeft + cw, cTop + ch),
            android.graphics.Rect(pad, pad, pad + targetW, pad + targetH),
            paint
        )
        return out
    }

    private fun prepareHighContrastPaddedBitmap(src: Bitmap): Bitmap {
        val scale = if (src.width < 120 || src.height < 120) 3 else 2
        val targetW = (src.width * scale).coerceAtLeast(150)
        val targetH = (src.height * scale).coerceAtLeast(150)
        val pad = 28
        val out = Bitmap.createBitmap(targetW + pad * 2, targetH + pad * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.BLACK)
        val cm = ColorMatrix(
            floatArrayOf(
                1.85f, 0f, 0f, 0f, -45f,
                0f, 1.85f, 0f, 0f, -45f,
                0f, 0f, 1.85f, 0f, -45f,
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
        val targetW = (src.width * scale).coerceAtLeast(150)
        val targetH = (src.height * scale).coerceAtLeast(150)
        val pad = 28
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

    private suspend fun recognizeTextResult(bitmap: Bitmap): Text? {
        return withTimeoutOrNull(1800L) {
            suspendCancellableCoroutine { cont ->
                try {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            if (cont.isActive) cont.resume(result)
                        }
                        .addOnFailureListener { _ ->
                            if (cont.isActive) cont.resume(null)
                        }
                        .addOnCanceledListener {
                            if (cont.isActive) cont.resume(null)
                        }
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }
}
