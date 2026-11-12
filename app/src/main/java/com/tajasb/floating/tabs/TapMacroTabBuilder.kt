package com.tajasb.floating.tabs

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tajasb.FloatingControlService
import com.tajasb.floating.components.FloatingUiWidgets

/**
 * TapMacroTabBuilder:
 * Modular builder for Tab 5 (👆 Auto-Tap & Multi-Choreography Macro Engine):
 * - Top telemetry & sticky execution bar
 * - Segmented choreography loop mode (Urut 1->N->1, Acak Koreo, 1 Koreo)
 * - 3-mode Mark position lock & hide (Geser, Kunci Tembus, Kunci Sembunyi)
 * - Reticle size & delay engine (fixed / random min-max + presets)
 * - Choreography group manager (Save, Rename, Update, Delete, Repeat cycles, Reorder)
 * - Multi-Mark sequence list (+Klik, +Love, +Scroll, +Back, +Ketik, per-mark lock/delay/test)
 * - Auto-Ketik sentence list manager with per-choreography turn indicators
 */
object TapMacroTabBuilder {

    fun populateTapMacroTab(
        service: FloatingControlService,
        tabContentHolder: LinearLayout,
        dp: (Int) -> Int
    ) = with(service) {
        // ---------------------------------------------------------
        // 6. TAB 5: 👆 AUTO-TAP CONTAINER (STANDARDIZED PRO UI)
        // ---------------------------------------------------------
        val tabTapContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerTabTap = tabTapContainer

        val autoTap = com.tajasb.autotap.AutoTapManager.getInstance(this)

        val tapCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#0B0F17"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        // 1. Top Telemetry & Status Bar (Standard Size)
        val tapStatusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(7).toFloat()
                setColor(Color.parseColor("#131B2E"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val tBadge = TextView(this).apply {
            text = "● SIAP (STANDBY)"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tapStatusBadge = tBadge
        tapStatusRow.addView(tBadge)

        val posTv = TextView(this).apply {
            text = "4/4 Mark"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(dp(4), 0, dp(6), 0)
        }
        tapPosText = posTv
        tapStatusRow.addView(posTv)

        val tCount = TextView(this).apply {
            text = "${autoTap.totalTaps.value} aksi"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.END
        }
        tapCountText = tCount
        tapStatusRow.addView(tCount)

        val btnResetTap = TextView(this).apply {
            text = "RESET"
            textSize = 8.8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(6)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                autoTap.resetCount()
                updateAutoTapUi()
                onRefreshTapMarksUi?.invoke()
                Toast.makeText(
                    applicationContext,
                    "🔄 Penghitung & seluruh giliran koreografi/mark/pesan direset ke awal (#1)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        tapStatusRow.addView(btnResetTap)
        // tapStatusRow and primaryActionRow will be attached to stickyTapActionBar below so they never scroll away!

        // 2. Segmented Choreography Loop Switcher: URUT (1->N->1) vs ACAK KOREO vs HANYA 1 KOREO (3 Equal Width - Ratakan)
        val modeSwitchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#131B2E"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val btnModeMultiMark = TextView(this).apply {
            text = "🔁 URUT (1➔N➔1)"
            textSize = 8.8f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(7), dp(4), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                autoTap.setChainAllChoreographies(true)
                autoTap.setRandomChoreographyOrder(false)
                Toast.makeText(
                    applicationContext,
                    "🔁 Mode Urut Koreografi: Menjalankan Koreo #1 ➔ #2 ➔ #3 ➔ #4 lalu balik ke #1!",
                    Toast.LENGTH_SHORT
                ).show()
                onRefreshTapMarksUi?.invoke()
                updateAutoTapUi()
            }
        }
        modeSwitchRow.addView(btnModeMultiMark)

        val btnModeRandomChoreo = TextView(this).apply {
            text = "🔀 ACAK KOREO"
            textSize = 8.8f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(7), dp(4), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                autoTap.setChainAllChoreographies(true)
                autoTap.setRandomChoreographyOrder(true)
                Toast.makeText(
                    applicationContext,
                    "🔀 Mode Acak Koreografi: Menjalankan grup koreografi aktif secara acak (Random)!",
                    Toast.LENGTH_SHORT
                ).show()
                onRefreshTapMarksUi?.invoke()
                updateAutoTapUi()
            }
        }
        modeSwitchRow.addView(btnModeRandomChoreo)

        val btnModeSingleTap = TextView(this).apply {
            text = "📌 1 KOREO"
            textSize = 8.8f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(7), dp(4), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                autoTap.setChainAllChoreographies(false)
                Toast.makeText(
                    applicationContext,
                    "📌 Mode 1 Koreografi: Hanya mengulang Koreografi yang sedang dipilih.",
                    Toast.LENGTH_SHORT
                ).show()
                onRefreshTapMarksUi?.invoke()
                updateAutoTapUi()
            }
        }
        modeSwitchRow.addView(btnModeSingleTap)
        tapCard.addView(modeSwitchRow)

        // 3. Primary Control Row: [ ▶ JALANKAN MAKRO ] + [ 🎯 TARGET: ON/OFF ] (Equal Width - Ratakan)
        val primaryActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val btnToggleTap = TextView(this).apply {
            text = "▶ JALANKAN MAKRO"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(3)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(7).toFloat()
                setColor(Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                if (com.tajasb.VcamAccessibilityService.instance == null) {
                    Toast.makeText(applicationContext, "Aktifkan Layanan Aksesibilitas 'Tajasb' terlebih dahulu!", Toast.LENGTH_LONG).show()
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {}
                    return@setOnClickListener
                }
                // Sync any visible mark positions before starting so exact screen coordinates are locked in
                com.tajasb.floating.FloatingBubbleWindow.syncVisibleMarkCoordinates(this@with)
                if (!autoTap.isTapping.value && !autoTap.isPointerVisible.value && !autoTap.isMarksHiddenFromView()) {
                    autoTap.setPointerVisible(true)
                }
                val isResuming = !autoTap.isTapping.value && autoTap.hasSavedTurnProgress()
                val turnSummary = autoTap.getResumeSummaryShort()
                val started = autoTap.toggleTapping()
                updateAutoTapUi()
                onRefreshTapMarksUi?.invoke()
                if (started) {
                    showBubbleOnly()
                    val modeInfo = if (autoTap.isMarksHiddenFromView()) " (Mark Tersembunyi 🙈)" else ""
                    val msg = if (isResuming) {
                        "▶️ Melanjutkan: $turnSummary$modeInfo! Tekan [👆] di bar mini untuk jeda."
                    } else {
                        "▶️ Makro dimulai$modeInfo! Tekan [👆] di bar mini untuk jeda."
                    }
                    Toast.makeText(
                        applicationContext,
                        msg,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        applicationContext,
                        "⏸️ Makro dijeda. Giliran disimpan: ${autoTap.getResumeSummaryShort()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        btnToggleTapText = btnToggleTap
        primaryActionRow.addView(btnToggleTap)

        val btnToggleMarker = TextView(this).apply {
            text = "🎯 TARGET: ON"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(3)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(7).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                if (autoTap.isPointerVisible.value) {
                    com.tajasb.floating.FloatingBubbleWindow.syncVisibleMarkCoordinates(this@with)
                } else if (autoTap.markDisplayMode.value == com.tajasb.autotap.MarkDisplayMode.LOCKED_HIDDEN) {
                    autoTap.setMarkDisplayMode(com.tajasb.autotap.MarkDisplayMode.EDITABLE)
                }
                autoTap.togglePointerVisible()
                onRefreshTapMarksUi?.invoke()
            }
        }
        primaryActionRow.addView(btnToggleMarker)
        if (stickyTapActionBar != null) {
            stickyTapActionBar?.removeAllViews()
            stickyTapActionBar?.addView(tapStatusRow)
            stickyTapActionBar?.addView(primaryActionRow)
        } else {
            tapCard.addView(tapStatusRow)
            tapCard.addView(primaryActionRow)
        }

        // 4. Lock & Hide Mark Control Bar: [ 👁️ GESER ] [ 🔒 KUNCI TEMBUS ] [ 🙈 KUNCI & HILANG ]
        val lockModeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#131B2E"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(7), dp(6), dp(7), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val tvLockModeHeader = TextView(this).apply {
            text = "🛡️ MODE POSISI MARK (AGAR TIDAK MENGHALANGI LAYAR)"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, dp(4))
        }
        lockModeCard.addView(tvLockModeHeader)

        val lockModeBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val lockModeButtons = mutableListOf<Pair<com.tajasb.autotap.MarkDisplayMode, TextView>>()
        fun createLockModeBtn(mode: com.tajasb.autotap.MarkDisplayMode, labelText: String): TextView {
            return TextView(this).apply {
                text = labelText
                textSize = 9.2f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(7), dp(4), dp(7))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
                setOnClickListener {
                    // Always save exact screen position before locking or hiding!
                    com.tajasb.floating.FloatingBubbleWindow.syncVisibleMarkCoordinates(this@with)
                    if (!autoTap.isPointerVisible.value && mode != com.tajasb.autotap.MarkDisplayMode.LOCKED_HIDDEN) {
                        autoTap.setPointerVisible(true)
                    }
                    autoTap.setMarkDisplayMode(mode)
                    val msg = when (mode) {
                        com.tajasb.autotap.MarkDisplayMode.EDITABLE -> "👁️ Mode Geser Aktif: Mark bisa digeser bebas ke seluruh layar."
                        com.tajasb.autotap.MarkDisplayMode.LOCKED_GHOST -> "🔒 Mark Dikunci & Tembus Sentuh: Posisi aman & tidak menghalangi klik!"
                        com.tajasb.autotap.MarkDisplayMode.LOCKED_HIDDEN -> "🙈 Mark Dikunci & Disembunyikan dari layar! Titik klik tetap aktif 100%."
                    }
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                    showCrosshairPointer()
                    onRefreshTapMarksUi?.invoke()
                }
            }.also { lockModeButtons.add(mode to it) }
        }

        lockModeBtnRow.addView(createLockModeBtn(com.tajasb.autotap.MarkDisplayMode.EDITABLE, "👁️ GESER"))
        lockModeBtnRow.addView(createLockModeBtn(com.tajasb.autotap.MarkDisplayMode.LOCKED_GHOST, "🔒 TEMBUS"))
        lockModeBtnRow.addView(createLockModeBtn(com.tajasb.autotap.MarkDisplayMode.LOCKED_HIDDEN, "🙈 SEMBUNYI"))
        lockModeCard.addView(lockModeBtnRow)
        tapCard.addView(lockModeCard)

        // 5. Precision Reticle Size Bar (Standardized Equal-Width Grid - Ratakan)
        val markSizeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#131B2E"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(7), dp(6), dp(7), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val tvMarkSizeTitle = TextView(this).apply {
            text = "📏 UKURAN MARK: ${autoTap.markSizeDp.value}dp"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 0, 0, dp(4))
        }
        markSizeCard.addView(tvMarkSizeTitle)

        val markSizeControlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnMinusMarkSize = TextView(this).apply {
            text = "−4"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                autoTap.changeMarkSize(-4)
                onRefreshTapMarksUi?.invoke()
            }
        }
        markSizeControlsRow.addView(btnMinusMarkSize)

        val markSizePresets = listOf(
            36 to "36dp",
            48 to "48dp",
            60 to "60dp",
            72 to "72dp"
        )
        val markSizeChipsList = mutableListOf<Pair<Int, TextView>>()

        markSizePresets.forEach { (sz, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 9.2f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(6), dp(4), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
                setOnClickListener {
                    autoTap.setMarkSizeDp(sz)
                    onRefreshTapMarksUi?.invoke()
                }
            }
            markSizeChipsList.add(sz to chip)
            markSizeControlsRow.addView(chip)
        }

        val btnPlusMarkSize = TextView(this).apply {
            text = "+4"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                autoTap.changeMarkSize(4)
                onRefreshTapMarksUi?.invoke()
            }
        }
        markSizeControlsRow.addView(btnPlusMarkSize)
        markSizeCard.addView(markSizeControlsRow)
        tapCard.addView(markSizeCard)

        // 6. Real Delay Antar-Mark & Random Interval Engine Card (Standard Size & Equal Width)
        val delayCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#131B2E"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(7), dp(6), dp(7), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val delayHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        val speedLbl = TextView(this).apply {
            text = "⏱️ JEDA: ${autoTap.minDelayMs.value}-${autoTap.maxDelayMs.value}ms"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        delayHeaderRow.addView(speedLbl)
        delayCard.addView(delayHeaderRow)

        val delayAdjustRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnAntiBot = TextView(this).apply {
            text = "🎲 ACAK"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f).apply {
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setOnClickListener {
                val next = !autoTap.antiRobotEnabled.value
                autoTap.setAntiRobot(next)
                Toast.makeText(
                    applicationContext,
                    if (next) "Delay Random (Min-Max) Aktif" else "Delay Tetap (Fixed) Aktif",
                    Toast.LENGTH_SHORT
                ).show()
                updateAutoTapUi()
                onRefreshTapMarksUi?.invoke()
            }
        }
        btnAntiRobotText = btnAntiBot
        delayAdjustRow.addView(btnAntiBot)

        val btnMinus = TextView(this).apply {
            text = "−100ms"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                val next = (autoTap.baseIntervalMs.value - 100L).coerceAtLeast(50L)
                autoTap.setBaseDelay(next)
                updateAutoTapUi()
                onRefreshTapMarksUi?.invoke()
            }
        }
        delayAdjustRow.addView(btnMinus)

        val btnPlus = TextView(this).apply {
            text = "+100ms"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                val next = (autoTap.baseIntervalMs.value + 100L).coerceAtMost(60000L)
                autoTap.setBaseDelay(next)
                updateAutoTapUi()
                onRefreshTapMarksUi?.invoke()
            }
        }
        delayAdjustRow.addView(btnPlus)

        // Custom Min-Max Delay Inline Editor Box
        val customDelayEditorBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0B0F17"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
        }

        val etMinDelay = EditText(this).apply {
            hint = "Min (ms)"
            setText(autoTap.minDelayMs.value.toString())
            textSize = 9.5f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
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
                setPanelFocusable(hasFocus)
            }
        }
        customDelayEditorBox.addView(etMinDelay)

        val lblDash = TextView(this).apply {
            text = "s/d"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.LTGRAY)
            setPadding(dp(2), 0, dp(4), 0)
        }
        customDelayEditorBox.addView(lblDash)

        val etMaxDelay = EditText(this).apply {
            hint = "Max (ms)"
            setText(autoTap.maxDelayMs.value.toString())
            textSize = 9.5f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
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
                setPanelFocusable(hasFocus)
            }
        }
        customDelayEditorBox.addView(etMaxDelay)

        val btnSaveCustomDelay = TextView(this).apply {
            text = "SIMPAN"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                val minVal = etMinDelay.text.toString().trim().toLongOrNull() ?: autoTap.minDelayMs.value
                val maxVal = etMaxDelay.text.toString().trim().toLongOrNull() ?: minVal
                autoTap.setCustomDelayRange(minVal, maxOf(minVal, maxVal))
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etMinDelay.windowToken, 0)
                etMinDelay.clearFocus()
                etMaxDelay.clearFocus()
                setPanelFocusable(false)
                customDelayEditorBox.visibility = View.GONE
                Toast.makeText(applicationContext, "Delay diset: ${autoTap.minDelayMs.value}-${autoTap.maxDelayMs.value}ms", Toast.LENGTH_SHORT).show()
                updateAutoTapUi()
                onRefreshTapMarksUi?.invoke()
            }
        }
        customDelayEditorBox.addView(btnSaveCustomDelay)

        val btnOpenCustomDelay = TextView(this).apply {
            text = "⚙️ SET MS"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                val isVis = customDelayEditorBox.visibility == View.VISIBLE
                if (!isVis) {
                    etMinDelay.setText(autoTap.minDelayMs.value.toString())
                    etMaxDelay.setText(autoTap.maxDelayMs.value.toString())
                    customDelayEditorBox.visibility = View.VISIBLE
                } else {
                    setPanelFocusable(false)
                    customDelayEditorBox.visibility = View.GONE
                }
            }
        }
        delayAdjustRow.addView(btnOpenCustomDelay)
        delayCard.addView(delayAdjustRow)
        delayCard.addView(customDelayEditorBox)

        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
        }

        val speedPresetChips = mutableListOf<Pair<com.tajasb.autotap.TapSpeedPreset, TextView>>()
        fun createSpeedBtn(preset: com.tajasb.autotap.TapSpeedPreset, label: String): TextView {
            return TextView(this).apply {
                text = label
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(6), dp(4), dp(6))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
                layoutParams = lp
                setOnClickListener {
                    autoTap.setSpeedPreset(preset)
                    updateAutoTapUi()
                    onRefreshTapMarksUi?.invoke()
                }
            }.also { speedPresetChips.add(preset to it) }
        }

        speedRow.addView(createSpeedBtn(com.tajasb.autotap.TapSpeedPreset.TURBO_FAST, "100-240ms"))
        speedRow.addView(createSpeedBtn(com.tajasb.autotap.TapSpeedPreset.FAST_RANDOM, "350-750ms"))
        speedRow.addView(createSpeedBtn(com.tajasb.autotap.TapSpeedPreset.BALANCED_RANDOM, "0.9-1.8d"))
        speedRow.addView(createSpeedBtn(com.tajasb.autotap.TapSpeedPreset.HUMAN_NATURAL, "2.2-4.5d"))
        delayCard.addView(speedRow)
        tapCard.addView(delayCard)

        // ---------------------------------------------------------
        // 7. MULTI-MARK SEQUENCE & CHOREOGRAPHY BUILDER SECTION
        // ---------------------------------------------------------
        val multiMarkSectionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        // 7A. GRUP KOREOGRAFI MARK CARD (Save, Load, Update, Delete Choreographies)
        val choreoGroupCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#131B2E"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setPadding(dp(7), dp(6), dp(7), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val tvChoreoHeader = TextView(this).apply {
            text = "📂 GRUP KOREOGRAFI MARK (SIMPAN BANYAK FORMASI)"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, dp(4))
        }
        choreoGroupCard.addView(tvChoreoHeader)

        // 4 Equal-Width Action Buttons: [ ➕ BARU ] [ ✏️ NAMA ] [ 💾 UPDATE ] [ 🗑️ HAPUS ]
        val choreoActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val inlineSaveGroupBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0B0F17"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
        }

        val etNewGroupName = EditText(this).apply {
            hint = "Nama koreografi baru..."
            textSize = 9.5f
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            setPadding(dp(6), dp(5), dp(6), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
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
                setPanelFocusable(hasFocus)
            }
        }
        inlineSaveGroupBox.addView(etNewGroupName)

        val btnConfirmInlineSaveGroup = TextView(this).apply {
            text = "💾 SIMPAN"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                com.tajasb.floating.FloatingBubbleWindow.syncVisibleMarkCoordinates(this@with)
                val name = etNewGroupName.text.toString().trim().ifEmpty {
                    "Koreografi ${autoTap.choreographyGroups.value.size + 1}"
                }
                val saved = autoTap.saveNewChoreographyGroup(name)
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etNewGroupName.windowToken, 0)
                etNewGroupName.clearFocus()
                setPanelFocusable(false)
                inlineSaveGroupBox.visibility = View.GONE
                Toast.makeText(applicationContext, "✅ Grup '${saved.name}' disimpan!", Toast.LENGTH_SHORT).show()
                onRefreshTapMarksUi?.invoke()
            }
        }
        inlineSaveGroupBox.addView(btnConfirmInlineSaveGroup)

        val btnDialogSaveGroup = TextView(this).apply {
            text = "⌨️"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                com.tajasb.floating.FloatingBubbleWindow.syncVisibleMarkCoordinates(this@with)
                FloatingUiWidgets.showSaveChoreographyDialog(this@with) {
                    inlineSaveGroupBox.visibility = View.GONE
                    onRefreshTapMarksUi?.invoke()
                }
            }
        }
        inlineSaveGroupBox.addView(btnDialogSaveGroup)

        // Inline Rename Choreography Box (for renaming active or any selected choreography)
        var renamingTargetGroupId: String? = null
        val inlineRenameGroupBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0B0F17"))
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
        }

        val etRenameGroupName = EditText(this).apply {
            hint = "Nama koreografi baru..."
            textSize = 9.5f
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            setPadding(dp(6), dp(5), dp(6), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
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
                setPanelFocusable(hasFocus)
            }
        }
        inlineRenameGroupBox.addView(etRenameGroupName)

        val btnConfirmInlineRenameGroup = TextView(this).apply {
            text = "💾 UBAH"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#F59E0B"))
            }
            setOnClickListener {
                val targetId = renamingTargetGroupId ?: autoTap.activeGroupId.value
                val newName = etRenameGroupName.text.toString().trim()
                if (targetId != null && newName.isNotEmpty()) {
                    val ok = autoTap.renameChoreographyGroup(targetId, newName)
                    if (ok) {
                        Toast.makeText(applicationContext, "✏️ Nama koreografi diubah menjadi '$newName'!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "Ketik nama koreografi terlebih dahulu!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etRenameGroupName.windowToken, 0)
                etRenameGroupName.clearFocus()
                setPanelFocusable(false)
                inlineRenameGroupBox.visibility = View.GONE
                onRefreshTapMarksUi?.invoke()
            }
        }
        inlineRenameGroupBox.addView(btnConfirmInlineRenameGroup)

        val btnDialogRenameGroup = TextView(this).apply {
            text = "⌨️"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(6), dp(7), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
            setOnClickListener {
                val targetId = renamingTargetGroupId ?: autoTap.activeGroupId.value ?: return@setOnClickListener
                val curName = etRenameGroupName.text.toString().trim().ifEmpty {
                    autoTap.choreographyGroups.value.firstOrNull { it.id == targetId }?.name ?: "Koreografi"
                }
                FloatingUiWidgets.showRenameChoreographyDialog(this@with, targetId, curName) {
                    inlineRenameGroupBox.visibility = View.GONE
                    onRefreshTapMarksUi?.invoke()
                }
            }
        }
        inlineRenameGroupBox.addView(btnDialogRenameGroup)

        val btnCancelInlineRenameGroup = TextView(this).apply {
            text = "✕"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCA5A5"))
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(6), dp(7), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#450A0A"))
            }
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etRenameGroupName.windowToken, 0)
                etRenameGroupName.clearFocus()
                setPanelFocusable(false)
                inlineRenameGroupBox.visibility = View.GONE
            }
        }
        inlineRenameGroupBox.addView(btnCancelInlineRenameGroup)

        fun openInlineRenameForGroup(groupId: String, currentName: String) {
            renamingTargetGroupId = groupId
            inlineSaveGroupBox.visibility = View.GONE
            etRenameGroupName.setText(currentName)
            etRenameGroupName.setSelection(etRenameGroupName.text.length)
            inlineRenameGroupBox.visibility = View.VISIBLE
            setPanelFocusable(true)
            etRenameGroupName.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etRenameGroupName, InputMethodManager.SHOW_IMPLICIT)
        }

        val btnNewChoreo = TextView(this).apply {
            text = "➕ BARU"
            textSize = 8.8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(7), dp(3), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                com.tajasb.floating.FloatingBubbleWindow.syncVisibleMarkCoordinates(this@with)
                inlineRenameGroupBox.visibility = View.GONE
                val isVis = inlineSaveGroupBox.visibility == View.VISIBLE
                if (!isVis) {
                    etNewGroupName.setText("Koreografi ${autoTap.choreographyGroups.value.size + 1}")
                    inlineSaveGroupBox.visibility = View.VISIBLE
                } else {
                    setPanelFocusable(false)
                    inlineSaveGroupBox.visibility = View.GONE
                }
            }
        }
        choreoActionRow.addView(btnNewChoreo)

        val btnRenameChoreo = TextView(this).apply {
            text = "✏️ NAMA"
            textSize = 8.8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FDE047"))
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(7), dp(3), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#422006"))
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
            setOnClickListener {
                val activeId = autoTap.activeGroupId.value ?: autoTap.choreographyGroups.value.firstOrNull()?.id
                if (activeId == null) {
                    Toast.makeText(applicationContext, "Simpan koreografi terlebih dahulu sebelum ubah nama!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val isVis = inlineRenameGroupBox.visibility == View.VISIBLE && renamingTargetGroupId == activeId
                if (!isVis) {
                    val curName = autoTap.choreographyGroups.value.firstOrNull { it.id == activeId }?.name ?: "Koreografi"
                    openInlineRenameForGroup(activeId, curName)
                } else {
                    setPanelFocusable(false)
                    inlineRenameGroupBox.visibility = View.GONE
                }
            }
        }
        choreoActionRow.addView(btnRenameChoreo)

        val btnUpdateChoreo = TextView(this).apply {
            text = "💾 UPDATE"
            textSize = 8.8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(7), dp(3), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setOnClickListener {
                com.tajasb.floating.FloatingBubbleWindow.syncVisibleMarkCoordinates(this@with)
                val ok = autoTap.updateActiveChoreographyGroup()
                if (ok != null) {
                    Toast.makeText(applicationContext, "💾 Posisi & urutan Mark di '${ok.name}' berhasil diupdate!", Toast.LENGTH_SHORT).show()
                } else {
                    val saved = autoTap.saveNewChoreographyGroup("Koreografi ${autoTap.choreographyGroups.value.size + 1}")
                    Toast.makeText(applicationContext, "✅ Disimpan sebagai '${saved.name}'!", Toast.LENGTH_SHORT).show()
                }
                onRefreshTapMarksUi?.invoke()
            }
        }
        choreoActionRow.addView(btnUpdateChoreo)

        val btnDeleteChoreo = TextView(this).apply {
            text = "🗑️ HAPUS"
            textSize = 8.8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCA5A5"))
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(7), dp(3), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#450A0A"))
                setStroke(dp(1), Color.parseColor("#EF4444"))
            }
            setOnClickListener {
                val activeId = autoTap.activeGroupId.value
                if (activeId == null) {
                    Toast.makeText(applicationContext, "Pilih grup koreografi terlebih dahulu!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (autoTap.choreographyGroups.value.size <= 1) {
                    Toast.makeText(applicationContext, "Minimal harus ada 1 grup koreografi tersimpan!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val oldName = autoTap.choreographyGroups.value.firstOrNull { it.id == activeId }?.name ?: "Grup"
                val deleted = autoTap.deleteChoreographyGroup(activeId)
                if (deleted) {
                    Toast.makeText(applicationContext, "🗑️ Grup '$oldName' dihapus", Toast.LENGTH_SHORT).show()
                    showCrosshairPointer()
                    onRefreshTapMarksUi?.invoke()
                }
            }
        }
        choreoActionRow.addView(btnDeleteChoreo)
        choreoGroupCard.addView(choreoActionRow)
        choreoGroupCard.addView(inlineSaveGroupBox)
        choreoGroupCard.addView(inlineRenameGroupBox)

        // Container for Saved Choreography Groups (Ordered List #1 -> #2 -> #3 -> #4 -> Balik ke #1)
        val choreoGroupsListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
        }
        choreoGroupCard.addView(choreoGroupsListContainer)
        multiMarkSectionContainer.addView(choreoGroupCard)

        // 7B. URUTAN EKSEKUSI MARK CARD (KLIK + LOVE + SCROLL + BACK + KETIK)
        val markSequenceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#131B2E"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(7), dp(6), dp(7), dp(6))
        }

        val markHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val markHeaderTitle = TextView(this).apply {
            text = "🎯 DAFTAR & URUTAN MARK (TERMASUK LOVE ❤️)"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        markHeaderRow.addView(markHeaderTitle)

        val btnResetDefaultMarks = TextView(this).apply {
            text = "🔄 RESET 1-5"
            textSize = 8.8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                autoTap.resetMarksToDefaultTemplate()
                Toast.makeText(applicationContext, "Urutan direset ke Default (1.Klik, 2.Love ❤️, 3.Scroll, 4.Back, 5.Ketik)", Toast.LENGTH_SHORT).show()
                onRefreshTapMarksUi?.invoke()
            }
        }
        markHeaderRow.addView(btnResetDefaultMarks)
        markSequenceCard.addView(markHeaderRow)

        // Add Mark 5 Equal-Width Buttons Row: + KLIK, + ❤️ LOVE, + SCROLL, + BACK, + KETIK (Standard Size - Ratakan)
        val addMarkButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        fun createAddMarkBtn(type: com.tajasb.autotap.TapMarkActionType, labelText: String, strokeHex: String): TextView {
            return TextView(this).apply {
                text = labelText
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(7), dp(3), dp(7))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor(strokeHex))
                }
                setOnClickListener {
                    if (autoTap.markDisplayMode.value == com.tajasb.autotap.MarkDisplayMode.LOCKED_HIDDEN) {
                        autoTap.setMarkDisplayMode(com.tajasb.autotap.MarkDisplayMode.EDITABLE)
                    }
                    val added = autoTap.addMark(type)
                    if (!autoTap.isPointerVisible.value) {
                        autoTap.setPointerVisible(true)
                    }
                    Toast.makeText(applicationContext, "Ditambahkan ke koreografi: ${added.type.shortLabel}", Toast.LENGTH_SHORT).show()
                    onRefreshTapMarksUi?.invoke()
                }
            }
        }

        addMarkButtonsRow.addView(createAddMarkBtn(com.tajasb.autotap.TapMarkActionType.CLICK, "+KLIK", "#00E5FF"))
        addMarkButtonsRow.addView(createAddMarkBtn(com.tajasb.autotap.TapMarkActionType.LOVE, "+❤️LOVE", "#F43F5E"))
        addMarkButtonsRow.addView(createAddMarkBtn(com.tajasb.autotap.TapMarkActionType.SCROLL, "+SCROLL", "#38BDF8"))
        addMarkButtonsRow.addView(createAddMarkBtn(com.tajasb.autotap.TapMarkActionType.BACK, "+BACK", "#F59E0B"))
        addMarkButtonsRow.addView(createAddMarkBtn(com.tajasb.autotap.TapMarkActionType.AUTO_TYPE, "+KETIK", "#10B981"))
        markSequenceCard.addView(addMarkButtonsRow)

        val marksListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        markSequenceCard.addView(marksListContainer)
        multiMarkSectionContainer.addView(markSequenceCard)

        // ---------------------------------------------------------
        // 8. AUTO KETIK & EASY RANDOM TEXT MANAGER SECTION
        // ---------------------------------------------------------
        val autoTypeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#131B2E"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setPadding(dp(7), dp(6), dp(7), dp(6))
        }

        val autoTypeTitleTv = TextView(this).apply {
            text = "⌨️ DAFTAR TEKS AUTO KETIK (${autoTap.autoTypeTexts.value.size} KALIMAT)"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            setPadding(0, 0, 0, dp(4))
        }
        autoTypeCard.addView(autoTypeTitleTv)

        // Row 1 of Auto-Ketik Controls (3 Equal-Width Standard Buttons - Ratakan)
        val autoTypeControlRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnToggleAutoSend = TextView(this).apply {
            text = "KIRIM: ON"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            setOnClickListener {
                val next = autoTap.toggleAutoSendAfterType()
                Toast.makeText(
                    applicationContext,
                    if (next) "Auto-Kirim (Enter/Send) setelah ketik: AKTIF" else "Auto-Kirim: NONAKTIF (Hanya isi teks)",
                    Toast.LENGTH_SHORT
                ).show()
                onRefreshTapMarksUi?.invoke()
            }
        }
        autoTypeControlRow1.addView(btnToggleAutoSend)

        val btnToggleTextMode = TextView(this).apply {
            text = "MODE: ACAK"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setOnClickListener {
                val nextMode = autoTap.toggleAutoTypeSelectionMode()
                Toast.makeText(applicationContext, "Urutan Teks: ${nextMode.label}", Toast.LENGTH_SHORT).show()
                onRefreshTapMarksUi?.invoke()
            }
        }
        autoTypeControlRow1.addView(btnToggleTextMode)

        val btnToggleLivingIcons = TextView(this).apply {
            text = if (autoTap.appendLivingIcons.value) "🎭 2 IKON: ON" else "🎭 2 IKON: OFF"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (autoTap.appendLivingIcons.value) Color.parseColor("#FDE047") else Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(if (autoTap.appendLivingIcons.value) Color.parseColor("#713F12") else Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor(if (autoTap.appendLivingIcons.value) "#EAB308" else "#334155"))
            }
            setOnClickListener {
                val next = autoTap.toggleAppendLivingIcons()
                Toast.makeText(
                    applicationContext,
                    if (next) "🎭 2 Ikon Hidup di Ujung Pesan: AKTIF! (Setiap teks otomatis diberi 2 ikon acak)"
                    else "2 Ikon Hidup: NONAKTIF (Kirim teks polos)",
                    Toast.LENGTH_SHORT
                ).show()
                onRefreshTapMarksUi?.invoke()
            }
        }
        autoTypeControlRow1.addView(btnToggleLivingIcons)
        autoTypeCard.addView(autoTypeControlRow1)

        // Row 2 of Auto-Ketik Controls (3 Equal-Width Standard Buttons - Ratakan)
        val autoTypeControlRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        val btnOpenAutoTypeDialog = TextView(this).apply {
            text = "⌨️ MULTI BARIS"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setOnClickListener {
                FloatingUiWidgets.showAutoTypeTextInputDialog(this@with) {
                    onRefreshTapMarksUi?.invoke()
                }
            }
        }
        autoTypeControlRow2.addView(btnOpenAutoTypeDialog)

        val btnResetDefaultTexts = TextView(this).apply {
            text = "🔄 DEFAULT"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                autoTap.resetDefaultAutoTypeTexts()
                Toast.makeText(applicationContext, "🔄 Teks Auto Ketik dikembalikan ke default", Toast.LENGTH_SHORT).show()
                onRefreshTapMarksUi?.invoke()
            }
        }
        autoTypeControlRow2.addView(btnResetDefaultTexts)

        val btnClearAllTexts = TextView(this).apply {
            text = "🗑️ KOSONGKAN"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCA5A5"))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#450A0A"))
                setStroke(dp(1), Color.parseColor("#EF4444"))
            }
            setOnClickListener {
                autoTap.clearAllAutoTypeTexts()
                Toast.makeText(applicationContext, "🗑️ Semua teks random telah dihapus!", Toast.LENGTH_SHORT).show()
                onRefreshTapMarksUi?.invoke()
            }
        }
        autoTypeControlRow2.addView(btnClearAllTexts)
        autoTypeCard.addView(autoTypeControlRow2)

        // Quick Add Single/Multi Text Input Row + Dialog Button
        val addAutoTypeInputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val etNewSingleAutoType = EditText(this).apply {
            hint = "Ketik kalimat baru..."
            textSize = 9.5f
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0B0F17"))
                setStroke(dp(1), Color.parseColor("#10B981"))
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
                setPanelFocusable(hasFocus)
            }
        }
        addAutoTypeInputRow.addView(etNewSingleAutoType)

        val btnAddSingleAutoType = TextView(this).apply {
            text = "➕ TAMBAH"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#10B981"))
            }
            setOnClickListener {
                val raw = etNewSingleAutoType.text.toString().trim()
                if (raw.isEmpty()) {
                    Toast.makeText(applicationContext, "Ketik kalimat terlebih dahulu atau tekan [⌨️ MULTI BARIS]", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                autoTap.addAutoTypeText(raw)
                etNewSingleAutoType.setText("")
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etNewSingleAutoType.windowToken, 0)
                etNewSingleAutoType.clearFocus()
                setPanelFocusable(false)
                Toast.makeText(applicationContext, "✅ Kalimat ditambahkan!", Toast.LENGTH_SHORT).show()
                onRefreshTapMarksUi?.invoke()
            }
        }
        addAutoTypeInputRow.addView(btnAddSingleAutoType)
        autoTypeCard.addView(addAutoTypeInputRow)

        // Individual Auto-Type Text Rows Container (Each item has its own 1-tap [ 🗑️ HAPUS ] button!)
        val autoTypeItemsListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }
        autoTypeCard.addView(autoTypeItemsListContainer)

        multiMarkSectionContainer.addView(autoTypeCard)
        tapCard.addView(multiMarkSectionContainer)

        // Function to dynamically refresh the Multi-Mark Sequence UI, Lock Modes, Choreography Groups & Auto-Ketik List
        val refreshTapMarksUi: () -> Unit = {
            multiMarkSectionContainer.visibility = View.VISIBLE
            val isChainAll = autoTap.chainAllChoreographies.value
            val isRandomKoreo = autoTap.isRandomChoreographyOrder.value

            val isSeqMode = isChainAll && !isRandomKoreo
            val isRndMode = isChainAll && isRandomKoreo
            val isSingleMode = !isChainAll

            btnModeMultiMark.apply {
                setTextColor(if (isSeqMode) Color.BLACK else Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(if (isSeqMode) Color.parseColor("#00E5FF") else Color.TRANSPARENT)
                }
            }
            btnModeRandomChoreo.apply {
                setTextColor(if (isRndMode) Color.BLACK else Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(if (isRndMode) Color.parseColor("#F59E0B") else Color.TRANSPARENT)
                }
            }
            btnModeSingleTap.apply {
                setTextColor(if (isSingleMode) Color.BLACK else Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(if (isSingleMode) Color.parseColor("#00E5FF") else Color.TRANSPARENT)
                }
            }

            // Update pointer button & label
            val pointerOn = autoTap.isPointerVisible.value
            val curDispMode = autoTap.markDisplayMode.value
            btnToggleMarker.apply {
                text = when {
                    !pointerOn -> "🎯 TARGET: OFF"
                    curDispMode == com.tajasb.autotap.MarkDisplayMode.LOCKED_HIDDEN -> "🙈 TARGET: SEMBUNYI"
                    curDispMode == com.tajasb.autotap.MarkDisplayMode.LOCKED_GHOST -> "🔒 TARGET: TEMBUS"
                    else -> "🎯 TARGET: ON"
                }
                setTextColor(if (pointerOn) Color.BLACK else Color.parseColor("#CBD5E1"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(7).toFloat()
                    setColor(
                        when {
                            !pointerOn -> Color.parseColor("#1E293B")
                            curDispMode == com.tajasb.autotap.MarkDisplayMode.LOCKED_HIDDEN -> Color.parseColor("#F59E0B")
                            curDispMode == com.tajasb.autotap.MarkDisplayMode.LOCKED_GHOST -> Color.parseColor("#10B981")
                            else -> Color.parseColor("#38BDF8")
                        }
                    )
                    setStroke(dp(1), Color.parseColor(if (pointerOn) "#00E5FF" else "#334155"))
                }
            }

            // Update 3-Mode Lock & Hide Buttons
            lockModeButtons.forEach { (mode, btn) ->
                val isSelected = (curDispMode == mode)
                val activeColor = when (mode) {
                    com.tajasb.autotap.MarkDisplayMode.EDITABLE -> "#00E5FF"
                    com.tajasb.autotap.MarkDisplayMode.LOCKED_GHOST -> "#10B981"
                    com.tajasb.autotap.MarkDisplayMode.LOCKED_HIDDEN -> "#F59E0B"
                }
                btn.setTextColor(if (isSelected) Color.BLACK else Color.parseColor("#CBD5E1"))
                btn.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(if (isSelected) Color.parseColor(activeColor) else Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor(if (isSelected) activeColor else "#334155"))
                }
            }

            val activeCount = autoTap.marks.value.count { it.enabled }
            val enabledKoreoCount = autoTap.choreographyGroups.value.count { it.enabledInSequence }
            posTv.text = if (isChainAll && enabledKoreoCount > 1) {
                val modeBadge = if (isRandomKoreo) "🔀" else "🔁"
                "$modeBadge${enabledKoreoCount} Koreo • $activeCount Mark"
            } else {
                "$activeCount/${autoTap.marks.value.size} Mark"
            }

            // Update mark size title & active preset chip
            val curMarkSize = autoTap.markSizeDp.value
            tvMarkSizeTitle.text = "📏 UKURAN MARK: ${curMarkSize}dp"
            markSizeChipsList.forEach { (sz, chip) ->
                val isSelected = (curMarkSize == sz)
                chip.setTextColor(if (isSelected) Color.BLACK else Color.parseColor("#CBD5E1"))
                chip.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(5).toFloat()
                    setColor(if (isSelected) Color.parseColor("#00E5FF") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor(if (isSelected) "#00E5FF" else "#334155"))
                }
            }

            // Update delay label & active preset highlight
            if (autoTap.antiRobotEnabled.value) {
                speedLbl.text = "⏱️ JEDA ACAK: ${autoTap.minDelayMs.value}-${autoTap.maxDelayMs.value}ms"
            } else {
                speedLbl.text = "⏱️ JEDA TETAP: ${autoTap.baseIntervalMs.value}ms"
            }
            val activePreset = autoTap.currentPreset.value
            speedPresetChips.forEach { (preset, btn) ->
                val isSel = (preset == activePreset)
                btn.setTextColor(if (isSel) Color.BLACK else Color.parseColor("#CBD5E1"))
                btn.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(5).toFloat()
                    setColor(if (isSel) Color.parseColor("#00E5FF") else Color.parseColor("#0B0F17"))
                    setStroke(dp(1), Color.parseColor(if (isSel) "#00E5FF" else "#334155"))
                }
            }

            // Rebuild Ordered Choreography Sequence List (1 -> 2 -> ... -> N -> Balik ke 1 OR Random)
            choreoGroupsListContainer.removeAllViews()
            val groups = autoTap.choreographyGroups.value
            val activeGrpId = autoTap.activeGroupId.value
            val activeGrpIdx = groups.indexOfFirst { it.id == activeGrpId }
            val activeGrpName = groups.getOrNull(activeGrpIdx)?.name ?: "Default"
            val modeLabel = when {
                isChainAll && isRandomKoreo && groups.size > 1 -> "🔀 ACAK (${enabledKoreoCount}/${groups.size} KOREO)"
                isChainAll && groups.size > 1 -> "🔁 URUT #1➔#${groups.size}➔#1"
                else -> "AKTIF: ${activeGrpName.uppercase()}"
            }
            tvChoreoHeader.text = "📂 DAFTAR KOREOGRAFI (${groups.size}) • $modeLabel"

            if (groups.isEmpty()) {
                val emptyChoreoInfo = TextView(this).apply {
                    text = "💡 Belum ada daftar koreografi. Atur mark di bawah lalu tekan [➕ SIMPAN BARU] untuk membuat Koreo #1, #2, #3, #4 (otomatis jalan berurutan 1➔4➔1)."
                    textSize = 8.8f
                    setTextColor(Color.parseColor("#94A3B8"))
                    setPadding(dp(4), dp(3), dp(4), dp(3))
                }
                choreoGroupsListContainer.addView(emptyChoreoInfo)
            } else {
                groups.forEachIndexed { gIdx, grp ->
                    val koreoNo = gIdx + 1
                    val isSelectedGrp = (grp.id == activeGrpId)
                    val isPlayingThisGrp = autoTap.isTapping.value && isSelectedGrp

                    val grpRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp(4)
                        }
                        layoutParams = lp
                    }

                    // 1. Toggle Include in 1->N->1 Sequence [ ON / OFF ]
                    val btnGrpEnable = TextView(this).apply {
                        text = if (grp.enabledInSequence) "ON" else "OFF"
                        textSize = 8.8f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (grp.enabledInSequence) Color.BLACK else Color.parseColor("#94A3B8"))
                        gravity = Gravity.CENTER
                        setPadding(dp(7), dp(6), dp(7), dp(6))
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = dp(3)
                        }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(5).toFloat()
                            setColor(if (grp.enabledInSequence) Color.parseColor("#10B981") else Color.parseColor("#1E293B"))
                        }
                        setOnClickListener {
                            val nowOn = autoTap.toggleChoreographyGroupEnabled(grp.id)
                            Toast.makeText(
                                applicationContext,
                                if (nowOn) "Koreo #$koreoNo (${grp.name}) ikut antrean urut" else "Koreo #$koreoNo dilewati dalam antrean",
                                Toast.LENGTH_SHORT
                            ).show()
                            onRefreshTapMarksUi?.invoke()
                        }
                    }
                    grpRow.addView(btnGrpEnable)

                    // 2. Main Choreography Selector Button [ #1 Nama Koreo (N Mark • M#next • Psn#next) ]
                    val enabledMarksInGrp = grp.marks.count { it.enabled }
                    val nextMarkStepInGrp = autoTap.getNextMarkStepNumberForGroup(grp.id)
                    val nextTxtIdxInGrp = autoTap.getNextTextIndexForGroup(grp.id)
                    val turnBadge = buildString {
                        if (enabledMarksInGrp > 0) append(" • ⏭M#$nextMarkStepInGrp")
                        if (grp.autoTypeTexts.isNotEmpty() && grp.autoTypeSelectionMode == com.tajasb.autotap.AutoTypeSelectionMode.SEQUENTIAL && nextTxtIdxInGrp >= 0) {
                            append(" ⌨#${nextTxtIdxInGrp + 1}")
                        }
                    }
                    val btnGrpSelect = TextView(this).apply {
                        text = "${if (isPlayingThisGrp) "▶ " else if (isSelectedGrp) "✅ " else ""}#$koreoNo ${grp.name} (${enabledMarksInGrp}M$turnBadge)"
                        textSize = 9.0f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(
                            when {
                                isPlayingThisGrp -> Color.BLACK
                                isSelectedGrp -> Color.BLACK
                                grp.enabledInSequence -> Color.WHITE
                                else -> Color.parseColor("#64748B")
                            }
                        )
                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        setPadding(dp(8), dp(6), dp(8), dp(6))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(3)
                        }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(6).toFloat()
                            setColor(
                                when {
                                    isPlayingThisGrp -> Color.parseColor("#FDE047")
                                    isSelectedGrp -> Color.parseColor("#38BDF8")
                                    else -> Color.parseColor("#1E293B")
                                }
                            )
                            setStroke(
                                dp(1),
                                Color.parseColor(
                                    when {
                                        isPlayingThisGrp -> "#F59E0B"
                                        isSelectedGrp -> "#00E5FF"
                                        else -> "#334155"
                                    }
                                )
                            )
                        }
                        setOnClickListener {
                            com.tajasb.floating.FloatingBubbleWindow.syncVisibleMarkCoordinates(this@with)
                            val loaded = autoTap.loadChoreographyGroup(grp.id)
                            if (loaded != null) {
                                Toast.makeText(
                                    applicationContext,
                                    "📂 Koreo #$koreoNo '${loaded.name}' dimuat (${loaded.marks.size} Mark)!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                showCrosshairPointer()
                                onRefreshTapMarksUi?.invoke()
                            }
                        }
                    }
                    grpRow.addView(btnGrpSelect)

                    // 3. Edit Choreography Name Button [ ✏️ ]
                    val btnGrpEditName = TextView(this).apply {
                        text = "✏️"
                        textSize = 8.8f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#FDE047"))
                        gravity = Gravity.CENTER
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = dp(3)
                        }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(5).toFloat()
                            setColor(Color.parseColor("#422006"))
                            setStroke(dp(1), Color.parseColor("#F59E0B"))
                        }
                        setOnClickListener {
                            openInlineRenameForGroup(grp.id, grp.name)
                        }
                    }
                    grpRow.addView(btnGrpEditName)

                    // 4. Repeat Cycles per Choreography before advancing to next choreography [ 🔁 1x..10x ]
                    val btnGrpRepeat = TextView(this).apply {
                        text = "🔁${grp.repeatCycles}x"
                        textSize = 8.8f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#38BDF8"))
                        gravity = Gravity.CENTER
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = dp(3)
                        }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(5).toFloat()
                            setColor(Color.parseColor("#0B0F17"))
                            setStroke(dp(1), Color.parseColor("#334155"))
                        }
                        setOnClickListener {
                            val nextRep = autoTap.cycleChoreographyGroupRepeat(grp.id)
                            Toast.makeText(
                                applicationContext,
                                "Koreo #$koreoNo (${grp.name}) diulang ${nextRep}x putaran sebelum lanjut ke koreo berikutnya",
                                Toast.LENGTH_SHORT
                            ).show()
                            onRefreshTapMarksUi?.invoke()
                        }
                    }
                    grpRow.addView(btnGrpRepeat)

                    // 5. Move Choreography Up in Sequence [ ▲ ]
                    val canGrpUp = gIdx > 0
                    val btnGrpUp = TextView(this).apply {
                        text = "▲"
                        textSize = 8.8f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (canGrpUp) Color.WHITE else Color.parseColor("#475569"))
                        gravity = Gravity.CENTER
                        setPadding(dp(7), dp(6), dp(7), dp(6))
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = dp(2)
                        }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(5).toFloat()
                            setColor(Color.parseColor("#0B0F17"))
                            setStroke(dp(1), Color.parseColor(if (canGrpUp) "#334155" else "#1E293B"))
                        }
                        setOnClickListener {
                            if (canGrpUp) {
                                autoTap.moveChoreographyGroupOrder(grp.id, moveUp = true)
                                onRefreshTapMarksUi?.invoke()
                            }
                        }
                    }
                    grpRow.addView(btnGrpUp)

                    // 6. Move Choreography Down in Sequence [ ▼ ]
                    val canGrpDown = gIdx < groups.size - 1
                    val btnGrpDown = TextView(this).apply {
                        text = "▼"
                        textSize = 8.8f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (canGrpDown) Color.WHITE else Color.parseColor("#475569"))
                        gravity = Gravity.CENTER
                        setPadding(dp(7), dp(6), dp(7), dp(6))
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(5).toFloat()
                            setColor(Color.parseColor("#0B0F17"))
                            setStroke(dp(1), Color.parseColor(if (canGrpDown) "#334155" else "#1E293B"))
                        }
                        setOnClickListener {
                            if (canGrpDown) {
                                autoTap.moveChoreographyGroupOrder(grp.id, moveUp = false)
                                onRefreshTapMarksUi?.invoke()
                            }
                        }
                    }
                    grpRow.addView(btnGrpDown)

                    choreoGroupsListContainer.addView(grpRow)
                }
            }

            // Update Auto Ketik Header & Individual Text Rows
            val texts = autoTap.autoTypeTexts.value
            val mode = autoTap.autoTypeSelectionMode.value
            val autoSend = autoTap.autoSendAfterType.value
            val activeGrpIdForText = activeGrpId
            val nextSeqTextIdx = autoTap.getNextTextIndexForGroup(activeGrpIdForText)
            val lastSentTextIdx = autoTap.getLastSentTextIndexForGroup(activeGrpIdForText)
            val randomRemCount = autoTap.getRandomTextRemainingCountForGroup(activeGrpIdForText)
            val turnHeaderSuffix = when {
                texts.isEmpty() -> ""
                mode == com.tajasb.autotap.AutoTypeSelectionMode.SEQUENTIAL && nextSeqTextIdx >= 0 ->
                    " • ⏭️ GILIRAN #${nextSeqTextIdx + 1}"
                mode == com.tajasb.autotap.AutoTypeSelectionMode.RANDOM ->
                    " • 🎲 SISA ACAK: $randomRemCount/${texts.size}"
                else -> ""
            }
            autoTypeTitleTv.text = "⌨️ DAFTAR TEKS AUTO KETIK (${texts.size} KALIMAT$turnHeaderSuffix)"
            btnToggleTextMode.text = "MODE: ${mode.shortLabel.uppercase()}"
            btnToggleAutoSend.apply {
                text = if (autoSend) "KIRIM: ON" else "KIRIM: OFF"
                setTextColor(if (autoSend) Color.WHITE else Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(5).toFloat()
                    setColor(if (autoSend) Color.parseColor("#064E3B") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor(if (autoSend) "#10B981" else "#334155"))
                }
            }

            val living = autoTap.appendLivingIcons.value
            btnToggleLivingIcons.apply {
                text = if (living) "🎭 2 IKON: ON" else "🎭 2 IKON: OFF"
                setTextColor(if (living) Color.parseColor("#FDE047") else Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(5).toFloat()
                    setColor(if (living) Color.parseColor("#713F12") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor(if (living) "#EAB308" else "#334155"))
                }
            }

            autoTypeItemsListContainer.removeAllViews()
            if (texts.isEmpty()) {
                val emptyTv = TextView(this).apply {
                    text = "⚠️ Daftar teks kosong. Ketik kalimat di atas lalu tekan [➕ TAMBAH] atau [🔄 DEFAULT]."
                    textSize = 9f
                    setTextColor(Color.parseColor("#F59E0B"))
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                }
                autoTypeItemsListContainer.addView(emptyTv)
            } else {
                texts.forEachIndexed { tIdx, lineText ->
                    val isNextTurnSeq = (mode == com.tajasb.autotap.AutoTypeSelectionMode.SEQUENTIAL && tIdx == nextSeqTextIdx)
                    val isLastSent = (tIdx == lastSentTextIdx)
                    val isStillInRandomQueue = (mode == com.tajasb.autotap.AutoTypeSelectionMode.RANDOM &&
                        autoTap.isTextStillInRandomBag(lineText, activeGrpIdForText))

                    val itemRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(5), dp(6), dp(5))
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp(4)
                        }
                        layoutParams = lp
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(6).toFloat()
                            setColor(
                                when {
                                    isNextTurnSeq -> Color.parseColor("#112233")
                                    isLastSent -> Color.parseColor("#0F291E")
                                    else -> Color.parseColor("#0B0F17")
                                }
                            )
                            setStroke(
                                dp(1),
                                Color.parseColor(
                                    when {
                                        isNextTurnSeq -> "#38BDF8"
                                        isLastSent -> "#10B981"
                                        else -> "#1E293B"
                                    }
                                )
                            )
                        }
                    }

                    val turnTag = buildString {
                        if (isNextTurnSeq) append(" ⏭️[GILIRAN]")
                        if (isLastSent) append(" ✅[TERAKHIR]")
                        else if (mode == com.tajasb.autotap.AutoTypeSelectionMode.RANDOM && !isStillInRandomQueue) {
                            append(" ✓[SUDAH]")
                        }
                    }

                    val tvItemText = TextView(this).apply {
                        text = "${tIdx + 1}. $lineText$turnTag"
                        textSize = 9.2f
                        setTextColor(
                            when {
                                isNextTurnSeq -> Color.parseColor("#38BDF8")
                                isLastSent -> Color.parseColor("#6EE7B7")
                                else -> Color.parseColor("#E2E8F0")
                            }
                        )
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(6)
                        }
                        setOnClickListener {
                            FloatingUiWidgets.showEditSingleAutoTypeDialog(this@with, tIdx, lineText) {
                                onRefreshTapMarksUi?.invoke()
                            }
                        }
                    }
                    itemRow.addView(tvItemText)

                    val btnEditTextItem = TextView(this).apply {
                        text = "✏️ EDIT"
                        textSize = 8.8f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#38BDF8"))
                        gravity = Gravity.CENTER
                        setPadding(dp(8), dp(5), dp(8), dp(5))
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = dp(4)
                        }
                        layoutParams = lp
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(5).toFloat()
                            setColor(Color.parseColor("#0C4A6E"))
                            setStroke(dp(1), Color.parseColor("#0284C7"))
                        }
                        setOnClickListener {
                            FloatingUiWidgets.showEditSingleAutoTypeDialog(this@with, tIdx, lineText) {
                                onRefreshTapMarksUi?.invoke()
                            }
                        }
                    }
                    itemRow.addView(btnEditTextItem)

                    val btnDelTextItem = TextView(this).apply {
                        text = "🗑️ HAPUS"
                        textSize = 8.8f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#FCA5A5"))
                        gravity = Gravity.CENTER
                        setPadding(dp(8), dp(5), dp(8), dp(5))
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(5).toFloat()
                            setColor(Color.parseColor("#450A0A"))
                            setStroke(dp(1), Color.parseColor("#EF4444"))
                        }
                        setOnClickListener {
                            autoTap.removeAutoTypeTextAt(tIdx)
                            Toast.makeText(applicationContext, "🗑️ Dihapus: \"$lineText\"", Toast.LENGTH_SHORT).show()
                            onRefreshTapMarksUi?.invoke()
                        }
                    }
                    itemRow.addView(btnDelTextItem)
                    autoTypeItemsListContainer.addView(itemRow)
                }
            }

            // Rebuild Marks list rows (2-Row Standard Cards with Equal-Width Buttons - Ratakan)
            marksListContainer.removeAllViews()
            val allMarks = autoTap.marks.value
            val selectedId = autoTap.selectedMarkId.value
            val executingId = autoTap.activeExecutingMarkId.value
            val isRunning = autoTap.isTapping.value
            val delayCycleOptions = listOf(0L, 400L, 800L, 1500L, 2500L, 4000L, 6000L)
            val scrollDistOptions = listOf(320f, 520f, 780f)
            val loveBurstOptions = listOf(1, 3, 5, 10, 15, 25)

            val nextResumeMarkId = autoTap.getNextMarkIdForGroup(activeGrpId)

            allMarks.forEachIndexed { idx, mark ->
                val stepNo = idx + 1
                val isSelected = (mark.id == selectedId)
                val isExec = isRunning && (mark.id == executingId)
                val isNextResumeTurn = !isRunning && autoTap.hasSavedTurnProgress() && (mark.id == nextResumeMarkId)

                val markCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(7), dp(6), dp(7), dp(6))
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(5)
                    }
                    layoutParams = lp
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(7).toFloat()
                        setColor(
                            when {
                                isExec -> Color.parseColor("#1E293B")
                                isNextResumeTurn -> Color.parseColor("#172554")
                                mark.enabled -> Color.parseColor("#162032")
                                else -> Color.parseColor("#0B0F17")
                            }
                        )
                        setStroke(
                            dp(if (isExec || isSelected || isNextResumeTurn) 2 else 1),
                            Color.parseColor(
                                when {
                                    isExec -> "#FDE047"
                                    isNextResumeTurn -> "#38BDF8"
                                    isSelected -> "#00E5FF"
                                    mark.enabled -> mark.type.colorHex
                                    else -> "#1E293B"
                                }
                            )
                        )
                    }
                    setOnClickListener {
                        autoTap.selectMark(mark.id)
                    }
                }

                // ROW 1: [ ON/OFF ] + #N TYPE (X,Y) + [ 🔒/🔓 ] + [ ▶ TES ] + [ 🗑️ ]
                val topRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val btnEnable = TextView(this).apply {
                    text = if (mark.enabled) "ON" else "OFF"
                    textSize = 8.8f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (mark.enabled) Color.BLACK else Color.parseColor("#94A3B8"))
                    gravity = Gravity.CENTER
                    setPadding(dp(7), dp(4), dp(7), dp(4))
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dp(6)
                    }
                    layoutParams = lp
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        setColor(if (mark.enabled) Color.parseColor("#10B981") else Color.parseColor("#1E293B"))
                    }
                    setOnClickListener {
                        autoTap.updateMark(mark.id) { it.copy(enabled = !it.enabled) }
                    }
                }
                topRow.addView(btnEnable)

                val lblTitle = TextView(this).apply {
                    val coordInfo = " (${mark.x.toInt()}, ${mark.y.toInt()})"
                    val typeTag = if (mark.type == com.tajasb.autotap.TapMarkActionType.LOVE) {
                        "❤️ LOVE ${mark.loveBurstCount}x"
                    } else {
                        mark.type.shortLabel.uppercase()
                    }
                    val resumePrefix = if (isNextResumeTurn) "⏭️ " else ""
                    val resumeSuffix = if (isNextResumeTurn) " [GILIRAN]" else ""
                    text = "$resumePrefix#$stepNo $typeTag$coordInfo$resumeSuffix"
                    textSize = 9.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(
                        when {
                            isExec -> Color.parseColor("#FDE047")
                            isNextResumeTurn -> Color.parseColor("#38BDF8")
                            mark.enabled -> Color.WHITE
                            else -> Color.GRAY
                        }
                    )
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                topRow.addView(lblTitle)

                // Per-Mark Lock Position Toggle Button
                val isItemLocked = mark.isLocked || autoTap.isAllMarksLocked()
                val btnLockItem = TextView(this).apply {
                    text = if (isItemLocked) "🔒" else "🔓"
                    textSize = 9.2f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (isItemLocked) Color.parseColor("#FDE047") else Color.parseColor("#CBD5E1"))
                    gravity = Gravity.CENTER
                    setPadding(dp(7), dp(4), dp(7), dp(4))
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dp(4)
                    }
                    layoutParams = lp
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        setColor(Color.parseColor("#0B0F17"))
                        setStroke(dp(1), Color.parseColor(if (isItemLocked) "#F59E0B" else "#334155"))
                    }
                    setOnClickListener {
                        com.tajasb.floating.FloatingBubbleWindow.syncVisibleMarkCoordinates(this@with)
                        val lockedNow = autoTap.toggleMarkItemLock(mark.id)
                        Toast.makeText(
                            applicationContext,
                            if (lockedNow) "🔒 Posisi Mark #$stepNo dikunci" else "🔓 Kunci Mark #$stepNo dibuka",
                            Toast.LENGTH_SHORT
                        ).show()
                        showCrosshairPointer()
                        onRefreshTapMarksUi?.invoke()
                    }
                }
                topRow.addView(btnLockItem)

                // Direct Single-Step Test Button (▶ TES)
                val btnTestSingle = TextView(this).apply {
                    text = "▶ TES"
                    textSize = 8.8f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#00E5FF"))
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dp(4)
                    }
                    layoutParams = lp
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        setColor(Color.parseColor("#0B0F17"))
                        setStroke(dp(1), Color.parseColor("#00E5FF"))
                    }
                    setOnClickListener {
                        if (com.tajasb.VcamAccessibilityService.instance == null) {
                            Toast.makeText(applicationContext, "Aktifkan Aksesibilitas 'Tajasb' terlebih dahulu!", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        com.tajasb.floating.FloatingBubbleWindow.syncVisibleMarkCoordinates(this@with)
                        autoTap.executeSingleMarkOnce(mark.id)
                    }
                }
                topRow.addView(btnTestSingle)

                // Delete Mark Button
                val btnDel = TextView(this).apply {
                    text = "🗑️"
                    textSize = 9.2f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#FCA5A5"))
                    gravity = Gravity.CENTER
                    setPadding(dp(7), dp(4), dp(7), dp(4))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        setColor(Color.parseColor("#450A0A"))
                        setStroke(dp(1), Color.parseColor("#EF4444"))
                    }
                    setOnClickListener {
                        autoTap.removeMark(mark.id)
                    }
                }
                topRow.addView(btnDel)
                markCard.addView(topRow)

                // ROW 2: 4 Equal-Width Standard Setting Buttons (Ratakan!)
                // [ Mode/Arah/Love ] [ ⏱️ Jeda Mark ] [ ▲ Naik ] [ ▼ Turun ]
                val bottomOpsRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(5)
                    }
                    layoutParams = lp
                }

                val optionChip = TextView(this).apply {
                    textSize = 9f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                        marginEnd = dp(2)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        setColor(Color.parseColor("#0B0F17"))
                        setStroke(
                            dp(1),
                            Color.parseColor(
                                if (mark.type == com.tajasb.autotap.TapMarkActionType.LOVE) "#F43F5E" else "#334155"
                            )
                        )
                    }
                    when (mark.type) {
                        com.tajasb.autotap.TapMarkActionType.CLICK -> {
                            text = if (mark.isDoubleTap) "⚡ 2x Klik" else "👆 1x Klik"
                            setOnClickListener {
                                autoTap.updateMark(mark.id) { it.copy(isDoubleTap = !it.isDoubleTap) }
                            }
                        }
                        com.tajasb.autotap.TapMarkActionType.LOVE -> {
                            text = "❤️ ${mark.loveBurstCount}x Love"
                            setTextColor(Color.parseColor("#FDA4AF"))
                            setOnClickListener {
                                val curIdx = loveBurstOptions.indexOf(mark.loveBurstCount)
                                val nextBursts = loveBurstOptions[(curIdx + 1) % loveBurstOptions.size]
                                autoTap.updateMark(mark.id) { it.copy(loveBurstCount = nextBursts) }
                                Toast.makeText(
                                    applicationContext,
                                    "Mark #$stepNo Tap Love: ${nextBursts}x Beruntun ❤️",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        com.tajasb.autotap.TapMarkActionType.SCROLL -> {
                            val distTag = when {
                                mark.scrollDistancePx <= 380f -> "Pdk"
                                mark.scrollDistancePx >= 650f -> "Jauh"
                                else -> "Sdg"
                            }
                            text = "${mark.scrollDirection.icon}${mark.scrollDirection.label}•$distTag"
                            setOnClickListener {
                                val nextDir = mark.scrollDirection.next()
                                autoTap.updateMark(mark.id) { it.copy(scrollDirection = nextDir) }
                            }
                            setOnLongClickListener {
                                val curDistIdx = scrollDistOptions.indexOfFirst { it == mark.scrollDistancePx }.let { if (it < 0) 1 else it }
                                val nextDist = scrollDistOptions[(curDistIdx + 1) % scrollDistOptions.size]
                                autoTap.updateMark(mark.id) { it.copy(scrollDistancePx = nextDist) }
                                Toast.makeText(applicationContext, "Jarak Scroll Mark #$stepNo: ${nextDist.toInt()}px", Toast.LENGTH_SHORT).show()
                                true
                            }
                        }
                        com.tajasb.autotap.TapMarkActionType.BACK -> {
                            text = "🔙 Sistem"
                        }
                        com.tajasb.autotap.TapMarkActionType.AUTO_TYPE -> {
                            text = "⌨️ ${autoTap.autoTypeSelectionMode.value.shortLabel}"
                            setOnClickListener {
                                autoTap.toggleAutoTypeSelectionMode()
                            }
                        }
                    }
                }
                bottomOpsRow.addView(optionChip)

                // Per-Mark Custom Delay Chip
                val btnMarkDelay = TextView(this).apply {
                    text = if (mark.customDelayMs <= 0L) "⏱️ Auto" else "⏱️ ${mark.customDelayMs}ms"
                    textSize = 9f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (mark.customDelayMs <= 0L) Color.parseColor("#CBD5E1") else Color.parseColor("#00E5FF"))
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        setColor(Color.parseColor("#0B0F17"))
                        setStroke(dp(1), Color.parseColor(if (mark.customDelayMs <= 0L) "#334155" else "#00E5FF"))
                    }
                    setOnClickListener {
                        val curIdx = delayCycleOptions.indexOf(mark.customDelayMs)
                        val nextDelay = delayCycleOptions[(curIdx + 1) % delayCycleOptions.size]
                        autoTap.updateMark(mark.id) { it.copy(customDelayMs = nextDelay) }
                        val msg = if (nextDelay == 0L) "Mark #$stepNo mengikuti Delay Global" else "Delay Mark #$stepNo: ${nextDelay}ms"
                        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                bottomOpsRow.addView(btnMarkDelay)

                // Move Up Button (Equal Width)
                val canUp = idx > 0
                val btnUp = TextView(this).apply {
                    text = "▲ Naik"
                    textSize = 8.8f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (canUp) Color.WHITE else Color.parseColor("#475569"))
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        setColor(Color.parseColor("#0B0F17"))
                        setStroke(dp(1), Color.parseColor(if (canUp) "#334155" else "#1E293B"))
                    }
                    setOnClickListener {
                        if (canUp) autoTap.moveMarkOrder(mark.id, moveUp = true)
                    }
                }
                bottomOpsRow.addView(btnUp)

                // Move Down Button (Equal Width)
                val canDown = idx < allMarks.size - 1
                val btnDown = TextView(this).apply {
                    text = "▼ Turun"
                    textSize = 8.8f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (canDown) Color.WHITE else Color.parseColor("#475569"))
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f).apply {
                        marginStart = dp(2)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        setColor(Color.parseColor("#0B0F17"))
                        setStroke(dp(1), Color.parseColor(if (canDown) "#334155" else "#1E293B"))
                    }
                    setOnClickListener {
                        if (canDown) autoTap.moveMarkOrder(mark.id, moveUp = false)
                    }
                }
                bottomOpsRow.addView(btnDown)

                markCard.addView(bottomOpsRow)
                marksListContainer.addView(markCard)
            }
        }

        onRefreshTapMarksUi = refreshTapMarksUi
        refreshTapMarksUi()

        tabTapContainer.addView(tapCard)
        tabContentHolder.addView(tabTapContainer)
    }
}
