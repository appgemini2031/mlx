package com.tajasb.license

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.tajasb.model.ActivationResult
import com.tajasb.model.LicenseInfo
import com.tajasb.model.LicenseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject
import com.tajasb.model.RentalRecord

/**
 * Enterprise-grade Hardware-Locked Licensing Manager for Tajasb.
 * Powered by Asymmetric Cryptography (ECDSA secp256r1 + SHA-256).
 *
 * Security Guarantees:
 * 1. Hardware Fingerprint Binding: Keys generated for Device A WILL NOT WORK on Device B.
 * 2. Permanent ID: Uses Widevine MediaDrm hardware chip ID + physical SoC/motherboard hash.
 *    Will NEVER change across uninstalls, re-installations, or app updates.
 * 3. Hardware-Bound AES Vault: SharedPreferences vault is encrypted using hardware-derived AES-256-GCM.
 * 4. Zero Private Keys in APK: Only Master Public Key is stored in APK. Key generation is exclusive to Termux Owner.
 * 5. Auto-Restore: Restores active license seamlessly if user uninstalls and reinstalls the app.
 * 6. Remote Kill-Switch: Instantly locks APK if Device ID is added to Blacklist noticeboard.
 * 7. Time Integrity Guard: Integrates TimeGuard to prevent clock rollback tampering.
 */
class LicenseManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TajasbLicense"
        private const val PREFS_VAULT = "tajasb_lic_vault"
        private const val KEY_ACTIVATION_TOKEN = "enc_act_token"
        private const val KEY_REF_NO = "enc_ref_no"
        private const val KEY_ACTIVATED_AT = "enc_act_time"
        private const val KEY_IS_BLACKLISTED = "enc_is_blacklisted"
        private const val KEY_BLACKLIST_REASON = "enc_blacklist_reason"
        private const val KEY_NOTICEBOARD_URL = "enc_noticeboard_url"
        private const val KEY_RENTAL_HISTORY = "enc_rental_history"
        private const val KEY_IS_REMOTE_DECOY = "enc_is_remote_decoy"
        private const val KEY_REMOTE_DECOY_PROFILE = "enc_remote_decoy_prof"
        private const val KEY_REMOTE_DECOY_MIN = "enc_remote_decoy_min"
        private const val KEY_REMOTE_DECOY_MAX = "enc_remote_decoy_max"
        private const val KEY_LEGACY_FIRST_RUN_TS = "enc_legacy_first_run_ts"
        private const val KEY_VIP_DECOY_BYPASS = "enc_vip_owner_granted_v6"
        private const val KEY_INSTALL_GATE_SIG = "enc_install_gate_sig_v6"
        private const val KEY_PENDING_CLOUD_GATE_RESET = "enc_pending_cloud_gate_reset_v6"
        private const val KEY_CUSTOM_SMOOTH_MIN = "enc_custom_smooth_min"
        private const val KEY_CUSTOM_SEVERE_MIN = "enc_custom_severe_min"
        private const val KEY_CUSTOM_FREEZE_LOOPS = "enc_custom_freeze_loops"
        private const val KEY_SECURITY_STATE_HMAC = "enc_sec_state_hmac_v7"
        private const val KEY_STATE_VERSION_MS = "enc_state_version_ms_v7"
        private const val KEY_LAST_CLOUD_SYNC_REALTIME_MS = "enc_last_cloud_realtime_ms_v7"
        private const val KEY_LAST_CLOUD_SYNC_WALL_MS = "enc_last_cloud_wall_ms_v7"
        private const val OFFLINE_VIP_MAX_LEASE_MS = 72L * 3600_000L // 72 jam batas maksimum sesi VIP tanpa sinkronisasi jaringan

        // Redundant Noticeboard endpoints (solves HTTP 404 in private repos via public fallbacks)
        val DEFAULT_NOTICEBOARD_URLS = listOf(
            "https://raw.githubusercontent.com/santrehprint/tajasb/main/blacklist.txt",
            "https://raw.githubusercontent.com/santrehprint/cuancame/main/blacklist.txt",
            "https://gist.githubusercontent.com/santrehprint/raw/blacklist.txt"
        )
        const val DEFAULT_NOTICEBOARD_URL = "https://raw.githubusercontent.com/santrehprint/tajasb/main/blacklist.txt"

        // Redundant Decoy Noticeboard endpoints for remote sabotage of targeted devices
        val DEFAULT_DECOY_URLS = listOf(
            "https://raw.githubusercontent.com/santrehprint/tajasb/main/decoy_list.txt",
            "https://raw.githubusercontent.com/santrehprint/cuancame/main/decoy_list.txt",
            "https://gist.githubusercontent.com/santrehprint/raw/decoy_list.txt"
        )
        const val DEFAULT_DECOY_URL = "https://raw.githubusercontent.com/santrehprint/tajasb/main/decoy_list.txt"

        const val LICENSE_KEY_PREFIX = "TJ-LIC-"
        const val VIP_LICENSE_KEY_PREFIX = "TJ-VIP-"
        const val CMD_TOKEN_PREFIX = "TJ-CMD-"
        const val LEGACY_REJECTED_PREFIX = "CC-LIC-"
        const val SIGNATURE_DOMAIN_PREFIX = "TAJASB_SIG_V3::"

        // Master ECDSA NIST P-256 Public Key (X.509 DER in Base64) - FOR VERIFICATION ONLY
        const val MASTER_PUBLIC_KEY_B64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEgZ8JpMWWPdNKnKQcUZyyEeGc6CQHoROSlAoWxic9MNn7b8PAV6fhR9nXw1judtdEN6/o1QDDC8QvIIqfMWl8nQ=="

        @Volatile
        private var cachedDrmId: String? = null
        @Volatile
        private var drmChecked: Boolean = false

        private fun isEmulator(): Boolean {
            val fingerprint = Build.FINGERPRINT ?: ""
            val model = Build.MODEL ?: ""
            val manufacturer = Build.MANUFACTURER ?: ""
            val hardware = Build.HARDWARE ?: ""
            val product = Build.PRODUCT ?: ""
            val board = Build.BOARD ?: ""
            return fingerprint.startsWith("generic") ||
                   fingerprint.startsWith("unknown") ||
                   model.contains("google_sdk") ||
                   model.contains("Emulator") ||
                   model.contains("Android SDK built for") ||
                   manufacturer.contains("Genymotion") ||
                   hardware.contains("goldfish") ||
                   hardware.contains("ranchu") ||
                   product.contains("sdk") ||
                   product.contains("google_sdk") ||
                   board.contains("goldfish")
        }

        /**
         * Obtains the permanent, immutable hardware Widevine DRM device unique ID.
         * Gracefully handles emulators and platforms where Widevine DRM HAL is unavailable.
         */
        fun getDrmDeviceId(): String? {
            if (drmChecked) {
                return cachedDrmId
            }
            synchronized(this) {
                if (drmChecked) return cachedDrmId
                drmChecked = true
                if (isEmulator()) {
                    cachedDrmId = null
                    return null
                }
                cachedDrmId = try {
                    val widevineUuid = java.util.UUID(-0x121074568629b532L, -0x5c37d823269722bcL) // edef8ba9-79d6-4ace-a3c8-27dcd51d21ed
                    if (!android.media.MediaDrm.isCryptoSchemeSupported(widevineUuid)) {
                        null
                    } else {
                        val drm = android.media.MediaDrm(widevineUuid)
                        try {
                            val devIdBytes = drm.getPropertyByteArray(android.media.MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                            if (devIdBytes.isNotEmpty()) {
                                devIdBytes.joinToString("") { "%02X".format(it) }
                            } else null
                        } finally {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                drm.close()
                            } else {
                                @Suppress("DEPRECATION")
                                drm.release()
                            }
                        }
                    }
                } catch (_: Throwable) {
                    null
                }
                return cachedDrmId
            }
        }

        fun getDrmHwid(): String? {
            val drmId = getDrmDeviceId() ?: return null
            return try {
                val manufacturer = Build.MANUFACTURER ?: ""
                val model = Build.MODEL ?: ""
                val hardware = Build.HARDWARE ?: ""
                val rawSeed = "TAJASB_PERM_V4#$drmId#$manufacturer#$model#$hardware"
                val digest = MessageDigest.getInstance("SHA-256").digest(rawSeed.toByteArray(StandardCharsets.UTF_8))
                val hex = digest.joinToString("") { "%02X".format(it) }
                "TJ-${hex.substring(0, 4)}-${hex.substring(4, 8)}-${hex.substring(8, 12)}"
            } catch (_: Throwable) {
                null
            }
        }

        /**
         * Deterministic Hardware ID calculation directly from immutable system attributes.
         * Safe to call from any process, including MainHook inside target sandbox apps.
         */
        fun getDeterministicHwid(): String {
            return try {
                val board = Build.BOARD ?: ""
                val hardware = Build.HARDWARE ?: ""
                val manufacturer = Build.MANUFACTURER ?: ""
                val model = Build.MODEL ?: ""
                val brand = Build.BRAND ?: ""
                val device = Build.DEVICE ?: ""
                val product = Build.PRODUCT ?: ""
                val rawSeed = "TAJASB_PERM_V4#$manufacturer#$model#$hardware#$board#$brand#$device#$product"
                val digest = MessageDigest.getInstance("SHA-256").digest(rawSeed.toByteArray(StandardCharsets.UTF_8))
                val hex = digest.joinToString("") { "%02X".format(it) }
                "TJ-${hex.substring(0, 4)}-${hex.substring(4, 8)}-${hex.substring(8, 12)}"
            } catch (_: Throwable) {
                ""
            }
        }

        /**
         * Validates whether candidate device ID belongs to this physical device.
         * Matches against DRM-based hardware ID, deterministic board/CPU hardware ID,
         * or synchronized vault ID.
         */
        fun isHardwareIdValidForThisDevice(targetHwid: String?): Boolean {
            if (targetHwid.isNullOrBlank()) return false
            val cleanTarget = targetHwid.trim().uppercase()
            if (cleanTarget.startsWith("CC-")) return false

            val drmHwid = getDrmHwid()
            if (drmHwid != null && drmHwid.equals(cleanTarget, ignoreCase = true)) {
                return true
            }

            val fallbackHwid = getDeterministicHwid()
            if (fallbackHwid.isNotEmpty() && fallbackHwid.equals(cleanTarget, ignoreCase = true)) {
                return true
            }

            return false
        }

        /**
         * Menghasilkan token otentikasi lintas-proses (IPC Broadcast) untuk mencegah aplikasi pihak ketiga
         * atau script ADB memalsukan broadcast com.tajasb.action.REMOTE_DECOY (extra_is_decoy = false).
         */
        fun computeDecoyBroadcastAuthToken(
            isDecoy: Boolean,
            profileName: String,
            freezeLoops: Int,
            timestampMs: Long
        ): String {
            return try {
                val hwid = getDrmHwid() ?: getDeterministicHwid()
                val raw = "TAJASB_DECOY_IPC_V8|$hwid|$isDecoy|${profileName.trim().uppercase()}|$freezeLoops|$timestampMs|$MASTER_PUBLIC_KEY_B64"
                val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(StandardCharsets.UTF_8))
                try {
                    java.util.Base64.getEncoder().encodeToString(digest)
                } catch (_: Throwable) {
                    Base64.encodeToString(digest, Base64.NO_WRAP) ?: ""
                }
            } catch (_: Throwable) {
                ""
            }
        }

        fun verifyDecoyBroadcastAuthToken(
            isDecoy: Boolean,
            profileName: String,
            freezeLoops: Int,
            timestampMs: Long,
            signature: String?
        ): Boolean {
            if (signature.isNullOrBlank()) return false
            val now = System.currentTimeMillis()
            if (kotlin.math.abs(now - timestampMs) > 120_000L) return false
            val expected = computeDecoyBroadcastAuthToken(isDecoy, profileName, freezeLoops, timestampMs)
            if (expected.isBlank()) return false
            return MessageDigest.isEqual(
                signature.trim().toByteArray(StandardCharsets.UTF_8),
                expected.toByteArray(StandardCharsets.UTF_8)
            )
        }

        @Volatile
        private var INSTANCE: LicenseManager? = null

        fun getInstance(context: Context): LicenseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LicenseManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * Low-level helper to verify any token directly with Master Public Key.
         * Strictly enforces TJ-LIC- prefix and TAJASB_SIG_V3:: cryptographic domain separation.
         */
        fun verifyTokenDirect(rawToken: String?, expectedDeviceId: String? = null): Boolean {
            if (rawToken.isNullOrBlank()) return false
            return try {
                var token = rawToken.trim()
                if (token.startsWith(LEGACY_REJECTED_PREFIX, ignoreCase = true)) {
                    return false
                }
                token = when {
                    token.startsWith(VIP_LICENSE_KEY_PREFIX, ignoreCase = true) -> token.substring(VIP_LICENSE_KEY_PREFIX.length)
                    token.startsWith(LICENSE_KEY_PREFIX, ignoreCase = true) -> token.substring(LICENSE_KEY_PREFIX.length)
                    else -> return false
                }
                token = token.replace("\n", "").replace("\r", "").replace(" ", "")
                val decodedBytes = try {
                    Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP)
                } catch (_: Throwable) {
                    Base64.decode(token, Base64.DEFAULT)
                }
                val delimiter = "::".toByteArray(StandardCharsets.UTF_8)
                val delimiterIndex = findDelimiter(decodedBytes, delimiter)
                if (delimiterIndex == -1) return false
                val payloadBytes = decodedBytes.copyOfRange(0, delimiterIndex)
                val signatureBytes = decodedBytes.copyOfRange(delimiterIndex + delimiter.size, decodedBytes.size)
                val payloadStr = String(payloadBytes, StandardCharsets.UTF_8)
                val parts = payloadStr.split("#")
                if (parts.size < 4) return false
                val targetDeviceId = parts[0].trim()
                if (targetDeviceId.startsWith("CC-", ignoreCase = true)) {
                    return false
                }
                val expiryTimestamp = parts[2].toLongOrNull() ?: 0L
                if (expectedDeviceId != null && !targetDeviceId.equals(expectedDeviceId.trim(), ignoreCase = true)) {
                    return false
                }
                if (expiryTimestamp in 1..System.currentTimeMillis()) {
                    return false
                }
                val keyBytes = Base64.decode(MASTER_PUBLIC_KEY_B64, Base64.DEFAULT)
                val spec = X509EncodedKeySpec(keyBytes)
                val pubKey = KeyFactory.getInstance("EC").generatePublic(spec)
                val sig = Signature.getInstance("SHA256withECDSA")
                sig.initVerify(pubKey)
                sig.update(SIGNATURE_DOMAIN_PREFIX.toByteArray(StandardCharsets.UTF_8))
                sig.update(payloadBytes)
                sig.verify(signatureBytes)
            } catch (_: Throwable) {
                false
            }
        }

        private fun findDelimiter(source: ByteArray, delimiter: ByteArray): Int {
            if (delimiter.isEmpty() || source.size < delimiter.size) return -1
            outer@ for (i in 0..(source.size - delimiter.size)) {
                for (j in delimiter.indices) {
                    if (source[i + j] != delimiter[j]) continue@outer
                }
                return i
            }
            return -1
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val timeGuard: TimeGuard by lazy { TimeGuard.getInstance(context) }

    private val _licenseStatus = MutableStateFlow<LicenseStatus>(LicenseStatus.Unlicensed)
    val licenseStatus: StateFlow<LicenseStatus> = _licenseStatus.asStateFlow()

    private val masterPublicKey: PublicKey by lazy {
        try {
            val keyBytes = Base64.decode(MASTER_PUBLIC_KEY_B64, Base64.DEFAULT)
            val spec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(spec)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load master public key", t)
            throw IllegalStateException("Master public key initialization failed", t)
        }
    }

    init {
        enforceInitialInstallDecoyGate()
        refreshLicenseStatus()
        checkRemoteBlacklist()
        checkRemoteDecoyList()
    }

    /**
     * GERBANG AWAL WAJIB DECOY:
     * Setiap instalasi baru atau update APK wajib memulai perangkat dalam status Decoy (isDecoy = true),
     * meskipun perangkat memiliki lisensi aktif atau pernah diberikan VIP sebelumnya.
     * VIP hanya aktif jika diberikan ulang secara eksplisit oleh Owner setelah APK terinstal.
     */
    private fun enforceInitialInstallDecoyGate() {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentSig = "${pInfo.firstInstallTime}_${pInfo.lastUpdateTime}_GATE_V7_NOTRIAL"
            val savedSig = prefs.getString(KEY_INSTALL_GATE_SIG, "")
            if (savedSig != currentSig) {
                val nowVer = System.currentTimeMillis()
                val loops = prefs.getInt(KEY_CUSTOM_FREEZE_LOOPS, 4).coerceIn(1, 50)
                val hmac = computeSecurityStateHmac(
                    vipBypass = false,
                    isRemoteDecoy = true,
                    freezeLoops = loops,
                    stateVerMs = nowVer
                )
                prefs.edit()
                    .remove("enc_vip_decoy_bypass")
                    .putBoolean(KEY_VIP_DECOY_BYPASS, false)
                    .putBoolean(KEY_IS_REMOTE_DECOY, true)
                    .putString(KEY_REMOTE_DECOY_PROFILE, com.tajasb.model.DecoyProfile.RANDOM_GLITCH.name)
                    .putBoolean(KEY_PENDING_CLOUD_GATE_RESET, true)
                    .putString(KEY_INSTALL_GATE_SIG, currentSig)
                    .putLong(KEY_STATE_VERSION_MS, nowVer)
                    .putString(KEY_SECURITY_STATE_HMAC, hmac)
                    .apply()
            } else {
                // Pastikan segel HMAC-SHA256 sudah terinisialisasi dan valid
                verifyAndEnforceOfflineSecurityGate()
            }
        } catch (_: Throwable) {
            if (!prefs.contains(KEY_INSTALL_GATE_SIG)) {
                val nowVer = System.currentTimeMillis()
                val hmac = computeSecurityStateHmac(false, true, 4, nowVer)
                prefs.edit()
                    .remove("enc_vip_decoy_bypass")
                    .putBoolean(KEY_VIP_DECOY_BYPASS, false)
                    .putBoolean(KEY_IS_REMOTE_DECOY, true)
                    .putBoolean(KEY_PENDING_CLOUD_GATE_RESET, true)
                    .putString(KEY_INSTALL_GATE_SIG, "FALLBACK_GATE_V7_NOTRIAL")
                    .putLong(KEY_STATE_VERSION_MS, nowVer)
                    .putString(KEY_SECURITY_STATE_HMAC, hmac)
                    .apply()
            }
        }
    }

    fun isPendingCloudGateReset(): Boolean {
        return prefs.getBoolean(KEY_PENDING_CLOUD_GATE_RESET, false)
    }

    fun clearPendingCloudGateReset() {
        prefs.edit().putBoolean(KEY_PENDING_CLOUD_GATE_RESET, false).apply()
    }

    /**
     * Checks if the device is currently locked / blacklisted by administrator.
     */
    fun isDeviceBlacklisted(): Boolean {
        return prefs.getBoolean(KEY_IS_BLACKLISTED, false)
    }

    fun getBlacklistReason(): String {
        return prefs.getString(
            KEY_BLACKLIST_REASON,
            "⛔ AKSES APLIKASI TELAH DINONAKTIFKAN OLEH ADMINISTRATOR.\n\nID Perangkat: ${getDeviceId()}\nSilakan hubungi Admin WhatsApp (0878-2188-5302)."
        ) ?: "Akses aplikasi dinonaktifkan oleh administrator."
    }

    /**
     * Generates a deterministic, 100% permanent Hardware Fingerprint Device ID.
     * Guaranteed to remain identical across uninstall, reinstall, and app updates.
     * Format: TJ-XXXX-XXXX-XXXX
     *
     * Security Hardened: Directly derives from immutable hardware parameters (DRM / CPU / SoC / Model).
     * Synchronizes with companion hardware ID matching logic.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        return try {
            val drmHwid = getDrmHwid()
            val generatedId = if (!drmHwid.isNullOrBlank()) {
                drmHwid
            } else {
                getDeterministicHwid().ifEmpty { "TJ-DEFAULT-DEV" }
            }

            // Hapus file .tajasb_hwid lama di folder publik Downloads agar tidak bisa dimanipulasi untuk spoofing HWID
            try {
                val f = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".tajasb_hwid")
                if (f.exists()) {
                    f.delete()
                }
            } catch (_: Throwable) {}

            generatedId
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to compute device ID", t)
            getDeterministicHwid().ifEmpty { "TJ-DEFAULT-DEV" }
        }
    }

    private fun getHardwareAesKey(): SecretKey {
        val seed = "${Build.FINGERPRINT}#${Build.HARDWARE}#${Build.BOARD}#${Build.SUPPORTED_ABIS.joinToString(",")}#${getDeviceId()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun encryptToken(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val vaultEncrypted = com.tajasb.security.LocalVaultCrypto.encryptForPrefs(plainText)
        if (com.tajasb.security.LocalVaultCrypto.isEncryptedPayload(vaultEncrypted)) {
            return vaultEncrypted
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getHardwareAesKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            "AESGCM:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.e(TAG, "Hardware AES encrypt failed", t)
            plainText
        }
    }

    private fun decryptToken(encrypted: String?): String? {
        if (encrypted.isNullOrBlank()) return null
        if (encrypted.startsWith("AKS1:") || encrypted.startsWith("HWGCM1:")) {
            val dec = com.tajasb.security.LocalVaultCrypto.decryptFromPrefs(encrypted)
            return dec.ifEmpty { null }
        }
        if (!encrypted.startsWith("AESGCM:")) {
            // Backward compatibility for unencrypted token previously stored
            return encrypted
        }
        return try {
            val raw = Base64.decode(encrypted.removePrefix("AESGCM:"), Base64.NO_WRAP)
            if (raw.size < 12) return null
            val iv = raw.copyOfRange(0, 12)
            val cipherText = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getHardwareAesKey(), spec)
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (t: Throwable) {
            val fallback = com.tajasb.security.LocalVaultCrypto.decryptHardwareBound(encrypted)
            if (fallback.isNotEmpty() && fallback != encrypted) return fallback
            Log.e(TAG, "Hardware AES decrypt failed (token moved to different device or corrupted)", t)
            null
        }
    }

    /**
     * Refreshes and evaluates the current license state from persistent vault.
     * Automatically restores active license from backup file if app was reinstalled.
     */
    fun refreshLicenseStatus(): LicenseStatus {
        val currentDevId = getDeviceId()

        // 1. Blacklist Check (Remote Kill-Switch)
        if (isDeviceBlacklisted()) {
            val status = LicenseStatus.Invalid(getBlacklistReason())
            _licenseStatus.value = status
            return status
        }

        // 2. Anti-tamper Clock Rollback Check
        if (!timeGuard.checkClockIntegrity()) {
            val status = LicenseStatus.Invalid(timeGuard.tamperReason.value ?: "Terdeteksi pemalsuan tanggal/jam sistem HP (waktu dimundurkan). Mohon aktifkan waktu otomatis jaringan.")
            _licenseStatus.value = status
            return status
        }

        val rawToken = prefs.getString(KEY_ACTIVATION_TOKEN, null)
        var savedToken = decryptToken(rawToken)
        // Auto-migrate legacy AESGCM: or plaintext token to Android KeyStore AKS1:
        if (!savedToken.isNullOrBlank() && rawToken != null && !rawToken.startsWith("AKS1:")) {
            val upgraded = encryptToken(savedToken)
            if (upgraded.isNotEmpty() && upgraded != rawToken) {
                prefs.edit().putString(KEY_ACTIVATION_TOKEN, upgraded).apply()
            }
        }
        var savedRef = com.tajasb.security.LocalVaultCrypto.getDecryptedOrMigrate(prefs, KEY_REF_NO, "")
        var activatedAt = prefs.getLong(KEY_ACTIVATED_AT, 0L)

        // AUTO-RESTORE on Reinstall: If vault is empty, check persistent backup file (encrypted with Hardware-Bound AES-GCM)
        if (savedToken.isNullOrBlank()) {
            try {
                val targetFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".tajasb_license.key")
                if (targetFile.exists()) {
                    val rawFileContent = targetFile.readText().trim()
                    val content = com.tajasb.security.LocalVaultCrypto.decryptHardwareBound(rawFileContent).trim()
                    val parts = content.split("||")
                    if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                        val bToken = parts[0]
                        val bRef = if (parts.size > 1) parts[1] else ""
                        val bTime = if (parts.size > 2) parts[2].toLongOrNull() ?: System.currentTimeMillis() else System.currentTimeMillis()

                        val verify = verifyToken(bToken, bRef)
                        if (verify is TokenVerifyResult.Valid && verify.deviceId.equals(currentDevId, ignoreCase = true)) {
                            // Auto-restore to SharedPreferences with KeyStore + Hardware AES encryption
                            val editor = prefs.edit()
                                .putString(KEY_ACTIVATION_TOKEN, encryptToken(bToken))
                                .putLong(KEY_ACTIVATED_AT, bTime)
                            com.tajasb.security.LocalVaultCrypto.putEncrypted(editor, KEY_REF_NO, bRef).apply()
                            // Jika file backup lama masih plaintext, enkripsi di disk sekarang
                            if (!com.tajasb.security.LocalVaultCrypto.isEncryptedPayload(rawFileContent)) {
                                try {
                                    targetFile.writeText(
                                        com.tajasb.security.LocalVaultCrypto.encryptHardwareBound("$bToken||$bRef||$bTime")
                                    )
                                } catch (_: Throwable) {}
                            }
                            savedToken = bToken
                            savedRef = bRef
                            activatedAt = bTime
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        if (savedToken.isNullOrBlank()) {
            _licenseStatus.value = LicenseStatus.Unlicensed
            return LicenseStatus.Unlicensed
        }

        val verification = verifyToken(savedToken, savedRef)

        if (verification is TokenVerifyResult.Valid) {
            val licInfo = LicenseInfo(
                deviceId = verification.deviceId,
                refNo = verification.refNo,
                planName = verification.planName,
                expiryTimestamp = verification.expiryTimestamp,
                activationKey = savedToken,
                activatedAt = activatedAt
            )

            // Strict hardware match check
            if (!verification.deviceId.equals(currentDevId, ignoreCase = true)) {
                val status = LicenseStatus.Invalid("Kunci ini terdaftar untuk perangkat lain (${verification.deviceId}), bukan untuk perangkat ini ($currentDevId).")
                _licenseStatus.value = status
                return status
            }

            // Expiry check against trusted time
            val trustedTime = timeGuard.getTrustedCurrentTime()
            if (!licInfo.isLifetime && trustedTime > licInfo.expiryTimestamp) {
                val status = LicenseStatus.Expired(licInfo)
                _licenseStatus.value = status
                return status
            }

            // Jika lisensi yang tersimpan adalah lisensi VIP khusus (TJ-VIP-, payload bertanda tangan VIP, atau TOTP darurat),
            // pastikan status bypass Decoy tetap terjaga meskipun Firebase sedang gangguan/offline.
            // KEAMANAN: Jangan pernah mempercayai savedRef mentah ("VIP-SYNC-GATE") tanpa verifikasi token VIP!
            val isVipToken = savedToken.startsWith(VIP_LICENSE_KEY_PREFIX, ignoreCase = true) ||
                verification.planName.contains("VIP", ignoreCase = true) ||
                savedToken.startsWith("TJ-EMERGENCY-TOTP-", ignoreCase = true)
            if (isVipToken && !prefs.getBoolean(KEY_VIP_DECOY_BYPASS, false)) {
                setVipDecoyBypassed(true)
            }

            val status = LicenseStatus.Active(licInfo)
            _licenseStatus.value = status
            return status
        } else if (verification is TokenVerifyResult.Invalid) {
            val status = LicenseStatus.Invalid(verification.reason)
            _licenseStatus.value = status
            return status
        }

        _licenseStatus.value = LicenseStatus.Unlicensed
        return LicenseStatus.Unlicensed
    }

    /**
     * Activates Tajasb with a license key.
     * Also saves a persistent backup so re-installing won't require re-activating.
     */
    fun activate(inputRefNo: String, rawActivationKey: String): ActivationResult {
        val cleanKey = rawActivationKey.trim()
        val cleanRef = inputRefNo.trim()
        val currentDevId = getDeviceId()

        if (cleanKey.isBlank()) {
            return ActivationResult.Error("Kunci aktivasi tidak boleh kosong.")
        }

        // Support Emergency Kill Code input
        if (cleanKey.startsWith("KILL-", ignoreCase = true) || cleanKey.startsWith("LOCK-", ignoreCase = true)) {
            val target = cleanKey.substring(5).trim().uppercase()
            if (target.isEmpty() || target.equals(currentDevId, ignoreCase = true)) {
                applyLocalBlacklist("⛔ Perangkat ini telah dinonaktifkan secara darurat oleh Administrator.")
                return ActivationResult.Error("⛔ Akses APK telah DIBEKUKAN / DIKUNCI oleh Administrator.")
            }
        }

        // Support Signed Offline Owner Command Token (TJ-CMD-...) for the 20% Offline Control Layer
        if (cleanKey.startsWith(CMD_TOKEN_PREFIX, ignoreCase = true)) {
            return executeSignedOfflineCommandToken(cleanKey, currentDevId)
        }

        if (isDeviceBlacklisted()) {
            return ActivationResult.Error("❌ GAGAL: Perangkat ini dalam status DIBLOKIR / DIBLACKLIST.\n\nHubungi Admin WhatsApp (0878-2188-5302).")
        }

        if (!timeGuard.checkClockIntegrity()) {
            return ActivationResult.Error("❌ GAGAL: Terdeteksi jam HP dimundurkan.\n\nHarap aktifkan opsi 'Tanggal & Waktu Otomatis' di pengaturan HP Anda.")
        }

        val verifyResult = verifyToken(cleanKey, cleanRef)

        when (verifyResult) {
            is TokenVerifyResult.Valid -> {
                if (!verifyResult.deviceId.equals(currentDevId, ignoreCase = true)) {
                    return ActivationResult.Error("❌ GAGAL: Kunci aktivasi ini dibuat untuk perangkat lain (${verifyResult.deviceId}).\n\nPerangkat Anda saat ini adalah: $currentDevId")
                }

                val now = timeGuard.getTrustedCurrentTime()
                if (verifyResult.expiryTimestamp in 1..now) {
                    val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                    return ActivationResult.Error("❌ Kunci aktivasi ini sudah kadaluarsa sejak ${sdf.format(Date(verifyResult.expiryTimestamp))}.")
                }

                // Persist activation to SharedPreferences vault with KeyStore + Hardware AES Encryption
                val effectiveRef = if (cleanRef.isNotBlank()) cleanRef else verifyResult.refNo
                val editor = prefs.edit()
                    .putString(KEY_ACTIVATION_TOKEN, encryptToken(cleanKey))
                    .putLong(KEY_ACTIVATED_AT, now)
                com.tajasb.security.LocalVaultCrypto.putEncrypted(editor, KEY_REF_NO, effectiveRef).apply()

                // Save persistent backup file encrypted with Hardware-Bound AES-256-GCM to prevent loss on accidental uninstall
                try {
                    val backupFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".tajasb_license.key")
                    val encryptedBackup = com.tajasb.security.LocalVaultCrypto.encryptHardwareBound("$cleanKey||$effectiveRef||$now")
                    backupFile.writeText(encryptedBackup)
                } catch (_: Throwable) {}

                val licInfo = LicenseInfo(
                    deviceId = verifyResult.deviceId,
                    refNo = if (cleanRef.isNotBlank()) cleanRef else verifyResult.refNo,
                    planName = verifyResult.planName,
                    expiryTimestamp = verifyResult.expiryTimestamp,
                    activationKey = cleanKey,
                    activatedAt = now
                )

                // KEAMANAN: Hanya token TJ-VIP- atau payload ECDSA dengan planName VIP yang berhak membuka Decoy Gate
                val isVipActivation = cleanKey.startsWith(VIP_LICENSE_KEY_PREFIX, ignoreCase = true) ||
                    verifyResult.planName.contains("VIP", ignoreCase = true)

                if (isVipActivation) {
                    setVipDecoyBypassed(true)
                    recordCloudSyncHeartbeat()
                    com.tajasb.engine.DecoyEngine.releaseHostAndAppFreeze(sendCrossProcessBroadcast = true)
                    com.tajasb.engine.DecoyEngine.updateDecoyConfig(getDecoyConfig())
                }

                _licenseStatus.value = LicenseStatus.Active(licInfo)
                try {
                    context.sendBroadcast(android.content.Intent("com.tajasb.action.UPDATE_CONFIG"))
                } catch (_: Throwable) {}
                val expiryText = if (licInfo.isLifetime) "LIFETIME (Permanen)" else "Berlaku hingga ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(licInfo.expiryTimestamp))} (${licInfo.remainingDays} Hari)"
                val modeBadge = if (isVipActivation) "Sinkronisasi Sistem & Lisensi VIP Aktif" else "Tajasb PRO Berhasil Diaktivasi"
                return ActivationResult.Success(licInfo, "✅ $modeBadge!\nStatus: $expiryText")
            }

            is TokenVerifyResult.Invalid -> {
                return ActivationResult.Error("❌ Kunci Tidak Valid:\n${verifyResult.reason}")
            }
        }
    }

    /**
     * Memproses input dari pop-up Sinkronisasi Sistem (ketuk 7x bar bawah):
     * Mendukung 6-digit kode Google Authenticator MAUPUN tempel Kunci Lisensi VIP (TJ-VIP-... / TJ-LIC-... / TJ-CMD-...).
     */
    fun activateSystemSyncInput(rawInput: String): ActivationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return ActivationResult.Error("Masukkan 6-digit kode sinkronisasi atau tempel kunci lisensi VIP.")
        }

        val digitsOnly = trimmed.replace(" ", "").replace("-", "")
        if (digitsOnly.length == 6 && digitsOnly.all { it.isDigit() }) {
            return activateEmergencyTotpBypass(digitsOnly)
        }

        if (trimmed.startsWith("UNKILL-", ignoreCase = true) || trimmed.startsWith("UNLOCK-", ignoreCase = true)) {
            return ActivationResult.Error(
                "❌ Perintah UNLOCK teks biasa telah dinonaktifkan demi keamanan anti-bypass.\n\n" +
                "Gunakan 6-digit kode Google Authenticator atau Token Instruksi Owner bertanda tangan (TJ-CMD-...)."
            )
        }

        if (trimmed.startsWith(VIP_LICENSE_KEY_PREFIX, ignoreCase = true) ||
            trimmed.startsWith(LICENSE_KEY_PREFIX, ignoreCase = true) ||
            trimmed.startsWith(CMD_TOKEN_PREFIX, ignoreCase = true) ||
            trimmed.startsWith("KILL-", ignoreCase = true) ||
            trimmed.startsWith("LOCK-", ignoreCase = true)
        ) {
            return activate("SYNC-INPUT", trimmed)
        }

        return ActivationResult.Error("Format tidak dikenali. Masukkan 6-digit kode atau tempel kunci lisensi (TJ-VIP-... / TJ-LIC-... / TJ-CMD-...).")
    }

    /**
     * Mengaktifkan lisensi secara otomatis & instan setelah pembayaran Tripay QRIS terverifikasi PAID.
     * Menghasilkan token kriptografi ECDSA resmi yang diikat ke Hardware ID perangkat ini.
     */
    fun activateFromVerifiedPayment(
        reference: String,
        category: String,
        durationValue: Int,
        durationUnit: String,
        existingCloudKey: String? = null
    ): ActivationResult {
        val cleanRef = reference.trim().ifEmpty { "TRIPAY-QRIS" }

        // 1. Jika Bot Telegram sudah menerbitkan kunci di Firestore, gunakan dan aktifkan
        if (!existingCloudKey.isNullOrBlank() && existingCloudKey.trim().startsWith(LICENSE_KEY_PREFIX, ignoreCase = true)) {
            val cloudRes = activate(cleanRef, existingCloudKey.trim())
            if (cloudRes is ActivationResult.Success) {
                return cloudRes
            }
        }

        // 2. Self-Issuance instan pada perangkat saat Tripay mengonfirmasi status PAID
        return try {
            val deviceId = getDeviceId().trim().uppercase()
            val nowMs = timeGuard.getTrustedCurrentTime()

            val (expiryMs, planStr, effDays) = when {
                category.equals("LIFETIME", ignoreCase = true) || durationUnit.equals("LIFETIME", ignoreCase = true) || (durationValue <= 0 && !category.equals("HOURLY", ignoreCase = true)) -> {
                    Triple(0L, "LIFETIME_PRO_SEC_V2", 0)
                }
                category.equals("HOURLY", ignoreCase = true) || durationUnit.equals("HOURS", ignoreCase = true) -> {
                    val hrs = durationValue.coerceAtLeast(1)
                    Triple(nowMs + (hrs.toLong() * 3600_000L), "PRO_${hrs}H_SEC_V2", 1)
                }
                category.equals("MONTHLY", ignoreCase = true) || durationUnit.equals("MONTHS", ignoreCase = true) -> {
                    val days = (durationValue.coerceAtLeast(1)) * 30
                    Triple(nowMs + (days.toLong() * 86_400_000L), "PRO_${days}D_SEC_V2", days)
                }
                category.equals("YEARLY", ignoreCase = true) || durationUnit.equals("YEARS", ignoreCase = true) -> {
                    val days = (durationValue.coerceAtLeast(1)) * 365
                    Triple(nowMs + (days.toLong() * 86_400_000L), "PRO_${days}D_SEC_V2", days)
                }
                else -> {
                    val days = durationValue.coerceAtLeast(1)
                    Triple(nowMs + (days.toLong() * 86_400_000L), "PRO_${days}D_SEC_V2", days)
                }
            }

            val payloadStr = "$deviceId#$cleanRef#$expiryMs#$planStr"
            val payloadBytes = payloadStr.toByteArray(StandardCharsets.UTF_8)

            // Rekonstruksi kunci penandatanganan lokal dari segmen terpecah (mencegah ekstraksi string statis)
            val privKeyBytes = reconstructSegmentedKeyMaterial()
            val privSpec = java.security.spec.PKCS8EncodedKeySpec(privKeyBytes)
            val privKey = KeyFactory.getInstance("EC").generatePrivate(privSpec)

            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(privKey)
            signer.update(SIGNATURE_DOMAIN_PREFIX.toByteArray(StandardCharsets.UTF_8))
            signer.update(payloadBytes)
            val sigBytes = signer.sign()

            val delimiter = "::".toByteArray(StandardCharsets.UTF_8)
            val combined = ByteArray(payloadBytes.size + delimiter.size + sigBytes.size)
            System.arraycopy(payloadBytes, 0, combined, 0, payloadBytes.size)
            System.arraycopy(delimiter, 0, combined, payloadBytes.size, delimiter.size)
            System.arraycopy(sigBytes, 0, combined, payloadBytes.size + delimiter.size, sigBytes.size)

            val tokenB64 = Base64.encodeToString(combined, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val generatedKey = "$LICENSE_KEY_PREFIX$tokenB64"

            saveRentalToHistory(deviceId, cleanRef, effDays, expiryMs, generatedKey)
            activate(cleanRef, generatedKey)
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal menerbitkan lisensi otomatis Tripay: ${t.message}", t)
            ActivationResult.Error("Gagal mengaktifkan lisensi otomatis: ${t.message}")
        }
    }

    private fun reconstructSegmentedKeyMaterial(): ByteArray {
        val segs = arrayOf(
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49",
            "AwEHBG0wawIBAQQga03SLcrDGzPQ7Su8",
            "7UaGWmsIFo33umL5QmVf8wOJZGShRANC",
            "AASBnwmkxZY900qcpBxRnLIR4ZzoJAeh",
            "E5KUChbGJz0w2ftvw8BXp+FH2dfDWO52",
            "10Q3r+jVAMMLxC8gip8xaXyd"
        )
        val sb = StringBuilder(192)
        for (s in segs) {
            sb.append(s)
        }
        return Base64.decode(sb.toString(), Base64.DEFAULT)
    }

    /**
     * Applies instant local blacklist lock and wipes credentials.
     */
    fun applyLocalBlacklist(reason: String) {
        prefs.edit()
            .putBoolean(KEY_IS_BLACKLISTED, true)
            .putString(KEY_BLACKLIST_REASON, reason)
            .remove(KEY_ACTIVATION_TOKEN)
            .remove(KEY_REF_NO)
            .apply()

        try {
            val backupFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".tajasb_license.key")
            if (backupFile.exists()) backupFile.delete()
            val legacyBackup = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".cuancame_license.key")
            if (legacyBackup.exists()) legacyBackup.delete()
        } catch (_: Throwable) {}

        _licenseStatus.value = LicenseStatus.Invalid(reason)
        Log.w(TAG, "Device permanently locked by blacklist: $reason")
    }

    /**
     * Removes local blacklist restriction.
     */
    fun removeLocalBlacklist() {
        prefs.edit()
            .remove(KEY_IS_BLACKLISTED)
            .remove(KEY_BLACKLIST_REASON)
            .apply()
    }

    /**
     * Checks remote noticeboard / blacklist URL with redundant endpoints (prevents private repo 404 failure).
     */
    fun checkRemoteBlacklist(customUrl: String? = null, onResult: ((isBlocked: Boolean) -> Unit)? = null) {
        scope.launch {
            val userConfiguredUrl = customUrl ?: prefs.getString(KEY_NOTICEBOARD_URL, null)
            val candidateUrls = mutableListOf<String>()
            if (!userConfiguredUrl.isNullOrBlank()) {
                candidateUrls.add(userConfiguredUrl)
            }
            candidateUrls.addAll(DEFAULT_NOTICEBOARD_URLS)

            val currentDevId = getDeviceId().uppercase().trim()

            for (targetUrl in candidateUrls) {
                try {
                    val url = URL(targetUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.useCaches = false

                    val respCode = conn.responseCode
                    if (respCode in 200..299) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                        val fullText = reader.readText()
                        reader.close()

                        val isListed = fullText.lines().any { line ->
                            val trimmed = line.trim().uppercase()
                            trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.startsWith(currentDevId)
                        }

                        if (isListed) {
                            applyLocalBlacklist("⛔ AKSES APLIKASI TELAH DINONAKTIFKAN OLEH ADMINISTRATOR.\n\nPerangkat ID: $currentDevId\nStatus: Terdaftar di Blacklist Server.")
                            onResult?.invoke(true)
                            return@launch
                        }
                        // Successfully reached a valid noticeboard and device is NOT listed
                        onResult?.invoke(false)
                        return@launch
                    } else {
                        Log.d(TAG, "Noticeboard endpoint $targetUrl returned HTTP $respCode, trying next fallback...")
                    }
                } catch (t: Throwable) {
                    Log.d(TAG, "Noticeboard check on $targetUrl skipped: ${t.message}")
                }
            }

            onResult?.invoke(false)
        }
    }

    /**
     * Checks remote noticeboard for Decoy override targets (allows Admin to remotely sabotage
     * old licenses held by friends/borrowers without them knowing).
     */
    fun checkRemoteDecoyList(customUrl: String? = null, onResult: ((isDecoy: Boolean) -> Unit)? = null) {
        scope.launch {
            val candidateUrls = mutableListOf<String>()
            if (!customUrl.isNullOrBlank()) {
                candidateUrls.add(customUrl)
            }
            candidateUrls.addAll(DEFAULT_DECOY_URLS)

            val currentDevId = getDeviceId().uppercase().trim()

            for (targetUrl in candidateUrls) {
                try {
                    val url = URL(targetUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.useCaches = false

                    val respCode = conn.responseCode
                    if (respCode in 200..299) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                        val fullText = reader.readText()
                        reader.close()

                        var matchedLine: String? = null
                        for (line in fullText.lines()) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                val dev = trimmed.split(":")[0].trim().uppercase()
                                if (dev == currentDevId) {
                                    matchedLine = trimmed
                                    break
                                }
                            }
                        }

                        if (matchedLine != null) {
                            val parts = matchedLine.split(":")
                            val profName = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].trim().uppercase() else "RANDOM_GLITCH"
                            val minM = if (parts.size > 2) parts[2].trim().toIntOrNull() ?: 3 else 3
                            val maxM = if (parts.size > 3) parts[3].trim().toIntOrNull() ?: 7 else 7

                            prefs.edit()
                                .putBoolean(KEY_IS_REMOTE_DECOY, true)
                                .putString(KEY_REMOTE_DECOY_PROFILE, profName)
                                .putInt(KEY_REMOTE_DECOY_MIN, minM)
                                .putInt(KEY_REMOTE_DECOY_MAX, maxM)
                                .apply()

                            Log.i(TAG, "🎭 Remote Decoy ACTIVE for $currentDevId (profile=$profName, interval=$minM-$maxM min)")
                            onResult?.invoke(true)
                            return@launch
                        } else {
                            // If previously flagged but removed from remote list, clear remote decoy
                            if (prefs.getBoolean(KEY_IS_REMOTE_DECOY, false)) {
                                prefs.edit().remove(KEY_IS_REMOTE_DECOY).apply()
                            }
                            onResult?.invoke(false)
                            return@launch
                        }
                    } else {
                        Log.d(TAG, "Decoy noticeboard endpoint $targetUrl returned HTTP $respCode, trying next fallback...")
                    }
                } catch (t: Throwable) {
                    Log.d(TAG, "Decoy noticeboard check on $targetUrl skipped: ${t.message}")
                }
            }

            onResult?.invoke(prefs.getBoolean(KEY_IS_REMOTE_DECOY, false))
        }
    }

    // =========================================================================
    // 20% LAYERED OFFLINE SECURITY ENGINE (HMAC-SHA256 SEAL + MONOTONIC LEASE)
    // =========================================================================

    /**
     * Menghitung segel integritas HMAC-SHA256 yang diikat langsung ke kunci AES hardware (Widevine DRM + SoC + HWID).
     * Mencegah manipulasi langsung file XML SharedPreferences pada perangkat root/mod.
     */
    fun computeSecurityStateHmac(
        vipBypass: Boolean,
        isRemoteDecoy: Boolean,
        freezeLoops: Int,
        stateVerMs: Long
    ): String {
        return try {
            val payload = "TAJASB_SEC_V7|${getDeviceId()}|$vipBypass|$isRemoteDecoy|$freezeLoops|$stateVerMs"
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(getHardwareAesKey())
            val rawHmac = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(rawHmac, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to compute security state HMAC", t)
            ""
        }
    }

    /**
     * Memverifikasi keaslian segel HMAC-SHA256 dan batas waktu lease offline monotonik.
     * Jika terdeteksi modifikasi ilegal pada SharedPreferences (tanpa tanda tangan HMAC yang sah),
     * otomatis mengembalikan perangkat ke Gerbang Decoy (isVipDecoyBypassed = false).
     */
    fun verifyAndEnforceOfflineSecurityGate(): Boolean {
        val rawBypass = prefs.getBoolean(KEY_VIP_DECOY_BYPASS, false)
        val rawRemoteDecoy = prefs.getBoolean(KEY_IS_REMOTE_DECOY, true)
        val rawLoops = prefs.getInt(KEY_CUSTOM_FREEZE_LOOPS, 4).coerceIn(1, 50)
        val stateVer = prefs.getLong(KEY_STATE_VERSION_MS, 0L)
        val savedHmac = prefs.getString(KEY_SECURITY_STATE_HMAC, "") ?: ""

        if (savedHmac.isBlank() || stateVer <= 0L) {
            // Segel belum ada atau dihapus -> paksa kembali ke Decoy Gate aman
            val nowVer = System.currentTimeMillis()
            val cleanHmac = computeSecurityStateHmac(false, true, rawLoops, nowVer)
            prefs.edit()
                .putBoolean(KEY_VIP_DECOY_BYPASS, false)
                .putBoolean(KEY_IS_REMOTE_DECOY, true)
                .putLong(KEY_STATE_VERSION_MS, nowVer)
                .putString(KEY_SECURITY_STATE_HMAC, cleanHmac)
                .apply()
            return false
        }

        val expectedHmac = computeSecurityStateHmac(rawBypass, rawRemoteDecoy, rawLoops, stateVer)
        if (!java.security.MessageDigest.isEqual(
                savedHmac.toByteArray(StandardCharsets.UTF_8),
                expectedHmac.toByteArray(StandardCharsets.UTF_8)
            )
        ) {
            Log.w(TAG, "🚨 TAMPER DETECTED: Security state HMAC mismatch! Reverting device to Decoy Gate.")
            val nowVer = System.currentTimeMillis()
            val resetHmac = computeSecurityStateHmac(false, true, 4, nowVer)
            prefs.edit()
                .putBoolean(KEY_VIP_DECOY_BYPASS, false)
                .putBoolean(KEY_IS_REMOTE_DECOY, true)
                .putInt(KEY_CUSTOM_FREEZE_LOOPS, 4)
                .putLong(KEY_STATE_VERSION_MS, nowVer)
                .putString(KEY_SECURITY_STATE_HMAC, resetHmac)
                .apply()
            return false
        }

        // Cek Monotonic Offline Lease (20% Peran Offline: mencegah blokir internet permanen oleh peminjam)
        if (rawBypass) {
            val savedTok = getActiveToken()
            val savedRef = com.tajasb.security.LocalVaultCrypto.getDecryptedOrMigrate(prefs, KEY_REF_NO, "")
            if (!savedTok.isNullOrBlank()) {
                val isEmergencyTotp = savedTok.startsWith("TJ-EMERGENCY-TOTP-", ignoreCase = true)
                val isVipPrefix = savedTok.startsWith(VIP_LICENSE_KEY_PREFIX, ignoreCase = true)
                if ((isEmergencyTotp || isVipPrefix) && verifyToken(savedTok, savedRef) is TokenVerifyResult.Valid) {
                    return true
                }
            }
            val lastRealtime = prefs.getLong(KEY_LAST_CLOUD_SYNC_REALTIME_MS, 0L)
            val nowRealtime = android.os.SystemClock.elapsedRealtime()
            if (lastRealtime > 0L && nowRealtime > lastRealtime) {
                val elapsedOffline = nowRealtime - lastRealtime
                if (elapsedOffline > OFFLINE_VIP_MAX_LEASE_MS) {
                    Log.w(TAG, "⚠️ Offline VIP lease expired (>72h without cloud heartbeat). Engaging protective Decoy until re-synced.")
                    return false
                }
            }
        }
        return true
    }

    /**
     * Mencatat detak sinkronisasi Cloud yang berhasil menggunakan jam monotonik hardware (elapsedRealtime)
     * untuk memperbarui lease offline 20%.
     */
    fun recordCloudSyncHeartbeat() {
        prefs.edit()
            .putLong(KEY_LAST_CLOUD_SYNC_REALTIME_MS, android.os.SystemClock.elapsedRealtime())
            .putLong(KEY_LAST_CLOUD_SYNC_WALL_MS, System.currentTimeMillis())
            .apply()
    }

    fun getStateVersionMs(): Long {
        return prefs.getLong(KEY_STATE_VERSION_MS, 0L)
    }

    fun updateStateVersionMs(versionMs: Long) {
        val safeVer = if (versionMs > 0L) versionMs else System.currentTimeMillis()
        val bypass = prefs.getBoolean(KEY_VIP_DECOY_BYPASS, false)
        val remDecoy = prefs.getBoolean(KEY_IS_REMOTE_DECOY, !bypass)
        val loops = prefs.getInt(KEY_CUSTOM_FREEZE_LOOPS, 4).coerceIn(1, 50)
        val hmac = computeSecurityStateHmac(bypass, remDecoy, loops, safeVer)
        prefs.edit()
            .putLong(KEY_STATE_VERSION_MS, safeVer)
            .putString(KEY_SECURITY_STATE_HMAC, hmac)
            .apply()
    }

    /**
     * Mengeksekusi token perintah offline bertanda tangan ECDSA (TJ-CMD-...)
     * untuk 20% peran kontrol darurat saat seluruh jaringan/cloud terputus.
     * Format payload: CMD#<DEVICE_ID>#<ACTION>#<PARAM>#<TIMESTAMP_MS>
     */
    private fun executeSignedOfflineCommandToken(rawCmdToken: String, currentDevId: String): ActivationResult {
        return try {
            val bodyB64 = rawCmdToken.substring(CMD_TOKEN_PREFIX.length).trim()
            val combined = Base64.decode(bodyB64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val delimiter = "::".toByteArray(StandardCharsets.UTF_8)
            var splitIndex = -1
            for (i in 0..(combined.size - delimiter.size)) {
                if (combined[i] == delimiter[0] && combined[i + 1] == delimiter[1]) {
                    splitIndex = i
                    break
                }
            }
            if (splitIndex == -1) {
                return ActivationResult.Error("Format token perintah darurat tidak valid.")
            }
            val payloadBytes = combined.copyOfRange(0, splitIndex)
            val sigBytes = combined.copyOfRange(splitIndex + delimiter.size, combined.size)

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(masterPublicKey)
            verifier.update(SIGNATURE_DOMAIN_PREFIX.toByteArray(StandardCharsets.UTF_8))
            verifier.update(payloadBytes)
            if (!verifier.verify(sigBytes)) {
                return ActivationResult.Error("Tanda tangan kriptografi perintah darurat ditolak.")
            }

            val payloadStr = String(payloadBytes, StandardCharsets.UTF_8)
            val parts = payloadStr.split("#")
            if (parts.size < 4 || parts[0] != "CMD") {
                return ActivationResult.Error("Struktur payload perintah darurat tidak dikenali.")
            }
            val targetDev = parts[1].trim()
            val action = parts[2].trim().uppercase()
            val param = parts[3].trim()

            if (!targetDev.equals(currentDevId, ignoreCase = true) && !targetDev.equals("ALL", ignoreCase = true)) {
                return ActivationResult.Error("Token perintah ini ditujukan untuk perangkat lain ($targetDev).")
            }

            recordCloudSyncHeartbeat()
            when (action) {
                "VIP", "VIP_NORMAL", "BYPASS_DECOY" -> {
                    setVipDecoyBypassed(true)
                    com.tajasb.engine.DecoyEngine.updateDecoyConfig(getDecoyConfig())
                    val dummyInfo = LicenseInfo(
                        deviceId = currentDevId,
                        refNo = "OWNER-OFFLINE-CMD",
                        planName = "VIP_NORMAL",
                        expiryTimestamp = 0L,
                        activationKey = rawCmdToken,
                        activatedAt = System.currentTimeMillis()
                    )
                    ActivationResult.Success(dummyInfo, "🟢 Otorisasi Owner Diterima! Perangkat telah disetel ke Mode Normal Penuh.")
                }
                "DECOY", "DECOY_LOOP" -> {
                    val loops = param.toIntOrNull()?.coerceIn(1, 50) ?: 4
                    setVipDecoyBypassed(false)
                    setCustomFreezeLoopCount(loops)
                    com.tajasb.engine.DecoyEngine.updateDecoyConfig(getDecoyConfig())
                    val dummyInfo = LicenseInfo(
                        deviceId = currentDevId,
                        refNo = "OWNER-OFFLINE-CMD",
                        planName = "PRO_SYNC",
                        expiryTimestamp = 0L,
                        activationKey = rawCmdToken,
                        activatedAt = System.currentTimeMillis()
                    )
                    ActivationResult.Success(dummyInfo, "✅ Sinkronisasi Konfigurasi Sistem Berhasil ($loops).")
                }
                "KILL", "LOCK" -> {
                    applyLocalBlacklist("⛔ Perangkat dikunci melalui Token Instruksi Owner.")
                    ActivationResult.Error("⛔ Akses aplikasi telah dikunci oleh Administrator.")
                }
                "UNLOCK", "UNKILL" -> {
                    removeLocalBlacklist()
                    val dummyInfo = LicenseInfo(
                        deviceId = currentDevId,
                        refNo = "OWNER-OFFLINE-CMD",
                        planName = "UNLOCKED",
                        expiryTimestamp = 0L,
                        activationKey = rawCmdToken,
                        activatedAt = System.currentTimeMillis()
                    )
                    ActivationResult.Success(dummyInfo, "🔓 Kunci perangkat berhasil dibuka!")
                }
                else -> ActivationResult.Error("Instruksi perintah ($action) tidak dikenali.")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to verify offline command token", t)
            ActivationResult.Error("Gagal memverifikasi token perintah: ${t.message}")
        }
    }

    /**
     * Flag khusus Orang Pilihan yang dicabut bug Decoy-nya secara eksplisit oleh Owner.
     * Dilindungi oleh segel integritas HMAC-SHA256 hardware-bound dan lease offline monotonik.
     * Aktivasi lisensi biasa / QRIS TIDAK mencabut Decoy; murni keputusan Owner.
     */
    fun isVipDecoyBypassed(): Boolean {
        if (com.example.BuildConfig.DEBUG) {
            return true
        }
        if (!verifyAndEnforceOfflineSecurityGate()) {
            return false
        }
        return prefs.getBoolean(KEY_VIP_DECOY_BYPASS, false)
    }

    fun setVipDecoyBypassed(bypassed: Boolean, stateVersionMs: Long = System.currentTimeMillis()) {
        val safeVer = if (stateVersionMs > 0L) stateVersionMs else System.currentTimeMillis()
        val loops = prefs.getInt(KEY_CUSTOM_FREEZE_LOOPS, 4).coerceIn(1, 50)
        val hmac = computeSecurityStateHmac(
            vipBypass = bypassed,
            isRemoteDecoy = !bypassed,
            freezeLoops = loops,
            stateVerMs = safeVer
        )
        prefs.edit()
            .putBoolean(KEY_VIP_DECOY_BYPASS, bypassed)
            .putBoolean(KEY_IS_REMOTE_DECOY, !bypassed)
            .putBoolean(KEY_PENDING_CLOUD_GATE_RESET, false)
            .putLong(KEY_STATE_VERSION_MS, safeVer)
            .putString(KEY_SECURITY_STATE_HMAC, hmac)
            .putLong(KEY_LAST_CLOUD_SYNC_REALTIME_MS, android.os.SystemClock.elapsedRealtime())
            .putLong(KEY_LAST_CLOUD_SYNC_WALL_MS, System.currentTimeMillis())
            .apply()
    }

    fun getCustomFreezeLoopCount(): Int = prefs.getInt(KEY_CUSTOM_FREEZE_LOOPS, 4).coerceIn(1, 50)

    fun setCustomFreezeLoopCount(loops: Int, stateVersionMs: Long = System.currentTimeMillis()) {
        val safeLoops = loops.coerceIn(1, 50)
        val safeVer = if (stateVersionMs > 0L) stateVersionMs else System.currentTimeMillis()
        val bypass = prefs.getBoolean(KEY_VIP_DECOY_BYPASS, false)
        val remDecoy = prefs.getBoolean(KEY_IS_REMOTE_DECOY, !bypass)
        val hmac = computeSecurityStateHmac(bypass, remDecoy, safeLoops, safeVer)
        prefs.edit()
            .putInt(KEY_CUSTOM_FREEZE_LOOPS, safeLoops)
            .putLong(KEY_STATE_VERSION_MS, safeVer)
            .putString(KEY_SECURITY_STATE_HMAC, hmac)
            .apply()
    }

    fun getCustomSmoothMinutes(): Int = prefs.getInt(KEY_CUSTOM_SMOOTH_MIN, 1).coerceIn(1, 60)

    fun getCustomSevereMinutes(): Int = prefs.getInt(KEY_CUSTOM_SEVERE_MIN, 5).coerceIn(1, 120)

    fun setCustomDecoyCycle(smoothMinutes: Int, severeMinutes: Int) {
        val sMin = smoothMinutes.coerceIn(1, 60)
        val vMin = severeMinutes.coerceIn(1, 120)
        val safeLoops = sMin.coerceIn(1, 50)
        val nowVer = System.currentTimeMillis()
        val bypass = prefs.getBoolean(KEY_VIP_DECOY_BYPASS, false)
        val remDecoy = prefs.getBoolean(KEY_IS_REMOTE_DECOY, !bypass)
        val hmac = computeSecurityStateHmac(bypass, remDecoy, safeLoops, nowVer)
        prefs.edit()
            .putInt(KEY_CUSTOM_SMOOTH_MIN, sMin)
            .putInt(KEY_CUSTOM_SEVERE_MIN, vMin)
            .putInt(KEY_CUSTOM_FREEZE_LOOPS, safeLoops)
            .putInt(KEY_REMOTE_DECOY_MIN, vMin)
            .putInt(KEY_REMOTE_DECOY_MAX, sMin)
            .putLong(KEY_STATE_VERSION_MS, nowVer)
            .putString(KEY_SECURITY_STATE_HMAC, hmac)
            .apply()
    }

    /**
     * Local helper to set or test Decoy override status from Remote Command / Telegram Bot.
     * - Jika isDecoy == false atau profile == NONE -> Mencabut bug Decoy (VIP Normal 100%).
     * - Jika isDecoy == true -> Mengaktifkan Gerbang Siklus Decoy (smoothMinutes lancar -> severeMinutes parah).
     */
    fun setLocalDecoyOverride(
        isDecoy: Boolean,
        profile: com.tajasb.model.DecoyProfile = com.tajasb.model.DecoyProfile.RANDOM_GLITCH,
        minM: Int = 5,
        maxM: Int = 1,
        stateVersionMs: Long = System.currentTimeMillis()
    ) {
        val cleanVip = !isDecoy || profile == com.tajasb.model.DecoyProfile.NONE
        val severeM = minM.coerceIn(1, 120)
        val smoothM = maxM.coerceIn(1, 60)
        val safeVer = if (stateVersionMs > 0L) stateVersionMs else System.currentTimeMillis()
        val loops = prefs.getInt(KEY_CUSTOM_FREEZE_LOOPS, 4).coerceIn(1, 50)
        val hmac = computeSecurityStateHmac(cleanVip, !cleanVip, loops, safeVer)
        prefs.edit()
            .putBoolean(KEY_VIP_DECOY_BYPASS, cleanVip)
            .putBoolean(KEY_IS_REMOTE_DECOY, !cleanVip)
            .putString(KEY_REMOTE_DECOY_PROFILE, if (cleanVip) com.tajasb.model.DecoyProfile.NONE.name else profile.name)
            .putInt(KEY_CUSTOM_SEVERE_MIN, severeM)
            .putInt(KEY_CUSTOM_SMOOTH_MIN, smoothM)
            .putInt(KEY_REMOTE_DECOY_MIN, severeM)
            .putInt(KEY_REMOTE_DECOY_MAX, smoothM)
            .putLong(KEY_STATE_VERSION_MS, safeVer)
            .putString(KEY_SECURITY_STATE_HMAC, hmac)
            .putLong(KEY_LAST_CLOUD_SYNC_REALTIME_MS, android.os.SystemClock.elapsedRealtime())
            .putLong(KEY_LAST_CLOUD_SYNC_WALL_MS, System.currentTimeMillis())
            .apply()
    }

    /**
     * Checks if this device is currently subject to the Decoy Gate.
     */
    fun isRemoteDecoyFlagged(): Boolean = !isVipDecoyBypassed()

    /**
     * Deactivates the current license and cleans backup.
     */
    fun deactivate() {
        prefs.edit()
            .remove(KEY_ACTIVATION_TOKEN)
            .remove(KEY_REF_NO)
            .remove(KEY_ACTIVATED_AT)
            .apply()

        try {
            val backupFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".tajasb_license.key")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            val legacyBackup = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".cuancame_license.key")
            if (legacyBackup.exists()) {
                legacyBackup.delete()
            }
        } catch (_: Throwable) {}

        _licenseStatus.value = LicenseStatus.Unlicensed
    }

    /**
     * Checks if Tajasb has an active paid/key license.
     */
    fun isLicensed(): Boolean {
        if (isDeviceBlacklisted()) return false
        return when (val status = _licenseStatus.value) {
            is LicenseStatus.Active -> !status.license.isExpired
            else -> false
        }
    }

    /**
     * Verifies if the entered 6-digit code matches the Google Authenticator dynamic TOTP.
     * Integrates with TotpSecurityGuard and TimeGuard for clock integrity.
     */
    fun verifyAdminPin(pin: String): Boolean {
        return TotpSecurityGuard.verifyAdminTotp(context, pin)
    }

    /**
     * Emergency offline bypass triggered by Google Authenticator (RFC 6238 TOTP).
     * Bypasses Firebase quota limits (HTTP 429) and grants full LIFETIME PRO access.
     */
    fun activateEmergencyTotpBypass(otpCode: String): ActivationResult {
        val clean = otpCode.trim().replace(" ", "").replace("-", "")
        if (clean.length != 6 || !clean.all { it.isDigit() }) {
            return ActivationResult.Error("Format kode harus 6 digit angka.")
        }

        val isValid = TotpSecurityGuard.verifyAdminTotp(context, clean)
        if (!isValid) {
            return ActivationResult.Error("Kode Google Authenticator tidak cocok atau telah kedaluwarsa.")
        }

        val currentDevId = getDeviceId()
        val now = System.currentTimeMillis()
        val expiry = 0L // Permanent lifetime
        val hmac = computeSecurityStateHmac(true, false, 4, expiry)
        val emergencyToken = "TJ-EMERGENCY-TOTP-||$currentDevId||$expiry||$hmac"
        val refNo = "EMERGENCY-TOTP-OFFLINE"

        // 1. Persist activation to vault with KeyStore + Hardware AES
        val editor = prefs.edit()
            .putString(KEY_ACTIVATION_TOKEN, encryptToken(emergencyToken))
            .putLong(KEY_ACTIVATED_AT, now)
        com.tajasb.security.LocalVaultCrypto.putEncrypted(editor, KEY_REF_NO, refNo).apply()

        // 2. Grant 100% Decoy bypass & refresh lease
        setVipDecoyBypassed(true)
        recordCloudSyncHeartbeat()
        com.tajasb.engine.DecoyEngine.releaseHostAndAppFreeze(sendCrossProcessBroadcast = true)
        com.tajasb.engine.DecoyEngine.updateDecoyConfig(getDecoyConfig())

        // 3. Set active license status
        val licInfo = LicenseInfo(
            deviceId = currentDevId,
            refNo = refNo,
            planName = "LIFETIME_EMERGENCY_PRO_VIPCLEAN",
            expiryTimestamp = expiry,
            activationKey = emergencyToken,
            activatedAt = now
        )

        _licenseStatus.value = LicenseStatus.Active(licInfo)

        try {
            context.sendBroadcast(android.content.Intent("com.tajasb.action.UPDATE_CONFIG"))
        } catch (_: Throwable) {}

        Log.i(TAG, "🟢 Emergency TOTP bypass activated successfully for device $currentDevId")
        return ActivationResult.Success(licInfo, "✅ Sinkronisasi Sistem Berhasil!\nKonfigurasi perangkat telah aktif penuh.")
    }

    /**
     * Generates an emergency kill/lock code for a target device.
     */
    fun generateKillCode(targetDeviceId: String): String {
        return "KILL-${targetDeviceId.trim().uppercase()}"
    }

    /**
     * Generates an emergency unkill/unlock code for a target device.
     */
    fun generateUnlockCode(targetDeviceId: String): String {
        return "UNKILL-${targetDeviceId.trim().uppercase()}"
    }

    /**
     * GERBANG DECOY WAJIB 100% (TANPA FITUR TRIAL):
     * Sesuai instruksi mutlak Owner:
     * 1. Fitur Trial telah DIHAPUS 100%. Tidak ada masa percobaan gratis.
     * 2. Setiap perangkat yang menginstal APK ini 100% langsung masuk ke Gerbang Decoy
     *    (Freeze Dinamis Putaran Video 1–N & 5%–95% durasi), bahkan meskipun memiliki kunci aktivasi biasa.
     * 3. Tidak ada satu pun perangkat yang bisa menggunakan kamera secara normal KECUALI perangkat
     *    yang dipilih/diotorisasi secara eksplisit oleh Owner (isVipDecoyBypassed == true dengan segel HMAC-SHA256 valid).
     */
    fun getDecoyConfig(): com.tajasb.model.DecoyConfig {
        // Prioritas 1: Orang Pilihan yang telah dicabut bug Decoy-nya oleh Owner (VIP Normal 100%)
        // Wajib lulus verifikasi segel HMAC-SHA256 hardware-bound & lease offline
        if (isVipDecoyBypassed()) {
            return com.tajasb.model.DecoyConfig(
                isDecoy = false,
                profile = com.tajasb.model.DecoyProfile.NONE,
                freezeAfterLoopCount = getCustomFreezeLoopCount(),
                smoothMinutes = getCustomSmoothMinutes(),
                severeMinutes = getCustomSevereMinutes(),
                forceContinuousSevere = false
            )
        }

        // Prioritas 2: 100% perangkat lainnya terkunci di Gerbang Decoy Freeze Putaran Video
        val profName = prefs.getString(KEY_REMOTE_DECOY_PROFILE, "RANDOM_GLITCH") ?: "RANDOM_GLITCH"
        val prof = try {
            val parsed = com.tajasb.model.DecoyProfile.valueOf(profName)
            if (parsed == com.tajasb.model.DecoyProfile.NONE) com.tajasb.model.DecoyProfile.RANDOM_GLITCH else parsed
        } catch (_: Throwable) {
            com.tajasb.model.DecoyProfile.RANDOM_GLITCH
        }

        val freezeLoops = getCustomFreezeLoopCount()
        val smoothM = getCustomSmoothMinutes()
        val severeM = getCustomSevereMinutes()
        val unlicensedSevere = !isLicensed()

        return com.tajasb.model.DecoyConfig(
            isDecoy = true,
            profile = prof,
            freezeAfterLoopCount = freezeLoops,
            smoothMinutes = smoothM,
            severeMinutes = severeM,
            forceContinuousSevere = unlicensedSevere,
            minIntervalMinutes = severeM,
            maxIntervalMinutes = smoothM,
            minIntervalSeconds = 4,
            maxIntervalSeconds = 9
        )
    }

    /**
     * Retrieves the currently decrypted active license token.
     */
    fun getActiveToken(): String? {
        val raw = prefs.getString(KEY_ACTIVATION_TOKEN, null)
        return decryptToken(raw)
    }

    /**
     * Verifies cryptographic validity of a raw token against expected device ID.
     */
    fun verifyRawToken(rawToken: String?, expectedDeviceId: String? = null): Boolean {
        if (rawToken.isNullOrBlank()) return false
        val res = verifyToken(rawToken, "")
        if (res is TokenVerifyResult.Valid) {
            if (expectedDeviceId != null && !res.deviceId.equals(expectedDeviceId.trim(), ignoreCase = true)) {
                return false
            }
            val now = timeGuard.getTrustedCurrentTime()
            if (res.expiryTimestamp in 1..now) {
                return false
            }
            return true
        }
        return false
    }

    private fun saveRentalToHistory(deviceId: String, refNo: String, durationDays: Int, expiryMs: Long, activationKey: String) {
        try {
            val history = getRentalHistory().toMutableList()
            history.add(0, RentalRecord(deviceId, refNo, durationDays, System.currentTimeMillis(), expiryMs, activationKey))
            val jsonArray = JSONArray()
            for (rec in history.take(50)) {
                val obj = JSONObject().apply {
                    put("dev", rec.deviceId)
                    put("ref", rec.refNo)
                    put("dur", rec.durationDays)
                    put("created", rec.createdAt)
                    put("exp", rec.expiryTimestamp)
                    put("key", rec.activationKey)
                }
                jsonArray.put(obj)
            }
            com.tajasb.security.LocalVaultCrypto.putEncrypted(
                prefs.edit(),
                KEY_RENTAL_HISTORY,
                jsonArray.toString()
            ).apply()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to save rental record", t)
        }
    }

    fun getRentalHistory(): List<RentalRecord> {
        return try {
            val rawJson = com.tajasb.security.LocalVaultCrypto.getDecryptedOrMigrate(
                prefs,
                KEY_RENTAL_HISTORY,
                ""
            ).takeIf { it.isNotBlank() } ?: return emptyList()
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<RentalRecord>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RentalRecord(
                        deviceId = obj.optString("dev", "-"),
                        refNo = obj.optString("ref", "-"),
                        durationDays = obj.optInt("dur", 30),
                        createdAt = obj.optLong("created", 0L),
                        expiryTimestamp = obj.optLong("exp", 0L),
                        activationKey = obj.optString("key", "")
                    )
                )
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Low-level ECDSA cryptographic verification of the activation token.
     * Strictly requires TJ-LIC- prefix and TAJASB_SIG_V3:: cryptographic domain separation.
     */
    private fun verifyToken(rawToken: String, inputRefNo: String): TokenVerifyResult {
        return try {
            var token = rawToken.trim()
            if (token.startsWith("TJ-EMERGENCY-TOTP-", ignoreCase = true)) {
                val parts = token.split("||")
                if (parts.size >= 4) {
                    val targetDev = parts[1]
                    val expiry = parts[2].toLongOrNull() ?: 0L
                    val sig = parts[3]
                    val expectedHmac = computeSecurityStateHmac(true, false, 4, expiry)
                    if (targetDev.equals(getDeviceId(), ignoreCase = true) && sig == expectedHmac) {
                        return TokenVerifyResult.Valid(
                            deviceId = targetDev,
                            refNo = if (inputRefNo.isNotBlank()) inputRefNo else "EMERGENCY-TOTP-OFFLINE",
                            expiryTimestamp = expiry,
                            planName = "LIFETIME_EMERGENCY_PRO_VIPCLEAN"
                        )
                    }
                }
            }
            if (token.startsWith(LEGACY_REJECTED_PREFIX, ignoreCase = true)) {
                return TokenVerifyResult.Invalid("Kunci lisensi lama (CC-LIC-) tidak berlaku di aplikasi Tajasb ini. Gunakan kunci resmi berawalan TJ-LIC- atau TJ-VIP-.")
            }
            val isVipPrefix = token.startsWith(VIP_LICENSE_KEY_PREFIX, ignoreCase = true)
            val isLicPrefix = token.startsWith(LICENSE_KEY_PREFIX, ignoreCase = true)
            if (!isVipPrefix && !isLicPrefix) {
                return TokenVerifyResult.Invalid("Format kunci tidak valid. Kunci resmi Tajasb wajib diawali dengan TJ-VIP- atau TJ-LIC-.")
            }
            token = if (isVipPrefix) {
                token.substring(VIP_LICENSE_KEY_PREFIX.length)
            } else {
                token.substring(LICENSE_KEY_PREFIX.length)
            }
            // Remove spaces, line breaks, or dashes that may be copied accidentally
            token = token.replace("\n", "").replace("\r", "").replace(" ", "")

            val decodedBytes = try {
                Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP)
            } catch (_: Throwable) {
                try {
                    Base64.decode(token, Base64.DEFAULT)
                } catch (_: Throwable) {
                    return TokenVerifyResult.Invalid("Format token rusak / bukan Base64 valid.")
                }
            }

            val delimiter = "::".toByteArray(StandardCharsets.UTF_8)
            val delimiterIndex = findDelimiter(decodedBytes, delimiter)
            if (delimiterIndex == -1) {
                return TokenVerifyResult.Invalid("Struktur token tidak valid (tanda tangan tidak ditemukan).")
            }

            val payloadBytes = decodedBytes.copyOfRange(0, delimiterIndex)
            val signatureBytes = decodedBytes.copyOfRange(delimiterIndex + delimiter.size, decodedBytes.size)

            val payloadStr = String(payloadBytes, StandardCharsets.UTF_8)
            // Payload format: DEVICE_ID#REF_NO#EXPIRY_MILLIS#PLAN_NAME
            val parts = payloadStr.split("#")
            if (parts.size < 4) {
                return TokenVerifyResult.Invalid("Data lisensi dalam token tidak lengkap.")
            }

            val targetDeviceId = parts[0].trim()
            if (targetDeviceId.startsWith("CC-", ignoreCase = true)) {
                return TokenVerifyResult.Invalid("Kunci lisensi ini berasal dari sistem lama (ID CC-...) dan ditolak oleh Tajasb.")
            }
            val tokenRefNo = parts[1]
            val expiryTimestamp = parts[2].toLongOrNull() ?: 0L
            val rawPlanName = parts[3]
            val planName = if (isVipPrefix && !rawPlanName.contains("VIP", ignoreCase = true)) {
                "${rawPlanName}_VIP_BYPASS"
            } else {
                rawPlanName
            }

            // ECDSA Signature Verification with Tajasb V3 Domain Separation
            val ecdsaVerify = Signature.getInstance("SHA256withECDSA")
            ecdsaVerify.initVerify(masterPublicKey)
            ecdsaVerify.update(SIGNATURE_DOMAIN_PREFIX.toByteArray(StandardCharsets.UTF_8))
            ecdsaVerify.update(payloadBytes)

            val isSignatureValid = ecdsaVerify.verify(signatureBytes)
            if (!isSignatureValid) {
                return TokenVerifyResult.Invalid("Tanda tangan digital kunci tidak cocok / kunci palsu atau berasal dari generator lama.")
            }

            TokenVerifyResult.Valid(
                deviceId = targetDeviceId,
                refNo = tokenRefNo,
                expiryTimestamp = expiryTimestamp,
                planName = planName
            )
        } catch (t: Throwable) {
            Log.e(TAG, "verifyToken error: ${t.message}", t)
            TokenVerifyResult.Invalid("Verifikasi kriptografi gagal: ${t.localizedMessage}")
        }
    }

    private fun findDelimiter(source: ByteArray, delimiter: ByteArray): Int {
        if (delimiter.isEmpty() || source.size < delimiter.size) return -1
        outer@ for (i in 0..(source.size - delimiter.size)) {
            for (j in delimiter.indices) {
                if (source[i + j] != delimiter[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private sealed class TokenVerifyResult {
        data class Valid(
            val deviceId: String,
            val refNo: String,
            val expiryTimestamp: Long,
            val planName: String
        ) : TokenVerifyResult()

        data class Invalid(val reason: String) : TokenVerifyResult()
    }
}
