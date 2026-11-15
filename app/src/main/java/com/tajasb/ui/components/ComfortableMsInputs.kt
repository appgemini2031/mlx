package com.tajasb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan

/**
 * Input angka (ms / int) ramah ketik:
 * - Memperbolehkan hapus sampai kosong ("") atau ketik "0" / "200" tanpa langsung
 *   dipaksa kembali ke nilai fallback atau nilai minimum di tengah pengetikan.
 * - Normalisasi min/max hanya diterapkan saat field selesai diedit / blur bila kosong.
 */
@Composable
fun ComfortableMsInput(
    label: String,
    value: Long,
    onValueCommitted: (Long) -> Unit,
    minValue: Long = 0L,
    maxValue: Long = 120_000L,
    maxDigits: Int = 6,
    resetKey: Any? = Unit,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var rawText by remember(resetKey) { mutableStateOf(value.toString()) }
    var isFocused by remember(resetKey) { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            val parsed = rawText.toLongOrNull()
            if (parsed != value) {
                rawText = value.toString()
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun commitOnLeave() {
        val trimmed = rawText.trim()
        val resolved = if (trimmed.isEmpty()) {
            minValue
        } else {
            (trimmed.toLongOrNull() ?: minValue).coerceIn(minValue, maxValue)
        }
        rawText = resolved.toString()
        if (resolved != value) {
            onValueCommitted(resolved)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(DarkSurface)
            .border(0.7.dp, if (isFocused) NeonCyan else DarkCardBorder, RoundedCornerShape(5.dp))
            .clickable {
                onFocusChange(true)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value = rawText,
            onValueChange = { incoming ->
                val digits = incoming.filter(Char::isDigit).take(maxDigits)
                rawText = digits
                if (digits.isNotEmpty()) {
                    val v = digits.toLongOrNull() ?: 0L
                    val capped = v.coerceAtMost(maxValue)
                    if (v > maxValue) {
                        rawText = capped.toString()
                    }
                    // Izinkan ketik bertahap ("2" -> "20" -> "200") tanpa clamp minimum mid-typing
                    if (capped != value && capped >= minValue) {
                        onValueCommitted(capped)
                    } else if (minValue == 0L && capped != value) {
                        onValueCommitted(capped)
                    }
                } else if (minValue == 0L && value != 0L) {
                    onValueCommitted(0L)
                }
            },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(NeonCyan),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    commitOnLeave()
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onFocusChange(false)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 16.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { fs ->
                    val focusedNow = fs.isFocused
                    if (isFocused && !focusedNow) {
                        commitOnLeave()
                    }
                    isFocused = focusedNow
                    onFocusChange(focusedNow)
                    if (focusedNow) {
                        keyboardController?.show()
                    }
                }
        )
    }
}

@Composable
fun OutlinedMsTextField(
    label: String,
    value: Long,
    onValueCommitted: (Long) -> Unit,
    minValue: Long = 0L,
    maxValue: Long = 120_000L,
    maxDigits: Int = 6,
    resetKey: Any? = Unit,
    modifier: Modifier = Modifier
) {
    var rawText by remember(resetKey) { mutableStateOf(value.toString()) }
    var isFocused by remember(resetKey) { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            val parsed = rawText.toLongOrNull()
            if (parsed != value) {
                rawText = value.toString()
            }
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun commitOnLeave() {
        val trimmed = rawText.trim()
        val resolved = if (trimmed.isEmpty()) {
            minValue
        } else {
            (trimmed.toLongOrNull() ?: minValue).coerceIn(minValue, maxValue)
        }
        rawText = resolved.toString()
        if (resolved != value) {
            onValueCommitted(resolved)
        }
    }

    OutlinedTextField(
        value = rawText,
        onValueChange = { incoming ->
            val digits = incoming.filter(Char::isDigit).take(maxDigits)
            rawText = digits
            if (digits.isNotEmpty()) {
                val v = digits.toLongOrNull() ?: 0L
                val capped = v.coerceAtMost(maxValue)
                if (v > maxValue) {
                    rawText = capped.toString()
                }
                if (capped >= minValue && capped != value) {
                    onValueCommitted(capped)
                }
            } else if (minValue == 0L && value != 0L) {
                onValueCommitted(0L)
            }
        },
        label = { Text(label, fontSize = 8.5.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                commitOnLeave()
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        ),
        modifier = modifier.onFocusChanged { fs ->
            val focusedNow = fs.isFocused
            if (isFocused && !focusedNow) {
                commitOnLeave()
            }
            isFocused = focusedNow
        }
    )
}
