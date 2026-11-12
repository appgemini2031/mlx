package com.tajasb.quran

import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tajasb.data.VcamRepository
import com.tajasb.infinity.IfsAudioMode
import com.tajasb.infinity.IfsManager
import com.tajasb.infinity.IfsVideoMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class QuranAudioDownloadManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "tajasb_quran_downloader_prefs"
        private const val KEY_SELECTED_IMAM_ID = "key_selected_imam_id"
        private const val KEY_SELECTED_ROLE_ID = "key_selected_role_id"
        private const val KEY_IS_EXPANDED = "key_is_expanded"

        @Volatile
        private var INSTANCE: QuranAudioDownloadManager? = null

        fun getInstance(context: Context): QuranAudioDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QuranAudioDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<QuranDownloadState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    @Volatile
    private var previewMediaPlayer: MediaPlayer? = null

    init {
        refreshAllDownloadedFiles()
    }

    private fun loadInitialState(): QuranDownloadState {
        val imamId = prefs.getString(KEY_SELECTED_IMAM_ID, QuranCatalog.IMAMS.first().id)
            ?: QuranCatalog.IMAMS.first().id
        val imam = QuranCatalog.findImamById(imamId)
        val roleId = prefs.getString(KEY_SELECTED_ROLE_ID, QuranAudioRoleOption.AUTO.id)
            ?: QuranAudioRoleOption.AUTO.id
        val role = QuranAudioRoleOption.fromId(roleId)
        val expanded = prefs.getBoolean(KEY_IS_EXPANDED, false)

        return QuranDownloadState(
            isExpanded = expanded,
            selectedImam = imam,
            selectedRole = role,
            selectedSurahNumbers = QuranCatalog.PREFERRED_SURAH_NUMBERS
        )
    }

    private fun persistPreferences(current: QuranDownloadState) {
        prefs.edit()
            .putBoolean(KEY_IS_EXPANDED, current.isExpanded)
            .putString(KEY_SELECTED_IMAM_ID, current.selectedImam.id)
            .putString(KEY_SELECTED_ROLE_ID, current.selectedRole.id)
            .apply()
    }

    fun toggleExpanded() {
        val next = _state.value.copy(isExpanded = !_state.value.isExpanded)
        _state.value = next
        persistPreferences(next)
    }

    fun selectImam(imam: QuranImam) {
        val next = _state.value.copy(
            selectedImam = imam,
            statusMessage = "Qari dipilih: ${imam.displayName} (${imam.styleBadge})"
        )
        _state.value = next
        persistPreferences(next)
        refreshExistingDownloadedFilesForImam(imam)
    }

    fun selectAudioRole(role: QuranAudioRoleOption) {
        val next = _state.value.copy(
            selectedRole = role,
            statusMessage = "Peran Audio diatur ke ${role.label}: ${role.description}"
        )
        _state.value = next
        persistPreferences(next)
    }

    fun switchTab(tab: QuranTabMode) {
        _state.value = _state.value.copy(activeTab = tab)
        if (tab == QuranTabMode.SAVED) {
            refreshAllDownloadedFiles()
        }
    }

    fun setSavedSurahFilterImam(imamId: String?) {
        _state.value = _state.value.copy(savedSurahFilterImamId = imamId)
    }

    fun updateSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun toggleSurahSelection(surahNumber: Int) {
        val cur = _state.value.selectedSurahNumbers
        val updated = if (cur.contains(surahNumber)) cur - surahNumber else cur + surahNumber
        _state.value = _state.value.copy(selectedSurahNumbers = updated)
    }

    fun selectAll114() {
        _state.value = _state.value.copy(
            selectedSurahNumbers = QuranCatalog.ALL_114_SURAH_NUMBERS,
            statusMessage = "✅ Seluruh 114 Surat dipilih."
        )
    }

    fun selectJuz30() {
        _state.value = _state.value.copy(
            selectedSurahNumbers = QuranCatalog.JUZ_30_SURAH_NUMBERS,
            statusMessage = "✅ Juz 30 (Surat 078 An-Naba' – 114 An-Nas) dipilih."
        )
    }

    fun selectPreferredSurahs() {
        _state.value = _state.value.copy(
            selectedSurahNumbers = QuranCatalog.PREFERRED_SURAH_NUMBERS,
            statusMessage = "✅ 6 Surat Pilihan (Al-Fatihah, Al-Kahfi, Yasin, Ar-Rahman, Al-Waqi'ah, Al-Mulk) dipilih."
        )
    }

    fun resetSelection() {
        _state.value = _state.value.copy(
            selectedSurahNumbers = emptySet(),
            statusMessage = "Pilihan surat direset."
        )
    }

    /**
     * Plays a live streaming voice preview for the specified Imam & Surah using an independent
     * MediaPlayer instance so it works concurrently even while batch downloads are running.
     */
    fun startVoiceTest(imam: QuranImam = _state.value.selectedImam, surahNumber: Int? = null) {
        val targetSurahNum = surahNumber
            ?: _state.value.selectedSurahNumbers.minOrNull()
            ?: 1
        val surah = QuranCatalog.findSurahByNumber(targetSurahNum) ?: QuranCatalog.SURAHS.first()

        stopVoiceTestInternal(updateUiState = false)

        _state.value = _state.value.copy(
            isLoadingVoiceTest = true,
            isTestingVoice = false,
            testingSurahNumber = surah.number,
            testingImamId = imam.id,
            voiceTestStatusText = "⏳ Memuat Suara ${imam.displayName} (QS. ${surah.code3} ${surah.nameLatin})..."
        )

        managerScope.launch {
            // Check if already downloaded locally first for instant playback
            val existingLocal = resolveLocalSurahFile(imam, surah)
            val streamSource = if (existingLocal != null && existingLocal.exists() && existingLocal.length() > 1024L) {
                existingLocal.absolutePath
            } else {
                imam.buildSurahAudioUrl(surah.number, useFallback = false)
            }

            playVoiceTestWithFallback(
                imam = imam,
                surah = surah,
                sourceUrlOrPath = streamSource,
                triedFallback = false
            )
        }
    }

    private fun playVoiceTestWithFallback(
        imam: QuranImam,
        surah: QuranSurah,
        sourceUrlOrPath: String,
        triedFallback: Boolean
    ) {
        try {
            val mp = MediaPlayer()
            previewMediaPlayer = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(sourceUrlOrPath)
            mp.setOnPreparedListener { player ->
                try {
                    player.start()
                    _state.value = _state.value.copy(
                        isLoadingVoiceTest = false,
                        isTestingVoice = true,
                        testingSurahNumber = surah.number,
                        testingImamId = imam.id,
                        voiceTestStatusText = "🔊 Memutar: ${imam.displayName} • QS. ${surah.code3} ${surah.nameLatin}"
                    )
                } catch (_: Throwable) {}
            }
            mp.setOnCompletionListener {
                stopVoiceTest()
            }
            mp.setOnErrorListener { _, _, _ ->
                if (!triedFallback) {
                    stopVoiceTestInternal(updateUiState = false)
                    val fallbackUrl = imam.buildSurahAudioUrl(surah.number, useFallback = true)
                    playVoiceTestWithFallback(imam, surah, fallbackUrl, triedFallback = true)
                } else {
                    _state.value = _state.value.copy(
                        isLoadingVoiceTest = false,
                        isTestingVoice = false,
                        testingSurahNumber = null,
                        testingImamId = null,
                        voiceTestStatusText = "⚠️ Gagal memuat sampel suara ${imam.displayName}. Periksa koneksi internet."
                    )
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoadingVoiceTest = false,
                isTestingVoice = false,
                testingSurahNumber = null,
                testingImamId = null,
                voiceTestStatusText = "⚠️ Gagal memutar sampel suara: ${e.message}"
            )
        }
    }

    fun stopVoiceTest() {
        stopVoiceTestInternal(updateUiState = true)
    }

    private fun stopVoiceTestInternal(updateUiState: Boolean) {
        try {
            previewMediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (_: Throwable) {}
        previewMediaPlayer = null

        if (updateUiState) {
            _state.value = _state.value.copy(
                isLoadingVoiceTest = false,
                isTestingVoice = false,
                testingSurahNumber = null,
                testingImamId = null,
                voiceTestStatusText = ""
            )
        }
    }

    /**
     * Sequentially downloads selected Surahs in the background and immediately adds each
     * completed Surah to VcamRepository.addAudios with the selected role (SV1, SV2, SV3, SV4, or AUTO).
     */
    fun startSequentialDownload(onFeedback: (String) -> Unit = {}) {
        if (_state.value.isDownloading) {
            onFeedback("Proses download Al-Quran sedang berjalan di latar belakang.")
            return
        }

        val currentSnap = _state.value
        val sortedSurahNumbers = currentSnap.selectedSurahNumbers.toList().sorted()
        if (sortedSurahNumbers.isEmpty()) {
            val msg = "Pilih minimal 1 surat Al-Quran untuk didownload."
            _state.value = currentSnap.copy(statusMessage = "⚠️ $msg")
            onFeedback(msg)
            return
        }

        val imam = currentSnap.selectedImam
        val role = currentSnap.selectedRole

        downloadJob = managerScope.launch {
            _state.value = _state.value.copy(
                isDownloading = true,
                completedCount = 0,
                totalQueueCount = sortedSurahNumbers.size,
                currentSurahProgressPercent = 0,
                statusMessage = "⬇️ Memulai download ${sortedSurahNumbers.size} surat (${imam.displayName})..."
            )

            var successCount = 0
            val repo = VcamRepository.getInstance(context)
            val ifsMgr = IfsManager.getInstance(context)

            for ((idx, surahNum) in sortedSurahNumbers.withIndex()) {
                if (!isActive) break
                val surah = QuranCatalog.findSurahByNumber(surahNum) ?: continue

                _state.value = _state.value.copy(
                    currentDownloadingSurah = surah,
                    currentSurahProgressPercent = 0,
                    statusMessage = "⬇️ [${idx + 1}/${sortedSurahNumbers.size}] Mengunduh ${surah.buildFileName(imam.id)}..."
                )

                val fileResult = downloadOrReuseSurahFile(imam, surah) { pct ->
                    _state.value = _state.value.copy(
                        currentSurahProgressPercent = pct
                    )
                }

                if (fileResult.isSuccess) {
                    val mp3File = fileResult.getOrThrow()
                    // Immediately add this surah to VcamRepository audio playlist in real-time!
                    repo.addAudios(listOf(Uri.fromFile(mp3File)))

                    // Apply selected role (AUTO, SV1, SV2, SV3, SV4) immediately
                    applyAudioRoleForNewlyAddedSurah(
                        repo = repo,
                        ifsMgr = ifsMgr,
                        fileName = mp3File.name,
                        role = role,
                        queueIndex = idx
                    )

                    successCount++
                    val key = "${imam.id}_${surah.number}"
                    _state.value = _state.value.copy(
                        completedCount = successCount,
                        downloadedSurahKeys = _state.value.downloadedSurahKeys + key,
                        currentSurahProgressPercent = 100,
                        statusMessage = "✅ [${successCount}/${sortedSurahNumbers.size}] ${mp3File.name} masuk ke Playlist Audio (${role.shortBadge})!"
                    )
                } else {
                    val err = fileResult.exceptionOrNull()?.message ?: "Gagal unduh"
                    _state.value = _state.value.copy(
                        statusMessage = "⚠️ Gagal mengunduh QS. ${surah.code3} ${surah.nameLatin}: $err"
                    )
                }
            }

            val finalMsg = if (successCount > 0) {
                "🎉 Selesai! $successCount surat (${imam.displayName}) tersimpan di Music/Quran_${imam.id} & masuk Playlist Audio (${role.shortBadge})."
            } else {
                "⚠️ Tidak ada surat yang berhasil diunduh. Periksa koneksi internet Anda."
            }

            _state.value = _state.value.copy(
                isDownloading = false,
                currentDownloadingSurah = null,
                currentSurahProgressPercent = 0,
                statusMessage = finalMsg
            )
            withContext(Dispatchers.Main) {
                onFeedback(finalMsg)
            }
        }
    }

    fun cancelDownload(onFeedback: (String) -> Unit = {}) {
        managerScope.launch {
            downloadJob?.cancelAndJoin()
            downloadJob = null
            val msg = "⏹️ Antrean download Al-Quran dihentikan. Surat yang sudah selesai tetap ada di Playlist."
            _state.value = _state.value.copy(
                isDownloading = false,
                currentDownloadingSurah = null,
                currentSurahProgressPercent = 0,
                statusMessage = msg
            )
            withContext(Dispatchers.Main) {
                onFeedback(msg)
            }
        }
    }

    private fun applyAudioRoleForNewlyAddedSurah(
        repo: VcamRepository,
        ifsMgr: IfsManager,
        fileName: String,
        role: QuranAudioRoleOption,
        queueIndex: Int
    ) {
        try {
            val playlist = repo.config.value.audioPlaylist
            val addedItem = playlist.lastOrNull {
                it.name.equals(fileName, ignoreCase = true) || File(it.path).name.equals(fileName, ignoreCase = true)
            } ?: playlist.lastOrNull() ?: return

            when (role) {
                QuranAudioRoleOption.AUTO -> {
                    ifsMgr.setAudioAsSr(addedItem.id)
                }
                QuranAudioRoleOption.SV1 -> {
                    ifsMgr.setAudioAsSr(addedItem.id)
                    val vtPkgs = ifsMgr.vtPackages.value
                    if (queueIndex < vtPkgs.size) {
                        val pkg = vtPkgs[queueIndex]
                        if (pkg.linkedAudioId.isNullOrBlank()) {
                            ifsMgr.setLinkedAudio(pkg.vtNumber, addedItem.id)
                        }
                    }
                }
                QuranAudioRoleOption.SV2 -> {
                    ifsMgr.setAudioAsSr(addedItem.id)
                    val vtPkgs = ifsMgr.vtPackages.value
                    if (queueIndex < vtPkgs.size) {
                        val pkg = vtPkgs[queueIndex]
                        if (pkg.linkedAudioId.isNullOrBlank()) {
                            ifsMgr.setLinkedAudio(pkg.vtNumber, addedItem.id)
                        }
                    }
                }
                QuranAudioRoleOption.SV3 -> {
                    ifsMgr.setAudioAsSr(addedItem.id)
                }
                QuranAudioRoleOption.SV4 -> {
                    val currentLIds = ifsMgr.sv4AudioLIds.value
                    if (!currentLIds.contains(addedItem.id)) {
                        ifsMgr.setSv4AudioLIds(currentLIds + addedItem.id)
                    }
                    ifsMgr.setAudioMode(IfsAudioMode.SV4_VS)
                }
            }
        } catch (_: Throwable) {}
    }

    fun getPublicQuranFolder(imam: QuranImam): File {
        val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val dir = File(publicMusic, "Quran_${imam.id}")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return if (dir.exists() && dir.canWrite()) {
            dir
        } else {
            File(context.filesDir, "Music/Quran_${imam.id}").apply { mkdirs() }
        }
    }

    fun resolveLocalSurahFile(imam: QuranImam, surah: QuranSurah): File? {
        val fileName = surah.buildFileName(imam.id)
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Quran_${imam.id}"
        )
        val publicFile = File(publicDir, fileName)
        if (publicFile.exists() && publicFile.length() > 1024L) return publicFile

        val internalDir = File(context.filesDir, "Music/Quran_${imam.id}")
        val internalFallback = File(internalDir, fileName)
        if (internalFallback.exists() && internalFallback.length() > 1024L) return internalFallback

        // Fallback pencarian prefix nomor surat (misal: 001_*) jika nama file sedikit berbeda
        if (publicDir.exists()) {
            val match = publicDir.listFiles()?.firstOrNull { it.name.startsWith("${surah.code3}_") && it.length() > 1024L }
            if (match != null) return match
        }
        if (internalDir.exists()) {
            val match = internalDir.listFiles()?.firstOrNull { it.name.startsWith("${surah.code3}_") && it.length() > 1024L }
            if (match != null) return match
        }

        return null
    }

    fun refreshAllDownloadedFiles() {
        managerScope.launch {
            val foundKeys = mutableSetOf<String>()
            QuranCatalog.IMAMS.forEach { imam ->
                QuranCatalog.SURAHS.forEach { surah ->
                    val f = resolveLocalSurahFile(imam, surah)
                    if (f != null && f.exists() && f.length() > 1024L) {
                        foundKeys.add("${imam.id}_${surah.number}")
                    }
                }
            }
            _state.value = _state.value.copy(
                downloadedSurahKeys = foundKeys
            )
        }
    }

    fun refreshExistingDownloadedFilesForImam(imam: QuranImam = _state.value.selectedImam) {
        refreshAllDownloadedFiles()
    }

    fun getSavedQuranAudioItems(filterImamId: String? = _state.value.savedSurahFilterImamId): List<SavedQuranAudioItem> {
        val list = mutableListOf<SavedQuranAudioItem>()
        val targetImams = if (!filterImamId.isNullOrBlank()) {
            listOf(QuranCatalog.findImamById(filterImamId))
        } else {
            QuranCatalog.IMAMS
        }

        for (imam in targetImams) {
            for (surah in QuranCatalog.SURAHS) {
                val f = resolveLocalSurahFile(imam, surah)
                if (f != null && f.exists() && f.length() > 1024L) {
                    list.add(
                        SavedQuranAudioItem(
                            imamId = imam.id,
                            imamName = imam.displayName,
                            surahNumber = surah.number,
                            surahCode = surah.code3,
                            surahName = surah.nameLatin,
                            surahMeaning = surah.meaningId,
                            filePath = f.absolutePath,
                            fileSizeBytes = f.length()
                        )
                    )
                }
            }
        }
        return list
    }

    fun addSavedAudioItemToPlaylist(
        item: SavedQuranAudioItem,
        onFeedback: (String) -> Unit = {}
    ) {
        managerScope.launch {
            val file = File(item.filePath)
            if (!file.exists()) {
                val msg = "⚠️ File ${item.surahName} (${item.imamName}) tidak ditemukan di memori HP."
                withContext(Dispatchers.Main) { onFeedback(msg) }
                return@launch
            }
            val repo = VcamRepository.getInstance(context)
            val ifsMgr = IfsManager.getInstance(context)
            repo.addAudios(listOf(Uri.fromFile(file)))
            applyAudioRoleForNewlyAddedSurah(
                repo = repo,
                ifsMgr = ifsMgr,
                fileName = file.name,
                role = _state.value.selectedRole,
                queueIndex = 0
            )
            val msg = "✅ QS. ${item.surahCode} ${item.surahName} (${item.imamName}) masuk ke Playlist Audio!"
            _state.value = _state.value.copy(statusMessage = msg)
            withContext(Dispatchers.Main) { onFeedback(msg) }
        }
    }

    fun addAllSavedItemsToPlaylist(
        items: List<SavedQuranAudioItem>,
        onFeedback: (String) -> Unit = {}
    ) {
        managerScope.launch {
            if (items.isEmpty()) {
                val msg = "⚠️ Tidak ada surat Al-Quran tersimpan untuk dimasukkan ke playlist."
                withContext(Dispatchers.Main) { onFeedback(msg) }
                return@launch
            }
            val repo = VcamRepository.getInstance(context)
            val ifsMgr = IfsManager.getInstance(context)
            val uris = items.map { Uri.fromFile(File(it.filePath)) }
            repo.addAudios(uris)
            items.forEachIndexed { idx, it ->
                applyAudioRoleForNewlyAddedSurah(
                    repo = repo,
                    ifsMgr = ifsMgr,
                    fileName = File(it.filePath).name,
                    role = _state.value.selectedRole,
                    queueIndex = idx
                )
            }
            val msg = "✅ Berhasil menambahkan ${items.size} surat Al-Quran tersimpan ke Playlist Audio!"
            _state.value = _state.value.copy(statusMessage = msg)
            withContext(Dispatchers.Main) { onFeedback(msg) }
        }
    }

    /**
     * Langsung memasukkan 1 surat yang sudah tersimpan di memori HP ke Playlist Audio tanpa download ulang.
     */
    fun addSingleDownloadedSurahToPlaylist(
        surahNumber: Int,
        onFeedback: (String) -> Unit = {}
    ) {
        managerScope.launch {
            val snap = _state.value
            val imam = snap.selectedImam
            val role = snap.selectedRole
            val surah = QuranCatalog.findSurahByNumber(surahNumber) ?: return@launch
            val existingFile = resolveLocalSurahFile(imam, surah)
            if (existingFile == null || !existingFile.exists()) {
                val msg = "⚠️ QS. ${surah.code3} ${surah.nameLatin} belum terunduh."
                _state.value = _state.value.copy(statusMessage = msg)
                withContext(Dispatchers.Main) { onFeedback(msg) }
                return@launch
            }

            val repo = VcamRepository.getInstance(context)
            val ifsMgr = IfsManager.getInstance(context)
            repo.addAudios(listOf(Uri.fromFile(existingFile)))
            applyAudioRoleForNewlyAddedSurah(
                repo = repo,
                ifsMgr = ifsMgr,
                fileName = existingFile.name,
                role = role,
                queueIndex = 0
            )
            val msg = "✅ QS. ${surah.code3} ${surah.nameLatin} ditambahkan ke Playlist (${role.shortBadge})."
            _state.value = _state.value.copy(statusMessage = msg)
            withContext(Dispatchers.Main) { onFeedback(msg) }
        }
    }

    /**
     * Langsung memasukkan semua surat yang sudah tersimpan (atau yang dicentang & sudah tersimpan) ke Playlist Audio.
     */
    fun addAllDownloadedSurahsToPlaylist(
        onlySelected: Boolean = false,
        onFeedback: (String) -> Unit = {}
    ) {
        managerScope.launch {
            val snap = _state.value
            val imam = snap.selectedImam
            val role = snap.selectedRole
            val candidateNumbers = if (onlySelected && snap.selectedSurahNumbers.isNotEmpty()) {
                snap.selectedSurahNumbers.toList().sorted()
            } else {
                QuranCatalog.SURAHS.map { it.number }
            }

            val filesToAdd = mutableListOf<Pair<QuranSurah, File>>()
            for (num in candidateNumbers) {
                val surah = QuranCatalog.findSurahByNumber(num) ?: continue
                val f = resolveLocalSurahFile(imam, surah)
                if (f != null && f.exists() && f.length() > 1024L) {
                    filesToAdd.add(surah to f)
                }
            }

            if (filesToAdd.isEmpty()) {
                val msg = "⚠️ Belum ada surat tersimpan untuk ${imam.displayName}."
                _state.value = _state.value.copy(statusMessage = msg)
                withContext(Dispatchers.Main) { onFeedback(msg) }
                return@launch
            }

            val repo = VcamRepository.getInstance(context)
            val ifsMgr = IfsManager.getInstance(context)
            repo.addAudios(filesToAdd.map { Uri.fromFile(it.second) })
            filesToAdd.forEachIndexed { idx, (_, mp3File) ->
                applyAudioRoleForNewlyAddedSurah(
                    repo = repo,
                    ifsMgr = ifsMgr,
                    fileName = mp3File.name,
                    role = role,
                    queueIndex = idx
                )
            }

            val msg = "✅ ${filesToAdd.size} surat tersimpan (${imam.displayName}) masuk ke Playlist (${role.shortBadge})!"
            _state.value = _state.value.copy(statusMessage = msg)
            withContext(Dispatchers.Main) { onFeedback(msg) }
        }
    }

    private fun downloadOrReuseSurahFile(
        imam: QuranImam,
        surah: QuranSurah,
        onProgress: (Int) -> Unit
    ): Result<File> {
        val existing = resolveLocalSurahFile(imam, surah)
        if (existing != null && existing.exists() && existing.length() > 1024L) {
            onProgress(100)
            registerInMediaStoreAndScanner(existing, imam)
            return Result.success(existing)
        }

        val folder = getPublicQuranFolder(imam)
        val fileName = surah.buildFileName(imam.id)
        val targetFile = File(folder, fileName)
        val tempFile = File(folder, "$fileName.part")

        val primaryUrl = imam.buildSurahAudioUrl(surah.number, useFallback = false)
        val firstTry = downloadUrlToFile(primaryUrl, tempFile, onProgress)
        if (firstTry.isSuccess) {
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
            registerInMediaStoreAndScanner(targetFile, imam)
            return Result.success(targetFile)
        }

        // Automatic fallback mirror
        val fallbackUrl = imam.buildSurahAudioUrl(surah.number, useFallback = true)
        val secondTry = downloadUrlToFile(fallbackUrl, tempFile, onProgress)
        return if (secondTry.isSuccess) {
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
            registerInMediaStoreAndScanner(targetFile, imam)
            Result.success(targetFile)
        } else {
            try { tempFile.delete() } catch (_: Throwable) {}
            Result.failure(secondTry.exceptionOrNull() ?: Exception("Server tidak merespons"))
        }
    }

    private fun downloadUrlToFile(
        urlStr: String,
        destFile: File,
        onProgress: (Int) -> Unit
    ): Result<File> {
        var conn: HttpURLConnection? = null
        return try {
            destFile.parentFile?.mkdirs()
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "TajasbQuranDownloader/2.0")
            }
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                return Result.failure(IllegalStateException("HTTP $code"))
            }

            val totalLen = conn.contentLengthLong
            var downloaded = 0L
            BufferedInputStream(conn.inputStream, 32 * 1024).use { inp ->
                FileOutputStream(destFile).use { out ->
                    val buf = ByteArray(16 * 1024)
                    var read: Int
                    while (inp.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        downloaded += read
                        if (totalLen > 0L) {
                            val pct = ((downloaded * 100L) / totalLen).toInt().coerceIn(1, 99)
                            onProgress(pct)
                        }
                    }
                    out.flush()
                }
            }

            if (destFile.length() <= 1024L) {
                Result.failure(IllegalStateException("Ukuran file tidak valid"))
            } else {
                onProgress(100)
                Result.success(destFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }

    private fun registerInMediaStoreAndScanner(file: File, imam: QuranImam) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("audio/mpeg"),
                null
            )
        } catch (_: Throwable) {}

        // Also register in MediaStore on Android 10+ if the file is inside internal fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && file.absolutePath.startsWith(context.filesDir.absolutePath)) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Quran_${imam.id}")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(file).use { inp -> inp.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } catch (_: Throwable) {}
        }
    }
}
