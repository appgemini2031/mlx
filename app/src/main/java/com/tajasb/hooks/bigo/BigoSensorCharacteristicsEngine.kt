package com.tajasb.hooks.bigo

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Log
import android.util.Size
import android.util.SizeF
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * BigoSensorCharacteristicsEngine:
 * Melindungi metadata karakteristik sensor fisik & orientasi kamera khusus untuk proses BIGO Live.
 * Menjaga SENSOR_ORIENTATION (270 untuk kamera depan, 90 untuk belakang jika null),
 * PHYSICAL_SIZE, PIXEL_ARRAY_SIZE, ACTIVE_ARRAY_SIZE, dan INFO_SUPPORTED_HARDWARE_LEVEL.
 * Menggunakan ThreadLocal re-entrancy guard agar aman dari infinite recursion.
 */
object BigoSensorCharacteristicsEngine {
    private const val TAG = "Tajasb-BIGO"

    @Volatile
    private var isHooked = false

    private val inHookGuard = ThreadLocal.withInitial { false }

    fun hookCharacteristics(classLoader: ClassLoader, getActiveCameraId: () -> String) {
        if (isHooked) return

        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (inHookGuard.get() == true) return
                inHookGuard.set(true)
                try {
                    val key = param.args?.getOrNull(0) ?: return
                    val keyName = try {
                        if (key is CameraCharacteristics.Key<*>) key.name ?: "" else key.toString()
                    } catch (_: Throwable) {
                        return
                    }
                    if (keyName.isBlank()) return

                    when {
                        keyName.contains("sensor.info.physicalSize", ignoreCase = true) -> {
                            if (param.result == null) {
                                param.result = SizeF(4.8f, 3.6f)
                            }
                        }
                        keyName.contains("sensor.info.pixelArraySize", ignoreCase = true) -> {
                            if (param.result == null) {
                                param.result = Size(3264, 2448)
                            }
                        }
                        keyName.contains("sensor.info.activeArraySize", ignoreCase = true) -> {
                            if (param.result == null) {
                                param.result = Rect(0, 0, 3264, 2448)
                            }
                        }
                        keyName.contains("sensor.orientation", ignoreCase = true) -> {
                            if (param.result == null) {
                                val camId = try { getActiveCameraId() } catch (_: Throwable) { "0" }
                                param.result = if (camId == "1") 270 else 90
                            }
                        }
                        keyName.contains("info.supportedHardwareLevel", ignoreCase = true) -> {
                            val existing = param.result as? Int
                            val isLowTier = existing == null ||
                                existing == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY ||
                                existing == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
                            if (isLowTier) {
                                val camId = try { getActiveCameraId() } catch (_: Throwable) { "0" }
                                param.result = if (camId == "1") {
                                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
                                } else {
                                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // Cegah crash jika OEM memiliki custom CameraCharacteristics key
                } finally {
                    inHookGuard.set(false)
                }
            }
        }

        try {
            XposedHelpers.findAndHookMethod(
                CameraCharacteristics::class.java,
                "get",
                CameraCharacteristics.Key::class.java,
                hook
            )
            Log.d(TAG, "BigoSensorCharacteristicsEngine: hooked CameraCharacteristics.get")
        } catch (t: Throwable) {
            Log.w(TAG, "BigoSensorCharacteristicsEngine: CameraCharacteristics hook skipped: ${t.message}")
        }

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
            Log.d(TAG, "BigoSensorCharacteristicsEngine: hooked CameraMetadataNative.get(CameraCharacteristics.Key)")
        } catch (t: Throwable) {
            Log.w(TAG, "BigoSensorCharacteristicsEngine: CameraMetadataNative hook skipped: ${t.message}")
        }

        isHooked = true
    }
}
