package com.tajasb.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import com.tajasb.model.TargetAppInfo

/**
 * PerAppConfigRepository:
 * Modular helper for installed target app discovery (including dual apps / work profiles / cloned apps).
 */
object PerAppConfigRepository {

    suspend fun loadInstalledApps(
        context: Context,
        prefs: SharedPreferences,
        currentTargetPackages: Set<String>,
        onFirstInitPackages: suspend (Set<String>) -> Unit
    ): List<TargetAppInfo> {
        val pm = context.packageManager
        val installedMap = mutableMapOf<String, TargetAppInfo>()

        val knownApps = listOf(
            TargetAppInfo("com.zhiliaoapp.musically", "TikTok", false, false, "Live Streaming"),
            TargetAppInfo("com.ss.android.ugc.trill", "TikTok Asia", false, false, "Live Streaming"),
            TargetAppInfo("com.instagram.android", "Instagram", false, false, "Sosial Media"),
            TargetAppInfo("com.whatsapp", "WhatsApp Messenger", false, false, "Panggilan Video"),
            TargetAppInfo("com.whatsapp.w4b", "WhatsApp Business", false, false, "Bisnis Video"),
            TargetAppInfo("org.telegram.messenger", "Telegram", false, false, "Panggilan Video"),
            TargetAppInfo("us.zoom.videomeetings", "Zoom Workplace", false, false, "Meeting / Konferensi"),
            TargetAppInfo("com.google.android.apps.meetings", "Google Meet", false, false, "Meeting"),
            TargetAppInfo("com.facebook.katana", "Facebook", false, false, "Sosial Media"),
            TargetAppInfo("com.facebook.orca", "Messenger", false, false, "Panggilan Video"),
            TargetAppInfo("com.snapchat.android", "Snapchat", false, false, "Kamera Efek"),
            TargetAppInfo("com.shopee.id", "Shopee Live", false, false, "Live Shopping"),
            TargetAppInfo("com.tokopedia.tkpd", "Tokopedia Play", false, false, "Live Shopping"),
            TargetAppInfo("com.lazada.android", "Lazada Live", false, false, "Live Shopping"),
            TargetAppInfo("sg.bigo.live", "Bigo Live", false, false, "Live Streaming"),
            TargetAppInfo("sg.bigo.lite", "Bigo Live Lite", false, false, "Live Streaming"),
            TargetAppInfo("chat.ometv", "OmeTV Video Chat", false, false, "Random Chat"),
            TargetAppInfo("litatom.android.litmatch", "Litmatch", false, false, "Voice & Video"),
            TargetAppInfo("com.michatapp.im", "MiChat", false, false, "Chat & Live"),
            TargetAppInfo("video.like", "Likee", false, false, "Live Streaming"),
            TargetAppInfo("com.discord", "Discord", false, false, "Voice & Video"),
            TargetAppInfo("com.jy.x.separation.manager", "MochiCloner (Live Sandbox)", false, false, "Kloning Mochi / Dual")
        )

        for (app in knownApps) {
            installedMap[app.packageName] = app
        }

        try {
            val installed = pm.getInstalledApplications(0)
            for (appInfo in installed) {
                if (appInfo.packageName == context.packageName) continue
                val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { appInfo.packageName }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val existing = installedMap[appInfo.packageName]
                val cat = existing?.category ?: if (isSystem) "Sistem" else "Aplikasi"

                installedMap[appInfo.packageName] = TargetAppInfo(
                    packageName = appInfo.packageName,
                    appName = label,
                    isSelected = false,
                    isSystemApp = isSystem,
                    category = cat
                )
            }
        } catch (_: Exception) {}

        try {
            val pkgList = pm.getInstalledPackages(0)
            for (pkg in pkgList) {
                val pName = pkg.packageName
                if (pName == context.packageName || installedMap.containsKey(pName)) continue
                val appInfo = pkg.applicationInfo ?: continue
                val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { pName }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                installedMap[pName] = TargetAppInfo(
                    packageName = pName,
                    appName = label,
                    isSelected = false,
                    isSystemApp = isSystem,
                    category = if (isSystem) "Sistem" else "Aplikasi"
                )
            }
        } catch (_: Exception) {}

        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveList = pm.queryIntentActivities(launcherIntent, 0)
            for (ri in resolveList) {
                val pName = ri.activityInfo?.packageName ?: continue
                if (pName == context.packageName || installedMap.containsKey(pName)) continue
                val label = try { ri.loadLabel(pm).toString() } catch (_: Exception) { pName }
                installedMap[pName] = TargetAppInfo(
                    packageName = pName,
                    appName = label,
                    isSelected = false,
                    isSystemApp = false,
                    category = "Aplikasi"
                )
            }
        } catch (_: Exception) {}

        try {
            val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE")
            val resolveList = pm.queryIntentActivities(cameraIntent, 0)
            for (ri in resolveList) {
                val pName = ri.activityInfo?.packageName ?: continue
                if (pName == context.packageName || installedMap.containsKey(pName)) continue
                val label = try { ri.loadLabel(pm).toString() } catch (_: Exception) { pName }
                installedMap[pName] = TargetAppInfo(
                    packageName = pName,
                    appName = label,
                    isSelected = false,
                    isSystemApp = false,
                    category = "Kamera / Live"
                )
            }
        } catch (_: Exception) {}

        try {
            val launcherApps = try {
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            } catch (_: Throwable) {
                null
            }
            val userManager = try {
                context.getSystemService(Context.USER_SERVICE) as? UserManager
            } catch (_: Throwable) {
                null
            }
            val profiles = try {
                userManager?.userProfiles ?: listOf(Process.myUserHandle())
            } catch (_: Throwable) {
                listOf(Process.myUserHandle())
            }
            for (user in profiles) {
                try {
                    val activityList = try {
                        launcherApps?.getActivityList(null, user) ?: emptyList()
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    val isSecondaryUser = try {
                        user != Process.myUserHandle()
                    } catch (_: Throwable) {
                        false
                    }
                    for (la in activityList) {
                        try {
                            val pName = la.applicationInfo?.packageName ?: continue
                            if (pName == context.packageName) continue
                            val label = try { la.label?.toString() ?: pName } catch (_: Throwable) { pName }
                            val displayLabel = if (isSecondaryUser && !label.contains("Klon", ignoreCase = true)) "$label (Kloning)" else label
                            val isSystem = try { (la.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } catch (_: Throwable) { false }
                            val key = if (isSecondaryUser && installedMap.containsKey(pName)) "$pName#clone" else pName
                            installedMap[key] = TargetAppInfo(
                                packageName = pName,
                                appName = displayLabel,
                                isSelected = false,
                                isSystemApp = isSystem,
                                category = if (isSecondaryUser) "Kloning Mochi / Dual" else "Aplikasi"
                            )
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        val isFirstInit = !prefs.contains("target_packages_init")
        val currentSet = HashSet(currentTargetPackages)
        if (isFirstInit) {
            val defaultPicks = setOf(
                "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
                "com.instagram.android", "com.whatsapp", "com.whatsapp.w4b"
            )
            currentSet.addAll(defaultPicks)
            prefs.edit().putBoolean("target_packages_init", true).apply()
            onFirstInitPackages(currentSet)
        }

        val finalList = installedMap.values.map { app ->
            app.copy(isSelected = currentSet.contains(app.packageName))
        }.distinctBy { "${it.packageName}#${it.appName}#${it.category}" }.toMutableList()

        for (pkg in currentSet) {
            if (finalList.none { it.packageName == pkg }) {
                finalList.add(
                    TargetAppInfo(
                        packageName = pkg,
                        appName = pkg,
                        isSelected = true,
                        isSystemApp = false,
                        category = "Target Kloning / Kustom"
                    )
                )
            }
        }

        return finalList.sortedWith(
            compareByDescending<TargetAppInfo> { it.isSelected }
                .thenByDescending { it.category.contains("Kloning") || it.category == "Live Streaming" || it.category == "Sosial Media" }
                .thenBy { it.appName.lowercase() }
        )
    }
}
