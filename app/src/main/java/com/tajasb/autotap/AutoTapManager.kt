package com.tajasb.autotap

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.tajasb.VcamAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.util.UUID
import kotlin.random.Random

enum class TapSpeedPreset(val label: String, val minDelayMs: Long, val maxDelayMs: Long, val desc: String) {
    HUMAN_NATURAL("Alami (1.0s - 2.5s)", 1000L, 2500L, "Jeda santai mirip jari manusia asli"),
    BALANCED_RANDOM("Sedang (400ms - 900ms)", 400L, 900L, "Kecepatan sedang dengan jeda acak"),
    FAST_RANDOM("Cepat (150ms - 350ms)", 150L, 350L, "Ketukan cepat anti-robot"),
    TURBO_FAST("Kilat (50ms - 120ms)", 50L, 120L, "Kecepatan tinggi / spam tap"),
    CUSTOM("Kustom", 500L, 1500L, "Diatur secara manual");

    val intervalMs: Long
        get() = (minDelayMs + maxDelayMs) / 2
}

enum class TapExecutionMode(val label: String, val desc: String) {
    MULTI_MARK("Rangkaian Koreografi Mark (Klik/Love/Scroll/Back/Ketik)", "Jalankan urutan mark & koreografi secara berurutan"),
    SINGLE_LOVE("Tap Love Tunggal", "Ketuk cepat 1 titik untuk spam love")
}

/**
 * Controls how marks behave on screen once positioned:
 * - EDITABLE: Visible on screen and draggable to adjust coordinates.
 * - LOCKED_GHOST: Locked in place, semi-transparent, and touch pass-through so they cannot be accidentally moved.
 * - LOCKED_HIDDEN: Locked at their exact calibrated coordinates and completely hidden from screen view so they never obstruct the UI.
 */
enum class MarkDisplayMode(val label: String, val shortLabel: String) {
    EDITABLE("Tampil & Bisa Digeser", "👁️ GESER"),
    LOCKED_GHOST("Kunci & Tembus Sentuh", "🔒 KUNCI"),
    LOCKED_HIDDEN("Kunci & Sembunyi dari Layar", "🙈 SEMBUNYI");

    companion object {
        fun fromNameOrDefault(name: String): MarkDisplayMode {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: EDITABLE
        }
    }
}

enum class TapMarkActionType(
    val code: Int,
    val label: String,
    val shortLabel: String,
    val icon: String,
    val colorHex: String,
    val needsCoordinate: Boolean
) {
    CLICK(1, "1. Klik", "KLIK", "👆", "#38BDF8", true),
    LOVE(2, "2. Tap Love", "LOVE", "❤️", "#F43F5E", true),
    SCROLL(3, "3. Scroll", "SCROLL", "↕", "#A78BFA", true),
    BACK(4, "4. Back", "BACK", "◁", "#F59E0B", true),
    AUTO_TYPE(5, "5. Auto Ketik", "KETIK", "⌨", "#10B981", true);

    companion object {
        fun fromNameOrDefault(name: String): TapMarkActionType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CLICK
        }
    }
}

enum class TapScrollDirection(val label: String, val icon: String) {
    UP("Atas", "⬆️"),
    DOWN("Bawah", "⬇️"),
    LEFT("Kiri", "⬅️"),
    RIGHT("Kanan", "➡️");

    fun next(): TapScrollDirection {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    companion object {
        fun fromNameOrDefault(name: String): TapScrollDirection {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: UP
        }
    }
}

enum class AutoTypeSelectionMode(val label: String, val shortLabel: String, val icon: String) {
    RANDOM("Acak (Random)", "Acak", "🎲"),
    SEQUENTIAL("Berurutan", "Urut", "🔢");

    fun toggle(): AutoTypeSelectionMode = if (this == RANDOM) SEQUENTIAL else RANDOM

    companion object {
        fun fromNameOrDefault(name: String): AutoTypeSelectionMode {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: RANDOM
        }
    }
}

data class TapMarkItem(
    val id: String = UUID.randomUUID().toString().take(8),
    val type: TapMarkActionType = TapMarkActionType.CLICK,
    val enabled: Boolean = true,
    val x: Float = 540f,
    val y: Float = 1200f,
    val isDoubleTap: Boolean = false,
    val loveBurstCount: Int = 5, // Jumlah beruntun double-tap Love saat giliran mark ini
    val scrollDirection: TapScrollDirection = TapScrollDirection.UP,
    val scrollDistancePx: Float = 520f,
    val customDelayMs: Long = 0L, // 0L = ikut delay antar-mark global
    val customText: String = "",  // Kosong = ambil dari daftar teks Auto Ketik
    val isLocked: Boolean = false
)

data class TapChoreographyGroup(
    val id: String = "grp_${System.currentTimeMillis()}_${Random.nextInt(10, 99)}",
    val name: String,
    val executionMode: TapExecutionMode = TapExecutionMode.MULTI_MARK,
    val marks: List<TapMarkItem>,
    val enabledInSequence: Boolean = true, // Aktif dalam rotasi koreografi berurutan (1 -> N -> 1)
    val repeatCycles: Int = 1,             // Berapa putaran mark sebelum lanjut ke koreografi berikutnya
    val targetX: Float = 540f,
    val targetY: Float = 1200f,
    val minDelayMs: Long = 800L,
    val maxDelayMs: Long = 1800L,
    val baseIntervalMs: Long = 1200L,
    val antiRobotEnabled: Boolean = true,
    val speedPreset: TapSpeedPreset = TapSpeedPreset.HUMAN_NATURAL,
    val autoTypeTexts: List<String> = emptyList(),
    val autoTypeSelectionMode: AutoTypeSelectionMode = AutoTypeSelectionMode.RANDOM,
    val autoSendAfterType: Boolean = true,
    val updatedAtMs: Long = System.currentTimeMillis()
)

class AutoTapManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "AutoTapManager"
        private const val PREFS_NAME = "tajasb_autotap_prefs"
        private const val KEY_STATE_JSON = "autotap_state_json_v2"
        private const val KEY_MARK_SIZE_DP = "autotap_mark_size_dp"

        val LIVING_ICONS_LIST = listOf(
            "😊", "✨", "🔥", "❤️", "👍", "🙏", "🎉", "⚡", "🌟", "🥰",
            "🙌", "💥", "🥳", "💫", "👏", "🤩", "💖", "😎", "💯", "🎈",
            "🌸", "💐", "🎯", "🚀", "💪", "🌈"
        )

        @Volatile
        private var instance: AutoTapManager? = null

        fun getInstance(context: Context): AutoTapManager {
            return instance ?: synchronized(this) {
                instance ?: AutoTapManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var tapJob: Job? = null

    private val _isTapping = MutableStateFlow(false)
    val isTapping: StateFlow<Boolean> = _isTapping.asStateFlow()

    private val _executionMode = MutableStateFlow(TapExecutionMode.MULTI_MARK)
    val executionMode: StateFlow<TapExecutionMode> = _executionMode.asStateFlow()

    private val _markDisplayMode = MutableStateFlow(MarkDisplayMode.EDITABLE)
    val markDisplayMode: StateFlow<MarkDisplayMode> = _markDisplayMode.asStateFlow()

    private val _targetX = MutableStateFlow(0f)
    val targetX: StateFlow<Float> = _targetX.asStateFlow()

    private val _targetY = MutableStateFlow(0f)
    val targetY: StateFlow<Float> = _targetY.asStateFlow()

    private val _minDelayMs = MutableStateFlow(800L)
    val minDelayMs: StateFlow<Long> = _minDelayMs.asStateFlow()

    private val _maxDelayMs = MutableStateFlow(1800L)
    val maxDelayMs: StateFlow<Long> = _maxDelayMs.asStateFlow()

    private val _currentPreset = MutableStateFlow(TapSpeedPreset.HUMAN_NATURAL)
    val currentPreset: StateFlow<TapSpeedPreset> = _currentPreset.asStateFlow()

    private val _baseIntervalMs = MutableStateFlow(1200L)
    val baseIntervalMs: StateFlow<Long> = _baseIntervalMs.asStateFlow()

    private val _lastGeneratedDelayMs = MutableStateFlow(1200L)
    val lastGeneratedDelayMs: StateFlow<Long> = _lastGeneratedDelayMs.asStateFlow()

    // Delay Random
    private val _antiRobotEnabled = MutableStateFlow(true)
    val antiRobotEnabled: StateFlow<Boolean> = _antiRobotEnabled.asStateFlow()

    private val _isPointerVisible = MutableStateFlow(false)
    val isPointerVisible: StateFlow<Boolean> = _isPointerVisible.asStateFlow()

    private val _markSizeDp = MutableStateFlow(prefs.getInt(KEY_MARK_SIZE_DP, 48).coerceIn(28, 88))
    val markSizeDp: StateFlow<Int> = _markSizeDp.asStateFlow()

    private val _totalTaps = MutableStateFlow(0L)
    val totalTaps: StateFlow<Long> = _totalTaps.asStateFlow()

    // Multi-Mark Sequence State
    private val _marks = MutableStateFlow<List<TapMarkItem>>(emptyList())
    val marks: StateFlow<List<TapMarkItem>> = _marks.asStateFlow()

    private val _selectedMarkId = MutableStateFlow<String?>(null)
    val selectedMarkId: StateFlow<String?> = _selectedMarkId.asStateFlow()

    private val _activeExecutingMarkId = MutableStateFlow<String?>(null)
    val activeExecutingMarkId: StateFlow<String?> = _activeExecutingMarkId.asStateFlow()

    private val _activeStepDescription = MutableStateFlow("Standby")
    val activeStepDescription: StateFlow<String> = _activeStepDescription.asStateFlow()

    // Saved Choreography Groups State & Sequential Multi-Choreography Mode (1 -> 2 -> 3 -> 4 -> 1)
    private val _choreographyGroups = MutableStateFlow<List<TapChoreographyGroup>>(emptyList())
    val choreographyGroups: StateFlow<List<TapChoreographyGroup>> = _choreographyGroups.asStateFlow()

    private val _activeGroupId = MutableStateFlow<String?>(null)
    val activeGroupId: StateFlow<String?> = _activeGroupId.asStateFlow()

    private val _chainAllChoreographies = MutableStateFlow(true)
    val chainAllChoreographies: StateFlow<Boolean> = _chainAllChoreographies.asStateFlow()

    private val _isRandomChoreographyOrder = MutableStateFlow(false)
    val isRandomChoreographyOrder: StateFlow<Boolean> = _isRandomChoreographyOrder.asStateFlow()

    // Auto Ketik Multi-Text Pool State
    private val _autoTypeTexts = MutableStateFlow<List<String>>(
        listOf(
            "Halo kak, spill produknya dong 🙏",
            "Cek keranjang kuning nomor berapa kak?",
            "Mantap banget live nya 🔥",
            "Bantu tap-tap layar yuk teman-teman ❤️"
        )
    )
    val autoTypeTexts: StateFlow<List<String>> = _autoTypeTexts.asStateFlow()

    private val _autoTypeSelectionMode = MutableStateFlow(AutoTypeSelectionMode.RANDOM)
    val autoTypeSelectionMode: StateFlow<AutoTypeSelectionMode> = _autoTypeSelectionMode.asStateFlow()

    private val _autoSendAfterType = MutableStateFlow(true)
    val autoSendAfterType: StateFlow<Boolean> = _autoSendAfterType.asStateFlow()

    private val _appendLivingIcons = MutableStateFlow(true)
    val appendLivingIcons: StateFlow<Boolean> = _appendLivingIcons.asStateFlow()

    /**
     * Menyimpan giliran (turn/cursor) independen untuk masing-masing Grup Koreografi:
     * - Giliran Mark berikutnya (nextActiveMarkIndex & nextMarkId)
     * - Giliran Putaran Koreografi (currentCycle)
     * - Giliran Pesan Auto-Ketik Berurutan (sequentialTextCursor)
     * - Kantong Acak Pesan Auto-Ketik (randomTextBag) agar pesan yang sudah terkirim tidak diulang sebelum semua dapat giliran
     * - Info pesan terakhir yang terkirim (lastSentTextIndex & lastSentTextPreview)
     */
    class ChoreoTurnProgress(
        @Volatile var nextActiveMarkIndex: Int = 0,
        @Volatile var nextMarkId: String? = null,
        @Volatile var currentCycle: Int = 1,
        @Volatile var sequentialTextCursor: Int = 0,
        val randomTextBag: MutableList<String> = mutableListOf(),
        @Volatile var lastPickedRandomText: String? = null,
        @Volatile var lastSentTextIndex: Int = -1,
        @Volatile var lastSentTextPreview: String? = null,
        @Volatile var totalMessagesSent: Int = 0
    )

    private val turnProgressByGroup = java.util.concurrent.ConcurrentHashMap<String, ChoreoTurnProgress>()
    private val markCustomTextCursorMap = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val choreoRandomBagIds = mutableListOf<String>()

    @Volatile
    private var resumeChoreoGroupId: String? = null
    @Volatile
    private var _hasPausedTurnProgress: Boolean = false
    @Volatile
    private var lastLivingIconPair: String? = null
    @Volatile
    private var lastExecutedChoreoId: String? = null
    @Volatile
    private var sameChoreoStreakCount: Int = 0

    private fun getGroupTurnKey(groupId: String? = _activeGroupId.value): String {
        return groupId?.takeIf { it.isNotBlank() } ?: "__default_group__"
    }

    private fun getTurnProgress(groupId: String? = _activeGroupId.value): ChoreoTurnProgress {
        val key = getGroupTurnKey(groupId)
        return turnProgressByGroup.getOrPut(key) { ChoreoTurnProgress() }
    }

    fun hasSavedTurnProgress(): Boolean = _hasPausedTurnProgress

    fun getNextMarkIdForGroup(groupId: String? = _activeGroupId.value): String? {
        val marksList = if (groupId == null || groupId == _activeGroupId.value) {
            _marks.value
        } else {
            _choreographyGroups.value.firstOrNull { it.id == groupId }?.marks ?: _marks.value
        }
        val enabledMarks = marksList.filter { it.enabled }
        if (enabledMarks.isEmpty()) return null
        val prog = getTurnProgress(groupId)
        val byId = prog.nextMarkId?.let { id -> enabledMarks.firstOrNull { it.id == id }?.id }
        if (byId != null) return byId
        val safeIdx = (prog.nextActiveMarkIndex % enabledMarks.size).coerceAtLeast(0)
        return enabledMarks[safeIdx].id
    }

    fun getNextMarkStepNumberForGroup(groupId: String? = _activeGroupId.value): Int {
        val marksList = if (groupId == null || groupId == _activeGroupId.value) {
            _marks.value
        } else {
            _choreographyGroups.value.firstOrNull { it.id == groupId }?.marks ?: _marks.value
        }
        val nextId = getNextMarkIdForGroup(groupId) ?: return 1
        val idx = marksList.indexOfFirst { it.id == nextId }
        return if (idx >= 0) idx + 1 else 1
    }

    fun getNextTextIndexForGroup(groupId: String? = _activeGroupId.value): Int {
        val textsList = if (groupId == null || groupId == _activeGroupId.value) {
            _autoTypeTexts.value
        } else {
            _choreographyGroups.value.firstOrNull { it.id == groupId }?.autoTypeTexts ?: _autoTypeTexts.value
        }
        val pool = textsList.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (pool.isEmpty()) return -1
        val prog = getTurnProgress(groupId)
        return (prog.sequentialTextCursor % pool.size).coerceAtLeast(0)
    }

    fun getLastSentTextIndexForGroup(groupId: String? = _activeGroupId.value): Int {
        val prog = getTurnProgress(groupId)
        return prog.lastSentTextIndex
    }

    fun getRandomTextRemainingCountForGroup(groupId: String? = _activeGroupId.value): Int {
        val textsList = if (groupId == null || groupId == _activeGroupId.value) {
            _autoTypeTexts.value
        } else {
            _choreographyGroups.value.firstOrNull { it.id == groupId }?.autoTypeTexts ?: _autoTypeTexts.value
        }
        val pool = textsList.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (pool.isEmpty()) return 0
        val prog = getTurnProgress(groupId)
        synchronized(prog.randomTextBag) {
            prog.randomTextBag.retainAll(pool.toSet())
            return if (prog.randomTextBag.isEmpty()) pool.size else prog.randomTextBag.size
        }
    }

    fun isTextStillInRandomBag(text: String, groupId: String? = _activeGroupId.value): Boolean {
        val prog = getTurnProgress(groupId)
        synchronized(prog.randomTextBag) {
            if (prog.randomTextBag.isEmpty()) return true
            return prog.randomTextBag.contains(text.trim())
        }
    }

    fun getResumeSummaryShort(): String {
        val allGroups = _choreographyGroups.value
        val enabledGroups = allGroups.filter { it.enabledInSequence && it.marks.any { m -> m.enabled } }
        val isMulti = _chainAllChoreographies.value && enabledGroups.size > 1
        val targetGrpId = if (isMulti) {
            resumeChoreoGroupId?.takeIf { id -> enabledGroups.any { it.id == id } }
                ?: _activeGroupId.value?.takeIf { id -> enabledGroups.any { it.id == id } }
                ?: enabledGroups.firstOrNull()?.id
        } else {
            _activeGroupId.value
        }

        val targetGrp = allGroups.firstOrNull { it.id == targetGrpId }
        val marksList = if (targetGrp != null && targetGrp.id != _activeGroupId.value) targetGrp.marks else _marks.value
        val textsList = if (targetGrp != null && targetGrp.id != _activeGroupId.value) targetGrp.autoTypeTexts else _autoTypeTexts.value
        val mode = if (targetGrp != null && targetGrp.id != _activeGroupId.value) targetGrp.autoTypeSelectionMode else _autoTypeSelectionMode.value

        val nextMarkId = getNextMarkIdForGroup(targetGrpId)
        val markIdx = marksList.indexOfFirst { it.id == nextMarkId }
        val stepNo = if (markIdx >= 0) markIdx + 1 else 1
        val markObj = marksList.getOrNull(markIdx) ?: marksList.firstOrNull { it.enabled }
        val markTag = if (markObj != null) "Mark #$stepNo (${markObj.type.shortLabel})" else "Mark #$stepNo"

        val koreoPart = if (isMulti && targetGrp != null) {
            val kNo = allGroups.indexOfFirst { it.id == targetGrp.id }.let { if (it >= 0) it + 1 else 1 }
            val prog = getTurnProgress(targetGrp.id)
            val cyc = if (targetGrp.repeatCycles > 1) " [${prog.currentCycle}/${targetGrp.repeatCycles}x]" else ""
            "Koreo #$kNo$cyc • "
        } else {
            ""
        }

        val hasAutoTypeMark = marksList.any { it.enabled && it.type == TapMarkActionType.AUTO_TYPE }
        val prog = getTurnProgress(targetGrpId)
        val poolSize = textsList.map { it.trim() }.filter { it.isNotBlank() }.distinct().size
        val textPart = if ((hasAutoTypeMark || prog.totalMessagesSent > 0) && poolSize > 0) {
            if (mode == AutoTypeSelectionMode.SEQUENTIAL) {
                val nextT = ((prog.sequentialTextCursor % poolSize).coerceAtLeast(0)) + 1
                " • Pesan #$nextT/$poolSize"
            } else {
                val lastT = if (prog.lastSentTextIndex in 0 until poolSize) "Terakhir #${prog.lastSentTextIndex + 1}" else "Acak"
                " • Pesan $lastT"
            }
        } else {
            ""
        }
        return "$koreoPart$markTag$textPart"
    }

    fun resetAllTurnProgress() {
        turnProgressByGroup.clear()
        markCustomTextCursorMap.clear()
        synchronized(choreoRandomBagIds) {
            choreoRandomBagIds.clear()
        }
        resumeChoreoGroupId = _activeGroupId.value
        _hasPausedTurnProgress = false
        lastExecutedChoreoId = null
        sameChoreoStreakCount = 0
        if (!_isTapping.value) {
            _activeStepDescription.value = "Standby"
        }
        savePersistedState()
    }

    /**
     * Resolves the exact physical screen coordinate (rawX, rawY) of a mark reticle on screen
     * via View.getLocationOnScreen(IntArray(2)). Pass markId = null for Single Tap target.
     */
    @Volatile
    var onResolveScreenCoordinate: ((markId: String?) -> Pair<Float, Float>?)? = null

    /**
     * Synchronously sets FLAG_NOT_TOUCHABLE on all mark overlay windows on the Main Thread
     * before dispatching any gesture so the gesture never hits our own mark overlay view.
     */
    @Volatile
    var onSetOverlaysPassThrough: ((passThrough: Boolean) -> Boolean)? = null

    /**
     * Returns the true full physical screen dimensions (including status bar, cutout, and navigation bar)
     * so marks can be placed anywhere on the entire screen without bouncing back ("mental").
     */
    fun getRealScreenSize(): Pair<Float, Float> {
        return try {
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bounds = wm.currentWindowMetrics.bounds
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()
                    if (w > 200f && h > 200f) {
                        return w to h
                    }
                }
                @Suppress("DEPRECATION")
                val realMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay?.getRealMetrics(realMetrics)
                if (realMetrics.widthPixels > 200 && realMetrics.heightPixels > 200) {
                    return realMetrics.widthPixels.toFloat() to realMetrics.heightPixels.toFloat()
                }
            }
            val dm = appContext.resources.displayMetrics
            dm.widthPixels.toFloat().coerceAtLeast(360f) to (dm.heightPixels + 240).toFloat().coerceAtLeast(640f)
        } catch (_: Throwable) {
            val dm = appContext.resources.displayMetrics
            dm.widthPixels.toFloat().coerceAtLeast(360f) to (dm.heightPixels + 240).toFloat().coerceAtLeast(640f)
        }
    }

    private suspend fun resolveExactTargetPoint(markId: String?, fallbackX: Float, fallbackY: Float): Pair<Float, Float> {
        val resolver = onResolveScreenCoordinate ?: return fallbackX to fallbackY
        return try {
            withContext(Dispatchers.Main.immediate) {
                resolver.invoke(markId)
            } ?: (fallbackX to fallbackY)
        } catch (_: Throwable) {
            fallbackX to fallbackY
        }
    }

    private suspend fun ensureOverlaysPassThrough(passThrough: Boolean) {
        val setter = onSetOverlaysPassThrough ?: return
        try {
            val changed = withContext(Dispatchers.Main.immediate) {
                setter.invoke(passThrough)
            }
            if (changed && passThrough) {
                delay(28L)
            }
        } catch (_: Throwable) {}
    }

    init {
        val (realW, realH) = getRealScreenSize()
        val defaultX = if (realW >= 720f) 700f else realW * 0.85f
        val defaultY = if (realH >= 1600f) 1550f else realH * 0.72f
        _targetX.value = defaultX.coerceIn(2f, realW - 2f)
        _targetY.value = defaultY.coerceIn(2f, realH - 2f)

        loadPersistedState()
        // Always unify into MULTI_MARK mode so Tap Love is integrated with other marks in choreography
        _executionMode.value = TapExecutionMode.MULTI_MARK
        if (_marks.value.isEmpty()) {
            resetMarksToDefaultTemplate()
        }
    }

    fun resetMarksToDefaultTemplate() {
        val (w, h) = getRealScreenSize()

        val defaultList = listOf(
            TapMarkItem(
                id = "mark_1",
                type = TapMarkActionType.CLICK,
                enabled = true,
                x = (w * 0.50f).coerceIn(10f, w - 10f),
                y = (h * 0.44f).coerceIn(10f, h - 10f),
                isDoubleTap = false,
                customDelayMs = 0L
            ),
            TapMarkItem(
                id = "mark_2",
                type = TapMarkActionType.LOVE,
                enabled = true,
                x = (w * 0.68f).coerceIn(10f, w - 10f),
                y = (h * 0.52f).coerceIn(10f, h - 10f),
                loveBurstCount = 5,
                customDelayMs = 0L
            ),
            TapMarkItem(
                id = "mark_3",
                type = TapMarkActionType.SCROLL,
                enabled = true,
                x = (w * 0.50f).coerceIn(10f, w - 10f),
                y = (h * 0.64f).coerceIn(10f, h - 10f),
                scrollDirection = TapScrollDirection.UP,
                scrollDistancePx = (h * 0.28f).coerceIn(220f, 750f),
                customDelayMs = 0L
            ),
            TapMarkItem(
                id = "mark_4",
                type = TapMarkActionType.BACK,
                enabled = false,
                x = (w * 0.20f).coerceIn(10f, w - 10f),
                y = (h * 0.80f).coerceIn(10f, h - 10f),
                customDelayMs = 0L
            ),
            TapMarkItem(
                id = "mark_5",
                type = TapMarkActionType.AUTO_TYPE,
                enabled = false,
                x = (w * 0.35f).coerceIn(10f, w - 10f),
                y = (h * 0.88f).coerceIn(10f, h - 10f),
                customDelayMs = 0L
            )
        )
        _marks.value = defaultList
        _selectedMarkId.value = defaultList.firstOrNull()?.id
        getTurnProgress(_activeGroupId.value).apply {
            nextActiveMarkIndex = 0
            nextMarkId = defaultList.firstOrNull { it.enabled }?.id
            currentCycle = 1
        }
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun setExecutionMode(mode: TapExecutionMode) {
        _executionMode.value = TapExecutionMode.MULTI_MARK
        savePersistedState()
    }

    fun toggleExecutionMode(): TapExecutionMode {
        _executionMode.value = TapExecutionMode.MULTI_MARK
        savePersistedState()
        return TapExecutionMode.MULTI_MARK
    }

    fun setChainAllChoreographies(enabled: Boolean) {
        _chainAllChoreographies.value = enabled
        savePersistedState()
    }

    fun toggleChainAllChoreographies(): Boolean {
        val next = !_chainAllChoreographies.value
        _chainAllChoreographies.value = next
        savePersistedState()
        return next
    }

    fun setMarkDisplayMode(mode: MarkDisplayMode) {
        _markDisplayMode.value = mode
        if (mode == MarkDisplayMode.EDITABLE || mode == MarkDisplayMode.LOCKED_GHOST) {
            _isPointerVisible.value = true
        }
        savePersistedState()
    }

    fun isAllMarksLocked(): Boolean {
        return _markDisplayMode.value == MarkDisplayMode.LOCKED_GHOST ||
            _markDisplayMode.value == MarkDisplayMode.LOCKED_HIDDEN
    }

    fun isMarksHiddenFromView(): Boolean {
        return !_isPointerVisible.value || _markDisplayMode.value == MarkDisplayMode.LOCKED_HIDDEN
    }

    fun toggleMarkItemLock(markId: String): Boolean {
        var nextLocked = false
        updateMark(markId) {
            nextLocked = !it.isLocked
            it.copy(isLocked = nextLocked)
        }
        return nextLocked
    }

    fun selectMark(markId: String?) {
        _selectedMarkId.value = markId
    }

    fun addMark(type: TapMarkActionType): TapMarkItem {
        val (w, h) = getRealScreenSize()
        val count = _marks.value.size
        val offset = ((count % 6) - 2) * 38f

        val baseX = when (type) {
            TapMarkActionType.CLICK -> w * 0.52f + offset
            TapMarkActionType.LOVE -> w * 0.68f + offset
            TapMarkActionType.SCROLL -> w * 0.50f + offset
            TapMarkActionType.BACK -> w * 0.20f
            TapMarkActionType.AUTO_TYPE -> w * 0.38f + offset
        }.coerceIn(10f, w - 10f)

        val baseY = when (type) {
            TapMarkActionType.CLICK -> h * 0.46f + offset
            TapMarkActionType.LOVE -> h * 0.52f + offset
            TapMarkActionType.SCROLL -> h * 0.64f + offset
            TapMarkActionType.BACK -> h * 0.80f
            TapMarkActionType.AUTO_TYPE -> h * 0.88f
        }.coerceIn(10f, h - 10f)

        val newItem = TapMarkItem(
            id = "m_${System.currentTimeMillis().toString().takeLast(5)}_${Random.nextInt(10, 99)}",
            type = type,
            enabled = true,
            x = baseX,
            y = baseY,
            isDoubleTap = (type == TapMarkActionType.LOVE),
            loveBurstCount = 5,
            scrollDirection = TapScrollDirection.UP,
            scrollDistancePx = (h * 0.28f).coerceIn(220f, 750f),
            customDelayMs = 0L,
            isLocked = false
        )
        _marks.value = _marks.value + newItem
        _selectedMarkId.value = newItem.id
        // Adding a mark automatically switches to visible editable mode so the user can position it
        if (_markDisplayMode.value == MarkDisplayMode.LOCKED_HIDDEN) {
            _markDisplayMode.value = MarkDisplayMode.EDITABLE
        }
        _isPointerVisible.value = true
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
        return newItem
    }

    fun removeMark(markId: String) {
        val updated = _marks.value.filterNot { it.id == markId }
        _marks.value = updated
        if (_selectedMarkId.value == markId) {
            _selectedMarkId.value = updated.firstOrNull()?.id
        }
        markCustomTextCursorMap.remove(markId)
        val prog = getTurnProgress(_activeGroupId.value)
        if (prog.nextMarkId == markId) {
            prog.nextMarkId = null
        }
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun moveMarkOrder(markId: String, moveUp: Boolean) {
        val current = _marks.value.toMutableList()
        val idx = current.indexOfFirst { it.id == markId }
        if (idx == -1) return
        val targetIdx = if (moveUp) idx - 1 else idx + 1
        if (targetIdx !in current.indices) return
        val item = current.removeAt(idx)
        current.add(targetIdx, item)
        _marks.value = current
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun updateMark(markId: String, transform: (TapMarkItem) -> TapMarkItem) {
        val (realW, realH) = getRealScreenSize()
        val maxW = (realW - 2f).coerceAtLeast(100f)
        val maxH = (realH - 2f).coerceAtLeast(100f)

        _marks.value = _marks.value.map { item ->
            if (item.id == markId) {
                val updated = transform(item)
                updated.copy(
                    x = updated.x.coerceIn(2f, maxW),
                    y = updated.y.coerceIn(2f, maxH),
                    loveBurstCount = updated.loveBurstCount.coerceIn(1, 50)
                )
            } else {
                item
            }
        }
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun updateMarkPosition(markId: String, x: Float, y: Float, select: Boolean = true) {
        val (realW, realH) = getRealScreenSize()
        val safeX = x.coerceIn(2f, (realW - 2f).coerceAtLeast(100f))
        val safeY = y.coerceIn(2f, (realH - 2f).coerceAtLeast(100f))
        val existing = _marks.value.firstOrNull { it.id == markId }
        val posChanged = existing == null ||
            kotlin.math.abs(existing.x - safeX) >= 0.5f ||
            kotlin.math.abs(existing.y - safeY) >= 0.5f

        if (posChanged) {
            _marks.value = _marks.value.map { item ->
                if (item.id == markId) item.copy(x = safeX, y = safeY) else item
            }
            syncActiveChoreographyGroupInMemory()
            savePersistedState()
        }
        if (select && _selectedMarkId.value != markId) {
            _selectedMarkId.value = markId
        }
    }

    // =========================================================================
    // CHOREOGRAPHY GROUP (PRESET GRUP MARK & URUTAN MULTI-KOREOGRAFI) MANAGEMENT
    // =========================================================================

    /**
     * Automatically keeps the currently active choreography group in sync with any mark/delay changes
     * so when cycling through Koreo 1 -> 2 -> 3 -> 4 -> 1, the user's latest changes are always preserved.
     */
    private fun syncActiveChoreographyGroupInMemory() {
        val activeId = _activeGroupId.value ?: return
        val currentGroups = _choreographyGroups.value
        if (currentGroups.none { it.id == activeId }) return
        _choreographyGroups.value = currentGroups.map { grp ->
            if (grp.id == activeId) {
                grp.copy(
                    executionMode = TapExecutionMode.MULTI_MARK,
                    marks = _marks.value.map { it.copy() },
                    targetX = _targetX.value,
                    targetY = _targetY.value,
                    minDelayMs = _minDelayMs.value,
                    maxDelayMs = _maxDelayMs.value,
                    baseIntervalMs = _baseIntervalMs.value,
                    antiRobotEnabled = _antiRobotEnabled.value,
                    speedPreset = _currentPreset.value,
                    autoTypeTexts = _autoTypeTexts.value.toList(),
                    autoTypeSelectionMode = _autoTypeSelectionMode.value,
                    autoSendAfterType = _autoSendAfterType.value,
                    updatedAtMs = System.currentTimeMillis()
                )
            } else {
                grp
            }
        }
    }

    fun saveNewChoreographyGroup(customName: String? = null): TapChoreographyGroup {
        syncActiveChoreographyGroupInMemory()
        val existing = _choreographyGroups.value
        val nextNum = existing.size + 1
        val finalName = customName?.trim()?.takeIf { it.isNotEmpty() } ?: "Koreo #$nextNum"

        val group = TapChoreographyGroup(
            id = "grp_${System.currentTimeMillis()}_${Random.nextInt(10, 99)}",
            name = finalName,
            executionMode = TapExecutionMode.MULTI_MARK,
            marks = _marks.value.map { it.copy() },
            enabledInSequence = true,
            repeatCycles = 1,
            targetX = _targetX.value,
            targetY = _targetY.value,
            minDelayMs = _minDelayMs.value,
            maxDelayMs = _maxDelayMs.value,
            baseIntervalMs = _baseIntervalMs.value,
            antiRobotEnabled = _antiRobotEnabled.value,
            speedPreset = _currentPreset.value,
            autoTypeTexts = _autoTypeTexts.value.toList(),
            autoTypeSelectionMode = _autoTypeSelectionMode.value,
            autoSendAfterType = _autoSendAfterType.value,
            updatedAtMs = System.currentTimeMillis()
        )
        // Jika ini grup pertama yang disimpan dari workspace default, wariskan progres gilirannya
        if (existing.isEmpty()) {
            turnProgressByGroup["__default_group__"]?.let { defProg ->
                val copied = ChoreoTurnProgress(
                    nextActiveMarkIndex = defProg.nextActiveMarkIndex,
                    nextMarkId = defProg.nextMarkId,
                    currentCycle = defProg.currentCycle,
                    sequentialTextCursor = defProg.sequentialTextCursor,
                    lastPickedRandomText = defProg.lastPickedRandomText,
                    lastSentTextIndex = defProg.lastSentTextIndex,
                    lastSentTextPreview = defProg.lastSentTextPreview,
                    totalMessagesSent = defProg.totalMessagesSent
                )
                synchronized(defProg.randomTextBag) {
                    copied.randomTextBag.addAll(defProg.randomTextBag)
                }
                turnProgressByGroup[group.id] = copied
            }
        } else {
            turnProgressByGroup.putIfAbsent(group.id, ChoreoTurnProgress())
        }
        _choreographyGroups.value = existing + group
        _activeGroupId.value = group.id
        if (!_isTapping.value) {
            resumeChoreoGroupId = group.id
        }
        savePersistedState()
        return group
    }

    fun updateActiveChoreographyGroup(): TapChoreographyGroup? {
        val activeId = _activeGroupId.value ?: return null
        var updatedGroup: TapChoreographyGroup? = null
        _choreographyGroups.value = _choreographyGroups.value.map { grp ->
            if (grp.id == activeId) {
                grp.copy(
                    executionMode = TapExecutionMode.MULTI_MARK,
                    marks = _marks.value.map { it.copy() },
                    targetX = _targetX.value,
                    targetY = _targetY.value,
                    minDelayMs = _minDelayMs.value,
                    maxDelayMs = _maxDelayMs.value,
                    baseIntervalMs = _baseIntervalMs.value,
                    antiRobotEnabled = _antiRobotEnabled.value,
                    speedPreset = _currentPreset.value,
                    autoTypeTexts = _autoTypeTexts.value.toList(),
                    autoTypeSelectionMode = _autoTypeSelectionMode.value,
                    autoSendAfterType = _autoSendAfterType.value,
                    updatedAtMs = System.currentTimeMillis()
                ).also { updatedGroup = it }
            } else {
                grp
            }
        }
        if (updatedGroup != null) {
            savePersistedState()
        }
        return updatedGroup
    }

    fun loadChoreographyGroup(groupId: String, saveBeforeSwitch: Boolean = true): TapChoreographyGroup? {
        if (saveBeforeSwitch && !_isTapping.value) {
            syncActiveChoreographyGroupInMemory()
        }
        val grp = _choreographyGroups.value.firstOrNull { it.id == groupId } ?: return null
        _activeGroupId.value = grp.id
        if (!_isTapping.value) {
            resumeChoreoGroupId = grp.id
        }
        _executionMode.value = TapExecutionMode.MULTI_MARK
        _marks.value = grp.marks.map { it.copy() }
        _selectedMarkId.value = _marks.value.firstOrNull()?.id
        _targetX.value = grp.targetX
        _targetY.value = grp.targetY
        _baseIntervalMs.value = grp.baseIntervalMs
        _antiRobotEnabled.value = grp.antiRobotEnabled
        if (grp.antiRobotEnabled && grp.maxDelayMs <= grp.minDelayMs) {
            val base = grp.baseIntervalMs.coerceAtLeast(40L)
            val minD = (base * 0.70f).toLong().coerceAtLeast(30L)
            val maxD = (base * 1.45f).toLong().coerceAtLeast(minD + 20L)
            _minDelayMs.value = minD
            _maxDelayMs.value = maxD
        } else {
            _minDelayMs.value = grp.minDelayMs
            _maxDelayMs.value = grp.maxDelayMs
        }
        _currentPreset.value = grp.speedPreset
        _autoTypeTexts.value = grp.autoTypeTexts.toList()
        // PENTING: Jangan reset giliran pesan (sequentialTextCursor / randomTextBag) milik grup ini!
        // Setiap grup koreografi memiliki ChoreoTurnProgress masing-masing di turnProgressByGroup.
        getTurnProgress(grp.id)
        _autoTypeSelectionMode.value = grp.autoTypeSelectionMode
        _autoSendAfterType.value = grp.autoSendAfterType
        if (!_isTapping.value && _hasPausedTurnProgress) {
            _activeStepDescription.value = "Dijeda • Lanjut: ${getResumeSummaryShort()}"
        }
        savePersistedState()
        return grp
    }

    fun toggleChoreographyGroupEnabled(groupId: String): Boolean {
        var newState = true
        _choreographyGroups.value = _choreographyGroups.value.map { grp ->
            if (grp.id == groupId) {
                newState = !grp.enabledInSequence
                grp.copy(enabledInSequence = newState, updatedAtMs = System.currentTimeMillis())
            } else {
                grp
            }
        }
        savePersistedState()
        return newState
    }

    fun cycleChoreographyGroupRepeat(groupId: String): Int {
        val options = listOf(1, 2, 3, 5, 10)
        var nextRepeat = 1
        _choreographyGroups.value = _choreographyGroups.value.map { grp ->
            if (grp.id == groupId) {
                val curIdx = options.indexOf(grp.repeatCycles)
                nextRepeat = options[(curIdx + 1) % options.size]
                grp.copy(repeatCycles = nextRepeat, updatedAtMs = System.currentTimeMillis())
            } else {
                grp
            }
        }
        savePersistedState()
        return nextRepeat
    }

    fun moveChoreographyGroupOrder(groupId: String, moveUp: Boolean) {
        val current = _choreographyGroups.value.toMutableList()
        val idx = current.indexOfFirst { it.id == groupId }
        if (idx == -1) return
        val targetIdx = if (moveUp) idx - 1 else idx + 1
        if (targetIdx !in current.indices) return
        val item = current.removeAt(idx)
        current.add(targetIdx, item)
        _choreographyGroups.value = current
        savePersistedState()
    }

    fun renameChoreographyGroup(groupId: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        var changed = false
        _choreographyGroups.value = _choreographyGroups.value.map { grp ->
            if (grp.id == groupId) {
                changed = true
                grp.copy(name = trimmed, updatedAtMs = System.currentTimeMillis())
            } else {
                grp
            }
        }
        if (changed) savePersistedState()
        return changed
    }

    fun setRandomChoreographyOrder(random: Boolean) {
        _isRandomChoreographyOrder.value = random
        if (random) {
            _chainAllChoreographies.value = true
        }
        savePersistedState()
    }

    fun toggleRandomChoreographyOrder(): Boolean {
        val next = !_isRandomChoreographyOrder.value
        setRandomChoreographyOrder(next)
        return next
    }

    fun deleteChoreographyGroup(groupId: String): Boolean {
        val before = _choreographyGroups.value
        val after = before.filterNot { it.id == groupId }
        if (after.size == before.size) return false
        _choreographyGroups.value = after
        turnProgressByGroup.remove(groupId)
        synchronized(choreoRandomBagIds) {
            choreoRandomBagIds.remove(groupId)
        }
        if (resumeChoreoGroupId == groupId) {
            resumeChoreoGroupId = after.firstOrNull { it.enabledInSequence }?.id ?: after.firstOrNull()?.id
        }
        if (_activeGroupId.value == groupId) {
            val nextActive = after.firstOrNull()
            _activeGroupId.value = nextActive?.id
            if (nextActive != null) {
                loadChoreographyGroup(nextActive.id, saveBeforeSwitch = false)
            }
        }
        savePersistedState()
        return true
    }

    // =========================================================================
    // AUTO KETIK TEXT LIST MANAGEMENT (EASY DELETE & ADD)
    // =========================================================================

    fun setAutoTypeTexts(texts: List<String>) {
        val cleaned = texts.map { it.trim() }.filter { it.isNotEmpty() }
        _autoTypeTexts.value = cleaned
        val prog = getTurnProgress(_activeGroupId.value)
        if (cleaned.isEmpty()) {
            prog.sequentialTextCursor = 0
            prog.lastSentTextIndex = -1
            synchronized(prog.randomTextBag) {
                prog.randomTextBag.clear()
            }
        } else {
            prog.sequentialTextCursor = prog.sequentialTextCursor.coerceIn(0, cleaned.size - 1)
            synchronized(prog.randomTextBag) {
                prog.randomTextBag.retainAll(cleaned.toSet())
            }
        }
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun addAutoTypeText(text: String): Int {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return 0
        _autoTypeTexts.value = _autoTypeTexts.value + lines
        val prog = getTurnProgress(_activeGroupId.value)
        synchronized(prog.randomTextBag) {
            // Jika sedang dalam putaran acak aktif, masukkan kalimat baru ke antrean acak tanpa mereset kalimat yang sudah terkirim
            if (prog.randomTextBag.isNotEmpty()) {
                lines.forEach { newLine ->
                    if (!prog.randomTextBag.contains(newLine)) {
                        prog.randomTextBag.add(newLine)
                    }
                }
            }
        }
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
        return lines.size
    }

    fun removeAutoTypeTextAt(index: Int): Boolean {
        val current = _autoTypeTexts.value.toMutableList()
        if (index in current.indices) {
            val removedText = current.removeAt(index)
            _autoTypeTexts.value = current
            val prog = getTurnProgress(_activeGroupId.value)
            if (current.isEmpty()) {
                prog.sequentialTextCursor = 0
                prog.lastSentTextIndex = -1
            } else {
                if (index < prog.sequentialTextCursor) {
                    prog.sequentialTextCursor = (prog.sequentialTextCursor - 1).coerceAtLeast(0)
                }
                if (prog.sequentialTextCursor >= current.size) {
                    prog.sequentialTextCursor = 0
                }
                if (prog.lastSentTextIndex == index) {
                    prog.lastSentTextIndex = -1
                } else if (index < prog.lastSentTextIndex) {
                    prog.lastSentTextIndex = (prog.lastSentTextIndex - 1).coerceAtLeast(-1)
                }
            }
            synchronized(prog.randomTextBag) {
                prog.randomTextBag.remove(removedText)
                prog.randomTextBag.retainAll(current.toSet())
            }
            syncActiveChoreographyGroupInMemory()
            savePersistedState()
            return true
        }
        return false
    }

    fun updateAutoTypeTextAt(index: Int, newText: String): Boolean {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return false
        val current = _autoTypeTexts.value.toMutableList()
        if (index in current.indices) {
            val oldText = current[index]
            current[index] = trimmed
            _autoTypeTexts.value = current
            val prog = getTurnProgress(_activeGroupId.value)
            synchronized(prog.randomTextBag) {
                val bagIdx = prog.randomTextBag.indexOf(oldText)
                if (bagIdx >= 0) {
                    prog.randomTextBag[bagIdx] = trimmed
                }
            }
            syncActiveChoreographyGroupInMemory()
            savePersistedState()
            return true
        }
        return false
    }

    fun setAppendLivingIcons(enabled: Boolean) {
        _appendLivingIcons.value = enabled
        savePersistedState()
    }

    fun toggleAppendLivingIcons(): Boolean {
        val next = !_appendLivingIcons.value
        _appendLivingIcons.value = next
        savePersistedState()
        return next
    }

    fun appendLivingIcons(text: String): String {
        if (!_appendLivingIcons.value || text.isBlank()) return text
        var pairStr = "✨🔥"
        val prevPair = lastLivingIconPair
        for (attempt in 0 until 6) {
            val first = LIVING_ICONS_LIST.random()
            val second = LIVING_ICONS_LIST.filter { it != first }.randomOrNull() ?: "✨"
            val candidate = "$first$second"
            if (candidate != prevPair) {
                pairStr = candidate
                break
            }
            pairStr = candidate
        }
        lastLivingIconPair = pairStr
        return "${text.trim()} $pairStr"
    }

    fun clearAllAutoTypeTexts() {
        _autoTypeTexts.value = emptyList()
        val prog = getTurnProgress(_activeGroupId.value)
        prog.sequentialTextCursor = 0
        prog.lastSentTextIndex = -1
        prog.lastSentTextPreview = null
        synchronized(prog.randomTextBag) {
            prog.randomTextBag.clear()
        }
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun resetDefaultAutoTypeTexts() {
        _autoTypeTexts.value = listOf(
            "Halo kak, spill produknya dong 🙏",
            "Cek keranjang kuning nomor berapa kak?",
            "Mantap banget live nya 🔥",
            "Bantu tap-tap layar yuk teman-teman ❤️"
        )
        val prog = getTurnProgress(_activeGroupId.value)
        prog.sequentialTextCursor = 0
        prog.lastSentTextIndex = -1
        prog.lastSentTextPreview = null
        synchronized(prog.randomTextBag) {
            prog.randomTextBag.clear()
        }
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun setAutoTypeSelectionMode(mode: AutoTypeSelectionMode) {
        _autoTypeSelectionMode.value = mode
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun toggleAutoTypeSelectionMode(): AutoTypeSelectionMode {
        val next = _autoTypeSelectionMode.value.toggle()
        _autoTypeSelectionMode.value = next
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
        return next
    }

    fun toggleAutoSendAfterType(): Boolean {
        val next = !_autoSendAfterType.value
        _autoSendAfterType.value = next
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
        return next
    }

    private fun pickNextAutoTypeText(mark: TapMarkItem): String {
        val prog = getTurnProgress(_activeGroupId.value)
        val raw = if (mark.customText.isNotBlank()) {
            // Jika mark punya teks khusus sendiri (bisa beberapa kalimat dipisah '|' atau baris baru),
            // maka mark ini punya giliran (hiliran) sendiri di markCustomTextCursorMap!
            val customOptions = mark.customText
                .split('|', '\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (customOptions.size <= 1) {
                mark.customText.trim()
            } else {
                val cur = markCustomTextCursorMap.getOrDefault(mark.id, 0).coerceAtLeast(0)
                val chosen = customOptions[cur % customOptions.size]
                markCustomTextCursorMap[mark.id] = (cur + 1) % customOptions.size
                chosen
            }
        } else {
            val pool = _autoTypeTexts.value.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (pool.isEmpty()) {
                "Halo kak 🙏"
            } else if (pool.size == 1) {
                prog.lastSentTextIndex = 0
                prog.lastSentTextPreview = pool.first()
                prog.sequentialTextCursor = 0
                prog.totalMessagesSent += 1
                pool.first()
            } else {
                when (_autoTypeSelectionMode.value) {
                    AutoTypeSelectionMode.RANDOM -> {
                        synchronized(prog.randomTextBag) {
                            // Buang item di bag yang sudah tidak ada di pool tanpa menghapus sisa giliran yang valid
                            prog.randomTextBag.retainAll(pool.toSet())
                            if (prog.randomTextBag.isEmpty()) {
                                val refilled = pool.shuffled().toMutableList()
                                // Cegah kalimat pertama di siklus baru sama dengan kalimat terakhir di siklus lama
                                val prev = prog.lastPickedRandomText
                                if (prev != null && refilled.size > 1 && refilled.first() == prev) {
                                    val swapIdx = Random.nextInt(1, refilled.size)
                                    val tmp = refilled[0]
                                    refilled[0] = refilled[swapIdx]
                                    refilled[swapIdx] = tmp
                                }
                                prog.randomTextBag.addAll(refilled)
                            }
                            val picked = prog.randomTextBag.removeAt(0)
                            prog.lastPickedRandomText = picked
                            val poolIdx = pool.indexOf(picked)
                            prog.lastSentTextIndex = poolIdx
                            prog.lastSentTextPreview = picked
                            if (poolIdx >= 0) {
                                prog.sequentialTextCursor = (poolIdx + 1) % pool.size
                            }
                            prog.totalMessagesSent += 1
                            picked
                        }
                    }
                    AutoTypeSelectionMode.SEQUENTIAL -> {
                        val idx = (prog.sequentialTextCursor % pool.size).coerceAtLeast(0)
                        val picked = pool[idx]
                        prog.lastSentTextIndex = idx
                        prog.lastSentTextPreview = picked
                        prog.sequentialTextCursor = (idx + 1) % pool.size
                        prog.totalMessagesSent += 1
                        picked
                    }
                }
            }
        }
        return appendLivingIcons(raw)
    }

    fun setTargetPosition(x: Float, y: Float) {
        val (realW, realH) = getRealScreenSize()
        val safeX = x.coerceIn(2f, (realW - 2f).coerceAtLeast(100f))
        val safeY = y.coerceIn(2f, (realH - 2f).coerceAtLeast(100f))
        _targetX.value = safeX
        _targetY.value = safeY
        savePersistedState()
    }

    fun moveTargetPosition(dx: Float, dy: Float): Boolean {
        if (isAllMarksLocked()) {
            return false
        }
        val targetId = _selectedMarkId.value
            ?: _marks.value.firstOrNull { it.enabled && it.type.needsCoordinate }?.id
            ?: _marks.value.firstOrNull { it.type.needsCoordinate }?.id
        if (targetId != null) {
            val mark = _marks.value.firstOrNull { it.id == targetId }
            if (mark != null) {
                if (mark.isLocked) return false
                updateMarkPosition(targetId, mark.x + dx, mark.y + dy)
                return true
            }
        }
        setTargetPosition(_targetX.value + dx, _targetY.value + dy)
        return true
    }

    fun resetTargetPosition() {
        val (realW, realH) = getRealScreenSize()
        val targetId = _selectedMarkId.value ?: _marks.value.firstOrNull()?.id
        if (targetId != null) {
            updateMarkPosition(targetId, realW * 0.5f, realH * 0.5f)
            return
        }
        setTargetPosition(realW * 0.85f, realH * 0.72f)
    }

    fun setPointerVisible(visible: Boolean) {
        _isPointerVisible.value = visible
        if (visible && _markDisplayMode.value == MarkDisplayMode.LOCKED_HIDDEN) {
            _markDisplayMode.value = MarkDisplayMode.EDITABLE
        }
    }

    fun togglePointerVisible() {
        val next = !_isPointerVisible.value
        _isPointerVisible.value = next
        if (next && _markDisplayMode.value == MarkDisplayMode.LOCKED_HIDDEN) {
            _markDisplayMode.value = MarkDisplayMode.EDITABLE
        }
    }

    fun setMarkSizeDp(sizeDp: Int) {
        val safe = sizeDp.coerceIn(28, 88)
        if (_markSizeDp.value != safe) {
            _markSizeDp.value = safe
            prefs.edit().putInt(KEY_MARK_SIZE_DP, safe).apply()
            savePersistedState()
        }
    }

    fun changeMarkSize(deltaDp: Int) {
        setMarkSizeDp(_markSizeDp.value + deltaDp)
    }

    fun setSpeedPreset(preset: TapSpeedPreset) {
        _currentPreset.value = preset
        _minDelayMs.value = preset.minDelayMs
        _maxDelayMs.value = preset.maxDelayMs
        _baseIntervalMs.value = preset.intervalMs
        _lastGeneratedDelayMs.value = preset.intervalMs
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun setCustomDelayRange(minMs: Long, maxMs: Long) {
        val safeMin = minMs.coerceIn(30L, 60000L)
        val safeMax = maxMs.coerceIn(safeMin, 120000L)
        _minDelayMs.value = safeMin
        _maxDelayMs.value = safeMax
        _currentPreset.value = TapSpeedPreset.CUSTOM
        _baseIntervalMs.value = (safeMin + safeMax) / 2
        _lastGeneratedDelayMs.value = _baseIntervalMs.value
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun setBaseDelay(delayMs: Long) {
        val clamped = delayMs.coerceIn(40L, 60000L)
        _baseIntervalMs.value = clamped
        if (_antiRobotEnabled.value) {
            val minD = (clamped * 0.70f).toLong().coerceAtLeast(30L)
            val maxD = (clamped * 1.45f).toLong().coerceAtLeast(minD + 20L)
            _minDelayMs.value = minD
            _maxDelayMs.value = maxD
        } else {
            _minDelayMs.value = clamped
            _maxDelayMs.value = clamped
        }
        _currentPreset.value = TapSpeedPreset.CUSTOM
        _lastGeneratedDelayMs.value = clamped
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun setAntiRobot(enabled: Boolean) {
        _antiRobotEnabled.value = enabled
        if (!enabled) {
            val base = _baseIntervalMs.value
            _minDelayMs.value = base
            _maxDelayMs.value = base
        } else {
            val curMin = _minDelayMs.value
            val curMax = _maxDelayMs.value
            if (curMax <= curMin) {
                val base = _baseIntervalMs.value
                val minD = (base * 0.70f).toLong().coerceAtLeast(30L)
                val maxD = (base * 1.45f).toLong().coerceAtLeast(minD + 20L)
                _minDelayMs.value = minD
                _maxDelayMs.value = maxD
            }
        }
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
    }

    fun resetCount() {
        _totalTaps.value = 0L
        resetAllTurnProgress()
    }

    fun toggleTapping(): Boolean {
        return if (_isTapping.value) {
            stopTapping()
            false
        } else {
            startTapping()
        }
    }

    fun startTapping(): Boolean {
        if (_isTapping.value) return true
        // Save any unsaved mark adjustments to the active choreography group before starting
        syncActiveChoreographyGroupInMemory()
        savePersistedState()
        _executionMode.value = TapExecutionMode.MULTI_MARK
        _isTapping.value = true

        tapJob?.cancel()
        tapJob = scope.launch {
            runMultiMarkLoop()
        }
        return true
    }

    fun executeSingleMarkOnce(markId: String) {
        val all = _marks.value
        val idx = all.indexOfFirst { it.id == markId }
        if (idx == -1) return
        val mark = all[idx]
        scope.launch {
            _activeExecutingMarkId.value = mark.id
            ensureOverlaysPassThrough(true)
            executeMarkStep(mark, idx + 1, null)
            delay(220L)
            if (!_isTapping.value) {
                ensureOverlaysPassThrough(isAllMarksLocked())
                _activeExecutingMarkId.value = null
                _activeStepDescription.value = if (_hasPausedTurnProgress) {
                    "Dijeda • Lanjut: ${getResumeSummaryShort()}"
                } else {
                    "Standby"
                }
            }
        }
    }

    private suspend fun executeMarkStep(mark: TapMarkItem, stepNum: Int, choreoPrefix: String?) {
        val isRandomDelay = _antiRobotEnabled.value
        val currentService = VcamAccessibilityService.instance
        val prefix = if (!choreoPrefix.isNullOrBlank()) "$choreoPrefix • " else ""

        // Ensure mark overlays are touch-transparent before firing gesture
        ensureOverlaysPassThrough(true)

        // Resolve 100% exact physical screen pixel of the mark reticle center (+)
        // If marks are hidden (LOCKED_HIDDEN), fallback mark.x/mark.y already stores the calibrated physical coordinate!
        val (exactX, exactY) = resolveExactTargetPoint(mark.id, mark.x, mark.y)

        when (mark.type) {
            TapMarkActionType.CLICK -> {
                val (sW, sH) = getRealScreenSize()
                // Micro-jitter manusiawi (±3.5px) & variasi durasi ketuk saat Anti-Robot aktif
                val jitterX = if (isRandomDelay) Random.nextDouble(-3.5, 3.5).toFloat() else 0f
                val jitterY = if (isRandomDelay) Random.nextDouble(-3.5, 3.5).toFloat() else 0f
                val tapX = (exactX + jitterX).coerceIn(2f, sW - 2f)
                val tapY = (exactY + jitterY).coerceIn(2f, sH - 2f)
                val durMs = if (isRandomDelay) Random.nextLong(42L, 74L) else 55L
                val dblDurMs = if (isRandomDelay) Random.nextLong(38L, 56L) else 45L
                val dblGapMs = if (isRandomDelay) Random.nextLong(46L, 68L) else 55L

                _activeStepDescription.value = "$prefix#$stepNum KLIK ${if (mark.isDoubleTap) "2x" else "1x"} (${tapX.toInt()},${tapY.toInt()})"
                var ok = false
                if (currentService != null) {
                    ok = if (mark.isDoubleTap) {
                        currentService.performDoubleTapAsync(tapX, tapY, dblDurMs, dblGapMs)
                    } else {
                        currentService.performTapAsync(tapX, tapY, durMs)
                    }
                }
                if (!ok && currentService == null) {
                    ok = if (mark.isDoubleTap) {
                        executeRootDoubleTap(tapX.toInt(), tapY.toInt())
                    } else {
                        executeRootTap(tapX.toInt(), tapY.toInt())
                    }
                }
                _totalTaps.value = _totalTaps.value + if (mark.isDoubleTap) 2L else 1L
            }

            TapMarkActionType.LOVE -> {
                val bursts = mark.loveBurstCount.coerceIn(1, 50)
                val (sW, sH) = getRealScreenSize()

                for (b in 1..bursts) {
                    if (!_isTapping.value && _activeExecutingMarkId.value != mark.id) break
                    // Micro-jitter (±4.5px) aktif meskipun bursts == 1 selama Anti-Robot menyala
                    val jitterX = if (isRandomDelay) Random.nextDouble(-4.5, 4.5).toFloat() else 0f
                    val jitterY = if (isRandomDelay) Random.nextDouble(-4.5, 4.5).toFloat() else 0f
                    val tapX = (exactX + jitterX).coerceIn(2f, sW - 2f)
                    val tapY = (exactY + jitterY).coerceIn(2f, sH - 2f)
                    val tapDur = if (isRandomDelay) Random.nextLong(36L, 52L) else 42L
                    val tapInterval = if (isRandomDelay) Random.nextLong(44L, 64L) else 52L

                    _activeStepDescription.value = "$prefix#$stepNum ❤️ LOVE $b/${bursts}x (${tapX.toInt()},${tapY.toInt()})"
                    var ok = false
                    if (currentService != null) {
                        ok = currentService.performDoubleTapAsync(
                            x = tapX,
                            y = tapY,
                            tapDurationMs = tapDur,
                            intervalBetweenTapsMs = tapInterval
                        )
                    }
                    if (!ok && currentService == null) {
                        ok = executeRootDoubleTap(tapX.toInt(), tapY.toInt())
                    }
                    _totalTaps.value = _totalTaps.value + 2L

                    if (b < bursts) {
                        val burstGap = if (isRandomDelay) Random.nextLong(95L, 155L) else 115L
                        delay(burstGap)
                    }
                }
            }

            TapMarkActionType.SCROLL -> {
                val (sW, sH) = getRealScreenSize()
                val baseDist = mark.scrollDistancePx.coerceIn(150f, maxOf(sW, sH) * 0.72f)
                val distFactor = if (isRandomDelay) Random.nextDouble(0.94, 1.06).toFloat() else 1f
                val dist = (baseDist * distFactor).coerceIn(140f, maxOf(sW, sH) * 0.75f)

                val startJitterX = if (isRandomDelay) Random.nextDouble(-3.5, 3.5).toFloat() else 0f
                val startJitterY = if (isRandomDelay) Random.nextDouble(-3.5, 3.5).toFloat() else 0f
                val endJitterX = if (isRandomDelay) Random.nextDouble(-4.0, 4.0).toFloat() else 0f
                val endJitterY = if (isRandomDelay) Random.nextDouble(-4.0, 4.0).toFloat() else 0f

                val startX = (exactX + startJitterX).coerceIn(10f, sW - 10f)
                val startY = (exactY + startJitterY).coerceIn(10f, sH - 10f)

                val (endX, endY) = when (mark.scrollDirection) {
                    TapScrollDirection.UP -> (startX + endJitterX).coerceIn(10f, sW - 10f) to (startY - dist + endJitterY).coerceIn(10f, sH - 10f)
                    TapScrollDirection.DOWN -> (startX + endJitterX).coerceIn(10f, sW - 10f) to (startY + dist + endJitterY).coerceIn(10f, sH - 10f)
                    TapScrollDirection.LEFT -> (startX - dist + endJitterX).coerceIn(10f, sW - 10f) to (startY + endJitterY).coerceIn(10f, sH - 10f)
                    TapScrollDirection.RIGHT -> (startX + dist + endJitterX).coerceIn(10f, sW - 10f) to (startY + endJitterY).coerceIn(10f, sH - 10f)
                }
                val swipeDur = if (isRandomDelay) Random.nextLong(225L, 335L) else 260L

                _activeStepDescription.value = "$prefix#$stepNum SCROLL ${mark.scrollDirection.label.uppercase()}"
                var ok = false
                if (currentService != null) {
                    ok = currentService.performSwipeAsync(startX, startY, endX, endY, swipeDur)
                }
                if (!ok && currentService == null) {
                    ok = executeRootSwipe(startX.toInt(), startY.toInt(), endX.toInt(), endY.toInt(), swipeDur.toInt())
                }
                _totalTaps.value = _totalTaps.value + 1L
            }

            TapMarkActionType.BACK -> {
                _activeStepDescription.value = "$prefix#$stepNum BACK"
                var ok = false
                if (currentService != null) {
                    ok = currentService.performBackAction()
                }
                if (!ok && currentService == null) {
                    ok = executeRootBack()
                }
                _totalTaps.value = _totalTaps.value + 1L
            }

            TapMarkActionType.AUTO_TYPE -> {
                val chosenText = pickNextAutoTypeText(mark)
                val prog = getTurnProgress(_activeGroupId.value)
                val msgNoBadge = if (prog.lastSentTextIndex >= 0) " [Pesan #${prog.lastSentTextIndex + 1}]" else ""
                val (sW, sH) = getRealScreenSize()
                val jitterX = if (isRandomDelay) Random.nextDouble(-3.0, 3.0).toFloat() else 0f
                val jitterY = if (isRandomDelay) Random.nextDouble(-3.0, 3.0).toFloat() else 0f
                val tapX = (exactX + jitterX).coerceIn(2f, sW - 2f)
                val tapY = (exactY + jitterY).coerceIn(2f, sH - 2f)

                _activeStepDescription.value = "$prefix#$stepNum KETIK$msgNoBadge \"${chosenText.take(12)}..\""
                var ok = false
                if (currentService != null) {
                    ok = currentService.performAutoTypeAsync(
                        x = tapX,
                        y = tapY,
                        textToType = chosenText,
                        isAntiBot = isRandomDelay,
                        autoSendEnter = _autoSendAfterType.value
                    )
                }
                if (!ok && currentService == null) {
                    ok = executeRootTap(tapX.toInt(), tapY.toInt())
                }
                _totalTaps.value = _totalTaps.value + 1L
            }
        }
    }

    private suspend fun applyChoreographyGroupForExecution(grp: TapChoreographyGroup) {
        withContext(Dispatchers.Main.immediate) {
            _activeGroupId.value = grp.id
            _executionMode.value = TapExecutionMode.MULTI_MARK
            _marks.value = grp.marks.map { it.copy() }
            _selectedMarkId.value = _marks.value.firstOrNull()?.id
            _baseIntervalMs.value = grp.baseIntervalMs
            _antiRobotEnabled.value = grp.antiRobotEnabled
            if (grp.antiRobotEnabled && grp.maxDelayMs <= grp.minDelayMs) {
                val base = grp.baseIntervalMs.coerceAtLeast(40L)
                val minD = (base * 0.70f).toLong().coerceAtLeast(30L)
                val maxD = (base * 1.45f).toLong().coerceAtLeast(minD + 20L)
                _minDelayMs.value = minD
                _maxDelayMs.value = maxD
            } else {
                _minDelayMs.value = grp.minDelayMs
                _maxDelayMs.value = grp.maxDelayMs
            }
            _currentPreset.value = grp.speedPreset
            _autoTypeTexts.value = grp.autoTypeTexts.toList()
            // JANGAN reset sequentialTextCursor / randomTextBag!
            // Masing-masing koreografi menyimpan gilirannya sendiri di turnProgressByGroup[grp.id].
            getTurnProgress(grp.id)
            _autoTypeSelectionMode.value = grp.autoTypeSelectionMode
            _autoSendAfterType.value = grp.autoSendAfterType
        }
        // Give WindowManager a brief frame if reticles are visible so coordinates are ready
        delay(35L)
        ensureOverlaysPassThrough(true)
    }

    private fun pickNextRandomChoreoGroup(enabledGroups: List<TapChoreographyGroup>): TapChoreographyGroup {
        if (enabledGroups.size <= 1) return enabledGroups.first()
        if (enabledGroups.size == 2) {
            val prevId = lastExecutedChoreoId
            return if (prevId != null && sameChoreoStreakCount >= 2) {
                enabledGroups.firstOrNull { it.id != prevId } ?: enabledGroups.random()
            } else {
                enabledGroups[Random.nextInt(enabledGroups.size)]
            }
        }
        val validIds = enabledGroups.map { it.id }.toSet()
        synchronized(choreoRandomBagIds) {
            choreoRandomBagIds.retainAll(validIds)
            if (choreoRandomBagIds.isEmpty()) {
                val prevId = lastExecutedChoreoId ?: _activeGroupId.value
                val shuffled = enabledGroups.map { it.id }.shuffled().toMutableList()
                if (prevId != null && shuffled.size > 1 && shuffled.first() == prevId) {
                    val swapIdx = Random.nextInt(1, shuffled.size)
                    val tmp = shuffled[0]
                    shuffled[0] = shuffled[swapIdx]
                    shuffled[swapIdx] = tmp
                }
                choreoRandomBagIds.addAll(shuffled)
            }
            val nextId = choreoRandomBagIds.removeAt(0)
            return enabledGroups.firstOrNull { it.id == nextId } ?: enabledGroups.first()
        }
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.runMultiMarkLoop() {
        Log.i(TAG, "Multi-Mark & Multi-Choreography engine started (ChainAll=${_chainAllChoreographies.value})")
        ensureOverlaysPassThrough(true)

        while (isActive && _isTapping.value) {
            val allGroups = _choreographyGroups.value
            val enabledGroups = allGroups.filter { it.enabledInSequence && it.marks.any { m -> m.enabled } }

            // Mode 1: Multi-Choreography Loop (Sequential #1 -> #N -> #1 OR Random Koreo)
            if (_chainAllChoreographies.value && enabledGroups.size > 1) {
                val totalGroups = enabledGroups.size
                val isRandomKoreo = _isRandomChoreographyOrder.value

                if (isRandomKoreo) {
                    // Jika ada koreografi yang sedang dijeda di tengah jalan (atau sudah dijadwalkan berikutnya), lanjutkan dulu!
                    val grp: TapChoreographyGroup = resumeChoreoGroupId
                        ?.let { savedId -> enabledGroups.firstOrNull { it.id == savedId } }
                        ?: pickNextRandomChoreoGroup(enabledGroups).also { resumeChoreoGroupId = it.id }

                    if (lastExecutedChoreoId == grp.id) {
                        sameChoreoStreakCount += 1
                    } else {
                        lastExecutedChoreoId = grp.id
                        sameChoreoStreakCount = 1
                    }

                    val realKoreoIdx = allGroups.indexOfFirst { it.id == grp.id }.let { if (it >= 0) it + 1 else 1 }
                    val koreoPrefix = "🎲 Acak #$realKoreoIdx (${grp.name.take(10)})"

                    if (_activeGroupId.value != grp.id || _marks.value != grp.marks) {
                        applyChoreographyGroupForExecution(grp)
                    }

                    val activeMarks = _marks.value.mapIndexedNotNull { idx, item ->
                        if (item.enabled) (idx + 1) to item else null
                    }
                    if (activeMarks.isEmpty()) {
                        resumeChoreoGroupId = null
                        continue
                    }

                    val grpProg = getTurnProgress(grp.id)
                    val cycles = grp.repeatCycles.coerceIn(1, 20)
                    val startCycle = grpProg.currentCycle.coerceIn(1, cycles)

                    for (cycle in startCycle..cycles) {
                        if (!isActive || !_isTapping.value) break
                        grpProg.currentCycle = cycle
                        val cyclePrefix = if (cycles > 1) "$koreoPrefix [${cycle}/${cycles}x]" else koreoPrefix

                        val resolvedStartMarkIdx = grpProg.nextMarkId
                            ?.let { id -> activeMarks.indexOfFirst { it.second.id == id } }
                            ?.takeIf { it >= 0 }
                            ?: grpProg.nextActiveMarkIndex.coerceIn(0, activeMarks.size - 1)

                        for (mIdx in resolvedStartMarkIdx until activeMarks.size) {
                            if (!isActive || !_isTapping.value) break
                            val (stepNum, mark) = activeMarks[mIdx]

                            grpProg.nextActiveMarkIndex = mIdx
                            grpProg.nextMarkId = mark.id
                            _hasPausedTurnProgress = true
                            _activeExecutingMarkId.value = mark.id
                            val isRandomDelay = _antiRobotEnabled.value

                            executeMarkStep(mark, stepNum, cyclePrefix)
                            if (!isActive || !_isTapping.value) break

                            // Majukan giliran tepat setelah aksi mark selesai dieksekusi:
                            val isLastMarkInCycle = (mIdx == activeMarks.size - 1)
                            val isLastCycleInKoreo = (cycle == cycles)
                            if (!isLastMarkInCycle) {
                                grpProg.nextActiveMarkIndex = mIdx + 1
                                grpProg.nextMarkId = activeMarks[mIdx + 1].second.id
                            } else {
                                // Siklus ini selesai; kembalikan pointer mark grup ini ke mark pertama untuk giliran berikutnya
                                grpProg.nextActiveMarkIndex = 0
                                grpProg.nextMarkId = activeMarks.first().second.id
                                if (!isLastCycleInKoreo) {
                                    grpProg.currentCycle = cycle + 1
                                } else {
                                    grpProg.currentCycle = 1
                                    // Siapkan koreografi acak berikutnya agar jika dijeda saat delay, giliran koreo berikutnya sudah tercatat
                                    val nextRandomGrp = pickNextRandomChoreoGroup(enabledGroups)
                                    resumeChoreoGroupId = nextRandomGrp.id
                                }
                            }

                            val calculatedDelay = computeInterMarkDelay(mark.customDelayMs, isRandomDelay)
                            _lastGeneratedDelayMs.value = calculatedDelay
                            val nextInfo = if (isLastMarkInCycle && isLastCycleInKoreo) {
                                "Jeda ${calculatedDelay}ms ➔ 🎲 Lanjut Koreo Acak"
                            } else {
                                "$cyclePrefix • Jeda ${calculatedDelay}ms"
                            }
                            _activeStepDescription.value = nextInfo
                            delay(calculatedDelay)
                        }
                    }
                    continue
                } else {
                    // Mode Urut Koreografi (#1 -> #2 -> ... -> #N -> #1):
                    // Mulai dari koreografi tempat terakhir dijeda (resumeChoreoGroupId)
                    val startGroupIdx = resumeChoreoGroupId
                        ?.let { savedId -> enabledGroups.indexOfFirst { it.id == savedId } }
                        ?.takeIf { it >= 0 }
                        ?: enabledGroups.indexOfFirst { it.id == _activeGroupId.value }.coerceAtLeast(0)

                    for (gIdx in startGroupIdx until enabledGroups.size) {
                        if (!isActive || !_isTapping.value) break
                        val grp = enabledGroups[gIdx]
                        resumeChoreoGroupId = grp.id

                        val koreoPrefix = "Koreo ${gIdx + 1}/$totalGroups (${grp.name.take(10)})"

                        if (_activeGroupId.value != grp.id || _marks.value != grp.marks) {
                            applyChoreographyGroupForExecution(grp)
                        }

                        val activeMarks = _marks.value.mapIndexedNotNull { idx, item ->
                            if (item.enabled) (idx + 1) to item else null
                        }
                        if (activeMarks.isEmpty()) {
                            val nextGrp = enabledGroups[(gIdx + 1) % enabledGroups.size]
                            resumeChoreoGroupId = nextGrp.id
                            continue
                        }

                        val grpProg = getTurnProgress(grp.id)
                        val cycles = grp.repeatCycles.coerceIn(1, 20)
                        val startCycle = grpProg.currentCycle.coerceIn(1, cycles)

                        for (cycle in startCycle..cycles) {
                            if (!isActive || !_isTapping.value) break
                            grpProg.currentCycle = cycle
                            val cyclePrefix = if (cycles > 1) "$koreoPrefix [${cycle}/${cycles}x]" else koreoPrefix

                            val resolvedStartMarkIdx = grpProg.nextMarkId
                                ?.let { id -> activeMarks.indexOfFirst { it.second.id == id } }
                                ?.takeIf { it >= 0 }
                                ?: grpProg.nextActiveMarkIndex.coerceIn(0, activeMarks.size - 1)

                            for (mIdx in resolvedStartMarkIdx until activeMarks.size) {
                                if (!isActive || !_isTapping.value) break
                                val (stepNum, mark) = activeMarks[mIdx]

                                grpProg.nextActiveMarkIndex = mIdx
                                grpProg.nextMarkId = mark.id
                                _hasPausedTurnProgress = true
                                _activeExecutingMarkId.value = mark.id
                                val isRandomDelay = _antiRobotEnabled.value

                                executeMarkStep(mark, stepNum, cyclePrefix)
                                if (!isActive || !_isTapping.value) break

                                // Majukan giliran tepat setelah aksi mark selesai dieksekusi:
                                val isLastMarkInCycle = (mIdx == activeMarks.size - 1)
                                val isLastCycleInKoreo = (cycle == cycles)
                                if (!isLastMarkInCycle) {
                                    grpProg.nextActiveMarkIndex = mIdx + 1
                                    grpProg.nextMarkId = activeMarks[mIdx + 1].second.id
                                } else {
                                    // Siklus selesai, reset mark ke awal untuk giliran koreo ini berikutnya
                                    grpProg.nextActiveMarkIndex = 0
                                    grpProg.nextMarkId = activeMarks.first().second.id
                                    if (!isLastCycleInKoreo) {
                                        grpProg.currentCycle = cycle + 1
                                    } else {
                                        grpProg.currentCycle = 1
                                        val nextGrp = enabledGroups[(gIdx + 1) % enabledGroups.size]
                                        resumeChoreoGroupId = nextGrp.id
                                    }
                                }

                                val calculatedDelay = computeInterMarkDelay(mark.customDelayMs, isRandomDelay)
                                _lastGeneratedDelayMs.value = calculatedDelay
                                val nextInfo = if (isLastMarkInCycle && isLastCycleInKoreo) {
                                    val nextKoreoNum = if ((gIdx + 1) >= totalGroups) 1 else (gIdx + 2)
                                    "Jeda ${calculatedDelay}ms ➔ Lanjut Koreo #$nextKoreoNum"
                                } else {
                                    "$cyclePrefix • Jeda ${calculatedDelay}ms"
                                }
                                _activeStepDescription.value = nextInfo
                                delay(calculatedDelay)
                            }
                        }
                    }
                    continue
                }
            }

            // Mode 2: Single Active Choreography (or when only 1 choreography group exists)
            if (_chainAllChoreographies.value && enabledGroups.size == 1) {
                val singleGrp = enabledGroups.first()
                if (_activeGroupId.value != singleGrp.id) {
                    applyChoreographyGroupForExecution(singleGrp)
                }
            }

            val allMarks = _marks.value
            val activeMarks = allMarks.mapIndexedNotNull { idx, item ->
                if (item.enabled) (idx + 1) to item else null
            }
            if (activeMarks.isEmpty()) {
                _activeStepDescription.value = "Tidak ada titik mark aktif"
                delay(400L)
                continue
            }

            val activeGrpId = _activeGroupId.value
            resumeChoreoGroupId = activeGrpId
            val grpProg = getTurnProgress(activeGrpId)
            val activeGrpName = _choreographyGroups.value.firstOrNull { it.id == activeGrpId }?.name
            val singlePrefix = activeGrpName?.take(12)

            val resolvedStartMarkIdx = grpProg.nextMarkId
                ?.let { id -> activeMarks.indexOfFirst { it.second.id == id } }
                ?.takeIf { it >= 0 }
                ?: grpProg.nextActiveMarkIndex.coerceIn(0, activeMarks.size - 1)

            for (mIdx in resolvedStartMarkIdx until activeMarks.size) {
                if (!isActive || !_isTapping.value) break
                val (stepNum, mark) = activeMarks[mIdx]

                grpProg.nextActiveMarkIndex = mIdx
                grpProg.nextMarkId = mark.id
                _hasPausedTurnProgress = true
                _activeExecutingMarkId.value = mark.id
                val isRandomDelay = _antiRobotEnabled.value

                executeMarkStep(mark, stepNum, singlePrefix)
                if (!isActive || !_isTapping.value) break

                // Majukan giliran mark berikutnya tepat setelah langkah ini selesai
                val nextMIdx = (mIdx + 1) % activeMarks.size
                grpProg.nextActiveMarkIndex = nextMIdx
                grpProg.nextMarkId = activeMarks[nextMIdx].second.id

                val calculatedDelay = computeInterMarkDelay(mark.customDelayMs, isRandomDelay)
                _lastGeneratedDelayMs.value = calculatedDelay
                val nextStepNum = activeMarks[nextMIdx].first
                _activeStepDescription.value = "Jeda ${calculatedDelay}ms ➔ Mark #$nextStepNum"
                delay(calculatedDelay)
            }
        }

        ensureOverlaysPassThrough(isAllMarksLocked())
        _activeExecutingMarkId.value = null
        _activeStepDescription.value = if (_hasPausedTurnProgress) {
            "Dijeda • Lanjut: ${getResumeSummaryShort()}"
        } else {
            "Standby"
        }
        Log.i(TAG, "Multi-Mark & Multi-Choreography engine paused/stopped; turn progress preserved.")
    }

    private fun computeInterMarkDelay(customDelayMs: Long, isRandomDelay: Boolean): Long {
        if (customDelayMs > 0L) {
            return if (isRandomDelay) {
                val minCustom = (customDelayMs * 0.72f).toLong().coerceAtLeast(40L)
                val maxCustom = (customDelayMs * 1.35f).toLong().coerceAtLeast(minCustom + 20L)
                Random.nextLong(minCustom, maxCustom + 1L)
            } else {
                customDelayMs.coerceAtLeast(40L)
            }
        }

        val minD = _minDelayMs.value.coerceAtLeast(30L)
        val maxD = _maxDelayMs.value.coerceAtLeast(minD)
        return if (isRandomDelay) {
            if (maxD > minD) {
                Random.nextLong(minD, maxD + 1L)
            } else {
                val variance = (minD * 0.35f).toLong().coerceAtLeast(25L)
                Random.nextLong((minD - variance).coerceAtLeast(30L), minD + variance + 1L)
            }
        } else {
            _baseIntervalMs.value.coerceAtLeast(40L)
        }
    }

    private fun executeRootDoubleTap(x: Int, y: Int): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("input tap $x $y\n")
                os.writeBytes("usleep 50000\n")
                os.writeBytes("input tap $x $y\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun executeRootTap(x: Int, y: Int): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("input tap $x $y\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun executeRootSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("input swipe $x1 $y1 $x2 $y2 $durationMs\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun executeRootBack(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("input keyevent 4\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    fun stopTapping() {
        _isTapping.value = false
        _activeExecutingMarkId.value = null
        tapJob?.cancel()
        tapJob = null
        _activeStepDescription.value = if (_hasPausedTurnProgress) {
            "Dijeda • Lanjut: ${getResumeSummaryShort()}"
        } else {
            "Standby"
        }
        savePersistedState(immediate = true)
        scope.launch(Dispatchers.Main) {
            try {
                onSetOverlaysPassThrough?.invoke(isAllMarksLocked())
            } catch (_: Throwable) {}
        }
    }

    private fun serializeMarksArray(marksList: List<TapMarkItem>): JSONArray {
        val marksArr = JSONArray()
        marksList.forEach { m ->
            val mObj = JSONObject().apply {
                put("id", m.id)
                put("type", m.type.name)
                put("enabled", m.enabled)
                put("x", m.x.toDouble())
                put("y", m.y.toDouble())
                put("isDoubleTap", m.isDoubleTap)
                put("loveBurstCount", m.loveBurstCount)
                put("scrollDirection", m.scrollDirection.name)
                put("scrollDistancePx", m.scrollDistancePx.toDouble())
                put("customDelayMs", m.customDelayMs)
                put("customText", m.customText)
                put("isLocked", m.isLocked)
            }
            marksArr.put(mObj)
        }
        return marksArr
    }

    private fun parseMarksArray(arr: JSONArray?, maxW: Float, maxH: Float): List<TapMarkItem> {
        if (arr == null) return emptyList()
        val parsed = mutableListOf<TapMarkItem>()
        for (i in 0 until arr.length()) {
            val mObj = arr.optJSONObject(i) ?: continue
            parsed.add(
                TapMarkItem(
                    id = mObj.optString("id").ifBlank { "m_$i" },
                    type = TapMarkActionType.fromNameOrDefault(mObj.optString("type")),
                    enabled = mObj.optBoolean("enabled", true),
                    x = mObj.optDouble("x", 500.0).toFloat().coerceIn(2f, maxW),
                    y = mObj.optDouble("y", 900.0).toFloat().coerceIn(2f, maxH),
                    isDoubleTap = mObj.optBoolean("isDoubleTap", false),
                    loveBurstCount = mObj.optInt("loveBurstCount", 5).coerceIn(1, 50),
                    scrollDirection = TapScrollDirection.fromNameOrDefault(mObj.optString("scrollDirection")),
                    scrollDistancePx = mObj.optDouble("scrollDistancePx", 480.0).toFloat().coerceIn(150f, 1200f),
                    customDelayMs = mObj.optLong("customDelayMs", 0L).coerceAtLeast(0L),
                    customText = mObj.optString("customText", ""),
                    isLocked = mObj.optBoolean("isLocked", false)
                )
            )
        }
        return parsed
    }

    fun exportToJsonObject(): JSONObject {
        val obj = JSONObject()
        try {
            obj.put("executionMode", TapExecutionMode.MULTI_MARK.name)
            obj.put("markDisplayMode", _markDisplayMode.value.name)
            obj.put("chainAllChoreographies", _chainAllChoreographies.value)
            obj.put("isRandomChoreographyOrder", _isRandomChoreographyOrder.value)
            obj.put("targetX", _targetX.value.toDouble())
            obj.put("targetY", _targetY.value.toDouble())
            obj.put("speedPreset", _currentPreset.value.name)
            obj.put("baseIntervalMs", _baseIntervalMs.value)
            obj.put("minDelayMs", _minDelayMs.value)
            obj.put("maxDelayMs", _maxDelayMs.value)
            obj.put("antiRobotEnabled", _antiRobotEnabled.value)
            obj.put("autoTypeSelectionMode", _autoTypeSelectionMode.value.name)
            obj.put("autoSendAfterType", _autoSendAfterType.value)
            obj.put("appendLivingIcons", _appendLivingIcons.value)
            obj.put("markSizeDp", _markSizeDp.value)
            obj.put("activeGroupId", _activeGroupId.value ?: "")
            obj.put("resumeChoreoGroupId", resumeChoreoGroupId ?: "")
            obj.put("hasPausedTurnProgress", _hasPausedTurnProgress)

            val textsArr = JSONArray()
            _autoTypeTexts.value.forEach { textsArr.put(it) }
            obj.put("autoTypeTexts", textsArr)

            obj.put("marks", serializeMarksArray(_marks.value))

            val groupsArr = JSONArray()
            _choreographyGroups.value.forEach { grp ->
                val gObj = JSONObject().apply {
                    put("id", grp.id)
                    put("name", grp.name)
                    put("executionMode", TapExecutionMode.MULTI_MARK.name)
                    put("marks", serializeMarksArray(grp.marks))
                    put("enabledInSequence", grp.enabledInSequence)
                    put("repeatCycles", grp.repeatCycles)
                    put("targetX", grp.targetX.toDouble())
                    put("targetY", grp.targetY.toDouble())
                    put("minDelayMs", grp.minDelayMs)
                    put("maxDelayMs", grp.maxDelayMs)
                    put("baseIntervalMs", grp.baseIntervalMs)
                    put("antiRobotEnabled", grp.antiRobotEnabled)
                    put("speedPreset", grp.speedPreset.name)
                    val gTexts = JSONArray()
                    grp.autoTypeTexts.forEach { gTexts.put(it) }
                    put("autoTypeTexts", gTexts)
                    put("autoTypeSelectionMode", grp.autoTypeSelectionMode.name)
                    put("autoSendAfterType", grp.autoSendAfterType)
                    put("updatedAtMs", grp.updatedAtMs)
                }
                groupsArr.put(gObj)
            }
            obj.put("choreographyGroups", groupsArr)

            // Simpan giliran (turn state) per grup koreografi
            val turnsObj = JSONObject()
            turnProgressByGroup.forEach { (grpKey, prog) ->
                val pObj = JSONObject().apply {
                    put("nextActiveMarkIndex", prog.nextActiveMarkIndex)
                    put("nextMarkId", prog.nextMarkId ?: "")
                    put("currentCycle", prog.currentCycle)
                    put("sequentialTextCursor", prog.sequentialTextCursor)
                    put("lastPickedRandomText", prog.lastPickedRandomText ?: "")
                    put("lastSentTextIndex", prog.lastSentTextIndex)
                    put("lastSentTextPreview", prog.lastSentTextPreview ?: "")
                    put("totalMessagesSent", prog.totalMessagesSent)
                    val bagArr = JSONArray()
                    synchronized(prog.randomTextBag) {
                        prog.randomTextBag.forEach { bagArr.put(it) }
                    }
                    put("randomTextBag", bagArr)
                }
                turnsObj.put(grpKey, pObj)
            }
            obj.put("turnProgressByGroup", turnsObj)

            val choreoBagArr = JSONArray()
            synchronized(choreoRandomBagIds) {
                choreoRandomBagIds.forEach { choreoBagArr.put(it) }
            }
            obj.put("choreoRandomBagIds", choreoBagArr)
        } catch (e: Exception) {
            Log.w(TAG, "Error exporting AutoTap JSON: ${e.message}")
        }
        return obj
    }

    fun importFromJsonObject(obj: JSONObject, persistAfterImport: Boolean = true) {
        try {
            val (realW, realH) = getRealScreenSize()
            val maxW = (realW - 2f).coerceAtLeast(100f)
            val maxH = (realH - 2f).coerceAtLeast(100f)

            _executionMode.value = TapExecutionMode.MULTI_MARK
            if (obj.has("markDisplayMode")) {
                _markDisplayMode.value = MarkDisplayMode.fromNameOrDefault(obj.optString("markDisplayMode"))
            }
            if (obj.has("chainAllChoreographies")) {
                _chainAllChoreographies.value = obj.optBoolean("chainAllChoreographies", true)
            }
            if (obj.has("isRandomChoreographyOrder")) {
                _isRandomChoreographyOrder.value = obj.optBoolean("isRandomChoreographyOrder", false)
            }
            if (obj.has("targetX") && obj.has("targetY")) {
                _targetX.value = obj.optDouble("targetX", 500.0).toFloat().coerceIn(2f, maxW)
                _targetY.value = obj.optDouble("targetY", 1000.0).toFloat().coerceIn(2f, maxH)
            }
            if (obj.has("speedPreset")) {
                TapSpeedPreset.entries.firstOrNull { it.name == obj.optString("speedPreset") }?.let {
                    _currentPreset.value = it
                }
            }
            if (obj.has("baseIntervalMs")) {
                _baseIntervalMs.value = obj.optLong("baseIntervalMs", 1000L).coerceIn(40L, 60000L)
            }
            if (obj.has("minDelayMs") && obj.has("maxDelayMs")) {
                val minD = obj.optLong("minDelayMs", 500L).coerceIn(30L, 60000L)
                val maxD = obj.optLong("maxDelayMs", 1500L).coerceIn(minD, 120000L)
                _minDelayMs.value = minD
                _maxDelayMs.value = maxD
            }
            if (obj.has("antiRobotEnabled")) {
                _antiRobotEnabled.value = obj.optBoolean("antiRobotEnabled", true)
            }
            if (obj.has("autoTypeSelectionMode")) {
                _autoTypeSelectionMode.value = AutoTypeSelectionMode.fromNameOrDefault(obj.optString("autoTypeSelectionMode"))
            }
            if (obj.has("autoSendAfterType")) {
                _autoSendAfterType.value = obj.optBoolean("autoSendAfterType", true)
            }
            if (obj.has("appendLivingIcons")) {
                _appendLivingIcons.value = obj.optBoolean("appendLivingIcons", true)
            }
            if (obj.has("markSizeDp")) {
                val sz = obj.optInt("markSizeDp", 48).coerceIn(28, 88)
                _markSizeDp.value = sz
                prefs.edit().putInt(KEY_MARK_SIZE_DP, sz).apply()
            }
            if (obj.has("autoTypeTexts")) {
                val arr = obj.optJSONArray("autoTypeTexts")
                if (arr != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i).trim()
                        if (s.isNotEmpty()) list.add(s)
                    }
                    _autoTypeTexts.value = list
                }
            }
            if (obj.has("marks")) {
                val parsed = parseMarksArray(obj.optJSONArray("marks"), maxW, maxH)
                if (parsed.isNotEmpty()) {
                    _marks.value = parsed
                    if (_selectedMarkId.value == null || parsed.none { it.id == _selectedMarkId.value }) {
                        _selectedMarkId.value = parsed.first().id
                    }
                }
            }
            if (obj.has("choreographyGroups")) {
                val gArr = obj.optJSONArray("choreographyGroups")
                if (gArr != null) {
                    val parsedGroups = mutableListOf<TapChoreographyGroup>()
                    for (i in 0 until gArr.length()) {
                        val gObj = gArr.optJSONObject(i) ?: continue
                        val gMarks = parseMarksArray(gObj.optJSONArray("marks"), maxW, maxH)
                        val gTexts = mutableListOf<String>()
                        val tArr = gObj.optJSONArray("autoTypeTexts")
                        if (tArr != null) {
                            for (j in 0 until tArr.length()) {
                                val t = tArr.optString(j).trim()
                                if (t.isNotEmpty()) gTexts.add(t)
                            }
                        }
                        val presetName = gObj.optString("speedPreset", TapSpeedPreset.HUMAN_NATURAL.name)
                        val spdPreset = TapSpeedPreset.entries.firstOrNull { it.name == presetName } ?: TapSpeedPreset.HUMAN_NATURAL

                        parsedGroups.add(
                            TapChoreographyGroup(
                                id = gObj.optString("id").ifBlank { "grp_$i" },
                                name = gObj.optString("name").ifBlank { "Koreo #${i + 1}" },
                                executionMode = TapExecutionMode.MULTI_MARK,
                                marks = gMarks,
                                enabledInSequence = gObj.optBoolean("enabledInSequence", true),
                                repeatCycles = gObj.optInt("repeatCycles", 1).coerceIn(1, 20),
                                targetX = gObj.optDouble("targetX", 540.0).toFloat().coerceIn(2f, maxW),
                                targetY = gObj.optDouble("targetY", 1200.0).toFloat().coerceIn(2f, maxH),
                                minDelayMs = gObj.optLong("minDelayMs", 800L),
                                maxDelayMs = gObj.optLong("maxDelayMs", 1800L),
                                baseIntervalMs = gObj.optLong("baseIntervalMs", 1200L),
                                antiRobotEnabled = gObj.optBoolean("antiRobotEnabled", true),
                                speedPreset = spdPreset,
                                autoTypeTexts = gTexts,
                                autoTypeSelectionMode = AutoTypeSelectionMode.fromNameOrDefault(gObj.optString("autoTypeSelectionMode")),
                                autoSendAfterType = gObj.optBoolean("autoSendAfterType", true),
                                updatedAtMs = gObj.optLong("updatedAtMs", System.currentTimeMillis())
                            )
                        )
                    }
                    _choreographyGroups.value = parsedGroups
                }
            }
            val savedActiveGroup = obj.optString("activeGroupId", "").takeIf { it.isNotBlank() }
            if (savedActiveGroup != null && _choreographyGroups.value.any { it.id == savedActiveGroup }) {
                _activeGroupId.value = savedActiveGroup
            } else {
                _activeGroupId.value = _choreographyGroups.value.firstOrNull()?.id
            }

            // Pulihkan giliran (turn state) per grup koreografi
            if (obj.has("turnProgressByGroup")) {
                val turnsObj = obj.optJSONObject("turnProgressByGroup")
                if (turnsObj != null) {
                    turnProgressByGroup.clear()
                    val keys = turnsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val pObj = turnsObj.optJSONObject(k) ?: continue
                        val prog = ChoreoTurnProgress(
                            nextActiveMarkIndex = pObj.optInt("nextActiveMarkIndex", 0).coerceAtLeast(0),
                            nextMarkId = pObj.optString("nextMarkId", "").takeIf { it.isNotBlank() },
                            currentCycle = pObj.optInt("currentCycle", 1).coerceAtLeast(1),
                            sequentialTextCursor = pObj.optInt("sequentialTextCursor", 0).coerceAtLeast(0),
                            lastPickedRandomText = pObj.optString("lastPickedRandomText", "").takeIf { it.isNotBlank() },
                            lastSentTextIndex = pObj.optInt("lastSentTextIndex", -1),
                            lastSentTextPreview = pObj.optString("lastSentTextPreview", "").takeIf { it.isNotBlank() },
                            totalMessagesSent = pObj.optInt("totalMessagesSent", 0).coerceAtLeast(0)
                        )
                        val bagArr = pObj.optJSONArray("randomTextBag")
                        if (bagArr != null) {
                            for (b in 0 until bagArr.length()) {
                                val item = bagArr.optString(b).trim()
                                if (item.isNotEmpty()) prog.randomTextBag.add(item)
                            }
                        }
                        turnProgressByGroup[k] = prog
                    }
                }
            }

            if (obj.has("choreoRandomBagIds")) {
                val bagArr = obj.optJSONArray("choreoRandomBagIds")
                synchronized(choreoRandomBagIds) {
                    choreoRandomBagIds.clear()
                    if (bagArr != null) {
                        for (b in 0 until bagArr.length()) {
                            val id = bagArr.optString(b).trim()
                            if (id.isNotEmpty()) choreoRandomBagIds.add(id)
                        }
                    }
                }
            }

            resumeChoreoGroupId = obj.optString("resumeChoreoGroupId", "").takeIf { it.isNotBlank() } ?: _activeGroupId.value
            _hasPausedTurnProgress = obj.optBoolean("hasPausedTurnProgress", false)
            if (!_isTapping.value) {
                _activeStepDescription.value = if (_hasPausedTurnProgress) {
                    "Dijeda • Lanjut: ${getResumeSummaryShort()}"
                } else {
                    "Standby"
                }
            }

            if (persistAfterImport) {
                savePersistedState(immediate = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error importing AutoTap JSON: ${e.message}")
        }
    }

    private var persistJob: Job? = null

    private fun savePersistedState(immediate: Boolean = false) {
        persistJob?.cancel()
        if (immediate) {
            scope.launch(Dispatchers.IO) {
                try {
                    val json = exportToJsonObject().toString()
                    prefs.edit().putString(KEY_STATE_JSON, json).apply()
                } catch (_: Throwable) {}
            }
        } else {
            persistJob = scope.launch(Dispatchers.IO) {
                delay(350L)
                try {
                    val json = exportToJsonObject().toString()
                    prefs.edit().putString(KEY_STATE_JSON, json).apply()
                } catch (_: Throwable) {}
            }
        }
    }

    private fun loadPersistedState() {
        try {
            val raw = prefs.getString(KEY_STATE_JSON, null) ?: return
            val obj = JSONObject(raw)
            importFromJsonObject(obj, persistAfterImport = false)
        } catch (_: Throwable) {}
    }

    fun reloadFromPrefs() {
        loadPersistedState()
    }
}
