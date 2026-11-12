package com.tajasb.remote

import android.util.Base64
import android.util.Log
import com.tajasb.license.LicenseManager
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Validates cryptographic signatures for remote commands sent from Telegram.
 * Guarantees zero-tamper security: commands cannot be spoofed without the ECDSA private key.
 */
object RemoteCommandValidator {
    private const val TAG = "RemoteCmdValidator"
    private const val MAX_COMMAND_AGE_SEC = 900L // 15 menit batas anti-replay ketat untuk perintah remote

    private fun logW(msg: String) {
        try { Log.w(TAG, msg) } catch (_: Throwable) { println(msg) }
    }

    /**
     * Verifies the cryptographic signature of a remote command.
     * Payload format: "CMD:<action>:<targetDeviceId>:<timestampSec>"
     */
    fun verifySignature(
        action: String,
        targetDeviceId: String,
        timestampSec: Long,
        signatureBase64: String
    ): Boolean {
        if (action.isBlank() || targetDeviceId.isBlank() || signatureBase64.isBlank()) {
            logW("Gagal validasi: Parameter perintah kosong")
            return false
        }

        // Cek replay attack (untuk non-ACTIVATE maksimal 7 hari; untuk ACTIVATE token TJ-LIC sudah memiliki expiry sendiri)
        val nowSec = System.currentTimeMillis() / 1000L
        if (!action.equals("ACTIVATE", ignoreCase = true) && Math.abs(nowSec - timestampSec) > MAX_COMMAND_AGE_SEC) {
            logW("Perintah ditolak: Timestamp kedaluwarsa ($timestampSec vs now $nowSec)")
            return false
        }

        return try {
            val payload = "CMD:${action.trim()}:${targetDeviceId.trim().uppercase()}:$timestampSec"
                .toByteArray(Charsets.UTF_8)

            val sigBytes = try {
                java.util.Base64.getDecoder().decode(signatureBase64.trim())
            } catch (_: Throwable) {
                try {
                    android.util.Base64.decode(signatureBase64.trim(), android.util.Base64.DEFAULT)
                } catch (_: Throwable) {
                    return false
                }
            }

            val pubKeyBytes = try {
                java.util.Base64.getDecoder().decode(LicenseManager.MASTER_PUBLIC_KEY_B64.trim())
            } catch (_: Throwable) {
                try {
                    android.util.Base64.decode(LicenseManager.MASTER_PUBLIC_KEY_B64.trim(), android.util.Base64.DEFAULT)
                } catch (_: Throwable) {
                    return false
                }
            }

            val keySpec = X509EncodedKeySpec(pubKeyBytes)
            val kf = KeyFactory.getInstance("EC")
            val pubKey = kf.generatePublic(keySpec)

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(pubKey)
            verifier.update(payload)
            val isValid = verifier.verify(sigBytes)

            try {
                if (!isValid) {
                    Log.e(TAG, "TANDA TANGAN PERINTAH TIDAK VALID! Potensi serangan manipulasi payload.")
                } else {
                    Log.i(TAG, "Verifikasi kriptografi sukses: Perintah '$action' terkonfirmasi sah dari Admin.")
                }
            } catch (_: Throwable) {
                if (!isValid) println("TANDA TANGAN TIDAK VALID: $action")
            }
            isValid
        } catch (t: Throwable) {
            try {
                Log.e(TAG, "Kesalahan saat memverifikasi tanda tangan ECDSA: ${t.message}", t)
            } catch (_: Throwable) {
                println("Error verify: ${t.message}")
            }
            false
        }
    }
}
