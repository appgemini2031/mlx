package com.tajasb.interaction

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import com.tajasb.ui.components.FloatingInteractionPanelContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private class FloatingComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onAttach() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDetach() {
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        } catch (_: Throwable) {}
        store.clear()
    }
}

/**
 * Container header floating yang memungkinkan geser bebas dari area mana pun di header/bar
 * (termasuk mulai geser dari atas tombol), sementara ketukan singkat tetap diteruskan ke tombol.
 */
private class DraggableFloatingContainer(
    context: Context,
    private val onDragStart: () -> Unit,
    private val onDragMove: (dx: Int, dy: Int) -> Unit,
    private val onDragEnd: (moved: Boolean) -> Unit
) : LinearLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.coerceAtLeast(8)
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false
    private var dragStarted = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = ev.rawX
                downRawY = ev.rawY
                dragging = false
                dragStarted = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    if (!dragStarted) {
                        dragStarted = true
                        onDragStart()
                    }
                    dragging = true
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                dragStarted = false
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                dragStarted = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downRawX).roundToInt()
                val dy = (event.rawY - downRawY).roundToInt()
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                    if (!dragStarted) {
                        dragStarted = true
                        onDragStart()
                    }
                }
                if (dragging) {
                    onDragMove(dx, dy)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragStarted || dragging) {
                    onDragEnd(dragging)
                }
                dragging = false
                dragStarted = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

/**
 * FloatingTriggerOverlayService:
 * - 1 floating bar selalu berada di dalam batas layar aman (tidak masuk status bar, notch, atau nav bar).
 * - Bebas digeser ke area layar aman mana pun tanpa snap-back.
 * - Bar D-Pad Navigasi Kursor Presisi (◀ ▲ ▼ ▶ + Halus/Cepat) untuk menyetel pin tepat ke tombol MLBB.
 * - Setiap challenge memiliki warna pembeda & tombol tutup/buka mata (👁️ / 🙈) masing-masing.
 */
class FloatingTriggerOverlayService : Service() {

    companion object {
        private const val TAG = "FloatingTriggerHud"
        private const val CHANNEL_ID = "mlbb_floating_hud_channel"
        private const val NOTIF_ID = 4107
        private const val EXTRA_START_EDIT_MODE = "extra_start_edit_mode"
        private const val EXTRA_OPEN_PANEL = "extra_open_panel"
        private const val EXTRA_HIDDEN_LIVE = "extra_hidden_live"
        private const val ACTION_STOP_SERVICE = "com.tajasb.interaction.ACTION_STOP_FLOATING_HUD"
        private const val ACTION_SHOW_FLOATING = "com.tajasb.interaction.ACTION_SHOW_FLOATING_HUD"
        private const val ACTION_HIDE_FLOATING = "com.tajasb.interaction.ACTION_HIDE_FLOATING_HUD"
        private const val ACTION_RESET_POSITION = "com.tajasb.interaction.ACTION_RESET_FLOATING_POS"

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun start(
            context: Context,
            startInEditMode: Boolean = false,
            openSettingsPanel: Boolean = false,
            hideFloatingForLive: Boolean = false
        ): Boolean {
            if (!canDrawOverlays(context)) return false
            val intent = Intent(context, FloatingTriggerOverlayService::class.java).apply {
                putExtra(EXTRA_START_EDIT_MODE, startInEditMode)
                putExtra(EXTRA_OPEN_PANEL, openSettingsPanel)
                putExtra(EXTRA_HIDDEN_LIVE, hideFloatingForLive)
            }
            ContextCompat.startForegroundService(context, intent)
            return true
        }

        fun resetHudPosition(context: Context) {
            if (!canDrawOverlays(context)) return
            val intent = Intent(context, FloatingTriggerOverlayService::class.java).apply {
                action = ACTION_RESET_POSITION
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, FloatingTriggerOverlayService::class.java))
            } catch (_: Throwable) {}
            UniversalTriggerEngine.peekInstance()?.setFloatingOverlayRunning(false)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var engine: UniversalTriggerEngine
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var liveStageView: FrameLayout? = null
    private var touchBlockerView: FrameLayout? = null
    private var touchBlockerParams: WindowManager.LayoutParams? = null
    private var touchBlockerStatusPill: TextView? = null
    private var singleHudView: LinearLayout? = null
    private var singleHudParams: WindowManager.LayoutParams? = null
    private var subMenuComposeOwner: FloatingComposeOwner? = null
    private var subMenuContainer: FrameLayout? = null
    private var subMenuComposeView: ComposeView? = null
    private var isDraggingHud = false
    private var lastAppliedXRatio: Float = -1f
    private var lastAppliedYRatio: Float = -1f

    private var isSubMenuExpanded by mutableStateOf(false)
    private var selectedRuleIdForSubMenu by mutableStateOf<String?>(null)
    private var isFinePrecisionStep by mutableStateOf(true)

    private var hudTopRow: LinearLayout? = null
    private var hudTitleBtn: TextView? = null
    private var btnEngineTiny: TextView? = null
    private var btnLockTiny: TextView? = null
    private var btnPinTiny: TextView? = null
    private var btnSubMenuTiny: TextView? = null
    private var btnMinTiny: TextView? = null
    private var btnCloseTiny: TextView? = null

    @Volatile
    private var isTemporarilyHiddenForAutomation = false

    @Volatile
    private var isTemporarilyHiddenForScan = false

    private val overlayBitmapCache = object : LruCache<String, Bitmap>(24) {}
    private val inFlightRemoteBitmaps = Collections.synchronizedSet(mutableSetOf<String>())

    // Precision Cursor Navigation Bar views
    private var dpadContainer: LinearLayout? = null
    private var dpadColorDot: View? = null
    private var dpadTargetLabel: TextView? = null
    private var dpadCoordLabel: TextView? = null
    private var dpadEyeBtn: TextView? = null
    private var dpadStepBtn: TextView? = null
    private var dpadArrowRow: LinearLayout? = null
    private var dpadHiddenBannerRow: LinearLayout? = null

    private data class MarkerWindowEntry(
        val root: LinearLayout,
        val crosshairView: TextView,
        val badgeLabel: TextView,
        val quickActionsRow: LinearLayout,
        val params: WindowManager.LayoutParams,
        var isActionsOpen: Boolean = false
    )

    private val markerWindows = linkedMapOf<String, MarkerWindowEntry>()
    private var draggingRuleId: String? = null
    private var lastState: UniversalEngineUiState = UniversalEngineUiState()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        engine = UniversalTriggerEngine.getInstance(applicationContext)
        lastState = engine.state.value

        ensureNotificationChannel()
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(lastState.isFloatingHiddenForLive), fgsType)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        MlbbGestureAccessibilityService.onPrepareScreenForTouch = { hideForTouch ->
            isTemporarilyHiddenForAutomation = hideForTouch
            setMarkerWindowsTemporaryHidden(hideForTouch)
        }
        MlbbGestureAccessibilityService.onPrepareScreenForScan = { hideForScan ->
            isTemporarilyHiddenForScan = hideForScan
            setAllFloatingWindowsHiddenForScan(hideForScan)
        }

        engine.setFloatingOverlayRunning(true)
        attachLiveStageWindow()
        attachTouchBlockerWindow()
        attachSingleFloatingHudWindow()
        observeEngineState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(engine.state.value.isFloatingHiddenForLive), fgsType)

        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_FLOATING -> {
                engine.setFloatingHiddenForLive(false)
                engine.updateFloatingConfig { it.copy(hudCollapsed = false) }
                lastState = engine.state.value
                refreshSingleHudVisuals()
                applyHudPositionFromConfig(lastState.floatingConfig, forceApply = true)
                clampHudIntoSafeBounds()
                updateNotification(lastState.isFloatingHiddenForLive)
                return START_STICKY
            }
            ACTION_HIDE_FLOATING -> {
                isSubMenuExpanded = false
                engine.setFloatingHiddenForLive(true)
                lastState = engine.state.value
                refreshSingleHudVisuals()
                updateNotification(lastState.isFloatingHiddenForLive)
                return START_STICKY
            }
            ACTION_RESET_POSITION -> {
                engine.setFloatingHiddenForLive(false)
                engine.resetFloatingPosition()
                lastState = engine.state.value
                applyHudPositionFromConfig(lastState.floatingConfig, forceApply = true)
                updateNotification(lastState.isFloatingHiddenForLive)
                Toast.makeText(this, "↺ Posisi floating direset ke area aman", Toast.LENGTH_SHORT).show()
                return START_STICKY
            }
        }

        val openEdit = intent?.getBooleanExtra(EXTRA_START_EDIT_MODE, false) == true
        val openPanel = intent?.getBooleanExtra(EXTRA_OPEN_PANEL, false) == true
        val hiddenLive = intent?.getBooleanExtra(EXTRA_HIDDEN_LIVE, false) == true

        if (hiddenLive) {
            isSubMenuExpanded = false
            engine.setFloatingHiddenForLive(true)
            Toast.makeText(
                this,
                "👁️ Live Bersih aktif — MLBB tetap ketrigger dari notifikasi.",
                Toast.LENGTH_SHORT
            ).show()
        } else if (openEdit) {
            isSubMenuExpanded = false
            engine.setFloatingHiddenForLive(false)
            engine.updateFloatingConfig { it.copy(hudCollapsed = false) }
            if (engine.state.value.rules.isEmpty()) {
                engine.createQuickTapTarget(UniversalTriggerSource.GIFT)
            } else {
                engine.setPositionEditMode(true)
            }
            Toast.makeText(this, "Geser pin ⊕ atau pakai panah presisi ◀ ▲ ▼ ▶", Toast.LENGTH_SHORT).show()
        } else if (openPanel) {
            engine.setFloatingHiddenForLive(false)
            engine.updateFloatingConfig { it.copy(hudCollapsed = false) }
            isSubMenuExpanded = true
        } else {
            engine.setFloatingHiddenForLive(false)
            engine.updateFloatingConfig { it.copy(hudCollapsed = false) }
        }
        lastState = engine.state.value
        refreshSingleHudVisuals()
        applyHudPositionFromConfig(lastState.floatingConfig, forceApply = true)
        clampHudIntoSafeBounds()
        updateNotification(lastState.isFloatingHiddenForLive)
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        renderLiveStage(lastState)
        applyHudPositionFromConfig(lastState.floatingConfig, forceApply = true)
        if (lastState.isPositionEditMode) {
            syncMarkerWindows(lastState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (MlbbGestureAccessibilityService.onPrepareScreenForTouch != null) {
            MlbbGestureAccessibilityService.onPrepareScreenForTouch = null
        }
        if (MlbbGestureAccessibilityService.onPrepareScreenForScan != null) {
            MlbbGestureAccessibilityService.onPrepareScreenForScan = null
        }
        overlayBitmapCache.evictAll()
        serviceScope.cancel()
        removeAllMarkerWindows()
        subMenuComposeOwner?.onDetach()
        subMenuComposeOwner = null
        singleHudView?.let { safeRemoveView(it) }
        singleHudView = null
        touchBlockerView?.let { safeRemoveView(it) }
        touchBlockerView = null
        liveStageView?.let { safeRemoveView(it) }
        liveStageView = null
        engine.setFloatingOverlayRunning(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(isHiddenForLive: Boolean) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        mgr?.notify(NOTIF_ID, buildNotification(isHiddenForLive))
    }

    private fun observeEngineState() {
        serviceScope.launch {
            engine.state.collectLatest { st ->
                val hiddenChanged = lastState.isFloatingHiddenForLive != st.isFloatingHiddenForLive
                val cfgRatioChanged = lastAppliedXRatio != st.floatingConfig.hudXRatio ||
                    lastAppliedYRatio != st.floatingConfig.hudYRatio
                lastState = st
                if (!isTemporarilyHiddenForScan) {
                    refreshSingleHudVisuals()
                    if (!isDraggingHud && !st.isFloatingHiddenForLive) {
                        if (cfgRatioChanged) {
                            applyHudPositionFromConfig(st.floatingConfig, forceApply = false)
                        } else {
                            clampHudIntoSafeBounds()
                        }
                    }
                    renderLiveStage(st)
                    syncMarkerWindows(st)
                }
                updateTouchBlockerState(
                    st,
                    temporarilyHiddenForAutomation = isTemporarilyHiddenForAutomation || isTemporarilyHiddenForScan
                )
                if (hiddenChanged) {
                    updateNotification(st.isFloatingHiddenForLive)
                }
            }
        }
    }

    // ==================== Layer 1: Non-Blocking Feedback & Uploaded Overlay Stage ====================

    private fun attachLiveStageWindow() {
        val container = FrameLayout(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            applyCutoutMode(this)
        }
        try {
            windowManager.addView(container, params)
            liveStageView = container
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal pasang live stage overlay: ${t.message}")
        }
    }

    private fun attachTouchBlockerWindow() {
        val blocker = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            isFocusable = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.argb(38, 15, 23, 42))
                setStroke(dp(2.5f), Color.argb(210, 239, 68, 68))
            }
            setOnTouchListener { _, _ -> true }
        }
        val pill = TextView(this).apply {
            text = "🔒 Layar Terkunci — Challenge Sedang Berjalan"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = roundedBoxBg(
                fillColor = Color.argb(220, 153, 27, 27),
                strokeColor = Color.rgb(252, 165, 165),
                strokePx = dp(1f),
                radiusPx = dp(16).toFloat()
            )
        }
        touchBlockerStatusPill = pill
        blocker.addView(
            pill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(24)
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            applyCutoutMode(this)
        }
        touchBlockerParams = params
        try {
            windowManager.addView(blocker, params)
            touchBlockerView = blocker
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal pasang touch blocker overlay: ${t.message}")
        }
    }

    private fun updateTouchBlockerState(
        st: UniversalEngineUiState,
        temporarilyHiddenForAutomation: Boolean
    ) {
        val blocker = touchBlockerView ?: return
        val params = touchBlockerParams ?: return
        val shouldLock = !temporarilyHiddenForAutomation &&
            st.blockTouchDuringChallenge &&
            st.isChallengeExecuting &&
            !st.isPositionEditMode &&
            !st.isEmergencyStopped

        val desiredVis = if (shouldLock) View.VISIBLE else View.GONE
        if (blocker.visibility != desiredVis) {
            blocker.visibility = desiredVis
        }
        if (shouldLock && st.cooldownStatusHint.isNotBlank()) {
            touchBlockerStatusPill?.text = "🔒 Layar Terkunci • ${st.cooldownStatusHint}"
        } else {
            touchBlockerStatusPill?.text = "🔒 Layar Terkunci — Challenge Sedang Berjalan"
        }
        val currentlyNotTouchable = (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        val desiredNotTouchable = !shouldLock
        if (currentlyNotTouchable != desiredNotTouchable) {
            params.flags = if (desiredNotTouchable) {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            try {
                windowManager.updateViewLayout(blocker, params)
            } catch (_: Throwable) {}
        }
    }

    private fun renderLiveStage(st: UniversalEngineUiState) {
        val stage = liveStageView ?: return
        stage.removeAllViews()
        val hasScanAreas = st.isPositionEditMode && !st.isFloatingHiddenForLive &&
            st.rules.any { it.isPinVisible && it.enableVisualCooldownWait }
        if (st.activeOverlays.isEmpty() && st.lastSimEvent == null && !hasScanAreas) return

        val (screenW, screenH) = getDisplaySizePx()

        if (hasScanAreas) {
            for (rule in st.rules) {
                if (!rule.isPinVisible || !rule.enableVisualCooldownWait) continue
                val touchSteps = rule.actionSequence.filter {
                    it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
                }
                val targetStep = rule.cooldownTargetStepId?.let { id ->
                    touchSteps.firstOrNull { it.id == id }
                } ?: touchSteps.firstOrNull()
                val cxNorm = ((targetStep?.posX ?: rule.overlayPosX) + rule.cooldownAreaOffsetX).coerceIn(0.02f, 0.98f)
                val cyNorm = ((targetStep?.posY ?: rule.overlayPosY) + rule.cooldownAreaOffsetY).coerceIn(0.02f, 0.98f)
                val boxW = (screenW * rule.cooldownAreaWidthRatio.coerceIn(0.025f, 0.45f)).roundToInt().coerceAtLeast(dp(24))
                val boxH = (screenH * rule.cooldownAreaHeightRatio.coerceIn(0.025f, 0.55f)).roundToInt().coerceAtLeast(dp(24))
                val left = ((cxNorm * screenW) - boxW / 2f).roundToInt().coerceIn(0, (screenW - boxW).coerceAtLeast(0))
                val top = ((cyNorm * screenH) - boxH / 2f).roundToInt().coerceIn(0, (screenH - boxH).coerceAtLeast(0))
                val ruleColor = rule.accentColorHex.toInt()
                val scanFrame = FrameLayout(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(6).toFloat()
                        setColor(Color.argb(38, Color.red(ruleColor), Color.green(ruleColor), Color.blue(ruleColor)))
                        setStroke(dp(1.8f), ruleColor, dp(6).toFloat(), dp(3).toFloat())
                    }
                }
                val tag = TextView(this).apply {
                    text = "⏱ CD"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(4), dp(1), dp(4), dp(1))
                    setBackgroundColor(Color.argb(190, 15, 23, 42))
                }
                scanFrame.addView(
                    tag,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.END
                    )
                )
                stage.addView(scanFrame, FrameLayout.LayoutParams(boxW, boxH).apply {
                    leftMargin = left
                    topMargin = top
                })
            }
        }

        for (ov in st.activeOverlays) {
            val scale = ov.scale.coerceIn(0.35f, 3.0f)
            val isProfileOverlay = ov.kind == ActionType.PROFILE_OVERLAY
            val isMemeOrCustomImage = (ov.kind == ActionType.CUSTOM_IMAGE || ov.kind == ActionType.MEME_OVERLAY) && ov.imageUri.isNotBlank()
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                if (isMemeOrCustomImage || isProfileOverlay) {
                    setPadding(0, 0, 0, 0)
                    setBackgroundColor(Color.TRANSPARENT)
                } else {
                    setPadding(dp(9 * scale.coerceAtMost(1.3f)), dp(5 * scale.coerceAtMost(1.3f)), dp(9 * scale.coerceAtMost(1.3f)), dp(5 * scale.coerceAtMost(1.3f)))
                    background = roundedBoxBg(
                        fillColor = Color.argb(195, 15, 23, 42),
                        strokeColor = Color.argb(175, 34, 211, 238),
                        strokePx = dp(0.9f),
                        radiusPx = dp(9).toFloat()
                    )
                }
            }
            if (isProfileOverlay) {
                val avatarPx = dp(76f * scale).coerceIn(dp(36), dp(220))
                val avatarBmp = ov.imageUri.takeIf { it.isNotBlank() }?.let { uriStr ->
                    resolveOverlayBitmapAsyncAware(uriStr, avatarPx)
                }
                if (avatarBmp != null) {
                    val circleBmp = createCircularFramedBitmap(avatarBmp, avatarPx, Color.rgb(34, 211, 238), dp(2.5f).toFloat())
                    val img = ImageView(this).apply {
                        setImageBitmap(circleBmp)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    card.addView(img, LinearLayout.LayoutParams(avatarPx, avatarPx))
                } else {
                    val cleanName = ov.title.replace("👤", "").trim().ifBlank { "P" }
                    val initial = cleanName.firstOrNull()?.uppercaseChar()?.toString() ?: "P"
                    val badge = TextView(this).apply {
                        text = initial
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, (22f * scale).coerceIn(13f, 44f))
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.argb(230, 14, 116, 144))
                            setStroke(dp(2.5f), Color.rgb(34, 211, 238))
                        }
                    }
                    card.addView(badge, LinearLayout.LayoutParams(avatarPx, avatarPx))
                }
            } else if (isMemeOrCustomImage) {
                val targetImgPx = dp(120f * scale).coerceIn(dp(48), dp(360))
                resolveOverlayBitmapAsyncAware(ov.imageUri, targetImgPx)?.let { bmp ->
                    val img = ImageView(this).apply {
                        setImageBitmap(bmp)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                        maxWidth = targetImgPx
                        maxHeight = targetImgPx
                        setPadding(dp(2), dp(2), dp(2), dp(2))
                        background = roundedBoxBg(
                            fillColor = Color.argb(110, 15, 23, 42),
                            strokeColor = Color.argb(160, 244, 114, 182),
                            strokePx = dp(1.2f),
                            radiusPx = dp(10).toFloat()
                        )
                    }
                    card.addView(img, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                }
            }
            if (ov.title.isNotBlank()) {
                val titleTv = TextView(this).apply {
                    text = ov.title
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * scale.coerceIn(0.75f, 1.45f))
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    maxLines = 2
                    if (isMemeOrCustomImage || isProfileOverlay) {
                        setPadding(dp(8), dp(3), dp(8), dp(3))
                        background = roundedBoxBg(
                            fillColor = Color.argb(195, 15, 23, 42),
                            strokeColor = if (isProfileOverlay) Color.argb(150, 34, 211, 238) else Color.TRANSPARENT,
                            strokePx = if (isProfileOverlay) dp(0.8f) else 0,
                            radiusPx = dp(8).toFloat()
                        )
                    }
                }
                card.addView(
                    titleTv,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        if (isMemeOrCustomImage || isProfileOverlay) topMargin = dp(3)
                    }
                )
            }
            if (isProfileOverlay && ov.subtitle.isNotBlank()) {
                val subTv = TextView(this).apply {
                    text = ov.subtitle
                    setTextColor(Color.rgb(165, 243, 252))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f * scale.coerceIn(0.75f, 1.3f))
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(dp(7), dp(2), dp(7), dp(2))
                    background = roundedBoxBg(
                        fillColor = Color.argb(180, 15, 23, 42),
                        strokeColor = Color.TRANSPARENT,
                        strokePx = 0,
                        radiusPx = dp(6).toFloat()
                    )
                }
                card.addView(
                    subTv,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(2)
                    }
                )
            }
            card.measure(
                View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
            )
            val cw = card.measuredWidth.coerceAtLeast(dp(60))
            val ch = card.measuredHeight.coerceAtLeast(dp(28))
            val left = ((ov.posX * screenW) - cw / 2f).roundToInt().coerceIn(0, (screenW - cw).coerceAtLeast(0))
            val top = ((ov.posY * screenH) - ch / 2f).roundToInt().coerceIn(0, (screenH - ch).coerceAtLeast(0))
            stage.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = left
                topMargin = top
            })
        }

        st.lastSimEvent?.let { sim ->
            val dotSize = dp(30)
            val a11yOk = MlbbGestureAccessibilityService.peekInstance() != null
            val ringColor = if (a11yOk) sim.colorHex.toInt() else Color.rgb(249, 115, 22)
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(120, Color.red(ringColor), Color.green(ringColor), Color.blue(ringColor)))
                    setStroke(dp(3f), ringColor)
                }
            }
            val left = ((sim.posX * screenW) - dotSize / 2f).roundToInt().coerceIn(0, (screenW - dotSize).coerceAtLeast(0))
            val top = ((sim.posY * screenH) - dotSize / 2f).roundToInt().coerceIn(0, (screenH - dotSize).coerceAtLeast(0))
            stage.addView(dot, FrameLayout.LayoutParams(dotSize, dotSize).apply {
                leftMargin = left
                topMargin = top
            })
        }
    }

    // ==================== Layer 2: External Trigger Pins OUTSIDE Floating (Colored + Per-Challenge Eye) ====================

    private fun setMarkerWindowsTemporaryHidden(hide: Boolean) {
        updateTouchBlockerState(lastState, temporarilyHiddenForAutomation = hide || isTemporarilyHiddenForScan)
        liveStageView?.visibility = if (hide || isTemporarilyHiddenForScan) View.INVISIBLE else View.VISIBLE
        val targetVis = if (hide || isTemporarilyHiddenForScan) View.INVISIBLE else View.VISIBLE
        for (entry in markerWindows.values) {
            if (entry.root.visibility != targetVis) {
                entry.root.visibility = targetVis
            }
            val shouldBeNotTouchable = hide || isTemporarilyHiddenForScan
            val currentlyNotTouchable = (entry.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
            if (currentlyNotTouchable != shouldBeNotTouchable) {
                entry.params.flags = if (shouldBeNotTouchable) {
                    entry.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    entry.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
                try {
                    windowManager.updateViewLayout(entry.root, entry.params)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun setAllFloatingWindowsHiddenForScan(hide: Boolean) {
        setMarkerWindowsTemporaryHidden(hide || isTemporarilyHiddenForAutomation)
        val hud = singleHudView
        val hParams = singleHudParams
        if (hud != null && hParams != null) {
            if (hide) {
                if (hud.visibility == View.VISIBLE) {
                    hud.visibility = View.INVISIBLE
                }
                if ((hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0) {
                    hParams.flags = hParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    try { windowManager.updateViewLayout(hud, hParams) } catch (_: Throwable) {}
                }
            } else {
                if ((hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                    hParams.flags = hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    try { windowManager.updateViewLayout(hud, hParams) } catch (_: Throwable) {}
                }
                refreshSingleHudVisuals()
            }
        }
    }

    private data class PinDescriptor(
        val key: String,
        val rule: UniversalTriggerRule,
        val step: UniversalActionStep?,
        val stepIndex: Int,
        val totalTouchSteps: Int,
        val normX: Float,
        val normY: Float
    )

    private fun buildPinDescriptors(st: UniversalEngineUiState): List<PinDescriptor> {
        val list = mutableListOf<PinDescriptor>()
        for (rule in st.rules) {
            if (!rule.isPinVisible) continue
            val touchSteps = rule.actionSequence.filter {
                it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
            }
            if (touchSteps.isEmpty()) {
                list += PinDescriptor(
                    key = "${rule.id}::root",
                    rule = rule,
                    step = null,
                    stepIndex = 0,
                    totalTouchSteps = 0,
                    normX = rule.overlayPosX,
                    normY = rule.overlayPosY
                )
            } else {
                touchSteps.forEachIndexed { idx, step ->
                    list += PinDescriptor(
                        key = "${rule.id}::${step.id}",
                        rule = rule,
                        step = step,
                        stepIndex = idx + 1,
                        totalTouchSteps = touchSteps.size,
                        normX = step.posX ?: rule.overlayPosX,
                        normY = step.posY ?: rule.overlayPosY
                    )
                }
            }
        }
        return list
    }

    private fun syncMarkerWindows(st: UniversalEngineUiState) {
        if (!st.isPositionEditMode || st.isFloatingHiddenForLive) {
            removeAllMarkerWindows()
            return
        }

        val descriptors = buildPinDescriptors(st)
        val activeKeys = descriptors.map { it.key }.toSet()
        val toRemove = markerWindows.keys.filter { it !in activeKeys }
        for (k in toRemove) {
            markerWindows.remove(k)?.let { safeRemoveView(it.root) }
        }

        val (screenW, screenH) = getDisplaySizePx()
        val ringHalf = dp(19)

        for (desc in descriptors) {
            if (desc.key == draggingRuleId) continue

            val rule = desc.rule
            val step = desc.step
            val isSelected = rule.id == st.activePinRuleId &&
                (step == null || st.activePinStepId == null || step.id == st.activePinStepId)
            val ruleColor = rule.accentColorHex.toInt()
            val shortBadge = buildPinBadgeText(desc)
            val pinSymbol = if (desc.totalTouchSteps > 1) "${desc.stepIndex}" else "⊕"
            val existing = markerWindows[desc.key]

            if (existing == null) {
                createMarkerWindow(desc, isSelected, screenW, screenH)
            } else {
                existing.badgeLabel.text = shortBadge
                existing.badgeLabel.background = roundedBoxBg(
                    fillColor = Color.argb(230, 15, 23, 42),
                    strokeColor = ruleColor,
                    strokePx = dp(if (isSelected) 1.8f else 1.1f),
                    radiusPx = dp(6).toFloat()
                )
                existing.crosshairView.text = pinSymbol
                existing.crosshairView.setTextColor(if (isSelected) Color.WHITE else ruleColor)
                existing.crosshairView.background = crosshairBg(ruleColor, rule.enabled, isSelected)
                existing.root.measure(
                    View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
                )
                val mw = existing.root.measuredWidth.coerceAtLeast(dp(38))
                val mh = existing.root.measuredHeight.coerceAtLeast(dp(38))
                existing.params.x = ((desc.normX * screenW) - mw / 2f).roundToInt()
                    .coerceIn(0, (screenW - mw).coerceAtLeast(0))
                existing.params.y = ((desc.normY * screenH) - ringHalf).roundToInt()
                    .coerceIn(0, (screenH - mh).coerceAtLeast(0))
                try {
                    windowManager.updateViewLayout(existing.root, existing.params)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun buildPinBadgeText(desc: PinDescriptor): String {
        val xPct = (desc.normX * 100).roundToInt()
        val yPct = (desc.normY * 100).roundToInt()
        val cleanName = desc.rule.triggerName.trim().ifBlank { desc.rule.triggerSource.displayName }.take(10)
        val stepTag = if (desc.totalTouchSteps > 1) " #${desc.stepIndex}" else ""
        val swipeTag = if (desc.step?.actionType == ActionType.SIMULATED_SWIPE) " ↗" else ""
        val muteTag = if (desc.rule.enabled) "" else " [OFF]"
        return "● $cleanName$stepTag$swipeTag$muteTag ($xPct,$yPct)"
    }

    private fun crosshairBg(ruleColor: Int, enabled: Boolean, isSelected: Boolean): GradientDrawable {
        val r = Color.red(ruleColor)
        val g = Color.green(ruleColor)
        val b = Color.blue(ruleColor)
        val fill = when {
            isSelected -> Color.argb(150, r, g, b)
            enabled -> Color.argb(72, r, g, b)
            else -> Color.argb(110, 30, 41, 59)
        }
        val strokeColor = if (enabled) ruleColor else Color.argb(165, r, g, b)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(dp(if (isSelected) 3.0f else 2.2f), strokeColor)
        }
    }

    private fun createMarkerWindow(
        desc: PinDescriptor,
        isSelected: Boolean,
        screenW: Int,
        screenH: Int
    ) {
        val rule = desc.rule
        val step = desc.step
        val ringSize = dp(38)
        val ringHalf = ringSize / 2
        val ruleColor = rule.accentColorHex.toInt()
        val pinSymbol = if (desc.totalTouchSteps > 1) "${desc.stepIndex}" else "⊕"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        val crosshair = TextView(this).apply {
            text = pinSymbol
            setTextColor(if (isSelected) Color.WHITE else ruleColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (desc.totalTouchSteps > 1) 15f else 19f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = crosshairBg(ruleColor, rule.enabled, isSelected)
        }

        val badgeLabel = TextView(this).apply {
            text = buildPinBadgeText(desc)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            maxLines = 1
            background = roundedBoxBg(
                fillColor = Color.argb(230, 15, 23, 42),
                strokeColor = ruleColor,
                strokePx = dp(if (isSelected) 1.8f else 1.1f),
                radiusPx = dp(6).toFloat()
            )
        }

        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, 0)
            visibility = View.GONE
        }
        val testPinBtn = makeMicroPinBtn("▶ Tes", Color.rgb(16, 185, 129), Color.BLACK) {
            engine.forceTestRuleNow(rule.id) { resultSummary ->
                mainHandler.post {
                    Toast.makeText(this, resultSummary, Toast.LENGTH_SHORT).show()
                }
            }
        }
        val hideEyeBtn = makeMicroPinBtn("🙈", Color.rgb(71, 85, 105), Color.WHITE) {
            engine.toggleRulePinVisibility(rule.id)
        }
        val editPinBtn = makeMicroPinBtn("✎", Color.rgb(14, 116, 144), Color.WHITE) {
            engine.selectActivePin(rule.id, step?.id)
            selectedRuleIdForSubMenu = rule.id
            isSubMenuExpanded = true
            engine.updateFloatingConfig { it.copy(hudCollapsed = false) }
            refreshSingleHudVisuals()
            clampHudIntoSafeBounds()
        }
        var delArmed = false
        var delResetRunnable: Runnable? = null
        val idleDelBg = roundedBoxBg(Color.rgb(51, 65, 85), Color.argb(140, 239, 68, 68), dp(1f), dp(6).toFloat())
        val armedDelBg = roundedBoxBg(Color.rgb(220, 38, 38), Color.rgb(254, 202, 202), dp(1.2f), dp(6).toFloat())
        val delPinBtn = makeMicroPinBtn("✕", Color.rgb(51, 65, 85), Color.WHITE) {}.apply {
            background = idleDelBg
            setOnClickListener {
                if (!delArmed) {
                    delArmed = true
                    background = armedDelBg
                    setTextColor(Color.WHITE)
                    delResetRunnable?.let { mainHandler.removeCallbacks(it) }
                    val reset = Runnable {
                        delArmed = false
                        background = idleDelBg
                        setTextColor(Color.WHITE)
                    }
                    delResetRunnable = reset
                    mainHandler.postDelayed(reset, 3000L)
                } else {
                    delResetRunnable?.let { mainHandler.removeCallbacks(it) }
                    delArmed = false
                    if (step != null && desc.totalTouchSteps > 1) {
                        engine.removeChoreographyStep(rule.id, step.id)
                    } else {
                        engine.deleteRule(rule.id)
                    }
                }
            }
        }
        fun btnLp() = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(3) }
        quickRow.addView(testPinBtn, btnLp())
        quickRow.addView(hideEyeBtn, btnLp())
        quickRow.addView(editPinBtn, btnLp())
        quickRow.addView(delPinBtn)

        root.addView(crosshair, LinearLayout.LayoutParams(ringSize, ringSize))
        root.addView(badgeLabel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
        root.addView(quickRow)

        root.measure(
            View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        )
        val mw = root.measuredWidth.coerceAtLeast(ringSize)
        val mh = root.measuredHeight.coerceAtLeast(ringSize)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((desc.normX * screenW) - mw / 2f).roundToInt().coerceIn(0, (screenW - mw).coerceAtLeast(0))
            y = ((desc.normY * screenH) - ringHalf).roundToInt().coerceIn(0, (screenH - mh).coerceAtLeast(0))
            applyCutoutMode(this)
        }

        val entry = MarkerWindowEntry(root, crosshair, badgeLabel, quickRow, params)

        var startX = 0
        var startY = 0
        var touchDownRawX = 0f
        var touchDownRawY = 0f
        var moved = false

        val dragListener = View.OnTouchListener { _, event ->
            val (curW, curH) = getDisplaySizePx()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    draggingRuleId = desc.key
                    engine.selectActivePin(rule.id, step?.id)
                    startX = params.x
                    startY = params.y
                    touchDownRawX = event.rawX
                    touchDownRawY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchDownRawX).roundToInt()
                    val dy = (event.rawY - touchDownRawY).roundToInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                    val vw = root.width.coerceAtLeast(ringSize)
                    val vh = root.height.coerceAtLeast(ringSize)
                    params.x = (startX + dx).coerceIn(0, (curW - vw).coerceAtLeast(0))
                    params.y = (startY + dy).coerceIn(0, (curH - vh).coerceAtLeast(0))
                    try {
                        windowManager.updateViewLayout(root, params)
                    } catch (_: Throwable) {}

                    val centerX = ((params.x + vw / 2f) / curW.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                    val centerY = ((params.y + ringHalf.toFloat()) / curH.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                    val latestRule = engine.state.value.rules.firstOrNull { it.id == rule.id } ?: rule
                    val shortName = latestRule.triggerName.trim().ifBlank { latestRule.triggerSource.displayName }.take(10)
                    val stepTag = if (desc.totalTouchSteps > 1) " #${desc.stepIndex}" else ""
                    badgeLabel.text = "● $shortName$stepTag (${(centerX * 100).roundToInt()},${(centerY * 100).roundToInt()})"
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val vw = root.width.coerceAtLeast(ringSize)
                    draggingRuleId = null
                    if (moved) {
                        val centerX = ((params.x + vw / 2f) / curW.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                        val centerY = ((params.y + ringHalf.toFloat()) / curH.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                        engine.updateStepPosition(rule.id, step?.id, centerX, centerY)
                    } else {
                        entry.isActionsOpen = !entry.isActionsOpen
                        quickRow.visibility = if (entry.isActionsOpen) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }

        crosshair.setOnTouchListener(dragListener)
        badgeLabel.setOnTouchListener(dragListener)

        try {
            windowManager.addView(root, params)
            markerWindows[desc.key] = entry
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal tambah marker window: ${t.message}")
        }
    }

    private fun removeAllMarkerWindows() {
        for (entry in markerWindows.values) {
            safeRemoveView(entry.root)
        }
        markerWindows.clear()
        draggingRuleId = null
    }

    // ==================== Layer 3: ONE Safe-Bounded Draggable Floating + Precision D-Pad + Dropdown ====================

    private fun attachSingleFloatingHudWindow() {
        val cfg = engine.state.value.floatingConfig
        val owner = FloatingComposeOwner().apply { onAttach() }
        subMenuComposeOwner = owner

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            alpha = cfg.hudOpacity
            background = roundedBoxBg(
                fillColor = Color.argb(236, 15, 23, 42),
                strokeColor = Color.rgb(34, 211, 238),
                strokePx = dp(1.2f),
                radiusPx = dp(12).toFloat()
            )
        }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)

        // Tidak memakai FLAG_LAYOUT_NO_LIMITS agar floating HUD TIDAK pernah tersesat keluar layar atau masuk area terlarang sistem.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            applyCutoutMode(this)
        }

        var dragStartX = 0
        var dragStartY = 0

        val headerDragContainer = DraggableFloatingContainer(
            context = this,
            onDragStart = {
                isDraggingHud = true
                dragStartX = params.x
                dragStartY = params.y
            },
            onDragMove = { dx, dy ->
                val safe = getSafeHudBoundsPx()
                val vw = root.width.coerceAtLeast(dp(42))
                val vh = root.height.coerceAtLeast(dp(36))
                val minX = safe.left
                val maxX = (safe.right - vw).coerceAtLeast(minX)
                val minY = safe.top
                val maxY = (safe.bottom - vh).coerceAtLeast(minY)
                params.x = (dragStartX + dx).coerceIn(minX, maxX)
                params.y = (dragStartY + dy).coerceIn(minY, maxY)
                try {
                    windowManager.updateViewLayout(root, params)
                } catch (_: Throwable) {}
            },
            onDragEnd = { moved ->
                isDraggingHud = false
                if (moved) {
                    val safe = getSafeHudBoundsPx()
                    val vw = root.width.coerceAtLeast(dp(42))
                    val vh = root.height.coerceAtLeast(dp(36))
                    val minX = safe.left
                    val maxX = (safe.right - vw).coerceAtLeast(minX)
                    val minY = safe.top
                    val maxY = (safe.bottom - vh).coerceAtLeast(minY)
                    val spanX = (maxX - minX).coerceAtLeast(1)
                    val spanY = (maxY - minY).coerceAtLeast(1)
                    val xRatio = ((params.x - minX).toFloat() / spanX).coerceIn(0f, 1f)
                    val yRatio = ((params.y - minY).toFloat() / spanY).coerceIn(0f, 1f)
                    lastAppliedXRatio = xRatio
                    lastAppliedYRatio = yRatio
                    engine.updateFloatingConfig {
                        it.copy(hudXRatio = xRatio, hudYRatio = yRatio)
                    }
                } else {
                    val nextCollapsed = !lastState.floatingConfig.hudCollapsed
                    if (nextCollapsed) {
                        isSubMenuExpanded = false
                        setHudFocusableForKeyboard(false)
                    }
                    engine.updateFloatingConfig { it.copy(hudCollapsed = nextCollapsed) }
                }
            }
        ).apply {
            orientation = LinearLayout.VERTICAL
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        hudTopRow = topRow

        // Handle grip: geser bebas dari area mana pun; ketuk singkat grip untuk ciutkan/buka bubble minimalis
        val titleBtn = TextView(this).apply {
            text = "⠿"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumWidth = dp(28)
            minimumHeight = dp(28)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = roundedBoxBg(Color.argb(145, 30, 41, 59), Color.argb(80, 34, 211, 238), dp(0.8f), dp(7).toFloat())
            setOnClickListener {
                if (!isDraggingHud) {
                    val nextCollapsed = !lastState.floatingConfig.hudCollapsed
                    if (nextCollapsed) {
                        isSubMenuExpanded = false
                        setHudFocusableForKeyboard(false)
                    }
                    engine.updateFloatingConfig { it.copy(hudCollapsed = nextCollapsed) }
                }
            }
        }
        hudTitleBtn = titleBtn

        val engineBtn = makeHudButton("⚡", Color.rgb(14, 116, 144), Color.WHITE) {
            engine.setEngineEnabled(!engine.state.value.isEngineEnabled)
        }
        btnEngineTiny = engineBtn

        val lockBtn = makeHudButton("🔒", Color.rgb(153, 27, 27), Color.WHITE) {
            val nextLock = !engine.state.value.blockTouchDuringChallenge
            engine.setBlockTouchDuringChallenge(nextLock)
            Toast.makeText(
                this,
                if (nextLock) "🔒 Kunci Sentuhan Saat Challenge: AKTIF" else "🔓 Kunci Sentuhan Saat Challenge: NONAKTIF",
                Toast.LENGTH_SHORT
            ).show()
        }
        btnLockTiny = lockBtn

        val pinBtn = makeHudButton("📍", Color.rgb(51, 65, 85), Color.WHITE) {
            val next = !engine.state.value.isPositionEditMode
            engine.setPositionEditMode(next)
            if (next && engine.state.value.rules.none { r ->
                    r.actionSequence.any { it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE) }
                }
            ) {
                Toast.makeText(this, "Buka ☰ → + Konsep untuk pasang pin", Toast.LENGTH_SHORT).show()
            }
            refreshSingleHudVisuals()
            clampHudIntoSafeBounds()
        }
        btnPinTiny = pinBtn

        val menuBtn = makeHudButton("☰", Color.rgb(51, 65, 85), Color.WHITE) {
            isSubMenuExpanded = !isSubMenuExpanded
            if (!isSubMenuExpanded) {
                selectedRuleIdForSubMenu = null
                setHudFocusableForKeyboard(false)
            }
            refreshSingleHudVisuals()
            clampHudIntoSafeBounds()
        }
        btnSubMenuTiny = menuBtn

        val minBtn = makeHudButton("—", Color.rgb(51, 65, 85), Color.WHITE) {
            isSubMenuExpanded = false
            setHudFocusableForKeyboard(false)
            engine.updateFloatingConfig { it.copy(hudCollapsed = true) }
        }
        btnMinTiny = minBtn

        val closeBtn = makeHudButton("✕", Color.rgb(127, 29, 29), Color.WHITE) {
            stopSelf()
        }
        btnCloseTiny = closeBtn

        val gap = dp(2.5f)
        fun gapLp() = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = gap }

        topRow.addView(titleBtn, gapLp())
        topRow.addView(engineBtn, gapLp())
        topRow.addView(lockBtn, gapLp())
        topRow.addView(pinBtn, gapLp())
        topRow.addView(menuBtn, gapLp())
        topRow.addView(minBtn, gapLp())
        topRow.addView(closeBtn)

        val dpadPanel = buildPrecisionCursorBar()
        dpadContainer = dpadPanel

        headerDragContainer.addView(topRow)
        headerDragContainer.addView(dpadPanel)

        val subMenuHolder = FrameLayout(this).apply {
            visibility = View.GONE
        }
        subMenuContainer = subMenuHolder

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                MyApplicationTheme(darkTheme = true) {
                    FloatingInteractionPanelContent(
                        selectedRuleIdToEdit = selectedRuleIdForSubMenu,
                        onClearSelectedRuleToEdit = { selectedRuleIdForSubMenu = null },
                        onCloseSubMenu = {
                            isSubMenuExpanded = false
                            setHudFocusableForKeyboard(false)
                            refreshSingleHudVisuals()
                            clampHudIntoSafeBounds()
                        },
                        onEnterPositionDragMode = {
                            isSubMenuExpanded = false
                            setHudFocusableForKeyboard(false)
                            engine.setPositionEditMode(true)
                            refreshSingleHudVisuals()
                            clampHudIntoSafeBounds()
                        },
                        onHideFloatingForLive = {
                            isSubMenuExpanded = false
                            setHudFocusableForKeyboard(false)
                            engine.setFloatingHiddenForLive(true)
                        },
                        onResetHudPosition = {
                            engine.resetFloatingPosition()
                            applyHudPositionFromConfig(engine.state.value.floatingConfig, forceApply = true)
                        },
                        onKeyboardFocusChanged = { needFocus ->
                            setHudFocusableForKeyboard(needFocus)
                        }
                    )
                }
            }
        }
        subMenuComposeView = composeView
        val dropWidth = computeSubMenuWidthPx(cfg)
        subMenuHolder.addView(
            composeView,
            FrameLayout.LayoutParams(dropWidth, FrameLayout.LayoutParams.WRAP_CONTENT)
        )

        root.addView(headerDragContainer)
        root.addView(subMenuHolder)

        try {
            windowManager.addView(root, params)
            singleHudView = root
            singleHudParams = params
            refreshSingleHudVisuals()
            applyHudPositionFromConfig(cfg, forceApply = true)
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal pasang floating HUD: ${t.message}")
        }
    }

    private fun buildPrecisionCursorBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(6), dp(5), dp(6), dp(4))
            background = roundedBoxBg(
                fillColor = Color.argb(215, 2, 6, 23),
                strokeColor = Color.argb(140, 16, 185, 129),
                strokePx = dp(1),
                radiusPx = dp(8).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5) }
        }

        // Row 1: Pilih Challenge + Warna + Koordinat + Tutup/Buka Mata + Step Halus/Cepat
        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val prevBtn = makeMiniControlBtn("‹", Color.rgb(30, 41, 59), Color.WHITE) {
            engine.cycleActivePin(-1)
        }
        val colorDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.CYAN)
            }
        }
        dpadColorDot = colorDot

        val targetLbl = TextView(this).apply {
            text = "Target"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
        }
        dpadTargetLabel = targetLbl

        val coordLbl = TextView(this).apply {
            text = "50%,50%"
            setTextColor(Color.rgb(34, 211, 238))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
        }
        dpadCoordLabel = coordLbl

        val nextBtn = makeMiniControlBtn("›", Color.rgb(30, 41, 59), Color.WHITE) {
            engine.cycleActivePin(+1)
        }

        val eyeBtn = makeMiniControlBtn("👁", Color.rgb(51, 65, 85), Color.WHITE) {
            val activeRule = resolveActiveDpadRule(lastState) ?: return@makeMiniControlBtn
            engine.toggleRulePinVisibility(activeRule.id)
        }
        dpadEyeBtn = eyeBtn

        val stepBtn = makeMiniControlBtn("Halus", Color.rgb(14, 116, 144), Color.WHITE) {
            isFinePrecisionStep = !isFinePrecisionStep
            refreshPrecisionBarVisuals(lastState)
        }
        dpadStepBtn = stepBtn

        infoRow.addView(prevBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) })
        infoRow.addView(colorDot, LinearLayout.LayoutParams(dp(11), dp(11)).apply { marginEnd = dp(4) })
        infoRow.addView(targetLbl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        infoRow.addView(coordLbl, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) })
        infoRow.addView(nextBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) })
        infoRow.addView(eyeBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) })
        infoRow.addView(stepBtn)

        // Row 2: D-Pad Panah Presisi ◀ ▲ ▼ ▶ + ▶ Tes
        val arrowRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        dpadArrowRow = arrowRow

        val leftBtn = makeArrowRepeatBtn("◀") { step -> nudgeActivePin(-step, 0f) }
        val upBtn = makeArrowRepeatBtn("▲") { step -> nudgeActivePin(0f, -step) }
        val downBtn = makeArrowRepeatBtn("▼") { step -> nudgeActivePin(0f, +step) }
        val rightBtn = makeArrowRepeatBtn("▶") { step -> nudgeActivePin(+step, 0f) }
        val addStepBtn = makeMiniControlBtn("+Step", Color.rgb(168, 85, 247), Color.WHITE) {
            val activeRule = resolveActiveDpadRule(lastState) ?: return@makeMiniControlBtn
            val added = engine.addChoreographyTouchStep(activeRule.id, ActionType.SIMULATED_CLICK)
            if (added != null) {
                Toast.makeText(this, "➕ Langkah koreografi baru ditambahkan ke ${activeRule.triggerName}", Toast.LENGTH_SHORT).show()
            }
        }
        val tesBtn = makeMiniControlBtn("▶ Tes", Color.rgb(16, 185, 129), Color.BLACK) {
            val activeRule = resolveActiveDpadRule(lastState) ?: return@makeMiniControlBtn
            engine.forceTestRuleNow(activeRule.id) { res ->
                mainHandler.post {
                    Toast.makeText(this, "${activeRule.triggerName}: $res", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun arrowLp() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
        arrowRow.addView(leftBtn, arrowLp())
        arrowRow.addView(upBtn, arrowLp())
        arrowRow.addView(downBtn, arrowLp())
        arrowRow.addView(rightBtn, arrowLp())
        arrowRow.addView(addStepBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) })
        arrowRow.addView(tesBtn)

        // Alternatif Row jika seluruh mata challenge tertutup
        val hiddenBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, dp(4), 0, 0)
        }
        dpadHiddenBannerRow = hiddenBanner
        val hiddenInfo = TextView(this).apply {
            text = "🙈 Pin challenge ini sedang disembunyikan"
            setTextColor(Color.rgb(251, 146, 60))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val showEyeNowBtn = makeMiniControlBtn("👁 Buka Pin", Color.rgb(14, 116, 144), Color.WHITE) {
            val activeRule = resolveActiveDpadRule(lastState)
            if (activeRule != null && !activeRule.isPinVisible) {
                engine.toggleRulePinVisibility(activeRule.id)
            } else {
                engine.setAllPinsVisible(true)
            }
        }
        hiddenBanner.addView(hiddenInfo)
        hiddenBanner.addView(showEyeNowBtn)

        bar.addView(infoRow)
        bar.addView(arrowRow)
        bar.addView(hiddenBanner)
        return bar
    }

    private fun resolveActiveDpadRule(st: UniversalEngineUiState): UniversalTriggerRule? {
        return st.rules.firstOrNull { it.id == st.activePinRuleId }
            ?: st.rules.firstOrNull { it.isPinVisible }
            ?: st.rules.firstOrNull()
    }

    private fun resolveActiveDpadStep(rule: UniversalTriggerRule, st: UniversalEngineUiState): UniversalActionStep? {
        val touchSteps = rule.actionSequence.filter {
            it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
        }
        return touchSteps.firstOrNull { it.id == st.activePinStepId } ?: touchSteps.firstOrNull()
    }

    private fun nudgeActivePin(dx: Float, dy: Float) {
        val activeRule = resolveActiveDpadRule(lastState) ?: return
        if (!activeRule.isPinVisible) {
            engine.toggleRulePinVisibility(activeRule.id)
        }
        val activeStep = resolveActiveDpadStep(activeRule, lastState)
        engine.nudgeStepPosition(activeRule.id, activeStep?.id, dx, dy)
    }

    private fun makeMiniControlBtn(
        label: String,
        bgColor: Int,
        fgColor: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumWidth = dp(28)
            minimumHeight = dp(30)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = roundedBoxBg(bgColor, Color.TRANSPARENT, 0, dp(6).toFloat())
            setOnClickListener { onClick() }
        }
    }

    private fun makeArrowRepeatBtn(
        symbol: String,
        onStep: (Float) -> Unit
    ): TextView {
        val repeatHandler = Handler(Looper.getMainLooper())
        var holding = false
        val stepRunner = object : Runnable {
            override fun run() {
                if (!holding) return
                val step = if (isFinePrecisionStep) 0.0025f else 0.015f
                onStep(step)
                repeatHandler.postDelayed(this, 65L)
            }
        }
        return TextView(this).apply {
            text = symbol
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumWidth = dp(34)
            minimumHeight = dp(32)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = roundedBoxBg(Color.rgb(30, 41, 59), Color.argb(140, 34, 211, 238), dp(1), dp(6).toFloat())
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        holding = true
                        val step = if (isFinePrecisionStep) 0.0025f else 0.015f
                        onStep(step)
                        repeatHandler.removeCallbacks(stepRunner)
                        repeatHandler.postDelayed(stepRunner, 260L)
                        v.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        holding = false
                        repeatHandler.removeCallbacks(stepRunner)
                        v.isPressed = false
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun refreshPrecisionBarVisuals(st: UniversalEngineUiState) {
        val bar = dpadContainer ?: return
        val showDpad = st.isPositionEditMode && !st.floatingConfig.hudCollapsed && !st.isFloatingHiddenForLive
        bar.visibility = if (showDpad) View.VISIBLE else View.GONE
        if (!showDpad) return

        val activeRule = resolveActiveDpadRule(st)
        if (activeRule == null) {
            dpadTargetLabel?.text = "Belum ada challenge"
            dpadCoordLabel?.text = "-"
            dpadArrowRow?.visibility = View.GONE
            dpadHiddenBannerRow?.visibility = View.GONE
            return
        }

        val ruleColor = activeRule.accentColorHex.toInt()
        (dpadColorDot?.background as? GradientDrawable)?.setColor(ruleColor)
        val touchSteps = activeRule.actionSequence.filter {
            it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
        }
        val activeStep = resolveActiveDpadStep(activeRule, st)
        val stepIndex = if (activeStep != null) touchSteps.indexOfFirst { it.id == activeStep.id }.coerceAtLeast(0) + 1 else 0
        val stepNamePart = activeStep?.stepName?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
        val stepLabel = (if (touchSteps.size > 1 && stepIndex > 0) " #$stepIndex/${touchSteps.size}" else "") + stepNamePart
        val curX = activeStep?.posX ?: activeRule.overlayPosX
        val curY = activeStep?.posY ?: activeRule.overlayPosY
        dpadTargetLabel?.text = "${activeRule.triggerName}$stepLabel"
        dpadCoordLabel?.text = String.format(
            Locale.US,
            "%.1f%%,%.1f%%",
            curX * 100f,
            curY * 100f
        )
        dpadEyeBtn?.apply {
            text = if (activeRule.isPinVisible) "👁" else "🙈"
            background = roundedBoxBg(
                fillColor = if (activeRule.isPinVisible) Color.rgb(14, 116, 144) else Color.rgb(71, 85, 105),
                strokeColor = Color.TRANSPARENT,
                strokePx = 0,
                radiusPx = dp(6).toFloat()
            )
        }
        dpadStepBtn?.apply {
            text = if (isFinePrecisionStep) "Halus" else "Cepat"
            background = roundedBoxBg(
                fillColor = if (isFinePrecisionStep) Color.rgb(14, 116, 144) else Color.rgb(168, 85, 247),
                strokeColor = Color.TRANSPARENT,
                strokePx = 0,
                radiusPx = dp(6).toFloat()
            )
        }

        dpadArrowRow?.visibility = if (activeRule.isPinVisible) View.VISIBLE else View.GONE
        dpadHiddenBannerRow?.visibility = if (activeRule.isPinVisible) View.GONE else View.VISIBLE
    }

    private fun setHudFocusableForKeyboard(focusable: Boolean) {
        val hud = singleHudView ?: return
        val params = singleHudParams ?: return
        val currentlyFocusable = (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0
        if (currentlyFocusable != focusable) {
            params.flags = if (focusable) {
                (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            } else {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            }
            try {
                windowManager.updateViewLayout(hud, params)
            } catch (_: Throwable) {}
        }
        val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        if (focusable) {
            hud.post {
                hud.requestFocus()
                val target = hud.findFocus() ?: hud
                imm?.showSoftInput(target, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            imm?.hideSoftInputFromWindow(hud.windowToken, 0)
        }
    }

    private fun applyHudPositionFromConfig(cfg: FloatingWindowConfig, forceApply: Boolean) {
        val hud = singleHudView ?: return
        val params = singleHudParams ?: return
        val safe = getSafeHudBoundsPx()
        val maxMeasureW = (safe.width()).coerceAtLeast(dp(60))
        val maxMeasureH = (safe.height()).coerceAtLeast(dp(48))
        hud.measure(
            View.MeasureSpec.makeMeasureSpec(maxMeasureW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxMeasureH, View.MeasureSpec.AT_MOST)
        )
        val w = hud.measuredWidth.coerceAtLeast(dp(42))
        val h = hud.measuredHeight.coerceAtLeast(dp(36))
        val minX = safe.left
        val maxX = (safe.right - w).coerceAtLeast(minX)
        val minY = safe.top
        val maxY = (safe.bottom - h).coerceAtLeast(minY)
        val targetX = (minX + (cfg.hudXRatio * (maxX - minX)).roundToInt()).coerceIn(minX, maxX)
        val targetY = (minY + (cfg.hudYRatio * (maxY - minY)).roundToInt()).coerceIn(minY, maxY)
        lastAppliedXRatio = cfg.hudXRatio
        lastAppliedYRatio = cfg.hudYRatio
        if (forceApply || params.x != targetX || params.y != targetY) {
            params.x = targetX
            params.y = targetY
            try {
                windowManager.updateViewLayout(hud, params)
            } catch (_: Throwable) {}
        }
    }

    private fun clampHudIntoSafeBounds() {
        val hud = singleHudView ?: return
        val params = singleHudParams ?: return
        val safe = getSafeHudBoundsPx()
        hud.measure(
            View.MeasureSpec.makeMeasureSpec(safe.width().coerceAtLeast(dp(60)), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(safe.height().coerceAtLeast(dp(48)), View.MeasureSpec.AT_MOST)
        )
        val w = hud.measuredWidth.coerceAtLeast(dp(42))
        val h = hud.measuredHeight.coerceAtLeast(dp(36))
        val minX = safe.left
        val maxX = (safe.right - w).coerceAtLeast(minX)
        val minY = safe.top
        val maxY = (safe.bottom - h).coerceAtLeast(minY)
        val clampedX = params.x.coerceIn(minX, maxX)
        val clampedY = params.y.coerceIn(minY, maxY)
        if (clampedX != params.x || clampedY != params.y) {
            params.x = clampedX
            params.y = clampedY
            try {
                windowManager.updateViewLayout(hud, params)
            } catch (_: Throwable) {}
        }
    }

    private fun computeSubMenuWidthPx(cfg: FloatingWindowConfig): Int {
        val safeBounds = getSafeHudBoundsPx()
        val maxSafe = (safeBounds.width() - dp(14)).coerceAtLeast(dp(210))
        val targetDp = 175f + cfg.panelWidthRatio.coerceIn(0.38f, 0.98f) * 165f
        return minOf(dp(targetDp), maxSafe).coerceAtLeast(minOf(dp(220), maxSafe))
    }

    private fun refreshSingleHudVisuals() {
        val st = lastState
        val cfg = st.floatingConfig
        val hud = singleHudView ?: return

        if (st.isFloatingHiddenForLive) {
            hud.visibility = View.GONE
            return
        }
        hud.visibility = View.VISIBLE

        val s = cfg.hudScale.coerceIn(0.65f, 1.35f)
        val collapsedBubble = cfg.hudCollapsed
        val showSubMenu = !collapsedBubble && isSubMenuExpanded
        val a11yReady = MlbbGestureAccessibilityService.peekInstance() != null

        hud.apply {
            alpha = cfg.hudOpacity
            val strokeCol = when {
                st.isPositionEditMode -> Color.rgb(16, 185, 129)
                !a11yReady -> Color.rgb(249, 115, 22)
                else -> Color.argb(190, 34, 211, 238)
            }
            if (collapsedBubble) {
                setPadding(dp(7 * s), dp(7 * s), dp(7 * s), dp(7 * s))
                background = roundedBoxBg(Color.argb(215, 15, 23, 42), strokeCol, dp(1.2f), dp(20 * s).toFloat())
            } else {
                setPadding(dp(4.5f * s), dp(4f * s), dp(4.5f * s), dp(4f * s))
                background = roundedBoxBg(Color.argb(218, 15, 23, 42), strokeCol, dp(1f), dp(10 * s).toFloat())
            }
        }

        val btnVis = if (collapsedBubble) View.GONE else View.VISIBLE
        btnEngineTiny?.visibility = btnVis
        btnLockTiny?.visibility = btnVis
        btnPinTiny?.visibility = btnVis
        btnSubMenuTiny?.visibility = btnVis
        btnMinTiny?.visibility = btnVis
        btnCloseTiny?.visibility = btnVis
        subMenuContainer?.visibility = if (showSubMenu) View.VISIBLE else View.GONE

        subMenuComposeView?.let { cv ->
            val targetDropW = computeSubMenuWidthPx(cfg)
            val lp = cv.layoutParams
            if (lp != null && abs(lp.width - targetDropW) >= dp(4)) {
                lp.width = targetDropW
                cv.layoutParams = lp
            }
        }

        hudTitleBtn?.apply {
            text = if (collapsedBubble) "⚔️" else "⠿"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (collapsedBubble) 14.5f else 11.5f) * s)
            setPadding(dp(5f * s), dp(4f * s), dp(5f * s), dp(4f * s))
            minimumWidth = dp(25 * s)
            minimumHeight = dp(25 * s)
            (layoutParams as? LinearLayout.LayoutParams)?.marginEnd = if (collapsedBubble) 0 else dp(2.5f * s)
        }

        val sp = 10f * s
        val pH = dp(6.5f * s)
        val pV = dp(4f * s)
        val minW = dp(25 * s)
        val minH = dp(25 * s)

        btnEngineTiny?.apply {
            val active = st.isEngineEnabled && !st.isEmergencyStopped
            text = if (active) "⚡" else "⏸"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setPadding(pH, pV, pH, pV)
            minimumWidth = minW
            minimumHeight = minH
            background = roundedBoxBg(
                fillColor = if (active) Color.rgb(14, 116, 144) else Color.rgb(71, 85, 105),
                strokeColor = Color.TRANSPARENT,
                strokePx = 0,
                radiusPx = dp(6 * s).toFloat()
            )
        }

        btnLockTiny?.apply {
            val lockOn = st.blockTouchDuringChallenge
            text = if (lockOn) "🔒" else "🔓"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setPadding(pH, pV, pH, pV)
            minimumWidth = minW
            minimumHeight = minH
            background = roundedBoxBg(
                fillColor = if (lockOn) Color.rgb(185, 28, 28) else Color.rgb(51, 65, 85),
                strokeColor = Color.TRANSPARENT,
                strokePx = 0,
                radiusPx = dp(6 * s).toFloat()
            )
        }

        btnPinTiny?.apply {
            text = if (st.isPositionEditMode) "✓" else "📍"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setPadding(pH, pV, pH, pV)
            minimumWidth = minW
            minimumHeight = minH
            setTextColor(if (st.isPositionEditMode) Color.BLACK else Color.WHITE)
            background = roundedBoxBg(
                fillColor = if (st.isPositionEditMode) Color.rgb(16, 185, 129) else Color.rgb(51, 65, 85),
                strokeColor = Color.TRANSPARENT,
                strokePx = 0,
                radiusPx = dp(6 * s).toFloat()
            )
        }

        btnSubMenuTiny?.apply {
            text = if (showSubMenu) "▾" else "☰"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setPadding(pH, pV, pH, pV)
            minimumWidth = minW
            minimumHeight = minH
            setTextColor(if (showSubMenu) Color.BLACK else Color.WHITE)
            background = roundedBoxBg(
                fillColor = if (showSubMenu) Color.rgb(34, 211, 238) else Color.rgb(51, 65, 85),
                strokeColor = Color.TRANSPARENT,
                strokePx = 0,
                radiusPx = dp(6 * s).toFloat()
            )
        }

        btnMinTiny?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setPadding(pH, pV, pH, pV)
            minimumWidth = minW
            minimumHeight = minH
            background = roundedBoxBg(
                fillColor = Color.rgb(51, 65, 85),
                strokeColor = Color.TRANSPARENT,
                strokePx = 0,
                radiusPx = dp(6 * s).toFloat()
            )
        }

        btnCloseTiny?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setPadding(pH, pV, pH, pV)
            minimumWidth = minW
            minimumHeight = minH
            background = roundedBoxBg(
                fillColor = Color.rgb(127, 29, 29),
                strokeColor = Color.TRANSPARENT,
                strokePx = 0,
                radiusPx = dp(6 * s).toFloat()
            )
        }

        refreshPrecisionBarVisuals(st)
    }

    // ==================== Utilities & Safe Bounds ====================

    private fun getSafeHudBoundsPx(): Rect {
        val margin = dp(6)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val b = metrics.bounds
            val insets = metrics.windowInsets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            val left = (b.left + insets.left + margin).coerceAtLeast(margin)
            val top = (b.top + insets.top + margin).coerceAtLeast(margin)
            val right = (b.right - insets.right - margin).coerceAtLeast(left + dp(80))
            val bottom = (b.bottom - insets.bottom - margin).coerceAtLeast(top + dp(60))
            Rect(left, top, right, bottom)
        } else {
            val (screenW, screenH) = getDisplaySizePx()
            val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val statusH = if (isPortrait) getSystemDimenPx("status_bar_height", dp(24)) else dp(6)
            val navH = if (isPortrait) getSystemDimenPx("navigation_bar_height", dp(24)) else dp(6)
            val left = margin
            val top = statusH + margin
            val right = (screenW - margin).coerceAtLeast(left + dp(80))
            val bottom = (screenH - navH - margin).coerceAtLeast(top + dp(60))
            Rect(left, top, right, bottom)
        }
    }

    private fun getSystemDimenPx(name: String, fallback: Int): Int {
        val resId = resources.getIdentifier(name, "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else fallback
    }

    private fun applyCutoutMode(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun makeHudButton(
        label: String,
        bgColor: Int,
        fgColor: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumWidth = dp(32)
            minimumHeight = dp(32)
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = roundedBoxBg(bgColor, Color.TRANSPARENT, 0, dp(7).toFloat())
            setOnClickListener { onClick() }
        }
    }

    private fun makeMicroPinBtn(
        label: String,
        bgColor: Int,
        fgColor: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumHeight = dp(28)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = roundedBoxBg(bgColor, Color.TRANSPARENT, 0, dp(6).toFloat())
            setOnClickListener { onClick() }
        }
    }

    private fun roundedBoxBg(
        fillColor: Int,
        strokeColor: Int,
        strokePx: Int,
        radiusPx: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fillColor)
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }
    }

    private fun getDisplaySizePx(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay?.getRealMetrics(dm)
            dm.widthPixels.coerceAtLeast(1) to dm.heightPixels.coerceAtLeast(1)
        }
    }

    private fun resolveOverlayBitmapAsyncAware(uriStr: String, reqSizePx: Int): Bitmap? {
        val clean = uriStr.trim()
        if (clean.isEmpty()) return null
        val key = "$clean::$reqSizePx"
        overlayBitmapCache.get(key)?.let { cached ->
            if (!cached.isRecycled) return cached
        }
        val lower = clean.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            if (inFlightRemoteBitmaps.add(key)) {
                serviceScope.launch {
                    val fetched = withContext(Dispatchers.IO) {
                        downloadRemoteBitmap(clean, reqSizePx)
                    }
                    inFlightRemoteBitmaps.remove(key)
                    if (fetched != null) {
                        overlayBitmapCache.put(key, fetched)
                        if (lastState.activeOverlays.isNotEmpty() && !isTemporarilyHiddenForAutomation && !isTemporarilyHiddenForScan) {
                            renderLiveStage(lastState)
                        }
                    }
                }
            }
            return null
        }
        val localBmp = decodeSafeScaledBitmap(clean, reqSizePx)
        if (localBmp != null) {
            overlayBitmapCache.put(key, localBmp)
        }
        return localBmp
    }

    private fun downloadRemoteBitmap(urlString: String, reqSizePx: Int): Bitmap? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 3500
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.isEmpty()) return null
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            var sample = 1
            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            val target = reqSizePx.coerceAtLeast(96)
            while (maxDim / sample > target * 2) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Throwable) {
            null
        }
    }

    private fun createCircularFramedBitmap(src: Bitmap, diameterPx: Int, ringColor: Int, ringWidthPx: Float): Bitmap {
        val size = diameterPx.coerceAtLeast(24)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cropSide = min(src.width, src.height).coerceAtLeast(1)
        val cropX = (src.width - cropSide) / 2
        val cropY = (src.height - cropSide) / 2
        val squareSrc = Bitmap.createBitmap(src, cropX, cropY, cropSide, cropSide)
        val scaled = if (squareSrc.width == size) squareSrc else Bitmap.createScaledBitmap(squareSrc, size, size, true)
        if (scaled !== squareSrc && !squareSrc.isRecycled) squareSrc.recycle()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = size / 2f
        val innerR = (radius - ringWidthPx).coerceAtLeast(2f)
        canvas.drawCircle(radius, radius, innerR, paint)

        if (ringWidthPx > 0f) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = ringWidthPx
                color = ringColor
            }
            canvas.drawCircle(radius, radius, (radius - ringWidthPx / 2f).coerceAtLeast(2f), strokePaint)
        }
        if (scaled !== src && !scaled.isRecycled) scaled.recycle()
        return out
    }

    private fun decodeSafeScaledBitmap(uriStr: String, reqSizePx: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriStr)
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            var sample = 1
            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            val target = reqSizePx.coerceAtLeast(96)
            while (maxDim / sample > target * 2) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureNotificationChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Floating MLBB Trigger HUD",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(isHiddenForLive: Boolean): Notification {
        val showFloatingPending = PendingIntent.getForegroundService(
            this,
            2,
            Intent(this, FloatingTriggerOverlayService::class.java).apply {
                action = ACTION_SHOW_FLOATING
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openAppPending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getForegroundService(
            this,
            1,
            Intent(this, FloatingTriggerOverlayService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleAction = if (isHiddenForLive) {
            NotificationCompat.Action(0, "Tampilkan Floating", showFloatingPending)
        } else {
            val hideIntent = PendingIntent.getForegroundService(
                this,
                3,
                Intent(this, FloatingTriggerOverlayService::class.java).apply {
                    action = ACTION_HIDE_FLOATING
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action(0, "Sembunyikan", hideIntent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pin_filled)
            .setContentTitle(
                if (isHiddenForLive) "⚔️ MLBB Auto-Tap Aktif (Tanpa Floating)"
                else "⚔️ MLBB Floating HUD Aktif"
            )
            .setContentText(
                if (isHiddenForLive) "Ketuk untuk memunculkan floating HUD & pin lagi"
                else "Geser bebas di layar aman & pakai D-Pad presisi untuk pin"
            )
            .setContentIntent(if (isHiddenForLive) showFloatingPending else openAppPending)
            .addAction(toggleAction)
            .addAction(0, "Tutup", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun safeRemoveView(view: View) {
        try {
            windowManager.removeView(view)
        } catch (_: Throwable) {}
    }

    private fun dp(value: Number): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }
}
