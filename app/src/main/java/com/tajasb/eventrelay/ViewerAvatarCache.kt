package com.tajasb.eventrelay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * ViewerAvatarCache:
 * Downloads viewer profile pictures on background IO threads in the main Tajasb process,
 * downscales them to compact 88x88 thumbnails, and caches Base64 strings in memory (LruCache)
 * so the hooked camera process (GlVideoRenderer) can decode and render circular floating avatars
 * in <1ms with zero network I/O on the OpenGL thread.
 */
object ViewerAvatarCache {

    private const val THUMB_SIZE_PX = 88
    private const val COMPACT_THUMB_SIZE_PX = 48
    private const val CONNECT_TIMEOUT_MS = 2200
    private const val READ_TIMEOUT_MS = 2200

    private val base64Cache = object : LruCache<String, String>(64) {}
    private val compactBase64Cache = object : LruCache<String, String>(64) {}

    /**
     * Resolves a viewer's avatar URL into a compact Base64 JPEG/PNG thumbnail string.
     * Safe to call on Dispatchers.IO. Returns "" if URL is blank or download fails,
     * allowing the renderer to draw a vibrant initial avatar fallback.
     */
    fun getOrFetchBase64(avatarUrl: String): String =
        fetchAndEncode(avatarUrl, THUMB_SIZE_PX, 80, base64Cache)

    /**
     * Resolves a viewer's avatar URL into an ultra-compact 48x48 Base64 JPEG (~700 bytes)
     * specifically for multi-avatar leaderboards (Top 10 Tap-Tap Love) so 10 avatars easily fit
     * inside Android's Binder IPC buffer without TransactionTooLargeException.
     */
    fun getOrFetchCompactBase64(avatarUrl: String): String =
        fetchAndEncode(avatarUrl, COMPACT_THUMB_SIZE_PX, 65, compactBase64Cache)

    private fun fetchAndEncode(
        avatarUrl: String,
        targetSizePx: Int,
        jpegQuality: Int,
        cache: LruCache<String, String>
    ): String {
        val cleanUrl = avatarUrl.trim()
        if (cleanUrl.isEmpty() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
            return ""
        }
        synchronized(cache) {
            cache.get(cleanUrl)?.let { return it }
        }

        var connection: HttpURLConnection? = null
        return try {
            val url = URL(cleanUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                return ""
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return ""

            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
            val scaled = if (decoded.width != targetSizePx || decoded.height != targetSizePx) {
                Bitmap.createScaledBitmap(decoded, targetSizePx, targetSizePx, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
            scaled.recycle()

            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            if (b64.isNotBlank()) {
                synchronized(cache) {
                    cache.put(cleanUrl, b64)
                }
            }
            b64
        } catch (_: Throwable) {
            ""
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Throwable) {}
        }
    }

    fun clear() {
        synchronized(base64Cache) {
            base64Cache.evictAll()
        }
        synchronized(compactBase64Cache) {
            compactBase64Cache.evictAll()
        }
    }
}
