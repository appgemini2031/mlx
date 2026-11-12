package com.tajasb.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.tajasb.data.VcamRepository
import com.tajasb.model.AutoNextMode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * VcamContentProvider:
 * Allows target apps (TikTok, Instagram, WhatsApp, Bank apps, etc.) running in sandboxes
 * to access virtual.mp4 and audio.mp3 cross-process without Scoped Storage permission issues.
 * Also provides synchronous IPC via call() for instant auto-next and playlist synchronization.
 */
class VcamContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.tajasb.provider"
        private const val TAG = "VcamContentProvider"
    }

    override fun onCreate(): Boolean {
        return true
    }

    private fun isCallerAuthorized(): Boolean {
        val ctx = context ?: return false
        val callingUid = android.os.Binder.getCallingUid()
        val myUid = android.os.Process.myUid()
        if (callingUid == myUid || callingUid == 0 || callingUid == 1000) {
            return true
        }

        val pm = ctx.packageManager
        val uidPackages = try { pm.getPackagesForUid(callingUid)?.toList() ?: emptyList() } catch (_: Throwable) { emptyList() }
        val callerPkg = callingPackage ?: uidPackages.firstOrNull() ?: ""
        if (callerPkg == ctx.packageName || uidPackages.contains(ctx.packageName)) {
            return true
        }

        // Izinkan penuh MochiCloner dan ruang kloning MochiCloner agar kamera virtual & IPC berjalan lancar
        if (com.tajasb.engine.DecoyEngine.isMochiClonerPackage(callerPkg) ||
            com.tajasb.engine.DecoyEngine.isCallerFromMochiCloner(ctx, callingUid, callerPkg)
        ) {
            return true
        }

        try {
            val repo = VcamRepository.getInstance(ctx)
            val cfg = repo.config.value
            val primary = cfg.primaryTargetPackage
            if (primary.isNotBlank() && (callerPkg == primary || uidPackages.contains(primary))) {
                return true
            }
            if ((callerPkg.isNotBlank() && cfg.targetPackages.contains(callerPkg)) ||
                uidPackages.any { cfg.targetPackages.contains(it) }
            ) {
                return true
            }

            val blockedTamperPackages = listOf(
                "com.guoshi.httpcanary",
                "com.reqable.android",
                "bin.mt.plus",
                "com.chelpus.lackypatch",
                "catch_.me_.if_.you_.can_",
                "eu.faircode.xlua",
                "com.variable.apkhook"
            )
            if (blockedTamperPackages.any { callerPkg.contains(it, ignoreCase = true) }) {
                Log.w(TAG, "🚨 Blocked tamper/sniffer package from VcamContentProvider: $callerPkg")
                return false
            }

            val knownTargetPrefixes = listOf(
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.ss.android.ugc.aweme",
                "com.shopee.",
                "com.tokopedia.",
                "com.lazada.",
                "sg.bigo.",
                "sg.bigo.live",
                "sg.bigo.lite",
                "com.instagram.android",
                "com.facebook.",
                "com.google.android.youtube",
                "com.whatsapp",
                "org.telegram.messenger",
                "us.zoom.",
                "com.mochi",
                "id.mochi",
                "com.excelliance.",
                "com.lbe.parallel",
                "com.ludashi.",
                "com.polestar.",
                "io.va.",
                "top.niunaijun."
            )
            val allCallers = (listOf(callerPkg) + uidPackages).filter { it.isNotBlank() }
            if (allCallers.any { pkg -> knownTargetPrefixes.any { pkg.startsWith(it) || pkg.contains(it) } }) {
                return true
            }
        } catch (_: Throwable) {}

        return false
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!isCallerAuthorized()) {
            Log.w(TAG, "Blocked unauthorized call($method) from UID ${android.os.Binder.getCallingUid()}, package: $callingPackage")
            return Bundle().apply {
                putBoolean("success", false)
                putBoolean("error", true)
                putString("message", "Unauthorized IPC access")
            }
        }
        return try {
            val repo = VcamRepository.getInstance(ctx)
            when (method) {
                "bigoGetConfig" -> {
                    val bCfg = com.tajasb.hooks.bigo.BigoConfigStore.getEffectiveConfig(ctx)
                    Bundle().apply {
                        putBoolean("success", true)
                        putBoolean("enabled", bCfg.enabled)
                        putString("videoPath", bCfg.videoPath)
                        putString("audioPath", bCfg.audioPath)
                        putInt("rotation", bCfg.rotation)
                        putBoolean("isMirrored", bCfg.isMirrored)
                        putBoolean("isVerticalFlipped", bCfg.isVerticalFlipped)
                        putFloat("zoomFactor", bCfg.zoomFactor)
                        putString("scaleMode", bCfg.scaleMode)
                        putBoolean("isPaused", bCfg.isPaused)
                        putBoolean("isMuted", bCfg.isMuted)
                        putBoolean("isVideoAudioMuted", bCfg.isVideoAudioMuted)
                        putBoolean("isMp3AudioPaused", bCfg.isMp3AudioPaused)
                        putInt("fps", bCfg.fps)
                        putLong("version", bCfg.version)
                    }
                }
                "bigoSaveConfig" -> {
                    val cur = com.tajasb.hooks.bigo.BigoConfigStore.getConfig(ctx)
                    val updated = cur.copy(
                        enabled = extras?.getBoolean("enabled", cur.enabled) ?: cur.enabled,
                        videoPath = extras?.getString("videoPath") ?: cur.videoPath,
                        audioPath = extras?.getString("audioPath") ?: cur.audioPath,
                        rotation = extras?.getInt("rotation", cur.rotation) ?: cur.rotation,
                        isMirrored = extras?.getBoolean("isMirrored", cur.isMirrored) ?: cur.isMirrored,
                        isVerticalFlipped = extras?.getBoolean("isVerticalFlipped", cur.isVerticalFlipped) ?: cur.isVerticalFlipped,
                        zoomFactor = extras?.getFloat("zoomFactor", cur.zoomFactor) ?: cur.zoomFactor,
                        scaleMode = extras?.getString("scaleMode") ?: cur.scaleMode,
                        isPaused = extras?.getBoolean("isPaused", cur.isPaused) ?: cur.isPaused,
                        isMuted = extras?.getBoolean("isMuted", cur.isMuted) ?: cur.isMuted,
                        isVideoAudioMuted = extras?.getBoolean("isVideoAudioMuted", cur.isVideoAudioMuted) ?: cur.isVideoAudioMuted,
                        isMp3AudioPaused = extras?.getBoolean("isMp3AudioPaused", cur.isMp3AudioPaused) ?: cur.isMp3AudioPaused,
                        fps = extras?.getInt("fps", cur.fps) ?: cur.fps
                    )
                    val saved = com.tajasb.hooks.bigo.BigoConfigStore.saveConfig(ctx, updated, broadcastUpdate = true)
                    Bundle().apply {
                        putBoolean("success", true)
                        putLong("version", saved.version)
                    }
                }
                "onVideoCompleted", "nextVideo" -> {
                    val cfg = repo.config.value
                    if (cfg.playlist.isEmpty()) return null
                    val prevVideoPath = cfg.currentVideo?.path ?: cfg.videoPath
                    val prevIndex = cfg.currentVideoIndex

                    val ifsManager = com.tajasb.infinity.IfsManager.getInstance(ctx)
                    val isIfs = ifsManager.isIfsActive.value

                    val nextItem = if (isIfs) {
                        val completedPath = extras?.getString("completedVideoPath")
                        val ifsItem = ifsManager.onVideoCompleted(
                            broadcastToHook = false,
                            completedVideoPath = completedPath
                        )
                        if (ifsItem != null) {
                            if (ifsItem.id.startsWith("vs_rolling_")) {
                                if (repo.config.value.currentVideo?.id != ifsItem.id) {
                                    repo.selectRollingVsVideoSync(ifsItem, broadcastToHook = false)
                                }
                            } else {
                                val idx = repo.config.value.playlist.indexOfFirst { it.id == ifsItem.id }
                                if (idx >= 0 && repo.config.value.currentVideoIndex != idx) {
                                    runBlocking {
                                        repo.syncVideoIndexWithoutBroadcast(idx, broadcastPreload = false)
                                    }
                                }
                            }
                        }
                        ifsItem ?: repo.config.value.currentVideo
                    } else if (cfg.isBridgeCycleMode) {
                        runBlocking {
                            repo.advanceBridgeCycle(broadcastToHook = false)
                        }
                    } else if (cfg.isMasterSpillMode) {
                        runBlocking {
                            repo.advanceMasterSpill(broadcastToHook = false)
                        }
                    } else {
                        val mode = when (arg ?: extras?.getString("mode")) {
                            "SINGLE_LOOP", "LOOP" -> AutoNextMode.SINGLE_LOOP
                            "RANDOM" -> AutoNextMode.RANDOM
                            "SEQUENTIAL" -> AutoNextMode.SEQUENTIAL
                            else -> cfg.autoNextMode
                        }
                        runBlocking {
                            repo.nextVideo(mode, broadcastToHook = false)
                        }
                    }
                    val updatedCfg = repo.config.value
                    val nextVideoPath = nextItem?.path ?: updatedCfg.videoPath
                    val isSameVideoLoop = prevVideoPath.isNotBlank() &&
                        prevVideoPath == nextVideoPath &&
                        prevIndex == updatedCfg.currentVideoIndex &&
                        !nextVideoPath.contains("vs_rolling_")
                    Log.i(TAG, "call(onVideoCompleted) processed: ifs=$isIfs, sameLoop=$isSameVideoLoop, masterSpill=${updatedCfg.isMasterSpillMode}, bridgeMode=${updatedCfg.isBridgeCycleMode}, nextIndex=${updatedCfg.currentVideoIndex} of ${updatedCfg.playlist.size}, video=${nextItem?.name}")
                    val shouldLoopNative = when {
                        isIfs -> ifsManager.isCurrentPhaseLooping()
                        updatedCfg.isBridgeCycleMode -> false
                        updatedCfg.isMasterSpillMode -> false
                        else -> (!updatedCfg.autoNextEnabled || updatedCfg.autoNextMode == AutoNextMode.SINGLE_LOOP)
                    }
                    val nextFileName = nextItem?.path?.let { File(it).name } ?: ""
                    val resolvedUri = if (nextFileName.startsWith("vs_rolling_") && nextFileName.endsWith(".mp4")) {
                        "content://$AUTHORITY/$nextFileName"
                    } else {
                        "content://$AUTHORITY/virtual.mp4"
                    }
                    Bundle().apply {
                        putBoolean("success", true)
                        putInt("nextIndex", updatedCfg.currentVideoIndex)
                        putInt("totalCount", updatedCfg.playlist.size)
                        putString("videoName", nextItem?.name ?: "")
                        putString("videoPath", nextItem?.path ?: "")
                        putBoolean("isSameVideoLoop", isSameVideoLoop)
                        putBoolean("isMuted", nextItem?.isMuted ?: updatedCfg.isVideoAudioMuted)
                        putString("uri", resolvedUri)
                        putLong("version", repo.playlistVersion)
                        putBoolean("isIfsActive", isIfs)
                        putString("ifsPhase", ifsManager.ifsState.value.activeSubPhase.name)
                        putInt("ifsVtNumber", ifsManager.ifsState.value.activeVtNumber)
                        putBoolean("isMasterSpillMode", updatedCfg.isMasterSpillMode)
                        putBoolean("isCurrentPlayingMaster", updatedCfg.isCurrentPlayingMaster)
                        putBoolean("isCurrentPlayingSpill", updatedCfg.isCurrentPlayingSpill)
                        putBoolean("isBridgeCycleMode", updatedCfg.isBridgeCycleMode)
                        putString("currentBridgePhase", updatedCfg.currentBridgePhase.name)
                        putBoolean("shouldLoopNative", shouldLoopNative)
                        putInt("sessionDnaIndex", updatedCfg.sessionDnaIndex)
                        putLong("sessionDnaSeed", updatedCfg.sessionDnaSeed)
                    }
                }
                "getConfig" -> {
                    val callingUid = android.os.Binder.getCallingUid()
                    val myUid = android.os.Process.myUid()
                    val isCameraActive = extras?.getBoolean("isCameraActive", false) ?: false
                    if (callingUid != myUid && isCameraActive) {
                        repo.notifyCameraStreamHeartbeat()
                    }
                    val cfg = repo.config.value
                    val curVid = cfg.currentVideo
                    val ifsManager = com.tajasb.infinity.IfsManager.getInstance(ctx)
                    val isIfs = ifsManager.isIfsActive.value || cfg.isIfsMode
                    val effAutoNext = if (isIfs) false else cfg.autoNextEnabled
                    val isSv4Rolling = isIfs && ifsManager.videoMode.value == com.tajasb.infinity.IfsVideoMode.SV4 && cfg.videoPath.contains("vs_rolling_")
                    Bundle().apply {
                        putBoolean("isIfsActive", isIfs)
                        putBoolean("isIfsMode", isIfs)
                        putBoolean("isIfsLoopingNative", ifsManager.isCurrentPhaseLooping())
                        putBoolean("autoNextEnabled", effAutoNext)
                        putString("autoNextMode", if (isIfs) "SINGLE_LOOP" else cfg.autoNextMode.name)
                        putInt("autoNextLeadSeconds", cfg.autoNextLeadSeconds)
                        putInt("autoNextLeadMs", cfg.autoNextLeadMs)
                        putString("primaryTargetPackage", cfg.primaryTargetPackage)
                        putInt("currentIndex", cfg.currentVideoIndex)
                        putInt("pendingIndex", repo.pendingVideoIndex.value ?: -1)
                        putInt("playlistCount", cfg.playlist.size)
                        putString("videoPath", if (isSv4Rolling) cfg.videoPath else (curVid?.path ?: cfg.videoPath))
                        putString("videoName", if (isSv4Rolling) (ifsManager.ifsState.value.currentVideoName ?: "VS Rolling") else (curVid?.name ?: ""))
                        putString("audioPath", cfg.currentAudio?.path ?: cfg.audioPath)
                        putBoolean("isMuted", if (isSv4Rolling) cfg.isVideoAudioMuted else (curVid?.isMuted ?: cfg.isVideoAudioMuted))
                        putBoolean("isPaused", cfg.isVideoPaused)
                        putBoolean("isLightweightMode", cfg.isLightweightMode)
                        putBoolean("isDebugOsdInVirtualCameraEnabled", cfg.isDebugOsdInVirtualCameraEnabled)
                        putString("antiHashMode", cfg.antiHashMode.name)
                        putFloat("videoAntiHashPercent", cfg.videoAntiHashPercent)
                        putBoolean("isAudioAntiHashEnabled", cfg.isAudioAntiHashEnabled)
                        putString("audioAntiHashMode", cfg.audioAntiHashMode.name)
                        putFloat("audioAntiHashPercent", cfg.audioAntiHashPercent)
                        putBoolean("enableDnaModule", cfg.enableDnaModule)
                        putBoolean("isDnaZoomEnabled", cfg.isDnaZoomEnabled)
                        putInt("sessionDnaIndex", cfg.sessionDnaIndex)
                        putLong("sessionDnaSeed", cfg.sessionDnaSeed)
                        putFloat("sessionDnaCustomZoom", cfg.sessionDnaCustomZoom)
                        putFloat("sessionDnaPanX", cfg.sessionDnaPanX)
                        putFloat("sessionDnaPanY", cfg.sessionDnaPanY)
                        putInt("fps", cfg.fps)
                        putBoolean("isLiveStreamSourceActive", cfg.isLiveStreamSourceActive)
                        putString("liveStreamUrl", cfg.liveStreamUrl)
                        putString("liveStreamTitle", cfg.liveStreamTitle)
                        putInt("rotation", cfg.rotation)
                        putBoolean("isMirrored", cfg.isMirrored)
                        putBoolean("isVerticalFlipped", cfg.isVerticalFlipped)
                        putFloat("zoomFactor", cfg.zoomFactor)
                        putString("scaleMode", cfg.scaleMode.name)
                        putString("videoSwitchMode", cfg.videoSwitchMode.name)
                        putBoolean("isMasterSpillMode", cfg.isMasterSpillMode)
                        putString("masterVideoId", cfg.masterVideoId ?: "")
                        putInt("masterIndex", repo.getMasterVideoIndex())
                        putBoolean("masterSpillWaitFinish", cfg.masterSpillWaitFinish)
                        putBoolean("isSpillPlaying", cfg.isCurrentPlayingSpill)
                        putString("activeSpillVideoId", cfg.activeSpillVideoId ?: "")
                        putBoolean("autoSpillFromComments", cfg.autoSpillFromComments)
                        putBoolean("isBridgeCycleMode", cfg.isBridgeCycleMode)
                        putString("currentBridgePhase", cfg.currentBridgePhase.name)
                        putInt("bridge1Index", cfg.bridge1VideoIndex)
                        val lm = com.tajasb.license.LicenseManager.getInstance(ctx)
                        val isLic = lm.isLicensed()
                        val isBlk = lm.isDeviceBlacklisted()
                        putBoolean("isLicensed", isLic)
                        putBoolean("isBlacklisted", isBlk)
                        putString("licenseToken", lm.getActiveToken() ?: "")
                        putString("hardwareId", lm.getDeviceId())
                        val decoyCfg = lm.getDecoyConfig()
                        putBoolean("isDecoyLicense", decoyCfg.isDecoy)
                        putString("decoyProfile", decoyCfg.profile.name)
                        putBoolean("decoyMochiBlind", decoyCfg.isMochiBlind)
                        putInt("decoyFreezeLoopCount", decoyCfg.freezeAfterLoopCount)
                        putInt("decoySmoothMinutes", decoyCfg.smoothMinutes)
                        putInt("decoySevereMinutes", decoyCfg.severeMinutes)
                        putBoolean("decoyForceSevere", decoyCfg.forceContinuousSevere)
                        putInt("decoyMinMinutes", decoyCfg.minIntervalMinutes)
                        putInt("decoyMaxMinutes", decoyCfg.maxIntervalMinutes)
                        putLong("version", repo.playlistVersion)
                    }
                }
                "onCameraSessionStarted" -> {
                    val pkg = extras?.getString("package") ?: arg ?: callingPackage
                    val (dna, didRotate) = repo.onCameraSessionStarted(pkg)
                    Bundle().apply {
                        putBoolean("success", true)
                        putBoolean("didRotateSessionDna", didRotate)
                        putInt("sessionDnaIndex", dna.sessionIndex)
                        putLong("sessionDnaSeed", dna.sessionSeed)
                        putString("codeName", dna.codeName)
                        putString("badgeText", dna.badgeText)
                        putString("quadrantLabel", dna.quadrantLabel)
                        putString("summaryText", dna.summaryText)
                    }
                }
                "onCameraSessionStopped" -> {
                    val activeDurationMs = extras?.getLong("activeDurationMs", 0L) ?: 0L
                    val reason = extras?.getString("reason") ?: arg ?: "camera_closed"
                    repo.onCameraSessionStopped(activeDurationMs, reason)
                    Bundle().apply {
                        putBoolean("success", true)
                    }
                }
                "rotateSessionDna" -> {
                    val reason = extras?.getString("reason") ?: arg ?: "ipc_call"
                    val dna = repo.rotateToNextSessionDnaSync(reason)
                    Bundle().apply {
                        putBoolean("success", true)
                        putInt("sessionDnaIndex", dna.sessionIndex)
                        putLong("sessionDnaSeed", dna.sessionSeed)
                        putString("codeName", dna.codeName)
                        putString("quadrantLabel", dna.quadrantLabel)
                    }
                }
                "getLicenseStatus", "checkLicense" -> {
                    val lm = com.tajasb.license.LicenseManager.getInstance(ctx)
                    val status = lm.refreshLicenseStatus()
                    val isLic = lm.isLicensed()
                    val devId = lm.getDeviceId()
                    val decoyCfg = lm.getDecoyConfig()
                    Bundle().apply {
                        putBoolean("success", true)
                        putBoolean("isLicensed", isLic)
                        putBoolean("isBlacklisted", lm.isDeviceBlacklisted())
                        putString("deviceId", devId)
                        putBoolean("isDecoyLicense", decoyCfg.isDecoy)
                        putString("decoyProfile", decoyCfg.profile.name)
                        putBoolean("decoyMochiBlind", decoyCfg.isMochiBlind)
                        putInt("decoyFreezeLoopCount", decoyCfg.freezeAfterLoopCount)
                        putInt("decoySmoothMinutes", decoyCfg.smoothMinutes)
                        putInt("decoySevereMinutes", decoyCfg.severeMinutes)
                        putBoolean("decoyForceSevere", decoyCfg.forceContinuousSevere)
                        putInt("decoyMinMinutes", decoyCfg.minIntervalMinutes)
                        putInt("decoyMaxMinutes", decoyCfg.maxIntervalMinutes)
                        putString("status", when (status) {
                            is com.tajasb.model.LicenseStatus.Active -> "ACTIVE"
                            is com.tajasb.model.LicenseStatus.Expired -> "EXPIRED"
                            is com.tajasb.model.LicenseStatus.Invalid -> "INVALID"
                            else -> "UNLICENSED"
                        })
                    }
                }
                "getStickerConfig" -> {
                    val sm = com.tajasb.stiker.CustomStikerManager.getInstance(ctx)
                    Bundle().apply {
                        putBoolean("enabled", sm.isEnabled.value)
                        putString("text", sm.stikerText.value)
                        putString("boxColor", sm.boxColorHex.value)
                        putFloat("boxAlpha", sm.boxAlpha.value)
                        putString("textColor", sm.textColorHex.value)
                        putFloat("scale", sm.sizeScale.value)
                        putFloat("posX", sm.posXpct.value)
                        putFloat("posY", sm.posYpct.value)
                        putInt("rotation", sm.rotation.value)
                        putBoolean("isMirrored", sm.isMirrored.value)
                        putBoolean("isRandomMove", sm.isRandomMoveEnabled.value)
                        putInt("outlineWidth", sm.outlineWidth.value)
                        putString("outlineColor", sm.outlineColorHex.value)
                        putString("displayStyle", sm.displayStyle.value)
                        putBoolean("isRunningText", sm.isRunningTextEnabled.value)
                        putFloat("runningTextSpeed", sm.runningTextSpeed.value)
                        putString("runningTextDirection", sm.runningTextDirection.value)
                    }
                }
                "getWatermarkConfig" -> {
                    val wm = com.tajasb.stiker.CustomWatermarkManager.getInstance(ctx)
                    Bundle().apply {
                        putBoolean("enabled", wm.isEnabled.value)
                        putString("text", wm.watermarkText.value)
                        putString("boxColor", wm.boxColorHex.value)
                        putFloat("boxAlpha", wm.boxAlpha.value)
                        putString("textColor", wm.textColorHex.value)
                        putFloat("scale", wm.sizeScale.value)
                        putFloat("posX", wm.posXpct.value)
                        putFloat("posY", wm.posYpct.value)
                        putInt("rotation", wm.rotation.value)
                        putBoolean("isMirrored", wm.isMirrored.value)
                        putBoolean("isRandomMove", wm.isRandomMoveEnabled.value)
                        putInt("outlineWidth", wm.outlineWidth.value)
                        putString("outlineColor", wm.outlineColorHex.value)
                        putString("displayStyle", wm.displayStyle.value)
                        putBoolean("isRunningText", wm.isRunningTextEnabled.value)
                        putFloat("runningTextSpeed", wm.runningTextSpeed.value)
                        putString("runningTextDirection", wm.runningTextDirection.value)
                    }
                }
                "getBuyerNotifConfig" -> {
                    val bm = com.tajasb.notif.BuyerNotificationManager.getInstance(ctx)
                    Bundle().apply {
                        putBoolean("enabled", bm.isEnabled.value)
                        putString("actionText", bm.actionText.value)
                        putLong("durationMs", bm.displayDurationMs.value)
                        putFloat("scale", bm.scale.value)
                        putInt("posX", bm.posX.value)
                        putInt("posY", bm.posY.value)
                        putInt("rotation", bm.rotation.value)
                        putBoolean("isMirrored", bm.isMirrored.value)
                        putString("entryDirection", bm.entryDirection.value)
                    }
                }
                "getAutoTimeConfig" -> {
                    val atm = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(ctx)
                    val (dayStr, monthStr, timeStr) = com.tajasb.notif.AutoTimeCapsuleManager.formatDateAndTimeParts(
                        showSeconds = atm.showSeconds.value
                    )
                    val stylePack = com.tajasb.notif.AutoTimeCapsuleManager.encodeStylePack(
                        dateBoxBgHex = atm.dateBoxBgColorHex.value,
                        dateBoxTextHex = atm.dateBoxTextColorHex.value,
                        timeTextHex = atm.timeTextColorHex.value,
                        showSeconds = atm.showSeconds.value
                    )
                    Bundle().apply {
                        putBoolean("enabled", atm.isEnabled.value)
                        putBoolean("showSeconds", atm.showSeconds.value)
                        putString("senderName", "$dayStr|$monthStr")
                        putString("content", timeStr)
                        putString("stylePack", stylePack)
                        putString("capsuleBgColor", atm.capsuleBgColorHex.value)
                        putFloat("capsuleAlpha", atm.capsuleAlpha.value)
                        putInt("capsuleCornerRadius", atm.capsuleCornerRadius.value)
                        putInt("capsulePaddingH", atm.capsulePaddingH.value)
                        putInt("capsuleOutlineWidth", atm.capsuleOutlineWidth.value)
                        putString("capsuleOutlineColor", atm.capsuleOutlineColorHex.value)
                        putFloat("scale", atm.sizeScale.value)
                        putFloat("posX", atm.posXpct.value)
                        putFloat("posY", atm.posYpct.value)
                        putInt("rotation", atm.rotation.value)
                        putBoolean("isMirrored", atm.isMirrored.value)
                    }
                }
                "getQuranKhatamConfig" -> {
                    val qe = com.tajasb.quran.InteractiveQuranKhatamEngine.getInstance(ctx)
                    val s = qe.state.value
                    val (headerText, payloadContent) = qe.buildOverlayPayload()
                    val remStatsPayload = qe.buildRemainingStatsPayload()
                    Bundle().apply {
                        putBoolean("enabled", s.isEnabled)
                        putString("senderName", headerText)
                        putString("content", payloadContent)
                        putBoolean("isGift", s.isAyahCompletedFlash)
                        putString("customPrefix", "${if (s.showTranslation) "SHOW_TRANS" else "HIDE_TRANS"}:::${s.latinFontSizeSp}:::${if (s.showNotifUnderAyah) "SHOW_NOTIF" else "HIDE_NOTIF"}")
                        putString("formatMode", "MAX_LINES_${s.maxArabicLines}")
                        putFloat("posX", s.posXpct)
                        putFloat("posY", s.posYpct)
                        putInt("rotation", s.overlayRotation)
                        putFloat("scale", s.overlayScale)
                        putBoolean("isMirrored", s.overlayMirrored)
                        putFloat("fontSizeSp", s.arabicFontSizeSp)
                        putInt("capsulePaddingH", (s.cardWidthPct * 100).toInt())
                        putInt("capsulePaddingV", s.cardPaddingV)
                        // Remaining stats overlay
                        putBoolean("remStatsEnabled", s.enableRemainingStatsOverlay && s.isEnabled)
                        putString("remStatsContent", remStatsPayload)
                        putFloat("remStatsPosX", s.remainingStatsPosXpct)
                        putFloat("remStatsPosY", s.remainingStatsPosYpct)
                        putFloat("remStatsScale", s.remainingStatsScale)
                        putFloat("remStatsFontSizeSp", s.remainingStatsFontSizeSp)
                    }
                }
                "getEventStatsConfig" -> {
                    val ep = com.tajasb.eventrelay.LiveEventPreferences.getInstance(ctx).config.value
                    val er = com.tajasb.eventrelay.LiveEventReceiver.getInstance(ctx)
                    Bundle().apply {
                        putBoolean("enabled", ep.enableEventStatsOverlay)
                        putString("content", er.buildUnifiedDashboardPayload())
                        putFloat("posX", ep.eventStatsPosXpct)
                        putFloat("posY", ep.eventStatsPosYpct)
                        putFloat("scale", ep.eventStatsScale)
                        putFloat("fontSizeSp", ep.eventStatsFontSizeSp)
                        putInt("capsulePaddingH", (ep.eventStatsWidthPct * 100).toInt())
                    }
                }
                "getTopLikersConfig" -> {
                    val ep = com.tajasb.eventrelay.LiveEventPreferences.getInstance(ctx).config.value
                    val quranTracker = com.tajasb.quran.QuranKhatamLeaderboardTracker.getInstance(ctx)
                    val unifiedPayload = quranTracker.buildUnified4CategoryPayload(
                        showQuran = ep.showTopQuran,
                        showSurah = ep.showTopSurah,
                        showAyah = ep.showTopAyah,
                        showLetters = ep.showTopHuruf
                    )
                    val payload = unifiedPayload.ifBlank { "QURAN:::|||SURAH:::|||AYAH:::|||HURUF:::" }
                    Bundle().apply {
                        putBoolean("enabled", ep.enableTopLikersOverlay)
                        putString("content", payload)
                        putString("customPrefix", "${ep.topLikersOrientation}:::${ep.topGiftAvatarScale}:::${ep.topGiftOrientation}:::${ep.topLikersLatinFontSizeSp}")
                        putFloat("posX", ep.topLikersPosXpct)
                        putFloat("posY", ep.topLikersPosYpct)
                        putFloat("scale", ep.topLikersScale)
                        putFloat("fontSizeSp", ep.topLikersFontSizeSp)
                        putInt("capsulePaddingH", (ep.topLikersWidthPct * 100).toInt())
                        putInt("capsulePaddingV", ep.topLikersHeightPadV)
                    }
                }
                "peekNextVideo" -> {
                    val cfg = repo.config.value
                    if (cfg.playlist.isEmpty()) return null
                    val mode = when (arg ?: extras?.getString("mode")) {
                        "SINGLE_LOOP", "LOOP" -> AutoNextMode.SINGLE_LOOP
                        "RANDOM" -> AutoNextMode.RANDOM
                        "SEQUENTIAL" -> AutoNextMode.SEQUENTIAL
                        else -> cfg.autoNextMode
                    }
                    val pred = repo.peekNextVideoDetailed(mode)
                    val nextIndex = pred.nextIndex
                    val nextItem = pred.nextItem
                    if (nextIndex < 0 || nextItem == null) return null
                    val nextFileName = File(nextItem.path).name
                    val segmentName = if (nextFileName.startsWith("vs_rolling_") && nextFileName.endsWith(".mp4")) {
                        nextFileName
                    } else {
                        "video_$nextIndex.mp4"
                    }
                    Bundle().apply {
                        putBoolean("success", true)
                        putInt("nextIndex", nextIndex)
                        putInt("currentIndex", cfg.currentVideoIndex)
                        putInt("totalCount", cfg.playlist.size)
                        putString("videoName", nextItem.name)
                        putString("videoPath", nextItem.path)
                        putBoolean("isMuted", nextItem.isMuted)
                        putBoolean("isSameSlotLoop", pred.isSameSlotLoop)
                        putString("modeLabel", pred.modeLabel)
                        putString("segment", segmentName)
                        putString("uri", "content://$AUTHORITY/$segmentName")
                        putLong("version", System.currentTimeMillis())
                    }
                }
                "queueVideo", "setPendingVideo" -> {
                    val targetIndex = extras?.getInt("index", -1) ?: arg?.toIntOrNull() ?: -1
                    if (targetIndex >= 0) {
                        val item = runBlocking {
                            repo.queueCustomVideo(targetIndex)
                        }
                        Bundle().apply {
                            putBoolean("success", true)
                            putInt("pendingIndex", targetIndex)
                            putString("videoName", item?.name ?: "")
                            putLong("version", System.currentTimeMillis())
                        }
                    } else null
                }
                "cancelPendingVideo" -> {
                    runBlocking {
                        repo.cancelQueuedVideo()
                    }
                    Bundle().apply {
                        putBoolean("success", true)
                    }
                }
                "selectVideo" -> {
                    val targetIndex = extras?.getInt("index", -1) ?: arg?.toIntOrNull() ?: -1
                    if (targetIndex >= 0) {
                        val item = runBlocking {
                            repo.selectVideoByIndex(targetIndex)
                        }
                        val updatedCfg = repo.config.value
                        Bundle().apply {
                            putBoolean("success", true)
                            putInt("currentIndex", updatedCfg.currentVideoIndex)
                            putBoolean("isMuted", item?.isMuted ?: updatedCfg.isVideoAudioMuted)
                            putLong("version", System.currentTimeMillis())
                        }
                    } else null
                }
                "syncVideoIndex" -> {
                    val targetIndex = extras?.getInt("index", -1) ?: arg?.toIntOrNull() ?: -1
                    if (targetIndex >= 0) {
                        val item = runBlocking {
                            repo.syncVideoIndexWithoutBroadcast(targetIndex)
                        }
                        val updatedCfg = repo.config.value
                        Bundle().apply {
                            putBoolean("success", true)
                            putInt("currentIndex", updatedCfg.currentVideoIndex)
                            putBoolean("isMuted", item?.isMuted ?: updatedCfg.isVideoAudioMuted)
                            putString("videoName", item?.name ?: "")
                            putLong("version", System.currentTimeMillis())
                        }
                    } else null
                }
                "setLightweightMode", "toggleLightweightMode" -> {
                    val enabled = extras?.getBoolean("enabled", true) ?: arg?.toBooleanStrictOrNull()
                    val result = runBlocking {
                        repo.toggleLightweightMode(enabled)
                    }
                    Bundle().apply {
                        putBoolean("success", true)
                        putBoolean("isLightweightMode", result)
                    }
                }
                "triggerSpill" -> {
                    val query = extras?.getString("query") ?: extras?.getString("keyword") ?: arg ?: ""
                    val immediate = extras?.getBoolean("immediate", false) ?: false
                    val item = runBlocking {
                        repo.triggerSpillVideo(query, immediate = immediate)
                    }
                    val updatedCfg = repo.config.value
                    Bundle().apply {
                        putBoolean("success", item != null)
                        putInt("currentIndex", updatedCfg.currentVideoIndex)
                        putString("videoName", item?.name ?: "")
                        putBoolean("isPending", repo.pendingVideoIndex.value != null)
                        putLong("version", System.currentTimeMillis())
                    }
                }
                "returnToMaster" -> {
                    val immediate = extras?.getBoolean("immediate", true) ?: true
                    val item = runBlocking {
                        repo.returnToMasterVideo(immediate = immediate)
                    }
                    val updatedCfg = repo.config.value
                    Bundle().apply {
                        putBoolean("success", item != null)
                        putInt("currentIndex", updatedCfg.currentVideoIndex)
                        putInt("masterIndex", repo.getMasterVideoIndex())
                        putString("videoName", item?.name ?: "")
                        putLong("version", System.currentTimeMillis())
                    }
                }
                "setMasterVideo" -> {
                    val id = extras?.getString("id") ?: arg ?: ""
                    if (id.isNotEmpty()) {
                        runBlocking { repo.setMasterVideo(id) }
                        Bundle().apply { putBoolean("success", true) }
                    } else null
                }
                "toggleMasterSpillMode" -> {
                    val enabled = extras?.getBoolean("enabled", true) ?: arg?.toBooleanStrictOrNull()
                    val result = runBlocking { repo.toggleMasterSpillMode(enabled) }
                    Bundle().apply {
                        putBoolean("success", true)
                        putBoolean("isMasterSpillMode", result)
                    }
                }
                "pushLog", "logDebug" -> {
                    val tag = extras?.getString("tag") ?: "IPC"
                    val msg = extras?.getString("message") ?: extras?.getString("msg") ?: arg ?: ""
                    val isError = extras?.getBoolean("isError", false) ?: false
                    com.tajasb.debug.DebugLogManager.getInstance(ctx).log(tag, msg, isError)
                    Bundle().apply { putBoolean("success", true) }
                }
                else -> null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error handling call($method): ${t.message}", t)
            null
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val proj = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(proj)
        val isBackupZipRequest = uri.lastPathSegment?.lowercase()?.endsWith(".zip") == true
        if (!isBackupZipRequest && !isCallerAuthorized()) {
            Log.w(TAG, "Blocked unauthorized query($uri) from UID ${android.os.Binder.getCallingUid()}, package: $callingPackage")
            return cursor
        }
        val file = getTargetFile(uri)
        if (file != null && file.exists()) {
            val row = cursor.newRow()
            for (col in proj) {
                when (col) {
                    OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                    OpenableColumns.SIZE -> row.add(file.length())
                    else -> row.add(null)
                }
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val name = uri.lastPathSegment?.lowercase() ?: ""
        return when {
            name.endsWith(".zip") -> "application/zip"
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".mp3") -> "audio/mpeg"
            name.endsWith(".wav") -> "audio/wav"
            name.endsWith(".json") -> "application/json"
            name.endsWith(".txt") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val isBackupZipRequest = uri.lastPathSegment?.lowercase()?.endsWith(".zip") == true
        if (!isBackupZipRequest && !isCallerAuthorized()) {
            Log.w(TAG, "Blocked unauthorized openFile($uri) from UID ${android.os.Binder.getCallingUid()}, package: $callingPackage")
            throw SecurityException("Caller package $callingPackage is not authorized to access Tajasb virtual camera files")
        }
        val file = getTargetFile(uri) ?: throw FileNotFoundException("File not found for URI: $uri")
        if (!file.exists() || !file.canRead()) {
            throw FileNotFoundException("File does not exist or cannot be read: ${file.absolutePath}")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun getBigoTargetFile(ctx: android.content.Context, subSegments: List<String>): File? {
        if (subSegments.size != 1) return null
        val sub = subSegments[0].trim()
        if (sub.contains("..") || sub.contains("/") || sub.contains("\\") || sub.contains("%2e") || sub.contains("%2f")) {
            return null
        }
        return when (sub) {
            "config", "config.json" -> com.tajasb.hooks.bigo.BigoConfigStore.getOrCreateConfigJsonFile(ctx)
            "virtual.mp4", "current_video.mp4" -> com.tajasb.hooks.bigo.BigoConfigStore.resolveBigoVideoFile(ctx)
            "audio.mp3", "audio.wav" -> com.tajasb.hooks.bigo.BigoConfigStore.resolveBigoAudioFile(ctx)
            else -> null
        }
    }

    private fun getTargetFile(uri: Uri): File? {
        val ctx = context ?: return null
        val segments = uri.pathSegments ?: emptyList()
        if (segments.firstOrNull() == "bigo") {
            return getBigoTargetFile(ctx, segments.drop(1))
        }

        val rawSegment = uri.lastPathSegment ?: "virtual.mp4"
        // Security Hardening: Block directory traversal attempts
        if (rawSegment.contains("..") || rawSegment.contains("/") || rawSegment.contains("\\") || rawSegment.contains("%2e") || rawSegment.contains("%2f")) {
            Log.w(TAG, "Blocked path traversal attempt in URI: $uri")
            return null
        }
        val segment = rawSegment.trim()

        // 0. Check if requesting an exported .zip backup archive for Google Drive / Share
        if (segment.lowercase().endsWith(".zip")) {
            val cacheExport = File(File(ctx.cacheDir, "backup_exports"), segment)
            if (cacheExport.exists() && cacheExport.length() > 0L) return cacheExport

            val filesExport = File(File(ctx.filesDir, "backup_exports"), segment)
            if (filesExport.exists() && filesExport.length() > 0L) return filesExport

            try {
                val vaultDir = com.tajasb.data.ConceptBackupManager.getInstance(ctx).getResolvedCustomVaultDir()
                val vaultExport = File(vaultDir, segment)
                if (vaultExport.exists() && vaultExport.length() > 0L) return vaultExport
            } catch (_: Throwable) {}
            return null
        }

        try {
            val repo = VcamRepository.getInstance(ctx)
            val cfg = repo.config.value

            // 1. Current active virtual video
            if (segment == "virtual.mp4" || segment == "current_video.mp4") {
                if (cfg.playlist.isEmpty() && cfg.videoPath.isBlank()) {
                    return null
                }
                if (cfg.videoPath.isNotEmpty() && cfg.videoPath.contains("vs_rolling_")) {
                    val vsFile = File(cfg.videoPath)
                    if (vsFile.exists() && vsFile.length() > 0) {
                        return vsFile
                    }
                }
                val activeVideo = cfg.currentVideo
                if (activeVideo != null) {
                    val f = File(activeVideo.path)
                    if (f.exists() && f.length() > 0) {
                        return f
                    }
                }
                if (cfg.playlist.isNotEmpty() && cfg.currentVideoIndex in cfg.playlist.indices) {
                    val f = File(cfg.playlist[cfg.currentVideoIndex].path)
                    if (f.exists() && f.length() > 0) {
                        return f
                    }
                }
                if (cfg.videoPath.isNotEmpty()) {
                    val f = File(cfg.videoPath)
                    if (f.exists() && f.length() > 0) {
                        return f
                    }
                }
                // Never fall through to stale DCIM or internal Camera1 for virtual.mp4!
                return null
            }

            // 1b. Next video prefetch segment
            if (segment == "next_video.mp4") {
                if (cfg.playlist.isEmpty()) return null
                val nextIdx = (cfg.currentVideoIndex + 1) % cfg.playlist.size
                val nextFile = File(cfg.playlist[nextIdx].path)
                if (nextFile.exists() && nextFile.length() > 0) {
                    return nextFile
                }
                return null
            }

            // 2. Indexed video from playlist: video_0.mp4, video_1.mp4, etc.
            if (segment.startsWith("video_") && segment.endsWith(".mp4")) {
                val idxStr = segment.removePrefix("video_").removeSuffix(".mp4")
                val idx = idxStr.toIntOrNull()
                if (idx != null && idx in cfg.playlist.indices) {
                    val f = File(cfg.playlist[idx].path)
                    if (f.exists() && f.length() > 0) {
                        return f
                    }
                }
                // CRITICAL FIX: If requested indexed video is out of bounds or file removed,
                // return null immediately. NEVER fall through to reading stale deleted files from disk!
                return null
            }

            // 3. Audio file
            if (segment == "audio.mp3" || segment == "audio.wav") {
                val activeAudio = cfg.currentAudio
                if (activeAudio != null) {
                    val f = File(activeAudio.path)
                    if (f.exists() && f.length() > 0) {
                        return f
                    }
                }
                if (cfg.audioPath.isNotEmpty()) {
                    val f = File(cfg.audioPath)
                    if (f.exists() && f.length() > 0) {
                        return f
                    }
                }
                return null
            }
        } catch (_: Throwable) {}

        // 4. Check internal Camera1 directory for config files, playlist.txt, etc.
        val internalDir = File(ctx.filesDir, "Camera1")
        val internalFile = File(internalDir, segment)
        if (internalFile.exists() && internalFile.length() > 0) {
            return internalFile
        }

        // 5. Fallback storage locations
        val dcimDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1")
        val dcimFile = File(dcimDir, segment)
        if (dcimFile.exists() && dcimFile.length() > 0) {
            return dcimFile
        }

        val sdcardFile = File("/sdcard/DCIM/Camera1/$segment")
        if (sdcardFile.exists() && sdcardFile.length() > 0) {
            return sdcardFile
        }

        val localTmpFile = File("/data/local/tmp/$segment")
        if (localTmpFile.exists() && localTmpFile.length() > 0) {
            return localTmpFile
        }

        return if (internalFile.exists()) internalFile else null
    }
}
