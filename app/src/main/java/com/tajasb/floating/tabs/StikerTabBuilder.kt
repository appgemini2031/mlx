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
 * StikerTabBuilder:
 * Modular builder for Tab 4 Sub-Mode 3: Stiker Tulisan (Mode Tanpa Blok, Outline Tanpa Blok + Teks,
 * Hanya Outline, Pakai Blok, Running Teks, Warna, Transparansi, & Pindah Acak).
 */
object StikerTabBuilder {

    val fontColors: List<Pair<String, String>> = listOf(
        "#FFFFFF" to "⬜ Putih",
        "#FACC15" to "🟡 Kuning",
        "#000000" to "⬛ Hitam",
        "#00E5FF" to "🟦 Cyan",
        "#10B981" to "🟢 Hijau",
        "#F97316" to "🟠 Oranye",
        "#F43F5E" to "🔴 Pink/Merah"
    )

    val boxColors: List<Pair<String, String>> = listOf(
        "#D97724" to "🟠 Oranye TikTok",
        "#EA580C" to "🟠 Oranye Tua",
        "#DC2626" to "🔴 Merah Promo",
        "#0F172A" to "🌑 Hitam/Navy",
        "#7C3AED" to "🟣 Ungu",
        "#059669" to "🟢 Hijau",
        "#2563EB" to "🔵 Biru",
        "#EAB308" to "🟡 Emas",
        "#FFFFFF" to "⬜ Putih"
    )

    fun populateStikerTab(
        service: FloatingControlService,
        tabNotifContainer: LinearLayout,
        dp: (Int) -> Int,
        createStepperBtn: (String, () -> Unit) -> TextView
    ) = with(service) {
        // =========================================================
        // C. MODE 3: STIKER TULISAN (CUSTOM TEXT STICKER) CONTAINER
        // =========================================================
        val stikerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerModeStiker = stikerContainer

        val stikerManager = com.tajasb.stiker.CustomStikerManager.getInstance(this)

        // 1. Status & Start/Stop Stiker Card
        val stikerStatusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#1E1035"))
                setStroke(dp(1), Color.parseColor("#A855F7"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val btnToggleStiker = TextView(this).apply {
            text = if (stikerManager.isEnabled.value) "SEMBUNYIKAN" else "TAMPILKAN"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#9333EA"))
            }
            setOnClickListener {
                val next = stikerManager.toggleEnabled()
                Toast.makeText(applicationContext, if (next) "Stiker Tulisan: AKTIF" else "Stiker Tulisan: NONAKTIF", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi()
            }
        }
        btnToggleStikerText = btnToggleStiker
        stikerStatusCard.addView(btnToggleStiker)

        val btnCopyStiker = TextView(this).apply {
            text = "Salin Settingan"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#581C87"))
                setStroke(dp(1), Color.parseColor("#A855F7"))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                copyToClipboard("Stiker Tulisan", stikerManager.getFormattedSettingsText())
            }
        }
        stikerStatusCard.addView(btnCopyStiker)
        stikerContainer.addView(stikerStatusCard)

        // 2. Teks Stiker Input Card
        val stikerTextCard = LinearLayout(this).apply {
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

        val stikerTextTitle = TextView(this).apply {
            text = "Teks Stiker (1-5 Baris):"
            setTextColor(Color.parseColor("#C084FC"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        stikerTextCard.addView(stikerTextTitle)

        val textInputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        val etStikerText = EditText(this).apply {
            setText(stikerManager.stikerText.value)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            hint = "Baris 1...\nBaris 2 (Tekan Enter)..."
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
        textInputRow.addView(etStikerText)

        val btnOpenKeyboardDialog = TextView(this).apply {
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
                setColor(Color.parseColor("#6D28D9"))
            }
            setOnClickListener {
                showStikerTextInputDialog(etStikerText)
            }
        }
        textInputRow.addView(btnOpenKeyboardDialog)

        val btnSaveStikerText = TextView(this).apply {
            text = "SIMPAN"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#C084FC"))
            }
            setOnClickListener {
                val txt = etStikerText.text.toString()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etStikerText.windowToken, 0)
                etStikerText.clearFocus()
                setPanelFocusable(false)
                stikerManager.setStikerText(txt)
                Toast.makeText(applicationContext, "Teks stiker disimpan!", Toast.LENGTH_SHORT).show()
            }
        }
        textInputRow.addView(btnSaveStikerText)
        stikerTextCard.addView(textInputRow)

        val lineTemplatesRow = LinearLayout(this).apply {
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
                    setColor(Color.parseColor("#311042"))
                    setStroke(dp(1), Color.parseColor("#A855F7"))
                }
                setOnClickListener {
                    stikerManager.setTemplateLines(linesCount)
                    etStikerText.setText(stikerManager.stikerText.value)
                    Toast.makeText(applicationContext, "Template $linesCount baris dimuat!", Toast.LENGTH_SHORT).show()
                    updateBuyerNotifUi()
                }
            }
            lineTemplatesRow.addView(btn)
        }
        stikerTextCard.addView(lineTemplatesRow)
        stikerContainer.addView(stikerTextCard)

        // 2.5. Gaya Tampilan Stiker
        val stikerStyleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#A855F7"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val stikerStyleTitle = TextView(this).apply {
            text = "Gaya Tampilan:"
            setTextColor(Color.parseColor("#C084FC"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        stikerStyleCard.addView(stikerStyleTitle)

        val stikerStyleScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(4), 0, 0)
        }
        val stikerStyleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stikerDisplayStyleChipsList.clear()

        listOf(
            com.tajasb.stiker.CustomStikerManager.STYLE_WITH_BLOCK to "Pakai Blok",
            com.tajasb.stiker.CustomStikerManager.STYLE_TEXT_ONLY to "Tanpa Blok",
            com.tajasb.stiker.CustomStikerManager.STYLE_OUTLINE_ONLY to "Outline + Teks",
            com.tajasb.stiker.CustomStikerManager.STYLE_HOLLOW_OUTLINE to "Hanya Outline"
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
                    stikerManager.setDisplayStyle(styleKey)
                    Toast.makeText(applicationContext, "Gaya: $label", Toast.LENGTH_SHORT).show()
                    updateBuyerNotifUi()
                }
            }
            stikerDisplayStyleChipsList.add(Pair(styleKey, chip))
            stikerStyleRow.addView(chip)
        }
        stikerStyleScroll.addView(stikerStyleRow)
        stikerStyleCard.addView(stikerStyleScroll)

        val stikerPreviewBox = createStyledOverlayPreviewView(isWatermark = false).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply {
                topMargin = dp(6)
            }
        }
        stikerLivePreviewView = stikerPreviewBox
        stikerStyleCard.addView(stikerPreviewBox)

        stikerContainer.addView(stikerStyleCard)

        // 2.6. Running Teks (Teks Berjalan / Marquee) Stiker
        val stikerRunningCard = LinearLayout(this).apply {
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

        val stikerRunTitle = TextView(this).apply {
            text = "Running Teks:"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
        }
        stikerRunningCard.addView(stikerRunTitle)

        val btnToggleStikerRun = TextView(this).apply {
            val isRun = stikerManager.isRunningText.value
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
                setColor(if (isRun) Color.parseColor("#0284C7") else Color.parseColor("#334155"))
            }
            setOnClickListener {
                val next = stikerManager.toggleRunningText()
                Toast.makeText(applicationContext, if (next) "Running Teks: AKTIF" else "Running Teks: MATI", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi()
            }
        }
        btnToggleStikerRunningText = btnToggleStikerRun
        stikerRunningCard.addView(btnToggleStikerRun)

        val stikerRunSpeedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }
        stikerRunningSpeedChipsList.clear()
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
                    stikerManager.setRunningTextSpeed(spd)
                    updateBuyerNotifUi()
                }
            }
            stikerRunningSpeedChipsList.add(Pair(spd, chip))
            stikerRunSpeedRow.addView(chip)
        }
        stikerRunningCard.addView(stikerRunSpeedRow)

        val stikerRunDirRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }
        stikerRunningDirChipsList.clear()
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
                    stikerManager.setRunningTextDirection(dirKey)
                    updateBuyerNotifUi()
                }
            }
            stikerRunningDirChipsList.add(Pair(dirKey, chip))
            stikerRunDirRow.addView(chip)
        }
        stikerRunningCard.addView(stikerRunDirRow)
        stikerContainer.addView(stikerRunningCard)

        // 3. Warna Teks Font Card
        val fontColorCard = LinearLayout(this).apply {
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

        val fontColorTitle = TextView(this).apply {
            text = "Warna Teks:"
            setTextColor(Color.parseColor("#C084FC"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        fontColorCard.addView(fontColorTitle)

        val fontScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(4), 0, 0)
        }
        val fontChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        stikerTextColorChipsList.clear()
        fontColors.forEach { (hex, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(6), dp(4), dp(6), dp(4))
                val parsed = Color.parseColor(hex)
                setTextColor(if (hex == "#000000") Color.WHITE else Color.BLACK)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(parsed)
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    stikerManager.setTextColor(hex)
                    updateBuyerNotifUi()
                }
            }
            stikerTextColorChipsList.add(Pair(hex, chip))
            fontChipsRow.addView(chip)
        }
        fontScroll.addView(fontChipsRow)
        fontColorCard.addView(fontScroll)

        val btnCustomFontCol = TextView(this).apply {
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
                setColor(Color.parseColor("#334155"))
                setStroke(dp(1), Color.parseColor("#C084FC"))
            }
            setOnClickListener {
                showHexColorInputDialog("Warna Teks", stikerManager.textColorHex.value) { hex ->
                    stikerManager.setTextColor(hex)
                    updateBuyerNotifUi()
                }
            }
        }
        fontColorCard.addView(btnCustomFontCol)
        stikerContainer.addView(fontColorCard)

        // 4. Warna Kotak Background (Box) Card
        val boxColorCard = LinearLayout(this).apply {
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

        val boxColorTitle = TextView(this).apply {
            text = "Warna Kotak:"
            setTextColor(Color.parseColor("#C084FC"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        boxColorCard.addView(boxColorTitle)

        val boxScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(4), 0, 0)
        }
        val boxChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        stikerBoxColorChipsList.clear()
        boxColors.forEach { (hex, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(6), dp(4), dp(6), dp(4))
                val parsed = Color.parseColor(hex)
                setTextColor(if (hex == "#FFFFFF" || hex == "#EAB308") Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(parsed)
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    stikerManager.setBoxColor(hex)
                    updateBuyerNotifUi()
                }
            }
            stikerBoxColorChipsList.add(Pair(hex, chip))
            boxChipsRow.addView(chip)
        }
        boxScroll.addView(boxChipsRow)
        boxColorCard.addView(boxScroll)

        val btnCustomBoxCol = TextView(this).apply {
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
                setColor(Color.parseColor("#334155"))
                setStroke(dp(1), Color.parseColor("#C084FC"))
            }
            setOnClickListener {
                showHexColorInputDialog("Warna Kotak", stikerManager.boxColorHex.value) { hex ->
                    stikerManager.setBoxColor(hex)
                    updateBuyerNotifUi()
                }
            }
        }
        boxColorCard.addView(btnCustomBoxCol)
        stikerContainer.addView(boxColorCard)

        // 4.5. Outline / Stroke Kotak Stiker Card
        val stikerOutlineCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#A855F7"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val stikerOutlineTitle = TextView(this).apply {
            val oW = stikerManager.outlineWidth.value
            val oCol = stikerManager.outlineColorHex.value
            text = "Outline: ${oW}dp ($oCol)"
            setTextColor(Color.parseColor("#C084FC"))
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
        }
        stikerOutlineInfoText = stikerOutlineTitle
        stikerOutlineCard.addView(stikerOutlineTitle)

        val stikerOutlineWRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
        }
        stikerOutlineWRow.addView(createPureIconBtn("➖") {
            stikerManager.adjustOutlineWidth(-1)
            updateBuyerNotifUi()
        })
        stikerOutlineWRow.addView(createPureIconBtn("➕") {
            stikerManager.adjustOutlineWidth(1)
            updateBuyerNotifUi()
        })

        stikerOutlineWidthChipsList.clear()
        listOf(
            0 to "0dp (Off)",
            1 to "1dp",
            2 to "2dp",
            3 to "3dp",
            5 to "5dp"
        ).forEach { (wVal, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(2), dp(5), dp(2))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    stikerManager.setOutlineWidth(wVal)
                    updateBuyerNotifUi()
                }
            }
            stikerOutlineWidthChipsList.add(Pair(wVal, chip))
            stikerOutlineWRow.addView(chip)
        }
        stikerOutlineCard.addView(stikerOutlineWRow)

        val stikerOutlineColScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val stikerOutlineColRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stikerOutlineColorChipsList.clear()
        listOf(
            "#FFFFFF" to "Putih",
            "#000000" to "Hitam",
            "#FDE047" to "Kuning",
            "#00E5FF" to "Cyan",
            "#EF4444" to "Merah",
            "#A855F7" to "Ungu"
        ).forEach { (hex, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8.5f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(3), dp(6), dp(3))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    stikerManager.setOutlineColor(hex)
                    updateBuyerNotifUi()
                }
            }
            stikerOutlineColorChipsList.add(Pair(hex, chip))
            stikerOutlineColRow.addView(chip)
        }
        stikerOutlineColScroll.addView(stikerOutlineColRow)
        stikerOutlineCard.addView(stikerOutlineColScroll)

        val btnCustomStikerOutlineCol = TextView(this).apply {
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
                setColor(Color.parseColor("#4C1D95"))
                setStroke(dp(1), Color.parseColor("#C084FC"))
            }
            setOnClickListener {
                showHexColorInputDialog("Warna Outline", stikerManager.outlineColorHex.value) { hex ->
                    stikerManager.setOutlineColor(hex)
                    updateBuyerNotifUi()
                }
            }
        }
        stikerOutlineCard.addView(btnCustomStikerOutlineCol)
        stikerContainer.addView(stikerOutlineCard)

        // 5. Ukuran Stiker (Scale) Card
        val scaleCard = LinearLayout(this).apply {
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

        val scaleTitle = TextView(this).apply {
            text = "Posisi & Ukuran:"
            setTextColor(Color.parseColor("#C084FC"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        scaleCard.addView(scaleTitle)

        var isStikerFine = false

        val stikerPosInfo = TextView(this).apply {
            val px = (stikerManager.posXpct.value * 100).toInt()
            val py = (stikerManager.posYpct.value * 100).toInt()
            val sc = "%.2f".format(stikerManager.sizeScale.value)
            text = "X=$px% • Y=$py% | Skala: ${sc}x"
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(1), 0, dp(3))
        }
        scaleCard.addView(stikerPosInfo)

        fun updateStikerInfoLabel() {
            val px = (stikerManager.posXpct.value * 100).toInt()
            val py = (stikerManager.posYpct.value * 100).toInt()
            val sc = "%.2f".format(stikerManager.sizeScale.value)
            stikerPosInfo.text = "X=$px% • Y=$py% | Skala: ${sc}x"
        }

        // Kecepatan: Cepat vs Halus
        val stikerModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(3))
        }
        val btnStikerFast = TextView(this).apply {
            text = "Cepat"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(2) }
        }
        val btnStikerFine = TextView(this).apply {
            text = "Halus"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(2) }
        }
        fun refreshStikerMode() {
            btnStikerFast.setTextColor(if (!isStikerFine) Color.BLACK else Color.WHITE)
            btnStikerFast.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (!isStikerFine) Color.parseColor("#C084FC") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (!isStikerFine) Color.parseColor("#C084FC") else Color.parseColor("#334155"))
            }
            btnStikerFine.setTextColor(if (isStikerFine) Color.BLACK else Color.WHITE)
            btnStikerFine.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isStikerFine) Color.parseColor("#E879F9") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (isStikerFine) Color.parseColor("#E879F9") else Color.parseColor("#334155"))
            }
        }
        btnStikerFast.setOnClickListener {
            isStikerFine = false
            refreshStikerMode()
        }
        btnStikerFine.setOnClickListener {
            isStikerFine = true
            refreshStikerMode()
        }
        refreshStikerMode()
        stikerModeRow.addView(btnStikerFast)
        stikerModeRow.addView(btnStikerFine)
        scaleCard.addView(stikerModeRow)

        // D-Pad 4 Arah
        val stikerDpadRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(3))
        }
        stikerDpadRow.addView(createPureIconBtn("⬅️") {
            val delta = if (isStikerFine) 0.01f else 0.05f
            stikerManager.movePosition(-delta, 0f)
            updateStikerInfoLabel()
            updateBuyerNotifUi()
        })
        stikerDpadRow.addView(createPureIconBtn("⬆️") {
            val delta = if (isStikerFine) 0.01f else 0.05f
            stikerManager.movePosition(0f, -delta)
            updateStikerInfoLabel()
            updateBuyerNotifUi()
        })
        stikerDpadRow.addView(createPureIconBtn("⬇️") {
            val delta = if (isStikerFine) 0.01f else 0.05f
            stikerManager.movePosition(0f, delta)
            updateStikerInfoLabel()
            updateBuyerNotifUi()
        })
        stikerDpadRow.addView(createPureIconBtn("➡️") {
            val delta = if (isStikerFine) 0.01f else 0.05f
            stikerManager.movePosition(delta, 0f)
            updateStikerInfoLabel()
            updateBuyerNotifUi()
        })
        scaleCard.addView(stikerDpadRow)

        // Stepper Skala (➖ / ➕)
        val stikerScaleStepperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvStikerSkalaLabel = TextView(this).apply {
            text = "Skala"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        stikerScaleStepperRow.addView(tvStikerSkalaLabel)
        stikerScaleStepperRow.addView(createPureIconBtn("➖") {
            val delta = if (isStikerFine) 0.02f else 0.10f
            stikerManager.changeScale(-delta)
            updateStikerInfoLabel()
            updateBuyerNotifUi()
        })
        stikerScaleStepperRow.addView(createPureIconBtn("➕") {
            val delta = if (isStikerFine) 0.02f else 0.10f
            stikerManager.changeScale(delta)
            updateStikerInfoLabel()
            updateBuyerNotifUi()
        })
        scaleCard.addView(stikerScaleStepperRow)

        val stikerScaleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, 0)
        }

        stikerScaleChipsList.clear()
        listOf(0.8f, 1.0f, 1.2f, 1.4f, 1.7f).forEach { s ->
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
                    stikerManager.setSizeScale(s)
                    updateStikerInfoLabel()
                    updateBuyerNotifUi()
                }
            }
            stikerScaleChipsList.add(Pair(s, chip))
            stikerScaleRow.addView(chip)
        }
        scaleCard.addView(stikerScaleRow)
        stikerContainer.addView(scaleCard)

        // 6. Transparansi Kotak Stiker (Alpha) Card
        val stikerAlphaCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#818CF8"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val stikerAlphaTitle = TextView(this).apply {
            val pct = (stikerManager.boxAlpha.value * 100).toInt()
            text = "Transparansi: $pct%"
            setTextColor(Color.parseColor("#818CF8"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        stikerBoxAlphaText = stikerAlphaTitle
        stikerAlphaCard.addView(stikerAlphaTitle)

        val stikerAlphaStepperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }
        stikerAlphaStepperRow.addView(createPureTextBtn("-10%") {
            stikerManager.adjustBoxAlpha(-0.10f)
            updateBuyerNotifUi()
        })
        stikerAlphaStepperRow.addView(createPureTextBtn("-5%") {
            stikerManager.adjustBoxAlpha(-0.05f)
            updateBuyerNotifUi()
        })
        stikerAlphaStepperRow.addView(createPureTextBtn("+5%") {
            stikerManager.adjustBoxAlpha(0.05f)
            updateBuyerNotifUi()
        })
        stikerAlphaStepperRow.addView(createPureTextBtn("+10%") {
            stikerManager.adjustBoxAlpha(0.10f)
            updateBuyerNotifUi()
        })
        stikerAlphaCard.addView(stikerAlphaStepperRow)

        val stikerAlphaChipsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val stikerAlphaChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stikerAlphaChipsList.clear()
        listOf(
            0.30f to "30%",
            0.50f to "50%",
            0.70f to "70%",
            0.85f to "85%",
            0.95f to "95%",
            1.00f to "100%"
        ).forEach { (alphaVal, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8.5f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(3), dp(6), dp(3))
                val isMatched = Math.abs(alphaVal - stikerManager.boxAlpha.value) < 0.04f
                setTextColor(if (isMatched) Color.BLACK else Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isMatched) {
                        setColor(Color.parseColor("#818CF8"))
                        setStroke(dp(1), Color.WHITE)
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#334155"))
                    }
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    stikerManager.setBoxAlpha(alphaVal)
                    updateBuyerNotifUi()
                }
            }
            stikerAlphaChipsList.add(Pair(alphaVal, chip))
            stikerAlphaChipsRow.addView(chip)
        }
        stikerAlphaChipsScroll.addView(stikerAlphaChipsRow)
        stikerAlphaCard.addView(stikerAlphaChipsScroll)
        stikerContainer.addView(stikerAlphaCard)

        // 7. Fitur Stiker Pindah Acak (Random Wander 5 Detik di 1/3 Tengah Layar)
        val stikerRandomMoveCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#172554"))
                setStroke(dp(1), Color.parseColor("#3B82F6"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val randomMoveTitle = TextView(this).apply {
            text = "Gerak Acak (Tengah):"
            setTextColor(Color.parseColor("#60A5FA"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        stikerRandomMoveCard.addView(randomMoveTitle)

        val btnToggleRandom = TextView(this).apply {
            val isRand = stikerManager.isRandomMoveEnabled.value
            text = if (isRand) "NONAKTIFKAN" else "AKTIFKAN"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isRand) Color.parseColor("#2563EB") else Color.parseColor("#334155"))
            }
            setOnClickListener {
                val next = stikerManager.toggleRandomMove()
                Toast.makeText(applicationContext, if (next) "Pindah Acak: AKTIF" else "Pindah Acak: NONAKTIF", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi()
            }
        }
        btnToggleStikerRandomMove = btnToggleRandom
        stikerRandomMoveCard.addView(btnToggleRandom)

        val btnJumpRandomNow = TextView(this).apply {
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
                setColor(Color.parseColor("#1E3A8A"))
                setStroke(dp(1), Color.parseColor("#60A5FA"))
            }
            setOnClickListener {
                stikerManager.pickRandomMiddlePosition()
                val x = (stikerManager.posXpct.value * 100).toInt()
                val y = (stikerManager.posYpct.value * 100).toInt()
                Toast.makeText(applicationContext, "Posisi: X=$x% Y=$y%", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi()
            }
        }
        stikerRandomMoveCard.addView(btnJumpRandomNow)
        stikerContainer.addView(stikerRandomMoveCard)

        tabNotifContainer.addView(stikerContainer)
    }
}
