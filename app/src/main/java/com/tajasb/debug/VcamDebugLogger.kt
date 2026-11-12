package com.tajasb.debug

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel(val label: String, val colorHex: String) {
    VERBOSE("VERBOSE", "#64748B"),
    DEBUG("DEBUG", "#38BDF8"),
    INFO("INFO", "#10B981"),
    WARN("WARN", "#F59E0B"),
    ERROR("ERROR", "#EF4444")
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val timeFormatted: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val errorDetails: String? = null,
    val processId: Int = Process.myPid()
)

object VcamDebugLogger {

    const val MAX_LOGS = 100
    const val ACTION_DEBUG_LOG = "com.tajasb.action.DEBUG_LOG"
    const val EXTRA_LOG_LEVEL = "extra_log_level"
    const val EXTRA_LOG_TAG = "extra_log_tag"
    const val EXTRA_LOG_MSG = "extra_log_msg"
    const val EXTRA_LOG_TIME = "extra_log_time"
    const val EXTRA_LOG_ERR = "extra_log_err"
    const val EXTRA_LOG_PID = "extra_log_pid"

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val nextId = AtomicLong(1L)
    private val lock = Any()
    private val logQueue = ArrayDeque<LogEntry>(MAX_LOGS + 1)
    private val recentRemoteKeys = ArrayDeque<String>(128)
    private val ipcExecutor by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }

    private val KNOWN_PROVIDER_AUTHORITIES = listOf(
        "com.tajasb.provider",
        "com.tajasb.pro.issber.provider",
        "com.tajasb.pro.provider",
        "com.aistudio.tajasb.provider",
        "com.aistudio.cuancame.provider",
        "com.example.provider"
    )

    private val KNOWN_RECEIVER_PACKAGES = listOf(
        "com.tajasb.pro.tqnzya",
        "com.tajasb.pro.tdlclp",
        "com.tajasb.pro.issber",
        "com.tajasb.pro",
        "com.tajasb",
        "com.aistudio.tajasb",
        "com.aistudio.cuancame",
        "com.example"
    )

    @Volatile
    private var cachedProviderAuthority: String? = null

    private val _isLoggingEnabled = MutableStateFlow(true)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private var logReceiver: BroadcastReceiver? = null

    private fun isSelfTajasbProcess(context: Context?): Boolean {
        val pkg = context?.packageName?.lowercase() ?: return false
        return pkg.startsWith("com.tajasb") ||
            pkg.startsWith("com.aistudio.tajasb") ||
            pkg.startsWith("com.aistudio.cuancame") ||
            pkg == "com.example"
    }

    fun setLoggingEnabled(enabled: Boolean) {
        if (_isLoggingEnabled.value == enabled) return
        _isLoggingEnabled.value = enabled
        if (enabled) {
            log(LogLevel.INFO, "VcamDebug", "🟢 Live Debug Logging AKTIF (Maks $MAX_LOGS log)")
        }
    }

    fun toggleLogging(): Boolean {
        val next = !_isLoggingEnabled.value
        setLoggingEnabled(next)
        return next
    }

    fun clearLogs(silent: Boolean = false) {
        synchronized(lock) {
            logQueue.clear()
            recentRemoteKeys.clear()
            _logsFlow.value = emptyList()
        }
        if (!silent && _isLoggingEnabled.value) {
            log(LogLevel.INFO, "VcamDebug", "🗑️ Log dibersihkan oleh pengguna")
        }
    }

    fun appendRemoteLog(
        level: LogLevel,
        tag: String,
        message: String,
        errorDetails: String? = null,
        pid: Int = 0,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (!_isLoggingEnabled.value) return
        if (pid != 0 && pid == Process.myPid()) return

        val dedupeKey = "$pid:$timestamp:${level.name}:$tag:$message"
        val timeStr = synchronized(timeFormat) { timeFormat.format(Date(timestamp)) }
        val entry = LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = timestamp,
            timeFormatted = timeStr,
            level = level,
            tag = if (pid > 0) "[$pid]$tag" else tag,
            message = message,
            errorDetails = errorDetails,
            processId = pid
        )

        synchronized(lock) {
            if (recentRemoteKeys.contains(dedupeKey)) {
                return
            }
            while (recentRemoteKeys.size >= 120) {
                recentRemoteKeys.pollFirst()
            }
            recentRemoteKeys.offerLast(dedupeKey)
            while (logQueue.size >= MAX_LOGS) {
                logQueue.pollFirst()
            }
            logQueue.offerLast(entry)
            _logsFlow.value = logQueue.toList()
        }
    }

    private fun pushRemoteLogViaProviderAsync(
        context: Context,
        level: LogLevel,
        tag: String,
        message: String,
        errStr: String?,
        now: Long,
        pid: Int
    ) {
        try {
            val appCtx = context.applicationContext ?: context
            ipcExecutor.execute {
                val authorities = mutableListOf<String>()
                cachedProviderAuthority?.let { authorities.add(it) }
                try {
                    val providers = appCtx.packageManager.queryContentProviders(null, 0, 0)
                    for (p in providers) {
                        val auth = p.authority ?: continue
                        if ((auth.contains("tajasb") || auth.contains("cuancame")) && !authorities.contains(auth)) {
                            authorities.add(auth)
                        }
                    }
                } catch (_: Throwable) {}
                for (a in KNOWN_PROVIDER_AUTHORITIES) {
                    if (!authorities.contains(a)) authorities.add(a)
                }

                val extras = android.os.Bundle().apply {
                    putString("level", level.name)
                    putString("tag", tag)
                    putString("msg", message)
                    putString("message", message)
                    putString("err", errStr)
                    putBoolean("isError", level == LogLevel.ERROR || level == LogLevel.WARN)
                    putLong("time", now)
                    putInt("pid", pid)
                }

                for (auth in authorities) {
                    try {
                        val res = appCtx.contentResolver.call(
                            android.net.Uri.parse("content://$auth"),
                            "pushLog",
                            null,
                            extras
                        )
                        if (res != null && res.getBoolean("success", false)) {
                            cachedProviderAuthority = auth
                            break
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        context: Context? = null
    ) {
        if (!_isLoggingEnabled.value) {
            return
        }

        val now = System.currentTimeMillis()
        val timeStr = synchronized(timeFormat) { timeFormat.format(Date(now)) }
        val errStr = throwable?.let { Log.getStackTraceString(it) }
        val myPid = Process.myPid()

        // Standard Android Logcat output
        val fullMsg = if (errStr.isNullOrBlank()) message else "$message\n$errStr"
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, fullMsg)
            LogLevel.DEBUG -> Log.d(tag, fullMsg)
            LogLevel.INFO -> Log.i(tag, fullMsg)
            LogLevel.WARN -> Log.w(tag, fullMsg)
            LogLevel.ERROR -> Log.e(tag, fullMsg)
        }

        val entry = LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = now,
            timeFormatted = timeStr,
            level = level,
            tag = tag,
            message = message,
            errorDetails = errStr,
            processId = myPid
        )

        synchronized(lock) {
            while (logQueue.size >= MAX_LOGS) {
                logQueue.pollFirst()
            }
            logQueue.offerLast(entry)
            _logsFlow.value = logQueue.toList()
        }

        // If running inside a hooked target/cloned process, forward to Tajasb Floating Control
        // via BOTH ContentProvider ("pushLog") AND Broadcast (explicit + implicit) so Android 14+/HyperOS
        // or cloner sandboxes cannot drop logs!
        if (context != null && !isSelfTajasbProcess(context)) {
            pushRemoteLogViaProviderAsync(context, level, tag, message, errStr, now, myPid)
            try {
                val intent = Intent(ACTION_DEBUG_LOG).apply {
                    putExtra(EXTRA_LOG_LEVEL, level.name)
                    putExtra(EXTRA_LOG_TAG, tag)
                    putExtra(EXTRA_LOG_MSG, message)
                    putExtra(EXTRA_LOG_TIME, now)
                    putExtra(EXTRA_LOG_ERR, errStr)
                    putExtra(EXTRA_LOG_PID, myPid)
                }
                context.sendBroadcast(intent)
                for (targetPkg in KNOWN_RECEIVER_PACKAGES) {
                    try {
                        intent.setPackage(targetPkg)
                        context.sendBroadcast(intent)
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }
    }

    fun v(tag: String, msg: String, context: Context? = null) = log(LogLevel.VERBOSE, tag, msg, null, context)
    fun d(tag: String, msg: String, context: Context? = null) = log(LogLevel.DEBUG, tag, msg, null, context)
    fun i(tag: String, msg: String, context: Context? = null) = log(LogLevel.INFO, tag, msg, null, context)
    fun w(tag: String, msg: String, err: Throwable? = null, context: Context? = null) = log(LogLevel.WARN, tag, msg, err, context)
    fun e(tag: String, msg: String, err: Throwable? = null, context: Context? = null) = log(LogLevel.ERROR, tag, msg, err, context)

    fun registerBroadcastReceiver(context: Context, scope: CoroutineScope) {
        if (logReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (!_isLoggingEnabled.value) return
                if (intent?.action != ACTION_DEBUG_LOG) return
                val lvlName = intent.getStringExtra(EXTRA_LOG_LEVEL) ?: LogLevel.INFO.name
                val lvl = try { LogLevel.valueOf(lvlName) } catch (_: Exception) { LogLevel.INFO }
                val tag = intent.getStringExtra(EXTRA_LOG_TAG) ?: "IPC"
                val msg = intent.getStringExtra(EXTRA_LOG_MSG) ?: ""
                val time = intent.getLongExtra(EXTRA_LOG_TIME, System.currentTimeMillis())
                val err = intent.getStringExtra(EXTRA_LOG_ERR)
                val pid = intent.getIntExtra(EXTRA_LOG_PID, 0)
                appendRemoteLog(lvl, tag, msg, err, pid, time)
            }
        }

        val filter = IntentFilter(ACTION_DEBUG_LOG)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            logReceiver = receiver
        } catch (_: Throwable) {}
    }

    fun unregisterBroadcastReceiver(context: Context) {
        logReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Throwable) {}
            logReceiver = null
        }
    }

    fun getFormattedLogs(limit: Int = MAX_LOGS, customList: List<LogEntry>? = null): String {
        val rawList: List<LogEntry> = if (customList != null) {
            customList
        } else {
            synchronized(lock) {
                logQueue.toList()
            }
        }
        val list = if (limit > 0 && rawList.size > limit) rawList.takeLast(limit) else rawList

        val sb = StringBuilder()
        sb.append("=== TAJASB DEBUG LOGS (Menampilkan: ${list.size} / Buffer: ${rawList.size} / Kapasitas: $MAX_LOGS) ===\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n")
        sb.append("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        sb.append("Logging Enabled: ${_isLoggingEnabled.value}\n")
        sb.append("--------------------------------------------------\n\n")

        if (list.isEmpty()) {
            sb.append("(Belum ada log terekam)\n")
        } else {
            list.forEach { entry ->
                sb.append("[${entry.timeFormatted}] [${entry.level.label}] [${entry.tag}] [PID:${entry.processId}] ${entry.message}\n")
                if (!entry.errorDetails.isNullOrBlank()) {
                    sb.append("   └─ Error: ${entry.errorDetails}\n")
                }
            }
        }
        sb.append("\n=== END OF LOGS ===")
        return sb.toString()
    }

    fun copyLogsToClipboard(context: Context, limit: Int = MAX_LOGS, customList: List<LogEntry>? = null): Pair<Int, Boolean> {
        val text = getFormattedLogs(limit, customList)
        val count = if (customList != null) {
            if (limit > 0 && customList.size > limit) limit else customList.size
        } else {
            synchronized(lock) {
                if (limit > 0 && logQueue.size > limit) limit else logQueue.size
            }
        }

        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Tajasb Debug Logs", text)
            clipboard.setPrimaryClip(clip)
            Pair(count, true)
        } catch (e: Exception) {
            log(LogLevel.ERROR, "VcamDebug", "Gagal menyalin ke clipboard: ${e.message}", e)
            Pair(0, false)
        }
    }

    fun copySingleLog(context: Context, entry: LogEntry): Boolean {
        return try {
            val sb = StringBuilder()
            sb.append("[${entry.timeFormatted}] [${entry.level.label}] [${entry.tag}] ${entry.message}")
            if (!entry.errorDetails.isNullOrBlank()) {
                sb.append("\n└─ Error Details: ${entry.errorDetails}")
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Tajasb Log Entry #${entry.id}", sb.toString())
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            log(LogLevel.ERROR, "VcamDebug", "Gagal menyalin 1 log: ${e.message}", e)
            false
        }
    }

    fun exportLogToFile(context: Context, limit: Int = MAX_LOGS): java.io.File? {
        return try {
            val dir = context.getExternalFilesDir(null) ?: context.cacheDir
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = java.io.File(dir, "tajasb_debug_log_$timeStamp.txt")
            val text = getFormattedLogs(limit)
            file.writeText(text)
            file.setReadable(true, false)
            file
        } catch (e: Exception) {
            log(LogLevel.ERROR, "VcamDebug", "Gagal menulis file log: ${e.message}", e)
            null
        }
    }

    fun shareLogs(context: Context, limit: Int = MAX_LOGS, customList: List<LogEntry>? = null) {
        try {
            val text = getFormattedLogs(limit, customList)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Tajasb Debug Logs - Bukti Video Live TikTok")
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Kirim / Bagikan Bukti Debug Log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            log(LogLevel.ERROR, "VcamDebug", "Gagal membagikan log: ${e.message}", e)
        }
    }

    fun logVideo(tag: String, msg: String, err: Throwable? = null, context: Context? = null) {
        val lvl = if (err != null) LogLevel.ERROR else LogLevel.INFO
        log(lvl, "🎬 $tag", msg, err, context)
    }

    fun logHook(tag: String, msg: String, err: Throwable? = null, context: Context? = null) {
        val lvl = if (err != null) LogLevel.ERROR else LogLevel.DEBUG
        log(lvl, "🪝 $tag", msg, err, context)
    }

    fun logWatchdog(msg: String, isStall: Boolean = false, context: Context? = null) {
        val lvl = if (isStall) LogLevel.WARN else LogLevel.DEBUG
        log(lvl, "⏱️ Watchdog", msg, null, context)
    }

    fun getRecentLogs(count: Int = 10): List<LogEntry> {
        synchronized(lock) {
            val list = logQueue.toList()
            return if (list.size > count) list.takeLast(count) else list
        }
    }
}
