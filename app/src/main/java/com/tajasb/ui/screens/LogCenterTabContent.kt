package com.tajasb.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.tajasb.FloatingControlService
import com.tajasb.debug.LogLevel
import com.tajasb.debug.VcamDebugLogger
import com.tajasb.engine.SystemDiagnosticHelper
import com.tajasb.livecomment.CommentScanInspector
import com.tajasb.livecomment.CommentScanRecorder
import com.tajasb.model.VcamConfig
import java.io.File
import java.util.Locale

@Composable
fun LogCenterTabContent(
    config: VcamConfig,
    isFullScreenMode: Boolean,
    onToggleFullScreenMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logs by VcamDebugLogger.logsFlow.collectAsState()
    val isLoggingEnabled by VcamDebugLogger.isLoggingEnabled.collectAsState()
    val shieldRecoveredCount by com.tajasb.debug.CrashShieldManager.totalRecoveredCount.collectAsState()

    val scanInspector = remember { CommentScanInspector.getInstance(context) }
    val scanEntries by scanInspector.entriesFlow.collectAsState()
    val scanRecorder = remember { CommentScanRecorder.getInstance(context) }
    val isScanRecording by scanRecorder.isRecording.collectAsState()
    val scannedCount by scanRecorder.scannedCount.collectAsState()

    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }

    val curVidFile = config.currentVideo?.let { File(it.path) }
    val diag = remember(config.currentVideoIndex, config.playlist.size, logs.size) {
        SystemDiagnosticHelper.analyzeRootCause(
            context = context,
            activeVideoFile = curVidFile,
            glFps = config.fps.toFloat(),
            isDecoderStalled = false
        )
    }

    val filteredLogs = remember(logs, selectedFilter) {
        val base = logs.asReversed()
        when (selectedFilter) {
            "VIDEO" -> base.filter {
                it.tag.contains("Video", ignoreCase = true) ||
                    it.tag.contains("Gl", ignoreCase = true) ||
                    it.tag.contains("Ifs", ignoreCase = true) ||
                    it.message.contains("Video", ignoreCase = true) ||
                    it.message.contains("Transisi", ignoreCase = true) ||
                    it.message.contains("EOF", ignoreCase = true)
            }
            "HOOK" -> base.filter {
                it.tag.contains("Hook", ignoreCase = true) ||
                    it.tag.contains("Cam", ignoreCase = true) ||
                    it.tag.contains("Surface", ignoreCase = true)
            }
            "AUDIO" -> base.filter {
                it.tag.contains("Audio", ignoreCase = true) ||
                    it.tag.contains("Music", ignoreCase = true) ||
                    it.message.contains("Audio", ignoreCase = true) ||
                    it.message.contains("MP3", ignoreCase = true)
            }
            "WARN_ERR" -> base.filter {
                it.level == LogLevel.WARN || it.level == LogLevel.ERROR
            }
            "SHIELD" -> base.filter {
                it.tag.contains("Crash", ignoreCase = true) ||
                    it.tag.contains("Shield", ignoreCase = true) ||
                    it.tag.contains("SafeLooper", ignoreCase = true) ||
                    it.message.contains("Crash", ignoreCase = true) ||
                    it.message.contains("Shield", ignoreCase = true)
            }
            else -> base
        }
    }

    fun launchFloatingLog(fullScreenFloating: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Izin Jendela Melayang diperlukan terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(context, FloatingControlService::class.java).apply {
                action = FloatingControlService.ACTION_OPEN_LOG_TAB
                putExtra(FloatingControlService.EXTRA_LOG_FULLSCREEN, fullScreenFloating)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Toast.makeText(
                context,
                if (fullScreenFloating) "Membuka Floating Log Full Layar ⛶" else "Membuka Floating Log 🪟",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuka Floating Log: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Full-bleed dark terminal layout — NO Card obstruction!
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF020617))
            .testTag("log_center_screen")
    ) {
        // 1. Compact Control & Telemetry Header Bar (Flat, Unobstructed)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF090D16))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Row A: Status Telemetry + Full-Screen App Toggle + Floating Log Optional Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isLoggingEnabled) Color(0xFF10B981) else Color(0xFF64748B))
                    )
                    Text(
                        text = "📋 LOG SISTEM (${logs.size}/100)",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "• 🌐 ${diag.netInfo.networkType} (${String.format(Locale.US, "%.1f", diag.netInfo.downstreamMbps)}M) • 🧠 ${diag.ramInfo.availRamMb}MB",
                        color = Color(0xFF38BDF8),
                        fontSize = 8.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Optional Floating Log Normal
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0F172A))
                            .border(0.7.dp, Color(0xFF38BDF8), RoundedCornerShape(5.dp))
                            .clickable { launchFloatingLog(fullScreenFloating = false) }
                            .padding(horizontal = 6.dp, vertical = 3.5.dp)
                            .testTag("btn_open_floating_log_normal")
                    ) {
                        Text(
                            text = "🪟 Float",
                            color = Color(0xFF38BDF8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Optional Floating Log Full Screen
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF1E1B4B))
                            .border(0.7.dp, Color(0xFF818CF8), RoundedCornerShape(5.dp))
                            .clickable { launchFloatingLog(fullScreenFloating = true) }
                            .padding(horizontal = 6.dp, vertical = 3.5.dp)
                            .testTag("btn_open_floating_log_fullscreen")
                    ) {
                        Text(
                            text = "⛶ Float Full",
                            color = Color(0xFFA5B4FC),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Toggle Full Screen inside Main App
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isFullScreenMode) Color(0xFF7C2D12) else Color(0xFF083344))
                            .border(
                                0.7.dp,
                                if (isFullScreenMode) Color(0xFFF97316) else Color(0xFF00E5FF),
                                RoundedCornerShape(5.dp)
                            )
                            .clickable { onToggleFullScreenMode() }
                            .padding(horizontal = 6.dp, vertical = 3.5.dp)
                            .testTag("btn_toggle_main_log_fullscreen")
                    ) {
                        Text(
                            text = if (isFullScreenMode) "🗗 Normal" else "⛶ Full",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Row B: Action Bar (Saklar ON/OFF, Salin 50, Salin Semua, Probe, Hapus)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isLoggingEnabled) Color(0xFF064E3B) else Color(0xFF1E293B))
                        .border(
                            0.7.dp,
                            if (isLoggingEnabled) Color(0xFF10B981) else Color(0xFF64748B),
                            RoundedCornerShape(5.dp)
                        )
                        .clickable {
                            val next = VcamDebugLogger.toggleLogging()
                            Toast.makeText(
                                context,
                                if (next) "Saklar Log: ON 🟢" else "Saklar Log: OFF ⚪",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(vertical = 4.5.dp)
                        .testTag("btn_log_center_switch"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLoggingEnabled) "🟢 Saklar: ON" else "⚪ Saklar: OFF",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF083344))
                        .border(0.7.dp, Color(0xFF00E5FF), RoundedCornerShape(5.dp))
                        .clickable {
                            val (count, ok) = VcamDebugLogger.copyLogsToClipboard(context, limit = 50)
                            Toast.makeText(
                                context,
                                if (ok) "Disalin $count log ke clipboard! 📋" else "Gagal menyalin log",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(vertical = 4.5.dp)
                        .testTag("btn_log_center_copy50"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📋 Salin 50",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF1E1B4B))
                        .border(0.7.dp, Color(0xFF818CF8), RoundedCornerShape(5.dp))
                        .clickable {
                            val (count, ok) = VcamDebugLogger.copyLogsToClipboard(context, limit = 100)
                            Toast.makeText(
                                context,
                                if (ok) "Disalin $count log ke clipboard! 📋" else "Gagal menyalin log",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(vertical = 4.5.dp)
                        .testTag("btn_log_center_copy_all"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📋 Semua",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.75f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF1E293B))
                        .clickable {
                            VcamDebugLogger.i(
                                "LiveProbe",
                                "⚡ Probe Manual: Video=${config.currentVideo?.name ?: "-"} | FPS=${config.fps} | Mode=${if (config.isIfsMode) "IFS" else if (config.isMasterSpillMode) "Spill" else "Standar"}"
                            )
                            Toast.makeText(context, "Probe dikirim! ⚡", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 4.5.dp)
                        .testTag("btn_log_center_probe"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡ Probe",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.75f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF3B0712))
                        .border(0.7.dp, Color(0xFFEF4444), RoundedCornerShape(5.dp))
                        .clickable {
                            if (selectedFilter == "SCAN") {
                                scanInspector.clearLogs()
                                Toast.makeText(context, "Log Scan dibersihkan! 🧹", Toast.LENGTH_SHORT).show()
                            } else {
                                VcamDebugLogger.clearLogs()
                                Toast.makeText(context, "Log Sistem dibersihkan! 🧹", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 4.5.dp)
                        .testTag("btn_log_center_clear"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🧹 Hapus",
                        color = Color(0xFFF87171),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Row C: Category Filter Chips (Semua, Video, Hook, Audio, Warn/Error, Scan Chat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filterItems = listOf(
                    "ALL" to "🖥️ Semua (${logs.size})",
                    "VIDEO" to "🎬 Video & Transisi",
                    "HOOK" to "📹 Kamera Hook",
                    "AUDIO" to "🎵 Audio/MP3",
                    "WARN_ERR" to "⚠️ Warn/Error",
                    "SHIELD" to "🛡️ Shield ($shieldRecoveredCount)",
                    "SCAN" to "💬 Scan Chat (${scanEntries.size})"
                )
                filterItems.forEach { (key, label) ->
                    val active = selectedFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (active) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF0F172A))
                            .border(
                                0.7.dp,
                                if (active) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                RoundedCornerShape(5.dp)
                            )
                            .clickable { selectedFilter = key }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.White else Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Optional Scan Recorder Bar when "SCAN" filter is active
            if (selectedFilter == "SCAN") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📝 Terekam: $scannedCount Chat",
                        color = Color(0xFF38BDF8),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isScanRecording) Color(0xFFDC2626) else Color(0xFF047857))
                            .clickable {
                                val next = scanRecorder.toggleRecording()
                                Toast.makeText(
                                    context,
                                    if (next) "Rekam Scan: ON 🟢" else "Rekam Scan: OFF ⏹️",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isScanRecording) "🔴 REKAM: ON" else "▶️ REKAM: OFF",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0284C7))
                            .clickable {
                                val res = scanRecorder.exportToDownloadScanFolder()
                                if (res.success) {
                                    Toast.makeText(
                                        context,
                                        "✅ Disimpan ${res.totalComments} chat ke:\n${res.filePath}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "⚠️ ${res.errorMessage ?: "Gagal simpan"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "💾 Simpan TXT",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Thin divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF1E293B))
        )

        // 2. Unobstructed Full-Bleed Terminal Log Output (No Card Wrapper!)
        if (selectedFilter == "SCAN") {
            if (scanEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "(Belum ada log scan komentar live. Aksesibilitas akan menampilkan tangkapan layar secara langsung di sini.)",
                        color = Color(0xFF64748B),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(scanEntries, key = { it.id }) { entry ->
                        val statusColor = when (entry.status.name) {
                            "ACCEPTED" -> Color(0xFF10B981)
                            "REJECTED_SYSTEM" -> Color(0xFFF59E0B)
                            "REJECTED_DUPLICATE" -> Color(0xFF06B6D4)
                            else -> Color(0xFF8B5CF6)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF070E1B))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "[${entry.timeFormatted}]",
                                    color = Color(0xFF64748B),
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = entry.status.label,
                                    color = statusColor,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (entry.spillKeywordMatched != null) {
                                    Text(
                                        text = "🎯 ${entry.spillKeywordMatched}",
                                        color = Color(0xFFFDE047),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Text(
                                text = "${if (entry.senderName.isNotBlank() && entry.senderName != "-") "@${entry.senderName}: " else ""}${entry.content}",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "↳ ${entry.reasonDetail}",
                                color = Color(0xFF94A3B8),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        } else {
            if (!isLoggingEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "(Saklar Log sedang OFF. Ketuk [🟢 Saklar: ON] di atas untuk merekam log real-time.)",
                        color = Color(0xFF64748B),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "(Belum ada log untuk kategori ini. Ketuk [⚡ Probe] atau jalankan video/live.)",
                        color = Color(0xFF64748B),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        val levelColor = when (entry.level) {
                            LogLevel.ERROR -> Color(0xFFEF4444)
                            LogLevel.WARN -> Color(0xFFF59E0B)
                            LogLevel.INFO -> Color(0xFF10B981)
                            LogLevel.DEBUG -> Color(0xFF38BDF8)
                            LogLevel.VERBOSE -> Color(0xFF94A3B8)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.5.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${entry.timeFormatted} ",
                                color = Color(0xFF64748B),
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${entry.level.label.first()}/${entry.tag}: ",
                                color = levelColor,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (entry.errorDetails.isNullOrBlank()) {
                                    entry.message
                                } else {
                                    "${entry.message}\n${entry.errorDetails}"
                                },
                                color = Color(0xFFE2E8F0),
                                fontSize = 8.8.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
