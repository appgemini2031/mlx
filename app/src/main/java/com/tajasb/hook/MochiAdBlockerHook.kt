package com.tajasb.hook

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * MochiAdBlockerHook:
 * Pencegat & Pemblokir Iklan In-Process khusus untuk MochiCloner (com.jy.x.separation.manager)
 * dan aplikasi container kloning lainnya.
 *
 * Mengatasi masalah:
 * - Pengguna live DI DALAM MochiCloner sehingga MochiCloner TIDAK BOLEH dimatikan atau diputus internetnya secara total.
 * - Modul ini mencegat pemanggilan iklan dari DALAM proses secara presisi:
 *   1. Memblokir resolusi DNS domain server iklan (AdMob, Pangle, CSJ, AppLovin, UnityAds, GDT, dll.)
 *      sehingga iklan gagal memuat tanpa menyentuh koneksi streaming TikTok/Shopee sama sekali.
 *   2. Memblokir peluncuran Activity Iklan layar penuh (AdActivity, Interstitial, TTFullScreen, dll.).
 *   3. Menahan eksekusi metode show() pada SDK iklan.
 *   4. Menutup Dialog / Pop-up iklan yang mencoba melompat ke atas layar saat live.
 */
object MochiAdBlockerHook {

    private const val TAG = "MochiAntiAd"

    private val AD_DOMAIN_KEYWORDS = listOf(
        "googleads",
        "doubleclick.net",
        "pagead",
        "adservice.google",
        "admob",
        "pangle.io",
        "pangolin",
        "openadsdk",
        "applovin",
        "unityads",
        "unity3d.ads",
        "ironsrc",
        "supersonicads",
        "mintegral",
        "anythink",
        "toponad",
        "gdt.qq.com",
        "e.qq.com",
        "sigmob",
        "kwad.com",
        "kuaishou.ad",
        "adcolony",
        "vungle",
        "inmobi",
        "tapjoy",
        "chartboost",
        "fyber",
        "adx.",
        "adserver",
        "adview",
        "mobvista"
    )

    private val AD_ACTIVITY_KEYWORDS = listOf(
        "adactivity",
        "ttfullscreenvideoactivity",
        "ttrewardvideoactivity",
        "ttfullscreenexpressvideoactivity",
        "interstitialactivity",
        "rewardactivity",
        "rewardvideoactivity",
        "splashadactivity",
        "popupadactivity",
        "applovininterstitialactivity",
        "appopenadactivity",
        "unifiedinterstitialad",
        "anythinkinterstitial",
        "kswindowactivity"
    )

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        Log.i(TAG, "🛡️ [MOCHI-ANTI-AD] Mengaktifkan pemblokir iklan cerdas di $pkg (${lpparam.processName})")

        hookAdNetworkDns(lpparam)
        hookAdActivities(lpparam)
        hookAdSdkClasses(lpparam)
        hookAdDialogs(lpparam)
    }

    /**
     * 1. Blokir DNS Server Iklan:
     * Menolak permintaan domain iklan dengan melempar UnknownHostException.
     * Aliran live stream (TikTok CDN, Shopee Live, dll.) tetap 100% normal tanpa terganggu.
     */
    private fun hookAdNetworkDns(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                InetAddress::class.java,
                "getAllByName",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val host = (param.args[0] as? String)?.lowercase() ?: return
                        if (isAdDomain(host)) {
                            Log.d(TAG, "🚫 [DNS-BLOCKED] Memutus akses domain iklan: $host")
                            param.throwable = UnknownHostException("Blocked by Tajasb Anti-Ad Engine: $host")
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Hook InetAddress.getAllByName gagal: ${t.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                InetAddress::class.java,
                "getByName",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val host = (param.args[0] as? String)?.lowercase() ?: return
                        if (isAdDomain(host)) {
                            Log.d(TAG, "🚫 [DNS-BLOCKED] Memutus akses domain iklan: $host")
                            param.throwable = UnknownHostException("Blocked by Tajasb Anti-Ad Engine: $host")
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun isAdDomain(host: String): Boolean {
        // Pengecualian mutlak: jangan pernah memblokir domain live streaming / media pengguna
        if (host.contains("tiktok") || host.contains("musical.ly") || host.contains("shopee") ||
            host.contains("instagram") || host.contains("facebook") || host.contains("whatsapp") ||
            host.contains("byteoversea") || host.contains("ibyteimg") || host.contains("snssdk") ||
            host.contains("amemv") || host.contains("tajasb") || host.contains("google.com") && !host.contains("ad")
        ) {
            return false
        }
        return AD_DOMAIN_KEYWORDS.any { host.contains(it) }
    }

    /**
     * 2. Blokir Peluncuran Activity Iklan Layar Penuh:
     * Menolak startActivity jika targetnya adalah Activity Iklan (AdMob, Pangle, Unity, dll.).
     * Jika Activity iklan tetap terbuat, langsung panggil finish() sebelum tampil di layar.
     */
    private fun hookAdActivities(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "startActivity",
                Intent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val intent = param.args[0] as? Intent ?: return
                        if (isAdIntent(intent)) {
                            Log.i(TAG, "🛑 [AD-LAUNCH-BLOCKED] Mencegah peluncuran Activity Iklan: ${intent.component?.className ?: intent.action}")
                            param.result = null
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "startActivityForResult",
                Intent::class.java,
                Int::class.javaPrimitiveType,
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val intent = param.args[0] as? Intent ?: return
                        if (isAdIntent(intent)) {
                            Log.i(TAG, "🛑 [AD-LAUNCH-BLOCKED] Mencegah peluncuran Activity Iklan (forResult): ${intent.component?.className}")
                            param.result = null
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onCreate",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val act = param.thisObject as? Activity ?: return
                        val className = act.javaClass.name.lowercase()
                        if (AD_ACTIVITY_KEYWORDS.any { className.contains(it) }) {
                            Log.i(TAG, "⚡ [AD-DESTROYED] Menutup paksa Activity Iklan sebelum muncul: ${act.javaClass.name}")
                            act.finish()
                            param.result = null
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun isAdIntent(intent: Intent): Boolean {
        val comp = intent.component?.className?.lowercase() ?: ""
        if (AD_ACTIVITY_KEYWORDS.any { comp.contains(it) }) return true
        val action = intent.action?.lowercase() ?: ""
        if (action.contains("ad.activity") || action.contains("openadsdk")) return true
        return false
    }

    /**
     * 3. Netralkan Pemanggilan show() pada SDK Iklan Populer:
     * Mencegah iklan dipanggil lewat method API ad network.
     */
    private fun hookAdSdkClasses(lpparam: XC_LoadPackage.LoadPackageParam) {
        val adClassesWithShow = listOf(
            "com.google.android.gms.ads.interstitial.InterstitialAd" to "show",
            "com.google.android.gms.ads.rewarded.RewardedAd" to "show",
            "com.google.android.gms.ads.appopen.AppOpenAd" to "show",
            "com.bytedance.sdk.openadsdk.TTFullScreenVideoAd" to "showFullScreenVideoAd",
            "com.bytedance.sdk.openadsdk.TTRewardVideoAd" to "showRewardVideoAd",
            "com.qq.e.ads.interstitial2.UnifiedInterstitialAD" to "show",
            "com.applovin.adview.AppLovinInterstitialAdDialog" to "show",
            "com.anythink.core.api.ATInterstitial" to "show"
        )

        for ((className, methodName) in adClassesWithShow) {
            try {
                val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
                if (clazz != null) {
                    XposedBridge.hookAllMethods(clazz, methodName, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            Log.i(TAG, "🛡️ [AD-SHOW-INTERCEPTED] Mencegah eksekusi $className.$methodName()")
                            param.result = null
                        }
                    })
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * 4. Blokir Dialog Pop-up Iklan:
     * Menolak dialog iklan mengambang yang mencoba melompat di atas layar live streaming.
     */
    private fun hookAdDialogs(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                Dialog::class.java,
                "show",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val dlg = param.thisObject as? Dialog ?: return
                        val cName = dlg.javaClass.name.lowercase()
                        if (cName.contains("ad") && (cName.contains("dialog") || cName.contains("pop") || cName.contains("interstitial"))) {
                            Log.i(TAG, "🛑 [AD-DIALOG-BLOCKED] Mencegah pop-up dialog iklan: ${dlg.javaClass.name}")
                            param.result = null
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }
}
