package com.tajasb.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonPurple
import com.tajasb.interaction.ActionType
import com.tajasb.interaction.ExecutionMode
import com.tajasb.interaction.FloatingTriggerOverlayService
import com.tajasb.interaction.FullAppBackupManager
import com.tajasb.interaction.InstalledAppInfo
import com.tajasb.interaction.InstalledAppSearcher
import com.tajasb.interaction.MlbbGestureAccessibilityService
import com.tajasb.interaction.UniversalActionStep
import com.tajasb.interaction.UniversalTriggerEngine
import com.tajasb.interaction.UniversalTriggerRule
import com.tajasb.interaction.UniversalTriggerSource
import com.tajasb.ui.components.ConfirmDeleteTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Layar Tab MLBB:
 * 1) Cek 2 izin utama: Overlay (Pin + HUD mini) & Aksesibilitas (Tap/Swipe nyata ke tombol MLBB).
 * 2) Saklar HUD Floating Minimalis atau langsung Mode Live Bersih tanpa floating.
 */
@Composable
fun UniversalInteractionScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { UniversalTriggerEngine.getInstance(context) }
    val uiState by engine.state.collectAsState()
    val a11yConnected by MlbbGestureAccessibilityService.isConnected.collectAsState()

    var hasOverlayPermission by remember {
        mutableStateOf(FloatingTriggerOverlayService.canDrawOverlays(context))
    }
    var hasAccessibilityPermission by remember {
        mutableStateOf(
            a11yConnected || MlbbGestureAccessibilityService.isAccessibilityServiceEnabled(context)
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = FloatingTriggerOverlayService.canDrawOverlays(context)
                hasAccessibilityPermission =
                    MlbbGestureAccessibilityService.peekInstance() != null ||
                        MlbbGestureAccessibilityService.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val effectiveA11yReady = a11yConnected || hasAccessibilityPermission

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = FloatingTriggerOverlayService.canDrawOverlays(context)
        hasOverlayPermission = granted
        if (granted) {
            FloatingTriggerOverlayService.start(context, startInEditMode = false, openSettingsPanel = false)
            Toast.makeText(context, "⚔️ Floating minimalis aktif!", Toast.LENGTH_SHORT).show()
        }
    }

    val requestFloatingToggle: (Boolean) -> Unit = { turnOn ->
        if (!turnOn) {
            FloatingTriggerOverlayService.stop(context)
        } else if (!FloatingTriggerOverlayService.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            FloatingTriggerOverlayService.start(context, startInEditMode = false, openSettingsPanel = false)
            Toast.makeText(
                context,
                "⚔️ Tekan + / 📍 di floating untuk pasang pin di tombol MLBB",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(
                1.dp,
                if (uiState.isFloatingOverlayRunning && effectiveA11yReady) {
                    EmeraldGreen.copy(alpha = 0.7f)
                } else {
                    NeonCyan.copy(alpha = 0.5f)
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚔️ MLBB AUTO TRIGGER",
                            color = NeonCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = when {
                                uiState.isFloatingOverlayRunning && uiState.isFloatingHiddenForLive ->
                                    "Mode Live Bersih aktif (tanpa floating di layar)"
                                uiState.isFloatingOverlayRunning ->
                                    "Floating minimalis aktif — siap atur pin di luar panel"
                                else ->
                                    "Nyalakan untuk atur Pin Trigger di atas Mobile Legends"
                            },
                            color = if (uiState.isFloatingOverlayRunning) EmeraldGreen else Color(0xFF94A3B8),
                            fontSize = 9.5.sp
                        )
                    }
                    Switch(
                        checked = uiState.isFloatingOverlayRunning,
                        onCheckedChange = requestFloatingToggle,
                        modifier = Modifier.testTag("mlbb_floating_master_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldGreen
                        )
                    )
                }

                // Step 1: Accessibility permission (wajib agar tombol MLBB tersentuh)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkBackground)
                        .border(
                            0.8.dp,
                            if (effectiveA11yReady) EmeraldGreen.copy(alpha = 0.55f) else NeonOrange.copy(alpha = 0.8f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (effectiveA11yReady) "✅ Aksesibilitas Tap MLBB Aktif" else "⚠️ Wajib: Aksesibilitas Tap MLBB",
                            color = if (effectiveA11yReady) EmeraldGreen else NeonOrange,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (effectiveA11yReady) {
                                "Siap mengirim tap/swipe nyata ke tombol Mobile Legends."
                            } else {
                                "Tanpa izin ini, sentuhan tidak bisa masuk ke game MLBB."
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp
                        )
                    }
                    if (!effectiveA11yReady) {
                        Button(
                            onClick = { MlbbGestureAccessibilityService.openAccessibilitySettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                            modifier = Modifier.testTag("grant_a11y_perm_btn")
                        ) {
                            Text("Aktifkan", color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Step 2: Overlay permission
                if (!hasOverlayPermission) {
                    Button(
                        onClick = { requestFloatingToggle(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        modifier = Modifier.fillMaxWidth().testTag("grant_overlay_perm_btn")
                    ) {
                        Text(
                            text = "🪟 Aktifkan Izin Tampil di Atas Aplikasi Lain",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusMiniTile(
                        label = "Pin Target",
                        value = "${uiState.rules.count { it.enabled }}/${uiState.rules.size}",
                        accent = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    StatusMiniTile(
                        label = "Tap ML",
                        value = if (effectiveA11yReady) "SIAP" else "OFF",
                        accent = if (effectiveA11yReady) EmeraldGreen else NeonOrange,
                        modifier = Modifier.weight(1f)
                    )
                    StatusMiniTile(
                        label = "Jeda Tap",
                        value = if (uiState.isMlbbMatchActive) "JEDA" else "ON",
                        accent = if (uiState.isMlbbMatchActive) NeonOrange else EmeraldGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkBackground)
                        .border(
                            0.8.dp,
                            if (uiState.blockTouchDuringChallenge) Color(0xFFDC2626).copy(alpha = 0.7f) else DarkCardBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔒 Blokir Sentuhan Layar Saat Challenge",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sentuhan manual diblokir sementara selama challenge berjalan agar combo/serangan tidak mengganggu trigger.",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp
                        )
                    }
                    Switch(
                        checked = uiState.blockTouchDuringChallenge,
                        onCheckedChange = { engine.setBlockTouchDuringChallenge(it) },
                        modifier = Modifier.testTag("block_touch_during_challenge_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFDC2626)
                        )
                    )
                }

                if (hasOverlayPermission) {
                    val needShowFloating = uiState.isFloatingOverlayRunning &&
                        (uiState.isFloatingHiddenForLive || uiState.floatingConfig.hudCollapsed)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                FloatingTriggerOverlayService.start(
                                    context = context,
                                    startInEditMode = true,
                                    openSettingsPanel = false,
                                    hideFloatingForLive = false
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📍 Pasang Pin di ML", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                        if (needShowFloating) {
                            Button(
                                onClick = {
                                    FloatingTriggerOverlayService.start(
                                        context = context,
                                        startInEditMode = false,
                                        openSettingsPanel = false,
                                        hideFloatingForLive = false
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                modifier = Modifier.weight(1f).testTag("show_floating_btn")
                            ) {
                                Text("👁️ Tampilkan Floating", fontSize = 9.5.sp, color = Color.Black, fontWeight = FontWeight.Black)
                            }
                        } else {
                            Button(
                                onClick = {
                                    FloatingTriggerOverlayService.start(
                                        context = context,
                                        startInEditMode = false,
                                        openSettingsPanel = false,
                                        hideFloatingForLive = true
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                modifier = Modifier.weight(1f).testTag("start_hidden_live_btn")
                            ) {
                                Text("👁️ Live Tanpa Floating", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (uiState.isFloatingOverlayRunning) {
                        OutlinedButton(
                            onClick = {
                                FloatingTriggerOverlayService.resetHudPosition(context)
                                Toast.makeText(context, "↺ Posisi floating dikembalikan ke area aman", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("reset_hud_pos_btn")
                        ) {
                            Text("↺ Reset Posisi Floating (Area Aman)", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkBackground)
                        .border(0.7.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Alur Pakai Cepat (1 HP atau 2 HP + Termux):",
                        color = NeonCyan,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1. Aktifkan Aksesibilitas Tap MLBB & Izin Tampil di Atas Aplikasi.\n" +
                            "2. Buka Mobile Legends, tekan + atau 📍 di floating minimalis untuk menggeser pin ⊕ tepat ke tombol ML (Skill/Recall/Emote).\n" +
                            "3. Jika pakai 2 HP: jalankan server Termux di HP 1, lalu hubungkan IP Termux dari Tab Termux atau sub-menu ☰ → Live.\n" +
                            "4. Saat live mulai, tekan 👁️ agar floating & pin hilang bersih sementara sentuhan otomatis tetap berjalan.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 8.5.sp,
                        lineHeight = 12.5.sp
                    )
                }
            }
        }

        // Daftar Challenge & Koreografi (bisa ubah nama challenge & koreografi langsung di aplikasi)
        var editingRuleInApp by remember { mutableStateOf<UniversalTriggerRule?>(null) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎯 Challenge & Koreografi (${uiState.rules.size})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = {
                            val created = engine.createEmptyConcept(UniversalTriggerSource.GIFT)
                            editingRuleInApp = created
                        },
                        modifier = Modifier.testTag("inapp_add_challenge_btn")
                    ) {
                        Text("+ Konsep", fontSize = 9.5.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    }
                }

                if (uiState.rules.isEmpty()) {
                    Text(
                        text = "Belum ada konsep. Tekan '+ Konsep' di sini atau '+' di floating HUD (default kosong, bebas tambah Klik / Geser / Home→Game / Jeda / Overlay / Sound).",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.5.sp
                    )
                } else {
                    uiState.rules.forEachIndexed { idx, rule ->
                        val activeSteps = rule.actionSequence.filter { it.enabled }
                        val choreoSummary = if (activeSteps.isEmpty()) {
                            "Kosong (belum ada koreografi)"
                        } else {
                            activeSteps.mapIndexed { sIdx, st ->
                                val base = st.stepName.trim().ifBlank { st.actionType.displayName }
                                "#${sIdx + 1} $base"
                            }.joinToString(" → ")
                        }
                        val accent = Color(rule.accentColorHex)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkBackground)
                                .border(0.8.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .clickable { editingRuleInApp = rule }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${idx + 1}", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${rule.triggerSource.displayName} → ${rule.triggerName}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = choreoSummary,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = { editingRuleInApp = rule },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("edit_challenge_${rule.id}")
                            ) {
                                Text("✎ Ubah", fontSize = 9.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                            ConfirmDeleteTextButton(
                                label = "Hapus",
                                fontSize = 9.sp,
                                horizontalPadding = 8.dp,
                                verticalPadding = 7.dp,
                                modifier = Modifier.testTag("delete_challenge_${rule.id}")
                            ) {
                                engine.deleteRule(rule.id)
                            }
                        }
                    }
                }
            }
        }

        // Card Backup & Restore Semua Settingan JSON
        var showPasteBackupDialog by remember { mutableStateOf(false) }
        var pasteBackupText by remember { mutableStateOf("") }

        val exportFullBackupLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                try {
                    val json = FullAppBackupManager.exportFullBackupJson(context)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "✅ Semua settingan berhasil dibackup ke JSON", Toast.LENGTH_LONG).show()
                } catch (t: Throwable) {
                    Toast.makeText(context, "❌ Gagal backup: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val importFullBackupLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    FullAppBackupManager.importFullBackupJson(context, raw).fold(
                        onSuccess = { msg -> Toast.makeText(context, "✅ $msg", Toast.LENGTH_LONG).show() },
                        onFailure = { err -> Toast.makeText(context, "❌ ${err.message}", Toast.LENGTH_LONG).show() }
                    )
                } catch (t: Throwable) {
                    Toast.makeText(context, "❌ Gagal baca file backup: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💾 Backup & Restore JSON (Semua Settingan)",
                    color = NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mencakup semua Pin Challenge, Koreografi, Settingan Cooldown ML, Kunci Sentuh, Posisi HUD, serta pengaturan Server Termux & Event Overlay.",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            exportFullBackupLauncher.launch("streambattle_backup_${System.currentTimeMillis() / 1000}.json")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.weight(1f).testTag("export_full_backup_btn")
                    ) {
                        Text("📤 Ekspor File", color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                    }
                    Button(
                        onClick = { importFullBackupLauncher.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier.weight(1f).testTag("import_full_backup_btn")
                    ) {
                        Text("📥 Restore File", color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val json = FullAppBackupManager.exportFullBackupJson(context)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("StreamBattle Backup JSON", json))
                            Toast.makeText(context, "📋 JSON semua settingan disalin ke clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📋 Salin JSON", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            pasteBackupText = ""
                            showPasteBackupDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📥 Tempel / Paste JSON", color = NeonOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showPasteBackupDialog) {
            AlertDialog(
                onDismissRequest = { showPasteBackupDialog = false },
                containerColor = DarkSurface,
                title = {
                    Text("Tempel JSON Backup Semua Settingan", color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    OutlinedTextField(
                        value = pasteBackupText,
                        onValueChange = { pasteBackupText = it },
                        label = { Text("Paste JSON di sini", fontSize = 10.sp) },
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            FullAppBackupManager.importFullBackupJson(context, pasteBackupText).fold(
                                onSuccess = { msg ->
                                    showPasteBackupDialog = false
                                    Toast.makeText(context, "✅ $msg", Toast.LENGTH_LONG).show()
                                },
                                onFailure = { err ->
                                    Toast.makeText(context, "❌ ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Text("Pulihkan", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasteBackupDialog = false }) {
                        Text("Batal", color = Color(0xFF94A3B8))
                    }
                }
            )
        }

        editingRuleInApp?.let { targetRule ->
            EditChallengeAndChoreographyDialog(
                rule = targetRule,
                onDismiss = { editingRuleInApp = null },
                onSave = { updated ->
                    engine.addOrUpdateRule(updated)
                    editingRuleInApp = null
                    Toast.makeText(context, "✅ ${updated.triggerName} disimpan", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditChallengeAndChoreographyDialog(
    rule: UniversalTriggerRule,
    onDismiss: () -> Unit,
    onSave: (UniversalTriggerRule) -> Unit
) {
    var draftChallengeName by remember(rule.id) { mutableStateOf(rule.triggerName) }
    var draftSteps by remember(rule.id) { mutableStateOf(rule.actionSequence) }
    var draftEnableVisualCooldown by remember(rule.id) { mutableStateOf(rule.enableVisualCooldownWait) }
    var draftCooldownWidth by remember(rule.id) { mutableStateOf(rule.cooldownAreaWidthRatio.coerceIn(0.025f, 0.45f)) }
    var draftCooldownHeight by remember(rule.id) { mutableStateOf(rule.cooldownAreaHeightRatio.coerceIn(0.025f, 0.55f)) }
    var draftCooldownOffsetX by remember(rule.id) { mutableStateOf(rule.cooldownAreaOffsetX.coerceIn(-0.30f, 0.30f)) }
    var draftCooldownOffsetY by remember(rule.id) { mutableStateOf(rule.cooldownAreaOffsetY.coerceIn(-0.30f, 0.30f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = "Ubah Konsep & Koreografi",
                color = NeonCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draftChallengeName,
                    onValueChange = { draftChallengeName = it },
                    label = { Text("Nama Konsep / Challenge", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_challenge_name_input")
                )

                Text(
                    text = "Koreografi (${draftSteps.size} langkah):",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                if (draftSteps.isEmpty()) {
                    Text(
                        text = "Kosong — tambahkan koreografi sesuai kebutuhan di bawah.",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.5.sp
                    )
                }

                draftSteps.forEachIndexed { idx, step ->
                    fun updateThisStep(transform: (UniversalActionStep) -> UniversalActionStep) {
                        draftSteps = draftSteps.map { if (it.id == step.id) transform(it) else it }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .border(0.7.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${idx + 1} ${step.actionType.displayName}",
                                color = NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (idx > 0) {
                                    TextButton(onClick = {
                                        val next = draftSteps.toMutableList()
                                        val m = next.removeAt(idx)
                                        next.add(idx - 1, m)
                                        draftSteps = next
                                    }) { Text("▲", fontSize = 11.sp, color = NeonCyan) }
                                }
                                if (idx < draftSteps.lastIndex) {
                                    TextButton(onClick = {
                                        val next = draftSteps.toMutableList()
                                        val m = next.removeAt(idx)
                                        next.add(idx + 1, m)
                                        draftSteps = next
                                    }) { Text("▼", fontSize = 11.sp, color = NeonCyan) }
                                }
                                ConfirmDeleteTextButton(
                                    label = "✕",
                                    fontSize = 11.sp,
                                    horizontalPadding = 10.dp,
                                    verticalPadding = 5.dp
                                ) {
                                    draftSteps = draftSteps.filterNot { it.id == step.id }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = step.stepName,
                            onValueChange = { raw -> updateThisStep { it.copy(stepName = raw) } },
                            label = { Text("Nama Koreografi #${idx + 1} (opsional)", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("dialog_step_name_input_${idx + 1}")
                        )

                        if (step.actionType == ActionType.SIMULATED_CLICK || step.actionType == ActionType.SIMULATED_SWIPE) {
                            if (step.actionType == ActionType.SIMULATED_SWIPE) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("LEFT", "RIGHT", "UP", "DOWN", "RANDOM").forEach { dir ->
                                        FilterChip(
                                            selected = step.paramValue.equals(dir, ignoreCase = true),
                                            onClick = { updateThisStep { it.copy(paramValue = dir) } },
                                            label = { Text(if (dir == "RANDOM") "🎲 RANDOM" else dir, fontSize = 9.sp) },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(alpha = 0.25f))
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = step.paramValue,
                                    onValueChange = { raw -> updateThisStep { it.copy(paramValue = raw) } },
                                    label = { Text("Arah Geser (LEFT/RIGHT/UP/DOWN/RANDOM/45°/X,Y)", fontSize = 9.5.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            var durDraft by remember(step.id, step.durationMs) { mutableStateOf(step.durationMs.toString()) }
                            OutlinedTextField(
                                value = durDraft,
                                onValueChange = { raw ->
                                    val digits = raw.filter(Char::isDigit).take(5)
                                    durDraft = digits
                                    digits.toLongOrNull()?.let { v ->
                                        val clamped = if (step.actionType == ActionType.SIMULATED_CLICK) {
                                            v.coerceIn(0L, 12_000L)
                                        } else {
                                            v.coerceIn(60L, 5000L)
                                        }
                                        updateThisStep { it.copy(durationMs = clamped, secondaryValue = clamped.toString()) }
                                    }
                                },
                                label = {
                                    Text(
                                        if (step.actionType == ActionType.SIMULATED_CLICK) "Tahan(ms, boleh 0)" else "Durasi Geser(ms)",
                                        fontSize = 10.sp
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (step.actionType == ActionType.SIMULATED_CLICK) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("✊ Tahan + Geser → Lepas", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    Switch(
                                        checked = step.holdAndDrag,
                                        onCheckedChange = { checked -> updateThisStep { it.copy(holdAndDrag = checked) } }
                                    )
                                }
                                if (step.holdAndDrag) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("LEFT", "RIGHT", "UP", "DOWN", "RANDOM").forEach { dir ->
                                            FilterChip(
                                                selected = step.dragDirection.equals(dir, ignoreCase = true),
                                                onClick = { updateThisStep { it.copy(dragDirection = dir) } },
                                                label = { Text(if (dir == "RANDOM") "🎲 RANDOM" else dir, fontSize = 9.sp) }
                                            )
                                        }
                                    }
                                    var dragDurDraft by remember(step.id, step.dragDurationMs) {
                                        mutableStateOf(step.dragDurationMs.toString())
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(
                                            value = step.dragDirection,
                                            onValueChange = { raw -> updateThisStep { it.copy(dragDirection = raw) } },
                                            label = { Text("Arah Geser (atau 45°/X,Y)", fontSize = 9.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.2f)
                                        )
                                        OutlinedTextField(
                                            value = dragDurDraft,
                                            onValueChange = { raw ->
                                                val digits = raw.filter(Char::isDigit).take(4)
                                                dragDurDraft = digits
                                                digits.toLongOrNull()?.let { v ->
                                                    updateThisStep { it.copy(dragDurationMs = v.coerceIn(60L, 5000L)) }
                                                }
                                            },
                                            label = { Text("Geser(ms)", fontSize = 9.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(0.8f)
                                        )
                                    }
                                }
                            }
                        } else if (step.actionType == ActionType.HOME_BACK_GAME) {
                            val dlgContext = LocalContext.current
                            var appQuery by remember(step.id) { mutableStateOf("") }
                            var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
                            LaunchedEffect(step.id) {
                                installedApps = withContext(Dispatchers.IO) {
                                    InstalledAppSearcher.loadLaunchableApps(dlgContext)
                                }
                            }
                            val filteredApps = remember(installedApps, appQuery) {
                                InstalledAppSearcher.filterApps(installedApps, appQuery, limit = 12)
                            }
                            val chosenPkg = step.secondaryValue.trim()
                            val chosenLabel = installedApps.firstOrNull { it.packageName == chosenPkg }?.appName
                                ?: chosenPkg.ifBlank { "Auto (Aplikasi Terakhir / MLBB)" }
                            Text(
                                text = "Tujuan Back: $chosenLabel",
                                color = NeonOrange,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = appQuery,
                                onValueChange = { appQuery = it },
                                label = { Text("🔍 Cari APK / Aplikasi tujuan back", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (appQuery.isNotBlank() || chosenPkg.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 110.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (chosenPkg.isNotBlank()) {
                                        TextButton(onClick = {
                                            updateThisStep { it.copy(secondaryValue = "") }
                                        }) { Text("↺ Pakai Auto (Aplikasi Terakhir)", fontSize = 9.5.sp, color = NeonCyan) }
                                    }
                                    filteredApps.forEach { app ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(DarkSurface)
                                                .clickable {
                                                    updateThisStep { it.copy(secondaryValue = app.packageName) }
                                                    appQuery = ""
                                                }
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${app.appName} (${app.packageName})",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text("Pilih", color = EmeraldGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else if (step.actionType == ActionType.CUSTOM_IMAGE || step.actionType == ActionType.SHOW_OVERLAY || step.actionType == ActionType.TEXT_OVERLAY) {
                            val sc = step.overlayScale.coerceIn(0.35f, 3.0f)
                            Text(
                                text = "Ukuran Overlay: ${(sc * 100).roundToInt()}%",
                                color = NeonCyan,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = sc,
                                onValueChange = { next ->
                                    updateThisStep { it.copy(overlayScale = next.coerceIn(0.35f, 3.0f)) }
                                },
                                valueRange = 0.35f..3.0f,
                                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
                                modifier = Modifier.fillMaxWidth().height(24.dp)
                            )
                        }
                    }
                }

                // Cooldown visual skill ML per pin
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkBackground)
                        .border(0.7.dp, if (draftEnableVisualCooldown) NeonCyan else DarkCardBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏱️ Baca Angka Cooldown Skill ML", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = draftEnableVisualCooldown,
                            onCheckedChange = { draftEnableVisualCooldown = it }
                        )
                    }
                    if (draftEnableVisualCooldown) {
                        Text(
                            text = "Area Scan: ${(draftCooldownWidth * 100).roundToInt()}% × ${(draftCooldownHeight * 100).roundToInt()}% • Offset (${(draftCooldownOffsetX * 100).roundToInt()}%, ${(draftCooldownOffsetY * 100).roundToInt()}%)",
                            color = NeonCyan,
                            fontSize = 8.5.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Slider(
                                value = draftCooldownWidth,
                                onValueChange = { draftCooldownWidth = it },
                                valueRange = 0.025f..0.35f,
                                modifier = Modifier.weight(1f).height(22.dp)
                            )
                            Slider(
                                value = draftCooldownHeight,
                                onValueChange = { draftCooldownHeight = it },
                                valueRange = 0.025f..0.45f,
                                modifier = Modifier.weight(1f).height(22.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Slider(
                                value = draftCooldownOffsetX,
                                onValueChange = { draftCooldownOffsetX = it },
                                valueRange = -0.25f..0.25f,
                                modifier = Modifier.weight(1f).height(22.dp)
                            )
                            Slider(
                                value = draftCooldownOffsetY,
                                onValueChange = { draftCooldownOffsetY = it },
                                valueRange = -0.25f..0.25f,
                                modifier = Modifier.weight(1f).height(22.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "+ Tambah Koreografi:",
                    color = EmeraldGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val lastPin = draftSteps.lastOrNull {
                        it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE
                    }
                    val nx = ((lastPin?.posX ?: rule.overlayPosX) + if (lastPin == null) 0f else 0.05f).coerceIn(0.05f, 0.95f)
                    val ny = ((lastPin?.posY ?: rule.overlayPosY) - if (lastPin == null) 0f else 0.05f).coerceIn(0.05f, 0.95f)
                    OutlinedButton(onClick = {
                        draftSteps = draftSteps + UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.SIMULATED_CLICK,
                            paramValue = String.format(Locale.US, "%.3f,%.3f", nx, ny),
                            secondaryValue = "70",
                            durationMs = 70L,
                            posX = nx,
                            posY = ny,
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                    }) { Text("+ Klik ML", fontSize = 9.5.sp, color = EmeraldGreen) }

                    OutlinedButton(onClick = {
                        draftSteps = draftSteps + UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.SIMULATED_SWIPE,
                            paramValue = "RIGHT",
                            secondaryValue = "220",
                            durationMs = 220L,
                            posX = nx,
                            posY = ny,
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                    }) { Text("+ Geser ML", fontSize = 9.5.sp, color = NeonCyan) }

                    OutlinedButton(onClick = {
                        draftSteps = draftSteps + UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.DELAY,
                            paramValue = "500",
                            durationMs = 500L,
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                    }) { Text("+ Jeda", fontSize = 9.5.sp, color = Color.White) }

                    OutlinedButton(onClick = {
                        draftSteps = draftSteps + UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.HOME_BACK_GAME,
                            paramValue = "1500",
                            durationMs = 1500L,
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                    }) { Text("+ Home→Game", fontSize = 9.5.sp, color = NeonOrange) }

                    OutlinedButton(onClick = {
                        draftSteps = draftSteps + UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.CUSTOM_IMAGE,
                            durationMs = 2500L,
                            posX = rule.overlayPosX,
                            posY = rule.overlayPosY,
                            executionMode = ExecutionMode.PARALLEL
                        )
                    }) { Text("+ Overlay", fontSize = 9.5.sp, color = NeonPurple) }

                    OutlinedButton(onClick = {
                        draftSteps = draftSteps + UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.PLAY_SOUND,
                            paramValue = "CHIME",
                            executionMode = ExecutionMode.PARALLEL
                        )
                    }) { Text("+ Sound", fontSize = 9.5.sp, color = NeonCyan) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleaned = draftSteps.map { it.copy(stepName = it.stepName.trim()) }
                    onSave(
                        rule.copy(
                            triggerName = draftChallengeName.trim().ifBlank { "Konsep" },
                            enableVisualCooldownWait = draftEnableVisualCooldown,
                            cooldownAreaWidthRatio = draftCooldownWidth.coerceIn(0.025f, 0.45f),
                            cooldownAreaHeightRatio = draftCooldownHeight.coerceIn(0.025f, 0.55f),
                            cooldownAreaOffsetX = draftCooldownOffsetX.coerceIn(-0.30f, 0.30f),
                            cooldownAreaOffsetY = draftCooldownOffsetY.coerceIn(-0.30f, 0.30f),
                            actionSequence = cleaned
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Text("Simpan", color = Color.Black, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
private fun StatusMiniTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkBackground)
            .border(0.7.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color(0xFF94A3B8), fontSize = 8.sp)
    }
}
