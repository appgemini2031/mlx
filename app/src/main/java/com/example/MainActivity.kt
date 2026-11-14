package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme
import com.tajasb.ui.VcamMainScreen
import com.tajasb.ui.VcamViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val viewModel: VcamViewModel by viewModels()
    private var crashRecoveryMessage by mutableStateOf<String?>(null)

    companion object {
        @Volatile
        var isActivityResumed: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Jaga agar layar HP selalu aktif / tidak mati selama aplikasi terbuka & saat live streaming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        com.tajasb.debug.CrashShieldManager.install(this)
        handleCrashRecoveryIntent(intent)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    VcamMainScreen(
                        viewModel = viewModel,
                        crashRecoveryMessage = crashRecoveryMessage,
                        onDismissCrashRecovery = { crashRecoveryMessage = null }
                    )
                }
            }
        }

        handleTabIntent(intent)

        try {
            com.tajasb.debug.CrashReporter.init(this)
        } catch (_: Throwable) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCrashRecoveryIntent(intent)
        handleTabIntent(intent)
    }

    private fun handleCrashRecoveryIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(com.tajasb.debug.CrashShieldManager.EXTRA_CRASH_RECOVERED, false) == true) {
            crashRecoveryMessage = intent.getStringExtra(com.tajasb.debug.CrashShieldManager.EXTRA_CRASH_MESSAGE) ?: "Sistem memulihkan aplikasi secara otomatis"
            intent.removeExtra(com.tajasb.debug.CrashShieldManager.EXTRA_CRASH_RECOVERED)
        }
    }

    private fun handleTabIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("extra_open_mlbb_tab", false) == true) {
            intent.removeExtra("extra_open_mlbb_tab")
            viewModel.setSelectedMainTab(0)
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        isActivityResumed = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            cleanupOnExit()
        }
    }

    override fun onDestroy() {
        isActivityResumed = false
        super.onDestroy()
        activityScope.cancel()
        cleanupOnExit()
    }

    private fun cleanupOnExit() {
        try {
            val triggerEngine = com.tajasb.interaction.UniversalTriggerEngine.peekInstance()
            if (triggerEngine != null && !triggerEngine.state.value.isFloatingOverlayRunning) {
                triggerEngine.emergencyStop()
            }
        } catch (_: Exception) {}
    }
}
