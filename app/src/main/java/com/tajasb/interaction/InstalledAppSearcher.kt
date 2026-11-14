package com.tajasb.interaction

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

/**
 * Pencari daftar aplikasi (APK) yang terpasang dan dapat diluncurkan di perangkat,
 * digunakan untuk fitur Home → Delay → Back (pencarian aplikasi tujuan).
 */
object InstalledAppSearcher {

    fun loadLaunchableApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
        } catch (_: Throwable) {
            emptyList()
        }

        val selfPkg = context.packageName
        val seen = HashSet<String>()
        val list = ArrayList<InstalledAppInfo>(resolved.size)
        for (ri in resolved) {
            val activityInfo = ri.activityInfo ?: continue
            val pkg = activityInfo.packageName?.trim().orEmpty()
            if (pkg.isEmpty() || pkg == selfPkg || !seen.add(pkg)) continue
            val label = try {
                ri.loadLabel(pm)?.toString()?.trim().orEmpty()
            } catch (_: Throwable) {
                ""
            }.ifBlank { pkg }
            list += InstalledAppInfo(appName = label, packageName = pkg)
        }

        return list.sortedWith(
            compareBy<InstalledAppInfo>(
                { !isMlbbPackage(it.packageName) },
                { it.appName.lowercase() }
            )
        )
    }

    fun filterApps(
        apps: List<InstalledAppInfo>,
        query: String,
        limit: Int = 20
    ): List<InstalledAppInfo> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps.take(limit)
        return apps.filter {
            it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }.take(limit)
    }

    fun resolveAppName(context: Context, packageName: String): String? {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return null
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
            pm.getApplicationLabel(appInfo)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun isMlbbPackage(pkg: String): Boolean {
        return pkg == "com.mobile.legends" ||
            pkg == "com.mobile.legends.hwag" ||
            pkg == "com.vng.mlbbvn"
    }
}
