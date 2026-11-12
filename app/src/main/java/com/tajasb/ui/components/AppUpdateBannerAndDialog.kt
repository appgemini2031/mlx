package com.tajasb.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.tajasb.updater.AppUpdateManager
import java.io.File

private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF94A3B8)

@Composable
fun AppUpdateTopBanner(
    onOpenUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateManager = remember { AppUpdateManager.getInstance(context) }

    // Eksklusif untuk device yang diizinkan (Owner / VIP), tidak tampil pada device umum
    if (!updateManager.isAuthorizedForUpdate()) return

    val state by updateManager.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (!state.hasCheckedOnce && !state.isChecking) {
            updateManager.checkForUpdates(manualTrigger = false, showSystemNotification = true)
        }
    }

    val release = state.latestRelease
    AnimatedVisibility(
        visible = state.isUpdateAvailable && !state.isBannerDismissed && release != null,
        modifier = modifier
    ) {
        if (release != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag("optional_update_banner"),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = NeonCyan.copy(alpha = 0.12f)
                ),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Pembaruan",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "v${release.cleanVersion} Tersedia",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenUpdateDialog)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onOpenUpdateDialog,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp
                        ),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("banner_update_now_button")
                    ) {
                        Text(
                            text = "Update",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { updateManager.dismissBannerForSession() },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("banner_dismiss_update_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderUpdateApkChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateManager = remember { AppUpdateManager.getInstance(context) }

    // Fitur pembaruan APK eksklusif untuk device yang diizinkan (Owner / VIP)
    if (!updateManager.isAuthorizedForUpdate()) return

    val state by updateManager.uiState.collectAsState()

    // Hanya tampilkan tombol di header jika benar-benar ada versi baru yang belum terpasang
    if (!state.isUpdateAvailable || state.latestRelease == null) return

    val chipColor = EmeraldGreen

    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(chipColor.copy(alpha = 0.22f))
            .border(0.5.dp, chipColor.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .testTag("header_update_apk_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = "Update APK",
            tint = chipColor,
            modifier = Modifier.size(11.dp)
        )
    }
}

@Composable
fun SystemTabUpdateApkCard(
    onOpenUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateManager = remember { AppUpdateManager.getInstance(context) }

    // Fitur pembaruan APK eksklusif untuk device yang diizinkan (Owner / VIP)
    if (!updateManager.isAuthorizedForUpdate()) return

    val state by updateManager.uiState.collectAsState()
    val hasToken = state.githubToken.isNotBlank()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("system_update_apk_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(
            1.dp,
            if (state.isUpdateAvailable) EmeraldGreen.copy(alpha = 0.55f)
            else NeonCyan.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Pembaruan APK",
                        tint = if (state.isUpdateAvailable) EmeraldGreen else NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Update APK",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.isUpdateAvailable && state.latestRelease != null)
                                "Terpasang: v${state.currentVersion} → Baru: v${state.latestRelease?.cleanVersion}"
                            else
                                "✅ Versi Terbaru (v${state.currentVersion})",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isUpdateAvailable) EmeraldGreen else TextSecondary
                        )
                    }
                }

                if (state.isUpdateAvailable && state.latestRelease != null) {
                    Surface(
                        color = EmeraldGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, EmeraldGreen)
                    ) {
                        Text(
                            text = "BARU v${state.latestRelease?.cleanVersion}",
                            color = EmeraldGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Status Token",
                    tint = if (hasToken) EmeraldGreen else NeonCyan,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (hasToken) "Token PAT: ••••${state.githubToken.takeLast(4)}"
                           else "Token PAT belum diatur",
                    fontSize = 10.sp,
                    color = if (hasToken) EmeraldGreen else TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        updateManager.checkForUpdates(manualTrigger = true, showSystemNotification = true)
                    },
                    enabled = !state.isChecking && !state.isDownloading,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("check_update_apk_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
                ) {
                    if (state.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = NeonCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cek...", fontSize = 11.sp, color = NeonCyan)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Cek Pembaruan",
                            tint = NeonCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cek Update", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }

                if (state.isUpdateAvailable) {
                    Button(
                        onClick = onOpenUpdateDialog,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_update_apk_dialog_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Update Sekarang",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Update v${state.latestRelease?.cleanVersion ?: ""}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenUpdateDialog,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_update_apk_dialog_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DarkSurfaceVariant)
                    ) {
                        Text(
                            text = "⚙️ Token PAT",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val updateManager = remember { AppUpdateManager.getInstance(context) }

    // Eksklusif untuk device khusus yang diizinkan (Owner / VIP)
    if (!updateManager.isAuthorizedForUpdate()) {
        onDismiss()
        return
    }

    val state by updateManager.uiState.collectAsState()
    var repoInput by remember(state.repoSlug) { mutableStateOf(state.repoSlug) }
    var tokenInput by remember(state.githubToken) { mutableStateOf(state.githubToken) }
    var isTokenVisible by remember { mutableStateOf(false) }
    var showRepoSettings by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_update_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Pembaruan APK",
                            tint = NeonCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Pembaruan APK (Opsional)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Terpasang: v${state.currentVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup Dialog Pembaruan",
                            tint = TextSecondary
                        )
                    }
                }

                // Version & status summary box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkBackground)
                        .border(
                            1.dp,
                            if (state.isUpdateAvailable) EmeraldGreen.copy(alpha = 0.5f)
                            else DarkSurfaceVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = state.statusMessage,
                        color = if (state.isUpdateAvailable) EmeraldGreen else TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    val release = state.latestRelease
                    if (release != null) {
                        Text(
                            text = "Versi Rilis: ${release.versionTag} (${release.releaseName})",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (release.apkSizeBytes > 0L) {
                            Text(
                                text = "Ukuran APK: ${AppUpdateManager.formatBytes(release.apkSizeBytes)}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        if (release.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Catatan Pembaruan:",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant.copy(alpha = 0.45f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = release.releaseNotes,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (!state.errorMessage.isNullOrBlank()) {
                        Text(
                            text = state.errorMessage ?: "",
                            color = Color(0xFFFF6B6B),
                            fontSize = 11.sp
                        )
                    }
                }

                // Download progress bar
                if (state.isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { (state.downloadProgressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = EmeraldGreen,
                            trackColor = DarkSurfaceVariant
                        )
                        Text(
                            text = "Mengunduh ${state.downloadProgressPercent}% (${AppUpdateManager.formatBytes(state.downloadedBytes)} / ${AppUpdateManager.formatBytes(state.totalBytes)})",
                            color = EmeraldGreen,
                            fontSize = 11.sp
                        )
                    }
                }

                // Primary action buttons: only show download/install when an update is genuinely available
                val release = state.latestRelease
                if (state.isUpdateAvailable && release != null) {
                    Button(
                        onClick = {
                            updateManager.downloadAndInstallLatestApk(context)
                        },
                        enabled = !state.isDownloading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_and_install_apk_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Unduh dan Pasang APK",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                state.isDownloading -> "Mengunduh APK..."
                                state.downloadedApkPath != null && File(state.downloadedApkPath!!).exists() ->
                                    "Pasang APK v${release.cleanVersion}"
                                !release.apkDownloadUrl.isNullOrBlank() ->
                                    "Unduh & Pasang APK v${release.cleanVersion}"
                                else ->
                                    "Buka Rilis v${release.cleanVersion}"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "✅ APK Sudah Versi Terbaru (v${state.currentVersion})",
                            color = EmeraldGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            updateManager.checkForUpdates(manualTrigger = true, showSystemNotification = true)
                        },
                        enabled = !state.isChecking && !state.isDownloading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_check_update_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = if (state.isChecking) "Mengecek..." else "🔄 Cek Ulang",
                            fontSize = 11.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            updateManager.openReleasePageInBrowser(context)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_open_github_releases_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "🌐 Rilis GitHub",
                            fontSize = 11.sp,
                            color = NeonPurple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Expandable repository & GitHub PAT configuration
                TextButton(
                    onClick = { showRepoSettings = !showRepoSettings },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = if (showRepoSettings) "▲ Sembunyikan Pengaturan Repo & Token"
                               else "⚙️ Sumber Repo & Token GitHub (${state.repoSlug})",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                AnimatedVisibility(visible = showRepoSettings) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground.copy(alpha = 0.6f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Konfigurasi Repositori Privat (Khusus Owner)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )

                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            label = { Text("GitHub Repo (owner/repo)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("GitHub Personal Access Token (PAT)", fontSize = 11.sp) },
                            placeholder = { Text("ghp_... atau github_pat_...", fontSize = 11.sp, color = TextSecondary) },
                            singleLine = true,
                            visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Token Key",
                                    tint = if (tokenInput.isNotBlank()) EmeraldGreen else NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { isTokenVisible = !isTokenVisible },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isTokenVisible) "Sembunyikan Token" else "Tampilkan Token",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_token_input_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Text(
                            text = "💡 Format Repositori: username/repo (contoh: santrehprint/tajasb).\n🔑 Token PAT: Wajib memiliki centang izin scope 'repo' (Full control of private repositories) di GitHub agar tidak 404 pada repo privat.",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )

                        if (!state.testConnectionMessage.isNullOrBlank()) {
                            val isSuccess = state.testConnectionMessage?.startsWith("✅") == true
                            Surface(
                                color = if (isSuccess) EmeraldGreen.copy(alpha = 0.15f) else Color(0xFFFF6B6B).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isSuccess) EmeraldGreen.copy(alpha = 0.5f) else Color(0xFFFF6B6B).copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.testConnectionMessage ?: "",
                                    color = if (isSuccess) EmeraldGreen else Color(0xFFFF8E8E),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    updateManager.setGithubRepoSlug(repoInput)
                                    updateManager.saveGithubToken(tokenInput)
                                    updateManager.testConnection()
                                },
                                enabled = !state.isTestingConnection && !state.isChecking,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_github_connection_button")
                            ) {
                                if (state.isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = NeonPurple
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Menguji...", fontSize = 10.sp, color = NeonPurple)
                                } else {
                                    Text("🔍 Tes Koneksi", fontSize = 10.sp, color = NeonPurple, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (tokenInput.isNotBlank() || state.githubToken.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        tokenInput = ""
                                        updateManager.saveGithubToken("")
                                    },
                                    modifier = Modifier.testTag("clear_github_token_button")
                                ) {
                                    Text("Hapus", fontSize = 10.sp, color = Color(0xFFFF6B6B))
                                }
                            }

                            Button(
                                onClick = {
                                    updateManager.setGithubRepoSlug(repoInput)
                                    updateManager.saveGithubToken(tokenInput)
                                    updateManager.checkForUpdates(manualTrigger = true, showSystemNotification = true)
                                },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("save_token_and_repo_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Simpan & Cek", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Optional skip / dismiss row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isUpdateAvailable && state.latestRelease != null) {
                        TextButton(
                            onClick = {
                                updateManager.skipCurrentUpdateVersion()
                                onDismiss()
                            },
                            modifier = Modifier.testTag("skip_version_optional_button")
                        ) {
                            Text(
                                text = "Lewati Versi Ini",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(
                        onClick = {
                            updateManager.dismissBannerForSession()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("later_optional_update_button")
                    ) {
                        Text(
                            text = "Nanti Saja (Opsional)",
                            fontSize = 12.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
