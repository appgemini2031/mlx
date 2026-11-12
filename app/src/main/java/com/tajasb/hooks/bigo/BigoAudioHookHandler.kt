package com.tajasb.hooks.bigo

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * BigoAudioHookHandler:
 * Mengambil file audio virtual khusus BIGO Live secara terisolasi melalui
 * VcamContentProvider (content://com.tajasb.provider/bigo/audio.mp3) tanpa pernah
 * membaca atau melakukan fallback ke folder/konfigurasi audio milik TikTok.
 */
object BigoAudioHookHandler {
    private const val TAG = "Tajasb-BIGO"

    private val audioLock = Any()

    @Volatile
    private var lastAudioVersion: Long = 0L

    fun getVirtualAudioFile(
        appContext: Context?,
        authorities: List<String>,
        forceRefresh: Boolean = false,
        configVersion: Long = 0L
    ): File? = synchronized(audioLock) {
        val ctx = appContext ?: return null
        val cacheOut = File(ctx.cacheDir, "virtual_vcam_bigo_audio.mp3")
        val tempOut = File(ctx.cacheDir, "temp_bigo_audio.mp3")

        val shouldFetch = forceRefresh ||
            !cacheOut.exists() ||
            cacheOut.length() <= 0L ||
            (configVersion > 0L && configVersion != lastAudioVersion)

        if (!shouldFetch) {
            return cacheOut
        }

        for (auth in authorities) {
            val uri = Uri.parse("content://$auth/bigo/audio.mp3")
            try {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    try { tempOut.delete() } catch (_: Throwable) {}
                    FileOutputStream(tempOut).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                        output.flush()
                    }
                    if (tempOut.exists() && tempOut.length() > 0L) {
                        try { cacheOut.delete() } catch (_: Throwable) {}
                        if (tempOut.renameTo(cacheOut)) {
                            if (configVersion > 0L) lastAudioVersion = configVersion
                            Log.i(TAG, "Audio BIGO tersalin dari provider ($auth): ${cacheOut.length()} bytes")
                            return cacheOut
                        }
                    }
                }
            } catch (_: Throwable) {
                // Provider belum memiliki audio BIGO atau authority berbeda
            }
        }

        return if (cacheOut.exists() && cacheOut.length() > 0L) cacheOut else null
    }

    fun purgeCachedAudio(appContext: Context?) {
        val ctx = appContext ?: return
        try {
            File(ctx.cacheDir, "virtual_vcam_bigo_audio.mp3").delete()
            File(ctx.cacheDir, "temp_bigo_audio.mp3").delete()
            lastAudioVersion = 0L
        } catch (_: Throwable) {}
    }
}
