package com.tajasb.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.tajasb.FloatingControlService
import com.tajasb.eventrelay.ConnectionStatus
import com.tajasb.eventrelay.LiveEventPreferences
import com.tajasb.eventrelay.LiveEventReceiver
import com.tajasb.game.InteractiveKingOfThroneEngine
import com.tajasb.ui.components.StandardGameCard
import com.tajasb.ui.components.UnifiedGameSettingsDialog
import kotlinx.coroutines.delay

private fun setFloatingBubbleCleanArenaModeKot(context: Context, cleanArenaActive: Boolean) {
    try {
        if (FloatingControlService.isRunning) {
            val intent = Intent(context, FloatingControlService::class.java).apply {
                action = FloatingControlService.ACTION_SET_CLEAN_ARENA_MODE
                putExtra(FloatingControlService.EXTRA_CLEAN_ARENA_ACTIVE, cleanArenaActive)
            }
            context.startService(intent)
        }
    } catch (_: Throwable) {}
}

private fun formatDurationMmSs(seconds: Long): String {
    val safeSec = seconds.coerceAtLeast(0L)
    val mins = safeSec / 60L
    val secs = safeSec % 60L
    return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
}

@Composable
private fun ViewerAvatarBadge(
    name: String,
    avatarBase64: String,
    size: Dp,
    borderColor: Color,
    borderWidth: Dp = 2.dp
) {
    val decodedBitmap = remember(avatarBase64) {
        if (avatarBase64.isBlank()) {
            null
        } else {
            try {
                val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }
    }

    val initial = remember(name) {
        name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "👑"
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(borderColor.copy(alpha = 0.45f), Color(0xFF0F172A))
                )
            )
            .border(borderWidth, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (decodedBitmap != null) {
            Image(
                bitmap = decodedBitmap,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Text(
                text = initial,
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/**
 * LAYAR PENUH ARENA 60 FPS: RAJA SINGGASANA (KING OF THE HILL / REBUTAN KURSI SULTAN)
 * Dirancang khusus untuk Device 2 (Mode Game Seluler TikTok Live) agar direkam langsung 1080p 60 FPS.
 */
@Composable
fun KingOfThroneFullScreenArenaOverlay(
    onExitArena: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { InteractiveKingOfThroneEngine.getInstance(context) }
    val state by engine.state.collectAsState()

    BackHandler(enabled = true) {
        onExitArena()
    }

    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setFloatingBubbleCleanArenaModeKot(context, true)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setFloatingBubbleCleanArenaModeKot(context, false)
        }
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(250L)
        }
    }

    val isCelebratingUsurp = nowMs < state.usurpCelebrationUntilMs && state.currentKingName.isNotBlank()
    val hasKing = state.currentKingName.isNotBlank()
    val liveReignSec = if (hasKing && state.reignStartedAtMs > 0L) {
        ((nowMs - state.reignStartedAtMs) / 1000L).coerceAtLeast(0L)
    } else {
        0L
    }

    val shieldFractionTarget = if (state.maxThroneShieldHp > 0) {
        (state.throneShieldHp.toFloat() / state.maxThroneShieldHp.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedShieldFraction by animateFloatAsState(
        targetValue = shieldFractionTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "throne_shield_fraction"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "kot_arena_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    val goldPrimary = Color(0xFFFACC15)
    val goldDeep = Color(0xFFD97706)
    val crimsonAttack = Color(0xFFF43F5E)
    val royalPurple = Color(0xFF7C3AED)

    val shieldBarColor = when {
        animatedShieldFraction > 0.55f -> Color(0xFF10B981)
        animatedShieldFraction > 0.25f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090514),
                        Color(0xFF160B2E),
                        Color(0xFF090612)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. AURA SINGGASANA & CAHAYA MAHKOTA DI LATAR BELAKANG
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        goldPrimary.copy(alpha = 0.16f * pulseGlow),
                        royalPurple.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.36f),
                    radius = w * 0.68f
                ),
                center = Offset(w * 0.5f, h * 0.36f),
                radius = w * 0.68f
            )

            // Lingkaran suci singgasana
            drawCircle(
                color = goldPrimary.copy(alpha = 0.18f * pulseGlow),
                center = Offset(w * 0.5f, h * 0.36f),
                radius = w * 0.32f,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 2. KONTEN UTAMA ARENA (ATAS - TENGAH - BAWAH)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // A. HEADER ATAS: RONDE, REKOR TERLAMA & TOMBOL KELUAR KECIL
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1B4B).copy(alpha = 0.85f))
                            .border(1.dp, goldPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "👑 RONDE ${state.currentRound} • KUDETA: ${state.totalUsurpCount}x",
                            color = goldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x401E293B))
                            .border(0.8.dp, Color(0x4064748B), RoundedCornerShape(8.dp))
                            .clickable { onExitArena() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("exit_kot_fullscreen_arena_button")
                    ) {
                        Text(
                            text = "✕",
                            color = Color(0x9994A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Baris Rekor Terlama & Raja Tertinggi (Hall of Fame)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xCC0F172A))
                            .border(1.dp, goldDeep.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text(
                                text = "⏳ REKOR TAKHTA TERLAMA",
                                color = goldPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${state.longestReignName} (${formatDurationMmSs(state.longestReignSec)})",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xCC0F172A))
                            .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "💎 SKOR SULTAN TERTINGGI",
                                color = NeonCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${state.highestKingName} (${state.highestKingScore} Poin)",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // B. PANGGUNG UTAMA: SINGGASANA SULTAN & PERISAI TAKHTA
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Judul Singgasana
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF78350F), Color(0xFFB45309), Color(0xFF78350F))
                            )
                        )
                        .border(1.5.dp, goldPrimary, RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "👑 ${state.throneTitle.uppercase()} 👑",
                        color = Color(0xFFFEF08A),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // KARTU SINGGASANA EMAS (RAJA SAAT INI)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xE61E1B4B),
                                    Color(0xE60F172A),
                                    Color(0xE61E1338)
                                )
                            )
                        )
                        .border(
                            width = 2.5.dp,
                            brush = Brush.horizontalGradient(
                                listOf(goldDeep, goldPrimary.copy(alpha = 0.6f + 0.4f * pulseGlow), goldDeep)
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (hasKing) "👑 PENGUASA TAKHTA SAAT INI 👑" else "🪑 SINGGASANA MASIH KOSONG!",
                            color = if (hasKing) goldPrimary else NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Avatar Raja + Aura Mahkota
                        ViewerAvatarBadge(
                            name = if (hasKing) state.currentKingName else "?",
                            avatarBase64 = state.currentKingAvatarBase64,
                            size = 86.dp,
                            borderColor = if (hasKing) goldPrimary else NeonCyan,
                            borderWidth = 3.dp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Nama Raja / Penguasa
                        Text(
                            text = if (hasKing) state.currentKingName else "KETIK KOMENTAR / GIFT UNTUK KLAIM!",
                            color = Color.White,
                            fontSize = if (hasKing) 22.sp else 15.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Statistik Waktu Berkuasa & Skor Raja
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF090D16))
                                    .border(1.dp, goldPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⏱️ Berkuasa: ${formatDurationMmSs(liveReignSec)}",
                                    color = goldPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF090D16))
                                    .border(1.dp, EmeraldGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⚡ Kekuasaan: ${state.currentKingScore} Poin",
                                    color = EmeraldGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // BAR PERISAI TAKHTA (SHIELD HP)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🛡️ PERISAI SINGGASANA",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${state.throneShieldHp.toInt()} / ${state.maxThroneShieldHp.toInt()} HP (${state.shieldPercent}%)",
                                    color = shieldBarColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(22.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Color(0xFF090D16))
                                    .border(1.2.dp, Color(0xFF334155), RoundedCornerShape(11.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedShieldFraction.coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    shieldBarColor.copy(alpha = 0.75f),
                                                    shieldBarColor
                                                )
                                            )
                                        )
                                )
                                Text(
                                    text = if (hasKing) {
                                        "🔥 Penonton Lain Tap/Chat/Gift = Hancurkan Perisai & Kudeta Raja!"
                                    } else {
                                        "⚡ Jadilah Orang Pertama yang Menduduki Singgasana!"
                                    },
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.Center),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // C. BAGIAN BAWAH: DAFTAR PENANTang KUDETA TERKUAT & TICKER AKSI
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Papan Top 3 Penantang Kudeta
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xD90F172A))
                        .border(1.dp, crimsonAttack.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚔️ PENANTANG KUDETA TERKUAT",
                            color = crimsonAttack,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Tap Like • Chat • Gift = Serang HP Raja",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val top3 = state.topChallengers.take(3)
                    if (top3.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Belum ada penantang kudeta di ronde ini. Ayo serang takhta sekarang!",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            top3.forEachIndexed { idx, challenger ->
                                val rankMedal = when (idx) {
                                    0 -> "🥇 #1"
                                    1 -> "🥈 #2"
                                    else -> "🥉 #3"
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E293B))
                                        .border(
                                            0.8.dp,
                                            if (idx == 0) goldPrimary.copy(alpha = 0.7f) else Color(0xFF334155),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ViewerAvatarBadge(
                                        name = challenger.name,
                                        avatarBase64 = challenger.avatarBase64,
                                        size = 30.dp,
                                        borderColor = if (idx == 0) goldPrimary else crimsonAttack,
                                        borderWidth = 1.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$rankMedal ${challenger.name}",
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "⚔️ ${challenger.points} Poin",
                                            color = Color(0xFFFDE047),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Ticker Aksi Terakhir
                val tickerBorderColor = when (state.lastActionType) {
                    "USURP" -> goldPrimary
                    "DEFEND" -> EmeraldGreen
                    "ATTACK" -> crimsonAttack
                    else -> NeonCyan.copy(alpha = 0.6f)
                }
                val tickerBgColor = when (state.lastActionType) {
                    "USURP" -> goldDeep.copy(alpha = 0.28f)
                    "DEFEND" -> EmeraldGreen.copy(alpha = 0.22f)
                    "ATTACK" -> crimsonAttack.copy(alpha = 0.22f)
                    else -> Color(0xFF0F172A)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tickerBgColor)
                        .border(1.2.dp, tickerBorderColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.lastActionText,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. OVERLAY SELEBRASI KUDETA SINGGASANA (5 DETIK OTOMATIS)
        AnimatedVisibility(
            visible = isCelebratingUsurp,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFA1E1B4B), Color(0xFA090D16), Color(0xFA3B0764))
                        )
                    )
                    .border(2.5.dp, goldPrimary, RoundedCornerShape(22.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "👑 KUDETA SINGGASANA! 👑",
                        color = goldPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ViewerAvatarBadge(
                        name = state.currentKingName,
                        avatarBase64 = state.currentKingAvatarBase64,
                        size = 76.dp,
                        borderColor = goldPrimary,
                        borderWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${state.currentKingName.uppercase()} NAIK TAKHTA!",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    if (state.lastDethronedName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Menggulingkan ${state.lastDethronedName} dari Singgasana!",
                            color = Color(0xFFFDA4AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🛡️ Perisai Takhta Baru: ${state.throneShieldHp.toInt()} HP — Siapa Berani Merebut?",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * PANEL KONTROLER & PENGATURAN GAME 2: RAJA SINGGASANA (KING OF THE HILL)
 * Digunakan di Tab Game Hub (mendukung Mode 2-HP Game Seluler & Mode Kamera Virtual).
 */
@Composable
fun KingOfThroneControllerContent(
    onLaunchFullScreenArena: () -> Unit
) {
    val context = LocalContext.current
    val engine = remember { InteractiveKingOfThroneEngine.getInstance(context) }
    val receiver = remember { LiveEventReceiver.getInstance(context) }
    val prefs = remember { LiveEventPreferences.getInstance(context) }

    val state by engine.state.collectAsState()
    val brokerConfig by prefs.config.collectAsState()
    val connStatus by receiver.connectionStatus.collectAsState()
    val connDetails by receiver.connectionDetails.collectAsState()

    var ipInput by remember(brokerConfig.serverHost) { mutableStateOf(brokerConfig.serverHost) }
    var customTitleInput by remember(state.throneTitle) { mutableStateOf(state.throneTitle) }
    var customSimName by remember { mutableStateOf("Sultan Baru") }

    val isConnected = connStatus == ConnectionStatus.CONNECTED

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val liveReignSec = if (state.currentKingName.isNotBlank() && state.reignStartedAtMs > 0L) {
        ((nowMs - state.reignStartedAtMs) / 1000L).coerceAtLeast(0L)
    } else 0L

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var showSettingsDialog by remember { mutableStateOf(false) }

        // GAME 2: RAJA SINGGASANA — KARTU SERAGAM COMPACT
        StandardGameCard(
            gameIcon = "👑",
            gameTitle = "Raja Singgasana",
            gameSubtitle = "Takhta Sultan Live 1-Kursi",
            isVcamActive = state.isEnabled,
            onToggleVcam = {
                val next = engine.toggleEnabled()
                Toast.makeText(
                    context,
                    if (next) "👑 VCam Singgasana: ON" else "VCam Singgasana: OFF",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onLaunchFullScreen = {
                engine.setArenaDisplayActive(true)
                onLaunchFullScreenArena()
            },
            likeAction = "+1 Poin / Perisai",
            chatAction = "Chat (+15 Serang)",
            giftAction = "Gift (Kudeta Takhta)",
            statusText = if (state.currentKingName.isNotBlank()) {
                "👑 Raja: ${state.currentKingName} (${formatDurationMmSs(liveReignSec)}) • 🛡️ ${state.throneShieldHp.toInt()} HP (${state.shieldPercent}%) • Kudeta: ${state.totalUsurpCount}x"
            } else {
                "🪑 Singgasana Kosong • 🛡️ ${state.throneShieldHp.toInt()} HP • Menunggu Penonton!"
            },
            isRunning = state.isAutoTraffic,
            onToggleRunning = {
                val active = engine.toggleAutoTraffic()
                Toast.makeText(context, if (active) "Auto: ON" else "Auto: OFF", Toast.LENGTH_SHORT).show()
            },
            onReset = { engine.startNewRound() },
            botButtonLabel = if (state.isAutoTraffic) "Auto: ON" else "Auto: OFF",
            onBotAction = {
                val active = engine.toggleAutoTraffic()
                Toast.makeText(context, if (active) "Auto: ON" else "Auto: OFF", Toast.LENGTH_SHORT).show()
            },
            giftTestButtonLabel = "Kudeta Takhta",
            onGiftTestAction = {
                val neededPts = state.throneShieldHp + 200f
                engine.manualAction("Sultan Baru", neededPts, "👑 Kudeta Langsung")
            },
            onOpenSettings = { showSettingsDialog = true },
            accentColor = Color(0xFFFACC15)
        )

        if (showSettingsDialog) {
            UnifiedGameSettingsDialog(
                gameTitle = "Pengaturan Raja Singgasana",
                onDismiss = { showSettingsDialog = false },
                onShiftX = {},
                onShiftY = {},
                onScaleChange = {},
                scalePercent = 100,
                customSettingsContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "👑 Preset Judul Singgasana:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("SINGGASANA SULTAN", "RAJA TIKTOK LIVE", "KURSI PENGUASA", "TAKHTA TERTINGGI").forEach { title ->
                                val selected = state.throneTitle.equals(title, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) Color(0xFFD97706) else DarkSurfaceVariant)
                                        .border(0.8.dp, if (selected) Color(0xFFFACC15) else DarkCardBorder, RoundedCornerShape(6.dp))
                                        .clickable {
                                            customTitleInput = title
                                            engine.setThroneTitle(title)
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title.split(" ").firstOrNull() ?: title,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customTitleInput,
                                onValueChange = { customTitleInput = it },
                                label = { Text("Judul Kustom", fontSize = 9.5.sp) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.5.sp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val clean = customTitleInput.trim().ifEmpty { "SINGGASANA SULTAN" }
                                    engine.setThroneTitle(clean)
                                    Toast.makeText(context, "Judul: $clean", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(38.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("Simpan", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "🛡️ Ketebalan Perisai Awal Kudeta:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(250f, 500f, 1000f, 2500f).forEach { baseHp ->
                                val selected = state.baseShieldOnUsurp.toInt() == baseHp.toInt()
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) Color(0xFFD97706) else DarkSurfaceVariant)
                                        .border(0.8.dp, if (selected) Color(0xFFFACC15) else DarkCardBorder, RoundedCornerShape(6.dp))
                                        .clickable { engine.setBaseShield(baseHp) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${baseHp.toInt()} HP",
                                        color = if (selected) Color(0xFFFEF08A) else Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { engine.boostKingShield(150f) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+150 HP", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.startNewRound() },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Kosongkan", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.resetThroneAndRecords() },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Reset Skor", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ThronePresetChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
