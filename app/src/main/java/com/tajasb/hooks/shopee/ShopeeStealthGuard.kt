package com.tajasb.hooks.shopee

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Debug
import android.provider.Settings
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * ShopeeStealthGuard:
 * Pelindung anti-deteksi yang 100% terisolasi khusus untuk proses Shopee Live & Shopee Video.
 * Mengurangi risiko error "Maaf, Livestream gagal karena terdapat aktivitas mencurigakan"
 * akibat SDK keamanan Shopee memindai paket modul/kloner, layanan aksesibilitas, atau flag debug.
 */
object ShopeeStealthGuard {

    @Volatile
    private var installed = false

    private val internalLookupDepth = ThreadLocal.withInitial { 0 }

    private val HIDDEN_PACKAGE_TOKENS = listOf(
        "com.tajasb",
        "com.aistudio.tajasb",
        "com.aistudio.cuancame",
        "de.robv.android.xposed",
        "org.lsposed",
        "io.github.lsposed",
        "com.topjohnwu.magisk",
        "io.github.vvb2060.magisk",
        "me.weishu.exp",
        "com.solohsu.android.edxp",
        "org.meowcat.edxposed"
    )

    private val HIDDEN_ACCESSIBILITY_TOKENS = listOf(
        "com.tajasb",
        "tajasb",
        "aistudio.tajasb",
        "aistudio.cuancame",
        "mochi",
        "com.jy.x"
    )

    fun <T> runInternal(block: () -> T): T {
        val prev = internalLookupDepth.get() ?: 0
        internalLookupDepth.set(prev + 1)
        return try {
            block()
        } finally {
            internalLookupDepth.set(prev)
        }
    }

    private fun isInternalCall(): Boolean = (internalLookupDepth.get() ?: 0) > 0

    fun shouldHidePackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.trim().lowercase()
        return HIDDEN_PACKAGE_TOKENS.any { token ->
            lower == token || lower.startsWith("$token.") || lower.contains(token)
        }
    }

    fun shouldHideAccessibilityComponent(componentOrId: String?): Boolean {
        if (componentOrId.isNullOrBlank()) return false
        val lower = componentOrId.lowercase()
        return HIDDEN_ACCESSIBILITY_TOKENS.any { lower.contains(it) }
    }

    fun sanitizeAccessibilityServicesSetting(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.split(':')
            .map { it.trim() }
            .filter { it.isNotBlank() && !shouldHideAccessibilityComponent(it) }
            .joinToString(":")
    }

    fun install(classLoader: ClassLoader) {
        if (installed) return
        installed = true
        hookPackageManager(classLoader)
        hookAccessibilityManager()
        hookSettingsSecure()
        hookDebugFlags()
        ShopeeMainHook.logInfo("ShopeeStealthGuard aktif (PackageManager + Accessibility + Debug guard)")
    }

    private fun hookPackageManager(classLoader: ClassLoader) {
        val pmClass = try {
            XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader)
        } catch (_: Throwable) {
            return
        }

        try {
            XposedBridge.hookAllMethods(pmClass, "getInstalledPackages", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (isInternalCall()) return
                    val list = param.result as? MutableList<*> ?: return
                    val filtered = list.filterNot { item ->
                        (item as? PackageInfo)?.packageName?.let { shouldHidePackage(it) } == true
                    }
                    if (filtered.size != list.size) {
                        param.result = ArrayList(filtered)
                    }
                }
            })
        } catch (_: Throwable) {}

        try {
            XposedBridge.hookAllMethods(pmClass, "getInstalledApplications", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (isInternalCall()) return
                    val list = param.result as? MutableList<*> ?: return
                    val filtered = list.filterNot { item ->
                        (item as? ApplicationInfo)?.packageName?.let { shouldHidePackage(it) } == true
                    }
                    if (filtered.size != list.size) {
                        param.result = ArrayList(filtered)
                    }
                }
            })
        } catch (_: Throwable) {}

        try {
            XposedBridge.hookAllMethods(pmClass, "queryIntentActivities", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (isInternalCall()) return
                    val list = param.result as? List<*> ?: return
                    val filtered = list.filterNot { item ->
                        val ri = item as? ResolveInfo
                        val pkg = ri?.activityInfo?.packageName
                        shouldHidePackage(pkg)
                    }
                    if (filtered.size != list.size) {
                        param.result = ArrayList(filtered)
                    }
                }
            })
        } catch (_: Throwable) {}

        listOf("getPackageInfo", "getApplicationInfo").forEach { methodName ->
            try {
                XposedBridge.hookAllMethods(pmClass, methodName, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isInternalCall()) return
                        val targetPkg = param.args?.firstOrNull() as? String ?: return
                        if (shouldHidePackage(targetPkg)) {
                            param.throwable = PackageManager.NameNotFoundException(targetPkg)
                        }
                    }
                })
            } catch (_: Throwable) {}
        }
    }

    private fun hookAccessibilityManager() {
        val amClass = try {
            Class.forName("android.view.accessibility.AccessibilityManager")
        } catch (_: Throwable) {
            return
        }

        listOf("getEnabledAccessibilityServiceList", "getInstalledAccessibilityServiceList").forEach { methodName ->
            try {
                XposedBridge.hookAllMethods(amClass, methodName, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isInternalCall()) return
                        val list = param.result as? List<*> ?: return
                        val filtered = list.filterNot { item ->
                            val info = item as? AccessibilityServiceInfo
                            val id = info?.id ?: info?.resolveInfo?.serviceInfo?.packageName
                            shouldHideAccessibilityComponent(id)
                        }
                        if (filtered.size != list.size) {
                            param.result = ArrayList(filtered)
                        }
                    }
                })
            } catch (_: Throwable) {}
        }
    }

    private fun hookSettingsSecure() {
        try {
            XposedHelpers.findAndHookMethod(
                Settings.Secure::class.java,
                "getString",
                ContentResolver::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isInternalCall()) return
                        val name = param.args?.getOrNull(1) as? String ?: return
                        when (name) {
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES -> {
                                val raw = param.result as? String ?: return
                                val cleaned = sanitizeAccessibilityServicesSetting(raw)
                                if (cleaned != raw) {
                                    param.result = cleaned
                                }
                            }
                            Settings.Global.ADB_ENABLED,
                            "adb_enabled",
                            "development_settings_enabled" -> {
                                param.result = "0"
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                Settings.Global::class.java,
                "getInt",
                ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isInternalCall()) return
                        val name = param.args?.getOrNull(1) as? String ?: return
                        if (name == Settings.Global.ADB_ENABLED || name == Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) {
                            param.result = 0
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun hookDebugFlags() {
        try {
            XposedHelpers.findAndHookMethod(
                Debug::class.java,
                "isDebuggerConnected",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                }
            )
        } catch (_: Throwable) {}
    }
}
