package com.tajasb.livecomment

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class BannerMode {
    LIVE_DOA_COMMENT, // Automatic capture of real comments/prayers from TikTok live
    BUYER_SALES,      // Realistic simulated buyer orders ("sedang membeli")
    STICKER_TEXT,     // Solid color custom text sticker/banner ("PROMO LIVE 50%")
    WATERMARK_TEXT,   // Dedicated custom watermark (separate from sticker)
    AUTO_TIME,        // Automatic live Date+Month box (left) & HH:mm[:ss] Clock (right) capsule
    QURAN_KHATAM,     // Interactive Khatam Al-Qur'an 30 Juz letter-by-letter live overlay
    TUG_OF_WAR        // Interactive Tug of War / Team Battle game overlay
}

enum class BannerIconMode {
    NONE,             // Clean text without icon (ideal for prayers / minimal look)
    PIN,              // 📌 Pin icon
    PRAYER,           // 🤲 Prayer hands
    SHOPPING          // 🛍️ Shopping bag
}

enum class CommentFormatMode(val label: String, val pattern: String) {
    PREFIX_AND_COMMENT("Prefix + Komentar", "(Custom): (Komentar)"),        // e.g. "Doa: Semoga berkah"
    PREFIX_NAME_COMMENT("Prefix + Nama + Komentar", "(Custom): (Nama) (Komentar)"), // e.g. "Doa: Bang Jo Semoga berkah"
    NAME_AND_COMMENT("Nama + Komentar", "(Nama): (Komentar)"),             // e.g. "Bang Jo: Semoga berkah"
    COMMENT_ONLY("Hanya Komentar", "(Komentar Saja)")                      // e.g. "Semoga berkah"
}

enum class ScanAreaPreset(val label: String, val topPct: Float, val bottomPct: Float, val leftPct: Float, val rightPct: Float) {
    STANDARD_LIVE("Area Chat Standar (TikTok/Shopee)", 0.40f, 0.88f, 0.00f, 0.80f),
    LOWER_HALF("Setengah Bawah (50% - 90%)", 0.50f, 0.90f, 0.00f, 0.85f),
    COMPACT_CHAT("Chat Bawah Kiri (60% - 88%)", 0.60f, 0.88f, 0.00f, 0.75f),
    FULL_SCREEN("Seluruh Layar (Tanpa Batasan)", 0.00f, 1.00f, 0.00f, 1.00f),
    CUSTOM("Area Kustom", 0.40f, 0.88f, 0.00f, 0.80f)
}

data class LiveCommentItem(
    val id: String = UUID.randomUUID().toString(),
    val senderName: String,
    val content: String,
    val isGift: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class LiveCommentManager private constructor(private val context: Context) {

    companion object {
        const val TAG = "LiveCommentManager"
        private const val PREFS_NAME = "live_comment_prefs"
        private const val KEY_BANNER_MODE = "key_banner_mode"
        private const val KEY_ICON_MODE = "key_icon_mode"
        private const val KEY_CAPTURE_ACTIVE = "key_capture_active"

        private const val KEY_AREA_FILTER_ENABLED = "key_area_filter_enabled"
        private const val KEY_SCAN_AREA_PRESET = "key_scan_area_preset"
        private const val KEY_SCAN_TOP_PCT = "key_scan_top_pct"
        private const val KEY_SCAN_BOTTOM_PCT = "key_scan_bottom_pct"
        private const val KEY_SCAN_LEFT_PCT = "key_scan_left_pct"
        private const val KEY_SCAN_RIGHT_PCT = "key_scan_right_pct"

        private const val KEY_OVERLAY_POS_X_PCT = "key_overlay_pos_x_pct"
        private const val KEY_OVERLAY_POS_Y_PCT = "key_overlay_pos_y_pct"
        private const val KEY_OVERLAY_ROTATION = "key_overlay_rotation"
        private const val KEY_OVERLAY_SCALE = "key_overlay_scale"
        private const val KEY_OVERLAY_MIRRORED = "key_overlay_mirrored"
        private const val KEY_CUSTOM_PREFIX = "key_custom_prefix"
        private const val KEY_FORMAT_MODE = "key_format_mode"

        @Volatile
        private var instance: LiveCommentManager? = null

        fun getInstance(context: Context): LiveCommentManager {
            return instance ?: synchronized(this) {
                instance ?: LiveCommentManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Banner & Icon modes
    private val _bannerMode = MutableStateFlow(
        try {
            BannerMode.valueOf(prefs.getString(KEY_BANNER_MODE, BannerMode.WATERMARK_TEXT.name) ?: BannerMode.WATERMARK_TEXT.name)
        } catch (_: Exception) {
            BannerMode.WATERMARK_TEXT
        }
    )
    val bannerMode: StateFlow<BannerMode> = _bannerMode.asStateFlow()

    private val _iconMode = MutableStateFlow(
        try {
            BannerIconMode.valueOf(prefs.getString(KEY_ICON_MODE, BannerIconMode.NONE.name) ?: BannerIconMode.NONE.name)
        } catch (_: Exception) {
            BannerIconMode.NONE
        }
    )
    val iconMode: StateFlow<BannerIconMode> = _iconMode.asStateFlow()

    init {
        if (!prefs.getBoolean("key_scan_defaults_migrated_v3_sepuh", false)) {
            prefs.edit()
                .putString(KEY_BANNER_MODE, BannerMode.WATERMARK_TEXT.name)
                .putString(KEY_ICON_MODE, BannerIconMode.NONE.name)
                .putString(KEY_FORMAT_MODE, CommentFormatMode.PREFIX_AND_COMMENT.name)
                .putString(KEY_CUSTOM_PREFIX, "")
                .putBoolean(KEY_CAPTURE_ACTIVE, false)
                .putBoolean(KEY_AREA_FILTER_ENABLED, false)
                .putString(KEY_SCAN_AREA_PRESET, ScanAreaPreset.STANDARD_LIVE.name)
                .putFloat(KEY_SCAN_TOP_PCT, 0.40f)
                .putFloat(KEY_SCAN_BOTTOM_PCT, 0.98f)
                .putFloat(KEY_SCAN_LEFT_PCT, 0.00f)
                .putFloat(KEY_SCAN_RIGHT_PCT, 1.00f)
                .putFloat(KEY_OVERLAY_POS_X_PCT, 0.50f)
                .putFloat(KEY_OVERLAY_POS_Y_PCT, 0.50f)
                .putInt(KEY_OVERLAY_ROTATION, 0)
                .putFloat(KEY_OVERLAY_SCALE, 1.0f)
                .putBoolean(KEY_OVERLAY_MIRRORED, false)
                .putBoolean("key_scan_defaults_migrated_v3_sepuh", true)
                .putBoolean("key_comment_frame_centered_v2", true)
                .apply()
            _bannerMode.value = BannerMode.WATERMARK_TEXT
            _iconMode.value = BannerIconMode.NONE
        } else if (!prefs.getBoolean("key_comment_frame_centered_v2", false)) {
            prefs.edit()
                .putFloat(KEY_OVERLAY_POS_X_PCT, 0.50f)
                .putFloat(KEY_OVERLAY_POS_Y_PCT, 0.50f)
                .putFloat(KEY_OVERLAY_SCALE, 1.0f)
                .putBoolean("key_comment_frame_centered_v2", true)
                .apply()
        }
    }

    // Live capture state (Default OFF)
    private val _isCaptureActive = MutableStateFlow(prefs.getBoolean(KEY_CAPTURE_ACTIVE, false))
    val isCaptureActive: StateFlow<Boolean> = _isCaptureActive.asStateFlow()

    // Scan Area Filtering (Batas Area Tertentu untuk Pembacaan Komentar - Default OFF)
    private val _isAreaFilterEnabled = MutableStateFlow(prefs.getBoolean(KEY_AREA_FILTER_ENABLED, false))
    val isAreaFilterEnabled: StateFlow<Boolean> = _isAreaFilterEnabled.asStateFlow()

    private val _scanAreaPreset = MutableStateFlow(
        try {
            ScanAreaPreset.valueOf(prefs.getString(KEY_SCAN_AREA_PRESET, ScanAreaPreset.STANDARD_LIVE.name) ?: ScanAreaPreset.STANDARD_LIVE.name)
        } catch (_: Exception) {
            ScanAreaPreset.STANDARD_LIVE
        }
    )
    val scanAreaPreset: StateFlow<ScanAreaPreset> = _scanAreaPreset.asStateFlow()

    private val _scanTopPct = MutableStateFlow(prefs.getFloat(KEY_SCAN_TOP_PCT, 0.40f))
    val scanTopPct: StateFlow<Float> = _scanTopPct.asStateFlow()

    private val _scanBottomPct = MutableStateFlow(prefs.getFloat(KEY_SCAN_BOTTOM_PCT, 0.98f))
    val scanBottomPct: StateFlow<Float> = _scanBottomPct.asStateFlow()

    private val _scanLeftPct = MutableStateFlow(prefs.getFloat(KEY_SCAN_LEFT_PCT, 0.00f))
    val scanLeftPct: StateFlow<Float> = _scanLeftPct.asStateFlow()

    private val _scanRightPct = MutableStateFlow(prefs.getFloat(KEY_SCAN_RIGHT_PCT, 1.00f))
    val scanRightPct: StateFlow<Float> = _scanRightPct.asStateFlow()

    // Virtual Camera Overlay Manual Positioning & Rotation (User Customizable)
    private val _overlayPosXpct = MutableStateFlow(prefs.getFloat(KEY_OVERLAY_POS_X_PCT, 0.50f))
    val overlayPosXpct: StateFlow<Float> = _overlayPosXpct.asStateFlow()

    private val _overlayPosYpct = MutableStateFlow(prefs.getFloat(KEY_OVERLAY_POS_Y_PCT, 0.50f))
    val overlayPosYpct: StateFlow<Float> = _overlayPosYpct.asStateFlow()

    private val _overlayRotation = MutableStateFlow(prefs.getInt(KEY_OVERLAY_ROTATION, 0))
    val overlayRotation: StateFlow<Int> = _overlayRotation.asStateFlow()

    private val _overlayScale = MutableStateFlow(prefs.getFloat(KEY_OVERLAY_SCALE, 1.0f))
    val overlayScale: StateFlow<Float> = _overlayScale.asStateFlow()

    private val _overlayMirrored = MutableStateFlow(prefs.getBoolean(KEY_OVERLAY_MIRRORED, false))
    val overlayMirrored: StateFlow<Boolean> = _overlayMirrored.asStateFlow()

    // Custom Sender Prefix (e.g. "Doa", "Penonton", "Sahabat", or empty)
    private val _customPrefix = MutableStateFlow(prefs.getString(KEY_CUSTOM_PREFIX, "") ?: "")
    val customPrefix: StateFlow<String> = _customPrefix.asStateFlow()

    // Comment Format Mode (e.g. (Custom): (Komentar) or (Custom): (Nama) (Komentar))
    private val _formatMode = MutableStateFlow(
        try {
            CommentFormatMode.valueOf(prefs.getString(KEY_FORMAT_MODE, CommentFormatMode.PREFIX_AND_COMMENT.name) ?: CommentFormatMode.PREFIX_AND_COMMENT.name)
        } catch (_: Exception) {
            CommentFormatMode.PREFIX_AND_COMMENT
        }
    )
    val formatMode: StateFlow<CommentFormatMode> = _formatMode.asStateFlow()

    // Queue state
    private val queueLock = Any()
    private val commentQueue = ArrayDeque<LiveCommentItem>()
    private val seenSignatures = LinkedHashSet<String>()

    private val _queueCount = MutableStateFlow(0)
    val queueCount: StateFlow<Int> = _queueCount.asStateFlow()

    private val _currentDisplayedItem = MutableStateFlow<LiveCommentItem?>(null)
    val currentDisplayedItem: StateFlow<LiveCommentItem?> = _currentDisplayedItem.asStateFlow()

    private val _totalCapturedCount = MutableStateFlow(0)
    val totalCapturedCount: StateFlow<Int> = _totalCapturedCount.asStateFlow()

    // Dispatcher coroutine job
    private var dispatchJob: Job? = null
    private var skipJobTrigger: Job? = null

    // UI Callback
    var onShowComment: ((item: LiveCommentItem, durationMs: Long) -> Unit)? = null
    var onHideComment: (() -> Unit)? = null

    init {
        if (_isCaptureActive.value && _bannerMode.value == BannerMode.LIVE_DOA_COMMENT) {
            startDispatcher()
        }
    }

    fun setBannerMode(mode: BannerMode) {
        _bannerMode.value = mode
        prefs.edit().putString(KEY_BANNER_MODE, mode.name).apply()

        if (mode == BannerMode.LIVE_DOA_COMMENT && _isCaptureActive.value) {
            startDispatcher()
        } else if (mode == BannerMode.BUYER_SALES) {
            stopDispatcher()
        }
    }

    fun setIconMode(mode: BannerIconMode) {
        _iconMode.value = mode
        prefs.edit().putString(KEY_ICON_MODE, mode.name).apply()
    }

    fun toggleCaptureActive(active: Boolean? = null): Boolean {
        val next = active ?: !_isCaptureActive.value
        _isCaptureActive.value = next
        prefs.edit().putBoolean(KEY_CAPTURE_ACTIVE, next).apply()

        if (next) {
            startDispatcher()
        } else {
            stopDispatcher()
        }
        return next
    }

    fun setAreaFilterEnabled(enabled: Boolean) {
        _isAreaFilterEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AREA_FILTER_ENABLED, enabled).apply()
    }

    fun setScanAreaPreset(preset: ScanAreaPreset) {
        _scanAreaPreset.value = preset
        prefs.edit().putString(KEY_SCAN_AREA_PRESET, preset.name).apply()
        if (preset != ScanAreaPreset.CUSTOM) {
            setCustomScanArea(preset.topPct, preset.bottomPct, preset.leftPct, preset.rightPct, updatePreset = false)
        }
    }

    fun setCustomScanArea(top: Float, bottom: Float, left: Float = 0.00f, right: Float = 0.80f, updatePreset: Boolean = true) {
        val clampedTop = top.coerceIn(0.0f, 0.90f)
        val clampedBottom = bottom.coerceIn(clampedTop + 0.05f, 1.0f)
        val clampedLeft = left.coerceIn(0.0f, 0.90f)
        val clampedRight = right.coerceIn(clampedLeft + 0.05f, 1.0f)

        _scanTopPct.value = clampedTop
        _scanBottomPct.value = clampedBottom
        _scanLeftPct.value = clampedLeft
        _scanRightPct.value = clampedRight

        if (updatePreset) {
            _scanAreaPreset.value = ScanAreaPreset.CUSTOM
            prefs.edit().putString(KEY_SCAN_AREA_PRESET, ScanAreaPreset.CUSTOM.name).apply()
        }

        prefs.edit()
            .putFloat(KEY_SCAN_TOP_PCT, clampedTop)
            .putFloat(KEY_SCAN_BOTTOM_PCT, clampedBottom)
            .putFloat(KEY_SCAN_LEFT_PCT, clampedLeft)
            .putFloat(KEY_SCAN_RIGHT_PCT, clampedRight)
            .apply()
    }

    fun setOverlayPosition(xPct: Float, yPct: Float, preview: Boolean = true) {
        val clampedX = xPct.coerceIn(0.00f, 1.00f)
        val clampedY = yPct.coerceIn(0.00f, 1.00f)
        _overlayPosXpct.value = clampedX
        _overlayPosYpct.value = clampedY
        prefs.edit()
            .putFloat(KEY_OVERLAY_POS_X_PCT, clampedX)
            .putFloat(KEY_OVERLAY_POS_Y_PCT, clampedY)
            .apply()
        if (preview) triggerPreviewBanner("Posisi: X ${(clampedX * 100).toInt()}% • Y ${(clampedY * 100).toInt()}%")
    }

    fun moveOverlay(deltaXPct: Float, deltaYPct: Float) {
        val newX = (_overlayPosXpct.value + deltaXPct).coerceIn(0.00f, 1.00f)
        val newY = (_overlayPosYpct.value + deltaYPct).coerceIn(0.00f, 1.00f)
        setOverlayPosition(newX, newY, preview = true)
    }

    fun cycleOverlayRotation() {
        val nextRot = (_overlayRotation.value + 90) % 360
        setOverlayRotation(nextRot)
    }

    fun setOverlayRotation(angle: Int) {
        val cleanAngle = ((angle % 360) + 360) % 360
        _overlayRotation.value = cleanAngle
        prefs.edit().putInt(KEY_OVERLAY_ROTATION, cleanAngle).apply()
        triggerPreviewBanner("Rotasi: $cleanAngle°")
    }

    fun setOverlayScale(scale: Float, preview: Boolean = false) {
        val newScale = scale.coerceIn(0.5f, 2.0f)
        _overlayScale.value = newScale
        prefs.edit().putFloat(KEY_OVERLAY_SCALE, newScale).apply()
        if (preview) triggerPreviewBanner("Ukuran: ${String.format("%.1f", newScale)}x")
    }

    fun adjustOverlayScale(delta: Float) {
        val newScale = (_overlayScale.value + delta).coerceIn(0.5f, 2.0f)
        setOverlayScale(newScale, preview = true)
    }

    fun setOverlayMirrored(mirrored: Boolean, preview: Boolean = false) {
        _overlayMirrored.value = mirrored
        prefs.edit().putBoolean(KEY_OVERLAY_MIRRORED, mirrored).apply()
        if (preview) triggerPreviewBanner(if (mirrored) "Cermin (Mirror): ON 🪞" else "Cermin (Mirror): OFF 🪞")
    }

    fun setCustomPrefix(prefix: String) {
        val clean = prefix.trim()
        _customPrefix.value = clean
        prefs.edit().putString(KEY_CUSTOM_PREFIX, clean).apply()
        triggerPreviewBanner("Prefix: ${if (clean.isBlank()) "Tanpa Prefix (Nama Saja)" else clean}")
    }

    fun setFormatMode(mode: CommentFormatMode) {
        _formatMode.value = mode
        prefs.edit().putString(KEY_FORMAT_MODE, mode.name).apply()
        triggerPreviewBanner("Format: ${mode.pattern}")
    }

    fun toggleOverlayMirror(mirrored: Boolean? = null): Boolean {
        val next = mirrored ?: !_overlayMirrored.value
        _overlayMirrored.value = next
        prefs.edit().putBoolean(KEY_OVERLAY_MIRRORED, next).apply()
        triggerPreviewBanner(if (next) "Cermin (Mirror): ON 🪞" else "Cermin (Mirror): OFF 🪞")
        return next
    }

    fun resetOverlayTransform() {
        setOverlayPosition(0.50f, 0.35f, preview = false)
        _overlayRotation.value = 0
        _overlayScale.value = 1.0f
        _overlayMirrored.value = false
        prefs.edit()
            .putInt(KEY_OVERLAY_ROTATION, 0)
            .putFloat(KEY_OVERLAY_SCALE, 1.0f)
            .putBoolean(KEY_OVERLAY_MIRRORED, false)
            .apply()
        triggerPreviewBanner("Posisi & Rotasi Direset (Tengah 35%)")
    }

    fun triggerPreviewBanner(customText: String? = null) {
        val msg = customText ?: "bonus (Semoga laris manis berkah 🤲)"
        com.tajasb.notif.OverlayBannerBroadcaster.broadcastShow(
            context = context,
            senderName = "Bang Jo",
            content = msg,
            isGift = false,
            bannerType = "COMMENT",
            iconMode = _iconMode.value.name,
            customPrefix = _customPrefix.value,
            formatMode = _formatMode.value.name,
            durationMs = 3500L,
            posXpct = _overlayPosXpct.value,
            posYpct = _overlayPosYpct.value,
            rotation = _overlayRotation.value,
            scale = _overlayScale.value,
            isMirrored = _overlayMirrored.value
        )
    }

    /**
     * Called by Accessibility Service (1-HP) or LiveEventReceiver (2-HP Broker) when a comment or gift is detected.
     */
    fun submitDetectedComment(
        senderName: String,
        text: String,
        isGift: Boolean = false,
        fromEventBroker: Boolean = false
    ): Boolean {
        val cleanName = senderName.trim().ifBlank { "Penonton" }
        val cleanText = text.trim()
        if (cleanText.isBlank()) return false

        // Normalize signature to avoid adding or triggering the same comment multiple times
        val signature = "${cleanName.lowercase()}::${cleanText.lowercase()}"

        synchronized(queueLock) {
            if (!fromEventBroker && seenSignatures.contains(signature)) {
                try {
                    CommentScanInspector.getInstance(context).logRejectedDuplicate(cleanName, cleanText)
                } catch (_: Throwable) {}
                return false
            }

            // Cap seen history at 150 items
            if (seenSignatures.size > 150) {
                val oldest = seenSignatures.iterator().next()
                seenSignatures.remove(oldest)
            }
            seenSignatures.add(signature)

            // Forward 1-HP Accessibility detected comments/gifts to InteractiveQuranKhatamEngine ONLY when 1-HP capture is enabled
            if (!fromEventBroker && _isCaptureActive.value) {
                try {
                    val quranEngine = com.tajasb.quran.InteractiveQuranKhatamEngine.getInstance(context)
                    if (quranEngine.state.value.isEnabled) {
                        if (isGift) {
                            quranEngine.onGiftEvent(cleanName, cleanText, 1)
                        } else {
                            quranEngine.onChatEvent(cleanName, cleanText)
                        }
                    }
                } catch (_: Throwable) {}
            }

            // Check if comment requests a spill video in Bridge Cycle or Master & Spill mode
            var spillTriggerMatched: String? = null
            try {
                val repo = com.tajasb.data.VcamRepository.getInstance(context)
                val cfg = repo.config.value
                val isAutoSpillAllowed = cfg.autoSpillFromComments && (_isCaptureActive.value || fromEventBroker)

                val ifsManager = com.tajasb.infinity.IfsManager.getInstance(context)
                if (ifsManager.isIfsActive.value && ifsManager.isScEnabled.value && (_isCaptureActive.value || fromEventBroker)) {
                    val matched = ifsManager.onCommentDetected(cleanName, cleanText)
                    if (matched) {
                        spillTriggerMatched = "Infinity System (IFS) Trigger"
                    }
                } else if (cfg.isBridgeCycleMode && isAutoSpillAllowed) {
                    val excluded = setOf(cfg.bridge1VideoIndex, cfg.masterVideoIndex, cfg.bridge2VideoIndex)
                    val match = repo.findMatchingSpillVideo(cleanText, excluded)
                    if (match != null) {
                        spillTriggerMatched = "Bridge II ➡️ Konten #${match.first + 1} (${match.second.name})"
                        Log.i(TAG, "🔀 [BRIDGE-CYCLE] Live comment '$cleanText' matched content video #${match.first + 1}: ${match.second.name}")
                        scope.launch {
                            repo.triggerBridgeContentVideo(match.first)
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                val isMasterPlaying = (cfg.currentVideoIndex == cfg.masterVideoIndex) || (cfg.currentBridgePhase == com.tajasb.model.BridgeCyclePhase.MASTER_LOOP)
                                val info = if (isMasterPlaying) "Tunggu Master Loop ➡️ Bridge II ➡️ Konten" else "Antre: Bridge II ➡️ Konten"
                                android.widget.Toast.makeText(context, "🔀 Komen '$cleanName': Memicu '${match.second.name}' ($info)", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else if (cfg.isMasterSpillMode && isAutoSpillAllowed) {
                    val match = repo.findMatchingSpillVideo(cleanText)
                    if (match != null) {
                        spillTriggerMatched = "Spill #${match.first + 1} (${match.second.name})"
                        Log.i(TAG, "🎯 [MASTER-SPILL] Live comment '$cleanText' ($cleanName) matched spill video #${match.first + 1}: ${match.second.name}")
                        scope.launch {
                            val waitFinish = cfg.masterSpillWaitFinish
                            repo.triggerSpillVideo(match.first, immediate = !waitFinish)
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                val waitInfo = if (waitFinish) "Tunggu Master" else "Langsung"
                                android.widget.Toast.makeText(context, "💬 Komen '$cleanName': Memicu '${match.second.name}' ($waitInfo)", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}

            // Log accepted comment into real-time inspector
            if (_isCaptureActive.value || fromEventBroker) {
                try {
                    CommentScanInspector.getInstance(context).logAccepted(cleanName, cleanText, spillTriggerMatched)
                } catch (_: Throwable) {}
            }

            // Enqueue into DOA banner queue ONLY if 1-HP capture is active or event came from LiveEventReceiver broker
            val shouldEnqueueDoa = (_isCaptureActive.value || fromEventBroker) && _bannerMode.value != BannerMode.BUYER_SALES
            if (shouldEnqueueDoa && commentQueue.size < 80) {
                val item = LiveCommentItem(
                    senderName = cleanName,
                    content = cleanText,
                    isGift = isGift
                )
                commentQueue.addLast(item)
                _queueCount.value = commentQueue.size
                _totalCapturedCount.value += 1
                startDispatcher()
                Log.d(TAG, "Enqueued live comment [Queue: ${commentQueue.size}]: $cleanName -> $cleanText")
                return true
            }
            return spillTriggerMatched != null
        }
    }

    /**
     * Skips currently displayed prayer and immediately shows next item in queue.
     */
    fun skipToNext() {
        skipJobTrigger?.cancel()
    }

    /**
     * Clears all pending comments from queue.
     */
    fun clearQueue() {
        synchronized(queueLock) {
            commentQueue.clear()
            _queueCount.value = 0
        }
        skipToNext()
    }

    /**
     * Calculates realistic reading duration:
     * - Average reading speed: ~3.5 words/second.
     * - Gifts: 5.5s (crisp spotlight).
     * - Short prayers: 6-8s.
     * - Long prayers: 14-18s.
     * - Rush hour: if queue > 6 items, accelerates smoothly so comments catch up.
     */
    fun calculateReadingDurationMs(content: String, isGift: Boolean, currentQueueSize: Int): Long {
        if (isGift) {
            return 5500L
        }

        val words = content.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val wordCount = words.size

        var durationSec = when {
            wordCount <= 3 -> 6L
            wordCount <= 7 -> 9L
            wordCount <= 14 -> 13L
            wordCount <= 22 -> 16L
            else -> 19L
        }

        // Surge acceleration
        if (currentQueueSize > 12) {
            durationSec = (durationSec * 0.65).toLong().coerceAtLeast(5L)
        } else if (currentQueueSize > 5) {
            durationSec = (durationSec * 0.8).toLong().coerceAtLeast(6L)
        }

        return durationSec * 1000L
    }

    fun startDispatcher() {
        if (dispatchJob?.isActive == true) return

        dispatchJob = scope.launch {
            Log.i(TAG, "Live Comment Queue Dispatcher started")
            while (isActive && (_isCaptureActive.value || synchronized(queueLock) { commentQueue.isNotEmpty() })) {
                var nextItem: LiveCommentItem? = null
                var currentQueueSize = 0

                synchronized(queueLock) {
                    if (commentQueue.isNotEmpty()) {
                        nextItem = commentQueue.removeFirst()
                        currentQueueSize = commentQueue.size
                        _queueCount.value = currentQueueSize
                    }
                }

                if (nextItem != null) {
                    val item = nextItem!!
                    // Proteksi Gift eksklusif: jika sedang ada gift yang tampil di layar, tunda komentar sampai gift selesai
                    val giftWaitMs = com.tajasb.eventrelay.LiveEventReceiver.getGiftRemainingMs()
                    if (giftWaitMs > 0L) {
                        delay(giftWaitMs + 200L)
                    }
                    _currentDisplayedItem.value = item

                    val durationMs = calculateReadingDurationMs(item.content, item.isGift, currentQueueSize)
                    onShowComment?.invoke(item, durationMs)

                    // Synchronize active prayer into Quran Khatam Card so sequential prayers never get overwritten prematurely
                    if (!item.isGift) {
                        try {
                            val quranEngine = com.tajasb.quran.InteractiveQuranKhatamEngine.getInstance(context)
                            if (quranEngine.state.value.isEnabled) {
                                quranEngine.setActivePrayer(item.senderName, item.content)
                            }
                        } catch (_: Throwable) {}
                    }

                    // Broadcast directly to Virtual Camera pipeline (GlVideoRenderer / MainHook)
                    com.tajasb.notif.OverlayBannerBroadcaster.broadcastShow(
                        context = context,
                        senderName = item.senderName,
                        content = item.content,
                        isGift = item.isGift,
                        bannerType = "COMMENT",
                        iconMode = _iconMode.value.name,
                        customPrefix = _customPrefix.value,
                        formatMode = _formatMode.value.name,
                        durationMs = durationMs,
                        posXpct = _overlayPosXpct.value,
                        posYpct = _overlayPosYpct.value,
                        rotation = _overlayRotation.value,
                        scale = _overlayScale.value,
                        isMirrored = _overlayMirrored.value
                    )

                    // Wait for duration or until user skips
                    try {
                        skipJobTrigger = launch {
                            delay(durationMs)
                        }
                        skipJobTrigger?.join()
                    } catch (_: CancellationException) {
                        // User requested skip
                    }

                    com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(context)

                    // Small subtle pause between comments (400ms)
                    delay(400L)
                } else {
                    // Queue empty: poll gently every 800ms
                    delay(800L)
                }
            }
        }
    }

    fun stopDispatcher() {
        dispatchJob?.cancel()
        dispatchJob = null
        skipJobTrigger?.cancel()
        _currentDisplayedItem.value = null
        onHideComment?.invoke()
        com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(context)
    }

    /**
     * Formats all current Doa & Komentar Live configuration into a clean copyable text.
     */
    fun getFormattedSettingsText(): String {
        return """
            📌 [SETTINGAN PIN DOA & KOMENTAR LIVE]
            • Status Scan: ${if (_isCaptureActive.value) "AKTIF (ON)" else "NONAKTIF (OFF)"}
            • Antrean Aktif: ${_queueCount.value} Komentar
            • Ikon Pin: ${_iconMode.value.name}
            • Prefix Custom: "${_customPrefix.value.ifBlank { "Doa" }}"
            • Format Tampilan: ${_formatMode.value.label} (${_formatMode.value.pattern})
            • Filter Area Chat: ${if (_isAreaFilterEnabled.value) "Aktif (${_scanAreaPreset.value.label})" else "Nonaktif"}
            • Koordinat Area: Atas: ${(_scanTopPct.value * 100).toInt()}%, Bawah: ${(_scanBottomPct.value * 100).toInt()}%, Kiri: ${(_scanLeftPct.value * 100).toInt()}%, Kanan: ${(_scanRightPct.value * 100).toInt()}%
            • Posisi Badge: X: ${(_overlayPosXpct.value * 100).toInt()}%, Y: ${(_overlayPosYpct.value * 100).toInt()}%
            • Skala / Ukuran: ${_overlayScale.value}x
            • Rotasi: ${_overlayRotation.value}° | Mirror: ${if (_overlayMirrored.value) "Ya" else "Tidak"}
        """.trimIndent()
    }
}
