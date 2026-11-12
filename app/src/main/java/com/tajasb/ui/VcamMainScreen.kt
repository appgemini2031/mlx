@file:Suppress("DEPRECATION")

package com.tajasb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.tajasb.ui.screens.MediaManagerScreen
import kotlinx.coroutines.delay

/**
 * Halaman Utama TAJASB
 * Dilengkapi ruang khusus notifikasi di bagian bawah (Bottom Status Dock)
 * agar notifikasi setiap perubahan TIDAK pernah menutupi tombol atau fitur di atasnya.
 */
@Composable
fun VcamMainScreen(
    viewModel: VcamViewModel,
    onPickVideoClick: () -> Unit,
    onPickAudioClick: () -> Unit,
    crashRecoveryMessage: String? = null,
    onDismissCrashRecovery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userMessage by viewModel.userMessage.collectAsState()
    val isAppFrozen by com.tajasb.engine.DecoyEngine.isAppUiFrozen.collectAsState()
    val towEngine = remember { com.tajasb.game.InteractiveTugOfWarEngine.getInstance(context) }
    val towState by towEngine.state.collectAsState()
    val kotEngine = remember { com.tajasb.game.InteractiveKingOfThroneEngine.getInstance(context) }
    val kotState by kotEngine.state.collectAsState()
    val beyEngine = remember { com.tajasb.game.InteractiveBeyBattleEngine.getInstance(context) }
    val beyState by beyEngine.state.collectAsState()
    val trooperEngine = remember { com.tajasb.game.InteractiveTrooperRoyaleEngine.getInstance(context) }
    val trooperState by trooperEngine.state.collectAsState()

    var activeBannerMessage by remember { mutableStateOf<String?>(null) }
    var messageVersion by remember { mutableIntStateOf(0) }
    var showEmergencyTotpDialog by remember { mutableStateOf(false) }
    var secretTapCount by remember { mutableIntStateOf(0) }
    var lastSecretTapTimeMs by remember { mutableLongStateOf(0L) }

    androidx.compose.runtime.DisposableEffect(context) {
        val freezeReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == com.tajasb.engine.DecoyEngine.ACTION_DECOY_APP_FREEZE) {
                    val frozen = intent.getBooleanExtra("frozen", true)
                    if (frozen && !viewModel.licenseManager.isVipDecoyBypassed()) {
                        com.tajasb.engine.DecoyEngine.activateProcessUiFreeze(sendCrossProcessBroadcast = false)
                    } else if (!frozen) {
                        com.tajasb.engine.DecoyEngine.releaseHostAndAppFreeze(sendCrossProcessBroadcast = false)
                    }
                }
            }
        }
        val filter = android.content.IntentFilter(com.tajasb.engine.DecoyEngine.ACTION_DECOY_APP_FREEZE)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(freezeReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(freezeReceiver, filter)
            }
        } catch (_: Throwable) {}
        onDispose {
            try {
                context.unregisterReceiver(freezeReceiver)
            } catch (_: Throwable) {}
        }
    }

    if (isAppFrozen) {
        androidx.activity.compose.BackHandler(enabled = true) {
            // Blokir tombol Back saat APK membeku (pengguna harus keluar paksa)
        }
    }

    // Langsung ganti pesan baru tanpa antrean yang menghalangi aktivitas
    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            if (viewModel.licenseManager.isVipDecoyBypassed()) {
                activeBannerMessage = msg.replace("\n", " • ")
                messageVersion++
            }
            viewModel.clearUserMessage()
        }
    }

    // Otomatis bersihkan pesan setelah 2.2 detik
    LaunchedEffect(messageVersion) {
        if (activeBannerMessage != null) {
            delay(2200)
            activeBannerMessage = null
        }
    }

    if (towState.isArenaDisplayActive) {
        com.tajasb.ui.screens.TugOfWarFullScreenArenaOverlay(
            onExitArena = {
                viewModel.openGameFromIntent(0)
                towEngine.setArenaDisplayActive(false)
            },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (kotState.isArenaDisplayActive) {
        com.tajasb.ui.screens.KingOfThroneFullScreenArenaOverlay(
            onExitArena = {
                viewModel.openGameFromIntent(1)
                kotEngine.setArenaDisplayActive(false)
            },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (beyState.isArenaDisplayActive) {
        com.tajasb.ui.screens.BeyBattleFullScreenArenaOverlay(
            onExitArena = {
                viewModel.openGameFromIntent(2)
                beyEngine.setArenaDisplayActive(false)
            },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (trooperState.isArenaDisplayActive) {
        com.tajasb.ui.screens.TrooperRoyaleFullScreenArenaOverlay(
            onExitArena = {
                viewModel.openGameFromIntent(3)
                trooperEngine.setArenaDisplayActive(false)
            },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        bottomBar = {
            // Ruang khusus di bagian bawah untuk notifikasi agar TIDAK PERNAH menutupi tombol/fitur
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .height(30.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (activeBannerMessage != null) DarkSurfaceVariant else DarkSurface.copy(alpha = 0.75f)
                        )
                        .border(
                            width = 0.8.dp,
                            color = if (activeBannerMessage != null) NeonCyan.copy(alpha = 0.55f) else DarkCardBorder.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (activeBannerMessage != null) {
                                activeBannerMessage = null
                            }
                            if (showEmergencyTotpDialog) {
                                return@clickable
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastSecretTapTimeMs > 750L) {
                                secretTapCount = 1
                            } else {
                                secretTapCount++
                            }
                            lastSecretTapTimeMs = now

                            // Haptic feedback tick on tap
                            try {
                                val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                if (vibrator != null && vibrator.hasVibrator()) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(25L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(25L)
                                    }
                                }
                            } catch (_: Throwable) {}

                            if (secretTapCount >= 7) {
                                secretTapCount = 0
                                showEmergencyTotpDialog = true
                            }
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (activeBannerMessage != null) NeonCyan else EmeraldGreen.copy(alpha = 0.7f))
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        AnimatedVisibility(
                            visible = activeBannerMessage != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                text = activeBannerMessage ?: "",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (activeBannerMessage == null) {
                            Text(
                                text = "Sistem Siap • Panel Status Aktif",
                                color = Color(0xFF94A3B8),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (activeBannerMessage != null) {
                        Text(
                            text = "✕",
                            color = NeonCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MediaManagerScreen(
                viewModel = viewModel,
                onPickVideoClick = onPickVideoClick,
                onPickAudioClick = onPickAudioClick,
                modifier = Modifier.fillMaxSize()
            )
            if (isAppFrozen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = true, onClick = {})
                )
            }
        }
    }

    if (showEmergencyTotpDialog) {
        com.tajasb.ui.components.EmergencyTotpDialog(
            onDismiss = { showEmergencyTotpDialog = false },
            onSuccessActivated = { _ ->
                activeBannerMessage = "✅ Sinkronisasi Sistem Berhasil Diaktifkan"
                messageVersion++
            }
        )
    }

    if (!crashRecoveryMessage.isNullOrEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onDismissCrashRecovery() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🛡️ Crash Shield Aktif",
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Aplikasi berhasil dicegah dari keluar (force close). Sistem memulihkan kembali kondisi kerja secara otomatis.",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Catatan Pemulihan: $crashRecoveryMessage",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { onDismissCrashRecovery() }
                ) {
                    Text("Lanjutkan", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
