package com.tajasb.interaction

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.min

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
        screenH: Int
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val start = toScreenPx(startNormX, startNormY, screenW, screenH)
        val clean = spec.trim().uppercase()
        val coords = clean.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (coords.size >= 2) {
            val end = toScreenPx(coords[0], coords[1], screenW, screenH)
            return ensureNonZeroSegment(start, end, screenW, screenH)
        }

        val stepPx = (min(screenW, screenH).coerceAtLeast(100) * 0.24f).coerceAtLeast(48f)
        val (dx, dy) = when {
            clean.contains("LEFT") -> -stepPx to 0f
            clean.contains("UP") -> 0f to -stepPx
            clean.contains("DOWN") -> 0f to stepPx
            else -> stepPx to 0f // RIGHT default
        }
        val endX = (start.first + dx).coerceIn(2f, (screenW - 2).coerceAtLeast(2).toFloat())
        val endY = (start.second + dy).coerceIn(2f, (screenH - 2).coerceAtLeast(2).toFloat())
        return ensureNonZeroSegment(start, endX to endY, screenW, screenH)
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

/**
 * AccessibilityService resmi Android (API 24+) untuk mengeksekusi sentuhan nyata
 * (Tap, Hold, Multi-Tap, dan Swipe) langsung ke tombol game Mobile Legends di layar.
 */
class MlbbGestureAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MlbbGestureService"

        @Volatile
        private var activeInstance: MlbbGestureAccessibilityService? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        @Volatile
        var onPrepareScreenForTouch: ((hideForTouch: Boolean) -> Unit)? = null

        fun peekInstance(): MlbbGestureAccessibilityService? = activeInstance

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

    suspend fun dispatchTap(
        normX: Float,
        normY: Float,
        holdMs: Long = 70L,
        tapCount: Int = 1
    ): Boolean {
        val (screenW, screenH) = getRealScreenSizePx()
        val (px, py) = ScreenCoordinateMapper.toScreenPx(normX, normY, screenW, screenH)
        val safeHold = holdMs.coerceIn(40L, 2500L)
        val repeats = tapCount.coerceIn(1, 10)

        var allOk = true
        val prepare = onPrepareScreenForTouch
        if (prepare != null) {
            mainHandler.post { prepare(true) }
            delay(55L)
        }
        try {
            for (i in 0 until repeats) {
                val path = Path().apply {
                    moveTo(px, py)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0L, safeHold)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val ok = dispatchGestureAsync(gesture, safeHold + 900L)
                if (!ok) allOk = false
                if (i < repeats - 1) delay(60L)
            }
        } finally {
            if (prepare != null) {
                mainHandler.post { prepare(false) }
            }
        }
        return allOk
    }

    suspend fun dispatchSwipe(
        startNormX: Float,
        startNormY: Float,
        spec: String,
        durationMs: Long = 220L
    ): Boolean {
        val (screenW, screenH) = getRealScreenSizePx()
        val (start, end) = ScreenCoordinateMapper.resolveSwipePoints(
            startNormX = startNormX,
            startNormY = startNormY,
            spec = spec,
            screenW = screenW,
            screenH = screenH
        )
        val safeDur = durationMs.coerceIn(90L, 2000L)

        val prepare = onPrepareScreenForTouch
        if (prepare != null) {
            mainHandler.post { prepare(true) }
            delay(55L)
        }
        return try {
            val path = Path().apply {
                moveTo(start.first, start.second)
                lineTo(end.first, end.second)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, safeDur)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGestureAsync(gesture, safeDur + 900L)
        } finally {
            if (prepare != null) {
                mainHandler.post { prepare(false) }
            }
        }
    }

    private suspend fun dispatchGestureAsync(
        gesture: GestureDescription,
        timeoutMs: Long
    ): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                mainHandler.post {
                    val accepted = try {
                        dispatchGesture(
                            gesture,
                            object : GestureResultCallback() {
                                override fun onCompleted(gestureDescription: GestureDescription?) {
                                    if (cont.isActive) cont.resume(true)
                                }

                                override fun onCancelled(gestureDescription: GestureDescription?) {
                                    if (cont.isActive) cont.resume(false)
                                }
                            },
                            mainHandler
                        )
                    } catch (t: Throwable) {
                        Log.e(TAG, "dispatchGesture error: ${t.message}")
                        false
                    }
                    if (!accepted && cont.isActive) {
                        cont.resume(false)
                    }
                }
            }
        } ?: false
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
