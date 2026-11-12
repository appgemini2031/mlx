package com.tajasb.debug

import android.content.Context
import android.os.Build
import android.util.Log
import com.tajasb.license.LicenseManager
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
 * Menangkap Uncaught Exception dan mencatat log kritis (codec, memori)
 * lalu mengirimkannya secara otomatis ke Firestore sub-koleksi /devices/{deviceId}/logs.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"

    private const val FIRESTORE_PROJECT_ID = "gen-lang-client-0093873107"
    private const val FIRESTORE_DATABASE_ID = "ai-studio-android-tajasb-2fe2569e-1897-4a5c-99ce-a6c3fc1888bb"
    private const val FIRESTORE_API_KEY = "AIzaSyAbeoS_V-a7GzU_JMfP_PFq2w43yCcSUF0"

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var isInitialized = false
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO)

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
            val deviceId = LicenseManager.getInstance(ctx).getDeviceId()
            val msg = throwable.message ?: throwable.javaClass.simpleName
            val stackLines = throwable.stackTrace.take(8).joinToString("\n") { "  at $it" }
            val fullDetail = "$msg\n$stackLines"

            // Kirim secara sinkron atau segera
            sendReportSync(deviceId, "CRASH", "UncaughtException", fullDetail)
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal mengirim crash report: ${t.message}")
        }
    }

    /**
     * Mencatat bug atau error runtime non-fatal (misal: Codec crash, OOM, LSPosed detach)
     */
    fun logBug(tag: String, message: String, level: String = "WARN") {
        val ctx = appContext ?: return
        scope.launch {
            try {
                val deviceId = LicenseManager.getInstance(ctx).getDeviceId()
                sendReportSync(deviceId, level, tag, message)
            } catch (t: Throwable) {
                Log.w(TAG, "Gagal logBug: ${t.message}")
            }
        }
    }

    private fun sendReportSync(deviceId: String, level: String, tag: String, message: String) {
        try {
            val endpoint = "https://firestore.googleapis.com/v1/projects/$FIRESTORE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents/devices/$deviceId/logs?key=$FIRESTORE_API_KEY"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val payload = JSONObject().apply {
                val fields = JSONObject().apply {
                    put("timestamp", JSONObject().put("stringValue", isoDate))
                    put("level", JSONObject().put("stringValue", level))
                    put("tag", JSONObject().put("stringValue", tag))
                    put("message", JSONObject().put("stringValue", message.take(500)))
                    put("model", JSONObject().put("stringValue", "${Build.MANUFACTURER} ${Build.MODEL}"))
                }
                put("fields", fields)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            conn.disconnect()
            Log.d(TAG, "Log terkirim ke Firestore (HTTP $code): [$level] $tag")
        } catch (t: Throwable) {
            Log.w(TAG, "Koneksi log error gagal: ${t.message}")
        }
    }
}
