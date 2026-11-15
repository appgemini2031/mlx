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
        private const val EXTRA_SYNC_FROM_ENGINE = "extra_sync_from_engine"
        private const val ACTION_STOP_SERVICE = "com.tajasb.interaction.ACTION_STOP_FLOATING_HUD"
        private const val ACTION_SHOW_FLOATING = "com.tajasb.interaction.ACTION_SHOW_FLOATING_HUD"
        private const val ACTION_HIDE_FLOATING = "com.tajasb.interaction.ACTION_HIDE_FLOATING_HUD"
        private const val ACTION_RESET_POSITION = "com.tajasb.interaction.ACTION_RESET_FLOATING_POS"

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun start(
            context: Context,
            startInEditMode: Boolean = false,
            openSettingsPanel: Boolean = false,
            hideFloatingForLive: Boolean = false,
            syncFromEngine: Boolean = false
        ): Boolean {
            if (!canDrawOverlays(context)) return false
            val intent = Intent(context, FloatingTriggerOverlayService::class.java).apply {
                putExtra(EXTRA_START_EDIT_MODE, startInEditMode)
                putExtra(EXTRA_OPEN_PANEL, openSettingsPanel)
                putExtra(EXTRA_HIDDEN_LIVE, hideFloatingForLive)
                putExtra(EXTRA_SYNC_FROM_ENGINE, syncFromEngine)
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
    private var liveStageParams: WindowManager.LayoutParams? = null
    private var touchBlockerView: FrameLayout? = null
    private var touchBlockerParams: WindowManager.LayoutParams? = null
    private var touchBlockerStatusPill: TextView? = null
    private val touchBlockerHolePanels = mutableListOf<FrameLayout>()
    private val touchBlockerHoleParams = mutableListOf<WindowManager.LayoutParams>()
    private var currentBlockerMode: BlockerWindowMode = BlockerWindowMode.DETACHED
    private var currentAppliedHole: PixelRect? = null
    private var currentHolePanelRects: List<PixelRect> = emptyList()
    private var lastTouchBlockerPillText: String = ""
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
    private var activeTouchHolePx: PixelRect? = null
    private var touchHiddenHudForHole = false

    @Volatile
    private var isTemporarilyHiddenForScan = false

    @Volatile
    private var syncedTouchLockOverride: Boolean? = null

    private var scanHiddenHud = false
    private var scanHiddenStage = false
    private var scanHiddenBlockerPill = false
    private val scanHiddenMarkerKeys = mutableSetOf<String>()
    private val scanHiddenCoverKeys = mutableSetOf<String>()

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
        val root: FrameLayout,
        val crosshairView: TextView,
        val badgeLabel: TextView,
        val quickActionsRow: LinearLayout,
        val params: WindowManager.LayoutParams,
        var isActionsOpen: Boolean = false,
        var lastCrosshairLeft: Int = 0,
        var lastCrosshairTop: Int = 0
    )

    private data class CoverWindowEntry(
        val root: FrameLayout,
        val labelPill: TextView,
        val resizeHandle: TextView,
        val params: WindowManager.LayoutParams
    )

    private val markerWindows = linkedMapOf<String, MarkerWindowEntry>()
    private val coverWindows = linkedMapOf<String, CoverWindowEntry>()
    private var draggingRuleId: String? = null
    private var draggingCoverId: String? = null
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
            if (!hideForTouch) {
                setTouchHoleForAutomation(null, false)
            } else {
                isTemporarilyHiddenForAutomation = true
                setMarkerWindowsTemporaryHidden(true)
            }
        }
        MlbbGestureAccessibilityService.onPrepareScreenForTouchRegion = { holeRect, active ->
            setTouchHoleForAutomation(holeRect, active)
        }
        MlbbGestureAccessibilityService.onPrepareScreenForScan = { hideForScan ->
            setFloatingWindowsHiddenForScanRegion(null, hideForScan)
        }
        MlbbGestureAccessibilityService.onPrepareScreenForScanRegion = { region, hideForScan ->
            setFloatingWindowsHiddenForScanRegion(region, hideForScan)
        }
        MlbbGestureAccessibilityService.onSyncTouchLockOverlay = { locked ->
            val engineExecuting = engine.state.value.isChallengeExecuting
            syncedTouchLockOverride = when {
                locked -> true
                lastState.isChallengeExecuting || engineExecuting -> false
                else -> null
            }
            if (!locked) {
                clearAutomationTouchHoleAndRestoreUi(refreshBlocker = false)
            }
            updateTouchBlockerState(lastState, temporarilyHiddenForAutomation = isTemporarilyHiddenForScan)
        }

        engine.setFloatingOverlayRunning(true)
        if (!lastState.isFloatingHiddenForLive) {
            attachSingleFloatingHudWindowIfNeeded()
        }
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
                attachSingleFloatingHudWindowIfNeeded()
                refreshSingleHudVisuals()
                applyHudPositionFromConfig(lastState.floatingConfig, forceApply = true)
                clampHudIntoSafeBounds()
                updateNotification(lastState.isFloatingHiddenForLive)
                return START_STICKY
            }
            ACTION_HIDE_FLOATING -> {
                isSubMenuExpanded = false
                setHudFocusableForKeyboard(false)
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
                attachSingleFloatingHudWindowIfNeeded()
                refreshSingleHudVisuals()
                applyHudPositionFromConfig(lastState.floatingConfig, forceApply = true)
                updateNotification(lastState.isFloatingHiddenForLive)
                Toast.makeText(this, "↺ Posisi floating direset ke area aman", Toast.LENGTH_SHORT).show()
                return START_STICKY
            }
        }

        val syncFromEngine = intent?.getBooleanExtra(EXTRA_SYNC_FROM_ENGINE, false) == true
        val openEdit = intent?.getBooleanExtra(EXTRA_START_EDIT_MODE, false) == true
        val openPanel = intent?.getBooleanExtra(EXTRA_OPEN_PANEL, false) == true
        val hiddenLive = intent?.getBooleanExtra(EXTRA_HIDDEN_LIVE, false) == true

        if (syncFromEngine) {
            if (engine.state.value.isFloatingHiddenForLive) {
                isSubMenuExpanded = false
            }
        } else if (hiddenLive) {
            isSubMenuExpanded = false
            setHudFocusableForKeyboard(false)
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
        if (!lastState.isFloatingHiddenForLive) {
            attachSingleFloatingHudWindowIfNeeded()
        }
        refreshSingleHudVisuals()
        if (!lastState.isFloatingHiddenForLive) {
            applyHudPositionFromConfig(lastState.floatingConfig, forceApply = true)
            clampHudIntoSafeBounds()
        }
        updateNotification(lastState.isFloatingHiddenForLive)
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        draggingRuleId = null
        draggingCoverId = null
        isDraggingHud = false
        val relayoutAll = {
            if (!isTemporarilyHiddenForScan) {
                refreshSingleHudVisuals()
                if (!lastState.isFloatingHiddenForLive) {
                    applyHudPositionFromConfig(lastState.floatingConfig, forceApply = true)
                    clampHudIntoSafeBounds()
                }
                renderLiveStage(lastState)
                syncMarkerWindows(lastState)
                syncCoverWindows(lastState)
            }
        }
        relayoutAll()
        // Beberapa device memperbarui WindowMetrics / insets 1-2 frame setelah onConfigurationChanged
        singleHudView?.postDelayed({ relayoutAll() }, 120L)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (MlbbGestureAccessibilityService.onPrepareScreenForTouch != null) {
            MlbbGestureAccessibilityService.onPrepareScreenForTouch = null
        }
        if (MlbbGestureAccessibilityService.onPrepareScreenForTouchRegion != null) {
            MlbbGestureAccessibilityService.onPrepareScreenForTouchRegion = null
        }
        if (MlbbGestureAccessibilityService.onPrepareScreenForScan != null) {
            MlbbGestureAccessibilityService.onPrepareScreenForScan = null
        }
        if (MlbbGestureAccessibilityService.onPrepareScreenForScanRegion != null) {
            MlbbGestureAccessibilityService.onPrepareScreenForScanRegion = null
        }
        if (MlbbGestureAccessibilityService.onSyncTouchLockOverlay != null) {
            MlbbGestureAccessibilityService.onSyncTouchLockOverlay = null
        }
        syncedTouchLockOverride = null
        overlayBitmapCache.evictAll()
        serviceScope.cancel()
        removeAllMarkerWindows()
        removeAllCoverWindows()
        detachSingleFloatingHudWindow()
        detachAllHoleBlockerPanels()
        detachTouchBlockerWindow()
        detachLiveStageWindow()
        currentBlockerMode = BlockerWindowMode.DETACHED
        currentAppliedHole = null
        lastTouchBlockerPillText = ""
        engine.setFloatingOverlayRunning(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(isHiddenForLive: Boolean) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        mgr?.notify(NOTIF_ID, buildNotification(isHiddenForLive))
    }

    private fun observeEngineState() {
        serviceScope.launch {
            var initialized = false
            engine.state.collectLatest { st ->
                val prev = lastState
                val isFirst = !initialized
                initialized = true

                // Jika baru menekan ▶ Tes 1 konsep atau aksi sentuh berjalan, lepas fokus keyboard seketika agar tidak menghalangi touch game
                if ((st.isSingleTestRunning && !prev.isSingleTestRunning) ||
                    (st.isChallengeExecuting && !prev.isChallengeExecuting)
                ) {
                    if (st.isSingleTestRunning && isSubMenuExpanded) {
                        isSubMenuExpanded = false
                    }
                    setHudFocusableForKeyboard(false)
                }

                val hiddenChanged = isFirst || prev.isFloatingHiddenForLive != st.isFloatingHiddenForLive
                val cfgRatioChanged = isFirst ||
                    lastAppliedXRatio != st.floatingConfig.hudXRatio ||
                    lastAppliedYRatio != st.floatingConfig.hudYRatio
                val hudLayoutRelevantChanged = isFirst ||
                    prev.isFloatingHiddenForLive != st.isFloatingHiddenForLive ||
                    prev.floatingConfig != st.floatingConfig ||
                    prev.isPositionEditMode != st.isPositionEditMode
                val hudChanged = isFirst ||
                    hudLayoutRelevantChanged ||
                    prev.isCoverEditMode != st.isCoverEditMode ||
                    prev.isEngineEnabled != st.isEngineEnabled ||
                    prev.isEmergencyStopped != st.isEmergencyStopped ||
                    prev.blockTouchDuringChallenge != st.blockTouchDuringChallenge ||
                    prev.activePinRuleId != st.activePinRuleId ||
                    prev.activePinStepId != st.activePinStepId ||
                    prev.rules != st.rules ||
                    prev.isSingleTestRunning != st.isSingleTestRunning
                val scanAreasMatter = (st.isPositionEditMode && !st.isFloatingHiddenForLive) ||
                    (prev.isPositionEditMode && !prev.isFloatingHiddenForLive)
                val stageChanged = isFirst ||
                    prev.activeOverlays != st.activeOverlays ||
                    (st.isPositionEditMode && !st.isFloatingHiddenForLive && prev.lastSimEvent != st.lastSimEvent) ||
                    (prev.lastSimEvent != null && st.lastSimEvent == null) ||
                    prev.isPositionEditMode != st.isPositionEditMode ||
                    prev.isFloatingHiddenForLive != st.isFloatingHiddenForLive ||
                    (scanAreasMatter && prev.rules != st.rules)
                val markerChanged = isFirst ||
                    prev.isPositionEditMode != st.isPositionEditMode ||
                    prev.isFloatingHiddenForLive != st.isFloatingHiddenForLive ||
                    prev.rules != st.rules ||
                    prev.activePinRuleId != st.activePinRuleId ||
                    prev.activePinStepId != st.activePinStepId
                val coverChanged = isFirst ||
                    prev.gameCoverAreas != st.gameCoverAreas ||
                    prev.isCoverEditMode != st.isCoverEditMode ||
                    prev.activeCoverAreaId != st.activeCoverAreaId

                val overrideBeforeReset = syncedTouchLockOverride
                if (overrideBeforeReset != null &&
                    (st.isChallengeExecuting == overrideBeforeReset || !st.isChallengeExecuting)
                ) {
                    syncedTouchLockOverride = null
                }

                val blockerChanged = isFirst ||
                    prev.blockTouchDuringChallenge != st.blockTouchDuringChallenge ||
                    prev.isChallengeExecuting != st.isChallengeExecuting ||
                    prev.isPositionEditMode != st.isPositionEditMode ||
                    prev.isEmergencyStopped != st.isEmergencyStopped ||
                    prev.cooldownStatusHint != st.cooldownStatusHint ||
                    prev.touchConflictHint != st.touchConflictHint ||
                    syncedTouchLockOverride != null ||
                    (overrideBeforeReset != null &&
                        syncedTouchLockOverride == null &&
                        currentBlockerMode != BlockerWindowMode.DETACHED)

                lastState = st

                if (!isTemporarilyHiddenForScan) {
                    if (st.isFloatingHiddenForLive) {
                        if (singleHudView != null) {
                            detachSingleFloatingHudWindow()
                        }
                    } else {
                        val justReattachedHud = singleHudView == null
                        if (justReattachedHud) {
                            attachSingleFloatingHudWindowIfNeeded()
                        }
                        if (hudChanged || justReattachedHud) {
                            refreshSingleHudVisuals()
                        }
                        if (!isDraggingHud && (justReattachedHud || hudLayoutRelevantChanged || cfgRatioChanged)) {
                            if (cfgRatioChanged || justReattachedHud) {
                                applyHudPositionFromConfig(st.floatingConfig, forceApply = justReattachedHud)
                            } else {
                                clampHudIntoSafeBounds()
                            }
                        }
                    }
                    if (stageChanged) {
                        renderLiveStage(st)
                    }
                    if (markerChanged) {
                        syncMarkerWindows(st)
                    }
                    if (coverChanged) {
                        syncCoverWindows(st)
                    }
                }
                if (blockerChanged) {
                    updateTouchBlockerState(
                        st,
                        temporarilyHiddenForAutomation = isTemporarilyHiddenForScan
                    )
                }
                if (hiddenChanged) {
                    updateNotification(st.isFloatingHiddenForLive)
                }
            }
        }
    }

    // ==================== Layer 1: Non-Blocking Feedback & Uploaded Overlay Stage ====================

    private fun attachLiveStageWindowIfNeeded(bounds: PixelRect): FrameLayout? {
        val w = (bounds.right - bounds.left).coerceAtLeast(1)
        val h = (bounds.bottom - bounds.top).coerceAtLeast(1)
        val existing = liveStageView
        val existingParams = liveStageParams
        if (existing != null && existingParams != null) {
            if (existingParams.x != bounds.left ||
                existingParams.y != bounds.top ||
                existingParams.width != w ||
                existingParams.height != h
            ) {
                existingParams.x = bounds.left
                existingParams.y = bounds.top
                existingParams.width = w
                existingParams.height = h
                try {
                    windowManager.updateViewLayout(existing, existingParams)
                } catch (_: Throwable) {}
            }
            return existing
        }
        val container = FrameLayout(this)
        val params = WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
            applyCutoutMode(this)
        }
        return try {
            windowManager.addView(container, params)
            liveStageView = container
            liveStageParams = params
            container
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal pasang live stage overlay: ${t.message}")
            null
        }
    }

    private fun detachLiveStageWindow() {
        val stage = liveStageView ?: return
        stage.removeAllViews()
        safeRemoveViewImmediate(stage)
        liveStageView = null
        liveStageParams = null
        scanHiddenStage = false
    }

    private fun ensureTouchBlockerWindowAttached(notTouchable: Boolean): FrameLayout? {
        val existing = touchBlockerView
        val existingParams = touchBlockerParams
        if (existing != null && existingParams != null) {
            val currentlyNotTouchable = (existingParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
            if (currentlyNotTouchable != notTouchable) {
                existingParams.flags = if (notTouchable) {
                    existingParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    existingParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
                try { windowManager.updateViewLayout(existing, existingParams) } catch (_: Throwable) {}
            }
            if (existing.visibility != View.VISIBLE) {
                existing.visibility = View.VISIBLE
            }
            return existing
        }

        val blocker = FrameLayout(this).apply {
            visibility = View.VISIBLE
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
            text = "🔒 Layar Terkunci — Aksi ML Sedang Berjalan"
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
        lastTouchBlockerPillText = pill.text.toString()
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

        var baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (notTouchable) {
            baseFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            applyCutoutMode(this)
        }
        touchBlockerParams = params
        return try {
            windowManager.addView(blocker, params)
            touchBlockerView = blocker
            blocker
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal pasang touch blocker overlay: ${t.message}")
            touchBlockerParams = null
            touchBlockerStatusPill = null
            null
        }
    }

    private fun detachTouchBlockerWindow() {
        touchBlockerView?.let { safeRemoveViewImmediate(it) }
        touchBlockerView = null
        touchBlockerParams = null
        touchBlockerStatusPill = null
        lastTouchBlockerPillText = ""
        scanHiddenBlockerPill = false
    }

    private fun detachAllHoleBlockerPanels() {
        if (touchBlockerHolePanels.isEmpty()) {
            currentHolePanelRects = emptyList()
            return
        }
        touchBlockerHolePanels.forEach { safeRemoveViewImmediate(it) }
        touchBlockerHolePanels.clear()
        touchBlockerHoleParams.clear()
        currentHolePanelRects = emptyList()
    }

    private fun syncHoleBlockerPanels(rects: List<PixelRect>) {
        // Lepas kelebihan panel segera agar tidak menyisakan window transparan di WindowManager
        while (touchBlockerHolePanels.size > rects.size) {
            val lastIdx = touchBlockerHolePanels.lastIndex
            safeRemoveViewImmediate(touchBlockerHolePanels.removeAt(lastIdx))
            touchBlockerHoleParams.removeAt(lastIdx)
        }

        if (currentHolePanelRects == rects && touchBlockerHolePanels.size == rects.size) {
            return
        }

        for (i in rects.indices) {
            val r = rects[i]
            val rw = (r.right - r.left).coerceAtLeast(1)
            val rh = (r.bottom - r.top).coerceAtLeast(1)
            if (i < touchBlockerHolePanels.size) {
                val panel = touchBlockerHolePanels[i]
                val pParams = touchBlockerHoleParams[i]
                val posOrSizeChanged = pParams.x != r.left || pParams.y != r.top || pParams.width != rw || pParams.height != rh
                val flagsChanged = (pParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
                if (posOrSizeChanged || flagsChanged || panel.visibility != View.VISIBLE) {
                    pParams.x = r.left
                    pParams.y = r.top
                    pParams.width = rw
                    pParams.height = rh
                    pParams.flags = pParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    if (panel.visibility != View.VISIBLE) {
                        panel.visibility = View.VISIBLE
                    }
                    try { windowManager.updateViewLayout(panel, pParams) } catch (_: Throwable) {}
                }
            } else {
                val strip = FrameLayout(this).apply {
                    visibility = View.VISIBLE
                    isClickable = true
                    isFocusable = false
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnTouchListener { _, _ -> true }
                }
                val stripParams = WindowManager.LayoutParams(
                    rw,
                    rh,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = r.left
                    y = r.top
                    applyCutoutMode(this)
                }
                try {
                    windowManager.addView(strip, stripParams)
                    touchBlockerHolePanels += strip
                    touchBlockerHoleParams += stripParams
                } catch (t: Throwable) {
                    Log.e(TAG, "Gagal pasang panel hole touch blocker: ${t.message}")
                }
            }
        }
        currentHolePanelRects = rects
    }

    private fun isTouchLockActuallyActive(st: UniversalEngineUiState): Boolean {
        val isExecutingNow = syncedTouchLockOverride ?: st.isChallengeExecuting
        return !isTemporarilyHiddenForScan &&
            st.blockTouchDuringChallenge &&
            isExecutingNow &&
            !st.isPositionEditMode &&
            !st.isEmergencyStopped
    }

    private fun clearAutomationTouchHoleAndRestoreUi(refreshBlocker: Boolean) {
        val hadAutomationUiState = isTemporarilyHiddenForAutomation || activeTouchHolePx != null || touchHiddenHudForHole
        isTemporarilyHiddenForAutomation = false
        activeTouchHolePx = null
        if (touchHiddenHudForHole) {
            touchHiddenHudForHole = false
            val hud = singleHudView
            val hParams = singleHudParams
            if (hud != null && hParams != null && !isTemporarilyHiddenForScan && !scanHiddenHud && !lastState.isFloatingHiddenForLive) {
                hud.alpha = lastState.floatingConfig.hudOpacity
                if ((hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                    hParams.flags = hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    try { windowManager.updateViewLayout(hud, hParams) } catch (_: Throwable) {}
                }
            }
        }
        if (hadAutomationUiState && markerWindows.isNotEmpty()) {
            setMarkerWindowsTemporaryHidden(false, skipTouchBlockerRefresh = true)
        }
        if (refreshBlocker && (currentBlockerMode != BlockerWindowMode.DETACHED || isTouchLockActuallyActive(lastState))) {
            updateTouchBlockerState(lastState, temporarilyHiddenForAutomation = isTemporarilyHiddenForScan)
        }
    }

    private fun setTouchHoleForAutomation(holeRect: PixelRect?, active: Boolean): Boolean {
        if (active) {
            if (!isTouchLockActuallyActive(lastState)) {
                activeTouchHolePx = null
                if (currentBlockerMode != BlockerWindowMode.DETACHED) {
                    updateTouchBlockerState(lastState, temporarilyHiddenForAutomation = isTemporarilyHiddenForScan)
                }
                return false
            }
            isTemporarilyHiddenForAutomation = true
            activeTouchHolePx = holeRect
            if (markerWindows.isNotEmpty()) {
                setMarkerWindowsTemporaryHidden(true, skipTouchBlockerRefresh = true)
            }

            if (holeRect != null) {
                val hud = singleHudView
                val hParams = singleHudParams
                if (hud != null && hParams != null && hud.visibility == View.VISIBLE) {
                    val hw = hud.width.coerceAtLeast(hud.measuredWidth)
                    val hh = hud.height.coerceAtLeast(hud.measuredHeight)
                    val hudRect = PixelRect(hParams.x, hParams.y, hParams.x + hw, hParams.y + hh)
                    if (hw > 0 && hh > 0 && ScanRegionGeometry.rectsIntersect(hudRect, holeRect)) {
                        // Jangan ubah visibility menjadi INVISIBLE agar tidak memicu relayout/kedip WindowManager:
                        // cukup pasang FLAG_NOT_TOUCHABLE + redupkan alpha agar gesture Accessibility menembus bersih.
                        hud.alpha = 0.15f
                        if ((hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0) {
                            hParams.flags = hParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            try { windowManager.updateViewLayout(hud, hParams) } catch (_: Throwable) {}
                        }
                        touchHiddenHudForHole = true
                    }
                }
            }
            updateTouchBlockerState(lastState, temporarilyHiddenForAutomation = isTemporarilyHiddenForScan)
            return true
        } else {
            clearAutomationTouchHoleAndRestoreUi(
                refreshBlocker = currentBlockerMode != BlockerWindowMode.DETACHED || isTouchLockActuallyActive(lastState)
            )
            return false
        }
    }

    private fun updateTouchBlockerState(
        st: UniversalEngineUiState,
        temporarilyHiddenForAutomation: Boolean
    ) {
        // Saat scan OCR berlangsung (isTemporarilyHiddenForScan), blocker dilepas sementara agar OCR bersih.
        // Saat challenge menjalankan klik/tahan/geser (activeTouchHolePx != null), blocker TETAP MENGUNCI SENTUHAN USER
        // melalui panel di luar lubang gesture, sementara Accessibility gesture bebas menyentuh game MLBB.
        val isExecutingNow = syncedTouchLockOverride ?: st.isChallengeExecuting
        val shouldLock = !isTemporarilyHiddenForScan &&
            !temporarilyHiddenForAutomation &&
            st.blockTouchDuringChallenge &&
            isExecutingNow &&
            !st.isPositionEditMode &&
            !st.isEmergencyStopped

        val (screenW, screenH) = if (shouldLock) getDisplaySizePx() else (1080 to 2400)
        val hole = if (shouldLock) activeTouchHolePx else null
        val plan = TouchBlockerLifecyclePlanner.computePlan(
            shouldLock = shouldLock,
            screenW = screenW,
            screenH = screenH,
            activeHole = hole
        )

        if (plan.mode == BlockerWindowMode.DETACHED) {
            if (currentBlockerMode == BlockerWindowMode.DETACHED &&
                touchBlockerView == null &&
                touchBlockerHolePanels.isEmpty()
            ) {
                return
            }
            detachAllHoleBlockerPanels()
            detachTouchBlockerWindow()
            currentBlockerMode = BlockerWindowMode.DETACHED
            currentAppliedHole = null
            return
        }

        val desiredPillText = when {
            st.touchConflictHint.isNotBlank() -> st.touchConflictHint
            st.cooldownStatusHint.isNotBlank() -> "🔒 Layar Terkunci • ${st.cooldownStatusHint}"
            else -> "🔒 Layar Terkunci — Aksi ML Sedang Berjalan"
        }

        if (plan.mode == BlockerWindowMode.FULL_SCREEN) {
            detachAllHoleBlockerPanels()
            ensureTouchBlockerWindowAttached(notTouchable = false) ?: return
            touchBlockerStatusPill?.let { pill ->
                if (lastTouchBlockerPillText != desiredPillText) {
                    pill.text = desiredPillText
                    lastTouchBlockerPillText = desiredPillText
                }
                val targetPillVis = if (scanHiddenBlockerPill) View.INVISIBLE else View.VISIBLE
                if (pill.visibility != targetPillVis) {
                    pill.visibility = targetPillVis
                }
            }
            currentBlockerMode = BlockerWindowMode.FULL_SCREEN
            currentAppliedHole = null
        } else {
            // Mode lubang gesture: visual merah non-touchable + panel strip presisi mengunci area luar lubang
            ensureTouchBlockerWindowAttached(notTouchable = true) ?: return
            touchBlockerStatusPill?.let { pill ->
                if (lastTouchBlockerPillText != desiredPillText) {
                    pill.text = desiredPillText
                    lastTouchBlockerPillText = desiredPillText
                }
                if (pill.width <= 0 || pill.height <= 0) {
                    pill.measure(
                        View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
                    )
                }
                val pw = pill.width.takeIf { it > 0 } ?: pill.measuredWidth
                val ph = pill.height.takeIf { it > 0 } ?: pill.measuredHeight
                val hasLayout = pill.width > 0 && pill.height > 0
                val pLeft = if (hasLayout) pill.left else ((screenW - pw) / 2).coerceAtLeast(0)
                val pTop = if (hasLayout) pill.top else dp(24)
                val pRect = PixelRect(pLeft, pTop, pLeft + pw, pTop + ph)
                val overlapsPill = hole != null && pw > 0 && ph > 0 && ScanRegionGeometry.rectsIntersect(pRect, hole)
                val targetPillVis = if (scanHiddenBlockerPill || overlapsPill) View.INVISIBLE else View.VISIBLE
                if (pill.visibility != targetPillVis) {
                    pill.visibility = targetPillVis
                }
            }
            syncHoleBlockerPanels(plan.holePanels)
            currentBlockerMode = BlockerWindowMode.HOLE_PUNCH
            currentAppliedHole = hole
        }
    }

    private data class StageChildPlacement(
        val view: View,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    )

    private fun renderLiveStage(st: UniversalEngineUiState) {
        val hasScanAreas = st.isPositionEditMode && !st.isFloatingHiddenForLive &&
            st.rules.any { it.isPinVisible && it.enableVisualCooldownWait }
        val showSimEvent = st.isPositionEditMode && !st.isFloatingHiddenForLive && st.lastSimEvent != null
        val shouldAttach = LiveStageLifecyclePlanner.shouldAttachStageWindow(
            hasScanAreas = hasScanAreas,
            activeOverlayCount = st.activeOverlays.size,
            hasSimEvent = st.lastSimEvent != null,
            hiddenForScan = false,
            isPositionEditMode = st.isPositionEditMode,
            isFloatingHiddenForLive = st.isFloatingHiddenForLive
        )
        if (!shouldAttach) {
            detachLiveStageWindow()
            return
        }

        val (screenW, screenH) = getDisplaySizePx()
        val placements = mutableListOf<StageChildPlacement>()
        val childRects = mutableListOf<PixelRect>()

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
                placements += StageChildPlacement(scanFrame, left, top, boxW, boxH)
                childRects += PixelRect(left, top, left + boxW, top + boxH)
            }
        }

        for (ov in st.activeOverlays) {
            val scale = ov.scale.coerceIn(0.35f, 3.0f)
            val isProfileOverlay = ov.kind == ActionType.PROFILE_OVERLAY
            val isMemeOrCustomImage = (ov.kind == ActionType.CUSTOM_IMAGE || ov.kind == ActionType.MEME_OVERLAY) && ov.imageUri.isNotBlank()
            var primaryMediaHeightPx = 0
            var primaryMediaView: View? = null
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
                primaryMediaHeightPx = avatarPx
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
                    primaryMediaView = img
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
            val resolvedPrimaryMediaH = if (primaryMediaHeightPx > 0) {
                primaryMediaHeightPx
            } else {
                primaryMediaView?.measuredHeight ?: 0
            }
            val anchorTopLeft = OverlayAnchorGeometry.computeCardTopLeft(
                normX = ov.posX,
                normY = ov.posY,
                screenW = screenW,
                screenH = screenH,
                cardWidthPx = cw,
                cardHeightPx = ch,
                primaryMediaHeightPx = resolvedPrimaryMediaH
            )
            placements += StageChildPlacement(
                view = card,
                left = anchorTopLeft.left,
                top = anchorTopLeft.top,
                width = FrameLayout.LayoutParams.WRAP_CONTENT,
                height = FrameLayout.LayoutParams.WRAP_CONTENT
            )
            childRects += PixelRect(
                left = anchorTopLeft.left,
                top = anchorTopLeft.top,
                right = (anchorTopLeft.left + cw).coerceAtMost(screenW),
                bottom = (anchorTopLeft.top + ch).coerceAtMost(screenH)
            )
        }

        if (showSimEvent) {
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
                placements += StageChildPlacement(dot, left, top, dotSize, dotSize)
                childRects += PixelRect(left, top, (left + dotSize).coerceAtMost(screenW), (top + dotSize).coerceAtMost(screenH))
            }
        }

        val bounds = LiveStageBoundsPlanner.computeTightWindowRect(
            childRects = childRects,
            screenW = screenW,
            screenH = screenH,
            fullScreenForScanAreas = hasScanAreas
        )
        if (bounds == null) {
            detachLiveStageWindow()
            return
        }
        val stage = attachLiveStageWindowIfNeeded(bounds) ?: return
        stage.removeAllViews()
        for (item in placements) {
            stage.addView(
                item.view,
                FrameLayout.LayoutParams(item.width, item.height).apply {
                    leftMargin = (item.left - bounds.left).coerceAtLeast(0)
                    topMargin = (item.top - bounds.top).coerceAtLeast(0)
                }
            )
        }
    }

    // ==================== Layer 2: External Trigger Pins OUTSIDE Floating (Colored + Per-Challenge Eye) ====================

    private fun setMarkerWindowsTemporaryHidden(hide: Boolean, skipTouchBlockerRefresh: Boolean = false) {
        if (!skipTouchBlockerRefresh) {
            updateTouchBlockerState(lastState, temporarilyHiddenForAutomation = isTemporarilyHiddenForScan)
        }
        // liveStageView sudah memiliki FLAG_NOT_TOUCHABLE sehingga tidak pernah memblokir sentuhan;
        // hanya sembunyikan saat OCR visual scan aktif agar tidak berkedip saat tes/klik berjalan.
        val stageTargetVis = if (isTemporarilyHiddenForScan) View.INVISIBLE else View.VISIBLE
        if (liveStageView?.visibility != stageTargetVis) {
            liveStageView?.visibility = stageTargetVis
        }
        val shouldBeNotTouchable = hide || isTemporarilyHiddenForScan
        val targetVis = if (isTemporarilyHiddenForScan) View.INVISIBLE else View.VISIBLE
        val targetAlpha = if (hide && !isTemporarilyHiddenForScan) 0.28f else 1.0f
        for (entry in markerWindows.values) {
            if (entry.root.visibility != targetVis) {
                entry.root.visibility = targetVis
            }
            if (entry.root.alpha != targetAlpha) {
                entry.root.alpha = targetAlpha
            }
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

    private fun setFloatingWindowsHiddenForScanRegion(region: ScanRegionSpec?, hide: Boolean): Boolean {
        if (region == null) {
            isTemporarilyHiddenForScan = hide
            setAllFloatingWindowsHiddenForScan(hide)
            return hide
        }

        val (screenW, screenH) = getDisplaySizePx()
        val scanRect = ScanRegionGeometry.computeScanRectPx(
            centerNormX = region.centerNormX,
            centerNormY = region.centerNormY,
            widthRatio = region.widthRatio,
            heightRatio = region.heightRatio,
            screenW = screenW,
            screenH = screenH,
            minBoxPx = dp(24),
            paddingPx = dp(12)
        )

        if (hide) {
            scanHiddenHud = false
            scanHiddenStage = false
            scanHiddenBlockerPill = false
            scanHiddenMarkerKeys.clear()
            scanHiddenCoverKeys.clear()

            val hud = singleHudView
            val hParams = singleHudParams
            if (hud != null && hParams != null && hud.visibility == View.VISIBLE) {
                val hw = hud.width.coerceAtLeast(hud.measuredWidth)
                val hh = hud.height.coerceAtLeast(hud.measuredHeight)
                val hudRect = PixelRect(hParams.x, hParams.y, hParams.x + hw, hParams.y + hh)
                if (hw > 0 && hh > 0 && ScanRegionGeometry.rectsIntersect(hudRect, scanRect)) {
                    hud.visibility = View.INVISIBLE
                    if ((hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0) {
                        hParams.flags = hParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        try { windowManager.updateViewLayout(hud, hParams) } catch (_: Throwable) {}
                    }
                    scanHiddenHud = true
                }
            }

            val stage = liveStageView
            if (stage != null && stage.visibility == View.VISIBLE && stage.childCount > 0) {
                val stageOriginX = liveStageParams?.x ?: 0
                val stageOriginY = liveStageParams?.y ?: 0
                var stageOverlaps = false
                for (i in 0 until stage.childCount) {
                    val child = stage.getChildAt(i) ?: continue
                    if (child.visibility != View.VISIBLE) continue
                    val lp = child.layoutParams as? FrameLayout.LayoutParams
                    val cl = stageOriginX + (if (child.width > 0) child.left else (lp?.leftMargin ?: 0))
                    val ct = stageOriginY + (if (child.height > 0) child.top else (lp?.topMargin ?: 0))
                    val cw = child.width.coerceAtLeast(child.measuredWidth)
                    val ch = child.height.coerceAtLeast(child.measuredHeight)
                    if (cw > 0 && ch > 0 && ScanRegionGeometry.rectsIntersect(PixelRect(cl, ct, cl + cw, ct + ch), scanRect)) {
                        stageOverlaps = true
                        break
                    }
                }
                if (stageOverlaps) {
                    stage.visibility = View.INVISIBLE
                    scanHiddenStage = true
                }
            }

            for ((key, entry) in markerWindows) {
                if (entry.root.visibility != View.VISIBLE) continue
                val mw = entry.root.width.coerceAtLeast(entry.root.measuredWidth)
                val mh = entry.root.height.coerceAtLeast(entry.root.measuredHeight)
                val mRect = PixelRect(entry.params.x, entry.params.y, entry.params.x + mw, entry.params.y + mh)
                if (mw > 0 && mh > 0 && ScanRegionGeometry.rectsIntersect(mRect, scanRect)) {
                    entry.root.visibility = View.INVISIBLE
                    if ((entry.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0) {
                        entry.params.flags = entry.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        try { windowManager.updateViewLayout(entry.root, entry.params) } catch (_: Throwable) {}
                    }
                    scanHiddenMarkerKeys += key
                }
            }

            for ((key, cEntry) in coverWindows) {
                if (cEntry.root.visibility != View.VISIBLE) continue
                val cw = cEntry.params.width.coerceAtLeast(cEntry.root.width)
                val ch = cEntry.params.height.coerceAtLeast(cEntry.root.height)
                val cRect = PixelRect(cEntry.params.x, cEntry.params.y, cEntry.params.x + cw, cEntry.params.y + ch)
                if (cw > 0 && ch > 0 && ScanRegionGeometry.rectsIntersect(cRect, scanRect)) {
                    cEntry.root.visibility = View.INVISIBLE
                    scanHiddenCoverKeys += key
                }
            }

            val blocker = touchBlockerView
            val pill = touchBlockerStatusPill
            if (blocker != null && blocker.visibility == View.VISIBLE && pill != null && pill.visibility == View.VISIBLE) {
                val (screenW, screenH) = getDisplaySizePx()
                if (pill.width <= 0 || pill.height <= 0) {
                    pill.measure(
                        View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
                    )
                }
                val pw = pill.width.takeIf { it > 0 } ?: pill.measuredWidth
                val ph = pill.height.takeIf { it > 0 } ?: pill.measuredHeight
                val hasLayout = pill.width > 0 && pill.height > 0
                val pLeft = if (hasLayout) pill.left else ((screenW - pw) / 2).coerceAtLeast(0)
                val pTop = if (hasLayout) pill.top else dp(24)
                val pRect = PixelRect(pLeft, pTop, pLeft + pw, pTop + ph)
                if (pw > 0 && ph > 0 && ScanRegionGeometry.rectsIntersect(pRect, scanRect)) {
                    pill.visibility = View.INVISIBLE
                    scanHiddenBlockerPill = true
                }
            }

            isTemporarilyHiddenForScan = scanHiddenHud || scanHiddenStage || scanHiddenBlockerPill ||
                scanHiddenMarkerKeys.isNotEmpty() || scanHiddenCoverKeys.isNotEmpty()
            return isTemporarilyHiddenForScan
        } else {
            val wasHidden = isTemporarilyHiddenForScan || scanHiddenHud || scanHiddenStage || scanHiddenBlockerPill ||
                scanHiddenMarkerKeys.isNotEmpty() || scanHiddenCoverKeys.isNotEmpty()
            isTemporarilyHiddenForScan = false

            if (scanHiddenHud) {
                scanHiddenHud = false
                val hud = singleHudView
                val hParams = singleHudParams
                if (hud != null && hParams != null) {
                    if ((hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                        hParams.flags = hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        try { windowManager.updateViewLayout(hud, hParams) } catch (_: Throwable) {}
                    }
                    refreshSingleHudVisuals()
                }
            }

            if (scanHiddenStage) {
                scanHiddenStage = false
                liveStageView?.visibility = View.VISIBLE
            }

            if (scanHiddenBlockerPill) {
                scanHiddenBlockerPill = false
                touchBlockerStatusPill?.visibility = View.VISIBLE
            }

            if (scanHiddenMarkerKeys.isNotEmpty()) {
                val keys = scanHiddenMarkerKeys.toList()
                scanHiddenMarkerKeys.clear()
                for (key in keys) {
                    val entry = markerWindows[key] ?: continue
                    entry.root.visibility = View.VISIBLE
                    if (!isTemporarilyHiddenForAutomation) {
                        entry.root.alpha = 1.0f
                        if ((entry.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                            entry.params.flags = entry.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                            try { windowManager.updateViewLayout(entry.root, entry.params) } catch (_: Throwable) {}
                        }
                    }
                }
            }

            if (scanHiddenCoverKeys.isNotEmpty()) {
                val keys = scanHiddenCoverKeys.toList()
                scanHiddenCoverKeys.clear()
                for (key in keys) {
                    coverWindows[key]?.root?.visibility = View.VISIBLE
                }
            }
            return wasHidden
        }
    }

    private fun setAllFloatingWindowsHiddenForScan(hide: Boolean) {
        isTemporarilyHiddenForScan = hide
        setMarkerWindowsTemporaryHidden(hide || isTemporarilyHiddenForAutomation)
        for (cEntry in coverWindows.values) {
            cEntry.root.visibility = if (hide) View.INVISIBLE else View.VISIBLE
        }
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
        val mediaTypes = setOf(ActionType.PROFILE_OVERLAY, ActionType.MEME_OVERLAY, ActionType.CUSTOM_IMAGE)
        for (rule in st.rules) {
            if (!rule.isPinVisible) continue
            val touchSteps = rule.actionSequence.filter {
                it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
            }
            val visualSteps = rule.actionSequence.filter {
                it.enabled && it.actionType in mediaTypes
            }
            if (touchSteps.isEmpty()) {
                val primaryVisual = visualSteps.firstOrNull()
                list += PinDescriptor(
                    key = "${rule.id}::${primaryVisual?.id ?: "root"}",
                    rule = rule,
                    step = primaryVisual,
                    stepIndex = 0,
                    totalTouchSteps = 0,
                    normX = primaryVisual?.posX ?: rule.overlayPosX,
                    normY = primaryVisual?.posY ?: rule.overlayPosY
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
                val rootX = touchSteps.first().posX ?: rule.overlayPosX
                val rootY = touchSteps.first().posY ?: rule.overlayPosY
                for (vStep in visualSteps) {
                    val vx = vStep.posX ?: rule.overlayPosX
                    val vy = vStep.posY ?: rule.overlayPosY
                    val isExplicitSelected = st.activePinRuleId == rule.id && st.activePinStepId == vStep.id
                    val divergesFromRoot = abs(vx - rootX) > 0.025f || abs(vy - rootY) > 0.025f
                    if (isExplicitSelected || divergesFromRoot) {
                        list += PinDescriptor(
                            key = "${rule.id}::${vStep.id}",
                            rule = rule,
                            step = vStep,
                            stepIndex = 0,
                            totalTouchSteps = touchSteps.size,
                            normX = vx,
                            normY = vy
                        )
                    }
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

        for (desc in descriptors) {
            if (desc.key == draggingRuleId) continue

            val rule = desc.rule
            val step = desc.step
            val isSelected = rule.id == st.activePinRuleId &&
                (step == null || st.activePinStepId == null || step.id == st.activePinStepId)
            val ruleColor = rule.accentColorHex.toInt()
            val shortBadge = buildPinBadgeText(desc)
            val pinSymbol = when {
                desc.step?.actionType == ActionType.PROFILE_OVERLAY -> "👤"
                desc.step?.actionType == ActionType.MEME_OVERLAY || desc.step?.actionType == ActionType.CUSTOM_IMAGE -> "🖼"
                desc.totalTouchSteps > 1 -> "${desc.stepIndex}"
                else -> "⊕"
            }
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
                applyMarkerAnchoredGeometry(existing, desc.normX, desc.normY, screenW, screenH, updateWindow = true)
            }
        }
    }

    private fun applyMarkerAnchoredGeometry(
        entry: MarkerWindowEntry,
        normX: Float,
        normY: Float,
        screenW: Int,
        screenH: Int,
        updateWindow: Boolean
    ) {
        val ringSize = dp(38)
        val atMostW = View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST)
        val atMostH = View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        entry.badgeLabel.measure(atMostW, atMostH)
        entry.quickActionsRow.measure(atMostW, atMostH)

        val badgeW = entry.badgeLabel.measuredWidth.coerceAtLeast(dp(44))
        val badgeH = entry.badgeLabel.measuredHeight.coerceAtLeast(dp(16))
        val rowW = entry.quickActionsRow.measuredWidth.coerceAtLeast(0)
        val rowH = entry.quickActionsRow.measuredHeight.coerceAtLeast(0)

        val layout = PinAnchorGeometry.computeLayout(
            normX = normX,
            normY = normY,
            screenW = screenW,
            screenH = screenH,
            ringSize = ringSize,
            badgeW = badgeW,
            badgeH = badgeH,
            quickRowW = rowW,
            quickRowH = rowH,
            isActionsOpen = entry.isActionsOpen,
            gapBadgePx = dp(2),
            gapRowPx = dp(3)
        )

        entry.crosshairView.layoutParams = FrameLayout.LayoutParams(ringSize, ringSize).apply {
            leftMargin = layout.crosshairLeft
            topMargin = layout.crosshairTop
        }
        entry.badgeLabel.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = layout.badgeLeft
            topMargin = layout.badgeTop
        }
        entry.quickActionsRow.visibility = if (entry.isActionsOpen) View.VISIBLE else View.GONE
        entry.quickActionsRow.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = layout.quickRowLeft
            topMargin = layout.quickRowTop
        }

        entry.lastCrosshairLeft = layout.crosshairLeft
        entry.lastCrosshairTop = layout.crosshairTop
        entry.params.x = layout.winX
        entry.params.y = layout.winY
        entry.params.width = layout.winW
        entry.params.height = layout.winH
        if (updateWindow) {
            try {
                windowManager.updateViewLayout(entry.root, entry.params)
            } catch (_: Throwable) {}
        }
    }

    private fun buildPinBadgeText(desc: PinDescriptor): String {
        val xPct = (desc.normX * 100).roundToInt()
        val yPct = (desc.normY * 100).roundToInt()
        val cleanName = desc.rule.triggerName.trim().ifBlank { desc.rule.triggerSource.displayName }.take(10)
        val mediaTag = when (desc.step?.actionType) {
            ActionType.PROFILE_OVERLAY -> " 👤"
            ActionType.MEME_OVERLAY, ActionType.CUSTOM_IMAGE -> " 🖼"
            else -> ""
        }
        val stepTag = if (desc.stepIndex > 0 && desc.totalTouchSteps > 1) " #${desc.stepIndex}" else ""
        val swipeTag = if (desc.step?.actionType == ActionType.SIMULATED_SWIPE) " ↗" else ""
        val muteTag = if (desc.rule.enabled) "" else " [OFF]"
        return "● $cleanName$mediaTag$stepTag$swipeTag$muteTag ($xPct,$yPct)"
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
        val pinSymbol = when {
            desc.step?.actionType == ActionType.PROFILE_OVERLAY -> "👤"
            desc.step?.actionType == ActionType.MEME_OVERLAY || desc.step?.actionType == ActionType.CUSTOM_IMAGE -> "🖼"
            desc.stepIndex > 0 && desc.totalTouchSteps > 1 -> "${desc.stepIndex}"
            else -> "⊕"
        }

        val root = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
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

        root.addView(crosshair, FrameLayout.LayoutParams(ringSize, ringSize))
        root.addView(badgeLabel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(quickRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))

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
            applyCutoutMode(this)
        }

        val entry = MarkerWindowEntry(root, crosshair, badgeLabel, quickRow, params)
        applyMarkerAnchoredGeometry(entry, desc.normX, desc.normY, screenW, screenH, updateWindow = false)

        var startX = 0
        var startY = 0
        var downCrosshairLeft = 0
        var downCrosshairTop = 0
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
                    downCrosshairLeft = entry.lastCrosshairLeft
                    downCrosshairTop = entry.lastCrosshairTop
                    touchDownRawX = event.rawX
                    touchDownRawY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchDownRawX).roundToInt()
                    val dy = (event.rawY - touchDownRawY).roundToInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                    // Izinkan crosshair mencapai seluruh koordinat layar walau badge/quickRow lebih lebar
                    val minWinX = -downCrosshairLeft
                    val maxWinX = (curW - downCrosshairLeft - ringSize).coerceAtLeast(minWinX)
                    val minWinY = -downCrosshairTop
                    val maxWinY = (curH - downCrosshairTop - ringSize).coerceAtLeast(minWinY)
                    params.x = (startX + dx).coerceIn(minWinX, maxWinX)
                    params.y = (startY + dy).coerceIn(minWinY, maxWinY)
                    try {
                        windowManager.updateViewLayout(root, params)
                    } catch (_: Throwable) {}

                    val centerX = ((params.x + downCrosshairLeft + ringHalf) / curW.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                    val centerY = ((params.y + downCrosshairTop + ringHalf) / curH.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                    val latestRule = engine.state.value.rules.firstOrNull { it.id == rule.id } ?: rule
                    val shortName = latestRule.triggerName.trim().ifBlank { latestRule.triggerSource.displayName }.take(10)
                    val stepTag = if (desc.totalTouchSteps > 1) " #${desc.stepIndex}" else ""
                    badgeLabel.text = "● $shortName$stepTag (${(centerX * 100).roundToInt()},${(centerY * 100).roundToInt()})"
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    draggingRuleId = null
                    if (moved) {
                        val centerX = ((params.x + downCrosshairLeft + ringHalf) / curW.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                        val centerY = ((params.y + downCrosshairTop + ringHalf) / curH.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                        applyMarkerAnchoredGeometry(entry, centerX, centerY, curW, curH, updateWindow = true)
                        engine.updateStepPosition(rule.id, step?.id, centerX, centerY)
                    } else {
                        entry.isActionsOpen = !entry.isActionsOpen
                        val latestRule = engine.state.value.rules.firstOrNull { it.id == rule.id } ?: rule
                        val latestStep = step?.let { st -> latestRule.actionSequence.firstOrNull { it.id == st.id } }
                        val curNormX = latestStep?.posX ?: latestRule.overlayPosX
                        val curNormY = latestStep?.posY ?: latestRule.overlayPosY
                        applyMarkerAnchoredGeometry(entry, curNormX, curNormY, curW, curH, updateWindow = true)
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

    // ==================== Layer 2B: Area Penutup Layar Game (Custom Ukuran & Posisi) ====================

    private fun syncCoverWindows(st: UniversalEngineUiState) {
        val visibleAreas = if (st.isCoverEditMode) {
            st.gameCoverAreas
        } else {
            st.gameCoverAreas.filter { it.enabled }
        }
        val activeIds = visibleAreas.map { it.id }.toSet()
        val toRemove = coverWindows.keys.filter { it !in activeIds }
        for (id in toRemove) {
            coverWindows.remove(id)?.let { safeRemoveView(it.root) }
        }
        if (visibleAreas.isEmpty()) return

        val (screenW, screenH) = getDisplaySizePx()
        val editMode = st.isCoverEditMode

        for (area in visibleAreas) {
            if (area.id == draggingCoverId) continue
            val isSelected = editMode && area.id == st.activeCoverAreaId
            val rect = GameCoverGeometry.computeCoverRectPx(
                centerNormX = area.posX,
                centerNormY = area.posY,
                widthRatio = area.widthRatio,
                heightRatio = area.heightRatio,
                screenW = screenW,
                screenH = screenH,
                minSizePx = dp(28)
            )
            val cw = (rect.right - rect.left).coerceAtLeast(dp(28))
            val ch = (rect.bottom - rect.top).coerceAtLeast(dp(28))
            val existing = coverWindows[area.id]
            val fillAlpha = ((if (editMode && !area.enabled) 0.45f else area.opacity).coerceIn(0.20f, 1.0f) * 255).roundToInt()
            val baseColor = area.colorHex.toInt()
            val argbFill = Color.argb(fillAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            val strokeColor = when {
                isSelected -> Color.rgb(34, 211, 238)
                editMode -> Color.argb(200, 148, 163, 184)
                else -> Color.TRANSPARENT
            }

            if (existing == null) {
                createCoverWindow(area, rect, cw, ch, editMode, isSelected, argbFill, strokeColor)
            } else {
                existing.root.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(argbFill)
                    if (editMode) setStroke(dp(if (isSelected) 2f else 1.2f), strokeColor)
                }
                existing.labelPill.text = "${area.name} ${(area.widthRatio * 100).roundToInt()}×${(area.heightRatio * 100).roundToInt()}%"
                existing.labelPill.visibility = if (editMode) View.VISIBLE else View.GONE
                existing.resizeHandle.visibility = if (editMode) View.VISIBLE else View.GONE
                val wantNotTouchable = !editMode
                val isNotTouchable = (existing.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
                val posOrFlagChanged = existing.params.x != rect.left ||
                    existing.params.y != rect.top ||
                    existing.params.width != cw ||
                    existing.params.height != ch ||
                    isNotTouchable != wantNotTouchable
                existing.params.x = rect.left
                existing.params.y = rect.top
                existing.params.width = cw
                existing.params.height = ch
                existing.params.flags = if (wantNotTouchable) {
                    existing.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                } else {
                    existing.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
                if (posOrFlagChanged) {
                    try {
                        windowManager.updateViewLayout(existing.root, existing.params)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun createCoverWindow(
        area: GameCoverArea,
        rect: PixelRect,
        cw: Int,
        ch: Int,
        editMode: Boolean,
        isSelected: Boolean,
        argbFill: Int,
        strokeColor: Int
    ) {
        val root = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(argbFill)
                if (editMode) setStroke(dp(if (isSelected) 2f else 1.2f), strokeColor)
            }
        }

        val labelPill = TextView(this).apply {
            text = "${area.name} ${(area.widthRatio * 100).roundToInt()}×${(area.heightRatio * 100).roundToInt()}%"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            visibility = if (editMode) View.VISIBLE else View.GONE
            background = roundedBoxBg(Color.argb(185, 15, 23, 42), Color.argb(140, 34, 211, 238), dp(0.8f), dp(6).toFloat())
        }
        root.addView(
            labelPill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = dp(4)
                topMargin = dp(4)
            }
        )

        val resizeHandle = TextView(this).apply {
            text = "↘"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumWidth = dp(26)
            minimumHeight = dp(26)
            visibility = if (editMode) View.VISIBLE else View.GONE
            background = roundedBoxBg(Color.rgb(34, 211, 238), Color.WHITE, dp(1f), dp(6).toFloat())
        }
        root.addView(
            resizeHandle,
            FrameLayout.LayoutParams(dp(26), dp(26), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = dp(2)
                bottomMargin = dp(2)
            }
        )

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            if (!editMode) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0

        val params = WindowManager.LayoutParams(
            cw,
            ch,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
            applyCutoutMode(this)
        }

        // Move gesture on cover body
        var moveStartX = 0
        var moveStartY = 0
        var downRawX = 0f
        var downRawY = 0f
        root.setOnTouchListener { _, ev ->
            if (!lastState.isCoverEditMode) return@setOnTouchListener false
            val (sw, sh) = getDisplaySizePx()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    draggingCoverId = area.id
                    engine.selectActiveCoverArea(area.id)
                    moveStartX = params.x
                    moveStartY = params.y
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downRawX).roundToInt()
                    val dy = (ev.rawY - downRawY).roundToInt()
                    params.x = (moveStartX + dx).coerceIn(0, (sw - params.width).coerceAtLeast(0))
                    params.y = (moveStartY + dy).coerceIn(0, (sh - params.height).coerceAtLeast(0))
                    try { windowManager.updateViewLayout(root, params) } catch (_: Throwable) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    draggingCoverId = null
                    val latest = engine.state.value.gameCoverAreas.firstOrNull { it.id == area.id } ?: area
                    val cx = ((params.x + params.width / 2f) / sw.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                    val cy = ((params.y + params.height / 2f) / sh.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                    engine.updateGameCoverArea(latest.copy(posX = cx, posY = cy))
                    true
                }
                else -> false
            }
        }

        // Resize gesture on bottom-right handle
        var resizeStartW = cw
        var resizeStartH = ch
        var rDownRawX = 0f
        var rDownRawY = 0f
        resizeHandle.setOnTouchListener { v, ev ->
            if (!lastState.isCoverEditMode) return@setOnTouchListener false
            val (sw, sh) = getDisplaySizePx()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    draggingCoverId = area.id
                    engine.selectActiveCoverArea(area.id)
                    resizeStartW = params.width
                    resizeStartH = params.height
                    rDownRawX = ev.rawX
                    rDownRawY = ev.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - rDownRawX).roundToInt()
                    val dy = (ev.rawY - rDownRawY).roundToInt()
                    val maxW = (sw - params.x).coerceAtLeast(dp(36))
                    val maxH = (sh - params.y).coerceAtLeast(dp(36))
                    params.width = (resizeStartW + dx).coerceIn(dp(36), maxW)
                    params.height = (resizeStartH + dy).coerceIn(dp(36), maxH)
                    val wPct = ((params.width / sw.coerceAtLeast(1).toFloat()) * 100).roundToInt()
                    val hPct = ((params.height / sh.coerceAtLeast(1).toFloat()) * 100).roundToInt()
                    labelPill.text = "${area.name} ${wPct}×${hPct}%"
                    try { windowManager.updateViewLayout(root, params) } catch (_: Throwable) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    draggingCoverId = null
                    val latest = engine.state.value.gameCoverAreas.firstOrNull { it.id == area.id } ?: area
                    val newWRatio = (params.width / sw.coerceAtLeast(1).toFloat()).coerceIn(0.03f, 0.96f)
                    val newHRatio = (params.height / sh.coerceAtLeast(1).toFloat()).coerceIn(0.03f, 0.96f)
                    val newCx = ((params.x + params.width / 2f) / sw.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                    val newCy = ((params.y + params.height / 2f) / sh.coerceAtLeast(1).toFloat()).coerceIn(0.01f, 0.99f)
                    engine.updateGameCoverArea(
                        latest.copy(
                            posX = newCx,
                            posY = newCy,
                            widthRatio = newWRatio,
                            heightRatio = newHRatio
                        )
                    )
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(root, params)
            coverWindows[area.id] = CoverWindowEntry(root, labelPill, resizeHandle, params)
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal tambah cover window: ${t.message}")
        }
    }

    private fun removeAllCoverWindows() {
        for (entry in coverWindows.values) {
            safeRemoveView(entry.root)
        }
        coverWindows.clear()
        draggingCoverId = null
    }

    // ==================== Layer 3: ONE Safe-Bounded Draggable Floating + Precision D-Pad + Dropdown ====================

    private fun attachSingleFloatingHudWindowIfNeeded() {
        if (singleHudView != null) return
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

        root.addView(headerDragContainer)
        root.addView(subMenuHolder)

        try {
            windowManager.addView(root, params)
            singleHudView = root
            singleHudParams = params
            refreshSingleHudVisuals()
            applyHudPositionFromConfig(cfg, forceApply = true)
        } catch (t: Throwable) {
            owner.onDetach()
            subMenuComposeOwner = null
            subMenuContainer = null
            Log.e(TAG, "Gagal pasang floating HUD: ${t.message}")
        }
    }

    private fun ensureSubMenuComposeAttached(cfg: FloatingWindowConfig) {
        val holder = subMenuContainer ?: return
        val owner = subMenuComposeOwner ?: return
        val targetDropW = computeSubMenuWidthPx(cfg)
        val existing = subMenuComposeView
        if (existing != null) {
            val lp = existing.layoutParams
            if (lp != null && abs(lp.width - targetDropW) >= dp(4)) {
                lp.width = targetDropW
                existing.layoutParams = lp
                existing.requestLayout()
            }
            return
        }
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
        holder.addView(
            composeView,
            FrameLayout.LayoutParams(targetDropW, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun detachSubMenuCompose() {
        val cv = subMenuComposeView ?: return
        try { cv.disposeComposition() } catch (_: Throwable) {}
        subMenuContainer?.removeView(cv)
        subMenuComposeView = null
    }

    private fun detachSingleFloatingHudWindow() {
        setHudFocusableForKeyboard(false)
        detachSubMenuCompose()
        subMenuComposeOwner?.onDetach()
        subMenuComposeOwner = null
        singleHudView?.let { safeRemoveViewImmediate(it) }
        singleHudView = null
        singleHudParams = null
        subMenuContainer = null
        dpadContainer = null
        dpadColorDot = null
        dpadTargetLabel = null
        dpadCoordLabel = null
        dpadEyeBtn = null
        dpadStepBtn = null
        dpadArrowRow = null
        dpadHiddenBannerRow = null
        hudTopRow = null
        hudTitleBtn = null
        btnEngineTiny = null
        btnLockTiny = null
        btnPinTiny = null
        btnSubMenuTiny = null
        btnMinTiny = null
        btnCloseTiny = null
        touchHiddenHudForHole = false
        scanHiddenHud = false
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
        val explicit = st.activePinStepId?.let { id ->
            rule.actionSequence.firstOrNull { it.enabled && it.id == id }
        }
        if (explicit != null) return explicit
        val touchSteps = rule.actionSequence.filter {
            it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
        }
        return touchSteps.firstOrNull() ?: rule.actionSequence.firstOrNull {
            it.enabled && (it.actionType == ActionType.PROFILE_OVERLAY || it.actionType == ActionType.MEME_OVERLAY || it.actionType == ActionType.CUSTOM_IMAGE)
        }
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
        val btn = TextView(this).apply {
            text = symbol
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumWidth = dp(34)
            minimumHeight = dp(32)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = roundedBoxBg(Color.rgb(30, 41, 59), Color.argb(140, 34, 211, 238), dp(1), dp(6).toFloat())
        }
        val stepRunner = object : Runnable {
            override fun run() {
                if (!holding || !btn.isAttachedToWindow || !lastState.isPositionEditMode) {
                    holding = false
                    return
                }
                val step = if (isFinePrecisionStep) 0.0025f else 0.015f
                onStep(step)
                repeatHandler.postDelayed(this, 65L)
            }
        }
        btn.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                holding = false
                repeatHandler.removeCallbacks(stepRunner)
                v.isPressed = false
            }
        })
        btn.setOnTouchListener { v, event ->
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
        return btn
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
        if (focusable) {
            val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            hud.post {
                hud.requestFocus()
                val target = hud.findFocus() ?: hud
                imm?.showSoftInput(target, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        } else if (HudFocusGuardPlanner.shouldClearFocusAndIme(
                currentlyFocusable = currentlyFocusable,
                hasViewFocus = hud.hasFocus(),
                requestedFocusable = false
            )
        ) {
            hud.clearFocus()
            val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
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
        if (st.isFloatingHiddenForLive) {
            if (singleHudView != null) {
                detachSingleFloatingHudWindow()
            }
            return
        }
        val cfg = st.floatingConfig
        val hud = singleHudView ?: return
        val hParams = singleHudParams
        if (scanHiddenHud || isTemporarilyHiddenForScan) {
            if (hud.visibility != View.INVISIBLE) {
                hud.visibility = View.INVISIBLE
            }
            return
        }
        if (hParams != null && !touchHiddenHudForHole &&
            (hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        ) {
            hParams.flags = hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try { windowManager.updateViewLayout(hud, hParams) } catch (_: Throwable) {}
        }
        if (hud.visibility != View.VISIBLE) {
            hud.visibility = View.VISIBLE
        }
        if (touchHiddenHudForHole) {
            hud.alpha = 0.15f
            return
        }

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
        if (showSubMenu) {
            ensureSubMenuComposeAttached(cfg)
            subMenuContainer?.visibility = View.VISIBLE
        } else {
            subMenuContainer?.visibility = View.GONE
            detachSubMenuCompose()
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

    private fun safeRemoveViewImmediate(view: View) {
        try {
            windowManager.removeViewImmediate(view)
        } catch (_: Throwable) {
            safeRemoveView(view)
        }
    }

    private fun dp(value: Number): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }
}
