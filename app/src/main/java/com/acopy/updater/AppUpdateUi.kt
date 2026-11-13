package com.acopy.updater

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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File

@Composable
fun AppUpdateTopBanner(
    onOpenUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateManager = remember { AppUpdateManager.getInstance(context) }
    val state by updateManager.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (!state.hasCheckedOnce && !state.isChecking) {
            updateManager.checkForUpdates(manualTrigger = false, showSystemNotification = false)
        }
    }

    val release = state.latestRelease
    AnimatedVisibility(
        visible = state.isUpdateAvailable && !state.isBannerDismissed && release != null,
        modifier = modifier
    ) {
        if (release != null) {
            val colors = MaterialTheme.colorScheme
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("optional_update_banner"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.primaryContainer.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Pembaruan tersedia",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Pembaruan v${release.cleanVersion} tersedia",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenUpdateDialog)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onOpenUpdateDialog,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.tertiary,
                            contentColor = colors.onTertiary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("banner_update_now_button")
                    ) {
                        Text(
                            text = "Update",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { updateManager.dismissBannerForSession() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("banner_dismiss_update_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup banner pembaruan",
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GitHubUpdateCard(
    onOpenUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateManager = remember { AppUpdateManager.getInstance(context) }
    val state by updateManager.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val hasToken = state.githubToken.isNotBlank()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("github_update_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(
            1.dp,
            if (state.isUpdateAvailable) colors.tertiary.copy(alpha = 0.6f)
            else colors.outline
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.primaryContainer.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Pembaruan GitHub",
                            tint = if (state.isUpdateAvailable) colors.tertiary else colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Pembaruan APK GitHub",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface
                        )
                        Text(
                            text = if (state.isUpdateAvailable && state.latestRelease != null) {
                                "v${state.currentVersion} -> v${state.latestRelease?.cleanVersion}"
                            } else {
                                "Versi terpasang v${state.currentVersion}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isUpdateAvailable) colors.tertiary else colors.onSurfaceVariant
                        )
                    }
                }

                if (state.isUpdateAvailable && state.latestRelease != null) {
                    Surface(
                        color = colors.tertiary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.tertiary)
                    ) {
                        Text(
                            text = "BARU",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.65f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Repo: ${state.repoSlug}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Status token",
                        tint = if (hasToken) colors.tertiary else colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (hasToken) "Token PAT: ••••${state.githubToken.takeLast(4)}"
                        else "Token PAT belum diatur (repo publik)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasToken) colors.tertiary else colors.onSurfaceVariant
                    )
                }
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        updateManager.checkForUpdates(manualTrigger = true, showSystemNotification = false)
                    },
                    enabled = !state.isChecking && !state.isDownloading,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("check_update_apk_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.65f))
                ) {
                    if (state.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mengecek...", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Cek pembaruan",
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cek Update", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                    }
                }

                Button(
                    onClick = onOpenUpdateDialog,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("open_update_apk_dialog_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isUpdateAvailable) colors.tertiary else colors.surfaceVariant,
                        contentColor = if (state.isUpdateAvailable) colors.onTertiary else colors.onSurface
                    )
                ) {
                    Text(
                        text = if (state.isUpdateAvailable) "Unduh Update" else "Repo & Token",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
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
    val state by updateManager.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme

    var repoInput by remember(state.repoSlug) { mutableStateOf(state.repoSlug) }
    var tokenInput by remember(state.githubToken) { mutableStateOf(state.githubToken) }
    var isTokenVisible by remember { mutableStateOf(false) }
    var showRepoSettings by remember { mutableStateOf(!state.isUpdateAvailable) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_update_dialog"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Pembaruan APK GitHub",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface
                            )
                            Text(
                                text = "Terpasang: v${state.currentVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup dialog pembaruan",
                            tint = colors.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.background)
                        .border(
                            1.dp,
                            if (state.isUpdateAvailable) colors.tertiary.copy(alpha = 0.5f) else colors.outline,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = state.statusMessage,
                        color = if (state.isUpdateAvailable) colors.tertiary else colors.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    val release = state.latestRelease
                    if (release != null) {
                        Text(
                            text = "Rilis: ${release.versionTag} (${release.releaseName})",
                            color = colors.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (release.apkSizeBytes > 0L) {
                            Text(
                                text = "Ukuran APK: ${AppUpdateManager.formatBytes(release.apkSizeBytes)}",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (release.releaseNotes.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 110.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.45f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = release.releaseNotes,
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    if (!state.errorMessage.isNullOrBlank()) {
                        Text(
                            text = state.errorMessage ?: "",
                            color = colors.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

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
                            color = colors.tertiary,
                            trackColor = colors.surfaceVariant
                        )
                        Text(
                            text = "Mengunduh ${state.downloadProgressPercent}% (${AppUpdateManager.formatBytes(state.downloadedBytes)} / ${AppUpdateManager.formatBytes(state.totalBytes)})",
                            color = colors.tertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                val release = state.latestRelease
                if (state.isUpdateAvailable && release != null) {
                    Button(
                        onClick = { updateManager.downloadAndInstallLatestApk(context) },
                        enabled = !state.isDownloading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("download_and_install_apk_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.tertiary,
                            contentColor = colors.onTertiary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Unduh dan pasang APK",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                state.isDownloading -> "Mengunduh APK..."
                                state.downloadedApkPath != null && File(state.downloadedApkPath!!).exists() ->
                                    "Pasang APK v${release.cleanVersion}"
                                !release.apkDownloadUrl.isNullOrBlank() ->
                                    "Unduh & Pasang v${release.cleanVersion}"
                                else ->
                                    "Buka Rilis v${release.cleanVersion}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            updateManager.checkForUpdates(manualTrigger = true, showSystemNotification = false)
                        },
                        enabled = !state.isChecking && !state.isDownloading,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("dialog_check_update_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = if (state.isChecking) "Mengecek..." else "Cek Ulang",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { updateManager.openReleasePageInBrowser(context) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("dialog_open_github_releases_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "Rilis GitHub",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TextButton(
                    onClick = { showRepoSettings = !showRepoSettings },
                    modifier = Modifier
                        .align(Alignment.Start)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = if (showRepoSettings) "Sembunyikan Pengaturan Repo & Token"
                        else "Pengaturan Repo & Token (${state.repoSlug})",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showRepoSettings) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.background.copy(alpha = 0.7f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            label = { Text("GitHub Repo (owner/repo)", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_repo_input_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.outline,
                                focusedTextColor = colors.onSurface,
                                unfocusedTextColor = colors.onSurface
                            )
                        )

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("GitHub PAT (Opsional / Repo Privat)", style = MaterialTheme.typography.labelSmall) },
                            placeholder = { Text("ghp_... atau github_pat_...", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Ikon token",
                                    tint = if (tokenInput.isNotBlank()) colors.tertiary else colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { isTokenVisible = !isTokenVisible },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isTokenVisible) "Sembunyikan token" else "Tampilkan token",
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_token_input_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.outline,
                                focusedTextColor = colors.onSurface,
                                unfocusedTextColor = colors.onSurface
                            )
                        )

                        if (!state.testConnectionMessage.isNullOrBlank()) {
                            val isSuccess = state.testConnectionMessage?.startsWith("✅") == true
                            Surface(
                                color = if (isSuccess) colors.tertiary.copy(alpha = 0.15f) else colors.error.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSuccess) colors.tertiary.copy(alpha = 0.5f) else colors.error.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.testConnectionMessage ?: "",
                                    color = if (isSuccess) colors.tertiary else colors.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("test_github_connection_button")
                            ) {
                                Text(
                                    text = if (state.isTestingConnection) "Menguji..." else "Tes Koneksi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    updateManager.setGithubRepoSlug(repoInput)
                                    updateManager.saveGithubToken(tokenInput)
                                    updateManager.checkForUpdates(manualTrigger = true, showSystemNotification = false)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_token_and_repo_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Simpan & Cek",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (tokenInput.isNotBlank() || state.githubToken.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    tokenInput = ""
                                    updateManager.saveGithubToken("")
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_github_token_button")
                            ) {
                                Text("Hapus Token", style = MaterialTheme.typography.labelSmall, color = colors.error)
                            }
                        }
                    }
                }

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
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("skip_version_optional_button")
                        ) {
                            Text("Lewati Versi", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(
                        onClick = {
                            updateManager.dismissBannerForSession()
                            onDismiss()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("later_optional_update_button")
                    ) {
                        Text(
                            text = "Tutup",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
