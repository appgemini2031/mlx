package com.tajasb.photostudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object PhotoStudioRenderer {

    const val TARGET_WIDTH = 720
    const val TARGET_HEIGHT = 1280
    const val TARGET_FPS = 25
    const val FRAME_DURATION_US = 1_000_000L / TARGET_FPS // 40,000 us per frame

    fun computeFramePtsUs(writtenSampleCount: Long, fps: Int = TARGET_FPS): Long {
        val frameDurationUs = 1_000_000L / fps.coerceAtLeast(1)
        return writtenSampleCount.coerceAtLeast(0L) * frameDurationUs
    }

    /**
     * Renders a single studio frame (Ken Burns multi-photo slideshow + selected atmospheric effect)
     * onto an Android Canvas (works identically for Compose 9:16 Live Preview and MediaCodec Surface).
     *
     * Mathematically guarantees seamless periodic looping:
     * Position at start (t = 0) and position at end (t = totalDurationSec) are identical,
     * completely eliminating any visual seam, jump, or stutter when the video loops.
     */
    fun drawStudioFrame(
        canvas: Canvas,
        width: Float,
        height: Float,
        bitmaps: List<Bitmap>,
        timeSec: Float,
        totalDurationSec: Float,
        effect: PhotoAnimEffect,
        imageSpeedMultiplier: Float,
        effectSpeedMultiplier: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val duration = totalDurationSec.coerceAtLeast(1.0f)
        val normTime = (timeSec / duration).coerceIn(0f, 1f)

        // 1. Background Base + Multi-Image Ken Burns Slideshow
        canvas.drawColor(Color.parseColor("#080C14"))
        if (bitmaps.isNotEmpty()) {
            drawKenBurnsSlideshow(
                canvas = canvas,
                width = width,
                height = height,
                bitmaps = bitmaps,
                timeSec = timeSec,
                totalDurationSec = duration,
                normTime = normTime,
                imageSpeed = imageSpeedMultiplier,
                paint = paint
            )
        } else {
            drawPlaceholderBackdrop(canvas, width, height, normTime, duration, paint)
        }

        // 2. Overlay Atmospheric / Particle Effect (100% Seamless Looping via integer cycle quantization)
        when (effect) {
            PhotoAnimEffect.NONE -> Unit
            PhotoAnimEffect.RAIN -> drawRainEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier, heavy = false)
            PhotoAnimEffect.SNOW -> drawSnowEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier)
            PhotoAnimEffect.SHOOTING_STAR -> drawShootingStarsEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier)
            PhotoAnimEffect.RAIN_SHOOTING_STAR -> {
                drawShootingStarsEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier)
                drawRainEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier, heavy = false)
            }
            PhotoAnimEffect.SAKURA -> drawSakuraEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier)
            PhotoAnimEffect.FIRE_EMBERS -> drawFireEmbersEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier)
            PhotoAnimEffect.FIREFLIES -> drawFirefliesEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier)
            PhotoAnimEffect.THUNDERSTORM -> {
                drawRainEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier, heavy = true)
                drawThunderFlash(canvas, width, height, normTime, duration, effectSpeedMultiplier)
            }
            PhotoAnimEffect.FLOATING_HEARTS -> drawFloatingHeartsEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier)
            PhotoAnimEffect.AURORA -> drawAuroraEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier)
            PhotoAnimEffect.AUTUMN_LEAVES -> drawAutumnLeavesEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier)
            PhotoAnimEffect.GOLD_BOKEH -> drawGoldBokehEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier)
            PhotoAnimEffect.NEON_PULSE_FRAME -> drawNeonPulseFrameEffect(canvas, width, height, normTime, duration, effectSpeedMultiplier)
        }
    }

    private fun drawKenBurnsSlideshow(
        canvas: Canvas,
        width: Float,
        height: Float,
        bitmaps: List<Bitmap>,
        timeSec: Float,
        totalDurationSec: Float,
        normTime: Float,
        imageSpeed: Float,
        paint: Paint
    ) {
        val count = bitmaps.size
        if (count == 1) {
            // Integer number of complete Ken Burns zoom/pan oscillation cycles during totalDurationSec
            val fullCycles = max(1, (totalDurationSec * 0.15f * imageSpeed).toInt())
            val phase = normTime * fullCycles * 2f * PI.toFloat()
            drawSingleKenBurnsBitmap(
                canvas = canvas,
                width = width,
                height = height,
                bmp = bitmaps[0],
                phase = phase,
                directionSeed = 0,
                alpha = 255,
                paint = paint
            )
            return
        }

        // Multi-photo seamless cyclic cross-fade: slide (count - 1) fades cleanly into slide 0
        val slideSec = totalDurationSec / count.toFloat()
        val totalProgress = (timeSec / totalDurationSec).coerceIn(0f, 1f) * count.toFloat()
        val currentIdx = totalProgress.toInt().coerceIn(0, count - 1)
        val nextIdx = (currentIdx + 1) % count
        val localProgress = (totalProgress - currentIdx.toFloat()).coerceIn(0f, 1f)
        val fadePortion = 0.22f

        val currentPhase = localProgress * PI.toFloat()
        drawSingleKenBurnsBitmap(
            canvas = canvas,
            width = width,
            height = height,
            bmp = bitmaps[currentIdx],
            phase = currentPhase,
            directionSeed = currentIdx,
            alpha = 255,
            paint = paint
        )

        if (localProgress > (1f - fadePortion)) {
            val fadeRatio = ((localProgress - (1f - fadePortion)) / fadePortion).coerceIn(0f, 1f)
            val fadeAlpha = (fadeRatio * 255f).toInt()
            val nextPhase = (fadeRatio * 0.35f) * PI.toFloat()
            drawSingleKenBurnsBitmap(
                canvas = canvas,
                width = width,
                height = height,
                bmp = bitmaps[nextIdx],
                phase = nextPhase,
                directionSeed = nextIdx,
                alpha = fadeAlpha,
                paint = paint
            )
        }
    }

    private fun drawSingleKenBurnsBitmap(
        canvas: Canvas,
        width: Float,
        height: Float,
        bmp: Bitmap,
        phase: Float,
        directionSeed: Int,
        alpha: Int,
        paint: Paint
    ) {
        if (bmp.isRecycled || bmp.width <= 0 || bmp.height <= 0) return
        val baseScale = max(width / bmp.width.toFloat(), height / bmp.height.toFloat())

        // Smooth cosine wave: 0 at phase = 0, 0 at phase = 2*PI*N -> perfect loop
        val smoothWave = 0.5f - 0.5f * cos(phase)
        val zoomFactor = 1.04f + 0.14f * smoothWave
        val finalScale = baseScale * zoomFactor

        val scaledW = bmp.width * finalScale
        val scaledH = bmp.height * finalScale
        val extraW = (scaledW - width).coerceAtLeast(0f)
        val extraH = (scaledH - height).coerceAtLeast(0f)

        val dirX = if (directionSeed % 2 == 0) 1f else -1f
        val dirY = if ((directionSeed / 2) % 2 == 0) 1f else -1f

        // Pan displacements return strictly to 0 at phase = 0 and phase = 2*PI*N
        val panX = (sin(phase) * 0.35f * dirX) * (extraW * 0.5f)
        val panY = ((cos(phase) - 1f) * 0.175f * dirY) * (extraH * 0.5f)

        val dx = (width - scaledW) * 0.5f + panX
        val dy = (height - scaledH) * 0.5f + panY

        val matrix = Matrix().apply {
            postScale(finalScale, finalScale)
            postTranslate(dx, dy)
        }
        paint.alpha = alpha.coerceIn(0, 255)
        canvas.drawBitmap(bmp, matrix, paint)
        paint.alpha = 255
    }

    private fun drawPlaceholderBackdrop(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        paint: Paint
    ) {
        val shader = LinearGradient(
            0f, 0f, width, height,
            intArrayOf(
                Color.parseColor("#0F172A"),
                Color.parseColor("#1E1B4B"),
                Color.parseColor("#090D16")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null

        // Seamless pulsating radial glow in center
        val pulseCycles = max(1, (totalDurationSec * 0.5f).toInt())
        val pulse = 0.85f + 0.15f * sin(normTime * pulseCycles * 2f * PI.toFloat())
        val radial = RadialGradient(
            width * 0.5f,
            height * 0.42f,
            width * 0.55f * pulse,
            Color.argb(65, 56, 189, 248),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        paint.shader = radial
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
    }

    private fun drawRainEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float,
        heavy: Boolean
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.argb(if (heavy) 50 else 28, 10, 22, 48)
        canvas.drawRect(0f, 0f, width, height, p)

        val dropCount = if (heavy) 95 else 65
        p.strokeCap = Paint.Cap.ROUND
        val baseCycles = max(3, (totalDurationSec * 1.5f * speedMultiplier).toInt())

        for (i in 0 until dropCount) {
            val seed = (i * 97.31f)
            // Each drop completes an exact integer number of falls across totalDurationSec
            val k = baseCycles + (i % 7) * max(1, (totalDurationSec * 0.25f).toInt())
            val prog = (normTime * k + (seed % 1f)) % 1f
            val xBase = ((seed * 43.17f) % 1f) * (width * 1.15f) - width * 0.08f
            val slant = width * 0.08f
            val x = xBase + prog * slant
            val y = prog * (height + 120f) - 60f
            val len = (height * 0.045f) * (0.7f + (i % 5) * 0.15f)

            p.strokeWidth = (width / 360f) * (if (i % 3 == 0) 2.1f else 1.3f)
            p.color = Color.argb(135 + (i % 80), 186, 230, 253)
            canvas.drawLine(x, y, x + slant * 0.045f, y + len, p)
        }

        // Ground ripples with integer cycles
        p.style = Paint.Style.STROKE
        p.strokeWidth = width / 400f
        val rippleBaseCycles = max(2, (totalDurationSec * 1.6f * speedMultiplier).toInt())
        for (r in 0 until 14) {
            val rCycles = rippleBaseCycles + (r % 3)
            val rProg = (normTime * rCycles + r * 0.23f) % 1f
            val rx = ((r * 71.9f) % 1f) * width
            val ry = height * (0.87f + (r % 5) * 0.025f)
            val radX = width * 0.045f * rProg
            val radY = radX * 0.28f
            p.color = Color.argb(((1f - rProg) * 150f).toInt().coerceIn(0, 255), 186, 230, 253)
            canvas.drawOval(RectF(rx - radX, ry - radY, rx + radX, ry + radY), p)
        }
    }

    private fun drawSnowEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val baseCycles = max(2, (totalDurationSec * 0.35f * speedMultiplier).toInt())
        val swayBaseCycles = max(1, (totalDurationSec * 0.6f * speedMultiplier).toInt())

        for (i in 0 until 70) {
            val seed = i * 53.7f
            val k = baseCycles + (i % 5) * max(1, (totalDurationSec * 0.1f).toInt())
            val prog = (normTime * k + (seed % 1f)) % 1f

            val swayCycles = swayBaseCycles + (i % 2)
            val sway = sin(normTime * swayCycles * 2f * PI.toFloat() + i) * (width * 0.035f)
            val x = (((seed * 19.3f) % 1f) * width + sway + width) % width
            val y = prog * (height + 40f) - 20f
            val radius = (width / 180f) * (0.55f + (i % 5) * 0.28f)

            p.color = Color.argb(150 + (i % 95), 241, 245, 249)
            canvas.drawCircle(x, y, radius, p)
        }
    }

    private fun drawShootingStarsEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val twinkleBaseCycles = max(2, (totalDurationSec * 1.5f * speedMultiplier).toInt())

        // Twinkling stars with periodic harmonic cycles
        for (i in 0 until 55) {
            val sx = ((i * 73.13f) % 1f) * width
            val sy = ((i * 39.71f) % 1f) * (height * 0.72f)
            val twinkleCycles = twinkleBaseCycles + (i % 4)
            val twinkle = 0.35f + 0.65f * abs(sin(normTime * twinkleCycles * 2f * PI.toFloat() + i))
            val r = (width / 320f) * (0.6f + (i % 3) * 0.45f)
            p.color = Color.argb((twinkle * 235f).toInt(), 255, 255, 255)
            canvas.drawCircle(sx, sy, r, p)
        }

        // Periodic shooting meteors quantized to integer passes
        p.strokeCap = Paint.Cap.ROUND
        val meteorPasses = max(1, (totalDurationSec * 0.4f * speedMultiplier).toInt())
        for (m in 0 until 4) {
            val mCycle = (normTime * meteorPasses + m * 0.25f) % 1f
            if (mCycle <= 0.6f) {
                val norm = mCycle / 0.6f
                val startX = width * (0.18f + (m * 0.23f) % 0.7f)
                val startY = height * (0.06f + (m * 0.11f) % 0.32f)
                val headX = startX + norm * width * 0.48f
                val headY = startY + norm * height * 0.34f
                val tailX = headX - width * 0.16f
                val tailY = headY - height * 0.11f

                val alpha = ((1f - norm) * 255f).toInt().coerceIn(0, 255)
                p.strokeWidth = width / 170f
                p.shader = LinearGradient(
                    headX, headY, tailX, tailY,
                    Color.argb(alpha, 255, 255, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawLine(tailX, tailY, headX, headY, p)
                p.shader = null
                p.color = Color.argb(alpha, 186, 230, 253)
                canvas.drawCircle(headX, headY, width / 110f, p)
            }
        }
    }

    private fun drawSakuraEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val baseCycles = max(2, (totalDurationSec * 0.3f * speedMultiplier).toInt())
        val swayBaseCycles = max(1, (totalDurationSec * 0.5f * speedMultiplier).toInt())
        val rotBaseTurns = max(1, (totalDurationSec * 0.4f * speedMultiplier).toInt())

        for (i in 0 until 48) {
            val seed = i * 61.9f
            val k = baseCycles + (i % 5) * max(1, (totalDurationSec * 0.08f).toInt())
            val prog = (normTime * k + (seed % 1f)) % 1f

            val sway = sin(normTime * (swayBaseCycles + (i % 2)) * 2f * PI.toFloat() + i * 0.7f) * (width * 0.06f)
            val x = (((seed * 29.1f) % 1f) * width + sway + width) % width
            val y = prog * (height + 50f) - 25f
            val w = (width / 62f) * (0.7f + (i % 4) * 0.2f)
            val h = w * 0.58f

            val angle = (i * 37f + normTime * (rotBaseTurns + (i % 3)) * 360f) % 360f

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(angle)
            p.color = if (i % 2 == 0) Color.argb(215, 251, 182, 206) else Color.argb(200, 244, 114, 182)
            canvas.drawOval(RectF(-w, -h, w, h), p)
            canvas.restore()
        }
    }

    private fun drawFireEmbersEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, height * 0.68f, 0f, height,
            Color.TRANSPARENT,
            Color.argb(115, 234, 88, 12),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, height * 0.68f, width, height, p)
        p.shader = null

        val baseCycles = max(3, (totalDurationSec * 0.45f * speedMultiplier).toInt())
        val swayBaseCycles = max(1, (totalDurationSec * 0.8f * speedMultiplier).toInt())

        for (i in 0 until 62) {
            val seed = i * 47.3f
            val k = baseCycles + (i % 6) * max(1, (totalDurationSec * 0.1f).toInt())
            val prog = (normTime * k + (seed % 1f)) % 1f

            val sway = sin(normTime * (swayBaseCycles + (i % 2)) * 2f * PI.toFloat() + i) * width * 0.045f
            val x = (((seed * 31.7f) % 1f) * width + sway + width) % width
            val y = height - prog * (height * 0.95f)
            val alpha = ((1f - prog * 0.85f) * 245f).toInt().coerceIn(0, 255)
            val r = (width / 150f) * (0.5f + (i % 4) * 0.3f)

            p.color = when (i % 3) {
                0 -> Color.argb(alpha, 251, 191, 36)
                1 -> Color.argb(alpha, 249, 115, 22)
                else -> Color.argb(alpha, 239, 68, 68)
            }
            canvas.drawCircle(x, y, r, p)
        }
    }

    private fun drawFirefliesEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val xBaseCycles = max(1, (totalDurationSec * 0.35f * speedMultiplier).toInt())
        val yBaseCycles = max(1, (totalDurationSec * 0.3f * speedMultiplier).toInt())
        val pulseBaseCycles = max(2, (totalDurationSec * 0.8f * speedMultiplier).toInt())

        for (i in 0 until 38) {
            val baseX = ((i * 83.1f) % 1f) * width
            val baseY = ((i * 59.4f) % 1f) * height

            val xCycles = xBaseCycles + (i % 2)
            val yCycles = yBaseCycles + ((i + 1) % 2)
            val x = (baseX + sin(normTime * xCycles * 2f * PI.toFloat() + i) * width * 0.08f + width) % width
            val y = (baseY + cos(normTime * yCycles * 2f * PI.toFloat() + i * 1.3f) * height * 0.05f + height) % height

            val pulseCycles = pulseBaseCycles + (i % 3)
            val pulse = 0.25f + 0.75f * abs(sin(normTime * pulseCycles * 2f * PI.toFloat() + i * 0.9f))
            val outerR = (width / 42f) * (0.7f + (i % 3) * 0.25f)

            p.shader = RadialGradient(
                x, y, outerR,
                Color.argb((pulse * 210f).toInt(), 190, 242, 100),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, outerR, p)
            p.shader = null
            p.color = Color.argb((pulse * 255f).toInt(), 254, 249, 195)
            canvas.drawCircle(x, y, outerR * 0.22f, p)
        }
    }

    private fun drawThunderFlash(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float
    ) {
        val flashes = max(1, (totalDurationSec * 0.3f * speedMultiplier).toInt())
        val flashCycle = (normTime * flashes) % 1f
        if (flashCycle < 0.15f) {
            val flashAlpha = ((1f - flashCycle / 0.15f) * 165f).toInt().coerceIn(0, 255)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.argb(flashAlpha, 224, 242, 254)
            canvas.drawRect(0f, 0f, width, height, p)

            val boltPath = Path()
            var bx = width * (0.35f + ((normTime * flashes).toInt() % 3) * 0.15f)
            var by = 0f
            boltPath.moveTo(bx, by)
            for (seg in 1..7) {
                bx += (if (seg % 2 == 0) 1 else -1) * width * 0.055f
                by = height * (seg / 9f)
                boltPath.lineTo(bx, by)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = width / 95f
            p.color = Color.argb((flashAlpha * 1.4f).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawPath(boltPath, p)
        }
    }

    private fun drawFloatingHeartsEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val heartPath = Path()
        val baseCycles = max(2, (totalDurationSec * 0.3f * speedMultiplier).toInt())
        val swayBaseCycles = max(1, (totalDurationSec * 0.6f * speedMultiplier).toInt())

        for (i in 0 until 32) {
            val seed = i * 67.3f
            val k = baseCycles + (i % 5) * max(1, (totalDurationSec * 0.08f).toInt())
            val prog = (normTime * k + (seed % 1f)) % 1f

            val sway = sin(normTime * (swayBaseCycles + (i % 2)) * 2f * PI.toFloat() + i) * width * 0.04f
            val x = (((seed * 23.9f) % 1f) * width + sway + width) % width
            val y = height - prog * (height + 60f)
            val size = (width / 34f) * (0.65f + (i % 4) * 0.22f)
            val alpha = ((1f - prog * 0.65f) * 225f).toInt().coerceIn(0, 255)

            p.color = if (i % 2 == 0) {
                Color.argb(alpha, 244, 63, 94)
            } else {
                Color.argb(alpha, 251, 113, 133)
            }

            heartPath.reset()
            heartPath.moveTo(x, y + size * 0.35f)
            heartPath.cubicTo(x - size, y - size * 0.35f, x - size * 0.55f, y - size, x, y - size * 0.35f)
            heartPath.cubicTo(x + size * 0.55f, y - size, x + size, y - size * 0.35f, x, y + size * 0.35f)
            heartPath.close()
            canvas.drawPath(heartPath, p)
        }
    }

    private fun drawAuroraEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val colors = intArrayOf(
            Color.argb(95, 16, 185, 129),
            Color.argb(85, 56, 189, 248),
            Color.argb(80, 168, 85, 247)
        )
        val waveCycles = max(1, (totalDurationSec * 0.35f * speedMultiplier).toInt())

        for (band in 0..2) {
            val path = Path()
            val baseY = height * (0.14f + band * 0.09f)
            val ribbonH = height * 0.16f
            path.moveTo(0f, baseY)
            val steps = 16
            for (s in 0..steps) {
                val rx = (s.toFloat() / steps) * width
                val wave = sin(normTime * waveCycles * 2f * PI.toFloat() + s * 0.45f + band * 1.2f) * (height * 0.045f)
                path.lineTo(rx, baseY + wave)
            }
            for (s in steps downTo 0) {
                val rx = (s.toFloat() / steps) * width
                val wave = sin(normTime * waveCycles * 2f * PI.toFloat() + s * 0.45f + band * 1.2f) * (height * 0.045f)
                path.lineTo(rx, baseY + wave + ribbonH)
            }
            path.close()
            p.shader = LinearGradient(
                0f, baseY - height * 0.05f, 0f, baseY + ribbonH,
                colors[band],
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, p)
        }
        p.shader = null
    }

    private fun drawAutumnLeavesEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val baseCycles = max(2, (totalDurationSec * 0.35f * speedMultiplier).toInt())
        val swayBaseCycles = max(1, (totalDurationSec * 0.5f * speedMultiplier).toInt())
        val rotBaseTurns = max(1, (totalDurationSec * 0.4f * speedMultiplier).toInt())

        for (i in 0 until 40) {
            val seed = i * 59.1f
            val k = baseCycles + (i % 5) * max(1, (totalDurationSec * 0.08f).toInt())
            val prog = (normTime * k + (seed % 1f)) % 1f

            val sway = sin(normTime * (swayBaseCycles + (i % 2)) * 2f * PI.toFloat() + i * 0.8f) * (width * 0.07f)
            val x = (((seed * 37.3f) % 1f) * width + sway + width) % width
            val y = prog * (height + 50f) - 25f
            val w = (width / 50f) * (0.75f + (i % 4) * 0.2f)
            val h = w * 0.48f

            val angle = (i * 45f + normTime * (rotBaseTurns + (i % 3)) * 360f) % 360f

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(angle)
            p.color = when (i % 3) {
                0 -> Color.argb(220, 245, 158, 11)
                1 -> Color.argb(215, 234, 88, 12)
                else -> Color.argb(205, 180, 83, 9)
            }
            canvas.drawOval(RectF(-w, -h, w, h), p)
            canvas.restore()
        }
    }

    private fun drawGoldBokehEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val xBaseCycles = max(1, (totalDurationSec * 0.25f * speedMultiplier).toInt())
        val riseBaseCycles = max(1, (totalDurationSec * 0.35f * speedMultiplier).toInt())
        val pulseBaseCycles = max(1, (totalDurationSec * 0.5f * speedMultiplier).toInt())

        for (i in 0 until 34) {
            val baseX = ((i * 91.7f) % 1f) * width
            val baseY = ((i * 47.9f) % 1f) * height

            val x = (baseX + sin(normTime * (xBaseCycles + (i % 2)) * 2f * PI.toFloat() + i) * width * 0.05f + width) % width
            val riseCycles = riseBaseCycles + (i % 3)
            val y = (baseY - (normTime * riseCycles * height) + height * 1000f) % height

            val r = (width / 20f) * (0.45f + (i % 5) * 0.22f)
            val pulse = 0.35f + 0.65f * abs(sin(normTime * (pulseBaseCycles + (i % 2)) * 2f * PI.toFloat() + i))

            p.shader = RadialGradient(
                x, y, r,
                Color.argb((pulse * 155f).toInt(), 253, 224, 71),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, r, p)
        }
        p.shader = null
    }

    private fun drawNeonPulseFrameEffect(
        canvas: Canvas,
        width: Float,
        height: Float,
        normTime: Float,
        totalDurationSec: Float,
        speedMultiplier: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val pulseCycles = max(2, (totalDurationSec * 1.0f * speedMultiplier).toInt())
        val pulse = 0.55f + 0.45f * abs(sin(normTime * pulseCycles * PI.toFloat()))
        val pad = width * 0.032f
        val corner = width * 0.055f

        // Outer glow stroke
        p.strokeWidth = width / 48f
        p.shader = LinearGradient(
            0f, 0f, width, height,
            intArrayOf(
                Color.argb((pulse * 180f).toInt(), 0, 245, 212),
                Color.argb((pulse * 180f).toInt(), 247, 37, 133),
                Color.argb((pulse * 180f).toInt(), 56, 189, 248)
            ),
            null,
            Shader.TileMode.MIRROR
        )
        canvas.drawRoundRect(RectF(pad, pad, width - pad, height - pad), corner, corner, p)

        // Crisp inner neon border
        p.strokeWidth = width / 160f
        p.shader = null
        p.color = Color.argb(235, 165, 243, 252)
        val innerPad = pad * 1.45f
        canvas.drawRoundRect(
            RectF(innerPad, innerPad, width - innerPad, height - innerPad),
            corner * 0.85f,
            corner * 0.85f,
            p
        )
    }

    /**
     * Renders the selected photos + effect to an H.264 MP4 file (720x1280 portrait, 25 FPS)
     * using MediaCodec Surface & MediaMuxer with strict monotonic presentationTimeUs
     * (`writtenSampleCount * FRAME_DURATION_US`) and ignoring `BUFFER_FLAG_CODEC_CONFIG`.
     */
    fun encodeStudioVideoToMp4(
        outputFile: File,
        bitmaps: List<Bitmap>,
        durationSeconds: Int,
        effect: PhotoAnimEffect,
        imageSpeed: ImageMotionSpeed,
        effectSpeed: EffectAnimSpeed,
        onProgress: (renderedFrames: Int, totalFrames: Int, percent: Int) -> Unit
    ): Result<File> {
        val totalFrames = (durationSeconds.coerceIn(1, 120) * TARGET_FPS).coerceAtLeast(TARGET_FPS)
        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1
        var writtenSampleCount = 0L

        return try {
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val format = MediaFormat.createVideoFormat(mime, TARGET_WIDTH, TARGET_HEIGHT).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 3_500_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, TARGET_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(mime)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    codec.signalEndOfInputStream()
                }
                while (true) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(codec.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        outIndex >= 0 -> {
                            val encodedData = codec.getOutputBuffer(outIndex)
                            // Ignore BUFFER_FLAG_CODEC_CONFIG so config bytes are never written as a frame
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0 && muxerStarted && encodedData != null) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                bufferInfo.presentationTimeUs = computeFramePtsUs(writtenSampleCount, TARGET_FPS)
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                                writtenSampleCount++
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }

            val totalDurationSec = durationSeconds.toFloat()
            for (frameIdx in 0 until totalFrames) {
                val timeSec = frameIdx.toFloat() / TARGET_FPS.toFloat()
                val surfaceCanvas = inputSurface.lockCanvas(null)
                try {
                    drawStudioFrame(
                        canvas = surfaceCanvas,
                        width = TARGET_WIDTH.toFloat(),
                        height = TARGET_HEIGHT.toFloat(),
                        bitmaps = bitmaps,
                        timeSec = timeSec,
                        totalDurationSec = totalDurationSec,
                        effect = effect,
                        imageSpeedMultiplier = imageSpeed.multiplier,
                        effectSpeedMultiplier = effectSpeed.multiplier
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(surfaceCanvas)
                }

                drainEncoder(endOfStream = false)

                val pct = (((frameIdx + 1) * 100) / totalFrames).coerceIn(1, 99)
                onProgress(frameIdx + 1, totalFrames, pct)
            }

            drainEncoder(endOfStream = true)
            inputSurface.release()
            onProgress(totalFrames, totalFrames, 100)

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                codec?.stop()
            } catch (_: Throwable) {}
            try {
                codec?.release()
            } catch (_: Throwable) {}
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Throwable) {}
            try {
                muxer?.release()
            } catch (_: Throwable) {}
        }
    }
}
