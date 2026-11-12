package com.tajasb.floating.tabs

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tajasb.FloatingControlService
import com.tajasb.floating.components.FloatingUiWidgets
import kotlin.math.abs

/**
 * BuyerNotifTabBuilder:
 * Modular builder for Tab 4 Sub-Mode 2: Notif Pembeli (Jualan), Kustom Kapsul Presisi,
 * Ukuran Font & Keranjang, plus unified state updater (updateBuyerNotifUi) for all Tab 4 sub-modes.
 */
object BuyerNotifTabBuilder {

    fun populateSalesTab(
        service: FloatingControlService,
        tabNotifContainer: LinearLayout,
        dp: (Int) -> Int,
        createStepperBtn: (String, () -> Unit) -> TextView,
        createPresetChip: (String, () -> Unit) -> TextView
    ) = with(service) {
        val buyerNotif = com.tajasb.notif.BuyerNotificationManager.getInstance(this)

        // =========================================================
        // B. MODE 2: NOTIF PEMBELI (JUALAN) CONTAINER
        // =========================================================
        val salesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerModeSales = salesContainer

        // 1. Status & Start/Stop Sales Card
        val notifStatusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#261108"))
                setStroke(dp(1), Color.parseColor("#F97316"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val notifStatusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val notifBadge = TextView(this).apply {
            text = "STANDBY"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.GRAY)
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        buyerNotifStatusBadge = notifBadge
        notifStatusRow.addView(notifBadge)

        val notifCount = TextView(this).apply {
            text = "Total: 0 notif"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        buyerNotifCountText = notifCount
        notifStatusRow.addView(notifCount)
        notifStatusCard.addView(notifStatusRow)

        val btnActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val btnToggleBuyerNotif = TextView(this).apply {
            text = "MULAI"
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#EA580C"))
            }
            setOnClickListener {
                val running = buyerNotif.toggleEnabled()
                Toast.makeText(applicationContext, if (running) "Notif Pembeli: ON" else "Notif Pembeli: OFF", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi(this@with)
            }
        }
        btnToggleBuyerNotifText = btnToggleBuyerNotif
        btnActionRow.addView(btnToggleBuyerNotif)

        val btnTestTrigger = TextView(this).apply {
            text = "MUNCULKAN"
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#EAA728"))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginStart = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#EAA728"))
            }
            setOnClickListener {
                buyerNotif.triggerManualNotification()
                Toast.makeText(applicationContext, "Memunculkan notif...", Toast.LENGTH_SHORT).show()
            }
        }
        btnActionRow.addView(btnTestTrigger)
        notifStatusCard.addView(btnActionRow)

        val btnCopySales = TextView(this).apply {
            text = "Salin Pengaturan"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#9A3412"))
                setStroke(dp(1), Color.parseColor("#F97316"))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                copyToClipboard("Notif Jualan", buyerNotif.getFormattedSettingsText())
            }
        }
        notifStatusCard.addView(btnCopySales)
        salesContainer.addView(notifStatusCard)

        // Accordion untuk Parameter Lanjutan & Desain Kapsul
        val salesAdvContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        var isSalesAdvOpen = false
        val salesAdvHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val tvAdvTitle = TextView(this).apply {
            text = "Parameter & Desain Kapsul"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FB923C"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        salesAdvHeader.addView(tvAdvTitle)

        val btnAdvArrow = TextView(this).apply {
            text = "▸"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FB923C"))
            setPadding(dp(4), 0, dp(2), 0)
        }
        salesAdvHeader.addView(btnAdvArrow)

        salesAdvHeader.setOnClickListener {
            isSalesAdvOpen = !isSalesAdvOpen
            salesAdvContent.visibility = if (isSalesAdvOpen) View.VISIBLE else View.GONE
            btnAdvArrow.text = if (isSalesAdvOpen) "▾" else "▸"
        }
        salesContainer.addView(salesAdvHeader)
        salesContainer.addView(salesAdvContent)

        // 1.5. Arah Masuk Animasi Notif Card
        val dirCard = LinearLayout(this).apply {
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

        val dirTitle = TextView(this).apply {
            text = "Arah Animasi:"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FB923C"))
        }
        dirCard.addView(dirTitle)

        val dirChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }
        notifDirectionChipsList.clear()
        listOf(
            "LEFT" to "Kiri",
            "RIGHT" to "Kanan",
            "TOP" to "Atas",
            "BOTTOM" to "Bawah",
            "FADE" to "Pudar"
        ).forEach { (dirKey, label) ->
            val chip = TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(3), dp(2), dp(3))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    buyerNotif.setEntryDirection(dirKey)
                }
            }
            notifDirectionChipsList.add(Pair(dirKey, chip))
            dirChipsRow.addView(chip)
        }
        dirCard.addView(dirChipsRow)
        salesAdvContent.addView(dirCard)

        // 2. Durasi Tampil Card
        val durationCard = LinearLayout(this).apply {
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

        val durTitle = TextView(this).apply {
            text = "Durasi Tampil: 3.84s"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FB923C"))
        }
        buyerNotifDurationText = durTitle
        durationCard.addView(durTitle)

        val durStepRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }
        listOf(-1000L, -500L, -100L, 0L, 100L, 500L, 1000L).forEach { delta ->
            val stepBtn = TextView(this).apply {
                text = if (delta == 0L) "3.84s" else if (delta > 0) "+${delta / 1000.0}s" else "${delta / 1000.0}s"
                setTextColor(if (delta == 0L) Color.parseColor("#F97316") else if (delta > 0) Color.parseColor("#10B981") else Color.parseColor("#FCA5A5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(3), dp(2), dp(3))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (delta == 0L) Color.parseColor("#F97316") else Color.parseColor("#334155"))
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                setOnClickListener {
                    if (delta == 0L) {
                        buyerNotif.setDisplayDurationMs(3840L)
                    } else {
                        val cur = buyerNotif.displayDurationMs.value
                        buyerNotif.setDisplayDurationMs(cur + delta)
                    }
                }
            }
            durStepRow.addView(stepBtn)
        }
        durationCard.addView(durStepRow)

        val durChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }
        notifDurationChipsList.clear()
        listOf(2000L, 3000L, 3840L, 4500L, 5000L).forEach { msVal ->
            val chip = TextView(this).apply {
                text = if (msVal == 3840L) "3.84s" else "${msVal / 1000.0}s"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(2), dp(4), dp(2))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    buyerNotif.setDisplayDurationMs(msVal)
                }
            }
            notifDurationChipsList.add(Pair(msVal, chip))
            durChipsRow.addView(chip)
        }
        durationCard.addView(durChipsRow)
        salesAdvContent.addView(durationCard)

        // 3. Jeda Antar Notif (Interval) Card
        val intervalCard = LinearLayout(this).apply {
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

        val intTitle = TextView(this).apply {
            text = "Jeda Muncul: Acak 5s - 12s"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FB923C"))
        }
        buyerNotifIntervalText = intTitle
        intervalCard.addView(intTitle)

        val intChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }
        notifIntervalChipsList.clear()
        listOf(Pair(3, 6), Pair(5, 12), Pair(10, 20)).forEach { range ->
            val chip = TextView(this).apply {
                text = when (range) {
                    Pair(3, 6) -> "3-6s"
                    Pair(5, 12) -> "5-12s"
                    else -> "10-20s"
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(3), dp(4), dp(3))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    buyerNotif.setIntervalRange(range.first, range.second)
                }
            }
            notifIntervalChipsList.add(Pair(range, chip))
            intChipsRow.addView(chip)
        }
        intervalCard.addView(intChipsRow)
        salesAdvContent.addView(intervalCard)

        // 4. Format Template & Rentang Jumlah Notifikasi Jualan
        val actionCard = LinearLayout(this).apply {
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

        val actTitle = TextView(this).apply {
            text = "Format Notifikasi:"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        buyerNotifActionTextLabel = actTitle
        actionCard.addView(actTitle)

        val formatSubtitle = TextView(this).apply {
            text = "Pilihan Format:"
            textSize = 9f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(4), 0, 0)
        }
        actionCard.addView(formatSubtitle)

        val formatChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, 0)
        }
        notifFormatChipsList.clear()
        listOf(
            Pair(com.tajasb.notif.BuyerNotificationManager.FORMAT_MIXED, "Semua"),
            Pair(com.tajasb.notif.BuyerNotificationManager.FORMAT_BUYING, "Membeli"),
            Pair(com.tajasb.notif.BuyerNotificationManager.FORMAT_VIEWING, "Melihat"),
            Pair(com.tajasb.notif.BuyerNotificationManager.FORMAT_BOUGHT, "Pembeli")
        ).forEach { (fmtKey, labelStr) ->
            val chip = TextView(this).apply {
                text = labelStr
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(2), dp(2), dp(2))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    buyerNotif.setSalesFormat(fmtKey)
                }
            }
            notifFormatChipsList.add(Pair(fmtKey, chip))
            formatChipsRow.addView(chip)
        }
        actionCard.addView(formatChipsRow)

        // =========================================================================
        // --- 1. RENTANG JUMLAH ORANG (DARI ... SAMPAI ...) - ACAK OTOMATIS ---
        // =========================================================================
        val countCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#170E08"))
                setStroke(dp(1), Color.parseColor("#EA580C"))
            }
            setPadding(dp(8), dp(7), dp(8), dp(7))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val countHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val countTitle = TextView(this).apply {
            text = "Rentang Jumlah Orang:"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        countHeaderRow.addView(countTitle)

        val countStatusBadge = TextView(this).apply {
            val cMin = buyerNotif.countMin.value
            val cMax = buyerNotif.countMax.value
            text = "$cMin - $cMax Org"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FB923C"))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#431407"))
                setStroke(dp(1), Color.parseColor("#EA580C"))
            }
        }
        buyerCountRangeStatusText = countStatusBadge
        countHeaderRow.addView(countStatusBadge)
        countCard.addView(countHeaderRow)

        val countSubDesc = TextView(this).apply {
            text = "Setiap notif otomatis memunculkan angka acak di rentang ini."
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, dp(4))
        }
        countCard.addView(countSubDesc)

        // Simple interactive input fields: "Dari [min] s/d [max]" + [Simpan]
        val countInputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }

        val lblFromCount = TextView(this).apply {
            text = "Dari:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FDBA74"))
            setPadding(0, 0, dp(4), 0)
        }
        countInputRow.addView(lblFromCount)

        val etCountMin = EditText(this).apply {
            hint = "Min"
            setText(buyerNotif.countMin.value.toString())
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#EA580C"))
            }
            setPadding(dp(6), dp(5), dp(6), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
        etBuyerCountMin = etCountMin
        countInputRow.addView(etCountMin)

        val lblToCount = TextView(this).apply {
            text = "Sampai:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FDBA74"))
            setPadding(dp(6), 0, dp(4), 0)
        }
        countInputRow.addView(lblToCount)

        val etCountMax = EditText(this).apply {
            hint = "Max"
            setText(buyerNotif.countMax.value.toString())
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#EA580C"))
            }
            setPadding(dp(6), dp(5), dp(6), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
        etBuyerCountMax = etCountMax
        countInputRow.addView(etCountMax)

        val btnApplyCount = TextView(this).apply {
            text = "SIMPAN"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(5)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#EA580C"))
            }
            setOnClickListener {
                val minVal = etCountMin.text.toString().toIntOrNull() ?: 1
                val maxVal = etCountMax.text.toString().toIntOrNull() ?: minVal
                buyerNotif.setCountMinMax(minVal, maxVal)
                Toast.makeText(applicationContext, "Jumlah orang: $minVal - $maxVal", Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi(this@with)
            }
        }
        countInputRow.addView(btnApplyCount)
        countCard.addView(countInputRow)

        // Interactive Stepper Buttons for Quick Increment/Decrement during live!
        val countStepperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(1), 0, dp(1))
        }
        listOf(-10, -5, +5, +10).forEach { delta ->
            val stepBtn = TextView(this).apply {
                text = if (delta > 0) "+$delta" else "$delta"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(if (delta > 0) Color.parseColor("#10B981") else Color.parseColor("#FCA5A5"))
                setPadding(dp(4), dp(3), dp(4), dp(3))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
                setOnClickListener {
                    val curMin = buyerNotif.countMin.value
                    val curMax = buyerNotif.countMax.value
                    val newMin = (curMin + delta).coerceAtLeast(1)
                    val newMax = (curMax + delta).coerceAtLeast(newMin)
                    buyerNotif.setCountMinMax(newMin, newMax)
                    etCountMin.setText(newMin.toString())
                    etCountMax.setText(newMax.toString())
                    updateBuyerNotifUi(this@with)
                }
            }
            countStepperRow.addView(stepBtn)
        }
        countCard.addView(countStepperRow)
        actionCard.addView(countCard)

        // =========================================================================
        // --- 2. NOMOR ETALASE PRODUK (DARI NO. ... SAMPAI NO. ...) - ACAK OTOMATIS ---
        // =========================================================================
        val prodCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#181404"))
                setStroke(dp(1), Color.parseColor("#CA8A04"))
            }
            setPadding(dp(8), dp(7), dp(8), dp(7))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val prodHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val prodTitle = TextView(this).apply {
            text = "Rentang Nomor Etalase:"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        prodHeaderRow.addView(prodTitle)

        val prodStatusBadge = TextView(this).apply {
            val pMin = buyerNotif.productMin.value
            val pMax = buyerNotif.productMax.value
            text = if (pMin == pMax) "No. $pMin" else "No. $pMin - $pMax"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FDE047"))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#422006"))
                setStroke(dp(1), Color.parseColor("#CA8A04"))
            }
        }
        buyerProductRangeStatusText = prodStatusBadge
        prodHeaderRow.addView(prodStatusBadge)
        prodCard.addView(prodHeaderRow)

        val prodSubDesc = TextView(this).apply {
            text = "Setiap notif otomatis memunculkan nomor acak di rentang ini."
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, dp(4))
        }
        prodCard.addView(prodSubDesc)

        // Simple interactive input fields: "Dari No. [min] s/d [max]" + [Simpan]
        val prodInputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }

        val lblFromProd = TextView(this).apply {
            text = "Dari No.:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FEF08A"))
            setPadding(0, 0, dp(4), 0)
        }
        prodInputRow.addView(lblFromProd)

        val etProdMin = EditText(this).apply {
            hint = "Min"
            setText(buyerNotif.productMin.value.toString())
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#CA8A04"))
            }
            setPadding(dp(6), dp(5), dp(6), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
        etBuyerProdMin = etProdMin
        prodInputRow.addView(etProdMin)

        val lblToProd = TextView(this).apply {
            text = "Sampai:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FEF08A"))
            setPadding(dp(6), 0, dp(4), 0)
        }
        prodInputRow.addView(lblToProd)

        val etProdMax = EditText(this).apply {
            hint = "Max"
            setText(buyerNotif.productMax.value.toString())
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#CA8A04"))
            }
            setPadding(dp(6), dp(5), dp(6), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
        etBuyerProdMax = etProdMax
        prodInputRow.addView(etProdMax)

        val btnApplyProd = TextView(this).apply {
            text = "SIMPAN"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(5)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#FDE047"))
            }
            setOnClickListener {
                val minVal = etProdMin.text.toString().toIntOrNull() ?: 1
                val maxVal = etProdMax.text.toString().toIntOrNull() ?: minVal
                buyerNotif.setProductMinMax(minVal, maxVal)
                val toastDesc = if (minVal == maxVal) "Etalase: No. $minVal" else "Etalase: No. $minVal - $maxVal"
                Toast.makeText(applicationContext, toastDesc, Toast.LENGTH_SHORT).show()
                updateBuyerNotifUi(this@with)
            }
        }
        prodInputRow.addView(btnApplyProd)
        prodCard.addView(prodInputRow)

        // Interactive Stepper Buttons for Quick Increment/Decrement Etalase!
        val prodStepperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(1), 0, dp(1))
        }
        listOf(-5, -1, +1, +5).forEach { delta ->
            val stepBtn = TextView(this).apply {
                text = if (delta > 0) "+$delta" else "$delta"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(if (delta > 0) Color.parseColor("#FDE047") else Color.parseColor("#CBD5E1"))
                setPadding(dp(4), dp(3), dp(4), dp(3))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
                setOnClickListener {
                    val curMin = buyerNotif.productMin.value
                    val curMax = buyerNotif.productMax.value
                    val newMin = (curMin + delta).coerceAtLeast(1)
                    val newMax = (curMax + delta).coerceAtLeast(newMin)
                    buyerNotif.setProductMinMax(newMin, newMax)
                    etProdMin.setText(newMin.toString())
                    etProdMax.setText(newMax.toString())
                    updateBuyerNotifUi(this@with)
                }
            }
            prodStepperRow.addView(stepBtn)
        }
        prodCard.addView(prodStepperRow)
        actionCard.addView(prodCard)
        salesAdvContent.addView(actionCard)

        // 4. Kustom Kapsul Presisi
        val capsuleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#D97724"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val capsuleTitle = TextView(this).apply {
            text = "💊 Kustom Kapsul Presisi (Bentuk & Ukuran)"
            setTextColor(Color.parseColor("#D97724"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        capsuleCard.addView(capsuleTitle)

        val capsuleInfo = TextView(this).apply {
            val cRad = buyerNotif.capsuleCornerRadius.value
            val cPadH = buyerNotif.capsulePaddingH.value
            val cPadV = buyerNotif.capsulePaddingV.value
            text = "Sudut: ${cRad}dp | Pad Samping: ${cPadH}dp | Pad Atas/Bwh: ${cPadV}dp"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 8.5f
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
                bottomMargin = dp(4)
            }
            layoutParams = lp
        }
        buyerNotifCapsuleInfoText = capsuleInfo
        capsuleCard.addView(capsuleInfo)

        val radRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
        }
        val radLabel = TextView(this).apply {
            text = "Sudut:"
            setTextColor(Color.WHITE)
            textSize = 8.5f
            val lp = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        radRow.addView(radLabel)
        radRow.addView(createStepperBtn(" -5 ") {
            buyerNotif.adjustCapsuleCornerRadius(-5)
            updateBuyerNotifUi(this@with)
        })
        radRow.addView(createStepperBtn(" -1 ") {
            buyerNotif.adjustCapsuleCornerRadius(-1)
            updateBuyerNotifUi(this@with)
        })
        radRow.addView(createStepperBtn(" +1 ") {
            buyerNotif.adjustCapsuleCornerRadius(1)
            updateBuyerNotifUi(this@with)
        })
        radRow.addView(createStepperBtn(" +5 ") {
            buyerNotif.adjustCapsuleCornerRadius(5)
            updateBuyerNotifUi(this@with)
        })
        capsuleCard.addView(radRow)

        val radPresetRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        radPresetRow1.addView(createPresetChip("Kotak 0dp") {
            buyerNotif.setCapsuleCornerRadius(0)
            updateBuyerNotifUi(this@with)
        })
        radPresetRow1.addView(createPresetChip("Kecil 8dp") {
            buyerNotif.setCapsuleCornerRadius(8)
            updateBuyerNotifUi(this@with)
        })
        radPresetRow1.addView(createPresetChip("Kapsul 18dp") {
            buyerNotif.setCapsuleCornerRadius(18)
            updateBuyerNotifUi(this@with)
        })
        radPresetRow1.addView(createPresetChip("Melengkung 26dp") {
            buyerNotif.setCapsuleCornerRadius(26)
            updateBuyerNotifUi(this@with)
        })
        capsuleCard.addView(radPresetRow1)

        val radPresetRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        radPresetRow2.addView(createPresetChip("Bulat 36dp") {
            buyerNotif.setCapsuleCornerRadius(36)
            updateBuyerNotifUi(this@with)
        })
        radPresetRow2.addView(createPresetChip("Oval 50dp") {
            buyerNotif.setCapsuleCornerRadius(50)
            updateBuyerNotifUi(this@with)
        })
        radPresetRow2.addView(createPresetChip("Bulat 75dp") {
            buyerNotif.setCapsuleCornerRadius(75)
            updateBuyerNotifUi(this@with)
        })
        radPresetRow2.addView(createPresetChip("Bulat Penuh 100dp") {
            buyerNotif.setCapsuleCornerRadius(100)
            updateBuyerNotifUi(this@with)
        })
        capsuleCard.addView(radPresetRow2)

        val padHRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
        }
        val padHLabel = TextView(this).apply {
            text = "Lebar (H):"
            setTextColor(Color.WHITE)
            textSize = 8.5f
            val lp = LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        padHRow.addView(padHLabel)
        padHRow.addView(createStepperBtn(" -1 ") {
            buyerNotif.adjustCapsulePaddingH(-1)
            updateBuyerNotifUi(this@with)
        })
        padHRow.addView(createStepperBtn(" +1 ") {
            buyerNotif.adjustCapsulePaddingH(1)
            updateBuyerNotifUi(this@with)
        })
        padHRow.addView(createPresetChip("Rapat 4dp") {
            buyerNotif.setCapsulePaddingH(4)
            updateBuyerNotifUi(this@with)
        })
        padHRow.addView(createPresetChip("Normal 8dp") {
            buyerNotif.setCapsulePaddingH(8)
            updateBuyerNotifUi(this@with)
        })
        padHRow.addView(createPresetChip("Lebar 14dp") {
            buyerNotif.setCapsulePaddingH(14)
            updateBuyerNotifUi(this@with)
        })
        capsuleCard.addView(padHRow)

        val padVRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
        }
        val padVLabel = TextView(this).apply {
            text = "Tinggi (V):"
            setTextColor(Color.WHITE)
            textSize = 8.5f
            val lp = LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        padVRow.addView(padVLabel)
        padVRow.addView(createStepperBtn(" -5 ") {
            buyerNotif.adjustCapsulePaddingV(-5)
            updateBuyerNotifUi(this@with)
        })
        padVRow.addView(createStepperBtn(" -2 ") {
            buyerNotif.adjustCapsulePaddingV(-2)
            updateBuyerNotifUi(this@with)
        })
        padVRow.addView(createStepperBtn(" -1 ") {
            buyerNotif.adjustCapsulePaddingV(-1)
            updateBuyerNotifUi(this@with)
        })
        padVRow.addView(createStepperBtn(" +1 ") {
            buyerNotif.adjustCapsulePaddingV(1)
            updateBuyerNotifUi(this@with)
        })
        padVRow.addView(createStepperBtn(" +2 ") {
            buyerNotif.adjustCapsulePaddingV(2)
            updateBuyerNotifUi(this@with)
        })
        padVRow.addView(createStepperBtn(" +5 ") {
            buyerNotif.adjustCapsulePaddingV(5)
            updateBuyerNotifUi(this@with)
        })
        capsuleCard.addView(padVRow)

        val padVPresetRow0 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        padVPresetRow0.addView(createPresetChip("Razor -30dp") {
            buyerNotif.setCapsulePaddingV(-30)
            updateBuyerNotifUi(this@with)
        })
        padVPresetRow0.addView(createPresetChip("Ultra -20dp") {
            buyerNotif.setCapsulePaddingV(-20)
            updateBuyerNotifUi(this@with)
        })
        padVPresetRow0.addView(createPresetChip("Ultra -15dp") {
            buyerNotif.setCapsulePaddingV(-15)
            updateBuyerNotifUi(this@with)
        })
        padVPresetRow0.addView(createPresetChip("Max -10dp") {
            buyerNotif.setCapsulePaddingV(-10)
            updateBuyerNotifUi(this@with)
        })
        capsuleCard.addView(padVPresetRow0)

        val padVPresetRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        padVPresetRow1.addView(createPresetChip("Slim -3dp") {
            buyerNotif.setCapsulePaddingV(-3)
            updateBuyerNotifUi(this@with)
        })
        padVPresetRow1.addView(createPresetChip("Tipis -1dp") {
            buyerNotif.setCapsulePaddingV(-1)
            updateBuyerNotifUi(this@with)
        })
        padVPresetRow1.addView(createPresetChip("Nol 0dp") {
            buyerNotif.setCapsulePaddingV(0)
            updateBuyerNotifUi(this@with)
        })
        padVPresetRow1.addView(createPresetChip("Rapat 1dp") {
            buyerNotif.setCapsulePaddingV(1)
            updateBuyerNotifUi(this@with)
        })
        capsuleCard.addView(padVPresetRow1)

        val padVPresetRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        padVPresetRow2.addView(createPresetChip("Normal 3dp") {
            buyerNotif.setCapsulePaddingV(3)
            updateBuyerNotifUi(this@with)
        })
        padVPresetRow2.addView(createPresetChip("Sedang 5dp") {
            buyerNotif.setCapsulePaddingV(5)
            updateBuyerNotifUi(this@with)
        })
        padVPresetRow2.addView(createPresetChip("Tinggi 8dp") {
            buyerNotif.setCapsulePaddingV(8)
            updateBuyerNotifUi(this@with)
        })
        capsuleCard.addView(padVPresetRow2)

        val resetBtn = TextView(this).apply {
            text = "↺ Reset Standar Kapsul (39dp, 27dp, 2dp, #ff941a)"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 8.5f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                buyerNotif.resetCapsuleDefaults()
                updateBuyerNotifUi(this@with)
                Toast.makeText(applicationContext, "Kapsul direset ke standar", Toast.LENGTH_SHORT).show()
            }
        }
        capsuleCard.addView(resetBtn)

        val colSubTitle = TextView(this).apply {
            text = "🎨 Warna Background Kapsul:"
            textSize = 9f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(6), 0, 0)
        }
        capsuleCard.addView(colSubTitle)

        val colScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val colChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        capsuleColorChipsList.clear()
        listOf(
            "#FACC15" to "🟡 Kuning Terang",
            "#FDE047" to "🟡 Kuning Cerah",
            "#ff941a" to "🟠 Oranye (#ff941a)",
            "#FF941A" to "🟠 Oranye Cerah",
            "#D97724" to "🟠 Oranye (TikTok)",
            "#DC2626" to "🔴 Merah",
            "#0F172A" to "🌑 Navy Gelap",
            "#000000" to "⬛ Hitam",
            "#16A34A" to "🟢 Hijau",
            "#2563EB" to "🔵 Biru",
            "#9333EA" to "🟣 Ungu",
            "#F59E0B" to "🟡 Emas",
            "#FFFFFF" to "⬜ Putih"
        ).forEach { (hex, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(3), dp(6), dp(3))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    buyerNotif.setCapsuleBgColor(hex)
                    updateBuyerNotifUi(this@with)
                }
            }
            capsuleColorChipsList.add(Pair(hex, chip))
            colChipsRow.addView(chip)
        }
        colScroll.addView(colChipsRow)
        capsuleCard.addView(colScroll)

        val customColorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, 0)
        }
        val btnInputCustomColor = TextView(this).apply {
            text = "✏️ Custom Isian Kode Hex (#...)"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#C2410C"))
                setStroke(dp(1), Color.parseColor("#F97316"))
            }
            setOnClickListener {
                showHexColorInputDialog("Warna Background Kapsul", buyerNotif.capsuleBgColorHex.value) { hex ->
                    buyerNotif.setCapsuleBgColor(hex)
                    updateBuyerNotifUi(this@with)
                }
            }
        }
        customColorRow.addView(btnInputCustomColor)
        val curColorLabel = TextView(this).apply {
            text = "  ${buyerNotif.capsuleBgColorHex.value}"
            textSize = 8.5f
            setTextColor(Color.parseColor("#CBD5E1"))
        }
        buyerNotifCapsuleColorText = curColorLabel
        customColorRow.addView(curColorLabel)
        capsuleCard.addView(customColorRow)

        val alphaSubTitle = TextView(this).apply {
            val curAlphaPct = (buyerNotif.capsuleAlpha.value * 100).toInt()
            text = "👻 Transparansi Kapsul: $curAlphaPct%"
            textSize = 9f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(6), 0, 0)
        }
        buyerNotifCapsuleAlphaText = alphaSubTitle
        capsuleCard.addView(alphaSubTitle)

        val alphaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        alphaRow.addView(createStepperBtn(" -10% ") {
            buyerNotif.adjustCapsuleAlpha(-0.10f)
            updateBuyerNotifUi(this@with)
        })
        alphaRow.addView(createStepperBtn(" -5% ") {
            buyerNotif.adjustCapsuleAlpha(-0.05f)
            updateBuyerNotifUi(this@with)
        })
        alphaRow.addView(createStepperBtn(" +5% ") {
            buyerNotif.adjustCapsuleAlpha(0.05f)
            updateBuyerNotifUi(this@with)
        })
        alphaRow.addView(createStepperBtn(" +10% ") {
            buyerNotif.adjustCapsuleAlpha(0.10f)
            updateBuyerNotifUi(this@with)
        })
        capsuleCard.addView(alphaRow)

        val alphaPresetScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val alphaChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        capsuleAlphaChipsList.clear()
        listOf(
            0.30f to "30%",
            0.50f to "50%",
            0.70f to "70%",
            0.85f to "85%",
            0.95f to "95%",
            1.00f to "100% (Solid)"
        ).forEach { (alphaVal, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(3), dp(6), dp(3))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    buyerNotif.setCapsuleAlpha(alphaVal)
                    updateBuyerNotifUi(this@with)
                }
            }
            capsuleAlphaChipsList.add(Pair(alphaVal, chip))
            alphaChipsRow.addView(chip)
        }
        alphaPresetScroll.addView(alphaChipsRow)
        capsuleCard.addView(alphaPresetScroll)

        val outlineSubTitle = TextView(this).apply {
            val oW = buyerNotif.capsuleOutlineWidth.value
            val oCol = buyerNotif.capsuleOutlineColor.value
            text = "🖼️ Outline Kapsul: ${oW}dp ($oCol)"
            textSize = 9f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(6), 0, 0)
        }
        buyerNotifCapsuleOutlineText = outlineSubTitle
        capsuleCard.addView(outlineSubTitle)

        val outlineWRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        val oWLabel = TextView(this).apply {
            text = "Tebal:"
            setTextColor(Color.WHITE)
            textSize = 8.5f
            val lp = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        outlineWRow.addView(oWLabel)
        outlineWRow.addView(createStepperBtn(" -1 ") {
            buyerNotif.adjustCapsuleOutlineWidth(-1)
            updateBuyerNotifUi(this@with)
        })
        outlineWRow.addView(createStepperBtn(" +1 ") {
            buyerNotif.adjustCapsuleOutlineWidth(1)
            updateBuyerNotifUi(this@with)
        })

        capsuleOutlineWidthChipsList.clear()
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
                    buyerNotif.setCapsuleOutlineWidth(wVal)
                    updateBuyerNotifUi(this@with)
                }
            }
            capsuleOutlineWidthChipsList.add(Pair(wVal, chip))
            outlineWRow.addView(chip)
        }
        capsuleCard.addView(outlineWRow)

        val outlineColScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val outlineColChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        capsuleOutlineColorChipsList.clear()
        listOf(
            "#FFFFFF" to "⬜ Putih",
            "#000000" to "⬛ Hitam",
            "#FDE047" to "🟡 Kuning",
            "#00E5FF" to "🟦 Cyan",
            "#EF4444" to "🔴 Merah",
            "#EA580C" to "🟠 Oranye"
        ).forEach { (hex, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(3), dp(6), dp(3))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    buyerNotif.setCapsuleOutlineColor(hex)
                    updateBuyerNotifUi(this@with)
                }
            }
            capsuleOutlineColorChipsList.add(Pair(hex, chip))
            outlineColChipsRow.addView(chip)
        }
        outlineColScroll.addView(outlineColChipsRow)
        capsuleCard.addView(outlineColScroll)

        val btnCustomOutlineCol = TextView(this).apply {
            text = "✏️ Custom Isian Hex Outline (#...)"
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
                setColor(Color.parseColor("#475569"))
                setStroke(dp(1), Color.WHITE)
            }
            setOnClickListener {
                showHexColorInputDialog("Warna Outline Kapsul", buyerNotif.capsuleOutlineColor.value) { hex ->
                    buyerNotif.setCapsuleOutlineColor(hex)
                    updateBuyerNotifUi(this@with)
                }
            }
        }
        capsuleCard.addView(btnCustomOutlineCol)

        salesAdvContent.addView(capsuleCard)

        // 5. Kustom Ukuran Font & Keranjang (Terpisah)
        val fontAndCartCard = LinearLayout(this).apply {
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

        val fontCartTitle = TextView(this).apply {
            text = "🔤 Ukuran Font & 🛒 Ikon Keranjang (Terpisah)"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        fontAndCartCard.addView(fontCartTitle)

        val fontCartInfo = TextView(this).apply {
            val fSp = buyerNotif.fontSizeSp.value
            val cDp = buyerNotif.cartIconSizeDp.value
            text = "Ukuran Font: ${fSp}sp | Ukuran Keranjang: ${cDp}dp"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 8.5f
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
                bottomMargin = dp(4)
            }
            layoutParams = lp
        }
        buyerNotifFontAndIconInfoText = fontCartInfo
        fontAndCartCard.addView(fontCartInfo)

        val fontRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }
        val fontLabel = TextView(this).apply {
            text = "Font:"
            setTextColor(Color.WHITE)
            textSize = 8.5f
            val lp = LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        fontRow.addView(fontLabel)
        fontRow.addView(createStepperBtn(" -1sp ") {
            buyerNotif.adjustFontSizeSp(-1.0f)
            updateBuyerNotifUi(this@with)
        })
        fontRow.addView(createStepperBtn(" -0.5 ") {
            buyerNotif.adjustFontSizeSp(-0.5f)
            updateBuyerNotifUi(this@with)
        })
        fontRow.addView(createStepperBtn(" +0.5 ") {
            buyerNotif.adjustFontSizeSp(0.5f)
            updateBuyerNotifUi(this@with)
        })
        fontRow.addView(createStepperBtn(" +1sp ") {
            buyerNotif.adjustFontSizeSp(1.0f)
            updateBuyerNotifUi(this@with)
        })
        fontAndCartCard.addView(fontRow)

        val fontPresetScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val fontPresetChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        notifFontSizeChipsList.clear()
        listOf(
            8.0f to "8sp (Kecil)",
            9.5f to "9.5sp",
            10.5f to "10.5sp (Standar)",
            12.0f to "12sp",
            14.0f to "14sp",
            16.0f to "16sp (Besar)"
        ).forEach { (spVal, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(3), dp(6), dp(3))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    buyerNotif.setFontSizeSp(spVal)
                    updateBuyerNotifUi(this@with)
                }
            }
            notifFontSizeChipsList.add(Pair(spVal, chip))
            fontPresetChipsRow.addView(chip)
        }
        fontPresetScroll.addView(fontPresetChipsRow)
        fontAndCartCard.addView(fontPresetScroll)

        val cartRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }
        val cartLabel = TextView(this).apply {
            text = "Keranjang:"
            setTextColor(Color.WHITE)
            textSize = 8.5f
            val lp = LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        cartRow.addView(cartLabel)
        cartRow.addView(createStepperBtn(" -2dp ") {
            buyerNotif.adjustCartIconSizeDp(-2.0f)
            updateBuyerNotifUi(this@with)
        })
        cartRow.addView(createStepperBtn(" -1dp ") {
            buyerNotif.adjustCartIconSizeDp(-1.0f)
            updateBuyerNotifUi(this@with)
        })
        cartRow.addView(createStepperBtn(" +1dp ") {
            buyerNotif.adjustCartIconSizeDp(1.0f)
            updateBuyerNotifUi(this@with)
        })
        cartRow.addView(createStepperBtn(" +2dp ") {
            buyerNotif.adjustCartIconSizeDp(2.0f)
            updateBuyerNotifUi(this@with)
        })
        fontAndCartCard.addView(cartRow)

        val cartScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val cartChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        notifCartSizeChipsList.clear()
        listOf(
            8.0f to "8dp (Kecil)",
            11.0f to "11dp",
            13.0f to "13dp (Standar)",
            16.0f to "16dp",
            20.0f to "20dp",
            26.0f to "26dp (Besar)"
        ).forEach { (dpVal, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(3), dp(6), dp(3))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    buyerNotif.setCartIconSizeDp(dpVal)
                    updateBuyerNotifUi(this@with)
                }
            }
            notifCartSizeChipsList.add(Pair(dpVal, chip))
            cartChipsRow.addView(chip)
        }
        cartScroll.addView(cartChipsRow)
        fontAndCartCard.addView(cartScroll)

        salesAdvContent.addView(fontAndCartCard)

        // 6. Posisi & Skala Notifikasi Jualan (D-Pad & Stepper Bebas)
        val posScaleCard = LinearLayout(this).apply {
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

        val posScaleTitle = TextView(this).apply {
            text = "Posisi & Skala:"
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        posScaleCard.addView(posScaleTitle)

        var isBuyerPosFine = false

        val posInfoText = TextView(this).apply {
            val px = buyerNotif.posX.value
            val py = buyerNotif.posY.value
            val sc = "%.2f".format(buyerNotif.scale.value)
            text = "X=$px Y=$py | Skala: ${sc}x"
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(1), 0, dp(3))
        }
        buyerNotifPosText = posInfoText
        posScaleCard.addView(posInfoText)

        fun updateBuyerPosInfoLabel() {
            val px = buyerNotif.posX.value
            val py = buyerNotif.posY.value
            val sc = "%.2f".format(buyerNotif.scale.value)
            buyerNotifPosText?.text = "X=$px Y=$py | Skala: ${sc}x"
        }

        // Kecepatan: Cepat vs Halus
        val buyerModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(3))
        }
        val btnBuyerFast = TextView(this).apply {
            text = "Cepat"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(2) }
        }
        val btnBuyerFine = TextView(this).apply {
            text = "Halus"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(2) }
        }
        fun refreshBuyerMode() {
            btnBuyerFast.setTextColor(if (!isBuyerPosFine) Color.BLACK else Color.WHITE)
            btnBuyerFast.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (!isBuyerPosFine) Color.parseColor("#F59E0B") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (!isBuyerPosFine) Color.parseColor("#F59E0B") else Color.parseColor("#334155"))
            }
            btnBuyerFine.setTextColor(if (isBuyerPosFine) Color.BLACK else Color.WHITE)
            btnBuyerFine.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isBuyerPosFine) Color.parseColor("#FDE047") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (isBuyerPosFine) Color.parseColor("#FDE047") else Color.parseColor("#334155"))
            }
        }
        btnBuyerFast.setOnClickListener {
            isBuyerPosFine = false
            refreshBuyerMode()
        }
        btnBuyerFine.setOnClickListener {
            isBuyerPosFine = true
            refreshBuyerMode()
        }
        refreshBuyerMode()
        buyerModeRow.addView(btnBuyerFast)
        buyerModeRow.addView(btnBuyerFine)
        posScaleCard.addView(buyerModeRow)

        // D-Pad 4 Arah
        val buyerDpadRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(3))
        }
        buyerDpadRow.addView(createPureIconBtn("⬅️") {
            val delta = if (isBuyerPosFine) 5 else 30
            buyerNotif.movePosition(-delta, 0)
            updateBuyerPosInfoLabel()
        })
        buyerDpadRow.addView(createPureIconBtn("⬆️") {
            val delta = if (isBuyerPosFine) 5 else 30
            buyerNotif.movePosition(0, -delta)
            updateBuyerPosInfoLabel()
        })
        buyerDpadRow.addView(createPureIconBtn("⬇️") {
            val delta = if (isBuyerPosFine) 5 else 30
            buyerNotif.movePosition(0, delta)
            updateBuyerPosInfoLabel()
        })
        buyerDpadRow.addView(createPureIconBtn("➡️") {
            val delta = if (isBuyerPosFine) 5 else 30
            buyerNotif.movePosition(delta, 0)
            updateBuyerPosInfoLabel()
        })
        posScaleCard.addView(buyerDpadRow)

        // Stepper Skala (➖ / ➕)
        val buyerScaleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvBuyerSkalaLabel = TextView(this).apply {
            text = "Skala"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        buyerScaleRow.addView(tvBuyerSkalaLabel)
        buyerScaleRow.addView(createPureIconBtn("➖") {
            val delta = if (isBuyerPosFine) 0.02f else 0.10f
            buyerNotif.changeScale(-delta)
            updateBuyerPosInfoLabel()
        })
        buyerScaleRow.addView(createPureIconBtn("➕") {
            val delta = if (isBuyerPosFine) 0.02f else 0.10f
            buyerNotif.changeScale(delta)
            updateBuyerPosInfoLabel()
        })
        val btnLockPos = TextView(this).apply {
            text = if (buyerNotif.isPositionLocked.value) "Buka Kunci" else "Kunci Posisi"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginStart = dp(4)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (buyerNotif.isPositionLocked.value) Color.parseColor("#0284C7") else Color.parseColor("#334155"))
            }
            setOnClickListener {
                val next = buyerNotif.togglePositionLock()
                text = if (next) "Buka Kunci" else "Kunci Posisi"
                (background as? GradientDrawable)?.setColor(if (next) Color.parseColor("#0284C7") else Color.parseColor("#334155"))
            }
        }
        btnBuyerNotifLockText = btnLockPos
        buyerScaleRow.addView(btnLockPos)
        posScaleCard.addView(buyerScaleRow)

        salesAdvContent.addView(posScaleCard)
        tabNotifContainer.addView(salesContainer)
    }

    fun updateBuyerNotifUi(service: FloatingControlService) = with(service) {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val notifManager = com.tajasb.notif.BuyerNotificationManager.getInstance(this)
        val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)

        val isLocked = notifManager.isPositionLocked.value

        // --- DOA & COMMENT LIVE UI ---
        val isCaptureActive = liveComment.isCaptureActive.value
        val a11yOk = com.tajasb.VcamAccessibilityService.isAccessibilityEnabled(this)

        liveCaptureStatusBadge?.apply {
            text = if (isCaptureActive) "MENANGKAP" else "STANDBY"
            setTextColor(if (isCaptureActive) Color.parseColor("#10B981") else Color.GRAY)
        }

        btnA11yStatusText?.apply {
            text = if (a11yOk) "Aksesibilitas Aktif" else "Izin Aksesibilitas Diperlukan"
            setTextColor(if (a11yOk) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
        }

        btnToggleLiveCaptureText?.apply {
            text = if (isCaptureActive) "Scan: ON" else "Scan: OFF"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isCaptureActive) Color.parseColor("#DC2626") else Color.parseColor("#0891B2"))
            }
        }

        liveCommentQueueText?.text = "Antrean: ${liveComment.queueCount.value} • Total: ${liveComment.totalCapturedCount.value}"

        val currentItem = liveComment.currentDisplayedItem.value
        liveCommentCurrentText?.text = if (currentItem != null) {
            if (currentItem.isGift) "Sedang Tayang: ${currentItem.senderName} ${currentItem.content}"
            else "Sedang Tayang: ${currentItem.senderName}: ${currentItem.content}"
        } else {
            "Sedang Tayang: Menunggu komentar..."
        }

        // Style Icon Mode chips
        val currentIconMode = liveComment.iconMode.value
        iconModeChipsList.forEach { (modeVal, chipTv) ->
            val isMatched = modeVal == currentIconMode
            chipTv.apply {
                setTextColor(if (isMatched) Color.BLACK else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isMatched) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isMatched) {
                        setColor(Color.parseColor("#00E5FF"))
                        setStroke(dp(1), Color.WHITE)
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                }
            }
        }

        // Style Format Mode chips
        val currentFormatMode = liveComment.formatMode.value
        formatModeChipsList.forEach { (modeVal, chipTv) ->
            val isMatched = modeVal == currentFormatMode
            chipTv.apply {
                setTextColor(if (isMatched) Color.BLACK else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isMatched) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isMatched) {
                        setColor(Color.parseColor("#38BDF8"))
                        setStroke(dp(1), Color.WHITE)
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                }
            }
        }

        // Style Custom Prefix chips
        val curPrefix = liveComment.customPrefix.value
        val effectivePrefix = if (curPrefix.isNotBlank()) curPrefix else "Doa"
        val samplePreview = when (currentFormatMode) {
            com.tajasb.livecomment.CommentFormatMode.PREFIX_AND_COMMENT -> "$effectivePrefix: bonus ya kak"
            com.tajasb.livecomment.CommentFormatMode.PREFIX_NAME_COMMENT -> "$effectivePrefix: Bang Jo bonus ya kak"
            com.tajasb.livecomment.CommentFormatMode.NAME_AND_COMMENT -> "Bang Jo: bonus ya kak"
            com.tajasb.livecomment.CommentFormatMode.COMMENT_ONLY -> "bonus ya kak"
        }
        customPrefixDisplayLabel?.text = "Contoh: \"$samplePreview\""

        prefixChipsList.forEach { (pfxVal, chipTv) ->
            val isMatched = pfxVal.equals(curPrefix, ignoreCase = true)
            chipTv.apply {
                setTextColor(if (isMatched) Color.BLACK else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isMatched) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isMatched) {
                        setColor(Color.parseColor("#F59E0B"))
                        setStroke(dp(1), Color.WHITE)
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                }
            }
        }

        // Style Scan Area Filtering views
        val isAreaFiltered = liveComment.isAreaFilterEnabled.value
        val currentPreset = liveComment.scanAreaPreset.value
        val topPct = (liveComment.scanTopPct.value * 100).toInt()
        val bottomPct = (liveComment.scanBottomPct.value * 100).toInt()
        val leftPct = (liveComment.scanLeftPct.value * 100).toInt()
        val rightPct = (liveComment.scanRightPct.value * 100).toInt()

        btnToggleAreaFilterText?.apply {
            text = if (isAreaFiltered) "Filter Area: ON" else "Filter Area: OFF"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isAreaFiltered) Color.parseColor("#064E3B") else Color.parseColor("#451A03"))
                setStroke(dp(1), if (isAreaFiltered) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
            }
        }

        scanAreaInfoText?.text = if (isAreaFiltered) {
            "Area: Y ($topPct% - $bottomPct%) • X ($leftPct% - $rightPct%)"
        } else {
            "Membaca seluruh layar"
        }

        scanAreaPresetChipsList.forEach { (preset, chipTv) ->
            val isMatched = preset == currentPreset
            chipTv.apply {
                setTextColor(if (isMatched) Color.BLACK else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isMatched) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isMatched) {
                        setColor(Color.parseColor("#00E5FF"))
                        setStroke(dp(1), Color.WHITE)
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                        setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                }
            }
        }

        // Style Virtual Banner Position & Rotation status
        val ovX = (liveComment.overlayPosXpct.value * 100).toInt()
        val ovY = (liveComment.overlayPosYpct.value * 100).toInt()
        val ovRot = liveComment.overlayRotation.value
        val ovScale = String.format("%.1f", liveComment.overlayScale.value)
        overlayPosRotStatusText?.text = "Posisi: X $ovX% • Y $ovY% | Rotasi: $ovRot° | Skala: ${ovScale}x"
        btnOverlayRotText?.text = "${ovRot}°"

        btnOverlayMirrorText?.apply {
            val isM = liveComment.overlayMirrored.value
            text = if (isM) "Mir: ON" else "Mir: OFF"
            setTextColor(if (isM) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(if (isM) Color.parseColor("#00E5FF") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (isM) Color.WHITE else Color.parseColor("#334155"))
            }
        }

        // --- SUB-MODE TAB STYLING & CONTAINER VISIBILITY ---
        val currentBannerMode = liveComment.bannerMode.value
        val stikerManager = com.tajasb.stiker.CustomStikerManager.getInstance(this)
        val watermarkManager = com.tajasb.stiker.CustomWatermarkManager.getInstance(this)
        val autoTimeManager = com.tajasb.notif.AutoTimeCapsuleManager.getInstance(this)
        val cfg = repository.config.value

        btnModeDoaTab?.visibility = View.GONE
        btnModeScanLogTab?.visibility = View.GONE
        btnModeSalesTab?.visibility = if (cfg.enableSalesNotifModule) View.VISIBLE else View.GONE
        btnModeStikerTab?.visibility = if (cfg.enableStickerModule) View.VISIBLE else View.GONE
        btnModeWatermarkTab?.visibility = if (cfg.enableWatermarkModule) View.VISIBLE else View.GONE
        btnModeAutoTimeTab?.visibility = if (cfg.enableAutoTimeModule) View.VISIBLE else View.GONE
        btnModeQuranKhatamTab?.visibility = if (cfg.enableQuranKhatamModule) View.VISIBLE else View.GONE
        btnModeLiveEventTab?.visibility = if (cfg.enableLiveEventBrokerModule) View.VISIBLE else View.GONE

        btnModeSalesTab?.apply {
            val active = !isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.BUYER_SALES
            setTextColor(if (active) Color.WHITE else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (active) Color.parseColor("#EA580C") else Color.parseColor("#1E293B"))
            }
        }

        btnModeStikerTab?.apply {
            val active = !isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.STICKER_TEXT
            setTextColor(if (active) Color.WHITE else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (active) Color.parseColor("#4F46E5") else Color.parseColor("#1E293B"))
            }
        }

        btnModeWatermarkTab?.apply {
            val active = !isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.WATERMARK_TEXT
            setTextColor(if (active) Color.BLACK else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (active) Color.parseColor("#00E5FF") else Color.parseColor("#1E293B"))
            }
        }

        btnModeAutoTimeTab?.apply {
            val active = !isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.AUTO_TIME
            setTextColor(if (active) Color.BLACK else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (active) Color.parseColor("#10B981") else Color.parseColor("#1E293B"))
            }
        }

        btnModeQuranKhatamTab?.apply {
            val active = !isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.QURAN_KHATAM
            setTextColor(if (active) Color.BLACK else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (active) Color.parseColor("#EAB308") else Color.parseColor("#1E293B"))
            }
        }

        btnModeTugOfWarTab?.apply {
            visibility = View.VISIBLE
            val active = !isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.TUG_OF_WAR
            setTextColor(if (active) Color.BLACK else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (active) Color.parseColor("#06B6D4") else Color.parseColor("#1E293B"))
            }
        }

        btnModeLiveEventTab?.apply {
            val active = isShowingLiveEvent
            setTextColor(if (active) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (active) Color.parseColor("#6366F1") else Color.parseColor("#1E293B"))
                if (active) setStroke(dp(1), Color.WHITE)
            }
        }

        containerModeDoa?.visibility = View.GONE
        containerModeScanLog?.visibility = View.GONE
        containerModeSales?.visibility = if (!isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.BUYER_SALES && cfg.enableSalesNotifModule) View.VISIBLE else View.GONE
        containerModeStiker?.visibility = if (!isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.STICKER_TEXT && cfg.enableStickerModule) View.VISIBLE else View.GONE
        containerModeWatermark?.visibility = if (!isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.WATERMARK_TEXT && cfg.enableWatermarkModule) View.VISIBLE else View.GONE
        containerModeAutoTime?.visibility = if (!isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.AUTO_TIME && cfg.enableAutoTimeModule) View.VISIBLE else View.GONE
        containerModeQuranKhatam?.visibility = if (!isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.QURAN_KHATAM && cfg.enableQuranKhatamModule) View.VISIBLE else View.GONE
        containerModeTugOfWar?.visibility = if (!isShowingLiveEvent && currentBannerMode == com.tajasb.livecomment.BannerMode.TUG_OF_WAR) View.VISIBLE else View.GONE
        containerModeLiveEventBroker?.visibility = if (isShowingLiveEvent && cfg.enableLiveEventBrokerModule) View.VISIBLE else View.GONE

        // Style Stiker Display Style & Running Text
        val curStikerStyle = stikerManager.displayStyle.value
        stikerDisplayStyleChipsList.forEach { (styleKey, chipTv) ->
            val isSel = styleKey.equals(curStikerStyle, ignoreCase = true)
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#A5B4FC") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#475569"))
                }
            }
        }

        btnToggleStikerRunningText?.apply {
            val isRun = stikerManager.isRunningText.value
            text = if (isRun) "Running Teks: ON" else "Running Teks: OFF"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isRun) Color.parseColor("#0284C7") else Color.parseColor("#334155"))
            }
        }

        val curStikerRunSpd = stikerManager.runningTextSpeed.value
        stikerRunningSpeedChipsList.forEach { (spdVal, chipTv) ->
            val isSel = Math.abs(spdVal - curStikerRunSpd) < 0.08f
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#38BDF8") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#334155"))
                }
            }
        }

        val curStikerRunDir = stikerManager.runningTextDirection.value
        stikerRunningDirChipsList.forEach { (dirKey, chipTv) ->
            val isSel = dirKey.equals(curStikerRunDir, ignoreCase = true)
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#38BDF8") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#334155"))
                }
            }
        }

        // Style Watermark Controls
        btnToggleWatermarkText?.apply {
            val isWmOn = watermarkManager.isEnabled.value
            text = if (isWmOn) "Watermark: OFF" else "Watermark: ON"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isWmOn) Color.parseColor("#DC2626") else Color.parseColor("#0284C7"))
            }
        }

        val curWmStyle = watermarkManager.displayStyle.value
        watermarkDisplayStyleChipsList.forEach { (styleKey, chipTv) ->
            val isSel = styleKey.equals(curWmStyle, ignoreCase = true)
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#475569"))
                }
            }
        }

        btnToggleWatermarkRunningText?.apply {
            val isRun = watermarkManager.isRunningText.value
            text = if (isRun) "Running Teks: ON" else "Running Teks: OFF"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isRun) Color.parseColor("#059669") else Color.parseColor("#334155"))
            }
        }

        val curWmRunSpd = watermarkManager.runningTextSpeed.value
        watermarkRunningSpeedChipsList.forEach { (spdVal, chipTv) ->
            val isSel = Math.abs(spdVal - curWmRunSpd) < 0.08f
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#10B981") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#334155"))
                }
            }
        }

        val curWmRunDir = watermarkManager.runningTextDirection.value
        watermarkRunningDirChipsList.forEach { (dirKey, chipTv) ->
            val isSel = dirKey.equals(curWmRunDir, ignoreCase = true)
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#10B981") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#334155"))
                }
            }
        }

        val curWmBoxCol = watermarkManager.boxColorHex.value
        watermarkBoxColorChipsList.forEach { (hex, chipTv) ->
            val isSel = hex.equals(curWmBoxCol, ignoreCase = true)
            chipTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor(hex))
                setStroke(dp(if (isSel) 2 else 1), if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
            }
        }

        val curWmTextCol = watermarkManager.textColorHex.value
        watermarkTextColorChipsList.forEach { (hex, chipTv) ->
            val isSel = hex.equals(curWmTextCol, ignoreCase = true)
            chipTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor(hex))
                setStroke(dp(if (isSel) 2 else 1), if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
            }
        }

        val curWmScale = watermarkManager.sizeScale.value
        watermarkScaleChipsList.forEach { (sVal, chipTv) ->
            val isSel = Math.abs(curWmScale - sVal) < 0.05f
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#334155"))
                }
            }
        }

        val curWmAlpha = watermarkManager.boxAlpha.value
        watermarkBoxAlphaText?.text = "Transparansi Watermark: ${(curWmAlpha * 100).toInt()}%"
        watermarkAlphaChipsList.forEach { (aVal, chipTv) ->
            val isSel = Math.abs(aVal - curWmAlpha) < 0.04f
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#334155"))
                }
            }
        }

        watermarkOutlineInfoText?.text = "Tebal: ${watermarkManager.outlineWidth.value}dp | Warna: ${watermarkManager.outlineColorHex.value}"
        val curWmOutlineW = watermarkManager.outlineWidth.value
        watermarkOutlineWidthChipsList.forEach { (wVal, chipTv) ->
            val isSel = wVal == curWmOutlineW
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#0F172A"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#1E293B"))
                }
            }
        }

        val curWmOutlineCol = watermarkManager.outlineColorHex.value
        watermarkOutlineColorChipsList.forEach { (hex, chipTv) ->
            val isSel = hex.equals(curWmOutlineCol, ignoreCase = true)
            chipTv.apply {
                setTextColor(if (hex == "#FFFFFF" || hex == "#FDE047" || hex == "#00E5FF") Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor(hex))
                    setStroke(dp(if (isSel) 2 else 1), if (isSel) Color.WHITE else Color.parseColor("#475569"))
                }
            }
        }

        btnToggleWatermarkRandomMove?.apply {
            val isRand = watermarkManager.isRandomMoveEnabled.value
            text = if (isRand) "Acak (5s): ON" else "Acak (5s): OFF"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isRand) Color.parseColor("#0284C7") else Color.parseColor("#334155"))
            }
        }

        btnToggleStikerText?.apply {
            val isStikerOn = stikerManager.isEnabled.value
            text = if (isStikerOn) "Stiker: OFF" else "Stiker: ON"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isStikerOn) Color.parseColor("#DC2626") else Color.parseColor("#4F46E5"))
            }
        }
        btnStikerRotText?.text = "${stikerManager.rotation.value}°"

        btnStikerMirrorText?.apply {
            val isM = stikerManager.isMirrored.value
            text = if (isM) "Mir: ON" else "Mir: OFF"
            setTextColor(if (isM) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isM) Color.parseColor("#818CF8") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (isM) Color.WHITE else Color.parseColor("#334155"))
            }
        }

        // Style stiker box colors
        val currentBoxColor = stikerManager.boxColorHex.value
        stikerBoxColorChipsList.forEach { (hex, chipTv) ->
            val isSelected = hex.equals(currentBoxColor, ignoreCase = true)
            chipTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor(hex))
                setStroke(dp(if (isSelected) 2 else 1), if (isSelected) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
            }
        }

        // Style stiker text colors
        val currentTextColor = stikerManager.textColorHex.value
        stikerTextColorChipsList.forEach { (hex, chipTv) ->
            val isSelected = hex.equals(currentTextColor, ignoreCase = true)
            chipTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor(hex))
                setStroke(dp(if (isSelected) 2 else 1), if (isSelected) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
            }
        }

        // Style stiker scale chips
        val currentScale = stikerManager.sizeScale.value
        stikerScaleChipsList.forEach { (scaleVal, chipTv) ->
            val isSelected = Math.abs(currentScale - scaleVal) < 0.05f
            chipTv.apply {
                setTextColor(if (isSelected) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSelected) Color.parseColor("#818CF8") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSelected) Color.parseColor("#A5B4FC") else Color.parseColor("#334155"))
                }
            }
        }

        // Style stiker alpha
        val curStikerAlpha = stikerManager.boxAlpha.value
        stikerBoxAlphaText?.text = "Transparansi Blok: ${(curStikerAlpha * 100).toInt()}%"
        stikerAlphaChipsList.forEach { (alphaVal, chipTv) ->
            val isMatched = Math.abs(alphaVal - curStikerAlpha) < 0.04f
            chipTv.apply {
                setTextColor(if (isMatched) Color.BLACK else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isMatched) Typeface.BOLD else Typeface.NORMAL)
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
            }
        }

        // Style stiker random move button
        btnToggleStikerRandomMove?.apply {
            val isRand = stikerManager.isRandomMoveEnabled.value
            text = if (isRand) "Acak (5s): ON" else "Acak (5s): OFF"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isRand) Color.parseColor("#2563EB") else Color.parseColor("#334155"))
            }
        }

        stikerLivePreviewView?.invalidate()
        watermarkLivePreviewView?.invalidate()

        // --- AUTO TIME CAPSULE ("WAKTU OTOMATIS") UI ---
        val isAutoTimeOn = autoTimeManager.isEnabled.value
        val isSecOn = autoTimeManager.showSeconds.value
        btnToggleAutoTimeText?.apply {
            text = if (isAutoTimeOn) "Waktu: OFF" else "Waktu: ON"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isAutoTimeOn) Color.parseColor("#DC2626") else Color.parseColor("#059669"))
            }
        }
        btnToggleAutoTimeSecondsText?.apply {
            text = if (isSecOn) "Format: Detik ON" else "Format: Detik OFF"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isSecOn) Color.parseColor("#0284C7") else Color.parseColor("#334155"))
            }
        }

        val curAtCapHex = autoTimeManager.capsuleBgColorHex.value
        autoTimeCapsuleColorChipsList.forEach { (hex, chipTv) ->
            val isSel = hex.equals(curAtCapHex, ignoreCase = true)
            chipTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor(hex))
                setStroke(dp(if (isSel) 2 else 1), if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
            }
        }

        val curAtAlpha = autoTimeManager.capsuleAlpha.value
        autoTimeAlphaInfoText?.text = "👻 Transparansi Kapsul: ${(curAtAlpha * 100).toInt()}%"
        autoTimeAlphaChipsList.forEach { (aVal, chipTv) ->
            val isSel = Math.abs(aVal - curAtAlpha) < 0.04f
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#10B981") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#334155"))
                }
            }
        }

        autoTimeCapsuleInfoText?.text = "Sudut: ${autoTimeManager.capsuleCornerRadius.value}dp | Lebar Samping: ${autoTimeManager.capsulePaddingH.value}dp"

        val curAtOutW = autoTimeManager.capsuleOutlineWidth.value
        val curAtOutHex = autoTimeManager.capsuleOutlineColorHex.value
        autoTimeOutlineInfoText?.text = "Garis Tepi (Outline): ${curAtOutW}dp | Warna: $curAtOutHex"
        autoTimeOutlineWidthChipsList.forEach { (wVal, chipTv) ->
            val isSel = (wVal == curAtOutW)
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.parseColor("#94A3B8"))
                setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#10B981") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#334155"))
                }
            }
        }
        autoTimeOutlineColorChipsList.forEach { (hex, chipTv) ->
            val isSel = hex.equals(curAtOutHex, ignoreCase = true)
            chipTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor(hex))
                setStroke(dp(if (isSel) 2 else 1), if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
            }
        }

        val curAtDateBg = autoTimeManager.dateBoxBgColorHex.value
        autoTimeDateBoxBgChipsList.forEach { (hex, chipTv) ->
            val isSel = hex.equals(curAtDateBg, ignoreCase = true)
            chipTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor(hex))
                setStroke(dp(if (isSel) 2 else 1), if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
            }
        }

        val curAtDateTxt = autoTimeManager.dateBoxTextColorHex.value
        autoTimeDateTextChipsList.forEach { (hex, chipTv) ->
            val isSel = hex.equals(curAtDateTxt, ignoreCase = true)
            chipTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor(hex))
                setStroke(dp(if (isSel) 2 else 1), if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
            }
        }

        val curAtClockTxt = autoTimeManager.timeTextColorHex.value
        autoTimeClockTextChipsList.forEach { (hex, chipTv) ->
            val isSel = hex.equals(curAtClockTxt, ignoreCase = true)
            chipTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor(hex))
                setStroke(dp(if (isSel) 2 else 1), if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#475569"))
            }
        }

        val curAtScale = autoTimeManager.sizeScale.value
        autoTimeScaleChipsList.forEach { (sVal, chipTv) ->
            val isSel = Math.abs(curAtScale - sVal) < 0.05f
            chipTv.apply {
                setTextColor(if (isSel) Color.BLACK else Color.WHITE)
                setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSel) Color.parseColor("#38BDF8") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSel) Color.WHITE else Color.parseColor("#334155"))
                }
            }
        }

        val atPx = (autoTimeManager.posXpct.value * 100).toInt()
        val atPy = (autoTimeManager.posYpct.value * 100).toInt()
        autoTimePosInfoText?.text = "Posisi Kapsul: X=$atPx% • Y=$atPy%"

        val atLocked = autoTimeManager.isPositionLocked.value
        btnAutoTimeLockText?.apply {
            text = if (atLocked) "Buka Kunci" else "Kunci Posisi"
            setTextColor(if (atLocked) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (atLocked) Color.parseColor("#00E5FF") else Color.parseColor("#10B981"))
            }
        }
        btnAutoTimeRotText?.text = "${autoTimeManager.rotation.value}°"
        btnAutoTimeMirrorText?.apply {
            val isM = autoTimeManager.isMirrored.value
            text = if (isM) "Mir: ON" else "Mir: OFF"
            setTextColor(if (isM) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isM) Color.parseColor("#10B981") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (isM) Color.WHITE else Color.parseColor("#334155"))
            }
        }
        autoTimeLivePreviewView?.invalidate()

        // --- SALES UI ---
        val isEnabled = notifManager.isEnabled.value
        btnBuyerNotifRotText?.text = "${notifManager.rotation.value}°"

        btnBuyerMirrorText?.apply {
            val isM = notifManager.isMirrored.value
            text = if (isM) "Mir: ON" else "Mir: OFF"
            setTextColor(if (isM) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isM) Color.parseColor("#F97316") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (isM) Color.WHITE else Color.parseColor("#334155"))
            }
        }
        val durMs = notifManager.displayDurationMs.value
        val minS = notifManager.minIntervalSeconds.value
        val maxS = notifManager.maxIntervalSeconds.value
        val totalCount = notifManager.totalTriggeredCount.value

        btnToggleBuyerNotifText?.apply {
            text = if (isEnabled) "HENTIKAN" else "MULAI"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isEnabled) Color.parseColor("#DC2626") else Color.parseColor("#EA580C"))
            }
        }

        buyerNotifStatusBadge?.apply {
            text = if (isEnabled) "AKTIF ${minS}-${maxS}s" else "STANDBY"
            setTextColor(if (isEnabled) Color.parseColor("#10B981") else Color.GRAY)
        }

        val curFormat = notifManager.salesFormat.value
        val cMin = notifManager.countMin.value
        val cMax = notifManager.countMax.value
        val pMin = notifManager.productMin.value
        val pMax = notifManager.productMax.value

        val prodNoLabel = if (pMin == pMax) "No. $pMin" else "No. $pMin-$pMax Acak"
        val rangeLabel = "$cMin-$cMax Orang Acak"

        buyerCountRangeStatusText?.text = "Acak: $cMin - $cMax Org"
        buyerProductRangeStatusText?.text = if (pMin == pMax) "No. $pMin Tetap" else "Acak: No. $pMin - $pMax"

        if (etBuyerCountMin?.hasFocus() == false) {
            val curText = etBuyerCountMin?.text?.toString() ?: ""
            if (curText != cMin.toString()) {
                etBuyerCountMin?.setText(cMin.toString())
            }
        }
        if (etBuyerCountMax?.hasFocus() == false) {
            val curText = etBuyerCountMax?.text?.toString() ?: ""
            if (curText != cMax.toString()) {
                etBuyerCountMax?.setText(cMax.toString())
            }
        }

        if (etBuyerProdMin?.hasFocus() == false) {
            val curText = etBuyerProdMin?.text?.toString() ?: ""
            if (curText != pMin.toString()) {
                etBuyerProdMin?.setText(pMin.toString())
            }
        }
        if (etBuyerProdMax?.hasFocus() == false) {
            val curText = etBuyerProdMax?.text?.toString() ?: ""
            if (curText != pMax.toString()) {
                etBuyerProdMax?.setText(pMax.toString())
            }
        }

        val formatLabel = when (curFormat) {
            com.tajasb.notif.BuyerNotificationManager.FORMAT_BUYING -> "r***8 sedang membeli"
            com.tajasb.notif.BuyerNotificationManager.FORMAT_VIEWING -> "X org melihat produk $prodNoLabel"
            com.tajasb.notif.BuyerNotificationManager.FORMAT_BOUGHT -> "X org telah membeli dari LIVE"
            else -> "Campuran 3 Format Otomatis"
        }

        buyerNotifCountText?.text = "Total: $totalCount notif"
        buyerNotifDurationText?.text = "Durasi Tampil: ${"%.2f".format(durMs / 1000.0)}s"
        buyerNotifIntervalText?.text = "Jeda: Acak ${minS}s - ${maxS}s"
        buyerNotifActionTextLabel?.text = "Format: $formatLabel\nEtalase: $prodNoLabel | Jumlah: $rangeLabel"

        val curCornerRad = notifManager.capsuleCornerRadius.value
        val curPadH = notifManager.capsulePaddingH.value
        val curPadV = notifManager.capsulePaddingV.value
        buyerNotifCapsuleInfoText?.text = "Sudut: ${curCornerRad}dp | Pad Samping: ${curPadH}dp | Pad Atas/Bwh: ${curPadV}dp"

        val curRange = notifManager.countRange.value
        val curProdNo = notifManager.productNoMode.value
        FloatingUiWidgets.styleBuyerNotifChips(
            service = this,
            dp = ::dp,
            durMs = durMs,
            minS = minS,
            maxS = maxS,
            curFormat = curFormat,
            curRange = curRange,
            curProdNo = curProdNo
        )

        val posX = notifManager.posX.value
        val posY = notifManager.posY.value
        buyerNotifPosText?.text = "Posisi: X=$posX Y=$posY"

        btnBuyerNotifLockText?.apply {
            text = if (isLocked) "Buka Kunci" else "Kunci Posisi"
            setTextColor(if (isLocked) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isLocked) Color.parseColor("#00E5FF") else Color.parseColor("#10B981"))
            }
        }

        updateFixedBottomDockUi()
        updateBubbleIconsStatus()
        updateScanRecorderUi()
    }
}
