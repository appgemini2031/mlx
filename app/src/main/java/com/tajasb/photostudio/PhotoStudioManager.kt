package com.tajasb.photostudio

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.tajasb.data.VcamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PhotoStudioManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "tajasb_photo_studio_prefs"
        private const val KEY_SELECTED_EFFECT = "key_selected_effect"
        private const val KEY_DOWNLOADED_EFFECTS = "key_downloaded_effects"
        private const val KEY_IMAGE_SPEED = "key_image_speed"
        private const val KEY_EFFECT_SPEED = "key_effect_speed"
        private const val KEY_DURATION_SEC = "key_duration_sec"
        private const val KEY_SAVED_PRESET_SUMMARY = "key_saved_preset_summary"
        private const val KEY_IS_EXPANDED = "key_is_expanded"

        @Volatile
        private var INSTANCE: PhotoStudioManager? = null

        fun getInstance(context: Context): PhotoStudioManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PhotoStudioManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val studioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<PhotoStudioState> = _state.asStateFlow()

    private val _previewBitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val previewBitmaps: StateFlow<List<Bitmap>> = _previewBitmaps.asStateFlow()

    private var renderJob: Job? = null

    private fun loadInitialState(): PhotoStudioState {
        val effect = PhotoAnimEffect.fromId(
            prefs.getString(KEY_SELECTED_EFFECT, PhotoAnimEffect.RAIN.id) ?: PhotoAnimEffect.RAIN.id
        )
        val rawDownloaded = prefs.getString(KEY_DOWNLOADED_EFFECTS, null)
        val downloadedSet = mutableSetOf(PhotoAnimEffect.NONE.id, PhotoAnimEffect.RAIN.id)
        if (!rawDownloaded.isNullOrBlank()) {
            try {
                val arr = JSONArray(rawDownloaded)
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i)
                    if (id.isNotBlank()) downloadedSet.add(id)
                }
            } catch (_: Throwable) {}
        }
        val imgSpeed = ImageMotionSpeed.fromId(
            prefs.getString(KEY_IMAGE_SPEED, ImageMotionSpeed.NORMAL_10.id) ?: ImageMotionSpeed.NORMAL_10.id
        )
        val fxSpeed = EffectAnimSpeed.fromId(
            prefs.getString(KEY_EFFECT_SPEED, EffectAnimSpeed.NORMAL_10.id) ?: EffectAnimSpeed.NORMAL_10.id
        )
        val durPreset = OutputDurationPreset.fromSeconds(prefs.getInt(KEY_DURATION_SEC, 10))
        val presetSummary = prefs.getString(KEY_SAVED_PRESET_SUMMARY, null)
        val expanded = prefs.getBoolean(KEY_IS_EXPANDED, false)

        return PhotoStudioState(
            isExpanded = expanded,
            selectedEffect = effect,
            downloadedEffectIds = downloadedSet,
            imageSpeed = imgSpeed,
            effectSpeed = fxSpeed,
            durationPreset = durPreset,
            savedPresetSummary = presetSummary
        )
    }

    private fun persistSettings(current: PhotoStudioState) {
        val arr = JSONArray()
        current.downloadedEffectIds.forEach { arr.put(it) }
        prefs.edit()
            .putBoolean(KEY_IS_EXPANDED, current.isExpanded)
            .putString(KEY_SELECTED_EFFECT, current.selectedEffect.id)
            .putString(KEY_DOWNLOADED_EFFECTS, arr.toString())
            .putString(KEY_IMAGE_SPEED, current.imageSpeed.id)
            .putString(KEY_EFFECT_SPEED, current.effectSpeed.id)
            .putInt(KEY_DURATION_SEC, current.durationPreset.seconds)
            .putString(KEY_SAVED_PRESET_SUMMARY, current.savedPresetSummary)
            .apply()
    }

    fun setExpanded(expanded: Boolean) {
        val next = _state.value.copy(isExpanded = expanded)
        _state.value = next
        persistSettings(next)
    }

    fun toggleExpanded() {
        setExpanded(!_state.value.isExpanded)
    }

    fun selectEffect(effect: PhotoAnimEffect) {
        val updatedDownloaded = _state.value.downloadedEffectIds + effect.id
        val next = _state.value.copy(
            selectedEffect = effect,
            downloadedEffectIds = updatedDownloaded,
            statusMessage = "Efek dipilih: ${effect.icon} ${effect.title} — ${effect.description}"
        )
        _state.value = next
        persistSettings(next)
    }

    fun downloadEffectPack(effect: PhotoAnimEffect, onComplete: (String) -> Unit = {}) {
        studioScope.launch {
            val packDir = File(context.filesDir, "studio_effect_packs").apply { mkdirs() }
            val packFile = File(packDir, "pack_${effect.id.lowercase(Locale.US)}.json")
            val json = JSONObject().apply {
                put("effectId", effect.id)
                put("title", effect.title)
                put("description", effect.description)
                put("downloadedAtMs", System.currentTimeMillis())
                put("version", 1)
            }
            packFile.writeText(json.toString())

            val updatedSet = _state.value.downloadedEffectIds + effect.id
            val msg = "📦 Paket efek '${effect.title}' berhasil diunduh & disimpan di lokal."
            val next = _state.value.copy(
                downloadedEffectIds = updatedSet,
                statusMessage = msg
            )
            _state.value = next
            persistSettings(next)
            withContext(Dispatchers.Main) { onComplete(msg) }
        }
    }

    fun downloadAllEffects(onComplete: (String) -> Unit = {}) {
        studioScope.launch {
            val packDir = File(context.filesDir, "studio_effect_packs").apply { mkdirs() }
            val allIds = mutableSetOf<String>()
            PhotoAnimEffect.entries.forEach { effect ->
                allIds.add(effect.id)
                val packFile = File(packDir, "pack_${effect.id.lowercase(Locale.US)}.json")
                val json = JSONObject().apply {
                    put("effectId", effect.id)
                    put("title", effect.title)
                    put("description", effect.description)
                    put("downloadedAtMs", System.currentTimeMillis())
                    put("version", 1)
                }
                packFile.writeText(json.toString())
            }
            val msg = "✅ Seluruh ${PhotoAnimEffect.entries.size} Paket Efek Animasi berhasil disimpan ke lokal!"
            val next = _state.value.copy(
                downloadedEffectIds = allIds,
                statusMessage = msg
            )
            _state.value = next
            persistSettings(next)
            withContext(Dispatchers.Main) { onComplete(msg) }
        }
    }

    fun saveCurrentPreset(onComplete: (String) -> Unit = {}) {
        val cur = _state.value
        val summary = "${cur.selectedEffect.icon} ${cur.selectedEffect.title} • Foto ${cur.imageSpeed.shortLabel} • Efek ${cur.effectSpeed.label} • ${cur.durationPreset.label}"
        val next = cur.copy(
            savedPresetSummary = summary,
            statusMessage = "💾 Preset disimpan: $summary"
        )
        _state.value = next
        persistSettings(next)
        onComplete("💾 Preset Studio Foto disimpan: $summary")
    }

    fun setImageSpeed(speed: ImageMotionSpeed) {
        val next = _state.value.copy(
            imageSpeed = speed,
            statusMessage = "Kecepatan gerak/transisi foto diatur ke ${speed.label}"
        )
        _state.value = next
        persistSettings(next)
    }

    fun setEffectSpeed(speed: EffectAnimSpeed) {
        val next = _state.value.copy(
            effectSpeed = speed,
            statusMessage = "Kecepatan partikel efek diatur ke ${speed.label}"
        )
        _state.value = next
        persistSettings(next)
    }

    fun setDurationPreset(preset: OutputDurationPreset) {
        val next = _state.value.copy(
            durationPreset = preset,
            statusMessage = "Durasi output video MP4 diatur ke ${preset.seconds} detik (${preset.label})"
        )
        _state.value = next
        persistSettings(next)
    }

    fun onPhotosPicked(uris: List<Uri>, append: Boolean = true) {
        if (uris.isEmpty()) return
        studioScope.launch {
            val cacheDir = File(context.filesDir, "studio_photos_cache").apply { mkdirs() }
            val newItems = mutableListOf<SelectedStudioPhoto>()
            val decodedBitmaps = mutableListOf<Bitmap>()

            val baseList = if (append) _state.value.selectedPhotos else emptyList()
            val baseBitmaps = if (append) _previewBitmaps.value else emptyList()

            uris.forEachIndexed { idx, uri ->
                val displayName = resolveDisplayName(uri) ?: "Foto_${System.currentTimeMillis()}_${idx + 1}.jpg"
                val localFile = File(cacheDir, "photo_${UUID.randomUUID()}.jpg")
                val bmp = decodeScaledBitmapFromUri(uri, maxDim = 1280)
                if (bmp != null) {
                    try {
                        FileOutputStream(localFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                    } catch (_: Throwable) {}
                    newItems.add(
                        SelectedStudioPhoto(
                            id = UUID.randomUUID().toString(),
                            uriString = localFile.absolutePath,
                            displayName = displayName
                        )
                    )
                    decodedBitmaps.add(bmp)
                }
            }

            if (newItems.isNotEmpty()) {
                val combinedPhotos = (baseList + newItems).take(20)
                val combinedBitmaps = (baseBitmaps + decodedBitmaps).take(20)
                _previewBitmaps.value = combinedBitmaps
                _state.value = _state.value.copy(
                    isExpanded = true,
                    selectedPhotos = combinedPhotos,
                    statusMessage = "📸 ${combinedPhotos.size} foto siap di-preview & digabungkan ke MP4 VCam."
                )
            }
        }
    }

    fun removePhoto(photoId: String) {
        val curPhotos = _state.value.selectedPhotos
        val idx = curPhotos.indexOfFirst { it.id == photoId }
        if (idx < 0) return
        val updatedPhotos = curPhotos.toMutableList().apply { removeAt(idx) }
        val curBitmaps = _previewBitmaps.value.toMutableList()
        if (idx in curBitmaps.indices) {
            curBitmaps.removeAt(idx)
        }
        _previewBitmaps.value = curBitmaps
        _state.value = _state.value.copy(
            selectedPhotos = updatedPhotos,
            statusMessage = if (updatedPhotos.isEmpty()) {
                "Semua foto dihapus. Pilih foto dari galeri untuk memulai."
            } else {
                "📸 Tersisa ${updatedPhotos.size} foto di Studio."
            }
        )
    }

    fun clearAllPhotos() {
        _previewBitmaps.value = emptyList()
        _state.value = _state.value.copy(
            selectedPhotos = emptyList(),
            statusMessage = "Daftar foto Studio dibersihkan."
        )
    }

    /**
     * Renders the selected photos + effect to an H.264 MP4 (720x1280 portrait, 25 FPS)
     * in the background SupervisorJob scope and automatically adds it to VcamRepository.
     */
    fun renderToVcamMp4(onFinished: (Boolean, String) -> Unit = { _, _ -> }) {
        if (_state.value.isRendering) {
            onFinished(false, "Proses render sedang berjalan di latar belakang.")
            return
        }

        val currentSnap = _state.value
        val bitmapsToRender = _previewBitmaps.value.toList()
        if (bitmapsToRender.isEmpty()) {
            val msg = "Pilih minimal 1 foto terlebih dahulu sebelum merender ke MP4 VCam."
            _state.value = currentSnap.copy(statusMessage = "⚠️ $msg")
            onFinished(false, msg)
            return
        }

        renderJob = studioScope.launch {
            val totalFrames = currentSnap.durationPreset.seconds * PhotoStudioRenderer.TARGET_FPS
            _state.value = _state.value.copy(
                isRendering = true,
                renderProgressPercent = 1,
                renderedFrames = 0,
                totalFrames = totalFrames,
                statusMessage = "⏳ Merender ${bitmapsToRender.size} foto + ${currentSnap.selectedEffect.title} (${currentSnap.durationPreset.seconds}d)..."
            )

            val outputDir = File(context.filesDir, "studio_rendered_mp4").apply { mkdirs() }
            val stamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
            val safeEffectName = currentSnap.selectedEffect.id.lowercase(Locale.US)
            val outFile = File(
                outputDir,
                "Studio_${safeEffectName}_${currentSnap.durationPreset.seconds}s_$stamp.mp4"
            )

            val result = PhotoStudioRenderer.encodeStudioVideoToMp4(
                outputFile = outFile,
                bitmaps = bitmapsToRender,
                durationSeconds = currentSnap.durationPreset.seconds,
                effect = currentSnap.selectedEffect,
                imageSpeed = currentSnap.imageSpeed,
                effectSpeed = currentSnap.effectSpeed,
                onProgress = { done, total, pct ->
                    _state.value = _state.value.copy(
                        isRendering = true,
                        renderedFrames = done,
                        totalFrames = total,
                        renderProgressPercent = pct,
                        statusMessage = "🎬 Merender MP4 VCam: $pct% ($done/$total frame • ${currentSnap.durationPreset.seconds}d)..."
                    )
                }
            )

            if (result.isSuccess) {
                val renderedFile = result.getOrThrow()
                val repo = VcamRepository.getInstance(context)
                repo.addVideos(listOf(Uri.fromFile(renderedFile)))

                val okMsg = "✅ Selesai! Video '${renderedFile.name}' (${currentSnap.durationPreset.seconds}d) otomatis masuk ke Playlist VCam."
                _state.value = _state.value.copy(
                    isRendering = false,
                    renderProgressPercent = 100,
                    renderedFrames = totalFrames,
                    totalFrames = totalFrames,
                    lastRenderedMp4Path = renderedFile.absolutePath,
                    statusMessage = okMsg
                )
                withContext(Dispatchers.Main) {
                    onFinished(true, okMsg)
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Gagal merender video MP4"
                val failMsg = "❌ Gagal render MP4: $err"
                _state.value = _state.value.copy(
                    isRendering = false,
                    renderProgressPercent = 0,
                    statusMessage = failMsg
                )
                withContext(Dispatchers.Main) {
                    onFinished(false, failMsg)
                }
            }
        }
    }

    /**
     * Exports the last rendered MP4 file to public Movies/Tajasb_Studio & Download folders.
     */
    fun saveLastMp4ToMoviesOrDownload(onFinished: (Boolean, String) -> Unit = { _, _ -> }) {
        val lastPath = _state.value.lastRenderedMp4Path
        if (lastPath.isNullOrBlank() || !File(lastPath).exists()) {
            val msg = "Belum ada hasil render MP4. Klik 'Render ke VCam' terlebih dahulu."
            onFinished(false, msg)
            return
        }

        studioScope.launch {
            val sourceFile = File(lastPath)
            val fileName = sourceFile.name
            var savedLocationDesc = ""

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Tajasb_Studio")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val itemUri = resolver.insert(collection, values)
                    if (itemUri != null) {
                        resolver.openOutputStream(itemUri)?.use { out ->
                            FileInputStream(sourceFile).use { inp -> inp.copyTo(out) }
                        }
                        values.clear()
                        values.put(MediaStore.Video.Media.IS_PENDING, 0)
                        resolver.update(itemUri, values, null, null)
                        savedLocationDesc = "Movies/Tajasb_Studio/$fileName"
                    }
                }

                // Also copy to public Movies/Download directory directly if accessible
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "Tajasb_Studio"
                ).apply { mkdirs() }
                val destFile = File(moviesDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("video/mp4"), null)
                if (savedLocationDesc.isEmpty()) {
                    savedLocationDesc = destFile.absolutePath
                }

                val msg = "💾 MP4 berhasil disimpan ke Galeri/Folder: $savedLocationDesc"
                _state.value = _state.value.copy(
                    lastSavedPublicPath = savedLocationDesc,
                    statusMessage = msg
                )
                withContext(Dispatchers.Main) {
                    onFinished(true, msg)
                }
            } catch (e: Exception) {
                val errMsg = "⚠️ Gagal menyimpan ke folder publik: ${e.message}"
                _state.value = _state.value.copy(statusMessage = errMsg)
                withContext(Dispatchers.Main) {
                    onFinished(false, errMsg)
                }
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment
        } catch (_: Throwable) {
            uri.lastPathSegment
        }
    }

    private fun decodeScaledBitmapFromUri(uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            var sampleSize = 1
            while ((boundsOpts.outWidth / sampleSize) > maxDim || (boundsOpts.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
