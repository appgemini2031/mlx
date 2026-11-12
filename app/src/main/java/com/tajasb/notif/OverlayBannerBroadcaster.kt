package com.tajasb.notif

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * OverlayBannerBroadcaster:
 * Transmits real-time comment/doa and buyer notification events across process boundaries
 * directly into the target app's Virtual Camera pipeline (MainHook / GlVideoRenderer),
 * ensuring viewers on TikTok / Shopee Live see every prayer comment and buyer badge.
 */
object OverlayBannerBroadcaster {
    private const val TAG = "OverlayBannerBroadcaster"

    const val ACTION_SHOW = "com.tajasb.action.SHOW_OVERLAY_BANNER"
    const val ACTION_HIDE = "com.tajasb.action.HIDE_OVERLAY_BANNER"
    const val ACTION_CONTROL = "com.tajasb.action.CONTROL_EVENT"

    private val TARGET_PACKAGES = listOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.ss.android.ugc.live",
        "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.aweme.lite",
        "com.instagram.android",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.shopee.id"
    )

    private const val MAX_SAFE_CONTENT_CHARS = 48000

    private val targetBroadcastExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "OverlayIpcSender").apply { isDaemon = true }
    }

    fun broadcastShow(
        context: Context,
        senderName: String,
        content: String,
        isGift: Boolean = false,
        bannerType: String = "COMMENT", // "COMMENT", "BUYER", "STICKER"
        iconMode: String = "PRAYER",    // "NONE", "PIN", "PRAYER", "SHOPPING"
        customPrefix: String = "",      // Custom prefix, e.g. "Doa", "Penonton", "Sahabat"
        formatMode: String = "PREFIX_AND_COMMENT", // "PREFIX_AND_COMMENT", "PREFIX_NAME_COMMENT", "NAME_AND_COMMENT", "COMMENT_ONLY"
        durationMs: Long = 4000L,
        posXpct: Float = 0.50f,
        posYpct: Float = 0.35f,
        rotation: Int = 0,
        scale: Float = 1.0f,
        isMirrored: Boolean = false,
        entryDirection: String = "LEFT",
        capsuleCornerRadius: Int = 39,
        capsulePaddingH: Int = 27,
        capsulePaddingV: Int = 2,
        capsuleAlpha: Float = 0.75f,
        capsuleOutlineWidth: Int = 0,
        capsuleOutlineColor: String = "#FFFFFF",
        capsuleBgColor: String = "#ff941a",
        fontSizeSp: Float = 16.0f,
        cartIconSizeDp: Float = 21.0f,
        stikerOutlineWidth: Int = 2,
        stikerOutlineColor: String = "#FFFFFF",
        stikerDisplayStyle: String = "WITH_BLOCK",
        isRunningText: Boolean = false,
        runningTextSpeed: Float = 1.0f,
        runningTextDirection: String = "LEFT"
    ) {
        try {
            val safeContent = if (content.length > MAX_SAFE_CONTENT_CHARS) {
                content.take(MAX_SAFE_CONTENT_CHARS)
            } else {
                content
            }
            val intent = Intent(ACTION_SHOW).apply {
                putExtra("extra_action", "SHOW_OVERLAY_BANNER")
                putExtra("extra_sender_name", senderName)
                putExtra("extra_content", safeContent)
                putExtra("extra_is_gift", isGift)
                putExtra("extra_banner_type", bannerType)
                putExtra("extra_icon_mode", iconMode)
                putExtra("extra_custom_prefix", customPrefix)
                putExtra("extra_format_mode", formatMode)
                putExtra("extra_duration_ms", durationMs)
                putExtra("extra_pos_x_pct", posXpct)
                putExtra("extra_pos_y_pct", posYpct)
                putExtra("extra_banner_rotation", rotation)
                putExtra("extra_banner_scale", scale)
                putExtra("extra_banner_mirrored", isMirrored)
                putExtra("extra_entry_direction", entryDirection)
                putExtra("extra_capsule_corner_radius", capsuleCornerRadius)
                putExtra("extra_capsule_padding_h", capsulePaddingH)
                putExtra("extra_capsule_padding_v", capsulePaddingV)
                putExtra("extra_capsule_alpha", capsuleAlpha)
                putExtra("extra_capsule_outline_width", capsuleOutlineWidth)
                putExtra("extra_capsule_outline_color", capsuleOutlineColor)
                putExtra("extra_capsule_bg_color", capsuleBgColor)
                putExtra("extra_font_size_sp", fontSizeSp)
                putExtra("extra_cart_icon_size_dp", cartIconSizeDp)
                putExtra("extra_stiker_outline_width", stikerOutlineWidth)
                putExtra("extra_stiker_outline_color", stikerOutlineColor)
                putExtra("extra_stiker_display_style", stikerDisplayStyle)
                putExtra("extra_is_running_text", isRunningText)
                putExtra("extra_running_text_speed", runningTextSpeed)
                putExtra("extra_running_text_direction", runningTextDirection)
                putExtra("extra_timestamp", System.currentTimeMillis())
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)

            // Offload explicit cross-package IPC broadcasts to a single background thread
            // so UI thread interactions never stall waiting on 9 ActivityManager Binder calls.
            val appCtx = context.applicationContext ?: context
            targetBroadcastExecutor.execute {
                for (tp in TARGET_PACKAGES) {
                    try {
                        val directIntent = Intent(intent).apply { setPackage(tp) }
                        appCtx.sendBroadcast(directIntent)
                    } catch (_: Throwable) {}
                }
            }
            Log.d(TAG, "Broadcasted SHOW_OVERLAY_BANNER: '$senderName' ($bannerType, dur=${durationMs}ms, pos=($posXpct, $posYpct))")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to broadcast overlay banner: ${t.message}")
        }
    }

    fun broadcastHide(context: Context, bannerType: String = "COMMENT") {
        try {
            val intent = Intent(ACTION_HIDE).apply {
                putExtra("extra_action", "HIDE_OVERLAY_BANNER")
                putExtra("extra_banner_type", bannerType)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)

            val ctrlIntent = Intent(ACTION_CONTROL).apply {
                putExtra("extra_action", "HIDE_OVERLAY_BANNER")
                putExtra("extra_banner_type", bannerType)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(ctrlIntent)

            val appCtx = context.applicationContext ?: context
            targetBroadcastExecutor.execute {
                for (tp in TARGET_PACKAGES) {
                    try {
                        val directIntent = Intent(intent).apply { setPackage(tp) }
                        appCtx.sendBroadcast(directIntent)
                        val directCtrl = Intent(ctrlIntent).apply { setPackage(tp) }
                        appCtx.sendBroadcast(directCtrl)
                    } catch (_: Throwable) {}
                }
            }
            Log.i(TAG, "Broadcasted HIDE_OVERLAY_BANNER ($bannerType) to virtual camera")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to broadcast hide overlay banner: ${t.message}")
        }
    }

    fun broadcastHideSticker(context: Context) {
        broadcastHide(context, "STICKER")
    }

    fun broadcastHideWatermark(context: Context) {
        broadcastHide(context, "WATERMARK")
    }

    fun broadcastHideAutoTime(context: Context) {
        broadcastHide(context, "AUTO_TIME")
    }

    fun broadcastHideQuranKhatam(context: Context) {
        broadcastHide(context, "QURAN_KHATAM")
    }

    fun broadcastHideQuranStats(context: Context) {
        broadcastHide(context, "QURAN_STATS")
    }

    fun broadcastHideEventStats(context: Context) {
        broadcastHide(context, "EVENT_STATS")
    }

    fun broadcastHideLike(context: Context) {
        broadcastHide(context, "EVENT_LIKE")
    }

    fun broadcastHideGift(context: Context) {
        broadcastHide(context, "EVENT_GIFT")
    }

    fun broadcastHideAll(context: Context) {
        broadcastHide(context, "ALL")
    }
}
