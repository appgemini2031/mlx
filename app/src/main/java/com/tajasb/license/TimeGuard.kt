package com.tajasb.license

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Enterprise Multi-Layer Time Guard for Tajasb.
 * Prevents rental time fraud, device clock rollback, and offline lease tampering.
 *
 * Security Layers:
 * 1. Monotonic Hardware Clock: SystemClock.elapsedRealtime() cannot be manipulated by user settings.
 * 2. Universal HTTP Date Sync: Obtains true UTC time from trusted global CDNs (Google, Cloudflare).
 * 3. Anti-Rollback High-Water Mark: Detects if user rolls system clock backwards into the past.
 * 4. Drift Tracking: Maintains continuous trusted epoch even if device goes temporarily offline.
 */
class TimeGuard private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TajasbTimeGuard"
        private const val PREFS_TIME = "tajasb_time_vault"
        private const val KEY_HIGH_WATER_MARK = "high_water_mark_ts"
        private const val KEY_LAST_SYNC_NETWORK_TS = "last_sync_net_ts"
        private const val KEY_LAST_SYNC_ELAPSED_RT = "last_sync_elapsed_rt"

        // Toleransi rollback maksimum: 2 menit (120.000 ms) untuk mengakomodasi jitter wajar
        private const val MAX_ALLOWED_ROLLBACK_MS = 120_000L

        // Endpoint CDN global terpercaya dengan uptime 99.999%
        private val TRUSTED_TIME_ENDPOINTS = listOf(
            "https://www.google.com/generate_204",
            "https://1.1.1.1",
            "https://developers.cloudflare.com",
            "https://www.apple.com/library/test/success.html"
        )

        @Volatile
        private var INSTANCE: TimeGuard? = null

        fun getInstance(context: Context): TimeGuard {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TimeGuard(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_TIME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var syncNetworkTimestamp: Long = prefs.getLong(KEY_LAST_SYNC_NETWORK_TS, 0L)
    private var syncElapsedRealtime: Long = prefs.getLong(KEY_LAST_SYNC_ELAPSED_RT, 0L)

    private val _isTimeTampered = MutableStateFlow(false)
    val isTimeTampered: StateFlow<Boolean> = _isTimeTampered.asStateFlow()

    private val _tamperReason = MutableStateFlow<String?>(null)
    val tamperReason: StateFlow<String?> = _tamperReason.asStateFlow()

    private val _isNetworkSynced = MutableStateFlow(syncNetworkTimestamp > 0L)
    val isNetworkSynced: StateFlow<Boolean> = _isNetworkSynced.asStateFlow()

    init {
        checkClockIntegrity()
        triggerNetworkTimeSync()
    }

    /**
     * Checks if the device clock was rolled backwards or tampered with.
     */
    fun checkClockIntegrity(): Boolean {
        val currentSysTime = System.currentTimeMillis()
        val highWaterMark = prefs.getLong(KEY_HIGH_WATER_MARK, 0L)

        // Check Rollback: Waktu sistem sekarang lebih kecil dari waktu puncak sebelumnya (lebih dari 2 menit)
        if (highWaterMark > 0L && currentSysTime < (highWaterMark - MAX_ALLOWED_ROLLBACK_MS)) {
            val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            val msg = "Terdeteksi manipulasi tanggal/jam HP dimundurkan (${sdf.format(Date(currentSysTime))} vs tercatat ${sdf.format(Date(highWaterMark))}). Harap aktifkan tanggal & jam otomatis jaringan."
            Log.w(TAG, msg)
            _isTimeTampered.value = true
            _tamperReason.value = msg
            return false
        }

        // Simpan waktu puncak baru jika waktu sistem terus maju
        if (currentSysTime > highWaterMark) {
            prefs.edit().putLong(KEY_HIGH_WATER_MARK, currentSysTime).apply()
        }

        _isTimeTampered.value = false
        _tamperReason.value = null
        return true
    }

    /**
     * Returns the most accurate, tamper-proof current epoch millisecond.
     * If network time was synced during this session/boot, calculates via Monotonic Clock.
     */
    fun getTrustedCurrentTime(): Long {
        val nowElapsed = SystemClock.elapsedRealtime()

        if (syncNetworkTimestamp > 0L && syncElapsedRealtime > 0L && nowElapsed >= syncElapsedRealtime) {
            val calculated = syncNetworkTimestamp + (nowElapsed - syncElapsedRealtime)
            // Update high water mark
            val highWaterMark = prefs.getLong(KEY_HIGH_WATER_MARK, 0L)
            if (calculated > highWaterMark) {
                prefs.edit().putLong(KEY_HIGH_WATER_MARK, calculated).apply()
            }
            return calculated
        }

        // Fallback to system time with high-water protection
        val sysTime = System.currentTimeMillis()
        val highWaterMark = prefs.getLong(KEY_HIGH_WATER_MARK, 0L)
        val trusted = if (sysTime > highWaterMark) sysTime else highWaterMark

        if (trusted > highWaterMark) {
            prefs.edit().putLong(KEY_HIGH_WATER_MARK, trusted).apply()
        }
        return trusted
    }

    /**
     * Triggers asynchronous background sync with trusted global CDN headers.
     */
    fun triggerNetworkTimeSync(onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val success = fetchTrueNetworkTime()
            if (success) {
                _isNetworkSynced.value = true
                checkClockIntegrity()
            }
            onComplete?.invoke(success)
        }
    }

    private fun fetchTrueNetworkTime(): Boolean {
        for (endpoint in TRUSTED_TIME_ENDPOINTS) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.useCaches = false
                conn.instanceFollowRedirects = false

                val headerDate = conn.date
                if (headerDate > 1700000000000L) { // Post Nov 2023 sanity check
                    val nowElapsed = SystemClock.elapsedRealtime()
                    syncNetworkTimestamp = headerDate
                    syncElapsedRealtime = nowElapsed

                    prefs.edit()
                        .putLong(KEY_LAST_SYNC_NETWORK_TS, headerDate)
                        .putLong(KEY_LAST_SYNC_ELAPSED_RT, nowElapsed)
                        .putLong(KEY_HIGH_WATER_MARK, headerDate)
                        .apply()

                    Log.i(TAG, "Successfully synced atomic world time from $endpoint: ${Date(headerDate)}")
                    return true
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Sync attempt to $endpoint failed: ${t.message}")
            } finally {
                try { conn?.disconnect() } catch (_: Throwable) {}
            }
        }
        return false
    }
}
