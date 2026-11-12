package com.tajasb.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.tajasb.autotap.AutoTapManager
import com.tajasb.autotap.TapSpeedPreset
import com.tajasb.livecomment.BannerIconMode
import com.tajasb.livecomment.BannerMode
import com.tajasb.livecomment.CommentFormatMode
import com.tajasb.livecomment.LiveCommentManager
import com.tajasb.livecomment.ScanAreaPreset
import com.tajasb.model.HookEngineType
import com.tajasb.model.ResolutionPreset
import com.tajasb.model.ScaleMode
import com.tajasb.notif.AutoTimeCapsuleManager
import com.tajasb.notif.BuyerNotificationManager
import com.tajasb.stiker.CustomStikerManager
import com.tajasb.stiker.CustomWatermarkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * FloatingConfigBackupManager:
 * Manajer untuk Ekspor dan Impor seluruh pengaturan posisi, warna, ukuran, style,
 * modul, dan parameter floating ke/dari format JSON.
 *
 * Memudahkan pengguna mencadangkan pengaturan ke catatan (Notes/WA/Keep)
 * dan mengembalikannya secara instan saat APK di-update tanpa harus menyetel ulang satu per satu.
 */
object FloatingConfigBackupManager {

    private const val TAG = "ConfigBackup"
    const val CURRENT_BACKUP_VERSION = 1

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val updatedModules: List<String> = emptyList()
    )

    /**
     * Mengekspor seluruh konfigurasi mengambang & kamera ke format string JSON berindentasi rapi.
     */
    fun exportConfigToJson(
        context: Context,
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): String {
        val root = JSONObject()

        try {
            root.put("version", CURRENT_BACKUP_VERSION)
            root.put("appName", "Tajasb")
            root.put("exportedAt", System.currentTimeMillis())

            if (options.includeOverlays) {
                val wm = CustomWatermarkManager.getInstance(context)
                val wmObj = JSONObject().apply {
                    put("isEnabled", wm.isEnabled.value)
                    put("watermarkText", wm.watermarkText.value)
                    put("displayStyle", wm.displayStyle.value)
                    put("boxColorHex", wm.boxColorHex.value)
                    put("boxAlpha", wm.boxAlpha.value.toDouble())
                    put("textColorHex", wm.textColorHex.value)
                    put("sizeScale", wm.sizeScale.value.toDouble())
                    put("posXpct", wm.posXpct.value.toDouble())
                    put("posYpct", wm.posYpct.value.toDouble())
                    put("rotation", wm.rotation.value)
                    put("isMirrored", wm.isMirrored.value)
                    put("outlineWidth", wm.outlineWidth.value)
                    put("outlineColorHex", wm.outlineColorHex.value)
                    put("isRunningTextEnabled", wm.isRunningTextEnabled.value)
                    put("runningTextSpeed", wm.runningTextSpeed.value.toDouble())
                    put("runningTextDirection", wm.runningTextDirection.value)
                    put("isRandomMoveEnabled", wm.isRandomMoveEnabled.value)
                }
                root.put("watermark", wmObj)

                val stk = CustomStikerManager.getInstance(context)
                val stkObj = JSONObject().apply {
                    put("isEnabled", stk.isEnabled.value)
                    put("stikerText", stk.stikerText.value)
                    put("displayStyle", stk.displayStyle.value)
                    put("boxColorHex", stk.boxColorHex.value)
                    put("boxAlpha", stk.boxAlpha.value.toDouble())
                    put("textColorHex", stk.textColorHex.value)
                    put("sizeScale", stk.sizeScale.value.toDouble())
                    put("posXpct", stk.posXpct.value.toDouble())
                    put("posYpct", stk.posYpct.value.toDouble())
                    put("rotation", stk.rotation.value)
                    put("isMirrored", stk.isMirrored.value)
                    put("outlineWidth", stk.outlineWidth.value)
                    put("outlineColorHex", stk.outlineColorHex.value)
                    put("isRunningTextEnabled", stk.isRunningTextEnabled.value)
                    put("runningTextSpeed", stk.runningTextSpeed.value.toDouble())
                    put("runningTextDirection", stk.runningTextDirection.value)
                    put("isRandomMoveEnabled", stk.isRandomMoveEnabled.value)
                }
                root.put("stiker", stkObj)

                val bn = BuyerNotificationManager.getInstance(context)
                val bnObj = JSONObject().apply {
                    put("isEnabled", bn.isEnabled.value)
                    put("salesFormat", bn.salesFormat.value)
                    put("productNoMode", bn.productNoMode.value)
                    put("actionText", bn.actionText.value)
                    put("countMin", bn.countMin.value)
                    put("countMax", bn.countMax.value)
                    put("productMin", bn.productMin.value)
                    put("productMax", bn.productMax.value)
                    put("minIntervalSeconds", bn.minIntervalSeconds.value)
                    put("maxIntervalSeconds", bn.maxIntervalSeconds.value)
                    put("displayDurationMs", bn.displayDurationMs.value)
                    put("capsuleBgColorHex", bn.capsuleBgColorHex.value)
                    put("capsuleAlpha", bn.capsuleAlpha.value.toDouble())
                    put("capsuleOutlineWidth", bn.capsuleOutlineWidth.value)
                    put("capsuleOutlineColor", bn.capsuleOutlineColor.value)
                    put("capsuleCornerRadius", bn.capsuleCornerRadius.value)
                    put("capsulePaddingH", bn.capsulePaddingH.value)
                    put("capsulePaddingV", bn.capsulePaddingV.value)
                    put("fontSizeSp", bn.fontSizeSp.value.toDouble())
                    put("cartIconSizeDp", bn.cartIconSizeDp.value.toDouble())
                    put("posX", bn.posX.value)
                    put("posY", bn.posY.value)
                    put("isPositionLocked", bn.isPositionLocked.value)
                    put("scale", bn.scale.value.toDouble())
                    put("rotation", bn.rotation.value)
                    put("isMirrored", bn.isMirrored.value)
                    put("entryDirection", bn.entryDirection.value)
                }
                root.put("buyerNotif", bnObj)

                val lc = LiveCommentManager.getInstance(context)
                val lcObj = JSONObject().apply {
                    put("bannerMode", lc.bannerMode.value.name)
                    put("iconMode", lc.iconMode.value.name)
                    put("formatMode", lc.formatMode.value.name)
                    put("customPrefix", lc.customPrefix.value)
                    put("isCaptureActive", lc.isCaptureActive.value)
                    put("isAreaFilterEnabled", lc.isAreaFilterEnabled.value)
                    put("scanAreaPreset", lc.scanAreaPreset.value.name)
                    put("scanTopPct", lc.scanTopPct.value.toDouble())
                    put("scanBottomPct", lc.scanBottomPct.value.toDouble())
                    put("scanLeftPct", lc.scanLeftPct.value.toDouble())
                    put("scanRightPct", lc.scanRightPct.value.toDouble())
                    put("overlayPosXpct", lc.overlayPosXpct.value.toDouble())
                    put("overlayPosYpct", lc.overlayPosYpct.value.toDouble())
                    put("overlayRotation", lc.overlayRotation.value)
                    put("overlayScale", lc.overlayScale.value.toDouble())
                    put("overlayMirrored", lc.overlayMirrored.value)
                }
                root.put("liveComment", lcObj)

                val at = AutoTimeCapsuleManager.getInstance(context)
                val atObj = JSONObject().apply {
                    put("isEnabled", at.isEnabled.value)
                    put("showSeconds", at.showSeconds.value)
                    put("capsuleBgColorHex", at.capsuleBgColorHex.value)
                    put("capsuleAlpha", at.capsuleAlpha.value.toDouble())
                    put("capsuleCornerRadius", at.capsuleCornerRadius.value)
                    put("capsulePaddingH", at.capsulePaddingH.value)
                    put("capsuleOutlineWidth", at.capsuleOutlineWidth.value)
                    put("capsuleOutlineColorHex", at.capsuleOutlineColorHex.value)
                    put("dateBoxBgColorHex", at.dateBoxBgColorHex.value)
                    put("dateBoxTextColorHex", at.dateBoxTextColorHex.value)
                    put("timeTextColorHex", at.timeTextColorHex.value)
                    put("sizeScale", at.sizeScale.value.toDouble())
                    put("posXpct", at.posXpct.value.toDouble())
                    put("posYpct", at.posYpct.value.toDouble())
                    put("isPositionLocked", at.isPositionLocked.value)
                    put("rotation", at.rotation.value)
                    put("isMirrored", at.isMirrored.value)
                }
                root.put("autoTimeCapsule", atObj)
            }

            if (options.includeAutoTapChoreo) {
                val tap = AutoTapManager.getInstance(context)
                val tapObj = tap.exportToJsonObject()
                root.put("autoTap", tapObj)
            }

            if (options.includeCameraVisual || options.includeFloatingLayout) {
                val tgMon = com.tajasb.remote.TelegramScreenMonitorManager.getInstance(context)
                root.put("telegramScreenMonitor", tgMon.exportToJsonObject())
            }

            if (options.includeCameraVisual || options.includeFloatingLayout || options.includeOverlays || options.includeAutoTapChoreo) {
                val repo = VcamRepository.getInstance(context)
                val cfg = repo.config.value
                val genObj = JSONObject().apply {
                    if (options.includeFloatingLayout) {
                        put("floatingAlpha", cfg.floatingAlpha.toDouble())
                    }
                    if (options.includeCameraVisual) {
                        put("hookEngine", cfg.hookEngine.name)
                        put("forceResolution", cfg.forceResolution.name)
                        put("scaleMode", cfg.scaleMode.name)
                        put("rotation", cfg.rotation)
                        put("isMirrored", cfg.isMirrored)
                        put("isVerticalFlipped", cfg.isVerticalFlipped)
                        put("zoomFactor", cfg.zoomFactor.toDouble())
                        put("isLightweightMode", cfg.isLightweightMode)
                        put("isDebugOsdInVirtualCameraEnabled", cfg.isDebugOsdInVirtualCameraEnabled)
                    }
                    if (options.includeVideos) {
                        put("isMasterSpillMode", cfg.isMasterSpillMode)
                        put("masterSpillWaitFinish", cfg.masterSpillWaitFinish)
                        put("autoSpillFromComments", cfg.autoSpillFromComments)
                    }
                    if (options.includeOverlays) {
                        put("enableSalesNotifModule", cfg.enableSalesNotifModule)
                        put("enableStickerModule", cfg.enableStickerModule)
                        put("enableWatermarkModule", cfg.enableWatermarkModule)
                        put("enableAutoTimeModule", cfg.enableAutoTimeModule)
                        put("enablePrayerCommentModule", cfg.enablePrayerCommentModule)
                        put("enableQuranKhatamModule", cfg.enableQuranKhatamModule)
                        put("enableMakkahRelayModule", cfg.enableMakkahRelayModule)
                        put("enableLiveEventBrokerModule", cfg.enableLiveEventBrokerModule)
                        put("enableScanInspectorModule", cfg.enableScanInspectorModule)
                        put("enableOsdModule", cfg.enableOsdModule)
                    }
                    if (options.includeAutoTapChoreo) {
                        put("enableAutoTapModule", cfg.enableAutoTapModule)
                    }
                    if (options.includeAudios) {
                        put("enableMp3MusicModule", cfg.enableMp3MusicModule)
                        put("enableQuranAudioModule", cfg.enableQuranAudioModule)
                    }
                }
                root.put("floatingGeneral", genObj)
            }

            return root.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengekspor konfigurasi JSON", e)
            return "{}"
        }
    }

    /**
     * Menyalin string teks ke papan klip perangkat.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "Tajasb Settings JSON"): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyalin ke clipboard", e)
            false
        }
    }

    /**
     * Membaca teks dari papan klip perangkat jika tersedia.
     */
    fun readFromClipboard(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0)?.coerceToText(context)?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membaca dari clipboard", e)
            null
        }
    }

    /**
     * Mengimpor konfigurasi dari string JSON dan memperbarui seluruh manajer & preferensi.
     */
    fun importConfigFromJson(
        context: Context,
        jsonStr: String,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): ImportResult {
        var raw = jsonStr.trim()
        if (raw.isEmpty()) {
            return ImportResult(false, "Teks JSON kosong. Silakan tempel teks pengaturan.")
        }

        // Pembersih Cerdas (Fault-Tolerant Sanitizer):
        // Jika teks diawali tanda ':{', markdown ```json, atau teks obrolan,
        // cari dan potong hanya blok JSON valid dari '{' pertama sampai '}' terakhir.
        val firstBrace = raw.indexOf('{')
        val lastBrace = raw.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            raw = raw.substring(firstBrace, lastBrace + 1)
        }

        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return ImportResult(false, "Format JSON tidak valid: ${e.localizedMessage}")
        }

        val updatedModules = mutableListOf<String>()

        try {
            // 1. Watermark
            if (options.includeOverlays && root.has("watermark")) {
                val wmObj = root.getJSONObject("watermark")
                val wm = CustomWatermarkManager.getInstance(context)

                if (wmObj.has("watermarkText")) wm.setWatermarkText(wmObj.getString("watermarkText"))
                if (wmObj.has("displayStyle")) wm.setDisplayStyle(wmObj.getString("displayStyle"))
                if (wmObj.has("boxColorHex")) wm.setBoxColor(wmObj.getString("boxColorHex"))
                if (wmObj.has("boxAlpha")) wm.setBoxAlpha(wmObj.getDouble("boxAlpha").toFloat())
                if (wmObj.has("textColorHex")) wm.setTextColor(wmObj.getString("textColorHex"))
                if (wmObj.has("sizeScale")) wm.setSizeScale(wmObj.getDouble("sizeScale").toFloat())
                if (wmObj.has("posXpct") && wmObj.has("posYpct")) {
                    wm.setPosition(wmObj.getDouble("posXpct").toFloat(), wmObj.getDouble("posYpct").toFloat())
                }
                if (wmObj.has("rotation")) wm.setRotation(wmObj.getInt("rotation"))
                if (wmObj.has("isMirrored")) wm.toggleMirror(wmObj.getBoolean("isMirrored"))
                if (wmObj.has("outlineWidth")) wm.setOutlineWidth(wmObj.getInt("outlineWidth"))
                if (wmObj.has("outlineColorHex")) wm.setOutlineColor(wmObj.getString("outlineColorHex"))
                if (wmObj.has("isRunningTextEnabled")) wm.toggleRunningText(wmObj.getBoolean("isRunningTextEnabled"))
                if (wmObj.has("runningTextSpeed")) wm.setRunningTextSpeed(wmObj.getDouble("runningTextSpeed").toFloat())
                if (wmObj.has("runningTextDirection")) wm.setRunningTextDirection(wmObj.getString("runningTextDirection"))
                if (wmObj.has("isRandomMoveEnabled")) wm.toggleRandomMove(wmObj.getBoolean("isRandomMoveEnabled"))
                if (wmObj.has("isEnabled")) wm.toggleEnabled(wmObj.getBoolean("isEnabled"))

                updatedModules.add("Watermark Kamera")
            }

            // 2. Stiker
            if (options.includeOverlays && root.has("stiker")) {
                val stkObj = root.getJSONObject("stiker")
                val stk = CustomStikerManager.getInstance(context)

                if (stkObj.has("stikerText")) stk.setStikerText(stkObj.getString("stikerText"))
                if (stkObj.has("displayStyle")) stk.setDisplayStyle(stkObj.getString("displayStyle"))
                if (stkObj.has("boxColorHex")) stk.setBoxColor(stkObj.getString("boxColorHex"))
                if (stkObj.has("boxAlpha")) stk.setBoxAlpha(stkObj.getDouble("boxAlpha").toFloat())
                if (stkObj.has("textColorHex")) stk.setTextColor(stkObj.getString("textColorHex"))
                if (stkObj.has("sizeScale")) stk.setSizeScale(stkObj.getDouble("sizeScale").toFloat())
                if (stkObj.has("posXpct") && stkObj.has("posYpct")) {
                    stk.setPosition(stkObj.getDouble("posXpct").toFloat(), stkObj.getDouble("posYpct").toFloat())
                }
                if (stkObj.has("rotation")) stk.setRotation(stkObj.getInt("rotation"))
                if (stkObj.has("isMirrored")) stk.toggleMirror(stkObj.getBoolean("isMirrored"))
                if (stkObj.has("outlineWidth")) stk.setOutlineWidth(stkObj.getInt("outlineWidth"))
                if (stkObj.has("outlineColorHex")) stk.setOutlineColor(stkObj.getString("outlineColorHex"))
                if (stkObj.has("isRunningTextEnabled")) stk.toggleRunningText(stkObj.getBoolean("isRunningTextEnabled"))
                if (stkObj.has("runningTextSpeed")) stk.setRunningTextSpeed(stkObj.getDouble("runningTextSpeed").toFloat())
                if (stkObj.has("runningTextDirection")) stk.setRunningTextDirection(stkObj.getString("runningTextDirection"))
                if (stkObj.has("isRandomMoveEnabled")) stk.toggleRandomMove(stkObj.getBoolean("isRandomMoveEnabled"))
                if (stkObj.has("isEnabled")) stk.toggleEnabled(stkObj.getBoolean("isEnabled"))

                updatedModules.add("Stiker Promo")
            }

            // 3. Buyer Notif (Notif Jualan)
            if (options.includeOverlays && root.has("buyerNotif")) {
                val bnObj = root.getJSONObject("buyerNotif")
                val bn = BuyerNotificationManager.getInstance(context)

                if (bnObj.has("salesFormat")) bn.setSalesFormat(bnObj.getString("salesFormat"))
                if (bnObj.has("actionText")) bn.setActionText(bnObj.getString("actionText"))
                if (bnObj.has("countMin") && bnObj.has("countMax")) {
                    bn.setCountMinMax(bnObj.getInt("countMin"), bnObj.getInt("countMax"))
                }
                if (bnObj.has("productMin") && bnObj.has("productMax")) {
                    bn.setProductMinMax(bnObj.getInt("productMin"), bnObj.getInt("productMax"))
                } else if (bnObj.has("productNoMode")) {
                    bn.setProductNoMode(bnObj.getString("productNoMode"))
                }
                if (bnObj.has("minIntervalSeconds") && bnObj.has("maxIntervalSeconds")) {
                    bn.setIntervalRange(bnObj.getInt("minIntervalSeconds"), bnObj.getInt("maxIntervalSeconds"))
                }
                if (bnObj.has("displayDurationMs")) bn.setDisplayDurationMs(bnObj.getLong("displayDurationMs"))
                if (bnObj.has("capsuleBgColorHex")) bn.setCapsuleBgColor(bnObj.getString("capsuleBgColorHex"))
                if (bnObj.has("capsuleAlpha")) bn.setCapsuleAlpha(bnObj.getDouble("capsuleAlpha").toFloat())
                if (bnObj.has("capsuleOutlineWidth")) bn.setCapsuleOutlineWidth(bnObj.getInt("capsuleOutlineWidth"))
                if (bnObj.has("capsuleOutlineColor")) bn.setCapsuleOutlineColor(bnObj.getString("capsuleOutlineColor"))
                if (bnObj.has("capsuleCornerRadius")) bn.setCapsuleCornerRadius(bnObj.getInt("capsuleCornerRadius"))
                if (bnObj.has("capsulePaddingH")) bn.setCapsulePaddingH(bnObj.getInt("capsulePaddingH"))
                if (bnObj.has("capsulePaddingV")) bn.setCapsulePaddingV(bnObj.getInt("capsulePaddingV"))
                if (bnObj.has("fontSizeSp")) bn.setFontSizeSp(bnObj.getDouble("fontSizeSp").toFloat())
                if (bnObj.has("cartIconSizeDp")) bn.setCartIconSizeDp(bnObj.getDouble("cartIconSizeDp").toFloat())
                if (bnObj.has("posX") && bnObj.has("posY")) {
                    bn.savePosition(bnObj.getInt("posX"), bnObj.getInt("posY"))
                }
                if (bnObj.has("isPositionLocked")) bn.setPositionLocked(bnObj.getBoolean("isPositionLocked"))
                if (bnObj.has("scale")) bn.setScale(bnObj.getDouble("scale").toFloat())
                if (bnObj.has("rotation")) bn.setRotation(bnObj.getInt("rotation"))
                if (bnObj.has("isMirrored")) bn.toggleMirror(bnObj.getBoolean("isMirrored"))
                if (bnObj.has("entryDirection")) bn.setEntryDirection(bnObj.getString("entryDirection"))
                if (bnObj.has("isEnabled")) bn.toggleEnabled(bnObj.getBoolean("isEnabled"))

                updatedModules.add("Notif Jualan")
            }

            // 4. Live Comment (Doa & Komentar)
            if (options.includeOverlays && root.has("liveComment")) {
                val lcObj = root.getJSONObject("liveComment")
                val lc = LiveCommentManager.getInstance(context)

                if (lcObj.has("bannerMode")) {
                    try { lc.setBannerMode(BannerMode.valueOf(lcObj.getString("bannerMode"))) } catch (_: Exception) {}
                }
                if (lcObj.has("iconMode")) {
                    try { lc.setIconMode(BannerIconMode.valueOf(lcObj.getString("iconMode"))) } catch (_: Exception) {}
                }
                if (lcObj.has("formatMode")) {
                    try { lc.setFormatMode(CommentFormatMode.valueOf(lcObj.getString("formatMode"))) } catch (_: Exception) {}
                }
                if (lcObj.has("customPrefix")) lc.setCustomPrefix(lcObj.getString("customPrefix"))
                if (lcObj.has("isAreaFilterEnabled")) lc.setAreaFilterEnabled(lcObj.getBoolean("isAreaFilterEnabled"))
                if (lcObj.has("scanAreaPreset")) {
                    try { lc.setScanAreaPreset(ScanAreaPreset.valueOf(lcObj.getString("scanAreaPreset"))) } catch (_: Exception) {}
                }
                if (lcObj.has("scanTopPct") && lcObj.has("scanBottomPct")) {
                    val lPct = if (lcObj.has("scanLeftPct")) lcObj.getDouble("scanLeftPct").toFloat() else 0.00f
                    val rPct = if (lcObj.has("scanRightPct")) lcObj.getDouble("scanRightPct").toFloat() else 0.80f
                    lc.setCustomScanArea(
                        top = lcObj.getDouble("scanTopPct").toFloat(),
                        bottom = lcObj.getDouble("scanBottomPct").toFloat(),
                        left = lPct,
                        right = rPct,
                        updatePreset = false
                    )
                }
                if (lcObj.has("overlayPosXpct") && lcObj.has("overlayPosYpct")) {
                    lc.setOverlayPosition(
                        lcObj.getDouble("overlayPosXpct").toFloat(),
                        lcObj.getDouble("overlayPosYpct").toFloat(),
                        preview = false
                    )
                }
                if (lcObj.has("overlayRotation")) lc.setOverlayRotation(lcObj.getInt("overlayRotation"))
                if (lcObj.has("overlayScale")) lc.setOverlayScale(lcObj.getDouble("overlayScale").toFloat(), preview = false)
                if (lcObj.has("overlayMirrored")) lc.setOverlayMirrored(lcObj.getBoolean("overlayMirrored"), preview = false)
                if (lcObj.has("isCaptureActive")) lc.toggleCaptureActive(lcObj.getBoolean("isCaptureActive"))

                updatedModules.add("Doa & Komentar")
            }

            // 4B. Auto Time Capsule (Waktu Otomatis)
            if (options.includeOverlays && root.has("autoTimeCapsule")) {
                val atObj = root.getJSONObject("autoTimeCapsule")
                val at = AutoTimeCapsuleManager.getInstance(context)

                if (atObj.has("showSeconds")) at.toggleShowSeconds(atObj.getBoolean("showSeconds"))
                if (atObj.has("capsuleBgColorHex")) at.setCapsuleBgColor(atObj.getString("capsuleBgColorHex"))
                if (atObj.has("capsuleAlpha")) at.setCapsuleAlpha(atObj.getDouble("capsuleAlpha").toFloat())
                if (atObj.has("capsuleCornerRadius")) at.setCapsuleCornerRadius(atObj.getInt("capsuleCornerRadius"))
                if (atObj.has("capsulePaddingH")) at.setCapsulePaddingH(atObj.getInt("capsulePaddingH"))
                if (atObj.has("capsuleOutlineWidth")) at.setCapsuleOutlineWidth(atObj.getInt("capsuleOutlineWidth"))
                if (atObj.has("capsuleOutlineColorHex")) at.setCapsuleOutlineColor(atObj.getString("capsuleOutlineColorHex"))
                if (atObj.has("dateBoxBgColorHex")) at.setDateBoxBgColor(atObj.getString("dateBoxBgColorHex"))
                if (atObj.has("dateBoxTextColorHex")) at.setDateBoxTextColor(atObj.getString("dateBoxTextColorHex"))
                if (atObj.has("timeTextColorHex")) at.setTimeTextColor(atObj.getString("timeTextColorHex"))
                if (atObj.has("sizeScale")) at.setSizeScale(atObj.getDouble("sizeScale").toFloat())
                if (atObj.has("isPositionLocked")) at.togglePositionLock(false)
                if (atObj.has("posXpct") && atObj.has("posYpct")) {
                    at.setPosition(atObj.getDouble("posXpct").toFloat(), atObj.getDouble("posYpct").toFloat())
                }
                if (atObj.has("isPositionLocked")) at.togglePositionLock(atObj.getBoolean("isPositionLocked"))
                if (atObj.has("rotation")) at.setRotation(atObj.getInt("rotation"))
                if (atObj.has("isMirrored")) at.toggleMirror(atObj.getBoolean("isMirrored"))
                if (atObj.has("isEnabled")) at.toggleEnabled(atObj.getBoolean("isEnabled"))

                updatedModules.add("Waktu Otomatis")
            }

            // 5. Auto-Tap & Multi-Mark Sequence
            if (options.includeAutoTapChoreo && root.has("autoTap")) {
                val tapObj = root.getJSONObject("autoTap")
                val tap = AutoTapManager.getInstance(context)
                tap.importFromJsonObject(tapObj)
                updatedModules.add("Auto-Tap & Multi-Mark")
            }

            // 5B. Telegram Screen Monitor (2.5m)
            if ((options.includeCameraVisual || options.includeFloatingLayout) && root.has("telegramScreenMonitor")) {
                val tgObj = root.getJSONObject("telegramScreenMonitor")
                val tgMon = com.tajasb.remote.TelegramScreenMonitorManager.getInstance(context)
                tgMon.importFromJsonObject(tgObj)
                updatedModules.add("Pantau Layar Telegram")
            }

            // 6. Floating & General Settings
            if (root.has("floatingGeneral") &&
                (options.includeCameraVisual || options.includeFloatingLayout || options.includeOverlays || options.includeAutoTapChoreo || options.includeVideos || options.includeAudios)
            ) {
                val genObj = root.getJSONObject("floatingGeneral")
                val repo = VcamRepository.getInstance(context)

                coroutineScope.launch {
                    repo.updateConfig { cur ->
                        var updated = cur

                        if (options.includeFloatingLayout && genObj.has("floatingAlpha")) {
                            updated = updated.copy(floatingAlpha = genObj.getDouble("floatingAlpha").toFloat())
                        }
                        if (options.includeCameraVisual) {
                            if (genObj.has("hookEngine")) {
                                try { updated = updated.copy(hookEngine = HookEngineType.valueOf(genObj.getString("hookEngine"))) } catch (_: Exception) {}
                            }
                            if (genObj.has("forceResolution")) {
                                try { updated = updated.copy(forceResolution = ResolutionPreset.valueOf(genObj.getString("forceResolution"))) } catch (_: Exception) {}
                            }
                            if (genObj.has("scaleMode")) {
                                try { updated = updated.copy(scaleMode = ScaleMode.valueOf(genObj.getString("scaleMode"))) } catch (_: Exception) {}
                            }
                            if (genObj.has("rotation")) {
                                updated = updated.copy(rotation = genObj.getInt("rotation"))
                            }
                            if (genObj.has("isMirrored")) {
                                updated = updated.copy(isMirrored = genObj.getBoolean("isMirrored"))
                            }
                            if (genObj.has("isVerticalFlipped")) {
                                updated = updated.copy(isVerticalFlipped = genObj.getBoolean("isVerticalFlipped"))
                            }
                            if (genObj.has("zoomFactor")) {
                                updated = updated.copy(zoomFactor = genObj.getDouble("zoomFactor").toFloat().coerceIn(0.5f, 3.0f))
                            }
                            if (genObj.has("isLightweightMode")) {
                                updated = updated.copy(isLightweightMode = genObj.getBoolean("isLightweightMode"))
                            }
                            if (genObj.has("isDebugOsdInVirtualCameraEnabled")) {
                                updated = updated.copy(isDebugOsdInVirtualCameraEnabled = genObj.getBoolean("isDebugOsdInVirtualCameraEnabled"))
                            }
                        }
                        if (options.includeVideos) {
                            if (genObj.has("isMasterSpillMode")) {
                                updated = updated.copy(isMasterSpillMode = genObj.getBoolean("isMasterSpillMode"))
                            }
                            if (genObj.has("masterSpillWaitFinish")) {
                                updated = updated.copy(masterSpillWaitFinish = genObj.getBoolean("masterSpillWaitFinish"))
                            }
                            if (genObj.has("autoSpillFromComments")) {
                                updated = updated.copy(autoSpillFromComments = genObj.getBoolean("autoSpillFromComments"))
                            }
                        }
                        if (options.includeOverlays) {
                            if (genObj.has("enableSalesNotifModule")) {
                                updated = updated.copy(enableSalesNotifModule = genObj.getBoolean("enableSalesNotifModule"))
                            }
                            if (genObj.has("enableStickerModule")) {
                                updated = updated.copy(enableStickerModule = genObj.getBoolean("enableStickerModule"))
                            }
                            if (genObj.has("enableWatermarkModule")) {
                                updated = updated.copy(enableWatermarkModule = genObj.getBoolean("enableWatermarkModule"))
                            }
                            if (genObj.has("enableAutoTimeModule")) {
                                updated = updated.copy(enableAutoTimeModule = genObj.getBoolean("enableAutoTimeModule"))
                            }
                            if (genObj.has("enablePrayerCommentModule")) {
                                updated = updated.copy(enablePrayerCommentModule = genObj.getBoolean("enablePrayerCommentModule"))
                            }
                            if (genObj.has("enableQuranKhatamModule")) {
                                updated = updated.copy(enableQuranKhatamModule = genObj.getBoolean("enableQuranKhatamModule"))
                            }
                            if (genObj.has("enableMakkahRelayModule")) {
                                updated = updated.copy(enableMakkahRelayModule = genObj.getBoolean("enableMakkahRelayModule"))
                            }
                            if (genObj.has("enableLiveEventBrokerModule")) {
                                updated = updated.copy(enableLiveEventBrokerModule = genObj.getBoolean("enableLiveEventBrokerModule"))
                            }
                            if (genObj.has("enableScanInspectorModule")) {
                                updated = updated.copy(enableScanInspectorModule = genObj.getBoolean("enableScanInspectorModule"))
                            }
                            if (genObj.has("enableOsdModule")) {
                                updated = updated.copy(enableOsdModule = genObj.getBoolean("enableOsdModule"))
                            }
                        }
                        if (options.includeAutoTapChoreo && genObj.has("enableAutoTapModule")) {
                            updated = updated.copy(enableAutoTapModule = genObj.getBoolean("enableAutoTapModule"))
                        }
                        if (options.includeAudios && genObj.has("enableMp3MusicModule")) {
                            updated = updated.copy(enableMp3MusicModule = genObj.getBoolean("enableMp3MusicModule"))
                        }
                        if (options.includeAudios && genObj.has("enableQuranAudioModule")) {
                            updated = updated.copy(enableQuranAudioModule = genObj.getBoolean("enableQuranAudioModule"))
                        }

                        updated
                    }
                    repo.broadcastControlEvent(action = "UPDATE_CONFIG")
                }

                updatedModules.add("Pengaturan Floating & Kamera")
            }

            if (updatedModules.isEmpty()) {
                return ImportResult(false, "Tidak ada bagian konfigurasi yang dikenali atau dipilih di dalam teks JSON ini.")
            }

            return ImportResult(
                success = true,
                message = "Berhasil memperbarui: ${updatedModules.joinToString(", ")}",
                updatedModules = updatedModules
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memproses impor konfigurasi JSON", e)
            return ImportResult(false, "Terjadi kesalahan saat menerapkan pengaturan: ${e.localizedMessage}")
        }
    }
}
