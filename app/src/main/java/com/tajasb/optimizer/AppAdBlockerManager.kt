package com.tajasb.optimizer

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File

/**
 * AppAdBlockerManager:
 * Modul pemutus akses data & pembeku aplikasi iklan paksaan saat live streaming.
 *
 * Menerapkan proteksi berlapis:
 * 1. Blackhole Network Firewall (VpnService): Mengarahkan paket data aplikasi iklan ke blackhole (0 KB/s).
 * 2. OS-Level NetPolicy & Iptables (Root/Shell): Memblokir socket keluar (DROP) di tingkat kernel Linux.
 * 3. Force-Stop & Suspend: Menghentikan proses iklan paksaan seketika dan mencegahnya bangun saat live.
 * 4. Overlay Permission Revocation Shortcut: Membuka pengaturan izin pop-up/overlay agar iklan tidak bisa tampil di atas layar.
 */
object AppAdBlockerManager {

    private const val TAG = "AppAdBlockerManager"

    @Volatile
    private var cachedRootAvailable: Boolean? = null

    fun isRootAvailable(): Boolean {
        cachedRootAvailable?.let { return it }
        val rootPaths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/su/bin/su"
        )
        val exists = rootPaths.any { File(it).exists() } || try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exit = process.waitFor()
            exit == 0
        } catch (_: Throwable) {
            false
        }
        cachedRootAvailable = exists
        return exists
    }

    suspend fun runSuCommand(vararg commands: String): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext false
        try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                for (cmd in commands) {
                    os.writeBytes("$cmd\n")
                }
                os.writeBytes("exit\n")
                os.flush()
            }
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menjalankan perintah su: ${e.message}")
            false
        }
    }

    suspend fun blockAppNetworkAndFreeze(context: Context, packageName: String): String = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val appInfo: ApplicationInfo? = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (_: Throwable) {
            null
        }

        val uid = appInfo?.uid ?: -1
        var rootApplied = false

        if (uid > 0 && isRootAvailable()) {
            val success = runSuCommand(
                "cmd netpolicy add restrict-background-blacklist $uid",
                "iptables -I OUTPUT -m owner --uid-owner $uid -j DROP",
                "ip6tables -I OUTPUT -m owner --uid-owner $uid -j DROP",
                "am force-stop $packageName",
                "pm suspend $packageName"
            )
            rootApplied = success
            if (success) {
                Log.i(TAG, "🛡️ [ROOT-BLOCK] Kernel iptables DROP & netpolicy blacklist diterapkan untuk $packageName (UID=$uid)")
            }
        }

        // Kill background process via ActivityManager (Non-Root fallback)
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(packageName)
        } catch (_: Throwable) {}

        if (rootApplied) {
            "Data diputus via Firewall Kernel & VPN Blackhole, proses iklan dibekukan."
        } else {
            "Data diputus via VPN Blackhole (0 KB/s). Proses latar belakang dihentikan."
        }
    }

    suspend fun unblockAppNetworkAndUnfreeze(context: Context, packageName: String): String = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val appInfo: ApplicationInfo? = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (_: Throwable) {
            null
        }

        val uid = appInfo?.uid ?: -1
        if (uid > 0 && isRootAvailable()) {
            runSuCommand(
                "cmd netpolicy remove restrict-background-blacklist $uid",
                "iptables -D OUTPUT -m owner --uid-owner $uid -j DROP",
                "ip6tables -D OUTPUT -m owner --uid-owner $uid -j DROP",
                "pm unsuspend $packageName"
            )
            Log.i(TAG, "🟢 [ROOT-UNBLOCK] Aturan iptables DROP & netpolicy dicabut untuk $packageName (UID=$uid)")
        }

        "Blokir data untuk $packageName telah dicabut. Aplikasi dapat mengakses internet kembali."
    }

    fun forceStopApp(context: Context, packageName: String): Boolean {
        var killed = false
        if (isRootAvailable()) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $packageName"))
                killed = (p.waitFor() == 0)
            } catch (_: Throwable) {}
        }
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(packageName)
            killed = true
        } catch (_: Throwable) {}
        return killed
    }

    fun suspendApp(packageName: String, suspend: Boolean): Boolean {
        if (!isRootAvailable()) return false
        val cmd = if (suspend) "pm suspend $packageName" else "pm unsuspend $packageName"
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            p.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    fun openAppDetailsSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuka pengaturan aplikasi: ${e.message}")
        }
    }

    fun openOverlayPermissionSettings(context: Context, packageName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                openAppDetailsSettings(context, packageName)
            }
        } catch (_: Throwable) {
            openAppDetailsSettings(context, packageName)
        }
    }
}
