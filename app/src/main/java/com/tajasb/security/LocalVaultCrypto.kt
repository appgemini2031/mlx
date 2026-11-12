package com.tajasb.security

import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enkripsi berlapis untuk penyimpanan data sensitif lokal (Token Telegram, Chat ID,
 * Token Aktivasi Lisensi, Riwayat Sewa, dan Dynamic Firebase Nodes) di SharedPreferences
 * maupun file backup lokal.
 *
 * Arsitektur Enkripsi:
 * 1. Lapisan Utama (`AKS1:`): Menggunakan Android KeyStore Hardware-Backed AES-256-GCM
 *    (`KeyGenParameterSpec` di dalam `AndroidKeyStore`) yang kuncinya tidak pernah keluar
 *    dari TEE/Secure Element perangkat.
 * 2. Lapisan Hardware-Bound (`HWGCM1:` & kompatibilitas mundur `AESGCM:`): Menggunakan
 *    derivasi SHA-256 berlapis dari identitas fisik perangkat untuk file backup lintas-reinstall
 *    (`.tajasb_license.key`) atau fallback jika proses berjalan di lingkungan khusus.
 * 3. Migrasi Otomatis: Data lama yang masih berupa plaintext atau format lama otomatis
 *    dibaca tanpa kehilangan data dan langsung di-upgrade ke format terenkripsi di disk.
 */
object LocalVaultCrypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "tajasb_local_vault_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    private const val PREFIX_KEYSTORE_V1 = "AKS1:"
    private const val PREFIX_HW_GCM_V1 = "HWGCM1:"
    private const val PREFIX_LEGACY_AESGCM = "AESGCM:"

    @Volatile
    private var cachedKeyStoreKey: SecretKey? = null

    /**
     * Mengambil atau membuat SecretKey AES-256-GCM di dalam Android KeyStore.
     */
    @Synchronized
    private fun getOrCreateKeyStoreKey(): SecretKey? {
        cachedKeyStoreKey?.let { return it }
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingEntry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                cachedKeyStoreKey = existingEntry.secretKey
                return existingEntry.secretKey
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            val generated = keyGenerator.generateKey()
            cachedKeyStoreKey = generated
            generated
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Kunci AES-256 deterministik berbasis sidik perangkat keras (untuk file backup
     * lintas-reinstall atau fallback ketika KeyStore tidak tersedia).
     */
    private fun deriveHardwareKey(pepper: String): SecretKeySpec {
        val rawSeed = "${Build.BOARD}|${Build.BRAND}|${Build.DEVICE}|${Build.HARDWARE}|${Build.MANUFACTURER}|${Build.MODEL}|$pepper"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawSeed.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun deriveLegacyLicenseKey(): SecretKeySpec {
        val seed = "${Build.FINGERPRINT}|${Build.BOARD}|${Build.HARDWARE}|TAJASB_V9_AES_GCM_KEY"
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Mengecek apakah string sudah berada dalam format terenkripsi.
     */
    fun isEncryptedPayload(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.startsWith(PREFIX_KEYSTORE_V1) ||
            value.startsWith(PREFIX_HW_GCM_V1) ||
            value.startsWith(PREFIX_LEGACY_AESGCM)
    }

    /**
     * Mengenkripsi string sensitif untuk disimpan di SharedPreferences.
     * Mengutamakan Android KeyStore (`AKS1:`), dengan fallback ke Hardware-Bound AES-256-GCM (`HWGCM1:`).
     */
    fun encryptForPrefs(plainText: String): String {
        if (plainText.isEmpty()) return ""
        if (plainText.startsWith(PREFIX_KEYSTORE_V1)) return plainText

        // 1. Coba enkripsi dengan Android KeyStore AES-256-GCM
        try {
            val ksKey = getOrCreateKeyStoreKey()
            if (ksKey != null) {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, ksKey)
                val iv = cipher.iv
                val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val combined = ByteArray(1 + iv.size + encrypted.size)
                combined[0] = iv.size.toByte()
                System.arraycopy(iv, 0, combined, 1, iv.size)
                System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
                return PREFIX_KEYSTORE_V1 + Base64.encodeToString(combined, Base64.NO_WRAP)
            }
        } catch (_: Throwable) {
            // Lanjut ke fallback hardware-bound jika KeyStore gagal
        }

        // 2. Fallback ke Hardware-Bound AES-256-GCM
        return encryptHardwareBound(plainText)
    }

    /**
     * Mendekripsi string dari SharedPreferences.
     * Mendukung `AKS1:` (Android KeyStore), `HWGCM1:` (Hardware-Bound v1),
     * `AESGCM:` (Legacy LicenseManager), serta plaintext lama (untuk migrasi mulus).
     */
    fun decryptFromPrefs(storedValue: String?): String {
        if (storedValue.isNullOrEmpty()) return ""

        if (storedValue.startsWith(PREFIX_KEYSTORE_V1)) {
            return try {
                val ksKey = getOrCreateKeyStoreKey() ?: return ""
                val raw = Base64.decode(
                    storedValue.removePrefix(PREFIX_KEYSTORE_V1),
                    Base64.NO_WRAP
                )
                if (raw.size <= 2) return ""
                val ivLen = raw[0].toInt() and 0xFF
                if (ivLen <= 0 || raw.size <= 1 + ivLen) return ""
                val iv = raw.copyOfRange(1, 1 + ivLen)
                val cipherBytes = raw.copyOfRange(1 + ivLen, raw.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, ksKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
            } catch (_: Throwable) {
                ""
            }
        }

        return decryptHardwareBound(storedValue)
    }

    /**
     * Mengenkripsi string menggunakan kunci Hardware-Bound AES-256-GCM (`HWGCM1:`).
     * Cocok untuk file backup lokal (`.tajasb_license.key`) yang harus tetap bisa
     * dipulihkan setelah reinstall APK di perangkat fisik yang sama.
     */
    fun encryptHardwareBound(plainText: String): String {
        if (plainText.isEmpty()) return ""
        if (plainText.startsWith(PREFIX_HW_GCM_V1)) return plainText
        return try {
            val keySpec = deriveHardwareKey("TAJASB_VAULT_HW_V1")
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            PREFIX_HW_GCM_V1 + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Throwable) {
            plainText
        }
    }

    /**
     * Mendekripsi string yang dienkripsi dengan `HWGCM1:` atau legacy `AESGCM:`,
     * serta mengembalikan plaintext apa adanya jika belum terenkripsi (migrasi mundur).
     */
    fun decryptHardwareBound(cipherText: String?): String {
        if (cipherText.isNullOrEmpty()) return ""

        if (cipherText.startsWith(PREFIX_HW_GCM_V1)) {
            return try {
                val raw = Base64.decode(
                    cipherText.removePrefix(PREFIX_HW_GCM_V1),
                    Base64.NO_WRAP
                )
                if (raw.size <= GCM_IV_LENGTH) return ""
                val iv = raw.copyOfRange(0, GCM_IV_LENGTH)
                val encrypted = raw.copyOfRange(GCM_IV_LENGTH, raw.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    deriveHardwareKey("TAJASB_VAULT_HW_V1"),
                    GCMParameterSpec(GCM_TAG_BITS, iv)
                )
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            } catch (_: Throwable) {
                ""
            }
        }

        if (cipherText.startsWith(PREFIX_LEGACY_AESGCM)) {
            return try {
                val raw = Base64.decode(
                    cipherText.removePrefix(PREFIX_LEGACY_AESGCM),
                    Base64.NO_WRAP
                )
                if (raw.size <= GCM_IV_LENGTH) return ""
                val iv = raw.copyOfRange(0, GCM_IV_LENGTH)
                val encrypted = raw.copyOfRange(GCM_IV_LENGTH, raw.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    deriveLegacyLicenseKey(),
                    GCMParameterSpec(GCM_TAG_BITS, iv)
                )
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            } catch (_: Throwable) {
                ""
            }
        }

        // Plaintext lama: kembalikan langsung agar migrasi otomatis berjalan mulus
        return cipherText
    }

    /**
     * Mengubah payload terenkripsi KeyStore (`AKS1:`) menjadi enkripsi Hardware-Bound (`HWGCM1:`)
     * saat diekspor ke file backup agar tetap terenkripsi di dalam file backup namun tetap bisa
     * didekripsi setelah pengguna melakukan reinstall APK di perangkat yang sama.
     */
    fun rewrapForBackup(storedOrPlain: String): String {
        if (storedOrPlain.isEmpty()) return ""
        val plain = decryptFromPrefs(storedOrPlain)
        if (plain.isEmpty()) return storedOrPlain
        return encryptHardwareBound(plain)
    }

    /**
     * Membaca string terenkripsi dari SharedPreferences dan otomatis memigrasikan
     * nilai lama (plaintext atau legacy `AESGCM:`) ke enkripsi KeyStore (`AKS1:`) di latar belakang.
     */
    fun getDecryptedOrMigrate(
        prefs: SharedPreferences,
        key: String,
        defaultValue: String = ""
    ): String {
        val raw = prefs.getString(key, null) ?: return defaultValue
        if (raw.isEmpty()) return defaultValue

        val decrypted = decryptFromPrefs(raw)
        if (decrypted.isEmpty()) return defaultValue

        // Jika di disk belum menggunakan enkripsi KeyStore terbaru (misal masih plaintext atau legacy),
        // migrasikan otomatis ke bentuk terenkripsi di SharedPreferences.
        if (!raw.startsWith(PREFIX_KEYSTORE_V1)) {
            val reEncrypted = encryptForPrefs(decrypted)
            if (reEncrypted.isNotEmpty() && reEncrypted != raw) {
                prefs.edit().putString(key, reEncrypted).apply()
            }
        }
        return decrypted
    }

    /**
     * Menyimpan string sensitif ke SharedPreferences.Editor dalam keadaan terenkripsi.
     */
    fun putEncrypted(
        editor: SharedPreferences.Editor,
        key: String,
        plainValue: String
    ): SharedPreferences.Editor {
        val encrypted = if (plainValue.isEmpty()) "" else encryptForPrefs(plainValue)
        return editor.putString(key, encrypted)
    }
}
