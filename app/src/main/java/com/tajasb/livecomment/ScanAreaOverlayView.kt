@file:Suppress("DEPRECATION")

package com.tajasb.livecomment

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * ScanAreaOverlayView:
 * Floating, resizable and draggable crop box that appears on screen directly over live streaming apps.
 * Allows the user to visually position and resize the exact comment scanning area.
 */
class ScanAreaOverlayView(
    context: Context,
    private val windowManager: WindowManager,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val liveCommentManager = LiveCommentManager.getInstance(context)
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).toInt()

    private val screenWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    private val screenHeight = context.resources.displayMetrics.heightPixels.coerceAtLeast(1)

    private val layoutParams: WindowManager.LayoutParams
    private val infoLabel: TextView

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var initialWidth = 0
    private var initialHeight = 0

    init {
        // Calculate initial box dimensions from current LiveCommentManager values
        val left = (liveCommentManager.scanLeftPct.value * screenWidth).toInt().coerceIn(0, (screenWidth * 0.7f).toInt())
        val top = (liveCommentManager.scanTopPct.value * screenHeight).toInt().coerceIn(0, (screenHeight * 0.8f).toInt())
        val right = (liveCommentManager.scanRightPct.value * screenWidth).toInt().coerceIn(left + dp(120), screenWidth)
        val bottom = (liveCommentManager.scanBottomPct.value * screenHeight).toInt().coerceIn(top + dp(120), screenHeight)

        val boxWidth = (right - left).coerceIn(dp(160), screenWidth)
        val boxHeight = (bottom - top).coerceIn(dp(140), screenHeight)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            boxWidth,
            boxHeight,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = left
            y = top
        }

        // Main Box Container
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor("#4D0284C7")) // 30% translucent ocean blue
            setStroke(dp(2.5f.toInt()), Color.parseColor("#00E5FF")) // Glowing Neon Cyan Border
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        // 1. Header Bar (Drag to move)
        val headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#D90F172A"))
            }
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        val title = TextView(context).apply {
            text = "✥ AREA BACA KOMENTAR"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerBar.addView(title)

        val btnClose = TextView(context).apply {
            text = "✕"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                dismiss()
            }
        }
        headerBar.addView(btnClose)
        rootLayout.addView(headerBar)

        // Setup drag to move on header
        headerBar.setOnTouchListener { _, event ->
            handleMoveTouch(event)
        }

        // 2. Middle Spacer (Shows clear live comments underneath)
        val middleSpace = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            // Touching middle space also moves the box
            setOnTouchListener { _, event ->
                handleMoveTouch(event)
            }
        }
        rootLayout.addView(middleSpace)

        // 3. Info Badge
        infoLabel = TextView(context).apply {
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#CC0F172A"))
            }
            setPadding(dp(6), dp(3), dp(6), dp(3))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
        }
        rootLayout.addView(infoLabel)

        // 4. Quick Size Adjust Bar
        val resizeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        fun createSizeBtn(label: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                text = label
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
                setPadding(dp(2), dp(4), dp(2), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                setOnClickListener { onClick() }
            }
        }

        resizeRow.addView(createSizeBtn("➕ T") { adjustSize(0, dp(35)) })
        resizeRow.addView(createSizeBtn("➖ T") { adjustSize(0, -dp(35)) })
        resizeRow.addView(createSizeBtn("➕ L") { adjustSize(dp(35), 0) })
        resizeRow.addView(createSizeBtn("➖ L") { adjustSize(-dp(35), 0) })
        rootLayout.addView(resizeRow)

        // 5. Action Row: [ SIMPAN AREA ] & Corner Resize Handle
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnSave = TextView(context).apply {
            text = "✅ SIMPAN AREA INI"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#10B981")) // Emerald Green
            }
            setPadding(dp(8), dp(7), dp(8), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                saveAndDismiss()
            }
        }
        actionRow.addView(btnSave)

        // Corner Resize Grip Handle
        val cornerGrip = TextView(context).apply {
            text = " 📐 "
            textSize = 13f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0284C7"))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(6)
            }
            layoutParams = lp
        }
        setupCornerResize(cornerGrip)
        actionRow.addView(cornerGrip)

        rootLayout.addView(actionRow)
        addView(rootLayout)

        updateInfoText()
    }

    private fun handleMoveTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                layoutParams.x = (initialX + dx).coerceIn(0, screenWidth - layoutParams.width)
                layoutParams.y = (initialY + dy).coerceIn(0, screenHeight - layoutParams.height)
                try {
                    windowManager.updateViewLayout(this, layoutParams)
                } catch (_: Exception) {}
                updateInfoText()
                return true
            }
        }
        return false
    }

    private fun setupCornerResize(view: View) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = layoutParams.width
                    initialHeight = layoutParams.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    val newW = (initialWidth + dx).coerceIn(dp(140), screenWidth - layoutParams.x)
                    val newH = (initialHeight + dy).coerceIn(dp(120), screenHeight - layoutParams.y)

                    layoutParams.width = newW
                    layoutParams.height = newH
                    try {
                        windowManager.updateViewLayout(this, layoutParams)
                    } catch (_: Exception) {}
                    updateInfoText()
                    true
                }
                else -> false
            }
        }
    }

    private fun adjustSize(deltaW: Int, deltaH: Int) {
        val newW = (layoutParams.width + deltaW).coerceIn(dp(140), screenWidth - layoutParams.x)
        val newH = (layoutParams.height + deltaH).coerceIn(dp(120), screenHeight - layoutParams.y)
        layoutParams.width = newW
        layoutParams.height = newH
        try {
            windowManager.updateViewLayout(this, layoutParams)
        } catch (_: Exception) {}
        updateInfoText()
    }

    private fun updateInfoText() {
        val topPct = ((layoutParams.y.toFloat() / screenHeight) * 100).toInt()
        val bottomPct = (((layoutParams.y + layoutParams.height).toFloat() / screenHeight) * 100).toInt()
        val leftPct = ((layoutParams.x.toFloat() / screenWidth) * 100).toInt()
        val rightPct = (((layoutParams.x + layoutParams.width).toFloat() / screenWidth) * 100).toInt()

        infoLabel.text = "📍 Area: Atas $topPct% - Bawah $bottomPct% | Kiri $leftPct% - Kanan $rightPct%"
    }

    fun show() {
        try {
            windowManager.addView(this, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismiss() {
        try {
            if (windowToken != null) {
                windowManager.removeView(this)
            }
        } catch (_: Exception) {}
        onDismiss()
    }

    private fun saveAndDismiss() {
        val leftPct = (layoutParams.x.toFloat() / screenWidth).coerceIn(0.0f, 0.90f)
        val topPct = (layoutParams.y.toFloat() / screenHeight).coerceIn(0.0f, 0.90f)
        val rightPct = ((layoutParams.x + layoutParams.width).toFloat() / screenWidth).coerceIn(leftPct + 0.05f, 1.0f)
        val bottomPct = ((layoutParams.y + layoutParams.height).toFloat() / screenHeight).coerceIn(topPct + 0.05f, 1.0f)

        liveCommentManager.setCustomScanArea(topPct, bottomPct, leftPct, rightPct, updatePreset = true)
        liveCommentManager.setAreaFilterEnabled(true)

        val topInt = (topPct * 100).toInt()
        val botInt = (bottomPct * 100).toInt()
        Toast.makeText(context, "🎯 Area scan komentar disimpan: $topInt% - $botInt%! Layanan sekarang hanya membaca area ini.", Toast.LENGTH_LONG).show()

        dismiss()
    }
}
