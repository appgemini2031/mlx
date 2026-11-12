package com.tajasb.engine

import android.app.ActivityManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class RamHealthInfo(
    val totalRamMb: Long,
    val availRamMb: Long,
    val usedRamPercent: Int,
    val isLowMemory: Boolean,
    val jvmHeapUsedMb: Long,
    val jvmHeapMaxMb: Long,
    val statusText: String
)

data class NetworkHealthInfo(
    val isConnected: Boolean,
    val networkType: String,
    val downstreamMbps: Float,
    val upstreamMbps: Float,
    val statusText: String
)

data class VideoHealthInfo(
    val fileName: String,
    val sizeMb: Float,
    val isReadable: Boolean,
    val width: Int,
    val height: Int,
    val durationSeconds: Float,
    val statusText: String
)

data class AudioHealthInfo(
    val hasIssue: Boolean,
    val severity: String, // "NORMAL", "WARNING", "CRITICAL"
    val issueCategory: String, // "OK", "HARDWARE_MUTE", "MIC_MUTE", "AUDIO_MODE_LOCKED", "PLAYER_STALL", "ALL_SOURCES_MUTED", "SIGNAL_DROP", "LOW_VOLUME"
    val musicVolumePercent: Int,
    val isStreamMuted: Boolean,
    val isMicMuted: Boolean,
    val isAudioModeLocked: Boolean,
    val outputRouteLabel: String,
    val isExternalOutput: Boolean,
    val isVideoSoundActive: Boolean,
    val isMp3SoundActive: Boolean,
    val isSignalWeak: Boolean,
    val upstreamMbps: Float,
    val headlineText: String,
    val detailText: String,
    val shortBadgeText: String
)

data class DiagnosticSummary(
    val overallVerdict: String,
    val suspectCategory: String, // "NORMAL", "VIDEO", "INTERNET", "RAM", "APP", "AUDIO"
    val adviceText: String,
    val ramInfo: RamHealthInfo,
    val netInfo: NetworkHealthInfo,
    val videoInfo: VideoHealthInfo?,
    val audioInfo: AudioHealthInfo? = null
)

object SystemDiagnosticHelper {

    fun checkRamHealth(context: Context?): RamHealthInfo {
        return try {
            val ctx = context ?: return fallbackRam()
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)

            val totalMb = memInfo.totalMem / (1024 * 1024)
            val availMb = memInfo.availMem / (1024 * 1024)
            val usedPercent = if (totalMb > 0) (((totalMb - availMb) * 100) / totalMb).toInt() else 0

            val rt = Runtime.getRuntime()
            val heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val heapMaxMb = rt.maxMemory() / (1024 * 1024)

            val statusText = when {
                memInfo.lowMemory || availMb < 400 -> "🚨 KRITIS (${availMb}MB sisa - HP berisiko freeze!)"
                availMb < 800 -> "⚠️ MENIPIS (${availMb}MB sisa / $usedPercent% terpakai)"
                else -> "✅ AMAN (${availMb}MB bebas / total ${totalMb}MB)"
            }

            RamHealthInfo(
                totalRamMb = totalMb,
                availRamMb = availMb,
                usedRamPercent = usedPercent,
                isLowMemory = memInfo.lowMemory || availMb < 400,
                jvmHeapUsedMb = heapUsedMb,
                jvmHeapMaxMb = heapMaxMb,
                statusText = statusText
            )
        } catch (_: Throwable) {
            fallbackRam()
        }
    }

    private fun fallbackRam(): RamHealthInfo {
        val rt = Runtime.getRuntime()
        val heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val heapMaxMb = rt.maxMemory() / (1024 * 1024)
        return RamHealthInfo(
            totalRamMb = 4096,
            availRamMb = 2048,
            usedRamPercent = 50,
            isLowMemory = false,
            jvmHeapUsedMb = heapUsedMb,
            jvmHeapMaxMb = heapMaxMb,
            statusText = "✅ NORMAL (Heap: ${heapUsedMb}MB / ${heapMaxMb}MB)"
        )
    }

    @Suppress("DEPRECATION")
    fun checkNetworkHealth(context: Context?): NetworkHealthInfo {
        val ctx = context ?: return NetworkHealthInfo(
            isConnected = true,
            networkType = "Aktif",
            downstreamMbps = 30.0f,
            upstreamMbps = 15.0f,
            statusText = "✅ Tersambung"
        )

        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkHealthInfo(true, "Aktif", 20f, 10f, "✅ Aktif")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(network)

                if (caps == null) {
                    return NetworkHealthInfo(false, "OFFLINE", 0f, 0f, "🚨 Tidak ada koneksi internet!")
                }

                val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Seluler 4G/5G"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    else -> "Lainnya"
                }

                val downKbps = caps.linkDownstreamBandwidthKbps
                val upKbps = caps.linkUpstreamBandwidthKbps
                val downMbps = (downKbps / 1000f).coerceAtLeast(0f)
                val upMbps = (upKbps / 1000f).coerceAtLeast(0f)

                val statusText = when {
                    !isConnected -> "🚨 Terputus dari internet"
                    downMbps > 0f && downMbps < 3.0f -> "⚠️ Lambat (${String.format(Locale.US, "%.1f", downMbps)} Mbps)"
                    downMbps >= 3.0f -> "✅ Lancar (${String.format(Locale.US, "%.1f", downMbps)} Mbps)"
                    else -> "✅ Tersambung ($type)"
                }

                NetworkHealthInfo(
                    isConnected = isConnected,
                    networkType = type,
                    downstreamMbps = downMbps,
                    upstreamMbps = upMbps,
                    statusText = statusText
                )
            } else {
                @Suppress("DEPRECATION")
                val ni = cm.activeNetworkInfo
                val isConnected = ni?.isConnected == true
                NetworkHealthInfo(
                    isConnected = isConnected,
                    networkType = if (isConnected) (ni?.typeName ?: "Aktif") else "OFFLINE",
                    downstreamMbps = 10f,
                    upstreamMbps = 5f,
                    statusText = if (isConnected) "✅ Terhubung" else "🚨 Offline"
                )
            }
        } catch (_: Throwable) {
            NetworkHealthInfo(true, "Aktif", 20f, 10f, "✅ Aktif")
        }
    }

    fun checkVideoHealth(file: File?): VideoHealthInfo {
        if (file == null || !file.exists()) {
            return VideoHealthInfo(
                fileName = "Tidak Ada",
                sizeMb = 0f,
                isReadable = false,
                width = 0,
                height = 0,
                durationSeconds = 0f,
                statusText = "🚨 Berkas video tidak ditemukan!"
            )
        }

        val sizeMb = file.length() / (1024f * 1024f)
        val isReadable = file.canRead() && file.length() > 0

        var w = 0
        var h = 0
        var durSec = 0f

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            durSec = durMs / 1000f
            retriever.release()
        } catch (_: Throwable) {}

        val statusText = when {
            !isReadable -> "🚨 Berkas tidak terbaca / ukuran 0 byte"
            sizeMb > 300f -> "⚠️ Berkas sangat besar (${String.format(Locale.US, "%.1f", sizeMb)}MB - butuh RAM tinggi)"
            w > 0 && h > 0 -> "✅ Siap (${w}x${h}, ${String.format(Locale.US, "%.1f", durSec)}s, ${String.format(Locale.US, "%.1f", sizeMb)}MB)"
            else -> "✅ Berkas valid (${String.format(Locale.US, "%.1f", sizeMb)}MB)"
        }

        return VideoHealthInfo(
            fileName = file.name,
            sizeMb = sizeMb,
            isReadable = isReadable,
            width = w,
            height = h,
            durationSeconds = durSec,
            statusText = statusText
        )
    }

    fun analyzeRootCause(
        context: Context?,
        activeVideoFile: File?,
        glFps: Float,
        isDecoderStalled: Boolean
    ): DiagnosticSummary {
        val ram = checkRamHealth(context)
        val net = checkNetworkHealth(context)
        val vid = activeVideoFile?.let { checkVideoHealth(it) }

        val suspectCategory: String
        val verdict: String
        val advice: String

        val audio = checkAudioHealth(context = context)

        when {
            // 1. Check Video Issue
            vid != null && (!vid.isReadable || vid.sizeMb == 0f) -> {
                suspectCategory = "VIDEO"
                verdict = "MASALAH BERKAS VIDEO (Video Kosong / Rusak)"
                advice = "Berkas video ${vid.fileName} tidak dapat dibaca atau rusak. Hapus dan pilih video baru."
            }
            // 2. Check Audio Hardware / Mute Issue
            audio.hasIssue && audio.severity == "CRITICAL" -> {
                suspectCategory = "AUDIO"
                verdict = audio.headlineText
                advice = audio.detailText
            }
            // 3. Check RAM Issue
            ram.isLowMemory || ram.availRamMb < 400 -> {
                suspectCategory = "RAM"
                verdict = "MASALAH RAM HP PENUH (Tersisa < 400MB)"
                advice = "RAM perangkat tersisa ${ram.availRamMb}MB. Tutup aplikasi latar belakang lain atau restart ponsel."
            }
            // 4. Check Network Issue
            !net.isConnected || (net.downstreamMbps > 0 && net.downstreamMbps < 2.0f) || (net.upstreamMbps > 0 && net.upstreamMbps < 1.5f) -> {
                suspectCategory = "INTERNET"
                verdict = "MASALAH INTERNET / UPLOAD LAMBAT (${String.format(Locale.US, "%.1f", net.upstreamMbps)} Mbps Up)"
                advice = "Koneksi internet / upload drop sehingga suara dan gambar Live rawan terhambat atau terkena PL."
            }
            // 5. Check Decoder / App Issue
            isDecoderStalled || glFps < 10f -> {
                suspectCategory = "APP"
                verdict = "MASALAH DECODER / CODEC LIMIT"
                advice = "Hardware decoder ponsel sempat antre. Sistem auto-healing Tajasb sedang mereset slot."
            }
            // 6. All Normal
            else -> {
                suspectCategory = "NORMAL"
                verdict = "SEMUA SISTEM STABIL & OPTIMAL"
                advice = "Video OK, Audio ${audio.outputRouteLabel} (${audio.musicVolumePercent}%), Internet ${net.networkType} (${net.downstreamMbps} Mbps), RAM Bebas ${ram.availRamMb}MB."
            }
        }

        return DiagnosticSummary(
            overallVerdict = verdict,
            suspectCategory = suspectCategory,
            adviceText = advice,
            ramInfo = ram,
            netInfo = net,
            videoInfo = vid,
            audioInfo = audio
        )
    }

    /**
     * Memeriksa kondisi hardware audio HP, volume media, mikrofon, sumber suara aplikasi (Video & MP3),
     * perangkat output (Speaker/Headset/Bluetooth), serta kualitas sinyal upload secara real-time
     * agar pengguna langsung mendapat peringatan sebelum terkena PL akibat suara hilang/terhambat.
     */
    fun checkAudioHealth(
        context: Context?,
        hasActiveVideo: Boolean = false,
        isVideoPaused: Boolean = false,
        isVideoAudioMuted: Boolean = false,
        videoVolume: Float = 1.0f,
        hasMp3Playlist: Boolean = false,
        isMp3Playing: Boolean = false,
        isMp3Muted: Boolean = false,
        mp3Volume: Float = 1.0f,
        isAudioPlayerStalled: Boolean = false,
        audioPlayerErrorReason: String? = null
    ): AudioHealthInfo {
        val ctx = context ?: return AudioHealthInfo(
            hasIssue = false,
            severity = "NORMAL",
            issueCategory = "OK",
            musicVolumePercent = 80,
            isStreamMuted = false,
            isMicMuted = false,
            isAudioModeLocked = false,
            outputRouteLabel = "Speaker HP",
            isExternalOutput = false,
            isVideoSoundActive = true,
            isMp3SoundActive = false,
            isSignalWeak = false,
            upstreamMbps = 15f,
            headlineText = "✅ Output Suara Normal",
            detailText = "Speaker aktif dan siap",
            shortBadgeText = "🔊 Suara OK"
        )

        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val curVol = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 10
            val maxVol = (am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
            val volPct = ((curVol * 100f) / maxVol).toInt().coerceIn(0, 100)
            val isStreamMute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am?.isStreamMute(AudioManager.STREAM_MUSIC) == true || curVol <= 0
            } else {
                curVol <= 0
            }
            val isMicMuted = try { am?.isMicrophoneMute == true } catch (_: Throwable) { false }
            val audioMode = try { am?.mode ?: AudioManager.MODE_NORMAL } catch (_: Throwable) { AudioManager.MODE_NORMAL }
            val isModeLocked = audioMode == AudioManager.MODE_IN_CALL ||
                audioMode == AudioManager.MODE_IN_COMMUNICATION ||
                audioMode == AudioManager.MODE_RINGTONE

            var routeLabel = "Speaker HP"
            var isExternal = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && am != null) {
                try {
                    val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    for (dev in outputs) {
                        when (dev.type) {
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                                routeLabel = "Bluetooth (${dev.productName?.takeIf { it.isNotBlank() } ?: "TWS/Speaker"})"
                                isExternal = true
                                break
                            }
                            AudioDeviceInfo.TYPE_WIRED_HEADSET,
                            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                                routeLabel = "Headset Kabel"
                                isExternal = true
                                break
                            }
                            AudioDeviceInfo.TYPE_USB_HEADSET,
                            AudioDeviceInfo.TYPE_USB_DEVICE,
                            AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
                                routeLabel = "USB Audio / Soundcard"
                                isExternal = true
                                break
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            val net = checkNetworkHealth(ctx)
            val isSignalWeak = !net.isConnected ||
                (net.upstreamMbps > 0f && net.upstreamMbps < 1.8f) ||
                (net.downstreamMbps > 0f && net.downstreamMbps < 2.5f)

            val isVideoSoundActive = hasActiveVideo && !isVideoPaused && !isVideoAudioMuted && videoVolume > 0.03f
            val isMp3SoundActive = hasMp3Playlist && isMp3Playing && !isMp3Muted && mp3Volume > 0.03f
            val areAllAppSourcesSilent = hasActiveVideo && !isVideoPaused && !isVideoSoundActive && !isMp3SoundActive

            val severity: String
            val category: String
            val headline: String
            val detail: String
            val badge: String

            when {
                isStreamMute || volPct == 0 -> {
                    severity = "CRITICAL"
                    category = "HARDWARE_MUTE"
                    headline = "🚨 VOLUME HP MATI (0%) — RAWAN PL SUARA!"
                    detail = "Volume media hardware HP Anda sedang 0% atau ter-mute sistem ($routeLabel). HP tidak mengeluarkan suara sama sekali! Klik 'Pulihkan Suara' atau naikkan tombol volume HP."
                    badge = "🔇 VOL HP 0% (RAWAN PL)"
                }
                isMicMuted -> {
                    severity = "CRITICAL"
                    category = "MIC_MUTE"
                    headline = "🚨 MIKROFON HARDWARE HP TER-MUTE!"
                    detail = "Mikrofon sistem HP terdeteksi dalam kondisi Mute sehingga aplikasi Live tidak bisa menangkap suara. Klik 'Pulihkan Suara' untuk membuka kunci mikrofon."
                    badge = "🎙️ MIC MUTE (RAWAN PL)"
                }
                isModeLocked -> {
                    severity = "CRITICAL"
                    category = "AUDIO_MODE_LOCKED"
                    headline = "🚨 HARDWARE AUDIO TERTAHAN PANGGILAN / KOMUNIKASI!"
                    detail = "Jalur suara HP sedang dikunci mode telepon/komunikasi aplikasi lain sehingga output suara terhambat. Klik 'Pulihkan Suara' untuk mereset jalur audio."
                    badge = "⚠️ AUDIO TERKUNCI"
                }
                isAudioPlayerStalled -> {
                    severity = "CRITICAL"
                    category = "PLAYER_STALL"
                    headline = "🚨 OUTPUT SUARA TERHAMBAT DI HARDWARE!"
                    detail = audioPlayerErrorReason ?: "Decoder audio sempat tertahan oleh sistem HP. Klik 'Pulihkan Suara' untuk menyegarkan output suara sekarang."
                    badge = "🚨 SUARA MACET"
                }
                areAllAppSourcesSilent -> {
                    severity = "CRITICAL"
                    category = "ALL_SOURCES_MUTED"
                    headline = "🚨 RAWAN PL: SUARA VIDEO & MUSIK MP3 SAMA-SAMA MATI!"
                    detail = "Suara asli video sedang Mute DAN musik MP3 tidak berputar. Streaming tanpa suara berisiko tinggi terkena PL! Aktifkan MP3 atau nyalakan suara video."
                    badge = "🔇 SEMUA AUDIO MUTE"
                }
                !net.isConnected -> {
                    severity = "CRITICAL"
                    category = "SIGNAL_DROP"
                    headline = "🚨 KONEKSI INTERNET PUTUS — SUARA LIVE TERHENTI!"
                    detail = "HP kehilangan koneksi internet (${net.networkType}). Suara & gambar tidak terkirim ke server Live."
                    badge = "📡 OFFLINE (RAWAN PL)"
                }
                isSignalWeak -> {
                    severity = "WARNING"
                    category = "SIGNAL_DROP"
                    val upStr = String.format(Locale.US, "%.1f", net.upstreamMbps)
                    val downStr = String.format(Locale.US, "%.1f", net.downstreamMbps)
                    headline = "📡 SINYAL LEMAH (Upload $upStr Mbps) — SUARA RAWAN PUTUS / PL!"
                    detail = "Kecepatan sinyal (${net.networkType}: Down $downStr Mbps / Up $upStr Mbps) sedang turun. Suara ke server Live bisa patah-patah atau terlambat."
                    badge = "📡 SINYAL LEMAH ($upStr Mbps)"
                }
                volPct in 1..22 -> {
                    severity = "WARNING"
                    category = "LOW_VOLUME"
                    headline = "⚠️ VOLUME HP SANGAT KECIL ($volPct% di $routeLabel)"
                    detail = "Volume media HP hanya $volPct%. Jika mengandalkan tangkapan mikrofon/speaker, suara bisa terlalu pelan dan rawan terdeteksi hening (PL)."
                    badge = "🔉 VOL KECIL ($volPct%)"
                }
                isExternal && !routeLabel.contains("Soundcard", ignoreCase = true) -> {
                    severity = "WARNING"
                    category = "LOW_VOLUME"
                    headline = "🎧 SUARA DIALIHKAN KE ${routeLabel.uppercase()}"
                    detail = "Suara keluar melalui $routeLabel (Volume $volPct%), bukan Speaker HP. Pastikan perangkat tersebut dekat dengan mic atau memang digunakan untuk Live."
                    badge = "🎧 $routeLabel ($volPct%)"
                }
                else -> {
                    severity = "NORMAL"
                    category = "OK"
                    val activeSrc = when {
                        isVideoSoundActive && isMp3SoundActive -> "Video + MP3 Aktif"
                        isMp3SoundActive -> "Musik MP3 Aktif"
                        isVideoSoundActive -> "Suara Video Aktif"
                        else -> "Siaga"
                    }
                    headline = "✅ Output Suara & Sinyal Aman ($activeSrc)"
                    detail = "Jalur $routeLabel ($volPct%) | Sinyal ${net.networkType} (${String.format(Locale.US, "%.1f", net.downstreamMbps)} Mbps)"
                    badge = "🔊 $volPct% • ${net.networkType}"
                }
            }

            AudioHealthInfo(
                hasIssue = severity != "NORMAL",
                severity = severity,
                issueCategory = category,
                musicVolumePercent = volPct,
                isStreamMuted = isStreamMute,
                isMicMuted = isMicMuted,
                isAudioModeLocked = isModeLocked,
                outputRouteLabel = routeLabel,
                isExternalOutput = isExternal,
                isVideoSoundActive = isVideoSoundActive,
                isMp3SoundActive = isMp3SoundActive,
                isSignalWeak = isSignalWeak,
                upstreamMbps = net.upstreamMbps,
                headlineText = headline,
                detailText = detail,
                shortBadgeText = badge
            )
        } catch (_: Throwable) {
            AudioHealthInfo(
                hasIssue = false,
                severity = "NORMAL",
                issueCategory = "OK",
                musicVolumePercent = 80,
                isStreamMuted = false,
                isMicMuted = false,
                isAudioModeLocked = false,
                outputRouteLabel = "Speaker HP",
                isExternalOutput = false,
                isVideoSoundActive = true,
                isMp3SoundActive = false,
                isSignalWeak = false,
                upstreamMbps = 15f,
                headlineText = "✅ Output Suara Normal",
                detailText = "Speaker aktif",
                shortBadgeText = "🔊 Suara OK"
            )
        }
    }

    /**
     * Memulihkan hardware audio HP (membuka mute sistem, menaikkan volume media ke level aman,
     * membuka mute mikrofon, mereset mode panggilan, mengambil AudioFocus) serta menyalakan kembali
     * pemutar MP3 atau suara video secara instan dengan 1 klik.
     */
    fun recoverAudioHardwareAndStream(
        context: Context,
        repository: com.tajasb.data.VcamRepository
    ): String {
        val actions = mutableListOf<String>()
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                // 1. Unmute STREAM_MUSIC
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        if (am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                            actions.add("Unmute Sistem")
                        }
                    } catch (_: Throwable) {}
                }
                // 2. Naikkan volume hardware jika terlalu kecil (< 40%) ke 70%
                try {
                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val targetVol = (maxVol * 0.70f).toInt().coerceAtLeast(1)
                    if (curVol < (maxVol * 0.40f).toInt()) {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                        actions.add("Volume HP -> 70%")
                    }
                } catch (_: Throwable) {}

                // 3. Buka kunci mikrofon jika ter-mute
                try {
                    if (am.isMicrophoneMute) {
                        am.isMicrophoneMute = false
                        actions.add("Unmute Mic")
                    }
                } catch (_: Throwable) {}

                // 4. Kembalikan mode audio ke MODE_NORMAL jika terkunci mode komunikasi
                try {
                    if (am.mode != AudioManager.MODE_NORMAL) {
                        am.mode = AudioManager.MODE_NORMAL
                        actions.add("Reset Mode Audio")
                    }
                } catch (_: Throwable) {}

                // 5. Minta AudioFocus agar suara tidak ditahan aplikasi lain
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener { }
                            .build()
                        am.requestAudioFocus(req)
                    } else {
                        @Suppress("DEPRECATION")
                        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                    }
                } catch (_: Throwable) {}
            }

            // 6. Pulihkan sumber suara di dalam aplikasi (MP3 atau Video)
            val cfg = repository.config.value
            if (cfg.audioPlaylist.isNotEmpty()) {
                repository.musicPlayer.forceRecoverAudioOutput()
                repository.playAudio()
                actions.add("Restart Musik MP3")
            } else if (cfg.playlist.isNotEmpty()) {
                if (repository.videoVolume.value < 0.3f) {
                    repository.setVideoVolume(0.85f)
                }
                if (cfg.isVideoAudioMuted) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        repository.setAllVideosMuted(false)
                    }
                    actions.add("Unmute Suara Video")
                }
            }
        } catch (_: Throwable) {}

        return if (actions.isEmpty()) {
            "🔊 Jalur hardware audio & volume HP telah disegarkan!"
        } else {
            "🔊 Dipulihkan: ${actions.joinToString(", ")}"
        }
    }

    fun formatDiagnosticLog(
        context: Context?,
        activeVideoFile: File?,
        glFps: Float,
        isDecoderStalled: Boolean
    ): String {
        val diag = analyzeRootCause(context, activeVideoFile, glFps, isDecoderStalled)
        val ram = diag.ramInfo
        val net = diag.netInfo
        val vid = diag.videoInfo
        val aud = diag.audioInfo

        val vidSummary = if (vid != null) {
            "${vid.fileName} (${vid.width}x${vid.height}, ${String.format(Locale.US, "%.1f", vid.durationSeconds)}s, ${String.format(Locale.US, "%.1f", vid.sizeMb)}MB - ${vid.statusText})"
        } else "Tidak Ada Berkas"

        val netSummary = "${net.networkType} (Down ${String.format(Locale.US, "%.1f", net.downstreamMbps)} / Up ${String.format(Locale.US, "%.1f", net.upstreamMbps)} Mbps - ${net.statusText})"
        val ramSummary = "Sisa ${ram.availRamMb}MB / Total ${ram.totalRamMb}MB (${ram.usedRamPercent}% dipakai - ${ram.statusText})"
        val audSummary = if (aud != null) "${aud.outputRouteLabel} Vol ${aud.musicVolumePercent}% (${aud.shortBadgeText})" else "Normal"
        val apkSummary = "GL ${String.format(Locale.US, "%.1f", glFps)} FPS | Decoder: ${if (isDecoderStalled) "⚠️ STALL" else "✅ NORMAL"}"

        return "🩺 [DIAGNOSTIK-SISTEM] Status: ${diag.overallVerdict} | 1. VIDEO: $vidSummary | 2. AUDIO: $audSummary | 3. INTERNET: $netSummary | 4. RAM HP: $ramSummary | 5. APK: $apkSummary | REKOMENDASI: ${diag.adviceText}"
    }
}

