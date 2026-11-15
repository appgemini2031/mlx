package com.tajasb.interaction

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tajasb.eventrelay.ConnectionStatus
import com.tajasb.eventrelay.LiveEventItem
import com.tajasb.eventrelay.LiveEventReceiver
import com.tajasb.eventrelay.LiveEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class CounterEvaluationResult(
    val shouldExecute: Boolean,
    val status: DispatchStatus,
    val updatedCounter: TriggerCounterState,
    val detail: String
)

/**
 * Evaluator murni (JVM-testable) untuk kondisi, counter, threshold, combo, milestone,
 * cooldown, dan reset condition.
 */
object UniversalRuleEvaluator {

    fun resolveGlobalDelayPairMs(
        rule: UniversalTriggerRule,
        globalBeforeMs: Long,
        globalAfterMs: Long,
        excludeCooldownRules: Boolean
    ): Pair<Long, Long> {
        if (excludeCooldownRules && rule.enableVisualCooldownWait) {
            return 0L to 0L
        }
        return globalBeforeMs.coerceIn(0L, 60_000L) to globalAfterMs.coerceIn(0L, 60_000L)
    }

    /**
     * Jika Delay Scan Cooldown Skill ML aktif, Delay Awal & Random Delay dilewati otomatis
     * agar tidak terjadi jeda ganda.
     */
    fun effectiveBeforeDelayMs(rule: UniversalTriggerRule, randomOverride: Long? = null): Long {
        if (rule.enableVisualCooldownWait) return 0L
        val base = if (rule.enableDelayBefore) rule.delayBeforeMs.coerceIn(0L, 60_000L) else 0L
        val rnd = if (rule.enableRandomDelay) {
            val minD = minOf(rule.randomDelayMinMs, rule.randomDelayMaxMs).coerceIn(0L, 30_000L)
            val maxD = maxOf(rule.randomDelayMinMs, rule.randomDelayMaxMs).coerceIn(minD, 30_000L)
            if (randomOverride != null) {
                randomOverride.coerceIn(minD, maxD)
            } else if (maxD > minD) {
                kotlin.random.Random.nextLong(minD, maxD + 1L)
            } else {
                minD
            }
        } else 0L
        return base + rnd
    }

    fun evaluateCounterAndEligibility(
        rule: UniversalTriggerRule,
        currentCounter: TriggerCounterState?,
        event: LiveInteractionEvent,
        isMlbbMatchActive: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): CounterEvaluationResult {
        var counter = currentCounter ?: TriggerCounterState(ruleId = rule.id)

        if (!rule.enabled) {
            return CounterEvaluationResult(
                shouldExecute = false,
                status = DispatchStatus.SKIPPED_CONDITION,
                updatedCounter = counter,
                detail = "Rule nonaktif"
            )
        }

        // Filter nama Gift spesifik agar 1 koin dengan nama berbeda (Mawar, Kopi, Es Krim, dll.) tidak bentrok
        if ((rule.triggerSource == UniversalTriggerSource.GIFT || rule.triggerSource == UniversalTriggerSource.GIFT_QUANTITY) &&
            rule.targetGiftName.isNotBlank() &&
            !GiftCoinCatalog.matchesGiftName(rule.targetGiftName, event.giftName)
        ) {
            val incoming = event.giftName.ifBlank { "(tanpa nama)" }
            return CounterEvaluationResult(
                shouldExecute = false,
                status = DispatchStatus.SKIPPED_CONDITION,
                updatedCounter = counter,
                detail = "Gift '$incoming' bukan '${rule.targetGiftName}'"
            )
        }

        // Minimum Quantity (hanya aktif bila saklar enableMinimumQuantity = true)
        if (rule.enableMinimumQuantity && event.quantity < rule.minimumQuantity) {
            return CounterEvaluationResult(
                shouldExecute = false,
                status = DispatchStatus.SKIPPED_CONDITION,
                updatedCounter = counter,
                detail = "Qty ${event.quantity} < minimum ${rule.minimumQuantity}"
            )
        }

        // Reset idle timeout jika saklar resetCondition = AFTER_IDLE_TIMEOUT aktif
        if (rule.enableResetCondition &&
            rule.resetCondition == ResetConditionType.AFTER_IDLE_TIMEOUT &&
            counter.lastEventAtMs > 0L &&
            (nowMs - counter.lastEventAtMs) >= rule.resetTimeoutMs
        ) {
            counter = counter.copy(
                accumulatedCount = 0,
                comboCount = 0,
                comboWindowStartMs = 0L,
                lastMilestoneReached = 0
            )
        }

        val eventQty = event.quantity.coerceAtLeast(1)

        // Cek kondisi non-counter terlebih dahulu jika diaktifkan
        if ((rule.enableConditions || rule.enableCustomCondition || rule.accumulationMode == AccumulationMode.CUSTOM) &&
            rule.conditions.any { it.enabled }
        ) {
            val nonCounterConds = rule.conditions.filter {
                it.enabled && it.field != ConditionField.ACCUMULATED_COUNT && it.field != ConditionField.COMBO_COUNT
            }
            if (nonCounterConds.isNotEmpty() && !evaluateConditionList(nonCounterConds, event, counter, isMlbbMatchActive)) {
                return CounterEvaluationResult(
                    shouldExecute = false,
                    status = DispatchStatus.SKIPPED_CONDITION,
                    updatedCounter = counter,
                    detail = "Syarat event tidak terpenuhi"
                )
            }
        }

        // Hitung akumulasi & combo baru
        val nextAccumulated = counter.accumulatedCount + eventQty
        val withinComboWindow = counter.comboWindowStartMs > 0L &&
            (nowMs - counter.comboWindowStartMs) <= rule.comboWindowMs.coerceAtLeast(200L)
        val nextComboStart = if (withinComboWindow) counter.comboWindowStartMs else nowMs
        val nextComboCount = if (withinComboWindow) counter.comboCount + eventQty else eventQty

        val previewCounter = counter.copy(
            accumulatedCount = nextAccumulated,
            comboCount = nextComboCount,
            comboWindowStartMs = nextComboStart,
            lastEventAtMs = nowMs
        )

        // Evaluasi kondisi berbasis counter bila aktif
        val counterConds = if (rule.enableConditions || rule.enableCustomCondition || rule.accumulationMode == AccumulationMode.CUSTOM) {
            rule.conditions.filter {
                it.enabled && (it.field == ConditionField.ACCUMULATED_COUNT || it.field == ConditionField.COMBO_COUNT)
            }
        } else {
            emptyList()
        }
        val counterCondsPassed = if (counterConds.isEmpty()) {
            true
        } else {
            evaluateConditionList(counterConds, event, previewCounter, isMlbbMatchActive)
        }

        // Evaluasi mode accumulation utama + saklar independen
        val modeResult = evaluateModeTrigger(rule, counter, previewCounter, counterConds.isNotEmpty(), counterCondsPassed)

        if (!modeResult.first) {
            return CounterEvaluationResult(
                shouldExecute = false,
                status = DispatchStatus.ACCUMULATED,
                updatedCounter = previewCounter,
                detail = modeResult.second
            )
        }

        // Cek Cooldown (jika saklar enableCooldown = true)
        if (rule.enableCooldown && rule.cooldownMs > 0L && counter.lastExecutedAtMs > 0L) {
            val elapsed = nowMs - counter.lastExecutedAtMs
            if (elapsed < rule.cooldownMs) {
                val remaining = rule.cooldownMs - elapsed
                return CounterEvaluationResult(
                    shouldExecute = false,
                    status = DispatchStatus.SKIPPED_COOLDOWN,
                    updatedCounter = previewCounter,
                    detail = "Cooldown aktif (sisa ${remaining}ms)"
                )
            }
        }

        // Jika lolos eksekusi, perbarui milestone & terapkan reset condition jika aktif
        val milestoneStep = rule.milestoneStep.coerceAtLeast(1)
        val reachedMilestone = if (rule.accumulationMode == AccumulationMode.MILESTONE || rule.enableMilestone) {
            (nextAccumulated / milestoneStep) * milestoneStep
        } else {
            previewCounter.lastMilestoneReached
        }

        var postExecCounter = previewCounter.copy(
            lastMilestoneReached = reachedMilestone,
            lastExecutedAtMs = nowMs
        )

        if (rule.enableResetCondition) {
            when (rule.resetCondition) {
                ResetConditionType.AFTER_TRIGGER -> {
                    postExecCounter = postExecCounter.copy(
                        accumulatedCount = 0,
                        comboCount = 0,
                        comboWindowStartMs = 0L,
                        lastMilestoneReached = 0
                    )
                }
                ResetConditionType.ON_COUNT_REACHED -> {
                    if (nextAccumulated >= rule.thresholdValue.coerceAtLeast(1)) {
                        postExecCounter = postExecCounter.copy(
                            accumulatedCount = 0,
                            comboCount = 0,
                            comboWindowStartMs = 0L
                        )
                    }
                }
                ResetConditionType.NONE,
                ResetConditionType.AFTER_IDLE_TIMEOUT,
                ResetConditionType.MANUAL_ONLY -> Unit
            }
        }

        return CounterEvaluationResult(
            shouldExecute = true,
            status = DispatchStatus.EXECUTED,
            updatedCounter = postExecCounter,
            detail = modeResult.second
        )
    }

    private fun evaluateModeTrigger(
        rule: UniversalTriggerRule,
        previousCounter: TriggerCounterState,
        previewCounter: TriggerCounterState,
        hasCounterConditions: Boolean,
        counterCondsPassed: Boolean
    ): Pair<Boolean, String> {
        // Jika ada kondisi counter yang tidak terpenuhi, tahan sebagai akumulasi
        if (hasCounterConditions && !counterCondsPassed) {
            return false to "Terkumpul ${previewCounter.accumulatedCount} (menunggu kondisi counter)"
        }

        val thresholdTarget = rule.thresholdValue.coerceAtLeast(1)
        val milestoneStep = rule.milestoneStep.coerceAtLeast(1)

        // Cek saklar independen tambahan yang aktif meski mode berbeda
        val independentGates = mutableListOf<Pair<Boolean, String>>()
        if (rule.enableThreshold && rule.accumulationMode != AccumulationMode.THRESHOLD) {
            val met = previewCounter.accumulatedCount >= thresholdTarget
            independentGates += met to "Threshold ${previewCounter.accumulatedCount}/$thresholdTarget"
        }
        if (rule.enableCombo && rule.accumulationMode != AccumulationMode.COMBO) {
            val met = previewCounter.comboCount >= thresholdTarget
            independentGates += met to "Combo ${previewCounter.comboCount}/$thresholdTarget"
        }
        if (rule.enableMilestone && rule.accumulationMode != AccumulationMode.MILESTONE) {
            val currentMilestone = (previewCounter.accumulatedCount / milestoneStep) * milestoneStep
            val met = currentMilestone > previousCounter.lastMilestoneReached && currentMilestone > 0
            independentGates += met to "Milestone ${previewCounter.accumulatedCount}/kelipatan $milestoneStep"
        }

        for ((met, msg) in independentGates) {
            if (!met) return false to "Terkumpul: $msg"
        }

        return when (rule.accumulationMode) {
            AccumulationMode.INDIVIDUAL -> {
                true to "Direct #${previewCounter.accumulatedCount}"
            }
            AccumulationMode.ACCUMULATION -> {
                if (hasCounterConditions && counterCondsPassed) {
                    true to "Akumulasi #${previewCounter.accumulatedCount} (Kondisi terpenuhi)"
                } else if (rule.enableIndividual && !rule.enableThreshold && thresholdTarget <= 1) {
                    true to "Akumulasi #${previewCounter.accumulatedCount}"
                } else if (previewCounter.accumulatedCount >= thresholdTarget) {
                    true to "Akumulasi ${previewCounter.accumulatedCount}/$thresholdTarget tercapai"
                } else {
                    false to "Terkumpul ${previewCounter.accumulatedCount}/$thresholdTarget"
                }
            }
            AccumulationMode.THRESHOLD -> {
                if (previewCounter.accumulatedCount >= thresholdTarget) {
                    true to "Threshold ${previewCounter.accumulatedCount}/$thresholdTarget tercapai"
                } else {
                    false to "Menuju threshold ${previewCounter.accumulatedCount}/$thresholdTarget"
                }
            }
            AccumulationMode.COMBO -> {
                if (previewCounter.comboCount >= thresholdTarget) {
                    true to "Combo ${previewCounter.comboCount}/$thresholdTarget (${rule.comboWindowMs}ms)"
                } else {
                    false to "Combo berjalan ${previewCounter.comboCount}/$thresholdTarget"
                }
            }
            AccumulationMode.MILESTONE -> {
                val currentMilestone = (previewCounter.accumulatedCount / milestoneStep) * milestoneStep
                if (currentMilestone > previousCounter.lastMilestoneReached && currentMilestone > 0) {
                    true to "Milestone $currentMilestone tercapai (Total ${previewCounter.accumulatedCount})"
                } else {
                    false to "Progres milestone ${previewCounter.accumulatedCount} (step $milestoneStep)"
                }
            }
            AccumulationMode.RANDOM -> {
                true to "Random selection #${previewCounter.accumulatedCount}"
            }
            AccumulationMode.CUSTOM -> {
                if (!rule.enableConditions && !rule.enableCustomCondition && !hasCounterConditions) {
                    true to "Custom langsung #${previewCounter.accumulatedCount}"
                } else if (counterCondsPassed) {
                    true to "Custom condition terpenuhi (#${previewCounter.accumulatedCount})"
                } else {
                    false to "Menunggu custom condition (#${previewCounter.accumulatedCount})"
                }
            }
        }
    }

    fun evaluateConditionList(
        conditions: List<TriggerCondition>,
        event: LiveInteractionEvent,
        counter: TriggerCounterState,
        isMlbbMatchActive: Boolean
    ): Boolean {
        for (cond in conditions) {
            if (!cond.enabled) continue
            if (!evaluateSingleCondition(cond, event, counter, isMlbbMatchActive)) {
                return false
            }
        }
        return true
    }

    fun evaluateSingleCondition(
        cond: TriggerCondition,
        event: LiveInteractionEvent,
        counter: TriggerCounterState,
        isMlbbMatchActive: Boolean
    ): Boolean {
        val targetRaw = cond.value.trim()
        return when (cond.field) {
            ConditionField.QUANTITY -> compareNumeric(event.quantity.toLong(), cond.operator, targetRaw)
            ConditionField.ACCUMULATED_COUNT -> compareNumeric(counter.accumulatedCount.toLong(), cond.operator, targetRaw)
            ConditionField.COMBO_COUNT -> compareNumeric(counter.comboCount.toLong(), cond.operator, targetRaw)
            ConditionField.DIAMONDS -> compareNumeric(event.diamonds.toLong(), cond.operator, targetRaw)
            ConditionField.GIFT_NAME -> compareText(event.giftName, cond.operator, targetRaw)
            ConditionField.SENDER_NAME -> compareText(event.senderName, cond.operator, targetRaw)
            ConditionField.CHAT_TEXT -> compareText(event.text, cond.operator, targetRaw)
            ConditionField.MATCH_NOT_ACTIVE -> {
                val expectedNotActive = targetRaw.equals("true", ignoreCase = true) || targetRaw == "1" || targetRaw.isEmpty()
                (!isMlbbMatchActive) == expectedNotActive
            }
        }
    }

    private fun compareNumeric(actual: Long, op: ConditionOperator, targetRaw: String): Boolean {
        val target = targetRaw.toLongOrNull() ?: return false
        return when (op) {
            ConditionOperator.EQUALS -> actual == target
            ConditionOperator.NOT_EQUALS -> actual != target
            ConditionOperator.GREATER_OR_EQUAL -> actual >= target
            ConditionOperator.LESS_OR_EQUAL -> actual <= target
            ConditionOperator.CONTAINS -> actual.toString().contains(targetRaw, ignoreCase = true)
            ConditionOperator.MULTIPLE_OF -> target > 0L && actual > 0L && (actual % target == 0L)
        }
    }

    private fun compareText(actual: String, op: ConditionOperator, targetRaw: String): Boolean {
        return when (op) {
            ConditionOperator.EQUALS -> actual.equals(targetRaw, ignoreCase = true)
            ConditionOperator.NOT_EQUALS -> !actual.equals(targetRaw, ignoreCase = true)
            ConditionOperator.CONTAINS -> actual.contains(targetRaw, ignoreCase = true)
            ConditionOperator.GREATER_OR_EQUAL -> actual.length >= (targetRaw.toIntOrNull() ?: 0)
            ConditionOperator.LESS_OR_EQUAL -> actual.length <= (targetRaw.toIntOrNull() ?: Int.MAX_VALUE)
            ConditionOperator.MULTIPLE_OF -> false
        }
    }
}

private data class QueuedDispatchTask(
    val seq: Long,
    val priority: Int,
    val rule: UniversalTriggerRule,
    val event: LiveInteractionEvent,
    val counterSnapshot: TriggerCounterState,
    val matchActiveSnapshot: Boolean,
    val detailNote: String
)

/**
 * Single Trigger Engine untuk Universal Interaction Mapping System (MLBB Special Mode).
 * Mengamati LiveEventReceiver secara pasif (tanpa memodifikasi LiveEventReceiver.kt) dan
 * menjadi satu-satunya jalur eksekusi bagi Live Relay maupun Test Center.
 */
class UniversalTriggerEngine private constructor(private val appContext: Context) : ActionDispatchSink {

    companion object {
        private const val TAG = "UniversalTriggerEngine"
        private const val PREFS_NAME = "tajasb_universal_mapping_prefs"
        private const val KEY_RULES = "universal_rules_json"
        private const val KEY_PRESETS = "universal_presets_json"
        private const val KEY_ENGINE_ENABLED = "engine_enabled"
        private const val KEY_MATCH_ACTIVE = "mlbb_match_active"
        private const val KEY_BLOCK_TOUCH_DURING_CHALLENGE = "block_touch_during_challenge"
        private const val KEY_ACTIVE_SCENE = "active_scene"
        private const val KEY_FLOATING_CONFIG = "floating_window_config_json"
        private const val KEY_GLOBAL_DELAY_BEFORE_MS = "global_delay_before_ms"
        private const val KEY_GLOBAL_DELAY_AFTER_MS = "global_delay_after_ms"
        private const val KEY_GLOBAL_DELAY_EXCLUDE_COOLDOWN = "global_delay_exclude_cooldown"
        private const val KEY_GAME_COVER_AREAS_JSON = "game_cover_areas_json"
        private const val KEY_DEVICE2_SIMPLE_MODE = "device2_simple_mode"
        private const val MAX_HISTORY = 60
        private const val DUPLICATE_WINDOW_MS = 2500L

        @Volatile
        private var instance: UniversalTriggerEngine? = null

        fun getInstance(context: Context): UniversalTriggerEngine {
            return instance ?: synchronized(this) {
                instance ?: UniversalTriggerEngine(context.applicationContext).also { instance = it }
            }
        }

        fun peekInstance(): UniversalTriggerEngine? = instance
    }

    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dispatcher = UniversalActionDispatcher(appContext, this)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<UniversalEngineUiState> = _state.asStateFlow()

    val uiState: StateFlow<UniversalEngineUiState>
        get() = state

    private val queueLock = Any()
    private val pendingQueue = mutableListOf<QueuedDispatchTask>()
    private val queueSeq = AtomicLong(0L)
    private val isWorkerRunning = AtomicBoolean(false)
    private val isSingleTestRunningGuard = AtomicBoolean(false)
    private val activeChallengeCount = AtomicInteger(0)

    private val recentEventIds = object : LinkedHashMap<String, Long>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 180
    }
    private val recentRelayIds = object : LinkedHashMap<String, Long>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 200
    }

    private var lastObservedRelayEventId: String? = null

    private data class RemoteSyncDigest(
        val isEngineEnabled: Boolean,
        val isMlbbMatchActive: Boolean,
        val blockTouchDuringChallenge: Boolean,
        val isEmergencyStopped: Boolean,
        val isDevice2SimpleMode: Boolean,
        val isFloatingOverlayRunning: Boolean,
        val isFloatingHiddenForLive: Boolean,
        val isPositionEditMode: Boolean,
        val isCoverEditMode: Boolean,
        val activePinRuleId: String?,
        val activePinStepId: String?,
        val activeCoverAreaId: String?,
        val globalDelayBeforeMs: Long,
        val globalDelayAfterMs: Long,
        val globalDelayExcludeCooldownRules: Boolean,
        val rules: List<UniversalTriggerRule>,
        val gameCoverAreas: List<GameCoverArea>,
        val counters: Map<String, TriggerCounterState>,
        val historyIds: List<Pair<String, DispatchStatus>>
    )

    init {
        observeLiveEventRelay()
        startOverlayExpiryTicker()
        startRemoteSyncObserver()
    }

    private fun loadInitialState(): UniversalEngineUiState {
        // TIDAK ADA RULE DEFAULT HARDCODED untuk Like/Follow/Share/Gift.
        val savedRules = UniversalInteractionJsonCodec.decodeRules(prefs.getString(KEY_RULES, null))
        val savedPresets = UniversalInteractionJsonCodec.decodePresets(prefs.getString(KEY_PRESETS, null))
        val enabled = prefs.getBoolean(KEY_ENGINE_ENABLED, true)
        val matchActive = prefs.getBoolean(KEY_MATCH_ACTIVE, false)
        val blockTouch = prefs.getBoolean(KEY_BLOCK_TOUCH_DURING_CHALLENGE, true)
        val simpleMode = prefs.getBoolean(KEY_DEVICE2_SIMPLE_MODE, true)
        val scene = prefs.getString(KEY_ACTIVE_SCENE, "SCENE_UTAMA")?.ifBlank { "SCENE_UTAMA" } ?: "SCENE_UTAMA"
        val floatingCfg = UniversalInteractionJsonCodec.decodeFloatingConfig(prefs.getString(KEY_FLOATING_CONFIG, null))
        val globalBeforeMs = prefs.getLong(KEY_GLOBAL_DELAY_BEFORE_MS, 0L).coerceIn(0L, 60_000L)
        val globalAfterMs = prefs.getLong(KEY_GLOBAL_DELAY_AFTER_MS, 0L).coerceIn(0L, 60_000L)
        val globalExcludeCd = prefs.getBoolean(KEY_GLOBAL_DELAY_EXCLUDE_COOLDOWN, true)
        val savedCovers = UniversalInteractionJsonCodec.decodeGameCoverAreas(prefs.getString(KEY_GAME_COVER_AREAS_JSON, null))
        val initialRule = savedRules.firstOrNull { it.isPinVisible } ?: savedRules.firstOrNull()
        val initialStep = initialRule?.actionSequence?.firstOrNull {
            it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
        }
        return UniversalEngineUiState(
            isEngineEnabled = enabled,
            isMlbbMatchActive = matchActive,
            blockTouchDuringChallenge = blockTouch,
            isDevice2SimpleMode = simpleMode,
            isEmergencyStopped = false,
            globalDelayBeforeMs = globalBeforeMs,
            globalDelayAfterMs = globalAfterMs,
            globalDelayExcludeCooldownRules = globalExcludeCd,
            gameCoverAreas = savedCovers,
            activeCoverAreaId = savedCovers.firstOrNull()?.id,
            activePinRuleId = initialRule?.id,
            activePinStepId = initialStep?.id,
            floatingConfig = floatingCfg,
            activeSceneName = scene,
            rules = savedRules,
            presets = savedPresets
        )
    }

    private fun observeLiveEventRelay() {
        val receiver = LiveEventReceiver.getInstance(appContext)
        receiver.onBrokerConnected = {
            scope.launch {
                sendCurrentSyncToBroker()
            }
        }
        receiver.onMlbbControlReceived = { obj ->
            scope.launch {
                applyRemoteControlCommand(obj)
            }
        }
        scope.launch {
            try {
                receiver.lastEvent.collect { item ->
                    if (item == null) return@collect
                    if (item.id == lastObservedRelayEventId) return@collect
                    synchronized(recentRelayIds) {
                        if (recentRelayIds.containsKey(item.id)) return@collect
                        recentRelayIds[item.id] = System.currentTimeMillis()
                    }
                    lastObservedRelayEventId = item.id
                    onRelayEventReceived(item)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Relay observer stopped: ${t.message}")
            }
        }
    }

    private fun startRemoteSyncObserver() {
        scope.launch {
            _state
                .map { st ->
                    RemoteSyncDigest(
                        isEngineEnabled = st.isEngineEnabled,
                        isMlbbMatchActive = st.isMlbbMatchActive,
                        blockTouchDuringChallenge = st.blockTouchDuringChallenge,
                        isEmergencyStopped = st.isEmergencyStopped,
                        isDevice2SimpleMode = st.isDevice2SimpleMode,
                        isFloatingOverlayRunning = st.isFloatingOverlayRunning,
                        isFloatingHiddenForLive = st.isFloatingHiddenForLive,
                        isPositionEditMode = st.isPositionEditMode,
                        isCoverEditMode = st.isCoverEditMode,
                        activePinRuleId = st.activePinRuleId,
                        activePinStepId = st.activePinStepId,
                        activeCoverAreaId = st.activeCoverAreaId,
                        globalDelayBeforeMs = st.globalDelayBeforeMs,
                        globalDelayAfterMs = st.globalDelayAfterMs,
                        globalDelayExcludeCooldownRules = st.globalDelayExcludeCooldownRules,
                        rules = st.rules,
                        gameCoverAreas = st.gameCoverAreas,
                        counters = st.counters,
                        historyIds = st.history.take(15).map { it.id to it.status }
                    )
                }
                .distinctUntilChanged()
                .collectLatest {
                    delay(150L)
                    val receiver = LiveEventReceiver.getInstance(appContext)
                    if (receiver.connectionStatus.value == ConnectionStatus.CONNECTED) {
                        sendCurrentSyncToBroker()
                    }
                }
        }
    }

    fun sendCurrentSyncToBroker(): Boolean {
        val receiver = LiveEventReceiver.getInstance(appContext)
        if (receiver.connectionStatus.value != ConnectionStatus.CONNECTED) return false
        val cfg = receiver.currentServerConfig()
        val payload = MlbbRemoteSyncCodec.encodeStateSyncPayload(
            state = _state.value,
            serverHost = cfg.serverHost,
            serverPort = cfg.serverPort,
            activePlatform = cfg.activePlatform
        )
        return receiver.sendMlbbStateSync(payload)
    }

    private suspend fun applyRemoteControlCommand(obj: JSONObject) {
        val cmd = MlbbRemoteSyncCodec.parseControlCommand(obj) ?: return
        when (cmd) {
            is MlbbControlCommand.SetEngineEnabled -> setEngineEnabled(cmd.enabled)
            is MlbbControlCommand.SetMatchSafePause -> setMlbbMatchActive(cmd.paused)
            is MlbbControlCommand.SetTouchLock -> setBlockTouchDuringChallenge(cmd.enabled)
            is MlbbControlCommand.SetDevice2SimpleMode -> setDevice2SimpleMode(cmd.simple)
            is MlbbControlCommand.SetGlobalDelays -> setGlobalDelaySettings(
                beforeMs = cmd.beforeMs,
                afterMs = cmd.afterMs,
                excludeCooldownRules = cmd.excludeCooldownRules
            )
            is MlbbControlCommand.SetLiveDisplayMode -> {
                withContext(Dispatchers.Main) {
                    val runningNow = _state.value.isFloatingOverlayRunning
                    if (cmd.floatingRunning == false) {
                        FloatingTriggerOverlayService.stop(appContext)
                    } else if (
                        (cmd.floatingRunning == true || !runningNow) &&
                        FloatingTriggerOverlayService.canDrawOverlays(appContext)
                    ) {
                        FloatingTriggerOverlayService.start(
                            context = appContext,
                            startInEditMode = cmd.positionEditMode ?: _state.value.isPositionEditMode,
                            openSettingsPanel = false,
                            hideFloatingForLive = cmd.hiddenForLive ?: _state.value.isFloatingHiddenForLive
                        )
                    }
                    cmd.hiddenForLive?.let { setFloatingHiddenForLive(it) }
                    cmd.positionEditMode?.let { setPositionEditMode(it) }
                    cmd.coverEditMode?.let { setCoverEditMode(it) }
                }
            }
            is MlbbControlCommand.UpsertRuleDetail -> {
                val finalRule = if (cmd.preserveCoordinates) {
                    val existing = _state.value.rules.firstOrNull { it.id == cmd.rule.id }
                    if (existing != null) {
                        val existingStepsById = existing.actionSequence.associateBy { it.id }
                        val preservedSteps = cmd.rule.actionSequence.map { incomingStep ->
                            val curStep = existingStepsById[incomingStep.id]
                            if (curStep != null && curStep.posX != null && curStep.posY != null) {
                                incomingStep.copy(
                                    posX = curStep.posX,
                                    posY = curStep.posY,
                                    paramValue = if (incomingStep.actionType == ActionType.SIMULATED_CLICK) {
                                        String.format(Locale.US, "%.3f,%.3f", curStep.posX, curStep.posY)
                                    } else {
                                        incomingStep.paramValue
                                    }
                                )
                            } else {
                                incomingStep
                            }
                        }
                        cmd.rule.copy(
                            overlayPosX = existing.overlayPosX,
                            overlayPosY = existing.overlayPosY,
                            actionSequence = preservedSteps
                        )
                    } else {
                        cmd.rule
                    }
                } else {
                    cmd.rule
                }
                addOrUpdateRule(finalRule)
            }
            is MlbbControlCommand.CreateRule -> {
                val created = createEmptyConcept(
                    source = cmd.source,
                    name = cmd.name.ifBlank { null }
                )
                val starterStep = when (cmd.starterActionType) {
                    ActionType.SIMULATED_CLICK -> UniversalActionStep(
                        enabled = true,
                        actionType = ActionType.SIMULATED_CLICK,
                        paramValue = String.format(Locale.US, "%.3f,%.3f", created.overlayPosX, created.overlayPosY),
                        secondaryValue = "70",
                        durationMs = 70L,
                        posX = created.overlayPosX,
                        posY = created.overlayPosY
                    )
                    ActionType.SIMULATED_SWIPE -> UniversalActionStep(
                        enabled = true,
                        actionType = ActionType.SIMULATED_SWIPE,
                        paramValue = "RIGHT",
                        secondaryValue = "220",
                        durationMs = 220L,
                        posX = created.overlayPosX,
                        posY = created.overlayPosY
                    )
                    ActionType.DELAY -> UniversalActionStep(
                        enabled = true,
                        actionType = ActionType.DELAY,
                        paramValue = "500",
                        durationMs = 500L
                    )
                    ActionType.HOME_BACK_GAME -> UniversalActionStep(
                        enabled = true,
                        actionType = ActionType.HOME_BACK_GAME,
                        paramValue = "1500",
                        durationMs = 1500L
                    )
                    ActionType.MEME_OVERLAY -> UniversalActionStep(
                        enabled = true,
                        actionType = ActionType.MEME_OVERLAY,
                        secondaryValue = "MEME {user}!",
                        durationMs = 2500L,
                        posX = created.overlayPosX,
                        posY = created.overlayPosY,
                        executionMode = ExecutionMode.PARALLEL
                    )
                    ActionType.PROFILE_OVERLAY -> UniversalActionStep(
                        enabled = true,
                        actionType = ActionType.PROFILE_OVERLAY,
                        paramValue = "{user}",
                        secondaryValue = "{gift}",
                        durationMs = 2500L,
                        posX = created.overlayPosX,
                        posY = created.overlayPosY,
                        executionMode = ExecutionMode.PARALLEL
                    )
                    ActionType.PLAY_SOUND -> UniversalActionStep(
                        enabled = true,
                        actionType = ActionType.PLAY_SOUND,
                        paramValue = "CHIME",
                        executionMode = ExecutionMode.PARALLEL
                    )
                    else -> null
                }
                val updated = created.copy(
                    targetGiftName = cmd.targetGiftName,
                    actionSequence = listOfNotNull(starterStep)
                )
                addOrUpdateRule(updated)
            }
            is MlbbControlCommand.ToggleRule -> {
                val target = _state.value.rules.firstOrNull { it.id == cmd.ruleId }
                if (target != null && (cmd.enabled == null || target.enabled != cmd.enabled)) {
                    toggleRuleEnabled(cmd.ruleId)
                }
            }
            is MlbbControlCommand.ToggleRulePin -> {
                val target = _state.value.rules.firstOrNull { it.id == cmd.ruleId }
                if (target != null && (cmd.visible == null || target.isPinVisible != cmd.visible)) {
                    toggleRulePinVisibility(cmd.ruleId)
                }
            }
            is MlbbControlCommand.DuplicateRule -> {
                duplicateRule(cmd.ruleId)
            }
            is MlbbControlCommand.DeleteRule -> {
                deleteRule(cmd.ruleId)
            }
            is MlbbControlCommand.MoveRule -> {
                val rules = _state.value.rules
                val idx = rules.indexOfFirst { it.id == cmd.ruleId }
                if (idx >= 0 && cmd.delta != 0) {
                    val targetIdx = (idx + cmd.delta).coerceIn(0, rules.lastIndex)
                    moveRule(idx, targetIdx)
                }
            }
            is MlbbControlCommand.SelectActivePin -> {
                selectActivePin(cmd.ruleId, cmd.stepId)
            }
            is MlbbControlCommand.SetAllPinsVisible -> {
                setAllPinsVisible(cmd.visible)
            }
            is MlbbControlCommand.UpsertCoverArea -> {
                val existing = _state.value.gameCoverAreas.firstOrNull { it.id == cmd.area.id }
                if (existing != null) {
                    val nextArea = if (cmd.preservePosition) {
                        cmd.area.copy(posX = existing.posX, posY = existing.posY)
                    } else {
                        cmd.area
                    }
                    updateGameCoverArea(nextArea)
                } else {
                    addGameCoverArea(
                        name = cmd.area.name,
                        posX = cmd.area.posX,
                        posY = cmd.area.posY,
                        widthRatio = cmd.area.widthRatio,
                        heightRatio = cmd.area.heightRatio,
                        opacity = cmd.area.opacity,
                        colorHex = cmd.area.colorHex
                    )
                }
            }
            is MlbbControlCommand.CreateCoverArea -> {
                addGameCoverArea(
                    name = cmd.name,
                    posX = cmd.posX,
                    posY = cmd.posY,
                    widthRatio = cmd.widthRatio,
                    heightRatio = cmd.heightRatio,
                    opacity = cmd.opacity
                )
            }
            is MlbbControlCommand.ToggleCoverArea -> {
                val area = _state.value.gameCoverAreas.firstOrNull { it.id == cmd.areaId }
                if (area != null && (cmd.enabled == null || area.enabled != cmd.enabled)) {
                    toggleGameCoverAreaEnabled(cmd.areaId)
                }
            }
            is MlbbControlCommand.DeleteCoverArea -> deleteGameCoverArea(cmd.areaId)
            is MlbbControlCommand.SelectCoverArea -> selectActiveCoverArea(cmd.areaId)
            is MlbbControlCommand.TestRule -> {
                forceTestRuleNow(cmd.ruleId)
            }
            is MlbbControlCommand.InspectCooldown -> {
                val rule = _state.value.rules.firstOrNull { it.id == cmd.ruleId }
                inspectRuleCooldownOnce(cmd.ruleId) { result ->
                    appendHistory(
                        TriggerExecutionRecord(
                            timestampMs = System.currentTimeMillis(),
                            ruleId = cmd.ruleId,
                            ruleName = rule?.triggerName ?: "Scan CD",
                            source = rule?.triggerSource ?: UniversalTriggerSource.CUSTOM_EVENT,
                            senderName = "Device 1 Web",
                            quantity = 1,
                            status = DispatchStatus.EXECUTED,
                            detail = "SCAN OCR • $result"
                        )
                    )
                }
            }
            is MlbbControlCommand.InjectEvent -> {
                dispatchEvent(
                    LiveInteractionEvent(
                        source = cmd.source,
                        senderName = cmd.senderName,
                        quantity = cmd.quantity,
                        diamonds = cmd.diamonds,
                        giftName = cmd.giftName,
                        text = cmd.text
                    )
                )
            }
            is MlbbControlCommand.EmergencyStop -> emergencyStop()
            is MlbbControlCommand.ResumeEngine -> resumeAfterEmergencyStop()
            is MlbbControlCommand.ResetCounters -> resetAllCounters()
            is MlbbControlCommand.ClearHistory -> clearHistory()
            is MlbbControlCommand.RequestStateSync -> sendCurrentSyncToBroker()
        }
    }

    private fun startOverlayExpiryTicker() {
        scope.launch {
            while (true) {
                delay(250L)
                val now = System.currentTimeMillis()
                val current = _state.value
                val hasExpiredOverlay = current.activeOverlays.any { it.expiresAtMs <= now }
                val simExpired = current.lastSimEvent != null && (now - current.lastSimEvent.timestampMs) > 650L
                if (hasExpiredOverlay || simExpired) {
                    _state.update { st ->
                        st.copy(
                            activeOverlays = st.activeOverlays.filter { it.expiresAtMs > now },
                            lastSimEvent = if (st.lastSimEvent != null && (now - st.lastSimEvent.timestampMs) > 650L) null else st.lastSimEvent
                        )
                    }
                }
            }
        }
    }

    private fun onRelayEventReceived(item: LiveEventItem) {
        val currentRules = _state.value.rules
        if (currentRules.none { it.enabled }) return

        val sender = item.senderName.ifBlank { item.nickname.ifBlank { "Penonton" } }
        when (item.type) {
            LiveEventType.LIKE -> {
                dispatchEvent(
                    LiveInteractionEvent(
                        id = "${item.id}_LIKE",
                        source = UniversalTriggerSource.LIKE,
                        senderName = sender,
                        quantity = item.likeCount.coerceAtLeast(1),
                        avatarUrl = item.avatarUrl,
                        timestampMs = item.timestamp
                    )
                )
            }
            LiveEventType.FOLLOW -> {
                dispatchEvent(
                    LiveInteractionEvent(
                        id = "${item.id}_FOLLOW",
                        source = UniversalTriggerSource.FOLLOW,
                        senderName = sender,
                        quantity = 1,
                        avatarUrl = item.avatarUrl,
                        timestampMs = item.timestamp
                    )
                )
            }
            LiveEventType.SHARE -> {
                dispatchEvent(
                    LiveInteractionEvent(
                        id = "${item.id}_SHARE",
                        source = UniversalTriggerSource.SHARE,
                        senderName = sender,
                        quantity = 1,
                        avatarUrl = item.avatarUrl,
                        timestampMs = item.timestamp
                    )
                )
            }
            LiveEventType.GIFT -> {
                val giftQty = item.giftCount.coerceAtLeast(1)
                val coins = item.diamondCount.coerceAtLeast(0)
                if (currentRules.any { it.enabled && it.triggerSource == UniversalTriggerSource.GIFT }) {
                    dispatchEvent(
                        LiveInteractionEvent(
                            id = "${item.id}_GIFT",
                            source = UniversalTriggerSource.GIFT,
                            senderName = sender,
                            quantity = giftQty,
                            diamonds = coins,
                            giftName = item.giftName,
                            avatarUrl = item.avatarUrl,
                            timestampMs = item.timestamp
                        )
                    )
                }
                if (currentRules.any { it.enabled && it.triggerSource == UniversalTriggerSource.GIFT_QUANTITY }) {
                    dispatchEvent(
                        LiveInteractionEvent(
                            id = "${item.id}_GIFT_QTY",
                            source = UniversalTriggerSource.GIFT_QUANTITY,
                            senderName = sender,
                            quantity = if (coins > 0) coins else giftQty,
                            diamonds = coins,
                            giftName = item.giftName,
                            avatarUrl = item.avatarUrl,
                            timestampMs = item.timestamp
                        )
                    )
                }
            }
            LiveEventType.CHAT -> {
                dispatchEvent(
                    LiveInteractionEvent(
                        id = "${item.id}_CHAT",
                        source = UniversalTriggerSource.CHAT,
                        senderName = sender,
                        quantity = 1,
                        text = item.text,
                        avatarUrl = item.avatarUrl,
                        timestampMs = item.timestamp
                    )
                )
            }
            LiveEventType.MEMBER -> {
                dispatchEvent(
                    LiveInteractionEvent(
                        id = "${item.id}_MEMBER",
                        source = UniversalTriggerSource.MEMBER_JOIN,
                        senderName = sender,
                        quantity = 1,
                        avatarUrl = item.avatarUrl,
                        timestampMs = item.timestamp
                    )
                )
            }
            LiveEventType.SHOP_ORDER -> {
                dispatchEvent(
                    LiveInteractionEvent(
                        id = "${item.id}_ORDER",
                        source = UniversalTriggerSource.SHOP_ORDER,
                        senderName = sender,
                        quantity = item.productQuantity.coerceAtLeast(1),
                        text = item.productName,
                        avatarUrl = item.avatarUrl,
                        timestampMs = item.timestamp
                    )
                )
            }
            LiveEventType.PIN_PRODUCT,
            LiveEventType.ROOM_STATS,
            LiveEventType.BROKER_STATUS -> Unit
        }
    }

    /**
     * Titik masuk tunggal (Unified Trigger Entry) untuk Live Relay maupun Test Center.
     * Menjamin simulasi menggunakan konfigurasi tersimpan yang identik dengan live stream.
     */
    fun dispatchEvent(event: LiveInteractionEvent): List<TriggerExecutionRecord> {
        val snapshot = _state.value
        if (!snapshot.isEngineEnabled || snapshot.isEmergencyStopped) {
            return emptyList()
        }

        val now = System.currentTimeMillis()
        // Duplicate protection
        synchronized(recentEventIds) {
            val prevTs = recentEventIds[event.id]
            if (prevTs != null && (now - prevTs) < DUPLICATE_WINDOW_MS) {
                val dupRec = TriggerExecutionRecord(
                    timestampMs = now,
                    ruleId = "DUP",
                    ruleName = "Duplicate Protection",
                    source = event.source,
                    senderName = event.senderName,
                    quantity = event.quantity,
                    status = DispatchStatus.SKIPPED_DUPLICATE,
                    detail = "Event ID duplikat diabaikan (${event.id.take(8)})"
                )
                appendHistory(dupRec)
                return listOf(dupRec)
            }
            recentEventIds[event.id] = now
        }

        val matchingRules = snapshot.rules.filter { it.enabled && it.triggerSource == event.source }
        if (matchingRules.isEmpty()) {
            val emptyRec = TriggerExecutionRecord(
                timestampMs = now,
                ruleId = "-",
                ruleName = "Belum ada trigger untuk ${event.source.displayName}",
                source = event.source,
                senderName = event.senderName,
                quantity = event.quantity,
                status = DispatchStatus.SKIPPED_CONDITION,
                detail = "Buat & aktifkan rule untuk sumber ${event.source.displayName} di Builder."
            )
            appendHistory(emptyRec)
            return listOf(emptyRec)
        }

        val sortedRules = matchingRules.sortedByDescending {
            if (it.enablePriority) it.priority else 5
        }

        val immediateRecords = mutableListOf<TriggerExecutionRecord>()
        val updatedCounters = snapshot.counters.toMutableMap()
        val tasksToEnqueue = mutableListOf<QueuedDispatchTask>()

        for (rule in sortedRules) {
            val eval = UniversalRuleEvaluator.evaluateCounterAndEligibility(
                rule = rule,
                currentCounter = updatedCounters[rule.id],
                event = event,
                isMlbbMatchActive = snapshot.isMlbbMatchActive,
                nowMs = now
            )
            updatedCounters[rule.id] = eval.updatedCounter

            if (eval.shouldExecute) {
                val prio = if (rule.enablePriority) rule.priority else 5
                tasksToEnqueue += QueuedDispatchTask(
                    seq = queueSeq.incrementAndGet(),
                    priority = prio,
                    rule = rule,
                    event = event,
                    counterSnapshot = eval.updatedCounter,
                    matchActiveSnapshot = snapshot.isMlbbMatchActive,
                    detailNote = eval.detail
                )
                immediateRecords += TriggerExecutionRecord(
                    timestampMs = now,
                    ruleId = rule.id,
                    ruleName = rule.triggerName,
                    source = event.source,
                    senderName = event.senderName,
                    quantity = event.quantity,
                    status = DispatchStatus.QUEUED,
                    detail = "${eval.detail} • Antrean Prio $prio"
                )
            } else {
                val skipRec = TriggerExecutionRecord(
                    timestampMs = now,
                    ruleId = rule.id,
                    ruleName = rule.triggerName,
                    source = event.source,
                    senderName = event.senderName,
                    quantity = event.quantity,
                    status = eval.status,
                    detail = eval.detail
                )
                immediateRecords += skipRec
            }
        }

        if (tasksToEnqueue.isNotEmpty()) {
            synchronized(queueLock) {
                pendingQueue.addAll(tasksToEnqueue)
                pendingQueue.sortWith(compareByDescending<QueuedDispatchTask> { it.priority }.thenBy { it.seq })
            }
        }

        val qSize = synchronized(queueLock) { pendingQueue.size }
        _state.update { st ->
            st.copy(
                counters = updatedCounters,
                queueSize = qSize,
                history = (immediateRecords.reversed() + st.history).take(MAX_HISTORY)
            )
        }

        if (tasksToEnqueue.isNotEmpty()) {
            ensureQueueWorkerRunning()
        }

        return immediateRecords
    }

    /**
     * Menjalankan 1 rule tertentu secara langsung untuk pengujian (melewati filter cooldown/threshold/minQty)
     * agar tombol ▶ Tes di pin maupun dropdown selalu mengeksekusi Tap ML + Overlay + Sound dengan jelas.
     */
    fun testSpecificRule(
        ruleId: String,
        senderName: String = "Tester",
        quantity: Int = 1,
        diamonds: Int = 10,
        giftName: String = "Rose",
        customText: String = "Test event",
        avatarUrl: String = ""
    ): List<TriggerExecutionRecord> {
        val rule = _state.value.rules.firstOrNull { it.id == ruleId } ?: return emptyList()
        val effectiveQty = if (rule.enableMinimumQuantity) maxOf(quantity, rule.minimumQuantity) else quantity.coerceAtLeast(1)
        val effectiveGiftName = if (rule.targetGiftName.isNotBlank() && giftName == "Rose") {
            GiftCoinCatalog.firstGiftNameForTest(rule.targetGiftName)
        } else {
            giftName
        }
        val effectiveDiamonds = if (rule.targetGiftName.isNotBlank() && diamonds == 10) {
            GiftCoinCatalog.resolveCoinCost(effectiveGiftName) ?: diamonds
        } else {
            diamonds
        }
        val event = LiveInteractionEvent(
            source = rule.triggerSource,
            senderName = senderName.ifBlank { "Tester" },
            quantity = effectiveQty,
            diamonds = effectiveDiamonds.coerceAtLeast(0),
            giftName = effectiveGiftName,
            text = customText,
            avatarUrl = avatarUrl
        )
        forceTestRuleNow(ruleId, event)
        return emptyList()
    }

    fun forceTestRuleNow(
        ruleId: String,
        customEvent: LiveInteractionEvent? = null,
        onResult: ((String) -> Unit)? = null
    ) {
        val rule = _state.value.rules.firstOrNull { it.id == ruleId } ?: run {
            onResult?.invoke("Target tidak ditemukan")
            return
        }
        if (!isSingleTestRunningGuard.compareAndSet(false, true)) {
            onResult?.invoke("Tes lain masih berjalan...")
            return
        }
        val defaultGift = GiftCoinCatalog.firstGiftNameForTest(rule.targetGiftName)
        val defaultDiamonds = GiftCoinCatalog.resolveCoinCost(defaultGift) ?: 10
        val event = customEvent ?: LiveInteractionEvent(
            source = rule.triggerSource,
            senderName = "Tester",
            quantity = if (rule.enableMinimumQuantity) rule.minimumQuantity else 1,
            diamonds = defaultDiamonds,
            giftName = defaultGift,
            text = "Tes ${rule.triggerName}"
        )
        _state.update { it.copy(isSingleTestRunning = true, isEmergencyStopped = false) }
        scope.launch {
            val summary = try {
                // Beri waktu 95ms agar panel submenu/keyboard floating menutup dulu & WindowManager stabil (zero blink)
                delay(95L)
                val cooldownNote = waitForSkillCooldownIfNeeded(rule)
                if (_state.value.isEmergencyStopped) {
                    "Dihentikan darurat saat tunggu cooldown"
                } else {
                    val snapshot = _state.value
                    val (gBefore, gAfter) = UniversalRuleEvaluator.resolveGlobalDelayPairMs(
                        rule = rule,
                        globalBeforeMs = snapshot.globalDelayBeforeMs,
                        globalAfterMs = snapshot.globalDelayAfterMs,
                        excludeCooldownRules = snapshot.globalDelayExcludeCooldownRules
                    )
                    val execSummary = dispatcher.dispatchRuleActions(
                        rule = rule.copy(enabled = true),
                        event = event,
                        counterCount = (snapshot.counters[rule.id]?.accumulatedCount ?: 0) + 1,
                        isMlbbMatchActive = false,
                        globalDelayBeforeMs = gBefore,
                        globalDelayAfterMs = gAfter,
                        enableTouchLock = false
                    )
                    if (!cooldownNote.isNullOrBlank()) "$cooldownNote • $execSummary" else execSummary
                }
            } catch (t: Throwable) {
                "Error: ${t.message}"
            } finally {
                isSingleTestRunningGuard.set(false)
                _state.update { it.copy(cooldownStatusHint = "", isSingleTestRunning = false) }
            }
            val a11yOk = MlbbGestureAccessibilityService.peekInstance() != null
            val needsA11y = rule.actionSequence.any {
                it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
            } && !a11yOk
            val status = if (needsA11y) DispatchStatus.SKIPPED_CONDITION else DispatchStatus.EXECUTED
            appendHistory(
                TriggerExecutionRecord(
                    timestampMs = System.currentTimeMillis(),
                    ruleId = rule.id,
                    ruleName = rule.triggerName,
                    source = event.source,
                    senderName = event.senderName,
                    quantity = event.quantity,
                    status = status,
                    detail = "TES • $summary"
                )
            )
            onResult?.invoke(summary)
        }
    }

    fun previewSoundSpec(spec: String) {
        dispatcher.playConfiguredSound(spec.ifBlank { "CHIME" })
    }

    fun previewRuleActions(rule: UniversalTriggerRule) {
        val sampleEvent = LiveInteractionEvent(
            source = rule.triggerSource,
            senderName = "PreviewPenonton",
            quantity = if (rule.enableMinimumQuantity) rule.minimumQuantity else 1,
            diamonds = 99,
            giftName = "GiftPreview",
            text = "Preview ${rule.triggerName}"
        )
        scope.launch {
            dispatcher.dispatchRuleActions(
                rule = rule.copy(enabled = true),
                event = sampleEvent,
                counterCount = 1,
                isMlbbMatchActive = false
            )
        }
    }

    private fun enterChallengeExecution() {
        val count = activeChallengeCount.incrementAndGet()
        if (count > 0) {
            _state.update { it.copy(isChallengeExecuting = true) }
        }
    }

    private fun exitChallengeExecution() {
        val count = activeChallengeCount.decrementAndGet().coerceAtLeast(0)
        if (count <= 0) {
            activeChallengeCount.set(0)
            _state.update { it.copy(isChallengeExecuting = false) }
        }
    }

    private fun resolveCooldownTargetStep(rule: UniversalTriggerRule): UniversalActionStep? {
        val touchSteps = rule.actionSequence.filter {
            it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
        }
        if (touchSteps.isEmpty()) return null
        val explicitId = rule.cooldownTargetStepId
        if (!explicitId.isNullOrBlank()) {
            touchSteps.firstOrNull { it.id == explicitId }?.let { return it }
        }
        return touchSteps.firstOrNull()
    }

    private suspend fun scanRuleCooldownRaw(rule: UniversalTriggerRule): SkillCooldownScanResult {
        val targetStep = resolveCooldownTargetStep(rule)
        val cx = (targetStep?.posX ?: rule.overlayPosX).coerceIn(0.02f, 0.98f)
        val cy = (targetStep?.posY ?: rule.overlayPosY).coerceIn(0.02f, 0.98f)
        val adjX = (cx + rule.cooldownAreaOffsetX).coerceIn(0.02f, 0.98f)
        val adjY = (cy + rule.cooldownAreaOffsetY).coerceIn(0.02f, 0.98f)
        val regionSpec = ScanRegionSpec(
            centerNormX = adjX,
            centerNormY = adjY,
            widthRatio = rule.cooldownAreaWidthRatio,
            heightRatio = rule.cooldownAreaHeightRatio
        )
        val prepareRegion = MlbbGestureAccessibilityService.onPrepareScreenForScanRegion
        val prepareFallback = MlbbGestureAccessibilityService.onPrepareScreenForScan
        var hiddenApplied = false
        return try {
            if (prepareRegion != null) {
                hiddenApplied = withContext(Dispatchers.Main) { prepareRegion.invoke(regionSpec, true) }
                if (hiddenApplied) delay(85L)
            } else if (prepareFallback != null) {
                hiddenApplied = true
                withContext(Dispatchers.Main) { prepareFallback.invoke(true) }
                delay(95L)
            }
            SkillCooldownDetector.inspectSkillArea(
                centerNormX = cx,
                centerNormY = cy,
                widthRatio = rule.cooldownAreaWidthRatio,
                heightRatio = rule.cooldownAreaHeightRatio,
                offsetX = rule.cooldownAreaOffsetX,
                offsetY = rule.cooldownAreaOffsetY
            )
        } finally {
            if (hiddenApplied) {
                if (prepareRegion != null) {
                    withContext(Dispatchers.Main) { prepareRegion.invoke(regionSpec, false) }
                } else if (prepareFallback != null) {
                    withContext(Dispatchers.Main) { prepareFallback.invoke(false) }
                }
            }
        }
    }

    private suspend fun waitForSkillCooldownIfNeeded(rule: UniversalTriggerRule): String? {
        if (!rule.enableVisualCooldownWait) return null
        val startedMs = System.currentTimeMillis()
        val maxWait = rule.cooldownMaxWaitMs.coerceIn(2000L, 120_000L)
        var maxCooldownSeenSec: Double? = null
        var lastCooldownSeenSec: Double? = null
        var reconfirmedReady = false

        while (!_state.value.isEmergencyStopped && (System.currentTimeMillis() - startedMs) < maxWait) {
            when (val scanResult = scanRuleCooldownRaw(rule)) {
                is SkillCooldownScanResult.RateLimited -> {
                    delay(scanResult.retryAfterMs.coerceIn(300L, 1250L))
                }
                is SkillCooldownScanResult.Ready -> {
                    val lastSec = lastCooldownSeenSec
                    val remainingWindow = maxWait - (System.currentTimeMillis() - startedMs)
                    if (lastSec != null && lastSec > 1.8 && !reconfirmedReady && remainingWindow > 1200L) {
                        reconfirmedReady = true
                        delay(1080L)
                        continue
                    }
                    _state.update { it.copy(cooldownStatusHint = "") }
                    return maxCooldownSeenSec?.let {
                        "Tunggu CD (${String.format(Locale.US, "%.1f", it)}d) → Siap"
                    }
                }
                is SkillCooldownScanResult.OnCooldown -> {
                    reconfirmedReady = false
                    lastCooldownSeenSec = scanResult.secondsRemaining
                    maxCooldownSeenSec = maxOf(maxCooldownSeenSec ?: 0.0, scanResult.secondsRemaining)
                    val secText = String.format(Locale.US, "%.1f", scanResult.secondsRemaining)
                    _state.update {
                        it.copy(cooldownStatusHint = "⏱️ ${rule.triggerName}: cooldown ${secText}d")
                    }
                    // Interval adaptif agar tidak boros scan/berkedip saat sisa cooldown masih panjang,
                    // namun tetap presisi mendekati 0 detik (limit OS Android >= 1050ms per screenshot)
                    val waitStep = when {
                        scanResult.secondsRemaining > 8.0 ->
                            (scanResult.secondsRemaining * 420.0).toLong().coerceIn(2200L, 4200L)
                        scanResult.secondsRemaining > 3.0 ->
                            ((scanResult.secondsRemaining - 1.2) * 550.0).toLong().coerceIn(1350L, 2400L)
                        else -> 1080L
                    }
                    delay(waitStep)
                }
                is SkillCooldownScanResult.Unavailable -> {
                    _state.update { it.copy(cooldownStatusHint = "") }
                    return scanResult.reason
                }
            }
        }
        _state.update { it.copy(cooldownStatusHint = "") }
        return if (_state.value.isEmergencyStopped) null else "Timeout tunggu cooldown (${maxWait / 1000}d)"
    }

    fun inspectRuleCooldownOnce(ruleId: String, onResult: (String) -> Unit) {
        val rule = _state.value.rules.firstOrNull { it.id == ruleId } ?: run {
            onResult("Konsep/Pin tidak ditemukan")
            return
        }
        scope.launch {
            var res = scanRuleCooldownRaw(rule)
            if (res is SkillCooldownScanResult.RateLimited) {
                delay(res.retryAfterMs.coerceIn(300L, 1250L))
                res = scanRuleCooldownRaw(rule)
            }
            val msg = when (res) {
                is SkillCooldownScanResult.Ready -> "✅ Skill SIAP / Aktif (angka cooldown tidak terdeteksi)"
                is SkillCooldownScanResult.OnCooldown ->
                    "⏳ Cooldown terbaca: ${String.format(Locale.US, "%.1f", res.secondsRemaining)} detik (teks: \"${res.rawText.replace('\n', ' ').take(18)}\")"
                is SkillCooldownScanResult.RateLimited -> "⏳ Terlalu cepat, coba tap Scan Sekali lagi"
                is SkillCooldownScanResult.Unavailable -> "⚠️ ${res.reason}"
            }
            withContext(Dispatchers.Main) { onResult(msg) }
        }
    }

    private fun ensureQueueWorkerRunning() {
        if (!isWorkerRunning.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    val nextTask = synchronized(queueLock) {
                        if (pendingQueue.isEmpty() || _state.value.isEmergencyStopped) null
                        else pendingQueue.removeAt(0)
                    }
                    if (nextTask == null) break

                    _state.update { st ->
                        st.copy(queueSize = synchronized(queueLock) { pendingQueue.size })
                    }

                    val summary = try {
                        val cooldownNote = waitForSkillCooldownIfNeeded(nextTask.rule)
                        if (_state.value.isEmergencyStopped) {
                            "Dihentikan darurat saat tunggu cooldown"
                        } else {
                            val snapshot = _state.value
                            val (gBefore, gAfter) = UniversalRuleEvaluator.resolveGlobalDelayPairMs(
                                rule = nextTask.rule,
                                globalBeforeMs = snapshot.globalDelayBeforeMs,
                                globalAfterMs = snapshot.globalDelayAfterMs,
                                excludeCooldownRules = snapshot.globalDelayExcludeCooldownRules
                            )
                            val execSummary = dispatcher.dispatchRuleActions(
                                rule = nextTask.rule,
                                event = nextTask.event,
                                counterCount = nextTask.counterSnapshot.accumulatedCount,
                                isMlbbMatchActive = snapshot.isMlbbMatchActive,
                                globalDelayBeforeMs = gBefore,
                                globalDelayAfterMs = gAfter,
                                enableTouchLock = snapshot.blockTouchDuringChallenge
                            )
                            if (!cooldownNote.isNullOrBlank()) "$cooldownNote • $execSummary" else execSummary
                        }
                    } catch (t: Throwable) {
                        "Error eksekusi: ${t.message}"
                    } finally {
                        _state.update { it.copy(cooldownStatusHint = "") }
                    }

                    val execStatus = if (_state.value.isEmergencyStopped) {
                        DispatchStatus.STOPPED_EMERGENCY
                    } else {
                        DispatchStatus.EXECUTED
                    }
                    appendHistory(
                        TriggerExecutionRecord(
                            timestampMs = System.currentTimeMillis(),
                            ruleId = nextTask.rule.id,
                            ruleName = nextTask.rule.triggerName,
                            source = nextTask.event.source,
                            senderName = nextTask.event.senderName,
                            quantity = nextTask.event.quantity,
                            status = execStatus,
                            detail = "${nextTask.detailNote} • $summary"
                        )
                    )
                }
            } finally {
                isWorkerRunning.set(false)
                val hasMore = synchronized(queueLock) {
                    pendingQueue.isNotEmpty() && !_state.value.isEmergencyStopped
                }
                if (hasMore) {
                    ensureQueueWorkerRunning()
                }
            }
        }
    }

    // ==================== Engine Controls & Safety ====================

    fun setEngineEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENGINE_ENABLED, enabled).apply()
        _state.update { it.copy(isEngineEnabled = enabled, isEmergencyStopped = if (enabled) false else it.isEmergencyStopped) }
    }

    fun setMlbbMatchActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_MATCH_ACTIVE, active).apply()
        _state.update { it.copy(isMlbbMatchActive = active) }
    }

    fun setBlockTouchDuringChallenge(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_TOUCH_DURING_CHALLENGE, enabled).apply()
        _state.update { it.copy(blockTouchDuringChallenge = enabled) }
    }

    fun setDevice2SimpleMode(simple: Boolean) {
        prefs.edit().putBoolean(KEY_DEVICE2_SIMPLE_MODE, simple).apply()
        _state.update { it.copy(isDevice2SimpleMode = simple) }
    }

    fun setGlobalDelaySettings(
        beforeMs: Long? = null,
        afterMs: Long? = null,
        excludeCooldownRules: Boolean? = null
    ) {
        val cur = _state.value
        val nextBefore = (beforeMs ?: cur.globalDelayBeforeMs).coerceIn(0L, 60_000L)
        val nextAfter = (afterMs ?: cur.globalDelayAfterMs).coerceIn(0L, 60_000L)
        val nextExclude = excludeCooldownRules ?: cur.globalDelayExcludeCooldownRules
        prefs.edit()
            .putLong(KEY_GLOBAL_DELAY_BEFORE_MS, nextBefore)
            .putLong(KEY_GLOBAL_DELAY_AFTER_MS, nextAfter)
            .putBoolean(KEY_GLOBAL_DELAY_EXCLUDE_COOLDOWN, nextExclude)
            .apply()
        _state.update {
            it.copy(
                globalDelayBeforeMs = nextBefore,
                globalDelayAfterMs = nextAfter,
                globalDelayExcludeCooldownRules = nextExclude
            )
        }
    }

    fun setCoverEditMode(editing: Boolean) {
        _state.update {
            it.copy(
                isCoverEditMode = editing,
                isFloatingHiddenForLive = if (editing) false else it.isFloatingHiddenForLive
            )
        }
    }

    fun addGameCoverArea(
        name: String,
        posX: Float = 0.14f,
        posY: Float = 0.24f,
        widthRatio: Float = 0.18f,
        heightRatio: Float = 0.24f,
        opacity: Float = 0.98f,
        colorHex: Long = 0xFF0B1120L
    ): GameCoverArea {
        val created = GameCoverArea(
            name = name.trim().ifBlank { "Penutup #${_state.value.gameCoverAreas.size + 1}" },
            enabled = true,
            posX = posX.coerceIn(0.01f, 0.99f),
            posY = posY.coerceIn(0.01f, 0.99f),
            widthRatio = widthRatio.coerceIn(0.03f, 0.96f),
            heightRatio = heightRatio.coerceIn(0.03f, 0.96f),
            opacity = opacity.coerceIn(0.20f, 1.0f),
            colorHex = colorHex
        )
        val updated = _state.value.gameCoverAreas + created
        persistAndUpdateCoverAreas(updated, activeId = created.id)
        return created
    }

    fun updateGameCoverArea(area: GameCoverArea) {
        val clean = area.copy(
            name = area.name.trim().ifBlank { "Penutup" },
            posX = area.posX.coerceIn(0.01f, 0.99f),
            posY = area.posY.coerceIn(0.01f, 0.99f),
            widthRatio = area.widthRatio.coerceIn(0.03f, 0.96f),
            heightRatio = area.heightRatio.coerceIn(0.03f, 0.96f),
            opacity = area.opacity.coerceIn(0.20f, 1.0f)
        )
        val updated = _state.value.gameCoverAreas.map { if (it.id == clean.id) clean else it }
        persistAndUpdateCoverAreas(updated, activeId = clean.id)
    }

    fun toggleGameCoverAreaEnabled(id: String) {
        val updated = _state.value.gameCoverAreas.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
        persistAndUpdateCoverAreas(updated)
    }

    fun selectActiveCoverArea(id: String?) {
        _state.update { it.copy(activeCoverAreaId = id) }
    }

    fun deleteGameCoverArea(id: String) {
        val updated = _state.value.gameCoverAreas.filterNot { it.id == id }
        val nextActive = if (_state.value.activeCoverAreaId == id) updated.firstOrNull()?.id else _state.value.activeCoverAreaId
        persistAndUpdateCoverAreas(updated, activeId = nextActive)
    }

    private fun persistAndUpdateCoverAreas(
        areas: List<GameCoverArea>,
        activeId: String? = _state.value.activeCoverAreaId
    ) {
        prefs.edit().putString(KEY_GAME_COVER_AREAS_JSON, UniversalInteractionJsonCodec.encodeGameCoverAreas(areas)).apply()
        _state.update { st ->
            val resolvedActive = activeId?.takeIf { id -> areas.any { it.id == id } } ?: areas.firstOrNull()?.id
            st.copy(gameCoverAreas = areas, activeCoverAreaId = resolvedActive)
        }
    }

    fun updateRuleVisualCooldown(
        ruleId: String,
        enabled: Boolean? = null,
        widthRatio: Float? = null,
        heightRatio: Float? = null,
        offsetX: Float? = null,
        offsetY: Float? = null,
        maxWaitMs: Long? = null,
        targetStepId: String? = null,
        updateTargetStep: Boolean = false
    ) {
        val updated = _state.value.rules.map { rule ->
            if (rule.id != ruleId) rule
            else rule.copy(
                enableVisualCooldownWait = enabled ?: rule.enableVisualCooldownWait,
                cooldownTargetStepId = if (updateTargetStep) targetStepId else rule.cooldownTargetStepId,
                cooldownAreaWidthRatio = (widthRatio ?: rule.cooldownAreaWidthRatio).coerceIn(0.025f, 0.45f),
                cooldownAreaHeightRatio = (heightRatio ?: rule.cooldownAreaHeightRatio).coerceIn(0.025f, 0.55f),
                cooldownAreaOffsetX = (offsetX ?: rule.cooldownAreaOffsetX).coerceIn(-0.25f, 0.25f),
                cooldownAreaOffsetY = (offsetY ?: rule.cooldownAreaOffsetY).coerceIn(-0.25f, 0.25f),
                cooldownMaxWaitMs = (maxWaitMs ?: rule.cooldownMaxWaitMs).coerceIn(2000L, 120_000L)
            )
        }
        persistAndUpdateRules(updated)
    }

    fun restoreFullUniversalState(payload: FullAppBackupPayload) {
        synchronized(queueLock) {
            pendingQueue.clear()
        }
        activeChallengeCount.set(0)
        isSingleTestRunningGuard.set(false)
        val clampedFloating = payload.floatingConfig.copy(
            hudXRatio = payload.floatingConfig.hudXRatio.coerceIn(0f, 1f),
            hudYRatio = payload.floatingConfig.hudYRatio.coerceIn(0f, 1f),
            hudScale = payload.floatingConfig.hudScale.coerceIn(0.65f, 1.35f),
            hudOpacity = payload.floatingConfig.hudOpacity.coerceIn(0.30f, 1.0f),
            panelXRatio = payload.floatingConfig.panelXRatio.coerceIn(0f, 1f),
            panelYRatio = payload.floatingConfig.panelYRatio.coerceIn(0f, 1f),
            panelWidthRatio = payload.floatingConfig.panelWidthRatio.coerceIn(0.38f, 0.98f),
            panelHeightRatio = payload.floatingConfig.panelHeightRatio.coerceIn(0.35f, 0.96f),
            panelOpacity = payload.floatingConfig.panelOpacity.coerceIn(0.45f, 1.0f)
        )
        val safeGlobalBefore = payload.globalDelayBeforeMs.coerceIn(0L, 60_000L)
        val safeGlobalAfter = payload.globalDelayAfterMs.coerceIn(0L, 60_000L)
        val safeCovers = payload.gameCoverAreas
        prefs.edit()
            .putString(KEY_RULES, UniversalInteractionJsonCodec.encodeRules(payload.rules))
            .putString(KEY_PRESETS, UniversalInteractionJsonCodec.encodePresets(payload.presets))
            .putString(KEY_FLOATING_CONFIG, UniversalInteractionJsonCodec.encodeFloatingConfig(clampedFloating))
            .putBoolean(KEY_ENGINE_ENABLED, payload.isEngineEnabled)
            .putBoolean(KEY_MATCH_ACTIVE, payload.isMlbbMatchActive)
            .putBoolean(KEY_BLOCK_TOUCH_DURING_CHALLENGE, payload.blockTouchDuringChallenge)
            .putBoolean(KEY_DEVICE2_SIMPLE_MODE, payload.isDevice2SimpleMode)
            .putString(KEY_ACTIVE_SCENE, payload.activeSceneName.ifBlank { "SCENE_UTAMA" })
            .putLong(KEY_GLOBAL_DELAY_BEFORE_MS, safeGlobalBefore)
            .putLong(KEY_GLOBAL_DELAY_AFTER_MS, safeGlobalAfter)
            .putBoolean(KEY_GLOBAL_DELAY_EXCLUDE_COOLDOWN, payload.globalDelayExcludeCooldownRules)
            .putString(KEY_GAME_COVER_AREAS_JSON, UniversalInteractionJsonCodec.encodeGameCoverAreas(safeCovers))
            .apply()

        val initialRule = payload.rules.firstOrNull { it.isPinVisible } ?: payload.rules.firstOrNull()
        val initialStep = initialRule?.actionSequence?.firstOrNull {
            it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
        }
        _state.update { st ->
            st.copy(
                isEngineEnabled = payload.isEngineEnabled,
                isMlbbMatchActive = payload.isMlbbMatchActive,
                blockTouchDuringChallenge = payload.blockTouchDuringChallenge,
                isDevice2SimpleMode = payload.isDevice2SimpleMode,
                isChallengeExecuting = false,
                isSingleTestRunning = false,
                cooldownStatusHint = "",
                isEmergencyStopped = false,
                globalDelayBeforeMs = safeGlobalBefore,
                globalDelayAfterMs = safeGlobalAfter,
                globalDelayExcludeCooldownRules = payload.globalDelayExcludeCooldownRules,
                gameCoverAreas = safeCovers,
                activeCoverAreaId = safeCovers.firstOrNull()?.id,
                isCoverEditMode = false,
                activeSceneName = payload.activeSceneName.ifBlank { "SCENE_UTAMA" },
                floatingConfig = clampedFloating,
                rules = payload.rules,
                presets = payload.presets,
                activePinRuleId = initialRule?.id,
                activePinStepId = initialStep?.id,
                queueSize = 0
            )
        }
    }

    fun emergencyStop() {
        synchronized(queueLock) {
            pendingQueue.clear()
        }
        activeChallengeCount.set(0)
        dispatcher.emergencyStopAll()
        val rec = TriggerExecutionRecord(
            timestampMs = System.currentTimeMillis(),
            ruleId = "EMERGENCY",
            ruleName = "Emergency Stop",
            source = UniversalTriggerSource.CUSTOM_EVENT,
            senderName = "Sistem",
            quantity = 0,
            status = DispatchStatus.STOPPED_EMERGENCY,
            detail = "Seluruh antrean, suara, dan overlay dihentikan seketika"
        )
        _state.update { st ->
            st.copy(
                isEmergencyStopped = true,
                isChallengeExecuting = false,
                cooldownStatusHint = "",
                queueSize = 0,
                activeOverlays = emptyList(),
                lastSimEvent = null,
                history = (listOf(rec) + st.history).take(MAX_HISTORY)
            )
        }
    }

    fun resumeAfterEmergencyStop() {
        _state.update { it.copy(isEmergencyStopped = false, isEngineEnabled = true) }
        prefs.edit().putBoolean(KEY_ENGINE_ENABLED, true).apply()
    }

    fun resetCounter(ruleId: String) {
        _state.update { st ->
            val updated = st.counters.toMutableMap()
            updated.remove(ruleId)
            st.copy(counters = updated)
        }
    }

    fun resetAllCounters() {
        _state.update { it.copy(counters = emptyMap()) }
    }

    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
    }

    fun release() {
        emergencyStop()
        scope.cancel()
    }

    // ==================== Rule CRUD ====================

    fun addOrUpdateRule(rule: UniversalTriggerRule) {
        val current = _state.value.rules.toMutableList()
        val idx = current.indexOfFirst { it.id == rule.id }
        if (idx >= 0) {
            current[idx] = rule
        } else {
            current.add(rule)
        }
        persistAndUpdateRules(current)
    }

    fun toggleRuleEnabled(ruleId: String) {
        val updated = _state.value.rules.map {
            if (it.id == ruleId) it.copy(enabled = !it.enabled) else it
        }
        persistAndUpdateRules(updated)
    }

    fun selectActivePin(ruleId: String?, stepId: String? = null) {
        _state.update { st ->
            val targetRule = ruleId?.let { id -> st.rules.firstOrNull { it.id == id } }
            val validStepId = if (targetRule != null) {
                stepId?.takeIf { sid -> targetRule.actionSequence.any { it.id == sid } }
                    ?: targetRule.actionSequence.firstOrNull {
                        it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
                    }?.id
            } else {
                null
            }
            st.copy(activePinRuleId = targetRule?.id, activePinStepId = validStepId)
        }
    }

    fun toggleRulePinVisibility(ruleId: String) {
        var becameVisible = false
        val updated = _state.value.rules.map { rule ->
            if (rule.id == ruleId) {
                becameVisible = !rule.isPinVisible
                rule.copy(isPinVisible = becameVisible)
            } else {
                rule
            }
        }
        persistAndUpdateRules(updated)
        if (becameVisible) {
            selectActivePin(ruleId, null)
        } else {
            _state.update { st ->
                val nextRule = if (st.activePinRuleId == ruleId) {
                    updated.firstOrNull { it.isPinVisible } ?: updated.firstOrNull()
                } else {
                    updated.firstOrNull { it.id == st.activePinRuleId }
                }
                val nextStep = nextRule?.actionSequence?.firstOrNull {
                    it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
                }?.id
                st.copy(activePinRuleId = nextRule?.id, activePinStepId = nextStep)
            }
        }
    }

    fun setAllPinsVisible(visible: Boolean) {
        val updated = _state.value.rules.map { it.copy(isPinVisible = visible) }
        persistAndUpdateRules(updated)
        if (visible) {
            val active = _state.value.activePinRuleId ?: updated.firstOrNull()?.id
            selectActivePin(active, _state.value.activePinStepId)
        }
    }

    fun updateRuleColor(ruleId: String, colorHex: Long) {
        val updated = _state.value.rules.map { rule ->
            if (rule.id == ruleId) rule.copy(accentColorHex = colorHex) else rule
        }
        persistAndUpdateRules(updated)
    }

    fun cycleActivePin(delta: Int) {
        val st = _state.value
        val candidates = st.rules.filter { it.isPinVisible }.ifEmpty { st.rules }
        if (candidates.isEmpty()) return

        val pinPairs = mutableListOf<Pair<String, String?>>()
        for (r in candidates) {
            val touchSteps = r.actionSequence.filter {
                it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
            }
            if (touchSteps.isEmpty()) {
                pinPairs += r.id to null
            } else {
                for (s in touchSteps) {
                    pinPairs += r.id to s.id
                }
            }
        }
        if (pinPairs.isEmpty()) return
        val curIdx = pinPairs.indexOfFirst { (rId, sId) ->
            rId == st.activePinRuleId && (sId == st.activePinStepId || sId == null)
        }.coerceAtLeast(0)
        val nextIdx = ((curIdx + delta) % pinPairs.size + pinPairs.size) % pinPairs.size
        val (nextRuleId, nextStepId) = pinPairs[nextIdx]
        _state.update { it.copy(activePinRuleId = nextRuleId, activePinStepId = nextStepId) }
    }

    fun duplicateRule(ruleId: String): UniversalTriggerRule? {
        val current = _state.value.rules.toMutableList()
        val idx = current.indexOfFirst { it.id == ruleId }
        if (idx < 0) return null
        val orig = current[idx]
        val usedColors = current.map { it.accentColorHex }.toSet()
        val stepIdMap = mutableMapOf<String, String>()
        val copiedSteps = orig.actionSequence.map { step ->
            val newId = UUID.randomUUID().toString()
            stepIdMap[step.id] = newId
            step.copy(id = newId)
        }
        val mappedCooldownStepId = orig.cooldownTargetStepId?.let { oldId -> stepIdMap[oldId] ?: oldId }
        val copyRule = orig.copy(
            id = UUID.randomUUID().toString(),
            triggerName = "${orig.triggerName} (Salinan)",
            accentColorHex = ChallengeColorPalette.nextUnusedColor(usedColors),
            cooldownTargetStepId = mappedCooldownStepId,
            conditions = orig.conditions.map { it.copy(id = UUID.randomUUID().toString()) },
            actionSequence = copiedSteps
        )
        current.add(idx + 1, copyRule)
        persistAndUpdateRules(current)
        return copyRule
    }

    fun deleteRule(ruleId: String) {
        val updated = _state.value.rules.filterNot { it.id == ruleId }
        _state.update { st ->
            val counters = st.counters.toMutableMap().apply { remove(ruleId) }
            val nextActive = if (st.activePinRuleId == ruleId) {
                updated.firstOrNull { it.isPinVisible }?.id ?: updated.firstOrNull()?.id
            } else {
                st.activePinRuleId
            }
            st.copy(rules = updated, counters = counters, activePinRuleId = nextActive)
        }
        prefs.edit().putString(KEY_RULES, UniversalInteractionJsonCodec.encodeRules(updated)).apply()
    }

    fun moveRule(fromIndex: Int, toIndex: Int) {
        val list = _state.value.rules.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        persistAndUpdateRules(list)
    }

    fun setFloatingOverlayRunning(running: Boolean) {
        _state.update {
            it.copy(
                isFloatingOverlayRunning = running,
                isFloatingHiddenForLive = if (!running) false else it.isFloatingHiddenForLive,
                isPositionEditMode = if (!running) false else it.isPositionEditMode
            )
        }
    }

    fun setFloatingHiddenForLive(hidden: Boolean) {
        _state.update {
            it.copy(
                isFloatingHiddenForLive = hidden,
                isPositionEditMode = if (hidden) false else it.isPositionEditMode
            )
        }
    }

    fun setPositionEditMode(editing: Boolean) {
        _state.update {
            it.copy(
                isPositionEditMode = editing,
                isFloatingHiddenForLive = if (editing) false else it.isFloatingHiddenForLive
            )
        }
    }

    fun createEmptyConcept(
        source: UniversalTriggerSource = UniversalTriggerSource.GIFT,
        name: String? = null
    ): UniversalTriggerRule {
        val currentRules = _state.value.rules
        val currentCount = currentRules.size
        val defaultCoords = listOf(
            0.80f to 0.72f,
            0.72f to 0.78f,
            0.86f to 0.56f,
            0.64f to 0.80f,
            0.50f to 0.76f,
            0.22f to 0.72f
        )
        val (nx, ny) = defaultCoords[currentCount % defaultCoords.size]
        val usedColors = currentRules.map { it.accentColorHex }.toSet()
        val pickedColor = ChallengeColorPalette.nextUnusedColor(usedColors)
        val rule = UniversalTriggerRule(
            isPinVisible = true,
            accentColorHex = pickedColor,
            triggerSource = source,
            triggerName = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Konsep #${currentCount + 1}",
            overlayPosX = nx,
            overlayPosY = ny,
            actionSequence = emptyList()
        )
        addOrUpdateRule(rule)
        _state.update {
            it.copy(
                activePinRuleId = rule.id,
                activePinStepId = null,
                isFloatingHiddenForLive = false
            )
        }
        return rule
    }

    fun createQuickTapTarget(
        source: UniversalTriggerSource = UniversalTriggerSource.GIFT,
        name: String? = null
    ): UniversalTriggerRule {
        val currentRules = _state.value.rules
        val currentCount = currentRules.size
        val defaultCoords = listOf(
            0.80f to 0.72f,
            0.72f to 0.78f,
            0.86f to 0.56f,
            0.64f to 0.80f,
            0.50f to 0.76f,
            0.22f to 0.72f
        )
        val (nx, ny) = defaultCoords[currentCount % defaultCoords.size]
        val usedColors = currentRules.map { it.accentColorHex }.toSet()
        val pickedColor = ChallengeColorPalette.nextUnusedColor(usedColors)
        val coordText = String.format(java.util.Locale.US, "%.2f,%.2f", nx, ny)
        val rule = UniversalTriggerRule(
            isPinVisible = true,
            accentColorHex = pickedColor,
            triggerSource = source,
            triggerName = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Challenge #${currentCount + 1}",
            overlayPosX = nx,
            overlayPosY = ny,
            actionSequence = listOf(
                UniversalActionStep(
                    actionType = ActionType.SIMULATED_CLICK,
                    paramValue = coordText,
                    secondaryValue = "70",
                    durationMs = 70L,
                    posX = nx,
                    posY = ny
                )
            )
        )
        addOrUpdateRule(rule)
        _state.update {
            it.copy(
                activePinRuleId = rule.id,
                activePinStepId = rule.actionSequence.firstOrNull()?.id,
                isPositionEditMode = true,
                isFloatingHiddenForLive = false
            )
        }
        return rule
    }

    fun addChoreographyTouchStep(
        ruleId: String,
        actionType: ActionType = ActionType.SIMULATED_CLICK
    ): UniversalActionStep? {
        val current = _state.value.rules.toMutableList()
        val idx = current.indexOfFirst { it.id == ruleId }
        if (idx < 0) return null
        val rule = current[idx]
        val existingTouchSteps = rule.actionSequence.filter {
            it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE
        }
        val lastTouch = existingTouchSteps.lastOrNull()
        val baseX = lastTouch?.posX ?: rule.overlayPosX
        val baseY = lastTouch?.posY ?: rule.overlayPosY
        val nx = if (existingTouchSteps.isEmpty()) baseX else ((baseX + 0.06f).let { if (it > 0.90f) 0.25f else it }).coerceIn(0.05f, 0.95f)
        val ny = if (existingTouchSteps.isEmpty()) baseY else ((baseY - 0.06f).let { if (it < 0.10f) 0.80f else it }).coerceIn(0.05f, 0.95f)
        val coordText = String.format(java.util.Locale.US, "%.3f,%.3f", nx, ny)
        val isSwipe = actionType == ActionType.SIMULATED_SWIPE
        val newStep = UniversalActionStep(
            enabled = true,
            actionType = actionType,
            paramValue = if (isSwipe) "RIGHT" else coordText,
            secondaryValue = if (isSwipe) "220" else "70",
            durationMs = if (isSwipe) 220L else 70L,
            delayMs = if (existingTouchSteps.isEmpty()) 0L else 180L,
            repeatCount = 1,
            executionMode = ExecutionMode.SEQUENTIAL,
            posX = nx,
            posY = ny
        )
        // Sisipkan sebelum langkah media paralel (CUSTOM_IMAGE / PLAY_SOUND) agar koreografi sentuhan tetap berurutan rapi
        val mediaIdx = rule.actionSequence.indexOfFirst {
            it.actionType != ActionType.SIMULATED_CLICK &&
                it.actionType != ActionType.SIMULATED_SWIPE &&
                it.actionType != ActionType.DELAY
        }
        val updatedSeq = rule.actionSequence.toMutableList()
        if (mediaIdx >= 0) {
            updatedSeq.add(mediaIdx, newStep)
        } else {
            updatedSeq.add(newStep)
        }
        current[idx] = rule.copy(isPinVisible = true, actionSequence = updatedSeq)
        persistAndUpdateRules(current)
        _state.update {
            it.copy(
                activePinRuleId = ruleId,
                activePinStepId = newStep.id,
                isPositionEditMode = true,
                isFloatingHiddenForLive = false
            )
        }
        return newStep
    }

    fun addCustomChoreographyStep(ruleId: String, step: UniversalActionStep) {
        val current = _state.value.rules.toMutableList()
        val idx = current.indexOfFirst { it.id == ruleId }
        if (idx < 0) return
        val rule = current[idx]
        val updatedSeq = rule.actionSequence.toMutableList().apply { add(step) }
        current[idx] = rule.copy(actionSequence = updatedSeq)
        persistAndUpdateRules(current)
    }

    fun duplicateChoreographyStep(ruleId: String, stepId: String) {
        val current = _state.value.rules.toMutableList()
        val idx = current.indexOfFirst { it.id == ruleId }
        if (idx < 0) return
        val seq = current[idx].actionSequence.toMutableList()
        val sIdx = seq.indexOfFirst { it.id == stepId }
        if (sIdx < 0) return
        val orig = seq[sIdx]
        val isTouch = orig.actionType == ActionType.SIMULATED_CLICK || orig.actionType == ActionType.SIMULATED_SWIPE
        val offsetX = if (isTouch && orig.posX != null) (orig.posX + 0.04f).coerceIn(0.05f, 0.95f) else orig.posX
        val offsetY = if (isTouch && orig.posY != null) (orig.posY - 0.04f).coerceIn(0.05f, 0.95f) else orig.posY
        val cloned = orig.copy(
            id = UUID.randomUUID().toString(),
            posX = offsetX,
            posY = offsetY,
            paramValue = if (orig.actionType == ActionType.SIMULATED_CLICK && offsetX != null && offsetY != null) {
                String.format(Locale.US, "%.3f,%.3f", offsetX, offsetY)
            } else {
                orig.paramValue
            }
        )
        seq.add(sIdx + 1, cloned)
        current[idx] = current[idx].copy(actionSequence = seq)
        persistAndUpdateRules(current)
    }

    fun removeChoreographyStep(ruleId: String, stepId: String) {
        val current = _state.value.rules.toMutableList()
        val idx = current.indexOfFirst { it.id == ruleId }
        if (idx < 0) return
        val rule = current[idx]
        val updatedSeq = rule.actionSequence.filterNot { it.id == stepId }
        current[idx] = rule.copy(
            cooldownTargetStepId = if (rule.cooldownTargetStepId == stepId) null else rule.cooldownTargetStepId,
            actionSequence = updatedSeq
        )
        persistAndUpdateRules(current)
    }

    fun moveChoreographyStep(ruleId: String, stepId: String, delta: Int) {
        val current = _state.value.rules.toMutableList()
        val rIdx = current.indexOfFirst { it.id == ruleId }
        if (rIdx < 0) return
        val seq = current[rIdx].actionSequence.toMutableList()
        val fromIdx = seq.indexOfFirst { it.id == stepId }
        val toIdx = fromIdx + delta
        if (fromIdx < 0 || toIdx !in seq.indices || fromIdx == toIdx) return
        val moved = seq.removeAt(fromIdx)
        seq.add(toIdx, moved)
        current[rIdx] = current[rIdx].copy(actionSequence = seq)
        persistAndUpdateRules(current)
    }

    fun updateFloatingConfig(transform: (FloatingWindowConfig) -> FloatingWindowConfig) {
        val rawNext = transform(_state.value.floatingConfig)
        val clamped = rawNext.copy(
            hudXRatio = rawNext.hudXRatio.coerceIn(0f, 1f),
            hudYRatio = rawNext.hudYRatio.coerceIn(0f, 1f),
            hudScale = rawNext.hudScale.coerceIn(0.65f, 1.35f),
            hudOpacity = rawNext.hudOpacity.coerceIn(0.30f, 1.0f),
            panelXRatio = rawNext.panelXRatio.coerceIn(0f, 1f),
            panelYRatio = rawNext.panelYRatio.coerceIn(0f, 1f),
            panelWidthRatio = rawNext.panelWidthRatio.coerceIn(0.38f, 0.98f),
            panelHeightRatio = rawNext.panelHeightRatio.coerceIn(0.35f, 0.96f),
            panelOpacity = rawNext.panelOpacity.coerceIn(0.45f, 1.0f)
        )
        prefs.edit().putString(KEY_FLOATING_CONFIG, UniversalInteractionJsonCodec.encodeFloatingConfig(clamped)).apply()
        _state.update { it.copy(floatingConfig = clamped) }
    }

    fun resetFloatingConfig() {
        val defaults = FloatingWindowConfig()
        prefs.edit().putString(KEY_FLOATING_CONFIG, UniversalInteractionJsonCodec.encodeFloatingConfig(defaults)).apply()
        _state.update { it.copy(floatingConfig = defaults) }
    }

    fun resetFloatingPosition() {
        updateFloatingConfig {
            it.copy(
                hudXRatio = 0.08f,
                hudYRatio = 0.16f,
                hudCollapsed = false
            )
        }
    }

    fun updateRulePosition(ruleId: String, posX: Float, posY: Float) {
        updateStepPosition(ruleId, null, posX, posY)
    }

    fun updateStepPosition(ruleId: String, stepId: String?, posX: Float, posY: Float) {
        val clampedX = posX.coerceIn(0.01f, 0.99f)
        val clampedY = posY.coerceIn(0.01f, 0.99f)
        val coordText = String.format(java.util.Locale.US, "%.3f,%.3f", clampedX, clampedY)
        val visualOverlayTypes = setOf(
            ActionType.PROFILE_OVERLAY,
            ActionType.MEME_OVERLAY,
            ActionType.CUSTOM_IMAGE,
            ActionType.TEXT_OVERLAY,
            ActionType.SHOW_OVERLAY,
            ActionType.ANIMATION,
            ActionType.TIER_OVERLAY,
            ActionType.RANDOM_SELECTION
        )
        val updated = _state.value.rules.map { rule ->
            if (rule.id != ruleId) {
                rule
            } else {
                val touchSteps = rule.actionSequence.filter {
                    it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE
                }
                val firstTouch = touchSteps.firstOrNull()
                val targetStepId = stepId ?: firstTouch?.id
                val isFirstTouchOrRoot = targetStepId == null || firstTouch?.id == targetStepId
                val prevAnchorX = firstTouch?.posX ?: rule.overlayPosX
                val prevAnchorY = firstTouch?.posY ?: rule.overlayPosY
                rule.copy(
                    overlayPosX = if (isFirstTouchOrRoot) clampedX else rule.overlayPosX,
                    overlayPosY = if (isFirstTouchOrRoot) clampedY else rule.overlayPosY,
                    actionSequence = rule.actionSequence.map { step ->
                        val isExplicitTarget = stepId != null && step.id == stepId
                        val isTargetTouch = isExplicitTarget || (stepId == null && touchSteps.size <= 1 && (step.actionType == ActionType.SIMULATED_CLICK || step.actionType == ActionType.SIMULATED_SWIPE))
                        val isVisualStepAlignedWithRoot = isFirstTouchOrRoot &&
                            step.actionType in visualOverlayTypes && (
                            step.posX == null ||
                                (kotlin.math.abs((step.posX ?: prevAnchorX) - prevAnchorX) < 0.025f &&
                                    kotlin.math.abs((step.posY ?: prevAnchorY) - prevAnchorY) < 0.025f)
                            )
                        when {
                            isExplicitTarget && step.actionType == ActionType.SIMULATED_CLICK ->
                                step.copy(paramValue = coordText, posX = clampedX, posY = clampedY)
                            isExplicitTarget ->
                                step.copy(posX = clampedX, posY = clampedY)
                            isTargetTouch && step.actionType == ActionType.SIMULATED_CLICK ->
                                step.copy(paramValue = coordText, posX = clampedX, posY = clampedY)
                            isTargetTouch ->
                                step.copy(posX = clampedX, posY = clampedY)
                            isVisualStepAlignedWithRoot ->
                                step.copy(posX = clampedX, posY = clampedY)
                            else -> step
                        }
                    }
                )
            }
        }
        persistAndUpdateRules(updated)
    }

    fun nudgeRulePosition(ruleId: String, dx: Float, dy: Float) {
        nudgeStepPosition(ruleId, _state.value.activePinStepId, dx, dy)
    }

    fun nudgeStepPosition(ruleId: String, stepId: String?, dx: Float, dy: Float) {
        val rule = _state.value.rules.firstOrNull { it.id == ruleId } ?: return
        val targetStep = stepId?.let { id -> rule.actionSequence.firstOrNull { it.id == id } }
            ?: rule.actionSequence.firstOrNull {
                it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
            }
            ?: rule.actionSequence.firstOrNull {
                it.enabled && (it.actionType == ActionType.PROFILE_OVERLAY || it.actionType == ActionType.MEME_OVERLAY || it.actionType == ActionType.CUSTOM_IMAGE)
            }
        val curX = targetStep?.posX ?: rule.overlayPosX
        val curY = targetStep?.posY ?: rule.overlayPosY
        updateStepPosition(
            ruleId = ruleId,
            stepId = targetStep?.id,
            posX = (curX + dx).coerceIn(0.01f, 0.99f),
            posY = (curY + dy).coerceIn(0.01f, 0.99f)
        )
    }

    fun updateRuleScale(ruleId: String, scale: Float) {
        val clampedScale = scale.coerceIn(0.35f, 3.0f)
        val updated = _state.value.rules.map { rule ->
            if (rule.id == ruleId) rule.copy(overlayScale = clampedScale) else rule
        }
        persistAndUpdateRules(updated)
    }

    fun updateRuleImageOverlay(ruleId: String, imageUri: String, caption: String? = null) {
        val current = _state.value.rules.toMutableList()
        val idx = current.indexOfFirst { it.id == ruleId }
        if (idx < 0) return
        val rule = current[idx]
        val existingIdx = rule.actionSequence.indexOfFirst {
            it.actionType == ActionType.MEME_OVERLAY || it.actionType == ActionType.CUSTOM_IMAGE
        }
        val updatedSeq = rule.actionSequence.toMutableList()
        if (imageUri.isBlank()) {
            updatedSeq.removeAll { it.actionType == ActionType.MEME_OVERLAY || it.actionType == ActionType.CUSTOM_IMAGE }
        } else {
            val baseType = if (existingIdx >= 0) updatedSeq[existingIdx].actionType else ActionType.MEME_OVERLAY
            val nextStep = (if (existingIdx >= 0) updatedSeq[existingIdx] else UniversalActionStep(actionType = baseType)).copy(
                enabled = true,
                actionType = baseType,
                paramValue = imageUri,
                secondaryValue = caption ?: (if (existingIdx >= 0) updatedSeq[existingIdx].secondaryValue else ""),
                posX = rule.overlayPosX,
                posY = rule.overlayPosY,
                executionMode = ExecutionMode.PARALLEL
            )
            if (existingIdx >= 0) updatedSeq[existingIdx] = nextStep else updatedSeq.add(nextStep)
        }
        current[idx] = rule.copy(actionSequence = updatedSeq)
        persistAndUpdateRules(current)
    }

    fun updateStepParam(ruleId: String, stepId: String, paramValue: String) {
        val updated = _state.value.rules.map { rule ->
            if (rule.id != ruleId) {
                rule
            } else {
                rule.copy(
                    actionSequence = rule.actionSequence.map { s ->
                        if (s.id == stepId) s.copy(enabled = true, paramValue = paramValue) else s
                    }
                )
            }
        }
        persistAndUpdateRules(updated)
    }

    fun updateRuleSoundEffect(ruleId: String, soundSpec: String) {
        val current = _state.value.rules.toMutableList()
        val idx = current.indexOfFirst { it.id == ruleId }
        if (idx < 0) return
        val rule = current[idx]
        val existingIdx = rule.actionSequence.indexOfFirst {
            it.actionType == ActionType.PLAY_SOUND || it.actionType == ActionType.CUSTOM_BACKSOUND
        }
        val updatedSeq = rule.actionSequence.toMutableList()
        if (soundSpec.isBlank()) {
            updatedSeq.removeAll {
                it.actionType == ActionType.PLAY_SOUND || it.actionType == ActionType.CUSTOM_BACKSOUND
            }
        } else {
            val nextStep = (if (existingIdx >= 0) updatedSeq[existingIdx] else UniversalActionStep(actionType = ActionType.PLAY_SOUND)).copy(
                enabled = true,
                actionType = ActionType.PLAY_SOUND,
                paramValue = soundSpec,
                executionMode = ExecutionMode.PARALLEL
            )
            if (existingIdx >= 0) updatedSeq[existingIdx] = nextStep else updatedSeq.add(nextStep)
        }
        current[idx] = rule.copy(actionSequence = updatedSeq)
        persistAndUpdateRules(current)
    }

    private fun persistAndUpdateRules(rules: List<UniversalTriggerRule>) {
        prefs.edit().putString(KEY_RULES, UniversalInteractionJsonCodec.encodeRules(rules)).apply()
        _state.update { st ->
            val activeRule = st.activePinRuleId?.let { id -> rules.firstOrNull { it.id == id } }
                ?: rules.firstOrNull { it.isPinVisible }
                ?: rules.firstOrNull()
            val validStepId = if (activeRule != null) {
                st.activePinStepId?.takeIf { sid -> activeRule.actionSequence.any { it.id == sid } }
                    ?: activeRule.actionSequence.firstOrNull {
                        it.enabled && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
                    }?.id
            } else {
                null
            }
            st.copy(rules = rules, activePinRuleId = activeRule?.id, activePinStepId = validStepId)
        }
    }

    // ==================== Universal Preset CRUD + Assign Trigger ====================

    fun savePreset(preset: UniversalPreset) {
        val current = _state.value.presets.toMutableList()
        val idx = current.indexOfFirst { it.id == preset.id }
        if (idx >= 0) current[idx] = preset else current.add(preset)
        persistAndUpdatePresets(current)
    }

    fun renamePreset(presetId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        val updated = _state.value.presets.map {
            if (it.id == presetId) it.copy(name = clean) else it
        }
        persistAndUpdatePresets(updated)
    }

    fun duplicatePreset(presetId: String) {
        val current = _state.value.presets.toMutableList()
        val idx = current.indexOfFirst { it.id == presetId }
        if (idx < 0) return
        val orig = current[idx]
        val copy = orig.copy(
            id = UUID.randomUUID().toString(),
            name = "${orig.name} (Salinan)",
            conditions = orig.conditions.map { it.copy(id = UUID.randomUUID().toString()) },
            actionSequence = orig.actionSequence.map { it.copy(id = UUID.randomUUID().toString()) }
        )
        current.add(idx + 1, copy)
        persistAndUpdatePresets(current)
    }

    fun deletePreset(presetId: String) {
        val updated = _state.value.presets.filterNot { it.id == presetId }
        persistAndUpdatePresets(updated)
    }

    fun exportPresetJson(presetId: String): String? {
        val preset = _state.value.presets.firstOrNull { it.id == presetId } ?: return null
        return UniversalInteractionJsonCodec.exportPresetPackageJson(preset)
    }

    fun exportAllPresetsJson(): String {
        return UniversalInteractionJsonCodec.exportAllPresetsJson(_state.value.presets)
    }

    fun importPresetsJson(rawJson: String): Result<Int> {
        val parsed = UniversalInteractionJsonCodec.importPresetsFromJson(rawJson)
        return parsed.map { imported ->
            val existing = _state.value.presets.toMutableList()
            var count = 0
            for (preset in imported) {
                val fresh = preset.copy(
                    id = UUID.randomUUID().toString(),
                    conditions = preset.conditions.map { it.copy(id = UUID.randomUUID().toString()) },
                    actionSequence = preset.actionSequence.map { it.copy(id = UUID.randomUUID().toString()) }
                )
                existing.add(fresh)
                count++
            }
            persistAndUpdatePresets(existing)
            count
        }
    }

    /**
     * Menghubungkan preset universal apa pun ke sumber trigger mana pun (Like/Follow/Share/Gift/dll).
     */
    fun assignPresetToTrigger(
        presetId: String,
        targetSource: UniversalTriggerSource,
        customTriggerName: String? = null
    ): UniversalTriggerRule? {
        val preset = _state.value.presets.firstOrNull { it.id == presetId } ?: return null
        val newRule = UniversalTriggerRule(
            id = UUID.randomUUID().toString(),
            enabled = true,
            triggerSource = targetSource,
            triggerName = customTriggerName?.trim()?.takeIf { it.isNotEmpty() }
                ?: "${preset.name} (${targetSource.displayName})",
            enableConditions = preset.enableConditions && preset.conditions.isNotEmpty(),
            conditions = preset.conditions.map { it.copy(id = UUID.randomUUID().toString()) },
            accumulationMode = preset.accumulationMode,
            enableAccumulation = preset.accumulationMode == AccumulationMode.ACCUMULATION,
            enableThreshold = preset.accumulationMode == AccumulationMode.THRESHOLD,
            thresholdValue = preset.thresholdValue,
            enableCombo = preset.accumulationMode == AccumulationMode.COMBO,
            comboWindowMs = preset.comboWindowMs,
            enableMilestone = preset.accumulationMode == AccumulationMode.MILESTONE,
            milestoneStep = preset.milestoneStep,
            enableRandomSelection = preset.accumulationMode == AccumulationMode.RANDOM,
            enableCustomCondition = preset.accumulationMode == AccumulationMode.CUSTOM,
            actionSequence = preset.actionSequence.map { it.copy(id = UUID.randomUUID().toString()) },
            enableDelayBefore = preset.delayBeforeMs > 0L,
            delayBeforeMs = preset.delayBeforeMs,
            enableDelayAfter = preset.delayAfterMs > 0L,
            delayAfterMs = preset.delayAfterMs,
            enableInterval = preset.intervalMs > 0L,
            intervalMs = preset.intervalMs,
            enableDuration = true,
            durationMs = preset.durationMs,
            enableRepeat = preset.repeatCount > 1,
            repeatCount = preset.repeatCount,
            enableCooldown = preset.cooldownMs > 0L,
            cooldownMs = preset.cooldownMs,
            enablePriority = true,
            priority = preset.priority,
            enableResetCondition = preset.resetCondition != ResetConditionType.NONE,
            resetCondition = preset.resetCondition,
            assignedPresetId = preset.id
        )
        addOrUpdateRule(newRule)
        return newRule
    }

    private fun persistAndUpdatePresets(presets: List<UniversalPreset>) {
        prefs.edit().putString(KEY_PRESETS, UniversalInteractionJsonCodec.encodePresets(presets)).apply()
        _state.update { it.copy(presets = presets) }
    }

    // ==================== ActionDispatchSink Implementation ====================

    override fun onShowOverlay(overlay: ActiveVisualOverlay) {
        _state.update { st ->
            val kept = (st.activeOverlays + overlay).takeLast(6)
            st.copy(activeOverlays = kept)
        }
    }

    override fun onHideOverlay(targetFilter: String) {
        _state.update { st ->
            val remaining = if (targetFilter.equals("ALL", ignoreCase = true) || targetFilter.isBlank()) {
                emptyList()
            } else {
                st.activeOverlays.filterNot {
                    it.title.contains(targetFilter, ignoreCase = true) ||
                        it.kind.name.contains(targetFilter, ignoreCase = true)
                }
            }
            st.copy(activeOverlays = remaining)
        }
    }

    override fun onChangeScene(sceneName: String) {
        val clean = sceneName.ifBlank { "SCENE_UTAMA" }
        prefs.edit().putString(KEY_ACTIVE_SCENE, clean).apply()
        _state.update { it.copy(activeSceneName = clean) }
    }

    override fun onInteractionTestSimulated(simEvent: InteractionTestSimEvent) {
        _state.update { it.copy(lastSimEvent = simEvent) }
    }

    override fun onRecordStepDetail(
        rule: UniversalTriggerRule,
        event: LiveInteractionEvent,
        status: DispatchStatus,
        detail: String
    ) {
        appendHistory(
            TriggerExecutionRecord(
                timestampMs = System.currentTimeMillis(),
                ruleId = rule.id,
                ruleName = rule.triggerName,
                source = event.source,
                senderName = event.senderName,
                quantity = event.quantity,
                status = status,
                detail = detail
            )
        )
    }

    override fun onTouchLockChanged(locked: Boolean) {
        if (locked) {
            enterChallengeExecution()
        } else {
            exitChallengeExecution()
        }
    }

    private fun appendHistory(record: TriggerExecutionRecord) {
        _state.update { st ->
            st.copy(history = (listOf(record) + st.history).take(MAX_HISTORY))
        }
    }
}
