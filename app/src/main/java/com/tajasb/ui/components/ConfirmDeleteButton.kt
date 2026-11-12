package com.tajasb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Tombol hapus ikonik dengan konfirmasi langsung pada tombol itu sendiri (tanpa pop-up).
 * - Klik ke-1: Tombol berubah warna merah dan menampilkan status konfirmasi ("Hapus?").
 * - Klik ke-2: Aksi penghapusan (onDelete) baru dieksekusi.
 * - Auto-reset ke keadaan awal setelah 3.5 detik jika tidak diklik lagi.
 */
@Composable
fun ConfirmDeleteIconButton(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Hapus",
    defaultTint: Color = Color(0xFFEF4444),
    iconSize: Dp = 16.dp,
    testTag: String = "btn_delete_item"
) {
    var isConfirming by remember { mutableStateOf(false) }

    LaunchedEffect(isConfirming) {
        if (isConfirming) {
            delay(3500)
            isConfirming = false
        }
    }

    if (isConfirming) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFDC2626))
                .clickable {
                    isConfirming = false
                    onDelete()
                }
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .testTag("${testTag}_confirm"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Konfirmasi Hapus",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Hapus?",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    } else {
        IconButton(
            onClick = { isConfirming = true },
            modifier = modifier
                .size(28.dp)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = contentDescription,
                tint = defaultTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Tombol tombol aksi (Kosongkan / Hapus Semua) dengan konfirmasi langsung pada tombol (tanpa pop-up).
 * - Klik ke-1: Tombol berubah menjadi merah menyala ("Yakin Hapus?").
 * - Klik ke-2: Aksi pembersihan (onClear) dijalankan.
 * - Auto-reset ke keadaan awal setelah 3.5 detik jika tidak diklik lagi.
 */
@Composable
fun ConfirmClearButton(
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Kosongkan",
    height: Dp = 36.dp,
    testTag: String = "btn_clear_all"
) {
    var isConfirming by remember { mutableStateOf(false) }

    LaunchedEffect(isConfirming) {
        if (isConfirming) {
            delay(3500)
            isConfirming = false
        }
    }

    if (isConfirming) {
        Button(
            onClick = {
                isConfirming = false
                onClear()
            },
            modifier = modifier
                .height(height)
                .testTag("${testTag}_confirm"),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFDC2626),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Yakin Hapus?",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    } else {
        OutlinedButton(
            onClick = { isConfirming = true },
            modifier = modifier
                .height(height)
                .testTag(testTag),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
