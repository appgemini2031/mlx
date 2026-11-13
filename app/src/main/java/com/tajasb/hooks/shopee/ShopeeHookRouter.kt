@file:Suppress("DEPRECATION")

package com.tajasb.hooks.shopee

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.SurfaceHolder
import com.tajasb.hook.YuvFrameConverter
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ShopeeHookRouter:
 * Router & pencegat pipeline kamera terisolasi khusus Shopee (Shopee Live & Shopee Video,
 * termasuk kloning paket acak seperti vhgh.wzia.dppd.apts.ohqa.qbgo).
 *
 * Fitur utama untuk audit mudah:
 * - Pelacakan Activity (`ActiveActivity=...`) + bypass otomatis Scanner/QR/KYC (`bypassScannerAndKyc`)
 *   serta filter opsional khusus Live/Video (`onlyInLiveOrVideoActivity`).
 * - Audit Surface (`CaptureRequest.Builder.addTarget`, `OutputConfiguration`, `SessionConfiguration`,
 *   `createCaptureSession`, `Camera1.setPreviewTexture`, `ImageReader.newInstance`).
 * - Isolasi dummy surface Camera1 & Camera2 agar HAL kamera asli tidak memicu error BufferQueue.
 * - Dukungan onPreviewFrame NV21 dan ImageReader YUV_420_888 termasuk SurfacePlane read-only via `getBuffer()`.
 */
object ShopeeHookRouter {
    @Volatile
    var currentActiveCameraId: String = "0"
        private set

    @Volatile
    var isFrontFacing: Boolean = false
        private set

    @Volatile
    var currentActivityName: String? = null
        private set

    private val activeCameraTokens = ConcurrentHashMap.newKeySet<Any>()
    private val loggedSurfaceTypes = ConcurrentHashMap.newKeySet<String>()
    private val loggedActivities = ConcurrentHashMap.newKeySet<String>()
    private val loggedBypassActivities = ConcurrentHashMap.newKeySet<String>()
    private val hookedCallbackClasses = ConcurrentHashMap.newKeySet<String>()
    private val hookedStateCallbackClasses = ConcurrentHashMap.newKeySet<String>()
    private val hookedPlaneClasses = ConcurrentHashMap.newKeySet<String>()

    private val swappedCamera2Surfaces = ConcurrentHashMap<Surface, Surface>()
    private val dummyToRealSurface = ConcurrentHashMap<Surface, Surface>()
    private val dummyCamera2SurfaceSet: MutableSet<Surface> = Collections.newSetFromMap(ConcurrentHashMap())
    private val dummyCamera2Textures = ConcurrentHashMap<Surface, SurfaceTexture>()
    private val dummyCamera2ImageReaders = ConcurrentHashMap<Surface, ImageReader>()
    private val dummyImageReaderSet: MutableSet<ImageReader> = Collections.newSetFromMap(ConcurrentHashMap())
    private val isCreatingDummyOutputConfig = ThreadLocal<Boolean>()
    private val isCreatingDummyImageReader = ThreadLocal<Boolean>()

    private val halDrainThread by lazy {
        HandlerThread("TajasbShopeeHalDrain").apply { start() }
    }
    private val halDrainHandler by lazy {
        Handler(halDrainThread.looper)
    }

    private val virtualPlaneBuffers: MutableMap<Any, ByteBuffer> =
        Collections.synchronizedMap(WeakHashMap())

    @Volatile
    private var dummyCamera1Texture: SurfaceTexture? = null

    @Volatile
    private var cachedNv21Data: ByteArray? = null
    @Volatile
    private var cachedNv21Width = 0
    @Volatile
    private var cachedNv21Height = 0
    @Volatile
    private var cachedNv21Rot = -1
    @Volatile
    private var cachedNv21Mirror = false
    @Volatile
    private var lastNv21RefreshMs = 0L
    @Volatile
    private var nv21StartClockMs = 0L
    private val nv21Refreshing = AtomicBoolean(false)
    private val nv21Executor = Executors.newSingleThreadExecutor()

    private val SCANNER_KYC_KEYWORDS = listOf(
        "scanner",
        "scan",
        "qrcode",
        "qr_",
        ".qr",
        "barcode",
        "kyc",
        "ekyc",
        "faceverify",
        "liveness",
        "identity",
        "ocr",
        "visualsearch",
        "visual_search",
        "camerasearch",
        "photosearch",
        "shopeepay"
    )

    private val LIVE_VIDEO_KEYWORDS = listOf(
        "live",
        "streamer",
        "broadcast",
        "svs",
        "feed",
        "video",
        "shortvideo",
        "studio",
        "anchor",
        "rtc",
        "creator",
        "sszmedia",
        "mediasdk",
        "mediaactivity"
    )

    fun isMainShopeeProcess(packageName: String?, processName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (processName.isNullOrBlank()) return true
        return processName == packageName
    }

    fun isScannerOrKycActivity(activityName: String?): Boolean {
        if (activityName.isNullOrBlank()) return false
        val lower = activityName.lowercase()
        return SCANNER_KYC_KEYWORDS.any { lower.contains(it) }
    }

    fun isLiveOrVideoActivity(activityName: String?): Boolean {
        if (activityName.isNullOrBlank()) return false
        val lower = activityName.lowercase()
        if (isScannerOrKycActivity(lower)) return false
        return LIVE_VIDEO_KEYWORDS.any { lower.contains(it) }
    }

    fun shouldBypassForActivity(activityName: String?, config: ShopeeVcamConfig): Boolean {
        if (config.bypassScannerAndKyc && isScannerOrKycActivity(activityName)) {
            return true
        }
        if (config.onlyInLiveOrVideoActivity && !isLiveOrVideoActivity(activityName)) {
            return true
        }
        return false
    }

    fun onActivityResumed(activityName: String?) {
        if (activityName.isNullOrBlank()) return
        val changed = (currentActivityName != activityName)
        currentActivityName = activityName
        if (changed) {
            ShopeeMainHook.logInfo("ActiveActivity=$activityName")
        }
        val cfg = ShopeeMainHook.liveConfig
        if (shouldBypassForActivity(activityName, cfg)) {
            if (loggedBypassActivities.size < 32 && loggedBypassActivities.add(activityName)) {
                ShopeeMainHook.logInfo("Bypass VCam aktif untuk ActiveActivity=$activityName (kamera asli dipertahankan)")
            }
            ShopeeMainHook.stopVideoPlayback("Activity bypass ($activityName)", force = true)
            return
        }
        ShopeeMainHook.onActivityResumedRecovery(activityName)
    }

    private fun isCurrentlyBypassed(): Boolean {
        val act = currentActivityName
        val cfg = ShopeeMainHook.liveConfig
        val bypassed = shouldBypassForActivity(act, cfg)
        if (bypassed && !act.isNullOrBlank() && loggedBypassActivities.add("hook:$act")) {
            ShopeeMainHook.logInfo("VCam Shopee dilewati pada activity=$act (Scanner/KYC atau bukan Live/Video)")
        }
        return bypassed
    }

    fun computeExpectedNv21Size(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 0
        return (width * height * 3) / 2
    }

    fun isNv21BufferValid(buffer: ByteArray?, width: Int, height: Int): Boolean {
        if (buffer == null) return false
        val expected = computeExpectedNv21Size(width, height)
        return expected > 0 && buffer.size >= expected
    }

    fun invalidateNv21Cache() {
        cachedNv21Rot = -1
    }

    fun registerCameraOpened(token: Any?) {
        if (token != null) {
            activeCameraTokens.add(token)
        }
    }

    fun registerCameraClosed(token: Any?) {
        if (token != null) {
            activeCameraTokens.remove(token)
        } else {
            activeCameraTokens.clear()
        }
    }

    fun isAnyCameraActive(): Boolean = activeCameraTokens.isNotEmpty()

    fun activeCameraCount(): Int = activeCameraTokens.size

    fun shouldDestroyOnActivityDestroyed(destroyedActivityName: String?, currentForegroundActivity: String?): Boolean {
        if (destroyedActivityName.isNullOrBlank()) return false
        if (currentForegroundActivity.isNullOrBlank()) return true
        return destroyedActivityName == currentForegroundActivity
    }

    fun isDummySurface(surface: Surface?): Boolean {
        if (surface == null) return false
        return dummyCamera2SurfaceSet.contains(surface)
    }

    fun resolveRealSurface(surface: Surface?): Surface? {
        if (surface == null) return null
        return dummyToRealSurface[surface]
    }

    private fun getOrCreateDummyCamera2Surface(realSurface: Surface): Surface {
        swappedCamera2Surfaces[realSurface]?.let { existing ->
            if (existing.isValid) return existing
        }

        // Gunakan ImageReader PRIV + listener drain agar HAL Camera2 tidak pernah stall (BufferQueue penuh)
        // sehingga CaptureCallback.onCaptureCompleted tetap berjalan mulus 30fps.
        val dummyFromReader = try {
            isCreatingDummyImageReader.set(true)
            val reader = ImageReader.newInstance(640, 480, ImageFormat.PRIVATE, 3)
            reader.setOnImageAvailableListener({ r ->
                try {
                    r.acquireLatestImage()?.close()
                } catch (_: Throwable) {}
            }, halDrainHandler)
            val surf = reader.surface
            if (surf != null && surf.isValid) {
                dummyImageReaderSet.add(reader)
                dummyCamera2ImageReaders[surf] = reader
                surf
            } else {
                try { reader.close() } catch (_: Throwable) {}
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            isCreatingDummyImageReader.set(false)
        }

        if (dummyFromReader != null) {
            swappedCamera2Surfaces[realSurface] = dummyFromReader
            dummyToRealSurface[dummyFromReader] = realSurface
            dummyCamera2SurfaceSet.add(dummyFromReader)
            return dummyFromReader
        }

        val st = SurfaceTexture(0).apply {
            setDefaultBufferSize(1080, 1920)
            setOnFrameAvailableListener({ tex ->
                try { tex.releaseTexImage() } catch (_: Throwable) {}
            }, halDrainHandler)
        }
        val dummy = Surface(st)
        swappedCamera2Surfaces[realSurface] = dummy
        dummyToRealSurface[dummy] = realSurface
        dummyCamera2SurfaceSet.add(dummy)
        dummyCamera2Textures[dummy] = st
        return dummy
    }

    fun releaseAllDummyCamera2Surfaces() {
        val dummies = dummyCamera2SurfaceSet.toList()
        swappedCamera2Surfaces.clear()
        dummyToRealSurface.clear()
        dummyCamera2SurfaceSet.clear()
        for (d in dummies) {
            dummyCamera2ImageReaders.remove(d)?.let { reader ->
                dummyImageReaderSet.remove(reader)
                try { reader.close() } catch (_: Throwable) {}
            }
            try { d.release() } catch (_: Throwable) {}
            try { dummyCamera2Textures.remove(d)?.release() } catch (_: Throwable) {}
        }
    }

    private fun isImageReaderSurface(surface: Surface): Boolean {
        val desc = try { surface.toString() } catch (_: Throwable) { "" }
        return desc.contains("ImageReader", ignoreCase = true)
    }

    private fun auditSurface(surface: Surface, sourceMethod: String) {
        if (isDummySurface(surface)) return
        val desc = try { surface.toString() } catch (_: Throwable) { "Surface@unknown" }
        val kind = when {
            desc.contains("ImageReader", ignoreCase = true) -> "ImageReader(YUV/BeautySDK)"
            desc.contains("SurfaceTexture", ignoreCase = true) -> "SurfaceTexture"
            else -> "NativeSurface"
        }
        val key = "$sourceMethod:$kind:${System.identityHashCode(surface)}"
        if (loggedSurfaceTypes.size < 64 && loggedSurfaceTypes.add(key)) {
            ShopeeMainHook.logInfo("Audit Surface [$sourceMethod] -> $kind ($desc)")
        }
    }

    private fun pickPreferredSurface(surfaces: Collection<*>): Surface? {
        var fallbackImageReader: Surface? = null
        for (item in surfaces) {
            if (item is Surface && item.isValid && !isDummySurface(item)) {
                if (!isImageReaderSurface(item)) {
                    return item
                }
                if (fallbackImageReader == null) {
                    fallbackImageReader = item
                }
            }
        }
        return fallbackImageReader
    }

    fun sanitizeCameraId(rawId: String?): String {
        val trimmed = rawId?.trim()
        return if (trimmed.isNullOrEmpty()) "0" else trimmed
    }

    private fun notifyCameraSwitched(cameraId: String?) {
        val normalized = sanitizeCameraId(cameraId)
        currentActiveCameraId = normalized
        isFrontFacing = (normalized == "1")
        ShopeeMainHook.logInfo("Kamera Shopee aktif: ID=$normalized (Front=$isFrontFacing, Activity=${currentActivityName ?: "-"})")
        ShopeeMainHook.onFrontFacingChanged(isFrontFacing)
    }

    fun installHooks(classLoader: ClassLoader, packageName: String) {
        hookCameraManager()
        hookCamera1(classLoader, packageName)
        hookCamera2(classLoader, packageName)
        hookImageReader(classLoader, packageName)
    }

    private fun hookStateCallback(callback: Any?) {
        if (callback == null) return
        val cls = callback.javaClass
        if (!hookedStateCallbackClasses.add(cls.name)) return

        try {
            XposedBridge.hookAllMethods(cls, "onOpened", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val cam = param.args?.firstOrNull()
                    if (cam != null) {
                        registerCameraOpened(cam)
                    }
                }
            })
        } catch (_: Throwable) {}

        listOf("onClosed", "onDisconnected", "onError").forEach { methodName ->
            try {
                XposedBridge.hookAllMethods(cls, methodName, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val cam = param.args?.firstOrNull()
                        if (cam != null) {
                            registerCameraClosed(cam)
                        }
                        if (methodName != "onClosed") {
                            ShopeeMainHook.logWarn("CameraDevice.StateCallback.$methodName dipanggil di Shopee (activeCam=${activeCameraTokens.size})")
                        }
                        ShopeeMainHook.scheduleSessionCloseCheck("StateCallback.$methodName", ShopeeMainHook.appContext?.packageName ?: "com.shopee.id")
                    }
                })
            } catch (_: Throwable) {}
        }
    }

    private fun hookCameraManager() {
        try {
            XposedHelpers.findAndHookMethod(
                CameraManager::class.java,
                "openCamera",
                String::class.java,
                CameraDevice.StateCallback::class.java,
                Handler::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val camId = param.args[0] as? String
                        hookStateCallback(param.args[1])
                        notifyCameraSwitched(camId)
                    }
                }
            )
        } catch (t: Throwable) {
            ShopeeMainHook.logWarn("CameraManager.openCamera(Handler) hook skipped: ${t.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                XposedHelpers.findAndHookMethod(
                    CameraManager::class.java,
                    "openCamera",
                    String::class.java,
                    Executor::class.java,
                    CameraDevice.StateCallback::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val camId = param.args[0] as? String
                            hookStateCallback(param.args[2])
                            notifyCameraSwitched(camId)
                        }
                    }
                )
            } catch (t: Throwable) {
                ShopeeMainHook.logWarn("CameraManager.openCamera(Executor) hook skipped: ${t.message}")
            }
        }

        try {
            XposedHelpers.findAndHookMethod(
                Camera::class.java,
                "open",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val camId = (param.args[0] as? Int)?.toString() ?: "0"
                        notifyCameraSwitched(camId)
                    }
                }
            )
        } catch (t: Throwable) {
            ShopeeMainHook.logWarn("Camera.open(int) hook skipped: ${t.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                Camera::class.java,
                "open",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        notifyCameraSwitched("0")
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun hookCamera1(classLoader: ClassLoader, packageName: String) {
        try {
            val cameraClass = XposedHelpers.findClass("android.hardware.Camera", classLoader)

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "setPreviewTexture",
                SurfaceTexture::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val texture = param.args[0] as? SurfaceTexture ?: return
                        if (isCurrentlyBypassed()) return
                        ShopeeMainHook.onSurfaceTextureAvailable(texture)
                        val surface = Surface(texture)
                        auditSurface(surface, "Camera1.setPreviewTexture")
                        ShopeeMainHook.playVideoOnSurface(surface, packageName)

                        if (ShopeeMainHook.hasVirtualVideoAvailable(packageName)) {
                            try {
                                dummyCamera1Texture?.release()
                            } catch (_: Throwable) {}
                            val dummy = SurfaceTexture(0)
                            dummyCamera1Texture = dummy
                            param.args[0] = dummy
                            if (loggedSurfaceTypes.add("camera1_dummy_swap")) {
                                ShopeeMainHook.logInfo("Camera1 setPreviewTexture dialihkan ke dummy texture agar VCam Shopee eksklusif")
                            }
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "setPreviewDisplay",
                SurfaceHolder::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isCurrentlyBypassed()) return
                        val holder = param.args[0] as? SurfaceHolder ?: return
                        holder.surface?.let { surface ->
                            if (surface.isValid) {
                                auditSurface(surface, "Camera1.setPreviewDisplay")
                                ShopeeMainHook.playVideoOnSurface(surface, packageName)
                            }
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "startPreview",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        registerCameraOpened(param.thisObject)
                        if (isCurrentlyBypassed()) return
                        if (loggedSurfaceTypes.add("camera1_startPreview")) {
                            ShopeeMainHook.logInfo("Camera1.startPreview dipanggil di $packageName")
                        }
                        ShopeeMainHook.onStartPreviewRequested(packageName)
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "stopPreview",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        ShopeeMainHook.scheduleSessionCloseCheck("Camera1.stopPreview", packageName)
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "release",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        registerCameraClosed(param.thisObject)
                        if (!isAnyCameraActive()) {
                            try {
                                dummyCamera1Texture?.release()
                            } catch (_: Throwable) {}
                            dummyCamera1Texture = null
                        }
                        ShopeeMainHook.scheduleSessionCloseCheck("Camera1.release", packageName)
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "takePicture",
                Camera.ShutterCallback::class.java,
                Camera.PictureCallback::class.java,
                Camera.PictureCallback::class.java,
                Camera.PictureCallback::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isCurrentlyBypassed()) return
                        val videoFile = ShopeeMainHook.getVirtualVideoFile(pkg = packageName) ?: return
                        var bitmap = YuvFrameConverter.extractVideoFrameBitmap(videoFile) ?: return
                        try {
                            val cfg = ShopeeMainHook.liveConfig
                            val rot = (cfg.rotation + 270) % 360
                            val mirror = if (isFrontFacing) !cfg.isMirrored else cfg.isMirrored
                            bitmap = YuvFrameConverter.transformBitmap(bitmap, rot, mirror)
                            val bos = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                            val jpegBytes = bos.toByteArray()
                            val shutterCallback = param.args[0] as? Camera.ShutterCallback
                            val jpegCallback = (param.args[3] as? Camera.PictureCallback)
                                ?: (param.args[1] as? Camera.PictureCallback)
                            shutterCallback?.onShutter()
                            jpegCallback?.onPictureTaken(jpegBytes, param.thisObject as? Camera)
                            param.result = null
                        } catch (t: Throwable) {
                            ShopeeMainHook.logWarn("Camera1 takePicture Shopee error: ${t.message}")
                        }
                    }
                }
            )

            listOf("setPreviewCallback", "setOneShotPreviewCallback", "setPreviewCallbackWithBuffer").forEach { methodName ->
                try {
                    XposedHelpers.findAndHookMethod(
                        cameraClass,
                        methodName,
                        Camera.PreviewCallback::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (loggedSurfaceTypes.add("cb:$methodName")) {
                                    ShopeeMainHook.logInfo("Audit Camera1: $methodName dipanggil di $packageName")
                                }
                                hookPreviewCallback(param, packageName)
                            }
                        }
                    )
                } catch (_: Throwable) {}
            }

            ShopeeMainHook.logInfo("Camera1 hook Shopee terpasang di $packageName")
        } catch (t: Throwable) {
            ShopeeMainHook.logWarn("Camera1 hook Shopee dilewati: ${t.message}")
        }
    }

    private fun extractNv21Frame(width: Int, height: Int, rot: Int, mirror: Boolean, packageName: String, timeUs: Long = 0L): ByteArray? {
        val videoFile = ShopeeMainHook.getVirtualVideoFile(pkg = packageName) ?: return null
        val retriever = MediaMetadataRetriever()
        val rawBmp = try {
            retriever.setDataSource(videoFile.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durUs = (durStr?.toLongOrNull() ?: 0L) * 1000L
            val targetUs = if (durUs > 0L) timeUs % durUs else 0L
            retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        } ?: return null

        val transformed = YuvFrameConverter.transformBitmap(rawBmp, rot, mirror)
        val nv21 = YuvFrameConverter.bitmapToNv21(transformed, width, height)
        if (transformed != rawBmp && !transformed.isRecycled) transformed.recycle()
        if (!rawBmp.isRecycled) rawBmp.recycle()
        return nv21
    }

    private fun scheduleAsyncNv21Refresh(width: Int, height: Int, rot: Int, mirror: Boolean, packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastNv21RefreshMs < 100L) return
        if (!nv21Refreshing.compareAndSet(false, true)) return
        lastNv21RefreshMs = now
        if (nv21StartClockMs == 0L) {
            nv21StartClockMs = now
        }
        val elapsedUs = (now - nv21StartClockMs).coerceAtLeast(0L) * 1000L
        nv21Executor.execute {
            try {
                val fresh = extractNv21Frame(width, height, rot, mirror, packageName, elapsedUs)
                if (fresh != null) {
                    cachedNv21Data = fresh
                    cachedNv21Width = width
                    cachedNv21Height = height
                    cachedNv21Rot = rot
                    cachedNv21Mirror = mirror
                }
            } catch (_: Throwable) {
            } finally {
                nv21Refreshing.set(false)
            }
        }
    }

    private fun hookPreviewCallback(param: XC_MethodHook.MethodHookParam, packageName: String) {
        val originalCallback = param.args[0] ?: return
        val cbClass = originalCallback.javaClass
        if (!hookedCallbackClasses.add(cbClass.name)) return

        try {
            XposedHelpers.findAndHookMethod(
                cbClass,
                "onPreviewFrame",
                ByteArray::class.java,
                Camera::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(cbParam: MethodHookParam) {
                        try {
                            if (isCurrentlyBypassed()) return
                            val data = cbParam.args[0] as? ByteArray ?: return
                            val camera = cbParam.args[1] as? Camera ?: return
                            val params = camera.parameters ?: return
                            val size = params.previewSize ?: return
                            if (!isNv21BufferValid(data, size.width, size.height)) {
                                val mismatchKey = "nv21_mismatch:${size.width}x${size.height}:${data.size}"
                                if (loggedSurfaceTypes.add(mismatchKey)) {
                                    ShopeeMainHook.logWarn("NV21 buffer mismatch: data=${data.size} bytes, expected>=${computeExpectedNv21Size(size.width, size.height)} (${size.width}x${size.height})")
                                }
                                return
                            }
                            val cfg = ShopeeMainHook.liveConfig
                            if (!cfg.enabled) return

                            val nv21ActiveKey = "nv21_active:${size.width}x${size.height}"
                            if (loggedSurfaceTypes.add(nv21ActiveKey)) {
                                ShopeeMainHook.logInfo("Camera1 NV21 aktif pada ${size.width}x${size.height} (buffer=${data.size} bytes)")
                            }

                            val rot = (cfg.rotation + 270) % 360
                            val mirror = if (isFrontFacing) !cfg.isMirrored else cfg.isMirrored

                            if (cachedNv21Data == null ||
                                cachedNv21Width != size.width ||
                                cachedNv21Height != size.height ||
                                cachedNv21Rot != rot ||
                                cachedNv21Mirror != mirror
                            ) {
                                cachedNv21Data = extractNv21Frame(size.width, size.height, rot, mirror, packageName, 0L) ?: return
                                cachedNv21Width = size.width
                                cachedNv21Height = size.height
                                cachedNv21Rot = rot
                                cachedNv21Mirror = mirror
                            } else if (!cfg.isPaused) {
                                scheduleAsyncNv21Refresh(size.width, size.height, rot, mirror, packageName)
                            }

                            val expectedBytes = computeExpectedNv21Size(size.width, size.height)
                            val fakeBytes = cachedNv21Data ?: return
                            if (fakeBytes.size >= expectedBytes && data.size >= expectedBytes) {
                                System.arraycopy(fakeBytes, 0, data, 0, expectedBytes)
                            }
                        } catch (t: Throwable) {
                            ShopeeMainHook.logWarn("NV21 onPreviewFrame guard warning: ${t.message}")
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun hookImageReader(classLoader: ClassLoader, packageName: String) {
        try {
            val irClass = XposedHelpers.findClass("android.media.ImageReader", classLoader)
            XposedHelpers.findAndHookMethod(
                irClass,
                "newInstance",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isCreatingDummyImageReader.get() == true) return
                        val w = param.args[0] as? Int ?: 0
                        val h = param.args[1] as? Int ?: 0
                        val fmt = param.args[2] as? Int ?: 0
                        val fmtName = when (fmt) {
                            ImageFormat.YUV_420_888 -> "YUV_420_888"
                            ImageFormat.PRIVATE -> "PRIVATE"
                            ImageFormat.JPEG -> "JPEG"
                            ImageFormat.NV21 -> "NV21"
                            else -> "0x${Integer.toHexString(fmt)}"
                        }
                        val key = "ir_new:${w}x${h}:$fmtName"
                        if (loggedSurfaceTypes.add(key)) {
                            ShopeeMainHook.logInfo("Audit ImageReader.newInstance(${w}x${h}, format=$fmtName) di $packageName")
                        }
                    }
                }
            )

            listOf("acquireLatestImage", "acquireNextImage").forEach { methodName ->
                try {
                    XposedHelpers.findAndHookMethod(
                        irClass,
                        methodName,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val readerObj = param.thisObject as? ImageReader
                                if (readerObj != null && dummyImageReaderSet.contains(readerObj)) return
                                val image = param.result as? Image ?: return
                                if (image.format != ImageFormat.YUV_420_888) return
                                ShopeeMainHook.ensureConfigSynced()
                                val cfg = ShopeeMainHook.liveConfig
                                if (!cfg.enabled || isCurrentlyBypassed()) return

                                val key = "ir_acq:${methodName}:${image.width}x${image.height}"
                                if (loggedSurfaceTypes.add(key)) {
                                    ShopeeMainHook.logInfo("ImageReader.$methodName aktif (${image.width}x${image.height}, planes=${image.planes.size})")
                                }

                                val planes = image.planes
                                if (planes.size < 3) return
                                val yBuf = planes[0].buffer
                                val uBuf = planes[1].buffer
                                val vBuf = planes[2].buffer

                                val rot = (cfg.rotation + 270) % 360
                                val mirror = if (isFrontFacing) !cfg.isMirrored else cfg.isMirrored
                                val w = image.width
                                val h = image.height

                                if (cachedNv21Data == null ||
                                    cachedNv21Width != w ||
                                    cachedNv21Height != h ||
                                    cachedNv21Rot != rot ||
                                    cachedNv21Mirror != mirror
                                ) {
                                    cachedNv21Data = extractNv21Frame(w, h, rot, mirror, packageName, 0L) ?: return
                                    cachedNv21Width = w
                                    cachedNv21Height = h
                                    cachedNv21Rot = rot
                                    cachedNv21Mirror = mirror
                                } else if (!cfg.isPaused) {
                                    scheduleAsyncNv21Refresh(w, h, rot, mirror, packageName)
                                }

                                val nv21 = cachedNv21Data ?: return
                                if (!isNv21BufferValid(nv21, w, h)) return

                                if (!yBuf.isReadOnly && !uBuf.isReadOnly && !vBuf.isReadOnly) {
                                    writeNv21IntoYuv420Buffers(nv21, w, h, planes, yBuf, uBuf, vBuf)
                                } else {
                                    ensurePlaneGetBufferHooked(planes[0])
                                    val yOut = allocateCloneDirectBuffer(yBuf)
                                    val uOut = allocateCloneDirectBuffer(uBuf)
                                    val vOut = allocateCloneDirectBuffer(vBuf)
                                    writeNv21IntoYuv420Buffers(nv21, w, h, planes, yOut, uOut, vOut)
                                    virtualPlaneBuffers[planes[0]] = yOut
                                    virtualPlaneBuffers[planes[1]] = uOut
                                    virtualPlaneBuffers[planes[2]] = vOut
                                    if (loggedSurfaceTypes.add("ir_virtual_plane:${w}x${h}")) {
                                        ShopeeMainHook.logInfo("ImageReader YUV_420_888 virtual plane hook aktif pada ${w}x${h}")
                                    }
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            ShopeeMainHook.logWarn("ImageReader hook Shopee dilewati: ${t.message}")
        }
    }

    private fun allocateCloneDirectBuffer(orig: ByteBuffer): ByteBuffer {
        val cap = orig.capacity().coerceAtLeast(orig.limit()).coerceAtLeast(1)
        val out = ByteBuffer.allocateDirect(cap)
        out.order(orig.order())
        if (orig.limit() in 1..cap) {
            out.limit(orig.limit())
        }
        return out
    }

    private fun ensurePlaneGetBufferHooked(samplePlane: Image.Plane) {
        val cls = samplePlane.javaClass
        if (!hookedPlaneClasses.add(cls.name)) return
        try {
            XposedHelpers.findAndHookMethod(
                cls,
                "getBuffer",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val replacement = virtualPlaneBuffers[param.thisObject] ?: return
                        param.result = replacement.duplicate().apply { position(0) }
                    }
                }
            )
            ShopeeMainHook.logInfo("SurfacePlane.getBuffer() hook terpasang pada ${cls.name}")
        } catch (t: Throwable) {
            ShopeeMainHook.logWarn("SurfacePlane.getBuffer() hook gagal: ${t.message}")
        }
    }

    private fun writeNv21IntoYuv420Buffers(
        nv21: ByteArray,
        width: Int,
        height: Int,
        planes: Array<Image.Plane>,
        yBuf: ByteBuffer,
        uBuf: ByteBuffer,
        vBuf: ByteBuffer
    ) {
        try {
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            if (yPixelStride == 1 && yRowStride == width && yBuf.limit() >= width * height) {
                yBuf.duplicate().apply {
                    position(0)
                    put(nv21, 0, width * height)
                }
            } else {
                val yDup = yBuf.duplicate()
                for (row in 0 until height) {
                    val dstOffset = row * yRowStride
                    val srcOffset = row * width
                    if (yPixelStride == 1 && dstOffset + width <= yDup.limit()) {
                        yDup.position(dstOffset)
                        yDup.put(nv21, srcOffset, width)
                    } else {
                        for (col in 0 until width) {
                            val idx = dstOffset + col * yPixelStride
                            if (idx < yDup.limit()) {
                                yDup.put(idx, nv21[srcOffset + col])
                            }
                        }
                    }
                }
            }

            val uvStart = width * height
            val chromaH = height / 2
            val chromaW = width / 2
            val uDup = uBuf.duplicate()
            val vDup = vBuf.duplicate()
            val uRowStride = uPlane.rowStride
            val vRowStride = vPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vPixelStride = vPlane.pixelStride

            for (row in 0 until chromaH) {
                for (col in 0 until chromaW) {
                    val nv21Idx = uvStart + row * width + col * 2
                    if (nv21Idx + 1 >= nv21.size) break
                    val vVal = nv21[nv21Idx]
                    val uVal = nv21[nv21Idx + 1]

                    val uIdx = row * uRowStride + col * uPixelStride
                    val vIdx = row * vRowStride + col * vPixelStride
                    if (uIdx < uDup.limit()) uDup.put(uIdx, uVal)
                    if (vIdx < vDup.limit()) vDup.put(vIdx, vVal)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun hookCamera2(classLoader: ClassLoader, packageName: String) {
        try {
            val captureRequestBuilderClass = XposedHelpers.findClass(
                "android.hardware.camera2.CaptureRequest\$Builder",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                captureRequestBuilderClass,
                "addTarget",
                Surface::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val targetSurface = param.args[0] as? Surface ?: return
                        if (!targetSurface.isValid || isDummySurface(targetSurface)) return
                        auditSurface(targetSurface, "CaptureRequest.Builder.addTarget")
                        if (isCurrentlyBypassed()) return
                        if (!isImageReaderSurface(targetSurface) && ShopeeMainHook.hasVirtualVideoAvailable(packageName)) {
                            ShopeeMainHook.playVideoOnSurface(targetSurface, packageName)
                            val dummy = getOrCreateDummyCamera2Surface(targetSurface)
                            param.args[0] = dummy
                        }
                    }
                }
            )

            try {
                XposedHelpers.findAndHookMethod(
                    captureRequestBuilderClass,
                    "removeTarget",
                    Surface::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val targetSurface = param.args[0] as? Surface ?: return
                            swappedCamera2Surfaces[targetSurface]?.let { dummy ->
                                param.args[0] = dummy
                            }
                        }
                    }
                )
            } catch (_: Throwable) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val outputConfigClass = XposedHelpers.findClass("android.hardware.camera2.params.OutputConfiguration", classLoader)
                    XposedBridge.hookAllConstructors(
                        outputConfigClass,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (isCreatingDummyOutputConfig.get() == true) return
                                val args = param.args ?: return
                                val bypassed = isCurrentlyBypassed()
                                for (i in args.indices) {
                                    val arg = args[i]
                                    if (arg is Surface && arg.isValid && !isDummySurface(arg) && !isImageReaderSurface(arg)) {
                                        auditSurface(arg, "OutputConfiguration.ctor")
                                        if (!bypassed && ShopeeMainHook.hasVirtualVideoAvailable(packageName)) {
                                            ShopeeMainHook.playVideoOnSurface(arg, packageName)
                                            args[i] = getOrCreateDummyCamera2Surface(arg)
                                        }
                                    }
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {}
            }

            val candidateDeviceClasses = mutableListOf<Class<*>>()
            try {
                candidateDeviceClasses.add(XposedHelpers.findClass("android.hardware.camera2.impl.CameraDeviceImpl", classLoader))
            } catch (_: Throwable) {}
            try {
                candidateDeviceClasses.add(XposedHelpers.findClass("android.hardware.camera2.impl.CameraDeviceImpl", ClassLoader.getSystemClassLoader()))
            } catch (_: Throwable) {}
            candidateDeviceClasses.add(CameraDevice::class.java)

            for (deviceClass in candidateDeviceClasses.distinct()) {
                try {
                    XposedHelpers.findAndHookMethod(
                        deviceClass,
                        "createCaptureSession",
                        List::class.java,
                        CameraCaptureSession.StateCallback::class.java,
                        Handler::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                registerCameraOpened(param.thisObject)
                                val surfaces = param.args[0] as? List<*> ?: return
                                val canSwap = !isCurrentlyBypassed() && ShopeeMainHook.hasVirtualVideoAvailable(packageName)
                                val newList = ArrayList<Any?>(surfaces.size)
                                var didSwap = false
                                for (item in surfaces) {
                                    if (item is Surface && item.isValid && !isDummySurface(item)) {
                                        auditSurface(item, "CameraDevice.createCaptureSession")
                                        if (!isImageReaderSurface(item) && canSwap) {
                                            ShopeeMainHook.playVideoOnSurface(item, packageName)
                                            newList.add(getOrCreateDummyCamera2Surface(item))
                                            didSwap = true
                                            continue
                                        }
                                    }
                                    newList.add(item)
                                }
                                if (didSwap) {
                                    param.args[0] = newList
                                    if (loggedSurfaceTypes.add("camera2_dummy_swap_list")) {
                                        ShopeeMainHook.logInfo("Camera2 createCaptureSession Surface dialihkan ke dummy agar VCam Shopee & HAL tidak bentrok")
                                    }
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    listOf("createCaptureSession", "createCustomCaptureSession").forEach { methodName ->
                        try {
                            XposedHelpers.findAndHookMethod(
                                deviceClass,
                                methodName,
                                SessionConfiguration::class.java,
                                object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam) {
                                        registerCameraOpened(param.thisObject)
                                        val sessionConfig = param.args[0] as? SessionConfiguration ?: return
                                        val canSwap = !isCurrentlyBypassed() && ShopeeMainHook.hasVirtualVideoAvailable(packageName)
                                        val origOutputs = sessionConfig.outputConfigurations
                                        val newOutputs = ArrayList<OutputConfiguration>(origOutputs.size)
                                        var didSwap = false
                                        for (oc in origOutputs) {
                                            val surf = oc.surface
                                            if (surf != null && surf.isValid && !isDummySurface(surf)) {
                                                auditSurface(surf, "CameraDevice.$methodName(SessionConfig)")
                                                if (!isImageReaderSurface(surf) && canSwap) {
                                                    ShopeeMainHook.playVideoOnSurface(surf, packageName)
                                                    val dummy = getOrCreateDummyCamera2Surface(surf)
                                                    val newOc = try {
                                                        isCreatingDummyOutputConfig.set(true)
                                                        OutputConfiguration(dummy)
                                                    } catch (_: Throwable) {
                                                        oc
                                                    } finally {
                                                        isCreatingDummyOutputConfig.set(false)
                                                    }
                                                    newOutputs.add(newOc)
                                                    if (newOc !== oc) didSwap = true
                                                    continue
                                                }
                                            }
                                            newOutputs.add(oc)
                                        }
                                        if (didSwap) {
                                            try {
                                                val rebuilt = SessionConfiguration(
                                                    sessionConfig.sessionType,
                                                    newOutputs,
                                                    sessionConfig.executor,
                                                    sessionConfig.stateCallback
                                                ).apply {
                                                    sessionConfig.inputConfiguration?.let { inputConfiguration = it }
                                                    sessionConfig.sessionParameters?.let { sessionParameters = it }
                                                }
                                                param.args[0] = rebuilt
                                                if (loggedSurfaceTypes.add("camera2_dummy_swap_session_cfg")) {
                                                    ShopeeMainHook.logInfo("Camera2 $methodName(SessionConfig) Surface dialihkan ke dummy")
                                                }
                                            } catch (_: Throwable) {}
                                        }
                                    }
                                }
                            )
                        } catch (_: Throwable) {}
                    }
                }

                try {
                    XposedHelpers.findAndHookMethod(
                        deviceClass,
                        "createCaptureSessionByOutputConfigurations",
                        List::class.java,
                        CameraCaptureSession.StateCallback::class.java,
                        Handler::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                registerCameraOpened(param.thisObject)
                                val configs = param.args[0] as? List<*> ?: return
                                val canSwap = !isCurrentlyBypassed() && ShopeeMainHook.hasVirtualVideoAvailable(packageName)
                                val replaced = ArrayList<Any?>(configs.size)
                                var didSwap = false
                                for (oc in configs) {
                                    val surf = (oc as? OutputConfiguration)?.surface
                                    if (surf != null && surf.isValid && !isDummySurface(surf)) {
                                        auditSurface(surf, "CameraDevice.createCaptureSessionByOutputConfigurations")
                                        if (!isImageReaderSurface(surf) && canSwap) {
                                            ShopeeMainHook.playVideoOnSurface(surf, packageName)
                                            val dummy = getOrCreateDummyCamera2Surface(surf)
                                            val newOc = try {
                                                isCreatingDummyOutputConfig.set(true)
                                                OutputConfiguration(dummy)
                                            } catch (_: Throwable) {
                                                oc
                                            } finally {
                                                isCreatingDummyOutputConfig.set(false)
                                            }
                                            replaced.add(newOc)
                                            if (newOc !== oc) didSwap = true
                                            continue
                                        }
                                    }
                                    replaced.add(oc)
                                }
                                if (didSwap) {
                                    param.args[0] = replaced
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {}

                try {
                    XposedHelpers.findAndHookMethod(
                        deviceClass,
                        "close",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                registerCameraClosed(param.thisObject)
                                ShopeeMainHook.scheduleSessionCloseCheck("CameraDevice.close", packageName)
                            }
                        }
                    )
                } catch (_: Throwable) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val outputConfigClass = XposedHelpers.findClass("android.hardware.camera2.params.OutputConfiguration", classLoader)
                    XposedHelpers.findAndHookMethod(
                        outputConfigClass,
                        "addSurface",
                        Surface::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val surf = param.args[0] as? Surface ?: return
                                if (!surf.isValid || isDummySurface(surf) || isImageReaderSurface(surf)) return
                                auditSurface(surf, "OutputConfiguration.addSurface")
                                if (!isCurrentlyBypassed() && ShopeeMainHook.hasVirtualVideoAvailable(packageName)) {
                                    ShopeeMainHook.playVideoOnSurface(surf, packageName)
                                    param.args[0] = getOrCreateDummyCamera2Surface(surf)
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {}
            }

            val candidateSessionClasses = mutableListOf<Class<*>>()
            try {
                candidateSessionClasses.add(XposedHelpers.findClass("android.hardware.camera2.impl.CameraCaptureSessionImpl", classLoader))
            } catch (_: Throwable) {}
            try {
                candidateSessionClasses.add(XposedHelpers.findClass("android.hardware.camera2.impl.CameraCaptureSessionImpl", ClassLoader.getSystemClassLoader()))
            } catch (_: Throwable) {}
            candidateSessionClasses.add(CameraCaptureSession::class.java)

            for (sessionClass in candidateSessionClasses.distinct()) {
                listOf("setRepeatingRequest", "capture").forEach { methodName ->
                    try {
                        XposedHelpers.findAndHookMethod(
                            sessionClass,
                            methodName,
                            CaptureRequest::class.java,
                            CameraCaptureSession.CaptureCallback::class.java,
                            Handler::class.java,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (isCurrentlyBypassed()) return
                                    val req = param.args[0] as? CaptureRequest
                                    extractAndPlayFromRequest(req, packageName, methodName)
                                }
                            }
                        )
                    } catch (_: Throwable) {}
                }

                listOf("setRepeatingBurst", "captureBurst").forEach { methodName ->
                    try {
                        XposedHelpers.findAndHookMethod(
                            sessionClass,
                            methodName,
                            List::class.java,
                            CameraCaptureSession.CaptureCallback::class.java,
                            Handler::class.java,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (isCurrentlyBypassed()) return
                                    val list = param.args[0] as? List<*> ?: return
                                    for (item in list) {
                                        if (item is CaptureRequest) {
                                            extractAndPlayFromRequest(item, packageName, methodName)
                                        }
                                    }
                                }
                            }
                        )
                    } catch (_: Throwable) {}
                }

                try {
                    XposedHelpers.findAndHookMethod(
                        sessionClass,
                        "close",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                ShopeeMainHook.scheduleSessionCloseCheck("CaptureSession.close", packageName)
                            }
                        }
                    )
                } catch (_: Throwable) {}
            }

            ShopeeMainHook.logInfo("Camera2 hook Shopee terpasang di $packageName")
        } catch (t: Throwable) {
            ShopeeMainHook.logWarn("Camera2 hook Shopee error: ${t.message}")
        }
    }

    private fun extractAndPlayFromRequest(request: CaptureRequest?, packageName: String, source: String) {
        if (request == null) {
            ShopeeMainHook.onFallbackActiveSurfaceRequested(packageName)
            return
        }
        var found = false
        try {
            val targets = XposedHelpers.callMethod(request, "getTargets") as? Collection<*>
            if (!targets.isNullOrEmpty()) {
                for (t in targets) {
                    if (t is Surface) {
                        if (isDummySurface(t)) {
                            resolveRealSurface(t)?.takeIf { it.isValid && !isImageReaderSurface(it) }?.let { real ->
                                ShopeeMainHook.playVideoOnSurface(real, packageName)
                                found = true
                            }
                        } else if (t.isValid) {
                            auditSurface(t, "CaptureSession.$source")
                        }
                    }
                }
                if (!found) {
                    pickPreferredSurface(targets)?.let { preferred ->
                        if (!isImageReaderSurface(preferred)) {
                            ShopeeMainHook.playVideoOnSurface(preferred, packageName)
                        }
                        found = true
                    }
                }
            }
        } catch (_: Throwable) {}

        if (!found) {
            try {
                val surfacesField = XposedHelpers.findFieldIfExists(request.javaClass, "mSurfaces")
                if (surfacesField != null) {
                    surfacesField.isAccessible = true
                    val raw = surfacesField.get(request)
                    val items = when (raw) {
                        is Collection<*> -> raw
                        is Array<*> -> raw.toList()
                        else -> emptyList<Any?>()
                    }
                    for (t in items) {
                        if (t is Surface) {
                            if (isDummySurface(t)) {
                                resolveRealSurface(t)?.takeIf { it.isValid && !isImageReaderSurface(it) }?.let { real ->
                                    ShopeeMainHook.playVideoOnSurface(real, packageName)
                                    found = true
                                }
                            } else if (t.isValid) {
                                auditSurface(t, "CaptureSession.$source(mSurfaces)")
                            }
                        }
                    }
                    if (!found) {
                        pickPreferredSurface(items)?.let { preferred ->
                            if (!isImageReaderSurface(preferred)) {
                                ShopeeMainHook.playVideoOnSurface(preferred, packageName)
                            }
                            found = true
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        if (!found) {
            ShopeeMainHook.onFallbackActiveSurfaceRequested(packageName)
        }
    }
}
