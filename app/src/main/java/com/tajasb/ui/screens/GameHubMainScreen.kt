package com.tajasb.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.tajasb.eventrelay.ConnectionStatus
import com.tajasb.eventrelay.LiveEventPreferences
import com.tajasb.eventrelay.LiveEventReceiver
import com.tajasb.interaction.UniversalTriggerEngine
import com.tajasb.ui.VcamViewModel
import com.tajasb.ui.components.AppUpdateDialog
import com.tajasb.ui.components.HeaderUpdateApkChip
import com.tajasb.ui.components.LiveEventBrokerCard
import com.tajasb.ui.components.SystemTabUpdateApkCard
import com.tajasb.updater.AppUpdateManager

private fun copyToClipboard(context: Context, label: String, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "✅ Perintah disalin ke clipboard", Toast.LENGTH_SHORT).show()
    } catch (_: Throwable) {
        Toast.makeText(context, "Gagal menyalin", Toast.LENGTH_SHORT).show()
    }
}

/**
 * GameHubMainScreen
 * Layar Utama Eksklusif: Mobile Legends (MLBB), Termux Event Relay, dan update.sh
 */
@Composable
fun GameHubMainScreen(
    viewModel: VcamViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val receiver = remember { LiveEventReceiver.getInstance(context) }
    val brokerPrefs = remember { LiveEventPreferences.getInstance(context) }
    val universalEngine = remember { UniversalTriggerEngine.getInstance(context) }

    val connStatus by receiver.connectionStatus.collectAsState()
    val activeTab by viewModel.selectedMainTab.collectAsState()
    val universalState by universalEngine.state.collectAsState()

    var showUpdateDialog by remember { mutableStateOf(false) }

    val isConnected = connStatus == ConnectionStatus.CONNECTED

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // =====================================================================
        // 1. TOP HEADER APP BAR (Minimalis, Kompak & Anti-Wrapping)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "⚔️ MLBB LIVE STUDIO",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonCyan.copy(alpha = 0.2f))
                            .border(0.6.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "TERMUX EVENT",
                            color = NeonCyan,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = "TikTok & Bigo Relay",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Status Chip Termux (Klik untuk langsung ke Tab Termux)
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isConnected) EmeraldGreen.copy(alpha = 0.18f) else DarkSurfaceVariant)
                        .border(
                            0.8.dp,
                            if (isConnected) EmeraldGreen else DarkCardBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { viewModel.setSelectedMainTab(1) }
                        .testTag("header_termux_status_chip")
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) EmeraldGreen else Color(0xFFF59E0B))
                        )
                        Text(
                            text = if (isConnected) "Online" else "Siaga",
                            color = if (isConnected) EmeraldGreen else Color(0xFFCBD5E1),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Badge Kompak Layar Selalu Hidup (Screen Awake Indicator)
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x33F59E0B))
                        .border(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable {
                            Toast.makeText(context, "☀️ Layar tetap aktif saat aplikasi berjalan", Toast.LENGTH_SHORT).show()
                        }
                        .testTag("header_screen_awake_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "☀️",
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Header Update APK Chip
                HeaderUpdateApkChip(onClick = { showUpdateDialog = true })
            }
        }

        // =====================================================================
        // 2. MAIN NAVIGATION TABS (3 TAB FOKUS: MLBB, TERMUX, UPDATE)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface)
                .border(0.8.dp, DarkCardBorder, RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val mlbbBadge = if (!universalState.isEngineEnabled) "OFF"
            else "${universalState.rules.count { it.enabled }} Rule"
            val tabs = listOf(
                Triple("⚔️ MLBB", mlbbBadge, "main_tab_mlbb"),
                Triple("📟 Termux", if (isConnected) "Online" else "LAN", "main_tab_termux"),
                Triple("🚀 Update", "v" + AppUpdateManager.resolveInstalledVersion(context), "main_tab_update")
            )

            tabs.forEachIndexed { index, (label, badge, tag) ->
                val isSelected = activeTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (isSelected) NeonCyan.copy(alpha = 0.16f) else Color.Transparent
                        )
                        .border(
                            width = if (isSelected) 0.8.dp else 0.dp,
                            color = if (isSelected) NeonCyan.copy(alpha = 0.6f) else Color.Transparent,
                            shape = RoundedCornerShape(7.dp)
                        )
                        .clickable { viewModel.setSelectedMainTab(index) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag(tag),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = badge,
                            fontSize = 7.5.sp,
                            color = if (isSelected) NeonCyan else Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // =====================================================================
        // 3. TAB CONTENT
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            when (activeTab) {
                0 -> UniversalInteractionScreen()
                1 -> TermuxRelayTabContent(
                    receiver = receiver,
                    brokerPrefs = brokerPrefs
                )
                2 -> UpdateShTabContent(
                    onOpenUpdateDialog = { showUpdateDialog = true }
                )
            }
        }
    }

    if (showUpdateDialog) {
        AppUpdateDialog(onDismiss = { showUpdateDialog = false })
    }
}

/**
 * Tab 1: TERMUX LIVE RELAY
 * Kontrol koneksi Termux, platform TikTok / Bigo, test injection, dan log event terminal
 */
@Composable
private fun TermuxRelayTabContent(
    receiver: LiveEventReceiver,
    brokerPrefs: LiveEventPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Panduan Perintah Termux Ringkas (Quick-Copy)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "📟 PERINTAH TERMUX (SALIN CEPAT)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Port: 8765",
                        color = EmeraldGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Jalankan perintah ini di Termux (Device 1) untuk membaca stream live dan meneruskannya ke MLBB Live Studio:",
                    fontSize = 8.5.sp,
                    color = Color(0xFFCBD5E1)
                )

                TermuxCommandRow(
                    label = "1. Jalankan TikTok Live Server:",
                    cmd = "python tools/tiktok_event_server.py",
                    onCopy = { copyToClipboard(context, "TikTok Server", "python tools/tiktok_event_server.py") }
                )

                TermuxCommandRow(
                    label = "2. Jalankan Bigo Live Server:",
                    cmd = "python tools/bigo_event_server.py",
                    onCopy = { copyToClipboard(context, "Bigo Server", "python tools/bigo_event_server.py") }
                )

                TermuxCommandRow(
                    label = "3. Eksekusi Script Update Pipeline:",
                    cmd = "bash update.sh",
                    onCopy = { copyToClipboard(context, "Update Script", "bash update.sh") }
                )
            }
        }

        // Kamus Koin Hadiah TikTok (Dropdown Terkecil ke Terbanyak)
        TikTokGiftCoinDropdownCard(modifier = Modifier.fillMaxWidth())

        // Live Event Broker Card (Koneksi, Saklar Event, Tes Injeksi, Log Event Realtime)
        LiveEventBrokerCard(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Tab 2: UPDATE.SH & APK UPDATER
 * Mengelola update APK otomatis dan panduan eksekusi update.sh di Termux
 */
@Composable
private fun UpdateShTabContent(
    onOpenUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateManager = remember { AppUpdateManager.getInstance(context) }
    val updateState by updateManager.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Panduan update.sh di Termux
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🚀 PIPELINE UPDATE.SH (TERMUX)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(EmeraldGreen.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "v${updateState.currentVersion}",
                            color = EmeraldGreen,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Berkas update.sh terletak di direktori root aplikasi. Script ini dirancang untuk dijalankan langsung di lingkungan Termux Android untuk pembaruan instan tanpa jejak:",
                    fontSize = 9.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 12.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF020617))
                        .border(0.6.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "$ cd ~ && bash update.sh",
                            color = EmeraldGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Mendeteksi rilis APK terbaru otomatis\n• Mendukung self-update script mandiri\n• Sinkronisasi commit Git dan repositori privat",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            lineHeight = 11.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, "Perintah update.sh", "bash update.sh")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(7.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salin Perintah", fontSize = 9.5.sp, color = NeonCyan)
                    }

                    Button(
                        onClick = onOpenUpdateDialog,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(7.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cek Update APK", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Komponen Updater APK Bawaan (SystemTabUpdateApkCard)
        SystemTabUpdateApkCard(
            onOpenUpdateDialog = onOpenUpdateDialog,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TermuxCommandRow(
    label: String,
    cmd: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.6f))
            .border(0.6.dp, DarkCardBorder, RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = cmd,
                color = NeonCyan,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkSurface)
                    .clickable { onCopy() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Salin",
                    color = Color.White,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class GiftCoinEntry(
    val coins: String,
    val gifts: String,
    val iconEmoji: String,
    val iconUrl: String
)

@Composable
fun TikTokGiftCoinDropdownCard(
    modifier: Modifier = Modifier
) {
    var isDismissed by remember { mutableStateOf(false) }

    if (isDismissed) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(0.8.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { isDismissed = false }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "💎 Buka Kamus Koin & Ikon TikTok",
                    color = NeonCyan,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

    val giftCoinList = remember {
        listOf(
            GiftCoinEntry("1 💎", "🌹 Mawar, 🎵 TikTok, 💖 Heart Me, ☕ Kopi, 🍦 Es Krim, 🦆 Bebek, ❤️ Love/Hati", "🌹", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/563a6a9b49f993d9370773d573c38b29.png~tplv-obj.png"),
            GiftCoinEntry("5 💎", "🫶 Jari Hati (Finger Heart), 🐼 Panda", "🫶", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_finger_heart.png~tplv-obj.png"),
            GiftCoinEntry("10 💎", "🍬 Permen (Lollipop), 🌾 Bunga Kering, 🕊️ Origami", "🍬", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_lollipop.png~tplv-obj.png"),
            GiftCoinEntry("20 💎", "💄 Parfum (Perfume)", "💄", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_perfume.png~tplv-obj.png"),
            GiftCoinEntry("30 💎", "🍩 Donat (Doughnut)", "🍩", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_doughnut.png~tplv-obj.png"),
            GiftCoinEntry("99 💎", "🧢 Topi & Kumis, 👑 Mahkota (Crown), ⭐️ Bintang (Star)", "👑", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_crown.png~tplv-obj.png"),
            GiftCoinEntry("100 💎", "🫶 Hand Heart (Hati Tangan), 🎊 Konfeti (Confetti)", "🎊", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_hand_heart.png~tplv-obj.png"),
            GiftCoinEntry("199 💎", "🕶️ Kacamata (Sunglasses)", "🕶️", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_sunglasses.png~tplv-obj.png"),
            GiftCoinEntry("299 💎", "🐶 Corgi", "🐶", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_corgi.png~tplv-obj.png"),
            GiftCoinEntry("300 💎", "🍚 Nasi Tumpeng (Tumpeng)", "🍚", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_tumpeng.png~tplv-obj.png"),
            GiftCoinEntry("399 💎", "🎙️ Mic (Microphone)", "🎙️", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_microphone.png~tplv-obj.png"),
            GiftCoinEntry("500 💎", "🎸 Gitar (Guitar)", "🎸", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_guitar.png~tplv-obj.png"),
            GiftCoinEntry("699 💎", "🦢 Angsa (Swan)", "🦢", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_swan.png~tplv-obj.png"),
            GiftCoinEntry("899 💎", "🚂 Kereta Api (Train)", "🚂", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_train.png~tplv-obj.png"),
            GiftCoinEntry("1.000 💎", "🌌 Galaxy, 🌠 Bintang Jatuh, ☄️ Komet, 🏍️ Motor Balap", "🌌", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_galaxy.png~tplv-obj.png"),
            GiftCoinEntry("1.088 💎", "🎆 Kembang Api (Fireworks)", "🎆", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_fireworks.png~tplv-obj.png"),
            GiftCoinEntry("2.150 💎", "🐋 Paus (Whale / Menyelam / Terbang / Samudra)", "🐋", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_whale.png~tplv-obj.png"),
            GiftCoinEntry("4.888 💎", "🛩️ Pesawat Jet (Private Jet)", "🛩️", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_jet.png~tplv-obj.png"),
            GiftCoinEntry("4.999 💎", "🚁 Helikopter (Helicopter)", "🚁", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_helicopter.png~tplv-obj.png"),
            GiftCoinEntry("5.000 💎", "🌙 Moon (Bulan)", "🌙", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_moon.png~tplv-obj.png"),
            GiftCoinEntry("7.000 💎", "🏎️ Mobil Balap (Sports Car)", "🏎️", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_car.png~tplv-obj.png"),
            GiftCoinEntry("9.888 💎", "🛥️ Yacht (Kapal Pesiar)", "🛥️", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_yacht.png~tplv-obj.png"),
            GiftCoinEntry("9.999 💎", "🦅 Garuda", "🦅", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_garuda.png~tplv-obj.png"),
            GiftCoinEntry("10.000 💎", "☀️ Matahari (Sun / Interstellar)", "☀️", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_sun.png~tplv-obj.png"),
            GiftCoinEntry("10.999 💎", "🦅 Falcon (Elang)", "🦅", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_falcon.png~tplv-obj.png"),
            GiftCoinEntry("15.000 💎", "🪐 Planet", "🪐", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_planet.png~tplv-obj.png"),
            GiftCoinEntry("20.000 💎", "🏰 Kastil Fantasi (Castle), ✈️ Pesawat", "🏰", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_castle.png~tplv-obj.png"),
            GiftCoinEntry("29.999 💎", "🦁 Singa (Lion), 🏎️ Supercar", "🦁", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_lion.png~tplv-obj.png"),
            GiftCoinEntry("34.999 💎", "🌌 TikTok Universe", "🌌", "https://p16-webcast.tiktokcdn.com/img/maliva/webcast-va/resource/gift_universe.png~tplv-obj.png")
        )
    }

    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "💎 KAMUS KOIN & IKON HADIAH TIKTOK",
                        color = NeonCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Terkecil ➔ Terbanyak",
                            color = NeonCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { isDismissed = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup Kamus",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Klik dropdown di bawah untuk melihat nama gift dan ikon asli sesuai koin:",
                fontSize = 8.5.sp,
                color = Color(0xFFCBD5E1)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dropdown Selector Box dengan Ikon Visual
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Badge Ikon Visual Gift Utama (Image + Emoji Fallback)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.2f))
                                .border(0.8.dp, NeonCyan, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val selectedEntry = giftCoinList[selectedIndex]
                            AsyncImage(
                                model = selectedEntry.iconUrl,
                                contentDescription = selectedEntry.gifts,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = selectedEntry.iconEmoji,
                                fontSize = 16.sp
                            )
                        }

                        Column {
                            Text(
                                text = giftCoinList[selectedIndex].coins,
                                color = EmeraldGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = giftCoinList[selectedIndex].gifts,
                                color = Color.White,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown Menu",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .background(DarkSurface)
                        .border(1.dp, NeonCyan, RoundedCornerShape(8.dp))
                ) {
                    giftCoinList.forEachIndexed { index, entry ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkSurfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = entry.iconUrl,
                                            contentDescription = entry.gifts,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = entry.iconEmoji,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = entry.coins,
                                            color = EmeraldGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = entry.gifts,
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                selectedIndex = index
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
