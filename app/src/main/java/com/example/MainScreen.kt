package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acopy.data.BackupRestoreManager
import com.acopy.updater.AppUpdateManager

@Composable
fun MainScreen(
    isFloatingRunning: Boolean,
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    onToggleFloating: (Boolean) -> Unit,
    onExpandFloatingPanel: () -> Unit,
    onMinimizeFloatingBubble: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    backupActiveCount: Int,
    backupTrashCount: Int,
    backupPinnedCount: Int,
    onExportBackupFile: () -> Unit,
    onSaveBackupToLocation: () -> Unit,
    onPickBackupFile: () -> Unit,
    onCopyBackupJson: () -> Unit,
    onPasteAndRestore: () -> Unit,
    pendingRestoreData: BackupRestoreManager.BackupParsedResult?,
    onConfirmRestore: (BackupRestoreManager.BackupParsedResult, BackupRestoreManager.RestoreMode) -> Unit,
    onDismissRestoreDialog: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val installedVersion = remember { AppUpdateManager.resolveInstalledVersion(context) }

    val backgroundBrush = remember(colors) {
        Brush.verticalGradient(
            colors = listOf(
                colors.background,
                colors.surface.copy(alpha = 0.85f),
                colors.background
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_icon),
                            contentDescription = stringResource(id = R.string.app_name),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Column {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                color = colors.onBackground
                            )
                            Text(
                                text = "v$installedVersion",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = if (isFloatingRunning) colors.tertiary.copy(alpha = 0.16f) else colors.surfaceVariant,
                        shape = CircleShape,
                        border = BorderStroke(
                            1.dp,
                            if (isFloatingRunning) colors.tertiary else colors.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isFloatingRunning) colors.tertiary else colors.onSurfaceVariant)
                            )
                            Text(
                                text = if (isFloatingRunning) "Aktif" else "Nonaktif",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFloatingRunning) colors.tertiary else colors.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Overlay Permission Prompt Card
                AnimatedVisibility(visible = !hasOverlayPermission) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overlay_permission_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.error.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Izin overlay diperlukan",
                                    tint = colors.error
                                )
                                Text(
                                    text = "Izin Tampilkan di Atas Aplikasi Lain Diperlukan",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.onSurface
                                )
                            }
                            Text(
                                text = "Aktifkan izin overlay agar gelembung dan panel floating dapat muncul di layar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Button(
                                onClick = onRequestOverlayPermission,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("grant_overlay_permission_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Izinkan Overlay", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Accessibility Service Prompt Card
                AnimatedVisibility(visible = !hasAccessibilityPermission) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("accessibility_permission_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.secondary.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Accessibility,
                                    contentDescription = "Izin aksesibilitas diperlukan",
                                    tint = colors.secondary
                                )
                                Text(
                                    text = "Layanan Aksesibilitas (Auto-Paste & Kolom)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.onSurface
                                )
                            }
                            Text(
                                text = "Aktifkan layanan Pintasan di pengaturan aksesibilitas agar dapat menempelkan teks langsung ke kolom input aplikasi lain.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Button(
                                onClick = onRequestAccessibilityPermission,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("grant_accessibility_permission_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.secondary,
                                    contentColor = colors.onSecondary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Aktifkan Aksesibilitas", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Floating Window Control Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("floating_control_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(
                        1.dp,
                        if (isFloatingRunning) colors.primary.copy(alpha = 0.6f) else colors.outline
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.primaryContainer.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = "Floating Window",
                                        tint = colors.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Pintasan Melayang & Catatan",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = if (isFloatingRunning) "Pintasan aktif (Sematkan & Lepas Pin aplikasi favorit)"
                                        else "Aktifkan gelembung & panel pintasan melayang",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = isFloatingRunning,
                                onCheckedChange = onToggleFloating,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("floating_toggle_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onPrimary,
                                    checkedTrackColor = colors.primary
                                )
                            )
                        }

                        AnimatedVisibility(visible = isFloatingRunning) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onMinimizeFloatingBubble,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("minimize_floating_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, colors.outline)
                                ) {
                                    Text("Mode Bubble", style = MaterialTheme.typography.labelMedium)
                                }

                                Button(
                                    onClick = onExpandFloatingPanel,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("expand_floating_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.primary,
                                        contentColor = colors.onPrimary
                                    )
                                ) {
                                    Text(
                                        text = "Buka Panel Multitask",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // CADANGKAN & PULIHKAN DATA (BACKUP & RESTORE)
                // ==========================================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_restore_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Title row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.tertiaryContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Cadangkan & Pulihkan",
                                    tint = colors.tertiary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Cadangkan & Pulihkan Data",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "Amankan catatan, riwayat sampah, dan aplikasi yang disematkan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }

                        // Data Status Chips
                        Surface(
                            color = colors.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$backupActiveCount",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary
                                    )
                                    Text(
                                        text = "Aktif",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(24.dp)
                                        .background(colors.outline.copy(alpha = 0.4f))
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$backupTrashCount",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.error
                                    )
                                    Text(
                                        text = "Sampah",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(24.dp)
                                        .background(colors.outline.copy(alpha = 0.4f))
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$backupPinnedCount",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.secondary
                                    )
                                    Text(
                                        text = "Disematkan",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Section 1: Ekspor / Simpan
                        Text(
                            text = "EKSPOR CADANGAN (BACKUP)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onExportBackupFile,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_backup_file_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bagikan / File", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = onSaveBackupToLocation,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_backup_folder_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.outline)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan ke...", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        OutlinedButton(
                            onClick = onCopyBackupJson,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("copy_backup_clipboard_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salin Teks Cadangan Lengkap ke Clipboard")
                        }

                        // Section 2: Impor / Pulihkan
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "IMPOR / PULIHKAN DATA (RESTORE)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.tertiary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onPickBackupFile,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("pick_backup_file_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.tertiary,
                                    contentColor = colors.onTertiary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buka File .JSON", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = onPasteAndRestore,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("paste_restore_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.secondary,
                                    contentColor = colors.onSecondary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dari Clipboard", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    // ============================================================
    // DIALOG KONFIRMASI PEMULIHAN (RESTORE PREVIEW & MODE SELECTION)
    // ============================================================
    if (pendingRestoreData != null) {
        var selectedMode by remember { mutableStateOf(BackupRestoreManager.RestoreMode.MERGE) }

        AlertDialog(
            onDismissRequest = onDismissRestoreDialog,
            icon = {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Konfirmasi Pemulihan Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Ditemukan data cadangan valid. Silakan tinjau detail berikut:",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )

                    Surface(
                        color = colors.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "• Aplikasi: ${pendingRestoreData.app} (v${pendingRestoreData.version})",
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (pendingRestoreData.exportDate.isNotBlank()) {
                                Text(
                                    text = "• Tanggal: ${pendingRestoreData.exportDate}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Text(
                                text = "• Catatan: ${pendingRestoreData.notes.size} total (${pendingRestoreData.activeNotesCount} aktif, ${pendingRestoreData.trashNotesCount} di sampah)",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.primary
                            )
                            Text(
                                text = "• Aplikasi Disematkan: ${pendingRestoreData.pinnedApps.size} item",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.secondary
                            )
                        }
                    }

                    Text(
                        text = "Pilih Metode Pemulihan:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Option 1: MERGE
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selectedMode == BackupRestoreManager.RestoreMode.MERGE) colors.primary else colors.outline.copy(alpha = 0.5f)
                        ),
                        color = if (selectedMode == BackupRestoreManager.RestoreMode.MERGE) colors.primary.copy(alpha = 0.08f) else colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = BackupRestoreManager.RestoreMode.MERGE }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMode == BackupRestoreManager.RestoreMode.MERGE,
                                onClick = { selectedMode = BackupRestoreManager.RestoreMode.MERGE },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "➕ Gabungkan (Merge - Aman)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "Menambah catatan baru & perbarui yang berubah tanpa menghapus catatan yang ada saat ini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Option 2: OVERWRITE
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selectedMode == BackupRestoreManager.RestoreMode.OVERWRITE) colors.error else colors.outline.copy(alpha = 0.5f)
                        ),
                        color = if (selectedMode == BackupRestoreManager.RestoreMode.OVERWRITE) colors.error.copy(alpha = 0.08f) else colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = BackupRestoreManager.RestoreMode.OVERWRITE }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMode == BackupRestoreManager.RestoreMode.OVERWRITE,
                                onClick = { selectedMode = BackupRestoreManager.RestoreMode.OVERWRITE },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.error)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "🔄 Timpa Semua (Pulihkan Penuh)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.error
                                )
                                Text(
                                    text = "Menggantikan seluruh catatan dan daftar pin dengan data cadangan ini secara bersih.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmRestore(pendingRestoreData, selectedMode)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedMode == BackupRestoreManager.RestoreMode.OVERWRITE) colors.error else colors.primary
                    )
                ) {
                    Text(
                        text = if (selectedMode == BackupRestoreManager.RestoreMode.OVERWRITE) "Terapkan Timpa Semua" else "Terapkan Gabungkan",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestoreDialog) {
                    Text("Batal")
                }
            }
        )
    }
}
