package com.tajasb.livecomment

import android.content.ContentValues
import android.content.Context
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
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

/**
 * Data model for a single scanned live comment.
 */
data class ScannedCommentRecord(
    val timestamp: Long,
    val timeFormatted: String,
    val senderName: String,
    val content: String,
    val isGift: Boolean
)

/**
 * Result data class when exporting scanned comments to a file.
 */
data class ExportScanResult(
    val success: Boolean,
    val filePath: String,
    val totalComments: Int,
    val errorMessage: String? = null
)

/**
 * CommentScanRecorder:
 * Manages dedicated live stream comment scanning sessions.
 * Features:
 * - Independent ON/OFF recording switch.
 * - Real-time comment count state for UI badges.
 * - Smart LRU deduplication (prevents repeated recording of static on-screen comments).
 * - Safe export to public Download/scan folder as a clean .txt file without debug clutter.
 * - Zero-clipboard copy to eliminate system lag or memory pressure during long 30+ min live streams.
 */
class CommentScanRecorder private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "CommentScanRecorder"
        private const val MAX_LRU_HASH_CACHE = 800

        @Volatile
        private var INSTANCE: CommentScanRecorder? = null

        fun getInstance(context: Context): CommentScanRecorder {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CommentScanRecorder(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val fullDateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.forLanguageTag("id-ID"))
    private val fileTimestampFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _scannedCount = MutableStateFlow(0)
    val scannedCount: StateFlow<Int> = _scannedCount.asStateFlow()

    private var recordingStartTimeMs: Long = 0L

    private val lock = Any()
    private val recordedList = mutableListOf<ScannedCommentRecord>()

    // LRU Cache for deduplicating repeated comments during live scans
    private val recentHashCache = object : LinkedHashMap<String, Long>(MAX_LRU_HASH_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > MAX_LRU_HASH_CACHE
        }
    }

    fun toggleRecording(): Boolean {
        val next = !_isRecording.value
        setRecording(next)
        return next
    }

    fun setRecording(enable: Boolean) {
        if (_isRecording.value == enable) return
        _isRecording.value = enable
        if (enable) {
            if (recordingStartTimeMs == 0L) {
                recordingStartTimeMs = System.currentTimeMillis()
            }
            Log.i(TAG, "🔴 Comment scanning started")
        } else {
            Log.i(TAG, "⚪ Comment scanning stopped")
        }
    }

    fun recordComment(senderName: String, content: String, isGift: Boolean): Boolean {
        if (!_isRecording.value) return false
        val cleanSender = senderName.trim()
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) return false

        // Compute unique fingerprint
        val hashKey = "$cleanSender|||$cleanContent|||$isGift"
        val now = System.currentTimeMillis()

        synchronized(lock) {
            val lastSeen = recentHashCache[hashKey]
            // If identical comment appeared within the last 45 seconds, treat as duplicate on-screen node
            if (lastSeen != null && (now - lastSeen) < 45000L) {
                return false
            }
            recentHashCache[hashKey] = now

            val timeStr = timeFormat.format(Date(now))
            val record = ScannedCommentRecord(
                timestamp = now,
                timeFormatted = timeStr,
                senderName = if (cleanSender.isNotBlank()) cleanSender else "Penonton",
                content = cleanContent,
                isGift = isGift
            )
            recordedList.add(record)
            _scannedCount.value = recordedList.size
        }
        return true
    }

    fun clearRecords() {
        synchronized(lock) {
            recordedList.clear()
            recentHashCache.clear()
            recordingStartTimeMs = if (_isRecording.value) System.currentTimeMillis() else 0L
            _scannedCount.value = 0
        }
    }

    fun exportToDownloadScanFolder(): ExportScanResult {
        val snapshot: List<ScannedCommentRecord>
        val count: Int
        val startMs: Long
        synchronized(lock) {
            snapshot = recordedList.toList()
            count = snapshot.size
            startMs = if (recordingStartTimeMs > 0L) recordingStartTimeMs else System.currentTimeMillis()
        }

        if (snapshot.isEmpty()) {
            return ExportScanResult(
                success = false,
                filePath = "",
                totalComments = 0,
                errorMessage = "Belum ada komentar yang terekam. Aktifkan saklar scan terlebih dahulu."
            )
        }

        val now = System.currentTimeMillis()
        val durationSeconds = ((now - startMs) / 1000).coerceAtLeast(0)
        val durMins = durationSeconds / 60
        val durSecs = durationSeconds % 60
        val durationText = "${durMins} Menit ${durSecs} Detik"
        val dateText = fullDateFormat.format(Date(now))
        val fileTime = fileTimestampFormat.format(Date(now))
        val fileName = "scan_komentar_$fileTime.txt"

        val sb = StringBuilder()
        sb.append("====================================================\n")
        sb.append("LOG SCAN KOMENTAR LIVE STREAM\n")
        sb.append("Waktu Simpan  : $dateText\n")
        sb.append("Durasi Scan   : $durationText\n")
        sb.append("Total Komentar: $count Komentar\n")
        sb.append("====================================================\n\n")

        for (item in snapshot) {
            val prefix = if (item.isGift) "[GIFT] " else ""
            sb.append("[${item.timeFormatted}] $prefix${item.senderName}: ${item.content}\n")
        }

        val textContent = sb.toString()

        // 1. Try direct File write to Download/scan
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val scanDir = File(downloadDir, "scan")
            if (!scanDir.exists()) {
                scanDir.mkdirs()
            }
            if (scanDir.exists() && scanDir.canWrite()) {
                val targetFile = File(scanDir, fileName)
                FileOutputStream(targetFile).use { fos ->
                    OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                        writer.write(textContent)
                    }
                }
                return ExportScanResult(
                    success = true,
                    filePath = targetFile.absolutePath,
                    totalComments = count
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Direct File write failed, trying MediaStore: ${t.message}")
        }

        // 2. Fallback via MediaStore.Downloads (Android 10+ Scoped Storage)
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
                        OutputStreamWriter(os, StandardCharsets.UTF_8).use { writer ->
                            writer.write(textContent)
                        }
                    }
                    val displayPath = "Download/scan/$fileName"
                    return ExportScanResult(
                        success = true,
                        filePath = displayPath,
                        totalComments = count
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "MediaStore write failed: ${t.message}")
            return ExportScanResult(
                success = false,
                filePath = "",
                totalComments = count,
                errorMessage = "Gagal menyimpan: ${t.message}"
            )
        }

        return ExportScanResult(
            success = false,
            filePath = "",
            totalComments = count,
            errorMessage = "Gagal mengakses folder Download/scan"
        )
    }
}
