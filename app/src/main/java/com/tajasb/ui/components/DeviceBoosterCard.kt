package com.tajasb.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.tajasb.model.TargetAppInfo
import com.tajasb.optimizer.LiveFocusVpnService
import com.tajasb.ui.VcamViewModel

@Composable
fun DeviceBoosterCard(
    viewModel: VcamViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val memoryStats by viewModel.memoryStats.collectAsState()
    val installedApps by viewModel.targetApps.collectAsState()

    var showWhitelistDialog by remember { mutableStateOf(false) }
    var showVpnBypassDialog by remember { mutableStateOf(false) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleLiveFocusVpn(true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshMemoryStats()
        if (installedApps.isEmpty()) {
            viewModel.loadInstalledApps()
        }
    }

    val stats = memoryStats ?: viewModel.repository.getDeviceMemoryStats()
    val totalRam = stats.totalRamMb.coerceAtLeast(1)
    val availRam = stats.availableRamMb
    val percentFree = stats.percentFree
    val percentUsed = stats.percentUsed / 100f

    val isVpnOn = config.isLiveFocusVpnEnabled || stats.isVpnActive
    val fpsLimit = config.fpsGovernorLimit

    val currentSpeedMbps = stats.networkSpeedMbps
    val currentNetType = stats.networkTypeLabel
    val tier = stats.tier
    val temp = stats.batteryTempC
    val tempText = if (temp > 0f) "${temp}°C" else "Normal"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_device_booster"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isVpnOn) NeonCyan.copy(alpha = 0.45f) else DarkCardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // BARIS 1: HEADER SERAGAM (Badge Ikon 26.dp + Judul 11.sp + ! + Refresh + Tombol Bersihkan RAM)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(NeonCyan.copy(alpha = 0.14f))
                            .border(0.6.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "Optimasi & Jaringan",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                    FeatureInfoButton(
                        title = "Optimasi Device & Fokus Jaringan",
                        description = "• Profil Perangkat: ${tier.title} (Target: ${tier.targetResolution} ${tier.targetFps}) — ${tier.recommendation}\n\n" +
                            "• RAM: Bebas $availRam MB dari $totalRam MB ($percentFree% Bebas • ${stats.statusLabel})\n\n" +
                            "• Suhu HP: $tempText | Jaringan: $currentSpeedMbps Mbps ($currentNetType)\n\n" +
                            "• Auto-Clean RAM: Membersihkan memori latar otomatis saat sisa RAM turun di bawah 380 MB atau setiap 30 menit.\n\n" +
                            "• Fokus Jaringan Live: Memfokuskan bandwidth internet khusus untuk aplikasi target live dan memblokir pemakaian data aplikasi latar belakang.\n\n" +
                            "• Batas FPS: Mengunci frame rate kamera (25/30/60 FPS) agar suhu HP tetap dingin.\n\n" +
                            "• Auto-Rocket (Anti-Freeze): Mendeteksi macet/freeze (2s/4s/6s) dan memulihkan aliran kamera secara otomatis tanpa menekan roket manual.",
                        accentColor = NeonCyan
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.refreshMemoryStats() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Status",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Button(
                        onClick = { viewModel.cleanGalleryDuplicates() },
                        modifier = Modifier
                            .height(27.dp)
                            .testTag("btn_clean_gallery_duplicates"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = EmeraldGreen
                        ),
                        border = androidx.compose.foundation.BorderStroke(0.7.dp, EmeraldGreen.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Bersihkan Duplikat Galeri",
                            modifier = Modifier.size(11.dp),
                            tint = EmeraldGreen
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Bersihkan Duplikat Galeri",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { viewModel.triggerTurboRamClean() },
                        modifier = Modifier
                            .height(27.dp)
                            .testTag("btn_turbo_ram_clean"),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = NeonCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(0.7.dp, NeonCyan.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = NeonCyan
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Boost RAM",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // BARIS 2: INDIKATOR TELEMETRI MINIMALIS + PROGRESS BAR TIPIS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF090D16))
                    .border(0.6.dp, DarkCardBorder.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RAM $availRam/$totalRam MB ($percentFree%)",
                        fontSize = 8.8.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "Suhu $tempText",
                        fontSize = 8.8.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (temp >= 40f) Color(0xFFF43F5E) else Color(0xFF94A3B8)
                    )
                    Text(
                        text = "$currentSpeedMbps Mbps",
                        fontSize = 8.8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldGreen
                    )
                }

                LinearProgressIndicator(
                    progress = { percentUsed },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = when {
                        stats.isLowRam -> Color(0xFFF43F5E)
                        percentFree < 25 -> Color(0xFFF59E0B)
                        else -> NeonCyan
                    },
                    trackColor = Color(0xFF1E293B)
                )
            }

            // BARIS 3: FOKUS JARINGAN & BYPASS SEJAJAR 1 BARIS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF090D16))
                    .border(0.6.dp, DarkCardBorder.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Fokus Jaringan",
                        fontSize = 9.2.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isVpnOn) NeonCyan else Color(0xFFE2E8F0)
                    )
                    val bypassCount = config.vpnBypassPackages.size
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E293B))
                            .border(0.5.dp, DarkCardBorder, RoundedCornerShape(4.dp))
                            .clickable { showVpnBypassDialog = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (bypassCount > 0) "Bypass ($bypassCount) ▾" else "Bypass ▾",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan
                        )
                    }
                }

                Switch(
                    checked = isVpnOn,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val prepIntent = LiveFocusVpnService.getPrepareIntent(context)
                            if (prepIntent != null) {
                                vpnLauncher.launch(prepIntent)
                            } else {
                                viewModel.toggleLiveFocusVpn(true)
                            }
                        } else {
                            viewModel.toggleLiveFocusVpn(false)
                        }
                    },
                    modifier = Modifier
                        .scale(0.65f)
                        .height(20.dp)
                        .testTag("switch_live_focus_vpn")
                )
            }

            // BARIS 4: AUTO-CLEAN RAM, WHITELIST & RUTIN 30M SEJAJAR 1 BARIS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF090D16))
                    .border(0.6.dp, DarkCardBorder.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Auto-RAM",
                        fontSize = 9.2.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0)
                    )
                    Switch(
                        checked = config.isAutoRamCleanEnabled,
                        onCheckedChange = { viewModel.toggleAutoRamClean(it) },
                        modifier = Modifier
                            .scale(0.62f)
                            .height(20.dp)
                    )
                    val wlCount = config.ramCleanWhitelistPackages.size
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E293B))
                            .border(0.5.dp, DarkCardBorder, RoundedCornerShape(4.dp))
                            .clickable { showWhitelistDialog = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (wlCount > 0) "Whitelist ($wlCount) ▾" else "Whitelist ▾",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Tiap 30m",
                        fontSize = 8.2.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Switch(
                        checked = config.isPeriodicRamCleanEnabled,
                        onCheckedChange = { viewModel.togglePeriodicRamClean(it) },
                        modifier = Modifier
                            .scale(0.62f)
                            .height(20.dp)
                    )
                }
            }

            // BARIS 5: BATAS FPS SEJAJAR 1 BARIS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF090D16))
                    .border(0.6.dp, DarkCardBorder.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Batas FPS Kamera",
                    fontSize = 9.2.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E8F0)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(25, 30, 60).forEach { targetFps ->
                        val isSelected = fpsLimit == targetFps
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSelected) NeonCyan.copy(alpha = 0.18f) else Color(0xFF1E293B)
                                )
                                .border(
                                    0.6.dp,
                                    if (isSelected) NeonCyan else DarkCardBorder,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { viewModel.setFpsGovernorLimit(targetFps) }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$targetFps FPS",
                                fontSize = 8.2.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) NeonCyan else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            // BARIS 6: AUTO-ROCKET SEJAJAR 1 BARIS (Saklar + Deteksi 2s/4s/6s + Tes)
            val isAutoRocketActive = config.isAutoRocketRecoveryEnabled
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF090D16))
                    .border(
                        0.6.dp,
                        if (isAutoRocketActive) EmeraldGreen.copy(alpha = 0.45f) else DarkCardBorder.copy(alpha = 0.7f),
                        RoundedCornerShape(7.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Auto-Rocket",
                        fontSize = 9.2.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isAutoRocketActive) EmeraldGreen else Color(0xFFE2E8F0)
                    )
                    listOf(2, 4, 6).forEach { sec ->
                        val isCur = config.autoRecoveryStallThresholdSec == sec
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isCur) EmeraldGreen.copy(alpha = 0.18f) else Color(0xFF1E293B))
                                .border(0.6.dp, if (isCur) EmeraldGreen else DarkCardBorder, RoundedCornerShape(4.dp))
                                .clickable { viewModel.setAutoRecoveryStallThresholdSec(sec) }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${sec}s",
                                fontSize = 8.sp,
                                fontWeight = if (isCur) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCur) EmeraldGreen else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.testAutoRocketRecovery() },
                        modifier = Modifier
                            .height(22.dp)
                            .testTag("btn_test_auto_rocket"),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(0.6.dp, NeonCyan.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "Tes",
                            fontSize = 8.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Switch(
                        checked = isAutoRocketActive,
                        onCheckedChange = { viewModel.toggleAutoRocketRecovery(it) },
                        modifier = Modifier
                            .scale(0.65f)
                            .height(20.dp)
                            .testTag("switch_auto_rocket_recovery")
                    )
                }
            }

            // BARIS 7: PRE-FLIGHT LIVE 24 JAM CHECK & SELF-HEAL DIAGNOSTIC
            val readinessReport by viewModel.lastReadinessReport.collectAsState()
            var showReadinessDetails by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF090D16))
                    .border(
                        0.6.dp,
                        if (readinessReport?.isAllReady == true) EmeraldGreen.copy(alpha = 0.45f) else DarkCardBorder.copy(alpha = 0.7f),
                        RoundedCornerShape(7.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (readinessReport?.isAllReady == true) EmeraldGreen else NeonCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = readinessReport?.let { "Kesiapan Live 24J (${it.readyScore}%)" } ?: "Cek Kesiapan Live 24J",
                            fontSize = 9.2.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (readinessReport != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1E293B))
                                    .clickable { showReadinessDetails = !showReadinessDetails }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (showReadinessDetails) "Tutup ▴" else "Detail ▾",
                                    fontSize = 8.sp,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.runPreFlightCheckAndHeal()
                                showReadinessDetails = true
                            },
                            modifier = Modifier
                                .height(22.dp)
                                .testTag("btn_preflight_live_check"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(5.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = NeonCyan
                            ),
                            border = androidx.compose.foundation.BorderStroke(0.6.dp, NeonCyan.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = NeonCyan
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Auto-Heal",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showReadinessDetails && readinessReport != null) {
                    readinessReport?.let { rep ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF111827))
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.5.dp)
                        ) {
                            rep.details.forEach { line ->
                                Text(
                                    text = line,
                                    fontSize = 8.2.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog 1: Pilih Whitelist RAM
    if (showWhitelistDialog) {
        AppSelectionDialog(
            title = "🛡️ Pilih Whitelist RAM (Anti-Kill)",
            subtitle = "Aplikasi yang dicentang TIDAK AKAN DITUTUP saat Turbo RAM Clean bekerja.",
            allApps = installedApps,
            initialSelected = if (config.ramCleanWhitelistPackages.isNotEmpty()) {
                config.ramCleanWhitelistPackages
            } else {
                config.targetPackages
            },
            onDismiss = { showWhitelistDialog = false },
            onConfirm = { chosen ->
                viewModel.updateRamCleanWhitelist(chosen)
                showWhitelistDialog = false
            }
        )
    }

    // Modal Dialog 2: Pilih Blackhole / Bypass VPN
    if (showVpnBypassDialog) {
        AppSelectionDialog(
            title = "🎯 Pilih Aplikasi Bypass Internet (Bebas Data)",
            subtitle = "Aplikasi yang dicentang BEBAS MENGAKSES INTERNET. Semua aplikasi lain yang TIDAK DICENTANG akan otomatis DIBLOKIR (Blackhole).",
            allApps = installedApps,
            initialSelected = if (config.vpnBypassPackages.isNotEmpty()) {
                config.vpnBypassPackages
            } else {
                config.targetPackages
            },
            onDismiss = { showVpnBypassDialog = false },
            onConfirm = { chosen ->
                viewModel.updateVpnBypassPackages(chosen)
                showVpnBypassDialog = false
            }
        )
    }
}

/**
 * Komponen Dialog Pemilihan Aplikasi Fleksibel (Multi-select)
 * Mendukung pencarian, pilih semua, bersihkan pilihan, dan penyimpanan instan.
 */
@Composable
fun AppSelectionDialog(
    title: String,
    subtitle: String,
    allApps: List<TargetAppInfo>,
    initialSelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPkgs by remember { mutableStateOf(initialSelected) }

    val distinctApps = remember(allApps) {
        allApps.distinctBy { it.packageName }
    }

    val filteredApps = remember(distinctApps, searchQuery) {
        if (searchQuery.isBlank()) {
            distinctApps
        } else {
            val q = searchQuery.lowercase()
            distinctApps.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            lineHeight = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama aplikasi atau package...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = NeonCyan,
                        unfocusedIndicatorColor = Color(0xFF334155)
                    ),
                    singleLine = true
                )

                // Quick Select Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedPkgs.size} dari ${distinctApps.size} dipilih",
                        fontSize = 10.sp,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Pilih Semua",
                            fontSize = 10.sp,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier
                                .clickable {
                                    selectedPkgs = filteredApps.map { it.packageName }.toSet()
                                }
                                .padding(4.dp)
                        )
                        Text(
                            text = "Kosongkan",
                            fontSize = 10.sp,
                            color = Color(0xFFEF4444),
                            modifier = Modifier
                                .clickable {
                                    selectedPkgs = emptySet()
                                }
                                .padding(4.dp)
                        )
                    }
                }

                // Apps List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = filteredApps,
                        key = { index, app -> "${app.packageName}_$index" }
                    ) { _, app ->
                        val isChecked = selectedPkgs.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isChecked) Color(0xFF0C4A6E).copy(alpha = 0.35f) else Color(0xFF1E293B).copy(alpha = 0.5f))
                                .border(
                                    0.5.dp,
                                    if (isChecked) NeonCyan.copy(alpha = 0.5f) else Color(0xFF334155),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedPkgs = if (isChecked) {
                                        selectedPkgs - app.packageName
                                    } else {
                                        selectedPkgs + app.packageName
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedPkgs = if (checked) {
                                        selectedPkgs + app.packageName
                                    } else {
                                        selectedPkgs - app.packageName
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = NeonCyan,
                                    uncheckedColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 8.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (app.category.isNotBlank() && app.category != "Lainnya") {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF334155))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = app.category,
                                        fontSize = 7.5.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Batal", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(selectedPkgs) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Simpan Pilihan (${selectedPkgs.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
