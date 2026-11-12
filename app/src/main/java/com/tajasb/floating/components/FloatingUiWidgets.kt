@file:Suppress("DEPRECATION")

package com.tajasb.floating.components

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.tajasb.FloatingControlService
import com.tajasb.FloatingTab
import com.example.MainActivity
import kotlinx.coroutines.launch

/**
 * FloatingUiWidgets:
 * Modul komponen UI mengambang yang digunakan bersama:
 * - Preview Live Stiker/Watermark (Pakai Blok, Tanpa Blok, Outline Tanpa Blok + Teks, Hanya Outline, Running Teks)
 * - Dialog input teks Stiker & Watermark (1-5 baris)
 * - Dialog input kode warna Hex (#RRGGBB)
 * - Fixed Bottom Movement Dock (D-Pad, Rotasi, Mirror, Scale, Reset)
 * - Tombol Stepper & Preset Chip
 */
object FloatingUiWidgets {

    fun createStepperBtn(
        context: Context,
        dp: (Int) -> Int,
        label: String,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    fun createPresetChip(
        context: Context,
        dp: (Int) -> Int,
        label: String,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 8f
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(2), dp(5), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#D97724"))
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    fun createCollapsibleSectionCard(
        context: Context,
        dp: (Int) -> Int,
        title: String,
        accentHex: String = "#38BDF8",
        defaultExpanded: Boolean = false,
        headerRightView: View? = null,
        onContent: (LinearLayout) -> Unit
    ): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
            visibility = if (defaultExpanded) View.VISIBLE else View.GONE
        }

        var isExpanded = defaultExpanded

        val arrowView = TextView(context).apply {
            text = if (defaultExpanded) "▼" else "▶"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(accentHex))
            setPadding(0, 0, dp(4), 0)
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                isExpanded = !isExpanded
                contentLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
                arrowView.text = if (isExpanded) "▼" else "▶"
            }
        }

        headerRow.addView(arrowView)

        val titleView = TextView(context).apply {
            text = title
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(titleView)

        if (headerRightView != null) {
            headerRow.addView(headerRightView)
        }

        card.addView(headerRow)
        onContent(contentLayout)
        card.addView(contentLayout)
        return card
    }

    fun createPureIconBtn(
        context: Context,
        dp: (Int) -> Int,
        icon: String,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = icon
        textSize = 9.5f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(Color.parseColor("#1E293B"))
            setStroke(dp(1), Color.parseColor("#475569"))
        }
        setPadding(dp(4), dp(5), dp(4), dp(5))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(1)
            marginEnd = dp(1)
        }
        setOnClickListener { onClick() }
    }

    fun createPureTextBtn(
        context: Context,
        dp: (Int) -> Int,
        textLabel: String,
        accentBgHex: String = "#1E293B",
        accentStrokeHex: String = "#475569",
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = textLabel
        textSize = 8.5f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(Color.parseColor(accentBgHex))
            setStroke(dp(1), Color.parseColor(accentStrokeHex))
        }
        setPadding(dp(6), dp(5), dp(6), dp(5))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(1)
            marginEnd = dp(1)
        }
        setOnClickListener { onClick() }
    }

    fun createDenseToggleChip(
        context: Context,
        dp: (Int) -> Int,
        label: String,
        getVal: () -> Boolean,
        onToggle: (Boolean) -> Unit
    ): TextView = TextView(context).apply {
        val cur = getVal()
        text = label
        textSize = 8.0f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(if (cur) Color.WHITE else Color.parseColor("#94A3B8"))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(if (cur) Color.parseColor("#047857") else Color.parseColor("#1E293B"))
            setStroke(dp(1), if (cur) Color.parseColor("#10B981") else Color.parseColor("#334155"))
        }
        setPadding(dp(4), dp(5), dp(4), dp(5))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(1)
            marginEnd = dp(1)
        }
        setOnClickListener {
            val next = !getVal()
            onToggle(next)
            setTextColor(if (next) Color.WHITE else Color.parseColor("#94A3B8"))
            (background as? GradientDrawable)?.apply {
                setColor(if (next) Color.parseColor("#047857") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (next) Color.parseColor("#10B981") else Color.parseColor("#334155"))
            }
        }
    }

    fun createStyledOverlayPreviewView(service: FloatingControlService, isWatermark: Boolean): View = with(service) {
        val density = resources.displayMetrics.density
        return object : View(this@with) {
            private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val gridPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1f * density
            }
            private val badgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 8f * density
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            private val fillPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                style = android.graphics.Paint.Style.FILL
            }
            private val strokePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                style = android.graphics.Paint.Style.STROKE
                strokeJoin = android.graphics.Paint.Join.ROUND
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            private val blockPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
            }
            private val blockBorderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat().coerceAtLeast(1f)
                val h = height.toFloat().coerceAtLeast(1f)

                bgPaint.color = Color.parseColor("#090D16")
                val outerRect = android.graphics.RectF(0f, 0f, w, h)
                canvas.drawRoundRect(outerRect, 6f * density, 6f * density, bgPaint)

                val step = 14f * density
                var gx = step
                while (gx < w) {
                    canvas.drawLine(gx, 0f, gx, h, gridPaint)
                    gx += step
                }
                var gy = step
                while (gy < h) {
                    canvas.drawLine(0f, gy, w, gy, gridPaint)
                    gy += step
                }

                val stikerMgr = com.tajasb.stiker.CustomStikerManager.getInstance(context)
                val wmMgr = com.tajasb.stiker.CustomWatermarkManager.getInstance(context)

                val rawText = if (isWatermark) wmMgr.watermarkText.value else stikerMgr.stikerText.value
                val dispStyle = (if (isWatermark) wmMgr.displayStyle.value else stikerMgr.displayStyle.value).uppercase()
                val isRun = if (isWatermark) wmMgr.isRunningText.value else stikerMgr.isRunningText.value
                val runSpd = if (isWatermark) wmMgr.runningTextSpeed.value else stikerMgr.runningTextSpeed.value
                val runDir = if (isWatermark) wmMgr.runningTextDirection.value else stikerMgr.runningTextDirection.value
                val boxHex = if (isWatermark) wmMgr.boxColorHex.value else stikerMgr.boxColorHex.value
                val boxAlpha = if (isWatermark) wmMgr.boxAlpha.value else stikerMgr.boxAlpha.value
                val txtHex = if (isWatermark) wmMgr.textColorHex.value else stikerMgr.textColorHex.value
                val outW = if (isWatermark) wmMgr.outlineWidth.value else stikerMgr.outlineWidth.value
                val outHex = if (isWatermark) wmMgr.outlineColorHex.value else stikerMgr.outlineColorHex.value

                val isWithBlock = (dispStyle == "WITH_BLOCK" && boxAlpha > 0.01f)
                val isOutlineOnly = (dispStyle == "OUTLINE_ONLY")
                val isHollowOutline = (dispStyle == "HOLLOW_OUTLINE")

                val styleBadge = when {
                    isHollowOutline -> "⭕ HANYA OUTLINE (TANPA BLOK)"
                    isOutlineOnly -> "✒️ OUTLINE TANPA BLOK + TEKS"
                    isWithBlock -> "📦 PAKAI BLOK"
                    else -> "🔤 TANPA BLOK (CUMAN TEKS)"
                } + if (isRun) " • 🏃 RUNNING (${runSpd}x)" else " • DIAM"

                canvas.drawText(styleBadge, 8f * density, 11f * density, badgePaint)

                val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(2).ifEmpty {
                    listOf(if (isWatermark) "💧 LIVE WATERMARK" else "⚡ PROMO STIKER")
                }

                val boxCol = try { Color.parseColor(boxHex) } catch (_: Throwable) { Color.parseColor("#DC2626") }
                val txtCol = try { Color.parseColor(txtHex) } catch (_: Throwable) { Color.WHITE }
                val baseOutCol = try { Color.parseColor(outHex) } catch (_: Throwable) { Color.BLACK }

                val alphaInt = (boxAlpha * 255).toInt().coerceIn(0, 255)
                val framelessAlpha = if (!isWithBlock) alphaInt.coerceIn(70, 255) else 255

                fillPaint.textSize = (if (lines.size > 1) 11.5f else 13f) * density
                fillPaint.color = Color.argb(framelessAlpha, Color.red(txtCol), Color.green(txtCol), Color.blue(txtCol))

                val rawOutCol = if (isOutlineOnly && baseOutCol == txtCol) {
                    if (txtCol == Color.BLACK) Color.WHITE else Color.BLACK
                } else {
                    baseOutCol
                }
                strokePaint.textSize = fillPaint.textSize
                strokePaint.color = Color.argb(framelessAlpha, Color.red(rawOutCol), Color.green(rawOutCol), Color.blue(rawOutCol))
                val effOutDp = if (isOutlineOnly || isHollowOutline) outW.coerceAtLeast(2) else outW
                strokePaint.strokeWidth = (effOutDp * density * (if (isOutlineOnly) 1.55f else 1.2f)).coerceAtLeast(1.8f)

                val lineH = fillPaint.textSize * 1.28f
                val maxLineW = lines.maxOfOrNull { fillPaint.measureText(it) } ?: 80f
                val pillPadH = 12f * density
                val pillPadV = 5f * density
                val pillW = if (isRun) (w - 20f * density) else (maxLineW + pillPadH * 2).coerceAtMost(w - 16f * density)
                val pillH = (lines.size * lineH + pillPadV * 2)
                val pillLeft = (w - pillW) / 2f
                val pillTop = 15f * density + ((h - 15f * density - pillH) / 2f).coerceAtLeast(2f)
                val pillRect = android.graphics.RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)

                if (isWithBlock) {
                    blockPaint.color = Color.argb(alphaInt, Color.red(boxCol), Color.green(boxCol), Color.blue(boxCol))
                    canvas.drawRoundRect(pillRect, 6f * density, 6f * density, blockPaint)
                    if (outW > 0) {
                        blockBorderPaint.strokeWidth = (outW * density).coerceAtLeast(1f)
                        blockBorderPaint.color = Color.argb(alphaInt.coerceAtLeast(180), Color.red(baseOutCol), Color.green(baseOutCol), Color.blue(baseOutCol))
                        canvas.drawRoundRect(pillRect, 6f * density, 6f * density, blockBorderPaint)
                    }
                }

                fun drawStyled(str: String, x: Float, y: Float) {
                    when {
                        isHollowOutline -> canvas.drawText(str, x, y, strokePaint)
                        isOutlineOnly -> {
                            canvas.drawText(str, x, y, strokePaint)
                            canvas.drawText(str, x, y, fillPaint)
                        }
                        else -> canvas.drawText(str, x, y, fillPaint)
                    }
                }

                canvas.save()
                canvas.clipRect(pillRect)
                if (isRun) {
                    val now = System.currentTimeMillis()
                    val speedPxPerSec = 55f * density * runSpd.coerceIn(0.3f, 3.0f)
                    val elapsedSec = ((now % 3600000L) / 1000.0).toFloat()
                    val sep = "   •   "
                    for (i in lines.indices) {
                        val chunk = lines[i] + sep
                        val cycleW = fillPaint.measureText(chunk).coerceAtLeast(30f)
                        val shift = (elapsedSec * speedPxPerSec) % cycleW
                        val ty = pillTop + pillPadV + (i * lineH) + (fillPaint.textSize * 0.86f)
                        var sx = if (runDir.equals("RIGHT", ignoreCase = true)) {
                            pillLeft + pillPadH + shift - cycleW
                        } else {
                            pillLeft + pillPadH - shift
                        }
                        while (sx > pillLeft + pillPadH) sx -= cycleW
                        var dx = sx
                        while (dx < pillRect.right) {
                            drawStyled(chunk, dx, ty)
                            dx += cycleW
                        }
                    }
                    postInvalidateDelayed(32L)
                } else {
                    for (i in lines.indices) {
                        val line = lines[i]
                        val lw = fillPaint.measureText(line)
                        val tx = pillLeft + ((pillW - lw) / 2f).coerceAtLeast(pillPadH * 0.4f)
                        val ty = pillTop + pillPadV + (i * lineH) + (fillPaint.textSize * 0.86f)
                        drawStyled(line, tx, ty)
                    }
                }
                canvas.restore()
            }
        }
    }

    fun createAutoTimePreviewView(service: FloatingControlService): View = with(service) {
        val density = resources.displayMetrics.density
        return object : View(this@with) {
            private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val gridPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1f * density
            }
            private val badgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 7.8f * density
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            private val pillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
            }
            private val pillBorderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
            }
            private val dateBoxPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
            }
            private val dayTextPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            private val monthTextPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            private val timeTextPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                fontFeatureSettings = "tnum"
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat().coerceAtLeast(1f)
                val h = height.toFloat().coerceAtLeast(1f)

                bgPaint.color = Color.parseColor("#090D16")
                val outerRect = android.graphics.RectF(0f, 0f, w, h)
                canvas.drawRoundRect(outerRect, 6f * density, 6f * density, bgPaint)

                val step = 14f * density
                var gx = step
                while (gx < w) {
                    canvas.drawLine(gx, 0f, gx, h, gridPaint)
                    gx += step
                }
                var gy = step
                while (gy < h) {
                    canvas.drawLine(0f, gy, w, gy, gridPaint)
                    gy += step
                }

                val mgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(context)
                val isOn = mgr.isEnabled.value
                val showSec = mgr.showSeconds.value
                val statusText = (if (isOn) "🟢 AKTIF LIVE" else "⚪ PREVIEW") +
                    (if (showSec) " • HH:mm:ss" else " • HH:mm") +
                    " • (${mgr.sizeScale.value}x)"
                canvas.drawText(statusText, 7f * density, 10.5f * density, badgePaint)

                val (dayStr, monthStr, timeStr) = com.tajasb.notif.AutoTimeCapsuleManager.formatDateAndTimeParts(
                    System.currentTimeMillis(),
                    showSec
                )

                val capBgCol = try { Color.parseColor(mgr.capsuleBgColorHex.value) } catch (_: Throwable) { Color.parseColor("#EA580C") }
                val outCol = try { Color.parseColor(mgr.capsuleOutlineColorHex.value) } catch (_: Throwable) { Color.WHITE }
                val dateBgCol = try { Color.parseColor(mgr.dateBoxBgColorHex.value) } catch (_: Throwable) { Color.WHITE }
                val dateTxtCol = try { Color.parseColor(mgr.dateBoxTextColorHex.value) } catch (_: Throwable) { Color.parseColor("#EA580C") }
                val clockTxtCol = try { Color.parseColor(mgr.timeTextColorHex.value) } catch (_: Throwable) { Color.WHITE }

                val alphaInt = (mgr.capsuleAlpha.value.coerceIn(0.15f, 1.0f) * 255).toInt().coerceIn(45, 255)
                val outW = mgr.capsuleOutlineWidth.value

                val badgeSize = 26f * density
                val padL = 4f * density
                val padR = (mgr.capsulePaddingH.value.coerceIn(6, 32) * 0.8f) * density
                val gap = 7f * density

                dayTextPaint.textSize = 10f * density
                dayTextPaint.color = dateTxtCol
                monthTextPaint.textSize = 6.5f * density
                monthTextPaint.color = dateTxtCol
                timeTextPaint.textSize = 13.5f * density
                timeTextPaint.color = clockTxtCol

                val timeW = timeTextPaint.measureText(timeStr)
                val pillW = (padL + badgeSize + gap + timeW + padR).coerceAtMost(w - 16f * density)
                val pillH = badgeSize + 7f * density
                val pillLeft = (w - pillW) / 2f
                val pillTop = 13f * density + ((h - 13f * density - pillH) / 2f).coerceAtLeast(1f)
                val pillRect = android.graphics.RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)

                val cRad = mgr.capsuleCornerRadius.value
                val maxRad = pillH / 2f
                val radPx = if (cRad >= 32) maxRad else (cRad * density * 0.85f).coerceAtMost(maxRad)

                pillPaint.color = Color.argb(alphaInt, Color.red(capBgCol), Color.green(capBgCol), Color.blue(capBgCol))
                canvas.drawRoundRect(pillRect, radPx, radPx, pillPaint)

                if (outW > 0) {
                    pillBorderPaint.strokeWidth = (outW * density).coerceAtLeast(1f)
                    pillBorderPaint.color = Color.argb(alphaInt.coerceAtLeast(210), Color.red(outCol), Color.green(outCol), Color.blue(outCol))
                    canvas.drawRoundRect(pillRect, radPx, radPx, pillBorderPaint)
                }

                val badgeLeft = pillLeft + padL
                val badgeTop = pillTop + (pillH - badgeSize) / 2f
                val badgeRect = android.graphics.RectF(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize)
                val badgeRad = (radPx * 0.78f).coerceIn(3.5f * density, badgeSize * 0.44f)

                dateBoxPaint.color = Color.argb(alphaInt.coerceAtLeast(230), Color.red(dateBgCol), Color.green(dateBgCol), Color.blue(dateBgCol))
                canvas.drawRoundRect(badgeRect, badgeRad, badgeRad, dateBoxPaint)

                val bcx = badgeRect.centerX()
                canvas.drawText(dayStr, bcx, badgeRect.top + badgeSize * 0.49f, dayTextPaint)
                canvas.drawText(monthStr, bcx, badgeRect.top + badgeSize * 0.84f, monthTextPaint)

                val timeFm = timeTextPaint.fontMetrics
                val timeBaseline = pillRect.centerY() - ((timeFm.ascent + timeFm.descent) / 2f)
                canvas.drawText(timeStr, badgeRect.right + gap, timeBaseline, timeTextPaint)

                postInvalidateDelayed(1000L)
            }
        }
    }

    fun showStikerTextInputDialog(service: FloatingControlService, targetEt: EditText) = with(service) {
        try {
            val density = resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()

            val stikerManager = com.tajasb.stiker.CustomStikerManager.getInstance(this)
            val currentVal = stikerManager.stikerText.value

            val input = EditText(this).apply {
                setText(currentVal)
                setSingleLine(false)
                maxLines = 5
                setLines(4)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#64748B"))
                hint = "Ketik teks stiker promo (bisa 1 s/d 5 baris)..."
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#38BDF8"))
                }
            }

            val container = FrameLayout(this).apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(input)
            }

            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🏷️ Edit Teks Stiker Live (1 - 5 Baris)")
                .setMessage("Ketik teks promo stiker (tekan Enter untuk baris baru 2, 3, 4, 5 baris):")
                .setView(container)
                .setPositiveButton("💾 Simpan") { d, _ ->
                    val text = input.text.toString().trim()
                    targetEt.setText(text)
                    stikerManager.setStikerText(text)
                    Toast.makeText(applicationContext, "Teks stiker disimpan! 🏷️", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }
                .create()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.show()
            input.requestFocus()
            input.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 250L)
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Gagal membuka keyboard dialog", Toast.LENGTH_SHORT).show()
        }
    }

    fun showWatermarkTextInputDialog(service: FloatingControlService, targetEt: EditText) = with(service) {
        try {
            val density = resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()

            val watermarkManager = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)
            val currentVal = watermarkManager.watermarkText.value

            val input = EditText(this).apply {
                setText(currentVal)
                setSingleLine(false)
                maxLines = 5
                setLines(4)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#64748B"))
                hint = "Ketik teks watermark (bisa 1 s/d 5 baris)..."
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                }
            }

            val container = FrameLayout(this).apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(input)
            }

            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("💧 Edit Teks Watermark (1 - 5 Baris)")
                .setMessage("Ketik teks watermark kamera (tekan Enter untuk baris baru):")
                .setView(container)
                .setPositiveButton("💾 Simpan") { d, _ ->
                    val text = input.text.toString().trim()
                    targetEt.setText(text)
                    watermarkManager.setWatermarkText(text)
                    Toast.makeText(applicationContext, "Teks watermark disimpan! 💧", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }
                .create()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.show()
            input.requestFocus()
            input.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 250L)
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Gagal membuka keyboard dialog", Toast.LENGTH_SHORT).show()
        }
    }

    fun showHexColorInputDialog(
        service: FloatingControlService,
        title: String,
        initialHex: String,
        onColorApplied: (String) -> Unit
    ) = with(service) {
        try {
            val density = resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()

            val input = EditText(this).apply {
                setText(initialHex)
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#64748B"))
                hint = "#RRGGBB (contoh: #D97724)"
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#38BDF8"))
                }
            }

            val previewBox = View(this).apply {
                val lp = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    marginEnd = dp(8)
                }
                layoutParams = lp
                val initialCol = try { Color.parseColor(if (initialHex.startsWith("#")) initialHex else "#$initialHex") } catch (_: Throwable) { Color.parseColor("#D97724") }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(initialCol)
                    setStroke(dp(1), Color.WHITE)
                }
            }

            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val hex = s?.toString()?.trim() ?: ""
                    val clean = if (hex.startsWith("#")) hex else "#$hex"
                    try {
                        val c = Color.parseColor(clean)
                        (previewBox.background as? GradientDrawable)?.setColor(c)
                    } catch (_: Throwable) {}
                }
            })

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(previewBox)
                addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }

            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setMessage("Ketik atau tempel kode warna heksadesimal (#RRGGBB):")
                .setView(row)
                .setPositiveButton("✓ Terapkan") { d, _ ->
                    val hex = input.text.toString().trim()
                    val clean = if (hex.startsWith("#")) hex else "#$hex"
                    try {
                        Color.parseColor(clean)
                        onColorApplied(clean)
                        Toast.makeText(applicationContext, "Warna diterapkan: $clean", Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) {
                        Toast.makeText(applicationContext, "Format warna tidak valid (Contoh: #D97724)", Toast.LENGTH_LONG).show()
                    }
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }
                .create()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.show()
            input.requestFocus()
            input.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 250L)
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Gagal membuka dialog warna", Toast.LENGTH_SHORT).show()
        }
    }

    fun createFixedBottomDockBar(service: FloatingControlService, dp: (Int) -> Int): LinearLayout = with(service) {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#09101D"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            background = bg
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }
        fixedBottomDockBar = dock

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(3))
        }
        val title = TextView(this).apply {
            text = "📍 Geser Menu Aktif"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        fixedBottomDockTitle = title
        titleRow.addView(title)

        val hint = TextView(this).apply {
            text = "Tap: 1x | Tahan: Cepat"
            textSize = 7.5f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 0, dp(4), 0)
        }
        titleRow.addView(hint)

        var isDockExpanded = true
        val btnToggleDockCollapse = TextView(this).apply {
            text = " ▾ "
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(1), dp(4), dp(1))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#1E293B"))
            }
        }
        titleRow.addView(btnToggleDockCollapse)

        dock.addView(titleRow)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }

        val toggleDock: () -> Unit = {
            isDockExpanded = !isDockExpanded
            btnRow.visibility = if (isDockExpanded) View.VISIBLE else View.GONE
            hint.visibility = if (isDockExpanded) View.VISIBLE else View.GONE
            btnToggleDockCollapse.text = if (isDockExpanded) " ▾ " else " ▴ "
        }
        titleRow.setOnClickListener { toggleDock() }
        btnToggleDockCollapse.setOnClickListener { toggleDock() }

        fun makeDockBtn(
            icon: String,
            isAccent: Boolean = false,
            onClick: () -> Unit,
            onLongClick: (() -> Unit)? = null
        ): TextView {
            return TextView(this).apply {
                text = icon
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(if (isAccent) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isAccent) {
                        setColor(Color.parseColor("#F59E0B"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#334155"))
                    }
                }
                setPadding(dp(2), dp(6), dp(2), dp(6))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    onClick()
                    updateFixedBottomDockUi()
                }
                if (onLongClick != null) {
                    setOnLongClickListener {
                        onLongClick()
                        updateFixedBottomDockUi()
                        true
                    }
                }
            }
        }

        btnRow.addView(makeDockBtn("◀️", onClick = {
            dispatchMove(-0.005f, 0f, -2f, 0f)
        }, onLongClick = {
            dispatchMove(-0.04f, 0f, -15f, 0f)
        }))

        btnRow.addView(makeDockBtn("▶️", onClick = {
            dispatchMove(0.005f, 0f, 2f, 0f)
        }, onLongClick = {
            dispatchMove(0.04f, 0f, 15f, 0f)
        }))

        btnRow.addView(makeDockBtn("🔼", onClick = {
            dispatchMove(0f, -0.005f, 0f, -2f)
        }, onLongClick = {
            dispatchMove(0f, -0.04f, 0f, -15f)
        }))

        btnRow.addView(makeDockBtn("🔽", onClick = {
            dispatchMove(0f, 0.005f, 0f, 2f)
        }, onLongClick = {
            dispatchMove(0f, 0.04f, 0f, 15f)
        }))

        val btnRot = makeDockBtn("🔄", isAccent = true, onClick = {
            dispatchRotate()
        })
        btnFixedBottomRotText = btnRot
        btnRow.addView(btnRot)

        val btnMir = makeDockBtn("🪞", onClick = {
            dispatchMirror()
        })
        btnFixedBottomMirrorText = btnMir
        btnRow.addView(btnMir)

        btnRow.addView(makeDockBtn("➖", onClick = {
            dispatchScale(-0.05f)
        }, onLongClick = {
            dispatchScale(-0.15f)
        }))

        btnRow.addView(makeDockBtn("➕", onClick = {
            dispatchScale(0.05f)
        }, onLongClick = {
            dispatchScale(0.15f)
        }))

        btnRow.addView(makeDockBtn("↺", onClick = {
            dispatchReset()
        }))

        dock.addView(btnRow)
        return dock
    }

    fun updateFixedBottomDockUi(service: FloatingControlService) = with(service) {
        val shouldShow = (currentTab == FloatingTab.NOTIF || currentTab == FloatingTab.TAP)
        fixedBottomDockBar?.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (!shouldShow) return

        if (currentTab == FloatingTab.TAP) {
            val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
            val lockTag = when (autoTap.markDisplayMode.value) {
                com.tajasb.autotap.MarkDisplayMode.LOCKED_HIDDEN -> " [🙈 SEMBUNYI]"
                com.tajasb.autotap.MarkDisplayMode.LOCKED_GHOST -> " [🔒 TERKUNCI]"
                com.tajasb.autotap.MarkDisplayMode.EDITABLE -> ""
            }
            if (autoTap.executionMode.value == com.tajasb.autotap.TapExecutionMode.MULTI_MARK) {
                val marks = autoTap.marks.value
                val selIdx = marks.indexOfFirst { it.id == autoTap.selectedMarkId.value }
                val selMark = if (selIdx >= 0) marks[selIdx] else marks.firstOrNull()
                if (selMark != null) {
                    val num = if (selIdx >= 0) selIdx + 1 else 1
                    val itemLock = if (selMark.isLocked && lockTag.isEmpty()) " [🔒]" else lockTag
                    val coordStr = " (${selMark.x.toInt()}, ${selMark.y.toInt()}) • ${autoTap.markSizeDp.value}dp$itemLock"
                    fixedBottomDockTitle?.text = "🎯 Kursor Mark #$num ${selMark.type.shortLabel}$coordStr"
                } else {
                    fixedBottomDockTitle?.text = "🎯 Kursor Multi-Mark (${autoTap.markSizeDp.value}dp)$lockTag"
                }
            } else {
                fixedBottomDockTitle?.text = "🎯 Kursor Target Tap (${autoTap.targetX.value.toInt()}, ${autoTap.targetY.value.toInt()}) • ${autoTap.markSizeDp.value}dp$lockTag"
            }
            btnFixedBottomRotText?.visibility = View.GONE
            btnFixedBottomMirrorText?.visibility = View.GONE
        } else {
            btnFixedBottomRotText?.visibility = View.VISIBLE
            btnFixedBottomMirrorText?.visibility = View.VISIBLE
            val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)
            val buyerNotif = com.tajasb.notif.BuyerNotificationManager.getInstance(this)
            val stikerManager = com.tajasb.stiker.CustomStikerManager.getInstance(this)

            when (liveComment.bannerMode.value) {
                com.tajasb.livecomment.BannerMode.LIVE_DOA_COMMENT -> {
                    val rot = liveComment.overlayRotation.value
                    val isMir = liveComment.overlayMirrored.value
                    fixedBottomDockTitle?.text = "🤲 Doa & Komen Live (Rot: ${rot}°, Mir: ${if (isMir) "ON" else "OFF"})"
                    btnFixedBottomRotText?.text = "${rot}°"
                    btnFixedBottomMirrorText?.text = if (isMir) "🪞 ON" else "🪞"
                }
                com.tajasb.livecomment.BannerMode.BUYER_SALES -> {
                    val rot = buyerNotif.rotation.value
                    val isMir = buyerNotif.isMirrored.value
                    fixedBottomDockTitle?.text = "🛍️ Notif Jualan (Rot: ${rot}°, Mir: ${if (isMir) "ON" else "OFF"})"
                    btnFixedBottomRotText?.text = "${rot}°"
                    btnFixedBottomMirrorText?.text = if (isMir) "🪞 ON" else "🪞"
                }
                com.tajasb.livecomment.BannerMode.STICKER_TEXT -> {
                    val rot = stikerManager.rotation.value
                    val isMir = stikerManager.isMirrored.value
                    val isRand = stikerManager.isRandomMoveEnabled.value
                    fixedBottomDockTitle?.text = "🏷️ Stiker Tulisan (${if (isRand) "🎲 Acak 5s, " else ""}Rot: ${rot}°, Mir: ${if (isMir) "ON" else "OFF"})"
                    btnFixedBottomRotText?.text = "${rot}°"
                    btnFixedBottomMirrorText?.text = if (isMir) "🪞 ON" else "🪞"
                }
                com.tajasb.livecomment.BannerMode.WATERMARK_TEXT -> {
                    val wmManager = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)
                    val rot = wmManager.rotation.value
                    val isMir = wmManager.isMirrored.value
                    val isRand = wmManager.isRandomMoveEnabled.value
                    fixedBottomDockTitle?.text = "💧 Watermark (${if (isRand) "🎲 Acak 5s, " else ""}Rot: ${rot}°, Mir: ${if (isMir) "ON" else "OFF"})"
                    btnFixedBottomRotText?.text = "${rot}°"
                    btnFixedBottomMirrorText?.text = if (isMir) "🪞 ON" else "🪞"
                }
                com.tajasb.livecomment.BannerMode.AUTO_TIME -> {
                    val autoTimeMgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(this)
                    val rot = autoTimeMgr.rotation.value
                    val isMir = autoTimeMgr.isMirrored.value
                    val isLock = autoTimeMgr.isPositionLocked.value
                    fixedBottomDockTitle?.text = "⏰ Waktu Otomatis (${if (isLock) "🔒 Kunci, " else ""}Rot: ${rot}°, Mir: ${if (isMir) "ON" else "OFF"})"
                    btnFixedBottomRotText?.text = "${rot}°"
                    btnFixedBottomMirrorText?.text = if (isMir) "🪞 ON" else "🪞"
                }
                com.tajasb.livecomment.BannerMode.QURAN_KHATAM -> {
                    val quran = com.tajasb.quran.InteractiveQuranKhatamEngine.getInstance(this)
                    val qState = quran.state.value
                    val rot = qState.overlayRotation
                    val isMir = qState.overlayMirrored
                    val sc = String.format(java.util.Locale.US, "%.2f", qState.overlayScale)
                    fixedBottomDockTitle?.text = "🕌 Khatam Al-Qur'an (Skala: ${sc}x, Rot: ${rot}°, Mir: ${if (isMir) "ON" else "OFF"})"
                    btnFixedBottomRotText?.text = "${rot}°"
                    btnFixedBottomMirrorText?.text = if (isMir) "🪞 ON" else "🪞"
                }
                com.tajasb.livecomment.BannerMode.TUG_OF_WAR -> {
                    val tow = com.tajasb.game.InteractiveTugOfWarEngine.getInstance(this)
                    val s = tow.state.value
                    val sc = String.format(java.util.Locale.US, "%.1f", s.overlayScale)
                    fixedBottomDockTitle?.text = "⚔️ Tarik Tambang (${s.teamAName} vs ${s.teamBName}, Skala: ${sc}x)"
                    btnFixedBottomRotText?.text = "${s.overlayRotation}°"
                    btnFixedBottomMirrorText?.text = if (s.isMirrored) "🪞 ON" else "🪞"
                }
            }
        }
    }

    fun dispatchMove(service: FloatingControlService, dxPct: Float, dyPct: Float, dxPixels: Float, dyPixels: Float) = with(service) {
        if (currentTab == FloatingTab.TAP) {
            val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
            if (autoTap.isAllMarksLocked()) {
                Toast.makeText(applicationContext, "🔒 Mark sedang dikunci! Tekan '👁️ GESER' untuk membuka kunci posisi.", Toast.LENGTH_SHORT).show()
                return
            }
            val selMark = autoTap.marks.value.firstOrNull { it.id == autoTap.selectedMarkId.value } ?: autoTap.marks.value.firstOrNull()
            if (autoTap.executionMode.value == com.tajasb.autotap.TapExecutionMode.MULTI_MARK && selMark?.isLocked == true) {
                Toast.makeText(applicationContext, "🔒 Mark ini dikunci! Buka ikon gembok pada baris Mark untuk menggeser.", Toast.LENGTH_SHORT).show()
                return
            }
            if (!autoTap.isPointerVisible.value) {
                autoTap.setPointerVisible(true)
            }
            autoTap.moveTargetPosition(dxPixels * 8f, dyPixels * 8f)
            updateCrosshairWindow(this)
            updateAutoTapUi()
            onRefreshTapMarksUi?.invoke()
            updateFixedBottomDockUi(this)
            return
        }

        if (currentTab == FloatingTab.NOTIF) {
            val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)
            val buyerNotif = com.tajasb.notif.BuyerNotificationManager.getInstance(this)
            val stikerManager = com.tajasb.stiker.CustomStikerManager.getInstance(this)
            val wmManager = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)

            when (liveComment.bannerMode.value) {
                com.tajasb.livecomment.BannerMode.LIVE_DOA_COMMENT -> {
                    liveComment.moveOverlay(dxPct, dyPct)
                }
                com.tajasb.livecomment.BannerMode.BUYER_SALES -> {
                    buyerNotif.movePositionStep(dxPixels, dyPixels)
                }
                com.tajasb.livecomment.BannerMode.STICKER_TEXT -> {
                    stikerManager.movePosition(dxPct, dyPct)
                }
                com.tajasb.livecomment.BannerMode.WATERMARK_TEXT -> {
                    wmManager.movePosition(dxPct, dyPct)
                }
                com.tajasb.livecomment.BannerMode.AUTO_TIME -> {
                    val autoTimeMgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(this)
                    if (autoTimeMgr.isPositionLocked.value) {
                        Toast.makeText(applicationContext, "🔒 Posisi Waktu Otomatis sedang dikunci!", Toast.LENGTH_SHORT).show()
                    } else {
                        autoTimeMgr.movePosition(dxPct, dyPct)
                    }
                }
                com.tajasb.livecomment.BannerMode.QURAN_KHATAM -> {
                    com.tajasb.quran.InteractiveQuranKhatamEngine.getInstance(this).moveOverlay(dxPct, dyPct)
                }
                com.tajasb.livecomment.BannerMode.TUG_OF_WAR -> {
                    val tow = com.tajasb.game.InteractiveTugOfWarEngine.getInstance(this)
                    val s = tow.state.value
                    tow.setTransform(
                        (s.posXpct + dxPct).coerceIn(0.05f, 0.95f),
                        (s.posYpct + dyPct).coerceIn(0.05f, 0.95f),
                        s.overlayScale,
                        s.overlayRotation,
                        s.isMirrored
                    )
                }
            }
            updateBuyerNotifUi()
        }
    }

    fun dispatchRotate(service: FloatingControlService) = with(service) {
        if (currentTab == FloatingTab.VIDEO || currentTab == FloatingTab.VISUAL) {
            val nextRot = (repository.config.value.rotation + 90) % 360
            serviceScope.launch {
                repository.setRotation(nextRot)
                Toast.makeText(applicationContext, "Rotasi Video: $nextRot° 📐", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (currentTab == FloatingTab.NOTIF) {
            val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)
            val buyerNotif = com.tajasb.notif.BuyerNotificationManager.getInstance(this)
            val stikerManager = com.tajasb.stiker.CustomStikerManager.getInstance(this)
            val wmManager = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)
            val autoTimeMgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(this)
            when (liveComment.bannerMode.value) {
                com.tajasb.livecomment.BannerMode.LIVE_DOA_COMMENT -> liveComment.cycleOverlayRotation()
                com.tajasb.livecomment.BannerMode.BUYER_SALES -> buyerNotif.cycleRotation()
                com.tajasb.livecomment.BannerMode.STICKER_TEXT -> stikerManager.cycleRotation()
                com.tajasb.livecomment.BannerMode.WATERMARK_TEXT -> wmManager.cycleRotation()
                com.tajasb.livecomment.BannerMode.AUTO_TIME -> autoTimeMgr.cycleRotation()
                com.tajasb.livecomment.BannerMode.QURAN_KHATAM -> com.tajasb.quran.InteractiveQuranKhatamEngine.getInstance(this).cycleOverlayRotation()
                com.tajasb.livecomment.BannerMode.TUG_OF_WAR -> {
                    val tow = com.tajasb.game.InteractiveTugOfWarEngine.getInstance(this)
                    val s = tow.state.value
                    tow.setTransform(s.posXpct, s.posYpct, s.overlayScale, (s.overlayRotation + 90) % 360, s.isMirrored)
                }
            }
            updateBuyerNotifUi()
        }
    }

    fun dispatchMirror(service: FloatingControlService) = with(service) {
        if (currentTab == FloatingTab.VIDEO || currentTab == FloatingTab.VISUAL) {
            serviceScope.launch {
                val next = repository.toggleMirror()
                Toast.makeText(applicationContext, if (next) "Mirror Video: ON 🪞" else "Mirror Video: OFF", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (currentTab == FloatingTab.NOTIF) {
            val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)
            val buyerNotif = com.tajasb.notif.BuyerNotificationManager.getInstance(this)
            val stikerManager = com.tajasb.stiker.CustomStikerManager.getInstance(this)
            val wmManager = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)
            val autoTimeMgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(this)
            when (liveComment.bannerMode.value) {
                com.tajasb.livecomment.BannerMode.LIVE_DOA_COMMENT -> {
                    val next = liveComment.toggleOverlayMirror()
                    Toast.makeText(applicationContext, if (next) "Mirror Doa: ON 🪞" else "Mirror Doa: OFF", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.BUYER_SALES -> {
                    val next = buyerNotif.toggleMirror()
                    Toast.makeText(applicationContext, if (next) "Mirror Notif: ON 🪞" else "Mirror Notif: OFF", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.STICKER_TEXT -> {
                    val next = stikerManager.toggleMirror()
                    Toast.makeText(applicationContext, if (next) "Mirror Stiker: ON 🪞" else "Mirror Stiker: OFF", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.WATERMARK_TEXT -> {
                    val next = wmManager.toggleMirror()
                    Toast.makeText(applicationContext, if (next) "Mirror Watermark: ON 🪞" else "Mirror Watermark: OFF", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.AUTO_TIME -> {
                    val next = autoTimeMgr.toggleMirror()
                    Toast.makeText(applicationContext, if (next) "Mirror Waktu Otomatis: ON 🪞" else "Mirror Waktu Otomatis: OFF", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.QURAN_KHATAM -> {
                    val next = com.tajasb.quran.InteractiveQuranKhatamEngine.getInstance(this).toggleOverlayMirror()
                    Toast.makeText(applicationContext, if (next) "Mirror Khatam: ON 🪞" else "Mirror Khatam: OFF", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.TUG_OF_WAR -> {
                    val tow = com.tajasb.game.InteractiveTugOfWarEngine.getInstance(this)
                    val s = tow.state.value
                    val nextMir = !s.isMirrored
                    tow.setTransform(s.posXpct, s.posYpct, s.overlayScale, s.overlayRotation, nextMir)
                    Toast.makeText(applicationContext, if (nextMir) "Mirror Tambang: ON 🪞" else "Mirror Tambang: OFF", Toast.LENGTH_SHORT).show()
                }
            }
            updateBuyerNotifUi()
        }
    }

    fun dispatchScale(service: FloatingControlService, delta: Float) = with(service) {
        if (currentTab == FloatingTab.VIDEO || currentTab == FloatingTab.VISUAL) {
            serviceScope.launch {
                val z = repository.adjustZoom(delta)
                Toast.makeText(applicationContext, "Zoom Video: ${String.format(java.util.Locale.US, "%.2f", z)}x (${(z * 100).toInt()}%) 🔍", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (currentTab == FloatingTab.TAP) {
            val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
            if (!autoTap.isPointerVisible.value && !autoTap.isMarksHiddenFromView()) {
                autoTap.setPointerVisible(true)
            }
            val deltaDp = if (delta > 0) 4 else -4
            autoTap.changeMarkSize(deltaDp)
            Toast.makeText(applicationContext, "Ukuran Mark: ${autoTap.markSizeDp.value}dp 🎯", Toast.LENGTH_SHORT).show()
            updateCrosshairWindow(this)
            updateAutoTapUi()
            onRefreshTapMarksUi?.invoke()
            updateFixedBottomDockUi(this)
            return
        }

        if (currentTab == FloatingTab.NOTIF) {
            val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)
            val buyerNotif = com.tajasb.notif.BuyerNotificationManager.getInstance(this)
            val stikerManager = com.tajasb.stiker.CustomStikerManager.getInstance(this)
            val wmManager = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)
            val autoTimeMgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(this)
            when (liveComment.bannerMode.value) {
                com.tajasb.livecomment.BannerMode.LIVE_DOA_COMMENT -> liveComment.adjustOverlayScale(delta * 2f)
                com.tajasb.livecomment.BannerMode.BUYER_SALES -> buyerNotif.changeScale(delta)
                com.tajasb.livecomment.BannerMode.STICKER_TEXT -> stikerManager.changeScale(delta)
                com.tajasb.livecomment.BannerMode.WATERMARK_TEXT -> wmManager.changeScale(delta)
                com.tajasb.livecomment.BannerMode.AUTO_TIME -> autoTimeMgr.changeScale(delta * 2f)
                com.tajasb.livecomment.BannerMode.QURAN_KHATAM -> {
                    val nextSc = com.tajasb.quran.InteractiveQuranKhatamEngine.getInstance(this).adjustOverlayScale(delta * 2f)
                    Toast.makeText(applicationContext, "Skala Khatam: ${String.format(java.util.Locale.US, "%.2f", nextSc)}x 🔍", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.TUG_OF_WAR -> {
                    val tow = com.tajasb.game.InteractiveTugOfWarEngine.getInstance(this)
                    val s = tow.state.value
                    val nextSc = (s.overlayScale + delta * 2f).coerceIn(0.6f, 2.0f)
                    tow.setTransform(s.posXpct, s.posYpct, nextSc, s.overlayRotation, s.isMirrored)
                    Toast.makeText(applicationContext, "Skala Tambang: ${String.format(java.util.Locale.US, "%.1f", nextSc)}x 🔍", Toast.LENGTH_SHORT).show()
                }
            }
            updateBuyerNotifUi()
            updateFixedBottomDockUi(this)
        }
    }

    fun dispatchReset(service: FloatingControlService) = with(service) {
        if (currentTab == FloatingTab.VIDEO || currentTab == FloatingTab.VISUAL) {
            serviceScope.launch {
                repository.setZoomFactor(1.0f)
                Toast.makeText(applicationContext, "Zoom Video Direset: 1.00x (100%) 🎯", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (currentTab == FloatingTab.TAP) {
            val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
            if (autoTap.isAllMarksLocked()) {
                Toast.makeText(applicationContext, "🔒 Mark sedang dikunci! Tekan '👁️ GESER' terlebih dahulu.", Toast.LENGTH_SHORT).show()
                return
            }
            autoTap.resetTargetPosition()
            updateCrosshairWindow(this)
            updateAutoTapUi()
            onRefreshTapMarksUi?.invoke()
            updateFixedBottomDockUi(this)
            Toast.makeText(applicationContext, "Target tap direset ke tengah", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentTab == FloatingTab.NOTIF) {
            val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)
            val buyerNotif = com.tajasb.notif.BuyerNotificationManager.getInstance(this)
            val stikerManager = com.tajasb.stiker.CustomStikerManager.getInstance(this)
            val wmManager = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)
            val autoTimeMgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(this)
            when (liveComment.bannerMode.value) {
                com.tajasb.livecomment.BannerMode.LIVE_DOA_COMMENT -> {
                    liveComment.resetOverlayTransform()
                    Toast.makeText(applicationContext, "Posisi Doa direset", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.BUYER_SALES -> {
                    buyerNotif.resetToDefaultPosition()
                    Toast.makeText(applicationContext, "Posisi Notif Jualan direset", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.STICKER_TEXT -> {
                    stikerManager.resetPosition()
                    Toast.makeText(applicationContext, "Posisi Stiker direset", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.WATERMARK_TEXT -> {
                    wmManager.resetPosition()
                    Toast.makeText(applicationContext, "Posisi Watermark direset", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.AUTO_TIME -> {
                    autoTimeMgr.resetPosition()
                    Toast.makeText(applicationContext, "Posisi Waktu Otomatis direset", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.QURAN_KHATAM -> {
                    com.tajasb.quran.InteractiveQuranKhatamEngine.getInstance(this).resetOverlayTransform()
                    Toast.makeText(applicationContext, "Posisi Khatam Qur'an direset", Toast.LENGTH_SHORT).show()
                }
                com.tajasb.livecomment.BannerMode.TUG_OF_WAR -> {
                    val tow = com.tajasb.game.InteractiveTugOfWarEngine.getInstance(this)
                    tow.setTransform(0.50f, 0.28f, 1.0f, 0, false)
                    tow.resetRope()
                    Toast.makeText(applicationContext, "Posisi Tarik Tambang direset", Toast.LENGTH_SHORT).show()
                }
            }
            updateBuyerNotifUi()
        }
    }

    fun updateCrosshairWindow(service: FloatingControlService): Unit = with(service) {
        val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
        if (autoTap.isPointerVisible.value) {
            showCrosshairPointer()
        } else {
            removeCrosshairPointer()
        }
    }

    fun showSaveChoreographyDialog(
        service: FloatingControlService,
        onSaved: () -> Unit
    ) = with(service) {
        try {
            val density = resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()
            val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
            val defaultName = "Koreografi ${autoTap.choreographyGroups.value.size + 1}"

            val input = EditText(this).apply {
                setText(defaultName)
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#64748B"))
                hint = "Nama Grup Koreografi (mis: Live TikTok, Scroll Cepat)..."
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                }
                setSelectAllOnFocus(true)
            }

            val container = FrameLayout(this).apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(input)
            }

            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("📂 Simpan Grup Koreografi Mark")
                .setMessage("Simpan posisi & urutan ${autoTap.marks.value.size} Mark saat ini ke dalam grup koreografi:")
                .setView(container)
                .setPositiveButton("💾 Simpan Grup") { d, _ ->
                    val name = input.text.toString().trim().ifEmpty { defaultName }
                    val saved = autoTap.saveNewChoreographyGroup(name)
                    Toast.makeText(applicationContext, "✅ Grup '${saved.name}' (${saved.marks.size} Mark) tersimpan!", Toast.LENGTH_SHORT).show()
                    onSaved()
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }
                .create()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.show()
            input.requestFocus()
            input.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 250L)
        } catch (_: Exception) {
            Toast.makeText(applicationContext, "Gagal membuka dialog simpan grup", Toast.LENGTH_SHORT).show()
        }
    }

    fun showRenameChoreographyDialog(
        service: FloatingControlService,
        groupId: String,
        currentName: String,
        onSaved: () -> Unit
    ) = with(service) {
        try {
            val density = resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()
            val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)

            val input = EditText(this).apply {
                setText(currentName)
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#64748B"))
                hint = "Ketik nama koreografi baru..."
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#38BDF8"))
                }
                setSelectAllOnFocus(true)
            }

            val container = FrameLayout(this).apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(input)
            }

            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("✏️ Edit Nama Koreografi")
                .setMessage("Ubah nama untuk koreografi '$currentName':")
                .setView(container)
                .setPositiveButton("💾 Simpan Nama") { d, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        val ok = autoTap.renameChoreographyGroup(groupId, newName)
                        if (ok) {
                            Toast.makeText(applicationContext, "✏️ Nama koreografi diubah menjadi '$newName'!", Toast.LENGTH_SHORT).show()
                            onSaved()
                        }
                    }
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }
                .create()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.show()
            input.requestFocus()
            input.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 250L)
        } catch (_: Exception) {
            Toast.makeText(applicationContext, "Gagal membuka dialog edit nama koreografi", Toast.LENGTH_SHORT).show()
        }
    }

    fun showAutoTypeTextInputDialog(
        service: FloatingControlService,
        onSaved: () -> Unit
    ) = with(service) {
        try {
            val density = resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()
            val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)

            val input = EditText(this).apply {
                setSingleLine(false)
                maxLines = 6
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#64748B"))
                hint = "Ketik 1 atau beberapa kalimat (pisahkan dengan Enter untuk banyak kalimat)..."
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                }
            }

            val container = FrameLayout(this).apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(input)
            }

            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⌨️ Tambah Teks Auto Ketik")
                .setMessage("Ketik kalimat yang ingin ditambahkan ke daftar Auto Ketik (1 baris = 1 kalimat):")
                .setView(container)
                .setPositiveButton("➕ Tambahkan") { d, _ ->
                    val raw = input.text.toString().trim()
                    if (raw.isNotEmpty()) {
                        autoTap.addAutoTypeText(raw)
                        Toast.makeText(applicationContext, "✅ Teks ditambahkan ke daftar Auto Ketik!", Toast.LENGTH_SHORT).show()
                        onSaved()
                    }
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }
                .create()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.show()
            input.requestFocus()
            input.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 250L)
        } catch (_: Exception) {
            Toast.makeText(applicationContext, "Gagal membuka dialog teks Auto Ketik", Toast.LENGTH_SHORT).show()
        }
    }

    fun showEditSingleAutoTypeDialog(
        service: FloatingControlService,
        index: Int,
        currentText: String,
        onSaved: () -> Unit
    ) = with(service) {
        try {
            val density = resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()
            val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)

            val input = EditText(this).apply {
                setSingleLine(false)
                maxLines = 5
                minLines = 2
                gravity = Gravity.TOP or Gravity.START
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#64748B"))
                setText(currentText)
                setSelection(currentText.length)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                }
            }

            val container = FrameLayout(this).apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(input)
            }

            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("✏️ Edit Pesan #${index + 1}")
                .setMessage("Ubah isi pesan Auto Ketik satuan:")
                .setView(container)
                .setPositiveButton("💾 Simpan") { d, _ ->
                    val raw = input.text.toString().trim()
                    if (raw.isNotEmpty()) {
                        autoTap.updateAutoTypeTextAt(index, raw)
                        Toast.makeText(applicationContext, "✅ Pesan #${index + 1} berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        onSaved()
                    }
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }
                .create()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.show()
            input.requestFocus()
            input.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 250L)
        } catch (_: Exception) {
            Toast.makeText(applicationContext, "Gagal membuka dialog edit pesan", Toast.LENGTH_SHORT).show()
        }
    }

    fun setupPanelDrag(service: FloatingControlService, view: View, params: WindowManager.LayoutParams) = with(service) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    params.x = initialX + dx
                    params.y = initialY + dy
                    if (expandedControlPanelView?.windowToken != null) {
                        windowManager.updateViewLayout(expandedControlPanelView, params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun setupPanelCornerResize(
        service: FloatingControlService,
        view: View,
        params: WindowManager.LayoutParams,
        scrollContent: ScrollView?
    ) = with(service) {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.coerceAtLeast(1)
        val screenHeight = displayMetrics.heightPixels.coerceAtLeast(1)

        var initialW = 0
        var initialScrollH = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialW = params.width
                    initialScrollH = scrollContent?.layoutParams?.height ?: dp(250)
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    view.alpha = 0.7f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    val minW = dp(240)
                    val maxW = (screenWidth - params.x).coerceAtLeast(minW).coerceAtMost(screenWidth)
                    val newW = (initialW + dx).coerceIn(minW, maxW)

                    val minScrollH = dp(140)
                    val maxScrollH = (screenHeight * 0.75f).toInt().coerceAtLeast(dp(280))
                    val newScrollH = (initialScrollH + dy).coerceIn(minScrollH, maxScrollH)

                    params.width = newW
                    scrollContent?.layoutParams?.height = newScrollH
                    scrollContent?.requestLayout()

                    if (expandedControlPanelView?.windowToken != null) {
                        try {
                            windowManager.updateViewLayout(expandedControlPanelView, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1.0f
                    val currentW = params.width
                    val currentH = scrollContent?.layoutParams?.height ?: dp(250)
                    getSharedPreferences("vcam_settings", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("floating_panel_custom_width", currentW)
                        .putInt("floating_panel_custom_scroll_height", currentH)
                        .apply()
                    true
                }
                else -> false
            }
        }
    }

    fun styleBuyerNotifChips(
        service: FloatingControlService,
        dp: (Int) -> Int,
        durMs: Long,
        minS: Int,
        maxS: Int,
        curFormat: String,
        curRange: String,
        curProdNo: String
    ) = with(service) {
        val notifManager = com.tajasb.notif.BuyerNotificationManager.getInstance(this)
        val stikerManager = com.tajasb.stiker.CustomStikerManager.getInstance(this)

        fun styleStandardChip(chipTv: TextView, isMatched: Boolean, activeBg: String, activeStroke: String, fallbackColor: Int = Color.parseColor("#94A3B8")) {
            chipTv.apply {
                setTextColor(if (isMatched) Color.BLACK else fallbackColor)
                setTypeface(null, if (isMatched) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isMatched) {
                        setColor(Color.parseColor(activeBg))
                        setStroke(dp(1), Color.parseColor(activeStroke))
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                }
            }
        }

        notifDurationChipsList.forEach { (msVal, chipTv) ->
            val isMatched = msVal == durMs
            val fallback = if (Math.abs(msVal - durMs) < 100) Color.parseColor("#FB923C") else Color.parseColor("#94A3B8")
            styleStandardChip(chipTv, isMatched, "#F97316", "#FB923C", fallback)
        }
        notifIntervalChipsList.forEach { (range, chipTv) ->
            styleStandardChip(chipTv, range.first == minS && range.second == maxS, "#F97316", "#FB923C")
        }
        notifFormatChipsList.forEach { (fmtKey, chipTv) ->
            styleStandardChip(chipTv, fmtKey.equals(curFormat, ignoreCase = true), "#EAA728", "#FDE047")
        }
        notifRangeChipsList.forEach { (rngKey, chipTv) ->
            styleStandardChip(chipTv, rngKey.equals(curRange, ignoreCase = true), "#EAA728", "#FDE047")
        }
        notifProductNoChipsList.forEach { (noKey, chipTv) ->
            styleStandardChip(chipTv, noKey.equals(curProdNo, ignoreCase = true), "#EAA728", "#FDE047")
        }
        val currentDir = notifManager.entryDirection.value
        notifDirectionChipsList.forEach { (dirKey, chipTv) ->
            styleStandardChip(chipTv, dirKey.equals(currentDir, ignoreCase = true), "#F97316", "#FB923C")
        }

        buyerNotifCapsuleColorText?.text = "  ${notifManager.capsuleBgColorHex.value}"
        buyerNotifCapsuleAlphaText?.text = "👻 Transparansi Kapsul: ${(notifManager.capsuleAlpha.value * 100).toInt()}%"
        buyerNotifCapsuleOutlineText?.text = "🖼️ Outline Kapsul: ${notifManager.capsuleOutlineWidth.value}dp (${notifManager.capsuleOutlineColor.value})"
        buyerNotifFontAndIconInfoText?.text = "Ukuran Font: ${notifManager.fontSizeSp.value}sp | Ukuran Keranjang: ${notifManager.cartIconSizeDp.value}dp"
        stikerOutlineInfoText?.text = "Tebal: ${stikerManager.outlineWidth.value}dp | Warna: ${stikerManager.outlineColorHex.value}"

        val curCapsuleColor = notifManager.capsuleBgColorHex.value
        capsuleColorChipsList.forEach { (hex, chipTv) ->
            val isMatched = hex.equals(curCapsuleColor, ignoreCase = true)
            chipTv.apply {
                setTextColor(if (isMatched) (if (hex == "#FFFFFF") Color.BLACK else Color.WHITE) else Color.parseColor("#CBD5E1"))
                setTypeface(null, if (isMatched) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor(hex))
                    setStroke(dp(if (isMatched) 2 else 1), if (isMatched) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
                }
            }
        }

        val curAlpha = notifManager.capsuleAlpha.value
        capsuleAlphaChipsList.forEach { (a, chipTv) ->
            styleStandardChip(chipTv, Math.abs(a - curAlpha) < 0.04f, "#F97316", "#FFFFFF")
        }

        val curOutlineW = notifManager.capsuleOutlineWidth.value
        capsuleOutlineWidthChipsList.forEach { (w, chipTv) ->
            styleStandardChip(chipTv, w == curOutlineW, "#F97316", "#FFFFFF")
        }

        val curOutlineCol = notifManager.capsuleOutlineColor.value
        capsuleOutlineColorChipsList.forEach { (hex, chipTv) ->
            val isMatched = hex.equals(curOutlineCol, ignoreCase = true)
            chipTv.apply {
                setTextColor(if (hex == "#FFFFFF") Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor(hex))
                    setStroke(dp(if (isMatched) 2 else 1), if (isMatched) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
                }
            }
        }

        val curFontSp = notifManager.fontSizeSp.value
        notifFontSizeChipsList.forEach { (sp, chipTv) ->
            styleStandardChip(chipTv, Math.abs(sp - curFontSp) < 0.2f, "#38BDF8", "#FFFFFF")
        }

        val curCartDp = notifManager.cartIconSizeDp.value
        notifCartSizeChipsList.forEach { (dpVal, chipTv) ->
            styleStandardChip(chipTv, Math.abs(dpVal - curCartDp) < 0.5f, "#38BDF8", "#FFFFFF")
        }

        val curStikerOutlineW = stikerManager.outlineWidth.value
        stikerOutlineWidthChipsList.forEach { (w, chipTv) ->
            styleStandardChip(chipTv, w == curStikerOutlineW, "#818CF8", "#FFFFFF")
        }

        val curStikerOutlineCol = stikerManager.outlineColorHex.value
        stikerOutlineColorChipsList.forEach { (hex, chipTv) ->
            val isMatched = hex.equals(curStikerOutlineCol, ignoreCase = true)
            chipTv.apply {
                setTextColor(if (hex == "#FFFFFF") Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor(hex))
                    setStroke(dp(if (isMatched) 2 else 1), if (isMatched) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
                }
            }
        }
    }

    fun populateDedicatedLogTab(
        service: FloatingControlService,
        tabContentHolder: LinearLayout,
        dp: (Int) -> Int
    ) = with(service) {
        val tabLogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 0, 0, dp(4))
        }
        containerTabLog = tabLogContainer

        // Top Bar: Title + Full-Screen Floating Toggle ("⛶ Full Layar" / "🗗 Ukuran Normal") + Saklar Log ON/OFF
        val topHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        val logBadge = TextView(this).apply {
            text = "🟢 0/100"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            setPadding(dp(5), dp(3), dp(5), dp(3))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#064E3B"))
            }
        }
        floatingLogCountBadge = logBadge
        topHeaderRow.addView(logBadge)

        val logSwitch = TextView(this).apply {
            val isEnabled = com.tajasb.debug.VcamDebugLogger.isLoggingEnabled.value
            text = if (isEnabled) "🟢 Saklar: ON" else "⚪ Saklar: OFF"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isEnabled) Color.WHITE else Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                if (isEnabled) {
                    setColor(Color.parseColor("#064E3B"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                } else {
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#64748B"))
                }
            }
            setOnClickListener {
                val next = com.tajasb.debug.VcamDebugLogger.toggleLogging()
                updateLogSwitchUi(next)
                floatingLogCountBadge?.text = if (next) "🟢 ${com.tajasb.debug.VcamDebugLogger.logsFlow.value.size}/100" else "⚪ OFF"
                if (!next) {
                    floatingLogPreviewText?.text = "(Saklar Log OFF - Pencatatan dinonaktifkan)"
                } else {
                    updateFloatingLogUi(com.tajasb.debug.VcamDebugLogger.logsFlow.value)
                }
                Toast.makeText(applicationContext, if (next) "Saklar Log: ON 🟢" else "Saklar Log: OFF ⚪", Toast.LENGTH_SHORT).show()
            }
        }
        btnLogSwitch = logSwitch
        topHeaderRow.addView(logSwitch)

        val btnFullToggle = TextView(this).apply {
            text = if (isFloatingLogFullScreen) "🗗 Ukuran Normal" else "⛶ Full Layar"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(4), dp(7), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#083344"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                toggleFloatingLogFullScreen()
            }
        }
        btnToggleFloatingLogFullScreen = btnFullToggle
        topHeaderRow.addView(btnFullToggle)
        tabLogContainer.addView(topHeaderRow)

        // Sub-Mode Switcher: 🖥️ Log Sistem vs 💬 Scan Chat
        val subModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }

        val sysContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
        }
        containerFloatingLogSystem = sysContainer

        val scanContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerFloatingLogScan = scanContainer

        fun updateSubModeUi() {
            val isSys = (floatingLogSubMode == "SYSTEM")
            sysContainer.visibility = if (isSys) View.VISIBLE else View.GONE
            scanContainer.visibility = if (!isSys) View.VISIBLE else View.GONE
            btnFloatingLogModeSystem?.apply {
                setTextColor(if (isSys) Color.WHITE else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isSys) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(5).toFloat()
                    if (isSys) {
                        setColor(Color.parseColor("#083344"))
                        setStroke(dp(1), Color.parseColor("#00E5FF"))
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                }
            }
            btnFloatingLogModeScan?.apply {
                setTextColor(if (!isSys) Color.WHITE else Color.parseColor("#94A3B8"))
                setTypeface(null, if (!isSys) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(5).toFloat()
                    if (!isSys) {
                        setColor(Color.parseColor("#064E3B"))
                        setStroke(dp(1), Color.parseColor("#10B981"))
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                }
            }
        }

        btnFloatingLogModeSystem = TextView(this).apply {
            text = "🖥️ Log Sistem"
            textSize = 9f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            layoutParams = lp
            setOnClickListener {
                floatingLogSubMode = "SYSTEM"
                updateSubModeUi()
            }
        }
        subModeRow.addView(btnFloatingLogModeSystem)

        btnFloatingLogModeScan = TextView(this).apply {
            text = "💬 Log Scan Chat"
            textSize = 9f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
            }
            layoutParams = lp
            setOnClickListener {
                floatingLogSubMode = "SCAN"
                isShowingScanLog = true
                updateSubModeUi()
                val entries = com.tajasb.livecomment.CommentScanInspector.getInstance(this@with).entriesFlow.value
                updateScanInspectorCounters()
                updateScanInspectorFeed(entries)
            }
        }
        subModeRow.addView(btnFloatingLogModeScan)
        tabLogContainer.addView(subModeRow)

        // 1. SYSTEM LOG VIEW (Unobstructed, No Card Wrapper, Full-Bleed)
        val logActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }

        val btnCopy50 = TextView(this).apply {
            text = "📋 Salin 50"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(2) }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#083344"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                val (count, ok) = com.tajasb.debug.VcamDebugLogger.copyLogsToClipboard(applicationContext, limit = 50)
                Toast.makeText(applicationContext, if (ok) "Disalin $count log! 📋" else "Gagal salin", Toast.LENGTH_SHORT).show()
            }
        }
        logActionRow.addView(btnCopy50)

        val btnCopy100 = TextView(this).apply {
            text = "📋 Semua"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(2) }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E1B4B"))
                setStroke(dp(1), Color.parseColor("#818CF8"))
            }
            setOnClickListener {
                val (count, ok) = com.tajasb.debug.VcamDebugLogger.copyLogsToClipboard(applicationContext, limit = 100)
                Toast.makeText(applicationContext, if (ok) "Disalin $count log! 📋" else "Gagal salin", Toast.LENGTH_SHORT).show()
            }
        }
        logActionRow.addView(btnCopy100)

        val btnProbe = TextView(this).apply {
            text = "⚡ Probe"
            textSize = 8.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f).apply { marginEnd = dp(2) }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
            }
            setOnClickListener {
                com.tajasb.debug.VcamDebugLogger.i("LiveProbe", "⚡ Probe Floating: VCam OK (FPS: ${repository.config.value.fps})")
                Toast.makeText(applicationContext, "Probe dikirim! ⚡", Toast.LENGTH_SHORT).show()
            }
        }
        logActionRow.addView(btnProbe)

        var isConfirmingClearLogs = false
        var resetClearLogsRunnable: Runnable? = null
        val setBtnClearLogs = TextView(this).apply {
            text = "🧹 Hapus"
            textSize = 8.5f
            setTextColor(Color.parseColor("#F87171"))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f)
            layoutParams = lp
            val defaultBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#3B0712"))
            }
            background = defaultBg
            fun resetToIdle() {
                isConfirmingClearLogs = false
                text = "🧹 Hapus"
                setTextColor(Color.parseColor("#F87171"))
                background = defaultBg
            }
            setOnClickListener {
                if (!isConfirmingClearLogs) {
                    isConfirmingClearLogs = true
                    text = "⚠️ Yakin?"
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        setColor(Color.parseColor("#DC2626"))
                    }
                    resetClearLogsRunnable?.let { removeCallbacks(it) }
                    resetClearLogsRunnable = Runnable { resetToIdle() }
                    postDelayed(resetClearLogsRunnable, 3500)
                } else {
                    resetClearLogsRunnable?.let { removeCallbacks(it) }
                    resetToIdle()
                    com.tajasb.debug.VcamDebugLogger.clearLogs()
                    Toast.makeText(applicationContext, "Log dibersihkan! 🧹", Toast.LENGTH_SHORT).show()
                }
            }
        }
        logActionRow.addView(setBtnClearLogs)
        sysContainer.addView(logActionRow)

        // Full-bleed terminal view — NOT trapped inside a small card or 5-line cap!
        val logBox = TextView(this).apply {
            text = "(Memuat log real-time...)"
            textSize = 8.5f
            setTypeface(Typeface.MONOSPACE)
            setTextColor(Color.parseColor("#38BDF8"))
            minLines = 12
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#020617"))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        floatingLogPreviewText = logBox
        sysContainer.addView(logBox)
        tabLogContainer.addView(sysContainer)

        // 2. SCAN CHAT LOG VIEW (Unobstructed, No Card Wrapper)
        val scanActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        val scanShortcutCount = TextView(this).apply {
            val count = com.tajasb.livecomment.CommentScanRecorder.getInstance(this@with).scannedCount.value
            text = "📝 $count Chat"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(dp(5), dp(4), dp(5), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0C4A6E"))
            }
        }
        scanRecordCountBadgeSettings = scanShortcutCount
        scanActionRow.addView(scanShortcutCount)

        val btnToggleScanSettings = TextView(this).apply {
            val isRec = com.tajasb.livecomment.CommentScanRecorder.getInstance(this@with).isRecording.value
            text = if (isRec) "🔴 REKAM: ON" else "▶️ REKAM: OFF"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(if (isRec) Color.parseColor("#DC2626") else Color.parseColor("#047857"))
                setStroke(dp(1), if (isRec) Color.parseColor("#EF4444") else Color.parseColor("#10B981"))
            }
            setOnClickListener {
                val scanRec = com.tajasb.livecomment.CommentScanRecorder.getInstance(this@with)
                val next = scanRec.toggleRecording()
                Toast.makeText(applicationContext, if (next) "Rekam Scan: ON 🟢" else "Rekam Scan: OFF ⏹️", Toast.LENGTH_SHORT).show()
                updateScanRecorderUi()
            }
        }
        btnToggleScanRecordTextSettings = btnToggleScanSettings
        scanActionRow.addView(btnToggleScanSettings)

        val btnSaveScanSettings = TextView(this).apply {
            text = "💾 Simpan"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f)
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#0284C7"))
            }
            setOnClickListener {
                val scanRec = com.tajasb.livecomment.CommentScanRecorder.getInstance(this@with)
                val res = scanRec.exportToDownloadScanFolder()
                if (res.success) {
                    Toast.makeText(applicationContext, "✅ Disimpan ${res.totalComments} chat:\n${res.filePath}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(applicationContext, "⚠️ ${res.errorMessage ?: "Gagal simpan"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        scanActionRow.addView(btnSaveScanSettings)
        scanContainer.addView(scanActionRow)

        val scanLiveFeed = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scanLogContainerList = scanLiveFeed
        scanContainer.addView(scanLiveFeed)

        tabLogContainer.addView(scanContainer)
        updateSubModeUi()
        tabContentHolder.addView(tabLogContainer)
    }

    fun populateSettingsLogAndFooterCards(
        service: FloatingControlService,
        settingsContent: LinearLayout,
        panel: LinearLayout,
        dp: (Int) -> Int
    ) = with(service) {

        val dcimCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val btnSyncDcim = TextView(this).apply {
            text = "📁 Sinkronkan Video ke DCIM / Camera"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setOnClickListener {
                serviceScope.launch {
                    try {
                        repository.writeHookConfigFiles(repository.config.value)
                        repository.syncToTargetApps(repository.config.value.targetPackages)
                        repository.broadcastControlEvent(
                            action = "RELOAD_MEDIA",
                            videoPath = repository.config.value.videoPath,
                            extraSource = "SYNC_FLOATING",
                            extraPurgeCache = true
                        )
                        Toast.makeText(applicationContext, "File video & config disinkronkan ke DCIM! 📁", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(applicationContext, "Gagal sinkron: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dcimCard.addView(btnSyncDcim)
        settingsContent.addView(dcimCard)

        val a11yCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val a11yBtn = TextView(this).apply {
            val isEnabledA11y = com.tajasb.VcamAccessibilityService.isAccessibilityEnabled(this@with)
            text = if (isEnabledA11y) "👆 Aksesibilitas: AKTIF ✅" else "👆 Aksesibilitas: PERLU IZIN ⚠️"
            textSize = 9.5f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    Toast.makeText(applicationContext, "Buka menu Layanan Terdownload -> Tajasb", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, "Buka Pengaturan -> Aksesibilitas", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnA11yStatusSettingsText = a11yBtn
        a11yCard.addView(a11yBtn)
        settingsContent.addView(a11yCard)

        val transCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val initialPct = (repository.config.value.floatingAlpha * 100).toInt().coerceIn(50, 100)
        val transLbl = TextView(this).apply {
            text = "Transparansi Menu: $initialPct% (Batas Min: 50%)"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
        }
        transparencyLabel = transLbl
        transCard.addView(transLbl)

        val transSb = SeekBar(this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                min = 50
            }
            max = 100
            val initialProg = (repository.config.value.floatingAlpha * 100).toInt().coerceIn(50, 100)
            progress = initialProg
            isEnabled = true
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val clampedProg = progress.coerceIn(50, 100)
                    if (progress < 50) {
                        seekBar?.progress = 50
                    }
                    val alphaVal = clampedProg / 100f
                    transLbl.text = "Transparansi Menu: $clampedProg% (Batas Min: 50%)"
                    panel.alpha = alphaVal
                    panelLayoutParams?.alpha = alphaVal
                    if (panel.windowToken != null && panelLayoutParams != null) {
                        try { windowManager.updateViewLayout(panel, panelLayoutParams) } catch (_: Exception) {}
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val progress = (seekBar?.progress ?: 85).coerceIn(50, 100)
                    val alphaVal = progress / 100f
                    serviceScope.launch { repository.setFloatingAlpha(alphaVal) }
                }
            })
        }
        transparencySeekBar = transSb
        transCard.addView(transSb)
        settingsContent.addView(transCard)

        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(4))
        }

        val btnOpenMain = TextView(this).apply {
            text = "📱 Buka APK Utama"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(2) }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
        }
        navRow.addView(btnOpenMain)

        var isConfirmingStopService = false
        var resetStopServiceRunnable: Runnable? = null

        val btnStopService = TextView(this).apply {
            text = "🛑 Tutup Floating"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F87171"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(2) }
            layoutParams = lp
            val defaultBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#450A0A"))
                setStroke(dp(1), Color.parseColor("#EF4444"))
            }
            background = defaultBg

            fun resetToIdle() {
                isConfirmingStopService = false
                text = "🛑 Tutup Floating"
                setTextColor(Color.parseColor("#F87171"))
                background = defaultBg
            }

            setOnClickListener {
                if (!isConfirmingStopService) {
                    isConfirmingStopService = true
                    text = "⚠️ Yakin Tutup? (Klik Lagi)"
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(6).toFloat()
                        setColor(Color.parseColor("#DC2626"))
                    }
                    Toast.makeText(applicationContext, "⚠️ Klik sekali lagi untuk menutup floating (batal dalam 3.5 detik)", Toast.LENGTH_SHORT).show()
                    resetStopServiceRunnable?.let { removeCallbacks(it) }
                    resetStopServiceRunnable = Runnable { resetToIdle() }
                    postDelayed(resetStopServiceRunnable, 3500)
                } else {
                    resetStopServiceRunnable?.let { removeCallbacks(it) }
                    resetToIdle()
                    Toast.makeText(applicationContext, "Layanan Floating ditutup 🛑", Toast.LENGTH_SHORT).show()
                    closeFloatingService()
                }
            }
        }
        navRow.addView(btnStopService)
        settingsContent.addView(navRow)
    }
}
