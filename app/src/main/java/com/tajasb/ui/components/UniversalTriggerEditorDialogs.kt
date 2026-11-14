package com.tajasb.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonPurple
import com.tajasb.interaction.AccumulationMode
import com.tajasb.interaction.ActionCategory
import com.tajasb.interaction.ActionType
import com.tajasb.interaction.ConditionField
import com.tajasb.interaction.ConditionOperator
import com.tajasb.interaction.ExecutionMode
import com.tajasb.interaction.FloatingPickerBridge
import com.tajasb.interaction.FloatingPickerMode
import com.tajasb.interaction.ResetConditionType
import com.tajasb.interaction.TriggerCondition
import com.tajasb.interaction.UniversalActionStep
import com.tajasb.interaction.UniversalPreset
import com.tajasb.interaction.UniversalTriggerRule
import com.tajasb.interaction.UniversalTriggerSource
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Seluruh editor ini dirender sebagai overlay in-tree (tanpa android Dialog / PopupWindow)
 * sehingga 100% aman dijalankan di dalam Floating WindowManager ComposeView di atas Mobile Legends.
 */
@Composable
fun FlexibleTriggerEditorOverlay(
    initialRule: UniversalTriggerRule,
    onDismiss: () -> Unit,
    onSave: (UniversalTriggerRule) -> Unit,
    onPreview: (UniversalTriggerRule) -> Unit,
    onTest: (UniversalTriggerRule) -> Unit,
    onOpenFloatingPinMode: (UniversalTriggerRule) -> Unit
) {
    val context = LocalContext.current
    var draft by remember(initialRule.id) { mutableStateOf(initialRule) }
    var showActionPicker by remember { mutableStateOf(false) }

    val pickerResult by FloatingPickerBridge.result.collectAsState()
    LaunchedEffect(pickerResult) {
        val res = pickerResult ?: return@LaunchedEffect
        if (res.requestKey.startsWith("step_")) {
            val stepId = res.requestKey.removePrefix("step_")
            draft = draft.copy(
                actionSequence = draft.actionSequence.map {
                    if (it.id == stepId) it.copy(paramValue = res.uriString) else it
                }
            )
            FloatingPickerBridge.consumeResult()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(enabled = true, onClick = {}) // Konsumsi sentuhan backdrop
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.65f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🛠️ VISUAL TRIGGER BUILDER",
                            color = NeonCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Sumber → Kondisi → Urutan Action & Posisi",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = draft.enabled,
                            onCheckedChange = { draft = draft.copy(enabled = it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmeraldGreen)
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup Editor", tint = Color.White)
                        }
                    }
                }

                // Live Visual Flow Preview Strip
                TriggerFlowPreviewStrip(rule = draft)

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. TRIGGER SOURCE
                    EditorSectionCard(title = "1. SUMBER INTERAKSI (TRIGGER SOURCE)") {
                        DarkMiniTextField(
                            value = draft.triggerName,
                            onValueChange = { draft = draft.copy(triggerName = it) },
                            label = "Nama Trigger"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TriggerSourceChipSelector(
                            selected = draft.triggerSource,
                            onSelected = { draft = draft.copy(triggerSource = it) }
                        )
                        if (!draft.triggerSource.isAvailableInRelay) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ ${draft.triggerSource.availabilityNote}",
                                color = NeonOrange,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Teruskan juga ke Virtual Camera Overlay",
                                color = Color(0xFFCBD5E1),
                                fontSize = 9.5.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = draft.broadcastToVirtualCamera,
                                onCheckedChange = { draft = draft.copy(broadcastToVirtualCamera = it) }
                            )
                        }
                    }

                    // 2. POSISI OVERLAY & PIN DI ATAS GAME
                    EditorSectionCard(
                        title = "2. POSISI DI LAYAR & UKURAN OVERLAY",
                        trailingContent = {
                            OutlinedButton(
                                onClick = { onOpenFloatingPinMode(draft) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("📍 Geser Pin di ML", fontSize = 8.5.sp, color = NeonCyan)
                            }
                        }
                    ) {
                        val updateDraftCoords: (Float, Float) -> Unit = { nx, ny ->
                            val cx = nx.coerceIn(0.05f, 0.95f)
                            val cy = ny.coerceIn(0.05f, 0.95f)
                            val fmt = String.format(java.util.Locale.US, "%.2f,%.2f", cx, cy)
                            draft = draft.copy(
                                overlayPosX = cx,
                                overlayPosY = cy,
                                actionSequence = draft.actionSequence.map { st ->
                                    if (st.actionType == ActionType.SIMULATED_CLICK) {
                                        st.copy(paramValue = fmt, posX = cx, posY = cy)
                                    } else {
                                        st.copy(posX = cx, posY = cy)
                                    }
                                }
                            )
                        }
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0A0F1E))
                                .border(0.8.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        updateDraftCoords(change.position.x / w, change.position.y / h)
                                    }
                                }
                        ) {
                            val padW = constraints.maxWidth
                            val padH = constraints.maxHeight
                            val pinX = ((padW * draft.overlayPosX) - 24).roundToInt().coerceIn(0, (padW - 48).coerceAtLeast(0))
                            val pinY = ((padH * draft.overlayPosY) - 14).roundToInt().coerceIn(0, (padH - 28).coerceAtLeast(0))
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(pinX, pinY) }
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NeonPurple.copy(alpha = 0.85f))
                                    .border(1.dp, NeonCyan, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "📍 ${(draft.overlayPosX * 100).roundToInt()}%, ${(draft.overlayPosY * 100).roundToInt()}%",
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Slider(
                                value = draft.overlayPosX,
                                onValueChange = { updateDraftCoords(it, draft.overlayPosY) },
                                valueRange = 0.05f..0.95f,
                                modifier = Modifier.weight(1f)
                            )
                            Slider(
                                value = draft.overlayPosY,
                                onValueChange = { updateDraftCoords(draft.overlayPosX, it) },
                                valueRange = 0.05f..0.95f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = "Skala Overlay: ${String.format(java.util.Locale.US, "%.1fx", draft.overlayScale)}",
                            color = Color.White,
                            fontSize = 9.sp
                        )
                        Slider(
                            value = draft.overlayScale,
                            onValueChange = { draft = draft.copy(overlayScale = it.coerceIn(0.5f, 2.0f)) },
                            valueRange = 0.5f..2.0f
                        )
                    }

                    // 3. COUNTER & ACCUMULATION SYSTEM
                    EditorSectionCard(title = "3. COUNTER & ACCUMULATION MODE") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            AccumulationMode.values().forEach { mode ->
                                val selected = draft.accumulationMode == mode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) NeonCyan.copy(alpha = 0.2f) else DarkBackground)
                                        .border(
                                            1.dp,
                                            if (selected) NeonCyan else DarkCardBorder,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { draft = draft.copy(accumulationMode = mode) }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = mode.label,
                                        color = if (selected) NeonCyan else Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(draft.accumulationMode.description, color = Color(0xFF94A3B8), fontSize = 8.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ModeFlagChip("Individual", draft.enableIndividual) { draft = draft.copy(enableIndividual = it) }
                            ModeFlagChip("Accumulation", draft.enableAccumulation) { draft = draft.copy(enableAccumulation = it) }
                            ModeFlagChip("Threshold", draft.enableThreshold) { draft = draft.copy(enableThreshold = it) }
                            ModeFlagChip("Combo", draft.enableCombo) { draft = draft.copy(enableCombo = it) }
                            ModeFlagChip("Milestone", draft.enableMilestone) { draft = draft.copy(enableMilestone = it) }
                            ModeFlagChip("Random", draft.enableRandomSelection) { draft = draft.copy(enableRandomSelection = it) }
                            ModeFlagChip("Custom", draft.enableCustomCondition) { draft = draft.copy(enableCustomCondition = it) }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NumberFieldBox(
                                label = "Threshold",
                                value = draft.thresholdValue.toString(),
                                onValueChange = {
                                    it.filter(Char::isDigit).toIntOrNull()?.let { n ->
                                        draft = draft.copy(thresholdValue = n.coerceAtLeast(1))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            NumberFieldBox(
                                label = "Combo (ms)",
                                value = draft.comboWindowMs.toString(),
                                onValueChange = {
                                    it.filter(Char::isDigit).toLongOrNull()?.let { n ->
                                        draft = draft.copy(comboWindowMs = n.coerceAtLeast(200L))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            NumberFieldBox(
                                label = "Milestone",
                                value = draft.milestoneStep.toString(),
                                onValueChange = {
                                    it.filter(Char::isDigit).toIntOrNull()?.let { n ->
                                        draft = draft.copy(milestoneStep = n.coerceAtLeast(1))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        ToggleNumberParamRow(
                            title = "Minimum Quantity per Event",
                            enabled = draft.enableMinimumQuantity,
                            onToggle = { draft = draft.copy(enableMinimumQuantity = it) },
                            value = draft.minimumQuantity.toString(),
                            onValueChange = {
                                it.filter(Char::isDigit).toIntOrNull()?.let { n ->
                                    draft = draft.copy(minimumQuantity = n.coerceAtLeast(1))
                                }
                            },
                            unit = "qty"
                        )
                    }

                    // 4. CONDITIONS BUILDER
                    EditorSectionCard(
                        title = "4. TRIGGER CONDITIONS",
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Switch(
                                    checked = draft.enableConditions,
                                    onCheckedChange = { draft = draft.copy(enableConditions = it) }
                                )
                                OutlinedButton(
                                    onClick = {
                                        val nextCond = TriggerCondition(
                                            field = ConditionField.QUANTITY,
                                            operator = ConditionOperator.GREATER_OR_EQUAL,
                                            value = "1"
                                        )
                                        draft = draft.copy(
                                            enableConditions = true,
                                            conditions = draft.conditions + nextCond
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Text("Condition", fontSize = 8.5.sp)
                                }
                            }
                        }
                    ) {
                        if (draft.conditions.isEmpty()) {
                            Text("Belum ada kondisi tambahan.", color = Color(0xFF94A3B8), fontSize = 8.5.sp)
                        } else {
                            if (!draft.enableConditions) {
                                Text(
                                    text = "Saklar Condition OFF — nilai tersimpan tetap dijaga.",
                                    color = NeonOrange,
                                    fontSize = 8.sp
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                draft.conditions.forEach { cond ->
                                    ConditionRowItem(
                                        condition = cond,
                                        onChange = { updated ->
                                            draft = draft.copy(
                                                conditions = draft.conditions.map { if (it.id == updated.id) updated else it }
                                            )
                                        },
                                        onDelete = {
                                            draft = draft.copy(
                                                conditions = draft.conditions.filterNot { it.id == cond.id }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 5. ACTION SEQUENCE BUILDER
                    EditorSectionCard(
                        title = "5. ACTION SEQUENCE (${draft.actionSequence.size})",
                        trailingContent = {
                            Button(
                                onClick = { showActionPicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Add Action", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    ) {
                        Text(
                            text = "Token: {user}, {qty}, {gift}, {coins}, {count}, {source}, {text}, {rule}",
                            color = NeonCyan,
                            fontSize = 8.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (draft.actionSequence.isEmpty()) {
                            Text("Belum ada action. Klik + Add Action.", color = Color(0xFF94A3B8), fontSize = 8.5.sp)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                draft.actionSequence.forEachIndexed { idx, step ->
                                    ActionStepCard(
                                        index = idx,
                                        total = draft.actionSequence.size,
                                        step = step,
                                        onUpdate = { updated ->
                                            draft = draft.copy(
                                                actionSequence = draft.actionSequence.map {
                                                    if (it.id == updated.id) updated else it
                                                }
                                            )
                                        },
                                        onMoveUp = {
                                            if (idx > 0) {
                                                val list = draft.actionSequence.toMutableList()
                                                val s = list.removeAt(idx)
                                                list.add(idx - 1, s)
                                                draft = draft.copy(actionSequence = list)
                                            }
                                        },
                                        onMoveDown = {
                                            if (idx < draft.actionSequence.lastIndex) {
                                                val list = draft.actionSequence.toMutableList()
                                                val s = list.removeAt(idx)
                                                list.add(idx + 1, s)
                                                draft = draft.copy(actionSequence = list)
                                            }
                                        },
                                        onDuplicate = {
                                            val list = draft.actionSequence.toMutableList()
                                            list.add(idx + 1, step.copy(id = UUID.randomUUID().toString()))
                                            draft = draft.copy(actionSequence = list)
                                        },
                                        onDelete = {
                                            draft = draft.copy(
                                                actionSequence = draft.actionSequence.filterNot { it.id == step.id }
                                            )
                                        },
                                        onPickImage = {
                                            FloatingPickerBridge.launch(
                                                context = context,
                                                mode = FloatingPickerMode.PICK_IMAGE,
                                                requestKey = "step_${step.id}"
                                            )
                                        },
                                        onPickAudio = {
                                            FloatingPickerBridge.launch(
                                                context = context,
                                                mode = FloatingPickerMode.PICK_AUDIO,
                                                requestKey = "step_${step.id}"
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 6. TIMING, PRIORITY, COOLDOWN & RESET
                    EditorSectionCard(title = "6. TIMING, PRIORITY, COOLDOWN & RESET") {
                        Text(
                            text = "Saklar OFF tidak menghapus nilai yang sudah tersimpan.",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            ToggleNumberParamRow(
                                title = "Delay Before",
                                enabled = draft.enableDelayBefore,
                                onToggle = { draft = draft.copy(enableDelayBefore = it) },
                                value = draft.delayBeforeMs.toString(),
                                onValueChange = {
                                    it.filter(Char::isDigit).toLongOrNull()?.let { v -> draft = draft.copy(delayBeforeMs = v) }
                                },
                                unit = "ms"
                            )
                            ToggleNumberParamRow(
                                title = "Delay After",
                                enabled = draft.enableDelayAfter,
                                onToggle = { draft = draft.copy(enableDelayAfter = it) },
                                value = draft.delayAfterMs.toString(),
                                onValueChange = {
                                    it.filter(Char::isDigit).toLongOrNull()?.let { v -> draft = draft.copy(delayAfterMs = v) }
                                },
                                unit = "ms"
                            )
                            ToggleNumberParamRow(
                                title = "Interval Step",
                                enabled = draft.enableInterval,
                                onToggle = { draft = draft.copy(enableInterval = it) },
                                value = draft.intervalMs.toString(),
                                onValueChange = {
                                    it.filter(Char::isDigit).toLongOrNull()?.let { v -> draft = draft.copy(intervalMs = v) }
                                },
                                unit = "ms"
                            )
                            ToggleNumberParamRow(
                                title = "Duration Overlay",
                                enabled = draft.enableDuration,
                                onToggle = { draft = draft.copy(enableDuration = it) },
                                value = draft.durationMs.toString(),
                                onValueChange = {
                                    it.filter(Char::isDigit).toLongOrNull()?.let { v ->
                                        draft = draft.copy(durationMs = v.coerceAtLeast(100L))
                                    }
                                },
                                unit = "ms"
                            )
                            ToggleNumberParamRow(
                                title = "Repeat Count",
                                enabled = draft.enableRepeat,
                                onToggle = { draft = draft.copy(enableRepeat = it) },
                                value = draft.repeatCount.toString(),
                                onValueChange = {
                                    it.filter(Char::isDigit).toIntOrNull()?.let { v ->
                                        draft = draft.copy(repeatCount = v.coerceIn(1, 30))
                                    }
                                },
                                unit = "x"
                            )
                            ToggleNumberParamRow(
                                title = "Cooldown",
                                enabled = draft.enableCooldown,
                                onToggle = { draft = draft.copy(enableCooldown = it) },
                                value = draft.cooldownMs.toString(),
                                onValueChange = {
                                    it.filter(Char::isDigit).toLongOrNull()?.let { v -> draft = draft.copy(cooldownMs = v) }
                                },
                                unit = "ms"
                            )
                            ToggleNumberParamRow(
                                title = "Priority (1-100)",
                                enabled = draft.enablePriority,
                                onToggle = { draft = draft.copy(enablePriority = it) },
                                value = draft.priority.toString(),
                                onValueChange = {
                                    it.filter(Char::isDigit).toIntOrNull()?.let { v ->
                                        draft = draft.copy(priority = v.coerceIn(1, 100))
                                    }
                                },
                                unit = "prio"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBackground)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Switch(
                                    checked = draft.enableRandomDelay,
                                    onCheckedChange = { draft = draft.copy(enableRandomDelay = it) }
                                )
                                Text(
                                    text = "Random Delay",
                                    color = if (draft.enableRandomDelay) Color.White else Color(0xFF64748B),
                                    fontSize = 9.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                NumberFieldBox(
                                    label = "Min",
                                    value = draft.randomDelayMinMs.toString(),
                                    onValueChange = {
                                        it.filter(Char::isDigit).toLongOrNull()?.let { v -> draft = draft.copy(randomDelayMinMs = v) }
                                    },
                                    modifier = Modifier.width(64.dp)
                                )
                                NumberFieldBox(
                                    label = "Max",
                                    value = draft.randomDelayMaxMs.toString(),
                                    onValueChange = {
                                        it.filter(Char::isDigit).toLongOrNull()?.let { v -> draft = draft.copy(randomDelayMaxMs = v) }
                                    },
                                    modifier = Modifier.width(64.dp)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBackground)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Reset Condition",
                                        color = if (draft.enableResetCondition) Color.White else Color(0xFF64748B),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Switch(
                                        checked = draft.enableResetCondition,
                                        onCheckedChange = { draft = draft.copy(enableResetCondition = it) }
                                    )
                                }
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    ResetConditionType.values().forEach { rc ->
                                        val sel = draft.resetCondition == rc
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (sel) NeonPurple.copy(alpha = 0.25f) else DarkSurfaceVariant)
                                                .border(0.8.dp, if (sel) NeonPurple else DarkCardBorder, RoundedCornerShape(6.dp))
                                                .clickable { draft = draft.copy(resetCondition = rc) }
                                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                        ) {
                                            Text(rc.label, color = if (sel) Color.White else Color(0xFF94A3B8), fontSize = 8.sp)
                                        }
                                    }
                                }
                                if (draft.resetCondition == ResetConditionType.AFTER_IDLE_TIMEOUT) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    NumberFieldBox(
                                        label = "Idle Reset Timeout (ms)",
                                        value = draft.resetTimeoutMs.toString(),
                                        onValueChange = {
                                            it.filter(Char::isDigit).toLongOrNull()?.let { v ->
                                                draft = draft.copy(resetTimeoutMs = v.coerceAtLeast(1000L))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Bar
                HorizontalDivider(color = DarkCardBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onPreview(draft) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Preview", fontSize = 9.5.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            onSave(draft)
                            onTest(draft)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Test", fontSize = 9.5.sp)
                    }
                    Button(
                        onClick = { onSave(draft) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.weight(1.2f),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text("Simpan Rule", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }
        }

        if (showActionPicker) {
            ActionLibraryPickerOverlay(
                onDismiss = { showActionPicker = false },
                onSelectAction = { type ->
                    val defaultParam = when (type) {
                        ActionType.TEXT_OVERLAY -> "{user} • {source} x{qty}"
                        ActionType.SHOW_OVERLAY -> "Trigger: {rule}"
                        ActionType.PROFILE_OVERLAY -> "{user}"
                        ActionType.TIER_OVERLAY -> "VIP • {user}"
                        ActionType.MEME_OVERLAY -> "MEME {user}!"
                        ActionType.ANIMATION -> "BOUNCE"
                        ActionType.CHANGE_SCENE -> "SCENE_ARENA"
                        ActionType.PLAY_SOUND -> "FANFARE"
                        ActionType.VOLUME_CONTROL -> "80"
                        ActionType.AUDIO_SEQUENCE -> "CHIME,ALERT,FANFARE"
                        ActionType.DELAY, ActionType.WAIT, ActionType.DURATION -> "1000"
                        ActionType.RANDOM_SELECTION -> "Opsi A|Opsi B|Opsi C"
                        ActionType.SIMULATED_CLICK -> String.format(java.util.Locale.US, "%.2f,%.2f", draft.overlayPosX, draft.overlayPosY)
                        ActionType.SIMULATED_SWIPE -> "RIGHT"
                        else -> ""
                    }
                    draft = draft.copy(
                        actionSequence = draft.actionSequence + UniversalActionStep(
                            actionType = type,
                            paramValue = defaultParam,
                            posX = draft.overlayPosX,
                            posY = draft.overlayPosY
                        )
                    )
                    showActionPicker = false
                }
            )
        }
    }
}

@Composable
private fun TriggerFlowPreviewStrip(rule: UniversalTriggerRule) {
    val activeConds = if (rule.enableConditions) rule.conditions.count { it.enabled } else 0
    val activeSteps = rule.actionSequence.filter { it.enabled }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        FlowBadge(text = rule.triggerSource.displayName.uppercase(), color = NeonCyan)
        Text("→", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        FlowBadge(
            text = "${rule.accumulationMode.label}${if (activeConds > 0) " + $activeConds Syarat" else ""}",
            color = NeonOrange
        )
        Text("→", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        if (activeSteps.isEmpty()) {
            FlowBadge(text = "Belum ada Action", color = Color(0xFF64748B))
        } else {
            activeSteps.forEachIndexed { i, step ->
                if (i > 0) {
                    Text(
                        text = if (step.executionMode == ExecutionMode.PARALLEL) "‖" else "→",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
                FlowBadge(text = step.actionType.displayName, color = EmeraldGreen)
            }
        }
    }
}

@Composable
private fun FlowBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.16f))
            .border(0.7.dp, color.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun ActionLibraryPickerOverlay(
    onDismiss: () -> Unit,
    onSelectAction: (ActionType) -> Unit
) {
    var selectedCat by remember { mutableStateOf(ActionCategory.VISUAL) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(enabled = true, onClick = {})
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.75f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📚 ACTION LIBRARY", color = NeonPurple, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ActionCategory.values().forEach { cat ->
                        val active = selectedCat == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) NeonPurple.copy(alpha = 0.25f) else DarkBackground)
                                .border(0.8.dp, if (active) NeonPurple else DarkCardBorder, RoundedCornerShape(6.dp))
                                .clickable { selectedCat = cat }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = cat.label,
                                color = if (active) Color.White else Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (selectedCat == ActionCategory.INTERACTION_TEST) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonOrange.copy(alpha = 0.14f))
                            .border(0.7.dp, NeonOrange.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "🛡️ Aman untuk MLBB: Aksi ini hanya berjalan pada simulasi preview internal dan diblokir otomatis saat Match MLBB aktif.",
                            color = NeonOrange,
                            fontSize = 8.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ActionType.values().filter { it.category == selectedCat }.forEach { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBackground)
                                .border(0.8.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                                .clickable { onSelectAction(action) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(action.displayName, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(action.defaultParamHint, color = Color(0xFF94A3B8), fontSize = 8.sp)
                            }
                            Icon(Icons.Default.Add, contentDescription = "Tambah", tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionRowItem(
    condition: TriggerCondition,
    onChange: (TriggerCondition) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkBackground)
            .border(0.8.dp, DarkCardBorder, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Switch(checked = condition.enabled, onCheckedChange = { onChange(condition.copy(enabled = it)) })
                Text("Syarat Aktif", color = Color(0xFFCBD5E1), fontSize = 9.sp)
            }
            ConfirmDeleteIconButton(
                contentDescriptionText = "Hapus Condition",
                size = 26.dp,
                iconSize = 15.dp,
                onConfirm = onDelete
            )
        }
        // Inline chip selector untuk Field (bebas PopupWindow)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ConditionField.values().forEach { f ->
                val sel = condition.field == f
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (sel) NeonCyan.copy(alpha = 0.22f) else DarkSurfaceVariant)
                        .border(0.7.dp, if (sel) NeonCyan else DarkCardBorder, RoundedCornerShape(5.dp))
                        .clickable { onChange(condition.copy(field = f)) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(f.label, color = if (sel) NeonCyan else Color(0xFF94A3B8), fontSize = 8.sp)
                }
            }
        }
        // Inline chip selector untuk Operator
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ConditionOperator.values().forEach { op ->
                val sel = condition.operator == op
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (sel) NeonPurple.copy(alpha = 0.25f) else DarkSurfaceVariant)
                        .border(0.7.dp, if (sel) NeonPurple else DarkCardBorder, RoundedCornerShape(5.dp))
                        .clickable { onChange(condition.copy(operator = op)) }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(op.label, color = if (sel) Color.White else Color(0xFF94A3B8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        DarkMiniTextField(
            value = condition.value,
            onValueChange = { onChange(condition.copy(value = it)) },
            label = "Nilai Syarat (angka / teks / true)"
        )
    }
}

@Composable
private fun ActionStepCard(
    index: Int,
    total: Int,
    step: UniversalActionStep,
    onUpdate: (UniversalActionStep) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onPickImage: () -> Unit,
    onPickAudio: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkBackground)
            .border(
                0.8.dp,
                if (step.enabled) NeonPurple.copy(alpha = 0.5f) else DarkCardBorder,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Switch(checked = step.enabled, onCheckedChange = { onUpdate(step.copy(enabled = it)) })
                Column {
                    Text(
                        text = "#${index + 1} ${step.actionType.displayName}",
                        color = if (step.enabled) Color.White else Color(0xFF64748B),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(step.actionType.category.label, color = NeonPurple, fontSize = 7.5.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Naik", tint = Color(0xFFCBD5E1), modifier = Modifier.size(13.dp))
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Turun", tint = Color(0xFFCBD5E1), modifier = Modifier.size(13.dp))
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplikat", tint = NeonCyan, modifier = Modifier.size(13.dp))
                }
                ConfirmDeleteIconButton(
                    contentDescriptionText = "Hapus",
                    size = 24.dp,
                    iconSize = 13.dp,
                    onConfirm = onDelete
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Eksekusi:", color = Color(0xFF94A3B8), fontSize = 8.sp)
            ExecutionMode.values().forEach { mode ->
                val sel = step.executionMode == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (sel) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceVariant)
                        .border(0.7.dp, if (sel) NeonCyan else DarkCardBorder, RoundedCornerShape(5.dp))
                        .clickable { onUpdate(step.copy(executionMode = mode)) }
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(mode.label, color = if (sel) NeonCyan else Color(0xFF94A3B8), fontSize = 8.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            DarkMiniTextField(
                value = step.paramValue,
                onValueChange = { onUpdate(step.copy(paramValue = it)) },
                label = step.actionType.defaultParamHint,
                modifier = Modifier.weight(1f)
            )
            if (step.actionType == ActionType.CUSTOM_IMAGE) {
                OutlinedButton(
                    onClick = onPickImage,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Pilih Gambar", modifier = Modifier.size(15.dp))
                }
            } else if (step.actionType == ActionType.PLAY_SOUND || step.actionType == ActionType.CUSTOM_BACKSOUND) {
                OutlinedButton(
                    onClick = onPickAudio,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.AudioFile, contentDescription = "Pilih Audio", modifier = Modifier.size(15.dp))
                }
            }
        }

        if (step.actionType.category == ActionCategory.VISUAL) {
            DarkMiniTextField(
                value = step.secondaryValue,
                onValueChange = { onUpdate(step.copy(secondaryValue = it)) },
                label = "Subjudul / Catatan Opsional"
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            NumberFieldBox(
                label = "Delay",
                value = step.delayMs.toString(),
                onValueChange = { it.filter(Char::isDigit).toLongOrNull()?.let { v -> onUpdate(step.copy(delayMs = v)) } },
                modifier = Modifier.weight(1f)
            )
            NumberFieldBox(
                label = "Durasi",
                value = step.durationMs.toString(),
                onValueChange = { raw ->
                    val minDur = if (step.actionType == ActionType.SIMULATED_CLICK) 0L else 60L
                    raw.filter(Char::isDigit).toLongOrNull()?.let { v -> onUpdate(step.copy(durationMs = v.coerceAtLeast(minDur))) }
                },
                modifier = Modifier.weight(1f)
            )
            NumberFieldBox(
                label = "Ulang",
                value = step.repeatCount.toString(),
                onValueChange = { it.filter(Char::isDigit).toIntOrNull()?.let { v -> onUpdate(step.copy(repeatCount = v.coerceIn(1, 20))) } },
                modifier = Modifier.weight(0.8f)
            )
        }
    }
}

@Composable
fun UniversalPresetEditorOverlay(
    initialPreset: UniversalPreset,
    onDismiss: () -> Unit,
    onSave: (UniversalPreset) -> Unit
) {
    var draft by remember(initialPreset.id) { mutableStateOf(initialPreset) }
    var showActionPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(enabled = true, onClick = {})
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.75f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧩 EDITOR PRESET UNIVERSAL", color = NeonPurple, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Preset bersifat universal dan bisa dipasang ke Like, Follow, Share, Gift, maupun sumber lain.",
                        color = Color(0xFF94A3B8),
                        fontSize = 8.5.sp
                    )
                    DarkMiniTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = "Nama Preset"
                    )
                    DarkMiniTextField(
                        value = draft.description,
                        onValueChange = { draft = draft.copy(description = it) },
                        label = "Deskripsi Singkat"
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AccumulationMode.values().forEach { mode ->
                            val sel = draft.accumulationMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) NeonPurple.copy(alpha = 0.25f) else DarkBackground)
                                    .border(0.8.dp, if (sel) NeonPurple else DarkCardBorder, RoundedCornerShape(6.dp))
                                    .clickable { draft = draft.copy(accumulationMode = mode) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(mode.label, color = if (sel) Color.White else Color(0xFF94A3B8), fontSize = 8.5.sp)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NumberFieldBox(
                            label = "Threshold",
                            value = draft.thresholdValue.toString(),
                            onValueChange = { it.filter(Char::isDigit).toIntOrNull()?.let { v -> draft = draft.copy(thresholdValue = v.coerceAtLeast(1)) } },
                            modifier = Modifier.weight(1f)
                        )
                        NumberFieldBox(
                            label = "Durasi",
                            value = draft.durationMs.toString(),
                            onValueChange = { it.filter(Char::isDigit).toLongOrNull()?.let { v -> draft = draft.copy(durationMs = v.coerceAtLeast(100L)) } },
                            modifier = Modifier.weight(1f)
                        )
                        NumberFieldBox(
                            label = "Cooldown",
                            value = draft.cooldownMs.toString(),
                            onValueChange = { it.filter(Char::isDigit).toLongOrNull()?.let { v -> draft = draft.copy(cooldownMs = v) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Action Preset (${draft.actionSequence.size})", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { showActionPicker = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                            Text("Add Action", fontSize = 8.5.sp)
                        }
                    }
                    draft.actionSequence.forEachIndexed { idx, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBackground)
                                .border(0.7.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("#${idx + 1} ${step.actionType.displayName}", color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                DarkMiniTextField(
                                    value = step.paramValue,
                                    onValueChange = { txt ->
                                        draft = draft.copy(
                                            actionSequence = draft.actionSequence.map {
                                                if (it.id == step.id) it.copy(paramValue = txt) else it
                                            }
                                        )
                                    },
                                    label = step.actionType.defaultParamHint
                                )
                            }
                            ConfirmDeleteIconButton(
                                contentDescriptionText = "Hapus",
                                size = 26.dp,
                                iconSize = 15.dp
                            ) {
                                draft = draft.copy(actionSequence = draft.actionSequence.filterNot { it.id == step.id })
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkCardBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Batal", fontSize = 9.5.sp)
                    }
                    Button(
                        onClick = { onSave(draft.copy(name = draft.name.trim().ifBlank { "Preset Tanpa Nama" })) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Simpan Preset", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showActionPicker) {
            ActionLibraryPickerOverlay(
                onDismiss = { showActionPicker = false },
                onSelectAction = { type ->
                    draft = draft.copy(actionSequence = draft.actionSequence + UniversalActionStep(actionType = type))
                    showActionPicker = false
                }
            )
        }
    }
}

@Composable
fun UniversalPresetAssignOverlay(
    preset: UniversalPreset,
    onDismiss: () -> Unit,
    onAssign: (UniversalTriggerSource, String) -> Unit
) {
    var selectedSource by remember(preset.id) { mutableStateOf(UniversalTriggerSource.LIKE) }
    var triggerName by remember(preset.id) { mutableStateOf("${preset.name} • ${selectedSource.displayName}") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(enabled = true, onClick = {})
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔗 ASSIGN PRESET KE TRIGGER", color = NeonCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                Text("Pilih sumber interaksi untuk preset '${preset.name}':", color = Color(0xFFCBD5E1), fontSize = 9.sp)
                TriggerSourceChipSelector(
                    selected = selectedSource,
                    onSelected = {
                        selectedSource = it
                        triggerName = "${preset.name} • ${it.displayName}"
                    }
                )
                DarkMiniTextField(value = triggerName, onValueChange = { triggerName = it }, label = "Nama Trigger Rule")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Batal", fontSize = 9.5.sp)
                    }
                    Button(
                        onClick = { onAssign(selectedSource, triggerName) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pasang Trigger", fontSize = 9.5.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun UniversalPresetImportPasteOverlay(
    onDismiss: () -> Unit,
    onImportJson: (String) -> Unit
) {
    var rawText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(enabled = true, onClick = {})
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("📥 TEMPEL JSON PRESET", color = NeonCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("JSON Preset", fontSize = 9.5.sp) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Batal", fontSize = 9.5.sp)
                    }
                    Button(
                        onClick = { onImportJson(rawText) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import", fontSize = 9.5.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TriggerSourceChipSelector(
    selected: UniversalTriggerSource,
    onSelected: (UniversalTriggerSource) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        UniversalTriggerSource.values().forEach { src ->
            val sel = selected == src
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (sel) NeonCyan.copy(alpha = 0.22f) else DarkBackground)
                    .border(0.8.dp, if (sel) NeonCyan else DarkCardBorder, RoundedCornerShape(6.dp))
                    .clickable { onSelected(src) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = src.displayName,
                    color = if (sel) NeonCyan else Color(0xFF94A3B8),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.7f)),
        border = BorderStroke(0.8.dp, DarkCardBorder)
    ) {
        Column(modifier = Modifier.padding(9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = NeonCyan,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                trailingContent?.invoke()
            }
            Spacer(modifier = Modifier.height(5.dp))
            content()
        }
    }
}

@Composable
private fun ModeFlagChip(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) EmeraldGreen.copy(alpha = 0.2f) else DarkBackground)
            .border(0.8.dp, if (checked) EmeraldGreen else DarkCardBorder, RoundedCornerShape(6.dp))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(
            text = "${if (checked) "✓ " else ""}$label",
            color = if (checked) EmeraldGreen else Color(0xFF94A3B8),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ToggleNumberParamRow(
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Switch(checked = enabled, onCheckedChange = onToggle)
        Text(
            text = title,
            color = if (enabled) Color.White else Color(0xFF64748B),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = { Text(unit, fontSize = 8.sp, color = Color(0xFF94A3B8)) },
            modifier = Modifier.width(98.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = if (enabled) Color.White else Color(0xFF64748B),
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = DarkCardBorder
            )
        )
    }
}

@Composable
private fun NumberFieldBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = DarkCardBorder
        )
    )
}

@Composable
private fun DarkMiniTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = DarkCardBorder
        )
    )
}
