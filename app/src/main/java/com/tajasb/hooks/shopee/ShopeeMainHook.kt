package com.tajasb.hooks.shopee

import android.app.Activity
import android.app.AndroidAppHelper
import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.tajasb.debug.LogLevel
import com.tajasb.debug.VcamDebugLogger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * ShopeeMainHook:
 * Entry point Xposed/LSPosed yang 100% terisolasi khusus untuk Shopee Live & Shopee Video
 * (`com.shopee.*` serta APK kloning paket acak seperti `vhgh.wzia.dppd.apts.ohqa.qbgo`).
 *
 * Prinsip isolasi ketat:
 * 1. Hanya mengeksekusi hook penuh pada proses utama (processName == packageName).
 * 2. Aman terhadap stub/packer cloner (classes.dex kecil): hook kamera framework dipasang di bootclasspath,
 *    sedangkan Context, ShopeeBroadcastReceiver, VcamContentProvider sync, dan watcher terikat saat
 *    attachBaseContext / Application.onCreate setelah wrapper cloner selesai memuat runtime Shopee.
 * 3. Membaca konfigurasi vcam_config_shopee melalui VcamContentProvider (/shopee/config),
 *    dengan cache buffer terpisah (virtual_vcam_shopee_feed_a/b/c.mp4), broadcast terpisah
 *    (com.tajasb.ACTION_SHOPEE_CMD), serta tag Logcat/Floating "Tajasb-Shopee".
 */
object ShopeeMainHook {
    const val TAG = "Tajasb-Shopee"

    private val KNOWN_AUTHORITIES = listOf(
        "com.tajasb.provider",
        "com.tajasb.pro.issber.provider",
        "com.tajasb.pro.provider",
        "com.aistudio.tajasb.provider",
        "com.aistudio.cuancame.provider",
        "com.example.provider"
    )

    @Volatile
    var appContext: Context? = null
        private set

    @Volatile
    var liveConfig: ShopeeVcamConfig = ShopeeVcamConfig()
        private set

    @Volatile
    private var currentRenderer: ShopeeGlVideoRenderer? = null

    @Volatile
    private var activeSurface: Surface? = null

    @Volatile
    private var lastValidSurface: Surface? = null

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

    @Volatile
    private var activePackageName: String = "com.shopee.id"

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val fetchLock = Any()

    private data class EarlyLogItem(val level: LogLevel, val msg: String, val err: Throwable?)
    private val pendingEarlyLogs = java.util.ArrayDeque<EarlyLogItem>(40)

    private fun enqueueOrFlushToFloating(level: LogLevel, msg: String, err: Throwable? = null) {
        val ctx = appContext
        if (ctx != null) {
            try {
                VcamDebugLogger.log(level, TAG, msg, err, ctx)
            } catch (_: Throwable) {}
            return
        }
        synchronized(pendingEarlyLogs) {
            while (pendingEarlyLogs.size >= 35) {
                pendingEarlyLogs.pollFirst()
            }
            pendingEarlyLogs.offerLast(EarlyLogItem(level, msg, err))
        }
    }

    private fun flushEarlyLogs(ctx: Context) {
        val items = synchronized(pendingEarlyLogs) {
            if (pendingEarlyLogs.isEmpty()) return
            val copy = pendingEarlyLogs.toList()
            pendingEarlyLogs.clear()
            copy
        }
        for (item in items) {
            try {
                VcamDebugLogger.log(item.level, TAG, item.msg, item.err, ctx)
            } catch (_: Throwable) {}
        }
    }

    fun logDebug(msg: String, sendToXposed: Boolean = false) {
        try { Log.d(TAG, msg) } catch (_: Throwable) {}
        if (sendToXposed) {
            try { XposedBridge.log("[$TAG] $msg") } catch (_: Throwable) {}
        }
        enqueueOrFlushToFloating(LogLevel.DEBUG, msg, null)
    }

    fun logInfo(msg: String, sendToXposed: Boolean = true) {
        try { Log.i(TAG, msg) } catch (_: Throwable) {}
        if (sendToXposed) {
            try { XposedBridge.log("[$TAG] $msg") } catch (_: Throwable) {}
        }
        enqueueOrFlushToFloating(LogLevel.INFO, msg, null)
    }

    fun logWarn(msg: String, err: Throwable? = null, sendToXposed: Boolean = true) {
        try {
            if (err != null) Log.w(TAG, msg, err) else Log.w(TAG, msg)
        } catch (_: Throwable) {}
        if (sendToXposed) {
            try {
                val suffix = if (err != null) " (${err.javaClass.simpleName}: ${err.message})" else ""
                XposedBridge.log("[$TAG][WARN] $msg$suffix")
            } catch (_: Throwable) {}
        }
        enqueueOrFlushToFloating(LogLevel.WARN, msg, err)
    }

    fun isShopeePackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.lowercase()
        return ShopeeConfigStore.SHOPEE_PACKAGES.contains(lower) || lower.startsWith("com.shopee.")
    }

    fun isShopeeTarget(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        val pkg = lpparam.packageName ?: return false
        if (isShopeePackage(pkg)) return true
        if (isKnownNonShopeePackage(pkg)) return false

        val appCls = try { lpparam.appInfo?.className?.lowercase() ?: "" } catch (_: Throwable) { "" }
        if (appCls.startsWith("com.shopee.") || appCls.contains("shopeeapplication")) {
            return true
        }

        val metaMatch = try {
            val md = lpparam.appInfo?.metaData
            md?.keySet()?.any { k ->
                val lk = k.lowercase()
                val valStr = try { md.get(k)?.toString()?.lowercase() ?: "" } catch (_: Throwable) { "" }
                lk.contains("shopee") || valStr.contains("shopee")
            } ?: false
        } catch (_: Throwable) {
            false
        }
        if (metaMatch) return true

        val cl = lpparam.classLoader ?: return false
        val probeClasses = listOf(
            "com.shopee.app.application.ShopeeApplication",
            "com.shopee.app.ui.home.HomeActivity_",
            "com.shopee.live.ui.LiveActivity",
            "com.shopee.feeds.feedlibrary.activity.CreatePostActivity",
            "com.shopee.sz.camera.CameraActivity",
            "com.garena.android.appkit.tools.BaseApplication",
            "com.shopee.id.BuildConfig",
            "com.shopee.app.BuildConfig"
        )
        for (clsName in probeClasses) {
            try {
                if (XposedHelpers.findClassIfExists(clsName, cl) != null) {
                    return true
                }
            } catch (_: Throwable) {}
        }
        return false
    }

    private fun isKnownNonShopeePackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.startsWith("com.zhiliaoapp.musically") ||
            lower.startsWith("com.ss.android.ugc.") ||
            lower.startsWith("sg.bigo.") ||
            lower == "xbqg.qlvu.ocbz.poro.lunf.ksov" ||
            lower.startsWith("com.yy.") ||
            lower.startsWith("com.tajasb") ||
            lower.startsWith("com.aistudio.") ||
            lower.startsWith("com.instagram.") ||
            lower.startsWith("com.whatsapp") ||
            lower.startsWith("com.tokopedia.") ||
            lower.startsWith("com.lazada.") ||
            lower.startsWith("com.facebook.") ||
            lower.startsWith("org.telegram.") ||
            lower.startsWith("com.android.") ||
            lower.startsWith("com.google.") ||
            lower == "android" ||
            lower == "system"
    }

    private fun resolveAppContext(): Context? {
        appContext?.let { return it }
        return try {
            val app = AndroidAppHelper.currentApplication()
            if (app != null) {
                val ctx = app.applicationContext ?: app
                appContext = ctx
                flushEarlyLogs(ctx)
                ctx
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName ?: return
        val processName = lpparam.processName ?: packageName
        activePackageName = packageName
        val isClone = !packageName.lowercase().startsWith("com.shopee.")
        logInfo("Loaded package=$packageName process=$processName (clone=$isClone)")

        if (!ShopeeHookRouter.isMainShopeeProcess(packageName, processName)) {
            logInfo("Melewati sub-proses Shopee ($processName) untuk mencegah dual-watcher & tabrakan cache.")
            return
        }

        fun onContextReady(ctx: Context, source: String) {
            val resolved = ctx.applicationContext ?: ctx
            val firstTime = (appContext == null)
            appContext = resolved
            if (firstTime) {
                logInfo("Context Shopee siap ($source): pkg=$packageName")
                flushEarlyLogs(resolved)
                ensureConfigSynced(force = true)
            }
            ShopeeBroadcastReceiver.createAndRegister(resolved)
            startWatcherThread(packageName)
        }

        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? Context)?.let { onContextReady(it, "Application.onCreate") }
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
                        (param.args[0] as? Context)?.let { onContextReady(it, "attachBaseContext") }
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
                        (param.thisObject as? Context)?.let { onContextReady(it, "Activity.onCreate") }
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val act = param.thisObject as? Activity ?: return
                        onContextReady(act, "Activity.onResume")
                        ensureConfigSynced()
                        ShopeeHookRouter.onActivityResumed(act.javaClass.name)
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
                            val destroyedName = act.javaClass.name
                            mainHandler.postDelayed({
                                val shouldStop = ShopeeHookRouter.shouldDestroyOnActivityDestroyed(
                                    destroyedActivityName = destroyedName,
                                    currentForegroundActivity = ShopeeHookRouter.currentActivityName
                                )
                                if (shouldStop && !ShopeeHookRouter.isAnyCameraActive()) {
                                    stopVideoPlayback("Activity.onDestroy ($destroyedName)", force = true)
                                }
                            }, 350L)
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        ShopeeStealthGuard.install(lpparam.classLoader)
        ShopeeAuto3AEngine.hookCaptureResult(lpparam.classLoader)
        ShopeeSensorCharacteristicsEngine.hookCharacteristics(lpparam.classLoader) {
            ShopeeHookRouter.currentActiveCameraId
        }
        ShopeeHookRouter.installHooks(lpparam.classLoader, packageName)

        logInfo("Modul VCam Shopee terisolasi siap pada $packageName ($processName)")
    }

    private fun resolveAuthorities(ctx: Context?): List<String> {
        val list = mutableListOf<String>()
        cachedAuthority?.let { list.add(it) }
        if (ctx != null) {
            try {
                ShopeeStealthGuard.runInternal {
                    val providers = ctx.packageManager.queryContentProviders(null, 0, 0)
                    for (p in providers) {
                        val auth = p.authority ?: continue
                        if ((auth.contains("tajasb") || auth.contains("cuancame")) && !list.contains(auth)) {
                            list.add(auth)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        for (a in KNOWN_AUTHORITIES) {
            if (!list.contains(a)) list.add(a)
        }
        return list
    }

    fun queryConfigFromProvider(ctx: Context? = resolveAppContext()): ShopeeVcamConfig? {
        val context = ctx ?: return null
        val authorities = resolveAuthorities(context)
        for (auth in authorities) {
            try {
                val bundle = context.contentResolver.call(
                    Uri.parse("content://$auth/shopee/config"),
                    "shopeeGetConfig",
                    null,
                    null
                )
                if (bundle != null && bundle.getBoolean("success", false)) {
                    val firstConnect = (cachedAuthority == null)
                    cachedAuthority = auth
                    providerErrorLogged = false
                    val cfg = ShopeeVcamConfig(
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
                        bypassScannerAndKyc = bundle.getBoolean("bypassScannerAndKyc", true),
                        onlyInLiveOrVideoActivity = bundle.getBoolean("onlyInLiveOrVideoActivity", false),
                        customClonedPackages = bundle.getString("customClonedPackages") ?: "",
                        version = bundle.getLong("version", 1L)
                    )
                    if (firstConnect) {
                        logInfo("Terhubung ke VcamContentProvider ($auth), videoPath=${cfg.videoPath.ifBlank { "<kosong>" }}")
                    }
                    return cfg
                }
            } catch (_: Throwable) {}

            // Fallback URI stream /shopee/config (tetap via VcamContentProvider, bukan SharedPreferences lokal!)
            try {
                context.contentResolver.openInputStream(Uri.parse("content://$auth/shopee/config"))?.use { input ->
                    val text = input.bufferedReader().readText()
                    if (text.isNotBlank()) {
                        cachedAuthority = auth
                        providerErrorLogged = false
                        return ShopeeVcamConfig.fromJson(JSONObject(text))
                    }
                }
            } catch (_: Throwable) {}
        }
        if (!providerErrorLogged) {
            providerErrorLogged = true
            logWarn("Belum dapat mengakses content://<authority>/shopee/config (pastikan app Tajasb terpasang & authority aktif)")
        }
        return null
    }

    private fun resolveLocalFallbackVideoFile(pkg: String?): File? {
        val targetPkg = pkg?.takeIf { it.isNotBlank() } ?: activePackageName
        val candidates = listOf(
            File("/storage/emulated/0/Android/data/$targetPkg/files/Camera1/virtual.mp4"),
            File("/data/user/0/$targetPkg/files/Camera1/virtual.mp4"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/virtual.mp4"),
            File("/sdcard/DCIM/Camera1/virtual.mp4"),
            File("/storage/emulated/0/DCIM/Camera1/virtual.mp4")
        )
        for (f in candidates) {
            try {
                if (f.exists() && f.canRead() && f.length() > 1024L) {
                    return f
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    @Volatile
    private var lastConfigSyncMs: Long = 0L

    fun ensureConfigSynced(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastConfigSyncMs) < 400L) return
        val ctx = resolveAppContext() ?: return
        lastConfigSyncMs = now
        queryConfigFromProvider(ctx)?.let { liveConfig = it }
    }

    fun hasVirtualVideoAvailable(pkg: String? = activePackageName): Boolean {
        ensureConfigSynced()
        if (!liveConfig.enabled) return false
        val current = currentRenderer?.videoFile
        if (current != null && current.exists() && current.length() > 1024L) return true
        return getVirtualVideoFile(forceRefresh = false, pkg = pkg) != null
    }

    fun getVirtualVideoFile(forceRefresh: Boolean = false, pkg: String? = activePackageName): File? = synchronized(fetchLock) {
        val ctx = resolveAppContext()
        if (ctx == null) {
            return resolveLocalFallbackVideoFile(pkg)
        }
        val cfg = queryConfigFromProvider(ctx) ?: liveConfig
        liveConfig = cfg
        if (!cfg.enabled) return null

        val activeName = currentRenderer?.videoFile?.name ?: ""
        val nextTag = when {
            !activeName.contains("_a.") -> "a"
            !activeName.contains("_b.") -> "b"
            else -> "c"
        }
        val nextCache = File(ctx.cacheDir, "virtual_vcam_shopee_feed_$nextTag.mp4")
        val curCache = currentRenderer?.videoFile?.takeIf { it.exists() && it.length() > 1024L }
            ?: File(ctx.cacheDir, "virtual_vcam_shopee_feed_a.mp4")

        val isStale = (cfg.version > 0L && cfg.version != lastExtractedVersion) ||
            (cfg.videoPath.isNotBlank() && cfg.videoPath != lastExtractedPath)

        if (!forceRefresh && !isStale && curCache.exists() && curCache.length() > 1024L) {
            return curCache
        }

        val tempFile = File(ctx.cacheDir, "temp_shopee_feed_$nextTag.mp4")
        try { tempFile.delete() } catch (_: Throwable) {}

        for (auth in resolveAuthorities(ctx)) {
            val contentUri = Uri.parse("content://$auth/shopee/virtual.mp4")
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
                            logInfo("Video Shopee disalin ke ${nextCache.name} (${nextCache.length()} bytes)")
                            return nextCache
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        if (curCache.exists() && curCache.length() > 1024L) {
            return curCache
        }
        val localFallback = resolveLocalFallbackVideoFile(pkg)
        if (localFallback != null) {
            logInfo("Menggunakan video fallback lokal untuk Shopee: ${localFallback.absolutePath}")
            return localFallback
        }
        return null
    }

    private fun startWatcherThread(packageName: String) {
        if (watcherStarted) return
        watcherStarted = true
        val thread = Thread {
            while (true) {
                try {
                    Thread.sleep(1500L)
                    val ctx = resolveAppContext() ?: continue
                    val remoteCfg = queryConfigFromProvider(ctx) ?: continue
                    val prevCfg = liveConfig
                    val videoChanged = (remoteCfg.version != prevCfg.version && remoteCfg.videoPath != prevCfg.videoPath) ||
                        (remoteCfg.version > 0L && remoteCfg.version != lastExtractedVersion)
                    val audioChanged = remoteCfg.audioPath != prevCfg.audioPath

                    if (remoteCfg != prevCfg) {
                        applyLiveConfig(remoteCfg, ctx)
                    }
                    if (videoChanged && currentRenderer?.isRunning == true) {
                        val updatedVid = getVirtualVideoFile(forceRefresh = true, pkg = packageName)
                        if (updatedVid != null) {
                            currentRenderer?.switchVideoFile(updatedVid)
                        }
                    }
                    if (audioChanged && currentRenderer?.isRunning == true) {
                        val updatedAud = ShopeeAudioHookHandler.getVirtualAudioFile(
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
                    logWarn("ShopeeWatcherThread warning: ${t.message}")
                }
            }
        }
        thread.isDaemon = true
        thread.name = "ShopeeWatcherThread-$packageName"
        thread.start()
    }

    fun applyLiveConfig(newConfig: ShopeeVcamConfig, ctx: Context? = appContext) {
        liveConfig = newConfig
        ShopeeHookRouter.invalidateNv21Cache()
        if (!newConfig.enabled || ShopeeHookRouter.shouldBypassForActivity(ShopeeHookRouter.currentActivityName, newConfig)) {
            stopVideoPlayback("Config disabled atau Activity bypass", force = true)
            return
        }
        currentRenderer?.applyConfig(newConfig)
    }

    fun reloadVideoFromProviderAsync(ctx: Context, forceRefresh: Boolean) {
        ioExecutor.execute {
            val file = getVirtualVideoFile(forceRefresh = forceRefresh, pkg = ctx.packageName) ?: return@execute
            currentRenderer?.switchVideoFile(file)
        }
    }

    fun reloadAudioFromProviderAsync(ctx: Context, forceRefresh: Boolean) {
        ioExecutor.execute {
            val file = ShopeeAudioHookHandler.getVirtualAudioFile(
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
        val candidate = activeSurface?.takeIf { it.isValid }
            ?: lastValidSurface?.takeIf { it.isValid }
            ?: lastTexture?.let { Surface(it) }?.takeIf { it.isValid }
        candidate?.let { playVideoOnSurface(it, packageName) }
    }

    fun onActivityResumedRecovery(activityName: String) {
        val existing = currentRenderer
        if (existing != null) {
            if (existing.isHealthy()) {
                existing.ensurePlayingIfActive()
                if (ShopeeHookRouter.isLiveOrVideoActivity(activityName)) {
                    logDebug("Resume $activityName: renderer aktif & sehat (activeCam=${ShopeeHookRouter.activeCameraCount()})")
                }
                return
            } else {
                stopVideoPlayback("Surface tidak sehat saat resume ($activityName)", force = true)
            }
        }
        if (ShopeeHookRouter.isLiveOrVideoActivity(activityName)) {
            val candidate = activeSurface?.takeIf { it.isValid }
                ?: lastValidSurface?.takeIf { it.isValid }
                ?: lastTexture?.let { Surface(it) }?.takeIf { it.isValid }
            if (candidate != null) {
                logInfo("Memulihkan VCam Shopee saat kembali ke $activityName")
                playVideoOnSurface(candidate, activePackageName)
            } else {
                logInfo("Kembali ke $activityName: menunggu Surface baru dari kamera Shopee (activeCam=${ShopeeHookRouter.activeCameraCount()})")
            }
        }
    }

    fun onFrontFacingChanged(isFront: Boolean) {
        currentRenderer?.setFrontFacing(isFront)
    }

    @Synchronized
    fun playVideoOnSurface(surface: Surface, pkg: String?) {
        if (!surface.isValid) return

        val ctx = resolveAppContext()
        if (ctx != null) {
            ensureConfigSynced()
        }
        if (!liveConfig.enabled) {
            logWarn("playVideoOnSurface dilewati karena ShopeeVcamConfig.enabled=false")
            return
        }
        if (ShopeeHookRouter.shouldBypassForActivity(ShopeeHookRouter.currentActivityName, liveConfig)) {
            return
        }

        lastValidSurface = surface

        val existing = currentRenderer
        if (existing != null && existing.isHealthy()) {
            if (existing.targetSurface != surface) {
                activeSurface = surface
                existing.updateTargetSurface(surface)
                existing.setFrontFacing(ShopeeHookRouter.isFrontFacing)
                existing.applyConfig(liveConfig)
            }
            return
        } else if (existing != null) {
            try { existing.release() } catch (_: Throwable) {}
            currentRenderer = null
        }

        val videoFile = getVirtualVideoFile(forceRefresh = false, pkg = pkg)
        if (videoFile == null || !videoFile.exists() || videoFile.length() <= 0L) {
            logWarn("Video virtual Shopee belum tersedia di /shopee/virtual.mp4 ataupun DCIM/Camera1 untuk $pkg")
            return
        }

        val audioFile = ShopeeAudioHookHandler.getVirtualAudioFile(
            appContext = ctx,
            authorities = resolveAuthorities(ctx),
            forceRefresh = false,
            configVersion = liveConfig.version
        )

        activeSurface = surface
        val renderer = ShopeeGlVideoRenderer(
            targetSurface = surface,
            videoFile = videoFile,
            audioFile = audioFile,
            initialConfig = liveConfig,
            initialFrontFacing = ShopeeHookRouter.isFrontFacing
        )
        renderer.onSurfaceInvalidated = { deadSurface ->
            synchronized(this@ShopeeMainHook) {
                if (currentRenderer === renderer) {
                    currentRenderer = null
                }
                if (activeSurface === deadSurface) {
                    activeSurface = null
                }
                if (lastValidSurface === deadSurface) {
                    lastValidSurface = null
                }
            }
        }
        currentRenderer = renderer
        renderer.start(waitForEglMs = 250L)
        logInfo("ShopeeGlVideoRenderer dimulai untuk $pkg (${videoFile.name}, ${videoFile.length()} bytes)")
    }

    fun scheduleSessionCloseCheck(reason: String, packageName: String) {
        if (ShopeeHookRouter.isAnyCameraActive()) {
            logDebug("$reason diabaikan karena kamera Shopee lain masih aktif (transisi Preview/Live)")
            return
        }
        mainHandler.postDelayed({
            val anyActive = ShopeeHookRouter.isAnyCameraActive()
            val surf = activeSurface
            if (!anyActive && (surf == null || !surf.isValid)) {
                stopVideoPlayback("$reason (Timeout 1500ms)", force = false)
            }
        }, 1500L)
    }

    @Synchronized
    fun stopVideoPlayback(reason: String, force: Boolean = false) {
        if (!force && ShopeeHookRouter.isAnyCameraActive()) {
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
        if (!ShopeeHookRouter.isAnyCameraActive()) {
            ShopeeHookRouter.releaseAllDummyCamera2Surfaces()
        }
        logInfo("Playback Shopee dihentikan (alasan=$reason, force=$force)")
    }
}
