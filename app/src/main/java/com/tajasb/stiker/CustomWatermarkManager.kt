package com.tajasb.stiker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.tajasb.notif.OverlayBannerBroadcaster

/**
 * CustomWatermarkManager:
 * Dedicated manager specifically for Watermark overlay (separate from CustomStikerManager).
 * Supports:
 * - Display Styles: WITH_BLOCK (Pakai Blok), TEXT_ONLY (Tanpa Blok / Cuman Teks), OUTLINE_ONLY (Hanya Outline), HOLLOW_OUTLINE
 * - Running Text (Teks Berjalan / Marquee) with adjustable speed & direction
 * - Automatic Random Move (5-second delay in middle-third portrait screen)
 * - Independent position, rotation, mirror, scale, colors, and outline
 */
class CustomWatermarkManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "custom_watermark_prefs"
        private const val KEY_ENABLED = "key_wm_enabled"
        private const val KEY_TEXT = "key_wm_text"
        private const val KEY_BOX_COLOR = "key_wm_box_color"
        private const val KEY_BOX_ALPHA = "key_wm_box_alpha"
        private const val KEY_TEXT_COLOR = "key_wm_text_color"
        private const val KEY_SIZE_SCALE = "key_wm_size_scale"
        private const val KEY_POS_X_PCT = "key_wm_pos_x_pct"
        private const val KEY_POS_Y_PCT = "key_wm_pos_y_pct"
        private const val KEY_ROTATION = "key_wm_rotation"
        private const val KEY_MIRRORED = "key_wm_mirrored"
        private const val KEY_OUTLINE_WIDTH = "key_wm_outline_width"
        private const val KEY_OUTLINE_COLOR = "key_wm_outline_color"
        private const val KEY_RANDOM_MOVE = "key_wm_random_move"
        private const val KEY_DISPLAY_STYLE = "key_wm_display_style"
        private const val KEY_RUNNING_TEXT = "key_wm_running_text"
        private const val KEY_RUNNING_SPEED = "key_wm_running_speed"
        private const val KEY_RUNNING_DIRECTION = "key_wm_running_direction"

        const val STYLE_WITH_BLOCK = "WITH_BLOCK"
        const val STYLE_TEXT_ONLY = "TEXT_ONLY"
        const val STYLE_OUTLINE_ONLY = "OUTLINE_ONLY"
        const val STYLE_HOLLOW_OUTLINE = "HOLLOW_OUTLINE"

        @Volatile
        private var instance: CustomWatermarkManager? = null

        fun getInstance(context: Context): CustomWatermarkManager {
            return instance ?: synchronized(this) {
                instance ?: CustomWatermarkManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var randomMoveJob: Job? = null

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _watermarkText = MutableStateFlow(prefs.getString(KEY_TEXT, "💧 Doa Sepuh Store\n🔒 DILARANG REKAM") ?: "💧 Doa Sepuh Store\n🔒 DILARANG REKAM")
    val watermarkText: StateFlow<String> = _watermarkText.asStateFlow()

    private val _boxColorHex = MutableStateFlow(prefs.getString(KEY_BOX_COLOR, "#0284C7") ?: "#0284C7")
    val boxColorHex: StateFlow<String> = _boxColorHex.asStateFlow()

    // Watermark Background Alpha (0.00f to 1.0f, default 0.35f)
    private val _boxAlpha = MutableStateFlow(prefs.getFloat(KEY_BOX_ALPHA, 0.35f))
    val boxAlpha: StateFlow<Float> = _boxAlpha.asStateFlow()

    // Default to TEXT_ONLY
    private val _displayStyle = MutableStateFlow(prefs.getString(KEY_DISPLAY_STYLE, STYLE_TEXT_ONLY) ?: STYLE_TEXT_ONLY)
    val displayStyle: StateFlow<String> = _displayStyle.asStateFlow()

    // Running Text (Teks Berjalan) - Default False
    private val _isRunningTextEnabled = MutableStateFlow(prefs.getBoolean(KEY_RUNNING_TEXT, false))
    val isRunningTextEnabled: StateFlow<Boolean> = _isRunningTextEnabled.asStateFlow()
    val isRunningText: StateFlow<Boolean> get() = isRunningTextEnabled

    private val _runningTextSpeed = MutableStateFlow(prefs.getFloat(KEY_RUNNING_SPEED, 1.0f))
    val runningTextSpeed: StateFlow<Float> = _runningTextSpeed.asStateFlow()

    private val _runningTextDirection = MutableStateFlow(prefs.getString(KEY_RUNNING_DIRECTION, "LEFT") ?: "LEFT")
    val runningTextDirection: StateFlow<String> = _runningTextDirection.asStateFlow()

    // Watermark Random Wander - Default False
    private val _isRandomMoveEnabled = MutableStateFlow(prefs.getBoolean(KEY_RANDOM_MOVE, false))
    val isRandomMoveEnabled: StateFlow<Boolean> = _isRandomMoveEnabled.asStateFlow()

    private val _textColorHex = MutableStateFlow(prefs.getString(KEY_TEXT_COLOR, "#FFFFFF") ?: "#FFFFFF")
    val textColorHex: StateFlow<String> = _textColorHex.asStateFlow()

    private val _sizeScale = MutableStateFlow(prefs.getFloat(KEY_SIZE_SCALE, 0.90f))
    val sizeScale: StateFlow<Float> = _sizeScale.asStateFlow()

    private val _posXpct = MutableStateFlow(prefs.getFloat(KEY_POS_X_PCT, 0.5444f))
    val posXpct: StateFlow<Float> = _posXpct.asStateFlow()

    private val _posYpct = MutableStateFlow(prefs.getFloat(KEY_POS_Y_PCT, 0.5569f))
    val posYpct: StateFlow<Float> = _posYpct.asStateFlow()

    private val _rotation = MutableStateFlow(prefs.getInt(KEY_ROTATION, 0))
    val rotation: StateFlow<Int> = _rotation.asStateFlow()

    private val _isMirrored = MutableStateFlow(prefs.getBoolean(KEY_MIRRORED, false))
    val isMirrored: StateFlow<Boolean> = _isMirrored.asStateFlow()

    private val _outlineWidth = MutableStateFlow(prefs.getInt(KEY_OUTLINE_WIDTH, 2))
    val outlineWidth: StateFlow<Int> = _outlineWidth.asStateFlow()

    private val _outlineColorHex = MutableStateFlow(prefs.getString(KEY_OUTLINE_COLOR, "#000000") ?: "#000000")
    val outlineColorHex: StateFlow<String> = _outlineColorHex.asStateFlow()

    init {
        if (!prefs.getBoolean("key_wm_defaults_migrated_v3_sepuh", false)) {
            prefs.edit()
                .putBoolean(KEY_ENABLED, false)
                .putString(KEY_TEXT, "💧 Doa Sepuh Store\n🔒 DILARANG REKAM")
                .putString(KEY_BOX_COLOR, "#0284C7")
                .putFloat(KEY_BOX_ALPHA, 0.35f)
                .putString(KEY_DISPLAY_STYLE, STYLE_TEXT_ONLY)
                .putString(KEY_TEXT_COLOR, "#FFFFFF")
                .putFloat(KEY_SIZE_SCALE, 0.90f)
                .putFloat(KEY_POS_X_PCT, 0.5444f)
                .putFloat(KEY_POS_Y_PCT, 0.5569f)
                .putInt(KEY_ROTATION, 0)
                .putBoolean(KEY_MIRRORED, false)
                .putInt(KEY_OUTLINE_WIDTH, 2)
                .putString(KEY_OUTLINE_COLOR, "#000000")
                .putBoolean(KEY_RUNNING_TEXT, false)
                .putFloat(KEY_RUNNING_SPEED, 1.0f)
                .putString(KEY_RUNNING_DIRECTION, "LEFT")
                .putBoolean(KEY_RANDOM_MOVE, false)
                .putBoolean("key_wm_defaults_migrated_v3_sepuh", true)
                .apply()

            _isEnabled.value = false
            _watermarkText.value = "💧 Doa Sepuh Store\n🔒 DILARANG REKAM"
            _boxColorHex.value = "#0284C7"
            _boxAlpha.value = 0.35f
            _displayStyle.value = STYLE_TEXT_ONLY
            _textColorHex.value = "#FFFFFF"
            _sizeScale.value = 0.90f
            _posXpct.value = 0.5444f
            _posYpct.value = 0.5569f
            _rotation.value = 0
            _isMirrored.value = false
            _outlineWidth.value = 2
            _outlineColorHex.value = "#000000"
            _isRunningTextEnabled.value = false
            _runningTextSpeed.value = 1.0f
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
        broadcastWatermarkState()
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
                val contrastCol = if (_textColorHex.value.equals("#000000", ignoreCase = true)) "#00E5FF" else "#000000"
                _outlineColorHex.value = contrastCol
                prefs.edit().putString(KEY_OUTLINE_COLOR, contrastCol).apply()
            }
        }
        broadcastWatermarkState()
    }

    fun toggleRunningText(enabled: Boolean? = null): Boolean {
        val next = enabled ?: !_isRunningTextEnabled.value
        _isRunningTextEnabled.value = next
        prefs.edit().putBoolean(KEY_RUNNING_TEXT, next).apply()
        broadcastWatermarkState()
        return next
    }

    fun setRunningTextSpeed(speed: Float) {
        val safe = speed.coerceIn(0.3f, 3.0f)
        _runningTextSpeed.value = safe
        prefs.edit().putFloat(KEY_RUNNING_SPEED, safe).apply()
        broadcastWatermarkState()
    }

    fun setRunningTextDirection(direction: String) {
        val clean = if (direction.equals("RIGHT", ignoreCase = true)) "RIGHT" else "LEFT"
        _runningTextDirection.value = clean
        prefs.edit().putString(KEY_RUNNING_DIRECTION, clean).apply()
        broadcastWatermarkState()
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
        broadcastWatermarkState()
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

        broadcastWatermarkState()
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
        broadcastWatermarkState()
        return next
    }

    fun setWatermarkText(text: String) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(5)
        val clean = if (lines.isEmpty()) {
            "💧 LIVE ORIGINAL • REAL STREAM"
        } else {
            lines.joinToString("\n")
        }
        _watermarkText.value = clean
        prefs.edit().putString(KEY_TEXT, clean).apply()
        broadcastWatermarkState()
    }

    fun setTemplateLines(lineCount: Int) {
        val template = when (lineCount.coerceIn(1, 5)) {
            1 -> "💧 LIVE ORIGINAL • REAL STREAM"
            2 -> "💧 LIVE STREAMING ASLI\n🔒 ANTI REUPLOAD • OFFICIAL"
            3 -> "💧 OFFICIAL LIVE STREAM\n🔥 PRODUK ORIGINAL 100%\n🛒 AMANAH & BERGARANSI"
            else -> "💧 LIVE ORIGINAL • REAL STREAM"
        }
        setWatermarkText(template)
    }

    fun setOutlineWidth(widthDp: Int) {
        val safe = widthDp.coerceIn(0, 10)
        _outlineWidth.value = safe
        prefs.edit().putInt(KEY_OUTLINE_WIDTH, safe).apply()
        broadcastWatermarkState()
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
        broadcastWatermarkState()
    }

    fun setBoxColor(hex: String) {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        _boxColorHex.value = clean
        prefs.edit().putString(KEY_BOX_COLOR, clean).apply()
        broadcastWatermarkState()
    }

    fun setTextColor(hex: String) {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        _textColorHex.value = clean
        prefs.edit().putString(KEY_TEXT_COLOR, clean).apply()
        broadcastWatermarkState()
    }

    fun setSizeScale(scale: Float) {
        val safe = scale.coerceAtLeast(0.05f)
        _sizeScale.value = safe
        prefs.edit().putFloat(KEY_SIZE_SCALE, safe).apply()
        broadcastWatermarkState()
    }

    fun changeScale(delta: Float): Float {
        val next = (_sizeScale.value + delta).coerceAtLeast(0.05f)
        setSizeScale(next)
        return next
    }

    fun resetPosition() {
        _posXpct.value = 0.28f
        _posYpct.value = 0.46f
        _rotation.value = 0
        _isMirrored.value = false
        _sizeScale.value = 0.9f
        prefs.edit()
            .putFloat(KEY_POS_X_PCT, 0.28f)
            .putFloat(KEY_POS_Y_PCT, 0.46f)
            .putInt(KEY_ROTATION, 0)
            .putBoolean(KEY_MIRRORED, false)
            .putFloat(KEY_SIZE_SCALE, 0.9f)
            .apply()
        broadcastWatermarkState()
    }

    fun setPosition(xPct: Float, yPct: Float) {
        val newX = xPct.coerceIn(-1.50f, 2.50f)
        val newY = yPct.coerceIn(-1.50f, 2.50f)
        _posXpct.value = newX
        _posYpct.value = newY
        prefs.edit().putFloat(KEY_POS_X_PCT, newX).putFloat(KEY_POS_Y_PCT, newY).apply()
        broadcastWatermarkState()
    }

    fun movePosition(dxPct: Float, dyPct: Float) {
        val newX = (_posXpct.value + dxPct).coerceIn(-1.50f, 2.50f)
        val newY = (_posYpct.value + dyPct).coerceIn(-1.50f, 2.50f)
        _posXpct.value = newX
        _posYpct.value = newY
        prefs.edit().putFloat(KEY_POS_X_PCT, newX).putFloat(KEY_POS_Y_PCT, newY).apply()
        broadcastWatermarkState()
    }

    fun setRotation(angle: Int) {
        val safe = when (angle) {
            90, 180, 270 -> angle
            else -> 0
        }
        _rotation.value = safe
        prefs.edit().putInt(KEY_ROTATION, safe).apply()
        broadcastWatermarkState()
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
        broadcastWatermarkState()
        return next
    }

    fun broadcastWatermarkState() {
        if (!_isEnabled.value) {
            OverlayBannerBroadcaster.broadcastHideWatermark(context)
            return
        }

        OverlayBannerBroadcaster.broadcastShow(
            context = context,
            senderName = "WATERMARK",
            content = _watermarkText.value,
            isGift = false,
            bannerType = "WATERMARK",
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
        val linesCount = _watermarkText.value.lines().count { it.isNotBlank() }
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
            💧 [SETTINGAN WATERMARK VIDEO LIVE]
            • Status: ${if (_isEnabled.value) "AKTIF (ON)" else "NONAKTIF (OFF)"}
            • Mode Tampilan: $styleLabel
            • Running Text: $runDesc
            • Pindah Random: ${if (_isRandomMoveEnabled.value) "AKTIF (Tiap 5 Detik di 1/3 Tengah Layar)" else "NONAKTIF (Diam)"}
            • Jumlah Baris: $linesCount Baris
            • Teks Watermark:
${_watermarkText.value.lines().joinToString("\n") { "  \"$it\"" }}
            • Warna Blok: ${_boxColorHex.value} (${(_boxAlpha.value * 100).toInt()}%)
            • Warna Teks: ${_textColorHex.value}
            • Outline: $outlineDesc
            • Ukuran Skala: ${_sizeScale.value}x
            • Posisi: X: ${(_posXpct.value * 100).toInt()}%, Y: ${(_posYpct.value * 100).toInt()}%
            • Rotasi: ${_rotation.value}° | Mirror: ${if (_isMirrored.value) "Ya" else "Tidak"}
        """.trimIndent()
    }
}
