package com.tajasb.notif

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.Locale

/**
 * AutoTimeCapsuleManager:
 * Mengelola fitur "Waktu Otomatis" berbentuk kapsul seperti Notif Jualan:
 * - Bagian Kiri Kapsul: Kotak/Badge Tanggal (2 digit di atas) & Bulan Singkat (3 huruf di bawah: JAN..DES).
 * - Bagian Kanan Kapsul: Jam & Menit (Wajib, HH:mm) serta Detik (Opsional, :ss).
 * - Tampil terus-menerus (persistent) selama diaktifkan dan ter-render langsung di OpenGL Virtual Camera.
 */
class AutoTimeCapsuleManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "auto_time_capsule_prefs"
        private const val KEY_ENABLED = "key_auto_time_enabled"
        private const val KEY_SHOW_SECONDS = "key_auto_time_show_seconds"
        private const val KEY_CAPSULE_BG_COLOR = "key_auto_time_capsule_bg_color"
        private const val KEY_CAPSULE_ALPHA = "key_auto_time_capsule_alpha"
        private const val KEY_CAPSULE_CORNER_RADIUS = "key_auto_time_capsule_corner_radius"
        private const val KEY_CAPSULE_PADDING_H = "key_auto_time_capsule_padding_h"
        private const val KEY_CAPSULE_OUTLINE_WIDTH = "key_auto_time_capsule_outline_width"
        private const val KEY_CAPSULE_OUTLINE_COLOR = "key_auto_time_capsule_outline_color"
        private const val KEY_DATE_BOX_BG_COLOR = "key_auto_time_date_box_bg_color"
        private const val KEY_DATE_BOX_TEXT_COLOR = "key_auto_time_date_box_text_color"
        private const val KEY_TIME_TEXT_COLOR = "key_auto_time_time_text_color"
        private const val KEY_SCALE = "key_auto_time_scale"
        private const val KEY_POS_X = "key_auto_time_pos_x"
        private const val KEY_POS_Y = "key_auto_time_pos_y"
        private const val KEY_POS_LOCKED = "key_auto_time_pos_locked"
        private const val KEY_ROTATION = "key_auto_time_rotation"
        private const val KEY_MIRRORED = "key_auto_time_mirrored"

        val INDONESIAN_MONTHS_SHORT = arrayOf(
            "JAN", "FEB", "MAR", "APR", "MEI", "JUN",
            "JUL", "AGU", "SEP", "OKT", "NOV", "DES"
        )

        @Volatile
        private var instance: AutoTimeCapsuleManager? = null

        fun getInstance(context: Context): AutoTimeCapsuleManager {
            return instance ?: synchronized(this) {
                instance ?: AutoTimeCapsuleManager(context.applicationContext).also { instance = it }
            }
        }

        fun formatDateAndTimeParts(
            timeMs: Long = System.currentTimeMillis(),
            showSeconds: Boolean = true
        ): Triple<String, String, String> {
            val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val monthIdx = cal.get(Calendar.MONTH).coerceIn(0, 11)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val second = cal.get(Calendar.SECOND)

            val dayStr = String.format(Locale.US, "%02d", day)
            val monthStr = INDONESIAN_MONTHS_SHORT[monthIdx]
            val timeStr = if (showSeconds) {
                String.format(Locale.US, "%02d:%02d:%02d", hour, minute, second)
            } else {
                String.format(Locale.US, "%02d:%02d", hour, minute)
            }
            return Triple(dayStr, monthStr, timeStr)
        }

        fun encodeStylePack(
            dateBoxBgHex: String,
            dateBoxTextHex: String,
            timeTextHex: String,
            showSeconds: Boolean
        ): String {
            return "${dateBoxBgHex.trim()}|${dateBoxTextHex.trim()}|${timeTextHex.trim()}|${if (showSeconds) "1" else "0"}"
        }

        fun decodeStylePack(packed: String): QuadStylePack {
            val parts = packed.split("|")
            val dateBg = parts.getOrNull(0)?.takeIf { it.startsWith("#") } ?: "#FFFFFF"
            val dateTxt = parts.getOrNull(1)?.takeIf { it.startsWith("#") } ?: "#D97724"
            val timeTxt = parts.getOrNull(2)?.takeIf { it.startsWith("#") } ?: "#FFFFFF"
            val showSec = parts.getOrNull(3)?.let { it == "1" || it.equals("true", ignoreCase = true) } ?: true
            return QuadStylePack(dateBg, dateTxt, timeTxt, showSec)
        }
    }

    data class QuadStylePack(
        val dateBoxBgColorHex: String,
        val dateBoxTextColorHex: String,
        val timeTextColorHex: String,
        val showSeconds: Boolean
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _showSeconds = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SECONDS, true))
    val showSeconds: StateFlow<Boolean> = _showSeconds.asStateFlow()

    private val _capsuleBgColorHex = MutableStateFlow(prefs.getString(KEY_CAPSULE_BG_COLOR, "#FF941A") ?: "#FF941A")
    val capsuleBgColorHex: StateFlow<String> = _capsuleBgColorHex.asStateFlow()

    private val _capsuleAlpha = MutableStateFlow(prefs.getFloat(KEY_CAPSULE_ALPHA, 0.88f).coerceIn(0.15f, 1.0f))
    val capsuleAlpha: StateFlow<Float> = _capsuleAlpha.asStateFlow()

    private val _capsuleCornerRadius = MutableStateFlow(prefs.getInt(KEY_CAPSULE_CORNER_RADIUS, 39).coerceIn(4, 75))
    val capsuleCornerRadius: StateFlow<Int> = _capsuleCornerRadius.asStateFlow()

    private val _capsulePaddingH = MutableStateFlow(prefs.getInt(KEY_CAPSULE_PADDING_H, 14).coerceIn(4, 48))
    val capsulePaddingH: StateFlow<Int> = _capsulePaddingH.asStateFlow()

    private val _capsuleOutlineWidth = MutableStateFlow(prefs.getInt(KEY_CAPSULE_OUTLINE_WIDTH, 1).coerceIn(0, 6))
    val capsuleOutlineWidth: StateFlow<Int> = _capsuleOutlineWidth.asStateFlow()

    private val _capsuleOutlineColorHex = MutableStateFlow(prefs.getString(KEY_CAPSULE_OUTLINE_COLOR, "#FFFFFF") ?: "#FFFFFF")
    val capsuleOutlineColorHex: StateFlow<String> = _capsuleOutlineColorHex.asStateFlow()

    private val _dateBoxBgColorHex = MutableStateFlow(prefs.getString(KEY_DATE_BOX_BG_COLOR, "#FFFFFF") ?: "#FFFFFF")
    val dateBoxBgColorHex: StateFlow<String> = _dateBoxBgColorHex.asStateFlow()

    private val _dateBoxTextColorHex = MutableStateFlow(prefs.getString(KEY_DATE_BOX_TEXT_COLOR, "#D97724") ?: "#D97724")
    val dateBoxTextColorHex: StateFlow<String> = _dateBoxTextColorHex.asStateFlow()

    private val _timeTextColorHex = MutableStateFlow(prefs.getString(KEY_TIME_TEXT_COLOR, "#FFFFFF") ?: "#FFFFFF")
    val timeTextColorHex: StateFlow<String> = _timeTextColorHex.asStateFlow()

    private val _sizeScale = MutableStateFlow(prefs.getFloat(KEY_SCALE, 1.0f).coerceIn(0.4f, 2.5f))
    val sizeScale: StateFlow<Float> = _sizeScale.asStateFlow()

    private val _posXpct = MutableStateFlow(prefs.getFloat(KEY_POS_X, 0.28f).coerceIn(0.00f, 0.95f))
    val posXpct: StateFlow<Float> = _posXpct.asStateFlow()

    private val _posYpct = MutableStateFlow(prefs.getFloat(KEY_POS_Y, 0.14f).coerceIn(0.02f, 0.95f))
    val posYpct: StateFlow<Float> = _posYpct.asStateFlow()

    private val _isPositionLocked = MutableStateFlow(prefs.getBoolean(KEY_POS_LOCKED, false))
    val isPositionLocked: StateFlow<Boolean> = _isPositionLocked.asStateFlow()

    private val _rotation = MutableStateFlow(prefs.getInt(KEY_ROTATION, 0))
    val rotation: StateFlow<Int> = _rotation.asStateFlow()

    private val _isMirrored = MutableStateFlow(prefs.getBoolean(KEY_MIRRORED, false))
    val isMirrored: StateFlow<Boolean> = _isMirrored.asStateFlow()

    init {
        if (_isEnabled.value) {
            broadcastCurrentState()
        }
    }

    fun toggleEnabled(force: Boolean? = null): Boolean {
        val next = force ?: !_isEnabled.value
        _isEnabled.value = next
        prefs.edit().putBoolean(KEY_ENABLED, next).apply()
        if (next) {
            broadcastCurrentState()
        } else {
            OverlayBannerBroadcaster.broadcastHideAutoTime(context)
        }
        return next
    }

    fun toggleShowSeconds(force: Boolean? = null): Boolean {
        val next = force ?: !_showSeconds.value
        _showSeconds.value = next
        prefs.edit().putBoolean(KEY_SHOW_SECONDS, next).apply()
        if (_isEnabled.value) broadcastCurrentState()
        return next
    }

    fun setCapsuleBgColor(hex: String) {
        val clean = normalizeHex(hex, "#FF941A")
        _capsuleBgColorHex.value = clean
        prefs.edit().putString(KEY_CAPSULE_BG_COLOR, clean).apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun setCapsuleAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.15f, 1.0f)
        _capsuleAlpha.value = clamped
        prefs.edit().putFloat(KEY_CAPSULE_ALPHA, clamped).apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun adjustCapsuleAlpha(delta: Float) {
        setCapsuleAlpha(_capsuleAlpha.value + delta)
    }

    fun setCapsuleCornerRadius(radiusDp: Int) {
        val clamped = radiusDp.coerceIn(4, 75)
        _capsuleCornerRadius.value = clamped
        prefs.edit().putInt(KEY_CAPSULE_CORNER_RADIUS, clamped).apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun adjustCapsuleCornerRadius(deltaDp: Int) {
        setCapsuleCornerRadius(_capsuleCornerRadius.value + deltaDp)
    }

    fun setCapsulePaddingH(padDp: Int) {
        val clamped = padDp.coerceIn(4, 48)
        _capsulePaddingH.value = clamped
        prefs.edit().putInt(KEY_CAPSULE_PADDING_H, clamped).apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun adjustCapsulePaddingH(deltaDp: Int) {
        setCapsulePaddingH(_capsulePaddingH.value + deltaDp)
    }

    fun setCapsuleOutlineWidth(widthDp: Int) {
        val clamped = widthDp.coerceIn(0, 6)
        _capsuleOutlineWidth.value = clamped
        prefs.edit().putInt(KEY_CAPSULE_OUTLINE_WIDTH, clamped).apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun adjustCapsuleOutlineWidth(deltaDp: Int) {
        setCapsuleOutlineWidth(_capsuleOutlineWidth.value + deltaDp)
    }

    fun setCapsuleOutlineColor(hex: String) {
        val clean = normalizeHex(hex, "#FFFFFF")
        _capsuleOutlineColorHex.value = clean
        prefs.edit().putString(KEY_CAPSULE_OUTLINE_COLOR, clean).apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun setDateBoxBgColor(hex: String) {
        val clean = normalizeHex(hex, "#FFFFFF")
        _dateBoxBgColorHex.value = clean
        prefs.edit().putString(KEY_DATE_BOX_BG_COLOR, clean).apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun setDateBoxTextColor(hex: String) {
        val clean = normalizeHex(hex, "#D97724")
        _dateBoxTextColorHex.value = clean
        prefs.edit().putString(KEY_DATE_BOX_TEXT_COLOR, clean).apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun setTimeTextColor(hex: String) {
        val clean = normalizeHex(hex, "#FFFFFF")
        _timeTextColorHex.value = clean
        prefs.edit().putString(KEY_TIME_TEXT_COLOR, clean).apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun setSizeScale(scale: Float) {
        val clamped = scale.coerceIn(0.4f, 2.5f)
        _sizeScale.value = clamped
        prefs.edit().putFloat(KEY_SCALE, clamped).apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun changeScale(delta: Float) {
        setSizeScale((_sizeScale.value + delta).coerceIn(0.4f, 2.5f))
    }

    fun setPosition(xPct: Float, yPct: Float) {
        if (_isPositionLocked.value) return
        val cx = xPct.coerceIn(0.00f, 0.95f)
        val cy = yPct.coerceIn(0.02f, 0.95f)
        _posXpct.value = cx
        _posYpct.value = cy
        prefs.edit()
            .putFloat(KEY_POS_X, cx)
            .putFloat(KEY_POS_Y, cy)
            .apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun movePosition(dxPct: Float, dyPct: Float) {
        if (_isPositionLocked.value) return
        setPosition(_posXpct.value + dxPct, _posYpct.value + dyPct)
    }

    fun resetPosition() {
        _isPositionLocked.value = false
        _posXpct.value = 0.28f
        _posYpct.value = 0.14f
        _rotation.value = 0
        _sizeScale.value = 1.0f
        _isMirrored.value = false
        prefs.edit()
            .putBoolean(KEY_POS_LOCKED, false)
            .putFloat(KEY_POS_X, 0.28f)
            .putFloat(KEY_POS_Y, 0.14f)
            .putInt(KEY_ROTATION, 0)
            .putFloat(KEY_SCALE, 1.0f)
            .putBoolean(KEY_MIRRORED, false)
            .apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun togglePositionLock(force: Boolean? = null): Boolean {
        val next = force ?: !_isPositionLocked.value
        _isPositionLocked.value = next
        prefs.edit().putBoolean(KEY_POS_LOCKED, next).apply()
        return next
    }

    fun cycleRotation(): Int {
        val next = (_rotation.value + 90) % 360
        setRotation(next)
        return next
    }

    fun setRotation(deg: Int) {
        val clean = ((deg % 360) + 360) % 360
        _rotation.value = clean
        prefs.edit().putInt(KEY_ROTATION, clean).apply()
        if (_isEnabled.value) broadcastCurrentState()
    }

    fun toggleMirror(force: Boolean? = null): Boolean {
        val next = force ?: !_isMirrored.value
        _isMirrored.value = next
        prefs.edit().putBoolean(KEY_MIRRORED, next).apply()
        if (_isEnabled.value) broadcastCurrentState()
        return next
    }

    private fun normalizeHex(raw: String, fallback: String): String {
        val trimmed = raw.trim()
        val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return try {
            android.graphics.Color.parseColor(withHash)
            withHash.uppercase(Locale.US)
        } catch (_: Throwable) {
            fallback
        }
    }

    fun broadcastCurrentState() {
        if (!_isEnabled.value) return
        val (dayStr, monthStr, timeStr) = formatDateAndTimeParts(
            timeMs = System.currentTimeMillis(),
            showSeconds = _showSeconds.value
        )
        val stylePack = encodeStylePack(
            dateBoxBgHex = _dateBoxBgColorHex.value,
            dateBoxTextHex = _dateBoxTextColorHex.value,
            timeTextHex = _timeTextColorHex.value,
            showSeconds = _showSeconds.value
        )
        OverlayBannerBroadcaster.broadcastShow(
            context = context,
            senderName = "$dayStr|$monthStr",
            content = timeStr,
            isGift = false,
            bannerType = "AUTO_TIME",
            iconMode = "NONE",
            customPrefix = stylePack,
            formatMode = if (_showSeconds.value) "HH_MM_SS" else "HH_MM",
            durationMs = 86400000L,
            posXpct = _posXpct.value,
            posYpct = _posYpct.value,
            rotation = _rotation.value,
            scale = _sizeScale.value,
            isMirrored = _isMirrored.value,
            entryDirection = "FADE",
            capsuleCornerRadius = _capsuleCornerRadius.value,
            capsulePaddingH = _capsulePaddingH.value,
            capsulePaddingV = 4,
            capsuleAlpha = _capsuleAlpha.value,
            capsuleOutlineWidth = _capsuleOutlineWidth.value,
            capsuleOutlineColor = _capsuleOutlineColorHex.value,
            capsuleBgColor = _capsuleBgColorHex.value
        )
    }

    fun getFormattedSettingsText(): String {
        val (dayStr, monthStr, timeStr) = formatDateAndTimeParts(showSeconds = _showSeconds.value)
        return buildString {
            appendLine("=== SETTINGAN WAKTU OTOMATIS TAJASB ===")
            appendLine("Contoh Tampilan: [$dayStr $monthStr | $timeStr]")
            appendLine("Tampilkan Detik: ${if (_showSeconds.value) "ON (HH:mm:ss)" else "OFF (HH:mm)"}")
            appendLine("Warna Kapsul: ${_capsuleBgColorHex.value} (Alpha: ${(_capsuleAlpha.value * 100).toInt()}%)")
            appendLine("Sudut Kapsul: ${_capsuleCornerRadius.value}dp | Padding Samping: ${_capsulePaddingH.value}dp")
            appendLine("Outline Kapsul: ${_capsuleOutlineWidth.value}dp (${_capsuleOutlineColorHex.value})")
            appendLine("Warna Kotak Tanggal (Kiri): ${_dateBoxBgColorHex.value}")
            appendLine("Warna Teks Tanggal/Bulan: ${_dateBoxTextColorHex.value}")
            appendLine("Warna Teks Jam (Kanan): ${_timeTextColorHex.value}")
            appendLine("Ukuran Skala: ${_sizeScale.value}x")
            appendLine("Posisi: X=${(_posXpct.value * 100).toInt()}%, Y=${(_posYpct.value * 100).toInt()}% | Rotasi: ${_rotation.value}° | Mirror: ${_isMirrored.value}")
        }
    }
}
