package com.tajasb.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.tajasb.game.InteractiveTugOfWarEngine
import com.tajasb.ui.components.StandardGameCard
import com.tajasb.ui.components.UnifiedGameSettingsDialog
import kotlinx.coroutines.delay

private fun parseSafeHexColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Throwable) {
        fallback
    }
}

private fun setFloatingBubbleCleanArenaMode(context: Context, cleanArenaActive: Boolean) {
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

/**
 * LAYAR PENUH ARENA GAME 60 FPS (KHUSUS DEVICE 2 — MODE GAME SELULER TIKTOK LIVE)
 * - 100% Bersih dari tombol pengaturan dan menyembunyikan sementara Floating Bubble
 * - Dirancang khusus untuk direkam langsung (Screen Cast) oleh fitur "Game Seluler" TikTok Live
 * - Dikendalikan secara wireless dari Device 1 (Kontroler) via WebSocket Broker
 */
@Composable
fun TugOfWarFullScreenArenaOverlay(
    onExitArena: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { InteractiveTugOfWarEngine.getInstance(context) }
    val receiver = remember { LiveEventReceiver.getInstance(context) }
    val state by engine.state.collectAsState()
    val connStatus by receiver.connectionStatus.collectAsState()
    val viewerCount by receiver.viewerCount.collectAsState()

    // Pertahankan layar tetap menyala & sembunyikan floating bubble selama mode layar penuh aktif
    DisposableEffect(Unit) {
        engine.setArenaDisplayActive(true)
        setFloatingBubbleCleanArenaMode(context, true)
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            engine.setArenaDisplayActive(false)
            setFloatingBubbleCleanArenaMode(context, false)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Re-evaluate celebration timer smoothly
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.winnerCelebrationUntilMs) {
        while (System.currentTimeMillis() < state.winnerCelebrationUntilMs) {
            nowMs = System.currentTimeMillis()
            delay(100L)
        }
        nowMs = System.currentTimeMillis()
    }
    val isCelebratingNow = nowMs < state.winnerCelebrationUntilMs

    BackHandler(enabled = true) {
        onExitArena()
    }

    val colorA = remember(state.teamAColorHex) { parseSafeHexColor(state.teamAColorHex, Color(0xFF0284C7)) }
    val colorB = remember(state.teamBColorHex) { parseSafeHexColor(state.teamBColorHex, Color(0xFFE11D48)) }

    // Animasi pergeseran tali 60 FPS dengan fisika pegas
    val animatedNorm by animateFloatAsState(
        targetValue = state.normalizedProgress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "rope_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "arena_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    val pctA = ((1f - animatedNorm) * 100f).toInt().coerceIn(0, 100)
    val pctB = (100 - pctA).coerceIn(0, 100)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050811))
            .testTag("tug_of_war_fullscreen_arena")
    ) {
        // 1. Latar Belakang Energi Dinamis Kiri vs Kanan
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val splitX = (w * (1f - animatedNorm)).coerceIn(w * 0.1f, w * 0.9f)

            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        colorA.copy(alpha = 0.28f * pulseGlow),
                        colorA.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    startX = 0f,
                    endX = splitX * 1.15f
                ),
                size = size
            )

            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colorB.copy(alpha = 0.08f),
                        colorB.copy(alpha = 0.28f * pulseGlow)
                    ),
                    startX = splitX * 0.85f,
                    endX = w
                ),
                size = size
            )

            // Garis Grid Halus Arena Esport
            val gridStep = 48.dp.toPx()
            var gx = 0f
            while (gx < w) {
                drawLine(
                    color = Color.White.copy(alpha = 0.025f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = 1f
                )
                gx += gridStep
            }
        }

        // 2. Konten Utama Arena
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // A. BAGIAN ATAS: PAPAN SKOR RONDE & KUBU
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indikator Live & Penonton
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xCC0F172A))
                            .border(0.8.dp, Color(0xFF334155), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (connStatus == ConnectionStatus.CONNECTED) EmeraldGreen
                                    else Color(0xFFF59E0B)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (viewerCount > 0) "LIVE • $viewerCount Penonton" else "LIVE DUEL INTERAKTIF",
                            color = Color(0xFFE2E8F0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Badge Ronde Tengah
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(colorA.copy(alpha = 0.85f), colorB.copy(alpha = 0.85f))
                                )
                            )
                            .border(1.dp, Color(0xFFFDE047), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⚔️ RONDE ${state.currentRound} ⚔️",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Tombol Keluar Stealth (Transparan agar tidak mencolok di Live Stream)
                    Box(
                        modifier = Modifier
                            .alpha(0.32f)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF1E293B))
                            .clickable { onExitArena() }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("btn_exit_fullscreen_arena"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕ Tutup",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Papan Skor Besar Kiri vs Kanan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Kartu Kubu A (Kiri)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colorA.copy(alpha = 0.22f))
                            .border(1.8.dp, colorA, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.teamAName.uppercase(),
                            color = Color.White,
                            fontSize = if (state.teamAName.length > 9) 15.sp else 19.sp,
                            lineHeight = if (state.teamAName.length > 9) 17.sp else 21.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC090D16))
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "🏆 ${state.teamAWins} MENANG",
                                color = Color(0xFFFDE047),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "💬 Ketik \"1\" / \"${state.teamAName}\"",
                            color = Color(0xFFE0F2FE),
                            fontSize = 10.5.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // VS Badge
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(1.5.dp, Color(0xFFFDE047), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VS",
                            color = Color(0xFFFDE047),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // Kartu Kubu B (Kanan)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colorB.copy(alpha = 0.22f))
                            .border(1.8.dp, colorB, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.teamBName.uppercase(),
                            color = Color.White,
                            fontSize = if (state.teamBName.length > 9) 15.sp else 19.sp,
                            lineHeight = if (state.teamBName.length > 9) 17.sp else 21.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC090D16))
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "🏆 ${state.teamBWins} MENANG",
                                color = Color(0xFFFDE047),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "💬 Ketik \"2\" / \"${state.teamBName}\"",
                            color = Color(0xFFFFE4E6),
                            fontSize = 10.5.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Banner Aturan Main Penonton
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xD90F172A))
                        .border(0.8.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "❤️ Tap = +${state.likeWeight.toInt()}",
                        color = Color(0xFFF43F5E),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "💬 Chat = +${state.chatWeight.toInt()}",
                        color = NeonCyan,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "🎁 1 Koin = +${state.giftMultiplier.toInt()}",
                        color = Color(0xFFFDE047),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // B. BAGIAN TENGAH: ARENA TALI TARIK TAMBANG REAL-TIME 60 FPS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    val remainPts = (state.pointsToWin - kotlin.math.abs(state.ropePosition)).toInt().coerceAtLeast(0)
                    Text(
                        text = "🔥 Sisa $remainPts Poin ke Garis Menang",
                        color = Color(0xFFF8FAFC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Persentase Dominasi Besar Kiri vs Kanan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "$pctA%",
                            color = colorA,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "◄ TARIK KIRI (${state.teamAName})",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "$pctB%",
                            color = colorB,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "(${state.teamBName}) TARIK KANAN ►",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bar Power Tug + Custom Canvas Tali & Bendera Tengah
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(135.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xE6090D16))
                        .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val cy = h * 0.52f
                        val leftGoalX = w * 0.08f
                        val rightGoalX = w * 0.92f
                        val trackWidth = rightGoalX - leftGoalX

                        // Posisi bendera tengah: saat ropePosition = -pointsToWin (norm=0) -> leftGoalX; +pointsToWin (norm=1) -> rightGoalX
                        val knotX = (leftGoalX + trackWidth * animatedNorm).coerceIn(leftGoalX, rightGoalX)

                        // Zona Kemenangan Kiri & Kanan
                        drawRoundRect(
                            color = colorA.copy(alpha = 0.22f),
                            topLeft = Offset(0f, 8f),
                            size = Size(leftGoalX, h - 16f),
                            cornerRadius = CornerRadius(16f, 16f)
                        )
                        drawLine(
                            color = colorA,
                            start = Offset(leftGoalX, 6f),
                            end = Offset(leftGoalX, h - 6f),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        drawRoundRect(
                            color = colorB.copy(alpha = 0.22f),
                            topLeft = Offset(rightGoalX, 8f),
                            size = Size(w - rightGoalX, h - 16f),
                            cornerRadius = CornerRadius(16f, 16f)
                        )
                        drawLine(
                            color = colorB,
                            start = Offset(rightGoalX, 6f),
                            end = Offset(rightGoalX, h - 6f),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Garis Tengah Netral
                        drawLine(
                            color = Color.White.copy(alpha = 0.25f),
                            start = Offset(w * 0.5f, 12f),
                            end = Offset(w * 0.5f, h - 12f),
                            strokeWidth = 2.dp.toPx()
                        )

                        // Aura Tegangan Tali Kiri (Warna Tim A)
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(colorA, colorA.copy(alpha = 0.85f)),
                                startX = 0f,
                                endX = knotX
                            ),
                            start = Offset(0f, cy),
                            end = Offset(knotX, cy),
                            strokeWidth = 18.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Aura Tegangan Tali Kanan (Warna Tim B)
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(colorB.copy(alpha = 0.85f), colorB),
                                startX = knotX,
                                endX = w
                            ),
                            start = Offset(knotX, cy),
                            end = Offset(w, cy),
                            strokeWidth = 18.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Inti Tali Tambang Kepang (Braided Core)
                        drawLine(
                            color = Color(0xFFD97706),
                            start = Offset(0f, cy),
                            end = Offset(w, cy),
                            strokeWidth = 9.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        val strandStep = 16.dp.toPx()
                        var sx = (knotX % strandStep) - strandStep
                        while (sx < w) {
                            drawLine(
                                color = Color(0xFFFDE68A).copy(alpha = 0.75f),
                                start = Offset(sx, cy - 4.dp.toPx()),
                                end = Offset(sx + 7.dp.toPx(), cy + 4.dp.toPx()),
                                strokeWidth = 2.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            sx += strandStep
                        }

                        // Simpul & Bendera Merah Emas di Titik Tengah Tarikan (knotX)
                        val flagPath = Path().apply {
                            moveTo(knotX, cy)
                            lineTo(knotX - 16.dp.toPx(), cy - 38.dp.toPx())
                            lineTo(knotX + 16.dp.toPx(), cy - 38.dp.toPx())
                            close()
                        }
                        drawPath(path = flagPath, color = Color(0xFFFACC15))
                        drawPath(
                            path = flagPath,
                            color = Color.White,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Lingkaran Energi Inti di Simpul Tali
                        drawCircle(
                            color = Color.White.copy(alpha = 0.35f * pulseGlow),
                            radius = 22.dp.toPx(),
                            center = Offset(knotX, cy)
                        )
                        drawCircle(
                            color = Color(0xFFEF4444),
                            radius = 13.dp.toPx(),
                            center = Offset(knotX, cy)
                        )
                        drawCircle(
                            color = Color(0xFFFDE047),
                            radius = 13.dp.toPx(),
                            center = Offset(knotX, cy),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    // Label WIN di ujung kiri dan kanan
                    Text(
                        text = "GARIS\nMENANG",
                        color = colorA,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 10.sp,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = "GARIS\nMENANG",
                        color = colorB,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 10.sp,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bar Dominasi Horizontal Real-Time
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(50))
                ) {
                    val weightA = (1f - animatedNorm).coerceIn(0.05f, 0.95f)
                    val weightB = (1f - weightA).coerceIn(0.05f, 0.95f)
                    Box(
                        modifier = Modifier
                            .weight(weightA)
                            .fillMaxHeight()
                            .background(colorA)
                    )
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFFDE047))
                    )
                    Box(
                        modifier = Modifier
                            .weight(weightB)
                            .fillMaxHeight()
                            .background(colorB)
                    )
                }
            }

            // C. BAGIAN BAWAH: TICKER AKSI TERAKHIR & PAPAN MVP (TOP PULLER)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Banner Aksi Terakhir
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (state.lastActionTeam) {
                                "A" -> colorA.copy(alpha = 0.25f)
                                "B" -> colorB.copy(alpha = 0.25f)
                                else -> Color(0xFF0F172A)
                            }
                        )
                        .border(
                            1.2.dp,
                            when (state.lastActionTeam) {
                                "A" -> colorA
                                "B" -> colorB
                                else -> NeonCyan.copy(alpha = 0.6f)
                            },
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡ ${state.lastActionText}",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Kartu MVP / Penarik Terkuat Kiri vs Kanan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xD90F172A))
                            .border(1.dp, colorA.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "👑 MVP ${state.teamAName}",
                            color = colorA,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.topPullerTeamA,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${state.topPullerPointsA} Poin Tarikan",
                            color = Color(0xFFFDE047),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xD90F172A))
                            .border(1.dp, colorB.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "👑 MVP ${state.teamBName}",
                            color = colorB,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.topPullerTeamB,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${state.topPullerPointsB} Poin Tarikan",
                            color = Color(0xFFFDE047),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 3. OVERLAY SELEBRASI KEMENANGAN RONDE (6 DETIK OTOMATIS)
        AnimatedVisibility(
            visible = isCelebratingNow && state.lastWinnerTeam.isNotBlank(),
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFA0F172A), Color(0xFA1E1B4B))
                        )
                    )
                    .border(2.5.dp, Color(0xFFFACC15), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🏆 VICTORY! 🏆",
                        color = Color(0xFFFACC15),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "KUBU ${state.lastWinnerTeam.uppercase()} MENANG!",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bersiap memasuki Ronde ${state.currentRound}...",
                        color = NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * TAB GAME INTERAKTIF 2-HP (MODE GAME SELULER & REMOTE KONTROLER)
 * Memungkinkan pengguna memilih peran perangkat:
 * - Device 2 (HP Siaran): Membuka Layar Penuh Arena 60 FPS untuk direkam fitur "Game Seluler" TikTok Live
 * - Device 1 (HP Kontroler): Mengendalikan jalannya pertandingan di Device 2 secara wireless tanpa menyentuh layar live
 */
@Composable
fun TugOfWarGameHubTabContent(
    onLaunchFullScreenArena: () -> Unit,
    selectedGameIndex: Int = 0,
    onSelectGameIndex: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { InteractiveTugOfWarEngine.getInstance(context) }
    val receiver = remember { LiveEventReceiver.getInstance(context) }
    val prefs = remember { LiveEventPreferences.getInstance(context) }

    val state by engine.state.collectAsState()
    val brokerConfig by prefs.config.collectAsState()
    val connStatus by receiver.connectionStatus.collectAsState()
    val connDetails by receiver.connectionDetails.collectAsState()
    val isConnected = connStatus == ConnectionStatus.CONNECTED

    var ipInput by remember(brokerConfig.serverHost) { mutableStateOf(brokerConfig.serverHost) }
    var customTeamA by remember(state.teamAName) { mutableStateOf(state.teamAName) }
    var customTeamB by remember(state.teamBName) { mutableStateOf(state.teamBName) }

    var showSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // =====================================================================
        // 0. PEMILIH MODE GAME INTERAKTIF (2 PILIHAN GAME UTAMA)
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = "🎮 PILIH GAME INTERAKTIF LIVE (2-HP & KAMERA VIRTUAL)",
                    color = NeonCyan,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isTowSelected = selectedGameIndex == 0
                    val isKotSelected = selectedGameIndex == 1
                    val isBeySelected = selectedGameIndex == 2
                    val isTrooperSelected = selectedGameIndex == 3

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isTowSelected) Color(0xFF0284C7) else DarkSurfaceVariant
                            )
                            .border(
                                1.5.dp,
                                if (isTowSelected) NeonCyan else DarkCardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectGameIndex(0) }
                            .padding(vertical = 8.dp, horizontal = 2.dp)
                            .testTag("select_game_tug_of_war_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "⚔️ 1. TAMBANG",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            Text(
                                text = "Duel 2 Kubu",
                                color = if (isTowSelected) Color(0xFFE0F2FE) else Color(0xFF94A3B8),
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isKotSelected) Color(0xFFB45309) else DarkSurfaceVariant
                            )
                            .border(
                                1.5.dp,
                                if (isKotSelected) Color(0xFFFACC15) else DarkCardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectGameIndex(1) }
                            .padding(vertical = 8.dp, horizontal = 2.dp)
                            .testTag("select_game_king_of_throne_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "👑 2. SULTAN",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            Text(
                                text = "Singgasana",
                                color = if (isKotSelected) Color(0xFFFEF08A) else Color(0xFF94A3B8),
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isBeySelected) Color(0xFF0891B2) else DarkSurfaceVariant
                            )
                            .border(
                                1.5.dp,
                                if (isBeySelected) Color(0xFF06B6D4) else DarkCardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectGameIndex(2) }
                            .padding(vertical = 8.dp, horizontal = 2.dp)
                            .testTag("select_game_bey_battle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "🌀 3. GANGSING",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            Text(
                                text = "BeyBattle",
                                color = if (isBeySelected) Color(0xFFE0F2FE) else Color(0xFF94A3B8),
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isTrooperSelected) Color(0xFF15803D) else DarkSurfaceVariant
                            )
                            .border(
                                1.5.dp,
                                if (isTrooperSelected) Color(0xFF4ADE80) else DarkCardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectGameIndex(3) }
                            .padding(vertical = 8.dp, horizontal = 2.dp)
                            .testTag("select_game_trooper_royale_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "🎖️ 4. PRAJURIT",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            Text(
                                text = "Mini Trooper",
                                color = if (isTrooperSelected) Color(0xFFDCFCE7) else Color(0xFF94A3B8),
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }


        // PUSAT KONEKSI JARINGAN 2-HP SERAGAM (UNTUK SEMUA 4 GAME)
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isBigo = brokerConfig.activePlatform.equals("BIGO", ignoreCase = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isBigo) Color(0xFFBE185D) else DarkSurfaceVariant)
                                .clickable { receiver.switchPlatform("TIKTOK") }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎵 TikTok", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isBigo) Color(0xFF0891B2) else DarkSurfaceVariant)
                                .clickable { receiver.switchPlatform("BIGO") }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🫘 Bigo", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = if (isConnected) "🟢 Online: $connDetails" else "🟡 Siaga (Port 8765)",
                        color = if (isConnected) EmeraldGreen else Color(0xFFFCD34D),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.5.sp),
                        placeholder = { Text("127.0.0.1 / IP Wi-Fi", fontSize = 10.sp, color = Color.Gray) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Button(
                        onClick = {
                            prefs.updateServerAddress(ipInput.trim())
                            receiver.connect()
                        },
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) EmeraldGreen else NeonCyan,
                            contentColor = Color(0xFF090D16)
                        )
                    ) {
                        Text(if (isConnected) "Tersambung" else "Sambung", fontSize = 10.5.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        if (selectedGameIndex == 3) {
            TrooperRoyaleControllerContent(
                onLaunchFullScreenArena = {
                    onSelectGameIndex(3)
                    com.tajasb.game.InteractiveTrooperRoyaleEngine.getInstance(context).setArenaDisplayActive(true)
                }
            )
        } else if (selectedGameIndex == 2) {
            BeyBattleControllerContent(
                onLaunchFullScreenArena = {
                    onSelectGameIndex(2)
                    com.tajasb.game.InteractiveBeyBattleEngine.getInstance(context).setArenaDisplayActive(true)
                }
            )
        } else if (selectedGameIndex == 1) {
            KingOfThroneControllerContent(
                onLaunchFullScreenArena = {
                    onSelectGameIndex(1)
                    com.tajasb.game.InteractiveKingOfThroneEngine.getInstance(context).setArenaDisplayActive(true)
                }
            )
        } else {
            // GAME 1: TARIK TAMBANG — KARTU SERAGAM COMPACT
            StandardGameCard(
                gameIcon = "⚔️",
                gameTitle = "Tarik Tambang",
                gameSubtitle = "Duel 2 Kubu Tarik Tali Live",
                isVcamActive = state.isEnabled,
                onToggleVcam = {
                    val next = engine.toggleEnabled()
                    Toast.makeText(
                        context,
                        if (next) "Overlay VCam: ON" else "Overlay VCam: OFF",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onLaunchFullScreen = {
                    if (connStatus == ConnectionStatus.DISCONNECTED) {
                        receiver.connect()
                    }
                    onLaunchFullScreenArena()
                },
                likeAction = "Tarik Ringan",
                chatAction = "Ketik 1/2 (+${state.chatWeight.toInt()})",
                giftAction = "Tarik Sultan (${state.giftMultiplier.toInt()}x)",
                statusText = "Ronde ${state.currentRound} • ${state.teamAName} [${state.teamAWins}] vs [${state.teamBWins}] ${state.teamBName} • Tali: ${state.teamAPercent}% - ${state.teamBPercent}%",
                isRunning = state.isAutoTraffic,
                onToggleRunning = {
                    val active = engine.toggleAutoTraffic()
                    Toast.makeText(context, if (active) "Auto: ON" else "Auto: OFF", Toast.LENGTH_SHORT).show()
                },
                onReset = { engine.resetRope() },
                botButtonLabel = if (state.isAutoTraffic) "Auto: ON" else "Auto: OFF",
                onBotAction = {
                    val active = engine.toggleAutoTraffic()
                    Toast.makeText(context, if (active) "Auto: ON" else "Auto: OFF", Toast.LENGTH_SHORT).show()
                },
                giftTestButtonLabel = "+10 Koin Gift",
                onGiftTestAction = {
                    engine.manualPull("A", state.giftMultiplier * 10f, "Sultan Kiri", "🎁 10 Koin")
                },
                onOpenSettings = { showSettingsDialog = true },
                accentColor = NeonCyan
            )

            if (showSettingsDialog) {
                UnifiedGameSettingsDialog(
                    gameTitle = "Pengaturan Tarik Tambang",
                    onDismiss = { showSettingsDialog = false },
                    onShiftX = {},
                    onShiftY = {},
                    onScaleChange = {},
                    scalePercent = 100,
                    customSettingsContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "🏷️ Pilih Cepat Nama Kubu:",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf(
                                    Triple("Cowok vs Cewek", "COWOK", "CEWEK"),
                                    Triple("Biru vs Merah", "TIM BIRU", "TIM MERAH"),
                                    Triple("Kiri vs Kanan", "KIRI", "KANAN"),
                                    Triple("Kucing vs Anjing", "KUCING", "ANJING"),
                                    Triple("Tim A vs Tim B", "TIM A", "TIM B")
                                )
                                presets.forEach { (label, tA, tB) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkSurfaceVariant)
                                            .border(0.7.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                customTeamA = tA
                                                customTeamB = tB
                                                engine.setTeamNames(tA, tB)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = customTeamA,
                                    onValueChange = { customTeamA = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.5.sp),
                                    label = { Text("Tim Kiri (1)", fontSize = 9.5.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = customTeamB,
                                    onValueChange = { customTeamB = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.5.sp),
                                    label = { Text("Tim Kanan (2)", fontSize = 9.5.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Button(
                                onClick = {
                                    engine.setTeamNames(customTeamA, customTeamB)
                                    Toast.makeText(context, "Nama kubu disimpan!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Simpan Nama Kubu", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF090D16))
                            }

                            Text(
                                text = "🎯 Target Poin Menang (Saat ini: ${state.pointsToWin.toInt()}):",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(250f, 500f, 1000f, 2500f, 5000f).forEach { pts ->
                                    val selected = state.pointsToWin.toInt() == pts.toInt()
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (selected) NeonCyan.copy(alpha = 0.22f) else DarkSurfaceVariant)
                                            .border(0.8.dp, if (selected) NeonCyan else DarkCardBorder, RoundedCornerShape(6.dp))
                                            .clickable { engine.setPointsToWin(pts) }
                                            .padding(vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${pts.toInt()}", color = if (selected) NeonCyan else Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { engine.startNewRound() },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Ronde Baru", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { engine.resetScores() },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
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
}
