package com.tajasb.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import com.tajasb.infinity.IfsAudioMode
import com.tajasb.infinity.IfsManager
import com.tajasb.infinity.IfsSubPhase
import com.tajasb.infinity.IfsVideoMode
import com.tajasb.infinity.IfsVtPackage
import com.tajasb.model.AudioItem
import com.tajasb.model.VcamConfig
import com.tajasb.model.VideoItem
import com.tajasb.ui.VcamViewModel
import com.tajasb.ui.components.FeatureInfoButton

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.platform.testTag

@Composable
fun InfinitySystemPanel(
    viewModel: VcamViewModel,
    config: VcamConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ifsManager = remember { IfsManager.getInstance(context) }
    val isIfsActive by ifsManager.isIfsActive.collectAsState()
    val isScEnabled by ifsManager.isScEnabled.collectAsState()
    val videoMode by ifsManager.videoMode.collectAsState()
    val vtmPackage by ifsManager.vtmPackage.collectAsState()
    val vtPackages by ifsManager.vtPackages.collectAsState()
    val srAudioIds by ifsManager.srAudioIds.collectAsState()
    val ifsState by ifsManager.ifsState.collectAsState()
    val sv3VlVideoId by ifsManager.sv3VlVideoId.collectAsState()
    val sv3VlMinLoops by ifsManager.sv3VlMinLoops.collectAsState()
    val sv3VlMaxLoops by ifsManager.sv3VlMaxLoops.collectAsState()
    val sv3VbVideoIds by ifsManager.sv3VbVideoIds.collectAsState()
    val sv4LVideoIds by ifsManager.sv4LVideoIds.collectAsState()
    val sv4MinLoops by ifsManager.sv4MinLoops.collectAsState()
    val sv4MaxLoops by ifsManager.sv4MaxLoops.collectAsState()
    val audioMode by ifsManager.audioMode.collectAsState()

    val visiblePackages = remember(videoMode, vtmPackage, vtPackages) {
        if (videoMode == IfsVideoMode.SV1) {
            listOf(vtmPackage) + vtPackages
        } else {
            vtPackages
        }
    }

    var slotPickerTarget by remember { mutableStateOf<Triple<Int, IfsSubPhase, String>?>(null) }
    var audioPickerTargetPkg by remember { mutableStateOf<IfsVtPackage?>(null) }
    var isPickingSv3Vl by remember { mutableStateOf(false) }
    var isPickingSv3VbGroup by remember { mutableStateOf(false) }
    var isPickingSv4LGroup by remember { mutableStateOf(false) }
    var isPackagesExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // BARIS 1: KONTROL UTAMA IFS SEJAJAR (SV1/SV2/SV3/SV4 + SC Toggle + Reset/Maju + !)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F172A))
                .border(0.8.dp, if (isIfsActive) NeonCyan.copy(alpha = 0.6f) else DarkCardBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kiri: Pilihan SV1 / SV2 / SV3 / SV4 (Mode VS) + Info (!)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.5.dp)
            ) {
                FeatureInfoButton(
                    title = "Arsitektur Infinity System (IFS)",
                    description = "• SV1: Menggunakan ABA (3 Video: ABB Awal, BL Looping Standby, BBT Transisi Keluar) ditambah VT1..n (masing-masing 1 Video Konten yang diputar 1x lalu kembali ke ABA).\n\n" +
                        "• SV2: PM1, PM2, PM3..n (masing-masing adalah Paket Master yang berisi ABA: ABB Awal, BL Looping Standby, dan BBT Transisi Keluar).\n\n" +
                        "• SV3: 1 Video VL (di-looping dengan jumlah putaran acak non-repeat dari rentang Min..Max sampai habis) lalu berotasi ke 1 video dari Grup VB (VB1, VB2, dst. secara acak non-repeat 1x per siklus), dan setelah VB habis otomatis kembali ke VL.\n\n" +
                        "• SV4 (Mode VS — Variasi Susunan Rolling JIT):\n" +
                        "  - Video L1, L2, L3, L4... diacak urutan susunannya sekaligus diacak jumlah loop tiap L (default 1..2x).\n" +
                        "  - VS1 digabungkan otomatis -> Masuk daftar -> Play VS1.\n" +
                        "  - Sambil Play VS1, sistem otomatis menggabungkan VS2 di background dan memasukkannya ke daftar siap tayang.\n" +
                        "  - Begitu VS1 selesai, langsung Play VS2, otomatis MENGHAPUS file gabungan VS1 agar memori penyimpanan HP tidak penuh, dan lanjut merakit VS3 dst. untuk live 6+ jam ribuan variasi nyata.\n\n" +
                        "• Audio IFS:\n" +
                        "  - SR: Musik latar acak otomatis.\n" +
                        "  - AX: Audio khusus paket (SV1/SV2).",
                    accentColor = NeonCyan
                )

                listOf(IfsVideoMode.SV1, IfsVideoMode.SV2, IfsVideoMode.SV3, IfsVideoMode.SV4).forEach { m ->
                    val isSelected = videoMode == m
                    val pillText = if (m == IfsVideoMode.SV4) "SV4(VS)" else m.name
                    Box(
                        modifier = Modifier
                            .testTag("btn_ifs_mode_${m.name.lowercase()}")
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) NeonCyan.copy(alpha = 0.22f) else DarkSurface)
                            .border(
                                width = 0.8.dp,
                                color = if (isSelected) NeonCyan else Color(0xFF334155),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { ifsManager.setVideoMode(m) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = pillText,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) NeonCyan else Color.LightGray
                        )
                    }
                }
            }

            // Tengah: Toggle SC Ringkas Sejajar (Khusus SV1 & SV2; SV3/SV4 murni rotasi otomatis)
            if (videoMode == IfsVideoMode.SV1 || videoMode == IfsVideoMode.SV2) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isScEnabled) Color(0xFF3B0764) else DarkSurface)
                        .border(0.6.dp, if (isScEnabled) NeonPurple else Color(0xFF334155), RoundedCornerShape(4.dp))
                        .clickable { ifsManager.setScEnabled(!isScEnabled) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isScEnabled) "💬 SC: ON" else "💬 SC: OFF",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isScEnabled) Color(0xFFE879F9) else Color.Gray
                    )
                }
            } else if (videoMode == IfsVideoMode.SV4) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(EmeraldGreen.copy(alpha = 0.16f))
                        .border(0.6.dp, EmeraldGreen.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "🧬 VS JIT",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(EmeraldGreen.copy(alpha = 0.16f))
                        .border(0.6.dp, EmeraldGreen.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "🔀 VL ↔ VB",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }
            }

            // Kanan: Tombol Cepat Maju Fase & Reset
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .testTag("btn_ifs_advance_phase")
                        .clip(RoundedCornerShape(4.dp))
                        .background(NeonPurple.copy(alpha = 0.22f))
                        .border(0.6.dp, NeonPurple, RoundedCornerShape(4.dp))
                        .clickable {
                            ifsManager.advancePhaseManually()
                            viewModel.showUserMessage(
                                when (videoMode) {
                                    IfsVideoMode.SV4 -> "⏭️ Melompat ke Variasi Susunan (VS) berikutnya"
                                    IfsVideoMode.SV3 -> "⏭️ Melompat ke fase SV3 berikutnya (VL ↔ VB)"
                                    else -> "⏭️ Maju ke sub-fase IFS berikutnya"
                                }
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (videoMode == IfsVideoMode.SV4) "⏭️ VS" else "⏭️ Fase",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE879F9)
                    )
                }

                Box(
                    modifier = Modifier
                        .testTag("btn_ifs_reset_cycle")
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurface)
                        .border(0.6.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                        .clickable {
                            ifsManager.resetCycleToBeginning()
                            viewModel.showUserMessage("🔄 Siklus IFS direset ke awal")
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "🔄",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                }
            }
        }

        // BARIS 2: BARIS TES TRIGGER CEPAT (Khusus SV1 & SV2)
        if (videoMode == IfsVideoMode.SV1 || videoMode == IfsVideoMode.SV2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF09101D))
                    .border(0.8.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "⚡ Tes Trigger:",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    vtPackages.forEach { pkg ->
                        val isThisActive = ifsState.activeVtNumber == pkg.vtNumber
                        val hasAx = !pkg.linkedAudioId.isNullOrBlank()
                        val pkgCode = pkg.getShortCode(videoMode)
                        val pkgAx = pkg.getAxCode(videoMode)
                        val btnLabel = if (hasAx) "▶ $pkgCode•$pkgAx" else "▶ $pkgCode"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isThisActive) Color(0xFF083344) else DarkSurfaceVariant)
                                .border(
                                    width = if (isThisActive) 1.dp else 0.6.dp,
                                    color = if (isThisActive) NeonCyan else Color(0xFF475569),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    val ok = ifsManager.triggerVt(pkg.vtNumber, "Host", "Tes Trigger")
                                    if (ok) {
                                        viewModel.showUserMessage("⚡ Tes Trigger: Memicu $pkgCode")
                                    } else {
                                        viewModel.showUserMessage("⚠️ Isi video pada $pkgCode terlebih dahulu")
                                    }
                                }
                                .padding(horizontal = 7.dp, vertical = 3.5.dp)
                        ) {
                            Text(
                                text = btnLabel,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isThisActive) NeonCyan else Color.White
                            )
                        }
                    }
                }
            }
        }

        // BARIS 3: STATUS AKTIF & DROPDOWN DAFTAR PAKET SLOT
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceVariant)
                .border(0.6.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                .clickable { isPackagesExpanded = !isPackagesExpanded }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val srCount = if (srAudioIds.isNotEmpty()) srAudioIds.size else config.audioPlaylist.size
            val audioBadge = when {
                audioMode == IfsAudioMode.SV4_VS -> "🧬 VSA${ifsState.sv4AudioCurrentVsNumber}"
                ifsState.isPlayingTriggeredAx -> "🔊 ${ifsState.activeTriggerAxLabel ?: "AX"}"
                audioMode == IfsAudioMode.SEQUENTIAL -> "🔁 Urut:$srCount"
                audioMode == IfsAudioMode.SINGLE_LOOP -> "🔂 Loop 1x"
                ifsState.isSrPlaying -> "🔀 SR"
                else -> "🔀 SR:$srCount"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                val dotColor = when {
                    videoMode == IfsVideoMode.SV4 && ifsState.sv4NextReady -> EmeraldGreen
                    videoMode == IfsVideoMode.SV4 && ifsState.sv4IsStitchingNext -> NeonCyan
                    videoMode == IfsVideoMode.SV3 && ifsState.activeSubPhase == IfsSubPhase.VL -> EmeraldGreen
                    videoMode == IfsVideoMode.SV3 && ifsState.activeSubPhase == IfsSubPhase.VB -> NeonCyan
                    ifsState.activeSubPhase == IfsSubPhase.BL -> EmeraldGreen
                    else -> Color(0xFFFCD34D)
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                val statusTitle = when (videoMode) {
                    IfsVideoMode.SV4 -> {
                        val nextBadge = when {
                            ifsState.sv4NextReady -> "✅ VS${ifsState.sv4NextVsNumber} Siap"
                            ifsState.sv4IsStitchingNext -> "⚙️ Gabung VS${ifsState.sv4NextVsNumber} (${ifsState.sv4NextStitchPercent}%)"
                            else -> "Standby"
                        }
                        "VS${ifsState.sv4CurrentVsNumber} [${ifsState.sv4ActiveFormula}] ➡️ $nextBadge | $audioBadge"
                    }
                    IfsVideoMode.SV3 -> {
                        val phaseInfo = if (ifsState.activeSubPhase == IfsSubPhase.VB) {
                            "VB${ifsState.sv3ActiveVbNumber} (1x ➡️ VL) • Siklus VB:${ifsState.sv3VbRemainingInCycle}/${sv3VbVideoIds.size}"
                        } else {
                            "VL Putaran ${ifsState.sv3CurrentVlLoop}/${ifsState.sv3TargetVlLoops}x ➡️ VB Acak"
                        }
                        "$phaseInfo | $audioBadge"
                    }
                    else -> {
                        "${ifsState.getActivePackageCode(videoMode)}•${ifsState.activeSubPhase.badge} | $audioBadge"
                    }
                }
                Text(
                    text = statusTitle,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (videoMode == IfsVideoMode.SV1 || videoMode == IfsVideoMode.SV2) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonPurple.copy(alpha = 0.25f))
                            .border(0.6.dp, NeonPurple, RoundedCornerShape(4.dp))
                            .clickable {
                                ifsManager.addVtPackage()
                                isPackagesExpanded = true
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (videoMode == IfsVideoMode.SV2) "+ PM${vtPackages.size + 1}" else "+ VT${vtPackages.size + 1}",
                            fontSize = 8.5.sp,
                            color = Color(0xFFE879F9),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0F172A))
                        .border(0.6.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    val expandText = when (videoMode) {
                        IfsVideoMode.SV4 -> if (isPackagesExpanded) "Atur Mode VS ▴" else "Atur Mode VS (L:${sv4LVideoIds.size}) ▾"
                        IfsVideoMode.SV3 -> if (isPackagesExpanded) "Atur VL & VB ▴" else "Atur VL & VB (VB:${sv3VbVideoIds.size}) ▾"
                        else -> if (isPackagesExpanded) "Atur Slot ▴" else "Atur Slot (${visiblePackages.size}) ▾"
                    }
                    Text(
                        text = expandText,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }
            }
        }

        // DAFTAR KARTU KONFIGURASI (SV4: Mode VS | SV3: Kartu VL + Grup VB | SV1/SV2: Kartu VTM & VT1..n)
        AnimatedVisibility(visible = isPackagesExpanded) {
            when (videoMode) {
                IfsVideoMode.SV4 -> {
                    Sv4VsConfigurationCards(
                        playlist = config.playlist,
                        lVideoIds = sv4LVideoIds,
                        minLoops = sv4MinLoops,
                        maxLoops = sv4MaxLoops,
                        ifsState = ifsState,
                        onUpdateLoopRange = { minL, maxL ->
                            ifsManager.setSv4LoopRange(minL, maxL)
                            val lbl = if (minL == maxL) "${minL}x" else "${minL}x s/d ${maxL}x"
                            viewModel.showUserMessage("🧬 Loop Dinamis L diatur ke $lbl")
                        },
                        onOpenLMultiSelect = { isPickingSv4LGroup = true },
                        onRemoveLVideo = { id ->
                            ifsManager.removeSv4LVideo(id)
                            viewModel.showUserMessage("🗑️ Video dihapus dari daftar Variasi Susunan (L)")
                        },
                        onRestartVsCycle = {
                            ifsManager.startSv4VideoEngine(resetCounter = true)
                            viewModel.showUserMessage("▶️ Engine SV4 Video dimulai & merakit variasi dari VS1!")
                        },
                        onStartSv4Engine = {
                            ifsManager.startSv4VideoEngine(resetCounter = false)
                            viewModel.showUserMessage("▶️ SV4 Video dimulai! Merakit variasi di background & menyembunyikan bahan mentah.")
                        },
                        onStopSv4EngineForEdit = {
                            ifsManager.stopSv4VideoEngineForEdit()
                            viewModel.showUserMessage("⏸️ SV4 Video dijeda ke Mode Edit! Silakan tambah, edit, atau optimasi video.")
                        },
                        onSkipToNextVs = {
                            ifsManager.advancePhaseManually()
                            viewModel.showUserMessage("⏭️ Beralih ke Variasi Susunan (VS) berikutnya...")
                        },
                        onSeekToLast3s = {
                            viewModel.seekVideoToLast3Seconds()
                        }
                    )
                }
                IfsVideoMode.SV3 -> {
                    Sv3ConfigurationCards(
                        playlist = config.playlist,
                        vlVideoId = sv3VlVideoId,
                        minLoops = sv3VlMinLoops,
                        maxLoops = sv3VlMaxLoops,
                        vbVideoIds = sv3VbVideoIds,
                        activeSubPhase = ifsState.activeSubPhase,
                        currentVlLoop = ifsState.sv3CurrentVlLoop,
                        targetVlLoops = ifsState.sv3TargetVlLoops,
                        activeVbNumber = ifsState.sv3ActiveVbNumber,
                        remainingVbInCycle = ifsState.sv3VbRemainingInCycle,
                        onPickVlVideo = { isPickingSv3Vl = true },
                        onUpdateLoopRange = { minL, maxL ->
                            ifsManager.setSv3VlLoopRange(minL, maxL)
                            viewModel.showUserMessage("🔁 Rentang Loop Acak VL: $minL s/d $maxL putaran (Non-Repeat)")
                        },
                        onOpenVbMultiSelect = { isPickingSv3VbGroup = true },
                        onRemoveVbVideo = { id ->
                            ifsManager.removeSv3VbVideo(id)
                            viewModel.showUserMessage("🗑️ Video dihapus dari Grup VB")
                        }
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        visiblePackages.forEach { pkg ->
                            VtPackageCard(
                                pkg = pkg,
                                videoMode = videoMode,
                                isScEnabled = isScEnabled,
                                playlist = config.playlist,
                                audioPlaylist = config.audioPlaylist,
                                canDelete = !pkg.isVtm && vtPackages.size > 1,
                                isCurrentActive = ifsState.activeVtNumber == pkg.vtNumber,
                                activeSubPhase = ifsState.activeSubPhase,
                                onPickSlot = { phase ->
                                    slotPickerTarget = Triple(pkg.vtNumber, phase, pkg.shortCode)
                                },
                                onPickAudioAx = {
                                    audioPickerTargetPkg = pkg
                                },
                                onUpdateKeyword = { newKeyword ->
                                    ifsManager.updateVtPackage(pkg.copy(triggerKeyword = newKeyword))
                                },
                                onTestTrigger = {
                                    val targetCode = pkg.getShortCode(videoMode)
                                    if (pkg.isVtm) {
                                        ifsManager.resetCycleToBeginning()
                                        viewModel.showUserMessage("👑 Siklus direset ke ABA Standby")
                                    } else {
                                        val ok = ifsManager.triggerVt(pkg.vtNumber, "Host", "Manual Trigger")
                                        if (ok) {
                                            viewModel.showUserMessage("⚡ Tes Trigger: Memicu $targetCode")
                                        } else {
                                            viewModel.showUserMessage("⚠️ Pilih video untuk $targetCode terlebih dahulu")
                                        }
                                    }
                                },
                                onDelete = {
                                    ifsManager.deleteVtPackage(pkg.vtNumber)
                                }
                            )
                        }
                    }
                }
            }
        }

        // PANEL TERHUBUNG AUDIO IFS (Mode SR/AX vs SV4 Audio Mode VS Rolling JIT)
        Sv4AudioConfigurationSection(
            viewModel = viewModel,
            config = config,
            isInsideIfsPanel = true
        )
    }

    // Modal Dialog Multi-Select Video L1..Ln untuk SV4 (Mode VS)
    if (isPickingSv4LGroup) {
        Sv4LMultiSelectDialog(
            playlist = config.playlist,
            selectedLIds = sv4LVideoIds,
            minLoops = sv4MinLoops,
            maxLoops = sv4MaxLoops,
            onDismiss = { isPickingSv4LGroup = false },
            onSaveSelection = { newLIds ->
                ifsManager.setSv4LVideoIds(newLIds)
                isPickingSv4LGroup = false
                viewModel.showUserMessage("🧬 Daftar Video L diperbarui: ${newLIds.size} video (L1..L${newLIds.size})")
            }
        )
    }

    // Modal Dialog Pemilihan 1 Video VL untuk SV3
    if (isPickingSv3Vl) {
        VideoSlotPickerDialog(
            targetTitle = "SV3 • 1 Video VL (Looping Acak)",
            playlist = config.playlist,
            onDismiss = { isPickingSv3Vl = false },
            onSelectVideo = { videoId ->
                ifsManager.setSv3VlVideo(videoId)
                isPickingSv3Vl = false
                viewModel.showUserMessage(
                    if (videoId != null) "👑 Video VL utama ditetapkan untuk SV3"
                    else "Slot Video VL dikosongkan"
                )
            }
        )
    }

    // Modal Dialog Multi-Select Grup VB (VB1, VB2, dst.) untuk SV3
    if (isPickingSv3VbGroup) {
        Sv3VbMultiSelectDialog(
            playlist = config.playlist,
            vlVideoId = sv3VlVideoId,
            selectedVbIds = sv3VbVideoIds,
            onDismiss = { isPickingSv3VbGroup = false },
            onSaveSelection = { newVbIds ->
                ifsManager.setSv3VbVideoIds(newVbIds)
                isPickingSv3VbGroup = false
                viewModel.showUserMessage("🌉 Grup VB diperbarui: ${newVbIds.size} Video Bridge (Acak 1x per siklus)")
            }
        )
    }

    // Modal Dialog Pemilihan Video Slot (SV1 & SV2)
    if (slotPickerTarget != null) {
        val (vtNum, phase, code) = slotPickerTarget!!
        VideoSlotPickerDialog(
            targetTitle = "$code • ${phase.badge}",
            playlist = config.playlist,
            onDismiss = { slotPickerTarget = null },
            onSelectVideo = { videoId ->
                ifsManager.setSlotVideo(vtNum, phase, videoId)
                slotPickerTarget = null
            }
        )
    }

    // Modal Dialog Pemilihan Audio Terkait (AX)
    if (audioPickerTargetPkg != null) {
        val targetPkg = audioPickerTargetPkg!!
        AudioAxPickerDialog(
            pkg = targetPkg,
            videoMode = videoMode,
            audioPlaylist = config.audioPlaylist,
            onDismiss = { audioPickerTargetPkg = null },
            onSelectAudio = { audioId ->
                ifsManager.setLinkedAudio(targetPkg.vtNumber, audioId)
                audioPickerTargetPkg = null
            },
            onTestPlayAx = {
                ifsManager.playLinkedAxForVt(targetPkg.vtNumber)
                audioPickerTargetPkg = null
            }
        )
    }
}

@Composable
private fun Sv4VsConfigurationCards(
    playlist: List<VideoItem>,
    lVideoIds: List<String>,
    minLoops: Int,
    maxLoops: Int,
    ifsState: com.tajasb.infinity.IfsState,
    onUpdateLoopRange: (Int, Int) -> Unit,
    onOpenLMultiSelect: () -> Unit,
    onRemoveLVideo: (String) -> Unit,
    onRestartVsCycle: () -> Unit,
    onStartSv4Engine: () -> Unit = {},
    onStopSv4EngineForEdit: () -> Unit = {},
    onSkipToNextVs: () -> Unit,
    onSeekToLast3s: () -> Unit = {}
) {
    val sourcePlaylist = remember(playlist) {
        playlist.filterNot { it.id.startsWith("vs_rolling_") || it.path.contains("vs_rolling_") }
    }
    val validSelectedLIds = remember(lVideoIds, sourcePlaylist) {
        lVideoIds.filter { id -> sourcePlaylist.any { it.id == id } }
    }
    val activeLVideos = remember(validSelectedLIds, sourcePlaylist) {
        if (validSelectedLIds.isNotEmpty()) {
            validSelectedLIds.mapNotNull { id -> sourcePlaylist.find { it.id == id } }
        } else {
            sourcePlaylist
        }
    }
    val uploadedVideoCount = sourcePlaylist.size
    val activeLCount = if (activeLVideos.isNotEmpty()) activeLVideos.size else uploadedVideoCount
    val metrics = remember(activeLCount, minLoops, maxLoops, activeLVideos) {
        com.tajasb.infinity.Sv4VariationCalculator.calculateMetrics(
            lCount = activeLCount,
            minLoops = minLoops,
            maxLoops = maxLoops,
            activeVideos = activeLVideos
        )
    }
    val formattedTotalVariations = metrics.formattedTotalVariations
    val formattedCoreVariations = metrics.formattedCoreVariations
    var isVariationListExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // KARTU 1: MONITOR ANTREAN ROLLING JIT (VS AKTIF ON-AIR + VS BERIKUTNYA DI DAFTAR + AUTO-HAPUS)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(EmeraldGreen),
                width = 1.1.dp
            ),
            shape = RoundedCornerShape(7.dp)
        ) {
            Column(
                modifier = Modifier.padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(EmeraldGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🧬 Mode VS (Variasi Susunan Rolling JIT)",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }

                        FeatureInfoButton(
                            title = "Cara Kerja SV4 — Mode VS (Rolling JIT + Auto-Hapus)",
                            description = "1. Sistem mengacak urutan video L1, L2, L3, L4... sekaligus mengacak jumlah loop tiap L (${minLoops}..${maxLoops}x) untuk membentuk variasi unik.\n\n" +
                                "2. VS1 digabungkan menjadi 1 file MP4 utuh -> dimasukkan ke daftar aktif -> langsung di-Play.\n\n" +
                                "3. Sambil Play VS1, sistem otomatis menggabungkan VS2 di background dan memasukkannya ke daftar siap tayang.\n\n" +
                                "4. Saat VS1 selesai, langsung Play VS2, otomatis MENGHAPUS file penggabungan VS1 agar tidak memakan memori penyimpanan HP, lalu lanjut menggabungkan VS3 dst. Cocok untuk live 6+ jam ribuan variasi mendekati nyata!",
                            accentColor = EmeraldGreen
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .testTag("btn_sv4_seek_last_3s")
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF3B0764))
                                .border(0.6.dp, NeonPurple, RoundedCornerShape(4.dp))
                                .clickable(onClick = onSeekToLast3s)
                                .padding(horizontal = 6.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                text = "⏩ -3d Akhir",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE879F9)
                            )
                        }

                        if (ifsState.sv4NextReady) {
                            Box(
                                modifier = Modifier
                                    .testTag("btn_sv4_skip_next_vs")
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(EmeraldGreen.copy(alpha = 0.2f))
                                    .border(0.6.dp, EmeraldGreen, RoundedCornerShape(4.dp))
                                    .clickable(onClick = onSkipToNextVs)
                                    .padding(horizontal = 6.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    text = "⏭️ Play VS${ifsState.sv4NextVsNumber}",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                            }
                        }
                    }
                }

                // BARIS UTAMA START / STOP MODE PERSIAPAN & EDIT SV4 VIDEO
                val isSv4VideoRunning = ifsState.sv4VideoRunning
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSv4VideoRunning) Color(0xFF052E16) else Color(0xFF3B1D06)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(if (isSv4VideoRunning) EmeraldGreen else Color(0xFFF59E0B)),
                        width = 1.dp
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.5.dp)
                        ) {
                            Text(
                                text = if (isSv4VideoRunning) {
                                    "🟢 SV4 VIDEO SEDANG JALAN (ON-AIR ROLLING)"
                                } else {
                                    "🛠️ TAHAP PERSIAPAN & EDIT MATERI VIDEO"
                                },
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSv4VideoRunning) EmeraldGreen else Color(0xFFFCD34D)
                            )
                            Text(
                                text = if (isSv4VideoRunning) {
                                    "Bahan L1..L$activeLCount disembunyikan & diracik otomatis. Klik 'Stop / Edit' untuk menambah atau mengoptimasi video."
                                } else {
                                    "SV4 belum dijalankan. Silakan tambah video, urutkan, atau optimasi dulu, lalu klik 'MULAI SV4'."
                                },
                                fontSize = 7.5.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        if (!isSv4VideoRunning) {
                            Box(
                                modifier = Modifier
                                    .testTag("btn_sv4_start_video_engine")
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(EmeraldGreen)
                                    .clickable(onClick = onStartSv4Engine)
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "▶️ MULAI SV4",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .testTag("btn_sv4_stop_video_engine_edit")
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF7C2D12))
                                    .border(0.8.dp, Color(0xFFF59E0B), RoundedCornerShape(5.dp))
                                    .clickable(onClick = onStopSv4EngineForEdit)
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "⏹️ Stop / Edit",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFDE68A)
                                )
                            }
                        }
                    }
                }

                // BARIS TOMBOL RAKIT ULANG & TOMBOL/CARD INFO TOTAL VARIASI (SEBELAH RAKIT ULANG)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol Rakit Ulang
                    Card(
                        modifier = Modifier
                            .testTag("btn_sv4_restart_vs")
                            .clickable(onClick = onRestartVsCycle),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF083344)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = SolidColor(NeonCyan),
                            width = 0.9.dp
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = if (isSv4VideoRunning) "⚡ Rakit Ulang" else "▶️ Rakit VS1",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonCyan
                            )
                            Text(
                                text = "Mulai dari VS1",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFBAE6FD)
                            )
                        }
                    }

                    // Tombol Card Info Total Variasi (Sebelah Rakit Ulang) dengan Ikon Tanda Seru (!) & List Menurun
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_sv4_total_variations")
                            .clickable { isVariationListExpanded = !isVariationListExpanded },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isVariationListExpanded) Color(0xFF0A2E36) else Color(0xFF072227)
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = SolidColor(if (isVariationListExpanded) NeonCyan else EmeraldGreen.copy(alpha = 0.9f)),
                            width = if (isVariationListExpanded) 1.1.dp else 0.9.dp
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Ikon Tanda Seru (!) seragam dengan FeatureInfoButton lainnya
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldGreen.copy(alpha = 0.16f))
                                        .border(0.8.dp, EmeraldGreen.copy(alpha = 0.75f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "!",
                                        color = EmeraldGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        lineHeight = 10.sp
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = "TOTAL VARIASI ($activeLCount VIDEO UPLOAD)",
                                        fontSize = 7.2.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFA7F3D0)
                                    )
                                    Text(
                                        text = if (activeLCount > 0) {
                                            "$formattedTotalVariations Variasi • ${metrics.formattedTotalUniqueDaysHours}"
                                        } else {
                                            "0 Variasi (Upload Video L1..Ln)"
                                        },
                                        fontSize = 9.2.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = EmeraldGreen,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(NeonCyan.copy(alpha = 0.16f))
                                        .padding(horizontal = 4.5.dp, vertical = 1.dp)
                                ) {
                                    val loopRangeBadge = if (minLoops == maxLoops) "${minLoops}x" else "${minLoops}-${maxLoops}x"
                                    Text(
                                        text = if (activeLCount > 0) "L1..L$activeLCount • $loopRangeBadge" else "Loop $loopRangeBadge",
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCyan
                                    )
                                }
                                Text(
                                    text = if (isVariationListExpanded) "Tutup Daftar ▴" else "Daftar Detail ▾",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVariationListExpanded) NeonCyan else Color(0xFFA7F3D0)
                                )
                            }
                        }
                    }
                }

                // DAFTAR MENURUN (EXPANDABLE LIST) DETAIL HARI, JAM, JUMLAH VARIASI & KAPASITAS LIVE
                AnimatedVisibility(visible = isVariationListExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("list_sv4_variation_details")
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF04181D))
                            .border(0.9.dp, NeonCyan.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Header Daftar Menurun dengan Ikon (!)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(NeonCyan.copy(alpha = 0.18f))
                                        .border(0.8.dp, NeonCyan, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "!",
                                        color = NeonCyan,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        lineHeight = 9.5.sp
                                    )
                                }
                                Text(
                                    text = "DAFTAR DETAIL VARIASI, HARI, JAM & DURASI LIVE",
                                    fontSize = 8.2.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonCyan
                                )
                            }
                            Text(
                                text = "Tutup ▴",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.clickable { isVariationListExpanded = false }
                            )
                        }

                        if (activeLCount <= 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF08222B))
                                    .padding(7.dp)
                            ) {
                                Text(
                                    text = "Belum ada video sumber yang diupload. Silakan upload minimal 2–4 video pada daftar playlist di bawah agar kalkulasi Hari, Jam, dan Jumlah Variasi muncul otomatis.",
                                    fontSize = 8.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        } else {
                            // 1. BARIS DAFTAR: ESTIMASI TOTAL HARI & JAM TAYANG UNIK NON-STOP
                            Sv4DetailListItemRow(
                                numberBadge = "1",
                                title = "Total Waktu Tayang Unik (Hari, Jam, Menit)",
                                primaryValue = metrics.formattedTotalUniqueDaysHours,
                                secondaryNote = "Setara ${metrics.formattedTotalUniqueHoursSummary} tayang terus-menerus tanpa ada urutan VS yang kembar",
                                valueColor = EmeraldGreen
                            )

                            // 2. BARIS DAFTAR: RINCIAN JUMLAH VARIASI SUSUNAN
                            Sv4DetailListItemRow(
                                numberBadge = "2",
                                title = "Jumlah Total Variasi Susunan (VS)",
                                primaryValue = "${metrics.formattedTotalVariations} Variasi Unik",
                                secondaryNote = "• Susunan Inti ($activeLCount Segmen Penuh): ${metrics.formattedCoreVariations} Variasi\n" +
                                    "• Susunan Rolling (+Segmen Penutup Dinamis): ${metrics.formattedExtendedVariations} Variasi\n" +
                                    "• Rumus Kombinasi: ${activeLCount}! urutan × ${metrics.loopChoices}^$activeLCount loop (${minLoops}x–${maxLoops}x)",
                                valueColor = NeonCyan
                            )

                            // 3. BARIS DAFTAR: DURASI RATA-RATA PER 1 FILE VS & DURASI VIDEO UPLOAD
                            Sv4DetailListItemRow(
                                numberBadge = "3",
                                title = "Durasi Video Upload & Rata-Rata per 1 VS",
                                primaryValue = "± ${metrics.formattedAvgVsDuration} / 1 File VS",
                                secondaryNote = "• Total Durasi Asli $activeLCount Video (L1..L$activeLCount): ${metrics.formattedBaseClipDuration} ${if (metrics.isExactVideoDuration) "(Durasi Asli File)" else "(Estimasi @15 dtk/video)"}\n" +
                                    "• Rentang Durasi per 1 VS (Loop ${minLoops}x s/d ${maxLoops}x): ${metrics.formattedVsDurationRange}\n" +
                                    "• Rata-rata Pengulangan per Segmen Video: ${String.format(java.util.Locale("id", "ID"), "%.1fx", metrics.avgLoopFactor)} Loop",
                                valueColor = Color(0xFFFBBF24)
                            )

                            // 4. BARIS DAFTAR: SIMULASI KEBUTUHAN PUTARAN LIVE STREAMING (JAM & HARI)
                            Sv4DetailListItemRow(
                                numberBadge = "4",
                                title = "Estimasi Kebutuhan Putaran Saat Live Streaming",
                                primaryValue = "Live 24 Jam (1 Hari) = ± ${com.tajasb.infinity.Sv4VariationCalculator.formatCount(metrics.vsCountFor24Hours.toLong())} VS",
                                secondaryNote = "• Live 6 Jam Non-Stop: ± ${com.tajasb.infinity.Sv4VariationCalculator.formatCount(metrics.vsCountFor6Hours.toLong())} putaran VS (VS1 s/d VS${metrics.vsCountFor6Hours})\n" +
                                    "• Live 12 Jam Non-Stop: ± ${com.tajasb.infinity.Sv4VariationCalculator.formatCount(metrics.vsCountFor12Hours.toLong())} putaran VS (VS1 s/d VS${metrics.vsCountFor12Hours})\n" +
                                    "• Live 24 Jam (1 Hari Penuh): ± ${com.tajasb.infinity.Sv4VariationCalculator.formatCount(metrics.vsCountFor24Hours.toLong())} putaran VS\n" +
                                    "• Live 7 Hari (1 Minggu): ± ${com.tajasb.infinity.Sv4VariationCalculator.formatCount(metrics.vsCountFor7Days.toLong())} putaran VS",
                                valueColor = EmeraldGreen
                            )

                            // 5. BARIS DAFTAR: RINCIAN TIAP VIDEO SUMBER L1..Ln & PENGHEMATAN MEMORI
                            val perVideoBreakdown = activeLVideos.mapIndexed { idx, item ->
                                val dMs = com.tajasb.infinity.Sv4VariationCalculator.resolveVideoDurationMs(item)
                                "L${idx + 1}: ${item.name.take(22)} (${com.tajasb.infinity.Sv4VariationCalculator.formatDurationShort(dMs)})"
                            }.joinToString("\n• ", prefix = "• ")

                            Sv4DetailListItemRow(
                                numberBadge = "5",
                                title = "Daftar Video Sumber (L1..L$activeLCount) & Memori HP",
                                primaryValue = "Auto-Hapus Aktif (${ifsState.sv4AutoDeletedCount} VS Lama Dihapus)",
                                secondaryNote = "$perVideoBreakdown\n" +
                                    "• Penyimpanan Aman: Hanya menahan 1 file VS aktif + 1 file VS cadangan di memori HP.",
                                valueColor = Color(0xFFE879F9)
                            )
                        }
                    }
                }


                // BADGE GUARD 2 MENIT: BEBAS KEMBAR KOREOGRAFI, SUARA & ANTI-DETEK
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_sv4_two_minute_guard"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF022C22)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(EmeraldGreen),
                        width = 0.9.dp
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🛡️ GUARD 2 MENIT SV4 (BEBAS KEMBAR AKTIF)",
                                fontSize = 8.2.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = EmeraldGreen
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(EmeraldGreen.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "120s Window",
                                    fontSize = 6.8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• Koreografi: Unik (${ifsState.sv4GuardChoreographyCount2m} dlm 2m)",
                                fontSize = 7.3.sp,
                                color = Color(0xFFA7F3D0),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "• Zoom: ${String.format(java.util.Locale.US, "%.2fx", ifsState.sv4GuardCurrentZoom)}",
                                fontSize = 7.3.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "• DNA: ${ifsState.sv4GuardCurrentDnaBadge}",
                                fontSize = 7.3.sp,
                                color = Color(0xFFFDE68A),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Kotak Status VS Aktif (Masuk Daftar #1 & Sedang Di-Play)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF06281E))
                        .border(0.8.dp, EmeraldGreen.copy(alpha = 0.75f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎬 DAFTAR #1 (AKTIF PLAY): VS${ifsState.sv4CurrentVsNumber}",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EmeraldGreen
                        )
                        Text(
                            text = "ON-AIR • Gabungan Utuh",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA7F3D0)
                        )
                    }
                    Text(
                        text = ifsState.sv4ActiveFormula.ifBlank { "Menyiapkan susunan L1..Ln..." },
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Kotak Status VS Berikutnya (Sedang Digabung / Sudah Masuk Daftar #2)
                val isNextReady = ifsState.sv4NextReady
                val isStitching = ifsState.sv4IsStitchingNext
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isNextReady) Color(0xFF083344) else Color(0xFF09101D))
                        .border(
                            0.8.dp,
                            if (isNextReady) NeonCyan else Color(0xFF334155),
                            RoundedCornerShape(5.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isNextReady -> "✅ DAFTAR #2 (SIAP PLAY): VS${ifsState.sv4NextVsNumber}"
                                isStitching -> "⚙️ GABUNG BACKGROUND ➡️ DAFTAR #2: VS${ifsState.sv4NextVsNumber} (${ifsState.sv4NextStitchPercent}%)"
                                else -> "⏳ ANTREAN BERIKUTNYA: VS${ifsState.sv4NextVsNumber}"
                            },
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isNextReady) NeonCyan else Color(0xFFFCD34D)
                        )
                        Text(
                            text = "🗑️ Dihapus: ${ifsState.sv4AutoDeletedCount} VS lama",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Text(
                        text = ifsState.sv4NextFormula.ifBlank { "Menunggu giliran penggabungan..." },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                }
            }
        }

        // KARTU 2: DAFTAR VIDEO L (L1, L2, L3, L4...) & RENTANG LOOP DINAMIS (DEFAULT 1..2X)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(NeonCyan.copy(alpha = 0.7f)),
                width = 0.9.dp
            ),
            shape = RoundedCornerShape(7.dp)
        ) {
            Column(
                modifier = Modifier.padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Baris Pengaturan Rentang Loop Dinamis L (Default 1..2x)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF09101D))
                        .border(0.6.dp, Color(0xFF1E293B), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎲 Loop Dinamis L:",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA7F3D0)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SmallStepButton(
                            text = "−",
                            onClick = {
                                if (minLoops > 1) onUpdateLoopRange(minLoops - 1, maxLoops)
                            }
                        )
                        Text(
                            text = "${minLoops}x",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        SmallStepButton(
                            text = "+",
                            onClick = {
                                val nextMin = (minLoops + 1).coerceAtMost(8)
                                val nextMax = maxLoops.coerceAtLeast(nextMin)
                                onUpdateLoopRange(nextMin, nextMax)
                            }
                        )

                        Text(
                            text = "s/d",
                            fontSize = 8.5.sp,
                            color = Color.Gray
                        )

                        SmallStepButton(
                            text = "−",
                            onClick = {
                                if (maxLoops > 1) {
                                    val nextMax = maxLoops - 1
                                    val nextMin = minLoops.coerceAtMost(nextMax)
                                    onUpdateLoopRange(nextMin, nextMax)
                                }
                            }
                        )
                        Text(
                            text = "${maxLoops}x",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonCyan
                        )
                        SmallStepButton(
                            text = "+",
                            onClick = {
                                if (maxLoops < 10) onUpdateLoopRange(minLoops, maxLoops + 1)
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(1 to 1, 1 to 2, 1 to 3, 2 to 3).forEach { (pMin, pMax) ->
                            val isPresetSelected = minLoops == pMin && maxLoops == pMax
                            val presetLabel = if (pMin == pMax) "${pMin}x" else "$pMin..${pMax}x"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isPresetSelected) NeonCyan.copy(alpha = 0.22f) else DarkSurface)
                                    .border(0.5.dp, if (isPresetSelected) NeonCyan else Color(0xFF334155), RoundedCornerShape(3.dp))
                                    .clickable { onUpdateLoopRange(pMin, pMax) }
                                    .padding(horizontal = 4.5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = presetLabel,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPresetSelected) NeonCyan else Color.LightGray
                                )
                            }
                        }
                    }
                }

                // Header Daftar Video L1, L2, L3, L4... (DISEMBUNYIKAN DEFAULT AGAR DIRACIK DI BELAKANG LAYAR)
                var isSourceLListExpanded by rememberSaveable { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF071A21))
                        .border(0.6.dp, EmeraldGreen.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                        .clickable { isSourceLListExpanded = !isSourceLListExpanded }
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = if (lVideoIds.isNotEmpty()) {
                                "🔒 ${lVideoIds.size} Bahan Video (L1..L${lVideoIds.size}) Diracik di Belakang Layar"
                            } else {
                                "🎞️ Sumber Video Variasi (Belum ada L dipilih)"
                            },
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                        Text(
                            text = if (isSourceLListExpanded) {
                                "Menampilkan daftar bahan L1..L${lVideoIds.size.coerceAtLeast(1)} • Ketuk untuk tutup ▴"
                            } else {
                                "Video upload disembunyikan agar rapi & otomatis dirakit menjadi VS • Kelola Bahan ▾"
                            },
                            fontSize = 7.3.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .testTag("btn_sv4_pick_l_group")
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonCyan.copy(alpha = 0.2f))
                                .border(0.7.dp, NeonCyan, RoundedCornerShape(4.dp))
                                .clickable(onClick = onOpenLMultiSelect)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "☑️ Pilih L (${lVideoIds.size})",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isSourceLListExpanded || lVideoIds.isEmpty()) {
                    if (lVideoIds.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(DarkBackground)
                                .border(0.6.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                                .clickable(onClick = onOpenLMultiSelect)
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Belum ada video L dipilih. Ketuk '☑️ Pilih L' untuk memilih L1, L2, L3, L4...",
                                fontSize = 8.5.sp,
                                color = Color.Gray
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.5.dp)
                        ) {
                            lVideoIds.forEachIndexed { idx, lId ->
                                val lNum = idx + 1
                                val item = playlist.firstOrNull { it.id == lId }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(DarkBackground)
                                        .border(0.6.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(EmeraldGreen.copy(alpha = 0.22f))
                                                .border(0.5.dp, EmeraldGreen, RoundedCornerShape(3.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        ) {
                                            Text(
                                                text = "L$lNum",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = EmeraldGreen
                                            )
                                        }

                                        Text(
                                            text = item?.name ?: "Video Tidak Ditemukan",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = if (minLoops == maxLoops) "Loop ${minLoops}x" else "Loop ${minLoops}..${maxLoops}x",
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NeonCyan
                                        )
                                        Text(
                                            text = "✕",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF5252),
                                            modifier = Modifier
                                                .clickable { onRemoveLVideo(lId) }
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sv4DetailListItemRow(
    numberBadge: String,
    title: String,
    primaryValue: String,
    secondaryNote: String,
    valueColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF07222B))
            .border(0.6.dp, Color(0xFF1E3A47), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 5.5.dp),
        verticalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(valueColor.copy(alpha = 0.2f))
                        .border(0.7.dp, valueColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = numberBadge,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = valueColor,
                        lineHeight = 8.sp
                    )
                }
                Text(
                    text = title,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE2E8F0)
                )
            }

            Text(
                text = primaryValue,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
        }

        Text(
            text = secondaryNote,
            fontSize = 7.5.sp,
            color = Color(0xFFCBD5E1),
            lineHeight = 10.5.sp
        )
    }
}

@Composable
fun Sv4LMultiSelectDialog(
    playlist: List<VideoItem>,
    selectedLIds: List<String>,
    minLoops: Int,
    maxLoops: Int,
    onDismiss: () -> Unit,
    onSaveSelection: (List<String>) -> Unit
) {
    val sourcePlaylist = remember(playlist) { playlist.filterNot { it.id.startsWith("vs_rolling_") } }
    var currentSelection by remember(selectedLIds) { mutableStateOf(selectedLIds.filterNot { it.startsWith("vs_rolling_") }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "🧬 Pilih Video Sumber Variasi Susunan (L1, L2, L3, L4...)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGreen
                )
                Text(
                    text = "Video L yang dipilih akan diacak urutan susunannya sekaligus diacak jumlah loop (${minLoops}..${maxLoops}x) untuk dirakit otomatis menjadi VS1, VS2, VS3 dst.",
                    fontSize = 9.5.sp,
                    color = Color.LightGray
                )
            }
        },
        text = {
            if (sourcePlaylist.isEmpty()) {
                Text(
                    text = "Belum ada video di daftar putar. Ketuk tombol 'Tambah Video' terlebih dahulu.",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terpilih: ${currentSelection.size} Video (L1..L${currentSelection.size.coerceAtLeast(1)})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonCyan.copy(alpha = 0.18f))
                                    .border(0.6.dp, NeonCyan, RoundedCornerShape(4.dp))
                                    .clickable {
                                        currentSelection = sourcePlaylist.map { it.id }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("Pilih Semua", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF3F1D2B))
                                    .border(0.6.dp, Color(0xFFFF5252), RoundedCornerShape(4.dp))
                                    .clickable { currentSelection = emptyList() }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("Kosongkan", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(sourcePlaylist.size) { idx ->
                            val video = sourcePlaylist[idx]
                            val lOrderIdx = currentSelection.indexOf(video.id)
                            val isChecked = lOrderIdx >= 0

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isChecked) Color(0xFF06281E) else DarkSurfaceVariant)
                                    .border(
                                        width = if (isChecked) 1.dp else 0.5.dp,
                                        color = if (isChecked) EmeraldGreen else Color(0xFF334155),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        currentSelection = if (isChecked) {
                                            currentSelection.filter { it != video.id }
                                        } else {
                                            currentSelection + video.id
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = EmeraldGreen,
                                        uncheckedColor = Color.Gray,
                                        checkmarkColor = Color.Black
                                    ),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "#${idx + 1} ${video.name}",
                                        fontSize = 10.5.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${video.width}x${video.height} • ${formatDuration(video.durationMs)}",
                                        fontSize = 9.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (isChecked) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(EmeraldGreen)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "L${lOrderIdx + 1}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveSelection(currentSelection) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Text("Simpan Video L (${currentSelection.size})", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color.LightGray, fontSize = 11.sp)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun Sv3ConfigurationCards(
    playlist: List<VideoItem>,
    vlVideoId: String?,
    minLoops: Int,
    maxLoops: Int,
    vbVideoIds: List<String>,
    activeSubPhase: IfsSubPhase,
    currentVlLoop: Int,
    targetVlLoops: Int,
    activeVbNumber: Int,
    remainingVbInCycle: Int,
    onPickVlVideo: () -> Unit,
    onUpdateLoopRange: (Int, Int) -> Unit,
    onOpenVbMultiSelect: () -> Unit,
    onRemoveVbVideo: (String) -> Unit
) {
    val vlItem = playlist.firstOrNull { it.id == vlVideoId } ?: playlist.firstOrNull()
    val isVlActive = activeSubPhase != IfsSubPhase.VB

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // KARTU 1: 1 VIDEO VL (LOOPING ACAK NON-REPEAT DARI MIN..MAX)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(if (isVlActive) EmeraldGreen else DarkCardBorder),
                width = if (isVlActive) 1.2.dp else 0.8.dp
            ),
            shape = RoundedCornerShape(7.dp)
        ) {
            Column(
                modifier = Modifier.padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(EmeraldGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "👑 VL (1 Video Looping Acak)",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }

                        FeatureInfoButton(
                            title = "Cara Kerja VL (Video Looping Acak)",
                            description = "• VL hanya berisi 1 video utama.\n" +
                                "• Setiap kali masuk ke VL, jumlah putarannya diundi secara ACAK NON-REPEATING dari rentang [Min s/d Max] putaran (sama seperti logika acak di Mode Standar, tidak pernah mengulang angka putaran yang sama sampai semua angka di rentang tersebut kebagian giliran).\n" +
                                "• VL ditunggu sampai seluruh putaran acaknya selesai (habis), lalu otomatis berpindah ke 1 video dari Grup VB.",
                            accentColor = EmeraldGreen
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isVlActive) EmeraldGreen.copy(alpha = 0.2f) else DarkBackground)
                            .border(0.6.dp, if (isVlActive) EmeraldGreen else Color(0xFF334155), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isVlActive) "● Putaran $currentVlLoop / ${targetVlLoops}x" else "Target Berikut: Acak $minLoops..${maxLoops}x",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVlActive) EmeraldGreen else Color.LightGray
                        )
                    }
                }

                // Pemilih 1 Video VL
                CompactVideoSlotChip(
                    badge = "VIDEO VL",
                    videoName = vlItem?.let { if (vlVideoId == null) "${it.name} (Auto #1)" else it.name },
                    isCurrentPlaying = isVlActive,
                    onClick = onPickVlVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_sv3_pick_vl_video")
                )

                // Pengaturan Rentang Jumlah Looping Acak (Contoh: Dari Min sampai Max putaran)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF09101D))
                        .border(0.6.dp, Color(0xFF1E293B), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎲 Loop Acak:",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA7F3D0)
                    )

                    // Kontrol Min .. Max Putaran
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Stepper Min
                        SmallStepButton(
                            text = "−",
                            onClick = {
                                if (minLoops > 1) onUpdateLoopRange(minLoops - 1, maxLoops)
                            }
                        )
                        Text(
                            text = "$minLoops",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        SmallStepButton(
                            text = "+",
                            onClick = {
                                val nextMin = (minLoops + 1).coerceAtMost(25)
                                val nextMax = maxLoops.coerceAtLeast(nextMin)
                                onUpdateLoopRange(nextMin, nextMax)
                            }
                        )

                        Text(
                            text = "sampai",
                            fontSize = 8.5.sp,
                            color = Color.Gray
                        )

                        // Stepper Max
                        SmallStepButton(
                            text = "−",
                            onClick = {
                                if (maxLoops > minLoops) onUpdateLoopRange(minLoops, maxLoops - 1)
                            }
                        )
                        Text(
                            text = "${maxLoops}x",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonCyan
                        )
                        SmallStepButton(
                            text = "+",
                            onClick = {
                                if (maxLoops < 30) onUpdateLoopRange(minLoops, maxLoops + 1)
                            }
                        )
                    }

                    // Preset Cepat Rentang Loop
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(1 to 3, 2 to 5, 3 to 7).forEach { (pMin, pMax) ->
                            val isPresetSelected = minLoops == pMin && maxLoops == pMax
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isPresetSelected) NeonCyan.copy(alpha = 0.22f) else DarkSurface)
                                    .border(0.5.dp, if (isPresetSelected) NeonCyan else Color(0xFF334155), RoundedCornerShape(3.dp))
                                    .clickable { onUpdateLoopRange(pMin, pMax) }
                                    .padding(horizontal = 4.5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$pMin..$pMax",
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPresetSelected) NeonCyan else Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }

        // KARTU 2: GRUP VB (VB1, VB2, VB3, DST. • ACAK NON-REPEATING 1X PER SIKLUS)
        val isVbActive = activeSubPhase == IfsSubPhase.VB
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(if (isVbActive) NeonCyan else DarkCardBorder),
                width = if (isVbActive) 1.2.dp else 0.8.dp
            ),
            shape = RoundedCornerShape(7.dp)
        ) {
            Column(
                modifier = Modifier.padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonCyan.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🌉 Grup VB (VB1..VB${vbVideoIds.size.coerceAtLeast(1)})",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }

                        FeatureInfoButton(
                            title = "Cara Kerja Grup VB (Acak Non-Repeating)",
                            description = "• Setelah VL menyelesaikan putaran acaknya, sistem memutar 1 video dari Grup VB (VB1, VB2, dst.) secara acak.\n" +
                                "• Jaminan 1x Giliran Penuh: Setiap video di Grup VB WAJIB kebagian 1x giliran dalam 1 siklus dan TIDAK BOLEH terpilih 2x sebelum semua anggota Grup VB selesai diputar.\n" +
                                "• Setelah 1 video VB selesai (habis), sistem otomatis kembali ke VL untuk looping acak berikutnya.",
                            accentColor = NeonCyan
                        )

                        if (vbVideoIds.isNotEmpty()) {
                            Text(
                                text = "Siklus: $remainingVbInCycle/${vbVideoIds.size}",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .testTag("btn_sv3_pick_vb_group")
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonCyan.copy(alpha = 0.2f))
                            .border(0.7.dp, NeonCyan, RoundedCornerShape(4.dp))
                            .clickable(onClick = onOpenVbMultiSelect)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "☑️ Pilih Multi VB (${vbVideoIds.size})",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }
                }

                if (vbVideoIds.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(DarkBackground)
                            .border(0.6.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                            .clickable(onClick = onOpenVbMultiSelect)
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada video di Grup VB. Ketuk '☑️ Pilih Multi VB' untuk memilih VB1, VB2, dst.",
                            fontSize = 8.5.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.5.dp)
                    ) {
                        vbVideoIds.forEachIndexed { idx, vbId ->
                            val vbNum = idx + 1
                            val item = playlist.firstOrNull { it.id == vbId }
                            val isThisVbPlaying = isVbActive && activeVbNumber == vbNum

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (isThisVbPlaying) Color(0xFF083344) else DarkBackground)
                                    .border(
                                        width = if (isThisVbPlaying) 1.dp else 0.6.dp,
                                        color = if (isThisVbPlaying) NeonCyan else Color(0xFF334155),
                                        shape = RoundedCornerShape(5.dp)
                                    )
                                    .padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (isThisVbPlaying) NeonCyan else Color(0xFF1E293B))
                                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    ) {
                                        Text(
                                            text = "VB$vbNum",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isThisVbPlaying) Color.Black else NeonCyan
                                        )
                                    }

                                    Text(
                                        text = item?.name ?: "Video Tidak Ditemukan",
                                        fontSize = 8.5.sp,
                                        fontWeight = if (isThisVbPlaying) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isThisVbPlaying) NeonCyan else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (isThisVbPlaying) {
                                        Text(
                                            text = "▶ TAYANG 1X",
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan
                                        )
                                    }
                                    Text(
                                        text = "✕",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF5252),
                                        modifier = Modifier
                                            .clickable { onRemoveVbVideo(vbId) }
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallStepButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(DarkSurfaceVariant)
            .border(0.6.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = NeonCyan
        )
    }
}

@Composable
fun Sv3VbMultiSelectDialog(
    playlist: List<VideoItem>,
    vlVideoId: String?,
    selectedVbIds: List<String>,
    onDismiss: () -> Unit,
    onSaveSelection: (List<String>) -> Unit
) {
    var currentSelection by remember(selectedVbIds) { mutableStateOf(selectedVbIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "🌉 Pilih Multi Video Grup VB (VB1, VB2, dst.)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Text(
                    text = "Setiap video VB yang dicentang wajib kebagian 1x giliran acak per siklus sebelum diacak ulang.",
                    fontSize = 9.5.sp,
                    color = Color.LightGray
                )
            }
        },
        text = {
            if (playlist.isEmpty()) {
                Text(
                    text = "Belum ada video di daftar putar. Ketuk tombol 'Tambah Video' terlebih dahulu.",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terpilih: ${currentSelection.size} Video Bridge",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonCyan.copy(alpha = 0.18f))
                                    .border(0.6.dp, NeonCyan, RoundedCornerShape(4.dp))
                                    .clickable {
                                        currentSelection = playlist
                                            .filter { it.id != vlVideoId }
                                            .map { it.id }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("Pilih Semua Non-VL", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF3F1D2B))
                                    .border(0.6.dp, Color(0xFFFF5252), RoundedCornerShape(4.dp))
                                    .clickable { currentSelection = emptyList() }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("Kosongkan", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(playlist.size) { idx ->
                            val video = playlist[idx]
                            val vbOrderIdx = currentSelection.indexOf(video.id)
                            val isChecked = vbOrderIdx >= 0
                            val isVl = video.id == vlVideoId

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isChecked) Color(0xFF083344) else DarkSurfaceVariant)
                                    .border(
                                        width = if (isChecked) 1.dp else 0.5.dp,
                                        color = if (isChecked) NeonCyan else Color(0xFF334155),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        currentSelection = if (isChecked) {
                                            currentSelection.filter { it != video.id }
                                        } else {
                                            currentSelection + video.id
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeonCyan,
                                        uncheckedColor = Color.Gray,
                                        checkmarkColor = Color.Black
                                    ),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "#${idx + 1} ${video.name}",
                                        fontSize = 10.5.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "${video.width}x${video.height} • ${formatDuration(video.durationMs)}",
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                        if (isVl) {
                                            Text(
                                                text = "👑 Video VL",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldGreen
                                            )
                                        }
                                    }
                                }
                                if (isChecked) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(NeonCyan)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "VB${vbOrderIdx + 1}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveSelection(currentSelection) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("Simpan Grup VB (${currentSelection.size})", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color.LightGray, fontSize = 11.sp)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun VtPackageCard(
    pkg: IfsVtPackage,
    videoMode: IfsVideoMode,
    isScEnabled: Boolean,
    playlist: List<VideoItem>,
    audioPlaylist: List<AudioItem>,
    canDelete: Boolean,
    isCurrentActive: Boolean,
    activeSubPhase: IfsSubPhase,
    onPickSlot: (IfsSubPhase) -> Unit,
    onPickAudioAx: () -> Unit,
    onUpdateKeyword: (String) -> Unit,
    onTestTrigger: () -> Unit,
    onDelete: () -> Unit
) {
    val requiresThree = pkg.requiresThreeVideos(videoMode)
    val shortCode = pkg.getShortCode(videoMode)
    val axCode = pkg.getAxCode(videoMode)
    val explicitAudio = pkg.linkedAudioId?.let { id -> audioPlaylist.firstOrNull { it.id == id } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                when {
                    isCurrentActive -> NeonCyan
                    pkg.isVtm -> Color(0xFFF59E0B).copy(alpha = 0.6f)
                    else -> DarkCardBorder
                }
            ),
            width = if (isCurrentActive) 1.2.dp else 0.8.dp
        ),
        shape = RoundedCornerShape(7.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // BARIS 1: HEADER SEJAJAR (Nama Paket + ! + Audio AX Pill + Trigger Button + Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (pkg.isVtm) Color(0xFFF59E0B).copy(alpha = 0.2f)
                                else NeonCyan.copy(alpha = 0.18f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (pkg.isVtm) "👑 ABA" else if (videoMode == IfsVideoMode.SV2) "👑 $shortCode (ABA)" else "🎬 $shortCode",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pkg.isVtm) Color(0xFFFCD34D) else NeonCyan
                        )
                    }

                    FeatureInfoButton(
                        title = if (pkg.isVtm) "Paket ABA (Standby & Transisi)" else if (videoMode == IfsVideoMode.SV2) "Paket Master $shortCode (ABA)" else "Paket $shortCode",
                        description = if (pkg.isVtm) {
                            "Paket ABA (A+B+A) Standby & Transisi:\n" +
                                "• ABB: Video awal sebelum masuk loop standby.\n" +
                                "• BL: Video standby yang berputar terus-menerus (Looping Standby).\n" +
                                "• BBT: Video transisi keluar saat ada VT yang dipicu penonton/host.\n\n" +
                                "• Audio: Memutar Musik Latar (SR) secara acak. Tidak ada audio AX karena ABA adalah kondisi standby istirahat."
                        } else if (videoMode == IfsVideoMode.SV2) {
                            "Paket Master $shortCode (A+B+A):\n" +
                                "• ABB: Video awal pembuka $shortCode.\n" +
                                "• BL: Video fokus produk $shortCode yang berputar terus-menerus (Looping).\n" +
                                "• BBT: Video transisi keluar $shortCode sebelum berpindah ke PM berikutnya.\n\n" +
                                "• Audio $axCode: Jika diisi, memutar $axCode saat $shortCode aktif."
                        } else {
                            "Paket $shortCode (1 Video Konten):\n" +
                                "• Saat $shortCode terpicu, ABA menyelesaikan transisi keluarnya (BBT), memutar video $shortCode 1x hingga selesai, lalu otomatis kembali ke ABA.\n\n" +
                                "• Audio $axCode: Jika diisi, musik latar SR dijeda sementara dan $axCode diputar bersama $shortCode. Setelah selesai, SR lanjut otomatis."
                        },
                        accentColor = if (pkg.isVtm) Color(0xFFFCD34D) else NeonCyan
                    )

                    if (isCurrentActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(EmeraldGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "AKTIF",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }
                    }
                }

                // Sisi Kanan: Pemilih Audio AX Sejajar + Tombol Trigger + Hapus
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (pkg.isVtm) {
                        // ABA di SV1 memutar musik latar acak SR (tidak ada audio AX)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E1B4B))
                                .border(0.6.dp, Color(0xFF6366F1), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "🎵 SR (Musik Acak)",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA5B4FC)
                            )
                        }

                        // Tombol Standby Default
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF451A03))
                                .border(0.6.dp, Color(0xFFF59E0B), RoundedCornerShape(4.dp))
                                .clickable { onTestTrigger() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "🔄 Ke Standby ABA",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFCD34D)
                            )
                        }
                    } else {
                        // Tombol Pilih Audio AX Ringkas Sejajar
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (explicitAudio != null) Color(0xFF78350F) else Color(0xFF1E1B4B))
                                .border(
                                    0.6.dp,
                                    if (explicitAudio != null) Color(0xFFF59E0B) else Color(0xFF475569),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { onPickAudioAx() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (explicitAudio != null) "🔊 $axCode ✓" else "🎵 SR ▾",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (explicitAudio != null) Color(0xFFFDE68A) else Color(0xFFA5B4FC)
                            )
                        }

                        // Tombol Trigger
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonCyan.copy(alpha = 0.18f))
                                .border(0.6.dp, NeonCyan, RoundedCornerShape(4.dp))
                                .clickable { onTestTrigger() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (videoMode == IfsVideoMode.SV2) "▶ Tes $shortCode" else "▶ Tes Trigger",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }
                    }

                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Hapus $shortCode",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // BARIS 2: SLOT VIDEO SEJAJAR (3 Kolom untuk ABB/BL/BBT atau 1 Baris untuk Konten)
            if (requiresThree) {
                val abbItem = playlist.firstOrNull { it.id == pkg.abbVideoId }
                val blItem = playlist.firstOrNull { it.id == pkg.blVideoId }
                val bbtItem = playlist.firstOrNull { it.id == pkg.bbtVideoId }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactVideoSlotChip(
                        badge = "ABB",
                        videoName = abbItem?.name,
                        isCurrentPlaying = isCurrentActive && activeSubPhase == IfsSubPhase.ABB,
                        onClick = { onPickSlot(IfsSubPhase.ABB) },
                        modifier = Modifier.weight(1f)
                    )
                    CompactVideoSlotChip(
                        badge = "BL",
                        videoName = blItem?.name,
                        isCurrentPlaying = isCurrentActive && activeSubPhase == IfsSubPhase.BL,
                        onClick = { onPickSlot(IfsSubPhase.BL) },
                        modifier = Modifier.weight(1f)
                    )
                    CompactVideoSlotChip(
                        badge = "BBT",
                        videoName = bbtItem?.name,
                        isCurrentPlaying = isCurrentActive && activeSubPhase == IfsSubPhase.BBT,
                        onClick = { onPickSlot(IfsSubPhase.BBT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                val videoItem = playlist.firstOrNull { it.id == pkg.blVideoId }
                CompactVideoSlotChip(
                    badge = "VIDEO",
                    videoName = videoItem?.name,
                    isCurrentPlaying = isCurrentActive && activeSubPhase == IfsSubPhase.CONTENT,
                    onClick = { onPickSlot(IfsSubPhase.CONTENT) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // BARIS 3 (Kondisional): Input Kata Kunci SC hanya tampil jika SC Aktif & bukan VTM
            if (!pkg.isVtm && isScEnabled) {
                OutlinedTextField(
                    value = pkg.triggerKeyword,
                    onValueChange = onUpdateKeyword,
                    placeholder = {
                        Text(
                            "Kata kunci komentar $shortCode...",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray
                    ),
                    textStyle = TextStyle(fontSize = 12.sp, color = Color.White)
                )
            }
        }
    }
}

@Composable
private fun CompactVideoSlotChip(
    badge: String,
    videoName: String?,
    isCurrentPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSet = videoName != null
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (isCurrentPlaying) NeonCyan.copy(alpha = 0.16f) else DarkBackground)
            .border(
                width = 0.8.dp,
                color = when {
                    isCurrentPlaying -> NeonCyan
                    isSet -> EmeraldGreen.copy(alpha = 0.55f)
                    else -> Color(0xFF334155)
                },
                shape = RoundedCornerShape(5.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = badge,
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            color = when {
                isCurrentPlaying -> NeonCyan
                isSet -> EmeraldGreen
                else -> Color(0xFF94A3B8)
            }
        )
        Text(
            text = videoName ?: "Pilih ▾",
            fontSize = 8.5.sp,
            fontWeight = if (isSet) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSet) Color.White else Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun VideoSlotPickerDialog(
    targetTitle: String,
    playlist: List<VideoItem>,
    onDismiss: () -> Unit,
    onSelectVideo: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🎬 Pilih Video • $targetTitle",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )
        },
        text = {
            if (playlist.isEmpty()) {
                Text(
                    text = "Belum ada video di daftar. Ketuk tombol 'Tambah Video' terlebih dahulu.",
                    fontSize = 11.5.sp,
                    color = Color.LightGray
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(playlist.size) { idx ->
                        val video = playlist[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                .clickable { onSelectVideo(video.id) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#${idx + 1} ${video.name}",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${video.width}x${video.height} • ${formatDuration(video.durationMs)}",
                                    fontSize = 9.5.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelectVideo(null) }) {
                Text("Kosongkan Slot", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = Color.LightGray, fontSize = 11.sp)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun AudioAxPickerDialog(
    pkg: IfsVtPackage,
    videoMode: IfsVideoMode,
    audioPlaylist: List<AudioItem>,
    onDismiss: () -> Unit,
    onSelectAudio: (String?) -> Unit,
    onTestPlayAx: () -> Unit
) {
    val shortCode = pkg.shortCode
    val axCode = pkg.axCode

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "🔊 Pilih Audio $axCode • $shortCode",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFCD34D)
                )
                FeatureInfoButton(
                    title = "Cara Kerja Audio $axCode",
                    description = "Saat paket $shortCode terpicu, musik latar (SR) otomatis dijeda sementara dan sistem memutar audio $axCode hingga selesai, lalu melanjutkan SR.",
                    accentColor = Color(0xFFFCD34D)
                )
            }
        },
        text = {
            if (audioPlaylist.isEmpty()) {
                Text(
                    text = "Belum ada file audio di Tab Musik. Silakan tambahkan file MP3 terlebih dahulu.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(audioPlaylist.size) { idx ->
                        val track = audioPlaylist[idx]
                        val isSelected = pkg.linkedAudioId == track.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF78350F).copy(alpha = 0.4f) else DarkSurfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF334155),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectAudio(track.id) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFFFCD34D) else NeonPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "#${idx + 1} ${track.name}",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.sizeBytes / 1024} KB",
                                        fontSize = 9.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            if (isSelected) {
                                Text(
                                    text = "✓ $axCode",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCD34D)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (audioPlaylist.isNotEmpty()) {
                    TextButton(onClick = onTestPlayAx) {
                        Text("▶ Tes $axCode", color = NeonCyan, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = { onSelectAudio(null) }) {
                    Text("🎵 Gunakan SR", color = EmeraldGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = Color.LightGray, fontSize = 10.5.sp)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    )
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / 60000) % 60
    val hours = durationMs / 3600000
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

// =============================================================================
// KOMPONEN BERSAMA SV4 AUDIO — MODE VS AUDIO (ROLLING JIT VSA1, VSA2..N)
// Terhubung sinkron di Tab Musik & Panel Infinity System
// =============================================================================

@Composable
fun Sv4AudioConfigurationSection(
    viewModel: VcamViewModel,
    config: VcamConfig,
    isInsideIfsPanel: Boolean = false
) {
    val context = LocalContext.current
    val ifsManager = remember { IfsManager.getInstance(context) }
    val audioMode by ifsManager.audioMode.collectAsState()
    val sv4AudioLIds by ifsManager.sv4AudioLIds.collectAsState()
    val sv4AudioMinLoops by ifsManager.sv4AudioMinLoops.collectAsState()
    val sv4AudioMaxLoops by ifsManager.sv4AudioMaxLoops.collectAsState()
    val ifsState by ifsManager.ifsState.collectAsState()

    var isPickingAudioLAGroup by remember { mutableStateOf(false) }
    var isAudioSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var isAudioVariationListExpanded by rememberSaveable { mutableStateOf(false) }
    var isSourceAudioLListExpanded by rememberSaveable { mutableStateOf(false) }

    val sourceAudios = remember(config.audioPlaylist) {
        config.audioPlaylist.filterNot {
            it.id.startsWith("vs_audio_rolling_") || it.path.contains("vs_audio_rolling_")
        }
    }
    val validSelectedLAIds = remember(sv4AudioLIds, sourceAudios) {
        sv4AudioLIds.filter { id -> sourceAudios.any { it.id == id } }
    }
    val activeLAAudios = remember(validSelectedLAIds, sourceAudios) {
        if (validSelectedLAIds.isNotEmpty()) {
            validSelectedLAIds.mapNotNull { id -> sourceAudios.find { it.id == id } }
        } else {
            sourceAudios
        }
    }
    val activeLACount = if (activeLAAudios.isNotEmpty()) activeLAAudios.size else sourceAudios.size
    val audioMetrics = remember(activeLACount, sv4AudioMinLoops, sv4AudioMaxLoops, activeLAAudios) {
        com.tajasb.infinity.Sv4VariationCalculator.calculateAudioMetrics(
            lCount = activeLACount,
            minLoops = sv4AudioMinLoops,
            maxLoops = sv4AudioMaxLoops,
            activeAudios = activeLAAudios
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isInsideIfsPanel) "card_sv4_audio_ifs_panel" else "card_sv4_audio_music_tab"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (audioMode == IfsAudioMode.SV4_VS) NeonPurple else DarkCardBorder
            ),
            width = if (audioMode == IfsAudioMode.SV4_VS) 1.1.dp else 0.8.dp
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // BARIS ATAS: PEMILIH MODE AUDIO MP3 BEBAS (URUT / ACAK SR / LOOP 1 LAGU / SV4 AUDIO)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "🎵 Mode Audio MP3 (Bebas Pilih):",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                        FeatureInfoButton(
                            title = "Pemilih Mode Audio MP3 Independen",
                            description = "Mode Audio MP3 bekerja mandiri dan bebas dipilih kapan saja tanpa terpaku pada mode video (Standar, Spill, SV1, SV2, SV3, maupun SV4):\n\n" +
                                "• 🔁 Urut (Sequential): Memutar daftar lagu MP3 secara berurutan dari atas ke bawah + mendukung Trigger AX paket.\n" +
                                "• 🔀 Acak / SR: Memutar lagu secara acak cerdas (tanpa mengulang lagu yang sama berturut-turut) + Trigger AX.\n" +
                                "• 🔂 Loop 1 Lagu: Mengulang 1 lagu MP3 yang sedang Anda pilih secara terus-menerus.\n" +
                                "• 🧬 SV4 Audio (Mode VS Rolling JIT): Menggabungkan fisik trek audio secara acak menjadi VSA1, VSA2.. non-stop di latar belakang.",
                            accentColor = NeonPurple
                        )
                    }

                    if (audioMode == IfsAudioMode.SV4_VS) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0F172A))
                                .border(0.6.dp, NeonPurple.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .clickable { isAudioSectionExpanded = !isAudioSectionExpanded }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isAudioSectionExpanded) "Tutup ▴" else "Panel VSA ▾",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE879F9)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pill 1: Urut (Sequential)
                    val isSeq = audioMode == IfsAudioMode.SEQUENTIAL
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_audio_mode_sequential")
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSeq) EmeraldGreen.copy(alpha = 0.2f) else Color(0xFF0F172A))
                            .border(0.6.dp, if (isSeq) EmeraldGreen else Color(0xFF334155), RoundedCornerShape(5.dp))
                            .clickable {
                                ifsManager.setAudioMode(IfsAudioMode.SEQUENTIAL)
                                viewModel.showUserMessage("🔁 Mode Audio MP3: Urut (Sequential)")
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔁 Urut",
                            fontSize = 8.sp,
                            fontWeight = if (isSeq) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSeq) EmeraldGreen else Color.LightGray,
                            maxLines = 1
                        )
                    }

                    // Pill 2: Acak / SR & AX
                    val isSrAx = audioMode == IfsAudioMode.SR_AX
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_audio_mode_sr_ax")
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSrAx) NeonCyan.copy(alpha = 0.2f) else Color(0xFF0F172A))
                            .border(0.6.dp, if (isSrAx) NeonCyan else Color(0xFF334155), RoundedCornerShape(5.dp))
                            .clickable {
                                ifsManager.setAudioMode(IfsAudioMode.SR_AX)
                                viewModel.showUserMessage("🔀 Mode Audio MP3: Acak / SR & AX")
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔀 Acak/SR",
                            fontSize = 8.sp,
                            fontWeight = if (isSrAx) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSrAx) NeonCyan else Color.LightGray,
                            maxLines = 1
                        )
                    }

                    // Pill 3: Loop 1 Lagu
                    val isSingleLoop = audioMode == IfsAudioMode.SINGLE_LOOP
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_audio_mode_single_loop")
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSingleLoop) Color(0xFF78350F).copy(alpha = 0.45f) else Color(0xFF0F172A))
                            .border(0.6.dp, if (isSingleLoop) Color(0xFFF59E0B) else Color(0xFF334155), RoundedCornerShape(5.dp))
                            .clickable {
                                ifsManager.setAudioMode(IfsAudioMode.SINGLE_LOOP)
                                viewModel.showUserMessage("🔂 Mode Audio MP3: Loop 1 Lagu")
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔂 Loop 1x",
                            fontSize = 8.sp,
                            fontWeight = if (isSingleLoop) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSingleLoop) Color(0xFFFCD34D) else Color.LightGray,
                            maxLines = 1
                        )
                    }

                    // Pill 4: SV4 Audio (Mode VS Rolling JIT)
                    val isSv4Audio = audioMode == IfsAudioMode.SV4_VS
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .testTag("btn_audio_mode_sv4_vs")
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSv4Audio) Color(0xFF4C1D95) else Color(0xFF0F172A))
                            .border(0.8.dp, if (isSv4Audio) NeonPurple else Color(0xFF334155), RoundedCornerShape(5.dp))
                            .clickable {
                                ifsManager.setAudioMode(IfsAudioMode.SV4_VS)
                                isAudioSectionExpanded = true
                                viewModel.showUserMessage("🧬 Mode Audio MP3: SV4 Audio (VS Rolling)")
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🧬 SV4 Audio",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSv4Audio) Color(0xFFE879F9) else Color.White,
                            maxLines = 1
                        )
                    }
                }
            }

            // DETAIL PANEL SV4 AUDIO (MODE VS ROLLING JIT)
            AnimatedVisibility(visible = audioMode == IfsAudioMode.SV4_VS && isAudioSectionExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Header Status VSA + Tombol -3d Akhir & Skip VSA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonPurple.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🧬 SV4 Audio — Variasi Susunan Mandiri (VSA Rolling JIT)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE879F9)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .testTag("btn_sv4_audio_seek_last_3s")
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF3B0764))
                                    .border(0.6.dp, NeonPurple, RoundedCornerShape(4.dp))
                                    .clickable { viewModel.seekAudioToLast3Seconds() }
                                    .padding(horizontal = 6.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    text = "⏩ -3d Akhir",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE879F9)
                                )
                            }

                            if (ifsState.sv4AudioNextReady) {
                                Box(
                                    modifier = Modifier
                                        .testTag("btn_sv4_audio_skip_next_vsa")
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(EmeraldGreen.copy(alpha = 0.2f))
                                        .border(0.6.dp, EmeraldGreen, RoundedCornerShape(4.dp))
                                        .clickable {
                                            ifsManager.onSv4AudioCompleted(forceAdvance = true)
                                            viewModel.showUserMessage("⏭️ Beralih ke VSA${ifsState.sv4AudioNextVsNumber}...")
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = "⏭️ Play VSA${ifsState.sv4AudioNextVsNumber}",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldGreen
                                    )
                                }
                            }
                        }
                    }

                    // BARIS UTAMA START / STOP MODE PERSIAPAN & EDIT SV4 AUDIO
                    val isSv4AudioRunning = ifsState.sv4AudioRunning
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSv4AudioRunning) Color(0xFF052E16) else Color(0xFF2E1065)
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = SolidColor(if (isSv4AudioRunning) EmeraldGreen else Color(0xFFF59E0B)),
                            width = 1.dp
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = if (isSv4AudioRunning) "🟢 SV4 AUDIO AKTIF (NON-STOP)" else "🛠️ TAHAP PERSIAPAN & EDIT",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSv4AudioRunning) EmeraldGreen else Color(0xFFFCD34D)
                                )
                                Text(
                                    text = if (isSv4AudioRunning) "Bahan LA1..LA$activeLACount diracik otomatis • Klik 'Stop' untuk edit" else "Edit massal sebelum SV4 dimulai",
                                    fontSize = 7.5.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            if (!isSv4AudioRunning) {
                                Box(
                                    modifier = Modifier
                                        .testTag("btn_sv4_start_audio_engine")
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(EmeraldGreen)
                                        .clickable {
                                            ifsManager.startSv4AudioEngine(resetCounter = false)
                                            viewModel.showUserMessage("▶️ SV4 Audio dimulai! Merakit VSA1 & menyembunyikan bahan audio mentah.")
                                        }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "▶️ MULAI SV4",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.Black
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .testTag("btn_sv4_stop_audio_engine_edit")
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF7C2D12))
                                        .border(0.8.dp, Color(0xFFF59E0B), RoundedCornerShape(5.dp))
                                        .clickable {
                                            ifsManager.stopSv4AudioEngineForEdit()
                                            viewModel.showUserMessage("⏸️ SV4 Audio dijeda ke Mode Edit! Silakan tambah/edit audio massal.")
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "⏹️ Stop / Edit",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFDE68A)
                                    )
                                }
                            }
                        }
                    }

                    // BARIS TOMBOL RAKIT ULANG & TOMBOL CARD INFO TOTAL VARIASI AUDIO (!) SEBELAH RAKIT ULANG
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tombol Rakit Ulang VSA
                        Card(
                            modifier = Modifier
                                .testTag("btn_sv4_audio_restart_vsa")
                                .clickable {
                                    ifsManager.startSv4AudioEngine(resetCounter = true)
                                    viewModel.showUserMessage("⚡ Merakit ulang Variasi Susunan Audio dari VSA1...")
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1065)),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = SolidColor(NeonPurple),
                                width = 0.9.dp
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = if (isSv4AudioRunning) "⚡ Rakit Ulang" else "▶️ Rakit VSA1",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFE879F9)
                                )
                                Text(
                                    text = "Mulai dari VSA1",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFDDD6FE)
                                )
                            }
                        }

                        // Tombol Card Info Total Variasi Audio (Sebelah Rakit Ulang) dengan Ikon (!) & Daftar Menurun
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("card_sv4_audio_total_variations")
                                .clickable { isAudioVariationListExpanded = !isAudioVariationListExpanded },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAudioVariationListExpanded) Color(0xFF241242) else Color(0xFF170B2B)
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = SolidColor(if (isAudioVariationListExpanded) NeonCyan else NeonPurple.copy(alpha = 0.9f)),
                                width = if (isAudioVariationListExpanded) 1.1.dp else 0.9.dp
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Ikon Tanda Seru (!) seragam dengan yang lain
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(NeonPurple.copy(alpha = 0.2f))
                                            .border(0.8.dp, Color(0xFFE879F9), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "!",
                                            color = Color(0xFFE879F9),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            lineHeight = 10.sp
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = "TOTAL VARIASI AUDIO ($activeLACount AUDIO UPLOAD)",
                                            fontSize = 7.2.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFDDD6FE)
                                        )
                                        Text(
                                            text = if (activeLACount > 0) {
                                                "${audioMetrics.formattedTotalVariations} Variasi • ${audioMetrics.formattedTotalUniqueDaysHours}"
                                            } else {
                                                "0 Variasi (Upload Audio LA1..LAn)"
                                            },
                                            fontSize = 9.2.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = EmeraldGreen,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(NeonCyan.copy(alpha = 0.16f))
                                            .padding(horizontal = 4.5.dp, vertical = 1.dp)
                                    ) {
                                        val audioLoopBadge = if (sv4AudioMinLoops == sv4AudioMaxLoops) "${sv4AudioMinLoops}x" else "${sv4AudioMinLoops}-${sv4AudioMaxLoops}x"
                                        Text(
                                            text = if (activeLACount > 0) "LA1..LA$activeLACount • $audioLoopBadge" else "Loop $audioLoopBadge",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan
                                        )
                                    }
                                    Text(
                                        text = if (isAudioVariationListExpanded) "Tutup Daftar ▴" else "Daftar Detail ▾",
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAudioVariationListExpanded) NeonCyan else Color(0xFFDDD6FE)
                                    )
                                }
                            }
                        }
                    }

                    // DAFTAR MENURUN (EXPANDABLE LIST) DETAIL HARI, JAM, JUMLAH VARIASI AUDIO & KAPASITAS LIVE
                    AnimatedVisibility(visible = isAudioVariationListExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("list_sv4_audio_variation_details")
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0E0720))
                                .border(0.9.dp, NeonPurple.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(NeonPurple.copy(alpha = 0.2f))
                                            .border(0.8.dp, Color(0xFFE879F9), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "!",
                                            color = Color(0xFFE879F9),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            lineHeight = 9.5.sp
                                        )
                                    }
                                    Text(
                                        text = "DAFTAR DETAIL VARIASI AUDIO, HARI, JAM & DURASI LIVE",
                                        fontSize = 8.2.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFE879F9)
                                    )
                                }
                                Text(
                                    text = "Tutup ▴",
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.clickable { isAudioVariationListExpanded = false }
                                )
                            }

                            if (activeLACount <= 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF190D33))
                                        .padding(7.dp)
                                ) {
                                    Text(
                                        text = "Belum ada trek audio sumber yang diupload. Silakan klik 'Tambah Audio' untuk mengupload minimal 2–4 file audio agar kalkulasi Hari, Jam, dan Jumlah Variasi VSA muncul otomatis.",
                                        fontSize = 8.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            } else {
                                // 1. Total Waktu Putar Unik Audio (Hari, Jam, Menit)
                                Sv4DetailListItemRow(
                                    numberBadge = "1",
                                    title = "Total Waktu Putar Audio Unik (Hari, Jam, Menit)",
                                    primaryValue = audioMetrics.formattedTotalUniqueDaysHours,
                                    secondaryNote = "Setara ${audioMetrics.formattedTotalUniqueHoursSummary} musik variasi berputar non-stop tanpa urutan VSA yang kembar",
                                    valueColor = EmeraldGreen
                                )

                                // 2. Rincian Jumlah Variasi Susunan Audio (VSA)
                                Sv4DetailListItemRow(
                                    numberBadge = "2",
                                    title = "Jumlah Total Variasi Susunan Audio (VSA)",
                                    primaryValue = "${audioMetrics.formattedTotalVariations} Variasi Audio",
                                    secondaryNote = "• Susunan Inti ($activeLACount Segmen Penuh): ${audioMetrics.formattedCoreVariations} Variasi\n" +
                                        "• Susunan Rolling (+Segmen Penutup Dinamis): ${audioMetrics.formattedExtendedVariations} Variasi\n" +
                                        "• Rumus Kombinasi: ${activeLACount}! urutan × ${audioMetrics.loopChoices}^$activeLACount loop (${sv4AudioMinLoops}x–${sv4AudioMaxLoops}x)",
                                    valueColor = Color(0xFFE879F9)
                                )

                                // 3. Durasi Audio Upload & Rata-Rata per 1 File VSA
                                Sv4DetailListItemRow(
                                    numberBadge = "3",
                                    title = "Durasi Audio Upload & Rata-Rata per 1 File VSA",
                                    primaryValue = "± ${audioMetrics.formattedAvgVsDuration} / 1 File VSA",
                                    secondaryNote = "• Total Durasi Asli $activeLACount Trek Audio (LA1..LA$activeLACount): ${audioMetrics.formattedBaseClipDuration} ${if (audioMetrics.isExactVideoDuration) "(Durasi Asli File)" else "(Estimasi @30 dtk/audio)"}\n" +
                                        "• Rentang Durasi per 1 VSA (Loop ${sv4AudioMinLoops}x s/d ${sv4AudioMaxLoops}x): ${audioMetrics.formattedVsDurationRange}\n" +
                                        "• Berjalan mandiri non-stop mengikuti durasi file gabungan VSA sendiri",
                                    valueColor = Color(0xFFFBBF24)
                                )

                                // 4. Simulasi Kebutuhan Putaran Audio Live Streaming
                                Sv4DetailListItemRow(
                                    numberBadge = "4",
                                    title = "Estimasi Putaran VSA Saat Live Streaming",
                                    primaryValue = "Live 24 Jam (1 Hari) = ± ${com.tajasb.infinity.Sv4VariationCalculator.formatCount(audioMetrics.vsCountFor24Hours.toLong())} VSA",
                                    secondaryNote = "• Live 6 Jam Non-Stop: ± ${com.tajasb.infinity.Sv4VariationCalculator.formatCount(audioMetrics.vsCountFor6Hours.toLong())} putaran VSA (VSA1 s/d VSA${audioMetrics.vsCountFor6Hours})\n" +
                                        "• Live 12 Jam Non-Stop: ± ${com.tajasb.infinity.Sv4VariationCalculator.formatCount(audioMetrics.vsCountFor12Hours.toLong())} putaran VSA (VSA1 s/d VSA${audioMetrics.vsCountFor12Hours})\n" +
                                        "• Live 24 Jam (1 Hari Penuh): ± ${com.tajasb.infinity.Sv4VariationCalculator.formatCount(audioMetrics.vsCountFor24Hours.toLong())} putaran VSA\n" +
                                        "• Live 7 Hari (1 Minggu): ± ${com.tajasb.infinity.Sv4VariationCalculator.formatCount(audioMetrics.vsCountFor7Days.toLong())} putaran VSA",
                                    valueColor = NeonCyan
                                )

                                // 5. Rincian Trek Sumber LA1..LAn & Penghematan Memori
                                val perAudioBreakdown = activeLAAudios.mapIndexed { idx, item ->
                                    val dMs = com.tajasb.infinity.Sv4VariationCalculator.resolveAudioDurationMs(item)
                                    "LA${idx + 1}: ${item.name.take(22)} (${com.tajasb.infinity.Sv4VariationCalculator.formatDurationShort(dMs)})"
                                }.joinToString("\n• ", prefix = "• ")

                                Sv4DetailListItemRow(
                                    numberBadge = "5",
                                    title = "Daftar Audio Sumber (LA1..LA$activeLACount) & Memori HP",
                                    primaryValue = "Auto-Hapus Aktif (${ifsState.sv4AudioAutoDeletedCount} VSA Lama Dihapus)",
                                    secondaryNote = "$perAudioBreakdown\n" +
                                        "• Penyimpanan Aman: Hanya menahan 1 file VSA aktif + 1 file VSA cadangan di memori HP.",
                                    valueColor = EmeraldGreen
                                )
                            }
                        }
                    }

                    // KOTAK STATUS #1 AKTIF PLAY (VSA ON-AIR)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF1F103B))
                            .border(0.8.dp, NeonPurple.copy(alpha = 0.85f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎵 DAFTAR #1 (AKTIF PLAY): VSA${ifsState.sv4AudioCurrentVsNumber}",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFE879F9)
                            )
                            Text(
                                text = "ON-AIR • Mandiri Non-Stop",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }
                        Text(
                            text = ifsState.sv4AudioActiveFormula.ifBlank { "Menyiapkan susunan LA1..LAn..." },
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // KOTAK STATUS #2 SIAP PLAY / SEDANG DIGABUNG DI BACKGROUND
                    val isNextAudioReady = ifsState.sv4AudioNextReady
                    val isAudioStitching = ifsState.sv4AudioIsStitchingNext
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isNextAudioReady) Color(0xFF083344) else Color(0xFF09101D))
                            .border(
                                0.8.dp,
                                if (isNextAudioReady) NeonCyan else Color(0xFF334155),
                                RoundedCornerShape(5.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    isNextAudioReady -> "✅ DAFTAR #2 (SIAP PLAY): VSA${ifsState.sv4AudioNextVsNumber}"
                                    isAudioStitching -> "⚙️ GABUNG BACKGROUND ➡️ DAFTAR #2: VSA${ifsState.sv4AudioNextVsNumber} (${ifsState.sv4AudioNextStitchPercent}%)"
                                    else -> "⏳ ANTREAN BERIKUTNYA: VSA${ifsState.sv4AudioNextVsNumber}"
                                },
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isNextAudioReady) NeonCyan else Color(0xFFFCD34D)
                            )
                            Text(
                                text = "🗑️ Dihapus: ${ifsState.sv4AudioAutoDeletedCount} VSA lama",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Text(
                            text = ifsState.sv4AudioNextFormula.ifBlank { "Menunggu giliran penggabungan audio..." },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.LightGray
                        )
                    }

                    // BARIS RENTANG LOOP DINAMIS AUDIO LA (DEFAULT 1..2X)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF09101D))
                            .border(0.6.dp, Color(0xFF1E293B), RoundedCornerShape(5.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎲 Loop Dinamis LA:",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDDD6FE)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SmallStepButton(
                                text = "−",
                                onClick = {
                                    if (sv4AudioMinLoops > 1) {
                                        ifsManager.setSv4AudioLoopRange(sv4AudioMinLoops - 1, sv4AudioMaxLoops)
                                    }
                                }
                            )
                            Text(
                                text = "${sv4AudioMinLoops}x",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            SmallStepButton(
                                text = "+",
                                onClick = {
                                    val nextMin = (sv4AudioMinLoops + 1).coerceAtMost(8)
                                    val nextMax = sv4AudioMaxLoops.coerceAtLeast(nextMin)
                                    ifsManager.setSv4AudioLoopRange(nextMin, nextMax)
                                }
                            )

                            Text(
                                text = "s/d",
                                fontSize = 8.5.sp,
                                color = Color.Gray
                            )

                            SmallStepButton(
                                text = "−",
                                onClick = {
                                    if (sv4AudioMaxLoops > 1) {
                                        val nextMax = sv4AudioMaxLoops - 1
                                        val nextMin = sv4AudioMinLoops.coerceAtMost(nextMax)
                                        ifsManager.setSv4AudioLoopRange(nextMin, nextMax)
                                    }
                                }
                            )
                            Text(
                                text = "${sv4AudioMaxLoops}x",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFE879F9)
                            )
                            SmallStepButton(
                                text = "+",
                                onClick = {
                                    if (sv4AudioMaxLoops < 10) {
                                        ifsManager.setSv4AudioLoopRange(sv4AudioMinLoops, sv4AudioMaxLoops + 1)
                                    }
                                }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            listOf(1 to 1, 1 to 2, 1 to 3, 2 to 3).forEach { (pMin, pMax) ->
                                val isPresetSelected = sv4AudioMinLoops == pMin && sv4AudioMaxLoops == pMax
                                val presetLabel = if (pMin == pMax) "${pMin}x" else "$pMin..${pMax}x"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isPresetSelected) NeonPurple.copy(alpha = 0.28f) else DarkSurface)
                                        .border(0.5.dp, if (isPresetSelected) NeonPurple else Color(0xFF334155), RoundedCornerShape(3.dp))
                                        .clickable { ifsManager.setSv4AudioLoopRange(pMin, pMax) }
                                        .padding(horizontal = 4.5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = presetLabel,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPresetSelected) Color(0xFFE879F9) else Color.LightGray
                                    )
                                }
                            }
                        }
                    }

                    // BARIS BAHAN AUDIO SUMBER LA1..LAn (DISEMBUNYIKAN DEFAULT AGAR DIRACIK DI BELAKANG LAYAR)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF160C29))
                            .border(0.6.dp, NeonPurple.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                            .clickable { isSourceAudioLListExpanded = !isSourceAudioLListExpanded }
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = if (activeLACount > 0) {
                                    "🔒 $activeLACount Bahan Audio (LA1..LA$activeLACount) Diracik di Belakang Layar"
                                } else {
                                    "🎵 Sumber Audio Variasi (Belum ada LA dipilih)"
                                },
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE879F9)
                            )
                            Text(
                                text = if (isSourceAudioLListExpanded) {
                                    "Menampilkan daftar bahan LA1..LA${activeLACount.coerceAtLeast(1)} • Ketuk untuk tutup ▴"
                                } else {
                                    "Audio upload disembunyikan agar rapi & otomatis dirakit menjadi VSA • Kelola Bahan ▾"
                                },
                                fontSize = 7.3.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .testTag("btn_sv4_audio_pick_la_group")
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonPurple.copy(alpha = 0.25f))
                                .border(0.7.dp, NeonPurple, RoundedCornerShape(4.dp))
                                .clickable { isPickingAudioLAGroup = true }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "☑️ Pilih LA ($activeLACount)",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE879F9)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isSourceAudioLListExpanded || activeLACount == 0) {
                        if (activeLACount == 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(DarkBackground)
                                    .border(0.6.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                                    .clickable { isPickingAudioLAGroup = true }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Belum ada trek audio LA. Upload audio di Tab Musik lalu ketuk '☑️ Pilih LA'.",
                                    fontSize = 8.5.sp,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.5.dp)
                            ) {
                                activeLAAudios.forEachIndexed { idx, item ->
                                    val laNum = idx + 1
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(DarkBackground)
                                            .border(0.6.dp, Color(0xFF334155), RoundedCornerShape(5.dp))
                                            .padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(NeonPurple.copy(alpha = 0.25f))
                                                    .border(0.5.dp, NeonPurple, RoundedCornerShape(3.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                            ) {
                                                Text(
                                                    text = "LA$laNum",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFFE879F9)
                                                )
                                            }

                                            Text(
                                                text = item.name,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = if (sv4AudioMinLoops == sv4AudioMaxLoops) "Loop ${sv4AudioMinLoops}x" else "Loop ${sv4AudioMinLoops}..${sv4AudioMaxLoops}x",
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = NeonCyan
                                            )
                                            Text(
                                                text = "✕",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFF5252),
                                                modifier = Modifier
                                                    .clickable {
                                                        ifsManager.removeSv4AudioL(item.id)
                                                        viewModel.showUserMessage("🗑️ Audio dihapus dari daftar Variasi Susunan (LA)")
                                                    }
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isPickingAudioLAGroup) {
        Sv4AudioLAMultiSelectDialog(
            audioPlaylist = sourceAudios,
            selectedLAIds = validSelectedLAIds.ifEmpty { sourceAudios.map { it.id } },
            minLoops = sv4AudioMinLoops,
            maxLoops = sv4AudioMaxLoops,
            onDismiss = { isPickingAudioLAGroup = false },
            onSaveSelection = { newIds ->
                ifsManager.setSv4AudioLIds(newIds)
                isPickingAudioLAGroup = false
                viewModel.showUserMessage("🧬 Daftar Audio LA diperbarui: ${newIds.size} trek (LA1..LA${newIds.size})")
            }
        )
    }
}

@Composable
fun Sv4AudioLAMultiSelectDialog(
    audioPlaylist: List<AudioItem>,
    selectedLAIds: List<String>,
    minLoops: Int,
    maxLoops: Int,
    onDismiss: () -> Unit,
    onSaveSelection: (List<String>) -> Unit
) {
    val cleanList = remember(audioPlaylist) {
        audioPlaylist.filterNot { it.id.startsWith("vs_audio_rolling_") }
    }
    var currentSelection by remember(selectedLAIds) {
        mutableStateOf(selectedLAIds.filterNot { it.startsWith("vs_audio_rolling_") })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "🧬 Pilih Trek Sumber Variasi Audio (LA1, LA2, LA3...)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE879F9)
                )
                Text(
                    text = "Trek LA yang dipilih akan diacak urutannya sekaligus diacak jumlah loop (${minLoops}..${maxLoops}x) untuk digabung otomatis menjadi VSA1, VSA2, VSA3 dst.",
                    fontSize = 9.5.sp,
                    color = Color.LightGray
                )
            }
        },
        text = {
            if (cleanList.isEmpty()) {
                Text(
                    text = "Belum ada trek audio di daftar putar. Ketuk tombol 'Tambah Audio' terlebih dahulu.",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terpilih: ${currentSelection.size} Audio (LA1..LA${currentSelection.size.coerceAtLeast(1)})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE879F9)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonCyan.copy(alpha = 0.18f))
                                    .border(0.6.dp, NeonCyan, RoundedCornerShape(4.dp))
                                    .clickable { currentSelection = cleanList.map { it.id } }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("Pilih Semua", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF3F1D2B))
                                    .border(0.6.dp, Color(0xFFFF5252), RoundedCornerShape(4.dp))
                                    .clickable { currentSelection = emptyList() }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("Kosongkan", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(cleanList.size) { idx ->
                            val track = cleanList[idx]
                            val laOrderIdx = currentSelection.indexOf(track.id)
                            val isChecked = laOrderIdx >= 0
                            val dMs = com.tajasb.infinity.Sv4VariationCalculator.resolveAudioDurationMs(track)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isChecked) Color(0xFF241242) else DarkSurfaceVariant)
                                    .border(
                                        width = if (isChecked) 1.dp else 0.5.dp,
                                        color = if (isChecked) NeonPurple else Color(0xFF334155),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        currentSelection = if (isChecked) {
                                            currentSelection.filter { it != track.id }
                                        } else {
                                            currentSelection + track.id
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeonPurple,
                                        uncheckedColor = Color.Gray,
                                        checkmarkColor = Color.White
                                    ),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "#${idx + 1} ${track.name}",
                                        fontSize = 10.5.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.sizeBytes / 1024} KB • ${formatDuration(dMs)}",
                                        fontSize = 9.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (isChecked) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(NeonPurple)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "LA${laOrderIdx + 1}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveSelection(currentSelection) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
            ) {
                Text("Simpan Audio LA (${currentSelection.size})", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color.LightGray, fontSize = 11.sp)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    )
}
