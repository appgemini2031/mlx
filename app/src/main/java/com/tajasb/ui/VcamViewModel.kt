package com.tajasb.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tajasb.FloatingControlService
import com.tajasb.data.VcamRepository
import com.tajasb.model.HookEngineType
import com.tajasb.model.MediaMetadata
import com.tajasb.model.ResolutionPreset
import com.tajasb.model.ScaleMode
import com.tajasb.model.TargetAppInfo
import com.tajasb.model.VcamConfig
import com.tajasb.optimizer.DeviceMemoryStats
import com.tajasb.optimizer.DeviceOptimizationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class VcamViewModel(application: Application) : AndroidViewModel(application) {

    val repository = VcamRepository.getInstance(application)

    val config: StateFlow<VcamConfig> = repository.config

    val targetApps: StateFlow<List<TargetAppInfo>> = repository.targetApps

    val isMusicAutoPausedWaitingForMute: StateFlow<Boolean> = repository.isMusicAutoPausedWaitingForMute

    val videoVolume: StateFlow<Float> = repository.videoVolume

    val pendingVideoIndex: StateFlow<Int?> = repository.pendingVideoIndex

    val playbackStepVersion: StateFlow<Long> = repository.playbackStepVersion

    val videoSeekEvent: StateFlow<Pair<Long, Long>?> = repository.videoSeekEvent

    val licenseManager = com.tajasb.license.LicenseManager.getInstance(application)
    val licenseStatus: StateFlow<com.tajasb.model.LicenseStatus> = licenseManager.licenseStatus
    val deviceId: String = licenseManager.getDeviceId()

    fun activateLicense(refNo: String, activationKey: String, onResult: ((com.tajasb.model.ActivationResult) -> Unit)? = null) {
        viewModelScope.launch {
            // Realistic handshake delay (1.8s) so the user experiences an authentic server gateway verification attempt
            kotlinx.coroutines.delay(1800)
            val res = licenseManager.activate(refNo, activationKey)
            if (res is com.tajasb.model.ActivationResult.Success) {
                _userMessage.value = res.message
            } else if (res is com.tajasb.model.ActivationResult.Error) {
                _userMessage.value = res.message
            }
            onResult?.invoke(res)
        }
    }

    fun deactivateLicense() {
        licenseManager.deactivate()
        _userMessage.value = "Lisensi telah dinonaktifkan."
    }

    fun refreshLicense() {
        licenseManager.refreshLicenseStatus()
        licenseManager.checkRemoteBlacklist { blocked ->
            if (blocked) {
                _userMessage.value = "⛔ Akses aplikasi telah dinonaktifkan oleh administrator."
            }
        }
        com.tajasb.license.TimeGuard.getInstance(getApplication()).triggerNetworkTimeSync {
            licenseManager.refreshLicenseStatus()
        }
        viewModelScope.launch {
            try {
                com.tajasb.remote.RemoteTelemetryManager.getInstance(getApplication()).triggerImmediateSync()
                val activated = com.tajasb.tripay.TripayManager.getInstance(getApplication()).checkPendingTransaction()
                if (activated) {
                    licenseManager.refreshLicenseStatus()
                }
            } catch (_: Throwable) {}
        }
    }

    fun setVideoVolume(vol: Float) {
        repository.setVideoVolume(vol)
    }

    fun seekVideo(posMs: Long) {
        repository.seekVideo(posMs)
    }

    fun seekVideoToLast3Seconds() {
        val cur = config.value.currentVideo
        val rawDur = when {
            cur != null && cur.durationMs > 0L -> cur.durationMs
            (_activeMediaMetadata.value?.durationMs ?: 0L) > 0L -> _activeMediaMetadata.value?.durationMs ?: 0L
            else -> repository.realVideoDurationMs.value
        }
        val targetMs = if (rawDur > 3200L) (rawDur - 3000L) else (rawDur * 75L / 100L).coerceAtLeast(0L)
        repository.seekVideo(targetMs)
        _userMessage.value = "⏩ Lompat ke 3 detik akhir video (${targetMs / 1000}s / ${rawDur / 1000}s)"
    }

    fun seekAudioToLast3Seconds() {
        val dur = musicDurationMs.value.takeIf { it > 0L }
            ?: currentPlayingTrack.value?.durationMs?.takeIf { it > 0L }
            ?: config.value.currentAudio?.durationMs?.takeIf { it > 0L }
            ?: 0L
        if (dur <= 0L) {
            _userMessage.value = "⚠️ Belum ada audio aktif yang sedang diputar"
            return
        }
        val targetMs = if (dur > 3200L) (dur - 3000L) else (dur * 75L / 100L).coerceAtLeast(0L)
        repository.seekAudio(targetMs)
        try {
            com.tajasb.infinity.IfsManager.getInstance(getApplication()).onAudioSeeked(targetMs)
        } catch (_: Throwable) {}
        _userMessage.value = "⏩ Lompat ke 3 detik akhir audio (${targetMs / 1000}s / ${dur / 1000}s)"
    }

    private val _isXposedActive = MutableStateFlow(repository.isXposedModuleActive())
    val isXposedActive: StateFlow<Boolean> = _isXposedActive.asStateFlow()

    private val _activeMediaMetadata = MutableStateFlow<MediaMetadata?>(null)
    val activeMediaMetadata: StateFlow<MediaMetadata?> = _activeMediaMetadata.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _selectedMainTab = MutableStateFlow(0)
    val selectedMainTab: StateFlow<Int> = _selectedMainTab.asStateFlow()

    private val _selectedGameIndex = MutableStateFlow(0)
    val selectedGameIndex: StateFlow<Int> = _selectedGameIndex.asStateFlow()

    fun setSelectedMainTab(tabIndex: Int) {
        _selectedMainTab.value = tabIndex.coerceIn(0, 5)
    }

    fun setSelectedGameIndex(gameIndex: Int) {
        _selectedGameIndex.value = gameIndex.coerceIn(0, 3)
    }

    fun openGameFromIntent(gameIndex: Int) {
        _selectedMainTab.value = 2
        _selectedGameIndex.value = gameIndex.coerceIn(0, 3)
    }

    private val _isStitchingVideos = MutableStateFlow(false)
    val isStitchingVideos: StateFlow<Boolean> = _isStitchingVideos.asStateFlow()

    private val _stitchProgressText = MutableStateFlow<String?>(null)
    val stitchProgressText: StateFlow<String?> = _stitchProgressText.asStateFlow()

    fun stitchStandardPlaylistVideos() {
        if (_isStitchingVideos.value) return
        viewModelScope.launch {
            _isStitchingVideos.value = true
            _stitchProgressText.value = "Menyiapkan penggabungan video..."
            try {
                val res = repository.stitchPlaylistToSingleVideo { pct, msg ->
                    _stitchProgressText.value = "$pct% • $msg"
                }
                _userMessage.value = res.message
            } catch (e: Throwable) {
                _userMessage.value = "⚠️ Gagal menggabungkan video: ${e.message ?: "Error"}"
            } finally {
                _isStitchingVideos.value = false
                _stitchProgressText.value = null
            }
        }
    }

    val memoryStats: StateFlow<DeviceMemoryStats?> = DeviceOptimizationManager.statsFlow

    private val _lastReadinessReport = MutableStateFlow<com.tajasb.optimizer.LiveReadinessReport?>(null)
    val lastReadinessReport: StateFlow<com.tajasb.optimizer.LiveReadinessReport?> = _lastReadinessReport.asStateFlow()

    init {
        refreshStatus()
        loadInstalledApps()
        refreshMemoryStats()
        DeviceOptimizationManager.startAutoRamGuard(getApplication(), viewModelScope)
        refreshLicense()
        viewModelScope.launch {
            try {
                // Bersihkan otomatis file duplikat yang terlanjur masuk ke folder publik Galeri HP (DCIM/Movies/Download)
                val cleanupReport = repository.cleanGalleryDuplicates()
                val backupRes = com.tajasb.data.ConceptBackupManager
                    .getInstance(getApplication())
                    .checkAndAutoRestoreOnStartup()
                if (!backupRes.message.isNullOrBlank()) {
                    _userMessage.value = backupRes.message
                } else if (cleanupReport.deletedFilesCount > 0) {
                    val freedMb = String.format(java.util.Locale.US, "%.1f MB", cleanupReport.freedBytes.toDouble() / (1024.0 * 1024.0))
                    _userMessage.value = "🧹 Auto-Clean Galeri: ${cleanupReport.deletedFilesCount} video duplikat di Galeri berhasil dibersihkan ($freedMb dibebaskan)."
                }
            } catch (_: Throwable) {}
        }
    }

    fun refreshMemoryStats() {
        DeviceOptimizationManager.getMemoryStats(getApplication())
    }

    fun runPreFlightCheckAndHeal() {
        viewModelScope.launch {
            _userMessage.value = "🛡️ Memeriksa & memperbaiki kesiapan Live 24 Jam..."
            val report = DeviceOptimizationManager.runLiveReadinessDiagnostic(
                context = getApplication(),
                autoFix = true
            )
            _lastReadinessReport.value = report
            refreshMemoryStats()
            refreshStatus()
            _userMessage.value = report.summaryTitle
        }
    }

    fun triggerTurboRamClean() {
        viewModelScope.launch {
            _userMessage.value = "⚡ Membersihkan RAM & proses background..."
            val res = repository.triggerTurboRamClean()
            _userMessage.value = if (res.freedMb > 0) {
                "⚡ RAM Dibersihkan! +${res.freedMb} MB Bebas (${res.availableMb} MB Tersedia). ${res.killedProcessesCount} aplikasi latar ditutup."
            } else {
                "⚡ RAM Dibersihkan! Kondisi memori optimal (${res.availableMb} MB Tersedia). ${res.killedProcessesCount} proses latar ditutup."
            }
            refreshMemoryStats()
        }
    }

    fun cleanGalleryDuplicates() {
        viewModelScope.launch {
            _userMessage.value = "🧹 Membersihkan video duplikat di Galeri HP & menyinkronkan Vault internal..."
            try {
                val report = repository.cleanGalleryDuplicates()
                val freedMb = String.format(java.util.Locale.US, "%.1f MB", report.freedBytes.toDouble() / (1024.0 * 1024.0))
                _userMessage.value = if (report.deletedFilesCount > 0) {
                    "✅ Bersih! ${report.deletedFilesCount} file duplikat Galeri/Vault dihapus ($freedMb dibebaskan). Konsep Backup tetap aman."
                } else {
                    "✨ Galeri HP & Vault sudah bersih! Tidak ditemukan file video duplikat."
                }
                refreshMemoryStats()
                refreshStatus()
            } catch (e: Exception) {
                _userMessage.value = "⚠️ Gagal membersihkan duplikat Galeri: ${e.message}"
            }
        }
    }

    fun toggleLiveFocusVpn(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleLiveFocusVpn(enabled)
            _userMessage.value = if (next) {
                "🛡️ FOKUS JARINGAN LIVE: AKTIF! (Koneksi difokuskan penuh untuk aplikasi pilihan Anda, aplikasi lain diblokir)"
            } else {
                "Fokus Jaringan Dinonaktifkan (Koneksi normal dipulihkan)"
            }
            refreshMemoryStats()
        }
    }

    fun updateRamCleanWhitelist(packages: Set<String>) {
        viewModelScope.launch {
            repository.updateRamCleanWhitelist(packages)
            _userMessage.value = "✅ Whitelist RAM diperbarui (${packages.size} aplikasi dilindungi)"
            refreshMemoryStats()
        }
    }

    fun updateVpnBypassPackages(packages: Set<String>) {
        viewModelScope.launch {
            repository.updateVpnBypassPackages(packages)
            _userMessage.value = "✅ Aturan Jaringan diperbarui (${packages.size} aplikasi bebas akses, lainnya diblokir)"
            refreshMemoryStats()
        }
    }

    fun addBlockedAdPackage(packageName: String, appName: String = "") {
        viewModelScope.launch {
            val resMsg = repository.addBlockedAdPackage(packageName)
            val name = if (appName.isNotBlank()) appName else packageName
            _userMessage.value = "🚫 $name ditambahkan ke daftar blokir! $resMsg"
            refreshMemoryStats()
        }
    }

    fun removeBlockedAdPackage(packageName: String, appName: String = "") {
        viewModelScope.launch {
            val resMsg = repository.removeBlockedAdPackage(packageName)
            val name = if (appName.isNotBlank()) appName else packageName
            _userMessage.value = "🟢 Blokir untuk $name dicabut. $resMsg"
            refreshMemoryStats()
        }
    }

    fun toggleAdBlocker(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleAdBlocker(enabled)
            val count = config.value.blockedAdPackages.size
            _userMessage.value = if (next) {
                "🚫 BLOKIR DATA IKLAN: AKTIF! ($count aplikasi pembuat iklan diputus total 0 KB/s)"
            } else {
                "Blokir Data Iklan Dinonaktifkan (Koneksi dipulihkan)"
            }
            refreshMemoryStats()
        }
    }

    fun toggleAdBlockerAutoFreeze(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleAdBlockerAutoFreeze(enabled)
            _userMessage.value = if (next) "Auto-Freeze Iklan: AKTIF (Aplikasi iklan otomatis dibekukan saat live)" else "Auto-Freeze Iklan: OFF"
        }
    }

    fun forceStopBlockedApp(packageName: String, appName: String = "") {
        viewModelScope.launch {
            val killed = repository.forceStopBlockedApp(packageName)
            val name = if (appName.isNotBlank()) appName else packageName
            _userMessage.value = if (killed) "🛑 Proses $name berhasil dihentikan paksa!" else "Gagal menghentikan $name"
            refreshMemoryStats()
        }
    }

    fun suspendBlockedApp(packageName: String, suspend: Boolean, appName: String = "") {
        viewModelScope.launch {
            val ok = repository.suspendBlockedApp(packageName, suspend)
            val name = if (appName.isNotBlank()) appName else packageName
            _userMessage.value = if (ok) {
                if (suspend) "❄️ $name dibekukan oleh OS (Tidak bisa bangun/munculkan iklan)!" else "🟢 $name dibuka dari pembekuan"
            } else {
                "Gagal membekukan $name (Fitur pembekuan memerlukan akses Root)"
            }
        }
    }

    fun openAppOverlaySettings(packageName: String) {
        com.tajasb.optimizer.AppAdBlockerManager.openOverlayPermissionSettings(getApplication(), packageName)
    }

    fun openAppDetailsSettings(packageName: String) {
        com.tajasb.optimizer.AppAdBlockerManager.openAppDetailsSettings(getApplication(), packageName)
    }

    fun toggleAutoRamClean(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleAutoRamClean(enabled)
            _userMessage.value = if (next) "Auto-Guard RAM: AKTIF (Pembersihan otomatis jika RAM < 380 MB)" else "Auto-Guard RAM: OFF"
        }
    }

    fun setFpsGovernorLimit(fps: Int) {
        viewModelScope.launch {
            repository.setFpsGovernorLimit(fps)
            _userMessage.value = "🎮 Stabilisator Frame Rate: $fps FPS ${if (fps == 30) "(Rekomendasi Hemat Suhu)" else ""}"
            refreshMemoryStats()
        }
    }

    fun toggleAutoRocketRecovery(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleAutoRocketRecovery(enabled)
            _userMessage.value = if (next) {
                "🚀 Auto-Rocket (Anti-Freeze): AKTIF! (Stream macet akan dipulihkan seketika otomatis tanpa disentuh)"
            } else {
                "Auto-Rocket Dinonaktifkan"
            }
        }
    }

    fun setAutoRecoveryStallThresholdSec(sec: Int) {
        viewModelScope.launch {
            repository.setAutoRecoveryStallThresholdSec(sec)
            _userMessage.value = "⏱️ Waktu Deteksi Freeze: $sec Detik"
        }
    }

    fun togglePeriodicRamClean(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.togglePeriodicRamClean(enabled)
            _userMessage.value = if (next) {
                "🧹 Pembersihan RAM Rutin: AKTIF (${config.value.periodicRamCleanIntervalMinutes} menit sekali)"
            } else {
                "Pembersihan RAM Rutin Dinonaktifkan"
            }
        }
    }

    fun setPeriodicRamCleanIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            repository.setPeriodicRamCleanIntervalMinutes(minutes)
            _userMessage.value = "⏱️ Interval Pembersihan RAM: $minutes Menit"
        }
    }

    fun resetAutoRecoveryCount() {
        viewModelScope.launch {
            repository.resetAutoRecoveryCount()
            _userMessage.value = "Hitungan pemulihan otomatis direset ke 0"
        }
    }

    fun testAutoRocketRecovery() {
        viewModelScope.launch {
            _userMessage.value = "🚀 Menguji coba Auto-Rocket pemulihan sekarang..."
            val ctx = getApplication<android.app.Application>()
            try {
                // Trigger Turbo RAM Clean
                repository.triggerTurboRamClean()
                // Broadcast reload
                val reloadIntent = android.content.Intent("com.tajasb.action.RELOAD_MEDIA")
                ctx.sendBroadcast(reloadIntent)
                // Record recovery
                val count = repository.recordAutoRecovery()
                _userMessage.value = "✅ Tes Auto-Rocket Berhasil! Live stream disegarkan (Total Pulih: $count)"
            } catch (e: Exception) {
                _userMessage.value = "Gagal tes Auto-Rocket: ${e.message}"
            }
        }
    }

    val aiWatermarkReport: StateFlow<com.tajasb.data.AiWatermarkReport?> = repository.aiWatermarkReport
    val isScanningAiWatermark: StateFlow<Boolean> = repository.isScanningAiWatermark
    val isCleaningAiWatermark: StateFlow<Boolean> = repository.isCleaningAiWatermark

    private val _showAiWatermarkDetailDialog = MutableStateFlow(false)
    val showAiWatermarkDetailDialog: StateFlow<Boolean> = _showAiWatermarkDetailDialog.asStateFlow()

    fun openAiWatermarkDetailDialog() {
        _showAiWatermarkDetailDialog.value = true
    }

    fun dismissAiWatermarkDetailDialog() {
        _showAiWatermarkDetailDialog.value = false
    }

    fun toggleAiDetectionModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleAiDetectionModule(enabled)
            _userMessage.value = if (next) "🧹 Modul Hapus Kode AI: AKTIF" else "🧹 Modul Hapus Kode AI: NONAKTIF"
            if (!next) {
                _showAiWatermarkDetailDialog.value = false
            }
        }
    }

    fun toggleDnaModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleDnaModule(enabled)
            val dna = config.value.activeSessionDna
            _userMessage.value = if (next) {
                "🧬 Modul DNA Anti-Deteksi: AKTIF (${dna.badgeText})"
            } else {
                "🧬 Modul DNA Anti-Deteksi: NONAKTIF (Zero-Load)"
            }
        }
    }

    fun scanActiveVideoAiWatermark(showDetailDialog: Boolean = false) {
        if (!config.value.enableAiDetectionModule) {
            _userMessage.value = "⚠️ Aktifkan saklar Deteksi Kode AI terlebih dahulu."
            return
        }
        viewModelScope.launch {
            val report = repository.scanActiveVideoAiWatermark()
            if (report == null) {
                _userMessage.value = "⚠️ Belum ada file video aktif untuk dipindai."
            } else {
                _userMessage.value = report.buildNotificationHeadline()
                if (showDetailDialog) {
                    _showAiWatermarkDetailDialog.value = true
                }
            }
        }
    }

    fun cleanActiveVideoAiWatermark() {
        if (!config.value.enableAiDetectionModule) {
            _userMessage.value = "⚠️ Modul Deteksi Kode AI sedang nonaktif."
            return
        }
        if (isCleaningAiWatermark.value) return
        viewModelScope.launch {
            _userMessage.value = "🧹 Mengupas kode AI (C2PA/JUMBF/XMP/Metadata) & mengaktifkan perisai Anti-Hash..."
            val result = repository.cleanActiveVideoAiWatermark()
            _userMessage.value = result.message
            refreshStatus()
        }
    }

    fun refreshStatus() {
        _isXposedActive.value = repository.isXposedModuleActive()
        val currentVideo = config.value.videoPath
        if (currentVideo.isNotEmpty()) {
            _activeMediaMetadata.value = repository.retrieveMediaMetadata(currentVideo)
            if (config.value.enableAiDetectionModule) {
                viewModelScope.launch {
                    repository.scanActiveVideoAiWatermark(currentVideo)
                }
            }
        }
    }

    fun setAntiHashMode(mode: com.tajasb.model.AntiHashIntensity) {
        viewModelScope.launch {
            repository.setAntiHashMode(mode)
            _userMessage.value = "🛡️ Anti-Deteksi Video: ${mode.label}"
        }
    }

    fun toggleDnaZoom(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleDnaZoom(enabled)
            _userMessage.value = if (next) {
                "🎯 DNA Zoom & Framing: AKTIF (${String.format(java.util.Locale.US, "%.2f", config.value.sessionDnaCustomZoom)}x)"
            } else {
                "🎯 DNA Zoom & Framing: NONAKTIF (1.00x)"
            }
        }
    }

    fun toggleAudioAntiHash(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleAudioAntiHash(enabled)
            _userMessage.value = if (next) "🛡️ Audio Anti-Deteksi: Aktif" else "🛡️ Audio Anti-Deteksi: Nonaktif"
        }
    }

    fun setAudioAntiHashMode(mode: com.tajasb.model.AudioAntiHashIntensity) {
        viewModelScope.launch {
            repository.setAudioAntiHashMode(mode)
            _userMessage.value = "🛡️ Audio Anti-Deteksi: ${mode.displayName}"
        }
    }

    fun rotateSessionDna() {
        viewModelScope.launch {
            val dna = repository.rotateToNextSessionDna("manual_ui")
            _userMessage.value = "🧬 ${dna.badgeText} (${dna.quadrantLabel}) — ${dna.summaryText}"
        }
    }

    fun toggleSafeCodecGuard(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleSafeCodecGuard(enabled)
            _userMessage.value = if (next) "🛡️ Codec Guard & Anti-Crash RAM: AKTIF" else "Codec Guard: Nonaktif"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showUserMessage(msg: String) {
        _userMessage.value = msg
        refreshStatus()
    }

    fun toggleMasterSwitch(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateConfig { it.copy(isMasterEnabled = enabled) }
            repository.broadcastControlEvent(action = "UPDATE_CONFIG", isMasterEnabled = enabled)
            _userMessage.value = if (enabled) "Layanan Virtual Camera Diaktifkan" else "Layanan Virtual Camera Dinonaktifkan"
        }
    }

    fun toggleSalesNotifModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableSalesNotifModule
            repository.updateConfig { it.copy(enableSalesNotifModule = next) }
            if (!next) {
                com.tajasb.notif.BuyerNotificationManager.getInstance(getApplication()).toggleEnabled(false)
            }
        }
    }

    fun toggleStickerModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableStickerModule
            repository.updateConfig { it.copy(enableStickerModule = next) }
            if (!next) {
                val sm = com.tajasb.stiker.CustomStikerManager.getInstance(getApplication())
                sm.toggleEnabled(false)
                sm.stopRandomMoveLoop()
            }
        }
    }

    fun toggleWatermarkModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableWatermarkModule
            repository.updateConfig { it.copy(enableWatermarkModule = next) }
            if (!next) {
                val wm = com.tajasb.stiker.CustomWatermarkManager.getInstance(getApplication())
                wm.toggleEnabled(false)
                wm.stopRandomMoveLoop()
            }
        }
    }

    fun toggleAutoTimeModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableAutoTimeModule
            repository.updateConfig { it.copy(enableAutoTimeModule = next) }
            if (!next) {
                val atm = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(getApplication())
                atm.toggleEnabled(false)
            }
        }
    }

    fun togglePrayerCommentModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enablePrayerCommentModule
            repository.updateConfig { it.copy(enablePrayerCommentModule = next) }
            if (!next) {
                val lm = com.tajasb.livecomment.LiveCommentManager.getInstance(getApplication())
                lm.toggleCaptureActive(false)
                lm.clearQueue()
                com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(getApplication(), "COMMENT")
            }
        }
    }

    fun toggleQuranKhatamModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableQuranKhatamModule
            repository.updateConfig { it.copy(enableQuranKhatamModule = next) }
            if (!next) {
                val qm = com.tajasb.quran.InteractiveQuranKhatamEngine.getInstance(getApplication())
                qm.setEnabled(false)
                com.tajasb.notif.OverlayBannerBroadcaster.broadcastHide(getApplication(), "QURAN_KHATAM")
            }
        }
    }

    fun toggleMakkahRelayModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableMakkahRelayModule
            repository.updateConfig { it.copy(enableMakkahRelayModule = next) }
            if (!next) {
                repository.updateConfig { it.copy(isLiveStreamSourceActive = false) }
            }
        }
    }

    fun toggleLiveEventBrokerModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableLiveEventBrokerModule
            repository.updateConfig { it.copy(enableLiveEventBrokerModule = next) }
            if (!next) {
                com.tajasb.eventrelay.LiveEventPreferences.getInstance(getApplication()).setAutoConnect(false)
            }
        }
    }

    fun toggleScanInspectorModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableScanInspectorModule
            repository.updateConfig { it.copy(enableScanInspectorModule = next) }
            if (!next) {
                com.tajasb.livecomment.CommentScanInspector.getInstance(getApplication()).setInspectorActive(false)
            }
        }
    }

    fun toggleAutoTapModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableAutoTapModule
            repository.updateConfig { it.copy(enableAutoTapModule = next) }
            if (!next) {
                val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(getApplication())
                autoTap.stopTapping()
                autoTap.setPointerVisible(false)
            }
        }
    }

    fun toggleMp3MusicModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableMp3MusicModule
            repository.updateConfig { it.copy(enableMp3MusicModule = next) }
            if (!next) {
                repository.pauseAudio()
            }
        }
    }

    fun toggleOsdModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableOsdModule
            repository.updateConfig { it.copy(enableOsdModule = next, isDebugOsdInVirtualCameraEnabled = if (next) it.isDebugOsdInVirtualCameraEnabled else false) }
            repository.broadcastControlEvent(action = "UPDATE_CONFIG")
            if (!next) {
                try {
                    val directIntent = android.content.Intent("com.tajasb.action.TOGGLE_DEBUG_OSD").apply {
                        putExtra("extra_debug_osd_enabled", false)
                    }
                    getApplication<android.app.Application>().sendBroadcast(directIntent)
                } catch (_: Exception) {}
            }
        }
    }

    fun toggleQuranAudioModule(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = enabled ?: !config.value.enableQuranAudioModule
            repository.toggleQuranAudioModule(next)
            _userMessage.value = if (next) "📖 Audio Al-Qur'an: TAMPIL (ON)" else "📖 Audio Al-Qur'an: HILANG (OFF)"
        }
    }

    fun setRotation(rotation: Int) {
        viewModelScope.launch {
            repository.updateConfig { it.copy(rotation = rotation) }
            repository.broadcastControlEvent(action = "CYCLE_ROTATION", rotation = rotation)
            _userMessage.value = "Rotasi: $rotation°"
        }
    }

    fun cycleRotation() {
        viewModelScope.launch {
            val newRot = repository.cycleRotation()
            _userMessage.value = "Rotasi: $newRot°"
        }
    }

    fun toggleMirror(isMirrored: Boolean? = null) {
        viewModelScope.launch {
            val nextMirror = isMirrored ?: !config.value.isMirrored
            repository.updateConfig { it.copy(isMirrored = nextMirror) }
            repository.broadcastControlEvent(action = "MIRROR", isMirrored = nextMirror)
            _userMessage.value = if (nextMirror) "Mirror ON" else "Mirror OFF"
        }
    }

    fun toggleFlipVertical(isFlipped: Boolean? = null) {
        viewModelScope.launch {
            val nextFlip = isFlipped ?: !config.value.isVerticalFlipped
            repository.updateConfig { it.copy(isVerticalFlipped = nextFlip) }
            repository.broadcastControlEvent(action = "FLIP_VERTICAL", isVerticalFlipped = nextFlip)
            _userMessage.value = if (nextFlip) "Flip V: ON" else "Flip V: OFF"
        }
    }

    fun setZoomFactor(zoom: Float) {
        viewModelScope.launch {
            val res = repository.setZoomFactor(zoom)
            _userMessage.value = "Zoom: ${String.format(java.util.Locale.US, "%.2f", res)}x"
        }
    }

    fun adjustZoom(delta: Float) {
        viewModelScope.launch {
            val res = repository.adjustZoom(delta)
            _userMessage.value = "Zoom: ${String.format(java.util.Locale.US, "%.2f", res)}x"
        }
    }

    fun togglePauseVideo() {
        viewModelScope.launch {
            val isPaused = repository.togglePauseVideo()
            _userMessage.value = if (isPaused) "Video Dijeda" else "Video Diputar"
        }
    }

    fun toggleVideoAudio() {
        viewModelScope.launch {
            val isMuted = repository.toggleAllVideosMute()
            _userMessage.value = if (isMuted) "Semua Video Dimute 🔇" else "Semua Video Bersuara 🔊"
        }
    }

    fun toggleVideoMute(index: Int) {
        viewModelScope.launch {
            val isMuted = repository.toggleVideoMute(index)
            _userMessage.value = if (isMuted) "Video #${index + 1}: Dimute (Musik tetap play)" else "Video #${index + 1}: Bersuara (Musik auto-pause)"
        }
    }

    fun toggleVideoEnable(index: Int) {
        viewModelScope.launch {
            val isEnabled = repository.toggleVideoEnable(index)
            _userMessage.value = if (isEnabled) "Video #${index + 1}: Ditampilkan & Aktif di Antrian" else "Video #${index + 1}: Disembunyikan / Dikeluarkan dari Antrian"
        }
    }

    fun toggleAllVideosEnabled() {
        viewModelScope.launch {
            val isEnabled = repository.toggleAllVideosEnabled()
            _userMessage.value = if (isEnabled) "Semua Video Diaktifkan 👁️" else "Semua Video Ditutup 🙈"
        }
    }

    fun toggleMp3Audio() {
        viewModelScope.launch {
            val isPaused = repository.toggleMp3Audio()
            _userMessage.value = if (isPaused) "Audio MP3 Dijeda" else "Audio MP3 Diputar"
        }
    }

    fun togglePauseAudio() {
        toggleMp3Audio()
    }

    fun toggleLiveStreamSource() {
        viewModelScope.launch {
            val active = repository.toggleLiveStreamSource()
            _userMessage.value = if (active) {
                "🕋 Web Live Stream Diaktifkan! Feed disalurkan ke Kamera Virtual TikTok."
            } else {
                "🎬 Beralih kembali ke Playlist Video Lokal."
            }
        }
    }

    fun setLiveStreamSource(active: Boolean, url: String? = null, title: String? = null) {
        viewModelScope.launch {
            repository.setLiveStreamSource(active, url, title)
            _userMessage.value = if (active) {
                "🕋 Web Live Stream Diaktifkan: ${title ?: "Live Web"} ➔ Kamera Virtual."
            } else {
                "🎬 Mode Live Stream Dimatikan. Kembali ke Video Lokal."
            }
        }
    }

    fun togglePause() {
        togglePauseVideo()
    }

    fun setFloatingAlpha(alpha: Float) {
        viewModelScope.launch {
            repository.setFloatingAlpha(alpha)
        }
    }

    fun setScaleMode(mode: ScaleMode) {
        viewModelScope.launch {
            repository.updateConfig { it.copy(scaleMode = mode) }
            repository.broadcastControlEvent(action = "SCALE", scaleMode = mode.name)
            _userMessage.value = "Mode Skala: ${mode.label}"
        }
    }

    fun cycleScaleMode() {
        viewModelScope.launch {
            val nextMode = repository.cycleScaleMode()
            _userMessage.value = "Mode Skala: ${nextMode.label}"
        }
    }

    fun toggleAudio(enabled: Boolean? = null) {
        viewModelScope.launch {
            val nextAudio = enabled ?: !config.value.audioEnabled
            repository.updateConfig { it.copy(audioEnabled = nextAudio) }
            repository.broadcastControlEvent(action = "AUDIO", audioEnabled = nextAudio)
            _userMessage.value = if (nextAudio) "Audio Video Aktif (Unmuted)" else "Audio Video Dibisukan (Muted)"
        }
    }

    fun setResolution(preset: ResolutionPreset) {
        viewModelScope.launch {
            repository.updateConfig { it.copy(forceResolution = preset) }
            _userMessage.value = "Resolusi: ${preset.label}"
        }
    }

    fun setHookEngine(engine: HookEngineType) {
        viewModelScope.launch {
            repository.updateConfig { it.copy(hookEngine = engine) }
            _userMessage.value = "Engine Hook: ${engine.label}"
        }
    }

    fun setLightweightMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateConfig { it.copy(isLightweightMode = enabled) }
            _userMessage.value = if (enabled) "Mode Ringan AKTIF ⚡" else "Mode Ringan NONAKTIF"
        }
    }

    fun setDebugOsdInVirtualCamera(enabled: Boolean) {
        viewModelScope.launch {
            val next = repository.toggleDebugOsdInVirtualCamera(enabled)
            _userMessage.value = if (next) "📊 OSD Virtual Camera: ON (Tampil di Video)" else "📊 OSD Virtual Camera: OFF (Bersih)"
            refreshStatus()
        }
    }

    fun restartHookServices() {
        viewModelScope.launch {
            repository.updateConfig { it }
            repository.broadcastControlEvent(action = "RELOAD_MEDIA")
            _userMessage.value = "Layanan Hook & Konfigurasi dimuat ulang! ⚡"
            refreshStatus()
        }
    }

    fun onVideosSelected(uris: List<Uri>) {
        viewModelScope.launch {
            val added = repository.addVideos(uris)
            if (added > 0) {
                refreshStatus()
                val report = repository.scanActiveVideoAiWatermark()
                if (report != null) {
                    _userMessage.value = "✅ $added video ditambahkan! ${report.buildNotificationHeadline()}"
                    if (report.severity == com.tajasb.data.AiWatermarkSeverity.AI_DETECTED) {
                        _showAiWatermarkDetailDialog.value = true
                    }
                } else {
                    _userMessage.value = "Berhasil menambahkan $added video ke daftar putar!"
                }
            } else {
                _userMessage.value = "Gagal memproses file video"
            }
        }
    }

    fun removeVideo(id: String) {
        viewModelScope.launch {
            repository.removeVideo(id)
            _userMessage.value = "Video dihapus dari daftar putar"
            refreshStatus()
        }
    }

    fun removeAllVideos() {
        viewModelScope.launch {
            repository.removeAllVideos()
            _userMessage.value = "Semua video telah dihapus"
            refreshStatus()
        }
    }

    fun selectVideo(index: Int, immediate: Boolean = true) {
        viewModelScope.launch {
            if (immediate) {
                val selected = repository.selectVideoByIndex(index)
                if (selected != null) {
                    _userMessage.value = "⚡ Beralih langsung ke: #${index + 1} ${selected.name}"
                    refreshStatus()
                }
            } else {
                val queued = repository.queueCustomVideo(index)
                if (queued != null) {
                    _userMessage.value = "⏳ Masuk antrean: #${index + 1} (${queued.name})\nMenunggu video saat ini selesai agar tidak bentrok."
                    refreshStatus()
                }
            }
        }
    }

    fun handleVideoClick(index: Int) {
        val cfg = config.value
        if (cfg.isMasterSpillMode) {
            val clickedItem = cfg.playlist.getOrNull(index)
            val masterItem = cfg.masterVideo
            if (clickedItem != null && masterItem != null && clickedItem.id == masterItem.id) {
                returnToMasterVideo(immediate = !cfg.masterSpillWaitFinish)
            } else {
                triggerSpillVideo(index, immediate = !cfg.masterSpillWaitFinish)
            }
            return
        }
        val isImmediate = (cfg.videoSwitchMode == com.tajasb.model.VideoSwitchMode.INSTANT)
        selectVideo(index, immediate = isImmediate)
    }

    fun handleNextVideoClick() {
        viewModelScope.launch {
            val isIfs = try {
                com.tajasb.infinity.IfsManager.getInstance(getApplication()).isIfsActive.value
            } catch (_: Throwable) { false } || config.value.isIfsMode
            if (isIfs || config.value.videoSwitchMode == com.tajasb.model.VideoSwitchMode.INSTANT) {
                val shouldForceSeq = isIfs || (config.value.autoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP)
                val next = repository.nextVideo(forceSequential = shouldForceSeq)
                if (next != null) {
                    _userMessage.value = "⚡ Beralih langsung ke: ${next.name}"
                    refreshStatus()
                }
            } else {
                val queued = repository.queueNextVideo()
                if (queued != null) {
                    _userMessage.value = "⏳ Video berikutnya (${queued.name}) MASUK ANTREAN!\nMenunggu video saat ini selesai."
                    refreshStatus()
                }
            }
        }
    }

    fun handlePrevVideoClick() {
        viewModelScope.launch {
            if (config.value.videoSwitchMode == com.tajasb.model.VideoSwitchMode.INSTANT) {
                val prev = repository.prevVideo()
                if (prev != null) {
                    _userMessage.value = "⚡ Beralih langsung ke: ${prev.name}"
                    refreshStatus()
                }
            } else {
                val queued = repository.queuePrevVideo()
                if (queued != null) {
                    _userMessage.value = "⏳ Video sebelumnya (${queued.name}) MASUK ANTREAN!\nMenunggu video saat ini selesai."
                    refreshStatus()
                }
            }
        }
    }

    fun setVideoSwitchMode(mode: com.tajasb.model.VideoSwitchMode) {
        viewModelScope.launch {
            repository.setVideoSwitchMode(mode)
            _userMessage.value = "Mode Pindah Video: ${mode.label}"
        }
    }

    fun toggleVideoSwitchMode() {
        viewModelScope.launch {
            val next = repository.toggleVideoSwitchMode()
            _userMessage.value = "Mode Pindah Video: ${next.label}"
        }
    }

    fun queueCustomVideo(index: Int) {
        selectVideo(index, immediate = false)
    }

    // ==========================================
    // MASTER & SPILL (LIVE STREAMING)
    // ==========================================

    fun setMasterVideo(id: String) {
        viewModelScope.launch {
            repository.setMasterVideo(id)
            val item = config.value.playlist.firstOrNull { it.id == id }
            _userMessage.value = "⭐ Video Master ditetapkan: ${item?.name ?: ""}"
        }
    }

    fun activateStandardMode(mode: com.tajasb.model.AutoNextMode? = null) {
        val applied = repository.activateStandardModeSync(mode)
        _userMessage.value = "⚪ Mode Standar Aktif (${applied.label})"
        refreshStatus()
    }

    fun activateMasterSpillMode() {
        repository.activateMasterSpillModeSync(true)
        _userMessage.value = "👑 Mode Master & Spill: AKTIF"
        refreshStatus()
    }

    fun activateIfsMode() {
        val ifsMgr = com.tajasb.infinity.IfsManager.getInstance(getApplication())
        ifsMgr.setIfsEnabled(true)
        _userMessage.value = "♾️ Infinity System (IFS): AKTIF (${ifsMgr.videoMode.value.badge})"
        refreshStatus()
    }

    fun toggleMasterSpillMode(enabled: Boolean? = null) {
        val next = enabled ?: !config.value.isMasterSpillMode
        repository.activateMasterSpillModeSync(next)
        _userMessage.value = if (next) "👑 Mode Master & Spill: AKTIF" else "Mode Master & Spill: NONAKTIF"
        refreshStatus()
    }

    fun setMasterSpillWaitFinish(waitFinish: Boolean) {
        viewModelScope.launch {
            repository.setMasterSpillWaitFinish(waitFinish)
            _userMessage.value = if (waitFinish) "⏳ Tunggu Master selesai loop baru putar spill" else "⚡ Putar spill langsung instan"
        }
    }

    fun toggleAutoSpillFromComments(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleAutoSpillFromComments(enabled)
            _userMessage.value = if (next) "💬 Auto Spill dari Komentar: AKTIF" else "Auto Spill dari Komentar: NONAKTIF"
        }
    }

    fun setVideoSpillKeyword(id: String, keyword: String) {
        viewModelScope.launch {
            repository.setVideoSpillKeyword(id, keyword)
            val clean = keyword.trim()
            _userMessage.value = if (clean.isNotEmpty()) "🏷️ Kata kunci spill: \"$clean\"" else "Kata kunci spill dihapus"
        }
    }

    fun triggerSpillVideo(indexOrId: Any, immediate: Boolean = false) {
        viewModelScope.launch {
            val item = repository.triggerSpillVideo(indexOrId, immediate = immediate)
            if (item != null) {
                if (immediate || !config.value.masterSpillWaitFinish) {
                    _userMessage.value = "🎯 Memutar Spill: ${item.name} (Selesai 1x auto kembali ke Master)"
                } else {
                    _userMessage.value = "⏳ Menghabiskan video master dulu, lalu memutar: ${item.name} (Auto balik ke Master)"
                }
            }
        }
    }

    fun returnToMasterVideo(immediate: Boolean = true) {
        viewModelScope.launch {
            val master = repository.returnToMasterVideo(immediate = immediate)
            if (master != null) {
                _userMessage.value = "⭐ Kembali ke Video Master: ${master.name}"
            }
        }
    }

    fun toggleBridgeCycleMode(enabled: Boolean? = null) {
        val ifsMgr = com.tajasb.infinity.IfsManager.getInstance(getApplication())
        val current = ifsMgr.isIfsActive.value || config.value.isIfsMode
        val next = enabled ?: !current
        if (next) {
            activateIfsMode()
        } else {
            activateStandardMode()
        }
    }

    fun cancelQueuedVideo() {
        viewModelScope.launch {
            repository.cancelQueuedVideo()
            _userMessage.value = "Antrean video dibatalkan."
            refreshStatus()
        }
    }

    fun nextVideo(forceSequential: Boolean? = null) {
        viewModelScope.launch {
            val effForceSeq = forceSequential ?: (config.value.autoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP)
            val next = repository.nextVideo(forceSequential = effForceSeq)
            if (next != null) {
                _userMessage.value = "Beralih ke: ${next.name}"
                refreshStatus()
            }
        }
    }

    fun onPreviewVideoCompleted() {
        val next = repository.nextVideoSync(forceSequential = false)
        if (next != null) {
            refreshStatus()
        }
    }

    fun advanceMasterSpill() {
        val next = repository.advanceMasterSpillSync()
        if (next != null) {
            refreshStatus()
        }
    }

    fun toggleLightweightMode(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleLightweightMode(enabled)
            _userMessage.value = if (next) "⚡ Mode Ringan (Anti-Freeze & Anti-Flicker) DIAKTIFKAN" else "Mode Normal DIAKTIFKAN"
            refreshStatus()
        }
    }

    fun toggleDebugOsdInVirtualCamera(enabled: Boolean? = null) {
        viewModelScope.launch {
            val next = repository.toggleDebugOsdInVirtualCamera(enabled)
            _userMessage.value = if (next) "📹 OSD Detik & MS Virtual Camera DIAKTIFKAN (Tampil di TikTok/Shopee)" else "📹 OSD Virtual Camera DINONAKTIFKAN (Stream Bersih)"
            refreshStatus()
        }
    }

    fun toggleAutoNext(enabled: Boolean? = null) {
        viewModelScope.launch {
            repository.toggleAutoNext(enabled)
            val current = repository.config.value.autoNextEnabled
            val modeLabel = repository.config.value.autoNextMode.label
            _userMessage.value = if (current) "Auto Next Video: AKTIF ($modeLabel)" else "Auto Next Video: NONAKTIF"
        }
    }

    fun setAutoNextMode(mode: com.tajasb.model.AutoNextMode) {
        viewModelScope.launch {
            repository.setAutoNextMode(mode)
            _userMessage.value = "Mode Auto Next diubah: ${mode.label}"
        }
    }

    fun setAutoNextLeadSeconds(seconds: Int) {
        viewModelScope.launch {
            repository.setAutoNextLeadSeconds(seconds)
            _userMessage.value = "Waktu Persiapan Video: $seconds detik sebelum selesai"
        }
    }

    fun setAutoNextLeadMs(ms: Int) {
        viewModelScope.launch {
            repository.setAutoNextLeadMs(ms)
        }
    }

    fun prevVideo() {
        viewModelScope.launch {
            val prev = repository.prevVideo()
            if (prev != null) {
                _userMessage.value = "Beralih ke: ${prev.name}"
                refreshStatus()
            }
        }
    }

    private fun runMediaMutation(action: suspend () -> Boolean, successMessage: String) {
        viewModelScope.launch {
            if (action()) {
                _userMessage.value = successMessage
                refreshStatus()
            }
        }
    }

    fun moveVideoUp(index: Int) = runMediaMutation(
        action = { repository.moveVideoUp(index) },
        successMessage = "Urutan video dipindahkan naik"
    )

    fun moveVideoDown(index: Int) = runMediaMutation(
        action = { repository.moveVideoDown(index) },
        successMessage = "Urutan video dipindahkan turun"
    )

    fun renameVideo(id: String, newName: String) = runMediaMutation(
        action = { repository.renameVideo(id, newName) },
        successMessage = "Nama video & berkas di File Manager diubah: $newName"
    )

    fun renameAudio(id: String, newName: String) = runMediaMutation(
        action = { repository.renameAudio(id, newName) },
        successMessage = "Nama audio & berkas di File Manager diubah: $newName"
    )

    private val _batchAudioConfig = MutableStateFlow(com.tajasb.audio.BatchAudioEditConfig())
    val batchAudioConfig: StateFlow<com.tajasb.audio.BatchAudioEditConfig> = _batchAudioConfig.asStateFlow()

    private val _isTestingSingleAudio = MutableStateFlow(false)
    val isTestingSingleAudio: StateFlow<Boolean> = _isTestingSingleAudio.asStateFlow()

    private val _isApplyingBatchAudio = MutableStateFlow(false)
    val isApplyingBatchAudio: StateFlow<Boolean> = _isApplyingBatchAudio.asStateFlow()

    private val _batchAudioProgressText = MutableStateFlow<String?>(null)
    val batchAudioProgressText: StateFlow<String?> = _batchAudioProgressText.asStateFlow()

    private val _hasAudioOriginalBackups = MutableStateFlow(false)
    val hasAudioOriginalBackups: StateFlow<Boolean> = _hasAudioOriginalBackups.asStateFlow()

    fun updateBatchAudioConfig(
        transform: (com.tajasb.audio.BatchAudioEditConfig) -> com.tajasb.audio.BatchAudioEditConfig
    ) {
        _batchAudioConfig.value = transform(_batchAudioConfig.value)
    }

    fun resetBatchAudioConfig() {
        _batchAudioConfig.value = com.tajasb.audio.BatchAudioEditConfig()
        _userMessage.value = "Pengaturan Edit Audio Massal direset ke standar"
    }

    fun refreshAudioBackupState() {
        val currentAudios = config.value.audioPlaylist
        val isExcluded: (com.tajasb.model.AudioItem) -> Boolean = { config.value.isExcludedFromType1(it) }
        _hasAudioOriginalBackups.value = com.tajasb.audio.BatchAudioProcessor.hasAnyOriginalBackups(
            getApplication(),
            currentAudios,
            isExcluded = isExcluded
        )
    }

    fun setVoiceEffect(effect: com.tajasb.model.VoiceEffect) {
        viewModelScope.launch {
            repository.setVoiceEffect(effect)
            updateBatchAudioConfig { it.copy(voiceEffect = effect) }
            _userMessage.value = "🎭 Karakter Suara: ${effect.icon} ${effect.label}"
        }
    }

    /**
     * Memproses & memutar HANYA 1 file audio sampel dengan pengaturan Edit Massal saat ini
     * agar pengguna bisa menguji hasil potongan/volume/tempo secara instan sebelum diterapkan ke semua audio.
     * PENTING: Audio Tipe 2 dan Tipe 3 otomatis tidak diikutkan.
     */
    fun testSingleAudioSample(sampleAudioId: String? = null) {
        if (_isTestingSingleAudio.value || _isApplyingBatchAudio.value) return
        val isExcluded: (com.tajasb.model.AudioItem) -> Boolean = { config.value.isExcludedFromType1(it) }
        val sourceAudios = config.value.audioPlaylist.filterNot {
            it.id.startsWith("vs_audio_rolling_") || it.path.contains("vs_audio_rolling_") || isExcluded(it)
        }
        val targetItem = if (sampleAudioId != null) {
            sourceAudios.find { it.id == sampleAudioId } ?: sourceAudios.firstOrNull()
        } else {
            sourceAudios.firstOrNull()
        }
        if (targetItem == null) {
            _userMessage.value = "Belum ada trek Audio Tipe 1 untuk dites. Upload audio/ZIP terlebih dahulu."
            return
        }

        viewModelScope.launch {
            _isTestingSingleAudio.value = true
            _batchAudioProgressText.value = "🎧 Merender tes 1 sampel (${targetItem.name})..."
            try {
                val cfg = _batchAudioConfig.value
                val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.tajasb.audio.BatchAudioProcessor.processSingleAudioForTest(
                        context = getApplication(),
                        sampleItem = targetItem,
                        config = cfg
                    ) { pct, _ ->
                        _batchAudioProgressText.value = "🎧 Menyiapkan tes 1 audio (${targetItem.name}): $pct%"
                    }
                }
                if (res.success && res.previewItem != null) {
                    repository.setVoiceEffect(cfg.voiceEffect)
                    repository.musicPlayer.playAudioItem(
                        item = res.previewItem,
                        startPlaying = true,
                        startPositionMs = 0L
                    )
                }
                _userMessage.value = res.message
            } catch (e: Exception) {
                _userMessage.value = "Gagal mengetes 1 audio: ${e.message}"
            } finally {
                _isTestingSingleAudio.value = false
                _batchAudioProgressText.value = null
            }
        }
    }

    /**
     * Menerapkan pengaturan Edit Audio Massal ke seluruh trek audio sumber (LA1..LAn) sebelum SV4 dimulai.
     */
    fun applyBatchEditToAllAudios() {
        if (_isApplyingBatchAudio.value || _isTestingSingleAudio.value) return
        val cfg = _batchAudioConfig.value
        if (cfg.isDefault) {
            _userMessage.value = "Geser minimal 1 pengaturan (Potong Awal/Akhir, Volume, Tempo, atau Fade) sebelum menerapkan Edit Massal."
            return
        }
        viewModelScope.launch {
            _isApplyingBatchAudio.value = true
            _batchAudioProgressText.value = "⚡ Memulai Edit Audio Massal..."
            try {
                repository.pauseAudio()
                val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.tajasb.audio.BatchAudioProcessor.applyBatchEditToAllAudios(
                        repo = repository,
                        config = cfg
                    ) { cur, total, name ->
                        _batchAudioProgressText.value = "⚡ Mengedit Audio Massal ($cur/$total): $name"
                    }
                }
                if (res.successCount > 0) {
                    repository.setVoiceEffect(cfg.voiceEffect)
                }
                refreshAudioBackupState()
                refreshStatus()
                _userMessage.value = res.message
            } catch (e: Exception) {
                _userMessage.value = "Gagal menerapkan Edit Audio Massal: ${e.message}"
            } finally {
                _isApplyingBatchAudio.value = false
                _batchAudioProgressText.value = null
            }
        }
    }

    fun restoreOriginalAudiosFromBackup() {
        if (_isApplyingBatchAudio.value || _isTestingSingleAudio.value) return
        viewModelScope.launch {
            _isApplyingBatchAudio.value = true
            _batchAudioProgressText.value = "↩️ Memulihkan seluruh audio asli..."
            try {
                repository.pauseAudio()
                val restoredCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.tajasb.audio.BatchAudioProcessor.restoreOriginalAudios(repository)
                }
                refreshAudioBackupState()
                refreshStatus()
                _userMessage.value = if (restoredCount > 0) {
                    "↩️ Berhasil memulihkan $restoredCount file audio ke kondisi asli!"
                } else {
                    "Tidak ada backup audio asli yang perlu dipulihkan"
                }
            } catch (e: Exception) {
                _userMessage.value = "Gagal memulihkan audio asli: ${e.message}"
            } finally {
                _isApplyingBatchAudio.value = false
                _batchAudioProgressText.value = null
            }
        }
    }

    fun onAudiosSelected(uris: List<Uri>) {
        viewModelScope.launch {
            val added = repository.addAudios(uris)
            if (added > 0) {
                _userMessage.value = "✅ Berhasil menambahkan $added trek audio (Siap diedit/optimasi sebelum SV4 dimulai)!"
                refreshAudioBackupState()
                refreshStatus()
            } else {
                _userMessage.value = "Gagal memproses file audio / ZIP tidak berisi file audio yang didukung"
            }
        }
    }

    fun onAudioSelected(uri: Uri) {
        onAudiosSelected(listOf(uri))
    }

    fun selectAudio(index: Int) {
        viewModelScope.launch {
            val selected = repository.selectAudioByIndex(index)
            if (selected != null) {
                _userMessage.value = "Audio aktif diubah ke: ${selected.name}"
                refreshStatus()
            }
        }
    }

    fun nextAudio() {
        viewModelScope.launch {
            val next = repository.nextAudio()
            if (next != null) {
                _userMessage.value = "Beralih ke: ${next.name}"
                refreshStatus()
            }
        }
    }

    fun prevAudio() {
        viewModelScope.launch {
            val prev = repository.prevAudio()
            if (prev != null) {
                _userMessage.value = "Beralih ke: ${prev.name}"
                refreshStatus()
            }
        }
    }

    val isMusicPlaying: StateFlow<Boolean> = repository.musicPlayer.isPlaying
    val currentPlayingTrack: StateFlow<com.tajasb.model.AudioItem?> = repository.musicPlayer.currentTrack
    val musicPositionMs: StateFlow<Long> = repository.musicPlayer.currentPositionMs
    val musicDurationMs: StateFlow<Long> = repository.musicPlayer.durationMs
    val isMusicLooping: StateFlow<Boolean> = repository.musicPlayer.isLooping
    val musicVolume: StateFlow<Float> = repository.musicPlayer.volume

    fun playAudio() {
        repository.playAudio()
    }

    fun pauseAudio() {
        repository.pauseAudio()
    }

    fun seekAudio(positionMs: Long) {
        repository.seekAudio(positionMs)
    }

    fun setAudioVolume(volume: Float) {
        repository.setAudioVolume(volume)
    }

    fun toggleAudioLoop() {
        val loop = repository.toggleAudioLoop()
        _userMessage.value = if (loop) "Mode Ulang Musik: Aktif (Loop Lagu)" else "Mode Ulang Musik: Nonaktif"
    }

    fun toggleDualAudioMixing() {
        val next = repository.toggleDualAudioMixing()
        _userMessage.value = if (next) {
            "🎛️ Dual Audio Mixing Aktif (Suara Video & MP3 Bunyi Bersamaan)"
        } else {
            "🔇 Dual Audio Mixing Nonaktif (Auto-Pause Aktif)"
        }
    }

    fun setDualAudioMixing(enabled: Boolean) {
        repository.setDualAudioMixing(enabled)
        _userMessage.value = if (enabled) {
            "🎛️ Dual Audio Mixing Aktif (Suara Video & MP3 Bunyi Bersamaan)"
        } else {
            "🔇 Dual Audio Mixing Nonaktif (Auto-Pause Aktif)"
        }
    }

    fun toggleIndependentAudio() {
        val next = repository.toggleIndependentAudio()
        _userMessage.value = if (next) {
            "🎧 Audio Mandiri AKTIF (Terpisah dari pergantian video & mode)"
        } else {
            "📻 Audio Terikat Mode Video (Standar)"
        }
    }

    fun setIndependentAudio(enabled: Boolean) {
        repository.setIndependentAudio(enabled)
        _userMessage.value = if (enabled) {
            "🎧 Audio Mandiri AKTIF (Terpisah dari pergantian video & mode)"
        } else {
            "📻 Audio Terikat Mode Video (Standar)"
        }
    }

    fun toggleHumanVolumeFluctuation() {
        val next = repository.toggleHumanVolumeFluctuation()
        _userMessage.value = if (next) {
            "🎙️ Dinamika Volume Manusiawi AKTIF (Anti-Deteksi Rekaman)"
        } else {
            "🔇 Dinamika Volume Manusiawi Nonaktif"
        }
    }

    fun setHumanVolumeFluctuation(enabled: Boolean) {
        repository.setHumanVolumeFluctuation(enabled)
        _userMessage.value = if (enabled) {
            "🎙️ Dinamika Volume Manusiawi AKTIF (Anti-Deteksi Rekaman)"
        } else {
            "🔇 Dinamika Volume Manusiawi Nonaktif"
        }
    }

    fun setHumanFluctuationIntensity(intensity: Float) {
        repository.setHumanFluctuationIntensity(intensity)
        val pct = (intensity * 100).toInt()
        _userMessage.value = "🎙️ Intensitas Fluktuasi Mic disetel ke ±$pct%"
    }

    // --- Audio Tipe 2: Bebas / Tidak Terikat Mode (Secondary Audio Layer) ---
    val isAudioLayer2Active = repository.musicPlayer.isLayer2Active
    val currentTrackLayer2 = repository.musicPlayer.currentTrackLayer2
    val isPlayingLayer2 = repository.musicPlayer.isPlayingLayer2
    val currentPositionMsLayer2 = repository.musicPlayer.currentPositionMsLayer2
    val durationMsLayer2 = repository.musicPlayer.durationMsLayer2
    val volumeLayer2 = repository.musicPlayer.volumeLayer2
    val isMutedLayer2 = repository.musicPlayer.isMutedLayer2
    val isLoopingLayer2 = repository.musicPlayer.isLoopingLayer2

    fun toggleAudioLayer2(enabled: Boolean? = null) {
        val next = enabled ?: !config.value.isAudioLayer2Enabled
        repository.setAudioLayer2Active(next)
        _userMessage.value = if (next) {
            "🎧 Audio Tipe 2 (Bebas Mode): AKTIF • Berjalan bersamaan dengan Tipe 1"
        } else {
            "🔇 Audio Tipe 2 (Bebas Mode): NONAKTIF"
        }
    }

    fun selectAudioForLayer2(item: com.tajasb.model.AudioItem) {
        repository.selectAudioLayer2Track(item)
        _userMessage.value = "🎧 Audio Tipe 2 (Bebas) disetel: ${item.name}"
    }

    fun toggleAudioLayer2PlayPause() {
        val playing = repository.toggleLayer2PlayPause()
        _userMessage.value = if (playing) "▶️ Audio Tipe 2: Diputar" else "⏸️ Audio Tipe 2: Dijeda"
    }

    fun setAudioLayer2Volume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        repository.setAudioLayer2Volume(clamped)
        _userMessage.value = "🔊 Volume Tipe 2: ${(clamped * 100).toInt()}%"
    }

    fun toggleAudioLayer2Mute() {
        val muted = repository.toggleAudioLayer2Mute()
        _userMessage.value = if (muted) "🔇 Audio Tipe 2: Dimute" else "🔊 Audio Tipe 2: Bersuara"
    }

    fun toggleAudioLayer2Loop() {
        val loop = repository.toggleAudioLayer2Loop()
        _userMessage.value = if (loop) "🔂 Audio Tipe 2: Loop Mandiri Aktif" else "➡️ Audio Tipe 2: 1x Putar Selesai"
    }

    fun seekAudioLayer2(positionMs: Long) {
        repository.seekAudioLayer2(positionMs)
    }

    // --- Audio Tipe 3: Ringtone Acak Dinamis ---
    val isAudioLayer3Active = repository.musicPlayer.isLayer3Active
    val isPlayingLayer3 = repository.musicPlayer.isPlayingLayer3
    val currentTrackLayer3 = repository.musicPlayer.currentTrackLayer3
    val volumeLayer3 = repository.musicPlayer.volumeLayer3
    val isMutedLayer3 = repository.musicPlayer.isMutedLayer3
    val nextRingtoneEtaSec = repository.musicPlayer.nextRingtoneEtaSec
    val layer3RingtoneIds = repository.musicPlayer.layer3RingtoneIds

    fun toggleAudioLayer3(enabled: Boolean? = null) {
        val next = enabled ?: !config.value.isAudioLayer3Enabled
        repository.setAudioLayer3Active(next)
        _userMessage.value = if (next) {
            "🔔 Audio Tipe 3 (Ringtone Acak Dinamis): AKTIF"
        } else {
            "🔇 Audio Tipe 3 (Ringtone Acak Dinamis): NONAKTIF"
        }
    }

    fun toggleAudioLayer3Ringtone(ringtoneId: String) {
        repository.toggleAudioLayer3RingtoneId(ringtoneId)
    }

    fun setAudioLayer3RingtoneIds(ids: Set<String>) {
        repository.setAudioLayer3RingtoneIds(ids)
    }

    fun autoSelectLayer3Ringtones() {
        val playlist = config.value.audioPlaylist
        val candidates = playlist.filter { it.durationMs in 500L..45000L }.takeIf { it.isNotEmpty() } ?: playlist.take(3)
        val ids = candidates.map { it.id }.toSet()
        repository.setAudioLayer3RingtoneIds(ids)
        _userMessage.value = "⚡ Otomatis memilih ${ids.size} nada dering/SFX Tipe 3"
    }

    fun selectAllLayer3Ringtones() {
        val allIds = config.value.audioPlaylist.map { it.id }.toSet()
        repository.setAudioLayer3RingtoneIds(allIds)
        _userMessage.value = "☑ Seluruh audio (${allIds.size}) dipilih untuk Tipe 3"
    }

    fun clearLayer3Ringtones() {
        repository.setAudioLayer3RingtoneIds(emptySet())
        _userMessage.value = "Daftar ringtone Tipe 3 dikosongkan"
    }

    fun setAudioLayer3Volume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        repository.setAudioLayer3Volume(clamped)
        _userMessage.value = "🔊 Volume Tipe 3: ${(clamped * 100).toInt()}%"
    }

    fun toggleAudioLayer3Mute() {
        val muted = repository.toggleAudioLayer3Mute()
        _userMessage.value = if (muted) "🔇 Audio Tipe 3: Dimute" else "🔊 Audio Tipe 3: Bersuara"
    }

    fun setAudioLayer3IntervalRange(minSec: Int, maxSec: Int) {
        repository.setAudioLayer3IntervalRange(minSec, maxSec)
        _userMessage.value = "⏱️ Interval Tipe 3: $minSec - $maxSec dtk"
    }

    fun triggerTestLayer3Ringtone(item: com.tajasb.model.AudioItem? = null) {
        val target = item ?: run {
            val poolIds = config.value.audioLayer3Ids
            val playlist = config.value.audioPlaylist
            playlist.firstOrNull { poolIds.contains(it.id) } ?: playlist.firstOrNull()
        }
        if (target != null) {
            repository.triggerTestLayer3Ringtone(target)
            _userMessage.value = "⚡ Mengetes Ringtone Tipe 3: ${target.name}"
        } else {
            _userMessage.value = "⚠️ Belum ada file audio di daftar untuk dites sebagai Tipe 3"
        }
    }

    fun uploadAndSetAudioLayer2(uris: List<Uri>) {
        viewModelScope.launch {
            val beforeIds = config.value.audioPlaylist.map { it.id }.toSet()
            val added = repository.addAudios(uris)
            if (added > 0) {
                val afterList = repository.config.value.audioPlaylist
                val newTrack = afterList.firstOrNull { it.id !in beforeIds } ?: afterList.lastOrNull()
                if (newTrack != null) {
                    repository.selectAudioLayer2Track(newTrack)
                    repository.setAudioLayer2Active(true)
                    _userMessage.value = "🎧 Audio Tipe 2 berhasil disetel ke: ${newTrack.name}"
                }
                refreshAudioBackupState()
                refreshStatus()
            } else {
                _userMessage.value = "Gagal memproses file audio untuk Tipe 2"
            }
        }
    }

    fun uploadAndSetAudioLayer3(uris: List<Uri>) {
        viewModelScope.launch {
            val beforeIds = config.value.audioPlaylist.map { it.id }.toSet()
            val added = repository.addAudios(uris)
            if (added > 0) {
                val afterList = repository.config.value.audioPlaylist
                val newTracks = afterList.filter { it.id !in beforeIds }
                if (newTracks.isNotEmpty()) {
                    val curSet = config.value.audioLayer3Ids.toMutableSet()
                    newTracks.forEach { curSet.add(it.id) }
                    repository.setAudioLayer3RingtoneIds(curSet)
                    repository.setAudioLayer3Active(true)
                    _userMessage.value = "🔔 ${newTracks.size} file SFX baru berhasil dipilih untuk Tipe 3"
                }
                refreshAudioBackupState()
                refreshStatus()
            } else {
                _userMessage.value = "Gagal memproses file SFX untuk Tipe 3"
            }
        }
    }

    fun removeAudioItem(id: String) {
        viewModelScope.launch {
            repository.removeAudioItem(id)
            _userMessage.value = "Audio dihapus dari daftar putar"
            refreshStatus()
        }
    }

    fun moveAudioUp(index: Int) = runMediaMutation(
        action = { repository.moveAudioUp(index) },
        successMessage = "Urutan audio dipindahkan naik"
    )

    fun moveAudioDown(index: Int) = runMediaMutation(
        action = { repository.moveAudioDown(index) },
        successMessage = "Urutan audio dipindahkan turun"
    )

    fun removeAudio() {
        viewModelScope.launch {
            repository.removeAudio()
            _userMessage.value = "Audio dinonaktifkan"
            refreshStatus()
        }
    }

    fun toggleAudioMute(index: Int) {
        viewModelScope.launch {
            val isMuted = repository.toggleAudioMute(index)
            _userMessage.value = if (isMuted) "Audio #${index + 1}: Dimute 🔇" else "Audio #${index + 1}: Bersuara 🔊"
        }
    }

    fun toggleAudioEnable(index: Int) {
        viewModelScope.launch {
            val isEnabled = repository.toggleAudioEnable(index)
            _userMessage.value = if (isEnabled) "Audio #${index + 1}: Diaktifkan 👁️" else "Audio #${index + 1}: Ditutup 🙈"
        }
    }

    fun toggleAllAudioMute() {
        viewModelScope.launch {
            val isMuted = repository.toggleAllAudioMute()
            _userMessage.value = if (isMuted) "Semua Audio Dimute 🔇" else "Semua Audio Bersuara 🔊"
        }
    }

    fun toggleAllAudioEnabled() {
        viewModelScope.launch {
            val isEnabled = repository.toggleAllAudioEnabled()
            _userMessage.value = if (isEnabled) "Semua Audio Diaktifkan 👁️" else "Semua Audio Ditutup 🙈"
        }
    }

    fun applyPresetTestPattern(type: String) {
        viewModelScope.launch {
            val msg = repository.generateAndApplyTestPattern(type)
            _userMessage.value = msg
            refreshStatus()
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            repository.loadInstalledApps()
        }
    }

    fun toggleTargetApp(packageName: String) {
        viewModelScope.launch {
            val app = targetApps.value.find { it.packageName == packageName }
            val name = app?.appName ?: packageName
            val isNowSelected = repository.toggleTargetApp(packageName)
            _userMessage.value = if (isNowSelected) "$name ditambahkan ke target" else "$name dihapus dari target"
        }
    }

    fun addCustomTargetPackage(pkgOrName: String) {
        viewModelScope.launch {
            repository.addCustomTargetPackage(pkgOrName)
            _userMessage.value = "$pkgOrName berhasil ditambahkan ke Target Aplikasi!"
        }
    }

    fun setPrimaryTargetApp(packageName: String) {
        viewModelScope.launch {
            repository.setPrimaryTargetPackage(packageName)
            val app = targetApps.value.find { it.packageName == packageName }
            val name = app?.appName ?: packageName
            _userMessage.value = "$name ditetapkan sebagai Target Utama 🚀"
        }
    }

    fun selectAllApps(select: Boolean) {
        viewModelScope.launch {
            val allPackages = if (select) targetApps.value.map { it.packageName }.toSet() else emptySet()
            repository.setTargetPackages(allPackages)
            _userMessage.value = if (select) "Semua aplikasi dipilih (${allPackages.size} target)" else "Semua target dibersihkan"
        }
    }

    fun isAllFilesPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun openPackageOrGlobalSettings(packageAction: String, globalFallbackAction: String) {
        val app = getApplication<Application>()
        try {
            val intent = Intent(packageAction, Uri.parse("package:${app.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(globalFallbackAction).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openPackageOrGlobalSettings(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            )
        }
    }

    fun syncFilesToDcim() {
        viewModelScope.launch {
            try {
                val currentPlaylist = config.value.playlist

                // 1. Bersihkan semua salinan duplikat di folder publik Galeri (DCIM/Movies/Download)
                val cleanReport = repository.cleanGalleryDuplicates()

                // 2. Tulis hook config files lengkap di folder internal tersembunyi
                repository.writeHookConfigFiles(config.value)

                // 3. Salin virtual.mp4 aktif ke folder internal/sandbox hook (tanpa menyentuh Galeri publik)
                val activeVideo = config.value.currentVideo
                if (activeVideo != null && File(activeVideo.path).exists()) {
                    repository.copyToAllLocations(File(activeVideo.path), "virtual.mp4")
                }

                // 4. Sinkronkan ke aplikasi target yang dipilih
                repository.syncToTargetApps(config.value.targetPackages)

                // 5. Purge cache di hook dan reload media agar video lama terhapus sepenuhnya
                repository.broadcastControlEvent(
                    action = "RELOAD_MEDIA",
                    videoPath = config.value.videoPath,
                    extraSource = "SYNC_INTERNAL_VAULT",
                    extraPurgeCache = true
                )

                _userMessage.value = "✅ Sinkronisasi Internal berhasil! ${currentPlaylist.size} video aktif siap Live (${cleanReport.deletedFilesCount} duplikat Galeri dibersihkan)."
            } catch (e: Exception) {
                _userMessage.value = "Gagal menyinkronkan file: ${e.message}"
            }
        }
    }

    fun isOverlayPermissionGranted(): Boolean {
        val app = getApplication<Application>()
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(app)
    }

    fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            openPackageOrGlobalSettings(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            )
        }
    }

    fun toggleFloatingWidget(forceState: Boolean? = null) {
        val app = getApplication<Application>()
        val current = config.value.isFloatingControlEnabled
        val newState = forceState ?: !current

        if (newState) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
                _userMessage.value = "Izinkan 'Tampilkan di atas aplikasi lain' di Pengaturan"
                openOverlaySettings()
                return
            }
            val intent = Intent(app, FloatingControlService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (_: Exception) {}
        } else {
            try {
                app.stopService(Intent(app, FloatingControlService::class.java))
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            repository.updateConfig { it.copy(isFloatingControlEnabled = newState) }
            _userMessage.value = if (newState) "Floating Window AKTIF (Otomatis Buka/Tutup)" else "Floating Window NONAKTIF"
        }
    }

    fun stopAllAudioAndHooks() {
        viewModelScope.launch {
            try {
                repository.musicPlayer.stop()
                val ctx = getApplication<android.app.Application>()
                ctx.sendBroadcast(android.content.Intent("com.tajasb.action.STOP_PLAYBACK"))
                ctx.sendBroadcast(android.content.Intent("com.tajasb.action.STOP_ALL_AUDIO"))
                _userMessage.value = "Semua audio dan media hook telah dimatikan secara paksa 🛑"
            } catch (_: Exception) {}
        }
    }
}
