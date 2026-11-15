package com.tajasb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonOrange
import com.tajasb.interaction.GiftCoinCatalog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GiftNameCatalogPicker(
    giftName: String,
    onGiftNameChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    onQuickPicked: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCatalog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTier by remember { mutableStateOf("1 💎") }

    val focusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val matchedCatalogEntry = remember(giftName) {
        if (giftName.isBlank()) null else GiftCoinCatalog.findEntry(giftName.split(',', '|').firstOrNull().orEmpty())
    }

    val filteredGifts = remember(searchQuery, selectedTier) {
        val tier = if (searchQuery.isNotBlank()) "Semua" else selectedTier
        GiftCoinCatalog.filterEntries(query = searchQuery, tierBucket = tier)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurface)
            .border(
                0.8.dp,
                if (giftName.isNotBlank()) EmeraldGreen.copy(alpha = 0.7f) else DarkCardBorder,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(5.dp))
                    .background(DarkBackground)
                    .border(0.7.dp, DarkCardBorder, RoundedCornerShape(5.dp))
                    .clickable {
                        onFocusChange(true)
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "🎁 Target Nama Gift / Koin (kosong = semua gift)",
                    color = Color(0xFF94A3B8),
                    fontSize = 7.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    if (giftName.isEmpty()) {
                        Text(
                            text = "Mis: Mawar, Kopi, Es Krim...",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp
                        )
                    }
                    BasicTextField(
                        value = giftName,
                        onValueChange = onGiftNameChange,
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(NeonCyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onFocusChange(false)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 17.dp)
                            .focusRequester(focusRequester)
                            .testTag("gift_name_target_input")
                            .onFocusChanged {
                                onFocusChange(it.isFocused)
                                if (it.isFocused) keyboardController?.show()
                            }
                    )
                }
            }

            if (giftName.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF334155))
                        .clickable {
                            onGiftNameChange("")
                            onQuickPicked?.invoke("")
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .testTag("gift_name_clear_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (showCatalog) NeonCyan else Color(0xFF0E7490))
                    .clickable { showCatalog = !showCatalog }
                    .padding(horizontal = 7.dp, vertical = 6.dp)
                    .testTag("toggle_gift_database_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showCatalog) "📚 Tutup" else "📚 Database",
                    color = if (showCatalog) Color.Black else Color.White,
                    fontSize = 8.4.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        val statusInfo = when {
            giftName.isBlank() -> "Semua gift masuk akan memicu challenge ini. Pilih nama agar 1 koin tidak bentrok."
            matchedCatalogEntry != null -> {
                val aliasNote = if (matchedCatalogEntry.aliases.isNotEmpty()) {
                    " • Alias: ${matchedCatalogEntry.aliases.take(2).joinToString("/")}"
                } else ""
                "✅ ${matchedCatalogEntry.emoji} ${matchedCatalogEntry.primaryName} (${matchedCatalogEntry.coinCost} 💎 ${matchedCatalogEntry.platform})$aliasNote"
            }
            else -> "🎯 Filter nama kustom: \"$giftName\""
        }
        Text(
            text = statusInfo,
            color = if (matchedCatalogEntry != null) EmeraldGreen else if (giftName.isBlank()) Color(0xFF94A3B8) else NeonCyan,
            fontSize = 7.6.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (showCatalog) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkBackground.copy(alpha = 0.85f))
                    .border(0.7.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Search Gift/Coin
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(DarkSurface)
                        .border(0.6.dp, DarkCardBorder, RoundedCornerShape(5.dp))
                        .clickable {
                            onFocusChange(true)
                            searchFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "🔍 Cari nama gift / angka koin (mis: Mawar, Kopi, 1, Paus)...",
                                color = Color(0xFF64748B),
                                fontSize = 8.2.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(NeonCyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onFocusChange(false)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 16.dp)
                                .focusRequester(searchFocusRequester)
                                .testTag("gift_database_search_input")
                                .onFocusChanged {
                                    onFocusChange(it.isFocused)
                                    if (it.isFocused) keyboardController?.show()
                                }
                        )
                    }
                }

                // Filter Tier Koin
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    GiftCoinCatalog.coinTierBuckets.forEach { tier ->
                        val active = (if (searchQuery.isNotBlank()) "Semua" else selectedTier) == tier
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (active) NeonCyan.copy(alpha = 0.25f) else DarkSurface)
                                .border(0.7.dp, if (active) NeonCyan else DarkCardBorder, RoundedCornerShape(5.dp))
                                .clickable {
                                    searchQuery = ""
                                    selectedTier = tier
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = tier,
                                color = if (active) NeonCyan else Color(0xFFCBD5E1),
                                fontSize = 7.8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Daftar Gift dari Database
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 135.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredGifts.isEmpty()) {
                        Text(
                            text = "Tidak ditemukan di database. Ketik langsung di kolom nama gift di atas.",
                            color = NeonOrange,
                            fontSize = 8.sp
                        )
                    } else {
                        filteredGifts.forEach { entry ->
                            val isSelected = giftName.isNotBlank() &&
                                GiftCoinCatalog.matchesGiftName(giftName, entry.primaryName)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (isSelected) EmeraldGreen.copy(alpha = 0.22f) else DarkSurface)
                                    .border(
                                        0.7.dp,
                                        if (isSelected) EmeraldGreen else DarkCardBorder,
                                        RoundedCornerShape(5.dp)
                                    )
                                    .clickable {
                                        onGiftNameChange(entry.primaryName)
                                        onQuickPicked?.invoke(entry.primaryName)
                                        showCatalog = false
                                        onFocusChange(false)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.5.dp)
                                    .testTag("gift_db_item_${GiftCoinCatalog.normalizeName(entry.primaryName)}")
                            ) {
                                Text(
                                    text = "${entry.emoji} ${entry.primaryName} (${entry.coinCost}💎)",
                                    color = if (isSelected) EmeraldGreen else Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
