package com.tajasb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.tajasb.ui.VcamViewModel
import com.tajasb.ui.components.ConfirmClearButton

/**
 * Kartu Ringkasan Aplikasi Target di Halaman Menu Utama.
 * - Tampilan ringkas, bersih, dan tidak memakan banyak tempat
 * - Menampilkan status aplikasi target yang terpilih
 * - Tombol untuk membuka Pop-Up Pengaturan Aplikasi Target
 */
@Composable
fun TargetAppsCard(
    viewModel: VcamViewModel,
    modifier: Modifier = Modifier
) {
    val apps by viewModel.targetApps.collectAsState()
    val config by viewModel.config.collectAsState()
    var showPopup by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            viewModel.loadInstalledApps()
        }
    }

    val selectedCount = remember(apps) { apps.count { it.isSelected } }
    val primaryAppName = remember(apps, config.primaryTargetPackage) {
        apps.find { it.packageName == config.primaryTargetPackage }?.appName
            ?: config.primaryTargetPackage.ifBlank { "Global" }
    }

    if (showPopup) {
        TargetAppsDialog(
            viewModel = viewModel,
            onDismiss = { showPopup = false }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPopup = true },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(NeonCyan.copy(alpha = 0.14f))
                        .border(0.6.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "Aplikasi Target",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                com.tajasb.ui.components.FeatureInfoButton(
                    title = "Aplikasi Target (Scope)",
                    description = "• Menentukan aplikasi mana saja yang di-hook oleh Kamera Virtual TAJASB.\n\n" +
                        "• Jika belum ada yang dipilih (Global), semua aplikasi kamera yang dicentang di LSPosed akan menerima aliran video virtual.\n\n" +
                        "• Utama: $primaryAppName",
                    accentColor = NeonCyan
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (selectedCount > 0) EmeraldGreen.copy(alpha = 0.14f) else DarkSurfaceVariant)
                        .border(
                            0.6.dp,
                            if (selectedCount > 0) EmeraldGreen.copy(alpha = 0.45f) else DarkCardBorder,
                            RoundedCornerShape(5.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (selectedCount > 0) "$selectedCount App" else "Global",
                        color = if (selectedCount > 0) EmeraldGreen else Color(0xFF94A3B8),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = { showPopup = true },
                modifier = Modifier
                    .height(27.dp)
                    .testTag("btn_open_target_apps_popup"),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkSurfaceVariant,
                    contentColor = NeonCyan
                ),
                border = androidx.compose.foundation.BorderStroke(0.7.dp, NeonCyan.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Atur",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.5.sp
                )
            }
        }
    }
}

/**
 * Pop-up Dialog Pemilihan Aplikasi Target Lengkap.
 * - Logika multi-select: Pilih Semua, Hapus Semua, dan Pilih Target Utama
 * - Tidak dibatasi hanya 1 target
 * - Fitur pencarian instan berdasarkan nama aplikasi dan package name
 */
@Composable
fun TargetAppsDialog(
    viewModel: VcamViewModel,
    onDismiss: () -> Unit
) {
    val apps by viewModel.targetApps.collectAsState()
    val config by viewModel.config.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadInstalledApps()
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val distinctFilteredApps = remember(filteredApps) {
        filteredApps.distinctBy { "${it.packageName}#${it.appName}#${it.category}" }
    }

    val selectedCount = remember(apps) { apps.count { it.isSelected } }
    val primaryAppName = remember(apps, config.primaryTargetPackage) {
        apps.find { it.packageName == config.primaryTargetPackage }?.appName
            ?: config.primaryTargetPackage.ifBlank { "Belum dipilih" }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .imePadding()
                .heightIn(max = 580.dp)
                .clip(RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // 1. Header with Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PILIH APLIKASI TARGET",
                            color = NeonCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.loadInstalledApps() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan Ulang",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (config.primaryTargetPackage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonCyan.copy(alpha = 0.15f))
                            .border(0.5.dp, NeonCyan, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🚀 Utama: $primaryAppName",
                            color = NeonCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari aplikasi / package...", color = Color.Gray, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedIndicatorColor = NeonCyan,
                        unfocusedIndicatorColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_popup_target_apps")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Multi-Select Action Bar (Pilih Semua & Hapus Semua)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$selectedCount / ${apps.size} terpilih",
                            color = if (selectedCount > 0) EmeraldGreen else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.selectAllApps(true) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("btn_popup_select_all"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                        ) {
                            Text("Pilih Semua", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        ConfirmClearButton(
                            onClear = { viewModel.selectAllApps(false) },
                            text = "Hapus Semua",
                            height = 30.dp,
                            testTag = "btn_popup_clear_all"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Scrollable List of Apps with Multi-Checkboxes & Primary Target selector
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (searchQuery.isNotBlank()) {
                        val trimmed = searchQuery.trim()
                        val hasExactMatch = filteredApps.any {
                            it.packageName.equals(trimmed, ignoreCase = true) || it.appName.equals(trimmed, ignoreCase = true)
                        }
                        if (!hasExactMatch) {
                            item(key = "custom_add_candidate") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            viewModel.addCustomTargetPackage(trimmed)
                                            searchQuery = ""
                                        },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = CardDefaults.cardColors(containerColor = NeonCyan.copy(alpha = 0.16f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddCircle,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Tambahkan \"$trimmed\" Sebagai Target",
                                                color = NeonCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Klik di sini untuk langsung mengaitkan nama / package kloning MochiCloner",
                                                color = Color.LightGray,
                                                fontSize = 9.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filteredApps.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank())
                                        "Aplikasi \"$searchQuery\" belum terdeteksi otomatis"
                                    else
                                        "Tidak ada aplikasi ditemukan",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                if (searchQuery.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            viewModel.addCustomTargetPackage(searchQuery.trim())
                                            searchQuery = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tambahkan \"$searchQuery\" Sekarang", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = distinctFilteredApps,
                            key = { index, app -> "${app.packageName}_${app.category}_${app.appName}_$index" }
                        ) { _, app ->
                            val isPrimary = config.primaryTargetPackage == app.packageName
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.toggleTargetApp(app.packageName) },
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPrimary) NeonCyan.copy(alpha = 0.12f)
                                    else if (app.isSelected) DarkSurfaceVariant.copy(alpha = 0.7f)
                                    else DarkSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isPrimary) 1.dp else 0.5.dp,
                                    if (isPrimary) NeonCyan
                                    else if (app.isSelected) NeonCyan.copy(alpha = 0.5f)
                                    else DarkCardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isPrimary) NeonCyan.copy(alpha = 0.25f)
                                                    else if (app.isSelected) NeonCyan.copy(alpha = 0.15f)
                                                    else DarkSurfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = app.appName.firstOrNull()?.uppercase() ?: "A",
                                                color = if (isPrimary || app.isSelected) NeonCyan else Color.LightGray,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = app.appName,
                                                    color = if (app.isSelected) Color.White else Color.LightGray,
                                                    fontWeight = if (isPrimary || app.isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(NeonPurple.copy(alpha = 0.15f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = app.category,
                                                        color = NeonPurple,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }

                                            Text(
                                                text = app.packageName,
                                                color = Color.Gray,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Set Primary Button
                                        if (app.isSelected) {
                                            if (isPrimary) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(NeonCyan.copy(alpha = 0.25f))
                                                        .border(1.dp, NeonCyan, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "🎯 Utama",
                                                        color = NeonCyan,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(DarkSurfaceVariant)
                                                        .border(0.5.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                        .clickable { viewModel.setPrimaryTargetApp(app.packageName) }
                                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "Jadikan Utama",
                                                        color = Color.LightGray,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }

                                        // Multi-select Checkbox
                                        Checkbox(
                                            checked = app.isSelected,
                                            onCheckedChange = { viewModel.toggleTargetApp(app.packageName) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = NeonCyan,
                                                checkmarkColor = Color.Black,
                                                uncheckedColor = Color.Gray
                                            ),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 5. Done / Dismiss Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Selesai & Simpan", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
            }
        }
    }
}
