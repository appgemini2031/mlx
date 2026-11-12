package com.tajasb.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.tajasb.infinity.IfsManager
import com.tajasb.model.AntiHashIntensity
import com.tajasb.model.AudioAntiHashIntensity
import com.tajasb.model.AudioItem
import com.tajasb.model.AutoNextMode
import com.tajasb.model.BridgeCyclePhase
import com.tajasb.model.HookEngineType
import com.tajasb.model.ResolutionPreset
import com.tajasb.model.ScaleMode
import com.tajasb.model.VideoItem
import com.tajasb.model.VideoSwitchMode
import com.tajasb.provider.VcamContentProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Opsi ceklis item yang diikutsertakan saat melakukan Backup Konsep, Ekspor .ZIP, maupun Restore.
 */
data class BackupChecklistOptions(
    val includeVideos: Boolean = true,          // 🎬 File Fisik Video MP4, Playlist & Mode Putar (Master/Spill/Bridge/IFS)
    val includeAudios: Boolean = true,          // 🎵 File Fisik Musik MP3 & Playlist Audio
    val includeAutoTapChoreo: Boolean = true,   // 👆 Koreografi Auto-Tap (Urut/Acak), Titik Mark & Teks Auto-Ketik
    val includeOverlays: Boolean = true,        // 🏷️ Stiker, Watermark, Notif Jualan & Doa Komentar Live
    val includeCameraVisual: Boolean = true,    // 🎨 Pengaturan Kamera, Visual, Rotasi, Anti-Hash DNA & Aplikasi Target
    val includeFloatingLayout: Boolean = true   // 🪟 Ukuran, Transparansi & Posisi Panel Floating
) {
    val anySelected: Boolean
        get() = includeVideos || includeAudios || includeAutoTapChoreo || includeOverlays || includeCameraVisual || includeFloatingLayout

    val selectedCount: Int
        get() = listOf(includeVideos, includeAudios, includeAutoTapChoreo, includeOverlays, includeCameraVisual, includeFloatingLayout).count { it }
}

/**
 * Representasi 1 Paket Konsep Lengkap (Video, MP3, Mode IFS/Spill/Urut, Stiker, Watermark, Notif, Komen, Auto-Tap, Kamera & Sistem).
 */
data class ConceptProfile(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val videoCount: Int,
    val audioCount: Int,
    val modeSummary: String,
    val overlaySummary: String,
    val snapshotJson: String
)

/**
 * Hasil pembersihan file duplikat & sampah media di Vault internal.
 */
data class VaultCleanupReport(
    val mergedDuplicateCount: Int,
    val deletedOrphanCount: Int,
    val freedBytes: Long
)

/**
 * Informasi status penyimpanan Folder Backup Lokal Custom (Custom Local Vault).
 */
data class CustomVaultStorageStats(
    val folderPath: String,
    val isWritable: Boolean,
    val mediaFileCount: Int,
    val totalSizeBytes: Long,
    val availableStorageBytes: Long = 0L,
    val hasAutoCheckpoint: Boolean,
    val lastCheckpointTimeMs: Long
)

/**
 * Laporan hasil impor paket lengkap (.zip) dari Google Drive / File Manager.
 */
data class ZipImportReport(
    val restoredConceptCount: Int,
    val restoredMediaFileCount: Int,
    val activeStateRestored: Boolean,
    val summaryMessage: String
)

/**
 * Laporan hasil pemeriksaan & pemulihan otomatis saat aplikasi baru dibuka (Anti-Restart / Anti-Reset).
 */
data class AutoRestoreStartupResult(
    val restoredFromVault: Boolean,
    val healedMediaCount: Int,
    val restoredConceptsCount: Int,
    val message: String?
)

/**
 * ConceptBackupManager:
 * Mengelola penyimpanan multi-konsep (Konsep 1, Konsep 2, dst.), pemulihan penuh 1-klik (< 0,2 detik),
 * Custom Local Backup Folder (di memori perangkat yang bisa dipilih pengguna),
 * Real-Time Auto-Save & Auto-Restore saat aplikasi dibuka (Anti-Restart / Anti-Reset),
 * serta Ekspor/Impor Paket Lengkap 1-File (.zip) termasuk seluruh file fisik Video MP4 + Audio MP3 + Stiker/Pengaturan untuk Google Drive.
 */
class ConceptBackupManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "ConceptBackupManager"
        private const val PREFS_NAME = "tajasb_concept_vault_v1"
        private const val KEY_CONCEPTS_JSON = "key_saved_concepts_json"
        private const val KEY_ACTIVE_CONCEPT_ID = "key_active_concept_id"
        private const val KEY_CUSTOM_VAULT_PATH = "key_custom_vault_folder_path"
        private const val KEY_AUTO_BACKUP_ENABLED = "key_auto_backup_realtime_enabled"
        private const val KEY_AUTO_CHECKPOINT_JSON = "key_auto_checkpoint_snapshot_json"
        private const val KEY_LAST_CHECKPOINT_TIME = "key_last_auto_checkpoint_time_ms"

        private const val VAULT_DB_FILE_NAME = "concepts_vault_db.json"
        private const val AUTO_CHECKPOINT_FILE_NAME = "auto_checkpoint_state.json"
        private const val ALL_PREFS_MASTER_FILE_NAME = "all_preferences_master.json"
        private const val MEDIA_VAULT_SUBDIR = "media_vault"
        private const val AUTO_CHECKPOINT_ID = "AUTO_CHECKPOINT"

        val ALLOWED_BACKUP_PREF_NAMES = setOf(
            "vcam_settings",
            "ifs_settings_v2",
            "custom_stiker_prefs",
            "custom_watermark_prefs",
            "buyer_notif_prefs",
            "auto_time_capsule_prefs",
            "live_comment_prefs",
            "vcam_screen_anchors",
            "tajasb_autotap_prefs",
            "vcam_floating_prefs",
            "tajasb_telegram_screen_prefs"
        )

        /**
         * Memvalidasi secara ketat (Strict Whitelist) apakah sebuah nama file SharedPreferences
         * boleh diekspor/diimpor oleh sistem Backup Konsep.
         * Menolak 100% semua brankas lisensi, waktu, TOTP, Tripay, remote telemetry, maupun karakter path traversal.
         */
        fun isBackupPreferenceAllowed(prefName: String?): Boolean {
            if (prefName.isNullOrBlank()) return false
            val trimmed = prefName.trim()
            if (trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\")) return false
            return ALLOWED_BACKUP_PREF_NAMES.contains(trimmed)
        }

        @Volatile
        private var INSTANCE: ConceptBackupManager? = null

        fun getInstance(context: Context): ConceptBackupManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConceptBackupManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * Membersihkan awalan UUID/Vault lama pada nama file agar nama file selalu bersih, rapi, dan mudah dicari.
         * Contoh: "video_a1b2c3d4_vault_vid_9988776655_Demo_Skincare.mp4" -> "Demo_Skincare.mp4"
         */
        fun normalizeCleanFileName(rawName: String, defaultExt: String = ".mp4"): String {
            var name = rawName.trim()
            val prefixRegex = Regex("""^(?:vault_(?:vid|aud)_[0-9a-fA-F]{8,16}_|video_[0-9a-fA-F\-]{6,12}_|audio_[0-9a-fA-F\-]{6,12}_)+""")
            name = name.replace(prefixRegex, "")
            name = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
            if (name.isBlank()) {
                name = "media_${System.currentTimeMillis()}$defaultExt"
            }
            return name
        }

        /**
         * Menghitung hash SHA-256 cepat (12 karakter hex) dari sebuah File fisik.
         * Untuk file besar (> 4 MB), menggunakan kombinasi ukuran file + blok 256KB awal, tengah, dan akhir
         * agar proses pengecekan berjalan dalam hitungan milidetik tanpa membebani CPU/IO.
         */
        fun computeFastFileHash(file: File): String {
            if (!file.exists() || file.length() <= 0L) return ""
            return try {
                val md = MessageDigest.getInstance("SHA-256")
                val len = file.length()
                md.update(len.toString().toByteArray(Charsets.UTF_8))

                FileInputStream(file).use { fis ->
                    val ch = fis.channel
                    val buf = java.nio.ByteBuffer.allocate(256 * 1024) // 256 KB sample blocks
                    // 1. Blok Awal
                    ch.position(0L)
                    buf.clear()
                    val r1 = ch.read(buf)
                    if (r1 > 0) md.update(buf.array(), 0, r1)

                    // 2. Blok Tengah (jika file > 512 KB)
                    if (len > 512 * 1024L) {
                        ch.position(len / 2)
                        buf.clear()
                        val r2 = ch.read(buf)
                        if (r2 > 0) md.update(buf.array(), 0, r2)
                    }

                    // 3. Blok Akhir (jika file > 1 MB)
                    if (len > 1024 * 1024L) {
                        ch.position((len - 256 * 1024L).coerceAtLeast(0L))
                        buf.clear()
                        val r3 = ch.read(buf)
                        if (r3 > 0) md.update(buf.array(), 0, r3)
                    }
                }
                md.digest().joinToString("") { "%02x".format(it) }.take(12)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal menghitung fast hash untuk ${file.name}: ${e.message}")
                ""
            }
        }
    }

    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ioMutex = Mutex()
    private val isRestoringState = AtomicBoolean(false)
    private val startupCheckDone = AtomicBoolean(false)
    private val watcherStarted = AtomicBoolean(false)
    private var autoSaveDebounceJob: Job? = null

    // Listener untuk mendeteksi perubahan di SharedPreferences modul-modul overlay, Auto-Tap & IFS secara real-time
    private val overlayPrefsLocations = listOf(
        "ifs_settings_v2",
        "custom_stiker_prefs",
        "custom_watermark_prefs",
        "buyer_notif_prefs",
        "live_comment_prefs",
        "vcam_screen_anchors",
        "tajasb_autotap_prefs",
        "vcam_floating_prefs",
        "tajasb_telegram_screen_prefs"
    )
    private val overlayPrefsInstances = mutableListOf<SharedPreferences>()
    private val sharedPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        scheduleDebouncedAutoCheckpoint()
    }

    private val _concepts = MutableStateFlow(loadConceptsFromPrefs())
    val concepts: StateFlow<List<ConceptProfile>> = _concepts.asStateFlow()

    private val _activeConceptId = MutableStateFlow(prefs.getString(KEY_ACTIVE_CONCEPT_ID, null))
    val activeConceptId: StateFlow<String?> = _activeConceptId.asStateFlow()

    private val _customVaultPath = MutableStateFlow(loadInitialCustomVaultPath())
    val customVaultPath: StateFlow<String> = _customVaultPath.asStateFlow()

    private val _isAutoBackupEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, true))
    val isAutoBackupEnabled: StateFlow<Boolean> = _isAutoBackupEnabled.asStateFlow()

    private val _lastAutoCheckpointTimeMs = MutableStateFlow(prefs.getLong(KEY_LAST_CHECKPOINT_TIME, 0L))
    val lastAutoCheckpointTimeMs: StateFlow<Long> = _lastAutoCheckpointTimeMs.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _busyProgressText = MutableStateFlow<String?>(null)
    val busyProgressText: StateFlow<String?> = _busyProgressText.asStateFlow()

    fun getInternalHiddenVaultDir(): File {
        val dir = File(appContext.filesDir, "LiveHost_Backup_Vault")
        if (!dir.exists()) dir.mkdirs()
        try {
            val noMedia = File(dir, ".nomedia")
            if (!noMedia.exists()) noMedia.createNewFile()
        } catch (_: Exception) {}
        return dir
    }

    private fun loadInitialCustomVaultPath(): String {
        val internalDefault = getInternalHiddenVaultDir()
        val saved = prefs.getString(KEY_CUSTOM_VAULT_PATH, null)?.trim()
        if (!saved.isNullOrEmpty()) {
            val savedDir = File(saved)
            if (MediaFileStorageHelper.isPublicGalleryScannedDirectory(savedDir)) {
                // Migrasi otomatis dari folder publik (Download/DCIM/Movies) ke folder internal tersembunyi
                // agar file cadangan tidak pernah terdeteksi sebagai video duplikat di Galeri HP.
                try {
                    if (savedDir.exists() && savedDir.isDirectory) {
                        val dbFile = File(savedDir, VAULT_DB_FILE_NAME)
                        val targetDb = File(internalDefault, VAULT_DB_FILE_NAME)
                        if (dbFile.exists() && (!targetDb.exists() || dbFile.length() >= targetDb.length())) {
                            dbFile.copyTo(targetDb, overwrite = true)
                        }
                        val cpFile = File(savedDir, AUTO_CHECKPOINT_FILE_NAME)
                        val targetCp = File(internalDefault, AUTO_CHECKPOINT_FILE_NAME)
                        if (cpFile.exists() && (!targetCp.exists() || cpFile.lastModified() >= targetCp.lastModified())) {
                            cpFile.copyTo(targetCp, overwrite = true)
                        }
                        val oldMediaDir = File(savedDir, MEDIA_VAULT_SUBDIR)
                        val newMediaDir = File(internalDefault, MEDIA_VAULT_SUBDIR)
                        if (!newMediaDir.exists()) newMediaDir.mkdirs()
                        File(newMediaDir, ".nomedia").apply { if (!exists()) createNewFile() }
                        oldMediaDir.listFiles()?.forEach { f ->
                            if (f.isFile && f.length() > 0L) {
                                val dest = File(newMediaDir, f.name)
                                if (!dest.exists() || dest.length() != f.length()) {
                                    try { f.copyTo(dest, overwrite = true) } catch (_: Exception) {}
                                }
                                try { f.delete() } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {}
                prefs.edit().putString(KEY_CUSTOM_VAULT_PATH, internalDefault.absolutePath).apply()
                return internalDefault.absolutePath
            }
            return saved
        }
        prefs.edit().putString(KEY_CUSTOM_VAULT_PATH, internalDefault.absolutePath).apply()
        return internalDefault.absolutePath
    }

    /**
     * Daftar pilihan lokasi folder backup lokal populer agar pengguna bisa memilih dalam 1 ketukan.
     * Prioritas utama adalah folder internal tersembunyi agar video tidak pernah duplikat di Galeri HP.
     */
    fun getPresetVaultDirectories(): List<Pair<String, String>> {
        val internalHidden = getInternalHiddenVaultDir().absolutePath
        val appExternal = (appContext.getExternalFilesDir("LiveHost_Backup_Vault") ?: getInternalHiddenVaultDir()).absolutePath
        val docs = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "LiveHost_Backup_Vault").absolutePath
        return listOf(
            "🔒 Internal Tersembunyi (Anti-Duplikat Galeri)" to internalHidden,
            "📦 Sandbox Aplikasi (Bebas Galeri)" to appExternal,
            "📄 Folder Documents" to docs
        )
    }

    /**
     * Mengubah URI pohon direktori (dari OpenDocumentTree SAF) menjadi path lokal fisik `/storage/emulated/0/...` jika memungkinkan.
     */
    fun resolveTreeUriToLocalPath(treeUri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri) ?: return null
            val parts = docId.split(":")
            if (parts.isNotEmpty()) {
                val type = parts[0]
                val rel = if (parts.size > 1) parts[1].trim('/') else ""
                if (type.equals("primary", ignoreCase = true)) {
                    val base = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
                    val chosen = if (rel.isEmpty()) base else "$base/$rel"
                    return if (chosen.endsWith("LiveHost_Backup_Vault", ignoreCase = true)) {
                        chosen
                    } else {
                        "$chosen/LiveHost_Backup_Vault"
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Gagal konversi Tree URI ke path lokal: ${e.message}")
            null
        }
    }

    /**
     * Mengembalikan direktori Custom Local Vault yang siap ditulis.
     * Selalu menyertakan `.nomedia` agar tidak dipindai oleh Galeri HP.
     */
    fun getResolvedCustomVaultDir(): File {
        val preferred = File(_customVaultPath.value)
        try {
            if (!preferred.exists()) preferred.mkdirs()
            if (preferred.exists() && preferred.canWrite()) {
                try {
                    val noMedia = File(preferred, ".nomedia")
                    if (!noMedia.exists()) noMedia.createNewFile()
                } catch (_: Exception) {}
                return preferred
            }
        } catch (_: Exception) {}

        val extFallback = appContext.getExternalFilesDir("LiveHost_Backup_Vault")
        if (extFallback != null) {
            if (!extFallback.exists()) extFallback.mkdirs()
            try {
                val noMedia = File(extFallback, ".nomedia")
                if (!noMedia.exists()) noMedia.createNewFile()
            } catch (_: Exception) {}
            if (extFallback.exists() && extFallback.canWrite()) return extFallback
        }

        return getInternalHiddenVaultDir()
    }

    fun getCustomMediaVaultDir(): File {
        val dir = File(getResolvedCustomVaultDir(), MEDIA_VAULT_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        try {
            val noMedia = File(dir, ".nomedia")
            if (!noMedia.exists()) noMedia.createNewFile()
        } catch (_: Exception) {}
        return dir
    }

    /**
     * Mengambil statistik penyimpanan Folder Backup Lokal Custom saat ini.
     */
    fun getVaultStorageStats(): CustomVaultStorageStats {
        val vaultDir = getResolvedCustomVaultDir()
        val mediaDir = File(vaultDir, MEDIA_VAULT_SUBDIR)
        val mediaFiles = (mediaDir.listFiles() ?: emptyArray()).filter { it.isFile && it.length() > 0L }
        val totalMediaBytes = mediaFiles.sumOf { it.length() }
        val dbBytes = File(vaultDir, VAULT_DB_FILE_NAME).takeIf { it.exists() }?.length() ?: 0L
        val cpFile = File(vaultDir, AUTO_CHECKPOINT_FILE_NAME)
        val cpBytes = if (cpFile.exists()) cpFile.length() else 0L
        val hasCp = cpFile.exists() || !prefs.getString(KEY_AUTO_CHECKPOINT_JSON, null).isNullOrBlank()
        val lastCpTime = if (cpFile.exists()) maxOf(cpFile.lastModified(), _lastAutoCheckpointTimeMs.value) else _lastAutoCheckpointTimeMs.value

        val usableBytes = try { vaultDir.usableSpace } catch (_: Exception) { 0L }

        return CustomVaultStorageStats(
            folderPath = _customVaultPath.value,
            isWritable = vaultDir.exists() && vaultDir.canWrite(),
            mediaFileCount = mediaFiles.size,
            totalSizeBytes = totalMediaBytes + dbBytes + cpBytes,
            availableStorageBytes = usableBytes,
            hasAutoCheckpoint = hasCp,
            lastCheckpointTimeMs = lastCpTime
        )
    }

    /**
     * Mengubah lokasi Folder Backup Lokal Custom dan otomatis memindahkan database konsep + seluruh file media
     * ke lokasi folder baru agar pengguna tidak kehilangan satu pun file backup.
     */
    suspend fun setCustomVaultDirectory(
        newPath: String,
        migrateExistingFiles: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanPath = newPath.trim()
        if (cleanPath.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Path folder tidak boleh kosong"))
        }

        ioMutex.withLock {
            try {
                _isBusy.value = true
                _busyProgressText.value = "Menyiapkan folder backup baru..."

                val oldVaultDir = getResolvedCustomVaultDir()
                val newVaultDir = File(cleanPath)
                if (!newVaultDir.exists()) {
                    newVaultDir.mkdirs()
                }
                val newMediaDir = File(newVaultDir, MEDIA_VAULT_SUBDIR)
                if (!newMediaDir.exists()) {
                    newMediaDir.mkdirs()
                }

                // Uji apakah folder baru dapat ditulis
                val probeFile = File(newVaultDir, ".vault_write_probe")
                val canWriteNew = try {
                    probeFile.writeText("ok")
                    probeFile.delete()
                    true
                } catch (_: Exception) {
                    false
                }

                if (!canWriteNew) {
                    return@withLock Result.failure(
                        IllegalStateException("Folder '$cleanPath' tidak dapat ditulis. Pastikan Izin Penyimpanan sudah aktif.")
                    )
                }

                if (migrateExistingFiles && oldVaultDir.absolutePath != newVaultDir.absolutePath && oldVaultDir.exists()) {
                    // 1. Salin concepts_vault_db.json & auto_checkpoint_state.json
                    val oldDb = File(oldVaultDir, VAULT_DB_FILE_NAME)
                    val newDb = File(newVaultDir, VAULT_DB_FILE_NAME)
                    if (oldDb.exists() && oldDb.length() > 0L) {
                        oldDb.copyTo(newDb, overwrite = true)
                    }

                    val oldCp = File(oldVaultDir, AUTO_CHECKPOINT_FILE_NAME)
                    val newCp = File(newVaultDir, AUTO_CHECKPOINT_FILE_NAME)
                    if (oldCp.exists() && oldCp.length() > 0L) {
                        oldCp.copyTo(newCp, overwrite = true)
                    }

                    // 2. Salin seluruh file fisik video/audio di media_vault/
                    val oldMediaDir = File(oldVaultDir, MEDIA_VAULT_SUBDIR)
                    val oldMediaFiles = (oldMediaDir.listFiles() ?: emptyArray()).filter { it.isFile && it.length() > 0L }
                    oldMediaFiles.forEachIndexed { idx, file ->
                        _busyProgressText.value = "Memindahkan media backup (${idx + 1}/${oldMediaFiles.size}): ${file.name}"
                        val target = File(newMediaDir, file.name)
                        if (!target.exists() || target.length() != file.length()) {
                            file.copyTo(target, overwrite = true)
                        }
                    }
                }

                _customVaultPath.value = newVaultDir.absolutePath
                prefs.edit().putString(KEY_CUSTOM_VAULT_PATH, newVaultDir.absolutePath).apply()

                // Pastikan seluruh media aktif saat ini juga tersinkron ke folder baru
                mirrorActiveAndConceptMediaToCustomVault()
                writeConceptsDbToCustomVault(_concepts.value, _activeConceptId.value)
                saveAutoCheckpointInternal()

                Result.success(newVaultDir.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal mengubah folder backup: ${e.message}", e)
                Result.failure(e)
            } finally {
                _busyProgressText.value = null
                _isBusy.value = false
            }
        }
    }

    /**
     * Mengaktifkan atau menonaktifkan fitur Auto-Save & Auto-Restore Realtime.
     */
    fun setAutoBackupEnabled(enabled: Boolean) {
        _isAutoBackupEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
        if (enabled) {
            startRealtimeAutoBackupWatcher()
            triggerImmediateAutoCheckpoint()
        }
    }

    /**
     * Menjalankan pemantau otomatis (Real-Time Watcher) di background yang mendeteksi setiap perubahan
     * pada playlist video, playlist musik MP3, mode IFS/Spill/Urut, stiker, watermark, notifikasi, komentar,
     * auto-tap, dan pengaturan kamera, lalu menyimpannya secara otomatis (debounced 1,5 detik).
     */
    fun startRealtimeAutoBackupWatcher() {
        if (!watcherStarted.compareAndSet(false, true)) return

        try {
            // Pasang listener pada SharedPreferences seluruh modul overlay & IFS
            for (prefName in overlayPrefsLocations) {
                val sp = appContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                overlayPrefsInstances.add(sp)
                sp.registerOnSharedPreferenceChangeListener(sharedPrefListener)
            }

            // Pantau perubahan VcamConfig (Playlist Video, Audio, Mode Siklus, Kamera, Anti-Hash, Target App)
            backgroundScope.launch {
                val repo = VcamRepository.getInstance(appContext)
                repo.config.collectLatest {
                    if (!isRestoringState.get() && _isAutoBackupEnabled.value) {
                        scheduleDebouncedAutoCheckpoint()
                    }
                }
            }

            // Pantau perubahan Infinity System (IFS SV1/SV2, VTM/VT1..n, AX/SR/SC)
            backgroundScope.launch {
                val ifsMgr = IfsManager.getInstance(appContext)
                ifsMgr.vtPackages.collectLatest {
                    if (!isRestoringState.get() && _isAutoBackupEnabled.value) {
                        scheduleDebouncedAutoCheckpoint()
                    }
                }
            }

            backgroundScope.launch {
                val ifsMgr = IfsManager.getInstance(appContext)
                ifsMgr.vtmPackage.collectLatest {
                    if (!isRestoringState.get() && _isAutoBackupEnabled.value) {
                        scheduleDebouncedAutoCheckpoint()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menginisialisasi watcher auto-backup: ${e.message}")
        }
    }

    private fun scheduleDebouncedAutoCheckpoint() {
        if (!_isAutoBackupEnabled.value || isRestoringState.get()) return
        autoSaveDebounceJob?.cancel()
        autoSaveDebounceJob = backgroundScope.launch {
            delay(1500L)
            if (!isRestoringState.get() && _isAutoBackupEnabled.value) {
                saveAutoCheckpointInternal()
            }
        }
    }

    /**
     * Memicu penyimpanan Auto-Checkpoint secara langsung di background.
     */
    fun triggerImmediateAutoCheckpoint() {
        backgroundScope.launch {
            if (!isRestoringState.get()) {
                saveAutoCheckpointInternal()
            }
        }
    }

    fun scheduleAutoCheckpoint() {
        triggerImmediateAutoCheckpoint()
    }

    /**
     * Dipanggil saat pengguna secara eksplisit menghapus video/audio dari playlist aktif.
     * Menghapus salinan fisik di `media_vault/` (kecuali dikunci oleh Konsep Backup yang disimpan pengguna)
     * dan langsung memperbarui `auto_checkpoint_state.json` tanpa debounce agar file yang sudah dihapus
     * tidak pernah hidup kembali saat aplikasi dibuka ulang.
     */
    suspend fun onMediaRemovedFromActivePlaylist(removedPaths: List<String>) = withContext(Dispatchers.IO) {
        try {
            autoSaveDebounceJob?.cancel()
            val protectedPaths = getAllProtectedMediaPaths()
            val protectedNames = getAllProtectedMediaFileNames()
            val customMediaDir = getCustomMediaVaultDir()
            val internalVaultDir = VcamRepository.getInstance(appContext).getInternalCamera1Directory()

            for (rawPath in removedPaths) {
                if (rawPath.isBlank()) continue
                if (isMediaPathProtectedByAnyConcept(rawPath)) continue

                val f = File(rawPath)
                val fileName = f.name
                try { if (f.exists()) f.delete() } catch (_: Exception) {}

                if (fileName.isNotBlank() && !protectedNames.contains(fileName)) {
                    try {
                        val intCopy = File(internalVaultDir, fileName)
                        if (intCopy.exists() && !protectedPaths.contains(intCopy.absolutePath)) {
                            intCopy.delete()
                        }
                    } catch (_: Exception) {}

                    try {
                        val vaultCopy = File(customMediaDir, fileName)
                        if (vaultCopy.exists() && !protectedPaths.contains(vaultCopy.absolutePath)) {
                            vaultCopy.delete()
                        }
                    } catch (_: Exception) {}
                }
            }

            // Bersihkan juga file yatim di media_vault/ yang tidak ada di playlist aktif maupun Konsep Backup
            val repo = VcamRepository.getInstance(appContext)
            val cfg = repo.config.value
            val activeFileNames = (cfg.playlist.map { File(it.path).name } + cfg.audioPlaylist.map { File(it.path).name }).toSet()
            val validNames = activeFileNames + protectedNames
            if (customMediaDir.exists()) {
                customMediaDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name != ".nomedia" && !validNames.contains(file.name)) {
                        val abs = file.absolutePath
                        val canon = try { file.canonicalPath } catch (_: Exception) { abs }
                        if (!protectedPaths.contains(abs) && !protectedPaths.contains(canon)) {
                            try { file.delete() } catch (_: Exception) {}
                        }
                    }
                }
            }

            saveAutoCheckpointInternal(allowEmptyOverwrite = true)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membersihkan media dari vault saat penghapusan: ${e.message}")
        }
    }

    private suspend fun saveAutoCheckpointInternal(allowEmptyOverwrite: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val repo = VcamRepository.getInstance(appContext)
            val cfg = repo.config.value
            // Jangan menimpa checkpoint yang berisi data dengan state kosong jika baru saja ter-reset saat startup
            val existingCpJson = readAutoCheckpointJsonFromDiskOrPrefs()
            if (!allowEmptyOverwrite && cfg.playlist.isEmpty() && cfg.audioPlaylist.isEmpty() && !existingCpJson.isNullOrBlank()) {
                val existingHasMedia = try {
                    val root = JSONObject(existingCpJson)
                    val vArr = JSONArray(root.optString("videoPlaylistJson", "[]"))
                    val aArr = JSONArray(root.optString("audioPlaylistJson", "[]"))
                    vArr.length() > 0 || aArr.length() > 0
                } catch (_: Exception) {
                    false
                }
                // Jika state aktif kosong padahal ada checkpoint bermedia dan baru startup, jangan timpa dulu
                if (existingHasMedia && !startupCheckDone.get()) {
                    return@withContext
                }
            }

            // 1. Cerminkan file fisik video & MP3 aktif ke Custom Local Vault media_vault/
            mirrorPlaylistFilesToCustomMediaVault(cfg.playlist, cfg.audioPlaylist)

            // 2. Potret seluruh state saat ini
            val now = System.currentTimeMillis()
            val checkpointProfile = captureCurrentStateSnapshot(
                id = AUTO_CHECKPOINT_ID,
                name = "Sesi Terakhir (Auto-Save)",
                createdAt = now,
                updatedAt = now
            )

            // 3. Simpan ke SharedPreferences & File Fisik di Custom Local Vault
            prefs.edit()
                .putString(KEY_AUTO_CHECKPOINT_JSON, checkpointProfile.snapshotJson)
                .putLong(KEY_LAST_CHECKPOINT_TIME, now)
                .apply()
            _lastAutoCheckpointTimeMs.value = now

            val vaultDir = getResolvedCustomVaultDir()
            val cpFile = File(vaultDir, AUTO_CHECKPOINT_FILE_NAME)
            val wrapper = JSONObject().apply {
                put("schemaVersion", 1)
                put("savedAt", now)
                put("activeConceptId", _activeConceptId.value ?: "")
                put("profile", serializeConceptProfileToJson(checkpointProfile))
            }
            cpFile.writeText(wrapper.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menyimpan auto-checkpoint: ${e.message}")
        }
    }

    /**
     * Memeriksa dan memulihkan otomatis seluruh Konsep, File Fisik Video/MP3, serta Sesi Terakhir
     * ketika aplikasi pertama kali dibuka (misal setelah HP mati mendadak, restart, atau install ulang).
     */
    suspend fun checkAndAutoRestoreOnStartup(): AutoRestoreStartupResult = withContext(Dispatchers.IO) {
        if (!startupCheckDone.compareAndSet(false, true)) {
            startRealtimeAutoBackupWatcher()
            return@withContext AutoRestoreStartupResult(false, 0, 0, null)
        }

        var restoredConceptsCount = 0
        var healedMediaCount = 0
        var restoredSession = false
        var statusMsg: String? = null

        try {
            // 1. Pulihkan / gabungkan daftar Konsep dari Folder Backup Lokal Custom jika internal kosong atau kurang
            val externalConceptsPair = readConceptsDbFromCustomVault()
            if (externalConceptsPair != null) {
                val (extList, extActiveId) = externalConceptsPair
                val currentInternal = _concepts.value
                if (extList.isNotEmpty()) {
                    val internalIds = currentInternal.map { it.id }.toSet()
                    val missingFromInternal = extList.filter { !internalIds.contains(it.id) }
                    if (missingFromInternal.isNotEmpty() || currentInternal.isEmpty()) {
                        val merged = (currentInternal + missingFromInternal).sortedByDescending { it.updatedAt }
                        _concepts.value = merged
                        val nextActive = _activeConceptId.value ?: extActiveId
                        _activeConceptId.value = nextActive
                        saveConceptsToPrefs(merged, nextActive, syncToExternal = false)
                        restoredConceptsCount = missingFromInternal.size
                    }
                }
            } else if (_concepts.value.isNotEmpty()) {
                // Jika di internal ada konsep tapi di folder eksternal belum tertulis, tuliskan sekarang
                writeConceptsDbToCustomVault(_concepts.value, _activeConceptId.value)
            }

            // 2. Jika Auto-Backup & Auto-Restore aktif, periksa kesehatan file media & state aktif
            if (_isAutoBackupEnabled.value) {
                val repo = VcamRepository.getInstance(appContext)
                val internalVaultDir = repo.getInternalCamera1Directory()
                val customMediaDir = getCustomMediaVaultDir()
                val curCfg = repo.config.value

                val isInternalEmpty = curCfg.playlist.isEmpty() && curCfg.audioPlaylist.isEmpty()
                val checkpointJson = readAutoCheckpointJsonFromDiskOrPrefs()

                if (isInternalEmpty && !checkpointJson.isNullOrBlank()) {
                    // Cek apakah checkpoint memiliki media atau konfigurasi yang perlu dipulihkan
                    val cpRoot = try { JSONObject(checkpointJson) } catch (_: Exception) { null }
                    val cpVidArr = cpRoot?.optString("videoPlaylistJson", "[]")?.let { JSONArray(it) }
                    val cpAudArr = cpRoot?.optString("audioPlaylistJson", "[]")?.let { JSONArray(it) }
                    val hasSavedMedia = (cpVidArr != null && cpVidArr.length() > 0) || (cpAudArr != null && cpAudArr.length() > 0)

                    if (hasSavedMedia) {
                        val res = applySnapshotJsonInternal(
                            snapshotJson = checkpointJson,
                            profileId = _activeConceptId.value,
                            profileName = "Sesi Terakhir (Auto-Restore)"
                        )
                        if (res.isSuccess) {
                            restoredSession = true
                            val finalCfg = repo.config.value
                            healedMediaCount = finalCfg.playlist.size + finalCfg.audioPlaylist.size
                            statusMsg = "⚡ Auto-Restore: ${finalCfg.playlist.size} Video, ${finalCfg.audioPlaylist.size} MP3 & Pengaturan pulih otomatis!"
                        }
                    }
                } else if (!isInternalEmpty) {
                    // Periksa apakah ada file video atau MP3 di playlist aktif yang terhapus dari cache internal
                    var anyHealed = false
                    val healedVideos = curCfg.playlist.mapNotNull { item ->
                        if (item.id.startsWith("vs_rolling_") || item.path.contains("vs_rolling_")) {
                            if (File(item.path).exists() && File(item.path).length() > 0L) item else null
                        } else {
                            val resolved = resolveAndHealMediaFile(
                                rawPath = item.path,
                                rawName = item.name,
                                defaultExt = ".mp4",
                                internalVaultDir = internalVaultDir,
                                customMediaDir = customMediaDir
                            )
                            if (resolved != null) {
                                if (resolved.absolutePath != item.path) {
                                    anyHealed = true
                                    healedMediaCount++
                                }
                                item.copy(
                                    name = normalizeCleanFileName(item.name, ".mp4"),
                                    path = resolved.absolutePath
                                )
                            } else {
                                item
                            }
                        }
                    }

                    val healedAudios = curCfg.audioPlaylist.mapNotNull { item ->
                        val resolved = resolveAndHealMediaFile(
                            rawPath = item.path,
                            rawName = item.name,
                            defaultExt = ".mp3",
                            internalVaultDir = internalVaultDir,
                            customMediaDir = customMediaDir
                        )
                        if (resolved != null) {
                            if (resolved.absolutePath != item.path) {
                                anyHealed = true
                                healedMediaCount++
                            }
                            item.copy(
                                name = normalizeCleanFileName(item.name, ".mp3"),
                                path = resolved.absolutePath
                            )
                        } else {
                            item
                        }
                    }

                    if (anyHealed) {
                        val safeIdx = curCfg.currentVideoIndex.coerceIn(0, (healedVideos.size - 1).coerceAtLeast(0))
                        val activeV = healedVideos.getOrNull(safeIdx)
                        val safeAudIdx = curCfg.currentAudioIndex.coerceIn(0, (healedAudios.size - 1).coerceAtLeast(0))
                        val activeA = healedAudios.getOrNull(safeAudIdx)
                        repo.updateConfig {
                            it.copy(
                                playlist = healedVideos,
                                videoPath = activeV?.path ?: it.videoPath,
                                audioPlaylist = healedAudios,
                                audioPath = activeA?.path ?: it.audioPath
                            )
                        }
                        statusMsg = "♻️ Pemulihan Otomatis: $healedMediaCount file media berhasil disinkronkan dari Vault!"
                    }

                    // Pastikan virtual.mp4 di internal Camera1 selalu siap saat aplikasi dibuka pasca-restart HP
                    val finalCfg = repo.config.value
                    val activeVid = finalCfg.currentVideo
                    val virtualMp4 = File(internalVaultDir, "virtual.mp4")
                    if (activeVid != null && File(activeVid.path).exists()) {
                        if (!virtualMp4.exists() || virtualMp4.length() == 0L) {
                            repo.copyToAllLocations(File(activeVid.path), "virtual.mp4")
                            repo.writeHookConfigFiles(finalCfg)
                        }
                    }

                    // Cerminkan juga semua file aktif ke Custom Local Vault jika belum ada
                    mirrorActiveAndConceptMediaToCustomVault()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saat checkAndAutoRestoreOnStartup: ${e.message}", e)
        }

        // Aktifkan pemantau real-time untuk seluruh aktivitas berikutnya
        startRealtimeAutoBackupWatcher()

        if (statusMsg == null && restoredConceptsCount > 0) {
            statusMsg = "📂 $restoredConceptsCount Konsep Backup ditemukan & dimuat dari Folder Lokal!"
        }

        AutoRestoreStartupResult(
            restoredFromVault = restoredSession || restoredConceptsCount > 0,
            healedMediaCount = healedMediaCount,
            restoredConceptsCount = restoredConceptsCount,
            message = statusMsg
        )
    }

    /**
     * Memulihkan Sesi Terakhir (Auto-Checkpoint) secara instan dalam 1 klik.
     */
    suspend fun restoreLastAutoCheckpoint(
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): Result<ConceptProfile> = withContext(Dispatchers.IO) {
        val checkpointJson = readAutoCheckpointJsonFromDiskOrPrefs()
            ?: return@withContext Result.failure(IllegalStateException("Belum ada sesi Auto-Save yang tersimpan di Folder Backup."))

        applySnapshotJsonInternal(
            snapshotJson = checkpointJson,
            profileId = _activeConceptId.value,
            profileName = "Sesi Terakhir (Auto-Save)",
            options = options
        )
    }

    /**
     * Memindai dan menyinkronkan ulang seluruh Konsep & Media dari Folder Backup Lokal Custom secara langsung.
     */
    suspend fun syncFromCustomLocalVaultNow(): Result<String> = withContext(Dispatchers.IO) {
        try {
            _isBusy.value = true
            _busyProgressText.value = "Memindai Folder Backup Lokal..."

            var importedConcepts = 0
            val externalConceptsPair = readConceptsDbFromCustomVault()
            if (externalConceptsPair != null) {
                val (extList, extActiveId) = externalConceptsPair
                if (extList.isNotEmpty()) {
                    val byId = _concepts.value.associateBy { it.id }.toMutableMap()
                    for (c in extList) {
                        val existing = byId[c.id]
                        if (existing == null || c.updatedAt >= existing.updatedAt) {
                            byId[c.id] = c
                            importedConcepts++
                        }
                    }
                    val merged = byId.values.sortedByDescending { it.updatedAt }
                    _concepts.value = merged
                    val nextActive = extActiveId ?: _activeConceptId.value
                    _activeConceptId.value = nextActive
                    saveConceptsToPrefs(merged, nextActive, syncToExternal = true)
                }
            }

            // Jika ada auto_checkpoint_state.json di folder lokal, pulihkan juga
            val cpJson = readAutoCheckpointJsonFromDiskOrPrefs()
            var sessionRestored = false
            if (!cpJson.isNullOrBlank()) {
                val res = applySnapshotJsonInternal(
                    snapshotJson = cpJson,
                    profileId = _activeConceptId.value,
                    profileName = "Sesi Terakhir Folder Lokal"
                )
                sessionRestored = res.isSuccess
            }

            val stats = getVaultStorageStats()
            val msg = when {
                sessionRestored && importedConcepts > 0 ->
                    "✅ Pulih penuh! $importedConcepts Konsep, ${stats.mediaFileCount} file media & sesi terakhir aktif kembali."
                sessionRestored ->
                    "✅ Sesi terakhir & ${stats.mediaFileCount} file media berhasil dipulihkan dari Folder Lokal!"
                importedConcepts > 0 ->
                    "✅ $importedConcepts Konsep & ${stats.mediaFileCount} file media berhasil dimuat dari Folder Lokal!"
                else ->
                    "✅ Folder Lokal tersinkron (${_concepts.value.size} Konsep, ${stats.mediaFileCount} file media)."
            }
            Result.success(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal sinkronisasi dari folder lokal: ${e.message}", e)
            Result.failure(e)
        } finally {
            _busyProgressText.value = null
            _isBusy.value = false
        }
    }

    /**
     * Menyimpan file dari Uri ke Vault internal secara Anti-Duplikat (Zero-Duplicate Content-Addressed Storage),
     * serta otomatis mencerminkannya ke Folder Backup Lokal Custom (`media_vault/`).
     */
    fun storeOrReuseVaultMediaFile(
        vaultDir: File,
        uri: Uri,
        rawFileName: String,
        isAudio: Boolean
    ): Pair<File, String>? {
        return try {
            if (!vaultDir.exists()) vaultDir.mkdirs()
            val defaultExt = if (isAudio) ".mp3" else ".mp4"
            val cleanDisplayName = normalizeCleanFileName(rawFileName, defaultExt)

            // Tulis ke file sementara terlebih dahulu sambil menghitung ukuran
            val tempFile = File(vaultDir, "temp_upload_${UUID.randomUUID().toString().take(8)}_$cleanDisplayName")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                try { tempFile.delete() } catch (_: Exception) {}
                return null
            }

            val fastHash = computeFastFileHash(tempFile)
            val prefix = if (isAudio) "vault_aud" else "vault_vid"
            val canonicalVaultName = if (fastHash.isNotEmpty()) {
                "${prefix}_${fastHash}_$cleanDisplayName"
            } else {
                "${prefix}_${tempFile.length()}_$cleanDisplayName"
            }
            val targetVaultFile = File(vaultDir, canonicalVaultName)

            // 1. Cek apakah file Vault kanonik sudah ada
            if (targetVaultFile.exists() && targetVaultFile.length() == tempFile.length()) {
                try { tempFile.delete() } catch (_: Exception) {}
                Log.i(TAG, "♻️ [Anti-Duplikat] Menggunakan ulang file Vault yang sudah ada: ${targetVaultFile.name}")
                mirrorSingleFileToCustomMediaVaultAsync(targetVaultFile)
                return Pair(targetVaultFile, cleanDisplayName)
            }

            // 2. Cek apakah ada file lama di vaultDir dengan ukuran & hash identik
            val existingIdentical = vaultDir.listFiles()?.firstOrNull { f ->
                f.isFile &&
                    f.absolutePath != tempFile.absolutePath &&
                    !f.name.matches(Regex("""^video_\d+\.mp4$""")) &&
                    f.name != "virtual.mp4" &&
                    f.name != "audio.mp3" &&
                    f.name != "audio.wav" &&
                    f.length() == tempFile.length() &&
                    computeFastFileHash(f) == fastHash
            }

            if (existingIdentical != null) {
                try { tempFile.delete() } catch (_: Exception) {}
                Log.i(TAG, "♻️ [Anti-Duplikat] Menemukan file fisik identik di penyimpanan: ${existingIdentical.name}")
                mirrorSingleFileToCustomMediaVaultAsync(existingIdentical)
                return Pair(existingIdentical, cleanDisplayName)
            }

            // 3. Pindahkan tempFile ke targetVaultFile
            val renamed = tempFile.renameTo(targetVaultFile)
            val finalFile = if (renamed) targetVaultFile else {
                tempFile.copyTo(targetVaultFile, overwrite = true)
                try { tempFile.delete() } catch (_: Exception) {}
                targetVaultFile
            }
            finalFile.setReadable(true, false)
            finalFile.setWritable(true, false)

            // 4. Cerminkan otomatis ke Custom Local Vault (media_vault/) di background
            mirrorSingleFileToCustomMediaVaultAsync(finalFile)

            Pair(finalFile, cleanDisplayName)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan media ke Vault: ${e.message}", e)
            null
        }
    }

    private fun mirrorSingleFileToCustomMediaVaultAsync(file: File) {
        backgroundScope.launch {
            try {
                mirrorFileToCustomMediaVault(file, isAudio = isAudioFileName(file.name))
            } catch (_: Exception) {}
        }
    }

    private fun isAudioFileName(name: String): Boolean {
        val low = name.lowercase()
        return low.endsWith(".mp3") || low.endsWith(".wav") || low.endsWith(".m4a") || low.endsWith(".aac") || low.endsWith(".ogg")
    }

    private fun mirrorFileToCustomMediaVault(sourceFile: File, isAudio: Boolean): File? {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return null
        return try {
            val customMediaDir = getCustomMediaVaultDir()
            val defaultExt = if (isAudio) ".mp3" else ".mp4"
            val cleanName = normalizeCleanFileName(sourceFile.name, defaultExt)
            val canonicalName = if (sourceFile.name.startsWith("vault_vid_") || sourceFile.name.startsWith("vault_aud_")) {
                sourceFile.name
            } else {
                val hash = computeFastFileHash(sourceFile)
                val prefix = if (isAudio) "vault_aud" else "vault_vid"
                if (hash.isNotEmpty()) "${prefix}_${hash}_$cleanName" else "${prefix}_${sourceFile.length()}_$cleanName"
            }

            val destFile = File(customMediaDir, canonicalName)
            if (destFile.exists() && destFile.length() == sourceFile.length()) {
                return destFile
            }
            if (destFile.absolutePath != sourceFile.absolutePath) {
                sourceFile.copyTo(destFile, overwrite = true)
            }
            destFile
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menyalin ${sourceFile.name} ke Custom Media Vault: ${e.message}")
            null
        }
    }

    private fun mirrorPlaylistFilesToCustomMediaVault(videos: List<VideoItem>, audios: List<AudioItem>) {
        for (v in videos) {
            if (v.id.startsWith("vs_rolling_") || v.path.contains("vs_rolling_")) continue
            val f = File(v.path)
            if (f.exists() && f.length() > 0L) {
                mirrorFileToCustomMediaVault(f, isAudio = false)
            }
        }
        for (a in audios) {
            val f = File(a.path)
            if (f.exists() && f.length() > 0L) {
                mirrorFileToCustomMediaVault(f, isAudio = true)
            }
        }
    }

    private fun mirrorActiveAndConceptMediaToCustomVault() {
        try {
            val repo = VcamRepository.getInstance(appContext)
            val cfg = repo.config.value
            mirrorPlaylistFilesToCustomMediaVault(cfg.playlist, cfg.audioPlaylist)

            for (path in getAllProtectedMediaPaths()) {
                val f = File(path)
                if (f.exists() && f.isFile && f.length() > 0L) {
                    mirrorFileToCustomMediaVault(f, isAudio = isAudioFileName(f.name))
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Mencari apakah ada VideoItem di konsep tersimpan yang menggunakan path fisik yang persis sama,
     * tanpa mencocokkan nama file secara longgar agar video baru tidak mewarisi ID konsep lama.
     */
    fun findSavedVideoMetadataByPath(physicalPath: String): VideoItem? {
        val targetCanon = try { File(physicalPath).canonicalPath } catch (_: Exception) { physicalPath }
        for (concept in _concepts.value) {
            try {
                val root = JSONObject(concept.snapshotJson)
                val plStr = root.optString("videoPlaylistJson", "")
                val items = VideoPlaylistRepository.parsePlaylistFromJson(plStr)
                val found = items.firstOrNull { item ->
                    val c = try { File(item.path).canonicalPath } catch (_: Exception) { item.path }
                    c == targetCanon || item.path == physicalPath
                }
                if (found != null) return found
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Mencari apakah ada AudioItem di konsep tersimpan yang menggunakan path fisik yang persis sama,
     * tanpa mencocokkan nama file secara longgar agar audio baru tidak mewarisi ID konsep lama.
     */
    fun findSavedAudioMetadataByPath(physicalPath: String): AudioItem? {
        val targetCanon = try { File(physicalPath).canonicalPath } catch (_: Exception) { physicalPath }
        for (concept in _concepts.value) {
            try {
                val root = JSONObject(concept.snapshotJson)
                val alStr = root.optString("audioPlaylistJson", "")
                val items = AudioPlaylistRepository.parseAudioPlaylistJson(alStr)
                val found = items.firstOrNull { item ->
                    val c = try { File(item.path).canonicalPath } catch (_: Exception) { item.path }
                    c == targetCanon || item.path == physicalPath
                }
                if (found != null) return found
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Mengembalikan himpunan seluruh path file (absolute & canonical) yang masih dipakai oleh minimal 1 Konsep Backup Tersimpan.
     * PENTING: Tidak menyertakan auto_checkpoint_state.json agar ketika pengguna menghapus video/audio di sesi aktif,
     * file sesi lama benar-benar dihapus dari disk apabila tidak disimpan di Konsep Backup mana pun.
     */
    fun getAllProtectedMediaPaths(): Set<String> {
        val protectedSet = mutableSetOf<String>()
        val allSnapshots = _concepts.value.map { it.snapshotJson }
        for (snap in allSnapshots) {
            try {
                val root = JSONObject(snap)
                val vArr = JSONArray(root.optString("videoPlaylistJson", "[]"))
                for (i in 0 until vArr.length()) {
                    val p = vArr.optJSONObject(i)?.optString("path", "") ?: ""
                    if (p.isNotBlank()) {
                        protectedSet.add(p)
                        val f = File(p)
                        protectedSet.add(f.absolutePath)
                        try { protectedSet.add(f.canonicalPath) } catch (_: Exception) {}
                    }
                }
                val aArr = JSONArray(root.optString("audioPlaylistJson", "[]"))
                for (i in 0 until aArr.length()) {
                    val p = aArr.optJSONObject(i)?.optString("path", "") ?: ""
                    if (p.isNotBlank()) {
                        protectedSet.add(p)
                        val f = File(p)
                        protectedSet.add(f.absolutePath)
                        try { protectedSet.add(f.canonicalPath) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
        return protectedSet
    }

    fun getAllProtectedMediaFileNames(): Set<String> {
        return getAllProtectedMediaPaths().mapNotNull { path ->
            val name = File(path).name
            name.takeIf { it.isNotBlank() }
        }.toSet()
    }

    /**
     * Mengecek apakah suatu file fisik masih direferensikan oleh Konsep Backup mana pun.
     */
    fun isMediaPathProtectedByAnyConcept(path: String): Boolean {
        if (path.isBlank()) return false
        val protectedPaths = getAllProtectedMediaPaths()
        val f = File(path)
        val abs = f.absolutePath
        val canon = try { f.canonicalPath } catch (_: Exception) { abs }
        return protectedPaths.contains(path) ||
            protectedPaths.contains(abs) ||
            protectedPaths.contains(canon) ||
            getAllProtectedMediaFileNames().contains(f.name)
    }

    /**
     * Memotret 100% seluruh pengaturan aplikasi saat ini (Video, MP3, IFS SV1/SV2, VTM/VT1..n, AX/SR/SC,
     * Stiker, Watermark, Notif Beli, Komentar, Auto-Tap, Kamera, Anti-Hash, Target App, RAM/VPN)
     * menjadi 1 objek ConceptProfile.
     */
    fun captureCurrentStateSnapshot(
        id: String,
        name: String,
        createdAt: Long,
        updatedAt: Long,
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): ConceptProfile {
        val repo = VcamRepository.getInstance(appContext)
        val cfg = repo.config.value
        val ifsMgr = IfsManager.getInstance(appContext)

        val isIfs = ifsMgr.isIfsActive.value || cfg.isIfsMode
        val modeSummary = when {
            !options.includeVideos -> "Tanpa Video"
            isIfs -> {
                val mode = ifsMgr.videoMode.value
                if (mode == com.tajasb.infinity.IfsVideoMode.SV3) {
                    "♾️ IFS SV3 (VL ${ifsMgr.sv3VlMinLoops.value}..${ifsMgr.sv3VlMaxLoops.value}x • ${ifsMgr.sv3VbVideoIds.value.size} VB)"
                } else {
                    val vtCount = ifsMgr.vtPackages.value.size
                    "♾️ IFS ${mode.badge} ($vtCount VT)"
                }
            }
            cfg.isMasterSpillMode -> "🎯 Master & Spill"
            cfg.isBridgeCycleMode -> "🔀 Siklus Bridge"
            else -> "🔁 Standar (${cfg.autoNextMode.label})"
        }

        val activeOverlays = mutableListOf<String>()
        if (options.includeOverlays) {
            if (cfg.enableWatermarkModule) activeOverlays.add("WM")
            if (cfg.enableStickerModule) activeOverlays.add("Stiker")
            if (cfg.enableSalesNotifModule) activeOverlays.add("Notif")
            if (cfg.enableAutoTimeModule) activeOverlays.add("Waktu")
            if (cfg.enablePrayerCommentModule) activeOverlays.add("Komen")
        }
        if (options.includeAutoTapChoreo && cfg.enableAutoTapModule) activeOverlays.add("Tap")
        val overlaySummary = if (activeOverlays.isEmpty()) "Overlay OFF" else activeOverlays.joinToString(" • ")

        val normalizedVideos = if (options.includeVideos) {
            cfg.playlist
                .filterNot { repo.isRollingVsItem(it) }
                .map {
                    it.copy(name = normalizeCleanFileName(it.name, ".mp4"))
                }
        } else emptyList()
        val normalizedAudios = if (options.includeAudios) {
            cfg.audioPlaylist
                .filterNot { repo.isRollingVsAudioItem(it) }
                .map {
                    it.copy(name = normalizeCleanFileName(it.name, ".mp3"))
                }
        } else emptyList()

        val root = JSONObject().apply {
            put("schemaVersion", 2)
            put("conceptId", id)
            put("conceptName", name)
            put("savedAt", updatedAt)

            // 1. Video & Audio Playlists (dengan nama file bersih & metadata lengkap, tanpa file sementara vs_rolling_*)
            put("videoPlaylistJson", VideoPlaylistRepository.serializePlaylistToJson(normalizedVideos))
            put("audioPlaylistJson", AudioPlaylistRepository.serializeAudioPlaylistJson(normalizedAudios))

            val safeSnapVidIdx = if (normalizedVideos.isEmpty()) 0 else {
                val curNonRollingId = cfg.currentVideo?.takeIf { !repo.isRollingVsItem(it) }?.id
                val foundIdx = if (curNonRollingId != null) normalizedVideos.indexOfFirst { it.id == curNonRollingId } else -1
                if (foundIdx >= 0) foundIdx else cfg.currentVideoIndex.coerceIn(0, normalizedVideos.size - 1)
            }
            val safeSnapVid = normalizedVideos.getOrNull(safeSnapVidIdx)
            val safeSnapAudIdx = if (normalizedAudios.isEmpty()) 0 else {
                val curNonRollingAudId = cfg.currentAudio?.takeIf { !repo.isRollingVsAudioItem(it) }?.id
                val foundIdx = if (curNonRollingAudId != null) normalizedAudios.indexOfFirst { it.id == curNonRollingAudId } else -1
                if (foundIdx >= 0) foundIdx else cfg.currentAudioIndex.coerceIn(0, normalizedAudios.size - 1)
            }
            val safeSnapAud = normalizedAudios.getOrNull(safeSnapAudIdx)

            // 2. Seluruh Pengaturan VcamConfig & Pemutar Media
            val vcamObj = JSONObject().apply {
                put("isMasterEnabled", cfg.isMasterEnabled)
                put("videoPath", safeSnapVid?.path ?: "")
                put("videoUri", safeSnapVid?.uri ?: "")
                put("audioPath", safeSnapAud?.path ?: "")
                put("audioUri", safeSnapAud?.uri ?: "")
                put("audioName", safeSnapAud?.name ?: "")
                put("currentVideoIndex", safeSnapVidIdx)
                put("currentAudioIndex", safeSnapAudIdx)
                put("videoVolume", repo.videoVolume.value.toDouble())
                put("audioVolume", repo.musicPlayer.volume.value.toDouble())
                put("audioLoopMode", repo.musicPlayer.isLooping.value)
                put("rotation", cfg.rotation)
                put("isMirrored", cfg.isMirrored)
                put("isVerticalFlipped", cfg.isVerticalFlipped)
                put("zoomFactor", cfg.zoomFactor.toDouble())
                put("scaleMode", cfg.scaleMode.name)
                put("audioEnabled", cfg.audioEnabled)
                put("loopPlayback", cfg.loopPlayback)
                put("autoNextEnabled", cfg.autoNextEnabled)
                put("autoNextMode", cfg.autoNextMode.name)
                put("autoNextLeadSeconds", cfg.autoNextLeadSeconds)
                put("autoNextLeadMs", cfg.autoNextLeadMs)
                put("forceResolution", cfg.forceResolution.name)
                put("customWidth", cfg.customWidth)
                put("customHeight", cfg.customHeight)
                put("fps", cfg.fps)
                put("primaryTargetPackage", cfg.primaryTargetPackage)
                put("targetPackages", JSONArray(cfg.targetPackages.toList()))
                put("isFloatingControlEnabled", cfg.isFloatingControlEnabled)
                put("floatingAlpha", cfg.floatingAlpha.toDouble())
                put("isVideoPaused", cfg.isVideoPaused)
                put("isVideoAudioMuted", cfg.isVideoAudioMuted)
                put("isMp3AudioPaused", cfg.isMp3AudioPaused)
                put("isMp3AudioMuted", cfg.isMp3AudioMuted)
                put("hookEngine", cfg.hookEngine.name)
                put("videoSwitchMode", cfg.videoSwitchMode.name)
                put("isLightweightMode", cfg.isLightweightMode)
                put("isDebugOsdInVirtualCameraEnabled", cfg.isDebugOsdInVirtualCameraEnabled)
                put("isMasterSpillMode", cfg.isMasterSpillMode)
                put("masterVideoId", cfg.masterVideoId ?: "")
                put("masterSpillWaitFinish", cfg.masterSpillWaitFinish)
                put("autoSpillFromComments", cfg.autoSpillFromComments)
                put("isBridgeCycleMode", cfg.isBridgeCycleMode)
                put("isIfsMode", cfg.isIfsMode)
                put("bridge1VideoId", cfg.bridge1VideoId ?: "")
                put("bridge2VideoId", cfg.bridge2VideoId ?: "")
                put("currentBridgePhase", cfg.currentBridgePhase.name)
                put("enableSalesNotifModule", cfg.enableSalesNotifModule)
                put("enableStickerModule", cfg.enableStickerModule)
                put("enableWatermarkModule", cfg.enableWatermarkModule)
                put("enableAutoTimeModule", cfg.enableAutoTimeModule)
                put("enablePrayerCommentModule", cfg.enablePrayerCommentModule)
                put("enableAutoTapModule", cfg.enableAutoTapModule)
                put("enableMp3MusicModule", cfg.enableMp3MusicModule)
                put("enableQuranAudioModule", cfg.enableQuranAudioModule)
                put("enableOsdModule", cfg.enableOsdModule)
                put("isLiveFocusVpnEnabled", cfg.isLiveFocusVpnEnabled)
                put("isAutoRamCleanEnabled", cfg.isAutoRamCleanEnabled)
                put("fpsGovernorLimit", cfg.fpsGovernorLimit)
                put("ramCleanWhitelistPackages", JSONArray(cfg.ramCleanWhitelistPackages.toList()))
                put("vpnBypassPackages", JSONArray(cfg.vpnBypassPackages.toList()))
                put("antiHashMode", cfg.antiHashMode.name)
                put("isSafeCodecGuardActive", cfg.isSafeCodecGuardActive)
                put("isAudioAntiHashEnabled", cfg.isAudioAntiHashEnabled)
                put("audioAntiHashMode", cfg.audioAntiHashMode.name)
                put("isAutoRocketRecoveryEnabled", cfg.isAutoRocketRecoveryEnabled)
                put("autoRecoveryStallThresholdSec", cfg.autoRecoveryStallThresholdSec)
                put("isPeriodicRamCleanEnabled", cfg.isPeriodicRamCleanEnabled)
                put("periodicRamCleanIntervalMinutes", cfg.periodicRamCleanIntervalMinutes)
                put("sessionDnaIndex", cfg.sessionDnaIndex)
                put("sessionDnaSeed", cfg.sessionDnaSeed)
                put("voiceEffect", cfg.voiceEffect.name)
                put("isDualAudioMixingEnabled", cfg.isDualAudioMixingEnabled)
                put("isIndependentAudioEnabled", cfg.isIndependentAudioEnabled)
                put("isHumanVolumeFluctuationEnabled", cfg.isHumanVolumeFluctuationEnabled)
                put("humanFluctuationIntensity", cfg.humanFluctuationIntensity.toDouble())
                put("isAudioLayer2Enabled", cfg.isAudioLayer2Enabled)
                put("audioLayer2Id", cfg.audioLayer2Id ?: "")
                put("audioLayer2Path", cfg.audioLayer2Path)
                put("audioLayer2Name", cfg.audioLayer2Name)
                put("audioLayer2Volume", cfg.audioLayer2Volume.toDouble())
                put("isAudioLayer2Muted", cfg.isAudioLayer2Muted)
                put("isAudioLayer2Looping", cfg.isAudioLayer2Looping)
                put("isAudioLayer3Enabled", cfg.isAudioLayer3Enabled)
                put("audioLayer3Ids", JSONArray(cfg.audioLayer3Ids.toList()))
                put("audioLayer3Volume", cfg.audioLayer3Volume.toDouble())
                put("isAudioLayer3Muted", cfg.isAudioLayer3Muted)
                put("audioLayer3MinIntervalSec", cfg.audioLayer3MinIntervalSec)
                put("audioLayer3MaxIntervalSec", cfg.audioLayer3MaxIntervalSec)
                put("enableDnaModule", cfg.enableDnaModule)
                put("enableAiDetectionModule", cfg.enableAiDetectionModule)
                put("isAdBlockerActive", cfg.isAdBlockerActive)
                put("adBlockerAutoFreeze", cfg.adBlockerAutoFreeze)
                put("videoAntiHashPercent", cfg.videoAntiHashPercent.toDouble())
                put("audioAntiHashPercent", cfg.audioAntiHashPercent.toDouble())
                put("isAudioLayer2SyncAntiHash", cfg.isAudioLayer2SyncAntiHash)
                put("isDnaZoomEnabled", cfg.isDnaZoomEnabled)
                put("sessionDnaCustomZoom", cfg.sessionDnaCustomZoom.toDouble())
                put("sessionDnaPanX", cfg.sessionDnaPanX.toDouble())
                put("sessionDnaPanY", cfg.sessionDnaPanY.toDouble())
                put("usedDnaZoomHistory", JSONArray(cfg.usedDnaZoomHistory.toList()))
            }
            put("vcamConfig", vcamObj)

            // 3. Infinity System (IFS) Full State (SV1/SV2/SV3, VTM, VT1..n, VL/VB, AX, SR, SC)
            if (options.includeVideos || options.includeAudios) {
                put("ifsSnapshot", ifsMgr.exportToJson())
            }

            // 4. Floating Overlays (Watermark, Stiker, Notif Beli, Komentar, Auto-Tap)
            put("floatingJson", FloatingConfigBackupManager.exportConfigToJson(appContext, options))

            // 5. Master Raw SharedPreferences Dump (mencakup 100% seluruh koordinat, anchor, per-app config, koreografi Auto-Tap, dll)
            put("rawPrefsMaster", exportAllPreferencesMasterJson(options))
        }

        return ConceptProfile(
            id = id,
            name = name.trim().ifEmpty { "Konsep Live" },
            createdAt = createdAt,
            updatedAt = updatedAt,
            videoCount = normalizedVideos.size,
            audioCount = normalizedAudios.size,
            modeSummary = modeSummary,
            overlaySummary = overlaySummary,
            snapshotJson = root.toString()
        )
    }

    /**
     * Menyimpan seluruh racikan aktif saat ini sebagai Konsep baru (di memori internal + Folder Backup Lokal Custom).
     */
    fun saveCurrentAsConcept(
        conceptName: String,
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): ConceptProfile {
        val now = System.currentTimeMillis()
        val id = "CONCEPT-${UUID.randomUUID().toString().take(8).uppercase()}"
        val cleanName = conceptName.trim().ifEmpty { "Konsep #${_concepts.value.size + 1}" }
        val profile = captureCurrentStateSnapshot(
            id = id,
            name = cleanName,
            createdAt = now,
            updatedAt = now,
            options = options
        )

        val updatedList = listOf(profile) + _concepts.value
        _concepts.value = updatedList
        _activeConceptId.value = profile.id
        saveConceptsToPrefs(updatedList, profile.id, syncToExternal = true)

        backgroundScope.launch {
            val repo = VcamRepository.getInstance(appContext)
            val cfg = repo.config.value
            val vids = if (options.includeVideos) cfg.playlist else emptyList()
            val auds = if (options.includeAudios) cfg.audioPlaylist else emptyList()
            mirrorPlaylistFilesToCustomMediaVault(vids, auds)
            saveAutoCheckpointInternal()
        }
        return profile
    }

    /**
     * Memperbarui (timpa) Konsep yang sudah ada dengan pengaturan aktif saat ini.
     */
    fun updateConceptWithCurrentState(
        conceptId: String,
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): ConceptProfile? {
        val currentList = _concepts.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == conceptId }
        if (idx == -1) return null

        val existing = currentList[idx]
        val updatedProfile = captureCurrentStateSnapshot(
            id = existing.id,
            name = existing.name,
            createdAt = existing.createdAt,
            updatedAt = System.currentTimeMillis(),
            options = options
        )
        currentList[idx] = updatedProfile
        _concepts.value = currentList
        _activeConceptId.value = updatedProfile.id
        saveConceptsToPrefs(currentList, updatedProfile.id, syncToExternal = true)

        backgroundScope.launch {
            val repo = VcamRepository.getInstance(appContext)
            val cfg = repo.config.value
            val vids = if (options.includeVideos) cfg.playlist else emptyList()
            val auds = if (options.includeAudios) cfg.audioPlaylist else emptyList()
            mirrorPlaylistFilesToCustomMediaVault(vids, auds)
            saveAutoCheckpointInternal()
        }
        return updatedProfile
    }

    /**
     * Mengubah nama Konsep yang tersimpan.
     */
    fun renameConcept(conceptId: String, newName: String): Boolean {
        val clean = newName.trim()
        if (clean.isEmpty()) return false
        val currentList = _concepts.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == conceptId }
        if (idx == -1) return false

        currentList[idx] = currentList[idx].copy(
            name = clean,
            updatedAt = System.currentTimeMillis()
        )
        _concepts.value = currentList
        saveConceptsToPrefs(currentList, _activeConceptId.value, syncToExternal = true)
        return true
    }

    /**
     * Menghapus Konsep tersimpan dan otomatis membersihkan file media yatim yang sudah tidak dipakai konsep mana pun.
     */
    suspend fun deleteConcept(conceptId: String): Boolean = withContext(Dispatchers.IO) {
        val currentList = _concepts.value.filter { it.id != conceptId }
        if (currentList.size == _concepts.value.size) return@withContext false

        val nextActiveId = if (_activeConceptId.value == conceptId) null else _activeConceptId.value
        _concepts.value = currentList
        _activeConceptId.value = nextActiveId
        saveConceptsToPrefs(currentList, nextActiveId, syncToExternal = true)

        // Bersihkan file fisik yang sudah tidak dipakai di daftar aktif maupun di konsep lain
        deduplicateAndCleanVault()
        true
    }

    /**
     * Memulihkan (Restore) Konsep secara utuh dalam 1 klik (< 0,2 detik).
     */
    suspend fun restoreConcept(
        conceptId: String,
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): Result<ConceptProfile> = withContext(Dispatchers.IO) {
        val profile = _concepts.value.firstOrNull { it.id == conceptId }
            ?: return@withContext Result.failure(IllegalArgumentException("Konsep tidak ditemukan"))

        val res = applySnapshotJsonInternal(
            snapshotJson = profile.snapshotJson,
            profileId = profile.id,
            profileName = profile.name,
            options = options
        )
        if (res.isSuccess) {
            _activeConceptId.value = profile.id
            prefs.edit().putString(KEY_ACTIVE_CONCEPT_ID, profile.id).apply()
            writeConceptsDbToCustomVault(_concepts.value, profile.id)
            saveAutoCheckpointInternal()
            Result.success(profile)
        } else {
            res
        }
    }

    /**
     * Mesin pemulihan utama dari snapshot JSON (dipakai oleh Restore Konsep, Auto-Restore Startup, dan Impor ZIP).
     * Otomatis menyembuhkan (self-heal) file fisik Video MP4 & Audio MP3 dari Custom Local Vault `media_vault/`
     * apabila file di direktori internal aplikasi sempat terhapus.
     */
    private suspend fun applySnapshotJsonInternal(
        snapshotJson: String,
        profileId: String?,
        profileName: String,
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): Result<ConceptProfile> = withContext(Dispatchers.IO) {
        isRestoringState.set(true)
        return@withContext try {
            val root = JSONObject(snapshotJson)
            val repo = VcamRepository.getInstance(appContext)
            val ifsMgr = IfsManager.getInstance(appContext)

            val internalVaultDir = repo.getInternalCamera1Directory()
            val customMediaDir = getCustomMediaVaultDir()

            // 0.0. Bersihkan seluruh file fisik rolling sementara dari konsep sebelumnya agar tidak bocor
            if (options.includeVideos || options.includeAudios) {
                ifsMgr.resetAndPurgeAllRollingFilesForProjectSwitch()
            }

            // 0. Pulihkan Raw SharedPreferences Master terlebih dahulu (koordinat floating, screen anchors, per-app, koreo Auto-Tap, dll)
            root.optJSONObject("rawPrefsMaster")?.let { rawMaster ->
                importAllPreferencesMasterJson(rawMaster, options)
                if (options.includeOverlays) {
                    try {
                        com.tajasb.livecomment.ViewerAnchorManager.getInstance(appContext).reloadFromPrefs()
                    } catch (_: Throwable) {}
                }
                if (options.includeAutoTapChoreo) {
                    try {
                        com.tajasb.autotap.AutoTapManager.getInstance(appContext).reloadFromPrefs()
                    } catch (_: Throwable) {}
                }
            }

            // 1. Pulihkan Playlist Video (cek directFile -> internalVaultDir -> customMediaDir lalu salin ke internal jika perlu)
            val rawVideos = VideoPlaylistRepository.parsePlaylistFromJson(root.optString("videoPlaylistJson", "[]"))
                .filterNot { repo.isRollingVsItem(it) }
            val restoredVideos = rawVideos.mapNotNull { item ->
                val resolvedFile = resolveAndHealMediaFile(
                    rawPath = item.path,
                    rawName = item.name,
                    defaultExt = ".mp4",
                    internalVaultDir = internalVaultDir,
                    customMediaDir = customMediaDir
                )
                if (resolvedFile != null) {
                    item.copy(
                        name = normalizeCleanFileName(item.name, ".mp4"),
                        path = resolvedFile.absolutePath
                    )
                } else null
            }

            // 2. Pulihkan Playlist Audio MP3 (cek directFile -> internalVaultDir -> customMediaDir)
            val rawAudioJsonStr = root.optString("audioPlaylistJson", "[]")
            val rawAudios = parseAudioListAllowingVaultLookup(
                jsonStr = rawAudioJsonStr,
                internalVaultDir = internalVaultDir,
                customMediaDir = customMediaDir
            ).filterNot { repo.isRollingVsAudioItem(it) }

            // Fungsi penyembuh ID Video & Audio (jika terjadi perubahan ID atau perlu pencocokan nama bersih pasca reset HP)
            fun healVideoRefId(candidateId: String?): String? {
                if (candidateId.isNullOrBlank()) return null
                if (restoredVideos.any { it.id == candidateId }) return candidateId
                val origItem = rawVideos.firstOrNull { it.id == candidateId }
                if (origItem != null) {
                    val cleanOrig = normalizeCleanFileName(origItem.name, ".mp4")
                    val matched = restoredVideos.firstOrNull { it.name.equals(cleanOrig, ignoreCase = true) }
                    if (matched != null) return matched.id
                }
                return candidateId
            }

            // 3. Pulihkan VcamConfig penuh
            val vcamObj = root.optJSONObject("vcamConfig") ?: JSONObject()
            val safeVidIdx = if (restoredVideos.isEmpty()) 0 else vcamObj.optInt("currentVideoIndex", 0).coerceIn(0, restoredVideos.size - 1)
            val activeVid = restoredVideos.getOrNull(safeVidIdx)

            val safeAudIdx = if (rawAudios.isEmpty()) 0 else vcamObj.optInt("currentAudioIndex", 0).coerceIn(0, rawAudios.size - 1)
            val activeAud = rawAudios.getOrNull(safeAudIdx)

            val targetPkgs = mutableSetOf<String>()
            vcamObj.optJSONArray("targetPackages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i, "")
                    if (pkg.isNotBlank()) targetPkgs.add(pkg)
                }
            }

            val ramWhitelist = mutableSetOf<String>()
            vcamObj.optJSONArray("ramCleanWhitelistPackages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i, "")
                    if (pkg.isNotBlank()) ramWhitelist.add(pkg)
                }
            }

            val vpnBypass = mutableSetOf<String>()
            vcamObj.optJSONArray("vpnBypassPackages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i, "")
                    if (pkg.isNotBlank()) vpnBypass.add(pkg)
                }
            }

            val curCfg = repo.config.value
            val finalVideos = if (options.includeVideos) restoredVideos else curCfg.playlist
            val finalVidIdx = if (options.includeVideos) safeVidIdx else curCfg.currentVideoIndex.coerceIn(0, (finalVideos.size - 1).coerceAtLeast(0))
            val finalActiveVid = finalVideos.getOrNull(finalVidIdx)

            val finalAudios = if (options.includeAudios) rawAudios else curCfg.audioPlaylist
            val finalAudIdx = if (options.includeAudios) safeAudIdx else curCfg.currentAudioIndex.coerceIn(0, (finalAudios.size - 1).coerceAtLeast(0))
            val finalActiveAud = finalAudios.getOrNull(finalAudIdx)

            val restoredCfg = curCfg.copy(
                isMasterEnabled = if (options.includeCameraVisual) vcamObj.optBoolean("isMasterEnabled", curCfg.isMasterEnabled) else curCfg.isMasterEnabled,
                playlist = finalVideos,
                currentVideoIndex = finalVidIdx,
                videoPath = finalActiveVid?.path ?: "",
                videoUri = finalActiveVid?.uri,
                audioPlaylist = finalAudios,
                currentAudioIndex = finalAudIdx,
                audioPath = finalActiveAud?.path ?: "",
                audioUri = finalActiveAud?.uri,
                audioName = finalActiveAud?.name ?: "",
                rotation = if (options.includeCameraVisual) vcamObj.optInt("rotation", curCfg.rotation) else curCfg.rotation,
                isMirrored = if (options.includeCameraVisual) vcamObj.optBoolean("isMirrored", curCfg.isMirrored) else curCfg.isMirrored,
                isVerticalFlipped = if (options.includeCameraVisual) vcamObj.optBoolean("isVerticalFlipped", curCfg.isVerticalFlipped) else curCfg.isVerticalFlipped,
                zoomFactor = if (options.includeCameraVisual) vcamObj.optDouble("zoomFactor", curCfg.zoomFactor.toDouble()).toFloat().coerceIn(0.5f, 3.0f) else curCfg.zoomFactor,
                scaleMode = if (options.includeCameraVisual) {
                    try { ScaleMode.valueOf(vcamObj.optString("scaleMode", curCfg.scaleMode.name)) } catch (_: Exception) { curCfg.scaleMode }
                } else curCfg.scaleMode,
                audioEnabled = if (options.includeAudios) vcamObj.optBoolean("audioEnabled", curCfg.audioEnabled) else curCfg.audioEnabled,
                loopPlayback = if (options.includeVideos) vcamObj.optBoolean("loopPlayback", curCfg.loopPlayback) else curCfg.loopPlayback,
                autoNextEnabled = if (options.includeVideos) vcamObj.optBoolean("autoNextEnabled", curCfg.autoNextEnabled) else curCfg.autoNextEnabled,
                autoNextMode = if (options.includeVideos) {
                    try { AutoNextMode.valueOf(vcamObj.optString("autoNextMode", curCfg.autoNextMode.name)) } catch (_: Exception) { curCfg.autoNextMode }
                } else curCfg.autoNextMode,
                autoNextLeadSeconds = if (options.includeVideos) vcamObj.optInt("autoNextLeadSeconds", curCfg.autoNextLeadSeconds) else curCfg.autoNextLeadSeconds,
                autoNextLeadMs = if (options.includeVideos) vcamObj.optInt("autoNextLeadMs", curCfg.autoNextLeadMs) else curCfg.autoNextLeadMs,
                forceResolution = if (options.includeCameraVisual) {
                    try { ResolutionPreset.valueOf(vcamObj.optString("forceResolution", curCfg.forceResolution.name)) } catch (_: Exception) { curCfg.forceResolution }
                } else curCfg.forceResolution,
                customWidth = if (options.includeCameraVisual) vcamObj.optInt("customWidth", curCfg.customWidth) else curCfg.customWidth,
                customHeight = if (options.includeCameraVisual) vcamObj.optInt("customHeight", curCfg.customHeight) else curCfg.customHeight,
                fps = if (options.includeCameraVisual) vcamObj.optInt("fps", curCfg.fps) else curCfg.fps,
                targetPackages = if (options.includeCameraVisual && targetPkgs.isNotEmpty()) targetPkgs else curCfg.targetPackages,
                primaryTargetPackage = if (options.includeCameraVisual) vcamObj.optString("primaryTargetPackage", curCfg.primaryTargetPackage) else curCfg.primaryTargetPackage,
                floatingAlpha = if (options.includeFloatingLayout) vcamObj.optDouble("floatingAlpha", curCfg.floatingAlpha.toDouble()).toFloat() else curCfg.floatingAlpha,
                isVideoPaused = false,
                isVideoAudioMuted = if (options.includeVideos) (finalActiveVid?.isMuted ?: vcamObj.optBoolean("isVideoAudioMuted", false)) else curCfg.isVideoAudioMuted,
                isMp3AudioPaused = if (options.includeAudios) vcamObj.optBoolean("isMp3AudioPaused", curCfg.isMp3AudioPaused) else curCfg.isMp3AudioPaused,
                isMp3AudioMuted = if (options.includeAudios) vcamObj.optBoolean("isMp3AudioMuted", curCfg.isMp3AudioMuted) else curCfg.isMp3AudioMuted,
                hookEngine = if (options.includeCameraVisual) {
                    try { HookEngineType.valueOf(vcamObj.optString("hookEngine", curCfg.hookEngine.name)) } catch (_: Exception) { curCfg.hookEngine }
                } else curCfg.hookEngine,
                videoSwitchMode = if (options.includeVideos) {
                    try { VideoSwitchMode.valueOf(vcamObj.optString("videoSwitchMode", curCfg.videoSwitchMode.name)) } catch (_: Exception) { curCfg.videoSwitchMode }
                } else curCfg.videoSwitchMode,
                isLightweightMode = if (options.includeCameraVisual) vcamObj.optBoolean("isLightweightMode", curCfg.isLightweightMode) else curCfg.isLightweightMode,
                isDebugOsdInVirtualCameraEnabled = if (options.includeCameraVisual) vcamObj.optBoolean("isDebugOsdInVirtualCameraEnabled", curCfg.isDebugOsdInVirtualCameraEnabled) else curCfg.isDebugOsdInVirtualCameraEnabled,
                isMasterSpillMode = if (options.includeVideos) vcamObj.optBoolean("isMasterSpillMode", false) else curCfg.isMasterSpillMode,
                masterVideoId = if (options.includeVideos) healVideoRefId(vcamObj.optString("masterVideoId", "").takeIf { it.isNotBlank() }) else curCfg.masterVideoId,
                masterSpillWaitFinish = if (options.includeVideos) vcamObj.optBoolean("masterSpillWaitFinish", true) else curCfg.masterSpillWaitFinish,
                autoSpillFromComments = if (options.includeVideos) vcamObj.optBoolean("autoSpillFromComments", false) else curCfg.autoSpillFromComments,
                isBridgeCycleMode = if (options.includeVideos) vcamObj.optBoolean("isBridgeCycleMode", false) else curCfg.isBridgeCycleMode,
                isIfsMode = if (options.includeVideos) vcamObj.optBoolean("isIfsMode", false) else curCfg.isIfsMode,
                bridge1VideoId = if (options.includeVideos) healVideoRefId(vcamObj.optString("bridge1VideoId", "").takeIf { it.isNotBlank() }) else curCfg.bridge1VideoId,
                bridge2VideoId = if (options.includeVideos) healVideoRefId(vcamObj.optString("bridge2VideoId", "").takeIf { it.isNotBlank() }) else curCfg.bridge2VideoId,
                currentBridgePhase = if (options.includeVideos) {
                    try { BridgeCyclePhase.valueOf(vcamObj.optString("currentBridgePhase", BridgeCyclePhase.OFF.name)) } catch (_: Exception) { BridgeCyclePhase.OFF }
                } else curCfg.currentBridgePhase,
                enableSalesNotifModule = if (options.includeOverlays) vcamObj.optBoolean("enableSalesNotifModule", curCfg.enableSalesNotifModule) else curCfg.enableSalesNotifModule,
                enableStickerModule = if (options.includeOverlays) vcamObj.optBoolean("enableStickerModule", curCfg.enableStickerModule) else curCfg.enableStickerModule,
                enableWatermarkModule = if (options.includeOverlays) vcamObj.optBoolean("enableWatermarkModule", curCfg.enableWatermarkModule) else curCfg.enableWatermarkModule,
                enableAutoTimeModule = if (options.includeOverlays) vcamObj.optBoolean("enableAutoTimeModule", curCfg.enableAutoTimeModule) else curCfg.enableAutoTimeModule,
                enablePrayerCommentModule = if (options.includeOverlays) vcamObj.optBoolean("enablePrayerCommentModule", curCfg.enablePrayerCommentModule) else curCfg.enablePrayerCommentModule,
                enableAutoTapModule = if (options.includeAutoTapChoreo) vcamObj.optBoolean("enableAutoTapModule", curCfg.enableAutoTapModule) else curCfg.enableAutoTapModule,
                enableMp3MusicModule = if (options.includeAudios) vcamObj.optBoolean("enableMp3MusicModule", curCfg.enableMp3MusicModule) else curCfg.enableMp3MusicModule,
                enableQuranAudioModule = if (options.includeAudios) vcamObj.optBoolean("enableQuranAudioModule", curCfg.enableQuranAudioModule) else curCfg.enableQuranAudioModule,
                enableOsdModule = if (options.includeOverlays) vcamObj.optBoolean("enableOsdModule", curCfg.enableOsdModule) else curCfg.enableOsdModule,
                isLiveFocusVpnEnabled = if (options.includeCameraVisual) vcamObj.optBoolean("isLiveFocusVpnEnabled", curCfg.isLiveFocusVpnEnabled) else curCfg.isLiveFocusVpnEnabled,
                isAutoRamCleanEnabled = if (options.includeCameraVisual) vcamObj.optBoolean("isAutoRamCleanEnabled", curCfg.isAutoRamCleanEnabled) else curCfg.isAutoRamCleanEnabled,
                fpsGovernorLimit = if (options.includeCameraVisual) vcamObj.optInt("fpsGovernorLimit", curCfg.fpsGovernorLimit) else curCfg.fpsGovernorLimit,
                ramCleanWhitelistPackages = if (options.includeCameraVisual && ramWhitelist.isNotEmpty()) ramWhitelist else curCfg.ramCleanWhitelistPackages,
                vpnBypassPackages = if (options.includeCameraVisual && vpnBypass.isNotEmpty()) vpnBypass else curCfg.vpnBypassPackages,
                antiHashMode = if (options.includeCameraVisual) {
                    try { AntiHashIntensity.valueOf(vcamObj.optString("antiHashMode", curCfg.antiHashMode.name)) } catch (_: Exception) { curCfg.antiHashMode }
                } else curCfg.antiHashMode,
                isSafeCodecGuardActive = if (options.includeCameraVisual) vcamObj.optBoolean("isSafeCodecGuardActive", curCfg.isSafeCodecGuardActive) else curCfg.isSafeCodecGuardActive,
                isAudioAntiHashEnabled = if (options.includeAudios) vcamObj.optBoolean("isAudioAntiHashEnabled", curCfg.isAudioAntiHashEnabled) else curCfg.isAudioAntiHashEnabled,
                audioAntiHashMode = if (options.includeAudios) {
                    try { AudioAntiHashIntensity.valueOf(vcamObj.optString("audioAntiHashMode", curCfg.audioAntiHashMode.name)) } catch (_: Exception) { curCfg.audioAntiHashMode }
                } else curCfg.audioAntiHashMode,
                isAutoRocketRecoveryEnabled = if (options.includeCameraVisual) vcamObj.optBoolean("isAutoRocketRecoveryEnabled", curCfg.isAutoRocketRecoveryEnabled) else curCfg.isAutoRocketRecoveryEnabled,
                autoRecoveryStallThresholdSec = if (options.includeCameraVisual) vcamObj.optInt("autoRecoveryStallThresholdSec", curCfg.autoRecoveryStallThresholdSec) else curCfg.autoRecoveryStallThresholdSec,
                isPeriodicRamCleanEnabled = if (options.includeCameraVisual) vcamObj.optBoolean("isPeriodicRamCleanEnabled", curCfg.isPeriodicRamCleanEnabled) else curCfg.isPeriodicRamCleanEnabled,
                periodicRamCleanIntervalMinutes = if (options.includeCameraVisual) vcamObj.optInt("periodicRamCleanIntervalMinutes", curCfg.periodicRamCleanIntervalMinutes) else curCfg.periodicRamCleanIntervalMinutes,
                sessionDnaIndex = if (options.includeCameraVisual) vcamObj.optInt("sessionDnaIndex", curCfg.sessionDnaIndex) else curCfg.sessionDnaIndex,
                sessionDnaSeed = if (options.includeCameraVisual) vcamObj.optLong("sessionDnaSeed", curCfg.sessionDnaSeed) else curCfg.sessionDnaSeed,
                voiceEffect = if (options.includeAudios) {
                    try { com.tajasb.model.VoiceEffect.valueOf(vcamObj.optString("voiceEffect", curCfg.voiceEffect.name)) } catch (_: Exception) { curCfg.voiceEffect }
                } else curCfg.voiceEffect,
                isDualAudioMixingEnabled = if (options.includeAudios) vcamObj.optBoolean("isDualAudioMixingEnabled", curCfg.isDualAudioMixingEnabled) else curCfg.isDualAudioMixingEnabled,
                isIndependentAudioEnabled = if (options.includeAudios) vcamObj.optBoolean("isIndependentAudioEnabled", curCfg.isIndependentAudioEnabled) else curCfg.isIndependentAudioEnabled,
                isHumanVolumeFluctuationEnabled = if (options.includeAudios) vcamObj.optBoolean("isHumanVolumeFluctuationEnabled", curCfg.isHumanVolumeFluctuationEnabled) else curCfg.isHumanVolumeFluctuationEnabled,
                humanFluctuationIntensity = if (options.includeAudios) vcamObj.optDouble("humanFluctuationIntensity", curCfg.humanFluctuationIntensity.toDouble()).toFloat() else curCfg.humanFluctuationIntensity,
                isAudioLayer2Enabled = if (options.includeAudios) vcamObj.optBoolean("isAudioLayer2Enabled", curCfg.isAudioLayer2Enabled) else curCfg.isAudioLayer2Enabled,
                audioLayer2Id = if (options.includeAudios) vcamObj.optString("audioLayer2Id", "").takeIf { it.isNotBlank() } else curCfg.audioLayer2Id,
                audioLayer2Path = if (options.includeAudios) vcamObj.optString("audioLayer2Path", curCfg.audioLayer2Path) else curCfg.audioLayer2Path,
                audioLayer2Name = if (options.includeAudios) vcamObj.optString("audioLayer2Name", curCfg.audioLayer2Name) else curCfg.audioLayer2Name,
                audioLayer2Volume = if (options.includeAudios) vcamObj.optDouble("audioLayer2Volume", curCfg.audioLayer2Volume.toDouble()).toFloat() else curCfg.audioLayer2Volume,
                isAudioLayer2Muted = if (options.includeAudios) vcamObj.optBoolean("isAudioLayer2Muted", curCfg.isAudioLayer2Muted) else curCfg.isAudioLayer2Muted,
                isAudioLayer2Looping = if (options.includeAudios) vcamObj.optBoolean("isAudioLayer2Looping", curCfg.isAudioLayer2Looping) else curCfg.isAudioLayer2Looping,
                isAudioLayer3Enabled = if (options.includeAudios) vcamObj.optBoolean("isAudioLayer3Enabled", curCfg.isAudioLayer3Enabled) else curCfg.isAudioLayer3Enabled,
                audioLayer3Ids = if (options.includeAudios && vcamObj.has("audioLayer3Ids")) {
                    val l3Ids = mutableSetOf<String>()
                    vcamObj.optJSONArray("audioLayer3Ids")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val id = arr.optString(i, "")
                            if (id.isNotBlank()) l3Ids.add(id)
                        }
                    }
                    l3Ids
                } else curCfg.audioLayer3Ids,
                audioLayer3Volume = if (options.includeAudios) vcamObj.optDouble("audioLayer3Volume", curCfg.audioLayer3Volume.toDouble()).toFloat() else curCfg.audioLayer3Volume,
                isAudioLayer3Muted = if (options.includeAudios) vcamObj.optBoolean("isAudioLayer3Muted", curCfg.isAudioLayer3Muted) else curCfg.isAudioLayer3Muted,
                audioLayer3MinIntervalSec = if (options.includeAudios) vcamObj.optInt("audioLayer3MinIntervalSec", curCfg.audioLayer3MinIntervalSec) else curCfg.audioLayer3MinIntervalSec,
                audioLayer3MaxIntervalSec = if (options.includeAudios) vcamObj.optInt("audioLayer3MaxIntervalSec", curCfg.audioLayer3MaxIntervalSec) else curCfg.audioLayer3MaxIntervalSec,
                enableDnaModule = if (options.includeCameraVisual) vcamObj.optBoolean("enableDnaModule", curCfg.enableDnaModule) else curCfg.enableDnaModule,
                enableAiDetectionModule = if (options.includeCameraVisual) vcamObj.optBoolean("enableAiDetectionModule", curCfg.enableAiDetectionModule) else curCfg.enableAiDetectionModule,
                isAdBlockerActive = if (options.includeCameraVisual) vcamObj.optBoolean("isAdBlockerActive", curCfg.isAdBlockerActive) else curCfg.isAdBlockerActive,
                adBlockerAutoFreeze = if (options.includeCameraVisual) vcamObj.optBoolean("adBlockerAutoFreeze", curCfg.adBlockerAutoFreeze) else curCfg.adBlockerAutoFreeze,
                videoAntiHashPercent = if (options.includeCameraVisual) vcamObj.optDouble("videoAntiHashPercent", curCfg.videoAntiHashPercent.toDouble()).toFloat() else curCfg.videoAntiHashPercent,
                audioAntiHashPercent = if (options.includeAudios) vcamObj.optDouble("audioAntiHashPercent", curCfg.audioAntiHashPercent.toDouble()).toFloat() else curCfg.audioAntiHashPercent,
                isAudioLayer2SyncAntiHash = if (options.includeAudios) vcamObj.optBoolean("isAudioLayer2SyncAntiHash", curCfg.isAudioLayer2SyncAntiHash) else curCfg.isAudioLayer2SyncAntiHash,
                isDnaZoomEnabled = if (options.includeCameraVisual) vcamObj.optBoolean("isDnaZoomEnabled", curCfg.isDnaZoomEnabled) else curCfg.isDnaZoomEnabled,
                sessionDnaCustomZoom = if (options.includeCameraVisual) vcamObj.optDouble("sessionDnaCustomZoom", curCfg.sessionDnaCustomZoom.toDouble()).toFloat() else curCfg.sessionDnaCustomZoom,
                sessionDnaPanX = if (options.includeCameraVisual) vcamObj.optDouble("sessionDnaPanX", curCfg.sessionDnaPanX.toDouble()).toFloat() else curCfg.sessionDnaPanX,
                sessionDnaPanY = if (options.includeCameraVisual) vcamObj.optDouble("sessionDnaPanY", curCfg.sessionDnaPanY.toDouble()).toFloat() else curCfg.sessionDnaPanY,
                usedDnaZoomHistory = if (options.includeCameraVisual) {
                    val zHist = mutableSetOf<String>()
                    vcamObj.optJSONArray("usedDnaZoomHistory")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val tag = arr.optString(i, "")
                            if (tag.isNotBlank()) zHist.add(tag)
                        }
                    }
                    if (zHist.isNotEmpty()) zHist else curCfg.usedDnaZoomHistory
                } else curCfg.usedDnaZoomHistory
            )

            // Terapkan VcamConfig ke Repository
            repo.updateConfig { restoredCfg }
            repo.bumpPlaylistVersion()
            repo.bumpPlaybackStepVersion()

            // Pulihkan Efek Suara & Sinkronisasi Audio Layer 2
            if (options.includeAudios) {
                withContext(Dispatchers.Main) {
                    repo.musicPlayer.setVoiceEffect(restoredCfg.voiceEffect)
                    repo.musicPlayer.setAudioLayer2SyncAntiHash(restoredCfg.isAudioLayer2SyncAntiHash)
                    repo.musicPlayer.setAudioAntiHash(restoredCfg.isAudioAntiHashEnabled, restoredCfg.audioAntiHashMode, restoredCfg.audioAntiHashPercent)
                    repo.musicPlayer.updateLayer3Config(
                        enabled = restoredCfg.isAudioLayer3Enabled,
                        ringtoneIds = restoredCfg.audioLayer3Ids,
                        volume = restoredCfg.audioLayer3Volume,
                        isMuted = restoredCfg.isAudioLayer3Muted,
                        minIntervalSec = restoredCfg.audioLayer3MinIntervalSec,
                        maxIntervalSec = restoredCfg.audioLayer3MaxIntervalSec
                    )
                }
            }

            // Pulihkan Volume Video & Musik
            if (options.includeVideos && vcamObj.has("videoVolume")) {
                repo.setVideoVolume(vcamObj.optDouble("videoVolume", 1.0).toFloat())
            }
            if (options.includeAudios && vcamObj.has("audioVolume")) {
                repo.setAudioVolume(vcamObj.optDouble("audioVolume", 1.0).toFloat())
            }
            if (options.includeAudios) {
                repo.musicPlayer.setAudioAntiHash(restoredCfg.isAudioAntiHashEnabled, restoredCfg.audioAntiHashMode)
            }

            // 4. Pulihkan Floating Overlays (Watermark, Stiker, Notif Beli, Komentar, Auto-Tap)
            val floatingJson = root.optString("floatingJson", "")
            if (floatingJson.isNotBlank() && floatingJson != "{}") {
                FloatingConfigBackupManager.importConfigFromJson(
                    context = appContext,
                    jsonStr = floatingJson,
                    options = options
                )
            }

            // 4.5. Reset state pemutar musik sesi sebelumnya sebelum mengimpor IFS & audio baru
            if (options.includeAudios) {
                withContext(Dispatchers.Main) {
                    repo.musicPlayer.resetSessionAudioState()
                }
            }

            // 5. Pulihkan Infinity System (IFS SV1/SV2/SV3, VTM, VT1..n, VL/VB, AX, SR, SC) dengan Auto-Relink ID Media
            if (options.includeVideos || options.includeAudios) {
                root.optJSONObject("ifsSnapshot")?.let { ifsObj ->
                    val healedIfsObj = healIfsSnapshotMediaIds(
                        ifsObj = ifsObj,
                        rawVideos = rawVideos,
                        restoredVideos = finalVideos,
                        rawAudioJsonStr = rawAudioJsonStr,
                        restoredAudios = finalAudios
                    )
                    withContext(Dispatchers.Main) {
                        ifsMgr.importFromJson(healedIfsObj)
                        ifsMgr.onMediaPlaylistChanged(finalVideos, finalAudios, broadcastIfActive = false)
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        ifsMgr.onMediaPlaylistChanged(finalVideos, finalAudios, broadcastIfActive = false)
                    }
                }
            }

            // 6. Sinkronkan pemutar musik & kamera virtual hook secara instan
            if (options.includeAudios) {
                withContext(Dispatchers.Main) {
                    if (finalActiveAud != null && !ifsMgr.isIfsActive.value) {
                        val shouldPlayAudio = !restoredCfg.isMp3AudioPaused
                        repo.musicPlayer.playAudioItem(finalActiveAud, startPlaying = shouldPlayAudio)
                    } else if (finalActiveAud == null) {
                        repo.musicPlayer.resetSessionAudioState()
                    }
                }
            }

            val finalCfg = repo.config.value
            val allHookDirs = repo.getAllHookDirectories()
            MediaFileStorageHelper.cleanupStaleIndexedVideos(allHookDirs, finalCfg.playlist.size)
            val validPaths = (finalCfg.playlist.map { it.path } + finalCfg.audioPlaylist.map { it.path }).flatMap { p ->
                val f = File(p)
                listOf(p, f.absolutePath, try { f.canonicalPath } catch (_: Exception) { p })
            }.toSet() + getAllProtectedMediaPaths()
            val validFileNames = (finalCfg.playlist.map { File(it.path).name } + finalCfg.audioPlaylist.map { File(it.path).name }).toSet() + getAllProtectedMediaFileNames()
            MediaFileStorageHelper.cleanupOrphanedMediaFiles(
                hookDirs = allHookDirs,
                validMediaPaths = validPaths,
                validFileNames = validFileNames,
                validCount = finalCfg.playlist.size
            )

            repo.writeHookConfigFiles(finalCfg)
            val activeVidForHook = finalCfg.currentVideo
            if (activeVidForHook != null && File(activeVidForHook.path).exists()) {
                repo.copyToAllLocations(File(activeVidForHook.path), "virtual.mp4")
                repo.syncToTargetApps(finalCfg.targetPackages)
                repo.broadcastControlEvent(
                    action = "SELECT_VIDEO",
                    videoPath = activeVidForHook.path,
                    isVideoPaused = false,
                    isVideoAudioMuted = activeVidForHook.isMuted,
                    extraSource = "CONCEPT_RESTORED",
                    extraPurgeCache = true
                )
            } else {
                repo.cleanupStaleVirtualVideo()
                repo.syncToTargetApps(finalCfg.targetPackages)
                repo.broadcastControlEvent(
                    action = "RELOAD_MEDIA",
                    videoPath = "",
                    extraSource = "CONCEPT_RESTORED",
                    extraPurgeCache = true
                )
            }

            val now = System.currentTimeMillis()
            val resultProfile = captureCurrentStateSnapshot(
                id = profileId ?: AUTO_CHECKPOINT_ID,
                name = profileName,
                createdAt = root.optLong("savedAt", now),
                updatedAt = now
            )
            Result.success(resultProfile)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulihkan snapshot $profileName: ${e.message}", e)
            Result.failure(e)
        } finally {
            isRestoringState.set(false)
        }
    }

    private fun isPrefEnabledByOptions(prefName: String, options: BackupChecklistOptions): Boolean {
        return when (prefName) {
            "tajasb_autotap_prefs" -> options.includeAutoTapChoreo
            "custom_stiker_prefs",
            "custom_watermark_prefs",
            "buyer_notif_prefs",
            "auto_time_capsule_prefs",
            "live_comment_prefs",
            "vcam_screen_anchors" -> options.includeOverlays
            "vcam_floating_prefs" -> options.includeFloatingLayout
            "tajasb_telegram_screen_prefs" -> options.includeCameraVisual || options.includeFloatingLayout
            "ifs_settings_v2" -> options.includeVideos || options.includeAudios
            "vcam_settings" -> options.includeCameraVisual || options.includeVideos || options.includeAudios || options.includeFloatingLayout || options.includeOverlays || options.includeAutoTapChoreo
            else -> false
        }
    }

    /**
     * Mengekspor 100% seluruh SharedPreferences fungsional aplikasi ke dalam 1 JSONObject master
     * agar koordinat floating, screen anchor penonton, koreografi Auto-Tap (urut/acak), konfigurasi per-aplikasi,
     * dan preferensi lintas modul ikut tercadangkan secara utuh.
     */
    private fun exportAllPreferencesMasterJson(
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): JSONObject {
        val prefNames = ALLOWED_BACKUP_PREF_NAMES
        val master = JSONObject()
        for (name in prefNames) {
            if (!isPrefEnabledByOptions(name, options)) continue
            try {
                val sp = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                val allEntries = sp.all
                val prefObj = JSONObject()
                for ((key, value) in allEntries) {
                    if (key.isNullOrBlank() || value == null) continue
                    val entryObj = JSONObject()
                    when (value) {
                        is Boolean -> {
                            entryObj.put("t", "bool")
                            entryObj.put("v", value)
                        }
                        is Int -> {
                            entryObj.put("t", "int")
                            entryObj.put("v", value)
                        }
                        is Long -> {
                            entryObj.put("t", "long")
                            entryObj.put("v", value)
                        }
                        is Float -> {
                            entryObj.put("t", "float")
                            entryObj.put("v", value.toDouble())
                        }
                        is String -> {
                            entryObj.put("t", "string")
                            val exportedStr = if (com.tajasb.security.LocalVaultCrypto.isEncryptedPayload(value)) {
                                com.tajasb.security.LocalVaultCrypto.rewrapForBackup(value)
                            } else {
                                value
                            }
                            entryObj.put("v", exportedStr)
                        }
                        is Set<*> -> {
                            entryObj.put("t", "set")
                            val arr = JSONArray()
                            value.forEach { elem -> if (elem is String) arr.put(elem) }
                            entryObj.put("v", arr)
                        }
                    }
                    if (entryObj.has("t")) {
                        prefObj.put(key, entryObj)
                    }
                }
                master.put(name, prefObj)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mengekspor SharedPreferences $name: ${e.message}")
            }
        }
        return master
    }

    /**
     * Memulihkan seluruh SharedPreferences dari JSONObject master dengan Strict Whitelist
     * (mencegah injeksi ke tajasb_lic_vault, tajasb_time_vault, tajasb_totp_prefs, tajasb_remote_prefs, dll.).
     */
    private fun importAllPreferencesMasterJson(
        master: JSONObject,
        options: BackupChecklistOptions = BackupChecklistOptions()
    ) {
        val skipVolatileKeys = setOf(
            "playlist_json",
            "audio_playlist_json",
            "video_path",
            "audio_path",
            "sv4_current_video_json",
            "sv4_next_video_json",
            "sv4_current_recipe_json",
            "sv4_next_recipe_json",
            "sv4_audio_current_item_json",
            "sv4_audio_next_item_json",
            "sv4_audio_current_recipe_json",
            "sv4_audio_next_recipe_json",
            "ifs_l_video_ids_json",
            "ifs_sv4_audio_l_ids_json"
        )
        val keys = master.keys()
        while (keys.hasNext()) {
            val prefName = keys.next()
            // KEAMANAN MUTLAK: Hanya izinkan nama SharedPreferences yang masuk daftar putih (Strict Whitelist)
            if (!isBackupPreferenceAllowed(prefName)) {
                Log.w(TAG, "🚨 Menolak injeksi SharedPreferences tidak sah dari file backup: $prefName")
                continue
            }
            if (!isPrefEnabledByOptions(prefName, options)) continue
            val prefObj = master.optJSONObject(prefName) ?: continue
            try {
                val sp = appContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val editor = sp.edit()
                val entryKeys = prefObj.keys()
                while (entryKeys.hasNext()) {
                    val k = entryKeys.next()
                    if (skipVolatileKeys.contains(k)) continue
                    val entryObj = prefObj.optJSONObject(k) ?: continue
                    when (entryObj.optString("t", "")) {
                        "bool" -> editor.putBoolean(k, entryObj.optBoolean("v", false))
                        "int" -> editor.putInt(k, entryObj.optInt("v", 0))
                        "long" -> editor.putLong(k, entryObj.optLong("v", 0L))
                        "float" -> editor.putFloat(k, entryObj.optDouble("v", 0.0).toFloat())
                        "string" -> editor.putString(k, entryObj.optString("v", ""))
                        "set" -> {
                            val arr = entryObj.optJSONArray("v")
                            if (arr != null) {
                                val set = mutableSetOf<String>()
                                for (i in 0 until arr.length()) {
                                    set.add(arr.optString(i, ""))
                                }
                                editor.putStringSet(k, set)
                            }
                        }
                    }
                }
                editor.apply()
            } catch (e: Exception) {
                Log.w(TAG, "Gagal memulihkan SharedPreferences $prefName: ${e.message}")
            }
        }
    }

    /**
     * Menyembuhkan (auto-relink) seluruh referensi ID Video & Audio di dalam snapshot IFS
     * (mencakup SV1 ABA/VT, SV2 PM, dan SV3 VL & Grup VB, serta AX & SR)
     * apabila terjadi pencocokan berdasarkan nama bersih pasca reset HP.
     */
    private fun healIfsSnapshotMediaIds(
        ifsObj: JSONObject,
        rawVideos: List<VideoItem>,
        restoredVideos: List<VideoItem>,
        rawAudioJsonStr: String,
        restoredAudios: List<AudioItem>
    ): JSONObject {
        return try {
            val cloned = JSONObject(ifsObj.toString())
            val rawAudioNameById = mutableMapOf<String, String>()
            try {
                val aArr = JSONArray(rawAudioJsonStr)
                for (i in 0 until aArr.length()) {
                    val o = aArr.optJSONObject(i) ?: continue
                    val id = o.optString("id", "")
                    val nm = o.optString("name", "")
                    if (id.isNotBlank() && nm.isNotBlank()) {
                        rawAudioNameById[id] = normalizeCleanFileName(nm, ".mp3")
                    }
                }
            } catch (_: Exception) {}

            fun healVidId(rawId: String?): String {
                if (rawId.isNullOrBlank()) return ""
                if (restoredVideos.any { it.id == rawId }) return rawId
                val orig = rawVideos.firstOrNull { it.id == rawId }
                if (orig != null) {
                    val clean = normalizeCleanFileName(orig.name, ".mp4")
                    val matched = restoredVideos.firstOrNull { it.name.equals(clean, ignoreCase = true) }
                    if (matched != null) return matched.id
                }
                return rawId
            }

            fun healAudId(rawId: String?): String {
                if (rawId.isNullOrBlank()) return ""
                if (restoredAudios.any { it.id == rawId }) return rawId
                val clean = rawAudioNameById[rawId]
                if (!clean.isNullOrBlank()) {
                    val matched = restoredAudios.firstOrNull { it.name.equals(clean, ignoreCase = true) }
                    if (matched != null) return matched.id
                }
                return rawId
            }

            fun healPkgObj(pkgObj: JSONObject) {
                if (pkgObj.has("abbVideoId")) pkgObj.put("abbVideoId", healVidId(pkgObj.optString("abbVideoId", "")))
                if (pkgObj.has("blVideoId")) pkgObj.put("blVideoId", healVidId(pkgObj.optString("blVideoId", "")))
                if (pkgObj.has("bbtVideoId")) pkgObj.put("bbtVideoId", healVidId(pkgObj.optString("bbtVideoId", "")))
                if (pkgObj.has("linkedAudioId")) pkgObj.put("linkedAudioId", healAudId(pkgObj.optString("linkedAudioId", "")))
            }

            cloned.optJSONObject("vtmPackage")?.let { healPkgObj(it) }
            cloned.optJSONArray("vtPackages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { healPkgObj(it) }
                }
            }

            if (cloned.has("sv3VlVideoId")) {
                cloned.put("sv3VlVideoId", healVidId(cloned.optString("sv3VlVideoId", "")))
            }
            cloned.optJSONArray("sv3VbVideoIds")?.let { vbArr ->
                val newVbArr = JSONArray()
                for (i in 0 until vbArr.length()) {
                    val healed = healVidId(vbArr.optString(i, ""))
                    if (healed.isNotBlank()) newVbArr.put(healed)
                }
                cloned.put("sv3VbVideoIds", newVbArr)
            }
            cloned.optJSONArray("sv4LVideoIds")?.let { sv4Arr ->
                val newSv4Arr = JSONArray()
                for (i in 0 until sv4Arr.length()) {
                    val healed = healVidId(sv4Arr.optString(i, ""))
                    if (healed.isNotBlank()) newSv4Arr.put(healed)
                }
                cloned.put("sv4LVideoIds", newSv4Arr)
            }
            cloned.optJSONArray("sv4AudioLIds")?.let { sv4AArr ->
                val newSv4AArr = JSONArray()
                for (i in 0 until sv4AArr.length()) {
                    val healed = healAudId(sv4AArr.optString(i, ""))
                    if (healed.isNotBlank()) newSv4AArr.put(healed)
                }
                cloned.put("sv4AudioLIds", newSv4AArr)
            }
            fun healRecipeObj(recObj: JSONObject) {
                val segArr = recObj.optJSONArray("segments") ?: return
                for (i in 0 until segArr.length()) {
                    val sObj = segArr.optJSONObject(i) ?: continue
                    if (sObj.has("videoId")) {
                        sObj.put("videoId", healVidId(sObj.optString("videoId", "")))
                    }
                    if (sObj.has("audioId")) {
                        sObj.put("audioId", healAudId(sObj.optString("audioId", "")))
                    }
                }
            }
            cloned.optJSONObject("sv4CurrentRecipe")?.let { healRecipeObj(it) }
            cloned.optJSONObject("sv4NextRecipe")?.let { healRecipeObj(it) }
            cloned.optJSONObject("sv4AudioCurrentRecipe")?.let { healRecipeObj(it) }
            cloned.optJSONObject("sv4AudioNextRecipe")?.let { healRecipeObj(it) }
            cloned.optJSONArray("srAudioIds")?.let { srArr ->
                val newSrArr = JSONArray()
                for (i in 0 until srArr.length()) {
                    val healed = healAudId(srArr.optString(i, ""))
                    if (healed.isNotBlank()) newSrArr.put(healed)
                }
                cloned.put("srAudioIds", newSrArr)
            }
            cloned
        } catch (_: Exception) {
            ifsObj
        }
    }

    /**
     * Mencari file media di path asli -> nama file persis (hash unik) di Camera1 -> nama file persis di Custom Vault ->
     * baru fallback ke nama bersih jika perlu.
     * Menjamin Proyek 2 yang punya nama bersih sama (mis. 1.mp4) tetapi hash berbeda tidak pernah salah mengambil video Proyek 1!
     */
    private fun resolveAndHealMediaFile(
        rawPath: String,
        rawName: String,
        defaultExt: String,
        internalVaultDir: File,
        customMediaDir: File
    ): File? {
        val cleanTarget = normalizeCleanFileName(rawName, defaultExt)
        val rawFileName = File(rawPath).name
        val directFile = File(rawPath)

        // 1. Cek di lokasi langsung (jika di internal/valid)
        if (rawPath.isNotBlank() && directFile.exists() && directFile.length() > 0L) {
            return directFile
        }

        if (!internalVaultDir.exists()) internalVaultDir.mkdirs()
        val internalFiles = internalVaultDir.listFiles()?.filter {
            it.isFile && it.length() > 0L && !it.name.startsWith("vs_rolling_") && !it.name.startsWith("vs_audio_rolling_")
        }.orEmpty()

        // 2. Prioritas Utama: Cocokkan NAMA FILE PERSIS (termasuk hash unik vault_vid_<hash>_...) di internal Camera1
        if (rawFileName.isNotBlank()) {
            val exactInternal = internalFiles.firstOrNull { f ->
                f.name.equals(rawFileName, ignoreCase = true)
            }
            if (exactInternal != null) return exactInternal
        }

        // 3. Prioritas Kedua: Cocokkan NAMA FILE PERSIS di Custom Local Vault media_vault/ (agar video spesifik proyek ini tersalin utuh)
        val externalFiles = if (customMediaDir.exists()) {
            customMediaDir.listFiles()?.filter {
                it.isFile && it.length() > 0L && !it.name.startsWith("vs_rolling_") && !it.name.startsWith("vs_audio_rolling_")
            }.orEmpty()
        } else emptyList()

        if (rawFileName.isNotBlank()) {
            val exactExternal = externalFiles.firstOrNull { f ->
                f.name.equals(rawFileName, ignoreCase = true)
            }
            if (exactExternal != null) {
                return try {
                    val healedFile = File(internalVaultDir, exactExternal.name)
                    if (!healedFile.exists() || healedFile.length() != exactExternal.length()) {
                        exactExternal.copyTo(healedFile, overwrite = true)
                        healedFile.setReadable(true, false)
                        healedFile.setWritable(true, false)
                    }
                    healedFile
                } catch (_: Exception) {
                    exactExternal
                }
            }
        }

        // 4. Fallback nama bersih di internal Camera1 (hanya jika nama file persis ber-hash tidak ditemukan di mana pun)
        val cleanInternal = internalFiles.firstOrNull { f ->
            normalizeCleanFileName(f.name, defaultExt).equals(cleanTarget, ignoreCase = true)
        }
        if (cleanInternal != null) return cleanInternal

        // 5. Fallback nama bersih di Custom Local Vault media_vault/
        val cleanExternal = externalFiles.firstOrNull { f ->
            normalizeCleanFileName(f.name, defaultExt).equals(cleanTarget, ignoreCase = true)
        }
        if (cleanExternal != null) {
            return try {
                val healedFile = File(internalVaultDir, cleanExternal.name)
                if (!healedFile.exists() || healedFile.length() != cleanExternal.length()) {
                    cleanExternal.copyTo(healedFile, overwrite = true)
                    healedFile.setReadable(true, false)
                    healedFile.setWritable(true, false)
                }
                healedFile
            } catch (_: Exception) {
                cleanExternal
            }
        }
        return null
    }

    // =========================================================================
    // EKSPOR & IMPOR PAKET LENGKAP 1-FILE (.ZIP) UNTUK GOOGLE DRIVE / LOKAL
    // =========================================================================

    /**
     * Membangun 1 file arsip `.zip` lengkap (`LiveHost_FullBackup_<Timestamp>.zip`) yang berisi:
     * 1. `backup_manifest.json`
     * 2. `concepts_vault_db.json` (Seluruh Konsep Tersimpan)
     * 3. `auto_checkpoint_state.json` (Kondisi Aktif Saat Ini)
     * 4. `media_vault/` (Seluruh file fisik Video MP4 & Audio MP3 tanpa duplikat)
     */
    suspend fun buildFullBackupZipFile(
        targetZipFile: File? = null,
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): Result<File> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            try {
                _isBusy.value = true
                _busyProgressText.value = "Menyiapkan database & sesi aktif..."

                // Pastikan sesi terakhir dan konsep sudah paling mutakhir
                saveAutoCheckpointInternal()
                writeConceptsDbToCustomVault(_concepts.value, _activeConceptId.value)

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                val zipFile = targetZipFile ?: run {
                    val exportDir = File(appContext.cacheDir, "backup_exports")
                    if (!exportDir.exists()) exportDir.mkdirs()
                    // Bersihkan file zip sementara lama di cache agar hemat ruang
                    exportDir.listFiles()?.forEach { old ->
                        if (old.isFile && old.name.endsWith(".zip", true)) {
                            try { old.delete() } catch (_: Exception) {}
                        }
                    }
                    File(exportDir, "LiveHost_FullBackup_$timestamp.zip")
                }

                // Kumpulkan seluruh file fisik Video & Audio yang dipakai di state aktif maupun di semua Konsep Tersimpan
                val repo = VcamRepository.getInstance(appContext)
                val cfg = repo.config.value
                val internalVaultDir = repo.getInternalCamera1Directory()
                val customMediaDir = getCustomMediaVaultDir()

                val uniqueMediaByEntryName = linkedMapOf<String, File>()

                fun registerMediaFile(rawPath: String, rawName: String, isAudio: Boolean) {
                    if (isAudio && !options.includeAudios) return
                    if (!isAudio && !options.includeVideos) return
                    val defaultExt = if (isAudio) ".mp3" else ".mp4"
                    val resolved = resolveAndHealMediaFile(
                        rawPath = rawPath,
                        rawName = rawName,
                        defaultExt = defaultExt,
                        internalVaultDir = internalVaultDir,
                        customMediaDir = customMediaDir
                    ) ?: return
                    if (!resolved.exists() || resolved.length() <= 0L) return

                    val cleanName = normalizeCleanFileName(rawName.ifBlank { resolved.name }, defaultExt)
                    val entryFileName = if (resolved.name.startsWith("vault_vid_") || resolved.name.startsWith("vault_aud_")) {
                        resolved.name
                    } else {
                        val hash = computeFastFileHash(resolved)
                        val prefix = if (isAudio) "vault_aud" else "vault_vid"
                        if (hash.isNotEmpty()) "${prefix}_${hash}_$cleanName" else "${prefix}_${resolved.length()}_$cleanName"
                    }
                    if (!uniqueMediaByEntryName.containsKey(entryFileName)) {
                        uniqueMediaByEntryName[entryFileName] = resolved
                    }
                }

                // A. Dari playlist aktif saat ini
                if (options.includeVideos) {
                    cfg.playlist.forEach { registerMediaFile(it.path, it.name, isAudio = false) }
                }
                if (options.includeAudios) {
                    cfg.audioPlaylist.forEach { registerMediaFile(it.path, it.name, isAudio = true) }
                }

                // B. Dari seluruh konsep yang tersimpan
                for (concept in _concepts.value) {
                    try {
                        val root = JSONObject(concept.snapshotJson)
                        if (options.includeVideos) {
                            val vItems = VideoPlaylistRepository.parsePlaylistFromJson(root.optString("videoPlaylistJson", "[]"))
                            vItems.forEach { registerMediaFile(it.path, it.name, isAudio = false) }
                        }

                        if (options.includeAudios) {
                            val aArr = JSONArray(root.optString("audioPlaylistJson", "[]"))
                            for (i in 0 until aArr.length()) {
                                val o = aArr.optJSONObject(i) ?: continue
                                registerMediaFile(o.optString("path", ""), o.optString("name", "audio.mp3"), isAudio = true)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Tulis ke ZipOutputStream dengan Deflater.BEST_SPEED (karena MP4/MP3 sudah terkompresi)
                ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile), 128 * 1024)).use { zos ->
                    zos.setLevel(Deflater.BEST_SPEED)

                    // 1. Tulis backup_manifest.json
                    val manifestJson = JSONObject().apply {
                        put("schemaVersion", 1)
                        put("appName", "Host Live")
                        put("createdAt", System.currentTimeMillis())
                        put("conceptCount", _concepts.value.size)
                        put("mediaFileCount", uniqueMediaByEntryName.size)
                        put("activeConceptId", _activeConceptId.value ?: "")
                        put("checklistOptions", JSONObject().apply {
                            put("includeVideos", options.includeVideos)
                            put("includeAudios", options.includeAudios)
                            put("includeAutoTapChoreo", options.includeAutoTapChoreo)
                            put("includeOverlays", options.includeOverlays)
                            put("includeCameraVisual", options.includeCameraVisual)
                            put("includeFloatingLayout", options.includeFloatingLayout)
                        })
                    }
                    zos.putNextEntry(ZipEntry("backup_manifest.json"))
                    zos.write(manifestJson.toString(2).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 2. Tulis concepts_vault_db.json
                    val conceptsDbJson = buildConceptsDbJson(_concepts.value, _activeConceptId.value)
                    zos.putNextEntry(ZipEntry(VAULT_DB_FILE_NAME))
                    zos.write(conceptsDbJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 3. Tulis auto_checkpoint_state.json
                    val now = System.currentTimeMillis()
                    val activeSnapshot = captureCurrentStateSnapshot(
                        id = AUTO_CHECKPOINT_ID,
                        name = "Sesi Aktif Backup ZIP",
                        createdAt = now,
                        updatedAt = now,
                        options = options
                    )
                    val cpWrapper = JSONObject().apply {
                        put("schemaVersion", 2)
                        put("savedAt", now)
                        put("activeConceptId", _activeConceptId.value ?: "")
                        put("profile", serializeConceptProfileToJson(activeSnapshot))
                    }
                    zos.putNextEntry(ZipEntry(AUTO_CHECKPOINT_FILE_NAME))
                    zos.write(cpWrapper.toString().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 3b. Tulis all_preferences_master.json (Cadangan 100% seluruh SharedPreferences sesuai ceklis)
                    val masterPrefsJson = exportAllPreferencesMasterJson(options)
                    zos.putNextEntry(ZipEntry(ALL_PREFS_MASTER_FILE_NAME))
                    zos.write(masterPrefsJson.toString().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 4. Tulis seluruh file fisik Video MP4 & Audio MP3 ke media_vault/
                    val entriesList = uniqueMediaByEntryName.entries.toList()
                    val buf = ByteArray(128 * 1024)
                    entriesList.forEachIndexed { index, (entryName, srcFile) ->
                        _busyProgressText.value = "Mengemas media (${index + 1}/${entriesList.size}): ${normalizeCleanFileName(entryName)}"
                        zos.putNextEntry(ZipEntry("$MEDIA_VAULT_SUBDIR/$entryName"))
                        BufferedInputStream(FileInputStream(srcFile), 128 * 1024).use { bis ->
                            var read: Int
                            while (bis.read(buf).also { read = it } != -1) {
                                zos.write(buf, 0, read)
                            }
                        }
                        zos.closeEntry()
                    }
                }

                // Simpan salinan ZIP terbaru ke dalam Custom Local Vault juga agar mudah ditemukan di File Manager
                try {
                    val localVaultCopy = File(getResolvedCustomVaultDir(), zipFile.name)
                    if (localVaultCopy.absolutePath != zipFile.absolutePath) {
                        zipFile.copyTo(localVaultCopy, overwrite = true)
                    }
                } catch (_: Exception) {}

                Result.success(zipFile)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal membuat paket ZIP backup: ${e.message}", e)
                Result.failure(e)
            } finally {
                _busyProgressText.value = null
                _isBusy.value = false
            }
        }
    }

    /**
     * Mengekspor paket lengkap `.zip` langsung ke URI tujuan yang dipilih pengguna
     * (melalui `ActivityResultContracts.CreateDocument("application/zip")`, bisa langsung pilih Google Drive atau Folder HP).
     */
    suspend fun exportFullBackupZipToUri(
        destUri: Uri,
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): Result<String> = withContext(Dispatchers.IO) {
        val zipRes = buildFullBackupZipFile(options = options)
        val zipFile = zipRes.getOrElse { return@withContext Result.failure(it) }

        return@withContext try {
            _isBusy.value = true
            _busyProgressText.value = "Menyimpan paket ZIP ke lokasi tujuan..."
            appContext.contentResolver.openOutputStream(destUri)?.use { out ->
                FileInputStream(zipFile).use { input ->
                    input.copyTo(out, bufferSize = 128 * 1024)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Gagal membuka lokasi penyimpanan tujuan"))

            val sizeMb = String.format(Locale.US, "%.1f MB", zipFile.length().toDouble() / (1024.0 * 1024.0))
            Result.success("✅ Paket Backup Lengkap ($sizeMb, ${options.selectedCount}/6 kategori) berhasil disimpan!")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengekspor ZIP ke URI: ${e.message}", e)
            Result.failure(e)
        } finally {
            _busyProgressText.value = null
            _isBusy.value = false
        }
    }

    /**
     * Menyiapkan paket `.zip` lengkap dan membuat Intent Share (`ACTION_SEND`)
     * agar pengguna bisa langsung membagikan / mengunggah ke **Google Drive**, Telegram, atau WhatsApp dalam 1 klik.
     */
    suspend fun prepareShareBackupZipIntent(
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): Result<Intent> = withContext(Dispatchers.IO) {
        val zipRes = buildFullBackupZipFile(options = options)
        val zipFile = zipRes.getOrElse { return@withContext Result.failure(it) }

        return@withContext try {
            val contentUri = Uri.parse("content://${VcamContentProvider.AUTHORITY}/${zipFile.name}")
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, zipFile.name)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Cadangan Paket Lengkap Host Live (${_concepts.value.size} Konsep + Video MP4 + Audio MP3 + Koreografi Auto-Tap + Stiker & Pengaturan)"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Simpan / Upload Backup ke Google Drive").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Result.success(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyiapkan intent share backup ZIP: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Mengimpor paket lengkap `.zip` (dari Google Drive atau penyimpanan HP) dalam 1 klik:
     * 1. Mengekstrak seluruh file fisik Video MP4 & Audio MP3 ke `Camera1` internal DAN Custom Local Vault `media_vault/`.
     * 2. Memulihkan seluruh daftar Konsep (`concepts_vault_db.json`).
     * 3. Memulihkan sesi aktif (`auto_checkpoint_state.json` atau Konsep Aktif) sehingga Video, MP3, IFS, Koreografi Auto-Tap, Stiker, Watermark, dan Pengaturan langsung siap Live.
     */
    suspend fun importFullBackupZipFromUri(
        sourceUri: Uri,
        options: BackupChecklistOptions = BackupChecklistOptions()
    ): Result<ZipImportReport> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            try {
                _isBusy.value = true
                _busyProgressText.value = "Memverifikasi integritas paket backup (.zip)..."

                val repo = VcamRepository.getInstance(appContext)
                val internalVaultDir = repo.getInternalCamera1Directory()
                if (!internalVaultDir.exists()) internalVaultDir.mkdirs()
                val customMediaDir = getCustomMediaVaultDir()

                // 0. Pre-Scan Integritas: Validasi bahwa berkas ZIP lengkap dan tidak korup/terpotong
                var validEntryCount = 0
                var hasValidManifestOrDb = false
                try {
                    val preCheckStream = appContext.contentResolver.openInputStream(sourceUri)
                        ?: return@withLock Result.failure(IllegalArgumentException("File backup tidak dapat dibuka"))
                    preCheckStream.use { rawIn ->
                        ZipInputStream(BufferedInputStream(rawIn, 64 * 1024)).use { zis ->
                            var pe = zis.nextEntry
                            while (pe != null) {
                                validEntryCount++
                                val pName = pe.name ?: ""
                                if (pName == VAULT_DB_FILE_NAME || pName.endsWith("/$VAULT_DB_FILE_NAME") ||
                                    pName == AUTO_CHECKPOINT_FILE_NAME || pName.endsWith("/$AUTO_CHECKPOINT_FILE_NAME") ||
                                    pName == ALL_PREFS_MASTER_FILE_NAME || pName.endsWith("/$ALL_PREFS_MASTER_FILE_NAME") ||
                                    pName == "backup_manifest.json"
                                ) {
                                    hasValidManifestOrDb = true
                                }
                                zis.closeEntry()
                                pe = zis.nextEntry
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Verifikasi integritas ZIP gagal: ${e.message}")
                    return@withLock Result.failure(
                        IllegalStateException("Berkas .ZIP tidak lengkap atau corrupt (unduhan Google Drive mungkin terputus). Silakan pastikan berkas terunduh 100% lalu coba lagi.")
                    )
                }

                if (validEntryCount == 0 || !hasValidManifestOrDb) {
                    return@withLock Result.failure(
                        IllegalArgumentException("Berkas bukan arsip backup Host Live yang valid atau file kosong.")
                    )
                }

                _busyProgressText.value = "Membaca paket backup ($validEntryCount item terverifikasi)..."

                var extractedMediaCount = 0
                var conceptsDbJsonStr: String? = null
                var autoCheckpointJsonStr: String? = null
                var allPrefsMasterJsonStr: String? = null

                val inputStream = appContext.contentResolver.openInputStream(sourceUri)
                    ?: return@withLock Result.failure(IllegalArgumentException("File backup tidak dapat dibuka"))

                inputStream.use { rawIn ->
                    ZipInputStream(BufferedInputStream(rawIn, 128 * 1024)).use { zis ->
                        val buf = ByteArray(128 * 1024)
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            val rawEntryName = entry.name ?: ""
                            // Keamanan: cegah zip-slip
                            if (!entry.isDirectory && !rawEntryName.contains("..")) {
                                when {
                                    rawEntryName == VAULT_DB_FILE_NAME || rawEntryName.endsWith("/$VAULT_DB_FILE_NAME") -> {
                                        conceptsDbJsonStr = zis.readBytes().toString(Charsets.UTF_8)
                                    }
                                    rawEntryName == AUTO_CHECKPOINT_FILE_NAME || rawEntryName.endsWith("/$AUTO_CHECKPOINT_FILE_NAME") -> {
                                        autoCheckpointJsonStr = zis.readBytes().toString(Charsets.UTF_8)
                                    }
                                    rawEntryName == ALL_PREFS_MASTER_FILE_NAME || rawEntryName.endsWith("/$ALL_PREFS_MASTER_FILE_NAME") -> {
                                        allPrefsMasterJsonStr = zis.readBytes().toString(Charsets.UTF_8)
                                    }
                                    rawEntryName.startsWith("$MEDIA_VAULT_SUBDIR/") ||
                                        isMediaFileExtension(rawEntryName) -> {
                                        val fileNameOnly = File(rawEntryName).name
                                        val isAudioEntry = isAudioFileName(fileNameOnly)
                                        val shouldExtract = if (isAudioEntry) options.includeAudios else options.includeVideos
                                        if (fileNameOnly.isNotBlank() && shouldExtract) {
                                            extractedMediaCount++
                                            _busyProgressText.value = "Mengekstrak media #$extractedMediaCount: ${normalizeCleanFileName(fileNameOnly)}"

                                            val internalTarget = File(internalVaultDir, fileNameOnly)
                                            FileOutputStream(internalTarget).use { fos ->
                                                var read: Int
                                                while (zis.read(buf).also { read = it } != -1) {
                                                    fos.write(buf, 0, read)
                                                }
                                            }
                                            internalTarget.setReadable(true, false)
                                            internalTarget.setWritable(true, false)

                                            // Salin juga ke Custom Local Vault media_vault/
                                            try {
                                                val customTarget = File(customMediaDir, fileNameOnly)
                                                if (customTarget.absolutePath != internalTarget.absolutePath) {
                                                    internalTarget.copyTo(customTarget, overwrite = true)
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                // 0. Pulihkan all_preferences_master.json jika tersedia di dalam ZIP
                if (!allPrefsMasterJsonStr.isNullOrBlank()) {
                    try {
                        importAllPreferencesMasterJson(JSONObject(allPrefsMasterJsonStr!!), options)
                        if (options.includeOverlays) {
                            com.tajasb.livecomment.ViewerAnchorManager.getInstance(appContext).reloadFromPrefs()
                        }
                        if (options.includeAutoTapChoreo) {
                            com.tajasb.autotap.AutoTapManager.getInstance(appContext).reloadFromPrefs()
                        }
                    } catch (_: Exception) {}
                }

                // 1. Pulihkan daftar konsep dari concepts_vault_db.json
                var restoredConceptsCount = 0
                var importedActiveConceptId: String? = null
                if (!conceptsDbJsonStr.isNullOrBlank()) {
                    val parsedPair = parseConceptsDbJson(conceptsDbJsonStr!!)
                    if (parsedPair != null) {
                        val (importedList, activeIdFromZip) = parsedPair
                        importedActiveConceptId = activeIdFromZip
                        if (importedList.isNotEmpty()) {
                            val byId = _concepts.value.associateBy { it.id }.toMutableMap()
                            for (c in importedList) {
                                byId[c.id] = c
                                restoredConceptsCount++
                            }
                            val merged = byId.values.sortedByDescending { it.updatedAt }
                            _concepts.value = merged
                            val nextActive = activeIdFromZip ?: _activeConceptId.value ?: merged.firstOrNull()?.id
                            _activeConceptId.value = nextActive
                            saveConceptsToPrefs(merged, nextActive, syncToExternal = true)
                        }
                    }
                }

                // 2. Pulihkan sesi aktif dari auto_checkpoint_state.json atau konsep pertama di ZIP
                var activeRestored = false
                val snapshotToRestore = extractSnapshotFromCheckpointJson(autoCheckpointJsonStr)
                    ?: _concepts.value.firstOrNull { it.id == importedActiveConceptId }?.snapshotJson
                    ?: _concepts.value.firstOrNull()?.snapshotJson

                if (!snapshotToRestore.isNullOrBlank()) {
                    _busyProgressText.value = "Memulihkan playlist, IFS, koreografi Auto-Tap, stiker & pengaturan..."
                    val restoreRes = applySnapshotJsonInternal(
                        snapshotJson = snapshotToRestore,
                        profileId = _activeConceptId.value,
                        profileName = "Impor Paket Backup",
                        options = options
                    )
                    activeRestored = restoreRes.isSuccess
                    if (activeRestored) {
                        saveAutoCheckpointInternal()
                    }
                }

                if (restoredConceptsCount == 0 && extractedMediaCount == 0 && !activeRestored) {
                    return@withLock Result.failure(
                        IllegalArgumentException("File ZIP tidak berisi data cadangan Host Live yang valid")
                    )
                }

                val summary = "✅ Pulih (${options.selectedCount}/6 kategori)! $extractedMediaCount file Video/MP3 & $restoredConceptsCount Konsep berhasil dikembalikan."
                Result.success(
                    ZipImportReport(
                        restoredConceptCount = restoredConceptsCount,
                        restoredMediaFileCount = extractedMediaCount,
                        activeStateRestored = activeRestored,
                        summaryMessage = summary
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Gagal mengimpor paket ZIP: ${e.message}", e)
                Result.failure(e)
            } finally {
                _busyProgressText.value = null
                _isBusy.value = false
            }
        }
    }

    private fun isMediaFileExtension(name: String): Boolean {
        val low = name.lowercase()
        return low.endsWith(".mp4") || low.endsWith(".mkv") || low.endsWith(".mp3") || low.endsWith(".wav") || low.endsWith(".m4a")
    }

    /**
     * Mengkonsolidasi file-file duplikat di Vault internal menjadi 1 file fisik tunggal,
     * memperbarui path di daftar aktif & semua konsep tersimpan,
     * serta menghapus file sampah yang tidak terpakai di konsep mana pun.
     */
    suspend fun deduplicateAndCleanVault(): VaultCleanupReport = withContext(Dispatchers.IO) {
        var mergedDuplicates = 0
        var deletedOrphans = 0
        var freedBytes = 0L

        try {
            val repo = VcamRepository.getInstance(appContext)
            val vaultDir = repo.getInternalCamera1Directory()
            if (!vaultDir.exists()) return@withContext VaultCleanupReport(0, 0, 0L)

            val standardKeepNames = setOf(
                "virtual.mp4", "disable.jpg", "no-silent.jpg", "pause.jpg",
                "video_paused.txt", "video_audio_muted.txt", "audio_paused.txt", "mp3_audio_paused.txt",
                "audio_disabled.txt", "audio_path.txt", "auto_next.txt", "current_index.txt", "playlist.txt",
                "vcam_config.json", "rotation.txt", "mirror.txt", "flip_v.txt", "scale.txt", "audio.mp3",
                "audio.wav", ".nomedia"
            )
            val indexedRegex = Regex("""^video_(\d+)\.mp4$""")

            val allMediaFiles = (vaultDir.listFiles() ?: emptyArray()).filter { f ->
                f.isFile &&
                    !standardKeepNames.contains(f.name) &&
                    !indexedRegex.matches(f.name) &&
                    (f.name.endsWith(".mp4", true) || f.name.endsWith(".mkv", true) ||
                        f.name.endsWith(".mp3", true) || f.name.endsWith(".wav", true) ||
                        f.name.endsWith(".m4a", true) || f.name.endsWith(".aac", true))
            }

            // 1. Kelompokkan berdasarkan (ukuran + fastHash) untuk mendeteksi duplikat fisik
            val hashToPrimaryFile = mutableMapOf<String, File>()
            val duplicatePathRedirectMap = mutableMapOf<String, String>() // oldPath -> primaryPath

            for (file in allMediaFiles) {
                val len = file.length()
                if (len <= 0L) continue
                val hash = computeFastFileHash(file)
                if (hash.isBlank()) continue
                val key = "${len}_$hash"

                val existingPrimary = hashToPrimaryFile[key]
                if (existingPrimary == null) {
                    hashToPrimaryFile[key] = file
                } else if (existingPrimary.absolutePath != file.absolutePath) {
                    duplicatePathRedirectMap[file.absolutePath] = existingPrimary.absolutePath
                    try {
                        duplicatePathRedirectMap[file.canonicalPath] = existingPrimary.absolutePath
                    } catch (_: Exception) {}
                    val size = file.length()
                    if (file.delete()) {
                        mergedDuplicates++
                        freedBytes += size
                    }
                }
            }

            // 2. Jika ada duplikat yang digabung, perbarui path di Playlist Aktif & Seluruh Konsep Tersimpan
            if (duplicatePathRedirectMap.isNotEmpty()) {
                val curCfg = repo.config.value
                val newVidList = curCfg.playlist.map { item ->
                    val newPath = duplicatePathRedirectMap[item.path] ?: item.path
                    item.copy(
                        name = normalizeCleanFileName(item.name, ".mp4"),
                        path = newPath
                    )
                }
                val newAudList = curCfg.audioPlaylist.map { item ->
                    val newPath = duplicatePathRedirectMap[item.path] ?: item.path
                    item.copy(
                        name = normalizeCleanFileName(item.name, ".mp3"),
                        path = newPath
                    )
                }
                repo.updateConfig {
                    it.copy(
                        playlist = newVidList,
                        audioPlaylist = newAudList,
                        videoPath = duplicatePathRedirectMap[it.videoPath] ?: it.videoPath,
                        audioPath = duplicatePathRedirectMap[it.audioPath] ?: it.audioPath
                    )
                }

                val customMediaDir = getCustomMediaVaultDir()
                val updatedConcepts = _concepts.value.map { concept ->
                    try {
                        val root = JSONObject(concept.snapshotJson)
                        val vItems = VideoPlaylistRepository.parsePlaylistFromJson(root.optString("videoPlaylistJson", "[]")).map {
                            it.copy(
                                name = normalizeCleanFileName(it.name, ".mp4"),
                                path = duplicatePathRedirectMap[it.path] ?: it.path
                            )
                        }
                        val aItems = parseAudioListAllowingVaultLookup(
                            jsonStr = root.optString("audioPlaylistJson", "[]"),
                            internalVaultDir = vaultDir,
                            customMediaDir = customMediaDir
                        ).map {
                            it.copy(
                                name = normalizeCleanFileName(it.name, ".mp3"),
                                path = duplicatePathRedirectMap[it.path] ?: it.path
                            )
                        }
                        root.put("videoPlaylistJson", VideoPlaylistRepository.serializePlaylistToJson(vItems))
                        root.put("audioPlaylistJson", AudioPlaylistRepository.serializeAudioPlaylistJson(aItems))
                        concept.copy(snapshotJson = root.toString())
                    } catch (_: Exception) {
                        concept
                    }
                }
                _concepts.value = updatedConcepts
                saveConceptsToPrefs(updatedConcepts, _activeConceptId.value, syncToExternal = true)
            }

            // 3. Hapus file media yatim yang TIDAK dipakai di playlist aktif DAN TIDAK dipakai di Konsep Backup mana pun
            val activeVidPaths = repo.config.value.playlist.flatMap {
                val f = File(it.path)
                listOf(it.path, f.absolutePath, try { f.canonicalPath } catch (_: Exception) { it.path })
            }.toSet()
            val activeAudPaths = repo.config.value.audioPlaylist.flatMap {
                val f = File(it.path)
                listOf(it.path, f.absolutePath, try { f.canonicalPath } catch (_: Exception) { it.path })
            }.toSet()
            val conceptProtectedPaths = getAllProtectedMediaPaths()
            val allValidPaths = activeVidPaths + activeAudPaths + conceptProtectedPaths

            val remainingFiles = (vaultDir.listFiles() ?: emptyArray()).filter { f ->
                f.isFile &&
                    !standardKeepNames.contains(f.name) &&
                    !indexedRegex.matches(f.name) &&
                    (f.name.endsWith(".mp4", true) || f.name.endsWith(".mkv", true) ||
                        f.name.endsWith(".mp3", true) || f.name.endsWith(".wav", true) ||
                        f.name.endsWith(".m4a", true) || f.name.endsWith(".aac", true))
            }

            for (file in remainingFiles) {
                val abs = file.absolutePath
                val canon = try { file.canonicalPath } catch (_: Exception) { abs }
                if (!allValidPaths.contains(abs) && !allValidPaths.contains(canon)) {
                    val sz = file.length()
                    if (file.delete()) {
                        deletedOrphans++
                        freedBytes += sz
                    }
                }
            }

            // 4. Bersihkan juga file media yatim di dalam Custom Media Vault (media_vault/)
            // yang tidak dipakai di playlist aktif maupun di Konsep Backup tersimpan mana pun
            val validFileNames = (
                repo.config.value.playlist.map { File(it.path).name } +
                    repo.config.value.audioPlaylist.map { File(it.path).name } +
                    getAllProtectedMediaFileNames()
                ).toSet()
            val customMediaDir = getCustomMediaVaultDir()
            if (customMediaDir.exists() && customMediaDir.absolutePath != vaultDir.absolutePath) {
                val customFiles = (customMediaDir.listFiles() ?: emptyArray()).filter { f ->
                    f.isFile &&
                        f.name != ".nomedia" &&
                        (f.name.endsWith(".mp4", true) || f.name.endsWith(".mkv", true) ||
                            f.name.endsWith(".mp3", true) || f.name.endsWith(".wav", true) ||
                            f.name.endsWith(".m4a", true) || f.name.endsWith(".aac", true))
                }
                for (file in customFiles) {
                    val abs = file.absolutePath
                    val canon = try { file.canonicalPath } catch (_: Exception) { abs }
                    if (!allValidPaths.contains(abs) &&
                        !allValidPaths.contains(canon) &&
                        !validFileNames.contains(file.name)
                    ) {
                        val sz = file.length()
                        if (file.delete()) {
                            deletedOrphans++
                            freedBytes += sz
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membersihkan duplikat Vault: ${e.message}", e)
        }

        VaultCleanupReport(
            mergedDuplicateCount = mergedDuplicates,
            deletedOrphanCount = deletedOrphans,
            freedBytes = freedBytes
        )
    }

    private fun parseAudioListAllowingVaultLookup(
        jsonStr: String,
        internalVaultDir: File,
        customMediaDir: File
    ): List<AudioItem> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<AudioItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val rawPath = obj.optString("path", "")
                val rawName = obj.optString("name", "audio_${i + 1}.mp3")
                val cleanName = normalizeCleanFileName(rawName, ".mp3")
                val resolvedFile = resolveAndHealMediaFile(
                    rawPath = rawPath,
                    rawName = cleanName,
                    defaultExt = ".mp3",
                    internalVaultDir = internalVaultDir,
                    customMediaDir = customMediaDir
                )
                if (resolvedFile != null) {
                    list.add(
                        AudioItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = cleanName,
                            path = resolvedFile.absolutePath,
                            uri = obj.optString("uri", "").takeIf { it.isNotBlank() },
                            durationMs = obj.optLong("durationMs", 0L),
                            sizeBytes = resolvedFile.length(),
                            addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                            isMuted = obj.optBoolean("isMuted", false),
                            isEnabled = obj.optBoolean("isEnabled", true)
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeConceptProfileToJson(item: ConceptProfile): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("name", item.name)
            put("createdAt", item.createdAt)
            put("updatedAt", item.updatedAt)
            put("videoCount", item.videoCount)
            put("audioCount", item.audioCount)
            put("modeSummary", item.modeSummary)
            put("overlaySummary", item.overlaySummary)
            put("snapshotJson", item.snapshotJson)
        }
    }

    private fun parseConceptProfileFromJson(obj: JSONObject, fallbackIndex: Int = 0): ConceptProfile {
        return ConceptProfile(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "Konsep #${fallbackIndex + 1}"),
            createdAt = obj.optLong("createdAt", 0L),
            updatedAt = obj.optLong("updatedAt", 0L),
            videoCount = obj.optInt("videoCount", 0),
            audioCount = obj.optInt("audioCount", 0),
            modeSummary = obj.optString("modeSummary", "Standar"),
            overlaySummary = obj.optString("overlaySummary", ""),
            snapshotJson = obj.optString("snapshotJson", "{}")
        )
    }

    private fun buildConceptsDbJson(list: List<ConceptProfile>, activeId: String?): String {
        val arr = JSONArray()
        for (item in list) {
            arr.put(serializeConceptProfileToJson(item))
        }
        return JSONObject().apply {
            put("schemaVersion", 1)
            put("updatedAt", System.currentTimeMillis())
            put("activeConceptId", activeId ?: "")
            put("concepts", arr)
        }.toString()
    }

    private fun parseConceptsDbJson(rawJson: String): Pair<List<ConceptProfile>, String?>? {
        return try {
            val trimmed = rawJson.trim()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                val list = mutableListOf<ConceptProfile>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(parseConceptProfileFromJson(obj, i))
                }
                Pair(list, null)
            } else {
                val root = JSONObject(trimmed)
                val activeId = root.optString("activeConceptId", "").takeIf { it.isNotBlank() }
                val arr = root.optJSONArray("concepts") ?: JSONArray()
                val list = mutableListOf<ConceptProfile>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(parseConceptProfileFromJson(obj, i))
                }
                Pair(list, activeId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal parse concepts_vault_db.json: ${e.message}")
            null
        }
    }

    private fun writeConceptsDbToCustomVault(list: List<ConceptProfile>, activeId: String?) {
        try {
            val vaultDir = getResolvedCustomVaultDir()
            val dbFile = File(vaultDir, VAULT_DB_FILE_NAME)
            dbFile.writeText(buildConceptsDbJson(list, activeId))
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menulis database konsep ke Custom Local Vault: ${e.message}")
        }
    }

    private fun readConceptsDbFromCustomVault(): Pair<List<ConceptProfile>, String?>? {
        return try {
            val vaultDir = getResolvedCustomVaultDir()
            val dbFile = File(vaultDir, VAULT_DB_FILE_NAME)
            if (!dbFile.exists() || dbFile.length() <= 0L) return null
            parseConceptsDbJson(dbFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun extractSnapshotFromCheckpointJson(rawJson: String?): String? {
        if (rawJson.isNullOrBlank()) return null
        return try {
            val root = JSONObject(rawJson)
            if (root.has("profile")) {
                root.optJSONObject("profile")?.optString("snapshotJson", "")?.takeIf { it.isNotBlank() }
            } else if (root.has("vcamConfig") || root.has("videoPlaylistJson")) {
                rawJson
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readAutoCheckpointJsonFromDiskOrPrefs(): String? {
        try {
            val vaultDir = getResolvedCustomVaultDir()
            val cpFile = File(vaultDir, AUTO_CHECKPOINT_FILE_NAME)
            if (cpFile.exists() && cpFile.length() > 0L) {
                val extracted = extractSnapshotFromCheckpointJson(cpFile.readText())
                if (!extracted.isNullOrBlank()) return extracted
            }
        } catch (_: Exception) {}
        return prefs.getString(KEY_AUTO_CHECKPOINT_JSON, null)?.takeIf { it.isNotBlank() }
    }

    private fun loadConceptsFromPrefs(): List<ConceptProfile> {
        val raw = prefs.getString(KEY_CONCEPTS_JSON, null)
        if (!raw.isNullOrBlank()) {
            val list = mutableListOf<ConceptProfile>()
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(parseConceptProfileFromJson(obj, i))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memuat daftar konsep dari prefs: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun saveConceptsToPrefs(
        list: List<ConceptProfile>,
        activeId: String?,
        syncToExternal: Boolean = true
    ) {
        try {
            val arr = JSONArray()
            for (item in list) {
                arr.put(serializeConceptProfileToJson(item))
            }
            prefs.edit()
                .putString(KEY_CONCEPTS_JSON, arr.toString())
                .putString(KEY_ACTIVE_CONCEPT_ID, activeId)
                .apply()

            if (syncToExternal) {
                backgroundScope.launch {
                    writeConceptsDbToCustomVault(list, activeId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan daftar konsep: ${e.message}")
        }
    }
}
