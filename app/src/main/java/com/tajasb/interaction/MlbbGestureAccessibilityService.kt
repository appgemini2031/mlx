package com.tajasb.interaction

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pemetaan koordinat ternormalisasi (0.0..1.0) ke piksel layar fisik penuh
 * agar posisi pin eksternal dan sentuhan AccessibilityService cocok 1:1 di landscape maupun portrait.
 */
object ScreenCoordinateMapper {

    fun toScreenPx(
        normX: Float,
        normY: Float,
        screenW: Int,
        screenH: Int
    ): Pair<Float, Float> {
        val w = screenW.coerceAtLeast(2)
        val h = screenH.coerceAtLeast(2)
        val cx = normX.coerceIn(0.01f, 0.99f)
        val cy = normY.coerceIn(0.01f, 0.99f)
        val px = (cx * w).coerceIn(1f, (w - 1).toFloat())
        val py = (cy * h).coerceIn(1f, (h - 1).toFloat())
        return px to py
    }

    fun resolveSwipePoints(
        startNormX: Float,
        startNormY: Float,
        spec: String,
        screenW: Int,
        screenH: Int,
        angleDegreesOverride: Double? = null
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val start = toScreenPx(startNormX, startNormY, screenW, screenH)
        val clean = spec.trim().uppercase()
        val coords = clean.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (coords.size >= 2) {
            val end = toScreenPx(coords[0], coords[1], screenW, screenH)
            return ensureNonZeroSegment(start, end, screenW, screenH)
        }

        val stepPx = (min(screenW, screenH).coerceAtLeast(100) * 0.24f).coerceAtLeast(48f)
        val explicitAngle = parseAngleDegrees(clean)
        val (dx, dy) = when {
            clean == "RANDOM" || clean.contains("ACAK") -> {
                val deg = angleDegreesOverride ?: Random.nextDouble(0.0, 360.0)
                val rad = Math.toRadians(deg)
                (cos(rad) * stepPx).toFloat() to (sin(rad) * stepPx).toFloat()
            }
            explicitAngle != null -> {
                val rad = Math.toRadians(explicitAngle)
                (cos(rad) * stepPx).toFloat() to (sin(rad) * stepPx).toFloat()
            }
            clean.contains("LEFT") -> -stepPx to 0f
            clean.contains("UP") -> 0f to -stepPx
            clean.contains("DOWN") -> 0f to stepPx
            else -> stepPx to 0f // RIGHT default
        }
        val endX = (start.first + dx).coerceIn(2f, (screenW - 2).coerceAtLeast(2).toFloat())
        val endY = (start.second + dy).coerceIn(2f, (screenH - 2).coerceAtLeast(2).toFloat())
        return ensureNonZeroSegment(start, endX to endY, screenW, screenH)
    }

    private fun parseAngleDegrees(cleanUpper: String): Double? {
        if (cleanUpper.startsWith("ANGLE:")) {
            return cleanUpper.removePrefix("ANGLE:").trim().removeSuffix("°").toDoubleOrNull()
        }
        if (cleanUpper.endsWith("°")) {
            return cleanUpper.removeSuffix("°").trim().toDoubleOrNull()
        }
        if (!cleanUpper.contains(",") && cleanUpper.toDoubleOrNull() != null) {
            return cleanUpper.toDoubleOrNull()
        }
        return null
    }

    private fun ensureNonZeroSegment(
        start: Pair<Float, Float>,
        end: Pair<Float, Float>,
        screenW: Int,
        screenH: Int
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val dx = end.first - start.first
        val dy = end.second - start.second
        if (dx * dx + dy * dy >= 16f) return start to end
        val fallbackX = (start.first + 24f).coerceIn(1f, (screenW - 1).coerceAtLeast(2).toFloat())
        return start to (fallbackX to start.second)
    }
}

sealed class ScreenCaptureResult {
    data class Success(val bitmap: Bitmap) : ScreenCaptureResult()
    data class RateLimited(val waitMs: Long) : ScreenCaptureResult()
    data class Error(val reason: String) : ScreenCaptureResult()
}

enum class GestureOutcome {
    COMPLETED,
    CANCELLED_BY_TOUCH,
    FAILED
}

data class ScanRegionSpec(
    val centerNormX: Float,
    val centerNormY: Float,
    val widthRatio: Float,
    val heightRatio: Float
)

/**
 * AccessibilityService resmi Android (API 24+) untuk mengeksekusi sentuhan nyata
 * (Tap, Hold, Multi-Tap, dan Swipe) langsung ke tombol game Mobile Legends di layar.
 */
class MlbbGestureAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MlbbGestureService"
        private const val MIN_SCREENSHOT_INTERVAL_MS = 1050L

        @Volatile
        private var activeInstance: MlbbGestureAccessibilityService? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        @Volatile
        var onPrepareScreenForTouch: ((hideForTouch: Boolean) -> Unit)? = null

        @Volatile
        var onPrepareScreenForTouchRegion: ((holeRectPx: PixelRect?, active: Boolean) -> Unit)? = null

        @Volatile
        var onPrepareScreenForScan: ((hideForScan: Boolean) -> Unit)? = null

        @Volatile
        var onPrepareScreenForScanRegion: ((region: ScanRegionSpec?, hideForScan: Boolean) -> Boolean)? = null

        @Volatile
        var onSyncTouchLockOverlay: ((locked: Boolean) -> Unit)? = null

        fun peekInstance(): MlbbGestureAccessibilityService? = activeInstance

        fun currentInstance(): MlbbGestureAccessibilityService? = activeInstance

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            if (activeInstance != null) return true
            return try {
                val expected = ComponentName(context, MlbbGestureAccessibilityService::class.java)
                val flat = expected.flattenToString()
                val shortFlat = expected.flattenToShortString()
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()
                enabled.split(':').any { entry ->
                    entry.equals(flat, ignoreCase = true) || entry.equals(shortFlat, ignoreCase = true)
                }
            } catch (_: Throwable) {
                false
            }
        }

        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Gagal buka Accessibility Settings: ${t.message}")
            }
        }
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var cachedHomeLauncherPackage: String? = null

    @Volatile
    private var lastScreenshotRequestAtMs: Long = 0L

    @Volatile
    var lastForegroundAppPackage: String? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        cachedHomeLauncherPackage = resolveHomeLauncherPackage()
        _isConnected.value = true
        Log.i(TAG, "MlbbGestureAccessibilityService terhubung")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()?.trim().orEmpty()
        if (pkg.isEmpty()) return
        if (pkg == packageName) return
        val homePkg = cachedHomeLauncherPackage ?: resolveHomeLauncherPackage().also { cachedHomeLauncherPackage = it }
        val ignored = pkg == "com.android.systemui" ||
            (homePkg != null && pkg == homePkg) ||
            pkg.contains("launcher", ignoreCase = true) ||
            pkg.contains("miui.home", ignoreCase = true) ||
            pkg.contains("inputmethod", ignoreCase = true)
        if (!ignored) {
            lastForegroundAppPackage = pkg
        }
    }

    private fun resolveHomeLauncherPackage(): String? {
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName
        } catch (_: Throwable) {
            null
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "MlbbGestureAccessibilityService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (activeInstance === this) {
            activeInstance = null
        }
        _isConnected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        _isConnected.value = false
        super.onDestroy()
    }

    fun goHome(): Boolean {
        return try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal performGlobalAction HOME: ${t.message}")
            false
        }
    }

    fun bringGameToFront(context: Context, preferredPackage: String = ""): Boolean {
        val candidates = buildList {
            if (preferredPackage.isNotBlank()) add(preferredPackage.trim())
            lastForegroundAppPackage?.takeIf { it.isNotBlank() }?.let { add(it) }
            add("com.mobile.legends")
            add("com.mobile.legends.hwag")
            add("com.vng.mlbbvn")
        }.distinct()

        for (pkg in candidates) {
            val launch = try {
                context.packageManager.getLaunchIntentForPackage(pkg)
            } catch (_: Throwable) {
                null
            }
            if (launch != null) {
                launch.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                return try {
                    context.startActivity(launch)
                    true
                } catch (t: Throwable) {
                    Log.e(TAG, "Gagal kembali ke $pkg: ${t.message}")
                    false
                }
            }
        }
        return false
    }

    private fun buildTouchHoleRect(
        points: List<Pair<Float, Float>>,
        screenW: Int,
        screenH: Int
    ): PixelRect {
        val pad = (min(screenW, screenH) * 0.032f).roundToInt().coerceIn(24, 54)
        val minX = points.minOf { it.first }.roundToInt()
        val maxX = points.maxOf { it.first }.roundToInt()
        val minY = points.minOf { it.second }.roundToInt()
        val maxY = points.maxOf { it.second }.roundToInt()
        return PixelRect(
            left = (minX - pad).coerceIn(0, screenW),
            top = (minY - pad).coerceIn(0, screenH),
            right = (maxX + pad).coerceIn(0, screenW),
            bottom = (maxY + pad).coerceIn(0, screenH)
        )
    }

    private suspend fun awaitMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                block()
            } catch (_: Throwable) {}
            return
        }
        val done = CompletableDeferred<Unit>()
        mainHandler.post {
            try {
                block()
            } catch (_: Throwable) {
            } finally {
                done.complete(Unit)
            }
        }
        withTimeoutOrNull(450L) { done.await() }
    }

    suspend fun syncTouchLockOverlayNow(locked: Boolean) {
        val hook = onSyncTouchLockOverlay ?: return
        awaitMain { hook(locked) }
        if (locked) {
            // Beri 34ms agar panel pemblokir sentuhan terpasang penuh di SurfaceFlinger sebelum gesture masuk
            delay(34L)
        }
    }

    private suspend fun prepareTouchWindow(holeRect: PixelRect?) {
        val prepareRegion = onPrepareScreenForTouchRegion
        val prepareFallback = onPrepareScreenForTouch
        if (prepareRegion != null) {
            awaitMain { prepareRegion(holeRect, true) }
            delay(28L)
        } else if (prepareFallback != null) {
            awaitMain { prepareFallback(true) }
            delay(65L)
        }
    }

    private suspend fun releaseTouchWindow() {
        val prepareRegion = onPrepareScreenForTouchRegion
        val prepareFallback = onPrepareScreenForTouch
        if (prepareRegion != null) {
            awaitMain { prepareRegion(null, false) }
        } else if (prepareFallback != null) {
            awaitMain { prepareFallback(false) }
        }
    }

    suspend fun dispatchTap(
        normX: Float,
        normY: Float,
        holdMs: Long = 0L,
        tapCount: Int = 1
    ): Boolean = dispatchTapOutcome(normX, normY, holdMs, tapCount) == GestureOutcome.COMPLETED

    suspend fun dispatchTapOutcome(
        normX: Float,
        normY: Float,
        holdMs: Long = 0L,
        tapCount: Int = 1
    ): GestureOutcome {
        val (screenW, screenH) = getRealScreenSizePx()
        val (px, py) = ScreenCoordinateMapper.toScreenPx(normX, normY, screenW, screenH)
        val safeHold = if (holdMs <= 0L) 38L else holdMs.coerceIn(38L, 12_000L)
        val repeats = tapCount.coerceIn(1, 10)
        val holeRect = buildTouchHoleRect(listOf(px to py), screenW, screenH)

        var lastOutcome = GestureOutcome.COMPLETED
        prepareTouchWindow(holeRect)
        try {
            for (i in 0 until repeats) {
                val path = Path().apply {
                    moveTo(px, py)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0L, safeHold)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val outcome = dispatchGestureOutcomeAsync(gesture, safeHold + 900L)
                if (outcome != GestureOutcome.COMPLETED) {
                    lastOutcome = outcome
                    if (outcome == GestureOutcome.CANCELLED_BY_TOUCH) {
                        return GestureOutcome.CANCELLED_BY_TOUCH
                    }
                }
                if (i < repeats - 1) delay(60L)
            }
        } finally {
            releaseTouchWindow()
        }
        return lastOutcome
    }

    /**
     * Tahan di titik awal selama [holdMs] (jika >0), lalu masih dalam keadaan menekan/tahan
     * menggeser ke [dragSpec] selama [dragDurationMs], kemudian melepas jari.
     */
    suspend fun dispatchHoldAndDrag(
        normX: Float,
        normY: Float,
        holdMs: Long,
        holdAndDrag: Boolean,
        dragSpec: String,
        dragDurationMs: Long = 220L
    ): Boolean = dispatchHoldAndDragOutcome(
        normX = normX,
        normY = normY,
        holdMs = holdMs,
        holdAndDrag = holdAndDrag,
        dragSpec = dragSpec,
        dragDurationMs = dragDurationMs
    ) == GestureOutcome.COMPLETED

    suspend fun dispatchHoldAndDragOutcome(
        normX: Float,
        normY: Float,
        holdMs: Long,
        holdAndDrag: Boolean,
        dragSpec: String,
        dragDurationMs: Long = 220L
    ): GestureOutcome {
        if (!holdAndDrag) {
            return dispatchTapOutcome(normX, normY, holdMs = holdMs, tapCount = 1)
        }
        if (holdMs <= 0L) {
            return dispatchSwipeOutcome(normX, normY, dragSpec, durationMs = dragDurationMs)
        }

        val (screenW, screenH) = getRealScreenSizePx()
        val (start, end) = ScreenCoordinateMapper.resolveSwipePoints(
            startNormX = normX,
            startNormY = normY,
            spec = dragSpec.ifBlank { "RIGHT" },
            screenW = screenW,
            screenH = screenH
        )
        val safeHold = holdMs.coerceIn(40L, 12_000L)
        val safeDrag = dragDurationMs.coerceIn(60L, 5000L)
        val midX = (start.first + 0.5f).coerceIn(1f, (screenW - 1).coerceAtLeast(2).toFloat())
        val midY = start.second
        val holeRect = buildTouchHoleRect(listOf(start, midX to midY, end), screenW, screenH)

        prepareTouchWindow(holeRect)
        return try {
            val holdPath = Path().apply {
                moveTo(start.first, start.second)
                lineTo(midX, midY)
            }
            val holdStroke = GestureDescription.StrokeDescription(holdPath, 0L, safeHold, true)
            val holdGesture = GestureDescription.Builder().addStroke(holdStroke).build()
            val dragPath = Path().apply {
                moveTo(midX, midY)
                lineTo(end.first, end.second)
            }
            // Rangkai continueStroke langsung di dalam onCompleted mainHandler tanpa jeda coroutine
            withTimeoutOrNull(safeHold + safeDrag + 1600L) {
                suspendCancellableCoroutine { cont ->
                    mainHandler.post {
                        val acceptedHold = try {
                            dispatchGesture(
                                holdGesture,
                                object : GestureResultCallback() {
                                    override fun onCompleted(gestureDescription: GestureDescription?) {
                                        val dragStroke = holdStroke.continueStroke(dragPath, 0L, safeDrag, false)
                                        val dragGesture = GestureDescription.Builder().addStroke(dragStroke).build()
                                        val acceptedDrag = try {
                                            dispatchGesture(
                                                dragGesture,
                                                object : GestureResultCallback() {
                                                    override fun onCompleted(gd: GestureDescription?) {
                                                        if (cont.isActive) cont.resume(GestureOutcome.COMPLETED)
                                                    }

                                                    override fun onCancelled(gd: GestureDescription?) {
                                                        if (cont.isActive) cont.resume(GestureOutcome.CANCELLED_BY_TOUCH)
                                                    }
                                                },
                                                mainHandler
                                            )
                                        } catch (t: Throwable) {
                                            Log.e(TAG, "continueStroke drag error: ${t.message}")
                                            false
                                        }
                                        if (!acceptedDrag && cont.isActive) {
                                            cont.resume(GestureOutcome.FAILED)
                                        }
                                    }

                                    override fun onCancelled(gestureDescription: GestureDescription?) {
                                        if (cont.isActive) cont.resume(GestureOutcome.CANCELLED_BY_TOUCH)
                                    }
                                },
                                mainHandler
                            )
                        } catch (t: Throwable) {
                            Log.e(TAG, "dispatchHoldAndDrag error: ${t.message}")
                            false
                        }
                        if (!acceptedHold && cont.isActive) {
                            cont.resume(GestureOutcome.FAILED)
                        }
                    }
                }
            } ?: GestureOutcome.FAILED
        } finally {
            releaseTouchWindow()
        }
    }

    suspend fun dispatchSwipe(
        startNormX: Float,
        startNormY: Float,
        spec: String,
        durationMs: Long = 220L
    ): Boolean = dispatchSwipeOutcome(startNormX, startNormY, spec, durationMs) == GestureOutcome.COMPLETED

    suspend fun dispatchSwipeOutcome(
        startNormX: Float,
        startNormY: Float,
        spec: String,
        durationMs: Long = 220L
    ): GestureOutcome {
        val (screenW, screenH) = getRealScreenSizePx()
        val (start, end) = ScreenCoordinateMapper.resolveSwipePoints(
            startNormX = startNormX,
            startNormY = startNormY,
            spec = spec,
            screenW = screenW,
            screenH = screenH
        )
        val safeDur = durationMs.coerceIn(60L, 5000L)
        val holeRect = buildTouchHoleRect(listOf(start, end), screenW, screenH)

        prepareTouchWindow(holeRect)
        return try {
            val path = Path().apply {
                moveTo(start.first, start.second)
                lineTo(end.first, end.second)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, safeDur)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGestureOutcomeAsync(gesture, safeDur + 900L)
        } finally {
            releaseTouchWindow()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureScreenRegionBitmap(
        centerNormX: Float,
        centerNormY: Float,
        widthRatio: Float,
        heightRatio: Float
    ): Bitmap? {
        return when (val res = captureScreenRegionResult(centerNormX, centerNormY, widthRatio, heightRatio)) {
            is ScreenCaptureResult.Success -> res.bitmap
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureScreenRegionResult(
        centerNormX: Float,
        centerNormY: Float,
        widthRatio: Float,
        heightRatio: Float
    ): ScreenCaptureResult {
        val now = System.currentTimeMillis()
        val sinceLast = now - lastScreenshotRequestAtMs
        if (lastScreenshotRequestAtMs > 0L && sinceLast < MIN_SCREENSHOT_INTERVAL_MS) {
            return ScreenCaptureResult.RateLimited((MIN_SCREENSHOT_INTERVAL_MS - sinceLast).coerceAtLeast(180L))
        }
        lastScreenshotRequestAtMs = now

        return withTimeoutOrNull(2200L) {
            suspendCancellableCoroutine { cont ->
                try {
                    takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshot: ScreenshotResult) {
                                var hwBitmap: Bitmap? = null
                                val hb = screenshot.hardwareBuffer
                                val cropped = try {
                                    hwBitmap = Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace)
                                    if (hwBitmap == null) {
                                        null
                                    } else {
                                        val fullW = hwBitmap.width
                                        val fullH = hwBitmap.height
                                        val rw = (fullW * widthRatio.coerceIn(0.025f, 0.45f)).toInt().coerceIn(28, fullW)
                                        val rh = (fullH * heightRatio.coerceIn(0.025f, 0.55f)).toInt().coerceIn(28, fullH)
                                        val cx = (centerNormX.coerceIn(0.02f, 0.98f) * fullW).toInt()
                                        val cy = (centerNormY.coerceIn(0.02f, 0.98f) * fullH).toInt()
                                        val left = (cx - rw / 2).coerceIn(0, (fullW - rw).coerceAtLeast(0))
                                        val top = (cy - rh / 2).coerceIn(0, (fullH - rh).coerceAtLeast(0))
                                        val out = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                                        val swCopy = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                        if (swCopy != null) {
                                            val canvas = Canvas(out)
                                            canvas.drawBitmap(
                                                swCopy,
                                                Rect(left, top, left + rw, top + rh),
                                                Rect(0, 0, rw, rh),
                                                null
                                            )
                                            swCopy.recycle()
                                            out
                                        } else {
                                            out.recycle()
                                            null
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Crop screenshot error: ${t.message}")
                                    null
                                } finally {
                                    try { hwBitmap?.recycle() } catch (_: Throwable) {}
                                    try { hb.close() } catch (_: Throwable) {}
                                }
                                if (cont.isActive) {
                                    if (cropped != null) {
                                        cont.resume(ScreenCaptureResult.Success(cropped))
                                    } else {
                                        cont.resume(ScreenCaptureResult.Error("Gagal memproses gambar layar"))
                                    }
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                Log.w(TAG, "takeScreenshot failed code=$errorCode")
                                val result = when (errorCode) {
                                    ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
                                        ScreenCaptureResult.RateLimited(1100L)
                                    ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
                                        ScreenCaptureResult.Error("Layar terproteksi (FLAG_SECURE)")
                                    else ->
                                        ScreenCaptureResult.Error("Gagal ambil screenshot (kode $errorCode)")
                                }
                                if (cont.isActive) cont.resume(result)
                            }
                        }
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "takeScreenshot exception: ${t.message}")
                    if (cont.isActive) {
                        cont.resume(ScreenCaptureResult.Error("Exception screenshot: ${t.message}"))
                    }
                }
            }
        } ?: ScreenCaptureResult.Error("Timeout ambil screenshot layar")
    }

    private suspend fun dispatchGestureOutcomeAsync(
        gesture: GestureDescription,
        timeoutMs: Long
    ): GestureOutcome {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                mainHandler.post {
                    val accepted = try {
                        dispatchGesture(
                            gesture,
                            object : GestureResultCallback() {
                                override fun onCompleted(gestureDescription: GestureDescription?) {
                                    if (cont.isActive) cont.resume(GestureOutcome.COMPLETED)
                                }

                                override fun onCancelled(gestureDescription: GestureDescription?) {
                                    if (cont.isActive) cont.resume(GestureOutcome.CANCELLED_BY_TOUCH)
                                }
                            },
                            mainHandler
                        )
                    } catch (t: Throwable) {
                        Log.e(TAG, "dispatchGesture error: ${t.message}")
                        false
                    }
                    if (!accepted && cont.isActive) {
                        cont.resume(GestureOutcome.FAILED)
                    }
                }
            }
        } ?: GestureOutcome.FAILED
    }

    private fun getRealScreenSizePx(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width().coerceAtLeast(2) to bounds.height().coerceAtLeast(2)
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay?.getRealMetrics(dm)
            dm.widthPixels.coerceAtLeast(2) to dm.heightPixels.coerceAtLeast(2)
        }
    }
}
