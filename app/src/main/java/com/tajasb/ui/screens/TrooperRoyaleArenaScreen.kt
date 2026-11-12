package com.tajasb.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.tajasb.game.InteractiveTrooperRoyaleEngine
import com.tajasb.ui.components.StandardGameCard
import com.tajasb.ui.components.UnifiedGameSettingsDialog
import com.tajasb.game.Top5DisplayConfig
import com.tajasb.game.Top5LeaderboardEntry
import com.tajasb.game.TrooperPlayer
import com.tajasb.game.TrooperWeaponTier
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * TrooperRoyaleFullScreenArenaOverlay:
 * Tampilan Layar Penuh 60 FPS untuk Game Interaktif "🎖️ Mini Trooper Royale (Perang Prajurit Chibi)".
 *
 * Fitur:
 * - Canvas 2D pertempuran: Chibi troopers dengan foto profil asli di helm, peluru, ledakan, gas safe zone.
 * - Komponen Top 5 Ultra-Fleksibel: Bebas digeser (drag & drop), diatur posisi X/Y, skala zoom, avatar, dan nama.
 * - Mini HUD Kontrol: Tombol 1 kata simbolik kompak dan pedoman padat tanpa teks panjang yang mengganggu.
 */
@Composable
fun TrooperRoyaleFullScreenArenaOverlay(
    onExitArena: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { InteractiveTrooperRoyaleEngine.getInstance(context) }
    val state by engine.state.collectAsState()

    var showTop5AdjustDialog by remember { mutableStateOf(false) }

    // Mencegah layar mati saat arena live dibuka
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalFlags = activity?.window?.attributes?.flags ?: 0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            if ((originalFlags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == 0) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    BackHandler {
        onExitArena()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        val arenaWidthPx = constraints.maxWidth.toFloat()
        val arenaHeightPx = constraints.maxHeight.toFloat()

        // 1. KANVAS PERTEMPURAN 60 FPS
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawBattlefieldArena(
                arenaW = size.width,
                arenaH = size.height,
                safeZoneRadius = state.safeZoneRadius,
                safeZoneCx = state.safeZoneCenterX,
                safeZoneCy = state.safeZoneCenterY
            )

            // Render Peluru
            val bullets = engine.getBulletsSnapshot()
            for (b in bullets) {
                val bx = b.x * size.width
                val by = b.y * size.height
                val color = try { Color(android.graphics.Color.parseColor(b.colorHex)) } catch (_: Throwable) { Color.Yellow }
                val r = (b.radius * size.width).coerceAtLeast(3f)

                drawCircle(
                    color = color,
                    radius = r,
                    center = Offset(bx, by)
                )

                // Jejak peluru
                drawLine(
                    color = color.copy(alpha = 0.5f),
                    start = Offset(bx - b.vx * 300f, by - b.vy * 300f),
                    end = Offset(bx, by),
                    strokeWidth = r * 0.8f
                )
            }

            // Render Ledakan
            val explosions = engine.getExplosionsSnapshot()
            for (exp in explosions) {
                val ex = exp.x * size.width
                val ey = exp.y * size.height
                val er = exp.currentRadius * size.width
                val color = try { Color(android.graphics.Color.parseColor(exp.colorHex)) } catch (_: Throwable) { Color.Red }

                drawCircle(
                    color = color.copy(alpha = exp.alpha * 0.4f),
                    radius = er,
                    center = Offset(ex, ey)
                )
                drawCircle(
                    color = color.copy(alpha = exp.alpha),
                    radius = er,
                    center = Offset(ex, ey),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // 2. LAYER ELEMEN CHIBI TROOPERS (AVATAR FOTO PROFIL ASLI DI HELM)
        val players = engine.getPlayersSnapshot()
        for (player in players) {
            if (player.isAlive) {
                TrooperChibiComposable(
                    player = player,
                    parentWidthPx = arenaWidthPx,
                    parentHeightPx = arenaHeightPx
                )
            }
        }

        // 3. KOMPONEN TOP 5 ULTRA-FLEKSIBEL (Bisa Digeser & Disesuaikan Host)
        if (state.top5Config.isVisible && state.top5List.isNotEmpty()) {
            FlexibleTop5Leaderboard(
                top5List = state.top5List,
                config = state.top5Config,
                parentWidthPx = arenaWidthPx,
                parentHeightPx = arenaHeightPx,
                onDragOffset = { dxPct, dyPct ->
                    engine.setTop5Position(
                        state.top5Config.posXpct + dxPct,
                        state.top5Config.posYpct + dyPct
                    )
                }
            )
        }

        // 4. HEADER MIKRO & PEDOMAN PADAT (Atas Layar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC0F172A), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🎖️ R-${state.currentRound}",
                        color = Color(0xFFFACC15),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "👥 ${state.totalAliveCount}/${state.totalPlayerCount} HIDUP",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "⏱️ ${String.format("%02d:%02d", state.roundTimeLeftSec / 60, state.roundTimeLeftSec % 60)}",
                        color = if (state.roundTimeLeftSec <= 30) Color(0xFFEF4444) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tombol Kustomisasi Top 5
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF8B5CF6))
                            .clickable { showTop5AdjustDialog = !showTop5AdjustDialog }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TOP 5 ⚙️",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    // Tombol Keluar Arena
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                            .clickable { onExitArena() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Keluar",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // PEDOMAN SIMPLE JELAS PADAT (1 Baris Ringkas)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(Color(0x99020617), RoundedCornerShape(6.dp))
                    .padding(vertical = 3.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "❤️ LIKE: TEMBAK | 💬 CHAT: HINDAR | 🎁 GIFT: BAZOOKA & TANK",
                    color = Color(0xFFE2E8F0),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 5. SELEBRASI JUARA 1 (VICTORY BANNER)
        if (state.isCelebratingVictory) {
            VictoryCelebrationOverlay(
                winnerName = state.winnerPlayerName,
                winnerAvatarBase64 = state.winnerAvatarBase64,
                winnerKills = state.winnerKills,
                weaponTitle = state.winnerWeaponTitle
            )
        }

        // 6. MINI HUD BOTTOM DOCK (Tombol 1 Kata Padat)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xEE0F172A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // MULAI / JEDA
                MiniActionButton(
                    text = if (state.isGameRunning) "JEDA" else "MULAI",
                    color = if (state.isGameRunning) Color(0xFFF59E0B) else EmeraldGreen,
                    onClick = { engine.toggleGameRunning() }
                )

                // RESET
                MiniActionButton(
                    text = "RESET",
                    color = Color(0xFF64748B),
                    onClick = { engine.resetGame() }
                )

                // +5 BOT TEST
                MiniActionButton(
                    text = "+5 BOT",
                    color = Color(0xFF0284C7),
                    onClick = { engine.spawnBotTroopers(5) }
                )

                // GIFT TEST
                MiniActionButton(
                    text = "GIFT TES",
                    color = Color(0xFFD97706),
                    onClick = { engine.simulateGiftTest() }
                )

                // SUARA
                MiniActionButton(
                    text = if (state.soundEnabled) "🔊 ON" else "🔇 OFF",
                    color = if (state.soundEnabled) Color(0xFF8B5CF6) else Color(0xFF475569),
                    onClick = { engine.setSoundEnabled(!state.soundEnabled) }
                )
            }
        }

        // 7. DIALOG PENGATUR TOP 5 ULTRA-FLEKSIBEL (GESER, UKURAN, ELEMEN)
        if (showTop5AdjustDialog) {
            Top5CustomizerDialog(
                config = state.top5Config,
                onDismiss = { showTop5AdjustDialog = false },
                onShiftX = { dx -> engine.setTop5Position(state.top5Config.posXpct + dx, state.top5Config.posYpct) },
                onShiftY = { dy -> engine.setTop5Position(state.top5Config.posXpct, state.top5Config.posYpct + dy) },
                onScaleChange = { ds -> engine.setTop5Scale(state.top5Config.scale + ds) },
                onToggleHorizontal = { engine.setTop5Orientation(!state.top5Config.isHorizontal) },
                onToggleAvatar = {
                    engine.setTop5ElementVisibility(
                        showAvatar = !state.top5Config.showAvatar,
                        showName = state.top5Config.showName,
                        showKills = state.top5Config.showKills,
                        showScore = state.top5Config.showScore
                    )
                },
                onToggleName = {
                    engine.setTop5ElementVisibility(
                        showAvatar = state.top5Config.showAvatar,
                        showName = !state.top5Config.showName,
                        showKills = state.top5Config.showKills,
                        showScore = state.top5Config.showScore
                    )
                },
                onToggleKills = {
                    engine.setTop5ElementVisibility(
                        showAvatar = state.top5Config.showAvatar,
                        showName = state.top5Config.showName,
                        showKills = !state.top5Config.showKills,
                        showScore = state.top5Config.showScore
                    )
                },
                onToggleVisibility = { engine.setTop5Visible(!state.top5Config.isVisible) }
            )
        }
    }
}

// =============================================================================
// SUB-KOMPONEN PRAJURIT CHIBI DENGAN FOTO PROFIL ASLI DI HELM
// =============================================================================

@Composable
private fun TrooperChibiComposable(
    player: TrooperPlayer,
    parentWidthPx: Float,
    parentHeightPx: Float
) {
    val px = player.x * parentWidthPx
    val py = player.y * parentHeightPx

    val decodedAvatar = remember(player.avatarBase64) {
        if (player.avatarBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(player.avatarBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Throwable) { null }
        } else null
    }

    val teamColor = try {
        Color(android.graphics.Color.parseColor(player.colorHex))
    } catch (_: Throwable) { Color(0xFF06B6D4) }

    Box(
        modifier = Modifier
            .offset { IntOffset(px.roundToInt() - 40, py.roundToInt() - 50) }
            .size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // NAMA & HP BAR MIKRO
            Row(
                modifier = Modifier
                    .background(Color(0xCC020617), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = player.name.take(7),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (player.kills > 0) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "⭐${player.kills}",
                        color = Color(0xFFFACC15),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(1.dp))

            // Bar HP
            val hpFraction = (player.hp / player.maxHp).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(3.dp)
                    .background(Color(0xFF334155), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(hpFraction)
                        .height(3.dp)
                        .background(
                            if (hpFraction > 0.5f) EmeraldGreen else Color(0xFFEF4444),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            Spacer(Modifier.height(2.dp))

            // KARAKTER CHIBI BERHELM + FOTO PROFIL
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                // Helm Pelindung
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(teamColor.copy(alpha = 0.85f))
                        .border(1.5.dp, if (player.weaponTier.tierLevel >= 2) Color(0xFFFACC15) else Color.White, CircleShape)
                )

                // Foto Profil Penonton di dalam Helm
                if (decodedAvatar != null) {
                    Image(
                        bitmap = decodedAvatar,
                        contentDescription = player.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = player.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Moncong Senjata (Mengikuti arah tembak)
                val rad = Math.toRadians(player.angleDeg.toDouble())
                val gunOffsetX = (cos(rad) * 16.0).dp
                val gunOffsetY = (sin(rad) * 16.0).dp

                Box(
                    modifier = Modifier
                        .offset(x = gunOffsetX, y = gunOffsetY)
                        .size(if (player.weaponTier == TrooperWeaponTier.TIER_2_BAZOOKA) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            when (player.weaponTier) {
                                TrooperWeaponTier.TIER_0_PISTOL -> Color(0xFFFACC15)
                                TrooperWeaponTier.TIER_1_SHOTGUN -> Color(0xFF38BDF8)
                                TrooperWeaponTier.TIER_2_BAZOOKA -> Color(0xFFEF4444)
                                TrooperWeaponTier.TIER_3_TANK -> Color(0xFF10B981)
                                TrooperWeaponTier.TIER_4_AIRSTRIKE -> Color(0xFFA855F7)
                            }
                        )
                        .border(0.5.dp, Color.White, CircleShape)
                )
            }
        }
    }
}

// =============================================================================
// SUB-KOMPONEN TOP 5 ULTRA-FLEKSIBEL (POSISI, UKURAN, AVATAR, NAMA)
// =============================================================================

@Composable
private fun FlexibleTop5Leaderboard(
    top5List: List<Top5LeaderboardEntry>,
    config: Top5DisplayConfig,
    parentWidthPx: Float,
    parentHeightPx: Float,
    onDragOffset: (Float, Float) -> Unit
) {
    val posX = (config.posXpct * parentWidthPx).roundToInt()
    val posY = (config.posYpct * parentHeightPx).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(posX, posY) }
            .scale(config.scale)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val dxPct = dragAmount.x / parentWidthPx
                    val dyPct = dragAmount.y / parentHeightPx
                    onDragOffset(dxPct, dyPct)
                }
            }
            .background(Color(0xD90F172A), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(6.dp)
    ) {
        if (config.isHorizontal) {
            // FORMAT HORIZONTAL (1 Baris Menyamping)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (entry in top5List) {
                    Top5ItemCell(entry = entry, config = config, isCompact = true)
                }
            }
        } else {
            // FORMAT VERTIKAL (1 Kolom Bertingkat)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "🏆 TOP 5 PRAJURIT",
                    color = Color(0xFFFACC15),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                for (entry in top5List) {
                    Top5ItemCell(entry = entry, config = config, isCompact = false)
                }
            }
        }
    }
}

@Composable
private fun Top5ItemCell(
    entry: Top5LeaderboardEntry,
    config: Top5DisplayConfig,
    isCompact: Boolean
) {
    val decoded = remember(entry.avatarBase64) {
        if (entry.avatarBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(entry.avatarBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Throwable) { null }
        } else null
    }

    val rankColor = when (entry.rank) {
        1 -> Color(0xFFFACC15) // Emas
        2 -> Color(0xFFE2E8F0) // Perak
        3 -> Color(0xFFF97316) // Perunggu
        else -> Color(0xFF94A3B8)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0x661E293B), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // Badge Rank
        Text(
            text = "#${entry.rank}",
            color = rankColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black
        )

        // Foto Profil (Bisa dimatikan)
        if (config.showAvatar) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155))
                    .border(1.dp, rankColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (decoded != null) {
                    Image(
                        bitmap = decoded,
                        contentDescription = entry.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = entry.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Nama Pemain (Bisa dimatikan)
        if (config.showName) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = entry.name.take(6),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        // Kills / Skor (Bisa dimatikan)
        if (config.showKills) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "⚔️${entry.kills}",
                color = Color(0xFFEF4444),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =============================================================================
// SUB-KOMPONEN DIALOG PENGATUR TOP 5 ULTRA-FLEKSIBEL
// =============================================================================

@Composable
private fun Top5CustomizerDialog(
    config: Top5DisplayConfig,
    onDismiss: () -> Unit,
    onShiftX: (Float) -> Unit,
    onShiftY: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onToggleHorizontal: () -> Unit,
    onToggleAvatar: () -> Unit,
    onToggleName: () -> Unit,
    onToggleKills: () -> Unit,
    onToggleVisibility: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(310.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚙️ ATUR TAMPILAN TOP 5",
                    color = Color(0xFFFACC15),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(10.dp))

                // 1. KONTROL POSISI GESER
                Text(
                    text = "GESER POSISI (X / Y)",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniStepperBtn("⬅️") { onShiftX(-0.04f) }
                    MiniStepperBtn("⬆️") { onShiftY(-0.04f) }
                    MiniStepperBtn("⬇️") { onShiftY(0.04f) }
                    MiniStepperBtn("➡️") { onShiftX(0.04f) }
                }

                Spacer(Modifier.height(10.dp))

                // 2. KONTROL UKURAN ZOOM
                Text(
                    text = "UKURAN SKALA: ${(config.scale * 100).toInt()}%",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniStepperBtn("➖ KECIL") { onScaleChange(-0.1f) }
                    MiniStepperBtn("➕ BESAR") { onScaleChange(0.1f) }
                }

                Spacer(Modifier.height(10.dp))

                // 3. TOGGLE ELEMEN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ToggleChip(
                        label = if (config.showAvatar) "FOTO: ON" else "FOTO: OFF",
                        active = config.showAvatar,
                        onClick = onToggleAvatar
                    )
                    ToggleChip(
                        label = if (config.showName) "NAMA: ON" else "NAMA: OFF",
                        active = config.showName,
                        onClick = onToggleName
                    )
                    ToggleChip(
                        label = if (config.showKills) "KILLS: ON" else "KILLS: OFF",
                        active = config.showKills,
                        onClick = onToggleKills
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ToggleChip(
                        label = if (config.isHorizontal) "LAYOUT: BARIS" else "LAYOUT: KOLOM",
                        active = config.isHorizontal,
                        onClick = onToggleHorizontal
                    )
                    ToggleChip(
                        label = if (config.isVisible) "TOP 5: TAMPIL" else "TOP 5: SEMBUNYI",
                        active = config.isVisible,
                        onClick = onToggleVisibility
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Tombol Selesai
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("SELESAI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MiniStepperBtn(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Color(0xFF0284C7) else Color(0xFF334155))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MiniActionButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

// =============================================================================
// SELEBRASI JUARA VICTORY ROYALE
// =============================================================================

@Composable
private fun VictoryCelebrationOverlay(
    winnerName: String,
    winnerAvatarBase64: String,
    winnerKills: Int,
    weaponTitle: String
) {
    val decoded = remember(winnerAvatarBase64) {
        if (winnerAvatarBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(winnerAvatarBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Throwable) { null }
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.width(280.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFACC15))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🏆 VICTORY ROYALE! 🏆",
                    color = Color(0xFFFACC15),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFACC15))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (decoded != null) {
                        Image(
                            bitmap = decoded,
                            contentDescription = winnerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("👑", fontSize = 28.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = winnerName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Text(
                    text = "⚔️ ${winnerKills} KILLS • Senjata: ${weaponTitle}",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "JUARA 1 SULTAN PERANG!",
                    color = Color(0xFF10B981),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// =============================================================================
// KANVAS DRAWING ARENA PERTEMPURAN & SAFE ZONE
// =============================================================================

private fun DrawScope.drawBattlefieldArena(
    arenaW: Float,
    arenaH: Float,
    safeZoneRadius: Float,
    safeZoneCx: Float,
    safeZoneCy: Float
) {
    // Background Taktis Hangar Gelap
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
            center = Offset(arenaW * 0.5f, arenaH * 0.5f),
            radius = arenaW * 0.7f
        )
    )

    // Grid garis lantai taktis
    val step = 40.dp.toPx()
    var x = 0f
    while (x < arenaW) {
        drawLine(
            color = Color(0xFF334155).copy(alpha = 0.2f),
            start = Offset(x, 0f),
            end = Offset(x, arenaH),
            strokeWidth = 1f
        )
        x += step
    }
    var y = 0f
    while (y < arenaH) {
        drawLine(
            color = Color(0xFF334155).copy(alpha = 0.2f),
            start = Offset(0f, y),
            end = Offset(arenaW, y),
            strokeWidth = 1f
        )
        y += step
    }

    // LINGKARAN GAS RACUN (SAFE ZONE)
    val cx = safeZoneCx * arenaW
    val cy = safeZoneCy * arenaH
    val radiusPx = safeZoneRadius * arenaW

    // Area luar merah transparan (gas racun)
    drawRect(
        color = Color(0xFFEF4444).copy(alpha = 0.08f)
    )

    // Garis batas neon Safe Zone
    drawCircle(
        color = Color(0xFF06B6D4),
        radius = radiusPx,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5.dp.toPx())
    )
}

// =============================================================================
// KONTEN KONTROLER COMPACT UNTUK FLOATING CONTROL / MAIN SCREEN
// =============================================================================

@Composable
fun TrooperRoyaleControllerContent(
    onLaunchFullScreenArena: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { InteractiveTrooperRoyaleEngine.getInstance(context) }
    val state by engine.state.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // GAME 4: MINI TROOPER ROYALE — KARTU SERAGAM COMPACT
        StandardGameCard(
            gameIcon = "🎖️",
            gameTitle = "Mini Trooper Royale",
            gameSubtitle = "Perang Chibi Saling Tembak Live",
            isVcamActive = state.isEnabled,
            onToggleVcam = {
                val next = !state.isEnabled
                engine.setEnabled(next)
                android.widget.Toast.makeText(
                    context,
                    if (next) "🎖️ VCam Trooper: ON" else "VCam Trooper: OFF",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onLaunchFullScreen = onLaunchFullScreenArena,
            likeAction = "Tembak Biasa",
            chatAction = "Chat (+Peluru)",
            giftAction = "Senjata Super",
            statusText = "👥 ${state.totalAliveCount}/${state.totalPlayerCount} Prajurit Hidup • Safe Zone: ${(state.safeZoneRadius * 100).toInt()}% • Top 5: ${if (state.top5Config.isVisible) "Tampil" else "Sembunyi"}",
            isRunning = state.isGameActive && state.isGameRunning,
            onToggleRunning = {
                if (state.totalPlayerCount == 0) {
                    engine.spawnBotTroopers(5)
                } else if (state.isGameActive) {
                    engine.toggleGameRunning()
                }
            },
            onReset = { engine.resetGame() },
            botButtonLabel = "+5 BOT",
            onBotAction = { engine.spawnBotTroopers(5) },
            giftTestButtonLabel = "GIFT TES",
            onGiftTestAction = { engine.simulateGiftTest() },
            onOpenSettings = { showSettingsDialog = true },
            accentColor = Color(0xFF10B981)
        )

        if (showSettingsDialog) {
            UnifiedGameSettingsDialog(
                gameTitle = "Pengaturan Mini Trooper",
                onDismiss = { showSettingsDialog = false },
                onShiftX = { delta ->
                    val cfg = state.top5Config
                    engine.setTop5Position((cfg.posXpct + delta).coerceIn(0f, 0.85f), cfg.posYpct)
                },
                onShiftY = { delta ->
                    val cfg = state.top5Config
                    engine.setTop5Position(cfg.posXpct, (cfg.posYpct + delta).coerceIn(0f, 0.85f))
                },
                onScaleChange = { deltaScale ->
                    val cfg = state.top5Config
                    engine.setTop5Scale((cfg.scale + deltaScale).coerceIn(0.5f, 2.0f))
                },
                scalePercent = (state.top5Config.scale * 100).toInt(),
                customSettingsContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "🏆 Papan Juara TOP 5 Live:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    engine.setTop5Visible(!state.top5Config.isVisible)
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.top5Config.isVisible) Color(0xFF059669) else Color(0xFF475569)
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (state.top5Config.isVisible) "TOP 5: TAMPIL" else "TOP 5: SEMBUNYI",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = {
                                    val cfg = state.top5Config
                                    engine.setTop5ElementVisibility(!cfg.showAvatar, cfg.showName, cfg.showKills, cfg.showScore)
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (state.top5Config.showAvatar) "Foto: ON" else "Foto: OFF",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = {
                                    engine.setTop5Position(0.03f, 0.12f)
                                    engine.setTop5Scale(1.0f)
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Reset Posisi", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "💡 Top 5 bisa digeser/drag langsung di layar HP saat live!",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { engine.spawnBotTroopers(10) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+10 Prajurit", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.simulateGiftTest() },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Tes Bazooka", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { engine.resetGame() },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Reset Game", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            )
        }
    }
}
