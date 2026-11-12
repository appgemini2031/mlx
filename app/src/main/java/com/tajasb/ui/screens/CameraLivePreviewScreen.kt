@file:Suppress("DEPRECATION")

package com.tajasb.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Shared Live Overlay Renderer for BOTH Stiker (CustomStikerManager) and Watermark (CustomWatermarkManager).
 * Supports all 4 Display Styles:
 * - WITH_BLOCK ("Pakai Blok")
 * - TEXT_ONLY ("Tanpa Blok - Cuman Teks")
 * - OUTLINE_ONLY ("Outline Tanpa Blok dengan Teks")
 * - HOLLOW_OUTLINE ("Hanya Outline")
 * And supports real-time Running Text (Marquee) for both Stiker and Watermark.
 */
@Composable
fun StikerAndWatermarkLiveOverlay(
    modifier: Modifier = Modifier,
    compactScale: Float = 1.0f
) {
    val context = LocalContext.current

    // 1. Stiker State
    val stikerManager = remember { com.tajasb.stiker.CustomStikerManager.getInstance(context) }
    val isStikerEnabled by stikerManager.isEnabled.collectAsState()
    val stikerText by stikerManager.stikerText.collectAsState()
    val stikerPosX by stikerManager.posXpct.collectAsState()
    val stikerPosY by stikerManager.posYpct.collectAsState()
    val stikerScale by stikerManager.sizeScale.collectAsState()
    val stikerRot by stikerManager.rotation.collectAsState()
    val stikerMirrored by stikerManager.isMirrored.collectAsState()
    val stikerBoxColor by stikerManager.boxColorHex.collectAsState()
    val stikerBoxAlpha by stikerManager.boxAlpha.collectAsState()
    val stikerTextColor by stikerManager.textColorHex.collectAsState()
    val stikerOutlineW by stikerManager.outlineWidth.collectAsState()
    val stikerOutlineColor by stikerManager.outlineColorHex.collectAsState()
    val stikerStyle by stikerManager.displayStyle.collectAsState()
    val stikerRunning by stikerManager.isRunningText.collectAsState()
    val stikerRunSpeed by stikerManager.runningTextSpeed.collectAsState()
    val stikerRunDir by stikerManager.runningTextDirection.collectAsState()

    // 2. Watermark State (Terpisah penuh dari Stiker)
    val watermarkManager = remember { com.tajasb.stiker.CustomWatermarkManager.getInstance(context) }
    val isWatermarkEnabled by watermarkManager.isEnabled.collectAsState()
    val watermarkText by watermarkManager.watermarkText.collectAsState()
    val watermarkPosX by watermarkManager.posXpct.collectAsState()
    val watermarkPosY by watermarkManager.posYpct.collectAsState()
    val watermarkScale by watermarkManager.sizeScale.collectAsState()
    val watermarkRot by watermarkManager.rotation.collectAsState()
    val watermarkMirrored by watermarkManager.isMirrored.collectAsState()
    val watermarkBoxColor by watermarkManager.boxColorHex.collectAsState()
    val watermarkBoxAlpha by watermarkManager.boxAlpha.collectAsState()
    val watermarkTextColor by watermarkManager.textColorHex.collectAsState()
    val watermarkOutlineW by watermarkManager.outlineWidth.collectAsState()
    val watermarkOutlineColor by watermarkManager.outlineColorHex.collectAsState()
    val watermarkStyle by watermarkManager.displayStyle.collectAsState()
    val watermarkRunning by watermarkManager.isRunningText.collectAsState()
    val watermarkRunSpeed by watermarkManager.runningTextSpeed.collectAsState()
    val watermarkRunDir by watermarkManager.runningTextDirection.collectAsState()

    // 3. Auto Time Capsule State ("Waktu Otomatis")
    val autoTimeManager = remember { com.tajasb.notif.AutoTimeCapsuleManager.getInstance(context) }
    val isAutoTimeEnabled by autoTimeManager.isEnabled.collectAsState()
    val autoTimeShowSeconds by autoTimeManager.showSeconds.collectAsState()
    val autoTimeCapsuleBgHex by autoTimeManager.capsuleBgColorHex.collectAsState()
    val autoTimeCapsuleAlpha by autoTimeManager.capsuleAlpha.collectAsState()
    val autoTimeCornerRad by autoTimeManager.capsuleCornerRadius.collectAsState()
    val autoTimePadH by autoTimeManager.capsulePaddingH.collectAsState()
    val autoTimeOutlineW by autoTimeManager.capsuleOutlineWidth.collectAsState()
    val autoTimeOutlineHex by autoTimeManager.capsuleOutlineColorHex.collectAsState()
    val autoTimeDateBgHex by autoTimeManager.dateBoxBgColorHex.collectAsState()
    val autoTimeDateTextHex by autoTimeManager.dateBoxTextColorHex.collectAsState()
    val autoTimeClockTextHex by autoTimeManager.timeTextColorHex.collectAsState()
    val autoTimeScale by autoTimeManager.sizeScale.collectAsState()
    val autoTimePosX by autoTimeManager.posXpct.collectAsState()
    val autoTimePosY by autoTimeManager.posYpct.collectAsState()
    val autoTimeRot by autoTimeManager.rotation.collectAsState()
    val autoTimeMirrored by autoTimeManager.isMirrored.collectAsState()

    var animTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var wallClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val needAnim = (isStikerEnabled && stikerRunning) || (isWatermarkEnabled && watermarkRunning)
    LaunchedEffect(needAnim) {
        while (needAnim) {
            withFrameMillis { frameMs ->
                animTimeMs = frameMs
            }
        }
    }
    LaunchedEffect(isAutoTimeEnabled) {
        while (isAutoTimeEnabled) {
            wallClockMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(500L)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        if (isStikerEnabled) {
            CanvasStyledBannerItem(
                rawText = stikerText,
                posXpct = stikerPosX,
                posYpct = stikerPosY,
                scale = stikerScale * compactScale,
                rotationDeg = stikerRot,
                isMirrored = stikerMirrored,
                boxColorHex = stikerBoxColor,
                boxAlpha = stikerBoxAlpha,
                textColorHex = stikerTextColor,
                outlineWidthDp = stikerOutlineW,
                outlineColorHex = stikerOutlineColor,
                displayStyle = stikerStyle,
                isRunningText = stikerRunning,
                runningSpeed = stikerRunSpeed,
                runningDirection = stikerRunDir,
                animTimeMs = animTimeMs,
                containerWidth = maxWidth,
                containerHeight = maxHeight
            )
        }

        if (isWatermarkEnabled) {
            CanvasStyledBannerItem(
                rawText = watermarkText,
                posXpct = watermarkPosX,
                posYpct = watermarkPosY,
                scale = watermarkScale * compactScale,
                rotationDeg = watermarkRot,
                isMirrored = watermarkMirrored,
                boxColorHex = watermarkBoxColor,
                boxAlpha = watermarkBoxAlpha,
                textColorHex = watermarkTextColor,
                outlineWidthDp = watermarkOutlineW,
                outlineColorHex = watermarkOutlineColor,
                displayStyle = watermarkStyle,
                isRunningText = watermarkRunning,
                runningSpeed = watermarkRunSpeed,
                runningDirection = watermarkRunDir,
                animTimeMs = animTimeMs,
                containerWidth = maxWidth,
                containerHeight = maxHeight
            )
        }

        if (isAutoTimeEnabled) {
            CanvasAutoTimeCapsuleItem(
                wallClockMs = wallClockMs,
                showSeconds = autoTimeShowSeconds,
                capsuleBgHex = autoTimeCapsuleBgHex,
                capsuleAlpha = autoTimeCapsuleAlpha,
                cornerRadiusDp = autoTimeCornerRad,
                paddingHDp = autoTimePadH,
                outlineWidthDp = autoTimeOutlineW,
                outlineColorHex = autoTimeOutlineHex,
                dateBoxBgHex = autoTimeDateBgHex,
                dateBoxTextHex = autoTimeDateTextHex,
                timeTextHex = autoTimeClockTextHex,
                scale = autoTimeScale * compactScale,
                posXpct = autoTimePosX,
                posYpct = autoTimePosY,
                rotationDeg = autoTimeRot,
                isMirrored = autoTimeMirrored,
                containerWidth = maxWidth,
                containerHeight = maxHeight
            )
        }
    }
}

@Composable
private fun CanvasAutoTimeCapsuleItem(
    wallClockMs: Long,
    showSeconds: Boolean,
    capsuleBgHex: String,
    capsuleAlpha: Float,
    cornerRadiusDp: Int,
    paddingHDp: Int,
    outlineWidthDp: Int,
    outlineColorHex: String,
    dateBoxBgHex: String,
    dateBoxTextHex: String,
    timeTextHex: String,
    scale: Float,
    posXpct: Float,
    posYpct: Float,
    rotationDeg: Int,
    isMirrored: Boolean,
    containerWidth: androidx.compose.ui.unit.Dp,
    containerHeight: androidx.compose.ui.unit.Dp
) {
    val density = LocalDensity.current.density
    val effScale = scale.coerceIn(0.45f, 2.5f)
    val (dayStr, monthStr, timeStr) = remember(wallClockMs / 1000L, showSeconds) {
        com.tajasb.notif.AutoTimeCapsuleManager.formatDateAndTimeParts(wallClockMs, showSeconds)
    }

    val dayFontPx = 11.5f * density * effScale
    val monthFontPx = 7.5f * density * effScale
    val timeFontPx = 15.5f * density * effScale

    val timeMeasurePaint = remember(timeFontPx) {
        android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            fontFeatureSettings = "tnum"
            textSize = timeFontPx
        }
    }

    val badgeSizePx = 30f * density * effScale
    val outerPadL = 4f * density * effScale
    val outerPadR = paddingHDp.coerceIn(6, 40) * density * effScale
    val outerPadV = 4f * density * effScale
    val gapBetween = 8f * density * effScale

    val sampleTimeTemplate = if (showSeconds) "88:88:88" else "88:88"
    val timeTextW = maxOf(timeMeasurePaint.measureText(timeStr), timeMeasurePaint.measureText(sampleTimeTemplate))
    val totalW = (outerPadL + badgeSizePx + gapBetween + timeTextW + outerPadR).coerceAtLeast(70f * density)
    val totalH = (badgeSizePx + outerPadV * 2f).coerceAtLeast(28f * density)

    val wDp = with(LocalDensity.current) { totalW.toDp() }
    val hDp = with(LocalDensity.current) { totalH.toDp() }

    val leftOffset = (containerWidth * posXpct).coerceIn(0.dp, (containerWidth - wDp).coerceAtLeast(0.dp))
    val topOffset = (containerHeight * posYpct).coerceIn(0.dp, (containerHeight - hDp).coerceAtLeast(0.dp))

    Canvas(
        modifier = Modifier
            .offset(x = leftOffset, y = topOffset)
            .size(wDp, hDp)
            .graphicsLayer {
                rotationZ = rotationDeg.toFloat()
                scaleX = if (isMirrored) -1f else 1f
            }
    ) {
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val capBgCol = try { android.graphics.Color.parseColor(capsuleBgHex) } catch (_: Throwable) { android.graphics.Color.parseColor("#EA580C") }
        val outCol = try { android.graphics.Color.parseColor(outlineColorHex) } catch (_: Throwable) { android.graphics.Color.WHITE }
        val dateBgCol = try { android.graphics.Color.parseColor(dateBoxBgHex) } catch (_: Throwable) { android.graphics.Color.WHITE }
        val dateTxtCol = try { android.graphics.Color.parseColor(dateBoxTextHex) } catch (_: Throwable) { android.graphics.Color.parseColor("#EA580C") }
        val clockTxtCol = try { android.graphics.Color.parseColor(timeTextHex) } catch (_: Throwable) { android.graphics.Color.WHITE }

        val alphaInt = (capsuleAlpha.coerceIn(0.15f, 1.0f) * 255).toInt().coerceIn(38, 255)
        val inset = if (outlineWidthDp > 0) (outlineWidthDp * density * 0.6f) else 1f
        val pillRect = android.graphics.RectF(inset, inset, size.width - inset, size.height - inset)
        val maxRad = pillRect.height() / 2f
        val pillRad = if (cornerRadiusDp >= 32) maxRad else (cornerRadiusDp * density * effScale).coerceAtMost(maxRad)

        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.argb(alphaInt, android.graphics.Color.red(capBgCol), android.graphics.Color.green(capBgCol), android.graphics.Color.blue(capBgCol))
        }
        nativeCanvas.drawRoundRect(pillRect, pillRad, pillRad, bgPaint)

        if (outlineWidthDp > 0) {
            val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = (outlineWidthDp * density).coerceAtLeast(1f)
                color = android.graphics.Color.argb(alphaInt.coerceAtLeast(210), android.graphics.Color.red(outCol), android.graphics.Color.green(outCol), android.graphics.Color.blue(outCol))
            }
            nativeCanvas.drawRoundRect(pillRect, pillRad, pillRad, strokePaint)
        }

        // Left Date + Month Box
        val badgeLeft = inset + outerPadL
        val badgeTop = (size.height - badgeSizePx) / 2f
        val badgeRect = android.graphics.RectF(badgeLeft, badgeTop, badgeLeft + badgeSizePx, badgeTop + badgeSizePx)
        val badgeRad = (pillRad * 0.78f).coerceIn(4f * density, badgeSizePx * 0.44f)

        val badgeBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.argb(alphaInt.coerceAtLeast(225), android.graphics.Color.red(dateBgCol), android.graphics.Color.green(dateBgCol), android.graphics.Color.blue(dateBgCol))
        }
        nativeCanvas.drawRoundRect(badgeRect, badgeRad, badgeRad, badgeBgPaint)

        val dayPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textSize = dayFontPx
            color = dateTxtCol
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val monthPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textSize = monthFontPx
            color = dateTxtCol
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val badgeCx = badgeRect.centerX()
        val dayBaseline = badgeRect.top + badgeSizePx * 0.49f
        val monthBaseline = badgeRect.top + badgeSizePx * 0.84f
        nativeCanvas.drawText(dayStr, badgeCx, dayBaseline, dayPaint)
        nativeCanvas.drawText(monthStr, badgeCx, monthBaseline, monthPaint)

        // Right Clock Text
        val timePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            fontFeatureSettings = "tnum"
            textSize = timeFontPx
            color = clockTxtCol
        }
        val timeX = badgeRect.right + gapBetween
        val timeFm = timePaint.fontMetrics
        val timeBaseline = (size.height / 2f) - ((timeFm.ascent + timeFm.descent) / 2f)
        nativeCanvas.drawText(timeStr, timeX, timeBaseline, timePaint)
    }
}

@Composable
private fun CanvasStyledBannerItem(
    rawText: String,
    posXpct: Float,
    posYpct: Float,
    scale: Float,
    rotationDeg: Int,
    isMirrored: Boolean,
    boxColorHex: String,
    boxAlpha: Float,
    textColorHex: String,
    outlineWidthDp: Int,
    outlineColorHex: String,
    displayStyle: String,
    isRunningText: Boolean,
    runningSpeed: Float,
    runningDirection: String,
    animTimeMs: Long,
    containerWidth: androidx.compose.ui.unit.Dp,
    containerHeight: androidx.compose.ui.unit.Dp
) {
    val density = LocalDensity.current.density
    val lines = remember(rawText) {
        val filtered = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(5)
        if (filtered.isEmpty()) listOf("⚡ LIVE") else filtered
    }
    val lineCount = lines.size
    val dispStyle = displayStyle.uppercase()
    val isWithBlock = (dispStyle == "WITH_BLOCK" && boxAlpha > 0.01f)
    val isOutlineOnly = (dispStyle == "OUTLINE_ONLY")
    val isHollowOutline = (dispStyle == "HOLLOW_OUTLINE")

    val baseFontSp = if (lineCount > 3) 11f else 12.5f
    val fontPx = baseFontSp * density * scale.coerceIn(0.45f, 2.5f)
    val padH = 10f * density * scale.coerceIn(0.5f, 2.0f)
    val padV = 6f * density * scale.coerceIn(0.5f, 2.0f)
    val lineHeight = fontPx * 1.30f

    val textMeasurePaint = remember(fontPx) {
        android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textSize = fontPx
        }
    }
    val maxLineW = lines.maxOfOrNull { textMeasurePaint.measureText(it) } ?: 80f
    val minRunW = 140f * density * scale.coerceIn(0.5f, 1.5f)
    val maxW = with(LocalDensity.current) { (containerWidth * 0.92f).toPx() }.coerceAtLeast(120f)
    val totalW = if (isRunningText) {
        (maxLineW + padH * 2).coerceIn(minRunW.coerceAtMost(maxW), maxW)
    } else {
        (maxLineW + padH * 2).coerceIn(48f, maxW)
    }
    val totalH = (lineHeight * lineCount + padV * 2).coerceAtLeast(24f)

    val wDp = with(LocalDensity.current) { totalW.toDp() }
    val hDp = with(LocalDensity.current) { totalH.toDp() }

    val leftOffset = (containerWidth * posXpct).coerceIn(0.dp, (containerWidth - wDp).coerceAtLeast(0.dp))
    val topOffset = (containerHeight * posYpct).coerceIn(0.dp, (containerHeight - hDp).coerceAtLeast(0.dp))

    Canvas(
        modifier = Modifier
            .offset(x = leftOffset, y = topOffset)
            .size(wDp, hDp)
            .graphicsLayer {
                rotationZ = rotationDeg.toFloat()
                scaleX = if (isMirrored) -1f else 1f
            }
    ) {
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val boxColor = try { android.graphics.Color.parseColor(boxColorHex) } catch (_: Throwable) { android.graphics.Color.parseColor("#DC2626") }
        val textColor = try { android.graphics.Color.parseColor(textColorHex) } catch (_: Throwable) { android.graphics.Color.WHITE }
        val baseOutlineCol = try { android.graphics.Color.parseColor(outlineColorHex) } catch (_: Throwable) { android.graphics.Color.BLACK }

        val alphaInt = (boxAlpha * 255).toInt().coerceIn(0, 255)
        val framelessAlpha = if (!isWithBlock) alphaInt.coerceIn(50, 255) else 255

        val effectiveTextColor = android.graphics.Color.argb(
            framelessAlpha,
            android.graphics.Color.red(textColor),
            android.graphics.Color.green(textColor),
            android.graphics.Color.blue(textColor)
        )
        val rawOutlineCol = if (isOutlineOnly && baseOutlineCol == textColor) {
            if (textColor == android.graphics.Color.BLACK) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        } else {
            baseOutlineCol
        }
        val effectiveOutlineCol = android.graphics.Color.argb(
            framelessAlpha,
            android.graphics.Color.red(rawOutlineCol),
            android.graphics.Color.green(rawOutlineCol),
            android.graphics.Color.blue(rawOutlineCol)
        )

        val fillPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textSize = fontPx
            color = effectiveTextColor
            this.style = android.graphics.Paint.Style.FILL
        }

        val strokePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textSize = fontPx
            color = effectiveOutlineCol
            this.style = android.graphics.Paint.Style.STROKE
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.ROUND
            val effOutlineDp = if (isOutlineOnly || isHollowOutline) outlineWidthDp.coerceAtLeast(2) else outlineWidthDp
            strokeWidth = (effOutlineDp * density * (if (isOutlineOnly) 1.5f else 1.15f)).coerceAtLeast(1.8f)
        }

        if (isWithBlock) {
            val rect = android.graphics.RectF(0f, 0f, size.width, size.height)
            val cornerRadius = 6f * density
            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.argb(
                    alphaInt,
                    android.graphics.Color.red(boxColor),
                    android.graphics.Color.green(boxColor),
                    android.graphics.Color.blue(boxColor)
                )
            }
            nativeCanvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

            if (outlineWidthDp > 0) {
                val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = android.graphics.Paint.Style.STROKE
                    strokeWidth = (outlineWidthDp * density).coerceAtLeast(1f)
                    color = android.graphics.Color.argb(
                        alphaInt.coerceAtLeast(180),
                        android.graphics.Color.red(baseOutlineCol),
                        android.graphics.Color.green(baseOutlineCol),
                        android.graphics.Color.blue(baseOutlineCol)
                    )
                }
                nativeCanvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
            }
        }

        fun drawLineStyled(str: String, x: Float, y: Float) {
            when {
                isHollowOutline -> {
                    nativeCanvas.drawText(str, x, y, strokePaint)
                }
                isOutlineOnly -> {
                    nativeCanvas.drawText(str, x, y, strokePaint)
                    nativeCanvas.drawText(str, x, y, fillPaint)
                }
                else -> {
                    nativeCanvas.drawText(str, x, y, fillPaint)
                }
            }
        }

        if (isRunningText) {
            nativeCanvas.save()
            val clipPad = if (isWithBlock) (3f * density) else 0f
            nativeCanvas.clipRect(clipPad, 0f, size.width - clipPad, size.height)
            val speedPxPerSec = 65f * density * runningSpeed.coerceIn(0.3f, 3.0f)
            val elapsedSec = ((animTimeMs % 3600000L) / 1000.0).toFloat()
            val sep = "   •   "
            for (i in lines.indices) {
                val chunk = lines[i] + sep
                val cycleW = fillPaint.measureText(chunk).coerceAtLeast(30f)
                val shift = (elapsedSec * speedPxPerSec) % cycleW
                val textY = padV + (i * lineHeight) + (fontPx * 0.88f)
                var startX = if (runningDirection.equals("RIGHT", ignoreCase = true)) {
                    padH + shift - cycleW
                } else {
                    padH - shift
                }
                while (startX > padH) startX -= cycleW
                var drawX = startX
                while (drawX < size.width) {
                    drawLineStyled(chunk, drawX, textY)
                    drawX += cycleW
                }
            }
            nativeCanvas.restore()
        } else {
            for (i in lines.indices) {
                val line = lines[i]
                val lineW = fillPaint.measureText(line)
                val textX = ((size.width - lineW) / 2f).coerceAtLeast(padH * 0.4f)
                val textY = padV + (i * lineHeight) + (fontPx * 0.88f)
                drawLineStyled(line, textX, textY)
            }
        }
    }
}
