@file:Suppress("DEPRECATION")

package com.tajasb.hooks.bigo

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import com.tajasb.hook.YuvFrameConverter
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * BigoHookRouter:
 * Router & pencegat pipeline kamera terisolasi untuk BIGO Live (sg.bigo.live / sg.bigo.lite).
 * Mencakup audit tipe Surface (TextureView / ImageReader / Beauty SDK),
 * pelacakan Multi-Camera ID (0/1), dukungan Camera1 setPreviewCallbackWithBuffer dengan
 * validasi ukuran buffer NV21, serta hook Camera2 SessionConfiguration & CaptureRequest.
 */
object BigoHookRouter {
    private const val TAG = "Tajasb-BIGO"

    @Volatile
    var currentActiveCameraId: String = "0"
        private set

    @Volatile
    var isFrontFacing: Boolean = false
        private set

    private val activeCameraTokens = ConcurrentHashMap.newKeySet<Any>()
    private val loggedSurfaceTypes = ConcurrentHashMap.newKeySet<String>()
    private val hookedCallbackClasses = ConcurrentHashMap.newKeySet<String>()

    private var cachedNv21Data: ByteArray? = null
    private var cachedNv21Width = 0
    private var cachedNv21Height = 0
    private var cachedNv21Rot = -1
    private var cachedNv21Mirror = false

    fun isMainBigoProcess(packageName: String?, processName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (processName.isNullOrBlank()) return true
        return processName == packageName
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

    private fun auditSurface(surface: Surface, sourceMethod: String) {
        val desc = try { surface.toString() } catch (_: Throwable) { "Surface@unknown" }
        val kind = when {
            desc.contains("ImageReader", ignoreCase = true) -> "ImageReader(YUV/BeautySDK)"
            desc.contains("SurfaceTexture", ignoreCase = true) -> "SurfaceTexture"
            else -> "NativeSurface"
        }
        val key = "$sourceMethod:$kind"
        if (loggedSurfaceTypes.add(key)) {
            Log.d(TAG, "Audit Surface [$sourceMethod] -> $kind ($desc)")
        }
    }

    private fun notifyCameraSwitched(cameraId: String) {
        currentActiveCameraId = cameraId
        isFrontFacing = (cameraId == "1")
        Log.i(TAG, "Kamera BIGO aktif: ID=$cameraId (Front=$isFrontFacing)")
        BigoMainHook.onFrontFacingChanged(isFrontFacing)
    }

    fun installHooks(classLoader: ClassLoader, packageName: String) {
        hookCameraManager(classLoader)
        hookCamera1(classLoader, packageName)
        hookCamera2(classLoader, packageName)
    }

    private fun hookCameraManager(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CameraManager::class.java,
                "openCamera",
                String::class.java,
                CameraDevice.StateCallback::class.java,
                Handler::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val camId = param.args[0] as? String ?: "0"
                        notifyCameraSwitched(camId)
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "CameraManager.openCamera(Handler) hook skipped: ${t.message}")
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
                            val camId = param.args[0] as? String ?: "0"
                            notifyCameraSwitched(camId)
                        }
                    }
                )
            } catch (t: Throwable) {
                Log.w(TAG, "CameraManager.openCamera(Executor) hook skipped: ${t.message}")
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
            Log.w(TAG, "Camera.open(int) hook skipped: ${t.message}")
        }
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
                        BigoMainHook.onSurfaceTextureAvailable(texture)
                        val surface = Surface(texture)
                        auditSurface(surface, "Camera1.setPreviewTexture")
                        BigoMainHook.playVideoOnSurface(surface, packageName)
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "setPreviewDisplay",
                SurfaceHolder::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val holder = param.args[0] as? SurfaceHolder ?: return
                        holder.surface?.let { surface ->
                            if (surface.isValid) {
                                auditSurface(surface, "Camera1.setPreviewDisplay")
                                BigoMainHook.playVideoOnSurface(surface, packageName)
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
                        BigoMainHook.onStartPreviewRequested(packageName)
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "stopPreview",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        BigoMainHook.scheduleSessionCloseCheck("Camera1.stopPreview", packageName)
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "release",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        registerCameraClosed(param.thisObject)
                        BigoMainHook.scheduleSessionCloseCheck("Camera1.release", packageName)
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
                        val videoFile = BigoMainHook.getVirtualVideoFile() ?: return
                        var bitmap = YuvFrameConverter.extractVideoFrameBitmap(videoFile) ?: return
                        try {
                            val cfg = BigoMainHook.liveConfig
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
                            Log.w(TAG, "Camera1 takePicture BIGO error: ${t.message}")
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
                                Log.d(TAG, "Audit Camera1: $methodName dipanggil di $packageName")
                                hookPreviewCallback(param)
                            }
                        }
                    )
                } catch (_: Throwable) {}
            }

            Log.i(TAG, "Camera1 hook BIGO terpasang di $packageName")
        } catch (t: Throwable) {
            Log.w(TAG, "Camera1 hook BIGO dilewati: ${t.message}")
        }
    }

    private fun hookPreviewCallback(param: XC_MethodHook.MethodHookParam) {
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
                            val data = cbParam.args[0] as? ByteArray ?: return
                            val camera = cbParam.args[1] as? Camera ?: return
                            val params = camera.parameters ?: return
                            val size = params.previewSize ?: return
                            if (!isNv21BufferValid(data, size.width, size.height)) {
                                val mismatchKey = "nv21_mismatch:${size.width}x${size.height}:${data.size}"
                                if (loggedSurfaceTypes.add(mismatchKey)) {
                                    Log.w(TAG, "NV21 buffer mismatch: data=${data.size} bytes, expected>=${computeExpectedNv21Size(size.width, size.height)} (${size.width}x${size.height})")
                                }
                                return
                            }
                            val cfg = BigoMainHook.liveConfig
                            if (!cfg.enabled) return

                            val nv21ActiveKey = "nv21_active:${size.width}x${size.height}"
                            if (loggedSurfaceTypes.add(nv21ActiveKey)) {
                                Log.i(TAG, "Camera1 NV21 aktif pada ${size.width}x${size.height} (buffer=${data.size} bytes)")
                            }

                            val rot = (cfg.rotation + 270) % 360
                            val mirror = if (isFrontFacing) !cfg.isMirrored else cfg.isMirrored

                            if (cachedNv21Data == null ||
                                cachedNv21Width != size.width ||
                                cachedNv21Height != size.height ||
                                cachedNv21Rot != rot ||
                                cachedNv21Mirror != mirror
                            ) {
                                val videoFile = BigoMainHook.getVirtualVideoFile() ?: return
                                val rawBmp = YuvFrameConverter.extractVideoFrameBitmap(videoFile) ?: return
                                val transformed = YuvFrameConverter.transformBitmap(rawBmp, rot, mirror)
                                cachedNv21Data = YuvFrameConverter.bitmapToNv21(transformed, size.width, size.height)
                                cachedNv21Width = size.width
                                cachedNv21Height = size.height
                                cachedNv21Rot = rot
                                cachedNv21Mirror = mirror
                                if (transformed != rawBmp && !transformed.isRecycled) transformed.recycle()
                                if (!rawBmp.isRecycled) rawBmp.recycle()
                            }

                            val expectedBytes = computeExpectedNv21Size(size.width, size.height)
                            val fakeBytes = cachedNv21Data ?: return
                            if (fakeBytes.size >= expectedBytes && data.size >= expectedBytes) {
                                System.arraycopy(fakeBytes, 0, data, 0, expectedBytes)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "NV21 onPreviewFrame guard warning: ${t.message}")
                        }
                    }
                }
            )
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
                        if (targetSurface.isValid) {
                            auditSurface(targetSurface, "CaptureRequest.Builder.addTarget")
                            BigoMainHook.playVideoOnSurface(targetSurface, packageName)
                        }
                    }
                }
            )

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
                                var played = false
                                for (item in surfaces) {
                                    if (item is Surface && item.isValid) {
                                        auditSurface(item, "CameraDevice.createCaptureSession")
                                        if (!played) {
                                            BigoMainHook.playVideoOnSurface(item, packageName)
                                            played = true
                                        }
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
                                        for (output in sessionConfig.outputConfigurations) {
                                            output.surface?.let { surf ->
                                                if (surf.isValid) {
                                                    auditSurface(surf, "CameraDevice.$methodName(SessionConfig)")
                                                    BigoMainHook.playVideoOnSurface(surf, packageName)
                                                }
                                            }
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
                                for (cfg in configs) {
                                    if (cfg is OutputConfiguration) {
                                        cfg.surface?.let { surf ->
                                            if (surf.isValid) {
                                                auditSurface(surf, "CameraDevice.createCaptureSessionByOutputConfigurations")
                                                BigoMainHook.playVideoOnSurface(surf, packageName)
                                            }
                                        }
                                    }
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
                                BigoMainHook.scheduleSessionCloseCheck("CameraDevice.close", packageName)
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
                                if (surf.isValid) {
                                    auditSurface(surf, "OutputConfiguration.addSurface")
                                    BigoMainHook.playVideoOnSurface(surf, packageName)
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
                                BigoMainHook.scheduleSessionCloseCheck("CaptureSession.close", packageName)
                            }
                        }
                    )
                } catch (_: Throwable) {}
            }

            Log.i(TAG, "Camera2 hook BIGO terpasang di $packageName")
        } catch (t: Throwable) {
            Log.w(TAG, "Camera2 hook BIGO error: ${t.message}")
        }
    }

    private fun extractAndPlayFromRequest(request: CaptureRequest?, packageName: String, source: String) {
        if (request == null) {
            BigoMainHook.onFallbackActiveSurfaceRequested(packageName)
            return
        }
        var found = false
        try {
            val targets = XposedHelpers.callMethod(request, "getTargets") as? Collection<*>
            if (!targets.isNullOrEmpty()) {
                for (t in targets) {
                    if (t is Surface && t.isValid) {
                        auditSurface(t, "CaptureSession.$source")
                        BigoMainHook.playVideoOnSurface(t, packageName)
                        found = true
                        break
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
                        if (t is Surface && t.isValid) {
                            auditSurface(t, "CaptureSession.$source(mSurfaces)")
                            BigoMainHook.playVideoOnSurface(t, packageName)
                            found = true
                            break
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        if (!found) {
            BigoMainHook.onFallbackActiveSurfaceRequested(packageName)
        }
    }
}
