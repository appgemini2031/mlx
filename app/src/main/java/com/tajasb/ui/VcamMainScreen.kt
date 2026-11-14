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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.tajasb.ui.screens.GameHubMainScreen
import kotlinx.coroutines.delay

/**
 * Halaman Utama: Mobile Legends (MLBB), Termux Event Relay, dan update.sh
 */
@Composable
fun VcamMainScreen(
    viewModel: VcamViewModel,
    crashRecoveryMessage: String? = null,
    onDismissCrashRecovery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val userMessage by viewModel.userMessage.collectAsState()

    var activeBannerMessage by remember { mutableStateOf<String?>(null) }
    var messageVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            activeBannerMessage = msg.replace("\n", " • ")
            messageVersion++
            viewModel.clearUserMessage()
        }
    }

    LaunchedEffect(messageVersion) {
        if (activeBannerMessage != null) {
            delay(2200)
            activeBannerMessage = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        bottomBar = {
            // Ruang khusus di bagian bawah untuk notifikasi status
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
                                text = "⚔️ MLBB Mode Siap • Termux Event Relay",
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
            GameHubMainScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
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
                        text = "Aplikasi berhasil dicegah dari keluar. Sistem memulihkan kembali kondisi kerja secara otomatis.",
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
