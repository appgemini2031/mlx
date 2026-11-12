package com.tajasb.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.tajasb.data.FloatingConfigBackupManager
import com.tajasb.ui.VcamViewModel
import com.tajasb.ui.components.FeatureInfoButton
import kotlinx.coroutines.launch

@Composable
fun CameraSettingsSection(
    viewModel: VcamViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val isXposedActive by viewModel.isXposedActive.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // 1. FLOATING CONTROL LAUNCHER CARD (Compact 1 Row)
        FloatingControlLauncherCard(viewModel = viewModel, config = config)

        // 2. SAKLAR MODUL MENU FLOATING (Collapsible Dropdown + 2-Column Grid termasuk DNA & Hapus Kode AI)
        FloatingModulesCard(viewModel = viewModel, config = config)

        // 2A. PEMINDAI & PEMBERSIH KODE AI (Hanya tampil jika Saklar Hapus Kode AI diaktifkan)
        if (config.enableAiDetectionModule) {
            AiWatermarkScannerCard(viewModel = viewModel, config = config)
        }

        // 2B. ANTI-DETEKSI VIDEO, DNA ZOOM, DNA SESI & AUDIO ANTI-DETEKSI (Terpisah & Mandiri)
        AntiHashShieldCard(viewModel = viewModel, config = config)
        DnaZoomFramingCard(viewModel = viewModel, config = config)
        SessionDnaMutatorCard(viewModel = viewModel, config = config)
        AudioAntiHashShieldCard(viewModel = viewModel, config = config)

        // 3. CODEC GUARD (Compact 1 Row + ! Info)
        CodecGuardCard(viewModel = viewModel, config = config)

        // 5. STATUS LSPOSED / ROOT MODULE CARD (Compact + ! Info)
        LsposedStatusCard(viewModel = viewModel, isXposedActive = isXposedActive)

        // 5B. PANTAU LAYAR LIVE KE TELEGRAM (TIAP 2,5 MENIT • RESOLUSI RENDAH)
        TelegramScreenMonitorCard()

        // 6. CADANGAN PENGATURAN (Compact Sejajar + ! Info)
        BackupSettingsSection()
    }
}

@Composable
fun FloatingControlLauncherCard(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (config.isFloatingControlEnabled) NeonPurple.copy(alpha = 0.45f) else DarkCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            .background(NeonPurple.copy(alpha = 0.14f))
                            .border(0.6.dp, NeonPurple.copy(alpha = 0.35f), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPicture,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "Menu Melayang",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )

                    FeatureInfoButton(
                        title = "Menu Melayang (Floating Control)",
                        description = "• Menampilkan panel kontrol melayang (Floating Overlay) di atas aplikasi Live (TikTok / Shopee).\n" +
                            "• Memungkinkan Anda mengatur kamera virtual, rotasi, stiker, komentar, dan musik secara real-time tanpa keluar dari aplikasi Live.",
                        accentColor = NeonPurple
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (config.isFloatingControlEnabled) EmeraldGreen.copy(alpha = 0.14f)
                                else DarkSurfaceVariant
                            )
                            .border(
                                0.6.dp,
                                if (config.isFloatingControlEnabled) EmeraldGreen.copy(alpha = 0.45f) else DarkCardBorder,
                                RoundedCornerShape(5.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (config.isFloatingControlEnabled) "AKTIF" else "OFF",
                            color = if (config.isFloatingControlEnabled) EmeraldGreen else Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Switch(
                    checked = config.isFloatingControlEnabled,
                    onCheckedChange = { viewModel.toggleFloatingWidget(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonPurple,
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier
                        .scale(0.68f)
                        .height(22.dp)
                        .testTag("switch_floating_control")
                )
            }

            if (!viewModel.isOverlayPermissionGranted()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(AmberWarning.copy(alpha = 0.12f))
                        .border(0.6.dp, AmberWarning.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⚠️ Izin Overlay Belum Aktif",
                        color = AmberWarning,
                        fontSize = 9.2.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = { viewModel.openOverlaySettings() },
                        modifier = Modifier.height(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text("Izinkan", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingModulesCard(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val enabledCount = listOf(
        config.enableSalesNotifModule,
        config.enableStickerModule,
        config.enableWatermarkModule,
        config.enableAutoTimeModule,
        config.enablePrayerCommentModule,
        config.enableAutoTapModule,
        config.enableMp3MusicModule,
        config.enableOsdModule,
        config.enableDnaModule,
        config.enableAiDetectionModule,
        config.enableQuranAudioModule
    ).count { it }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkCardBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "Modul Menu Floating",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                    FeatureInfoButton(
                        title = "Modul Menu Floating",
                        description = "Tampilkan atau sembunyikan pintasan modul di dalam Jendela Melayang (Floating Overlay):\n" +
                            "• 🛒 Notifikasi Pembeli (Banner sosial bukti pembelian)\n" +
                            "• 🏷️ Stiker Tulisan (Teks promosi di atas video)\n" +
                            "• 💧 Watermark (Tanda air anti-curian konten)\n" +
                            "• 💬 Komentar Live (Pemicu interaktif komentar penonton)\n" +
                            "• 👆 Auto-Tap Layar (Otomatis ketuk layar selama Live)\n" +
                            "• 🎵 Musik BGM (Kontrol pemutar MP3 latar)\n" +
                            "• 📖 Audio Al-Qur'an (Download murottal 20 imam, tes suara, & playlist)\n" +
                            "• ⏱️ OSD Jam & FPS (Indikator waktu & performa)",
                        accentColor = NeonCyan
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(NeonCyan.copy(alpha = 0.14f))
                            .border(0.6.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$enabledCount/11 Aktif",
                            color = NeonCyan,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Buka/Tutup Modul",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(7.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactModuleChip(
                            icon = "🛒",
                            title = "Notif Beli",
                            checked = config.enableSalesNotifModule,
                            onCheckedChange = { viewModel.toggleSalesNotifModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                        CompactModuleChip(
                            icon = "🏷️",
                            title = "Stiker",
                            checked = config.enableStickerModule,
                            onCheckedChange = { viewModel.toggleStickerModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactModuleChip(
                            icon = "💧",
                            title = "Watermark",
                            checked = config.enableWatermarkModule,
                            onCheckedChange = { viewModel.toggleWatermarkModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                        CompactModuleChip(
                            icon = "💬",
                            title = "Komentar",
                            checked = config.enablePrayerCommentModule,
                            onCheckedChange = { viewModel.togglePrayerCommentModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactModuleChip(
                            icon = "👆",
                            title = "Auto-Tap",
                            checked = config.enableAutoTapModule,
                            onCheckedChange = { viewModel.toggleAutoTapModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                        CompactModuleChip(
                            icon = "🎵",
                            title = "Musik BGM",
                            checked = config.enableMp3MusicModule,
                            onCheckedChange = { viewModel.toggleMp3MusicModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactModuleChip(
                            icon = "⏱️",
                            title = "OSD & FPS",
                            checked = config.enableOsdModule,
                            onCheckedChange = { viewModel.toggleOsdModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                        CompactModuleChip(
                            icon = "⏰",
                            title = "Waktu Otomatis",
                            checked = config.enableAutoTimeModule,
                            onCheckedChange = { viewModel.toggleAutoTimeModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactModuleChip(
                            icon = "🧬",
                            title = "Modul DNA",
                            checked = config.enableDnaModule,
                            onCheckedChange = { viewModel.toggleDnaModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                        CompactModuleChip(
                            icon = "🧹",
                            title = "Hapus Kode AI",
                            checked = config.enableAiDetectionModule,
                            onCheckedChange = { viewModel.toggleAiDetectionModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactModuleChip(
                            icon = "📖",
                            title = "Audio Al-Qur'an",
                            checked = config.enableQuranAudioModule,
                            onCheckedChange = { viewModel.toggleQuranAudioModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                        CompactModuleChip(
                            icon = "🕌",
                            title = "Khatam 30 Juz",
                            checked = config.enableQuranKhatamModule,
                            onCheckedChange = { viewModel.toggleQuranKhatamModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactModuleChip(
                            icon = "🕋",
                            title = "Relay Mekah",
                            checked = config.enableMakkahRelayModule,
                            onCheckedChange = { viewModel.toggleMakkahRelayModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                        CompactModuleChip(
                            icon = "⚡",
                            title = "TikTok Broker",
                            checked = config.enableLiveEventBrokerModule,
                            onCheckedChange = { viewModel.toggleLiveEventBrokerModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactModuleChip(
                            icon = "🔍",
                            title = "Log Inspeksi",
                            checked = config.enableScanInspectorModule,
                            onCheckedChange = { viewModel.toggleScanInspectorModule(it) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactModuleChip(
    icon: String,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) EmeraldGreen.copy(alpha = 0.12f) else Color(0xFF090D16))
            .border(
                0.6.dp,
                if (checked) EmeraldGreen.copy(alpha = 0.5f) else DarkCardBorder,
                RoundedCornerShape(6.dp)
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(icon, fontSize = 11.sp)
            Text(
                text = title,
                color = if (checked) Color.White else Color(0xFF94A3B8),
                fontSize = 9.5.sp,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (checked) "ON" else "OFF",
            color = if (checked) EmeraldGreen else Color(0xFF64748B),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AntiHashShieldCard(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val isAntiVideoActive = config.antiHashMode != com.tajasb.model.AntiHashIntensity.OFF && config.videoAntiHashPercent > 0.0f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_anti_hash_video"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isAntiVideoActive) NeonCyan.copy(alpha = 0.45f) else DarkCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "Anti-Deteksi Video",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )

                    FeatureInfoButton(
                        title = "Perisai Anti-Deteksi Video (Shader & DCT)",
                        description = "• Menginjeksi micro-noise piksel sub-visual, modulasi frekuensi spasial DCT 8x8/16x16, dan tripod breathing mikro secara real-time di GPU.\n" +
                            "• Memecah sidik jari pHash & ViT frame video tanpa merusak ketajaman gambar.\n" +
                            "• Berdiri mandiri (terpisah dari DNA Zoom & Mutator DNA Sesi).",
                        accentColor = NeonCyan
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (isAntiVideoActive) EmeraldGreen.copy(alpha = 0.14f)
                                else DarkSurfaceVariant
                            )
                            .border(
                                0.6.dp,
                                if (isAntiVideoActive) EmeraldGreen.copy(alpha = 0.45f) else DarkCardBorder,
                                RoundedCornerShape(5.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isAntiVideoActive) {
                                "${config.antiHashMode.badge} • ${String.format(java.util.Locale.US, "%.1f%%", config.videoAntiHashPercent)}"
                            } else {
                                "OFF"
                            },
                            color = if (isAntiVideoActive) EmeraldGreen else Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.5.sp
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Buka/Tutup Anti-Deteksi Video",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    com.tajasb.model.AntiHashIntensity.values().forEach { mode ->
                        val isSelected = config.antiHashMode == mode
                        OutlinedButton(
                            onClick = { viewModel.setAntiHashMode(mode) },
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .testTag("btn_anti_hash_${mode.name.lowercase()}"),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                0.7.dp,
                                if (isSelected) NeonCyan else DarkCardBorder
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) NeonCyan.copy(alpha = 0.15f) else Color(0xFF090D16)
                            )
                        ) {
                            Text(
                                text = mode.label.substringBefore(" "),
                                color = if (isSelected) NeonCyan else Color(0xFF94A3B8),
                                fontSize = 8.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Stepper Agresifitas Video (+/- 0.1%)
                val displayPct = if (isAntiVideoActive) config.videoAntiHashPercent else 0.0f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF090D16))
                        .border(0.6.dp, DarkCardBorder, RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Agresifitas Video: ${String.format(java.util.Locale.US, "%.1f", displayPct)}%",
                            color = if (isAntiVideoActive) NeonCyan else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "Micro-noise & DCT shader (Step 0.1%, 0.0% = OFF)",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .clickable { viewModel.repository.adjustVideoAntiHashPercent(-0.1f) }
                                .testTag("btn_video_anti_hash_minus"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", displayPct)}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .clickable { viewModel.repository.adjustVideoAntiHashPercent(+0.1f) }
                                .testTag("btn_video_anti_hash_plus"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DnaZoomFramingCard(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val isZoomDuplicate = remember(config.sessionDnaCustomZoom, config.usedDnaZoomHistory) {
        viewModel.repository.isZoomAlreadyUsed(config.sessionDnaCustomZoom)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_dna_zoom_framing"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            when {
                isZoomDuplicate && config.isDnaZoomEnabled -> Color(0xFFEF4444).copy(alpha = 0.7f)
                config.isDnaZoomEnabled -> EmeraldGreen.copy(alpha = 0.45f)
                else -> DarkCardBorder
            }
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            .background(EmeraldGreen.copy(alpha = 0.14f))
                            .border(0.6.dp, EmeraldGreen.copy(alpha = 0.35f), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPicture,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "DNA Zoom & Framing",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )

                    FeatureInfoButton(
                        title = "DNA Zoom & Safe Framing Envelope Guard",
                        description = "• Mengubah komposisi skala Zoom (1.05x–1.40x) dan posisi Geser X/Y dengan rumus Safe Framing Guard (±(Z-1)/2Z) sehingga 100% bebas celah/pinggiran hitam.\n" +
                            "• Dilengkapi pelacak riwayat zoom unik agar Anda tidak memakai angka zoom yang sama pada sesi berbeda.",
                        accentColor = EmeraldGreen
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (config.isDnaZoomEnabled) EmeraldGreen.copy(alpha = 0.14f)
                                else DarkSurfaceVariant
                            )
                            .border(
                                0.6.dp,
                                if (config.isDnaZoomEnabled) EmeraldGreen.copy(alpha = 0.45f) else DarkCardBorder,
                                RoundedCornerShape(5.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (config.isDnaZoomEnabled) {
                                "${String.format(java.util.Locale.US, "%.2f", config.sessionDnaCustomZoom)}x"
                            } else {
                                "OFF"
                            },
                            color = if (config.isDnaZoomEnabled) EmeraldGreen else Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.5.sp
                        )
                    }

                    Switch(
                        checked = config.isDnaZoomEnabled,
                        onCheckedChange = { viewModel.toggleDnaZoom(it) },
                        modifier = Modifier
                            .scale(0.62f)
                            .height(20.dp)
                            .testTag("switch_dna_zoom_enabled"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldGreen,
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Buka/Tutup DNA Zoom",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(6.dp))

                if (isZoomDuplicate) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(7.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "⚠️ PERINGATAN: ZOOM PERNAH DIPAKAI!",
                                    color = Color(0xFFFCA5A5),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp
                                )
                            }
                            Text(
                                text = "Zoom ${String.format(java.util.Locale.US, "%.2f", config.sessionDnaCustomZoom)}x sudah tercatat di riwayat sesi lampau. Klik tombol di bawah untuk mengacak zoom unik baru.",
                                color = Color.White,
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                            Button(
                                onClick = { viewModel.repository.generateNextUniqueZoom() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                                    .testTag("btn_randomize_unique_zoom_warning"),
                                shape = RoundedCornerShape(5.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                            ) {
                                Text("🎲 Acak Zoom Unik Bebas Hitam", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF090D16))
                        .border(0.6.dp, DarkCardBorder, RoundedCornerShape(7.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎯 Zoom DNA: ${String.format(java.util.Locale.US, "%.2f", config.sessionDnaCustomZoom)}x (${if (config.isDnaZoomEnabled) "AKTIF" else "NONAKTIF"})",
                            color = when {
                                isZoomDuplicate -> Color(0xFFEF4444)
                                config.isDnaZoomEnabled -> EmeraldGreen
                                else -> Color(0xFF94A3B8)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (kotlin.math.abs(config.sessionDnaPanX) > 0.001f || kotlin.math.abs(config.sessionDnaPanY) > 0.001f) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.repository.setSessionDnaFraming(
                                            config.sessionDnaCustomZoom,
                                            0.0f,
                                            0.0f,
                                            enableDnaZoom = config.isDnaZoomEnabled
                                        )
                                    },
                                    modifier = Modifier.height(24.dp),
                                    shape = RoundedCornerShape(5.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.6.dp, AmberWarning)
                                ) {
                                    Text("Tengah", color = AmberWarning, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.repository.generateNextUniqueZoom() },
                                modifier = Modifier
                                    .height(24.dp)
                                    .testTag("btn_generate_unique_zoom"),
                                shape = RoundedCornerShape(5.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                border = androidx.compose.foundation.BorderStroke(0.6.dp, NeonCyan)
                            ) {
                                Text("🎲 Zoom Baru", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "Batas Aman Geser X/Y: ±${String.format(java.util.Locale.US, "%.3f", config.maxSafePan)} (100% Bebas Celah Hitam)",
                        color = Color(0xFF94A3B8),
                        fontSize = 7.8.sp
                    )

                    // Slider Zoom Terasa (1.05x - 1.40x)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Zoom", color = Color(0xFFCBD5E1), fontSize = 8.5.sp, modifier = Modifier.width(42.dp))
                        Slider(
                            value = config.sessionDnaCustomZoom,
                            onValueChange = { newZ: Float ->
                                val rounded = ((kotlin.math.round(newZ * 50f) / 50f).toFloat())
                                viewModel.repository.setSessionDnaFraming(rounded, config.sessionDnaPanX, config.sessionDnaPanY)
                            },
                            valueRange = 1.05f..1.40f,
                            steps = 17,
                            modifier = Modifier.weight(1f).height(24.dp)
                        )
                        Text("${String.format(java.util.Locale.US, "%.2f", config.sessionDnaCustomZoom)}x", color = Color.White, fontSize = 8.5.sp, modifier = Modifier.width(36.dp))
                    }

                    // Slider Geser X (Clamped to maxSafePan)
                    val safeLim = config.maxSafePan.coerceAtLeast(0.001f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Geser X", color = Color(0xFFCBD5E1), fontSize = 8.5.sp, modifier = Modifier.width(42.dp))
                        Slider(
                            value = config.sessionDnaPanX.coerceIn(-safeLim, safeLim),
                            onValueChange = { newX: Float ->
                                viewModel.repository.setSessionDnaFraming(config.sessionDnaCustomZoom, newX, config.sessionDnaPanY)
                            },
                            valueRange = -safeLim..safeLim,
                            modifier = Modifier.weight(1f).height(24.dp)
                        )
                        Text(String.format(java.util.Locale.US, "%+.2f", config.sessionDnaPanX), color = Color.White, fontSize = 8.5.sp, modifier = Modifier.width(36.dp))
                    }

                    // Slider Geser Y (Clamped to maxSafePan)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Geser Y", color = Color(0xFFCBD5E1), fontSize = 8.5.sp, modifier = Modifier.width(42.dp))
                        Slider(
                            value = config.sessionDnaPanY.coerceIn(-safeLim, safeLim),
                            onValueChange = { newY: Float ->
                                viewModel.repository.setSessionDnaFraming(config.sessionDnaCustomZoom, config.sessionDnaPanX, newY)
                            },
                            valueRange = -safeLim..safeLim,
                            modifier = Modifier.weight(1f).height(24.dp)
                        )
                        Text(String.format(java.util.Locale.US, "%+.2f", config.sessionDnaPanY), color = Color.White, fontSize = 8.5.sp, modifier = Modifier.width(36.dp))
                    }

                    // Kalkulator Sesi Framing Interaktif
                    val calcResult = remember {
                        com.tajasb.model.FramingGuardMath.calculateCombinations(1.05f, 1.40f, 0.02f, 0.015f)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "📊 Kalkulator Framing: Tersedia ${calcResult.totalUniqueSessions} Sesi Unik Bebas Hitam (${calcResult.zoomLevelsCount} tingkat zoom × posisi geser)",
                            color = Color(0xFF38BDF8),
                            fontSize = 7.8.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (config.usedDnaZoomHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Riwayat: ${config.usedDnaZoomHistory.size} zoom lampau (${config.usedDnaZoomHistory.toList().takeLast(3).joinToString(", ")})",
                                color = Color(0xFF64748B),
                                fontSize = 7.5.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clickable { viewModel.repository.clearZoomHistory() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Reset Riwayat", color = AmberWarning, fontSize = 7.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionDnaMutatorCard(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val dna = config.activeSessionDna

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_session_dna_mutator"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (config.enableDnaModule) NeonPurple.copy(alpha = 0.55f) else DarkCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            .background(NeonPurple.copy(alpha = 0.14f))
                            .border(0.6.dp, NeonPurple.copy(alpha = 0.35f), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "Mutator DNA Sesi",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )

                    FeatureInfoButton(
                        title = "Mutator DNA Lintas Sesi (Sesi #1, #2, #3, dst)",
                        description = "• Mengubah spektrum Gamma/Kontras/Warmth, gradien pencahayaan ruangan 2D, rasio aspek anamorfik mikro, serta centroid Pitch & Tempo di setiap sesi Live.\n" +
                            "• Saklar DNA Sesi berdiri mandiri: mengaktifkan/mematikan DNA Sesi tidak akan mereset Anti-Deteksi Video maupun Audio Anti-Deteksi.",
                        accentColor = NeonPurple
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (config.enableDnaModule) NeonPurple.copy(alpha = 0.16f)
                                else DarkSurfaceVariant
                            )
                            .border(
                                0.6.dp,
                                if (config.enableDnaModule) NeonPurple.copy(alpha = 0.5f) else DarkCardBorder,
                                RoundedCornerShape(5.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (config.enableDnaModule) "DNA-${dna.codeName}" else "OFF",
                            color = if (config.enableDnaModule) NeonPurple else Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.5.sp
                        )
                    }

                    Switch(
                        checked = config.enableDnaModule,
                        onCheckedChange = { viewModel.toggleDnaModule(it) },
                        modifier = Modifier
                            .scale(0.62f)
                            .height(20.dp)
                            .testTag("switch_session_dna_module"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeonPurple,
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Buka/Tutup Mutator DNA Sesi",
                        tint = NeonPurple,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(6.dp))

                if (config.enableDnaModule) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF090D16))
                            .border(0.6.dp, DarkCardBorder.copy(alpha = 0.8f), RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "🧬 ${dna.badgeText}",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.2.sp
                                )
                                Text(
                                    text = "• ${dna.quadrantLabel}",
                                    color = AmberWarning,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 8.2.sp
                                )
                            }
                            Text(
                                text = dna.summaryText,
                                color = Color(0xFF94A3B8),
                                fontSize = 7.8.sp,
                                maxLines = 2
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.rotateSessionDna() },
                            modifier = Modifier
                                .height(26.dp)
                                .padding(start = 6.dp)
                                .testTag("btn_rotate_session_dna"),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            border = androidx.compose.foundation.BorderStroke(0.7.dp, NeonPurple.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = NeonPurple.copy(alpha = 0.14f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Ganti DNA Sesi Baru",
                                tint = NeonPurple,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ganti DNA",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 8.5.sp
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF090D16))
                            .border(0.6.dp, DarkCardBorder.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚪ Mutator DNA Sesi: NONAKTIF (OFF)",
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "DNA-A/B/C/D (kuadran, gamma, warm, pitch & tempo sesi) dimatikan sepenuhnya.",
                                color = Color(0xFF64748B),
                                fontSize = 7.8.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiWatermarkScannerCard(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig,
    modifier: Modifier = Modifier
) {
    val report by viewModel.aiWatermarkReport.collectAsState()
    val isScanning by viewModel.isScanningAiWatermark.collectAsState()
    val isCleaning by viewModel.isCleaningAiWatermark.collectAsState()
    val showDetailDialog by viewModel.showAiWatermarkDetailDialog.collectAsState()

    var isProofDropdownExpanded by remember { mutableStateOf(false) }

    val currentReport = report
    val severity = currentReport?.severity
    val borderColor = when (severity) {
        com.tajasb.data.AiWatermarkSeverity.AI_DETECTED -> Color(0xFFF43F5E).copy(alpha = 0.65f)
        com.tajasb.data.AiWatermarkSeverity.ENCODER_TRACE -> AmberWarning.copy(alpha = 0.55f)
        com.tajasb.data.AiWatermarkSeverity.CLEAN -> EmeraldGreen.copy(alpha = 0.45f)
        null -> DarkCardBorder
    }

    val badgeColor = when (severity) {
        com.tajasb.data.AiWatermarkSeverity.AI_DETECTED -> Color(0xFFF43F5E)
        com.tajasb.data.AiWatermarkSeverity.ENCODER_TRACE -> AmberWarning
        com.tajasb.data.AiWatermarkSeverity.CLEAN -> EmeraldGreen
        null -> Color(0xFF94A3B8)
    }

    // Automatically open proof dropdown right after cleaning or when AI code is detected
    LaunchedEffect(currentReport?.cleaningProof, currentReport?.severity) {
        if (currentReport?.cleaningProof != null ||
            currentReport?.severity == com.tajasb.data.AiWatermarkSeverity.AI_DETECTED
        ) {
            isProofDropdownExpanded = true
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_ai_watermark_scanner"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // Header Row with Dev Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            .background(if (config.enableAiDetectionModule) badgeColor.copy(alpha = 0.14f) else Color(0xFF1E293B))
                            .border(0.6.dp, if (config.enableAiDetectionModule) badgeColor.copy(alpha = 0.35f) else Color(0xFF334155), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (config.enableAiDetectionModule && severity == com.tajasb.data.AiWatermarkSeverity.AI_DETECTED) {
                                Icons.Default.WarningAmber
                            } else {
                                Icons.Default.Security
                            },
                            contentDescription = "Deteksi Watermark AI",
                            tint = if (config.enableAiDetectionModule) badgeColor else Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Deteksi Kode AI (Dev/Lab)",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                            FeatureInfoButton(
                                title = "Saklar Deteksi Kode AI Video (Riset/Pengembangan)",
                                description = "• Saklar ini menonaktifkan fitur pemindaian kode AI secara default agar tidak membebani performa saat live.\n" +
                                    "• Aktifkan saklar hanya saat ingin menguji atau mengembangkan deteksi metadata kontainer MP4 untuk riset lanjutan.",
                                accentColor = NeonCyan
                            )
                        }
                        Text(
                            text = if (config.enableAiDetectionModule) "Mode Riset/Dev: AKTIF" else "Nonaktif (Zero-Load / Siap Live)",
                            fontSize = 8.sp,
                            color = if (config.enableAiDetectionModule) EmeraldGreen else Color(0xFF94A3B8)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (config.enableAiDetectionModule) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(badgeColor.copy(alpha = 0.14f))
                                .border(0.6.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = when {
                                    isCleaning -> "🧹 MEMBERSIHKAN..."
                                    isScanning -> "🔍 MEMINDAI..."
                                    currentReport != null -> currentReport.severity.badgeText
                                    else -> "BELUM DIPINDAI"
                                },
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.2.sp
                            )
                        }
                    }

                    Switch(
                        checked = config.enableAiDetectionModule,
                        onCheckedChange = { viewModel.toggleAiDetectionModule(it) },
                        modifier = Modifier
                            .scale(0.75f)
                            .testTag("switch_ai_detection_module"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldGreen,
                            checkedTrackColor = EmeraldGreen.copy(alpha = 0.35f),
                            uncheckedThumbColor = Color(0xFF64748B),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                }
            }

            if (!config.enableAiDetectionModule) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💡 Saklar dinonaktifkan untuk pengembangan nanti. Menu disembunyikan dari Floating Control dan sistem tidak memindai video otomatis demi kelancaran live streaming.",
                    fontSize = 8.4.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))

                // Diagnostic Summary Box (Clickable to toggle Dropdown Proof)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF090D16))
                    .border(0.6.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
                    .clickable {
                        if (currentReport != null) {
                            isProofDropdownExpanded = !isProofDropdownExpanded
                        } else {
                            viewModel.scanActiveVideoAiWatermark(showDetailDialog = false)
                            isProofDropdownExpanded = true
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (currentReport == null) {
                    Text(
                        text = if (config.videoPath.isBlank()) {
                            "Pilih video di Daftar Putar untuk otomatis memindai ada/tidaknya kode tersembunyi AI."
                        } else {
                            "Klik [🔍 Cek AI] untuk memeriksa kandungan watermark/metadata AI pada video aktif."
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 8.5.sp
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📄 ${currentReport.fileName} (${currentReport.fileSizeBytes} B)",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "SHA256 #${currentReport.sha256Short}",
                            color = NeonCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = currentReport.buildNotificationHeadline(),
                        color = badgeColor,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentReport.findings.isNotEmpty()) {
                            val previewFindings = currentReport.findings.take(2).joinToString(" • ") {
                                "${it.codeName} @ ${it.hexOffset.substringBefore(" ")}"
                            }
                            Text(
                                text = "Bukti Offset: $previewFindings",
                                color = Color(0xFFCBD5E1),
                                fontSize = 7.8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else if (currentReport.cleaningProof != null) {
                            val p = currentReport.cleaningProof
                            Text(
                                text = "Bukti Hapus: #${p.beforeSha256} ➔ #${p.afterSha256} (${p.removedFindings.size} kode dihapus)",
                                color = EmeraldGreen,
                                fontSize = 7.8.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            val shieldActive = config.antiHashMode != com.tajasb.model.AntiHashIntensity.OFF
                            Text(
                                text = "Atom MP4: ${currentReport.mp4Atoms.size} Box • Anti-Hash: ${if (shieldActive) "AKTIF" else "OFF"}",
                                color = Color(0xFF94A3B8),
                                fontSize = 7.8.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = if (isProofDropdownExpanded) "Tutup Bukti ▴" else "Lihat Bukti ▾",
                            color = NeonCyan,
                            fontSize = 7.8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3 Equal-Width Action Buttons (Ratakan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.scanActiveVideoAiWatermark(showDetailDialog = false) },
                    enabled = !isScanning && !isCleaning,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .testTag("btn_scan_ai_watermark"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(0.7.dp, NeonCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NeonCyan.copy(alpha = 0.12f)
                    )
                ) {
                    Text(
                        text = if (isScanning) "⏳ Scan..." else "🔍 Cek AI",
                        color = NeonCyan,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.5.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (currentReport == null) {
                            viewModel.scanActiveVideoAiWatermark(showDetailDialog = false)
                            isProofDropdownExpanded = true
                        } else {
                            isProofDropdownExpanded = !isProofDropdownExpanded
                        }
                    },
                    enabled = !isScanning && !isCleaning,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .testTag("btn_info_ai_watermark"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        0.7.dp,
                        if (isProofDropdownExpanded) NeonCyan else DarkCardBorder
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isProofDropdownExpanded) NeonCyan.copy(alpha = 0.16f) else Color(0xFF131B2E)
                    )
                ) {
                    Text(
                        text = if (isProofDropdownExpanded) "🔬 Bukti ▴" else "🔬 Bukti ▾",
                        color = if (isProofDropdownExpanded) NeonCyan else Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.5.sp
                    )
                }

                Button(
                    onClick = {
                        isProofDropdownExpanded = true
                        viewModel.cleanActiveVideoAiWatermark()
                    },
                    enabled = !isScanning && !isCleaning && config.videoPath.isNotBlank(),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(28.dp)
                        .testTag("btn_clean_ai_watermark"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (severity == com.tajasb.data.AiWatermarkSeverity.AI_DETECTED) {
                            Color(0xFFF43F5E)
                        } else {
                            EmeraldGreen
                        },
                        contentColor = if (severity == com.tajasb.data.AiWatermarkSeverity.AI_DETECTED) {
                            Color.White
                        } else {
                            Color.Black
                        }
                    )
                ) {
                    Text(
                        text = if (isCleaning) "⏳ Proses..." else "🧹 Bersihkan AI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp
                    )
                }
            }

            // =========================================================================
            // DROPDOWN BUKTI FORENSIK BINER (BEFORE vs AFTER, HEX DUMP & ATOM MP4)
            // =========================================================================
            if (isProofDropdownExpanded && currentReport != null) {
                Spacer(modifier = Modifier.height(7.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF060911))
                        .border(0.8.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔬 BUKTI AUDIT BINER & HEX DUMP MP4",
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.8.sp
                        )
                        Text(
                            text = "Layar Penuh ↗",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.sp,
                            modifier = Modifier.clickable { viewModel.openAiWatermarkDetailDialog() }
                        )
                    }

                    // 1. BUKTI PERBANDINGAN SEBELUM vs SESUDAH DIBERSIHKAN (Jika sudah dibersihkan)
                    val proof = currentReport.cleaningProof
                    if (proof != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(EmeraldGreen.copy(alpha = 0.09f))
                                .border(0.7.dp, EmeraldGreen.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .padding(7.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "✅ BUKTI SEBELUM vs SESUDAH PENGHAPUSAN BINER:",
                                color = EmeraldGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.4.sp
                            )
                            Text(
                                text = "• File Asli   : ${proof.beforeFileName}\n" +
                                    "• File Bersih : ${proof.afterFileName}\n" +
                                    "• Ukuran Byte : %,d Byte ➔ %,d Byte (Remux + %,d Byte Di-Nol-kan 0x00)\n".format(
                                        proof.beforeSizeBytes,
                                        proof.afterSizeBytes,
                                        proof.zeroedBytesCount
                                    ) +
                                    "• Hash SHA256 : #${proof.beforeSha256} ➔ #${proof.afterSha256} (TERBUKTI BERUBAH)\n" +
                                    "• Kode Dihapus: ${proof.removedFindings.size} Kode AI/Metadata ➔ 0 Tersisa",
                                color = Color(0xFFE2E8F0),
                                fontSize = 7.9.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 11.5.sp
                            )

                            if (proof.removedFindings.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "🗑️ Rincian Byte Asli yang Telah Dihapus / Di-Nol-kan:",
                                    color = AmberWarning,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 8.sp
                                )
                                proof.removedFindings.forEachIndexed { idx, f ->
                                    Text(
                                        text = "${idx + 1}. [DIHAPUS] ${f.codeName}\n" +
                                            "   📍 Offset: ${f.hexOffset}\n" +
                                            (if (f.rawHexSnippet.isNotBlank()) "   🔢 Hex   : ${f.rawHexSnippet} ➔ [00 00 00 00]\n" else "") +
                                            (if (f.rawAsciiSnippet.isNotBlank()) "   🔤 ASCII : \"${f.rawAsciiSnippet}\"" else ""),
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 7.6.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 10.8.sp
                                    )
                                }
                            }
                        }
                    }

                    // 2. BUKTI TANGKAPAN KODE / HEX SAAT INI DI DALAM FILE
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0B1120))
                            .border(0.6.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (currentReport.findings.isEmpty()) {
                            Text(
                                text = "🟢 BUKTI PEMINDAIAN BINER SAAT INI (0 KODE DITEMUKAN):",
                                color = EmeraldGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.3.sp
                            )
                            Text(
                                text = "• Atom C2PA / JUMBF (urn:c2pa) : 0 Byte (TIDAK ADA)\n" +
                                    "• Tag AIGC (aigc_info / XMP)   : 0 Byte (TIDAK ADA)\n" +
                                    "• Box Metadata (udta/meta/uuid): 0 Aktif (BERSIH 100%)\n" +
                                    "• Steganografi Piksel & Suara  : Dilindungi Shader Anti-Hash (${config.antiHashMode.badge})",
                                color = Color(0xFFCBD5E1),
                                fontSize = 7.8.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 11.2.sp
                            )
                        } else {
                            Text(
                                text = "🚨 BUKTI TANGKAPAN KODE DI DALAM FILE (${currentReport.findings.size} TEMUAN):",
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.3.sp
                            )
                            currentReport.findings.forEachIndexed { idx, f ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF060911))
                                        .padding(5.dp),
                                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}. [${f.category}] ${f.codeName}",
                                        color = if (f.isAiSignature) Color(0xFFF43F5E) else AmberWarning,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp
                                    )
                                    Text(
                                        text = "📍 Offset File : ${f.hexOffset}",
                                        color = NeonCyan,
                                        fontSize = 7.6.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (f.rawHexSnippet.isNotBlank()) {
                                        Text(
                                            text = "🔢 Hex Asli    : ${f.rawHexSnippet}",
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 7.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    if (f.rawAsciiSnippet.isNotBlank()) {
                                        Text(
                                            text = "🔤 Teks Biner  : \"${f.rawAsciiSnippet}\"",
                                            color = Color(0xFFFDE68A),
                                            fontSize = 7.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. PETA STRUKTUR ATOM / BOX MP4 ASLI DARI FILE
                    if (currentReport.mp4Atoms.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0B1120))
                                .border(0.6.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                .padding(7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "📦 PETA STRUKTUR ATOM / BOX MP4 (${currentReport.mp4Atoms.size} BOX TERBACA):",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.1.sp
                            )
                            Text(
                                text = currentReport.mp4Atoms.take(12).joinToString("\n") { it.formatLine() },
                                color = Color(0xFF94A3B8),
                                fontSize = 7.5.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 10.8.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

    if (showDetailDialog && currentReport != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissAiWatermarkDetailDialog() },
            containerColor = Color(0xFF0F172A),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE2E8F0),
            title = {
                Text(
                    text = "🔬 Bukti Audit Forensik Kode AI Video",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = currentReport.buildForensicDropdownText(
                            isAntiHashActive = config.antiHashMode != com.tajasb.model.AntiHashIntensity.OFF,
                            isAudioAntiHashActive = config.isAudioAntiHashEnabled
                        ),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp,
                        color = Color(0xFFE2E8F0)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissAiWatermarkDetailDialog()
                        isProofDropdownExpanded = true
                        viewModel.cleanActiveVideoAiWatermark()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Text("🧹 Hapus Kode AI & Lindungi", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.dismissAiWatermarkDetailDialog() }
                ) {
                    Text("Tutup", color = Color.White, fontSize = 11.sp)
                }
            }
        )
    }
}

@Composable
fun AudioAntiHashShieldCard(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_audio_anti_hash_settings"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (config.isAudioAntiHashEnabled) NeonCyan.copy(alpha = 0.45f) else DarkCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "Audio Anti-Deteksi",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )

                    FeatureInfoButton(
                        title = "Audio Anti-Deteksi & Pemecah Hash (Acoustic Fingerprint Disruptor)",
                        description = "• Menggeser nada mikro (micro-pitch shift ±1-3%) dan memvariasikan tempo dinamis (drift) secara berkala.\n" +
                            "• Mengubah sidik jari akustik (Spectrogram / Audio Landmark) secara kontinu tanpa membuat suara menjadi kartun/chipmunk.",
                        accentColor = NeonCyan
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Switch(
                        checked = config.isAudioAntiHashEnabled,
                        onCheckedChange = { viewModel.toggleAudioAntiHash(it) },
                        modifier = Modifier
                            .scale(0.68f)
                            .height(22.dp)
                            .testTag("switch_audio_anti_hash_settings")
                    )

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Buka/Tutup Audio Anti-Deteksi",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                com.tajasb.model.AudioAntiHashIntensity.values().forEach { mode ->
                    val isSelected = config.isAudioAntiHashEnabled && config.audioAntiHashMode == mode
                    OutlinedButton(
                        onClick = {
                            if (!config.isAudioAntiHashEnabled) {
                                viewModel.toggleAudioAntiHash(true)
                            }
                            viewModel.setAudioAntiHashMode(mode)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.7.dp,
                            if (isSelected) NeonCyan else DarkCardBorder
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) NeonCyan.copy(alpha = 0.15f) else Color(0xFF090D16)
                        )
                    ) {
                        Text(
                            text = mode.displayName,
                            color = if (isSelected) NeonCyan else Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Stepper Agresifitas Audio (+/- 0.1%)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF090D16))
                    .border(0.6.dp, DarkCardBorder, RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Agresifitas Audio: ${String.format(java.util.Locale.US, "%.1f", config.audioAntiHashPercent)}%",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Pergeseran nada & drift tempo (Step 0.1%)",
                        color = Color(0xFF94A3B8),
                        fontSize = 8.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable { viewModel.repository.adjustAudioAntiHashPercent(-0.1f) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", config.audioAntiHashPercent)}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable { viewModel.repository.adjustAudioAntiHashPercent(+0.1f) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Switch Opsi Audio Tipe 2 Ikut Agresifitas Tipe 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF090D16))
                    .border(0.6.dp, DarkCardBorder, RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio Tipe 2 Ikut Agresifitas Tipe 1",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.2.sp
                    )
                    Text(
                        text = "Audio latar / efek Tipe 2 otomatis menyamakan drift pitch & tempo dengan Tipe 1",
                        color = Color(0xFF94A3B8),
                        fontSize = 7.8.sp
                    )
                }

                Switch(
                    checked = config.isAudioLayer2SyncAntiHash,
                    onCheckedChange = { viewModel.repository.setAudioLayer2SyncAntiHash(it) },
                    modifier = Modifier.scale(0.65f).height(20.dp)
                )
            }
            }
        }
    }
}

@Composable
fun CodecGuardCard(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig,
    modifier: Modifier = Modifier
) {
    val activeVideo = config.currentVideo
    val risk = remember(activeVideo?.path) {
        val file = activeVideo?.path?.let { java.io.File(it) }
        com.tajasb.optimizer.VideoCodecGuard.inspectVideoFile(file)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            when (risk.level) {
                com.tajasb.model.VideoCodecRisk.RiskLevel.DANGER -> AmberWarning.copy(alpha = 0.5f)
                com.tajasb.model.VideoCodecRisk.RiskLevel.WARNING -> NeonPurple.copy(alpha = 0.45f)
                else -> DarkCardBorder
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f)
            ) {
                val accent = when (risk.level) {
                    com.tajasb.model.VideoCodecRisk.RiskLevel.DANGER -> AmberWarning
                    com.tajasb.model.VideoCodecRisk.RiskLevel.WARNING -> NeonPurple
                    else -> EmeraldGreen
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .border(0.6.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = "Codec Guard",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )

                FeatureInfoButton(
                    title = "Codec Guard (Proteksi RAM ~2600MB)",
                    description = "• Memantau beban dekoder video agar tidak melampaui kapasitas RAM perangkat (~2600MB).\n" +
                        "• Status Video Saat Ini:\n${risk.warningMessage}",
                    accentColor = EmeraldGreen
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .border(0.6.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when (risk.level) {
                            com.tajasb.model.VideoCodecRisk.RiskLevel.DANGER -> "BERAT"
                            com.tajasb.model.VideoCodecRisk.RiskLevel.WARNING -> "SEDANG"
                            else -> "AMAN"
                        },
                        color = accent,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Switch(
                checked = config.isSafeCodecGuardActive,
                onCheckedChange = { viewModel.toggleSafeCodecGuard(it) },
                modifier = Modifier
                    .scale(0.68f)
                    .height(22.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = EmeraldGreen,
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = DarkSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun LsposedStatusCard(
    viewModel: VcamViewModel,
    isXposedActive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = if (isXposedActive) EmeraldGreen else AmberWarning
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkCardBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            .background(accent.copy(alpha = 0.14f))
                            .border(0.6.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isXposedActive) Icons.Default.Security else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "Status LSPosed",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )

                    FeatureInfoButton(
                        title = "Status Modul LSPosed / Root Hook",
                        description = "• Menunjukkan apakah modul kamera virtual aktif di dalam framework LSPosed.\n" +
                            "• Gunakan 'Cek' untuk menyegarkan status, 'LSPosed' untuk membuka manajer modul, atau 'Restart' untuk memuat ulang layanan hook.",
                        accentColor = accent
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .border(0.6.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isXposedActive) "AKTIF" else "NONAKTIF",
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.refreshStatus() },
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(0.7.dp, NeonCyan.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF090D16))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Cek", color = NeonCyan, fontSize = 8.8.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        val opened = com.tajasb.engine.LsposedHealthGuard.openLsposedManager(context)
                        if (!opened) {
                            Toast.makeText(context, "LSPosed Manager tidak terpasang di HP ini", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(0.7.dp, NeonPurple.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF090D16))
                ) {
                    Text("LSPosed", color = NeonPurple, fontSize = 8.8.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { viewModel.restartHookServices() },
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(0.7.dp, DarkCardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF090D16))
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Restart", color = Color(0xFFCBD5E1), fontSize = 8.8.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun BackupSettingsSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conceptManager = remember { com.tajasb.data.ConceptBackupManager.getInstance(context) }
    val concepts by conceptManager.concepts.collectAsState()
    val activeConceptId by conceptManager.activeConceptId.collectAsState()
    val isAutoBackupEnabled by conceptManager.isAutoBackupEnabled.collectAsState()
    val lastCheckpointTimeMs by conceptManager.lastAutoCheckpointTimeMs.collectAsState()
    val isBusy by conceptManager.isBusy.collectAsState()
    val busyProgressText by conceptManager.busyProgressText.collectAsState()

    var showConceptManagerDialog by remember { mutableStateOf(false) }
    var showSaveConceptDialog by remember { mutableStateOf(false) }
    var showConceptDropdown by remember { mutableStateOf(false) }
    var checklistOptions by remember { mutableStateOf(com.tajasb.data.BackupChecklistOptions()) }
    // null = tertutup, "VIEW_ONLY" = lihat/atur saja, "DRIVE" = sebelum ke Drive, "EXPORT_ZIP" = sebelum simpan .ZIP, "RESTORE_ZIP" = sebelum restore .ZIP
    var pendingHubAction by remember { mutableStateOf<String?>(null) }

    val activeConcept = concepts.firstOrNull { it.id == activeConceptId }

    val createZipLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        if (destUri != null) {
            scope.launch {
                val res = conceptManager.exportFullBackupZipToUri(destUri, checklistOptions)
                res.onSuccess { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "⚠️ Gagal menyimpan ZIP: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val openZipLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { sourceUri ->
        if (sourceUri != null) {
            scope.launch {
                val res = conceptManager.importFullBackupZipFromUri(sourceUri, checklistOptions)
                res.onSuccess { report ->
                    Toast.makeText(context, report.summaryMessage, Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "⚠️ Gagal restore backup ZIP: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_unified_backup_hub"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // BARIS 1: HEADER 1 PUSAT BACKUP, RESTORE & KONSEP
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            .background(NeonPurple.copy(alpha = 0.14f))
                            .border(0.6.dp, NeonPurple.copy(alpha = 0.35f), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "Backup, Restore & Konsep",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    FeatureInfoButton(
                        title = "1 Pusat Backup, Restore & Konsep Total (.ZIP)",
                        description = "• 1 Pusat Terpadu: Seluruh Backup, Restore (.ZIP Google Drive / HP), dan Manajemen Konsep Live disatukan di sini.\n\n" +
                            "• Konfirmasi Ceklis Pra-Proses: Sebelum Backup atau Restore dijalankan, muncul dropdown ceklis untuk memilih/memverifikasi item yang diproses (Video MP4, Audio MP3, Koreografi Auto-Tap Urut/Acak + Nama + Mark + Teks Random + Living Icon, Overlay, Efek Kamera/Anti-Hash DNA, & Posisi Floating).\n\n" +
                            "• Siap Pasca Reset HP: Setelah reset HP, cukup unduh file .ZIP dari Google Drive lalu klik '📥 Restore .ZIP', semua langsung kembali 100% siap live!",
                        accentColor = NeonPurple
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isAutoBackupEnabled) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (pendingHubAction != null) EmeraldGreen.copy(alpha = 0.18f)
                                else NeonPurple.copy(alpha = 0.14f)
                            )
                            .border(
                                0.6.dp,
                                if (pendingHubAction != null) EmeraldGreen.copy(alpha = 0.6f)
                                else NeonPurple.copy(alpha = 0.45f),
                                RoundedCornerShape(5.dp)
                            )
                            .clickable(enabled = !isBusy) {
                                pendingHubAction = if (pendingHubAction == null) "VIEW_ONLY" else null
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("btn_hub_toggle_checklist_badge")
                    ) {
                        Text(
                            text = "☑️ Ceklis (${checklistOptions.selectedCount}/6) ${if (pendingHubAction != null) "▴" else "▾"}",
                            color = if (pendingHubAction != null) EmeraldGreen else Color(0xFFE9D5FF),
                            fontSize = 8.2.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(NeonCyan.copy(alpha = 0.14f))
                            .border(0.6.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${concepts.size} Konsep",
                            color = NeonCyan,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // INDIKATOR PROGRES SAAT BACKUP / RESTORE .ZIP BERJALAN
            if (isBusy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(NeonCyan.copy(alpha = 0.14f))
                        .border(0.7.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = NeonCyan
                    )
                    Text(
                        text = busyProgressText ?: "Memproses paket backup (.zip)...",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // BARIS 2: PEMILIH KONSEP LIVE 1-KLIK + SIMPAN KONSEP + BRANKAS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1.35f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(27.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF090D16))
                            .border(
                                width = 0.7.dp,
                                color = if (activeConcept != null) EmeraldGreen.copy(alpha = 0.55f) else DarkCardBorder,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable(enabled = !isBusy) {
                                if (concepts.isNotEmpty() || lastCheckpointTimeMs > 0L) {
                                    showConceptDropdown = true
                                } else {
                                    showSaveConceptDialog = true
                                }
                            }
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                activeConcept != null -> "⚡ ${activeConcept.name}"
                                concepts.isNotEmpty() -> "Pilih Konsep (${concepts.size})"
                                lastCheckpointTimeMs > 0L -> "🛡️ Sesi Auto-Save Aktif"
                                else -> "+ Buat Konsep Pertama"
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (activeConcept != null) EmeraldGreen else Color(0xFFCBD5E1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "▾",
                            fontSize = 9.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = showConceptDropdown,
                        onDismissRequest = { showConceptDropdown = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        if (lastCheckpointTimeMs > 0L) {
                            val timeStr = remember(lastCheckpointTimeMs) {
                                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(lastCheckpointTimeMs))
                            }
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "🛡️ Pulihkan Sesi Terakhir ($timeStr)",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldGreen
                                    )
                                },
                                onClick = {
                                    showConceptDropdown = false
                                    scope.launch {
                                        val res = conceptManager.restoreLastAutoCheckpoint(checklistOptions)
                                        res.onSuccess { restored ->
                                            Toast.makeText(
                                                context,
                                                "⚡ Sesi terakhir (${restored.videoCount} Video, ${restored.audioCount} MP3) dipulihkan!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "⚠️ ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }

                        concepts.forEach { item ->
                            val isSelected = item.id == activeConceptId
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (isSelected) "✅ ${item.name}" else "⚡ ${item.name}",
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) NeonCyan else Color.White
                                        )
                                        Text(
                                            text = "(${item.videoCount}V/${item.audioCount}A)",
                                            fontSize = 8.5.sp,
                                            color = Color.Gray
                                        )
                                    }
                                },
                                onClick = {
                                    showConceptDropdown = false
                                    scope.launch {
                                        val res = conceptManager.restoreConcept(item.id, checklistOptions)
                                        res.onSuccess { restored ->
                                            Toast.makeText(context, "⚡ Konsep '${restored.name}' diterapkan!", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "⚠️ ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("pill_concept_${item.id}")
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showSaveConceptDialog = true },
                    enabled = !isBusy,
                    modifier = Modifier
                        .height(27.dp)
                        .testTag("btn_quick_save_concept"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(0.7.dp, EmeraldGreen.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = EmeraldGreen.copy(alpha = 0.14f),
                        contentColor = EmeraldGreen
                    )
                ) {
                    Text("Simpan", fontSize = 8.8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { showConceptManagerDialog = true },
                    enabled = !isBusy,
                    modifier = Modifier
                        .height(27.dp)
                        .testTag("btn_backup_section_concept_manager"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(0.7.dp, NeonCyan.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF090D16))
                ) {
                    Text(
                        text = "Kelola",
                        color = NeonCyan,
                        fontSize = 8.8.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // BARIS 3: TOMBOL UTAMA BACKUP TOTAL (.ZIP DRIVE / HP) & RESTORE TOTAL (.ZIP)
            // Klik pertama membuka Dropdown Konfirmasi Ceklis sebelum diproses
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        pendingHubAction = if (pendingHubAction == "DRIVE") null else "DRIVE"
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(27.dp)
                        .testTag("btn_hub_backup_drive_zip"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (pendingHubAction == "DRIVE") 1.1.dp else 0.7.dp,
                        if (pendingHubAction == "DRIVE") NeonPurple else NeonPurple.copy(alpha = 0.5f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (pendingHubAction == "DRIVE") NeonPurple.copy(alpha = 0.28f) else NeonPurple.copy(alpha = 0.14f)
                    )
                ) {
                    Text(
                        text = "☁️ Ke Drive",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        pendingHubAction = if (pendingHubAction == "EXPORT_ZIP") null else "EXPORT_ZIP"
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(27.dp)
                        .testTag("btn_hub_save_local_zip"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (pendingHubAction == "EXPORT_ZIP") 1.1.dp else 0.7.dp,
                        if (pendingHubAction == "EXPORT_ZIP") NeonCyan else NeonCyan.copy(alpha = 0.5f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (pendingHubAction == "EXPORT_ZIP") NeonCyan.copy(alpha = 0.18f) else Color(0xFF090D16)
                    )
                ) {
                    Text(
                        text = "💾 Ekspor .ZIP",
                        color = NeonCyan,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        pendingHubAction = if (pendingHubAction == "RESTORE_ZIP") null else "RESTORE_ZIP"
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(27.dp)
                        .testTag("btn_hub_restore_zip"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (pendingHubAction == "RESTORE_ZIP") 1.1.dp else 0.7.dp,
                        if (pendingHubAction == "RESTORE_ZIP") EmeraldGreen else EmeraldGreen.copy(alpha = 0.5f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (pendingHubAction == "RESTORE_ZIP") EmeraldGreen.copy(alpha = 0.26f) else EmeraldGreen.copy(alpha = 0.14f)
                    )
                ) {
                    Text(
                        text = "📥 Restore .ZIP",
                        color = EmeraldGreen,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // DROPDOWN KONFIRMASI CEKLIS PRA-PROSES (BACKUP / RESTORE)
            if (pendingHubAction != null) {
                val actionLabel = when (pendingHubAction) {
                    "DRIVE" -> "Konfirmasi Ceklis Sebelum Upload Backup (.ZIP) ke Drive"
                    "EXPORT_ZIP" -> "Konfirmasi Ceklis Sebelum Ekspor File Backup (.ZIP)"
                    "RESTORE_ZIP" -> "Konfirmasi Ceklis Sebelum Restore Paket (.ZIP)"
                    else -> "Daftar Ceklis Item yang Di-Backup & Restore"
                }
                val confirmBtnLabel = when (pendingHubAction) {
                    "DRIVE" -> "✅ Lanjut Buat & Upload .ZIP ke Google Drive (${checklistOptions.selectedCount}/6)"
                    "EXPORT_ZIP" -> "✅ Lanjut Simpan File Backup .ZIP (${checklistOptions.selectedCount}/6)"
                    "RESTORE_ZIP" -> "✅ Lanjut Pilih File .ZIP untuk Di-Restore (${checklistOptions.selectedCount}/6)"
                    else -> null
                }
                com.tajasb.ui.components.BackupChecklistConfirmDropdown(
                    title = actionLabel,
                    options = checklistOptions,
                    onOptionsChange = { checklistOptions = it },
                    actionButtonLabel = confirmBtnLabel,
                    onConfirmAction = if (confirmBtnLabel != null) {
                        {
                            val actionToRun = pendingHubAction
                            pendingHubAction = null
                            when (actionToRun) {
                                "DRIVE" -> {
                                    scope.launch {
                                        val intentRes = conceptManager.prepareShareBackupZipIntent(checklistOptions)
                                        intentRes.onSuccess { chooserIntent ->
                                            context.startActivity(chooserIntent)
                                        }.onFailure { err ->
                                            Toast.makeText(context, "⚠️ Gagal membuat ZIP: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                "EXPORT_ZIP" -> {
                                    val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
                                    createZipLauncher.launch("LiveHost_FullBackup_$ts.zip")
                                }
                                "RESTORE_ZIP" -> {
                                    openZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                }
                            }
                        }
                    } else null,
                    onCancelAction = { pendingHubAction = null },
                    defaultExpanded = true
                )
            }
        }
    }

    if (showSaveConceptDialog) {
        com.tajasb.ui.components.SaveConceptDialog(
            conceptManager = conceptManager,
            defaultName = "Konsep #${concepts.size + 1}",
            activeConcept = activeConcept,
            onDismiss = { showSaveConceptDialog = false },
            onSaved = { savedProfile, isOverwrite ->
                showSaveConceptDialog = false
                val actionWord = if (isOverwrite) "diperbarui" else "disimpan"
                Toast.makeText(
                    context,
                    "💾 Konsep '${savedProfile.name}' berhasil $actionWord!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showConceptManagerDialog) {
        com.tajasb.ui.components.ConceptVaultManagerDialog(
            conceptManager = conceptManager,
            onDismiss = { showConceptManagerDialog = false },
            onNotifyUser = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun TelegramScreenMonitorCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val monitor = remember { com.tajasb.remote.TelegramScreenMonitorManager.getInstance(context) }

    val isEnabled by monitor.isEnabled.collectAsState()
    val savedBotToken by monitor.botToken.collectAsState()
    val savedChatId by monitor.chatId.collectAsState()
    val savedDeviceLabel by monitor.deviceLabel.collectAsState()
    val sentCount by monitor.sentCount.collectAsState()
    val lastSentTimeMs by monitor.lastSentTimeMs.collectAsState()
    val lastStatusText by monitor.lastStatusText.collectAsState()
    val isSending by monitor.isSending.collectAsState()
    val isFetchingChatId by monitor.isFetchingChatId.collectAsState()

    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var tokenInput by remember(savedBotToken) { mutableStateOf(savedBotToken) }
    var chatIdInput by remember(savedChatId) { mutableStateOf(savedChatId) }
    var labelInput by remember(savedDeviceLabel) { mutableStateOf(savedDeviceLabel) }
    var showTokenPlain by rememberSaveable { mutableStateOf(false) }

    val isA11yReady = com.tajasb.VcamAccessibilityService.isServiceRunning ||
        com.tajasb.VcamAccessibilityService.isAccessibilityEnabled(context)

    val lastSentFormatted = remember(lastSentTimeMs) {
        if (lastSentTimeMs > 0L) {
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(lastSentTimeMs))
        } else {
            "Belum ada"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("telegram_screen_monitor_card"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.8.dp,
            color = if (isEnabled) NeonCyan.copy(alpha = 0.55f) else DarkCardBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // HEADER ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isEnabled) NeonCyan.copy(alpha = 0.18f)
                                else Color(0xFF1E293B)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📸",
                            fontSize = 11.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Pantau Layar Live (Telegram)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            FeatureInfoButton(
                                title = "Pantau Tangkapan Layar Live via Telegram (Tiap 2,5 Menit)",
                                description = "Mengirim tangkapan layar (screenshot) HP Live ke Bot Telegram pribadi Anda secara otomatis setiap 2,5 menit.\n\n" +
                                    "• Kualitas Rendah & Hemat Performa: Gambar otomatis diperkecil (~420px, JPEG 45%, ~35 KB) di thread latar belakang sehingga tidak mengganggu FPS kamera virtual maupun kelancaran Live.\n" +
                                    "• Caption Telemetri Lengkap: Setiap foto dilengkapi status Baterai, Suhu (°C), Sisa RAM, Nama Video Aktif, dan Status Auto-Tap.\n" +
                                    "• Riwayat Aman: Foto yang terkirim ke Telegram TIDAK dihapus otomatis oleh aplikasi agar Anda bebas mengecek dan menghapusnya sendiri kapan saja."
                            )
                        }
                        Text(
                            text = if (isEnabled) {
                                "Aktif • Kirim tiap 2,5m • Terkirim: $sentCount ($lastSentFormatted)"
                            } else {
                                "Kirim SS resolusi rendah tiap 2,5 menit tanpa ganggu performa"
                            },
                            color = if (isEnabled) EmeraldGreen else Color.Gray,
                            fontSize = 8.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isEnabled) EmeraldGreen.copy(alpha = 0.16f)
                                else Color(0xFF1E293B)
                            )
                            .border(
                                0.6.dp,
                                if (isEnabled) EmeraldGreen.copy(alpha = 0.45f) else DarkCardBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isEnabled) "ON • 2.5m" else "OFF",
                            color = if (isEnabled) EmeraldGreen else Color.Gray,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { wantActive ->
                            monitor.setBotToken(tokenInput)
                            monitor.setChatId(chatIdInput)
                            monitor.setDeviceLabel(labelInput)
                            val (ok, msg) = monitor.setEnabled(wantActive)
                            if (!ok) {
                                isExpanded = true
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .scale(0.62f)
                            .height(22.dp)
                            .testTag("switch_telegram_screen_monitor"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = NeonCyan,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Tutup pengaturan Telegram" else "Buka pengaturan Telegram",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isExpanded) {
                // STATUS & INFO AKSESIBILITAS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF090D16))
                        .border(0.6.dp, DarkCardBorder, RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isA11yReady) "🟢 Engine Screenshot: Aksesibilitas Siap (Tanpa Popup)" else "⚠️ Aksesibilitas belum aktif (Atau gunakan Root)",
                            color = if (isA11yReady) EmeraldGreen else AmberWarning,
                            fontSize = 8.3.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isA11yReady) {
                            Text(
                                text = "Aktifkan ➔",
                                color = NeonCyan,
                                fontSize = 8.3.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Throwable) {}
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = lastStatusText,
                        color = Color(0xFFCBD5E1),
                        fontSize = 8.2.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // INPUT 1: BOT TOKEN TELEGRAM
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. Bot Token Telegram (@BotFather)",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (showTokenPlain) "🙈 Sembunyikan" else "👁️ Lihat Token",
                            color = NeonCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { showTokenPlain = !showTokenPlain }
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    androidx.compose.material3.OutlinedTextField(
                        value = tokenInput,
                        onValueChange = {
                            tokenInput = it
                            monitor.setBotToken(it)
                        },
                        placeholder = {
                            Text(
                                text = "Contoh: 7123456789:AAHxyz...",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (showTokenPlain) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_telegram_bot_token")
                    )
                }

                // INPUT 2: CHAT ID & NAMA PERANGKAT (SEJAJAR)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "2. Chat ID Tujuan",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = chatIdInput,
                            onValueChange = {
                                chatIdInput = it
                                monitor.setChatId(it)
                            },
                            placeholder = {
                                Text(
                                    text = "Contoh: 123456789",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_telegram_chat_id")
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "3. Label HP Live",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = labelInput,
                            onValueChange = {
                                labelInput = it
                                monitor.setDeviceLabel(it)
                            },
                            placeholder = {
                                Text(
                                    text = "HP Live Utama",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_telegram_device_label")
                        )
                    }
                }

                // BARIS TOMBOL AKSI (AMBIL CHAT ID OTOMATIS & TES KIRIM SS SEKARANG)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            monitor.setBotToken(tokenInput)
                            scope.launch {
                                val (ok, msg) = monitor.autoDetectChatId()
                                if (ok) {
                                    chatIdInput = monitor.chatId.value
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isFetchingChatId && !isSending,
                        modifier = Modifier
                            .weight(1f)
                            .height(29.dp)
                            .testTag("btn_telegram_detect_chat_id"),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        border = androidx.compose.foundation.BorderStroke(0.7.dp, NeonCyan.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF090D16),
                            contentColor = NeonCyan
                        )
                    ) {
                        Text(
                            text = if (isFetchingChatId) "⏳ Mencari ID..." else "🔍 Ambil Chat ID",
                            fontSize = 8.6.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            monitor.setBotToken(tokenInput)
                            monitor.setChatId(chatIdInput)
                            monitor.setDeviceLabel(labelInput)
                            scope.launch {
                                val (_, msg) = monitor.sendSnapshotNow(isManualTest = true)
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSending && !isFetchingChatId,
                        modifier = Modifier
                            .weight(1f)
                            .height(29.dp)
                            .testTag("btn_telegram_test_send_now"),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreen.copy(alpha = 0.88f),
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = if (isSending) "⏳ Mengirim..." else "📸 Tes Kirim Sekarang",
                            fontSize = 8.6.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "💡 Cara cepat: Masukkan Bot Token ➔ kirim /start ke bot Anda di Telegram ➔ klik '🔍 Ambil Chat ID' ➔ aktifkan saklar ON. Foto yang terkirim di Telegram tidak dihapus otomatis agar bisa Anda hapus sendiri.",
                    color = Color.Gray,
                    fontSize = 7.8.sp,
                    lineHeight = 10.5.sp
                )
            }
        }
    }
}

