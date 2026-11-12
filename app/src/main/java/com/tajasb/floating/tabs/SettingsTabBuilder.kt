package com.tajasb.floating.tabs

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tajasb.FloatingControlService
import com.tajasb.FloatingTab
import com.tajasb.floating.components.FloatingUiWidgets
import kotlinx.coroutines.launch

/**
 * SettingsTabBuilder:
 * Modular builder for Tab 6 (⚙️ Settings & Zero-Load Module Switches, RAM/Network Booster,
 * Hook Engine, Resolution Presets, Scan Recorder Shortcut, Log Viewer, Fixed Bottom Dock,
 * and Panel Resize Bar).
 */
object SettingsTabBuilder {

    fun populateSettingsTab(
        service: FloatingControlService,
        panel: LinearLayout,
        tabContentHolder: LinearLayout,
        dragHandle: View,
        header: View,
        wmPanelParams: WindowManager.LayoutParams,
        dp: (Int) -> Int
    ) = with(service) {
        // ---------------------------------------------------------
        // 6. TAB 6: ⚙️ SETTINGS TAB CONTAINER (ALL-IN-ONE CONTROL CENTER)
        // ---------------------------------------------------------
        val tabSettingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerTabSettings = tabSettingsContainer

        val settingsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(2), 0)
        }
        tabSettingsContainer.addView(settingsContent)

        val moduleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#1E1B4B"))
                setStroke(dp(1), Color.parseColor("#6366F1"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val moduleCardTitle = TextView(this).apply {
            text = "🎛️ SAKLAR MODUL FLOATING:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#A5B4FC"))
        }
        moduleCard.addView(moduleCardTitle)

        val moduleCardDesc = TextView(this).apply {
            text = "Matikan modul yang tidak dipakai agar menu ringkas & hemat baterai."
            textSize = 8f
            setTextColor(Color.parseColor("#C7D2FE"))
            setPadding(0, dp(2), 0, dp(5))
        }
        moduleCard.addView(moduleCardDesc)

        fun createModuleSwitchBtn(label: String, icon: String, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = "$icon $label"
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(3)
                }
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        btnModuleSalesToggle = createModuleSwitchBtn("Modul Notif Jualan", "🛒") {
            serviceScope.launch {
                val next = !repository.config.value.enableSalesNotifModule
                repository.updateConfig { it.copy(enableSalesNotifModule = next) }
                if (!next) {
                    com.tajasb.notif.BuyerNotificationManager.getInstance(applicationContext).toggleEnabled(false)
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(applicationContext, if (next) "🛒 Modul Notif Jualan: TAMPIL (ON)" else "🛒 Modul Notif Jualan: HILANG & MATI (OFF)", Toast.LENGTH_SHORT).show()
            }
        }
        moduleCard.addView(btnModuleSalesToggle)

        btnModuleStickerToggle = createModuleSwitchBtn("Modul Stiker Tulisan", "🏷️") {
            serviceScope.launch {
                val next = !repository.config.value.enableStickerModule
                repository.updateConfig { it.copy(enableStickerModule = next) }
                if (!next) {
                    val sm = com.tajasb.stiker.CustomStikerManager.getInstance(applicationContext)
                    sm.toggleEnabled(false)
                    sm.stopRandomMoveLoop()
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(applicationContext, if (next) "🏷️ Modul Stiker: TAMPIL (ON)" else "🏷️ Modul Stiker: HILANG & MATI (OFF)", Toast.LENGTH_SHORT).show()
            }
        }
        moduleCard.addView(btnModuleStickerToggle)

        btnModuleWatermarkToggle = createModuleSwitchBtn("Modul Watermark Kamera", "💧") {
            serviceScope.launch {
                val next = !repository.config.value.enableWatermarkModule
                repository.updateConfig { it.copy(enableWatermarkModule = next) }
                if (!next) {
                    val wm = com.tajasb.stiker.CustomWatermarkManager.getInstance(applicationContext)
                    wm.toggleEnabled(false)
                    wm.stopRandomMoveLoop()
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(applicationContext, if (next) "💧 Modul Watermark: TAMPIL (ON)" else "💧 Modul Watermark: HILANG & MATI (OFF)", Toast.LENGTH_SHORT).show()
            }
        }
        moduleCard.addView(btnModuleWatermarkToggle)

        btnModulePrayerToggle = createModuleSwitchBtn("Modul Doa Komentar Live", "🤲") {
            serviceScope.launch {
                val next = !repository.config.value.enablePrayerCommentModule
                repository.updateConfig { it.copy(enablePrayerCommentModule = next) }
                if (!next) {
                    val lm = com.tajasb.livecomment.LiveCommentManager.getInstance(applicationContext)
                    lm.toggleCaptureActive(false)
                    lm.clearQueue()
                    com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(applicationContext, "COMMENT")
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(applicationContext, if (next) "🤲 Modul Doa: TAMPIL (ON)" else "🤲 Modul Doa: HILANG & MATI (OFF)", Toast.LENGTH_SHORT).show()
            }
        }
        moduleCard.addView(btnModulePrayerToggle)

        btnModuleQuranKhatamToggle = createModuleSwitchBtn("Modul Khatam Al-Qur'an Live", "🕌") {
            serviceScope.launch {
                val next = !repository.config.value.enableQuranKhatamModule
                repository.updateConfig { it.copy(enableQuranKhatamModule = next) }
                if (!next) {
                    val qm = com.tajasb.quran.InteractiveQuranKhatamEngine.getInstance(applicationContext)
                    qm.setEnabled(false)
                    com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(applicationContext, "QURAN_KHATAM")
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(applicationContext, if (next) "🕌 Modul Khatam Qur'an: TAMPIL (ON)" else "🕌 Modul Khatam Qur'an: HILANG & MATI (OFF)", Toast.LENGTH_SHORT).show()
            }
        }
        moduleCard.addView(btnModuleQuranKhatamToggle)

        btnModuleTapToggle = createModuleSwitchBtn("Modul Auto-Tap", "👆") {
            serviceScope.launch {
                val next = !repository.config.value.enableAutoTapModule
                repository.updateConfig { it.copy(enableAutoTapModule = next) }
                if (!next) {
                    val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(applicationContext)
                    autoTap.stopTapping()
                    autoTap.setPointerVisible(false)
                    removeCrosshairPointer()
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                updateBubbleIconsStatus()
                Toast.makeText(applicationContext, if (next) "👆 Modul Auto-Tap: TAMPIL (ON)" else "👆 Modul Auto-Tap: HILANG & MATI (OFF)", Toast.LENGTH_SHORT).show()
            }
        }
        moduleCard.addView(btnModuleTapToggle)

        btnModuleMusicToggle = createModuleSwitchBtn("Modul Musik MP3 Ekstra", "🎵") {
            serviceScope.launch {
                val next = !repository.config.value.enableMp3MusicModule
                repository.updateConfig { it.copy(enableMp3MusicModule = next) }
                if (!next) {
                    repository.pauseAudio()
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(applicationContext, if (next) "🎵 Modul Musik MP3: TAMPIL (ON)" else "🎵 Modul Musik MP3: HILANG & MATI (OFF)", Toast.LENGTH_SHORT).show()
            }
        }
        moduleCard.addView(btnModuleMusicToggle)

        btnModuleOsdToggle = createModuleSwitchBtn("Modul OSD Jam/Detik MS", "⏱️") {
            serviceScope.launch {
                val next = !repository.config.value.enableOsdModule
                repository.updateConfig { it.copy(enableOsdModule = next, isDebugOsdInVirtualCameraEnabled = if (next) it.isDebugOsdInVirtualCameraEnabled else false) }
                repository.broadcastControlEvent(action = "UPDATE_CONFIG")
                if (!next) {
                    try {
                        val directIntent = Intent("com.tajasb.action.TOGGLE_DEBUG_OSD").apply {
                            putExtra("extra_debug_osd_enabled", false)
                        }
                        applicationContext.sendBroadcast(directIntent)
                    } catch (_: Exception) {}
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(applicationContext, if (next) "⏱️ Modul OSD: TAMPIL (ON)" else "⏱️ Modul OSD: HILANG & MATI (OFF)", Toast.LENGTH_SHORT).show()
            }
        }
        moduleCard.addView(btnModuleOsdToggle)

        btnModuleAutoTimeToggle = createModuleSwitchBtn("Modul Waktu Otomatis", "⏰") {
            serviceScope.launch {
                val next = !repository.config.value.enableAutoTimeModule
                repository.updateConfig { it.copy(enableAutoTimeModule = next) }
                if (!next) {
                    com.tajasb.notif.AutoTimeCapsuleManager.getInstance(applicationContext).toggleEnabled(false)
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(applicationContext, if (next) "⏰ Modul Waktu Otomatis: TAMPIL (ON)" else "⏰ Modul Waktu Otomatis: HILANG & MATI (OFF)", Toast.LENGTH_SHORT).show()
            }
        }
        moduleCard.addView(btnModuleAutoTimeToggle)

        btnModuleDnaToggle = createModuleSwitchBtn("Modul DNA Anti-Deteksi", "🧬") {
            serviceScope.launch {
                val next = repository.toggleDnaModule()
                updateTabVisibilityAndSwitches(repository.config.value)
                val dna = repository.config.value.activeSessionDna
                Toast.makeText(
                    applicationContext,
                    if (next) "🧬 Modul DNA: TAMPIL & AKTIF (${dna.badgeText})" else "🧬 Modul DNA: HILANG & MATI (OFF)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        moduleCard.addView(btnModuleDnaToggle)

        btnModuleAiDetectionToggle = createModuleSwitchBtn("Modul Hapus Kode AI", "🧹") {
            serviceScope.launch {
                val next = repository.toggleAiDetectionModule()
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(
                    applicationContext,
                    if (next) "🧹 Modul Hapus Kode AI: TAMPIL (ON)" else "🧹 Modul Hapus Kode AI: HILANG & MATI (OFF)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        moduleCard.addView(btnModuleAiDetectionToggle)

        btnModuleQuranAudioToggle = createModuleSwitchBtn("Modul Audio Al-Qur'an", "📖") {
            serviceScope.launch {
                val next = repository.toggleQuranAudioModule()
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(
                    applicationContext,
                    if (next) "📖 Modul Audio Al-Qur'an: TAMPIL (ON)" else "📖 Modul Audio Al-Qur'an: HILANG & MATI (OFF)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        moduleCard.addView(btnModuleQuranAudioToggle)

        btnModuleMakkahRelayToggle = createModuleSwitchBtn("Modul Relay Mekah Live", "🕋") {
            serviceScope.launch {
                val next = !repository.config.value.enableMakkahRelayModule
                repository.updateConfig { it.copy(enableMakkahRelayModule = next) }
                if (!next) {
                    repository.updateConfig { it.copy(isLiveStreamSourceActive = false) }
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(
                    applicationContext,
                    if (next) "🕋 Modul Relay Mekah: TAMPIL (ON)" else "🕋 Modul Relay Mekah: HILANG & MATI (OFF)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        moduleCard.addView(btnModuleMakkahRelayToggle)

        btnModuleLiveEventBrokerToggle = createModuleSwitchBtn("Modul TikTok Event Broker", "⚡") {
            serviceScope.launch {
                val next = !repository.config.value.enableLiveEventBrokerModule
                repository.updateConfig { it.copy(enableLiveEventBrokerModule = next) }
                if (!next) {
                    com.tajasb.eventrelay.LiveEventPreferences.getInstance(applicationContext).setAutoConnect(false)
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(
                    applicationContext,
                    if (next) "⚡ Modul Event Broker: TAMPIL (ON)" else "⚡ Modul Event Broker: HILANG & MATI (OFF)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        moduleCard.addView(btnModuleLiveEventBrokerToggle)

        btnModuleScanInspectorToggle = createModuleSwitchBtn("Modul Inspeksi / Log Scan", "🔍") {
            serviceScope.launch {
                val next = !repository.config.value.enableScanInspectorModule
                repository.updateConfig { it.copy(enableScanInspectorModule = next) }
                if (!next) {
                    com.tajasb.livecomment.CommentScanInspector.getInstance(applicationContext).setInspectorActive(false)
                }
                updateTabVisibilityAndSwitches(repository.config.value)
                Toast.makeText(
                    applicationContext,
                    if (next) "🔍 Modul Log Inspeksi: TAMPIL (ON)" else "🔍 Modul Log Inspeksi: HILANG & MATI (OFF)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        moduleCard.addView(btnModuleScanInspectorToggle)

        updateModuleSwitchesUi(repository.config.value)
        settingsContent.addView(moduleCard)

        val btnMaster = TextView(this).apply {
            text = "🟢 Virtual Camera: AKTIF"
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val nextMaster = !repository.config.value.isMasterEnabled
                    repository.updateConfig { it.copy(isMasterEnabled = nextMaster) }
                    repository.broadcastControlEvent(action = "UPDATE_CONFIG", isMasterEnabled = nextMaster)
                    Toast.makeText(applicationContext, if (nextMaster) "Virtual Cam AKTIF" else "Virtual Cam NONAKTIF", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnMasterToggleText = btnMaster
        settingsContent.addView(btnMaster)

        // -------------------------------------------------------------
        // Card Optimasi Perangkat & Jaringan Dinamis (Floating Settings)
        // -------------------------------------------------------------
        val boosterCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val boosterTitle = TextView(this).apply {
            val netSpeed = repository.getDeviceMemoryStats().networkSpeedMbps
            text = "⚡ OPTIMASI RAM & FOKUS JARINGAN (~${netSpeed}M):"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, dp(4))
        }
        boosterCard.addView(boosterTitle)

        val btnTurboCleanCard = TextView(this).apply {
            text = "⚡ Turbo Bersihkan RAM & Cache APK Latar"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#6D28D9"))
                setStroke(dp(1), Color.parseColor("#A78BFA"))
            }
            setOnClickListener {
                serviceScope.launch {
                    val res = repository.triggerTurboRamClean()
                    val stats = repository.getDeviceMemoryStats()
                    val msg = if (res.freedMb > 0) "+${res.freedMb} MB Bebas" else "RAM Optimal"
                    Toast.makeText(applicationContext, "⚡ RAM Dibersihkan! $msg (${stats.availableRamMb} MB Tersedia)", Toast.LENGTH_SHORT).show()
                }
            }
        }
        boosterCard.addView(btnTurboCleanCard)

        val isVpnActiveCard = repository.config.value.isLiveFocusVpnEnabled || com.tajasb.optimizer.LiveFocusVpnService.isVpnActive
        val currentSpeedMbps = repository.getDeviceMemoryStats().networkSpeedMbps
        val btnVpnFocusCard = TextView(this).apply {
            text = if (isVpnActiveCard) "🛡️ Fokus Aliran Data (~${currentSpeedMbps}M): AKTIF" else "🛡️ Fokus Aliran Data: NONAKTIF"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                if (isVpnActiveCard) {
                    setColor(Color.parseColor("#075985"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                } else {
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
            }
            setOnClickListener {
                serviceScope.launch {
                    val willEnable = !repository.config.value.isLiveFocusVpnEnabled
                    if (willEnable && !com.tajasb.optimizer.LiveFocusVpnService.isPrepared(applicationContext)) {
                        val prepIntent = com.tajasb.optimizer.LiveFocusVpnService.getPrepareIntent(applicationContext)
                        if (prepIntent != null) {
                            prepIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            applicationContext.startActivity(prepIntent)
                            Toast.makeText(applicationContext, "Izinkan dialog koneksi VPN di layar HP Anda 🛡️", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                    }
                    val next = repository.toggleLiveFocusVpn()
                    val dynSpeed = repository.getDeviceMemoryStats().networkSpeedMbps
                    text = if (next) "🛡️ Fokus Aliran Data (~${dynSpeed}M): AKTIF" else "🛡️ Fokus Aliran Data: NONAKTIF"
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        if (next) {
                            setColor(Color.parseColor("#075985"))
                            setStroke(dp(1), Color.parseColor("#00E5FF"))
                        } else {
                            setColor(Color.parseColor("#1E293B"))
                            setStroke(dp(1), Color.parseColor("#475569"))
                        }
                    }
                    val msg = if (next) "🛡️ Fokus Jaringan: AKTIF!\nKoneksi difokuskan untuk aplikasi live pilihan Anda." else "Fokus Jaringan: NONAKTIF"
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        boosterCard.addView(btnVpnFocusCard)

        val blockedAdsCount = repository.config.value.blockedAdPackages.size
        val btnBlockAdsFloating = TextView(this).apply {
            val isBlockActive = repository.config.value.isAdBlockerActive
            text = if (isBlockActive && blockedAdsCount > 0) "🚫 Blokir Data Iklan ($blockedAdsCount APK): AKTIF" else "🚫 Blokir Data Iklan ($blockedAdsCount APK): NONAKTIF"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                if (isBlockActive) {
                    setColor(Color.parseColor("#7F1D1D"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
            }
            setOnClickListener {
                serviceScope.launch {
                    val willEnable = !repository.config.value.isAdBlockerActive
                    if (willEnable && !com.tajasb.optimizer.LiveFocusVpnService.isPrepared(applicationContext)) {
                        val prepIntent = com.tajasb.optimizer.LiveFocusVpnService.getPrepareIntent(applicationContext)
                        if (prepIntent != null) {
                            prepIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            applicationContext.startActivity(prepIntent)
                            Toast.makeText(applicationContext, "Izinkan izin firewall VPN di layar HP Anda 🛡️", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                    }
                    val next = repository.toggleAdBlocker()
                    val count = repository.config.value.blockedAdPackages.size
                    text = if (next) "🚫 Blokir Data Iklan ($count APK): AKTIF" else "🚫 Blokir Data Iklan ($count APK): NONAKTIF"
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        if (next) {
                            setColor(Color.parseColor("#7F1D1D"))
                            setStroke(dp(1), Color.parseColor("#EF4444"))
                        } else {
                            setColor(Color.parseColor("#1E293B"))
                            setStroke(dp(1), Color.parseColor("#475569"))
                        }
                    }
                    val msg = if (next) "🚫 Blokir Data Iklan AKTIF ($count APK diputus 0 KB/s)!" else "Blokir Data Iklan: NONAKTIF"
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        boosterCard.addView(btnBlockAdsFloating)

        val fpsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        val fpsLabel = TextView(this).apply {
            text = "FPS Suhu Dingin: "
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        fpsRow.addView(fpsLabel)

        val currentFpsGovernor = repository.config.value.fpsGovernorLimit
        fun createFpsChip(fps: Int): TextView {
            return TextView(this).apply {
                text = "${fps} FPS"
                textSize = 8.5f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(3), dp(6), dp(3))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(2)
                }
                layoutParams = lp
                val isCur = (fps == currentFpsGovernor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isCur) {
                        setColor(Color.parseColor("#0284C7"))
                        setStroke(dp(1), Color.parseColor("#38BDF8"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#475569"))
                    }
                }
                setTextColor(if (isCur) Color.WHITE else Color.parseColor("#CBD5E1"))
                setOnClickListener {
                    serviceScope.launch {
                        repository.setFpsGovernorLimit(fps)
                        Toast.makeText(applicationContext, "FPS Limit: $fps FPS ${if (fps == 30) "(Rekomendasi Hemat Suhu)" else ""}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        fpsRow.addView(createFpsChip(25))
        fpsRow.addView(createFpsChip(30))
        fpsRow.addView(createFpsChip(60))
        boosterCard.addView(fpsRow)

        val isAutoRocketCard = repository.config.value.isAutoRocketRecoveryEnabled
        val btnAutoRocketCard = TextView(this).apply {
            val count = repository.config.value.autoRecoveryCount
            text = if (isAutoRocketCard) "🚀 Auto-Rocket (Anti-Freeze): AKTIF (Pulih: $count)" else "🚀 Auto-Rocket: NONAKTIF"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
                bottomMargin = dp(3)
            }
            layoutParams = lp
            val applyBg: (Boolean) -> Unit = { active ->
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(5).toFloat()
                    if (active) {
                        setColor(Color.parseColor("#065F46"))
                        setStroke(dp(1), Color.parseColor("#10B981"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#475569"))
                    }
                }
            }
            applyBg(isAutoRocketCard)
            setOnClickListener {
                serviceScope.launch {
                    val next = repository.toggleAutoRocketRecovery()
                    val c = repository.config.value.autoRecoveryCount
                    text = if (next) "🚀 Auto-Rocket (Anti-Freeze): AKTIF (Pulih: $c)" else "🚀 Auto-Rocket: NONAKTIF"
                    applyBg(next)
                    val msg = if (next) "🚀 Auto-Rocket: AKTIF!\nFreeze live dipulihkan otomatis tanpa sentuh roket." else "Auto-Rocket: NONAKTIF"
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        boosterCard.addView(btnAutoRocketCard)

        // Threshold & Test Row
        val rocketActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(2)
            }
            layoutParams = lp
        }

        val tvThreshLabel = TextView(this).apply {
            text = "Deteksi: "
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        rocketActionRow.addView(tvThreshLabel)

        fun createThreshChip(sec: Int): TextView {
            return TextView(this).apply {
                text = "${sec}s"
                textSize = 8.5f
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(3), dp(5), dp(3))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(2)
                }
                layoutParams = lp
                val isCur = (sec == repository.config.value.autoRecoveryStallThresholdSec)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isCur) {
                        setColor(Color.parseColor("#059669"))
                        setStroke(dp(1), Color.parseColor("#34D399"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#475569"))
                    }
                }
                setTextColor(if (isCur) Color.WHITE else Color.parseColor("#CBD5E1"))
                setOnClickListener {
                    serviceScope.launch {
                        repository.setAutoRecoveryStallThresholdSec(sec)
                        Toast.makeText(applicationContext, "Waktu Deteksi Freeze: $sec Detik", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        rocketActionRow.addView(createThreshChip(2))
        rocketActionRow.addView(createThreshChip(4))
        rocketActionRow.addView(createThreshChip(6))

        val btnTestRocket = TextView(this).apply {
            text = "⚡ Tes Roket 🚀"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0891B2"))
                setStroke(dp(1), Color.parseColor("#22D3EE"))
            }
            setOnClickListener {
                triggerAutoRocketRecovery(0L, "manual_test")
            }
        }
        rocketActionRow.addView(btnTestRocket)
        boosterCard.addView(rocketActionRow)

        settingsContent.addView(boosterCard)

        val engineCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val engineTitle = TextView(this).apply {
            text = "🔧 Engine Hook Kamera:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, dp(4))
        }
        engineCard.addView(engineTitle)

        val engineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun createEngineBtn(engine: com.tajasb.model.HookEngineType, label: String): TextView {
            return TextView(this).apply {
                text = label
                textSize = 9.5f
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(6), dp(2), dp(6))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                setOnClickListener {
                    serviceScope.launch {
                        repository.updateConfig { it.copy(hookEngine = engine) }
                        Toast.makeText(applicationContext, "Engine: ${engine.label}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnEngineHybrid = createEngineBtn(com.tajasb.model.HookEngineType.HYBRID, "Hybrid (Semua)")
        btnEngineCam1 = createEngineBtn(com.tajasb.model.HookEngineType.CAMERA1_LEGACY, "Cam1")
        btnEngineCam2 = createEngineBtn(com.tajasb.model.HookEngineType.CAMERA2_PIPELINE, "Cam2 Pipe")

        engineRow.addView(btnEngineHybrid)
        engineRow.addView(btnEngineCam1)
        engineRow.addView(btnEngineCam2)
        engineCard.addView(engineRow)
        settingsContent.addView(engineCard)

        val resCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val resTitle = TextView(this).apply {
            text = "📐 Resolusi Virtual Cam:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, dp(4))
        }
        resCard.addView(resTitle)

        val resRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun createResBtn(preset: com.tajasb.model.ResolutionPreset, label: String): TextView {
            return TextView(this).apply {
                text = label
                textSize = 9.5f
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(6), dp(2), dp(6))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                setOnClickListener {
                    serviceScope.launch {
                        repository.updateConfig { it.copy(forceResolution = preset) }
                        Toast.makeText(applicationContext, "Resolusi: ${preset.label}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val resRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        btnResAuto = createResBtn(com.tajasb.model.ResolutionPreset.AUTO, "Auto")
        btnRes9_16 = createResBtn(com.tajasb.model.ResolutionPreset.PORTRAIT_1080P, "9:16 (1080p)")
        btnRes1080 = createResBtn(com.tajasb.model.ResolutionPreset.FHD_1080P, "16:9 (1080p)")

        btnRes720 = createResBtn(com.tajasb.model.ResolutionPreset.HD_720P, "720p")
        btnResStride = createResBtn(com.tajasb.model.ResolutionPreset.STRIDE_XIAOMI, "1088p Fix")
        btnRes480 = createResBtn(com.tajasb.model.ResolutionPreset.SD_480P, "480p")

        resRow.addView(btnResAuto)
        resRow.addView(btnRes9_16)
        resRow.addView(btnRes1080)

        resRow2.addView(btnRes720)
        resRow2.addView(btnResStride)
        resRow2.addView(btnRes480)

        resCard.addView(resRow)
        resCard.addView(resRow2)
        settingsContent.addView(resCard)

        val featuresCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val btnLightweight = TextView(this).apply {
            text = "⚡ Mode Ringan (Anti-Lag): OFF"
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val next = !repository.config.value.isLightweightMode
                    repository.updateConfig { it.copy(isLightweightMode = next) }
                    Toast.makeText(applicationContext, if (next) "Mode Ringan AKTIF ⚡" else "Mode Ringan NONAKTIF", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnLightweightModeSettings = btnLightweight
        featuresCard.addView(btnLightweight)

        val btnOsd = TextView(this).apply {
            text = "📊 OSD Telemetri Video: OFF"
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val next = repository.toggleDebugOsdInVirtualCamera()
                    updateUiForConfig(repository.config.value)
                    Toast.makeText(applicationContext, if (next) "📊 OSD Virtual Camera: ON (Tampil di Video)" else "📊 OSD Virtual Camera: OFF (Bersih)", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnOsdSettings = btnOsd
        featuresCard.addView(btnOsd)
        settingsContent.addView(featuresCard)

        val targetCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val targetScopeBtn = TextView(this).apply {
            text = "🌐 Target: Semua Aplikasi (Global)"
            textSize = 9.5f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val cfg = repository.config.value
                    if (cfg.primaryTargetPackage.isNotBlank()) {
                        repository.setPrimaryTargetPackage("")
                        Toast.makeText(applicationContext, "Scope diubah ke: Semua Aplikasi (Global)", Toast.LENGTH_SHORT).show()
                    } else {
                        launchTargetApp(showChooser = true)
                    }
                }
            }
        }
        btnTargetScopeSettings = targetScopeBtn
        targetCard.addView(targetScopeBtn)

        val quickAppRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun createQuickAppBtn(pkg: String, label: String): TextView {
            return TextView(this).apply {
                text = label
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(2), dp(5), dp(2), dp(5))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                }
                setOnClickListener {
                    launchSpecificPackage(pkg, label)
                }
            }
        }

        quickAppRow.addView(createQuickAppBtn("com.zhiliaoapp.musically", "🎵 TikTok"))
        quickAppRow.addView(createQuickAppBtn("com.shopee.id", "🛍️ Shopee"))
        quickAppRow.addView(createQuickAppBtn("com.instagram.android", "📸 IG"))
        quickAppRow.addView(createQuickAppBtn("com.whatsapp", "💬 WA"))
        targetCard.addView(quickAppRow)
        settingsContent.addView(targetCard)

        val btnOpenLogMenuShortcut = TextView(this).apply {
            text = "📋 Buka Menu Log & Bukti Live ➔"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(7))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#083344"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                selectTab(FloatingTab.LOG)
            }
        }
        settingsContent.addView(btnOpenLogMenuShortcut)

        FloatingUiWidgets.populateSettingsLogAndFooterCards(
            service = this,
            settingsContent = settingsContent,
            panel = panel,
            dp = dp
        )

        tabContentHolder.addView(tabSettingsContainer)

        FloatingUiWidgets.populateDedicatedLogTab(
            service = this,
            tabContentHolder = tabContentHolder,
            dp = dp
        )

        val bottomDock = createFixedBottomDockBar(dp)
        panel.addView(bottomDock)

        // ---------------------------------------------------------
        // 7. BOTTOM RESIZE & DRAG-TO-FIT BAR
        // ---------------------------------------------------------
        val resizeBottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        val resizeHint = TextView(this).apply {
            text = "📐 Tarik ujung kanan untuk sesuaikan ukuran"
            textSize = 7.5f
            setTextColor(Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        resizeBottomBar.addView(resizeHint)

        val btnResetSize = TextView(this).apply {
            text = "🔄 Reset"
            textSize = 8f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(2), dp(5), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                val defW = dp(290)
                val defH = dp(250)
                wmPanelParams.width = defW
                tabContentScrollView?.layoutParams?.height = defH
                tabContentScrollView?.requestLayout()
                if (expandedControlPanelView?.windowToken != null) {
                    try { windowManager.updateViewLayout(panel, wmPanelParams) } catch (_: Exception) {}
                }
                getSharedPreferences("vcam_settings", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putInt("floating_panel_custom_width", defW)
                    .putInt("floating_panel_custom_scroll_height", defH)
                    .apply()
                Toast.makeText(applicationContext, "Ukuran panel direset ke default (290dp)", Toast.LENGTH_SHORT).show()
            }
        }
        resizeBottomBar.addView(btnResetSize)

        val cornerResizeGrip = TextView(this).apply {
            text = " 📐 ⤡ "
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0C4A6E"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
        }
        setupPanelCornerResize(cornerResizeGrip, wmPanelParams, tabContentScrollView)
        resizeBottomBar.addView(cornerResizeGrip)
        panel.addView(resizeBottomBar)

        setupPanelDrag(dragHandle, wmPanelParams)
        setupPanelDrag(header, wmPanelParams)
        expandedControlPanelView = panel

        selectTab(FloatingTab.VIDEO)
    }
}
