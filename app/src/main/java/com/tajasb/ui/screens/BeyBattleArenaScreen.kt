package com.tajasb.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tajasb.game.ArenaBoundaryMode
import com.tajasb.game.BeyBladeAvatarHelper
import com.tajasb.game.BeyBladeGiftTier
import com.tajasb.game.BeyBladePlayer
import com.tajasb.game.FloatingDamageText
import com.tajasb.game.InteractiveBeyBattleEngine
import com.tajasb.game.ParticleKind
import com.tajasb.game.SparkParticle
import com.tajasb.ui.components.StandardGameCard
import com.tajasb.ui.components.UnifiedGameSettingsDialog
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * BeyBattleArenaScreen:
 * Layar Penuh 60 FPS Arena Pertempuran Gangsing (BeyBattle Royale) untuk Device 2 (Mode Game Seluler TikTok Live).
 */
@Composable
fun BeyBattleFullScreenArenaOverlay(
    onExitArena: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { InteractiveBeyBattleEngine.getInstance(context) }
    val state by engine.state.collectAsState()

    var isHudVisible by remember { mutableStateOf(true) }
    var showThumbPanel by remember { mutableStateOf(false) }
    var thumbPanelTab by remember { mutableIntStateOf(0) }

    BackHandler(enabled = true) {
        if (showThumbPanel) {
            showThumbPanel = false
        } else {
            onExitArena()
        }
    }

    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Jam Render 60 FPS GPU murni tanpa memicu recomposition pohon UI
    val infiniteTransition = rememberInfiniteTransition(label = "render_clock")
    val renderClock by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "render_tick"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        // 1. BACKGROUND KUSTOM / PROSEDURAL ARENA
        BeyBattleBackgroundView(state = state)

        // 2. ARENA FISIKA 2D KANVAS
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            // Guncangan Layar Halus saat ledakan keras / hantaman meteor (dapat diatur host)
            val effectiveShake = if (state.isScreenShakeEnabled) state.screenShakeIntensity * state.screenShakeMultiplier else 0f
            val shakeOffsetX = if (effectiveShake > 0.01f) {
                (kotlin.random.Random.nextFloat() - 0.5f) * 24f * effectiveShake
            } else 0f
            val shakeOffsetY = if (effectiveShake > 0.01f) {
                (kotlin.random.Random.nextFloat() - 0.5f) * 24f * effectiveShake
            } else 0f

            val arenaCenterX = (widthPx / 2f) + shakeOffsetX
            val arenaCenterY = (heightPx / 2f) + shakeOffsetY
            val arenaRadiusPx = (minOf(widthPx, heightPx) * 0.44f).coerceAtLeast(150f)
            val arenaRadiusX = arenaRadiusPx * state.arenaWidthScale
            val arenaRadiusY = arenaRadiusPx * state.arenaHeightScale
            val effRadiusX = arenaRadiusX * state.shrinkFactor
            val effRadiusY = arenaRadiusY * state.shrinkFactor

            Canvas(modifier = Modifier.fillMaxSize()) {
                val _tick = renderClock // Menghubungkan langsung ke jam GPU

                // A. Gambar Stadium Arena (Kotak atau Bulat Sesuai Rasio P x L & Shrinking Zone)
                drawArenaStadium(
                    cx = arenaCenterX,
                    cy = arenaCenterY,
                    rx = arenaRadiusX,
                    ry = arenaRadiusY,
                    effRx = effRadiusX,
                    effRy = effRadiusY,
                    shape = state.arenaShape,
                    isWallBounce = state.boundaryMode == ArenaBoundaryMode.WALL_BOUNCE
                )

                // B. Gambar Efek Neon Fever Mode di Sekeliling Stadium (Jika Fever Aktif)
                if (state.isFeverActive) {
                    val pulse = ((kotlin.math.sin(_tick * 0.15f) + 1f) * 0.5f)
                    val feverColor = Color(0xFFF59E0B).copy(alpha = 0.40f + 0.35f * pulse)
                    if (state.arenaShape.equals("RECTANGLE", ignoreCase = true)) {
                        drawRoundRect(
                            color = feverColor,
                            topLeft = androidx.compose.ui.geometry.Offset(arenaCenterX - effRadiusX - 8.dp.toPx(), arenaCenterY - effRadiusY - 8.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size((effRadiusX + 8.dp.toPx()) * 2f, (effRadiusY + 8.dp.toPx()) * 2f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx())
                        )
                    } else {
                        drawOval(
                            color = feverColor,
                            topLeft = androidx.compose.ui.geometry.Offset(arenaCenterX - effRadiusX - 8.dp.toPx(), arenaCenterY - effRadiusY - 8.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size((effRadiusX + 8.dp.toPx()) * 2f, (effRadiusY + 8.dp.toPx()) * 2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx())
                        )
                    }
                }

                // C. Gambar Rintangan Dinamis Individual (Trapdoor, Vortex, Laser)
                if (state.isHazardsEnabled) {
                    if (state.isTrapdoorsEnabled) {
                        val trapdoors = engine.getTrapdoorsSnapshot()
                        for (td in trapdoors) {
                            val tdx = arenaCenterX + td.x * arenaRadiusPx
                            val tdy = arenaCenterY + td.y * arenaRadiusPx
                            val tdR = td.radius * arenaRadiusPx
                            drawTrapdoorZone(td, tdx, tdy, tdR)
                        }
                    }

                    if (state.isVortexEnabled) {
                        drawCenterVortex(arenaCenterX, arenaCenterY, arenaRadiusPx * 0.22f, _tick)
                    }

                    if (state.isLaserEnabled) {
                        drawLaserSweeper(arenaCenterX, arenaCenterY, effRadiusX, effRadiusY, state.laserAngleDeg)
                    }
                }

                // E. Gambar Titan Boss Goliath Raksasa (Jika Aktif)
                val boss = engine.getBossSnapshot()
                if (state.isBossModeActive && boss != null && !boss.isEliminated) {
                    val bx = arenaCenterX + boss.x * arenaRadiusPx
                    val by = arenaCenterY + boss.y * arenaRadiusPx
                    val bRadius = boss.effectiveRadius * arenaRadiusPx
                    drawTitanBossTop(boss, bx, by, bRadius)
                }

                // F. Gambar Setiap Pemain Gangsing yang Aktif dengan Evolusi Senjata & Crown Pemimpin
                val activePlayers = engine.getActivePlayersSnapshot()
                for (p in activePlayers) {
                    val px = arenaCenterX + p.x * arenaRadiusPx
                    val py = arenaCenterY + p.y * arenaRadiusPx
                    val radiusPx = p.effectiveRadius * arenaRadiusPx
                    val isTopHunter = (state.topHunterId != null && state.topHunterId == p.id)

                    drawBeyBladeTop(
                        p = p,
                        x = px,
                        y = py,
                        radius = radiusPx,
                        arenaCenterX = arenaCenterX,
                        arenaCenterY = arenaCenterY,
                        arenaRadiusPx = arenaRadiusPx,
                        tick = _tick,
                        isTopHunter = isTopHunter
                    )
                }

                // G. Gambar Partikel Percikan Api, Pecahan Shatter & Kilatan Petir
                val particles = engine.getParticlesSnapshot()
                for (sp in particles) {
                    val spx = arenaCenterX + sp.x * arenaRadiusPx
                    val spy = arenaCenterY + sp.y * arenaRadiusPx
                    drawSparkParticle(sp, spx, spy)
                }

                // H. Gambar Teks Angka Damage Melayang & Upgrade Tier
                val floatingTexts = engine.getFloatingTextsSnapshot()
                for (ft in floatingTexts) {
                    val ftx = arenaCenterX + ft.x * arenaRadiusPx
                    val fty = arenaCenterY + ft.y * arenaRadiusPx
                    drawFloatingDamageText(ft, ftx, fty)
                }

                // I. Gambar Meteor Strike yang Sedang Meluncur Jatuh
                val meteors = engine.getMeteorsSnapshot()
                for (m in meteors) {
                    val mtx = arenaCenterX + m.targetX * arenaRadiusPx
                    val mty = arenaCenterY + m.targetY * arenaRadiusPx
                    drawActiveMeteor(m, mtx, mty, arenaRadiusPx)
                }

                // J. Efek Frost / Badai Salju Beku jika Blizzard Aktif
                if (state.isBlizzardActive) {
                    drawBlizzardFrostOverlay(size.width, size.height, _tick)
                }
            }
        }

        // 3. AREA ATAS 100% BERSIH DARI TOMBOL (Hanya Info Non-Interaktif Opsional: Kill Feed & Boss HP)
        AnimatedVisibility(
            visible = isHudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Banner Duel Final 1 VS 1 Slow Motion (Cinematic)
                if (state.isFinalClashActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFF59E0B), Color(0xFFDC2626))))
                            .border(1.2.dp, Color(0xFFFDE047), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⚔️ DUEL FINAL 1 VS 1 • SLOW-MOTION CLASH! ⚔️",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                // Banner Fever Mode atau Progres Like
                if (state.isFeverActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFD97706), Color(0xFFF59E0B))))
                            .border(1.dp, Color(0xFFFEF08A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "🔥 FEVER MODE AKTIF! ROTASI TURBO 1.55X! 🔥",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                } else if (state.currentFeverLikes > 0) {
                    val progress = (state.currentFeverLikes.toFloat() / state.feverTargetLikes.coerceAtLeast(1)).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.70f))
                            .border(0.6.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⚡ Fever Meter: ${state.currentFeverLikes}/${state.feverTargetLikes} Likes (${(progress * 100).toInt()}%)",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFDE047)
                        )
                    }
                }

                // Card Papan Klasemen Top Sultan & Pemburu K.O (Jika Host Toggle Aktifkan)
                if (state.showLeaderboardOverlay) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth(0.95f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.90f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Kolom Top Sultan
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "👑 TOP SULTAN GIFT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFCD34D)
                                )
                                val sultans = state.topSultans
                                if (sultans.isEmpty()) {
                                    Text("Belum ada gifter", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                } else {
                                    sultans.take(3).forEachIndexed { idx, s ->
                                        Text(
                                            text = "#${idx + 1} ${s.name}: ${s.coinsContributed} 🪙",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Kolom Top Pemburu K.O
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🎯 TOP PEMBURU K.O",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFF87171)
                                )
                                val hunters = state.topHunters
                                if (hunters.isEmpty()) {
                                    Text("Belum ada K.O", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                } else {
                                    hunters.take(3).forEachIndexed { idx, h ->
                                        Text(
                                            text = "#${idx + 1} ${h.name}: ${h.koCount} 💀",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Kill Feed Transparan (Bisa di-toggle OFF dari Pusat Pengaturan)
                if (state.showKillFeed && state.killFeed.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (kf in state.killFeed.takeLast(3)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0F172A).copy(alpha = 0.68f))
                                    .border(0.6.dp, Color(0xFFEF4444).copy(alpha = 0.50f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "⚔️ ${kf.killerName} ➔ 💀 ${kf.victimName}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFCA5A5),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Bar HP Boss Titan Goliath (Muncul hanya saat Boss sedang hidup)
                val boss = state.bossPlayer
                if (state.isBossModeActive && boss != null && !boss.isEliminated) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth(0.90f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E112A).copy(alpha = 0.92f)),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFEF4444))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "👹 TITAN GOLIATH",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFEF4444)
                                )
                                Text(
                                    text = "${boss.hp.toInt()} / ${boss.maxHp.toInt()} HP",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF3F1313))
                            ) {
                                val bossFrac = (boss.hp / boss.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(bossFrac)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Brush.horizontalGradient(listOf(Color(0xFFF97316), Color(0xFFEF4444))))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tombol Mengembalikan Dock Bawah jika sedang Disembunyikan (Pojok Kanan Bawah)
        if (!isHudVisible) {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(14.dp)
                    .align(Alignment.BottomEnd)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A).copy(alpha = 0.88f))
                    .border(1.2.dp, Color(0xFF06B6D4), CircleShape)
                    .testTag("bey_restore_hud_btn")
                    .clickable { isHudVisible = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Tampilkan Kontrol Bawah",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 4. DOCK KONTROL MINIMALIS BAWAH + TICKER OPSIONAL (SEMUA KONTROL TERPUSAT DI BAWAH)
        AnimatedVisibility(
            visible = isHudVisible && !showThumbPanel,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                // Ticker Event Live Opsional tepat di atas Dock Bawah
                if (state.showEventTicker) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.72f))
                            .border(0.8.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = state.lastEventBanner,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Kapsul Dock Bawah Tunggal Super Minimalis
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1222).copy(alpha = 0.94f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Pill Info Ronde & Pemain (Tap -> Buka Tab Tes Live)
                        if (state.showRoundBadge) {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF1E293B))
                                    .testTag("bey_dock_test_pill")
                                    .clickable {
                                        thumbPanelTab = 3
                                        showThumbPanel = true
                                    }
                                    .padding(horizontal = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "R${state.currentRound} • 🛡️${state.activePlayersCount}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        // 2. Quick Reset Ronde
                        IconButton(
                            onClick = { engine.resetRound() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF065F46).copy(alpha = 0.90f))
                                .testTag("bey_dock_reset_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Ronde",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 3. Tombol Utama Pusat Pengaturan
                        Row(
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF0284C7))
                                .testTag("bey_dock_settings_btn")
                                .clickable { showThumbPanel = true }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Pusat Pengaturan",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "ATUR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        // 4. Sembunyikan Dock (Layar Bersih 100%)
                        IconButton(
                            onClick = { isHudVisible = false },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .testTag("bey_dock_hide_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Sembunyikan Kontrol",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 5. Keluar Arena
                        IconButton(
                            onClick = onExitArena,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF7F1D1D).copy(alpha = 0.92f))
                                .testTag("bey_dock_exit_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Keluar Arena",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. BOTTOM SHEET PUSAT PENGATURAN TERPADU (4 TAB FLEKSIBEL & SCROLLABLE)
        AnimatedVisibility(
            visible = showThumbPanel,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BeyBattleThumbControlPanel(
                engine = engine,
                state = state,
                selectedTab = thumbPanelTab,
                onTabSelect = { thumbPanelTab = it },
                onDismiss = { showThumbPanel = false }
            )
        }

        // 6. OVERLAY SELEBRASI JUARA / SERI (DENGAN HITUNG MUNDUR AUTO-RESET)
        var countdownNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(state.isRoundEnding, state.celebrationUntilMs) {
            while (state.isRoundEnding) {
                countdownNowMs = System.currentTimeMillis()
                delay(250L)
            }
        }
        val showEndOverlay = state.isCelebratingChampion || (state.isDrawEnding && state.isRoundEnding)
        AnimatedVisibility(
            visible = showEndOverlay,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val secLeft = (((state.celebrationUntilMs - countdownNowMs) + 999L) / 1000L).coerceAtLeast(1L)
            val isDraw = state.isDrawEnding && state.lastChampion == null
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.96f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, if (isDraw) Color(0xFF38BDF8) else Color(0xFFFACC15)),
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(0.85f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Piala",
                        tint = if (isDraw) Color(0xFF38BDF8) else Color(0xFFFACC15),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isDraw) "⚔️ SERI TOTAL! ⚔️" else "👑 VICTORY ROYALE 👑",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDraw) Color(0xFF38BDF8) else Color(0xFFFACC15)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isDraw) "Semua Gangsing Tumbang" else (state.lastChampion?.name ?: "Sang Juara"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isDraw) {
                            "Pertarungan imbang tanpa pemenang"
                        } else {
                            "Berhasil menyingkirkan ${state.lastChampion?.koCount ?: 0} lawan!"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (state.autoResetEnabled) {
                            "🌀 Ronde baru otomatis dalam ${secLeft} detik..."
                        } else {
                            "⏸️ Auto-Reset mati — tekan ↺ di bawah untuk lanjut"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Renderer Background Arena (Kustom dari Galeri HP atau Tema Prosedural)
 */
@Composable
private fun BeyBattleBackgroundView(
    state: com.tajasb.game.BeyBattleState,
    modifier: Modifier = Modifier
) {
    val customBitmap = remember(state.customBgPath, state.arenaBgType) {
        if (state.arenaBgType == "CUSTOM_IMAGE" && state.customBgPath.isNotBlank()) {
            try {
                val f = java.io.File(state.customBgPath)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    if (state.arenaBgType == "CUSTOM_IMAGE" && customBitmap != null) {
        val imgBitmap = remember(customBitmap) { customBitmap.asImageBitmap() }
        Canvas(modifier = modifier.fillMaxSize()) {
            drawImage(
                image = imgBitmap,
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
            // Overlay vignette gelap halus agar arena dan gangsing tetap kontras
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    center = center,
                    radius = maxOf(size.width, size.height) * 0.75f
                )
            )
        }
    } else {
        Canvas(modifier = modifier.fillMaxSize()) {
            when (state.arenaBgType) {
                "LAVA_INFERNO" -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF380808), Color(0xFF150202), Color(0xFF070000)),
                            center = center,
                            radius = size.width * 0.9f
                        )
                    )
                    for (i in 0..12) {
                        val ly = size.height * (i / 12f)
                        drawLine(
                            color = Color(0xFFEF4444).copy(alpha = 0.08f),
                            start = Offset(0f, ly),
                            end = Offset(size.width, ly),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
                "DEEP_GALAXY" -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1E103A), Color(0xFF0F0721), Color(0xFF03010A)),
                            center = center,
                            radius = size.width * 0.95f
                        )
                    )
                    val rand = java.util.Random(1337)
                    for (k in 0..30) {
                        val sx = rand.nextFloat() * size.width
                        val sy = rand.nextFloat() * size.height
                        val sr = rand.nextFloat() * 1.5.dp.toPx()
                        drawCircle(
                            color = Color(0xFFE9D5FF).copy(alpha = rand.nextFloat() * 0.5f + 0.2f),
                            radius = sr,
                            center = Offset(sx, sy)
                        )
                    }
                }
                "NEON_CIRCUIT" -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF022329), Color(0xFF011417), Color(0xFF02070A)),
                            center = center,
                            radius = size.width * 0.9f
                        )
                    )
                    for (i in 0..14) {
                        val lx = size.width * (i / 14f)
                        drawLine(
                            color = Color(0xFF10B981).copy(alpha = 0.07f),
                            start = Offset(lx, 0f),
                            end = Offset(lx, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
                "METAL_STADIUM" -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF070B14)),
                            center = center,
                            radius = size.width * 0.85f
                        )
                    )
                }
                "SOLID_DARK" -> {
                    drawRect(color = Color(0xFF05070B))
                }
                else -> { // CYBER_GRID
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF0C192E), Color(0xFF070D18), Color(0xFF04070D)),
                            center = center,
                            radius = size.width * 0.85f
                        )
                    )
                    for (i in 0..16) {
                        val ly = size.height * (i / 16f)
                        drawLine(
                            color = Color(0xFF06B6D4).copy(alpha = 0.06f),
                            start = Offset(0f, ly),
                            end = Offset(size.width, ly),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Penggambar Arena Stadium: Mendukung Bentuk KOTAK (Persegi/Box), BULAT (Oval), dan Zona Menyusut (Shrinking Zone)
 */
private fun DrawScope.drawArenaStadium(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    effRx: Float,
    effRy: Float,
    shape: String,
    isWallBounce: Boolean
) {
    if (shape.equals("RECTANGLE", ignoreCase = true)) {
        drawBoxArenaStadium(cx, cy, rx, ry, effRx, effRy, isWallBounce)
    } else {
        drawOvalArenaStadium(cx, cy, rx, ry, effRx, effRy, isWallBounce)
    }
}

/**
 * ARENA KOTAK (Persegi Stadium dengan P x L, Bantalan Sudut, dan Shrinking Barrier)
 */
private fun DrawScope.drawBoxArenaStadium(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    effRx: Float,
    effRy: Float,
    isWallBounce: Boolean
) {
    val cornerR = 20.dp.toPx()
    val left = cx - rx
    val top = cy - ry
    val width = rx * 2f
    val height = ry * 2f

    // 1. Lantai Stadium Kotak
    drawRoundRect(
        color = Color(0xFF070E1A),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(cornerR, cornerR)
    )

    // 2. Garis Grid Kotak Konsentris Sesuai Proporsi P x L
    for (step in 1..4) {
        val frac = step / 5f
        val stepRx = rx * frac
        val stepRy = ry * frac
        drawRoundRect(
            color = Color(0xFF1E293B).copy(alpha = 0.5f),
            topLeft = Offset(cx - stepRx, cy - stepRy),
            size = Size(stepRx * 2f, stepRy * 2f),
            cornerRadius = CornerRadius(cornerR * frac, cornerR * frac),
            style = Stroke(width = 1.dp.toPx())
        )
    }

    // 3. Garis Silang Tengah (Crosshair Focus)
    drawLine(
        color = Color(0xFF1E293B).copy(alpha = 0.45f),
        start = Offset(left + 10.dp.toPx(), cy),
        end = Offset(left + width - 10.dp.toPx(), cy),
        strokeWidth = 1.dp.toPx()
    )
    drawLine(
        color = Color(0xFF1E293B).copy(alpha = 0.45f),
        start = Offset(cx, top + 10.dp.toPx()),
        end = Offset(cx, top + height - 10.dp.toPx()),
        strokeWidth = 1.dp.toPx()
    )

    // 4. Inti Gravitasi Pusat Arena
    val coreR = minOf(rx, ry) * 0.14f
    drawCircle(
        color = Color(0xFF06B6D4).copy(alpha = 0.18f),
        radius = coreR,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFF06B6D4),
        radius = coreR,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // 5. Dinding Luar Stadium
    drawRoundRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(cornerR, cornerR),
        style = Stroke(width = 2.dp.toPx())
    )

    // 6. Batas Aktif / Shrinking Death Zone
    val activeLeft = cx - effRx
    val activeTop = cy - effRy
    val activeW = effRx * 2f
    val activeH = effRy * 2f
    val wallColor = if (isWallBounce) {
        if (effRx < rx * 0.96f) Color(0xFFF59E0B) else Color(0xFF06B6D4)
    } else {
        Color(0xFFEF4444)
    }
    drawRoundRect(
        color = wallColor,
        topLeft = Offset(activeLeft, activeTop),
        size = Size(activeW, activeH),
        cornerRadius = CornerRadius(cornerR * (effRx / rx).coerceIn(0.4f, 1f), cornerR * (effRy / ry).coerceIn(0.4f, 1f)),
        style = Stroke(width = 3.5.dp.toPx())
    )

    // 7. Aksen Bantalan Bumper Sudut
    val bumperSize = minOf(effRx, effRy) * 0.18f
    val corners = listOf(
        Offset(activeLeft, activeTop),
        Offset(activeLeft + activeW, activeTop),
        Offset(activeLeft, activeTop + activeH),
        Offset(activeLeft + activeW, activeTop + activeH)
    )
    for (c in corners) {
        drawCircle(
            color = wallColor.copy(alpha = 0.6f),
            radius = bumperSize * 0.45f,
            center = c
        )
    }
}

/**
 * ARENA BULAT (Piringan Oval Klasik dengan P x L dan Shrinking Barrier)
 */
private fun DrawScope.drawOvalArenaStadium(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    effRx: Float,
    effRy: Float,
    isWallBounce: Boolean
) {
    // 1. Lantai Arena Dalam Oval Sesuai P x L
    drawOval(
        color = Color(0xFF070E1A),
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f)
    )

    // 2. Garis Grid Ellips Konsentris
    for (step in 1..4) {
        val stepFrac = step / 5f
        val stepRx = rx * stepFrac
        val stepRy = ry * stepFrac
        drawOval(
            color = Color(0xFF1E293B).copy(alpha = 0.5f),
            topLeft = Offset(cx - stepRx, cy - stepRy),
            size = Size(stepRx * 2f, stepRy * 2f),
            style = Stroke(width = 1.dp.toPx())
        )
    }

    // 3. Garis Silang Tengah (Crosshair Focus)
    drawLine(
        color = Color(0xFF1E293B).copy(alpha = 0.4f),
        start = Offset(cx - rx * 0.9f, cy),
        end = Offset(cx + rx * 0.9f, cy),
        strokeWidth = 1.dp.toPx()
    )
    drawLine(
        color = Color(0xFF1E293B).copy(alpha = 0.4f),
        start = Offset(cx, cy - ry * 0.9f),
        end = Offset(cx, cy + ry * 0.9f),
        strokeWidth = 1.dp.toPx()
    )

    // 4. Lingkaran Inti Pusat (Center Gravity Core)
    val coreRx = rx * 0.12f
    val coreRy = ry * 0.12f
    drawOval(
        color = Color(0xFF06B6D4).copy(alpha = 0.15f),
        topLeft = Offset(cx - coreRx, cy - coreRy),
        size = Size(coreRx * 2f, coreRy * 2f)
    )

    // 5. Dinding Luar Stadium
    drawOval(
        color = Color(0xFF1E293B),
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f),
        style = Stroke(width = 2.dp.toPx())
    )

    // 6. Batas Aktif / Shrinking Death Zone
    val wallColor = if (isWallBounce) {
        if (effRx < rx * 0.96f) Color(0xFFF59E0B) else Color(0xFF06B6D4)
    } else {
        Color(0xFFEF4444)
    }
    drawOval(
        color = wallColor,
        topLeft = Offset(cx - effRx, cy - effRy),
        size = Size(effRx * 2f, effRy * 2f),
        style = Stroke(width = 3.5.dp.toPx())
    )
}

/**
 * PUSARAN HISAP PUSAT (BLACK HOLE VORTEX)
 */
private fun DrawScope.drawCenterVortex(cx: Float, cy: Float, radius: Float, tick: Float) {
    val pulse = (sin(tick * 2f * Math.PI.toFloat()) * 0.15f + 0.85f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF7C3AED).copy(alpha = 0.35f), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius * 1.5f * pulse
        ),
        radius = radius * 1.5f * pulse,
        center = Offset(cx, cy)
    )
    rotate(degrees = tick * 360f * 2.5f, pivot = Offset(cx, cy)) {
        for (k in 1..3) {
            drawCircle(
                color = Color(0xFF06B6D4).copy(alpha = 0.45f),
                radius = (radius * k / 3f) * pulse,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
    drawCircle(
        color = Color.Black,
        radius = radius * 0.45f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFFA855F7),
        radius = radius * 0.45f,
        center = Offset(cx, cy),
        style = Stroke(width = 2.dp.toPx())
    )
}

/**
 * TRAPDOOR ZONES (LUBANG MAUT 4 KUADRAN)
 */
private fun DrawScope.drawTrapdoorZone(td: com.tajasb.game.TrapdoorZone, x: Float, y: Float, radius: Float) {
    when (td.state) {
        com.tajasb.game.HazardTrapState.CLOSED -> {
            drawCircle(
                color = Color(0xFF0B132B),
                radius = radius,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color(0xFF1E293B),
                radius = radius,
                center = Offset(x, y),
                style = Stroke(width = 2.dp.toPx())
            )
            drawLine(
                color = Color(0xFF334155),
                start = Offset(x - radius * 0.7f, y),
                end = Offset(x + radius * 0.7f, y),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = Color(0xFF334155),
                start = Offset(x, y - radius * 0.7f),
                end = Offset(x, y + radius * 0.7f),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        com.tajasb.game.HazardTrapState.WARNING -> {
            drawCircle(
                color = Color(0xFF451A03),
                radius = radius,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color(0xFFF59E0B),
                radius = radius,
                center = Offset(x, y),
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawLine(
                color = Color(0xFFF59E0B),
                start = Offset(x - radius * 0.6f, y - radius * 0.6f),
                end = Offset(x + radius * 0.6f, y + radius * 0.6f),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color(0xFFF59E0B),
                start = Offset(x - radius * 0.6f, y + radius * 0.6f),
                end = Offset(x + radius * 0.6f, y - radius * 0.6f),
                strokeWidth = 2.dp.toPx()
            )
        }
        com.tajasb.game.HazardTrapState.OPEN -> {
            drawCircle(
                color = Color.Black,
                radius = radius,
                center = Offset(x, y)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0xFFEF4444).copy(alpha = 0.5f), Color(0xFFDC2626)),
                    center = Offset(x, y),
                    radius = radius
                ),
                radius = radius,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color(0xFFEF4444),
                radius = radius,
                center = Offset(x, y),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

/**
 * SINAR LASER BERPUTAR (DEATH BEAM SWEEPER)
 */
private fun DrawScope.drawLaserSweeper(cx: Float, cy: Float, rx: Float, ry: Float, angleDeg: Float) {
    val maxR = maxOf(rx, ry) * 1.05f
    val rad = Math.toRadians(angleDeg.toDouble())
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()

    val start = Offset(cx - cosA * maxR, cy - sinA * maxR)
    val end = Offset(cx + cosA * maxR, cy + sinA * maxR)

    drawLine(
        color = Color(0xFF06B6D4).copy(alpha = 0.25f),
        start = start,
        end = end,
        strokeWidth = 10.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFF38BDF8).copy(alpha = 0.7f),
        start = start,
        end = end,
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White,
        start = start,
        end = end,
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round
    )
}

/**
 * METEOR STRIKE JATUH & KAWAH TARGET
 */
private fun DrawScope.drawActiveMeteor(m: com.tajasb.game.ActiveMeteor, tx: Float, ty: Float, arenaRadiusPx: Float) {
    val alt = m.altitude.coerceIn(0f, 1f)
    val reticleR = (0.20f * arenaRadiusPx) * (1f - alt * 0.4f)
    drawCircle(
        color = Color(0xFFEF4444).copy(alpha = 0.4f + 0.5f * (1f - alt)),
        radius = reticleR,
        center = Offset(tx, ty),
        style = Stroke(width = 2.dp.toPx())
    )

    val skyY = ty - alt * 260.dp.toPx()
    val skyX = tx - alt * 70.dp.toPx()
    val meteorSize = (12.dp.toPx() + (1f - alt) * 16.dp.toPx())

    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color(0xFFF97316), Color(0xFFFACC15)),
            start = Offset(skyX - 35.dp.toPx() * alt, skyY - 90.dp.toPx() * alt),
            end = Offset(skyX, skyY)
        ),
        start = Offset(skyX - 35.dp.toPx() * alt, skyY - 90.dp.toPx() * alt),
        end = Offset(skyX, skyY),
        strokeWidth = meteorSize * 0.75f,
        cap = StrokeCap.Round
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFEF08A), Color(0xFFEA580C), Color(0xFF7F1D1D)),
            center = Offset(skyX, skyY),
            radius = meteorSize
        ),
        radius = meteorSize,
        center = Offset(skyX, skyY)
    )
}

/**
 * BADAI SALJU / BLIZZARD FROST OVERLAY
 */
private fun DrawScope.drawBlizzardFrostOverlay(w: Float, h: Float, tick: Float) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0xFF38BDF8).copy(alpha = 0.18f), Color(0xFF0284C7).copy(alpha = 0.35f)),
            center = center,
            radius = maxOf(w, h) * 0.75f
        )
    )
}

/**
 * TITAN BOSS GOLIATH (RAKSASA TIKTOK LIVE)
 */
private fun DrawScope.drawTitanBossTop(boss: com.tajasb.game.BeyBladePlayer, x: Float, y: Float, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFEF4444).copy(alpha = 0.45f), Color(0xFF991B1B).copy(alpha = 0.25f), Color.Transparent),
            center = Offset(x, y),
            radius = radius * 1.55f
        ),
        radius = radius * 1.55f,
        center = Offset(x, y)
    )

    rotate(degrees = boss.rotationDeg, pivot = Offset(x, y)) {
        val numTeeth = 16
        val gearPath = Path()
        val angleStep = (2f * Math.PI / numTeeth).toFloat()
        val innerR = radius * 0.65f
        val outerR = radius * 1.15f

        for (i in 0 until numTeeth) {
            val a0 = i * angleStep
            val a1 = a0 + angleStep * 0.5f
            val a2 = a0 + angleStep

            val p0x = x + cos(a0) * innerR
            val p0y = y + sin(a0) * innerR
            val p1x = x + cos(a1) * outerR
            val p1y = y + sin(a1) * outerR
            val p2x = x + cos(a2) * innerR
            val p2y = y + sin(a2) * innerR

            if (i == 0) gearPath.moveTo(p0x, p0y)
            gearPath.lineTo(p1x, p1y)
            gearPath.lineTo(p2x, p2y)
        }
        gearPath.close()

        drawPath(path = gearPath, color = Color(0xFF7F1D1D))
        drawPath(path = gearPath, color = Color(0xFFEF4444), style = Stroke(width = 2.5.dp.toPx()))
    }

    drawCircle(
        color = Color(0xFF18181B),
        radius = radius * 0.55f,
        center = Offset(x, y)
    )
    drawCircle(
        color = Color(0xFFDC2626),
        radius = radius * 0.55f,
        center = Offset(x, y),
        style = Stroke(width = 3.dp.toPx())
    )

    val avatarBmp = boss.avatarBitmap ?: BeyBladeAvatarHelper.getCircularAvatar(
        name = "BOSS",
        colorHex = "#DC2626",
        targetSizePx = 140
    )
    val avatarImg = avatarBmp.asImageBitmap()
    val avR = radius * 0.50f
    drawImage(
        image = avatarImg,
        dstOffset = IntOffset((x - avR).toInt(), (y - avR).toInt()),
        dstSize = IntSize((avR * 2f).toInt(), (avR * 2f).toInt())
    )
}

/**
 * PENGGAMBAR PARTIKEL PERCIKAN API, SHATTER K.O, & KILATAN PETIR
 */
private fun DrawScope.drawSparkParticle(sp: SparkParticle, x: Float, y: Float) {
    val alpha = (sp.lifeRemainingSec / sp.maxLifeSec.coerceAtLeast(0.05f)).coerceIn(0f, 1f)
    val pColor = try {
        Color(android.graphics.Color.parseColor(sp.colorHex))
    } catch (_: Exception) {
        Color(0xFFFACC15)
    }
    val r = sp.sizeDp.dp.toPx() * (0.4f + 0.6f * alpha)
    when (sp.kind) {
        ParticleKind.SHATTER -> {
            drawRect(
                color = pColor.copy(alpha = alpha),
                topLeft = Offset(x - r, y - r),
                size = Size(r * 2f, r * 2f)
            )
        }
        ParticleKind.LIGHTNING -> {
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = r * 1.2f,
                center = Offset(x, y)
            )
            drawCircle(
                color = pColor.copy(alpha = alpha * 0.7f),
                radius = r * 2.1f,
                center = Offset(x, y)
            )
        }
        else -> {
            drawCircle(
                color = pColor.copy(alpha = alpha),
                radius = r,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * PENGGAMBAR ANGKA DAMAGE MELAYANG & UPGRADE TIER
 */
private fun DrawScope.drawFloatingDamageText(ft: FloatingDamageText, x: Float, y: Float) {
    val alpha = ((ft.lifeRemainingSec / ft.maxLifeSec.coerceAtLeast(0.05f)) * 255).toInt().coerceIn(0, 255)
    val baseColor = try {
        android.graphics.Color.parseColor(ft.colorHex)
    } catch (_: Exception) {
        android.graphics.Color.YELLOW
    }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(
            alpha,
            android.graphics.Color.red(baseColor),
            android.graphics.Color.green(baseColor),
            android.graphics.Color.blue(baseColor)
        )
        textSize = if (ft.isCrit) 13.5.dp.toPx() else 10.5.dp.toPx()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
        setShadowLayer(4f, 0f, 1.5f, android.graphics.Color.argb(alpha, 0, 0, 0))
    }
    drawContext.canvas.nativeCanvas.drawText(ft.text, x, y, paint)
}

/**
 * PENGGAMBAR GANGSING BEYBLADE TEMPUR (SAW-SHRED EDITION):
 * Evolusi Visual Weapon Shell (Tier 0..4), Motion Trail, Hit-Flash, dan Crown Pemimpin K.O
 */
private fun DrawScope.drawBeyBladeTop(
    p: BeyBladePlayer,
    x: Float,
    y: Float,
    radius: Float,
    arenaCenterX: Float,
    arenaCenterY: Float,
    arenaRadiusPx: Float,
    tick: Float,
    isTopHunter: Boolean
) {
    val topColor = try {
        Color(android.graphics.Color.parseColor(p.colorHex))
    } catch (_: Exception) {
        Color(0xFF06B6D4)
    }

    // 0. MOTION TRAIL SAAT TURBO DASH / MELUNCUR CEPAT
    if (p.isTurboDashing || (p.vx * p.vx + p.vy * p.vy) > 0.35f) {
        val tx1 = arenaCenterX + p.trailX1 * arenaRadiusPx
        val ty1 = arenaCenterY + p.trailY1 * arenaRadiusPx
        val tx2 = arenaCenterX + p.trailX2 * arenaRadiusPx
        val ty2 = arenaCenterY + p.trailY2 * arenaRadiusPx
        drawCircle(
            color = topColor.copy(alpha = 0.16f),
            radius = radius * 0.85f,
            center = Offset(tx2, ty2)
        )
        drawCircle(
            color = topColor.copy(alpha = 0.28f),
            radius = radius * 0.94f,
            center = Offset(tx1, ty1)
        )
    }

    // A. Aura Tier Tinggi (Inferno / Thunder / Galaxy)
    if (p.tier.tierLevel >= 2) {
        val auraColor = try {
            Color(android.graphics.Color.parseColor(p.tier.auraColorHex))
        } catch (_: Exception) {
            topColor
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(auraColor.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(x, y),
                radius = radius * 1.48f
            ),
            radius = radius * 1.48f,
            center = Offset(x, y)
        )
    }

    // B. Efek Perisai Aktif di Lapisan Paling Luar
    if (p.isShielded) {
        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = 0.25f),
            radius = radius * 1.30f,
            center = Offset(x, y)
        )
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = radius * 1.30f,
            center = Offset(x, y),
            style = Stroke(width = 2.dp.toPx())
        )
    }

    // C. Efek Shockwave saat Baru Gift Masuk
    if (p.isShockwaving) {
        drawCircle(
            color = Color(0xFFFACC15).copy(alpha = 0.45f),
            radius = radius * 1.58f,
            center = Offset(x, y),
            style = Stroke(width = 3.dp.toPx())
        )
    }

    // DIMENSI PRESISI TIAP LAYER (DARI LUAR KE DALAM)
    val avatarRadius = radius * 0.46f
    val indicatorThickness = 3.5.dp.toPx()
    val indicatorRadius = avatarRadius + indicatorThickness / 2f + 0.8.dp.toPx()
    val gearInnerRadius = avatarRadius + indicatorThickness + 1.8.dp.toPx()
    val gearOuterRadius = radius * 1.06f

    // =========================================================================
    // LAYER 3 (SISI TERLUAR): EVOLUSI WEAPON SHELL BERPUTAR (TIER 0..4)
    // =========================================================================
    // Cincin Buzzsaw Sekunder Berputar Berlawanan Arah untuk Tier >= 1
    if (p.tier.tierLevel >= 1) {
        rotate(degrees = -p.rotationDeg * 1.35f, pivot = Offset(x, y)) {
            val subTeeth = 10 + p.tier.tierLevel * 2
            val subPath = Path()
            val subStep = (2f * Math.PI / subTeeth).toFloat()
            val subInner = gearInnerRadius * 0.96f
            val subOuter = gearOuterRadius * 1.08f
            for (i in 0 until subTeeth) {
                val a0 = i * subStep
                val a1 = a0 + subStep * 0.45f
                val a2 = a0 + subStep
                if (i == 0) subPath.moveTo(x + cos(a0) * subInner, y + sin(a0) * subInner)
                subPath.lineTo(x + cos(a1) * subOuter, y + sin(a1) * subOuter)
                subPath.lineTo(x + cos(a2) * subInner, y + sin(a2) * subInner)
            }
            subPath.close()
            val shellTint = try {
                Color(android.graphics.Color.parseColor(p.tier.auraColorHex))
            } catch (_: Exception) {
                topColor
            }
            drawPath(path = subPath, color = shellTint.copy(alpha = 0.45f))
            drawPath(path = subPath, color = shellTint, style = Stroke(width = 1.3.dp.toPx()))
        }
    }

    // Bilah Gergaji Utama
    rotate(degrees = p.rotationDeg, pivot = Offset(x, y)) {
        val numTeeth = when (p.tier) {
            BeyBladeGiftTier.TIER_4_GALAXY -> 18
            BeyBladeGiftTier.TIER_3_THUNDER -> 16
            BeyBladeGiftTier.TIER_2_TITAN -> 14
            BeyBladeGiftTier.TIER_1_MINI -> 12
            else -> 8
        }

        // Piringan dasar metal di bawah gerigi
        drawCircle(
            color = Color(0xFF1E293B),
            radius = gearOuterRadius * 0.88f,
            center = Offset(x, y)
        )
        drawCircle(
            color = topColor.copy(alpha = 0.85f),
            radius = gearOuterRadius * 0.82f,
            center = Offset(x, y)
        )

        // Bentuk Gerigi Gergaji Tajam
        val gearPath = Path()
        val angleStep = (2f * Math.PI / numTeeth).toFloat()
        for (i in 0 until numTeeth) {
            val a0 = i * angleStep
            val a1 = a0 + angleStep * 0.25f
            val a2 = a0 + angleStep * 0.72f
            val a3 = a0 + angleStep

            val p0x = x + cos(a0) * gearInnerRadius
            val p0y = y + sin(a0) * gearInnerRadius
            val p1x = x + cos(a1) * (gearOuterRadius * 0.94f)
            val p1y = y + sin(a1) * (gearOuterRadius * 0.94f)
            val p2x = x + cos(a2) * gearOuterRadius
            val p2y = y + sin(a2) * gearOuterRadius
            val p3x = x + cos(a3) * gearInnerRadius
            val p3y = y + sin(a3) * gearInnerRadius

            if (i == 0) gearPath.moveTo(p0x, p0y)
            gearPath.lineTo(p1x, p1y)
            gearPath.lineTo(p2x, p2y)
            gearPath.lineTo(p3x, p3y)
        }
        gearPath.close()

        drawPath(path = gearPath, color = if (p.isHitFlashing) Color.White else topColor)
        drawPath(
            path = gearPath,
            color = Color.White.copy(alpha = 0.78f),
            style = Stroke(width = 1.3.dp.toPx())
        )

        // Aksen paku keling / rivet mekanis pada cincin gerigi
        val numBolts = minOf(numTeeth, 8)
        for (b in 0 until numBolts) {
            val bAngle = (b * (2f * Math.PI / numBolts)).toFloat()
            val bx = x + cos(bAngle) * (gearInnerRadius + (gearOuterRadius - gearInnerRadius) * 0.35f)
            val by = y + sin(bAngle) * (gearInnerRadius + (gearOuterRadius - gearInnerRadius) * 0.35f)
            drawCircle(
                color = Color(0xFF0F172A),
                radius = 1.5.dp.toPx(),
                center = Offset(bx, by)
            )
        }
    }

    // Busur Petir Menyambar untuk Tier 3 (Thunder) & Tier 4 (Galaxy)
    if (p.tier.tierLevel >= 3) {
        val boltColor = if (p.tier == BeyBladeGiftTier.TIER_4_GALAXY) Color(0xFFFACC15) else Color(0xFFC084FC)
        val arcBaseAngle = tick * 360f * 2f
        for (arcIdx in 0 until 3) {
            val aStart = Math.toRadians((arcBaseAngle + arcIdx * 120f).toDouble()).toFloat()
            val aMid = aStart + 0.35f
            val aEnd = aStart + 0.70f
            val rArc = gearOuterRadius * 1.16f
            val pStart = Offset(x + cos(aStart) * rArc, y + sin(aStart) * rArc)
            val pMid = Offset(x + cos(aMid) * (rArc * 1.12f), y + sin(aMid) * (rArc * 1.12f))
            val pEnd = Offset(x + cos(aEnd) * rArc, y + sin(aEnd) * rArc)
            drawLine(color = boltColor, start = pStart, end = pMid, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color = Color.White, start = pMid, end = pEnd, strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        }
    }

    // =========================================================================
    // LAYER 2 (TENGAH): INDIKATOR STAMINA HP
    // =========================================================================
    drawCircle(
        color = Color(0xFF0F172A),
        radius = indicatorRadius,
        center = Offset(x, y),
        style = Stroke(width = indicatorThickness)
    )

    val hpFraction = (p.hp / p.maxHp).coerceIn(0f, 1f)
    val hpColor = when {
        hpFraction > 0.5f -> Color(0xFF10B981)
        hpFraction > 0.25f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    drawArc(
        color = hpColor,
        startAngle = -90f,
        sweepAngle = 360f * hpFraction,
        useCenter = false,
        topLeft = Offset(x - indicatorRadius, y - indicatorRadius),
        size = Size(indicatorRadius * 2f, indicatorRadius * 2f),
        style = Stroke(width = indicatorThickness, cap = StrokeCap.Round)
    )

    drawCircle(
        color = if (p.isHitFlashing) Color.White else Color.White.copy(alpha = 0.9f),
        radius = avatarRadius,
        center = Offset(x, y),
        style = Stroke(width = if (p.isHitFlashing) 2.5.dp.toPx() else 1.2.dp.toPx())
    )

    // =========================================================================
    // LAYER 1 (INTI PALING DALAM): FOTO PROFIL PENONTON
    // =========================================================================
    val avatarBmp = p.avatarBitmap ?: BeyBladeAvatarHelper.getCircularAvatar(
        name = p.name,
        avatarBase64 = p.avatarBase64,
        colorHex = p.colorHex
    )
    val avatarImg = avatarBmp.asImageBitmap()

    drawImage(
        image = avatarImg,
        dstOffset = IntOffset((x - avatarRadius).toInt(), (y - avatarRadius).toInt()),
        dstSize = IntSize((avatarRadius * 2f).toInt(), (avatarRadius * 2f).toInt())
    )

    // =========================================================================
    // LABEL NAMA PENONTON, MAHKOTA PEMIMPIN K.O, JUARA BERTAHAN & BADGE BURONAN
    // =========================================================================
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val rawName = p.name.trim().ifBlank { "User" }
    val shortName = if (rawName.length > 9) rawName.take(8) + ".." else rawName
    val crownPrefix = when {
        p.isDefendingChampion -> "👑 "
        isTopHunter -> "⭐ "
        else -> ""
    }
    val nameTagText = if (p.koCount > 0) "$crownPrefix$shortName (${p.koCount} KO)" else "$crownPrefix$shortName"

    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = when {
            p.isDefendingChampion -> android.graphics.Color.parseColor("#FDE047")
            isTopHunter -> android.graphics.Color.parseColor("#FACC15")
            p.isBounty -> android.graphics.Color.parseColor("#FCA5A5")
            else -> android.graphics.Color.WHITE
        }
        textSize = 9.5.dp.toPx()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val textWidth = textPaint.measureText(nameTagText)
    val badgeW = textWidth + 12.dp.toPx()
    val badgeH = 15.dp.toPx()
    val badgeLeft = x - badgeW / 2f
    val badgeTop = y - radius * 1.32f - badgeH

    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = when {
            p.isDefendingChampion -> android.graphics.Color.parseColor("#E63B2506")
            p.isBounty -> android.graphics.Color.parseColor("#E6450A0A")
            else -> android.graphics.Color.parseColor("#E60F172A")
        }
        style = android.graphics.Paint.Style.FILL
    }
    nativeCanvas.drawRoundRect(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH, 6.dp.toPx(), 6.dp.toPx(), bgPaint)

    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = when {
            p.isDefendingChampion -> android.graphics.Color.parseColor("#FDE047")
            p.isBounty -> android.graphics.Color.parseColor("#EF4444")
            isTopHunter -> android.graphics.Color.parseColor("#FACC15")
            else -> android.graphics.Color.parseColor(p.colorHex)
        }
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = if (p.isDefendingChampion || p.isBounty || isTopHunter) 1.6.dp.toPx() else 1.0.dp.toPx()
    }
    nativeCanvas.drawRoundRect(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH, 6.dp.toPx(), 6.dp.toPx(), borderPaint)

    val yText = badgeTop + badgeH * 0.72f
    nativeCanvas.drawText(nameTagText, x, yText, textPaint)

    // Sub-badge: BURONAN / JUARA BERTAHAN / TIER GELAR
    var subBadgeOffset = badgeTop - 2.5.dp.toPx()
    if (p.isDefendingChampion) {
        val champPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FDE047")
            textSize = 7.5.dp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
        }
        nativeCanvas.drawText("👑 JUARA BERTAHAN", x, subBadgeOffset, champPaint)
        subBadgeOffset -= 9.dp.toPx()
    } else if (p.isBounty) {
        val bountyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#EF4444")
            textSize = 7.5.dp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
        }
        nativeCanvas.drawText("🎯 BURONAN", x, subBadgeOffset, bountyPaint)
        subBadgeOffset -= 9.dp.toPx()
    }

    if (p.tier != BeyBladeGiftTier.TIER_0_FREE) {
        val tierPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(p.tier.auraColorHex)
            textSize = 8.0.dp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
        }
        nativeCanvas.drawText(p.tier.title.uppercase(), x, subBadgeOffset, tierPaint)
    }
}

/**
 * PUSAT PENGATURAN BAWAH TERPADU ("PAS DI JEMPOL" - 4 TAB FLEKSIBEL & SCROLLABLE)
 * Tab 0: Aturan & Auto-Reset (Auto-Reset, Jeda Selebrasi, Auto-Bot, Kapasitas, Peluruhan HP)
 * Tab 1: Fisika & Zona (Mode Batas, Shrink Timer, Min Zona, Pantulan, Damage, Maks Size Like)
 * Tab 2: Efek & Suara (Saklar SFX, Saklar TTS, Volume Master, Rintangan Individual, Titan Boss)
 * Tab 3: Arena & Tes Live (Kotak/Bulat, P×L, Tema BG, Visibilitas HUD, Simulator Event Live)
 */
@Composable
fun BeyBattleThumbControlPanel(
    engine: InteractiveBeyBattleEngine,
    state: com.tajasb.game.BeyBattleState,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var customName by remember { mutableStateOf("Tester") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) {
                    val saved = engine.saveCustomBackground(stream)
                    if (saved) {
                        Toast.makeText(context, "Background terpasang!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.98f)),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF0284C7))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Header Pinned: Judul & Tombol Tutup
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "⚙️ PUSAT PENGATURAN ARENA",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF38BDF8)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF334155).copy(alpha = 0.85f), CircleShape)
                        .testTag("bey_panel_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Pengaturan",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Segmented 4 Tabs Pas di Jempol
            val tabs = listOf(
                0 to "🔄 Aturan",
                1 to "⚔️ Fisika",
                2 to "⚡ Suara",
                3 to "📐 Arena"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                tabs.forEach { (idx, label) ->
                    val isActive = selectedTab == idx
                    val activeColor = when (idx) {
                        0 -> Color(0xFF059669)
                        1 -> Color(0xFFD97706)
                        2 -> Color(0xFF7C3AED)
                        else -> Color(0xFF0284C7)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isActive) activeColor else Color.Transparent)
                            .testTag("bey_settings_tab_$idx")
                            .clickable { onTabSelect(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color.White else Color(0xFF94A3B8),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Konten Scrollable agar Semua Pengaturan Lengkap Namun Ringkas di Layar HP
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // TAB 0: ATURAN RONDE & AUTO-RESET
                        SettingSwitchRow(
                            title = "Auto-Reset Saat Ronde Selesai",
                            subtitle = "Langsung mulai ronde baru setelah juara / seri",
                            checked = state.autoResetEnabled,
                            onCheckedChange = { engine.setAutoResetConfig(it, state.celebrationDurationSec) }
                        )
                        SettingSwitchRow(
                            title = "Retensi Gifter & Survivor",
                            subtitle = "Simpan tier gift & pemain bertahan ke ronde baru",
                            checked = state.retainGiftersAcrossRounds,
                            onCheckedChange = { engine.setRetainGiftersConfig(it) }
                        )
                        SettingSliderRow(
                            label = "Jeda Selebrasi Juara",
                            valueText = "${state.celebrationDurationSec} dtk",
                            value = state.celebrationDurationSec.toFloat(),
                            valueRange = 1f..15f,
                            onValueChange = { engine.setAutoResetConfig(state.autoResetEnabled, it.toInt()) }
                        )
                        SettingSwitchRow(
                            title = "Auto-Isi Bot Awal / Reset",
                            subtitle = "Isi otomatis jika pemain live masih sepi",
                            checked = state.autoSpawnBotsEnabled,
                            onCheckedChange = { engine.setAutoBotConfig(it, state.minBotTargetCount, state.maxActivePlayers) }
                        )
                        SettingSliderRow(
                            label = "Target Minimal Bot/Pemain",
                            valueText = "${state.minBotTargetCount} top",
                            value = state.minBotTargetCount.toFloat(),
                            valueRange = 2f..12f,
                            onValueChange = { engine.setAutoBotConfig(state.autoSpawnBotsEnabled, it.toInt(), state.maxActivePlayers) }
                        )
                        SettingSliderRow(
                            label = "Batas Maksimal Pemain Arena",
                            valueText = "${state.maxActivePlayers} top",
                            value = state.maxActivePlayers.toFloat(),
                            valueRange = 5f..40f,
                            onValueChange = { engine.setAutoBotConfig(state.autoSpawnBotsEnabled, state.minBotTargetCount, it.toInt()) }
                        )
                        SettingSliderRow(
                            label = "Peluruhan Stamina Pasif",
                            valueText = "${String.format("%.2f", state.passiveHpDecayPerSec)}/dtk",
                            value = state.passiveHpDecayPerSec,
                            valueRange = 0f..3.0f,
                            onValueChange = {
                                engine.setPhysicsConfig(
                                    state.bounceMultiplier,
                                    state.damageMultiplier,
                                    it,
                                    state.maxLikeSizeBonus,
                                    state.shrinkStartSec,
                                    state.minShrinkFactor
                                )
                            }
                        )
                        Button(
                            onClick = {
                                engine.setAutoResetConfig(true, 4)
                                engine.setAutoBotConfig(true, 4, 25)
                                engine.setPhysicsConfig(
                                    state.bounceMultiplier,
                                    state.damageMultiplier,
                                    0.45f,
                                    state.maxLikeSizeBonus,
                                    state.shrinkStartSec,
                                    state.minShrinkFactor
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(34.dp)
                        ) {
                            Text("↺ Kembalikan Aturan Default", fontSize = 10.sp, color = Color(0xFF38BDF8))
                        }
                    }

                    1 -> {
                        // TAB 1: FISIKA, ZONA MENYUSUT & PERTUMBUHAN LIKE
                        val isBounce = state.boundaryMode == ArenaBoundaryMode.WALL_BOUNCE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isBounce) Color(0xFF065F46) else Color(0xFF1E293B))
                                    .border(1.dp, if (isBounce) Color(0xFF10B981) else Color(0xFF334155), RoundedCornerShape(8.dp))
                                    .clickable { engine.setBoundaryMode(ArenaBoundaryMode.WALL_BOUNCE) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🧱 Dinding Pantul", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isBounce) Color(0xFF7F1D1D) else Color(0xFF1E293B))
                                    .border(1.dp, if (!isBounce) Color(0xFFEF4444) else Color(0xFF334155), RoundedCornerShape(8.dp))
                                    .clickable { engine.setBoundaryMode(ArenaBoundaryMode.RING_OUT) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("💨 Jatuh Ring-Out", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        SettingSliderRow(
                            label = "Mulai Zona Menyusut",
                            valueText = "${state.shrinkStartSec} dtk",
                            value = state.shrinkStartSec.toFloat(),
                            valueRange = 5f..60f,
                            onValueChange = {
                                engine.setPhysicsConfig(
                                    state.bounceMultiplier,
                                    state.damageMultiplier,
                                    state.passiveHpDecayPerSec,
                                    state.maxLikeSizeBonus,
                                    it.toInt(),
                                    state.minShrinkFactor
                                )
                            }
                        )
                        SettingSliderRow(
                            label = "Batas Ukuran Minimal Zona",
                            valueText = "${(state.minShrinkFactor * 100).toInt()}%",
                            value = state.minShrinkFactor,
                            valueRange = 0.35f..0.90f,
                            onValueChange = {
                                engine.setPhysicsConfig(
                                    state.bounceMultiplier,
                                    state.damageMultiplier,
                                    state.passiveHpDecayPerSec,
                                    state.maxLikeSizeBonus,
                                    state.shrinkStartSec,
                                    it
                                )
                            }
                        )
                        SettingSliderRow(
                            label = "Kecepatan & Pantulan",
                            valueText = "${String.format("%.2f", state.bounceMultiplier)}x",
                            value = state.bounceMultiplier,
                            valueRange = 0.5f..2.0f,
                            onValueChange = {
                                engine.setPhysicsConfig(
                                    it,
                                    state.damageMultiplier,
                                    state.passiveHpDecayPerSec,
                                    state.maxLikeSizeBonus,
                                    state.shrinkStartSec,
                                    state.minShrinkFactor
                                )
                            }
                        )
                        SettingSliderRow(
                            label = "Damage Gesekan Bilah",
                            valueText = "${String.format("%.2f", state.damageMultiplier)}x",
                            value = state.damageMultiplier,
                            valueRange = 0.5f..2.5f,
                            onValueChange = {
                                engine.setPhysicsConfig(
                                    state.bounceMultiplier,
                                    it,
                                    state.passiveHpDecayPerSec,
                                    state.maxLikeSizeBonus,
                                    state.shrinkStartSec,
                                    state.minShrinkFactor
                                )
                            }
                        )
                        SettingSliderRow(
                            label = "Maks Pembesaran Tap Like",
                            valueText = "+${(state.maxLikeSizeBonus * 100).toInt()}%",
                            value = state.maxLikeSizeBonus,
                            valueRange = 0f..0.50f,
                            onValueChange = {
                                engine.setPhysicsConfig(
                                    state.bounceMultiplier,
                                    state.damageMultiplier,
                                    state.passiveHpDecayPerSec,
                                    it,
                                    state.shrinkStartSec,
                                    state.minShrinkFactor
                                )
                            }
                        )
                        Button(
                            onClick = {
                                engine.setPhysicsConfig(1.0f, 1.0f, state.passiveHpDecayPerSec, 0.35f, 20, 0.52f)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(34.dp)
                        ) {
                            Text("↺ Kembalikan Fisika Default", fontSize = 10.sp, color = Color(0xFFF59E0B))
                        }
                    }

                    2 -> {
                        // TAB 2: SUARA TERPISAH (ANTI-BOCOR), RINTANGAN SATUAN & BOSS
                        Text("🔊 KONTROL AUDIO (TERPISAH & ANTI-BOCOR)", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        SettingSwitchRow(
                            title = "SFX Benturan, Gergaji & Ledakan",
                            subtitle = "Suara logam, shred, laser, meteor",
                            checked = state.isSfxEnabled,
                            onCheckedChange = { engine.setAudioSettings(it, state.isAnnouncerEnabled, state.masterVolume) }
                        )
                        SettingSwitchRow(
                            title = "Suara Komentator TTS (Announcer)",
                            subtitle = "First Blood, Rampage, Victory Royale",
                            checked = state.isAnnouncerEnabled,
                            onCheckedChange = { engine.setAudioSettings(state.isSfxEnabled, it, state.masterVolume) }
                        )
                        SettingSliderRow(
                            label = "Volume Master Game",
                            valueText = "${(state.masterVolume * 100).toInt()}%",
                            value = state.masterVolume,
                            valueRange = 0f..1f,
                            onValueChange = { engine.setAudioSettings(state.isSfxEnabled, state.isAnnouncerEnabled, it) }
                        )

                        Spacer(modifier = Modifier.height(2.dp))
                        Text("📳 EFEK GOYANG LAYAR (SCREEN SHAKE)", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
                        SettingSwitchRow(
                            title = "Goyang Layar (Screen Shake)",
                            subtitle = "Guncang visual arena saat benturan & jurus ulti",
                            checked = state.isScreenShakeEnabled,
                            onCheckedChange = { engine.setScreenShakeConfig(it, state.screenShakeMultiplier) }
                        )
                        if (state.isScreenShakeEnabled) {
                            SettingSliderRow(
                                label = "Kekuatan Goyang Layar",
                                valueText = "${(state.screenShakeMultiplier * 100).toInt()}%",
                                value = state.screenShakeMultiplier,
                                valueRange = 0.2f..1.5f,
                                onValueChange = { engine.setScreenShakeConfig(state.isScreenShakeEnabled, it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text("⚠️ RINTANGAN ARENA (MASTER & SATUAN)", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFACC15))
                        SettingSwitchRow(
                            title = "Master Semua Rintangan",
                            subtitle = "Matikan untuk duel murni tanpa trap",
                            checked = state.isHazardsEnabled,
                            onCheckedChange = { engine.setHazardsEnabled(it) }
                        )
                        if (state.isHazardsEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                HazardChipToggle(
                                    label = "🌀 Vortex",
                                    active = state.isVortexEnabled,
                                    onClick = { engine.setIndividualHazards(!state.isVortexEnabled, state.isLaserEnabled, state.isTrapdoorsEnabled, state.bossMaxHp) },
                                    modifier = Modifier.weight(1f)
                                )
                                HazardChipToggle(
                                    label = "⚡ Laser",
                                    active = state.isLaserEnabled,
                                    onClick = { engine.setIndividualHazards(state.isVortexEnabled, !state.isLaserEnabled, state.isTrapdoorsEnabled, state.bossMaxHp) },
                                    modifier = Modifier.weight(1f)
                                )
                                HazardChipToggle(
                                    label = "🕳️ Trapdoor",
                                    active = state.isTrapdoorsEnabled,
                                    onClick = { engine.setIndividualHazards(state.isVortexEnabled, state.isLaserEnabled, !state.isTrapdoorsEnabled, state.bossMaxHp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text("👹 TITAN BOSS CO-OP", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                        SettingSliderRow(
                            label = "HP Maksimal Titan Boss",
                            valueText = "${state.bossMaxHp.toInt()} HP",
                            value = state.bossMaxHp,
                            valueRange = 1000f..10000f,
                            onValueChange = { engine.setIndividualHazards(state.isVortexEnabled, state.isLaserEnabled, state.isTrapdoorsEnabled, it) }
                        )
                        Button(
                            onClick = { engine.setBossModeActive(!state.isBossModeActive) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (state.isBossModeActive) Color(0xFFDC2626) else Color(0xFF6D28D9)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text(
                                text = if (state.isBossModeActive) "✖ Matikan Titan Boss Sekarang" else "👹 Panggil Titan Boss Sekarang",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    else -> {
                        // TAB 3: ARENA (BENTUK, P×L, TEMA BG), VISIBILITAS HUD & TES LIVE
                        val isBox = state.arenaShape.equals("RECTANGLE", ignoreCase = true)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isBox) Color(0xFF0284C7) else Color(0xFF1E293B))
                                    .clickable { engine.setArenaShape("RECTANGLE") }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⬛ KOTAK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (!isBox) Color(0xFF0284C7) else Color(0xFF1E293B))
                                    .clickable { engine.setArenaShape("CIRCLE") }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚪ BULAT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            listOf("1:1", "4:3", "16:9", "9:16").forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E293B))
                                        .clickable { engine.setArenaPreset(preset) }
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(preset, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8))
                                }
                            }
                        }

                        SettingSliderRow(
                            label = "Lebar Arena (L)",
                            valueText = "${(state.arenaWidthScale * 100).toInt()}%",
                            value = state.arenaWidthScale,
                            valueRange = 0.5f..1.6f,
                            onValueChange = { engine.setArenaDimensions(it, state.arenaHeightScale) }
                        )
                        SettingSliderRow(
                            label = "Panjang Arena (P)",
                            valueText = "${(state.arenaHeightScale * 100).toInt()}%",
                            value = state.arenaHeightScale,
                            valueRange = 0.5f..1.6f,
                            onValueChange = { engine.setArenaDimensions(state.arenaWidthScale, it) }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "CYBER_GRID" to "⚡Cyber",
                                "LAVA_INFERNO" to "🌋Lava",
                                "DEEP_GALAXY" to "🌌Galaxy",
                                "NEON_CIRCUIT" to "🟢Neon"
                            ).forEach { (tKey, tLabel) ->
                                val isSel = state.arenaBgType == tKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) Color(0xFF0D9488) else Color(0xFF1E293B))
                                        .clickable { engine.setArenaBackground(tKey) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(tLabel, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("METAL_STADIUM" to "🛡️Metal", "SOLID_DARK" to "⬛Dark").forEach { (tKey, tLabel) ->
                                val isSel = state.arenaBgType == tKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) Color(0xFF0D9488) else Color(0xFF1E293B))
                                        .clickable { engine.setArenaBackground(tKey) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(tLabel, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            Button(
                                onClick = {
                                    try {
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    } catch (_: Throwable) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(2f).height(28.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("📷 Foto Galeri", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Visibilitas HUD di Layar
                        Text("👁️ VISIBILITAS ELEMEN HUD", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            HazardChipToggle(
                                label = "Info Ronde",
                                active = state.showRoundBadge,
                                onClick = { engine.setHudVisibility(state.showKillFeed, state.showEventTicker, !state.showRoundBadge) },
                                modifier = Modifier.weight(1f)
                            )
                            HazardChipToggle(
                                label = "Kill Feed",
                                active = state.showKillFeed,
                                onClick = { engine.setHudVisibility(!state.showKillFeed, state.showEventTicker, state.showRoundBadge) },
                                modifier = Modifier.weight(1f)
                            )
                            HazardChipToggle(
                                label = "Ticker Live",
                                active = state.showEventTicker,
                                onClick = { engine.setHudVisibility(state.showKillFeed, !state.showEventTicker, state.showRoundBadge) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Simulator Tes Live
                        Text("🧪 SIMULATOR & TES LIVE", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC084FC))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                label = { Text("Nama Tes", fontSize = 8.5.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF8B5CF6),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1.2f).height(48.dp)
                            )
                            Button(
                                onClick = { engine.spawnBot(customName, BeyBladeGiftTier.TIER_0_FREE) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(0.9f).height(44.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+ Bot", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.onGiftEvent(customName, "Parfum", 1, 15L) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(44.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("🎁 Titan", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // TOMBOL AKSI CHAOS HOST (BENCANA INSTAN & PAPAN KLASEMEN)
                        Text("⚡ AKSI CHAOS HOST & KLASEMEN", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { engine.triggerMeteorRain(4) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("☄️ Hujan 4x", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.triggerHostBlizzard() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("❄️ Badai Es", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.toggleLeaderboardOverlay() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.showLeaderboardOverlay) Color(0xFFEAB308) else Color(0xFF475569)
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1.2f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (state.showLeaderboardOverlay) "🏆 Top Sultan: ON" else "🏆 Top Sultan",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { engine.onLikeEvent(customName, count = 20) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("⚡ Like+20", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.onChatEvent(customName, "TABRAK!") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("💬 Tabrak!", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.triggerMeteorStrike(callerName = customName) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("☄️ Meteor", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.triggerBlizzard(callerName = customName) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("❄️ Es", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { engine.triggerChainLightning(callerName = customName) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("⚡ Petir", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.resetRound() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("↺ Reset", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.clearArena() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("🧹 Bersihkan", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.72f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = subtitle, fontSize = 8.5.sp, color = Color(0xFF94A3B8))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF0284C7),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF0F172A)
            )
        )
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.65f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE2E8F0))
            Text(text = valueText, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF38BDF8))
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().height(26.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF0284C7),
                inactiveTrackColor = Color(0xFF0F172A)
            )
        )
    }
}

@Composable
private fun HazardChipToggle(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF065F46) else Color(0xFF1E293B))
            .border(1.dp, if (active) Color(0xFF10B981) else Color(0xFF334155), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (active) "$label ✓" else label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else Color(0xFF94A3B8)
        )
    }
}

/**
 * DIALOG POPUP: PENGATURAN DIMENSI ARENA (PANJANG x LEBAR / P x L & BENTUK KOTAK/BULAT)
 */
@Composable
private fun BeyBattleDimensionsDialog(
    engine: InteractiveBeyBattleEngine,
    state: com.tajasb.game.BeyBattleState,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        color = Color.Black.copy(alpha = 0.65f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF06B6D4)),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(0.95f)
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "📐 Atur Dimensi Arena (P × L)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF06B6D4)
                            )
                            Text(
                                text = "Pilih Bentuk Kotak / Bulat & Sesuaikan Panjang × Lebar",
                                fontSize = 9.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    BeyBattleDimensionsControls(engine = engine, state = state)

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✓ Selesai & Terapkan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * DIALOG POPUP: KUSTOMISASI BACKGROUND ARENA
 */
@Composable
private fun BeyBattleBackgroundDialog(
    engine: InteractiveBeyBattleEngine,
    state: com.tajasb.game.BeyBattleState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) {
                    val saved = engine.saveCustomBackground(stream)
                    if (saved) {
                        Toast.makeText(context, "Background kustom berhasil dipasang!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        color = Color.Black.copy(alpha = 0.65f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0D9488)),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(0.95f)
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🎨 Kustomisasi Background Arena",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2DD4BF)
                            )
                            Text(
                                text = "Pilih Tema Visual atau Pasang Gambar Sendiri dari Galeri",
                                fontSize = 9.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "1. PILIH TEMA BACKGROUND ARENA:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFACC15)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val bgThemes = listOf(
                        Triple("CYBER_GRID", "⚡ Cyber Grid", Color(0xFF0284C7)),
                        Triple("LAVA_INFERNO", "🌋 Lava Inferno", Color(0xFFDC2626)),
                        Triple("DEEP_GALAXY", "🌌 Deep Galaxy", Color(0xFF7C3AED)),
                        Triple("NEON_CIRCUIT", "🟢 Neon Circuit", Color(0xFF059669)),
                        Triple("METAL_STADIUM", "🛡️ Titanium Steel", Color(0xFF475569)),
                        Triple("SOLID_DARK", "⬛ Hitam Polos", Color(0xFF1E293B))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in bgThemes.chunked(2)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for ((key, label, colorSwatch) in row) {
                                    val isSelected = state.arenaBgType == key
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) colorSwatch.copy(alpha = 0.35f) else Color(0xFF1E293B))
                                            .border(
                                                1.5.dp,
                                                if (isSelected) colorSwatch else Color(0xFF334155),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { engine.setArenaBackground(key) }
                                            .padding(horizontal = 8.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "2. BACKGROUND GAMBAR DARI GALERI HP:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFACC15)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📁 Pilih Gambar dari Galeri HP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    if (state.arenaBgType == "CUSTOM_IMAGE") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✓ Gambar kustom sedang aktif", fontSize = 10.sp, color = Color(0xFF34D399))
                            Button(
                                onClick = { engine.setArenaBackground("CYBER_GRID") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Hapus Kustom", fontSize = 9.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✓ Selesai & Terapkan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * KOMPONEN SLIDER & PRESET PANJANG x LEBAR ARENA (P x L) DENGAN PILIHAN BENTUK KOTAK / BULAT
 */
@Composable
fun BeyBattleDimensionsControls(
    engine: InteractiveBeyBattleEngine,
    state: com.tajasb.game.BeyBattleState
) {
    var widthScale by remember(state.arenaWidthScale) { mutableStateOf(state.arenaWidthScale) }
    var heightScale by remember(state.arenaHeightScale) { mutableStateOf(state.arenaHeightScale) }
    val isBox = state.arenaShape.equals("RECTANGLE", ignoreCase = true)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 1. Pemilihan Bentuk Arena: KOTAK vs BULAT
        Text(
            text = "🔲 PILIH BENTUK ARENA:",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFACC15)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Pilihan ARENA KOTAK
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBox) Color(0xFF0284C7).copy(alpha = 0.35f) else Color(0xFF1E293B))
                    .border(1.5.dp, if (isBox) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(8.dp))
                    .clickable { engine.setArenaShape("RECTANGLE") }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⬛", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "ARENA KOTAK",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBox) Color(0xFF38BDF8) else Color.White
                        )
                        Text("Persegi / Box P×L", fontSize = 8.sp, color = Color(0xFF94A3B8))
                    }
                }
            }

            // Pilihan ARENA BULAT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!isBox) Color(0xFF06B6D4).copy(alpha = 0.35f) else Color(0xFF1E293B))
                    .border(1.5.dp, if (!isBox) Color(0xFF06B6D4) else Color(0xFF334155), RoundedCornerShape(8.dp))
                    .clickable { engine.setArenaShape("CIRCLE") }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚪", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "ARENA BULAT",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isBox) Color(0xFF38BDF8) else Color.White
                        )
                        Text("Lingkaran / Oval", fontSize = 8.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. Preset Cepat P x L Sesuai Bentuk Arena
        Text(
            text = if (isBox) "⚡ PRESET KOTAK (P × L):" else "⚡ PRESET BULAT/OVAL (P × L):",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFACC15)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val presets = if (isBox) {
                listOf(
                    "1:1" to "Persegi",
                    "4:3" to "Lebar",
                    "16:9" to "Wide",
                    "3:4" to "Tinggi",
                    "9:16" to "Vertikal"
                )
            } else {
                listOf(
                    "1:1" to "Bulat",
                    "4:3" to "Melebar",
                    "16:9" to "Wide",
                    "3:4" to "Lonjong",
                    "9:16" to "Vertikal"
                )
            }
            presets.forEach { (key, label) ->
                val isSelected = when (key) {
                    "1:1" -> kotlin.math.abs(state.arenaWidthScale - 1.0f) < 0.05f && kotlin.math.abs(state.arenaHeightScale - 1.0f) < 0.05f
                    "4:3" -> kotlin.math.abs(state.arenaWidthScale - 1.25f) < 0.05f && kotlin.math.abs(state.arenaHeightScale - 0.95f) < 0.05f
                    "16:9" -> kotlin.math.abs(state.arenaWidthScale - 1.45f) < 0.05f && kotlin.math.abs(state.arenaHeightScale - 0.82f) < 0.05f
                    "3:4" -> kotlin.math.abs(state.arenaWidthScale - 0.95f) < 0.05f && kotlin.math.abs(state.arenaHeightScale - 1.25f) < 0.05f
                    "9:16" -> kotlin.math.abs(state.arenaWidthScale - 0.85f) < 0.05f && kotlin.math.abs(state.arenaHeightScale - 1.50f) < 0.05f
                    else -> false
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(0xFF06B6D4).copy(alpha = 0.25f) else Color(0xFF1E293B))
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFF06B6D4) else Color(0xFF334155),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { engine.setArenaPreset(key) }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = key,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF38BDF8) else Color.White
                        )
                        Text(
                            text = label,
                            fontSize = 7.5.sp,
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // Slider 1: Lebar Arena (L / Width)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "↔ LEBAR ARENA (L)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
                Text(
                    text = "${(state.arenaWidthScale * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF38BDF8)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val next = (state.arenaWidthScale - 0.05f).coerceIn(0.4f, 1.8f)
                        engine.setArenaDimensions(next, state.arenaHeightScale)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("-", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Slider(
                    value = widthScale,
                    onValueChange = {
                        widthScale = it
                        engine.setArenaDimensions(it, state.arenaHeightScale)
                    },
                    valueRange = 0.40f..1.80f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF06B6D4),
                        activeTrackColor = Color(0xFF06B6D4),
                        inactiveTrackColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val next = (state.arenaWidthScale + 0.05f).coerceIn(0.4f, 1.8f)
                        engine.setArenaDimensions(next, state.arenaHeightScale)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Slider 2: Panjang Arena (P / Height)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "↕ PANJANG ARENA (P)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF472B6)
                )
                Text(
                    text = "${(state.arenaHeightScale * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFF472B6)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val next = (state.arenaHeightScale - 0.05f).coerceIn(0.4f, 1.8f)
                        engine.setArenaDimensions(state.arenaWidthScale, next)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("-", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Slider(
                    value = heightScale,
                    onValueChange = {
                        heightScale = it
                        engine.setArenaDimensions(state.arenaWidthScale, it)
                    },
                    valueRange = 0.40f..1.80f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFEC4899),
                        activeTrackColor = Color(0xFFEC4899),
                        inactiveTrackColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val next = (state.arenaHeightScale + 0.05f).coerceIn(0.4f, 1.8f)
                        engine.setArenaDimensions(state.arenaWidthScale, next)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Tombol Reset Standar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = { engine.setArenaPreset("1:1") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("↺ Reset 100% × 100%", fontSize = 9.5.sp, color = Color.White)
            }
        }
    }
}

/**
 * PANEL KONTROLER & PENGATURAN GAME 3: BEYBATTLE ROYALE (GANGSING BATTLE ROYALE)
 * Digunakan di Tab Game Hub (mendukung Mode 2-HP Game Seluler & Mode Kamera Virtual).
 */
@Composable
fun BeyBattleControllerContent(
    onLaunchFullScreenArena: () -> Unit
) {
    val context = LocalContext.current
    val engine = remember { InteractiveBeyBattleEngine.getInstance(context) }
    val state by engine.state.collectAsState()

    var customSimName by remember { mutableStateOf("Penonton_1") }
    var showBackgroundDialogInHub by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        var showSettingsDialog by remember { mutableStateOf(false) }

        // GAME 3: BEYBATTLE ROYALE — KARTU SERAGAM COMPACT
        StandardGameCard(
            gameIcon = "🌀",
            gameTitle = "BeyBattle Royale",
            gameSubtitle = "Gangsing Tempur TikTok Live",
            isVcamActive = state.isEnabled,
            onToggleVcam = {
                val next = !state.isEnabled
                engine.setEnabled(next)
                Toast.makeText(
                    context,
                    if (next) "🌀 VCam BeyBattle: ON" else "VCam BeyBattle: OFF",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onLaunchFullScreen = onLaunchFullScreenArena,
            likeAction = "+Size & HP",
            chatAction = "Turbo Dash",
            giftAction = "Evolusi Bilah",
            statusText = "Ronde ${state.currentRound} • 🛡️ ${state.activePlayersCount} Gangsing Aktif • P:${(state.arenaHeightScale * 100).toInt()}% L:${(state.arenaWidthScale * 100).toInt()}%",
            isRunning = state.isGameActive && state.activePlayersCount > 0,
            onToggleRunning = {
                if (state.activePlayersCount == 0) {
                    engine.spawnBot("Bot_1", BeyBladeGiftTier.TIER_0_FREE)
                }
            },
            onReset = { engine.resetRound() },
            botButtonLabel = "+ BOT",
            onBotAction = {
                val botNum = (System.currentTimeMillis() % 100).toInt()
                engine.spawnBot("Bot_$botNum", BeyBladeGiftTier.TIER_0_FREE)
            },
            giftTestButtonLabel = "🎁 Titan",
            onGiftTestAction = {
                engine.onGiftEvent("Sultan", "Parfum", 1, 15L)
            },
            onOpenSettings = { showSettingsDialog = true },
            accentColor = Color(0xFF06B6D4)
        )

        if (showSettingsDialog) {
            UnifiedGameSettingsDialog(
                gameTitle = "Pengaturan BeyBattle",
                onDismiss = { showSettingsDialog = false },
                onShiftX = {},
                onShiftY = {},
                onScaleChange = {},
                scalePercent = 100,
                customSettingsContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📐 Dimensi Arena (P × L):",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { showBackgroundDialogInHub = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("🎨 BG", fontSize = 9.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { engine.setArenaPreset("1:1") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("1:1 Reset", fontSize = 9.sp, color = Color(0xFF38BDF8))
                                }
                            }
                        }

                        BeyBattleDimensionsControls(engine = engine, state = state)

                        // Quick Toggle Goyang Layar & Retensi Gifter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { engine.setScreenShakeConfig(!state.isScreenShakeEnabled, state.screenShakeMultiplier) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.isScreenShakeEnabled) Color(0xFF7C3AED) else Color(0xFF334155)
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (state.isScreenShakeEnabled) "📳 Goyang: ON" else "📳 Goyang: OFF",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Button(
                                onClick = { engine.setRetainGiftersConfig(!state.retainGiftersAcrossRounds) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.retainGiftersAcrossRounds) Color(0xFF059669) else Color(0xFF334155)
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (state.retainGiftersAcrossRounds) "👑 Retensi: ON" else "👑 Retensi: OFF",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { engine.onLikeEvent("Penonton", count = 20) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("⚡ Like +20", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.onChatEvent("Penonton", "TABRAK!") },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("💬 Tabrak!", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.onGiftEvent("Sultan Paus", "Singa", 1, 500L) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("👑 Galaxy", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Tombol Aksi Cepat Host (Hujan Meteor, Badai & Papan Sultan)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { engine.triggerMeteorRain(4) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("☄️ Meteor 4x", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.toggleLeaderboardOverlay() },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.showLeaderboardOverlay) Color(0xFFEAB308) else Color(0xFF475569)
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (state.showLeaderboardOverlay) "🏆 Sultan: ON" else "🏆 Sultan",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Button(
                                onClick = { engine.resetRound() },
                                modifier = Modifier.weight(0.9f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("↺ Reset", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            )
        }

        if (showBackgroundDialogInHub) {
            BeyBattleBackgroundDialog(
                engine = engine,
                state = state,
                onDismiss = { showBackgroundDialogInHub = false }
            )
        }
    }
}

