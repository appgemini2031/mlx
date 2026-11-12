package com.tajasb.hook

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

/**
 * VirtualAudioHookHandler:
 * Resolves virtual MP3/WAV audio files across target package directories, public DCIM folders,
 * and ContentProvider fallback, and evaluates per-app audio mute/pause flags.
 */
object VirtualAudioHookHandler {

    fun getVirtualAudioFile(pkg: String?, appContext: Context?): File? {
        if (isAudioDisabled(pkg)) {
            return null
        }
        val candidatePaths = mutableListOf<File>()

        val audioPathFiles = mutableListOf<File>()
        if (!pkg.isNullOrBlank()) {
            audioPathFiles.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/audio_path.txt"))
            audioPathFiles.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/audio_path.txt"))
            audioPathFiles.add(File("/data/user/0/$pkg/files/Camera1/audio_path.txt"))
            audioPathFiles.add(File("/data/data/$pkg/files/Camera1/audio_path.txt"))
        }
        audioPathFiles.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/audio_path.txt"))
        audioPathFiles.add(File("/sdcard/DCIM/Camera1/audio_path.txt"))
        audioPathFiles.add(File("/storage/emulated/0/DCIM/Camera1/audio_path.txt"))
        audioPathFiles.add(File("/sdcard/Camera1/audio_path.txt"))
        audioPathFiles.add(File("/data/local/tmp/audio_path.txt"))

        for (apf in audioPathFiles) {
            try {
                if (apf.exists() && apf.canRead()) {
                    val path = apf.readText().trim()
                    if (path.isNotEmpty()) {
                        val directFile = File(path)
                        if (directFile.exists() && directFile.canRead() && directFile.length() > 0) {
                            return directFile
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        val audioNames = listOf("audio.mp3", "audio.wav", "audio.m4a", "audio.aac", "audio.ogg")
        if (!pkg.isNullOrBlank()) {
            for (name in audioNames) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/$name"))
                candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/$name"))
                candidatePaths.add(File("/data/user/0/$pkg/files/Camera1/$name"))
                candidatePaths.add(File("/data/data/$pkg/files/Camera1/$name"))
            }
        }

        val vcamPkgs = listOf(
            "com.tajasb.pro.issber",
            "com.tajasb.pro",
            "com.tajasb",
            "com.aistudio.tajasb",
            "com.aistudio.cuancame",
            "com.example"
        )
        for (vPkg in vcamPkgs) {
            for (name in audioNames) {
                candidatePaths.add(File("/storage/emulated/0/Android/data/$vPkg/files/Camera1/$name"))
                candidatePaths.add(File("/data/user/0/$vPkg/files/Camera1/$name"))
                candidatePaths.add(File("/data/data/$vPkg/files/Camera1/$name"))
            }
        }

        for (name in audioNames) {
            candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/$name"))
            candidatePaths.add(File("/sdcard/DCIM/Camera1/$name"))
            candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/$name"))
            candidatePaths.add(File(Environment.getExternalStorageDirectory(), "DCIM/Camera1/$name"))
            candidatePaths.add(File("/sdcard/Camera1/$name"))
            candidatePaths.add(File("/storage/emulated/0/Camera1/$name"))
            candidatePaths.add(File("/data/local/tmp/$name"))
            candidatePaths.add(File("/data/local/tmp/Camera1/$name"))
        }

        for (file in candidatePaths) {
            try {
                if (file.exists() && file.canRead() && file.length() > 0) {
                    return file
                }
            } catch (_: Throwable) {}
        }

        if (appContext != null) {
            try {
                val cacheOut = File(appContext.cacheDir, "virtual_vcam_audio.mp3")
                if (cacheOut.exists() && cacheOut.length() > 0) {
                    return cacheOut
                }
                val authorities = listOf(
                    "com.tajasb.pro.issber.provider",
                    "com.tajasb.pro.provider",
                    "com.tajasb.provider",
                    "com.aistudio.tajasb.provider",
                    "com.aistudio.cuancame.provider"
                )
                for (auth in authorities) {
                    val contentUri = Uri.parse("content://$auth/audio.mp3")
                    try {
                        appContext.contentResolver.openInputStream(contentUri)?.use { input ->
                            FileOutputStream(cacheOut).use { output ->
                                input.copyTo(output)
                            }
                            if (cacheOut.exists() && cacheOut.length() > 0) {
                                return cacheOut
                            }
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }

        return null
    }

    fun isAudioDisabled(pkg: String?): Boolean {
        val candidatePaths = mutableListOf<File>()
        if (!pkg.isNullOrBlank()) {
            candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/audio_disabled.txt"))
            candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/audio_disabled.txt"))
            candidatePaths.add(File("/data/user/0/$pkg/files/Camera1/audio_disabled.txt"))
            candidatePaths.add(File("/data/data/$pkg/files/Camera1/audio_disabled.txt"))
        }
        candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/audio_disabled.txt"))
        candidatePaths.add(File("/sdcard/DCIM/Camera1/audio_disabled.txt"))
        candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/audio_disabled.txt"))
        candidatePaths.add(File("/sdcard/Camera1/audio_disabled.txt"))
        candidatePaths.add(File("/data/local/tmp/audio_disabled.txt"))
        return candidatePaths.any {
            try { it.exists() } catch (_: Throwable) { false }
        }
    }

    fun isMuted(pkg: String?): Boolean {
        val noSilentPaths = mutableListOf<File>()
        val silentPaths = mutableListOf<File>()

        if (!pkg.isNullOrBlank()) {
            noSilentPaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/no-silent.jpg"))
            noSilentPaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/no-silent.jpg"))
            noSilentPaths.add(File("/data/user/0/$pkg/files/Camera1/no-silent.jpg"))
            noSilentPaths.add(File("/data/data/$pkg/files/Camera1/no-silent.jpg"))

            silentPaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/silent.jpg"))
            silentPaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/mute.txt"))
        }
        noSilentPaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/no-silent.jpg"))
        noSilentPaths.add(File("/sdcard/DCIM/Camera1/no-silent.jpg"))
        noSilentPaths.add(File("/storage/emulated/0/DCIM/Camera1/no-silent.jpg"))
        noSilentPaths.add(File("/sdcard/Camera1/no-silent.jpg"))
        noSilentPaths.add(File("/data/local/tmp/no-silent.jpg"))

        silentPaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/silent.jpg"))
        silentPaths.add(File("/sdcard/DCIM/Camera1/silent.jpg"))
        silentPaths.add(File("/storage/emulated/0/DCIM/Camera1/silent.jpg"))
        silentPaths.add(File("/sdcard/Camera1/silent.jpg"))

        val isExplicitSilent = silentPaths.any {
            try { it.exists() } catch (_: Throwable) { false }
        }
        if (isExplicitSilent) return true

        val hasNoSilentFlag = noSilentPaths.any {
            try { it.exists() } catch (_: Throwable) { false }
        }
        if (hasNoSilentFlag) return false

        return false
    }

    fun isAudioPaused(pkg: String?): Boolean? {
        val candidatePaths = mutableListOf<File>()
        if (!pkg.isNullOrBlank()) {
            candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/audio_paused.txt"))
            candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/audio_paused.txt"))
        }
        candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/audio_paused.txt"))
        candidatePaths.add(File("/sdcard/DCIM/Camera1/audio_paused.txt"))
        candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/audio_paused.txt"))

        for (file in candidatePaths) {
            try {
                if (file.exists() && file.canRead()) {
                    return file.readText().trim() == "1"
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    fun isVideoAudioMuted(pkg: String?): Boolean? {
        val candidatePaths = mutableListOf<File>()
        if (!pkg.isNullOrBlank()) {
            candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/Camera1/video_audio_muted.txt"))
            candidatePaths.add(File("/storage/emulated/0/Android/data/$pkg/files/camera1/video_audio_muted.txt"))
        }
        candidatePaths.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera1/video_audio_muted.txt"))
        candidatePaths.add(File("/sdcard/DCIM/Camera1/video_audio_muted.txt"))
        candidatePaths.add(File("/storage/emulated/0/DCIM/Camera1/video_audio_muted.txt"))

        for (file in candidatePaths) {
            try {
                if (file.exists() && file.canRead()) {
                    return file.readText().trim() == "1"
                }
            } catch (_: Throwable) {}
        }
        return null
    }
}
