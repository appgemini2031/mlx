package com.tajasb.debug

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data informasi pemulihan crash
 */
data class CrashRecoveryInfo(
    val timestamp: Long,
    val threadName: String,
    val errorMessage: String,
    val wasMainThread: Boolean,
    val recoveredCount: Int
)

/**
 * CrashShieldManager: Sistem Pencegah Force Close & Auto-Recovery APK.
 * 
 * 1. SafeLooper (Cockroach Pattern):
 *    Mengapit Main UI Looper dalam try-catch loop agar crash/exception di UI Thread
 *    tidak pernah membunuh proses aplikasi atau membuat APK keluar.
 * 
 * 2. Global Uncaught Exception Auto-Recovery:
 *    Untuk thread background atau fatal error di luar UI looper, menangkap crash,
 *    mencatat log, dan secara instan meluncurkan ulang MainActivity dengan bendera
 *    FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK sebelum proses lama diakhiri.
 * 
 * 3. Anti-Loop Protection:
 *    Menghitung frekuensi crash beruntun (< 5 detik). Jika lebih dari 3 kali berturut-turut,
 *    memberikan toleransi fallback agar tidak terjadi infinite reboot loop.
 */
object CrashShieldManager {
    private const val TAG = "CrashShieldManager"
    const val PREFS_NAME = "tajasb_crash_shield_prefs"
    const val KEY_LAST_CRASH_TIME = "last_crash_time"
    const val KEY_RAPID_CRASH_COUNT = "rapid_crash_count"
    const val KEY_TOTAL_RECOVERED = "total_recovered_count"
    const val KEY_SHIELD_ENABLED = "shield_enabled"

    const val EXTRA_CRASH_RECOVERED = "extra_crash_shield_recovered"
    const val EXTRA_CRASH_MESSAGE = "extra_crash_message"
    const val EXTRA_CRASH_TIME = "extra_crash_time"
    const val EXTRA_IS_UI_THREAD = "extra_is_ui_thread"

    @Volatile
    private var isInstalled = false
    private var appContext: Context? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    private val _recoveryState = MutableStateFlow<CrashRecoveryInfo?>(null)
    val recoveryState: StateFlow<CrashRecoveryInfo?> = _recoveryState.asStateFlow()

    private val _totalRecoveredCount = MutableStateFlow(0)
    val totalRecoveredCount: StateFlow<Int> = _totalRecoveredCount.asStateFlow()

    fun install(context: Context) {
        if (isInstalled) return
        val app = context.applicationContext
        appContext = app
        isInstalled = true

        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _totalRecoveredCount.value = prefs.getInt(KEY_TOTAL_RECOVERED, 0)

        // 1. Inisialisasi CrashReporter (Firestore logger)
        CrashReporter.init(app)

        // 2. Pasang SafeLooper untuk meredam crash di UI Thread
        installSafeLooper(app)

        // 3. Pasang Global UncaughtExceptionHandler untuk background & fatal thread
        installGlobalUncaughtHandler(app)

        Log.i(TAG, "🛡️ CrashShieldManager aktif! Aplikasi dilindungi dari force close.")
        VcamDebugLogger.i("CrashShield", "Sistem Crash Shield aktif: SafeLooper + Auto-Recovery siap")
    }

    /**
     * Memeriksa apakah fitur Crash Shield aktif
     */
    fun isShieldEnabled(): Boolean {
        val app = appContext ?: return true
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHIELD_ENABLED, true)
    }

    /**
     * Mengatur status proteksi Crash Shield
     */
    fun setShieldEnabled(enabled: Boolean) {
        val app = appContext ?: return
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHIELD_ENABLED, enabled)
            .apply()
    }

    /**
     * SafeLooper: Mencegah UI Thread keluar saat terjadi exception
     */
    private fun installSafeLooper(context: Context) {
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                } catch (t: Throwable) {
                    if (t is InterruptedException) break
                    handleUiThreadCrash(context, t)
                }
            }
        }
    }

    /**
     * Menangani crash yang terjadi di antrean UI (Main Looper)
     */
    fun handleUiThreadCrash(context: Context, throwable: Throwable) {
        val errMsg = throwable.message ?: throwable.javaClass.simpleName
        Log.e(TAG, "🛡️ SafeLooper meredam crash UI: $errMsg", throwable)

        // Catat ke logger internal dan Firestore
        VcamDebugLogger.e("CrashShield", "UI Crash diredam aman: $errMsg")
        CrashReporter.logBug("SafeLooperUI", "UI Crash diredam: $errMsg", "ERROR")

        recordRecoveryEvent(context, Thread.currentThread().name, errMsg, isMainThread = true)

        // Tampilkan pesan kilat yang ramah di UI
        Handler(Looper.getMainLooper()).post {
            try {
                android.widget.Toast.makeText(
                    context,
                    "🛡️ Crash Shield: Kendala UI diredam, aplikasi tetap berjalan",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Memasang Global UncaughtExceptionHandler
     */
    private fun installGlobalUncaughtHandler(context: Context) {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleFatalUncaughtCrash(context, thread, throwable)
        }
    }

    /**
     * Menangani Uncaught Exception fatal di thread manapun
     */
    fun handleFatalUncaughtCrash(context: Context, thread: Thread, throwable: Throwable) {
        val errMsg = throwable.message ?: throwable.javaClass.simpleName
        Log.e(TAG, "🛡️ Uncaught Exception fatal di [${thread.name}]: $errMsg", throwable)

        val isMainThread = (thread == Looper.getMainLooper().thread)

        // Log ke debug logger & Firestore
        VcamDebugLogger.e("CrashShield", "Uncaught Exception fatal di [${thread.name}]: $errMsg")
        CrashReporter.logBug("FatalThreadCrash", "[${thread.name}] $errMsg", "ERROR")

        // Cek apakah proteksi diaktifkan
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_SHIELD_ENABLED, true)
        if (!enabled) {
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }

        // Anti-Loop Protection: Hitung crash beruntun (< 5 detik)
        val now = System.currentTimeMillis()
        val lastCrashTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0L)
        val rapidCount = if (now - lastCrashTime < 5000L) {
            prefs.getInt(KEY_RAPID_CRASH_COUNT, 0) + 1
        } else {
            1
        }

        val totalRecovered = prefs.getInt(KEY_TOTAL_RECOVERED, 0) + 1
        prefs.edit()
            .putLong(KEY_LAST_CRASH_TIME, now)
            .putInt(KEY_RAPID_CRASH_COUNT, rapidCount)
            .putInt(KEY_TOTAL_RECOVERED, totalRecovered)
            .apply()

        _totalRecoveredCount.value = totalRecovered
        recordRecoveryEvent(context, thread.name, errMsg, isMainThread)

        // Jika terjadi crash berulang > 3 kali dalam 5 detik, serahkan ke handler bawaan
        // untuk mencegah bootloop tanpa henti
        if (rapidCount > 3) {
            Log.w(TAG, "Crash Shield: Melebihi ambang batas crash beruntun ($rapidCount). Mengakhiri proses secara aman.")
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }

        // Auto-Recovery: Luncurkan kembali MainActivity secara instan
        try {
            val restartIntent = Intent(context, com.example.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_CRASH_RECOVERED, true)
                putExtra(EXTRA_CRASH_MESSAGE, errMsg)
                putExtra(EXTRA_CRASH_TIME, now)
                putExtra(EXTRA_IS_UI_THREAD, isMainThread)
            }
            context.startActivity(restartIntent)
        } catch (e: Throwable) {
            Log.e(TAG, "Gagal meluncurkan intent recovery MainActivity: ${e.message}")
        }

        // Hentikan proses yang crash secara senyap agar instans baru langsung menggantikan
        try {
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        } catch (_: Throwable) {}
    }

    private fun recordRecoveryEvent(
        context: Context,
        threadName: String,
        errorMessage: String,
        isMainThread: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val total = prefs.getInt(KEY_TOTAL_RECOVERED, 0)
        _recoveryState.value = CrashRecoveryInfo(
            timestamp = System.currentTimeMillis(),
            threadName = threadName,
            errorMessage = errorMessage,
            wasMainThread = isMainThread,
            recoveredCount = total
        )
    }

    /**
     * Mereset statistik pemulihan crash
     */
    fun resetStatistics(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_RAPID_CRASH_COUNT, 0)
            .putInt(KEY_TOTAL_RECOVERED, 0)
            .apply()
        _totalRecoveredCount.value = 0
        _recoveryState.value = null
    }
}
