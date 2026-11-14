package com.tajasb.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.tajasb.eventrelay.ConnectionStatus
import com.tajasb.eventrelay.LiveEventItem
import com.tajasb.eventrelay.LiveEventPreferences
import com.tajasb.eventrelay.LiveEventReceiver
import com.tajasb.eventrelay.LiveEventType

@Composable
fun LiveEventBrokerCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val receiver = remember { LiveEventReceiver.getInstance(context) }
    val prefs = remember { LiveEventPreferences.getInstance(context) }

    val config by prefs.config.collectAsState()
    val connStatus by receiver.connectionStatus.collectAsState()
    val connDetails by receiver.connectionDetails.collectAsState()
    val lastEvent by receiver.lastEvent.collectAsState()
    val viewerCount by receiver.viewerCount.collectAsState()
    val totalReceived by receiver.totalReceivedCount.collectAsState()
    val termuxLogs by receiver.termuxLogs.collectAsState()
    val totalOrders by receiver.totalOrdersAccumulated.collectAsState()

    var hostInput by remember(config.serverHost) { mutableStateOf(config.serverHost) }
    var isExpanded by remember { mutableStateOf(true) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val isConnected = connStatus == ConnectionStatus.CONNECTED
    val isConnecting = connStatus == ConnectionStatus.CONNECTING

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_live_event_broker"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isConnected) EmeraldGreen.copy(alpha = 0.5f) else DarkCardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(9.dp)
        ) {
            // Header: Judul & Status Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isConnected) EmeraldGreen.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (isConnected) EmeraldGreen else NeonCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "⚡ LIVE EVENT BROKER",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Device 1 (Termux) ➔ Device 2 (MLBB)",
                            fontSize = 8.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Badge Status
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            when (connStatus) {
                                ConnectionStatus.CONNECTED -> EmeraldGreen.copy(alpha = 0.2f)
                                ConnectionStatus.CONNECTING -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                else -> Color(0xFFEF4444).copy(alpha = 0.15f)
                            }
                        )
                        .border(
                            0.6.dp,
                            when (connStatus) {
                                ConnectionStatus.CONNECTED -> EmeraldGreen.copy(alpha = 0.6f)
                                ConnectionStatus.CONNECTING -> Color(0xFFF59E0B).copy(alpha = 0.6f)
                                else -> Color(0xFFEF4444).copy(alpha = 0.5f)
                            },
                            RoundedCornerShape(5.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when (connStatus) {
                            ConnectionStatus.CONNECTED -> "🟢 TERHUBUNG"
                            ConnectionStatus.CONNECTING -> "🟡 MENGHUBUNGKAN..."
                            ConnectionStatus.ERROR -> "🔴 ERROR"
                            ConnectionStatus.DISCONNECTED -> "⚪ TERPUTUS"
                        },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (connStatus) {
                            ConnectionStatus.CONNECTED -> EmeraldGreen
                            ConnectionStatus.CONNECTING -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    // Penjelasan Konsep Modern
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .padding(7.dp)
                    ) {
                        Text(
                            text = "💡 Bebas scan layar & zero OCR. Device 1 membaca stream Webcast TikTok / Bigo Live langsung dari server, lalu meneruskannya ke Device 2 via WebSocket LAN (<5ms latency).",
                            fontSize = 8.5.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    // Selector Platform: TikTok Live vs Bigo Live
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isTikTok = config.activePlatform != "BIGO"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isTikTok) NeonCyan.copy(alpha = 0.22f) else Color.Transparent)
                                .border(
                                    0.8.dp,
                                    if (isTikTok) NeonCyan else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { receiver.switchPlatform("TIKTOK") }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🎵 TikTok Live",
                                fontSize = 10.sp,
                                fontWeight = if (isTikTok) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTikTok) NeonCyan else Color(0xFF94A3B8)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isTikTok) Color(0xFFF59E0B).copy(alpha = 0.22f) else Color.Transparent)
                                .border(
                                    0.8.dp,
                                    if (!isTikTok) Color(0xFFF59E0B) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { receiver.switchPlatform("BIGO") }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🫘 Bigo Live",
                                fontSize = 10.sp,
                                fontWeight = if (!isTikTok) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isTikTok) Color(0xFFF59E0B) else Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    // Input Alamat IP Server Device 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text("IP Server Device 1 (Termux)", fontSize = 9.sp) },
                            placeholder = { Text("192.168.43.1", fontSize = 9.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    prefs.updateServerAddress(hostInput)
                                    receiver.connect()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = NeonCyan,
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_broker_ip")
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                prefs.updateServerAddress(hostInput)
                                receiver.toggleConnection()
                            },
                            shape = RoundedCornerShape(7.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) Color(0xFFDC2626) else EmeraldGreen
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier
                                .testTag("btn_toggle_broker_connection")
                        ) {
                            Text(
                                text = if (isConnected) "Putus" else "Hubungkan",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Input Target Host / Bigo ID
                    val isBigoMode = config.activePlatform == "BIGO"
                    var targetInput by remember(isBigoMode, config.targetUsername, config.targetBigoId) {
                        mutableStateOf(if (isBigoMode) config.targetBigoId else config.targetUsername)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = targetInput,
                            onValueChange = { targetInput = it },
                            label = { Text(if (isBigoMode) "Target Bigo ID / Room ID" else "Target Username TikTok", fontSize = 9.sp) },
                            placeholder = { Text(if (isBigoMode) "bigoid_official" else "makkahlive", fontSize = 9.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    if (isBigoMode) {
                                        receiver.switchBigoTargetId(targetInput)
                                    } else {
                                        receiver.switchTargetUsername(targetInput)
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isBigoMode) Color(0xFFF59E0B) else NeonCyan,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = if (isBigoMode) Color(0xFFF59E0B) else NeonCyan,
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_broker_target")
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                if (isBigoMode) {
                                    receiver.switchBigoTargetId(targetInput)
                                } else {
                                    receiver.switchTargetUsername(targetInput)
                                }
                            },
                            shape = RoundedCornerShape(7.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBigoMode) Color(0xFFD97706) else Color(0xFF0284C7)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("btn_switch_target")
                        ) {
                            Text(
                                text = "Ganti",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Status Baris Koneksi
                    Text(
                        text = connDetails,
                        fontSize = 8.sp,
                        color = if (isConnected) EmeraldGreen else Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 3.dp, start = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // =========================================================
                    // SAKLAR MANDIRI SETIAP EVENT (SEMUA EVENT SAKLAR)
                    // =========================================================
                    Text(
                        text = "🎛️ SAKLAR EVENT SIARAN (AKTIFKAN / NONAKTIFKAN):",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    // Grid Saklar 2 Kolom
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.6f))
                            .border(0.6.dp, DarkCardBorder, RoundedCornerShape(7.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EventSwitchRow(
                            label = "💬 Komentar & Doa",
                            desc = "Muncul di banner OSD virtual camera",
                            checked = config.enableComment,
                            onCheckedChange = { prefs.setEnableComment(it) }
                        )
                        EventSwitchRow(
                            label = "🎁 Gift & Saweran",
                            desc = "Apresiasi pengirim hadiah saweran",
                            checked = config.enableGift,
                            onCheckedChange = { prefs.setEnableGift(it) }
                        )
                        EventSwitchRow(
                            label = "❤️ Tap Like Masuk",
                            desc = "Banner apresiasi pengetuk like",
                            checked = config.enableLike,
                            onCheckedChange = { prefs.setEnableLike(it) }
                        )
                        EventSwitchRow(
                            label = "👋 Sambutan Join",
                            desc = "Sapa penonton baru masuk ke live",
                            checked = config.enableJoin,
                            onCheckedChange = { prefs.setEnableJoin(it) }
                        )
                        EventSwitchRow(
                            label = "➕ Follow Baru",
                            desc = "Ucapan terima kasih follow siaran",
                            checked = config.enableFollow,
                            onCheckedChange = { prefs.setEnableFollow(it) }
                        )
                        EventSwitchRow(
                            label = "🔄 Share Siaran",
                            desc = "Apresiasi penonton membagikan live",
                            checked = config.enableShare,
                            onCheckedChange = { prefs.setEnableShare(it) }
                        )
                        EventSwitchRow(
                            label = "👥 Indikator Penonton",
                            desc = "Statistik jumlah viewer realtime",
                            checked = config.enableViewerCount,
                            onCheckedChange = { prefs.setEnableViewerCount(it) }
                        )
                        EventSwitchRow(
                            label = "🛍️ Checkout / Pembeli (Jualan)",
                            desc = "Saklar & event penonton order keranjang kuning",
                            checked = config.enableShopOrder,
                            onCheckedChange = { prefs.setEnableShopOrder(it) }
                        )
                        EventSwitchRow(
                            label = "📌 Pin Produk / Etalase (Jualan)",
                            desc = "Saklar & event host pin etalase TikTok Shop",
                            checked = config.enablePinProduct,
                            onCheckedChange = { prefs.setEnablePinProduct(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // =========================================================
                    // PEMISAHAN OVERLAY EVENT (LIKE & GIFT OVERLAY MANDIRI)
                    // =========================================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                            .border(0.6.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { prefs.setSeparateOverlays(!config.separateOverlays) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "✨ Pisahkan Overlay Like & Gift",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (config.separateOverlays) Color(0xFFFDE047) else Color.White
                            )
                            Text(
                                text = if (config.separateOverlays)
                                    "Like & Gift punya layer overlay sendiri (tidak bertumpuk)"
                                else
                                    "Semua event masuk ke satu banner komentar gabungan",
                                fontSize = 7.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = config.separateOverlays,
                            onCheckedChange = { prefs.setSeparateOverlays(it) },
                            modifier = Modifier.size(width = 34.dp, height = 22.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFF59E0B),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }

                    if (config.separateOverlays) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F172A))
                                .border(0.6.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "❤️ Pengaturan Overlay Tap Like:",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF87171)
                            )
                            Text(
                                text = "Posisi: X=${(config.likePosXpct*100).toInt()}% Y=${(config.likePosYpct*100).toInt()}% | Font: ${config.likeFontSizeSp.toInt()}sp | Skala: ${"%.1f".format(config.likeScale)}x",
                                fontSize = 7.5.sp,
                                color = Color(0xFFCBD5E1)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MiniStepperButton("⬅️", onClick = { prefs.setLikePos((config.likePosXpct - 0.05f).coerceIn(0.05f, 0.95f), config.likePosYpct) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("⬆️", onClick = { prefs.setLikePos(config.likePosXpct, (config.likePosYpct - 0.04f).coerceIn(0.05f, 0.95f)) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("⬇️", onClick = { prefs.setLikePos(config.likePosXpct, (config.likePosYpct + 0.04f).coerceIn(0.05f, 0.95f)) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("➡️", onClick = { prefs.setLikePos((config.likePosXpct + 0.05f).coerceIn(0.05f, 0.95f), config.likePosYpct) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("🔤-", onClick = { prefs.setLikeFontSizeSp((config.likeFontSizeSp - 1f).coerceIn(9f, 24f)) }, modifier = Modifier.weight(0.9f))
                                MiniStepperButton("🔤+", onClick = { prefs.setLikeFontSizeSp((config.likeFontSizeSp + 1f).coerceIn(9f, 24f)) }, modifier = Modifier.weight(0.9f))
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "🎁 Pengaturan Overlay Saweran Gift:",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24)
                            )
                            Text(
                                text = "Posisi: X=${(config.giftPosXpct*100).toInt()}% Y=${(config.giftPosYpct*100).toInt()}% | Font: ${config.giftFontSizeSp.toInt()}sp | Skala: ${"%.1f".format(config.giftScale)}x",
                                fontSize = 7.5.sp,
                                color = Color(0xFFCBD5E1)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MiniStepperButton("⬅️", onClick = { prefs.setGiftPos((config.giftPosXpct - 0.05f).coerceIn(0.05f, 0.95f), config.giftPosYpct) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("⬆️", onClick = { prefs.setGiftPos(config.giftPosXpct, (config.giftPosYpct - 0.04f).coerceIn(0.05f, 0.95f)) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("⬇️", onClick = { prefs.setGiftPos(config.giftPosXpct, (config.giftPosYpct + 0.04f).coerceIn(0.05f, 0.95f)) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("➡️", onClick = { prefs.setGiftPos((config.giftPosXpct + 0.05f).coerceIn(0.05f, 0.95f), config.giftPosYpct) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("🔤-", onClick = { prefs.setGiftFontSizeSp((config.giftFontSizeSp - 1f).coerceIn(9f, 24f)) }, modifier = Modifier.weight(0.9f))
                                MiniStepperButton("🔤+", onClick = { prefs.setGiftFontSizeSp((config.giftFontSizeSp + 1f).coerceIn(9f, 24f)) }, modifier = Modifier.weight(0.9f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    // =========================================================
                    // ANALITIK OVERLAY EVENT REAL-TIME
                    // =========================================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                            .border(0.6.dp, EmeraldGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { prefs.setEnableEventStatsOverlay(!config.enableEventStatsOverlay) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📊 Analitik Overlay Event di Kamera",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (config.enableEventStatsOverlay) EmeraldGreen else Color.White
                            )
                            Text(
                                text = "Opsional (Default OFF) • Metrik real-time khusus host tampil di Floating Control",
                                fontSize = 7.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = config.enableEventStatsOverlay,
                            onCheckedChange = { prefs.setEnableEventStatsOverlay(it) },
                            modifier = Modifier.size(width = 34.dp, height = 22.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EmeraldGreen,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }

                    if (config.enableEventStatsOverlay) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F172A))
                                .border(0.6.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Kustom Analitik: Posisi, Font, dan Ukuran",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                            Text(
                                text = "Posisi: X=${(config.eventStatsPosXpct*100).toInt()}% Y=${(config.eventStatsPosYpct*100).toInt()}% | Font: ${config.eventStatsFontSizeSp.toInt()}sp | Skala: ${"%.1f".format(config.eventStatsScale)}x",
                                fontSize = 7.5.sp,
                                color = Color(0xFFCBD5E1)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1E293B))
                                    .clickable { prefs.setShowDiamondsInAnalytics(!config.showDiamondsInAnalytics) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (config.showDiamondsInAnalytics) "💎 Diamond di Analitik: AKTIF" else "💎 Diamond di Analitik: DITUTUP",
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (config.showDiamondsInAnalytics) EmeraldGreen else Color(0xFF94A3B8)
                                )
                                Switch(
                                    checked = config.showDiamondsInAnalytics,
                                    onCheckedChange = { prefs.setShowDiamondsInAnalytics(it) },
                                    modifier = Modifier.size(width = 30.dp, height = 18.dp),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = EmeraldGreen,
                                        uncheckedThumbColor = Color(0xFF94A3B8),
                                        uncheckedTrackColor = Color(0xFF334155)
                                    )
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MiniStepperButton("⬅️", onClick = { prefs.setEventStatsPos((config.eventStatsPosXpct - 0.05f).coerceIn(0.05f, 0.95f), config.eventStatsPosYpct) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("⬆️", onClick = { prefs.setEventStatsPos(config.eventStatsPosXpct, (config.eventStatsPosYpct - 0.04f).coerceIn(0.05f, 0.95f)) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("⬇️", onClick = { prefs.setEventStatsPos(config.eventStatsPosXpct, (config.eventStatsPosYpct + 0.04f).coerceIn(0.05f, 0.95f)) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("➡️", onClick = { prefs.setEventStatsPos((config.eventStatsPosXpct + 0.05f).coerceIn(0.05f, 0.95f), config.eventStatsPosYpct) }, modifier = Modifier.weight(1f))
                                MiniStepperButton("🔤-", onClick = { prefs.setEventStatsFontSizeSp((config.eventStatsFontSizeSp - 1f).coerceIn(8f, 20f)) }, modifier = Modifier.weight(0.9f))
                                MiniStepperButton("🔤+", onClick = { prefs.setEventStatsFontSizeSp((config.eventStatsFontSizeSp + 1f).coerceIn(8f, 20f)) }, modifier = Modifier.weight(0.9f))
                                MiniStepperButton("🔍+", onClick = {
                                    val nextScale = if (config.eventStatsScale >= 1.4f) 0.7f else config.eventStatsScale + 0.15f
                                    prefs.setEventStatsScale(nextScale)
                                }, modifier = Modifier.weight(0.9f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    // Tombol Uji Coba Cepat (Test Injections)
                    Text(
                        text = "🧪 UJI COBA CEPAT EVENT (SIMULASI):",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    val isBigoActive = config.activePlatform == "BIGO"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TestEventButton(label = "💬 Tes Doa", onClick = { receiver.injectTestEvent(LiveEventType.CHAT) }, modifier = Modifier.weight(1f))
                        TestEventButton(label = if (isBigoActive) "🎁 Tes Mawar" else "🎁 Tes Gift", onClick = { receiver.injectTestEvent(LiveEventType.GIFT) }, modifier = Modifier.weight(1f))
                        TestEventButton(label = "❤️ Tes Like", onClick = { receiver.injectTestEvent(LiveEventType.LIKE) }, modifier = Modifier.weight(1f))
                        TestEventButton(label = "👋 Tes Join", onClick = { receiver.injectTestEvent(LiveEventType.MEMBER) }, modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isBigoActive) {
                            TestEventButton(label = "🏎️ Tes Supercar (3.000 🫘)", onClick = { receiver.injectTestBigoGift() }, modifier = Modifier.weight(1.3f))
                        } else {
                            TestEventButton(label = "🐋 Tes Paus (2.150 💎)", onClick = { receiver.injectTestPausGift() }, modifier = Modifier.weight(1.3f))
                        }
                        TestEventButton(label = "🛍️ Tes Order", onClick = { receiver.injectTestEvent(LiveEventType.SHOP_ORDER) }, modifier = Modifier.weight(0.9f))
                        TestEventButton(label = "📌 Tes Pin", onClick = { receiver.injectTestEvent(LiveEventType.PIN_PRODUCT) }, modifier = Modifier.weight(0.9f))
                    }

                    // Tampilan Event Terakhir
                    if (lastEvent != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(DarkSurface)
                                .border(0.6.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                                .padding(5.dp)
                        ) {
                            Text(
                                text = "⚡ Terakhir Diterima: [${lastEvent?.type?.name}] ${lastEvent?.senderName}: ${lastEvent?.text}",
                                fontSize = 8.sp,
                                color = NeonCyan,
                                maxLines = 2
                            )
                        }
                    }

                    // =========================================================
                    // REALTIME LOG EVENT TERMUX
                    // =========================================================
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF020617))
                            .border(0.6.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "📟 LOG EVENT TERMUX",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                                if (totalOrders > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF059669))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "🛍️ $totalOrders Order",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Bersihkan",
                                fontSize = 7.5.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .clickable { receiver.clearTermuxLogs() }
                                    .padding(2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 110.dp)
                                .verticalScroll(scrollState)
                        ) {
                            termuxLogs.takeLast(30).forEach { logLine ->
                                Text(
                                    text = logLine,
                                    fontSize = 7.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = when {
                                        logLine.contains("[ORDER]") -> Color(0xFF34D399)
                                        logLine.contains("[PIN]") -> Color(0xFFFDE047)
                                        logLine.contains("[GIFT]") -> Color(0xFFFBBF24)
                                        logLine.contains("[LIKE]") -> Color(0xFFF87171)
                                        else -> Color(0xFF94A3B8)
                                    },
                                    lineHeight = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventSwitchRow(
    label: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) Color.White else Color(0xFF94A3B8)
            )
            Text(
                text = desc,
                fontSize = 7.5.sp,
                color = Color(0xFF64748B)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(width = 34.dp, height = 22.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EmeraldGreen,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF334155)
            )
        )
    }
}

@Composable
private fun TestEventButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(26.dp),
        shape = RoundedCornerShape(5.dp),
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, NeonCyan.copy(alpha = 0.5f))
    ) {
        Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniStepperButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(24.dp),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF1E293B),
            contentColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF475569))
    ) {
        Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
    }
}

