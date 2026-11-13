package com.tajasb.hooks.shopee

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * ShopeeBroadcastReceiver:
 * Menerima perintah real-time terisolasi ("com.tajasb.ACTION_SHOPEE_CMD") di dalam proses Shopee.
 * Wajib menyertakan Context.RECEIVER_EXPORTED pada Android 13+ (API 33+) agar tidak SecurityException
 * saat menerima intent dari proses aplikasi controller com.tajasb.
 */
object ShopeeBroadcastReceiver {
    @Volatile
    private var registered = false

    fun createAndRegister(ctx: Context): Boolean {
        if (registered) return true
        return try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    handleIntent(context ?: ctx, intent)
                }
            }
            val filter = IntentFilter(ShopeeConfigStore.ACTION_SHOPEE_CMD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, filter)
            }
            registered = true
            ShopeeMainHook.logInfo("ShopeeBroadcastReceiver terdaftar (ACTION_SHOPEE_CMD)")
            true
        } catch (t: Throwable) {
            ShopeeMainHook.logWarn("Gagal mendaftarkan ShopeeBroadcastReceiver: ${t.message}")
            false
        }
    }

    private fun handleIntent(context: Context, intent: Intent?) {
        if (intent?.action != ShopeeConfigStore.ACTION_SHOPEE_CMD) return
        val cmd = intent.getStringExtra("extra_shopee_cmd") ?: "UPDATE_CONFIG"
        ShopeeMainHook.logDebug("Menerima broadcast Shopee cmd=$cmd", sendToXposed = true)

        val cur = ShopeeMainHook.liveConfig
        when (cmd) {
            "UPDATE_CONFIG" -> {
                val updated = cur.copy(
                    enabled = intent.getBooleanExtra("extra_enabled", cur.enabled),
                    videoPath = intent.getStringExtra("extra_video_path") ?: cur.videoPath,
                    audioPath = intent.getStringExtra("extra_audio_path") ?: cur.audioPath,
                    rotation = intent.getIntExtra("extra_rotation", cur.rotation),
                    isMirrored = intent.getBooleanExtra("extra_mirror", cur.isMirrored),
                    isVerticalFlipped = intent.getBooleanExtra("extra_flip_v", cur.isVerticalFlipped),
                    zoomFactor = intent.getFloatExtra("extra_zoom_factor", cur.zoomFactor).coerceIn(0.3f, 5.0f),
                    scaleMode = intent.getStringExtra("extra_scale_mode") ?: cur.scaleMode,
                    isPaused = intent.getBooleanExtra("extra_paused", cur.isPaused),
                    isMuted = intent.getBooleanExtra("extra_muted", cur.isMuted),
                    isVideoAudioMuted = intent.getBooleanExtra("extra_video_audio_muted", cur.isVideoAudioMuted),
                    isMp3AudioPaused = intent.getBooleanExtra("extra_mp3_paused", cur.isMp3AudioPaused),
                    fps = intent.getIntExtra("extra_fps", cur.fps).coerceIn(15, 60),
                    bypassScannerAndKyc = intent.getBooleanExtra("extra_bypass_scanner_kyc", cur.bypassScannerAndKyc),
                    onlyInLiveOrVideoActivity = intent.getBooleanExtra("extra_only_live_video", cur.onlyInLiveOrVideoActivity),
                    customClonedPackages = intent.getStringExtra("extra_custom_cloned_pkgs") ?: cur.customClonedPackages,
                    version = intent.getLongExtra("extra_version", cur.version)
                )
                ShopeeMainHook.applyLiveConfig(updated, context)
            }
            "RELOAD_VIDEO" -> ShopeeMainHook.reloadVideoFromProviderAsync(context, forceRefresh = true)
            "RELOAD_AUDIO" -> ShopeeMainHook.reloadAudioFromProviderAsync(context, forceRefresh = true)
            "TOGGLE_PAUSE" -> ShopeeMainHook.applyLiveConfig(cur.copy(isPaused = !cur.isPaused), context)
            "TOGGLE_MUTE" -> ShopeeMainHook.applyLiveConfig(cur.copy(isMuted = !cur.isMuted), context)
            "CYCLE_ROTATION" -> ShopeeMainHook.applyLiveConfig(cur.copy(rotation = (cur.rotation + 90) % 360), context)
            "TOGGLE_MIRROR" -> ShopeeMainHook.applyLiveConfig(cur.copy(isMirrored = !cur.isMirrored), context)
            "TOGGLE_BYPASS_SCANNER_KYC" -> {
                ShopeeMainHook.applyLiveConfig(cur.copy(bypassScannerAndKyc = !cur.bypassScannerAndKyc), context)
            }
            "SET_SCALE_MODE" -> {
                val mode = intent.getStringExtra("extra_scale_mode") ?: "CROP"
                ShopeeMainHook.applyLiveConfig(cur.copy(scaleMode = mode), context)
            }
            "STOP_PLAYBACK" -> ShopeeMainHook.stopVideoPlayback(reason = "Broadcast STOP_PLAYBACK", force = true)
        }
    }
}
