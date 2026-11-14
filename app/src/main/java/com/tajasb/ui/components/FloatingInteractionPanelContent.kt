package com.tajasb.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonPurple
import com.tajasb.eventrelay.ConnectionStatus
import com.tajasb.eventrelay.LiveEventPreferences
import com.tajasb.eventrelay.LiveEventReceiver
import com.tajasb.interaction.ActionType
import com.tajasb.interaction.ChallengeColorPalette
import com.tajasb.interaction.DispatchStatus
import com.tajasb.interaction.ExecutionMode
import com.tajasb.interaction.FloatingPickerBridge
import com.tajasb.interaction.FloatingPickerMode
import com.tajasb.interaction.FloatingPickerProxyActivity
import com.tajasb.interaction.FullAppBackupManager
import com.tajasb.interaction.InstalledAppInfo
import com.tajasb.interaction.InstalledAppSearcher
import com.tajasb.interaction.LiveInteractionEvent
import com.tajasb.interaction.MlbbGestureAccessibilityService
import com.tajasb.interaction.TriggerExecutionRecord
import com.tajasb.interaction.UniversalActionStep
import com.tajasb.interaction.UniversalTriggerEngine
import com.tajasb.interaction.UniversalTriggerRule
import com.tajasb.interaction.UniversalTriggerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private enum class FloatingDropdownSection {
    CONCEPTS,
    TEST_TRIGGER,
    LIVE_SETTINGS
}

/**
 * Sub-menu Dropdown Minimalis di bawah Menu Horizontal Floating:
 * 1) Konsep & Koreografi Dinamis (default kosong; tambah bebas Klik / Geser / Jeda / Home->Game / Overlay / Sound)
 * 2) Tes Trigger & Log
 * 3) Pengaturan Termux, Izin & Live Bersih
 */
@Composable
fun FloatingInteractionPanelContent(
    selectedRuleIdToEdit: String? = null,
    onClearSelectedRuleToEdit: () -> Unit = {},
    onCloseSubMenu: () -> Unit = {},
    onEnterPositionDragMode: () -> Unit,
    onHideFloatingForLive: () -> Unit = {},
    onResetHudPosition: () -> Unit = {},
    onKeyboardFocusChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val engine = remember { UniversalTriggerEngine.getInstance(context) }
    val uiState by engine.state.collectAsState()
    val a11yConnected by MlbbGestureAccessibilityService.isConnected.collectAsState()
    val isA11yEnabled = a11yConnected || MlbbGestureAccessibilityService.isAccessibilityServiceEnabled(context)

    var openSection by remember { mutableStateOf(FloatingDropdownSection.CONCEPTS) }
    var editingRuleId by remember(selectedRuleIdToEdit) { mutableStateOf(selectedRuleIdToEdit) }

    LaunchedEffect(selectedRuleIdToEdit) {
        if (selectedRuleIdToEdit != null) {
            openSection = FloatingDropdownSection.CONCEPTS
            editingRuleId = selectedRuleIdToEdit
        }
    }

    val pickerResult by FloatingPickerBridge.result.collectAsState()
    LaunchedEffect(pickerResult) {
        if (pickerResult != null) {
            FloatingPickerBridge.consumeResult()
        }
    }

    val activeAccent = when (openSection) {
        FloatingDropdownSection.CONCEPTS -> NeonCyan
        FloatingDropdownSection.TEST_TRIGGER -> EmeraldGreen
        FloatingDropdownSection.LIVE_SETTINGS -> NeonOrange
    }

    val dynamicMaxHeightDp = (190f + uiState.floatingConfig.panelHeightRatio.coerceIn(0.35f, 0.96f) * 210f).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = dynamicMaxHeightDp)
            .padding(top = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (!isA11yEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonOrange.copy(alpha = 0.18f))
                    .border(0.8.dp, NeonOrange, RoundedCornerShape(8.dp))
                    .clickable { MlbbGestureAccessibilityService.openAccessibilitySettings(context) }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "⚠️ Aksesibilitas OFF — tap & Home belum aktif",
                    color = NeonOrange,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Aktifkan →",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // MENU HORIZONTAL: 3 Tab Utama Sejajar (Konsep | Tes & Log | Termux & Live)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HorizontalSectionTab(
                label = "🎯 Konsep (${uiState.rules.size})",
                selected = openSection == FloatingDropdownSection.CONCEPTS,
                accent = NeonCyan,
                modifier = Modifier.weight(1f),
                onClick = { openSection = FloatingDropdownSection.CONCEPTS }
            )
            HorizontalSectionTab(
                label = "🧪 Tes & Log",
                selected = openSection == FloatingDropdownSection.TEST_TRIGGER,
                accent = EmeraldGreen,
                modifier = Modifier.weight(1f),
                onClick = {
                    onKeyboardFocusChanged(false)
                    openSection = FloatingDropdownSection.TEST_TRIGGER
                }
            )
            HorizontalSectionTab(
                label = "📡 Termux/Live",
                selected = openSection == FloatingDropdownSection.LIVE_SETTINGS,
                accent = NeonOrange,
                modifier = Modifier.weight(1f),
                onClick = {
                    onKeyboardFocusChanged(false)
                    openSection = FloatingDropdownSection.LIVE_SETTINGS
                }
            )
        }

        // ISI SUB-MENU DROPDOWN AKTIF
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(0.8.dp, activeAccent.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState())
                .background(DarkBackground.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (openSection) {
                FloatingDropdownSection.CONCEPTS -> {
                    val activeEditRule = editingRuleId?.let { id -> uiState.rules.firstOrNull { it.id == id } }
                    if (activeEditRule != null) {
                        DropdownConceptEditor(
                            rule = activeEditRule,
                            activePinStepId = uiState.activePinStepId,
                            isNewEmpty = activeEditRule.actionSequence.isEmpty(),
                            onSelectStepPin = { stepId -> engine.selectActivePin(activeEditRule.id, stepId) },
                            onSaveDraftOnly = { updated -> engine.addOrUpdateRule(updated) },
                            onSave = { updated ->
                                engine.addOrUpdateRule(updated)
                                editingRuleId = null
                                onClearSelectedRuleToEdit()
                                onKeyboardFocusChanged(false)
                                Toast.makeText(context, "✅ ${updated.triggerName} disimpan", Toast.LENGTH_SHORT).show()
                            },
                            onCancel = {
                                editingRuleId = null
                                onClearSelectedRuleToEdit()
                                onKeyboardFocusChanged(false)
                            },
                            onDragExternalStepPin = { updated, stepId ->
                                engine.addOrUpdateRule(updated.copy(isPinVisible = true))
                                engine.selectActivePin(updated.id, stepId)
                                editingRuleId = null
                                onClearSelectedRuleToEdit()
                                onKeyboardFocusChanged(false)
                                onEnterPositionDragMode()
                            },
                            onTestNow = { updated ->
                                engine.addOrUpdateRule(updated)
                                engine.forceTestRuleNow(updated.id) { res ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onInspectCooldown = { updated, callback ->
                                engine.addOrUpdateRule(updated)
                                engine.inspectRuleCooldownOnce(updated.id) { res ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        callback(res)
                                    }
                                }
                            },
                            onPreviewSound = { spec -> engine.previewSoundSpec(spec) },
                            onKeyboardFocusChanged = onKeyboardFocusChanged
                        )
                    } else {
                        DropdownConceptList(
                            rules = uiState.rules,
                            activePinRuleId = uiState.activePinRuleId,
                            onCreateEmptyConcept = {
                                val created = engine.createEmptyConcept(UniversalTriggerSource.GIFT)
                                editingRuleId = created.id
                            },
                            onToggleRule = { engine.toggleRuleEnabled(it.id) },
                            onTogglePinEye = { engine.toggleRulePinVisibility(it.id) },
                            onSetAllEyes = { visible -> engine.setAllPinsVisible(visible) },
                            onSelectActivePin = {
                                engine.selectActivePin(it.id)
                                if (!it.isPinVisible) engine.toggleRulePinVisibility(it.id)
                                onEnterPositionDragMode()
                            },
                            onEditRule = {
                                engine.selectActivePin(it.id)
                                editingRuleId = it.id
                            },
                            onTestRule = { rule ->
                                engine.forceTestRuleNow(rule.id) { res ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDeleteRule = {
                                if (editingRuleId == it.id) editingRuleId = null
                                engine.deleteRule(it.id)
                            },
                            onShowPinsOutside = onEnterPositionDragMode
                        )
                    }
                }

                FloatingDropdownSection.TEST_TRIGGER -> {
                    DropdownTestSection(
                        engine = engine,
                        rules = uiState.rules,
                        isEngineEnabled = uiState.isEngineEnabled && !uiState.isEmergencyStopped,
                        isA11yEnabled = isA11yEnabled,
                        history = uiState.history
                    )
                }

                FloatingDropdownSection.LIVE_SETTINGS -> {
                    DropdownLiveSettingsSection(
                        engine = engine,
                        isA11yEnabled = isA11yEnabled,
                        onHideFloatingForLive = onHideFloatingForLive,
                        onResetHudPosition = onResetHudPosition,
                        onKeyboardFocusChanged = onKeyboardFocusChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalSectionTab(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent.copy(alpha = 0.2f) else DarkSurface)
            .border(
                width = if (selected) 1.dp else 0.7.dp,
                color = if (selected) accent else DarkCardBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color(0xFFE2E8F0),
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DropdownConceptList(
    rules: List<UniversalTriggerRule>,
    activePinRuleId: String?,
    onCreateEmptyConcept: () -> Unit,
    onToggleRule: (UniversalTriggerRule) -> Unit,
    onTogglePinEye: (UniversalTriggerRule) -> Unit,
    onSetAllEyes: (Boolean) -> Unit,
    onSelectActivePin: (UniversalTriggerRule) -> Unit,
    onEditRule: (UniversalTriggerRule) -> Unit,
    onTestRule: (UniversalTriggerRule) -> Unit,
    onDeleteRule: (UniversalTriggerRule) -> Unit,
    onShowPinsOutside: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Button(
                onClick = onCreateEmptyConcept,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("+ Konsep Baru", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            OutlinedButton(
                onClick = onShowPinsOutside,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("📍 D-Pad", color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (rules.isNotEmpty()) {
            val visibleCount = rules.count { it.isPinVisible }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pin Tampil: $visibleCount/${rules.size}",
                    color = Color(0xFF94A3B8),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectPill("👁 Buka Semua", visibleCount == rules.size) { onSetAllEyes(true) }
                    SelectPill("🙈 Tutup Semua", visibleCount == 0) { onSetAllEyes(false) }
                }
            }
        }

        if (rules.isEmpty()) {
            Text(
                text = "Belum ada konsep. Ketuk '+ Konsep Baru' lalu tambahkan koreografi sesuai kebutuhan (Klik, Geser, Home→Game, Jeda, Overlay, atau Sound).",
                color = Color(0xFF94A3B8),
                fontSize = 9.sp
            )
        } else {
            rules.forEachIndexed { idx, rule ->
                val ruleColor = Color(rule.accentColorHex)
                val isSelected = rule.id == activePinRuleId
                val activeSteps = rule.actionSequence.filter { it.enabled }
                val stepSummary = when {
                    activeSteps.isEmpty() -> "Kosong"
                    activeSteps.size == 1 -> shortStepLabel(activeSteps.first())
                    else -> "${activeSteps.size} Koreografi: " + activeSteps.take(3).joinToString(" → ") { shortStepLabel(it) }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isSelected) ruleColor.copy(alpha = 0.12f) else DarkSurface)
                        .border(
                            if (isSelected) 1.2.dp else 0.8.dp,
                            if (rule.isPinVisible) ruleColor.copy(alpha = 0.8f) else DarkCardBorder,
                            RoundedCornerShape(7.dp)
                        )
                        .clickable { onEditRule(rule) }
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(ruleColor)
                            .clickable { onSelectActivePin(rule) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${idx + 1}",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${rule.triggerSource.displayName} → ${rule.triggerName}",
                            color = if (rule.enabled) Color.White else Color(0xFF64748B),
                            fontSize = 9.8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stepSummary + if (!rule.isPinVisible) " • Pin Tutup" else "",
                            color = if (rule.isPinVisible) Color(0xFF94A3B8) else NeonOrange,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (rule.isPinVisible) ruleColor.copy(alpha = 0.20f) else Color(0xFF334155))
                            .border(0.7.dp, if (rule.isPinVisible) ruleColor else Color(0xFF475569), RoundedCornerShape(6.dp))
                            .clickable { onTogglePinEye(rule) }
                            .padding(horizontal = 5.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (rule.isPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (rule.isPinVisible) "Tutup Mata Pin" else "Buka Mata Pin",
                            tint = if (rule.isPinVisible) ruleColor else Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    ActionPillButton(if (rule.enabled) "ON" else "OFF", if (rule.enabled) EmeraldGreen else Color(0xFF64748B)) {
                        onToggleRule(rule)
                    }
                    ActionPillButton("▶", EmeraldGreen) { onTestRule(rule) }
                    ActionPillButton("✎", NeonCyan) { onEditRule(rule) }
                    ConfirmDeleteActionPill(text = "✕") { onDeleteRule(rule) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DropdownConceptEditor(
    rule: UniversalTriggerRule,
    activePinStepId: String?,
    isNewEmpty: Boolean,
    onSelectStepPin: (String?) -> Unit,
    onSaveDraftOnly: (UniversalTriggerRule) -> Unit,
    onSave: (UniversalTriggerRule) -> Unit,
    onCancel: () -> Unit,
    onDragExternalStepPin: (UniversalTriggerRule, String?) -> Unit,
    onTestNow: (UniversalTriggerRule) -> Unit,
    onInspectCooldown: (UniversalTriggerRule, (String) -> Unit) -> Unit,
    onPreviewSound: (String) -> Unit,
    onKeyboardFocusChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var draftName by remember(rule.id) { mutableStateOf(rule.triggerName) }
    var draftSource by remember(rule.id) { mutableStateOf(rule.triggerSource) }
    var draftColorHex by remember(rule.id, rule.accentColorHex) { mutableStateOf(rule.accentColorHex) }
    var draftPinVisible by remember(rule.id, rule.isPinVisible) { mutableStateOf(rule.isPinVisible) }
    var cooldownText by remember(rule.id) { mutableStateOf(if (rule.enableCooldown) rule.cooldownMs.toString() else "0") }
    var minQtyText by remember(rule.id) { mutableStateOf(if (rule.enableMinimumQuantity) rule.minimumQuantity.toString() else "1") }
    var fineNudge by remember { mutableStateOf(true) }

    var draftEnableVisualCooldown by remember(rule.id) { mutableStateOf(rule.enableVisualCooldownWait) }
    var draftCooldownWidthRatio by remember(rule.id) { mutableStateOf(rule.cooldownAreaWidthRatio.coerceIn(0.025f, 0.45f)) }
    var draftCooldownHeightRatio by remember(rule.id) { mutableStateOf(rule.cooldownAreaHeightRatio.coerceIn(0.025f, 0.55f)) }
    var draftCooldownOffsetX by remember(rule.id) { mutableStateOf(rule.cooldownAreaOffsetX.coerceIn(-0.30f, 0.30f)) }
    var draftCooldownOffsetY by remember(rule.id) { mutableStateOf(rule.cooldownAreaOffsetY.coerceIn(-0.30f, 0.30f)) }
    var draftCooldownMaxWaitText by remember(rule.id) { mutableStateOf(rule.cooldownMaxWaitMs.toString()) }
    var showCooldownScanSection by remember(rule.id) { mutableStateOf(rule.enableVisualCooldownWait) }
    var cooldownScanTestMsg by remember(rule.id) { mutableStateOf<String?>(null) }

    var draftSteps by remember(rule.id) {
        mutableStateOf(rule.actionSequence)
    }

    // Sinkronkan perubahan pin/media eksternal (misal geser pin di layar atau hasil photo/audio picker)
    LaunchedEffect(rule.actionSequence) {
        val byId = rule.actionSequence.associateBy { it.id }
        draftSteps = draftSteps.map { local ->
            val remote = byId[local.id]
            if (remote != null) {
                local.copy(
                    posX = remote.posX ?: local.posX,
                    posY = remote.posY ?: local.posY,
                    paramValue = if (
                        local.actionType == ActionType.SIMULATED_CLICK ||
                        local.actionType == ActionType.CUSTOM_IMAGE ||
                        local.actionType == ActionType.PLAY_SOUND ||
                        local.actionType == ActionType.CUSTOM_BACKSOUND
                    ) {
                        remote.paramValue
                    } else {
                        local.paramValue
                    }
                )
            } else {
                local
            }
        }
    }

    var expandedStepId by remember(rule.id) {
        mutableStateOf(activePinStepId ?: draftSteps.firstOrNull()?.id)
    }
    var showEventDrop by remember { mutableStateOf(false) }
    var showColorDrop by remember { mutableStateOf(false) }
    var showConditionDrop by remember { mutableStateOf(false) }
    var showAddMenu by remember(rule.id) { mutableStateOf(isNewEmpty) }

    val buildRuleWithSteps: (List<UniversalActionStep>) -> UniversalTriggerRule = { list ->
        val normalized = list.map { s ->
            val sx = (s.posX ?: rule.overlayPosX).coerceIn(0.01f, 0.99f)
            val sy = (s.posY ?: rule.overlayPosY).coerceIn(0.01f, 0.99f)
            when (s.actionType) {
                ActionType.SIMULATED_CLICK -> {
                    val hold = s.durationMs.coerceIn(0L, 12_000L)
                    val dragDur = s.dragDurationMs.coerceIn(60L, 5000L)
                    s.copy(
                        enabled = true,
                        stepName = s.stepName.trim(),
                        paramValue = String.format(Locale.US, "%.3f,%.3f", sx, sy),
                        secondaryValue = hold.toString(),
                        durationMs = hold,
                        holdAndDrag = s.holdAndDrag,
                        dragDirection = s.dragDirection.trim().ifBlank { "RIGHT" },
                        dragDurationMs = dragDur,
                        delayMs = s.delayMs.coerceIn(0L, 60_000L),
                        repeatCount = s.repeatCount.coerceIn(1, 20),
                        posX = sx,
                        posY = sy
                    )
                }
                ActionType.SIMULATED_SWIPE -> {
                    val dur = s.durationMs.coerceIn(60L, 5000L)
                    s.copy(
                        enabled = true,
                        stepName = s.stepName.trim(),
                        paramValue = s.paramValue.ifBlank { "RIGHT" },
                        secondaryValue = dur.toString(),
                        durationMs = dur,
                        delayMs = s.delayMs.coerceIn(0L, 60_000L),
                        repeatCount = s.repeatCount.coerceIn(1, 20),
                        posX = sx,
                        posY = sy
                    )
                }
                ActionType.DELAY -> {
                    val ms = s.durationMs.coerceIn(50L, 60_000L)
                    s.copy(
                        enabled = true,
                        stepName = s.stepName.trim(),
                        paramValue = ms.toString(),
                        durationMs = ms,
                        delayMs = 0L
                    )
                }
                ActionType.HOME_BACK_GAME -> {
                    val ms = s.durationMs.coerceIn(200L, 30_000L)
                    s.copy(
                        enabled = true,
                        stepName = s.stepName.trim(),
                        paramValue = ms.toString(),
                        secondaryValue = s.secondaryValue.trim(),
                        durationMs = ms,
                        delayMs = s.delayMs.coerceIn(0L, 60_000L)
                    )
                }
                else -> s.copy(
                    enabled = true,
                    stepName = s.stepName.trim(),
                    delayMs = s.delayMs.coerceIn(0L, 60_000L),
                    posX = sx,
                    posY = sy,
                    overlayScale = s.overlayScale.coerceIn(0.35f, 3.0f)
                )
            }
        }
        val firstPin = normalized.firstOrNull {
            it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE
        }
        val cd = cooldownText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val minQ = minQtyText.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val maxWait = draftCooldownMaxWaitText.toLongOrNull()?.coerceIn(1500L, 180_000L) ?: 35_000L
        rule.copy(
            enabled = true,
            isPinVisible = draftPinVisible,
            accentColorHex = draftColorHex,
            triggerName = draftName.trim().ifBlank { "Konsep" },
            triggerSource = draftSource,
            overlayPosX = firstPin?.posX ?: rule.overlayPosX,
            overlayPosY = firstPin?.posY ?: rule.overlayPosY,
            enableMinimumQuantity = minQ > 1,
            minimumQuantity = minQ,
            enableCooldown = cd > 0L,
            cooldownMs = cd,
            enableVisualCooldownWait = draftEnableVisualCooldown,
            cooldownAreaWidthRatio = draftCooldownWidthRatio.coerceIn(0.025f, 0.45f),
            cooldownAreaHeightRatio = draftCooldownHeightRatio.coerceIn(0.025f, 0.55f),
            cooldownAreaOffsetX = draftCooldownOffsetX.coerceIn(-0.30f, 0.30f),
            cooldownAreaOffsetY = draftCooldownOffsetY.coerceIn(-0.30f, 0.30f),
            cooldownMaxWaitMs = maxWait,
            actionSequence = normalized
        )
    }

    val buildRule: () -> UniversalTriggerRule = { buildRuleWithSteps(draftSteps) }
    val hasTouchPins = draftSteps.any {
        it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE
    }

    fun appendDynamicStep(type: ActionType) {
        val lastTouch = draftSteps.lastOrNull {
            it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE
        }
        val bx = lastTouch?.posX ?: rule.overlayPosX
        val by = lastTouch?.posY ?: rule.overlayPosY
        val nx = if (lastTouch == null) bx else ((bx + 0.06f).let { if (it > 0.90f) 0.25f else it }).coerceIn(0.05f, 0.95f)
        val ny = if (lastTouch == null) by else ((by - 0.06f).let { if (it < 0.10f) 0.80f else it }).coerceIn(0.05f, 0.95f)

        val newStep = when (type) {
            ActionType.SIMULATED_CLICK -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.SIMULATED_CLICK,
                paramValue = String.format(Locale.US, "%.3f,%.3f", nx, ny),
                secondaryValue = "70",
                durationMs = 70L,
                delayMs = 0L,
                posX = nx,
                posY = ny,
                executionMode = ExecutionMode.SEQUENTIAL
            )
            ActionType.SIMULATED_SWIPE -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.SIMULATED_SWIPE,
                paramValue = "RIGHT",
                secondaryValue = "220",
                durationMs = 220L,
                delayMs = 0L,
                posX = nx,
                posY = ny,
                executionMode = ExecutionMode.SEQUENTIAL
            )
            ActionType.DELAY -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.DELAY,
                paramValue = "500",
                durationMs = 500L,
                executionMode = ExecutionMode.SEQUENTIAL
            )
            ActionType.HOME_BACK_GAME -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.HOME_BACK_GAME,
                paramValue = "1500",
                secondaryValue = "",
                durationMs = 1500L,
                executionMode = ExecutionMode.SEQUENTIAL
            )
            ActionType.CUSTOM_IMAGE -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.CUSTOM_IMAGE,
                paramValue = "",
                secondaryValue = "",
                durationMs = 2500L,
                posX = bx,
                posY = by,
                executionMode = ExecutionMode.PARALLEL
            )
            ActionType.PLAY_SOUND -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.PLAY_SOUND,
                paramValue = "CHIME",
                executionMode = ExecutionMode.PARALLEL
            )
            else -> UniversalActionStep(actionType = type, executionMode = ExecutionMode.SEQUENTIAL)
        }

        val next = draftSteps + newStep
        draftSteps = next
        expandedStepId = newStep.id
        showAddMenu = false
        onSaveDraftOnly(buildRuleWithSteps(next))
        if (type == ActionType.SIMULATED_CLICK || type == ActionType.SIMULATED_SWIPE) {
            onSelectStepPin(newStep.id)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // Header aksi cepat konsep
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.clickable { showColorDrop = !showColorDrop }
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(draftColorHex))
                        .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                )
                Text("Warna ▾", color = Color(0xFF94A3B8), fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextChip(
                    label = if (draftPinVisible) "👁" else "🙈",
                    bg = if (draftPinVisible) Color(0xFF0E7490) else Color(0xFF475569),
                    fg = Color.White
                ) { draftPinVisible = !draftPinVisible }
                TextChip("▶ Tes", EmeraldGreen, Color.Black) { onTestNow(buildRule()) }
                if (hasTouchPins) {
                    TextChip("📍 Pin", NeonPurple, Color.White) {
                        val targetStep = draftSteps.firstOrNull {
                            it.id == expandedStepId && (it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE)
                        } ?: draftSteps.firstOrNull {
                            it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE
                        }
                        onDragExternalStepPin(buildRule(), targetStep?.id)
                    }
                }
            }
        }

        if (showColorDrop) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                ChallengeColorPalette.presets.forEach { (_, hex) ->
                    val selected = draftColorHex == hex
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(if (selected) 2.dp else 0.7.dp, if (selected) Color.White else Color.Black.copy(alpha = 0.4f), CircleShape)
                            .clickable {
                                draftColorHex = hex
                                showColorDrop = false
                            }
                    )
                }
            }
        }

        ComfortableInput(
            label = "Nama Konsep / Challenge",
            value = draftName,
            onValueChange = { draftName = it },
            onFocusChange = onKeyboardFocusChanged
        )

        // Dropdown ringkas Event Pemicu & Syarat (tertutup rapi saat tidak diklik)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SubDropdownHeader("Event: ${draftSource.displayName}", showEventDrop) {
                    showEventDrop = !showEventDrop
                    if (showEventDrop) showConditionDrop = false
                }
            }
            Box(modifier = Modifier.weight(0.9f)) {
                val condLabel = buildString {
                    val mq = minQtyText.toIntOrNull() ?: 1
                    val cd = cooldownText.toLongOrNull() ?: 0L
                    append(if (mq > 1 || cd > 0L) "Syarat: Aktif" else "Syarat: Bebas")
                }
                SubDropdownHeader(condLabel, showConditionDrop) {
                    showConditionDrop = !showConditionDrop
                    if (showConditionDrop) showEventDrop = false
                }
            }
        }

        if (showEventDrop) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    UniversalTriggerSource.LIKE,
                    UniversalTriggerSource.GIFT,
                    UniversalTriggerSource.GIFT_QUANTITY,
                    UniversalTriggerSource.CHAT,
                    UniversalTriggerSource.FOLLOW,
                    UniversalTriggerSource.SHARE,
                    UniversalTriggerSource.MEMBER_JOIN
                ).forEach { src ->
                    SelectPill(src.displayName, draftSource == src) {
                        draftSource = src
                        showEventDrop = false
                    }
                }
            }
        }

        if (showConditionDrop) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ComfortableInput(
                    label = "Min Qty",
                    value = minQtyText,
                    onValueChange = { minQtyText = it.filter(Char::isDigit) },
                    keyboardType = KeyboardType.Number,
                    onFocusChange = onKeyboardFocusChanged,
                    modifier = Modifier.weight(1f)
                )
                ComfortableInput(
                    label = "Cooldown(ms)",
                    value = cooldownText,
                    onValueChange = { cooldownText = it.filter(Char::isDigit) },
                    keyboardType = KeyboardType.Number,
                    onFocusChange = onKeyboardFocusChanged,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // DAFTAR KOREOGRAFI DINAMIS (DEFAULT KOSONG)
        Text(
            text = "Koreografi (${draftSteps.size} langkah):",
            color = Color(0xFFCBD5E1),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )

        if (draftSteps.isEmpty()) {
            Text(
                text = "Kosong — ketuk '+ Tambah Koreografi' untuk pilih Klik, Geser, Home→Game, Jeda, Overlay, atau Sound.",
                color = Color(0xFF94A3B8),
                fontSize = 8.5.sp
            )
        } else {
            draftSteps.forEachIndexed { idx, step ->
                val isStepExpanded = expandedStepId == step.id
                val stepColor = Color(draftColorHex)
                val baseTypeLabel = shortStepLabel(step)
                val headerText = step.stepName.trim().let {
                    if (it.isNotEmpty()) "$it [$baseTypeLabel]" else baseTypeLabel
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isStepExpanded) stepColor.copy(alpha = 0.08f) else DarkSurface)
                        .border(
                            if (isStepExpanded) 1.dp else 0.7.dp,
                            if (isStepExpanded) stepColor.copy(alpha = 0.85f) else DarkCardBorder,
                            RoundedCornerShape(7.dp)
                        )
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedStepId = if (isStepExpanded) null else step.id
                                if (step.actionType == ActionType.SIMULATED_CLICK || step.actionType == ActionType.SIMULATED_SWIPE) {
                                    onSelectStepPin(step.id)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(stepColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${idx + 1}", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                        Text(
                            text = headerText,
                            color = Color.White,
                            fontSize = 9.2.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (draftSteps.size > 1 && idx > 0) {
                            ActionPillButton("▲", NeonCyan) {
                                val next = draftSteps.toMutableList()
                                val m = next.removeAt(idx)
                                next.add(idx - 1, m)
                                draftSteps = next
                                onSaveDraftOnly(buildRuleWithSteps(next))
                            }
                        }
                        if (draftSteps.size > 1 && idx < draftSteps.lastIndex) {
                            ActionPillButton("▼", NeonCyan) {
                                val next = draftSteps.toMutableList()
                                val m = next.removeAt(idx)
                                next.add(idx + 1, m)
                                draftSteps = next
                                onSaveDraftOnly(buildRuleWithSteps(next))
                            }
                        }
                        ConfirmDeleteActionPill(text = "✕") {
                            val next = draftSteps.filterNot { it.id == step.id }
                            draftSteps = next
                            if (expandedStepId == step.id) expandedStepId = next.firstOrNull()?.id
                            onSaveDraftOnly(buildRuleWithSteps(next))
                        }
                    }

                    if (isStepExpanded) {
                        fun updateCurrentStep(transform: (UniversalActionStep) -> UniversalActionStep) {
                            draftSteps = draftSteps.map { if (it.id == step.id) transform(it) else it }
                        }

                        ComfortableInput(
                            label = "Nama Langkah (opsional, mis. Skill 1 / Ulti)",
                            value = step.stepName,
                            onValueChange = { raw -> updateCurrentStep { it.copy(stepName = raw) } },
                            onFocusChange = onKeyboardFocusChanged
                        )

                        when (step.actionType) {
                            ActionType.SIMULATED_CLICK, ActionType.SIMULATED_SWIPE -> {
                                val sx = (step.posX ?: rule.overlayPosX).coerceIn(0.01f, 0.99f)
                                val sy = (step.posY ?: rule.overlayPosY).coerceIn(0.01f, 0.99f)

                                if (step.actionType == ActionType.SIMULATED_SWIPE) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            "LEFT" to "⬅ Kiri",
                                            "RIGHT" to "➡ Kanan",
                                            "UP" to "⬆ Atas",
                                            "DOWN" to "⬇ Bawah",
                                            "RANDOM" to "🎲 Random"
                                        ).forEach { (dir, label) ->
                                            SelectPill(label, step.paramValue.equals(dir, ignoreCase = true)) {
                                                updateCurrentStep { it.copy(paramValue = dir) }
                                            }
                                        }
                                    }
                                    ComfortableInput(
                                        label = "Arah Custom (opsional: 45° atau X,Y mis. 0.80,0.40)",
                                        value = step.paramValue,
                                        onValueChange = { raw -> updateCurrentStep { it.copy(paramValue = raw) } },
                                        onFocusChange = onKeyboardFocusChanged
                                    )
                                }

                                var durationDraft by remember(step.id, step.durationMs) {
                                    mutableStateOf(step.durationMs.toString())
                                }
                                var repeatDraft by remember(step.id, step.repeatCount) {
                                    mutableStateOf(step.repeatCount.toString())
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ComfortableInput(
                                        label = if (step.actionType == ActionType.SIMULATED_SWIPE) "Durasi(ms)" else "Tahan(ms, 0=Klik)",
                                        value = durationDraft,
                                        onValueChange = { raw ->
                                            val digits = raw.filter(Char::isDigit).take(5)
                                            durationDraft = digits
                                            digits.toLongOrNull()?.let { v ->
                                                val c = if (step.actionType == ActionType.SIMULATED_CLICK) {
                                                    v.coerceIn(0L, 12_000L)
                                                } else {
                                                    v.coerceIn(60L, 5000L)
                                                }
                                                updateCurrentStep {
                                                    it.copy(durationMs = c, secondaryValue = c.toString())
                                                }
                                            }
                                        },
                                        keyboardType = KeyboardType.Number,
                                        onFocusChange = onKeyboardFocusChanged,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ComfortableInput(
                                        label = "Ulang(x)",
                                        value = repeatDraft,
                                        onValueChange = { raw ->
                                            val digits = raw.filter(Char::isDigit).take(2)
                                            repeatDraft = digits
                                            digits.toIntOrNull()?.let { v ->
                                                updateCurrentStep { it.copy(repeatCount = v.coerceIn(1, 20)) }
                                            }
                                        },
                                        keyboardType = KeyboardType.Number,
                                        onFocusChange = onKeyboardFocusChanged,
                                        modifier = Modifier.weight(0.75f)
                                    )
                                }

                                if (step.actionType == ActionType.SIMULATED_CLICK) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        SelectPill("0ms (Klik)", step.durationMs == 0L) {
                                            durationDraft = "0"
                                            updateCurrentStep { it.copy(durationMs = 0L, secondaryValue = "0") }
                                        }
                                        SelectPill("400ms", step.durationMs == 400L) {
                                            durationDraft = "400"
                                            updateCurrentStep { it.copy(durationMs = 400L, secondaryValue = "400") }
                                        }
                                        SelectPill("1000ms", step.durationMs == 1000L) {
                                            durationDraft = "1000"
                                            updateCurrentStep { it.copy(durationMs = 1000L, secondaryValue = "1000") }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkBackground.copy(alpha = 0.65f))
                                            .padding(horizontal = 7.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "✊ Tahan + Geser → Lepas",
                                                color = if (step.holdAndDrag) NeonCyan else Color(0xFFCBD5E1),
                                                fontSize = 8.8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Tahan (${step.durationMs}ms), lalu geser arah pilihan tanpa melepas jari.",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 7.4.sp
                                            )
                                        }
                                        Switch(
                                            checked = step.holdAndDrag,
                                            onCheckedChange = { checked ->
                                                updateCurrentStep { it.copy(holdAndDrag = checked) }
                                            },
                                            colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan)
                                        )
                                    }

                                    if (step.holdAndDrag) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf(
                                                "LEFT" to "⬅ Kiri",
                                                "RIGHT" to "➡ Kanan",
                                                "UP" to "⬆ Atas",
                                                "DOWN" to "⬇ Bawah",
                                                "RANDOM" to "🎲 Random"
                                            ).forEach { (dir, label) ->
                                                SelectPill(label, step.dragDirection.equals(dir, ignoreCase = true)) {
                                                    updateCurrentStep { it.copy(dragDirection = dir) }
                                                }
                                            }
                                        }
                                        var dragDurDraft by remember(step.id, step.dragDurationMs) {
                                            mutableStateOf(step.dragDurationMs.toString())
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            ComfortableInput(
                                                label = "Arah Geser (atau 45° / X,Y)",
                                                value = step.dragDirection,
                                                onValueChange = { raw -> updateCurrentStep { it.copy(dragDirection = raw) } },
                                                onFocusChange = onKeyboardFocusChanged,
                                                modifier = Modifier.weight(1.25f)
                                            )
                                            ComfortableInput(
                                                label = "Durasi Geser(ms)",
                                                value = dragDurDraft,
                                                onValueChange = { raw ->
                                                    val digits = raw.filter(Char::isDigit).take(4)
                                                    dragDurDraft = digits
                                                    digits.toLongOrNull()?.let { v ->
                                                        updateCurrentStep { it.copy(dragDurationMs = v.coerceIn(60L, 5000L)) }
                                                    }
                                                },
                                                keyboardType = KeyboardType.Number,
                                                onFocusChange = onKeyboardFocusChanged,
                                                modifier = Modifier.weight(0.85f)
                                            )
                                        }
                                    }
                                }

                                val nudgeDelta = if (fineNudge) 0.0025f else 0.015f
                                fun applyNudge(dx: Float, dy: Float) {
                                    val nx = (sx + dx).coerceIn(0.01f, 0.99f)
                                    val ny = (sy + dy).coerceIn(0.01f, 0.99f)
                                    val nextList = draftSteps.map {
                                        if (it.id == step.id) {
                                            it.copy(
                                                posX = nx,
                                                posY = ny,
                                                paramValue = if (it.actionType == ActionType.SIMULATED_CLICK) {
                                                    String.format(Locale.US, "%.3f,%.3f", nx, ny)
                                                } else {
                                                    it.paramValue
                                                }
                                            )
                                        } else it
                                    }
                                    draftSteps = nextList
                                    onSelectStepPin(step.id)
                                    onSaveDraftOnly(buildRuleWithSteps(nextList))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextChip("◀", Color(0xFF1E293B), Color.White) { applyNudge(-nudgeDelta, 0f) }
                                    TextChip("▲", Color(0xFF1E293B), Color.White) { applyNudge(0f, -nudgeDelta) }
                                    TextChip("▼", Color(0xFF1E293B), Color.White) { applyNudge(0f, +nudgeDelta) }
                                    TextChip("▶", Color(0xFF1E293B), Color.White) { applyNudge(+nudgeDelta, 0f) }
                                    SelectPill(if (fineNudge) "Halus" else "Cepat", fineNudge) { fineNudge = !fineNudge }
                                    TextChip("📍", NeonPurple, Color.White) {
                                        onDragExternalStepPin(buildRule(), step.id)
                                    }
                                }
                            }

                            ActionType.DELAY, ActionType.WAIT -> {
                                ComfortableInput(
                                    label = "Durasi Jeda (ms)",
                                    value = step.durationMs.toString(),
                                    onValueChange = { raw ->
                                        val v = raw.filter(Char::isDigit).toLongOrNull() ?: 500L
                                        val c = v.coerceIn(50L, 60_000L)
                                        updateCurrentStep { it.copy(durationMs = c, paramValue = c.toString()) }
                                    },
                                    keyboardType = KeyboardType.Number,
                                    onFocusChange = onKeyboardFocusChanged
                                )
                            }

                            ActionType.HOME_BACK_GAME -> {
                                var appQuery by remember(step.id) { mutableStateOf("") }
                                var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
                                var showApkList by remember(step.id) { mutableStateOf(false) }

                                LaunchedEffect(step.id) {
                                    installedApps = withContext(Dispatchers.IO) {
                                        InstalledAppSearcher.loadLaunchableApps(context)
                                    }
                                }

                                val filteredApps = remember(installedApps, appQuery) {
                                    InstalledAppSearcher.filterApps(installedApps, appQuery, limit = 18)
                                }
                                val selectedPkg = step.secondaryValue.trim()
                                val selectedAppLabel = remember(selectedPkg, installedApps) {
                                    if (selectedPkg.isBlank()) {
                                        "Auto (Aplikasi Terakhir / MLBB)"
                                    } else {
                                        installedApps.firstOrNull { it.packageName == selectedPkg }?.appName
                                            ?: InstalledAppSearcher.resolveAppName(context, selectedPkg)
                                            ?: selectedPkg
                                    }
                                }

                                ComfortableInput(
                                    label = "Jeda di Home (ms)",
                                    value = step.durationMs.toString(),
                                    onValueChange = { raw ->
                                        val v = raw.filter(Char::isDigit).toLongOrNull() ?: 1500L
                                        val c = v.coerceIn(200L, 30_000L)
                                        updateCurrentStep { it.copy(durationMs = c, paramValue = c.toString()) }
                                    },
                                    keyboardType = KeyboardType.Number,
                                    onFocusChange = onKeyboardFocusChanged
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurface)
                                        .border(0.7.dp, NeonOrange.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                        .clickable { showApkList = !showApkList }
                                        .padding(horizontal = 7.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Tujuan Back: $selectedAppLabel",
                                            color = NeonOrange,
                                            fontSize = 8.8.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (selectedPkg.isNotBlank()) {
                                            Text(
                                                text = selectedPkg,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 7.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (selectedPkg.isNotBlank()) {
                                        TextChip("Auto", Color(0xFF334155), Color.White) {
                                            updateCurrentStep { it.copy(secondaryValue = "") }
                                            showApkList = false
                                        }
                                    } else {
                                        Text(
                                            text = if (showApkList) "Tutup ▲" else "Cari APK ▼",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ComfortableInput(
                                        label = "🔍 Cari APK / Nama Aplikasi tujuan back",
                                        value = appQuery,
                                        onValueChange = { q ->
                                            appQuery = q
                                            showApkList = true
                                        },
                                        onFocusChange = { focused ->
                                            onKeyboardFocusChanged(focused)
                                            if (focused) showApkList = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (appQuery.isNotBlank() && appQuery.contains(".")) {
                                        TextChip("Pakai", EmeraldGreen, Color.Black) {
                                            updateCurrentStep { it.copy(secondaryValue = appQuery.trim()) }
                                            appQuery = ""
                                            showApkList = false
                                            onKeyboardFocusChanged(false)
                                        }
                                    }
                                }

                                if (showApkList) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 120.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkBackground)
                                            .border(0.7.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                            .verticalScroll(rememberScrollState())
                                            .padding(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        if (filteredApps.isEmpty()) {
                                            Text(
                                                text = if (installedApps.isEmpty()) "Memuat daftar APK..." else "APK tidak ditemukan untuk \"$appQuery\"",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 8.5.sp,
                                                modifier = Modifier.padding(4.dp)
                                            )
                                        } else {
                                            filteredApps.forEach { app ->
                                                val isChosen = app.packageName == selectedPkg
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(if (isChosen) NeonOrange.copy(alpha = 0.2f) else DarkSurface)
                                                        .clickable {
                                                            updateCurrentStep { it.copy(secondaryValue = app.packageName) }
                                                            appQuery = ""
                                                            showApkList = false
                                                            onKeyboardFocusChanged(false)
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = app.appName,
                                                            color = if (isChosen) NeonOrange else Color.White,
                                                            fontSize = 8.8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = app.packageName,
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 7.3.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Text(
                                                        text = if (isChosen) "✓" else "Pilih",
                                                        color = if (isChosen) NeonOrange else EmeraldGreen,
                                                        fontSize = 8.2.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            ActionType.CUSTOM_IMAGE, ActionType.SHOW_OVERLAY, ActionType.TEXT_OVERLAY -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (step.paramValue.startsWith("file://") || step.paramValue.startsWith("content://")) {
                                        AsyncImage(
                                            model = step.paramValue,
                                            contentDescription = "Overlay Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).border(0.8.dp, NeonCyan, RoundedCornerShape(6.dp))
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            onSaveDraftOnly(buildRule())
                                            FloatingPickerBridge.launch(
                                                context = context,
                                                mode = FloatingPickerMode.PICK_IMAGE,
                                                requestKey = "${FloatingPickerProxyActivity.PREFIX_RULE_IMAGE}${rule.id}::${step.id}"
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (step.paramValue.isBlank()) "Upload Gambar" else "Ganti Gambar",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (step.paramValue.isNotBlank()) {
                                        ConfirmDeleteTextChip(label = "Hapus") {
                                            updateCurrentStep { it.copy(paramValue = "") }
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ComfortableInput(
                                        label = "Teks (opsional)",
                                        value = step.secondaryValue,
                                        onValueChange = { raw -> updateCurrentStep { it.copy(secondaryValue = raw) } },
                                        onFocusChange = onKeyboardFocusChanged,
                                        modifier = Modifier.weight(1.3f)
                                    )
                                    ComfortableInput(
                                        label = "Durasi(ms)",
                                        value = step.durationMs.toString(),
                                        onValueChange = { raw ->
                                            val v = raw.filter(Char::isDigit).toLongOrNull() ?: 2500L
                                            updateCurrentStep { it.copy(durationMs = v.coerceIn(300L, 30_000L)) }
                                        },
                                        keyboardType = KeyboardType.Number,
                                        onFocusChange = onKeyboardFocusChanged,
                                        modifier = Modifier.weight(0.7f)
                                    )
                                }
                                // Pengatur Ukuran Overlay Visual (40% .. 300%) + Posisi Cepat
                                val curScale = step.overlayScale.coerceIn(0.35f, 3.0f)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkBackground.copy(alpha = 0.7f))
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Ukuran Overlay: ${(curScale * 100).roundToInt()}%",
                                            color = NeonCyan,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            SelectPill("Kecil", curScale < 0.75f) {
                                                updateCurrentStep { it.copy(overlayScale = 0.55f) }
                                            }
                                            SelectPill("Sedang", curScale in 0.75f..1.3f) {
                                                updateCurrentStep { it.copy(overlayScale = 1.0f) }
                                            }
                                            SelectPill("Besar", curScale > 1.3f && curScale < 2.1f) {
                                                updateCurrentStep { it.copy(overlayScale = 1.65f) }
                                            }
                                            SelectPill("Jumbo", curScale >= 2.1f) {
                                                updateCurrentStep { it.copy(overlayScale = 2.4f) }
                                            }
                                        }
                                    }
                                    Slider(
                                        value = curScale,
                                        onValueChange = { next ->
                                            updateCurrentStep { it.copy(overlayScale = next.coerceIn(0.35f, 3.0f)) }
                                        },
                                        valueRange = 0.35f..3.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = NeonCyan,
                                            activeTrackColor = NeonCyan
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(22.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Posisi:", color = Color(0xFF94A3B8), fontSize = 8.sp)
                                        val curY = step.posY ?: rule.overlayPosY
                                        SelectPill("Atas", curY < 0.35f) {
                                            updateCurrentStep { it.copy(posX = 0.5f, posY = 0.22f) }
                                        }
                                        SelectPill("Tengah", curY in 0.35f..0.65f) {
                                            updateCurrentStep { it.copy(posX = 0.5f, posY = 0.50f) }
                                        }
                                        SelectPill("Bawah", curY > 0.65f) {
                                            updateCurrentStep { it.copy(posX = 0.5f, posY = 0.78f) }
                                        }
                                    }
                                }
                            }

                            ActionType.PLAY_SOUND, ActionType.CUSTOM_BACKSOUND -> {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("CHIME", "ALERT", "FANFARE", "BASS").forEach { preset ->
                                        SelectPill(preset, step.paramValue.equals(preset, ignoreCase = true)) {
                                            updateCurrentStep { it.copy(paramValue = preset) }
                                            onPreviewSound(preset)
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Button(
                                        onClick = {
                                            onSaveDraftOnly(buildRule())
                                            FloatingPickerBridge.launch(
                                                context = context,
                                                mode = FloatingPickerMode.PICK_AUDIO,
                                                requestKey = "${FloatingPickerProxyActivity.PREFIX_RULE_SOUND}${rule.id}::${step.id}"
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    ) {
                                        Icon(Icons.Default.AudioFile, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Upload MP3/SFX", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                    if (step.paramValue.isNotBlank()) {
                                        TextChip("🔊 Tes", EmeraldGreen, Color.Black) { onPreviewSound(step.paramValue) }
                                    }
                                }
                            }

                            else -> Unit
                        }

                        // Opsi urutan eksekusi langkah (Berurutan vs Bersamaan)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SelectPill("Berurutan", step.executionMode == ExecutionMode.SEQUENTIAL) {
                                updateCurrentStep { it.copy(executionMode = ExecutionMode.SEQUENTIAL) }
                            }
                            SelectPill("Bersamaan", step.executionMode == ExecutionMode.PARALLEL) {
                                updateCurrentStep { it.copy(executionMode = ExecutionMode.PARALLEL) }
                            }
                        }
                    }
                }
            }
        }

        // Dropdown + Tambah Koreografi Dinamis
        OutlinedButton(
            onClick = { showAddMenu = !showAddMenu },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth().height(34.dp)
        ) {
            Text(
                text = if (showAddMenu) "✕ Tutup Pilihan Koreografi" else "+ Tambah Koreografi (Klik / Geser / Home→Game / Lainnya)",
                color = EmeraldGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showAddMenu) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextChip("👆 Klik ML", EmeraldGreen, Color.Black) { appendDynamicStep(ActionType.SIMULATED_CLICK) }
                TextChip("👉 Geser ML", NeonCyan, Color.Black) { appendDynamicStep(ActionType.SIMULATED_SWIPE) }
                TextChip("⏱️ Jeda", Color(0xFF334155), Color.White) { appendDynamicStep(ActionType.DELAY) }
                TextChip("🏠 Home→Game", NeonOrange, Color.Black) { appendDynamicStep(ActionType.HOME_BACK_GAME) }
                TextChip("🖼️ Overlay", NeonPurple, Color.White) { appendDynamicStep(ActionType.CUSTOM_IMAGE) }
                TextChip("🔊 Sound", Color(0xFF0E7490), Color.White) { appendDynamicStep(ActionType.PLAY_SOUND) }
            }
        }

        // Dropdown Visual Skill Cooldown Reader MLBB (baca angka detik cooldown dari layar game)
        SubDropdownHeader(
            title = "⏱️ Cooldown Skill ML: ${if (draftEnableVisualCooldown) "AUTO TUNGGU AKTIF" else "OFF"}",
            open = showCooldownScanSection
        ) {
            showCooldownScanSection = !showCooldownScanSection
        }

        if (showCooldownScanSection) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(DarkSurface)
                    .border(
                        0.8.dp,
                        if (draftEnableVisualCooldown) NeonCyan.copy(alpha = 0.7f) else DarkCardBorder,
                        RoundedCornerShape(7.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Baca Angka Cooldown MLBB",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Jika area tombol masih ada angka detik cooldown, antrian otomatis menunggu hingga skill aktif (kosong angka).",
                            color = Color(0xFF94A3B8),
                            fontSize = 7.8.sp
                        )
                    }
                    Switch(
                        checked = draftEnableVisualCooldown,
                        onCheckedChange = { enabled ->
                            draftEnableVisualCooldown = enabled
                            onSaveDraftOnly(buildRule())
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan)
                    )
                }

                if (draftEnableVisualCooldown) {
                    Text(
                        text = "Lebar Scan: ${(draftCooldownWidthRatio * 100).roundToInt()}%  •  Tinggi Scan: ${(draftCooldownHeightRatio * 100).roundToInt()}%",
                        color = NeonCyan,
                        fontSize = 8.4.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Slider(
                            value = draftCooldownWidthRatio.coerceIn(0.025f, 0.35f),
                            onValueChange = {
                                draftCooldownWidthRatio = it.coerceIn(0.025f, 0.35f)
                                onSaveDraftOnly(buildRule())
                            },
                            valueRange = 0.025f..0.35f,
                            colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
                            modifier = Modifier.weight(1f).height(20.dp)
                        )
                        Slider(
                            value = draftCooldownHeightRatio.coerceIn(0.025f, 0.45f),
                            onValueChange = {
                                draftCooldownHeightRatio = it.coerceIn(0.025f, 0.45f)
                                onSaveDraftOnly(buildRule())
                            },
                            valueRange = 0.025f..0.45f,
                            colors = SliderDefaults.colors(thumbColor = EmeraldGreen, activeTrackColor = EmeraldGreen),
                            modifier = Modifier.weight(1f).height(20.dp)
                        )
                    }
                    Text(
                        text = "Geser Box X: ${(draftCooldownOffsetX * 100).roundToInt()}%  •  Geser Box Y: ${(draftCooldownOffsetY * 100).roundToInt()}%",
                        color = Color(0xFFCBD5E1),
                        fontSize = 8.2.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Slider(
                            value = draftCooldownOffsetX.coerceIn(-0.25f, 0.25f),
                            onValueChange = {
                                draftCooldownOffsetX = it.coerceIn(-0.25f, 0.25f)
                                onSaveDraftOnly(buildRule())
                            },
                            valueRange = -0.25f..0.25f,
                            colors = SliderDefaults.colors(thumbColor = NeonOrange, activeTrackColor = NeonOrange),
                            modifier = Modifier.weight(1f).height(20.dp)
                        )
                        Slider(
                            value = draftCooldownOffsetY.coerceIn(-0.25f, 0.25f),
                            onValueChange = {
                                draftCooldownOffsetY = it.coerceIn(-0.25f, 0.25f)
                                onSaveDraftOnly(buildRule())
                            },
                            valueRange = -0.25f..0.25f,
                            colors = SliderDefaults.colors(thumbColor = NeonPurple, activeTrackColor = NeonPurple),
                            modifier = Modifier.weight(1f).height(20.dp)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ComfortableInput(
                            label = "Max Tunggu(ms)",
                            value = draftCooldownMaxWaitText,
                            onValueChange = { draftCooldownMaxWaitText = it.filter(Char::isDigit).take(6) },
                            keyboardType = KeyboardType.Number,
                            onFocusChange = onKeyboardFocusChanged,
                            modifier = Modifier.weight(1f)
                        )
                        TextChip("Reset Box", Color(0xFF334155), Color.White) {
                            draftCooldownWidthRatio = 0.075f
                            draftCooldownHeightRatio = 0.12f
                            draftCooldownOffsetX = 0f
                            draftCooldownOffsetY = 0f
                            onSaveDraftOnly(buildRule())
                        }
                        TextChip("🔍 Tes Scan", NeonCyan, Color.Black) {
                            onInspectCooldown(buildRule()) { result ->
                                cooldownScanTestMsg = result
                            }
                        }
                    }
                    cooldownScanTestMsg?.let { msg ->
                        Text(
                            text = "Hasil Scan: $msg",
                            color = EmeraldGreen,
                            fontSize = 8.4.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = onCancel,
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("Tutup", fontSize = 10.sp, color = Color(0xFFCBD5E1))
            }
            Button(
                onClick = { onSave(buildRule()) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("Simpan", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Black)
            }
        }
    }
}

private fun shortStepLabel(step: UniversalActionStep): String {
    if (step.stepName.isNotBlank()) return step.stepName.trim()
    return when (step.actionType) {
        ActionType.SIMULATED_CLICK -> {
            val x = ((step.posX ?: 0.5f) * 100f).roundToInt()
            val y = ((step.posY ?: 0.5f) * 100f).roundToInt()
            val dragTag = if (step.holdAndDrag) "+Geser ${step.dragDirection}" else ""
            val holdTag = if (step.durationMs == 0L || step.holdAndDrag || step.durationMs != 70L) " ${step.durationMs}ms" else ""
            "Klik ($x%,$y%)$holdTag$dragTag"
        }
        ActionType.SIMULATED_SWIPE -> {
            val dir = if (step.paramValue.equals("RANDOM", true)) "🎲Random" else step.paramValue.ifBlank { "RIGHT" }
            "Geser $dir"
        }
        ActionType.DELAY, ActionType.WAIT -> "Jeda ${step.durationMs}ms"
        ActionType.HOME_BACK_GAME -> "Home ${step.durationMs}ms→Game"
        ActionType.CUSTOM_IMAGE, ActionType.SHOW_OVERLAY -> "Overlay"
        ActionType.PLAY_SOUND, ActionType.CUSTOM_BACKSOUND -> "Sound (${soundLabel(step.paramValue).take(10)})"
        else -> step.actionType.displayName
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DropdownTestSection(
    engine: UniversalTriggerEngine,
    rules: List<UniversalTriggerRule>,
    isEngineEnabled: Boolean,
    isA11yEnabled: Boolean,
    history: List<TriggerExecutionRecord>
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = when {
                rules.isEmpty() -> "⚠️ Belum ada konsep! Buat dulu di dropdown Konsep & Koreografi."
                !isEngineEnabled -> "⚠️ Engine dijeda (⏸) — ketuk ⚡ di menu horizontal atas."
                !isA11yEnabled -> "⚠️ Aksesibilitas OFF — tap ML & Home→Game butuh Aksesibilitas ON."
                else -> "Ketuk event untuk tes semua konsep terkait:"
            },
            color = if (rules.isEmpty() || !isEngineEnabled || !isA11yEnabled) NeonOrange else Color(0xFFCBD5E1),
            fontSize = 9.sp
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(
                UniversalTriggerSource.LIKE to "❤️ Like",
                UniversalTriggerSource.GIFT to "🎁 Gift",
                UniversalTriggerSource.CHAT to "💬 Chat",
                UniversalTriggerSource.FOLLOW to "➕ Follow",
                UniversalTriggerSource.SHARE to "↗️ Share",
                UniversalTriggerSource.MEMBER_JOIN to "👋 Join"
            ).forEach { (src, label) ->
                TextChip(label, NeonCyan.copy(alpha = 0.25f), Color.White) {
                    val matching = rules.filter { it.triggerSource == src }
                    if (matching.isEmpty()) {
                        Toast.makeText(context, "Belum ada konsep untuk ${src.displayName}", Toast.LENGTH_SHORT).show()
                    } else {
                        if (!isEngineEnabled) engine.setEngineEnabled(true)
                        matching.forEachIndexed { idx, r ->
                            engine.forceTestRuleNow(
                                ruleId = r.id,
                                customEvent = LiveInteractionEvent(
                                    source = src,
                                    senderName = "Tester",
                                    quantity = if (r.enableMinimumQuantity) r.minimumQuantity else 1,
                                    diamonds = 10,
                                    giftName = "Rose",
                                    text = "Tes ${src.displayName}"
                                )
                            ) { result ->
                                if (idx == 0) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        Toast.makeText(context, "${r.triggerName}: $result", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            TextChip("⏹ Stop", Color(0xFF991B1B), Color.White) {
                engine.emergencyStop()
                Toast.makeText(context, "⏹ Dihentikan", Toast.LENGTH_SHORT).show()
            }
        }

        if (history.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurface)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                history.take(4).forEach { rec ->
                    val col = when (rec.status) {
                        DispatchStatus.EXECUTED -> EmeraldGreen
                        DispatchStatus.QUEUED, DispatchStatus.ACCUMULATED -> NeonCyan
                        else -> NeonOrange
                    }
                    Text(
                        text = "• ${rec.ruleName}: ${rec.detail}",
                        color = col,
                        fontSize = 8.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownLiveSettingsSection(
    engine: UniversalTriggerEngine,
    isA11yEnabled: Boolean,
    onHideFloatingForLive: () -> Unit,
    onResetHudPosition: () -> Unit,
    onKeyboardFocusChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val uiState by engine.state.collectAsState()
    val prefs = remember { LiveEventPreferences.getInstance(context) }
    val receiver = remember { LiveEventReceiver.getInstance(context) }
    val relayConfig by prefs.config.collectAsState()
    val relayStatus by receiver.connectionStatus.collectAsState()
    var hostInput by remember(relayConfig.serverHost) { mutableStateOf(relayConfig.serverHost) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Aksesibilitas Tap MLBB", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (isA11yEnabled) "Aktif — siap sentuh & Home" else "Mati — ketuk Aktifkan",
                    color = if (isA11yEnabled) EmeraldGreen else NeonOrange,
                    fontSize = 8.5.sp
                )
            }
            if (!isA11yEnabled) {
                TextChip("Aktifkan", NeonOrange, Color.Black) {
                    MlbbGestureAccessibilityService.openAccessibilitySettings(context)
                }
            } else {
                Text("✓ ON", color = EmeraldGreen, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val isConn = relayStatus == ConnectionStatus.CONNECTED
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Termux (${if (isConn) "Terhubung" else relayStatus.name})",
                    color = if (isConn) EmeraldGreen else Color(0xFFCBD5E1),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
                TextChip(
                    label = if (isConn) "Putus" else "Hubung",
                    bg = if (isConn) Color(0xFF991B1B) else EmeraldGreen,
                    fg = if (isConn) Color.White else Color.Black
                ) {
                    if (isConn) receiver.disconnect() else {
                        prefs.updateServerAddress(hostInput, relayConfig.serverPort)
                        receiver.connect()
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ComfortableInput(
                    label = "IP Termux",
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    onFocusChange = onKeyboardFocusChanged,
                    modifier = Modifier.weight(1f)
                )
                TextChip("Simpan IP", NeonCyan, Color.Black) {
                    prefs.updateServerAddress(hostInput, relayConfig.serverPort)
                    onKeyboardFocusChanged(false)
                    Toast.makeText(context, "✅ IP Termux: $hostInput:${relayConfig.serverPort}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("⏸️ Jeda Sentuhan Sementara", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = uiState.isMlbbMatchActive,
                onCheckedChange = { engine.setMlbbMatchActive(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = NeonOrange)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("🔒 Blokir Sentuhan Layar Saat Challenge", color = Color.White, fontSize = 9.2.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Sentuhan manual diblokir selama challenge berjalan.",
                    color = Color(0xFF94A3B8),
                    fontSize = 7.6.sp
                )
            }
            Switch(
                checked = uiState.blockTouchDuringChallenge,
                onCheckedChange = { engine.setBlockTouchDuringChallenge(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFDC2626))
            )
        }

        // Pengaturan Ukuran & Transparansi Overlay Floating
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val cfg = uiState.floatingConfig
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ukuran HUD: ${(cfg.hudScale * 100).roundToInt()}%",
                    color = NeonCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectPill("Mini", cfg.hudScale < 0.85f) {
                        engine.updateFloatingConfig { it.copy(hudScale = 0.72f, panelWidthRatio = 0.45f) }
                    }
                    SelectPill("Sedang", cfg.hudScale in 0.85f..1.1f) {
                        engine.updateFloatingConfig { it.copy(hudScale = 1.0f, panelWidthRatio = 0.62f) }
                    }
                    SelectPill("Besar", cfg.hudScale > 1.1f) {
                        engine.updateFloatingConfig { it.copy(hudScale = 1.24f, panelWidthRatio = 0.85f) }
                    }
                }
            }
            Slider(
                value = cfg.hudScale.coerceIn(0.65f, 1.35f),
                onValueChange = { next ->
                    engine.updateFloatingConfig { it.copy(hudScale = next.coerceIn(0.65f, 1.35f)) }
                },
                valueRange = 0.65f..1.35f,
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
                modifier = Modifier.fillMaxWidth().height(22.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lebar Menu: ${(cfg.panelWidthRatio * 100).roundToInt()}%  •  Transparansi: ${(cfg.hudOpacity * 100).roundToInt()}%",
                    color = Color(0xFFCBD5E1),
                    fontSize = 8.2.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = cfg.panelWidthRatio.coerceIn(0.38f, 0.98f),
                    onValueChange = { next ->
                        engine.updateFloatingConfig { it.copy(panelWidthRatio = next.coerceIn(0.38f, 0.98f)) }
                    },
                    valueRange = 0.38f..0.98f,
                    colors = SliderDefaults.colors(thumbColor = EmeraldGreen, activeTrackColor = EmeraldGreen),
                    modifier = Modifier.weight(1f).height(22.dp)
                )
                Slider(
                    value = cfg.hudOpacity.coerceIn(0.35f, 1.0f),
                    onValueChange = { next ->
                        engine.updateFloatingConfig { it.copy(hudOpacity = next.coerceIn(0.35f, 1.0f)) }
                    },
                    valueRange = 0.35f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = NeonOrange, activeTrackColor = NeonOrange),
                    modifier = Modifier.weight(1f).height(22.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💾 Backup JSON Semua", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TextChip("📤 Ekspor", EmeraldGreen, Color.Black) {
                    val json = FullAppBackupManager.exportFullBackupJson(context)
                    FloatingPickerBridge.launch(
                        context = context,
                        mode = FloatingPickerMode.EXPORT_FULL_BACKUP_FILE,
                        suggestedFileName = "streambattle_backup_${System.currentTimeMillis() / 1000}.json",
                        exportJsonPayload = json
                    )
                }
                TextChip("📥 Impor", NeonCyan, Color.Black) {
                    FloatingPickerBridge.launch(
                        context = context,
                        mode = FloatingPickerMode.IMPORT_FULL_BACKUP_FILE
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            OutlinedButton(
                onClick = onResetHudPosition,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("↺ Reset", color = NeonCyan, fontSize = 9.2.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    engine.updateFloatingConfig { it.copy(hudCollapsed = true) }
                    onKeyboardFocusChanged(false)
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("— Ciutkan", color = NeonOrange, fontSize = 9.2.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onHideFloatingForLive,
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("👁️ Live Bersih", color = Color.White, fontSize = 9.2.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

private fun soundLabel(spec: String): String {
    if (spec.isBlank()) return "-"
    return if (spec.startsWith("file://") || spec.startsWith("content://")) {
        Uri.parse(spec).lastPathSegment?.substringAfterLast('/') ?: "File Audio"
    } else {
        spec
    }
}

@Composable
private fun SubDropdownHeader(title: String, open: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurface)
            .border(0.7.dp, if (open) NeonCyan.copy(alpha = 0.7f) else DarkCardBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (open) NeonCyan else Color(0xFFCBD5E1),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = if (open) NeonCyan else Color(0xFF94A3B8),
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun ActionPillButton(text: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.18f))
            .border(0.7.dp, tint.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TextChip(
    label: String,
    bg: Color,
    fg: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SelectPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) NeonCyan.copy(alpha = 0.24f) else DarkSurface)
            .border(0.7.dp, if (selected) NeonCyan else DarkCardBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) NeonCyan else Color(0xFFCBD5E1), fontSize = 8.8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ComfortableInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurface)
            .border(0.7.dp, DarkCardBorder, RoundedCornerShape(6.dp))
            .clickable {
                onFocusChange(true)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 7.5.sp, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(NeonCyan),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onFocusChange(false)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 20.dp)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    onFocusChange(it.isFocused)
                    if (it.isFocused) {
                        keyboardController?.show()
                    }
                }
        )
    }
}
