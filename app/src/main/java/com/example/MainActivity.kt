package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.acopy.FloatingControlService
import com.acopy.SplitScreenAccessibilityService
import com.acopy.data.BackupRestoreManager
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var backupStats by mutableStateOf(Triple(0, 0, 0))
    private var pendingRestoreData by mutableStateOf<BackupRestoreManager.BackupParsedResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshOverlayPermission()
        refreshAccessibilityPermission()
        refreshBackupStats()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                val isFloatingRunning by FloatingControlService.isRunningState.collectAsState()

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    startFloatingServiceInternal()
                }

                // File Picker for Restoring from JSON
                val openDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        val text = BackupRestoreManager.readBackupFromUri(this, uri)
                        if (!text.isNullOrBlank()) {
                            val parsed = BackupRestoreManager.parseBackup(text, allowPlainTextFallback = false)
                            if (parsed.isSuccess && (parsed.notes.isNotEmpty() || parsed.pinnedApps.isNotEmpty())) {
                                pendingRestoreData = parsed
                            } else {
                                Toast.makeText(
                                    this,
                                    "Berkas yang dipilih tidak berisi format cadangan yang valid.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            Toast.makeText(this, "Gagal membaca isi berkas.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // File Creator for Saving Backup to User's Custom Folder
                val createDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            contentResolver.openOutputStream(uri)?.use { os ->
                                val jsonStr = BackupRestoreManager.createBackupJson(this)
                                os.write(jsonStr.toByteArray(Charsets.UTF_8))
                                os.flush()
                            }
                            Toast.makeText(this, "Berkas cadangan berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this, "Gagal menyimpan berkas: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    MainScreen(
                        isFloatingRunning = isFloatingRunning,
                        hasOverlayPermission = hasOverlayPermission,
                        hasAccessibilityPermission = hasAccessibilityPermission,
                        onToggleFloating = { enabled ->
                            if (enabled) {
                                if (!canDrawOverlaysCompat()) {
                                    openOverlayPermissionSettings()
                                } else {
                                    FloatingControlService.setFloatingEnabledPref(this, true)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        startFloatingServiceInternal()
                                    }
                                }
                            } else {
                                FloatingControlService.setFloatingEnabledPref(this, false)
                                stopFloatingServiceInternal()
                            }
                        },
                        onExpandFloatingPanel = {
                            if (canDrawOverlaysCompat()) {
                                val intent = Intent(this, FloatingControlService::class.java).apply {
                                    action = FloatingControlService.ACTION_EXPAND_PANEL
                                }
                                startServiceCompat(intent)
                            }
                        },
                        onMinimizeFloatingBubble = {
                            if (canDrawOverlaysCompat()) {
                                val intent = Intent(this, FloatingControlService::class.java).apply {
                                    action = FloatingControlService.ACTION_MINIMIZE_BUBBLE
                                }
                                startServiceCompat(intent)
                            }
                        },
                        onRequestOverlayPermission = {
                            openOverlayPermissionSettings()
                        },
                        onRequestAccessibilityPermission = {
                            SplitScreenAccessibilityService.openAccessibilitySettings(this)
                        },
                        backupActiveCount = backupStats.first,
                        backupTrashCount = backupStats.second,
                        backupPinnedCount = backupStats.third,
                        onExportBackupFile = {
                            try {
                                val file = BackupRestoreManager.exportBackupToFile(this)
                                val shareIntent = BackupRestoreManager.createShareBackupIntent(this, file)
                                startActivity(shareIntent)
                            } catch (e: Exception) {
                                Toast.makeText(this, "Gagal membuat berkas cadangan: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSaveBackupToLocation = {
                            val timeTag = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                            createDocumentLauncher.launch("backup_pintasan_$timeTag.json")
                        },
                        onPickBackupFile = {
                            openDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        onCopyBackupJson = {
                            val json = BackupRestoreManager.createBackupJson(this)
                            SplitScreenAccessibilityService.copyToClipboard(this, json)
                            Toast.makeText(this, "Teks cadangan lengkap disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        onPasteAndRestore = {
                            val clip = SplitScreenAccessibilityService.readFromClipboard(this).trim()
                            if (clip.isBlank()) {
                                Toast.makeText(this, "Clipboard masih kosong.", Toast.LENGTH_SHORT).show()
                            } else {
                                val parsed = BackupRestoreManager.parseBackup(clip, allowPlainTextFallback = true)
                                if (parsed.isSuccess && (parsed.notes.isNotEmpty() || parsed.pinnedApps.isNotEmpty())) {
                                    pendingRestoreData = parsed
                                } else {
                                    Toast.makeText(this, "Teks di clipboard bukan format cadangan yang valid.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        pendingRestoreData = pendingRestoreData,
                        onConfirmRestore = { data, mode ->
                            val summary = BackupRestoreManager.restoreBackup(this, data, mode)
                            refreshBackupStats()
                            pendingRestoreData = null
                            Toast.makeText(this, summary.message, Toast.LENGTH_LONG).show()
                        },
                        onDismissRestoreDialog = {
                            pendingRestoreData = null
                        }
                    )
                }
            }
        }

        if (FloatingControlService.isFloatingEnabledPref(this) && canDrawOverlaysCompat()) {
            startFloatingServiceInternal()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayPermission()
        refreshAccessibilityPermission()
        refreshBackupStats()
        if (FloatingControlService.isFloatingEnabledPref(this) &&
            canDrawOverlaysCompat() &&
            !FloatingControlService.isRunning
        ) {
            startFloatingServiceInternal()
        }
    }

    private fun refreshBackupStats() {
        backupStats = BackupRestoreManager.getOverviewStats(this)
    }

    private fun refreshOverlayPermission() {
        hasOverlayPermission = canDrawOverlaysCompat()
    }

    private fun refreshAccessibilityPermission() {
        hasAccessibilityPermission = SplitScreenAccessibilityService.isAccessibilityEnabled(this)
    }

    private fun canDrawOverlaysCompat(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun openOverlayPermissionSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (_: Throwable) {}
    }

    private fun startFloatingServiceInternal() {
        if (!canDrawOverlaysCompat()) return
        startServiceCompat(Intent(this, FloatingControlService::class.java))
    }

    private fun stopFloatingServiceInternal() {
        try {
            val intent = Intent(this, FloatingControlService::class.java).apply {
                action = FloatingControlService.ACTION_STOP_SERVICE
            }
            startService(intent)
        } catch (_: Throwable) {
            stopService(Intent(this, FloatingControlService::class.java))
        }
    }

    private fun startServiceCompat(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Throwable) {}
    }
}
