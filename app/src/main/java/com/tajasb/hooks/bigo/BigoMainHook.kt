package com.tajasb.hooks.bigo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * BigoMainHook:
 * Entry point Xposed/LSPosed yang 100% terisolasi khusus untuk BIGO Live
 * (`sg.bigo.live` & `sg.bigo.lite`).
 *
 * Prinsip isolasi ketat:
 * 1. Hanya mengeksekusi hook penuh pada proses utama (processName == packageName).
 * 2. Membaca konfigurasi vcam_config_bigo HANYA melalui VcamContentProvider (/bigo/config),
 *    bukan SharedPreferences lokal milik proses BIGO.
 * 3. Menggunakan cache buffer terpisah (virtual_vcam_bigo_feed_a/b/c.mp4) dan
 *    broadcast terpisah (com.tajasb.ACTION_BIGO_CMD) dengan tag Logcat "Tajasb-BIGO".
 */
object BigoMainHook {
    const val TAG = "Tajasb-BIGO"

    private val KNOWN_AUTHORITIES = listOf(
        "com.tajasb.pro.issber.provider",
        "com.tajasb.pro.provider",
        "com.tajasb.provider",
        "com.aistudio.tajasb.provider",
        "com.aistudio.cuancame.provider",
        "com.example.provider"
    )

    @Volatile
    var appContext: Context? = null
        private set

    @Volatile
    var liveConfig: BigoVcamConfig = BigoVcamConfig()
        private set

    @Volatile
    private var currentRenderer: BigoGlVideoRenderer? = null

    @Volatile
    private var activeSurface: Surface? = null

    @Volatile
    private var lastTexture: SurfaceTexture? = null

    @Volatile
    private var watcherStarted = false

    @Volatile
    private var cachedAuthority: String? = null

    @Volatile
    private var lastExtractedVersion: Long = 0L

    @Volatile
    private var lastExtractedPath: String = ""

    @Volatile
    private var providerErrorLogged = false

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val fetchLock = Any()

    fun isBigoPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return BigoConfigStore.BIGO_PACKAGES.contains(packageName)
    }

    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName ?: return
        val processName = lpparam.processName ?: packageName
        Log.d(TAG, "Loaded package=$packageName process=$processName")

        if (!BigoHookRouter.isMainBigoProcess(packageName, processName)) {
            Log.d(TAG, "Melewati sub-proses BIGO ($processName) untuk mencegah dual-watcher & tabrakan cache.")
            return
        }

        fun onContextReady(ctx: Context) {
            appContext = ctx.applicationContext ?: ctx
            BigoBroadcastReceiver.createAndRegister(appContext ?: ctx)
            startWatcherThread(packageName)
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
                Activity::class.java,
                "onCreate",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? Context)?.let { onContextReady(it) }
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onDestroy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val act = param.thisObject as? Activity
                        if (act?.isFinishing == true) {
                            mainHandler.postDelayed({
                                if (!BigoHookRouter.isAnyCameraActive()) {
                                    stopVideoPlayback("Activity.onDestroy", force = true)
                                }
                            }, 350L)
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        BigoAuto3AEngine.hookCaptureResult(lpparam.classLoader)
        BigoSensorCharacteristicsEngine.hookCharacteristics(lpparam.classLoader) {
            BigoHookRouter.currentActiveCameraId
        }
        BigoHookRouter.installHooks(lpparam.classLoader, packageName)

        Log.i(TAG, "Modul VCam BIGO terisolasi siap pada $packageName ($processName)")
    }

    private fun resolveAuthorities(ctx: Context?): List<String> {
        val list = mutableListOf<String>()
        cachedAuthority?.let { list.add(it) }
        if (ctx != null) {
            try {
                val providers = ctx.packageManager.queryContentProviders(null, 0, 0)
                for (p in providers) {
                    val auth = p.authority ?: continue
                    if ((auth.contains("tajasb") || auth.contains("cuancame")) && !list.contains(auth)) {
                        list.add(auth)
                    }
                }
            } catch (_: Throwable) {}
        }
        for (a in KNOWN_AUTHORITIES) {
            if (!list.contains(a)) list.add(a)
        }
        return list
    }

    fun queryConfigFromProvider(ctx: Context? = appContext): BigoVcamConfig? {
        val context = ctx ?: return null
        val authorities = resolveAuthorities(context)
        for (auth in authorities) {
            try {
                val bundle = context.contentResolver.call(
                    Uri.parse("content://$auth/bigo/config"),
                    "bigoGetConfig",
                    null,
                    null
                )
                if (bundle != null && bundle.getBoolean("success", false)) {
                    cachedAuthority = auth
                    providerErrorLogged = false
                    return BigoVcamConfig(
                        enabled = bundle.getBoolean("enabled", true),
                        videoPath = bundle.getString("videoPath") ?: "",
                        audioPath = bundle.getString("audioPath") ?: "",
                        rotation = bundle.getInt("rotation", 0),
                        isMirrored = bundle.getBoolean("isMirrored", false),
                        isVerticalFlipped = bundle.getBoolean("isVerticalFlipped", false),
                        zoomFactor = bundle.getFloat("zoomFactor", 1.0f).coerceIn(0.3f, 5.0f),
                        scaleMode = bundle.getString("scaleMode") ?: "CROP",
                        isPaused = bundle.getBoolean("isPaused", false),
                        isMuted = bundle.getBoolean("isMuted", false),
                        isVideoAudioMuted = bundle.getBoolean("isVideoAudioMuted", false),
                        isMp3AudioPaused = bundle.getBoolean("isMp3AudioPaused", true),
                        fps = bundle.getInt("fps", 30).coerceIn(15, 60),
                        version = bundle.getLong("version", 1L)
                    )
                }
            } catch (_: Throwable) {}

            // Fallback URI stream /bigo/config (tetap via VcamContentProvider, bukan SharedPreferences lokal!)
            try {
                context.contentResolver.openInputStream(Uri.parse("content://$auth/bigo/config"))?.use { input ->
                    val text = input.bufferedReader().readText()
                    if (text.isNotBlank()) {
                        cachedAuthority = auth
                        providerErrorLogged = false
                        return BigoVcamConfig.fromJson(JSONObject(text))
                    }
                }
            } catch (_: Throwable) {}
        }
        if (!providerErrorLogged) {
            providerErrorLogged = true
            Log.w(TAG, "Belum dapat mengakses content://<authority>/bigo/config (pastikan app Tajasb terpasang & authority aktif)")
        }
        return null
    }

    fun getVirtualVideoFile(forceRefresh: Boolean = false): File? = synchronized(fetchLock) {
        val ctx = appContext ?: return null
        val cfg = queryConfigFromProvider(ctx) ?: liveConfig
        liveConfig = cfg
        if (!cfg.enabled) return null

        val activeName = currentRenderer?.videoFile?.name ?: ""
        val nextTag = when {
            !activeName.contains("_a.") -> "a"
            !activeName.contains("_b.") -> "b"
            else -> "c"
        }
        val nextCache = File(ctx.cacheDir, "virtual_vcam_bigo_feed_$nextTag.mp4")
        val curCache = currentRenderer?.videoFile?.takeIf { it.exists() && it.length() > 1024L }
            ?: File(ctx.cacheDir, "virtual_vcam_bigo_feed_a.mp4")

        val isStale = (cfg.version > 0L && cfg.version != lastExtractedVersion) ||
            (cfg.videoPath.isNotBlank() && cfg.videoPath != lastExtractedPath)

        if (!forceRefresh && !isStale && curCache.exists() && curCache.length() > 1024L) {
            return curCache
        }

        val tempFile = File(ctx.cacheDir, "temp_bigo_feed_$nextTag.mp4")
        try { tempFile.delete() } catch (_: Throwable) {}

        for (auth in resolveAuthorities(ctx)) {
            val contentUri = Uri.parse("content://$auth/bigo/virtual.mp4")
            try {
                ctx.contentResolver.openInputStream(contentUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output, bufferSize = 128 * 1024)
                        output.flush()
                    }
                    if (tempFile.exists() && tempFile.length() > 1024L) {
                        try { nextCache.delete() } catch (_: Throwable) {}
                        if (tempFile.renameTo(nextCache)) {
                            cachedAuthority = auth
                            lastExtractedVersion = cfg.version
                            lastExtractedPath = cfg.videoPath
                            Log.i(TAG, "Video BIGO disalin ke ${nextCache.name} (${nextCache.length()} bytes)")
                            return nextCache
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        return if (curCache.exists() && curCache.length() > 1024L) curCache else null
    }

    private fun startWatcherThread(packageName: String) {
        if (watcherStarted) return
        watcherStarted = true
        val thread = Thread {
            while (true) {
                try {
                    Thread.sleep(1500L)
                    val ctx = appContext ?: continue
                    val remoteCfg = queryConfigFromProvider(ctx) ?: continue
                    val prevCfg = liveConfig
                    val videoChanged = (remoteCfg.version != prevCfg.version && remoteCfg.videoPath != prevCfg.videoPath) ||
                        (remoteCfg.version > 0L && remoteCfg.version != lastExtractedVersion)
                    val audioChanged = remoteCfg.audioPath != prevCfg.audioPath

                    if (remoteCfg != prevCfg) {
                        applyLiveConfig(remoteCfg, ctx)
                    }
                    if (videoChanged && currentRenderer?.isRunning == true) {
                        val updatedVid = getVirtualVideoFile(forceRefresh = true)
                        if (updatedVid != null) {
                            currentRenderer?.switchVideoFile(updatedVid)
                        }
                    }
                    if (audioChanged && currentRenderer?.isRunning == true) {
                        val updatedAud = BigoAudioHookHandler.getVirtualAudioFile(
                            appContext = ctx,
                            authorities = resolveAuthorities(ctx),
                            forceRefresh = true,
                            configVersion = remoteCfg.version
                        )
                        currentRenderer?.setAudioFile(updatedAud)
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (t: Throwable) {
                    Log.w(TAG, "BigoWatcherThread warning: ${t.message}")
                }
            }
        }
        thread.isDaemon = true
        thread.name = "BigoWatcherThread-$packageName"
        thread.start()
    }

    fun applyLiveConfig(newConfig: BigoVcamConfig, ctx: Context? = appContext) {
        liveConfig = newConfig
        BigoHookRouter.invalidateNv21Cache()
        if (!newConfig.enabled) {
            stopVideoPlayback("Config disabled", force = true)
            return
        }
        currentRenderer?.applyConfig(newConfig)
    }

    fun reloadVideoFromProviderAsync(ctx: Context, forceRefresh: Boolean) {
        ioExecutor.execute {
            val file = getVirtualVideoFile(forceRefresh = forceRefresh) ?: return@execute
            currentRenderer?.switchVideoFile(file)
        }
    }

    fun reloadAudioFromProviderAsync(ctx: Context, forceRefresh: Boolean) {
        ioExecutor.execute {
            val file = BigoAudioHookHandler.getVirtualAudioFile(
                appContext = ctx,
                authorities = resolveAuthorities(ctx),
                forceRefresh = forceRefresh,
                configVersion = liveConfig.version
            )
            currentRenderer?.setAudioFile(file)
        }
    }

    fun onSurfaceTextureAvailable(texture: SurfaceTexture) {
        lastTexture = texture
    }

    fun onStartPreviewRequested(packageName: String) {
        activeSurface?.takeIf { it.isValid }?.let { playVideoOnSurface(it, packageName) }
            ?: lastTexture?.let { playVideoOnSurface(Surface(it), packageName) }
    }

    fun onFallbackActiveSurfaceRequested(packageName: String) {
        activeSurface?.takeIf { it.isValid }?.let { playVideoOnSurface(it, packageName) }
    }

    fun onFrontFacingChanged(isFront: Boolean) {
        currentRenderer?.setFrontFacing(isFront)
    }

    @Synchronized
    fun playVideoOnSurface(surface: Surface, pkg: String?) {
        if (!surface.isValid) return

        val ctx = appContext
        if (ctx != null) {
            queryConfigFromProvider(ctx)?.let { liveConfig = it }
        }
        if (!liveConfig.enabled) return

        val existing = currentRenderer
        if (existing != null && existing.isRunning) {
            if (existing.targetSurface != surface) {
                activeSurface = surface
                existing.updateTargetSurface(surface)
                existing.setFrontFacing(BigoHookRouter.isFrontFacing)
                existing.applyConfig(liveConfig)
            }
            return
        }

        val videoFile = getVirtualVideoFile(forceRefresh = false)
        if (videoFile == null || !videoFile.exists() || videoFile.length() <= 0L) {
            Log.w(TAG, "Video virtual BIGO belum tersedia di /bigo/virtual.mp4 untuk $pkg")
            return
        }

        val audioFile = BigoAudioHookHandler.getVirtualAudioFile(
            appContext = ctx,
            authorities = resolveAuthorities(ctx),
            forceRefresh = false,
            configVersion = liveConfig.version
        )

        activeSurface = surface
        val renderer = BigoGlVideoRenderer(
            targetSurface = surface,
            videoFile = videoFile,
            audioFile = audioFile,
            initialConfig = liveConfig,
            initialFrontFacing = BigoHookRouter.isFrontFacing
        )
        currentRenderer = renderer
        renderer.start()
        Log.i(TAG, "BigoGlVideoRenderer dimulai untuk $pkg (${videoFile.name})")
    }

    fun scheduleSessionCloseCheck(reason: String, packageName: String) {
        if (BigoHookRouter.isAnyCameraActive()) {
            Log.d(TAG, "$reason diabaikan karena kamera BIGO lain masih aktif (transisi Preview/Live/PK)")
            return
        }
        mainHandler.postDelayed({
            val anyActive = BigoHookRouter.isAnyCameraActive()
            val surf = activeSurface
            if (!anyActive && (surf == null || !surf.isValid)) {
                stopVideoPlayback("$reason (Timeout 1500ms)", force = false)
            }
        }, 1500L)
    }

    @Synchronized
    fun stopVideoPlayback(reason: String, force: Boolean = false) {
        if (!force && BigoHookRouter.isAnyCameraActive()) {
            return
        }
        val surf = activeSurface
        if (!force && surf != null && surf.isValid && currentRenderer?.isRunning == true) {
            return
        }
        try {
            currentRenderer?.release()
        } catch (_: Throwable) {}
        currentRenderer = null
        activeSurface = null
        Log.i(TAG, "Playback BIGO dihentikan (alasan=$reason, force=$force)")
    }
}
