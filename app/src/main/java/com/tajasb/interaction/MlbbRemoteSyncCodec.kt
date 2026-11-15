package com.tajasb.interaction

import org.json.JSONArray
import org.json.JSONObject

/**
 * Protokol sinkronisasi dua arah antara Device 2 (HP Gaming Android) dan
 * Device 1 (Server Termux + Web Controller Full Detail).
 */
sealed interface MlbbControlCommand {
    data class SetEngineEnabled(val enabled: Boolean) : MlbbControlCommand
    data class SetMatchSafePause(val paused: Boolean) : MlbbControlCommand
    data class SetTouchLock(val enabled: Boolean) : MlbbControlCommand
    data class SetGlobalDelays(
        val beforeMs: Long?,
        val afterMs: Long?,
        val excludeCooldownRules: Boolean?
    ) : MlbbControlCommand

    data class SetLiveDisplayMode(
        val floatingRunning: Boolean?,
        val hiddenForLive: Boolean?,
        val positionEditMode: Boolean?,
        val coverEditMode: Boolean?
    ) : MlbbControlCommand

    data class SetDevice2SimpleMode(val simple: Boolean) : MlbbControlCommand

    data class UpsertRuleDetail(
        val rule: UniversalTriggerRule,
        val preserveCoordinates: Boolean = false
    ) : MlbbControlCommand

    data class CreateRule(
        val name: String,
        val source: UniversalTriggerSource,
        val targetGiftName: String,
        val starterActionType: ActionType?
    ) : MlbbControlCommand

    data class ToggleRule(val ruleId: String, val enabled: Boolean?) : MlbbControlCommand
    data class ToggleRulePin(val ruleId: String, val visible: Boolean?) : MlbbControlCommand
    data class DuplicateRule(val ruleId: String) : MlbbControlCommand
    data class DeleteRule(val ruleId: String) : MlbbControlCommand
    data class MoveRule(val ruleId: String, val delta: Int) : MlbbControlCommand
    data class SelectActivePin(val ruleId: String?, val stepId: String?) : MlbbControlCommand
    data class SetAllPinsVisible(val visible: Boolean) : MlbbControlCommand

    data class UpsertCoverArea(
        val area: GameCoverArea,
        val preservePosition: Boolean = false
    ) : MlbbControlCommand

    data class CreateCoverArea(
        val name: String,
        val posX: Float,
        val posY: Float,
        val widthRatio: Float,
        val heightRatio: Float,
        val opacity: Float
    ) : MlbbControlCommand

    data class ToggleCoverArea(val areaId: String, val enabled: Boolean?) : MlbbControlCommand
    data class DeleteCoverArea(val areaId: String) : MlbbControlCommand
    data class SelectCoverArea(val areaId: String?) : MlbbControlCommand

    data class TestRule(val ruleId: String) : MlbbControlCommand
    data class InspectCooldown(val ruleId: String) : MlbbControlCommand
    data class InjectEvent(
        val source: UniversalTriggerSource,
        val senderName: String,
        val giftName: String,
        val quantity: Int,
        val diamonds: Int,
        val text: String
    ) : MlbbControlCommand

    data object EmergencyStop : MlbbControlCommand
    data object ResumeEngine : MlbbControlCommand
    data object ResetCounters : MlbbControlCommand
    data object ClearHistory : MlbbControlCommand
    data object RequestStateSync : MlbbControlCommand
}

object MlbbRemoteSyncCodec {

    fun encodeStateSyncPayload(
        state: UniversalEngineUiState,
        serverHost: String,
        serverPort: Int,
        activePlatform: String,
        giftCatalog: List<GiftCatalogEntry> = GiftCoinCatalog.entries
    ): JSONObject {
        return JSONObject().apply {
            put("protocolVersion", 1)
            put("syncedAtMs", System.currentTimeMillis())
            put("serverHost", serverHost)
            put("serverPort", serverPort)
            put("activePlatform", activePlatform)
            put("isEngineEnabled", state.isEngineEnabled)
            put("isMlbbMatchActive", state.isMlbbMatchActive)
            put("blockTouchDuringChallenge", state.blockTouchDuringChallenge)
            put("isChallengeExecuting", state.isChallengeExecuting)
            put("isSingleTestRunning", state.isSingleTestRunning)
            put("isEmergencyStopped", state.isEmergencyStopped)
            put("isDevice2SimpleMode", state.isDevice2SimpleMode)
            put("isFloatingOverlayRunning", state.isFloatingOverlayRunning)
            put("isFloatingHiddenForLive", state.isFloatingHiddenForLive)
            put("isPositionEditMode", state.isPositionEditMode)
            put("isCoverEditMode", state.isCoverEditMode)
            put("activePinRuleId", state.activePinRuleId ?: "")
            put("activePinStepId", state.activePinStepId ?: "")
            put("activeCoverAreaId", state.activeCoverAreaId ?: "")
            put("cooldownStatusHint", state.cooldownStatusHint)
            put("queueSize", state.queueSize)
            put("globalDelayBeforeMs", state.globalDelayBeforeMs)
            put("globalDelayAfterMs", state.globalDelayAfterMs)
            put("globalDelayExcludeCooldownRules", state.globalDelayExcludeCooldownRules)
            put("rules", JSONArray().apply {
                state.rules.forEach { put(UniversalInteractionJsonCodec.ruleToJson(it)) }
            })
            put("gameCoverAreas", JSONArray().apply {
                state.gameCoverAreas.forEach { put(UniversalInteractionJsonCodec.coverAreaToJson(it)) }
            })
            put("counters", JSONObject().apply {
                state.counters.forEach { (ruleId, c) ->
                    put(ruleId, JSONObject().apply {
                        put("accumulatedCount", c.accumulatedCount)
                        put("comboCount", c.comboCount)
                        put("lastExecutedAtMs", c.lastExecutedAtMs)
                        put("lastMilestoneReached", c.lastMilestoneReached)
                    })
                }
            })
            put("history", JSONArray().apply {
                state.history.take(15).forEach { rec ->
                    put(JSONObject().apply {
                        put("id", rec.id)
                        put("timestampMs", rec.timestampMs)
                        put("ruleId", rec.ruleId)
                        put("ruleName", rec.ruleName)
                        put("source", rec.source.name)
                        put("senderName", rec.senderName)
                        put("quantity", rec.quantity)
                        put("status", rec.status.name)
                        put("detail", rec.detail)
                    })
                }
            })
            put("giftCatalog", JSONArray().apply {
                giftCatalog.forEach { entry ->
                    put(JSONObject().apply {
                        put("name", entry.primaryName)
                        put("coinCost", entry.coinCost)
                        put("platform", entry.platform)
                        put("emoji", entry.emoji)
                        put("aliases", JSONArray(entry.aliases))
                    })
                }
            })
        }
    }

    fun parseControlCommand(raw: JSONObject): MlbbControlCommand? {
        val obj = if (raw.has("data") && raw.optJSONObject("data") != null && !raw.has("cmd")) {
            raw.getJSONObject("data")
        } else {
            raw
        }
        val cmd = obj.optString("cmd", "").trim().lowercase()
        if (cmd.isEmpty()) return null
        return when (cmd) {
            "set_engine_enabled" -> {
                MlbbControlCommand.SetEngineEnabled(obj.optBoolean("enabled", true))
            }
            "set_match_pause" -> {
                MlbbControlCommand.SetMatchSafePause(obj.optBoolean("paused", false))
            }
            "set_touch_lock" -> {
                MlbbControlCommand.SetTouchLock(obj.optBoolean("enabled", true))
            }
            "set_global_delays" -> {
                MlbbControlCommand.SetGlobalDelays(
                    beforeMs = if (obj.has("beforeMs")) obj.optLong("beforeMs", 0L).coerceIn(0L, 60_000L) else null,
                    afterMs = if (obj.has("afterMs")) obj.optLong("afterMs", 0L).coerceIn(0L, 60_000L) else null,
                    excludeCooldownRules = if (obj.has("excludeCooldownRules")) obj.optBoolean("excludeCooldownRules", true) else null
                )
            }
            "set_live_display_mode" -> {
                MlbbControlCommand.SetLiveDisplayMode(
                    floatingRunning = if (obj.has("floatingRunning")) obj.optBoolean("floatingRunning") else null,
                    hiddenForLive = if (obj.has("hiddenForLive")) obj.optBoolean("hiddenForLive") else null,
                    positionEditMode = if (obj.has("positionEditMode")) obj.optBoolean("positionEditMode") else null,
                    coverEditMode = if (obj.has("coverEditMode")) obj.optBoolean("coverEditMode") else null
                )
            }
            "set_device2_simple_mode" -> {
                MlbbControlCommand.SetDevice2SimpleMode(obj.optBoolean("simple", true))
            }
            "upsert_rule_detail" -> {
                val ruleObj = obj.optJSONObject("rule") ?: return null
                val parsed = UniversalInteractionJsonCodec.jsonToRule(ruleObj)
                MlbbControlCommand.UpsertRuleDetail(
                    rule = parsed,
                    preserveCoordinates = obj.optBoolean("preserveCoordinates", false)
                )
            }
            "create_rule" -> {
                val src = parseSource(obj.optString("source", "GIFT"))
                val actionStr = obj.optString("starterActionType", "").trim()
                val starter = if (actionStr.isNotEmpty()) {
                    try { ActionType.valueOf(actionStr.uppercase()) } catch (_: Throwable) { null }
                } else {
                    null
                }
                MlbbControlCommand.CreateRule(
                    name = obj.optString("name", "").trim(),
                    source = src,
                    targetGiftName = obj.optString("targetGiftName", "").trim(),
                    starterActionType = starter
                )
            }
            "toggle_rule" -> {
                val id = obj.optString("ruleId", "").trim()
                if (id.isEmpty()) return null
                MlbbControlCommand.ToggleRule(
                    ruleId = id,
                    enabled = if (obj.has("enabled")) obj.optBoolean("enabled") else null
                )
            }
            "toggle_rule_pin" -> {
                val id = obj.optString("ruleId", "").trim()
                if (id.isEmpty()) return null
                MlbbControlCommand.ToggleRulePin(
                    ruleId = id,
                    visible = if (obj.has("visible")) obj.optBoolean("visible") else null
                )
            }
            "duplicate_rule" -> {
                val id = obj.optString("ruleId", "").trim()
                if (id.isEmpty()) return null
                MlbbControlCommand.DuplicateRule(id)
            }
            "delete_rule" -> {
                val id = obj.optString("ruleId", "").trim()
                if (id.isEmpty()) return null
                MlbbControlCommand.DeleteRule(id)
            }
            "move_rule" -> {
                val id = obj.optString("ruleId", "").trim()
                if (id.isEmpty()) return null
                MlbbControlCommand.MoveRule(id, obj.optInt("delta", 0))
            }
            "select_active_pin" -> {
                MlbbControlCommand.SelectActivePin(
                    ruleId = obj.optString("ruleId", "").ifBlank { null },
                    stepId = obj.optString("stepId", "").ifBlank { null }
                )
            }
            "set_all_pins_visible" -> {
                MlbbControlCommand.SetAllPinsVisible(obj.optBoolean("visible", true))
            }
            "upsert_cover_area" -> {
                val areaObj = obj.optJSONObject("area") ?: return null
                MlbbControlCommand.UpsertCoverArea(
                    area = UniversalInteractionJsonCodec.jsonToCoverArea(areaObj),
                    preservePosition = obj.optBoolean("preservePosition", false)
                )
            }
            "create_cover_area" -> {
                MlbbControlCommand.CreateCoverArea(
                    name = obj.optString("name", "Penutup").ifBlank { "Penutup" },
                    posX = obj.optDouble("posX", 0.5).toFloat().coerceIn(0.01f, 0.99f),
                    posY = obj.optDouble("posY", 0.5).toFloat().coerceIn(0.01f, 0.99f),
                    widthRatio = obj.optDouble("widthRatio", 0.20).toFloat().coerceIn(0.03f, 0.96f),
                    heightRatio = obj.optDouble("heightRatio", 0.22).toFloat().coerceIn(0.03f, 0.96f),
                    opacity = obj.optDouble("opacity", 0.98).toFloat().coerceIn(0.20f, 1.0f)
                )
            }
            "toggle_cover_area" -> {
                val id = obj.optString("areaId", "").trim()
                if (id.isEmpty()) return null
                MlbbControlCommand.ToggleCoverArea(
                    areaId = id,
                    enabled = if (obj.has("enabled")) obj.optBoolean("enabled") else null
                )
            }
            "delete_cover_area" -> {
                val id = obj.optString("areaId", "").trim()
                if (id.isEmpty()) return null
                MlbbControlCommand.DeleteCoverArea(id)
            }
            "select_cover_area" -> {
                MlbbControlCommand.SelectCoverArea(obj.optString("areaId", "").ifBlank { null })
            }
            "test_rule" -> {
                val id = obj.optString("ruleId", "").trim()
                if (id.isEmpty()) return null
                MlbbControlCommand.TestRule(id)
            }
            "inspect_cooldown" -> {
                val id = obj.optString("ruleId", "").trim()
                if (id.isEmpty()) return null
                MlbbControlCommand.InspectCooldown(id)
            }
            "inject_event" -> {
                val src = parseSource(obj.optString("source", "GIFT"))
                val gName = obj.optString("giftName", "Mawar").ifBlank { "Mawar" }
                val rawDiamonds = obj.optInt("diamonds", 0)
                val resolvedDiamonds = if (rawDiamonds > 0) {
                    rawDiamonds
                } else {
                    GiftCoinCatalog.resolveCoinCost(gName) ?: 1
                }
                MlbbControlCommand.InjectEvent(
                    source = src,
                    senderName = obj.optString("senderName", "ControllerWeb").ifBlank { "ControllerWeb" },
                    giftName = gName,
                    quantity = obj.optInt("quantity", 1).coerceAtLeast(1),
                    diamonds = resolvedDiamonds,
                    text = obj.optString("text", "Remote Event Device 1")
                )
            }
            "emergency_stop" -> MlbbControlCommand.EmergencyStop
            "resume_engine" -> MlbbControlCommand.ResumeEngine
            "reset_counters" -> MlbbControlCommand.ResetCounters
            "clear_history" -> MlbbControlCommand.ClearHistory
            "request_sync" -> MlbbControlCommand.RequestStateSync
            else -> null
        }
    }

    private fun parseSource(raw: String): UniversalTriggerSource {
        if (raw.isBlank()) return UniversalTriggerSource.GIFT
        return try {
            UniversalTriggerSource.valueOf(raw.trim().uppercase())
        } catch (_: Throwable) {
            UniversalTriggerSource.GIFT
        }
    }
}
