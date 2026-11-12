package com.tajasb.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.tajasb.license.LicenseManager
import com.tajasb.model.ActivationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dialog Sinkronisasi Sistem (Ketuk 7x Bar Bawah).
 *
 * FITUR UTAMA:
 * 1. Samaran Aman: Menggunakan judul "Sinkronisasi Sistem" tanpa kata "Gerbang Admin".
 * 2. Anti-Keluar Tidak Sengaja: Ketukan ke-8 atau sentuhan di luar dialog TIDAK akan menutup pop-up.
 * 3. Tampilan ID Perangkat + Salin 1-Klik: Memudahkan salin HWID saat Firebase sedang gangguan/offline.
 * 4. Input Ganda: Mendukung tempel Kunci Lisensi VIP khusus (TJ-VIP-... / TJ-LIC-... / TJ-CMD-...)
 *    sekaligus kode 6-digit sinkronisasi.
 */
@Composable
fun EmergencyTotpDialog(
    onDismiss: () -> Unit,
    onSuccessActivated: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val licenseManager = remember { LicenseManager.getInstance(context) }
    val deviceId = remember { licenseManager.getDeviceId() }

    var syncInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var idCopied by remember { mutableStateOf(false) }

    var failedAttempts by remember { mutableIntStateOf(0) }
    var isLockedOut by remember { mutableStateOf(false) }
    var lockoutSeconds by remember { mutableIntStateOf(0) }

    val performVibration = { ms: Long ->
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(ms)
                }
            }
        } catch (_: Throwable) {}
    }

    // Countdown timer saat terkunci karena 5x salah
    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            lockoutSeconds = 180 // 3 menit
            while (lockoutSeconds > 0) {
                delay(1000)
                lockoutSeconds--
            }
            isLockedOut = false
            failedAttempts = 0
            errorMessage = null
        }
    }

    LaunchedEffect(idCopied) {
        if (idCopied) {
            delay(2000)
            idCopied = false
        }
    }

    val verifyInputNow: (String) -> Unit = { rawVal ->
        if (!isLockedOut && !isVerifying && !isSuccess) {
            val trimmed = rawVal.trim()
            if (trimmed.isBlank()) {
                errorMessage = "Masukkan kode 6-digit atau tempel kunci lisensi VIP."
                performVibration(80L)
            } else {
                isVerifying = true
                errorMessage = null
                coroutineScope.launch {
                    delay(180)
                    val result = licenseManager.activateSystemSyncInput(trimmed)
                    isVerifying = false

                    when (result) {
                        is ActivationResult.Success -> {
                            isSuccess = true
                            failedAttempts = 0
                            performVibration(120L)
                            delay(850)
                            onSuccessActivated(result.message)
                            onDismiss()
                        }
                        is ActivationResult.Error -> {
                            failedAttempts++
                            performVibration(100L)
                            if (failedAttempts >= 5) {
                                isLockedOut = true
                                errorMessage = "⛔ Terlalu banyak percobaan gagal. Tunggu 3 menit."
                            } else {
                                val sisa = 5 - failedAttempts
                                errorMessage = "${result.message} (Sisa percobaan: $sisa)"
                            }
                        }
                    }
                }
            }
        }
    }

    val isNumericMode = remember(syncInput) {
        val clean = syncInput.trim()
        clean.isEmpty() || clean.all { it.isDigit() || it == ' ' }
    }

    val isReadyToSubmit = remember(syncInput) {
        val clean = syncInput.trim()
        val digitsOnly = clean.replace(" ", "")
        (digitsOnly.length == 6 && digitsOnly.all { it.isDigit() }) ||
            clean.length >= 10
    }

    Dialog(
        onDismissRequest = {
            // Kosongkan agar ketukan ke-8, ke-9, atau sentuhan di luar kotak dialog
            // TIDAK PERNAH menutup pop-up secara tidak sengaja.
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .border(
                    width = 1.2.dp,
                    color = if (isSuccess) EmeraldGreen else NeonCyan.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isSuccess) EmeraldGreen.copy(alpha = 0.18f) else NeonCyan.copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            if (isSuccess) EmeraldGreen else NeonCyan.copy(alpha = 0.5f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSuccess) "✓" else "⚙️",
                        fontSize = 22.sp,
                        color = if (isSuccess) EmeraldGreen else NeonCyan
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isSuccess) "Sinkronisasi Sistem Selesai" else "Sinkronisasi Sistem",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isSuccess)
                        "Konfigurasi perangkat telah disinkronkan dan diaktifkan secara penuh."
                    else
                        "Masukkan kode sinkronisasi 6-digit atau tempel kunci konfigurasi VIP perangkat:",
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Box ID Perangkat + Tombol Salin (untuk memudahkan generate lisensi VIP saat Firebase gangguan)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface.copy(alpha = 0.75f))
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ID PERANGKAT HP INI",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = deviceId,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = NeonCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .testTag("copy_device_id_button")
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (idCopied) EmeraldGreen.copy(alpha = 0.2f)
                                else NeonCyan.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (idCopied) EmeraldGreen else NeonCyan.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("ID Perangkat", deviceId))
                                    idCopied = true
                                    performVibration(35L)
                                } catch (_: Throwable) {}
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (idCopied) "✓ Tersalin" else "📋 Salin ID",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (idCopied) EmeraldGreen else NeonCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Baris Tombol Tempel Cepat & Bersihkan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNumericMode && syncInput.isNotEmpty()) "Mode Kode 6-Digit" else "Kode 6-Digit / Lisensi VIP",
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (syncInput.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .testTag("clear_sync_input_button")
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                    .clickable(enabled = !isVerifying && !isSuccess) {
                                        syncInput = ""
                                        errorMessage = null
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "✕ Hapus",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFF87171),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .testTag("paste_sync_input_button")
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonCyan.copy(alpha = 0.14f))
                                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .clickable(enabled = !isVerifying && !isSuccess && !isLockedOut) {
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim() ?: ""
                                        if (pasted.isNotEmpty()) {
                                            syncInput = pasted
                                            errorMessage = null
                                            performVibration(30L)
                                            val digits = pasted.replace(" ", "").replace("-", "")
                                            if ((digits.length == 6 && digits.all { it.isDigit() }) ||
                                                pasted.startsWith("TJ-", ignoreCase = true) ||
                                                pasted.startsWith("UNKILL-", ignoreCase = true)
                                            ) {
                                                keyboardController?.hide()
                                                verifyInputNow(pasted)
                                            }
                                        } else {
                                            errorMessage = "Clipboard kosong. Salin kunci lisensi atau kode terlebih dahulu."
                                        }
                                    } catch (_: Throwable) {}
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "📋 Tempel Kunci / Kode",
                                fontSize = 10.5.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Input Field (Mendukung 6-digit angka MAUPUN Kunci Lisensi TJ-VIP-... / TJ-LIC-...)
                OutlinedTextField(
                    value = syncInput,
                    onValueChange = { input ->
                        if (!isLockedOut) {
                            val cleaned = input.replace("\n", "").replace("\r", "")
                            syncInput = cleaned
                            errorMessage = null
                            val trimmed = cleaned.trim()
                            // Jika pengguna mengetik 6 digit angka murni, langsung verifikasi otomatis
                            if (trimmed.length == 6 && trimmed.all { it.isDigit() }) {
                                keyboardController?.hide()
                                verifyInputNow(trimmed)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("system_sync_input_field"),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = if (isNumericMode && syncInput.length <= 6) 22.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = if (isNumericMode && syncInput.length <= 6) TextAlign.Center else TextAlign.Start,
                        letterSpacing = if (isNumericMode && syncInput.length <= 6) 6.sp else 0.5.sp
                    ),
                    maxLines = 2,
                    placeholder = {
                        Text(
                            text = "6-Digit atau Tempel TJ-VIP-...",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = Color.Gray.copy(alpha = 0.55f)
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            verifyInputNow(syncInput)
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isLockedOut) Color(0xFFEF4444) else NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        errorBorderColor = Color(0xFFEF4444),
                        focusedContainerColor = DarkSurface.copy(alpha = 0.8f),
                        unfocusedContainerColor = DarkSurface.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    isError = errorMessage != null || isLockedOut,
                    enabled = !isVerifying && !isSuccess && !isLockedOut
                )

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = if (isLockedOut)
                            "⛔ Sinkronisasi ditangguhkan! Tunggu ${lockoutSeconds / 60}m ${lockoutSeconds % 60}s"
                        else
                            errorMessage ?: "",
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("close_sync_dialog_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        enabled = !isVerifying
                    ) {
                        Text("Tutup", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    ElevatedButton(
                        onClick = {
                            keyboardController?.hide()
                            verifyInputNow(syncInput)
                        },
                        modifier = Modifier
                            .weight(1.35f)
                            .height(48.dp)
                            .testTag("submit_sync_dialog_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (isSuccess) EmeraldGreen else NeonCyan,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        enabled = isReadyToSubmit && !isVerifying && !isSuccess && !isLockedOut
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isSuccess) "Tersinkron!" else "Sinkronkan",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

