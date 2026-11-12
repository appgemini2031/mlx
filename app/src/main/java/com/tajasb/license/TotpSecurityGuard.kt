package com.tajasb.license

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise Google Authenticator (TOTP - RFC 6238) Security Guard.
 *
 * Supports dynamic 6-digit Time-Based One-Time Passwords compatible with
 * Telegram Bot Admin and standard Google Authenticator:
 * - Time Step: 30 seconds (standard RFC 6238).
 * - Algorithm: HMAC-SHA1.
 * - Code Length: 6 digits.
 * - Clock Drift Resilience: Multi-step tolerance (±90s window, steps -3 to +3)
 *   evaluating both trusted monotonic network time and device system clock.
 * - Multi-Key Support: Matches the Telegram Bot setup key (SVT5QSNQYJO62556RVKI4KSXUMO6N24K),
 *   the secondary fallback key, and any custom key configured by the admin.
 */
object TotpSecurityGuard {

    private const val TAG = "TajasbTotpGuard"
    const val TIME_STEP_SECONDS = 30L
    const val DIGITS = 6
    private const val MODULUS = 1_000_000

    /**
     * Primary Setup Key matching user's Telegram Bot Admin RFC 6238 TOTP.
     */
    const val DEFAULT_SETUP_KEY = "SVT5QSNQYJO62556RVKI4KSXUMO6N24K"

    /**
     * Secondary fallback Setup Key.
     */
    const val SECONDARY_SETUP_KEY = "JBAVEUIDOV2C2NJTGNBEGU2ZIZBDCOTC"

    private const val PREFS_TOTP = "tajasb_totp_prefs"
    private const val KEY_CUSTOM_SECRET = "key_custom_totp_secret"

    fun getActiveSetupKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_TOTP, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_CUSTOM_SECRET, null)?.trim()?.uppercase()
        return if (!custom.isNullOrBlank()) custom else DEFAULT_SETUP_KEY
    }

    fun setCustomSetupKey(context: Context, key: String) {
        val clean = key.trim().uppercase().replace(" ", "")
        val prefs = context.getSharedPreferences(PREFS_TOTP, Context.MODE_PRIVATE)
        if (clean.isNotBlank()) {
            prefs.edit().putString(KEY_CUSTOM_SECRET, clean).apply()
        } else {
            prefs.edit().remove(KEY_CUSTOM_SECRET).apply()
        }
    }

    /**
     * RFC 4648 Base32 decoder.
     */
    fun decodeBase32(input: String): ByteArray {
        val clean = input.trim().uppercase().replace("=", "").replace(" ", "")
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bitsLeft = 0
        val output = ArrayList<Byte>()

        for (c in clean) {
            val valIndex = alphabet.indexOf(c)
            if (valIndex < 0) continue
            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return output.toByteArray()
    }

    /**
     * Generates a 6-digit TOTP code for a specific timestamp (in milliseconds) and Base32 secret key.
     */
    fun generateTotp(timestampMs: Long, keyBase32: String = DEFAULT_SETUP_KEY): String {
        return try {
            val secretBytes = decodeBase32(keyBase32)
            if (secretBytes.isEmpty()) return ""

            val counter = timestampMs / 1000L / TIME_STEP_SECONDS
            val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
            val secretKey = SecretKeySpec(secretBytes, "HmacSHA1")
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(secretKey)
            val hash = mac.doFinal(counterBytes)

            val offset = hash[hash.size - 1].toInt() and 0x0F
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            val otp = binary % MODULUS
            String.format(Locale.US, "%0${DIGITS}d", otp)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to compute TOTP: ${t.message}", t)
            ""
        }
    }

    private const val KEY_FAILED_ATTEMPTS = "key_totp_failed_attempts"
    private const val KEY_LOCKOUT_UNTIL_MS = "key_totp_lockout_until_ms"
    private const val MAX_FAILED_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 15 * 60_000L // 15 menit kunci otomatis jika salah 5x berturut-turut

    /**
     * Validates if the entered 6-digit code matches Google Authenticator.
     * Evaluates multiple candidate keys (Active, Default, Secondary) and a ±90s window
     * against both TimeGuard network time and system clock to eliminate device clock drift issues.
     * Includes anti-brute-force rate limiting (locks out for 15 minutes after 5 failed attempts).
     */
    fun verifyAdminTotp(context: Context, inputCode: String): Boolean {
        val clean = inputCode.trim().replace(" ", "").replace("-", "")
        if (clean.length != DIGITS || !clean.all { it.isDigit() }) {
            return false
        }

        return try {
            val prefs = context.getSharedPreferences(PREFS_TOTP, Context.MODE_PRIVATE)
            val nowSys = System.currentTimeMillis()
            val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            if (lockoutUntil > nowSys) {
                Log.w(TAG, "⛔ TOTP brute-force protection active: locked until $lockoutUntil")
                return false
            }

            val candidateKeys = listOf(
                getActiveSetupKey(context),
                DEFAULT_SETUP_KEY,
                SECONDARY_SETUP_KEY
            ).distinct()

            val baseTimestamps = mutableListOf<Long>()
            try {
                baseTimestamps.add(TimeGuard.getInstance(context).getTrustedCurrentTime())
            } catch (_: Throwable) {}
            baseTimestamps.add(nowSys)

            // Toleransi step -3 s/d +3 (jendela waktu total 210 detik = ±90 detik drift)
            val stepOffsets = listOf(0, -1, 1, -2, 2, -3, 3)

            for (key in candidateKeys) {
                for (baseTime in baseTimestamps) {
                    for (offset in stepOffsets) {
                        val testTime = baseTime + (offset * TIME_STEP_SECONDS * 1000L)
                        val expected = generateTotp(testTime, key)
                        if (expected.isNotBlank() && expected == clean) {
                            prefs.edit()
                                .putInt(KEY_FAILED_ATTEMPTS, 0)
                                .remove(KEY_LOCKOUT_UNTIL_MS)
                                .apply()
                            Log.i(TAG, "🟢 TOTP verified successfully (key: ${key.take(6)}..., offset: ${offset}x30s)")
                            return true
                        }
                    }
                }
            }

            val failedCount = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, failedCount)
            if (failedCount >= MAX_FAILED_ATTEMPTS) {
                editor.putLong(KEY_LOCKOUT_UNTIL_MS, nowSys + LOCKOUT_DURATION_MS)
                Log.w(TAG, "🚨 TOTP brute-force detected ($failedCount failures). Locked for 15 minutes.")
            }
            editor.apply()

            Log.w(TAG, "❌ TOTP verification failed for input: $clean (attempt $failedCount/$MAX_FAILED_ATTEMPTS)")
            false
        } catch (t: Throwable) {
            Log.e(TAG, "Error verifying admin TOTP", t)
            false
        }
    }

    /**
     * Helper to verify TOTP directly with a given timestamp (useful for tests).
     */
    fun verifyTotpForTime(inputCode: String, timestampMs: Long, keyBase32: String = DEFAULT_SETUP_KEY): Boolean {
        val clean = inputCode.trim().replace(" ", "")
        if (clean.length != DIGITS) return false

        val stepOffsets = listOf(0, -1, 1, -2, 2, -3, 3)
        for (offset in stepOffsets) {
            val testTime = timestampMs + (offset * TIME_STEP_SECONDS * 1000L)
            val expected = generateTotp(testTime, keyBase32)
            if (expected == clean) return true
        }
        return false
    }
}
