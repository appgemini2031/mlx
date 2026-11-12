package com.tajasb.floating.tabs

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tajasb.FloatingControlService

/**
 * WatermarkTabBuilder:
 * Modular builder for Tab 4 Sub-Mode 4: Watermark Kamera (Mode Tanpa Blok, Outline Tanpa Blok + Teks,
 * Hanya Outline, Pakai Blok, Running Teks, Warna, Transparansi, & Pindah Acak).
 */
object WatermarkTabBuilder {

    fun populateWatermarkTab(
        service: FloatingControlService,
        tabContentHolder: LinearLayout,
        tabNotifContainer: LinearLayout,
        dp: (Int) -> Int,
        createStepperBtn: (String, () -> Unit) -> TextView
    ) = with(service) {
        val fontColors = StikerTabBuilder.fontColors
        val boxColors = StikerTabBuilder.boxColors

        // =========================================================
        // D. MODE 4: WATERMARK KAMERA (TERPISAH DARI STIKER!)
        // =========================================================
        val watermarkContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerModeWatermark = watermarkContainer

        val watermarkManager = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)

        // 1. Status & Start/Stop Watermark Card
        val wmStatusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#083344"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val btnToggleWm = TextView(this).apply {
            text = if (watermarkManager.isEnabled.value) "SEMBUNYIKAN" else "TAMPILKAN"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0284C7"))
            }
            setOnClickListener {
                val next = watermarkManager.toggleEnabled()
                Toast.makeText(applicationContext, if (next) "Watermark: AKTIF" else "Watermark: NONAKTIF", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi()
            }
        }
        btnToggleWatermarkText = btnToggleWm
        wmStatusCard.addView(btnToggleWm)

        val btnCopyWm = TextView(this).apply {
            text = "Salin Settingan"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0C4A6E"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                copyToClipboard("Watermark Kamera", watermarkManager.getFormattedSettingsText())
            }
        }
        wmStatusCard.addView(btnCopyWm)
        watermarkContainer.addView(wmStatusCard)

        // 2. Teks Watermark Input Card
        val wmTextCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val wmTextTitle = TextView(this).apply {
            text = "Teks Watermark (1-5 Baris):"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        wmTextCard.addView(wmTextTitle)

        val wmTextInputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        val etWatermarkText = EditText(this).apply {
            setText(watermarkManager.watermarkText.value)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            hint = "Teks watermark toko / akun..."
            setSingleLine(false)
            maxLines = 5
            minLines = 2
            gravity = Gravity.START or Gravity.TOP
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    setPanelFocusable(true)
                    requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
                false
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    setPanelFocusable(true)
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                } else {
                    setPanelFocusable(false)
                }
            }
        }
        wmTextInputRow.addView(etWatermarkText)

        val btnOpenWmKeyboard = TextView(this).apply {
            text = "⌨️"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0284C7"))
            }
            setOnClickListener {
                showWatermarkTextInputDialog(etWatermarkText)
            }
        }
        wmTextInputRow.addView(btnOpenWmKeyboard)

        val btnSaveWmText = TextView(this).apply {
            text = "SIMPAN"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etWatermarkText.windowToken, 0)
                etWatermarkText.clearFocus()
                setPanelFocusable(false)
                watermarkManager.setWatermarkText(etWatermarkText.text.toString())
                Toast.makeText(applicationContext, "Teks watermark disimpan!", Toast.LENGTH_SHORT).show()
            }
        }
        wmTextInputRow.addView(btnSaveWmText)
        wmTextCard.addView(wmTextInputRow)

        val wmTemplateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }
        listOf(
            1 to "1 Baris",
            2 to "2 Baris",
            3 to "3 Baris",
            4 to "4 Baris"
        ).forEach { (linesCount, label) ->
            val btn = TextView(this).apply {
                text = label
                textSize = 8.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(4), dp(3), dp(4), dp(3))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#083344"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                }
                setOnClickListener {
                    watermarkManager.setTemplateLines(linesCount)
                    etWatermarkText.setText(watermarkManager.watermarkText.value)
                    Toast.makeText(applicationContext, "Template watermark $linesCount baris dimuat!", Toast.LENGTH_SHORT).show()
                    updateBuyerNotifUi()
                }
            }
            wmTemplateRow.addView(btn)
        }
        wmTextCard.addView(wmTemplateRow)
        watermarkContainer.addView(wmTextCard)

        // 2.5. Gaya Tampilan Watermark
        val wmStyleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val wmStyleTitle = TextView(this).apply {
            text = "Gaya Tampilan:"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        wmStyleCard.addView(wmStyleTitle)

        val wmStyleScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(4), 0, 0)
        }
        val wmStyleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        watermarkDisplayStyleChipsList.clear()

        listOf(
            com.tajasb.stiker.CustomWatermarkManager.STYLE_TEXT_ONLY to "Tanpa Blok",
            com.tajasb.stiker.CustomWatermarkManager.STYLE_OUTLINE_ONLY to "Outline + Teks",
            com.tajasb.stiker.CustomWatermarkManager.STYLE_HOLLOW_OUTLINE to "Hanya Outline",
            com.tajasb.stiker.CustomWatermarkManager.STYLE_WITH_BLOCK to "Pakai Blok"
        ).forEach { (styleKey, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(8), dp(5), dp(8), dp(5))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    watermarkManager.setDisplayStyle(styleKey)
                    Toast.makeText(applicationContext, "Gaya: $label", Toast.LENGTH_SHORT).show()
                    updateBuyerNotifUi()
                }
            }
            watermarkDisplayStyleChipsList.add(Pair(styleKey, chip))
            wmStyleRow.addView(chip)
        }
        wmStyleScroll.addView(wmStyleRow)
        wmStyleCard.addView(wmStyleScroll)

        val wmPreviewBox = createStyledOverlayPreviewView(isWatermark = true).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply {
                topMargin = dp(6)
            }
        }
        watermarkLivePreviewView = wmPreviewBox
        wmStyleCard.addView(wmPreviewBox)

        watermarkContainer.addView(wmStyleCard)

        // 2.6. Running Teks Watermark
        val wmRunningCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val wmRunTitle = TextView(this).apply {
            text = "Running Teks:"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
        }
        wmRunningCard.addView(wmRunTitle)

        val btnToggleWmRun = TextView(this).apply {
            val isRun = watermarkManager.isRunningText.value
            text = if (isRun) "HENTIKAN" else "JALANKAN"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isRun) Color.parseColor("#059669") else Color.parseColor("#334155"))
            }
            setOnClickListener {
                val next = watermarkManager.toggleRunningText()
                Toast.makeText(applicationContext, if (next) "Running Teks: AKTIF" else "Running Teks: MATI", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi()
            }
        }
        btnToggleWatermarkRunningText = btnToggleWmRun
        wmRunningCard.addView(btnToggleWmRun)

        val wmRunSpeedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }
        watermarkRunningSpeedChipsList.clear()
        listOf(0.5f to "0.5x", 1.0f to "1.0x", 1.5f to "1.5x", 2.0f to "2.0x", 3.0f to "3.0x").forEach { (spd, lbl) ->
            val chip = TextView(this).apply {
                text = lbl
                textSize = 8.5f
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                setOnClickListener {
                    watermarkManager.setRunningTextSpeed(spd)
                    updateBuyerNotifUi()
                }
            }
            watermarkRunningSpeedChipsList.add(Pair(spd, chip))
            wmRunSpeedRow.addView(chip)
        }
        wmRunningCard.addView(wmRunSpeedRow)

        val wmRunDirRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }
        watermarkRunningDirChipsList.clear()
        listOf("LEFT" to "Kiri", "RIGHT" to "Kanan").forEach { (dirKey, lbl) ->
            val chip = TextView(this).apply {
                text = lbl
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(4), dp(6), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    watermarkManager.setRunningTextDirection(dirKey)
                    updateBuyerNotifUi()
                }
            }
            watermarkRunningDirChipsList.add(Pair(dirKey, chip))
            wmRunDirRow.addView(chip)
        }
        wmRunningCard.addView(wmRunDirRow)
        watermarkContainer.addView(wmRunningCard)

        // 3. Warna Teks Font Watermark
        val wmFontColorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }
        val wmFontTitle = TextView(this).apply {
            text = "Warna Teks:"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        wmFontColorCard.addView(wmFontTitle)

        val wmFontScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val wmFontChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        watermarkTextColorChipsList.clear()
        fontColors.forEach { (hex, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(6), dp(4), dp(6), dp(4))
                setTextColor(if (hex == "#000000") Color.WHITE else Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    watermarkManager.setTextColor(hex)
                    updateBuyerNotifUi()
                }
            }
            watermarkTextColorChipsList.add(Pair(hex, chip))
            wmFontChipsRow.addView(chip)
        }
        wmFontScroll.addView(wmFontChipsRow)
        wmFontColorCard.addView(wmFontScroll)

        val btnCustomWmFontCol = TextView(this).apply {
            text = "Custom Hex"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                showHexColorInputDialog("Warna Teks", watermarkManager.textColorHex.value) { hex ->
                    watermarkManager.setTextColor(hex)
                    updateBuyerNotifUi()
                }
            }
        }
        wmFontColorCard.addView(btnCustomWmFontCol)
        watermarkContainer.addView(wmFontColorCard)

        // 4. Outline Watermark (Teks Outline / Bingkai Blok)
        val wmOutlineCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }
        val wmOutlineTitle = TextView(this).apply {
            text = "Outline:"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        wmOutlineCard.addView(wmOutlineTitle)

        val wmOutlineInfo = TextView(this).apply {
            text = "Tebal: ${watermarkManager.outlineWidth.value}dp | Warna: ${watermarkManager.outlineColorHex.value}"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 8.5f
            setPadding(0, dp(1), 0, dp(2))
        }
        watermarkOutlineInfoText = wmOutlineInfo
        wmOutlineCard.addView(wmOutlineInfo)

        val wmOutlineWRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        wmOutlineWRow.addView(createPureIconBtn("➖") {
            watermarkManager.adjustOutlineWidth(-1)
            updateBuyerNotifUi()
        })
        wmOutlineWRow.addView(createPureIconBtn("➕") {
            watermarkManager.adjustOutlineWidth(1)
            updateBuyerNotifUi()
        })
        watermarkOutlineWidthChipsList.clear()
        listOf(0 to "0dp", 1 to "1dp", 2 to "2dp", 3 to "3dp", 5 to "5dp").forEach { (wVal, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(2), dp(5), dp(2))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    watermarkManager.setOutlineWidth(wVal)
                    updateBuyerNotifUi()
                }
            }
            watermarkOutlineWidthChipsList.add(Pair(wVal, chip))
            wmOutlineWRow.addView(chip)
        }
        wmOutlineCard.addView(wmOutlineWRow)

        val wmOutlineColScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val wmOutlineColRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        watermarkOutlineColorChipsList.clear()
        listOf(
            "#00E5FF" to "Cyan",
            "#FFFFFF" to "Putih",
            "#000000" to "Hitam",
            "#FDE047" to "Kuning",
            "#EF4444" to "Merah",
            "#EA580C" to "Oranye"
        ).forEach { (hex, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8.5f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(3), dp(6), dp(3))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    watermarkManager.setOutlineColor(hex)
                    updateBuyerNotifUi()
                }
            }
            watermarkOutlineColorChipsList.add(Pair(hex, chip))
            wmOutlineColRow.addView(chip)
        }
        wmOutlineColScroll.addView(wmOutlineColRow)
        wmOutlineCard.addView(wmOutlineColScroll)

        val btnCustomWmOutlineCol = TextView(this).apply {
            text = "Custom Hex"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#083344"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                showHexColorInputDialog("Warna Outline", watermarkManager.outlineColorHex.value) { hex ->
                    watermarkManager.setOutlineColor(hex)
                    updateBuyerNotifUi()
                }
            }
        }
        wmOutlineCard.addView(btnCustomWmOutlineCol)
        watermarkContainer.addView(wmOutlineCard)

        // 5. Warna Blok Watermark
        val wmBoxColorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }
        val wmBoxTitle = TextView(this).apply {
            text = "Warna Blok (Mode Blok):"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
        }
        wmBoxColorCard.addView(wmBoxTitle)

        val wmBoxScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val wmBoxRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        watermarkBoxColorChipsList.clear()
        boxColors.forEach { (hex, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(6), dp(4), dp(6), dp(4))
                setTextColor(if (hex == "#FFFFFF") Color.BLACK else Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    watermarkManager.setBoxColor(hex)
                    updateBuyerNotifUi()
                }
            }
            watermarkBoxColorChipsList.add(Pair(hex, chip))
            wmBoxRow.addView(chip)
        }
        wmBoxScroll.addView(wmBoxRow)
        wmBoxColorCard.addView(wmBoxScroll)

        val btnCustomWmBoxCol = TextView(this).apply {
            text = "Custom Hex"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                showHexColorInputDialog("Warna Blok", watermarkManager.boxColorHex.value) { hex ->
                    watermarkManager.setBoxColor(hex)
                    updateBuyerNotifUi()
                }
            }
        }
        wmBoxColorCard.addView(btnCustomWmBoxCol)
        watermarkContainer.addView(wmBoxColorCard)

        // 6. Ukuran & Transparansi Watermark
        val wmScaleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }
        val wmScaleTitle = TextView(this).apply {
            text = "Posisi & Ukuran:"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        wmScaleCard.addView(wmScaleTitle)

        var isWmFine = false

        val wmPosInfo = TextView(this).apply {
            val px = (watermarkManager.posXpct.value * 100).toInt()
            val py = (watermarkManager.posYpct.value * 100).toInt()
            val sc = "%.2f".format(watermarkManager.sizeScale.value)
            text = "X=$px% • Y=$py% | Skala: ${sc}x"
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(1), 0, dp(3))
        }
        wmScaleCard.addView(wmPosInfo)

        fun updateWmInfoLabel() {
            val px = (watermarkManager.posXpct.value * 100).toInt()
            val py = (watermarkManager.posYpct.value * 100).toInt()
            val sc = "%.2f".format(watermarkManager.sizeScale.value)
            wmPosInfo.text = "X=$px% • Y=$py% | Skala: ${sc}x"
        }

        // Kecepatan: Cepat vs Halus
        val wmModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(3))
        }
        val btnWmFast = TextView(this).apply {
            text = "Cepat"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(2) }
        }
        val btnWmFine = TextView(this).apply {
            text = "Halus"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(2) }
        }
        fun refreshWmMode() {
            btnWmFast.setTextColor(if (!isWmFine) Color.BLACK else Color.WHITE)
            btnWmFast.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (!isWmFine) Color.parseColor("#38BDF8") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (!isWmFine) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
            }
            btnWmFine.setTextColor(if (isWmFine) Color.BLACK else Color.WHITE)
            btnWmFine.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isWmFine) Color.parseColor("#00E5FF") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (isWmFine) Color.parseColor("#00E5FF") else Color.parseColor("#334155"))
            }
        }
        btnWmFast.setOnClickListener {
            isWmFine = false
            refreshWmMode()
        }
        btnWmFine.setOnClickListener {
            isWmFine = true
            refreshWmMode()
        }
        refreshWmMode()
        wmModeRow.addView(btnWmFast)
        wmModeRow.addView(btnWmFine)
        wmScaleCard.addView(wmModeRow)

        // D-Pad 4 Arah
        val wmDpadRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(3))
        }
        wmDpadRow.addView(createPureIconBtn("⬅️") {
            val delta = if (isWmFine) 0.01f else 0.05f
            watermarkManager.movePosition(-delta, 0f)
            updateWmInfoLabel()
            updateBuyerNotifUi()
        })
        wmDpadRow.addView(createPureIconBtn("⬆️") {
            val delta = if (isWmFine) 0.01f else 0.05f
            watermarkManager.movePosition(0f, -delta)
            updateWmInfoLabel()
            updateBuyerNotifUi()
        })
        wmDpadRow.addView(createPureIconBtn("⬇️") {
            val delta = if (isWmFine) 0.01f else 0.05f
            watermarkManager.movePosition(0f, delta)
            updateWmInfoLabel()
            updateBuyerNotifUi()
        })
        wmDpadRow.addView(createPureIconBtn("➡️") {
            val delta = if (isWmFine) 0.01f else 0.05f
            watermarkManager.movePosition(delta, 0f)
            updateWmInfoLabel()
            updateBuyerNotifUi()
        })
        wmScaleCard.addView(wmDpadRow)

        // Stepper Skala (➖ / ➕)
        val wmScaleStepperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvWmSkalaLabel = TextView(this).apply {
            text = "Skala"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        wmScaleStepperRow.addView(tvWmSkalaLabel)
        wmScaleStepperRow.addView(createPureIconBtn("➖") {
            val delta = if (isWmFine) 0.02f else 0.10f
            watermarkManager.changeScale(-delta)
            updateWmInfoLabel()
            updateBuyerNotifUi()
        })
        wmScaleStepperRow.addView(createPureIconBtn("➕") {
            val delta = if (isWmFine) 0.02f else 0.10f
            watermarkManager.changeScale(delta)
            updateWmInfoLabel()
            updateBuyerNotifUi()
        })
        wmScaleCard.addView(wmScaleStepperRow)

        val wmScaleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, 0)
        }
        watermarkScaleChipsList.clear()
        listOf(0.7f, 0.9f, 1.0f, 1.2f, 1.5f).forEach { s ->
            val chip = TextView(this).apply {
                text = "${s}x"
                textSize = 9.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(3), dp(4), dp(3))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                setOnClickListener {
                    watermarkManager.setSizeScale(s)
                    updateWmInfoLabel()
                    updateBuyerNotifUi()
                }
            }
            watermarkScaleChipsList.add(Pair(s, chip))
            wmScaleRow.addView(chip)
        }
        wmScaleCard.addView(wmScaleRow)

        val wmAlphaTitle = TextView(this).apply {
            text = "Transparansi: ${(watermarkManager.boxAlpha.value * 100).toInt()}%"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 9f
            setPadding(0, dp(4), 0, dp(2))
        }
        watermarkBoxAlphaText = wmAlphaTitle
        wmScaleCard.addView(wmAlphaTitle)

        val wmAlphaStepperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), dp(2), dp(2))
        }
        wmAlphaStepperRow.addView(createPureTextBtn("-10%") {
            watermarkManager.adjustBoxAlpha(-0.10f)
            updateBuyerNotifUi()
        })
        wmAlphaStepperRow.addView(createPureTextBtn("-5%") {
            watermarkManager.adjustBoxAlpha(-0.05f)
            updateBuyerNotifUi()
        })
        wmAlphaStepperRow.addView(createPureTextBtn("+5%") {
            watermarkManager.adjustBoxAlpha(0.05f)
            updateBuyerNotifUi()
        })
        wmAlphaStepperRow.addView(createPureTextBtn("+10%") {
            watermarkManager.adjustBoxAlpha(0.10f)
            updateBuyerNotifUi()
        })
        wmScaleCard.addView(wmAlphaStepperRow)

        val wmAlphaRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        watermarkAlphaChipsList.clear()
        listOf(0.35f to "35%", 0.50f to "50%", 0.65f to "65%", 0.85f to "85%", 1.00f to "100%").forEach { (alphaVal, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8.5f
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(3), dp(4), dp(3))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                setOnClickListener {
                    watermarkManager.setBoxAlpha(alphaVal)
                    updateBuyerNotifUi()
                }
            }
            watermarkAlphaChipsList.add(Pair(alphaVal, chip))
            wmAlphaRow.addView(chip)
        }
        wmScaleCard.addView(wmAlphaRow)
        watermarkContainer.addView(wmScaleCard)

        // 7. Pindah Acak Watermark
        val wmRandomCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#083344"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }
        val btnToggleWmRandom = TextView(this).apply {
            val isRand = watermarkManager.isRandomMoveEnabled.value
            text = if (isRand) "NONAKTIFKAN" else "AKTIFKAN"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isRand) Color.parseColor("#0284C7") else Color.parseColor("#334155"))
            }
            setOnClickListener {
                val next = watermarkManager.toggleRandomMove()
                Toast.makeText(applicationContext, if (next) "Pindah Acak: AKTIF" else "Pindah Acak: NONAKTIF", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi()
            }
        }
        btnToggleWatermarkRandomMove = btnToggleWmRandom
        wmRandomCard.addView(btnToggleWmRandom)

        val btnWmJumpRandom = TextView(this).apply {
            text = "Acak Posisi Sekarang"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FDE047"))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0C4A6E"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                watermarkManager.pickRandomMiddlePosition()
                val x = (watermarkManager.posXpct.value * 100).toInt()
                val y = (watermarkManager.posYpct.value * 100).toInt()
                Toast.makeText(applicationContext, "Posisi: X=$x% Y=$y%", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi()
            }
        }
        wmRandomCard.addView(btnWmJumpRandom)
        watermarkContainer.addView(wmRandomCard)

        tabNotifContainer.addView(watermarkContainer)
        tabContentHolder.addView(tabNotifContainer)
    }
}
