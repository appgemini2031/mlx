@file:Suppress("DEPRECATION")
package com.tajasb.engine

import android.hardware.Camera
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.Executor

/**
 * MultiCameraRouter:
 * Intercepts CameraManager.openCamera to route stream between Back Camera (ID 0),
 * Front Camera (ID 1), and Ultra-wide (ID 2).
 * Automatically toggles front-facing mirroring and sensor-to-display orientation matrices
 * so streamers don't have to manually flip mirror when toggling front/back cameras in TikTok/Shopee.
 */
object MultiCameraRouter {

    private const val TAG = "MultiCameraRouter"

    @Volatile
    var currentActiveCameraId: String = "0"
        private set

    @Volatile
    var isFrontFacing: Boolean = false
        private set

    private val activeCameraTokens = java.util.concurrent.ConcurrentHashMap.newKeySet<Any>()

    fun registerCameraOpened(token: Any?) {
        if (token != null) {
            activeCameraTokens.add(token)
            Log.d(TAG, "MultiCameraRouter: Camera registered (active count=${activeCameraTokens.size})")
        }
    }

    fun registerCameraClosed(token: Any?) {
        if (token != null) {
            activeCameraTokens.remove(token)
            Log.d(TAG, "MultiCameraRouter: Camera deregistered (active count=${activeCameraTokens.size})")
        } else {
            activeCameraTokens.clear()
        }
    }

    fun isAnyCameraActive(): Boolean = activeCameraTokens.isNotEmpty()

    private val listeners = mutableListOf<(cameraId: String, isFront: Boolean) -> Unit>()

    fun addCameraSwitchListener(listener: (cameraId: String, isFront: Boolean) -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    private fun notifyCameraSwitched(cameraId: String) {
        currentActiveCameraId = cameraId
        isFrontFacing = (cameraId == "1")
        Log.i(TAG, "MultiCameraRouter: Active Camera ID=$cameraId, isFrontFacing=$isFrontFacing")
        synchronized(listeners) {
            for (l in listeners) {
                try {
                    l(cameraId, isFrontFacing)
                } catch (t: Throwable) {
                    Log.e(TAG, "Listener notification error: ${t.message}")
                }
            }
        }
    }

    fun hookCameraManager(classLoader: ClassLoader, onCameraOpen: ((cameraId: String, isFront: Boolean) -> Unit)? = null) {
        if (onCameraOpen != null) {
            addCameraSwitchListener(onCameraOpen)
        }

        // 1. Hook CameraManager.openCamera(String, CameraDevice.StateCallback, Handler)
        try {
            XposedHelpers.findAndHookMethod(
                CameraManager::class.java,
                "openCamera",
                String::class.java,
                CameraDevice.StateCallback::class.java,
                Handler::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val cameraId = param.args[0] as? String ?: "0"
                        notifyCameraSwitched(cameraId)
                    }
                }
            )
            Log.i(TAG, "Hooked CameraManager.openCamera(String, StateCallback, Handler)")
        } catch (t: Throwable) {
            Log.w(TAG, "CameraManager.openCamera (Handler) hook skipped: ${t.message}")
        }

        // 2. Hook CameraManager.openCamera(String, Executor, CameraDevice.StateCallback) (Android P+)
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
                            val cameraId = param.args[0] as? String ?: "0"
                            notifyCameraSwitched(cameraId)
                        }
                    }
                )
                Log.i(TAG, "Hooked CameraManager.openCamera(String, Executor, StateCallback)")
            } catch (t: Throwable) {
                Log.w(TAG, "CameraManager.openCamera (Executor) hook skipped: ${t.message}")
            }
        }

        // 3. Hook Legacy Camera1 Camera.open(int)
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
            Log.i(TAG, "Hooked Camera.open(int)")
        } catch (t: Throwable) {
            Log.w(TAG, "Camera.open(int) hook skipped: ${t.message}")
        }
    }
}
