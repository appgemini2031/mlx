@file:Suppress("DEPRECATION")

package com.tajasb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.tajasb.data.VcamRepository
import com.tajasb.debug.VcamDebugLogger
import com.tajasb.floating.FloatingBubbleWindow
import com.tajasb.floating.FloatingServiceViewHost
import com.tajasb.floating.components.FloatingUiWidgets
import com.tajasb.floating.tabs.AutoTimeTabBuilder
import com.tajasb.floating.tabs.BuyerNotifTabBuilder
import com.tajasb.floating.tabs.LiveCommentTabBuilder
import com.tajasb.floating.tabs.QuranKhatamTabBuilder
import com.tajasb.floating.tabs.SettingsTabBuilder
import com.tajasb.floating.tabs.SpillTabBuilder
import com.tajasb.floating.tabs.StikerTabBuilder
import com.tajasb.floating.tabs.TapMacroTabBuilder
import com.tajasb.floating.tabs.TugOfWarTabBuilder
import com.tajasb.floating.tabs.WatermarkTabBuilder
import com.tajasb.model.ScaleMode
import com.tajasb.model.VcamConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class FloatingTab {
    VIDEO,
    MUSIC,
    VISUAL,
    NOTIF,
    TAP,
    SETTINGS,
    LOG
}

class FloatingControlService : FloatingServiceViewHost() {

    companion object {
        const val CHANNEL_ID = "vcam_floating_control_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP_SERVICE = "com.tajasb.action.STOP_FLOATING"
        const val ACTION_OPEN_LOG_TAB = "com.tajasb.action.OPEN_LOG_TAB"
        const val ACTION_SET_CLEAN_ARENA_MODE = "com.tajasb.action.SET_CLEAN_ARENA_MODE"
        const val EXTRA_CLEAN_ARENA_ACTIVE = "extra_clean_arena_active"
        const val EXTRA_LOG_FULLSCREEN = "extra_log_fullscreen"
        @Volatile var isRunning: Boolean = false
        internal val globalActiveOverlayViews: MutableSet<View> =
            java.util.Collections.synchronizedSet( java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>()) )
    }

    @Volatile internal var isStoppingOrDestroyed: Boolean = false
    @Volatile private var lastAudioToastWarningTimeMs: Long = 0L
    @Volatile private var lastWarnedAudioCategory: String = "OK"
    private var tapMarksUiRefreshJob: kotlinx.coroutines.Job? = null

    internal fun createStyledOverlayPreviewView(isWatermark: Boolean): View =
        FloatingUiWidgets.createStyledOverlayPreviewView(this, isWatermark)

    internal fun showVipOnlyToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        try {
            if (com.tajasb.license.LicenseManager.getInstance(applicationContext).isVipDecoyBypassed()) {
                Toast.makeText(applicationContext, message, duration).show()
            }
        } catch (_: Throwable) {}
    }

    internal fun copyToClipboard(label: String, text: String) {
        try {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
            showVipOnlyToast("$label disalin ke clipboard! 📋", Toast.LENGTH_SHORT)
        } catch (_: Exception) {}
    }

    internal fun requestTapMarksUiRefresh(immediate: Boolean = false) {
        if (!isPanelExpanded || currentTab != FloatingTab.TAP) {
            isTapMarksUiDirty = true
            return
        }
        tapMarksUiRefreshJob?.cancel()
        if (immediate) {
            isTapMarksUiDirty = false
            onRefreshTapMarksUi?.invoke()
        } else {
            tapMarksUiRefreshJob = serviceScope.launch {
                kotlinx.coroutines.delay(45L)
                if (isPanelExpanded && currentTab == FloatingTab.TAP) {
                    isTapMarksUiDirty = false
                    onRefreshTapMarksUi?.invoke()
                } else {
                    isTapMarksUiDirty = true
                }
            }
        }
    }

    internal lateinit var repository: VcamRepository
    internal val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var streamStalledReceiver: android.content.BroadcastReceiver? = null
    private var remoteCommandReceiver: android.content.BroadcastReceiver? = null
    private var autoRocketJob: kotlinx.coroutines.Job? = null
    @Volatile private var lastAutoRocketTriggerTime = 0L
    @Volatile private var lastPeriodicRamCleanTime = 0L

    override fun onCreate() {
        super.onCreate()
        isStoppingOrDestroyed = false
        isRunning = true
        repository = VcamRepository.getInstance(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Bersihkan sisa view lama jika sempat ada instance sebelumnya agar tidak pernah dobel
        purgeAllOverlayViews()

        // Inisialisasi Pelapor Crash & Telemetri Remote Realtime
        try {
            com.tajasb.debug.CrashReporter.init(this)
            com.tajasb.remote.RemoteTelemetryManager.getInstance(this).start(serviceScope)
            com.tajasb.remote.TelegramScreenMonitorManager.getInstance(this).ensureStarted()
        } catch (_: Throwable) {}

        // Daftarkan Receiver Perintah Remote (Kill-Switch & Pesan Layar)
        try {
            remoteCommandReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        com.tajasb.remote.RemoteTelemetryManager.ACTION_REMOTE_KILL -> {
                            android.util.Log.w("VcamFloating", "Menerima sinyal REMOTE KILL, menutup floating bubble...")
                            stopAllAudioAndPlayback()
                            stopAutoTapIfRunning()
                            stopSelf()
                        }
                        com.tajasb.remote.RemoteTelemetryManager.ACTION_REMOTE_MESSAGE -> {
                            val msg = intent.getStringExtra("message") ?: ""
                            if (msg.isNotBlank()) {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(this@FloatingControlService, "📢 PESAN ADMIN:\n$msg", Toast.LENGTH_LONG).show()
                                    panelTitleStatus?.let { tv ->
                                        tv.text = "📢 $msg"
                                        tv.setTextColor(Color.parseColor("#FF5252"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val filter = android.content.IntentFilter().apply {
                addAction(com.tajasb.remote.RemoteTelemetryManager.ACTION_REMOTE_KILL)
                addAction(com.tajasb.remote.RemoteTelemetryManager.ACTION_REMOTE_MESSAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(remoteCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(remoteCommandReceiver, filter)
            }
        } catch (_: Throwable) {}

        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildForegroundNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
            }
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildForegroundNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildForegroundNotification())
                }
            } catch (_: Throwable) {}
        }

        createBubbleView()
        createExpandedPanelView()

        // Show expanded panel by default
        showExpandedPanel()

        // Register Debug Logger Receiver
        try {
            VcamDebugLogger.registerBroadcastReceiver(this, serviceScope)
            VcamDebugLogger.i("VcamFloating", "FloatingControlService dimulai")
        } catch (_: Exception) {}

        observeState()
        initAutoRocketGuardian()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopAllAudioAndPlayback()
            closeFloatingService(disableInConfig = false)
            return START_NOT_STICKY
        }
        if (isStoppingOrDestroyed) {
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_OPEN_LOG_TAB) {
            val wantFull = intent.getBooleanExtra(EXTRA_LOG_FULLSCREEN, false)
            showExpandedPanel()
            selectTab(FloatingTab.LOG)
            if (wantFull != isFloatingLogFullScreen) {
                toggleFloatingLogFullScreen(forceFull = wantFull)
            }
        } else if (intent?.action == ACTION_SET_CLEAN_ARENA_MODE) {
            val cleanArena = intent.getBooleanExtra(EXTRA_CLEAN_ARENA_ACTIVE, false)
            if (cleanArena) {
                // Sembunyikan sementara seluruh jendela floating di Device 2 agar rekaman layar TikTok Game Seluler 100% bersih
                isPanelExpanded = false
                setPanelFocusable(false)
                removeViewSafely(expandedControlPanelView)
                removeViewSafely(floatingBubbleView)
            } else {
                showBubbleOnly()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Auto-terminate all audio & hooks if user clears/force-closes Tajasb from Recents
        stopAllAudioAndPlayback()
        closeFloatingService(disableInConfig = false)
    }

    private fun stopAllAudioAndPlayback() {
        try {
            com.tajasb.infinity.IfsManager.getInstance(applicationContext).stopAllBackgroundPlaybackOnAppExit()
        } catch (_: Throwable) {}
        try {
            repository.musicPlayer.stopAll(clearTrackState = false)
        } catch (_: Exception) {}
        try {
            com.tajasb.quran.QuranAudioDownloadManager.getInstance(applicationContext).stopVoiceTest()
        } catch (_: Throwable) {}
        try {
            val stopIntent = Intent("com.tajasb.action.STOP_PLAYBACK")
            sendBroadcast(stopIntent)
            val stopAudioIntent = Intent("com.tajasb.action.STOP_ALL_AUDIO")
            sendBroadcast(stopAudioIntent)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    internal fun stopAutoTapIfRunning() {
        try {
            val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
            autoTap.stopTapping()
            autoTap.setPointerVisible(false)
        } catch (_: Exception) {}
        removeCrosshairPointer()
    }

    internal fun purgeAllOverlayViews() {
        try {
            removeCrosshairPointer()
        } catch (_: Throwable) {}
        removeViewSafely(floatingBubbleView)
        removeViewSafely(expandedControlPanelView)
        try {
            scanAreaOverlayView?.dismiss()
            scanAreaOverlayView = null
        } catch (_: Throwable) {}
        val snapshot = synchronized(globalActiveOverlayViews) {
            val list = globalActiveOverlayViews.toList()
            globalActiveOverlayViews.clear()
            list
        }
        snapshot.forEach { v ->
            try { v.visibility = View.GONE } catch (_: Throwable) {}
            try {
                windowManager.removeViewImmediate(v)
            } catch (_: Throwable) {
                try { windowManager.removeView(v) } catch (_: Throwable) {}
            }
        }
    }

    internal fun closeFloatingService(disableInConfig: Boolean = true) {
        isStoppingOrDestroyed = true
        isRunning = false
        tapMarksUiRefreshJob?.cancel()
        autoRocketJob?.cancel()
        serviceScope.cancel()
        if (disableInConfig) {
            try {
                repository.updateConfigSync(writeHooks = false) { it.copy(isFloatingControlEnabled = false) }
            } catch (_: Throwable) {}
        }
        if (!com.example.MainActivity.isActivityResumed) {
            stopAllAudioAndPlayback()
        }
        stopAutoTapIfRunning()
        purgeAllOverlayViews()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {}
        stopSelf()
    }

    override fun onDestroy() {
        isStoppingOrDestroyed = true
        isRunning = false
        tapMarksUiRefreshJob?.cancel()
        autoRocketJob?.cancel()
        serviceScope.cancel()
        stopAutoTapIfRunning()
        purgeAllOverlayViews()
        if (!com.example.MainActivity.isActivityResumed) {
            stopAllAudioAndPlayback()
        }
        try {
            if (streamStalledReceiver != null) {
                unregisterReceiver(streamStalledReceiver)
                streamStalledReceiver = null
            }
        } catch (_: Exception) {}
        try {
            if (remoteCommandReceiver != null) {
                unregisterReceiver(remoteCommandReceiver)
                remoteCommandReceiver = null
            }
        } catch (_: Exception) {}
        try {
            VcamDebugLogger.unregisterBroadcastReceiver(this)
        } catch (_: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun initAutoRocketGuardian() {
        // 1. Listen for instant stall broadcast from MainHook / GlVideoRenderer & Decoy App Freeze
        try {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        com.tajasb.engine.DecoyEngine.ACTION_DECOY_APP_FREEZE -> {
                            val frozen = intent.getBooleanExtra("frozen", true)
                            val isVip = com.tajasb.license.LicenseManager.getInstance(applicationContext).isVipDecoyBypassed()
                            if (frozen && !isVip) {
                                try {
                                    bubbleLayoutParams?.let { lp ->
                                        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                        floatingBubbleView?.let { fv -> windowManager.updateViewLayout(fv, lp) }
                                    }
                                    panelLayoutParams?.let { lp ->
                                        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                        expandedControlPanelView?.let { fv -> windowManager.updateViewLayout(fv, lp) }
                                    }
                                } catch (_: Throwable) {}
                                com.tajasb.engine.DecoyEngine.activateProcessUiFreeze(sendCrossProcessBroadcast = false)
                            } else if (!frozen || isVip) {
                                try {
                                    bubbleLayoutParams?.let { lp ->
                                        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                        floatingBubbleView?.let { fv -> windowManager.updateViewLayout(fv, lp) }
                                    }
                                    panelLayoutParams?.let { lp ->
                                        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                        expandedControlPanelView?.let { fv -> windowManager.updateViewLayout(fv, lp) }
                                    }
                                } catch (_: Throwable) {}
                                com.tajasb.engine.DecoyEngine.releaseHostAndAppFreeze(sendCrossProcessBroadcast = false)
                            }
                        }
                        "com.tajasb.action.STREAM_STALLED" -> {
                            if (!com.tajasb.license.LicenseManager.getInstance(applicationContext).isVipDecoyBypassed()) {
                                return
                            }
                            val pkg = intent.getStringExtra("extra_package") ?: ""
                            val reason = intent.getStringExtra("extra_reason") ?: "unknown"
                            val duration = intent.getLongExtra("extra_stall_duration_ms", 3000L)
                            VcamDebugLogger.w("AutoRocket", "🚨 Menerima sinyal stream stall dari $pkg ($reason, ${duration}ms)")
                            triggerAutoRocketRecovery((duration / 1000).coerceAtLeast(3L), reason)
                        }
                    }
                }
            }
            streamStalledReceiver = receiver
            val filter = android.content.IntentFilter().apply {
                addAction("com.tajasb.action.STREAM_STALLED")
                addAction(com.tajasb.engine.DecoyEngine.ACTION_DECOY_APP_FREEZE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
        } catch (_: Exception) {}

        // 2. Active 1-Second Guardian Loop for Audio Hardware Anti-PL Guard & Zero-Touch Unfreeze
        autoRocketJob?.cancel()
        autoRocketJob = serviceScope.launch(Dispatchers.Default) {
            var tickCounter = 0
            while (isActive) {
                kotlinx.coroutines.delay(1000L)
                tickCounter++
                try {
                    val cfg = repository.config.value
                    val now = System.currentTimeMillis()

                    // Real-Time Audio Hardware & Signal Anti-PL Monitor (Every 2 seconds)
                    if (tickCounter % 2 == 0) {
                        val mp = repository.musicPlayer
                        val audioHealth = com.tajasb.engine.SystemDiagnosticHelper.checkAudioHealth(
                            context = applicationContext,
                            hasActiveVideo = cfg.playlist.isNotEmpty(),
                            isVideoPaused = cfg.isVideoPaused,
                            isVideoAudioMuted = cfg.isVideoAudioMuted,
                            videoVolume = repository.videoVolume.value,
                            hasMp3Playlist = cfg.audioPlaylist.isNotEmpty(),
                            isMp3Playing = mp.isPlaying.value,
                            isMp3Muted = mp.isMuted.value,
                            mp3Volume = mp.volume.value,
                            isAudioPlayerStalled = mp.isAudioStalledOrError.value,
                            audioPlayerErrorReason = mp.lastAudioErrorReason.value
                        )
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            updateFloatingAudioWarningBar(audioHealth)
                            if (audioHealth.hasIssue && audioHealth.severity == "CRITICAL" && cfg.isMasterEnabled) {
                                if (audioHealth.issueCategory != lastWarnedAudioCategory || (now - lastAudioToastWarningTimeMs > 25_000L)) {
                                    lastWarnedAudioCategory = audioHealth.issueCategory
                                    lastAudioToastWarningTimeMs = now
                                    VcamDebugLogger.w("AudioGuard", "${audioHealth.headlineText} — ${audioHealth.detailText}")
                                    Toast.makeText(
                                        applicationContext,
                                        "${audioHealth.headlineText}\nKetuk bar merah di Floating Tajasb untuk Pulihkan Suara!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else if (!audioHealth.hasIssue) {
                                lastWarnedAudioCategory = "OK"
                            }
                        }
                    }

                    if (com.tajasb.engine.DecoyEngine.isHardFrozenState || !com.tajasb.license.LicenseManager.getInstance(applicationContext).isVipDecoyBypassed()) {
                        continue
                    }
                    if (!cfg.isMasterEnabled || cfg.videoPath.isBlank() || cfg.isVideoPaused) {
                        continue
                    }

                    // Periodic RAM Clean Guardian (Prevent low memory drops after 1-3 hours live)
                    if (cfg.isPeriodicRamCleanEnabled) {
                        val intervalMs = (cfg.periodicRamCleanIntervalMinutes.coerceAtLeast(5)) * 60 * 1000L
                        if (lastPeriodicRamCleanTime == 0L) {
                            lastPeriodicRamCleanTime = now
                        } else if (now - lastPeriodicRamCleanTime >= intervalMs) {
                            lastPeriodicRamCleanTime = now
                            serviceScope.launch(Dispatchers.IO) {
                                try {
                                    val res = repository.triggerTurboRamClean()
                                    VcamDebugLogger.i("AutoBooster", "🧹 [AUTO-RAM] Pembersihan RAM rutin selesai (+${res.freedMb}MB bebas).")
                                } catch (_: Throwable) {}
                            }
                        }
                    }

                    // Auto-Rocket Freeze Detector (VIP Only)
                    // Guard with 9-second grace period after any video switch so large MB videos never trigger a false 4s reset to 0:00
                    if (cfg.isAutoRocketRecoveryEnabled) {
                        val lastPlayTime = repository.lastRealPlaybackTime.value
                        val isWithinVideoSwitchGrace = (now - repository.lastVideoSwitchTimestampMs) < 9_000L
                        val stallThresholdMs = ((cfg.autoRecoveryStallThresholdSec.coerceIn(2, 15)) * 1000L).coerceAtLeast(6_500L)

                        // If stream has been active but playback time hasn't advanced for > threshold
                        val isStalled = !isWithinVideoSwitchGrace && (lastPlayTime > 0L) && (now - lastPlayTime > stallThresholdMs)
                        val cooldownMs = 10_000L // 10s cooldown between recoveries

                        if (isStalled && (now - lastAutoRocketTriggerTime > cooldownMs)) {
                            lastAutoRocketTriggerTime = now
                            val stallSec = (now - lastPlayTime) / 1000
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                triggerAutoRocketRecovery(stallSec, "timer_heartbeat_timeout")
                            }
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.d("FloatingControlService", "Auto-Rocket loop: ${t.message}")
                }
            }
        }
    }

    internal fun updateFloatingAudioWarningBar(audioHealth: com.tajasb.engine.AudioHealthInfo) {
        val bar = floatingAudioSignalWarningBar ?: return
        if (!audioHealth.hasIssue) {
            bar.visibility = View.GONE
            return
        }
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        val isCrit = audioHealth.severity == "CRITICAL"
        bar.visibility = View.VISIBLE
        bar.text = "${audioHealth.shortBadgeText} • KETUK PULIHKAN SUARA 🔊"
        bar.setTextColor(Color.WHITE)
        bar.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(6).toFloat()
            setColor(Color.parseColor(if (isCrit) "#991B1B" else "#92400E"))
            setStroke(dp(1), Color.parseColor(if (isCrit) "#F87171" else "#FBBF24"))
        }
    }

    internal fun triggerAutoRocketRecovery(stallSec: Long = 0L, reason: String = "auto") {
        if (com.tajasb.engine.DecoyEngine.isHardFrozenState || !com.tajasb.license.LicenseManager.getInstance(applicationContext).isVipDecoyBypassed()) {
            return
        }
        val cfg = repository.config.value
        if (!cfg.isAutoRocketRecoveryEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastAutoRocketTriggerTime < 5_000L && reason != "manual_test") return
        lastAutoRocketTriggerTime = now

        serviceScope.launch {
            val count = repository.recordAutoRecovery()
            VcamDebugLogger.w("AutoRocket", "🚀 [AUTO-UNFREEZE] Live stream freeze terdeteksi (${stallSec}s, alasan=$reason). Memulihkan otomatis ke-$count...")

            // 1. Instantly trigger Rocket mechanism: Bring target app (e.g. TikTok) to front to refresh surface
            launchTargetApp(showChooser = false)

            // 2. Broadcast reload and config update
            try {
                val reloadIntent = Intent("com.tajasb.action.RELOAD_MEDIA")
                sendBroadcast(reloadIntent)
                repository.broadcastControlEvent(action = "UPDATE_CONFIG")
            } catch (_: Throwable) {}

            // 3. Clear fragmented buffers and RAM in background
            serviceScope.launch(Dispatchers.IO) {
                try {
                    repository.triggerTurboRamClean()
                } catch (_: Throwable) {}
            }

            // 4. Brief Toast feedback (VIP Only)
            showVipOnlyToast("🚀 Auto-Rocket: Layar pulih otomatis! (Ke-$count)", Toast.LENGTH_SHORT)
        }
    }

    internal fun addOverlayViewSafely(view: View, params: WindowManager.LayoutParams) {
        if (isStoppingOrDestroyed || !isRunning) return
        view.visibility = View.VISIBLE
        globalActiveOverlayViews.add(view)
        try {
            windowManager.addView(view, params)
        } catch (_: Throwable) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Throwable) {}
        }
    }

    internal fun removeViewSafely(view: View?) {
        if (view == null) return
        try {
            view.visibility = View.GONE
        } catch (_: Throwable) {}
        globalActiveOverlayViews.remove(view)
        try {
            windowManager.removeViewImmediate(view)
        } catch (_: Throwable) {
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {}
        }
    }

    private fun observeState() {
        // Observe Config
        serviceScope.launch {
            repository.config.collectLatest { cfg ->
                updateUiForConfig(cfg)
                onRefreshAiWatermarkUi?.invoke()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildForegroundNotification())
            }
        }

        serviceScope.launch {
            repository.aiWatermarkReport.collectLatest {
                onRefreshAiWatermarkUi?.invoke()
            }
        }

        serviceScope.launch {
            repository.isScanningAiWatermark.collectLatest {
                onRefreshAiWatermarkUi?.invoke()
            }
        }

        serviceScope.launch {
            repository.isCleaningAiWatermark.collectLatest {
                onRefreshAiWatermarkUi?.invoke()
            }
        }

        serviceScope.launch {
            if (repository.config.value.videoPath.isNotBlank() && repository.aiWatermarkReport.value == null) {
                repository.scanActiveVideoAiWatermark()
            }
        }

        // Observe IFS Active State & Audio Mode so Floating Video/Audio UI stays synced
        serviceScope.launch {
            try {
                com.tajasb.infinity.IfsManager.getInstance(this@FloatingControlService).isIfsActive.collectLatest {
                    updateUiForConfig(repository.config.value)
                }
            } catch (_: Throwable) {}
        }
        serviceScope.launch {
            try {
                com.tajasb.infinity.IfsManager.getInstance(this@FloatingControlService).audioMode.collectLatest {
                    updateMusicPlayerUi()
                }
            } catch (_: Throwable) {}
        }

        serviceScope.launch {
            repository.musicPlayer.isPlayingLayer2.collectLatest {
                updateMusicPlayerUi()
            }
        }
        serviceScope.launch {
            repository.musicPlayer.currentTrackLayer2.collectLatest {
                updateMusicPlayerUi()
            }
        }

        // Observe Video & Audio Volume State
        serviceScope.launch {
            repository.videoVolume.collectLatest { vol ->
                val pct = (vol * 100).toInt()
                videoVolumeLabel?.text = "🔊 $pct%"
                videoVolumeSeekBar?.progress = pct
            }
        }
        serviceScope.launch {
            repository.musicPlayer.volume.collectLatest { vol ->
                val pct = (vol * 100).toInt()
                musicVolumeLabel?.text = "🔊 $pct%"
                musicVolumeSeekBar?.progress = pct
            }
        }
        serviceScope.launch {
            repository.musicPlayer.volumeLayer2.collectLatest { vol ->
                val pct = (vol * 100).toInt()
                audioLayer2VolumeLabel?.text = "🔊 $pct%"
            }
        }
        serviceScope.launch {
            repository.musicPlayer.isPlayingLayer3.collectLatest {
                updateMusicPlayerUi()
            }
        }
        serviceScope.launch {
            repository.musicPlayer.nextRingtoneEtaSec.collectLatest {
                updateMusicPlayerUi()
            }
        }
        serviceScope.launch {
            repository.musicPlayer.currentTrackLayer3.collectLatest {
                updateMusicPlayerUi()
            }
        }
        serviceScope.launch {
            repository.musicPlayer.volumeLayer3.collectLatest { vol ->
                val pct = (vol * 100).toInt()
                audioLayer3VolumeLabel?.text = "🔔 $pct%"
            }
        }

        // ⚡ Observasi Live Event Broker Telemetry & Status
        serviceScope.launch {
            try {
                val receiver = com.tajasb.eventrelay.LiveEventReceiver.getInstance(applicationContext)
                receiver.connectionStatus.collectLatest {
                    updateLiveEventBrokerFloatingUi()
                }
            } catch (_: Throwable) {}
        }
        serviceScope.launch {
            try {
                val receiver = com.tajasb.eventrelay.LiveEventReceiver.getInstance(applicationContext)
                receiver.lastEvent.collectLatest {
                    updateLiveEventBrokerFloatingUi()
                }
            } catch (_: Throwable) {}
        }

        // Observe Video Track & Duration
        serviceScope.launch {
            var lastObservedVideoId: String? = null
            var lastObservedPath: String? = null
            repository.config.collectLatest { cfg ->
                val curVid = cfg.currentVideo
                if (curVid?.id != lastObservedVideoId || cfg.videoPath != lastObservedPath) {
                    lastObservedVideoId = curVid?.id
                    lastObservedPath = cfg.videoPath
                    currentVideoPositionMs = 0L
                }
                val dur = curVid?.durationMs ?: 0L
                currentVideoDurationMs = dur
                videoSeekBar?.max = dur.coerceAtLeast(1L).toInt()
                if (!isUserTrackingVideoSeek) {
                    videoTimeLabel?.text = "${formatTime(currentVideoPositionMs)} / ${formatTime(dur)}"
                    videoSeekBar?.progress = currentVideoPositionMs.coerceIn(0L, dur.coerceAtLeast(1L)).toInt()
                }
            }
        }

        serviceScope.launch {
            var lastStep = repository.playbackStepVersion.value
            repository.playbackStepVersion.collectLatest { step ->
                if (step != lastStep) {
                    lastStep = step
                    currentVideoPositionMs = 0L
                }
            }
        }

        // Observe Real-time Video Playback Position from Live Camera Hook or Preview Player
        serviceScope.launch {
            repository.realVideoPositionMs.collectLatest { posMs ->
                val dur = if (repository.realVideoDurationMs.value > 0L) {
                    repository.realVideoDurationMs.value
                } else {
                    currentVideoDurationMs
                }
                currentVideoPositionMs = posMs
                if (!isUserTrackingVideoSeek) {
                    videoTimeLabel?.text = "${formatTime(posMs)} / ${formatTime(dur)}"
                    videoSeekBar?.progress = posMs.coerceIn(0L, dur.coerceAtLeast(1L)).toInt()
                }
            }
        }

        // Passive UI-only seekbar sync (never triggers nextVideo or IFS completion from background service
        // to prevent out-of-sync jumps/freezes against VideoPlayerView and GlVideoRenderer)
        serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                val now = System.currentTimeMillis()
                val isLiveHookReporting = (now - repository.lastRealPlaybackTime.value) < 2500L
                val isPreviewReporting = (now - repository.lastPreviewPlaybackTime.value) < 2500L

                if (!isLiveHookReporting && !isPreviewReporting) {
                    val dur = currentVideoDurationMs
                    if (dur > 0L && !isUserTrackingVideoSeek) {
                        videoSeekBar?.progress = currentVideoPositionMs.coerceIn(0L, dur).toInt()
                        videoTimeLabel?.text = "${formatTime(currentVideoPositionMs)} / ${formatTime(dur)}"
                    }
                }
            }
        }

        // Observe Standalone Music Player State
        serviceScope.launch {
            repository.musicPlayer.isPlaying.collectLatest {
                updateMusicPlayerUi()
            }
        }

        serviceScope.launch {
            repository.isMusicAutoPausedWaitingForMute.collectLatest {
                updateMusicPlayerUi()
            }
        }

        serviceScope.launch {
            repository.musicPlayer.currentTrack.collectLatest {
                updateMusicPlayerUi()
            }
        }

        serviceScope.launch {
            repository.musicPlayer.isLooping.collectLatest {
                updateMusicPlayerUi()
            }
        }

        serviceScope.launch {
            repository.musicPlayer.volume.collectLatest { vol ->
                val pct = (vol * 100).toInt()
                musicVolumeLabel?.text = "Volume Musik: $pct%"
                musicVolumeSeekBar?.progress = pct
            }
        }

        serviceScope.launch {
            repository.musicPlayer.currentPositionMs.collectLatest { posMs ->
                if (!isUserTrackingMusicSeek) {
                    val durMs = repository.musicPlayer.durationMs.value
                    musicSeekBar?.max = durMs.coerceAtLeast(1L).toInt()
                    musicSeekBar?.progress = posMs.coerceIn(0L, durMs).toInt()
                    musicTimeLabel?.text = "${formatTime(posMs)} / ${formatTime(durMs)}"
                }
            }
        }

        serviceScope.launch {
            repository.musicPlayer.durationMs.collectLatest { durMs ->
                val posMs = repository.musicPlayer.currentPositionMs.value
                musicSeekBar?.max = durMs.coerceAtLeast(1L).toInt()
                musicTimeLabel?.text = "${formatTime(posMs)} / ${formatTime(durMs)}"
            }
        }

        // Observe Auto-Tap Manager State & Wire Exact Screen Coordinate / Pass-Through Hooks
        val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
        autoTap.onResolveScreenCoordinate = { markId ->
            FloatingBubbleWindow.resolveMarkScreenCoordinate(this@FloatingControlService, markId)
        }
        autoTap.onSetOverlaysPassThrough = { passThrough ->
            FloatingBubbleWindow.setAllOverlaysPassThrough(this@FloatingControlService, passThrough)
        }
        serviceScope.launch {
            autoTap.isTapping.collectLatest {
                if (autoTap.isPointerVisible.value && !isUserDraggingMark) {
                    showCrosshairPointer()
                }
                updateAutoTapUi()
                requestTapMarksUiRefresh(immediate = true)
            }
        }
        serviceScope.launch {
            autoTap.activeExecutingMarkId.collectLatest { execId ->
                if (autoTap.isPointerVisible.value && !isUserDraggingMark) {
                    FloatingBubbleWindow.updateExecutingMarkHighlight(this@FloatingControlService, execId)
                }
                requestTapMarksUiRefresh()
            }
        }
        serviceScope.launch {
            autoTap.totalTaps.collectLatest { taps ->
                tapCountText?.text = "$taps aksi"
            }
        }
        serviceScope.launch {
            autoTap.targetX.collectLatest { x ->
                val y = autoTap.targetY.value.toInt()
                if (autoTap.executionMode.value == com.tajasb.autotap.TapExecutionMode.SINGLE_LOVE) {
                    tapPosText?.text = "X:${x.toInt()} Y:$y"
                }
                if (isPanelExpanded) {
                    FloatingUiWidgets.updateFixedBottomDockUi(this@FloatingControlService)
                }
            }
        }
        serviceScope.launch {
            autoTap.targetY.collectLatest { y ->
                val x = autoTap.targetX.value.toInt()
                if (autoTap.executionMode.value == com.tajasb.autotap.TapExecutionMode.SINGLE_LOVE) {
                    tapPosText?.text = "X:$x Y:${y.toInt()}"
                }
                if (isPanelExpanded) {
                    FloatingUiWidgets.updateFixedBottomDockUi(this@FloatingControlService)
                }
            }
        }
        serviceScope.launch {
            autoTap.antiRobotEnabled.collectLatest {
                updateAutoTapUi()
                requestTapMarksUiRefresh()
            }
        }
        serviceScope.launch {
            autoTap.isPointerVisible.collectLatest { visible ->
                if (visible) showCrosshairPointer() else removeCrosshairPointer()
                updateBubbleIconsStatus()
                requestTapMarksUiRefresh()
                if (isPanelExpanded) {
                    FloatingUiWidgets.updateFixedBottomDockUi(this@FloatingControlService)
                }
            }
        }
        serviceScope.launch {
            autoTap.markDisplayMode.collectLatest {
                if (autoTap.isPointerVisible.value && !isUserDraggingMark) {
                    showCrosshairPointer()
                }
                updateBubbleIconsStatus()
                requestTapMarksUiRefresh()
                if (isPanelExpanded) {
                    FloatingUiWidgets.updateFixedBottomDockUi(this@FloatingControlService)
                }
            }
        }
        serviceScope.launch {
            autoTap.choreographyGroups.collectLatest {
                requestTapMarksUiRefresh()
            }
        }
        serviceScope.launch {
            autoTap.activeGroupId.collectLatest {
                requestTapMarksUiRefresh()
            }
        }
        serviceScope.launch {
            autoTap.chainAllChoreographies.collectLatest {
                requestTapTapUiAndMarksRefresh()
            }
        }
        serviceScope.launch {
            autoTap.isRandomChoreographyOrder.collectLatest {
                requestTapTapUiAndMarksRefresh()
            }
        }
        serviceScope.launch {
            autoTap.marks.collectLatest {
                if (autoTap.isPointerVisible.value && !isUserDraggingMark) {
                    showCrosshairPointer()
                }
                requestTapMarksUiRefresh()
                if (isPanelExpanded) {
                    FloatingUiWidgets.updateFixedBottomDockUi(this@FloatingControlService)
                }
            }
        }
        serviceScope.launch {
            autoTap.markSizeDp.collectLatest {
                if (autoTap.isPointerVisible.value && !isUserDraggingMark) {
                    showCrosshairPointer()
                }
                requestTapMarksUiRefresh()
            }
        }
        serviceScope.launch {
            autoTap.executionMode.collectLatest {
                if (autoTap.isPointerVisible.value && !isUserDraggingMark) {
                    showCrosshairPointer()
                }
                updateAutoTapUi()
                requestTapMarksUiRefresh()
                if (isPanelExpanded) {
                    FloatingUiWidgets.updateFixedBottomDockUi(this@FloatingControlService)
                }
            }
        }
        serviceScope.launch {
            autoTap.selectedMarkId.collectLatest {
                if (autoTap.isPointerVisible.value && !isUserDraggingMark) {
                    showCrosshairPointer()
                }
                requestTapMarksUiRefresh()
                if (isPanelExpanded) {
                    FloatingUiWidgets.updateFixedBottomDockUi(this@FloatingControlService)
                }
            }
        }
        serviceScope.launch {
            autoTap.activeStepDescription.collectLatest { desc ->
                if (autoTap.isTapping.value) {
                    tapStatusBadge?.text = "● $desc"
                }
            }
        }
        serviceScope.launch {
            autoTap.lastGeneratedDelayMs.collectLatest { d ->
                if (autoTap.isTapping.value && autoTap.executionMode.value == com.tajasb.autotap.TapExecutionMode.SINGLE_LOVE) {
                    tapStatusBadge?.text = "● AKTIF (${d}ms)"
                }
            }
        }
        serviceScope.launch {
            autoTap.autoTypeTexts.collectLatest {
                requestTapMarksUiRefresh()
            }
        }
        serviceScope.launch {
            autoTap.autoTypeSelectionMode.collectLatest {
                requestTapMarksUiRefresh()
            }
        }
        serviceScope.launch {
            autoTap.autoSendAfterType.collectLatest {
                requestTapMarksUiRefresh()
            }
        }

        // Observe Buyer Notification State
        val buyerNotif = com.tajasb.notif.BuyerNotificationManager.getInstance(this)
        buyerNotif.onShowNotification = { _, _, _ ->
            updateBuyerNotifUi()
        }
        buyerNotif.onHideNotification = {
            updateBuyerNotifUi()
        }

        serviceScope.launch { buyerNotif.isEnabled.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.isPositionLocked.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.posX.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.posY.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.displayDurationMs.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.minIntervalSeconds.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.maxIntervalSeconds.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.actionText.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.salesFormat.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.countRange.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.productNoMode.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.totalTriggeredCount.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.isMirrored.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.scale.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.rotation.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.capsuleCornerRadius.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.capsulePaddingH.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { buyerNotif.capsulePaddingV.collectLatest { updateBuyerNotifUi() } }

        // Observe Live Comment & Doa State (Broadcast ke Virtual Camera ditangani langsung oleh LiveCommentManager)
        val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)
        liveComment.onShowComment = null
        liveComment.onHideComment = null

        serviceScope.launch { liveComment.isCaptureActive.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { liveComment.bannerMode.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { liveComment.iconMode.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { liveComment.queueCount.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { liveComment.currentDisplayedItem.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { liveComment.totalCapturedCount.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { liveComment.overlayMirrored.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { liveComment.overlayRotation.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { liveComment.overlayScale.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { liveComment.overlayPosXpct.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { liveComment.overlayPosYpct.collectLatest { updateBuyerNotifUi() } }

        // Observe Custom Stiker State
        val stikerMgr = com.tajasb.stiker.CustomStikerManager.getInstance(this)
        serviceScope.launch { stikerMgr.isEnabled.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { stikerMgr.isMirrored.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { stikerMgr.rotation.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { stikerMgr.sizeScale.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { stikerMgr.displayStyle.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { stikerMgr.isRunningText.collectLatest { updateBuyerNotifUi() } }

        // Observe Custom Watermark State
        val watermarkMgr = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)
        serviceScope.launch { watermarkMgr.isEnabled.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { watermarkMgr.isMirrored.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { watermarkMgr.rotation.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { watermarkMgr.sizeScale.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { watermarkMgr.displayStyle.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { watermarkMgr.isRunningText.collectLatest { updateBuyerNotifUi() } }

        // Observe Auto Time Capsule State ("Waktu Otomatis")
        val autoTimeMgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(this)
        serviceScope.launch { autoTimeMgr.isEnabled.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { autoTimeMgr.showSeconds.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { autoTimeMgr.isMirrored.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { autoTimeMgr.rotation.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { autoTimeMgr.sizeScale.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { autoTimeMgr.posXpct.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { autoTimeMgr.posYpct.collectLatest { updateBuyerNotifUi() } }
        serviceScope.launch { autoTimeMgr.isPositionLocked.collectLatest { updateBuyerNotifUi() } }

        // Observe Real-Time Debug Logs for Floating Log Center
        serviceScope.launch {
            com.tajasb.debug.VcamDebugLogger.logsFlow.collectLatest { logs ->
                updateFloatingLogUi(logs)
            }
        }

        // Observe Comment Scan Recorder State
        val scanRecorder = com.tajasb.livecomment.CommentScanRecorder.getInstance(this)
        serviceScope.launch { scanRecorder.isRecording.collectLatest { updateScanRecorderUi() } }
        serviceScope.launch { scanRecorder.scannedCount.collectLatest { updateScanRecorderUi() } }

        // Observe Comment Scan Inspector State
        val inspector = com.tajasb.livecomment.CommentScanInspector.getInstance(this)
        serviceScope.launch {
            inspector.entriesFlow.collectLatest { entries ->
                if (!isScanInspectorFrozen && (isShowingScanLog || currentTab == FloatingTab.LOG)) {
                    updateScanInspectorFeed(entries)
                }
            }
        }
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                inspector.countAccepted,
                inspector.countRejectedSystem,
                inspector.countRejectedDuplicate,
                inspector.countRejectedOutOfBounds
            ) { acc, sys, dup, out ->
                listOf(acc, sys, dup, out)
            }.collectLatest {
                updateScanInspectorCounters()
            }
        }
        val anchorManager = com.tajasb.livecomment.ViewerAnchorManager.getInstance(this)
        serviceScope.launch {
            anchorManager.viewerAnchor.collectLatest {
                updateScanInspectorCounters()
            }
        }
        serviceScope.launch {
            inspector.isMenuCoveringChat.collectLatest {
                updateScanInspectorCounters()
            }
        }
    }

    internal fun updateScanInspectorCounters() {
        val inspector = com.tajasb.livecomment.CommentScanInspector.getInstance(this)
        scanLogCountAcceptedBadge?.text = "${inspector.countAccepted.value} Diterima"
        scanLogCountSystemBadge?.text = "${inspector.countRejectedSystem.value} Sistem"
        scanLogCountDupBadge?.text = "${inspector.countRejectedDuplicate.value} Duplikat"
        scanLogCountOutBadge?.text = "${inspector.countRejectedOutOfBounds.value} Luar"

        val a11yOk = com.tajasb.VcamAccessibilityService.isAccessibilityEnabled(this)
        scanLogA11yStatusBadge?.apply {
            text = if (a11yOk) "🟢 A11Y AKTIF" else "⚠️ A11Y NONAKTIF"
            setTextColor(if (a11yOk) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
        }

        val lm = com.tajasb.livecomment.LiveCommentManager.getInstance(this)
        scanLogAreaInfoText?.text = if (lm.isAreaFilterEnabled.value) {
            "📐 Kotak Scan: ${(lm.scanTopPct.value * 100).toInt()}% - ${(lm.scanBottomPct.value * 100).toInt()}% (Ubah di Tab Doa)"
        } else {
            "🌐 Seluruh Layar (Tanpa Batasan Kotak)"
        }

        val isCovered = inspector.isMenuCoveringChat.value
        scanLogMenuCoveredBanner?.apply {
            visibility = if (isCovered) View.VISIBLE else View.GONE
            text = "⏸️ SCAN DIJEDA: Menu Produk/Skrip Sedang Terbuka (Chat Tertutup)"
        }

        val anchor = com.tajasb.livecomment.ViewerAnchorManager.getInstance(this).viewerAnchor.value
        scanLogViewerAnchorText?.text = if (anchor != null) {
            "📍 Titik Penonton: X=${(anchor.posXpct * 100).toInt()}% Y=${(anchor.posYpct * 100).toInt()}% (${anchor.rawText} Penonton) • [Tersimpan]"
        } else {
            "📍 Titik Penonton: Mencari di pojok kanan atas..."
        }
    }

    internal fun updateScanInspectorFilterChips() {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        scanLogFilterChipsList.forEach { (filterKey, chipTv) ->
            val isMatched = filterKey.equals(currentScanLogFilter, ignoreCase = true)
            chipTv.apply {
                setTextColor(if (isMatched) Color.BLACK else Color.WHITE)
                setTypeface(null, if (isMatched) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isMatched) {
                        setColor(Color.parseColor("#38BDF8"))
                        setStroke(dp(1), Color.WHITE)
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                        setStroke(dp(1), Color.parseColor("#334155"))
                    }
                }
            }
        }
    }

    internal fun updateScanInspectorFeed(entries: List<com.tajasb.livecomment.ScanInspectionEntry>) {
        val container = scanLogContainerList ?: return
        container.removeAllViews()

        val filtered = when (currentScanLogFilter) {
            "ACCEPTED" -> entries.filter { it.status == com.tajasb.livecomment.ScanInspectionStatus.ACCEPTED }
            "SYSTEM" -> entries.filter { it.status == com.tajasb.livecomment.ScanInspectionStatus.REJECTED_SYSTEM }
            "DUP" -> entries.filter { it.status == com.tajasb.livecomment.ScanInspectionStatus.REJECTED_DUPLICATE }
            "OUT" -> entries.filter { it.status == com.tajasb.livecomment.ScanInspectionStatus.REJECTED_OUT_OF_BOUNDS }
            else -> entries
        }.take(35)

        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = if (entries.isEmpty()) {
                    "(Belum ada log scan. Aksesibilitas akan memindai teks saat live TikTok/Shopee berjalan)"
                } else {
                    "(Tidak ada riwayat untuk filter '$currentScanLogFilter')"
                }
                textSize = 9f
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(16), dp(8), dp(16))
            }
            container.addView(emptyTv)
            return
        }

        for (entry in filtered) {
            val itemCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(Color.parseColor("#0B1526"))
                    setStroke(dp(1), Color.parseColor(entry.status.badgeColorHex))
                }
                setPadding(dp(7), dp(5), dp(7), dp(5))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(4)
                }
                layoutParams = lp
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvTime = TextView(this).apply {
                text = "[${entry.timeFormatted}] "
                textSize = 8.5f
                setTextColor(Color.parseColor("#94A3B8"))
            }
            topRow.addView(tvTime)

            val tvBadge = TextView(this).apply {
                text = entry.status.label
                textSize = 8f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(entry.status.textColorHex))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor(entry.status.badgeColorHex))
                }
                setPadding(dp(4), dp(1), dp(4), dp(1))
            }
            topRow.addView(tvBadge)

            if (entry.spillKeywordMatched != null) {
                val tvSpill = TextView(this).apply {
                    text = "🎯 ${entry.spillKeywordMatched}"
                    textSize = 8f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#FDE047"))
                    setPadding(dp(4), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                topRow.addView(tvSpill)
            }
            itemCard.addView(topRow)

            val tvContent = TextView(this).apply {
                val prefix = if (entry.senderName != "-" && entry.senderName != "Sistem" && entry.senderName != "Layar") "@${entry.senderName}: " else ""
                text = "$prefix\"${entry.content}\""
                textSize = 9.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, dp(2), 0, dp(1))
            }
            itemCard.addView(tvContent)

            val tvReason = TextView(this).apply {
                text = "↳ ${entry.reasonDetail}"
                textSize = 8f
                setTextColor(Color.parseColor("#94A3B8"))
            }
            itemCard.addView(tvReason)

            container.addView(itemCard)
        }
    }

    internal fun updateFloatingLogUi(logs: List<com.tajasb.debug.LogEntry>) {
        val isLogOn = com.tajasb.debug.VcamDebugLogger.isLoggingEnabled.value
        updateLogSwitchUi(isLogOn)
        if (!isLogOn) {
            floatingLogCountBadge?.text = "⚪ SAKLAR OFF"
            floatingLogPreviewText?.text = "(Saklar Log OFF - Pencatatan dinonaktifkan)"
            return
        }
        val total = logs.size
        floatingLogCountBadge?.text = "🟢 $total/100 FIFO"
        val recent = logs.takeLast(50).reversed()
        val sb = StringBuilder()
        try {
            val curVid = repository.config.value.currentVideo?.let { java.io.File(it.path) }
            val diag = com.tajasb.engine.SystemDiagnosticHelper.analyzeRootCause(this, curVid, 30f, isDecoderStalled = false)
            sb.append("🩺 DIAGNOSTIK: ${diag.overallVerdict}\n")
            sb.append("• 📹 Video: ${diag.videoInfo?.statusText ?: "OK"}\n")
            sb.append("• 🌐 Net: ${diag.netInfo.networkType} (${String.format(java.util.Locale.US, "%.1f", diag.netInfo.downstreamMbps)} Mbps)\n")
            sb.append("• 🧠 RAM: Bebas ${diag.ramInfo.availRamMb}MB (${diag.ramInfo.usedRamPercent}%)\n")
            sb.append("─────────────────────────────\n")
        } catch (_: Throwable) {}

        if (recent.isEmpty()) {
            sb.append("(Belum ada log aktivitas baru...)")
        } else {
            recent.forEachIndexed { i, entry ->
                sb.append("[${entry.timeFormatted}] [${entry.level.label}] ${entry.tag}: ${entry.message}")
                if (i < recent.size - 1) sb.append("\n")
            }
        }
        floatingLogPreviewText?.text = sb.toString()
    }

    internal fun updateLogSwitchUi(enabled: Boolean) {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        btnLogSwitch?.apply {
            text = if (enabled) "🟢 Saklar: ON" else "⚪ Saklar: OFF"
            setTextColor(if (enabled) Color.WHITE else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                if (enabled) {
                    setColor(Color.parseColor("#064E3B"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                } else {
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#64748B"))
                }
            }
        }
    }

    internal fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    internal fun launchTargetApp(showChooser: Boolean = false) =
        FloatingBubbleWindow.launchTargetApp(this, showChooser)

    internal fun launchSpecificPackage(pkg: String, label: String) =
        FloatingBubbleWindow.launchSpecificPackage(this, pkg, label)

    internal fun selectTab(tab: FloatingTab) {
        val cfg = repository.config.value
        val anyNotifEnabled = cfg.enableSalesNotifModule || cfg.enableStickerModule || cfg.enableWatermarkModule || cfg.enablePrayerCommentModule || cfg.enableQuranKhatamModule || cfg.enableAutoTimeModule

        val safeTab = when (tab) {
            FloatingTab.MUSIC -> if (cfg.enableMp3MusicModule) tab else FloatingTab.VIDEO
            FloatingTab.TAP -> if (cfg.enableAutoTapModule) tab else FloatingTab.VIDEO
            FloatingTab.NOTIF -> if (anyNotifEnabled) tab else FloatingTab.VIDEO
            else -> tab
        }

        currentTab = safeTab
        setPanelFocusable(false)
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        containerTabVideo?.visibility = if (safeTab == FloatingTab.VIDEO) View.VISIBLE else View.GONE
        containerTabMusic?.visibility = if (safeTab == FloatingTab.MUSIC && cfg.enableMp3MusicModule) View.VISIBLE else View.GONE
        containerTabVisual?.visibility = if (safeTab == FloatingTab.VISUAL) View.VISIBLE else View.GONE
        containerTabNotif?.visibility = if (safeTab == FloatingTab.NOTIF && anyNotifEnabled) View.VISIBLE else View.GONE
        containerTabTap?.visibility = if (safeTab == FloatingTab.TAP && cfg.enableAutoTapModule) View.VISIBLE else View.GONE
        stickyTapActionBar?.visibility = if (safeTab == FloatingTab.TAP && cfg.enableAutoTapModule) View.VISIBLE else View.GONE
        containerTabSettings?.visibility = if (safeTab == FloatingTab.SETTINGS) View.VISIBLE else View.GONE
        containerTabLog?.visibility = if (safeTab == FloatingTab.LOG) View.VISIBLE else View.GONE

        if (safeTab != FloatingTab.LOG && isFloatingLogFullScreen) {
            toggleFloatingLogFullScreen(forceFull = false)
        }

        btnTabMusic?.visibility = if (cfg.enableMp3MusicModule) View.VISIBLE else View.GONE
        btnTabTap?.visibility = if (cfg.enableAutoTapModule) View.VISIBLE else View.GONE
        btnTabNotif?.visibility = if (anyNotifEnabled) View.VISIBLE else View.GONE

        tabContentScrollView?.post {
            tabContentScrollView?.scrollTo(0, 0)
        }

        val isMusicPlaying = repository.musicPlayer.isPlaying.value
        val isVideoActive = !cfg.isVideoPaused && cfg.isMasterEnabled
        val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)
        val buyerNotif = com.tajasb.notif.BuyerNotificationManager.getInstance(this)
        val stikerMgr = com.tajasb.stiker.CustomStikerManager.getInstance(this)
        val watermarkMgr = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)
        val autoTimeMgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(this)
        val scanRec = com.tajasb.livecomment.CommentScanRecorder.getInstance(this)
        val isPinActive = liveComment.isCaptureActive.value || buyerNotif.isEnabled.value || stikerMgr.isEnabled.value || watermarkMgr.isEnabled.value || autoTimeMgr.isEnabled.value || scanRec.isRecording.value
        val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
        val isTapActive = autoTap.isTapping.value
        val isLogActive = com.tajasb.debug.VcamDebugLogger.isLoggingEnabled.value

        fun styleTabBtn(btn: TextView?, active: Boolean, isFeatureRunning: Boolean = false, activeColor: String = "#00E5FF") {
            btn?.apply {
                setTextColor(if (active || isFeatureRunning) Color.WHITE else Color.parseColor("#94A3B8"))
                setTypeface(null, if (active || isFeatureRunning) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    if (active) {
                        val activeBg = when (safeTab) {
                            FloatingTab.MUSIC -> "#3B0764"
                            FloatingTab.NOTIF -> "#431407"
                            FloatingTab.TAP -> "#500724"
                            FloatingTab.LOG -> "#0C4A6E"
                            else -> "#083344"
                        }
                        setColor(Color.parseColor(activeBg))
                        val strokeColor = if (isFeatureRunning) "#10B981" else (if (safeTab == FloatingTab.MUSIC) "#D946EF" else if (safeTab == FloatingTab.NOTIF) "#F97316" else activeColor)
                        setStroke(dp(if (isFeatureRunning) 2 else 1), Color.parseColor(strokeColor))
                    } else if (isFeatureRunning) {
                        setColor(Color.parseColor("#064E3B"))
                        setStroke(dp(2), Color.parseColor("#10B981"))
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                    }
                }
            }
        }

        styleTabBtn(btnTabVideo, safeTab == FloatingTab.VIDEO, isVideoActive, "#00E5FF")
        styleTabBtn(btnTabMusic, safeTab == FloatingTab.MUSIC, isMusicPlaying, "#D946EF")
        styleTabBtn(btnTabVisual, safeTab == FloatingTab.VISUAL, false, "#818CF8")
        styleTabBtn(btnTabNotif, safeTab == FloatingTab.NOTIF, isPinActive, "#F97316")
        styleTabBtn(btnTabTap, safeTab == FloatingTab.TAP, isTapActive, "#EC4899")
        styleTabBtn(btnTabSettings, safeTab == FloatingTab.SETTINGS, false, "#10B981")
        styleTabBtn(btnTabLog, safeTab == FloatingTab.LOG, isLogActive, "#38BDF8")

        if (safeTab == FloatingTab.MUSIC) {
            updateMusicPlayerUi()
        } else if (safeTab == FloatingTab.NOTIF) {
            updateBuyerNotifUi()
        } else if (safeTab == FloatingTab.TAP) {
            updateAutoTapUi()
            requestTapMarksUiRefresh(immediate = true)
        } else if (safeTab == FloatingTab.LOG) {
            updateFloatingLogUi(com.tajasb.debug.VcamDebugLogger.logsFlow.value)
            val insp = com.tajasb.livecomment.CommentScanInspector.getInstance(this)
            updateScanInspectorCounters()
            updateScanInspectorFeed(insp.entriesFlow.value)
            updateScanRecorderUi()
        }
        updateFixedBottomDockUi()
    }

    internal fun toggleFloatingLogFullScreen(forceFull: Boolean? = null) {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        val params = panelLayoutParams ?: return
        val panel = expandedControlPanelView ?: return
        val scroll = tabContentScrollView ?: return

        val nextFull = forceFull ?: !isFloatingLogFullScreen
        if (nextFull == isFloatingLogFullScreen) return

        val dm = resources.displayMetrics
        if (nextFull) {
            savedNormalPanelWidth = params.width
            savedNormalScrollHeight = scroll.layoutParams.height
            savedNormalPanelX = params.x
            savedNormalPanelY = params.y

            isFloatingLogFullScreen = true
            params.width = (dm.widthPixels - dp(8)).coerceAtLeast(dp(280))
            params.x = dp(4)
            params.y = dp(20)
            scroll.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (dm.heightPixels * 0.76f).toInt().coerceAtLeast(dp(320))
            )
        } else {
            isFloatingLogFullScreen = false
            val prefs = getSharedPreferences("vcam_settings", Context.MODE_PRIVATE)
            val fallbackWidth = prefs.getInt("floating_panel_custom_width", dp(290))
                .coerceIn(dp(240), dm.widthPixels)
            val fallbackHeight = prefs.getInt("floating_panel_custom_scroll_height", dp(250))
                .coerceIn(dp(140), (dm.heightPixels * 0.8f).toInt())

            params.width = if (savedNormalPanelWidth > 0) savedNormalPanelWidth else fallbackWidth
            params.x = savedNormalPanelX
            params.y = savedNormalPanelY
            scroll.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (savedNormalScrollHeight > 0) savedNormalScrollHeight else fallbackHeight
            )
        }

        btnToggleFloatingLogFullScreen?.apply {
            text = if (isFloatingLogFullScreen) "🗗 Normal" else "⛶ Full Layar"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (isFloatingLogFullScreen) {
                    setColor(Color.parseColor("#7C2D12"))
                    setStroke(dp(1), Color.parseColor("#F97316"))
                } else {
                    setColor(Color.parseColor("#083344"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                }
            }
        }

        scroll.requestLayout()
        panel.requestLayout()
        if (panel.windowToken != null) {
            try {
                windowManager.updateViewLayout(panel, params)
            } catch (_: Exception) {}
        }
    }

    private fun requestTapTapUiAndMarksRefresh() {
        updateAutoTapUi()
        requestTapMarksUiRefresh()
    }

    internal fun updateModuleSwitchesUi(cfg: VcamConfig) {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        fun applySwitchStyle(btn: TextView?, label: String, icon: String, isEnabled: Boolean) {
            btn?.apply {
                val statusBadge = if (isEnabled) "[TAMPIL 🟢]" else "[SEMBUNYI 🔴]"
                text = "$icon $label: $statusBadge"
                setTextColor(if (isEnabled) Color.WHITE else Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    if (isEnabled) {
                        setColor(Color.parseColor("#064E3B"))
                        setStroke(dp(1), Color.parseColor("#10B981"))
                    } else {
                        setColor(Color.parseColor("#1F2937"))
                        setStroke(dp(1), Color.parseColor("#475569"))
                    }
                }
            }
        }

        applySwitchStyle(btnModuleSalesToggle, "Modul Notif Jualan", "🛒", cfg.enableSalesNotifModule)
        applySwitchStyle(btnModuleStickerToggle, "Modul Stiker Tulisan", "🏷️", cfg.enableStickerModule)
        applySwitchStyle(btnModuleWatermarkToggle, "Modul Watermark", "💧", cfg.enableWatermarkModule)
        applySwitchStyle(btnModulePrayerToggle, "Modul Doa Komentar Live", "🤲", cfg.enablePrayerCommentModule)
        applySwitchStyle(btnModuleQuranKhatamToggle, "Modul Khatam Al-Qur'an Live", "🕌", cfg.enableQuranKhatamModule)
        applySwitchStyle(btnModuleTapToggle, "Modul Auto-Tap", "👆", cfg.enableAutoTapModule)
        applySwitchStyle(btnModuleMusicToggle, "Modul Musik MP3 Ekstra", "🎵", cfg.enableMp3MusicModule)
        applySwitchStyle(btnModuleOsdToggle, "Modul OSD Jam/Detik MS", "⏱️", cfg.enableOsdModule)
        applySwitchStyle(btnModuleAutoTimeToggle, "Modul Waktu Otomatis", "⏰", cfg.enableAutoTimeModule)
        applySwitchStyle(btnModuleDnaToggle, "Modul Mutator DNA Sesi", "🧬", cfg.enableDnaModule)
        applySwitchStyle(btnModuleAiDetectionToggle, "Modul Hapus Kode AI", "🧹", cfg.enableAiDetectionModule)
        applySwitchStyle(btnModuleQuranAudioToggle, "Modul Audio Al-Qur'an", "📖", cfg.enableQuranAudioModule)
        applySwitchStyle(btnModuleMakkahRelayToggle, "Modul Relay Mekah Live", "🕋", cfg.enableMakkahRelayModule)
        applySwitchStyle(btnModuleLiveEventBrokerToggle, "Modul TikTok Event Broker", "⚡", cfg.enableLiveEventBrokerModule)
        applySwitchStyle(btnModuleScanInspectorToggle, "Modul Inspeksi / Log Scan", "🔍", cfg.enableScanInspectorModule)
    }

    internal fun updateTabVisibilityAndSwitches(cfg: VcamConfig) {
        val anyNotifEnabled = cfg.enableSalesNotifModule || cfg.enableStickerModule || cfg.enableWatermarkModule || cfg.enablePrayerCommentModule || cfg.enableQuranKhatamModule || cfg.enableAutoTimeModule

        antiDetCardView?.visibility = View.VISIBLE
        audAntiHashRowView?.visibility = View.VISIBLE
        btnSessionDnaQuickView?.visibility = if (cfg.enableDnaModule) View.VISIBLE else View.GONE
        videoAiQuickRow?.visibility = if (cfg.enableAiDetectionModule) View.VISIBLE else View.GONE
        aiWatermarkCardView?.visibility = if (cfg.enableAiDetectionModule) View.VISIBLE else View.GONE

        btnTabMusic?.visibility = if (cfg.enableMp3MusicModule) View.VISIBLE else View.GONE
        btnTabTap?.visibility = if (cfg.enableAutoTapModule) View.VISIBLE else View.GONE
        btnTabNotif?.visibility = if (anyNotifEnabled) View.VISIBLE else View.GONE

        if (currentTab == FloatingTab.MUSIC && !cfg.enableMp3MusicModule) {
            selectTab(FloatingTab.SETTINGS)
        } else if (currentTab == FloatingTab.TAP && !cfg.enableAutoTapModule) {
            selectTab(FloatingTab.SETTINGS)
        } else if (currentTab == FloatingTab.NOTIF && !anyNotifEnabled) {
            selectTab(FloatingTab.SETTINGS)
        }

        btnModeSalesTab?.visibility = if (cfg.enableSalesNotifModule) View.VISIBLE else View.GONE
        btnModeStikerTab?.visibility = if (cfg.enableStickerModule) View.VISIBLE else View.GONE
        btnModeWatermarkTab?.visibility = if (cfg.enableWatermarkModule) View.VISIBLE else View.GONE
        btnModeDoaTab?.visibility = if (cfg.enablePrayerCommentModule) View.VISIBLE else View.GONE
        btnModeQuranKhatamTab?.visibility = if (cfg.enableQuranKhatamModule) View.VISIBLE else View.GONE
        btnModeAutoTimeTab?.visibility = if (cfg.enableAutoTimeModule) View.VISIBLE else View.GONE

        btnOsdSettings?.visibility = if (cfg.enableOsdModule) View.VISIBLE else View.GONE

        val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)
        val curBanner = liveComment.bannerMode.value
        val isCurrentBannerDisabled = when (curBanner) {
            com.tajasb.livecomment.BannerMode.BUYER_SALES -> !cfg.enableSalesNotifModule
            com.tajasb.livecomment.BannerMode.STICKER_TEXT -> !cfg.enableStickerModule
            com.tajasb.livecomment.BannerMode.WATERMARK_TEXT -> !cfg.enableWatermarkModule
            com.tajasb.livecomment.BannerMode.LIVE_DOA_COMMENT -> !cfg.enablePrayerCommentModule
            com.tajasb.livecomment.BannerMode.QURAN_KHATAM -> !cfg.enableQuranKhatamModule
            com.tajasb.livecomment.BannerMode.AUTO_TIME -> !cfg.enableAutoTimeModule
            com.tajasb.livecomment.BannerMode.TUG_OF_WAR -> false
        }
        if (isCurrentBannerDisabled) {
            val nextMode = when {
                cfg.enableQuranKhatamModule -> com.tajasb.livecomment.BannerMode.QURAN_KHATAM
                cfg.enableSalesNotifModule -> com.tajasb.livecomment.BannerMode.BUYER_SALES
                cfg.enableStickerModule -> com.tajasb.livecomment.BannerMode.STICKER_TEXT
                cfg.enableWatermarkModule -> com.tajasb.livecomment.BannerMode.WATERMARK_TEXT
                cfg.enableAutoTimeModule -> com.tajasb.livecomment.BannerMode.AUTO_TIME
                cfg.enablePrayerCommentModule -> com.tajasb.livecomment.BannerMode.LIVE_DOA_COMMENT
                else -> null
            }
            if (nextMode != null) liveComment.setBannerMode(nextMode)
        }

        if (!cfg.enableAutoTapModule) {
            try {
                val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
                if (autoTap.isTapping.value) autoTap.stopTapping()
                if (autoTap.isPointerVisible.value) autoTap.setPointerVisible(false)
            } catch (_: Exception) {}
            removeCrosshairPointer()
        }

        updateBuyerNotifUi()
        updateModuleSwitchesUi(cfg)
        updateBubbleIconsStatus()
    }

    internal fun updateMusicPlayerUi() {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val isPlaying = repository.musicPlayer.isPlaying.value
        val track = repository.musicPlayer.currentTrack.value ?: repository.config.value.currentAudio
        val isLooping = repository.musicPlayer.isLooping.value
        val trackTitle = cleanDisplayName(track?.name ?: repository.config.value.audioName.ifBlank { "Belum ada lagu dipilih" })

        val isWaitingForMute = repository.isMusicAutoPausedWaitingForMute.value && !repository.config.value.isDualAudioMixingEnabled

        musicTrackTitleText?.text = trackTitle
        musicStatusBadge?.apply {
            if (isPlaying) {
                text = "SEDANG DIPUTAR"
                setTextColor(Color.parseColor("#10B981"))
            } else if (isWaitingForMute) {
                text = "JEDA OTOMATIS (VIDEO BERSUARA)"
                setTextColor(Color.parseColor("#F59E0B"))
            } else {
                text = "DIJEDA"
                setTextColor(Color.parseColor("#F59E0B"))
            }
        }

        btnMusicPlayPauseText?.apply {
            text = if (isPlaying) " ⏸️ Jeda " else " ▶️ Putar "
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                if (isPlaying) {
                    setColor(Color.parseColor("#4C1D95"))
                    setStroke(dp(1), Color.parseColor("#D946EF"))
                } else {
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#A855F7"))
                }
            }
        }

        val currentAudioMode = try {
            com.tajasb.infinity.IfsManager.getInstance(this@FloatingControlService).audioMode.value
        } catch (_: Throwable) {
            if (isLooping) com.tajasb.infinity.IfsAudioMode.SINGLE_LOOP else com.tajasb.infinity.IfsAudioMode.SEQUENTIAL
        }

        btnMusicLoopText?.apply {
            text = currentAudioMode.badge
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                when (currentAudioMode) {
                    com.tajasb.infinity.IfsAudioMode.SEQUENTIAL -> {
                        setColor(Color.parseColor("#064E3B"))
                        setStroke(dp(1), Color.parseColor("#10B981"))
                    }
                    com.tajasb.infinity.IfsAudioMode.SR_AX -> {
                        setColor(Color.parseColor("#083344"))
                        setStroke(dp(1), Color.parseColor("#06B6D4"))
                    }
                    com.tajasb.infinity.IfsAudioMode.SINGLE_LOOP -> {
                        setColor(Color.parseColor("#78350F"))
                        setStroke(dp(1), Color.parseColor("#F59E0B"))
                    }
                    com.tajasb.infinity.IfsAudioMode.SV4_VS -> {
                        setColor(Color.parseColor("#3B0764"))
                        setStroke(dp(1), Color.parseColor("#D946EF"))
                    }
                }
            }
        }

        musicModeBtnMap.forEach { (modeOpt, btn) ->
            val selected = (modeOpt == currentAudioMode)
            btn.setTextColor(if (selected) Color.WHITE else Color.parseColor("#94A3B8"))
            btn.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                if (selected) {
                    when (modeOpt) {
                        com.tajasb.infinity.IfsAudioMode.SEQUENTIAL -> {
                            setColor(Color.parseColor("#064E3B"))
                            setStroke(dp(1), Color.parseColor("#10B981"))
                        }
                        com.tajasb.infinity.IfsAudioMode.SR_AX -> {
                            setColor(Color.parseColor("#083344"))
                            setStroke(dp(1), Color.parseColor("#06B6D4"))
                        }
                        com.tajasb.infinity.IfsAudioMode.SINGLE_LOOP -> {
                            setColor(Color.parseColor("#78350F"))
                            setStroke(dp(1), Color.parseColor("#F59E0B"))
                        }
                        com.tajasb.infinity.IfsAudioMode.SV4_VS -> {
                            setColor(Color.parseColor("#3B0764"))
                            setStroke(dp(1), Color.parseColor("#D946EF"))
                        }
                    }
                } else {
                    setColor(Color.parseColor("#0F172A"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
            }
        }

        val curVoiceEffect = repository.config.value.voiceEffect
        musicVoiceEffectBtnMap.forEach { (eff, btn) ->
            val selected = (eff == curVoiceEffect)
            btn.setTextColor(if (selected) Color.WHITE else Color.parseColor("#94A3B8"))
            btn.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                if (selected) {
                    setColor(Color.parseColor("#31104F"))
                    setStroke(dp(1), Color.parseColor("#D946EF"))
                } else {
                    setColor(Color.parseColor("#0F172A"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
            }
        }

        val isL2On = repository.config.value.isAudioLayer2Enabled
        val isL2Playing = repository.musicPlayer.isPlayingLayer2.value
        val isL1Playing = repository.musicPlayer.isPlaying.value
        val l2Track = cleanDisplayName(
            repository.musicPlayer.currentTrackLayer2.value?.name
                ?: repository.config.value.currentAudioLayer2?.name
                ?: "Audio Tipe 2"
        )
        btnToggleAudioLayer2Text?.apply {
            text = if (isL2On) {
                if (isL2Playing) "🎧 Tipe 2: PLAY ▶ ($l2Track)"
                else "🎧 Tipe 2: JEDA ⏸ ($l2Track)"
            } else {
                "🎧 Tipe 2: NONAKTIF"
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                if (isL2On) {
                    setColor(Color.parseColor("#064E3B"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                } else {
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
            }
            setTextColor(if (isL2On) Color.parseColor("#34D399") else Color.parseColor("#94A3B8"))
        }

        btnAudioLayer2PlayPauseText?.apply {
            text = if (isL2Playing) "⏸️" else "▶️"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                if (isL2Playing) {
                    setColor(Color.parseColor("#065F46"))
                    setStroke(dp(1), Color.parseColor("#34D399"))
                } else {
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
            }
        }
        val l2Vol = (repository.musicPlayer.volumeLayer2.value * 100).toInt()
        audioLayer2VolumeLabel?.text = "🔊 $l2Vol%"

        // Update Layer 3 (Ringtone Acak Dinamis) UI
        val isL3On = repository.config.value.isAudioLayer3Enabled || repository.musicPlayer.isLayer3Active.value
        val isL3Playing = repository.musicPlayer.isPlayingLayer3.value
        val l3Track = cleanDisplayName(
            repository.musicPlayer.currentTrackLayer3.value?.name ?: "SFX / Ringtone"
        )
        val l3Eta = repository.musicPlayer.nextRingtoneEtaSec.value
        val hasRingtones = repository.config.value.audioLayer3Ids.isNotEmpty()
        btnToggleAudioLayer3Text?.apply {
            text = if (isL3On) {
                if (isL3Playing) "🔔 Tipe 3: 🔊 BUNYI ($l3Track)"
                else if (!hasRingtones) "🔔 Tipe 3: PILIH SFX DULU"
                else if (l3Eta != null && l3Eta > 0) "🔔 Tipe 3: AKTIF (⏳ ${l3Eta}s)"
                else "🔔 Tipe 3: AKTIF (SFX)"
            } else {
                "🔔 Tipe 3: NONAKTIF"
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                if (isL3On) {
                    setColor(Color.parseColor("#431407"))
                    setStroke(dp(1), Color.parseColor("#EA580C"))
                } else {
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
            }
            setTextColor(if (isL3On) Color.parseColor("#FB923C") else Color.parseColor("#94A3B8"))
        }

        btnTriggerTestLayer3Text?.apply {
            text = if (isL3Playing) "🔊" else "⚡ Tes"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                if (isL3Playing) {
                    setColor(Color.parseColor("#065F46"))
                    setStroke(dp(1), Color.parseColor("#34D399"))
                } else {
                    setColor(Color.parseColor("#7C2D12"))
                    setStroke(dp(1), Color.parseColor("#EA580C"))
                }
            }
        }
        val l3Vol = (repository.musicPlayer.volumeLayer3.value * 100).toInt()
        audioLayer3VolumeLabel?.text = "🔔 $l3Vol%"

        val totalAud = repository.config.value.audioPlaylist.size
        quickAudioListToggleBtn?.text = if (isQuickAudioListOpen) "📋 Daftar ($totalAud) ▴" else "📋 Daftar ($totalAud) ▾"

        buildQuickAudioListItems(repository.config.value.audioPlaylist, repository.config.value.currentAudioIndex)
        updateBubbleIconsStatus()
    }

    internal fun updateBubbleIconsStatus() =
        FloatingBubbleWindow.updateBubbleIconsStatus(this)

    internal fun cleanDisplayName(raw: String): String {
        if (raw.isBlank()) return "virtual.mp4"
        var clean = if (raw.contains("/")) raw.substringAfterLast("/") else raw
        val uuidPrefixRegex = Regex("^(video|audio)_[a-fA-F0-9]{8}_")
        clean = clean.replace(uuidPrefixRegex, "")
        return clean
    }

    internal fun navigateNextAudio() {
        val playlist = repository.config.value.audioPlaylist
        if (playlist.isEmpty()) return
        serviceScope.launch {
            try {
                val switched = repository.nextAudio()
                if (switched != null) {
                    val newIdx = repository.config.value.currentAudioIndex
                    val trackName = cleanDisplayName(switched.name)
                    musicTrackTitleText?.text = trackName
                    musicStatusBadge?.apply {
                        text = "AKTIF"
                        setTextColor(Color.parseColor("#10B981"))
                    }
                    buildQuickAudioListItems(repository.config.value.audioPlaylist, newIdx)
                    showVipOnlyToast("🎵 ${cleanDisplayName(switched.name)}", Toast.LENGTH_SHORT)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    internal fun navigatePrevAudio() {
        val playlist = repository.config.value.audioPlaylist
        if (playlist.isEmpty()) return
        serviceScope.launch {
            try {
                val switched = repository.prevAudio()
                if (switched != null) {
                    val newIdx = repository.config.value.currentAudioIndex
                    val trackName = cleanDisplayName(switched.name)
                    musicTrackTitleText?.text = trackName
                    musicStatusBadge?.apply {
                        text = "AKTIF"
                        setTextColor(Color.parseColor("#10B981"))
                    }
                    buildQuickAudioListItems(repository.config.value.audioPlaylist, newIdx)
                    showVipOnlyToast("🎵 ${cleanDisplayName(switched.name)}", Toast.LENGTH_SHORT)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    internal fun selectAudioItem(index: Int) {
        val playlist = repository.config.value.audioPlaylist
        if (playlist.isEmpty() || index !in playlist.indices) return
        val item = playlist[index]

        val trackName = cleanDisplayName(item.name)
        musicTrackTitleText?.text = trackName
        musicStatusBadge?.apply {
            text = "SEDANG DIPUTAR"
            setTextColor(Color.parseColor("#10B981"))
        }
        buildQuickAudioListItems(playlist, index)

        serviceScope.launch {
            try {
                val selected = repository.selectAudioByIndex(index, autoPlay = true)
                if (selected != null) {
                    showVipOnlyToast("Memutar lagu: ${cleanDisplayName(selected.name)}", Toast.LENGTH_SHORT)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    internal fun navigateNextVideo() {
        val playlist = repository.config.value.playlist
        if (playlist.isEmpty()) return
        val isInstant = (repository.config.value.videoSwitchMode == com.tajasb.model.VideoSwitchMode.INSTANT)

        serviceScope.launch {
            try {
                if (isInstant) {
                    currentVideoPositionMs = 0L
                    val shouldForceSeq = (repository.config.value.autoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP)
                    val switched = repository.nextVideo(forceSequential = shouldForceSeq)
                    if (switched != null) {
                        showVipOnlyToast("⚡ Video: ${cleanDisplayName(switched.name)}", Toast.LENGTH_SHORT)
                    }
                } else {
                    val queued = repository.queueNextVideo()
                    if (queued != null) {
                        showVipOnlyToast(
                            "⏳ Video (${cleanDisplayName(queued.name)}) MASUK ANTREAN!\nMenunggu video saat ini selesai.",
                            Toast.LENGTH_SHORT
                        )
                        buildQuickVideoListItems(playlist, repository.config.value.currentVideoIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    internal fun navigatePrevVideo() {
        val playlist = repository.config.value.playlist
        if (playlist.isEmpty()) return
        val isInstant = (repository.config.value.videoSwitchMode == com.tajasb.model.VideoSwitchMode.INSTANT)

        serviceScope.launch {
            try {
                if (isInstant) {
                    currentVideoPositionMs = 0L
                    val switched = repository.prevVideo()
                    if (switched != null) {
                        showVipOnlyToast("⚡ Video: ${cleanDisplayName(switched.name)}", Toast.LENGTH_SHORT)
                    }
                } else {
                    val queued = repository.queuePrevVideo()
                    if (queued != null) {
                        showVipOnlyToast(
                            "⏳ Video (${cleanDisplayName(queued.name)}) MASUK ANTREAN!\nMenunggu video saat ini selesai.",
                            Toast.LENGTH_SHORT
                        )
                        buildQuickVideoListItems(playlist, repository.config.value.currentVideoIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    internal fun selectVideoItem(index: Int, immediate: Boolean? = null) {
        val cfg = repository.config.value
        val playlist = cfg.playlist
        if (playlist.isEmpty() || index !in playlist.indices) return
        val item = playlist[index]
        val totalVideos = playlist.size
        val displayIndex = index + 1
        val videoName = cleanDisplayName(item.name)
        val shouldImmediate = immediate ?: (cfg.videoSwitchMode == com.tajasb.model.VideoSwitchMode.INSTANT)

        if (shouldImmediate) {
            currentVideoPositionMs = 0L
            panelTitleStatus?.text = "[$displayIndex/$totalVideos] $videoName"
            videoStatusText?.text = "🎬 [$displayIndex/$totalVideos] $videoName"
            bubbleTextBadge?.text = "VCAM [$displayIndex/$totalVideos]"
            buildQuickVideoListItems(playlist, index)

            serviceScope.launch {
                try {
                    val selected = repository.selectVideoByIndex(index)
                    if (selected != null) {
                        showVipOnlyToast("⚡ Langsung beralih ke: ${cleanDisplayName(selected.name)}", Toast.LENGTH_SHORT)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            serviceScope.launch {
                try {
                    val queued = repository.queueCustomVideo(index)
                    if (queued != null) {
                        showVipOnlyToast(
                            "⏳ Video #${displayIndex} (${videoName}) MASUK ANTREAN!\nVideo saat ini diselesaikan dulu agar tidak bentrok.",
                            Toast.LENGTH_LONG
                        )
                        buildQuickVideoListItems(playlist, repository.config.value.currentVideoIndex)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    internal fun updateUiForConfig(cfg: VcamConfig) {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val rotation = cfg.rotation
        val isMirrored = cfg.isMirrored
        val isVerticalFlipped = cfg.isVerticalFlipped
        val isVideoPaused = cfg.isVideoPaused
        val isVideoAudioMuted = cfg.isVideoAudioMuted
        val floatingAlpha = cfg.floatingAlpha.coerceIn(0.50f, 1.0f)
        val scaleMode = cfg.scaleMode
        val playlist = cfg.playlist
        val currentIdx = cfg.currentVideoIndex
        val totalVideos = playlist.size.coerceAtLeast(1)
        val displayIndex = if (playlist.isNotEmpty()) (currentIdx.coerceIn(0, playlist.size - 1) + 1) else 1

        expandedControlPanelView?.alpha = floatingAlpha
        panelLayoutParams?.alpha = floatingAlpha
        if (expandedControlPanelView?.windowToken != null && panelLayoutParams != null) {
            try {
                windowManager.updateViewLayout(expandedControlPanelView, panelLayoutParams)
            } catch (_: Exception) {}
        }
        val alphaPct = (floatingAlpha * 100).toInt().coerceIn(50, 100)
        transparencySeekBar?.progress = alphaPct
        transparencySeekBar?.isEnabled = true
        transparencyLabel?.text = "Transparansi Menu: $alphaPct% (Batas Min: 50%)"

        val isMusicPlaying = repository.musicPlayer.isPlaying.value
        val musicIcon = if (isMusicPlaying) " 🎵▶" else ""
        bubbleTextBadge?.text = "VCAM [$displayIndex/$totalVideos]$musicIcon"

        val safeIdx = if (playlist.isNotEmpty()) currentIdx.coerceIn(0, playlist.size - 1) else 0
        val activeItem = if (playlist.isNotEmpty() && safeIdx in playlist.indices) playlist[safeIdx] else cfg.currentVideo
        val rawName = activeItem?.name?.ifBlank { null } ?: activeItem?.path ?: cfg.videoPath
        val videoName = cleanDisplayName(rawName)
        val pIdx = cfg.pendingVideoIndex
        val pendingStatus = if (pIdx != null && pIdx in playlist.indices) {
            " ⏳ Next: #${pIdx + 1}"
        } else ""
        val isLiveRelay = cfg.isLiveStreamSourceActive
        if (isLiveRelay) {
            panelTitleStatus?.text = "🕋 [LIVE RELAY] Mekah 24 Jam"
            videoStatusText?.text = "🕋 [LIVE RELAY] ${cfg.liveStreamTitle} 🟢 ▾"
        } else {
            panelTitleStatus?.text = "[$displayIndex/$totalVideos] $videoName$pendingStatus"
            videoStatusText?.text = "🎬 [$displayIndex/$totalVideos] $videoName$pendingStatus ▾"
        }
        buildQuickVideoListItems(playlist, safeIdx)

        // 0. Update Makkah Live Relay Floating Card UI
        cardMakkahLiveRelay?.visibility = if (cfg.enableMakkahRelayModule) View.VISIBLE else View.GONE
        makkahControlsContainer?.visibility = if (isLiveRelay) View.VISIBLE else View.GONE
        cardMakkahLiveRelay?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            if (isLiveRelay) {
                setColor(Color.parseColor("#061D15"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            } else {
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
        }
        tvMakkahRelayStatus?.apply {
            text = if (isLiveRelay) "🟢 RELAY AKTIF" else "⚪ STANDBY"
            setTextColor(if (isLiveRelay) Color.parseColor("#10B981") else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isLiveRelay) Color.parseColor("#064E3B") else Color.parseColor("#1E293B"))
            }
        }
        btnToggleMakkahRelay?.apply {
            text = if (isLiveRelay) "🟢 ON" else "⚪ OFF"
            setTextColor(if (isLiveRelay) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(if (isLiveRelay) Color.parseColor("#10B981") else Color.parseColor("#334155"))
            }
        }

        fun updateStreamBtn(btn: TextView?, active: Boolean) {
            btn?.apply {
                setTextColor(if (active) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (active) {
                        setColor(Color.parseColor("#10B981"))
                        setStroke(dp(1), Color.parseColor("#34D399"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#334155"))
                    }
                }
            }
        }
        updateStreamBtn(btnMakkahStream1, cfg.liveStreamUrl.contains("quran/playlist"))
        updateStreamBtn(btnMakkahStream2, cfg.liveStreamUrl.contains("akamaized"))
        updateStreamBtn(btnMakkahStream3, cfg.liveStreamUrl.contains("sunnah"))

        fun updateScalePresetBtn(btn: TextView?, active: Boolean) {
            btn?.apply {
                setTextColor(if (active) Color.BLACK else Color.parseColor("#E2E8F0"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (active) {
                        setColor(Color.parseColor("#38BDF8"))
                        setStroke(dp(1), Color.parseColor("#7DD3FC"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#334155"))
                    }
                }
            }
        }
        updateScalePresetBtn(btnMakkahScaleCrop, cfg.scaleMode == com.tajasb.model.ScaleMode.CROP)
        updateScalePresetBtn(btnMakkahScaleFit, cfg.scaleMode == com.tajasb.model.ScaleMode.FIT)
        updateScalePresetBtn(btnMakkahScaleStretch, cfg.scaleMode == com.tajasb.model.ScaleMode.STRETCH)

        tvMakkahZoomLabel?.text = "🔍 Zoom: ${String.format(java.util.Locale.US, "%.2fx", cfg.zoomFactor)}"
        tvMakkahPanLabel?.text = "🎯 Pan X: ${String.format(java.util.Locale.US, "%.2f", cfg.sessionDnaPanX)} | Y: ${String.format(java.util.Locale.US, "%.2f", cfg.sessionDnaPanY)}"
        tvMakkahRotationLabel?.text = "🔄 ${cfg.rotation}°"
        tvMakkahTelemetryInfo?.text = if (isLiveRelay) {
            "📡 ${cfg.liveStreamTitle}\n💡 Tip: Kaaba Fokus otomatis memotong 16:9 jadi 9:16 alami tanpa gepeng!"
        } else {
            "💡 Ketuk tombol 🟢 ON untuk menyiarkan live stream Mekah langsung ke TikTok."
        }

        updateLiveEventBrokerFloatingUi()

        val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(applicationContext) } catch (_: Throwable) { null }
        val isIfsMode = ifsMgr?.isIfsActive?.value == true || cfg.isIfsMode || cfg.isBridgeCycleMode
        val masterIdx = repository.getMasterVideoIndex()
        val isMasterPlaying = (safeIdx == masterIdx)
        val isSpillMode = cfg.isMasterSpillMode && !isIfsMode
        val isStandardMode = !isIfsMode && !isSpillMode
        val isSpillPlaying = isSpillMode && !isMasterPlaying

        // 1. Update 3 Pill Mode Sejajar (⚪ Standar | 👑 Spill | ♾️ Infinity)
        btnModeStandardVideo?.apply {
            text = "⚪ Standar"
            setTextColor(if (isStandardMode) Color.parseColor("#00E5FF") else Color.parseColor("#94A3B8"))
            setTypeface(null, if (isStandardMode) Typeface.BOLD else Typeface.NORMAL)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (isStandardMode) {
                    setColor(Color.parseColor("#083344"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                } else {
                    setColor(Color.parseColor("#0F172A"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
            }
        }

        btnToggleMasterSpillText?.apply {
            text = "👑 Spill"
            setTextColor(if (isSpillMode) Color.parseColor("#FCD34D") else Color.parseColor("#94A3B8"))
            setTypeface(null, if (isSpillMode) Typeface.BOLD else Typeface.NORMAL)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (isSpillMode) {
                    setColor(Color.parseColor("#78350F"))
                    setStroke(dp(1), Color.parseColor("#F59E0B"))
                } else {
                    setColor(Color.parseColor("#0F172A"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
            }
        }

        btnToggleBridgeCycleText?.apply {
            text = "♾️ Infinity"
            setTextColor(if (isIfsMode) Color.parseColor("#E879F9") else Color.parseColor("#94A3B8"))
            setTypeface(null, if (isIfsMode) Typeface.BOLD else Typeface.NORMAL)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (isIfsMode) {
                    setColor(Color.parseColor("#4C1D95"))
                    setStroke(dp(1), Color.parseColor("#A855F7"))
                } else {
                    setColor(Color.parseColor("#0F172A"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
            }
        }

        // 2. Tampilkan HANYA Container Mode yang Sedang Aktif (Tidak Campur Aduk!)
        containerVideoModeStandard?.visibility = if (isStandardMode) View.VISIBLE else View.GONE
        containerVideoModeSpill?.visibility = if (isSpillMode) View.VISIBLE else View.GONE
        containerVideoModeIfs?.visibility = if (isIfsMode) View.VISIBLE else View.GONE

        // 3. Update Status & Kontrol khusus Mode Spill
        tvMasterSpillStatusBanner?.apply {
            val masterItem = playlist.getOrNull(masterIdx)
            val masterName = masterItem?.let { cleanDisplayName(it.name) } ?: "Master"
            val shortMaster = if (masterName.length > 14) masterName.take(12) + ".." else masterName
            text = when {
                pIdx != null && pIdx in playlist.indices -> {
                    val pName = cleanDisplayName(playlist[pIdx].name)
                    "⏳ Antre Spill: $pName"
                }
                isSpillPlaying -> {
                    "🎯 Spill Aktif: $videoName"
                }
                else -> {
                    "👑 Master Loop: $shortMaster"
                }
            }
            setTextColor(
                when {
                    pIdx != null -> Color.parseColor("#FCD34D")
                    isSpillPlaying -> Color.parseColor("#00E5FF")
                    else -> Color.parseColor("#FDE68A")
                }
            )
        }

        btnSpillSwitchModeText?.apply {
            val waitFinish = cfg.masterSpillWaitFinish
            text = if (waitFinish) "⏳ Tunggu" else "⚡ Instan"
            setTextColor(if (waitFinish) Color.parseColor("#FDE68A") else Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                if (waitFinish) {
                    setColor(Color.parseColor("#451A03"))
                    setStroke(dp(1), Color.parseColor("#F59E0B"))
                } else {
                    setColor(Color.parseColor("#083344"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                }
            }
        }

        val isAutoComment = cfg.autoSpillFromComments
        btnToggleAutoCommentSpillText?.apply {
            text = if (isAutoComment) "💬 Chat: ON" else "💬 Chat: OFF"
            setTextColor(if (isAutoComment) Color.parseColor("#6EE7B7") else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                if (isAutoComment) {
                    setColor(Color.parseColor("#065F46"))
                    setStroke(dp(1), Color.parseColor("#34D399"))
                } else {
                    setColor(Color.parseColor("#1F2937"))
                    setStroke(dp(1), Color.parseColor("#4B5563"))
                }
            }
        }

        btnReturnMasterQuick?.apply {
            text = "↩ Master"
            setTextColor(Color.parseColor("#FCD34D"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#451A03"))
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
        }

        val isScanLogActive = com.tajasb.livecomment.LiveCommentManager.getInstance(this).isCaptureActive.value
        spillScanLogButton?.visibility = if (isScanLogActive) View.VISIBLE else View.GONE

        if (isSpillMode) {
            populateQuickSpillChips(playlist, safeIdx)
        } else {
            quickSpillChipsContainer?.removeAllViews()
        }

        btnPlayPauseVideoText?.apply {
            text = if (isVideoPaused) "▶️" else "⏸️"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (isVideoPaused) {
                    setColor(Color.parseColor("#450A0A"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#064E3B"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                }
            }
        }

        val allVideosClosed = playlist.isNotEmpty() && playlist.all { !it.isEnabled }
        btnToggleAllVideosEyeText?.apply {
            text = if (allVideosClosed) "🙈" else "👁️"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (allVideosClosed) {
                    setColor(Color.parseColor("#450A0A"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#1E1B4B"))
                    setStroke(dp(1), Color.parseColor("#818CF8"))
                }
            }
        }

        val allVideosMuted = if (playlist.isNotEmpty()) playlist.all { it.isMuted } else isVideoAudioMuted
        btnVideoAudioMuteText?.apply {
            text = if (allVideosMuted) "🔇" else "🔊"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (allVideosMuted) {
                    setColor(Color.parseColor("#450A0A"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#083344"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                }
            }
        }

        val audioPlaylist = cfg.audioPlaylist
        val allAudioClosed = audioPlaylist.isNotEmpty() && audioPlaylist.all { !it.isEnabled }
        btnToggleAllAudioEyeText?.apply {
            text = if (allAudioClosed) "🙈" else "👁️"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (allAudioClosed) {
                    setColor(Color.parseColor("#450A0A"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#1E1B4B"))
                    setStroke(dp(1), Color.parseColor("#818CF8"))
                }
            }
        }

        val allAudioMuted = if (audioPlaylist.isNotEmpty()) audioPlaylist.all { it.isMuted } else cfg.isMp3AudioMuted
        btnAudioMuteAllText?.apply {
            text = if (allAudioMuted) "🔇" else "🔊"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (allAudioMuted) {
                    setColor(Color.parseColor("#450A0A"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#083344"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                }
            }
        }

        val isIfs = try { com.tajasb.infinity.IfsManager.getInstance(applicationContext).isIfsActive.value } catch (_: Throwable) { false } || cfg.isIfsMode
        val isSingleLoop = !isIfs && ((cfg.autoNextEnabled && cfg.autoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP) || !cfg.autoNextEnabled)
        val isSeq = !isIfs && cfg.autoNextEnabled && cfg.autoNextMode == com.tajasb.model.AutoNextMode.SEQUENTIAL
        val isRand = !isIfs && cfg.autoNextEnabled && cfg.autoNextMode == com.tajasb.model.AutoNextMode.RANDOM

        btnPlayModeQuickText?.text = when {
            isIfs -> "♾️"
            cfg.isBridgeCycleMode -> "🌉"
            cfg.isMasterSpillMode -> "⭐"
            isSingleLoop -> "🔂"
            isRand -> "🔀"
            else -> "🔁"
        }

        autoNextStatusLabel?.text = when {
            isIfs -> "Mode: INFINITY SYSTEM (IFS) ♾️"
            cfg.isBridgeCycleMode -> "Mode: SIKLUS BRIDGE 🌉"
            cfg.isMasterSpillMode -> if (cfg.isCurrentPlayingSpill) "Mode: SPILL TAYANG 🎯" else "Mode: MASTER LOOP ⭐"
            isSingleLoop -> "Auto-Next: LOOP 🔂"
            isSeq -> "Auto-Next: BERURUTAN 🔁"
            else -> "Auto-Next: ACAK 🔀"
        }
        autoNextStatusLabel?.setTextColor(
            when {
                isIfs -> Color.parseColor("#00E5FF")
                cfg.isBridgeCycleMode -> Color.parseColor("#A855F7")
                cfg.isMasterSpillMode -> if (cfg.isCurrentPlayingSpill) Color.parseColor("#00E5FF") else Color.parseColor("#FCD34D")
                isSingleLoop -> Color.parseColor("#D946EF")
                else -> Color.parseColor("#00E5FF")
            }
        )

        fun styleChoiceBtn(btn: TextView?, active: Boolean, activeBg: String = "#083344", activeStroke: String = "#00E5FF") {
            btn?.apply {
                setTextColor(if (active) Color.WHITE else Color.parseColor("#94A3B8"))
                setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    if (active) {
                        setColor(Color.parseColor(activeBg))
                        setStroke(dp(1), Color.parseColor(activeStroke))
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                }
            }
        }

        btnSwitchModeText?.apply {
            val isInst = cfg.videoSwitchMode == com.tajasb.model.VideoSwitchMode.INSTANT
            text = if (isInst) "⚡ Pindah: Instan (Langsung)" else "⏳ Pindah: Tunggu Selesai"
            styleChoiceBtn(this, isInst, "#083344", "#00E5FF")
            if (!isInst) {
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(Color.parseColor("#451A03"))
                    setStroke(dp(1), Color.parseColor("#F59E0B"))
                }
            }
        }

        btnAutoNextLeadText?.text = "⏱️ Pindah Awal: ${cfg.autoNextLeadSeconds}s"

        val isDualAudio = cfg.isDualAudioMixingEnabled
        val updateDualAudioBtn: (TextView?) -> Unit = { btn ->
            btn?.apply {
                text = if (isDualAudio) "🎧 Suara Ganda: ON (Video + MP3)" else "🎧 Suara Ganda: OFF (Musik Auto-Jeda)"
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(5).toFloat()
                    if (isDualAudio) {
                        setColor(Color.parseColor("#064E3B"))
                        setStroke(dp(1), Color.parseColor("#10B981"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#475569"))
                    }
                }
                setTextColor(if (isDualAudio) Color.parseColor("#34D399") else Color.parseColor("#94A3B8"))
            }
        }
        updateDualAudioBtn(btnDualAudioMixingText)
        updateDualAudioBtn(btnDualAudioMixingMusicText)

        styleChoiceBtn(btnRot0, rotation == 0)
        styleChoiceBtn(btnRot90, rotation == 90)
        styleChoiceBtn(btnRot180, rotation == 180)
        styleChoiceBtn(btnRot270, rotation == 270)

        styleChoiceBtn(btnMirrorToggle, isMirrored, "#3B0764", "#D946EF")
        btnMirrorToggle?.text = if (isMirrored) "↔️ Mirror: ON" else "↔️ Mirror: OFF"

        styleChoiceBtn(btnFlipVToggle, isVerticalFlipped, "#701A75", "#F43F5E")
        btnFlipVToggle?.text = if (isVerticalFlipped) "↕️ Flip V: ON" else "↕️ Flip V: OFF"

        styleChoiceBtn(btnScaleCrop, scaleMode == ScaleMode.CROP)
        styleChoiceBtn(btnScaleFit, scaleMode == ScaleMode.FIT)
        styleChoiceBtn(btnScaleStretch, scaleMode == ScaleMode.STRETCH)

        btnFpsQuick?.text = "⚡ ${cfg.fps} FPS ▾"
        styleChoiceBtn(btnFps15, cfg.fps == 15, "#1E1B4B", "#818CF8")
        styleChoiceBtn(btnFps24, cfg.fps == 24, "#083344", "#00E5FF")
        styleChoiceBtn(btnFps30, cfg.fps == 30, "#064E3B", "#10B981")
        styleChoiceBtn(btnFps60, cfg.fps == 60, "#3B0764", "#D946EF")

        styleChoiceBtn(qBtnFps15, cfg.fps == 15, "#1E1B4B", "#818CF8")
        styleChoiceBtn(qBtnFps24, cfg.fps == 24, "#083344", "#00E5FF")
        styleChoiceBtn(qBtnFps30, cfg.fps == 30, "#064E3B", "#10B981")
        styleChoiceBtn(qBtnFps60, cfg.fps == 60, "#3B0764", "#D946EF")

        val curZoom = cfg.zoomFactor
        zoomValueBadgeText?.text = String.format(java.util.Locale.US, "%.2fx (%d%%)", curZoom, (curZoom * 100).toInt())
        val isZ80 = kotlin.math.abs(curZoom - 0.80f) < 0.02f
        val isZ100 = kotlin.math.abs(curZoom - 1.00f) < 0.02f
        val isZ125 = kotlin.math.abs(curZoom - 1.25f) < 0.02f
        val isZ150 = kotlin.math.abs(curZoom - 1.50f) < 0.02f
        val isZ200 = kotlin.math.abs(curZoom - 2.00f) < 0.02f

        styleChoiceBtn(btnZoomChip80, isZ80, "#1E1B4B", "#818CF8")
        styleChoiceBtn(btnZoomChip100, isZ100, "#083344", "#00E5FF")
        styleChoiceBtn(btnZoomChip125, isZ125, "#064E3B", "#10B981")
        styleChoiceBtn(btnZoomChip150, isZ150, "#78350F", "#F59E0B")
        styleChoiceBtn(btnZoomChip200, isZ200, "#3B0764", "#D946EF")

        styleChoiceBtn(btnAutoNextSeq, isSeq, "#083344", "#00E5FF")
        styleChoiceBtn(btnAutoNextRand, isRand, "#1E1B4B", "#818CF8")
        styleChoiceBtn(btnAutoNextSingleLoop, isSingleLoop, "#3B0764", "#D946EF")

        btnMasterToggleText?.apply {
            val isMaster = cfg.isMasterEnabled
            text = if (isMaster) "🟢 Virtual Camera: AKTIF" else "🔴 Virtual Camera: NONAKTIF"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                if (isMaster) {
                    setColor(Color.parseColor("#064E3B"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                } else {
                    setColor(Color.parseColor("#450A0A"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                }
            }
        }

        styleChoiceBtn(btnEngineHybrid, cfg.hookEngine == com.tajasb.model.HookEngineType.HYBRID, "#083344", "#00E5FF")
        styleChoiceBtn(btnEngineCam1, cfg.hookEngine == com.tajasb.model.HookEngineType.CAMERA1_LEGACY, "#1E1B4B", "#818CF8")
        styleChoiceBtn(btnEngineCam2, cfg.hookEngine == com.tajasb.model.HookEngineType.CAMERA2_PIPELINE, "#3B0764", "#D946EF")

        styleChoiceBtn(btnResAuto, cfg.forceResolution == com.tajasb.model.ResolutionPreset.AUTO, "#083344", "#00E5FF")
        styleChoiceBtn(btnRes9_16, cfg.forceResolution == com.tajasb.model.ResolutionPreset.PORTRAIT_1080P, "#083344", "#00E5FF")
        styleChoiceBtn(btnRes1080, cfg.forceResolution == com.tajasb.model.ResolutionPreset.FHD_1080P, "#083344", "#00E5FF")
        styleChoiceBtn(btnRes720, cfg.forceResolution == com.tajasb.model.ResolutionPreset.HD_720P, "#083344", "#00E5FF")
        styleChoiceBtn(btnResStride, cfg.forceResolution == com.tajasb.model.ResolutionPreset.STRIDE_XIAOMI, "#083344", "#00E5FF")
        styleChoiceBtn(btnRes480, cfg.forceResolution == com.tajasb.model.ResolutionPreset.SD_480P, "#083344", "#00E5FF")

        styleChoiceBtn(btnLightweightModeSettings, cfg.isLightweightMode, "#78350F", "#F59E0B")
        btnLightweightModeSettings?.text = if (cfg.isLightweightMode) "⚡ Mode Ringan (Anti-Lag): ON" else "⚡ Mode Ringan: OFF (Normal)"

        styleChoiceBtn(btnOsdSettings, cfg.isDebugOsdInVirtualCameraEnabled, "#083344", "#00E5FF")
        btnOsdSettings?.text = if (cfg.isDebugOsdInVirtualCameraEnabled) "📊 OSD Telemetri Video: ON" else "📊 OSD Telemetri Video: OFF"

        val hasPrimary = cfg.primaryTargetPackage.isNotBlank()
        btnTargetScopeSettings?.apply {
            text = if (hasPrimary) "🎯 Target: ${cfg.primaryTargetPackage.substringAfterLast('.')}" else "🌐 Target: Semua Aplikasi (Global)"
            styleChoiceBtn(this, hasPrimary, "#064E3B", "#10B981")
        }

        val isA11y = com.tajasb.VcamAccessibilityService.isAccessibilityEnabled(this@FloatingControlService)
        btnA11yStatusSettingsText?.apply {
            text = if (isA11y) "👆 Aksesibilitas: AKTIF ✅" else "👆 Aksesibilitas: PERLU IZIN ⚠️"
            styleChoiceBtn(this, isA11y, "#064E3B", "#10B981")
        }

        updateMusicPlayerUi()
        buildQuickVideoListItems(playlist, currentIdx)
        updateBubbleIconsStatus()
        updateTabVisibilityAndSwitches(cfg)
    }

    internal fun showExpandedPanel() {
        if (isStoppingOrDestroyed || !isRunning) return
        isPanelExpanded = true
        removeViewSafely(floatingBubbleView)

        val params = panelLayoutParams ?: return
        val panel = expandedControlPanelView ?: return

        if (panel.windowToken == null) {
            addOverlayViewSafely(panel, params)
        } else {
            panel.visibility = View.VISIBLE
            FloatingBubbleWindow.bringFloatingWindowToFront(this)
        }
        updateUiForConfig(repository.config.value)
        if (currentTab == FloatingTab.TAP && isTapMarksUiDirty) {
            requestTapMarksUiRefresh(immediate = true)
        }
    }

    internal fun showBubbleOnly() {
        if (isStoppingOrDestroyed || !isRunning) return
        isPanelExpanded = false
        setPanelFocusable(false)
        removeViewSafely(expandedControlPanelView)

        val params = bubbleLayoutParams ?: return
        val bubble = floatingBubbleView ?: return

        if (bubble.windowToken == null) {
            addOverlayViewSafely(bubble, params)
        } else {
            bubble.visibility = View.VISIBLE
            FloatingBubbleWindow.bringFloatingWindowToFront(this)
        }
        updateUiForConfig(repository.config.value)
    }

    fun setPanelFocusable(focusable: Boolean) {
        val params = panelLayoutParams ?: return
        val panel = expandedControlPanelView ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (panel.windowToken != null) {
            try {
                windowManager.updateViewLayout(panel, params)
            } catch (_: Exception) {}
        }
    }

    internal fun showStikerTextInputDialog(targetEt: EditText) =
        FloatingUiWidgets.showStikerTextInputDialog(this, targetEt)

    internal fun showWatermarkTextInputDialog(targetEt: EditText) =
        FloatingUiWidgets.showWatermarkTextInputDialog(this, targetEt)

    internal fun showHexColorInputDialog(title: String, initialHex: String, onColorApplied: (String) -> Unit) =
        FloatingUiWidgets.showHexColorInputDialog(this, title, initialHex, onColorApplied)

    internal fun createBubbleView() =
        FloatingBubbleWindow.createBubbleView(this)

    internal fun setupBubbleDrag(view: View, params: WindowManager.LayoutParams) =
        FloatingBubbleWindow.setupBubbleDrag(this, view, params)

    private fun createExpandedPanelView() {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val prefs = getSharedPreferences("vcam_settings", Context.MODE_PRIVATE)
        val savedPanelWidth = prefs.getInt("floating_panel_custom_width", (290 * density).toInt())
            .coerceIn(dp(240), resources.displayMetrics.widthPixels)
        val savedScrollHeight = prefs.getInt("floating_panel_custom_scroll_height", dp(250))
            .coerceIn(dp(140), (resources.displayMetrics.heightPixels * 0.8f).toInt())

        val currentAlpha = repository.config.value.floatingAlpha.coerceIn(0.50f, 1.0f)
        val wmPanelParams = WindowManager.LayoutParams(
            savedPanelWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(80)
            alpha = currentAlpha
        }
        panelLayoutParams = wmPanelParams

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#0B1120"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            background = bg
            setPadding(dp(12), dp(8), dp(12), dp(10))
            elevation = dp(12).toFloat()
            alpha = currentAlpha
        }

        // Top Drag Handle Bar
        val dragHandle = View(this).apply {
            val lp = LinearLayout.LayoutParams(dp(44), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(2)
                bottomMargin = dp(6)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(2).toFloat()
                setColor(Color.parseColor("#64748B"))
            }
        }
        panel.addView(dragHandle)

        // 1. TOP HEADER (Status, Open App, Minimize, Close — tanpa logo & nama APK)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val subStatus = TextView(this).apply {
            text = "Siap"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            maxLines = 1
        }
        panelTitleStatus = subStatus
        titleContainer.addView(subStatus)

        header.addView(titleContainer)

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
        header.addView(btnOpenApp)

        val spacer0 = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(4), 1) }
        header.addView(spacer0)

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
            }
            setOnLongClickListener {
                launchTargetApp(showChooser = true)
                true
            }
        }
        header.addView(btnLaunchTarget)

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(4), 1) }
        header.addView(spacer1)

        val btnMin = TextView(this).apply {
            text = " ➖ "
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
                showBubbleOnly()
            }
        }
        header.addView(btnMin)

        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(4), 1) }
        header.addView(spacer2)

        var isConfirmingHeaderClose = false
        var resetHeaderCloseRunnable: Runnable? = null

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
                isConfirmingHeaderClose = false
                text = " 🛑 "
                setTextColor(Color.parseColor("#F87171"))
                background = defaultBg
            }

            setOnClickListener {
                if (!isConfirmingHeaderClose) {
                    isConfirmingHeaderClose = true
                    text = " ⚠️ Yakin? "
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(6).toFloat()
                        setColor(Color.parseColor("#DC2626"))
                    }
                    showVipOnlyToast("⚠️ Klik sekali lagi untuk menutup floating (batal dalam 3.5 detik)", Toast.LENGTH_SHORT)
                    resetHeaderCloseRunnable?.let { removeCallbacks(it) }
                    resetHeaderCloseRunnable = Runnable { resetToIdle() }
                    postDelayed(resetHeaderCloseRunnable, 3500)
                } else {
                    resetHeaderCloseRunnable?.let { removeCallbacks(it) }
                    resetToIdle()
                    showVipOnlyToast("Layanan Floating ditutup 🛑", Toast.LENGTH_SHORT)
                    closeFloatingService()
                }
            }
        }
        header.addView(btnClose)
        panel.addView(header)

        // 1B. REAL-TIME AUDIO HARDWARE & SIGNAL ANTI-PL WARNING BAR (1-Click Recovery)
        val audioWarnBar = TextView(this).apply {
            text = "🔇 SUARA MATI • KETUK PULIHKAN SUARA 🔊"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
                bottomMargin = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                val msg = com.tajasb.engine.SystemDiagnosticHelper.recoverAudioHardwareAndStream(
                    context = applicationContext,
                    repository = repository
                )
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                val cfg = repository.config.value
                val mp = repository.musicPlayer
                val refreshed = com.tajasb.engine.SystemDiagnosticHelper.checkAudioHealth(
                    context = applicationContext,
                    hasActiveVideo = cfg.playlist.isNotEmpty(),
                    isVideoPaused = cfg.isVideoPaused,
                    isVideoAudioMuted = cfg.isVideoAudioMuted,
                    videoVolume = repository.videoVolume.value,
                    hasMp3Playlist = cfg.audioPlaylist.isNotEmpty(),
                    isMp3Playing = mp.isPlaying.value,
                    isMp3Muted = mp.isMuted.value,
                    mp3Volume = mp.volume.value,
                    isAudioPlayerStalled = false,
                    audioPlayerErrorReason = null
                )
                updateFloatingAudioWarningBar(refreshed)
            }
        }
        floatingAudioSignalWarningBar = audioWarnBar
        panel.addView(audioWarnBar)

        // 2. TAB SELECTOR BAR (🎬 Video, 🎵 Musik, 🎨 Visual, 💬 Notif, 👆 Tap, ⚙️ Setelan)
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(8))
        }

        fun createTabItem(label: String, tab: FloatingTab): TextView {
            return TextView(this).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(6), dp(4), dp(6))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                setOnClickListener {
                    selectTab(tab)
                }
            }
        }

        btnTabVideo = createTabItem("🎬", FloatingTab.VIDEO)
        btnTabMusic = createTabItem("🎵", FloatingTab.MUSIC)
        btnTabVisual = createTabItem("🎨", FloatingTab.VISUAL)
        btnTabNotif = createTabItem("💬", FloatingTab.NOTIF)
        btnTabTap = createTabItem("👆", FloatingTab.TAP)
        btnTabSettings = createTabItem("⚙️", FloatingTab.SETTINGS)
        btnTabLog = createTabItem("📋", FloatingTab.LOG)

        tabRow.addView(btnTabVideo)
        tabRow.addView(btnTabMusic)
        tabRow.addView(btnTabVisual)
        tabRow.addView(btnTabNotif)
        tabRow.addView(btnTabTap)
        tabRow.addView(btnTabSettings)
        tabRow.addView(btnTabLog)
        panel.addView(tabRow)

        // 2B. PINNED STICKY HEADER FOR TAB 5 (AUTO-TAP) THAT NEVER SCROLLS DOWN!
        val stickyTapBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(9).toFloat()
                setColor(Color.parseColor("#08101E"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setPadding(dp(7), dp(6), dp(7), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
            visibility = View.GONE
        }
        stickyTapActionBar = stickyTapBar
        panel.addView(stickyTapBar)

        // 3. FIXED-HEIGHT SCROLLABLE CONTENT AREA
        val tabScroll = ScrollView(this).apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, savedScrollHeight)
            layoutParams = lp
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            isNestedScrollingEnabled = true
        }
        tabContentScrollView = tabScroll

        val tabContentHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        this.tabContentHolder = tabContentHolder
        tabScroll.addView(tabContentHolder)
        panel.addView(tabScroll)

        // Modular Tab Builders:
        // A. Tab 1 (Video & Spill), Tab 2 (Music), Tab 3 (Visual)
        SpillTabBuilder.populateVideoMusicVisualTabs(
            service = this,
            tabContentHolder = tabContentHolder,
            dp = ::dp
        )

        // B. Tab 4 Sub-Mode 1: Doa & Komentar Live
        val tabNotifContainer = LiveCommentTabBuilder.createNotifContainerAndDoaTab(
            service = this,
            dp = ::dp
        )

        fun createStepperBtn(label: String, onClick: () -> Unit): TextView =
            FloatingUiWidgets.createStepperBtn(this, ::dp, label, onClick)

        fun createPresetChip(label: String, onClick: () -> Unit): TextView =
            FloatingUiWidgets.createPresetChip(this, ::dp, label, onClick)

        // C. Tab 4 Sub-Mode 2: Notif Pembeli (Jualan)
        BuyerNotifTabBuilder.populateSalesTab(
            service = this,
            tabNotifContainer = tabNotifContainer,
            dp = ::dp,
            createStepperBtn = ::createStepperBtn,
            createPresetChip = ::createPresetChip
        )

        // D. Tab 4 Sub-Mode 3: Stiker Tulisan
        StikerTabBuilder.populateStikerTab(
            service = this,
            tabNotifContainer = tabNotifContainer,
            dp = ::dp,
            createStepperBtn = ::createStepperBtn
        )

        // D2. Tab 4 Sub-Mode 5: Kapsul Waktu Otomatis (Tgl+Bln & Jam Live)
        AutoTimeTabBuilder.populateAutoTimeTab(
            service = this,
            tabNotifContainer = tabNotifContainer,
            dp = ::dp,
            createStepperBtn = ::createStepperBtn,
            createPresetChip = ::createPresetChip
        )

        // D3. Tab 4 Sub-Mode 6: Khatam Al-Qur'an 30 Juz Live Interaktif
        QuranKhatamTabBuilder.populateQuranKhatamTab(
            service = this,
            tabNotifContainer = tabNotifContainer,
            dp = ::dp,
            createStepperBtn = ::createStepperBtn,
            createPresetChip = ::createPresetChip
        )

        // D4. Tab 4 Sub-Mode 7: Tarik Tambang (Duel Kubu) Interaktif
        TugOfWarTabBuilder.populateTugOfWarTab(
            service = this,
            tabNotifContainer = tabNotifContainer,
            dp = ::dp,
            createStepperBtn = ::createStepperBtn,
            createPresetChip = ::createPresetChip
        )

        // E. Tab 4 Sub-Mode 4: Watermark Kamera
        WatermarkTabBuilder.populateWatermarkTab(
            service = this,
            tabContentHolder = tabContentHolder,
            tabNotifContainer = tabNotifContainer,
            dp = ::dp,
            createStepperBtn = ::createStepperBtn
        )

        // F. Tab 5: Auto-Tap & Multi-Choreography Macro Engine
        TapMacroTabBuilder.populateTapMacroTab(
            service = this,
            tabContentHolder = tabContentHolder,
            dp = ::dp
        )

        // G. Tab 6: Settings, Zero-Load Module Switches, Fixed Bottom Dock & Resize Bar
        SettingsTabBuilder.populateSettingsTab(
            service = this,
            panel = panel,
            tabContentHolder = tabContentHolder,
            dragHandle = dragHandle,
            header = header,
            wmPanelParams = wmPanelParams,
            dp = ::dp
        )
    }

    fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    fun createPureIconBtn(icon: String, onClick: () -> Unit): TextView =
        FloatingUiWidgets.createPureIconBtn(this, ::dp, icon, onClick)

    fun createPureTextBtn(textLabel: String, onClick: () -> Unit): TextView =
        FloatingUiWidgets.createPureTextBtn(this, ::dp, textLabel, onClick = onClick)

    fun createPureTextBtn(
        textLabel: String,
        accentBgHex: String,
        accentStrokeHex: String,
        onClick: () -> Unit
    ): TextView =
        FloatingUiWidgets.createPureTextBtn(this, ::dp, textLabel, accentBgHex, accentStrokeHex, onClick)

    internal fun createFixedBottomDockBar(dp: (Int) -> Int): LinearLayout =
        FloatingUiWidgets.createFixedBottomDockBar(this, dp)

    internal fun updateFixedBottomDockUi() =
        FloatingUiWidgets.updateFixedBottomDockUi(this)

    internal fun dispatchMove(dxPct: Float, dyPct: Float, dxPixels: Float, dyPixels: Float) =
        FloatingUiWidgets.dispatchMove(this, dxPct, dyPct, dxPixels, dyPixels)

    internal fun dispatchRotate() =
        FloatingUiWidgets.dispatchRotate(this)

    internal fun dispatchMirror() =
        FloatingUiWidgets.dispatchMirror(this)

    internal fun dispatchScale(delta: Float) =
        FloatingUiWidgets.dispatchScale(this, delta)

    internal fun dispatchReset() =
        FloatingUiWidgets.dispatchReset(this)

    internal fun updateCrosshairWindow() =
        FloatingUiWidgets.updateCrosshairWindow(this)

    internal fun setupPanelDrag(view: View, params: WindowManager.LayoutParams) =
        FloatingUiWidgets.setupPanelDrag(this, view, params)

    internal fun setupPanelCornerResize(view: View, params: WindowManager.LayoutParams, scrollContent: ScrollView?) =
        FloatingUiWidgets.setupPanelCornerResize(this, view, params, scrollContent)

    internal fun buildQuickVideoListItems(playlist: List<com.tajasb.model.VideoItem>, currentIndex: Int) =
        SpillTabBuilder.buildQuickVideoListItems(this, playlist, currentIndex)

    internal fun populateQuickSpillChips(playlist: List<com.tajasb.model.VideoItem>, currentIdx: Int) =
        SpillTabBuilder.populateQuickSpillChips(this, playlist, currentIdx)

    internal fun buildQuickAudioListItems(playlist: List<com.tajasb.model.AudioItem>, currentIndex: Int) =
        SpillTabBuilder.buildQuickAudioListItems(this, playlist, currentIndex)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tajasb Floating Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kontrol melayang untuk kamera virtual real-time"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FloatingControlService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cfg = repository.config.value
        val rot = cfg.rotation
        val mirror = if (cfg.isMirrored) "🪞" else ""
        val count = cfg.playlist.size
        val cur = cfg.currentVideoIndex + 1

        val isMusicPlaying = repository.musicPlayer.isPlaying.value
        val musicTitle = repository.musicPlayer.currentTrack.value?.name ?: cfg.currentAudio?.name
        val musicText = if (isMusicPlaying && musicTitle != null) " • 🎵 $musicTitle" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tajasb Melayang Aktif [$cur/$count]$musicText")
            .setContentText("Rotasi: $rot°$mirror • ${cfg.scaleMode.label.substringBefore(" (")}")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .apply {
                try {
                    setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, com.example.R.drawable.ic_app_icon))
                } catch (_: Throwable) {}
            }
            .setContentIntent(pOpenIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tutup Overlay", pStopIntent)
            .setOngoing(true)
            .build()
    }

    internal fun getOverlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    internal fun showCrosshairPointer() =
        FloatingBubbleWindow.showCrosshairPointer(this)

    internal fun removeCrosshairPointer() =
        FloatingBubbleWindow.removeCrosshairPointer(this)

    internal fun updateAutoTapUi() =
        FloatingBubbleWindow.updateAutoTapUi(this)

    internal fun showScanAreaOverlay() =
        FloatingBubbleWindow.showScanAreaOverlay(this)

    internal fun updateBuyerNotifUi() =
        BuyerNotifTabBuilder.updateBuyerNotifUi(this)

    internal fun updateScanRecorderUi() =
        LiveCommentTabBuilder.updateScanRecorderUi(this)

    internal fun toggleMakkahRelay() {
        val cur = repository.config.value
        val nextActive = !cur.isLiveStreamSourceActive
        val url = if (cur.liveStreamUrl.isNotBlank()) cur.liveStreamUrl else com.tajasb.data.VcamRepository.MAKKAH_LIVE_STREAM_1
        serviceScope.launch {
            repository.setLiveStreamSource(active = nextActive, url = url)
        }
        showVipOnlyToast(
            if (nextActive) "🕋 Relay Makkah LIVE Diaktifkan!" else "⏸️ Relay Makkah Dinonaktifkan (Video Lokal)",
            Toast.LENGTH_SHORT
        )
    }

    internal fun selectMakkahStream(url: String, title: String) {
        serviceScope.launch {
            repository.setLiveStreamSource(active = true, url = url, title = title)
        }
        showVipOnlyToast("🕋 Memutar $title...", Toast.LENGTH_SHORT)
    }

    internal fun refreshMakkahStream() {
        serviceScope.launch {
            repository.refreshLiveStream()
        }
        showVipOnlyToast("⚡ Memuat Ulang Relay Makkah (Anti-Stall)...", Toast.LENGTH_SHORT)
    }

    internal fun setMakkahScaleMode(mode: com.tajasb.model.ScaleMode) {
        serviceScope.launch {
            repository.setScaleMode(mode)
        }
        val modeName = when (mode) {
            com.tajasb.model.ScaleMode.CROP -> "Kaaba Fokus (9:16 Center Crop)"
            com.tajasb.model.ScaleMode.FIT -> "Kaaba Utuh (16:9 Proporsional)"
            com.tajasb.model.ScaleMode.STRETCH -> "Layar Penuh (Stretch)"
        }
        showVipOnlyToast("📐 Skala: $modeName", Toast.LENGTH_SHORT)
    }

    internal fun adjustMakkahZoom(delta: Float) {
        val cur = repository.config.value
        val nextZoom = ((cur.zoomFactor + delta) * 100).toInt() / 100f
        val clampedZoom = nextZoom.coerceIn(0.5f, 3.0f)
        serviceScope.launch {
            repository.setSessionDnaFraming(
                zoom = clampedZoom,
                panX = cur.sessionDnaPanX,
                panY = cur.sessionDnaPanY,
                registerToHistory = false,
                enableDnaZoom = true
            )
        }
        showVipOnlyToast("🔍 Zoom: ${String.format(java.util.Locale.US, "%.2fx", clampedZoom)}", Toast.LENGTH_SHORT)
    }

    internal fun adjustMakkahPan(deltaX: Float, deltaY: Float) {
        repository.adjustMakkahPan(deltaX, deltaY)
    }

    internal fun resetMakkahFraming() {
        repository.resetMakkahFraming()
        showVipOnlyToast("↺ Framing & Zoom Ka'bah Direset ke Normal", Toast.LENGTH_SHORT)
    }

    internal fun cycleMakkahRotation() {
        val cur = repository.config.value.rotation
        val next = (cur + 90) % 360
        serviceScope.launch {
            repository.setRotation(next)
        }
        showVipOnlyToast("🔄 Rotasi Relay: $next°", Toast.LENGTH_SHORT)
    }

    // =========================================================================
    // ⚡ LIVE EVENT BROKER CONTROLS (DEVICE 1 ➔ DEVICE 2)
    // =========================================================================
    internal fun updateLiveEventBrokerFloatingUi() {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val receiver = com.tajasb.eventrelay.LiveEventReceiver.getInstance(applicationContext)
        val prefs = com.tajasb.eventrelay.LiveEventPreferences.getInstance(applicationContext)
        val cfg = prefs.config.value
        val status = receiver.connectionStatus.value
        val isConnected = (status == com.tajasb.eventrelay.ConnectionStatus.CONNECTED)

        tvBrokerStatusBadge?.apply {
            text = when (status) {
                com.tajasb.eventrelay.ConnectionStatus.CONNECTED -> "🟢 ${cfg.serverHost}"
                com.tajasb.eventrelay.ConnectionStatus.CONNECTING -> "🟡 KONEK..."
                com.tajasb.eventrelay.ConnectionStatus.ERROR -> "🔴 ERROR"
                else -> "⚪ OFFLINE"
            }
            setTextColor(when (status) {
                com.tajasb.eventrelay.ConnectionStatus.CONNECTED -> Color.parseColor("#10B981")
                com.tajasb.eventrelay.ConnectionStatus.CONNECTING -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#94A3B8")
            })
        }

        btnBrokerToggleConn?.apply {
            text = if (isConnected) "Putus" else "Hubungkan"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isConnected) Color.parseColor("#DC2626") else Color.parseColor("#059669"))
            }
        }

        fun updateSwitchBtn(btn: TextView?, active: Boolean, activeText: String, inactiveText: String) {
            btn?.apply {
                text = if (active) activeText else inactiveText
                setTextColor(if (active) Color.BLACK else Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (active) Color.parseColor("#10B981") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (active) Color.parseColor("#10B981") else Color.parseColor("#334155"))
                }
            }
        }

        updateSwitchBtn(btnBrokerSwitchChat, cfg.enableComment, "💬 Doa: ON", "💬 Doa: OFF")
        updateSwitchBtn(btnBrokerSwitchGift, cfg.enableGift, "🎁 Gift: ON", "🎁 Gift: OFF")
        updateSwitchBtn(btnBrokerSwitchLike, cfg.enableLike, "❤️ Like: ON", "❤️ Like: OFF")
        updateSwitchBtn(btnBrokerSwitchJoin, cfg.enableJoin, "👋 Join: ON", "👋 Join: OFF")
        updateSwitchBtn(btnBrokerSwitchFollow, cfg.enableFollow, "➕ Follow: ON", "➕ Follow: OFF")
        updateSwitchBtn(btnBrokerSwitchShare, cfg.enableShare, "🔄 Share: ON", "🔄 Share: OFF")
        updateSwitchBtn(btnBrokerSwitchViewers, cfg.enableViewerCount, "👥 View: ON", "👥 View: OFF")

        val lastEv = receiver.lastEvent.value
        tvBrokerEventLastLog?.text = if (lastEv != null) {
            "⚡ [${lastEv.type.name}] ${lastEv.senderName}: ${lastEv.text}"
        } else {
            "⚡ Bebas scan layar & OCR. Menunggu event dari Device 1 (${cfg.serverHost}:${cfg.serverPort})."
        }
    }

    internal fun toggleBrokerConnection() {
        val receiver = com.tajasb.eventrelay.LiveEventReceiver.getInstance(applicationContext)
        receiver.toggleConnection()
        updateLiveEventBrokerFloatingUi()
    }

    internal fun toggleBrokerComment() {
        val prefs = com.tajasb.eventrelay.LiveEventPreferences.getInstance(applicationContext)
        val next = !prefs.config.value.enableComment
        prefs.setEnableComment(next)
        showVipOnlyToast("💬 Saklar Doa & Komentar: ${if (next) "ON" else "OFF"}", Toast.LENGTH_SHORT)
        updateLiveEventBrokerFloatingUi()
    }

    internal fun toggleBrokerGift() {
        val prefs = com.tajasb.eventrelay.LiveEventPreferences.getInstance(applicationContext)
        val next = !prefs.config.value.enableGift
        prefs.setEnableGift(next)
        showVipOnlyToast("🎁 Saklar Gift & Saweran: ${if (next) "ON" else "OFF"}", Toast.LENGTH_SHORT)
        updateLiveEventBrokerFloatingUi()
    }

    internal fun toggleBrokerLike() {
        val prefs = com.tajasb.eventrelay.LiveEventPreferences.getInstance(applicationContext)
        val next = !prefs.config.value.enableLike
        prefs.setEnableLike(next)
        showVipOnlyToast("❤️ Saklar Tap Like: ${if (next) "ON" else "OFF"}", Toast.LENGTH_SHORT)
        updateLiveEventBrokerFloatingUi()
    }

    internal fun toggleBrokerJoin() {
        val prefs = com.tajasb.eventrelay.LiveEventPreferences.getInstance(applicationContext)
        val next = !prefs.config.value.enableJoin
        prefs.setEnableJoin(next)
        showVipOnlyToast("👋 Saklar Sambutan Join: ${if (next) "ON" else "OFF"}", Toast.LENGTH_SHORT)
        updateLiveEventBrokerFloatingUi()
    }

    internal fun toggleBrokerFollow() {
        val prefs = com.tajasb.eventrelay.LiveEventPreferences.getInstance(applicationContext)
        val next = !prefs.config.value.enableFollow
        prefs.setEnableFollow(next)
        showVipOnlyToast("➕ Saklar Follow Baru: ${if (next) "ON" else "OFF"}", Toast.LENGTH_SHORT)
        updateLiveEventBrokerFloatingUi()
    }

    internal fun toggleBrokerShare() {
        val prefs = com.tajasb.eventrelay.LiveEventPreferences.getInstance(applicationContext)
        val next = !prefs.config.value.enableShare
        prefs.setEnableShare(next)
        showVipOnlyToast("🔄 Saklar Share Live: ${if (next) "ON" else "OFF"}", Toast.LENGTH_SHORT)
        updateLiveEventBrokerFloatingUi()
    }

    internal fun toggleBrokerViewer() {
        val prefs = com.tajasb.eventrelay.LiveEventPreferences.getInstance(applicationContext)
        val next = !prefs.config.value.enableViewerCount
        prefs.setEnableViewerCount(next)
        showVipOnlyToast("👥 Saklar Indikator Penonton: ${if (next) "ON" else "OFF"}", Toast.LENGTH_SHORT)
        updateLiveEventBrokerFloatingUi()
    }

    internal fun testBrokerInject(type: com.tajasb.eventrelay.LiveEventType) {
        val receiver = com.tajasb.eventrelay.LiveEventReceiver.getInstance(applicationContext)
        receiver.injectTestEvent(type)
        showVipOnlyToast("🧪 Tes ${type.name} Dikirim ke Kamera Virtual", Toast.LENGTH_SHORT)
        updateLiveEventBrokerFloatingUi()
    }
}

