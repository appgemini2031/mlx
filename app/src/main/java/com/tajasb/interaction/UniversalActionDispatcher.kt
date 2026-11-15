package com.tajasb.interaction

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.tajasb.notif.OverlayBannerBroadcaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pure JVM helper for gesture retry timings when physical user touch (joystick/basic attack) cancels Android dispatchGesture.
 */
object GestureRetryPolicy {
    const val MAX_TOUCH_CONFLICT_RETRIES = 36

    fun backoffDelayMs(attemptIndex: Int): Long = when {
        attemptIndex < 12 -> 35L
        attemptIndex < 24 -> 50L
        else -> 75L
    }

    fun shouldRetry(
        outcome: GestureOutcome,
        attemptIndex: Int,
        aborted: Boolean
    ): Boolean = outcome == GestureOutcome.CANCELLED_BY_TOUCH && !aborted && attemptIndex < MAX_TOUCH_CONFLICT_RETRIES
}

/**
 * Pure JVM helper computing which step indices should keep Screen Touch Lock continuously engaged
 * so inter-step gaps in touch choreography cannot be interrupted by accidental screen taps.
 */
object TouchExecutionPlanner {
    private const val MAX_BRIDGE_GAP_MS = 2500L

    fun isHardwareTouchAction(type: ActionType): Boolean =
        type == ActionType.SIMULATED_CLICK ||
            type == ActionType.SIMULATED_SWIPE ||
            type == ActionType.HOME_BACK_GAME

    fun computeLockedStepIndices(steps: List<UniversalActionStep>): Set<Int> {
        val touchIndices = steps.indices.filter { isHardwareTouchAction(steps[it].actionType) }
        if (touchIndices.isEmpty()) return emptySet()
        val locked = touchIndices.toMutableSet()
        for (k in 0 until touchIndices.lastIndex) {
            val startIdx = touchIndices[k]
            val endIdx = touchIndices[k + 1]
            var totalGapMs = 0L
            var hasBlockingWait = false
            for (idx in (startIdx + 1) until endIdx) {
                val st = steps[idx]
                totalGapMs += st.delayMs.coerceAtLeast(0L)
                if (st.actionType == ActionType.DELAY || st.actionType == ActionType.WAIT || st.actionType == ActionType.DURATION) {
                    val waitMs = st.paramValue.trim().toLongOrNull() ?: st.durationMs
                    totalGapMs += waitMs.coerceAtLeast(0L)
                }
                if (totalGapMs > MAX_BRIDGE_GAP_MS) {
                    hasBlockingWait = true
                    break
                }
            }
            val nextStepDelay = steps[endIdx].delayMs.coerceAtLeast(0L)
            if (!hasBlockingWait && (totalGapMs + nextStepDelay) <= MAX_BRIDGE_GAP_MS) {
                for (idx in startIdx..endIdx) {
                    locked.add(idx)
                }
            }
        }
        return locked
    }
}

/**
 * Callback untuk mempublikasikan hasil visual/simulasi/scene ke UI Engine secara real-time.
 */
interface ActionDispatchSink {
    fun onShowOverlay(overlay: ActiveVisualOverlay)
    fun onHideOverlay(targetFilter: String)
    fun onChangeScene(sceneName: String)
    fun onInteractionTestSimulated(simEvent: InteractionTestSimEvent)
    fun onRecordStepDetail(rule: UniversalTriggerRule, event: LiveInteractionEvent, status: DispatchStatus, detail: String)
    fun onTouchLockChanged(locked: Boolean)
    suspend fun ensureTouchLockSynchronized(locked: Boolean) {}
    fun onTouchConflictChanged(hint: String) {}
}

/**
 * Single Action Dispatcher untuk seluruh kategori:
 * VISUAL, AUDIO, TIMING, LOGIC, dan INTERACTION_TEST (terbatas simulasi aman di luar pertandingan MLBB).
 */
class UniversalActionDispatcher(
    private val context: Context,
    private val sink: ActionDispatchSink
) {
    companion object {
        private const val TAG = "UniversalActionDisp"
        private const val SAMPLE_RATE = 22050
    }

    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeSoundTracks = CopyOnWriteArrayList<AudioTrack>()
    private var customClipPlayer: MediaPlayer? = null
    private val clipLock = Any()
    private val hardwareTouchMutex = Mutex()
    private val emergencyEpoch = AtomicLong(0L)
    @Volatile
    private var actionVolume: Float = 0.85f

    fun emergencyStopAll() {
        emergencyEpoch.incrementAndGet()
        stopAllSounds()
        sink.onTouchConflictChanged("")
        sink.onHideOverlay("ALL")
    }

    fun stopAllSounds() {
        for (track in activeSoundTracks) {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                track.release()
            } catch (_: Throwable) {}
        }
        activeSoundTracks.clear()

        synchronized(clipLock) {
            customClipPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                } catch (_: Throwable) {}
            }
            customClipPlayer = null
        }
    }

    suspend fun dispatchRuleActions(
        rule: UniversalTriggerRule,
        event: LiveInteractionEvent,
        counterCount: Int,
        isMlbbMatchActive: Boolean,
        globalDelayBeforeMs: Long = 0L,
        globalDelayAfterMs: Long = 0L,
        enableTouchLock: Boolean = true
    ): String {
        val startEpoch = emergencyEpoch.get()
        val enabledSteps = rule.actionSequence.filter { it.enabled }
        if (enabledSteps.isEmpty()) {
            return "Rule tanpa action aktif"
        }

        // 1. Global Delay Sebelum + Delay Sebelum Rule + Random Delay
        //    (Global delay sudah mengecualikan rule Scan Cooldown ML oleh caller)
        val totalBeforeDelay = globalDelayBeforeMs.coerceIn(0L, 60_000L) + buildBeforeDelay(rule)
        if (totalBeforeDelay > 0L) {
            delay(totalBeforeDelay)
            if (isAborted(startEpoch)) return "Dihentikan darurat sebelum eksekusi"
        }

        // 2. Pilih sequence atau mode Random
        val stepsToRun = if (rule.accumulationMode == AccumulationMode.RANDOM || rule.enableRandomSelection) {
            listOf(enabledSteps[Random.nextInt(enabledSteps.size)])
        } else {
            enabledSteps
        }

        val ruleRepeats = if (rule.enableRepeat) rule.repeatCount.coerceIn(1, 30) else 1
        val intervalMs = if (rule.enableInterval) rule.intervalMs.coerceIn(0L, 60_000L) else 0L
        val executedLabels = mutableListOf<String>()
        val lockedStepIndices = if (enableTouchLock && !isMlbbMatchActive) {
            TouchExecutionPlanner.computeLockedStepIndices(stepsToRun)
        } else {
            emptySet()
        }

        for (repeatIdx in 0 until ruleRepeats) {
            if (isAborted(startEpoch)) break

            var isSpanTouchLocked = false
            try {
                var i = 0
                while (i < stepsToRun.size) {
                    if (isAborted(startEpoch)) break
                    val step = stepsToRun[i]

                    val shouldKeepLockedHere = enableTouchLock && !isMlbbMatchActive && (i in lockedStepIndices)
                    if (shouldKeepLockedHere && !isSpanTouchLocked) {
                        sink.onTouchLockChanged(true)
                        sink.ensureTouchLockSynchronized(true)
                        isSpanTouchLocked = true
                    } else if (!shouldKeepLockedHere && isSpanTouchLocked) {
                        sink.ensureTouchLockSynchronized(false)
                        sink.onTouchLockChanged(false)
                        isSpanTouchLocked = false
                    }

                    if (step.executionMode == ExecutionMode.PARALLEL) {
                        // Kumpulkan seluruh langkah PARALLEL berurutan agar berjalan bersamaan bila kompatibel
                        val parallelGroup = mutableListOf<UniversalActionStep>()
                        val groupStartIdx = i
                        while (i < stepsToRun.size && stepsToRun[i].executionMode == ExecutionMode.PARALLEL) {
                            parallelGroup.add(stepsToRun[i])
                            i++
                        }
                        val groupTouchesScreen = enableTouchLock && !isMlbbMatchActive &&
                            (groupStartIdx until i).any { it in lockedStepIndices }
                        if (groupTouchesScreen && !isSpanTouchLocked) {
                            sink.onTouchLockChanged(true)
                            sink.ensureTouchLockSynchronized(true)
                            isSpanTouchLocked = true
                        }
                        coroutineScope {
                            val jobs = mutableListOf<Job>()
                            for (pStep in parallelGroup) {
                                jobs += launch {
                                    val label = executeSingleStep(
                                        rule = rule,
                                        step = pStep,
                                        event = event,
                                        counterCount = counterCount,
                                        isMlbbMatchActive = isMlbbMatchActive,
                                        startEpoch = startEpoch,
                                        enableTouchLock = enableTouchLock,
                                        alreadySpanLocked = isSpanTouchLocked
                                    )
                                    synchronized(executedLabels) { executedLabels.add(label) }
                                }
                            }
                            jobs.forEach { it.join() }
                        }
                    } else {
                        val label = executeSingleStep(
                            rule = rule,
                            step = step,
                            event = event,
                            counterCount = counterCount,
                            isMlbbMatchActive = isMlbbMatchActive,
                            startEpoch = startEpoch,
                            enableTouchLock = enableTouchLock,
                            alreadySpanLocked = isSpanTouchLocked
                        )
                        executedLabels.add(label)
                        i++
                    }

                    // Lepas kunci layar bila langkah berikutnya tidak berada dalam blok sentuh kontinu
                    val nextShouldLock = enableTouchLock && !isMlbbMatchActive && (i in lockedStepIndices)
                    if (isSpanTouchLocked && !nextShouldLock) {
                        sink.ensureTouchLockSynchronized(false)
                        sink.onTouchLockChanged(false)
                        isSpanTouchLocked = false
                    }

                    if (intervalMs > 0L && i < stepsToRun.size) {
                        delay(intervalMs)
                    }
                }
            } finally {
                if (isSpanTouchLocked) {
                    sink.ensureTouchLockSynchronized(false)
                    sink.onTouchLockChanged(false)
                    isSpanTouchLocked = false
                }
                sink.onTouchConflictChanged("")
            }

            if (intervalMs > 0L && repeatIdx < ruleRepeats - 1) {
                delay(intervalMs)
            }
        }

        // 3. Delay Sesudah Rule + Global Delay Sesudah Aksi
        val afterRuleDelay = if (rule.enableDelayAfter) rule.delayAfterMs.coerceIn(0L, 60_000L) else 0L
        val totalAfterDelay = afterRuleDelay + globalDelayAfterMs.coerceIn(0L, 60_000L)
        if (!isAborted(startEpoch) && totalAfterDelay > 0L) {
            delay(totalAfterDelay)
        }

        return executedLabels.joinToString(" → ").ifBlank { "Selesai" }
    }

    private suspend fun executeSingleStep(
        rule: UniversalTriggerRule,
        step: UniversalActionStep,
        event: LiveInteractionEvent,
        counterCount: Int,
        isMlbbMatchActive: Boolean,
        startEpoch: Long,
        enableTouchLock: Boolean = true,
        alreadySpanLocked: Boolean = false
    ): String {
        if (isAborted(startEpoch)) return "${step.actionType.displayName} (Batal)"

        if (step.delayMs > 0L) {
            delay(step.delayMs.coerceAtMost(60_000L))
            if (isAborted(startEpoch)) return "${step.actionType.displayName} (Batal)"
        }

        val effectiveDur = when {
            !rule.enableDuration -> step.durationMs.coerceIn(0L, 60_000L)
            step.durationMs != 2500L -> step.durationMs.coerceIn(0L, 60_000L)
            else -> rule.durationMs.coerceIn(0L, 60_000L)
        }
        val repeats = step.repeatCount.coerceIn(1, 20)
        var summary = step.actionType.displayName

        // Kunci layar HANYA selama aksi sentuh / navigasi nyata sedang dikerjakan (bukan saat delay/cooldown/tes 1 konsep)
        val acquiresTouchLock = !alreadySpanLocked && enableTouchLock && !isMlbbMatchActive &&
            TouchExecutionPlanner.isHardwareTouchAction(step.actionType)
        if (acquiresTouchLock) {
            sink.onTouchLockChanged(true)
            sink.ensureTouchLockSynchronized(true)
        }
        try {
            for (r in 0 until repeats) {
                if (isAborted(startEpoch)) break
                val resolvedParam = resolveTemplate(step.paramValue, event, counterCount, rule)
                val resolvedSecondary = resolveTemplate(step.secondaryValue, event, counterCount, rule)

                when (step.actionType) {
                // ---------------- VISUAL ----------------
                ActionType.SHOW_OVERLAY,
                ActionType.PROFILE_OVERLAY,
                ActionType.TIER_OVERLAY,
                ActionType.MEME_OVERLAY,
                ActionType.CUSTOM_IMAGE,
                ActionType.ANIMATION,
                ActionType.TEXT_OVERLAY -> {
                    val paramIsUri = isMediaUri(step.paramValue)
                    val secondaryIsUri = isMediaUri(step.secondaryValue)
                    val titleText = when (step.actionType) {
                        ActionType.PROFILE_OVERLAY -> resolvedParam.ifBlank { "👤 ${event.senderName}" }
                        ActionType.TIER_OVERLAY -> resolvedParam.ifBlank { "🏅 TIER • ${event.senderName}" }
                        ActionType.MEME_OVERLAY -> if (paramIsUri) resolvedSecondary.trim() else resolvedParam.ifBlank { "😂 ${event.senderName}" }
                        ActionType.CUSTOM_IMAGE -> if (paramIsUri) resolvedSecondary.trim() else resolvedParam.trim()
                        ActionType.ANIMATION -> resolvedParam.ifBlank { "✨ ANIMASI ${event.source.displayName.uppercase()}" }
                        else -> resolvedParam.ifBlank { "${event.senderName} (${event.source.displayName} x${event.quantity})" }
                    }
                    val subText = when (step.actionType) {
                        ActionType.CUSTOM_IMAGE -> ""
                        ActionType.MEME_OVERLAY -> if (paramIsUri || secondaryIsUri) "" else resolvedSecondary.ifBlank { "${event.source.displayName} • x${event.quantity}" }
                        ActionType.PROFILE_OVERLAY -> resolvedSecondary.ifBlank { "${event.source.displayName} • x${event.quantity}" }
                        else -> resolvedSecondary.ifBlank { "${event.source.displayName} • x${event.quantity} • Total #$counterCount" }
                    }
                    val anim = if (step.actionType == ActionType.ANIMATION) {
                        resolvedParam.uppercase().ifBlank { "PULSE" }
                    } else {
                        "PULSE"
                    }
                    val imgUri = when (step.actionType) {
                        ActionType.CUSTOM_IMAGE -> step.paramValue.trim()
                        ActionType.MEME_OVERLAY -> when {
                            paramIsUri -> step.paramValue.trim()
                            secondaryIsUri -> step.secondaryValue.trim()
                            else -> ""
                        }
                        ActionType.PROFILE_OVERLAY -> event.avatarUrl.trim()
                        else -> ""
                    }
                    val targetX = (step.posX ?: rule.overlayPosX).coerceIn(0.05f, 0.95f)
                    val targetY = (step.posY ?: rule.overlayPosY).coerceIn(0.05f, 0.95f)
                    val targetScale = (if (step.overlayScale != 1.0f || rule.overlayScale == 1.0f) {
                        step.overlayScale
                    } else {
                        rule.overlayScale
                    }).coerceIn(0.35f, 3.0f)

                    if (effectiveDur > 0L) {
                        val overlay = ActiveVisualOverlay(
                            kind = step.actionType,
                            title = titleText,
                            subtitle = subText,
                            imageUri = imgUri,
                            animationStyle = anim,
                            expiresAtMs = System.currentTimeMillis() + effectiveDur,
                            posX = targetX,
                            posY = targetY,
                            scale = targetScale
                        )
                        sink.onShowOverlay(overlay)

                        if (rule.broadcastToVirtualCamera) {
                            OverlayBannerBroadcaster.broadcastShow(
                                context = context,
                                senderName = event.senderName,
                                content = titleText,
                                isGift = event.source == UniversalTriggerSource.GIFT || event.source == UniversalTriggerSource.GIFT_QUANTITY,
                                bannerType = "COMMENT",
                                iconMode = "PRAYER",
                                customPrefix = step.actionType.displayName,
                                durationMs = effectiveDur,
                                posXpct = targetX,
                                posYpct = targetY,
                                scale = targetScale
                            )
                        }
                    }
                    summary = "${step.actionType.displayName}($titleText)"
                }

                ActionType.HIDE_OVERLAY -> {
                    val target = resolvedParam.ifBlank { "ALL" }
                    sink.onHideOverlay(target)
                    if (rule.broadcastToVirtualCamera) {
                        OverlayBannerBroadcaster.broadcastHideAll(context)
                    }
                    summary = "Hide Overlay($target)"
                }

                ActionType.CHANGE_SCENE -> {
                    val scene = resolvedParam.ifBlank { "SCENE_UTAMA" }
                    sink.onChangeScene(scene)
                    summary = "Scene -> $scene"
                }

                // ---------------- AUDIO ----------------
                ActionType.PLAY_SOUND -> {
                    playConfiguredSound(resolvedParam.ifBlank { "CHIME" })
                    summary = "Play Sound(${resolvedParam.ifBlank { "CHIME" }})"
                }

                ActionType.STOP_SOUND -> {
                    stopAllSounds()
                    summary = "Stop Sound"
                }

                ActionType.CUSTOM_BACKSOUND -> {
                    if (resolvedParam.startsWith("content://") || resolvedParam.startsWith("file://")) {
                        playAudioUri(resolvedParam)
                    } else {
                        playSynthesizedTone(523.25, 320)
                    }
                    summary = "Backsound"
                }

                ActionType.VOLUME_CONTROL -> {
                    val pct = resolvedParam.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100) ?: 75
                    actionVolume = (pct / 100f).coerceIn(0f, 1f)
                    summary = "Volume $pct%"
                }

                ActionType.AUDIO_SEQUENCE -> {
                    val tokens = resolvedParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val notes = if (tokens.isEmpty()) listOf("CHIME", "ALERT", "FANFARE") else tokens
                    for (token in notes) {
                        if (isAborted(startEpoch)) break
                        playConfiguredSound(token)
                        delay(260L)
                    }
                    summary = "AudioSeq(${notes.size})"
                }

                // ---------------- TIMING ----------------
                ActionType.DELAY, ActionType.WAIT, ActionType.DURATION -> {
                    val ms = resolvedParam.toLongOrNull()?.coerceIn(0L, 60_000L)
                        ?: step.durationMs.coerceIn(0L, 60_000L)
                    if (ms > 0L) {
                        delay(ms)
                    }
                    summary = "${step.actionType.displayName}(${ms}ms)"
                }

                ActionType.REPEAT, ActionType.COOLDOWN -> {
                    // Ditangani oleh loop repeat & cooldown rule, atau catat metadata langkah
                    summary = "${step.actionType.displayName}(${resolvedParam.ifBlank { "OK" }})"
                }

                // ---------------- LOGIC ----------------
                ActionType.RANDOM_SELECTION -> {
                    val choices = resolvedParam.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    if (choices.isNotEmpty()) {
                        val picked = choices[Random.nextInt(choices.size)]
                        if (effectiveDur > 0L) {
                            sink.onShowOverlay(
                                ActiveVisualOverlay(
                                    kind = ActionType.TEXT_OVERLAY,
                                    title = picked,
                                    subtitle = "Random Selection • ${event.senderName}",
                                    expiresAtMs = System.currentTimeMillis() + effectiveDur,
                                    posX = (step.posX ?: rule.overlayPosX).coerceIn(0.05f, 0.95f),
                                    posY = (step.posY ?: rule.overlayPosY).coerceIn(0.05f, 0.95f),
                                    scale = (if (step.overlayScale != 1.0f || rule.overlayScale == 1.0f) {
                                        step.overlayScale
                                    } else {
                                        rule.overlayScale
                                    }).coerceIn(0.35f, 3.0f)
                                )
                            )
                        }
                        summary = "Random($picked)"
                    }
                }

                ActionType.CONDITION,
                ActionType.COUNTER,
                ActionType.THRESHOLD,
                ActionType.SEQUENCE,
                ActionType.PRIORITY,
                ActionType.QUEUE -> {
                    summary = "${step.actionType.displayName}(${resolvedParam.ifBlank { "Aktif" }})"
                }

                // ---------------- SENTUH LAYAR MLBB NYATA + INTERACTION TEST ----------------
                ActionType.SIMULATED_CLICK -> {
                    if (isMlbbMatchActive) {
                        sink.onRecordStepDetail(
                            rule,
                            event,
                            DispatchStatus.BLOCKED_MATCH_SAFE,
                            "Tap Layar dijeda sementara (Mode Jeda aktif)"
                        )
                        summary = "Tap ML(Dijeda)"
                    } else {
                        val (px, py) = parseNormalizedCoords(
                            resolvedParam,
                            fallbackX = step.posX ?: rule.overlayPosX,
                            fallbackY = step.posY ?: rule.overlayPosY
                        )
                        val holdMs = resolvedSecondary.filter { it.isDigit() }.toLongOrNull()
                            ?.coerceIn(0L, 12_000L) ?: step.durationMs.coerceIn(0L, 12_000L)
                        val modeLabel = when {
                            step.holdAndDrag && holdMs > 0L ->
                                "Tahan ${holdMs}ms → Geser ${step.dragDirection.uppercase()}"
                            step.holdAndDrag ->
                                "Geser ${step.dragDirection.uppercase()}"
                            holdMs <= 0L -> "Klik Cepat"
                            else -> "Tahan ${holdMs}ms"
                        }
                        sink.onInteractionTestSimulated(
                            InteractionTestSimEvent(
                                actionType = step.actionType,
                                detail = "$modeLabel (${(px * 100).toInt()}%, ${(py * 100).toInt()}%)",
                                posX = px,
                                posY = py,
                                colorHex = rule.accentColorHex
                            )
                        )
                        val svc = MlbbGestureAccessibilityService.peekInstance()
                        if (svc != null) {
                            val (outcome, tries) = dispatchWithTouchRetry(
                                startEpoch = startEpoch,
                                allowRetry = true,
                                enableLockOnConflict = enableTouchLock && !isMlbbMatchActive
                            ) {
                                svc.dispatchHoldAndDragOutcome(
                                    normX = px,
                                    normY = py,
                                    holdMs = holdMs,
                                    holdAndDrag = step.holdAndDrag,
                                    dragSpec = step.dragDirection,
                                    dragDurationMs = step.dragDurationMs
                                )
                            }
                            summary = when {
                                outcome == GestureOutcome.COMPLETED && tries > 1 ->
                                    "$modeLabel (${(px * 100).toInt()}%, ${(py * 100).toInt()}% • retry ${tries - 1}x)"
                                outcome == GestureOutcome.COMPLETED ->
                                    "$modeLabel (${(px * 100).toInt()}%, ${(py * 100).toInt()}%)"
                                outcome == GestureOutcome.CANCELLED_BY_TOUCH ->
                                    "$modeLabel dibatalkan sentuhan aktif (layar dikunci)"
                                else ->
                                    "$modeLabel gagal di (${(px * 100).toInt()}%, ${(py * 100).toInt()}%)"
                            }
                        } else {
                            sink.onRecordStepDetail(
                                rule,
                                event,
                                DispatchStatus.SKIPPED_CONDITION,
                                "Aksesibilitas belum aktif — tap (${(px * 100).toInt()}%, ${(py * 100).toInt()}%) tidak tersentuh"
                            )
                            summary = "⚠️ Aksesibilitas OFF"
                        }
                    }
                }

                ActionType.SIMULATED_SWIPE -> {
                    if (isMlbbMatchActive) {
                        sink.onRecordStepDetail(
                            rule,
                            event,
                            DispatchStatus.BLOCKED_MATCH_SAFE,
                            "Swipe Layar dijeda sementara (Mode Jeda aktif)"
                        )
                        summary = "Swipe ML(Dijeda)"
                    } else {
                        val startX = (step.posX ?: rule.overlayPosX).coerceIn(0.02f, 0.98f)
                        val startY = (step.posY ?: rule.overlayPosY).coerceIn(0.02f, 0.98f)
                        val swipeSpec = resolvedParam.ifBlank { "RIGHT" }
                        val durMs = resolvedSecondary.filter { it.isDigit() }.toLongOrNull()
                            ?.coerceIn(60L, 5000L) ?: step.durationMs.coerceIn(60L, 5000L)
                        sink.onInteractionTestSimulated(
                            InteractionTestSimEvent(
                                actionType = step.actionType,
                                detail = "Swipe ${swipeSpec.uppercase()}",
                                posX = startX,
                                posY = startY,
                                colorHex = rule.accentColorHex
                            )
                        )
                        val svc = MlbbGestureAccessibilityService.peekInstance()
                        if (svc != null) {
                            val (outcome, tries) = dispatchWithTouchRetry(
                                startEpoch = startEpoch,
                                allowRetry = true,
                                enableLockOnConflict = enableTouchLock && !isMlbbMatchActive
                            ) {
                                svc.dispatchSwipeOutcome(startX, startY, swipeSpec, durationMs = durMs)
                            }
                            summary = when {
                                outcome == GestureOutcome.COMPLETED && tries > 1 ->
                                    "Swipe ML (${swipeSpec.uppercase()} • retry ${tries - 1}x)"
                                outcome == GestureOutcome.COMPLETED ->
                                    "Swipe ML (${swipeSpec.uppercase()})"
                                outcome == GestureOutcome.CANCELLED_BY_TOUCH ->
                                    "Swipe ML dibatalkan sentuhan aktif (layar dikunci)"
                                else ->
                                    "Swipe ML gagal"
                            }
                        } else {
                            sink.onRecordStepDetail(
                                rule,
                                event,
                                DispatchStatus.SKIPPED_CONDITION,
                                "Aksesibilitas belum aktif — swipe tidak tersentuh"
                            )
                            summary = "⚠️ Aksesibilitas OFF"
                        }
                    }
                }

                ActionType.HOME_BACK_GAME -> {
                    if (isMlbbMatchActive) {
                        sink.onRecordStepDetail(
                            rule,
                            event,
                            DispatchStatus.BLOCKED_MATCH_SAFE,
                            "Home → Game dijeda sementara (Mode Jeda aktif)"
                        )
                        summary = "Home → Game (Dijeda)"
                    } else {
                        val homeDelayMs = resolvedParam.toLongOrNull()?.coerceIn(0L, 30_000L)
                            ?: step.durationMs.coerceIn(0L, 30_000L)
                        val targetPkg = resolvedSecondary.trim()
                        val svc = MlbbGestureAccessibilityService.peekInstance()
                        if (svc != null) {
                            summary = hardwareTouchMutex.withLock {
                                val wentHome = svc.goHome()
                                if (homeDelayMs > 0L) {
                                    delay(homeDelayMs)
                                }
                                val returned = if (!isAborted(startEpoch)) {
                                    svc.bringGameToFront(context, targetPkg)
                                } else {
                                    false
                                }
                                when {
                                    isAborted(startEpoch) -> "Home → Back (Batal)"
                                    wentHome && returned -> "Home ${homeDelayMs}ms → ${targetPkg.ifBlank { "Balik APK" }}"
                                    wentHome -> "Home ${homeDelayMs}ms (APK tujuan belum ditemukan)"
                                    else -> "Gagal ke Home"
                                }
                            }
                        } else {
                            sink.onRecordStepDetail(
                                rule,
                                event,
                                DispatchStatus.SKIPPED_CONDITION,
                                "Aksesibilitas OFF — Home → Game tidak dijalankan"
                            )
                            summary = "⚠️ Aksesibilitas OFF"
                        }
                    }
                }

                ActionType.SIMULATED_HOME_NAVIGATION,
                ActionType.INTERNAL_MENU_ACTION -> {
                    val (px, py) = parseNormalizedCoords(
                        resolvedParam,
                        fallbackX = step.posX ?: rule.overlayPosX,
                        fallbackY = step.posY ?: rule.overlayPosY
                    )
                    val detailText = when (step.actionType) {
                        ActionType.SIMULATED_HOME_NAVIGATION -> "Navigasi Beranda"
                        ActionType.INTERNAL_MENU_ACTION -> "Menu: ${resolvedParam.ifBlank { "Toggle" }}"
                        else -> step.actionType.displayName
                    }
                    sink.onInteractionTestSimulated(
                        InteractionTestSimEvent(
                            actionType = step.actionType,
                            detail = detailText,
                            posX = px,
                            posY = py
                        )
                    )
                    summary = detailText
                }
            }
        }
        } finally {
            if (acquiresTouchLock) {
                sink.ensureTouchLockSynchronized(false)
                sink.onTouchLockChanged(false)
            }
        }
        return summary
    }

    private suspend fun dispatchWithTouchRetry(
        startEpoch: Long,
        allowRetry: Boolean,
        enableLockOnConflict: Boolean,
        dispatchCall: suspend () -> GestureOutcome
    ): Pair<GestureOutcome, Int> = hardwareTouchMutex.withLock {
        var attempt = 0
        var conflictLockAcquired = false
        try {
            while (true) {
                if (isAborted(startEpoch)) {
                    return@withLock GestureOutcome.FAILED to attempt.coerceAtLeast(1)
                }
                attempt++
                val outcome = dispatchCall()
                if (outcome == GestureOutcome.COMPLETED) {
                    return@withLock GestureOutcome.COMPLETED to attempt
                }
                val canRetry = allowRetry && GestureRetryPolicy.shouldRetry(
                    outcome = outcome,
                    attemptIndex = attempt - 1,
                    aborted = isAborted(startEpoch)
                )
                if (!canRetry) {
                    return@withLock outcome to attempt
                }
                // Pastikan layar tetap terkunci penuh & cepat tangkap celah milidetik berikutnya untuk eksekusi konsep
                if (enableLockOnConflict && !conflictLockAcquired) {
                    sink.onTouchLockChanged(true)
                    sink.ensureTouchLockSynchronized(true)
                    conflictLockAcquired = true
                }
                sink.onTouchConflictChanged("🔒 Layar Terkunci • Mengambil alih kontrol (${attempt + 1})...")
                delay(GestureRetryPolicy.backoffDelayMs(attempt - 1))
            }
            @Suppress("UNREACHABLE_CODE")
            GestureOutcome.FAILED to attempt
        } finally {
            sink.onTouchConflictChanged("")
            if (conflictLockAcquired) {
                sink.ensureTouchLockSynchronized(false)
                sink.onTouchLockChanged(false)
            }
        }
    }

    private fun buildBeforeDelay(rule: UniversalTriggerRule): Long {
        return UniversalRuleEvaluator.effectiveBeforeDelayMs(rule)
    }

    private fun isAborted(startEpoch: Long): Boolean = emergencyEpoch.get() != startEpoch

    private fun resolveTemplate(
        raw: String,
        event: LiveInteractionEvent,
        counterCount: Int,
        rule: UniversalTriggerRule
    ): String {
        if (raw.isEmpty()) return ""
        return raw
            .replace("{user}", event.senderName)
            .replace("{qty}", event.quantity.toString())
            .replace("{gift}", event.giftName.ifBlank { "-" })
            .replace("{coins}", event.diamonds.toString())
            .replace("{count}", counterCount.toString())
            .replace("{source}", event.source.displayName)
            .replace("{text}", event.text)
            .replace("{rule}", rule.triggerName)
    }

    private fun isMediaUri(raw: String): Boolean {
        val clean = raw.trim().lowercase()
        return clean.startsWith("content://") ||
            clean.startsWith("file://") ||
            clean.startsWith("http://") ||
            clean.startsWith("https://")
    }

    private fun parseNormalizedCoords(
        raw: String,
        fallbackX: Float = 0.5f,
        fallbackY: Float = 0.5f
    ): Pair<Float, Float> {
        val parts = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
        val x = (parts.getOrNull(0) ?: fallbackX).coerceIn(0.02f, 0.98f)
        val y = (parts.getOrNull(1) ?: fallbackY).coerceIn(0.02f, 0.98f)
        return x to y
    }

    fun playConfiguredSound(spec: String) {
        val clean = spec.trim()
        if (clean.startsWith("content://") || clean.startsWith("file://")) {
            playAudioUri(clean)
            return
        }
        when (clean.uppercase()) {
            "ALERT", "SIREN" -> playSynthesizedTone(880.0, 220)
            "FANFARE", "WIN" -> playSynthesizedTone(659.25, 340)
            "BASS", "BOOM" -> playSynthesizedTone(140.0, 260)
            else -> playSynthesizedTone(587.33, 200)
        }
    }

    private fun playAudioUri(uriString: String) {
        audioScope.launch {
            synchronized(clipLock) {
                try {
                    customClipPlayer?.release()
                } catch (_: Throwable) {}
                val mp = MediaPlayer()
                customClipPlayer = mp
                try {
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    mp.setDataSource(context, Uri.parse(uriString))
                    val vol = actionVolume
                    mp.setVolume(vol, vol)
                    mp.setOnPreparedListener { it.start() }
                    mp.setOnCompletionListener {
                        synchronized(clipLock) {
                            try { it.release() } catch (_: Throwable) {}
                            if (customClipPlayer === it) customClipPlayer = null
                        }
                    }
                    mp.setOnErrorListener { player, _, _ ->
                        synchronized(clipLock) {
                            try { player.release() } catch (_: Throwable) {}
                            if (customClipPlayer === player) customClipPlayer = null
                        }
                        true
                    }
                    mp.prepareAsync()
                } catch (e: Throwable) {
                    Log.w(TAG, "Gagal putar audio custom: ${e.message}")
                    try { mp.release() } catch (_: Throwable) {}
                    if (customClipPlayer === mp) customClipPlayer = null
                }
            }
        }
    }

    private fun playSynthesizedTone(freqHz: Double, durationMs: Int) {
        audioScope.launch {
            try {
                val sampleCount = (SAMPLE_RATE * durationMs / 1000).coerceAtLeast(512)
                val pcm = ByteArray(sampleCount * 2)
                for (i in 0 until sampleCount) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = exp(-t * 6.0)
                    val sample = (sin(2.0 * PI * freqHz * t) * env * 0.45 * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    pcm[i * 2] = (sample and 0xFF).toByte()
                    pcm[i * 2 + 1] = ((sample ushr 8) and 0xFF).toByte()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                activeSoundTracks.add(track)
                track.write(pcm, 0, pcm.size)
                track.play()
                delay(durationMs.toLong() + 80L)
                activeSoundTracks.remove(track)
                try {
                    track.stop()
                    track.release()
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}
        }
    }
}
