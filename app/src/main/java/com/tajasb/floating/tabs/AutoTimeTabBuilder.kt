package com.tajasb.floating.tabs

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tajasb.FloatingControlService
import com.tajasb.floating.components.FloatingUiWidgets

/**
 * AutoTimeTabBuilder:
 * Modular builder for Tab 4 Sub-Mode 5: Kapsul Waktu Otomatis (Tanggal & Bulan Singkat di kiri,
 * Jam:Menit[:Detik] di kanan) dengan kustomisasi penuh warna kapsul, kotak tanggal, teks,
 * ukuran, transparansi, outline, dan posisi.
 */
object AutoTimeTabBuilder {

    fun populateAutoTimeTab(
        service: FloatingControlService,
        tabNotifContainer: LinearLayout,
        dp: (Int) -> Int,
        createStepperBtn: (String, () -> Unit) -> TextView,
        createPresetChip: (String, () -> Unit) -> TextView
    ) = with(service) {
        val autoTimeMgr = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(this)

        // =========================================================
        // E. MODE 5: WAKTU OTOMATIS (AUTO TIME CAPSULE) CONTAINER
        // =========================================================
        val autoTimeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerModeAutoTime = autoTimeContainer

        // 1. Card Utama: Saklar ON/OFF, Toggle Detik, & Live Preview Kapsul
        val mainCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0D1F18"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val headerTitle = TextView(this).apply {
            text = "Kapsul Waktu Otomatis"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#34D399"))
        }
        mainCard.addView(headerTitle)

        // Live Preview View
        val previewBox = FloatingUiWidgets.createAutoTimePreviewView(this).apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }
        autoTimeLivePreviewView = previewBox
        mainCard.addView(previewBox)

        // Tombol Saklar ON/OFF
        val btnToggleMain = TextView(this).apply {
            val isOn = autoTimeMgr.isEnabled.value
            text = if (isOn) "HENTIKAN" else "AKTIFKAN"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(7), dp(6), dp(7))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isOn) Color.parseColor("#DC2626") else Color.parseColor("#059669"))
            }
            setOnClickListener {
                val next = autoTimeMgr.toggleEnabled()
                Toast.makeText(
                    applicationContext,
                    if (next) "Waktu: AKTIF" else "Waktu: NONAKTIF",
                    Toast.LENGTH_SHORT
                ).show()
                updateBuyerNotifUi()
            }
        }
        btnToggleAutoTimeText = btnToggleMain
        mainCard.addView(btnToggleMain)

        // Tombol Saklar Detik (Optional)
        val btnToggleSec = TextView(this).apply {
            val secOn = autoTimeMgr.showSeconds.value
            text = if (secOn) "Detik: ON" else "Detik: OFF"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (secOn) Color.parseColor("#0284C7") else Color.parseColor("#334155"))
            }
            setOnClickListener {
                val next = autoTimeMgr.toggleShowSeconds()
                Toast.makeText(
                    applicationContext,
                    if (next) "Detik: ON" else "Detik: OFF",
                    Toast.LENGTH_SHORT
                ).show()
                updateBuyerNotifUi()
            }
        }
        btnToggleAutoTimeSecondsText = btnToggleSec
        mainCard.addView(btnToggleSec)

        // Tombol Salin Settingan Waktu Otomatis
        val btnCopyAutoTime = TextView(this).apply {
            text = "Salin Settingan"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
            setOnClickListener {
                copyToClipboard("Waktu Otomatis", autoTimeMgr.getFormattedSettingsText())
            }
        }
        mainCard.addView(btnCopyAutoTime)
        autoTimeContainer.addView(mainCard)

        // =========================================================
        // 2. Card Warna & Bentuk Kapsul Utama
        // =========================================================
        val capsuleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val capTitle = TextView(this).apply {
            text = "Warna & Bentuk Kapsul"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#34D399"))
        }
        capsuleCard.addView(capTitle)

        // Warna Latar Kapsul
        val capColRowHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }
        val capColLabel = TextView(this).apply {
            text = "Warna Latar:"
            textSize = 8.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        capColRowHeader.addView(capColLabel)

        val btnCustomCapHex = TextView(this).apply {
            text = "Custom Hex"
            textSize = 8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#083344"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                showHexColorInputDialog(
                    title = "Warna Latar Kapsul",
                    initialHex = autoTimeMgr.capsuleBgColorHex.value
                ) { hex ->
                    autoTimeMgr.setCapsuleBgColor(hex)
                    updateBuyerNotifUi()
                }
            }
        }
        capColRowHeader.addView(btnCustomCapHex)
        capsuleCard.addView(capColRowHeader)

        val capColorsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        autoTimeCapsuleColorChipsList.clear()
        listOf(
            "#EA580C" to "Oranye",
            "#DC2626" to "Merah",
            "#059669" to "Hijau",
            "#2563EB" to "Biru",
            "#7C3AED" to "Ungu",
            "#DB2777" to "Pink",
            "#111827" to "Hitam"
        ).forEach { (hex, _) ->
            val chip = TextView(this).apply {
                text = " "
                val lp = LinearLayout.LayoutParams(0, dp(22), 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    autoTimeMgr.setCapsuleBgColor(hex)
                    updateBuyerNotifUi()
                }
            }
            autoTimeCapsuleColorChipsList.add(hex to chip)
            capColorsRow.addView(chip)
        }
        capsuleCard.addView(capColorsRow)

        // Transparansi Kapsul
        val alphaLabel = TextView(this).apply {
            text = "Transparansi: ${(autoTimeMgr.capsuleAlpha.value * 100).toInt()}%"
            textSize = 8.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(6), 0, dp(2))
        }
        autoTimeAlphaInfoText = alphaLabel
        capsuleCard.addView(alphaLabel)

        val alphaChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        autoTimeAlphaChipsList.clear()
        listOf(0.40f, 0.60f, 0.75f, 0.90f, 1.0f).forEach { aVal ->
            val chip = TextView(this).apply {
                text = "${(aVal * 100).toInt()}%"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(3), dp(2), dp(3))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    autoTimeMgr.setCapsuleAlpha(aVal)
                    updateBuyerNotifUi()
                }
            }
            autoTimeAlphaChipsList.add(aVal to chip)
            alphaChipsRow.addView(chip)
        }
        capsuleCard.addView(alphaChipsRow)

        // Sudut Kapsul & Padding Samping
        val capShapeInfo = TextView(this).apply {
            text = "Sudut: ${autoTimeMgr.capsuleCornerRadius.value}dp | Lebar: ${autoTimeMgr.capsulePaddingH.value}dp"
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(6), 0, dp(2))
        }
        autoTimeCapsuleInfoText = capShapeInfo
        capsuleCard.addView(capShapeInfo)

        val shapeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val radLbl = TextView(this).apply {
            text = "Sudut:"
            textSize = 8.2f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        shapeRow.addView(radLbl)
        shapeRow.addView(createPresetChip("6dp") {
            autoTimeMgr.setCapsuleCornerRadius(6)
            updateBuyerNotifUi()
        })
        shapeRow.addView(createPresetChip("16dp") {
            autoTimeMgr.setCapsuleCornerRadius(16)
            updateBuyerNotifUi()
        })
        shapeRow.addView(createPresetChip("Kapsul") {
            autoTimeMgr.setCapsuleCornerRadius(40)
            updateBuyerNotifUi()
        })
        capsuleCard.addView(shapeRow)

        val padRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
        }
        val padLbl = TextView(this).apply {
            text = "Spasi:"
            textSize = 8.2f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        padRow.addView(padLbl)
        padRow.addView(createPureTextBtn("-2dp") {
            autoTimeMgr.adjustCapsulePaddingH(-2)
            updateBuyerNotifUi()
        })
        padRow.addView(createPureTextBtn("+2dp") {
            autoTimeMgr.adjustCapsulePaddingH(2)
            updateBuyerNotifUi()
        })
        padRow.addView(createPresetChip("10dp") {
            autoTimeMgr.setCapsulePaddingH(10)
            updateBuyerNotifUi()
        })
        padRow.addView(createPresetChip("18dp") {
            autoTimeMgr.setCapsulePaddingH(18)
            updateBuyerNotifUi()
        })
        capsuleCard.addView(padRow)

        // Garis Tepi (Outline) Kapsul
        val outlineInfo = TextView(this).apply {
            text = "Outline: ${autoTimeMgr.capsuleOutlineWidth.value}dp (${autoTimeMgr.capsuleOutlineColorHex.value})"
            textSize = 8.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(6), 0, dp(2))
        }
        autoTimeOutlineInfoText = outlineInfo
        capsuleCard.addView(outlineInfo)

        val outWidthRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        autoTimeOutlineWidthChipsList.clear()
        listOf(0 to "0dp", 1 to "1dp", 2 to "2dp", 3 to "3dp").forEach { (wVal, label) ->
            val chip = TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(3), dp(2), dp(3))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    autoTimeMgr.setCapsuleOutlineWidth(wVal)
                    updateBuyerNotifUi()
                }
            }
            autoTimeOutlineWidthChipsList.add(wVal to chip)
            outWidthRow.addView(chip)
        }
        capsuleCard.addView(outWidthRow)

        val outColorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
        }
        autoTimeOutlineColorChipsList.clear()
        listOf("#FFFFFF", "#FDE047", "#00E5FF", "#10B981", "#F97316", "#000000").forEach { hex ->
            val chip = TextView(this).apply {
                text = " "
                val lp = LinearLayout.LayoutParams(0, dp(18), 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    autoTimeMgr.setCapsuleOutlineColor(hex)
                    if (autoTimeMgr.capsuleOutlineWidth.value == 0) {
                        autoTimeMgr.setCapsuleOutlineWidth(1)
                    }
                    updateBuyerNotifUi()
                }
            }
            autoTimeOutlineColorChipsList.add(hex to chip)
            outColorRow.addView(chip)
        }
        capsuleCard.addView(outColorRow)
        autoTimeContainer.addView(capsuleCard)

        // =========================================================
        // 3. Card Warna Kotak Tanggal & Bulan (Sebelah Kiri)
        // =========================================================
        val dateBoxCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val dateBoxTitle = TextView(this).apply {
            text = "Kotak Tanggal & Bulan (Kiri)"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FBBF24"))
        }
        dateBoxCard.addView(dateBoxTitle)

        // Warna Latar Kotak Tanggal
        val dateBgHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }
        val dateBgLbl = TextView(this).apply {
            text = "Warna Kotak:"
            textSize = 8.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        dateBgHeader.addView(dateBgLbl)

        val btnCustomDateBgHex = TextView(this).apply {
            text = "Custom Hex"
            textSize = 8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FBBF24"))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#451A03"))
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
            setOnClickListener {
                showHexColorInputDialog(
                    title = "Warna Kotak Tanggal",
                    initialHex = autoTimeMgr.dateBoxBgColorHex.value
                ) { hex ->
                    autoTimeMgr.setDateBoxBgColor(hex)
                    updateBuyerNotifUi()
                }
            }
        }
        dateBgHeader.addView(btnCustomDateBgHex)
        dateBoxCard.addView(dateBgHeader)

        val dateBgChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        autoTimeDateBoxBgChipsList.clear()
        listOf("#FFFFFF", "#FEF08A", "#111827", "#EA580C", "#DC2626", "#059669", "#2563EB").forEach { hex ->
            val chip = TextView(this).apply {
                text = " "
                val lp = LinearLayout.LayoutParams(0, dp(22), 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    autoTimeMgr.setDateBoxBgColor(hex)
                    updateBuyerNotifUi()
                }
            }
            autoTimeDateBoxBgChipsList.add(hex to chip)
            dateBgChipsRow.addView(chip)
        }
        dateBoxCard.addView(dateBgChipsRow)

        // Warna Teks Tanggal & Bulan
        val dateTxtHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(2))
        }
        val dateTxtLbl = TextView(this).apply {
            text = "Warna Teks:"
            textSize = 8.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        dateTxtHeader.addView(dateTxtLbl)

        val btnCustomDateTxtHex = TextView(this).apply {
            text = "Custom Hex"
            textSize = 8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FBBF24"))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#451A03"))
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
            setOnClickListener {
                showHexColorInputDialog(
                    title = "Warna Teks Tanggal & Bulan",
                    initialHex = autoTimeMgr.dateBoxTextColorHex.value
                ) { hex ->
                    autoTimeMgr.setDateBoxTextColor(hex)
                    updateBuyerNotifUi()
                }
            }
        }
        dateTxtHeader.addView(btnCustomDateTxtHex)
        dateBoxCard.addView(dateTxtHeader)

        val dateTxtChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        autoTimeDateTextChipsList.clear()
        listOf("#EA580C", "#DC2626", "#111827", "#FFFFFF", "#059669", "#2563EB", "#FDE047").forEach { hex ->
            val chip = TextView(this).apply {
                text = " "
                val lp = LinearLayout.LayoutParams(0, dp(22), 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    autoTimeMgr.setDateBoxTextColor(hex)
                    updateBuyerNotifUi()
                }
            }
            autoTimeDateTextChipsList.add(hex to chip)
            dateTxtChipsRow.addView(chip)
        }
        dateBoxCard.addView(dateTxtChipsRow)
        autoTimeContainer.addView(dateBoxCard)

        // =========================================================
        // 4. Card Warna Teks Jam & Skala Ukuran Kapsul (Kanan)
        // =========================================================
        val clockCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val clockHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(3))
        }
        val clockTitle = TextView(this).apply {
            text = "Warna Jam & Ukuran"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        clockHeader.addView(clockTitle)

        val btnCustomClockHex = TextView(this).apply {
            text = "Custom Hex"
            textSize = 8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#083344"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                showHexColorInputDialog(
                    title = "Warna Teks Jam",
                    initialHex = autoTimeMgr.timeTextColorHex.value
                ) { hex ->
                    autoTimeMgr.setTimeTextColor(hex)
                    updateBuyerNotifUi()
                }
            }
        }
        clockHeader.addView(btnCustomClockHex)
        clockCard.addView(clockHeader)

        val clockTxtChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        autoTimeClockTextChipsList.clear()
        listOf("#FFFFFF", "#FDE047", "#00E5FF", "#4ADE80", "#111827", "#FB923C", "#F472B6").forEach { hex ->
            val chip = TextView(this).apply {
                text = " "
                val lp = LinearLayout.LayoutParams(0, dp(22), 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    autoTimeMgr.setTimeTextColor(hex)
                    updateBuyerNotifUi()
                }
            }
            autoTimeClockTextChipsList.add(hex to chip)
            clockTxtChipsRow.addView(chip)
        }
        clockCard.addView(clockTxtChipsRow)

        // Skala Ukuran Kapsul
        val scaleHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(2))
        }
        val scaleLbl = TextView(this).apply {
            text = "Ukuran Kapsul:"
            textSize = 8.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        scaleHeaderRow.addView(scaleLbl)

        scaleHeaderRow.addView(createPureIconBtn("➖") {
            autoTimeMgr.changeScale(-0.1f)
            updateBuyerNotifUi()
        })
        scaleHeaderRow.addView(createPureIconBtn("➕") {
            autoTimeMgr.changeScale(0.1f)
            updateBuyerNotifUi()
        })
        clockCard.addView(scaleHeaderRow)

        val scaleChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        autoTimeScaleChipsList.clear()
        listOf(0.75f to "0.75x", 0.9f to "0.9x", 1.0f to "1.0x", 1.2f to "1.2x", 1.5f to "1.5x").forEach { (sVal, label) ->
            val chip = TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(3), dp(2), dp(3))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    autoTimeMgr.setSizeScale(sVal)
                    updateBuyerNotifUi()
                }
            }
            autoTimeScaleChipsList.add(sVal to chip)
            scaleChipsRow.addView(chip)
        }
        clockCard.addView(scaleChipsRow)
        autoTimeContainer.addView(clockCard)

        // =========================================================
        // 5. Card Posisi, Kunci, Rotasi & Mirror
        // =========================================================
        val posCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val posInfo = TextView(this).apply {
            val px = (autoTimeMgr.posXpct.value * 100).toInt()
            val py = (autoTimeMgr.posYpct.value * 100).toInt()
            text = "Posisi: X=$px% • Y=$py%"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        autoTimePosInfoText = posInfo
        posCard.addView(posInfo)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        val btnLock = TextView(this).apply {
            val locked = autoTimeMgr.isPositionLocked.value
            text = if (locked) "Buka Kunci" else "Kunci Posisi"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            setOnClickListener {
                val next = autoTimeMgr.togglePositionLock()
                Toast.makeText(
                    applicationContext,
                    if (next) "Posisi Dikunci" else "Kunci Dibuka",
                    Toast.LENGTH_SHORT
                ).show()
                updateBuyerNotifUi()
            }
        }
        btnAutoTimeLockText = btnLock
        actionRow.addView(btnLock)

        val btnRot = TextView(this).apply {
            text = "${autoTimeMgr.rotation.value}°"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                autoTimeMgr.cycleRotation()
                updateBuyerNotifUi()
            }
        }
        btnAutoTimeRotText = btnRot
        actionRow.addView(btnRot)

        val btnMir = TextView(this).apply {
            text = if (autoTimeMgr.isMirrored.value) "Mir: ON" else "Mir: OFF"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            setOnClickListener {
                val next = autoTimeMgr.toggleMirror()
                Toast.makeText(applicationContext, if (next) "Mirror: ON" else "Mirror: OFF", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi()
            }
        }
        btnAutoTimeMirrorText = btnMir
        actionRow.addView(btnMir)

        val btnReset = TextView(this).apply {
            text = "↺"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCA5A5"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f)
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#450A0A"))
                setStroke(dp(1), Color.parseColor("#EF4444"))
            }
            setOnClickListener {
                autoTimeMgr.resetPosition()
                Toast.makeText(applicationContext, "Posisi direset", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi()
            }
        }
        actionRow.addView(btnReset)
        posCard.addView(actionRow)
        autoTimeContainer.addView(posCard)

        tabNotifContainer.addView(autoTimeContainer)
    }
}
