package com.tajasb.engine

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * Auto3AStateEngine:
 * Synthesizes dynamic 3A state (Auto Focus, Auto Exposure, Auto White Balance) and Flash
 * across Camera2 CaptureResult callbacks.
 *
 * Prevents apps like TikTok Live, Shopee Live, and Instagram from hanging or reporting
 * camera focus freeze by guaranteeing that CONTROL_AF_STATE reports FOCUSED_LOCKED,
 * CONTROL_AE_STATE reports CONVERGED, and CONTROL_AWB_STATE reports CONVERGED.
 */
object Auto3AStateEngine {

    private const val TAG = "Auto3AStateEngine"
    private var isHooked = false

    fun hookCaptureResult(classLoader: ClassLoader) {
        if (isHooked) return

        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val key = param.args.getOrNull(0) ?: return
                val keyName = try {
                    if (key is CaptureResult.Key<*>) key.name else key.toString()
                } catch (_: Throwable) {
                    key.toString()
                }

                when {
                    keyName.contains("control.afState", ignoreCase = true) ||
                    key == CaptureResult.CONTROL_AF_STATE -> {
                        // AF Locked sharp (CONTROL_AF_STATE_FOCUSED_LOCKED = 4)
                        param.result = CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED
                    }
                    keyName.contains("control.aeState", ignoreCase = true) ||
                    key == CaptureResult.CONTROL_AE_STATE -> {
                        // AE Converged perfectly (CONTROL_AE_STATE_CONVERGED = 2)
                        param.result = CameraMetadata.CONTROL_AE_STATE_CONVERGED
                    }
                    keyName.contains("control.awbState", ignoreCase = true) ||
                    key == CaptureResult.CONTROL_AWB_STATE -> {
                        // AWB Converged white balance locked (CONTROL_AWB_STATE_CONVERGED = 2)
                        param.result = CameraMetadata.CONTROL_AWB_STATE_CONVERGED
                    }
                    keyName.contains("flash.state", ignoreCase = true) ||
                    key == CaptureResult.FLASH_STATE -> {
                        // Flash Ready (FLASH_STATE_READY = 3)
                        param.result = CameraMetadata.FLASH_STATE_READY
                    }
                    keyName.contains("lens.state", ignoreCase = true) ||
                    key == CaptureResult.LENS_STATE -> {
                        // Lens Stationary (LENS_STATE_STATIONARY = 0)
                        param.result = CameraMetadata.LENS_STATE_STATIONARY
                    }
                    keyName.contains("control.mode", ignoreCase = true) ||
                    key == CaptureResult.CONTROL_MODE -> {
                        param.result = CameraMetadata.CONTROL_MODE_AUTO
                    }
                }
            }
        }

        // 1. Hook CameraMetadataNative.get(CaptureResult.Key)
        try {
            val metadataNativeClass = XposedHelpers.findClass(
                "android.hardware.camera2.impl.CameraMetadataNative",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                metadataNativeClass,
                "get",
                CaptureResult.Key::class.java,
                hook
            )
            Log.i(TAG, "Hooked CameraMetadataNative.get(CaptureResult.Key)")
        } catch (t: Throwable) {
            Log.w(TAG, "CameraMetadataNative hook skipped: ${t.message}")
        }

        // 2. Hook CaptureResult.get(CaptureResult.Key)
        try {
            XposedHelpers.findAndHookMethod(
                CaptureResult::class.java,
                "get",
                CaptureResult.Key::class.java,
                hook
            )
            Log.i(TAG, "Hooked CaptureResult.get(CaptureResult.Key)")
        } catch (t: Throwable) {
            Log.w(TAG, "CaptureResult hook skipped: ${t.message}")
        }

        // 3. Hook TotalCaptureResult.get(CaptureResult.Key)
        try {
            XposedHelpers.findAndHookMethod(
                TotalCaptureResult::class.java,
                "get",
                CaptureResult.Key::class.java,
                hook
            )
            Log.i(TAG, "Hooked TotalCaptureResult.get(CaptureResult.Key)")
        } catch (t: Throwable) {
            Log.w(TAG, "TotalCaptureResult hook skipped: ${t.message}")
        }

        isHooked = true
    }
}
