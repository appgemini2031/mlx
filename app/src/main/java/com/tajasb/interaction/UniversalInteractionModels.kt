package com.tajasb.interaction

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class UniversalTriggerSource(
    val displayName: String,
    val isAvailableInRelay: Boolean,
    val availabilityNote: String
) {
    LIKE("Like", true, "Tersedia dari LiveEventReceiver (LIKE)"),
    FOLLOW("Follow", true, "Tersedia dari LiveEventReceiver (FOLLOW)"),
    SHARE("Share", true, "Tersedia dari LiveEventReceiver (SHARE)"),
    GIFT("Gift", true, "Tersedia dari LiveEventReceiver (GIFT)"),
    GIFT_QUANTITY("Gift Quantity", true, "Tersedia dari LiveEventReceiver (jumlah gift/koin)"),
    CHAT("Chat / Komentar", true, "Tersedia dari LiveEventReceiver (CHAT)"),
    MEMBER_JOIN("Penonton Join", true, "Tersedia dari LiveEventReceiver (MEMBER)"),
    SHOP_ORDER("Checkout Order", true, "Tersedia dari LiveEventReceiver (SHOP_ORDER)"),
    CUSTOM_EVENT("Custom Event", false, "Belum tersedia dari server relay; aktif untuk simulasi di Test Center")
}

enum class ActionCategory(val label: String) {
    VISUAL("Visual"),
    AUDIO("Audio"),
    TIMING("Timing"),
    LOGIC("Logic"),
    INTERACTION_TEST("Sentuh Layar ML")
}

enum class ActionType(
    val category: ActionCategory,
    val displayName: String,
    val defaultParamHint: String
) {
    // VISUAL
    SHOW_OVERLAY(ActionCategory.VISUAL, "Show Overlay", "Teks/judul overlay"),
    HIDE_OVERLAY(ActionCategory.VISUAL, "Hide Overlay", "Target overlay (ALL / teks)"),
    PROFILE_OVERLAY(ActionCategory.VISUAL, "Profile Overlay", "Tampilkan profil {user}"),
    TIER_OVERLAY(ActionCategory.VISUAL, "Tier Overlay", "Label tier (mis. VIP / Tier S)"),
    MEME_OVERLAY(ActionCategory.VISUAL, "Meme Overlay", "Nama/teks meme"),
    CUSTOM_IMAGE(ActionCategory.VISUAL, "Custom Image", "URI gambar kustom"),
    ANIMATION(ActionCategory.VISUAL, "Animation", "PULSE / BOUNCE / SHAKE / GLOW"),
    TEXT_OVERLAY(ActionCategory.VISUAL, "Text Overlay", "Contoh: {user} x{qty}"),
    CHANGE_SCENE(ActionCategory.VISUAL, "Change Scene", "Nama scene tujuan"),

    // AUDIO
    PLAY_SOUND(ActionCategory.AUDIO, "Play Sound", "CHIME / ALERT / FANFARE / URI audio"),
    STOP_SOUND(ActionCategory.AUDIO, "Stop Sound", "Hentikan suara aktif"),
    CUSTOM_BACKSOUND(ActionCategory.AUDIO, "Custom Backsound", "URI MP3/audio latar"),
    VOLUME_CONTROL(ActionCategory.AUDIO, "Volume Control", "Volume 0-100"),
    AUDIO_SEQUENCE(ActionCategory.AUDIO, "Audio Sequence", "Urutan nada/file dipisah koma"),

    // TIMING
    DELAY(ActionCategory.TIMING, "Delay", "Jeda (ms)"),
    WAIT(ActionCategory.TIMING, "Wait", "Tunggu (ms)"),
    DURATION(ActionCategory.TIMING, "Duration", "Durasi tahan (ms)"),
    REPEAT(ActionCategory.TIMING, "Repeat", "Jumlah ulangi"),
    COOLDOWN(ActionCategory.TIMING, "Cooldown", "Cooldown tambahan (ms)"),

    // LOGIC
    CONDITION(ActionCategory.LOGIC, "Condition", "Syarat lanjut sequence"),
    COUNTER(ActionCategory.LOGIC, "Counter", "Tambah/atur counter"),
    THRESHOLD(ActionCategory.LOGIC, "Threshold", "Target threshold"),
    RANDOM_SELECTION(ActionCategory.LOGIC, "Random Selection", "Pilihan dipisah '|'"),
    SEQUENCE(ActionCategory.LOGIC, "Sequence", "Penanda blok berurutan"),
    PRIORITY(ActionCategory.LOGIC, "Priority", "Prioritas antrean"),
    QUEUE(ActionCategory.LOGIC, "Queue", "Masuk antrean berurut"),

    // SENTUH LAYAR MLBB (Accessibility Gesture Nyata)
    SIMULATED_CLICK(ActionCategory.INTERACTION_TEST, "Klik Layar ML", "Koordinat X,Y (kosong = ikut Pin)"),
    SIMULATED_SWIPE(ActionCategory.INTERACTION_TEST, "Geser / Swipe ML", "Arah LEFT/RIGHT/UP/DOWN atau X2,Y2"),
    HOME_BACK_GAME(ActionCategory.INTERACTION_TEST, "Home → Delay → Back", "Jeda di Home (ms) & APK tujuan"),
    SIMULATED_HOME_NAVIGATION(ActionCategory.INTERACTION_TEST, "Simulated Home Navigation", "Simulasi navigasi beranda preview"),
    INTERNAL_MENU_ACTION(ActionCategory.INTERACTION_TEST, "Internal Menu Action", "Aksi menu internal aplikasi")
}

enum class ExecutionMode(val label: String) {
    SEQUENTIAL("Berurutan"),
    PARALLEL("Bersamaan")
}

enum class AccumulationMode(val label: String, val description: String) {
    INDIVIDUAL("Individual", "Setiap event langsung menjalankan action"),
    ACCUMULATION("Accumulation", "Mengumpulkan jumlah event ke counter"),
    THRESHOLD("Threshold", "Menjalankan action saat target tercapai"),
    COMBO("Combo", "Menggabungkan event dalam jendela waktu tertentu"),
    MILESTONE("Milestone", "Menjalankan action setiap kelipatan/target tertentu"),
    RANDOM("Random", "Memilih action acak dari daftar action rule"),
    CUSTOM("Custom", "Menggunakan evaluasi kondisi khusus buatan pengguna")
}

enum class ConditionField(val label: String) {
    QUANTITY("Quantity / Jumlah"),
    ACCUMULATED_COUNT("Counter Akumulasi"),
    COMBO_COUNT("Jumlah Combo"),
    DIAMONDS("Koin / Diamond"),
    GIFT_NAME("Nama Gift"),
    SENDER_NAME("Nama Pengirim"),
    CHAT_TEXT("Teks Pesan / Custom"),
    MATCH_NOT_ACTIVE("Di Luar Pertandingan MLBB")
}

enum class ConditionOperator(val label: String) {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER_OR_EQUAL(">="),
    LESS_OR_EQUAL("<="),
    CONTAINS("Mengandung"),
    MULTIPLE_OF("Kelipatan")
}

enum class ResetConditionType(val label: String) {
    NONE("Jangan Reset"),
    AFTER_TRIGGER("Reset Setelah Jalan"),
    ON_COUNT_REACHED("Reset Saat Target Tercapai"),
    AFTER_IDLE_TIMEOUT("Reset Setelah Jeda Diam"),
    MANUAL_ONLY("Reset Manual Saja")
}

enum class DispatchStatus(val label: String) {
    EXECUTED("Dieksekusi"),
    ACCUMULATED("Terkumpul"),
    QUEUED("Dalam Antrean"),
    SKIPPED_COOLDOWN("Ditahan Cooldown"),
    SKIPPED_DUPLICATE("Duplikat Diabaikan"),
    SKIPPED_CONDITION("Kondisi Tidak Terpenuhi"),
    BLOCKED_MATCH_SAFE("Sentuhan Dijeda"),
    STOPPED_EMERGENCY("Dihentikan Darurat")
}

data class TriggerCondition(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val field: ConditionField = ConditionField.QUANTITY,
    val operator: ConditionOperator = ConditionOperator.GREATER_OR_EQUAL,
    val value: String = "1"
)

data class UniversalActionStep(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val stepName: String = "",
    val actionType: ActionType = ActionType.TEXT_OVERLAY,
    val paramValue: String = "",
    val secondaryValue: String = "",
    val durationMs: Long = 2500L,
    val delayMs: Long = 0L,
    val repeatCount: Int = 1,
    val executionMode: ExecutionMode = ExecutionMode.SEQUENTIAL,
    val posX: Float? = null,
    val posY: Float? = null,
    val overlayScale: Float = 1.0f,
    val holdAndDrag: Boolean = false,
    val dragDirection: String = "RIGHT",
    val dragDurationMs: Long = 220L
)

object ChallengeColorPalette {
    val presets: List<Pair<String, Long>> = listOf(
        "Cyan" to 0xFF22D3EEL,
        "Hijau" to 0xFF10B981L,
        "Kuning" to 0xFFF59E0BL,
        "Pink" to 0xFFEC4899L,
        "Ungu" to 0xFFA855F7L,
        "Oranye" to 0xFFF97316L,
        "Lime" to 0xFF84CC16L,
        "Biru" to 0xFF38BDF8L,
        "Merah" to 0xFFF43F5EL,
        "Emas" to 0xFFEAB308L
    )

    fun colorForIndex(index: Int): Long {
        val safeIdx = ((index % presets.size) + presets.size) % presets.size
        return presets[safeIdx].second
    }

    fun nextUnusedColor(usedColors: Set<Long>): Long {
        return presets.firstOrNull { it.second !in usedColors }?.second
            ?: colorForIndex(usedColors.size)
    }
}

data class UniversalTriggerRule(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val isPinVisible: Boolean = true,
    val accentColorHex: Long = 0xFF22D3EEL,
    val triggerSource: UniversalTriggerSource = UniversalTriggerSource.LIKE,
    val triggerName: String = "Rule Baru",
    val targetGiftName: String = "",
    // Conditions + toggle
    val enableConditions: Boolean = false,
    val conditions: List<TriggerCondition> = emptyList(),
    // Minimum Quantity + toggle
    val enableMinimumQuantity: Boolean = false,
    val minimumQuantity: Int = 1,
    // Counter & Accumulation system (independent flags + mode)
    val accumulationMode: AccumulationMode = AccumulationMode.INDIVIDUAL,
    val enableIndividual: Boolean = true,
    val enableAccumulation: Boolean = false,
    val enableThreshold: Boolean = false,
    val thresholdValue: Int = 10,
    val enableCombo: Boolean = false,
    val comboWindowMs: Long = 5000L,
    val enableMilestone: Boolean = false,
    val milestoneStep: Int = 100,
    val enableRandomSelection: Boolean = false,
    val enableCustomCondition: Boolean = false,
    // Action Sequence
    val actionSequence: List<UniversalActionStep> = emptyList(),
    // Timing Parameters with independent ON/OFF switches (preserving values when OFF)
    val enableDelayBefore: Boolean = false,
    val delayBeforeMs: Long = 0L,
    val enableDelayAfter: Boolean = false,
    val delayAfterMs: Long = 0L,
    val enableInterval: Boolean = false,
    val intervalMs: Long = 300L,
    val enableDuration: Boolean = true,
    val durationMs: Long = 3000L,
    val enableRepeat: Boolean = false,
    val repeatCount: Int = 1,
    val enableCooldown: Boolean = false,
    val cooldownMs: Long = 1000L,
    val enablePriority: Boolean = false,
    val priority: Int = 5,
    val enableRandomDelay: Boolean = false,
    val randomDelayMinMs: Long = 0L,
    val randomDelayMaxMs: Long = 500L,
    val enableResetCondition: Boolean = false,
    val resetCondition: ResetConditionType = ResetConditionType.AFTER_TRIGGER,
    val resetTimeoutMs: Long = 30000L,
    val broadcastToVirtualCamera: Boolean = false,
    val assignedPresetId: String? = null,
    val overlayPosX: Float = 0.5f,
    val overlayPosY: Float = 0.5f,
    val overlayScale: Float = 1.0f,
    // Baca Cooldown Skill ML secara Visual per Pin / Rule
    val enableVisualCooldownWait: Boolean = false,
    val cooldownTargetStepId: String? = null,
    val cooldownAreaWidthRatio: Float = 0.085f,
    val cooldownAreaHeightRatio: Float = 0.14f,
    val cooldownAreaOffsetX: Float = 0f,
    val cooldownAreaOffsetY: Float = 0f,
    val cooldownMaxWaitMs: Long = 35000L
)

data class UniversalPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Preset Baru",
    val description: String = "",
    val enableConditions: Boolean = false,
    val conditions: List<TriggerCondition> = emptyList(),
    val accumulationMode: AccumulationMode = AccumulationMode.INDIVIDUAL,
    val thresholdValue: Int = 10,
    val comboWindowMs: Long = 5000L,
    val milestoneStep: Int = 100,
    val delayBeforeMs: Long = 0L,
    val delayAfterMs: Long = 0L,
    val intervalMs: Long = 300L,
    val durationMs: Long = 3000L,
    val repeatCount: Int = 1,
    val cooldownMs: Long = 1000L,
    val priority: Int = 5,
    val resetCondition: ResetConditionType = ResetConditionType.AFTER_TRIGGER,
    val actionSequence: List<UniversalActionStep> = emptyList()
)

data class LiveInteractionEvent(
    val id: String = UUID.randomUUID().toString(),
    val source: UniversalTriggerSource,
    val senderName: String = "Penonton",
    val quantity: Int = 1,
    val diamonds: Int = 0,
    val giftName: String = "",
    val text: String = "",
    val avatarUrl: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

data class TriggerCounterState(
    val ruleId: String,
    val accumulatedCount: Int = 0,
    val comboCount: Int = 0,
    val comboWindowStartMs: Long = 0L,
    val lastMilestoneReached: Int = 0,
    val lastEventAtMs: Long = 0L,
    val lastExecutedAtMs: Long = 0L
)

data class ActiveVisualOverlay(
    val id: String = UUID.randomUUID().toString(),
    val kind: ActionType,
    val title: String,
    val subtitle: String = "",
    val imageUri: String = "",
    val animationStyle: String = "PULSE",
    val expiresAtMs: Long = System.currentTimeMillis() + 3000L,
    val posX: Float = 0.5f,
    val posY: Float = 0.5f,
    val scale: Float = 1.0f
)

data class InteractionTestSimEvent(
    val id: String = UUID.randomUUID().toString(),
    val actionType: ActionType,
    val detail: String,
    val posX: Float = 0.5f,
    val posY: Float = 0.5f,
    val colorHex: Long = 0xFF10B981L,
    val timestampMs: Long = System.currentTimeMillis()
)

data class TriggerExecutionRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long,
    val ruleId: String,
    val ruleName: String,
    val source: UniversalTriggerSource,
    val senderName: String,
    val quantity: Int,
    val status: DispatchStatus,
    val detail: String
)

enum class FloatingPanelMode(val displayName: String) {
    FLOATING_CARD("Bebas"),
    DOCK_LEFT("Dock Kiri"),
    DOCK_RIGHT("Dock Kanan"),
    DOCK_TOP("Dock Atas"),
    DOCK_BOTTOM("Dock Bawah"),
    FULLSCREEN("Penuh")
}

data class FloatingWindowConfig(
    val hudXRatio: Float = 0.08f,
    val hudYRatio: Float = 0.14f,
    val hudScale: Float = 1.0f,
    val hudOpacity: Float = 0.90f,
    val hudAutoSnapEdge: Boolean = false,
    val hudCollapsed: Boolean = false,
    val panelMode: FloatingPanelMode = FloatingPanelMode.FLOATING_CARD,
    val panelXRatio: Float = 0.08f,
    val panelYRatio: Float = 0.12f,
    val panelWidthRatio: Float = 0.62f,
    val panelHeightRatio: Float = 0.72f,
    val panelOpacity: Float = 0.94f
)

data class GameCoverArea(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Tutup Area",
    val enabled: Boolean = true,
    val posX: Float = 0.14f,
    val posY: Float = 0.24f,
    val widthRatio: Float = 0.18f,
    val heightRatio: Float = 0.24f,
    val opacity: Float = 0.98f,
    val colorHex: Long = 0xFF0B1120L
)

data class UniversalEngineUiState(
    val isEngineEnabled: Boolean = true,
    val isMlbbMatchActive: Boolean = false,
    val blockTouchDuringChallenge: Boolean = true,
    val isChallengeExecuting: Boolean = false,
    val isSingleTestRunning: Boolean = false,
    val isDevice2SimpleMode: Boolean = true,
    val globalDelayBeforeMs: Long = 0L,
    val globalDelayAfterMs: Long = 0L,
    val globalDelayExcludeCooldownRules: Boolean = true,
    val gameCoverAreas: List<GameCoverArea> = emptyList(),
    val isCoverEditMode: Boolean = false,
    val activeCoverAreaId: String? = null,
    val cooldownStatusHint: String = "",
    val isEmergencyStopped: Boolean = false,
    val isFloatingOverlayRunning: Boolean = false,
    val isFloatingHiddenForLive: Boolean = false,
    val isPositionEditMode: Boolean = false,
    val activePinRuleId: String? = null,
    val activePinStepId: String? = null,
    val floatingConfig: FloatingWindowConfig = FloatingWindowConfig(),
    val activeSceneName: String = "SCENE_UTAMA",
    val rules: List<UniversalTriggerRule> = emptyList(),
    val presets: List<UniversalPreset> = emptyList(),
    val counters: Map<String, TriggerCounterState> = emptyMap(),
    val activeOverlays: List<ActiveVisualOverlay> = emptyList(),
    val lastSimEvent: InteractionTestSimEvent? = null,
    val queueSize: Int = 0,
    val history: List<TriggerExecutionRecord> = emptyList()
)

object UniversalInteractionJsonCodec {
    fun encodeRules(rules: List<UniversalTriggerRule>): String {
        val arr = JSONArray()
        rules.forEach { arr.put(ruleToJson(it)) }
        return arr.toString()
    }

    fun decodeRules(raw: String?): List<UniversalTriggerRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { jsonToRule(it, idx) }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun encodePresets(presets: List<UniversalPreset>): String {
        val arr = JSONArray()
        presets.forEach { arr.put(presetToJson(it)) }
        return arr.toString()
    }

    fun decodePresets(raw: String?): List<UniversalPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { jsonToPreset(it) }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun exportPresetPackageJson(preset: UniversalPreset): String {
        return JSONObject().apply {
            put("type", "UniversalInteractionPreset")
            put("schemaVersion", 1)
            put("preset", presetToJson(preset))
        }.toString(2)
    }

    fun exportAllPresetsJson(presets: List<UniversalPreset>): String {
        val arr = JSONArray()
        presets.forEach { arr.put(presetToJson(it)) }
        return JSONObject().apply {
            put("type", "UniversalInteractionPresetBundle")
            put("schemaVersion", 1)
            put("presets", arr)
        }.toString(2)
    }

    fun importPresetsFromJson(rawJson: String): Result<List<UniversalPreset>> {
        val trimmed = rawJson.removePrefix("\uFEFF").trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("JSON preset kosong."))
        return try {
            if (trimmed.startsWith("[")) {
                val list = decodePresets(trimmed)
                if (list.isEmpty()) Result.failure(IllegalArgumentException("Daftar preset kosong atau tidak valid."))
                else Result.success(list)
            } else {
                val obj = JSONObject(trimmed)
                when {
                    obj.has("preset") -> {
                        val p = jsonToPreset(obj.getJSONObject("preset"))
                        Result.success(listOf(p))
                    }
                    obj.has("presets") -> {
                        val arr = obj.getJSONArray("presets")
                        val list = (0 until arr.length()).mapNotNull { i ->
                            arr.optJSONObject(i)?.let { jsonToPreset(it) }
                        }
                        if (list.isEmpty()) Result.failure(IllegalArgumentException("Tidak ada preset valid dalam bundle."))
                        else Result.success(list)
                    }
                    obj.has("actionSequence") || obj.has("name") -> {
                        Result.success(listOf(jsonToPreset(obj)))
                    }
                    else -> Result.failure(IllegalArgumentException("Format JSON bukan preset Universal Interaction."))
                }
            }
        } catch (e: Throwable) {
            Result.failure(IllegalArgumentException("JSON preset tidak valid: ${e.message}"))
        }
    }

    fun ruleToJson(rule: UniversalTriggerRule): JSONObject = JSONObject().apply {
        put("id", rule.id)
        put("enabled", rule.enabled)
        put("isPinVisible", rule.isPinVisible)
        put("accentColorHex", rule.accentColorHex)
        put("triggerSource", rule.triggerSource.name)
        put("triggerName", rule.triggerName)
        put("targetGiftName", rule.targetGiftName)
        put("enableConditions", rule.enableConditions)
        put("conditions", JSONArray().apply { rule.conditions.forEach { put(conditionToJson(it)) } })
        put("enableMinimumQuantity", rule.enableMinimumQuantity)
        put("minimumQuantity", rule.minimumQuantity)
        put("accumulationMode", rule.accumulationMode.name)
        put("enableIndividual", rule.enableIndividual)
        put("enableAccumulation", rule.enableAccumulation)
        put("enableThreshold", rule.enableThreshold)
        put("thresholdValue", rule.thresholdValue)
        put("enableCombo", rule.enableCombo)
        put("comboWindowMs", rule.comboWindowMs)
        put("enableMilestone", rule.enableMilestone)
        put("milestoneStep", rule.milestoneStep)
        put("enableRandomSelection", rule.enableRandomSelection)
        put("enableCustomCondition", rule.enableCustomCondition)
        put("actionSequence", JSONArray().apply { rule.actionSequence.forEach { put(actionToJson(it)) } })
        put("enableDelayBefore", rule.enableDelayBefore)
        put("delayBeforeMs", rule.delayBeforeMs)
        put("enableDelayAfter", rule.enableDelayAfter)
        put("delayAfterMs", rule.delayAfterMs)
        put("enableInterval", rule.enableInterval)
        put("intervalMs", rule.intervalMs)
        put("enableDuration", rule.enableDuration)
        put("durationMs", rule.durationMs)
        put("enableRepeat", rule.enableRepeat)
        put("repeatCount", rule.repeatCount)
        put("enableCooldown", rule.enableCooldown)
        put("cooldownMs", rule.cooldownMs)
        put("enablePriority", rule.enablePriority)
        put("priority", rule.priority)
        put("enableRandomDelay", rule.enableRandomDelay)
        put("randomDelayMinMs", rule.randomDelayMinMs)
        put("randomDelayMaxMs", rule.randomDelayMaxMs)
        put("enableResetCondition", rule.enableResetCondition)
        put("resetCondition", rule.resetCondition.name)
        put("resetTimeoutMs", rule.resetTimeoutMs)
        put("broadcastToVirtualCamera", rule.broadcastToVirtualCamera)
        put("assignedPresetId", rule.assignedPresetId ?: "")
        put("overlayPosX", rule.overlayPosX.toDouble())
        put("overlayPosY", rule.overlayPosY.toDouble())
        put("overlayScale", rule.overlayScale.toDouble())
        put("enableVisualCooldownWait", rule.enableVisualCooldownWait)
        put("cooldownTargetStepId", rule.cooldownTargetStepId ?: "")
        put("cooldownAreaWidthRatio", rule.cooldownAreaWidthRatio.toDouble())
        put("cooldownAreaHeightRatio", rule.cooldownAreaHeightRatio.toDouble())
        put("cooldownAreaOffsetX", rule.cooldownAreaOffsetX.toDouble())
        put("cooldownAreaOffsetY", rule.cooldownAreaOffsetY.toDouble())
        put("cooldownMaxWaitMs", rule.cooldownMaxWaitMs)
    }

    fun jsonToRule(obj: JSONObject, indexHint: Int = 0): UniversalTriggerRule {
        val condArr = obj.optJSONArray("conditions")
        val conditions = if (condArr != null) {
            (0 until condArr.length()).mapNotNull { i -> condArr.optJSONObject(i)?.let { jsonToCondition(it) } }
        } else emptyList()
        val actArr = obj.optJSONArray("actionSequence")
        val actions = if (actArr != null) {
            (0 until actArr.length()).mapNotNull { i -> actArr.optJSONObject(i)?.let { jsonToAction(it) } }
        } else emptyList()

        return UniversalTriggerRule(
            id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
            enabled = obj.optBoolean("enabled", true),
            isPinVisible = obj.optBoolean("isPinVisible", true),
            accentColorHex = obj.optLong("accentColorHex", ChallengeColorPalette.colorForIndex(indexHint)),
            triggerSource = enumOrDefault(obj.optString("triggerSource"), UniversalTriggerSource.LIKE),
            triggerName = obj.optString("triggerName", "Rule Baru").ifBlank { "Rule Baru" },
            targetGiftName = obj.optString("targetGiftName", "").trim(),
            enableConditions = obj.optBoolean("enableConditions", false),
            conditions = conditions,
            enableMinimumQuantity = obj.optBoolean("enableMinimumQuantity", false),
            minimumQuantity = obj.optInt("minimumQuantity", 1).coerceAtLeast(1),
            accumulationMode = enumOrDefault(obj.optString("accumulationMode"), AccumulationMode.INDIVIDUAL),
            enableIndividual = obj.optBoolean("enableIndividual", true),
            enableAccumulation = obj.optBoolean("enableAccumulation", false),
            enableThreshold = obj.optBoolean("enableThreshold", false),
            thresholdValue = obj.optInt("thresholdValue", 10).coerceAtLeast(1),
            enableCombo = obj.optBoolean("enableCombo", false),
            comboWindowMs = obj.optLong("comboWindowMs", 5000L).coerceAtLeast(200L),
            enableMilestone = obj.optBoolean("enableMilestone", false),
            milestoneStep = obj.optInt("milestoneStep", 100).coerceAtLeast(1),
            enableRandomSelection = obj.optBoolean("enableRandomSelection", false),
            enableCustomCondition = obj.optBoolean("enableCustomCondition", false),
            actionSequence = actions,
            enableDelayBefore = obj.optBoolean("enableDelayBefore", false),
            delayBeforeMs = obj.optLong("delayBeforeMs", 0L).coerceAtLeast(0L),
            enableDelayAfter = obj.optBoolean("enableDelayAfter", false),
            delayAfterMs = obj.optLong("delayAfterMs", 0L).coerceAtLeast(0L),
            enableInterval = obj.optBoolean("enableInterval", false),
            intervalMs = obj.optLong("intervalMs", 300L).coerceAtLeast(0L),
            enableDuration = obj.optBoolean("enableDuration", true),
            durationMs = obj.optLong("durationMs", 3000L).coerceAtLeast(100L),
            enableRepeat = obj.optBoolean("enableRepeat", false),
            repeatCount = obj.optInt("repeatCount", 1).coerceIn(1, 50),
            enableCooldown = obj.optBoolean("enableCooldown", false),
            cooldownMs = obj.optLong("cooldownMs", 1000L).coerceAtLeast(0L),
            enablePriority = obj.optBoolean("enablePriority", false),
            priority = obj.optInt("priority", 5).coerceIn(1, 100),
            enableRandomDelay = obj.optBoolean("enableRandomDelay", false),
            randomDelayMinMs = obj.optLong("randomDelayMinMs", 0L).coerceAtLeast(0L),
            randomDelayMaxMs = obj.optLong("randomDelayMaxMs", 500L).coerceAtLeast(0L),
            enableResetCondition = obj.optBoolean("enableResetCondition", false),
            resetCondition = enumOrDefault(obj.optString("resetCondition"), ResetConditionType.AFTER_TRIGGER),
            resetTimeoutMs = obj.optLong("resetTimeoutMs", 30000L).coerceAtLeast(1000L),
            broadcastToVirtualCamera = obj.optBoolean("broadcastToVirtualCamera", false),
            assignedPresetId = obj.optString("assignedPresetId", "").ifBlank { null },
            overlayPosX = obj.optDouble("overlayPosX", 0.5).toFloat().coerceIn(0.02f, 0.98f),
            overlayPosY = obj.optDouble("overlayPosY", 0.5).toFloat().coerceIn(0.02f, 0.98f),
            overlayScale = obj.optDouble("overlayScale", 1.0).toFloat().coerceIn(0.35f, 3.0f),
            enableVisualCooldownWait = obj.optBoolean("enableVisualCooldownWait", false),
            cooldownTargetStepId = obj.optString("cooldownTargetStepId", "").ifBlank { null },
            cooldownAreaWidthRatio = obj.optDouble("cooldownAreaWidthRatio", 0.085).toFloat().coerceIn(0.025f, 0.45f),
            cooldownAreaHeightRatio = obj.optDouble("cooldownAreaHeightRatio", 0.14).toFloat().coerceIn(0.025f, 0.55f),
            cooldownAreaOffsetX = obj.optDouble("cooldownAreaOffsetX", 0.0).toFloat().coerceIn(-0.25f, 0.25f),
            cooldownAreaOffsetY = obj.optDouble("cooldownAreaOffsetY", 0.0).toFloat().coerceIn(-0.25f, 0.25f),
            cooldownMaxWaitMs = obj.optLong("cooldownMaxWaitMs", 35000L).coerceIn(2000L, 120000L)
        )
    }

    fun presetToJson(preset: UniversalPreset): JSONObject = JSONObject().apply {
        put("id", preset.id)
        put("name", preset.name)
        put("description", preset.description)
        put("enableConditions", preset.enableConditions)
        put("conditions", JSONArray().apply { preset.conditions.forEach { put(conditionToJson(it)) } })
        put("accumulationMode", preset.accumulationMode.name)
        put("thresholdValue", preset.thresholdValue)
        put("comboWindowMs", preset.comboWindowMs)
        put("milestoneStep", preset.milestoneStep)
        put("delayBeforeMs", preset.delayBeforeMs)
        put("delayAfterMs", preset.delayAfterMs)
        put("intervalMs", preset.intervalMs)
        put("durationMs", preset.durationMs)
        put("repeatCount", preset.repeatCount)
        put("cooldownMs", preset.cooldownMs)
        put("priority", preset.priority)
        put("resetCondition", preset.resetCondition.name)
        put("actionSequence", JSONArray().apply { preset.actionSequence.forEach { put(actionToJson(it)) } })
    }

    fun jsonToPreset(obj: JSONObject): UniversalPreset {
        val condArr = obj.optJSONArray("conditions")
        val conditions = if (condArr != null) {
            (0 until condArr.length()).mapNotNull { i -> condArr.optJSONObject(i)?.let { jsonToCondition(it) } }
        } else emptyList()
        val actArr = obj.optJSONArray("actionSequence")
        val actions = if (actArr != null) {
            (0 until actArr.length()).mapNotNull { i -> actArr.optJSONObject(i)?.let { jsonToAction(it) } }
        } else emptyList()
        return UniversalPreset(
            id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
            name = obj.optString("name", "Preset Tanpa Nama").ifBlank { "Preset Tanpa Nama" },
            description = obj.optString("description", ""),
            enableConditions = obj.optBoolean("enableConditions", false),
            conditions = conditions,
            accumulationMode = enumOrDefault(obj.optString("accumulationMode"), AccumulationMode.INDIVIDUAL),
            thresholdValue = obj.optInt("thresholdValue", 10).coerceAtLeast(1),
            comboWindowMs = obj.optLong("comboWindowMs", 5000L).coerceAtLeast(200L),
            milestoneStep = obj.optInt("milestoneStep", 100).coerceAtLeast(1),
            delayBeforeMs = obj.optLong("delayBeforeMs", 0L).coerceAtLeast(0L),
            delayAfterMs = obj.optLong("delayAfterMs", 0L).coerceAtLeast(0L),
            intervalMs = obj.optLong("intervalMs", 300L).coerceAtLeast(0L),
            durationMs = obj.optLong("durationMs", 3000L).coerceAtLeast(100L),
            repeatCount = obj.optInt("repeatCount", 1).coerceIn(1, 50),
            cooldownMs = obj.optLong("cooldownMs", 1000L).coerceAtLeast(0L),
            priority = obj.optInt("priority", 5).coerceIn(1, 100),
            resetCondition = enumOrDefault(obj.optString("resetCondition"), ResetConditionType.AFTER_TRIGGER),
            actionSequence = actions
        )
    }

    private fun conditionToJson(c: TriggerCondition): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("enabled", c.enabled)
        put("field", c.field.name)
        put("operator", c.operator.name)
        put("value", c.value)
    }

    private fun jsonToCondition(obj: JSONObject): TriggerCondition = TriggerCondition(
        id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
        enabled = obj.optBoolean("enabled", true),
        field = enumOrDefault(obj.optString("field"), ConditionField.QUANTITY),
        operator = enumOrDefault(obj.optString("operator"), ConditionOperator.GREATER_OR_EQUAL),
        value = obj.optString("value", "1")
    )

    private fun actionToJson(a: UniversalActionStep): JSONObject = JSONObject().apply {
        put("id", a.id)
        put("enabled", a.enabled)
        put("stepName", a.stepName)
        put("actionType", a.actionType.name)
        put("paramValue", a.paramValue)
        put("secondaryValue", a.secondaryValue)
        put("durationMs", a.durationMs)
        put("delayMs", a.delayMs)
        put("repeatCount", a.repeatCount)
        put("executionMode", a.executionMode.name)
        if (a.posX != null) put("posX", a.posX.toDouble())
        if (a.posY != null) put("posY", a.posY.toDouble())
        put("overlayScale", a.overlayScale.toDouble())
        put("holdAndDrag", a.holdAndDrag)
        put("dragDirection", a.dragDirection)
        put("dragDurationMs", a.dragDurationMs)
    }

    private fun jsonToAction(obj: JSONObject): UniversalActionStep {
        val type = enumOrDefault(obj.optString("actionType"), ActionType.TEXT_OVERLAY)
        val defaultDur = if (type == ActionType.SIMULATED_CLICK) 0L else 2500L
        val rawDur = obj.optLong("durationMs", defaultDur)
        val safeDur = if (type == ActionType.SIMULATED_CLICK) {
            rawDur.coerceIn(0L, 15000L)
        } else {
            rawDur.coerceAtLeast(0L)
        }
        return UniversalActionStep(
            id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
            enabled = obj.optBoolean("enabled", true),
            stepName = obj.optString("stepName", ""),
            actionType = type,
            paramValue = obj.optString("paramValue", ""),
            secondaryValue = obj.optString("secondaryValue", ""),
            durationMs = safeDur,
            delayMs = obj.optLong("delayMs", 0L).coerceAtLeast(0L),
            repeatCount = obj.optInt("repeatCount", 1).coerceIn(1, 30),
            executionMode = enumOrDefault(obj.optString("executionMode"), ExecutionMode.SEQUENTIAL),
            posX = if (obj.has("posX")) obj.optDouble("posX", 0.5).toFloat().coerceIn(0.02f, 0.98f) else null,
            posY = if (obj.has("posY")) obj.optDouble("posY", 0.5).toFloat().coerceIn(0.02f, 0.98f) else null,
            overlayScale = obj.optDouble("overlayScale", 1.0).toFloat().coerceIn(0.35f, 3.0f),
            holdAndDrag = obj.optBoolean("holdAndDrag", false),
            dragDirection = obj.optString("dragDirection", "RIGHT").ifBlank { "RIGHT" },
            dragDurationMs = obj.optLong("dragDurationMs", 220L).coerceIn(0L, 5000L)
        )
    }

    fun encodeGameCoverAreas(areas: List<GameCoverArea>): String {
        val arr = JSONArray()
        areas.forEach { arr.put(coverAreaToJson(it)) }
        return arr.toString()
    }

    fun decodeGameCoverAreas(raw: String?): List<GameCoverArea> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { jsonToCoverArea(it) }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun coverAreaToJson(a: GameCoverArea): JSONObject = JSONObject().apply {
        put("id", a.id)
        put("name", a.name)
        put("enabled", a.enabled)
        put("posX", a.posX.toDouble())
        put("posY", a.posY.toDouble())
        put("widthRatio", a.widthRatio.toDouble())
        put("heightRatio", a.heightRatio.toDouble())
        put("opacity", a.opacity.toDouble())
        put("colorHex", a.colorHex)
    }

    fun jsonToCoverArea(obj: JSONObject): GameCoverArea = GameCoverArea(
        id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
        name = obj.optString("name", "Tutup Area").ifBlank { "Tutup Area" },
        enabled = obj.optBoolean("enabled", true),
        posX = obj.optDouble("posX", 0.14).toFloat().coerceIn(0.01f, 0.99f),
        posY = obj.optDouble("posY", 0.24).toFloat().coerceIn(0.01f, 0.99f),
        widthRatio = obj.optDouble("widthRatio", 0.18).toFloat().coerceIn(0.03f, 0.96f),
        heightRatio = obj.optDouble("heightRatio", 0.24).toFloat().coerceIn(0.03f, 0.96f),
        opacity = obj.optDouble("opacity", 0.98).toFloat().coerceIn(0.20f, 1.0f),
        colorHex = obj.optLong("colorHex", 0xFF0B1120L)
    )

    fun encodeFloatingConfig(cfg: FloatingWindowConfig): String = JSONObject().apply {
        put("hudXRatio", cfg.hudXRatio.toDouble())
        put("hudYRatio", cfg.hudYRatio.toDouble())
        put("hudScale", cfg.hudScale.toDouble())
        put("hudOpacity", cfg.hudOpacity.toDouble())
        put("hudAutoSnapEdge", cfg.hudAutoSnapEdge)
        put("hudCollapsed", cfg.hudCollapsed)
        put("panelMode", cfg.panelMode.name)
        put("panelXRatio", cfg.panelXRatio.toDouble())
        put("panelYRatio", cfg.panelYRatio.toDouble())
        put("panelWidthRatio", cfg.panelWidthRatio.toDouble())
        put("panelHeightRatio", cfg.panelHeightRatio.toDouble())
        put("panelOpacity", cfg.panelOpacity.toDouble())
    }.toString()

    fun decodeFloatingConfig(raw: String?): FloatingWindowConfig {
        if (raw.isNullOrBlank()) return FloatingWindowConfig()
        return try {
            val obj = JSONObject(raw)
            FloatingWindowConfig(
                hudXRatio = obj.optDouble("hudXRatio", 0.08).toFloat().coerceIn(0f, 1f),
                hudYRatio = obj.optDouble("hudYRatio", 0.14).toFloat().coerceIn(0f, 1f),
                hudScale = obj.optDouble("hudScale", 1.0).toFloat().coerceIn(0.65f, 1.35f),
                hudOpacity = obj.optDouble("hudOpacity", 0.90).toFloat().coerceIn(0.30f, 1.0f),
                hudAutoSnapEdge = obj.optBoolean("hudAutoSnapEdge", false),
                hudCollapsed = obj.optBoolean("hudCollapsed", false),
                panelMode = enumOrDefault(obj.optString("panelMode"), FloatingPanelMode.FLOATING_CARD),
                panelXRatio = obj.optDouble("panelXRatio", 0.08).toFloat().coerceIn(0f, 1f),
                panelYRatio = obj.optDouble("panelYRatio", 0.12).toFloat().coerceIn(0f, 1f),
                panelWidthRatio = obj.optDouble("panelWidthRatio", 0.62).toFloat().coerceIn(0.38f, 0.98f),
                panelHeightRatio = obj.optDouble("panelHeightRatio", 0.72).toFloat().coerceIn(0.35f, 0.96f),
                panelOpacity = obj.optDouble("panelOpacity", 0.94).toFloat().coerceIn(0.45f, 1.0f)
            )
        } catch (_: Throwable) {
            FloatingWindowConfig()
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, defaultVal: T): T {
        if (raw.isNullOrBlank()) return defaultVal
        return try {
            enumValueOf<T>(raw.trim().uppercase())
        } catch (_: Throwable) {
            defaultVal
        }
    }
}
