package com.tajasb.floating.tabs

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tajasb.FloatingControlService
import com.tajasb.quran.InteractiveQuranKhatamEngine
import com.tajasb.quran.QuranTextCatalog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * QuranKhatamTabBuilder:
 * Modular builder for Tab 4 Sub-Mode 6: Khatam Al-Qur'an 30 Juz Interaktif.
 * Sedekah Huruf Per-Event TikTok Live (Like = 1 Huruf, Chat = 1 Kata, Gift = Buka Ayat).
 */
object QuranKhatamTabBuilder {

    fun populateQuranKhatamTab(
        service: FloatingControlService,
        tabNotifContainer: LinearLayout,
        dp: (Int) -> Int,
        createStepperBtn: (String, () -> Unit) -> TextView,
        createPresetChip: (String, () -> Unit) -> TextView
    ) = with(service) {
        val quranEngine = InteractiveQuranKhatamEngine.getInstance(this)

        val quranContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerModeQuranKhatam = quranContainer

        // 1. Header Card: Status & Master Switch
        val mainCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#151D14"))
                setStroke(dp(1), Color.parseColor("#EAB308"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val headerTitle = TextView(this).apply {
            text = "Khatam Al-Qur'an 30 Juz"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FACC15"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(headerTitle)

        val isInitActive = quranEngine.state.value.isEnabled
        val statusBadge = TextView(this).apply {
            text = if (isInitActive) "AKTIF" else "NONAKTIF"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isInitActive) Color.parseColor("#22C55E") else Color.parseColor("#EF4444"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isInitActive) Color.parseColor("#14532D") else Color.parseColor("#450A0A"))
                setStroke(dp(1), if (isInitActive) Color.parseColor("#22C55E") else Color.parseColor("#EF4444"))
            }
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        headerRow.addView(statusBadge)
        mainCard.addView(headerRow)

        // Master Switch Button
        val btnToggleActive = TextView(this).apply {
            text = if (isInitActive) "HENTIKAN" else "AKTIFKAN"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(7), dp(6), dp(7))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
            layoutParams = lp
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isInitActive) Color.parseColor("#DC2626") else Color.parseColor("#EAB308"))
            }
            setOnClickListener {
                val curActive = quranEngine.state.value.isEnabled
                val nextActive = !curActive
                quranEngine.setEnabled(nextActive)
                Toast.makeText(this@with, if (nextActive) "Khatam: AKTIF" else "Khatam: NONAKTIF", Toast.LENGTH_SHORT).show()
            }
        }
        mainCard.addView(btnToggleActive)
        quranContainer.addView(mainCard)

        // 2. Live Progress Card
        val progressCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val tvCurrentSurah = TextView(this).apply {
            text = "QS. 1. Al-Fatihah : Ayat 1 / 7 (Juz 1)"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FDE047"))
        }
        progressCard.addView(tvCurrentSurah)

        val tvLetterProgress = TextView(this).apply {
            text = "Huruf: 0 / 19 (0%)"
            textSize = 8.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, dp(2), 0, dp(2))
        }
        progressCard.addView(tvLetterProgress)

        val tvLastContributor = TextView(this).apply {
            text = "Penyumbang Terakhir: -"
            textSize = 8.5f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, dp(4))
        }
        progressCard.addView(tvLastContributor)

        // Ayah Stepper Controls: Prev Ayah / Next Ayah / Reset
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        val btnPrevAyah = TextView(this).apply {
            text = "Ayat Lalu"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#334155"))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(3)
            }
            setOnClickListener {
                quranEngine.prevAyah()
            }
        }
        navRow.addView(btnPrevAyah)

        val btnNextAyah = TextView(this).apply {
            text = "Ayat Berikut"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#334155"))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            setOnClickListener {
                quranEngine.nextAyah()
            }
        }
        navRow.addView(btnNextAyah)

        val btnResetAyah = TextView(this).apply {
            text = "Ulang"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F87171"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#450A0A"))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f).apply {
                marginStart = dp(3)
            }
            setOnClickListener {
                quranEngine.resetCurrentAyah()
                Toast.makeText(this@with, "Progres ayat diulang", Toast.LENGTH_SHORT).show()
            }
            setOnLongClickListener {
                quranEngine.resetProgress()
                Toast.makeText(this@with, "Reset ke Al-Fatihah", Toast.LENGTH_SHORT).show()
                true
            }
        }
        navRow.addView(btnResetAyah)
        progressCard.addView(navRow)

        // Juz & Surah Stepper Controls
        val juzSurahRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        juzSurahRow.addView(createPureTextBtn("Juz Lalu") {
            quranEngine.prevJuz()
        })
        juzSurahRow.addView(createPureTextBtn("Juz Lanjut") {
            quranEngine.nextJuz()
        })
        juzSurahRow.addView(createPureTextBtn("Surah Lalu") {
            quranEngine.prevSurah()
        })
        juzSurahRow.addView(createPureTextBtn("Surah Lanjut") {
            quranEngine.nextSurah()
        })
        progressCard.addView(juzSurahRow)

        // Toggle Efek Gift & Tombol Reset ke Juz 1
        val giftRuleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        val btnGiftMode = TextView(this).apply {
            val isFull = quranEngine.state.value.giftCompletesAyah
            text = if (isFull) "Gift: 1 Ayat" else "Gift: +10 Huruf"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isFull) Color.parseColor("#B45309") else Color.parseColor("#065F46"))
            }
            setPadding(dp(6), dp(5), dp(6), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f).apply {
                marginEnd = dp(3)
            }
            setOnClickListener {
                val nextFull = quranEngine.toggleGiftCompletesAyah()
                text = if (nextFull) "Gift: 1 Ayat" else "Gift: +10 Huruf"
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (nextFull) Color.parseColor("#B45309") else Color.parseColor("#065F46"))
                }
            }
        }
        giftRuleRow.addView(btnGiftMode)

        val btnStartJuz1 = TextView(this).apply {
            text = "Mulai Juz 1"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FDE047"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#EAB308"))
            }
            setPadding(dp(6), dp(5), dp(6), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.95f).apply {
                marginStart = dp(3)
            }
            setOnClickListener {
                quranEngine.jumpToSurah(1, 1)
                Toast.makeText(this@with, "Kembali ke Juz 1", Toast.LENGTH_SHORT).show()
            }
        }
        giftRuleRow.addView(btnStartJuz1)
        progressCard.addView(giftRuleRow)
        quranContainer.addView(progressCard)

        // 2b. Card Pengaturan Rasio Event -> Huruf (Like, Chat, Follow, Share, Gift)
        val eventRatioCard = LinearLayout(this).apply {
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

        val ratioHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ratioTitle = TextView(this).apply {
            text = "⚡ Pengaturan Event ➔ Huruf"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#34D399"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        ratioHeader.addView(ratioTitle)

        val btnResetRatios = TextView(this).apply {
            text = "↺ Standar"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#334155"))
            }
            setOnClickListener {
                quranEngine.applyPresetMode("FAST")
                Toast.makeText(this@with, "Rasio event di-reset ke standar", Toast.LENGTH_SHORT).show()
            }
        }
        ratioHeader.addView(btnResetRatios)
        eventRatioCard.addView(ratioHeader)

        // Row Preset Cepat (1-Tap Scenario Modes)
        val rowPresetsTitle = TextView(this).apply {
            text = "Pilih Cepat Mode Siaran:"
            textSize = 7.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(4), 0, dp(2))
        }
        eventRatioCard.addView(rowPresetsTitle)

        val scrollPresetModes = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, dp(4))
        }
        val layoutPresetModes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun createModeChip(title: String, colorHex: String, modeKey: String): TextView = TextView(this).apply {
            text = title
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor(colorHex))
            }
            setPadding(dp(6), dp(3), dp(6), dp(3))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dp(4), 0)
            }
            layoutParams = lp
            setOnClickListener {
                quranEngine.applyPresetMode(modeKey)
                Toast.makeText(this@with, "Mode $title Diterapkan! 🚀", Toast.LENGTH_SHORT).show()
            }
        }

        layoutPresetModes.addView(createModeChip("⚡ Cepat (1 Like = 1 H)", "#059669", "FAST"))
        layoutPresetModes.addView(createModeChip("🚀 Banjir Like (10 Like = 1 H)", "#D97706", "CROWD"))
        layoutPresetModes.addView(createModeChip("🤲 Fokus Doa & Sawer", "#7C3AED", "PRAYER"))
        layoutPresetModes.addView(createModeChip("👑 Maraton (25 Like = 1 H)", "#DC2626", "MARATHON"))
        scrollPresetModes.addView(layoutPresetModes)
        eventRatioCard.addView(scrollPresetModes)

        val tvRatioSummary = TextView(this).apply {
            val s = quranEngine.state.value
            val tier = if (s.giftTieringEnabled) "Tiering ON" else "Tiering OFF"
            text = "Like: ${s.likesPerLetter}x | Chat: +${s.chatRewardLetters}H | Doa: +${s.keywordBonusLetters}H | Join: +${s.memberRewardLetters}H | Follow: +${s.followRewardLetters}H | Share: +${s.shareRewardLetters}H | Koin: 1=${s.coinsPerLetter}H | $tier"
            textSize = 7.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, dp(4))
        }
        eventRatioCard.addView(tvRatioSummary)

        // Helper untuk tombol stepper kecil
        fun createMiniStepBtn(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
            text = label
            textSize = 7.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#334155"))
            }
            setPadding(dp(4), dp(2), dp(4), dp(2))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(1), 0, dp(1), 0)
            }
            layoutParams = lp
            setOnClickListener { onClick() }
        }

        fun createClickableValueBadge(initialText: String, colorHex: String, onClick: () -> Unit): TextView = TextView(this).apply {
            text = initialText
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(colorHex))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor(colorHex))
            }
            setPadding(dp(6), dp(3), dp(6), dp(3))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            layoutParams = lp
            setOnClickListener { onClick() }
        }

        // Baris 1: Setting Like -> Huruf (1 s/d 1000)
        val rowLikeRatio = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvLikeLabel = TextView(this).apply {
            text = "Like ➔ Huruf"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rowLikeRatio.addView(tvLikeLabel)
        rowLikeRatio.addView(createMiniStepBtn("-5") { quranEngine.adjustLikesPerLetter(-5) })
        rowLikeRatio.addView(createPureIconBtn("➖") { quranEngine.adjustLikesPerLetter(-1) })

        val tvLikeVal = createClickableValueBadge("${quranEngine.state.value.likesPerLetter} Like = 1 H ✎", "#FDE047") {
            showNumberInputDialog(
                this,
                "Rasio Like ke Huruf",
                quranEngine.state.value.likesPerLetter,
                1,
                1000,
                "Like per 1 Huruf",
                listOf(1, 5, 10, 20, 50, 100, 250, 500)
            ) { quranEngine.setLikesPerLetter(it) }
        }
        rowLikeRatio.addView(tvLikeVal)

        rowLikeRatio.addView(createPureIconBtn("➕") { quranEngine.adjustLikesPerLetter(1) })
        rowLikeRatio.addView(createMiniStepBtn("+5") { quranEngine.adjustLikesPerLetter(5) })
        eventRatioCard.addView(rowLikeRatio)

        // Baris 2: Setting Chat / Komentar Doa -> Huruf (0 s/d 500)
        val rowChatRatio = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvChatLabel = TextView(this).apply {
            text = "Chat Biasa"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rowChatRatio.addView(tvChatLabel)
        rowChatRatio.addView(createMiniStepBtn("-5") { quranEngine.adjustChatRewardLetters(-5) })
        rowChatRatio.addView(createPureIconBtn("➖") { quranEngine.adjustChatRewardLetters(-1) })

        val tvChatVal = createClickableValueBadge("+${quranEngine.state.value.chatRewardLetters} H ✎", "#60A5FA") {
            showNumberInputDialog(
                this,
                "Bonus Huruf Chat Biasa",
                quranEngine.state.value.chatRewardLetters,
                0,
                500,
                "Huruf per pesan",
                listOf(0, 1, 4, 5, 10, 20, 50)
            ) { quranEngine.setChatRewardLetters(it) }
        }
        rowChatRatio.addView(tvChatVal)

        rowChatRatio.addView(createPureIconBtn("➕") { quranEngine.adjustChatRewardLetters(1) })
        rowChatRatio.addView(createMiniStepBtn("+5") { quranEngine.adjustChatRewardLetters(5) })
        eventRatioCard.addView(rowChatRatio)

        // Baris 3: Bonus Kata Kunci Doa ("Aamiin", "Khatam", dll) (0 s/d 500)
        val rowKeywordBonus = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvKeywordLabel = TextView(this).apply {
            text = "Bonus Doa"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCD34D"))
            layoutParams = LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rowKeywordBonus.addView(tvKeywordLabel)
        rowKeywordBonus.addView(createMiniStepBtn("-5") { quranEngine.adjustKeywordBonusLetters(-5) })
        rowKeywordBonus.addView(createPureIconBtn("➖") { quranEngine.adjustKeywordBonusLetters(-1) })

        val tvKeywordVal = createClickableValueBadge("+${quranEngine.state.value.keywordBonusLetters} H ✎", "#FBBF24") {
            showNumberInputDialog(
                this,
                "Bonus Tambahan Kata Kunci Doa",
                quranEngine.state.value.keywordBonusLetters,
                0,
                500,
                "Huruf bonus",
                listOf(5, 10, 15, 20, 30, 50, 100)
            ) { quranEngine.setKeywordBonusLetters(it) }
        }
        rowKeywordBonus.addView(tvKeywordVal)

        rowKeywordBonus.addView(createPureIconBtn("➕") { quranEngine.adjustKeywordBonusLetters(1) })
        rowKeywordBonus.addView(createMiniStepBtn("+5") { quranEngine.adjustKeywordBonusLetters(5) })
        eventRatioCard.addView(rowKeywordBonus)

        // Baris 3b: Tombol Edit Kata Kunci Doa Kustom & Preview
        val rowEditKeywords = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(3))
        }
        val btnEditKeywords = TextView(this).apply {
            text = "✏️ Edit Kata Kunci Doa"
            textSize = 7.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#854D0E"))
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                showKeywordsInputDialog(this@with, quranEngine.state.value.customKeywords) {
                    quranEngine.setCustomKeywords(it)
                }
            }
        }
        rowEditKeywords.addView(btnEditKeywords)

        val tvKeywordsPreview = TextView(this).apply {
            val kw = quranEngine.state.value.customKeywords
            val kwCount = kw.split(",").filter { it.isNotBlank() }.size
            text = "($kwCount kata): ${kw.take(28)}${if (kw.length > 28) "..." else ""}"
            textSize = 7.0f
            setTextColor(Color.parseColor("#FDE68A"))
            setPadding(dp(6), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                showKeywordsInputDialog(this@with, quranEngine.state.value.customKeywords) {
                    quranEngine.setCustomKeywords(it)
                }
            }
        }
        rowEditKeywords.addView(tvKeywordsPreview)
        eventRatioCard.addView(rowEditKeywords)

        // Baris 4: Setting Member Join (Penonton Baru Masuk) (0 s/d 200)
        val rowMemberJoin = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvMemberLabel = TextView(this).apply {
            text = "Join Masuk"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rowMemberJoin.addView(tvMemberLabel)
        rowMemberJoin.addView(createPureIconBtn("➖") { quranEngine.adjustMemberRewardLetters(-1) })

        val tvMemberVal = createClickableValueBadge(
            if (quranEngine.state.value.memberRewardLetters > 0) "+${quranEngine.state.value.memberRewardLetters} H ✎" else "OFF ✎",
            "#34D399"
        ) {
            showNumberInputDialog(
                this,
                "Huruf Penonton Masuk Live",
                quranEngine.state.value.memberRewardLetters,
                0,
                200,
                "Huruf per join",
                listOf(0, 1, 2, 5, 10)
            ) { quranEngine.setMemberRewardLetters(it) }
        }
        rowMemberJoin.addView(tvMemberVal)

        rowMemberJoin.addView(createPureIconBtn("➕") { quranEngine.adjustMemberRewardLetters(1) })
        eventRatioCard.addView(rowMemberJoin)

        // Baris 5: Setting Follow & Share -> Huruf (0 s/d 200)
        val rowFollowShare = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvFollowLabel = TextView(this).apply {
            text = "Follow"
            textSize = 7.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rowFollowShare.addView(tvFollowLabel)
        rowFollowShare.addView(createPureIconBtn("➖") { quranEngine.adjustFollowRewardLetters(-1) })

        val tvFollowVal = createClickableValueBadge("+${quranEngine.state.value.followRewardLetters}H ✎", "#A78BFA") {
            showNumberInputDialog(
                this,
                "Bonus Huruf Follow Baru",
                quranEngine.state.value.followRewardLetters,
                0,
                200,
                "Huruf per follow",
                listOf(0, 1, 5, 10, 20)
            ) { quranEngine.setFollowRewardLetters(it) }
        }
        rowFollowShare.addView(tvFollowVal)

        rowFollowShare.addView(createPureIconBtn("➕") { quranEngine.adjustFollowRewardLetters(1) })

        val tvShareLabel = TextView(this).apply {
            text = "Share"
            textSize = 7.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rowFollowShare.addView(tvShareLabel)
        rowFollowShare.addView(createPureIconBtn("➖") { quranEngine.adjustShareRewardLetters(-1) })

        val tvShareVal = createClickableValueBadge("+${quranEngine.state.value.shareRewardLetters}H ✎", "#F472B6") {
            showNumberInputDialog(
                this,
                "Bonus Huruf Share Siaran",
                quranEngine.state.value.shareRewardLetters,
                0,
                200,
                "Huruf per share",
                listOf(0, 1, 5, 10, 20)
            ) { quranEngine.setShareRewardLetters(it) }
        }
        rowFollowShare.addView(tvShareVal)

        rowFollowShare.addView(createPureIconBtn("➕") { quranEngine.adjustShareRewardLetters(1) })
        eventRatioCard.addView(rowFollowShare)

        // Baris 6: Setting Gift Koin -> Huruf (1 s/d 100)
        val rowGiftCoins = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvCoinsLabel = TextView(this).apply {
            text = "Gift Koin"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rowGiftCoins.addView(tvCoinsLabel)
        rowGiftCoins.addView(createPureIconBtn("➖") { quranEngine.adjustCoinsPerLetter(-1) })

        val tvCoinsVal = createClickableValueBadge("1 Koin = ${quranEngine.state.value.coinsPerLetter} H ✎", "#38BDF8") {
            showNumberInputDialog(
                this,
                "Rasio Koin/Diamond Gift",
                quranEngine.state.value.coinsPerLetter,
                1,
                100,
                "Huruf per 1 koin",
                listOf(1, 2, 3, 5, 10, 20)
            ) { quranEngine.setCoinsPerLetter(it) }
        }
        rowGiftCoins.addView(tvCoinsVal)

        rowGiftCoins.addView(createPureIconBtn("➕") { quranEngine.adjustCoinsPerLetter(1) })
        eventRatioCard.addView(rowGiftCoins)

        // Baris 7: Saklar Tiering Gift Khusus
        val rowTieringGift = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(2))
        }
        val btnToggleGiftTiering = TextView(this).apply {
            val isTierOn = quranEngine.state.value.giftTieringEnabled
            text = if (isTierOn) "🎁 Tiering Gift: AKTIF (Koin=${quranEngine.state.value.coinsPerLetter}H, >=100💎=Ayat, Sultan=Surat)" else "🎁 Tiering Gift: NONAKTIF"
            textSize = 7.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isTierOn) Color.parseColor("#7C3AED") else Color.parseColor("#374151"))
            }
            setPadding(dp(4), dp(5), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val next = quranEngine.toggleGiftTiering()
                text = if (next) "🎁 Tiering Gift: AKTIF (Koin=${quranEngine.state.value.coinsPerLetter}H, >=100💎=Ayat, Sultan=Surat)" else "🎁 Tiering Gift: NONAKTIF"
                (background as? GradientDrawable)?.setColor(if (next) Color.parseColor("#7C3AED") else Color.parseColor("#374151"))
            }
        }
        rowTieringGift.addView(btnToggleGiftTiering)
        eventRatioCard.addView(rowTieringGift)

        quranContainer.addView(eventRatioCard)

        // 3. Quick Jump Presets Card
        val quickJumpCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val jumpTitle = TextView(this).apply {
            text = "Lompat Cepat:"
            textSize = 9.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, dp(4))
        }
        quickJumpCard.addView(jumpTitle)

        val rowPresets1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }
        rowPresets1.addView(createPresetChip("Al-Fatihah") { quranEngine.jumpToSurah(1, 1) })
        rowPresets1.addView(createPresetChip("Al-Baqarah") { quranEngine.jumpToSurah(2, 1) })
        rowPresets1.addView(createPresetChip("Ayat Kursi") { quranEngine.jumpToSurah(2, 255) })
        quickJumpCard.addView(rowPresets1)

        val rowPresets2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }
        rowPresets2.addView(createPresetChip("Al-Kahf") { quranEngine.jumpToSurah(18, 1) })
        rowPresets2.addView(createPresetChip("Yasin") { quranEngine.jumpToSurah(36, 1) })
        rowPresets2.addView(createPresetChip("Ar-Rahman") { quranEngine.jumpToSurah(55, 1) })
        quickJumpCard.addView(rowPresets2)

        val rowPresets3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        rowPresets3.addView(createPresetChip("Al-Mulk") { quranEngine.jumpToSurah(67, 1) })
        rowPresets3.addView(createPresetChip("An-Naba'") { quranEngine.jumpToSurah(78, 1) })
        rowPresets3.addView(createPresetChip("An-Nas") { quranEngine.jumpToSurah(114, 1) })
        quickJumpCard.addView(rowPresets3)
        quranContainer.addView(quickJumpCard)

        // 4. Manual Test / Simulation Buttons
        val testCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val testTitle = TextView(this).apply {
            text = "Uji Coba:"
            textSize = 9.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, dp(4))
        }
        testCard.addView(testTitle)

        val testRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnTestLike = TextView(this).apply {
            text = "+1 Huruf"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#DC2626"))
            }
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            setOnClickListener {
                if (!quranEngine.state.value.isEnabled) {
                    quranEngine.setEnabled(true)
                }
                val count = maxOf(1, quranEngine.state.value.likesPerLetter)
                quranEngine.onLikeEvent("Penonton_Tes", count)
            }
        }
        testRow.addView(btnTestLike)

        val btnTestChat = TextView(this).apply {
            text = "+1 Kata"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#2563EB"))
            }
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener {
                if (!quranEngine.state.value.isEnabled) {
                    quranEngine.setEnabled(true)
                }
                quranEngine.onChatEvent("Hamba_Allah", "Aamiin")
            }
        }
        testRow.addView(btnTestChat)

        val btnTestGift = TextView(this).apply {
            text = "Buka Ayat"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#F59E0B"))
            }
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
            }
            setOnClickListener {
                if (!quranEngine.state.value.isEnabled) {
                    quranEngine.setEnabled(true)
                }
                quranEngine.onGiftEvent("Sultan_TikTok", "Mawar", 1)
            }
        }
        testRow.addView(btnTestGift)
        testCard.addView(testRow)

        // Baris Tes Lanjutan: Join Member, Chat Doa "Aamiin" (Bonus), dan Gift Paus/Singa (Khatam Surat)
        val testRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }

        val btnTestJoin = TextView(this).apply {
            text = "👋 Tes Join"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#059669"))
            }
            setPadding(dp(4), dp(5), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            setOnClickListener {
                if (!quranEngine.state.value.isEnabled) {
                    quranEngine.setEnabled(true)
                }
                quranEngine.onMemberJoinEvent("Tamu_Berkah")
            }
        }
        testRow2.addView(btnTestJoin)

        val btnTestDoaAamiin = TextView(this).apply {
            text = "🤲 Doa Aamiin"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#4F46E5"))
            }
            setPadding(dp(4), dp(5), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener {
                if (!quranEngine.state.value.isEnabled) {
                    quranEngine.setEnabled(true)
                }
                quranEngine.onChatEvent("Hamba_Shalih", "Aamiin Ya Rabbal Alamin 🤲")
            }
        }
        testRow2.addView(btnTestDoaAamiin)

        val btnTestPaus = TextView(this).apply {
            text = "🐳 Paus Sultan"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#7C3AED"))
            }
            setPadding(dp(4), dp(5), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginStart = dp(2)
            }
            setOnClickListener {
                if (!quranEngine.state.value.isEnabled) {
                    quranEngine.setEnabled(true)
                }
                quranEngine.onGiftEvent("Sultan_Arab", "Paus TikTok", 1, 10000)
            }
        }
        testRow2.addView(btnTestPaus)

        testCard.addView(testRow2)
        quranContainer.addView(testCard)

        // 4b. Dedicated Card: Top Penulis Khatam (4 Kategori: Al-Qur'an, Surat, Ayat, Huruf)
        val eventPrefs = com.tajasb.eventrelay.LiveEventPreferences.getInstance(this)
        val eventReceiver = com.tajasb.eventrelay.LiveEventReceiver.getInstance(this)

        val topKhatamCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#E11D48"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val topKhatamHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val topKhatamTitle = TextView(this).apply {
            text = "🏆 Top Penulis Khatam (4 Kategori)"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F43F5E"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topKhatamHeader.addView(topKhatamTitle)

        val btnToggleTopKhatam = TextView(this).apply {
            val isOn = eventPrefs.config.value.enableTopLikersOverlay
            text = if (isOn) "ON" else "OFF"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isOn) Color.parseColor("#E11D48") else Color.parseColor("#334155"))
            }
            setOnClickListener {
                val next = !eventPrefs.config.value.enableTopLikersOverlay
                eventPrefs.setEnableTopLikersOverlay(next)
                text = if (next) "ON" else "OFF"
                (background as? GradientDrawable)?.setColor(if (next) Color.parseColor("#E11D48") else Color.parseColor("#334155"))
            }
        }
        topKhatamHeader.addView(btnToggleTopKhatam)
        topKhatamCard.addView(topKhatamHeader)

        val topKhatamSub = TextView(this).apply {
            text = "Ranking 4 Kategori: Al-Qur'an, Surat, Ayat, Huruf. Ditampilkan transparan di stream VCam."
            textSize = 7.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, dp(4))
        }
        topKhatamCard.addView(topKhatamSub)

        // Row Controls: Format Orientasi & Test Data
        val topKhatamRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }

        val btnOrientationToggle = TextView(this).apply {
            val isVert = eventPrefs.config.value.topLikersOrientation == "VERTICAL"
            text = if (isVert) "↕ Vertikal" else "↔ Horizontal"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0284C7"))
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            setOnClickListener {
                val current = eventPrefs.config.value.topLikersOrientation
                val next = if (current == "VERTICAL") "HORIZONTAL" else "VERTICAL"
                eventPrefs.setTopLikersOrientation(next)
                text = if (next == "VERTICAL") "↕ Vertikal" else "↔ Horizontal"
            }
        }
        topKhatamRow1.addView(btnOrientationToggle)

        val btnInjectKhatamTest = TextView(this).apply {
            text = "▶️ Tes 4 Kategori"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#059669"))
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener {
                if (!eventPrefs.config.value.enableTopLikersOverlay) {
                    eventPrefs.setEnableTopLikersOverlay(true)
                    btnToggleTopKhatam.text = "ON"
                    (btnToggleTopKhatam.background as? GradientDrawable)?.setColor(Color.parseColor("#E11D48"))
                }
                eventReceiver.injectTestTopLikersActivity()
                Toast.makeText(this@with, "Data uji 4 kategori dimasukkan ✨", Toast.LENGTH_SHORT).show()
            }
        }
        topKhatamRow1.addView(btnInjectKhatamTest)

        val btnResetKhatamData = TextView(this).apply {
            text = "↺ Reset"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#4B5563"))
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f).apply {
                marginStart = dp(2)
            }
            setOnClickListener {
                eventReceiver.clearLeaderboards()
                Toast.makeText(this@with, "Leaderboard di-reset", Toast.LENGTH_SHORT).show()
            }
        }
        topKhatamRow1.addView(btnResetKhatamData)
        topKhatamCard.addView(topKhatamRow1)

        quranContainer.addView(topKhatamCard)

        // 5. Position & Transform Controls Card (Geser & Atur Posisi Card di Layar)
        val posCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#151D14"))
                setStroke(dp(1), Color.parseColor("#EAB308"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val posHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val posTitle = TextView(this).apply {
            text = "Posisi & Ukuran:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FDE047"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        posHeaderRow.addView(posTitle)

        val btnResetPos = TextView(this).apply {
            text = "↺"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#334155"))
            }
            setOnClickListener {
                quranEngine.resetOverlayTransform()
                Toast.makeText(this@with, "Posisi direset", Toast.LENGTH_SHORT).show()
            }
        }
        posHeaderRow.addView(btnResetPos)
        posCard.addView(posHeaderRow)

        val tvPosInfo = TextView(this).apply {
            val st = quranEngine.state.value
            val px = (st.posXpct * 100).toInt()
            val py = (st.posYpct * 100).toInt()
            val rot = st.overlayRotation
            val mir = if (st.overlayMirrored) "ON" else "OFF"
            val sc = "%.2f".format(st.overlayScale)
            text = "Posisi: X=$px% • Y=$py% | Rotasi: $rot° | Cermin: $mir | Skala: ${sc}x"
            textSize = 8.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, dp(2), 0, dp(4))
        }
        tvQuranPosInfo = tvPosInfo
        posCard.addView(tvPosInfo)

        var isFineStep = false

        fun updatePosLabel() {
            val st = quranEngine.state.value
            val px = (st.posXpct * 100).toInt()
            val py = (st.posYpct * 100).toInt()
            val rot = st.overlayRotation
            val mir = if (st.overlayMirrored) "ON" else "OFF"
            val sc = "%.2f".format(st.overlayScale)
            tvQuranPosInfo?.text = "Posisi: X=$px% • Y=$py% | Rotasi: $rot° | Cermin: $mir | Skala: ${sc}x"
        }

        // Kecepatan Langkah: Cepat vs Halus
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }
        val btnModeFast = TextView(this).apply {
            text = "Cepat"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(2) }
        }
        val btnModeFine = TextView(this).apply {
            text = "Halus"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(2) }
        }
        fun refreshModeBtns() {
            btnModeFast.setTextColor(if (!isFineStep) Color.BLACK else Color.WHITE)
            btnModeFast.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (!isFineStep) Color.parseColor("#FDE047") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (!isFineStep) Color.parseColor("#FDE047") else Color.parseColor("#334155"))
            }
            btnModeFine.setTextColor(if (isFineStep) Color.BLACK else Color.WHITE)
            btnModeFine.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isFineStep) Color.parseColor("#38BDF8") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (isFineStep) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
            }
        }
        btnModeFast.setOnClickListener {
            isFineStep = false
            refreshModeBtns()
        }
        btnModeFine.setOnClickListener {
            isFineStep = true
            refreshModeBtns()
        }
        refreshModeBtns()
        modeRow.addView(btnModeFast)
        modeRow.addView(btnModeFine)
        posCard.addView(modeRow)

        // D-PAD Navigasi Posisi 4 Arah (⬅️, ⬆️, ⬇️, ➡️) Tanpa Batas Layar
        val dpadRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }
        dpadRow.addView(createPureIconBtn("⬅️") {
            val delta = if (isFineStep) 0.01f else 0.05f
            quranEngine.moveOverlay(-delta, 0f)
            updatePosLabel()
        })
        dpadRow.addView(createPureIconBtn("⬆️") {
            val delta = if (isFineStep) 0.01f else 0.05f
            quranEngine.moveOverlay(0f, -delta)
            updatePosLabel()
        })
        dpadRow.addView(createPureIconBtn("⬇️") {
            val delta = if (isFineStep) 0.01f else 0.05f
            quranEngine.moveOverlay(0f, delta)
            updatePosLabel()
        })
        dpadRow.addView(createPureIconBtn("➡️") {
            val delta = if (isFineStep) 0.01f else 0.05f
            quranEngine.moveOverlay(delta, 0f)
            updatePosLabel()
        })
        posCard.addView(dpadRow)

        // Preset Posisi
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(4))
        }
        presetRow.addView(createPresetChip("Atas 20%") {
            quranEngine.setOverlayPosition(0.50f, 0.20f)
            updatePosLabel()
        })
        presetRow.addView(createPresetChip("Tengah 50%") {
            quranEngine.setOverlayPosition(0.50f, 0.50f)
            quranEngine.setCardWidthPct(0.98f)
            updatePosLabel()
        })
        presetRow.addView(createPresetChip("Bawah 80%") {
            quranEngine.setOverlayPosition(0.50f, 0.80f)
            updatePosLabel()
        })
        posCard.addView(presetRow)

        // Transform row: Rotasi & Cermin & Skala Tanpa Batas
        val transformRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvSkalaLabel = TextView(this).apply {
            text = "Skala"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        transformRow.addView(tvSkalaLabel)

        val btnRotate = TextView(this).apply {
            text = "${quranEngine.state.value.overlayRotation}°"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            setOnClickListener {
                quranEngine.cycleOverlayRotation()
                text = "${quranEngine.state.value.overlayRotation}°"
                updatePosLabel()
            }
        }
        transformRow.addView(btnRotate)

        val btnMirror = TextView(this).apply {
            text = if (quranEngine.state.value.overlayMirrored) "Mir: ON" else "Mir: OFF"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener {
                quranEngine.toggleOverlayMirror()
                text = if (quranEngine.state.value.overlayMirrored) "Mir: ON" else "Mir: OFF"
                updatePosLabel()
            }
        }
        transformRow.addView(btnMirror)

        transformRow.addView(createPureIconBtn("➖") {
            val delta = if (isFineStep) 0.02f else 0.10f
            quranEngine.adjustOverlayScale(-delta)
            updatePosLabel()
        })
        transformRow.addView(createPureIconBtn("➕") {
            val delta = if (isFineStep) 0.02f else 0.10f
            quranEngine.adjustOverlayScale(delta)
            updatePosLabel()
        })
        posCard.addView(transformRow)
        quranContainer.addView(posCard)

        // 6. Typography & Card Dimension Settings
        val typoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val typoTitle = TextView(this).apply {
            text = "Ukuran Font & Format:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, dp(4))
        }
        typoCard.addView(typoTitle)

        fun formatSp(sp: Float): String = if (sp % 1f == 0f) "${sp.toInt()}" else "%.1f".format(sp)

        val tvTypoStatus = TextView(this).apply {
            val st = quranEngine.state.value
            val arabSp = formatSp(st.arabicFontSizeSp)
            val latinSp = formatSp(st.latinFontSizeSp)
            val lines = if (st.maxArabicLines <= 0) "Auto" else "${st.maxArabicLines}"
            val lebar = (st.cardWidthPct * 100).toInt()
            val pad = st.cardPaddingV
            text = "Arab: ${arabSp}sp | Latin: ${latinSp}sp | Baris: $lines | Lebar: $lebar% | Pad: ${pad}dp"
            textSize = 8.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 0, 0, dp(4))
        }
        typoCard.addView(tvTypoStatus)

        fun updateTypoStatus() {
            val st = quranEngine.state.value
            val arabSp = formatSp(st.arabicFontSizeSp)
            val latinSp = formatSp(st.latinFontSizeSp)
            val linesLabel = if (st.maxArabicLines <= 0) "Auto (Fleksibel)" else "${st.maxArabicLines} Baris"
            val transLabel = if (st.showTranslation) "Arti ON" else "Arti OFF"
            val notifLabel = if (st.showNotifUnderAyah) "Notif ON" else "Notif OFF"
            val lebar = (st.cardWidthPct * 100).toInt()
            val pad = st.cardPaddingV
            tvTypoStatus.text = "Arab: ${arabSp}sp | Latin: ${latinSp}sp | Baris: $linesLabel | Lebar: $lebar% | Pad: ${pad}dp | $transLabel | $notifLabel"
        }

        // Font Arab Row
        val typoRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvArabLabel = TextView(this).apply {
            text = "Arab"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        typoRow1.addView(tvArabLabel)
        typoRow1.addView(createPureIconBtn("➖") {
            val delta = if (isFineStep) 0.5f else 2.0f
            quranEngine.adjustArabicFontSize(-delta)
            val v = quranEngine.state.value.arabicFontSizeSp
            Toast.makeText(this@with, "Font Arab: ${formatSp(v)}sp", Toast.LENGTH_SHORT).show()
            updateTypoStatus()
        })
        typoRow1.addView(createPureIconBtn("➕") {
            val delta = if (isFineStep) 0.5f else 2.0f
            quranEngine.adjustArabicFontSize(delta)
            val v = quranEngine.state.value.arabicFontSizeSp
            Toast.makeText(this@with, "Font Arab: ${formatSp(v)}sp", Toast.LENGTH_SHORT).show()
            updateTypoStatus()
        })
        typoRow1.addView(createPureTextBtn("Reset 28sp") {
            quranEngine.setArabicFontSize(28f)
            Toast.makeText(this@with, "Font Arab: 28sp", Toast.LENGTH_SHORT).show()
            updateTypoStatus()
        })
        typoCard.addView(typoRow1)

        // Baris Ayat Row (Bebas / Fleksibel / Kustom Baris)
        val typoRowBaris = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvBarisLabel = TextView(this).apply {
            text = "Baris"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        typoRowBaris.addView(tvBarisLabel)
        typoRowBaris.addView(createPureIconBtn("➖") {
            quranEngine.adjustMaxArabicLines(-1)
            updateTypoStatus()
        })
        typoRowBaris.addView(createPureIconBtn("➕") {
            quranEngine.adjustMaxArabicLines(1)
            updateTypoStatus()
        })
        val btnAutoBaris = TextView(this).apply {
            text = "Auto"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#34D399"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dp(1), Color.parseColor("#059669"))
            }
            setPadding(dp(5), dp(4), dp(5), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener {
                quranEngine.setMaxArabicLines(0) // 0 = Auto Fleksibel Alami Sesuai Panjang Ayat
                Toast.makeText(this@with, "Baris: Auto (Fleksibel Alami)", Toast.LENGTH_SHORT).show()
                updateTypoStatus()
            }
        }
        typoRowBaris.addView(btnAutoBaris)

        val btnCycleLines = TextView(this).apply {
            text = "Ganti Baris"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginStart = dp(1)
            }
            setOnClickListener {
                val nextLines = quranEngine.cycleMaxArabicLines()
                val lineLabel = if (nextLines <= 0) "Auto (Fleksibel)" else "$nextLines Baris"
                Toast.makeText(this@with, "Baris: $lineLabel", Toast.LENGTH_SHORT).show()
                updateTypoStatus()
            }
        }
        typoRowBaris.addView(btnCycleLines)
        typoCard.addView(typoRowBaris)

        // Font Latin Row
        val typoRowLatin = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(3))
        }
        val tvLatinLabel = TextView(this).apply {
            text = "Latin"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        typoRowLatin.addView(tvLatinLabel)
        typoRowLatin.addView(createPureIconBtn("➖") {
            val delta = if (isFineStep) 0.5f else 1.0f
            quranEngine.adjustLatinFontSize(-delta)
            val v = quranEngine.state.value.latinFontSizeSp
            Toast.makeText(this@with, "Font Latin: ${formatSp(v)}sp", Toast.LENGTH_SHORT).show()
            updateTypoStatus()
        })
        typoRowLatin.addView(createPureIconBtn("➕") {
            val delta = if (isFineStep) 0.5f else 1.0f
            quranEngine.adjustLatinFontSize(delta)
            val v = quranEngine.state.value.latinFontSizeSp
            Toast.makeText(this@with, "Font Latin: ${formatSp(v)}sp", Toast.LENGTH_SHORT).show()
            updateTypoStatus()
        })
        typoRowLatin.addView(createPureTextBtn("Reset 12sp") {
            quranEngine.setLatinFontSize(12f)
            Toast.makeText(this@with, "Font Latin: 12sp", Toast.LENGTH_SHORT).show()
            updateTypoStatus()
        })
        typoCard.addView(typoRowLatin)

        // Lebar Kotak (Kiri-Kanan)
        val typoRowLebar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvLebarLabel = TextView(this).apply {
            text = "Lebar"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        typoRowLebar.addView(tvLebarLabel)
        typoRowLebar.addView(createPureIconBtn("➖") {
            val delta = if (isFineStep) 0.01f else 0.05f
            quranEngine.adjustCardWidthPct(-delta)
            updateTypoStatus()
        })
        typoRowLebar.addView(createPureIconBtn("➕") {
            val delta = if (isFineStep) 0.01f else 0.05f
            quranEngine.adjustCardWidthPct(delta)
            updateTypoStatus()
        })
        typoRowLebar.addView(createPureTextBtn("Auto 98%") {
            quranEngine.setCardWidthPct(0.98f)
            updateTypoStatus()
        })
        typoCard.addView(typoRowLebar)

        // Tinggi Kotak (Atas-Bawah / Padding Vertikal)
        val typoRowTinggi = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvTinggiLabel = TextView(this).apply {
            text = "Tinggi"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        typoRowTinggi.addView(tvTinggiLabel)
        typoRowTinggi.addView(createPureIconBtn("➖") {
            val delta = if (isFineStep) 1 else 4
            quranEngine.adjustCardPaddingV(-delta)
            updateTypoStatus()
        })
        typoRowTinggi.addView(createPureIconBtn("➕") {
            val delta = if (isFineStep) 1 else 4
            quranEngine.adjustCardPaddingV(delta)
            updateTypoStatus()
        })
        typoRowTinggi.addView(createPureTextBtn("Reset 16dp") {
            quranEngine.setCardPaddingV(16)
            updateTypoStatus()
        })
        typoCard.addView(typoRowTinggi)

        // Baris Saklar Fitur: Notif Bawah Ayat & Terjemahan Arti
        val typoRowSwitches = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(2))
        }

        val btnToggleNotifUnderAyah = TextView(this).apply {
            val isNotifOn = quranEngine.state.value.showNotifUnderAyah
            text = if (isNotifOn) "Notif Ayat: ON" else "Notif Ayat: OFF"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isNotifOn) Color.parseColor("#2563EB") else Color.parseColor("#374151"))
            }
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            setOnClickListener {
                val next = quranEngine.toggleNotifUnderAyah()
                text = if (next) "Notif Ayat: ON" else "Notif Ayat: OFF"
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (next) Color.parseColor("#2563EB") else Color.parseColor("#374151"))
                }
            }
        }
        typoRowSwitches.addView(btnToggleNotifUnderAyah)

        val btnToggleTrans = TextView(this).apply {
            val isShown = quranEngine.state.value.showTranslation
            text = if (isShown) "Arti: ON" else "Arti: OFF"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isShown) Color.parseColor("#065F46") else Color.parseColor("#374151"))
            }
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
            }
            setOnClickListener {
                val isShownNext = quranEngine.toggleTranslation()
                text = if (isShownNext) "Arti: ON" else "Arti: OFF"
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isShownNext) Color.parseColor("#065F46") else Color.parseColor("#374151"))
                }
            }
        }
        typoRowSwitches.addView(btnToggleTrans)
        typoCard.addView(typoRowSwitches)
        quranContainer.addView(typoCard)

        tabNotifContainer.addView(quranContainer)

        // Observe state updates
        serviceScope.launch {
            quranEngine.state.collectLatest { s ->
                val sInfo = QuranTextCatalog.getSurahInfo(s.currentSurahNumber)
                val surahName = sInfo.nameLatin
                val totalAyah = sInfo.ayahCount
                val totalChars = s.totalCharsInAyah.coerceAtLeast(1)
                val pct = ((s.revealedCharCount.toFloat() / totalChars.toFloat()) * 100f).toInt().coerceIn(0, 100)

                val curJuz = s.currentVerse.juzNumber.coerceIn(1, 30)
                tvCurrentSurah.text = "Juz $curJuz • QS. ${s.currentSurahNumber}. $surahName : Ayat ${s.currentAyahNumber} / $totalAyah"
                tvLetterProgress.text = "Huruf: ${s.revealedCharCount} / $totalChars ($pct%) | Selesai: ${s.totalAyahsCompletedSession} Ayat"
                tvLastContributor.text = "Penyumbang: ${s.lastContributorName} (${s.lastActionDescription})"

                btnGiftMode.text = if (s.giftCompletesAyah) "Gift: 1 Ayat" else "Gift: +10 Huruf"
                (btnGiftMode.background as? GradientDrawable)?.setColor(
                    if (s.giftCompletesAyah) Color.parseColor("#92400E") else Color.parseColor("#065F46")
                )

                // Event to Letters ratio labels
                val tier = if (s.giftTieringEnabled) "Tiering ON" else "Tiering OFF"
                tvRatioSummary.text = "Like: ${s.likesPerLetter}x | Chat: +${s.chatRewardLetters}H | Doa: +${s.keywordBonusLetters}H | Join: +${s.memberRewardLetters}H | Follow: +${s.followRewardLetters}H | Share: +${s.shareRewardLetters}H | Koin: 1=${s.coinsPerLetter}H | $tier"
                tvLikeVal.text = "${s.likesPerLetter} Like = 1 H ✎"
                tvChatVal.text = "+${s.chatRewardLetters} H ✎"
                tvKeywordVal.text = "+${s.keywordBonusLetters} H ✎"
                val kwCount = s.customKeywords.split(",").filter { it.isNotBlank() }.size
                tvKeywordsPreview.text = "($kwCount kata): ${s.customKeywords.take(28)}${if (s.customKeywords.length > 28) "..." else ""}"
                tvMemberVal.text = if (s.memberRewardLetters > 0) "+${s.memberRewardLetters} H ✎" else "OFF ✎"
                tvFollowVal.text = "+${s.followRewardLetters}H ✎"
                tvShareVal.text = "+${s.shareRewardLetters}H ✎"
                tvCoinsVal.text = "1 Koin = ${s.coinsPerLetter} H ✎"
                btnToggleGiftTiering.text = if (s.giftTieringEnabled) "🎁 Tiering Gift: AKTIF (Koin=${s.coinsPerLetter}H, >=100💎=Ayat, Sultan=Surat)" else "🎁 Tiering Gift: NONAKTIF"
                (btnToggleGiftTiering.background as? GradientDrawable)?.setColor(if (s.giftTieringEnabled) Color.parseColor("#7C3AED") else Color.parseColor("#374151"))

                // Position status text
                val px = (s.posXpct * 100).toInt()
                val py = (s.posYpct * 100).toInt()
                val sc = "%.2f".format(s.overlayScale)
                val mir = if (s.overlayMirrored) "ON" else "OFF"
                tvPosInfo.text = "Posisi: X=$px% • Y=$py% | Rotasi: ${s.overlayRotation}° | Cermin: $mir | Skala: ${sc}x"

                // Typo status text
                val lineLabel = if (s.maxArabicLines <= 0 || s.maxArabicLines >= 99) "Auto" else "${s.maxArabicLines} Baris"
                val transLabel = if (s.showTranslation) "Arti ON" else "Arti OFF"
                val notifLabel = if (s.showNotifUnderAyah) "Notif ON" else "Notif OFF"
                val arabSp = formatSp(s.arabicFontSizeSp)
                val latinSp = formatSp(s.latinFontSizeSp)
                tvTypoStatus.text = "Arab: ${arabSp}sp | Latin: ${latinSp}sp | $lineLabel | ${(s.cardWidthPct * 100).toInt()}% Lebar | Pad ${s.cardPaddingV}dp | $transLabel | $notifLabel"
                btnToggleTrans.text = if (s.showTranslation) "Arti: ON" else "Arti: OFF"
                btnToggleTrans.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (s.showTranslation) Color.parseColor("#065F46") else Color.parseColor("#374151"))
                }
                btnToggleNotifUnderAyah.text = if (s.showNotifUnderAyah) "Notif Ayat: ON" else "Notif Ayat: OFF"
                btnToggleNotifUnderAyah.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (s.showNotifUnderAyah) Color.parseColor("#2563EB") else Color.parseColor("#374151"))
                }

                statusBadge.text = if (s.isEnabled) "AKTIF" else "NONAKTIF"
                statusBadge.setTextColor(if (s.isEnabled) Color.parseColor("#22C55E") else Color.parseColor("#EF4444"))
                statusBadge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (s.isEnabled) Color.parseColor("#14532D") else Color.parseColor("#450A0A"))
                    setStroke(dp(1), if (s.isEnabled) Color.parseColor("#22C55E") else Color.parseColor("#EF4444"))
                }

                btnToggleActive.text = if (s.isEnabled) "HENTIKAN" else "AKTIFKAN"
                btnToggleActive.setTextColor(Color.WHITE)
                btnToggleActive.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (s.isEnabled) Color.parseColor("#DC2626") else Color.parseColor("#EAB308"))
                }
                updateFixedBottomDockUi()
            }
        }
    }

    private fun showNumberInputDialog(
        service: FloatingControlService,
        title: String,
        currentVal: Int,
        min: Int,
        max: Int,
        unitLabel: String,
        chips: List<Int>,
        onConfirm: (Int) -> Unit
    ) = with(service) {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        val etInput = EditText(this).apply {
            setText(currentVal.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
        }
        root.addView(etInput)

        val tvRange = TextView(this).apply {
            text = "Batas nilai: $min s/d $max $unitLabel"
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(tvRange)

        val chipsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        chips.forEach { chipVal ->
            val chip = TextView(this).apply {
                text = "$chipVal"
                textSize = 9.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (chipVal == currentVal) Color.parseColor("#2563EB") else Color.parseColor("#334155"))
                }
                setPadding(dp(8), dp(4), dp(8), dp(4))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    etInput.setText(chipVal.toString())
                }
            }
            chipsLayout.addView(chip)
        }
        root.addView(chipsLayout)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setView(root)
            .setPositiveButton("Simpan") { d, _ ->
                val v = etInput.text.toString().trim().toIntOrNull() ?: currentVal
                val safe = v.coerceIn(min, max)
                onConfirm(safe)
                d.dismiss()
            }
            .setNegativeButton("Batal") { d, _ -> d.dismiss() }
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
    }

    private fun showKeywordsInputDialog(
        service: FloatingControlService,
        currentKeywords: String,
        onConfirm: (String) -> Unit
    ) = with(service) {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        val tvInfo = TextView(this).apply {
            text = "Pisahkan tiap kata kunci dengan koma (,). Komentar chat yang mengandung kata ini akan mendapat bonus huruf:"
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(tvInfo)

        val etInput = EditText(this).apply {
            setText(currentKeywords)
            setTextColor(Color.WHITE)
            textSize = 11f
            minLines = 3
            maxLines = 5
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
        }
        root.addView(etInput)

        val sampleWords = listOf("aamiin", "khatam", "alhamdulillah", "masyaallah", "berkah", "bismillah", "sehat selalu", "lancar rezeki")
        val chipsScroll = HorizontalScrollView(this).apply {
            setPadding(0, dp(6), 0, dp(6))
        }
        val chipsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        sampleWords.forEach { word ->
            val chip = TextView(this).apply {
                text = "+ $word"
                textSize = 8.0f
                setTextColor(Color.parseColor("#FBBF24"))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#334155"))
                }
                setPadding(dp(6), dp(3), dp(6), dp(3))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                layoutParams = lp
                setOnClickListener {
                    val cur = etInput.text.toString().trim()
                    if (!cur.lowercase().contains(word.lowercase())) {
                        val updated = if (cur.isBlank()) word else "$cur, $word"
                        etInput.setText(updated)
                    }
                }
            }
            chipsLayout.addView(chip)
        }
        chipsScroll.addView(chipsLayout)
        root.addView(chipsScroll)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("🤲 Edit Kata Kunci Doa & Chat")
            .setView(root)
            .setPositiveButton("Simpan") { d, _ ->
                val text = etInput.text.toString().trim()
                onConfirm(text)
                Toast.makeText(this@with, "Kata kunci doa diperbarui! 🤲", Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNeutralButton("↺ Standar") { d, _ ->
                val standard = "aamiin, amin, khatam, alhamdulillah, masyaallah, barakallah, bismillah, subhanallah"
                onConfirm(standard)
                Toast.makeText(this@with, "Kata kunci doa di-reset ke standar", Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton("Batal") { d, _ -> d.dismiss() }
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
    }
}
