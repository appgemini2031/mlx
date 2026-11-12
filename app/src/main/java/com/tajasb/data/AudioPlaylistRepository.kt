package com.tajasb.data

import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import com.tajasb.model.AudioItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Modular repository helper for MP3 audio playlist serialization, lookup, and management.
 * Extracted from VcamRepository to keep audio playlist management modular.
 */
object AudioPlaylistRepository {

    fun parseAudioPlaylistJson(jsonStr: String): List<AudioItem> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<AudioItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("path", "")
                if (path.isNotBlank() && File(path).exists()) {
                    list.add(
                        AudioItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "audio_${i + 1}.mp3"),
                            path = path,
                            uri = obj.optString("uri", "").takeIf { it.isNotBlank() },
                            durationMs = obj.optLong("durationMs", 0L),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                            isMuted = obj.optBoolean("isMuted", false),
                            isEnabled = obj.optBoolean("isEnabled", true)
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeAudioPlaylistJson(list: List<AudioItem>): String {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("path", item.path)
                put("uri", item.uri ?: "")
                put("durationMs", item.durationMs)
                put("sizeBytes", item.sizeBytes)
                put("addedAt", item.addedAt)
                put("isMuted", item.isMuted)
                put("isEnabled", item.isEnabled)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "m4a", "aac", "ogg", "flac", "opus"
    )

    private fun isSupportedAudioFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return SUPPORTED_AUDIO_EXTENSIONS.contains(ext)
    }

    private fun isZipUriOrFile(repo: VcamRepository, uri: Uri, rawFileName: String): Boolean {
        if (rawFileName.endsWith(".zip", ignoreCase = true)) return true
        val mime = try { repo.context.contentResolver.getType(uri) ?: "" } catch (_: Throwable) { "" }
        if (mime.contains("zip", ignoreCase = true)) return true
        // Periksa 4 byte pertama magic header ZIP (PK\x03\x04)
        return try {
            repo.context.contentResolver.openInputStream(uri)?.use { ins ->
                val header = ByteArray(4)
                val read = ins.read(header)
                read == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
            } ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private fun extractAudioItemsFromZipUri(
        repo: VcamRepository,
        zipUri: Uri,
        existingActivePaths: MutableSet<String>
    ): List<AudioItem> {
        val extractedTempFiles = mutableListOf<Pair<String, File>>()
        val tempExtractDir = File(repo.context.cacheDir, "zip_audio_extract_${System.currentTimeMillis()}")
        if (!tempExtractDir.exists()) tempExtractDir.mkdirs()

        try {
            repo.context.contentResolver.openInputStream(zipUri)?.use { rawIn ->
                java.util.zip.ZipInputStream(java.io.BufferedInputStream(rawIn)).use { zis ->
                    var entry = zis.nextEntry
                    var idx = 0
                    val copyBuf = ByteArray(65536)
                    while (entry != null) {
                        val rawEntryName = entry.name ?: ""
                        val baseName = File(rawEntryName).name
                        val isHiddenOrMac = rawEntryName.contains("__MACOSX") ||
                            baseName.startsWith(".") ||
                            baseName.startsWith("._")
                        if (!entry.isDirectory && !isHiddenOrMac && isSupportedAudioFileName(baseName)) {
                            idx++
                            val safeBaseName = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            val tmpFile = File(tempExtractDir, "${idx}_$safeBaseName")
                            try {
                                FileOutputStream(tmpFile).use { fos ->
                                    while (true) {
                                        val read = zis.read(copyBuf)
                                        if (read <= 0) break
                                        fos.write(copyBuf, 0, read)
                                    }
                                }
                                if (tmpFile.exists() && tmpFile.length() > 128L) {
                                    extractedTempFiles.add(safeBaseName to tmpFile)
                                } else {
                                    tmpFile.delete()
                                }
                            } catch (_: Throwable) {
                                tmpFile.delete()
                            }
                        }
                        try { zis.closeEntry() } catch (_: Throwable) {}
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // Urutkan secara natural (misal: audio_1.mp3, audio_2.mp3, ..., audio_10.mp3)
        val sortedExtracted = extractedTempFiles.sortedWith(Comparator { a, b ->
            compareNaturalStrings(a.first, b.first)
        })

        val conceptMgr = ConceptBackupManager.getInstance(repo.context)
        val vaultDir = repo.getInternalCamera1Directory()
        if (!vaultDir.exists()) vaultDir.mkdirs()
        val resultItems = mutableListOf<AudioItem>()

        for ((displayFileName, tmpFile) in sortedExtracted) {
            try {
                val fileUri = Uri.fromFile(tmpFile)
                val storedPair = conceptMgr.storeOrReuseVaultMediaFile(
                    vaultDir = vaultDir,
                    uri = fileUri,
                    rawFileName = displayFileName,
                    isAudio = true
                )
                val destFile = storedPair?.first ?: run {
                    val fallback = File(vaultDir, "zip_${System.currentTimeMillis()}_$displayFileName")
                    tmpFile.copyTo(fallback, overwrite = true)
                    fallback
                }
                val cleanDisplay = storedPair?.second ?: displayFileName
                val canonPath = try { destFile.canonicalPath } catch (_: Exception) { destFile.absolutePath }

                if (!existingActivePaths.contains(canonPath) &&
                    !existingActivePaths.contains(destFile.absolutePath) &&
                    destFile.exists() &&
                    destFile.length() > 0L
                ) {
                    val savedMeta = conceptMgr.findSavedAudioMetadataByPath(destFile.absolutePath)
                    val meta = repo.retrieveMediaMetadata(destFile.absolutePath)
                    resultItems.add(
                        AudioItem(
                            id = savedMeta?.id ?: UUID.randomUUID().toString(),
                            name = cleanDisplay,
                            path = destFile.absolutePath,
                            uri = Uri.fromFile(destFile).toString(),
                            durationMs = meta?.durationMs ?: savedMeta?.durationMs ?: 0L,
                            sizeBytes = destFile.length()
                        )
                    )
                    existingActivePaths.add(canonPath)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                try { tmpFile.delete() } catch (_: Throwable) {}
            }
        }

        try { tempExtractDir.deleteRecursively() } catch (_: Throwable) {}
        return resultItems
    }

    private fun compareNaturalStrings(a: String, b: String): Int {
        val regex = Regex("(\\d+)|(\\D+)")
        val partsA = regex.findAll(a.lowercase()).map { it.value }.toList()
        val partsB = regex.findAll(b.lowercase()).map { it.value }.toList()
        val len = minOf(partsA.size, partsB.size)
        for (i in 0 until len) {
            val pA = partsA[i]
            val pB = partsB[i]
            val numA = pA.toBigIntegerOrNull()
            val numB = pB.toBigIntegerOrNull()
            val cmp = if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else {
                pA.compareTo(pB)
            }
            if (cmp != 0) return cmp
        }
        return partsA.size.compareTo(partsB.size)
    }

    suspend fun addAudios(repo: VcamRepository, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var addedCount = 0
        val newItems = mutableListOf<AudioItem>()
        val conceptMgr = ConceptBackupManager.getInstance(repo.context)
        val existingActivePaths = repo._config.value.audioPlaylist.map {
            try { File(it.path).canonicalPath } catch (_: Exception) { it.path }
        }.toMutableSet()

        for (uri in uris) {
            try {
                val fileName = repo.getFileName(uri) ?: "audio_${System.currentTimeMillis()}_${addedCount + 1}.mp3"
                if (isZipUriOrFile(repo, uri, fileName)) {
                    val zipExtractedItems = extractAudioItemsFromZipUri(repo, uri, existingActivePaths)
                    if (zipExtractedItems.isNotEmpty()) {
                        newItems.addAll(zipExtractedItems)
                        addedCount += zipExtractedItems.size
                    }
                    continue
                }

                val cleanName = if (isSupportedAudioFileName(fileName)) fileName else "$fileName.mp3"
                val storedPair = conceptMgr.storeOrReuseVaultMediaFile(
                    vaultDir = repo.getInternalCamera1Directory(),
                    uri = uri,
                    rawFileName = cleanName,
                    isAudio = true
                ) ?: continue

                val destFile = storedPair.first
                val displayCleanName = storedPair.second
                val canonPath = try { destFile.canonicalPath } catch (_: Exception) { destFile.absolutePath }

                if (existingActivePaths.contains(canonPath) || existingActivePaths.contains(destFile.absolutePath)) {
                    addedCount++
                    continue
                }

                if (destFile.exists() && destFile.length() > 0L) {
                    val savedMeta = conceptMgr.findSavedAudioMetadataByPath(destFile.absolutePath)
                    val meta = repo.retrieveMediaMetadata(destFile.absolutePath)
                    val item = AudioItem(
                        id = savedMeta?.id ?: UUID.randomUUID().toString(),
                        name = displayCleanName,
                        path = destFile.absolutePath,
                        uri = uri.toString(),
                        durationMs = meta?.durationMs ?: savedMeta?.durationMs ?: 0L,
                        sizeBytes = destFile.length()
                    )
                    newItems.add(item)
                    existingActivePaths.add(canonPath)
                    addedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (newItems.isNotEmpty()) {
            val wasEmpty = repo._config.value.audioPlaylist.isEmpty()
            val updatedPlaylist = repo._config.value.audioPlaylist + newItems
            val newIndex = if (wasEmpty) 0 else repo._config.value.currentAudioIndex.coerceIn(0, updatedPlaylist.size - 1)
            val activeItem = updatedPlaylist[newIndex]

            repo.updateConfig {
                it.copy(
                    audioPlaylist = updatedPlaylist,
                    currentAudioIndex = newIndex,
                    audioPath = activeItem.path,
                    audioUri = activeItem.uri,
                    audioName = activeItem.name
                )
            }
            try {
                com.tajasb.infinity.IfsManager.getInstance(repo.context).onMediaPlaylistChanged(
                    newVideos = repo._config.value.playlist,
                    newAudios = updatedPlaylist,
                    broadcastIfActive = false
                )
            } catch (_: Throwable) {}
            if (wasEmpty) {
                withContext(Dispatchers.Main) {
                    repo.musicPlayer.resetSessionAudioState()
                    val shouldPlay = !repo._config.value.isMp3AudioPaused
                    repo.musicPlayer.playAudioItem(activeItem, startPlaying = shouldPlay)
                }
            }
            repo.writeHookConfigFiles(repo._config.value)
            repo.broadcastControlEvent(
                action = "RELOAD_AUDIO",
                audioPath = activeItem.path,
                videoPath = repo._config.value.videoPath
            )
            conceptMgr.scheduleAutoCheckpoint()
        }
        addedCount
    }

    suspend fun removeAudioItem(repo: VcamRepository, id: String) = withContext(Dispatchers.IO) {
        val currentList = repo._config.value.audioPlaylist
        val indexToRemove = currentList.indexOfFirst { it.id == id }
        if (indexToRemove == -1) return@withContext

        val itemToRemove = currentList[indexToRemove]
        val conceptMgr = ConceptBackupManager.getInstance(repo.context)
        // Jangan hapus file fisik jika masih dipakai oleh Konsep Backup lain
        if (!conceptMgr.isMediaPathProtectedByAnyConcept(itemToRemove.path)) {
            try { File(itemToRemove.path).delete() } catch (_: Exception) {}
        }

        try {
            com.tajasb.infinity.IfsManager.getInstance(repo.context).clearAudioRole(id)
        } catch (_: Throwable) {}

        val isRemovedL2 = itemToRemove.id == repo._config.value.audioLayer2Id ||
            (repo._config.value.audioLayer2Path.isNotBlank() && itemToRemove.path == repo._config.value.audioLayer2Path)
        if (isRemovedL2) {
            withContext(Dispatchers.Main) {
                repo.musicPlayer.stopLayer2(clearTrackState = true)
            }
            repo.updateConfig {
                it.copy(
                    audioLayer2Id = null,
                    audioLayer2Path = "",
                    audioLayer2Name = "",
                    isAudioLayer2Enabled = false
                )
            }
        }

        if (itemToRemove.id in repo._config.value.audioLayer3Ids) {
            val nextL3Ids = repo._config.value.audioLayer3Ids - itemToRemove.id
            repo.updateConfig {
                it.copy(audioLayer3Ids = nextL3Ids)
            }
            withContext(Dispatchers.Main) {
                if (repo.musicPlayer.currentTrackLayer3.value?.id == itemToRemove.id) {
                    repo.musicPlayer.stopLayer3(clearTrackState = true)
                }
                repo.musicPlayer.setLayer3RingtoneIds(nextL3Ids)
            }
        }

        val updatedList = currentList.filter { it.id != id }
        val newIndex = if (updatedList.isEmpty()) 0 else {
            if (repo._config.value.currentAudioIndex >= updatedList.size) {
                updatedList.size - 1
            } else repo._config.value.currentAudioIndex
        }

        val activeItem = if (updatedList.isNotEmpty()) updatedList[newIndex] else null
        val activePath = activeItem?.path ?: ""
        val activeName = activeItem?.name ?: ""
        val activeUri = activeItem?.uri

        if (activeItem == null) {
            withContext(Dispatchers.Main) {
                repo.musicPlayer.resetSessionAudioState()
            }
            repo.removeAudio()
            return@withContext
        }

        repo.updateConfig {
            it.copy(
                audioPlaylist = updatedList,
                currentAudioIndex = newIndex,
                audioPath = activePath,
                audioName = activeName,
                audioUri = activeUri
            )
        }

        try {
            com.tajasb.infinity.IfsManager.getInstance(repo.context).onMediaPlaylistChanged(
                newVideos = repo._config.value.playlist,
                newAudios = updatedList,
                broadcastIfActive = false
            )
        } catch (_: Throwable) {}

        withContext(Dispatchers.Main) {
            if (repo.musicPlayer.currentTrack.value?.id == id) {
                val wasPlaying = repo.musicPlayer.isPlaying.value
                repo.musicPlayer.resetSessionAudioState()
                repo.musicPlayer.playAudioItem(activeItem, startPlaying = wasPlaying)
            }
        }

        repo.writeHookConfigFiles(repo._config.value)
        repo.broadcastControlEvent(
            action = "RELOAD_AUDIO",
            audioPath = activePath,
            videoPath = repo._config.value.videoPath
        )
        conceptMgr.onMediaRemovedFromActivePlaylist(listOf(itemToRemove.path))
    }

    suspend fun selectAudioByIndex(repo: VcamRepository, index: Int, autoPlay: Boolean = true): AudioItem? = withContext(Dispatchers.IO) {
        val playlist = repo._config.value.audioPlaylist
        if (playlist.isEmpty()) return@withContext null
        val safeIndex = index.coerceIn(0, playlist.size - 1)
        val selected = playlist[safeIndex]

        // Jika audio ini dipilih untuk Tipe 2, putar di layer 2 secara mandiri dan jangan tabrakkan ke Tipe 1
        val isL2Track = selected.id == repo._config.value.audioLayer2Id ||
            (repo._config.value.audioLayer2Path.isNotBlank() && selected.path == repo._config.value.audioLayer2Path)
        if (isL2Track) {
            if (autoPlay) {
                withContext(Dispatchers.Main) {
                    repo.musicPlayer.playLayer2()
                }
            }
            return@withContext selected
        }

        val isRollingVsTrack = selected.id.startsWith("vs_audio_rolling_") || selected.path.contains("vs_audio_rolling_")
        if (autoPlay && !isRollingVsTrack) {
            try {
                val ifsMgr = com.tajasb.infinity.IfsManager.getInstance(repo.context)
                if (ifsMgr.audioMode.value == com.tajasb.infinity.IfsAudioMode.SV4_VS && ifsMgr.sv4AudioRunning.value) {
                    ifsMgr.stopSv4AudioEngineForEdit()
                }
            } catch (_: Throwable) {}
        }

        repo.updateConfig {
            it.copy(
                currentAudioIndex = safeIndex,
                audioPath = selected.path,
                audioUri = selected.uri,
                audioName = selected.name,
                isMp3AudioPaused = !autoPlay
            )
        }

        repo.writeHookConfigFiles(repo._config.value)
        repo.broadcastControlEvent(action = "SELECT_AUDIO", audioPath = selected.path, isMp3AudioPaused = !autoPlay)

        withContext(Dispatchers.Main) {
            repo.musicPlayer.playAudioItem(selected, startPlaying = autoPlay)
        }
        selected
    }

    suspend fun nextAudio(repo: VcamRepository): AudioItem? = withContext(Dispatchers.IO) {
        val cfg = repo._config.value
        val playlist = cfg.audioPlaylist
        if (playlist.isEmpty()) return@withContext null
        val curTrack = repo.musicPlayer.currentTrack.value ?: cfg.currentAudio
        if (curTrack != null && (curTrack.id.startsWith("vs_audio_rolling_") || curTrack.path.contains("vs_audio_rolling_"))) {
            withContext(Dispatchers.Main) {
                repo.musicPlayer.seekTo((repo.musicPlayer.durationMs.value - 120L).coerceAtLeast(0L))
            }
            kotlinx.coroutines.delay(180L)
            return@withContext repo.musicPlayer.currentTrack.value ?: repo._config.value.currentAudio
        }
        if (repo.musicPlayer.isSrMode.value) {
            withContext(Dispatchers.Main) {
                repo.musicPlayer.playNextSrRandomTrack()
            }
            return@withContext repo.musicPlayer.currentTrack.value ?: repo._config.value.currentAudio
        }
        val type1Indices = playlist.indices.filter { !cfg.isExcludedFromType1(playlist[it]) }
        val poolIndices = if (type1Indices.isNotEmpty()) type1Indices else playlist.indices.toList()
        val enabledIndices = poolIndices.filter { playlist[it].isEnabled }
        val candidates = if (enabledIndices.isNotEmpty()) enabledIndices else poolIndices
        val curIdx = cfg.currentAudioIndex
        val higher = candidates.filter { it > curIdx }
        val nextIndex = if (higher.isNotEmpty()) higher.first() else candidates.first()
        selectAudioByIndex(repo, nextIndex, autoPlay = true)
    }

    suspend fun prevAudio(repo: VcamRepository): AudioItem? = withContext(Dispatchers.IO) {
        val cfg = repo._config.value
        val playlist = cfg.audioPlaylist
        if (playlist.isEmpty()) return@withContext null
        val type1Indices = playlist.indices.filter { !cfg.isExcludedFromType1(playlist[it]) }
        val poolIndices = if (type1Indices.isNotEmpty()) type1Indices else playlist.indices.toList()
        val enabledIndices = poolIndices.filter { playlist[it].isEnabled }
        val candidates = if (enabledIndices.isNotEmpty()) enabledIndices else poolIndices
        val curIdx = cfg.currentAudioIndex
        val lower = candidates.filter { it < curIdx }
        val prevIndex = if (lower.isNotEmpty()) lower.last() else candidates.last()
        selectAudioByIndex(repo, prevIndex, autoPlay = true)
    }

    suspend fun moveAudioUp(repo: VcamRepository, index: Int): Boolean = withContext(Dispatchers.IO) {
        val list = repo._config.value.audioPlaylist.toMutableList()
        if (index <= 0 || index >= list.size) return@withContext false
        val curActiveId = repo._config.value.currentAudio?.id
        val item = list.removeAt(index)
        list.add(index - 1, item)
        val newActiveIndex = if (curActiveId != null) {
            val found = list.indexOfFirst { it.id == curActiveId }
            if (found != -1) found else repo._config.value.currentAudioIndex
        } else repo._config.value.currentAudioIndex

        repo.updateConfig {
            it.copy(
                audioPlaylist = list,
                currentAudioIndex = newActiveIndex
            )
        }
        true
    }

    suspend fun moveAudioDown(repo: VcamRepository, index: Int): Boolean = withContext(Dispatchers.IO) {
        val list = repo._config.value.audioPlaylist.toMutableList()
        if (index < 0 || index >= list.size - 1) return@withContext false
        val curActiveId = repo._config.value.currentAudio?.id
        val item = list.removeAt(index)
        list.add(index + 1, item)
        val newActiveIndex = if (curActiveId != null) {
            val found = list.indexOfFirst { it.id == curActiveId }
            if (found != -1) found else repo._config.value.currentAudioIndex
        } else repo._config.value.currentAudioIndex

        repo.updateConfig {
            it.copy(
                audioPlaylist = list,
                currentAudioIndex = newActiveIndex
            )
        }
        true
    }

    suspend fun toggleAudioMute(repo: VcamRepository, index: Int): Boolean = withContext(Dispatchers.IO) {
        val playlist = repo._config.value.audioPlaylist
        if (index !in playlist.indices) return@withContext false
        val item = playlist[index]
        val newMuted = !item.isMuted
        val updatedPlaylist = playlist.mapIndexed { i, a ->
            if (i == index) a.copy(isMuted = newMuted) else a
        }

        repo.updateConfig {
            it.copy(audioPlaylist = updatedPlaylist)
        }

        val isCurrent = (index == repo._config.value.currentAudioIndex)
        if (isCurrent) {
            withContext(Dispatchers.Main) {
                repo.musicPlayer.applyEffectiveVolume()
            }
        }
        newMuted
    }

    suspend fun toggleAudioEnable(repo: VcamRepository, index: Int): Boolean = withContext(Dispatchers.IO) {
        val playlist = repo._config.value.audioPlaylist
        if (index !in playlist.indices) return@withContext false
        val item = playlist[index]
        val newEnabled = !item.isEnabled
        val updatedPlaylist = playlist.mapIndexed { i, a ->
            if (i == index) a.copy(isEnabled = newEnabled) else a
        }

        repo.updateConfig {
            it.copy(audioPlaylist = updatedPlaylist)
        }

        if (!newEnabled && index == repo._config.value.currentAudioIndex) {
            val enabledIndices = updatedPlaylist.indices.filter { updatedPlaylist[it].isEnabled }
            if (enabledIndices.isNotEmpty()) {
                val nextIdx = enabledIndices.firstOrNull { it > index } ?: enabledIndices.first()
                selectAudioByIndex(repo, nextIdx, autoPlay = repo.musicPlayer.isPlaying.value)
            } else {
                withContext(Dispatchers.Main) {
                    repo.musicPlayer.pause()
                }
            }
        }
        newEnabled
    }

    suspend fun toggleAllAudioMute(repo: VcamRepository): Boolean = withContext(Dispatchers.IO) {
        val playlist = repo._config.value.audioPlaylist
        val isMutedCurrently = if (playlist.isNotEmpty()) {
            playlist.all { it.isMuted }
        } else {
            repo._config.value.isMp3AudioMuted
        }
        val targetMuted = !isMutedCurrently

        val updatedPlaylist = playlist.map { it.copy(isMuted = targetMuted) }
        repo.updateConfig {
            it.copy(
                audioPlaylist = updatedPlaylist,
                isMp3AudioMuted = targetMuted
            )
        }
        withContext(Dispatchers.Main) {
            repo.musicPlayer.setMuted(targetMuted)
        }
        targetMuted
    }

    suspend fun toggleAllAudioEnabled(repo: VcamRepository): Boolean = withContext(Dispatchers.IO) {
        val playlist = repo._config.value.audioPlaylist
        if (playlist.isEmpty()) return@withContext false
        val anyEnabled = playlist.any { it.isEnabled }
        val targetEnabled = !anyEnabled
        val updatedPlaylist = playlist.map { it.copy(isEnabled = targetEnabled) }
        repo.updateConfig {
            it.copy(audioPlaylist = updatedPlaylist)
        }
        if (!targetEnabled) {
            withContext(Dispatchers.Main) {
                repo.musicPlayer.pause()
            }
        }
        targetEnabled
    }

    suspend fun renameAudio(repo: VcamRepository, id: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val rawClean = newName.trim()
        if (rawClean.isEmpty()) return@withContext false
        val currentList = repo._config.value.audioPlaylist
        val targetItem = currentList.find { it.id == id } ?: return@withContext false
        val oldFile = File(targetItem.path)
        val oldName = targetItem.name

        val oldExt = if (oldFile.extension.isNotBlank()) oldFile.extension else "mp3"
        val cleanName = if (rawClean.contains(".")) rawClean else "$rawClean.$oldExt"
        val safeFileName = cleanName.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        var newPath = targetItem.path
        var fileRenamed = false
        val pathsToScan = mutableListOf<String>()

        if (oldFile.exists()) {
            pathsToScan.add(oldFile.absolutePath)
            val parentDir = oldFile.parentFile ?: repo.getInternalCamera1Directory()
            val targetFile = File(parentDir, safeFileName)

            if (targetFile.canonicalPath != oldFile.canonicalPath) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val ok = oldFile.renameTo(targetFile)
                if (ok) {
                    newPath = targetFile.absolutePath
                    fileRenamed = true
                } else {
                    try {
                        oldFile.copyTo(targetFile, overwrite = true)
                        oldFile.delete()
                        newPath = targetFile.absolutePath
                        fileRenamed = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                newPath = targetFile.absolutePath
                fileRenamed = true
            }
            pathsToScan.add(newPath)
        }

        targetItem.uri?.let { uriStr ->
            try {
                val uri = Uri.parse(uriStr)
                if (DocumentsContract.isDocumentUri(repo.context, uri)) {
                    DocumentsContract.renameDocument(repo.context.contentResolver, uri, safeFileName)
                }
            } catch (_: Exception) {}
        }

        try {
            if (pathsToScan.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    repo.context,
                    pathsToScan.distinct().toTypedArray(),
                    null
                ) { path, _ ->
                    com.tajasb.debug.VcamDebugLogger.d("VcamRepo", "MediaScanner scanned: $path")
                }
            }
        } catch (_: Exception) {}

        val list = currentList.map { item ->
            if (item.id == id) item.copy(name = safeFileName, path = newPath) else item
        }
        val isCurrentActive = repo._config.value.currentAudio?.id == id
        val updatedAudioName = if (isCurrentActive) safeFileName else repo._config.value.audioName
        val updatedAudioPath = if (isCurrentActive) newPath else repo._config.value.audioPath

        repo.updateConfig {
            it.copy(
                audioPlaylist = list,
                audioName = updatedAudioName,
                audioPath = updatedAudioPath
            )
        }

        val logMsg = if (fileRenamed) {
            "✏️ [RENAME_AUDIO] Berkas audio asli berhasil diubah namanya: \"$oldName\" -> \"$safeFileName\" (Aktif: $isCurrentActive)"
        } else {
            "✏️ [RENAME_AUDIO] Nama audio playlist diperbarui: \"$oldName\" -> \"$safeFileName\" (Aktif: $isCurrentActive)"
        }
        com.tajasb.debug.VcamDebugLogger.i("VcamRepo", logMsg)

        if (isCurrentActive) {
            repo.writeHookConfigFiles(repo._config.value)
            repo.broadcastControlEvent(
                action = "RELOAD_AUDIO",
                audioPath = updatedAudioPath
            )
        }
        true
    }
}
