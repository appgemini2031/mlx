package com.tajasb.remote

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.tajasb.VcamAccessibilityService
import com.tajasb.autotap.AutoTapManager
import com.tajasb.data.VcamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TelegramScreenMonitorManager:
 * Mengirim tangkapan layar (screenshot) resolusi rendah (~420px, JPEG 45%, ~30-45 KB)
 * secara otomatis setiap 2,5 menit (150.000 ms) ke Bot Telegram pribadi pengguna.
 *
 * Dirancang khusus agar:
 * 1. Tidak mengganggu performa / FPS Virtual Camera maupun aplikasi Live.
 * 2. Berjalan senyap di latar belakang menggunakan VcamAccessibilityService (API 30+)
 *    atau fallback Root (screencap) tanpa popup izin perekaman layar.
 * 3. Menyertakan caption ringkas (Baterai, Suhu, Sisa RAM, Video Aktif, dan Status Auto-Tap).
 * 4. Tidak menghapus foto otomatis di chat Telegram sehingga pengguna leluasa meninjau
 *    dan menghapus riwayat foto secara manual kapan saja.
 */
class TelegramScreenMonitorManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TelegramScreenMonitor"
        const val PREFS_NAME = "tajasb_telegram_screen_prefs"

        private const val KEY_ENABLED = "key_tg_screen_enabled"
        private const val KEY_BOT_TOKEN = "key_tg_bot_token"
        private const val KEY_CHAT_ID = "key_tg_chat_id"
        private const val KEY_DEVICE_LABEL = "key_tg_device_label"
        private const val KEY_SENT_COUNT = "key_tg_sent_count"
        private const val KEY_LAST_SENT_MS = "key_tg_last_sent_ms"

        /** Interval tetap 2,5 menit (150 detik = 150.000 ms) sesuai permintaan pengguna */
        const val MONITOR_INTERVAL_MS = 150_000L

        /** Resolusi lebar maksimum & kualitas kompresi JPEG ringan agar hemat RAM & kuota */
        private const val LOW_RES_MAX_WIDTH_PX = 420
        private const val LOW_RES_JPEG_QUALITY = 45

        @Volatile
        private var INSTANCE: TelegramScreenMonitorManager? = null

        fun getInstance(context: Context): TelegramScreenMonitorManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TelegramScreenMonitorManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendMutex = Mutex()
    private var monitorJob: Job? = null

    private val defaultDeviceName: String =
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _botToken = MutableStateFlow(
        com.tajasb.security.LocalVaultCrypto.getDecryptedOrMigrate(prefs, KEY_BOT_TOKEN, "")
    )
    val botToken: StateFlow<String> = _botToken.asStateFlow()

    private val _chatId = MutableStateFlow(
        com.tajasb.security.LocalVaultCrypto.getDecryptedOrMigrate(prefs, KEY_CHAT_ID, "")
    )
    val chatId: StateFlow<String> = _chatId.asStateFlow()

    private val _deviceLabel = MutableStateFlow(
        prefs.getString(KEY_DEVICE_LABEL, defaultDeviceName)?.takeIf { it.isNotBlank() } ?: defaultDeviceName
    )
    val deviceLabel: StateFlow<String> = _deviceLabel.asStateFlow()

    private val _sentCount = MutableStateFlow(prefs.getInt(KEY_SENT_COUNT, 0))
    val sentCount: StateFlow<Int> = _sentCount.asStateFlow()

    private val _lastSentTimeMs = MutableStateFlow(prefs.getLong(KEY_LAST_SENT_MS, 0L))
    val lastSentTimeMs: StateFlow<Long> = _lastSentTimeMs.asStateFlow()

    private val _lastStatusText = MutableStateFlow(
        if (_isEnabled.value) "Siap memantau layar setiap 2,5 menit" else "Pemantau layar Telegram nonaktif"
    )
    val lastStatusText: StateFlow<String> = _lastStatusText.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isFetchingChatId = MutableStateFlow(false)
    val isFetchingChatId: StateFlow<Boolean> = _isFetchingChatId.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reloadFromPrefs()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        if (_isEnabled.value && isConfigured()) {
            startMonitorLoop()
        }
    }

    fun isConfigured(): Boolean {
        return _botToken.value.trim().isNotEmpty() && _chatId.value.trim().isNotEmpty()
    }

    fun ensureStarted() {
        if (_isEnabled.value && isConfigured()) {
            if (monitorJob?.isActive != true) {
                startMonitorLoop()
            }
        }
    }

    fun setBotToken(token: String) {
        val cleaned = com.tajasb.security.LocalVaultCrypto.decryptFromPrefs(token.trim()).trim()
        _botToken.value = cleaned
        com.tajasb.security.LocalVaultCrypto.putEncrypted(prefs.edit(), KEY_BOT_TOKEN, cleaned).apply()
    }

    fun setChatId(id: String) {
        val cleaned = com.tajasb.security.LocalVaultCrypto.decryptFromPrefs(id.trim()).trim()
        _chatId.value = cleaned
        com.tajasb.security.LocalVaultCrypto.putEncrypted(prefs.edit(), KEY_CHAT_ID, cleaned).apply()
    }

    fun setDeviceLabel(label: String) {
        val cleaned = label.trim().ifEmpty { defaultDeviceName }
        _deviceLabel.value = cleaned
        prefs.edit().putString(KEY_DEVICE_LABEL, cleaned).apply()
    }

    fun setEnabled(enabled: Boolean): Pair<Boolean, String> {
        if (enabled && !isConfigured()) {
            _isEnabled.value = false
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            val msg = "⚠️ Isi Bot Token & Chat ID Telegram terlebih dahulu."
            _lastStatusText.value = msg
            return false to msg
        }

        _isEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()

        return if (enabled) {
            startMonitorLoop()
            val msg = "✅ Aktif: Kirim tangkapan layar ke Telegram tiap 2,5 menit"
            _lastStatusText.value = msg
            true to msg
        } else {
            stopMonitorLoop()
            val msg = "⏹️ Pemantau layar Telegram dihentikan"
            _lastStatusText.value = msg
            true to msg
        }
    }

    fun reloadFromPrefs() {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val token = com.tajasb.security.LocalVaultCrypto.getDecryptedOrMigrate(prefs, KEY_BOT_TOKEN, "")
        val chat = com.tajasb.security.LocalVaultCrypto.getDecryptedOrMigrate(prefs, KEY_CHAT_ID, "")
        val label = prefs.getString(KEY_DEVICE_LABEL, defaultDeviceName)?.takeIf { it.isNotBlank() } ?: defaultDeviceName
        val count = prefs.getInt(KEY_SENT_COUNT, 0)
        val lastMs = prefs.getLong(KEY_LAST_SENT_MS, 0L)

        _botToken.value = token
        _chatId.value = chat
        _deviceLabel.value = label
        _sentCount.value = count
        _lastSentTimeMs.value = lastMs

        if (_isEnabled.value != enabled) {
            _isEnabled.value = enabled
            if (enabled && isConfigured()) {
                startMonitorLoop()
            } else if (!enabled) {
                stopMonitorLoop()
            }
        }
    }

    private fun startMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = managerScope.launch {
            _lastStatusText.value = "⏳ Aktif • Tangkapan pertama dikirim dalam 8 detik..."
            delay(8_000L)
            while (isActive && _isEnabled.value) {
                if (isConfigured()) {
                    captureAndSendInternal(isManualTest = false)
                }
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    private fun stopMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Mengambil Chat ID otomatis dari pesan terakhir yang dikirim pengguna ke Bot Telegram (/getUpdates).
     */
    suspend fun autoDetectChatId(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = _botToken.value.trim()
        if (token.isEmpty()) {
            val msg = "⚠️ Masukkan Bot Token terlebih dahulu, lalu kirim pesan /start ke bot Anda di Telegram."
            _lastStatusText.value = msg
            return@withContext false to msg
        }

        _isFetchingChatId.value = true
        _lastStatusText.value = "🔍 Mencari Chat ID dari pesan terakhir di Bot..."
        try {
            val url = URL("https://api.telegram.org/bot$token/getUpdates?limit=10")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val code = conn.responseCode
            val body = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            }
            conn.disconnect()

            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                val desc = json.optString("description", "Token tidak valid")
                val msg = "❌ Gagal cek bot: $desc"
                _lastStatusText.value = msg
                return@withContext false to msg
            }

            val results = json.optJSONArray("result")
            if (results == null || results.length() == 0) {
                val msg = "ℹ️ Belum ada pesan masuk. Buka bot Anda di Telegram lalu kirim /start, kemudian klik tombol ini lagi."
                _lastStatusText.value = msg
                return@withContext false to msg
            }

            var foundChatId: String? = null
            var foundChatName = ""
            for (i in results.length() - 1 downTo 0) {
                val update = results.optJSONObject(i) ?: continue
                val msgObj = update.optJSONObject("message")
                    ?: update.optJSONObject("edited_message")
                    ?: update.optJSONObject("channel_post")
                val chatObj = msgObj?.optJSONObject("chat") ?: continue
                val idVal = chatObj.optLong("id", 0L)
                if (idVal != 0L) {
                    foundChatId = idVal.toString()
                    val first = chatObj.optString("first_name", "")
                    val title = chatObj.optString("title", "")
                    val user = chatObj.optString("username", "")
                    foundChatName = listOf(first, title, if (user.isNotBlank()) "@$user" else "")
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    break
                }
            }

            if (foundChatId != null) {
                setChatId(foundChatId)
                val nameSuffix = if (foundChatName.isNotBlank()) " ($foundChatName)" else ""
                val msg = "✅ Chat ID ditemukan: $foundChatId$nameSuffix"
                _lastStatusText.value = msg
                true to msg
            } else {
                val msg = "ℹ️ Chat ID belum ditemukan. Kirim pesan /start ke bot Telegram Anda lalu coba lagi."
                _lastStatusText.value = msg
                false to msg
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error autoDetectChatId", t)
            val msg = "❌ Gagal koneksi ke Telegram: ${t.localizedMessage ?: "Periksa internet"}"
            _lastStatusText.value = msg
            false to msg
        } finally {
            _isFetchingChatId.value = false
        }
    }

    /**
     * Memicu pengiriman tangkapan layar saat ini juga (untuk tombol Tes Kirim Sekarang).
     */
    suspend fun sendSnapshotNow(isManualTest: Boolean = true): Pair<Boolean, String> {
        if (!isConfigured()) {
            val msg = "⚠️ Isi Bot Token dan Chat ID Telegram terlebih dahulu."
            _lastStatusText.value = msg
            return false to msg
        }
        return captureAndSendInternal(isManualTest = isManualTest)
    }

    private suspend fun captureAndSendInternal(isManualTest: Boolean): Pair<Boolean, String> =
        sendMutex.withLock {
            if (_isSending.value) {
                return false to "⏳ Pengiriman sedang berlangsung..."
            }
            _isSending.value = true
            try {
                _lastStatusText.value = "📸 Mengambil tangkapan layar ringan..."
                val (jpegBytes, captureSource) = captureLightweightScreenshotJpeg()
                if (jpegBytes == null || jpegBytes.isEmpty()) {
                    val a11yRunning = VcamAccessibilityService.isServiceRunning
                    val hint = if (!a11yRunning) {
                        "Aktifkan Aksesibilitas aplikasi (atau izin Root) agar bisa menangkap layar tanpa popup."
                    } else {
                        "Pastikan layar menyala & tidak berada di halaman yang memblokir screenshot."
                    }
                    val err = "⚠️ Gagal menangkap layar. $hint"
                    _lastStatusText.value = err
                    return false to err
                }

                val sizeKb = (jpegBytes.size / 1024).coerceAtLeast(1)
                _lastStatusText.value = "🚀 Mengirim foto ($sizeKb KB • $captureSource) ke Telegram..."

                val caption = buildLiveCaption(isManualTest = isManualTest, sizeKb = sizeKb, source = captureSource)
                val (ok, apiMsg) = uploadPhotoToTelegram(
                    botToken = _botToken.value.trim(),
                    chatId = _chatId.value.trim(),
                    jpegBytes = jpegBytes,
                    caption = caption
                )

                if (ok) {
                    val now = System.currentTimeMillis()
                    val nextCount = _sentCount.value + 1
                    _sentCount.value = nextCount
                    _lastSentTimeMs.value = now
                    prefs.edit()
                        .putInt(KEY_SENT_COUNT, nextCount)
                        .putLong(KEY_LAST_SENT_MS, now)
                        .apply()

                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
                    val statusMsg = "✅ Terkirim pukul $timeStr ($sizeKb KB • Total #$nextCount)"
                    _lastStatusText.value = statusMsg
                    true to statusMsg
                } else {
                    val errMsg = "❌ Gagal kirim ke Telegram: $apiMsg"
                    _lastStatusText.value = errMsg
                    false to errMsg
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in captureAndSendInternal", t)
                val errMsg = "❌ Error: ${t.localizedMessage ?: "Gagal mengirim screenshot"}"
                _lastStatusText.value = errMsg
                false to errMsg
            } finally {
                _isSending.value = false
            }
        }

    /**
     * Mengambil screenshot resolusi rendah (~420px lebar, JPEG 45%) secara hemat memori:
     * 1. Jalur Utama: VcamAccessibilityService.takeScreenshot (Android 11+ / API 30+)
     * 2. Jalur Cadangan: Root `su -c screencap -p` dengan inSampleSize = 2
     */
    private suspend fun captureLightweightScreenshotJpeg(): Pair<ByteArray?, String> =
        withContext(Dispatchers.IO) {
            // 1. Coba melalui VcamAccessibilityService (Zero-Popup, Sangat Ringan)
            val a11yService = VcamAccessibilityService.instance
            if (a11yService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bytes = try {
                    a11yService.captureLowResScreenJpeg(
                        maxWidthPx = LOW_RES_MAX_WIDTH_PX,
                        quality = LOW_RES_JPEG_QUALITY
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "A11y screenshot gagal: ${t.message}")
                    null
                }
                if (bytes != null && bytes.isNotEmpty()) {
                    return@withContext bytes to "A11y"
                }
            }

            // 2. Fallback menggunakan Root `su -c screencap -p`
            val rootBytes = captureViaRootScreencap(
                maxWidthPx = LOW_RES_MAX_WIDTH_PX,
                quality = LOW_RES_JPEG_QUALITY
            )
            if (rootBytes != null && rootBytes.isNotEmpty()) {
                return@withContext rootBytes to "Root"
            }

            null to "None"
        }

    private fun captureViaRootScreencap(maxWidthPx: Int, quality: Int): ByteArray? {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p"))
            val rawPngBytes = process.inputStream.use { it.readBytes() }
            process.waitFor()
            if (rawPngBytes.isEmpty()) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = 2
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeByteArray(rawPngBytes, 0, rawPngBytes.size, opts) ?: return null
            val origW = decoded.width.coerceAtLeast(1)
            val origH = decoded.height.coerceAtLeast(1)
            val targetW = maxWidthPx.coerceIn(240, 720).coerceAtMost(origW)
            val targetH = ((origH.toFloat() / origW.toFloat()) * targetW).toInt().coerceAtLeast(1)

            val scaled = if (targetW < origW) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else {
                decoded
            }
            if (scaled !== decoded) {
                try { decoded.recycle() } catch (_: Throwable) {}
            }

            val baos = ByteArrayOutputStream(36 * 1024)
            scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(20, 80), baos)
            try { scaled.recycle() } catch (_: Throwable) {}
            baos.toByteArray()
        } catch (t: Throwable) {
            Log.w(TAG, "Root screencap fallback gagal: ${t.message}")
            null
        } finally {
            try { process?.destroy() } catch (_: Throwable) {}
        }
    }

    /**
     * Menyusun keterangan (caption) foto yang ringkas:
     * Device, Waktu, Baterai & Suhu, Sisa RAM, Video Aktif, serta Status Auto-Tap.
     */
    private fun buildLiveCaption(isManualTest: Boolean, sizeKb: Int, source: String): String {
        val nowStr = SimpleDateFormat("HH:mm:ss • dd/MM", Locale.getDefault()).format(Date())
        val label = _deviceLabel.value.ifBlank { defaultDeviceName }

        // 1. Info Baterai & Suhu
        val (batPct, isCharging, tempC) = readBatterySnapshot()
        val chargeLabel = if (isCharging) "⚡ Cas" else "🔋 Baterai"
        val tempStr = if (tempC > 0f) String.format(Locale.US, "%.1f°C", tempC) else "-"

        // 2. Info RAM
        val (availRamMb, totalRamMb) = readRamSnapshot()

        // 3. Info Video Aktif
        val videoSummary = try {
            val repo = VcamRepository.getInstance(context)
            val cfg = repo.config.value
            val currentVidName = cfg.currentVideo?.name?.takeIf { it.isNotBlank() }
                ?: cfg.videoPath.substringAfterLast('/').takeIf { it.isNotBlank() }
                ?: "Belum dipilih"
            val pauseTag = if (cfg.isVideoPaused) " (Jeda)" else ""
            "$currentVidName$pauseTag"
        } catch (_: Throwable) {
            "-"
        }

        // 4. Info Auto-Tap & Koreografi
        val tapSummary = try {
            val tapMgr = AutoTapManager.getInstance(context)
            val running = tapMgr.isTapping.value
            val activeGroupId = tapMgr.activeGroupId.value
            val activeGroup = tapMgr.choreographyGroups.value.firstOrNull { it.id == activeGroupId }?.name ?: "-"
            val count = tapMgr.totalTaps.value
            val randomChoreo = if (tapMgr.isRandomChoreographyOrder.value) " • Acak" else ""
            if (running) {
                "🟢 ON ($activeGroup$randomChoreo • $count tap)"
            } else {
                "⚪ OFF ($activeGroup)"
            }
        } catch (_: Throwable) {
            "-"
        }

        val header = if (isManualTest) {
            "📸 TES TANGKAPAN LAYAR LIVE"
        } else {
            "📸 PANTAU LAYAR LIVE (2.5m)"
        }

        return buildString {
            appendLine(header)
            appendLine("📱 Device: $label")
            appendLine("🕒 Waktu: $nowStr")
            appendLine("$chargeLabel: $batPct% • 🌡️ Suhu: $tempStr")
            appendLine("🧠 Sisa RAM: $availRamMb MB / $totalRamMb MB")
            appendLine("🎬 Video: $videoSummary")
            append("👆 Auto-Tap: $tapSummary ($sizeKb KB • $source)")
        }
    }

    private fun readBatterySnapshot(): Triple<Int, Boolean, Float> {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
            val pct = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 0
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempC = if (tempTenths > 0) tempTenths / 10f else 0f
            Triple(pct, charging, tempC)
        } catch (_: Throwable) {
            Triple(0, false, 0f)
        }
    }

    private fun readRamSnapshot(): Pair<Long, Long> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            val availMb = (memInfo.availMem / (1024L * 1024L)).coerceAtLeast(0L)
            val totalMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1L)
            availMb to totalMb
        } catch (_: Throwable) {
            0L to 0L
        }
    }

    /**
     * Mengunggah byte JPEG ke endpoint `sendPhoto` Telegram Bot API menggunakan multipart/form-data.
     * Foto yang terkirim TIDAK dihapus otomatis agar pengguna dapat menghapus sendiri secara manual.
     */
    private fun uploadPhotoToTelegram(
        botToken: String,
        chatId: String,
        jpegBytes: ByteArray,
        caption: String
    ): Pair<Boolean, String> {
        val boundary = "----TajasbLiveBoundary${System.currentTimeMillis()}"
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.telegram.org/bot$botToken/sendPhoto")
            conn = (url.openConnection() as HttpURLConnection).apply {
                doInput = true
                doOutput = true
                useCaches = false
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "POST"
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            DataOutputStream(conn.outputStream).use { dos ->
                // Field 1: chat_id
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"chat_id\"$lineEnd$lineEnd")
                dos.write(chatId.toByteArray(Charsets.UTF_8))
                dos.writeBytes(lineEnd)

                // Field 2: caption
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"caption\"$lineEnd$lineEnd")
                dos.write(caption.toByteArray(Charsets.UTF_8))
                dos.writeBytes(lineEnd)

                // Field 3: photo (JPEG)
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes(
                    "Content-Disposition: form-data; name=\"photo\"; filename=\"live_screen.jpg\"$lineEnd"
                )
                dos.writeBytes("Content-Type: image/jpeg$lineEnd$lineEnd")
                dos.write(jpegBytes)
                dos.writeBytes(lineEnd)

                // End boundary
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                dos.flush()
            }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            }

            val json = JSONObject(responseText)
            if (json.optBoolean("ok", false)) {
                true to "OK"
            } else {
                val desc = json.optString("description", "HTTP $code")
                false to desc
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal unggah foto ke Telegram: ${t.message}")
            false to (t.localizedMessage ?: "Gagal terhubung ke server Telegram")
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }

    fun exportToJsonObject(): JSONObject {
        return JSONObject().apply {
            put("isEnabled", _isEnabled.value)
            put("botToken", com.tajasb.security.LocalVaultCrypto.encryptHardwareBound(_botToken.value))
            put("chatId", com.tajasb.security.LocalVaultCrypto.encryptHardwareBound(_chatId.value))
            put("deviceLabel", _deviceLabel.value)
        }
    }

    fun importFromJsonObject(obj: JSONObject) {
        if (obj.has("botToken")) {
            val rawToken = obj.optString("botToken", "")
            setBotToken(com.tajasb.security.LocalVaultCrypto.decryptFromPrefs(rawToken))
        }
        if (obj.has("chatId")) {
            val rawChat = obj.optString("chatId", "")
            setChatId(com.tajasb.security.LocalVaultCrypto.decryptFromPrefs(rawChat))
        }
        if (obj.has("deviceLabel")) setDeviceLabel(obj.optString("deviceLabel", defaultDeviceName))
        if (obj.has("isEnabled")) {
            val wantEnabled = obj.optBoolean("isEnabled", false)
            setEnabled(wantEnabled)
        }
    }
}
