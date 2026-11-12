package com.tajasb.ui.components

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.tajasb.model.TargetAppInfo
import com.tajasb.optimizer.AppAdBlockerManager
import com.tajasb.optimizer.LiveFocusVpnService
import com.tajasb.ui.VcamViewModel

@Composable
fun AppAdBlockerCard(
    viewModel: VcamViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val installedApps by viewModel.targetApps.collectAsState()

    var showAddAppDialog by remember { mutableStateOf(false) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleAdBlocker(true)
        }
    }

    LaunchedEffect(Unit) {
        if (installedApps.isEmpty()) {
            viewModel.loadInstalledApps()
        }
    }

    val isBlockerActive = config.isAdBlockerActive
    val blockedPackages = config.blockedAdPackages
    val pm = remember { context.packageManager }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_app_ad_blocker"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isBlockerActive) Color(0xFFF43F5E).copy(alpha = 0.45f) else DarkCardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // BARIS 1: HEADER SERAGAM (Badge Ikon 26.dp + Judul 11.sp + Info + Tombol Tambah + Switch)
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .background(Color(0xFFF43F5E).copy(alpha = 0.14f))
                            .border(0.6.dp, Color(0xFFF43F5E).copy(alpha = 0.35f), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = if (isBlockerActive) Color(0xFFF43F5E) else Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "Blokir Iklan APK",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    FeatureInfoButton(
                        title = "Pemblokir Data & Pop-up Iklan Aplikasi",
                        description = "• Putus Data Total (0 KB/s): Mengarahkan paket internet aplikasi pembuat iklan ke VPN Blackhole sehingga aplikasi sama sekali tidak bisa mengunduh konten iklan atau banner saat Anda live.\n\n" +
                            "• Tanpa Ganggu Aplikasi Lain: Berbeda dari VPN biasa, mode ini HANYA memblokir aplikasi iklan yang Anda tentukan. TikTok, Shopee, browser, dan internet lainnya tetap berjalan normal 100% dengan kecepatan penuh.\n\n" +
                            "• Paksa Berhenti (Kill): Menghentikan proses latar belakang aplikasi iklan seketika agar tidak menjalankan timer iklan.\n\n" +
                            "• Cabut Izin Pop-up (Overlay): Tekan tombol 'Pop-up' untuk mencabut izin 'Tampilkan di Atas Aplikasi Lain' agar aplikasi tidak bisa memunculkan jendela iklan di atas layar live.\n\n" +
                            "• Perlindungan Root (Opsional): Jika HP di-root, aturan kernel iptables DROP & netpolicy OS juga diterapkan secara permanen.",
                        accentColor = Color(0xFFF43F5E)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (blockedPackages.isNotEmpty()) Color(0xFFF43F5E).copy(alpha = 0.14f) else Color(0xFF1E293B))
                            .border(
                                0.6.dp,
                                if (blockedPackages.isNotEmpty()) Color(0xFFF43F5E).copy(alpha = 0.4f) else DarkCardBorder,
                                RoundedCornerShape(5.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${blockedPackages.size} APK",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (blockedPackages.isNotEmpty()) Color(0xFFFDA4AF) else Color(0xFF94A3B8)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .border(0.7.dp, Color(0xFFF43F5E).copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .clickable { showAddAppDialog = true }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFFFDA4AF),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Pilih APK",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFDA4AF)
                            )
                        }
                    }

                    Switch(
                        checked = isBlockerActive,
                        onCheckedChange = { checked ->
                            if (checked) {
                                val prepIntent = LiveFocusVpnService.getPrepareIntent(context)
                                if (prepIntent != null) {
                                    vpnLauncher.launch(prepIntent)
                                } else {
                                    viewModel.toggleAdBlocker(true)
                                }
                            } else {
                                viewModel.toggleAdBlocker(false)
                            }
                        },
                        modifier = Modifier
                            .scale(0.68f)
                            .height(22.dp)
                            .testTag("switch_app_ad_blocker")
                    )
                }
            }

            // DAFTAR APLIKASI YANG DIBLOKIR (Tampil ringkas hanya jika ada aplikasi terdaftar)
            if (blockedPackages.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (pkg in blockedPackages) {
                        val appLabel = try {
                            val ai = pm.getApplicationInfo(pkg, 0)
                            pm.getApplicationLabel(ai).toString()
                        } catch (_: Throwable) {
                            pkg
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF090D16))
                                .border(0.6.dp, DarkCardBorder, RoundedCornerShape(7.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = appLabel,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFF43F5E).copy(alpha = 0.16f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "0 KB/s",
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFDA4AF)
                                        )
                                    }
                                }
                                Text(
                                    text = pkg,
                                    fontSize = 8.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF1E293B))
                                        .clickable { viewModel.forceStopBlockedApp(pkg, appLabel) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Kill",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFDA4AF)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF1E293B))
                                        .clickable { viewModel.openAppOverlaySettings(pkg) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Pop-up",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFDE047)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.removeBlockedAdPackage(pkg, appLabel) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus Blokir",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG PILIH APLIKASI PEMBUAT IKLAN
    if (showAddAppDialog) {
        Dialog(
            onDismissRequest = { showAddAppDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.7f))
            ) {
                var searchQuery by remember { mutableStateOf("") }
                var manualPackageInput by remember { mutableStateOf("") }

                val filteredApps = remember(searchQuery, installedApps) {
                    if (searchQuery.isBlank()) {
                        installedApps
                    } else {
                        installedApps.filter {
                            it.appName.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header Dialog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Pilih Aplikasi Pembuat Iklan",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        IconButton(
                            onClick = { showAddAppDialog = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "Pilih aplikasi yang memunculkan iklan paksaan di HP Anda, atau ketik nama paketnya jika tersembunyi. Akses data aplikasi akan langsung diputus total.",
                        fontSize = 9.sp,
                        color = Color(0xFF94A3B8)
                    )

                    // Kolom Pencarian Cepat
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Cari nama aplikasi atau nama paket...", fontSize = 10.sp, color = Color.Gray) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFEF4444)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )

                    // Input Manual Nama Paket (Jika aplikasi tersembunyi / bloatware)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualPackageInput,
                            onValueChange = { manualPackageInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ketik nama paket (mis. com.ad.ware)", fontSize = 9.sp, color = Color.Gray) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )

                        Button(
                            onClick = {
                                if (manualPackageInput.isNotBlank()) {
                                    viewModel.addBlockedAdPackage(manualPackageInput.trim())
                                    manualPackageInput = ""
                                }
                            },
                            enabled = manualPackageInput.isNotBlank(),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Blokir", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // Daftar Aplikasi Terpasang
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps) { app ->
                            val isAlreadyBlocked = blockedPackages.contains(app.packageName)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isAlreadyBlocked) {
                                            viewModel.removeBlockedAdPackage(app.packageName, app.appName)
                                        } else {
                                            viewModel.addBlockedAdPackage(app.packageName, app.appName)
                                        }
                                    },
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isAlreadyBlocked) Color(0xFF7F1D1D).copy(alpha = 0.35f) else Color(0xFF1E293B)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    0.5.dp,
                                    if (isAlreadyBlocked) Color(0xFFEF4444) else Color(0xFF334155)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            fontSize = 8.5.sp,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isAlreadyBlocked) Color(0xFFEF4444) else Color(0xFF334155))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (isAlreadyBlocked) "✓ Terblokir" else "+ Blokir Data",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Tombol Tutup Selesai
                    Button(
                        onClick = { showAddAppDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Text("Selesai", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Black)
                    }
                }
            }
        }
    }
}
