package com.tajasb.remote

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.tajasb.data.VcamRepository
import com.tajasb.license.LicenseManager
import com.tajasb.model.ActivationResult
import com.tajasb.model.DecoyProfile
import com.tajasb.model.LicenseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pengelola Telemetri Jarak Jauh (Remote Telemetry & Command Listener).
 * Mengirimkan data detak (heartbeat), RAM, baterai, status sewa, dan nama video aktif ke Firestore,
 * sekaligus merespons instruksi kendali remote dari Bot Telegram secara realtime dan anti-hack.
 */
class RemoteTelemetryManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "RemoteTelemetry"
        private const val PREFS_NAME = "tajasb_remote_prefs"
        private const val KEY_LAST_EXEC_CMD = "last_executed_cmd_id"
        private const val KEY_ACTIVE_NODE_IDX = "active_firebase_node_idx"
        private const val KEY_DYNAMIC_NODES_JSON = "dynamic_firebase_nodes_json"

        private const val FIRESTORE_PROJECT_ID = "gen-lang-client-0093873107"
        private const val FIRESTORE_DATABASE_ID = "ai-studio-android-tajasb-2fe2569e-1897-4a5c-99ce-a6c3fc1888bb"
        private const val FIRESTORE_API_KEY = "AIzaSyAbeoS_V-a7GzU_JMfP_PFq2w43yCcSUF0"

        const val ACTION_REMOTE_MESSAGE = "com.tajasb.ACTION_REMOTE_MESSAGE"
        const val ACTION_REMOTE_KILL = "com.tajasb.ACTION_REMOTE_KILL"
        const val ACTION_EXECUTE_DIRECT_COMMAND = "com.tajasb.action.EXECUTE_DIRECT_COMMAND"
        const val ACTION_TRIGGER_SYNC = "com.tajasb.action.TRIGGER_SYNC"

        @Volatile
        private var instance: RemoteTelemetryManager? = null

        fun getInstance(context: Context): RemoteTelemetryManager {
            return instance ?: synchronized(this) {
                instance ?: RemoteTelemetryManager(context.applicationContext).also { instance = it }
            }
        }
    }

    data class FirebaseNodeConfig(
        val nodeId: String,
        val projectId: String,
        val databaseId: String,
        val apiKey: String
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var syncJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var adaptiveDelayMs: Long = 240_000L // Default: 4 menit (ultra hemat kuota)
    private var lastSentDataHash: Int = 0
    private var lastPatchTimeMs: Long = 0L

    // Multi-Firebase Auto-Switch State (80% Cloud Role)
    private val nodeCooldownUntilMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile
    private var activeNodeIndex: Int = prefs.getInt(KEY_ACTIVE_NODE_IDX, 0).coerceAtLeast(0)
    @Volatile
    private var needsCrossNodeStateHandover: Boolean = false

    private val _isQuotaExceeded = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isQuotaExceeded: kotlinx.coroutines.flow.StateFlow<Boolean> = _isQuotaExceeded

    private val _syncStatusMessage = kotlinx.coroutines.flow.MutableStateFlow("Siap (Multi-Node Hemat Kuota)")
    val syncStatusMessage: kotlinx.coroutines.flow.StateFlow<String> = _syncStatusMessage

    private val _lastSyncTime = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val lastSyncTime: kotlinx.coroutines.flow.StateFlow<Long> = _lastSyncTime

    private var directCommandReceiver: android.content.BroadcastReceiver? = null

    /**
     * Mengambil daftar seluruh Node Firebase yang tersedia (Primary + Fallback + Dynamic Pool dari Bot).
     */
    fun getFirebaseNodePool(): List<FirebaseNodeConfig> {
        val pool = mutableListOf(
            FirebaseNodeConfig(
                nodeId = "NODE-1-PRIMARY",
                projectId = FIRESTORE_PROJECT_ID,
                databaseId = FIRESTORE_DATABASE_ID,
                apiKey = FIRESTORE_API_KEY
            )
        )
        val rawDynamic = com.tajasb.security.LocalVaultCrypto.getDecryptedOrMigrate(
            prefs,
            KEY_DYNAMIC_NODES_JSON,
            ""
        )
        if (rawDynamic.isNotBlank()) {
            try {
                val arr = org.json.JSONArray(rawDynamic)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val pId = obj.optString("projectId").trim()
                    val dbId = obj.optString("databaseId", "(default)").trim().ifEmpty { "(default)" }
                    val key = obj.optString("apiKey").trim()
                    val nId = obj.optString("nodeId", "NODE-${i + 2}").trim()
                    if (pId.isNotEmpty() && key.isNotEmpty() && pool.none { it.projectId == pId && it.databaseId == dbId }) {
                        pool.add(FirebaseNodeConfig(nId, pId, dbId, key))
                    }
                }
            } catch (_: Throwable) {}
        }
        return pool
    }

    /**
     * Menyimpan daftar node cadangan Firebase baru untuk fitur Auto-Switch lintas server (Terenkripsi di lokal).
     */
    fun saveDynamicFirebaseNodes(nodesJsonArrayStr: String): Int {
        return try {
            val arr = org.json.JSONArray(nodesJsonArrayStr)
            val validArr = org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val pId = obj.optString("projectId").trim()
                val dbId = obj.optString("databaseId", "(default)").trim().ifEmpty { "(default)" }
                val key = obj.optString("apiKey").trim()
                val nId = obj.optString("nodeId", "NODE-${i + 2}").trim()
                if (pId.isNotEmpty() && key.isNotEmpty()) {
                    validArr.put(
                        JSONObject()
                            .put("nodeId", nId)
                            .put("projectId", pId)
                            .put("databaseId", dbId)
                            .put("apiKey", key)
                    )
                }
            }
            com.tajasb.security.LocalVaultCrypto.putEncrypted(
                prefs.edit(),
                KEY_DYNAMIC_NODES_JSON,
                validArr.toString()
            ).apply()
            validArr.length()
        } catch (_: Throwable) {
            0
        }
    }

    /**
     * Memilih node Firebase aktif yang sehat (tidak sedang terkena limit/cooldown).
     */
    fun getActiveFirebaseNode(): FirebaseNodeConfig {
        val pool = getFirebaseNodePool()
        if (pool.size <= 1) return pool.first()

        val now = System.currentTimeMillis()
        val safeIdx = activeNodeIndex % pool.size
        val preferred = pool[safeIdx]
        val cooldownUntil = nodeCooldownUntilMs[preferred.nodeId] ?: 0L
        if (now >= cooldownUntil) {
            return preferred
        }

        // Cari node cadangan yang tidak sedang cooldown
        for (offset in 1 until pool.size) {
            val idx = (safeIdx + offset) % pool.size
            val candidate = pool[idx]
            val candCooldown = nodeCooldownUntilMs[candidate.nodeId] ?: 0L
            if (now >= candCooldown) {
                activeNodeIndex = idx
                prefs.edit().putInt(KEY_ACTIVE_NODE_IDX, idx).apply()
                needsCrossNodeStateHandover = true
                return candidate
            }
        }

        // Jika semua node sedang cooldown, pilih yang waktu cooldown-nya paling cepat selesai
        val best = pool.minByOrNull { nodeCooldownUntilMs[it.nodeId] ?: 0L } ?: preferred
        val bestIdx = pool.indexOf(best).coerceAtLeast(0)
        if (bestIdx != safeIdx) {
            activeNodeIndex = bestIdx
            prefs.edit().putInt(KEY_ACTIVE_NODE_IDX, bestIdx).apply()
            needsCrossNodeStateHandover = true
        }
        return best
    }

    /**
     * Menandai node saat ini terkena limit/gangguan dan otomatis berpindah (Auto-Switch) ke node cadangan.
     */
    private fun handleNodeLimitAndAutoSwitch(failedNode: FirebaseNodeConfig, httpCode: Int): FirebaseNodeConfig? {
        val now = System.currentTimeMillis()
        val cooldownMs = if (httpCode == 429 || httpCode == 403) {
            30 * 60_000L // 30 menit cooldown untuk kuota habis (429/403)
        } else {
            3 * 60_000L // 3 menit cooldown untuk gangguan jaringan/503
        }
        nodeCooldownUntilMs[failedNode.nodeId] = now + cooldownMs

        val pool = getFirebaseNodePool()
        if (pool.size > 1) {
            for (offset in 1 until pool.size) {
                val nextIdx = (activeNodeIndex + offset) % pool.size
                val candidate = pool[nextIdx]
                val candCooldown = nodeCooldownUntilMs[candidate.nodeId] ?: 0L
                if (now >= candCooldown) {
                    activeNodeIndex = nextIdx
                    prefs.edit().putInt(KEY_ACTIVE_NODE_IDX, nextIdx).apply()
                    needsCrossNodeStateHandover = true
                    _isQuotaExceeded.value = false
                    _syncStatusMessage.value = "🔄 Auto-Switch Aktif (${candidate.nodeId})"
                    Log.i(TAG, "⚡ AUTO-SWITCH FIREBASE: Node ${failedNode.nodeId} limit (HTTP $httpCode) -> Beralih instan ke ${candidate.nodeId} (${candidate.projectId})")
                    return candidate
                }
            }
        }
        return null
    }

    /**
     * Memulai pemantauan dan pengiriman detak telemetri adaptif ke Firestore dan direct command listener.
     */
    fun start(scope: CoroutineScope) {
        registerDirectCommandReceiver()
        if (syncJob?.isActive == true) return

        syncJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Layanan Telemetri & Multi-Firebase Auto-Switch Tajasb AKTIF (80% Cloud / 20% Layered Offline).")
            while (isActive) {
                try {
                    sendHeartbeatAndProcessCommands()
                } catch (t: Throwable) {
                    Log.w(TAG, "Siklus telemetri terputus: ${t.message}")
                }
                delay(adaptiveDelayMs)
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
        unregisterDirectCommandReceiver()
    }

    private fun registerDirectCommandReceiver() {
        if (directCommandReceiver != null) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_TRIGGER_SYNC -> triggerImmediateSync()
                    ACTION_EXECUTE_DIRECT_COMMAND -> {
                        val act = intent.getStringExtra("action") ?: return
                        val payload = intent.getStringExtra("payload") ?: ""
                        val sig = intent.getStringExtra("signature") ?: ""
                        val ts = intent.getLongExtra("timestamp", 0L)
                        val licMgr = LicenseManager.getInstance(context)
                        val devId = licMgr.getDeviceId()
                        // KEAMANAN ANTI-BYPASS: Wajib 100% lolos verifikasi tanda tangan ECDSA (flag is_internal dihapus!)
                        if (RemoteCommandValidator.verifySignature(act, devId, ts, sig)) {
                            executeDirectAction(act, payload, "DIRECT_BROADCAST")
                        } else {
                            Log.w(TAG, "🚨 Direct command '$act' ditolak: Signature ECDSA tidak valid.")
                        }
                    }
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(ACTION_TRIGGER_SYNC)
            addAction(ACTION_EXECUTE_DIRECT_COMMAND)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            directCommandReceiver = receiver
        } catch (_: Throwable) {}
    }

    private fun unregisterDirectCommandReceiver() {
        directCommandReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Throwable) {}
            directCommandReceiver = null
        }
    }

    fun triggerImmediateSync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sendHeartbeatAndProcessCommands()
            } catch (_: Throwable) {}
        }
    }

    private fun sendHeartbeatAndProcessCommands() {
        val licenseMgr = LicenseManager.getInstance(context)
        val deviceId = licenseMgr.getDeviceId()
        val repo = VcamRepository.getInstance(context)

        // Verifikasi berlapis 20% Offline Security Gate sebelum sinkronisasi
        licenseMgr.verifyAndEnforceOfflineSecurityGate()

        // 0. Cek apakah ada pembayaran QRIS Tripay tertunda HANYA jika ada order pending lokal
        try {
            val tripayMgr = com.tajasb.tripay.TripayManager.getInstance(context)
            if (tripayMgr.hasPendingTransaction()) {
                tripayMgr.checkPendingTransactionBlocking()
            }
        } catch (_: Throwable) {}

        // 1. Cek Instruksi Remote dari Telegram via GET dengan dukungan Multi-Firebase Auto-Switch
        var activeNode = getActiveFirebaseNode()
        var executedCmdId: String? = null
        var executedResult: String? = null
        var getSucceeded = false

        for (attempt in 0..1) {
            val getEndpoint = "https://firestore.googleapis.com/v1/projects/${activeNode.projectId}/databases/${activeNode.databaseId}/documents/devices/$deviceId?key=${activeNode.apiKey}"
            try {
                val getConn = (URL(getEndpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    useCaches = false
                }
                val getRespCode = getConn.responseCode
                if (getRespCode in 200..299) {
                    val resp = BufferedReader(InputStreamReader(getConn.inputStream, "UTF-8")).use { it.readText() }
                    getConn.disconnect()
                    getSucceeded = true
                    licenseMgr.recordCloudSyncHeartbeat()
                    _isQuotaExceeded.value = false
                    _syncStatusMessage.value = "Terhubung (${activeNode.nodeId})"
                    _lastSyncTime.value = System.currentTimeMillis()
                    adaptiveDelayMs = 240_000L // 4 menit normal (ultra irit kuota)

                    val execInfo = parseAndExecuteCommands(JSONObject(resp), deviceId, licenseMgr, repo)
                    if (execInfo != null) {
                        executedCmdId = execInfo.first
                        executedResult = execInfo.second
                    }
                    break
                } else if (getRespCode == 404) {
                    // Dokumen belum ada di node baru ini -> tandai wajib handover state
                    getConn.disconnect()
                    getSucceeded = true
                    needsCrossNodeStateHandover = true
                    licenseMgr.recordCloudSyncHeartbeat()
                    break
                } else {
                    getConn.disconnect()
                    if (getRespCode == 429 || getRespCode == 403 || getRespCode == 503) {
                        val switchedNode = handleNodeLimitAndAutoSwitch(activeNode, getRespCode)
                        if (switchedNode != null && attempt == 0) {
                            activeNode = switchedNode
                            continue // Coba langsung di node cadangan!
                        }
                        _isQuotaExceeded.value = true
                        _syncStatusMessage.value = "⚠️ Semua Node Limit (Proteksi Offline 20% Aktif)"
                        adaptiveDelayMs = 600_000L // Backoff 10 menit
                        return
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Cek remote command pada ${activeNode.nodeId} gagal (${t.message})")
                val switchedNode = handleNodeLimitAndAutoSwitch(activeNode, -1)
                if (switchedNode != null && attempt == 0) {
                    activeNode = switchedNode
                    continue
                }
            }
            break
        }

        // 2. Kumpulkan Data Telemetri Perangkat
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()
        val availRamMb = (memInfo.availMem / (1024 * 1024)).toInt()
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(0)

        val status = licenseMgr.refreshLicenseStatus()
        val statusLabel = when (status) {
            is LicenseStatus.Active -> {
                if (status.license.isLifetime) "LIFETIME (Permanen)"
                else "AKTIF (${status.license.remainingTimeFormatted})"
            }
            is LicenseStatus.Expired -> "EXPIRED"
            is LicenseStatus.Invalid -> "TERKUNCI: ${status.reason}"
            LicenseStatus.Unlicensed -> "BELUM AKTIVASI (WAJIB DECOY)"
        }

        val decoyCfg = licenseMgr.getDecoyConfig()
        val decoyStatusLabel = if (!decoyCfg.isDecoy || decoyCfg.profile == DecoyProfile.NONE) {
            "🟢 VIP NORMAL (BEBAS DECOY)"
        } else if (decoyCfg.forceContinuousSevere || decoyCfg.freezeAfterLoopCount <= 1) {
            "🥶 FREEZE PUTARAN KE-1"
        } else {
            "🎲 FREEZE DINAMIS (1–${decoyCfg.freezeAfterLoopCount} Putaran)"
        }

        val currentVideo = repo.config.value.currentVideo?.name ?: "STANDBY / SIAP"
        val appVer = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (_: Throwable) {
            "1.0"
        }

        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val isGateResetPending = licenseMgr.isPendingCloudGateReset()
        val stateVerMs = licenseMgr.getStateVersionMs().let { if (it > 0L) it else System.currentTimeMillis() }

        // ULTRA HEMAT KUOTA (90% Penghematan Write):
        // Fluktuasi baterai & RAM dikeluarkan dari hash agar tidak memicu PATCH terus-menerus!
        // PATCH hanya dikirim jika ada perubahan status penting, handover antar node, atau detak 15 menit.
        val currentHash = (
            deviceId +
            statusLabel +
            decoyStatusLabel +
            (!decoyCfg.isDecoy).toString() +
            decoyCfg.freezeAfterLoopCount.toString() +
            currentVideo +
            activeNode.nodeId
        ).hashCode()
        val nowMs = System.currentTimeMillis()
        if (!isGateResetPending &&
            !needsCrossNodeStateHandover &&
            currentHash == lastSentDataHash &&
            executedCmdId == null &&
            (nowMs - lastPatchTimeMs < 900_000L) // Heartbeat tulis cukup 15 menit sekali jika tidak ada perubahan state
        ) {
            Log.d(TAG, "State utama tidak berubah (${activeNode.nodeId}), lewati PATCH untuk penghematan kuota 90%.")
            return
        }

        val lastCmdToSync = executedCmdId ?: prefs.getString(KEY_LAST_EXEC_CMD, null)

        val fieldsObj = JSONObject().apply {
            put("deviceId", JSONObject().put("stringValue", deviceId))
            put("model", JSONObject().put("stringValue", "${Build.MANUFACTURER} ${Build.MODEL}"))
            put("androidVersion", JSONObject().put("stringValue", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
            put("appVersion", JSONObject().put("stringValue", appVer))
            put("lastSeen", JSONObject().put("stringValue", isoDate))
            put("lastSeenMs", JSONObject().put("integerValue", nowMs.toString()))
            put("isOnline", JSONObject().put("booleanValue", true))
            put("batteryLevel", JSONObject().put("integerValue", batteryPct.toString()))
            put("ramUsageMb", JSONObject().put("integerValue", usedRamMb.toString()))
            put("totalRamMb", JSONObject().put("integerValue", totalRamMb.toString()))
            put("licenseStatus", JSONObject().put("stringValue", statusLabel))
            put("decoyStatus", JSONObject().put("stringValue", decoyStatusLabel))
            put("decoyBypass", JSONObject().put("booleanValue", !decoyCfg.isDecoy))
            put("freezeLoops", JSONObject().put("integerValue", decoyCfg.freezeAfterLoopCount.toString()))
            put("freezeLoopCount", JSONObject().put("integerValue", decoyCfg.freezeAfterLoopCount.toString()))
            put("stateVersionMs", JSONObject().put("integerValue", stateVerMs.toString()))
            put("activeFirebaseNode", JSONObject().put("stringValue", activeNode.nodeId))
            put("currentVideo", JSONObject().put("stringValue", currentVideo))
            if (!lastCmdToSync.isNullOrBlank()) {
                put("lastExecutedCommandId", JSONObject().put("stringValue", lastCmdToSync))
            }
            if (executedCmdId != null) {
                put("commandResult", JSONObject().put("stringValue", executedResult ?: "SUCCESS"))
            }
        }

        val maskFields = mutableListOf(
            "deviceId", "model", "androidVersion", "appVersion", "lastSeen", "lastSeenMs",
            "isOnline", "batteryLevel", "ramUsageMb", "totalRamMb", "licenseStatus",
            "decoyStatus", "decoyBypass", "freezeLoops", "freezeLoopCount",
            "stateVersionMs", "activeFirebaseNode", "currentVideo"
        )
        if (!lastCmdToSync.isNullOrBlank()) {
            maskFields.add("lastExecutedCommandId")
        }
        if (executedCmdId != null) {
            maskFields.add("commandResult")
        }

        val maskParam = maskFields.joinToString("&") { "updateMask.fieldPaths=$it" }
        val patchEndpoint = "https://firestore.googleapis.com/v1/projects/${activeNode.projectId}/databases/${activeNode.databaseId}/documents/devices/$deviceId?$maskParam&key=${activeNode.apiKey}"
        val patchUrl = URL(patchEndpoint)

        val requestBody = JSONObject().put("fields", fieldsObj)

        val conn = (patchUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("X-HTTP-Method-Override", "PATCH")
            connectTimeout = 8000
            readTimeout = 8000
            doOutput = true
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use {
            it.write(requestBody.toString())
            it.flush()
        }

        val code = conn.responseCode
        if (code in 200..299) {
            conn.disconnect()
            if (isGateResetPending) {
                licenseMgr.clearPendingCloudGateReset()
            }
            needsCrossNodeStateHandover = false
            licenseMgr.recordCloudSyncHeartbeat()
            lastSentDataHash = currentHash
            lastPatchTimeMs = nowMs
            _isQuotaExceeded.value = false
            _syncStatusMessage.value = "Terhubung (${activeNode.nodeId})"
            _lastSyncTime.value = nowMs
            Log.d(TAG, "Sinkronisasi state berhasil ke ${activeNode.nodeId} (stateVersionMs=$stateVerMs).")
        } else {
            conn.disconnect()
            if (code == 429 || code == 403 || code == 503) {
                val switched = handleNodeLimitAndAutoSwitch(activeNode, code)
                if (switched == null) {
                    _isQuotaExceeded.value = true
                    _syncStatusMessage.value = "⚠️ Kuota Limit (Proteksi Offline 20% Aktif)"
                    adaptiveDelayMs = 600_000L
                }
            }
            Log.w(TAG, "Sinkronisasi telemetri pada ${activeNode.nodeId} gagal dengan kode HTTP $code")
        }
    }

    private fun broadcastDecoyStateToAllHooks(
        isDecoy: Boolean,
        profile: DecoyProfile,
        freezeLoops: Int,
        smoothM: Int,
        severeM: Int,
        forceSevere: Boolean
    ) {
        if (!isDecoy || profile == DecoyProfile.NONE) {
            com.tajasb.engine.DecoyEngine.releaseHostAndAppFreeze(sendCrossProcessBroadcast = true)
        }
        val targetPackages = listOf(
            context.packageName,
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.shopee.id",
            "com.tokopedia.tkpd",
            "com.instagram.android",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "us.zoom.videomeetings"
        )
        val isMochiBlind = false
        com.tajasb.engine.DecoyEngine.updateDecoyConfig(
            com.tajasb.model.DecoyConfig(
                isDecoy = isDecoy,
                profile = profile,
                freezeAfterLoopCount = freezeLoops,
                smoothMinutes = smoothM,
                severeMinutes = severeM,
                forceContinuousSevere = forceSevere,
                isMochiBlind = isMochiBlind
            )
        )
        val authTs = System.currentTimeMillis()
        val authSig = LicenseManager.computeDecoyBroadcastAuthToken(
            isDecoy = isDecoy,
            profileName = profile.name,
            freezeLoops = freezeLoops,
            timestampMs = authTs
        )
        for (pkg in targetPackages) {
            try {
                val pkgIntent = Intent("com.tajasb.action.REMOTE_DECOY").apply {
                    setPackage(pkg)
                    putExtra("extra_is_decoy", isDecoy)
                    putExtra("extra_decoy_profile", profile.name)
                    putExtra("extra_mochi_blind", isMochiBlind)
                    putExtra("extra_freeze_loop_count", freezeLoops)
                    putExtra("extra_smooth_minutes", smoothM)
                    putExtra("extra_severe_minutes", severeM)
                    putExtra("extra_force_severe", forceSevere)
                    putExtra("extra_decoy_min_minutes", severeM)
                    putExtra("extra_decoy_max_minutes", smoothM)
                    putExtra("extra_auth_ts", authTs)
                    putExtra("extra_auth_sig", authSig)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(pkgIntent)
            } catch (_: Throwable) {}
        }
        val decoyIntent = Intent("com.tajasb.action.REMOTE_DECOY").apply {
            putExtra("extra_is_decoy", isDecoy)
            putExtra("extra_decoy_profile", profile.name)
            putExtra("extra_mochi_blind", isMochiBlind)
            putExtra("extra_freeze_loop_count", freezeLoops)
            putExtra("extra_smooth_minutes", smoothM)
            putExtra("extra_severe_minutes", severeM)
            putExtra("extra_force_severe", forceSevere)
            putExtra("extra_decoy_min_minutes", severeM)
            putExtra("extra_decoy_max_minutes", smoothM)
            putExtra("extra_auth_ts", authTs)
            putExtra("extra_auth_sig", authSig)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        context.sendBroadcast(decoyIntent)
    }

    private fun parseAndExecuteCommands(docJson: JSONObject, deviceId: String, licenseMgr: LicenseManager, repo: VcamRepository): Pair<String, String>? {
        val fields = docJson.optJSONObject("fields") ?: return null

        // Jika baru saja instal/update APK (Gerbang Awal Wajib Decoy 100%), abaikan state decoyBypass lama & command lama di cloud
        if (licenseMgr.isPendingCloudGateReset()) {
            val staleCmdId = fields.optJSONObject("activeCommand")
                ?.optJSONObject("mapValue")
                ?.optJSONObject("fields")
                ?.optJSONObject("commandId")
                ?.optString("stringValue")
            if (!staleCmdId.isNullOrBlank()) {
                prefs.edit().putString(KEY_LAST_EXEC_CMD, staleCmdId).apply()
            }
            val cfg = licenseMgr.getDecoyConfig()
            broadcastDecoyStateToAllHooks(cfg.isDecoy, cfg.profile, cfg.freezeAfterLoopCount, cfg.smoothMinutes, cfg.severeMinutes, cfg.forceContinuousSevere)
            return null
        }

        // Sinkronisasi Lintas Node Firebase (Cross-Node State Resolution via stateVersionMs)
        try {
            val cloudStateVer = fields.optJSONObject("stateVersionMs")?.optString("integerValue")?.toLongOrNull() ?: 0L
            val localStateVer = licenseMgr.getStateVersionMs()

            // Jika node cadangan yang baru aktif memiliki versi state lebih lama dari HP ini,
            // jangan timpa state lokal; sebaliknya jadwalkan Handover PATCH ke node cadangan ini!
            if (cloudStateVer in 1 until localStateVer) {
                needsCrossNodeStateHandover = true
            } else {
                val effVer = if (cloudStateVer > 0L) cloudStateVer else System.currentTimeMillis()
                val cloudFreezeLoops = fields.optJSONObject("freezeLoops")?.optString("integerValue")?.toIntOrNull()
                    ?: fields.optJSONObject("freezeLoopCount")?.optString("integerValue")?.toIntOrNull()
                if (cloudFreezeLoops != null && cloudFreezeLoops in 1..50 && cloudFreezeLoops != licenseMgr.getCustomFreezeLoopCount()) {
                    licenseMgr.setCustomFreezeLoopCount(cloudFreezeLoops, effVer)
                    val cfg = licenseMgr.getDecoyConfig()
                    broadcastDecoyStateToAllHooks(cfg.isDecoy, cfg.profile, cfg.freezeAfterLoopCount, cfg.smoothMinutes, cfg.severeMinutes, cfg.forceContinuousSevere)
                }
                val cloudSmooth = fields.optJSONObject("smoothMinutes")?.optString("integerValue")?.toIntOrNull()
                val cloudSevere = fields.optJSONObject("severeMinutes")?.optString("integerValue")?.toIntOrNull()
                if (cloudSmooth != null && cloudSevere != null) {
                    licenseMgr.setCustomDecoyCycle(cloudSmooth, cloudSevere)
                }
                val cloudBypassObj = fields.optJSONObject("decoyBypass")
                if (cloudBypassObj != null && cloudBypassObj.has("booleanValue")) {
                    val cloudBypass = cloudBypassObj.optBoolean("booleanValue", false)
                    if (cloudBypass != licenseMgr.isVipDecoyBypassed()) {
                        if (!cloudBypass) {
                            // Pencabutan VIP / penguncian kembali ke Decoy diizinkan kapan saja (Fail-Safe)
                            licenseMgr.setVipDecoyBypassed(false, effVer)
                            val cfg = licenseMgr.getDecoyConfig()
                            broadcastDecoyStateToAllHooks(cfg.isDecoy, cfg.profile, cfg.freezeAfterLoopCount, cfg.smoothMinutes, cfg.severeMinutes, cfg.forceContinuousSevere)
                        } else {
                            // KEAMANAN ANTI-SPOOFING FIRESTORE:
                            // Jangan izinkan pemberian VIP dari field boolean mentah kecuali disertai tanda tangan ECDSA Owner
                            // atau perangkat sudah memiliki token TJ-VIP- resmi!
                            val vipSig = fields.optJSONObject("vipSignature")?.optString("stringValue") ?: ""
                            val vipSigTs = fields.optJSONObject("vipSignatureTs")?.optString("integerValue")?.toLongOrNull() ?: (effVer / 1000L)
                            val hasValidVipToken = licenseMgr.getActiveToken()?.startsWith(LicenseManager.VIP_LICENSE_KEY_PREFIX, ignoreCase = true) == true
                            val isVipSigValid = vipSig.isNotBlank() && RemoteCommandValidator.verifySignature("VIP_BYPASS", deviceId, vipSigTs, vipSig)
                            if (hasValidVipToken || isVipSigValid) {
                                licenseMgr.setVipDecoyBypassed(true, effVer)
                                val cfg = licenseMgr.getDecoyConfig()
                                broadcastDecoyStateToAllHooks(cfg.isDecoy, cfg.profile, cfg.freezeAfterLoopCount, cfg.smoothMinutes, cfg.severeMinutes, cfg.forceContinuousSevere)
                            } else {
                                Log.w(TAG, "🚨 Menolak decoyBypass=true tanpa tanda tangan ECDSA di dokumen Firestore (proteksi anti-spoofing PATCH).")
                                needsCrossNodeStateHandover = true
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        val cmdObj = fields.optJSONObject("activeCommand")?.optJSONObject("mapValue")?.optJSONObject("fields") ?: return null

        val cmdId = cmdObj.optJSONObject("commandId")?.optString("stringValue") ?: return null
        val action = cmdObj.optJSONObject("action")?.optString("stringValue") ?: return null
        val payload = cmdObj.optJSONObject("payload")?.optString("stringValue") ?: ""
        val tsObj = cmdObj.optJSONObject("timestamp")
        val timestamp = tsObj?.optString("integerValue")?.toLongOrNull() ?: tsObj?.optLong("integerValue", 0L) ?: 0L
        val sig = cmdObj.optJSONObject("signature")?.optString("stringValue") ?: ""

        if (cmdId.isBlank() || action.isBlank()) return null

        val lastExecuted = prefs.getString(KEY_LAST_EXEC_CMD, "")
        if (cmdId == lastExecuted) {
            return null
        }

        Log.i(TAG, "Menerima perintah remote dari Telegram: [$action] Payload: '$payload' ID: $cmdId")

        // 🛡️ VERIFIKASI KEAMANAN TINGKAT TINGGI (ANTI-HACK):
        // Wajib lolos validasi tanda tangan kriptografi ECDSA SHA256
        val isAuthentic = RemoteCommandValidator.verifySignature(action, deviceId, timestamp, sig)
        if (!isAuthentic) {
            Log.e(TAG, "🚨 PERINGATAN KEAMANAN: Perintah '$action' DITOLAK karena tanda tangan digital tidak valid!")
            return null
        }

        // Tandai perintah telah dieksekusi agar tidak dijalankan berulang saat Auto-Switch node
        prefs.edit().putString(KEY_LAST_EXEC_CMD, cmdId).apply()

        val result = executeDirectAction(action, payload, "FIRESTORE_REMOTE")
        return Pair(cmdId, result)
    }

    fun executeDirectAction(action: String, payload: String, source: String = "DIRECT"): String {
        val licenseMgr = LicenseManager.getInstance(context)
        val repo = VcamRepository.getInstance(context)
        Log.i(TAG, "[$source] Mengeksekusi aksi: $action (Payload: $payload)")

        when (action.uppercase()) {
            "SYNC_NODES" -> {
                val count = saveDynamicFirebaseNodes(payload)
                needsCrossNodeStateHandover = true
                Log.i(TAG, "Pool Node Firebase diperbarui secara remote: $count node cadangan tersimpan.")
                return "NODES_SYNCED_$count"
            }

            "LOCK" -> {
                Log.w(TAG, "Perintah LOCK dieksekusi ($source): Menutup akses APK.")
                licenseMgr.applyLocalBlacklist(payload.ifEmpty { "Akses ditangguhkan oleh Administrator via Telegram" })
                context.sendBroadcast(Intent("com.tajasb.action.FORCE_STOP"))
                context.sendBroadcast(Intent("com.tajasb.action.STOP_PLAYBACK"))
                context.sendBroadcast(Intent(ACTION_REMOTE_KILL))
                mainHandler.post {
                    Toast.makeText(context, "⛔ Perangkat telah dikunci oleh Administrator.", Toast.LENGTH_LONG).show()
                }
                return "LOCKED"
            }

            "UNLOCK" -> {
                Log.i(TAG, "Perintah UNLOCK dieksekusi ($source): Membuka kunci perangkat.")
                licenseMgr.removeLocalBlacklist()
                context.sendBroadcast(Intent("com.tajasb.action.UPDATE_CONFIG"))
                mainHandler.post {
                    Toast.makeText(context, "✅ Kunci perangkat telah dibuka oleh Administrator.", Toast.LENGTH_LONG).show()
                }
                return "UNLOCKED"
            }

            "ACTIVATE" -> {
                Log.i(TAG, "Perintah ACTIVATE dieksekusi ($source): Memproses lisensi 1-Click secara remote.")
                val actResult = licenseMgr.activate("TG_REMOTE", payload)
                when (actResult) {
                    is ActivationResult.Success -> {
                        val cfg = licenseMgr.getDecoyConfig()
                        broadcastDecoyStateToAllHooks(cfg.isDecoy, cfg.profile, cfg.freezeAfterLoopCount, cfg.smoothMinutes, cfg.severeMinutes, cfg.forceContinuousSevere)
                        context.sendBroadcast(Intent("com.tajasb.action.UPDATE_CONFIG"))
                        mainHandler.post {
                            Toast.makeText(context, "🎉 Tajasb PRO Berhasil Diaktivasi!\nStatus: ${actResult.message}", Toast.LENGTH_LONG).show()
                        }
                        return "ACTIVATED"
                    }
                    is ActivationResult.Error -> {
                        Log.w(TAG, "Aktivasi remote gagal: ${actResult.message}")
                        mainHandler.post {
                            Toast.makeText(context, "⚠️ Aktivasi gagal: ${actResult.message}", Toast.LENGTH_LONG).show()
                        }
                        return "ACTIVATION_FAILED"
                    }
                }
            }

            "NEXT_VIDEO" -> {
                Log.i(TAG, "Perintah NEXT_VIDEO dieksekusi ($source): Mengganti video kamera.")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        repo.nextVideo(broadcastToHook = true)
                    } catch (t: Throwable) {
                        Log.w(TAG, "repo.nextVideo error: ${t.message}")
                    }
                }
                context.sendBroadcast(Intent("com.tajasb.action.NEXT_VIDEO"))
                if (licenseMgr.isVipDecoyBypassed()) {
                    mainHandler.post {
                        Toast.makeText(context, "📹 Video kamera diganti ke video berikutnya.", Toast.LENGTH_SHORT).show()
                    }
                }
                return "NEXT_VIDEO_TRIGGERED"
            }

            "TEST_FREEZE" -> {
                val sec = payload.toIntOrNull()?.coerceIn(1, 30) ?: 4
                Log.i(TAG, "Perintah TEST_FREEZE dieksekusi ($source) selama $sec detik")
                com.tajasb.engine.DecoyEngine.triggerTestFreeze(sec) {}
                return "TEST_FREEZE_TRIGGERED"
            }

            "VIP_BYPASS" -> {
                val nowVer = System.currentTimeMillis()
                licenseMgr.setVipDecoyBypassed(true, nowVer)
                val cfg = licenseMgr.getDecoyConfig()
                broadcastDecoyStateToAllHooks(cfg.isDecoy, cfg.profile, cfg.freezeAfterLoopCount, cfg.smoothMinutes, cfg.severeMinutes, cfg.forceContinuousSevere)
                context.sendBroadcast(Intent("com.tajasb.action.UPDATE_CONFIG"))
                return "VIP_BYPASS_ACTIVE"
            }

            "RESET_DECOY" -> {
                val nowVer = System.currentTimeMillis()
                licenseMgr.setVipDecoyBypassed(false, nowVer)
                val cfg = licenseMgr.getDecoyConfig()
                broadcastDecoyStateToAllHooks(cfg.isDecoy, cfg.profile, cfg.freezeAfterLoopCount, cfg.smoothMinutes, cfg.severeMinutes, cfg.forceContinuousSevere)
                context.sendBroadcast(Intent("com.tajasb.action.UPDATE_CONFIG"))
                return "DECOY_RESET"
            }

            "DECOY" -> {
                Log.i(TAG, "Perintah DECOY dieksekusi ($source): Mode $payload")
                val parts = payload.uppercase().trim().split(":")
                val mainMode = parts.getOrNull(0) ?: "RANDOM"
                val nowVer = System.currentTimeMillis()

                if (mainMode == "LOOP" || mainMode == "FREEZE" || mainMode == "CYCLE" || mainMode == "DYNAMIC") {
                    val loops = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 50) ?: 4
                    val severeM = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 120) ?: licenseMgr.getCustomSevereMinutes()
                    licenseMgr.setCustomFreezeLoopCount(loops, nowVer)
                    licenseMgr.setLocalDecoyOverride(true, DecoyProfile.RANDOM_GLITCH, severeM, loops, nowVer)
                    val cfg = licenseMgr.getDecoyConfig()
                    broadcastDecoyStateToAllHooks(cfg.isDecoy, cfg.profile, cfg.freezeAfterLoopCount, cfg.smoothMinutes, cfg.severeMinutes, cfg.forceContinuousSevere)
                    context.sendBroadcast(Intent("com.tajasb.action.UPDATE_CONFIG"))
                } else {
                    val loops = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 50) ?: licenseMgr.getCustomFreezeLoopCount()
                    val severeM = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 120) ?: licenseMgr.getCustomSevereMinutes()

                    val profile = when (mainMode) {
                        "LAG", "LAG_ONLY" -> DecoyProfile.LAG_ONLY
                        "AUDIO", "AUDIO_ONLY" -> DecoyProfile.AUDIO_ONLY
                        "GPU", "GPU_CORRUPT", "GREEN", "FLICKER" -> DecoyProfile.GPU_CORRUPT
                        "FLIP", "FLIP_ZOOM" -> DecoyProfile.FLIP_ZOOM
                        "MOCHI", "BLIND", "MOCHI_BLIND" -> DecoyProfile.MOCHI_BLIND
                        "RANDOM", "RANDOM_GLITCH", "ALL" -> DecoyProfile.RANDOM_GLITCH
                        "NONE", "OFF", "NORMAL", "BYPASS", "VIP" -> DecoyProfile.NONE
                        else -> DecoyProfile.RANDOM_GLITCH
                    }
                    val isDecoy = profile != DecoyProfile.NONE
                    if (isDecoy) {
                        licenseMgr.setCustomFreezeLoopCount(loops, nowVer)
                    }
                    licenseMgr.setLocalDecoyOverride(isDecoy, profile, severeM, loops, nowVer)
                    val cfg = licenseMgr.getDecoyConfig()
                    broadcastDecoyStateToAllHooks(cfg.isDecoy, cfg.profile, cfg.freezeAfterLoopCount, cfg.smoothMinutes, cfg.severeMinutes, cfg.forceContinuousSevere)
                    context.sendBroadcast(Intent("com.tajasb.action.UPDATE_CONFIG"))
                }
                return "DECOY_CONFIGURED"
            }

            "MESSAGE" -> {
                Log.i(TAG, "Perintah MESSAGE dieksekusi ($source): Menampilkan pesan di layar.")
                val intent = Intent(ACTION_REMOTE_MESSAGE).apply {
                    putExtra("message", payload)
                }
                context.sendBroadcast(intent)
                mainHandler.post {
                    Toast.makeText(context, "📢 Pesan Admin: $payload", Toast.LENGTH_LONG).show()
                }
                return "MESSAGE_SHOWN"
            }

            "WIPE" -> {
                Log.w(TAG, "Perintah WIPE dieksekusi ($source): Menghapus lisensi dan sesi.")
                licenseMgr.deactivate()
                context.sendBroadcast(Intent("com.tajasb.action.FORCE_STOP"))
                context.sendBroadcast(Intent(ACTION_REMOTE_KILL))
                return "WIPED"
            }
        }
        return "UNKNOWN_ACTION"
    }
}
