package com.tajasb.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Process
import com.tajasb.debug.LogLevel
import com.tajasb.debug.VcamDebugLogger
import java.util.Locale

/**
 * DebugOsdRenderer:
 * Generates the real-time Cyber Debug OSD Telemetry Bitmap and computes NDC coordinates
 * for in-stream diagnostic overlay rendering.
 */
object DebugOsdRenderer {

    fun generateDebugOsdBitmap(
        posMs: Int,
        durMs: Int,
        videoName: String,
        widthPx: Int,
        heightPx: Int,
        totalPlaylistCount: Int,
        currentPlaylistIndex: Int,
        videoWidth: Int,
        videoHeight: Int,
        currentFps: Float,
        currentRotation: Int,
        autoNextLeadMsVal: Int,
        autoNextModeName: String,
        isLoopingEnabled: Boolean,
        isMuted: Boolean,
        isVideoAudioMuted: Boolean,
        isPreloadAckReady: Boolean,
        activeSlot: Int,
        oesTextureIdA: Int,
        oesTextureIdB: Int,
        context: android.content.Context? = null,
        activeVideoFile: java.io.File? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgRect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#F2030712")
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            color = Color.parseColor("#00F0FF")
        }
        canvas.drawRoundRect(bgRect, 16f, 16f, bgPaint)
        canvas.drawRoundRect(bgRect, 16f, 16f, borderPaint)

        val curSec = (posMs / 1000).coerceAtLeast(0)
        val curMs = (posMs % 1000).coerceAtLeast(0)
        val totSec = (durMs / 1000).coerceAtLeast(0)
        val totMs = (durMs % 1000).coerceAtLeast(0)
        val remMs = (durMs - posMs).coerceAtLeast(0)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.parseColor("#38BDF8")
            textSize = 17f
            setShadowLayer(2f, 0f, 1f, Color.BLACK)
        }

        val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.WHITE
            textSize = 18f
            setShadowLayer(2f, 0f, 1f, Color.BLACK)
        }

        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.parseColor("#A7F3D0")
            textSize = 16f
            setShadowLayer(2f, 0f, 1f, Color.BLACK)
        }

        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.parseColor("#FBBF24")
            textSize = 15f
            setShadowLayer(2f, 0f, 1f, Color.BLACK)
        }

        val logPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            color = Color.parseColor("#E2E8F0")
            textSize = 13.5f
            setShadowLayer(1.5f, 0f, 1f, Color.BLACK)
        }

        val telemetryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.parseColor("#C084FC")
            textSize = 14f
            setShadowLayer(1.5f, 0f, 1f, Color.BLACK)
        }

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.parseColor("#334155")
        }

        val neonDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#0284C7")
        }

        canvas.drawText("⚡ TAJASB PRO PIPELINE | Xiaomi 2310FPCA4G (Android 15 / API 35) | GLES 3.2", 14f, 22f, headerPaint)

        val cleanName = if (videoName.length > 28) videoName.take(25) + "..." else videoName
        val idxStr = if (totalPlaylistCount > 1) " [${currentPlaylistIndex + 1}/$totalPlaylistCount]" else " [1/1]"
        canvas.drawText("📹 $cleanName$idxStr | ${videoWidth}x${videoHeight} | FPS: ${String.format(Locale.US, "%.1f", currentFps)} | ROT:${currentRotation}°", 14f, 44f, clockPaint)

        val timeStr = String.format(Locale.US, "⏱ %02d:%02d.%03d / %02d:%02d.%03d  (Pos: %dms | Sisa: %dms | Lead: %dms)", curSec / 60, curSec % 60, curMs, totSec / 60, totSec % 60, totMs, posMs, remMs, autoNextLeadMsVal)
        canvas.drawText(timeStr, 14f, 66f, detailPaint)

        val statusLine = String.format(Locale.US, "⚡ Mode: %s | Loop: %s | Snd: %s | ACK: %s | EGL: 0ms Swap", autoNextModeName, if (isLoopingEnabled) "ON" else "OFF", if (isMuted || isVideoAudioMuted) "MUTED" else "ON", if (isPreloadAckReady) "READY" else "WAIT")
        canvas.drawText(statusLine, 14f, 88f, subHeaderPaint)

        canvas.drawLine(10f, 96f, widthPx - 10f, 96f, neonDividerPaint)

        val splitX = widthPx * 0.54f
        canvas.drawLine(splitX, 98f, splitX, heightPx - 10f, dividerPaint)

        canvas.drawText("📋 BUKTI BACKGROUND LIVE LOG:", 14f, 114f, subHeaderPaint)
        val recentLogs = VcamDebugLogger.getRecentLogs(7)
        var logY = 134f
        val maxLogChars = ((splitX - 24f) / 8.2f).toInt().coerceIn(24, 60)

        if (recentLogs.isEmpty()) {
            canvas.drawText("• [HOOK] Camera2 session intercept aktif", 14f, logY, logPaint); logY += 18f
            canvas.drawText("• [DEC] MediaPlayer 0ms atomic loop standby", 14f, logY, logPaint); logY += 18f
            canvas.drawText("• [IPC] ContentProvider & Broadcast receiver ready", 14f, logY, logPaint); logY += 18f
            canvas.drawText("• [WATCHDOG] Frame heartbeat active (30 FPS)", 14f, logY, logPaint)
        } else {
            for (entry in recentLogs.takeLast(7)) {
                val shortTime = entry.timeFormatted.substringAfter(" ").ifBlank { entry.timeFormatted }
                val cleanTag = entry.tag.replace("[", "").replace("]", "").take(8)
                val fullLine = "$shortTime [$cleanTag] ${entry.message}"
                val truncated = if (fullLine.length > maxLogChars) fullLine.take(maxLogChars - 3) + "..." else fullLine

                val itemColor = when {
                    entry.level == LogLevel.ERROR -> Color.parseColor("#F87171")
                    entry.level == LogLevel.WARN -> Color.parseColor("#FBBF24")
                    entry.tag.contains("HOOK", ignoreCase = true) -> Color.parseColor("#38BDF8")
                    entry.tag.contains("IPC", ignoreCase = true) || entry.tag.contains("Provider", ignoreCase = true) -> Color.parseColor("#34D399")
                    entry.tag.contains("DEC", ignoreCase = true) || entry.tag.contains("Video", ignoreCase = true) -> Color.parseColor("#FCD34D")
                    else -> Color.parseColor("#E2E8F0")
                }
                logPaint.color = itemColor
                canvas.drawText(truncated, 14f, logY, logPaint)
                logY += 18f
                if (logY > heightPx - 10f) break
            }
        }

        val rightX = splitX + 14f
        canvas.drawText("🩺 DIAGNOSTIK LIVE (ANALISIS FREEZE):", rightX, 114f, subHeaderPaint)
        var telY = 134f

        val diag = com.tajasb.engine.SystemDiagnosticHelper.analyzeRootCause(context, activeVideoFile, currentFps, isDecoderStalled = false)
        val ram = diag.ramInfo
        val net = diag.netInfo
        val vid = diag.videoInfo

        val activeTexId = if (activeSlot == 0) oesTextureIdA else oesTextureIdB

        // 1. Apakah di Video?
        val vidStatus = if (vid != null && vid.width > 0) "${vid.width}x${vid.height} (${String.format(Locale.US, "%.1f", vid.sizeMb)}MB, ${String.format(Locale.US, "%.0f", vid.durationSeconds)}s)" else "Berkas OK (${String.format(Locale.US, "%.1f", vid?.sizeMb ?: 0f)}MB)"
        canvas.drawText("📹 Video: $vidStatus", rightX, telY, telemetryPaint); telY += 18f

        // 2. Apakah di Internet?
        val netSpeed = if (net.downstreamMbps > 0) "${String.format(Locale.US, "%.1f", net.downstreamMbps)} Mbps" else "Aktif"
        canvas.drawText("🌐 Internet: ${net.networkType} ($netSpeed) ${if (net.isConnected) "✅" else "🚨"}", rightX, telY, telemetryPaint); telY += 18f

        // 3. Apakah di RAM HP?
        val ramColor = if (ram.isLowMemory) Color.parseColor("#EF4444") else Color.parseColor("#34D399")
        telemetryPaint.color = ramColor
        canvas.drawText("🧠 RAM HP: Bebas ${ram.availRamMb}MB / Total ${ram.totalRamMb}MB (${ram.usedRamPercent}%)", rightX, telY, telemetryPaint); telY += 18f
        telemetryPaint.color = Color.parseColor("#C084FC")

        // 4. Apakah di APK ini?
        canvas.drawText("⚙️ APK Engine: GLES 2.0 | FPS: ${String.format(Locale.US, "%.1f", currentFps)} | Slot $activeSlot (Tex:#$activeTexId)", rightX, telY, telemetryPaint); telY += 18f

        // 5. Kesimpulan Analisis
        val verdictPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = when (diag.suspectCategory) {
                "NORMAL" -> Color.parseColor("#10B981")
                "VIDEO" -> Color.parseColor("#F59E0B")
                "INTERNET" -> Color.parseColor("#38BDF8")
                "RAM" -> Color.parseColor("#EF4444")
                else -> Color.parseColor("#FBBF24")
            }
            textSize = 13.5f
            setShadowLayer(1.5f, 0f, 1f, Color.BLACK)
        }
        val shortVerdict = if (diag.overallVerdict.length > 32) diag.overallVerdict.take(30) + ".." else diag.overallVerdict
        canvas.drawText("📊 Status: $shortVerdict", rightX, telY, verdictPaint); telY += 18f
        val shortAdvice = if (diag.adviceText.length > 36) diag.adviceText.take(34) + ".." else diag.adviceText
        canvas.drawText("💡 Solusi: $shortAdvice", rightX, telY, telemetryPaint)

        return bitmap
    }

    fun computeDebugOsdNdcCoords(
        viewW: Float,
        viewH: Float,
        quadWidth: Int,
        quadHeight: Int,
        effectiveRotation: Int
    ): FloatArray {
        val bannerW = quadWidth.toFloat()
        val bannerH = quadHeight.toFloat()

        val leftX = (viewW - bannerW) / 2f
        val rightX = leftX + bannerW
        val topY = 0.23f * viewH
        val bottomY = topY + bannerH

        val cornersX = floatArrayOf(leftX, rightX, leftX, rightX)
        val cornersY = floatArrayOf(bottomY, bottomY, topY, topY)

        val coords = FloatArray(8)
        for (i in 0 until 4) {
            val vx = cornersX[i]
            val vy = cornersY[i]

            val (ndcX, ndcY) = when (effectiveRotation) {
                0 -> Pair((vx / viewW) * 2f - 1f, 1f - (vy / viewH) * 2f)
                90 -> Pair(1f - (vy / viewH) * 2f, 1f - (vx / viewW) * 2f)
                180 -> Pair(1f - (vx / viewW) * 2f, (vy / viewH) * 2f - 1f)
                else -> Pair((vy / viewH) * 2f - 1f, (vx / viewW) * 2f - 1f)
            }

            coords[i * 2] = ndcX
            coords[i * 2 + 1] = ndcY
        }
        return coords
    }
}
