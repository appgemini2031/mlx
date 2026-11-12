package com.tajasb.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.NeonPurple
import com.tajasb.data.VcamRepository
import com.tajasb.quran.QuranAudioDownloadManager
import com.tajasb.quran.QuranAudioRoleOption
import com.tajasb.quran.QuranCatalog
import com.tajasb.quran.QuranTabMode
import com.tajasb.quran.SavedQuranAudioItem
import java.io.File

@Composable
fun QuranAudioDownloaderCard(
    onUserMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val quranManager = remember { QuranAudioDownloadManager.getInstance(context) }
    val vcamRepo = remember { VcamRepository.getInstance(context) }
    val state by quranManager.state.collectAsState()
    val vcamConfig by vcamRepo.config.collectAsState()

    var showLogicGuide by remember { mutableStateOf(false) }

    LaunchedEffect(state.selectedImam.id) {
        quranManager.refreshAllDownloadedFiles()
    }

    // Set path audio aktif di playlist untuk cek status "Sudah di Playlist"
    val activeAudioPaths = remember(vcamConfig.audioPlaylist) {
        vcamConfig.audioPlaylist.map { it.path }.toSet()
    }

    val savedItems = remember(state.downloadedSurahKeys, state.savedSurahFilterImamId) {
        quranManager.getSavedQuranAudioItems(state.savedSurahFilterImamId)
    }

    val filteredSurahs = remember(state.searchQuery) {
        val q = state.searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            QuranCatalog.SURAHS
        } else {
            QuranCatalog.SURAHS.filter { surah ->
                surah.code3.contains(q) ||
                    surah.number.toString() == q ||
                    surah.nameLatin.lowercase().contains(q) ||
                    surah.meaningId.lowercase().contains(q)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quran_downloader_card"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(
            width = if (state.isDownloading) 1.2.dp else 0.8.dp,
            color = if (state.isDownloading) EmeraldGreen else DarkCardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // =========================================================
            // 1. HEADER ROW UTAMA (Status ringkas + Tombol Expand)
            // =========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { quranManager.toggleExpanded() }
                    .testTag("quran_downloader_header"),
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(EmeraldGreen.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "🕋 QURAN MP3",
                            color = EmeraldGreen,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Audio Al-Quran (20 Qari)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (state.isDownloading) {
                                "⬇️ [${state.completedCount}/${state.totalQueueCount}] ${state.currentDownloadingSurah?.nameLatin ?: ""} (${state.currentSurahProgressPercent}%)"
                            } else {
                                "💾 ${state.downloadedSurahKeys.size} Surat Tersimpan di HP • ${state.selectedImam.displayName}"
                            },
                            color = if (state.isDownloading) EmeraldGreen else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showLogicGuide = !showLogicGuide },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Bantuan Logika",
                            tint = if (showLogicGuide) NeonCyan else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Icon(
                        imageVector = if (state.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Buka/Tutup Al-Quran",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Progress download aktif
            if (state.isDownloading) {
                val totalProg = if (state.totalQueueCount > 0) {
                    ((state.completedCount.toFloat() + (state.currentSurahProgressPercent / 100f)) / state.totalQueueCount.toFloat()).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = { totalProg },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = EmeraldGreen,
                    trackColor = DarkSurfaceVariant
                )
            }

            // =========================================================
            // 2. KOTAK PENJELASAN LOGIKA (Menghilangkan Kebingungan Pengguna)
            // =========================================================
            if (showLogicGuide) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(0.6.dp, NeonCyan.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("💡", fontSize = 12.sp)
                            Text(
                                text = "Cara Kerja Audio Al-Quran Tersimpan:",
                                color = NeonCyan,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "1. Unduh 1 Kali: Surat yang diunduh otomatis disimpan di memori HP Anda selamanya.\n" +
                                "2. Bebas Kuota: Surat yang sudah tersimpan TIDAK PERLU didownload ulang.\n" +
                                "3. Tambah ke Playlist: Tombol '➕ Playlist' langsung mendaftarkan file lokal HP ke pemutar musik Tajasb tanpa internet.\n" +
                                "4. Audio Mandiri: Anda bisa aktifkan saklar 'Audio Mandiri' di atas agar lantunan Al-Quran terus berputar tanpa terputus saat video berganti.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 8.5.sp,
                            lineHeight = 11.5.sp
                        )
                    }
                }
            }

            if (state.isExpanded) {
                // =========================================================
                // 3. SEGMENTED TAB JELAS: [💾 Tersimpan di HP] vs [🌐 Unduh Baru]
                // =========================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF090D16))
                        .border(0.7.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    QuranTabMode.entries.forEach { tab ->
                        val isSelected = state.activeTab == tab
                        val countLabel = if (tab == QuranTabMode.SAVED) " (${state.downloadedSurahKeys.size})" else ""
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) {
                                        if (tab == QuranTabMode.SAVED) EmeraldGreen.copy(alpha = 0.22f) else NeonCyan.copy(alpha = 0.22f)
                                    } else Color.Transparent
                                )
                                .border(
                                    width = if (isSelected) 0.8.dp else 0.dp,
                                    color = if (isSelected) {
                                        if (tab == QuranTabMode.SAVED) EmeraldGreen else NeonCyan
                                    } else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { quranManager.switchTab(tab) }
                                .padding(vertical = 6.dp)
                                .testTag("quran_tab_${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${tab.icon} ${tab.label}$countLabel",
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }

                // =========================================================
                // TAB 1: 💾 TERSIMPAN DI HP (KOLEKSI LOKAL - TANPA DOWNLOAD)
                // =========================================================
                if (state.activeTab == QuranTabMode.SAVED) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Info banner ringkas
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(EmeraldGreen.copy(alpha = 0.08f))
                                .border(0.5.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("📁", fontSize = 11.sp)
                            Text(
                                text = "File di bawah ini sudah ada di memori HP. Klik tombol '+' untuk memasukkan langsung ke Playlist Audio.",
                                fontSize = 8.5.sp,
                                color = Color(0xFFE2E8F0),
                                lineHeight = 11.sp
                            )
                        }

                        if (savedItems.isNotEmpty()) {
                            // Filter Imam untuk File Tersimpan
                            val imamIdsWithSavedFiles = remember(state.downloadedSurahKeys) {
                                state.downloadedSurahKeys.map { it.substringBefore('_') }.toSet()
                            }

                            if (imamIdsWithSavedFiles.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    QuickFilterChip(
                                        label = "Semua Qari (${savedItems.size})",
                                        isSelected = state.savedSurahFilterImamId == null,
                                        onClick = { quranManager.setSavedSurahFilterImam(null) },
                                        testTag = "filter_saved_all"
                                    )
                                    imamIdsWithSavedFiles.forEach { imamId ->
                                        val imam = QuranCatalog.findImamById(imamId)
                                        QuickFilterChip(
                                            label = imam.displayName,
                                            isSelected = state.savedSurahFilterImamId == imam.id,
                                            onClick = { quranManager.setSavedSurahFilterImam(imam.id) },
                                            testTag = "filter_saved_${imam.id}"
                                        )
                                    }
                                }
                            }

                            // TOMBOL AKSI CEPAT UTAMA: Masukkan Semua Tersimpan ke Playlist
                            Button(
                                onClick = {
                                    quranManager.addAllSavedItemsToPlaylist(savedItems) { msg ->
                                        onUserMessage(msg)
                                    }
                                },
                                shape = RoundedCornerShape(7.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldGreen,
                                    contentColor = Color(0xFF090D16)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_add_all_saved_to_playlist")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "➕ Masukkan Semua (${savedItems.size} Surat) ke Playlist",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            // DAFTAR SURAT TERSIMPAN DI HP
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 240.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBackground)
                                    .border(0.7.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                                    .testTag("saved_quran_surah_list"),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(savedItems, key = { "${it.imamId}_${it.surahNumber}" }) { item ->
                                    val isTestingThis = (state.isTestingVoice || state.isLoadingVoiceTest) &&
                                        state.testingSurahNumber == item.surahNumber &&
                                        state.testingImamId == item.imamId

                                    val isAlreadyInPlaylist = activeAudioPaths.contains(item.filePath)
                                    val sizeMb = String.format("%.1f MB", item.fileSizeBytes / (1024.0 * 1024.0))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkSurfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "${item.surahCode}. ${item.surahName}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "($sizeMb)",
                                                    color = Color.Gray,
                                                    fontSize = 8.sp
                                                )
                                            }
                                            Text(
                                                text = "${item.imamName} • ${item.surahMeaning}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 8.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Tombol Tes Suara
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(
                                                        if (isTestingThis) Color(0xFFEF4444).copy(alpha = 0.22f)
                                                        else NeonCyan.copy(alpha = 0.16f)
                                                    )
                                                    .border(
                                                        0.6.dp,
                                                        if (isTestingThis) Color(0xFFEF4444) else NeonCyan.copy(alpha = 0.5f),
                                                        RoundedCornerShape(5.dp)
                                                    )
                                                    .clickable {
                                                        if (isTestingThis) {
                                                            quranManager.stopVoiceTest()
                                                        } else {
                                                            quranManager.startVoiceTest(
                                                                QuranCatalog.findImamById(item.imamId),
                                                                item.surahNumber
                                                            )
                                                        }
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (isTestingThis) "⏹ Stop" else "🔊 Putar",
                                                    color = if (isTestingThis) Color(0xFFFCA5A5) else NeonCyan,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            // Tombol Status / Tambah ke Playlist
                                            if (isAlreadyInPlaylist) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(EmeraldGreen.copy(alpha = 0.15f))
                                                        .border(0.6.dp, EmeraldGreen.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = EmeraldGreen,
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Text(
                                                            text = "Di Playlist",
                                                            color = EmeraldGreen,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(EmeraldGreen.copy(alpha = 0.22f))
                                                        .border(0.6.dp, EmeraldGreen, RoundedCornerShape(5.dp))
                                                        .clickable {
                                                            quranManager.addSavedAudioItemToPlaylist(item) { msg ->
                                                                onUserMessage(msg)
                                                            }
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "➕ Tambah",
                                                        color = EmeraldGreen,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Tampilan Kosong jika belum ada surat yang terunduh
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBackground)
                                    .border(0.7.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "Belum Ada Surat Al-Quran Tersimpan di HP",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Silakan unduh surat favorit Anda terlebih dahulu melalui tab 'Unduh Baru' di sebelah. File yang diunduh akan otomatis tersimpan selamanya di HP Anda.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 8.5.sp,
                                        lineHeight = 11.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                    Button(
                                        onClick = { quranManager.switchTab(QuranTabMode.DOWNLOAD) },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonCyan,
                                            contentColor = Color(0xFF090D16)
                                        )
                                    ) {
                                        Text("🌐 Buka Tab Unduh Baru", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // =========================================================
                // TAB 2: 🌐 UNDUH BARU (KATALOG ONLINE 20 QARI)
                // =========================================================
                if (state.activeTab == QuranTabMode.DOWNLOAD) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "1. Pilih Qari (20 Imam):",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QuranCatalog.IMAMS.forEach { imam ->
                                val isSelected = state.selectedImam.id == imam.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) EmeraldGreen.copy(alpha = 0.2f) else DarkSurfaceVariant
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 0.6.dp,
                                            color = if (isSelected) EmeraldGreen else DarkCardBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { quranManager.selectImam(imam) }
                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                        .testTag("quran_imam_chip_${imam.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column {
                                        Text(
                                            text = "${imam.index}. ${imam.displayName}",
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = imam.styleBadge,
                                            color = if (isSelected) EmeraldGreen else Color(0xFF94A3B8),
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                            }
                        }

                        // FITUR TES SUARA IMAM
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    quranManager.startVoiceTest(state.selectedImam, surahNumber = null)
                                },
                                shape = RoundedCornerShape(7.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = Color(0xFF090D16)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quran_test_imam_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (state.isLoadingVoiceTest) {
                                        "⏳ Memuat Suara..."
                                    } else {
                                        "🔊 Tes Suara ${state.selectedImam.displayName}"
                                    },
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (state.isTestingVoice || state.isLoadingVoiceTest) {
                                OutlinedButton(
                                    onClick = { quranManager.stopVoiceTest() },
                                    shape = RoundedCornerShape(7.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFFEF4444)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                    modifier = Modifier.testTag("quran_stop_voice_test_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("⏹ Stop Tes", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (state.voiceTestStatusText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NeonCyan.copy(alpha = 0.1f))
                                    .border(0.6.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = state.voiceTestStatusText,
                                    color = Color(0xFFBAE6FD),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // PILIHAN PERAN AUDIO SAAT MASUK PLAYLIST (AUTO, SV1, SV2, SV3, SV4)
                        Text(
                            text = "2. Peran Playlist:",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            QuranAudioRoleOption.entries.forEach { roleOpt ->
                                val selected = state.selectedRole == roleOpt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) NeonPurple.copy(alpha = 0.22f) else DarkSurfaceVariant)
                                        .border(
                                            width = if (selected) 0.9.dp else 0.5.dp,
                                            color = if (selected) NeonPurple else DarkCardBorder,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { quranManager.selectAudioRole(roleOpt) }
                                        .padding(vertical = 5.dp)
                                        .testTag("quran_role_${roleOpt.id.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = roleOpt.shortBadge,
                                        color = if (selected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // PILIHAN SURAT LENGKAP (114 SURAT) & FILTER CEPAT
                        Text(
                            text = "3. Pilih Surat yang Ingin Diunduh:",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Quick Filter Buttons Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            QuickFilterChip(
                                label = "Pilih Semua (114)",
                                isSelected = state.selectedSurahNumbers.size == 114,
                                onClick = { quranManager.selectAll114() },
                                testTag = "quran_filter_all_114"
                            )
                            QuickFilterChip(
                                label = "Juz 30 (78–114)",
                                isSelected = state.selectedSurahNumbers == QuranCatalog.JUZ_30_SURAH_NUMBERS,
                                onClick = { quranManager.selectJuz30() },
                                testTag = "quran_filter_juz_30"
                            )
                            QuickFilterChip(
                                label = "Surat Pilihan",
                                isSelected = state.selectedSurahNumbers == QuranCatalog.PREFERRED_SURAH_NUMBERS,
                                onClick = { quranManager.selectPreferredSurahs() },
                                testTag = "quran_filter_preferred"
                            )
                            QuickFilterChip(
                                label = "Reset",
                                isSelected = state.selectedSurahNumbers.isEmpty(),
                                onClick = { quranManager.resetSelection() },
                                testTag = "quran_filter_reset"
                            )
                        }

                        // Search Box
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { quranManager.updateSearchQuery(it) },
                            placeholder = { Text("Cari nomor (001-114), nama surat, atau arti...", fontSize = 10.5.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { quranManager.updateSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Hapus Pencarian",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quran_surah_search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        // 114 Surah List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBackground)
                                .border(0.7.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                                .testTag("quran_surah_list"),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(filteredSurahs, key = { it.number }) { surah ->
                                val isChecked = state.selectedSurahNumbers.contains(surah.number)
                                val isSaved = state.downloadedSurahKeys.contains("${state.selectedImam.id}_${surah.number}")
                                val isTestingThis = (state.isTestingVoice || state.isLoadingVoiceTest) &&
                                    state.testingSurahNumber == surah.number &&
                                    state.testingImamId == state.selectedImam.id

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isChecked) EmeraldGreen.copy(alpha = 0.1f) else DarkSurfaceVariant
                                        )
                                        .clickable { quranManager.toggleSurahSelection(surah.number) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { quranManager.toggleSurahSelection(surah.number) },
                                            modifier = Modifier.size(22.dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = EmeraldGreen,
                                                uncheckedColor = Color(0xFF64748B),
                                                checkmarkColor = Color(0xFF090D16)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "${surah.code3}. ${surah.nameLatin}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isSaved) {
                                                    Text(
                                                        text = "✓ Tersimpan",
                                                        color = EmeraldGreen,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${surah.meaningId} • ${surah.ayahCount} Ayat",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 8.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Per-row "🔊 Tes" / "⏹ Stop" button
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(
                                                    if (isTestingThis) Color(0xFFEF4444).copy(alpha = 0.22f)
                                                    else NeonCyan.copy(alpha = 0.16f)
                                                )
                                                .border(
                                                    0.6.dp,
                                                    if (isTestingThis) Color(0xFFEF4444) else NeonCyan.copy(alpha = 0.5f),
                                                    RoundedCornerShape(5.dp)
                                                )
                                                .clickable {
                                                    if (isTestingThis) {
                                                        quranManager.stopVoiceTest()
                                                    } else {
                                                        quranManager.startVoiceTest(state.selectedImam, surah.number)
                                                    }
                                                }
                                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                                .testTag("quran_row_test_btn_${surah.number}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isTestingThis) "⏹ Stop" else "🔊 Tes",
                                                color = if (isTestingThis) Color(0xFFFCA5A5) else NeonCyan,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // TOMBOL DOWNLOAD UTAMA
                        if (!state.isDownloading) {
                            Button(
                                onClick = {
                                    quranManager.startSequentialDownload { msg ->
                                        onUserMessage(msg)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldGreen,
                                    contentColor = Color(0xFF090D16)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("quran_start_download_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "⬇️ Unduh ${state.selectedSurahNumbers.size} Surat (Simpan ke HP)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    quranManager.cancelDownload { msg ->
                                        onUserMessage(msg)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("quran_cancel_download_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Stop Download (${state.completedCount}/${state.totalQueueCount})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Text(
                    text = state.statusMessage,
                    color = Color(0xFFCBD5E1),
                    fontSize = 8.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceVariant)
            .border(
                width = if (isSelected) 0.9.dp else 0.5.dp,
                color = if (isSelected) NeonCyan else DarkCardBorder,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
