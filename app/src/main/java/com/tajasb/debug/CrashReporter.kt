package com.tajasb.debug

import android.content.Context
import android.os.Build
import android.util.Log
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pelapor Crash & Bug Otomatis (CrashReporter).
 */
object CrashReporter {
    private const val TAG = "CrashReporter"


    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var isInitialized = false
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getDeviceId(ctx: Context): String {
        return try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_DEVICE"
        } catch (_: Throwable) {
            "UNKNOWN_DEVICE"
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        val app = context.applicationContext
        appContext = app
        isInitialized = true

        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
            CrashShieldManager.handleFatalUncaughtCrash(app, thread, throwable)
        }
        Log.i(TAG, "CrashReporter & CrashShield berhasil diinisialisasi")
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val ctx = appContext ?: return
            val deviceId = getDeviceId(ctx)
            val msg = throwable.message ?: throwable.javaClass.simpleName
            val stackLines = throwable.stackTrace.take(8).joinToString("\n") { "  at $it" }
            val fullDetail = "$msg\n$stackLines"

            sendReportSync(deviceId, "CRASH", "UncaughtException", fullDetail)
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal mengirim crash report: ${t.message}")
        }
    }

    fun logBug(tag: String, message: String, level: String = "WARN") {
        val ctx = appContext ?: return
        scope.launch {
            try {
                val deviceId = getDeviceId(ctx)
                sendReportSync(deviceId, level, tag, message)
            } catch (t: Throwable) {
                Log.w(TAG, "Gagal logBug: ${t.message}")
            }
        }
    }

    private fun sendReportSync(deviceId: String, level: String, tag: String, message: String) {
        when (level) {
            "CRASH" -> Log.e(TAG, "💥 [CRASH] $tag: $message (Device: $deviceId)")
            "WARN" -> Log.w(TAG, "⚠️ [WARN] $tag: $message (Device: $deviceId)")
            else -> Log.i(TAG, "ℹ️ [$level] $tag: $message (Device: $deviceId)")
        }
    }
}
