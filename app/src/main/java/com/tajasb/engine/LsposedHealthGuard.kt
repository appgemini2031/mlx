package com.tajasb.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.tajasb.data.VcamRepository

data class LsposedHealthStatus(
    val isModuleActive: Boolean,
    val isTikTokInstalled: Boolean,
    val tikTokPackageName: String?,
    val isHealthy: Boolean,
    val statusTitle: String,
    val description: String,
    val recommendation: String,
    val actionGuide: List<String>
)

/**
 * LsposedHealthGuard:
 * Mendiagnosis status kesehatan integrasi Xposed / LSPosed secara real-time.
 * Mendeteksi apakah modul tercentang, apakah target TikTok terpasang,
 * dan menyediakan instruksi penyelesaian masalah tanpa tebak-tebak.
 */
object LsposedHealthGuard {

    private const val TAG = "LsposedHealthGuard"

    val TIKTOK_PACKAGES = listOf(
        "com.zhiliaoapp.musically", // TikTok Global / Asia Tenggara
        "com.ss.android.ugc.trill", // TikTok Asia Tenggara alternatif
        "com.zhiliaoapp.musically.go", // TikTok Lite
        "com.ss.android.ugc.aweme" // Douyin
    )

    fun checkHealth(context: Context): LsposedHealthStatus {
        val repo = try { VcamRepository.getInstance(context) } catch (_: Throwable) { null }
        val isHooked = repo?.isHookedIndicator() ?: false

        var installedPkg: String? = null
        val pm = context.packageManager
        for (pkg in TIKTOK_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                installedPkg = pkg
                break
            } catch (_: PackageManager.NameNotFoundException) {
                // not installed
            } catch (_: Throwable) {}
        }

        val isTikTokInstalled = installedPkg != null

        val isHealthy = isHooked && isTikTokInstalled

        val title: String
        val desc: String
        val rec: String
        val guide = mutableListOf<String>()

        when {
            isHooked && isTikTokInstalled -> {
                title = "LSPosed & TikTok Terhubung Sempurna"
                desc = "Modul Tajasb aktif dan mendeteksi target $installedPkg dalam scope injeksi."
                rec = "Injeksi native Camera2 siap digunakan untuk live streaming TikTok."
                guide.add("Semua sistem normal. Anda dapat langsung membuka TikTok dan memulai live.")
            }
            !isHooked -> {
                title = "Modul LSPosed Belum Aktif / Scope Terlepas"
                desc = "Framework Xposed/LSPosed belum mencegat method Tajasb (indikator hook false)."
                rec = "Buka LSPosed Manager, pastikan centang 'Modul Diaktifkan' dan centang aplikasi Tajasb serta TikTok."
                guide.add("1. Buka aplikasi LSPosed Manager.")
                guide.add("2. Masuk ke tab Modul dan pilih Tajasb.")
                guide.add("3. Aktifkan modul dan centang TikTok ($installedPkg) pada daftar aplikasi target.")
                guide.add("4. Paksa berhenti (Force Stop) aplikasi TikTok lalu buka kembali.")
            }
            else -> {
                title = "LSPosed Aktif, Menunggu Target TikTok"
                desc = "Modul aktif, namun aplikasi resmi TikTok belum terdeteksi terpasang pada profil perangkat ini."
                rec = "Pasang aplikasi TikTok dari toko aplikasi resmi agar scope injeksi dapat diarahkan."
                guide.add("1. Pasang aplikasi TikTok (com.zhiliaoapp.musically).")
                guide.add("2. Pastikan TikTok dicentang dalam scope LSPosed.")
            }
        }

        return LsposedHealthStatus(
            isModuleActive = isHooked,
            isTikTokInstalled = isTikTokInstalled,
            tikTokPackageName = installedPkg,
            isHealthy = isHealthy,
            statusTitle = title,
            description = desc,
            recommendation = rec,
            actionGuide = guide
        )
    }

    /**
     * Membuka aplikasi LSPosed Manager jika tersedia.
     */
    fun openLsposedManager(context: Context): Boolean {
        val lsposedManagers = listOf(
            "org.lsposed.manager",
            "io.github.lsposed.manager",
            "org.meowcat.edxposed.manager",
            "de.robv.android.xposed.installer"
        )
        for (pkg in lsposedManagers) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Throwable) {}
        }
        return false
    }
}
