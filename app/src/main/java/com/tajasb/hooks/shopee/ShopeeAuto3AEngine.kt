package com.tajasb.hooks.shopee

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * ShopeeAuto3AEngine:
 * Mensintesis status 3A (Auto Focus, Auto Exposure, Auto White Balance) khusus untuk proses Shopee.
 * Dilengkapi ThreadLocal re-entrancy guard & null-safety ketat agar aman terhadap custom vendor key.
 */
object ShopeeAuto3AEngine {
    private const val TAG = "Tajasb-Shopee"

    @Volatile
    private var isHooked = false

    private val inHookGuard = ThreadLocal.withInitial { false }

    fun hookCaptureResult(classLoader: ClassLoader) {
        if (isHooked) return

        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (inHookGuard.get() == true) return
                inHookGuard.set(true)
                try {
                    val key = param.args?.getOrNull(0) ?: return
                    val keyName = try {
                        if (key is CaptureResult.Key<*>) key.name ?: "" else key.toString()
                    } catch (_: Throwable) {
                        return
                    }
                    if (keyName.isBlank()) return

                    if (param.result != null) return

                    when {
                        keyName.equals("android.control.afState", ignoreCase = true) ||
                            keyName.contains("control.afState", ignoreCase = true) -> {
                            param.result = CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED
                        }
                        keyName.equals("android.control.aeState", ignoreCase = true) ||
                            keyName.contains("control.aeState", ignoreCase = true) -> {
                            param.result = CameraMetadata.CONTROL_AE_STATE_CONVERGED
                        }
                        keyName.equals("android.control.awbState", ignoreCase = true) ||
                            keyName.contains("control.awbState", ignoreCase = true) -> {
                            param.result = CameraMetadata.CONTROL_AWB_STATE_CONVERGED
                        }
                        keyName.equals("android.flash.state", ignoreCase = true) ||
                            keyName.contains("flash.state", ignoreCase = true) -> {
                            param.result = CameraMetadata.FLASH_STATE_READY
                        }
                        keyName.equals("android.lens.state", ignoreCase = true) ||
                            keyName.contains("lens.state", ignoreCase = true) -> {
                            param.result = CameraMetadata.LENS_STATE_STATIONARY
                        }
                        keyName.equals("android.control.mode", ignoreCase = true) ||
                            keyName.contains("control.mode", ignoreCase = true) -> {
                            param.result = CameraMetadata.CONTROL_MODE_AUTO
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    inHookGuard.set(false)
                }
            }
        }

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
            Log.d(TAG, "ShopeeAuto3AEngine: hooked CameraMetadataNative.get(CaptureResult.Key)")
        } catch (t: Throwable) {
            Log.w(TAG, "ShopeeAuto3AEngine: CameraMetadataNative hook skipped: ${t.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                CaptureResult::class.java,
                "get",
                CaptureResult.Key::class.java,
                hook
            )
            Log.d(TAG, "ShopeeAuto3AEngine: hooked CaptureResult.get(CaptureResult.Key)")
        } catch (t: Throwable) {
            Log.w(TAG, "ShopeeAuto3AEngine: CaptureResult hook skipped: ${t.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                TotalCaptureResult::class.java,
                "get",
                CaptureResult.Key::class.java,
                hook
            )
            Log.d(TAG, "ShopeeAuto3AEngine: hooked TotalCaptureResult.get(CaptureResult.Key)")
        } catch (t: Throwable) {
            Log.w(TAG, "ShopeeAuto3AEngine: TotalCaptureResult hook skipped: ${t.message}")
        }

        isHooked = true
    }
}
