package com.tajasb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan

/**
 * UnifiedGameComponents:
 * Komponen terpadu dan terstandarisasi untuk Pusat Kendali Game Interaktif TikTok Live.
 * Menyediakan layout seragam, tombol kompak (34dp) 1 kata, dan pedoman cara join penonton yang padat dan jelas.
 */

@Composable
fun GameJoinInstructionChips(
    likeAction: String,
    chatAction: String,
    giftAction: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Chip Like
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x33EF4444))
                .border(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(vertical = 4.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "❤️ $likeAction",
                color = Color(0xFFFCA5A5),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Chip Chat
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x338B5CF6))
                .border(0.8.dp, Color(0xFF8B5CF6).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(vertical = 4.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "💬 $chatAction",
                color = Color(0xFFDDD6FE),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Chip Gift
        Box(
            modifier = Modifier
                .weight(1.15f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x33F59E0B))
                .border(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(vertical = 4.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎁 $giftAction",
                color = Color(0xFFFDE68A),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StandardGameCard(
    gameIcon: String,
    gameTitle: String,
    gameSubtitle: String,
    isVcamActive: Boolean,
    onToggleVcam: () -> Unit,
    onLaunchFullScreen: () -> Unit,
    likeAction: String,
    chatAction: String,
    giftAction: String,
    statusText: String,
    isRunning: Boolean,
    onToggleRunning: () -> Unit,
    onReset: () -> Unit,
    botButtonLabel: String = "+BOT",
    onBotAction: () -> Unit,
    giftTestButtonLabel: String = "GIFT TES",
    onGiftTestAction: () -> Unit,
    onOpenSettings: () -> Unit,
    accentColor: Color = NeonCyan,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // 1. HEADER SERAGAM: NAMA GAME & 2 TOMBOL AKSI UTAMA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(gameIcon, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = gameTitle.uppercase(),
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = gameSubtitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp
                        )
                    }
                }

                // Dua Tombol Aksi Utama Seragam: FULLSCREEN & VCAM
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol Buka Layar Penuh Arena 60 FPS
                    Button(
                        onClick = onLaunchFullScreen,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("📺 FULLSCREEN", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    // Saklar VCam Overlay
                    Button(
                        onClick = onToggleVcam,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVcamActive) EmeraldGreen else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (isVcamActive) "VCAM: ON" else "VCAM: OFF",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 2. MODUL CARA JOIN PENONTON SERAGAM (3 Pill Chip Tanpa Teks Panjang)
            GameJoinInstructionChips(
                likeAction = likeAction,
                chatAction = chatAction,
                giftAction = giftAction
            )

            Spacer(Modifier.height(6.dp))

            // 3. BARIS STATUS MIKRO (Satu Baris Padat)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = statusText,
                    color = Color(0xFF38BDF8),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))

            // 4. ACTION BAR SERAGAM (5 Tombol Kompak 34dp 1 Kata + Ikon)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tombol 1: MULAI / JEDA
                Button(
                    onClick = onToggleRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFF59E0B) else EmeraldGreen
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1.05f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (isRunning) "⏸️ JEDA" else "▶️ MULAI",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Tombol 2: RESET
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(0.95f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("↺ RESET", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }

                // Tombol 3: +BOT
                Button(
                    onClick = onBotAction,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("🤖 $botButtonLabel", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }

                // Tombol 4: GIFT TES
                Button(
                    onClick = onGiftTestAction,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1.05f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("🎁 $giftTestButtonLabel", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // Tombol 5: ATUR (Buka Quick-Settings Dialog)
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(0.95f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("⚙️ ATUR", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun UnifiedGameSettingsDialog(
    gameTitle: String,
    onDismiss: () -> Unit,
    onShiftX: (Float) -> Unit,
    onShiftY: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    scalePercent: Int,
    customSettingsContent: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
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
                    text = "⚙️ ATUR: $gameTitle",
                    color = Color(0xFFFACC15),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(10.dp))

                // Bagian 1: Posisi Overlay X/Y
                Text(
                    text = "POSISI OVERLAY (X / Y)",
                    color = Color(0xFF94A3B8),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingStepperBtn("⬅️") { onShiftX(-0.04f) }
                    SettingStepperBtn("⬆️") { onShiftY(-0.04f) }
                    SettingStepperBtn("⬇️") { onShiftY(0.04f) }
                    SettingStepperBtn("➡️") { onShiftX(0.04f) }
                }

                Spacer(Modifier.height(10.dp))

                // Bagian 2: Ukuran Skala
                Text(
                    text = "UKURAN SKALA: $scalePercent%",
                    color = Color(0xFF94A3B8),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingStepperBtn("➖ KECIL") { onScaleChange(-0.1f) }
                    SettingStepperBtn("➕ BESAR") { onScaleChange(0.1f) }
                }

                // Bagian 3: Custom Content Game Spesifik
                if (customSettingsContent != null) {
                    Spacer(Modifier.height(10.dp))
                    customSettingsContent()
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(34.dp),
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
private fun SettingStepperBtn(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
