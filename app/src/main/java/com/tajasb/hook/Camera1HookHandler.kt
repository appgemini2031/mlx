@file:Suppress("DEPRECATION")

package com.tajasb.hook

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import com.tajasb.MainHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.io.ByteArrayOutputStream

/**
 * Camera1HookHandler:
 * Modular Xposed hook handler for legacy Camera1 API (android.hardware.Camera),
 * intercepting preview surfaces, takePicture JPEG capture, and NV21 preview callbacks.
 */
object Camera1HookHandler {
    private const val TAG = "TajasbCam"

    private var cachedNv21Data: ByteArray? = null
    private var cachedNv21Width = 0
    private var cachedNv21Height = 0
    private var cachedNv21Rot = -1
    private var cachedNv21Mirror = false
    private val hookedCallbackClasses = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun invalidateNv21Cache() {
        cachedNv21Rot = -1
    }

    fun hookCamera1(classLoader: ClassLoader, packageName: String) {
        try {
            val cameraClass = XposedHelpers.findClass("android.hardware.Camera", classLoader)

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "setPreviewTexture",
                SurfaceTexture::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val texture = param.args[0] as? SurfaceTexture ?: return
                        MainHook.onSurfaceTextureAvailable(texture)
                        val surface = Surface(texture)
                        MainHook.playVideoOnSurface(surface, packageName)
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
                                MainHook.playVideoOnSurface(surface, packageName)
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
                        com.tajasb.engine.MultiCameraRouter.registerCameraOpened(param.thisObject)
                        MainHook.onStartPreviewRequested(packageName)
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "stopPreview",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        MainHook.stopVideoPlayback(reason = "Camera.stopPreview")
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "release",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        com.tajasb.engine.MultiCameraRouter.registerCameraClosed(param.thisObject)
                        MainHook.stopVideoPlayback(reason = "Camera.release", force = true)
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
                        var bitmap = MainHook.getVideoFrameBitmap(packageName) ?: return
                        try {
                            val rot = (MainHook.liveRotation + 270) % 360
                            val mirror = MainHook.liveMirror
                            bitmap = MainHook.transformBitmap(bitmap, rot, mirror)

                            val bos = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                            val jpegBytes = bos.toByteArray()

                            val shutterCallback = param.args[0] as? Camera.ShutterCallback
                            val jpegCallback = param.args[3] as? Camera.PictureCallback
                                ?: param.args[1] as? Camera.PictureCallback

                            shutterCallback?.onShutter()
                            jpegCallback?.onPictureTaken(jpegBytes, param.thisObject as? Camera)

                            param.result = null
                        } catch (t: Throwable) {
                            Log.e(TAG, "Failed to inject virtual frame into Camera1: ${t.message}")
                        }
                    }
                }
            )

            try {
                XposedHelpers.findAndHookMethod(
                    cameraClass,
                    "setPreviewCallback",
                    Camera.PreviewCallback::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            hookPreviewCallback(param, packageName)
                        }
                    }
                )
            } catch (_: Throwable) {}

            try {
                XposedHelpers.findAndHookMethod(
                    cameraClass,
                    "setOneShotPreviewCallback",
                    Camera.PreviewCallback::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            hookPreviewCallback(param, packageName)
                        }
                    }
                )
            } catch (_: Throwable) {}

            try {
                XposedHelpers.findAndHookMethod(
                    cameraClass,
                    "setPreviewCallbackWithBuffer",
                    Camera.PreviewCallback::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            hookPreviewCallback(param, packageName)
                        }
                    }
                )
            } catch (_: Throwable) {}

            Log.i(TAG, "Camera1 hooks installed successfully for $packageName")
        } catch (t: Throwable) {
            Log.w(TAG, "Camera1 hook skipped: ${t.message}")
        }
    }

    private fun hookPreviewCallback(param: XC_MethodHook.MethodHookParam, packageName: String) {
        val originalCallback = param.args[0] ?: return
        val cbClass = originalCallback.javaClass
        val cbClassName = cbClass.name
        if (!hookedCallbackClasses.add(cbClassName)) {
            return
        }
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

                            val rot = (MainHook.liveRotation + 270) % 360
                            val mirror = MainHook.liveMirror

                            if (cachedNv21Data == null ||
                                cachedNv21Width != size.width ||
                                cachedNv21Height != size.height ||
                                cachedNv21Rot != rot ||
                                cachedNv21Mirror != mirror
                            ) {
                                val rawBmp = MainHook.getVideoFrameBitmap(packageName)
                                if (rawBmp != null) {
                                    val transformed = MainHook.transformBitmap(rawBmp, rot, mirror)
                                    cachedNv21Data = MainHook.bitmapToNv21(transformed, size.width, size.height)
                                    cachedNv21Width = size.width
                                    cachedNv21Height = size.height
                                    cachedNv21Rot = rot
                                    cachedNv21Mirror = mirror
                                    if (transformed != rawBmp && !transformed.isRecycled) {
                                        transformed.recycle()
                                    }
                                    if (!rawBmp.isRecycled) {
                                        rawBmp.recycle()
                                    }
                                }
                            }

                            cachedNv21Data?.let { fakeBytes ->
                                if (fakeBytes.size <= data.size) {
                                    System.arraycopy(fakeBytes, 0, data, 0, fakeBytes.size)
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
        } catch (_: Throwable) {}
    }
}
