package com.tajasb.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.tajasb.interaction.ConceptGrouper
import com.tajasb.interaction.ConceptGroupingMode
import com.tajasb.interaction.DispatchStatus
import com.tajasb.interaction.ExecutionMode
import com.tajasb.interaction.FloatingPickerBridge
import com.tajasb.interaction.FloatingPickerMode
import com.tajasb.interaction.FloatingPickerProxyActivity
import com.tajasb.interaction.FullAppBackupManager
import com.tajasb.interaction.GiftCoinCatalog
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
        val res = pickerResult ?: return@LaunchedEffect
        val belongsToFloating = res.requestKey.isBlank() ||
            res.requestKey.startsWith(FloatingPickerProxyActivity.PREFIX_RULE_IMAGE) ||
            res.requestKey.startsWith(FloatingPickerProxyActivity.PREFIX_RULE_SOUND)
        if (belongsToFloating) {
            FloatingPickerBridge.consumeResult()
        }
    }

    val activeAccent = when (openSection) {
        FloatingDropdownSection.CONCEPTS -> NeonCyan
        FloatingDropdownSection.TEST_TRIGGER -> EmeraldGreen
        FloatingDropdownSection.LIVE_SETTINGS -> NeonOrange
    }

    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val safeMaxHeightDp = (cfg.screenHeightDp - if (isLandscape) 86 else 135).coerceIn(155, 480)
    val configuredHeightDp = 190f + uiState.floatingConfig.panelHeightRatio.coerceIn(0.35f, 0.96f) * 210f
    val dynamicMaxHeightDp = minOf(configuredHeightDp, safeMaxHeightDp.toFloat()).dp

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
                            allRules = uiState.rules,
                            activePinStepId = uiState.activePinStepId,
                            isNewEmpty = activeEditRule.actionSequence.isEmpty(),
                            isDevice2SimpleMode = uiState.isDevice2SimpleMode,
                            onSetSimpleMode = { engine.setDevice2SimpleMode(it) },
                            onSelectStepPin = { stepId -> engine.selectActivePin(activeEditRule.id, stepId) },
                            onSaveDraftOnly = { updated -> engine.addOrUpdateRule(updated) },
                            onSave = { updated ->
                                engine.addOrUpdateRule(updated)
                                editingRuleId = null
                                onClearSelectedRuleToEdit()
                                onKeyboardFocusChanged(false)
                                Toast.makeText(context, "✅ ${updated.triggerName} disimpan", Toast.LENGTH_SHORT).show()
                            },
                            onDuplicateConcept = { updated ->
                                engine.addOrUpdateRule(updated)
                                val copy = engine.duplicateRule(updated.id)
                                if (copy != null) {
                                    editingRuleId = copy.id
                                    Toast.makeText(context, "✅ Diduplikat: ${copy.triggerName}", Toast.LENGTH_SHORT).show()
                                }
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
                            onCreateConcept = { source, starterAction ->
                                val created = engine.createEmptyConcept(source)
                                if (starterAction != null) {
                                    val starterStep = buildStarterStepForAction(
                                        actionType = starterAction,
                                        posX = created.overlayPosX,
                                        posY = created.overlayPosY
                                    )
                                    if (starterStep != null) {
                                        engine.addOrUpdateRule(created.copy(actionSequence = listOf(starterStep)))
                                    }
                                }
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
                            onDuplicateRule = { rule ->
                                val copy = engine.duplicateRule(rule.id)
                                if (copy != null) {
                                    Toast.makeText(context, "✅ Konsep diduplikat: ${copy.triggerName}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onTestRule = { rule ->
                                engine.forceTestRuleNow(rule.id) { res ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        Toast.makeText(context, "${rule.triggerName}: $res", Toast.LENGTH_SHORT).show()
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
                        activePinRuleId = uiState.activePinRuleId,
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
            .defaultMinSize(minHeight = 30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) accent.copy(alpha = 0.2f) else DarkSurface)
            .border(
                width = if (selected) 1.dp else 0.7.dp,
                color = if (selected) accent else DarkCardBorder,
                shape = RoundedCornerShape(7.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color(0xFFE2E8F0),
            fontSize = 8.8.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun buildStarterStepForAction(
    actionType: ActionType,
    posX: Float,
    posY: Float
): UniversalActionStep? {
    return when (actionType) {
        ActionType.SIMULATED_CLICK -> UniversalActionStep(
            enabled = true,
            actionType = ActionType.SIMULATED_CLICK,
            paramValue = String.format(Locale.US, "%.3f,%.3f", posX, posY),
            secondaryValue = "70",
            durationMs = 70L,
            posX = posX,
            posY = posY
        )
        ActionType.SIMULATED_SWIPE -> UniversalActionStep(
            enabled = true,
            actionType = ActionType.SIMULATED_SWIPE,
            paramValue = "RIGHT",
            secondaryValue = "220",
            durationMs = 220L,
            posX = posX,
            posY = posY
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
            posX = posX,
            posY = posY
        )
        ActionType.PLAY_SOUND -> UniversalActionStep(
            enabled = true,
            actionType = ActionType.PLAY_SOUND,
            paramValue = "CHIME"
        )
        else -> null
    }
}

private fun starterPresetForGroupKey(groupKey: String): Pair<UniversalTriggerSource, ActionType?> {
    if (groupKey.startsWith("EVT_")) {
        val name = groupKey.removePrefix("EVT_")
        val src = UniversalTriggerSource.values().firstOrNull { it.name == name } ?: UniversalTriggerSource.GIFT
        return src to null
    }
    val action = when (groupKey) {
        "ACT_CLICK" -> ActionType.SIMULATED_CLICK
        "ACT_SWIPE" -> ActionType.SIMULATED_SWIPE
        "ACT_HOME" -> ActionType.HOME_BACK_GAME
        "ACT_OVERLAY" -> ActionType.MEME_OVERLAY
        "ACT_SOUND" -> ActionType.PLAY_SOUND
        else -> null
    }
    return UniversalTriggerSource.GIFT to action
}

@Composable
private fun DropdownConceptList(
    rules: List<UniversalTriggerRule>,
    activePinRuleId: String?,
    onCreateConcept: (UniversalTriggerSource, ActionType?) -> Unit,
    onToggleRule: (UniversalTriggerRule) -> Unit,
    onTogglePinEye: (UniversalTriggerRule) -> Unit,
    onSetAllEyes: (Boolean) -> Unit,
    onSelectActivePin: (UniversalTriggerRule) -> Unit,
    onEditRule: (UniversalTriggerRule) -> Unit,
    onDuplicateRule: (UniversalTriggerRule) -> Unit,
    onTestRule: (UniversalTriggerRule) -> Unit,
    onDeleteRule: (UniversalTriggerRule) -> Unit,
    onShowPinsOutside: () -> Unit
) {
    var groupingMode by remember { mutableStateOf(ConceptGroupingMode.BY_EVENT) }
    var selectedGroupKey by remember { mutableStateOf<String?>(null) }
    var collapsedGroupKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val buckets = remember(rules, groupingMode) {
        ConceptGrouper.groupRules(rules, groupingMode)
    }
    val visibleBuckets = remember(buckets, selectedGroupKey) {
        if (selectedGroupKey == null) buckets
        else buckets.filter { it.key == selectedGroupKey }.ifEmpty { buckets }
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = {
                    val (src, starter) = selectedGroupKey?.let { starterPresetForGroupKey(it) }
                        ?: (UniversalTriggerSource.GIFT to null)
                    onCreateConcept(src, starter)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.weight(1f).height(30.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("+ Konsep Baru", color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
            }
            OutlinedButton(
                onClick = onShowPinsOutside,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("📍 D-Pad", color = NeonCyan, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (rules.isNotEmpty()) {
            // Menu Grup Konsep (Grup Event / Grup Aksi / Semua)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Grup:",
                    color = Color(0xFF94A3B8),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                ConceptGroupingMode.values().forEach { mode ->
                    val active = groupingMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (active) NeonCyan.copy(alpha = 0.22f) else DarkSurface)
                            .border(0.7.dp, if (active) NeonCyan else DarkCardBorder, RoundedCornerShape(5.dp))
                            .clickable {
                                groupingMode = mode
                                selectedGroupKey = null
                            }
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            color = if (active) NeonCyan else Color(0xFFCBD5E1),
                            fontSize = 8.sp,
                            fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (groupingMode != ConceptGroupingMode.ALL && buckets.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    SelectPill("Semua (${rules.size})", selectedGroupKey == null) {
                        selectedGroupKey = null
                    }
                    buckets.forEach { bucket ->
                        val active = selectedGroupKey == bucket.key
                        SelectPill("${bucket.emoji} ${bucket.title} (${bucket.rules.size})", active) {
                            selectedGroupKey = if (active) null else bucket.key
                        }
                    }
                }
            }

            val visibleCount = rules.count { it.isPinVisible }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pin: $visibleCount/${rules.size}",
                    color = Color(0xFF94A3B8),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    SelectPill("👁 Buka", visibleCount == rules.size) { onSetAllEyes(true) }
                    SelectPill("🙈 Tutup", visibleCount == 0) { onSetAllEyes(false) }
                }
            }
        }

        if (rules.isEmpty()) {
            Text(
                text = "Belum ada konsep. Ketuk '+ Konsep Baru' lalu susun koreografi (Klik, Geser, Foto Meme, Foto Profil, Home→Game, Jeda, Sound).",
                color = Color(0xFF94A3B8),
                fontSize = 8.5.sp
            )
        } else {
            visibleBuckets.forEach { bucket ->
                val showGroupHeader = groupingMode != ConceptGroupingMode.ALL
                val isGroupCollapsed = showGroupHeader && selectedGroupKey == null && bucket.key in collapsedGroupKeys

                if (showGroupHeader) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF162033))
                            .border(0.7.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .clickable {
                                if (selectedGroupKey == null) {
                                    collapsedGroupKeys = if (isGroupCollapsed) {
                                        collapsedGroupKeys - bucket.key
                                    } else {
                                        collapsedGroupKeys + bucket.key
                                    }
                                }
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${if (isGroupCollapsed) "▸" else "▾"} ${bucket.emoji} ${bucket.title} (${bucket.rules.size})",
                            color = NeonCyan,
                            fontSize = 8.4.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        ActionPillButton("+ Grup", EmeraldGreen) {
                            val (src, starter) = starterPresetForGroupKey(bucket.key)
                            onCreateConcept(src, starter)
                        }
                    }
                }

                if (!isGroupCollapsed) {
                    bucket.rules.forEach { indexed ->
                        val idx = indexed.globalIndex
                        val rule = indexed.rule
                        val ruleColor = Color(rule.accentColorHex)
                        val isSelected = rule.id == activePinRuleId
                        val activeSteps = rule.actionSequence.filter { it.enabled }
                        val giftConflicts = remember(rule.id, rule.triggerSource, rule.targetGiftName, rules) {
                            GiftCoinCatalog.findConflictingRules(
                                currentRuleId = rule.id,
                                currentSource = rule.triggerSource,
                                targetGiftName = rule.targetGiftName,
                                allRules = rules
                            )
                        }
                        val stepSummary = when {
                            activeSteps.isEmpty() -> "Kosong"
                            activeSteps.size == 1 -> shortStepLabel(activeSteps.first())
                            else -> "${activeSteps.size}x: " + activeSteps.take(3).joinToString(" → ") { shortStepLabel(it) }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ruleColor.copy(alpha = 0.12f) else DarkSurface)
                                .border(
                                    if (isSelected) 1.2.dp else 0.7.dp,
                                    if (giftConflicts.isNotEmpty()) NeonOrange.copy(alpha = 0.85f)
                                    else if (rule.isPinVisible) ruleColor.copy(alpha = 0.8f) else DarkCardBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onEditRule(rule) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(ruleColor)
                                    .clickable { onSelectActivePin(rule) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color.Black,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                val sourceLabel = if (
                                    rule.targetGiftName.isNotBlank() &&
                                    (rule.triggerSource == UniversalTriggerSource.GIFT || rule.triggerSource == UniversalTriggerSource.GIFT_QUANTITY)
                                ) {
                                    "${rule.triggerSource.displayName} [${rule.targetGiftName}]"
                                } else {
                                    rule.triggerSource.displayName
                                }
                                Text(
                                    text = "$sourceLabel → ${rule.triggerName}",
                                    color = if (rule.enabled) Color.White else Color(0xFF64748B),
                                    fontSize = 9.2.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subLine = buildString {
                                    append(stepSummary)
                                    if (rule.enableVisualCooldownWait) append(" • ⏱ Scan")
                                    if (!rule.isPinVisible) append(" • Tutup")
                                }
                                Text(
                                    text = subLine,
                                    color = if (rule.isPinVisible) Color(0xFF94A3B8) else NeonOrange,
                                    fontSize = 7.6.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (giftConflicts.isNotEmpty()) {
                                    Text(
                                        text = "⚠️ Gift bentrok dgn ${giftConflicts.first().conflictingRuleName}",
                                        color = NeonOrange,
                                        fontSize = 7.3.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (rule.isPinVisible) ruleColor.copy(alpha = 0.20f) else Color(0xFF334155))
                                    .border(0.7.dp, if (rule.isPinVisible) ruleColor else Color(0xFF475569), RoundedCornerShape(5.dp))
                                    .clickable { onTogglePinEye(rule) }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (rule.isPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (rule.isPinVisible) "Tutup Mata Pin" else "Buka Mata Pin",
                                    tint = if (rule.isPinVisible) ruleColor else Color(0xFF94A3B8),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            ActionPillButton(if (rule.enabled) "ON" else "OFF", if (rule.enabled) EmeraldGreen else Color(0xFF64748B)) {
                                onToggleRule(rule)
                            }
                            ActionPillButton("▶", EmeraldGreen) { onTestRule(rule) }
                            ActionPillButton("⧉", NeonPurple) { onDuplicateRule(rule) }
                            ActionPillButton("✎", NeonCyan) { onEditRule(rule) }
                            ConfirmDeleteActionPill(text = "✕") { onDeleteRule(rule) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConceptSectionCard(
    stepNumber: String,
    title: String,
    accent: Color = NeonCyan,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(DarkSurface)
            .border(0.8.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(stepNumber, color = Color.Black, fontSize = 8.2.sp, fontWeight = FontWeight.Black)
            }
            Text(
                text = title,
                color = accent,
                fontSize = 8.6.sp,
                fontWeight = FontWeight.Black
            )
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DropdownConceptEditor(
    rule: UniversalTriggerRule,
    allRules: List<UniversalTriggerRule>,
    activePinStepId: String?,
    isNewEmpty: Boolean,
    isDevice2SimpleMode: Boolean = true,
    onSetSimpleMode: (Boolean) -> Unit = {},
    onSelectStepPin: (String?) -> Unit,
    onSaveDraftOnly: (UniversalTriggerRule) -> Unit,
    onSave: (UniversalTriggerRule) -> Unit,
    onDuplicateConcept: (UniversalTriggerRule) -> Unit,
    onCancel: () -> Unit,
    onDragExternalStepPin: (UniversalTriggerRule, String?) -> Unit,
    onTestNow: (UniversalTriggerRule) -> Unit,
    onInspectCooldown: (UniversalTriggerRule, (String) -> Unit) -> Unit,
    onPreviewSound: (String) -> Unit,
    onKeyboardFocusChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var draftName by remember(rule.id) { mutableStateOf(rule.triggerName) }
    var draftTargetGiftName by remember(rule.id) { mutableStateOf(rule.targetGiftName) }
    var draftSource by remember(rule.id) { mutableStateOf(rule.triggerSource) }
    var draftColorHex by remember(rule.id, rule.accentColorHex) { mutableStateOf(rule.accentColorHex) }
    var draftPinVisible by remember(rule.id, rule.isPinVisible) { mutableStateOf(rule.isPinVisible) }
    var draftDelayBeforeMs by remember(rule.id) { mutableStateOf(if (rule.enableDelayBefore) rule.delayBeforeMs else 0L) }
    var draftCooldownMs by remember(rule.id) { mutableStateOf(if (rule.enableCooldown) rule.cooldownMs else 0L) }
    var draftMinQty by remember(rule.id) { mutableStateOf(if (rule.enableMinimumQuantity) rule.minimumQuantity.coerceAtLeast(1) else 1) }
    var fineNudge by remember { mutableStateOf(true) }

    var draftEnableVisualCooldown by remember(rule.id) { mutableStateOf(rule.enableVisualCooldownWait) }
    var draftCooldownTargetStepId by remember(rule.id) { mutableStateOf(rule.cooldownTargetStepId) }
    var draftCooldownWidthRatio by remember(rule.id) { mutableStateOf(rule.cooldownAreaWidthRatio.coerceIn(0.025f, 0.45f)) }
    var draftCooldownHeightRatio by remember(rule.id) { mutableStateOf(rule.cooldownAreaHeightRatio.coerceIn(0.025f, 0.55f)) }
    var draftCooldownOffsetX by remember(rule.id) { mutableStateOf(rule.cooldownAreaOffsetX.coerceIn(-0.30f, 0.30f)) }
    var draftCooldownOffsetY by remember(rule.id) { mutableStateOf(rule.cooldownAreaOffsetY.coerceIn(-0.30f, 0.30f)) }
    var draftCooldownMaxWaitMs by remember(rule.id) { mutableStateOf(rule.cooldownMaxWaitMs.coerceIn(1500L, 180_000L)) }
    var showCooldownScanSection by remember(rule.id) { mutableStateOf(rule.enableVisualCooldownWait) }
    var cooldownScanTestMsg by remember(rule.id) { mutableStateOf<String?>(null) }

    var draftSteps by remember(rule.id) {
        mutableStateOf(rule.actionSequence)
    }

    // Sinkronkan perubahan pin/media eksternal (geser pin di layar atau hasil photo/audio picker)
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
                        local.actionType == ActionType.MEME_OVERLAY ||
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
    var showMoreAddMenu by remember(rule.id) { mutableStateOf(false) }

    val buildRuleWithSteps: (List<UniversalActionStep>) -> UniversalTriggerRule = { list ->
        val normalized = list.map { s ->
            val sx = (s.posX ?: rule.overlayPosX).coerceIn(0.01f, 0.99f)
            val sy = (s.posY ?: rule.overlayPosY).coerceIn(0.01f, 0.99f)
            when (s.actionType) {
                ActionType.SIMULATED_CLICK -> {
                    val hold = s.durationMs.coerceIn(0L, 12_000L)
                    val dragDur = s.dragDurationMs.coerceIn(0L, 5000L)
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
                    val dur = s.durationMs.coerceIn(0L, 5000L)
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
                ActionType.DELAY, ActionType.WAIT -> {
                    val ms = s.durationMs.coerceIn(0L, 60_000L)
                    s.copy(
                        enabled = true,
                        stepName = s.stepName.trim(),
                        paramValue = ms.toString(),
                        durationMs = ms,
                        delayMs = 0L
                    )
                }
                ActionType.HOME_BACK_GAME -> {
                    val ms = s.durationMs.coerceIn(0L, 30_000L)
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
                    durationMs = s.durationMs.coerceIn(0L, 60_000L),
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
        val delayBeforeVal = draftDelayBeforeMs.coerceIn(0L, 60_000L)
        val cd = draftCooldownMs.coerceAtLeast(0L)
        val minQ = draftMinQty.coerceAtLeast(1)
        val maxWait = draftCooldownMaxWaitMs.coerceIn(1500L, 180_000L)
        rule.copy(
            enabled = true,
            isPinVisible = draftPinVisible,
            accentColorHex = draftColorHex,
            triggerName = draftName.trim().ifBlank { "Konsep" },
            targetGiftName = draftTargetGiftName.trim(),
            triggerSource = draftSource,
            overlayPosX = firstPin?.posX ?: rule.overlayPosX,
            overlayPosY = firstPin?.posY ?: rule.overlayPosY,
            enableMinimumQuantity = minQ > 1,
            minimumQuantity = minQ,
            enableDelayBefore = delayBeforeVal > 0L,
            delayBeforeMs = delayBeforeVal,
            enableCooldown = cd > 0L,
            cooldownMs = cd,
            enableVisualCooldownWait = draftEnableVisualCooldown,
            cooldownTargetStepId = draftCooldownTargetStepId,
            cooldownAreaWidthRatio = draftCooldownWidthRatio.coerceIn(0.025f, 0.45f),
            cooldownAreaHeightRatio = draftCooldownHeightRatio.coerceIn(0.025f, 0.55f),
            cooldownAreaOffsetX = draftCooldownOffsetX.coerceIn(-0.30f, 0.30f),
            cooldownAreaOffsetY = draftCooldownOffsetY.coerceIn(-0.30f, 0.30f),
            cooldownMaxWaitMs = maxWait,
            actionSequence = normalized
        )
    }

    val buildRule: () -> UniversalTriggerRule = { buildRuleWithSteps(draftSteps) }
    fun isPinDraggableStep(step: UniversalActionStep): Boolean =
        step.actionType == ActionType.SIMULATED_CLICK ||
            step.actionType == ActionType.SIMULATED_SWIPE ||
            step.actionType == ActionType.PROFILE_OVERLAY ||
            step.actionType == ActionType.MEME_OVERLAY ||
            step.actionType == ActionType.CUSTOM_IMAGE ||
            step.actionType == ActionType.TEXT_OVERLAY ||
            step.actionType == ActionType.SHOW_OVERLAY ||
            step.actionType == ActionType.ANIMATION ||
            step.actionType == ActionType.TIER_OVERLAY

    val hasTouchPins = draftSteps.any { isPinDraggableStep(it) }

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
            ActionType.MEME_OVERLAY -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.MEME_OVERLAY,
                paramValue = "",
                secondaryValue = "MEME {user}!",
                durationMs = 2500L,
                posX = bx,
                posY = by,
                executionMode = ExecutionMode.PARALLEL
            )
            ActionType.PROFILE_OVERLAY -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.PROFILE_OVERLAY,
                paramValue = "{user}",
                secondaryValue = "Gift {gift} x{qty}",
                durationMs = 2800L,
                posX = bx,
                posY = by,
                executionMode = ExecutionMode.PARALLEL
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
            ActionType.TEXT_OVERLAY, ActionType.SHOW_OVERLAY -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = type,
                paramValue = "{user} • {gift} x{qty}",
                secondaryValue = "{source}",
                durationMs = 2500L,
                posX = bx,
                posY = by,
                executionMode = ExecutionMode.PARALLEL
            )
            ActionType.ANIMATION -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.ANIMATION,
                paramValue = "BOUNCE",
                secondaryValue = "{user}!",
                durationMs = 2200L,
                posX = bx,
                posY = by,
                executionMode = ExecutionMode.PARALLEL
            )
            ActionType.TIER_OVERLAY -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.TIER_OVERLAY,
                paramValue = "VIP • {user}",
                secondaryValue = "{gift} ({coins} koin)",
                durationMs = 2800L,
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
            ActionType.RANDOM_SELECTION -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.RANDOM_SELECTION,
                paramValue = "Serang|Recall|Emote|Kabur",
                durationMs = 2200L,
                posX = bx,
                posY = by,
                executionMode = ExecutionMode.SEQUENTIAL
            )
            ActionType.VOLUME_CONTROL -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.VOLUME_CONTROL,
                paramValue = "80",
                executionMode = ExecutionMode.SEQUENTIAL
            )
            ActionType.HIDE_OVERLAY -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.HIDE_OVERLAY,
                paramValue = "ALL",
                executionMode = ExecutionMode.SEQUENTIAL
            )
            ActionType.STOP_SOUND -> UniversalActionStep(
                enabled = true,
                stepName = "",
                actionType = ActionType.STOP_SOUND,
                executionMode = ExecutionMode.SEQUENTIAL
            )
            else -> UniversalActionStep(actionType = type, executionMode = ExecutionMode.SEQUENTIAL)
        }

        val next = draftSteps + newStep
        draftSteps = next
        expandedStepId = newStep.id
        showAddMenu = false
        showMoreAddMenu = false
        onSaveDraftOnly(buildRuleWithSteps(next))
        if (type == ActionType.SIMULATED_CLICK || type == ActionType.SIMULATED_SWIPE) {
            onSelectStepPin(newStep.id)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Header aksi cepat konsep
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable { showColorDrop = !showColorDrop }
            ) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(Color(draftColorHex))
                        .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                )
                Text("Warna ▾", color = Color(0xFF94A3B8), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TextChip(
                    label = if (draftPinVisible) "👁" else "🙈",
                    bg = if (draftPinVisible) Color(0xFF0E7490) else Color(0xFF475569),
                    fg = Color.White
                ) { draftPinVisible = !draftPinVisible }
                TextChip("▶ Tes", EmeraldGreen, Color.Black) { onTestNow(buildRule()) }
                TextChip("⧉ Duplikat", NeonCyan, Color.Black) { onDuplicateConcept(buildRule()) }
                if (hasTouchPins) {
                    TextChip("📍 Pin", NeonPurple, Color.White) {
                        val targetStep = draftSteps.firstOrNull {
                            it.id == expandedStepId && isPinDraggableStep(it)
                        } ?: draftSteps.firstOrNull {
                            isPinDraggableStep(it)
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

        // BAGIAN 1: IDENTITAS & PEMICU (Event, Target Gift, Syarat)
        ConceptSectionCard(stepNumber = "1", title = "Identitas & Pemicu", accent = NeonCyan) {
            ComfortableInput(
                label = "Nama Konsep / Challenge",
                value = draftName,
                onValueChange = { draftName = it },
                onFocusChange = onKeyboardFocusChanged
            )

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
                if (!isDevice2SimpleMode) {
                    Box(modifier = Modifier.weight(0.85f)) {
                        val condLabel = if (draftMinQty > 1 || draftCooldownMs > 0L) "Syarat: Aktif" else "Syarat Event"
                        SubDropdownHeader(condLabel, showConditionDrop) {
                            showConditionDrop = !showConditionDrop
                            if (showConditionDrop) showEventDrop = false
                        }
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

            if (draftSource == UniversalTriggerSource.GIFT || draftSource == UniversalTriggerSource.GIFT_QUANTITY) {
                GiftNameCatalogPicker(
                    giftName = draftTargetGiftName,
                    onGiftNameChange = { draftTargetGiftName = it },
                    onFocusChange = onKeyboardFocusChanged,
                    onQuickPicked = { picked ->
                        draftTargetGiftName = picked
                        onSaveDraftOnly(buildRule())
                    },
                    currentRuleId = rule.id,
                    currentTriggerSource = draftSource,
                    existingRules = allRules
                )
            }

            if (!isDevice2SimpleMode && showConditionDrop) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ComfortableMsInput(
                        label = "Min Qty",
                        value = draftMinQty.toLong(),
                        onValueCommitted = { draftMinQty = it.toInt().coerceIn(1, 99_999) },
                        minValue = 1L,
                        maxValue = 99_999L,
                        maxDigits = 5,
                        resetKey = rule.id,
                        onFocusChange = onKeyboardFocusChanged,
                        modifier = Modifier.weight(0.8f)
                    )
                    ComfortableMsInput(
                        label = "Jeda Antar Trigger (ms)",
                        value = draftCooldownMs,
                        onValueCommitted = { draftCooldownMs = it },
                        minValue = 0L,
                        maxValue = 600_000L,
                        maxDigits = 6,
                        resetKey = rule.id,
                        onFocusChange = onKeyboardFocusChanged,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }

        // BAGIAN 2: PRA-AKSI (Disembunyikan di Mode 2-HP Simpel; diatur lewat Device 1 Web)
        if (isDevice2SimpleMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(DarkSurface)
                    .border(0.8.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚙️ Detail timing, scan OCR & syarat diatur di Web Device 1",
                    color = Color(0xFFCBD5E1),
                    fontSize = 7.8.sp,
                    modifier = Modifier.weight(1f)
                )
                TextChip("🎛️ Buka Detail", NeonCyan, Color.Black) {
                    onSetSimpleMode(false)
                }
            }
        } else {
        ConceptSectionCard(stepNumber = "2", title = "Pra-Aksi (Sebelum Koreografi)", accent = NeonOrange) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkBackground.copy(alpha = 0.7f))
                    .border(
                        0.8.dp,
                        if (draftEnableVisualCooldown) NeonCyan.copy(alpha = 0.7f) else DarkCardBorder,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⏱️ Delay Scan Cooldown MLBB",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (draftEnableVisualCooldown) {
                                "Aktif — menunggu angka CD hilang di tombol skill, tanpa jeda tambahan."
                            } else {
                                "Aktifkan jika ingin menunggu cooldown skill ML otomatis sebelum tap."
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 7.4.sp
                        )
                    }
                    Switch(
                        checked = draftEnableVisualCooldown,
                        onCheckedChange = { enabled ->
                            draftEnableVisualCooldown = enabled
                            showCooldownScanSection = enabled
                            onSaveDraftOnly(buildRule())
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan)
                    )
                }

                if (draftEnableVisualCooldown) {
                    val touchCandidates = draftSteps.filter {
                        it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE
                    }
                    if (touchCandidates.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pin Scan:", color = Color(0xFF94A3B8), fontSize = 7.8.sp)
                            touchCandidates.forEachIndexed { tIdx, tStep ->
                                val selected = draftCooldownTargetStepId == tStep.id ||
                                    (draftCooldownTargetStepId.isNullOrBlank() && tIdx == 0)
                                val label = tStep.stepName.trim().ifBlank { "#${tIdx + 1}" }
                                SelectPill(label.take(10), selected) {
                                    draftCooldownTargetStepId = tStep.id
                                    onSaveDraftOnly(buildRule())
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PrecisionStepperBar(
                            label = "Lebar",
                            valueText = "${(draftCooldownWidthRatio * 100).roundToInt()}%",
                            accent = NeonCyan,
                            onDecrement = {
                                draftCooldownWidthRatio = (draftCooldownWidthRatio - 0.01f).coerceIn(0.025f, 0.45f)
                                onSaveDraftOnly(buildRule())
                            },
                            onIncrement = {
                                draftCooldownWidthRatio = (draftCooldownWidthRatio + 0.01f).coerceIn(0.025f, 0.45f)
                                onSaveDraftOnly(buildRule())
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PrecisionStepperBar(
                            label = "Tinggi",
                            valueText = "${(draftCooldownHeightRatio * 100).roundToInt()}%",
                            accent = EmeraldGreen,
                            onDecrement = {
                                draftCooldownHeightRatio = (draftCooldownHeightRatio - 0.01f).coerceIn(0.025f, 0.55f)
                                onSaveDraftOnly(buildRule())
                            },
                            onIncrement = {
                                draftCooldownHeightRatio = (draftCooldownHeightRatio + 0.01f).coerceIn(0.025f, 0.55f)
                                onSaveDraftOnly(buildRule())
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PrecisionStepperBar(
                            label = "Geser X",
                            valueText = "${(draftCooldownOffsetX * 100).roundToInt()}%",
                            accent = NeonOrange,
                            decLabel = "◀",
                            incLabel = "▶",
                            onDecrement = {
                                draftCooldownOffsetX = (draftCooldownOffsetX - 0.01f).coerceIn(-0.30f, 0.30f)
                                onSaveDraftOnly(buildRule())
                            },
                            onIncrement = {
                                draftCooldownOffsetX = (draftCooldownOffsetX + 0.01f).coerceIn(-0.30f, 0.30f)
                                onSaveDraftOnly(buildRule())
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PrecisionStepperBar(
                            label = "Geser Y",
                            valueText = "${(draftCooldownOffsetY * 100).roundToInt()}%",
                            accent = NeonPurple,
                            decLabel = "▲",
                            incLabel = "▼",
                            onDecrement = {
                                draftCooldownOffsetY = (draftCooldownOffsetY - 0.01f).coerceIn(-0.30f, 0.30f)
                                onSaveDraftOnly(buildRule())
                            },
                            onIncrement = {
                                draftCooldownOffsetY = (draftCooldownOffsetY + 0.01f).coerceIn(-0.30f, 0.30f)
                                onSaveDraftOnly(buildRule())
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ComfortableMsInput(
                            label = "Max Tunggu(ms)",
                            value = draftCooldownMaxWaitMs,
                            onValueCommitted = { draftCooldownMaxWaitMs = it },
                            minValue = 1500L,
                            maxValue = 180_000L,
                            maxDigits = 6,
                            resetKey = rule.id,
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
                        TextChip("🔍 Scan Sekali", NeonCyan, Color.Black) {
                            onInspectCooldown(buildRule()) { result ->
                                cooldownScanTestMsg = result
                            }
                        }
                    }
                    cooldownScanTestMsg?.let { msg ->
                        Text(
                            text = "Hasil Scan: $msg",
                            color = EmeraldGreen,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (draftEnableVisualCooldown) {
                Text(
                    text = "⚡ Delay Awal otomatis dilewati saat Delay Scan aktif — langsung eksekusi saat siap.",
                    color = NeonCyan,
                    fontSize = 7.6.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                ComfortableMsInput(
                    label = "⏱️ Delay Awal Sebelum Koreografi (ms)",
                    value = draftDelayBeforeMs,
                    onValueCommitted = { draftDelayBeforeMs = it },
                    minValue = 0L,
                    maxValue = 60_000L,
                    maxDigits = 5,
                    resetKey = rule.id,
                    onFocusChange = onKeyboardFocusChanged
                )
            }
        }
        }

        // BAGIAN 3: LANGKAH KOREOGRAFI
        ConceptSectionCard(stepNumber = "3", title = "Langkah Koreografi (${draftSteps.size})", accent = EmeraldGreen) {
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
                        ActionPillButton("⧉", NeonPurple) {
                            val isTouch = step.actionType == ActionType.SIMULATED_CLICK || step.actionType == ActionType.SIMULATED_SWIPE
                            val nx = if (isTouch && step.posX != null) (step.posX + 0.04f).coerceIn(0.05f, 0.95f) else step.posX
                            val ny = if (isTouch && step.posY != null) (step.posY - 0.04f).coerceIn(0.05f, 0.95f) else step.posY
                            val cloned = step.copy(
                                id = java.util.UUID.randomUUID().toString(),
                                posX = nx,
                                posY = ny,
                                paramValue = if (step.actionType == ActionType.SIMULATED_CLICK && nx != null && ny != null) {
                                    String.format(Locale.US, "%.3f,%.3f", nx, ny)
                                } else {
                                    step.paramValue
                                }
                            )
                            val next = draftSteps.toMutableList().apply { add(idx + 1, cloned) }
                            draftSteps = next
                            expandedStepId = cloned.id
                            onSaveDraftOnly(buildRuleWithSteps(next))
                        }
                        ConfirmDeleteActionPill(text = "✕") {
                            val next = draftSteps.filterNot { it.id == step.id }
                            draftSteps = next
                            if (expandedStepId == step.id) expandedStepId = next.firstOrNull()?.id
                            if (draftCooldownTargetStepId == step.id) draftCooldownTargetStepId = null
                            onSaveDraftOnly(buildRuleWithSteps(next))
                        }
                    }

                    if (isStepExpanded) {
                        fun updateCurrentStep(transform: (UniversalActionStep) -> UniversalActionStep) {
                            draftSteps = draftSteps.map { if (it.id == step.id) transform(it) else it }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ComfortableInput(
                                label = "Nama Langkah",
                                value = step.stepName,
                                onValueChange = { raw -> updateCurrentStep { it.copy(stepName = raw) } },
                                onFocusChange = onKeyboardFocusChanged,
                                modifier = Modifier.weight(1.35f)
                            )
                            if (!isDevice2SimpleMode && step.actionType != ActionType.DELAY && step.actionType != ActionType.WAIT) {
                                ComfortableMsInput(
                                    label = "Delay(ms)",
                                    value = step.delayMs,
                                    onValueCommitted = { v ->
                                        updateCurrentStep { it.copy(delayMs = v) }
                                    },
                                    minValue = 0L,
                                    maxValue = 60_000L,
                                    maxDigits = 5,
                                    resetKey = step.id,
                                    onFocusChange = onKeyboardFocusChanged,
                                    modifier = Modifier.weight(0.65f)
                                )
                            }
                        }

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
                                    if (!isDevice2SimpleMode) {
                                        ComfortableInput(
                                            label = "Arah Custom (opsional: 45° atau X,Y mis. 0.80,0.40)",
                                            value = step.paramValue,
                                            onValueChange = { raw -> updateCurrentStep { it.copy(paramValue = raw) } },
                                            onFocusChange = onKeyboardFocusChanged
                                        )
                                    }
                                }

                                if (!isDevice2SimpleMode) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        ComfortableMsInput(
                                            label = if (step.actionType == ActionType.SIMULATED_SWIPE) "Durasi(ms)" else "Tahan(ms, 0=Klik)",
                                            value = step.durationMs,
                                            onValueCommitted = { c ->
                                                updateCurrentStep {
                                                    it.copy(durationMs = c, secondaryValue = c.toString())
                                                }
                                            },
                                            minValue = 0L,
                                            maxValue = if (step.actionType == ActionType.SIMULATED_CLICK) 12_000L else 5000L,
                                            maxDigits = 5,
                                            resetKey = step.id,
                                            onFocusChange = onKeyboardFocusChanged,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ComfortableMsInput(
                                            label = "Ulang(x)",
                                            value = step.repeatCount.toLong(),
                                            onValueCommitted = { v ->
                                                updateCurrentStep { it.copy(repeatCount = v.toInt().coerceIn(1, 20)) }
                                            },
                                            minValue = 1L,
                                            maxValue = 20L,
                                            maxDigits = 2,
                                            resetKey = step.id,
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
                                                updateCurrentStep { it.copy(durationMs = 0L, secondaryValue = "0") }
                                            }
                                            SelectPill("400ms", step.durationMs == 400L) {
                                                updateCurrentStep { it.copy(durationMs = 400L, secondaryValue = "400") }
                                            }
                                            SelectPill("1000ms", step.durationMs == 1000L) {
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
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                ComfortableInput(
                                                    label = "Arah Geser (atau 45° / X,Y)",
                                                    value = step.dragDirection,
                                                    onValueChange = { raw -> updateCurrentStep { it.copy(dragDirection = raw) } },
                                                    onFocusChange = onKeyboardFocusChanged,
                                                    modifier = Modifier.weight(1.25f)
                                                )
                                                ComfortableMsInput(
                                                    label = "Durasi Geser(ms)",
                                                    value = step.dragDurationMs,
                                                    onValueCommitted = { v ->
                                                        updateCurrentStep { it.copy(dragDurationMs = v) }
                                                    },
                                                    minValue = 0L,
                                                    maxValue = 5000L,
                                                    maxDigits = 4,
                                                    resetKey = step.id,
                                                    onFocusChange = onKeyboardFocusChanged,
                                                    modifier = Modifier.weight(0.85f)
                                                )
                                            }
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
                                if (!isDevice2SimpleMode) {
                                    ComfortableMsInput(
                                        label = "Durasi Jeda (ms)",
                                        value = step.durationMs,
                                        onValueCommitted = { c ->
                                            updateCurrentStep { it.copy(durationMs = c, paramValue = c.toString()) }
                                        },
                                        minValue = 0L,
                                        maxValue = 60_000L,
                                        maxDigits = 5,
                                        resetKey = step.id,
                                        onFocusChange = onKeyboardFocusChanged
                                    )
                                } else {
                                    Text(
                                        text = "⏱️ Jeda: ${step.durationMs} ms (ubah ms detail di Device 1 Web)",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 8.sp
                                    )
                                }
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

                                if (!isDevice2SimpleMode) {
                                    ComfortableMsInput(
                                        label = "Jeda di Home (ms)",
                                        value = step.durationMs,
                                        onValueCommitted = { c ->
                                            updateCurrentStep { it.copy(durationMs = c, paramValue = c.toString()) }
                                        },
                                        minValue = 0L,
                                        maxValue = 30_000L,
                                        maxDigits = 5,
                                        resetKey = step.id,
                                        onFocusChange = onKeyboardFocusChanged
                                    )
                                }

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

                            ActionType.MEME_OVERLAY, ActionType.CUSTOM_IMAGE, ActionType.PROFILE_OVERLAY,
                            ActionType.TEXT_OVERLAY, ActionType.SHOW_OVERLAY, ActionType.ANIMATION,
                            ActionType.TIER_OVERLAY -> {
                                val isMemeOrCustomImage = step.actionType == ActionType.MEME_OVERLAY ||
                                    step.actionType == ActionType.CUSTOM_IMAGE
                                val isProfileOverlay = step.actionType == ActionType.PROFILE_OVERLAY
                                val isAnimation = step.actionType == ActionType.ANIMATION

                                if (isMemeOrCustomImage) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (step.paramValue.startsWith("file://") ||
                                            step.paramValue.startsWith("content://") ||
                                            step.paramValue.startsWith("http")
                                        ) {
                                            AsyncImage(
                                                model = step.paramValue,
                                                contentDescription = "Preview Foto Meme",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .border(0.8.dp, NeonCyan, RoundedCornerShape(5.dp))
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
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(28.dp)
                                        ) {
                                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                if (step.paramValue.isBlank()) "Upload Foto Meme" else "Ganti Foto",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (step.paramValue.isNotBlank()) {
                                            ConfirmDeleteTextChip(label = "Hapus") {
                                                updateCurrentStep { it.copy(paramValue = "") }
                                            }
                                        }
                                    }
                                } else if (isProfileOverlay) {
                                    Text(
                                        text = "👤 Menampilkan foto profil pengirim gift/interaksi otomatis (fallback inisial bila offline).",
                                        color = NeonCyan,
                                        fontSize = 7.6.sp
                                    )
                                } else if (isAnimation) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        listOf("BOUNCE", "SHAKE", "PULSE", "FLASH").forEach { anim ->
                                            SelectPill(anim, step.paramValue.equals(anim, ignoreCase = true)) {
                                                updateCurrentStep { it.copy(paramValue = anim) }
                                            }
                                        }
                                    }
                                }

                                if (!isDevice2SimpleMode) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (!isMemeOrCustomImage && !isAnimation) {
                                            ComfortableInput(
                                                label = if (isProfileOverlay) "Judul ({user})" else "Teks ({user}/{gift})",
                                                value = step.paramValue,
                                                onValueChange = { raw -> updateCurrentStep { it.copy(paramValue = raw) } },
                                                onFocusChange = onKeyboardFocusChanged,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        ComfortableInput(
                                            label = if (isMemeOrCustomImage) "Caption (opsional)" else "Subjudul",
                                            value = step.secondaryValue,
                                            onValueChange = { raw -> updateCurrentStep { it.copy(secondaryValue = raw) } },
                                            onFocusChange = onKeyboardFocusChanged,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ComfortableMsInput(
                                            label = "Durasi(ms)",
                                            value = step.durationMs,
                                            onValueCommitted = { v ->
                                                updateCurrentStep { it.copy(durationMs = v) }
                                            },
                                            minValue = 0L,
                                            maxValue = 60_000L,
                                            maxDigits = 5,
                                            resetKey = step.id,
                                            onFocusChange = onKeyboardFocusChanged,
                                            modifier = Modifier.weight(0.65f)
                                        )
                                    }

                                    val curScale = step.overlayScale.coerceIn(0.35f, 3.0f)
                                    val curX = (step.posX ?: rule.overlayPosX).coerceIn(0.05f, 0.95f)
                                    val curY = (step.posY ?: rule.overlayPosY).coerceIn(0.05f, 0.95f)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(DarkBackground.copy(alpha = 0.7f))
                                            .padding(horizontal = 5.dp, vertical = 3.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        PrecisionStepperBar(
                                            label = "Ukuran",
                                            valueText = "${(curScale * 100).roundToInt()}%",
                                            accent = NeonCyan,
                                            onDecrementCoarse = {
                                                updateCurrentStep { it.copy(overlayScale = (curScale - 0.25f).coerceIn(0.35f, 3.0f)) }
                                            },
                                            onDecrement = {
                                                updateCurrentStep { it.copy(overlayScale = (curScale - 0.05f).coerceIn(0.35f, 3.0f)) }
                                            },
                                            onIncrement = {
                                                updateCurrentStep { it.copy(overlayScale = (curScale + 0.05f).coerceIn(0.35f, 3.0f)) }
                                            },
                                            onIncrementCoarse = {
                                                updateCurrentStep { it.copy(overlayScale = (curScale + 0.25f).coerceIn(0.35f, 3.0f)) }
                                            }
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SelectPill("55%", curScale < 0.75f) {
                                                updateCurrentStep { it.copy(overlayScale = 0.55f) }
                                            }
                                            SelectPill("100%", curScale in 0.75f..1.3f) {
                                                updateCurrentStep { it.copy(overlayScale = 1.0f) }
                                            }
                                            SelectPill("165%", curScale > 1.3f && curScale < 2.1f) {
                                                updateCurrentStep { it.copy(overlayScale = 1.65f) }
                                            }
                                            SelectPill("240%", curScale >= 2.1f) {
                                                updateCurrentStep { it.copy(overlayScale = 2.4f) }
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
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
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            PrecisionStepperBar(
                                                label = "X",
                                                valueText = "${(curX * 100).roundToInt()}%",
                                                accent = NeonOrange,
                                                onDecrement = {
                                                    updateCurrentStep { it.copy(posX = (curX - 0.04f).coerceIn(0.05f, 0.95f)) }
                                                },
                                                onIncrement = {
                                                    updateCurrentStep { it.copy(posX = (curX + 0.04f).coerceIn(0.05f, 0.95f)) }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            PrecisionStepperBar(
                                                label = "Y",
                                                valueText = "${(curY * 100).roundToInt()}%",
                                                accent = NeonPurple,
                                                onDecrement = {
                                                    updateCurrentStep { it.copy(posY = (curY - 0.04f).coerceIn(0.05f, 0.95f)) }
                                                },
                                                onIncrement = {
                                                    updateCurrentStep { it.copy(posY = (curY + 0.04f).coerceIn(0.05f, 0.95f)) }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Media tepat memusat di titik pin (${(curX * 100).roundToInt()}%, ${(curY * 100).roundToInt()}%)",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 7.3.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextChip("📍 Geser Pin", NeonPurple, Color.White) {
                                                onDragExternalStepPin(buildRule(), step.id)
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "📍 Posisi tampil tepat di pin (${(((step.posX ?: rule.overlayPosX) * 100).roundToInt())}%, ${(((step.posY ?: rule.overlayPosY) * 100).roundToInt())}%)",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 7.5.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextChip("📍 Geser Pin", NeonPurple, Color.White) {
                                            onDragExternalStepPin(buildRule(), step.id)
                                        }
                                    }
                                }
                            }

                            ActionType.RANDOM_SELECTION -> {
                                ComfortableInput(
                                    label = "Pilihan Acak (pisahkan dengan | )",
                                    value = step.paramValue,
                                    onValueChange = { raw -> updateCurrentStep { it.copy(paramValue = raw) } },
                                    onFocusChange = onKeyboardFocusChanged
                                )
                            }

                            ActionType.VOLUME_CONTROL -> {
                                val vol = step.paramValue.trim().toIntOrNull()?.coerceIn(0, 100) ?: 80
                                PrecisionStepperBar(
                                    label = "Volume SFX",
                                    valueText = "$vol%",
                                    accent = NeonCyan,
                                    onDecrementCoarse = { updateCurrentStep { it.copy(paramValue = "${(vol - 20).coerceIn(0, 100)}") } },
                                    onDecrement = { updateCurrentStep { it.copy(paramValue = "${(vol - 5).coerceIn(0, 100)}") } },
                                    onIncrement = { updateCurrentStep { it.copy(paramValue = "${(vol + 5).coerceIn(0, 100)}") } },
                                    onIncrementCoarse = { updateCurrentStep { it.copy(paramValue = "${(vol + 20).coerceIn(0, 100)}") } }
                                )
                            }

                            ActionType.HIDE_OVERLAY -> {
                                ComfortableInput(
                                    label = "Filter Hapus Overlay (ALL = semua)",
                                    value = step.paramValue,
                                    onValueChange = { raw -> updateCurrentStep { it.copy(paramValue = raw) } },
                                    onFocusChange = onKeyboardFocusChanged
                                )
                            }

                            ActionType.STOP_SOUND -> {
                                Text(
                                    text = "🔇 Menghentikan sound effect yang sedang diputar sebelum langkah berikutnya.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 7.8.sp
                                )
                            }

                            ActionType.PLAY_SOUND, ActionType.CUSTOM_BACKSOUND -> {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    listOf("CHIME", "ALERT", "FANFARE", "BASS").forEach { preset ->
                                        SelectPill(preset, step.paramValue.equals(preset, ignoreCase = true)) {
                                            updateCurrentStep { it.copy(paramValue = preset) }
                                            onPreviewSound(preset)
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(28.dp)
                                    ) {
                                        Icon(Icons.Default.AudioFile, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Upload MP3/SFX", color = Color.Black, fontSize = 8.5.sp, fontWeight = FontWeight.Black)
                                    }
                                    if (step.paramValue.isNotBlank()) {
                                        TextChip("🔊 Tes", EmeraldGreen, Color.Black) { onPreviewSound(step.paramValue) }
                                    }
                                }
                            }

                            else -> {
                                ComfortableInput(
                                    label = step.actionType.defaultParamHint.ifBlank { "Parameter" },
                                    value = step.paramValue,
                                    onValueChange = { raw -> updateCurrentStep { it.copy(paramValue = raw) } },
                                    onFocusChange = onKeyboardFocusChanged
                                )
                            }
                        }

                        // Opsi urutan eksekusi langkah (Berurutan vs Bersamaan - di Mode Simpel diatur lewat Device 1 Web)
                        if (!isDevice2SimpleMode) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
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
        }

        // Dropdown + Tambah Koreografi Dinamis (Bisa Klik/Geser/Foto Meme/Profil/Custom)
        OutlinedButton(
            onClick = { showAddMenu = !showAddMenu },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.fillMaxWidth().height(30.dp)
        ) {
            Text(
                text = if (showAddMenu) "✕ Tutup Pilihan Koreografi" else "+ Tambah Koreografi (Klik / Geser / Meme / Profil / Lainnya)",
                color = EmeraldGreen,
                fontSize = 8.6.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showAddMenu) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    TextChip("👆 Klik ML", EmeraldGreen, Color.Black) { appendDynamicStep(ActionType.SIMULATED_CLICK) }
                    TextChip("👉 Geser ML", NeonCyan, Color.Black) { appendDynamicStep(ActionType.SIMULATED_SWIPE) }
                    TextChip("⏱️ Jeda", Color(0xFF334155), Color.White) { appendDynamicStep(ActionType.DELAY) }
                    TextChip("🏠 Home→Game", NeonOrange, Color.Black) { appendDynamicStep(ActionType.HOME_BACK_GAME) }
                    TextChip("😂 Foto Meme", NeonPurple, Color.White) { appendDynamicStep(ActionType.MEME_OVERLAY) }
                    TextChip("👤 Foto Profil", Color(0xFF0284C7), Color.White) { appendDynamicStep(ActionType.PROFILE_OVERLAY) }
                    TextChip("🔊 Sound", Color(0xFF0E7490), Color.White) { appendDynamicStep(ActionType.PLAY_SOUND) }
                    TextChip(if (showMoreAddMenu) "▲ Custom" else "+ Custom", Color(0xFF1E293B), Color.White) {
                        showMoreAddMenu = !showMoreAddMenu
                    }
                }
                if (showMoreAddMenu) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        TextChip("📝 Teks", Color(0xFF1E293B), NeonCyan) { appendDynamicStep(ActionType.TEXT_OVERLAY) }
                        TextChip("✨ Animasi", Color(0xFF1E293B), EmeraldGreen) { appendDynamicStep(ActionType.ANIMATION) }
                        TextChip("🏆 Tier VIP", Color(0xFF1E293B), NeonOrange) { appendDynamicStep(ActionType.TIER_OVERLAY) }
                        TextChip("🖼️ Banner", Color(0xFF1E293B), Color.White) { appendDynamicStep(ActionType.SHOW_OVERLAY) }
                        TextChip("🎲 Random", Color(0xFF1E293B), Color.White) { appendDynamicStep(ActionType.RANDOM_SELECTION) }
                        TextChip("🎛️ Volume", Color(0xFF1E293B), Color.White) { appendDynamicStep(ActionType.VOLUME_CONTROL) }
                        TextChip("🧹 Hide", Color(0xFF1E293B), Color.White) { appendDynamicStep(ActionType.HIDE_OVERLAY) }
                        TextChip("🔇 Stop Sfx", Color(0xFF1E293B), Color.White) { appendDynamicStep(ActionType.STOP_SOUND) }
                    }
                }
            }
        }
        } // end ConceptSectionCard 3

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(
                onClick = onCancel,
                contentPadding = PaddingValues(vertical = 2.dp),
                modifier = Modifier.weight(1f).height(30.dp)
            ) {
                Text("Tutup", fontSize = 9.5.sp, color = Color(0xFFCBD5E1))
            }
            Button(
                onClick = { onSave(buildRule()) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                contentPadding = PaddingValues(vertical = 2.dp),
                modifier = Modifier.weight(1f).height(30.dp)
            ) {
                Text("Simpan", fontSize = 9.5.sp, color = Color.Black, fontWeight = FontWeight.Black)
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
        ActionType.MEME_OVERLAY -> "😂 Meme"
        ActionType.PROFILE_OVERLAY -> "👤 Profil"
        ActionType.CUSTOM_IMAGE, ActionType.SHOW_OVERLAY -> "🖼️ Overlay"
        ActionType.TEXT_OVERLAY -> "📝 Teks"
        ActionType.ANIMATION -> "✨ Animasi"
        ActionType.TIER_OVERLAY -> "🏆 Tier VIP"
        ActionType.RANDOM_SELECTION -> "🎲 Random"
        ActionType.PLAY_SOUND, ActionType.CUSTOM_BACKSOUND -> "Sound (${soundLabel(step.paramValue).take(8)})"
        else -> step.actionType.displayName
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DropdownTestSection(
    engine: UniversalTriggerEngine,
    rules: List<UniversalTriggerRule>,
    activePinRuleId: String?,
    isEngineEnabled: Boolean,
    isA11yEnabled: Boolean,
    history: List<TriggerExecutionRecord>
) {
    val context = LocalContext.current
    var selectedTestRuleId by remember(rules, activePinRuleId) {
        mutableStateOf(
            activePinRuleId?.takeIf { id -> rules.any { it.id == id } }
                ?: rules.firstOrNull()?.id
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = when {
                rules.isEmpty() -> "⚠️ Belum ada konsep! Buat dulu di tab Konsep."
                !isEngineEnabled -> "⚠️ Engine dijeda (⏸) — ketuk ⚡ di menu horizontal atas."
                !isA11yEnabled -> "⚠️ Aksesibilitas OFF — tap ML & Home→Game butuh Aksesibilitas ON."
                else -> "Pilih 1 konsep untuk tes terisolasi, atau simulasi event live:"
            },
            color = if (rules.isEmpty() || !isEngineEnabled || !isA11yEnabled) NeonOrange else Color(0xFFCBD5E1),
            fontSize = 8.5.sp
        )

        if (rules.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurface)
                    .border(0.7.dp, EmeraldGreen.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "1️⃣ Tes 1 Konsep Spesifik (Hanya menjalankan konsep dipilih):",
                    color = EmeraldGreen,
                    fontSize = 8.2.sp,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    rules.forEachIndexed { idx, r ->
                        SelectPill(
                            label = "#${idx + 1} ${r.triggerName.take(14)}",
                            selected = selectedTestRuleId == r.id
                        ) {
                            selectedTestRuleId = r.id
                        }
                    }
                }
                val chosenRule = rules.firstOrNull { it.id == selectedTestRuleId } ?: rules.first()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val resolvedGift = GiftCoinCatalog.firstGiftNameForTest(chosenRule.targetGiftName)
                            val resolvedDiamonds = GiftCoinCatalog.resolveCoinCost(resolvedGift) ?: 10
                            engine.forceTestRuleNow(
                                ruleId = chosenRule.id,
                                customEvent = LiveInteractionEvent(
                                    source = chosenRule.triggerSource,
                                    senderName = "SultanTester",
                                    quantity = if (chosenRule.enableMinimumQuantity) chosenRule.minimumQuantity else 1,
                                    diamonds = resolvedDiamonds,
                                    giftName = resolvedGift,
                                    text = "Tes ${chosenRule.triggerName}"
                                )
                            ) { result ->
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    Toast.makeText(context, "${chosenRule.triggerName}: $result", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(28.dp)
                    ) {
                        Text(
                            text = "▶ Tes \"${chosenRule.triggerName.take(18)}\"",
                            color = Color.Black,
                            fontSize = 8.8.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextChip("⏹ Stop", Color(0xFF991B1B), Color.White) {
                        engine.emergencyStop()
                        Toast.makeText(context, "⏹ Dihentikan", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        var simGiftName by remember { mutableStateOf("Mawar") }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .border(0.7.dp, NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "2️⃣ Simulasi Event Live (Sesuai saklar ON/OFF & nama koin):",
                color = NeonCyan,
                fontSize = 8.2.sp,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("Mawar", "Kopi", "Es Krim", "Bebek", "Heart Me", "Permen", "Paus Menyelam").forEach { gName ->
                    SelectPill("🎁 $gName", simGiftName.equals(gName, ignoreCase = true)) {
                        simGiftName = gName
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    UniversalTriggerSource.LIKE to "❤️ Like",
                    UniversalTriggerSource.GIFT to "🎁 $simGiftName",
                    UniversalTriggerSource.CHAT to "💬 Chat",
                    UniversalTriggerSource.FOLLOW to "➕ Follow",
                    UniversalTriggerSource.SHARE to "↗️ Share",
                    UniversalTriggerSource.MEMBER_JOIN to "👋 Join"
                ).forEach { (src, label) ->
                    TextChip(label, NeonCyan.copy(alpha = 0.25f), Color.White) {
                        if (!isEngineEnabled) engine.setEngineEnabled(true)
                        val simCoins = GiftCoinCatalog.resolveCoinCost(simGiftName) ?: 1
                        val records = engine.dispatchEvent(
                            LiveInteractionEvent(
                                id = "sim_${src.name}_${System.currentTimeMillis()}",
                                source = src,
                                senderName = "PenontonSim",
                                quantity = 1,
                                diamonds = simCoins,
                                giftName = simGiftName,
                                text = "Simulasi ${src.displayName}"
                            )
                        )
                        val msg = if (records.isEmpty()) {
                            "Tidak ada konsep aktif untuk ${src.displayName}"
                        } else {
                            "${src.displayName}: ${records.size} konsep diproses"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurface)
                    .padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
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
                        fontSize = 8.sp,
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
    val controllerUrl = "http://${relayConfig.serverHost}:${relayConfig.serverPort}/controller"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Toggle Mode Kerja: 📱 Simpel (2-HP) ↔ 🎛️ Lengkap
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .border(
                    0.7.dp,
                    if (uiState.isDevice2SimpleMode) NeonCyan.copy(alpha = 0.6f) else NeonPurple.copy(alpha = 0.6f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (uiState.isDevice2SimpleMode) "📱 Mode: 2-HP Simpel" else "🎛️ Mode: 1-HP Lengkap",
                    color = if (uiState.isDevice2SimpleMode) NeonCyan else NeonPurple,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = if (uiState.isDevice2SimpleMode) "Detail timing & OCR diatur via Web Device 1" else "Semua menu detail aktif di HP ini",
                    color = Color(0xFF94A3B8),
                    fontSize = 7.3.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SelectPill("Simpel", uiState.isDevice2SimpleMode) {
                    engine.setDevice2SimpleMode(true)
                }
                SelectPill("Lengkap", !uiState.isDevice2SimpleMode) {
                    engine.setDevice2SimpleMode(false)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Aksesibilitas Tap MLBB", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (isA11yEnabled) "Aktif — siap sentuh & Home" else "Mati — ketuk Aktifkan",
                    color = if (isA11yEnabled) EmeraldGreen else NeonOrange,
                    fontSize = 8.sp
                )
            }
            if (!isA11yEnabled) {
                TextChip("Aktifkan", NeonOrange, Color.Black) {
                    MlbbGestureAccessibilityService.openAccessibilitySettings(context)
                }
            } else {
                Text("✓ ON", color = EmeraldGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    fontSize = 9.sp,
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
            Text(
                text = "🎛️ Web Device 1: $controllerUrl",
                color = EmeraldGreen,
                fontSize = 7.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("⏸️ Jeda Sentuhan Sementara", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = uiState.isMlbbMatchActive,
                onCheckedChange = { engine.setMlbbMatchActive(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = NeonOrange)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .border(
                    0.7.dp,
                    if (uiState.blockTouchDuringChallenge) Color(0xFFDC2626).copy(alpha = 0.7f) else DarkCardBorder,
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("🔒 Blokir Sentuhan Layar Saat Challenge", color = Color.White, fontSize = 8.8.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Sentuhan manual terkunci penuh selama challenge aktif berjalan.",
                    color = Color(0xFF94A3B8),
                    fontSize = 7.3.sp
                )
            }
            Switch(
                checked = uiState.blockTouchDuringChallenge,
                onCheckedChange = { engine.setBlockTouchDuringChallenge(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFDC2626))
            )
        }

        // Delay Global Sebelum & Sesudah Aksi (hanya di Mode Lengkap)
        if (!uiState.isDevice2SimpleMode) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .border(
                    0.7.dp,
                    if (uiState.globalDelayBeforeMs > 0L || uiState.globalDelayAfterMs > 0L) NeonCyan.copy(alpha = 0.7f) else DarkCardBorder,
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "⏱️ Delay Global (Sebelum & Sesudah Aksi)",
                color = NeonCyan,
                fontSize = 8.8.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ComfortableMsInput(
                    label = "Sebelum (ms)",
                    value = uiState.globalDelayBeforeMs,
                    onValueCommitted = { v -> engine.setGlobalDelaySettings(beforeMs = v) },
                    minValue = 0L,
                    maxValue = 60_000L,
                    maxDigits = 5,
                    resetKey = "hud_global_before",
                    onFocusChange = onKeyboardFocusChanged,
                    modifier = Modifier.weight(1f)
                )
                ComfortableMsInput(
                    label = "Sesudah (ms)",
                    value = uiState.globalDelayAfterMs,
                    onValueCommitted = { v -> engine.setGlobalDelaySettings(afterMs = v) },
                    minValue = 0L,
                    maxValue = 60_000L,
                    maxDigits = 5,
                    resetKey = "hud_global_after",
                    onFocusChange = onKeyboardFocusChanged,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Kecualikan konsep dgn Delay Scan Cooldown ML",
                    color = Color(0xFFCBD5E1),
                    fontSize = 7.6.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.globalDelayExcludeCooldownRules,
                    onCheckedChange = { engine.setGlobalDelaySettings(excludeCooldownRules = it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan)
                )
            }
        }
        }

        // Area Penutup Layar Game (Custom Ukuran)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .border(
                    0.7.dp,
                    if (uiState.isCoverEditMode || uiState.gameCoverAreas.any { it.enabled }) NeonPurple.copy(alpha = 0.7f) else DarkCardBorder,
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🛡️ Tutup Area Game (${uiState.gameCoverAreas.count { it.enabled }}/${uiState.gameCoverAreas.size})",
                    color = NeonPurple,
                    fontSize = 8.8.sp,
                    fontWeight = FontWeight.Bold
                )
                TextChip(
                    label = if (uiState.isCoverEditMode) "✓ Selesai Atur" else "✥ Geser/Resize",
                    bg = if (uiState.isCoverEditMode) EmeraldGreen else Color(0xFF334155),
                    fg = if (uiState.isCoverEditMode) Color.Black else Color.White
                ) {
                    engine.setCoverEditMode(!uiState.isCoverEditMode)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextChip("+ Minimap", NeonCyan.copy(alpha = 0.22f), NeonCyan) {
                    engine.addGameCoverArea(
                        name = "Minimap",
                        posX = 0.12f,
                        posY = 0.22f,
                        widthRatio = 0.16f,
                        heightRatio = 0.26f
                    )
                    engine.setCoverEditMode(true)
                }
                TextChip("+ Chat/Lobby", NeonOrange.copy(alpha = 0.22f), NeonOrange) {
                    engine.addGameCoverArea(
                        name = "Chat/Lobby",
                        posX = 0.24f,
                        posY = 0.78f,
                        widthRatio = 0.28f,
                        heightRatio = 0.18f
                    )
                    engine.setCoverEditMode(true)
                }
                TextChip("+ Custom", EmeraldGreen.copy(alpha = 0.22f), EmeraldGreen) {
                    engine.addGameCoverArea(
                        name = "Custom ${uiState.gameCoverAreas.size + 1}",
                        posX = 0.50f,
                        posY = 0.50f,
                        widthRatio = 0.22f,
                        heightRatio = 0.22f
                    )
                    engine.setCoverEditMode(true)
                }
            }
            if (uiState.gameCoverAreas.isNotEmpty()) {
                val activeCover = uiState.gameCoverAreas.firstOrNull { it.id == uiState.activeCoverAreaId }
                    ?: uiState.gameCoverAreas.first()
                uiState.gameCoverAreas.forEach { area ->
                    val selected = area.id == activeCover.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (selected) NeonPurple.copy(alpha = 0.16f) else DarkBackground)
                            .border(
                                0.6.dp,
                                if (selected) NeonPurple else DarkCardBorder,
                                RoundedCornerShape(5.dp)
                            )
                            .clickable { engine.selectActiveCoverArea(area.id) }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${area.name} (${(area.widthRatio * 100).roundToInt()}%×${(area.heightRatio * 100).roundToInt()}%)",
                            color = if (area.enabled) Color.White else Color(0xFF64748B),
                            fontSize = 8.2.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        ActionPillButton(
                            text = if (area.enabled) "ON" else "OFF",
                            tint = if (area.enabled) EmeraldGreen else Color(0xFF64748B)
                        ) {
                            engine.toggleGameCoverAreaEnabled(area.id)
                        }
                        ConfirmDeleteActionPill(text = "✕") {
                            engine.deleteGameCoverArea(area.id)
                        }
                    }
                }
                if (!uiState.isDevice2SimpleMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PrecisionStepperBar(
                            label = "Lebar",
                            valueText = "${(activeCover.widthRatio * 100).roundToInt()}%",
                            accent = NeonCyan,
                            onDecrementCoarse = {
                                engine.updateGameCoverArea(activeCover.copy(widthRatio = (activeCover.widthRatio - 0.05f).coerceIn(0.03f, 0.96f)))
                            },
                            onDecrement = {
                                engine.updateGameCoverArea(activeCover.copy(widthRatio = (activeCover.widthRatio - 0.01f).coerceIn(0.03f, 0.96f)))
                            },
                            onIncrement = {
                                engine.updateGameCoverArea(activeCover.copy(widthRatio = (activeCover.widthRatio + 0.01f).coerceIn(0.03f, 0.96f)))
                            },
                            onIncrementCoarse = {
                                engine.updateGameCoverArea(activeCover.copy(widthRatio = (activeCover.widthRatio + 0.05f).coerceIn(0.03f, 0.96f)))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PrecisionStepperBar(
                            label = "Tinggi",
                            valueText = "${(activeCover.heightRatio * 100).roundToInt()}%",
                            accent = EmeraldGreen,
                            onDecrementCoarse = {
                                engine.updateGameCoverArea(activeCover.copy(heightRatio = (activeCover.heightRatio - 0.05f).coerceIn(0.03f, 0.96f)))
                            },
                            onDecrement = {
                                engine.updateGameCoverArea(activeCover.copy(heightRatio = (activeCover.heightRatio - 0.01f).coerceIn(0.03f, 0.96f)))
                            },
                            onIncrement = {
                                engine.updateGameCoverArea(activeCover.copy(heightRatio = (activeCover.heightRatio + 0.01f).coerceIn(0.03f, 0.96f)))
                            },
                            onIncrementCoarse = {
                                engine.updateGameCoverArea(activeCover.copy(heightRatio = (activeCover.heightRatio + 0.05f).coerceIn(0.03f, 0.96f)))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PrecisionStepperBar(
                        label = "Opacity Penutup",
                        valueText = "${(activeCover.opacity * 100).roundToInt()}%",
                        accent = NeonOrange,
                        onDecrement = {
                            engine.updateGameCoverArea(activeCover.copy(opacity = (activeCover.opacity - 0.05f).coerceIn(0.20f, 1.0f)))
                        },
                        onIncrement = {
                            engine.updateGameCoverArea(activeCover.copy(opacity = (activeCover.opacity + 0.05f).coerceIn(0.20f, 1.0f)))
                        }
                    )
                }
            }
        }

        if (!uiState.isDevice2SimpleMode) {
        // Pengaturan Ukuran & Transparansi Overlay Floating (dengan Tombol Presisi)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val cfg = uiState.floatingConfig
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preset HUD:",
                    color = NeonCyan,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
            PrecisionStepperBar(
                label = "Ukuran HUD",
                valueText = "${(cfg.hudScale * 100).roundToInt()}%",
                accent = NeonCyan,
                onDecrement = {
                    engine.updateFloatingConfig { it.copy(hudScale = (it.hudScale - 0.05f).coerceIn(0.65f, 1.35f)) }
                },
                onIncrement = {
                    engine.updateFloatingConfig { it.copy(hudScale = (it.hudScale + 0.05f).coerceIn(0.65f, 1.35f)) }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PrecisionStepperBar(
                    label = "Lebar",
                    valueText = "${(cfg.panelWidthRatio * 100).roundToInt()}%",
                    accent = EmeraldGreen,
                    onDecrement = {
                        engine.updateFloatingConfig { it.copy(panelWidthRatio = (it.panelWidthRatio - 0.04f).coerceIn(0.38f, 0.98f)) }
                    },
                    onIncrement = {
                        engine.updateFloatingConfig { it.copy(panelWidthRatio = (it.panelWidthRatio + 0.04f).coerceIn(0.38f, 0.98f)) }
                    },
                    modifier = Modifier.weight(1f)
                )
                PrecisionStepperBar(
                    label = "Opacity",
                    valueText = "${(cfg.hudOpacity * 100).roundToInt()}%",
                    accent = NeonOrange,
                    onDecrement = {
                        engine.updateFloatingConfig { it.copy(hudOpacity = (it.hudOpacity - 0.05f).coerceIn(0.35f, 1.0f)) }
                    },
                    onIncrement = {
                        engine.updateFloatingConfig { it.copy(hudOpacity = (it.hudOpacity + 0.05f).coerceIn(0.35f, 1.0f)) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💾 Backup JSON Semua", color = Color.White, fontSize = 8.8.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                onClick = onResetHudPosition,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.weight(1f).height(30.dp)
            ) {
                Text("↺ Reset", color = NeonCyan, fontSize = 8.8.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    engine.updateFloatingConfig { it.copy(hudCollapsed = true) }
                    onKeyboardFocusChanged(false)
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.weight(1f).height(30.dp)
            ) {
                Text("— Ciutkan", color = NeonOrange, fontSize = 8.8.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onHideFloatingForLive,
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.weight(1f).height(30.dp)
            ) {
                Text("👁️ Live Bersih", color = Color.White, fontSize = 8.8.sp, fontWeight = FontWeight.Black)
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
fun PrecisionStepperBar(
    label: String,
    valueText: String,
    accent: Color = NeonCyan,
    decLabel: String = "−",
    incLabel: String = "+",
    onDecrementCoarse: (() -> Unit)? = null,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onIncrementCoarse: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(DarkBackground.copy(alpha = 0.8f))
            .border(0.6.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "$label:",
            color = Color(0xFF94A3B8),
            fontSize = 7.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onDecrementCoarse != null) {
            StepperTinyButton("$decLabel$decLabel", accent, onDecrementCoarse)
        }
        StepperTinyButton(decLabel, accent, onDecrement)
        Text(
            text = valueText,
            color = accent,
            fontSize = 8.2.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        StepperTinyButton(incLabel, accent, onIncrement)
        if (onIncrementCoarse != null) {
            StepperTinyButton("$incLabel$incLabel", accent, onIncrementCoarse)
        }
    }
}

@Composable
private fun StepperTinyButton(
    symbol: String,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.18f))
            .border(0.6.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
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
            .padding(horizontal = 7.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (open) NeonCyan else Color(0xFFCBD5E1),
            fontSize = 8.6.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = if (open) NeonCyan else Color(0xFF94A3B8),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun ActionPillButton(text: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(tint.copy(alpha = 0.18f))
            .border(0.7.dp, tint.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = tint, fontSize = 9.2.sp, fontWeight = FontWeight.Black)
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
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SelectPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) NeonCyan.copy(alpha = 0.24f) else DarkSurface)
            .border(0.7.dp, if (selected) NeonCyan else DarkCardBorder, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) NeonCyan else Color(0xFFCBD5E1), fontSize = 8.2.sp, fontWeight = FontWeight.Bold)
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
            .clip(RoundedCornerShape(5.dp))
            .background(DarkSurface)
            .border(0.7.dp, DarkCardBorder, RoundedCornerShape(5.dp))
            .clickable {
                onFocusChange(true)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
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
                .defaultMinSize(minHeight = 16.dp)
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
