package com.tajasb.optimizer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.MainActivity
import com.tajasb.data.VcamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.IOException

/**
 * LiveFocusVpnService:
 * Local loopback firewall VPN that isolates the dynamic network connection
 * exclusively for user-selected live streaming apps, while dropping
 * background network packets from all other apps to prevent lag and frame drops.
 */
class LiveFocusVpnService : VpnService() {

    companion object {
        private const val TAG = "LiveFocusVpn"
        const val ACTION_START = "com.tajasb.action.START_VPN_FOCUS"
        const val ACTION_STOP = "com.tajasb.action.STOP_VPN_FOCUS"
        const val CHANNEL_ID = "vcam_live_focus_channel"
        const val NOTIFICATION_ID = 2026

        @Volatile
        var isVpnActive: Boolean = false
            private set

        fun isPrepared(context: Context): Boolean {
            return prepare(context) == null
        }

        fun getPrepareIntent(context: Context): Intent? {
            return prepare(context)
        }

        fun start(context: Context) {
            try {
                val intent = Intent(context, LiveFocusVpnService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start LiveFocusVpnService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, LiveFocusVpnService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop LiveFocusVpnService: ${e.message}", e)
            }
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var blackholeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopVpnTunnel()
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground: ${e.message}", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            } catch (_: Throwable) {}
        }

        startVpnTunnel()

        return START_STICKY
    }

    private fun startVpnTunnel() {
        if (isVpnActive && vpnInterface != null) return

        try {
            val builder = Builder()
                .setSession("TAJASB Firewall & Anti-Iklan")
                .addAddress("10.254.1.1", 24)
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)

            val repo = try { VcamRepository.getInstance(applicationContext) } catch (_: Exception) { null }
            val cfg = repo?.config?.value
            val isLiveFocusOn = cfg?.isLiveFocusVpnEnabled ?: false
            val isAdBlockerOn = cfg?.isAdBlockerActive ?: false
            val blockedAds = cfg?.blockedAdPackages ?: emptySet()
            val pm = packageManager

            var totalAffectedApps = 0

            if (!isLiveFocusOn && isAdBlockerOn && blockedAds.isNotEmpty()) {
                // MODE TARGET BLACKLIST (Khusus Blokir Aplikasi Iklan):
                // addAllowedApplication: HANYA aplikasi iklan yang masuk ke VPN Blackhole (paket dibuang 100%).
                // Semua aplikasi lain (TikTok, Shopee, browser, WA) langsung pakai koneksi normal tanpa sentuh VPN!
                for (pkg in blockedAds) {
                    try {
                        pm.getPackageInfo(pkg, 0)
                        builder.addAllowedApplication(pkg)
                        totalAffectedApps++
                    } catch (_: PackageManager.NameNotFoundException) {}
                }
                if (totalAffectedApps == 0) {
                    Log.w(TAG, "Tidak ada aplikasi iklan yang terpasang untuk diblokir.")
                    stopVpnTunnel()
                    stopSelf()
                    return
                }
                Log.i(TAG, "VPN Blackhole aktif: $totalAffectedApps aplikasi iklan diblokir total datanya. Aplikasi lain berjalan normal.")
            } else {
                // MODE LIVE FOCUS (Whitelist):
                val userBypass = cfg?.vpnBypassPackages ?: emptySet()
                val targetApps = cfg?.targetPackages ?: emptySet()
                val primaryTarget = cfg?.primaryTargetPackage ?: ""

                val allowedBypassPackages = mutableSetOf(packageName)

                if (userBypass.isNotEmpty()) {
                    allowedBypassPackages.addAll(userBypass)
                } else if (targetApps.isNotEmpty()) {
                    allowedBypassPackages.addAll(targetApps)
                    if (primaryTarget.isNotBlank()) allowedBypassPackages.add(primaryTarget)
                } else {
                    allowedBypassPackages.addAll(listOf(
                        "com.zhiliaoapp.musically",
                        "com.ss.android.ugc.trill",
                        "com.ss.android.ugc.aweme",
                        "com.shopee.id"
                    ))
                }

                // PASTIKAN aplikasi iklan yang diblokir TIDAK PERNAH masuk bypass:
                allowedBypassPackages.removeAll(blockedAds)

                for (pkg in allowedBypassPackages) {
                    try {
                        pm.getPackageInfo(pkg, 0)
                        builder.addDisallowedApplication(pkg)
                        totalAffectedApps++
                    } catch (_: PackageManager.NameNotFoundException) {}
                }
                Log.i(TAG, "Live Focus VPN Tunnel established! $totalAffectedApps aplikasi live dilewati tanpa hambatan. Aplikasi lain & iklan diblokir.")
            }

            val pfd = builder.establish()
            if (pfd == null) {
                Log.w(TAG, "VPN builder.establish() returned null. Permission might be pending or not granted.")
                isVpnActive = false
                stopVpnTunnel()
                stopSelf()
                return
            }
            vpnInterface = pfd
            isVpnActive = true

            // Read packets from background apps and drop them (blackhole)
            blackholeJob?.cancel()
            blackholeJob = serviceScope.launch {
                    val inStream = FileInputStream(pfd.fileDescriptor)
                    val buffer = ByteArray(4096)
                    try {
                        while (isActive && isVpnActive) {
                            val length = inStream.read(buffer)
                            if (length <= 0) {
                                kotlinx.coroutines.delay(20)
                            }
                            // Intentionally discarded (Drop non-live packets)
                        }
                    } catch (e: IOException) {
                        if (isActive) Log.d(TAG, "VPN Stream closed: ${e.message}")
                    } finally {
                        try { inStream.close() } catch (_: Exception) {}
                }
            }

            // Update Repository
            serviceScope.launch {
                try {
                    VcamRepository.getInstance(applicationContext).updateConfig {
                        it.copy(isLiveFocusVpnEnabled = true)
                    }
                } catch (_: Exception) {}
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN tunnel: ${e.message}", e)
            stopVpnTunnel()
            stopSelf()
        }
    }

    private fun stopVpnTunnel() {
        isVpnActive = false
        blackholeJob?.cancel()
        blackholeJob = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface: ${e.message}")
        }
        vpnInterface = null

        serviceScope.launch {
            try {
                VcamRepository.getInstance(applicationContext).updateConfig {
                    it.copy(isLiveFocusVpnEnabled = false)
                }
            } catch (_: Exception) {}
        }

        Log.i(TAG, "Live Focus VPN Tunnel stopped. Koneksi normal dipulihkan.")
    }

    override fun onDestroy() {
        stopVpnTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TAJASB Fokus Jaringan Live",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status pemblokiran data aplikasi lain saat live streaming"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveFocusVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (speedMbps, speedType) = DeviceOptimizationManager.getDynamicNetworkBandwidth(this)
        val speedStr = "$speedMbps Mbps ($speedType)"

        val repo = try { VcamRepository.getInstance(applicationContext) } catch (_: Exception) { null }
        val cfg = repo?.config?.value
        val isLiveFocusOn = cfg?.isLiveFocusVpnEnabled ?: false
        val isAdBlockerOn = cfg?.isAdBlockerActive ?: false
        val adCount = cfg?.blockedAdPackages?.size ?: 0

        val notifTitle = when {
            isAdBlockerOn && !isLiveFocusOn -> "🚫 Blokir Data Iklan: AKTIF ($adCount APK)"
            isAdBlockerOn && isLiveFocusOn -> "🛡️ Fokus Live + Blokir Iklan ($adCount APK)"
            else -> "🛡️ Fokus Jaringan Live: AKTIF"
        }

        val notifContent = when {
            isAdBlockerOn && !isLiveFocusOn -> "Semua data $adCount aplikasi pembuat iklan dibuang ke blackhole (0 KB/s)."
            else -> "Koneksi ~$speedStr dikhususkan untuk aplikasi live pilihan. Aplikasi lain & iklan diblokir."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(notifTitle)
            .setContentText(notifContent)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setOngoing(true)
            .setContentIntent(pOpenIntent)
            .addAction(0, "Matikan Firewall", pStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
