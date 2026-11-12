package com.tajasb.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.tajasb.OverlayBannerData

/**
 * OverlayBannerBitmapGenerator:
 * Dedicated generator for rendering Live Comments / Doa, Buyer Notification Badges,
 * Custom Stickers (Stiker), and Custom Watermarks into high-resolution ARGB_8888 Bitmaps,
 * as well as computing portrait-viewer-space NDC vertex coordinates.
 */
object OverlayBannerBitmapGenerator {

    fun drawShoppingBagIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
        val bagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        val centerX = x + size / 2f
        val bodyW = size * 0.88f
        val bodyH = size * 0.70f
        val bodyTop = y + size * 0.28f
        val bodyBottom = bodyTop + bodyH
        val bodyLeft = centerX - bodyW / 2f
        val bodyRight = centerX + bodyW / 2f
        val corner = size * 0.16f

        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.13f
            strokeCap = Paint.Cap.ROUND
            color = Color.WHITE
        }
        val handleW = size * 0.44f
        val handleH = size * 0.46f
        val handleRect = RectF(centerX - handleW / 2f, y + size * 0.02f, centerX + handleW / 2f, y + size * 0.02f + handleH)
        canvas.drawArc(handleRect, 180f, 180f, false, handlePaint)

        val path = android.graphics.Path().apply {
            moveTo(bodyLeft + corner, bodyTop)
            lineTo(bodyRight - corner, bodyTop)
            quadTo(bodyRight, bodyTop, bodyRight, bodyTop + corner)
            lineTo(bodyRight - size * 0.02f, bodyBottom - corner)
            quadTo(bodyRight - size * 0.02f, bodyBottom, bodyRight - corner, bodyBottom)
            lineTo(bodyLeft + corner, bodyBottom)
            quadTo(bodyLeft + size * 0.02f, bodyBottom, bodyLeft + size * 0.02f, bodyBottom - corner)
            lineTo(bodyLeft, bodyTop + corner)
            quadTo(bodyLeft, bodyTop, bodyLeft + corner, bodyTop)
            close()
        }
        canvas.drawPath(path, bagPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#D97724")
        }
        val dotRadius = size * 0.075f
        val dotY = bodyTop + bodyH * 0.36f
        canvas.drawCircle(centerX - bodyW * 0.23f, dotY, dotRadius, dotPaint)
        canvas.drawCircle(centerX + bodyW * 0.23f, dotY, dotRadius, dotPaint)
    }

    fun createBannerBitmap(
        banner: OverlayBannerData,
        maxWidthPx: Int,
        heightPx: Int,
        interSemiBoldTypeface: Typeface,
        activeDoaBanner: OverlayBannerData? = null,
        activeGiftBanner: OverlayBannerData? = null,
        activeLikeBanner: OverlayBannerData? = null,
        secondaryStatsBanner: OverlayBannerData? = null
    ): Bitmap {
        val densityScale = (heightPx / 74f).coerceIn(0.35f, 2.5f)

        if (banner.bannerType == "STICKER" || banner.bannerType == "WATERMARK") {
            return generateStickerBitmap(banner, densityScale, maxWidthPx, System.currentTimeMillis())
        }
        if (banner.bannerType == "AUTO_TIME") {
            return generateAutoTimeBitmap(banner, densityScale, maxWidthPx, System.currentTimeMillis())
        }
        if (banner.bannerType == "QURAN_KHATAM") {
            return generateQuranKhatamBitmap(
                banner = banner,
                densityScale = densityScale,
                maxWidthPx = maxWidthPx,
                activeDoaBanner = activeDoaBanner,
                activeGiftBanner = activeGiftBanner,
                activeLikeBanner = activeLikeBanner,
                secondaryStatsBanner = secondaryStatsBanner
            )
        }
        if (banner.bannerType == "QURAN_STATS" || banner.bannerType == "EVENT_STATS") {
            return generateUnifiedTopDashboardBitmap(banner, secondaryStatsBanner, densityScale, maxWidthPx)
        }
        if (banner.bannerType == "TOP_LIKERS") {
            return generateTopLikersBitmap(banner, densityScale, maxWidthPx, interSemiBoldTypeface)
        }
        if (banner.bannerType == "EVENT_LIKE") {
            return generateEventLikeBitmap(banner, densityScale, maxWidthPx, interSemiBoldTypeface)
        }
        if (banner.bannerType == "EVENT_GIFT") {
            return generateEventGiftBitmap(banner, densityScale, maxWidthPx, interSemiBoldTypeface)
        }
        if (banner.bannerType == "AVATAR_BUBBLE" || (banner.content.contains("|||") && banner.content.split("|||").size >= 3)) {
            return generateAvatarBubbleBitmap(banner, densityScale, interSemiBoldTypeface)
        }
        if (banner.bannerType == "TUG_OF_WAR") {
            return generateTugOfWarBitmap(banner, densityScale, maxWidthPx, interSemiBoldTypeface)
        }

        val isBuyer = (banner.bannerType == "BUYER")
        val isComment = (banner.bannerType == "COMMENT")
        val propScale = if (isComment) banner.bannerScale.coerceIn(0.55f, 1.85f) else 1.0f
        val effectiveHeightPx = if (isComment) {
            (heightPx * propScale).toInt().coerceIn(44, 180)
        } else {
            heightPx
        }
        val effectiveDensity = densityScale * propScale

        val padH = (if (isBuyer) banner.capsulePaddingH.toFloat() else 16f) * effectiveDensity
        val cornerRadius = if (isBuyer) banner.capsuleCornerRadius.toFloat() * effectiveDensity else (if (isComment) 14f * effectiveDensity else effectiveHeightPx / 2f)

        val isShopping = (banner.bannerType == "BUYER" || banner.iconMode == "SHOPPING")
        val fontScale = if (isBuyer) (banner.fontSizeSp / 10.5f).coerceIn(0.6f, 2.2f) else 1.0f
        val iconScale = if (isBuyer) (banner.cartIconSizeDp / 13.0f).coerceIn(0.5f, 2.5f) else 1.0f
        val iconSize = (24f * effectiveDensity) * iconScale

        val iconStr = when {
            isShopping -> ""
            banner.isGift -> "🎁 "
            banner.iconMode == "PRAYER" || banner.content.contains("🤲") || banner.content.contains("doa", ignoreCase = true) -> "🤲 "
            banner.iconMode == "PIN" -> "📌 "
            else -> ""
        }

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (isBuyer) interSemiBoldTypeface else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = (23f * effectiveDensity) * fontScale
            color = when {
                banner.isGift -> Color.parseColor("#FDE047")
                isBuyer -> Color.parseColor("#FFFFFF")
                else -> Color.parseColor("#38BDF8")
            }
            if (!isBuyer) {
                setShadowLayer(2f * effectiveDensity, 0f, 1f * effectiveDensity, 0x50000000.toInt())
            }
        }

        val contentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (isBuyer) interSemiBoldTypeface else Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = (23f * effectiveDensity) * fontScale
            color = Color.parseColor("#FFFFFF")
            if (!isBuyer) {
                setShadowLayer(2f * effectiveDensity, 0f, 1f * effectiveDensity, 0x50000000.toInt())
            }
        }

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 25f * effectiveDensity
        }

        val prefixText = if (banner.customPrefix.isNotBlank()) banner.customPrefix else "Doa"
        val cleanName = if (banner.senderName.isNotBlank() && !banner.senderName.equals("Penonton", ignoreCase = true)) banner.senderName else ""

        val senderPrefix = when {
            banner.bannerType == "BUYER" -> {
                if (cleanName.isNotBlank()) {
                    if (banner.customPrefix.isNotBlank()) "${banner.customPrefix} $cleanName " else "$cleanName "
                } else {
                    ""
                }
            }
            banner.formatMode == "COMMENT_ONLY" -> ""
            banner.formatMode == "NAME_AND_COMMENT" -> {
                if (cleanName.isNotBlank()) "$cleanName: " else "$prefixText: "
            }
            banner.formatMode == "PREFIX_NAME_COMMENT" -> {
                if (cleanName.isNotBlank()) "$prefixText: $cleanName " else "$prefixText: "
            }
            banner.formatMode == "PREFIX_AND_COMMENT" -> "$prefixText: "
            else -> {
                if (banner.customPrefix.isNotBlank()) {
                    if (cleanName.isNotBlank()) "$prefixText: $cleanName " else "$prefixText: "
                } else if (cleanName.isNotBlank()) {
                    "$cleanName: "
                } else {
                    "Penonton: "
                }
            }
        }

        val iconWidth = when {
            isShopping -> iconSize + 6f * effectiveDensity
            iconStr.isNotEmpty() -> iconPaint.measureText(iconStr) + 4f * effectiveDensity
            else -> 0f
        }
        val nameWidth = if (senderPrefix.isNotEmpty()) namePaint.measureText(senderPrefix) else 0f

        val totalWidth = if (isComment) {
            val effWidthPct = if (propScale < 1.0f) propScale.coerceIn(0.55f, 1.0f) else 1.0f
            (maxWidthPx * effWidthPct).toInt().coerceIn((effectiveHeightPx * 2).coerceAtLeast(240), maxWidthPx)
        } else {
            val availW = (maxWidthPx - (padH * 2) - iconWidth - nameWidth).coerceAtLeast(60f)
            val ellipsized = TextUtils.ellipsize(banner.content, contentPaint, availW, TextUtils.TruncateAt.END).toString()
            val cW = contentPaint.measureText(ellipsized)
            (iconWidth + nameWidth + cW + (padH * 2) + 2f * effectiveDensity).toInt()
                .coerceIn((effectiveHeightPx * 1.5f).toInt(), maxWidthPx)
        }

        val availContentWidth = (totalWidth - (padH * 2) - iconWidth - nameWidth).coerceAtLeast(60f)
        val ellipsizedContent = TextUtils.ellipsize(banner.content, contentPaint, availContentWidth, TextUtils.TruncateAt.END).toString()
        val contentWidth = contentPaint.measureText(ellipsizedContent)

        val bitmap = Bitmap.createBitmap(totalWidth, effectiveHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pillRect = RectF(0f, 0f, totalWidth.toFloat(), effectiveHeightPx.toFloat())
        val buyerBaseCol = try {
            Color.parseColor(banner.capsuleBgColor.ifBlank { "#D97724" })
        } catch (_: Throwable) {
            Color.parseColor("#D97724")
        }
        val alphaInt = (banner.capsuleAlpha * 255).toInt().coerceIn(0, 255)
        val buyerBgWithAlpha = Color.argb(
            alphaInt,
            Color.red(buyerBaseCol),
            Color.green(buyerBaseCol),
            Color.blue(buyerBaseCol)
        )

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = when {
                banner.isGift -> Color.parseColor("#E6EA580C")
                banner.bannerType == "BUYER" -> buyerBgWithAlpha
                else -> Color.parseColor("#E60F172A")
            }
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (isBuyer) (banner.capsuleOutlineWidth * effectiveDensity).coerceAtLeast(1f) else 1.4f * effectiveDensity
            color = when {
                banner.isGift -> Color.parseColor("#FFFDE047")
                banner.bannerType == "BUYER" -> {
                    try { Color.parseColor(banner.capsuleOutlineColor.ifBlank { "#FFFFFF" }) } catch (_: Throwable) { Color.WHITE }
                }
                else -> Color.parseColor("#6638BDF8")
            }
        }

        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, bgPaint)
        if (isBuyer) {
            if (banner.capsuleOutlineWidth > 0) {
                canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, borderPaint)
            }
        } else {
            canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, borderPaint)
        }

        val fontMetrics = (if (senderPrefix.isNotEmpty()) namePaint else contentPaint).fontMetrics
        val textCenterY = effectiveHeightPx / 2f
        val baseline = textCenterY - (fontMetrics.descent + fontMetrics.ascent) / 2f

        val combinedTextWidth = iconWidth + nameWidth + contentWidth
        var curX = if (isComment) {
            ((totalWidth - combinedTextWidth) / 2f).coerceAtLeast(padH)
        } else {
            padH
        }
        if (isShopping) {
            val bagTop = (effectiveHeightPx - iconSize) / 2f
            drawShoppingBagIcon(canvas, curX, bagTop, iconSize)
            curX += iconWidth
        } else if (iconStr.isNotEmpty()) {
            canvas.drawText(iconStr, curX, baseline, iconPaint)
            curX += iconWidth
        }

        if (senderPrefix.isNotEmpty()) {
            canvas.drawText(senderPrefix, curX, baseline, namePaint)
            curX += nameWidth
        }

        canvas.drawText(ellipsizedContent, curX, baseline, contentPaint)

        return bitmap
    }

    fun generateStickerBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        maxWidthPx: Int,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Bitmap {
        val boxColor = try { Color.parseColor(banner.customPrefix.ifBlank { "#DC2626" }) } catch (_: Throwable) { Color.parseColor("#DC2626") }
        val textColor = try { Color.parseColor(banner.formatMode.ifBlank { "#FFFFFF" }) } catch (_: Throwable) { Color.WHITE }
        val baseOutlineCol = try { Color.parseColor(banner.stikerOutlineColor.ifBlank { "#FFFFFF" }) } catch (_: Throwable) { Color.WHITE }

        val rawLines = banner.content.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(5)
        val lines = if (rawLines.isEmpty()) listOf(banner.content.trim().ifBlank { "⚡ PROMO LIVE" }) else rawLines
        val lineCount = lines.size

        val dispStyle = banner.stikerDisplayStyle.uppercase()
        val isWithBlock = (dispStyle == "WITH_BLOCK" && banner.capsuleAlpha > 0.01f)
        val isOutlineOnly = (dispStyle == "OUTLINE_ONLY")
        val isHollowOutline = (dispStyle == "HOLLOW_OUTLINE")

        val alphaInt = (banner.capsuleAlpha * 255).toInt().coerceIn(0, 255)
        val framelessAlpha = if (!isWithBlock) alphaInt.coerceIn(50, 255) else 255

        val effectiveTextColor = Color.argb(
            framelessAlpha,
            Color.red(textColor),
            Color.green(textColor),
            Color.blue(textColor)
        )
        val rawOutlineCol = if (isOutlineOnly && baseOutlineCol == textColor) {
            if (textColor == Color.BLACK) Color.WHITE else Color.BLACK
        } else {
            baseOutlineCol
        }
        val effectiveOutlineCol = Color.argb(
            framelessAlpha,
            Color.red(rawOutlineCol),
            Color.green(rawOutlineCol),
            Color.blue(rawOutlineCol)
        )

        val baseFontSize = (if (lineCount > 3) 22f else 26f) * densityScale
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = baseFontSize
            color = effectiveTextColor
            this.style = Paint.Style.FILL
            if (isWithBlock) {
                setShadowLayer(2.0f * densityScale, 0f, 1.0f * densityScale, 0x80000000.toInt())
            }
        }

        val textStrokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = baseFontSize
            color = effectiveOutlineCol
            this.style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            val effOutlineDp = if (isOutlineOnly || isHollowOutline) {
                banner.stikerOutlineWidth.coerceAtLeast(2)
            } else {
                banner.stikerOutlineWidth
            }
            strokeWidth = (effOutlineDp * densityScale * (if (isOutlineOnly) 1.85f else 1.35f)).coerceAtLeast(2.0f)
        }

        val padH = 16f * densityScale
        val padV = 10f * densityScale
        val lineHeight = baseFontSize * 1.30f

        val measuredLineWidths = lines.map { textPaint.measureText(it) }
        val maxLineWidth = measuredLineWidths.maxOrNull() ?: 100f

        val minRunWidth = (240f * densityScale).toInt().coerceAtMost(maxWidthPx)
        val rawWidth = (maxLineWidth + (padH * 2)).toInt()
        val totalWidth = if (banner.isRunningText) {
            rawWidth.coerceIn(minRunWidth, maxWidthPx)
        } else {
            rawWidth.coerceIn(100, maxWidthPx)
        }
        val totalHeight = (lineHeight * lineCount + (padV * 2)).toInt().coerceAtLeast(36)

        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (isWithBlock) {
            val boxColorWithAlpha = Color.argb(
                alphaInt,
                Color.red(boxColor),
                Color.green(boxColor),
                Color.blue(boxColor)
            )
            val rect = RectF(0f, 0f, totalWidth.toFloat(), totalHeight.toFloat())
            val cornerRadius = 8f * densityScale

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL
                color = boxColorWithAlpha
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

            if (banner.stikerOutlineWidth > 0) {
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = (banner.stikerOutlineWidth * densityScale).coerceAtLeast(1f)
                    color = Color.argb(
                        alphaInt.coerceAtLeast(180),
                        Color.red(baseOutlineCol),
                        Color.green(baseOutlineCol),
                        Color.blue(baseOutlineCol)
                    )
                }
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
            }
        }

        fun drawStyledTextLine(textStr: String, x: Float, y: Float) {
            when {
                isHollowOutline -> {
                    canvas.drawText(textStr, x, y, textStrokePaint)
                }
                isOutlineOnly -> {
                    canvas.drawText(textStr, x, y, textStrokePaint)
                    canvas.drawText(textStr, x, y, textPaint)
                }
                else -> {
                    canvas.drawText(textStr, x, y, textPaint)
                }
            }
        }

        if (banner.isRunningText) {
            canvas.save()
            val clipMargin = if (isWithBlock) (4f * densityScale) else 0f
            canvas.clipRect(clipMargin, 0f, totalWidth - clipMargin, totalHeight.toFloat())

            val speedPxPerSec = 95f * densityScale * banner.runningTextSpeed.coerceIn(0.3f, 3.0f)
            val elapsedSec = ((currentTimeMs % 3600000L) / 1000.0).toFloat()
            val sep = "   •   "

            for (i in lines.indices) {
                val line = lines[i]
                val tickerChunk = line + sep
                val cycleW = textPaint.measureText(tickerChunk).coerceAtLeast(40f)
                val shift = (elapsedSec * speedPxPerSec) % cycleW
                val textY = padV + (i * lineHeight) + (baseFontSize * 0.90f)

                var startX = if (banner.runningTextDirection.equals("RIGHT", ignoreCase = true)) {
                    padH + shift - cycleW
                } else {
                    padH - shift
                }
                while (startX > padH) {
                    startX -= cycleW
                }
                var drawX = startX
                while (drawX < totalWidth) {
                    drawStyledTextLine(tickerChunk, drawX, textY)
                    drawX += cycleW
                }
            }
            canvas.restore()
        } else {
            for (i in lines.indices) {
                val line = lines[i]
                val textW = measuredLineWidths[i]
                val textX = (totalWidth - textW) / 2f
                val textY = padV + (i * lineHeight) + (baseFontSize * 0.90f)
                drawStyledTextLine(line, textX.coerceAtLeast(padH * 0.5f), textY)
            }
        }

        return bitmap
    }

    fun computeOverlayQuadNdcCoords(
        banner: OverlayBannerData,
        quadWidth: Int,
        quadHeight: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
        currentRotation: Int,
        isMirrored: Boolean,
        isVerticalFlipped: Boolean,
        slideOffsetX: Float = 0f,
        slideOffsetY: Float = 0f
    ): FloatArray {
        val sW = surfaceWidth.toFloat().coerceAtLeast(1f)
        val sH = surfaceHeight.toFloat().coerceAtLeast(1f)
        val effectiveRotation = (currentRotation + 270) % 360

        val viewW = minOf(sW, sH)
        val viewH = maxOf(sW, sH)

        val isProportionalFrameBanner = banner.bannerType == "QURAN_KHATAM" ||
                banner.bannerType == "QURAN_STATS" ||
                banner.bannerType == "EVENT_STATS" ||
                banner.bannerType == "TOP_LIKERS" ||
                banner.bannerType == "EVENT_GIFT" ||
                banner.bannerType == "EVENT_LIKE" ||
                banner.bannerType == "COMMENT"

        val maxFrameW = (viewW * 0.98f).coerceAtLeast(1f)
        val maxFrameH = (viewH * 0.96f).coerceAtLeast(1f)

        // For proportional frame banners, bannerScale is already applied proportionally inside the
        // bitmap generator ( keeping full-width alignment when >= 1.0f and shrinking proportionally
        // when < 1.0f ). If a direct quad is passed in tests or external callers, scale and clamp
        // proportionally to the camera frame so left/right edges never overflow off-screen.
        val isTopLikersVertical = banner.bannerType == "TOP_LIKERS" && (banner.customPrefix.split(":::").getOrNull(0)?.equals("VERTICAL", ignoreCase = true) == true)
        val isTopLikers = banner.bannerType == "TOP_LIKERS"
        val rawW = if (isTopLikersVertical || isTopLikers) {
            quadWidth.toFloat()
        } else if (isProportionalFrameBanner) {
            if (quadWidth.toFloat() >= viewW * 0.94f && banner.bannerScale < 1.0f) {
                quadWidth.toFloat() * banner.bannerScale
            } else {
                quadWidth.toFloat()
            }
        } else {
            quadWidth.toFloat() * banner.bannerScale
        }
        val rawH = if (banner.bannerType == "QURAN_KHATAM" || isTopLikersVertical || isTopLikers) {
            quadHeight.toFloat()
        } else if (isProportionalFrameBanner) {
            if (quadWidth.toFloat() >= viewW * 0.94f && banner.bannerScale != 1.0f) {
                quadHeight.toFloat()
            } else {
                quadHeight.toFloat() * banner.bannerScale
            }
        } else {
            quadHeight.toFloat() * banner.bannerScale
        }

        val bannerW = if (isProportionalFrameBanner) rawW.coerceAtMost(maxFrameW) else rawW
        val bannerH = if (isProportionalFrameBanner) rawH.coerceAtMost(maxFrameH) else rawH

        // Overlays (stickers, watermarks, buyer notifs, time capsules) must NEVER be mirrored
        // or repositioned when the camera/video mirror is active. Only the video itself is mirrored.
        val effectivePosX = banner.posXpct
        val effectivePosY = banner.posYpct

        val baseLeftX = if (isProportionalFrameBanner) {
            effectivePosX * viewW - (bannerW / 2f)
        } else {
            effectivePosX * viewW
        }

        val baseTopY = if (banner.bannerType == "QURAN_KHATAM") {
            // Bagian atas Kartu Khatam (Top-Anchored):
            // Tepi atas kartu tidak berkedut saat ada penambahan ayat panjang atau notif event
            val lockedRefHalfH = viewH * (260f / 3840f)
            effectivePosY * viewH - lockedRefHalfH
        } else if (isProportionalFrameBanner) {
            effectivePosY * viewH - (bannerH / 2f)
        } else {
            effectivePosY * viewH
        }

        val leftX = baseLeftX + (slideOffsetX * viewW)
        val rightX = leftX + bannerW
        val topY = baseTopY + (slideOffsetY * viewH)
        val bottomY = topY + bannerH

        val cornersX = floatArrayOf(leftX, rightX, leftX, rightX)
        val cornersY = floatArrayOf(bottomY, bottomY, topY, topY)

        val centerX = (leftX + rightX) / 2f
        val centerY = (topY + bottomY) / 2f
        val rotRad = Math.toRadians(banner.bannerRotation.toDouble())
        val cosR = Math.cos(rotRad).toFloat()
        val sinR = Math.sin(rotRad).toFloat()

        val coords = FloatArray(8)
        for (i in 0 until 4) {
            var vx = cornersX[i]
            var vy = cornersY[i]

            if (banner.bannerRotation != 0) {
                val dx = vx - centerX
                val dy = vy - centerY
                vx = centerX + dx * cosR - dy * sinR
                vy = centerY + dx * sinR + dy * cosR
            }

            // Individual sticker mirror flip (only when explicitly set on the sticker itself)
            // Flips in-place around its own center
            if (banner.isBannerMirrored) {
                vx = 2f * centerX - vx
            }

            val (ndcX, ndcY) = when (effectiveRotation) {
                0 -> Pair((vx / viewW) * 2f - 1f, 1f - (vy / viewH) * 2f)
                90 -> Pair(1f - (vy / viewH) * 2f, 1f - (vx / viewW) * 2f)
                180 -> Pair(1f - (vx / viewW) * 2f, (vy / viewH) * 2f - 1f)
                else -> Pair((vy / viewH) * 2f - 1f, (vx / viewW) * 2f - 1f)
            }

            coords[i * 2] = ndcX
            coords[i * 2 + 1] = ndcY
        }
        return coords
    }

    fun refreshRunningStickerBitmap(
        banner: OverlayBannerData,
        surfaceWidth: Int,
        surfaceHeight: Int,
        now: Long
    ): Bitmap {
        val viewW = minOf(surfaceWidth, surfaceHeight).coerceAtLeast(720)
        val viewH = maxOf(surfaceWidth, surfaceHeight).coerceAtLeast(1280)
        val maxW = (viewW * 0.90f).toInt().coerceIn(320, 1600)
        val density = viewH / 1920f
        val baseH = (72f * density).toInt()
        val h = baseH.coerceIn((12f * density).toInt().coerceAtLeast(10), 130)
        val dScale = (h / 74f).coerceIn(0.35f, 2.5f)
        return generateStickerBitmap(banner, dScale, maxW, now)
    }

    fun refreshAutoTimeBitmap(
        banner: OverlayBannerData,
        surfaceWidth: Int,
        surfaceHeight: Int,
        now: Long
    ): Bitmap {
        val viewW = minOf(surfaceWidth, surfaceHeight).coerceAtLeast(720)
        val viewH = maxOf(surfaceWidth, surfaceHeight).coerceAtLeast(1280)
        val maxW = (viewW * 0.90f).toInt().coerceIn(320, 1600)
        val density = viewH / 1920f
        val baseH = (72f * density).toInt()
        val h = baseH.coerceIn((12f * density).toInt().coerceAtLeast(10), 130)
        val dScale = (h / 74f).coerceIn(0.35f, 2.5f)
        return generateAutoTimeBitmap(banner, dScale, maxW, now)
    }

    fun generateAutoTimeBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        maxWidthPx: Int,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Bitmap {
        val stylePack = com.tajasb.notif.AutoTimeCapsuleManager.decodeStylePack(banner.customPrefix)
        val (dayStr, monthStr, timeStr) = com.tajasb.notif.AutoTimeCapsuleManager.formatDateAndTimeParts(
            timeMs = currentTimeMs,
            showSeconds = stylePack.showSeconds
        )

        val capsuleBgCol = try {
            Color.parseColor(banner.capsuleBgColor)
        } catch (_: Throwable) {
            Color.parseColor("#FF941A")
        }
        val outlineCol = try {
            Color.parseColor(banner.capsuleOutlineColor)
        } catch (_: Throwable) {
            Color.WHITE
        }
        val dateBoxBgCol = try {
            Color.parseColor(stylePack.dateBoxBgColorHex)
        } catch (_: Throwable) {
            Color.WHITE
        }
        val dateBoxTxtCol = try {
            Color.parseColor(stylePack.dateBoxTextColorHex)
        } catch (_: Throwable) {
            Color.parseColor("#D97724")
        }
        val timeTxtCol = try {
            Color.parseColor(stylePack.timeTextColorHex)
        } catch (_: Throwable) {
            Color.WHITE
        }

        val alphaInt = (banner.capsuleAlpha * 255).toInt().coerceIn(25, 255)
        val capsuleH = (56f * densityScale).toInt().coerceAtLeast(36)
        val inset = (5f * densityScale).coerceAtLeast(3f)
        val dateBoxH = (capsuleH - inset * 2f).coerceAtLeast(26f)
        val dateBoxW = (dateBoxH * 1.12f).coerceAtLeast(30f * densityScale)

        val dayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16.5f * densityScale
            color = dateBoxTxtCol
            textAlign = Paint.Align.CENTER
            fontFeatureSettings = "tnum"
        }

        val monthPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10.2f * densityScale
            color = dateBoxTxtCol
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.04f
        }

        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 23.5f * densityScale
            color = timeTxtCol
            fontFeatureSettings = "tnum"
            setShadowLayer(2.2f * densityScale, 0f, 1.0f * densityScale, 0x66000000)
        }

        val timeTextW = timePaint.measureText(timeStr)
        val gap = 10f * densityScale
        val padRight = (banner.capsulePaddingH.coerceIn(4, 48).toFloat()) * densityScale
        val totalW = (inset + dateBoxW + gap + timeTextW + padRight).toInt().coerceIn(84, maxWidthPx)

        val bitmap = Bitmap.createBitmap(totalW, capsuleH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val outerCorner = (banner.capsuleCornerRadius.toFloat() * densityScale).coerceIn(4f * densityScale, capsuleH / 2f)
        val outerRect = RectF(0f, 0f, totalW.toFloat(), capsuleH.toFloat())

        // 1. Capsule Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(alphaInt, Color.red(capsuleBgCol), Color.green(capsuleBgCol), Color.blue(capsuleBgCol))
        }
        canvas.drawRoundRect(outerRect, outerCorner, outerCorner, bgPaint)

        // 2. Capsule Outline (Optional)
        if (banner.capsuleOutlineWidth > 0) {
            val strokePx = (banner.capsuleOutlineWidth * densityScale).coerceAtLeast(1f)
            val halfStroke = strokePx / 2f
            val borderRect = RectF(halfStroke, halfStroke, totalW - halfStroke, capsuleH - halfStroke)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                color = Color.argb(alphaInt.coerceAtLeast(190), Color.red(outlineCol), Color.green(outlineCol), Color.blue(outlineCol))
            }
            canvas.drawRoundRect(borderRect, outerCorner, outerCorner, borderPaint)
        }

        // 3. Left Date+Month Box inside Capsule
        val dateBoxRect = RectF(inset, inset, inset + dateBoxW, inset + dateBoxH)
        val dateBoxCorner = (outerCorner - inset * 0.6f).coerceIn(4f * densityScale, dateBoxH / 2f)
        val dateBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(alphaInt.coerceAtLeast(225), Color.red(dateBoxBgCol), Color.green(dateBoxBgCol), Color.blue(dateBoxBgCol))
        }
        canvas.drawRoundRect(dateBoxRect, dateBoxCorner, dateBoxCorner, dateBoxPaint)

        val dateCenterX = dateBoxRect.centerX()
        val dayBaselineY = dateBoxRect.top + dateBoxH * 0.48f
        val monthBaselineY = dateBoxRect.top + dateBoxH * 0.86f
        canvas.drawText(dayStr, dateCenterX, dayBaselineY, dayPaint)
        canvas.drawText(monthStr, dateCenterX, monthBaselineY, monthPaint)

        // 4. Right Live Time (HH:mm or HH:mm:ss)
        val timeStartX = dateBoxRect.right + gap
        val fm = timePaint.fontMetrics
        val timeBaselineY = (capsuleH / 2f) - ((fm.ascent + fm.descent) / 2f)
        canvas.drawText(timeStr, timeStartX, timeBaselineY, timePaint)

        return bitmap
    }

    fun computeBannerSlideAndAlpha(
        banner: OverlayBannerData,
        elapsed: Long
    ): Triple<Float, Float, Float> {
        val entryDur = 350f
        val exitDur = 350f
        var slideOffsetX = 0f
        var slideOffsetY = 0f
        if (elapsed < entryDur) {
            val t = (elapsed / entryDur).coerceIn(0f, 1f)
            val ease = 1f - (1f - t) * (1f - t)
            val factor = (ease - 1f) * 0.45f
            when (banner.entryDirection.uppercase()) {
                "LEFT" -> slideOffsetX = factor
                "RIGHT" -> slideOffsetX = -factor
                "TOP" -> slideOffsetY = -factor
                "BOTTOM" -> slideOffsetY = factor
                else -> {}
            }
        }
        val alpha = when {
            elapsed > (banner.durationMs - exitDur) -> {
                val fadeT = ((banner.durationMs - elapsed) / exitDur).coerceIn(0f, 1f)
                (fadeT * fadeT * (3f - 2f * fadeT)).coerceIn(0f, 1f)
            }
            elapsed < 120L -> (elapsed / 120f).coerceIn(0f, 1f)
            else -> 1.0f
        }
        return Triple(slideOffsetX, slideOffsetY, alpha)
    }

    private fun drawCircularAvatarWithRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        b64Avatar: String,
        fallbackName: String,
        fallbackRank: Int,
        fallbackColor: Int,
        ringColor: Int,
        effDensity: Float,
        showRankBadge: Boolean = false
    ) {
        var avatarBmp: Bitmap? = null
        if (b64Avatar.isNotEmpty()) {
            try {
                val bytes = android.util.Base64.decode(b64Avatar, android.util.Base64.DEFAULT)
                if (bytes.isNotEmpty()) {
                    avatarBmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (_: Throwable) {}
        }

        if (avatarBmp != null && !avatarBmp.isRecycled && avatarBmp.width > 0 && avatarBmp.height > 0) {
            val shader = android.graphics.BitmapShader(
                avatarBmp,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP
            )
            val srcRect = RectF(0f, 0f, avatarBmp.width.toFloat(), avatarBmp.height.toFloat())
            val dstRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            val matrix = android.graphics.Matrix().apply {
                setRectToRect(srcRect, dstRect, android.graphics.Matrix.ScaleToFit.FILL)
            }
            shader.setLocalMatrix(matrix)
            val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                style = Paint.Style.FILL
                this.shader = shader
            }
            canvas.drawCircle(cx, cy, radius, imgPaint)
            avatarBmp.recycle()
        } else {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = fallbackColor
            }
            canvas.drawCircle(cx, cy, radius, fillPaint)

            val initials = fallbackName
                .split(" ", "_", ".")
                .filter { it.isNotBlank() && !it.equals("Belum", ignoreCase = true) && !it.equals("Penonton", ignoreCase = true) }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifBlank { "$fallbackRank" }
            val initPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = (radius * 0.82f).coerceAtLeast(6f)
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }
            val fm = initPaint.fontMetrics
            canvas.drawText(initials, cx, cy - (fm.ascent + fm.descent) / 2f, initPaint)
        }

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (1.8f * effDensity).coerceAtLeast(1.1f)
            color = ringColor
        }
        canvas.drawCircle(cx, cy, radius, ringPaint)

        if (showRankBadge) {
            val badgeR = (radius * 0.42f).coerceAtLeast(6.5f)
            val badgeCx = cx - radius * 0.68f
            val badgeCy = cy - radius * 0.68f
            val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#F20F172A")
            }
            canvas.drawCircle(badgeCx, badgeCy, badgeR, badgeBg)
            canvas.drawCircle(badgeCx, badgeCy, badgeR, ringPaint)

            val rankPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = badgeR * 1.15f
                color = ringColor
                textAlign = Paint.Align.CENTER
            }
            val rfm = rankPaint.fontMetrics
            canvas.drawText("$fallbackRank", badgeCx, badgeCy - (rfm.ascent + rfm.descent) / 2f, rankPaint)
        }
    }

    fun generateQuranKhatamBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        maxWidthPx: Int,
        activeDoaBanner: OverlayBannerData? = null,
        activeGiftBanner: OverlayBannerData? = null,
        activeLikeBanner: OverlayBannerData? = null,
        secondaryStatsBanner: OverlayBannerData? = null
    ): Bitmap {
        val parts = banner.content.split("|||")
        val headerText = parts.getOrNull(0) ?: "QS. Al-Fatihah [1:1]"
        val revealedArabic = parts.getOrNull(1) ?: ""
        val unrevealedArabic = parts.getOrNull(2) ?: ""
        val indoText = parts.getOrNull(3) ?: ""
        val contributor = parts.getOrNull(4) ?: "Menunggu Penonton"
        val actionDesc = parts.getOrNull(5) ?: "Tap Like / Chat Doa"
        val progressStr = parts.getOrNull(6) ?: "0/0"
        val totalLettersStr = parts.getOrNull(7) ?: "0"
        val embeddedPrayerSender = parts.getOrNull(8)?.trim() ?: ""
        val embeddedPrayerText = parts.getOrNull(9)?.trim() ?: ""

        val propScale = banner.bannerScale.coerceAtLeast(0.10f)
        val effDensity = (densityScale * propScale).coerceAtLeast(0.10f)
        val baseWidthPct = if (banner.capsulePaddingH > 0) (banner.capsulePaddingH.toFloat() / 100f).coerceAtLeast(0.10f) else 0.98f
        val effWidthPct = (baseWidthPct * propScale).coerceAtLeast(0.10f)
        val targetWidth = (maxWidthPx * effWidthPct).toInt().coerceAtLeast(100)
        val padH = (16f * effDensity).coerceAtLeast(2f)
        val padV = (banner.capsulePaddingV.toFloat() * effDensity).coerceAtLeast(2f)
        val contentW = (targetWidth - (padH * 2)).toInt().coerceAtLeast(40)

        // =====================================================================
        // OVERLAY TERPADU DISATUKAN DI ATAS OVERLAY KHATAMAN (Top 3 Koin | Analitik | Sisa Juz)
        // Ikut ter-skala secara proporsional mengikuti propScale Kartu Khatam!
        // =====================================================================
        val topDashboardBmp = generateUnifiedTopDashboardBitmap(
            banner = secondaryStatsBanner ?: banner,
            secondaryStatsBanner = banner,
            densityScale = densityScale,
            maxWidthPx = maxWidthPx,
            overrideWidthPx = targetWidth,
            overrideScale = propScale
        )
        val topDashboardH = topDashboardBmp.height
        val topDashboardGap = (5f * effDensity).toInt().coerceAtLeast(2)

        val prefixParts = banner.customPrefix.split(":::")
        val showTranslation = prefixParts.getOrNull(0) != "HIDE_TRANS"
        val latinFontSizeSp = prefixParts.getOrNull(1)?.toFloatOrNull()?.coerceAtLeast(3f) ?: 12.0f
        val showNotifUnderAyah = prefixParts.getOrNull(2) != "HIDE_NOTIF"
        val latinRatio = (latinFontSizeSp / 12.0f).coerceIn(0.50f, 2.50f)

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (11.5f * effDensity * latinRatio).coerceIn(5.5f, 36f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = Color.parseColor("#00E676")
        }
        val surahBadgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (13f * effDensity * latinRatio).coerceIn(6.5f, 40f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = Color.parseColor("#FFD54F")
        }
        val progressPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (11f * effDensity * latinRatio).coerceIn(5.5f, 34f)
            typeface = Typeface.DEFAULT
            color = Color.parseColor("#94A3B8")
        }

        val maxArabicLines = if (banner.formatMode.startsWith("MAX_LINES_")) {
            banner.formatMode.removePrefix("MAX_LINES_").toIntOrNull() ?: 0
        } else 0

        val arabicFontSize = (banner.fontSizeSp * effDensity).coerceAtLeast(4f)
        val arabicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = arabicFontSize
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textLocale = java.util.Locale("ar")
            color = Color.parseColor("#FFD700")
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }

        // Jika maxArabicLines <= 0 (Mode Auto / Fleksibel Alami), render ayat alami tanpa dipaksa ayat lain.
        // Hanya jika pengguna secara sengaja memilih target baris tetap (misal 2, 3, 4, dst.) dan ayat terlalu pendek,
        // barulah ayat sambungan dimuat agar sesuai target baris pengguna.
        var workingFullArabic = (revealedArabic + unrevealedArabic).ifBlank { "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ" }
        val revCount = revealedArabic.length.coerceIn(0, workingFullArabic.length)

        fun buildArabicStaticLayout(text: String): android.text.StaticLayout {
            val safeRev = revCount.coerceIn(0, text.length)
            val spannable = android.text.SpannableString(text).apply {
                if (safeRev > 0) {
                    setSpan(android.text.style.ForegroundColorSpan(Color.parseColor("#FFD700")), 0, safeRev, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (safeRev < text.length) {
                    setSpan(android.text.style.ForegroundColorSpan(Color.parseColor("#44E2E8F0")), safeRev, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            return android.text.StaticLayout.Builder.obtain(
                spannable, 0, spannable.length, arabicPaint, contentW
            ).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .setLineSpacing(arabicFontSize * 0.18f, 1f)
                .build()
        }

        var arabicLayout = buildArabicStaticLayout(workingFullArabic)
        if (maxArabicLines > 1 && arabicLayout.lineCount < maxArabicLines) {
            val coordMatch = Regex("""\[(\d+):(\d+)]""").find(headerText)
            var curS = coordMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            var curA = coordMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1
            val lastVerseMarker = Regex("""﴿([٠-٩\d]+)﴾""").findAll(workingFullArabic).lastOrNull()
            if (lastVerseMarker != null) {
                val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
                val rawDigits = lastVerseMarker.groupValues[1]
                val normalizedAyah = rawDigits.map { ch ->
                    val idx = arabicDigits.indexOf(ch)
                    if (idx >= 0) ('0' + idx) else ch
                }.joinToString("").toIntOrNull()
                if (normalizedAyah != null && normalizedAyah in 1..286) {
                    curA = normalizedAyah
                }
            }
            val sb = StringBuilder(workingFullArabic)
            var safety = 0
            while (arabicLayout.lineCount < maxArabicLines && safety < 16) {
                val (nextS, nextA) = com.tajasb.quran.QuranTextCatalog.getNextCuratedCoord(curS, curA)
                val nextVerse = com.tajasb.quran.QuranTextCatalog.getVerse(nextS, nextA)
                sb.append(" ").append(nextVerse.arabicText)
                    .append(" ﴿").append(com.tajasb.quran.QuranTextCatalog.toArabicDigits(nextA)).append("﴾")
                curS = nextS
                curA = nextA
                safety++
                workingFullArabic = sb.toString()
                arabicLayout = buildArabicStaticLayout(workingFullArabic)
            }
        }

        val totalArabicLines = arabicLayout.lineCount.coerceAtLeast(1)
        val isAuto = maxArabicLines <= 0
        val effectiveMaxLines = if (isAuto) totalArabicLines else maxArabicLines
        val activeCursorOffset = if (revCount > 0) (revCount - 1).coerceIn(0, workingFullArabic.length) else 0
        val activeLineIdx = arabicLayout.getLineForOffset(activeCursorOffset).coerceIn(0, totalArabicLines - 1)

        val fontMetrics = arabicPaint.fontMetrics
        val fixedSingleLineH = (fontMetrics.descent - fontMetrics.ascent + arabicFontSize * 0.18f).coerceAtLeast(arabicFontSize * 1.25f)
        val arabicH = if (isAuto) {
            (totalArabicLines * fixedSingleLineH)
        } else {
            (effectiveMaxLines * fixedSingleLineH)
        }

        // =====================================================================
        // SMOOTH SUB-LINE SCROLLING: SEBELUM BARIS TERBAWAH HABIS
        // Menggeser ayat secara kontinu dan halus (sub-line pixel) sebelum baris
        // terbawah tuntas, bebas patah-patah / lompatan kasar.
        // =====================================================================
        val scrollOffsetY = if (isAuto || totalArabicLines <= effectiveMaxLines) {
            0f
        } else {
            if (activeLineIdx < effectiveMaxLines - 1) {
                0f
            } else {
                val baseLineIdx = activeLineIdx - (effectiveMaxLines - 1)
                val baseScrollY = arabicLayout.getLineTop(baseLineIdx).toFloat()
                val nextLineIdx = (baseLineIdx + 1).coerceAtMost(totalArabicLines - 1)
                val nextScrollY = if (nextLineIdx > baseLineIdx) arabicLayout.getLineTop(nextLineIdx).toFloat() else baseScrollY
                val scrollDelta = (nextScrollY - baseScrollY).coerceAtLeast(0f)

                val lineStart = arabicLayout.getLineStart(activeLineIdx)
                val lineEnd = arabicLayout.getLineEnd(activeLineIdx)
                val lineCharCount = (lineEnd - lineStart).coerceAtLeast(1)
                val charInLine = (activeCursorOffset - lineStart).coerceIn(0, lineCharCount)

                // Mulai gulir setelah 20% karakter di baris terbawah dan tuntas sebelum 90% karakter habis
                val startProgress = 0.20f
                val endProgress = 0.90f
                val progress = ((charInLine.toFloat() / lineCharCount.toFloat() - startProgress) / (endProgress - startProgress)).coerceIn(0f, 1f)
                val smoothRatio = (1f - kotlin.math.cos(progress * Math.PI.toFloat())) / 2f
                baseScrollY + scrollDelta * smoothRatio
            }
        }

        val indoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (latinFontSizeSp * effDensity).coerceAtLeast(3f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            color = Color.parseColor("#E2E8F0")
            setShadowLayer(2f, 0f, 1f, Color.BLACK)
        }
        val maxTransLines = if (maxArabicLines in 1..3) 2 else 3
        val indoFontMetrics = indoPaint.fontMetrics
        val fixedIndoSingleLineH = (indoFontMetrics.descent - indoFontMetrics.ascent + 2f * effDensity).coerceAtLeast(latinFontSizeSp * effDensity * 1.25f)
        val fixedIndoH = if (showTranslation) (fixedIndoSingleLineH * maxTransLines) else 0f

        val indoLayout = if (showTranslation && indoText.isNotBlank()) {
            val ib = android.text.StaticLayout.Builder.obtain(
                indoText, 0, indoText.length, indoPaint, contentW
            ).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            ib.setMaxLines(maxTransLines)
            ib.setEllipsize(TextUtils.TruncateAt.END)
            ib.build()
        } else null

        // =====================================================================
        // NOTIF EVENT DI BAWAH AYAT BERBENTUK KOLOM TETAP (DIAM & TIDAK NAIK TURUN)
        // Menampilkan Kolom Doa di kiri dan Kolom Saweran/Like di kanan dengan tinggi fixed
        // sehingga kartu tidak pernah berkedut/naik-turun saat ada event baru atau event selesai.
        // Dapat dimatikan lewat saklar Notif Bawah Ayat sehingga kartu Khatam menjadi ringkas.
        // =====================================================================
        val resolvedDoaText: Pair<String, String>? = if (!showNotifUnderAyah) null else when {
            activeDoaBanner != null && activeDoaBanner.content.isNotBlank() && !activeDoaBanner.content.contains("|||") -> {
                val pfx = activeDoaBanner.customPrefix.trim()
                val sdr = activeDoaBanner.senderName.trim().ifBlank { "Penonton" }
                val label = when {
                    pfx.isNotBlank() && !sdr.equals("Penonton", ignoreCase = true) -> "$pfx • $sdr"
                    sdr.isNotBlank() -> "Doa $sdr"
                    else -> "Doa Penonton"
                }
                Pair(label, activeDoaBanner.content.trim())
            }
            embeddedPrayerText.isNotBlank() -> {
                val sdr = embeddedPrayerSender.ifBlank { "Penonton" }
                Pair("Doa $sdr", embeddedPrayerText)
            }
            else -> null
        }

        val deckFontSize = (latinFontSizeSp * 0.90f * effDensity).coerceIn(5.5f, 26f)
        val fixedDeckH = if (showNotifUnderAyah) maxOf((deckFontSize * 3.4f).toInt(), (44f * effDensity).toInt()).coerceAtLeast(32) else 0
        val deckGap = if (showNotifUnderAyah) (5f * effDensity).toInt().coerceAtLeast(2) else 0
        val footerH = if (showNotifUnderAyah) (22f * effDensity).toInt().coerceAtLeast(16) else 0

        val contributorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = deckFontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = Color.parseColor("#FDE047")
        }
        val eventRowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = deckFontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = Color.WHITE
        }

        val headerH = 26f * effDensity
        val spacing1 = 8f * effDensity
        val spacing2 = if (showNotifUnderAyah) (if (showTranslation) 10f * effDensity else 4f * effDensity) else 0f

        // Tinggi kartu Khatam SELALU TETAP (DIAM & TANPA ANIMASI / RESIZE)
        val khatamCardH = (padV * 2 + headerH + arabicH + spacing1 + fixedIndoH + spacing2 + fixedDeckH + deckGap + footerH).toInt().coerceAtLeast((44f * effDensity).toInt().coerceAtLeast(36))
        val totalH = topDashboardH + topDashboardGap + khatamCardH

        val bmp = Bitmap.createBitmap(targetWidth, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 0. Gambar Overlay Terpadu 2-Kolom (Analitik Live | Progres & Sisa Juz) tepat di atas Kartu Khatam
        canvas.drawBitmap(topDashboardBmp, 0f, 0f, null)
        topDashboardBmp.recycle()

        val khatamTopY = (topDashboardH + topDashboardGap).toFloat()
        val cardRect = RectF(0f, khatamTopY, targetWidth.toFloat(), totalH.toFloat())
        val cardCorner = 16f * effDensity

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#EB0A0E17")
        }
        canvas.drawRoundRect(cardRect, cardCorner, cardCorner, bgPaint)

        // Border emas solid tanpa animasi / kedip
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * effDensity
            color = Color.parseColor("#FFD54F")
        }
        canvas.drawRoundRect(cardRect, cardCorner, cardCorner, strokePaint)

        var curY = khatamTopY + padV
        val leftHeaderLabel = "🕌 KHATAM 30 JUZ"
        val progText = "Huruf: $progressStr • ${effectiveMaxLines} Baris"
        var headerTotalW = headerPaint.measureText(leftHeaderLabel) + surahBadgePaint.measureText(headerText) + progressPaint.measureText(progText) + 24f * effDensity
        while (headerTotalW > contentW && surahBadgePaint.textSize > 8.5f) {
            headerPaint.textSize *= 0.92f
            surahBadgePaint.textSize *= 0.92f
            progressPaint.textSize *= 0.92f
            headerTotalW = headerPaint.measureText(leftHeaderLabel) + surahBadgePaint.measureText(headerText) + progressPaint.measureText(progText) + 24f * effDensity
        }
        val headerBaseline = curY + headerH * 0.56f
        canvas.drawText(leftHeaderLabel, padH, headerBaseline, headerPaint)
        canvas.drawText(headerText, targetWidth / 2f - surahBadgePaint.measureText(headerText) / 2f, headerBaseline, surahBadgePaint)
        val progW = progressPaint.measureText(progText)
        canvas.drawText(progText, targetWidth - padH - progW, headerBaseline, progressPaint)

        curY += headerH
        val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.parseColor("#334155")
        }
        canvas.drawLine(padH, curY, targetWidth - padH, curY, sepPaint)
        curY += 6f * effDensity

        // Render teks ayat Arab dengan smooth scrolling sub-line
        canvas.save()
        canvas.clipRect(padH, curY, targetWidth - padH, curY + arabicH)
        canvas.translate(padH, curY - scrollOffsetY)
        arabicLayout.draw(canvas)
        canvas.restore()
        curY += arabicH + spacing1

        if (showTranslation) {
            if (indoLayout != null) {
                canvas.save()
                canvas.clipRect(padH, curY, targetWidth - padH, curY + fixedIndoH)
                val vOffset = ((fixedIndoH - indoLayout.height) / 2f).coerceAtLeast(0f)
                canvas.translate(padH, curY + vOffset)
                indoLayout.draw(canvas)
                canvas.restore()
            }
            curY += fixedIndoH + spacing2
        }

        if (showNotifUnderAyah) {
            // =====================================================================
            // DECK KOLOM NOTIF TETAP DI BAWAH AYAT (KOLOM DOA DI KIRI, KOLOM EVENT DI KANAN)
            // =====================================================================
            val deckColGap = 6f * effDensity
            val col1W = (contentW - deckColGap) * 0.53f
            val col2W = (contentW - deckColGap) - col1W
            val col1Rect = RectF(padH, curY, padH + col1W, curY + fixedDeckH)
            val col2Rect = RectF(padH + col1W + deckColGap, curY, targetWidth - padH, curY + fixedDeckH)
            val deckCorner = 8f * effDensity

            // Kolom 1 (Kiri): Doa & Chat Penonton
            val col1BgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#E60C2D48")
            }
            val col1BorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.3f * effDensity
                color = Color.parseColor("#38BDF8")
            }
            canvas.drawRoundRect(col1Rect, deckCorner, deckCorner, col1BgPaint)
            canvas.drawRoundRect(col1Rect, deckCorner, deckCorner, col1BorderPaint)

            val colLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = (9.5f * effDensity * latinRatio).coerceIn(5.0f, 24f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                color = Color.parseColor("#38BDF8")
            }
            val col1Title = if (resolvedDoaText != null) "🤲 ${resolvedDoaText.first}" else "🤲 DOA PENONTON"
            val clampedCol1Title = TextUtils.ellipsize(col1Title, colLabelPaint, col1W - 12f * effDensity, TextUtils.TruncateAt.END).toString()
            canvas.drawText(clampedCol1Title, col1Rect.left + 6f * effDensity, col1Rect.top + fixedDeckH * 0.38f, colLabelPaint)

            val col1Content = resolvedDoaText?.second?.ifBlank { "Ketik doa di chat live 🤲" } ?: "Ketik doa di chat live 🤲"
            val clampedCol1Content = TextUtils.ellipsize(col1Content, eventRowPaint, col1W - 12f * effDensity, TextUtils.TruncateAt.END).toString()
            canvas.drawText(clampedCol1Content, col1Rect.left + 6f * effDensity, col1Rect.top + fixedDeckH * 0.78f, eventRowPaint)

            // Kolom 2 (Kanan): Saweran Gift & Tap Like / Event
            val hasActiveGift = activeGiftBanner != null && activeGiftBanner.content.isNotBlank()
            val hasActiveLike = activeLikeBanner != null && activeLikeBanner.content.isNotBlank()

            val col2BgHex = when {
                hasActiveGift -> "#E678350F"
                hasActiveLike -> "#E6881337"
                else -> "#E61E293B"
            }
            val col2BorderHex = when {
                hasActiveGift -> "#FACC15"
                hasActiveLike -> "#FB7185"
                else -> "#475569"
            }
            val col2BgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor(col2BgHex)
            }
            val col2BorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.3f * effDensity
                color = Color.parseColor(col2BorderHex)
            }
            canvas.drawRoundRect(col2Rect, deckCorner, deckCorner, col2BgPaint)
            canvas.drawRoundRect(col2Rect, deckCorner, deckCorner, col2BorderPaint)

            val col2TitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = (9.5f * effDensity * latinRatio).coerceIn(5.0f, 24f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                color = Color.parseColor(col2BorderHex)
            }
            val col2Title = when {
                hasActiveGift -> "🎁 SAWERAN TERAKHIR"
                hasActiveLike -> "❤️ INTERAKSI TERAKHIR"
                else -> "🎁 SAWERAN & LIKE"
            }
            val clampedCol2Title = TextUtils.ellipsize(col2Title, col2TitlePaint, col2W - 12f * effDensity, TextUtils.TruncateAt.END).toString()
            canvas.drawText(clampedCol2Title, col2Rect.left + 6f * effDensity, col2Rect.top + fixedDeckH * 0.38f, col2TitlePaint)

            val col2Content = when {
                activeGiftBanner != null && activeGiftBanner.content.isNotBlank() -> {
                    val sdr = activeGiftBanner.senderName.ifBlank { "Penonton" }
                    val cleanG = activeGiftBanner.content.removePrefix("Mengirim ").trim()
                    "🎁 $sdr: $cleanG"
                }
                activeLikeBanner != null && activeLikeBanner.content.isNotBlank() -> {
                    val sdr = activeLikeBanner.senderName.ifBlank { "Penonton" }
                    val cleanL = activeLikeBanner.content.replace("Mengetuk Like ", "").trim()
                    "❤️ $sdr: $cleanL"
                }
                else -> "Saweran & Tap Love Terbuka ✨"
            }
            val clampedCol2Content = TextUtils.ellipsize(col2Content, eventRowPaint, col2W - 12f * effDensity, TextUtils.TruncateAt.END).toString()
            canvas.drawText(clampedCol2Content, col2Rect.left + 6f * effDensity, col2Rect.top + fixedDeckH * 0.78f, eventRowPaint)

            curY += fixedDeckH + deckGap

            // BARIS FOOTER TETAP (KONTRIBUTOR HURUF TERAKHIR)
            val pillRect = RectF(padH, curY, targetWidth - padH, curY + footerH)
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#331E293B")
            }
            val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.parseColor("#475569")
            }
            canvas.drawRoundRect(pillRect, 6f * effDensity, 6f * effDensity, pillPaint)
            canvas.drawRoundRect(pillRect, 6f * effDensity, 6f * effDensity, pillStroke)

            val footerText = "❤️ Huruf terakhir: $contributor • $actionDesc • Total: $totalLettersStr Huruf"
            val clampedFooter = TextUtils.ellipsize(footerText, contributorPaint, pillRect.width() - 16f * effDensity, TextUtils.TruncateAt.END).toString()
            val footerW = contributorPaint.measureText(clampedFooter)
            val footerX = (pillRect.centerX() - footerW / 2f).coerceAtLeast(pillRect.left + 8f * effDensity)
            canvas.drawText(clampedFooter, footerX, pillRect.centerY() + (contributorPaint.textSize / 3f), contributorPaint)
        }

        return bmp
    }

    /**
     * Generates the Unified 3-Column Top Dashboard Overlay (`overlay yg jadi 1, namun ada pemisah`):
     * - Left Column (`Sebelah Kiri`): Top 3 Pengirim Gift berupa akumulasi Koin (`💎`) + Foto Profil Bulat
     * - Center Column (`Analitik di Tengahnya`): Analitik Live Realtime (Penonton, Join, Like, Gift & Koin TANPA tanda `()`, Doa, Follow, Share)
     * - Right Column (`Progres Sebelah Kanan`): Progres & Sisa Juz Khatam Al-Qur'an (Surah/Juz, Sisa Huruf, Sisa Ayat, Sisa Surat, Sisa Juz)
     */
    fun generateUnifiedTopDashboardBitmap(
        banner: OverlayBannerData,
        secondaryStatsBanner: OverlayBannerData? = null,
        densityScale: Float,
        maxWidthPx: Int,
        overrideWidthPx: Int = 0,
        overrideScale: Float = 0f
    ): Bitmap {
        val eventSource = when {
            banner.bannerType == "EVENT_STATS" -> banner
            secondaryStatsBanner?.bannerType == "EVENT_STATS" -> secondaryStatsBanner
            else -> null
        }
        val quranStatsSource = when {
            banner.bannerType == "QURAN_STATS" -> banner
            secondaryStatsBanner?.bannerType == "QURAN_STATS" -> secondaryStatsBanner
            else -> null
        }
        val quranKhatamSource = when {
            banner.bannerType == "QURAN_KHATAM" -> banner
            secondaryStatsBanner?.bannerType == "QURAN_KHATAM" -> secondaryStatsBanner
            else -> null
        }

        val eventParts = eventSource?.content?.split("|||") ?: emptyList()
        val quranStatsParts = quranStatsSource?.content?.split("|||") ?: emptyList()
        val quranKhatamParts = quranKhatamSource?.content?.split("|||") ?: emptyList()

        val likes = eventParts.getOrNull(0)?.ifBlank { "0" } ?: "0"
        val gifts = eventParts.getOrNull(1)?.ifBlank { "0" } ?: "0"
        val diamonds = eventParts.getOrNull(2)?.ifBlank { "0" } ?: "0"
        val chats = eventParts.getOrNull(3)?.ifBlank { "0" } ?: "0"
        val viewers = eventParts.getOrNull(4)?.ifBlank { "0" } ?: "0"
        val joins = eventParts.getOrNull(5)?.ifBlank { "0" } ?: "0"
        val follows = eventParts.getOrNull(6)?.ifBlank { "0" } ?: "0"
        val shares = eventParts.getOrNull(7)?.ifBlank { "0" } ?: "0"

        // Ambil data progres dari QURAN_STATS (0..4), QURAN_KHATAM (10..14), atau EVENT_STATS (9..13)
        val rawSurahTitle = quranStatsParts.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: quranKhatamParts.getOrNull(10)?.takeIf { it.isNotBlank() }
            ?: eventParts.getOrNull(9)?.takeIf { it.isNotBlank() }
            ?: quranKhatamParts.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: "QS. Al-Fatihah 1:1/7 • Juz 1"
        val surahTitle = rawSurahTitle.replace("(", "").replace(")", "")
        val remLetters = quranStatsParts.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: quranKhatamParts.getOrNull(11)?.takeIf { it.isNotBlank() }
            ?: eventParts.getOrNull(10)?.takeIf { it.isNotBlank() }
            ?: "0"
        val remAyahs = quranStatsParts.getOrNull(2)?.takeIf { it.isNotBlank() }
            ?: quranKhatamParts.getOrNull(12)?.takeIf { it.isNotBlank() }
            ?: eventParts.getOrNull(11)?.takeIf { it.isNotBlank() }
            ?: "0"
        val remSurahs = quranStatsParts.getOrNull(3)?.takeIf { it.isNotBlank() }
            ?: quranKhatamParts.getOrNull(13)?.takeIf { it.isNotBlank() }
            ?: eventParts.getOrNull(12)?.takeIf { it.isNotBlank() }
            ?: "113"
        val remJuz = quranStatsParts.getOrNull(4)?.takeIf { it.isNotBlank() }
            ?: quranKhatamParts.getOrNull(14)?.takeIf { it.isNotBlank() }
            ?: eventParts.getOrNull(13)?.takeIf { it.isNotBlank() }
            ?: "29"

        val propScale = (if (overrideScale > 0f) overrideScale else banner.bannerScale).coerceIn(0.35f, 2.20f)
        val effDensity = (densityScale * propScale).coerceIn(0.20f, 3.50f)
        val totalW = if (overrideWidthPx > 0) {
            overrideWidthPx.coerceAtLeast(200)
        } else {
            val baseWidthPct = if (banner.capsulePaddingH > 0) (banner.capsulePaddingH.toFloat() / 100f).coerceIn(0.35f, 1.0f) else 0.98f
            val effWidthPct = if (propScale < 1.0f) (baseWidthPct * propScale).coerceIn(0.32f, 1.0f) else (baseWidthPct * propScale).coerceIn(0.35f, 1.0f)
            (maxWidthPx * effWidthPct).toInt().coerceIn(200, maxWidthPx.coerceAtLeast(320))
        }

        val padH = (12f * effDensity).coerceIn(6f, 32f)
        val padV = (8f * effDensity).coerceIn(3f, 24f)

        val isQuranMode = (banner.bannerType == "QURAN_KHATAM" ||
                secondaryStatsBanner?.bannerType == "QURAN_KHATAM" ||
                banner.bannerType == "QURAN_STATS" ||
                secondaryStatsBanner?.bannerType == "QURAN_STATS")

        val prefixToParse = when {
            banner.bannerType == "QURAN_KHATAM" -> banner.customPrefix
            secondaryStatsBanner?.bannerType == "QURAN_KHATAM" -> secondaryStatsBanner.customPrefix
            else -> banner.customPrefix
        }
        val parsedLatinSp = prefixToParse.split(":::").getOrNull(1)?.toFloatOrNull()
        val effectiveFontSp = if (banner.bannerType == "QURAN_KHATAM" || isQuranMode) {
            ((parsedLatinSp ?: 12.0f) * 1.05f).coerceIn(6f, 28f)
        } else banner.fontSizeSp
        var baseFontSize = (effectiveFontSp * effDensity * 0.92f).coerceIn(5.5f, 26f)

        val bmp: Bitmap
        if (isQuranMode) {
            // =========================================================================
            // FORMAT PENUH (FULL-WIDTH) PROGRES & SISA KHATAMAN 30 JUZ (KHUSUS PENONTON)
            // Kolom analitik ditutup total dari siaran dan dipindah ke floating HP host!
            // =========================================================================
            val availW = (totalW - (padH * 2f)).coerceAtLeast(80f)
            val quranHeader = "📖 $surahTitle"
            val progLine1 = "🔤 $remLetters Huruf Tersisa   •   📜 $remAyahs Ayat Tersisa"
            val progLine2 = "🕌 $remSurahs Surat Menuju Khatam   •   ✨ $remJuz Juz Menuju Khatam"

            val measurePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = baseFontSize
            }

            val minReadableFont = (6.5f * effDensity).coerceIn(4.8f, 13f)
            while (
                (measurePaint.measureText(quranHeader) > availW ||
                        measurePaint.measureText(progLine1) > availW ||
                        measurePaint.measureText(progLine2) > availW) &&
                baseFontSize > minReadableFont
            ) {
                baseFontSize *= 0.94f
                measurePaint.textSize = baseFontSize
            }

            val headerFontSize = (baseFontSize * 1.08f).coerceAtLeast(5.5f)
            val rowStepY = maxOf(baseFontSize * 1.38f, 13.5f * effDensity)
            val totalH = (padV * 2f + headerFontSize * 1.20f + rowStepY * 2.15f).toInt().coerceAtLeast((34f * effDensity).toInt().coerceAtLeast(28))

            bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val rect = RectF(0f, 0f, totalW.toFloat(), totalH.toFloat())
            val corner = 11f * effDensity

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#EB0B1322")
            }
            canvas.drawRoundRect(rect, corner, corner, bgPaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * effDensity
                color = Color.parseColor("#00E5FF")
            }
            canvas.drawRoundRect(rect, corner, corner, borderPaint)

            val headerDividerY = padV + headerFontSize * 1.18f
            val hLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.parseColor("#40475569")
            }
            canvas.drawLine(padH, headerDividerY, totalW - padH, headerDividerY, hLinePaint)

            val rightHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = headerFontSize
                color = Color.parseColor("#34D399")
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = baseFontSize
                color = Color.WHITE
            }
            val goldBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = baseFontSize
                color = Color.parseColor("#FDE047")
            }

            fun drawCenteredLine(text: String, baselineY: Float, paint: TextPaint) {
                val clamped = TextUtils.ellipsize(text, paint, availW, TextUtils.TruncateAt.END).toString()
                val w = paint.measureText(clamped)
                val x = ((totalW - w) / 2f).coerceAtLeast(padH)
                canvas.drawText(clamped, x, baselineY, paint)
            }

            val headerY = padV + headerFontSize * 0.90f
            drawCenteredLine(quranHeader, headerY, rightHeaderPaint)

            val row1Y = headerDividerY + rowStepY * 0.90f
            val row2Y = row1Y + rowStepY
            drawCenteredLine(progLine1, row1Y, goldBodyPaint)
            drawCenteredLine(progLine2, row2Y, bodyPaint)
        } else {
            // =========================================================================
            // FORMAT STANDALONE EVENT STATS (JIKA DIAKTIFKAN OLEH SAKLAR KAMERA MANUAL)
            // =========================================================================
            val availW = (totalW - (padH * 2f)).coerceAtLeast(80f)
            val showDiamonds = when {
                banner.customPrefix.contains("SHOW_DIAMONDS") -> true
                banner.customPrefix.contains("HIDE_DIAMONDS") -> false
                secondaryStatsBanner?.customPrefix?.contains("SHOW_DIAMONDS") == true -> true
                secondaryStatsBanner?.customPrefix?.contains("HIDE_DIAMONDS") == true -> false
                else -> false
            }

            val statHeader = "⚡ ANALITIK LIVE • 👥 $viewers Penonton"
            val statLine1 = "❤️ $likes Suka   •   💬 $chats Doa"
            val statLine2 = if (showDiamonds) "🎁 $gifts Hadiah   •   💎 $diamonds Koin" else "🎁 $gifts Hadiah"
            val statLine3 = "👋 $joins Masuk   •   👤 $follows Pengikut   •   🔄 $shares Dibagikan"

            val measurePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = baseFontSize
            }

            val minReadableFont = (6.5f * effDensity).coerceIn(4.5f, 12f)
            while (
                (measurePaint.measureText(statHeader) > availW ||
                        measurePaint.measureText(statLine1) > availW ||
                        measurePaint.measureText(statLine2) > availW ||
                        measurePaint.measureText(statLine3) > availW) &&
                baseFontSize > minReadableFont
            ) {
                baseFontSize *= 0.93f
                measurePaint.textSize = baseFontSize
            }

            val headerFontSize = (baseFontSize * 1.05f).coerceAtLeast(5.0f)
            val rowStepY = maxOf(baseFontSize * 1.38f, 13.5f * effDensity)
            val totalH = (padV * 2f + headerFontSize * 1.18f + rowStepY * 3.10f).toInt().coerceAtLeast((36f * effDensity).toInt().coerceAtLeast(28))

            bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val rect = RectF(0f, 0f, totalW.toFloat(), totalH.toFloat())
            val corner = 11f * effDensity

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#EB0B1322")
            }
            canvas.drawRoundRect(rect, corner, corner, bgPaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * effDensity
                color = Color.parseColor("#00E5FF")
            }
            canvas.drawRoundRect(rect, corner, corner, borderPaint)

            val headerDividerY = padV + headerFontSize * 1.18f
            val hLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.parseColor("#40475569")
            }
            canvas.drawLine(padH, headerDividerY, totalW - padH, headerDividerY, hLinePaint)

            val statHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = headerFontSize
                color = Color.parseColor("#00E5FF")
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = baseFontSize
                color = Color.WHITE
            }
            val goldBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = baseFontSize
                color = Color.parseColor("#FDE047")
            }

            fun drawCenteredLine(text: String, baselineY: Float, paint: TextPaint) {
                val clamped = TextUtils.ellipsize(text, paint, availW, TextUtils.TruncateAt.END).toString()
                val w = paint.measureText(clamped)
                val x = ((totalW - w) / 2f).coerceAtLeast(padH)
                canvas.drawText(clamped, x, baselineY, paint)
            }

            val headerY = padV + headerFontSize * 0.90f
            drawCenteredLine(statHeader, headerY, statHeaderPaint)

            val row1Y = headerDividerY + rowStepY * 0.88f
            val row2Y = row1Y + rowStepY
            val row3Y = row2Y + rowStepY
            drawCenteredLine(statLine1, row1Y, bodyPaint)
            drawCenteredLine(statLine2, row2Y, goldBodyPaint)
            drawCenteredLine(statLine3, row3Y, bodyPaint)
        }

        return bmp
    }

    /**
     * Generates the 4-Category Quran Khatam Contributor Leaderboard Overlay (`TOP_LIKERS`):
     * 1. Top 3 Penulis Al-Qur'an (Juz / Khatam yang diselesaikan)
     * 2. Top 3 Penulis Surat (Surat yang diselesaikan)
     * 3. Top 3 Penulis Ayat (Ayat yang diselesaikan)
     * 4. Top 3 Penulis Huruf (Huruf yang ditulis)
     *
     * Features:
     * - Tampil Bersamaan (Simultan): All active categories display together at the same time without timer rotation.
     * - Dual Orientations:
     *   - HORIZONTAL: Active categories rendered side-by-side as distinct vertical columns (1..4 columns).
     *   - VERTICAL: Active categories stacked vertically as distinct horizontal blocks (1..4 rows).
     * - Real data tracking with profile photo (or fallback avatar ring), rank badge (1 Emas, 2 Perak, 3 Perunggu),
     *   viewer name, and clear achievement score.
     */
    fun generateTopLikersBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        maxWidthPx: Int,
        interSemiBoldTypeface: Typeface
    ): Bitmap {
        data class KhatamSlot(val rank: Int, val name: String, val score: String, val b64Avatar: String)
        data class CategoryDef(
            val key: String,
            val icon: String,
            val title: String,
            val headerColor: Int,
            val scoreColor: Int,
            val defaultUnit: String
        )

        val prefixParts = banner.customPrefix.split(":::")
        val isVertical = prefixParts.getOrNull(0).equals("VERTICAL", ignoreCase = true)
        val latinFontSizeSp = prefixParts.getOrNull(3)?.toFloatOrNull()?.coerceAtLeast(3f) ?: 11.5f

        val propScale = banner.bannerScale.coerceAtLeast(0.10f)
        val effDensity = (densityScale * propScale).coerceAtLeast(0.10f)

        // Parse 4 categories from payload:
        // Format: "QURAN:::item1;;item2;;item3|||SURAH:::item1;;item2...|||AYAH:::...|||HURUF:::..."
        val tokens = banner.content.split("|||")
        val rawByKey = mutableMapOf<String, String>()
        for (token in tokens) {
            val trimmed = token.trim()
            when {
                trimmed.startsWith("QURAN:::") -> rawByKey["QURAN"] = trimmed.removePrefix("QURAN:::")
                trimmed.startsWith("SURAH:::") -> rawByKey["SURAH"] = trimmed.removePrefix("SURAH:::")
                trimmed.startsWith("AYAH:::") -> rawByKey["AYAH"] = trimmed.removePrefix("AYAH:::")
                trimmed.startsWith("HURUF:::") -> rawByKey["HURUF"] = trimmed.removePrefix("HURUF:::")
                else -> {
                    // Fallback for legacy payloads
                    if (!rawByKey.containsKey("QURAN") && trimmed.isNotBlank()) {
                        rawByKey["QURAN"] = trimmed
                    } else if (!rawByKey.containsKey("HURUF") && trimmed.isNotBlank()) {
                        rawByKey["HURUF"] = trimmed
                    }
                }
            }
        }

        val allCategoryDefs = listOf(
            CategoryDef("QURAN", "🕌", "AL-QUR'AN", Color.parseColor("#10B981"), Color.parseColor("#FACC15"), "Juz"),
            CategoryDef("SURAH", "📖", "SURAT", Color.parseColor("#38BDF8"), Color.parseColor("#93C5FD"), "Surat"),
            CategoryDef("AYAH", "📜", "AYAT", Color.parseColor("#A78BFA"), Color.parseColor("#C4B5FD"), "Ayat"),
            CategoryDef("HURUF", "🔤", "HURUF", Color.parseColor("#FB7185"), Color.parseColor("#FDA4AF"), "Huruf")
        )

        // Only include categories that are present in the payload (or default all if none)
        val activeDefs = if (rawByKey.isNotEmpty()) {
            allCategoryDefs.filter { rawByKey.containsKey(it.key) }
        } else {
            allCategoryDefs
        }.ifEmpty { allCategoryDefs }

        data class ResolvedCategory(val def: CategoryDef, val slots: List<KhatamSlot>)

        val resolvedCategories = activeDefs.map { catDef ->
            val raw = rawByKey[catDef.key].orEmpty()
            val parsed = raw.split(";;")
                .mapNotNull { token ->
                    val trimmed = token.trim()
                    if (trimmed.isBlank()) null
                    else {
                        val parts = trimmed.split("~")
                        val name = parts.getOrNull(0)?.trim().orEmpty()
                        val score = parts.getOrNull(1)?.trim().orEmpty()
                        val b64 = parts.getOrNull(2)?.trim().orEmpty()
                        if (name.isNotBlank()) Triple(name, score, b64) else null
                    }
                }
                .take(3)

            val slots = (0 until 3).map { idx ->
                val item = parsed.getOrNull(idx)
                if (item != null) {
                    val scoreStr = if (item.second.isNotBlank()) item.second else "0 ${catDef.defaultUnit}"
                    KhatamSlot(idx + 1, item.first, scoreStr, item.third)
                } else {
                    KhatamSlot(idx + 1, "Belum ada", "- ${catDef.defaultUnit}", "")
                }
            }
            ResolvedCategory(catDef, slots)
        }

        val numCats = resolvedCategories.size.coerceIn(1, 4)

        val fallbackColors = intArrayOf(
            Color.parseColor("#E11D48"),
            Color.parseColor("#0284C7"),
            Color.parseColor("#059669"),
            Color.parseColor("#7C3AED"),
            Color.parseColor("#D97706")
        )
        val rankColors = intArrayOf(
            Color.parseColor("#FFD700"), // Emas (Rank 1)
            Color.parseColor("#E2E8F0"), // Perak (Rank 2)
            Color.parseColor("#F59E0B")  // Perunggu (Rank 3)
        )

        // Total width calculation
        val totalW = if (banner.capsulePaddingH > 0) {
            val baseWidthPct = (banner.capsulePaddingH.toFloat() / 100f).coerceAtLeast(0.15f)
            (maxWidthPx * baseWidthPct * propScale).toInt().coerceAtLeast(100)
        } else if (isVertical) {
            (maxWidthPx * 0.96f * propScale).toInt().coerceAtLeast(180)
        } else {
            val effWidthPct = if (propScale < 1.0f) (0.98f * propScale).coerceIn(0.35f, 1.0f) else 0.98f
            (maxWidthPx * effWidthPct).toInt().coerceAtLeast(200)
        }

        val padH = (10f * effDensity).coerceAtLeast(2f)
        val padV = (if (banner.capsulePaddingV > 0) banner.capsulePaddingV.toFloat() else 8f) * effDensity.coerceAtLeast(1f)
        val baseFontSize = (banner.fontSizeSp * effDensity * 0.85f).coerceAtLeast(3.5f)
        val latinFontSize = (latinFontSizeSp * effDensity).coerceAtLeast(3.5f)
        val headerFontSize = (baseFontSize * 1.05f).coerceAtLeast(4.5f)

        val mainHeaderH = headerFontSize * 1.35f
        val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.parseColor("#4038BDF8")
        }

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = interSemiBoldTypeface
            textSize = latinFontSize
            color = Color.WHITE
        }

        val bmp: Bitmap

        if (!isVertical) {
            // =========================================================================
            // ↔️ FORMAT HORIZONTAL: KATEGORI DITAMPILKAN DALAM KOLOM BERDAMPINGAN (1..4 Kolom)
            // =========================================================================
            val colW = (totalW - padH * 2f) / numCats.toFloat()
            val catHeaderH = headerFontSize * 1.30f
            // Adapt avatar size and text size if 4 columns are active to prevent clipping
            val avatarRadius = (minOf(colW * (if (numCats >= 4) 0.13f else 0.16f), 18f * effDensity)).coerceIn(5f, 26f)
            val rankRowH = maxOf(avatarRadius * 2f + 6f * effDensity, (namePaint.textSize + baseFontSize * 1.05f) + 6f * effDensity).coerceAtLeast(22f * effDensity)
            val listH = rankRowH * 3f
            val totalH = (padV * 2f + mainHeaderH + 4f * effDensity + catHeaderH + listH + 6f * effDensity).toInt().coerceAtLeast(120)

            bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Card Background & Border
            val cardRect = RectF(0f, 0f, totalW.toFloat(), totalH.toFloat())
            val corner = 12f * effDensity
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#EB0F172A")
            }
            canvas.drawRoundRect(cardRect, corner, corner, bgPaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * effDensity
                color = Color.parseColor("#38BDF8")
            }
            canvas.drawRoundRect(cardRect, corner, corner, borderPaint)

            // Main Header (Top)
            val mainHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = headerFontSize
                color = Color.parseColor("#FACC15")
            }
            val mainTitle = "🏆 TOP 3 PENULIS KHATAM AL-QUR'AN"
            val clampedMainHeader = TextUtils.ellipsize(mainTitle, mainHeaderPaint, totalW - padH * 2f, TextUtils.TruncateAt.END).toString()
            val mhw = mainHeaderPaint.measureText(clampedMainHeader)
            canvas.drawText(clampedMainHeader, (totalW - mhw) / 2f, padV + headerFontSize * 0.90f, mainHeaderPaint)

            val headerDividerY = padV + mainHeaderH
            canvas.drawLine(padH, headerDividerY, totalW - padH, headerDividerY, sepPaint)

            val colsTopY = headerDividerY + 4f * effDensity

            for (c in 0 until numCats) {
                val cat = resolvedCategories[c]
                val colLeft = padH + c * colW
                val colRight = colLeft + colW
                val colCenterX = (colLeft + colRight) / 2f

                // Column Header
                val catTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textSize = (headerFontSize * (if (numCats >= 4) 0.85f else 0.92f)).coerceAtLeast(3.8f)
                    color = cat.def.headerColor
                    textAlign = Paint.Align.CENTER
                }
                val colTitle = "${cat.def.icon} ${cat.def.title}"
                val clampedColTitle = TextUtils.ellipsize(colTitle, catTitlePaint, colW - 4f * effDensity, TextUtils.TruncateAt.END).toString()
                canvas.drawText(clampedColTitle, colCenterX, colsTopY + catHeaderH * 0.70f, catTitlePaint)

                // 3 Ranks in this column
                val catRowsStartTop = colsTopY + catHeaderH
                val colScoreSize = if (numCats >= 4) (baseFontSize * 0.78f).coerceAtLeast(3.0f) else (baseFontSize * 0.85f).coerceAtLeast(3.2f)
                val scorePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textSize = colScoreSize
                    color = cat.def.scoreColor
                }

                for (r in 0 until 3) {
                    val slot = cat.slots[r]
                    val rowTop = catRowsStartTop + r * rankRowH
                    val cy = rowTop + rankRowH / 2f
                    val ringColor = rankColors[r]

                    val cx = colLeft + 3f * effDensity + avatarRadius
                    drawCircularAvatarWithRing(
                        canvas = canvas,
                        cx = cx,
                        cy = cy,
                        radius = avatarRadius,
                        b64Avatar = slot.b64Avatar,
                        fallbackName = slot.name,
                        fallbackRank = slot.rank,
                        fallbackColor = fallbackColors[r % fallbackColors.size],
                        ringColor = ringColor,
                        effDensity = effDensity,
                        showRankBadge = true
                    )

                    val textLeft = cx + avatarRadius + 3f * effDensity
                    val textAvailW = (colRight - textLeft - 2f * effDensity).coerceAtLeast(16f)

                    val cleanName = TextUtils.ellipsize(slot.name, namePaint, textAvailW, TextUtils.TruncateAt.END).toString()
                    val nameY = cy - 1f * effDensity
                    canvas.drawText(cleanName, textLeft, nameY, namePaint)

                    val cleanScore = TextUtils.ellipsize(slot.score, scorePaint, textAvailW, TextUtils.TruncateAt.END).toString()
                    val scoreY = cy + scorePaint.textSize * 1.10f
                    canvas.drawText(cleanScore, textLeft, scoreY, scorePaint)
                }

                // Vertical column separator (if not last column)
                if (c < numCats - 1) {
                    canvas.drawLine(colRight, colsTopY + 2f * effDensity, colRight, colsTopY + catHeaderH + listH, sepPaint)
                }
            }
        } else {
            // =========================================================================
            // ↕️ FORMAT VERTIKAL: KATEGORI DITAMPILKAN BERTINGKAT (1..4 Baris Kategori)
            // Setiap baris kategori menampilkan Top 3 penulisnya berdampingan secara rapi!
            // =========================================================================
            val catHeaderH = headerFontSize * 1.25f
            val rankColW = (totalW - padH * 2f) / 3f
            val avatarRadius = (minOf(rankColW * 0.18f, 20f * effDensity)).coerceIn(6f, 28f)
            val ranksRowH = (avatarRadius * 2f + namePaint.textSize + baseFontSize * 1.4f + 10f * effDensity).coerceAtLeast(42f * effDensity)
            val sectionH = catHeaderH + ranksRowH + 6f * effDensity
            val totalH = (padV * 2f + mainHeaderH + 4f * effDensity + sectionH * numCats).toInt().coerceAtLeast(140)

            bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Card Background & Border
            val cardRect = RectF(0f, 0f, totalW.toFloat(), totalH.toFloat())
            val corner = 12f * effDensity
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#EB0F172A")
            }
            canvas.drawRoundRect(cardRect, corner, corner, bgPaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * effDensity
                color = Color.parseColor("#38BDF8")
            }
            canvas.drawRoundRect(cardRect, corner, corner, borderPaint)

            // Main Header (Top)
            val mainHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = headerFontSize
                color = Color.parseColor("#FACC15")
            }
            val mainTitle = "🏆 TOP 3 PENULIS KHATAM AL-QUR'AN"
            val clampedMainHeader = TextUtils.ellipsize(mainTitle, mainHeaderPaint, totalW - padH * 2f, TextUtils.TruncateAt.END).toString()
            val mhw = mainHeaderPaint.measureText(clampedMainHeader)
            canvas.drawText(clampedMainHeader, (totalW - mhw) / 2f, padV + headerFontSize * 0.90f, mainHeaderPaint)

            val headerDividerY = padV + mainHeaderH
            canvas.drawLine(padH, headerDividerY, totalW - padH, headerDividerY, sepPaint)

            var curSectionY = headerDividerY + 4f * effDensity

            for (c in 0 until numCats) {
                val cat = resolvedCategories[c]

                // Category Section Header
                val catTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textSize = (headerFontSize * 0.95f).coerceAtLeast(4.0f)
                    color = cat.def.headerColor
                }
                val secTitle = "${cat.def.icon} TOP 3 PENULIS ${cat.def.title}"
                val clampedSecTitle = TextUtils.ellipsize(secTitle, catTitlePaint, totalW - padH * 2f, TextUtils.TruncateAt.END).toString()
                canvas.drawText(clampedSecTitle, padH + 4f * effDensity, curSectionY + catHeaderH * 0.70f, catTitlePaint)

                // 3 Rank columns in this section
                val rankColsTopY = curSectionY + catHeaderH
                val scorePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textSize = (baseFontSize * 0.88f).coerceAtLeast(3.2f)
                    color = cat.def.scoreColor
                }

                for (r in 0 until 3) {
                    val slot = cat.slots[r]
                    val colLeft = padH + r * rankColW
                    val cx = colLeft + rankColW / 2f
                    val cy = rankColsTopY + 3f * effDensity + avatarRadius
                    val ringColor = rankColors[r]

                    drawCircularAvatarWithRing(
                        canvas = canvas,
                        cx = cx,
                        cy = cy,
                        radius = avatarRadius,
                        b64Avatar = slot.b64Avatar,
                        fallbackName = slot.name,
                        fallbackRank = slot.rank,
                        fallbackColor = fallbackColors[r % fallbackColors.size],
                        ringColor = ringColor,
                        effDensity = effDensity,
                        showRankBadge = true
                    )

                    val maxItemW = (rankColW - 6f * effDensity).coerceAtLeast(36f)
                    val cleanName = TextUtils.ellipsize(slot.name, namePaint, maxItemW, TextUtils.TruncateAt.END).toString()
                    val nW = namePaint.measureText(cleanName)
                    val nameY = cy + avatarRadius + namePaint.textSize * 1.15f
                    canvas.drawText(cleanName, cx - nW / 2f, nameY, namePaint)

                    val cleanScore = TextUtils.ellipsize(slot.score, scorePaint, maxItemW, TextUtils.TruncateAt.END).toString()
                    val sW = scorePaint.measureText(cleanScore)
                    val scoreY = nameY + scorePaint.textSize * 1.25f
                    canvas.drawText(cleanScore, cx - sW / 2f, scoreY, scorePaint)
                }

                curSectionY += sectionH

                // Horizontal separator between category sections (if not last)
                if (c < numCats - 1) {
                    canvas.drawLine(padH, curSectionY, totalW - padH, curSectionY, sepPaint)
                }
            }
        }

        return bmp
    }

    fun generateEventLikeBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        maxWidthPx: Int,
        interSemiBoldTypeface: Typeface
    ): Bitmap {
        val propScale = banner.bannerScale.coerceIn(0.55f, 1.85f)
        val effDensity = densityScale * propScale
        val fontSize = (banner.fontSizeSp * effDensity).coerceIn(11f, 34f)
        val padH = (16f * effDensity).coerceIn(10f, 28f)
        val heightPx = (fontSize * 2.3f).toInt().coerceIn(40, 140)

        val effWidthPct = if (propScale < 1.0f) propScale.coerceIn(0.55f, 1.0f) else 1.0f
        val totalW = (maxWidthPx * effWidthPct).toInt().coerceIn(220, maxWidthPx.coerceAtLeast(220))

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = interSemiBoldTypeface
            textSize = fontSize
            color = Color.WHITE
        }
        val senderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = fontSize
            color = Color.parseColor("#FDE047")
        }

        val isJoin = banner.iconMode.equals("MEMBER", ignoreCase = true) || banner.customPrefix.equals("Join", ignoreCase = true) || banner.content.contains("Masuk", ignoreCase = true)
        val isFollow = banner.iconMode.equals("FOLLOW", ignoreCase = true) || banner.customPrefix.equals("Follow", ignoreCase = true) || banner.content.contains("Mengikuti", ignoreCase = true)
        val isShare = banner.iconMode.equals("SHARE", ignoreCase = true) || banner.customPrefix.equals("Share", ignoreCase = true) || banner.content.contains("Membagikan", ignoreCase = true)

        val (prefix, bgHex, borderHex) = when {
            isJoin -> Triple("👋 ", "#E60284C7", "#38BDF8") // Sky Blue / Cyan
            isFollow -> Triple("👤 ", "#E67C3AED", "#C084FC") // Violet / Purple
            isShare -> Triple("🔄 ", "#E60D9488", "#2DD4BF") // Emerald / Teal
            else -> Triple("❤️ ", "#E6BE123C", "#FDA4AF") // Rose / Crimson (Like)
        }

        val name = if (banner.senderName.isNotBlank() && !banner.senderName.equals("Penonton", ignoreCase = true)) "${banner.senderName}: " else ""
        val rawContent = when {
            isJoin -> "Masuk"
            isFollow -> "Follow"
            isShare -> "Share"
            else -> banner.content.replace("Mengetuk Like ", "").ifBlank { "+1 Like" }
        }

        val prefixW = textPaint.measureText(prefix)
        val nameW = senderPaint.measureText(name)
        val availContentW = (totalW - padH * 2f - prefixW - nameW).coerceAtLeast(60f)
        val content = TextUtils.ellipsize(rawContent, textPaint, availContentW, TextUtils.TruncateAt.END).toString()
        val contentW = textPaint.measureText(content)

        val bmp = Bitmap.createBitmap(totalW, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val rect = RectF(0f, 0f, totalW.toFloat(), heightPx.toFloat())
        val corner = (12f * effDensity).coerceAtMost(heightPx / 2f)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor(bgHex)
        }
        canvas.drawRoundRect(rect, corner, corner, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * effDensity
            color = Color.parseColor(borderHex)
        }
        canvas.drawRoundRect(rect, corner, corner, borderPaint)

        val fm = textPaint.fontMetrics
        val baseline = heightPx / 2f - (fm.descent + fm.ascent) / 2f
        val combinedW = prefixW + nameW + contentW
        var curX = ((totalW - combinedW) / 2f).coerceAtLeast(padH)
        canvas.drawText(prefix, curX, baseline, textPaint)
        curX += prefixW
        if (name.isNotEmpty()) {
            canvas.drawText(name, curX, baseline, senderPaint)
            curX += nameW
        }
        canvas.drawText(content, curX, baseline, textPaint)

        return bmp
    }

    fun generateEventGiftBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        maxWidthPx: Int,
        interSemiBoldTypeface: Typeface
    ): Bitmap {
        val propScale = banner.bannerScale.coerceIn(0.55f, 1.85f)
        val effDensity = densityScale * propScale
        val fontSize = (banner.fontSizeSp * effDensity).coerceIn(12f, 36f)
        val padH = (16f * effDensity).coerceIn(10f, 30f)
        val heightPx = (fontSize * 2.35f).toInt().coerceIn(44, 150)

        val effWidthPct = if (propScale < 1.0f) propScale.coerceIn(0.55f, 1.0f) else 1.0f
        val totalW = (maxWidthPx * effWidthPct).toInt().coerceIn(240, maxWidthPx.coerceAtLeast(240))

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = interSemiBoldTypeface
            textSize = fontSize
            color = Color.WHITE
        }
        val senderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = fontSize
            color = Color.parseColor("#FDE047")
        }

        val prefix = "🎁 "
        val name = if (banner.senderName.isNotBlank() && !banner.senderName.equals("Penonton", ignoreCase = true)) "${banner.senderName}: " else ""
        val rawContent = banner.content.removePrefix("Mengirim ").trim().ifBlank { "Hadiah" }

        val prefixW = textPaint.measureText(prefix)
        val nameW = senderPaint.measureText(name)
        val availContentW = (totalW - padH * 2f - prefixW - nameW).coerceAtLeast(60f)
        val content = TextUtils.ellipsize(rawContent, textPaint, availContentW, TextUtils.TruncateAt.END).toString()
        val contentW = textPaint.measureText(content)

        val bmp = Bitmap.createBitmap(totalW, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val rect = RectF(0f, 0f, totalW.toFloat(), heightPx.toFloat())
        val corner = (12f * effDensity).coerceAtMost(heightPx / 2f)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#E6D97706") // Amber / Golden
        }
        canvas.drawRoundRect(rect, corner, corner, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * effDensity
            color = Color.parseColor("#FDE047")
        }
        canvas.drawRoundRect(rect, corner, corner, borderPaint)

        val fm = textPaint.fontMetrics
        val baseline = heightPx / 2f - (fm.descent + fm.ascent) / 2f
        val combinedW = prefixW + nameW + contentW
        var curX = ((totalW - combinedW) / 2f).coerceAtLeast(padH)
        canvas.drawText(prefix, curX, baseline, textPaint)
        curX += prefixW
        if (name.isNotEmpty()) {
            canvas.drawText(name, curX, baseline, senderPaint)
            curX += nameW
        }
        canvas.drawText(content, curX, baseline, textPaint)

        return bmp
    }

    /**
     * Generates a circular floating viewer avatar bitmap with:
     * - Circular-cropped viewer photo (or vibrant gradient initial circle fallback)
     * - Event-colored glowing ring (Gold for Gift, Sky Blue for Chat, Rose for Like, Emerald for Join, Purple for Follow/Share)
     * - Event badge icon at top-right of the circle
     * - Compact dark rounded name pill underneath with the viewer's short name
     */
    fun generateAvatarBubbleBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        interSemiBoldTypeface: Typeface
    ): Bitmap {
        val parts = banner.content.split("|||")
        val eventTypeStr = (parts.getOrNull(0) ?: banner.iconMode).uppercase()
        val rawName = (parts.getOrNull(1) ?: banner.senderName).trim().ifBlank { "Penonton" }
        val b64Avatar = (parts.getOrNull(2) ?: "").trim()

        val isGiftEvent = (eventTypeStr == "GIFT" || banner.isGift)
        val rawGiftMeta = (parts.getOrNull(4) ?: "").trim()

        val propScale = banner.bannerScale.coerceIn(0.20f, 4.0f)
        val effDensity = (densityScale * propScale).coerceIn(0.20f, 5.0f)

        val circleRadius = (40f * effDensity).coerceAtLeast(24f)
        val ringStroke = (3.4f * effDensity).coerceAtLeast(2f)
        // Extra aura margin for radiant gift glow
        val glowMargin = if (isGiftEvent) (16f * effDensity).coerceAtLeast(10f) else (6f * effDensity).coerceAtLeast(4f)

        val nameFontSize = (12.5f * effDensity).coerceIn(9f, 24f)
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = interSemiBoldTypeface
            textSize = nameFontSize
            color = Color.WHITE
        }

        val cleanShortName = if (rawName.length > 12) rawName.take(11).trim() + "…" else rawName
        val nameTextW = namePaint.measureText(cleanShortName)
        val pillPadH = 10f * effDensity
        val pillH = (nameFontSize * 1.65f).coerceAtLeast(18f * effDensity)
        val pillW = (nameTextW + pillPadH * 2f).coerceAtLeast(circleRadius * 1.65f)

        // For GIFT avatar: add extra height at the bottom for the glittering star trail (ekor bintang kemilau)
        val starTrailHeight = if (isGiftEvent) (52f * effDensity).coerceAtLeast(36f) else 0f
        val circleDiameter = (circleRadius + ringStroke + glowMargin) * 2f
        val totalW = maxOf(circleDiameter, pillW + 16f * effDensity).toInt().coerceAtLeast(64)
        val overlapY = 8f * effDensity
        val totalH = (circleDiameter + pillH - overlapY + 4f * effDensity + starTrailHeight).toInt().coerceAtLeast(72)

        val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cx = totalW / 2f
        val cy = glowMargin + ringStroke + circleRadius

        val (ringColor, badgeBgColor, badgeEmoji) = when {
            isGiftEvent -> Triple(Color.parseColor("#FFD700"), Color.parseColor("#D97706"), "🎁")
            eventTypeStr == "CHAT" -> Triple(Color.parseColor("#38BDF8"), Color.parseColor("#0284C7"), "💬")
            eventTypeStr == "LIKE" -> Triple(Color.parseColor("#FB7185"), Color.parseColor("#E11D48"), "❤️")
            eventTypeStr in listOf("MEMBER", "JOIN") -> Triple(Color.parseColor("#34D399"), Color.parseColor("#059669"), "👋")
            eventTypeStr == "FOLLOW" -> Triple(Color.parseColor("#C084FC"), Color.parseColor("#7C3AED"), "➕")
            eventTypeStr == "SHARE" -> Triple(Color.parseColor("#FBBF24"), Color.parseColor("#B45309"), "🔄")
            else -> Triple(Color.parseColor("#38BDF8"), Color.parseColor("#0284C7"), "✨")
        }

        // Helper function to draw sparkling 4-point stars with inner diamond glow
        fun drawGlitterStar(scx: Float, scy: Float, starR: Float, starColor: Int, alphaInt: Int) {
            val starPath = Path().apply {
                val outer = starR
                val inner = starR * 0.22f
                moveTo(scx, scy - outer)
                quadTo(scx, scy, scx + outer, scy)
                quadTo(scx, scy, scx, scy + outer)
                quadTo(scx, scy, scx - outer, scy)
                quadTo(scx, scy, scx, scy - outer)
                close()
            }
            val spPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(alphaInt, Color.red(starColor), Color.green(starColor), Color.blue(starColor))
            }
            canvas.drawPath(starPath, spPaint)

            // Inner bright core
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(alphaInt.coerceAtMost(255), 255, 255, 255)
            }
            canvas.drawCircle(scx, scy, starR * 0.28f, corePaint)
        }

        // 1. AURA ANIMASI (KHUSUS PENGIRIM GIFT): Radiant multi-tier golden/amber aura
        if (isGiftEvent) {
            // Tier 1: Wide warm amber radiant glow
            val auraOuterR = circleRadius + ringStroke + glowMargin * 1.05f
            val auraShader1 = RadialGradient(
                cx, cy, auraOuterR,
                intArrayOf(
                    Color.argb(140, 255, 215, 0),   // Gold
                    Color.argb(85, 251, 146, 60),   // Coral Orange
                    Color.argb(35, 217, 119, 6),    // Amber
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.0f, 0.50f, 0.82f, 1.0f),
                Shader.TileMode.CLAMP
            )
            val auraPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = auraShader1
            }
            canvas.drawCircle(cx, cy, auraOuterR, auraPaint1)

            // Tier 2: Pulsing corona ray sparks around the avatar rim
            val rayCount = 12
            val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (2.2f * effDensity).coerceAtLeast(1.5f)
                strokeCap = Paint.Cap.ROUND
                color = Color.argb(160, 255, 236, 179)
            }
            for (i in 0 until rayCount) {
                val angle = (i * (360.0 / rayCount) + 15.0) * (Math.PI / 180.0)
                val innerR = circleRadius + ringStroke + (1.5f * effDensity)
                val rayLen = if (i % 2 == 0) (8.5f * effDensity) else (4.5f * effDensity)
                val outerR = innerR + rayLen
                val x1 = cx + (innerR * kotlin.math.cos(angle)).toFloat()
                val y1 = cy + (innerR * kotlin.math.sin(angle)).toFloat()
                val x2 = cx + (outerR * kotlin.math.cos(angle)).toFloat()
                val y2 = cy + (outerR * kotlin.math.sin(angle)).toFloat()
                canvas.drawLine(x1, y1, x2, y2, rayPaint)
            }

            // Tier 3: Golden crown shimmer at the top of the avatar
            drawGlitterStar(cx, cy - circleRadius - ringStroke - (4.5f * effDensity), (7f * effDensity).coerceAtLeast(4.5f), Color.parseColor("#FFFBEB"), 255)
            drawGlitterStar(cx - circleRadius * 0.65f, cy - circleRadius * 0.70f, (5.2f * effDensity).coerceAtLeast(3.5f), Color.parseColor("#FEF08A"), 230)
            drawGlitterStar(cx + circleRadius * 0.65f, cy - circleRadius * 0.70f, (5.2f * effDensity).coerceAtLeast(3.5f), Color.parseColor("#FEF08A"), 230)
        } else {
            // Standard Outer soft halo ring
            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(65, Color.red(ringColor), Color.green(ringColor), Color.blue(ringColor))
            }
            canvas.drawCircle(cx, cy, circleRadius + ringStroke + glowMargin * 0.55f, haloPaint)
        }

        // 2. Decode viewer photo or draw vibrant initial avatar inside anti-aliased circle
        var avatarBmp: Bitmap? = null
        if (b64Avatar.isNotEmpty()) {
            try {
                val bytes = android.util.Base64.decode(b64Avatar, android.util.Base64.DEFAULT)
                if (bytes.isNotEmpty()) {
                    avatarBmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (_: Throwable) {}
        }

        if (avatarBmp != null && !avatarBmp.isRecycled && avatarBmp.width > 0 && avatarBmp.height > 0) {
            val shader = android.graphics.BitmapShader(
                avatarBmp,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP
            )
            val srcRect = RectF(0f, 0f, avatarBmp.width.toFloat(), avatarBmp.height.toFloat())
            val dstRect = RectF(cx - circleRadius, cy - circleRadius, cx + circleRadius, cy + circleRadius)
            val shaderMatrix = android.graphics.Matrix().apply {
                setRectToRect(srcRect, dstRect, android.graphics.Matrix.ScaleToFit.FILL)
            }
            shader.setLocalMatrix(shaderMatrix)
            val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                style = Paint.Style.FILL
                this.shader = shader
            }
            canvas.drawCircle(cx, cy, circleRadius, imgPaint)
            avatarBmp.recycle()
        } else {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = badgeBgColor
            }
            canvas.drawCircle(cx, cy, circleRadius, fillPaint)

            val initials = rawName
                .split(" ", "_", ".")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifBlank { "P" }

            val initPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = circleRadius * 0.82f
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }
            val fm = initPaint.fontMetrics
            val initBaseline = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(initials, cx, initBaseline, initPaint)
        }

        // 3. Glowing colored ring border (Double ring for GIFT with golden metallic stroke)
        if (isGiftEvent) {
            val goldRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = ringStroke * 1.25f
                color = Color.parseColor("#FBBF24")
            }
            canvas.drawCircle(cx, cy, circleRadius, goldRingPaint)

            val outerGoldRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (1.5f * effDensity).coerceAtLeast(1.0f)
                color = Color.parseColor("#FFFBEB")
            }
            canvas.drawCircle(cx, cy, circleRadius + ringStroke * 0.90f, outerGoldRingPaint)
        } else {
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = ringStroke
                color = ringColor
            }
            canvas.drawCircle(cx, cy, circleRadius, ringPaint)
        }

        // 4. Event emoji badge at top-right of avatar circle
        val badgeRadius = (circleRadius * 0.34f).coerceAtLeast(10f)
        val badgeCx = cx + circleRadius * 0.68f
        val badgeCy = cy - circleRadius * 0.68f
        val badgeCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isGiftEvent) Color.parseColor("#F57C00") else Color.parseColor("#F20F172A")
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (1.5f * effDensity).coerceAtLeast(1f)
            color = if (isGiftEvent) Color.parseColor("#FFFBEB") else ringColor
        }
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, badgeCirclePaint)
        canvas.drawCircle(badgeCx, badgeCy, badgeRadius, badgeBorderPaint)

        val emojiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = badgeRadius * 1.15f
            textAlign = Paint.Align.CENTER
        }
        val efm = emojiPaint.fontMetrics
        canvas.drawText(badgeEmoji, badgeCx, badgeCy - (efm.ascent + efm.descent) / 2f, emojiPaint)

        // 5. Bottom viewer short name pill (dengan teks gift jika ada)
        val pillTop = cy + circleRadius - overlapY * 0.45f
        val pillLeft = (totalW - pillW) / 2f
        val pillRect = RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)
        val pillCorner = pillH / 2f

        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isGiftEvent) Color.parseColor("#F0451A03") else Color.parseColor("#EB0F172A")
        }
        val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (1.4f * effDensity).coerceAtLeast(1f)
            color = if (isGiftEvent) Color.parseColor("#F59E0B") else ringColor
        }
        canvas.drawRoundRect(pillRect, pillCorner, pillCorner, pillBgPaint)
        canvas.drawRoundRect(pillRect, pillCorner, pillCorner, pillStrokePaint)

        val nfm = namePaint.fontMetrics
        val nameBaseline = pillRect.centerY() - (nfm.ascent + nfm.descent) / 2f
        canvas.drawText(cleanShortName, pillRect.centerX() - nameTextW / 2f, nameBaseline, namePaint)

        // 6. EKOR BINTANG KEMILAU (KHUSUS PENGIRIM GIFT): Cascading comet sparkles under name pill
        if (isGiftEvent) {
            val tailStartY = pillRect.bottom + 2f * effDensity
            // Trail sparkles cluster falling downward
            val sparkles = listOf(
                // xOffsetPct (-1..1), yOffset, starRadius, color, alpha
                Triple(-0.25f, tailStartY + (4f * effDensity), (6.5f * effDensity).coerceAtLeast(4f)),
                Triple(0.20f, tailStartY + (8f * effDensity), (7.5f * effDensity).coerceAtLeast(4.5f)),
                Triple(-0.05f, tailStartY + (14f * effDensity), (8.5f * effDensity).coerceAtLeast(5f)),
                Triple(0.35f, tailStartY + (20f * effDensity), (5.5f * effDensity).coerceAtLeast(3.5f)),
                Triple(-0.30f, tailStartY + (25f * effDensity), (5.0f * effDensity).coerceAtLeast(3f)),
                Triple(0.08f, tailStartY + (31f * effDensity), (6.0f * effDensity).coerceAtLeast(3.5f)),
                Triple(-0.12f, tailStartY + (38f * effDensity), (4.0f * effDensity).coerceAtLeast(2.5f)),
                Triple(0.18f, tailStartY + (44f * effDensity), (3.2f * effDensity).coerceAtLeast(2f))
            )

            // Connecting stardust luminous trail line
            val trailPath = Path().apply {
                moveTo(cx, pillRect.bottom)
                cubicTo(
                    cx - (10f * effDensity), tailStartY + (12f * effDensity),
                    cx + (12f * effDensity), tailStartY + (26f * effDensity),
                    cx, tailStartY + (46f * effDensity)
                )
            }
            val trailLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (2.2f * effDensity).coerceAtLeast(1.5f)
                strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(
                    cx, tailStartY, cx, tailStartY + (46f * effDensity),
                    intArrayOf(
                        Color.argb(220, 255, 215, 0),
                        Color.argb(140, 251, 191, 36),
                        Color.argb(40, 245, 158, 11),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0.0f, 0.40f, 0.80f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(trailPath, trailLinePaint)

            // Draw sparkling stars along the trail with progressive fade-out
            sparkles.forEachIndexed { idx, (xOffPct, py, r) ->
                val px = cx + (xOffPct * circleRadius * 0.95f)
                val starColor = if (idx % 2 == 0) Color.parseColor("#FEF08A") else Color.parseColor("#FDE047")
                val alpha = (255 - idx * 24).coerceIn(80, 255)
                drawGlitterStar(px, py, r, starColor, alpha)

                // Micro sparkle dots orbiting the main stars
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(alpha / 2, 255, 255, 255)
                }
                canvas.drawCircle(px + (r * 0.7f), py - (r * 0.4f), (1.2f * effDensity).coerceAtLeast(1f), dotPaint)
                canvas.drawCircle(px - (r * 0.8f), py + (r * 0.5f), (1.0f * effDensity).coerceAtLeast(0.8f), dotPaint)
            }
        }

        return bmp
    }

    /**
     * Computes smooth rising + sine-wave horizontal sway NDC quad coordinates for an AVATAR_BUBBLE.
     */
    fun computeAvatarBubbleQuadNdcCoords(
        banner: OverlayBannerData,
        quadWidth: Int,
        quadHeight: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
        currentRotation: Int,
        elapsedMs: Long,
        phaseSeed: Float = 0f
    ): FloatArray {
        val sW = surfaceWidth.toFloat().coerceAtLeast(1f)
        val sH = surfaceHeight.toFloat().coerceAtLeast(1f)
        val effectiveRotation = (currentRotation + 270) % 360
        val viewW = minOf(sW, sH)
        val viewH = maxOf(sW, sH)

        val dur = banner.durationMs.coerceAtLeast(600L).toFloat()
        val progress = (elapsedMs.toFloat() / dur).coerceIn(0f, 1f)

        // Smooth pop-in scale during first 260ms
        val entryScale = if (elapsedMs < 260L) {
            val t = (elapsedMs / 260f).coerceIn(0f, 1f)
            val easeOut = 1f - (1f - t) * (1f - t)
            0.35f + 0.65f * easeOut
        } else {
            1.0f
        }

        // Rise from bottom (startYpct = 0.88f) all the way past the top screen boundary (endYpct = -0.16f)
        val startYpct = banner.posYpct.coerceIn(0.65f, 0.94f)
        val endYpct = -0.16f
        val currentYpct = startYpct - (startYpct - endYpct) * progress

        // Sine-wave horizontal sway (gentle 1.8 wave cycles inside anti-collision lane)
        val waveAngle = (progress * 1.8f * Math.PI * 2.0 + phaseSeed.toDouble())
        val swayXpct = (kotlin.math.sin(waveAngle) * 0.022).toFloat()
        val currentXpct = (banner.posXpct + swayXpct).coerceIn(0.08f, 0.92f)

        val w = quadWidth.toFloat() * entryScale
        val h = quadHeight.toFloat() * entryScale

        val centerX = currentXpct * viewW
        val centerY = currentYpct * viewH
        val leftX = centerX - w / 2f
        val rightX = centerX + w / 2f
        val topY = centerY - h / 2f
        val bottomY = centerY + h / 2f

        val cornersX = floatArrayOf(leftX, rightX, leftX, rightX)
        val cornersY = floatArrayOf(bottomY, bottomY, topY, topY)

        val coords = FloatArray(8)
        for (i in 0 until 4) {
            val vx = cornersX[i]
            val vy = cornersY[i]
            val (ndcX, ndcY) = when (effectiveRotation) {
                0 -> Pair((vx / viewW) * 2f - 1f, 1f - (vy / viewH) * 2f)
                90 -> Pair(1f - (vy / viewH) * 2f, 1f - (vx / viewW) * 2f)
                180 -> Pair(1f - (vx / viewW) * 2f, (vy / viewH) * 2f - 1f)
                else -> Pair((vy / viewH) * 2f - 1f, (vx / viewW) * 2f - 1f)
            }
            coords[i * 2] = ndcX
            coords[i * 2 + 1] = ndcY
        }
        return coords
    }

    fun computeAvatarBubbleAlpha(elapsedMs: Long, durationMs: Long): Float {
        val totalDur = durationMs.coerceAtLeast(600L).toFloat()
        val fadeInMs = 180f
        val fadeOutMs = (totalDur * 0.22f).coerceIn(400f, 900f)
        return when {
            elapsedMs < fadeInMs -> (elapsedMs / fadeInMs).coerceIn(0f, 1f)
            elapsedMs > (durationMs - fadeOutMs) -> {
                val rem = (durationMs - elapsedMs).toFloat().coerceAtLeast(0f)
                val t = (rem / fadeOutMs).coerceIn(0f, 1f)
                (t * t * (3f - 2f * t)).coerceIn(0f, 1f)
            }
            else -> 1.0f
        }
    }

    fun generateTugOfWarBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        maxWidthPx: Int,
        interSemiBoldTypeface: Typeface
    ): Bitmap {
        if (banner.content.startsWith("KING_OF_THRONE|")) {
            return generateKingOfThroneBitmap(banner, densityScale, maxWidthPx, interSemiBoldTypeface)
        }
        if (banner.content.startsWith("BEYBATTLE|")) {
            return generateBeyBattleBitmap(banner, densityScale, maxWidthPx, interSemiBoldTypeface)
        }
        if (banner.content.startsWith("TROOPER_ROYALE|")) {
            return generateTrooperRoyaleBitmap(banner, densityScale, maxWidthPx, interSemiBoldTypeface)
        }
        val parts = banner.content.split("|")
        val teamA = parts.getOrNull(0)?.ifBlank { "COWOK" } ?: "COWOK"
        val teamB = parts.getOrNull(1)?.ifBlank { "CEWEK" } ?: "CEWEK"
        val colorHexA = parts.getOrNull(2)?.ifBlank { "#0284C7" } ?: "#0284C7"
        val colorHexB = parts.getOrNull(3)?.ifBlank { "#E11D48" } ?: "#E11D48"
        val ropePos = parts.getOrNull(4)?.toFloatOrNull() ?: 0.0f
        val ptsToWin = (parts.getOrNull(5)?.toFloatOrNull() ?: 500.0f).coerceAtLeast(1.0f)
        val winsA = parts.getOrNull(6)?.toIntOrNull() ?: 0
        val winsB = parts.getOrNull(7)?.toIntOrNull() ?: 0
        val round = parts.getOrNull(8)?.toIntOrNull() ?: 1
        val lastAction = parts.getOrNull(9) ?: "Menunggu tarikan penonton..."
        val winnerTeam = parts.getOrNull(10) ?: ""
        val celebUntil = parts.getOrNull(11)?.toLongOrNull() ?: 0L

        val isCelebrating = System.currentTimeMillis() < celebUntil && winnerTeam.isNotEmpty()
        val normalizedProgress = ((ropePos / ptsToWin).coerceIn(-1.0f, 1.0f) + 1.0f) / 2.0f
        val percentA = ((1.0f - normalizedProgress) * 100).toInt().coerceIn(0, 100)
        val percentB = (normalizedProgress * 100).toInt().coerceIn(0, 100)

        val totalW = maxWidthPx.coerceIn(520, 1000)
        val totalH = (120f * densityScale).toInt().coerceIn(130, 260)
        val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val colorA = try { Color.parseColor(colorHexA) } catch (_: Exception) { Color.parseColor("#0284C7") }
        val colorB = try { Color.parseColor(colorHexB) } catch (_: Exception) { Color.parseColor("#E11D48") }

        val cornerR = 12f * densityScale

        // 1. Background Card with subtle dual gradient glow
        val cardRect = RectF(4f * densityScale, 4f * densityScale, totalW - 4f * densityScale, totalH - 4f * densityScale)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0F172A") // Deep slate
        }
        canvas.drawRoundRect(cardRect, cornerR, cornerR, bgPaint)

        // Border with Cyan-to-Rose gradient
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * densityScale
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                intArrayOf(colorA, Color.parseColor("#F59E0B"), colorB),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, cornerR, cornerR, borderPaint)

        // 2. Header Row: "⚔️ DUEL TARIK TAMBANG ⚔️  •  RONDE $round  •  SKOR: $winsA - $winsB"
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = interSemiBoldTypeface
            textSize = 10.5f * densityScale
            color = Color.parseColor("#FDE047") // Gold title
            textAlign = Paint.Align.CENTER
        }
        val headerText = "⚔️ TARIK TAMBANG  •  RONDE $round  •  SKOR: $winsA - $winsB"
        val headerY = cardRect.top + 16f * densityScale
        canvas.drawText(headerText, totalW / 2f, headerY, headerPaint)

        // 3. Team Labels Row
        val teamTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
            textSize = 12.5f * densityScale
        }

        // Left Team Label: ◄ $teamA ($percentA%)
        teamTextPaint.color = colorA
        teamTextPaint.textAlign = Paint.Align.LEFT
        val teamLabelA = "◄ $teamA ($percentA%)"
        val teamY = headerY + 18f * densityScale
        canvas.drawText(teamLabelA, cardRect.left + 10f * densityScale, teamY, teamTextPaint)

        // Right Team Label: ($percentB%) $teamB ►
        teamTextPaint.color = colorB
        teamTextPaint.textAlign = Paint.Align.RIGHT
        val teamLabelB = "($percentB%) $teamB ►"
        canvas.drawText(teamLabelB, cardRect.right - 10f * densityScale, teamY, teamTextPaint)

        // 4. Central Tug Rope Track
        val trackMarginH = 10f * densityScale
        val trackLeft = cardRect.left + trackMarginH
        val trackRight = cardRect.right - trackMarginH
        val trackW = trackRight - trackLeft
        val trackH = 14f * densityScale
        val trackTop = teamY + 8f * densityScale
        val trackBottom = trackTop + trackH
        val trackRect = RectF(trackLeft, trackTop, trackRight, trackBottom)
        val trackCorner = trackH / 2f

        // Track Background Groove
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1E293B")
        }
        canvas.drawRoundRect(trackRect, trackCorner, trackCorner, groovePaint)

        // Left Fill (Team A progress)
        val knotX = trackLeft + (trackW * normalizedProgress)
        val leftFillRect = RectF(trackLeft, trackTop + 1.5f * densityScale, knotX, trackBottom - 1.5f * densityScale)
        val leftFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(190, Color.red(colorA), Color.green(colorA), Color.blue(colorA))
        }
        canvas.drawRoundRect(leftFillRect, trackCorner * 0.7f, trackCorner * 0.7f, leftFillPaint)

        // Right Fill (Team B progress)
        val rightFillRect = RectF(knotX, trackTop + 1.5f * densityScale, trackRight, trackBottom - 1.5f * densityScale)
        val rightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(190, Color.red(colorB), Color.green(colorB), Color.blue(colorB))
        }
        canvas.drawRoundRect(rightFillRect, trackCorner * 0.7f, trackCorner * 0.7f, rightFillPaint)

        // Rope Center Target Marker Line (Tengah 50%)
        val centerLineX = trackLeft + trackW * 0.5f
        val centerMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * densityScale
            color = Color.WHITE
        }
        canvas.drawLine(centerLineX, trackTop - 2f * densityScale, centerLineX, trackBottom + 2f * densityScale, centerMarkPaint)

        // Red Bandana / Knot Flag on the Rope
        val knotRadius = trackH * 0.75f
        val knotCenterY = (trackTop + trackBottom) / 2f
        val knotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#EF4444") // Bright Red Ribbon / Knot
        }
        canvas.drawCircle(knotX, knotCenterY, knotRadius, knotPaint)
        val knotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * densityScale
            color = Color.WHITE
        }
        canvas.drawCircle(knotX, knotCenterY, knotRadius, knotBorderPaint)

        // 5. Bottom Action Ticker or Celebration Banner
        val footerY = trackBottom + 16f * densityScale
        if (isCelebrating) {
            // Gold Victory Pill
            val celebRect = RectF(cardRect.left + 8f * densityScale, trackBottom + 4f * densityScale, cardRect.right - 8f * densityScale, cardRect.bottom - 4f * densityScale)
            val celebBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    celebRect.left, celebRect.top, celebRect.right, celebRect.bottom,
                    Color.parseColor("#D97706"), Color.parseColor("#F59E0B"), Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(celebRect, 8f * densityScale, 8f * densityScale, celebBgPaint)

            val celebTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
                textSize = 10.5f * densityScale
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }
            val winText = "👑 RONDE $round DIMENANGKAN OLEH: $winnerTeam! 🎉"
            canvas.drawText(winText, totalW / 2f, celebRect.centerY() + 3.5f * densityScale, celebTextPaint)
        } else {
            // Live Action Ticker Pill
            val tickerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = interSemiBoldTypeface
                textSize = 9.0f * densityScale
                color = Color.parseColor("#94A3B8") // Slate
                textAlign = Paint.Align.CENTER
            }
            val safeAction = if (lastAction.length > 55) lastAction.take(52) + "..." else lastAction
            val tickerText = "⚡ $safeAction  |  Like & Chat 1/2 untuk Menarik!"
            canvas.drawText(tickerText, totalW / 2f, footerY, tickerTextPaint)
        }

        return bmp
    }

    private fun generateKingOfThroneBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        maxWidthPx: Int,
        interSemiBoldTypeface: Typeface
    ): Bitmap {
        val parts = banner.content.split("|")
        // Format: KING_OF_THRONE|title|kingName|kingScore|shieldHp|maxShieldHp|round|usurpCount|topChallenger|longestReign|lastAction|lastUsurper|celebUntilMs
        val throneTitle = parts.getOrNull(1)?.ifBlank { "RAJA SINGGASANA" } ?: "RAJA SINGGASANA"
        val kingName = parts.getOrNull(2)?.ifBlank { "KOSONG" } ?: "KOSONG"
        val kingScore = parts.getOrNull(3)?.toLongOrNull() ?: 0L
        val shieldHp = parts.getOrNull(4)?.toIntOrNull() ?: 0
        val maxShieldHp = (parts.getOrNull(5)?.toIntOrNull() ?: 250).coerceAtLeast(1)
        val round = parts.getOrNull(6)?.toIntOrNull() ?: 1
        val usurpCount = parts.getOrNull(7)?.toIntOrNull() ?: 0
        val topChallengerRaw = parts.getOrNull(8) ?: "-:0"
        val lastAction = parts.getOrNull(10) ?: "Tap Like, Komen & Gift untuk merebut takhta!"
        val lastUsurper = parts.getOrNull(11) ?: ""
        val celebUntil = parts.getOrNull(12)?.toLongOrNull() ?: 0L

        val isCelebrating = System.currentTimeMillis() < celebUntil && lastUsurper.isNotBlank()
        val hasKing = kingName.isNotBlank() && kingName != "KOSONG"
        val shieldRatio = if (!hasKing) 0f else (shieldHp.toFloat() / maxShieldHp.toFloat()).coerceIn(0f, 1f)
        val shieldPct = (shieldRatio * 100f).toInt().coerceIn(0, 100)

        val totalW = maxWidthPx.coerceIn(520, 1000)
        val totalH = (128f * densityScale).toInt().coerceIn(138, 270)
        val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val goldColor = Color.parseColor("#FACC15")
        val royalAmber = Color.parseColor("#D97706")
        val crimsonAttack = Color.parseColor("#E11D48")
        val cornerR = 12f * densityScale

        val cardRect = RectF(4f * densityScale, 4f * densityScale, totalW - 4f * densityScale, totalH - 4f * densityScale)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0F172A")
        }
        canvas.drawRoundRect(cardRect, cornerR, cornerR, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * densityScale
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                intArrayOf(goldColor, royalAmber, crimsonAttack),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, cornerR, cornerR, borderPaint)

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
            textSize = 10.5f * densityScale
            color = goldColor
            textAlign = Paint.Align.CENTER
        }
        val headerY = cardRect.top + 16f * densityScale
        canvas.drawText("👑 $throneTitle  •  RONDE $round  •  KUDETA: ${usurpCount}x", totalW / 2f, headerY, headerPaint)

        val kingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
            textSize = 13.5f * densityScale
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
        }
        val kingY = headerY + 19f * densityScale
        val kingDisplay = if (hasKing) "👑 $kingName ($kingScore Poin)" else "👑 SINGGASANA KOSONG!"
        canvas.drawText(kingDisplay, cardRect.left + 12f * densityScale, kingY, kingPaint)

        val shieldLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
            textSize = 11.5f * densityScale
            color = if (hasKing) Color.parseColor("#38BDF8") else Color.parseColor("#F43F5E")
            textAlign = Paint.Align.RIGHT
        }
        val shieldText = if (hasKing) "🛡️ Perisai: $shieldHp HP ($shieldPct%)" else "⚔️ Penantang: $topChallengerRaw"
        canvas.drawText(shieldText, cardRect.right - 12f * densityScale, kingY, shieldLabelPaint)

        // Shield HP Bar vs Coup Bar
        val trackLeft = cardRect.left + 12f * densityScale
        val trackRight = cardRect.right - 12f * densityScale
        val trackW = trackRight - trackLeft
        val trackH = 14f * densityScale
        val trackTop = kingY + 8f * densityScale
        val trackBottom = trackTop + trackH
        val trackRect = RectF(trackLeft, trackTop, trackRight, trackBottom)
        val trackCorner = trackH / 2f

        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#331E293B")
        }
        canvas.drawRoundRect(trackRect, trackCorner, trackCorner, groovePaint)

        // Attack / Coup background fill (Crimson)
        val coupFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#88BE123C")
        }
        canvas.drawRoundRect(trackRect, trackCorner, trackCorner, coupFillPaint)

        // Shield Fill (Gold -> Emerald/Cyan)
        if (shieldRatio > 0f) {
            val fillRight = (trackLeft + trackW * shieldRatio).coerceAtLeast(trackLeft + trackCorner)
            val shieldRect = RectF(trackLeft, trackTop, fillRight, trackBottom)
            val shieldFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    trackLeft, trackTop, fillRight, trackBottom,
                    Color.parseColor("#F59E0B"),
                    Color.parseColor("#10B981"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(shieldRect, trackCorner, trackCorner, shieldFillPaint)
        }

        val footerY = trackBottom + 17f * densityScale
        if (isCelebrating) {
            val celebRect = RectF(cardRect.left + 8f * densityScale, trackBottom + 4f * densityScale, cardRect.right - 8f * densityScale, cardRect.bottom - 4f * densityScale)
            val celebBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    celebRect.left, celebRect.top, celebRect.right, celebRect.bottom,
                    Color.parseColor("#B45309"), Color.parseColor("#FACC15"), Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(celebRect, 8f * densityScale, 8f * densityScale, celebBgPaint)
            val celebTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
                textSize = 10.5f * densityScale
                color = Color.parseColor("#090D16")
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("👑 KUDETA! $lastUsurper MENDUDUKI SINGGASANA! 🔥", totalW / 2f, celebRect.centerY() + 3.5f * densityScale, celebTextPaint)
        } else {
            val tickerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = interSemiBoldTypeface
                textSize = 9.0f * densityScale
                color = Color.parseColor("#E2E8F0")
                textAlign = Paint.Align.CENTER
            }
            val safeAct = if (lastAction.length > 58) lastAction.take(55) + "..." else lastAction
            canvas.drawText("⚡ $safeAct", totalW / 2f, footerY, tickerPaint)
        }

        return bmp
    }

    private fun generateBeyBattleBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        maxWidthPx: Int,
        interSemiBoldTypeface: Typeface
    ): Bitmap {
        val parts = banner.content.split("|")
        // Format: BEYBATTLE|activeCount|championName|lastEventBanner|currentRound|hunterText
        val activeCount = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val champName = parts.getOrNull(2)?.ifBlank { "-" } ?: "-"
        val lastBanner = parts.getOrNull(3) ?: "Arena Gangsing Tempur"
        val round = parts.getOrNull(4)?.toIntOrNull() ?: 1
        val hunterText = parts.getOrNull(5)?.ifBlank { "-" } ?: "-"

        val totalW = maxWidthPx.coerceIn(520, 1000)
        val totalH = (128f * densityScale).toInt().coerceIn(138, 270)
        val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val neonCyan = Color.parseColor("#06B6D4")
        val electricPurple = Color.parseColor("#A855F7")
        val goldAccent = Color.parseColor("#FACC15")
        val cornerR = 12f * densityScale

        val cardRect = RectF(4f * densityScale, 4f * densityScale, totalW - 4f * densityScale, totalH - 4f * densityScale)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0B1329")
        }
        canvas.drawRoundRect(cardRect, cornerR, cornerR, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * densityScale
            color = neonCyan
        }
        canvas.drawRoundRect(cardRect, cornerR, cornerR, borderPaint)

        // Header: Title & Round
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
            textSize = 12f * densityScale
            color = neonCyan
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("🌀 BEYBATTLE ROYALE", 16f * densityScale, 26f * densityScale, titlePaint)

        val roundPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
            textSize = 10.5f * densityScale
            color = goldAccent
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("RONDE $round", totalW - 16f * densityScale, 26f * densityScale, roundPaint)

        // Middle Row: Active players badge & Top KO badge
        val badgeW = (totalW - 40f * densityScale) / 2f
        val badgeH = 34f * densityScale
        val badgeY = 36f * densityScale

        // Badge 1: Survivors
        val b1Rect = RectF(16f * densityScale, badgeY, 16f * densityScale + badgeW, badgeY + badgeH)
        val b1Bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1E293B")
        }
        canvas.drawRoundRect(b1Rect, 6f * densityScale, 6f * densityScale, b1Bg)

        val bTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
            textSize = 10f * densityScale
            color = Color.parseColor("#38BDF8")
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🛡️ $activeCount Gangsing Aktif", b1Rect.centerX(), b1Rect.centerY() + 3.5f * densityScale, bTextPaint)

        // Badge 2: Champion / Hunter
        val b2Rect = RectF(b1Rect.right + 8f * densityScale, badgeY, b1Rect.right + 8f * densityScale + badgeW, badgeY + badgeH)
        canvas.drawRoundRect(b2Rect, 6f * densityScale, 6f * densityScale, b1Bg)

        val b2Text = if (champName != "-") "👑 Juara: $champName" else "⚔️ K.O Hunter: $hunterText"
        val b2TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
            textSize = 9.5f * densityScale
            color = if (champName != "-") goldAccent else electricPurple
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(b2Text, b2Rect.centerX(), b2Rect.centerY() + 3.5f * densityScale, b2TextPaint)

        // Live Action Ticker Bar
        val tickerY = badgeY + badgeH + 8f * densityScale
        val tickerRect = RectF(16f * densityScale, tickerY, totalW - 16f * densityScale, tickerY + 26f * densityScale)
        val tickerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0F172A")
        }
        canvas.drawRoundRect(tickerRect, 6f * densityScale, 6f * densityScale, tickerBg)

        val tickerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * densityScale
            color = Color.parseColor("#334155")
        }
        canvas.drawRoundRect(tickerRect, 6f * densityScale, 6f * densityScale, tickerBorder)

        val tickerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = interSemiBoldTypeface
            textSize = 9.5f * densityScale
            color = Color.parseColor("#F1F5F9")
            textAlign = Paint.Align.CENTER
        }
        val safeBanner = if (lastBanner.length > 56) lastBanner.take(53) + "..." else lastBanner
        canvas.drawText(safeBanner, tickerRect.centerX(), tickerRect.centerY() + 3.5f * densityScale, tickerTextPaint)

        // Footer Guidance
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = interSemiBoldTypeface
            textSize = 8.0f * densityScale
            color = Color.parseColor("#94A3B8")
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("👆 Tap: +Stamina/Center | 💬 Chat: Dash Serang | 🎁 Gift: Mega Titan", totalW / 2f, totalH - 8f * densityScale, footerPaint)

        return bmp
    }

    private fun generateTrooperRoyaleBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        maxWidthPx: Int,
        interSemiBoldTypeface: Typeface
    ): Bitmap {
        val parts = banner.content.split("|")
        // Format: TROOPER_ROYALE|round|alive|total|safeR|lastMsg|winner|top1Name|top1Kills|posX|posY|scale
        val round = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val aliveCount = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val totalCount = parts.getOrNull(3)?.toIntOrNull() ?: 0
        val lastMsg = parts.getOrNull(5)?.ifBlank { "Tap Like = Tembak | Chat = Hindar | Gift = Bazooka" } ?: "Prajurit Tempur Chibi"
        val winnerName = parts.getOrNull(6) ?: ""
        val top1Name = parts.getOrNull(7)?.ifBlank { "-" } ?: "-"
        val top1Kills = parts.getOrNull(8)?.toIntOrNull() ?: 0

        val totalW = maxWidthPx.coerceIn(520, 1000)
        val totalH = (128f * densityScale).toInt().coerceIn(138, 270)
        val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val emeraldGreen = Color.parseColor("#10B981")
        val goldAccent = Color.parseColor("#FACC15")
        val skyCyan = Color.parseColor("#38BDF8")
        val cornerR = 12f * densityScale

        val cardRect = RectF(4f * densityScale, 4f * densityScale, totalW - 4f * densityScale, totalH - 4f * densityScale)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0F172A")
        }
        canvas.drawRoundRect(cardRect, cornerR, cornerR, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * densityScale
            color = emeraldGreen
        }
        canvas.drawRoundRect(cardRect, cornerR, cornerR, borderPaint)

        // Header
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
            textSize = 12f * densityScale
            color = goldAccent
        }
        canvas.drawText("🎖️ MINI TROOPER ROYALE • R-$round", 16f * densityScale, 24f * densityScale, titlePaint)

        val countPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(interSemiBoldTypeface, Typeface.BOLD)
            textSize = 11f * densityScale
            color = skyCyan
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("👥 $aliveCount/$totalCount HIDUP", totalW - 16f * densityScale, 24f * densityScale, countPaint)

        // Status Pemenang / Top 1
        val topPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = interSemiBoldTypeface
            textSize = 10f * densityScale
            color = Color.parseColor("#E2E8F0")
        }
        val topStatus = if (winnerName.isNotBlank()) "🏆 JUARA 1: $winnerName" else "👑 TOP 1: $top1Name ($top1Kills Kills)"
        canvas.drawText(topStatus, 16f * densityScale, 46f * densityScale, topPaint)

        // Ticker Box
        val tickerRect = RectF(16f * densityScale, 56f * densityScale, totalW - 16f * densityScale, 88f * densityScale)
        val tickerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1E293B")
        }
        canvas.drawRoundRect(tickerRect, 6f * densityScale, 6f * densityScale, tickerBg)

        val tickerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = interSemiBoldTypeface
            textSize = 9.5f * densityScale
            color = Color.parseColor("#F1F5F9")
            textAlign = Paint.Align.CENTER
        }
        val safeMsg = if (lastMsg.length > 56) lastMsg.take(53) + "..." else lastMsg
        canvas.drawText(safeMsg, tickerRect.centerX(), tickerRect.centerY() + 3.5f * densityScale, tickerTextPaint)

        // Footer Pedoman Simple Jelas Padat
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = interSemiBoldTypeface
            textSize = 8.0f * densityScale
            color = Color.parseColor("#94A3B8")
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("❤️ LIKE: TEMBAK | 💬 CHAT: HINDAR | 🎁 GIFT: BAZOOKA & TANK", totalW / 2f, totalH - 8f * densityScale, footerPaint)

        return bmp
    }
}
