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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.tajasb.interaction.ConceptGrouper
import com.tajasb.interaction.ConceptGroupingMode
import com.tajasb.interaction.ExecutionMode
import com.tajasb.ui.components.GiftNameCatalogPicker
import com.tajasb.interaction.FloatingPickerBridge
import com.tajasb.interaction.FloatingPickerMode
import com.tajasb.interaction.FloatingTriggerOverlayService
import com.tajasb.interaction.FullAppBackupManager
import com.tajasb.interaction.GiftCoinCatalog
import com.tajasb.interaction.InstalledAppInfo
import com.tajasb.interaction.InstalledAppSearcher
import com.tajasb.interaction.MlbbGestureAccessibilityService
import com.tajasb.interaction.UniversalActionStep
import com.tajasb.interaction.UniversalTriggerEngine
import com.tajasb.interaction.UniversalTriggerRule
import com.tajasb.interaction.UniversalTriggerSource
import com.tajasb.ui.components.ConfirmDeleteTextButton
import com.tajasb.ui.components.OutlinedMsTextField
import com.tajasb.ui.components.PrecisionStepperBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Layar Tab MLBB:
 * 1) Cek 2 izin utama: Overlay (Pin + HUD mini) & Aksesibilitas (Tap/Swipe nyata ke tombol MLBB).
 * 2) Saklar HUD Floating Minimalis atau langsung Mode Live Bersih tanpa floating.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UniversalInteractionScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { UniversalTriggerEngine.getInstance(context) }
    val uiState by engine.state.collectAsState()
    val a11yConnected by MlbbGestureAccessibilityService.isConnected.collectAsState()
    val relayPrefs = remember { LiveEventPreferences.getInstance(context) }
    val relayConfig by relayPrefs.config.collectAsState()
    val relayReceiver = remember { LiveEventReceiver.getInstance(context) }
    val relayStatus by relayReceiver.connectionStatus.collectAsState()
    val controllerUrl = "http://${relayConfig.serverHost}:${relayConfig.serverPort}/controller"

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

        // Card Mode Kerja (2-HP Split Control vs 1-HP Solo Lengkap)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(
                1.dp,
                if (uiState.isDevice2SimpleMode) NeonCyan.copy(alpha = 0.65f) else NeonPurple.copy(alpha = 0.65f)
            )
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
                        text = if (uiState.isDevice2SimpleMode) "📱 Mode Kerja: 2-HP Simpel" else "🎛️ Mode Kerja: 1-HP Lengkap",
                        color = if (uiState.isDevice2SimpleMode) NeonCyan else NeonPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = if (relayStatus == ConnectionStatus.CONNECTED) "● Termux Tersambung" else "○ Termux Offline",
                        color = if (relayStatus == ConnectionStatus.CONNECTED) EmeraldGreen else Color(0xFF94A3B8),
                        fontSize = 8.8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isSimple = uiState.isDevice2SimpleMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSimple) NeonCyan.copy(alpha = 0.2f) else DarkBackground)
                            .border(1.dp, if (isSimple) NeonCyan else DarkCardBorder, RoundedCornerShape(9.dp))
                            .clickable { engine.setDevice2SimpleMode(true) }
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                            .testTag("mode_2hp_simple_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📱 Simpel (2-HP)",
                            color = if (isSimple) NeonCyan else Color(0xFFCBD5E1),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (!isSimple) NeonPurple.copy(alpha = 0.22f) else DarkBackground)
                            .border(1.dp, if (!isSimple) NeonPurple else DarkCardBorder, RoundedCornerShape(9.dp))
                            .clickable { engine.setDevice2SimpleMode(false) }
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                            .testTag("mode_1hp_full_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎛️ Lengkap (1-HP)",
                            color = if (!isSimple) NeonPurple else Color(0xFFCBD5E1),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (uiState.isDevice2SimpleMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(DarkBackground)
                            .border(0.8.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kontroler Web Device 1:",
                                color = Color(0xFF94A3B8),
                                fontSize = 8.2.sp
                            )
                            Text(
                                text = controllerUrl,
                                color = EmeraldGreen,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "HP ini fokus + Konsep, Pin & Koreo. Detail delay, OCR scan & syarat diatur di Web Device 1.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 8.sp,
                                lineHeight = 11.5.sp
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Controller URL", controllerUrl))
                                Toast.makeText(context, "📋 URL Kontroler disalin: $controllerUrl", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("📋 Salin", fontSize = 8.8.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = "Semua pengaturan detail (Delay Global, Delay Scan Cooldown OCR, ms langkah, syarat & backup JSON) terbuka langsung di HP ini.",
                        color = Color(0xFF94A3B8),
                        fontSize = 8.5.sp
                    )
                }
            }
        }

        // Card Delay Global Sebelum & Sesudah Aksi (disembunyikan di Mode 2-HP Simpel)
        if (!uiState.isDevice2SimpleMode) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(
                1.dp,
                if (uiState.globalDelayBeforeMs > 0L || uiState.globalDelayAfterMs > 0L) {
                    NeonCyan.copy(alpha = 0.65f)
                } else {
                    DarkCardBorder
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "⏱️ Delay Global (Sebelum & Sesudah Aksi)",
                    color = NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Atur jeda global sebelum dan sesudah koreografi berjalan. Bisa diisi 0 ms. Konsep dengan Delay Scan Cooldown ML bisa dikecualikan otomatis.",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedMsTextField(
                        label = "Delay Sebelum (ms)",
                        value = uiState.globalDelayBeforeMs,
                        onValueCommitted = { v -> engine.setGlobalDelaySettings(beforeMs = v) },
                        minValue = 0L,
                        maxValue = 60_000L,
                        maxDigits = 5,
                        resetKey = "screen_global_before",
                        modifier = Modifier.weight(1f).testTag("global_delay_before_input")
                    )
                    OutlinedMsTextField(
                        label = "Delay Sesudah (ms)",
                        value = uiState.globalDelayAfterMs,
                        onValueCommitted = { v -> engine.setGlobalDelaySettings(afterMs = v) },
                        minValue = 0L,
                        maxValue = 60_000L,
                        maxDigits = 5,
                        resetKey = "screen_global_after",
                        modifier = Modifier.weight(1f).testTag("global_delay_after_input")
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkBackground)
                        .border(0.8.dp, DarkCardBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kecualikan konsep Delay Scan Cooldown ML",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Konsep yang memakai scan cooldown skill langsung jalan tanpa delay global.",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.2.sp
                        )
                    }
                    Switch(
                        checked = uiState.globalDelayExcludeCooldownRules,
                        onCheckedChange = { engine.setGlobalDelaySettings(excludeCooldownRules = it) },
                        modifier = Modifier.testTag("global_delay_exclude_cooldown_switch"),
                        colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan)
                    )
                }
            }
        }
        }

        // Card Area Penutup Layar Game (Custom Ukuran & Posisi)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(
                1.dp,
                if (uiState.isCoverEditMode || uiState.gameCoverAreas.any { it.enabled }) {
                    NeonPurple.copy(alpha = 0.65f)
                } else {
                    DarkCardBorder
                }
            )
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🛡️ Area Penutup Layar Game (${uiState.gameCoverAreas.count { it.enabled }}/${uiState.gameCoverAreas.size})",
                            color = NeonPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tutup minimap, chat, atau ID lobby dengan ukuran bebas. Sentuhan game tetap tembus saat main.",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.8.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val nextEdit = !uiState.isCoverEditMode
                            if (nextEdit && !uiState.isFloatingOverlayRunning) {
                                if (!FloatingTriggerOverlayService.canDrawOverlays(context)) {
                                    requestFloatingToggle(true)
                                    return@OutlinedButton
                                }
                                FloatingTriggerOverlayService.start(
                                    context = context,
                                    startInEditMode = false,
                                    openSettingsPanel = false,
                                    hideFloatingForLive = false
                                )
                            }
                            engine.setCoverEditMode(nextEdit)
                        },
                        modifier = Modifier.testTag("toggle_cover_edit_mode_btn")
                    ) {
                        Text(
                            text = if (uiState.isCoverEditMode) "✓ Selesai Atur" else "✥ Atur di Layar",
                            fontSize = 9.5.sp,
                            color = if (uiState.isCoverEditMode) EmeraldGreen else NeonPurple,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    fun addPresetCover(name: String, px: Float, py: Float, wr: Float, hr: Float) {
                        engine.addGameCoverArea(name = name, posX = px, posY = py, widthRatio = wr, heightRatio = hr)
                        if (FloatingTriggerOverlayService.canDrawOverlays(context)) {
                            if (!uiState.isFloatingOverlayRunning) {
                                FloatingTriggerOverlayService.start(
                                    context = context,
                                    startInEditMode = false,
                                    openSettingsPanel = false,
                                    hideFloatingForLive = false
                                )
                            }
                            engine.setCoverEditMode(true)
                        }
                    }
                    OutlinedButton(
                        onClick = { addPresetCover("Minimap", 0.12f, 0.22f, 0.16f, 0.26f) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("+ Minimap", fontSize = 9.sp, color = NeonCyan, fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { addPresetCover("Chat/Lobby", 0.24f, 0.78f, 0.28f, 0.18f) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("+ Chat/Lobby", fontSize = 9.sp, color = NeonOrange, fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = {
                            addPresetCover(
                                name = "Custom ${uiState.gameCoverAreas.size + 1}",
                                px = 0.50f,
                                py = 0.50f,
                                wr = 0.22f,
                                hr = 0.22f
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("+ Area Custom", fontSize = 9.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold) }
                }

                if (uiState.gameCoverAreas.isNotEmpty()) {
                    val activeCover = uiState.gameCoverAreas.firstOrNull { it.id == uiState.activeCoverAreaId }
                        ?: uiState.gameCoverAreas.first()
                    uiState.gameCoverAreas.forEach { area ->
                        val selected = area.id == activeCover.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) NeonPurple.copy(alpha = 0.14f) else DarkBackground)
                                .border(
                                    0.8.dp,
                                    if (selected) NeonPurple else DarkCardBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { engine.selectActiveCoverArea(area.id) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = area.name,
                                    color = if (area.enabled) Color.White else Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Ukuran ${(area.widthRatio * 100).roundToInt()}% × ${(area.heightRatio * 100).roundToInt()}% • Opacity ${(area.opacity * 100).roundToInt()}%",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 8.sp
                                )
                            }
                            Switch(
                                checked = area.enabled,
                                onCheckedChange = { engine.toggleGameCoverAreaEnabled(area.id) },
                                colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen)
                            )
                            ConfirmDeleteTextButton(
                                label = "Hapus",
                                fontSize = 8.5.sp,
                                horizontalPadding = 7.dp,
                                verticalPadding = 5.dp
                            ) {
                                engine.deleteGameCoverArea(area.id)
                            }
                        }
                    }

                    if (!uiState.isDevice2SimpleMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            label = "Opacity Area Aktif",
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
        }

        // Daftar Challenge & Koreografi (Menu Grup + Deteksi Bentrok Gift + Edit langsung di aplikasi)
        var editingRuleInApp by remember { mutableStateOf<UniversalTriggerRule?>(null) }
        var inAppGroupMode by remember { mutableStateOf(ConceptGroupingMode.BY_EVENT) }
        var inAppSelectedGroupKey by remember { mutableStateOf<String?>(null) }
        var inAppCollapsedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }

        val inAppBuckets = remember(uiState.rules, inAppGroupMode) {
            ConceptGrouper.groupRules(uiState.rules, inAppGroupMode)
        }
        val inAppVisibleBuckets = remember(inAppBuckets, inAppSelectedGroupKey) {
            if (inAppSelectedGroupKey == null) inAppBuckets
            else inAppBuckets.filter { it.key == inAppSelectedGroupKey }.ifEmpty { inAppBuckets }
        }

        fun createInAppConceptForGroup(groupKey: String?) {
            val source = if (groupKey != null && groupKey.startsWith("EVT_")) {
                UniversalTriggerSource.values().firstOrNull { "EVT_${it.name}" == groupKey } ?: UniversalTriggerSource.GIFT
            } else {
                UniversalTriggerSource.GIFT
            }
            val created = engine.createEmptyConcept(source)
            val starterStep = when (groupKey) {
                "ACT_CLICK" -> UniversalActionStep(
                    enabled = true,
                    actionType = ActionType.SIMULATED_CLICK,
                    paramValue = String.format(Locale.US, "%.3f,%.3f", created.overlayPosX, created.overlayPosY),
                    secondaryValue = "70",
                    durationMs = 70L,
                    posX = created.overlayPosX,
                    posY = created.overlayPosY
                )
                "ACT_SWIPE" -> UniversalActionStep(
                    enabled = true,
                    actionType = ActionType.SIMULATED_SWIPE,
                    paramValue = "RIGHT",
                    secondaryValue = "220",
                    durationMs = 220L,
                    posX = created.overlayPosX,
                    posY = created.overlayPosY
                )
                "ACT_HOME" -> UniversalActionStep(
                    enabled = true,
                    actionType = ActionType.HOME_BACK_GAME,
                    paramValue = "1500",
                    durationMs = 1500L
                )
                "ACT_OVERLAY" -> UniversalActionStep(
                    enabled = true,
                    actionType = ActionType.MEME_OVERLAY,
                    secondaryValue = "MEME {user}!",
                    durationMs = 2500L,
                    posX = created.overlayPosX,
                    posY = created.overlayPosY
                )
                "ACT_SOUND" -> UniversalActionStep(
                    enabled = true,
                    actionType = ActionType.PLAY_SOUND,
                    paramValue = "CHIME"
                )
                else -> null
            }
            val finalRule = if (starterStep != null) {
                val updated = created.copy(actionSequence = listOf(starterStep))
                engine.addOrUpdateRule(updated)
                updated
            } else {
                created
            }
            editingRuleInApp = finalRule
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
                        onClick = { createInAppConceptForGroup(inAppSelectedGroupKey) },
                        modifier = Modifier.testTag("inapp_add_challenge_btn")
                    ) {
                        Text("+ Konsep", fontSize = 9.5.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    }
                }

                if (uiState.rules.isNotEmpty()) {
                    // Menu Grup Konsep (Event / Aksi / Semua)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Grup:",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        ConceptGroupingMode.values().forEach { mode ->
                            val active = inAppGroupMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (active) NeonCyan.copy(alpha = 0.22f) else DarkBackground)
                                    .border(0.8.dp, if (active) NeonCyan else DarkCardBorder, RoundedCornerShape(7.dp))
                                    .clickable {
                                        inAppGroupMode = mode
                                        inAppSelectedGroupKey = null
                                    }
                                    .padding(vertical = 5.dp)
                                    .testTag("inapp_group_mode_${mode.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (active) NeonCyan else Color(0xFFCBD5E1),
                                    fontSize = 9.sp,
                                    fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (inAppGroupMode != ConceptGroupingMode.ALL && inAppBuckets.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            FilterChip(
                                selected = inAppSelectedGroupKey == null,
                                onClick = { inAppSelectedGroupKey = null },
                                label = { Text("Semua (${uiState.rules.size})", fontSize = 8.5.sp) }
                            )
                            inAppBuckets.forEach { bucket ->
                                val active = inAppSelectedGroupKey == bucket.key
                                FilterChip(
                                    selected = active,
                                    onClick = { inAppSelectedGroupKey = if (active) null else bucket.key },
                                    label = { Text("${bucket.emoji} ${bucket.title} (${bucket.rules.size})", fontSize = 8.5.sp) }
                                )
                            }
                        }
                    }
                }

                if (uiState.rules.isEmpty()) {
                    Text(
                        text = "Belum ada konsep. Tekan '+ Konsep' di sini atau '+' di floating HUD (default kosong, bebas tambah Klik / Geser / Home→Game / Jeda / Overlay / Sound).",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.5.sp
                    )
                } else {
                    inAppVisibleBuckets.forEach { bucket ->
                        val showHeader = inAppGroupMode != ConceptGroupingMode.ALL
                        val isCollapsed = showHeader && inAppSelectedGroupKey == null && bucket.key in inAppCollapsedGroups

                        if (showHeader) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF162033))
                                    .border(0.8.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (inAppSelectedGroupKey == null) {
                                            inAppCollapsedGroups = if (isCollapsed) {
                                                inAppCollapsedGroups - bucket.key
                                            } else {
                                                inAppCollapsedGroups + bucket.key
                                            }
                                        }
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${if (isCollapsed) "▸" else "▾"} ${bucket.emoji} ${bucket.title} (${bucket.rules.size})",
                                    color = NeonCyan,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "+ Grup",
                                    color = EmeraldGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(EmeraldGreen.copy(alpha = 0.16f))
                                        .border(0.7.dp, EmeraldGreen, RoundedCornerShape(6.dp))
                                        .clickable { createInAppConceptForGroup(bucket.key) }
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (!isCollapsed) {
                            bucket.rules.forEach { indexed ->
                                val idx = indexed.globalIndex
                                val rule = indexed.rule
                                val activeSteps = rule.actionSequence.filter { it.enabled }
                                val giftConflicts = remember(rule.id, rule.triggerSource, rule.targetGiftName, uiState.rules) {
                                    GiftCoinCatalog.findConflictingRules(
                                        currentRuleId = rule.id,
                                        currentSource = rule.triggerSource,
                                        targetGiftName = rule.targetGiftName,
                                        allRules = uiState.rules
                                    )
                                }
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
                                        .border(
                                            0.8.dp,
                                            if (giftConflicts.isNotEmpty()) NeonOrange.copy(alpha = 0.85f) else accent.copy(alpha = 0.6f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { editingRuleInApp = rule }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(accent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${idx + 1}", color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        val srcBadge = if (
                                            rule.targetGiftName.isNotBlank() &&
                                            (rule.triggerSource == UniversalTriggerSource.GIFT || rule.triggerSource == UniversalTriggerSource.GIFT_QUANTITY)
                                        ) {
                                            "${rule.triggerSource.displayName} [${rule.targetGiftName}]"
                                        } else {
                                            rule.triggerSource.displayName
                                        }
                                        Text(
                                            text = "$srcBadge → ${rule.triggerName}",
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = choreoSummary + if (rule.enableVisualCooldownWait) " • ⏱ Scan" else "",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 8.5.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (giftConflicts.isNotEmpty()) {
                                            Text(
                                                text = "⚠️ Gift sudah dipakai di: ${giftConflicts.first().conflictingRuleName}",
                                                color = NeonOrange,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            engine.forceTestRuleNow(rule.id)
                                            Toast.makeText(context, "▶ Menjalankan tes: ${rule.triggerName}", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp).testTag("test_challenge_${rule.id}")
                                    ) {
                                        Text("▶", fontSize = 9.sp, color = EmeraldGreen, fontWeight = FontWeight.Black)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val copy = engine.duplicateRule(rule.id)
                                            if (copy != null) {
                                                Toast.makeText(context, "⧉ Duplikat dibuat: ${copy.triggerName}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp).testTag("duplicate_challenge_${rule.id}")
                                    ) {
                                        Text("⧉", fontSize = 9.sp, color = NeonPurple, fontWeight = FontWeight.Black)
                                    }
                                    OutlinedButton(
                                        onClick = { editingRuleInApp = rule },
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp).testTag("edit_challenge_${rule.id}")
                                    ) {
                                        Text("✎", fontSize = 9.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                    }
                                    ConfirmDeleteTextButton(
                                        label = "Hapus",
                                        fontSize = 8.5.sp,
                                        horizontalPadding = 7.dp,
                                        verticalPadding = 5.dp,
                                        modifier = Modifier.testTag("delete_challenge_${rule.id}")
                                    ) {
                                        engine.deleteRule(rule.id)
                                    }
                                }
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

        if (!uiState.isDevice2SimpleMode) {
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
                    text = "Mencakup semua Pin Challenge, Koreografi, Delay Global, Tutup Area Game, Cooldown ML, Kunci Sentuh, Posisi HUD, serta pengaturan Server Termux & Event Overlay.",
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
                allRules = uiState.rules,
                isDevice2SimpleMode = uiState.isDevice2SimpleMode,
                engine = engine,
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

@Composable
private fun DialogConceptSectionCard(
    stepNumber: String,
    title: String,
    accent: Color = NeonCyan,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkBackground.copy(alpha = 0.65f))
            .border(0.8.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(stepNumber, color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
            }
            Text(
                text = title,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditChallengeAndChoreographyDialog(
    rule: UniversalTriggerRule,
    allRules: List<UniversalTriggerRule> = emptyList(),
    isDevice2SimpleMode: Boolean = true,
    engine: UniversalTriggerEngine,
    onDismiss: () -> Unit,
    onSave: (UniversalTriggerRule) -> Unit
) {
    val dlgContext = LocalContext.current
    val dlgCfg = LocalConfiguration.current
    val maxDialogBodyHeightDp = (dlgCfg.screenHeightDp * 0.56f).coerceIn(190f, 460f).dp
    var draftChallengeName by remember(rule.id) { mutableStateOf(rule.triggerName) }
    var draftSource by remember(rule.id) { mutableStateOf(rule.triggerSource) }
    var draftTargetGiftName by remember(rule.id) { mutableStateOf(rule.targetGiftName) }
    var draftDelayBeforeMs by remember(rule.id) { mutableStateOf(if (rule.enableDelayBefore) rule.delayBeforeMs else 0L) }
    var draftSteps by remember(rule.id) { mutableStateOf(rule.actionSequence) }
    var draftEnableVisualCooldown by remember(rule.id) { mutableStateOf(rule.enableVisualCooldownWait) }
    var draftCooldownTargetStepId by remember(rule.id) { mutableStateOf(rule.cooldownTargetStepId) }
    var draftCooldownWidth by remember(rule.id) { mutableStateOf(rule.cooldownAreaWidthRatio.coerceIn(0.025f, 0.45f)) }
    var draftCooldownHeight by remember(rule.id) { mutableStateOf(rule.cooldownAreaHeightRatio.coerceIn(0.025f, 0.55f)) }
    var draftCooldownOffsetX by remember(rule.id) { mutableStateOf(rule.cooldownAreaOffsetX.coerceIn(-0.30f, 0.30f)) }
    var draftCooldownOffsetY by remember(rule.id) { mutableStateOf(rule.cooldownAreaOffsetY.coerceIn(-0.30f, 0.30f)) }
    var draftCooldownMaxWaitMs by remember(rule.id) { mutableStateOf(rule.cooldownMaxWaitMs.coerceIn(1500L, 180_000L)) }
    var cooldownScanTestMsg by remember(rule.id) { mutableStateOf<String?>(null) }
    var showMoreAddMenu by remember(rule.id) { mutableStateOf(false) }

    val pickerResult by FloatingPickerBridge.result.collectAsState()
    LaunchedEffect(pickerResult) {
        val res = pickerResult ?: return@LaunchedEffect
        when {
            res.requestKey.startsWith("inapp_img_") -> {
                val targetStepId = res.requestKey.removePrefix("inapp_img_")
                draftSteps = draftSteps.map {
                    if (it.id == targetStepId) it.copy(paramValue = res.uriString) else it
                }
                FloatingPickerBridge.consumeResult()
            }
            res.requestKey.startsWith("inapp_sfx_") -> {
                val targetStepId = res.requestKey.removePrefix("inapp_sfx_")
                draftSteps = draftSteps.map {
                    if (it.id == targetStepId) it.copy(paramValue = res.uriString) else it
                }
                FloatingPickerBridge.consumeResult()
            }
        }
    }

    fun buildDraftRule(): UniversalTriggerRule {
        val cleaned = draftSteps.map { s ->
            when (s.actionType) {
                ActionType.DELAY, ActionType.WAIT -> {
                    val ms = s.durationMs.coerceIn(0L, 60_000L)
                    s.copy(stepName = s.stepName.trim(), durationMs = ms, paramValue = ms.toString())
                }
                ActionType.HOME_BACK_GAME -> {
                    val ms = s.durationMs.coerceIn(0L, 30_000L)
                    s.copy(stepName = s.stepName.trim(), durationMs = ms, paramValue = ms.toString())
                }
                ActionType.SIMULATED_CLICK -> {
                    s.copy(
                        stepName = s.stepName.trim(),
                        durationMs = s.durationMs.coerceIn(0L, 12_000L),
                        dragDurationMs = s.dragDurationMs.coerceIn(0L, 5000L)
                    )
                }
                ActionType.SIMULATED_SWIPE -> {
                    s.copy(
                        stepName = s.stepName.trim(),
                        durationMs = s.durationMs.coerceIn(0L, 5000L)
                    )
                }
                else -> s.copy(
                    stepName = s.stepName.trim(),
                    durationMs = s.durationMs.coerceIn(0L, 60_000L)
                )
            }
        }
        val delayBeforeParsed = draftDelayBeforeMs.coerceIn(0L, 60_000L)
        val maxWaitParsed = draftCooldownMaxWaitMs.coerceIn(1500L, 180_000L)
        return rule.copy(
            triggerName = draftChallengeName.trim().ifBlank { "Konsep" },
            triggerSource = draftSource,
            targetGiftName = draftTargetGiftName.trim(),
            enableDelayBefore = delayBeforeParsed > 0L,
            delayBeforeMs = delayBeforeParsed,
            enableVisualCooldownWait = draftEnableVisualCooldown,
            cooldownTargetStepId = draftCooldownTargetStepId,
            cooldownAreaWidthRatio = draftCooldownWidth.coerceIn(0.025f, 0.45f),
            cooldownAreaHeightRatio = draftCooldownHeight.coerceIn(0.025f, 0.55f),
            cooldownAreaOffsetX = draftCooldownOffsetX.coerceIn(-0.30f, 0.30f),
            cooldownAreaOffsetY = draftCooldownOffsetY.coerceIn(-0.30f, 0.30f),
            cooldownMaxWaitMs = maxWaitParsed,
            actionSequence = cleaned
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = "Ubah Konsep & Koreografi",
                color = NeonCyan,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogBodyHeightDp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // BAGIAN 1: IDENTITAS & PEMICU
                DialogConceptSectionCard(stepNumber = "1", title = "Identitas & Pemicu", accent = NeonCyan) {
                    OutlinedTextField(
                        value = draftChallengeName,
                        onValueChange = { draftChallengeName = it },
                        label = { Text("Nama Konsep / Challenge", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_challenge_name_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            UniversalTriggerSource.LIKE,
                            UniversalTriggerSource.GIFT,
                            UniversalTriggerSource.GIFT_QUANTITY,
                            UniversalTriggerSource.CHAT,
                            UniversalTriggerSource.FOLLOW,
                            UniversalTriggerSource.SHARE,
                            UniversalTriggerSource.MEMBER_JOIN
                        ).forEach { src ->
                            FilterChip(
                                selected = draftSource == src,
                                onClick = { draftSource = src },
                                label = { Text(src.displayName, fontSize = 8.5.sp) }
                            )
                        }
                    }

                    if (draftSource == UniversalTriggerSource.GIFT || draftSource == UniversalTriggerSource.GIFT_QUANTITY) {
                        GiftNameCatalogPicker(
                            giftName = draftTargetGiftName,
                            onGiftNameChange = { draftTargetGiftName = it },
                            onQuickPicked = { draftTargetGiftName = it },
                            currentRuleId = rule.id,
                            currentTriggerSource = draftSource,
                            existingRules = allRules
                        )
                    }
                }

                // BAGIAN 2: PRA-AKSI (Disembunyikan pada Mode 2-HP Simpel karena dikontrol lewat Web Device 1)
                if (isDevice2SimpleMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground.copy(alpha = 0.7f))
                            .border(0.8.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚙️ Detail timing, delay scan OCR & syarat diatur di Device 1 Web",
                            color = Color(0xFFCBD5E1),
                            fontSize = 8.4.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { engine.setDevice2SimpleMode(false) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("🎛️ Buka Detail di HP", fontSize = 8.4.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                DialogConceptSectionCard(stepNumber = "2", title = "Pra-Aksi (Sebelum Koreografi)", accent = NeonOrange) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                            .border(0.7.dp, if (draftEnableVisualCooldown) NeonCyan else DarkCardBorder, RoundedCornerShape(8.dp))
                            .padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("⏱️ Delay Scan Cooldown Skill ML", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (draftEnableVisualCooldown) {
                                        "Aktif — menunggu angka cooldown skill hilang sebelum eksekusi."
                                    } else {
                                        "Nonaktif — eksekusi memakai Delay Awal di bawah."
                                    },
                                    color = Color(0xFF94A3B8),
                                    fontSize = 8.sp
                                )
                            }
                            Switch(
                                checked = draftEnableVisualCooldown,
                                onCheckedChange = { draftEnableVisualCooldown = it }
                            )
                        }
                        if (draftEnableVisualCooldown) {
                            val touchCandidates = draftSteps.filter {
                                it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE
                            }
                            if (touchCandidates.size > 1) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Pin Scan:", color = Color(0xFF94A3B8), fontSize = 8.5.sp, modifier = Modifier.align(Alignment.CenterVertically))
                                    touchCandidates.forEachIndexed { tIdx, tStep ->
                                        val selected = draftCooldownTargetStepId == tStep.id ||
                                            (draftCooldownTargetStepId.isNullOrBlank() && tIdx == 0)
                                        val label = tStep.stepName.trim().ifBlank { "#${tIdx + 1}" }
                                        FilterChip(
                                            selected = selected,
                                            onClick = { draftCooldownTargetStepId = tStep.id },
                                            label = { Text(label.take(12), fontSize = 8.sp) }
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                PrecisionStepperBar(
                                    label = "Lebar",
                                    valueText = "${(draftCooldownWidth * 100).roundToInt()}%",
                                    accent = NeonCyan,
                                    onDecrement = { draftCooldownWidth = (draftCooldownWidth - 0.01f).coerceIn(0.025f, 0.35f) },
                                    onIncrement = { draftCooldownWidth = (draftCooldownWidth + 0.01f).coerceIn(0.025f, 0.35f) },
                                    modifier = Modifier.weight(1f)
                                )
                                PrecisionStepperBar(
                                    label = "Tinggi",
                                    valueText = "${(draftCooldownHeight * 100).roundToInt()}%",
                                    accent = EmeraldGreen,
                                    onDecrement = { draftCooldownHeight = (draftCooldownHeight - 0.01f).coerceIn(0.025f, 0.45f) },
                                    onIncrement = { draftCooldownHeight = (draftCooldownHeight + 0.01f).coerceIn(0.025f, 0.45f) },
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
                                    onDecrement = { draftCooldownOffsetX = (draftCooldownOffsetX - 0.01f).coerceIn(-0.25f, 0.25f) },
                                    onIncrement = { draftCooldownOffsetX = (draftCooldownOffsetX + 0.01f).coerceIn(-0.25f, 0.25f) },
                                    modifier = Modifier.weight(1f)
                                )
                                PrecisionStepperBar(
                                    label = "Geser Y",
                                    valueText = "${(draftCooldownOffsetY * 100).roundToInt()}%",
                                    accent = NeonPurple,
                                    decLabel = "▲",
                                    incLabel = "▼",
                                    onDecrement = { draftCooldownOffsetY = (draftCooldownOffsetY - 0.01f).coerceIn(-0.25f, 0.25f) },
                                    onIncrement = { draftCooldownOffsetY = (draftCooldownOffsetY + 0.01f).coerceIn(-0.25f, 0.25f) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedMsTextField(
                                    label = "Max Tunggu(ms)",
                                    value = draftCooldownMaxWaitMs,
                                    onValueCommitted = { draftCooldownMaxWaitMs = it },
                                    minValue = 1500L,
                                    maxValue = 180_000L,
                                    maxDigits = 6,
                                    resetKey = rule.id,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = {
                                        draftCooldownWidth = 0.075f
                                        draftCooldownHeight = 0.12f
                                        draftCooldownOffsetX = 0f
                                        draftCooldownOffsetY = 0f
                                    },
                                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)
                                ) { Text("Reset Box", fontSize = 8.5.sp, color = Color.White) }
                                Button(
                                    onClick = {
                                        val saved = buildDraftRule()
                                        engine.addOrUpdateRule(saved)
                                        engine.inspectRuleCooldownOnce(saved.id) { res ->
                                            cooldownScanTestMsg = res
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) { Text("🔍 Scan Sekali", color = Color.Black, fontSize = 8.5.sp, fontWeight = FontWeight.Black) }
                            }
                            cooldownScanTestMsg?.let { msg ->
                                Text(
                                    text = "Hasil Scan: $msg",
                                    color = EmeraldGreen,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (draftEnableVisualCooldown) {
                        Text(
                            text = "⚡ Delay Awal tidak diperlukan & dilewati otomatis saat Delay Scan aktif.",
                            color = NeonCyan,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        OutlinedMsTextField(
                            label = "⏱️ Delay Awal Sebelum Koreografi (ms)",
                            value = draftDelayBeforeMs,
                            onValueCommitted = { draftDelayBeforeMs = it },
                            minValue = 0L,
                            maxValue = 60_000L,
                            maxDigits = 5,
                            resetKey = rule.id,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                }

                // BAGIAN 3: LANGKAH KOREOGRAFI
                DialogConceptSectionCard(stepNumber = "3", title = "Langkah Koreografi (${draftSteps.size})", accent = EmeraldGreen) {
                if (draftSteps.isEmpty()) {
                    Text(
                        text = "Kosong — tambahkan koreografi sesuai kebutuhan di bawah.",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp
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
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${idx + 1} ${step.actionType.displayName}",
                                color = NeonCyan,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (idx > 0) {
                                    TextButton(
                                        onClick = {
                                            val next = draftSteps.toMutableList()
                                            val m = next.removeAt(idx)
                                            next.add(idx - 1, m)
                                            draftSteps = next
                                        },
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) { Text("▲", fontSize = 10.sp, color = NeonCyan) }
                                }
                                if (idx < draftSteps.lastIndex) {
                                    TextButton(
                                        onClick = {
                                            val next = draftSteps.toMutableList()
                                            val m = next.removeAt(idx)
                                            next.add(idx + 1, m)
                                            draftSteps = next
                                        },
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) { Text("▼", fontSize = 10.sp, color = NeonCyan) }
                                }
                                TextButton(
                                    onClick = {
                                        val next = draftSteps.toMutableList()
                                        next.add(
                                            idx + 1,
                                            step.copy(
                                                id = UUID.randomUUID().toString(),
                                                stepName = if (step.stepName.isBlank()) "" else "${step.stepName} Copy"
                                            )
                                        )
                                        draftSteps = next
                                    },
                                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) { Text("⧉", fontSize = 10.sp, color = NeonPurple, fontWeight = FontWeight.Black) }
                                ConfirmDeleteTextButton(
                                    label = "✕",
                                    fontSize = 10.sp,
                                    horizontalPadding = 8.dp,
                                    verticalPadding = 4.dp
                                ) {
                                    draftSteps = draftSteps.filterNot { it.id == step.id }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            OutlinedTextField(
                                value = step.stepName,
                                onValueChange = { raw -> updateThisStep { it.copy(stepName = raw) } },
                                label = { Text("Nama Langkah #${idx + 1}", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1.35f).testTag("dialog_step_name_input_${idx + 1}")
                            )
                            if (!isDevice2SimpleMode && step.actionType != ActionType.DELAY && step.actionType != ActionType.WAIT) {
                                OutlinedMsTextField(
                                    label = "Delay(ms)",
                                    value = step.delayMs,
                                    onValueCommitted = { d ->
                                        updateThisStep { it.copy(delayMs = d) }
                                    },
                                    minValue = 0L,
                                    maxValue = 60_000L,
                                    maxDigits = 5,
                                    resetKey = step.id,
                                    modifier = Modifier.weight(0.65f)
                                )
                            }
                        }

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
                                            label = { Text(if (dir == "RANDOM") "🎲 RANDOM" else dir, fontSize = 8.5.sp) },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(alpha = 0.25f))
                                        )
                                    }
                                }
                                if (!isDevice2SimpleMode) {
                                    OutlinedTextField(
                                        value = step.paramValue,
                                        onValueChange = { raw -> updateThisStep { it.copy(paramValue = raw) } },
                                        label = { Text("Arah Geser (LEFT/RIGHT/UP/DOWN/RANDOM/45°/X,Y)", fontSize = 9.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (!isDevice2SimpleMode) {
                                OutlinedMsTextField(
                                    label = if (step.actionType == ActionType.SIMULATED_CLICK) "Tahan(ms, boleh 0)" else "Durasi Geser(ms)",
                                    value = step.durationMs,
                                    onValueCommitted = { clamped ->
                                        updateThisStep { it.copy(durationMs = clamped, secondaryValue = clamped.toString()) }
                                    },
                                    minValue = 0L,
                                    maxValue = if (step.actionType == ActionType.SIMULATED_CLICK) 12_000L else 5000L,
                                    maxDigits = 5,
                                    resetKey = step.id,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (step.actionType == ActionType.SIMULATED_CLICK) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("✊ Tahan + Geser → Lepas", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                                                    label = { Text(if (dir == "RANDOM") "🎲 RANDOM" else dir, fontSize = 8.5.sp) }
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            OutlinedTextField(
                                                value = step.dragDirection,
                                                onValueChange = { raw -> updateThisStep { it.copy(dragDirection = raw) } },
                                                label = { Text("Arah (45°/X,Y)", fontSize = 8.5.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1.2f)
                                            )
                                            OutlinedMsTextField(
                                                label = "Geser(ms)",
                                                value = step.dragDurationMs,
                                                onValueCommitted = { v ->
                                                    updateThisStep { it.copy(dragDurationMs = v) }
                                                },
                                                minValue = 0L,
                                                maxValue = 5000L,
                                                maxDigits = 4,
                                                resetKey = step.id,
                                                modifier = Modifier.weight(0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (step.actionType == ActionType.DELAY || step.actionType == ActionType.WAIT) {
                            if (!isDevice2SimpleMode) {
                                OutlinedMsTextField(
                                    label = "Durasi Jeda (ms, boleh 0)",
                                    value = step.durationMs,
                                    onValueCommitted = { ms ->
                                        updateThisStep { it.copy(durationMs = ms, paramValue = ms.toString()) }
                                    },
                                    minValue = 0L,
                                    maxValue = 60_000L,
                                    maxDigits = 5,
                                    resetKey = step.id,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = "⏱️ Jeda saat ini: ${step.durationMs} ms (ubah ms detail di Device 1 Web)",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 8.5.sp
                                )
                            }
                        } else if (step.actionType == ActionType.HOME_BACK_GAME) {
                            if (!isDevice2SimpleMode) {
                                OutlinedMsTextField(
                                    label = "Jeda di Home (ms)",
                                    value = step.durationMs,
                                    onValueCommitted = { ms ->
                                        updateThisStep { it.copy(durationMs = ms, paramValue = ms.toString()) }
                                    },
                                    minValue = 0L,
                                    maxValue = 30_000L,
                                    maxDigits = 5,
                                    resetKey = step.id,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
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
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = appQuery,
                                onValueChange = { appQuery = it },
                                label = { Text("🔍 Cari APK / Aplikasi tujuan back", fontSize = 9.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (appQuery.isNotBlank() || chosenPkg.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 100.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (chosenPkg.isNotBlank()) {
                                        TextButton(onClick = {
                                            updateThisStep { it.copy(secondaryValue = "") }
                                        }) { Text("↺ Pakai Auto (Aplikasi Terakhir)", fontSize = 9.sp, color = NeonCyan) }
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
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${app.appName} (${app.packageName})",
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text("Pilih", color = EmeraldGreen, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else if (
                            step.actionType == ActionType.MEME_OVERLAY ||
                            step.actionType == ActionType.CUSTOM_IMAGE ||
                            step.actionType == ActionType.PROFILE_OVERLAY ||
                            step.actionType == ActionType.SHOW_OVERLAY ||
                            step.actionType == ActionType.TEXT_OVERLAY ||
                            step.actionType == ActionType.ANIMATION ||
                            step.actionType == ActionType.TIER_OVERLAY
                        ) {
                            val isMemeOrCustomImage = step.actionType == ActionType.MEME_OVERLAY ||
                                step.actionType == ActionType.CUSTOM_IMAGE
                            val isProfileOverlay = step.actionType == ActionType.PROFILE_OVERLAY
                            val isAnimation = step.actionType == ActionType.ANIMATION

                            if (isMemeOrCustomImage) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(0.8.dp, NeonCyan, RoundedCornerShape(6.dp))
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            FloatingPickerBridge.launch(
                                                context = dlgContext,
                                                mode = FloatingPickerMode.PICK_IMAGE,
                                                requestKey = "inapp_img_${step.id}"
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (step.paramValue.isBlank()) "Upload Foto Meme" else "Ganti Foto",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (step.paramValue.isNotBlank()) {
                                        ConfirmDeleteTextButton(
                                            label = "Hapus",
                                            fontSize = 8.5.sp,
                                            horizontalPadding = 7.dp,
                                            verticalPadding = 6.dp
                                        ) {
                                            updateThisStep { it.copy(paramValue = "") }
                                        }
                                    }
                                }
                            } else if (isProfileOverlay) {
                                Text(
                                    text = "👤 Menampilkan foto profil pengirim gift otomatis (fallback inisial bila offline).",
                                    color = NeonCyan,
                                    fontSize = 8.5.sp
                                )
                            } else if (isAnimation) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("BOUNCE", "SHAKE", "PULSE", "FLASH").forEach { anim ->
                                        FilterChip(
                                            selected = step.paramValue.equals(anim, ignoreCase = true),
                                            onClick = { updateThisStep { it.copy(paramValue = anim) } },
                                            label = { Text(anim, fontSize = 8.5.sp) }
                                        )
                                    }
                                }
                            }

                            if (!isDevice2SimpleMode) {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    if (!isMemeOrCustomImage && !isAnimation) {
                                        OutlinedTextField(
                                            value = step.paramValue,
                                            onValueChange = { raw -> updateThisStep { it.copy(paramValue = raw) } },
                                            label = { Text(if (isProfileOverlay) "Judul ({user})" else "Teks ({user}/{gift})", fontSize = 8.5.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    OutlinedTextField(
                                        value = step.secondaryValue,
                                        onValueChange = { raw -> updateThisStep { it.copy(secondaryValue = raw) } },
                                        label = { Text(if (isMemeOrCustomImage) "Caption (opsional)" else "Subjudul", fontSize = 8.5.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedMsTextField(
                                        label = "Durasi(ms)",
                                        value = step.durationMs,
                                        onValueCommitted = { v ->
                                            updateThisStep { it.copy(durationMs = v) }
                                        },
                                        minValue = 0L,
                                        maxValue = 60_000L,
                                        maxDigits = 5,
                                        resetKey = step.id,
                                        modifier = Modifier.weight(0.75f)
                                    )
                                }

                                val sc = step.overlayScale.coerceIn(0.35f, 3.0f)
                                val curX = (step.posX ?: rule.overlayPosX).coerceIn(0.05f, 0.95f)
                                val curY = (step.posY ?: rule.overlayPosY).coerceIn(0.05f, 0.95f)
                                PrecisionStepperBar(
                                    label = "Ukuran Overlay",
                                    valueText = "${(sc * 100).roundToInt()}%",
                                    accent = NeonCyan,
                                    onDecrementCoarse = {
                                        updateThisStep { it.copy(overlayScale = (sc - 0.25f).coerceIn(0.35f, 3.0f)) }
                                    },
                                    onDecrement = {
                                        updateThisStep { it.copy(overlayScale = (sc - 0.05f).coerceIn(0.35f, 3.0f)) }
                                    },
                                    onIncrement = {
                                        updateThisStep { it.copy(overlayScale = (sc + 0.05f).coerceIn(0.35f, 3.0f)) }
                                    },
                                    onIncrementCoarse = {
                                        updateThisStep { it.copy(overlayScale = (sc + 0.25f).coerceIn(0.35f, 3.0f)) }
                                    }
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PrecisionStepperBar(
                                        label = "Posisi X",
                                        valueText = "${(curX * 100).roundToInt()}%",
                                        accent = NeonOrange,
                                        decLabel = "◀",
                                        incLabel = "▶",
                                        onDecrement = { updateThisStep { it.copy(posX = (curX - 0.04f).coerceIn(0.05f, 0.95f)) } },
                                        onIncrement = { updateThisStep { it.copy(posX = (curX + 0.04f).coerceIn(0.05f, 0.95f)) } },
                                        modifier = Modifier.weight(1f)
                                    )
                                    PrecisionStepperBar(
                                        label = "Posisi Y",
                                        valueText = "${(curY * 100).roundToInt()}%",
                                        accent = NeonPurple,
                                        decLabel = "▲",
                                        incLabel = "▼",
                                        onDecrement = { updateThisStep { it.copy(posY = (curY - 0.04f).coerceIn(0.05f, 0.95f)) } },
                                        onIncrement = { updateThisStep { it.copy(posY = (curY + 0.04f).coerceIn(0.05f, 0.95f)) } },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else if (step.actionType == ActionType.PLAY_SOUND || step.actionType == ActionType.CUSTOM_BACKSOUND) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("CHIME", "ALERT", "FANFARE", "BASS").forEach { preset ->
                                    FilterChip(
                                        selected = step.paramValue.equals(preset, ignoreCase = true),
                                        onClick = { updateThisStep { it.copy(paramValue = preset) } },
                                        label = { Text(preset, fontSize = 8.5.sp) }
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    FloatingPickerBridge.launch(
                                        context = dlgContext,
                                        mode = FloatingPickerMode.PICK_AUDIO,
                                        requestKey = "inapp_sfx_${step.id}"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth().height(32.dp)
                            ) {
                                Icon(Icons.Default.AudioFile, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload File MP3 / SFX", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        } else if (step.actionType == ActionType.VOLUME_CONTROL) {
                            val vol = step.paramValue.trim().toIntOrNull()?.coerceIn(0, 100) ?: 80
                            PrecisionStepperBar(
                                label = "Volume SFX",
                                valueText = "$vol%",
                                accent = NeonCyan,
                                onDecrementCoarse = { updateThisStep { it.copy(paramValue = "${(vol - 20).coerceIn(0, 100)}") } },
                                onDecrement = { updateThisStep { it.copy(paramValue = "${(vol - 5).coerceIn(0, 100)}") } },
                                onIncrement = { updateThisStep { it.copy(paramValue = "${(vol + 5).coerceIn(0, 100)}") } },
                                onIncrementCoarse = { updateThisStep { it.copy(paramValue = "${(vol + 20).coerceIn(0, 100)}") } }
                            )
                        } else if (step.actionType != ActionType.STOP_SOUND) {
                            OutlinedTextField(
                                value = step.paramValue,
                                onValueChange = { raw -> updateThisStep { it.copy(paramValue = raw) } },
                                label = { Text(step.actionType.defaultParamHint.ifBlank { "Parameter" }, fontSize = 8.5.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!isDevice2SimpleMode) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = step.executionMode == ExecutionMode.SEQUENTIAL,
                                    onClick = { updateThisStep { it.copy(executionMode = ExecutionMode.SEQUENTIAL) } },
                                    label = { Text("Berurutan", fontSize = 8.sp) }
                                )
                                FilterChip(
                                    selected = step.executionMode == ExecutionMode.PARALLEL,
                                    onClick = { updateThisStep { it.copy(executionMode = ExecutionMode.PARALLEL) } },
                                    label = { Text("Bersamaan", fontSize = 8.sp) }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "+ Tambah Koreografi:",
                    color = EmeraldGreen,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
                val lastPin = draftSteps.lastOrNull {
                    it.actionType == ActionType.SIMULATED_CLICK || it.actionType == ActionType.SIMULATED_SWIPE
                }
                val nx = ((lastPin?.posX ?: rule.overlayPosX) + if (lastPin == null) 0f else 0.05f).coerceIn(0.05f, 0.95f)
                val ny = ((lastPin?.posY ?: rule.overlayPosY) - if (lastPin == null) 0f else 0.05f).coerceIn(0.05f, 0.95f)

                fun addStep(type: ActionType) {
                    val nextStep = when (type) {
                        ActionType.SIMULATED_CLICK -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.SIMULATED_CLICK,
                            paramValue = String.format(Locale.US, "%.3f,%.3f", nx, ny),
                            secondaryValue = "70",
                            durationMs = 70L,
                            posX = nx,
                            posY = ny,
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                        ActionType.SIMULATED_SWIPE -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.SIMULATED_SWIPE,
                            paramValue = "RIGHT",
                            secondaryValue = "220",
                            durationMs = 220L,
                            posX = nx,
                            posY = ny,
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                        ActionType.DELAY -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.DELAY,
                            paramValue = "500",
                            durationMs = 500L,
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                        ActionType.HOME_BACK_GAME -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.HOME_BACK_GAME,
                            paramValue = "1500",
                            durationMs = 1500L,
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                        ActionType.MEME_OVERLAY -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.MEME_OVERLAY,
                            paramValue = "",
                            secondaryValue = "{user}",
                            durationMs = 2500L,
                            posX = rule.overlayPosX,
                            posY = rule.overlayPosY,
                            executionMode = ExecutionMode.PARALLEL
                        )
                        ActionType.PROFILE_OVERLAY -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.PROFILE_OVERLAY,
                            paramValue = "{user}",
                            secondaryValue = "{gift}",
                            durationMs = 2500L,
                            posX = rule.overlayPosX,
                            posY = rule.overlayPosY,
                            executionMode = ExecutionMode.PARALLEL
                        )
                        ActionType.TEXT_OVERLAY -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.TEXT_OVERLAY,
                            paramValue = "{user} • {gift}",
                            durationMs = 2500L,
                            posX = rule.overlayPosX,
                            posY = rule.overlayPosY,
                            executionMode = ExecutionMode.PARALLEL
                        )
                        ActionType.ANIMATION -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.ANIMATION,
                            paramValue = "BOUNCE",
                            durationMs = 2000L,
                            posX = rule.overlayPosX,
                            posY = rule.overlayPosY,
                            executionMode = ExecutionMode.PARALLEL
                        )
                        ActionType.RANDOM_SELECTION -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.RANDOM_SELECTION,
                            paramValue = "Opsi A|Opsi B|Opsi C",
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                        ActionType.HIDE_OVERLAY -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.HIDE_OVERLAY,
                            paramValue = "ALL",
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                        ActionType.STOP_SOUND -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.STOP_SOUND,
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                        ActionType.PLAY_SOUND -> UniversalActionStep(
                            enabled = true,
                            actionType = ActionType.PLAY_SOUND,
                            paramValue = "CHIME",
                            executionMode = ExecutionMode.PARALLEL
                        )
                        else -> UniversalActionStep(
                            enabled = true,
                            actionType = type,
                            executionMode = ExecutionMode.SEQUENTIAL
                        )
                    }
                    draftSteps = draftSteps + nextStep
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {

                    OutlinedButton(onClick = { addStep(ActionType.SIMULATED_CLICK) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("+ Klik ML", fontSize = 9.sp, color = EmeraldGreen)
                    }
                    OutlinedButton(onClick = { addStep(ActionType.SIMULATED_SWIPE) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("+ Geser ML", fontSize = 9.sp, color = NeonCyan)
                    }
                    OutlinedButton(onClick = { addStep(ActionType.DELAY) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("+ Jeda", fontSize = 9.sp, color = Color.White)
                    }
                    OutlinedButton(onClick = { addStep(ActionType.HOME_BACK_GAME) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("+ Home→Game", fontSize = 9.sp, color = NeonOrange)
                    }
                    OutlinedButton(onClick = { addStep(ActionType.MEME_OVERLAY) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("+ 😂 Foto Meme", fontSize = 9.sp, color = NeonPurple)
                    }
                    OutlinedButton(onClick = { addStep(ActionType.PROFILE_OVERLAY) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("+ 👤 Foto Profil", fontSize = 9.sp, color = NeonCyan)
                    }
                    OutlinedButton(onClick = { addStep(ActionType.PLAY_SOUND) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("+ Sound", fontSize = 9.sp, color = NeonCyan)
                    }
                    OutlinedButton(onClick = { showMoreAddMenu = !showMoreAddMenu }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(if (showMoreAddMenu) "▲ Custom" else "+ Custom", fontSize = 9.sp, color = Color.White)
                    }
                }
                if (showMoreAddMenu) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        OutlinedButton(onClick = { addStep(ActionType.TEXT_OVERLAY) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("+ 📝 Teks", fontSize = 9.sp, color = NeonCyan)
                        }
                        OutlinedButton(onClick = { addStep(ActionType.ANIMATION) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("+ ✨ Animasi", fontSize = 9.sp, color = EmeraldGreen)
                        }
                        OutlinedButton(onClick = { addStep(ActionType.RANDOM_SELECTION) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("+ 🎲 Random", fontSize = 9.sp, color = Color.White)
                        }
                        OutlinedButton(onClick = { addStep(ActionType.HIDE_OVERLAY) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("+ 🧹 Hide", fontSize = 9.sp, color = Color.White)
                        }
                        OutlinedButton(onClick = { addStep(ActionType.STOP_SOUND) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("+ 🔇 Stop Sound", fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
                } // end DialogConceptSectionCard 3
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(buildDraftRule()) },
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
