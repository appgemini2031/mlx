package com.tajasb.hooks.bigo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/**
 * BigoBroadcastReceiver:
 * Menerima perintah real-time terisolasi ("com.tajasb.ACTION_BIGO_CMD") di dalam proses BIGO Live.
 * Wajib menyertakan Context.RECEIVER_EXPORTED pada Android 13+ (API 33+) agar tidak SecurityException
 * saat menerima intent dari proses aplikasi controller com.tajasb.
 */
object BigoBroadcastReceiver {
    private const val TAG = "Tajasb-BIGO"

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
            val filter = IntentFilter(BigoConfigStore.ACTION_BIGO_CMD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, filter)
            }
            registered = true
            BigoMainHook.logInfo("BigoBroadcastReceiver terdaftar (ACTION_BIGO_CMD)")
            true
        } catch (t: Throwable) {
            BigoMainHook.logWarn("Gagal mendaftarkan BigoBroadcastReceiver: ${t.message}")
            false
        }
    }

    private fun handleIntent(context: Context, intent: Intent?) {
        if (intent?.action != BigoConfigStore.ACTION_BIGO_CMD) return
        val cmd = intent.getStringExtra("extra_bigo_cmd") ?: "UPDATE_CONFIG"
        BigoMainHook.logDebug("Menerima broadcast BIGO cmd=$cmd", sendToXposed = true)

        val cur = BigoMainHook.liveConfig
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
                    version = intent.getLongExtra("extra_version", cur.version)
                )
                BigoMainHook.applyLiveConfig(updated, context)
            }
            "RELOAD_VIDEO" -> BigoMainHook.reloadVideoFromProviderAsync(context, forceRefresh = true)
            "RELOAD_AUDIO" -> BigoMainHook.reloadAudioFromProviderAsync(context, forceRefresh = true)
            "TOGGLE_PAUSE" -> BigoMainHook.applyLiveConfig(cur.copy(isPaused = !cur.isPaused), context)
            "TOGGLE_MUTE" -> BigoMainHook.applyLiveConfig(cur.copy(isMuted = !cur.isMuted), context)
            "CYCLE_ROTATION" -> BigoMainHook.applyLiveConfig(cur.copy(rotation = (cur.rotation + 90) % 360), context)
            "TOGGLE_MIRROR" -> BigoMainHook.applyLiveConfig(cur.copy(isMirrored = !cur.isMirrored), context)
            "SET_SCALE_MODE" -> {
                val mode = intent.getStringExtra("extra_scale_mode") ?: "CROP"
                BigoMainHook.applyLiveConfig(cur.copy(scaleMode = mode), context)
            }
            "STOP_PLAYBACK" -> BigoMainHook.stopVideoPlayback(reason = "Broadcast STOP_PLAYBACK", force = true)
        }
    }
}
