package com.tajasb.hooks.shopee

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Log
import android.util.Size
import android.util.SizeF
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.ConcurrentHashMap

/**
 * ShopeeSensorCharacteristicsEngine:
 * Melindungi metadata karakteristik sensor fisik & orientasi kamera khusus proses Shopee,
 * sekaligus mencatat key CameraCharacteristics unik (`CharKey=...`) agar SDK Live/Video Shopee mudah diaudit.
 */
object ShopeeSensorCharacteristicsEngine {
    private const val TAG = "Tajasb-Shopee"

    @Volatile
    private var isHooked = false

    private val inHookGuard = ThreadLocal.withInitial { false }
    private val loggedCharKeys = ConcurrentHashMap.newKeySet<String>()

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

                    if (loggedCharKeys.size < 40 && loggedCharKeys.add(keyName)) {
                        Log.d(TAG, "CharKey=$keyName")
                    }

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
            Log.d(TAG, "ShopeeSensorCharacteristicsEngine: hooked CameraCharacteristics.get")
        } catch (t: Throwable) {
            Log.w(TAG, "ShopeeSensorCharacteristicsEngine: CameraCharacteristics hook skipped: ${t.message}")
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
            Log.d(TAG, "ShopeeSensorCharacteristicsEngine: hooked CameraMetadataNative.get(CameraCharacteristics.Key)")
        } catch (t: Throwable) {
            Log.w(TAG, "ShopeeSensorCharacteristicsEngine: CameraMetadataNative hook skipped: ${t.message}")
        }

        isHooked = true
    }
}
