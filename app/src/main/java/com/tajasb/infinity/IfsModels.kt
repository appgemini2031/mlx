package com.tajasb.infinity

import java.util.UUID

/**
 * Infinity System (IFS) Models:
 * Arsitektur produksi penuh untuk:
 * 1. System Video SV1: ABA (3 Video: ABB, BL, BBT) + VT1, VT2, VT3..n (1 Video Konten Tunggal tanpa ABA)
 * 2. System Video SV2: PM1, PM2, PM3..n (Setiap Paket Master memiliki ABA: ABB, BL, BBT)
 * 3. System Video SV3: 1 Video VL (Loop Acak X..Y) <-> Grup VB (VB1..n Acak Non-Repeat)
 * 4. System Video SV4 (Mode VS — Variasi Susunan):
 *    L1, L2, L3, L4..n dengan Loop Acak Dinamis (1..2x) -> Rolling Gabung VS1 (Play) -> VS2 (Gabung di Latar) -> Hapus Otomatis VS1 saat selesai!
 * 5. System Audio X (AX + SR): Pause SR di detik terakhir -> Play AX sampai selesai -> Resume SR dari detik terakhir
 * 6. System Scan Komentar (SC) dengan antrean FIFO
 */

enum class IfsVideoMode(val title: String, val subtitle: String, val badge: String) {
    SV1(
        "System Video SV1",
        "ABA (ABB+BL+BBT) + VT1..n (1 Video)",
        "SV1"
    ),
    SV2(
        "System Video SV2",
        "PM1, PM2, PM3..n (Masing-masing ABA: ABB+BL+BBT)",
        "SV2"
    ),
    SV3(
        "System Video SV3",
        "1 Video VL (Loop Acak X..Y) ↔ Grup VB (VB1..n Acak Non-Repeat)",
        "SV3"
    ),
    SV4(
        "System Video SV4 (Mode VS)",
        "Variasi Susunan (L1..Ln Loop 1-2x) • Rolling Gabung VS1→VS2 & Auto-Hapus Memori",
        "SV4"
    )
}

enum class IfsAudioMode(val title: String, val subtitle: String, val badge: String) {
    SEQUENTIAL(
        "Mode Audio MP3 Urut (1→2→3)",
        "Putar daftar lagu MP3 secara berurutan & bebas pilih lagu kapan saja",
        "🔁 Urut"
    ),
    SR_AX(
        "Mode Audio Acak / SR & AX",
        "Musik Latar Acak Non-Repeat (SR) + Trigger Audio Paket (AX)",
        "🔀 Acak/SR"
    ),
    SINGLE_LOOP(
        "Mode Audio Loop 1 Lagu",
        "Ulangi 1 lagu MP3 yang sedang dipilih secara terus-menerus",
        "🔂 Loop 1x"
    ),
    SV4_VS(
        "System Audio SV4 (Mode VS Audio)",
        "Variasi Susunan Audio (LA1..LAn Loop 1-2x) • Gabung VSA1→VSA2 & Auto-Hapus Memori",
        "🧬 SV4 Audio"
    )
}

enum class IfsSubPhase(val label: String, val shortLabel: String, val badge: String, val colorHex: String) {
    ABB("Awal Sebelum Looping (A: ABB)", "A (ABB)", "ABB", "#38BDF8"),
    BL("Looping Standby (B: BL)", "B (BL)", "BL", "#F59E0B"),
    BBT("Transisi Keluar (A: BBT)", "A (BBT)", "BBT", "#E879F9"),
    CONTENT("Video Konten Tunggal (VT)", "VT Konten", "VT", "#34D399"),
    VL("Video Looping Acak (VL)", "VL (Loop Acak)", "VL", "#10B981"),
    VB("Video Bridge Acak (VB)", "VB (Bridge Acak)", "VB", "#38BDF8"),
    VS("Variasi Susunan Gabungan (VS)", "VS Rolling", "VS", "#10B981")
}

/**
 * Representasi paket ABA (vtNumber = 0 khusus SV1) maupun VT1..n / PM1..n (vtNumber = 1, 2, 3..n).
 */
data class IfsVtPackage(
    val vtNumber: Int, // 0 = ABA (khusus SV1), 1 = VT1/PM1, 2 = VT2/PM2, 3 = VT3/PM3, dst.
    val title: String = if (vtNumber == 0) "ABA (ABB + BL + BBT)" else "VT$vtNumber",
    val abbVideoId: String? = null, // A: ABB (Awal sebelum loop)
    val blVideoId: String? = null,  // B: BL (Loop standby) atau Video Konten Tunggal pada SV1 VT1..n
    val bbtVideoId: String? = null, // A: BBT (Transisi keluar setelah loop)
    val triggerKeyword: String = "", // Kata kunci komentar penonton SC
    val linkedAudioId: String? = null, // ID AudioItem spesifik dari playlist Musik untuk AX terkait
    val linkedAxNumber: Int = vtNumber // 0 = AX-ABA, 1 = AX1/AX-PM1, 2 = AX2/AX-PM2, dst.
) {
    val isVtm: Boolean get() = vtNumber == 0
    val isMasterAba: Boolean get() = vtNumber == 0

    val shortCode: String get() = if (vtNumber == 0) "ABA" else "VT$vtNumber"

    fun getShortCode(mode: IfsVideoMode = IfsVideoMode.SV1): String {
        return if (vtNumber == 0) {
            "ABA"
        } else if (mode == IfsVideoMode.SV2) {
            "PM$vtNumber"
        } else {
            "VT$vtNumber"
        }
    }

    val axCode: String get() = if (vtNumber == 0) "" else "AX$vtNumber"

    fun getAxCode(mode: IfsVideoMode = IfsVideoMode.SV1): String {
        return if (vtNumber == 0) {
            "" // ABA di SV1 tidak memiliki audio AX (hanya musik latar SR)
        } else {
            "AX$vtNumber"
        }
    }

    fun requiresThreeVideos(mode: IfsVideoMode): Boolean {
        return mode == IfsVideoMode.SV2 || vtNumber == 0
    }

    fun getDisplayName(mode: IfsVideoMode): String {
        return if (vtNumber == 0) {
            "ABA (ABB + BL + BBT)"
        } else if (mode == IfsVideoMode.SV1) {
            "VT$vtNumber (1 Video Konten)"
        } else {
            "PM$vtNumber (ABA: ABB + BL + BBT)"
        }
    }
}

data class IfsQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val targetVtNumber: Int, // 0 = ABA (SV1), 1 = VT1/PM1, 2 = VT2/PM2, dst.
    val senderName: String = "",
    val commentText: String = "",
    val queuedAtMs: Long = System.currentTimeMillis()
)

data class IfsState(
    val isEnabled: Boolean = false,
    val isScEnabled: Boolean = false,
    val videoMode: IfsVideoMode = IfsVideoMode.SV1,
    val activeVtNumber: Int = 0, // 0 = ABA (pada SV1), 1 = VT1/PM1, 2 = VT2/PM2, dst.
    val activeSubPhase: IfsSubPhase = IfsSubPhase.ABB,
    val pendingTargetVtNumber: Int? = null,
    val queue: List<IfsQueueItem> = emptyList(),
    val isSrActive: Boolean = true,
    val isSrPlaying: Boolean = false,
    val currentAudioTrackName: String? = null,
    val pausedSrTrackId: String? = null,
    val pausedSrTrackName: String? = null,
    val pausedSrPositionMs: Long = 0L,
    val isPlayingTriggeredAx: Boolean = false,
    val activeTriggerAxNumber: Int? = null,
    val activeTriggerAxLabel: String? = null,
    val currentVideoId: String? = null,
    val currentVideoName: String? = null,
    val statusMessage: String = "Infinity System Standby",
    val phaseStepVersion: Long = 0L,
    val sv3CurrentVlLoop: Int = 1,
    val sv3TargetVlLoops: Int = 2,
    val sv3ActiveVbNumber: Int = 1,
    val sv3VbRemainingInCycle: Int = 0,
    val sv3VbTotalCount: Int = 0,
    // State Khusus Mode SV4 Video (Variasi Susunan / VS Rolling JIT)
    val sv4VideoRunning: Boolean = false,
    val sv4CurrentVsNumber: Int = 1,
    val sv4ActiveFormula: String = "",
    val sv4NextVsNumber: Int = 2,
    val sv4NextFormula: String = "",
    val sv4IsStitchingNext: Boolean = false,
    val sv4NextStitchPercent: Int = 0,
    val sv4NextReady: Boolean = false,
    val sv4AutoDeletedCount: Int = 0,
    val sv4TotalLCount: Int = 0,
    val sv4MinLoops: Int = 1,
    val sv4MaxLoops: Int = 2,
    // Guard 2 Menit SV4: Bebas Kembar Koreografi, Suara & Anti-Detek dalam rentang waktu 120 detik
    val sv4TwoMinuteGuardActive: Boolean = true,
    val sv4GuardChoreographyCount2m: Int = 0,
    val sv4GuardAudioCount2m: Int = 0,
    val sv4GuardCurrentZoom: Float = 1.08f,
    val sv4GuardCurrentDnaBadge: String = "DNA-A",
    val sv4GuardStatusSummary: String = "🛡️ 2-Min Anti-Kembar Aktif: Koreografi, Suara & Anti-Detek Unik",
    // State Khusus Mode SV4 Audio (Variasi Susunan Audio / VSA Rolling JIT)
    val audioMode: IfsAudioMode = IfsAudioMode.SR_AX,
    val sv4AudioRunning: Boolean = false,
    val sv4AudioCurrentVsNumber: Int = 1,
    val sv4AudioActiveFormula: String = "",
    val sv4AudioNextVsNumber: Int = 2,
    val sv4AudioNextFormula: String = "",
    val sv4AudioIsStitchingNext: Boolean = false,
    val sv4AudioNextStitchPercent: Int = 0,
    val sv4AudioNextReady: Boolean = false,
    val sv4AudioAutoDeletedCount: Int = 0,
    val sv4AudioTotalLCount: Int = 0,
    val sv4AudioMinLoops: Int = 1,
    val sv4AudioMaxLoops: Int = 2,
    val sv4AudioStatusMessage: String = "SV4 Audio Standby"
) {
    val sv4CoreVariationsCount: Long
        get() = Sv4VariationCalculator.calculateCoreVariations(sv4TotalLCount, sv4MinLoops, sv4MaxLoops)

    val sv4TotalVariationsCount: Long
        get() = Sv4VariationCalculator.calculateTotalVariations(sv4TotalLCount, sv4MinLoops, sv4MaxLoops)

    val sv4TotalVariationsFormatted: String
        get() = Sv4VariationCalculator.formatCount(sv4TotalVariationsCount)

    val sv4CoreVariationsFormatted: String
        get() = Sv4VariationCalculator.formatCount(sv4CoreVariationsCount)

    val sv4AudioCoreVariationsCount: Long
        get() = Sv4VariationCalculator.calculateCoreVariations(sv4AudioTotalLCount, sv4AudioMinLoops, sv4AudioMaxLoops)

    val sv4AudioTotalVariationsCount: Long
        get() = Sv4VariationCalculator.calculateTotalVariations(sv4AudioTotalLCount, sv4AudioMinLoops, sv4AudioMaxLoops)

    val sv4AudioTotalVariationsFormatted: String
        get() = Sv4VariationCalculator.formatCount(sv4AudioTotalVariationsCount)

    val sv4AudioCoreVariationsFormatted: String
        get() = Sv4VariationCalculator.formatCount(sv4AudioCoreVariationsCount)

    fun getActivePackageCode(mode: IfsVideoMode = videoMode): String =
        when (mode) {
            IfsVideoMode.SV4 -> "VS$sv4CurrentVsNumber"
            IfsVideoMode.SV3 -> {
                if (activeSubPhase == IfsSubPhase.VB) {
                    "VB$sv3ActiveVbNumber"
                } else {
                    "VL (${sv3CurrentVlLoop}/${sv3TargetVlLoops}x)"
                }
            }
            IfsVideoMode.SV2 -> if (activeVtNumber == 0) "ABA" else "PM$activeVtNumber"
            IfsVideoMode.SV1 -> if (activeVtNumber == 0) "ABA" else "VT$activeVtNumber"
        }

    val activePackageCode: String
        get() = getActivePackageCode(videoMode)
}

data class Sv4VariationMetrics(
    val lCount: Int,
    val minLoops: Int,
    val maxLoops: Int,
    val loopChoices: Int,
    val avgLoopFactor: Double,
    val coreVariations: Long,
    val extendedVariations: Long,
    val totalVariations: Long,
    val formattedCoreVariations: String,
    val formattedExtendedVariations: String,
    val formattedTotalVariations: String,
    val totalBaseClipDurationMs: Long,
    val avgSingleClipDurationMs: Long,
    val isExactVideoDuration: Boolean,
    val avgVsDurationMs: Long,
    val minVsDurationMs: Long,
    val maxVsDurationMs: Long,
    val formattedBaseClipDuration: String,
    val formattedAvgVsDuration: String,
    val formattedVsDurationRange: String,
    val totalUniquePlaybackMs: Long,
    val totalUniqueDays: Long,
    val totalUniqueHoursRemainder: Long,
    val totalUniqueMinutesRemainder: Long,
    val totalUniqueHoursTotal: Double,
    val formattedTotalUniqueDaysHours: String,
    val formattedTotalUniqueHoursSummary: String,
    val vsCountFor6Hours: Int,
    val vsCountFor12Hours: Int,
    val vsCountFor24Hours: Int,
    val vsCountFor7Days: Int
)

object Sv4VariationCalculator {
    private val durationCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun resolveVideoDurationMs(item: com.tajasb.model.VideoItem): Long {
        if (item.durationMs > 500L) return item.durationMs
        val path = item.path
        if (path.isBlank()) return 15_000L
        durationCache[path]?.let { if (it > 500L) return it }
        return try {
            val file = java.io.File(path)
            if (file.exists()) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val dur = durStr?.toLongOrNull() ?: 0L
                    if (dur > 500L) {
                        durationCache[path] = dur
                        dur
                    } else 15_000L
                } finally {
                    try { retriever.release() } catch (_: Throwable) {}
                }
            } else 15_000L
        } catch (_: Throwable) {
            15_000L
        }
    }

    fun resolveAudioDurationMs(item: com.tajasb.model.AudioItem): Long {
        if (item.durationMs > 500L) return item.durationMs
        val path = item.path
        if (path.isBlank()) return 30_000L
        durationCache[path]?.let { if (it > 500L) return it }
        return try {
            val file = java.io.File(path)
            if (file.exists()) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val dur = durStr?.toLongOrNull() ?: 0L
                    if (dur > 500L) {
                        durationCache[path] = dur
                        dur
                    } else 30_000L
                } finally {
                    try { retriever.release() } catch (_: Throwable) {}
                }
            } else 30_000L
        } catch (_: Throwable) {
            30_000L
        }
    }

    fun calculateAudioMetrics(
        lCount: Int,
        minLoops: Int,
        maxLoops: Int,
        activeAudios: List<com.tajasb.model.AudioItem> = emptyList()
    ): Sv4VariationMetrics {
        val syntheticVideos = activeAudios.map { a ->
            val resolvedDur = resolveAudioDurationMs(a)
            com.tajasb.model.VideoItem(
                id = a.id,
                name = a.name,
                path = a.path,
                uri = a.uri,
                durationMs = resolvedDur,
                sizeBytes = a.sizeBytes
            )
        }
        return calculateMetrics(
            lCount = lCount,
            minLoops = minLoops,
            maxLoops = maxLoops,
            activeVideos = syntheticVideos
        )
    }

    /**
     * Menghitung jumlah susunan inti (N! * K^N) dari N video (L1..LN) dan rentang loop (minLoops..maxLoops).
     */
    fun calculateCoreVariations(lCount: Int, minLoops: Int, maxLoops: Int): Long {
        if (lCount <= 0) return 0L
        val safeN = lCount.coerceIn(1, 12)
        val loopChoices = (maxLoops - minLoops + 1).coerceAtLeast(1).toLong()
        if (safeN == 1) return loopChoices
        var fact = 1L
        for (i in 2..safeN) {
            fact = (fact * i).coerceAtMost(999_999_999_999L)
        }
        var loopComb = 1L
        repeat(safeN) {
            loopComb = (loopComb * loopChoices).coerceAtMost(999_999_999_999L)
        }
        return (fact * loopComb).coerceAtMost(999_999_999_999L)
    }

    /**
     * Menghitung total kemungkinan variasi susunan VS unik yang dihasilkan dari N video yang diupload:
     * Mencakup permutasi urutan penuh (L1..LN) dengan loop dinamis tiap L (minLoops..maxLoops)
     * beserta variasi kombinasi segmen penutup dinamis Rolling JIT:
     * Total = N! * K^N * (1 + (N - 1) * K)
     */
    fun calculateTotalVariations(lCount: Int, minLoops: Int, maxLoops: Int): Long {
        if (lCount <= 0) return 0L
        val safeN = lCount.coerceIn(1, 12)
        val safeMin = minLoops.coerceAtLeast(1)
        val safeMax = maxLoops.coerceAtLeast(safeMin)
        val loopChoices = (safeMax - safeMin + 1).coerceAtLeast(1).toLong()
        val core = calculateCoreVariations(safeN, safeMin, safeMax)
        if (safeN == 1 || (safeMin == 1 && safeMax == 1)) return core
        val tailFactor = 1L + ((safeN - 1).toLong() * loopChoices)
        return (core * tailFactor).coerceAtMost(999_999_999_999L)
    }

    fun calculateMetrics(
        lCount: Int,
        minLoops: Int,
        maxLoops: Int,
        activeVideos: List<com.tajasb.model.VideoItem> = emptyList()
    ): Sv4VariationMetrics {
        val safeMin = minLoops.coerceAtLeast(1)
        val safeMax = maxLoops.coerceAtLeast(safeMin)
        val loopChoices = (safeMax - safeMin + 1).coerceAtLeast(1)
        val avgLoopFactor = (safeMin + safeMax) / 2.0

        if (lCount <= 0) {
            return Sv4VariationMetrics(
                lCount = 0,
                minLoops = safeMin,
                maxLoops = safeMax,
                loopChoices = loopChoices,
                avgLoopFactor = avgLoopFactor,
                coreVariations = 0L,
                extendedVariations = 0L,
                totalVariations = 0L,
                formattedCoreVariations = "0",
                formattedExtendedVariations = "0",
                formattedTotalVariations = "0",
                totalBaseClipDurationMs = 0L,
                avgSingleClipDurationMs = 0L,
                isExactVideoDuration = false,
                avgVsDurationMs = 0L,
                minVsDurationMs = 0L,
                maxVsDurationMs = 0L,
                formattedBaseClipDuration = "0 Detik",
                formattedAvgVsDuration = "0 Detik",
                formattedVsDurationRange = "0 Detik",
                totalUniquePlaybackMs = 0L,
                totalUniqueDays = 0L,
                totalUniqueHoursRemainder = 0L,
                totalUniqueMinutesRemainder = 0L,
                totalUniqueHoursTotal = 0.0,
                formattedTotalUniqueDaysHours = "0 Hari 0 Jam 0 Menit",
                formattedTotalUniqueHoursSummary = "0 Jam Non-Stop",
                vsCountFor6Hours = 0,
                vsCountFor12Hours = 0,
                vsCountFor24Hours = 0,
                vsCountFor7Days = 0
            )
        }

        val core = calculateCoreVariations(lCount, safeMin, safeMax)
        val total = calculateTotalVariations(lCount, safeMin, safeMax)
        val extended = (total - core).coerceAtLeast(0L)

        val resolvedDurations = if (activeVideos.isNotEmpty()) {
            activeVideos.map { resolveVideoDurationMs(it) }
        } else {
            List(lCount) { 15_000L }
        }
        val isExact = activeVideos.isNotEmpty() && activeVideos.any { it.durationMs > 500L }
        val totalBaseMs = resolvedDurations.sum().coerceAtLeast(lCount * 1000L)
        val avgClipMs = (totalBaseMs / lCount.coerceAtLeast(1)).coerceAtLeast(1000L)

        val extraTailProb = if (lCount >= 2 && safeMax > 1) {
            val tailOptions = (lCount - 1) * loopChoices
            tailOptions.toDouble() / (1 + tailOptions).toDouble()
        } else 0.0

        val avgVsMs = (totalBaseMs * avgLoopFactor + avgClipMs * avgLoopFactor * extraTailProb).toLong().coerceAtLeast(1000L)
        val minVsMs = (totalBaseMs * safeMin).coerceAtLeast(1000L)
        val maxVsMs = if (lCount >= 2 && safeMax > 1) {
            ((totalBaseMs + avgClipMs) * safeMax).coerceAtLeast(minVsMs)
        } else {
            (totalBaseMs * safeMax).coerceAtLeast(minVsMs)
        }

        val totalUniqueMsDouble = total.toDouble() * avgVsMs.toDouble()
        val totalUniqueMs = totalUniqueMsDouble.toLong().coerceAtLeast(0L)
        val totalUniqueSec = totalUniqueMsDouble / 1000.0
        val totalDays = (totalUniqueSec / 86400.0).toLong()
        val remHours = ((totalUniqueSec % 86400.0) / 3600.0).toLong()
        val remMinutes = ((totalUniqueSec % 3600.0) / 60.0).toLong()
        val totalHours = totalUniqueSec / 3600.0

        val formattedDaysHours = when {
            totalDays > 0L -> "${formatCount(totalDays)} Hari ${remHours} Jam ${remMinutes} Menit"
            remHours > 0L -> "0 Hari ${remHours} Jam ${remMinutes} Menit"
            else -> "0 Hari 0 Jam ${remMinutes.coerceAtLeast(1L)} Menit"
        }

        val formattedHoursSummary = if (totalHours >= 100.0) {
            "${formatCount(totalHours.toLong())} Jam Non-Stop"
        } else {
            String.format(java.util.Locale("id", "ID"), "%.1f Jam Non-Stop", totalHours)
        }

        val avgVsSec = (avgVsMs / 1000.0).coerceAtLeast(1.0)
        val vs6h = kotlin.math.ceil((6.0 * 3600.0) / avgVsSec).toInt().coerceAtLeast(1)
        val vs12h = kotlin.math.ceil((12.0 * 3600.0) / avgVsSec).toInt().coerceAtLeast(1)
        val vs24h = kotlin.math.ceil((24.0 * 3600.0) / avgVsSec).toInt().coerceAtLeast(1)
        val vs7d = kotlin.math.ceil((7.0 * 24.0 * 3600.0) / avgVsSec).toInt().coerceAtLeast(1)

        return Sv4VariationMetrics(
            lCount = lCount,
            minLoops = safeMin,
            maxLoops = safeMax,
            loopChoices = loopChoices,
            avgLoopFactor = avgLoopFactor,
            coreVariations = core,
            extendedVariations = extended,
            totalVariations = total,
            formattedCoreVariations = formatCount(core),
            formattedExtendedVariations = formatCount(extended),
            formattedTotalVariations = formatCount(total),
            totalBaseClipDurationMs = totalBaseMs,
            avgSingleClipDurationMs = avgClipMs,
            isExactVideoDuration = isExact,
            avgVsDurationMs = avgVsMs,
            minVsDurationMs = minVsMs,
            maxVsDurationMs = maxVsMs,
            formattedBaseClipDuration = formatDurationMinSec(totalBaseMs),
            formattedAvgVsDuration = formatDurationMinSec(avgVsMs),
            formattedVsDurationRange = "${formatDurationShort(minVsMs)} – ${formatDurationShort(maxVsMs)}",
            totalUniquePlaybackMs = totalUniqueMs,
            totalUniqueDays = totalDays,
            totalUniqueHoursRemainder = remHours,
            totalUniqueMinutesRemainder = remMinutes,
            totalUniqueHoursTotal = totalHours,
            formattedTotalUniqueDaysHours = formattedDaysHours,
            formattedTotalUniqueHoursSummary = formattedHoursSummary,
            vsCountFor6Hours = vs6h,
            vsCountFor12Hours = vs12h,
            vsCountFor24Hours = vs24h,
            vsCountFor7Days = vs7d
        )
    }

    fun formatDurationMinSec(durationMs: Long): String {
        if (durationMs <= 0L) return "0 Detik"
        val totalSec = (durationMs / 1000L).coerceAtLeast(1L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return when {
            hours > 0L -> "${hours} Jam ${mins} Menit ${secs} Detik"
            mins > 0L && secs > 0L -> "${mins} Menit ${secs} Detik"
            mins > 0L -> "${mins} Menit"
            else -> "${secs} Detik"
        }
    }

    fun formatDurationShort(durationMs: Long): String {
        if (durationMs <= 0L) return "0 dtk"
        val totalSec = (durationMs / 1000L).coerceAtLeast(1L)
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        return when {
            mins > 0L && secs > 0L -> "${mins}m ${secs}s"
            mins > 0L -> "${mins}m"
            else -> "${secs}s"
        }
    }

    fun formatCount(value: Long): String {
        if (value <= 0L) return "0"
        if (value >= 1_000_000_000L) {
            val m = value / 1_000_000_000.0
            return String.format(java.util.Locale("id", "ID"), "%.1f Miliar+", m)
        }
        if (value >= 10_000_000L) {
            val jt = value / 1_000_000.0
            return String.format(java.util.Locale("id", "ID"), "%.1f Juta", jt)
        }
        return java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID")).format(value)
    }
}


