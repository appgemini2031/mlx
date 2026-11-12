package com.tajasb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonGreen
import com.tajasb.ui.components.AppAdBlockerCard
import com.tajasb.ui.components.AppUpdateDialog
import com.tajasb.ui.components.AppUpdateTopBanner
import com.tajasb.ui.components.ConfirmClearButton
import com.tajasb.ui.components.ConfirmDeleteIconButton
import com.tajasb.ui.components.DeviceBoosterCard
import com.tajasb.ui.components.FeatureInfoButton
import com.tajasb.ui.components.HeaderUpdateApkChip
import com.tajasb.ui.components.SystemTabUpdateApkCard
import com.tajasb.ui.VcamViewModel
import com.tajasb.ui.components.VideoPlayerView

private fun triggerHapticTick(context: android.content.Context, durationMs: Long = 25L) {
    try {
        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    } catch (_: Throwable) {}
}

@Composable
fun MediaManagerScreen(
    viewModel: VcamViewModel,
    onPickVideoClick: () -> Unit,
    onPickAudioClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ifsManager = remember { com.tajasb.infinity.IfsManager.getInstance(context) }
    val isIfsActive by ifsManager.isIfsActive.collectAsState()
    val ifsVideoMode by ifsManager.videoMode.collectAsState()
    val ifsVtmPkg by ifsManager.vtmPackage.collectAsState()
    val ifsVtPkgs by ifsManager.vtPackages.collectAsState()
    val ifsState by ifsManager.ifsState.collectAsState()
    val ifsAudioMode by ifsManager.audioMode.collectAsState()

    val config by viewModel.config.collectAsState()
    val videoVolume by viewModel.videoVolume.collectAsState()
    val videoMetadata by viewModel.activeMediaMetadata.collectAsState()
    val videoSeekEvent by viewModel.videoSeekEvent.collectAsState()

    val currentVideo = config.currentVideo
    var detectedVideoSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val rawWidth = detectedVideoSize?.first?.takeIf { it > 0 }
        ?: currentVideo?.width?.takeIf { it > 0 }
        ?: videoMetadata?.width
        ?: 0
    val rawHeight = detectedVideoSize?.second?.takeIf { it > 0 }
        ?: currentVideo?.height?.takeIf { it > 0 }
        ?: videoMetadata?.height
        ?: 0
    val isRotated90or270 = (config.rotation == 90 || config.rotation == 270)
    val effectiveWidth = if (isRotated90or270) rawHeight else rawWidth
    val effectiveHeight = if (isRotated90or270) rawWidth else rawHeight
    val isPortrait = effectiveHeight >= effectiveWidth

    val videoAspectRatio = when {
        config.isLiveStreamSourceActive -> {
            if (config.scaleMode == com.tajasb.model.ScaleMode.CROP) 9f / 16f else (16f / 9f)
        }
        effectiveWidth > 0 && effectiveHeight > 0 -> {
            (effectiveWidth.toFloat() / effectiveHeight.toFloat()).coerceIn(0.45f, 2.2f)
        }
        else -> 9f / 16f
    }

    val playbackStepVersion by viewModel.playbackStepVersion.collectAsState()
    val standaloneMusicPlayer = remember { com.tajasb.audio.StandaloneMusicPlayer.getInstance(context) }
    val isMp3PlayingNow by standaloneMusicPlayer.isPlaying.collectAsState()

    // Saklar Buka / Tutup Preview (Default terbuka agar pengguna langsung melihat mode yang sedang berjalan)
    var isPreviewExpanded by rememberSaveable { mutableStateOf(true) }

    // Tab Navigasi Menu Utama: 0 = Video, 1 = Musik, 2 = Game (2-HP), 3 = RTMP, 4 = Sistem, 5 = Log
    val selectedTab by viewModel.selectedMainTab.collectAsState()
    val selectedGameIndex by viewModel.selectedGameIndex.collectAsState()
    var isLogFullScreenMode by rememberSaveable { mutableStateOf(false) }

    // Otomatis minimalkan preview video saat membuka Tab Game (2), Sistem (4), atau Log (5) agar ruang baca leluasa
    LaunchedEffect(selectedTab) {
        if (selectedTab == 2 || selectedTab == 4 || selectedTab == 5) {
            isPreviewExpanded = false
        } else if (selectedTab == 0) {
            isPreviewExpanded = true
        }
    }

    var videoToRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var audioToRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var videoToEditSpillTag by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var singleVideoRoleTarget by remember { mutableStateOf<com.tajasb.model.VideoItem?>(null) }
    var singleAudioRoleTarget by remember { mutableStateOf<com.tajasb.model.AudioItem?>(null) }
    var showUpdateApkDialog by rememberSaveable { mutableStateOf(false) }

    if (showUpdateApkDialog) {
        AppUpdateDialog(
            onDismiss = { showUpdateApkDialog = false }
        )
    }

    // Dialog Pengaturan Slot IFS untuk Satu Video Tertentu
    if (singleVideoRoleTarget != null) {
        val targetItem = singleVideoRoleTarget!!
        IfsSingleVideoRoleDialog(
            item = targetItem,
            ifsManager = ifsManager,
            videoMode = ifsVideoMode,
            vtmPkg = ifsVtmPkg,
            vtPkgs = ifsVtPkgs,
            onDismiss = { singleVideoRoleTarget = null }
        )
    }

    // Dialog Pengaturan Peran Audio IFS (SR vs AX) untuk Satu Audio Tertentu
    if (singleAudioRoleTarget != null) {
        val targetAudio = singleAudioRoleTarget!!
        IfsSingleAudioRoleDialog(
            audioItem = targetAudio,
            ifsManager = ifsManager,
            videoMode = ifsVideoMode,
            vtmPkg = ifsVtmPkg,
            vtPkgs = ifsVtPkgs,
            onDismiss = { singleAudioRoleTarget = null }
        )
    }

    // Dialog Ganti Nama Video
    if (videoToRename != null) {
        val (videoId, currentName) = videoToRename!!
        MediaRenameDialog(
            title = "Ubah Nama Video",
            accentColor = NeonCyan,
            initialName = currentName,
            placeholder = "Nama baru video.mp4",
            onConfirm = { newName ->
                viewModel.renameVideo(videoId, newName)
                videoToRename = null
            },
            onDismiss = { videoToRename = null }
        )
    }

    // Dialog Edit Kata Kunci Spill Video
    if (videoToEditSpillTag != null) {
        val (videoId, vName, currentTag) = videoToEditSpillTag!!
        MediaSpillTagDialog(
            videoName = vName,
            currentTag = currentTag,
            onConfirm = { newTag ->
                viewModel.setVideoSpillKeyword(videoId, newTag)
                videoToEditSpillTag = null
            },
            onDismiss = { videoToEditSpillTag = null }
        )
    }

    // Dialog Ganti Nama Audio
    if (audioToRename != null) {
        val (audioId, currentName) = audioToRename!!
        MediaRenameDialog(
            title = "Ubah Nama Audio",
            accentColor = NeonPurple,
            initialName = currentName,
            placeholder = "Nama baru audio.mp3",
            onConfirm = { newName ->
                viewModel.renameAudio(audioId, newName)
                audioToRename = null
            },
            onDismiss = { audioToRename = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Notifikasi Pembaruan APK Opsional (Muncul otomatis bila ada versi baru di GitHub Releases)
        AppUpdateTopBanner(
            onOpenUpdateDialog = { showUpdateApkDialog = true }
        )

        // -------------------------------------------------------------
        // 1. LIVE VIDEO PREVIEW & INTEGRATED PLAYBACK BAR (Minimalis & Modern)
        // Disembunyikan saat Menu Log berada dalam mode Full Layar
        // -------------------------------------------------------------
        if (!(selectedTab == 5 && isLogFullScreenMode)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val isEffectiveIfs = isIfsActive || config.isIfsMode
                val isEffectiveSpill = !isEffectiveIfs && config.isMasterSpillMode
                val activeIfsVideo = if (isEffectiveIfs && !ifsState.currentVideoId.isNullOrBlank()) {
                    config.playlist.firstOrNull { it.id == ifsState.currentVideoId }
                } else null
                val effectivePreviewVideo = activeIfsVideo ?: currentVideo
                val effectivePreviewVideoIndex = if (activeIfsVideo != null) {
                    config.playlist.indexOfFirst { it.id == activeIfsVideo.id }.takeIf { it >= 0 } ?: config.currentVideoIndex
                } else {
                    config.currentVideoIndex
                }
                val effectivePreviewVideoPath = if (config.isLiveStreamSourceActive && config.liveStreamUrl.isNotBlank()) {
                    config.liveStreamUrl
                } else {
                    activeIfsVideo?.path?.takeIf { it.isNotBlank() } ?: config.videoPath
                }
                val activeModeBadgeText = when {
                    isEffectiveIfs -> "♾️ ${ifsVideoMode.badge}•${ifsState.getActivePackageCode(ifsVideoMode)}"
                    isEffectiveSpill -> if (config.isCurrentPlayingMaster) "👑 MASTER" else "🎯 SPILL"
                    config.autoNextMode == com.tajasb.model.AutoNextMode.RANDOM -> "🔀 ACAK"
                    config.autoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP -> "🔂 LOOP"
                    else -> "🔁 URUT"
                }
                val activeModeBadgeColor = when {
                    isEffectiveIfs -> Color(0xFFE879F9)
                    isEffectiveSpill -> Color(0xFFFCD34D)
                    config.autoNextMode == com.tajasb.model.AutoNextMode.RANDOM -> Color(0xFFA5B4FC)
                    config.autoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP -> Color(0xFFE879F9)
                    else -> NeonCyan
                }

                // Header Bar Preview + Kontrol Prev / Play-Pause / Next / Buka-Tutup Sejajar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                triggerHapticTick(context, 15L)
                                isPreviewExpanded = !isPreviewExpanded
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (config.isVideoPaused) Color.Yellow else EmeraldGreen)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(activeModeBadgeColor.copy(alpha = 0.16f))
                                .border(0.6.dp, activeModeBadgeColor.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = activeModeBadgeText,
                                color = activeModeBadgeColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (effectivePreviewVideo != null) {
                                "#${effectivePreviewVideoIndex + 1} ${effectivePreviewVideo.name}"
                            } else {
                                "Preview Kamera"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Prev Video (Touch-friendly 28dp)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .border(0.6.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    triggerHapticTick(context, 25L)
                                    viewModel.handlePrevVideoClick()
                                }
                                .testTag("btn_preview_prev_video"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⏮️", fontSize = 12.sp)
                        }

                        // Play / Pause (Highlight Neon / Amber)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (config.isVideoPaused) Color(0xFF78350F).copy(alpha = 0.35f)
                                    else NeonCyan.copy(alpha = 0.16f)
                                )
                                .border(
                                    0.7.dp,
                                    if (config.isVideoPaused) Color(0xFFF59E0B) else NeonCyan.copy(alpha = 0.85f),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    triggerHapticTick(context, 30L)
                                    viewModel.togglePauseVideo()
                                }
                                .testTag("btn_preview_toggle_pause"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (config.isVideoPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (config.isVideoPaused) "Putar" else "Jeda",
                                tint = if (config.isVideoPaused) Color(0xFFFCD34D) else NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Next Video (Touch-friendly 28dp)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .border(0.6.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    triggerHapticTick(context, 25L)
                                    viewModel.handleNextVideoClick()
                                }
                                .testTag("btn_preview_next_video"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⏭️", fontSize = 12.sp)
                        }

                        // Percepat ke 3 Detik Akhir (Uji Transisi Cepat)
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF064E3B).copy(alpha = 0.65f))
                                .border(0.6.dp, Color(0xFF34D399), RoundedCornerShape(6.dp))
                                .clickable {
                                    triggerHapticTick(context, 35L)
                                    viewModel.seekVideoToLast3Seconds()
                                }
                                .padding(horizontal = 5.dp)
                                .testTag("btn_preview_seek_last_3s"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⏩ -3d",
                                color = Color(0xFF6EE7B7),
                                fontSize = 8.8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Unified Volume Controller Pill (Tidak sumpek, ramah sentuh)
                        val vidVolPct = (videoVolume * 100).toInt()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F172A))
                                .border(0.6.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        triggerHapticTick(context, 20L)
                                        val nextPct = (vidVolPct - 10).coerceIn(0, 100)
                                        viewModel.setVideoVolume(nextPct / 100f)
                                    }
                                    .testTag("btn_video_vol_minus"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("－", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Text(
                                text = "🔊$vidVolPct%",
                                fontSize = 9.sp,
                                color = if (vidVolPct > 0) Color.White else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        triggerHapticTick(context, 20L)
                                        val nextPct = (vidVolPct + 10).coerceIn(0, 100)
                                        viewModel.setVideoVolume(nextPct / 100f)
                                    }
                                    .testTag("btn_video_vol_plus"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("＋", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Tombol Update APK Cepat di Header Bar (Hanya tampil jika ada versi lebih baru)
                        HeaderUpdateApkChip(
                            onClick = {
                                triggerHapticTick(context, 20L)
                                showUpdateApkDialog = true
                            }
                        )

                        // Tombol Buka / Tutup Preview
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonCyan.copy(alpha = 0.12f))
                                .border(0.6.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .clickable {
                                    triggerHapticTick(context, 20L)
                                    isPreviewExpanded = !isPreviewExpanded
                                }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isPreviewExpanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isPreviewExpanded) "Tutup" else "Buka",
                                    color = NeonCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Badan Preview (Hanya tampil jika Dibuka)
                if (isPreviewExpanded) {
                    LaunchedEffect(isEffectiveIfs, config.audioPlaylist.size) {
                        if (isEffectiveIfs && config.audioPlaylist.isNotEmpty()) {
                            ifsManager.ensureAudioActiveInPreview()
                        }
                    }

                    val shouldLoopPreview = when {
                        isEffectiveIfs -> ifsManager.isCurrentPhaseLooping()
                        isEffectiveSpill -> config.isMasterSpillLoopingNative
                        config.isBridgeCycleMode -> false
                        else -> (!config.autoNextEnabled || config.autoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP) && config.pendingVideoIndex == null
                    }
                    val previewSessionKey = remember(
                        effectivePreviewVideoPath,
                        ifsState.phaseStepVersion,
                        playbackStepVersion
                    ) {
                        "$effectivePreviewVideoPath|ifsStep=${ifsState.phaseStepVersion}|repoStep=$playbackStepVersion"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = if (isPortrait) {
                                Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(videoAspectRatio)
                                    .clip(RoundedCornerShape(6.dp))
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(videoAspectRatio)
                                    .clip(RoundedCornerShape(6.dp))
                            }
                        ) {
                            VideoPlayerView(
                                videoPath = effectivePreviewVideoPath,
                                rotation = config.rotation,
                                isMirrored = config.isMirrored,
                                isVerticalFlipped = config.isVerticalFlipped,
                                zoomFactor = config.zoomFactor,
                                scaleMode = config.scaleMode,
                                audioEnabled = config.audioEnabled,
                                isVideoAudioMuted = ((System.currentTimeMillis() - viewModel.repository.lastRealPlaybackTime.collectAsState().value) < 3500L) ||
                                    config.isVideoAudioMuted ||
                                    (effectivePreviewVideo?.isMuted == true) ||
                                    (!config.isDualAudioMixingEnabled && isMp3PlayingNow),
                                videoVolume = videoVolume,
                                isPaused = config.isVideoPaused,
                                isLooping = shouldLoopPreview,
                                playbackSessionKey = previewSessionKey,
                                seekEvent = videoSeekEvent,
                                onProgress = { pos, dur ->
                                    viewModel.repository.reportPreviewPlaybackProgress(pos.toLong(), dur.toLong())
                                },
                                onVideoSizeChanged = { w, h ->
                                    detectedVideoSize = Pair(w, h)
                                },
                                onVideoCompleted = {
                                    val isLiveHookStreaming = (System.currentTimeMillis() - viewModel.repository.lastRealPlaybackTime.value) < 3500L
                                    if (!isLiveHookStreaming) {
                                        if (isEffectiveIfs) {
                                            ifsManager.onVideoCompleted(
                                                forceAdvanceFromBl = false,
                                                broadcastToHook = true,
                                                completedVideoPath = effectivePreviewVideoPath
                                            )
                                        } else if (
                                            isEffectiveSpill ||
                                            config.isBridgeCycleMode ||
                                            config.pendingVideoIndex != null ||
                                            (config.autoNextEnabled && config.autoNextMode != com.tajasb.model.AutoNextMode.SINGLE_LOOP)
                                        ) {
                                            viewModel.onPreviewVideoCompleted()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            StikerAndWatermarkLiveOverlay(
                                modifier = Modifier.fillMaxSize(),
                                compactScale = 0.65f
                            )
                        }

                        // Live HUD Overlay di atas sudut Preview
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xD9090D16))
                                    .border(0.5.dp, activeModeBadgeColor.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                val hudLeftText = when {
                                    isEffectiveIfs -> "♾️ ${ifsVideoMode.badge} • ${ifsState.activePackageCode} • ${ifsState.activeSubPhase.shortLabel}"
                                    isEffectiveSpill -> if (config.isCurrentPlayingMaster) "👑 Master Loop" else "🎯 Spill 1x"
                                    config.autoNextMode == com.tajasb.model.AutoNextMode.RANDOM -> "🔀 Acak #${config.currentVideoIndex + 1}/${config.playlist.size.coerceAtLeast(1)}"
                                    config.autoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP -> "🔂 Loop #${config.currentVideoIndex + 1}"
                                    else -> "🔁 Urut #${config.currentVideoIndex + 1}/${config.playlist.size.coerceAtLeast(1)}"
                                }
                                Text(
                                    text = hudLeftText,
                                    color = activeModeBadgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isEffectiveIfs || ifsAudioMode == com.tajasb.infinity.IfsAudioMode.SV4_VS || config.audioPlaylist.isNotEmpty()) {
                                val sv4AudioActive = ifsAudioMode == com.tajasb.infinity.IfsAudioMode.SV4_VS
                                val axActive = ifsState.isPlayingTriggeredAx
                                val srPlaying = ifsState.isSrPlaying
                                val audioBadgeColor = when {
                                    sv4AudioActive -> Color(0xFFE879F9)
                                    axActive -> Color(0xFFFCD34D)
                                    ifsAudioMode == com.tajasb.infinity.IfsAudioMode.SEQUENTIAL -> EmeraldGreen
                                    ifsAudioMode == com.tajasb.infinity.IfsAudioMode.SINGLE_LOOP -> Color(0xFFF59E0B)
                                    srPlaying -> Color(0xFF34D399)
                                    else -> NeonCyan
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xD9090D16))
                                        .border(0.5.dp, audioBadgeColor.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                        .clickable {
                                            val nextMode = ifsManager.cycleStandardMp3AudioMode()
                                            viewModel.showUserMessage("🎵 Mode Audio MP3: ${nextMode.title}")
                                        }
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = when {
                                            sv4AudioActive -> "🧬 VSA${ifsState.sv4AudioCurrentVsNumber} Aktif"
                                            axActive -> "🔊 ${ifsState.activeTriggerAxLabel ?: "AX"}"
                                            ifsAudioMode == com.tajasb.infinity.IfsAudioMode.SEQUENTIAL -> "🔁 MP3 Urut"
                                            ifsAudioMode == com.tajasb.infinity.IfsAudioMode.SINGLE_LOOP -> "🔂 Loop 1x"
                                            srPlaying -> "🔀 SR Aktif"
                                            else -> "🔀 Acak/SR"
                                        },
                                        color = audioBadgeColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 1B. DETEKTOR & PERINGATAN REAL-TIME ANTI-PL: HARDWARE SUARA & SINYAL
        // -------------------------------------------------------------
        val isMp3MutedNow by standaloneMusicPlayer.isMuted.collectAsState()
        val mp3VolNow by standaloneMusicPlayer.volume.collectAsState()
        val isAudioStalledNow by standaloneMusicPlayer.isAudioStalledOrError.collectAsState()
        val audioErrorReasonNow by standaloneMusicPlayer.lastAudioErrorReason.collectAsState()

        var audioHealth by remember {
            mutableStateOf(
                com.tajasb.engine.SystemDiagnosticHelper.checkAudioHealth(
                    context = context,
                    hasActiveVideo = config.playlist.isNotEmpty(),
                    isVideoPaused = config.isVideoPaused,
                    isVideoAudioMuted = config.isVideoAudioMuted,
                    videoVolume = videoVolume,
                    hasMp3Playlist = config.audioPlaylist.isNotEmpty(),
                    isMp3Playing = isMp3PlayingNow,
                    isMp3Muted = isMp3MutedNow,
                    mp3Volume = mp3VolNow,
                    isAudioPlayerStalled = isAudioStalledNow,
                    audioPlayerErrorReason = audioErrorReasonNow
                )
            )
        }

        LaunchedEffect(
            config.playlist.size,
            config.isVideoPaused,
            config.isVideoAudioMuted,
            videoVolume,
            config.audioPlaylist.size,
            isMp3PlayingNow,
            isMp3MutedNow,
            mp3VolNow,
            isAudioStalledNow,
            audioErrorReasonNow
        ) {
            while (true) {
                audioHealth = com.tajasb.engine.SystemDiagnosticHelper.checkAudioHealth(
                    context = context,
                    hasActiveVideo = config.playlist.isNotEmpty(),
                    isVideoPaused = config.isVideoPaused,
                    isVideoAudioMuted = config.isVideoAudioMuted,
                    videoVolume = videoVolume,
                    hasMp3Playlist = config.audioPlaylist.isNotEmpty(),
                    isMp3Playing = isMp3PlayingNow,
                    isMp3Muted = isMp3MutedNow,
                    mp3Volume = mp3VolNow,
                    isAudioPlayerStalled = isAudioStalledNow,
                    audioPlayerErrorReason = audioErrorReasonNow
                )
                kotlinx.coroutines.delay(1200L)
            }
        }

        if (audioHealth.hasIssue) {
            val isCrit = audioHealth.severity == "CRITICAL"
            val alertColor = if (isCrit) Color(0xFFEF4444) else Color(0xFFF59E0B)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .testTag("audio_hardware_warning_banner"),
                shape = RoundedCornerShape(7.dp),
                colors = CardDefaults.cardColors(
                    containerColor = alertColor.copy(alpha = 0.14f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, alertColor.copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = audioHealth.headlineText,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isCrit) Color(0xFFFCA5A5) else Color(0xFFFDE68A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = audioHealth.detailText,
                            fontSize = 8.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 10.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(alertColor)
                            .clickable {
                                val msg = com.tajasb.engine.SystemDiagnosticHelper.recoverAudioHardwareAndStream(
                                    context = context,
                                    repository = com.tajasb.data.VcamRepository.getInstance(context)
                                )
                                audioHealth = com.tajasb.engine.SystemDiagnosticHelper.checkAudioHealth(
                                    context = context,
                                    hasActiveVideo = config.playlist.isNotEmpty(),
                                    isVideoPaused = config.isVideoPaused,
                                    isVideoAudioMuted = config.isVideoAudioMuted,
                                    videoVolume = videoVolume,
                                    hasMp3Playlist = config.audioPlaylist.isNotEmpty(),
                                    isMp3Playing = isMp3PlayingNow,
                                    isMp3Muted = isMp3MutedNow,
                                    mp3Volume = mp3VolNow,
                                    isAudioPlayerStalled = false,
                                    audioPlayerErrorReason = null
                                )
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                            .testTag("recover_audio_hardware_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔊 Pulihkan",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        }

        // -------------------------------------------------------------
        // 2. TAB NAVIGASI MODERN MINIMALIS (Segmented Studio Bar)
        // -------------------------------------------------------------
        val rtmpEngine = remember { com.tajasb.rtmp.RtmpStreamEngine.getInstance(context) }
        val rtmpTelemetry by rtmpEngine.telemetry.collectAsState()
        val liveLogCount by com.tajasb.debug.VcamDebugLogger.logsFlow.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface)
                .border(0.8.dp, DarkCardBorder, RoundedCornerShape(10.dp))
                .padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val rtmpBadgeText = if (rtmpTelemetry.isBroadcasting) "LIVE" else null
            val logBadgeText = if (liveLogCount.isNotEmpty()) liveLogCount.size.toString() else null
            val tabs = listOf(
                Triple("🎬 Video", config.playlist.size.toString(), "main_tab_video"),
                Triple("🎵 Musik", config.audioPlaylist.size.toString(), "main_tab_music"),
                Triple("🎮 Game", null, "main_tab_game"),
                Triple("📡 RTMP", rtmpBadgeText, "main_tab_rtmp"),
                Triple("⚙️ Sistem", null, "main_tab_system"),
                Triple("📋 Log", logBadgeText, "main_tab_log")
            )

            tabs.chunked(3).forEachIndexed { rowIdx, rowTabs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    rowTabs.forEachIndexed { colIdx, (title, badgeText, tag) ->
                        val index = rowIdx * 3 + colIdx
                        val isSelected = selectedTab == index
                        val isRtmpLiveBadge = index == 3 && rtmpTelemetry.isBroadcasting
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (isSelected) NeonCyan.copy(alpha = 0.14f) else Color.Transparent
                                )
                                .border(
                                    width = if (isSelected) 0.8.dp else 0.dp,
                                    color = if (isSelected) NeonCyan.copy(alpha = 0.55f) else Color.Transparent,
                                    shape = RoundedCornerShape(7.dp)
                                )
                                .clickable { viewModel.setSelectedMainTab(index) }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                .testTag(tag),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                                if (badgeText != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                when {
                                                    isRtmpLiveBadge -> EmeraldGreen.copy(alpha = 0.25f)
                                                    isSelected -> NeonCyan.copy(alpha = 0.22f)
                                                    else -> DarkSurfaceVariant
                                                }
                                            )
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when {
                                                isRtmpLiveBadge -> EmeraldGreen
                                                isSelected -> NeonCyan
                                                else -> Color(0xFF94A3B8)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // -------------------------------------------------------------
        // 3. KONTEN BERDASARKAN TAB TERPILIH
        // -------------------------------------------------------------
        when (selectedTab.coerceIn(0, 5)) {
            0 -> VideoTabContent(
                viewModel = viewModel,
                config = config,
                onPickVideoClick = onPickVideoClick,
                onEditSpillTag = { id, name, tag -> videoToEditSpillTag = Triple(id, name, tag) },
                onRenameVideo = { id, name -> videoToRename = Pair(id, name) },
                onOpenSingleRoleDialog = { singleVideoRoleTarget = it }
            )
            1 -> AudioTabContent(
                viewModel = viewModel,
                config = config,
                onPickAudioClick = onPickAudioClick,
                onRenameAudio = { id, name -> audioToRename = Pair(id, name) },
                onOpenAudioIfsRole = { singleAudioRoleTarget = it }
            )
            2 -> TugOfWarGameHubTabContent(
                onLaunchFullScreenArena = {
                    viewModel.openGameFromIntent(0)
                    com.tajasb.game.InteractiveTugOfWarEngine.getInstance(context).setArenaDisplayActive(true)
                },
                selectedGameIndex = selectedGameIndex,
                onSelectGameIndex = { viewModel.setSelectedGameIndex(it) },
                modifier = Modifier.weight(1f)
            )
            3 -> RtmpBroadcasterTabContent(
                viewModel = viewModel,
                vcamConfig = config
            )
            4 -> SystemTabContent(
                viewModel = viewModel,
                config = config,
                onOpenUpdateDialog = { showUpdateApkDialog = true },
                onOpenLogTab = { viewModel.setSelectedMainTab(5) }
            )
            5 -> LogCenterTabContent(
                config = config,
                isFullScreenMode = isLogFullScreenMode,
                onToggleFullScreenMode = { isLogFullScreenMode = !isLogFullScreenMode },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * TAB 1: KONTEN VIDEO & PILIHAN SIKLUS (Compact, Sejajar, Penjelasan di `!`)
 */
@Composable
private fun VideoTabContent(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig,
    onPickVideoClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onEditSpillTag: (String, String, String) -> Unit,
    onRenameVideo: (String, String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenSingleRoleDialog: (com.tajasb.model.VideoItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // BARIS AKSI UPLOAD & SINKRONISASI BERKAS VIDEO
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPickVideoClick,
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("add_videos_button"),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color(0xFF090D16)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Upload Video",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp
                    )
                }

                // Kosongkan Playlist Video
                if (config.playlist.isNotEmpty()) {
                    ConfirmClearButton(
                        onClear = { viewModel.removeAllVideos() },
                        text = "Kosongkan",
                        height = 30.dp,
                        testTag = "btn_clear_all_videos"
                    )
                }

                // Sinkron ke DCIM
                OutlinedButton(
                    onClick = { viewModel.syncFilesToDcim() },
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("btn_sync_dcim"),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, NeonCyan.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sinkron", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                }

                // Pintasan Buka Pengaturan Video di Floating Control
                OutlinedButton(
                    onClick = {
                        if (android.provider.Settings.canDrawOverlays(context)) {
                            viewModel.toggleFloatingWidget(true)
                            viewModel.showUserMessage("🎛️ Floating Control aktif! Semua pengaturan mode, siklus & slot video ada di tab Video Floating.")
                        } else {
                            viewModel.showUserMessage("⚠️ Aktifkan izin Overlay terlebih dahulu untuk membuka Floating Control.")
                        }
                    },
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("btn_open_floating_video_settings"),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE879F9)),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFD946EF).copy(alpha = 0.55f))
                ) {
                    Text("🎛️ Atur di Floating", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. STUDIO FOTO + EFEK ANIMASI LAYOUT (RENDER KE MP4 VCAM & SIMPAN)
            item {
                com.tajasb.ui.components.PhotoStudioCard(
                    onUserMessage = { msg -> viewModel.showUserMessage(msg) }
                )
            }

            // KARTU LIVE STREAM WEB RELAY (DEVICE 1 TERMUX -> DEVICE 2 VCAM TIKTOK LIVE)
            item {
                val isLiveActive = config.isLiveStreamSourceActive
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_live_stream_relay"),
                    shape = RoundedCornerShape(9.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLiveActive) Color(0xFF0F291E) else DarkSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        0.9.dp,
                        if (isLiveActive) EmeraldGreen else DarkCardBorder
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp)
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
                                    text = "🕋 Live Stream Web Relay",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLiveActive) EmeraldGreen else Color(0xFFF1F5F9)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isLiveActive) EmeraldGreen.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (isLiveActive) "🟢 RELAY AKTIF" else "⚪ STANDBY (2 DEVICE)",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLiveActive) EmeraldGreen else Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Switch(
                                checked = isLiveActive,
                                onCheckedChange = { viewModel.toggleLiveStreamSource() },
                                modifier = Modifier
                                    .scale(0.75f)
                                    .testTag("switch_live_stream_relay"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = EmeraldGreen,
                                    checkedTrackColor = EmeraldGreen.copy(alpha = 0.35f),
                                    uncheckedThumbColor = Color(0xFF64748B),
                                    uncheckedTrackColor = DarkSurfaceVariant
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (isLiveActive) {
                                "🔗 Stream Web: ${config.liveStreamTitle}\n📡 URL: ${config.liveStreamUrl}\n💡 Feed stream disalurkan langsung ke Kamera Virtual TikTok."
                            } else {
                                "💡 Tangkap live stream (contoh: Makkah Live) di Device 1 (Termux CLI), lalu aktifkan saklar ini untuk menyalurkan videonya ke Kamera Virtual Device 2."
                            },
                            fontSize = 8.5.sp,
                            color = if (isLiveActive) Color(0xFFD1FAE5) else Color(0xFF94A3B8),
                            lineHeight = 11.sp
                        )

                        if (isLiveActive) {
                            Spacer(modifier = Modifier.height(6.dp))
                            // Baris Tombol Saluran Langsung (Stream 1, Stream 2, Stream 3)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.setLiveStreamSource(
                                            active = true,
                                            url = "http://m.live.net.sa:1935/live/quran/playlist.m3u8",
                                            title = "Stream 1 - Saudi Quran TV (Masjidil Haram)"
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .testTag("btn_select_stream_1"),
                                    shape = RoundedCornerShape(5.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (config.liveStreamUrl.contains("quran/playlist")) EmeraldGreen else DarkSurfaceVariant,
                                        contentColor = if (config.liveStreamUrl.contains("quran/playlist")) Color.Black else Color.White
                                    )
                                ) {
                                    Text("🕋 Stream 1", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        viewModel.setLiveStreamSource(
                                            active = true,
                                            url = "https://cdn-globecast.akamaized.net/live/eds/saudi_quran/hls_roku/index.m3u8",
                                            title = "Stream 2 - Saudi Quran TV (Akamai Backup)"
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .testTag("btn_select_stream_2"),
                                    shape = RoundedCornerShape(5.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (config.liveStreamUrl.contains("akamaized")) EmeraldGreen else DarkSurfaceVariant,
                                        contentColor = if (config.liveStreamUrl.contains("akamaized")) Color.Black else Color.White
                                    )
                                ) {
                                    Text("🕋 Stream 2", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        viewModel.setLiveStreamSource(
                                            active = true,
                                            url = "http://m.live.net.sa:1935/live/sunnah/playlist.m3u8",
                                            title = "Stream 3 - Saudi Sunnah TV (Masjid an-Nabawi)"
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .testTag("btn_select_stream_3"),
                                    shape = RoundedCornerShape(5.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (config.liveStreamUrl.contains("sunnah")) EmeraldGreen else DarkSurfaceVariant,
                                        contentColor = if (config.liveStreamUrl.contains("sunnah")) Color.Black else Color.White
                                    )
                                ) {
                                    Text("🕌 Stream 3", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setLiveStreamSource(
                                            active = true,
                                            url = "http://m.live.net.sa:1935/live/quran/playlist.m3u8",
                                            title = "Makkah Live 24 Jam - Masjidil Haram"
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(26.dp)
                                        .testTag("btn_reset_makkah_stream"),
                                    shape = RoundedCornerShape(5.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                                    border = androidx.compose.foundation.BorderStroke(0.6.dp, EmeraldGreen.copy(alpha = 0.6f))
                                ) {
                                    Text("🕋 Default Makkah", fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.toggleLiveStreamSource()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(26.dp)
                                        .testTag("btn_switch_local_video"),
                                    shape = RoundedCornerShape(5.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                    border = androidx.compose.foundation.BorderStroke(0.6.dp, NeonCyan.copy(alpha = 0.6f))
                                ) {
                                    Text("🎬 Beralih ke Video Lokal", fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // ⚡ Live Event Broker & Saklar Event Dipindah ke Floating Menu
            item {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E1B4B).copy(alpha = 0.5f))
                        .border(0.6.dp, Color(0xFF6366F1).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⚡ Live Event Broker & Saklar dipindah ke Floating (Tab 4 ➔ ⚡ EVENT)",
                        fontSize = 8.5.sp,
                        color = Color(0xFFA5B4FC)
                    )
                }
            }

            // Info Singkat Bahwa Semua Pengaturan Video Dipindah ke Floating
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B).copy(alpha = 0.55f)),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF818CF8).copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "💡 Semua pengaturan Mode, Siklus, Spill, IFS (SV1–SV4) & Slot Video kini diatur penuh di Floating Control.",
                            color = Color(0xFFE0E7FF),
                            fontSize = 8.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Alert Banner: Izin Akses Semua File (Compact 1 Row jika belum diaktifkan di Android 11+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !viewModel.isAllFilesPermissionGranted()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openAllFilesSettings() },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.45f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Izin Akses Semua File Diperlukan",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                FeatureInfoButton(
                                    title = "Izin Akses Berkas",
                                    description = "Aktifkan 'Akses Semua File' agar sinkronisasi video/audio ke folder DCIM kamera virtual berjalan lancar tanpa pesan 'Akses Ditolak'.",
                                    accentColor = Color(0xFFF87171)
                                )
                            }
                            Text(
                                text = "Izinkan ➔",
                                color = Color(0xFFFCA5A5),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // DAFTAR BERKAS VIDEO YANG DI-UPLOAD (Khusus Manajemen File: Ganti Nama, Urutan, Hapus)
            val uploadedPlaylist = config.playlist.filter {
                !it.id.startsWith("vs_rolling_") && !it.path.contains("vs_rolling_")
            }

            if (uploadedPlaylist.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📁 Belum ada video • Ketuk 'Upload Video' di atas",
                            color = Color.Gray,
                            fontSize = 10.5.sp
                        )
                    }
                }
            } else {
                items(config.playlist.size, key = { i -> "${config.playlist[i].id}_$i" }) { index ->
                    val item = config.playlist[index]
                    val isRollingVsItem = item.id.startsWith("vs_rolling_") || item.path.contains("vs_rolling_")
                    if (isRollingVsItem) {
                        return@items
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp)),
                        shape = RoundedCornerShape(9.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(DarkSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.5.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Resolusi: ${item.width}x${item.height}",
                                        color = Color.Gray,
                                        fontSize = 8.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onRenameVideo(item.id, item.name) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ganti Nama",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.moveVideoUp(index) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Naik",
                                        tint = if (index > 0) NeonCyan else Color.DarkGray,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.moveVideoDown(index) },
                                    enabled = index < config.playlist.size - 1,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Turun",
                                        tint = if (index < config.playlist.size - 1) NeonCyan else Color.DarkGray,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                ConfirmDeleteIconButton(
                                    onDelete = { viewModel.removeVideo(item.id) },
                                    contentDescription = "Hapus Video",
                                    defaultTint = Color(0xFFEF4444),
                                    iconSize = 14.dp,
                                    testTag = "btn_delete_video_${item.id}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TAB 2: KONTEN AUDIO & BGM (Murni Upload & Playlist - Persis Menu Video)
 */
@Composable
private fun AudioTabContent(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig,
    onPickAudioClick: () -> Unit,
    onRenameAudio: (String, String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenAudioIfsRole: (com.tajasb.model.AudioItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(config.audioPlaylist.size) {
        viewModel.refreshAudioBackupState()
    }

    // Launcher khusus untuk memilih file .ZIP berisi kumpulan audio (atau multi file)
    val zipAudioPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.onAudiosSelected(listOf(uri))
        }
    }

    fun formatAudioTime(ms: Long): String {
        if (ms <= 0L) return ""
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val hr = totalSec / 3600
        val min = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return if (hr > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", hr, min, sec)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", min, sec)
        }
    }

    fun formatAudioSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // BARIS AKSI UPLOAD & SINKRONISASI BERKAS AUDIO (Persis Menu Video)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPickAudioClick,
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("add_audios_button"),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color(0xFF090D16)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Tambah Audio",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp
                    )
                }

                // Kosongkan Playlist Audio
                if (config.audioPlaylist.isNotEmpty()) {
                    ConfirmClearButton(
                        onClear = { viewModel.removeAudio() },
                        text = "Kosongkan",
                        height = 30.dp,
                        testTag = "btn_clear_all_audios"
                    )
                }

                // Upload ZIP Audio
                OutlinedButton(
                    onClick = {
                        try {
                            zipAudioPickerLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream"
                                )
                            )
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Gagal membuka file ZIP: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("btn_upload_audio_zip"),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, NeonCyan.copy(alpha = 0.45f))
                ) {
                    Text("📦 Upload ZIP", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                }

                // Pintasan Buka Pengaturan Audio di Floating Control
                OutlinedButton(
                    onClick = {
                        if (android.provider.Settings.canDrawOverlays(context)) {
                            viewModel.toggleFloatingWidget(true)
                            viewModel.showUserMessage("🎛️ Floating Control aktif! Semua pemutar audio, mode putar, efek suara, multi-layer & edit suara realtime ada di tab Musik Floating.")
                        } else {
                            viewModel.showUserMessage("⚠️ Aktifkan izin Overlay terlebih dahulu untuk membuka Floating Control.")
                        }
                    },
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("btn_open_floating_audio_settings"),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE879F9)),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFD946EF).copy(alpha = 0.55f))
                ) {
                    Text("🎛️ Atur di Floating", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 0. AUTO-DOWNLOAD AUDIO AL-QURAN (OPSIONAL - TETAP DI MENU UTAMA BERSAMA UPLOAD)
            if (config.enableQuranAudioModule) {
                item {
                    com.tajasb.ui.components.QuranAudioDownloaderCard(
                        onUserMessage = { msg -> viewModel.showUserMessage(msg) }
                    )
                }
            }

            // Info Singkat Bahwa Semua Pengaturan Audio & Live Realtime Editor Dipindah ke Floating
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B).copy(alpha = 0.55f)),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF818CF8).copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "💡 Semua pemutar audio, mode putar (Urut/Acak/Loop/SV4), efek suara, volume, multi-layer & Edit Audio Realtime kini diatur penuh di Floating Control.",
                            color = Color(0xFFE0E7FF),
                            fontSize = 8.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (config.audioPlaylist.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada audio yang diunggah. Klik 'Tambah Audio' untuk menambahkan musik.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                items(config.audioPlaylist.size, key = { i -> "${config.audioPlaylist[i].id}_$i" }) { index ->
                    val item = config.audioPlaylist[index]
                    val isRollingVsItem = item.id.startsWith("vs_audio_rolling_") || item.path.contains("vs_audio_rolling_")
                    if (isRollingVsItem) {
                        return@items
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp)),
                        shape = RoundedCornerShape(9.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(DarkSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.5.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val durationStr = formatAudioTime(item.durationMs)
                                    val sizeStr = formatAudioSize(item.sizeBytes)
                                    val metaText = when {
                                        durationStr.isNotBlank() && sizeStr.isNotBlank() -> "Durasi: $durationStr • $sizeStr"
                                        durationStr.isNotBlank() -> "Durasi: $durationStr"
                                        sizeStr.isNotBlank() -> "Ukuran: $sizeStr"
                                        else -> "Berkas Audio"
                                    }
                                    Text(
                                        text = metaText,
                                        color = Color.Gray,
                                        fontSize = 8.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onRenameAudio(item.id, item.name) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ganti Nama",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.moveAudioUp(index) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Naik",
                                        tint = if (index > 0) NeonCyan else Color.DarkGray,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.moveAudioDown(index) },
                                    enabled = index < config.audioPlaylist.size - 1,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Turun",
                                        tint = if (index < config.audioPlaylist.size - 1) NeonCyan else Color.DarkGray,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                ConfirmDeleteIconButton(
                                    onDelete = { viewModel.removeAudioItem(item.id) },
                                    contentDescription = "Hapus Audio",
                                    defaultTint = Color(0xFFEF4444),
                                    iconSize = 14.dp,
                                    testTag = "btn_delete_audio_${item.id}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TAB 3: SISTEM, TARGET APPS, BOOSTER & DIAGNOSTIK (Compact & Sejajar)
 */
@Composable
private fun SystemTabContent(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig,
    onOpenUpdateDialog: () -> Unit = {},
    onOpenLogTab: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 76.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // 0. PEMBARUAN APK & NOTIFIKASI VERSI BARU (Opsional Update)
        item {
            SystemTabUpdateApkCard(onOpenUpdateDialog = onOpenUpdateDialog)
        }

        // 1. APLIKASI TARGET (1 Baris Sejajar)
        item {
            TargetAppsCard(viewModel = viewModel)
        }

        // 2. PEMBLOKIR DATA & IKLAN APK (Anti-Adware Live)
        item {
            AppAdBlockerCard(viewModel = viewModel)
        }

        // 3. DEVICE BOOSTER & NETWORK OPTIMIZER (Minimalis + ! Info)
        item {
            DeviceBoosterCard(viewModel = viewModel)
        }

        // 4. PENGATURAN KAMERA & JENDELA MELAYANG (Minimalis + ! Info)
        item {
            CameraSettingsSection(viewModel = viewModel)
        }

        // 5. PINTASAN KE MENU LOG KHUSUS (Di Sebelah Setting)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF083344))
                    .border(0.8.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .clickable { onOpenLogTab() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("btn_open_dedicated_log_tab"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📋 Buka Menu Log & Diagnostik Lengkap ➔",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// -------------------------------------------------------------
// DIALOG-DIALOG HELPER (Tetap Utuh 100% Fungsinya)
// -------------------------------------------------------------

@Composable
private fun MediaRenameDialog(
    title: String,
    accentColor: Color,
    initialName: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember(initialName) {
        mutableStateOf(
            TextFieldValue(
                text = initialName,
                selection = TextRange(0, initialName.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = title,
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Nama file baru di penyimpanan:",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    singleLine = true,
                    placeholder = { Text(placeholder, fontSize = 11.sp, color = Color.Gray) },
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val text = textFieldValue.text.trim()
                            if (text.isNotBlank()) onConfirm(text) else onDismiss()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
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
                    val text = textFieldValue.text.trim()
                    if (text.isNotBlank()) onConfirm(text) else onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = if (accentColor == NeonCyan) Color.Black else Color.White
                )
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color.Gray, fontSize = 11.sp)
            }
        }
    )
}

@Composable
private fun MediaSpillTagDialog(
    videoName: String,
    currentTag: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tagText by remember(currentTag) { mutableStateOf(currentTag) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val suggestions = listOf("hitam", "merah", "putih", "navy", "maroon", "kuning", "pink", "hijau", "biru", "abu", "cokelat", "size s", "size m", "size l", "size xl")

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏷️", fontSize = 15.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Kata Kunci Spill Produk",
                    color = Color(0xFFFCD34D),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Video: $videoName",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Ketik pemicu komentar penonton (contoh: hitam, gamis).",
                    color = Color.Gray,
                    fontSize = 9.sp
                )
                OutlinedTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    singleLine = true,
                    placeholder = { Text("Contoh: hitam atau merah", fontSize = 11.sp, color = Color.Gray) },
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(tagText.trim()) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Text("Pilihan Cepat:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(suggestions.size) { i ->
                        val chip = suggestions[i]
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (tagText.equals(chip, ignoreCase = true)) Color(0xFF78350F) else Color(0xFF1E293B))
                                .clickable { tagText = chip }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = chip,
                                fontSize = 8.5.sp,
                                color = if (tagText.equals(chip, ignoreCase = true)) Color(0xFFFDE68A) else Color.LightGray
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tagText.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color.Black
                )
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            Row {
                if (currentTag.isNotEmpty()) {
                    TextButton(onClick = { onConfirm("") }) {
                        Text("Hapus", color = Color(0xFFEF4444), fontSize = 11.sp)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Batal", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    )
}

@Composable
private fun IfsSingleVideoRoleDialog(
    item: com.tajasb.model.VideoItem,
    ifsManager: com.tajasb.infinity.IfsManager,
    videoMode: com.tajasb.infinity.IfsVideoMode,
    vtmPkg: com.tajasb.infinity.IfsVtPackage,
    vtPkgs: List<com.tajasb.infinity.IfsVtPackage>,
    onDismiss: () -> Unit
) {
    val sv3VlVideoId by ifsManager.sv3VlVideoId.collectAsState()
    val sv3VbVideoIds by ifsManager.sv3VbVideoIds.collectAsState()
    val sv3MinLoops by ifsManager.sv3VlMinLoops.collectAsState()
    val sv3MaxLoops by ifsManager.sv3VlMaxLoops.collectAsState()
    val sv4LVideoIds by ifsManager.sv4LVideoIds.collectAsState()
    val sv4MinLoops by ifsManager.sv4MinLoops.collectAsState()
    val sv4MaxLoops by ifsManager.sv4MaxLoops.collectAsState()

    val visiblePackages = if (videoMode == com.tajasb.infinity.IfsVideoMode.SV1) {
        listOf(vtmPkg) + vtPkgs
    } else {
        vtPkgs
    }
    val currentRoles = remember(item.id, videoMode, vtmPkg, vtPkgs, sv3VlVideoId, sv3VbVideoIds, sv4LVideoIds, sv4MinLoops, sv4MaxLoops) {
        ifsManager.getVideoSlotRoles(item.id, videoMode)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Column {
                Text(
                    text = "♾️ Atur Slot IFS (${videoMode.name})",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "💡 Ketuk slot untuk Pasang, ketuk lagi slot yang aktif (✓) untuk Lepas",
                    color = Color.LightGray,
                    fontSize = 8.5.sp
                )
            }
        },
        text = {
            if (videoMode == com.tajasb.infinity.IfsVideoMode.SV4) {
                val lIdx = sv4LVideoIds.indexOf(item.id)
                val isAssignedL = lIdx >= 0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isAssignedL) EmeraldGreen else Color(0xFF334155)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "🧬 Grup Variasi Susunan (L1, L2, L3, L4... • Loop ${sv4MinLoops}..${sv4MaxLoops}x)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                        Text(
                            text = "Semua video L diacak urutannya + diacak loop ${sv4MinLoops}..${sv4MaxLoops}x untuk menghasilkan variasi VS1, VS2, VS3 dst. yang digabung otomatis di background.",
                            fontSize = 8.5.sp,
                            color = Color.LightGray
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isAssignedL) EmeraldGreen.copy(alpha = 0.25f) else Color(0xFF1E293B))
                                .border(
                                    0.8.dp,
                                    if (isAssignedL) EmeraldGreen else NeonCyan.copy(alpha = 0.6f),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    ifsManager.toggleSv4LVideo(item.id)
                                }
                                .padding(vertical = 7.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isAssignedL) "✓ Terdaftar sebagai L${lIdx + 1} (Ketuk untuk Lepas ✕)" else "+ Masukkan ke Daftar Variasi Susunan (L${sv4LVideoIds.size + 1})",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAssignedL) EmeraldGreen else NeonCyan
                            )
                        }
                    }
                }
            } else if (videoMode == com.tajasb.infinity.IfsVideoMode.SV3) {
                val isAssignedVl = (sv3VlVideoId == item.id)
                val vbIdx = sv3VbVideoIds.indexOf(item.id)
                val isAssignedVb = vbIdx >= 0

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Kartu 1: Video VL (1 Video Utama Looping Acak)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            if (isAssignedVl) NeonCyan else Color(0xFF334155)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "👑 Video VL (1 Video Looping Acak $sv3MinLoops..$sv3MaxLoops Putaran)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Text(
                                text = "Hanya 1 video yang menjadi VL utama. Setelah selesai putaran acak, rotasi otomatis ke Grup VB.",
                                fontSize = 8.5.sp,
                                color = Color.LightGray
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isAssignedVl) EmeraldGreen.copy(alpha = 0.25f) else Color(0xFF1E293B))
                                    .border(
                                        0.8.dp,
                                        if (isAssignedVl) EmeraldGreen else NeonCyan.copy(alpha = 0.6f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        ifsManager.setSv3VlVideo(if (isAssignedVl) null else item.id)
                                    }
                                    .padding(vertical = 7.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isAssignedVl) "✓ Terpasang sebagai Video VL (Ketuk untuk Lepas ✕)" else "👑 Jadikan Video VL Utama",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAssignedVl) EmeraldGreen else NeonCyan
                                )
                            }
                        }
                    }

                    // Kartu 2: Grup Video Bridge (VB1, VB2, dst.)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            if (isAssignedVb) Color(0xFFE879F9) else Color(0xFF334155)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "🌉 Grup Video Bridge (VB1, VB2, dst. • Total: ${sv3VbVideoIds.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE879F9)
                            )
                            Text(
                                text = "Grup multi-video Bridge yang diacak tanpa pengulangan (masing-masing kebagian 1x per siklus).",
                                fontSize = 8.5.sp,
                                color = Color.LightGray
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isAssignedVb) EmeraldGreen.copy(alpha = 0.25f) else Color(0xFF1E293B))
                                    .border(
                                        0.8.dp,
                                        if (isAssignedVb) EmeraldGreen else Color(0xFFE879F9).copy(alpha = 0.6f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        ifsManager.toggleSv3VbVideo(item.id)
                                    }
                                    .padding(vertical = 7.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isAssignedVb) "✓ Terdaftar sebagai VB${vbIdx + 1} (Ketuk untuk Lepas ✕)" else "+ Masukkan ke Grup VB (VB${sv3VbVideoIds.size + 1})",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAssignedVb) EmeraldGreen else Color(0xFFE879F9)
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(visiblePackages.size) { idx ->
                        val pkg = visiblePackages[idx]
                        val shortCode = pkg.shortCode
                        val requiresThree = pkg.requiresThreeVideos(videoMode)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF334155))
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val pCode = pkg.getShortCode(videoMode)
                                Text(
                                    text = if (pkg.isVtm) "👑 ABA (3 Video ABB+BL+BBT)" else if (videoMode == com.tajasb.infinity.IfsVideoMode.SV2) "👑 $pCode (ABA)" else "🎬 $pCode",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pkg.isVtm) Color(0xFFFCD34D) else NeonCyan
                                )

                                if (requiresThree) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val slots = listOf(
                                            com.tajasb.infinity.IfsSubPhase.ABB to "A (ABB)",
                                            com.tajasb.infinity.IfsSubPhase.BL to "B (BL Loop)",
                                            com.tajasb.infinity.IfsSubPhase.BBT to "A (BBT)"
                                        )
                                        slots.forEach { (phase, label) ->
                                            val isAssigned = when (phase) {
                                                com.tajasb.infinity.IfsSubPhase.ABB -> pkg.abbVideoId == item.id
                                                com.tajasb.infinity.IfsSubPhase.BL -> pkg.blVideoId == item.id
                                                com.tajasb.infinity.IfsSubPhase.BBT -> pkg.bbtVideoId == item.id
                                                else -> false
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (isAssigned) EmeraldGreen.copy(alpha = 0.25f)
                                                        else Color(0xFF1E293B)
                                                    )
                                                    .border(
                                                        0.8.dp,
                                                        if (isAssigned) EmeraldGreen else Color(0xFF475569),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .clickable {
                                                        ifsManager.setSlotVideo(
                                                            pkg.vtNumber,
                                                            phase,
                                                            if (isAssigned) null else item.id
                                                        )
                                                    }
                                                    .padding(vertical = 6.dp, horizontal = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (isAssigned) "✓ $label ✕" else label,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isAssigned) EmeraldGreen else Color.White,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    val isAssigned = (pkg.blVideoId == item.id)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isAssigned) EmeraldGreen.copy(alpha = 0.25f)
                                                else Color(0xFF1E293B)
                                            )
                                            .border(
                                                0.8.dp,
                                                if (isAssigned) EmeraldGreen else NeonCyan.copy(alpha = 0.6f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                ifsManager.setSlotVideo(
                                                    pkg.vtNumber,
                                                    com.tajasb.infinity.IfsSubPhase.CONTENT,
                                                    if (isAssigned) null else item.id
                                                )
                                            }
                                            .padding(vertical = 6.dp, horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isAssigned) "✓ Terpasang di $shortCode (Ketuk untuk Lepas ✕)" else "Pasang sebagai Video Konten $shortCode",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAssigned) EmeraldGreen else NeonCyan
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Opsi Cepat Buat Paket VT Baru dengan Video Ini
                    item {
                        Button(
                            onClick = {
                                val newVtNum = ifsManager.addVtPackage(item.id)
                                val defaultPhase = if (videoMode == com.tajasb.infinity.IfsVideoMode.SV1) {
                                    com.tajasb.infinity.IfsSubPhase.CONTENT
                                } else {
                                    com.tajasb.infinity.IfsSubPhase.BL
                                }
                                ifsManager.setSlotVideo(newVtNum, defaultPhase, item.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(alpha = 0.25f)),
                            border = ButtonDefaults.outlinedButtonBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(NeonPurple),
                                width = 1.dp
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "+ Buat VT${vtPkgs.size + 1} Baru & Pasang Video Ini",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE879F9)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
            ) {
                Text("Selesai", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (currentRoles.isNotEmpty()) {
                TextButton(
                    onClick = {
                        ifsManager.clearVideoFromAllSlots(item.id)
                    }
                ) {
                    Text("Lepas Semua Slot", color = Color(0xFFEF4444), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun IfsSingleAudioRoleDialog(
    audioItem: com.tajasb.model.AudioItem,
    ifsManager: com.tajasb.infinity.IfsManager,
    videoMode: com.tajasb.infinity.IfsVideoMode,
    vtmPkg: com.tajasb.infinity.IfsVtPackage,
    vtPkgs: List<com.tajasb.infinity.IfsVtPackage>,
    onDismiss: () -> Unit
) {
    val visiblePackages = vtPkgs

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Column {
                Text(
                    text = "🔊 Atur Peran Audio di Infinity System (IFS)",
                    color = Color(0xFFFCD34D),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = audioItem.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Pilihan 1: Jadikan Musik Latar SR (Sistem Random)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ifsManager.setAudioAsSr(audioItem.id)
                                onDismiss()
                            },
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF818CF8))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "🎵 Set sebagai Musik Latar SR (Sistem Random)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA5B4FC)
                            )
                            Text(
                                text = "Diputar acak sebagai musik latar standby & otomatis di-pause saat AX terpicu.",
                                fontSize = 8.5.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }

                // Pilihan 2: Tautkan sebagai Audio Terkait AX (AX-ABA, AX1, AX-PM1...)
                items(visiblePackages.size) { idx ->
                    val pkg = visiblePackages[idx]
                    val shortCode = pkg.getShortCode(videoMode)
                    val axCode = pkg.getAxCode(videoMode)
                    val isLinkedHere = pkg.linkedAudioId == audioItem.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ifsManager.setLinkedAudio(pkg.vtNumber, if (isLinkedHere) null else audioItem.id)
                            },
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLinkedHere) Color(0xFF78350F).copy(alpha = 0.45f) else Color(0xFF0F172A)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isLinkedHere) Color(0xFFF59E0B) else Color(0xFF334155)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isLinkedHere) "✓ Terhubung ke $axCode ($shortCode) — Ketuk untuk Lepas" else "🔊 Tautkan ke $axCode ($shortCode)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLinkedHere) Color(0xFFFCD34D) else Color.White
                                )
                                Text(
                                    text = "Diputar otomatis saat $shortCode aktif (SR dijeda sementara)",
                                    fontSize = 8.5.sp,
                                    color = Color.Gray
                                )
                            }
                            if (isLinkedHere) {
                                Text(
                                    text = "✓ Aktif ✕",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCD34D)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White)
            ) {
                Text("Selesai", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { ifsManager.clearAudioRole(audioItem.id) }) {
                Text("Reset ke SR", color = Color.LightGray, fontSize = 10.5.sp)
            }
        }
    )
}

@Composable
fun BatchAudioPreEditCard(
    viewModel: VcamViewModel,
    config: com.tajasb.model.VcamConfig
) {
    val batchCfg by viewModel.batchAudioConfig.collectAsState()
    val isTestingSingle by viewModel.isTestingSingleAudio.collectAsState()
    val isApplyingBatch by viewModel.isApplyingBatchAudio.collectAsState()
    val progressText by viewModel.batchAudioProgressText.collectAsState()
    val hasBackups by viewModel.hasAudioOriginalBackups.collectAsState()
    val isMusicPlaying by viewModel.isMusicPlaying.collectAsState()

    val sourceAudios = remember(config.audioPlaylist, config.audioLayer2Id, config.audioLayer2Path, config.audioLayer3Ids) {
        config.audioPlaylist.filterNot { item ->
            item.id.startsWith("vs_audio_rolling_") || item.path.contains("vs_audio_rolling_") ||
            config.isExcludedFromType1(item)
        }
    }

    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedSampleAudioId by rememberSaveable { mutableStateOf<String?>(null) }

    val activeSampleItem = remember(sourceAudios, selectedSampleAudioId) {
        sourceAudios.find { it.id == selectedSampleAudioId } ?: sourceAudios.firstOrNull()
    }
    val activeSampleIndex = remember(sourceAudios, activeSampleItem) {
        if (activeSampleItem != null) sourceAudios.indexOfFirst { it.id == activeSampleItem.id }.coerceAtLeast(0) else 0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_batch_audio_editor"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (!batchCfg.isDefault || isExpanded) Color(0xFFF59E0B) else DarkCardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // HEADER BARIS 1: Judul + Info (!) + Badge Ringkasan + Tombol Buka/Tutup
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🎛️", fontSize = 13.sp)
                    Text(
                        text = "Edit Audio Massal",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                    FeatureInfoButton(
                        title = "Edit Audio MP3 Massal (Khusus Tipe 1)",
                        description = "1. Hanya memproses Audio Tipe 1 (Audio Tipe 2 Latar Mandiri & Tipe 3 Ringtone otomatis tidak diikutkan agar bebas distorsi).\n\n" +
                            "2. Atur Potong Awal (Intro), Potong Akhir (Outro), Volume Boost, Tempo, atau Fade Halus untuk seluruh koleksi MP3 Tipe 1 Anda.\n\n" +
                            "3. Klik '🎧 Tes 1 Audio': Sistem HANYA memproses & memutar 1 file audio sampel pilihan Anda secara instan (±0.5 detik).\n\n" +
                            "4. Klik '⚡ Terapkan Massal ke Semua Audio' untuk memproses trek Audio Tipe 1 sekaligus.\n\n" +
                            "5. Tersedia tombol '↩️ Pulihkan Asli' untuk mengembalikan seluruh file audio ke kondisi awal kapan saja.",
                        accentColor = Color(0xFFF59E0B)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (!batchCfg.isDefault) Color(0xFF78350F).copy(alpha = 0.5f)
                                else Color(0xFF1E293B)
                            )
                            .border(
                                0.6.dp,
                                if (!batchCfg.isDefault) Color(0xFFF59E0B) else Color(0xFF475569),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (!batchCfg.isDefault) batchCfg.summaryBadge() else "${sourceAudios.size} Audio",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!batchCfg.isDefault) Color(0xFFFDE68A) else Color.LightGray,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0F172A))
                            .border(0.6.dp, Color(0xFFF59E0B).copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Tutup ▴" else "Buka Editor ▾",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFCD34D)
                        )
                    }
                }
            }

            if (progressText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF3B1D06))
                        .border(0.6.dp, Color(0xFFF59E0B), RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = progressText ?: "",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFDE68A)
                    )
                }
            }

            if (isExpanded) {
                if (sourceAudios.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0F172A))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Upload file audio atau 📦 Upload ZIP terlebih dahulu untuk menggunakan Edit Audio Massal.",
                            fontSize = 8.5.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    // 1. PEMILIH 1 AUDIO SAMPEL UNTUK TES INSTAN
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0F172A))
                            .border(0.6.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎯 Sampel Audio Tes:",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sourceAudios.size) { idx ->
                                val item = sourceAudios[idx]
                                val isChosen = item.id == activeSampleItem?.id
                                val shortLabel = if (item.name.length > 14) item.name.take(12) + ".." else item.name
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isChosen) NeonCyan.copy(alpha = 0.22f) else DarkSurface)
                                        .border(
                                            0.7.dp,
                                            if (isChosen) NeonCyan else Color(0xFF475569),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { selectedSampleAudioId = item.id }
                                        .padding(horizontal = 7.dp, vertical = 3.5.dp)
                                ) {
                                    Text(
                                        text = "🎧 LA${idx + 1}: $shortLabel",
                                        fontSize = 8.sp,
                                        fontWeight = if (isChosen) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isChosen) NeonCyan else Color.White
                                    )
                                }
                            }
                        }
                    }

                    // 2. KONTROL POTONG AWAL (TRIM START) & POTONG AKHIR (TRIM END)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Potong Awal
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF0F172A))
                                .border(0.6.dp, Color(0xFF1E293B), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✂️ Potong Awal",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCD34D)
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f dtk", batchCfg.trimStartSec),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            Slider(
                                value = batchCfg.trimStartSec,
                                onValueChange = { v ->
                                    val rounded = ( kotlin.math.round(v * 2f) / 2f ).coerceIn(0f, 15f)
                                    viewModel.updateBatchAudioConfig { it.copy(trimStartSec = rounded) }
                                },
                                valueRange = 0f..15f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFF59E0B),
                                    activeTrackColor = Color(0xFFF59E0B),
                                    inactiveTrackColor = Color(0xFF334155)
                                ),
                                modifier = Modifier.height(18.dp)
                            )
                        }

                        // Potong Akhir
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF0F172A))
                                .border(0.6.dp, Color(0xFF1E293B), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✂️ Potong Akhir",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCD34D)
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f dtk", batchCfg.trimEndSec),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            Slider(
                                value = batchCfg.trimEndSec,
                                onValueChange = { v ->
                                    val rounded = ( kotlin.math.round(v * 2f) / 2f ).coerceIn(0f, 15f)
                                    viewModel.updateBatchAudioConfig { it.copy(trimEndSec = rounded) }
                                },
                                valueRange = 0f..15f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFF59E0B),
                                    activeTrackColor = Color(0xFFF59E0B),
                                    inactiveTrackColor = Color(0xFF334155)
                                ),
                                modifier = Modifier.height(18.dp)
                            )
                        }
                    }

                    // 3. KONTROL VOLUME GAIN & KECEPATAN TEMPO
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Volume Gain (50% .. 200%) menggunakan Tombol - / +
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF0F172A))
                                .border(0.6.dp, Color(0xFF1E293B), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "🔊 Volume Boost",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1E293B))
                                        .border(0.5.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                                        .clickable {
                                            val next = (batchCfg.volumeGainPercent - 10).coerceIn(50, 200)
                                            viewModel.updateBatchAudioConfig { it.copy(volumeGainPercent = next) }
                                        }
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text("➖", fontSize = 8.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "${batchCfg.volumeGainPercent}%",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF064E3B))
                                        .border(0.5.dp, EmeraldGreen, RoundedCornerShape(4.dp))
                                        .clickable {
                                            val next = (batchCfg.volumeGainPercent + 10).coerceIn(50, 200)
                                            viewModel.updateBatchAudioConfig { it.copy(volumeGainPercent = next) }
                                        }
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text("➕", fontSize = 8.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Tempo / Speed (0.85x .. 1.25x)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF0F172A))
                                .border(0.6.dp, Color(0xFF1E293B), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚡ Tempo Audio",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%.2fx", batchCfg.speedMultiplier),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            Slider(
                                value = batchCfg.speedMultiplier,
                                onValueChange = { v ->
                                    val rounded = (kotlin.math.round(v * 20f) / 20f).coerceIn(0.85f, 1.25f)
                                    viewModel.updateBatchAudioConfig { it.copy(speedMultiplier = rounded) }
                                },
                                valueRange = 0.85f..1.25f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan,
                                    inactiveTrackColor = Color(0xFF334155)
                                ),
                                modifier = Modifier.height(18.dp)
                            )
                        }
                    }

                    // 4. PRESET CEPAT FADE IN/OUT & RESET
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0F172A))
                            .border(0.6.dp, Color(0xFF1E293B), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌊 Fade Halus:",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE879F9)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0f to "Off", 0.5f to "0.5d", 1.0f to "1.0d", 2.0f to "2.0d").forEach { (sec, label) ->
                                val isSel = kotlin.math.abs(batchCfg.fadeInSec - sec) < 0.05f &&
                                    kotlin.math.abs(batchCfg.fadeOutSec - sec) < 0.05f
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSel) NeonPurple.copy(alpha = 0.3f) else DarkSurface)
                                        .border(0.6.dp, if (isSel) NeonPurple else Color(0xFF334155), RoundedCornerShape(4.dp))
                                        .clickable {
                                            viewModel.updateBatchAudioConfig {
                                                it.copy(fadeInSec = sec, fadeOutSec = sec)
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color(0xFFE879F9) else Color.LightGray
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E293B))
                                .border(0.5.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                                .clickable { viewModel.resetBatchAudioConfig() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Reset Setelan",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray
                            )
                        }
                    }

                    // 4B. KARAKTER SUARA / VOICE EFFECT (MAN / WOMAN / NORMAL / CHIPMUNK / ROBOT)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0F172A))
                            .border(0.6.dp, Color(0xFF1E293B), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎭 Karakter Suara:",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            com.tajasb.model.VoiceEffect.values().forEach { effect ->
                                val isChosen = batchCfg.voiceEffect == effect
                                val activeColor = when (effect) {
                                    com.tajasb.model.VoiceEffect.NORMAL -> NeonCyan
                                    com.tajasb.model.VoiceEffect.ROOM -> Color(0xFF38BDF8)
                                    com.tajasb.model.VoiceEffect.HOUSE -> Color(0xFF34D399)
                                    com.tajasb.model.VoiceEffect.ECHO -> Color(0xFFF59E0B)
                                    com.tajasb.model.VoiceEffect.BASS_BOOST -> Color(0xFFEF4444)
                                    com.tajasb.model.VoiceEffect.MAN -> Color(0xFF60A5FA)
                                    com.tajasb.model.VoiceEffect.WOMAN -> Color(0xFFF472B6)
                                    com.tajasb.model.VoiceEffect.CHIPMUNK -> Color(0xFFFBBF24)
                                    com.tajasb.model.VoiceEffect.ROBOT -> Color(0xFFA78BFA)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isChosen) activeColor.copy(alpha = 0.22f) else DarkSurface)
                                        .border(
                                            0.7.dp,
                                            if (isChosen) activeColor else Color(0xFF334155),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable {
                                            viewModel.updateBatchAudioConfig { it.copy(voiceEffect = effect) }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${effect.icon} ${effect.label}",
                                        fontSize = 8.sp,
                                        fontWeight = if (isChosen) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isChosen) activeColor else Color.LightGray,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // 5. TOMBOL AKSI UTAMA: TES HANYA 1 AUDIO vs TERAPKAN MASSAL KE SEMUA AUDIO
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tombol 1: TES HANYA 1 AUDIO SAMPEL
                        Button(
                            onClick = { viewModel.testSingleAudioSample(activeSampleItem?.id) },
                            enabled = !isTestingSingle && !isApplyingBatch,
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .testTag("btn_batch_audio_test_single"),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = if (isTestingSingle) {
                                    "⏳ Menyiapkan Tes..."
                                } else {
                                    "🎧 Tes 1 Audio (LA${activeSampleIndex + 1})"
                                },
                                fontSize = 8.8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }

                        if (isMusicPlaying) {
                            OutlinedButton(
                                onClick = { viewModel.pauseAudio() },
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCD34D)),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFF59E0B))
                            ) {
                                Text("⏸️", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Tombol 2: TERAPKAN EDIT MASSAL KE SEMUA AUDIO (LA1..LAn)
                        Button(
                            onClick = { viewModel.applyBatchEditToAllAudios() },
                            enabled = !isTestingSingle && !isApplyingBatch,
                            modifier = Modifier
                                .weight(1.15f)
                                .height(30.dp)
                                .testTag("btn_batch_audio_apply_all"),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreen,
                                contentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = if (isApplyingBatch) {
                                    "⏳ Memproses..."
                                } else {
                                    "⚡ Terapkan ke ${sourceAudios.size} Audio"
                                },
                                fontSize = 8.8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }

                    if (hasBackups) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .testTag("btn_batch_audio_restore_orig")
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF3F1D2B))
                                    .border(0.6.dp, Color(0xFFFF5252), RoundedCornerShape(4.dp))
                                    .clickable(enabled = !isApplyingBatch && !isTestingSingle) {
                                        viewModel.restoreOriginalAudiosFromBackup()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "↩️ Pulihkan Semua Audio Asli Sebelum Edit Massal",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF8A80)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


