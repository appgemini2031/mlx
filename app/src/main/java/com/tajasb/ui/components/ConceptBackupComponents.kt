package com.tajasb.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.ui.theme.CyberPink
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.tajasb.autotap.AutoTapManager
import com.tajasb.data.BackupChecklistOptions
import com.tajasb.data.ConceptBackupManager
import com.tajasb.data.ConceptProfile
import com.tajasb.data.VcamRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dropdown Konfirmasi Ceklis Pra-Proses Backup & Restore.
 * Menampilkan daftar rincian item apa saja yang akan di-backup atau di-restore beserta jumlah datanya secara real-time,
 * sehingga pengguna dapat memilih/mengecek kategori sebelum proses dijalankan.
 */
@Composable
fun BackupChecklistConfirmDropdown(
    title: String,
    options: BackupChecklistOptions,
    onOptionsChange: (BackupChecklistOptions) -> Unit,
    actionButtonLabel: String? = null,
    onConfirmAction: (() -> Unit)? = null,
    onCancelAction: (() -> Unit)? = null,
    defaultExpanded: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { VcamRepository.getInstance(context) }
    val vcamCfg by repo.config.collectAsState()
    val autoTap = remember { AutoTapManager.getInstance(context) }
    val choreoGroups by autoTap.choreographyGroups.collectAsState()
    val tapMarks by autoTap.marks.collectAsState()
    val randomTexts by autoTap.autoTypeTexts.collectAsState()
    val chainAll by autoTap.chainAllChoreographies.collectAsState()
    val isRandomChoreo by autoTap.isRandomChoreographyOrder.collectAsState()

    var expanded by remember(defaultExpanded) { mutableStateOf(defaultExpanded) }

    val choreoModeBadge = when {
        !chainAll || choreoGroups.size <= 1 -> "1 Koreo"
        isRandomChoreo -> "Acak 🔀"
        else -> "Urut 🔁"
    }

    val items = listOf(
        Triple(
            "🎬 Video MP4, Playlist & Mode Putar",
            "${vcamCfg.playlist.size} File MP4 • Mode Master/Spill/Bridge/IFS",
            options.includeVideos to { v: Boolean -> onOptionsChange(options.copy(includeVideos = v)) }
        ),
        Triple(
            "🎵 Musik MP3 & Playlist Audio",
            "${vcamCfg.audioPlaylist.size} File MP3 • Volume & Loop Musik",
            options.includeAudios to { v: Boolean -> onOptionsChange(options.copy(includeAudios = v)) }
        ),
        Triple(
            "👆 Koreografi Auto-Tap & Teks Ketik",
            "${choreoGroups.size} Grup Koreo ($choreoModeBadge) • ${tapMarks.size} Mark • ${randomTexts.size} Teks",
            options.includeAutoTapChoreo to { v: Boolean -> onOptionsChange(options.copy(includeAutoTapChoreo = v)) }
        ),
        Triple(
            "🏷️ Stiker, Watermark, Notif & Komen",
            "Stiker Promo • Watermark • Notif Beli • Doa & Anchor",
            options.includeOverlays to { v: Boolean -> onOptionsChange(options.copy(includeOverlays = v)) }
        ),
        Triple(
            "🎨 Kamera, Visual, Anti-Hash & Target",
            "Rotasi ${vcamCfg.rotation}° • ${vcamCfg.antiHashMode.name} • ${vcamCfg.targetPackages.size} App",
            options.includeCameraVisual to { v: Boolean -> onOptionsChange(options.copy(includeCameraVisual = v)) }
        ),
        Triple(
            "🪟 Posisi & Ukuran Panel Floating",
            "Transparansi ${(vcamCfg.floatingAlpha * 100).toInt()}% & Koordinat Layar",
            options.includeFloatingLayout to { v: Boolean -> onOptionsChange(options.copy(includeFloatingLayout = v)) }
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A1220))
            .border(1.dp, NeonCyan.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Header Dropdown Ceklis
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceVariant)
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (expanded) "▾" else "▸",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan
                )
                Column {
                    Text(
                        text = title,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Terpilih ${options.selectedCount}/6 kategori • Ketuk item untuk ceklis/lepas",
                        fontSize = 7.5.sp,
                        color = EmeraldGreen
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val allChecked = options.selectedCount == 6
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (allChecked) EmeraldGreen.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.2f))
                        .border(0.7.dp, if (allChecked) EmeraldGreen else NeonCyan, RoundedCornerShape(4.dp))
                        .clickable {
                            if (allChecked) {
                                onOptionsChange(
                                    BackupChecklistOptions(
                                        includeVideos = false,
                                        includeAudios = false,
                                        includeAutoTapChoreo = false,
                                        includeOverlays = false,
                                        includeCameraVisual = false,
                                        includeFloatingLayout = false
                                    )
                                )
                            } else {
                                onOptionsChange(BackupChecklistOptions())
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = if (allChecked) "Lepas Semua" else "Pilih Semua",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (allChecked) EmeraldGreen else NeonCyan
                    )
                }
            }
        }

        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(3.5.dp)) {
                items.forEach { (label, subInfo, stateAndSetter) ->
                    val (checked, setter) = stateAndSetter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (checked) EmeraldGreen.copy(alpha = 0.10f) else DarkBackground)
                            .border(
                                width = 0.8.dp,
                                color = if (checked) EmeraldGreen.copy(alpha = 0.65f) else DarkCardBorder,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { setter(!checked) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
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
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (checked) EmeraldGreen else DarkSurfaceVariant)
                                    .border(1.dp, if (checked) EmeraldGreen else Color.Gray, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (checked) "✓" else "",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }
                            Column {
                                Text(
                                    text = label,
                                    fontSize = 8.8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (checked) Color.White else Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = subInfo,
                                    fontSize = 7.5.sp,
                                    color = if (checked) NeonCyan else Color.DarkGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = if (checked) "AKTIF" else "SKIP",
                            fontSize = 7.2.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (checked) EmeraldGreen else Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        if (onConfirmAction != null && actionButtonLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onCancelAction != null) {
                    OutlinedButton(
                        onClick = onCancelAction,
                        modifier = Modifier
                            .weight(0.35f)
                            .height(30.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        border = BorderStroke(0.8.dp, DarkCardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                    ) {
                        Text("✕ Batal", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onConfirmAction,
                    enabled = options.anySelected,
                    modifier = Modifier
                        .weight(if (onCancelAction != null) 0.65f else 1f)
                        .height(30.dp)
                        .testTag("btn_confirm_backup_checklist_process"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "$actionButtonLabel (${options.selectedCount}/6)",
                        fontSize = 8.8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Bilah Pintasan Konsep 1-Klik (Concept Quick-Switcher Bar) di halaman utama Media.
 * Memungkinkan pergantian antar konsep (Video + MP3 + Mode IFS/Spill + Stiker/Overlay) dalam 1 klik,
 * dilengkapi indikator Auto-Save Realtime & akses ke Folder Backup Lokal / Google Drive.
 */
@Composable
fun ConceptQuickSwitcherBar(
    onNotifyUser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conceptManager = remember { ConceptBackupManager.getInstance(context) }
    val concepts by conceptManager.concepts.collectAsState()
    val activeConceptId by conceptManager.activeConceptId.collectAsState()
    val isAutoBackupEnabled by conceptManager.isAutoBackupEnabled.collectAsState()
    val lastCheckpointTimeMs by conceptManager.lastAutoCheckpointTimeMs.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showConceptDropdown by remember { mutableStateOf(false) }

    val activeConcept = concepts.firstOrNull { it.id == activeConceptId }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_concept_quick_switcher"),
        shape = RoundedCornerShape(7.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(0.8.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sisi Kiri: Label Konsep + Badge Auto + Tombol Tanda Seru (!)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = "Manajer Konsep",
                    tint = NeonPurple,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Konsep",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isAutoBackupEnabled) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen)
                    )
                }
                FeatureInfoButton(
                    title = "Backup & Restore 1-Klik (Folder Lokal + Google Drive)",
                    description = "• Auto-Save & Auto-Restore Realtime: Setiap perubahan Video, MP3, Mode IFS/Spill/Urut, Stiker, Watermark, dan Pengaturan otomatis diamankan ke Folder Backup Lokal pilihan Anda. Jika HP mendadak mati/restart, semua otomatis pulih saat aplikasi dibuka!\n\n" +
                        "• Ekspor & Impor 1 File (.ZIP) Google Drive: Simpan seluruh file fisik Video MP4 + Audio MP3 + Stiker + Konsep ke 1 file .zip untuk di-upload ke Google Drive atau dipindahkan ke HP lain dalam 1 klik.\n\n" +
                        "• Anti-Duplikat File Vault: Menghapus video/MP3 saat meracik Konsep ke-2 tidak akan merusak file milik Konsep ke-1.",
                    accentColor = NeonPurple
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Tengah: Dropdown Pemilih Konsep 1-Klik + Pulihkan Sesi Terakhir
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(DarkSurfaceVariant)
                        .border(
                            width = 0.7.dp,
                            color = if (activeConcept != null) EmeraldGreen.copy(alpha = 0.65f) else DarkCardBorder,
                            shape = RoundedCornerShape(5.dp)
                        )
                        .clickable {
                            if (concepts.isNotEmpty() || lastCheckpointTimeMs > 0L) {
                                showConceptDropdown = true
                            } else {
                                showSaveDialog = true
                            }
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            activeConcept != null -> "⚡ ${activeConcept.name}"
                            concepts.isNotEmpty() -> "Pilih Konsep • ${concepts.size}"
                            lastCheckpointTimeMs > 0L -> "🛡️ Auto-Save Aktif"
                            else -> "Belum Ada Konsep"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (activeConcept != null) EmeraldGreen else Color.LightGray,
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

                DropdownMenu(
                    expanded = showConceptDropdown,
                    onDismissRequest = { showConceptDropdown = false },
                    modifier = Modifier.background(DarkSurface)
                ) {
                    // Opsi Cepat Pulihkan Sesi Terakhir (Anti-Restart)
                    if (lastCheckpointTimeMs > 0L) {
                        val timeStr = remember(lastCheckpointTimeMs) {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastCheckpointTimeMs))
                        }
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Pulihkan Sesi Terakhir ($timeStr)",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldGreen
                                    )
                                }
                            },
                            onClick = {
                                showConceptDropdown = false
                                scope.launch {
                                    val res = conceptManager.restoreLastAutoCheckpoint()
                                    res.onSuccess { restored ->
                                        onNotifyUser("⚡ Sesi terakhir (${restored.videoCount} Video, ${restored.audioCount} MP3) berhasil dipulihkan!")
                                    }.onFailure { err ->
                                        onNotifyUser("⚠️ ${err.message}")
                                    }
                                }
                            }
                        )
                        if (concepts.isNotEmpty()) {
                            HorizontalDivider(color = DarkCardBorder, thickness = 0.5.dp)
                        }
                    }

                    concepts.forEach { item ->
                        val isSelected = item.id == activeConceptId
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = if (isSelected) NeonCyan else AmberWarning,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = item.name,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) NeonCyan else Color.White
                                    )
                                    Text(
                                        text = "${item.videoCount}V/${item.audioCount}A",
                                        fontSize = 8.5.sp,
                                        color = Color.Gray,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            },
                            onClick = {
                                showConceptDropdown = false
                                scope.launch {
                                    val res = conceptManager.restoreConcept(item.id)
                                    res.onSuccess { restored ->
                                        onNotifyUser("⚡ Konsep '${restored.name}' dipulihkan!")
                                    }.onFailure { err ->
                                        onNotifyUser("⚠️ Gagal memulihkan konsep: ${err.message}")
                                    }
                                }
                            },
                            modifier = Modifier.testTag("pill_concept_${item.id}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Sisi Kanan: Tombol Simpan & Kelola Backup/Drive Sejajar
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(EmeraldGreen.copy(alpha = 0.18f))
                        .border(0.6.dp, EmeraldGreen.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
                        .clickable { showSaveDialog = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("btn_quick_save_concept")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Simpan Konsep",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "Simpan",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .border(0.6.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                        .clickable { showManageDialog = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("btn_open_concept_manager")
                ) {
                    Text(
                        text = "☁️ Backup • ${concepts.size}",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveConceptDialog(
            conceptManager = conceptManager,
            defaultName = "Konsep #${concepts.size + 1}",
            activeConcept = activeConcept,
            onDismiss = { showSaveDialog = false },
            onSaved = { savedProfile, isOverwrite ->
                showSaveDialog = false
                val actionWord = if (isOverwrite) "diperbarui" else "disimpan"
                onNotifyUser("💾 Konsep '${savedProfile.name}' berhasil $actionWord ke Folder Backup Lokal!")
            }
        )
    }

    if (showManageDialog) {
        ConceptVaultManagerDialog(
            conceptManager = conceptManager,
            onDismiss = { showManageDialog = false },
            onNotifyUser = onNotifyUser
        )
    }
}

/**
 * Dialog Simpan Konsep Baru atau Timpa Konsep Aktif.
 */
@Composable
fun SaveConceptDialog(
    conceptManager: ConceptBackupManager,
    defaultName: String,
    activeConcept: ConceptProfile?,
    onDismiss: () -> Unit,
    onSaved: (ConceptProfile, Boolean) -> Unit
) {
    var conceptName by remember { mutableStateOf(defaultName) }
    var checklistOptions by remember { mutableStateOf(BackupChecklistOptions()) }
    val customVaultPath by conceptManager.customVaultPath.collectAsState()
    val previewSnapshot = remember(checklistOptions) {
        conceptManager.captureCurrentStateSnapshot(
            id = "PREVIEW",
            name = defaultName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            options = checklistOptions
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Backup Konsep 1-Klik",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Pilih ceklis item yang ingin dimasukkan ke Konsep & Folder Backup Lokal sebelum disimpan:",
                    fontSize = 10.sp,
                    color = Color.LightGray
                )

                BackupChecklistConfirmDropdown(
                    title = "☑️ Ceklis Item yang Di-Backup ke Konsep",
                    options = checklistOptions,
                    onOptionsChange = { checklistOptions = it },
                    defaultExpanded = true
                )

                // Kotak Ringkasan Cakupan Backup
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "📦 Ringkasan Isi Konsep (${checklistOptions.selectedCount}/6 Kategori):",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Text(
                        text = "• Media Fisik: ${previewSnapshot.videoCount} Video MP4 & ${previewSnapshot.audioCount} Musik MP3",
                        fontSize = 9.sp,
                        color = Color.White
                    )
                    Text(
                        text = "• Mode & Siklus: ${previewSnapshot.modeSummary} (termasuk ABA/PM1..n & AX/SR)",
                        fontSize = 9.sp,
                        color = Color.White
                    )
                    Text(
                        text = "• Overlay & Koreo: ${previewSnapshot.overlaySummary} + Resolusi/Anti-Hash",
                        fontSize = 9.sp,
                        color = Color.White
                    )
                    Text(
                        text = "• Lokasi: $customVaultPath",
                        fontSize = 8.sp,
                        color = EmeraldGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace
                    )
                }

                OutlinedTextField(
                    value = conceptName,
                    onValueChange = { conceptName = it },
                    label = { Text("Nama Konsep (Contoh: Konsep 1 - Skincare)", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_concept_name"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                if (activeConcept != null) {
                    OutlinedButton(
                        onClick = {
                            val updated = conceptManager.updateConceptWithCurrentState(activeConcept.id, checklistOptions)
                            if (updated != null) {
                                onSaved(updated, true)
                            }
                        },
                        enabled = checklistOptions.anySelected,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Timpa Konsep Aktif: '${activeConcept.name}' (${checklistOptions.selectedCount}/6)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val saved = conceptManager.saveCurrentAsConcept(conceptName, checklistOptions)
                    onSaved(saved, false)
                },
                enabled = checklistOptions.anySelected,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black),
                modifier = Modifier.testTag("btn_confirm_save_concept")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Simpan Konsep (${checklistOptions.selectedCount}/6)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color.Gray, fontSize = 11.sp)
            }
        }
    )
}

/**
 * Dialog Manajer Koleksi Konsep Lengkap, Pengaturan Folder Backup Lokal Custom,
 * Auto-Save & Auto-Restore Realtime (Anti-Restart HP), serta Ekspor/Impor Paket Lengkap (.ZIP) ke Google Drive.
 */
@Composable
fun ConceptVaultManagerDialog(
    conceptManager: ConceptBackupManager,
    onDismiss: () -> Unit,
    onNotifyUser: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val concepts by conceptManager.concepts.collectAsState()
    val activeConceptId by conceptManager.activeConceptId.collectAsState()
    val customVaultPath by conceptManager.customVaultPath.collectAsState()
    val isAutoBackupEnabled by conceptManager.isAutoBackupEnabled.collectAsState()
    val lastCheckpointTimeMs by conceptManager.lastAutoCheckpointTimeMs.collectAsState()
    val isBusy by conceptManager.isBusy.collectAsState()
    val busyProgressText by conceptManager.busyProgressText.collectAsState()

    // 0 = Daftar Konsep & Pulihkan Cepat, 1 = Folder Lokal & Google Drive (.ZIP)
    var activeTab by remember { mutableIntStateOf(0) }
    var storageStats by remember { mutableStateOf(conceptManager.getVaultStorageStats()) }

    var showCreateNewDialog by remember { mutableStateOf(false) }
    var showChangeFolderDialog by remember { mutableStateOf(false) }
    var conceptToRename by remember { mutableStateOf<ConceptProfile?>(null) }
    var conceptToDelete by remember { mutableStateOf<ConceptProfile?>(null) }

    // Opsi ceklis bersama untuk Backup & Restore di dalam dialog
    var checklistOptions by remember { mutableStateOf(BackupChecklistOptions()) }
    // Aksi tertunda yang menunggu konfirmasi ceklis dropdown: "DRIVE", "SAVE_ZIP", "IMPORT_ZIP", atau null
    var pendingZipAction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(concepts.size, customVaultPath, lastCheckpointTimeMs, isBusy) {
        storageStats = conceptManager.getVaultStorageStats()
    }

    // Launcher Simpan File .ZIP Lengkap ke Perangkat / Google Drive
    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        if (destUri != null) {
            scope.launch {
                val res = conceptManager.exportFullBackupZipToUri(destUri, checklistOptions)
                res.onSuccess { msg ->
                    storageStats = conceptManager.getVaultStorageStats()
                    onNotifyUser(msg)
                }.onFailure { err ->
                    onNotifyUser("⚠️ Gagal menyimpan ZIP: ${err.message}")
                }
            }
        }
    }

    // Launcher Impor File .ZIP Lengkap dari Google Drive / Penyimpanan HP
    val openZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { sourceUri ->
        if (sourceUri != null) {
            scope.launch {
                val res = conceptManager.importFullBackupZipFromUri(sourceUri, checklistOptions)
                res.onSuccess { report ->
                    storageStats = conceptManager.getVaultStorageStats()
                    onNotifyUser(report.summaryMessage)
                    onDismiss()
                }.onFailure { err ->
                    onNotifyUser("⚠️ Gagal mengimpor backup ZIP: ${err.message}")
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = DarkSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Pusat Backup & Konsep 1-Klik",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Folder Lokal Custom • Auto-Restore • Google Drive (.ZIP)",
                                fontSize = 8.5.sp,
                                color = NeonCyan
                            )
                        }
                    }
                }

                // Sub-Tab Navigasi: [📂 Daftar Konsep] | [☁️ Folder & Google Drive]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(DarkBackground)
                        .border(0.8.dp, DarkCardBorder, RoundedCornerShape(7.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabItems = listOf(
                        "📂 Konsep (${concepts.size})" to 0,
                        "☁️ Folder & Drive (.ZIP)" to 1
                    )
                    tabItems.forEach { (label, idx) ->
                        val selected = activeTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (selected) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = if (selected) 1.dp else 0.dp,
                                    color = if (selected) NeonCyan else Color.Transparent,
                                    shape = RoundedCornerShape(5.dp)
                                )
                                .clickable(enabled = !isBusy) { activeTab = idx }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.5.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected) NeonCyan else Color.LightGray,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Indikator Progress Saat Mengemas / Mengekstrak File Fisik Video & MP3
                if (isBusy) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan.copy(alpha = 0.14f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NeonCyan
                        )
                        Text(
                            text = busyProgressText ?: "Memproses file backup...",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (activeTab == 0) {
                    // =========================================================
                    // TAB 0: DAFTAR KONSEP 1-KLIK & PEMULIHAN SESI TERAKHIR
                    // =========================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { showCreateNewDialog = true },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreen,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan Konsep Baru", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val rep = conceptManager.deduplicateAndCleanVault()
                                    val freedMb = String.format(Locale.US, "%.1f", rep.freedBytes / (1024.0 * 1024.0))
                                    storageStats = conceptManager.getVaultStorageStats()
                                    onNotifyUser(
                                        "🧹 Vault Rapi! ${rep.mergedDuplicateCount} duplikat digabung, ${rep.deletedOrphanCount} file sampah dihapus (+${freedMb} MB hemat)."
                                    )
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "Bersihkan Duplikat",
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Anti-Duplikat", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Baris Pintasan Anti-Restart (Pulihkan Sesi Terakhir 1-Klik)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(DarkBackground)
                            .border(0.8.dp, if (isAutoBackupEnabled) EmeraldGreen.copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isAutoBackupEnabled) EmeraldGreen else Color.Gray)
                                )
                                Text(
                                    text = if (isAutoBackupEnabled) "Auto-Save & Anti-Restart ON" else "Auto-Save Nonaktif",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAutoBackupEnabled) EmeraldGreen else Color.LightGray
                                )
                            }
                            val cpLabel = if (storageStats.lastCheckpointTimeMs > 0L) {
                                val fmt = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
                                "Tersimpan otomatis: ${fmt.format(Date(storageStats.lastCheckpointTimeMs))}"
                            } else {
                                "Siap mengamankan video, MP3 & pengaturan secara real-time"
                            }
                            Text(
                                text = cpLabel,
                                fontSize = 8.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (storageStats.hasAutoCheckpoint) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(EmeraldGreen.copy(alpha = 0.18f))
                                    .border(0.7.dp, EmeraldGreen, RoundedCornerShape(5.dp))
                                    .clickable(enabled = !isBusy) {
                                        scope.launch {
                                            val res = conceptManager.restoreLastAutoCheckpoint()
                                            res.onSuccess { restored ->
                                                onNotifyUser("⚡ Sesi Terakhir (${restored.videoCount} Video, ${restored.audioCount} MP3) berhasil dipulihkan!")
                                                onDismiss()
                                            }.onFailure { err ->
                                                onNotifyUser("⚠️ ${err.message}")
                                            }
                                        }
                                    }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⚡ Pulihkan Sesi",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                            }
                        }
                    }

                    if (concepts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(105.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Belum Ada Profil Konsep Tersimpan",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ketuk 'Simpan Konsep Baru' untuk menyimpan Konsep 1, Konsep 2, dst., atau buka tab '☁️ Folder & Drive (.ZIP)' untuk impor backup.",
                                    fontSize = 8.5.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        val sdf = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(concepts, key = { it.id }) { concept ->
                                val isCurrent = concept.id == activeConceptId
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                    border = BorderStroke(
                                        width = if (isCurrent) 1.2.dp else 0.8.dp,
                                        color = if (isCurrent) NeonCyan else DarkCardBorder
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
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
                                                if (isCurrent) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(7.dp)
                                                            .clip(CircleShape)
                                                            .background(NeonCyan)
                                                    )
                                                }
                                                Text(
                                                    text = concept.name,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isCurrent) NeonCyan else Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Text(
                                                text = sdf.format(Date(concept.updatedAt)),
                                                fontSize = 8.sp,
                                                color = Color.Gray,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        // Badge Ringkasan Isi Konsep
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(EmeraldGreen.copy(alpha = 0.15f))
                                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                            ) {
                                                Text(
                                                    text = "🎬 ${concept.videoCount} Video • 🎵 ${concept.audioCount} MP3",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldGreen
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(NeonPurple.copy(alpha = 0.18f))
                                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                            ) {
                                                Text(
                                                    text = concept.modeSummary,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NeonPurple
                                                )
                                            }
                                        }

                                        Text(
                                            text = "⚙️ ${concept.overlaySummary}",
                                            fontSize = 8.sp,
                                            color = Color.LightGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Deretan Tombol Aksi: Restore 1-Klik, Timpa/Update, Rename, Hapus
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        val res = conceptManager.restoreConcept(concept.id)
                                                        res.onSuccess { restored ->
                                                            onNotifyUser("⚡ Konsep '${restored.name}' berhasil di-restore 100%!")
                                                            onDismiss()
                                                        }.onFailure { err ->
                                                            onNotifyUser("⚠️ Gagal memulihkan konsep: ${err.message}")
                                                        }
                                                    }
                                                },
                                                enabled = !isBusy,
                                                modifier = Modifier.height(28.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isCurrent) NeonCyan else EmeraldGreen,
                                                    contentColor = Color.Black
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bolt,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = if (isCurrent) "Muat Ulang" else "Pakai Konsep",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(DarkSurface)
                                                        .border(0.6.dp, DarkCardBorder, RoundedCornerShape(5.dp))
                                                        .clickable(enabled = !isBusy) {
                                                            val updated = conceptManager.updateConceptWithCurrentState(concept.id)
                                                            if (updated != null) {
                                                                onNotifyUser("🔄 Konsep '${updated.name}' diperbarui dengan settingan sekarang!")
                                                            }
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "🔄 Update",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = NeonCyan
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { conceptToRename = concept },
                                                    enabled = !isBusy,
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Ubah Nama",
                                                        tint = Color.LightGray,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { conceptToDelete = concept },
                                                    enabled = !isBusy,
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Hapus Konsep",
                                                        tint = CyberPink,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // =========================================================
                    // TAB 1: FOLDER LOKAL CUSTOM, AUTO-RESTORE & GOOGLE DRIVE (.ZIP)
                    // =========================================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. KARTU AUTO-SAVE & AUTO-RESTORE REALTIME (ANTI-RESTART HP)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            border = BorderStroke(1.dp, if (isAutoBackupEnabled) EmeraldGreen.copy(alpha = 0.6f) else DarkCardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(9.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🛡️", fontSize = 13.sp)
                                        Column {
                                            Text(
                                                text = "Auto-Save & Pulihkan Otomatis",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Anti-Restart HP • Pulih otomatis saat aplikasi dibuka",
                                                fontSize = 8.sp,
                                                color = EmeraldGreen
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = isAutoBackupEnabled,
                                        onCheckedChange = { enabled ->
                                            conceptManager.setAutoBackupEnabled(enabled)
                                            onNotifyUser(
                                                if (enabled) "🛡️ Auto-Save & Auto-Restore Realtime diaktifkan!"
                                                else "⚪ Auto-Save Realtime dinonaktifkan."
                                            )
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = EmeraldGreen,
                                            uncheckedThumbColor = Color.LightGray,
                                            uncheckedTrackColor = DarkBackground
                                        ),
                                        modifier = Modifier
                                            .scale(0.75f)
                                            .height(22.dp)
                                    )
                                }

                                Text(
                                    text = "Setiap perubahan Video, MP3, Mode IFS/Spill, Stiker, Watermark & Pengaturan langsung disimpan real-time ke Folder Backup Lokal.",
                                    fontSize = 8.5.sp,
                                    color = Color.LightGray
                                )
                            }
                        }

                        // 2. KARTU LOKASI FOLDER BACKUP LOKAL CUSTOM (DATABASE + VIDEO + MP3)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.55f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(9.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Lokasi Folder Backup Lokal",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    val totalMb = remember(storageStats.totalSizeBytes) {
                                        String.format(Locale.US, "%.1f MB", storageStats.totalSizeBytes / (1024.0 * 1024.0))
                                    }
                                    val availStr = remember(storageStats.availableStorageBytes) {
                                        if (storageStats.availableStorageBytes >= 1024L * 1024L * 1024L) {
                                            String.format(Locale.US, "Bebas %.1f GB", storageStats.availableStorageBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
                                        } else {
                                            String.format(Locale.US, "Bebas %d MB", storageStats.availableStorageBytes / (1024L * 1024L))
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(NeonCyan.copy(alpha = 0.16f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${storageStats.mediaFileCount} Media • $totalMb • $availStr",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkBackground)
                                        .border(0.7.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = customVaultPath,
                                        fontSize = 8.5.sp,
                                        color = EmeraldGreen,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = { showChangeFolderDialog = true },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonCyan,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CreateNewFolder,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ubah Folder", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val res = conceptManager.syncFromCustomLocalVaultNow()
                                                storageStats = conceptManager.getVaultStorageStats()
                                                res.onSuccess { msg -> onNotifyUser(msg) }
                                                    .onFailure { err -> onNotifyUser("⚠️ ${err.message}") }
                                            }
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.7f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pulihkan dari Folder", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // 3. KARTU EKSPOR & IMPOR PAKET LENGKAP (.ZIP) KE GOOGLE DRIVE
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.65f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(9.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = NeonPurple,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Cadangan Paket Lengkap Google Drive (.ZIP)",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "1 File berisi semua Video MP4 + Audio MP3 + Koreo Auto-Tap + Stiker + Konsep",
                                            fontSize = 8.sp,
                                            color = Color(0xFFE879F9)
                                        )
                                    }
                                }

                                // Dropdown Konfirmasi Ceklis sebelum proses Backup / Restore dijalankan
                                BackupChecklistConfirmDropdown(
                                    title = when (pendingZipAction) {
                                        "DRIVE" -> "☑️ Konfirmasi Ceklis sebelum Upload ke Google Drive:"
                                        "SAVE_ZIP" -> "☑️ Konfirmasi Ceklis sebelum Simpan File .ZIP:"
                                        "IMPORT_ZIP" -> "☑️ Konfirmasi Ceklis Item yang Akan Di-Restore dari .ZIP:"
                                        else -> "☑️ Ceklis Item yang Masuk Backup / Restore (.ZIP)"
                                    },
                                    options = checklistOptions,
                                    onOptionsChange = { checklistOptions = it },
                                    actionButtonLabel = when (pendingZipAction) {
                                        "DRIVE" -> "✅ Lanjut Upload ke Drive"
                                        "SAVE_ZIP" -> "✅ Lanjut Simpan .ZIP"
                                        "IMPORT_ZIP" -> "✅ Pilih File .ZIP & Restore"
                                        else -> null
                                    },
                                    onConfirmAction = if (pendingZipAction != null) {
                                        {
                                            val act = pendingZipAction
                                            pendingZipAction = null
                                            when (act) {
                                                "DRIVE" -> {
                                                    scope.launch {
                                                        val intentRes = conceptManager.prepareShareBackupZipIntent(checklistOptions)
                                                        intentRes.onSuccess { chooserIntent ->
                                                            storageStats = conceptManager.getVaultStorageStats()
                                                            context.startActivity(chooserIntent)
                                                        }.onFailure { err ->
                                                            onNotifyUser("⚠️ Gagal menyiapkan paket ZIP: ${err.message}")
                                                        }
                                                    }
                                                }
                                                "SAVE_ZIP" -> {
                                                    val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                                    createZipLauncher.launch("LiveHost_FullBackup_$ts.zip")
                                                }
                                                "IMPORT_ZIP" -> {
                                                    openZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                                }
                                            }
                                        }
                                    } else null,
                                    onCancelAction = if (pendingZipAction != null) {
                                        { pendingZipAction = null }
                                    } else null,
                                    defaultExpanded = pendingZipAction != null
                                )

                                // Tombol 1: Bagikan / Upload Langsung ke Google Drive (Buka Konfirmasi Ceklis terlebih dahulu)
                                Button(
                                    onClick = {
                                        pendingZipAction = "DRIVE"
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .testTag("btn_share_zip_google_drive"),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonPurple,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "☁️ Upload / Bagikan Paket (.ZIP) ke Google Drive",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Baris Tombol 2 & 3: Simpan File .ZIP & Impor File .ZIP (Buka Konfirmasi Ceklis terlebih dahulu)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            pendingZipAction = "SAVE_ZIP"
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .testTag("btn_save_zip_file"),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                                    ) {
                                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Simpan File .ZIP", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            pendingZipAction = "IMPORT_ZIP"
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .testTag("btn_import_zip_file"),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = EmeraldGreen,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Impor Backup .ZIP", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy
            ) {
                Text("Tutup", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        }
    )

    // Dialog Pilih / Ubah Lokasi Folder Backup Lokal Custom
    if (showChangeFolderDialog) {
        CustomVaultFolderPickerDialog(
            conceptManager = conceptManager,
            currentPath = customVaultPath,
            onDismiss = { showChangeFolderDialog = false },
            onFolderChanged = { newPath ->
                showChangeFolderDialog = false
                storageStats = conceptManager.getVaultStorageStats()
                onNotifyUser("📁 Folder Backup Lokal dipindahkan ke: $newPath")
            },
            onError = { errMsg ->
                onNotifyUser("⚠️ $errMsg")
            }
        )
    }

    if (showCreateNewDialog) {
        SaveConceptDialog(
            conceptManager = conceptManager,
            defaultName = "Konsep #${concepts.size + 1}",
            activeConcept = concepts.firstOrNull { it.id == activeConceptId },
            onDismiss = { showCreateNewDialog = false },
            onSaved = { savedProfile, isOverwrite ->
                showCreateNewDialog = false
                storageStats = conceptManager.getVaultStorageStats()
                val actionWord = if (isOverwrite) "diperbarui" else "disimpan"
                onNotifyUser("💾 Konsep '${savedProfile.name}' berhasil $actionWord!")
            }
        )
    }

    conceptToRename?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { conceptToRename = null },
            containerColor = DarkSurface,
            title = { Text("Ubah Nama Konsep", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Nama Konsep", fontSize = 10.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (conceptManager.renameConcept(target.id, newName)) {
                            onNotifyUser("✏️ Nama konsep diubah menjadi '${newName.trim()}'")
                        }
                        conceptToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                ) {
                    Text("Simpan", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { conceptToRename = null }) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }

    conceptToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { conceptToDelete = null },
            containerColor = DarkSurface,
            title = { Text("Hapus Konsep '${target.name}'?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    text = "Konsep ini akan dihapus dari daftar. File video/MP3 yang masih dipakai di daftar aktif atau di konsep lain akan tetap aman.",
                    fontSize = 10.5.sp,
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDel = target
                        conceptToDelete = null
                        scope.launch {
                            conceptManager.deleteConcept(toDel.id)
                            storageStats = conceptManager.getVaultStorageStats()
                            onNotifyUser("🗑️ Konsep '${toDel.name}' telah dihapus.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink, contentColor = Color.White)
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { conceptToDelete = null }) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }
}

/**
 * Dialog Pemilih Folder Backup Lokal Custom (Preset 1-Klik + Pemilih Folder Android SAF + Input Path Manual).
 * Otomatis memindahkan seluruh database konsep & file fisik media ke lokasi baru.
 */
@Composable
fun CustomVaultFolderPickerDialog(
    conceptManager: ConceptBackupManager,
    currentPath: String,
    onDismiss: () -> Unit,
    onFolderChanged: (String) -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var pathInput by remember(currentPath) { mutableStateOf(currentPath) }
    val presets = remember { conceptManager.getPresetVaultDirectories() }
    val isBusy by conceptManager.isBusy.collectAsState()

    val folderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val resolved = conceptManager.resolveTreeUriToLocalPath(treeUri)
            if (!resolved.isNullOrBlank()) {
                pathInput = resolved
            } else {
                onError("Silakan pilih folder di Memori Internal Utama atau gunakan pilihan cepat di bawah.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = DarkSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "Atur Lokasi Folder Backup Lokal",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Pilih folder di memori HP untuk menyimpan database konsep beserta file fisik Video MP4 & Audio MP3. File backup lama akan otomatis dipindahkan ke folder baru:",
                    fontSize = 9.5.sp,
                    color = Color.LightGray
                )

                Text(
                    text = "⚡ Pilihan Lokasi Cepat (1-Klik):",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )

                presets.forEach { (title, presetPath) ->
                    val isSelected = pathInput.equals(presetPath, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) EmeraldGreen.copy(alpha = 0.16f) else DarkSurfaceVariant)
                            .border(
                                width = if (isSelected) 1.dp else 0.7.dp,
                                color = if (isSelected) EmeraldGreen else DarkCardBorder,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable(enabled = !isBusy) { pathInput = presetPath }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) EmeraldGreen else Color.White
                            )
                            Text(
                                text = presetPath,
                                fontSize = 7.5.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { folderTreeLauncher.launch(null) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE879F9))
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Cari Folder Lewat File Manager HP", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    label = { Text("Path Folder Backup Custom", fontSize = 9.5.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                    scope.launch {
                        val res = conceptManager.setCustomVaultDirectory(pathInput, migrateExistingFiles = true)
                        res.onSuccess { savedPath ->
                            onFolderChanged(savedPath)
                        }.onFailure { err ->
                            onError(err.message ?: "Gagal menyimpan lokasi folder backup")
                        }
                    }
                },
                enabled = !isBusy && pathInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black)
            ) {
                Text("Simpan & Pindahkan Backup", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Batal", color = Color.Gray, fontSize = 10.sp)
            }
        }
    )
}
