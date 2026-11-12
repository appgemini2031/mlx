@file:Suppress("DEPRECATION")

package com.tajasb

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.tajasb.livecomment.BannerMode
import com.tajasb.livecomment.CommentScanRecorder
import com.tajasb.livecomment.LiveCommentItem
import com.tajasb.livecomment.LiveCommentManager
import com.tajasb.livecomment.LiveCommentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class VcamAccessibilityService : AccessibilityService() {

    private val scannerScope = CoroutineScope(Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    private var commentScannerJob: Job? = null
    @Volatile
    private var lastScanTimeMs = 0L
    private val isScanInProgress = java.util.concurrent.atomic.AtomicBoolean(false)

    companion object {
        const val TAG = "VcamAccessibility"
        private val VIEWER_COUNT_REGEX = Regex("""^\d+([.,]\d+)?[kKmM]?$""")

        @Volatile
        var instance: VcamAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        fun isAccessibilityEnabled(context: Context): Boolean {
            try {
                val serviceName = "${context.packageName}/${VcamAccessibilityService::class.java.canonicalName}"
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServices)
                while (colonSplitter.hasNext()) {
                    val compName = colonSplitter.next()
                    if (compName.equals(serviceName, ignoreCase = true)) {
                        return true
                    }
                }
            } catch (_: Throwable) {}
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Tajasb Accessibility Service connected and ready for auto-tap gestures and live comment parsing")
        startCommentScanner()
        try {
            com.tajasb.remote.TelegramScreenMonitorManager.getInstance(applicationContext).ensureStarted()
        } catch (_: Throwable) {}
    }

    fun performTap(x: Float, y: Float, durationMs: Long = 55L, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return false
        }

        return try {
            val safeX = x.coerceAtLeast(1f)
            val safeY = y.coerceAtLeast(1f)
            val path = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX, safeY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(25L, 120L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback?.invoke(false)
                }
            }, null)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error dispatching tap gesture at ($x, $y): ${t.message}")
            callback?.invoke(false)
            false
        }
    }

    fun performDoubleTap(
        x: Float,
        y: Float,
        tapDurationMs: Long = 45L,
        intervalBetweenTapsMs: Long = 55L,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return false
        }

        return try {
            val safeX = x.coerceAtLeast(1f)
            val safeY = y.coerceAtLeast(1f)
            val path1 = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX, safeY)
            }
            val path2 = Path().apply {
                moveTo(safeX, safeY)
                lineTo(safeX, safeY)
            }
            val dur = tapDurationMs.coerceIn(25L, 75L)
            val stroke1 = GestureDescription.StrokeDescription(path1, 0L, dur)
            val stroke2 = GestureDescription.StrokeDescription(path2, dur + intervalBetweenTapsMs.coerceIn(35L, 95L), dur)

            val gesture = GestureDescription.Builder()
                .addStroke(stroke1)
                .addStroke(stroke2)
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback?.invoke(false)
                }
            }, null)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error dispatching double tap gesture at ($x, $y): ${t.message}")
            callback?.invoke(false)
            false
        }
    }

    suspend fun performDoubleTapAsync(
        x: Float,
        y: Float,
        tapDurationMs: Long = 45L,
        intervalBetweenTapsMs: Long = 55L
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val dur = tapDurationMs.coerceIn(25L, 75L)
        val first = performTapAsync(x, y, dur)
        delay(intervalBetweenTapsMs.coerceIn(35L, 95L))
        val second = performTapAsync(x, y, dur)
        return first || second
    }

    suspend fun performTapAsync(x: Float, y: Float, durationMs: Long = 55L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val timeout = (durationMs + 280L).coerceAtLeast(320L)
        return withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine { continuation ->
                val success = performTap(x, y, durationMs) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                if (!success && continuation.isActive) {
                    continuation.resume(false)
                }
            }
        } ?: false
    }

    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 280L,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return false
        }
        return try {
            val dm = resources.displayMetrics
            val maxW = maxOf(dm.widthPixels, 1080) - 5f
            val maxH = maxOf(dm.heightPixels + 250, 2400) - 5f
            val sX = startX.coerceIn(5f, maxW)
            val sY = startY.coerceIn(5f, maxH)
            val eX = endX.coerceIn(5f, maxW)
            val eY = endY.coerceIn(5f, maxH)

            val path = Path().apply {
                moveTo(sX, sY)
                lineTo(eX, eY)
            }
            val safeDuration = durationMs.coerceIn(100L, 1500L)
            val stroke = GestureDescription.StrokeDescription(path, 0L, safeDuration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback?.invoke(false)
                }
            }, null)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error dispatching swipe gesture: ${t.message}")
            callback?.invoke(false)
            false
        }
    }

    suspend fun performSwipeAsync(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 280L
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val timeout = (durationMs + 350L).coerceAtLeast(450L)
        return withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine { continuation ->
                val success = performSwipe(startX, startY, endX, endY, durationMs) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                if (!success && continuation.isActive) {
                    continuation.resume(false)
                }
            }
        } ?: false
    }

    fun performBackAction(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (t: Throwable) {
            Log.e(TAG, "Error performing BACK action: ${t.message}")
            false
        }
    }

    /**
     * Captures a silent, low-resolution JPEG screenshot via AccessibilityService.takeScreenshot (API 30+).
     * Immediately scales down and closes the HardwareBuffer so Live streaming FPS and RAM are unaffected.
     */
    suspend fun captureLowResScreenJpeg(
        maxWidthPx: Int = 420,
        quality: Int = 45
    ): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        val bgExecutor = java.util.concurrent.Executor { r ->
            CoroutineScope(Dispatchers.IO).launch { r.run() }
        }

        val lowResBitmap: android.graphics.Bitmap = withTimeoutOrNull(4500L) {
            suspendCancellableCoroutine { continuation ->
                try {
                    takeScreenshot(
                        android.view.Display.DEFAULT_DISPLAY,
                        bgExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshot: ScreenshotResult) {
                                val hwBuffer = screenshot.hardwareBuffer
                                val colorSpace = screenshot.colorSpace
                                try {
                                    val wrapped = android.graphics.Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                                    if (wrapped == null) {
                                        if (continuation.isActive) continuation.resume(null)
                                        return
                                    }
                                    val origW = wrapped.width.coerceAtLeast(1)
                                    val origH = wrapped.height.coerceAtLeast(1)
                                    val targetW = maxWidthPx.coerceIn(240, 720).coerceAtMost(origW)
                                    val targetH = ((origH.toFloat() / origW.toFloat()) * targetW).toInt().coerceAtLeast(1)

                                    val scaledHw = if (targetW < origW) {
                                        android.graphics.Bitmap.createScaledBitmap(wrapped, targetW, targetH, true)
                                    } else {
                                        wrapped
                                    }
                                    val softCopy = scaledHw.copy(android.graphics.Bitmap.Config.RGB_565, false)
                                        ?: scaledHw.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                    if (scaledHw !== wrapped) {
                                        try { scaledHw.recycle() } catch (_: Throwable) {}
                                    }
                                    try { wrapped.recycle() } catch (_: Throwable) {}

                                    if (continuation.isActive) {
                                        continuation.resume(softCopy)
                                    } else {
                                        try { softCopy?.recycle() } catch (_: Throwable) {}
                                    }
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Failed wrapping screenshot HardwareBuffer: ${t.message}")
                                    if (continuation.isActive) continuation.resume(null)
                                } finally {
                                    try { hwBuffer.close() } catch (_: Throwable) {}
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                Log.w(TAG, "Accessibility takeScreenshot failed with code: $errorCode")
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "Error invoking takeScreenshot: ${t.message}")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        } ?: return null

        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val baos = java.io.ByteArrayOutputStream(36 * 1024)
                lowResBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality.coerceIn(20, 80), baos)
                baos.toByteArray()
            } catch (t: Throwable) {
                Log.w(TAG, "Error compressing low-res screenshot: ${t.message}")
                null
            } finally {
                try { lowResBitmap.recycle() } catch (_: Throwable) {}
            }
        }
    }

    suspend fun performAutoTypeAsync(
        x: Float,
        y: Float,
        textToType: String,
        isAntiBot: Boolean = true,
        autoSendEnter: Boolean = true
    ): Boolean {
        if (textToType.isBlank()) return false
        // 1. Tap target coordinate to focus input field or open comment bar
        val tapDur = if (isAntiBot) kotlin.random.Random.nextLong(25L, 50L) else 35L
        performTapAsync(x, y, tapDur)

        // 2. Copy text to clipboard on main thread as reliable fallback & paste source
        try {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cb?.setPrimaryClip(android.content.ClipData.newPlainText("auto_type", textToType))
            }
        } catch (_: Throwable) {}

        // 3. Wait for input focus / keyboard popup (up to 2 checks for live apps like TikTok/Shopee)
        var targetNode: AccessibilityNodeInfo? = null
        val waitStages = if (isAntiBot) listOf(240L, 280L) else listOf(220L, 240L)
        for (waitMs in waitStages) {
            delay(waitMs)
            try {
                val root = rootInActiveWindow
                targetNode = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (targetNode == null || !targetNode.isEditable) {
                    if (root != null) {
                        targetNode = findBestEditableNode(root, x.toInt(), y.toInt())
                    }
                }
                if (targetNode != null && targetNode.isEditable) break
            } catch (_: Throwable) {}
        }

        // 4. Inject text and optionally trigger IME Enter / Send button
        return try {
            if (targetNode != null) {
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
                }
                var typed = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (!typed) {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    typed = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }

                if (typed && autoSendEnter) {
                    delay(if (isAntiBot) kotlin.random.Random.nextLong(120L, 240L) else 150L)
                    var sent = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        sent = targetNode.performAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
                        )
                    }
                    if (!sent) {
                        val root = rootInActiveWindow
                        if (root != null) {
                            sent = clickSendButtonNearInput(root)
                        }
                    }
                }

                try { targetNode.recycle() } catch (_: Throwable) {}
                typed
            } else {
                false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error performing auto-type: ${t.message}")
            false
        }
    }

    private fun clickSendButtonNearInput(root: AccessibilityNodeInfo): Boolean {
        val sendKeywords = listOf("kirim", "send", "posting", "post", "submit")
        for (kw in sendKeywords) {
            try {
                val matches = root.findAccessibilityNodeInfosByText(kw)
                if (!matches.isNullOrEmpty()) {
                    for (node in matches) {
                        if (node.isClickable && node.isVisibleToUser) {
                            val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (ok) return true
                        }
                        val parent = node.parent
                        if (parent != null && parent.isClickable && parent.isVisibleToUser) {
                            val ok = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (ok) return true
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        return false
    }

    private fun findBestEditableNode(
        node: AccessibilityNodeInfo?,
        targetX: Int,
        targetY: Int
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val candidates = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
        collectEditableNodes(node, targetX, targetY, candidates, 0)
        if (candidates.isEmpty()) return null
        candidates.sortBy { it.second }
        val best = candidates.first().first
        for (i in 1 until candidates.size) {
            try { candidates[i].first.recycle() } catch (_: Throwable) {}
        }
        return best
    }

    private fun collectEditableNodes(
        node: AccessibilityNodeInfo?,
        targetX: Int,
        targetY: Int,
        out: MutableList<Pair<AccessibilityNodeInfo, Int>>,
        depth: Int
    ) {
        if (node == null || depth > 22) return
        try {
            val cls = node.className?.toString() ?: ""
            if (node.isEditable || cls.contains("EditText", ignoreCase = true)) {
                val r = Rect()
                node.getBoundsInScreen(r)
                val cx = r.centerX()
                val cy = r.centerY()
                val distSq = (cx - targetX) * (cx - targetX) + (cy - targetY) * (cy - targetY)
                out.add(AccessibilityNodeInfo.obtain(node) to distSq)
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                collectEditableNodes(child, targetX, targetY, out, depth + 1)
                try { child.recycle() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }


    private class SinglePassScanState(
        val sW: Int,
        val sH: Int,
        val scanRect: Rect?,
        val inspector: com.tajasb.livecomment.CommentScanInspector
    ) {
        val headerTopLimit: Int = (sH * 0.16f).toInt()
        val headerMinX: Int = (sW * 0.50f).toInt()
        val headerMaxX: Int = (sW * 0.98f).toInt()
        val coverMinTop: Int = (sH * 0.18f).toInt()
        val coverProductMaxTop: Int = (sH * 0.40f).toInt()
        val coverScriptMaxTop: Int = (sH * 0.50f).toInt()

        var viewerAnchorRecorded: Boolean = false
        var foundProduct: Boolean = false
        var foundScriptOrAdd: Boolean = false
        var foundGmvOrClicks: Boolean = false
        val comments = ArrayList<LiveCommentItem>(12)

        fun isMenuCoveringChat(): Boolean {
            return (foundProduct && foundScriptOrAdd) || foundGmvOrClicks
        }
    }

    private fun isAnyCommentScannerConsumerActive(): Boolean {
        val manager = LiveCommentManager.getInstance(applicationContext)
        val scanRecorder = CommentScanRecorder.getInstance(applicationContext)
        val repo = try { com.tajasb.data.VcamRepository.getInstance(applicationContext) } catch (_: Exception) { null }
        val cfg = repo?.config?.value
        val isLiveCommentActive = manager.isCaptureActive.value && manager.bannerMode.value == BannerMode.LIVE_DOA_COMMENT
        val isScanRecorderActive = scanRecorder.isRecording.value
        val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(applicationContext) } catch (_: Throwable) { null }
        val isIfsScActive = ifsMgr?.isIfsActive?.value == true && ifsMgr.isScEnabled.value
        val isSpillTriggerActive = ((cfg?.autoSpillFromComments == true) && (cfg?.isMasterSpillMode == true || cfg?.isBridgeCycleMode == true)) || isIfsScActive
        val isInspectorActive = com.tajasb.livecomment.CommentScanInspector.getInstance(applicationContext).isInspectorActive.value
        return isLiveCommentActive || isScanRecorderActive || isSpillTriggerActive || isInspectorActive
    }

    private fun startCommentScanner() {
        commentScannerJob?.cancel()
        commentScannerJob = scannerScope.launch {
            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastScanTimeMs >= 500L && isAnyCommentScannerConsumerActive()) {
                        scanScreenForLiveComments()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error during live comment scan: ${e.message}")
                }
                delay(700L)
            }
        }
    }

    fun scanScreenForLiveComments() {
        if (!isScanInProgress.compareAndSet(false, true)) return
        lastScanTimeMs = System.currentTimeMillis()
        try {
            val root = rootInActiveWindow ?: return
            try {
                val manager = LiveCommentManager.getInstance(applicationContext)
                val inspector = com.tajasb.livecomment.CommentScanInspector.getInstance(applicationContext)

                val metrics = resources.displayMetrics
                val sW = metrics.widthPixels.coerceAtLeast(1)
                val sH = metrics.heightPixels.coerceAtLeast(1)

                val scanRect = if (manager.isAreaFilterEnabled.value) {
                    Rect(
                        (sW * manager.scanLeftPct.value).toInt(),
                        (sH * manager.scanTopPct.value).toInt(),
                        (sW * manager.scanRightPct.value).toInt(),
                        (sH * manager.scanBottomPct.value).toInt()
                    )
                } else {
                    null
                }

                val state = SinglePassScanState(
                    sW = sW,
                    sH = sH,
                    scanRect = scanRect,
                    inspector = inspector
                )

                // Single-pass traversal: checks viewer anchor, menu covering chat, and extracts live comments in 1 walk
                traverseSinglePass(root, state, 0)

                val isCovered = state.isMenuCoveringChat()
                inspector.setMenuCoveringChat(
                    isCovered,
                    if (isCovered) "Menu Produk/Skrip terbuka menutupi komentar" else ""
                )

                if (isCovered) {
                    return
                }

                if (state.comments.isNotEmpty()) {
                    val scanRecorder = CommentScanRecorder.getInstance(applicationContext)
                    val repo = try { com.tajasb.data.VcamRepository.getInstance(applicationContext) } catch (_: Exception) { null }
                    val cfg = repo?.config?.value
                    val isLiveActive = manager.isCaptureActive.value && manager.bannerMode.value == BannerMode.LIVE_DOA_COMMENT
                    val isRecordActive = scanRecorder.isRecording.value
                    val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(applicationContext) } catch (_: Throwable) { null }
                    val isIfsScActive = ifsMgr?.isIfsActive?.value == true && ifsMgr.isScEnabled.value
                    val isSpillTriggerActive = ((cfg?.autoSpillFromComments == true) && (cfg?.isMasterSpillMode == true || cfg?.isBridgeCycleMode == true)) || isIfsScActive
                    val isInspectorActive = inspector.isInspectorActive.value

                    for (item in state.comments) {
                        if (isLiveActive || isSpillTriggerActive || isInspectorActive) {
                            manager.submitDetectedComment(item.senderName, item.content, item.isGift)
                        }
                        if (isRecordActive) {
                            scanRecorder.recordComment(item.senderName, item.content, item.isGift)
                        }
                    }
                }
            } finally {
                try { root.recycle() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
        } finally {
            isScanInProgress.set(false)
        }
    }

    /**
     * Single-Pass Accessibility Tree Traversal:
     * 1. Records Viewer Count Anchor in top-right header (depth <= 15)
     * 2. Detects if Product/Script Drawer is covering the chat (depth <= 18) and aborts immediately if covered
     * 3. Extracts Live Comment rows (container pairs/triplets or leaf nodes) while fetching each child node ONLY ONCE
     */
    private fun traverseSinglePass(
        node: AccessibilityNodeInfo,
        state: SinglePassScanState,
        depth: Int
    ) {
        if (depth > 25 || state.isMenuCoveringChat()) return

        val nodeBounds = Rect()
        node.getBoundsInScreen(nodeBounds)
        val hasValidBounds = nodeBounds.width() > 0 && nodeBounds.height() > 0
        val rawNodeText = node.text?.toString()?.trim()

        // 1. Header Viewer Anchor Check (Top-right area)
        if (!state.viewerAnchorRecorded && depth <= 15 && nodeBounds.top <= state.headerTopLimit) {
            if (!rawNodeText.isNullOrEmpty() &&
                rawNodeText.length in 1..8 &&
                nodeBounds.centerX() in state.headerMinX..state.headerMaxX &&
                nodeBounds.centerY() in 0..state.headerTopLimit
            ) {
                if (VIEWER_COUNT_REGEX.matches(rawNodeText)) {
                    state.inspector.recordViewerAnchor(rawNodeText, nodeBounds, state.sW, state.sH)
                    state.viewerAnchorRecorded = true
                }
            }
        }

        // 2. Menu Covering Chat Check (Product / Script / GMV bottom sheet)
        if (depth <= 18 && nodeBounds.top >= state.coverMinTop && !rawNodeText.isNullOrEmpty()) {
            val lower = rawNodeText.lowercase()
            if (lower == "produk" && nodeBounds.top < state.coverProductMaxTop) {
                state.foundProduct = true
            }
            if ((lower == "skrip" || lower.contains("tambah") || lower.contains("flash sale")) &&
                nodeBounds.top < state.coverScriptMaxTop
            ) {
                state.foundScriptOrAdd = true
            }
            if (lower.contains("gmv teratribusi") ||
                lower.contains("penonton saat ini") ||
                lower.contains("jumlah klik")
            ) {
                state.foundGmvOrClicks = true
            }
            if (state.isMenuCoveringChat()) {
                return
            }
        }

        // 3. Area Filtering for Comment Extraction
        val scanRect = state.scanRect
        val intersectsScanArea = scanRect == null || !hasValidBounds || Rect.intersects(nodeBounds, scanRect)
        if (!intersectsScanArea) {
            if (!rawNodeText.isNullOrEmpty() && rawNodeText.length in 2..80) {
                state.inspector.logRejectedOutOfBounds(rawNodeText, nodeBounds, scanRect!!, state.sH)
            }
            // If this subtree is also completely outside any header/cover zone we still need, skip descending!
            val couldContainHeaderAnchor = !state.viewerAnchorRecorded && depth < 15 && nodeBounds.top <= state.headerTopLimit
            val couldContainCoverDrawer = depth < 18 && nodeBounds.bottom >= state.coverMinTop && nodeBounds.top < state.coverScriptMaxTop
            if (!couldContainHeaderAnchor && !couldContainCoverDrawer) {
                return
            }
        }

        val childCount = node.childCount
        if (childCount > 0) {
            // Fetch each child node ONCE over Binder IPC and reuse for both direct-text check and recursion
            val children = ArrayList<AccessibilityNodeInfo>(childCount.coerceAtMost(64))
            var directTexts: ArrayList<String>? = null
            try {
                for (i in 0 until childCount) {
                    val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                    children.add(child)
                    if (intersectsScanArea) {
                        val cText = child.text?.toString()?.trim()
                        if (!cText.isNullOrEmpty()) {
                            val list = directTexts ?: ArrayList<String>(6).also { directTexts = it }
                            list.add(cText)
                        }
                    }
                }

                val collectedTexts = directTexts
                if (intersectsScanArea && collectedTexts != null && collectedTexts.size in 2..6) {
                    val isInsideArea = scanRect == null || !hasValidBounds ||
                        scanRect.contains(nodeBounds.centerX(), nodeBounds.centerY()) ||
                        Rect.intersects(nodeBounds, scanRect)

                    if (isInsideArea) {
                        val parsed = LiveCommentParser.parseFromContainerTexts(collectedTexts)
                            ?: if (collectedTexts.size == 2) LiveCommentParser.parseFromNodePair(collectedTexts[0], collectedTexts[1]) else null
                        if (parsed != null) {
                            state.comments.add(parsed)
                            return // Found comment row, no need to recurse deeper into this item
                        } else {
                            val bodyCandidate = collectedTexts.drop(1).joinToString(" ").trim().ifBlank { collectedTexts.joinToString(" ") }
                            val reason = LiveCommentParser.getIgnoredReason(bodyCandidate)
                            if (reason != null) {
                                state.inspector.logRejectedSystem(bodyCandidate, reason)
                            }
                        }
                    }
                }

                for (i in 0 until children.size) {
                    if (state.isMenuCoveringChat()) break
                    traverseSinglePass(children[i], state, depth + 1)
                }
            } finally {
                for (i in 0 until children.size) {
                    try { children[i].recycle() } catch (_: Throwable) {}
                }
            }
        } else if (intersectsScanArea && !rawNodeText.isNullOrEmpty() && rawNodeText.length in 1..250) {
            val isInsideArea = scanRect == null || !hasValidBounds ||
                scanRect.contains(nodeBounds.centerX(), nodeBounds.centerY()) ||
                Rect.intersects(nodeBounds, scanRect)

            if (isInsideArea) {
                val parsed = LiveCommentParser.parseSingleString(rawNodeText)
                if (parsed != null) {
                    state.comments.add(parsed)
                } else {
                    val reason = LiveCommentParser.getIgnoredReason(rawNodeText)
                    if (reason != null) {
                        state.inspector.logRejectedSystem(rawNodeText, reason)
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName == null) return
        val now = System.currentTimeMillis()
        if (now - lastScanTimeMs < 550L || isScanInProgress.get()) return

        if (isAnyCommentScannerConsumerActive()) {
            scannerScope.launch {
                val checkNow = System.currentTimeMillis()
                if (checkNow - lastScanTimeMs >= 500L) {
                    scanScreenForLiveComments()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Tajasb Accessibility Service interrupted")
    }

    override fun onDestroy() {
        commentScannerJob?.cancel()
        commentScannerJob = null
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }
}
