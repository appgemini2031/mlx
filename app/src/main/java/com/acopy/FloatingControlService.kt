package com.acopy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.acopy.data.BackupRestoreManager
import com.acopy.data.NotesStorageUtil
import com.acopy.sync.DeviceSyncManager
import com.acopy.sync.QrCodeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

class FloatingControlService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingBubbleView: View? = null
    private var expandedPanelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelBackgroundCard: LinearLayout? = null
    private var alphaToggleButton: TextView? = null
    private var bubblePinnedScrollView: View? = null

    private var isPanelExpanded = true
    private var isStoppingOrDestroyed = false
    private var currentAlphaPercent = 100
    private var isBubbleVertical = false

    private var selectedTab = 1 // 0: Pintasan, 1: Catatan (Default Buka Floating)
    private var showingTrash = false

    private var installedAppsList: List<AppItem> = emptyList()
    private var pintasanSearchQuery: String = ""
    private var pintasanSearchInput: EditText? = null

    // Notes State (Menu: Catatan)
    private val notesList = mutableListOf<NoteItem>()
    private var editingNoteId: String? = null
    private var pendingPasteText: String = ""
    private var notesSearchQuery: String = ""
    private var notesSearchInput: EditText? = null

    private var tabPintasanButton: TextView? = null
    private var tabCatatanButton: TextView? = null
    private var tabPintasanView: View? = null
    private var tabCatatanView: View? = null
    private var notesListContainer: LinearLayout? = null
    private var noteEditorOverlayView: View? = null
    private var noteEditorHeaderTitle: TextView? = null
    private var noteTitleEditText: EditText? = null
    private var noteContentEditText: EditText? = null
    private var columnPickerOverlayView: View? = null
    private var columnPickerListContainer: LinearLayout? = null

    // Single Note QR Overlay (Transfer ke HP lain via QR)
    private var singleNoteQrOverlayView: View? = null
    private var singleNoteQrImageView: ImageView? = null
    private var singleNoteQrTitleView: TextView? = null
    private var singleNoteQrDescView: TextView? = null
    private var currentQrNote: NoteItem? = null

    // 3-Device Transfer Hub Overlay (Wi-Fi LAN / Barcode QR / Bagikan Backup)
    private var transferHubOverlayView: View? = null
    private var transferHubTab: Int = 0 // 0: LAN, 1: QR, 2: File
    private var transferServerStatusTv: TextView? = null
    private var transferServerToggleBtn: TextView? = null
    private var transferLanIpInput: EditText? = null
    private var transferQrImageView: ImageView? = null
    private var transferQrDescView: TextView? = null
    private var tabLanBtn: TextView? = null
    private var tabQrBtn: TextView? = null
    private var tabFileBtn: TextView? = null
    private var tabLanView: View? = null
    private var tabQrView: View? = null
    private var tabFileView: View? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val prefs by lazy {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    data class AppItem(
        val name: String,
        val packageName: String,
        val icon: Drawable?,
        var launchCount: Int = 0,
        var lastUsed: Long = 0L
    )

    data class NoteItem(
        val id: String,
        val title: String,
        val content: String,
        val isDeleted: Boolean = false,
        val deletedAt: Long = 0L
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isStoppingOrDestroyed = false
        isRunning = true
        _isRunningState.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val savedAlpha = prefs.getInt(KEY_ALPHA_PERCENT, 100)
        currentAlphaPercent = if (savedAlpha in ALPHA_STEPS) savedAlpha else 100
        isBubbleVertical = prefs.getBoolean(KEY_BUBBLE_IS_VERTICAL, false)

        loadNotesFromPrefs()
        loadPinnedAppsFromPrefs()

        loadInstalledAppsAsync()

        createNotificationChannel()
        startForegroundCompat()

        buildBubbleView()
        buildExpandedPanelView()
        applyOpacityToOverlays()
        showExpandedPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                closeFloatingService(disableInPref = true)
                return START_NOT_STICKY
            }
            ACTION_EXPAND_PANEL -> {
                if (!isStoppingOrDestroyed) showExpandedPanel()
            }
            ACTION_MINIMIZE_BUBBLE -> {
                if (!isStoppingOrDestroyed) showBubbleOnly()
            }
            ACTION_SYNC_NOTES -> {
                loadNotesFromPrefs()
                loadPinnedAppsFromPrefs()
                val savedAlpha = prefs.getInt(KEY_ALPHA_PERCENT, 100)
                currentAlphaPercent = if (savedAlpha in ALPHA_STEPS) savedAlpha else 100
                isBubbleVertical = prefs.getBoolean(KEY_BUBBLE_IS_VERTICAL, false)
                applyOpacityToOverlays()
                refreshNotesUI()
                refreshPintasanGridUI()
                refreshBubbleContent()
            }
        }
        return if (isStoppingOrDestroyed) START_NOT_STICKY else START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        clampOverlaysToScreen()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        closeFloatingService(disableInPref = false)
    }

    override fun onDestroy() {
        isStoppingOrDestroyed = true
        isRunning = false
        _isRunningState.value = false
        removeOverlaySafely(floatingBubbleView)
        removeOverlaySafely(expandedPanelView)
        floatingBubbleView = null
        expandedPanelView = null
        bubblePinnedScrollView = null
        panelBackgroundCard = null
        tabPintasanButton = null
        tabCatatanButton = null
        tabPintasanView = null
        tabCatatanView = null
        notesListContainer = null
        noteEditorOverlayView = null
        noteEditorHeaderTitle = null
        noteTitleEditText = null
        noteContentEditText = null
        columnPickerOverlayView = null
        columnPickerListContainer = null
        pintasanSearchInput = null
        notesSearchInput = null
        try {
            DeviceSyncManager.stopServer()
        } catch (_: Throwable) {}
        singleNoteQrOverlayView = null
        singleNoteQrImageView = null
        singleNoteQrTitleView = null
        singleNoteQrDescView = null
        currentQrNote = null
        transferHubOverlayView = null
        transferServerStatusTv = null
        transferServerToggleBtn = null
        transferLanIpInput = null
        transferQrImageView = null
        transferQrDescView = null
        tabLanBtn = null
        tabQrBtn = null
        tabFileBtn = null
        tabLanView = null
        tabQrView = null
        tabFileView = null
        stopForegroundCompat()
        serviceScope.cancel()
        super.onDestroy()
    }

    private val pinnedPackageNames = mutableListOf<String>()

    private fun loadPinnedAppsFromPrefs() {
        pinnedPackageNames.clear()
        val raw = prefs.getString(KEY_PINNED_APPS_JSON, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val pkg = arr.optString(i)
                if (pkg.isNotBlank() && !pinnedPackageNames.contains(pkg)) {
                    pinnedPackageNames.add(pkg)
                }
            }
        } catch (_: Exception) {}
    }

    private fun savePinnedAppsToPrefs() {
        try {
            val arr = JSONArray()
            pinnedPackageNames.forEach { arr.put(it) }
            prefs.edit().putString(KEY_PINNED_APPS_JSON, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun togglePinApp(packageName: String) {
        if (packageName.isBlank()) return
        if (pinnedPackageNames.contains(packageName)) {
            pinnedPackageNames.remove(packageName)
            Toast.makeText(applicationContext, "Lepas Pin aplikasi", Toast.LENGTH_SHORT).show()
        } else {
            pinnedPackageNames.add(0, packageName)
            Toast.makeText(applicationContext, "Aplikasi disematkan ke atas! 📌", Toast.LENGTH_SHORT).show()
        }
        savePinnedAppsToPrefs()
        refreshPintasanGridUI()
        refreshBubbleContent()
    }

    private fun movePinnedAppUp(packageName: String) {
        val idx = pinnedPackageNames.indexOf(packageName)
        if (idx > 0) {
            val temp = pinnedPackageNames[idx]
            pinnedPackageNames[idx] = pinnedPackageNames[idx - 1]
            pinnedPackageNames[idx - 1] = temp
            savePinnedAppsToPrefs()
            refreshPintasanGridUI()
            refreshBubbleContent()
        }
    }

    private fun movePinnedAppDown(packageName: String) {
        val idx = pinnedPackageNames.indexOf(packageName)
        if (idx >= 0 && idx < pinnedPackageNames.size - 1) {
            val temp = pinnedPackageNames[idx]
            pinnedPackageNames[idx] = pinnedPackageNames[idx + 1]
            pinnedPackageNames[idx + 1] = temp
            savePinnedAppsToPrefs()
            refreshPintasanGridUI()
            refreshBubbleContent()
        }
    }

    private fun loadUsageMap(): MutableMap<String, Pair<Int, Long>> {
        val map = mutableMapOf<String, Pair<Int, Long>>()
        val raw = prefs.getString(KEY_APP_USAGE_DATA, null) ?: return map
        try {
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val obj = json.optJSONObject(pkg)
                if (obj != null) {
                    val count = obj.optInt("count", 0)
                    val last = obj.optLong("last", 0L)
                    map[pkg] = Pair(count, last)
                }
            }
        } catch (_: Exception) {}
        return map
    }

    private fun saveUsageMap(map: Map<String, Pair<Int, Long>>) {
        try {
            val json = JSONObject()
            for ((pkg, stats) in map) {
                val obj = JSONObject().apply {
                    put("count", stats.first)
                    put("last", stats.second)
                }
                json.put(pkg, obj)
            }
            prefs.edit().putString(KEY_APP_USAGE_DATA, json.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun recordAppLaunch(targetPkg: String) {
        if (targetPkg.isBlank()) return
        val map = loadUsageMap()
        val current = map[targetPkg]
        val newCount = (current?.first ?: 0) + 1
        val newTime = System.currentTimeMillis()
        map[targetPkg] = Pair(newCount, newTime)
        saveUsageMap(map)

        installedAppsList.forEach { app ->
            if (app.packageName == targetPkg) {
                app.launchCount = newCount
                app.lastUsed = newTime
            }
        }
        sortAppsList()

        mainHandler.post {
            if (!isStoppingOrDestroyed) {
                refreshPintasanGridUI()
            }
        }
    }

    private fun resetUsageStats() {
        prefs.edit().remove(KEY_APP_USAGE_DATA).apply()
        installedAppsList.forEach { app ->
            app.launchCount = 0
            app.lastUsed = 0L
        }
        sortAppsList()
        refreshPintasanGridUI()
        Toast.makeText(applicationContext, "Riwayat frekuensi aplikasi di-reset", Toast.LENGTH_SHORT).show()
    }

    private fun sortAppsList() {
        installedAppsList = installedAppsList.sortedWith(
            compareByDescending<AppItem> { it.launchCount }
                .thenByDescending { it.lastUsed }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun loadInstalledAppsAsync() {
        Thread {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveList: List<ResolveInfo> = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    pm.queryIntentActivities(mainIntent, 0)
                }
            } catch (_: Exception) {
                emptyList()
            }

            val usageMap = loadUsageMap()

            val items = resolveList.mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null
                val label = try {
                    info.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg
                }
                val icon = try {
                    info.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }
                val stats = usageMap[pkg]
                val count = stats?.first ?: 0
                val last = stats?.second ?: 0L
                AppItem(label, pkg, icon, launchCount = count, lastUsed = last)
            }

            // Sering dibuka otomatis berada di paling atas
            installedAppsList = items.sortedWith(
                compareByDescending<AppItem> { it.launchCount }
                    .thenByDescending { it.lastUsed }
                    .thenBy { it.name.lowercase() }
            )

            mainHandler.post {
                if (!isStoppingOrDestroyed) {
                    refreshPintasanGridUI()
                    refreshBubbleContent()
                }
            }
        }.start()
    }

    private fun startForegroundCompat() {
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Throwable) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Throwable) {}
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.floating_service_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingControlService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle(getString(R.string.floating_service_started))
            .setContentText("Aplikasi yang sering Anda buka otomatis di atas")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Tutup", stopIntent)
            .build()
    }

    private fun getOverlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun cycleOpacityPercent() {
        val currentIdx = ALPHA_STEPS.indexOf(currentAlphaPercent)
        val nextIdx = if (currentIdx < 0) 0 else (currentIdx + 1) % ALPHA_STEPS.size
        currentAlphaPercent = ALPHA_STEPS[nextIdx]
        prefs.edit().putInt(KEY_ALPHA_PERCENT, currentAlphaPercent).apply()
        applyOpacityToOverlays()
    }

    private fun applyOpacityToOverlays() {
        val alphaValue = (currentAlphaPercent / 100f).coerceIn(0.5f, 1.0f)
        floatingBubbleView?.alpha = alphaValue
        expandedPanelView?.alpha = alphaValue
        alphaToggleButton?.text = "${currentAlphaPercent}%"
    }

    private fun buildBubbleView() {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val savedX = prefs.getInt(KEY_BUBBLE_X, dp(16)).coerceIn(0, (screenW - dp(44)).coerceAtLeast(0))
        val savedY = prefs.getInt(KEY_BUBBLE_Y, dp(120)).coerceIn(0, (screenH - dp(44)).coerceAtLeast(0))

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }
        bubbleParams = params

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.coerceAtLeast(dp(10))
        val container = object : LinearLayout(this) {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private var touchStartedInPinnedScroll = false

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = ev.rawX
                        initialTouchY = ev.rawY
                        isDragging = false
                        touchStartedInPinnedScroll = isPointInsideView(bubblePinnedScrollView, ev.rawX, ev.rawY)
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (ev.rawX - initialTouchX).toInt()
                        val dy = (ev.rawY - initialTouchY).toInt()
                        if (touchStartedInPinnedScroll) {
                            val scroller = bubblePinnedScrollView
                            if (!isBubbleVertical && scroller != null && abs(dx) >= abs(dy) && abs(dx) > touchSlop) {
                                if (scroller.canScrollHorizontally(-dx)) return false
                            } else if (isBubbleVertical && scroller != null && abs(dy) >= abs(dx) && abs(dy) > touchSlop) {
                                if (scroller.canScrollVertically(-dy)) return false
                            }
                        }
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            isDragging = true
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        touchStartedInPinnedScroll = false
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (isDragging || abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            isDragging = true
                            val curW = width.takeIf { it > 0 } ?: measuredWidth.coerceAtLeast(dp(40))
                            val curH = height.takeIf { it > 0 } ?: measuredHeight.coerceAtLeast(dp(40))
                            val maxX = (resources.displayMetrics.widthPixels - curW).coerceAtLeast(0)
                            val maxY = (resources.displayMetrics.heightPixels - curH).coerceAtLeast(0)
                            params.x = (initialX + dx).coerceIn(0, maxX)
                            params.y = (initialY + dy).coerceIn(0, maxY)
                            if (windowToken != null) {
                                try {
                                    windowManager.updateViewLayout(this, params)
                                } catch (_: Throwable) {}
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            prefs.edit()
                                .putInt(KEY_BUBBLE_X, params.x)
                                .putInt(KEY_BUBBLE_Y, params.y)
                                .apply()
                        }
                        isDragging = false
                        touchStartedInPinnedScroll = false
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#EE0B1120"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setPadding(dp(5), dp(5), dp(5), dp(5))
            elevation = dp(10).toFloat()
        }

        floatingBubbleView = container
        populateBubbleContent(container)
        measureAndPositionBubble(container, params, preserveAnchorFromPrev = false)
    }

    private fun isPointInsideView(view: View?, rawX: Float, rawY: Float): Boolean {
        if (view == null || view.visibility != View.VISIBLE || view.windowToken == null) return false
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val x = rawX.toInt()
        val y = rawY.toInt()
        return x >= loc[0] && x <= loc[0] + view.width && y >= loc[1] && y <= loc[1] + view.height
    }

    private fun measureBubbleSize(container: View): Pair<Int, Int> {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val maxW = (screenW - dp(16)).coerceAtLeast(dp(40))
        val maxH = (screenH - dp(32)).coerceAtLeast(dp(40))
        container.measure(
            View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST)
        )
        val w = container.measuredWidth.coerceAtLeast(dp(36))
        val h = container.measuredHeight.coerceAtLeast(dp(36))
        return Pair(w, h)
    }

    private fun measureAndPositionBubble(
        container: View,
        params: WindowManager.LayoutParams,
        preserveAnchorFromPrev: Boolean,
        prevX: Int = params.x,
        prevY: Int = params.y,
        prevW: Int = 0,
        prevH: Int = 0
    ) {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val (newW, newH) = measureBubbleSize(container)

        if (preserveAnchorFromPrev && prevW > 0 && prevH > 0) {
            val prevCenterX = prevX + prevW / 2
            val prevRight = prevX + prevW
            val wasRightAligned = prevCenterX > screenW / 2
            val targetX = if (wasRightAligned) prevRight - newW else prevX

            val prevBottom = prevY + prevH
            val wasBottomAligned = prevBottom >= screenH - dp(64)
            val targetY = if (wasBottomAligned) prevBottom - newH else prevY

            params.x = targetX.coerceIn(0, (screenW - newW).coerceAtLeast(0))
            params.y = targetY.coerceIn(0, (screenH - newH).coerceAtLeast(0))
        } else {
            params.x = params.x.coerceIn(0, (screenW - newW).coerceAtLeast(0))
            params.y = params.y.coerceIn(0, (screenH - newH).coerceAtLeast(0))
        }

        prefs.edit()
            .putInt(KEY_BUBBLE_X, params.x)
            .putInt(KEY_BUBBLE_Y, params.y)
            .apply()
    }

    private fun toggleBubbleOrientation() {
        isBubbleVertical = !isBubbleVertical
        prefs.edit().putBoolean(KEY_BUBBLE_IS_VERTICAL, isBubbleVertical).apply()
        refreshBubbleContent(preserveAnchor = true)
    }

    private fun refreshBubbleContent(preserveAnchor: Boolean = true) {
        val container = floatingBubbleView as? LinearLayout ?: return
        val params = bubbleParams ?: return
        val prevX = params.x
        val prevY = params.y
        val prevW = container.width.takeIf { it > 0 } ?: container.measuredWidth
        val prevH = container.height.takeIf { it > 0 } ?: container.measuredHeight

        populateBubbleContent(container)
        measureAndPositionBubble(
            container = container,
            params = params,
            preserveAnchorFromPrev = preserveAnchor,
            prevX = prevX,
            prevY = prevY,
            prevW = prevW,
            prevH = prevH
        )

        if (container.windowToken != null) {
            try {
                windowManager.updateViewLayout(container, params)
            } catch (_: Throwable) {}
        }
    }

    private fun getPinnedAppItems(): List<AppItem> {
        if (pinnedPackageNames.isEmpty()) return emptyList()
        val appByPkg = installedAppsList.associateBy { it.packageName }
        val pm = packageManager
        return pinnedPackageNames.mapNotNull { pkg ->
            appByPkg[pkg] ?: try {
                val ai = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(ai).toString()
                val icon = pm.getApplicationIcon(ai)
                AppItem(label, pkg, icon)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun populateBubbleContent(container: LinearLayout) {
        container.removeAllViews()
        bubblePinnedScrollView = null
        container.orientation = LinearLayout.VERTICAL
        container.gravity = if (isBubbleVertical) Gravity.END else Gravity.START

        val pinnedApps = getPinnedAppItems()
        val btnSize = dp(28)
        val gap = dp(4)

        if (!isBubbleVertical) {
            // HORIZONTAL MODE:
            // Baris 1 (Floating utama, ikon saja): [ ⋮⋮ | ↕ | 🚀 | (spacer) | 📝 | ✕ ]
            // Baris 2 (Di bawah floating): Icon Pin Pintasan
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val dragGrip = TextView(this).apply {
                text = "⋮⋮"
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
                includeFontPadding = false
                setTextColor(Color.parseColor("#64748B"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(2), 0, dp(4), 0)
            }
            topRow.addView(dragGrip)

            val btnToggleOrient = createBubbleIconButton(
                icon = "↕",
                sizePx = btnSize,
                bgColor = "#1E293B",
                strokeColor = "#334155",
                textColor = "#94A3B8",
                desc = "Ubah ke Vertikal"
            ) {
                toggleBubbleOrientation()
            }
            topRow.addView(btnToggleOrient)
            topRow.addView(createHorizontalSpacer(4))

            val btnPintasan = createBubbleIconButton(
                icon = "🚀",
                sizePx = btnSize,
                bgColor = "#0369A1",
                strokeColor = "#38BDF8",
                textColor = "#FFFFFF",
                desc = "Pintasan Aplikasi"
            ) {
                switchTab(0)
                showExpandedPanel()
            }
            topRow.addView(btnPintasan)

            // Flexible spacer pushes Catatan & Tutup cleanly to the right if pinned row is wider
            val flexSpacer = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(4), 1, 1f)
            }
            topRow.addView(flexSpacer)

            val btnCatatan = createBubbleIconButton(
                icon = "📝",
                sizePx = btnSize,
                bgColor = "#0C4A6E",
                strokeColor = "#38BDF8",
                textColor = "#FFFFFF",
                desc = "Catatan"
            ) {
                switchTab(1)
                showExpandedPanel()
            }
            topRow.addView(btnCatatan)
            topRow.addView(createHorizontalSpacer(4))

            val btnCloseBubble = createBubbleCloseIconButton(btnSize)
            topRow.addView(btnCloseBubble)

            if (pinnedApps.isNotEmpty()) {
                val maxPinnedWidth = (resources.displayMetrics.widthPixels - dp(36)).coerceAtLeast(dp(156))
                val approxPinsWidth = pinnedApps.size * btnSize + (pinnedApps.size - 1).coerceAtLeast(0) * gap

                topRow.measure(
                    View.MeasureSpec.makeMeasureSpec(maxPinnedWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(btnSize + dp(4), View.MeasureSpec.AT_MOST)
                )
                val topRowNaturalW = topRow.measuredWidth
                val targetRowWidth = maxOf(topRowNaturalW, minOf(approxPinsWidth, maxPinnedWidth))

                topRow.layoutParams = LinearLayout.LayoutParams(targetRowWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
                container.addView(topRow)
                container.addView(createVerticalSpacer(4))

                val pinsRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                for ((idx, app) in pinnedApps.withIndex()) {
                    if (idx > 0) pinsRow.addView(createHorizontalSpacer(4))
                    pinsRow.addView(createBubblePinnedAppButton(app, btnSize))
                }

                if (approxPinsWidth > maxPinnedWidth) {
                    val hScroll = HorizontalScrollView(this).apply {
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER
                        layoutParams = LinearLayout.LayoutParams(maxPinnedWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
                        addView(pinsRow)
                    }
                    bubblePinnedScrollView = hScroll
                    container.addView(hScroll)
                } else {
                    container.addView(pinsRow)
                }
            } else {
                container.addView(topRow)
            }
        } else {
            // VERTICAL MODE (Sejajar ke bawah: tombol tutup di kanan bawah dan atasnya catatan)
            val dragGrip = TextView(this).apply {
                text = "⋯"
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                setTextColor(Color.parseColor("#64748B"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(btnSize, dp(14))
            }
            container.addView(dragGrip)
            container.addView(createVerticalSpacer(2))

            val btnToggleOrient = createBubbleIconButton(
                icon = "↔",
                sizePx = btnSize,
                bgColor = "#1E293B",
                strokeColor = "#334155",
                textColor = "#94A3B8",
                desc = "Ubah ke Horizontal"
            ) {
                toggleBubbleOrientation()
            }
            container.addView(btnToggleOrient)
            container.addView(createVerticalSpacer(4))

            val btnPintasan = createBubbleIconButton(
                icon = "🚀",
                sizePx = btnSize,
                bgColor = "#0369A1",
                strokeColor = "#38BDF8",
                textColor = "#FFFFFF",
                desc = "Pintasan Aplikasi"
            ) {
                switchTab(0)
                showExpandedPanel()
            }
            container.addView(btnPintasan)

            if (pinnedApps.isNotEmpty()) {
                container.addView(createVerticalSpacer(4))
                val maxPinnedHeight = (resources.displayMetrics.heightPixels * 2 / 5).coerceAtLeast(dp(110))
                val pinsCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.END
                }
                for ((idx, app) in pinnedApps.withIndex()) {
                    if (idx > 0) pinsCol.addView(createVerticalSpacer(4))
                    pinsCol.addView(createBubblePinnedAppButton(app, btnSize))
                }

                val approxColHeight = pinnedApps.size * btnSize + (pinnedApps.size - 1).coerceAtLeast(0) * gap
                if (approxColHeight > maxPinnedHeight) {
                    val vScroll = ScrollView(this).apply {
                        isVerticalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, maxPinnedHeight)
                        addView(pinsCol)
                    }
                    bubblePinnedScrollView = vScroll
                    container.addView(vScroll)
                } else {
                    container.addView(pinsCol)
                }
            }

            // Atasnya tombol tutup: Catatan
            container.addView(createVerticalSpacer(4))
            val btnCatatan = createBubbleIconButton(
                icon = "📝",
                sizePx = btnSize,
                bgColor = "#0C4A6E",
                strokeColor = "#38BDF8",
                textColor = "#FFFFFF",
                desc = "Catatan"
            ) {
                switchTab(1)
                showExpandedPanel()
            }
            container.addView(btnCatatan)

            // Kanan bawah: Tombol Tutup
            container.addView(createVerticalSpacer(4))
            val btnCloseBubble = createBubbleCloseIconButton(btnSize)
            container.addView(btnCloseBubble)
        }
    }

    private fun createBubbleIconButton(
        icon: String,
        sizePx: Int,
        bgColor: String,
        strokeColor: String,
        textColor: String,
        desc: String,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = icon
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            setTextColor(Color.parseColor(textColor))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            contentDescription = desc
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor(bgColor))
                setStroke(dp(1), Color.parseColor(strokeColor))
            }
            setOnClickListener { onClick() }
            setOnLongClickListener {
                Toast.makeText(applicationContext, desc, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun createBubblePinnedAppButton(app: AppItem, sizePx: Int): View {
        val box = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#111E36"))
                setStroke(dp(1), Color.parseColor("#0284C7"))
            }
            contentDescription = app.name
            setOnClickListener {
                launchSingleApp(app.packageName)
            }
            setOnLongClickListener {
                Toast.makeText(applicationContext, app.name, Toast.LENGTH_SHORT).show()
                true
            }
        }
        val iconIv = ImageView(this).apply {
            val iconSize = (sizePx - dp(6)).coerceAtLeast(dp(18))
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            if (app.icon != null) {
                setImageDrawable(app.icon)
            } else {
                setImageResource(R.drawable.ic_app_icon)
            }
        }
        box.addView(iconIv)
        return box
    }

    private fun createBubbleCloseIconButton(sizePx: Int): TextView {
        var confirmingClose = false
        var resetRunnable: Runnable? = null

        val normalBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor("#1E293B"))
            setStroke(dp(1), Color.parseColor("#475569"))
        }
        val confirmRedBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor("#DC2626"))
            setStroke(dp(1), Color.parseColor("#FCA5A5"))
        }

        return TextView(this).apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            background = normalBg
            contentDescription = "Tutup Floating"

            fun resetToNormal() {
                confirmingClose = false
                text = "✕"
                setTextColor(Color.parseColor("#E2E8F0"))
                background = normalBg
            }

            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    resetRunnable?.let { removeCallbacks(it) }
                    resetToNormal()
                }
            })

            setOnLongClickListener {
                Toast.makeText(applicationContext, "Tutup Floating", Toast.LENGTH_SHORT).show()
                true
            }

            setOnClickListener {
                if (!confirmingClose) {
                    confirmingClose = true
                    text = "⚠️"
                    setTextColor(Color.WHITE)
                    background = confirmRedBg
                    resetRunnable?.let { removeCallbacks(it) }
                    val pending = Runnable {
                        if (!isStoppingOrDestroyed) {
                            resetToNormal()
                        }
                    }
                    resetRunnable = pending
                    postDelayed(pending, CLOSE_CONFIRM_TIMEOUT_MS)
                } else {
                    resetRunnable?.let { removeCallbacks(it) }
                    Toast.makeText(applicationContext, "Floating ditutup", Toast.LENGTH_SHORT).show()
                    closeFloatingService(disableInPref = true)
                }
            }
        }
    }

    private fun buildExpandedPanelView() {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val minW = minPanelWidthPx()
        val maxW = maxPanelWidthPx(screenW)
        val minH = minPanelHeightPx()
        val maxH = maxPanelHeightPx(screenH)

        val defaultW = dp(320).coerceIn(minW, maxW)
        val defaultH = dp(330).coerceIn(minH, maxH)
        val savedW = prefs.getInt(KEY_PANEL_WIDTH, defaultW).coerceIn(minW, maxW)
        val savedH = prefs.getInt(KEY_PANEL_HEIGHT, defaultH).coerceIn(minH, maxH)

        val savedX = prefs.getInt(KEY_PANEL_X, ((screenW - savedW) / 2).coerceAtLeast(0))
            .coerceIn(0, (screenW - savedW).coerceAtLeast(0))
        val savedY = prefs.getInt(KEY_PANEL_Y, dp(80))
            .coerceIn(0, (screenH - savedH).coerceAtLeast(0))

        val params = WindowManager.LayoutParams(
            savedW,
            savedH,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }
        panelParams = params

        val outerFrame = FrameLayout(this)

        val cardBgNormal = createPanelBackgroundDrawable(isResizing = false)
        val cardBgActive = createPanelBackgroundDrawable(isResizing = true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = cardBgNormal
            elevation = dp(14).toFloat()
            clipChildren = true
        }
        panelBackgroundCard = root

        // Draggable Header Bar
        val touchSlop = dp(6)
        val header = object : LinearLayout(this) {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = ev.rawX
                        initialTouchY = ev.rawY
                        isDragging = false
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (ev.rawX - initialTouchX).toInt()
                        val dy = (ev.rawY - initialTouchY).toInt()
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            isDragging = true
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (isDragging || abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            isDragging = true
                            val curW = params.width.coerceAtLeast(minPanelWidthPx())
                            val curH = params.height.coerceAtLeast(minPanelHeightPx())
                            val maxX = (resources.displayMetrics.widthPixels - curW).coerceAtLeast(0)
                            val maxY = (resources.displayMetrics.heightPixels - curH).coerceAtLeast(0)
                            params.x = (initialX + dx).coerceIn(0, maxX)
                            params.y = (initialY + dy).coerceIn(0, maxY)
                            if (outerFrame.windowToken != null) {
                                try {
                                    windowManager.updateViewLayout(outerFrame, params)
                                } catch (_: Throwable) {}
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            prefs.edit()
                                .putInt(KEY_PANEL_X, params.x)
                                .putInt(KEY_PANEL_Y, params.y)
                                .apply()
                        }
                        isDragging = false
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    dp(18).toFloat(), dp(18).toFloat(),
                    dp(18).toFloat(), dp(18).toFloat(),
                    0f, 0f, 0f, 0f
                )
                setColor(Color.parseColor("#FF111827"))
            }
        }

        val gripText = TextView(this).apply {
            text = "⋮⋮"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, dp(6), 0)
        }
        header.addView(gripText)

        val titleView = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(titleView)
        header.addView(createHorizontalSpacer(4))

        // Single 3-state transparency button: 100% -> 75% -> 50%
        val btnAlpha = createPillButton(
            label = "${currentAlphaPercent}%",
            bgColor = "#0F172A",
            strokeColor = "#38BDF8",
            textColor = "#38BDF8"
        ) {
            cycleOpacityPercent()
        }
        alphaToggleButton = btnAlpha
        header.addView(btnAlpha)
        header.addView(createHorizontalSpacer(4))

        val btnMinimize = createPillButton(
            label = "Mini",
            bgColor = "#1E293B",
            strokeColor = "#334155",
            textColor = "#E2E8F0"
        ) {
            showBubbleOnly()
        }
        header.addView(btnMinimize)
        header.addView(createHorizontalSpacer(4))

        val btnClosePanel = createCloseConfirmButton()
        header.addView(btnClosePanel)

        root.addView(header)

        // Divider line
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(Color.parseColor("#1E293B"))
        })

        // Tab Selector Row (Tab 0: Pintasan, Tab 1: Catatan)
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = ColorDrawableInt("#0B1329")
        }

        tabPintasanButton = createTabButton(
            label = "🚀 Pintasan",
            isSelected = selectedTab == 0
        ) {
            switchTab(0)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        tabCatatanButton = createTabButton(
            label = "📝 Catatan",
            isSelected = selectedTab == 1
        ) {
            switchTab(1)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        tabRow.addView(tabPintasanButton)
        tabRow.addView(createHorizontalSpacer(6))
        tabRow.addView(tabCatatanButton)
        root.addView(tabRow)

        // Divider
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(Color.parseColor("#1E293B"))
        })

        // Content Frame for Tabs
        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        // TAB 0: PINTASAN APP
        val tabPintasan = buildTabPintasanAppView()
        tabPintasanView = tabPintasan
        tabPintasan.visibility = if (selectedTab == 0) View.VISIBLE else View.GONE
        contentFrame.addView(tabPintasan)

        // TAB 1: CATATAN
        val tabCatatan = buildTabCatatanView()
        tabCatatanView = tabCatatan
        tabCatatan.visibility = if (selectedTab == 1) View.VISIBLE else View.GONE
        contentFrame.addView(tabCatatan)

        // INLINE NOTE EDITOR OVERLAY (Tambah / Ubah Catatan)
        val noteEditorOverlay = buildInlineNoteEditorOverlay()
        noteEditorOverlayView = noteEditorOverlay
        noteEditorOverlay.visibility = View.GONE
        contentFrame.addView(noteEditorOverlay)

        // INLINE COLUMN PICKER OVERLAY (Pilih Kolom Tertentu untuk Paste)
        val columnPickerOverlay = buildInlineColumnPickerOverlay()
        columnPickerOverlayView = columnPickerOverlay
        columnPickerOverlay.visibility = View.GONE
        contentFrame.addView(columnPickerOverlay)



        // SINGLE NOTE QR OVERLAY (Transfer catatan spesifik ke HP lain via QR)
        val singleNoteQrOverlay = buildSingleNoteQrOverlay()
        singleNoteQrOverlayView = singleNoteQrOverlay
        singleNoteQrOverlay.visibility = View.GONE
        contentFrame.addView(singleNoteQrOverlay)

        // 3-DEVICE TRANSFER HUB OVERLAY (Wi-Fi LAN / Barcode QR / Bagikan Backup)
        val transferHubOverlay = buildTransferHubOverlay()
        transferHubOverlayView = transferHubOverlay
        transferHubOverlay.visibility = View.GONE
        contentFrame.addView(transferHubOverlay)

        root.addView(contentFrame)
        outerFrame.addView(root)

        // Corner resize handles (Bottom-Right & Bottom-Left)
        val gripSize = dp(32)
        val rightResizeGrip = ResizeGripView(this, isRightCorner = true).apply {
            layoutParams = FrameLayout.LayoutParams(gripSize, gripSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            contentDescription = "Ubah ukuran floating kanan bawah"
            setOnTouchListener(
                createCornerResizeTouchListener(
                    outerFrame = outerFrame,
                    params = params,
                    isLeftCorner = false,
                    onResizingStateChanged = { resizing ->
                        root.background = if (resizing) cardBgActive else cardBgNormal
                    }
                )
            )
        }
        outerFrame.addView(rightResizeGrip)

        val leftResizeGrip = ResizeGripView(this, isRightCorner = false).apply {
            layoutParams = FrameLayout.LayoutParams(gripSize, gripSize).apply {
                gravity = Gravity.BOTTOM or Gravity.START
            }
            contentDescription = "Ubah ukuran floating kiri bawah"
            setOnTouchListener(
                createCornerResizeTouchListener(
                    outerFrame = outerFrame,
                    params = params,
                    isLeftCorner = true,
                    onResizingStateChanged = { resizing ->
                        root.background = if (resizing) cardBgActive else cardBgNormal
                    }
                )
            )
        }
        outerFrame.addView(leftResizeGrip)

        expandedPanelView = outerFrame
    }



    private fun buildTabPintasanAppView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        // Live App Search Bar
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
        }

        val searchIcon = TextView(this).apply {
            text = "🔍"
            textSize = 12f
            setPadding(0, 0, dp(6), 0)
        }
        searchRow.addView(searchIcon)

        val searchEdit = EditText(this).apply {
            hint = "Cari aplikasi cepat..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 12f
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) setOverlayFocusable(true)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    pintasanSearchQuery = s?.toString()?.trim() ?: ""
                    refreshPintasanGridUI()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        pintasanSearchInput = searchEdit
        searchRow.addView(searchEdit)

        val btnClearSearch = createMiniActionButton("✕") {
            searchEdit.setText("")
            pintasanSearchQuery = ""
            setOverlayFocusable(false)
            refreshPintasanGridUI()
        }
        searchRow.addView(btnClearSearch)
        root.addView(searchRow)
        root.addView(createVerticalSpacer(8))

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
            id = ID_PINTASAN_GRID_CONTAINER
        }
        scrollView.addView(container)
        root.addView(scrollView)

        return root
    }

    private fun buildTabCatatanView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        // Top action bar: + Tambah & Paste dari Clipboard
        val topActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnAddNote = createPillButton(
            label = "➕ Tambah",
            bgColor = "#0284C7",
            strokeColor = "#38BDF8",
            textColor = "#FFFFFF"
        ) {
            openNoteEditor(null)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnQuickPasteNote = createPillButton(
            label = "📋 Paste Baru",
            bgColor = "#065F46",
            strokeColor = "#34D399",
            textColor = "#FFFFFF"
        ) {
            val clipText = SplitScreenAccessibilityService.readFromClipboard(applicationContext).trim()
            if (clipText.isBlank()) {
                Toast.makeText(applicationContext, "Clipboard masih kosong", Toast.LENGTH_SHORT).show()
            } else {
                openNoteEditor(null, prefillContent = clipText)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnTransferHub = createPillButton(
            label = "📲 Sync 3 HP",
            bgColor = "#581C87",
            strokeColor = "#C084FC",
            textColor = "#FFFFFF"
        ) {
            openTransferHubOverlay()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f)
        }

        val btnTrashToggle = createPillButton(
            label = if (showingTrash) "📝 Catatan" else "🗑️ Sampah",
            bgColor = if (showingTrash) "#0F172A" else "#7F1D1D",
            strokeColor = if (showingTrash) "#38BDF8" else "#F87171",
            textColor = "#FFFFFF"
        ) {
            showingTrash = !showingTrash
            refreshNotesUI()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        topActions.addView(btnAddNote)
        topActions.addView(createHorizontalSpacer(3))
        topActions.addView(btnQuickPasteNote)
        topActions.addView(createHorizontalSpacer(3))
        topActions.addView(btnTransferHub)
        topActions.addView(createHorizontalSpacer(3))
        topActions.addView(btnTrashToggle)
        root.addView(topActions)
        root.addView(createVerticalSpacer(8))

        // Live Notes Search Bar
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
        }

        val searchIcon = TextView(this).apply {
            text = "🔍"
            textSize = 12f
            setPadding(0, 0, dp(6), 0)
        }
        searchRow.addView(searchIcon)

        val searchEdit = EditText(this).apply {
            hint = "Cari judul atau isi catatan..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 12f
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) setOverlayFocusable(true)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    notesSearchQuery = s?.toString()?.trim() ?: ""
                    refreshNotesUI()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        notesSearchInput = searchEdit
        searchRow.addView(searchEdit)

        val btnClearSearch = createMiniActionButton("✕") {
            searchEdit.setText("")
            notesSearchQuery = ""
            setOverlayFocusable(false)
            refreshNotesUI()
        }
        searchRow.addView(btnClearSearch)
        root.addView(searchRow)
        root.addView(createVerticalSpacer(8))

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
        }

        val listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        notesListContainer = listBox
        scrollView.addView(listBox)
        root.addView(scrollView)

        refreshNotesUI()
        return root
    }

    private fun refreshNotesUI() {
        val container = notesListContainer ?: return
        container.removeAllViews()

        if (showingTrash) {
            val allTrash = notesList.filter { it.isDeleted }
            val trashList = if (notesSearchQuery.isBlank()) {
                allTrash
            } else {
                allTrash.filter {
                    it.title.contains(notesSearchQuery, ignoreCase = true) ||
                    it.content.contains(notesSearchQuery, ignoreCase = true)
                }
            }
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(2), dp(2), dp(8))
            }

            val titleTv = TextView(this).apply {
                text = "🗑️ Tempat Sampah (${allTrash.size})"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F87171"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            headerRow.addView(titleTv)

            if (allTrash.isNotEmpty()) {
                val btnClearTrash = createPillButton(
                    label = "🧹 Bersihkan",
                    bgColor = "#7F1D1D",
                    strokeColor = "#F87171",
                    textColor = "#FFFFFF"
                ) {
                    emptyTrash()
                }
                headerRow.addView(btnClearTrash)
            }
            container.addView(headerRow)

            if (allTrash.isEmpty()) {
                val emptyBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(20), dp(12), dp(20))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(10).toFloat()
                        setColor(Color.parseColor("#111827"))
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                }
                val emptyTitle = TextView(this).apply {
                    text = "Tempat sampah kosong"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#94A3B8"))
                }
                emptyBox.addView(emptyTitle)
                container.addView(emptyBox)
                return
            }

            if (trashList.isEmpty() && notesSearchQuery.isNotBlank()) {
                val noMatchBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(20), dp(12), dp(20))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(10).toFloat()
                        setColor(Color.parseColor("#111827"))
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                }
                val noMatchTitle = TextView(this).apply {
                    text = "Sampah '$notesSearchQuery' tidak ditemukan"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#94A3B8"))
                }
                noMatchBox.addView(noMatchTitle)
                container.addView(noMatchBox)
                return
            }

            for ((idx, note) in trashList.withIndex()) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(10).toFloat()
                        setColor(Color.parseColor("#1F1315"))
                        setStroke(dp(1), Color.parseColor("#7F1D1D"))
                    }
                }

                val titleTv2 = TextView(this).apply {
                    text = note.title.ifBlank { "Catatan Sampah #${idx + 1}" }
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#FCA5A5"))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                card.addView(titleTv2)
                card.addView(createVerticalSpacer(3))

                val contentTv = TextView(this).apply {
                    text = note.content
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#E2E8F0"))
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                }
                card.addView(contentTv)
                card.addView(createVerticalSpacer(8))

                val buttonRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val btnRestore = createPillButton(
                    label = "♻️ Pulihkan",
                    bgColor = "#065F46",
                    strokeColor = "#34D399",
                    textColor = "#FFFFFF"
                ) {
                    restoreNote(note.id)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val btnDeletePerm = createPillButton(
                    label = "❌ Hapus Permanen",
                    bgColor = "#7F1D1D",
                    strokeColor = "#F87171",
                    textColor = "#FFFFFF"
                ) {
                    permanentlyDeleteNote(note.id)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                buttonRow.addView(btnRestore)
                buttonRow.addView(createHorizontalSpacer(6))
                buttonRow.addView(btnDeletePerm)
                card.addView(buttonRow)

                container.addView(card)
                container.addView(createVerticalSpacer(8))
            }
            return
        }

        val allActive = notesList.filter { !it.isDeleted }

        if (allActive.isEmpty()) {
            val emptyBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(22), dp(12), dp(22))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#111827"))
                    setStroke(dp(1), Color.parseColor("#1E293B"))
                }
            }
            val emptyTitle = TextView(this).apply {
                text = "Belum ada catatan aktif"
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#E2E8F0"))
                gravity = Gravity.CENTER
            }
            val emptyDesc = TextView(this).apply {
                text = "Tekan '➕ Tambah' atau '📋 Paste' untuk buat baru.\nJika catatan tidak sengaja terhapus, Anda bisa memeriksanya di tombol '🗑️ Sampah'."
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            }
            emptyBox.addView(emptyTitle)
            emptyBox.addView(emptyDesc)
            container.addView(emptyBox)
            return
        }

        val filteredNotes = if (notesSearchQuery.isBlank()) {
            allActive
        } else {
            allActive.filter {
                it.title.contains(notesSearchQuery, ignoreCase = true) ||
                it.content.contains(notesSearchQuery, ignoreCase = true)
            }
        }

        if (filteredNotes.isEmpty() && notesSearchQuery.isNotBlank()) {
            val noMatchBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(20), dp(12), dp(20))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#111827"))
                    setStroke(dp(1), Color.parseColor("#1E293B"))
                }
            }
            val noMatchTitle = TextView(this).apply {
                text = "Catatan '$notesSearchQuery' tidak ditemukan"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
            }
            noMatchBox.addView(noMatchTitle)
            container.addView(noMatchBox)
            return
        }

        for ((idx, note) in filteredNotes.withIndex()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
            }

            val titleTv = TextView(this).apply {
                text = note.title.ifBlank { "Catatan #${idx + 1}" }
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#38BDF8"))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            card.addView(titleTv)
            card.addView(createVerticalSpacer(3))

            val contentTv = TextView(this).apply {
                text = note.content
                textSize = 12f
                setTextColor(Color.parseColor("#F8FAFC"))
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
            }
            card.addView(contentTv)
            card.addView(createVerticalSpacer(8))

            // 1 BARIS SAJA - HANYA ICON TANPA TEKS
            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val btnCopy = createIconButton(
                icon = "📋",
                tooltip = "Salin Catatan ke Clipboard",
                bgColor = "#0369A1",
                strokeColor = "#38BDF8"
            ) {
                SplitScreenAccessibilityService.copyToClipboard(applicationContext, note.content)
                Toast.makeText(applicationContext, "Disalin: ${note.title.ifBlank { "Catatan" }}", Toast.LENGTH_SHORT).show()
                showBubbleOnly()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnPaste = createIconButton(
                icon = "📥",
                tooltip = "Tempel ke Input Aktif",
                bgColor = "#065F46",
                strokeColor = "#34D399"
            ) {
                handlePasteNoteToTarget(note.content)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnPickCol = createIconButton(
                icon = "🎯",
                tooltip = "Pilih Kolom Input di Layar",
                bgColor = "#0F172A",
                strokeColor = "#38BDF8"
            ) {
                openColumnPickerOverlay(note.content)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnShareQr = createIconButton(
                icon = "📲",
                tooltip = "Transfer / QR ke HP Lain",
                bgColor = "#581C87",
                strokeColor = "#C084FC"
            ) {
                openSingleNoteQrOverlay(note)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnEdit = createIconButton(
                icon = "✏️",
                tooltip = "Ubah Catatan",
                bgColor = "#0F172A",
                strokeColor = "#475569"
            ) {
                openNoteEditor(note)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnDelete = createIconDeleteConfirmButton {
                deleteNote(note.id)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            buttonRow.addView(btnCopy)
            buttonRow.addView(createHorizontalSpacer(3))
            buttonRow.addView(btnPaste)
            buttonRow.addView(createHorizontalSpacer(3))
            buttonRow.addView(btnPickCol)
            buttonRow.addView(createHorizontalSpacer(3))
            buttonRow.addView(btnShareQr)
            buttonRow.addView(createHorizontalSpacer(3))
            buttonRow.addView(btnEdit)
            buttonRow.addView(createHorizontalSpacer(3))
            buttonRow.addView(btnDelete)
            card.addView(buttonRow)

            container.addView(card)
            container.addView(createVerticalSpacer(8))
        }
    }

    private fun buildInlineNoteEditorOverlay(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawableInt("#0F172A")
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val headerTitle = TextView(this).apply {
            text = "Tambah Catatan"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        noteEditorHeaderTitle = headerTitle
        header.addView(headerTitle)

        val btnCancel = createPillButton(
            label = "Batal",
            bgColor = "#334155",
            strokeColor = "#475569",
            textColor = "#E2E8F0"
        ) {
            closeNoteEditor()
        }
        header.addView(btnCancel)
        root.addView(header)
        root.addView(createVerticalSpacer(8))

        val formScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
        }

        val formBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Kolom 1: Judul / Nama Kolom + Paste button
        val titleLabelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleLabel = TextView(this).apply {
            text = "Kolom Judul / Label:"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnPasteTitle = createCompactActionButton(
            label = "Paste",
            bgColor = "#065F46",
            strokeColor = "#34D399",
            textColor = "#FFFFFF"
        ) {
            val clip = SplitScreenAccessibilityService.readFromClipboard(applicationContext)
            if (clip.isNotBlank()) {
                noteTitleEditText?.setText(clip)
                noteTitleEditText?.setSelection(clip.length)
            } else {
                Toast.makeText(applicationContext, "Clipboard kosong", Toast.LENGTH_SHORT).show()
            }
        }
        titleLabelRow.addView(titleLabel)
        titleLabelRow.addView(btnPasteTitle)
        formBox.addView(titleLabelRow)
        formBox.addView(createVerticalSpacer(4))

        val titleEt = EditText(this).apply {
            hint = "Misal: Email, Alamat, No HP..."
            textSize = 12.5f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            setSingleLine(true)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = createPillDrawable("#1E293B", "#334155")
        }
        noteTitleEditText = titleEt
        formBox.addView(titleEt)
        formBox.addView(createVerticalSpacer(10))

        // Kolom 2: Isi Catatan + Paste button
        val contentLabelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val contentLabel = TextView(this).apply {
            text = "Kolom Isi Catatan:"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnPasteContent = createCompactActionButton(
            label = "Paste",
            bgColor = "#065F46",
            strokeColor = "#34D399",
            textColor = "#FFFFFF"
        ) {
            val clip = SplitScreenAccessibilityService.readFromClipboard(applicationContext)
            if (clip.isNotBlank()) {
                val existing = noteContentEditText?.text?.toString().orEmpty()
                val newText = if (existing.isBlank()) clip else "$existing\n$clip"
                noteContentEditText?.setText(newText)
                noteContentEditText?.setSelection(newText.length)
            } else {
                Toast.makeText(applicationContext, "Clipboard kosong", Toast.LENGTH_SHORT).show()
            }
        }
        contentLabelRow.addView(contentLabel)
        contentLabelRow.addView(btnPasteContent)
        formBox.addView(contentLabelRow)
        formBox.addView(createVerticalSpacer(4))

        val contentEt = EditText(this).apply {
            hint = "Tulis atau paste teks catatan di sini..."
            textSize = 12.5f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = createPillDrawable("#1E293B", "#334155")
        }
        noteContentEditText = contentEt
        formBox.addView(contentEt)
        formBox.addView(createVerticalSpacer(12))

        val btnSave = createPillButton(
            label = "💾 Simpan Catatan",
            bgColor = "#0284C7",
            strokeColor = "#38BDF8",
            textColor = "#FFFFFF"
        ) {
            val titleVal = noteTitleEditText?.text?.toString()?.trim().orEmpty()
            val contentVal = noteContentEditText?.text?.toString()?.trim().orEmpty()
            if (contentVal.isBlank()) {
                Toast.makeText(applicationContext, "Isi catatan tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@createPillButton
            }
            val editId = editingNoteId
            if (editId == null) {
                addNote(titleVal, contentVal)
                Toast.makeText(applicationContext, "Catatan ditambahkan", Toast.LENGTH_SHORT).show()
            } else {
                updateNote(editId, titleVal, contentVal)
                Toast.makeText(applicationContext, "Catatan diperbarui", Toast.LENGTH_SHORT).show()
            }
            closeNoteEditor()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        formBox.addView(btnSave)

        formScroll.addView(formBox)
        root.addView(formScroll)
        return root
    }

    private fun buildInlineColumnPickerOverlay(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawableInt("#0F172A")
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = TextView(this).apply {
            text = "Paste ke Kolom Tertentu:"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(titleTv)

        val btnRefreshCols = createPillButton(
            label = "Scan",
            bgColor = "#0C4A6E",
            strokeColor = "#38BDF8",
            textColor = "#38BDF8"
        ) {
            refreshColumnPickerList()
        }
        header.addView(btnRefreshCols)
        header.addView(createHorizontalSpacer(4))

        val btnCloseCol = createPillButton(
            label = "Batal",
            bgColor = "#334155",
            strokeColor = "#475569",
            textColor = "#E2E8F0"
        ) {
            closeColumnPickerOverlay()
        }
        header.addView(btnCloseCol)

        root.addView(header)
        root.addView(createVerticalSpacer(8))

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        columnPickerListContainer = listBox
        scroll.addView(listBox)
        root.addView(scroll)

        return root
    }

    private fun openNoteEditor(note: NoteItem?, prefillContent: String = "") {
        closePickerOverlay()
        closeColumnPickerOverlay()
        editingNoteId = note?.id
        noteEditorHeaderTitle?.text = if (note == null) "Tambah Catatan" else "Ubah Catatan"
        noteTitleEditText?.setText(note?.title ?: "")
        noteContentEditText?.setText(note?.content ?: prefillContent)

        setOverlayFocusable(true)
        noteEditorOverlayView?.visibility = View.VISIBLE
        val focusTarget = if (note == null && prefillContent.isNotBlank()) noteTitleEditText else noteContentEditText
        focusTarget?.requestFocus()
        focusTarget?.let { et ->
            et.setSelection(et.text?.length ?: 0)
            mainHandler.postDelayed({
                showSoftKeyboard(et)
            }, 120L)
        }
    }

    private fun closeNoteEditor() {
        editingNoteId = null
        noteEditorOverlayView?.visibility = View.GONE
        setOverlayFocusable(false)
    }

    private fun closePickerOverlay() {}

    private fun openColumnPickerOverlay(textToPaste: String) {
        closeNoteEditor()
        closePickerOverlay()
        pendingPasteText = textToPaste
        SplitScreenAccessibilityService.copyToClipboard(applicationContext, textToPaste)
        refreshColumnPickerList()
        columnPickerOverlayView?.visibility = View.VISIBLE
    }

    private fun closeColumnPickerOverlay() {
        columnPickerOverlayView?.visibility = View.GONE
    }

    private fun refreshColumnPickerList() {
        val listBox = columnPickerListContainer ?: return
        listBox.removeAllViews()

        val isAccessEnabled = SplitScreenAccessibilityService.isAccessibilityEnabled(applicationContext)
        if (!isAccessEnabled) {
            val warnTv = TextView(this).apply {
                text = "Aksesibilitas 'Pintasan' belum aktif.\nAktifkan agar Pintasan bisa mendeteksi & menempel langsung ke kolom input aplikasi di layar."
                textSize = 11.5f
                setTextColor(Color.parseColor("#FDBA74"))
                setPadding(dp(8), dp(8), dp(8), dp(10))
            }
            listBox.addView(warnTv)

            val btnEnable = createPillButton(
                label = "⚙️ Aktifkan Aksesibilitas",
                bgColor = "#9A3412",
                strokeColor = "#FB923C",
                textColor = "#FFFFFF"
            ) {
                SplitScreenAccessibilityService.openAccessibilitySettings(applicationContext)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            listBox.addView(btnEnable)
            return
        }

        val fields = SplitScreenAccessibilityService.getVisibleEditableFields(applicationContext)
        if (fields.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Tidak ada kolom input terdeteksi di layar.\nTeks sudah disalin ke Clipboard — ketuk kolom tujuan di aplikasi lain lalu tekan 'Scan' atau tempel langsung."
                textSize = 11.5f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(dp(8), dp(12), dp(8), dp(12))
            }
            listBox.addView(emptyTv)
            return
        }

        for (field in fields) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
                setOnClickListener {
                    val ok = SplitScreenAccessibilityService.pasteTextToSpecificField(
                        applicationContext,
                        field.node,
                        pendingPasteText
                    )
                    if (ok) {
                        Toast.makeText(applicationContext, "✅ Ditempel ke ${field.label}", Toast.LENGTH_SHORT).show()
                        closeColumnPickerOverlay()
                        showBubbleOnly()
                    } else {
                        Toast.makeText(applicationContext, "Gagal menempel langsung. Teks sudah di Clipboard!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val labelTv = TextView(this).apply {
                text = field.label
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#38BDF8"))
            }
            val previewTv = TextView(this).apply {
                text = "Isi saat ini: ${field.previewText}"
                textSize = 11f
                setTextColor(Color.parseColor("#CBD5E1"))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            row.addView(labelTv)
            row.addView(previewTv)
            listBox.addView(row)
            listBox.addView(createVerticalSpacer(6))
        }
    }

    private fun handlePasteNoteToTarget(text: String) {
        when (SplitScreenAccessibilityService.pasteTextToFocusedField(applicationContext, text)) {
            SplitScreenAccessibilityService.PasteOutcome.PASTED_TO_FIELD -> {
                Toast.makeText(applicationContext, "✅ Berhasil ditempel ke kolom!", Toast.LENGTH_SHORT).show()
                showBubbleOnly()
            }
            SplitScreenAccessibilityService.PasteOutcome.NEED_PICK_COLUMN -> {
                openColumnPickerOverlay(text)
            }
            SplitScreenAccessibilityService.PasteOutcome.COPIED_ONLY_NO_FIELD -> {
                Toast.makeText(applicationContext, "📋 Disalin! Ketuk kolom tujuan lalu tekan 'Kolom' atau Paste.", Toast.LENGTH_LONG).show()
                showBubbleOnly()
            }
            SplitScreenAccessibilityService.PasteOutcome.COPIED_ONLY_NO_SERVICE -> {
                Toast.makeText(applicationContext, "📋 Disalin ke Clipboard! Aktifkan Aksesibilitas untuk Auto-Paste ke kolom.", Toast.LENGTH_LONG).show()
                showBubbleOnly()
            }
        }
    }

    // -------------------------------------------------------------
    // SINGLE NOTE QR OVERLAY
    // -------------------------------------------------------------
    private fun buildSingleNoteQrOverlay(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawableInt("#0F172A")
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isClickable = true
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = TextView(this).apply {
            text = "📲 Transfer Catatan via QR"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        singleNoteQrTitleView = titleTv
        header.addView(titleTv)

        val btnClose = createPillButton(
            label = "Tutup",
            bgColor = "#334155",
            strokeColor = "#475569",
            textColor = "#E2E8F0"
        ) {
            closeSingleNoteQrOverlay()
        }
        header.addView(btnClose)
        root.addView(header)
        root.addView(createVerticalSpacer(8))

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val descTv = TextView(this).apply {
            text = "Scan QR ini dari HP ke-2 atau ke-3 menggunakan kamera / Pintasan untuk menyalin isi secara instan."
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(8))
        }
        singleNoteQrDescView = descTv
        body.addView(descTv)

        val qrFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.WHITE)
            }
        }

        val qrIv = ImageView(this).apply {
            val qrSize = dp(160)
            layoutParams = LinearLayout.LayoutParams(qrSize, qrSize)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        singleNoteQrImageView = qrIv
        qrFrame.addView(qrIv)
        body.addView(qrFrame)
        body.addView(createVerticalSpacer(10))

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnShare = createPillButton(
            label = "📤 Quick Share / WA",
            bgColor = "#581C87",
            strokeColor = "#C084FC",
            textColor = "#FFFFFF"
        ) {
            val note = currentQrNote
            if (note != null) {
                shareTextSystem("Catatan Pintasan: ${note.title}\n\n${note.content}")
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnCopyText = createPillButton(
            label = "📋 Salin Teks",
            bgColor = "#0369A1",
            strokeColor = "#38BDF8",
            textColor = "#FFFFFF"
        ) {
            val note = currentQrNote
            if (note != null) {
                SplitScreenAccessibilityService.copyToClipboard(applicationContext, note.content)
                Toast.makeText(applicationContext, "Teks catatan disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
                showBubbleOnly()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        actionsRow.addView(btnShare)
        actionsRow.addView(createHorizontalSpacer(6))
        actionsRow.addView(btnCopyText)
        body.addView(actionsRow)

        scroll.addView(body)
        root.addView(scroll)
        return root
    }

    private fun openSingleNoteQrOverlay(note: NoteItem) {
        currentQrNote = note
        closeNoteEditor()
        closeColumnPickerOverlay()
        closePickerOverlay()
        closeTransferHubOverlay()

        val overlay = singleNoteQrOverlayView ?: return
        singleNoteQrTitleView?.text = "📲 Transfer: ${note.title.ifBlank { "Catatan" }}"
        singleNoteQrDescView?.text = "Scan QR ini dari HP ke-2 atau ke-3 menggunakan kamera / Pintasan untuk menyalin isi secara instan."

        val jsonPayload = JSONObject().apply {
            put("type", "pintasan_single_note")
            put("id", note.id)
            put("title", note.title)
            put("content", note.content)
        }.toString()

        val qrBitmap = QrCodeUtil.generateQrBitmap(jsonPayload, dp(180), dp(180))
        singleNoteQrImageView?.setImageBitmap(qrBitmap)
        overlay.visibility = View.VISIBLE
    }

    private fun closeSingleNoteQrOverlay() {
        currentQrNote = null
        singleNoteQrOverlayView?.visibility = View.GONE
    }

    // -------------------------------------------------------------
    // 3-DEVICE TRANSFER HUB OVERLAY (Wi-Fi LAN / Barcode QR / Bagikan Backup)
    // -------------------------------------------------------------
    private fun buildTransferHubOverlay(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawableInt("#0F172A")
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val headerTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(this).apply {
            text = "📲 Transfer Data (3 HP)"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val subTitleTv = TextView(this).apply {
            text = "Sinkronisasi antar 3 HP di Wi-Fi sama / QR / Share"
            textSize = 10f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        headerTextCol.addView(titleTv)
        headerTextCol.addView(subTitleTv)
        header.addView(headerTextCol)

        val btnClose = createPillButton(
            label = "Tutup",
            bgColor = "#334155",
            strokeColor = "#475569",
            textColor = "#E2E8F0"
        ) {
            closeTransferHubOverlay()
        }
        header.addView(btnClose)
        root.addView(header)
        root.addView(createVerticalSpacer(8))

        val subTabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }

        tabLanBtn = createTabButton("📡 Wi-Fi LAN", isSelected = transferHubTab == 0) {
            switchTransferSubTab(0)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        tabQrBtn = createTabButton("🔲 QR Code", isSelected = transferHubTab == 1) {
            switchTransferSubTab(1)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        tabFileBtn = createTabButton("📁 Backup / Share", isSelected = transferHubTab == 2) {
            switchTransferSubTab(2)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        subTabRow.addView(tabLanBtn)
        subTabRow.addView(createHorizontalSpacer(4))
        subTabRow.addView(tabQrBtn)
        subTabRow.addView(createHorizontalSpacer(4))
        subTabRow.addView(tabFileBtn)
        root.addView(subTabRow)

        val subContentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val lanView = buildTransferLanView()
        tabLanView = lanView
        lanView.visibility = if (transferHubTab == 0) View.VISIBLE else View.GONE
        subContentFrame.addView(lanView)

        val qrView = buildTransferQrView()
        tabQrView = qrView
        qrView.visibility = if (transferHubTab == 1) View.VISIBLE else View.GONE
        subContentFrame.addView(qrView)

        val fileView = buildTransferFileView()
        tabFileView = fileView
        fileView.visibility = if (transferHubTab == 2) View.VISIBLE else View.GONE
        subContentFrame.addView(fileView)

        root.addView(subContentFrame)
        return root
    }

    private fun switchTransferSubTab(index: Int) {
        transferHubTab = index
        tabLanBtn?.background = createPillDrawable(if (index == 0) "#0284C7" else "#1E293B", if (index == 0) "#38BDF8" else "#334155")
        tabQrBtn?.background = createPillDrawable(if (index == 1) "#0284C7" else "#1E293B", if (index == 1) "#38BDF8" else "#334155")
        tabFileBtn?.background = createPillDrawable(if (index == 2) "#0284C7" else "#1E293B", if (index == 2) "#38BDF8" else "#334155")

        tabLanView?.visibility = if (index == 0) View.VISIBLE else View.GONE
        tabQrView?.visibility = if (index == 1) View.VISIBLE else View.GONE
        tabFileView?.visibility = if (index == 2) View.VISIBLE else View.GONE

        if (index == 0) {
            updateTransferServerStatusUI()
        } else if (index == 1) {
            refreshTransferQrCode()
        }
    }

    private fun buildTransferLanView(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(12))
        }

        val serverCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
        }

        val serverStatus = TextView(this).apply {
            text = "Status Server Wi-Fi: Memeriksa..."
            textSize = 11.5f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        transferServerStatusTv = serverStatus
        serverCard.addView(serverStatus)
        serverCard.addView(createVerticalSpacer(4))

        val serverDesc = TextView(this).apply {
            text = "HP ke-2 & ke-3 bisa membuka URL browser atau menarik/mengirim data langsung selama berada di Wi-Fi / Hotspot yang sama."
            textSize = 10.5f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        serverCard.addView(serverDesc)
        serverCard.addView(createVerticalSpacer(6))

        val toggleBtn = createPillButton(
            label = "🟢 Jalankan Server Wi-Fi",
            bgColor = "#065F46",
            strokeColor = "#34D399",
            textColor = "#FFFFFF"
        ) {
            toggleLanServer()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        transferServerToggleBtn = toggleBtn
        serverCard.addView(toggleBtn)

        box.addView(serverCard)
        box.addView(createVerticalSpacer(10))

        val targetCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#111827"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
        }

        val targetTitle = TextView(this).apply {
            text = "Hubungkan ke HP Target (HP 2 / HP 3):"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
        }
        targetCard.addView(targetTitle)
        targetCard.addView(createVerticalSpacer(4))

        val ipInput = EditText(this).apply {
            hint = "IP Target, misal: 192.168.1.25"
            textSize = 12f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            setSingleLine(true)
            val lastIp = prefs.getString(KEY_LAST_SYNC_IP, "") ?: ""
            setText(lastIp)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = createPillDrawable("#1E293B", "#334155")
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) setOverlayFocusable(true)
            }
        }
        transferLanIpInput = ipInput
        targetCard.addView(ipInput)
        targetCard.addView(createVerticalSpacer(8))

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnPull = createPillButton(
            label = "📥 Tarik (Pull) Data",
            bgColor = "#0369A1",
            strokeColor = "#38BDF8",
            textColor = "#FFFFFF"
        ) {
            pullNotesFromLanTarget(transferLanIpInput?.text?.toString().orEmpty())
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnPush = createPillButton(
            label = "📤 Kirim (Push) Data",
            bgColor = "#4338CA",
            strokeColor = "#818CF8",
            textColor = "#FFFFFF"
        ) {
            pushNotesToLanTarget(transferLanIpInput?.text?.toString().orEmpty())
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnRow.addView(btnPull)
        btnRow.addView(createHorizontalSpacer(5))
        btnRow.addView(btnPush)
        targetCard.addView(btnRow)

        box.addView(targetCard)
        scroll.addView(box)
        return scroll
    }

    private fun buildTransferQrView(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(12))
        }

        val desc = TextView(this).apply {
            text = "Scan QR ini dari HP 2 atau HP 3 untuk menyalin seluruh catatan tanpa perlu kabel / internet luar."
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(8))
        }
        transferQrDescView = desc
        box.addView(desc)

        val qrFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.WHITE)
            }
        }

        val qrIv = ImageView(this).apply {
            val qrSize = dp(170)
            layoutParams = LinearLayout.LayoutParams(qrSize, qrSize)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        transferQrImageView = qrIv
        qrFrame.addView(qrIv)
        box.addView(qrFrame)
        box.addView(createVerticalSpacer(10))

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnRefresh = createPillButton(
            label = "🔄 Perbarui QR",
            bgColor = "#1E293B",
            strokeColor = "#475569",
            textColor = "#E2E8F0"
        ) {
            refreshTransferQrCode()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnPasteQr = createPillButton(
            label = "📋 Impor Teks QR",
            bgColor = "#065F46",
            strokeColor = "#34D399",
            textColor = "#FFFFFF"
        ) {
            pasteAndImportNotesFromClipboard()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnRow.addView(btnRefresh)
        btnRow.addView(createHorizontalSpacer(5))
        btnRow.addView(btnPasteQr)
        box.addView(btnRow)

        scroll.addView(box)
        return scroll
    }

    private fun buildTransferFileView(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(12))
        }

        // Live stats card
        val activeCount = notesList.count { !it.isDeleted }
        val trashCount = notesList.count { it.isDeleted }
        val pinnedCount = pinnedPackageNames.size

        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createPillDrawable("#1E293B", "#334155")
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val statsTitle = TextView(this).apply {
            text = "📊 Status Data Tersimpan:"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val statsDesc = TextView(this).apply {
            text = "$activeCount Catatan Aktif • $trashCount di Sampah • $pinnedCount Aplikasi Disematkan"
            textSize = 11f
            setTextColor(Color.parseColor("#38BDF8"))
        }
        statsCard.addView(statsTitle)
        statsCard.addView(createVerticalSpacer(2))
        statsCard.addView(statsDesc)
        box.addView(statsCard)
        box.addView(createVerticalSpacer(10))

        val exportHeader = TextView(this).apply {
            text = "💾 EKSPOR / BUAT CADANGAN"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
        }
        box.addView(exportHeader)
        box.addView(createVerticalSpacer(6))

        val btnExportFile = createPillButton(
            label = "📁 Simpan / Bagikan Berkas JSON (.json)",
            bgColor = "#0369A1",
            strokeColor = "#38BDF8",
            textColor = "#FFFFFF"
        ) {
            try {
                val file = BackupRestoreManager.exportBackupToFile(applicationContext)
                val intent = BackupRestoreManager.createShareBackupIntent(applicationContext, file)
                startActivity(intent)
                Toast.makeText(applicationContext, "Berkas cadangan siap: ${file.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Gagal membuat berkas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        box.addView(btnExportFile)
        box.addView(createVerticalSpacer(6))

        val btnCopyJson = createPillButton(
            label = "📋 Salin Teks Cadangan ke Clipboard",
            bgColor = "#1E293B",
            strokeColor = "#475569",
            textColor = "#E2E8F0"
        ) {
            val json = BackupRestoreManager.createBackupJson(applicationContext)
            SplitScreenAccessibilityService.copyToClipboard(applicationContext, json)
            Toast.makeText(applicationContext, "Teks cadangan lengkap disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        box.addView(btnCopyJson)
        box.addView(createVerticalSpacer(12))

        val importHeader = TextView(this).apply {
            text = "📥 IMPOR / PULIHKAN DATA"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
        }
        box.addView(importHeader)
        box.addView(createVerticalSpacer(6))

        val btnMergeClipboard = createPillButton(
            label = "➕ Gabungkan dari Clipboard (Merge)",
            bgColor = "#065F46",
            strokeColor = "#34D399",
            textColor = "#FFFFFF"
        ) {
            pasteAndImportNotesFromClipboard(BackupRestoreManager.RestoreMode.MERGE)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        box.addView(btnMergeClipboard)
        box.addView(createVerticalSpacer(6))

        val btnOverwriteClipboard = createPillButton(
            label = "🔄 Pulihkan Penuh dari Clipboard (Timpa Semua)",
            bgColor = "#7F1D1D",
            strokeColor = "#F87171",
            textColor = "#FFFFFF"
        ) {
            pasteAndImportNotesFromClipboard(BackupRestoreManager.RestoreMode.OVERWRITE)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        box.addView(btnOverwriteClipboard)
        box.addView(createVerticalSpacer(6))

        val btnOpenMainBackup = createPillButton(
            label = "📂 Buka Pengelola File di Aplikasi Utama",
            bgColor = "#581C87",
            strokeColor = "#C084FC",
            textColor = "#FFFFFF"
        ) {
            try {
                val intent = Intent(applicationContext, com.example.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                closeTransferHubOverlay()
            } catch (_: Exception) {}
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        box.addView(btnOpenMainBackup)

        scroll.addView(box)
        return scroll
    }

    private fun openTransferHubOverlay() {
        closeNoteEditor()
        closeColumnPickerOverlay()
        closePickerOverlay()
        closeSingleNoteQrOverlay()
        updateTransferServerStatusUI()
        refreshTransferQrCode()
        transferHubOverlayView?.visibility = View.VISIBLE
    }

    private fun closeTransferHubOverlay() {
        transferHubOverlayView?.visibility = View.GONE
        setOverlayFocusable(false)
    }

    private fun updateTransferServerStatusUI() {
        val ip = DeviceSyncManager.getLocalIpAddress(applicationContext) ?: "127.0.0.1"
        val isRunning = DeviceSyncManager.isServerRunning
        val port = DeviceSyncManager.DEFAULT_PORT

        if (isRunning) {
            transferServerStatusTv?.text = "🟢 Server Wi-Fi Aktif: http://$ip:$port"
            transferServerStatusTv?.setTextColor(Color.parseColor("#34D399"))
            transferServerToggleBtn?.text = "🔴 Matikan Server Wi-Fi"
            transferServerToggleBtn?.background = createPillDrawable("#7F1D1D", "#F87171")
        } else {
            transferServerStatusTv?.text = "⚪ Server Wi-Fi Tidak Aktif (IP: $ip)"
            transferServerStatusTv?.setTextColor(Color.parseColor("#94A3B8"))
            transferServerToggleBtn?.text = "🟢 Jalankan Server Wi-Fi"
            transferServerToggleBtn?.background = createPillDrawable("#065F46", "#34D399")
        }
    }

    private fun toggleLanServer() {
        if (DeviceSyncManager.isServerRunning) {
            DeviceSyncManager.stopServer()
            updateTransferServerStatusUI()
            Toast.makeText(applicationContext, "Server Wi-Fi dimatikan", Toast.LENGTH_SHORT).show()
        } else {
            val started = DeviceSyncManager.startServer(
                currentNotesProvider = { getSyncNotesList() },
                onNotesReceived = { incoming, _ ->
                    mainHandler.post {
                        importSyncNotes(incoming, replace = false)
                        Toast.makeText(applicationContext, "Diterima ${incoming.size} catatan dari HP lain!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            updateTransferServerStatusUI()
            if (started) {
                val ip = DeviceSyncManager.getLocalIpAddress(applicationContext) ?: "127.0.0.1"
                Toast.makeText(applicationContext, "Server aktif di http://$ip:${DeviceSyncManager.DEFAULT_PORT}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "Gagal menjalankan server Wi-Fi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pullNotesFromLanTarget(targetHost: String) {
        val cleanHost = targetHost.trim()
        if (cleanHost.isBlank()) {
            Toast.makeText(applicationContext, "Masukkan IP HP target terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString(KEY_LAST_SYNC_IP, cleanHost).apply()
        Toast.makeText(applicationContext, "Menghubungkan ke $cleanHost...", Toast.LENGTH_SHORT).show()

        Thread {
            val result = DeviceSyncManager.fetchNotesFromDevice(cleanHost)
            mainHandler.post {
                if (result.isSuccess) {
                    val list = result.getOrNull().orEmpty()
                    importSyncNotes(list, replace = false)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Gagal terhubung"
                    Toast.makeText(applicationContext, "Gagal menarik: $err. Pastikan server di HP target aktif & di Wi-Fi sama.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun pushNotesToLanTarget(targetHost: String) {
        val cleanHost = targetHost.trim()
        if (cleanHost.isBlank()) {
            Toast.makeText(applicationContext, "Masukkan IP HP target terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString(KEY_LAST_SYNC_IP, cleanHost).apply()
        val listToSend = getSyncNotesList()
        if (listToSend.isEmpty()) {
            Toast.makeText(applicationContext, "Tidak ada catatan untuk dikirim", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(applicationContext, "Mengirim ke $cleanHost...", Toast.LENGTH_SHORT).show()

        Thread {
            val result = DeviceSyncManager.sendNotesToDevice(cleanHost, listToSend)
            mainHandler.post {
                if (result.isSuccess) {
                    Toast.makeText(applicationContext, "Sukses terkirim ke $cleanHost!", Toast.LENGTH_SHORT).show()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Gagal kirim"
                    Toast.makeText(applicationContext, "Gagal mengirim: $err. Pastikan server di HP target aktif.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun shareTextSystem(text: String, title: String = "Bagikan Catatan Pintasan") {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(chooser)
    }

    private fun getSyncNotesList(): List<DeviceSyncManager.SyncNote> {
        return notesList.map { DeviceSyncManager.SyncNote(it.id, it.title, it.content, it.isDeleted, it.deletedAt) }
    }

    private fun importSyncNotes(imported: List<DeviceSyncManager.SyncNote>, replace: Boolean = false) {
        if (imported.isEmpty()) {
            Toast.makeText(applicationContext, "Tidak ada catatan untuk diimpor", Toast.LENGTH_SHORT).show()
            return
        }
        val noteItems = imported.map {
            NoteItem(
                id = it.id.ifBlank { UUID.randomUUID().toString() },
                title = it.title,
                content = it.content,
                isDeleted = it.isDeleted,
                deletedAt = it.deletedAt
            )
        }
        val backupParsed = BackupRestoreManager.BackupParsedResult(
            app = "Pintasan",
            version = 2,
            timestamp = System.currentTimeMillis(),
            exportDate = "",
            notes = noteItems,
            pinnedApps = emptyList(),
            activeNotesCount = noteItems.count { !it.isDeleted },
            trashNotesCount = noteItems.count { it.isDeleted },
            isSuccess = true
        )
        val summary = BackupRestoreManager.restoreBackup(
            context = applicationContext,
            backup = backupParsed,
            mode = if (replace) BackupRestoreManager.RestoreMode.OVERWRITE else BackupRestoreManager.RestoreMode.MERGE
        )
        loadNotesFromPrefs()
        refreshNotesUI()
        Toast.makeText(applicationContext, summary.message, Toast.LENGTH_LONG).show()
    }

    private fun pasteAndImportNotesFromClipboard(mode: BackupRestoreManager.RestoreMode = BackupRestoreManager.RestoreMode.MERGE) {
        val clip = SplitScreenAccessibilityService.readFromClipboard(applicationContext).trim()
        if (clip.isBlank()) {
            Toast.makeText(applicationContext, "Clipboard masih kosong", Toast.LENGTH_SHORT).show()
            return
        }
        val parsed = BackupRestoreManager.parseBackup(clip, allowPlainTextFallback = true)
        if (parsed.isSuccess && (parsed.notes.isNotEmpty() || parsed.pinnedApps.isNotEmpty())) {
            val summary = BackupRestoreManager.restoreBackup(applicationContext, parsed, mode)
            loadNotesFromPrefs()
            loadPinnedAppsFromPrefs()
            refreshNotesUI()
            refreshPintasanGridUI()
            Toast.makeText(applicationContext, summary.message, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(applicationContext, "Teks di clipboard tidak dapat dibaca sebagai catatan atau cadangan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshTransferQrCode() {
        val iv = transferQrImageView ?: return
        val jsonPayload = DeviceSyncManager.exportNotesToJson(getSyncNotesList())
        val bitmap = QrCodeUtil.generateQrBitmap(jsonPayload, dp(180), dp(180))
        iv.setImageBitmap(bitmap)
    }

    private fun loadNotesFromPrefs() {
        notesList.clear()
        notesList.addAll(NotesStorageUtil.loadNotes(applicationContext))
    }

    private fun saveNotesToPrefs() {
        NotesStorageUtil.saveNotes(applicationContext, notesList)
    }

    private fun addNote(title: String, content: String) {
        val newNote = NoteItem(UUID.randomUUID().toString(), title, content)
        notesList.add(0, newNote)
        saveNotesToPrefs()
        refreshNotesUI()
    }

    private fun updateNote(id: String, title: String, content: String) {
        val idx = notesList.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val existing = notesList[idx]
            notesList[idx] = existing.copy(title = title, content = content)
            saveNotesToPrefs()
            refreshNotesUI()
        }
    }

    private fun deleteNote(id: String) {
        val idx = notesList.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val note = notesList[idx]
            val deletedAt = System.currentTimeMillis()
            notesList[idx] = note.copy(isDeleted = true, deletedAt = deletedAt)
            saveNotesToPrefs()
            refreshNotesUI()
            Toast.makeText(applicationContext, "Pindah ke Sampah 🗑️", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreNote(id: String) {
        val idx = notesList.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val note = notesList[idx]
            notesList[idx] = note.copy(isDeleted = false, deletedAt = 0L)
            saveNotesToPrefs()
            refreshNotesUI()
            Toast.makeText(applicationContext, "Catatan dipulihkan ♻️", Toast.LENGTH_SHORT).show()
        }
    }

    private fun permanentlyDeleteNote(id: String) {
        val removed = notesList.removeAll { it.id == id }
        if (removed) {
            saveNotesToPrefs()
            refreshNotesUI()
            Toast.makeText(applicationContext, "Dihapus permanen ❌", Toast.LENGTH_SHORT).show()
        }
    }

    private fun emptyTrash() {
        val count = notesList.count { it.isDeleted }
        notesList.removeAll { it.isDeleted }
        saveNotesToPrefs()
        refreshNotesUI()
        Toast.makeText(applicationContext, "$count catatan sampah dibersihkan 🧹", Toast.LENGTH_SHORT).show()
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        val params = panelParams ?: return
        val panel = expandedPanelView ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            hideSoftKeyboard(panel)
        }
        if (panel.windowToken != null) {
            try {
                windowManager.updateViewLayout(panel, params)
            } catch (_: Throwable) {}
        }
    }

    private fun showSoftKeyboard(target: View) {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Throwable) {}
    }

    private fun hideSoftKeyboard(view: View) {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (_: Throwable) {}
    }

    private fun createTabButton(
        label: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minHeight = dp(34)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = createPillDrawable(
                if (isSelected) "#0284C7" else "#1E293B",
                if (isSelected) "#38BDF8" else "#334155"
            )
            setOnClickListener { onClick() }
        }
    }

    private fun createMiniActionButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            minHeight = dp(24)
            setPadding(dp(7), dp(2), dp(7), dp(2))
            background = createPillDrawable("#1E293B", "#334155")
            setOnClickListener { onClick() }
        }
    }

    private fun createCompactActionButton(
        label: String,
        bgColor: String,
        strokeColor: String,
        textColor: String,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(textColor))
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minHeight = dp(32)
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = createPillDrawable(bgColor, strokeColor)
            setOnClickListener { onClick() }
        }
    }

    private fun createDeleteConfirmButton(onConfirmedDelete: () -> Unit): TextView {
        var confirmingDelete = false
        var resetRunnable: Runnable? = null

        val normalBg = createPillDrawable("#0F172A", "#475569")
        val confirmRedBg = createPillDrawable("#DC2626", "#FCA5A5")

        return TextView(this).apply {
            text = "Hapus"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCA5A5"))
            gravity = Gravity.CENTER
            minHeight = dp(32)
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = normalBg

            fun resetToNormal() {
                confirmingDelete = false
                text = "Hapus"
                setTextColor(Color.parseColor("#FCA5A5"))
                background = normalBg
            }

            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    resetRunnable?.let { removeCallbacks(it) }
                    resetToNormal()
                }
            })

            setOnClickListener {
                if (!confirmingDelete) {
                    confirmingDelete = true
                    text = "Yakin?"
                    setTextColor(Color.WHITE)
                    background = confirmRedBg
                    resetRunnable?.let { removeCallbacks(it) }
                    val pending = Runnable {
                        if (!isStoppingOrDestroyed) {
                            resetToNormal()
                        }
                    }
                    resetRunnable = pending
                    postDelayed(pending, CLOSE_CONFIRM_TIMEOUT_MS)
                } else {
                    resetRunnable?.let { removeCallbacks(it) }
                    onConfirmedDelete()
                }
            }
        }
    }

    private fun createIconButton(
        icon: String,
        tooltip: String,
        bgColor: String,
        strokeColor: String,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = icon
            textSize = 13.5f
            gravity = Gravity.CENTER
            minHeight = dp(32)
            setPadding(dp(4), dp(2), dp(4), dp(2))
            background = createPillDrawable(bgColor, strokeColor)
            contentDescription = tooltip
            setOnLongClickListener {
                Toast.makeText(applicationContext, tooltip, Toast.LENGTH_SHORT).show()
                true
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createIconDeleteConfirmButton(onConfirmedDelete: () -> Unit): TextView {
        var confirmingDelete = false
        var resetRunnable: Runnable? = null

        val normalBg = createPillDrawable("#0F172A", "#475569")
        val confirmRedBg = createPillDrawable("#DC2626", "#FCA5A5")

        return TextView(this).apply {
            text = "🗑️"
            textSize = 13.5f
            gravity = Gravity.CENTER
            minHeight = dp(32)
            setPadding(dp(4), dp(2), dp(4), dp(2))
            background = normalBg
            contentDescription = "Hapus Catatan"

            fun resetToNormal() {
                confirmingDelete = false
                text = "🗑️"
                background = normalBg
            }

            setOnLongClickListener {
                Toast.makeText(applicationContext, if (confirmingDelete) "Klik ⚠️ sekali lagi untuk menghapus" else "Hapus Catatan", Toast.LENGTH_SHORT).show()
                true
            }

            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    resetRunnable?.let { removeCallbacks(it) }
                    resetToNormal()
                }
            })

            setOnClickListener {
                if (!confirmingDelete) {
                    confirmingDelete = true
                    text = "⚠️"
                    background = confirmRedBg
                    Toast.makeText(applicationContext, "Tekan ⚠️ sekali lagi untuk konfirmasi hapus", Toast.LENGTH_SHORT).show()
                    resetRunnable?.let { removeCallbacks(it) }
                    val pending = Runnable {
                        if (!isStoppingOrDestroyed) {
                            resetToNormal()
                        }
                    }
                    resetRunnable = pending
                    postDelayed(pending, CLOSE_CONFIRM_TIMEOUT_MS)
                } else {
                    resetRunnable?.let { removeCallbacks(it) }
                    onConfirmedDelete()
                }
            }
        }
    }

    private fun switchTab(tabIndex: Int) {
        selectedTab = tabIndex
        tabPintasanButton?.background = createPillDrawable(
            if (tabIndex == 0) "#0284C7" else "#1E293B",
            if (tabIndex == 0) "#38BDF8" else "#334155"
        )
        tabCatatanButton?.background = createPillDrawable(
            if (tabIndex == 1) "#0284C7" else "#1E293B",
            if (tabIndex == 1) "#38BDF8" else "#334155"
        )

        closeNoteEditor()
        closeColumnPickerOverlay()
        closePickerOverlay()
        closeSingleNoteQrOverlay()
        closeTransferHubOverlay()

        tabPintasanView?.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        tabCatatanView?.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE

        if (tabIndex == 0) {
            refreshPintasanGridUI()
        } else if (tabIndex == 1) {
            refreshNotesUI()
        }
    }

    private fun refreshPintasanGridUI() {
        val tabView = tabPintasanView ?: return
        val container = tabView.findViewById<LinearLayout>(ID_PINTASAN_GRID_CONTAINER) ?: return
        container.removeAllViews()

        if (installedAppsList.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Memuat daftar aplikasi HP..."
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(20))
            }
            container.addView(emptyTv)
            return
        }

        loadPinnedAppsFromPrefs()

        val filteredList = if (pintasanSearchQuery.isBlank()) {
            installedAppsList
        } else {
            installedAppsList.filter {
                it.name.contains(pintasanSearchQuery, ignoreCase = true) ||
                it.packageName.contains(pintasanSearchQuery, ignoreCase = true)
            }
        }

        if (filteredList.isEmpty()) {
            val noMatchTv = TextView(this).apply {
                text = "Aplikasi '$pintasanSearchQuery' tidak ditemukan"
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(20))
            }
            container.addView(noMatchTv)
            return
        }

        val pinnedApps = filteredList
            .filter { pinnedPackageNames.contains(it.packageName) }
            .sortedBy { pinnedPackageNames.indexOf(it.packageName) }

        val otherApps = filteredList
            .filter { !pinnedPackageNames.contains(it.packageName) }
            .sortedBy { it.name.lowercase() }

        if (pintasanSearchQuery.isBlank()) {
            // SEKSI 1: APLIKASI DISEMATKAN (📌 PINNED APPS)
            val pinnedHeaderRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(2), dp(2), dp(6))
            }
            val pinnedLabel = TextView(this).apply {
                text = "📌 Aplikasi Disematkan (${pinnedApps.size})"
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#38BDF8"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            pinnedHeaderRow.addView(pinnedLabel)
            container.addView(pinnedHeaderRow)

            if (pinnedApps.isNotEmpty()) {
                for ((idx, app) in pinnedApps.withIndex()) {
                    container.addView(buildPintasanAppCard(
                        app = app,
                        isPinned = true,
                        canMoveUp = idx > 0,
                        canMoveDown = idx < pinnedApps.size - 1
                    ))
                    container.addView(createVerticalSpacer(5))
                }
            } else {
                val bannerTitle = TextView(this).apply {
                    text = "📌 Belum ada aplikasi disematkan"
                    textSize = 11.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#38BDF8"))
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                }
                container.addView(bannerTitle)
            }

            container.addView(createVerticalSpacer(12))

            val searchPromptBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(16), dp(12), dp(16))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#111827"))
                    setStroke(dp(1), Color.parseColor("#1E293B"))
                }
            }
            val promptTitle = TextView(this).apply {
                text = "🔍 Cari Aplikasi Lainnya"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#E2E8F0"))
                gravity = Gravity.CENTER
            }
            val promptDesc = TextView(this).apply {
                text = "Ketik nama aplikasi di kolom pencarian di atas untuk menemukan dan menyematkan aplikasi HP Anda dengan cepat tanpa lag."
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }
            searchPromptBox.addView(promptTitle)
            searchPromptBox.addView(promptDesc)
            container.addView(searchPromptBox)
        } else {
            // HASIL PENCARIAN
            val searchHeader = TextView(this).apply {
                text = "🔍 Hasil Pencarian (${filteredList.size}):"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(dp(2), 0, dp(2), dp(6))
            }
            container.addView(searchHeader)

            for (app in filteredList) {
                val isPinned = pinnedPackageNames.contains(app.packageName)
                val idx = pinnedPackageNames.indexOf(app.packageName)
                container.addView(buildPintasanAppCard(
                    app = app,
                    isPinned = isPinned,
                    canMoveUp = isPinned && idx > 0,
                    canMoveDown = isPinned && idx >= 0 && idx < pinnedPackageNames.size - 1
                ))
                container.addView(createVerticalSpacer(4))
            }
        }
    }

    private fun buildPintasanAppCard(
        app: AppItem,
        isPinned: Boolean,
        canMoveUp: Boolean = false,
        canMoveDown: Boolean = false
    ): View {
        val itemRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor(if (isPinned) "#111E36" else "#1E293B"))
                setStroke(
                    dp(1),
                    Color.parseColor(if (isPinned) "#0284C7" else "#283548")
                )
            }
            setOnClickListener {
                launchSingleApp(app.packageName)
            }
        }

        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(if (isPinned) 26 else 24), dp(if (isPinned) 26 else 24))
            if (app.icon != null) {
                setImageDrawable(app.icon)
            } else {
                setImageResource(R.drawable.ic_app_icon)
            }
        }
        itemRow.addView(iconView)
        itemRow.addView(createHorizontalSpacer(6))

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameTv = TextView(this).apply {
            text = app.name
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, if (isPinned) Typeface.BOLD else Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textCol.addView(nameTv)
        itemRow.addView(textCol)
        itemRow.addView(createHorizontalSpacer(4))

        val btnOpen = createPillButton(
            label = "Buka",
            bgColor = if (isPinned) "#0284C7" else "#0F172A",
            strokeColor = if (isPinned) "#38BDF8" else "#334155",
            textColor = "#FFFFFF"
        ) {
            launchSingleApp(app.packageName)
        }
        itemRow.addView(btnOpen)

        if (isPinned) {
            if (canMoveUp) {
                itemRow.addView(createHorizontalSpacer(3))
                val btnUp = createMiniActionButton("▲") {
                    movePinnedAppUp(app.packageName)
                }
                itemRow.addView(btnUp)
            }
            if (canMoveDown) {
                itemRow.addView(createHorizontalSpacer(3))
                val btnDown = createMiniActionButton("▼") {
                    movePinnedAppDown(app.packageName)
                }
                itemRow.addView(btnDown)
            }
        }

        itemRow.addView(createHorizontalSpacer(3))
        val btnPin = createPillButton(
            label = if (isPinned) "📍 Unpin" else "📌 Pin",
            bgColor = if (isPinned) "#7F1D1D" else "#0F172A",
            strokeColor = if (isPinned) "#F87171" else "#0284C7",
            textColor = if (isPinned) "#FCA5A5" else "#38BDF8"
        ) {
            togglePinApp(app.packageName)
        }
        itemRow.addView(btnPin)
        return itemRow
    }



    private fun launchSingleApp(packageName: String) {
        if (packageName.isBlank()) return
        recordAppLaunch(packageName)
        try {
            val pm = packageManager
            val intent = pm.getLaunchIntentForPackage(packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (isPanelExpanded) {
                showBubbleOnly()
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Gagal membuka aplikasi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createPillDrawable(bgColorHex: String, strokeColorHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor(bgColorHex))
            setStroke(dp(1), Color.parseColor(strokeColorHex))
        }
    }

    private fun createPanelBackgroundDrawable(isResizing: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(Color.parseColor("#F20F172A"))
            setStroke(
                dp(if (isResizing) 2 else 1),
                Color.parseColor(if (isResizing) "#7DD3FC" else "#38BDF8")
            )
        }
    }

    private fun createCornerResizeTouchListener(
        outerFrame: View,
        params: WindowManager.LayoutParams,
        isLeftCorner: Boolean,
        onResizingStateChanged: (Boolean) -> Unit
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var startW = 0
            private var startH = 0
            private var startRawX = 0f
            private var startRawY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        startW = params.width
                        startH = params.height
                        startRawX = event.rawX
                        startRawY = event.rawY
                        onResizingStateChanged(true)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val screenW = resources.displayMetrics.widthPixels
                        val screenH = resources.displayMetrics.heightPixels
                        val minW = minPanelWidthPx()
                        val maxW = maxPanelWidthPx(screenW)
                        val minH = minPanelHeightPx()
                        val maxH = maxPanelHeightPx(screenH)

                        val dx = (event.rawX - startRawX).toInt()
                        val dy = (event.rawY - startRawY).toInt()

                        if (isLeftCorner) {
                            val rightEdge = startX + startW
                            val maxPossibleW = minOf(maxW, rightEdge)
                            val newW = (startW - dx).coerceIn(minW.coerceAtMost(maxPossibleW), maxPossibleW)
                            params.width = newW
                            params.x = (rightEdge - newW).coerceIn(0, (screenW - newW).coerceAtLeast(0))
                        } else {
                            val availableW = (screenW - startX).coerceAtLeast(minW)
                            val clampedMaxW = minOf(maxW, availableW)
                            params.width = (startW + dx).coerceIn(minW.coerceAtMost(clampedMaxW), clampedMaxW)
                        }

                        val availableH = (screenH - startY).coerceAtLeast(minH)
                        val clampedMaxH = minOf(maxH, availableH)
                        params.height = (startH + dy).coerceIn(minH.coerceAtMost(clampedMaxH), clampedMaxH)

                        if (outerFrame.windowToken != null) {
                            try {
                                windowManager.updateViewLayout(outerFrame, params)
                            } catch (_: Throwable) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        onResizingStateChanged(false)
                        prefs.edit()
                            .putInt(KEY_PANEL_X, params.x)
                            .putInt(KEY_PANEL_Y, params.y)
                            .putInt(KEY_PANEL_WIDTH, params.width)
                            .putInt(KEY_PANEL_HEIGHT, params.height)
                            .apply()
                        return true
                    }
                }
                return false
            }
        }
    }

    private fun minPanelWidthPx(): Int = dp(240)
    private fun maxPanelWidthPx(screenW: Int): Int = (screenW - dp(16)).coerceAtLeast(minPanelWidthPx())
    private fun minPanelHeightPx(): Int = dp(180)
    private fun maxPanelHeightPx(screenH: Int): Int = (screenH - dp(48)).coerceAtLeast(minPanelHeightPx())

    private fun clampOverlaysToScreen() {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        panelParams?.let { p ->
            val minW = minPanelWidthPx()
            val maxW = maxPanelWidthPx(screenW)
            val minH = minPanelHeightPx()
            val maxH = maxPanelHeightPx(screenH)
            p.width = p.width.coerceIn(minW, maxW)
            p.height = p.height.coerceIn(minH, maxH)
            p.x = p.x.coerceIn(0, (screenW - p.width).coerceAtLeast(0))
            p.y = p.y.coerceIn(0, (screenH - p.height).coerceAtLeast(0))
            val panel = expandedPanelView
            if (panel != null && panel.windowToken != null) {
                try {
                    windowManager.updateViewLayout(panel, p)
                } catch (_: Throwable) {}
            }
        }

        bubbleParams?.let { bp ->
            val bubble = floatingBubbleView
            if (bubble != null) {
                val (measuredW, measuredH) = measureBubbleSize(bubble)
                bp.x = bp.x.coerceIn(0, (screenW - measuredW).coerceAtLeast(0))
                bp.y = bp.y.coerceIn(0, (screenH - measuredH).coerceAtLeast(0))
                if (bubble.windowToken != null) {
                    try {
                        windowManager.updateViewLayout(bubble, bp)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun createCloseConfirmButton(): TextView {
        var confirmingClose = false
        var resetRunnable: Runnable? = null

        val normalBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor("#1E293B"))
            setStroke(dp(1), Color.parseColor("#475569"))
        }
        val confirmRedBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor("#DC2626"))
            setStroke(dp(1), Color.parseColor("#FCA5A5"))
        }

        return TextView(this).apply {
            text = "Tutup"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            minHeight = dp(34)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = normalBg

            fun resetToNormal() {
                confirmingClose = false
                text = "Tutup"
                setTextColor(Color.parseColor("#E2E8F0"))
                background = normalBg
            }

            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    resetRunnable?.let { removeCallbacks(it) }
                    resetToNormal()
                }
            })

            setOnClickListener {
                if (!confirmingClose) {
                    confirmingClose = true
                    text = "Yakin?"
                    setTextColor(Color.WHITE)
                    background = confirmRedBg
                    resetRunnable?.let { removeCallbacks(it) }
                    val pending = Runnable {
                        if (!isStoppingOrDestroyed) {
                            resetToNormal()
                        }
                    }
                    resetRunnable = pending
                    postDelayed(pending, CLOSE_CONFIRM_TIMEOUT_MS)
                } else {
                    resetRunnable?.let { removeCallbacks(it) }
                    Toast.makeText(applicationContext, "Floating ditutup", Toast.LENGTH_SHORT).show()
                    closeFloatingService(disableInPref = true)
                }
            }
        }
    }

    private fun createPillButton(
        label: String,
        bgColor: String,
        strokeColor: String,
        textColor: String,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(textColor))
            gravity = Gravity.CENTER
            minHeight = dp(34)
            setPadding(dp(9), dp(4), dp(9), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor(bgColor))
                setStroke(dp(1), Color.parseColor(strokeColor))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createHorizontalSpacer(widthDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), 1)
        }
    }

    private fun createVerticalSpacer(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
        }
    }

    private fun showExpandedPanel() {
        if (isStoppingOrDestroyed) return
        isPanelExpanded = true
        removeOverlaySafely(floatingBubbleView)
        val panel = expandedPanelView ?: return
        val params = panelParams ?: return
        applyOpacityToOverlays()
        addOverlaySafely(panel, params)
    }

    private fun showBubbleOnly() {
        if (isStoppingOrDestroyed) return
        isPanelExpanded = false
        pintasanSearchInput?.clearFocus()
        notesSearchInput?.clearFocus()
        closeNoteEditor()
        closeColumnPickerOverlay()
        closePickerOverlay()
        closeSingleNoteQrOverlay()
        closeTransferHubOverlay()
        removeOverlaySafely(expandedPanelView)
        val bubble = floatingBubbleView ?: return
        val params = bubbleParams ?: return
        measureAndPositionBubble(bubble, params, preserveAnchorFromPrev = false)
        applyOpacityToOverlays()
        addOverlaySafely(bubble, params)
    }

    private fun addOverlaySafely(view: View, params: WindowManager.LayoutParams) {
        if (isStoppingOrDestroyed || !isRunning) return
        view.visibility = View.VISIBLE
        try {
            if (view.windowToken == null) {
                windowManager.addView(view, params)
            } else {
                windowManager.updateViewLayout(view, params)
            }
        } catch (_: Throwable) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Throwable) {}
        }
    }

    private fun removeOverlaySafely(view: View?) {
        if (view == null) return
        try {
            view.visibility = View.GONE
        } catch (_: Throwable) {}
        if (view.windowToken != null) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Throwable) {
                try {
                    windowManager.removeView(view)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun closeFloatingService(disableInPref: Boolean) {
        isStoppingOrDestroyed = true
        isRunning = false
        _isRunningState.value = false
        setOverlayFocusable(false)
        if (disableInPref) {
            setFloatingEnabledPref(applicationContext, false)
        }
        removeOverlaySafely(floatingBubbleView)
        removeOverlaySafely(expandedPanelView)
        stopForegroundCompat()
        stopSelf()
    }

    private inner class ColorDrawableInt(
        colorHex: String,
        strokeHex: String = "#38BDF8",
        radiusDp: Int = 12
    ) : GradientDrawable() {
        init {
            shape = RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(colorHex))
            setStroke(dp(1), Color.parseColor(strokeHex))
        }
    }

    private inner class ResizeGripView(
        context: Context,
        private val isRightCorner: Boolean
    ) : View(context) {

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            strokeWidth = dp(2).toFloat().coerceAtLeast(2f)
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val pad = dp(7).toFloat()

            if (isRightCorner) {
                val offsets = floatArrayOf(dp(6).toFloat(), dp(11).toFloat(), dp(16).toFloat())
                for (offset in offsets) {
                    canvas.drawLine(
                        w - pad - offset,
                        h - pad,
                        w - pad,
                        h - pad - offset,
                        linePaint
                    )
                }
            } else {
                val offsets = floatArrayOf(dp(6).toFloat(), dp(11).toFloat(), dp(16).toFloat())
                for (offset in offsets) {
                    canvas.drawLine(
                        pad,
                        h - pad - offset,
                        pad + offset,
                        h - pad,
                        linePaint
                    )
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "pintasan_floating_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP_SERVICE = "com.acopy.action.STOP_FLOATING"
        const val ACTION_EXPAND_PANEL = "com.acopy.action.EXPAND_FLOATING"
        const val ACTION_MINIMIZE_BUBBLE = "com.acopy.action.MINIMIZE_FLOATING"
        const val ACTION_SYNC_NOTES = "com.acopy.action.SYNC_NOTES"

        private const val CLOSE_CONFIRM_TIMEOUT_MS = 3000L
        private val ALPHA_STEPS = listOf(100, 75, 50)

        private const val ID_PINTASAN_GRID_CONTAINER = 9001

        private const val PREFS_NAME = "pintasan_floating_prefs"
        private const val KEY_FLOATING_ENABLED = "floating_enabled"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val KEY_BUBBLE_IS_VERTICAL = "bubble_is_vertical"
        private const val KEY_PANEL_X = "panel_x"
        private const val KEY_PANEL_Y = "panel_y"
        private const val KEY_PANEL_WIDTH = "panel_width"
        private const val KEY_PANEL_HEIGHT = "panel_height"
        private const val KEY_ALPHA_PERCENT = "floating_alpha_percent"

        private const val KEY_NOTES_JSON = "saved_notes_json"
        private const val KEY_APP_USAGE_DATA = "pintasan_app_usage_data"
        private const val KEY_LAST_SYNC_IP = "last_sync_target_ip"
        private const val KEY_PINNED_APPS_JSON = "pintasan_pinned_apps_json"

        @Volatile
        var isRunning: Boolean = false
            private set

        private val _isRunningState = MutableStateFlow(false)
        val isRunningState: StateFlow<Boolean> = _isRunningState.asStateFlow()

        fun isFloatingEnabledPref(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_FLOATING_ENABLED, false)
        }

        fun setFloatingEnabledPref(context: Context, enabled: Boolean) {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_FLOATING_ENABLED, enabled)
                .apply()
        }
    }
}
