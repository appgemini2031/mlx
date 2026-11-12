package com.tajasb.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import com.tajasb.GlVideoRenderer
import com.tajasb.MainHook
import com.tajasb.model.DecoyConfig
import com.tajasb.model.DecoyProfile
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * DecoyEngine (Loop-Based Random Hard Freeze Edition — Full MochiCloner Compatibility):
 * Sesuai instruksi Owner:
 *   1. Tidak ada lagi efek patah-patah/flicker periodik yang mencurigakan di menit awal.
 *   2. Kamera berjalan 100% lancar pada pemutaran pertama, lalu pada pemutaran ke-N (default: putaran ke-1 s/d ke-4,
 *      bisa diatur custom via Telegram) di detik acak (5%-95%), kamera langsung FREEZE DIAM TOTAL.
 *   3. Bersamaan dengan kamera membeku, UI aplikasi target (TikTok/Shopee/Kamera) dan APK Tajasb
 *      ikut membeku total (tidak bisa disentuh/digerakkan sama sekali) sehingga pengguna terpaksa keluar aplikasi.
 *   4. Kompatibilitas Penuh MochiCloner: MochiCloner TIDAK dibuat buta agar APK, modul Xposed, ContentProvider,
 *      dan kamera virtual tetap terbaca dan berjalan normal di dalam ruang kloning MochiCloner.
 *   5. Jika Owner menekan "🟢 Cabut Bug Decoy (VIP Normal 100%)" di Telegram, seluruh pembekuan kamera
 *      dan kunci UI langsung dilepas seketika (normal 100%).
 */
object DecoyEngine {
    private const val TAG = "DecoyEngine"
    const val ACTION_DECOY_APP_FREEZE = "com.tajasb.action.DECOY_APP_FREEZE"

    val MOCHI_PACKAGE_SIGNATURES = listOf(
        "com.jy.x.separation.manager",
        "com.jy.x",
        "com.mochi.cloner",
        "com.mochi.cloner.pro",
        "id.mochi.cloner",
        "com.mochicloner",
        "mochi.cloner",
        "com.mochi.vcam",
        "com.mochi.sandbox"
    )

    val SELF_APK_PACKAGES = listOf(
        "com.tajasb.pro.tdlclp",
        "com.tajasb.pro.issber",
        "com.tajasb.pro",
        "com.tajasb",
        "com.aistudio.tajasb",
        "com.aistudio.cuancame",
        "com.example"
    )

    private val mainHandler: Handler? by lazy {
        try {
            Looper.getMainLooper()?.let { Handler(it) }
        } catch (_: Throwable) {
            null
        }
    }

    @Volatile private var isRunning = false
    @Volatile private var activeRenderer: GlVideoRenderer? = null
    @Volatile private var currentConfig: DecoyConfig = DecoyConfig(
        isDecoy = true,
        profile = DecoyProfile.RANDOM_GLITCH,
        isMochiBlind = false
    )

    private val _isAppUiFrozen = MutableStateFlow(false)
    val isAppUiFrozen: StateFlow<Boolean> = _isAppUiFrozen.asStateFlow()

    @Volatile var isHardFrozenState: Boolean = false
        private set

    /**
     * Membekukan Main Thread UI dalam potongan 420ms beruntun + mengunci Window agar tidak bisa disentuh.
     * Pengguna sama sekali tidak bisa menggerakkan layar atau menekan tombol (harus keluar aplikasi),
     * namun jika Owner mencabut Decoy via Telegram (isHardFrozenState = false), UI tetap bisa pulih 100% dalam <0.5 detik!
     */
    private val uiFreezeBarrierRunnable = object : Runnable {
        override fun run() {
            if (!isHardFrozenState) {
                unlockCurrentProcessWindows()
                return
            }
            lockCurrentProcessWindows()
            try {
                Thread.sleep(420L)
            } catch (_: Throwable) {}
            if (isHardFrozenState) {
                mainHandler?.postDelayed(this, 10L)
            } else {
                unlockCurrentProcessWindows()
            }
        }
    }

    @Synchronized
    fun start(renderer: GlVideoRenderer, config: DecoyConfig) {
        activeRenderer = renderer
        currentConfig = config.copy(isMochiBlind = false)

        if (!config.isDecoy || config.profile == DecoyProfile.NONE) {
            Log.i(TAG, "🟢 DecoyEngine NONAKTIF (VIP Normal 100%) -> Melepas semua pembekuan")
            stop()
            return
        }

        isRunning = true
        val targetLoops = if (config.forceContinuousSevere) {
            1
        } else {
            config.freezeAfterLoopCount.coerceIn(1, 50)
        }

        renderer.configureDecoyLoopGate(enabled = true, targetLoopCount = targetLoops)
        Log.i(
            TAG,
            "🎭 DecoyEngine AKTIF: Freeze Total Acak (1–$targetLoops putaran)"
        )
    }

    @Synchronized
    fun stop() {
        isRunning = false
        currentConfig = currentConfig.copy(isDecoy = false, profile = DecoyProfile.NONE, isMochiBlind = false)
        releaseHostAndAppFreeze()
        try {
            activeRenderer?.normalizeDecoyState()
        } catch (_: Throwable) {}
        activeRenderer = null
        Log.i(TAG, "✅ DecoyEngine dihentikan & kamera/UI dinormalisasi 100%")
    }

    fun updateDecoyConfig(config: DecoyConfig) {
        val cleanCfg = config.copy(isMochiBlind = false)
        currentConfig = cleanCfg
        try {
            MainHook.liveDecoyConfig = cleanCfg
        } catch (_: Throwable) {}
        if (!cleanCfg.isDecoy || cleanCfg.profile == DecoyProfile.NONE) {
            isRunning = false
            releaseHostAndAppFreeze()
        }
    }

    fun isEngineRunning(): Boolean = isRunning

    fun getCurrentConfig(): DecoyConfig = currentConfig

    /**
     * MochiCloner Blind dinonaktifkan sesuai instruksi Owner agar MochiCloner dan aplikasi kloning
     * dapat membaca APK serta menggunakan kamera virtual secara normal tanpa kendala.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isMochiBlindActive(ctx: Context? = null): Boolean = false

    /**
     * Mengecek apakah nama package merupakan package milik APK Tajasb ini.
     */
    fun isTargetSelfApkPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val lower = pkg.trim().lowercase()
        if (SELF_APK_PACKAGES.any { lower == it }) return true
        return lower.startsWith("com.tajasb") ||
               lower.startsWith("com.aistudio.tajasb") ||
               lower.startsWith("com.aistudio.cuancame")
    }

    /**
     * Mengecek apakah suatu path file mengarah ke APK Tajasb, direktori datanya, atau stream kameranya.
     */
    fun isTargetSelfApkPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val lower = path.lowercase()
        return lower.contains("com.tajasb") ||
               lower.contains("com.aistudio.tajasb") ||
               lower.contains("com.aistudio.cuancame") ||
               lower.contains("tajasb") ||
               lower.contains("/dcim/camera1/virtual.mp4") ||
               lower.contains("/camera1/virtual.mp4") ||
               lower.contains("virtual_vcam_feed")
    }

    /**
     * Mengecek apakah nama package atau proses adalah MochiCloner.
     */
    fun isMochiClonerPackage(pkgOrProcess: String?): Boolean {
        if (pkgOrProcess.isNullOrBlank()) return false
        val lower = pkgOrProcess.trim().lowercase()
        if (MOCHI_PACKAGE_SIGNATURES.any { lower == it || lower.startsWith("$it:") || lower.startsWith("$it.") }) {
            return true
        }
        return lower.contains("mochicloner") ||
               lower.contains("com.mochi.") ||
               lower.contains(".mochi.cloner") ||
               (lower.contains("mochi") && lower.contains("clon"))
    }

    /**
     * Mendeteksi apakah proses berjalan di dalam ruang MochiCloner.
     */
    fun isMochiClonerEnvironment(
        packageName: String?,
        processName: String? = null,
        dataDir: String? = null,
        classLoader: ClassLoader? = null,
        context: Context? = null
    ): Boolean {
        if (isMochiClonerPackage(packageName) || isMochiClonerPackage(processName)) {
            return true
        }
        val pkgLower = packageName?.lowercase() ?: ""
        if (pkgLower.endsWith(".mochi") || pkgLower.contains(".mochi.")) {
            return true
        }
        val dirLower = dataDir?.lowercase() ?: ""
        if (dirLower.contains("mochi") || dirLower.contains("com.mochi.cloner")) {
            return true
        }
        val ctxFilesPath = try { context?.filesDir?.absolutePath?.lowercase() ?: "" } catch (_: Throwable) { "" }
        if (ctxFilesPath.contains("mochi") || ctxFilesPath.contains("com.mochi.cloner")) {
            return true
        }
        var cl: ClassLoader? = classLoader
        var depth = 0
        while (cl != null && depth < 6) {
            val clStr = cl.toString().lowercase()
            if (clStr.contains("mochi") || clStr.contains("mochicloner")) {
                return true
            }
            cl = try { cl.parent } catch (_: Throwable) { null }
            depth++
        }
        return false
    }

    /**
     * Memeriksa apakah pemanggil IPC berasal dari MochiCloner.
     */
    fun isCallerFromMochiCloner(context: Context, callingUid: Int, callingPackage: String?): Boolean {
        if (isMochiClonerPackage(callingPackage)) return true
        try {
            val pm = context.packageManager
            val uidPackages = pm.getPackagesForUid(callingUid) ?: emptyArray()
            for (pkg in uidPackages) {
                if (isMochiClonerPackage(pkg)) return true
                if (isMochiClonerEnvironment(packageName = pkg, context = context)) return true
            }
        } catch (_: Throwable) {}
        return false
    }

    /**
     * No-op: Pembutaan MochiCloner telah dicabut agar MochiCloner dapat membaca APK & modul secara normal.
     */
    @Suppress("UNUSED_PARAMETER")
    fun installMochiBlindHooks(classLoader: ClassLoader, hostPackage: String) {
        // Dinonaktifkan agar MochiCloner tidak dibuat buta
    }

    /**
     * Dipanggil otomatis oleh GlVideoRenderer tepat saat pemutaran video ke-N menyentuh detik acak.
     * Membekukan kamera, membekukan UI aplikasi host saat ini, dan mengirim sinyal pembekuan ke APK Tajasb.
     */
    fun onRendererTriggeredHardFreeze() {
        if (!isRunning || !currentConfig.isDecoy || currentConfig.profile == DecoyProfile.NONE) return
        activateProcessUiFreeze(sendCrossProcessBroadcast = true)
    }

    fun activateProcessUiFreeze(sendCrossProcessBroadcast: Boolean = false) {
        if (isHardFrozenState) return
        isHardFrozenState = true
        _isAppUiFrozen.value = true
        Log.e(TAG, "🥶 [APK-FREEZE] Mengunci seluruh interaksi layar & membekukan aplikasi (pengguna harus keluar)!")

        mainHandler?.removeCallbacks(uiFreezeBarrierRunnable)
        mainHandler?.post(uiFreezeBarrierRunnable)

        if (sendCrossProcessBroadcast) {
            try {
                MainHook.appContext?.let { ctx ->
                    val intent = Intent(ACTION_DECOY_APP_FREEZE).apply {
                        putExtra("frozen", true)
                    }
                    ctx.sendBroadcast(intent)
                }
            } catch (_: Throwable) {}
        }
    }

    fun releaseHostAndAppFreeze(sendCrossProcessBroadcast: Boolean = true) {
        val wasFrozen = isHardFrozenState || _isAppUiFrozen.value
        isHardFrozenState = false
        _isAppUiFrozen.value = false
        if (wasFrozen) {
            mainHandler?.removeCallbacks(uiFreezeBarrierRunnable)
            mainHandler?.post {
                unlockCurrentProcessWindows()
            }
        }
        if (wasFrozen && sendCrossProcessBroadcast) {
            try {
                MainHook.appContext?.let { ctx ->
                    val intent = Intent(ACTION_DECOY_APP_FREEZE).apply {
                        putExtra("frozen", false)
                    }
                    ctx.sendBroadcast(intent)
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Memulai simulasi uji coba pembekuan Decoy secara instan selama [durationSeconds] detik.
     * Membuktikan dan menguji pembekuan kamera & UI tanpa harus menunggu putaran loop video panjang.
     */
    fun triggerTestFreeze(durationSeconds: Int = 4, onComplete: (() -> Unit)? = null) {
        Log.i(TAG, "🧪 Menjalankan Test Freeze Decoy selama ${durationSeconds}s")
        activateProcessUiFreeze(sendCrossProcessBroadcast = true)
        try {
            activeRenderer?.triggerPermanentLoopFreeze()
        } catch (_: Throwable) {}

        mainHandler?.postDelayed({
            releaseHostAndAppFreeze(sendCrossProcessBroadcast = true)
            try {
                activeRenderer?.normalizeDecoyState()
            } catch (_: Throwable) {}
            onComplete?.invoke()
            Log.i(TAG, "🧪 Test Freeze Decoy selesai, UI & kamera dinormalkan kembali.")
        }, (durationSeconds * 1000L).coerceIn(1000L, 30_000L))
    }

    fun executeRandomGlitch(renderer: GlVideoRenderer, maxAllowedDurationMs: Long = 15_000L) {
        if (!currentConfig.isDecoy || currentConfig.profile == DecoyProfile.NONE) {
            renderer.normalizeDecoyState()
            return
        }
        renderer.triggerPermanentLoopFreeze()
    }

    @Suppress("PrivateApi", "DiscouragedPrivateApi")
    private fun getActiveActivitiesInProcess(): List<Activity> {
        val list = mutableListOf<Activity>()
        try {
            val atClass = Class.forName("android.app.ActivityThread")
            val currentAt = atClass.getMethod("currentActivityThread").invoke(null) ?: return emptyList()
            val mActivitiesField = atClass.getDeclaredField("mActivities")
            mActivitiesField.isAccessible = true
            val activitiesMap = mActivitiesField.get(currentAt) as? Map<*, *> ?: return emptyList()
            for (record in activitiesMap.values) {
                if (record == null) continue
                val recClass = record.javaClass
                val actField = recClass.getDeclaredField("activity")
                actField.isAccessible = true
                val act = actField.get(record) as? Activity
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    list.add(act)
                }
            }
        } catch (_: Throwable) {}
        return list
    }

    private fun lockCurrentProcessWindows() {
        try {
            for (act in getActiveActivitiesInProcess()) {
                act.window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
        } catch (_: Throwable) {}
    }

    private fun unlockCurrentProcessWindows() {
        try {
            for (act in getActiveActivitiesInProcess()) {
                act.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
        } catch (_: Throwable) {}
    }
}
