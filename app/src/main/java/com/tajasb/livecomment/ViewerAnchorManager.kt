package com.tajasb.livecomment

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class ViewerAnchorData(
    val rawText: String,
    val count: Int,
    val bounds: Rect,
    val posXpct: Float,
    val posYpct: Float,
    val lastUpdatedMs: Long
)

/**
 * ViewerAnchorManager:
 * Detects and persistently saves the screen position and count of live viewers (Ref 1 & 2).
 * Stores coordinates in local SharedPreferences for future reference and feature updates.
 */
class ViewerAnchorManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ViewerAnchorManager"
        private const val PREFS_NAME = "vcam_screen_anchors"
        private const val KEY_VIEWER_RAW = "viewer_count_raw"
        private const val KEY_VIEWER_COUNT = "viewer_count_numeric"
        private const val KEY_VIEWER_X_PCT = "viewer_anchor_x_pct"
        private const val KEY_VIEWER_Y_PCT = "viewer_anchor_y_pct"
        private const val KEY_BOUNDS_LEFT = "viewer_bounds_left"
        private const val KEY_BOUNDS_TOP = "viewer_bounds_top"
        private const val KEY_BOUNDS_RIGHT = "viewer_bounds_right"
        private const val KEY_BOUNDS_BOTTOM = "viewer_bounds_bottom"
        private const val KEY_VIEWER_UPDATED = "viewer_anchor_updated_ms"

        @Volatile
        private var instance: ViewerAnchorManager? = null

        fun getInstance(context: Context): ViewerAnchorManager {
            return instance ?: synchronized(this) {
                instance ?: ViewerAnchorManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Parses viewer count strings like "0", "12", "5.4K", "5,4k", "1.2M".
         */
        fun parseCountString(raw: String): Int? {
            val clean = raw.trim().replace(" ", "").lowercase(Locale.US)
            if (clean.isBlank()) return null

            return try {
                when {
                    clean.endsWith("k") -> {
                        val num = clean.removeSuffix("k").replace(",", ".").toFloat()
                        (num * 1000).toInt()
                    }
                    clean.endsWith("m") -> {
                        val num = clean.removeSuffix("m").replace(",", ".").toFloat()
                        (num * 1000000).toInt()
                    }
                    else -> {
                        clean.toIntOrNull()
                    }
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _viewerAnchor = MutableStateFlow<ViewerAnchorData?>(loadSavedAnchor())
    val viewerAnchor: StateFlow<ViewerAnchorData?> = _viewerAnchor.asStateFlow()

    private fun loadSavedAnchor(): ViewerAnchorData? {
        val raw = prefs.getString(KEY_VIEWER_RAW, null) ?: return null
        val count = prefs.getInt(KEY_VIEWER_COUNT, 0)
        val xPct = prefs.getFloat(KEY_VIEWER_X_PCT, 0.84f)
        val yPct = prefs.getFloat(KEY_VIEWER_Y_PCT, 0.05f)
        val left = prefs.getInt(KEY_BOUNDS_LEFT, 0)
        val top = prefs.getInt(KEY_BOUNDS_TOP, 0)
        val right = prefs.getInt(KEY_BOUNDS_RIGHT, 0)
        val bottom = prefs.getInt(KEY_BOUNDS_BOTTOM, 0)
        val updated = prefs.getLong(KEY_VIEWER_UPDATED, 0L)

        return ViewerAnchorData(
            rawText = raw,
            count = count,
            bounds = Rect(left, top, right, bottom),
            posXpct = xPct,
            posYpct = yPct,
            lastUpdatedMs = updated
        )
    }

    fun saveViewerAnchor(
        rawText: String,
        bounds: Rect,
        screenW: Int,
        screenH: Int
    ): ViewerAnchorData? {
        val count = parseCountString(rawText) ?: return null
        val sW = screenW.coerceAtLeast(1)
        val sH = screenH.coerceAtLeast(1)

        val xPct = (bounds.centerX().toFloat() / sW).coerceIn(0f, 1f)
        val yPct = (bounds.centerY().toFloat() / sH).coerceIn(0f, 1f)
        val now = System.currentTimeMillis()

        prefs.edit()
            .putString(KEY_VIEWER_RAW, rawText)
            .putInt(KEY_VIEWER_COUNT, count)
            .putFloat(KEY_VIEWER_X_PCT, xPct)
            .putFloat(KEY_VIEWER_Y_PCT, yPct)
            .putInt(KEY_BOUNDS_LEFT, bounds.left)
            .putInt(KEY_BOUNDS_TOP, bounds.top)
            .putInt(KEY_BOUNDS_RIGHT, bounds.right)
            .putInt(KEY_BOUNDS_BOTTOM, bounds.bottom)
            .putLong(KEY_VIEWER_UPDATED, now)
            .apply()

        val data = ViewerAnchorData(
            rawText = rawText,
            count = count,
            bounds = Rect(bounds),
            posXpct = xPct,
            posYpct = yPct,
            lastUpdatedMs = now
        )
        _viewerAnchor.value = data
        Log.i(TAG, "📍 Saved Viewer Anchor: '$rawText' ($count viewers) at X=${(xPct*100).toInt()}% Y=${(yPct*100).toInt()}% $bounds")
        return data
    }

    fun reloadFromPrefs() {
        _viewerAnchor.value = loadSavedAnchor()
    }
}
