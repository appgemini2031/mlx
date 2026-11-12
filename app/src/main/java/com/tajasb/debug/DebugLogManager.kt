package com.tajasb.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class DebugLogItem(
    val id: Long,
    val timestamp: Long,
    val tag: String,
    val message: String,
    val isError: Boolean = false
) {
    fun formatLine(dateFormat: SimpleDateFormat): String {
        val timeStr = dateFormat.format(Date(timestamp))
        val prefix = if (isError) "❌" else "ℹ️"
        return "[$timeStr] $prefix [$tag] $message"
    }
}

class DebugLogManager private constructor(private val context: Context) {

    companion object {
        const val MAX_LOGS = 100
        const val ACTION_DEBUG_LOG = "com.tajasb.action.DEBUG_LOG"
        const val EXTRA_LOG_TAG = "extra_log_tag"
        const val EXTRA_LOG_MSG = "extra_log_msg"
        const val EXTRA_LOG_ERROR = "extra_log_error"
        const val EXTRA_LOG_TIME = "extra_log_time"

        private const val PREFS_NAME = "vcam_debug_prefs"
        private const val KEY_LOGGING_ENABLED = "key_debug_logging_enabled"

        @Volatile
        private var INSTANCE: DebugLogManager? = null

        fun getInstance(context: Context): DebugLogManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DebugLogManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * Safe utility to send a debug log entry across process boundaries
         * (e.g. from MainHook running inside target camera app to Tajasb).
         */
        fun sendIpcLog(ctx: Context?, tag: String, msg: String, isError: Boolean = false) {
            val context = ctx ?: return
            try {
                // 1. Android Logcat
                if (isError) {
                    Log.e("VCAM_DEBUG", "[$tag] $msg")
                } else {
                    Log.i("VCAM_DEBUG", "[$tag] $msg")
                }

                // 2. Direct in-process if same process
                try {
                    getInstance(context).log(tag, msg, isError)
                } catch (_: Throwable) {}

                // 3. Fast & Reliable IPC via ContentProvider call
                val providerUris = listOf(
                    android.net.Uri.parse("content://com.tajasb.pro.issber.provider"),
                    android.net.Uri.parse("content://com.tajasb.provider"),
                    android.net.Uri.parse("content://com.aistudio.tajasb.provider"),
                    android.net.Uri.parse("content://com.aistudio.cuancame.provider"),
                    android.net.Uri.parse("content://com.example.provider")
                )
                for (uri in providerUris) {
                    try {
                        val extras = android.os.Bundle().apply {
                            putString("tag", tag)
                            putString("msg", msg)
                            putBoolean("isError", isError)
                            putLong("time", System.currentTimeMillis())
                        }
                        val res = context.contentResolver.call(uri, "pushLog", null, extras)
                        if (res != null && res.getBoolean("success", false)) {
                            break
                        }
                    } catch (_: Throwable) {}
                }

                // 4. Broadcast to Tajasb Floating Service (without restricting package to target app)
                val intent = Intent(ACTION_DEBUG_LOG).apply {
                    putExtra(EXTRA_LOG_TAG, tag)
                    putExtra(EXTRA_LOG_MSG, msg)
                    putExtra(EXTRA_LOG_ERROR, isError)
                    putExtra(EXTRA_LOG_TIME, System.currentTimeMillis())
                }
                context.sendBroadcast(intent)
            } catch (_: Throwable) {}
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val idCounter = AtomicLong(1L)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _isLoggingEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOGGING_ENABLED, true))
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private val logQueue = ArrayDeque<DebugLogItem>(MAX_LOGS + 1)
    private val _logsFlow = MutableStateFlow<List<DebugLogItem>>(emptyList())
    val logsFlow: StateFlow<List<DebugLogItem>> = _logsFlow.asStateFlow()

    init {
        log("SYSTEM", "DebugLogManager siap (Kapasitas: $MAX_LOGS log)")
    }

    fun setLoggingEnabled(enabled: Boolean) {
        _isLoggingEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LOGGING_ENABLED, enabled).apply()
        log("SYSTEM", if (enabled) "Logging Debug DIAKTIFKAN 🟢" else "Logging Debug DINONAKTIFKAN ⏹️")
    }

    fun toggleLogging(): Boolean {
        val next = !_isLoggingEnabled.value
        setLoggingEnabled(next)
        return next
    }

    @Synchronized
    fun log(tag: String, message: String, isError: Boolean = false, timestamp: Long = System.currentTimeMillis()) {
        if (!_isLoggingEnabled.value && tag != "SYSTEM") return

        val entry = DebugLogItem(
            id = idCounter.getAndIncrement(),
            timestamp = timestamp,
            tag = tag,
            message = message,
            isError = isError
        )

        while (logQueue.size >= MAX_LOGS) {
            logQueue.pollFirst()
        }
        logQueue.addLast(entry)
        _logsFlow.value = logQueue.toList()
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message (${throwable.javaClass.simpleName}: ${throwable.message})" else message
        log(tag, msg, isError = true)
    }

    @Synchronized
    fun clearLogs() {
        logQueue.clear()
        _logsFlow.value = emptyList()
        log("SYSTEM", "Log dibersihkan oleh pengguna 🗑️")
    }

    fun getFormattedLogsText(): String {
        val currentLogs = _logsFlow.value
        if (currentLogs.isEmpty()) {
            return "=== TAJASB DEBUG LOGS (KOSONG) ==="
        }
        val sb = StringBuilder()
        sb.append("=== TAJASB DEBUG LOGS (Total: ${currentLogs.size}/$MAX_LOGS) ===\n")
        sb.append("Waktu Ekspor: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("Status Logging: ${if (_isLoggingEnabled.value) "AKTIF 🟢" else "NONAKTIF ⏹️"}\n")
        sb.append("----------------------------------------\n")
        for (item in currentLogs) {
            sb.append(item.formatLine(dateFormat)).append("\n")
        }
        sb.append("========================================")
        return sb.toString()
    }

    fun copyLogsToClipboard(context: Context): Boolean {
        return try {
            val text = getFormattedLogsText()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Tajasb Debug Logs", text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            Log.e("DebugLogManager", "Failed to copy logs: ${e.message}")
            false
        }
    }
}
