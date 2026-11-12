package com.tajasb.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.tajasb.photostudio.EffectAnimSpeed
import com.tajasb.photostudio.ImageMotionSpeed
import com.tajasb.photostudio.OutputDurationPreset
import com.tajasb.photostudio.PhotoAnimEffect
import com.tajasb.photostudio.PhotoStudioManager
import com.tajasb.photostudio.PhotoStudioRenderer
import kotlinx.coroutines.isActive

@Composable
fun PhotoStudioCard(
    onUserMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val studioManager = remember { PhotoStudioManager.getInstance(context) }
    val state by studioManager.state.collectAsState()
    val previewBitmaps by studioManager.previewBitmaps.collectAsState()

    val multiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            studioManager.onPhotosPicked(uris, append = true)
            onUserMessage("📸 ${uris.size} foto ditambahkan ke Studio Animasi")
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("photo_studio_card"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(
            width = if (state.isRendering) 1.2.dp else 0.8.dp,
            color = if (state.isRendering) EmeraldGreen else DarkCardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // HEADER ROW (Accordion Toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { studioManager.toggleExpanded() }
                    .testTag("photo_studio_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonCyan.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "✨ STUDIO",
                            color = NeonCyan,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Studio Foto + Efek Animasi (Ke VCam)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (state.isRendering) {
                                "⏳ Merender MP4: ${state.renderProgressPercent}% (${state.renderedFrames}/${state.totalFrames} frame)"
                            } else {
                                "${state.selectedEffect.icon} ${state.selectedEffect.title} • ${state.selectedPhotos.size} Foto • ${state.durationPreset.label}"
                            },
                            color = if (state.isRendering) EmeraldGreen else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = if (state.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Buka/Tutup Studio Foto",
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Progress bar always visible even when card is collapsed if rendering in background
            if (state.isRendering) {
                LinearProgressIndicator(
                    progress = { (state.renderProgressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = EmeraldGreen,
                    trackColor = DarkSurfaceVariant
                )
            }

            if (state.isExpanded) {
                // 1. LIVE PREVIEW INTERAKTIF (9:16) + PHOTO PICKER SIDE-BY-SIDE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 9:16 Live Preview Box
                    var previewTimeSec by remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(state.isExpanded) {
                        val startNanos = System.nanoTime()
                        while (isActive) {
                            withFrameNanos { now ->
                                previewTimeSec = (now - startNanos) / 1_000_000_000f
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(116.dp)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .testTag("photo_studio_live_preview_box")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawIntoCanvas { canvas ->
                                PhotoStudioRenderer.drawStudioFrame(
                                    canvas = canvas.nativeCanvas,
                                    width = size.width,
                                    height = size.height,
                                    bitmaps = previewBitmaps,
                                    timeSec = previewTimeSec,
                                    totalDurationSec = state.durationPreset.seconds.toFloat(),
                                    effect = state.selectedEffect,
                                    imageSpeedMultiplier = state.imageSpeed.multiplier,
                                    effectSpeedMultiplier = state.effectSpeed.multiplier
                                )
                            }
                        }

                        // HUD Badge inside 9:16 preview
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xCC090D16))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "9:16 LIVE",
                                color = EmeraldGreen,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0xCC090D16))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${state.selectedEffect.icon} ${state.selectedEffect.title}",
                                color = Color.White,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right Column: Photo Selection & Preset Management
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { multiPhotoLauncher.launch("image/*") },
                            shape = RoundedCornerShape(7.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color(0xFF090D16)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("photo_studio_pick_photos_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (state.selectedPhotos.isEmpty()) "Pilih Foto Galeri" else "Tambah Foto (${state.selectedPhotos.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        if (state.selectedPhotos.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                state.selectedPhotos.forEachIndexed { idx, item ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkSurfaceVariant)
                                            .border(0.6.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "#${idx + 1} ${item.displayName}",
                                            color = Color.White,
                                            fontSize = 8.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.width(72.dp)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hapus Foto",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable { studioManager.removePhoto(item.id) }
                                        )
                                    }
                                }
                            }
                        }

                        // Preset & Effect Pack Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    studioManager.saveCurrentPreset { msg -> onUserMessage(msg) }
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                border = BorderStroke(0.7.dp, NeonPurple.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("photo_studio_save_preset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdded,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Simpan Preset", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    studioManager.downloadEffectPack(state.selectedEffect) { msg ->
                                        onUserMessage(msg)
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                border = BorderStroke(0.7.dp, NeonCyan.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("photo_studio_download_effect_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Simpan Efek", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                studioManager.downloadAllEffects { msg -> onUserMessage(msg) }
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            border = BorderStroke(0.7.dp, EmeraldGreen.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("photo_studio_download_all_effects_button")
                        ) {
                            Text(
                                text = "📦 Download Semua Efek (${state.downloadedEffectIds.size}/${PhotoAnimEffect.entries.size} Tersimpan)",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        state.savedPresetSummary?.let { presetText ->
                            Text(
                                text = "🔖 Preset Aktif: $presetText",
                                color = Color(0xFFA5B4FC),
                                fontSize = 8.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 2. PILIHAN 14 EFEK ANIMASI LAYOUT
                Text(
                    text = "A. Pilihan Efek Animasi Layout (14 Mode Atmosfer & Partikel):",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PhotoAnimEffect.entries.forEach { effect ->
                        val isSelected = state.selectedEffect == effect
                        val isDownloaded = state.downloadedEffectIds.contains(effect.id)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceVariant
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.6.dp,
                                    color = if (isSelected) NeonCyan else DarkCardBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { studioManager.selectEffect(effect) }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                                .testTag("photo_effect_chip_${effect.id.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${effect.icon} ${effect.title}",
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isDownloaded) {
                                    Text(
                                        text = "✓",
                                        color = EmeraldGreen,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. KONTROL KECEPATAN GERAK FOTO (TERPISAH DARI KECEPATAN ANIMASI)
                Text(
                    text = "B. Kecepatan Gerak / Transisi Foto (Ken Burns & Slideshow):",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ImageMotionSpeed.entries.forEach { spd ->
                        val selected = state.imageSpeed == spd
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) NeonPurple.copy(alpha = 0.22f) else DarkSurfaceVariant)
                                .border(
                                    width = if (selected) 0.9.dp else 0.5.dp,
                                    color = if (selected) NeonPurple else DarkCardBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { studioManager.setImageSpeed(spd) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("photo_img_speed_${spd.id.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = spd.label,
                                color = if (selected) Color.White else Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 4. KONTROL KECEPATAN ANIMASI EFEK PARTIKEL
                Text(
                    text = "C. Kecepatan Animasi Efek Partikel:",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    EffectAnimSpeed.entries.forEach { fxSpd ->
                        val selected = state.effectSpeed == fxSpd
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                .border(
                                    width = if (selected) 0.9.dp else 0.5.dp,
                                    color = if (selected) NeonCyan else DarkCardBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { studioManager.setEffectSpeed(fxSpd) }
                                .padding(vertical = 5.dp)
                                .testTag("photo_fx_speed_${fxSpd.id.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fxSpd.label,
                                color = if (selected) Color.White else Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 5. PILIHAN DURASI OUTPUT MP4 (5d, 8d, 10d, 15d, 30d, 60d)
                Text(
                    text = "D. Durasi Video Output MP4 (720x1280 Portrait • 25 FPS Akurat):",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    OutputDurationPreset.entries.forEach { dur ->
                        val selected = state.durationPreset == dur
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) EmeraldGreen.copy(alpha = 0.22f) else DarkSurfaceVariant)
                                .border(
                                    width = if (selected) 0.9.dp else 0.5.dp,
                                    color = if (selected) EmeraldGreen else DarkCardBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { studioManager.setDurationPreset(dur) }
                                .padding(vertical = 5.dp)
                                .testTag("photo_duration_${dur.seconds}s"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dur.label,
                                color = if (selected) Color.White else Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 6. TOMBOL RENDER KE MP4 VCAM & SIMPAN KE FOLDER MOVIES/DOWNLOAD
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            studioManager.renderToVcamMp4 { _, msg ->
                                onUserMessage(msg)
                            }
                        },
                        enabled = !state.isRendering,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreen,
                            contentColor = Color(0xFF090D16)
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("photo_studio_render_mp4_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieCreation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.isRendering) "Merender ${state.renderProgressPercent}%..." else "Render ke MP4 VCam",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            studioManager.saveLastMp4ToMoviesOrDownload { _, msg ->
                                onUserMessage(msg)
                            }
                        },
                        enabled = !state.isRendering && !state.lastRenderedMp4Path.isNullOrBlank(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        border = BorderStroke(0.8.dp, NeonCyan.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("photo_studio_save_movies_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Simpan ke Movies",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Status message bar
                Text(
                    text = state.statusMessage,
                    color = Color(0xFFCBD5E1),
                    fontSize = 8.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
