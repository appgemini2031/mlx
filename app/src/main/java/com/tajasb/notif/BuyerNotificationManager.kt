package com.tajasb.notif

import android.content.Context
import android.content.SharedPreferences
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
import kotlin.random.Random

/**
 * Data payload for generated sales notification.
 */
data class SalesNotificationPayload(
    val senderName: String,
    val actionText: String
)

/**
 * BuyerNotificationManager:
 * Manages periodic realistic fake buyer/order notification popups on TikTok live streams.
 * Matches exact TikTok live buyer pill badge style:
 * - Rounded translucent golden-amber yellow pill background (#D9EAA728)
 * - White shopping bag icon with top arch handle and dual dot cutouts
 * - Formats:
 *   1) "{char}***{char} sedang membeli" (strict 3-star censored username)
 *   2) "{count} orang sedang melihat produk No. {1-6}"
 *   3) "{count} orang telah membeli produk dari LIVE ini"
 *   4) Mixed dynamic rotation of all 3 templates
 * - Customizable viewer/buyer count range (1-10, 10-20, 20-30)
 * - Slide-in animation, 3.84s duration, smooth fade out exit.
 * - Zero lag / optimized coroutine scheduler.
 */
class BuyerNotificationManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "buyer_notif_prefs"
        private const val KEY_ENABLED = "key_buyer_notif_enabled"
        private const val KEY_DURATION_MS = "key_buyer_notif_duration_ms"
        private const val KEY_MIN_INTERVAL = "key_buyer_notif_min_interval"
        private const val KEY_MAX_INTERVAL = "key_buyer_notif_max_interval"
        private const val KEY_ACTION_TEXT = "key_buyer_notif_action_text"
        private const val KEY_SALES_FORMAT = "key_buyer_notif_sales_format"
        private const val KEY_COUNT_RANGE = "key_buyer_notif_count_range"
        private const val KEY_COUNT_MIN = "key_buyer_notif_count_min"
        private const val KEY_COUNT_MAX = "key_buyer_notif_count_max"
        private const val KEY_PRODUCT_MIN = "key_buyer_notif_product_min"
        private const val KEY_PRODUCT_MAX = "key_buyer_notif_product_max"
        private const val KEY_POS_X = "key_buyer_notif_pos_x"
        private const val KEY_POS_Y = "key_buyer_notif_pos_y"
        private const val KEY_POS_LOCKED = "key_buyer_notif_pos_locked"
        private const val KEY_ROTATION = "key_buyer_notif_rotation"
        private const val KEY_SCALE = "key_buyer_notif_scale"
        private const val KEY_MIRRORED = "key_buyer_notif_mirrored"
        private const val KEY_ENTRY_DIRECTION = "key_buyer_notif_entry_dir"
        private const val KEY_PRODUCT_NO_MODE = "key_buyer_notif_product_no_mode"
        private const val KEY_CAPSULE_CORNER_RADIUS = "key_buyer_notif_capsule_corner_radius"
        private const val KEY_CAPSULE_PADDING_H = "key_buyer_notif_capsule_padding_h"
        private const val KEY_CAPSULE_PADDING_V = "key_buyer_notif_capsule_padding_v"
        private const val KEY_CAPSULE_ALPHA = "key_buyer_notif_capsule_alpha"
        private const val KEY_CAPSULE_OUTLINE_WIDTH = "key_buyer_notif_capsule_outline_width"
        private const val KEY_CAPSULE_OUTLINE_COLOR = "key_buyer_notif_capsule_outline_color"
        private const val KEY_CAPSULE_BG_COLOR = "key_buyer_notif_capsule_bg_color"
        private const val KEY_FONT_SIZE_SP = "key_buyer_notif_font_size_sp"
        private const val KEY_CART_ICON_SIZE_DP = "key_buyer_notif_cart_icon_size_dp"
        private const val KEY_CONFIG_VERSION = "key_buyer_notif_config_version"
        private const val CURRENT_CONFIG_VERSION = 2

        // Default exact duration: 2840ms
        const val DEFAULT_DURATION_MS = 2840L

        // Format modes
        const val FORMAT_MIXED = "MIXED"
        const val FORMAT_BUYING = "BUYING"
        const val FORMAT_VIEWING = "VIEWING"
        const val FORMAT_BOUGHT = "BOUGHT"

        // Count range presets
        const val RANGE_1_10 = "1_10"
        const val RANGE_10_20 = "10_20"
        const val RANGE_20_30 = "20_30"

        @Volatile
        private var instance: BuyerNotificationManager? = null

        fun getInstance(context: Context): BuyerNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: BuyerNotificationManager(context.applicationContext).also { instance = it }
            }
        }

        // Realistic Indonesian buyer names pool: strictly exactly 3 stars (e.g. r***8, a***3)
        private val BUYER_NAMES = listOf(
            "r***8", "a***3", "m***9", "d***2", "s***1",
            "b***7", "f***5", "k***0", "n***6", "z***4",
            "w***8", "i***2", "y***7", "h***1", "t***9",
            "e***5", "g***3", "j***6", "l***4", "p***7",
            "c***2", "v***0", "u***8", "o***3", "x***9",
            "d***i", "r***y", "a***s", "s***i", "b***i",
            "p***i", "r***t", "a***a", "b***u", "m***a",
            "f***r", "f***i", "d***n", "h***a", "l***a",
            "w***u", "m***a", "y***o", "r***a", "a***i",
            "d***a", "j***o", "n***r", "e***o", "t***i",
            "s***t", "a***i", "n***i", "i***m", "t***a",
            "b***s", "c***a", "t***k", "r***a", "d***s",
            "l***a", "r***y", "a***u", "i***a", "w***n",
            "f***y", "n***a", "r***a", "a***d", "d***y",
            "s***i", "d***i", "r***u", "v***a", "a***y",
            "1***9", "7***a", "9***x", "3***b", "5***k",
            "8***m", "2***d", "4***s", "6***z", "0***w"
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loopJob: Job? = null

    init {
        if (!prefs.getBoolean("key_buyer_notif_defaults_migrated_v3_sepuh", false)) {
            prefs.edit()
                .putBoolean(KEY_ENABLED, false)
                .putString(KEY_SALES_FORMAT, FORMAT_MIXED)
                .putString(KEY_PRODUCT_NO_MODE, "1")
                .putString(KEY_ACTION_TEXT, "sedang membeli")
                .putInt(KEY_COUNT_MIN, 1)
                .putInt(KEY_COUNT_MAX, 12)
                .putInt(KEY_PRODUCT_MIN, 1)
                .putInt(KEY_PRODUCT_MAX, 1)
                .putInt(KEY_MIN_INTERVAL, 3)
                .putInt(KEY_MAX_INTERVAL, 6)
                .putLong(KEY_DURATION_MS, 1200L)
                .putString(KEY_CAPSULE_BG_COLOR, "#FF941A")
                .putFloat(KEY_CAPSULE_ALPHA, 0.58f)
                .putInt(KEY_CAPSULE_OUTLINE_WIDTH, 0)
                .putString(KEY_CAPSULE_OUTLINE_COLOR, "#FFFFFF")
                .putInt(KEY_CAPSULE_CORNER_RADIUS, 75)
                .putInt(KEY_CAPSULE_PADDING_H, 27)
                .putInt(KEY_CAPSULE_PADDING_V, 3)
                .putFloat(KEY_FONT_SIZE_SP, 16.0f)
                .putFloat(KEY_CART_ICON_SIZE_DP, 21.0f)
                .putInt(KEY_POS_X, 114)
                .putInt(KEY_POS_Y, 1409)
                .putBoolean(KEY_POS_LOCKED, true)
                .putFloat(KEY_SCALE, 0.85f)
                .putInt(KEY_ROTATION, 0)
                .putBoolean(KEY_MIRRORED, false)
                .putString(KEY_ENTRY_DIRECTION, "LEFT")
                .putInt(KEY_CONFIG_VERSION, CURRENT_CONFIG_VERSION)
                .putBoolean("key_buyer_notif_defaults_migrated_v3_sepuh", true)
                .apply()
        }
    }

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _displayDurationMs = MutableStateFlow(prefs.getLong(KEY_DURATION_MS, 1200L))
    val displayDurationMs: StateFlow<Long> = _displayDurationMs.asStateFlow()

    private val _minIntervalSeconds = MutableStateFlow(prefs.getInt(KEY_MIN_INTERVAL, 3))
    val minIntervalSeconds: StateFlow<Int> = _minIntervalSeconds.asStateFlow()

    private val _maxIntervalSeconds = MutableStateFlow(prefs.getInt(KEY_MAX_INTERVAL, 6))
    val maxIntervalSeconds: StateFlow<Int> = _maxIntervalSeconds.asStateFlow()

    private val _actionText = MutableStateFlow(prefs.getString(KEY_ACTION_TEXT, "sedang membeli") ?: "sedang membeli")
    val actionText: StateFlow<String> = _actionText.asStateFlow()

    private val _salesFormat = MutableStateFlow(prefs.getString(KEY_SALES_FORMAT, FORMAT_MIXED) ?: FORMAT_MIXED)
    val salesFormat: StateFlow<String> = _salesFormat.asStateFlow()

    private val _countRange = MutableStateFlow(prefs.getString(KEY_COUNT_RANGE, RANGE_1_10) ?: RANGE_1_10)
    val countRange: StateFlow<String> = _countRange.asStateFlow()

    private val _countMin = MutableStateFlow(
        prefs.getInt(
            KEY_COUNT_MIN,
            when (prefs.getString(KEY_COUNT_RANGE, RANGE_1_10)) {
                RANGE_10_20 -> 10
                RANGE_20_30 -> 20
                else -> 1
            }
        )
    )
    val countMin: StateFlow<Int> = _countMin.asStateFlow()

    private val _countMax = MutableStateFlow(
        prefs.getInt(
            KEY_COUNT_MAX,
            when (prefs.getString(KEY_COUNT_RANGE, RANGE_1_10)) {
                RANGE_10_20 -> 20
                RANGE_20_30 -> 30
                else -> 12
            }
        )
    )
    val countMax: StateFlow<Int> = _countMax.asStateFlow()

    // Default product number after "No." is 1 ("No. 1")
    private val _productNoMode = MutableStateFlow(prefs.getString(KEY_PRODUCT_NO_MODE, "1") ?: "1")
    val productNoMode: StateFlow<String> = _productNoMode.asStateFlow()

    private val _productMin = MutableStateFlow(
        prefs.getInt(KEY_PRODUCT_MIN, prefs.getString(KEY_PRODUCT_NO_MODE, "1")?.toIntOrNull() ?: 1)
    )
    val productMin: StateFlow<Int> = _productMin.asStateFlow()

    private val _productMax = MutableStateFlow(
        prefs.getInt(KEY_PRODUCT_MAX, if (prefs.getString(KEY_PRODUCT_NO_MODE, "1").equals("ACAK", ignoreCase = true)) 10 else (prefs.getString(KEY_PRODUCT_NO_MODE, "1")?.toIntOrNull() ?: 1))
    )
    val productMax: StateFlow<Int> = _productMax.asStateFlow()

    private val _isPositionLocked = MutableStateFlow(prefs.getBoolean(KEY_POS_LOCKED, true))
    val isPositionLocked: StateFlow<Boolean> = _isPositionLocked.asStateFlow()

    // Default position: X: 114px, Y: 1409px
    private val defaultX = 114
    private val defaultY = 1409

    private val _posX = MutableStateFlow(prefs.getInt(KEY_POS_X, defaultX))
    val posX: StateFlow<Int> = _posX.asStateFlow()

    private val _posY = MutableStateFlow(prefs.getInt(KEY_POS_Y, defaultY))
    val posY: StateFlow<Int> = _posY.asStateFlow()

    private val _rotation = MutableStateFlow(prefs.getInt(KEY_ROTATION, 0))
    val rotation: StateFlow<Int> = _rotation.asStateFlow()

    private val _scale = MutableStateFlow(prefs.getFloat(KEY_SCALE, 0.85f))
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _isMirrored = MutableStateFlow(prefs.getBoolean(KEY_MIRRORED, false))
    val isMirrored: StateFlow<Boolean> = _isMirrored.asStateFlow()

    private val _entryDirection = MutableStateFlow(prefs.getString(KEY_ENTRY_DIRECTION, "LEFT") ?: "LEFT")
    val entryDirection: StateFlow<String> = _entryDirection.asStateFlow()

    // Custom precision capsule parameters (Corner radius & padding)
    private val _capsuleCornerRadius = MutableStateFlow(prefs.getInt(KEY_CAPSULE_CORNER_RADIUS, 75))
    val capsuleCornerRadius: StateFlow<Int> = _capsuleCornerRadius.asStateFlow()

    private val _capsulePaddingH = MutableStateFlow(prefs.getInt(KEY_CAPSULE_PADDING_H, 27))
    val capsulePaddingH: StateFlow<Int> = _capsulePaddingH.asStateFlow()

    private val _capsulePaddingV = MutableStateFlow(prefs.getInt(KEY_CAPSULE_PADDING_V, 3))
    val capsulePaddingV: StateFlow<Int> = _capsulePaddingV.asStateFlow()

    // Capsule Transparency (0.10f to 1.0f) - Default 0.58f
    private val _capsuleAlpha = MutableStateFlow(prefs.getFloat(KEY_CAPSULE_ALPHA, 0.58f))
    val capsuleAlpha: StateFlow<Float> = _capsuleAlpha.asStateFlow()

    // Capsule Outline / Border Stroke Width (0 = no border, 1 to 10 dp)
    private val _capsuleOutlineWidth = MutableStateFlow(prefs.getInt(KEY_CAPSULE_OUTLINE_WIDTH, 0))
    val capsuleOutlineWidth: StateFlow<Int> = _capsuleOutlineWidth.asStateFlow()

    // Capsule Outline Color (Hex code, e.g. #FFFFFF)
    private val _capsuleOutlineColor = MutableStateFlow(prefs.getString(KEY_CAPSULE_OUTLINE_COLOR, "#FFFFFF") ?: "#FFFFFF")
    val capsuleOutlineColor: StateFlow<String> = _capsuleOutlineColor.asStateFlow()

    // Capsule Background Custom Color (Hex code, e.g. #FF941A)
    private val _capsuleBgColorHex = MutableStateFlow(prefs.getString(KEY_CAPSULE_BG_COLOR, "#FF941A") ?: "#FF941A")
    val capsuleBgColorHex: StateFlow<String> = _capsuleBgColorHex.asStateFlow()

    // Independent Font Size (sp) - 16.0sp
    private val _fontSizeSp = MutableStateFlow(prefs.getFloat(KEY_FONT_SIZE_SP, 16.0f))
    val fontSizeSp: StateFlow<Float> = _fontSizeSp.asStateFlow()

    // Independent Shopping Cart Icon Size (dp) - 21.0dp
    private val _cartIconSizeDp = MutableStateFlow(prefs.getFloat(KEY_CART_ICON_SIZE_DP, 21.0f))
    val cartIconSizeDp: StateFlow<Float> = _cartIconSizeDp.asStateFlow()

    // Live display state
    private val _currentBuyerName = MutableStateFlow("r***8")
    val currentBuyerName: StateFlow<String> = _currentBuyerName.asStateFlow()

    private val _isNotifVisible = MutableStateFlow(false)
    val isNotifVisible: StateFlow<Boolean> = _isNotifVisible.asStateFlow()

    private val _totalTriggeredCount = MutableStateFlow(0)
    val totalTriggeredCount: StateFlow<Int> = _totalTriggeredCount.asStateFlow()

    // Callback for overlay UI animation and styling
    var onShowNotification: ((buyerName: String, actionText: String, durationMs: Long) -> Unit)? = null
    var onHideNotification: (() -> Unit)? = null
    var onCapsuleStyleChanged: (() -> Unit)? = null

    init {
        if (_isEnabled.value) {
            startLoop()
        }
    }

    fun toggleEnabled(enabled: Boolean? = null): Boolean {
        val next = enabled ?: !_isEnabled.value
        _isEnabled.value = next
        prefs.edit().putBoolean(KEY_ENABLED, next).apply()

        if (next) {
            startLoop()
        } else {
            stopLoop()
        }
        return next
    }

    fun setSalesFormat(format: String) {
        val clean = when (format.uppercase()) {
            FORMAT_BUYING -> FORMAT_BUYING
            FORMAT_VIEWING -> FORMAT_VIEWING
            FORMAT_BOUGHT -> FORMAT_BOUGHT
            else -> FORMAT_MIXED
        }
        _salesFormat.value = clean
        prefs.edit().putString(KEY_SALES_FORMAT, clean).apply()
        broadcastCurrentState()
    }

    fun setCountMinMax(minVal: Int, maxVal: Int) {
        val safeMin = minOf(minVal, maxVal).coerceAtLeast(1)
        val safeMax = maxOf(minVal, maxVal).coerceAtLeast(safeMin)
        _countMin.value = safeMin
        _countMax.value = safeMax
        _countRange.value = "${safeMin}_${safeMax}"
        prefs.edit()
            .putInt(KEY_COUNT_MIN, safeMin)
            .putInt(KEY_COUNT_MAX, safeMax)
            .putString(KEY_COUNT_RANGE, "${safeMin}_${safeMax}")
            .apply()
        broadcastCurrentState()
    }

    fun setProductMinMax(minVal: Int, maxVal: Int) {
        val safeMin = minOf(minVal, maxVal).coerceAtLeast(1)
        val safeMax = maxOf(minVal, maxVal).coerceAtLeast(safeMin)
        _productMin.value = safeMin
        _productMax.value = safeMax
        val modeStr = if (safeMin == safeMax) safeMin.toString() else "ACAK_${safeMin}_${safeMax}"
        _productNoMode.value = modeStr
        prefs.edit()
            .putInt(KEY_PRODUCT_MIN, safeMin)
            .putInt(KEY_PRODUCT_MAX, safeMax)
            .putString(KEY_PRODUCT_NO_MODE, modeStr)
            .apply()
        broadcastCurrentState()
    }

    fun setCountRange(range: String) {
        when (range) {
            RANGE_10_20 -> setCountMinMax(10, 20)
            RANGE_20_30 -> setCountMinMax(20, 30)
            else -> setCountMinMax(1, 10)
        }
    }

    fun setProductNoMode(mode: String) {
        val clean = mode.trim()
        if (clean.equals("ACAK", ignoreCase = true) || clean.equals("RANDOM", ignoreCase = true)) {
            setProductMinMax(1, 10)
        } else {
            val num = clean.toIntOrNull() ?: 1
            setProductMinMax(num, num)
        }
    }

    fun toggleMirror(mirrored: Boolean? = null): Boolean {
        val next = mirrored ?: !_isMirrored.value
        _isMirrored.value = next
        prefs.edit().putBoolean(KEY_MIRRORED, next).apply()
        broadcastCurrentState()
        return next
    }

    fun setEntryDirection(direction: String) {
        val clean = when (direction.uppercase()) {
            "RIGHT" -> "RIGHT"
            "TOP" -> "TOP"
            "BOTTOM" -> "BOTTOM"
            "FADE" -> "FADE"
            else -> "LEFT"
        }
        _entryDirection.value = clean
        prefs.edit().putString(KEY_ENTRY_DIRECTION, clean).apply()
        broadcastCurrentState()
    }

    fun setDisplayDurationMs(ms: Long) {
        val safeMs = ms.coerceIn(1000L, 15000L)
        _displayDurationMs.value = safeMs
        prefs.edit().putLong(KEY_DURATION_MS, safeMs).apply()
    }

    fun setIntervalRange(minSec: Int, maxSec: Int) {
        val safeMin = minSec.coerceIn(2, 60)
        val safeMax = maxSec.coerceAtLeast(safeMin).coerceIn(safeMin, 120)
        _minIntervalSeconds.value = safeMin
        _maxIntervalSeconds.value = safeMax
        prefs.edit().putInt(KEY_MIN_INTERVAL, safeMin).putInt(KEY_MAX_INTERVAL, safeMax).apply()
    }

    fun setActionText(text: String) {
        val clean = text.trim().ifBlank { "sedang membeli" }
        _actionText.value = clean
        prefs.edit().putString(KEY_ACTION_TEXT, clean).apply()
        broadcastCurrentState()
    }

    fun togglePositionLock(locked: Boolean? = null): Boolean {
        val next = locked ?: !_isPositionLocked.value
        _isPositionLocked.value = next
        prefs.edit().putBoolean(KEY_POS_LOCKED, next).apply()
        return next
    }

    fun setPositionLocked(locked: Boolean) {
        _isPositionLocked.value = locked
        prefs.edit().putBoolean(KEY_POS_LOCKED, locked).apply()
    }

    fun savePosition(x: Int, y: Int) {
        _posX.value = x
        _posY.value = y
        prefs.edit().putInt(KEY_POS_X, x).putInt(KEY_POS_Y, y).apply()
        broadcastCurrentState()
    }

    fun setRotation(angle: Int) {
        val safe = when (angle) {
            90, 180, 270 -> angle
            else -> 0
        }
        _rotation.value = safe
        prefs.edit().putInt(KEY_ROTATION, safe).apply()
        broadcastCurrentState()
    }

    fun cycleRotation(): Int {
        val next = when (_rotation.value) {
            0 -> 90
            90 -> 180
            180 -> 270
            else -> 0
        }
        _rotation.value = next
        prefs.edit().putInt(KEY_ROTATION, next).apply()
        broadcastCurrentState()
        return next
    }

    fun setScale(scale: Float) {
        val next = ((scale) * 100).let { kotlin.math.round(it) / 100f }.coerceAtLeast(0.05f)
        _scale.value = next
        prefs.edit().putFloat(KEY_SCALE, next).apply()
        broadcastCurrentState()
    }

    fun changeScale(delta: Float): Float {
        val current = _scale.value
        val next = ((current + delta) * 100).let { kotlin.math.round(it) / 100f }.coerceAtLeast(0.05f)
        _scale.value = next
        prefs.edit().putFloat(KEY_SCALE, next).apply()
        broadcastCurrentState()
        return next
    }

    fun movePosition(dxPx: Int, dyPx: Int) {
        val newX = _posX.value + dxPx
        val newY = _posY.value + dyPx
        savePosition(newX, newY)
    }

    fun setCapsuleCornerRadius(radiusDp: Int) {
        val safe = radiusDp.coerceIn(0, 150)
        _capsuleCornerRadius.value = safe
        prefs.edit().putInt(KEY_CAPSULE_CORNER_RADIUS, safe).apply()
        onCapsuleStyleChanged?.invoke()
        broadcastCurrentState()
    }

    fun adjustCapsuleCornerRadius(delta: Int): Int {
        val next = (_capsuleCornerRadius.value + delta).coerceIn(0, 150)
        setCapsuleCornerRadius(next)
        return next
    }

    fun setCapsuleAlpha(alpha: Float) {
        val safe = ((alpha * 100).toInt() / 100f).coerceIn(0.10f, 1.0f)
        _capsuleAlpha.value = safe
        prefs.edit().putFloat(KEY_CAPSULE_ALPHA, safe).apply()
        onCapsuleStyleChanged?.invoke()
        broadcastCurrentState()
    }

    fun adjustCapsuleAlpha(delta: Float): Float {
        val next = (_capsuleAlpha.value + delta).coerceIn(0.10f, 1.0f)
        setCapsuleAlpha(next)
        return next
    }

    fun setCapsuleOutlineWidth(widthDp: Int) {
        val safe = widthDp.coerceIn(0, 10)
        _capsuleOutlineWidth.value = safe
        prefs.edit().putInt(KEY_CAPSULE_OUTLINE_WIDTH, safe).apply()
        onCapsuleStyleChanged?.invoke()
        broadcastCurrentState()
    }

    fun adjustCapsuleOutlineWidth(delta: Int): Int {
        val next = (_capsuleOutlineWidth.value + delta).coerceIn(0, 10)
        setCapsuleOutlineWidth(next)
        return next
    }

    fun setCapsuleOutlineColor(hex: String) {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        _capsuleOutlineColor.value = clean
        prefs.edit().putString(KEY_CAPSULE_OUTLINE_COLOR, clean).apply()
        onCapsuleStyleChanged?.invoke()
        broadcastCurrentState()
    }

    fun setCapsuleBgColor(hex: String) {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        _capsuleBgColorHex.value = clean
        prefs.edit().putString(KEY_CAPSULE_BG_COLOR, clean).apply()
        onCapsuleStyleChanged?.invoke()
        broadcastCurrentState()
    }

    fun setFontSizeSp(sp: Float) {
        val safe = ((sp * 10).toInt() / 10f).coerceAtLeast(4.0f)
        _fontSizeSp.value = safe
        prefs.edit().putFloat(KEY_FONT_SIZE_SP, safe).apply()
        onCapsuleStyleChanged?.invoke()
        broadcastCurrentState()
    }

    fun adjustFontSizeSp(delta: Float): Float {
        val next = (_fontSizeSp.value + delta).coerceAtLeast(4.0f)
        setFontSizeSp(next)
        return next
    }

    fun setCartIconSizeDp(dp: Float) {
        val safe = ((dp * 10).toInt() / 10f).coerceAtLeast(4.0f)
        _cartIconSizeDp.value = safe
        prefs.edit().putFloat(KEY_CART_ICON_SIZE_DP, safe).apply()
        onCapsuleStyleChanged?.invoke()
        broadcastCurrentState()
    }

    fun adjustCartIconSizeDp(delta: Float): Float {
        val next = (_cartIconSizeDp.value + delta).coerceAtLeast(4.0f)
        setCartIconSizeDp(next)
        return next
    }

    fun setCapsulePaddingH(padDp: Int) {
        val safe = padDp.coerceAtLeast(0)
        _capsulePaddingH.value = safe
        prefs.edit().putInt(KEY_CAPSULE_PADDING_H, safe).apply()
        onCapsuleStyleChanged?.invoke()
        broadcastCurrentState()
    }

    fun adjustCapsulePaddingH(delta: Int): Int {
        val next = (_capsulePaddingH.value + delta).coerceAtLeast(0)
        setCapsulePaddingH(next)
        return next
    }

    fun setCapsulePaddingV(padDp: Int) {
        val safe = padDp.coerceIn(-100, 200)
        _capsulePaddingV.value = safe
        prefs.edit().putInt(KEY_CAPSULE_PADDING_V, safe).apply()
        onCapsuleStyleChanged?.invoke()
        broadcastCurrentState()
    }

    fun adjustCapsulePaddingV(delta: Int): Int {
        val next = (_capsulePaddingV.value + delta).coerceIn(-100, 200)
        setCapsulePaddingV(next)
        return next
    }

    fun resetCapsuleDefaults() {
        setCapsuleCornerRadius(39)
        setCapsulePaddingH(27)
        setCapsulePaddingV(2)
        setCapsuleAlpha(0.75f)
        setCapsuleOutlineWidth(0)
        setCapsuleOutlineColor("#FFFFFF")
        setCapsuleBgColor("#ff941a")
        setFontSizeSp(16.0f)
        setCartIconSizeDp(21.0f)
        _scale.value = 0.85f
        prefs.edit().putFloat(KEY_SCALE, 0.85f).apply()
        broadcastCurrentState()
    }

    fun movePositionStep(dxDp: Float, dyDp: Float) {
        val metrics = context.resources.displayMetrics
        val sW = metrics.widthPixels.coerceAtLeast(1)
        val sH = metrics.heightPixels.coerceAtLeast(1)
        val deltaX = (dxDp * metrics.density).toInt()
        val deltaY = (dyDp * metrics.density).toInt()
        val newX = (_posX.value + deltaX).coerceIn(0, sW)
        val newY = (_posY.value + deltaY).coerceIn(0, sH)
        savePosition(newX, newY)
    }

    fun resetToDefaultPosition() {
        savePosition(114, 1415)
        setPositionLocked(true)
    }

    fun resetToFactoryDefaults() {
        toggleEnabled(false)
        setSalesFormat(FORMAT_VIEWING)
        setProductNoMode("1")
        setCountRange(RANGE_1_10)
        setIntervalRange(3, 6)
        setDisplayDurationMs(2840L)
        setCapsuleBgColor("#ff941a")
        setCapsuleAlpha(0.75f)
        setCapsuleOutlineWidth(0)
        setCapsuleOutlineColor("#FFFFFF")
        setCapsuleCornerRadius(39)
        setCapsulePaddingH(27)
        setCapsulePaddingV(2)
        setFontSizeSp(16.0f)
        setCartIconSizeDp(21.0f)
        savePosition(114, 1415)
        setPositionLocked(true)
        _scale.value = 0.85f
        prefs.edit().putFloat(KEY_SCALE, 0.85f).apply()
        _rotation.value = 0
        prefs.edit().putInt(KEY_ROTATION, 0).apply()
        _isMirrored.value = false
        prefs.edit().putBoolean(KEY_MIRRORED, false).apply()
        setEntryDirection("LEFT")
        broadcastCurrentState()
    }

    fun broadcastCurrentState(customName: String? = null) {
        val payload = generateNotificationPayload()
        val name = customName ?: payload.senderName
        val contentText = if (customName != null) _actionText.value else payload.actionText

        val metrics = context.resources.displayMetrics
        val sW = metrics.widthPixels.coerceAtLeast(1)
        val sH = metrics.heightPixels.coerceAtLeast(1)
        val posXpct = (_posX.value.toFloat() / sW).coerceIn(0.01f, 0.9f)
        val posYpct = (_posY.value.toFloat() / sH).coerceIn(0.01f, 0.95f)

        OverlayBannerBroadcaster.broadcastShow(
            context = context,
            senderName = name,
            content = contentText,
            isGift = false,
            bannerType = "BUYER",
            iconMode = "SHOPPING",
            durationMs = _displayDurationMs.value,
            posXpct = posXpct,
            posYpct = posYpct,
            rotation = _rotation.value,
            scale = _scale.value,
            isMirrored = _isMirrored.value,
            entryDirection = _entryDirection.value,
            capsuleCornerRadius = _capsuleCornerRadius.value,
            capsulePaddingH = _capsulePaddingH.value,
            capsulePaddingV = _capsulePaddingV.value,
            capsuleAlpha = _capsuleAlpha.value,
            capsuleOutlineWidth = _capsuleOutlineWidth.value,
            capsuleOutlineColor = _capsuleOutlineColor.value,
            capsuleBgColor = _capsuleBgColorHex.value,
            fontSizeSp = _fontSizeSp.value,
            cartIconSizeDp = _cartIconSizeDp.value
        )
    }

    /**
     * Generates a buyer username with EXACTLY 3 stars (e.g. r***8, a***3, 9***x).
     */
    fun getRandomBuyerName(): String {
        if (BUYER_NAMES.isNotEmpty() && Random.nextFloat() < 0.75f) {
            return BUYER_NAMES.random()
        }
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val firstChar = chars[Random.nextInt(chars.length)]
        val lastChar = chars[Random.nextInt(chars.length)]
        return "$firstChar***$lastChar"
    }

    /**
     * Generates random count for the chosen range:
     * Uses flexible min and max range (_countMin to _countMax)
     */
    fun getRandomCountForRange(): Int {
        val min = _countMin.value.coerceAtLeast(1)
        val max = _countMax.value.coerceAtLeast(min)
        return if (max > min) {
            Random.nextInt(min, max + 1)
        } else {
            min
        }
    }

    /**
     * Generates notification payload based on selected format template:
     * 1) "{char}***{char} sedang membeli"
     * 2) "{count} orang sedang melihat produk No. {prodNo}" (randomized from _productMin to _productMax)
     * 3) "{count} orang telah membeli produk dari LIVE ini"
     */
    fun generateNotificationPayload(): SalesNotificationPayload {
        val format = _salesFormat.value
        val selectedMode = if (format == FORMAT_MIXED) {
            listOf(FORMAT_BUYING, FORMAT_VIEWING, FORMAT_BOUGHT).random()
        } else {
            format
        }

        return when (selectedMode) {
            FORMAT_VIEWING -> {
                val count = getRandomCountForRange()
                val minProd = _productMin.value.coerceAtLeast(1)
                val maxProd = _productMax.value.coerceAtLeast(minProd)
                val prodNo = if (maxProd > minProd) {
                    Random.nextInt(minProd, maxProd + 1)
                } else {
                    minProd
                }
                SalesNotificationPayload(
                    senderName = "",
                    actionText = "$count orang sedang melihat produk No. $prodNo"
                )
            }
            FORMAT_BOUGHT -> {
                val count = getRandomCountForRange()
                val minProd = _productMin.value.coerceAtLeast(1)
                val maxProd = _productMax.value.coerceAtLeast(minProd)
                val prodNo = if (maxProd > minProd) {
                    Random.nextInt(minProd, maxProd + 1)
                } else {
                    minProd
                }
                val action = if (Random.nextBoolean()) {
                    "$count orang telah membeli produk No. $prodNo"
                } else {
                    "$count orang telah membeli produk dari LIVE ini"
                }
                SalesNotificationPayload(
                    senderName = "",
                    actionText = action
                )
            }
            else -> { // FORMAT_BUYING
                val name = getRandomBuyerName()
                val minProd = _productMin.value.coerceAtLeast(1)
                val maxProd = _productMax.value.coerceAtLeast(minProd)
                val prodNo = if (maxProd > minProd) {
                    Random.nextInt(minProd, maxProd + 1)
                } else {
                    minProd
                }
                val action = if (Random.nextBoolean()) {
                    "sedang membeli produk No. $prodNo"
                } else {
                    "sedang membeli"
                }
                SalesNotificationPayload(
                    senderName = name,
                    actionText = action
                )
            }
        }
    }

    fun triggerManualNotification() {
        scope.launch {
            val payload = generateNotificationPayload()
            displayNotification(payload.senderName, payload.actionText)
            delay(_displayDurationMs.value)
            _isNotifVisible.value = false
            onHideNotification?.invoke()
        }
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            // First notification fires soon after enabling
            delay(1000L)
            while (isActive && _isEnabled.value) {
                val payload = generateNotificationPayload()
                displayNotification(payload.senderName, payload.actionText)

                val dur = _displayDurationMs.value
                delay(dur)
                _isNotifVisible.value = false
                onHideNotification?.invoke()

                // Random interval pause before next notification
                val minS = _minIntervalSeconds.value
                val maxS = _maxIntervalSeconds.value
                val fromMs = (minOf(minS, maxS).coerceAtLeast(1) * 1000L)
                val toMs = (maxOf(minS, maxS).coerceAtLeast(1) * 1000L)
                val intervalMs = if (toMs > fromMs) {
                    Random.nextLong(fromMs, toMs)
                } else {
                    fromMs
                }
                delay(intervalMs)
            }
        }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        _isNotifVisible.value = false
        onHideNotification?.invoke()
        OverlayBannerBroadcaster.broadcastHide(context)
    }

    private suspend fun displayNotification(name: String, action: String) {
        _currentBuyerName.value = name
        _actionText.value = action
        _isNotifVisible.value = true
        _totalTriggeredCount.value += 1

        val dur = _displayDurationMs.value

        // Trigger overlay UI animation callback
        onShowNotification?.invoke(name, action, dur)

        // Broadcast directly to Virtual Camera pipeline (GlVideoRenderer / MainHook)
        val metrics = context.resources.displayMetrics
        val sW = metrics.widthPixels.coerceAtLeast(1)
        val sH = metrics.heightPixels.coerceAtLeast(1)
        val posXpct = (_posX.value.toFloat() / sW).coerceIn(0.01f, 0.9f)
        val posYpct = (_posY.value.toFloat() / sH).coerceIn(0.01f, 0.95f)

        OverlayBannerBroadcaster.broadcastShow(
            context = context,
            senderName = name,
            content = action,
            isGift = false,
            bannerType = "BUYER",
            iconMode = "SHOPPING",
            durationMs = dur,
            posXpct = posXpct,
            posYpct = posYpct,
            rotation = _rotation.value,
            scale = _scale.value,
            isMirrored = _isMirrored.value,
            entryDirection = _entryDirection.value,
            capsuleCornerRadius = _capsuleCornerRadius.value,
            capsulePaddingH = _capsulePaddingH.value,
            capsulePaddingV = _capsulePaddingV.value,
            capsuleAlpha = _capsuleAlpha.value,
            capsuleOutlineWidth = _capsuleOutlineWidth.value,
            capsuleOutlineColor = _capsuleOutlineColor.value,
            capsuleBgColor = _capsuleBgColorHex.value,
            fontSizeSp = _fontSizeSp.value,
            cartIconSizeDp = _cartIconSizeDp.value
        )
    }

    /**
     * Formats all current Notif Jualan configuration into a clean copyable text.
     */
    fun getFormattedSettingsText(): String {
        val prodDesc = if (_productMin.value == _productMax.value) {
            "No. ${_productMin.value}"
        } else {
            "No. ${_productMin.value} s/d No. ${_productMax.value} (Acak Otomatis)"
        }
        val formatLabel = when (_salesFormat.value) {
            FORMAT_BUYING -> "Sedang Membeli (r***8 sedang membeli)"
            FORMAT_VIEWING -> "Melihat Produk (X orang sedang melihat produk $prodDesc)"
            FORMAT_BOUGHT -> "Telah Membeli (X orang telah membeli dari LIVE ini)"
            else -> "Semua / Campuran (Acak 3 Mode)"
        }
        val rangeLabel = "${_countMin.value} s/d ${_countMax.value} Orang (Acak Otomatis)"
        val alphaPct = (_capsuleAlpha.value * 100).toInt()
        val outlineDesc = if (_capsuleOutlineWidth.value > 0) {
            "${_capsuleOutlineWidth.value}dp (${_capsuleOutlineColor.value})"
        } else {
            "Nonaktif (0dp)"
        }

        return """
            🛍️ [SETTINGAN PIN NOTIF JUALAN]
            • Status: ${if (_isEnabled.value) "AKTIF (ON)" else "NONAKTIF (OFF)"}
            • Pilihan Format: $formatLabel
            • Nomor Produk: $prodDesc
            • Rentang Jumlah: $rangeLabel
            • Jeda Interval: ${_minIntervalSeconds.value}s - ${_maxIntervalSeconds.value}s
            • Durasi Tayang: ${_displayDurationMs.value}ms
            • Warna Kapsul: ${_capsuleBgColorHex.value}
            • Transparansi: $alphaPct%
            • Outline Kapsul: $outlineDesc
            • Sudut Kapsul: ${_capsuleCornerRadius.value}dp
            • Padding Kapsul: H: ${_capsulePaddingH.value}dp, V: ${_capsulePaddingV.value}dp
            • Ukuran Font: ${_fontSizeSp.value}sp
            • Ukuran Keranjang: ${_cartIconSizeDp.value}dp
            • Posisi: X: ${_posX.value}px, Y: ${_posY.value}px (Terkunci: ${if (_isPositionLocked.value) "Ya" else "Tidak"})
            • Skala / Ukuran: ${_scale.value}x
            • Rotasi: ${_rotation.value}° | Mirror: ${if (_isMirrored.value) "Ya" else "Tidak"}
            • Arah Animasi Masuk: ${_entryDirection.value}
        """.trimIndent()
    }
}
