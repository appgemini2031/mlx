package com.tajasb.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import com.tajasb.model.MediaMetadata
import com.tajasb.model.VcamConfig
import java.io.File

import android.content.ContentResolver
import android.media.MediaScannerConnection
import android.provider.MediaStore

data class GalleryDuplicateCleanupReport(
    val deletedFilesCount: Int,
    val freedBytes: Long,
    val migratedVaultFilesCount: Int,
    val cleanedFoldersCount: Int
) {
    val freedMbFormatted: String
        get() = String.format(java.util.Locale.US, "%.1f MB", freedBytes.toDouble() / (1024.0 * 1024.0))
}

/**
 * MediaFileStorageHelper:
 * Modular helper for Camera1 directory management, physical file replication,
 * hook marker/config file writing, synthetic MP4 video generation, and MediaMetadataRetriever extraction.
 */
object MediaFileStorageHelper {

    private fun writeTextAtomically(target: File, content: String) {
        try {
            val parent = target.parentFile ?: return
            if (!parent.exists()) parent.mkdirs()
            if (target.exists() && target.length() > 0L) {
                try {
                    if (target.readText() == content) {
                        target.setReadable(true, false)
                        return
                    }
                } catch (_: Exception) {}
            }
            val tmpFile = File(parent, ".tmp_${target.name}_${Thread.currentThread().id}")
            try {
                tmpFile.writeText(content)
                tmpFile.setReadable(true, false)
                tmpFile.setWritable(true, false)
                if (!tmpFile.renameTo(target)) {
                    target.writeText(content)
                }
                target.setReadable(true, false)
                target.setWritable(true, false)
            } finally {
                if (tmpFile.exists()) {
                    try { tmpFile.delete() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Mengecek apakah sebuah direktori berada di dalam ruang publik yang dipindai oleh aplikasi Galeri HP
     * (seperti DCIM, Movies, Pictures, Download di luar Android/data).
     */
    fun isPublicGalleryScannedDirectory(dir: File): Boolean {
        val path = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
        val normalized = path.replace('\\', '/')
        if (normalized.contains("/Android/data/") ||
            normalized.startsWith("/data/data/") ||
            normalized.startsWith("/data/user/") ||
            normalized.startsWith("/data/local/tmp")
        ) {
            return false
        }
        return normalized.contains("/DCIM", ignoreCase = true) ||
            normalized.contains("/Movies", ignoreCase = true) ||
            normalized.contains("/Pictures", ignoreCase = true) ||
            normalized.contains("/Download", ignoreCase = true) ||
            normalized.endsWith("/Camera1", ignoreCase = true) ||
            normalized.contains("/LiveHost_Backup_Vault", ignoreCase = true)
    }

    /**
     * Memeriksa keutuhan file konfigurasi & video aktif di direktori hook internal tersembunyi.
     * TIDAK PERNAH menyalin file video kembali ke folder publik Galeri (DCIM/Download/Movies).
     */
    fun verifyAndHealHookFiles(
        config: VcamConfig,
        camera1Dir: File,
        internalCamera1Dir: File
    ): Int {
        var healedCount = 0
        val dirs = listOfNotNull(
            internalCamera1Dir,
            camera1Dir.takeIf {
                !isPublicGalleryScannedDirectory(it) && (it.exists() || it.parentFile?.canWrite() == true)
            }
        ).distinctBy { try { it.canonicalPath } catch (_: Exception) { it.absolutePath } }

        var needsConfigRewrite = false
        val activeVideo = config.currentVideo
        val activeSrc = if (activeVideo != null) File(activeVideo.path) else null

        for (dir in dirs) {
            try {
                if (!dir.exists()) {
                    dir.mkdirs()
                    needsConfigRewrite = true
                    healedCount++
                }
                try {
                    val noMedia = File(dir, ".nomedia")
                    if (!noMedia.exists()) noMedia.createNewFile()
                } catch (_: Exception) {}

                val jsonFile = File(dir, "vcam_config.json")
                if (!jsonFile.exists() || jsonFile.length() == 0L) {
                    needsConfigRewrite = true
                    healedCount++
                }
                if (config.playlist.isNotEmpty()) {
                    val playlistFile = File(dir, "playlist.txt")
                    if (!playlistFile.exists() || playlistFile.length() == 0L) {
                        needsConfigRewrite = true
                        healedCount++
                    }
                    for (i in config.playlist.indices) {
                        val src = File(config.playlist[i].path)
                        val indexedTarget = File(dir, "video_$i.mp4")
                        if (src.exists() && src.length() > 0L && (!indexedTarget.exists() || indexedTarget.length() != src.length())) {
                            if (indexedTarget.canonicalPath != src.canonicalPath) {
                                atomicCopyIfChanged(src, indexedTarget, skipIfSameLength = true)
                                healedCount++
                            }
                        }
                    }
                }
                if (activeSrc != null && activeSrc.exists() && activeSrc.length() > 0L) {
                    val virtualTarget = File(dir, "virtual.mp4")
                    if (!virtualTarget.exists() || virtualTarget.length() == 0L) {
                        if (virtualTarget.canonicalPath != activeSrc.canonicalPath) {
                            atomicCopyIfChanged(activeSrc, virtualTarget, skipIfSameLength = true)
                            healedCount++
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (needsConfigRewrite) {
            writeHookConfigFiles(config, camera1Dir, internalCamera1Dir)
        }
        return healedCount
    }

    fun writeHookConfigFiles(
        config: VcamConfig,
        camera1Dir: File,
        internalCamera1Dir: File
    ) {
        val targetDirs = mutableListOf<File>()
        try { targetDirs.add(internalCamera1Dir) } catch (_: Exception) {}
        try {
            if (!isPublicGalleryScannedDirectory(camera1Dir)) {
                targetDirs.add(camera1Dir)
            }
        } catch (_: Exception) {}

        val uniqueDirs = targetDirs.distinctBy {
            try { it.canonicalPath } catch (_: Exception) { it.absolutePath }
        }

        for (dir in uniqueDirs) {
            try {
                if (!dir.exists()) dir.mkdirs()
                try {
                    val noMedia = File(dir, ".nomedia")
                    if (!noMedia.exists()) noMedia.createNewFile()
                } catch (_: Exception) {}

                writeTextAtomically(File(dir, "rotation.txt"), config.rotation.toString())
                writeTextAtomically(File(dir, "mirror.txt"), if (config.isMirrored) "1" else "0")
                writeTextAtomically(File(dir, "flip_v.txt"), if (config.isVerticalFlipped) "1" else "0")
                writeTextAtomically(File(dir, "zoom.txt"), String.format(java.util.Locale.US, "%.2f", config.zoomFactor))
                writeTextAtomically(File(dir, "scale.txt"), config.scaleMode.name)

                val disableFile = File(dir, "disable.jpg")
                val isVideoActive = config.isMasterEnabled && (
                    (config.playlist.isNotEmpty() && config.currentVideo != null && File(config.currentVideo!!.path).exists()) ||
                    (config.isLiveStreamSourceActive && config.liveStreamUrl.isNotBlank())
                )
                if (!isVideoActive) {
                    if (!disableFile.exists()) {
                        disableFile.createNewFile()
                        disableFile.setReadable(true, false)
                    }
                } else {
                    if (disableFile.exists()) disableFile.delete()
                }

                if (config.isLiveStreamSourceActive && config.liveStreamUrl.isNotBlank()) {
                    writeTextAtomically(File(dir, "stream_url.txt"), config.liveStreamUrl)
                    writeTextAtomically(File(dir, "stream_mode.txt"), "LIVE_WEB")
                } else {
                    try { File(dir, "stream_url.txt").delete() } catch (_: Exception) {}
                    try { File(dir, "stream_mode.txt").delete() } catch (_: Exception) {}
                }

                val silentFile = File(dir, "no-silent.jpg")
                if (config.audioEnabled) {
                    if (!silentFile.exists()) silentFile.createNewFile()
                } else {
                    if (silentFile.exists()) silentFile.delete()
                }

                val pauseFile = File(dir, "pause.jpg")
                if (config.isVideoPaused) {
                    if (!pauseFile.exists()) pauseFile.createNewFile()
                } else {
                    if (pauseFile.exists()) pauseFile.delete()
                }

                writeTextAtomically(File(dir, "video_paused.txt"), if (config.isVideoPaused) "1" else "0")
                writeTextAtomically(File(dir, "video_audio_muted.txt"), if (config.isVideoAudioMuted) "1" else "0")
                writeTextAtomically(File(dir, "audio_paused.txt"), if (config.isMp3AudioPaused) "1" else "0")
                writeTextAtomically(File(dir, "mp3_audio_paused.txt"), if (config.isMp3AudioPaused) "1" else "0")

                val audioDisabledFile = File(dir, "audio_disabled.txt")
                val isAudioActive = config.audioPlaylist.isNotEmpty() && config.audioPath.isNotEmpty() && config.currentAudio != null && File(config.currentAudio!!.path).exists()
                if (!isAudioActive) {
                    if (!audioDisabledFile.exists()) {
                        audioDisabledFile.createNewFile()
                        audioDisabledFile.setReadable(true, false)
                    }
                    try { File(dir, "audio_path.txt").delete() } catch (_: Exception) {}
                    try { File(dir, "audio.mp3").delete() } catch (_: Exception) {}
                    try { File(dir, "audio.wav").delete() } catch (_: Exception) {}
                } else {
                    if (audioDisabledFile.exists()) audioDisabledFile.delete()
                    writeTextAtomically(File(dir, "audio_path.txt"), config.audioPath)
                }

                val autoNextValue = if (config.autoNextEnabled && !config.isIfsMode) {
                    config.autoNextMode.name
                } else {
                    "OFF"
                }
                writeTextAtomically(File(dir, "auto_next.txt"), autoNextValue)
                writeTextAtomically(File(dir, "current_index.txt"), config.currentVideoIndex.toString())

                // 1. Exclude temporary rolling VS files (vs_rolling_*) from indexed video_0.mp4..video_n.mp4
                // so that source videos L1, L2, L3, L4 are never overwritten on disk.
                val sourcePlaylistFiles = config.playlist.filterNot { item ->
                    item.id.startsWith("vs_rolling_") || item.path.contains("vs_rolling_")
                }
                cleanupStaleIndexedVideos(listOf(dir), sourcePlaylistFiles.size)

                val playlistLines = mutableListOf<String>()
                if (sourcePlaylistFiles.isEmpty() && config.currentVideo == null) {
                    try { File(dir, "playlist.txt").delete() } catch (_: Exception) {}
                    try { File(dir, "virtual.mp4").delete() } catch (_: Exception) {}
                } else {
                    for (i in sourcePlaylistFiles.indices) {
                        val src = File(sourcePlaylistFiles[i].path)
                        if (src.exists() && src.length() > 0L) {
                            val targetVideo = File(dir, "video_$i.mp4")
                            if (targetVideo.canonicalPath != src.canonicalPath) {
                                atomicCopyIfChanged(src, targetVideo, skipIfSameLength = true)
                            }
                            playlistLines.add("video_$i.mp4")
                        }
                    }
                    writeTextAtomically(File(dir, "playlist.txt"), playlistLines.joinToString("\n"))
                    val activeVideo = config.currentVideo
                    if (activeVideo != null) {
                        val activeSrc = File(activeVideo.path)
                        val virtualTarget = File(dir, "virtual.mp4")
                        if (activeSrc.exists() && activeSrc.length() > 0L && virtualTarget.canonicalPath != activeSrc.canonicalPath) {
                            atomicCopyIfChanged(activeSrc, virtualTarget, skipIfSameLength = false)
                        }
                    }
                }

                val jsonContent = """
                    {
                        "masterEnabled": ${config.isMasterEnabled},
                        "rotation": ${config.rotation},
                        "isMirrored": ${config.isMirrored},
                        "zoomFactor": ${config.zoomFactor},
                        "scaleMode": "${config.scaleMode.name}",
                        "audioEnabled": ${config.audioEnabled},
                        "isVideoPaused": ${config.isVideoPaused},
                        "isVideoAudioMuted": ${config.isVideoAudioMuted},
                        "isMp3AudioPaused": ${config.isMp3AudioPaused},
                        "floatingAlpha": ${config.floatingAlpha},
                        "videoPath": "${config.videoPath}",
                        "audioPath": "${config.audioPath}",
                        "currentVideoIndex": ${config.currentVideoIndex},
                        "playlistCount": ${config.playlist.size},
                        "isLiveStreamSourceActive": ${config.isLiveStreamSourceActive},
                        "liveStreamUrl": "${config.liveStreamUrl}",
                        "liveStreamTitle": "${config.liveStreamTitle}"
                    }
                """.trimIndent()
                writeTextAtomically(File(dir, "vcam_config.json"), jsonContent)
            } catch (_: Exception) {}
        }
    }

    private fun haveSameFastContentSignature(a: File, b: File): Boolean {
        return try {
            val len = a.length()
            if (len != b.length() || len <= 0L) return false
            val sampleSize = 4096
            val bufA = ByteArray(sampleSize)
            val bufB = ByteArray(sampleSize)
            java.io.RandomAccessFile(a, "r").use { rafA ->
                java.io.RandomAccessFile(b, "r").use { rafB ->
                    val readHeadA = rafA.read(bufA)
                    val readHeadB = rafB.read(bufB)
                    if (readHeadA != readHeadB) return false
                    for (i in 0 until readHeadA) {
                        if (bufA[i] != bufB[i]) return false
                    }
                    if (len > sampleSize * 2) {
                        val tailPos = len - sampleSize
                        rafA.seek(tailPos)
                        rafB.seek(tailPos)
                        val readTailA = rafA.read(bufA)
                        val readTailB = rafB.read(bufB)
                        if (readTailA != readTailB) return false
                        for (i in 0 until readTailA) {
                            if (bufA[i] != bufB[i]) return false
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Copies [source] to [target] atomically via a temporary sibling file + renameTo so that
     * any active MediaPlayer reading [target] never encounters a truncated 0-byte file (MEDIA_ERROR_IO -1004)
     * during large MB video transitions.
     */
    private fun atomicCopyIfChanged(source: File, target: File, skipIfSameLength: Boolean) {
        try {
            val srcLen = source.length()
            if (srcLen <= 0L) return
            if (skipIfSameLength && target.exists() && haveSameFastContentSignature(source, target)) {
                target.setReadable(true, false)
                return
            }
            val parent = target.parentFile ?: return
            parent.mkdirs()
            val tmpFile = File(parent, ".tmp_${target.name}_${Thread.currentThread().id}")
            try {
                source.copyTo(tmpFile, overwrite = true)
                tmpFile.setReadable(true, false)
                tmpFile.setWritable(true, false)
                tmpFile.setExecutable(true, false)
                if (!tmpFile.renameTo(target)) {
                    tmpFile.copyTo(target, overwrite = true)
                    tmpFile.delete()
                }
                target.setReadable(true, false)
                target.setWritable(true, false)
                target.setExecutable(true, false)
            } finally {
                if (tmpFile.exists()) {
                    try { tmpFile.delete() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Mengembalikan daftar direktori kerja kamera virtual yang 100% tersembunyi dari pemindaian Galeri HP
     * (hanya ruang internal aplikasi & sandbox Android/data aplikasi target).
     */
    fun getAllHookDirectories(
        context: Context,
        internalCamera1Dir: File,
        targetPackages: Set<String>
    ): List<File> {
        val targetDirs = mutableSetOf<String>()
        try {
            targetDirs.add(internalCamera1Dir.canonicalPath)
        } catch (_: Exception) {}
        try {
            val extFiles = context.getExternalFilesDir("Camera1")
            if (extFiles != null) {
                targetDirs.add(extFiles.canonicalPath)
            }
        } catch (_: Exception) {}
        try {
            targetDirs.add(File("/data/local/tmp/Camera1").canonicalPath)
        } catch (_: Exception) {}

        val allPkgs = targetPackages + listOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.ss.android.ugc.live",
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite",
            "com.instagram.android",
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
        for (pkg in allPkgs) {
            try {
                targetDirs.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1").canonicalPath)
                targetDirs.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1").canonicalPath)
                targetDirs.add(File("/sdcard/Android/data/$pkg/files/Camera1").canonicalPath)
                targetDirs.add(File("/data/data/$pkg/files/Camera1").canonicalPath)
                targetDirs.add(File("/data/user/0/$pkg/files/Camera1").canonicalPath)
            } catch (_: Exception) {}
        }
        return targetDirs.map { File(it) }
    }

    /**
     * Menghapus seluruh salinan video/media duplikat yang sebelumnya sempat tertulis ke folder publik Galeri
     * (DCIM/Camera1, Movies/Camera1, Download/Camera1, sdcard/Camera1, dan Download/LiveHost_Backup_Vault/media_vault),
     * memindahkan file konsep penting ke penyimpanan internal tersembunyi jika diperlukan,
     * serta memperbarui database MediaStore Android agar item duplikat langsung lenyap dari aplikasi Galeri HP.
     */
    fun purgePublicGalleryDuplicates(
        context: Context,
        internalCamera1Dir: File,
        internalVaultDir: File
    ): GalleryDuplicateCleanupReport {
        var deletedCount = 0
        var freedBytes = 0L
        var migratedCount = 0
        var cleanedFoldersCount = 0
        val deletedPathsForMediaScanner = mutableListOf<String>()

        if (!internalCamera1Dir.exists()) internalCamera1Dir.mkdirs()
        if (!internalVaultDir.exists()) internalVaultDir.mkdirs()
        val internalMediaVaultDir = File(internalVaultDir, "media_vault").apply {
            if (!exists()) mkdirs()
        }

        // 1. Bersihkan folder publik Camera1 (DCIM/Camera1, Movies/Camera1, Download/Camera1, sdcard/Camera1)
        val publicCamera1Dirs = mutableSetOf<File>()
        try {
            publicCamera1Dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1"))
            publicCamera1Dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Camera1"))
            publicCamera1Dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Camera1"))
            publicCamera1Dirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Camera1"))
        } catch (_: Exception) {}
        try {
            val extRoot = Environment.getExternalStorageDirectory()
            publicCamera1Dirs.add(File(extRoot, "Camera1"))
            publicCamera1Dirs.add(File(extRoot, "DCIM/Camera1"))
            publicCamera1Dirs.add(File(extRoot, "Movies/Camera1"))
            publicCamera1Dirs.add(File(extRoot, "Download/Camera1"))
        } catch (_: Exception) {}
        listOf(
            "/sdcard/DCIM/Camera1",
            "/storage/emulated/0/DCIM/Camera1",
            "/sdcard/Camera1",
            "/storage/emulated/0/Camera1",
            "/sdcard/Movies/Camera1",
            "/storage/emulated/0/Movies/Camera1",
            "/sdcard/Download/Camera1",
            "/storage/emulated/0/Download/Camera1"
        ).forEach { p ->
            try { publicCamera1Dirs.add(File(p)) } catch (_: Exception) {}
        }

        val uniquePublicCamDirs = publicCamera1Dirs.distinctBy {
            try { it.canonicalPath } catch (_: Exception) { it.absolutePath }
        }

        for (dir in uniquePublicCamDirs) {
            try {
                if (!dir.exists() || !dir.isDirectory) continue
                var folderHadFiles = false
                val files = dir.listFiles().orEmpty()
                for (f in files) {
                    if (f.isFile) {
                        val sz = f.length()
                        val abs = f.absolutePath
                        if (f.delete()) {
                            folderHadFiles = true
                            if (f.name.endsWith(".mp4", true) || f.name.endsWith(".mkv", true) ||
                                f.name.endsWith(".mov", true) || f.name.endsWith(".mp3", true) ||
                                f.name.endsWith(".wav", true)
                            ) {
                                deletedCount++
                                freedBytes += sz
                            }
                            deletedPathsForMediaScanner.add(abs)
                        }
                    } else if (f.isDirectory) {
                        f.listFiles()?.forEach { sub ->
                            val sz = sub.length()
                            val abs = sub.absolutePath
                            if (sub.delete()) {
                                deletedCount++
                                freedBytes += sz
                                deletedPathsForMediaScanner.add(abs)
                            }
                        }
                        try { f.delete() } catch (_: Exception) {}
                    }
                }
                // Hapus folder Camera1 publik agar tidak pernah muncul di album Galeri
                try { dir.delete() } catch (_: Exception) {}
                if (folderHadFiles) cleanedFoldersCount++
            } catch (_: Exception) {}
        }

        // 2. Migrasikan & bersihkan folder publik LiveHost_Backup_Vault (Download, DCIM, Documents, Root SDCard)
        val publicVaultDirs = mutableSetOf<File>()
        try {
            publicVaultDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LiveHost_Backup_Vault"))
            publicVaultDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "LiveHost_Backup_Vault"))
            publicVaultDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "LiveHost_Backup_Vault"))
            publicVaultDirs.add(File(Environment.getExternalStorageDirectory(), "LiveHost_Backup_Vault"))
        } catch (_: Exception) {}
        listOf(
            "/sdcard/Download/LiveHost_Backup_Vault",
            "/storage/emulated/0/Download/LiveHost_Backup_Vault",
            "/sdcard/DCIM/LiveHost_Backup_Vault",
            "/storage/emulated/0/DCIM/LiveHost_Backup_Vault",
            "/sdcard/Documents/LiveHost_Backup_Vault",
            "/storage/emulated/0/Documents/LiveHost_Backup_Vault",
            "/sdcard/LiveHost_Backup_Vault",
            "/storage/emulated/0/LiveHost_Backup_Vault"
        ).forEach { p ->
            try { publicVaultDirs.add(File(p)) } catch (_: Exception) {}
        }

        val internalVaultCanon = try { internalVaultDir.canonicalPath } catch (_: Exception) { internalVaultDir.absolutePath }
        val uniquePublicVaultDirs = publicVaultDirs.distinctBy {
            try { it.canonicalPath } catch (_: Exception) { it.absolutePath }
        }.filter {
            val c = try { it.canonicalPath } catch (_: Exception) { it.absolutePath }
            c != internalVaultCanon && !c.contains("/Android/data/") && !c.startsWith("/data/")
        }

        for (pubVault in uniquePublicVaultDirs) {
            try {
                if (!pubVault.exists() || !pubVault.isDirectory) continue
                var folderCleaned = false

                // A. Migrasikan database konsep jika belum ada di internalVaultDir
                val pubDb = File(pubVault, "concepts_vault_db.json")
                val intDb = File(internalVaultDir, "concepts_vault_db.json")
                if (pubDb.exists() && pubDb.length() > 0L) {
                    if (!intDb.exists() || intDb.length() == 0L || pubDb.lastModified() > intDb.lastModified()) {
                        try { pubDb.copyTo(intDb, overwrite = true) } catch (_: Exception) {}
                    }
                    try { pubDb.delete() } catch (_: Exception) {}
                }

                val pubCp = File(pubVault, "auto_checkpoint_state.json")
                val intCp = File(internalVaultDir, "auto_checkpoint_state.json")
                if (pubCp.exists() && pubCp.length() > 0L) {
                    if (!intCp.exists() || intCp.length() == 0L) {
                        try { pubCp.copyTo(intCp, overwrite = true) } catch (_: Exception) {}
                    }
                    try { pubCp.delete() } catch (_: Exception) {}
                }

                // B. Bersihkan media_vault publik (salin hanya file yang belum ada di internalCamera1Dir jika masih dipakai konsep)
                val pubMediaDir = File(pubVault, "media_vault")
                if (pubMediaDir.exists() && pubMediaDir.isDirectory) {
                    val protectedNames = try {
                        ConceptBackupManager.getInstance(context).getAllProtectedMediaFileNames()
                    } catch (_: Exception) { emptySet() }

                    val mediaFiles = pubMediaDir.listFiles().orEmpty()
                    for (mf in mediaFiles) {
                        if (!mf.isFile) continue
                        val sz = mf.length()
                        val abs = mf.absolutePath
                        // Jika file dilindungi Konsep Backup dan belum ada di internalMediaVaultDir maupun internalCamera1Dir, amankan ke internal
                        if (protectedNames.contains(mf.name) && sz > 0L) {
                            val inCam1 = File(internalCamera1Dir, mf.name)
                            val inIntVault = File(internalMediaVaultDir, mf.name)
                            if (!inCam1.exists() && !inIntVault.exists()) {
                                try {
                                    mf.copyTo(inIntVault, overwrite = true)
                                    migratedCount++
                                } catch (_: Exception) {}
                            }
                        }
                        if (mf.delete()) {
                            deletedCount++
                            freedBytes += sz
                            folderCleaned = true
                            deletedPathsForMediaScanner.add(abs)
                        }
                    }
                    try { pubMediaDir.delete() } catch (_: Exception) {}
                }

                // C. Hapus sisa file video/gambar lepas di pubVault (kecuali file .zip ekspor manual pengguna)
                pubVault.listFiles()?.forEach { f ->
                    if (f.isFile && !f.name.endsWith(".zip", true)) {
                        val sz = f.length()
                        val abs = f.absolutePath
                        if (f.delete()) {
                            if (f.name.endsWith(".mp4", true) || f.name.endsWith(".mkv", true) || f.name.endsWith(".mp3", true)) {
                                deletedCount++
                                freedBytes += sz
                            }
                            folderCleaned = true
                            deletedPathsForMediaScanner.add(abs)
                        }
                    }
                }
                if ((pubVault.listFiles()?.isEmpty()) == true) {
                    try { pubVault.delete() } catch (_: Exception) {}
                }
                if (folderCleaned) cleanedFoldersCount++
            } catch (_: Exception) {}
        }

        // 3. Bersihkan entri hantu di MediaStore Android & jalankan MediaScanner agar Galeri HP langsung bersih
        purgeMediaStoreEntries(context.contentResolver, deletedPathsForMediaScanner)
        if (deletedPathsForMediaScanner.isNotEmpty()) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    deletedPathsForMediaScanner.distinct().toTypedArray(),
                    null,
                    null
                )
            } catch (_: Exception) {}
        }

        return GalleryDuplicateCleanupReport(
            deletedFilesCount = deletedCount,
            freedBytes = freedBytes,
            migratedVaultFilesCount = migratedCount,
            cleanedFoldersCount = cleanedFoldersCount
        )
    }

    private fun purgeMediaStoreEntries(resolver: ContentResolver, deletedPaths: List<String>) {
        try {
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            // Hapus berdasarkan pola folder kerja aplikasi
            val folderPatterns = listOf(
                "%/DCIM/Camera1/%",
                "%/Movies/Camera1/%",
                "%/Download/Camera1/%",
                "%/Camera1/video_%.mp4",
                "%/Camera1/virtual.mp4",
                "%/LiveHost_Backup_Vault/media_vault/%"
            )
            for (pattern in folderPatterns) {
                try {
                    resolver.delete(
                        videoUri,
                        "${MediaStore.MediaColumns.DATA} LIKE ?",
                        arrayOf(pattern)
                    )
                } catch (_: Exception) {}
            }
            for (path in deletedPaths.take(200)) {
                try {
                    resolver.delete(
                        videoUri,
                        "${MediaStore.MediaColumns.DATA} = ?",
                        arrayOf(path)
                    )
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun cleanupStaleVirtualVideo(hookDirs: List<File>) {
        for (dir in hookDirs) {
            try {
                val v = File(dir, "virtual.mp4")
                if (v.exists()) v.delete()
                val dis = File(dir, "disable.jpg")
                if (!dis.exists()) {
                    dir.mkdirs()
                    dis.createNewFile()
                    dis.setReadable(true, false)
                }
            } catch (_: Exception) {}
        }
    }

    fun cleanupStaleIndexedVideos(hookDirs: List<File>, validCount: Int) {
        val indexedRegex = Regex("""^video_(\d+)\.mp4$""")
        for (dir in hookDirs) {
            try {
                if (!dir.exists()) continue
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    val m = indexedRegex.find(f.name)
                    if (m != null) {
                        val idx = m.groupValues[1].toIntOrNull()
                        if (idx != null && (idx >= validCount || validCount == 0)) {
                            try { f.delete() } catch (_: Exception) {}
                        }
                    } else if (f.name.startsWith("temp_") || f.name.startsWith("temp_indexed_") || f.name.startsWith(".tmp_")) {
                        try { f.delete() } catch (_: Exception) {}
                    }
                }
                if (validCount == 0) {
                    try { File(dir, "virtual.mp4").delete() } catch (_: Exception) {}
                    try { File(dir, "playlist.txt").delete() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun cleanupOrphanedMediaFiles(
        hookDirs: List<File>,
        validMediaPaths: Set<String>,
        validFileNames: Set<String> = emptySet(),
        validCount: Int
    ) {
        val indexedNames = (0 until validCount).map { "video_$it.mp4" }.toSet()
        val standardKeepNames = setOf(
            "virtual.mp4", "disable.jpg", "no-silent.jpg", "pause.jpg",
            "video_paused.txt", "video_audio_muted.txt", "audio_paused.txt", "mp3_audio_paused.txt",
            "audio_disabled.txt", "audio_path.txt", "auto_next.txt", "current_index.txt", "playlist.txt",
            "vcam_config.json", "rotation.txt", "mirror.txt", "flip_v.txt", "scale.txt", "audio.mp3",
            "audio.wav", ".nomedia"
        )

        for (dir in hookDirs) {
            try {
                if (!dir.exists()) continue
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    if (standardKeepNames.contains(f.name) || indexedNames.contains(f.name) || (validCount > 0 && f.name.startsWith("vs_rolling_"))) {
                        continue
                    }
                    // JANGAN PERNAH hapus file yang nama filenya cocok dengan video yang masih aktif di playlist!
                    if (validFileNames.contains(f.name)) {
                        continue
                    }
                    val absPath = f.absolutePath
                    val canPath = try { f.canonicalPath } catch (_: Exception) { absPath }
                    if (validMediaPaths.contains(absPath) || validMediaPaths.contains(canPath)) {
                        continue
                    }
                    if (f.name.endsWith(".mp4", true) || f.name.endsWith(".mkv", true)) {
                        try { f.delete() } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun cleanupStaleAudioFiles(hookDirs: List<File>) {
        val audioNames = listOf("audio.mp3", "audio.wav", "audio.m4a", "audio.aac", "audio.ogg", "audio_path.txt")
        for (dir in hookDirs) {
            try {
                for (name in audioNames) {
                    val f = File(dir, name)
                    if (f.exists()) f.delete()
                }
                val dis = File(dir, "audio_disabled.txt")
                if (!dis.exists()) {
                    dir.mkdirs()
                    dis.createNewFile()
                    dis.setReadable(true, false)
                }
            } catch (_: Exception) {}
        }
    }

    fun copyToAllLocations(hookDirs: List<File>, source: File, targetName: String) {
        if (!source.exists() || source.length() == 0L) return
        for (dir in hookDirs) {
            try {
                dir.mkdirs()
                dir.setReadable(true, false)
                dir.setWritable(true, false)
                dir.setExecutable(true, false)
                try {
                    val noMedia = File(dir, ".nomedia")
                    if (!noMedia.exists()) {
                        noMedia.createNewFile()
                        noMedia.setReadable(true, false)
                    }
                } catch (_: Exception) {}

                val target = File(dir, targetName)
                if (targetName == "virtual.mp4") {
                    try {
                        val dis = File(dir, "disable.jpg")
                        if (dis.exists()) dis.delete()
                    } catch (_: Exception) {}
                } else if (targetName == "audio.mp3" || targetName == "audio.wav") {
                    try {
                        val dis = File(dir, "audio_disabled.txt")
                        if (dis.exists()) dis.delete()
                    } catch (_: Exception) {}
                }
                if (target.canonicalPath == source.canonicalPath) continue

                val skipSameSize = (targetName != "virtual.mp4" && targetName != "audio.mp3" && targetName != "audio.wav")
                atomicCopyIfChanged(source, target, skipIfSameLength = skipSameSize)
            } catch (_: Exception) {}
        }
    }

    fun syncToTargetApps(
        internalDir: File,
        config: VcamConfig,
        packages: Set<String>
    ) {
        val videoSource = File(internalDir, "virtual.mp4")

        for (pkg in packages) {
            try {
                val appDirs = listOf(
                    File("/storage/emulated/0/Android/data/$pkg/files/Camera1"),
                    File("/storage/emulated/0/Android/data/$pkg/files/camera1"),
                    File("/sdcard/Android/data/$pkg/files/Camera1"),
                    File("/data/data/$pkg/files/Camera1"),
                    File("/data/user/0/$pkg/files/Camera1")
                )
                for (dir in appDirs) {
                    try {
                        dir.mkdirs()
                        dir.setReadable(true, false)
                        dir.setWritable(true, false)
                        dir.setExecutable(true, false)
                        if (videoSource.exists() && config.playlist.isNotEmpty()) {
                            val dest = File(dir, "virtual.mp4")
                            if (dest.canonicalPath != videoSource.canonicalPath) {
                                atomicCopyIfChanged(videoSource, dest, skipIfSameLength = false)
                            }
                            val dis = File(dir, "disable.jpg")
                            if (dis.exists()) dis.delete()
                        } else {
                            val dest = File(dir, "virtual.mp4")
                            if (dest.exists()) dest.delete()
                            val dis = File(dir, "disable.jpg")
                            if (!dis.exists()) {
                                dis.createNewFile()
                                dis.setReadable(true, false)
                            }
                        }
                        if (config.audioPlaylist.isNotEmpty() && config.audioPath.isNotEmpty()) {
                            File(dir, "audio_path.txt").apply {
                                writeText(config.audioPath)
                                setReadable(true, false)
                            }
                            val dis = File(dir, "audio_disabled.txt")
                            if (dis.exists()) dis.delete()
                        } else {
                            val dis = File(dir, "audio_disabled.txt")
                            if (!dis.exists()) {
                                dis.createNewFile()
                                dis.setReadable(true, false)
                            }
                            try { File(dir, "audio_path.txt").delete() } catch (_: Exception) {}
                        }
                        File(dir, "rotation.txt").apply {
                            writeText(config.rotation.toString())
                            setReadable(true, false)
                        }
                        File(dir, "mirror.txt").apply {
                            writeText(if (config.isMirrored) "1" else "0")
                            setReadable(true, false)
                        }
                        File(dir, "flip_v.txt").apply {
                            writeText(if (config.isVerticalFlipped) "1" else "0")
                            setReadable(true, false)
                        }
                        File(dir, "scale.txt").apply {
                            writeText(config.scaleMode.name)
                            setReadable(true, false)
                        }
                        File(dir, "video_paused.txt").apply {
                            writeText(if (config.isVideoPaused) "1" else "0")
                            setReadable(true, false)
                        }
                        File(dir, "audio_paused.txt").apply {
                            writeText(if (config.isAudioPaused) "1" else "0")
                            setReadable(true, false)
                        }
                        File(dir, "auto_next.txt").apply {
                            if (config.autoNextEnabled) {
                                writeText(config.autoNextMode.name)
                            } else {
                                writeText("OFF")
                            }
                            setReadable(true, false)
                        }
                        File(dir, "current_index.txt").apply {
                            writeText(config.currentVideoIndex.toString())
                            setReadable(true, false)
                        }

                        // Hapus video indeks lama yang sudah melebihi ukuran playlist baru
                        cleanupStaleIndexedVideos(listOf(dir), config.playlist.size)

                        val playlistFiles = config.playlist
                        val playlistLines = mutableListOf<String>()
                        if (playlistFiles.isEmpty()) {
                            try { File(dir, "playlist.txt").delete() } catch (_: Exception) {}
                        } else {
                            for (i in playlistFiles.indices) {
                                val src = File(playlistFiles[i].path)
                                if (src.exists() && src.length() > 0L) {
                                    val targetVideo = File(dir, "video_$i.mp4")
                                    if (targetVideo.canonicalPath != src.canonicalPath) {
                                        atomicCopyIfChanged(src, targetVideo, skipIfSameLength = true)
                                    }
                                    playlistLines.add("video_$i.mp4")
                                }
                            }
                            File(dir, "playlist.txt").apply {
                                writeText(playlistLines.joinToString("\n"))
                                setReadable(true, false)
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun generateTestVideoMp4(outputFile: File, width: Int = 1280, height: Int = 720, label: String = "SMPTE") {
        try {
            outputFile.parentFile?.mkdirs()
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            val colors = intArrayOf(
                Color.rgb(192, 192, 192),
                Color.rgb(192, 192, 0),
                Color.rgb(0, 192, 192),
                Color.rgb(0, 192, 0),
                Color.rgb(192, 0, 192),
                Color.rgb(192, 0, 0),
                Color.rgb(0, 0, 192)
            )

            val totalFrames = 60
            for (f in 0 until totalFrames) {
                val canvas = inputSurface.lockCanvas(null)
                val barWidth = width / colors.size
                val topHeight = (height * 0.75f).toInt()

                for (i in colors.indices) {
                    val p = Paint().apply { color = colors[i] }
                    canvas.drawRect(Rect(i * barWidth, 0, (i + 1) * barWidth, topHeight), p)
                }

                val bottomPaint = Paint().apply { color = Color.rgb(20, 24, 35) }
                canvas.drawRect(Rect(0, topHeight, width, height), bottomPaint)

                val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = (height * 0.045f).coerceAtLeast(24f)
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("TAJASB PRO • $label", width / 2f, topHeight + (height * 0.08f), textPaint)
                canvas.drawText("LOOP FRAME #${f + 1}/$totalFrames", width / 2f, topHeight + (height * 0.16f), textPaint.apply {
                    textSize = (height * 0.035f).coerceAtLeast(18f)
                    color = Color.rgb(0, 229, 255)
                })

                inputSurface.unlockCanvasAndPost(canvas)

                while (true) {
                    val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (outIndex >= 0) {
                        val encodedData = encoder.getOutputBuffer(outIndex)
                        if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                            bufferInfo.presentationTimeUs = (f * 1_000_000L) / 30L
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                    }
                }
            }

            encoder.signalEndOfInputStream()
            while (true) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(outIndex)
                    if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                } else {
                    break
                }
            }

            encoder.stop()
            encoder.release()
            inputSurface.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun retrieveMediaMetadata(path: String): MediaMetadata? {
        val file = File(path)
        if (!file.exists()) return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val (width, height) = if (rot == 90 || rot == 270) {
                rawH to rawW
            } else {
                rawW to rawH
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"

            MediaMetadata(
                title = file.name,
                width = width,
                height = height,
                durationMs = duration,
                sizeBytes = file.length(),
                mimeType = mime
            )
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
