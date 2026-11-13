package com.acopy.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.acopy.FloatingControlService.NoteItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object BackupRestoreManager {

    const val PREFS_NAME = "pintasan_floating_prefs"
    const val KEY_PINNED_APPS_PRIMARY = "pintasan_pinned_apps_json"
    const val KEY_PINNED_APPS_LEGACY = "saved_pinned_apps_json"
    const val ACTION_RELOAD_DATA = "com.acopy.action.SYNC_NOTES"

    data class AppSettings(
        val floatingEnabled: Boolean = true,
        val alphaPercent: Int = 100,
        val bubbleX: Int = -1,
        val bubbleY: Int = -1,
        val bubbleIsVertical: Boolean = false,
        val panelWidth: Int = -1,
        val panelHeight: Int = -1,
        val panelX: Int = -1,
        val panelY: Int = -1,
        val lastSyncIp: String = "",
        val appUsageData: String = ""
    )

    data class BackupParsedResult(
        val app: String,
        val version: Int,
        val timestamp: Long,
        val exportDate: String,
        val notes: List<NoteItem>,
        val pinnedApps: List<String>,
        val settings: AppSettings? = null,
        val activeNotesCount: Int,
        val trashNotesCount: Int,
        val isSuccess: Boolean,
        val errorMessage: String? = null
    )

    enum class RestoreMode {
        OVERWRITE, // Timpa Semua / Pulihkan Penuh (Catatan + Pin + Pengaturan)
        MERGE      // Gabungkan (Tambah & Perbarui Tanpa Hapus)
    }

    data class RestoreSummary(
        val mode: RestoreMode,
        val notesAdded: Int,
        val notesUpdated: Int,
        val notesSkipped: Int,
        val notesTotal: Int,
        val pinnedAppsRestored: Int,
        val settingsRestored: Boolean,
        val message: String
    )

    /**
     * Creates a complete, human-readable full APK backup JSON string containing:
     * - App metadata & timestamp
     * - Active notes & Trash notes with full metadata
     * - Complete Pinned Apps list (Aplikasi yang Disematkan)
     * - Full APK Settings (transparency, positions, floating status, usage data)
     */
    fun createBackupJson(context: Context): String {
        val notes = NotesStorageUtil.loadNotes(context)
        val pinnedApps = loadPinnedApps(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now))

        val root = JSONObject()
        root.put("app", "Pintasan")
        root.put("version", 2)
        root.put("exportDate", dateStr)
        root.put("timestamp", now)
        root.put("backupType", "FULL_APK")

        val statsObj = JSONObject().apply {
            put("totalNotes", notes.size)
            put("activeNotes", notes.count { !it.isDeleted })
            put("trashNotes", notes.count { it.isDeleted })
            put("pinnedApps", pinnedApps.size)
        }
        root.put("stats", statsObj)

        // Notes array
        val notesArr = JSONArray()
        for (n in notes) {
            val obj = JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("isDeleted", n.isDeleted)
                put("deletedAt", n.deletedAt)
            }
            notesArr.put(obj)
        }
        root.put("notes", notesArr)

        // Pinned apps array (Saved to both keys for 100% reliability)
        val pinnedArr = JSONArray()
        for (pkg in pinnedApps) {
            pinnedArr.put(pkg)
        }
        root.put("pinnedApps", pinnedArr)

        // Full APK Settings & Preferences
        val settingsObj = JSONObject().apply {
            put("floating_enabled", prefs.getBoolean("floating_enabled", true))
            put("floating_alpha_percent", prefs.getInt("floating_alpha_percent", 100))
            put("bubble_x", prefs.getInt("bubble_x", -1))
            put("bubble_y", prefs.getInt("bubble_y", -1))
            put("bubble_is_vertical", prefs.getBoolean("bubble_is_vertical", false))
            put("panel_x", prefs.getInt("panel_x", -1))
            put("panel_y", prefs.getInt("panel_y", -1))
            put("panel_width", prefs.getInt("panel_width", -1))
            put("panel_height", prefs.getInt("panel_height", -1))
            put("last_sync_target_ip", prefs.getString("last_sync_target_ip", "") ?: "")
            put("pintasan_app_usage_data", prefs.getString("pintasan_app_usage_data", "") ?: "")
        }
        root.put("settings", settingsObj)

        return root.toString(2)
    }

    /**
     * Resilient parser that accepts:
     * 1. Modern v2 Full APK JSON ({ "app": "Pintasan", "notes": [...], "pinnedApps": [...], "settings": {...} })
     * 2. Legacy v1 JSON ({ "app": "Pintasan", "notes": [...] })
     * 3. Direct JSON Array ([ {"id":..., "title":..., "content":...}, ... ])
     * 4. Single note JSON ({ "title":..., "content":... })
     * 5. Plain text (creates 1 note)
     * Never crashes and never loses note or pin data.
     */
    fun parseBackup(rawText: String, allowPlainTextFallback: Boolean = false): BackupParsedResult {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return BackupParsedResult(
                app = "Pintasan",
                version = 2,
                timestamp = System.currentTimeMillis(),
                exportDate = "",
                notes = emptyList(),
                pinnedApps = emptyList(),
                settings = null,
                activeNotesCount = 0,
                trashNotesCount = 0,
                isSuccess = false,
                errorMessage = "Teks cadangan kosong"
            )
        }

        val parsedNotes = mutableListOf<NoteItem>()
        val parsedPinnedApps = mutableListOf<String>()
        var parsedSettings: AppSettings? = null
        var version = 2
        var appName = "Pintasan"
        var timestamp = System.currentTimeMillis()
        var exportDate = ""

        try {
            if (trimmed.startsWith("[")) {
                // Direct JSON Array of notes or package names
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val item = arr.opt(i)
                    if (item is JSONObject) {
                        extractNoteFromJsonObject(item)?.let { parsedNotes.add(it) }
                    } else if (item is String && item.contains(".")) {
                        // Probably a package name list
                        parsedPinnedApps.add(item)
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                appName = root.optString("app", "Pintasan")
                version = root.optInt("version", 1)
                timestamp = root.optLong("timestamp", System.currentTimeMillis())
                exportDate = root.optString("exportDate", "")

                // Parse "notes" array
                val notesArr = root.optJSONArray("notes")
                if (notesArr != null) {
                    for (i in 0 until notesArr.length()) {
                        val obj = notesArr.optJSONObject(i) ?: continue
                        extractNoteFromJsonObject(obj)?.let { parsedNotes.add(it) }
                    }
                }

                // Parse "pinnedApps" or "pinned_apps" array
                val pinnedArr = root.optJSONArray("pinnedApps") ?: root.optJSONArray("pinned_apps")
                if (pinnedArr != null) {
                    for (i in 0 until pinnedArr.length()) {
                        val pkg = pinnedArr.optString(i, "")
                        if (pkg.isNotBlank()) parsedPinnedApps.add(pkg)
                    }
                }

                // Parse Full APK Settings
                val settingsObj = root.optJSONObject("settings")
                if (settingsObj != null) {
                    parsedSettings = AppSettings(
                        floatingEnabled = settingsObj.optBoolean("floating_enabled", true),
                        alphaPercent = settingsObj.optInt("floating_alpha_percent", 100),
                        bubbleX = settingsObj.optInt("bubble_x", -1),
                        bubbleY = settingsObj.optInt("bubble_y", -1),
                        bubbleIsVertical = settingsObj.optBoolean("bubble_is_vertical", false),
                        panelWidth = settingsObj.optInt("panel_width", -1),
                        panelHeight = settingsObj.optInt("panel_height", -1),
                        panelX = settingsObj.optInt("panel_x", -1),
                        panelY = settingsObj.optInt("panel_y", -1),
                        lastSyncIp = settingsObj.optString("last_sync_target_ip", ""),
                        appUsageData = settingsObj.optString("pintasan_app_usage_data", "")
                    )
                }

                // If no notes array found, check if the root object itself is a single note
                if (parsedNotes.isEmpty() && (root.has("content") || root.has("title"))) {
                    extractNoteFromJsonObject(root)?.let { parsedNotes.add(it) }
                }
            } else if (allowPlainTextFallback) {
                // Non-JSON plain text fallback: create 1 note
                parsedNotes.add(
                    NoteItem(
                        id = UUID.randomUUID().toString(),
                        title = "Catatan Impor",
                        content = trimmed,
                        isDeleted = false,
                        deletedAt = 0L
                    )
                )
            }
        } catch (e: Exception) {
            if (allowPlainTextFallback) {
                parsedNotes.add(
                    NoteItem(
                        id = UUID.randomUUID().toString(),
                        title = "Catatan Impor (Teks)",
                        content = trimmed,
                        isDeleted = false,
                        deletedAt = 0L
                    )
                )
            }
        }

        val activeCount = parsedNotes.count { !it.isDeleted }
        val trashCount = parsedNotes.count { it.isDeleted }

        return BackupParsedResult(
            app = appName,
            version = version,
            timestamp = timestamp,
            exportDate = exportDate,
            notes = parsedNotes,
            pinnedApps = parsedPinnedApps,
            settings = parsedSettings,
            activeNotesCount = activeCount,
            trashNotesCount = trashCount,
            isSuccess = parsedNotes.isNotEmpty() || parsedPinnedApps.isNotEmpty() || parsedSettings != null,
            errorMessage = if (parsedNotes.isEmpty() && parsedPinnedApps.isEmpty() && parsedSettings == null) "Tidak ada data valid yang dapat dibaca" else null
        )
    }

    private fun extractNoteFromJsonObject(obj: JSONObject): NoteItem? {
        val content = obj.optString("content", "").trim()
        val title = obj.optString("title", "").trim()
        if (content.isBlank() && title.isBlank()) return null

        val id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() }
        val isDeleted = obj.optBoolean("isDeleted", false)
        val deletedAt = obj.optLong("deletedAt", 0L)

        return NoteItem(
            id = id,
            title = title,
            content = content,
            isDeleted = isDeleted,
            deletedAt = deletedAt
        )
    }

    /**
     * Restores notes, pinned apps, and full APK settings according to the specified RestoreMode.
     * Guarantees clean state, persistence, and notifies FloatingControlService.
     */
    fun restoreBackup(
        context: Context,
        backup: BackupParsedResult,
        mode: RestoreMode
    ): RestoreSummary {
        val targetNotes = mutableListOf<NoteItem>()
        var added = 0
        var updated = 0
        var skipped = 0
        var pinnedRestored = 0
        var settingsRestored = false

        if (mode == RestoreMode.OVERWRITE) {
            // Complete clean replacement of notes
            targetNotes.addAll(backup.notes)
            added = backup.notes.size

            // Complete clean replacement of pinned apps
            if (backup.pinnedApps.isNotEmpty()) {
                savePinnedApps(context, backup.pinnedApps)
                pinnedRestored = backup.pinnedApps.size
            }

            // Restore Full APK settings
            if (backup.settings != null) {
                restoreSettings(context, backup.settings)
                settingsRestored = true
            }
        } else {
            // MERGE MODE: Combine existing notes with imported notes
            val currentNotes = NotesStorageUtil.loadNotes(context).toMutableList()
            val existingById = currentNotes.associateBy { it.id }.toMutableMap()
            val existingContentKey = currentNotes.map { it.title.trim() to it.content.trim() }.toMutableSet()

            for (incoming in backup.notes) {
                val existing = existingById[incoming.id]
                val contentKey = incoming.title.trim() to incoming.content.trim()

                if (existing != null) {
                    // Update existing note with backup version
                    val idx = currentNotes.indexOfFirst { it.id == incoming.id }
                    if (idx >= 0) {
                        currentNotes[idx] = incoming
                        updated++
                    }
                } else if (contentKey in existingContentKey) {
                    // Same title and content already exists, skip duplicate
                    skipped++
                } else {
                    // Brand new note
                    val noteToAdd = if (incoming.id.isBlank()) incoming.copy(id = UUID.randomUUID().toString()) else incoming
                    currentNotes.add(0, noteToAdd)
                    existingContentKey.add(contentKey)
                    added++
                }
            }
            targetNotes.addAll(currentNotes)

            // Merge pinned apps without duplicates
            if (backup.pinnedApps.isNotEmpty()) {
                val currentPinned = loadPinnedApps(context).toMutableList()
                for (pkg in backup.pinnedApps) {
                    if (pkg !in currentPinned) {
                        currentPinned.add(pkg)
                        pinnedRestored++
                    }
                }
                savePinnedApps(context, currentPinned)
            }

            // Merge settings if present
            if (backup.settings != null) {
                restoreSettings(context, backup.settings)
                settingsRestored = true
            }
        }

        // Save notes to persistent storage
        NotesStorageUtil.saveNotes(context, targetNotes)

        // Notify running FloatingControlService to immediately reload state
        notifyFloatingServiceToReload(context)

        val message = when (mode) {
            RestoreMode.OVERWRITE -> "Berhasil pulihkan penuh: ${targetNotes.size} catatan (${backup.activeNotesCount} aktif, ${backup.trashNotesCount} sampah)${if (pinnedRestored > 0) ", $pinnedRestored pin aplikasi" else ""}${if (settingsRestored) ", dan pengaturan APK" else ""}."
            RestoreMode.MERGE -> "Penggabungan selesai: +$added baru, $updated diperbarui, $skipped ada${if (pinnedRestored > 0) ", +$pinnedRestored pin aplikasi" else ""}${if (settingsRestored) ", pengaturan diperbarui" else ""}."
        }

        return RestoreSummary(
            mode = mode,
            notesAdded = added,
            notesUpdated = updated,
            notesSkipped = skipped,
            notesTotal = targetNotes.size,
            pinnedAppsRestored = pinnedRestored,
            settingsRestored = settingsRestored,
            message = message
        )
    }

    private fun restoreSettings(context: Context, settings: AppSettings) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putBoolean("floating_enabled", settings.floatingEnabled)
        if (settings.alphaPercent in 20..100) {
            editor.putInt("floating_alpha_percent", settings.alphaPercent)
        }
        if (settings.bubbleX >= 0) editor.putInt("bubble_x", settings.bubbleX)
        if (settings.bubbleY >= 0) editor.putInt("bubble_y", settings.bubbleY)
        editor.putBoolean("bubble_is_vertical", settings.bubbleIsVertical)
        if (settings.panelWidth > 0) editor.putInt("panel_width", settings.panelWidth)
        if (settings.panelHeight > 0) editor.putInt("panel_height", settings.panelHeight)
        if (settings.panelX >= 0) editor.putInt("panel_x", settings.panelX)
        if (settings.panelY >= 0) editor.putInt("panel_y", settings.panelY)
        if (settings.lastSyncIp.isNotBlank()) {
            editor.putString("last_sync_target_ip", settings.lastSyncIp)
        }
        if (settings.appUsageData.isNotBlank()) {
            editor.putString("pintasan_app_usage_data", settings.appUsageData)
        }
        editor.apply()
    }

    /**
     * Exports the backup JSON into a real .json file in the app's cache/backups directory,
     * ready for sharing or saving via FileProvider.
     */
    fun exportBackupToFile(context: Context): File {
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(backupDir, "backup_pintasan_full_$timeTag.json")

        val jsonStr = createBackupJson(context)
        FileOutputStream(backupFile).use { fos ->
            fos.write(jsonStr.toByteArray(Charsets.UTF_8))
            fos.flush()
        }
        return backupFile
    }

    /**
     * Creates an Intent to share or save the backup file with FileProvider authority.
     */
    fun createShareBackupIntent(context: Context, backupFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, backupFile)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Cadangan Pintasan Full APK (${backupFile.name})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(sendIntent, "Simpan / Bagikan Berkas Cadangan JSON").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Reads text safely from a Content URI selected via Document/File Picker.
     */
    fun readBackupFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getOverviewStats(context: Context): Triple<Int, Int, Int> {
        val notes = NotesStorageUtil.loadNotes(context)
        val active = notes.count { !it.isDeleted }
        val trash = notes.count { it.isDeleted }
        val pinned = loadPinnedApps(context).size
        return Triple(active, trash, pinned)
    }

    fun loadPinnedApps(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Check primary key first, then fallback key
        val raw = prefs.getString(KEY_PINNED_APPS_PRIMARY, null)
            ?: prefs.getString(KEY_PINNED_APPS_LEGACY, null)
            ?: return emptyList()
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val pkg = arr.optString(i, "")
                if (pkg.isNotBlank() && !list.contains(pkg)) {
                    list.add(pkg)
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun savePinnedApps(context: Context, pinnedApps: List<String>) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (pkg in pinnedApps.distinct()) {
            arr.put(pkg)
        }
        val serialized = arr.toString()
        // Save to BOTH primary and fallback keys so both services read it seamlessly
        prefs.edit()
            .putString(KEY_PINNED_APPS_PRIMARY, serialized)
            .putString(KEY_PINNED_APPS_LEGACY, serialized)
            .apply()
    }

    fun notifyFloatingServiceToReload(context: Context) {
        try {
            val intent = Intent(context, com.acopy.FloatingControlService::class.java).apply {
                action = ACTION_RELOAD_DATA
            }
            context.startService(intent)
        } catch (_: Throwable) {}
    }
}
