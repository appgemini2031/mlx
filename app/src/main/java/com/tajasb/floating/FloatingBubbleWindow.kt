@file:Suppress("DEPRECATION")

package com.tajasb.floating

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity
import com.tajasb.FloatingControlService
import com.tajasb.FloatingTab
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * FloatingBubbleWindow:
 * Modul terpisah untuk ikon mengambang (minimized bar), indikator aktif pada bar,
 * drag-and-drop bubble, pointer Auto-Tap (crosshair), serta kotak area scan komentar live.
 */
object FloatingBubbleWindow {

    fun updateBubbleIconsStatus(service: FloatingControlService) = with(service) {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val cfg = repository.config.value
        val isMusicPlaying = repository.musicPlayer.isPlaying.value
        val isVideoActive = cfg.isMasterEnabled && cfg.videoPath.isNotBlank() && !cfg.isVideoPaused

        val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)
        val buyerNotif = com.tajasb.notif.BuyerNotificationManager.getInstance(this)
        val stikerMgr = com.tajasb.stiker.CustomStikerManager.getInstance(this)
        val watermarkMgr = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)
        val autoTimeMgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(this)
        val scanRec = com.tajasb.livecomment.CommentScanRecorder.getInstance(this)
        val isPinActive = liveComment.isCaptureActive.value || buyerNotif.isEnabled.value || stikerMgr.isEnabled.value || watermarkMgr.isEnabled.value || autoTimeMgr.isEnabled.value || scanRec.isRecording.value

        val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
        val isTapActive = autoTap.isTapping.value

        fun styleIndicator(btn: TextView?, active: Boolean, activeEmoji: String, inactiveEmoji: String = activeEmoji) {
            btn?.apply {
                text = if (active) " $activeEmoji " else " $inactiveEmoji "
                setTextColor(if (active) Color.WHITE else Color.parseColor("#94A3B8"))
                alpha = if (active) 1.0f else 0.65f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    if (active) {
                        setColor(Color.parseColor("#064E3B"))
                        setStroke(dp(2), Color.parseColor("#10B981"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#334155"))
                    }
                }
            }
        }

        // 1. Video Media (🎬 / ⏸️)
        val showVideo = cfg.isMasterEnabled && cfg.videoPath.isNotBlank()
        bubbleBtnVideo?.visibility = if (showVideo) View.VISIBLE else View.GONE
        spacerVideo?.visibility = if (showVideo) View.VISIBLE else View.GONE
        if (showVideo) {
            styleIndicator(bubbleBtnVideo, isVideoActive, "🎬", "⏸️")
        }

        // 2. MP3 Audio (🎵 / ⏸️)
        val showMusic = cfg.enableMp3MusicModule && (
            cfg.audioPlaylist.isNotEmpty() ||
            cfg.audioPath.isNotBlank() ||
            repository.musicPlayer.currentTrack.value != null
        )
        bubbleBtnMusic?.visibility = if (showMusic) View.VISIBLE else View.GONE
        spacerMusic?.visibility = if (showMusic) View.VISIBLE else View.GONE
        if (showMusic) {
            styleIndicator(bubbleBtnMusic, isMusicPlaying, "🎵", "⏸️")
        }

        // 3. Media Audio (🔊) - Disembunyikan saat minimize agar menghemat ruang layar
        bubbleBtnMediaAudio?.visibility = View.GONE
        spacerMediaAudio?.visibility = View.GONE

        // 4. Pin / Chat Overlay (💬)
        bubbleBtnPin?.visibility = if (isPinActive) View.VISIBLE else View.GONE
        spacerPin?.visibility = if (isPinActive) View.VISIBLE else View.GONE
        if (isPinActive) {
            val emoji = if (scanRec.isRecording.value) "🔴" else "💬"
            styleIndicator(bubbleBtnPin, true, emoji)
        }

        // 5. Auto-Tap / Macro Quick Toggle (👆)
        // Hanya tampil berupa ikon saja saat Auto-Tap sedang aktif berjalan (tidak tampil saat posisi pause/mati)
        val showTap = cfg.enableAutoTapModule && isTapActive
        bubbleBtnTap?.visibility = if (showTap) View.VISIBLE else View.GONE
        spacerTap?.visibility = if (showTap) View.VISIBLE else View.GONE
        if (showTap) {
            styleIndicator(bubbleBtnTap, true, "👆")
        }
    }

    fun createBubbleView(service: FloatingControlService) = with(service) {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val wmBubbleParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }
        bubbleLayoutParams = wmBubbleParams

        // Minimized 4-Button Mini Bar:
        // Seluruh area bar saat minimize dapat digeser (drag) dengan mulus tanpa mengganggu klik tombol
        val touchSlopPx = dp(6)
        val barRoot = object : LinearLayout(this) {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDraggingBar = false

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = wmBubbleParams.x
                        initialY = wmBubbleParams.y
                        initialTouchX = ev.rawX
                        initialTouchY = ev.rawY
                        isDraggingBar = false
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (ev.rawX - initialTouchX).toInt()
                        val dy = (ev.rawY - initialTouchY).toInt()
                        if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                            isDraggingBar = true
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDraggingBar = false
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = wmBubbleParams.x
                        initialY = wmBubbleParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDraggingBar = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (isDraggingBar || abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                            isDraggingBar = true
                            wmBubbleParams.x = initialX + dx
                            wmBubbleParams.y = initialY + dy
                            if (windowToken != null) {
                                try {
                                    windowManager.updateViewLayout(this, wmBubbleParams)
                                } catch (_: Exception) {}
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDraggingBar = false
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#E60B1120"))
                setStroke(dp(1.5f.toInt()), Color.parseColor("#00E5FF"))
            }
            background = bg
            setPadding(dp(6), dp(5), dp(6), dp(5))
            elevation = dp(10).toFloat()
        }

        // Drag Grip (⋮⋮)
        val dragGrip = TextView(this).apply {
            text = "⋮⋮"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(2), 0, dp(4), 0)
        }
        barRoot.addView(dragGrip)

        // 1. Tombol Menuju APK Tajasb (📱)
        val btnOpenApp = TextView(this).apply {
            text = " 📱 "
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
            }
            setOnClickListener {
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
        }
        barRoot.addView(btnOpenApp)

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(4), 1) }
        barRoot.addView(spacer1)

        // 2. Tombol Menuju Target App (🚀)
        val btnLaunchTarget = TextView(this).apply {
            text = " 🚀 "
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#083344"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                launchTargetApp(showChooser = false)
                serviceScope.launch {
                    try {
                        repository.triggerTurboRamClean()
                        val reloadIntent = Intent("com.tajasb.action.RELOAD_MEDIA")
                        sendBroadcast(reloadIntent)
                    } catch (_: Throwable) {}
                }
            }
            setOnLongClickListener {
                launchTargetApp(showChooser = true)
                true
            }
        }
        barRoot.addView(btnLaunchTarget)

        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(4), 1) }
        barRoot.addView(spacer2)

        // Feature Active Indicators & Quick Controls
        val bBtnVideo = TextView(this).apply {
            text = " 🎬 "
            textSize = 11.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(4), dp(5), dp(4))
            setOnClickListener {
                serviceScope.launch {
                    val paused = repository.togglePauseVideo()
                    Toast.makeText(applicationContext, if (paused) "Video Live: Dijeda ⏸️" else "Video Live: Dilanjutkan ▶️", Toast.LENGTH_SHORT).show()
                    updateBubbleIconsStatus()
                }
            }
            setOnLongClickListener {
                showExpandedPanel()
                selectTab(FloatingTab.VIDEO)
                true
            }
        }
        bubbleBtnVideo = bBtnVideo
        barRoot.addView(bBtnVideo)

        val spacerV = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(3), 1) }
        spacerVideo = spacerV
        barRoot.addView(spacerV)

        val bBtnMusic = TextView(this).apply {
            text = " 🎵 "
            textSize = 11.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(4), dp(5), dp(4))
            setOnClickListener {
                serviceScope.launch {
                    val paused = repository.toggleMp3Audio()
                    Toast.makeText(applicationContext, if (paused) "MP3 Audio: Dijeda ⏸️" else "MP3 Audio: Dilanjutkan ▶️", Toast.LENGTH_SHORT).show()
                    updateBubbleIconsStatus()
                }
            }
            setOnLongClickListener {
                showExpandedPanel()
                selectTab(FloatingTab.MUSIC)
                true
            }
        }
        bubbleBtnMusic = bBtnMusic
        barRoot.addView(bBtnMusic)

        val spacerM = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(3), 1) }
        spacerMusic = spacerM
        barRoot.addView(spacerM)

        val bBtnMediaAudio = TextView(this).apply {
            text = " 🔊 "
            textSize = 11.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(4), dp(5), dp(4))
            setOnClickListener {
                serviceScope.launch {
                    val muted = repository.toggleAllVideosMute()
                    Toast.makeText(applicationContext, if (muted) "Audio Media: Senyap 🔇" else "Audio Media: Aktif 🔊", Toast.LENGTH_SHORT).show()
                    updateBubbleIconsStatus()
                }
            }
            setOnLongClickListener {
                showExpandedPanel()
                selectTab(FloatingTab.VIDEO)
                true
            }
        }
        bubbleBtnMediaAudio = bBtnMediaAudio
        barRoot.addView(bBtnMediaAudio)

        val spacerA = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(3), 1) }
        spacerMediaAudio = spacerA
        barRoot.addView(spacerA)

        val bBtnPin = TextView(this).apply {
            text = " 💬 "
            textSize = 11.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(4), dp(5), dp(4))
            setOnClickListener {
                val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(applicationContext)
                val buyerNotif = com.tajasb.notif.BuyerNotificationManager.getInstance(applicationContext)
                val stikerMgr = com.tajasb.stiker.CustomStikerManager.getInstance(applicationContext)
                val watermarkMgr = com.tajasb.stiker.CustomWatermarkManager.getInstance(applicationContext)
                val autoTimeMgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(applicationContext)
                val scanRec = com.tajasb.livecomment.CommentScanRecorder.getInstance(applicationContext)
                val anyActive = liveComment.isCaptureActive.value || buyerNotif.isEnabled.value || stikerMgr.isEnabled.value || watermarkMgr.isEnabled.value || autoTimeMgr.isEnabled.value || scanRec.isRecording.value
                Toast.makeText(applicationContext, if (anyActive) "Scan Komentar & Pin: AKTIF 🟢" else "Scan Komentar & Pin 💬", Toast.LENGTH_SHORT).show()
                showExpandedPanel()
                selectTab(FloatingTab.NOTIF)
            }
            setOnLongClickListener {
                showExpandedPanel()
                selectTab(FloatingTab.NOTIF)
                true
            }
        }
        bubbleBtnPin = bBtnPin
        barRoot.addView(bBtnPin)

        val spacerP = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(3), 1) }
        spacerPin = spacerP
        barRoot.addView(spacerP)

        val bBtnTap = TextView(this).apply {
            text = " 👆 "
            textSize = 11.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(4), dp(5), dp(4))
            setOnClickListener {
                val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(applicationContext)
                val started = autoTap.toggleTapping()
                val msg = if (started) {
                    "Auto-Tap: Dilanjutkan (${autoTap.getResumeSummaryShort()}) 👆"
                } else {
                    "Auto-Tap: Dijeda • Giliran disimpan (${autoTap.getResumeSummaryShort()}) ⏸️"
                }
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                updateAutoTapUi()
                onRefreshTapMarksUi?.invoke()
            }
            setOnLongClickListener {
                showExpandedPanel()
                selectTab(FloatingTab.TAP)
                true
            }
        }
        bubbleBtnTap = bBtnTap
        barRoot.addView(bBtnTap)

        val spacerT = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(3), 1) }
        spacerTap = spacerT
        barRoot.addView(spacerT)

        // 3. Tombol Minimize/Expand (+)
        val btnExpand = TextView(this).apply {
            text = " ➕ "
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0284C7"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                showExpandedPanel()
            }
        }
        barRoot.addView(btnExpand)

        val spacer3 = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(4), 1) }
        barRoot.addView(spacer3)

        // 4. Tombol Close Floating (🛑) dengan konfirmasi ganda anti-senggol
        var isConfirmingBubbleClose = false
        var resetBubbleCloseRunnable: Runnable? = null

        val btnClose = TextView(this).apply {
            text = " 🛑 "
            textSize = 12f
            setTextColor(Color.parseColor("#F87171"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val defaultBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#450A0A"))
                setStroke(dp(1), Color.parseColor("#EF4444"))
            }
            background = defaultBg

            fun resetToIdle() {
                isConfirmingBubbleClose = false
                text = " 🛑 "
                setTextColor(Color.parseColor("#F87171"))
                background = defaultBg
            }

            setOnClickListener {
                if (!isConfirmingBubbleClose) {
                    isConfirmingBubbleClose = true
                    text = " ⚠️ Tutup? "
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(6).toFloat()
                        setColor(Color.parseColor("#DC2626"))
                    }
                    Toast.makeText(applicationContext, "⚠️ Klik sekali lagi untuk menutup floating (batal dalam 3.5 detik)", Toast.LENGTH_SHORT).show()
                    resetBubbleCloseRunnable?.let { removeCallbacks(it) }
                    resetBubbleCloseRunnable = Runnable { resetToIdle() }
                    postDelayed(resetBubbleCloseRunnable, 3500)
                } else {
                    resetBubbleCloseRunnable?.let { removeCallbacks(it) }
                    resetToIdle()
                    Toast.makeText(applicationContext, "Layanan Floating ditutup 🛑", Toast.LENGTH_SHORT).show()
                    closeFloatingService()
                }
            }
        }
        barRoot.addView(btnClose)

        floatingBubbleView = barRoot
        updateBubbleIconsStatus()
    }

    /**
     * Memastikan jendela Floating (baik saat panel terbuka maupun saat minimize)
     * selalu berada di lapisan paling atas (di atas semua Mark Auto-Tap) agar
     * Mark tidak pernah menghalangi atau mengganggu sentuhan pada Floating.
     */
    fun bringFloatingWindowToFront(service: FloatingControlService) = with(service) {
        if (isStoppingOrDestroyed || !FloatingControlService.isRunning) return@with
        if (isPanelExpanded) {
            val panel = expandedControlPanelView ?: return@with
            val params = panelLayoutParams ?: return@with
            if (panel.windowToken != null) {
                val savedScrollY = tabContentScrollView?.scrollY ?: 0
                try {
                    windowManager.removeViewImmediate(panel)
                    addOverlayViewSafely(panel, params)
                    if (savedScrollY > 0) {
                        tabContentScrollView?.scrollTo(0, savedScrollY)
                        tabContentScrollView?.post {
                            tabContentScrollView?.scrollTo(0, savedScrollY)
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            val bubble = floatingBubbleView ?: return@with
            val params = bubbleLayoutParams ?: return@with
            if (bubble.windowToken != null) {
                try {
                    windowManager.removeViewImmediate(bubble)
                    addOverlayViewSafely(bubble, params)
                } catch (_: Exception) {}
            }
        }
    }

    fun setupBubbleDrag(service: FloatingControlService, view: View, params: WindowManager.LayoutParams) = with(service) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    if (v === floatingBubbleView) false else true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        isMoved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        if (floatingBubbleView?.windowToken != null) {
                            try {
                                windowManager.updateViewLayout(floatingBubbleView, params)
                            } catch (_: Exception) {}
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isMoved
                }
                else -> false
            }
        }
    }

    /**
     * Custom Canvas Reticle View for Professional Auto-Tap & Multi-Mark Targets.
     * Features:
     * - Transparent center bullseye (+) so the underlying app button remains clearly visible
     * - High-contrast outer halo + color-coded precision ring
     * - Directional vector arrow for SCROLL marks
     * - Step badge (#1..#N) and compact mode tag
     * - Live execution pulse ring when the mark is actively firing
     */
    private class TapTargetOverlayView(context: Context) : View(context) {
        private val density = context.resources.displayMetrics.density
        private fun dpF(v: Float): Float = v * density

        var stepNumber: Int = 1
        var topBadgeText: String = "#1"
        var bottomTagText: String = "KLIK"
        var ringColor: Int = Color.parseColor("#00E5FF")
        var isSelectedMark: Boolean = false
        var isExecutingMark: Boolean = false
        var scrollDir: com.tajasb.autotap.TapScrollDirection? = null
        var isSingleTarget: Boolean = false

        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#CC090D16")
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#E6090D16")
        }
        private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpF(1f)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
        }
        private val arrowPath = Path()
        private val pillRect = RectF()

        fun updateState(
            step: Int,
            topText: String,
            bottomText: String,
            accentColor: Int,
            selected: Boolean,
            executing: Boolean,
            scrollDirection: com.tajasb.autotap.TapScrollDirection?,
            singleMode: Boolean = false
        ) {
            stepNumber = step
            topBadgeText = topText
            bottomTagText = bottomText
            ringColor = accentColor
            isSelectedMark = selected
            isExecutingMark = executing
            scrollDir = scrollDirection
            isSingleTarget = singleMode
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val cx = w / 2f
            val cy = h / 2f
            val minDim = minOf(w, h)
            val outerRadius = (minDim / 2f) - dpF(4f)
            if (outerRadius <= dpF(6f)) return

            val activeColor = when {
                isExecutingMark -> Color.parseColor("#FDE047")
                isSelectedMark -> Color.parseColor("#38BDF8")
                else -> ringColor
            }

            // 1. Subtle translucent inner tint (keeps center transparent to see underlying app)
            fillPaint.color = if (isExecutingMark) {
                Color.parseColor("#45FDE047")
            } else {
                Color.parseColor("#24090D16")
            }
            canvas.drawCircle(cx, cy, outerRadius, fillPaint)

            // 2. Dark contrast outer halo + crisp precision ring
            val strokeW = dpF(if (isExecutingMark || isSelectedMark) 2.6f else 2.0f)
            haloPaint.strokeWidth = strokeW + dpF(2.2f)
            canvas.drawCircle(cx, cy, outerRadius, haloPaint)

            ringPaint.color = activeColor
            ringPaint.strokeWidth = strokeW
            canvas.drawCircle(cx, cy, outerRadius, ringPaint)

            // Inner dashed/secondary precision ring when selected or executing
            if (isSelectedMark || isExecutingMark) {
                ringPaint.strokeWidth = dpF(1f)
                ringPaint.alpha = 180
                canvas.drawCircle(cx, cy, outerRadius * 0.68f, ringPaint)
                ringPaint.alpha = 255
            }

            // 3. Center Crosshair (+) & Bullseye Point
            val armLen = outerRadius * 0.42f
            val gap = dpF(3.2f)
            crosshairPaint.color = Color.parseColor("#D9FFFFFF")
            crosshairPaint.strokeWidth = dpF(1.3f)
            // Horizontal arms
            canvas.drawLine(cx - armLen, cy, cx - gap, cy, crosshairPaint)
            canvas.drawLine(cx + gap, cy, cx + armLen, cy, crosshairPaint)
            // Vertical arms
            canvas.drawLine(cx, cy - armLen, cx, cy - gap, crosshairPaint)
            canvas.drawLine(cx, cy + gap, cx, cy + armLen, crosshairPaint)

            // Exact tap coordinate dot
            centerDotPaint.color = activeColor
            canvas.drawCircle(cx, cy, dpF(2.2f), centerDotPaint)

            // 4. Directional Swipe Vector Arrow if SCROLL mark
            scrollDir?.let { dir ->
                val arrowDist = outerRadius * 0.62f
                val headSize = dpF(4.5f)
                arrowPath.reset()
                when (dir) {
                    com.tajasb.autotap.TapScrollDirection.UP -> {
                        val tipY = cy - arrowDist
                        arrowPath.moveTo(cx, tipY)
                        arrowPath.lineTo(cx - headSize, tipY + headSize * 1.3f)
                        arrowPath.lineTo(cx + headSize, tipY + headSize * 1.3f)
                        arrowPath.close()
                    }
                    com.tajasb.autotap.TapScrollDirection.DOWN -> {
                        val tipY = cy + arrowDist
                        arrowPath.moveTo(cx, tipY)
                        arrowPath.lineTo(cx - headSize, tipY - headSize * 1.3f)
                        arrowPath.lineTo(cx + headSize, tipY - headSize * 1.3f)
                        arrowPath.close()
                    }
                    com.tajasb.autotap.TapScrollDirection.LEFT -> {
                        val tipX = cx - arrowDist
                        arrowPath.moveTo(tipX, cy)
                        arrowPath.lineTo(tipX + headSize * 1.3f, cy - headSize)
                        arrowPath.lineTo(tipX + headSize * 1.3f, cy + headSize)
                        arrowPath.close()
                    }
                    com.tajasb.autotap.TapScrollDirection.RIGHT -> {
                        val tipX = cx + arrowDist
                        arrowPath.moveTo(tipX, cy)
                        arrowPath.lineTo(tipX - headSize * 1.3f, cy - headSize)
                        arrowPath.lineTo(tipX - headSize * 1.3f, cy + headSize)
                        arrowPath.close()
                    }
                }
                centerDotPaint.color = activeColor
                canvas.drawPath(arrowPath, centerDotPaint)
            }

            // 5. Top Step Badge Pill (#1..#N)
            val badgeTextSize = (minDim * 0.21f).coerceIn(dpF(8.5f), dpF(13f))
            textPaint.textSize = badgeTextSize
            val topTextW = textPaint.measureText(topBadgeText)
            val pillW = (topTextW + dpF(8f)).coerceAtMost(w - dpF(2f))
            val pillH = badgeTextSize + dpF(4f)
            val pillTop = dpF(1.5f)
            pillRect.set(cx - pillW / 2f, pillTop, cx + pillW / 2f, pillTop + pillH)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint)
            pillStrokePaint.color = activeColor
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillStrokePaint)

            textPaint.color = if (isExecutingMark) Color.parseColor("#FDE047") else Color.WHITE
            val textBaseline = pillRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(topBadgeText, cx, textBaseline, textPaint)

            // 6. Bottom Compact Action Label Pill (if mark is large enough)
            if (bottomTagText.isNotEmpty() && minDim >= dpF(38f)) {
                val subTextSize = (minDim * 0.165f).coerceIn(dpF(7f), dpF(10.5f))
                textPaint.textSize = subTextSize
                val subW = (textPaint.measureText(bottomTagText) + dpF(7f)).coerceAtMost(w - dpF(2f))
                val subH = subTextSize + dpF(3.5f)
                val subBottom = h - dpF(1.5f)
                pillRect.set(cx - subW / 2f, subBottom - subH, cx + subW / 2f, subBottom)
                canvas.drawRoundRect(pillRect, subH / 2f, subH / 2f, pillBgPaint)
                textPaint.color = Color.parseColor("#E2E8F0")
                val subBaseline = pillRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(bottomTagText, cx, subBaseline, textPaint)
            }
        }
    }

    @Volatile
    private var windowFrameOffsetX: Int = 0

    @Volatile
    private var windowFrameOffsetY: Int = 0

    private fun buildMarkWindowFlags(passThroughTouchable: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        // CRITICAL: When macro is running, testing a single step, or marks are locked,
        // make mark overlays completely touch-transparent so taps pass straight through!
        if (passThroughTouchable) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    /**
     * Syncs the exact physical screen coordinates (rawX, rawY) of all currently visible mark views
     * back to AutoTapManager before hiding or locking them, ensuring 100% accuracy even when hidden.
     */
    fun syncVisibleMarkCoordinates(service: FloatingControlService) = with(service) {
        val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
        val currentMarks = autoTap.marks.value
        val loc = IntArray(2)
        multiMarkOverlayViews.forEach { (id, pair) ->
            val (v, _) = pair
            if (v.windowToken != null && v.width > 0 && v.height > 0) {
                v.getLocationOnScreen(loc)
                val exactX = loc[0] + v.width / 2f
                val exactY = loc[1] + v.height / 2f
                if (exactX > 0f && exactY > 0f) {
                    val existing = currentMarks.firstOrNull { it.id == id }
                    if (existing == null || abs(existing.x - exactX) >= 1.0f || abs(existing.y - exactY) >= 1.0f) {
                        autoTap.updateMarkPosition(id, exactX, exactY, select = false)
                    }
                }
            }
        }
        val cView = crosshairView
        if (cView != null && cView.windowToken != null && cView.width > 0 && cView.height > 0) {
            cView.getLocationOnScreen(loc)
            val exactX = loc[0] + cView.width / 2f
            val exactY = loc[1] + cView.height / 2f
            if (exactX > 0f && exactY > 0f) {
                if (abs(autoTap.targetX.value - exactX) >= 1.0f || abs(autoTap.targetY.value - exactY) >= 1.0f) {
                    autoTap.setTargetPosition(exactX, exactY)
                }
            }
        }
    }

    /**
     * Lightweight fast-path when only the currently executing mark ID changes during a macro loop:
     * redraws only the affected reticle views (prev & next) via View.invalidate() without calling
     * WindowManager.updateViewLayout() IPC unless window touchability flags actually changed.
     */
    fun updateExecutingMarkHighlight(service: FloatingControlService, activeExecId: String?) = with(service) {
        if (multiMarkOverlayViews.isEmpty()) return@with
        val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
        val isMacroRunning = autoTap.isTapping.value
        val isGlobalLockedGhost = (autoTap.markDisplayMode.value == com.tajasb.autotap.MarkDisplayMode.LOCKED_GHOST)
        val marksMap = autoTap.marks.value.associateBy { it.id }

        multiMarkOverlayViews.forEach { (id, pair) ->
            val (view, params) = pair
            val mark = marksMap[id]
            val isMarkLocked = isGlobalLockedGhost || (mark?.isLocked == true)
            val isExecuting = (id == activeExecId)
            val targetAlpha = if (isMarkLocked && !isExecuting) 0.42f else 1.0f
            if (view.alpha != targetAlpha) {
                view.alpha = targetAlpha
            }
            if (view is TapTargetOverlayView && view.isExecutingMark != isExecuting) {
                view.isExecutingMark = isExecuting
                view.invalidate()
            }
            val desiredFlags = buildMarkWindowFlags(isMacroRunning || activeExecId != null || isMarkLocked)
            if (params.flags != desiredFlags) {
                params.flags = desiredFlags
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Returns the 100% exact physical screen pixel (rawX, rawY) of the center (+) of the mark reticle
     * currently displayed on screen using View.getLocationOnScreen().
     */
    fun resolveMarkScreenCoordinate(service: FloatingControlService, markId: String?): Pair<Float, Float>? = with(service) {
        val v: View? = if (markId != null) {
            multiMarkOverlayViews[markId]?.first
        } else {
            crosshairView
        }
        if (v != null && v.windowToken != null && v.width > 0 && v.height > 0) {
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val exactX = loc[0] + v.width / 2f
            val exactY = loc[1] + v.height / 2f
            if (exactX > 0f && exactY > 0f) {
                return@with exactX to exactY
            }
        }
        null
    }

    /**
     * Synchronously applies or clears FLAG_NOT_TOUCHABLE on all active mark overlay windows
     * on the Main Thread. Returns true if any window's flags were modified.
     */
    fun setAllOverlaysPassThrough(service: FloatingControlService, passThrough: Boolean): Boolean = with(service) {
        val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
        val globallyLocked = autoTap.isAllMarksLocked()
        var changed = false

        multiMarkOverlayViews.forEach { (id, pair) ->
            val (view, params) = pair
            val itemLocked = autoTap.marks.value.firstOrNull { it.id == id }?.isLocked == true
            val desiredFlags = buildMarkWindowFlags(passThrough || globallyLocked || itemLocked)
            if (params.flags != desiredFlags) {
                params.flags = desiredFlags
                changed = true
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) {}
            }
        }

        val cView = crosshairView
        val cParams = crosshairLayoutParams
        val singleDesiredFlags = buildMarkWindowFlags(passThrough || globallyLocked)
        if (cView != null && cParams != null && cParams.flags != singleDesiredFlags) {
            cParams.flags = singleDesiredFlags
            changed = true
            try {
                windowManager.updateViewLayout(cView, cParams)
            } catch (_: Exception) {}
        }

        changed
    }

    fun showCrosshairPointer(service: FloatingControlService): Unit = with(service) {
        if (isStoppingOrDestroyed || !FloatingControlService.isRunning) return
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
        val displayMode = autoTap.markDisplayMode.value

        // If Auto-Tap module is disabled in settings, or user hid marks / turned on LOCKED_HIDDEN, sync coordinates and remove views
        if (!repository.config.value.enableAutoTapModule || !autoTap.isPointerVisible.value || displayMode == com.tajasb.autotap.MarkDisplayMode.LOCKED_HIDDEN) {
            syncVisibleMarkCoordinates(this)
            removeCrosshairPointer(this)
            return
        }

        // If user is currently dragging a mark, do not recreate or reposition overlays
        if (service.isUserDraggingMark) {
            return
        }

        val markSize = dp(autoTap.markSizeDp.value)
        val isMacroRunning = autoTap.isTapping.value
        val activeExecId = autoTap.activeExecutingMarkId.value
        val isGlobalLockedGhost = (displayMode == com.tajasb.autotap.MarkDisplayMode.LOCKED_GHOST)
        val (realScreenW, realScreenH) = autoTap.getRealScreenSize()

        if (autoTap.executionMode.value == com.tajasb.autotap.TapExecutionMode.MULTI_MARK) {
            // Remove single crosshair if present
            if (crosshairView != null) {
                try {
                    windowManager.removeView(crosshairView)
                } catch (_: Exception) {
                    try { windowManager.removeViewImmediate(crosshairView) } catch (_: Exception) {}
                }
                crosshairView = null
            }

            val allMarks = autoTap.marks.value
            val selectedId = autoTap.selectedMarkId.value
            val activeMarks = allMarks.filter { it.enabled && it.type.needsCoordinate }
            val activeIds = activeMarks.map { it.id }.toSet()

            // Remove views for deleted or disabled marks
            val removedIds = multiMarkOverlayViews.keys.filter { it !in activeIds }
            removedIds.forEach { id ->
                multiMarkOverlayViews.remove(id)?.let { (v, _) ->
                    try {
                        windowManager.removeView(v)
                    } catch (_: Exception) {
                        try { windowManager.removeViewImmediate(v) } catch (_: Exception) {}
                    }
                }
            }

            // Add or update active marks
            var anyNewMarkAdded = false
            allMarks.forEachIndexed { index, mark ->
                if (!mark.enabled || !mark.type.needsCoordinate) return@forEachIndexed
                val stepNum = index + 1
                val isSelected = (mark.id == selectedId)
                val isExecuting = (mark.id == activeExecId)
                val isMarkLocked = isGlobalLockedGhost || mark.isLocked
                val desiredFlags = buildMarkWindowFlags(isMacroRunning || activeExecId != null || isMarkLocked)

                val accentColor = when (mark.type) {
                    com.tajasb.autotap.TapMarkActionType.CLICK ->
                        if (mark.isDoubleTap) Color.parseColor("#F43F5E") else Color.parseColor("#00E5FF")
                    com.tajasb.autotap.TapMarkActionType.LOVE -> Color.parseColor("#F43F5E")
                    com.tajasb.autotap.TapMarkActionType.SCROLL -> Color.parseColor("#38BDF8")
                    com.tajasb.autotap.TapMarkActionType.AUTO_TYPE -> Color.parseColor("#10B981")
                    com.tajasb.autotap.TapMarkActionType.BACK -> Color.parseColor("#F59E0B")
                }

                val lockPrefix = if (isMarkLocked) "🔒" else ""
                val topBadge = when (mark.type) {
                    com.tajasb.autotap.TapMarkActionType.CLICK ->
                        if (mark.isDoubleTap) "$lockPrefix#$stepNum 2x" else "$lockPrefix#$stepNum"
                    com.tajasb.autotap.TapMarkActionType.LOVE ->
                        "$lockPrefix#$stepNum ❤️"
                    com.tajasb.autotap.TapMarkActionType.SCROLL ->
                        "$lockPrefix#$stepNum ${mark.scrollDirection.icon}"
                    else -> "$lockPrefix#$stepNum"
                }

                val bottomLabel = when (mark.type) {
                    com.tajasb.autotap.TapMarkActionType.CLICK -> if (mark.isDoubleTap) "DBL" else "KLIK"
                    com.tajasb.autotap.TapMarkActionType.LOVE -> "${mark.loveBurstCount}x❤️"
                    com.tajasb.autotap.TapMarkActionType.SCROLL -> "SCRL"
                    com.tajasb.autotap.TapMarkActionType.BACK -> "BACK"
                    com.tajasb.autotap.TapMarkActionType.AUTO_TYPE -> "KETIK"
                }

                val scrollDir = if (mark.type == com.tajasb.autotap.TapMarkActionType.SCROLL) {
                    mark.scrollDirection
                } else null

                val targetParamX = (mark.x - markSize / 2f - windowFrameOffsetX).toInt()
                val targetParamY = (mark.y - markSize / 2f - windowFrameOffsetY).toInt()

                val existing = multiMarkOverlayViews[mark.id]
                if (existing != null) {
                    // Update in-place: zero-flicker and zero-bounce
                    val (existingView, params) = existing
                    val sizeChanged = (params.width != markSize || params.height != markSize)
                    params.width = markSize
                    params.height = markSize

                    // Check if view is already at the exact screen position to prevent any post-drag bounce
                    var shouldUpdateXY = sizeChanged
                    if (!shouldUpdateXY && existingView.windowToken != null && existingView.width > 0) {
                        val curLoc = IntArray(2)
                        existingView.getLocationOnScreen(curLoc)
                        val curCenterX = curLoc[0] + existingView.width / 2f
                        val curCenterY = curLoc[1] + existingView.height / 2f
                        if (abs(curCenterX - mark.x) > 2.5f || abs(curCenterY - mark.y) > 2.5f) {
                            shouldUpdateXY = true
                        }
                    } else {
                        shouldUpdateXY = true
                    }

                    if (shouldUpdateXY) {
                        params.x = targetParamX
                        params.y = targetParamY
                    }
                    val flagsChanged = (params.flags != desiredFlags)
                    params.flags = desiredFlags
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        params.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }

                    existingView.alpha = if (isMarkLocked && !isExecuting) 0.42f else 1.0f
                    if (existingView is TapTargetOverlayView) {
                        existingView.updateState(
                            step = stepNum,
                            topText = topBadge,
                            bottomText = bottomLabel,
                            accentColor = accentColor,
                            selected = isSelected,
                            executing = isExecuting,
                            scrollDirection = scrollDir
                        )
                    }
                    if (sizeChanged || shouldUpdateXY || flagsChanged) {
                        try {
                            windowManager.updateViewLayout(existingView, params)
                        } catch (_: Exception) {}
                    }
                } else {
                    // Create new precision reticle overlay view
                    val params = WindowManager.LayoutParams(
                        markSize,
                        markSize,
                        getOverlayWindowType(),
                        desiredFlags,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = targetParamX
                        y = targetParamY
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            layoutInDisplayCutoutMode =
                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                    }

                    val markerView = TapTargetOverlayView(this).apply {
                        alpha = if (isMarkLocked && !isExecuting) 0.42f else 1.0f
                        updateState(
                            step = stepNum,
                            topText = topBadge,
                            bottomText = bottomLabel,
                            accentColor = accentColor,
                            selected = isSelected,
                            executing = isExecuting,
                            scrollDirection = scrollDir
                        )
                    }

                    var initialX = 0
                    var initialY = 0
                    var initialTouchX = 0f
                    var initialTouchY = 0f
                    var hasDragged = false
                    val touchSlopPx = dp(6)

                    markerView.setOnTouchListener { _, event ->
                        val currentMarkItem = autoTap.marks.value.firstOrNull { it.id == mark.id }
                        if (autoTap.isTapping.value || autoTap.isAllMarksLocked() || currentMarkItem?.isLocked == true) {
                            return@setOnTouchListener false
                        }
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                service.isUserDraggingMark = true
                                initialX = params.x
                                initialY = params.y
                                initialTouchX = event.rawX
                                initialTouchY = event.rawY
                                hasDragged = false
                                autoTap.selectMark(mark.id)
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = (event.rawX - initialTouchX).toInt()
                                val dy = (event.rawY - initialTouchY).toInt()
                                if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                                    hasDragged = true
                                }
                                val (scrW, scrH) = autoTap.getRealScreenSize()
                                val curSz = params.width.coerceAtLeast(markSize)
                                val minParamX = (2f - curSz / 2f - windowFrameOffsetX).toInt()
                                val maxParamX = (scrW - 2f - curSz / 2f - windowFrameOffsetX).toInt().coerceAtLeast(minParamX)
                                val minParamY = (2f - curSz / 2f - windowFrameOffsetY).toInt()
                                val maxParamY = (scrH - 2f - curSz / 2f - windowFrameOffsetY).toInt().coerceAtLeast(minParamY)

                                params.x = (initialX + dx).coerceIn(minParamX, maxParamX)
                                params.y = (initialY + dy).coerceIn(minParamY, maxParamY)
                                try {
                                    windowManager.updateViewLayout(markerView, params)
                                } catch (_: Exception) {}
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                service.isUserDraggingMark = false
                                val loc = IntArray(2)
                                markerView.getLocationOnScreen(loc)
                                val (scrW, scrH) = autoTap.getRealScreenSize()
                                val curWidth = if (markerView.width > 0) markerView.width else params.width
                                val curHeight = if (markerView.height > 0) markerView.height else params.height
                                // Only calibrate windowFrameOffset when away from screen borders so OEM edge clamping never skews offset
                                if (loc[0] in 24..(scrW.toInt() - curWidth - 24)) {
                                    windowFrameOffsetX = loc[0] - params.x
                                }
                                if (loc[1] in 64..(scrH.toInt() - curHeight - 64)) {
                                    windowFrameOffsetY = loc[1] - params.y
                                }
                                val exactCenterX = (loc[0] + curWidth / 2f).coerceIn(2f, scrW - 2f)
                                val exactCenterY = (loc[1] + curHeight / 2f).coerceIn(2f, scrH - 2f)
                                if (hasDragged) {
                                    autoTap.updateMarkPosition(mark.id, exactCenterX, exactCenterY)
                                } else if (event.action == MotionEvent.ACTION_UP) {
                                    autoTap.updateMarkPosition(mark.id, exactCenterX, exactCenterY)
                                    val curMark = autoTap.marks.value.find { it.id == mark.id }
                                    if (curMark != null) {
                                        when (curMark.type) {
                                            com.tajasb.autotap.TapMarkActionType.CLICK -> {
                                                val nextDbl = !curMark.isDoubleTap
                                                autoTap.updateMark(mark.id) { it.copy(isDoubleTap = nextDbl) }
                                                Toast.makeText(
                                                    applicationContext,
                                                    "Mark #$stepNum: ${if (nextDbl) "Double Tap (2x)" else "Single Klik (1x)"}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            com.tajasb.autotap.TapMarkActionType.LOVE -> {
                                                val loveOptions = listOf(1, 3, 5, 10, 15, 25)
                                                val curIdx = loveOptions.indexOf(curMark.loveBurstCount)
                                                val nextBursts = loveOptions[(curIdx + 1) % loveOptions.size]
                                                autoTap.updateMark(mark.id) { it.copy(loveBurstCount = nextBursts) }
                                                Toast.makeText(
                                                    applicationContext,
                                                    "Mark #$stepNum Tap Love: ${nextBursts}x Beruntun ❤️",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            com.tajasb.autotap.TapMarkActionType.SCROLL -> {
                                                val nextDir = curMark.scrollDirection.next()
                                                autoTap.updateMark(mark.id) { it.copy(scrollDirection = nextDir) }
                                                Toast.makeText(
                                                    applicationContext,
                                                    "Mark #$stepNum Scroll: ${nextDir.label}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            com.tajasb.autotap.TapMarkActionType.AUTO_TYPE -> {
                                                val nextMode = autoTap.toggleAutoTypeSelectionMode()
                                                Toast.makeText(
                                                    applicationContext,
                                                    "Auto Ketik: ${nextMode.shortLabel}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            com.tajasb.autotap.TapMarkActionType.BACK -> {
                                                Toast.makeText(
                                                    applicationContext,
                                                    "Mark #$stepNum: Aksi Sistem Back",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    }

                    try {
                        windowManager.addView(markerView, params)
                        multiMarkOverlayViews[mark.id] = markerView to params
                        anyNewMarkAdded = true
                        // Calibrate exact window frame offset (e.g. status bar / cutout) on first attach
                        markerView.post {
                            if (!service.isUserDraggingMark && markerView.windowToken != null) {
                                val loc = IntArray(2)
                                markerView.getLocationOnScreen(loc)
                                val curW = if (markerView.width > 0) markerView.width else params.width
                                val curH = if (markerView.height > 0) markerView.height else params.height
                                val measuredOffX = loc[0] - params.x
                                val measuredOffY = loc[1] - params.y
                                val isInsideSafeCalibrationZone =
                                    loc[0] in 24..(realScreenW.toInt() - curW - 24) &&
                                    loc[1] in 64..(realScreenH.toInt() - curH - 64)
                                if (isInsideSafeCalibrationZone && (measuredOffX != windowFrameOffsetX || measuredOffY != windowFrameOffsetY)) {
                                    windowFrameOffsetX = measuredOffX
                                    windowFrameOffsetY = measuredOffY
                                    val latestMark = autoTap.marks.value.find { it.id == mark.id } ?: mark
                                    val curSz = dp(autoTap.markSizeDp.value)
                                    params.x = (latestMark.x - curSz / 2f - windowFrameOffsetX).toInt()
                                    params.y = (latestMark.y - curSz / 2f - windowFrameOffsetY).toInt()
                                    try {
                                        windowManager.updateViewLayout(markerView, params)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            if (anyNewMarkAdded) {
                bringFloatingWindowToFront(this)
            }
            return
        }

        // Single Tap Mode: clear multi-mark views first
        if (multiMarkOverlayViews.isNotEmpty()) {
            val existingValues = multiMarkOverlayViews.values.toList()
            multiMarkOverlayViews.clear()
            existingValues.forEach { (v, _) ->
                try {
                    windowManager.removeView(v)
                } catch (_: Exception) {
                    try { windowManager.removeViewImmediate(v) } catch (_: Exception) {}
                }
            }
        }

        val singleDesiredFlags = buildMarkWindowFlags(isMacroRunning || activeExecId != null || isGlobalLockedGhost)
        val singleParamX = (autoTap.targetX.value - markSize / 2f - windowFrameOffsetX).toInt()
        val singleParamY = (autoTap.targetY.value - markSize / 2f - windowFrameOffsetY).toInt()

        if (crosshairView != null && crosshairView?.windowToken != null) {
            crosshairLayoutParams?.let { params ->
                val sizeChanged = (params.width != markSize || params.height != markSize)
                params.width = markSize
                params.height = markSize

                var shouldUpdateXY = sizeChanged
                val cView = crosshairView
                if (!shouldUpdateXY && cView != null && cView.width > 0) {
                    val curLoc = IntArray(2)
                    cView.getLocationOnScreen(curLoc)
                    val curCenterX = curLoc[0] + cView.width / 2f
                    val curCenterY = curLoc[1] + cView.height / 2f
                    if (abs(curCenterX - autoTap.targetX.value) > 2.5f || abs(curCenterY - autoTap.targetY.value) > 2.5f) {
                        shouldUpdateXY = true
                    }
                } else {
                    shouldUpdateXY = true
                }

                if (shouldUpdateXY) {
                    params.x = singleParamX
                    params.y = singleParamY
                }
                params.flags = singleDesiredFlags
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                crosshairView?.alpha = if (isGlobalLockedGhost && !isMacroRunning) 0.42f else 1.0f
                (crosshairView as? TapTargetOverlayView)?.updateState(
                    step = 1,
                    topText = if (isGlobalLockedGhost) "🔒TAP" else "TAP",
                    bottomText = "2x LOVE",
                    accentColor = Color.parseColor("#F43F5E"),
                    selected = true,
                    executing = isMacroRunning,
                    scrollDirection = null,
                    singleMode = true
                )
                try {
                    windowManager.updateViewLayout(crosshairView, params)
                } catch (_: Exception) {}
            }
            return
        }

        val params = WindowManager.LayoutParams(
            markSize,
            markSize,
            getOverlayWindowType(),
            singleDesiredFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = singleParamX
            y = singleParamY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        crosshairLayoutParams = params

        val targetView = TapTargetOverlayView(this).apply {
            alpha = if (isGlobalLockedGhost && !isMacroRunning) 0.42f else 1.0f
            updateState(
                step = 1,
                topText = if (isGlobalLockedGhost) "🔒TAP" else "TAP",
                bottomText = "2x LOVE",
                accentColor = Color.parseColor("#F43F5E"),
                selected = true,
                executing = isMacroRunning,
                scrollDirection = null,
                singleMode = true
            )
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        targetView.setOnTouchListener { _, event ->
            if (autoTap.isTapping.value || autoTap.isAllMarksLocked()) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    service.isUserDraggingMark = true
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    val (scrW, scrH) = autoTap.getRealScreenSize()
                    val curSz = params.width.coerceAtLeast(markSize)
                    val minParamX = (2f - curSz / 2f - windowFrameOffsetX).toInt()
                    val maxParamX = (scrW - 2f - curSz / 2f - windowFrameOffsetX).toInt().coerceAtLeast(minParamX)
                    val minParamY = (2f - curSz / 2f - windowFrameOffsetY).toInt()
                    val maxParamY = (scrH - 2f - curSz / 2f - windowFrameOffsetY).toInt().coerceAtLeast(minParamY)

                    params.x = (initialX + dx).coerceIn(minParamX, maxParamX)
                    params.y = (initialY + dy).coerceIn(minParamY, maxParamY)
                    try {
                        windowManager.updateViewLayout(targetView, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    service.isUserDraggingMark = false
                    val loc = IntArray(2)
                    targetView.getLocationOnScreen(loc)
                    val (scrW, scrH) = autoTap.getRealScreenSize()
                    val curWidth = if (targetView.width > 0) targetView.width else params.width
                    val curHeight = if (targetView.height > 0) targetView.height else params.height
                    if (loc[0] in 24..(scrW.toInt() - curWidth - 24)) {
                        windowFrameOffsetX = loc[0] - params.x
                    }
                    if (loc[1] in 64..(scrH.toInt() - curHeight - 64)) {
                        windowFrameOffsetY = loc[1] - params.y
                    }
                    val exactCenterX = (loc[0] + curWidth / 2f).coerceIn(2f, scrW - 2f)
                    val exactCenterY = (loc[1] + curHeight / 2f).coerceIn(2f, scrH - 2f)
                    autoTap.setTargetPosition(exactCenterX, exactCenterY)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(targetView, params)
            crosshairView = targetView
            bringFloatingWindowToFront(this)
            targetView.post {
                if (!service.isUserDraggingMark && targetView.windowToken != null) {
                    val loc = IntArray(2)
                    targetView.getLocationOnScreen(loc)
                    val curW = if (targetView.width > 0) targetView.width else params.width
                    val curH = if (targetView.height > 0) targetView.height else params.height
                    val measuredOffX = loc[0] - params.x
                    val measuredOffY = loc[1] - params.y
                    val isInsideSafeCalibrationZone =
                        loc[0] in 24..(realScreenW.toInt() - curW - 24) &&
                        loc[1] in 64..(realScreenH.toInt() - curH - 64)
                    if (isInsideSafeCalibrationZone && (measuredOffX != windowFrameOffsetX || measuredOffY != windowFrameOffsetY)) {
                        windowFrameOffsetX = measuredOffX
                        windowFrameOffsetY = measuredOffY
                        val curSz = dp(autoTap.markSizeDp.value)
                        params.x = (autoTap.targetX.value - curSz / 2f - windowFrameOffsetX).toInt()
                        params.y = (autoTap.targetY.value - curSz / 2f - windowFrameOffsetY).toInt()
                        try {
                            windowManager.updateViewLayout(targetView, params)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeCrosshairPointer(service: FloatingControlService) = with(service) {
        service.isUserDraggingMark = false
        if (crosshairView != null) {
            try {
                windowManager.removeView(crosshairView)
            } catch (_: Exception) {
                try {
                    windowManager.removeViewImmediate(crosshairView)
                } catch (_: Exception) {}
            }
            crosshairView = null
        }
        if (multiMarkOverlayViews.isNotEmpty()) {
            val existingValues = multiMarkOverlayViews.values.toList()
            multiMarkOverlayViews.clear()
            existingValues.forEach { (v, _) ->
                try {
                    windowManager.removeView(v)
                } catch (_: Exception) {
                    try {
                        windowManager.removeViewImmediate(v)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun updateAutoTapUi(service: FloatingControlService) = with(service) {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
        val isTapping = autoTap.isTapping.value
        val isAntiBot = autoTap.antiRobotEnabled.value
        val isMultiMark = autoTap.executionMode.value == com.tajasb.autotap.TapExecutionMode.MULTI_MARK

        val hasPausedProgress = autoTap.hasSavedTurnProgress()

        btnToggleTapText?.apply {
            text = if (isTapping) {
                if (isMultiMark) "⏸ JEDA MAKRO" else "⏸ JEDA TAP"
            } else if (hasPausedProgress) {
                if (isMultiMark) "▶ LANJUTKAN MAKRO" else "▶ LANJUTKAN TAP"
            } else {
                if (isMultiMark) "▶ JALANKAN MAKRO" else "▶ JALANKAN TAP"
            }
            setTextColor(if (isTapping) Color.WHITE else Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(7).toFloat()
                setColor(
                    when {
                        isTapping -> Color.parseColor("#EF4444")
                        hasPausedProgress -> Color.parseColor("#10B981")
                        else -> Color.parseColor("#00E5FF")
                    }
                )
            }
        }

        tapStatusBadge?.apply {
            text = when {
                isTapping -> "● ${autoTap.activeStepDescription.value}"
                hasPausedProgress -> "⏸ Dijeda • Lanjut: ${autoTap.getResumeSummaryShort()}"
                else -> "● SIAP (STANDBY)"
            }
            setTextColor(
                when {
                    isTapping -> Color.parseColor("#10B981")
                    hasPausedProgress -> Color.parseColor("#FDE047")
                    else -> Color.parseColor("#94A3B8")
                }
            )
        }

        btnAntiRobotText?.apply {
            text = if (isAntiBot) "MODE: ACAK" else "MODE: TETAP"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                if (isAntiBot) {
                    setColor(Color.parseColor("#064E3B"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                } else {
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
            }
        }
        updateBubbleIconsStatus()
    }

    fun showScanAreaOverlay(service: FloatingControlService) = with(service) {
        if (scanAreaOverlayView != null) {
            scanAreaOverlayView?.dismiss()
            scanAreaOverlayView = null
            return
        }
        try {
            showBubbleOnly()
            val overlay = com.tajasb.livecomment.ScanAreaOverlayView(this, windowManager) {
                scanAreaOverlayView = null
                showExpandedPanel()
                updateBuyerNotifUi()
            }
            scanAreaOverlayView = overlay
            overlay.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka kotak area: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchTargetApp(service: FloatingControlService, showChooser: Boolean = false) = with(service) {
        val pm = packageManager
        val cfg = repository.config.value
        val primaryPkg = cfg.primaryTargetPackage.ifBlank { null }

        val candidatePackages = mutableListOf<String>()
        if (primaryPkg != null) candidatePackages.add(primaryPkg)
        candidatePackages.addAll(cfg.targetPackages)
        candidatePackages.addAll(
            listOf(
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.live",
                "com.instagram.android",
                "com.whatsapp",
                "com.whatsapp.w4b"
            )
        )

        val installedList = mutableListOf<Pair<String, String>>()
        for (pkg in candidatePackages.distinct()) {
            try {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    val label = try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (_: Exception) {
                        pkg
                    }
                    if (!installedList.any { it.first == pkg }) {
                        installedList.add(Pair(pkg, label))
                    }
                }
            } catch (_: Exception) {}
        }

        if (installedList.isEmpty()) {
            Toast.makeText(applicationContext, "Tidak ada aplikasi target terinstal. Silakan atur di Tab Target.", Toast.LENGTH_SHORT).show()
            return
        }

        if (showChooser || (installedList.size > 1 && primaryPkg == null)) {
            showTargetAppChooserDialog(service, installedList)
        } else {
            val target = installedList.first()
            launchSpecificPackage(service, target.first, target.second)
        }
    }

    fun launchSpecificPackage(service: FloatingControlService, pkg: String, label: String) = with(service) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (intent != null) {
                Toast.makeText(applicationContext, "Membuka $label... 🚀", Toast.LENGTH_SHORT).show()
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Gagal membuka $label", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTargetAppChooserDialog(service: FloatingControlService, apps: List<Pair<String, String>>) = with(service) {
        try {
            val labels = apps.map { "🚀 ${it.second}" }.toTypedArray()
            val builder = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("📱 Pilih Aplikasi Target:")
                .setItems(labels) { dialog, which ->
                    val selected = apps[which]
                    launchSpecificPackage(service, selected.first, selected.second)
                    dialog.dismiss()
                }
                .setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }

            val dialog = builder.create()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                @Suppress("DEPRECATION")
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
            dialog.show()
        } catch (e: Exception) {
            if (apps.isNotEmpty()) launchSpecificPackage(service, apps.first().first, apps.first().second)
        }
    }
}
