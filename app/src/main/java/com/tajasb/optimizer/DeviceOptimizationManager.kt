package com.tajasb.optimizer

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.tajasb.data.VcamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlin.math.roundToInt

enum class DeviceSpecTier(
    val title: String,
    val badge: String,
    val colorHex: String,
    val targetResolution: String,
    val targetFps: String,
    val recommendation: String
) {
    LOW(
        title = "Low Spec (Batas Minimal)",
        badge = "🥉 LOW",
        colorHex = "#F59E0B",
        targetResolution = "720p HD",
        targetFps = "24-30 FPS",
        recommendation = "Video 720p 30 FPS, tutup aplikasi lain, aktifkan Turbo Clean."
    ),
    MEDIUM(
        title = "Medium Spec (Sweet Spot)",
        badge = "🥈 MEDIUM",
        colorHex = "#38BDF8",
        targetResolution = "1080p FHD",
        targetFps = "30 FPS",
        recommendation = "Video 1080p 30 FPS, stabil berjam-jam, transisi mulus."
    ),
    MONSTER(
        title = "Monster Spec (Performa Sultan)",
        badge = "🥇 MONSTER",
        colorHex = "#10B981",
        targetResolution = "1080p/2K",
        targetFps = "60 FPS",
        recommendation = "Video 1080p/2K 60 FPS, zero throttle, tahan 24 jam non-stop."
    )
}

data class DeviceMemoryStats(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val usedRamMb: Long,
    val percentFree: Int,
    val percentUsed: Int,
    val isLowRam: Boolean,
    val statusLabel: String,
    val batteryTempC: Float,
    val isVpnActive: Boolean,
    val fpsLimit: Int,
    val networkSpeedMbps: Int = 30,
    val networkTypeLabel: String = "Online"
) {
    val tier: DeviceSpecTier
        get() = when {
            totalRamMb >= 10000L -> DeviceSpecTier.MONSTER // 12GB+ RAM
            totalRamMb >= 5000L -> DeviceSpecTier.MEDIUM   // 6GB - 8GB RAM
            else -> DeviceSpecTier.LOW                     // <= 4GB RAM (e.g. 3GB, 4GB)
        }
}

data class CleanResult(
    val freedMb: Long,
    val availableMb: Long,
    val totalMb: Long,
    val killedProcessesCount: Int
)

data class LiveReadinessReport(
    val isAllReady: Boolean,
    val readyScore: Int,
    val healedFilesCount: Int,
    val freedRamMb: Long,
    val summaryTitle: String,
    val details: List<String>,
    val timestampMs: Long = System.currentTimeMillis()
)

object DeviceOptimizationManager {
    private const val TAG = "DeviceOptimizationMgr"

    private val _statsFlow = MutableStateFlow<DeviceMemoryStats?>(null)
    val statsFlow: StateFlow<DeviceMemoryStats?> = _statsFlow.asStateFlow()

    private var autoGuardJob: Job? = null
    @Volatile private var lastPeriodicCleanMs: Long = System.currentTimeMillis()
    @Volatile private var lastThermalMitigationMs: Long = 0L

    @Suppress("DEPRECATION")
    fun getDynamicNetworkBandwidth(context: Context): Pair<Int, String> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return Pair(30, "Online")
            val network = cm.activeNetwork ?: return Pair(0, "Offline")
            val caps = cm.getNetworkCapabilities(network) ?: return Pair(0, "Offline")

            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

            val downstreamKbps = caps.linkDownstreamBandwidthKbps
            var measuredMbps = if (downstreamKbps > 0) (downstreamKbps / 1000f).roundToInt() else 0

            val typeStr = when {
                isWifi -> "Wi-Fi"
                isCellular -> "Seluler"
                else -> "Online"
            }

            if (isWifi) {
                try {
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val linkSpeed = wm?.connectionInfo?.linkSpeed ?: 0
                    if (linkSpeed > 0 && (measuredMbps <= 0 || measuredMbps > 1000)) {
                        measuredMbps = linkSpeed
                    }
                } catch (_: Exception) {}
            }

            val finalMbps = if (measuredMbps > 0) measuredMbps.coerceIn(5, 500) else 30
            Pair(finalMbps, typeStr)
        } catch (_: Exception) {
            Pair(30, "Online")
        }
    }

    fun getMemoryStats(context: Context): DeviceMemoryStats {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = (totalMb - availMb).coerceAtLeast(0)
        val percentFree = if (totalMb > 0) ((availMb.toDouble() / totalMb.toDouble()) * 100).toInt() else 0
        val percentUsed = (100 - percentFree).coerceIn(0, 100)

        val statusLabel = when {
            availMb < 350 || memInfo.lowMemory -> "Kritis (< 350 MB)"
            availMb < 700 -> "Waspada"
            percentFree >= 40 -> "Sangat Lega"
            else -> "Optimal"
        }

        val batteryTemp = getBatteryTemperature(context)
        val vpnActive = LiveFocusVpnService.isVpnActive

        val fps = try {
            VcamRepository.getInstance(context).config.value.fpsGovernorLimit
        } catch (_: Exception) {
            30
        }

        val (netSpeed, netType) = getDynamicNetworkBandwidth(context)

        val stats = DeviceMemoryStats(
            totalRamMb = totalMb,
            availableRamMb = availMb,
            usedRamMb = usedMb,
            percentFree = percentFree,
            percentUsed = percentUsed,
            isLowRam = memInfo.lowMemory || availMb < 400,
            statusLabel = statusLabel,
            batteryTempC = batteryTemp,
            isVpnActive = vpnActive,
            fpsLimit = fps,
            networkSpeedMbps = netSpeed,
            networkTypeLabel = netType
        )

        _statsFlow.value = stats
        return stats
    }

    fun getBatteryTemperature(context: Context): Float {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            temp / 10.0f
        } catch (_: Exception) {
            0.0f
        }
    }

    suspend fun triggerTurboClean(context: Context): CleanResult = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memBefore = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memBefore)
        val availBeforeMb = memBefore.availMem / (1024 * 1024)

        val pm = context.packageManager
        val myPkg = context.packageName

        // Whitelist packages proteksi dari pilihan pengguna (bukan hardcode):
        val repo = try { VcamRepository.getInstance(context) } catch (_: Exception) { null }
        val userWhitelist = repo?.config?.value?.ramCleanWhitelistPackages ?: emptySet()
        val targetApps = repo?.config?.value?.targetPackages ?: emptySet()

        // Proteksi esensial mutlak agar VCAM dan UI sistem Android tidak force close:
        val essentialSystemPkgs = setOf(
            myPkg,
            "com.android.systemui"
        )

        // Jika pengguna sudah memilih whitelist sendiri, 100% gunakan pilihan pengguna!
        // Jika belum memilih, gunakan target aplikasi atau default live apps sebagai fallback awal:
        val protectedPkgs = if (userWhitelist.isNotEmpty()) {
            essentialSystemPkgs + userWhitelist
        } else if (targetApps.isNotEmpty()) {
            essentialSystemPkgs + targetApps
        } else {
            essentialSystemPkgs + setOf(
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.ss.android.ugc.aweme",
                "com.shopee.id"
            )
        }

        var killedCount = 0

        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                val pkgName = app.packageName
                if (pkgName in protectedPkgs) continue
                // Don't kill core system apps
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem && !pkgName.contains("browser") && !pkgName.contains("media")) {
                    continue
                }

                try {
                    am.killBackgroundProcesses(pkgName)
                    killedCount++
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning background packages: ${e.message}")
        }

        // Clean up temporary upload files and stale zip exports in cache
        try {
            val exportDir = java.io.File(context.cacheDir, "backup_exports")
            if (exportDir.exists()) {
                val now = System.currentTimeMillis()
                exportDir.listFiles()?.forEach { f ->
                    if (f.isFile && (now - f.lastModified() > 15 * 60 * 1000L)) {
                        try { f.delete() } catch (_: Exception) {}
                    }
                }
            }
            val vaultDir = repo?.getInternalCamera1Directory()
            vaultDir?.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("temp_upload_")) {
                    try { f.delete() } catch (_: Exception) {}
                }
            }
            repo?.cleanGalleryDuplicates()
        } catch (_: Exception) {}

        // Trigger internal garbage collection
        System.gc()
        kotlinx.coroutines.delay(180)

        val memAfter = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memAfter)
        val availAfterMb = memAfter.availMem / (1024 * 1024)

        val actualFreed = (availAfterMb - availBeforeMb).coerceAtLeast(0)

        val result = CleanResult(
            freedMb = actualFreed,
            availableMb = availAfterMb,
            totalMb = memAfter.totalMem / (1024 * 1024),
            killedProcessesCount = killedCount
        )

        try {
            val r = VcamRepository.getInstance(context)
            r.updateConfig {
                it.copy(
                    lastFreedRamMb = actualFreed,
                    lastRamCleanTimestamp = System.currentTimeMillis()
                )
            }
        } catch (_: Exception) {}

        getMemoryStats(context)
        Log.i(TAG, "Turbo RAM Clean complete: freed $actualFreed MB, killed $killedCount background processes.")
        result
    }

    /**
     * Diagnostik Kesiapan Live 24 Jam (Pre-Flight Live Readiness & Self-Heal):
     * Memeriksa 6 aspek fundamental sebelum/saat Live dan otomatis memperbaiki masalah yang ditemukan.
     */
    suspend fun runLiveReadinessDiagnostic(
        context: Context,
        autoFix: Boolean = true
    ): LiveReadinessReport = withContext(Dispatchers.IO) {
        val repo = VcamRepository.getInstance(context)
        var cfg = repo.config.value
        val details = mutableListOf<String>()
        var score = 0
        var healedCount = 0
        var freedMb = 0L

        // 1. Pemeriksaan Daftar Putar & Master Switch
        val validVideos = cfg.playlist.filter { item ->
            val f = java.io.File(item.path)
            item.isEnabled && f.exists() && f.length() > 0L
        }
        if (validVideos.isNotEmpty()) {
            score += 20
            details.add("✅ Daftar Putar: ${validVideos.size} video aktif siap diputar")
            if (!cfg.isMasterEnabled && autoFix) {
                repo.updateConfig { it.copy(isMasterEnabled = true) }
                repo.broadcastControlEvent(action = "UPDATE_CONFIG", isMasterEnabled = true)
                cfg = repo.config.value
                healedCount++
                details.add("🔧 Auto-Fix: Saklar Utama Virtual Camera diaktifkan otomatis")
            } else if (cfg.isMasterEnabled) {
                score += 15
            }
        } else {
            details.add("⚠️ Daftar Putar: Belum ada video aktif yang dipilih di antrean")
        }

        // 2. Pemeriksaan & Auto-Heal Integritas File Hook (Internal Vault & Pembersihan Duplikat Galeri)
        try {
            if (autoFix) {
                repo.cleanGalleryDuplicates()
            }
            val healed = com.tajasb.data.MediaFileStorageHelper.verifyAndHealHookFiles(
                config = cfg,
                camera1Dir = repo.getCamera1Directory(),
                internalCamera1Dir = repo.getInternalCamera1Directory()
            )
            healedCount += healed
            if (autoFix && cfg.playlist.isNotEmpty()) {
                repo.writeHookConfigFiles(cfg)
                repo.syncToTargetApps(cfg.targetPackages)
            }
            score += 20
            if (healed > 0) {
                details.add("🔧 Self-Heal Hook: $healed berkas konfigurasi/media dipulihkan otomatis")
            } else {
                details.add("✅ Sinkronisasi Hook: Berkas vcam_config.json & media internal 100% utuh (Bebas Duplikat Galeri)")
            }
        } catch (e: Exception) {
            details.add("⚠️ Sinkronisasi Hook: Gagal memeriksa direktori (${e.message})")
        }

        // 3. Pemeriksaan Memori RAM & Pembersihan Otomatis
        var stats = getMemoryStats(context)
        if (stats.availableRamMb < 450L && autoFix) {
            val cleanRes = triggerTurboClean(context)
            freedMb = cleanRes.freedMb
            stats = getMemoryStats(context)
            score += 15
            details.add("⚡ Optimasi RAM: Dibersihkan otomatis (+${cleanRes.freedMb} MB, Tersedia ${stats.availableRamMb} MB)")
        } else if (stats.availableRamMb >= 450L) {
            score += 15
            details.add("✅ Memori RAM: Lega (${stats.availableRamMb} MB bebas / ${stats.percentFree}%)")
        } else {
            details.add("⚠️ Memori RAM: Sempit (${stats.availableRamMb} MB bebas)")
        }

        // 4. Pemeriksaan Suhu Baterai & Proteksi Termal
        if (stats.batteryTempC >= 41.5f) {
            if (autoFix && !cfg.isLightweightMode) {
                repo.toggleLightweightMode(true)
                healedCount++
                details.add("🌡️ Suhu ${stats.batteryTempC}°C: Mode Ringan diaktifkan otomatis untuk mencegah overheat")
            } else {
                details.add("⚠️ Suhu Perangkat: ${stats.batteryTempC}°C (Disarankan gunakan kipas pendingin)")
            }
            score += 10
        } else {
            score += 15
            val tempDisp = if (stats.batteryTempC > 0f) "${stats.batteryTempC}°C" else "Normal"
            details.add("✅ Suhu Perangkat: Aman ($tempDisp)")
        }

        // 5. Pemeriksaan Codec Guard & Auto-Rocket Anti-Freeze
        if (!cfg.isSafeCodecGuardActive && autoFix) {
            repo.toggleSafeCodecGuard(true)
            healedCount++
            details.add("🔧 Auto-Fix: Safe Codec Guard diaktifkan untuk mencegah crash dekoder")
        } else {
            details.add("✅ Proteksi Codec & Stabilitas: Aktif (Batas ${cfg.fpsGovernorLimit} FPS)")
        }
        score += 15

        val finalScore = score.coerceIn(0, 100)
        val isReady = validVideos.isNotEmpty() && finalScore >= 75
        val title = when {
            isReady && healedCount > 0 -> "🛡️ Siap Live 24 Jam! ($healedCount titik dipulihkan otomatis • Skor $finalScore%)"
            isReady -> "✅ Sistem 100% Siap Live 24 Jam Non-Stop (Skor $finalScore%)"
            else -> "⚠️ Perlu Perhatian Sebelum Live (Skor Kesiapan $finalScore%)"
        }

        LiveReadinessReport(
            isAllReady = isReady,
            readyScore = finalScore,
            healedFilesCount = healedCount,
            freedRamMb = freedMb,
            summaryTitle = title,
            details = details
        )
    }

    fun startAutoRamGuard(context: Context, scope: CoroutineScope) {
        if (autoGuardJob != null && autoGuardJob?.isActive == true) return
        autoGuardJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(45_000) // Siklus Watchdog setiap 45 detik
                try {
                    val repo = VcamRepository.getInstance(context)
                    val cfg = repo.config.value
                    val stats = getMemoryStats(context)
                    val now = System.currentTimeMillis()

                    // 1. Low-RAM Guard (< 380 MB)
                    var cleanedThisCycle = false
                    if (cfg.isAutoRamCleanEnabled && stats.availableRamMb < 380) {
                        Log.w(TAG, "Auto RAM Guard triggered: Available RAM ${stats.availableRamMb} MB is low (< 380 MB). Cleaning...")
                        triggerTurboClean(context)
                        lastPeriodicCleanMs = now
                        cleanedThisCycle = true
                    }

                    // 2. Periodic RAM Clean terjadwal sesuai interval menit pengguna (misal 30 menit)
                    if (!cleanedThisCycle && cfg.isPeriodicRamCleanEnabled) {
                        val intervalMs = (cfg.periodicRamCleanIntervalMinutes.coerceAtLeast(5)) * 60_000L
                        if (now - lastPeriodicCleanMs >= intervalMs) {
                            Log.i(TAG, "Periodic RAM Clean triggered (${cfg.periodicRamCleanIntervalMinutes}m interval).")
                            triggerTurboClean(context)
                            lastPeriodicCleanMs = now
                            cleanedThisCycle = true
                        }
                    }

                    // 3. Smart Thermal Watchdog (> 41.5°C dengan cooldown 5 menit)
                    if (stats.batteryTempC >= 41.5f && (now - lastThermalMitigationMs >= 5 * 60_000L)) {
                        lastThermalMitigationMs = now
                        Log.w(TAG, "Thermal Guard triggered: Battery temp ${stats.batteryTempC}°C >= 41.5°C.")
                        if (!cfg.isLightweightMode) {
                            repo.toggleLightweightMode(true)
                        }
                        if (!cleanedThisCycle) {
                            triggerTurboClean(context)
                        }
                    }

                    // 4. Self-Healing Hook File Integrity Guard (memastikan vcam_config.json & virtual.mp4 tidak terhapus OS)
                    if (cfg.isMasterEnabled && cfg.playlist.isNotEmpty()) {
                        com.tajasb.data.MediaFileStorageHelper.verifyAndHealHookFiles(
                            config = cfg,
                            camera1Dir = repo.getCamera1Directory(),
                            internalCamera1Dir = repo.getInternalCamera1Directory()
                        )
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Watchdog cycle error: ${e.message}")
                }
            }
        }
    }

    fun stopAutoRamGuard() {
        autoGuardJob?.cancel()
        autoGuardJob = null
    }
}
