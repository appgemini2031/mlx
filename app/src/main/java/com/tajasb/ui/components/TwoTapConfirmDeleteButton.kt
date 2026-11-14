package com.tajasb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val ConfirmRedSolid = Color(0xFFDC2626)
private val ConfirmRedBorder = Color(0xFFFCA5A5)

/**
 * Tombol pill hapus 2x klik dengan ukuran IDENTIK antara kondisi normal & konfirmasi merah.
 * Klik ke-1 -> berubah merah solid (#DC2626) selama 3 detik.
 * Klik ke-2 (saat masih merah) -> mengeksekusi [onConfirm].
 * Jika tidak diklik ke-2x dalam 3 detik -> batal / kembali normal.
 */
@Composable
fun ConfirmDeleteActionPill(
    text: String = "✕",
    idleTint: Color = Color(0xFFEF4444),
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000L)
            armed = false
        }
    }

    val bg = if (armed) ConfirmRedSolid else idleTint.copy(alpha = 0.18f)
    val border = if (armed) ConfirmRedBorder else idleTint.copy(alpha = 0.7f)
    val fg = if (armed) Color.White else idleTint

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(if (armed) 1.dp else 0.7.dp, border, RoundedCornerShape(6.dp))
            .clickable(role = Role.Button) {
                if (!armed) {
                    armed = true
                } else {
                    armed = false
                    onConfirm()
                }
            }
            .semantics {
                contentDescription = if (armed) "Klik lagi untuk konfirmasi hapus" else "Hapus"
            }
            .padding(horizontal = 7.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

/**
 * Tombol chip teks hapus 2x klik dengan ukuran IDENTIK antara normal & konfirmasi merah.
 */
@Composable
fun ConfirmDeleteTextChip(
    label: String = "Hapus",
    idleBg: Color = Color(0xFF991B1B),
    idleFg: Color = Color.White,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000L)
            armed = false
        }
    }

    val bg = if (armed) ConfirmRedSolid else idleBg
    val fg = if (armed) Color.White else idleFg
    val borderColor = if (armed) ConfirmRedBorder else idleBg.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(role = Role.Button) {
                if (!armed) {
                    armed = true
                } else {
                    armed = false
                    onConfirm()
                }
            }
            .semantics {
                contentDescription = if (armed) "Klik lagi untuk konfirmasi $label" else label
            }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * IconButton hapus 2x klik dengan ukuran IDENTIK.
 */
@Composable
fun ConfirmDeleteIconButton(
    contentDescriptionText: String = "Hapus",
    size: Dp = 26.dp,
    iconSize: Dp = 15.dp,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000L)
            armed = false
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(if (armed) ConfirmRedSolid else Color.Transparent)
            .border(
                width = if (armed) 1.dp else 0.dp,
                color = if (armed) ConfirmRedBorder else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(role = Role.Button) {
                if (!armed) {
                    armed = true
                } else {
                    armed = false
                    onConfirm()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = if (armed) "Klik lagi untuk konfirmasi hapus" else contentDescriptionText,
            tint = if (armed) Color.White else Color(0xFFEF4444),
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * TextButton hapus 2x klik dengan ukuran IDENTIK antara kondisi normal & merah konfirmasi.
 */
@Composable
fun ConfirmDeleteTextButton(
    label: String = "Hapus",
    fontSize: TextUnit = 10.sp,
    idleFg: Color = Color(0xFFFF6B6B),
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 6.dp,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000L)
            armed = false
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (armed) ConfirmRedSolid else Color.Transparent)
            .border(
                width = 0.8.dp,
                color = if (armed) ConfirmRedBorder else idleFg.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(role = Role.Button) {
                if (!armed) {
                    armed = true
                } else {
                    armed = false
                    onConfirm()
                }
            }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (armed) Color.White else idleFg,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
