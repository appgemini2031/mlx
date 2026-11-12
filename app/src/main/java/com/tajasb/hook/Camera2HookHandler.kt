@file:Suppress("DEPRECATION")

package com.tajasb.hook

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.Surface
import com.tajasb.MainHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * Camera2HookHandler:
 * Modular Xposed hook handler for Camera2 & CameraX pipelines, intercepting CaptureRequest targets,
 * SessionConfiguration, OutputConfiguration, and CameraCaptureSession requests.
 */
object Camera2HookHandler {
    private const val TAG = "TajasbCam"

    fun hookCamera2(classLoader: ClassLoader, packageName: String) {
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
                            MainHook.playVideoOnSurface(targetSurface, packageName)
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
            try {
                candidateDeviceClasses.add(CameraDevice::class.java)
            } catch (_: Throwable) {}

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
                                com.tajasb.engine.MultiCameraRouter.registerCameraOpened(param.thisObject)
                                val surfaces = param.args[0] as? List<*> ?: return
                                for (item in surfaces) {
                                    if (item is Surface && item.isValid) {
                                        MainHook.playVideoOnSurface(item, packageName)
                                        break
                                    }
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {}

                try {
                    XposedHelpers.findAndHookMethod(
                        deviceClass,
                        "createCustomCaptureSession",
                        SessionConfiguration::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                com.tajasb.engine.MultiCameraRouter.registerCameraOpened(param.thisObject)
                                val sessionConfig = param.args[0] as? SessionConfiguration ?: return
                                for (output in sessionConfig.outputConfigurations) {
                                    output.surface?.let { surf ->
                                        if (surf.isValid) {
                                            MainHook.playVideoOnSurface(surf, packageName)
                                        }
                                    }
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        XposedHelpers.findAndHookMethod(
                            deviceClass,
                            "createCaptureSession",
                            SessionConfiguration::class.java,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    com.tajasb.engine.MultiCameraRouter.registerCameraOpened(param.thisObject)
                                    val sessionConfig = param.args[0] as? SessionConfiguration ?: return
                                    for (output in sessionConfig.outputConfigurations) {
                                        output.surface?.let { surf ->
                                            if (surf.isValid) {
                                                MainHook.playVideoOnSurface(surf, packageName)
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    } catch (_: Throwable) {}
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
                                com.tajasb.engine.MultiCameraRouter.registerCameraOpened(param.thisObject)
                                val configs = param.args[0] as? List<*> ?: return
                                for (cfg in configs) {
                                    if (cfg is OutputConfiguration) {
                                        cfg.surface?.let { surf ->
                                            if (surf.isValid) {
                                                MainHook.playVideoOnSurface(surf, packageName)
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
                                MainHook.onCameraDeviceClosed(param.thisObject, packageName)
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
                                    MainHook.playVideoOnSurface(surf, packageName)
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
            try {
                candidateSessionClasses.add(CameraCaptureSession::class.java)
            } catch (_: Throwable) {}

            for (sessionClass in candidateSessionClasses.distinct()) {
                try {
                    XposedHelpers.findAndHookMethod(
                        sessionClass,
                        "setRepeatingRequest",
                        CaptureRequest::class.java,
                        CameraCaptureSession.CaptureCallback::class.java,
                        Handler::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val request = param.args[0] as? CaptureRequest
                                extractAndPlaySurfacesFromRequest(request, packageName)
                            }
                        }
                    )
                } catch (_: Throwable) {}

                try {
                    XposedHelpers.findAndHookMethod(
                        sessionClass,
                        "setRepeatingBurst",
                        List::class.java,
                        CameraCaptureSession.CaptureCallback::class.java,
                        Handler::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val list = param.args[0] as? List<*> ?: return
                                for (item in list) {
                                    if (item is CaptureRequest) {
                                        extractAndPlaySurfacesFromRequest(item, packageName)
                                    }
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {}

                try {
                    XposedHelpers.findAndHookMethod(
                        sessionClass,
                        "capture",
                        CaptureRequest::class.java,
                        CameraCaptureSession.CaptureCallback::class.java,
                        Handler::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val request = param.args[0] as? CaptureRequest
                                extractAndPlaySurfacesFromRequest(request, packageName)
                            }
                        }
                    )
                } catch (_: Throwable) {}

                try {
                    XposedHelpers.findAndHookMethod(
                        sessionClass,
                        "captureBurst",
                        List::class.java,
                        CameraCaptureSession.CaptureCallback::class.java,
                        Handler::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val list = param.args[0] as? List<*> ?: return
                                for (item in list) {
                                    if (item is CaptureRequest) {
                                        extractAndPlaySurfacesFromRequest(item, packageName)
                                    }
                                }
                            }
                        }
                    )
                } catch (_: Throwable) {}

                try {
                    XposedHelpers.findAndHookMethod(
                        sessionClass,
                        "close",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                MainHook.onCaptureSessionClosed(param.thisObject, packageName)
                            }
                        }
                    )
                } catch (_: Throwable) {}
            }

            Log.i(TAG, "Camera2 hooks installed successfully for $packageName")
        } catch (t: Throwable) {
            Log.w(TAG, "Camera2 hook error: ${t.message}")
        }
    }

    fun extractAndPlaySurfacesFromRequest(request: CaptureRequest?, packageName: String) {
        if (request == null) {
            MainHook.onFallbackActiveSurfaceRequested(packageName)
            return
        }
        var foundAny = false

        try {
            val targets = XposedHelpers.callMethod(request, "getTargets") as? Collection<*>
            if (!targets.isNullOrEmpty()) {
                for (t in targets) {
                    if (t is Surface && t.isValid) {
                        MainHook.playVideoOnSurface(t, packageName)
                        foundAny = true
                        break
                    }
                }
            }
        } catch (_: Throwable) {}

        if (!foundAny) {
            try {
                val surfacesField = XposedHelpers.findFieldIfExists(request.javaClass, "mSurfaces")
                if (surfacesField != null) {
                    surfacesField.isAccessible = true
                    val rawSurfaces = surfacesField.get(request)
                    if (rawSurfaces is Collection<*>) {
                        for (t in rawSurfaces) {
                            if (t is Surface && t.isValid) {
                                MainHook.playVideoOnSurface(t, packageName)
                                foundAny = true
                                break
                            }
                        }
                    } else if (rawSurfaces is Array<*>) {
                        for (t in rawSurfaces) {
                            if (t is Surface && t.isValid) {
                                MainHook.playVideoOnSurface(t, packageName)
                                foundAny = true
                                break
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        if (!foundAny) {
            try {
                val surfaceSetField = XposedHelpers.findFieldIfExists(request.javaClass, "mSurfaceSet")
                if (surfaceSetField != null) {
                    surfaceSetField.isAccessible = true
                    val surfaceSet = surfaceSetField.get(request)
                    if (surfaceSet is Collection<*>) {
                        for (t in surfaceSet) {
                            if (t is Surface && t.isValid) {
                                MainHook.playVideoOnSurface(t, packageName)
                                foundAny = true
                                break
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        if (!foundAny) {
            MainHook.onFallbackActiveSurfaceRequested(packageName)
        }
    }
}
