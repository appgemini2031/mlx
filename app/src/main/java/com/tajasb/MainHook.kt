@file:Suppress("DEPRECATION")

package com.tajasb

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.Matrix as GlMatrix
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.widget.Toast
import com.tajasb.debug.LogLevel
import com.tajasb.debug.VcamDebugLogger
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Tajasb Pro / Universal Xposed / LSPosed / EdXposed Hook.
 * Intercepts Android Camera1, Camera2, and CameraX across TikTok, Instagram,
 * WhatsApp, Telegram, Banking, and system camera applications.
 *
 * Supports real-time Rotation (0°, 90°, 180°, 270°), Horizontal Mirror (Selfie Flip),
 * Vertical Flip, Live Pause, Proportional Scaling, and Audio stream injection.
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        const val TAG = "TajasbCam"

        fun hookLog(level: LogLevel, tag: String, msg: String, err: Throwable? = null) {
            try {
                VcamDebugLogger.log(level, tag, msg, err, appContext)
            } catch (_: Throwable) {}
        }

        @Volatile
        private var currentGlRenderer: GlVideoRenderer? = null

        @Volatile
        var liveDecoyConfig: com.tajasb.model.DecoyConfig = com.tajasb.model.DecoyConfig(
            isDecoy = true,
            profile = com.tajasb.model.DecoyProfile.RANDOM_GLITCH,
            isMochiBlind = false
        )

        @Volatile
        private var fallbackMediaPlayer: MediaPlayer? = null

        @Volatile
        var liveRotation: Int = 0

        @Volatile
        var liveMirror: Boolean = false

        @Volatile
        var liveFlipV: Boolean = false

        @Volatile
        var liveZoomFactor: Float = 1.0f

        @Volatile
        var livePause: Boolean = false

        @Volatile
        var liveVideoAudioMuted: Boolean = false

        @Volatile
        var liveVideoVolume: Float = 1.0f

        @Volatile
        var lastSavedPlaybackPositionMs: Int = 0

        @Volatile
        var lastPlayedVideoPath: String? = null

        @Volatile
        var liveMp3AudioPaused: Boolean = true

        @Volatile
        var liveMuted: Boolean = false

        @Volatile
        var liveMasterEnabled: Boolean = true

        @Volatile
        var liveScaleMode: com.tajasb.model.ScaleMode = com.tajasb.model.ScaleMode.CROP

        @Volatile
        var liveAutoNextEnabled: Boolean = false

        @Volatile
        var liveAutoNextMode: com.tajasb.model.AutoNextMode = com.tajasb.model.AutoNextMode.SEQUENTIAL

        @Volatile
        var livePendingVideoPath: String? = null

        @Volatile
        var livePendingVideoIndex: Int? = null

        @Volatile
        var liveLightweightMode: Boolean = false

        @Volatile
        var liveDebugOsdEnabled: Boolean = false

        @Volatile
        var liveAntiHashMode: com.tajasb.model.AntiHashIntensity = com.tajasb.model.AntiHashIntensity.OFF

        @Volatile
        var liveVideoAntiHashPercent: Float = 1.5f

        @Volatile
        var liveAudioAntiHashEnabled: Boolean = true

        @Volatile
        var liveAudioAntiHashMode: com.tajasb.model.AudioAntiHashIntensity = com.tajasb.model.AudioAntiHashIntensity.MEDIUM

        @Volatile
        var liveSessionDnaEnabled: Boolean = false

        @Volatile
        var liveDnaZoomEnabled: Boolean = false

        @Volatile
        var liveSessionDnaCustomZoom: Float = 1.08f

        @Volatile
        var liveSessionDnaPanX: Float = 0.0f

        @Volatile
        var liveSessionDnaPanY: Float = 0.0f

        @Volatile
        var liveSessionDnaIndex: Int = 1

        @Volatile
        var liveSessionDnaSeed: Long = 1001L

        @Volatile
        var liveFps: Int = 30

        @Volatile
        var liveCurrentVideoIndex: Int = 0

        @Volatile
        var liveMasterSpillMode: Boolean = false

        @Volatile
        var liveMasterIndex: Int = 0

        @Volatile
        var liveMasterVideoId: String = ""

        @Volatile
        var liveIsSpillPlaying: Boolean = false

        @Volatile
        var liveBridgeCycleMode: Boolean = false

        @Volatile
        var liveBridgePhase: String = "OFF"

        @Volatile
        var liveBridge1Index: Int = 0

        @Volatile
        var liveBridge2Index: Int = 0

        @Volatile
        var liveIfsMode: Boolean = false

        @Volatile
        var liveIfsLooping: Boolean = false

        @Volatile
        var liveIfsPhase: String = "ABB"

        @Volatile
        var liveIfsVt: Int = 1

        @Volatile
        var liveBridgeHasPending: Boolean = false

        @Volatile
        var liveLiveStreamActive: Boolean = false

        @Volatile
        var liveLiveStreamUrl: String = ""

        @Volatile
        var liveVideoDisplayName: String = ""

        private fun naturalCompare(s1: String, s2: String): Int {
            val regex = "(\\d+)|(\\D+)".toRegex()
            val tokens1 = regex.findAll(s1).map { it.value }.toList()
            val tokens2 = regex.findAll(s2).map { it.value }.toList()
            val minLen = minOf(tokens1.size, tokens2.size)
            for (i in 0 until minLen) {
                val t1 = tokens1[i]
                val t2 = tokens2[i]
                val n1 = t1.toLongOrNull()
                val n2 = t2.toLongOrNull()
                val cmp = if (n1 != null && n2 != null) n1.compareTo(n2) else t1.compareTo(t2, ignoreCase = true)
                if (cmp != 0) return cmp
            }
            return tokens1.size.compareTo(tokens2.size)
        }

        @Volatile
        var lastVideoTimestamp: Long = 0L

        @Volatile
        var lastAudioTimestamp: Long = 0L

        @Volatile
        private var lastBannerEventKey: String = ""

        @Volatile
        private var lastBannerEventTime: Long = 0L

        @Volatile
        var lastLiveBroadcastTime: Long = 0L

        @Volatile
        var lastProgressBroadcastTime: Long = 0L

        @Volatile
        private var watcherThreadStarted = false

        @Volatile
        private var receiverRegistered = false

        @Volatile
        private var activeSurface: Surface? = null

        @Volatile
        private var lastTexture: SurfaceTexture? = null

        @Volatile
        private var isPreparing: Boolean = false

        @Volatile
        private var isPlaying: Boolean = false

        @Volatile
        private var lastVideoPath: String? = null

        @Volatile
        private var lastAudioPath: String? = null

        @Volatile
        var appContext: Context? = null

        private var toastShown: Boolean = false
        private val mainHandler = Handler(Looper.getMainLooper())

        @Volatile
        var liveAutoNextLeadSeconds: Int = 2
        @Volatile
        var liveAutoNextLeadMs: Int = 1500

        @Volatile
        private var cachedProviderAuth: String? = null

        private val hookFallbackShuffleDeck = com.tajasb.data.VideoPlaylistRepository.ShuffleDeckManager()

        private val fetchLock = Any()

        fun getKnownAuthorities(ctx: Context? = null): List<String> {
            val list = mutableListOf(
                "com.tajasb.pro.issber.provider",
                "com.tajasb.pro.provider",
                "com.tajasb.provider",
                "com.aistudio.tajasb.provider",
                "com.aistudio.cuancame.provider",
                "com.example.provider"
            )
            cachedProviderAuth?.let { cached ->
                if (!list.contains(cached)) {
                    list.add(0, cached)
                }
            }
            val context = ctx ?: appContext
            if (context != null) {
                try {
                    val providers = context.packageManager.queryContentProviders(null, 0, 0)
                    for (p in providers) {
                        val auth = p.authority ?: continue
                        if (auth.contains("tajasb") || auth.contains("cuancame")) {
                            if (!list.contains(auth)) {
                                list.add(0, auth)
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
            return list
        }

        fun getKnownPackages(ctx: Context? = null): List<String> {
            val list = mutableListOf(
                "com.tajasb.pro.issber",
                "com.tajasb.pro",
                "com.tajasb",
                "com.aistudio.tajasb",
                "com.aistudio.cuancame",
                "com.example"
            )
            ctx?.packageName?.let {
                if (!list.contains(it)) list.add(0, it)
            }
            return list
        }

        @Volatile
        private var lastExtractedVideoPath: String? = null
        @Volatile
        private var lastExtractedVersion: Long = 0L

        fun purgeCachedVideoFiles(ctx: Context?) {
            val context = ctx ?: appContext ?: return
            try {
                releasePreloadedPlayer()
                val cacheDir = context.cacheDir ?: return
                val files = cacheDir.listFiles() ?: return
                for (f in files) {
                    if (f.name.startsWith("cached_playlist_item_") ||
                        f.name.startsWith("cached_vs_rolling_") ||
                        f.name.startsWith("temp_indexed_") ||
                        f.name.startsWith("temp_vs_rolling_") ||
                        f.name.startsWith("virtual_vcam_feed_")) {
                        try { f.delete() } catch (_: Throwable) {}
                    }
                }
                lastExtractedVideoPath = null
                lastExtractedVersion = 0L
                lastPlayedVideoPath = null
                lastSavedPlaybackPositionMs = 0
                lastVideoPath = null
                lastVideoTimestamp = 0L
                livePendingVideoPath = null
                livePendingVideoIndex = null
                cachedProviderAuth = null
                Log.i(TAG, "MainHook: Cache video & state sesi sebelumnya dibersihkan total.")
            } catch (_: Throwable) {}
        }

        /**
         * Safely extracts virtual video from VcamContentProvider across sandbox boundary.
         * Uses double-buffering (virtual_vcam_feed_a.mp4 / virtual_vcam_feed_b.mp4)
         * with atomic temp-file write and rename to guarantee no reader ever sees an incomplete file.
         */
        fun fetchVirtualVideoFromProvider(ctx: Context?, forceRefresh: Boolean = false, targetSegment: String = "virtual.mp4"): File? = synchronized(fetchLock) {
            val context = ctx ?: appContext ?: return null
            try {
                val pCfg = queryProviderConfig(context)
                val providerVer = pCfg?.getLong("version", 0L) ?: 0L
                val providerPath = pCfg?.getString("videoPath")
                val playlistCount = pCfg?.getInt("playlistCount", -1) ?: -1

                val isVersionStale = (providerVer > 0L && providerVer != lastExtractedVersion)
                val isPathChanged = (!providerPath.isNullOrBlank() && lastExtractedVideoPath != null && providerPath != lastExtractedVideoPath)
                if (isVersionStale) {
                    // Jangan hapus file yang sedang diputar oleh active player maupun yang sudah didekode di standbyPlayer!
                    val activeFilePath = currentGlRenderer?.videoFile?.absolutePath
                    val standbyFilePath = currentGlRenderer?.getStandbyFile()?.absolutePath
                    try {
                        context.cacheDir?.listFiles()?.forEach { f ->
                            if ((f.name.startsWith("cached_playlist_item_") || f.name.startsWith("temp_indexed_")) &&
                                f.absolutePath != activeFilePath &&
                                f.absolutePath != standbyFilePath
                            ) {
                                try { f.delete() } catch (_: Throwable) {}
                            }
                        }
                    } catch (_: Throwable) {}
                    lastExtractedVersion = providerVer
                }

                val isIndexedSegment = targetSegment.startsWith("video_") && targetSegment.endsWith(".mp4")
                if (isIndexedSegment) {
                    val requestedIdx = targetSegment.removePrefix("video_").removeSuffix(".mp4").toIntOrNull()
                    if (requestedIdx != null && playlistCount >= 0 && requestedIdx >= playlistCount) {
                        Log.w(TAG, "Requested segment $targetSegment is beyond current playlist ($playlistCount). Refusing stale playback.")
                        return null
                    }
                    val activeGlName = currentGlRenderer?.videoFile?.name ?: ""
                    val curIdxTag = if (activeGlName.startsWith("cached_playlist_item_b_")) "b" else "a"
                    val nextIdxTag = if (curIdxTag == "a") "b" else "a"
                    val curIndexedCache = File(context.cacheDir, "cached_playlist_item_${curIdxTag}_$targetSegment")
                    val nextIndexedCache = File(context.cacheDir, "cached_playlist_item_${nextIdxTag}_$targetSegment")

                    if (!forceRefresh && !isVersionStale) {
                        if (curIndexedCache.exists() && curIndexedCache.length() > 1024L && activeGlName != curIndexedCache.name) {
                            return curIndexedCache
                        }
                        if (nextIndexedCache.exists() && nextIndexedCache.length() > 1024L && activeGlName != nextIndexedCache.name) {
                            return nextIndexedCache
                        }
                        if (curIndexedCache.exists() && curIndexedCache.length() > 1024L) {
                            return curIndexedCache
                        }
                    }
                    val targetIndexedCache = if (activeGlName == curIndexedCache.name || forceRefresh || isVersionStale) nextIndexedCache else curIndexedCache
                    val tempIndexed = File(context.cacheDir, "temp_indexed_$targetSegment")
                    try { tempIndexed.delete() } catch (_: Throwable) {}
                    val authorities = getKnownAuthorities(context)
                    for (auth in authorities) {
                        val contentUri = Uri.parse("content://$auth/$targetSegment")
                        try {
                            context.contentResolver.openInputStream(contentUri)?.use { input ->
                                FileOutputStream(tempIndexed).use { output ->
                                    input.copyTo(output, bufferSize = 128 * 1024)
                                    output.flush()
                                }
                                if (tempIndexed.exists() && tempIndexed.length() > 1024L) {
                                    tempIndexed.setReadable(true, false)
                                    try { targetIndexedCache.delete() } catch (_: Throwable) {}
                                    if (tempIndexed.renameTo(targetIndexedCache)) {
                                        targetIndexedCache.setReadable(true, false)
                                        if (providerVer > 0L) lastExtractedVersion = providerVer
                                        Log.i(TAG, "Indexed playlist item cached: ${targetIndexedCache.name} (${targetIndexedCache.length()} bytes)")
                                        return targetIndexedCache
                                    }
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                    if (curIndexedCache.exists() && curIndexedCache.length() > 1024L) {
                        return curIndexedCache
                    }
                    if (nextIndexedCache.exists() && nextIndexedCache.length() > 1024L) {
                        return nextIndexedCache
                    }
                }

                val isRollingVsSegment = targetSegment.startsWith("vs_rolling_") && targetSegment.endsWith(".mp4")
                if (isRollingVsSegment) {
                    val cachedVsFile = File(context.cacheDir, "cached_$targetSegment")
                    if (!forceRefresh && cachedVsFile.exists() && cachedVsFile.length() > 1024L) {
                        if (providerVer > 0L) lastExtractedVersion = providerVer
                        if (!providerPath.isNullOrBlank()) lastExtractedVideoPath = providerPath
                        return cachedVsFile
                    }
                    // Jika file sudah dipreload oleh prefetchNextVideo dan ukurannya valid, gunakan langsung tanpa menimpa file yang sedang dibuka standbyPlayer!
                    if (cachedVsFile.exists() && cachedVsFile.length() > 1024L) {
                        if (providerVer > 0L) lastExtractedVersion = providerVer
                        if (!providerPath.isNullOrBlank()) lastExtractedVideoPath = providerPath
                        return cachedVsFile
                    }
                    val tempVs = File(context.cacheDir, "temp_$targetSegment")
                    try { tempVs.delete() } catch (_: Throwable) {}
                    val authorities = getKnownAuthorities(context)
                    for (auth in authorities) {
                        val contentUri = Uri.parse("content://$auth/$targetSegment")
                        try {
                            context.contentResolver.openInputStream(contentUri)?.use { input ->
                                FileOutputStream(tempVs).use { output ->
                                    input.copyTo(output, bufferSize = 128 * 1024)
                                    output.flush()
                                }
                                if (tempVs.exists() && tempVs.length() > 1024L) {
                                    tempVs.setReadable(true, false)
                                    try { cachedVsFile.delete() } catch (_: Throwable) {}
                                    if (tempVs.renameTo(cachedVsFile)) {
                                        cachedVsFile.setReadable(true, false)
                                        cachedProviderAuth = auth
                                        if (providerVer > 0L) lastExtractedVersion = providerVer
                                        if (!providerPath.isNullOrBlank()) lastExtractedVideoPath = providerPath
                                        // Bersihkan file cached_vs_rolling_ lama yang tidak sedang dipakai oleh renderer aktif maupun standby
                                        val activeGlFile = currentGlRenderer?.videoFile?.name
                                        try {
                                            context.cacheDir?.listFiles()?.forEach { f ->
                                                if (f.name.startsWith("cached_vs_rolling_") &&
                                                    f.name != cachedVsFile.name &&
                                                    f.name != activeGlFile
                                                ) {
                                                    try { f.delete() } catch (_: Throwable) {}
                                                }
                                            }
                                        } catch (_: Throwable) {}
                                        Log.i(TAG, "Rolling VS segment cached: ${cachedVsFile.name} (${cachedVsFile.length()} bytes)")
                                        return cachedVsFile
                                    }
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }

                val activeGlPath = currentGlRenderer?.videoFile?.name ?: ""
                val standbyGlPath = currentGlRenderer?.getStandbyFile()?.name ?: ""
                // Target next buffer: avoid both active videoFile and preloaded standbyFile
                val nextTag = when {
                    !activeGlPath.contains("_a") && !standbyGlPath.contains("_a") -> "a"
                    !activeGlPath.contains("_b") && !standbyGlPath.contains("_b") -> "b"
                    else -> "c"
                }
                val nextCache = File(context.cacheDir, "virtual_vcam_feed_$nextTag.mp4")
                val curCache = currentGlRenderer?.videoFile?.takeIf { it.name.startsWith("virtual_vcam_feed_") }
                    ?: File(context.cacheDir, if (nextTag == "b") "virtual_vcam_feed_a.mp4" else "virtual_vcam_feed_b.mp4")

                var shouldForce = forceRefresh || targetSegment.startsWith("vs_rolling_") || isVersionStale || isPathChanged
                if (!shouldForce && targetSegment == "virtual.mp4") {
                    if (!providerPath.isNullOrBlank() && providerPath != lastExtractedVideoPath) {
                        shouldForce = true
                    } else if (providerVer > 0L && providerVer != lastExtractedVersion) {
                        shouldForce = true
                    }
                }

                if (!shouldForce && curCache.exists() && curCache.length() > 1024) {
                    return curCache
                }

                val tempCache = File(context.cacheDir, "virtual_vcam_feed_${nextTag}_temp.mp4")
                try { tempCache.delete() } catch (_: Throwable) {}

                val authorities = getKnownAuthorities(context)
                for (auth in authorities) {
                    val contentUri = Uri.parse("content://$auth/$targetSegment")
                    try {
                        context.contentResolver.openInputStream(contentUri)?.use { input ->
                            FileOutputStream(tempCache).use { output ->
                                input.copyTo(output, bufferSize = 128 * 1024)
                                output.flush()
                            }
                            if (tempCache.exists() && tempCache.length() > 1024) {
                                tempCache.setReadable(true, false)
                                try { nextCache.delete() } catch (_: Throwable) {}
                                if (tempCache.renameTo(nextCache)) {
                                    nextCache.setReadable(true, false)
                                    cachedProviderAuth = auth
                                    if (!providerPath.isNullOrBlank()) lastExtractedVideoPath = providerPath
                                    lastExtractedVersion = if (providerVer > 0L) providerVer else System.currentTimeMillis()
                                    Log.i(TAG, "Successfully extracted $targetSegment via ContentProvider ($auth) into ${nextCache.name}: ${nextCache.length()} bytes")
                                    return nextCache
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
                if (curCache.exists() && curCache.length() > 1024) {
                    return curCache
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fetchVirtualVideoFromProvider error: ${t.message}")
            }
            return null
        }

        /**
         * Resolves the active virtual.mp4 video file with priority:
         * 1. Direct file path if directly accessible and readable across processes
         * 2. Live ContentProvider extraction from Tajasb (guarantees exact sync with UI and playlist)
         * 3. Fallback only if ContentProvider is disconnected: Target app scoped folder & public DCIM
         */
        fun getVirtualVideoFile(pkg: String?, forceRefresh: Boolean = false): File? {
            val ctx = appContext
            var directFile: File? = null
            if (ctx != null) {
                try {
                    val cfg = queryProviderConfig(ctx)
                    val directPath = cfg?.getString("videoPath")
                    if (!directPath.isNullOrBlank()) {
                        val f = File(directPath)
                        if (f.exists() && f.canRead() && f.length() > 0L) {
                            directFile = f
                        }
                    }
                } catch (_: Throwable) {}
            }

            // 1. If directFile exists and is readable in target process (same user / public), use it!
            if (directFile != null) {
                return directFile
            }

            // 2. High Priority: Live ContentProvider extraction (ensures exact match with selected video in Tajasb)
            val providerVideo = fetchVirtualVideoFromProvider(ctx, forceRefresh = forceRefresh)
            if (providerVideo != null && providerVideo.exists() && providerVideo.length() > 0L) {
                return providerVideo
            }

            // 3. Fallback only if ContentProvider is unavailable: check DCIM and other candidate paths
            if (!forceRefresh) {
                val candidatePaths = mutableListOf<File>()

                if (!pkg.isNullOrBlank()) {
                    candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/virtual.mp4"))
                    candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/virtual.mp4"))
                    candidatePaths.add(File("/data/user/0/$pkg/files/Camera1/virtual.mp4"))
                    candidatePaths.add(File("/data/data/$pkg/files/Camera1/virtual.mp4"))
                }

                // Tajasb package paths
                val tajasbPkgs = getKnownPackages(appContext)
                for (vPkg in tajasbPkgs) {
                    candidatePaths.add(File("/storage/emulated/0/Android/data/$vPkg/files/Camera1/virtual.mp4"))
                    candidatePaths.add(File("/data/user/0/$vPkg/files/Camera1/virtual.mp4"))
                    candidatePaths.add(File("/data/data/$vPkg/files/Camera1/virtual.mp4"))
                }

                // Public storage standard locations
                candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/virtual.mp4"))
                candidatePaths.add(File("/sdcard/DCIM/Camera1/virtual.mp4"))
                candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/virtual.mp4"))
                candidatePaths.add(File(Environment.getExternalStorageDirectory(), "DCIM/Camera1/virtual.mp4"))
                candidatePaths.add(File("/sdcard/Camera1/virtual.mp4"))
                candidatePaths.add(File("/storage/emulated/0/Camera1/virtual.mp4"))

                for (file in candidatePaths) {
                    try {
                        if (file.exists() && file.canRead() && file.length() > 0) {
                            return file
                        }
                    } catch (_: Throwable) {}
                }
            }

            return null
        }

        /**
         * Resolves virtual audio file (audio.mp3, audio.wav, etc.) with priority:
         * 1. Target app scoped private files folder
         * 2. Direct path specified in audio_path.txt
         * 3. Tajasb app internal directory
         * 4. Public /sdcard/DCIM/Camera1/ & /data/local/tmp/
         * 5. ContentProvider extraction fallback
         */
        fun getActiveGlRenderer(): GlVideoRenderer? = currentGlRenderer

        fun seekActivePlayer(seekMs: Long) {
            currentGlRenderer?.seekTo(seekMs)
            fallbackMediaPlayer?.let { try { it.seekTo(seekMs.toInt()) } catch (_: Throwable) {} }
        }

        fun updateLastVideoTracking(path: String, lastModified: Long) {
            lastVideoPath = path
            lastVideoTimestamp = lastModified
            lastExtractedVideoPath = path
            try {
                val pVer = queryProviderConfig(appContext)?.getLong("version", 0L) ?: 0L
                if (pVer > 0L) {
                    lastExtractedVersion = pVer
                }
            } catch (_: Throwable) {}
        }

        fun updateLastVideoPathOnly(path: String) {
            lastVideoPath = path
            lastExtractedVideoPath = path
        }

        fun onSurfaceTextureAvailable(texture: SurfaceTexture) {
            lastTexture = texture
        }

        fun onStartPreviewRequested(packageName: String) {
            activeSurface?.let { playVideoOnSurface(it, packageName) }
                ?: lastTexture?.let { playVideoOnSurface(Surface(it), packageName) }
        }

        fun onFallbackActiveSurfaceRequested(packageName: String) {
            activeSurface?.let { playVideoOnSurface(it, packageName) }
        }

        fun getVirtualAudioFile(pkg: String?): File? =
            com.tajasb.hook.VirtualAudioHookHandler.getVirtualAudioFile(pkg, appContext)

        fun getVideoFrameBitmap(pkg: String?): Bitmap? =
            com.tajasb.hook.YuvFrameConverter.extractVideoFrameBitmap(getVirtualVideoFile(pkg))

        fun isAudioDisabled(pkg: String?): Boolean =
            com.tajasb.hook.VirtualAudioHookHandler.isAudioDisabled(pkg)

        fun isHookDisabled(pkg: String?): Boolean {
            val candidatePaths = mutableListOf<File>()
            if (!pkg.isNullOrBlank()) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/disable.jpg"))
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/disable.jpg"))
                candidatePaths.add(File("/data/user/0/$pkg/files/Camera1/disable.jpg"))
                candidatePaths.add(File("/data/data/$pkg/files/Camera1/disable.jpg"))
            }
            candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/disable.jpg"))
            candidatePaths.add(File("/sdcard/DCIM/Camera1/disable.jpg"))
            candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/disable.jpg"))
            candidatePaths.add(File("/sdcard/Camera1/disable.jpg"))
            candidatePaths.add(File("/data/local/tmp/disable.jpg"))
            candidatePaths.add(File("/data/local/tmp/Camera1/disable.jpg"))

            val hasDisableMarker = candidatePaths.any {
                try { it.exists() } catch (_: Throwable) { false }
            }
            if (!hasDisableMarker) return false

            // If a valid virtual video file exists, do not disable hook!
            val video = getVirtualVideoFile(pkg)
            if (video != null && video.exists() && video.length() > 0) {
                return false
            }
            return true
        }

        fun isMuted(pkg: String?): Boolean =
            com.tajasb.hook.VirtualAudioHookHandler.isMuted(pkg)

        fun isNoToast(pkg: String?): Boolean {
            val candidatePaths = mutableListOf<File>()
            if (!pkg.isNullOrBlank()) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/no_toast.jpg"))
            }
            candidatePaths.add(File("/sdcard/DCIM/Camera1/no_toast.jpg"))
            candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/no_toast.jpg"))
            return candidatePaths.any {
                try { it.exists() } catch (_: Throwable) { false }
            }
        }

        fun getRotation(pkg: String?): Int? {
            val candidatePaths = mutableListOf<File>()
            if (!pkg.isNullOrBlank()) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/rotation.txt"))
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/rotation.txt"))
            }
            candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/rotation.txt"))
            candidatePaths.add(File("/sdcard/DCIM/Camera1/rotation.txt"))
            candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/rotation.txt"))
            candidatePaths.add(File("/sdcard/Camera1/rotation.txt"))
            candidatePaths.add(File("/data/local/tmp/rotation.txt"))

            for (file in candidatePaths) {
                try {
                    if (file.exists() && file.canRead()) {
                        val text = file.readText().trim()
                        val deg = text.toIntOrNull()
                        if (deg != null) return deg
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun isMirrored(pkg: String?): Boolean? {
            val candidatePaths = mutableListOf<File>()
            if (!pkg.isNullOrBlank()) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/mirror.txt"))
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/mirror.txt"))
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/flip.jpg"))
            }
            candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/mirror.txt"))
            candidatePaths.add(File("/sdcard/DCIM/Camera1/mirror.txt"))
            candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/mirror.txt"))
            candidatePaths.add(File("/sdcard/DCIM/Camera1/flip.jpg"))
            candidatePaths.add(File("/sdcard/Camera1/mirror.txt"))
            candidatePaths.add(File("/data/local/tmp/mirror.txt"))

            for (file in candidatePaths) {
                try {
                    if (file.exists() && file.canRead()) {
                        if (file.name.endsWith(".jpg")) return true
                        val text = file.readText().trim()
                        return text == "1" || text.equals("true", ignoreCase = true)
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun isVerticalFlipped(pkg: String?): Boolean? {
            val candidatePaths = mutableListOf<File>()
            if (!pkg.isNullOrBlank()) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/flip_v.txt"))
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/flip_v.txt"))
            }
            candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/flip_v.txt"))
            candidatePaths.add(File("/sdcard/DCIM/Camera1/flip_v.txt"))
            candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/flip_v.txt"))
            candidatePaths.add(File("/sdcard/Camera1/flip_v.txt"))
            candidatePaths.add(File("/data/local/tmp/flip_v.txt"))

            for (file in candidatePaths) {
                try {
                    if (file.exists() && file.canRead()) {
                        val text = file.readText().trim()
                        return text == "1" || text.equals("true", ignoreCase = true)
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun getZoomFactor(pkg: String?): Float? {
            val candidatePaths = mutableListOf<File>()
            if (!pkg.isNullOrBlank()) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/zoom.txt"))
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/zoom.txt"))
            }
            candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/zoom.txt"))
            candidatePaths.add(File("/sdcard/DCIM/Camera1/zoom.txt"))
            candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/zoom.txt"))
            candidatePaths.add(File("/sdcard/Camera1/zoom.txt"))
            candidatePaths.add(File("/data/local/tmp/zoom.txt"))

            for (file in candidatePaths) {
                try {
                    if (file.exists() && file.canRead()) {
                        val text = file.readText().trim()
                        val z = text.toFloatOrNull()
                        if (z != null && z in 0.3f..5.0f) return z
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun isPaused(pkg: String?): Boolean? {
            val candidatePaths = mutableListOf<File>()
            if (!pkg.isNullOrBlank()) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/pause.jpg"))
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/pause.jpg"))
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/video_paused.txt"))
            }
            candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/pause.jpg"))
            candidatePaths.add(File("/sdcard/DCIM/Camera1/pause.jpg"))
            candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/pause.jpg"))
            candidatePaths.add(File("/sdcard/Camera1/pause.jpg"))
            candidatePaths.add(File("/data/local/tmp/pause.jpg"))
            candidatePaths.add(File("/sdcard/DCIM/Camera1/video_paused.txt"))

            for (file in candidatePaths) {
                try {
                    if (file.exists()) {
                        if (file.name.endsWith(".txt")) {
                            return file.readText().trim() == "1"
                        }
                        return true
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun isAudioPaused(pkg: String?): Boolean? =
            com.tajasb.hook.VirtualAudioHookHandler.isAudioPaused(pkg)

        fun isVideoAudioMuted(pkg: String?): Boolean? =
            com.tajasb.hook.VirtualAudioHookHandler.isVideoAudioMuted(pkg)

        fun getScaleMode(pkg: String?): com.tajasb.model.ScaleMode? {
            val candidatePaths = mutableListOf<File>()
            if (!pkg.isNullOrBlank()) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/scale.txt"))
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/scale.txt"))
            }
            candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/scale.txt"))
            candidatePaths.add(File("/sdcard/DCIM/Camera1/scale.txt"))
            candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/scale.txt"))
            candidatePaths.add(File("/sdcard/Camera1/scale.txt"))
            candidatePaths.add(File("/data/local/tmp/scale.txt"))

            for (file in candidatePaths) {
                try {
                    if (file.exists() && file.canRead()) {
                        val text = file.readText().trim().uppercase()
                        return when (text) {
                            "FIT" -> com.tajasb.model.ScaleMode.FIT
                            "STRETCH" -> com.tajasb.model.ScaleMode.STRETCH
                            "CROP" -> com.tajasb.model.ScaleMode.CROP
                            else -> null
                        }
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun callProviderNextVideo(
            ctx: Context?,
            mode: com.tajasb.model.AutoNextMode,
            completedVideoPath: String? = null
        ): android.os.Bundle? {
            val context = ctx ?: appContext ?: return null
            val callExtras = android.os.Bundle().apply {
                putString("mode", mode.name)
                if (!completedVideoPath.isNullOrBlank()) {
                    putString("completedVideoPath", completedVideoPath)
                }
            }
            val cached = cachedProviderAuth
            if (cached != null) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$cached"),
                        "onVideoCompleted",
                        mode.name,
                        callExtras
                    )
                    if (bundle != null && bundle.getBoolean("success", false)) {
                        return bundle
                    }
                } catch (_: Throwable) {
                    cachedProviderAuth = null
                }
            }
            val authorities = getKnownAuthorities(context)
            for (auth in authorities) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$auth"),
                        "onVideoCompleted",
                        mode.name,
                        callExtras
                    )
                    if (bundle != null && bundle.getBoolean("success", false)) {
                        cachedProviderAuth = auth
                        return bundle
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun callProviderPeekNextVideo(ctx: Context?, mode: com.tajasb.model.AutoNextMode): android.os.Bundle? {
            val context = ctx ?: appContext ?: return null
            val authorities = getKnownAuthorities(context)
            for (auth in authorities) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$auth"),
                        "peekNextVideo",
                        mode.name,
                        android.os.Bundle().apply { putString("mode", mode.name) }
                    )
                    if (bundle != null && bundle.getBoolean("success", false)) {
                        return bundle
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun callProviderSyncVideoIndex(ctx: Context?, targetIndex: Int): android.os.Bundle? {
            val context = ctx ?: appContext ?: return null
            val authorities = getKnownAuthorities(context)
            for (auth in authorities) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$auth"),
                        "syncVideoIndex",
                        "$targetIndex",
                        android.os.Bundle().apply { putInt("index", targetIndex) }
                    )
                    if (bundle != null && bundle.getBoolean("success", false)) {
                        return bundle
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        private val prefetchExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

        fun releasePreloadedPlayer() {
            try {
                currentGlRenderer?.clearStandbyPlayer()
            } catch (_: Throwable) {}
        }

        fun prefetchNextVideo(pkg: String?) {
            val renderer = currentGlRenderer ?: return
            if (!renderer.isRunning) return
            prefetchExecutor.execute {
                try {
                    val activeRenderer = currentGlRenderer ?: return@execute
                    if (!activeRenderer.isRunning) return@execute

                    // 1. Jika ada antrean manual (QUEUE_VIDEO / Spill Tunggu / dsb) dengan path langsung
                    val pendingPath = livePendingVideoPath
                    if (!pendingPath.isNullOrBlank() && !liveIfsMode) {
                        val pFile = File(pendingPath)
                        if (pFile.exists() && pFile.canRead() && pFile.length() > 0L) {
                            activeRenderer.preloadStandbyVideo(pFile, pFile.name)
                            return@execute
                        }
                    }

                    // 2. Peek next video via ContentProvider across all modes (IFS, Bridge, Spill, Standar)
                    val ctx = appContext
                    val queryMode = if (liveAutoNextEnabled) liveAutoNextMode else (getAutoNextMode(pkg) ?: com.tajasb.model.AutoNextMode.SINGLE_LOOP)
                    if (ctx != null) {
                        val bundle = callProviderPeekNextVideo(ctx, queryMode)
                        if (bundle != null && bundle.getBoolean("success", false)) {
                            val isSameSlotLoop = bundle.getBoolean("isSameSlotLoop", false)
                            if (isSameSlotLoop && livePendingVideoPath == null && !liveBridgeHasPending) {
                                // Looping video yang sama di slot aktif (mis. SINGLE_LOOP, BL standby, MASTER_LOOP standby, SV3 VL intermediate)
                                return@execute
                            }
                            val nextIndex = bundle.getInt("nextIndex", -1)
                            val nextVideoName = bundle.getString("videoName") ?: ""
                            val directPath = bundle.getString("videoPath")
                            val directFile = if (!directPath.isNullOrBlank()) File(directPath) else null
                            val nextFile = if (directFile != null && directFile.exists() && directFile.canRead() && directFile.length() > 0L) {
                                directFile
                            } else {
                                val segment = bundle.getString("segment") ?: if (nextIndex >= 0) "video_$nextIndex.mp4" else "virtual.mp4"
                                fetchVirtualVideoFromProvider(ctx, forceRefresh = false, targetSegment = segment)
                            }
                            if (nextFile != null && nextFile.exists() && nextFile.length() > 0L) {
                                val curFile = activeRenderer.videoFile
                                val isIdenticalFile = try {
                                    curFile.canonicalPath == nextFile.canonicalPath && curFile.length() == nextFile.length()
                                } catch (_: Throwable) {
                                    curFile.absolutePath == nextFile.absolutePath
                                }
                                if (!isIdenticalFile) {
                                    activeRenderer.preloadStandbyVideo(
                                        nextFile,
                                        nextVideoName.ifBlank { nextFile.name }
                                    )
                                }
                            }
                            return@execute
                        }
                    }

                    // 3. Fallback disk playlist jika ContentProvider tidak terjangkau (Mode Standar Urut/Acak)
                    if (!liveIfsMode && !liveBridgeCycleMode && !liveMasterSpillMode && queryMode != com.tajasb.model.AutoNextMode.SINGLE_LOOP) {
                        val currentActiveFile = activeRenderer.videoFile
                        val playlist = getPlaylistVideos(pkg, currentActiveFile)
                        if (playlist.size > 1) {
                            val parentDir = currentActiveFile.parentFile ?: File(currentActiveFile.absolutePath).parentFile
                            val currentIdx = if (liveCurrentVideoIndex >= 0) liveCurrentVideoIndex else getCurrentPlaylistIndex(parentDir)
                            val nextIdx = (currentIdx + 1) % playlist.size
                            val nextFile = playlist.getOrNull(nextIdx)
                            if (nextFile != null && nextFile.exists() && nextFile.length() > 0L) {
                                activeRenderer.preloadStandbyVideo(nextFile, nextFile.name)
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        @Volatile
        private var activeCameraSessionStartMs: Long = 0L

        fun queryProviderConfig(ctx: Context?): android.os.Bundle? {
            val context = ctx ?: appContext ?: return null
            val extras = android.os.Bundle().apply {
                putBoolean("isCameraActive", currentGlRenderer?.isRunning == true || isPlaying)
            }
            val cached = cachedProviderAuth
            if (cached != null) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$cached"),
                        "getConfig",
                        null,
                        extras
                    )
                    if (bundle != null) return bundle
                } catch (_: Throwable) {
                    cachedProviderAuth = null
                }
            }
            val authorities = getKnownAuthorities(context)
            for (auth in authorities) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$auth"),
                        "getConfig",
                        null,
                        extras
                    )
                    if (bundle != null) {
                        cachedProviderAuth = auth
                        return bundle
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun notifyProviderCameraSessionStarted(ctx: Context?, pkg: String?): android.os.Bundle? {
            val context = ctx ?: appContext ?: return null
            val extras = android.os.Bundle().apply {
                putString("package", pkg ?: "")
            }
            val authorities = listOfNotNull(cachedProviderAuth) + getKnownAuthorities(context)
            for (auth in authorities.distinct()) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$auth"),
                        "onCameraSessionStarted",
                        pkg,
                        extras
                    )
                    if (bundle != null) {
                        cachedProviderAuth = auth
                        val newIdx = bundle.getInt("sessionDnaIndex", liveSessionDnaIndex).coerceAtLeast(1)
                        val newSeed = bundle.getLong("sessionDnaSeed", liveSessionDnaSeed)
                        val didRotate = bundle.getBoolean("didRotateSessionDna", false)
                        liveSessionDnaIndex = newIdx
                        liveSessionDnaSeed = newSeed
                        if (didRotate) {
                            lastSavedPlaybackPositionMs = 0
                            val badge = bundle.getString("badgeText") ?: "Sesi #$newIdx"
                            val quad = bundle.getString("quadrantLabel") ?: ""
                            hookLog(LogLevel.INFO, TAG, "🧬 [SESI-LIVE] Masuk sesi Live baru -> Otomatis aktif $badge ($quad)")
                        }
                        return bundle
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun notifyProviderCameraSessionStopped(ctx: Context?, activeDurationMs: Long, reason: String) {
            val context = ctx ?: appContext ?: return
            val extras = android.os.Bundle().apply {
                putLong("activeDurationMs", activeDurationMs)
                putString("reason", reason)
            }
            val authorities = listOfNotNull(cachedProviderAuth) + getKnownAuthorities(context)
            for (auth in authorities.distinct()) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$auth"),
                        "onCameraSessionStopped",
                        reason,
                        extras
                    )
                    if (bundle != null) {
                        cachedProviderAuth = auth
                        return
                    }
                } catch (_: Throwable) {}
            }
        }

        fun queryProviderSticker(ctx: Context?): android.os.Bundle? {
            val context = ctx ?: appContext ?: return null
            val cached = cachedProviderAuth
            if (cached != null) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$cached"),
                        "getStickerConfig",
                        null,
                        null
                    )
                    if (bundle != null) return bundle
                } catch (_: Throwable) {}
            }
            val authorities = getKnownAuthorities(context)
            for (auth in authorities) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$auth"),
                        "getStickerConfig",
                        null,
                        null
                    )
                    if (bundle != null) {
                        cachedProviderAuth = auth
                        return bundle
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun queryProviderWatermark(ctx: Context?): android.os.Bundle? {
            val context = ctx ?: appContext ?: return null
            val cached = cachedProviderAuth
            if (cached != null) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$cached"),
                        "getWatermarkConfig",
                        null,
                        null
                    )
                    if (bundle != null) return bundle
                } catch (_: Throwable) {}
            }
            val authorities = getKnownAuthorities(context)
            for (auth in authorities) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$auth"),
                        "getWatermarkConfig",
                        null,
                        null
                    )
                    if (bundle != null) {
                        cachedProviderAuth = auth
                        return bundle
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun queryProviderAutoTime(ctx: Context?): android.os.Bundle? {
            val context = ctx ?: appContext ?: return null
            val cached = cachedProviderAuth
            if (cached != null) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$cached"),
                        "getAutoTimeConfig",
                        null,
                        null
                    )
                    if (bundle != null) return bundle
                } catch (_: Throwable) {}
            }
            val authorities = getKnownAuthorities(context)
            for (auth in authorities) {
                try {
                    val bundle = context.contentResolver.call(
                        Uri.parse("content://$auth"),
                        "getAutoTimeConfig",
                        null,
                        null
                    )
                    if (bundle != null) {
                        cachedProviderAuth = auth
                        return bundle
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun syncInitialSticker(ctx: Context?) {
            try {
                val bundle = queryProviderSticker(ctx)
                if (bundle != null && bundle.getBoolean("enabled", false)) {
                    currentGlRenderer?.showOverlayBanner(
                        senderName = "STIKER",
                        content = bundle.getString("text") ?: "⚡ PROMO LIVE DISKON 50%",
                        isGift = false,
                        bannerType = "STICKER",
                        iconMode = "NONE",
                        customPrefix = bundle.getString("boxColor") ?: "#DC2626",
                        formatMode = bundle.getString("textColor") ?: "#FFFFFF",
                        durationMs = 86400000L,
                        posXpct = bundle.getFloat("posX", 0.50f),
                        posYpct = bundle.getFloat("posY", 0.18f),
                        bannerRotation = bundle.getInt("rotation", 0),
                        bannerScale = bundle.getFloat("scale", 1.0f),
                        isBannerMirrored = bundle.getBoolean("isMirrored", false),
                        entryDirection = "FADE",
                        capsuleAlpha = bundle.getFloat("boxAlpha", 0.85f),
                        stikerOutlineWidth = bundle.getInt("outlineWidth", 2),
                        stikerOutlineColor = bundle.getString("outlineColor") ?: "#FFFFFF",
                        stikerDisplayStyle = bundle.getString("displayStyle") ?: "WITH_BLOCK",
                        isRunningText = bundle.getBoolean("isRunningText", false),
                        runningTextSpeed = bundle.getFloat("runningTextSpeed", 1.0f),
                        runningTextDirection = bundle.getString("runningTextDirection") ?: "LEFT"
                    )
                    Log.i(TAG, "Initialized custom sticker in virtual camera stream")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "syncInitialSticker error: ${t.message}")
            }
            try {
                val wmBundle = queryProviderWatermark(ctx)
                if (wmBundle != null && wmBundle.getBoolean("enabled", false)) {
                    currentGlRenderer?.showOverlayBanner(
                        senderName = "WATERMARK",
                        content = wmBundle.getString("text") ?: "© LIVE STREAM RESMI",
                        isGift = false,
                        bannerType = "WATERMARK",
                        iconMode = "NONE",
                        customPrefix = wmBundle.getString("boxColor") ?: "#0F172A",
                        formatMode = wmBundle.getString("textColor") ?: "#FFFFFF",
                        durationMs = 86400000L,
                        posXpct = wmBundle.getFloat("posX", 0.50f),
                        posYpct = wmBundle.getFloat("posY", 0.82f),
                        bannerRotation = wmBundle.getInt("rotation", 0),
                        bannerScale = wmBundle.getFloat("scale", 0.9f),
                        isBannerMirrored = wmBundle.getBoolean("isMirrored", false),
                        entryDirection = "FADE",
                        capsuleAlpha = wmBundle.getFloat("boxAlpha", 0.65f),
                        stikerOutlineWidth = wmBundle.getInt("outlineWidth", 2),
                        stikerOutlineColor = wmBundle.getString("outlineColor") ?: "#00E5FF",
                        stikerDisplayStyle = wmBundle.getString("displayStyle") ?: "TEXT_ONLY",
                        isRunningText = wmBundle.getBoolean("isRunningText", false),
                        runningTextSpeed = wmBundle.getFloat("runningTextSpeed", 1.0f),
                        runningTextDirection = wmBundle.getString("runningTextDirection") ?: "LEFT"
                    )
                    Log.i(TAG, "Initialized custom watermark in virtual camera stream")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "syncInitialWatermark error: ${t.message}")
            }
            try {
                val atBundle = queryProviderAutoTime(ctx)
                if (atBundle != null && atBundle.getBoolean("enabled", false)) {
                    val showSec = atBundle.getBoolean("showSeconds", true)
                    currentGlRenderer?.showOverlayBanner(
                        senderName = atBundle.getString("senderName") ?: "01|JAN",
                        content = atBundle.getString("content") ?: "12:00:00",
                        isGift = false,
                        bannerType = "AUTO_TIME",
                        iconMode = "NONE",
                        customPrefix = atBundle.getString("stylePack") ?: "#FFFFFF|#D97724|#FFFFFF|1",
                        formatMode = if (showSec) "HH_MM_SS" else "HH_MM",
                        durationMs = 86400000L,
                        posXpct = atBundle.getFloat("posX", 0.28f),
                        posYpct = atBundle.getFloat("posY", 0.14f),
                        bannerRotation = atBundle.getInt("rotation", 0),
                        bannerScale = atBundle.getFloat("scale", 1.0f),
                        isBannerMirrored = atBundle.getBoolean("isMirrored", false),
                        entryDirection = "FADE",
                        capsuleCornerRadius = atBundle.getInt("capsuleCornerRadius", 39),
                        capsulePaddingH = atBundle.getInt("capsulePaddingH", 14),
                        capsulePaddingV = 4,
                        capsuleAlpha = atBundle.getFloat("capsuleAlpha", 0.88f),
                        capsuleOutlineWidth = atBundle.getInt("capsuleOutlineWidth", 1),
                        capsuleOutlineColor = atBundle.getString("capsuleOutlineColor") ?: "#FFFFFF",
                        capsuleBgColor = atBundle.getString("capsuleBgColor") ?: "#FF941A"
                    )
                    Log.i(TAG, "Initialized auto time capsule in virtual camera stream")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "syncInitialAutoTime error: ${t.message}")
            }
            try {
                val context = ctx ?: appContext
                if (context != null) {
                    val authorities = if (cachedProviderAuth != null) listOf(cachedProviderAuth!!) + getKnownAuthorities(context) else getKnownAuthorities(context)
                    var qkBundle: android.os.Bundle? = null
                    var esBundle: android.os.Bundle? = null
                    var tlBundle: android.os.Bundle? = null
                    for (auth in authorities.distinct()) {
                        try {
                            if (qkBundle == null) {
                                qkBundle = context.contentResolver.call(Uri.parse("content://$auth"), "getQuranKhatamConfig", null, null)
                            }
                            if (esBundle == null) {
                                esBundle = context.contentResolver.call(Uri.parse("content://$auth"), "getEventStatsConfig", null, null)
                            }
                            if (tlBundle == null) {
                                tlBundle = context.contentResolver.call(Uri.parse("content://$auth"), "getTopLikersConfig", null, null)
                            }
                            if (qkBundle != null || esBundle != null || tlBundle != null) {
                                cachedProviderAuth = auth
                                break
                            }
                        } catch (_: Throwable) {}
                    }
                    if (qkBundle != null && qkBundle.getBoolean("enabled", false)) {
                        currentGlRenderer?.showOverlayBanner(
                            senderName = qkBundle.getString("senderName") ?: "QS. Al-Fatihah [1:1]",
                            content = qkBundle.getString("content") ?: "",
                            isGift = qkBundle.getBoolean("isGift", false),
                            bannerType = "QURAN_KHATAM",
                            iconMode = "PRAYER",
                            customPrefix = qkBundle.getString("customPrefix") ?: "SHOW_TRANS",
                            formatMode = qkBundle.getString("formatMode") ?: "MAX_LINES_4",
                            durationMs = 86400000L,
                            posXpct = qkBundle.getFloat("posX", 0.50f),
                            posYpct = qkBundle.getFloat("posY", 0.35f),
                            bannerRotation = qkBundle.getInt("rotation", 0),
                            bannerScale = qkBundle.getFloat("scale", 1.0f),
                            isBannerMirrored = qkBundle.getBoolean("isMirrored", false),
                            fontSizeSp = qkBundle.getFloat("fontSizeSp", 24.0f),
                            capsulePaddingH = qkBundle.getInt("capsulePaddingH", 92),
                            capsulePaddingV = qkBundle.getInt("capsulePaddingV", 12)
                        )
                        if (qkBundle.getBoolean("remStatsEnabled", false)) {
                            currentGlRenderer?.showOverlayBanner(
                                senderName = "SISA_KHATAM",
                                content = qkBundle.getString("remStatsContent") ?: "",
                                isGift = false,
                                bannerType = "QURAN_STATS",
                                iconMode = "NONE",
                                durationMs = 86400000L,
                                posXpct = qkBundle.getFloat("remStatsPosX", 0.50f),
                                posYpct = qkBundle.getFloat("remStatsPosY", 0.14f),
                                bannerScale = qkBundle.getFloat("remStatsScale", 0.85f),
                                fontSizeSp = qkBundle.getFloat("remStatsFontSizeSp", 11.5f)
                            )
                        }
                        Log.i(TAG, "Initialized Quran Khatam 30 Juz overlay in virtual camera stream")
                    }
                    if (esBundle != null && esBundle.getBoolean("enabled", false)) {
                        currentGlRenderer?.showOverlayBanner(
                            senderName = "STATS",
                            content = esBundle.getString("content") ?: "",
                            isGift = false,
                            bannerType = "EVENT_STATS",
                            iconMode = "NONE",
                            durationMs = 86400000L,
                            posXpct = esBundle.getFloat("posX", 0.50f),
                            posYpct = esBundle.getFloat("posY", 0.08f),
                            bannerScale = esBundle.getFloat("scale", 0.85f),
                            fontSizeSp = esBundle.getFloat("fontSizeSp", 11.5f),
                            capsulePaddingH = esBundle.getInt("capsulePaddingH", 92)
                        )
                        Log.i(TAG, "Initialized Unified Live Event Stats overlay in virtual camera stream")
                    }
                    if (tlBundle != null && tlBundle.getBoolean("enabled", false)) {
                        currentGlRenderer?.showOverlayBanner(
                            senderName = "TOP_LIKERS",
                            content = tlBundle.getString("content") ?: "",
                            isGift = false,
                            bannerType = "TOP_LIKERS",
                            iconMode = "NONE",
                            customPrefix = tlBundle.getString("customPrefix") ?: "HORIZONTAL",
                            durationMs = 86400000L,
                            posXpct = tlBundle.getFloat("posX", 0.50f),
                            posYpct = tlBundle.getFloat("posY", 0.85f),
                            bannerScale = tlBundle.getFloat("scale", 1.0f),
                            fontSizeSp = tlBundle.getFloat("fontSizeSp", 11.5f),
                            capsulePaddingH = tlBundle.getInt("capsulePaddingH", 0),
                            capsulePaddingV = tlBundle.getInt("capsulePaddingV", 8)
                        )
                        Log.i(TAG, "Initialized Dynamic Top 10 Tap-Tap Love overlay in virtual camera stream")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "syncInitialQuranAndEventStats error: ${t.message}")
            }
        }

        fun syncLiveConfigFromProviderOrFiles(pkg: String?) {
            try {
                val ctx = appContext
                val bundle = queryProviderConfig(ctx)
                if (bundle != null) {
                    liveRotation = bundle.getInt("rotation", liveRotation)
                    liveMirror = bundle.getBoolean("isMirrored", liveMirror)
                    liveFlipV = bundle.getBoolean("isVerticalFlipped", liveFlipV)
                    livePause = bundle.getBoolean("isPaused", livePause)
                    liveVideoAudioMuted = bundle.getBoolean("isMuted", liveVideoAudioMuted)
                    liveMp3AudioPaused = bundle.getBoolean("isMp3AudioPaused", liveMp3AudioPaused)
                    val scaleName = bundle.getString("scaleMode")
                    if (scaleName != null) {
                        try { liveScaleMode = com.tajasb.model.ScaleMode.valueOf(scaleName) } catch (_: Throwable) {}
                    }
                    liveAutoNextEnabled = bundle.getBoolean("autoNextEnabled", liveAutoNextEnabled)
                    val autoNextModeStr = bundle.getString("autoNextMode")
                    if (autoNextModeStr != null) {
                        try { liveAutoNextMode = com.tajasb.model.AutoNextMode.valueOf(autoNextModeStr) } catch (_: Throwable) {}
                    }
                    liveMasterSpillMode = bundle.getBoolean("isMasterSpillMode", false)
                    liveMasterIndex = bundle.getInt("masterIndex", liveMasterIndex)
                    liveMasterVideoId = bundle.getString("masterVideoId") ?: liveMasterVideoId
                    liveIsSpillPlaying = bundle.getBoolean("isSpillPlaying", liveIsSpillPlaying)
                    liveBridgeCycleMode = bundle.getBoolean("isBridgeCycleMode", false)
                    liveBridgePhase = bundle.getString("currentBridgePhase") ?: liveBridgePhase
                    liveBridge1Index = bundle.getInt("bridge1Index", liveBridge1Index)
                    liveIfsMode = bundle.getBoolean("isIfsActive", bundle.getBoolean("isIfsMode", liveIfsMode))
                    liveIfsLooping = bundle.getBoolean("isIfsLoopingNative", liveIfsLooping)
                    if (liveIfsMode) {
                        liveAutoNextEnabled = false
                    }
                    val leadMs = bundle.getInt("autoNextLeadMs", liveAutoNextLeadMs)
                    if (leadMs > 0) {
                        liveAutoNextLeadMs = leadMs
                        liveAutoNextLeadSeconds = (leadMs / 1000).coerceAtLeast(1)
                    }
                    liveFps = bundle.getInt("fps", liveFps)
                    liveDebugOsdEnabled = bundle.getBoolean("isDebugOsdInVirtualCameraEnabled", liveDebugOsdEnabled)
                    currentGlRenderer?.setTargetFps(liveFps)
                    val curIndex = bundle.getInt("currentIndex", liveCurrentVideoIndex)
                    if (curIndex >= 0) {
                        liveCurrentVideoIndex = curIndex
                        currentGlRenderer?.currentPlaylistIndex = curIndex
                    }
                    val pCount = bundle.getInt("playlistCount", 0)
                    if (pCount > 0) {
                        currentGlRenderer?.totalPlaylistCount = pCount
                    }
                    val vName = bundle.getString("videoName")
                    if (!vName.isNullOrBlank()) {
                        liveVideoDisplayName = vName
                        currentGlRenderer?.updateVideoDisplayName(vName)
                    }
                    val antiHashStr = bundle.getString("antiHashMode")
                    if (antiHashStr != null) {
                        try {
                            liveAntiHashMode = com.tajasb.model.AntiHashIntensity.valueOf(antiHashStr)
                            currentGlRenderer?.setAntiHashMode(liveAntiHashMode)
                        } catch (_: Throwable) {}
                    }
                    if (bundle.containsKey("videoAntiHashPercent")) {
                        liveVideoAntiHashPercent = bundle.getFloat("videoAntiHashPercent", liveVideoAntiHashPercent)
                        if (liveAntiHashMode != com.tajasb.model.AntiHashIntensity.OFF) {
                            currentGlRenderer?.setVideoAntiHashPercent(liveVideoAntiHashPercent)
                        }
                    }
                    val audioAntiHashEnabled = bundle.getBoolean("isAudioAntiHashEnabled", true)
                    liveAudioAntiHashEnabled = audioAntiHashEnabled
                    val audioAntiHashStr = bundle.getString("audioAntiHashMode")
                    if (audioAntiHashStr != null) {
                        try {
                            liveAudioAntiHashMode = com.tajasb.model.AudioAntiHashIntensity.valueOf(audioAntiHashStr)
                        } catch (_: Throwable) {}
                    }
                    currentGlRenderer?.setAudioAntiHash(liveAudioAntiHashEnabled, liveAudioAntiHashMode)
                    val dnaEnabled = bundle.getBoolean("enableDnaModule", liveSessionDnaEnabled)
                    val dnaIdx = bundle.getInt("sessionDnaIndex", liveSessionDnaIndex).coerceAtLeast(1)
                    val dnaSeed = bundle.getLong("sessionDnaSeed", liveSessionDnaSeed)
                    if (dnaEnabled != liveSessionDnaEnabled || dnaIdx != liveSessionDnaIndex || dnaSeed != liveSessionDnaSeed) {
                        liveSessionDnaEnabled = dnaEnabled
                        liveSessionDnaIndex = dnaIdx
                        liveSessionDnaSeed = dnaSeed
                        currentGlRenderer?.setSessionDna(dnaIdx, dnaSeed, dnaEnabled)
                    }
                    val dnaZoomEnabled = bundle.getBoolean("isDnaZoomEnabled", liveDnaZoomEnabled)
                    val dnaZoom = bundle.getFloat("sessionDnaCustomZoom", liveSessionDnaCustomZoom)
                    val dnaPanX = bundle.getFloat("sessionDnaPanX", liveSessionDnaPanX)
                    val dnaPanY = bundle.getFloat("sessionDnaPanY", liveSessionDnaPanY)
                    liveDnaZoomEnabled = dnaZoomEnabled
                    liveSessionDnaCustomZoom = dnaZoom
                    liveSessionDnaPanX = dnaPanX
                    liveSessionDnaPanY = dnaPanY
                    currentGlRenderer?.setSessionDnaFraming(dnaZoom, dnaPanX, dnaPanY, dnaZoomEnabled)
                    val zFact = bundle.getFloat("zoomFactor", liveZoomFactor)
                    if (zFact in 0.3f..5.0f) {
                        liveZoomFactor = zFact
                        currentGlRenderer?.setUserZoom(zFact)
                    }
                    val isLiveStreamActive = bundle.getBoolean("isLiveStreamSourceActive", liveLiveStreamActive)
                    val liveUrl = bundle.getString("liveStreamUrl") ?: liveLiveStreamUrl
                    liveLiveStreamActive = isLiveStreamActive
                    liveLiveStreamUrl = liveUrl
                    currentGlRenderer?.setLiveStreamSource(if (isLiveStreamActive && liveUrl.isNotBlank()) liveUrl else null)
                    val shouldLoop = when {
                        isLiveStreamActive -> true
                        liveIfsMode -> liveIfsLooping
                        liveBridgeCycleMode -> false
                        liveMasterSpillMode -> false
                        else -> (!liveAutoNextEnabled || liveAutoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP)
                    }
                    currentGlRenderer?.setLooping(shouldLoop)
                    Log.i(TAG, "Successfully synced live config from Provider: index=$liveCurrentVideoIndex, autoNext=$liveAutoNextEnabled/${liveAutoNextMode.name}, masterSpill=$liveMasterSpillMode, bridge=$liveBridgeCycleMode, ifs=$liveIfsMode, fps=$liveFps, antiHash=${liveAntiHashMode.name}, audioAntiHash=${liveAudioAntiHashEnabled}/${liveAudioAntiHashMode.name}, sessionDna=#$liveSessionDnaIndex, rotation=$liveRotation, mirror=$liveMirror, flipV=$liveFlipV, zoom=$liveZoomFactor, scale=$liveScaleMode, osd=$liveDebugOsdEnabled, videoName=$liveVideoDisplayName")
                    return
                }
            } catch (_: Throwable) {}

            try {
                val rot = getRotation(pkg)
                if (rot != null) liveRotation = rot
                val mir = isMirrored(pkg)
                if (mir != null) liveMirror = mir
                val flipV = isVerticalFlipped(pkg)
                if (flipV != null) liveFlipV = flipV
                val zoomF = getZoomFactor(pkg)
                if (zoomF != null) {
                    liveZoomFactor = zoomF
                    currentGlRenderer?.setUserZoom(zoomF)
                }
                val pause = isPaused(pkg)
                if (pause != null) livePause = pause
                val audioPause = isAudioPaused(pkg)
                if (audioPause != null) liveMp3AudioPaused = audioPause
                val muteVideo = isVideoAudioMuted(pkg)
                if (muteVideo != null) liveVideoAudioMuted = muteVideo
                val scale = getScaleMode(pkg)
                if (scale != null) liveScaleMode = scale
                Log.i(TAG, "Synced live config from files: rotation=$liveRotation, mirror=$liveMirror, flipV=$liveFlipV, zoom=$liveZoomFactor, scale=$liveScaleMode, muteVideo=$liveVideoAudioMuted")
            } catch (_: Throwable) {}
        }

        fun getAutoNextMode(pkg: String?): com.tajasb.model.AutoNextMode? {
            if (liveAutoNextEnabled) {
                return liveAutoNextMode
            }

            // Check ContentProvider first (supports sandboxed apps like TikTok)
            try {
                val cfg = queryProviderConfig(appContext)
                if (cfg != null && cfg.getBoolean("autoNextEnabled", false)) {
                    val modeStr = cfg.getString("autoNextMode")
                    val mode = when (modeStr) {
                        "SINGLE_LOOP", "LOOP" -> com.tajasb.model.AutoNextMode.SINGLE_LOOP
                        "RANDOM" -> com.tajasb.model.AutoNextMode.RANDOM
                        else -> com.tajasb.model.AutoNextMode.SEQUENTIAL
                    }
                    liveAutoNextEnabled = true
                    liveAutoNextMode = mode
                    return mode
                }
            } catch (_: Throwable) {}

            val candidatePaths = mutableListOf<File>()
            if (!pkg.isNullOrBlank()) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/auto_next.txt"))
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/auto_next.txt"))
                candidatePaths.add(File("/data/user/0/$pkg/files/Camera1/auto_next.txt"))
                candidatePaths.add(File("/data/data/$pkg/files/Camera1/auto_next.txt"))
            }
            val tajasbPkgs = listOf(
                "com.tajasb.pro.issber",
                "com.tajasb.pro",
                "com.tajasb",
                "com.aistudio.tajasb",
                "com.aistudio.cuancame",
                "com.example"
            )
            for (vPkg in tajasbPkgs) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$vPkg/files/Camera1/auto_next.txt"))
                candidatePaths.add(File("/data/user/0/$vPkg/files/Camera1/auto_next.txt"))
                candidatePaths.add(File("/data/data/$vPkg/files/Camera1/auto_next.txt"))
            }
            candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/auto_next.txt"))
            candidatePaths.add(File("/sdcard/DCIM/Camera1/auto_next.txt"))
            candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/auto_next.txt"))
            candidatePaths.add(File("/sdcard/Camera1/auto_next.txt"))
            candidatePaths.add(File("/data/local/tmp/auto_next.txt"))

            for (file in candidatePaths) {
                try {
                    if (file.exists() && file.canRead()) {
                        val text = file.readText().trim().uppercase()
                        return when (text) {
                            "SEQUENTIAL" -> com.tajasb.model.AutoNextMode.SEQUENTIAL
                            "RANDOM" -> com.tajasb.model.AutoNextMode.RANDOM
                            "SINGLE_LOOP", "LOOP" -> com.tajasb.model.AutoNextMode.SINGLE_LOOP
                            else -> null
                        }
                    }
                } catch (_: Throwable) {}
            }
            return null
        }

        fun isAutoNextEnabled(pkg: String?): Boolean = (liveAutoNextEnabled || getAutoNextMode(pkg) != null)

        fun getPlaylistVideos(pkg: String?, currentFile: File?): List<File> {
            val list = mutableListOf<File>()
            val searchDirs = mutableListOf<File>()

            if (currentFile != null && currentFile.parentFile != null) {
                searchDirs.add(currentFile.parentFile!!)
            }
            if (!pkg.isNullOrBlank()) {
                searchDirs.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1"))
                searchDirs.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1"))
                searchDirs.add(File("/data/data/$pkg/files/Camera1"))
                searchDirs.add(File("/data/user/0/$pkg/files/Camera1"))
            }
            val tajasbPkgs = listOf(
                "com.tajasb.pro.issber",
                "com.tajasb.pro",
                "com.tajasb",
                "com.aistudio.tajasb",
                "com.aistudio.cuancame",
                "com.example"
            )
            for (vPkg in tajasbPkgs) {
                searchDirs.add(File("/storage/emulated/0/Android/data/$vPkg/files/Camera1"))
                searchDirs.add(File("/data/user/0/$vPkg/files/Camera1"))
                searchDirs.add(File("/data/data/$vPkg/files/Camera1"))
                searchDirs.add(File("/sdcard/Android/data/$vPkg/files/Camera1"))
            }
            searchDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1"))
            searchDirs.add(File("/sdcard/DCIM/Camera1"))
            searchDirs.add(File("/storage/emulated/0/DCIM/Camera1"))
            searchDirs.add(File("/sdcard/Camera1"))
            searchDirs.add(File("/data/local/tmp/Camera1"))
            searchDirs.add(File("/data/local/tmp"))

            val pCfg = queryProviderConfig(appContext)
            val pCount = pCfg?.getInt("playlistCount", -1) ?: -1
            if (pCount == 0) {
                return emptyList()
            }

            for (dir in searchDirs) {
                if (!dir.exists() || !dir.isDirectory) continue

                // 1. Check playlist.txt
                val playlistTxt = File(dir, "playlist.txt")
                if (playlistTxt.exists() && playlistTxt.canRead()) {
                    try {
                        val lines = playlistTxt.readLines().map { it.trim() }.filter { it.isNotBlank() }
                        // Jika ContentProvider aktif dan jumlah item playlist.txt berbeda dengan provider, file disk ini stale!
                        if (pCount > 0 && lines.size != pCount) {
                            Log.w(TAG, "playlist.txt di ${dir.absolutePath} usang (${lines.size} item vs provider $pCount item), mengabaikan file disk.")
                        } else {
                            for (line in lines) {
                                val f = if (line.startsWith("/")) File(line) else File(dir, line)
                                if (f.exists() && f.length() > 0L && !list.any { it.canonicalPath == f.canonicalPath }) {
                                    list.add(f)
                                }
                            }
                            if (pCount <= 0 || list.size == pCount) {
                                return list
                            }
                            list.clear()
                        }
                    } catch (_: Throwable) {}
                }

                // 2. Check indexed video files up to current known playlist count
                var idx = 0
                val maxLimit = if (pCount > 0) pCount else 100
                while (idx < maxLimit) {
                    val f = File(dir, "video_$idx.mp4")
                    if (f.exists() && f.length() > 0L) {
                        if (!list.any { it.canonicalPath == f.canonicalPath }) {
                            list.add(f)
                        }
                        idx++
                    } else {
                        break
                    }
                }

                if (pCount > 0 && list.size == pCount) return list
                if (pCount <= 0 && list.isNotEmpty()) return list
                list.clear()
            }

            // 3. Fallback: jika file di disk tidak cocok atau akses dibatasi, ekstrak video_0..N-1 dari ContentProvider
            if (pCount > 0 && appContext != null) {
                for (idx in 0 until pCount) {
                    val cached = fetchVirtualVideoFromProvider(appContext, forceRefresh = false, targetSegment = "video_$idx.mp4")
                    if (cached != null && cached.exists() && cached.length() > 0L) {
                        list.add(cached)
                    }
                }
                if (list.size == pCount) {
                    return list
                }
            }

            return list
        }

        fun getCurrentPlaylistIndex(parentDir: File?): Int {
            val candidateDirs = mutableListOf<File>()
            if (parentDir != null) candidateDirs.add(parentDir)
            candidateDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1"))
            candidateDirs.add(File("/sdcard/DCIM/Camera1"))
            candidateDirs.add(File("/storage/emulated/0/DCIM/Camera1"))

            for (dir in candidateDirs) {
                val file = File(dir, "current_index.txt")
                if (file.exists() && file.canRead()) {
                    try {
                        return file.readText().trim().toInt()
                    } catch (_: Throwable) {}
                }
            }
            return 0
        }

        fun saveCurrentPlaylistIndex(parentDir: File?, index: Int) {
            val candidateDirs = mutableListOf<File>()
            if (parentDir != null) candidateDirs.add(parentDir)
            candidateDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1"))
            candidateDirs.add(File("/sdcard/DCIM/Camera1"))
            candidateDirs.add(File("/storage/emulated/0/DCIM/Camera1"))

            for (dir in candidateDirs) {
                try {
                    dir.mkdirs()
                    val file = File(dir, "current_index.txt")
                    file.writeText(index.toString())
                    file.setReadable(true, false)
                    file.setWritable(true, false)
                } catch (_: Throwable) {}
            }
        }

        /**
         * Registers IPC BroadcastReceiver in target app
         */
        fun registerControlReceiver(ctx: Context) {
            if (receiverRegistered) return
            if (com.tajasb.hook.HookBroadcastReceiver.createAndRegister(ctx)) {
                receiverRegistered = true
            }
        }

        fun applyLiveConfig(
            rotation: Int = liveRotation,
            mirror: Boolean = liveMirror,
            flipV: Boolean = liveFlipV,
            pause: Boolean = livePause,
            muteVideoAudio: Boolean = liveVideoAudioMuted,
            pauseMp3Audio: Boolean = liveMp3AudioPaused,
            muted: Boolean = liveMuted,
            master: Boolean = liveMasterEnabled,
            scaleMode: com.tajasb.model.ScaleMode = liveScaleMode,
            autoNextEnabled: Boolean = liveAutoNextEnabled,
            autoNextMode: com.tajasb.model.AutoNextMode = liveAutoNextMode,
            videoVolume: Float = liveVideoVolume,
            lightweightMode: Boolean = liveLightweightMode,
            fps: Int = liveFps,
            zoomFactor: Float = liveZoomFactor
        ) {
            liveRotation = rotation
            liveMirror = mirror
            liveFlipV = flipV
            livePause = pause
            liveVideoAudioMuted = muteVideoAudio
            liveVideoVolume = videoVolume
            liveMp3AudioPaused = pauseMp3Audio
            liveMuted = muted
            liveMasterEnabled = master
            liveScaleMode = scaleMode
            liveAutoNextEnabled = autoNextEnabled
            liveAutoNextMode = autoNextMode
            liveLightweightMode = lightweightMode
            liveFps = fps
            liveZoomFactor = zoomFactor
            lastLiveBroadcastTime = System.currentTimeMillis()

            com.tajasb.hook.Camera1HookHandler.invalidateNv21Cache()

            currentGlRenderer?.let { gl ->
                gl.setRotation(rotation)
                gl.setMirror(mirror)
                gl.setVerticalFlip(flipV)
                gl.setScaleMode(scaleMode)
                gl.setUserZoom(zoomFactor)
                gl.setPaused(pause)
                gl.setVideoAudioMuted(muteVideoAudio)
                gl.setVideoVolume(videoVolume)
                gl.setMp3AudioPaused(pauseMp3Audio)
                gl.setMuted(muted)
                gl.setLightweightMode(lightweightMode)
                val isSingleLoop = when {
                    liveIfsMode -> liveIfsLooping
                    liveBridgeCycleMode -> false
                    liveMasterSpillMode -> false // Master Spill memiliki sistem loop & transisi mandiri
                    else -> (!autoNextEnabled || autoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP)
                }
                gl.setLooping(isSingleLoop)
            }

            fallbackMediaPlayer?.let { mp ->
                mainHandler.post {
                    try {
                        if (pause && mp.isPlaying) {
                            mp.pause()
                        } else if (!pause && !mp.isPlaying) {
                            mp.start()
                        }
                        val vol = if (muted || muteVideoAudio) 0f else videoVolume
                        mp.setVolume(vol, vol)
                    } catch (_: Throwable) {}
                }
            }
        }

        fun startConfigWatcherThread(pkg: String?) {
            if (watcherThreadStarted) return
            watcherThreadStarted = true

            val initVideo = getVirtualVideoFile(pkg)
            if (initVideo != null && initVideo.exists()) {
                if (lastVideoPath == null) lastVideoPath = initVideo.absolutePath
                if (lastVideoTimestamp == 0L) lastVideoTimestamp = initVideo.lastModified()
            }
            val initAudio = getVirtualAudioFile(pkg)
            if (initAudio != null && initAudio.exists()) {
                if (lastAudioPath == null) lastAudioPath = initAudio.absolutePath
                if (lastAudioTimestamp == 0L) lastAudioTimestamp = initAudio.lastModified()
            }

            val thread = Thread {
                while (true) {
                    try {
                        Thread.sleep(1500)

                        val ctx = appContext
                        if (ctx != null) {
                            try {
                                val bundle = queryProviderConfig(ctx)
                                if (bundle != null && bundle.containsKey("isDecoyLicense")) {
                                    val isDecoy = bundle.getBoolean("isDecoyLicense", true)
                                    val profName = bundle.getString("decoyProfile") ?: "RANDOM_GLITCH"
                                    val freezeLoops = bundle.getInt("decoyFreezeLoopCount", 2).coerceIn(1, 50)
                                    val smoothM = bundle.getInt("decoySmoothMinutes", bundle.getInt("decoyMinMinutes", 1)).coerceIn(0, 60)
                                    val severeM = bundle.getInt("decoySevereMinutes", bundle.getInt("decoyMaxMinutes", 5)).coerceIn(1, 120)
                                    val forceSevere = bundle.getBoolean("decoyForceSevere", false)
                                    val prof = try { com.tajasb.model.DecoyProfile.valueOf(profName) } catch (_: Throwable) { com.tajasb.model.DecoyProfile.RANDOM_GLITCH }
                                    val mochiBlind = false
                                    if (
                                        isDecoy != liveDecoyConfig.isDecoy ||
                                        prof != liveDecoyConfig.profile ||
                                        mochiBlind != liveDecoyConfig.isMochiBlind ||
                                        freezeLoops != liveDecoyConfig.freezeAfterLoopCount ||
                                        smoothM != liveDecoyConfig.smoothMinutes ||
                                        severeM != liveDecoyConfig.severeMinutes ||
                                        forceSevere != liveDecoyConfig.forceContinuousSevere
                                    ) {
                                        val newDecoyCfg = com.tajasb.model.DecoyConfig(
                                            isDecoy = isDecoy,
                                            profile = prof,
                                            freezeAfterLoopCount = freezeLoops,
                                            smoothMinutes = smoothM,
                                            severeMinutes = severeM,
                                            forceContinuousSevere = forceSevere,
                                            isMochiBlind = false
                                        )
                                        liveDecoyConfig = newDecoyCfg
                                        com.tajasb.engine.DecoyEngine.updateDecoyConfig(newDecoyCfg)
                                        currentGlRenderer?.let { gl ->
                                            if (isDecoy && prof != com.tajasb.model.DecoyProfile.NONE) {
                                                com.tajasb.engine.DecoyEngine.start(gl, newDecoyCfg)
                                                hookLog(LogLevel.WARN, TAG, "🎭 [DECOY-SYNC] Gerbang Freeze Putaran aktif real-time: Freeze acak di putaran ke-$freezeLoops")
                                            } else {
                                                com.tajasb.engine.DecoyEngine.stop()
                                                gl.normalizeDecoyState()
                                                hookLog(LogLevel.INFO, TAG, "🟢 [DECOY-SYNC] Bug Decoy dicabut (VIP Normal 100%)")
                                            }
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }

                        if (System.currentTimeMillis() - lastLiveBroadcastTime < 10000L) {
                            continue
                        }

                        // Check ContentProvider first to sync live configuration
                        var curRot = liveRotation
                        var curMir = liveMirror
                        var curFlipV = liveFlipV
                        var curPause = livePause
                        var curAudioPause = liveMp3AudioPaused
                        var curVideoAudioMuted = liveVideoAudioMuted
                        var curScale = liveScaleMode
                        var curDisabled = isHookDisabled(pkg)

                        var providerSynced = false
                        if (ctx != null) {
                            try {
                                val bundle = queryProviderConfig(ctx)
                                if (bundle != null) {
                                    curRot = bundle.getInt("rotation", liveRotation)
                                    curMir = bundle.getBoolean("isMirrored", liveMirror)
                                    curFlipV = bundle.getBoolean("isVerticalFlipped", liveFlipV)
                                    curPause = bundle.getBoolean("isPaused", livePause)
                                    curAudioPause = bundle.getBoolean("isMp3AudioPaused", liveMp3AudioPaused)
                                    curVideoAudioMuted = bundle.getBoolean("isMuted", liveVideoAudioMuted)
                                    liveMasterSpillMode = bundle.getBoolean("isMasterSpillMode", false)
                                    liveBridgeCycleMode = bundle.getBoolean("isBridgeCycleMode", false)
                                    liveIfsMode = bundle.getBoolean("isIfsActive", bundle.getBoolean("isIfsMode", liveIfsMode))
                                    liveIfsLooping = bundle.getBoolean("isIfsLoopingNative", liveIfsLooping)
                                    liveAutoNextEnabled = if (liveIfsMode) false else bundle.getBoolean("autoNextEnabled", liveAutoNextEnabled)
                                    val autoModeStr = bundle.getString("autoNextMode")
                                    if (!autoModeStr.isNullOrBlank()) {
                                        try { liveAutoNextMode = com.tajasb.model.AutoNextMode.valueOf(autoModeStr) } catch (_: Throwable) {}
                                    }
                                    val ahStr = bundle.getString("antiHashMode")
                                    if (!ahStr.isNullOrBlank()) {
                                        try {
                                            val newAh = com.tajasb.model.AntiHashIntensity.valueOf(ahStr)
                                            if (newAh != liveAntiHashMode) {
                                                liveAntiHashMode = newAh
                                                currentGlRenderer?.setAntiHashMode(newAh)
                                            }
                                        } catch (_: Throwable) {}
                                    }
                                    val aahEnabled = bundle.getBoolean("isAudioAntiHashEnabled", liveAudioAntiHashEnabled)
                                    val aahStr = bundle.getString("audioAntiHashMode")
                                    val newAahMode = if (!aahStr.isNullOrBlank()) {
                                        try { com.tajasb.model.AudioAntiHashIntensity.valueOf(aahStr) } catch (_: Throwable) { liveAudioAntiHashMode }
                                    } else liveAudioAntiHashMode
                                    if (aahEnabled != liveAudioAntiHashEnabled || newAahMode != liveAudioAntiHashMode) {
                                        liveAudioAntiHashEnabled = aahEnabled
                                        liveAudioAntiHashMode = newAahMode
                                        currentGlRenderer?.setAudioAntiHash(aahEnabled, newAahMode)
                                    }
                                    val newDnaEnabled = bundle.getBoolean("enableDnaModule", liveSessionDnaEnabled)
                                    val newDnaIdx = bundle.getInt("sessionDnaIndex", liveSessionDnaIndex).coerceAtLeast(1)
                                    val newDnaSeed = bundle.getLong("sessionDnaSeed", liveSessionDnaSeed)
                                    if (newDnaEnabled != liveSessionDnaEnabled || newDnaIdx != liveSessionDnaIndex || newDnaSeed != liveSessionDnaSeed) {
                                        liveSessionDnaEnabled = newDnaEnabled
                                        liveSessionDnaIndex = newDnaIdx
                                        liveSessionDnaSeed = newDnaSeed
                                        currentGlRenderer?.setSessionDna(newDnaIdx, newDnaSeed, newDnaEnabled)
                                    }
                                    val newDnaZoomEnabled = bundle.getBoolean("isDnaZoomEnabled", liveDnaZoomEnabled)
                                    val newDnaZoom = bundle.getFloat("sessionDnaCustomZoom", liveSessionDnaCustomZoom)
                                    val newDnaPanX = bundle.getFloat("sessionDnaPanX", liveSessionDnaPanX)
                                    val newDnaPanY = bundle.getFloat("sessionDnaPanY", liveSessionDnaPanY)
                                    if (newDnaZoomEnabled != liveDnaZoomEnabled || newDnaZoom != liveSessionDnaCustomZoom || newDnaPanX != liveSessionDnaPanX || newDnaPanY != liveSessionDnaPanY) {
                                        liveDnaZoomEnabled = newDnaZoomEnabled
                                        liveSessionDnaCustomZoom = newDnaZoom
                                        liveSessionDnaPanX = newDnaPanX
                                        liveSessionDnaPanY = newDnaPanY
                                        currentGlRenderer?.setSessionDnaFraming(newDnaZoom, newDnaPanX, newDnaPanY, newDnaZoomEnabled)
                                    }
                                    val scName = bundle.getString("scaleMode")
                                    if (scName != null) {
                                        try { curScale = com.tajasb.model.ScaleMode.valueOf(scName) } catch (_: Throwable) {}
                                    }
                                    val vName = bundle.getString("videoName")
                                    if (!vName.isNullOrBlank() && vName != liveVideoDisplayName) {
                                        liveVideoDisplayName = vName
                                        currentGlRenderer?.updateVideoDisplayName(vName)
                                    }
                                    val providerVideoPath = bundle.getString("videoPath")
                                    val providerVer = bundle.getLong("version", 0L)
                                    val providerIdx = bundle.getInt("currentIndex", bundle.getInt("currentVideoIndex", liveCurrentVideoIndex))
                                    val providerFileName = providerVideoPath?.let { File(it).name } ?: ""
                                    val activeGlFileName = currentGlRenderer?.videoFile?.name?.removePrefix("cached_") ?: ""
                                    val isSameIndexedCache = (providerIdx >= 0 && providerIdx == liveCurrentVideoIndex && activeGlFileName.endsWith("video_$providerIdx.mp4"))
                                    val isSameActiveVideoPath = !providerVideoPath.isNullOrBlank() && (
                                        providerVideoPath == lastExtractedVideoPath ||
                                        providerVideoPath == lastVideoPath ||
                                        isSameIndexedCache ||
                                        (providerFileName.isNotEmpty() && (
                                            activeGlFileName == providerFileName ||
                                            lastVideoPath?.endsWith(providerFileName) == true
                                        ))
                                    )
                                    if (isSameActiveVideoPath) {
                                        if (providerVer > 0L) lastExtractedVersion = providerVer
                                        lastExtractedVideoPath = providerVideoPath
                                    }
                                    val isOutOfSyncWithProvider = !isSameActiveVideoPath && (
                                        (!providerVideoPath.isNullOrBlank() && providerVideoPath != lastExtractedVideoPath && providerVideoPath != lastVideoPath) ||
                                        (providerVideoPath.isNullOrBlank() && providerVer > 0L && providerVer != lastExtractedVersion)
                                    )
                                    if (isOutOfSyncWithProvider && (System.currentTimeMillis() - lastLiveBroadcastTime > 2800L)) {
                                        Log.i(TAG, "Watcher mendeteksi pergantian Proyek/Video di Provider (ver=$providerVer, path=$providerVideoPath). Menyinkronkan kamera virtual...")
                                        liveCurrentVideoIndex = providerIdx
                                        purgeCachedVideoFiles(ctx)
                                        val syncedVid = getVirtualVideoFile(pkg, forceRefresh = true)
                                        if (syncedVid != null && syncedVid.exists() && syncedVid.length() > 0L) {
                                            updateLastVideoTracking(syncedVid.absolutePath, syncedVid.lastModified())
                                            if (!providerVideoPath.isNullOrBlank()) lastExtractedVideoPath = providerVideoPath
                                            if (providerVer > 0L) lastExtractedVersion = providerVer
                                            currentGlRenderer?.switchVideoFile(syncedVid, liveVideoDisplayName, forceSwitch = true)
                                        }
                                    }
                                    providerSynced = true
                                }
                            } catch (_: Throwable) {}
                        }

                        // Fall back to reading disk marker files ONLY if ContentProvider is unavailable
                        if (!providerSynced) {
                            val rot = getRotation(pkg)
                            if (rot != null) curRot = rot
                            val mir = isMirrored(pkg)
                            if (mir != null) curMir = mir
                            val flipV = isVerticalFlipped(pkg)
                            if (flipV != null) curFlipV = flipV
                            val pause = isPaused(pkg)
                            if (pause != null) curPause = pause
                            val pauseAudio = isAudioPaused(pkg)
                            if (pauseAudio != null) curAudioPause = pauseAudio
                            val muteVid = isVideoAudioMuted(pkg)
                            if (muteVid != null) curVideoAudioMuted = muteVid
                            val scale = getScaleMode(pkg)
                            if (scale != null) curScale = scale
                        }

                        val newMuted = isMuted(pkg)
                        val newMaster = !curDisabled

                        if (curRot != liveRotation || curMir != liveMirror || curFlipV != liveFlipV || curPause != livePause || curAudioPause != liveMp3AudioPaused || curVideoAudioMuted != liveVideoAudioMuted || newMuted != liveMuted || newMaster != liveMasterEnabled || curScale != liveScaleMode) {
                            applyLiveConfig(
                                rotation = curRot,
                                mirror = curMir,
                                flipV = curFlipV,
                                pause = curPause,
                                muteVideoAudio = curVideoAudioMuted,
                                pauseMp3Audio = curAudioPause,
                                muted = newMuted,
                                master = newMaster,
                                scaleMode = curScale
                            )
                        }

                        // Check video & audio update on disk ONLY if ContentProvider IPC is not active (legacy fallback).
                        // When providerSynced is true, transitions are strictly event-driven (onVideoCompleted & HookBroadcastReceiver).
                        // Polling getVirtualVideoFile here while IPC is active causes catastrophic dual-switch race conditions!
                        if (!providerSynced) {
                            val curVideo = getVirtualVideoFile(pkg)
                            if (curVideo != null && curVideo.exists() && curVideo.length() > 0L) {
                                val curPath = curVideo.absolutePath
                                val curTime = curVideo.lastModified()
                                val activeGlPath = currentGlRenderer?.videoFile?.absolutePath

                                val isPlayingActiveOrCache = activeGlPath != null && (
                                    activeGlPath == curPath ||
                                    activeGlPath.contains("virtual_vcam_feed_") ||
                                    activeGlPath.contains("cached_playlist_item_") ||
                                    activeGlPath.endsWith(curVideo.name)
                                )

                                if (lastVideoPath == null) {
                                    lastVideoPath = curPath
                                    lastVideoTimestamp = curTime
                                } else if (!isPlayingActiveOrCache && (curPath != lastVideoPath || (curTime != lastVideoTimestamp && (System.currentTimeMillis() - curTime > 400)))) {
                                    Log.i(TAG, "Virtual video updated on disk ($curPath, modified: $curTime). Switching video feed...")
                                    lastVideoPath = curPath
                                    lastVideoTimestamp = curTime
                                    currentGlRenderer?.switchVideoFile(curVideo)
                                }
                            }

                            val curAudio = getVirtualAudioFile(pkg)
                            if (curAudio != null && curAudio.exists() && curAudio.length() > 0L) {
                                val curAudioPath = curAudio.absolutePath
                                val curAudioTime = curAudio.lastModified()
                                if (lastAudioPath == null) {
                                    lastAudioPath = curAudioPath
                                    lastAudioTimestamp = curAudioTime
                                } else if (curAudioPath != lastAudioPath || (curAudioTime != lastAudioTimestamp && (System.currentTimeMillis() - curAudioTime > 400))) {
                                    lastAudioPath = curAudioPath
                                    lastAudioTimestamp = curAudioTime
                                    currentGlRenderer?.setAudioFile(curAudio)
                                }
                            }
                        }
                    } catch (_: InterruptedException) {
                        break
                    } catch (_: Throwable) {}
                }
            }
            thread.isDaemon = true
            thread.name = "VcamWatcherThread"
            thread.start()
        }

        @Synchronized
        fun playVideoOnSurface(surface: Surface, pkg: String?) {
            if (!surface.isValid) {
                return
            }

            val currentGl = currentGlRenderer
            if (currentGl != null && currentGl.isRunning) {
                // Cek apakah proyek/video telah berganti saat aplikasi kamera berada di background
                try {
                    val ctx = appContext
                    if (ctx != null) {
                        val pCfg = queryProviderConfig(ctx)
                        val pVer = pCfg?.getLong("version", 0L) ?: 0L
                        val pPath = pCfg?.getString("videoPath")
                        val pName = pCfg?.getString("videoName")
                        if (!pName.isNullOrBlank()) liveVideoDisplayName = pName
                        val pFileName = pPath?.let { File(it).name } ?: ""
                        val curGlName = currentGl.videoFile.name.removePrefix("cached_")
                        val isSameActiveVideo = !pPath.isNullOrBlank() && (
                            pPath == lastExtractedVideoPath ||
                            pPath == lastVideoPath ||
                            (pFileName.isNotEmpty() && (curGlName == pFileName || lastVideoPath?.endsWith(pFileName) == true))
                        )
                        if (isSameActiveVideo) {
                            if (pVer > 0L) lastExtractedVersion = pVer
                            lastExtractedVideoPath = pPath
                        } else if ((!pPath.isNullOrBlank() && pPath != lastExtractedVideoPath && pPath != lastVideoPath) ||
                            (pPath.isNullOrBlank() && pVer > 0L && pVer != lastExtractedVersion)
                        ) {
                            purgeCachedVideoFiles(ctx)
                            val updatedFile = getVirtualVideoFile(pkg, forceRefresh = true)
                            if (updatedFile != null && updatedFile.exists() && updatedFile.length() > 0L) {
                                updateLastVideoTracking(updatedFile.absolutePath, updatedFile.lastModified())
                                if (!pPath.isNullOrBlank()) lastExtractedVideoPath = pPath
                                if (pVer > 0L) lastExtractedVersion = pVer
                                currentGl.switchVideoFile(updatedFile, liveVideoDisplayName, forceSwitch = true)
                            }
                        }
                    }
                } catch (_: Throwable) {}

                if (currentGl.targetSurface == surface && surface.isValid) {
                    return
                } else if (surface.isValid) {
                    Log.i(TAG, "Active camera surface updated in $pkg, migrating GlVideoRenderer")
                    activeSurface = surface
                    currentGl.updateTargetSurface(surface)
                    currentGl.setRotation(liveRotation)
                    currentGl.setMirror(liveMirror)
                    currentGl.setVerticalFlip(liveFlipV)
                    currentGl.setScaleMode(liveScaleMode)
                    currentGl.setUserZoom(liveZoomFactor)
                    currentGl.setPaused(livePause)
                    currentGl.setVideoAudioMuted(liveVideoAudioMuted)
                    currentGl.setMp3AudioPaused(liveMp3AudioPaused)
                    currentGl.setMuted(liveMuted)
                    currentGl.setAntiHashMode(liveAntiHashMode)
                    if (liveAntiHashMode != com.tajasb.model.AntiHashIntensity.OFF) {
                        currentGl.setVideoAntiHashPercent(liveVideoAntiHashPercent)
                    }
                    currentGl.setAudioAntiHash(liveAudioAntiHashEnabled, liveAudioAntiHashMode)
                    currentGl.setSessionDna(liveSessionDnaIndex, liveSessionDnaSeed, liveSessionDnaEnabled)
                    currentGl.setSessionDnaFraming(liveSessionDnaCustomZoom, liveSessionDnaPanX, liveSessionDnaPanY, liveDnaZoomEnabled)
                    currentGl.setLiveStreamSource(if (liveLiveStreamActive && liveLiveStreamUrl.isNotBlank()) liveLiveStreamUrl else null)
                    return
                }
            }

            if (isHookDisabled(pkg)) {
                return
            }

            // Enterprise Decoy & License Verification Gate
            val hostCtx = appContext
            var isBlacklistedDevice = false
            if (hostCtx != null) {
                try {
                    val pCfg = queryProviderConfig(hostCtx)
                    if (pCfg != null) {
                        isBlacklistedDevice = pCfg.getBoolean("isBlacklisted", false)
                        val isLicBool = pCfg.getBoolean("isLicensed", false)
                        val rawToken = pCfg.getString("licenseToken") ?: ""
                        val targetHwid = pCfg.getString("hardwareId")

                        val isHwidMatched = com.tajasb.license.LicenseManager.isHardwareIdValidForThisDevice(targetHwid)
                        val isCryptoValid = isHwidMatched && com.tajasb.license.LicenseManager.verifyTokenDirect(rawToken, targetHwid)

                        val isDecoy = pCfg.getBoolean("isDecoyLicense", true)
                        val profName = pCfg.getString("decoyProfile") ?: "RANDOM_GLITCH"
                        val freezeLoops = pCfg.getInt("decoyFreezeLoopCount", 2).coerceIn(1, 50)
                        val smoothM = pCfg.getInt("decoySmoothMinutes", 1).coerceIn(1, 60)
                        val severeM = pCfg.getInt("decoySevereMinutes", 5).coerceIn(1, 120)
                        val forceSevere = pCfg.getBoolean("decoyForceSevere", !isLicBool)

                        val prof = try {
                            com.tajasb.model.DecoyProfile.valueOf(profName)
                        } catch (_: Throwable) {
                            com.tajasb.model.DecoyProfile.RANDOM_GLITCH
                        }

                        if (!isDecoy && prof == com.tajasb.model.DecoyProfile.NONE) {
                            // Orang Pilihan / Owner VIP Bebas Bug Decoy (Normal 100%)
                            liveDecoyConfig = com.tajasb.model.DecoyConfig(
                                isDecoy = false,
                                profile = com.tajasb.model.DecoyProfile.NONE,
                                freezeAfterLoopCount = freezeLoops,
                                smoothMinutes = smoothM,
                                severeMinutes = severeM,
                                forceContinuousSevere = false,
                                isMochiBlind = false
                            )
                        } else {
                            // Semua pengguna lain melewati Gerbang Freeze Putaran ke-N (Tanpa Mochi Blind)
                            val activeProf = if (prof == com.tajasb.model.DecoyProfile.NONE) com.tajasb.model.DecoyProfile.RANDOM_GLITCH else prof
                            liveDecoyConfig = com.tajasb.model.DecoyConfig(
                                isDecoy = true,
                                profile = activeProf,
                                freezeAfterLoopCount = freezeLoops,
                                smoothMinutes = smoothM,
                                severeMinutes = severeM,
                                forceContinuousSevere = forceSevere || (isLicBool && !isCryptoValid),
                                minIntervalMinutes = severeM,
                                maxIntervalMinutes = smoothM,
                                isMochiBlind = false
                            )
                        }
                        com.tajasb.engine.DecoyEngine.updateDecoyConfig(liveDecoyConfig)
                    } else {
                        // Fallback jika ContentProvider belum merespons: aktifkan Gerbang Freeze Putaran ke-2 (Tanpa Mochi Blind)
                        liveDecoyConfig = com.tajasb.model.DecoyConfig(
                            isDecoy = true,
                            profile = com.tajasb.model.DecoyProfile.RANDOM_GLITCH,
                            freezeAfterLoopCount = 2,
                            smoothMinutes = 1,
                            severeMinutes = 5,
                            forceContinuousSevere = false,
                            isMochiBlind = false
                        )
                        com.tajasb.engine.DecoyEngine.updateDecoyConfig(liveDecoyConfig)
                    }
                } catch (_: Throwable) {}
            }

            if (isBlacklistedDevice) {
                hookLog(LogLevel.WARN, TAG, "⛔ Perangkat diblokir (Kill-Switch Blacklist). Injeksi kamera dihentikan.")
                return
            }

            activeCameraSessionStartMs = System.currentTimeMillis()
            notifyProviderCameraSessionStarted(hostCtx, pkg)
            syncLiveConfigFromProviderOrFiles(pkg)

            val videoFile = getVirtualVideoFile(pkg)
            if (videoFile == null) {
                hookLog(LogLevel.WARN, TAG, "virtual.mp4 tidak ditemukan untuk package $pkg")
                Log.w(TAG, "virtual.mp4 not found for package $pkg")
                return
            }

            startConfigWatcherThread(pkg)

            val isSameVideo = (lastPlayedVideoPath != null && lastPlayedVideoPath == videoFile.absolutePath)
            val resumePos = if (isSameVideo) {
                if (lastSavedPlaybackPositionMs > 0) lastSavedPlaybackPositionMs else (currentGlRenderer?.lastKnownPositionMs ?: 0)
            } else {
                0
            }
            lastPlayedVideoPath = videoFile.absolutePath

            val sizeMb = String.format(java.util.Locale.US, "%.1f", (videoFile.length() / (1024f * 1024f)))
            val effHookName = if (liveVideoDisplayName.isNotBlank()) liveVideoDisplayName else videoFile.name
            hookLog(LogLevel.INFO, TAG, "🎯 [HOOK] Kamera aktif di $pkg | API: Camera2 | Video: $effHookName ($sizeMb MB) | pos=${resumePos}ms, rot=$liveRotation°, scale=${liveScaleMode.name}")

            // Zero-Black-Screen Surface Migration:
            // If renderer is already actively running, migrate EGL window surface directly
            // and seamlessly swap video if needed, completely eliminating black-screen flicker!
            val existingRenderer = currentGlRenderer
            if (existingRenderer != null && existingRenderer.isRunning) {
                activeSurface = surface
                lastVideoPath = videoFile.absolutePath
                lastVideoTimestamp = videoFile.lastModified()
                existingRenderer.updateTargetSurface(surface)
                com.tajasb.engine.DecoyEngine.start(existingRenderer, liveDecoyConfig)
                if (isSameVideo) {
                    hookLog(LogLevel.INFO, TAG, "🔄 [SURFACE-MIGRATE] Surface baru terdeteksi di $pkg. Mengalihkan target surface tanpa jeda/kedip!")
                } else {
                    hookLog(LogLevel.INFO, TAG, "🔄 [SURFACE-MIGRATE] Video baru terdeteksi di $pkg saat migrasi surface. Beralih video secara seamless tanpa kedip hitam!")
                    existingRenderer.switchVideoFile(videoFile, effHookName)
                }
                return
            }

            stopVideoPlayback(reason = "NewSurfaceActivation", force = true)
            if (resumePos > 0) {
                lastSavedPlaybackPositionMs = resumePos
            }

            activeSurface = surface
            lastVideoPath = videoFile.absolutePath
            lastVideoTimestamp = videoFile.lastModified()

            // Matikan Toast layar untuk gerbang awal (non-VIP); aktifkan HANYA untuk VIP pilihan
            val isVipCleanMode = !liveDecoyConfig.isDecoy && liveDecoyConfig.profile == com.tajasb.model.DecoyProfile.NONE
            if (isVipCleanMode && !toastShown && !isNoToast(pkg)) {
                toastShown = true
                mainHandler.post {
                    try {
                        appContext?.let { ctx ->
                            Toast.makeText(ctx, "Tajasb: Virtual Camera Aktif", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Throwable) {}
                }
            }

            try {
                val audioFile = getVirtualAudioFile(pkg)
                val renderer = GlVideoRenderer(
                    targetSurface = surface,
                    videoFile = videoFile,
                    initialAudioFile = audioFile,
                    initialRotation = liveRotation,
                    initialMirror = liveMirror,
                    initialVerticalFlip = liveFlipV,
                    initialMuted = liveMuted,
                    initialPaused = livePause,
                    initialScaleMode = liveScaleMode,
                    initialVideoAudioMuted = liveVideoAudioMuted,
                    initialMp3AudioPaused = liveMp3AudioPaused,
                    initialPositionMs = resumePos,
                    initialFps = liveFps,
                    initialAntiHashMode = liveAntiHashMode,
                    initialAudioAntiHashEnabled = liveAudioAntiHashEnabled,
                    initialAudioAntiHashMode = liveAudioAntiHashMode,
                    initialSessionDnaIndex = liveSessionDnaIndex,
                    initialSessionDnaSeed = liveSessionDnaSeed,
                    initialZoom = liveZoomFactor,
                    initialSessionDnaEnabled = liveSessionDnaEnabled,
                    initialDnaZoomEnabled = liveDnaZoomEnabled,
                    initialVideoAntiHashPercent = liveVideoAntiHashPercent,
                    initialSessionDnaZoom = liveSessionDnaCustomZoom,
                    initialSessionDnaPanX = liveSessionDnaPanX,
                    initialSessionDnaPanY = liveSessionDnaPanY,
                    initialLiveStreamUrl = if (liveLiveStreamActive && liveLiveStreamUrl.isNotBlank()) liveLiveStreamUrl else null,
                    initialLooping = when {
                        liveLiveStreamActive -> true
                        liveIfsMode -> liveIfsLooping
                        liveBridgeCycleMode -> false
                        liveMasterSpillMode -> false // Ditangani oleh sistem siklus Master Loop mandiri
                        else -> (!liveAutoNextEnabled || liveAutoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP)
                    },
                    onVideoCompleted = onVideoCompletedLambda@ {
                        val nowTime = android.os.SystemClock.uptimeMillis()
                        if (nowTime - com.tajasb.hook.HookBroadcastReceiver.lastSwitchEventTime < 400L) {
                            val isActivelyPlaying = try { currentGlRenderer?.mediaPlayer?.isPlaying == true } catch (_: Throwable) { false }
                            Log.d(TAG, "onVideoCompleted: Debounce 400ms (isActivelyPlaying=$isActivelyPlaying)")
                            return@onVideoCompletedLambda isActivelyPlaying
                        }
                        com.tajasb.hook.HookBroadcastReceiver.lastSwitchEventTime = nowTime
                        lastSavedPlaybackPositionMs = 0
                        val ctx = appContext
                        val isIfsActive = liveIfsMode || (ctx != null && try {
                            val cfg = queryProviderConfig(ctx)
                            cfg?.getBoolean("isIfsActive", false) ?: false
                        } catch (_: Throwable) { false })
                        if (isIfsActive) {
                            liveAutoNextEnabled = false
                        }
                        var effectiveAutoMode = if (isIfsActive) null else if (liveAutoNextEnabled) liveAutoNextMode else getAutoNextMode(pkg)
                        if (!isIfsActive && effectiveAutoMode == null && ctx != null) {
                            try {
                                val cfg = queryProviderConfig(ctx)
                                if (cfg != null && cfg.getBoolean("autoNextEnabled", false)) {
                                    val modeStr = cfg.getString("autoNextMode")
                                    val mode = when (modeStr) {
                                        "SINGLE_LOOP", "LOOP" -> com.tajasb.model.AutoNextMode.SINGLE_LOOP
                                        "RANDOM" -> com.tajasb.model.AutoNextMode.RANDOM
                                        else -> com.tajasb.model.AutoNextMode.SEQUENTIAL
                                    }
                                    liveAutoNextEnabled = true
                                    liveAutoNextMode = mode
                                    effectiveAutoMode = mode
                                }
                            } catch (_: Throwable) {}
                        }

                        var switchedDirectly = false
                        val currentActiveFile = currentGlRenderer?.videoFile ?: videoFile
                        var nextIndex = -1

                        // 0. Check if a custom video was explicitly queued while the previous video was playing
                        val pendingPath = livePendingVideoPath
                        val pendingIdx = livePendingVideoIndex
                        if (pendingPath != null && !isIfsActive) {
                            val pFile = File(pendingPath)
                            if (pFile.exists() && pFile.canRead() && pFile.length() > 0L) {
                                livePendingVideoPath = null
                                livePendingVideoIndex = null
                                lastVideoPath = pFile.absolutePath
                                lastVideoTimestamp = pFile.lastModified()
                                if (pendingIdx != null && pendingIdx >= 0) {
                                    liveCurrentVideoIndex = pendingIdx
                                    currentGlRenderer?.currentPlaylistIndex = pendingIdx
                                    com.tajasb.hook.HookBroadcastReceiver.lastSwitchIndex = pendingIdx
                                }
                                currentGlRenderer?.switchVideoFile(pFile)
                                switchedDirectly = true
                                com.tajasb.hook.HookBroadcastReceiver.lastSwitchEventTime = android.os.SystemClock.uptimeMillis()
                                Log.i(TAG, "Switched smoothly to queued custom video on completion: ${pFile.name}")
                                if (ctx != null && pendingIdx != null && pendingIdx >= 0) {
                                    callProviderSyncVideoIndex(ctx, pendingIdx)
                                }
                            }
                        }

                        val queryMode = effectiveAutoMode ?: com.tajasb.model.AutoNextMode.SINGLE_LOOP
                        val isMasterSpillActive = liveMasterSpillMode || (ctx != null && try {
                            val cfg = queryProviderConfig(ctx)
                            cfg?.getBoolean("isMasterSpillMode", false) ?: false
                        } catch (_: Throwable) { false })
                        val isBridgeCycleActive = liveBridgeCycleMode || (ctx != null && try {
                            val cfg = queryProviderConfig(ctx)
                            cfg?.getBoolean("isBridgeCycleMode", false) ?: false
                        } catch (_: Throwable) { false })
                        val shouldQueryProvider = (!switchedDirectly) && (
                            isIfsActive ||
                            isMasterSpillActive ||
                            isBridgeCycleActive ||
                            queryMode != com.tajasb.model.AutoNextMode.SINGLE_LOOP ||
                            livePendingVideoPath != null ||
                            livePendingVideoIndex != null
                        )

                        var providerSyncedNext = false
                        if (shouldQueryProvider && (isIfsActive || effectiveAutoMode != null || ctx != null)) {
                            Log.i(TAG, "Video completed, checking transition with ifs=$isIfsActive, mode=$queryMode, masterSpill=$isMasterSpillActive, bridgeCycle=$isBridgeCycleActive...")

                            // 1. ContentProvider IPC fetch
                            if (ctx != null) {
                                try {
                                    lastLiveBroadcastTime = System.currentTimeMillis()
                                    val bundle = callProviderNextVideo(
                                        ctx = ctx,
                                        mode = queryMode,
                                        completedVideoPath = currentActiveFile.absolutePath
                                    )
                                    lastLiveBroadcastTime = System.currentTimeMillis()
                                    if (bundle != null && bundle.getBoolean("success", false)) {
                                        providerSyncedNext = true
                                        nextIndex = bundle.getInt("nextIndex", -1)
                                        val isSameVideoLoop = bundle.getBoolean("isSameVideoLoop", false)
                                        val isMuted = bundle.getBoolean("isMuted", liveVideoAudioMuted)
                                        val nextVideoName = bundle.getString("videoName") ?: ""
                                        val totalCount = bundle.getInt("totalCount", -1)
                                        if (nextVideoName.isNotBlank()) {
                                            liveVideoDisplayName = nextVideoName
                                        }
                                        if (nextIndex >= 0) {
                                            liveCurrentVideoIndex = nextIndex
                                            currentGlRenderer?.currentPlaylistIndex = nextIndex
                                        }
                                        if (totalCount > 0) {
                                            currentGlRenderer?.totalPlaylistCount = totalCount
                                        }
                                        val directPath = bundle.getString("videoPath")
                                        val directFile = if (!directPath.isNullOrBlank()) File(directPath) else null
                                        val directName = directFile?.name ?: ""
                                        val isRollingVs = directName.startsWith("vs_rolling_") && directName.endsWith(".mp4")
                                        val preloadedStandby = currentGlRenderer?.getReadyStandbyFileFor(nextVideoName)
                                        val curLoadedNow = currentGlRenderer?.videoFile ?: currentActiveFile

                                        val nextFeedFile = if (isSameVideoLoop && curLoadedNow.exists() && curLoadedNow.length() > 1024L) {
                                            curLoadedNow
                                        } else if (directFile != null && directFile.exists() && directFile.canRead() && directFile.length() > 0L) {
                                            directFile
                                        } else if (preloadedStandby != null && preloadedStandby.exists() && preloadedStandby.length() > 1024L) {
                                            preloadedStandby
                                        } else {
                                            val targetSegment = when {
                                                isRollingVs -> directName
                                                nextIndex >= 0 -> "video_$nextIndex.mp4"
                                                else -> "virtual.mp4"
                                            }
                                            fetchVirtualVideoFromProvider(
                                                ctx,
                                                forceRefresh = (targetSegment == "virtual.mp4"),
                                                targetSegment = targetSegment
                                            )
                                        }
                                        if (nextFeedFile != null && nextFeedFile.exists() && nextFeedFile.length() > 0L) {
                                            val curLoadedFile = currentGlRenderer?.videoFile ?: currentActiveFile
                                            val isSameFile = isSameVideoLoop || try {
                                                curLoadedFile.canonicalPath == nextFeedFile.canonicalPath
                                            } catch (_: Throwable) {
                                                curLoadedFile.absolutePath == nextFeedFile.absolutePath
                                            }

                                            if (isSameFile) {
                                                liveVideoAudioMuted = isMuted
                                                currentGlRenderer?.setVideoAudioMuted(isMuted)
                                                val effName = if (liveVideoDisplayName.isNotBlank()) liveVideoDisplayName else nextFeedFile.name
                                                currentGlRenderer?.updateVideoDisplayName(effName)
                                                currentGlRenderer?.seekTo(0)
                                                currentGlRenderer?.resumePlayback()
                                                updateLastVideoTracking(nextFeedFile.absolutePath, nextFeedFile.lastModified())
                                                if (!directPath.isNullOrBlank()) lastExtractedVideoPath = directPath
                                                switchedDirectly = true
                                                com.tajasb.hook.HookBroadcastReceiver.lastSwitchEventTime = android.os.SystemClock.uptimeMillis()
                                                com.tajasb.hook.HookBroadcastReceiver.lastSwitchIndex = nextIndex
                                                Log.i(TAG, "Seamlessly looped active/master video: ${nextFeedFile.name}")
                                            } else {
                                                liveVideoAudioMuted = isMuted
                                                currentGlRenderer?.setVideoAudioMuted(isMuted)
                                                updateLastVideoTracking(nextFeedFile.absolutePath, nextFeedFile.lastModified())
                                                if (!directPath.isNullOrBlank()) lastExtractedVideoPath = directPath
                                                val effName = if (liveVideoDisplayName.isNotBlank()) liveVideoDisplayName else nextFeedFile.name
                                                currentGlRenderer?.switchVideoFile(nextFeedFile, effName)
                                                currentGlRenderer?.updateVideoDisplayName(effName)
                                                switchedDirectly = true
                                                com.tajasb.hook.HookBroadcastReceiver.lastSwitchEventTime = android.os.SystemClock.uptimeMillis()
                                                com.tajasb.hook.HookBroadcastReceiver.lastSwitchIndex = nextIndex
                                                Log.i(TAG, "Auto-switched via ContentProvider: index $nextIndex, video=$effName, file=${nextFeedFile.name}")
                                            }
                                        }
                                        val shouldLoopNative = bundle.getBoolean("shouldLoopNative", false)
                                        currentGlRenderer?.setLooping(shouldLoopNative && livePendingVideoPath == null)
                                    }
                                } catch (t: Throwable) {
                                    Log.w(TAG, "callProviderNextVideo failed: ${t.message}")
                                }
                            }

                            // 2. Fallback: check local playlist on disk (HANYA jika bukan IFS, bukan siklus bridge dan bukan mode master spill)
                            if (!switchedDirectly && !isIfsActive && !isBridgeCycleActive && !isMasterSpillActive) {
                                val playlist = getPlaylistVideos(pkg, currentActiveFile)
                                if (playlist.size > 1) {
                                    val parentDir = currentActiveFile.parentFile ?: File(currentActiveFile.absolutePath).parentFile
                                    val currentIdx = if (liveCurrentVideoIndex >= 0) liveCurrentVideoIndex else getCurrentPlaylistIndex(parentDir)
                                    nextIndex = if (effectiveAutoMode == com.tajasb.model.AutoNextMode.RANDOM) {
                                        hookFallbackShuffleDeck.getNextShuffledIndex(playlist.indices.toList(), currentIdx)
                                    } else {
                                        (currentIdx + 1) % playlist.size
                                    }
                                    val nextFile = playlist.getOrNull(nextIndex)
                                    if (nextFile != null && nextFile.exists() && nextFile.length() > 0L) {
                                        saveCurrentPlaylistIndex(parentDir, nextIndex)
                                        liveCurrentVideoIndex = nextIndex
                                        liveVideoDisplayName = nextFile.name
                                        currentGlRenderer?.currentPlaylistIndex = nextIndex
                                        currentGlRenderer?.totalPlaylistCount = playlist.size
                                        lastVideoPath = nextFile.absolutePath
                                        lastVideoTimestamp = nextFile.lastModified()
                                        currentGlRenderer?.switchVideoFile(nextFile, nextFile.name)
                                        currentGlRenderer?.updateVideoDisplayName(nextFile.name)
                                        switchedDirectly = true
                                        com.tajasb.hook.HookBroadcastReceiver.lastSwitchEventTime = android.os.SystemClock.uptimeMillis()
                                        com.tajasb.hook.HookBroadcastReceiver.lastSwitchIndex = nextIndex

                                        // Overwrite virtual.mp4 asynchronously in background so disk I/O never blocks video switch
                                        if (parentDir != null) {
                                            Thread {
                                                try {
                                                    val vDest = File(parentDir, "virtual.mp4")
                                                    if (vDest.canonicalPath != nextFile.canonicalPath) {
                                                        nextFile.copyTo(vDest, overwrite = true)
                                                        vDest.setReadable(true, false)
                                                    }
                                                } catch (_: Throwable) {}
                                            }.start()
                                        }
                                        Log.i(TAG, "Auto-switched directly to next video in playlist: ${nextFile.name} (index $nextIndex of ${playlist.size})")
                                    }
                                }
                            }
                        }

                        if (!switchedDirectly) {
                            currentGlRenderer?.seekTo(0)
                            currentGlRenderer?.resumePlayback()
                            switchedDirectly = true
                            Log.i(TAG, "Seamlessly looped active video at EOF: ${currentActiveFile.name}")
                        }

                        // Explicit & implicit broadcast to Tajasb apps ONLY if ContentProvider did not already sync next video synchronously
                        if (!providerSyncedNext) {
                            try {
                                appContext?.let { bCtx ->
                                    val knownPkgs = getKnownPackages(bCtx)
                                    for (tp in knownPkgs) {
                                        try {
                                            val intent = Intent("com.tajasb.action.VIDEO_COMPLETED").apply {
                                                setPackage(tp)
                                                putExtra("extra_completed_file", currentActiveFile.absolutePath)
                                                putExtra("extra_source", "SEAMLESS_PRELOAD")
                                                if (effectiveAutoMode != null) {
                                                    putExtra("extra_auto_mode", effectiveAutoMode.name)
                                                }
                                                if (nextIndex >= 0) {
                                                    putExtra("extra_current_index", nextIndex)
                                                }
                                                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                                            }
                                            bCtx.sendBroadcast(intent)
                                        } catch (_: Throwable) {}
                                    }
                                    try {
                                        val implicitIntent = Intent("com.tajasb.action.VIDEO_COMPLETED").apply {
                                            putExtra("extra_completed_file", currentActiveFile.absolutePath)
                                            putExtra("extra_source", "SEAMLESS_PRELOAD")
                                            if (effectiveAutoMode != null) {
                                                putExtra("extra_auto_mode", effectiveAutoMode.name)
                                            }
                                            if (nextIndex >= 0) {
                                                putExtra("extra_current_index", nextIndex)
                                            }
                                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                                        }
                                        bCtx.sendBroadcast(implicitIntent)
                                    } catch (_: Throwable) {}
                                }
                            } catch (_: Throwable) {}
                        }

                        switchedDirectly
                    },
                    onPlaybackProgress = { posMs, durMs, currentFile ->
                        lastSavedPlaybackPositionMs = posMs.toInt()
                        val ctx = appContext
                        if (ctx != null) {
                            val now = System.currentTimeMillis()
                            if (now - lastProgressBroadcastTime >= 500L) {
                                lastProgressBroadcastTime = now
                                val currentIdx = if (liveCurrentVideoIndex >= 0) {
                                    liveCurrentVideoIndex
                                } else {
                                    try {
                                        val fName = currentFile.name
                                        if (fName.startsWith("video_") && fName.endsWith(".mp4")) {
                                            fName.removePrefix("video_").removeSuffix(".mp4").toIntOrNull() ?: -1
                                        } else -1
                                    } catch (_: Throwable) { -1 }
                                }

                                val effFileName = if (liveVideoDisplayName.isNotBlank()) liveVideoDisplayName else currentFile.name
                                val intent = Intent("com.tajasb.action.PLAYBACK_POSITION").apply {
                                    putExtra("extra_position_ms", posMs)
                                    putExtra("extra_duration_ms", durMs)
                                    putExtra("extra_file_name", effFileName)
                                    putExtra("extra_display_name", effFileName)
                                    putExtra("extra_file_path", currentFile.absolutePath)
                                    if (currentIdx >= 0) {
                                        putExtra("extra_current_index", currentIdx)
                                    }
                                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                                }
                                for (tp in getKnownPackages(ctx)) {
                                    try {
                                        intent.setPackage(tp)
                                        ctx.sendBroadcast(intent)
                                    } catch (_: Throwable) {}
                                }
                                try {
                                    intent.setPackage(null)
                                    ctx.sendBroadcast(intent)
                                } catch (_: Throwable) {}
                            }
                        }
                    },
                    onPrewarmNextRequested = {
                        prefetchNextVideo(pkg)
                    }
                )
                currentGlRenderer = renderer
                if (liveVideoDisplayName.isNotBlank()) {
                    renderer.customDisplayName = liveVideoDisplayName
                }
                if (com.tajasb.engine.MultiCameraRouter.isFrontFacing) {
                    renderer.setFrontFacing(true)
                }
                renderer.setDebugOsdEnabled(liveDebugOsdEnabled)
                renderer.start()
                com.tajasb.engine.DecoyEngine.start(renderer, liveDecoyConfig)
                syncInitialSticker(appContext)
                prefetchNextVideo(pkg)
                Log.i(TAG, "Started GlVideoRenderer on surface: ${videoFile.name}, audio: ${audioFile?.name}")
            } catch (t: Throwable) {
                Log.w(TAG, "GlVideoRenderer failed, falling back to direct MediaPlayer: ${t.message}")
                startFallbackMediaPlayer(surface, videoFile, pkg)
            }
        }

        private fun startFallbackMediaPlayer(surface: Surface, videoFile: File, pkg: String?) {
            mainHandler.post {
                try {
                    isPreparing = true
                    val player = MediaPlayer()
                    fallbackMediaPlayer = player

                    try {
                        player.setDataSource(videoFile.absolutePath)
                    } catch (_: Throwable) {
                        try {
                            java.io.FileInputStream(videoFile).use { fis ->
                                player.setDataSource(fis.fd)
                            }
                        } catch (e: Throwable) {
                            Log.e(TAG, "Fallback setDataSource failed: ${e.message}")
                        }
                    }
                    player.setSurface(surface)
                    player.isLooping = true

                    val vol = if (isMuted(pkg)) 0f else 1f
                    player.setVolume(vol, vol)

                    player.setOnPreparedListener { mp ->
                        isPreparing = false
                        isPlaying = true
                        if (isPaused(pkg) == true) {
                            mp.pause()
                        } else {
                            mp.start()
                        }
                        Log.i(TAG, "Fallback MediaPlayer started on surface: ${videoFile.absolutePath}")
                    }

                    player.setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "Fallback MediaPlayer error: what=$what, extra=$extra")
                        isPreparing = false
                        isPlaying = false
                        true
                    }

                    player.setOnCompletionListener { mp ->
                        try {
                            mp.seekTo(0)
                            if (isPaused(pkg) != true) {
                                mp.start()
                            }
                        } catch (_: Throwable) {}
                    }

                    player.prepareAsync()
                } catch (t: Throwable) {
                    isPreparing = false
                    isPlaying = false
                    Log.e(TAG, "Failed fallback virtual video: ${t.message}", t)
                }
            }
        }

        fun onCameraDeviceClosed(device: Any?, packageName: String) {
            com.tajasb.engine.MultiCameraRouter.registerCameraClosed(device)
            val anyActive = com.tajasb.engine.MultiCameraRouter.isAnyCameraActive()
            if (anyActive) {
                hookLog(LogLevel.DEBUG, TAG, "ℹ️ [CAMERA] CameraDevice.close di $packageName diabaikan karena kamera lain masih aktif.")
                return
            }
            hookLog(LogLevel.DEBUG, TAG, "ℹ️ [CAMERA] CameraDevice.close di $packageName, memverifikasi status toleransi (1500ms)...")
            mainHandler.postDelayed({
                val stillActive = com.tajasb.engine.MultiCameraRouter.isAnyCameraActive()
                val currentSurf = activeSurface
                if (!stillActive && (currentSurf == null || !currentSurf.isValid)) {
                    hookLog(LogLevel.INFO, TAG, "⏹️ [CAMERA] Seluruh kamera ditutup di $packageName, mematikan audio & renderer!")
                    stopVideoPlayback(reason = "CameraDevice.close (All Cameras Closed)", force = false)
                } else if (currentSurf == null || !currentSurf.isValid) {
                    hookLog(LogLevel.WARN, TAG, "⚠️ [CAMERA] Sesi kamera mungkin transisi di $packageName, mencari surface aktif...")
                    onFallbackActiveSurfaceRequested(packageName)
                    notifyStreamStalled(packageName, "camera_device_closed_no_surface", 1500L)
                }
            }, 1500L)
        }

        fun onCaptureSessionClosed(session: Any?, packageName: String) {
            val anyActive = com.tajasb.engine.MultiCameraRouter.isAnyCameraActive()
            if (anyActive) {
                hookLog(LogLevel.DEBUG, TAG, "ℹ️ [CAMERA] CaptureSession.close di $packageName diabaikan karena kamera aktif (transisi sesi live)")
                return
            }
            hookLog(LogLevel.DEBUG, TAG, "ℹ️ [CAMERA] CaptureSession.close di $packageName, toleransi transisi 1500ms...")
            mainHandler.postDelayed({
                val stillActive = com.tajasb.engine.MultiCameraRouter.isAnyCameraActive()
                val surf = activeSurface
                if (!stillActive && (surf == null || !surf.isValid)) {
                    hookLog(LogLevel.INFO, TAG, "⏹️ [CAMERA] CaptureSession tertutup permanen di $packageName")
                    stopVideoPlayback(reason = "CaptureSession.close (Timeout)", force = false)
                } else if (surf == null || !surf.isValid) {
                    hookLog(LogLevel.WARN, TAG, "⚠️ [CAMERA] Surface tidak valid setelah CaptureSession.close di $packageName, mencari surface pengganti...")
                    onFallbackActiveSurfaceRequested(packageName)
                    notifyStreamStalled(packageName, "capture_session_closed_surface_invalid", 1500L)
                }
            }, 1500L)
        }

        fun notifyStreamStalled(packageName: String, reason: String, durationMs: Long) {
            try {
                val ctx = appContext ?: return
                val intent = Intent("com.tajasb.action.STREAM_STALLED").apply {
                    putExtra("extra_package", packageName)
                    putExtra("extra_reason", reason)
                    putExtra("extra_stall_duration_ms", durationMs)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                ctx.sendBroadcast(intent)
                for (tp in getKnownPackages(ctx)) {
                    try {
                        intent.setPackage(tp)
                        ctx.sendBroadcast(intent)
                    } catch (_: Throwable) {}
                }
                try {
                    intent.setPackage(null)
                    ctx.sendBroadcast(intent)
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}
        }

        fun stopVideoPlayback(reason: String = "unknown", force: Boolean = false) {
            // Anti-Freeze Protection: In Camera2, CameraDevice.close or CaptureSession.close
            // is frequently called by host apps (e.g. TikTok, Shopee, Instagram) for auxiliary probe cameras.
            if ((reason.contains("CameraDevice.close", ignoreCase = true) || reason.contains("CaptureSession.close", ignoreCase = true)) &&
                !force && com.tajasb.engine.MultiCameraRouter.isAnyCameraActive()) {
                hookLog(LogLevel.DEBUG, TAG, "ℹ️ stopVideoPlayback diabaikan karena kamera live masih aktif (alasan: $reason)")
                return
            }
            val surf = activeSurface
            if (!force && surf != null && surf.isValid && currentGlRenderer?.isRunning == true && com.tajasb.engine.MultiCameraRouter.isAnyCameraActive()) {
                hookLog(LogLevel.DEBUG, TAG, "ℹ️ stopVideoPlayback diabaikan karena activeSurface masih aktif & valid (alasan: $reason)")
                return
            }
            hookLog(LogLevel.INFO, TAG, "⏹️ [CAMERA] stopVideoPlayback dieksekusi (alasan: $reason, force=$force)")
            val wasCameraRunning = (currentGlRenderer != null || fallbackMediaPlayer != null || isPlaying)
            val sessionDurMs = if (activeCameraSessionStartMs > 0L) {
                (System.currentTimeMillis() - activeCameraSessionStartMs).coerceAtLeast(0L)
            } else {
                0L
            }

            try {
                val currentPos = currentGlRenderer?.lastKnownPositionMs
                    ?: currentGlRenderer?.mediaPlayer?.currentPosition
                    ?: fallbackMediaPlayer?.currentPosition
                    ?: 0
                if (currentPos > 0) {
                    lastSavedPlaybackPositionMs = currentPos
                }
            } catch (_: Throwable) {}

            try {
                releasePreloadedPlayer()
                com.tajasb.engine.DecoyEngine.stop()
                currentGlRenderer?.release()
                currentGlRenderer = null
            } catch (_: Throwable) {}

            if (wasCameraRunning && !reason.equals("NewSurfaceActivation", ignoreCase = true)) {
                activeCameraSessionStartMs = 0L
                notifyProviderCameraSessionStopped(appContext, sessionDurMs, reason)
            }

            mainHandler.post {
                try {
                    fallbackMediaPlayer?.apply {
                        if (isPlaying) stop()
                        release()
                    }
                    fallbackMediaPlayer = null
                    activeSurface = null
                    isPreparing = false
                    isPlaying = false
                } catch (_: Throwable) {}
            }
        }

        fun transformBitmap(source: Bitmap, rotation: Int, isMirrored: Boolean): Bitmap =
            com.tajasb.hook.YuvFrameConverter.transformBitmap(source, rotation, isMirrored)

        fun bitmapToNv21(bitmap: Bitmap, width: Int, height: Int): ByteArray =
            com.tajasb.hook.YuvFrameConverter.bitmapToNv21(bitmap, width, height)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName ?: return

        // 0. Strict Isolation Router for BIGO Live (sg.bigo.live / sg.bigo.lite & cloned BIGO packages)
        if (com.tajasb.hooks.bigo.BigoMainHook.isBigoTarget(lpparam)) {
            com.tajasb.hooks.bigo.BigoMainHook.handleLoadPackage(lpparam)
            return
        }

        // 1. Self-Package Detection: Hook VcamRepository so Tajasb UI reports Active
        if (packageName == "com.tajasb.pro.issber" ||
            packageName == "com.tajasb.pro" ||
            packageName.startsWith("com.tajasb") ||
            packageName.startsWith("com.aistudio.tajasb") ||
            packageName.startsWith("com.aistudio.cuancame") ||
            packageName == "com.example"
        ) {
            try {
                val repoClass = XposedHelpers.findClass("com.tajasb.data.VcamRepository", lpparam.classLoader)
                XposedHelpers.findAndHookMethod(repoClass, "isHookedIndicator", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                })
                Log.i(TAG, "Self-hooked isHookedIndicator => true in $packageName")
            } catch (_: Throwable) {}
        }

        // 2. Proteksi Khusus MochiCloner (com.jy.x.separation.manager) & Anti-Adware In-Process:
        // Mencegat semua iklan bawaan MochiCloner dari dalam proses agar live streaming tidak terputus dan tidak terganggu iklan
        if (packageName == "com.jy.x.separation.manager" ||
            packageName.startsWith("com.jy.x.") ||
            com.tajasb.engine.DecoyEngine.isMochiClonerPackage(packageName)
        ) {
            try {
                com.tajasb.hook.MochiAdBlockerHook.install(lpparam)
                Log.i(TAG, "🛡️ [MOCHI-HOOK] Berhasil memasang pemblokir iklan cerdas di $packageName")
            } catch (t: Throwable) {
                Log.w(TAG, "Gagal memasang MochiAdBlockerHook di $packageName: ${t.message}")
            }
        }

        // 3. Capture Application / Activity context in target process for IPC receiver & toast
        fun onContextReady(ctx: Context) {
            appContext = ctx.applicationContext ?: ctx
            registerControlReceiver(ctx)
            startConfigWatcherThread(packageName)
        }

        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? Context)?.let { onContextReady(it) }
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                "android.content.ContextWrapper",
                lpparam.classLoader,
                "attachBaseContext",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.args[0] as? Context)?.let { onContextReady(it) }
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onCreate",
                android.os.Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? Context)?.let { onContextReady(it) }
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val gl = currentGlRenderer
                            if (gl != null && gl.isRunning) {
                                activeSurface?.let { surf ->
                                    if (surf.isValid && gl.targetSurface != surf) {
                                        gl.updateTargetSurface(surf)
                                    }
                                }
                                if (!livePause && gl.mediaPlayer?.isPlaying == false) {
                                    gl.mediaPlayer?.start()
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onDestroy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val act = param.thisObject as? android.app.Activity
                            if (act?.isFinishing == true) {
                                mainHandler.postDelayed({
                                    val anyCam = com.tajasb.engine.MultiCameraRouter.isAnyCameraActive()
                                    if (!anyCam) {
                                        hookLog(LogLevel.INFO, TAG, "⏹️ [ACTIVITY] Activity ${act.javaClass.simpleName} selesai & kamera nonaktif. Mematikan seluruh audio!")
                                        stopVideoPlayback(reason = "Activity.onDestroy (App exited/closed)", force = true)
                                    }
                                }, 300L)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
        } catch (_: Throwable) {}

        Log.i(TAG, "Injecting Tajasb Pro hooks into target: $packageName")

        // 1. Install Multi-Camera Router (openCamera ID 0/1 detection & auto front-camera mirror)
        com.tajasb.engine.MultiCameraRouter.hookCameraManager(lpparam.classLoader) { camId, isFront ->
            Log.i(TAG, "Target $packageName switched to Camera ID: $camId (Front=$isFront)")
            currentGlRenderer?.setFrontFacing(isFront)
        }

        // 2. Install Auto 3A State Synthesizer (AF/AE/AWB Converged / Locked)
        com.tajasb.engine.Auto3AStateEngine.hookCaptureResult(lpparam.classLoader)

        // 3. Install Sensor Characteristics & Hardware Level Protection (Xiaomi 2310FPCA4G profile)
        com.tajasb.engine.SensorCharacteristicsEngine.hookCharacteristics(lpparam.classLoader) {
            com.tajasb.engine.MultiCameraRouter.currentActiveCameraId
        }

        com.tajasb.hook.Camera1HookHandler.hookCamera1(lpparam.classLoader, packageName)
        com.tajasb.hook.Camera2HookHandler.hookCamera2(lpparam.classLoader, packageName)
    }
}
