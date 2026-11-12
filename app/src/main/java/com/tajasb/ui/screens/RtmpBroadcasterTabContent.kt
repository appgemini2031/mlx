package com.tajasb.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.tajasb.model.VcamConfig
import com.tajasb.rtmp.RtmpBitratePreset
import com.tajasb.rtmp.RtmpPlatformPreset
import com.tajasb.rtmp.RtmpStreamEngine
import com.tajasb.rtmp.RtmpStreamState
import com.tajasb.ui.VcamViewModel
import java.util.Locale

@Composable
fun RtmpBroadcasterTabContent(
    viewModel: VcamViewModel,
    vcamConfig: VcamConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rtmpEngine = remember { RtmpStreamEngine.getInstance(context) }
    val rtmpConfig by rtmpEngine.config.collectAsState()
    val savedProfiles by rtmpEngine.savedProfiles.collectAsState()
    val telemetry by rtmpEngine.telemetry.collectAsState()
    val logs by rtmpEngine.logs.collectAsState()

    var showStreamKey by rememberSaveable { mutableStateOf(false) }
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var profileNameInput by remember { mutableStateOf("") }
    var isLogConsoleExpanded by rememberSaveable { mutableStateOf(true) }

    if (showSaveProfileDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Simpan Profil RTMP (${rtmpConfig.platform.label})",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Simpan URL & Stream Key ini agar bisa dipilih kembali dengan 1 klik (misal: Akun YT Utama, FB Page Toko, IG Live).",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = profileNameInput,
                        onValueChange = { profileNameInput = it },
                        placeholder = { Text("Contoh: ${rtmpConfig.platform.label} Toko Utama", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rtmp_profile_name_dialog_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val saved = rtmpEngine.saveCurrentProfile(profileNameInput)
                        viewModel.showUserMessage("💾 Profil '${saved.profileName}' berhasil disimpan")
                        profileNameInput = ""
                        showSaveProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF090D16)),
                    modifier = Modifier.testTag("rtmp_confirm_save_profile_button")
                ) {
                    Text("Simpan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProfileDialog = false }) {
                    Text("Batal", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. LIVE TELEMETRY & STATUS MONITOR CARD
        item {
            val stateColor = when (telemetry.state) {
                RtmpStreamState.LIVE -> EmeraldGreen
                RtmpStreamState.CONNECTING, RtmpStreamState.HANDSHAKING, RtmpStreamState.TESTING -> NeonCyan
                RtmpStreamState.RECONNECTING -> Color(0xFFF59E0B)
                RtmpStreamState.ERROR -> Color(0xFFEF4444)
                RtmpStreamState.IDLE -> Color(0xFF94A3B8)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rtmp_telemetry_card"),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(
                    width = if (telemetry.state == RtmpStreamState.LIVE) 1.2.dp else 0.8.dp,
                    color = if (telemetry.state == RtmpStreamState.LIVE) EmeraldGreen.copy(alpha = 0.7f) else DarkCardBorder
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(stateColor)
                            )
                            Text(
                                text = "RTMP / RTMPS DIRECT BROADCASTER",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(stateColor.copy(alpha = 0.16f))
                                .border(0.8.dp, stateColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("rtmp_state_badge")
                        ) {
                            Text(
                                text = telemetry.state.label,
                                color = stateColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Text(
                        text = telemetry.statusMessage,
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 4-Column Telemetry Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TelemetryMetricBox(
                            label = "DURASI",
                            value = formatDurationHms(telemetry.uptimeSeconds),
                            accentColor = if (telemetry.isBroadcasting) EmeraldGreen else Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMetricBox(
                            label = "BITRATE",
                            value = "${telemetry.currentBitrateKbps} kbps",
                            accentColor = NeonCyan,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMetricBox(
                            label = "FPS",
                            value = "${telemetry.currentFps} fps",
                            accentColor = NeonPurple,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMetricBox(
                            label = "TERKIRIM",
                            value = formatMegabytes(telemetry.totalBytesSent),
                            accentColor = Color(0xFFFBBF24),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Active Playlist & Audio/Keyframe Status Indicator
                    val activeVideoTitle = vcamConfig.currentVideo?.name ?: "Belum ada video dipilih di tab Video"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎬 Video: $activeVideoTitle",
                                color = Color(0xFFCBD5E1),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${vcamConfig.playlist.size} Video",
                                color = NeonCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔊 Audio RTMP: ${telemetry.audioSourceDescription}",
                                color = EmeraldGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "⚡ Keyframe: ${telemetry.keyframeIntervalSec}s (0.5 fps)",
                                color = if (telemetry.keyframeIntervalSec <= 2.5f) NeonCyan else Color(0xFFFBBF24),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. PLATFORM PRESET & SERVER CONFIGURATION CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(0.8.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1. Pilih Platform Tujuan (YT • FB • IG • TikTok)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RtmpPlatformPreset.entries.forEach { preset ->
                            val isSelected = rtmpConfig.platform == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) NeonCyan.copy(alpha = 0.18f) else DarkSurfaceVariant
                                    )
                                    .border(
                                        width = if (isSelected) 1.dp else 0.6.dp,
                                        color = if (isSelected) NeonCyan else DarkCardBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(enabled = !telemetry.isBroadcasting) {
                                        rtmpEngine.selectPlatform(preset)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                    .testTag("rtmp_platform_${preset.id.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${preset.badgeIcon} ${preset.label}",
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Helper Tip Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonCyan.copy(alpha = 0.08f))
                            .border(0.6.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "💡 ${rtmpConfig.platform.helperTip}",
                            color = Color(0xFFBAE6FD),
                            fontSize = 9.5.sp
                        )
                    }

                    // Server URL Input
                    OutlinedTextField(
                        value = rtmpConfig.serverUrl,
                        onValueChange = { rtmpEngine.updateServerUrl(it) },
                        enabled = !telemetry.isBroadcasting,
                        label = { Text("Server URL (rtmp:// atau rtmps://)", fontSize = 10.5.sp) },
                        placeholder = { Text(rtmpConfig.platform.defaultServerUrl, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rtmp_server_url_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color(0xFF94A3B8),
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        )
                    )

                    // Quick Switch: Server Utama & Cadangan (YouTube / Custom)
                    if (rtmpConfig.platform.backupServerUrl != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isDefaultActive = rtmpConfig.serverUrl == rtmpConfig.platform.defaultServerUrl
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDefaultActive) NeonCyan.copy(alpha = 0.16f) else DarkSurfaceVariant)
                                    .border(0.8.dp, if (isDefaultActive) NeonCyan else DarkCardBorder, RoundedCornerShape(6.dp))
                                    .clickable(enabled = !telemetry.isBroadcasting) {
                                        rtmpEngine.updateServerUrl(rtmpConfig.platform.defaultServerUrl)
                                    }
                                    .padding(vertical = 5.dp, horizontal = 6.dp)
                                    .testTag("rtmp_btn_server_default"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🔴 Utama: a.rtmp",
                                    color = if (isDefaultActive) NeonCyan else Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isDefaultActive) FontWeight.Bold else FontWeight.Medium
                                )
                            }

                            val isBackupActive = rtmpConfig.serverUrl == rtmpConfig.platform.backupServerUrl
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isBackupActive) EmeraldGreen.copy(alpha = 0.16f) else DarkSurfaceVariant)
                                    .border(0.8.dp, if (isBackupActive) EmeraldGreen else DarkCardBorder, RoundedCornerShape(6.dp))
                                    .clickable(enabled = !telemetry.isBroadcasting) {
                                        rtmpConfig.platform.backupServerUrl?.let { rtmpEngine.updateServerUrl(it) }
                                    }
                                    .padding(vertical = 5.dp, horizontal = 6.dp)
                                    .testTag("rtmp_btn_server_backup"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🛡️ Cadangan: b.rtmp (backup=1)",
                                    color = if (isBackupActive) EmeraldGreen else Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isBackupActive) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Stream Key Input + Visibility & Paste
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = rtmpConfig.streamKey,
                            onValueChange = { rtmpEngine.updateStreamKey(it) },
                            enabled = !telemetry.isBroadcasting,
                            label = { Text("Stream Key / Kunci Siaran", fontSize = 10.5.sp) },
                            placeholder = { Text("Tempel Stream Key di sini...", fontSize = 11.sp) },
                            singleLine = true,
                            visualTransformation = if (showStreamKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showStreamKey = !showStreamKey },
                                    modifier = Modifier.testTag("rtmp_toggle_key_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (showStreamKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Tampilkan/Sembunyikan Stream Key",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rtmp_stream_key_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                disabledTextColor = Color(0xFF94A3B8),
                                focusedLabelColor = NeonCyan,
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            )
                        )

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                if (!pasted.isNullOrEmpty()) {
                                    rtmpEngine.updateStreamKey(pasted)
                                    viewModel.showUserMessage("📋 Stream Key ditempel dari clipboard")
                                } else {
                                    viewModel.showUserMessage("⚠️ Clipboard kosong")
                                }
                            },
                            enabled = !telemetry.isBroadcasting,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                            border = BorderStroke(0.8.dp, NeonCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            modifier = Modifier.testTag("rtmp_paste_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Tempel Stream Key",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Action Buttons: Tes Server, Simpan Profil, Mulai/Hentikan Live
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                rtmpEngine.testServerConnection { _, msg ->
                                    viewModel.showUserMessage(msg)
                                }
                            },
                            enabled = !telemetry.isBroadcasting && telemetry.state != RtmpStreamState.TESTING,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            border = BorderStroke(0.8.dp, DarkCardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.testTag("rtmp_test_server_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = "Tes Server",
                                modifier = Modifier.size(15.dp),
                                tint = NeonCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tes Server", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                profileNameInput = "${rtmpConfig.platform.label} Live"
                                showSaveProfileDialog = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            border = BorderStroke(0.8.dp, DarkCardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.testTag("rtmp_save_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "Simpan Profil",
                                modifier = Modifier.size(15.dp),
                                tint = NeonPurple
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                if (telemetry.isBroadcasting) {
                                    rtmpEngine.stopBroadcast { msg -> viewModel.showUserMessage(msg) }
                                } else {
                                    rtmpEngine.startBroadcast { msg -> viewModel.showUserMessage(msg) }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (telemetry.isBroadcasting) Color(0xFFEF4444) else EmeraldGreen,
                                contentColor = if (telemetry.isBroadcasting) Color.White else Color(0xFF090D16)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rtmp_start_stop_button")
                        ) {
                            Icon(
                                imageVector = if (telemetry.isBroadcasting) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (telemetry.isBroadcasting) "HENTIKAN LIVE" else "MULAI LIVE RTMP",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 3. SAVED PROFILES CARD (Multi-Akun YT/FB/IG)
        if (savedProfiles.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rtmp_saved_profiles_card"),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(0.8.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🔖 Profil Akun RTMP Tersimpan (${savedProfiles.size})",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            savedProfiles.forEach { prof ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceVariant)
                                        .border(0.7.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                                        .clickable(enabled = !telemetry.isBroadcasting) {
                                            rtmpEngine.loadProfile(prof)
                                            viewModel.showUserMessage("✅ Profil '${prof.profileName}' dimuat")
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${prof.config.platform.badgeIcon} ${prof.profileName}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus Profil",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable {
                                                rtmpEngine.deleteProfile(prof.id)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. BITRATE, ORIENTATION & CONTINUITY SETTINGS CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(0.8.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "2. Kualitas Encoder, Resolusi & Siklus Otomatis",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Bitrate Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RtmpBitratePreset.entries.forEach { preset ->
                            val selected = rtmpConfig.bitratePreset == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (selected) NeonPurple.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                    .border(
                                        width = if (selected) 1.dp else 0.6.dp,
                                        color = if (selected) NeonPurple else DarkCardBorder,
                                        shape = RoundedCornerShape(7.dp)
                                    )
                                    .clickable { rtmpEngine.updateBitratePreset(preset) }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                                    .testTag("rtmp_bitrate_${preset.id.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = preset.shortBadge,
                                    color = if (selected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Orientation Selector (Portrait 9:16 vs Landscape 16:9)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val orientations = listOf(
                            Pair(true, "📱 Portrait 9:16 (${minOf(rtmpConfig.bitratePreset.width, rtmpConfig.bitratePreset.height)}x${maxOf(rtmpConfig.bitratePreset.width, rtmpConfig.bitratePreset.height)})"),
                            Pair(false, "🖥️ Landscape 16:9 (${maxOf(rtmpConfig.bitratePreset.width, rtmpConfig.bitratePreset.height)}x${minOf(rtmpConfig.bitratePreset.width, rtmpConfig.bitratePreset.height)})")
                        )
                        orientations.forEach { (portraitVal, label) ->
                            val selected = rtmpConfig.isPortrait == portraitVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (selected) NeonCyan.copy(alpha = 0.16f) else DarkSurfaceVariant)
                                    .border(
                                        width = if (selected) 0.9.dp else 0.6.dp,
                                        color = if (selected) NeonCyan else DarkCardBorder,
                                        shape = RoundedCornerShape(7.dp)
                                    )
                                    .clickable { rtmpEngine.updateOrientation(portraitVal) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Feature Toggles
                    RtmpToggleRow(
                        title = "🔄 Auto-Reconnect Jaringan (Live 24/7 Nonstop)",
                        subtitle = "Otomatis menyambung ulang jika sinyal internet terputus sesaat tanpa mematikan sesi Live.",
                        checked = rtmpConfig.autoReconnect,
                        onCheckedChange = { rtmpEngine.updateAutoReconnect(it) },
                        testTag = "rtmp_toggle_auto_reconnect"
                    )

                    RtmpToggleRow(
                        title = "🎬 Sinkron Otomatis Playlist, Master Spill & IFS",
                        subtitle = "Saat video berganti di Playlist, Spill produk, atau siklus IFS, aliran RTMP berpindah mulus tanpa putus koneksi.",
                        checked = rtmpConfig.syncPlaylistAndSpill,
                        onCheckedChange = { rtmpEngine.updateSyncPlaylistAndSpill(it) },
                        testTag = "rtmp_toggle_sync_playlist"
                    )

                    RtmpToggleRow(
                        title = "🛡️ Injeksi Metadata Anti-Hash RTMP",
                        subtitle = "Menyamarkan tanda tangan encoder stream (onMetaData) mengikuti profil DNA sesi aktif agar aman dari deteksi re-stream.",
                        checked = rtmpConfig.applyAntiHashMetadata,
                        onCheckedChange = { rtmpEngine.updateApplyAntiHashMetadata(it) },
                        testTag = "rtmp_toggle_anti_hash"
                    )

                    RtmpToggleRow(
                        title = "⚡ Live Transcoder Keyframe 2 Detik (Standar Facebook/IG)",
                        subtitle = "Mengunci interval keyframe tepat 2.0 detik (0.5 fps) untuk menghilangkan peringatan 'Too low key frame rate' pada Facebook Live.",
                        checked = rtmpConfig.isLiveTranscoderEnabled,
                        onCheckedChange = { rtmpEngine.updateLiveTranscoderEnabled(it) },
                        testTag = "rtmp_toggle_live_transcoder"
                    )
                }
            }
        }

        // 5. RTMP PROTOCOL CONSOLE LOG
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(0.8.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "📟 Log Protokol RTMP (${logs.size})",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { isLogConsoleExpanded = !isLogConsoleExpanded }
                            )
                            Text(
                                text = "• Terbaru di atas",
                                color = NeonCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Bersihkan",
                                color = NeonCyan,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { rtmpEngine.clearLogs() }
                            )
                            Text(
                                text = if (isLogConsoleExpanded) "Sembunyikan ▲" else "Tampilkan ▼",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                modifier = Modifier.clickable { isLogConsoleExpanded = !isLogConsoleExpanded }
                            )
                        }
                    }

                    if (isLogConsoleExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF06090F))
                                .border(0.6.dp, DarkCardBorder, RoundedCornerShape(7.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            logs.take(25).forEachIndexed { idx, line ->
                                Text(
                                    text = line,
                                    color = if (idx == 0) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricBox(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(DarkSurfaceVariant)
            .border(0.6.dp, DarkCardBorder, RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = accentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RtmpToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 8.5.sp,
                lineHeight = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF090D16),
                checkedTrackColor = NeonCyan,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = DarkBackground
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

private fun formatDurationHms(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
}

private fun formatMegabytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
