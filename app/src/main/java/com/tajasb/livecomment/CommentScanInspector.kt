package com.tajasb.livecomment

import android.content.ContentValues
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class ScanInspectionStatus(val label: String, val badgeColorHex: String, val textColorHex: String) {
    ACCEPTED("DITERIMA", "#10B981", "#022C22"),
    REJECTED_SYSTEM("DITOLAK SISTEM", "#F59E0B", "#451A03"),
    REJECTED_DUPLICATE("DUPLIKAT", "#06B6D4", "#083344"),
    REJECTED_OUT_OF_BOUNDS("LUAR AREA", "#8B5CF6", "#2E1065")
}

data class ScanInspectionEntry(
    val id: Long,
    val timestamp: Long,
    val timeFormatted: String,
    val senderName: String,
    val content: String,
    val status: ScanInspectionStatus,
    val reasonDetail: String,
    val spillKeywordMatched: String? = null
)

/**
 * CommentScanInspector:
 * Real-time diagnostic inspector for screen-scanning accessibility service.
 * Tracks accepted comments, filtered system messages, duplicate drops,
 * and out-of-bounds screen nodes with a zero-leak 100-entry ring buffer.
 */
class CommentScanInspector private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "CommentScanInspector"
        const val MAX_ENTRIES = 100

        @Volatile
        private var instance: CommentScanInspector? = null

        fun getInstance(context: Context): CommentScanInspector {
            return instance ?: synchronized(this) {
                instance ?: CommentScanInspector(context.applicationContext).also { instance = it }
            }
        }
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val fileDateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.forLanguageTag("id-ID"))
    private val fileTimestampFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
    private val idGen = AtomicLong(1L)
    private val lock = Any()

    private val queue = ArrayDeque<ScanInspectionEntry>(MAX_ENTRIES + 1)
    private val _entriesFlow = MutableStateFlow<List<ScanInspectionEntry>>(emptyList())
    val entriesFlow: StateFlow<List<ScanInspectionEntry>> = _entriesFlow.asStateFlow()

    private val _isInspectorActive = MutableStateFlow(false)
    val isInspectorActive: StateFlow<Boolean> = _isInspectorActive.asStateFlow()

    private val _isMenuCoveringChat = MutableStateFlow(false)
    val isMenuCoveringChat: StateFlow<Boolean> = _isMenuCoveringChat.asStateFlow()

    private val _menuCoveringReason = MutableStateFlow("")
    val menuCoveringReason: StateFlow<String> = _menuCoveringReason.asStateFlow()

    fun setMenuCoveringChat(covering: Boolean, reason: String = "") {
        _isMenuCoveringChat.value = covering
        _menuCoveringReason.value = reason
    }

    fun recordViewerAnchor(rawText: String, bounds: Rect, screenW: Int, screenH: Int): ViewerAnchorData? {
        return ViewerAnchorManager.getInstance(appContext).saveViewerAnchor(rawText, bounds, screenW, screenH)
    }

    private val _totalScanned = MutableStateFlow(0)
    val totalScanned: StateFlow<Int> = _totalScanned.asStateFlow()

    fun setInspectorActive(active: Boolean) {
        _isInspectorActive.value = active
    }

    private val _countAccepted = MutableStateFlow(0)
    val countAccepted: StateFlow<Int> = _countAccepted.asStateFlow()

    private val _countRejectedSystem = MutableStateFlow(0)
    val countRejectedSystem: StateFlow<Int> = _countRejectedSystem.asStateFlow()

    private val _countRejectedDuplicate = MutableStateFlow(0)
    val countRejectedDuplicate: StateFlow<Int> = _countRejectedDuplicate.asStateFlow()

    private val _countRejectedOutOfBounds = MutableStateFlow(0)
    val countRejectedOutOfBounds: StateFlow<Int> = _countRejectedOutOfBounds.asStateFlow()

    // Rate-limiter cache for noisy identical out-of-bounds / noise entries
    private val recentNoiseThrottle = HashMap<String, Long>()
    private var lastOutOfBoundsLogMs = 0L

    private fun addEntry(
        senderName: String,
        content: String,
        status: ScanInspectionStatus,
        reasonDetail: String,
        spillKeywordMatched: String? = null
    ) {
        val now = System.currentTimeMillis()
        val formatted = timeFormat.format(Date(now))
        val entry = ScanInspectionEntry(
            id = idGen.getAndIncrement(),
            timestamp = now,
            timeFormatted = formatted,
            senderName = senderName.trim().ifBlank { "-" },
            content = content.trim(),
            status = status,
            reasonDetail = reasonDetail,
            spillKeywordMatched = spillKeywordMatched
        )

        synchronized(lock) {
            if (queue.size >= MAX_ENTRIES) {
                queue.pollFirst()
            }
            queue.addLast(entry)
            _entriesFlow.value = queue.toList().asReversed() // Newest first

            _totalScanned.value += 1
            when (status) {
                ScanInspectionStatus.ACCEPTED -> _countAccepted.value += 1
                ScanInspectionStatus.REJECTED_SYSTEM -> _countRejectedSystem.value += 1
                ScanInspectionStatus.REJECTED_DUPLICATE -> _countRejectedDuplicate.value += 1
                ScanInspectionStatus.REJECTED_OUT_OF_BOUNDS -> _countRejectedOutOfBounds.value += 1
            }
        }
    }

    fun logAccepted(sender: String, content: String, spillTrigger: String? = null) {
        val detail = if (spillTrigger != null) {
            "Valid • Memicu $spillTrigger"
        } else {
            "Valid • Siap ditampilkan / antre"
        }
        addEntry(sender, content, ScanInspectionStatus.ACCEPTED, detail, spillTrigger)
    }

    fun logRejectedSystem(rawText: String, reason: String) {
        val snippet = if (rawText.length > 60) rawText.take(57) + "..." else rawText
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val last = recentNoiseThrottle[snippet]
            if (last != null && (now - last) < 3000L) {
                return // Throttle duplicate system message logs
            }
            recentNoiseThrottle[snippet] = now
            if (recentNoiseThrottle.size > 100) recentNoiseThrottle.clear()
        }
        addEntry("Sistem", snippet, ScanInspectionStatus.REJECTED_SYSTEM, reason)
    }

    fun logRejectedDuplicate(sender: String, content: String, note: String = "Komentar sama dalam 45 dtk") {
        val snippet = if (content.length > 60) content.take(57) + "..." else content
        val now = System.currentTimeMillis()
        val key = "$sender::$snippet"
        synchronized(lock) {
            val last = recentNoiseThrottle[key]
            if (last != null && (now - last) < 4000L) {
                return
            }
            recentNoiseThrottle[key] = now
            if (recentNoiseThrottle.size > 100) recentNoiseThrottle.clear()
        }
        addEntry(sender, snippet, ScanInspectionStatus.REJECTED_DUPLICATE, note)
    }

    fun logRejectedOutOfBounds(rawText: String, nodeBounds: Rect, scanRect: Rect?, screenH: Int) {
        val clean = rawText.trim()
        if (clean.length < 2) return
        val now = System.currentTimeMillis()

        // Rate limit out-of-bounds to prevent filling the log with static UI elements
        synchronized(lock) {
            if (now - lastOutOfBoundsLogMs < 1200L) return
            val last = recentNoiseThrottle[clean]
            if (last != null && (now - last) < 8000L) return
            lastOutOfBoundsLogMs = now
            recentNoiseThrottle[clean] = now
            if (recentNoiseThrottle.size > 100) recentNoiseThrottle.clear()
        }

        val snippet = if (clean.length > 50) clean.take(47) + "..." else clean
        val yPct = if (screenH > 0) ((nodeBounds.centerY().toFloat() / screenH) * 100).toInt() else 0
        val topLimitPct = if (screenH > 0 && scanRect != null) ((scanRect.top.toFloat() / screenH) * 100).toInt() else 40
        val bottomLimitPct = if (screenH > 0 && scanRect != null) ((scanRect.bottom.toFloat() / screenH) * 100).toInt() else 88

        val reason = "Di luar kotak (Y=$yPct%, batas $topLimitPct%-$bottomLimitPct%)"
        addEntry("Layar", snippet, ScanInspectionStatus.REJECTED_OUT_OF_BOUNDS, reason)
    }

    fun clearLogs() {
        synchronized(lock) {
            queue.clear()
            _entriesFlow.value = emptyList()
            recentNoiseThrottle.clear()
            _totalScanned.value = 0
            _countAccepted.value = 0
            _countRejectedSystem.value = 0
            _countRejectedDuplicate.value = 0
            _countRejectedOutOfBounds.value = 0
        }
    }

    fun exportDiagnosticToDownloadFolder(): ExportScanResult {
        val snapshot: List<ScanInspectionEntry>
        val total: Int
        val acc: Int
        val sys: Int
        val dup: Int
        val out: Int

        synchronized(lock) {
            snapshot = queue.toList()
            total = _totalScanned.value
            acc = _countAccepted.value
            sys = _countRejectedSystem.value
            dup = _countRejectedDuplicate.value
            out = _countRejectedOutOfBounds.value
        }

        if (snapshot.isEmpty()) {
            return ExportScanResult(
                success = false,
                filePath = "",
                totalComments = 0,
                errorMessage = "Belum ada log pemindaian untuk diekspor."
            )
        }

        val now = System.currentTimeMillis()
        val dateText = fileDateFormat.format(Date(now))
        val fileTime = fileTimestampFormat.format(Date(now))
        val fileName = "diagnostik_scan_$fileTime.txt"

        val sb = StringBuilder()
        sb.append("====================================================\n")
        sb.append("DIAGNOSTIK LOG SCAN AKSESIBILITAS TAJASB\n")
        sb.append("Waktu Simpan  : $dateText\n")
        sb.append("Total Terpindai: $total Teks\n")
        sb.append("• Diterima     : $acc Komentar\n")
        sb.append("• Ditolak Sistem: $sys Pesan\n")
        sb.append("• Ditolak Dup  : $dup Duplikat\n")
        sb.append("• Di Luar Area : $out Elemen\n")
        sb.append("====================================================\n\n")

        for (item in snapshot) {
            val statusTag = "[${item.status.label}]"
            val spillInfo = if (item.spillKeywordMatched != null) " [TRIGGER: ${item.spillKeywordMatched}]" else ""
            sb.append("[${item.timeFormatted}] $statusTag$spillInfo ${item.senderName}: \"${item.content}\"\n")
            sb.append("  ↳ Alasan: ${item.reasonDetail}\n\n")
        }

        val textContent = sb.toString()

        // 1. Direct File write to Download/scan
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val scanDir = File(downloadDir, "scan")
            if (!scanDir.exists()) scanDir.mkdirs()
            if (scanDir.exists() && scanDir.canWrite()) {
                val targetFile = File(scanDir, fileName)
                FileOutputStream(targetFile).use { fos ->
                    OutputStreamWriter(fos, StandardCharsets.UTF_8).use { it.write(textContent) }
                }
                return ExportScanResult(
                    success = true,
                    filePath = targetFile.absolutePath,
                    totalComments = snapshot.size
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Direct File write error, fallback to MediaStore: ${t.message}")
        }

        // 2. MediaStore write
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/scan")
                }
                val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    appContext.contentResolver.openOutputStream(uri)?.use { os ->
                        OutputStreamWriter(os, StandardCharsets.UTF_8).use { it.write(textContent) }
                    }
                    return ExportScanResult(
                        success = true,
                        filePath = "Download/scan/$fileName",
                        totalComments = snapshot.size
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "MediaStore write failed: ${t.message}")
            return ExportScanResult(
                success = false,
                filePath = "",
                totalComments = snapshot.size,
                errorMessage = "Gagal menyimpan file: ${t.message}"
            )
        }

        return ExportScanResult(
            success = false,
            filePath = "",
            totalComments = snapshot.size,
            errorMessage = "Gagal mengakses penyimpanan Download/scan"
        )
    }
}
