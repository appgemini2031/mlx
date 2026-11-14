package com.tajasb.interaction

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

enum class FloatingPickerMode {
    PICK_IMAGE,
    PICK_AUDIO,
    IMPORT_PRESET_FILE,
    EXPORT_PRESET_FILE
}

data class FloatingPickerResult(
    val requestKey: String,
    val uriString: String
)

object FloatingPickerBridge {
    private val _result = MutableStateFlow<FloatingPickerResult?>(null)
    val result: StateFlow<FloatingPickerResult?> = _result.asStateFlow()

    @Volatile
    var pendingExportJson: String? = null

    fun postResult(requestKey: String, uri: Uri?) {
        if (uri != null) {
            _result.value = FloatingPickerResult(requestKey, uri.toString())
        }
    }

    fun consumeResult() {
        _result.value = null
    }

    fun launch(
        context: Context,
        mode: FloatingPickerMode,
        requestKey: String = "",
        suggestedFileName: String = "universal_presets.json",
        exportJsonPayload: String? = null
    ) {
        if (exportJsonPayload != null) {
            pendingExportJson = exportJsonPayload
        }
        val intent = Intent(context, FloatingPickerProxyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(FloatingPickerProxyActivity.EXTRA_MODE, mode.name)
            putExtra(FloatingPickerProxyActivity.EXTRA_REQUEST_KEY, requestKey)
            putExtra(FloatingPickerProxyActivity.EXTRA_FILE_NAME, suggestedFileName)
        }
        context.startActivity(intent)
    }
}

/**
 * Activity transparan ringan agar panel Floating Service tetap dapat memanggil
 * Android Photo Picker & Audio Picker lalu menyalin file media ke penyimpanan internal
 * supaya Overlay Gambar & Sound Effect selalu dapat diputar tanpa kadaluwarsa izin URI.
 */
class FloatingPickerProxyActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "extra_picker_mode"
        const val EXTRA_REQUEST_KEY = "extra_request_key"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val PREFIX_RULE_IMAGE = "rule_img:"
        const val PREFIX_RULE_SOUND = "rule_sfx:"
    }

    private var requestKey: String = ""

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localUri = copyMediaToAppStorage(uri, "img") ?: uri
            applyPickedMediaIfRuleTarget(requestKey, localUri)
            FloatingPickerBridge.postResult(requestKey, localUri)
        }
        finish()
    }

    private val audioPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {}
            val localUri = copyMediaToAppStorage(uri, "sfx") ?: uri
            applyPickedMediaIfRuleTarget(requestKey, localUri)
            FloatingPickerBridge.postResult(requestKey, localUri)
        }
        finish()
    }

    private val importPresetPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                UniversalTriggerEngine.getInstance(applicationContext).importPresetsJson(raw).fold(
                    onSuccess = { count ->
                        Toast.makeText(this, "✅ $count preset diimpor", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { err ->
                        Toast.makeText(this, "❌ ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (t: Throwable) {
                Toast.makeText(this, "❌ Gagal membaca file: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    private val exportPresetCreator = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = FloatingPickerBridge.pendingExportJson
        FloatingPickerBridge.pendingExportJson = null
        if (uri != null && payload != null) {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "✅ Preset diekspor ke file JSON", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(this, "❌ Gagal ekspor: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    private fun copyMediaToAppStorage(uri: Uri, prefix: String): Uri? {
        return try {
            val mediaDir = File(filesDir, "mlbb_media").apply { mkdirs() }
            val ext = resolveFileExtension(uri, if (prefix == "img") "png" else "mp3")
            val dest = File(mediaDir, "${prefix}_${System.currentTimeMillis()}.$ext")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            if (dest.exists() && dest.length() > 0L) Uri.fromFile(dest) else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveFileExtension(uri: Uri, fallback: String): String {
        val name = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Throwable) {
            null
        }
        val fromName = name?.substringAfterLast('.', "")?.lowercase()?.filter { it.isLetterOrDigit() }
        if (!fromName.isNullOrBlank() && fromName.length <= 5) return fromName
        val mime = contentResolver.getType(uri).orEmpty().lowercase()
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("wav") -> "wav"
            mime.contains("ogg") -> "ogg"
            mime.contains("m4a") || mime.contains("aac") -> "m4a"
            else -> fallback
        }
    }

    private fun applyPickedMediaIfRuleTarget(key: String, localUri: Uri) {
        val engine = UniversalTriggerEngine.getInstance(applicationContext)
        when {
            key.startsWith(PREFIX_RULE_IMAGE) -> {
                val raw = key.removePrefix(PREFIX_RULE_IMAGE)
                if (raw.contains("::")) {
                    val ruleId = raw.substringBefore("::")
                    val stepId = raw.substringAfter("::")
                    engine.updateStepParam(ruleId, stepId, localUri.toString())
                } else {
                    engine.updateRuleImageOverlay(raw, localUri.toString())
                }
                Toast.makeText(this, "✅ Overlay gambar dipasang", Toast.LENGTH_SHORT).show()
            }
            key.startsWith(PREFIX_RULE_SOUND) -> {
                val raw = key.removePrefix(PREFIX_RULE_SOUND)
                if (raw.contains("::")) {
                    val ruleId = raw.substringBefore("::")
                    val stepId = raw.substringAfter("::")
                    engine.updateStepParam(ruleId, stepId, localUri.toString())
                } else {
                    engine.updateRuleSoundEffect(raw, localUri.toString())
                }
                Toast.makeText(this, "✅ Sound effect dipasang", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modeName = intent?.getStringExtra(EXTRA_MODE).orEmpty()
        requestKey = intent?.getStringExtra(EXTRA_REQUEST_KEY).orEmpty()
        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME).orEmpty().ifBlank { "universal_presets.json" }

        val mode = try {
            FloatingPickerMode.valueOf(modeName)
        } catch (_: Throwable) {
            finish()
            return
        }

        when (mode) {
            FloatingPickerMode.PICK_IMAGE -> {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            FloatingPickerMode.PICK_AUDIO -> {
                audioPicker.launch(arrayOf("audio/*"))
            }
            FloatingPickerMode.IMPORT_PRESET_FILE -> {
                importPresetPicker.launch(arrayOf("*/*"))
            }
            FloatingPickerMode.EXPORT_PRESET_FILE -> {
                exportPresetCreator.launch(fileName)
            }
        }
    }
}
