package com.tajasb.stiker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.tajasb.notif.OverlayBannerBroadcaster

class CustomStikerManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "custom_stiker_prefs"
        private const val KEY_ENABLED = "key_stiker_enabled"
        private const val KEY_TEXT = "key_stiker_text"
        private const val KEY_BOX_COLOR = "key_stiker_box_color"
        private const val KEY_BOX_ALPHA = "key_stiker_box_alpha"
        private const val KEY_TEXT_COLOR = "key_stiker_text_color"
        private const val KEY_SIZE_SCALE = "key_stiker_size_scale"
        private const val KEY_POS_X_PCT = "key_stiker_pos_x_pct"
        private const val KEY_POS_Y_PCT = "key_stiker_pos_y_pct"
        private const val KEY_ROTATION = "key_stiker_rotation"
        private const val KEY_MIRRORED = "key_stiker_mirrored"
        private const val KEY_OUTLINE_WIDTH = "key_stiker_outline_width"
        private const val KEY_OUTLINE_COLOR = "key_stiker_outline_color"
        private const val KEY_RANDOM_MOVE = "key_stiker_random_move"
        private const val KEY_DISPLAY_STYLE = "key_stiker_display_style"
        private const val KEY_RUNNING_TEXT = "key_stiker_running_text"
        private const val KEY_RUNNING_SPEED = "key_stiker_running_speed"
        private const val KEY_RUNNING_DIRECTION = "key_stiker_running_direction"

        const val STYLE_WITH_BLOCK = "WITH_BLOCK"
        const val STYLE_TEXT_ONLY = "TEXT_ONLY"
        const val STYLE_OUTLINE_ONLY = "OUTLINE_ONLY"
        const val STYLE_HOLLOW_OUTLINE = "HOLLOW_OUTLINE"

        @Volatile
        private var instance: CustomStikerManager? = null

        fun getInstance(context: Context): CustomStikerManager {
            return instance ?: synchronized(this) {
                instance ?: CustomStikerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var randomMoveJob: Job? = null

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val defaultStikerTextConst = "⚡ HARGA NORMAL 28.000\n🔥 CO 1x DIKIRIM 6pcs, CO 2x DIKIRIM 12pcs\n🛒 PLASTIK ANTI PECAH TAHAN PANAS"
    private val _stikerText = MutableStateFlow(prefs.getString(KEY_TEXT, defaultStikerTextConst) ?: defaultStikerTextConst)
    val stikerText: StateFlow<String> = _stikerText.asStateFlow()

    private val _boxColorHex = MutableStateFlow(prefs.getString(KEY_BOX_COLOR, "#DC2626") ?: "#DC2626") // Solid Red
    val boxColorHex: StateFlow<String> = _boxColorHex.asStateFlow()

    // Sticker Background Alpha (0.00f to 1.0f)
    private val _boxAlpha = MutableStateFlow(prefs.getFloat(KEY_BOX_ALPHA, 0.85f))
    val boxAlpha: StateFlow<Float> = _boxAlpha.asStateFlow()

    // Display Style: WITH_BLOCK, TEXT_ONLY (Tanpa Blok), OUTLINE_ONLY (Hanya Outline), HOLLOW_OUTLINE
    private val _displayStyle = MutableStateFlow(prefs.getString(KEY_DISPLAY_STYLE, STYLE_WITH_BLOCK) ?: STYLE_WITH_BLOCK)
    val displayStyle: StateFlow<String> = _displayStyle.asStateFlow()

    // Running Text (Marquee) - Default True (Kecepatan 0.5x, Arah LEFT)
    private val _isRunningTextEnabled = MutableStateFlow(prefs.getBoolean(KEY_RUNNING_TEXT, true))
    val isRunningTextEnabled: StateFlow<Boolean> = _isRunningTextEnabled.asStateFlow()
    val isRunningText: StateFlow<Boolean> get() = isRunningTextEnabled

    private val _runningTextSpeed = MutableStateFlow(prefs.getFloat(KEY_RUNNING_SPEED, 0.5f))
    val runningTextSpeed: StateFlow<Float> = _runningTextSpeed.asStateFlow()

    private val _runningTextDirection = MutableStateFlow(prefs.getString(KEY_RUNNING_DIRECTION, "LEFT") ?: "LEFT")
    val runningTextDirection: StateFlow<String> = _runningTextDirection.asStateFlow()

    // Optional Random Wander - Default False
    private val _isRandomMoveEnabled = MutableStateFlow(prefs.getBoolean(KEY_RANDOM_MOVE, false))
    val isRandomMoveEnabled: StateFlow<Boolean> = _isRandomMoveEnabled.asStateFlow()

    private val _textColorHex = MutableStateFlow(prefs.getString(KEY_TEXT_COLOR, "#FFFFFF") ?: "#FFFFFF") // White
    val textColorHex: StateFlow<String> = _textColorHex.asStateFlow()

    private val _sizeScale = MutableStateFlow(prefs.getFloat(KEY_SIZE_SCALE, 1.60f))
    val sizeScale: StateFlow<Float> = _sizeScale.asStateFlow()

    private val _posXpct = MutableStateFlow(prefs.getFloat(KEY_POS_X_PCT, 0.12f))
    val posXpct: StateFlow<Float> = _posXpct.asStateFlow()

    private val _posYpct = MutableStateFlow(prefs.getFloat(KEY_POS_Y_PCT, 0.175f))
    val posYpct: StateFlow<Float> = _posYpct.asStateFlow()

    private val _rotation = MutableStateFlow(prefs.getInt(KEY_ROTATION, 0))
    val rotation: StateFlow<Int> = _rotation.asStateFlow()

    private val _isMirrored = MutableStateFlow(prefs.getBoolean(KEY_MIRRORED, false))
    val isMirrored: StateFlow<Boolean> = _isMirrored.asStateFlow()

    // Sticker Outline / Border (0 = off, 1 to 10 dp) - Default 0
    private val _outlineWidth = MutableStateFlow(prefs.getInt(KEY_OUTLINE_WIDTH, 0))
    val outlineWidth: StateFlow<Int> = _outlineWidth.asStateFlow()

    private val _outlineColorHex = MutableStateFlow(prefs.getString(KEY_OUTLINE_COLOR, "#FFFFFF") ?: "#FFFFFF")
    val outlineColorHex: StateFlow<String> = _outlineColorHex.asStateFlow()

    init {
        if (!prefs.getBoolean("key_stiker_defaults_migrated_v3_sepuh", false)) {
            prefs.edit()
                .putBoolean(KEY_ENABLED, false)
                .putString(KEY_TEXT, defaultStikerTextConst)
                .putString(KEY_BOX_COLOR, "#DC2626")
                .putFloat(KEY_BOX_ALPHA, 0.85f)
                .putString(KEY_DISPLAY_STYLE, STYLE_WITH_BLOCK)
                .putString(KEY_TEXT_COLOR, "#FFFFFF")
                .putFloat(KEY_SIZE_SCALE, 1.60f)
                .putFloat(KEY_POS_X_PCT, 0.12f)
                .putFloat(KEY_POS_Y_PCT, 0.175f)
                .putInt(KEY_ROTATION, 0)
                .putBoolean(KEY_MIRRORED, false)
                .putInt(KEY_OUTLINE_WIDTH, 0)
                .putString(KEY_OUTLINE_COLOR, "#FFFFFF")
                .putBoolean(KEY_RUNNING_TEXT, true)
                .putFloat(KEY_RUNNING_SPEED, 0.5f)
                .putString(KEY_RUNNING_DIRECTION, "LEFT")
                .putBoolean(KEY_RANDOM_MOVE, false)
                .putBoolean("key_stiker_defaults_migrated_v3_sepuh", true)
                .apply()

            _isEnabled.value = false
            _stikerText.value = defaultStikerTextConst
            _boxColorHex.value = "#DC2626"
            _boxAlpha.value = 0.85f
            _displayStyle.value = STYLE_WITH_BLOCK
            _textColorHex.value = "#FFFFFF"
            _sizeScale.value = 1.60f
            _posXpct.value = 0.12f
            _posYpct.value = 0.175f
            _rotation.value = 0
            _isMirrored.value = false
            _outlineWidth.value = 0
            _outlineColorHex.value = "#FFFFFF"
            _isRunningTextEnabled.value = true
            _runningTextSpeed.value = 0.5f
            _runningTextDirection.value = "LEFT"
            _isRandomMoveEnabled.value = false
        }
        if (_isEnabled.value && _isRandomMoveEnabled.value) {
            startRandomMoveLoop()
        }
    }

    fun toggleEnabled(enabled: Boolean? = null): Boolean {
        val next = enabled ?: !_isEnabled.value
        _isEnabled.value = next
        prefs.edit().putBoolean(KEY_ENABLED, next).apply()
        if (next && _isRandomMoveEnabled.value) {
            startRandomMoveLoop()
        } else {
            stopRandomMoveLoop()
        }
        broadcastStikerState()
        return next
    }

    fun setDisplayStyle(style: String) {
        _displayStyle.value = style
        prefs.edit().putString(KEY_DISPLAY_STYLE, style).apply()
        if (style == STYLE_OUTLINE_ONLY || style == STYLE_HOLLOW_OUTLINE) {
            if (_outlineWidth.value == 0) {
                _outlineWidth.value = 3
                prefs.edit().putInt(KEY_OUTLINE_WIDTH, 3).apply()
            }
            if (style == STYLE_OUTLINE_ONLY && _outlineColorHex.value.equals(_textColorHex.value, ignoreCase = true)) {
                val contrastCol = if (_textColorHex.value.equals("#000000", ignoreCase = true)) "#FFFFFF" else "#000000"
                _outlineColorHex.value = contrastCol
                prefs.edit().putString(KEY_OUTLINE_COLOR, contrastCol).apply()
            }
        }
        broadcastStikerState()
    }

    fun toggleRunningText(enabled: Boolean? = null): Boolean {
        val next = enabled ?: !_isRunningTextEnabled.value
        _isRunningTextEnabled.value = next
        prefs.edit().putBoolean(KEY_RUNNING_TEXT, next).apply()
        broadcastStikerState()
        return next
    }

    fun setRunningTextSpeed(speed: Float) {
        val safe = speed.coerceIn(0.3f, 3.0f)
        _runningTextSpeed.value = safe
        prefs.edit().putFloat(KEY_RUNNING_SPEED, safe).apply()
        broadcastStikerState()
    }

    fun setRunningTextDirection(direction: String) {
        val clean = if (direction.equals("RIGHT", ignoreCase = true)) "RIGHT" else "LEFT"
        _runningTextDirection.value = clean
        prefs.edit().putString(KEY_RUNNING_DIRECTION, clean).apply()
        broadcastStikerState()
    }

    fun toggleRandomMove(enabled: Boolean? = null): Boolean {
        val next = enabled ?: !_isRandomMoveEnabled.value
        _isRandomMoveEnabled.value = next
        prefs.edit().putBoolean(KEY_RANDOM_MOVE, next).apply()
        if (next && _isEnabled.value) {
            startRandomMoveLoop()
        } else {
            stopRandomMoveLoop()
        }
        return next
    }

    fun setBoxAlpha(alpha: Float) {
        val safe = alpha.coerceIn(0.00f, 1.0f)
        _boxAlpha.value = safe
        prefs.edit().putFloat(KEY_BOX_ALPHA, safe).apply()
        broadcastStikerState()
    }

    fun adjustBoxAlpha(delta: Float): Float {
        val next = (_boxAlpha.value + delta).coerceIn(0.00f, 1.0f)
        setBoxAlpha(next)
        return next
    }

    fun pickRandomMiddlePosition() {
        val minX = 0.16f
        val maxX = 0.52f
        val minY = 0.36f
        val maxY = 0.58f

        val nextX = minX + kotlin.random.Random.nextFloat() * (maxX - minX)
        val nextY = minY + kotlin.random.Random.nextFloat() * (maxY - minY)

        _posXpct.value = nextX
        _posYpct.value = nextY
        prefs.edit()
            .putFloat(KEY_POS_X_PCT, nextX)
            .putFloat(KEY_POS_Y_PCT, nextY)
            .apply()

        broadcastStikerState()
    }

    fun startRandomMoveLoop() {
        randomMoveJob?.cancel()
        randomMoveJob = coroutineScope.launch {
            while (isActive && _isEnabled.value && _isRandomMoveEnabled.value) {
                delay(5000L)
                if (isActive && _isEnabled.value && _isRandomMoveEnabled.value) {
                    pickRandomMiddlePosition()
                }
            }
        }
    }

    fun stopRandomMoveLoop() {
        randomMoveJob?.cancel()
        randomMoveJob = null
    }

    fun toggleMirror(mirrored: Boolean? = null): Boolean {
        val next = mirrored ?: !_isMirrored.value
        _isMirrored.value = next
        prefs.edit().putBoolean(KEY_MIRRORED, next).apply()
        broadcastStikerState()
        return next
    }

    fun setStikerText(text: String) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(5)
        val clean = if (lines.isEmpty()) {
            "⚡ PROMO LIVE DISKON 50%"
        } else {
            lines.joinToString("\n")
        }
        _stikerText.value = clean
        prefs.edit().putString(KEY_TEXT, clean).apply()
        broadcastStikerState()
    }

    fun setTemplateLines(lineCount: Int) {
        val template = when (lineCount.coerceIn(1, 5)) {
            2 -> "⚡ FLASH SALE LIVE\n🔥 DISKON HINGGA 50%"
            3 -> "⚡ FLASH SALE SPESIAL\n🔥 GRATIS ONGKIR X-TRA\n🛒 CHECKOUT SEKARANG"
            4 -> "⚡ PROMO LIVE HARI INI\n🔥 DISKON BESAR 50%\n🎁 BONUS SPESIAL PEMBELI\n📦 SIAP KIRIM HARI INI"
            5 -> "⚡ PROMO FLASH SALE\n🔥 DISKON HINGGA 70%\n🎁 VOUCHER TERBATAS\n🚚 GRATIS ONGKIR SELURUH INDONESIA\n👇 KLIK KERANJANG KUNING"
            else -> "⚡ PROMO LIVE DISKON 50%"
        }
        setStikerText(template)
    }

    fun setOutlineWidth(widthDp: Int) {
        val safe = widthDp.coerceIn(0, 10)
        _outlineWidth.value = safe
        prefs.edit().putInt(KEY_OUTLINE_WIDTH, safe).apply()
        broadcastStikerState()
    }

    fun adjustOutlineWidth(delta: Int): Int {
        val next = (_outlineWidth.value + delta).coerceIn(0, 10)
        setOutlineWidth(next)
        return next
    }

    fun setOutlineColor(hex: String) {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        _outlineColorHex.value = clean
        prefs.edit().putString(KEY_OUTLINE_COLOR, clean).apply()
        broadcastStikerState()
    }

    fun setBoxColor(hex: String) {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        _boxColorHex.value = clean
        prefs.edit().putString(KEY_BOX_COLOR, clean).apply()
        broadcastStikerState()
    }

    fun setTextColor(hex: String) {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        _textColorHex.value = clean
        prefs.edit().putString(KEY_TEXT_COLOR, clean).apply()
        broadcastStikerState()
    }

    fun setSizeScale(scale: Float) {
        val safe = scale.coerceAtLeast(0.05f)
        _sizeScale.value = safe
        prefs.edit().putFloat(KEY_SIZE_SCALE, safe).apply()
        broadcastStikerState()
    }

    fun changeScale(delta: Float): Float {
        val next = (_sizeScale.value + delta).coerceAtLeast(0.05f)
        setSizeScale(next)
        return next
    }

    fun resetPosition() {
        _posXpct.value = 0.25f
        _posYpct.value = 0.20f
        _rotation.value = 0
        _isMirrored.value = false
        _sizeScale.value = 1.0f
        prefs.edit()
            .putFloat(KEY_POS_X_PCT, 0.25f)
            .putFloat(KEY_POS_Y_PCT, 0.20f)
            .putInt(KEY_ROTATION, 0)
            .putBoolean(KEY_MIRRORED, false)
            .putFloat(KEY_SIZE_SCALE, 1.0f)
            .apply()
        broadcastStikerState()
    }

    fun setPosition(xPct: Float, yPct: Float) {
        val newX = xPct.coerceIn(-1.50f, 2.50f)
        val newY = yPct.coerceIn(-1.50f, 2.50f)
        _posXpct.value = newX
        _posYpct.value = newY
        prefs.edit().putFloat(KEY_POS_X_PCT, newX).putFloat(KEY_POS_Y_PCT, newY).apply()
        broadcastStikerState()
    }

    fun movePosition(dxPct: Float, dyPct: Float) {
        val newX = (_posXpct.value + dxPct).coerceIn(-1.50f, 2.50f)
        val newY = (_posYpct.value + dyPct).coerceIn(-1.50f, 2.50f)
        _posXpct.value = newX
        _posYpct.value = newY
        prefs.edit().putFloat(KEY_POS_X_PCT, newX).putFloat(KEY_POS_Y_PCT, newY).apply()
        broadcastStikerState()
    }

    fun setRotation(angle: Int) {
        val safe = when (angle) {
            90, 180, 270 -> angle
            else -> 0
        }
        _rotation.value = safe
        prefs.edit().putInt(KEY_ROTATION, safe).apply()
        broadcastStikerState()
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
        broadcastStikerState()
        return next
    }

    fun broadcastStikerState() {
        if (!_isEnabled.value) {
            OverlayBannerBroadcaster.broadcastHideSticker(context)
            return
        }

        OverlayBannerBroadcaster.broadcastShow(
            context = context,
            senderName = "STIKER",
            content = _stikerText.value,
            isGift = false,
            bannerType = "STICKER",
            iconMode = "NONE",
            customPrefix = _boxColorHex.value,
            formatMode = _textColorHex.value,
            durationMs = 86400000L,
            posXpct = _posXpct.value,
            posYpct = _posYpct.value,
            rotation = _rotation.value,
            scale = _sizeScale.value,
            isMirrored = _isMirrored.value,
            capsuleAlpha = _boxAlpha.value,
            stikerOutlineWidth = _outlineWidth.value,
            stikerOutlineColor = _outlineColorHex.value,
            stikerDisplayStyle = _displayStyle.value,
            isRunningText = _isRunningTextEnabled.value,
            runningTextSpeed = _runningTextSpeed.value,
            runningTextDirection = _runningTextDirection.value
        )
    }

    fun getFormattedSettingsText(): String {
        val linesCount = _stikerText.value.lines().count { it.isNotBlank() }
        val styleLabel = when (_displayStyle.value) {
            STYLE_TEXT_ONLY -> "Tanpa Blok (Cuman Teks)"
            STYLE_OUTLINE_ONLY -> "Outline Tanpa Blok dengan Teks"
            STYLE_HOLLOW_OUTLINE -> "Hanya Outline (Tanpa Blok)"
            else -> "Pakai Blok Kotak"
        }
        val outlineDesc = if (_outlineWidth.value > 0) {
            "${_outlineWidth.value}dp (${_outlineColorHex.value})"
        } else {
            "Nonaktif (0dp)"
        }
        val runDesc = if (_isRunningTextEnabled.value) {
            "AKTIF (Kecepatan ${_runningTextSpeed.value}x, Arah: ${_runningTextDirection.value})"
        } else {
            "NONAKTIF (Diam)"
        }

        return """
            🏷️ [SETTINGAN PIN STIKER TULISAN]
            • Status: ${if (_isEnabled.value) "AKTIF (ON)" else "NONAKTIF (OFF)"}
            • Mode Tampilan: $styleLabel
            • Running Text: $runDesc
            • Pindah Random: ${if (_isRandomMoveEnabled.value) "AKTIF (Tiap 5 Detik)" else "NONAKTIF (Diam)"}
            • Jumlah Baris: $linesCount Baris (Maks 5)
            • Teks Stiker:
${_stikerText.value.lines().joinToString("\n") { "  \"$it\"" }}
            • Warna Blok: ${_boxColorHex.value} (${(_boxAlpha.value * 100).toInt()}%)
            • Warna Teks: ${_textColorHex.value}
            • Outline: $outlineDesc
            • Ukuran Skala: ${_sizeScale.value}x
            • Posisi: X: ${(_posXpct.value * 100).toInt()}%, Y: ${(_posYpct.value * 100).toInt()}%
            • Rotasi: ${_rotation.value}° | Mirror: ${if (_isMirrored.value) "Ya" else "Tidak"}
        """.trimIndent()
    }
}
