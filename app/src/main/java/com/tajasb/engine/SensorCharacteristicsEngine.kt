package com.tajasb.engine

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Log
import android.util.Size
import android.util.SizeF
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * SensorCharacteristicsEngine:
 * Protects virtual camera from anti-tamper / emulator audits by spoofing genuine
 * physical camera sensor hardware characteristics (matching the real Xiaomi 2310FPCA4G device):
 * - SENSOR_INFO_PHYSICAL_SIZE (4.8mm x 3.6mm Sony IMX/Samsung ISOCELL profile)
 * - SENSOR_INFO_PIXEL_ARRAY_SIZE (3264 x 2448)
 * - INFO_SUPPORTED_HARDWARE_LEVEL (FULL for Back Camera ID 0, LEVEL_3 for Front Camera ID 1)
 */
object SensorCharacteristicsEngine {

    private const val TAG = "SensorCharEngine"
    private var isHooked = false

    fun hookCharacteristics(classLoader: ClassLoader, getActiveCameraId: () -> String) {
        if (isHooked) return

        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val key = param.args.getOrNull(0) ?: return
                val keyName = try {
                    if (key is CameraCharacteristics.Key<*>) key.name else key.toString()
                } catch (_: Throwable) {
                    key.toString()
                }

                when {
                    keyName.contains("sensor.info.physicalSize", ignoreCase = true) ||
                    key == CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE -> {
                        // If device doesn't report physical size, supply valid fallback (4.8f x 3.6f)
                        if (param.result == null) {
                            param.result = SizeF(4.8f, 3.6f)
                        }
                    }
                    keyName.contains("sensor.info.pixelArraySize", ignoreCase = true) ||
                    key == CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE -> {
                        if (param.result == null) {
                            param.result = Size(3264, 2448)
                        }
                    }
                    keyName.contains("sensor.info.activeArraySize", ignoreCase = true) ||
                    key == CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE -> {
                        if (param.result == null) {
                            param.result = Rect(0, 0, 3264, 2448)
                        }
                    }
                    keyName.contains("info.supportedHardwareLevel", ignoreCase = true) ||
                    key == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL -> {
                        // Ensure hardware level is at least FULL / LEVEL_3 on any device brand
                        val existing = param.result as? Int
                        val isLowTier = existing == null ||
                                existing == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY ||
                                existing == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
                        if (isLowTier) {
                            val camId = getActiveCameraId()
                            param.result = if (camId == "1") {
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
                            } else {
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                            }
                        }
                    }
                }
            }
        }

        // 1. Hook CameraCharacteristics.get(Key)
        try {
            XposedHelpers.findAndHookMethod(
                CameraCharacteristics::class.java,
                "get",
                CameraCharacteristics.Key::class.java,
                hook
            )
            Log.i(TAG, "Hooked CameraCharacteristics.get(CameraCharacteristics.Key)")
        } catch (t: Throwable) {
            Log.w(TAG, "CameraCharacteristics hook skipped: ${t.message}")
        }

        // 2. Hook CameraMetadataNative.get(CameraCharacteristics.Key)
        try {
            val metadataNativeClass = XposedHelpers.findClass(
                "android.hardware.camera2.impl.CameraMetadataNative",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                metadataNativeClass,
                "get",
                CameraCharacteristics.Key::class.java,
                hook
            )
            Log.i(TAG, "Hooked CameraMetadataNative.get(CameraCharacteristics.Key)")
        } catch (t: Throwable) {
            Log.w(TAG, "CameraMetadataNative characteristics hook skipped: ${t.message}")
        }

        isHooked = true
    }
}
