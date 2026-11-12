package com.tajasb.floating.tabs

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tajasb.FloatingControlService
import com.tajasb.floating.components.FloatingUiWidgets
import com.tajasb.game.InteractiveTugOfWarEngine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * TugOfWarTabBuilder:
 * Builder modular untuk Tab 4 Sub-Mode: Game Interaktif Tarik Tambang (Duel Kubu).
 * Menyediakan kontrol live status permainan, papan skor & ronde, pergeseran tali,
 * preset nama tim, tombol simulasi uji coba real-time, serta penyesuaian posisi & zoom.
 */
object TugOfWarTabBuilder {

    private fun applyOverlayWindowType(dialog: AlertDialog) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
    }

    fun populateTugOfWarTab(
        service: FloatingControlService,
        tabNotifContainer: LinearLayout,
        dp: (Int) -> Int,
        createStepperBtn: (String, () -> Unit) -> TextView,
        createPresetChip: (String, () -> Unit) -> TextView
    ) = with(service) {
        val engine = InteractiveTugOfWarEngine.getInstance(this)

        val towContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerModeTugOfWar = towContainer

        // =========================================================================
        // 1. KARTU UTAMA: STATUS SAKLAR GAME & PAPAN SKOR
        // =========================================================================
        val mainCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#06B6D4"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val headerTitle = TextView(this).apply {
            text = "⚔️ Tarik Tambang Interaktif"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(headerTitle)

        // Tombol Saklar ON/OFF Utama
        val isInitActive = engine.state.value.isEnabled
        val btnToggleGame = TextView(this).apply {
            text = if (isInitActive) "GAME: AKTIF" else "GAME: OFF"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isInitActive) Color.parseColor("#059669") else Color.parseColor("#334155"))
            }
            setTextColor(Color.WHITE)
            setOnClickListener {
                val next = engine.toggleEnabled()
                text = if (next) "GAME: AKTIF" else "GAME: OFF"
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(if (next) Color.parseColor("#059669") else Color.parseColor("#334155"))
                }
                Toast.makeText(
                    service,
                    if (next) "⚔️ Tarik Tambang Aktif di Kamera Virtual!" else "Tarik Tambang Dinonaktifkan",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        headerRow.addView(btnToggleGame)
        mainCard.addView(headerRow)

        // Tombol 1-Klik Buka Layar Penuh Arena Game (Khusus Device 2 Mode Game Seluler TikTok)
        val btnOpenFullArena = TextView(this).apply {
            text = "📺 Buka Layar Penuh Arena (Mode Game Seluler 2-HP)"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0284C7"))
            }
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(5)
            }
            setOnClickListener {
                try {
                    engine.setArenaDisplayActive(true)
                    val launchIntent = android.content.Intent(service, com.example.MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("extra_open_game_arena", true)
                    }
                    startActivity(launchIntent)
                } catch (_: Throwable) {}
            }
        }
        mainCard.addView(btnOpenFullArena)

        // Tampilan Papan Skor & Tali Real-Time
        val scoreCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(4)
            }
            layoutParams = lp
        }

        val tvRoundAndScore = TextView(this).apply {
            text = "Ronde 1  |  COWOK [ 0 ] - [ 0 ] CEWEK"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FDE047"))
        }
        scoreCard.addView(tvRoundAndScore)

        val tvRopeProgress = TextView(this).apply {
            text = "Tali: 50% (COWOK) ◄══🔴══► 50% (CEWEK)"
            textSize = 9.5f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(2), 0, 0)
        }
        scoreCard.addView(tvRopeProgress)

        val tvLastAction = TextView(this).apply {
            text = "⚡ Menunggu interaksi penonton..."
            textSize = 8.5f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, 0)
        }
        scoreCard.addView(tvLastAction)
        mainCard.addView(scoreCard)

        // Baris Kontrol Skor: Reset Tali, Ronde Baru, Reset Skor
        val scoreActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        fun createActionBtn(label: String, colorHex: String, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(4), dp(6), dp(4))
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor(colorHex))
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
                setOnClickListener { onClick() }
            }
        }

        scoreActionsRow.addView(createActionBtn("↺ Reset Tali", "#0284C7") {
            engine.resetRope()
            Toast.makeText(service, "Tali dikembalikan ke tengah", Toast.LENGTH_SHORT).show()
        })

        scoreActionsRow.addView(createActionBtn("🏆 Ronde Baru", "#D97706") {
            engine.startNewRound()
            Toast.makeText(service, "Ronde baru dimulai!", Toast.LENGTH_SHORT).show()
        })

        scoreActionsRow.addView(createActionBtn("0️⃣ Reset Skor", "#DC2626") {
            engine.resetScores()
            Toast.makeText(service, "Skor dan ronde direset", Toast.LENGTH_SHORT).show()
        })

        mainCard.addView(scoreActionsRow)
        towContainer.addView(mainCard)

        // =========================================================================
        // 2. KARTU PRESET & KUSTOMISASI NAMA TIM
        // =========================================================================
        val teamCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val teamCardTitle = TextView(this).apply {
            text = "Pilihan Tema Kubu Lawan:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
        }
        teamCard.addView(teamCardTitle)

        val presetRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(2))
        }

        presetRow1.addView(createPresetChip("👦 Cowok vs 👧 Cewek") {
            engine.setTeamNames("COWOK", "CEWEK", "#0284C7", "#E11D48")
            Toast.makeText(service, "Tema diatur: COWOK vs CEWEK", Toast.LENGTH_SHORT).show()
        }.apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(3)
            }
        })

        presetRow1.addView(createPresetChip("🔵 Biru vs 🔴 Merah") {
            engine.setTeamNames("TIM BIRU", "TIM MERAH", "#2563EB", "#DC2626")
            Toast.makeText(service, "Tema diatur: TIM BIRU vs TIM MERAH", Toast.LENGTH_SHORT).show()
        }.apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(3)
            }
        })
        teamCard.addView(presetRow1)

        val presetRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(4))
        }

        presetRow2.addView(createPresetChip("💔 Jomblo vs 💍 Pacaran") {
            engine.setTeamNames("JOMBLO", "PACARAN", "#7C3AED", "#EC4899")
            Toast.makeText(service, "Tema diatur: JOMBLO vs PACARAN", Toast.LENGTH_SHORT).show()
        }.apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(3)
            }
        })

        presetRow2.addView(createPresetChip("⬅️ Kiri vs ➡️ Kanan") {
            engine.setTeamNames("KIRI", "KANAN", "#059669", "#D97706")
            Toast.makeText(service, "Tema diatur: KIRI vs KANAN", Toast.LENGTH_SHORT).show()
        }.apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(3)
            }
        })
        teamCard.addView(presetRow2)

        // Tombol Ubah Nama Kustom
        val btnCustomTeamNames = TextView(this).apply {
            text = "✏️ Kustom Nama Tim (Ubah Bebas)"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#334155"))
            }
            setOnClickListener {
                showCustomTeamNamesDialog(service, engine, dp)
            }
        }
        teamCard.addView(btnCustomTeamNames)
        towContainer.addView(teamCard)

        // =========================================================================
        // 3. KARTU SIMULATOR UJI COBA LANGSUNG (ANTI-MENUNGGU LIVE ASLI)
        // =========================================================================
        val simCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val simTitle = TextView(this).apply {
            text = "Uji Coba Tarikan (Tes Simulator):"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
        }
        simCard.addView(simTitle)

        // Baris 1: Chat A & Chat B
        val simRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(2))
        }

        simRow1.addView(createActionBtn("💬 Chat Tim A (+10)", "#0284C7") {
            engine.manualPull("A", 10.0f)
        })
        simRow1.addView(createActionBtn("💬 Chat Tim B (+10)", "#E11D48") {
            engine.manualPull("B", 10.0f)
        })
        simCard.addView(simRow1)

        // Baris 2: Gift Sawer A & Gift Sawer B
        val simRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }

        simRow2.addView(createActionBtn("🎁 Sawer Donat A (+300)", "#0369A1") {
            engine.manualPull("A", 300.0f)
        })
        simRow2.addView(createActionBtn("🎁 Sawer Donat B (+300)", "#BE123C") {
            engine.manualPull("B", 300.0f)
        })
        simCard.addView(simRow2)
        towContainer.addView(simCard)

        // =========================================================================
        // 4. KARTU POSISI & UKURAN ZOOM DI KAMERA
        // =========================================================================
        val transformCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val transformTitle = TextView(this).apply {
            text = "Posisi & Ukuran di Kamera:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
        }
        transformCard.addView(transformTitle)

        val posRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }

        val tvPosLabel = TextView(this).apply {
            text = "Posisi Y: 28%"
            textSize = 9.5f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        posRow.addView(tvPosLabel)

        posRow.addView(createStepperBtn("⬆️") {
            val cur = engine.state.value
            val nextY = (cur.posYpct - 0.03f).coerceIn(0.05f, 0.90f)
            engine.setTransform(cur.posXpct, nextY, cur.overlayScale)
            tvPosLabel.text = "Posisi Y: ${(nextY * 100).toInt()}%"
        })

        posRow.addView(createStepperBtn("⬇️") {
            val cur = engine.state.value
            val nextY = (cur.posYpct + 0.03f).coerceIn(0.05f, 0.90f)
            engine.setTransform(cur.posXpct, nextY, cur.overlayScale)
            tvPosLabel.text = "Posisi Y: ${(nextY * 100).toInt()}%"
        })
        transformCard.addView(posRow)

        val zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }

        val tvZoomLabel = TextView(this).apply {
            text = "Ukuran: 1.0x"
            textSize = 9.5f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        zoomRow.addView(tvZoomLabel)

        zoomRow.addView(createStepperBtn("➖") {
            val cur = engine.state.value
            val nextScale = (cur.overlayScale - 0.1f).coerceIn(0.6f, 2.0f)
            engine.setTransform(cur.posXpct, cur.posYpct, nextScale)
            tvZoomLabel.text = "Ukuran: ${String.format(java.util.Locale.US, "%.1fx", nextScale)}"
        })

        zoomRow.addView(createStepperBtn("➕") {
            val cur = engine.state.value
            val nextScale = (cur.overlayScale + 0.1f).coerceIn(0.6f, 2.0f)
            engine.setTransform(cur.posXpct, cur.posYpct, nextScale)
            tvZoomLabel.text = "Ukuran: ${String.format(java.util.Locale.US, "%.1fx", nextScale)}"
        })
        transformCard.addView(zoomRow)
        towContainer.addView(transformCard)

        // =========================================================================
        // 5. KARTU GAME 2: RAJA SINGGASANA (REBUTAN KURSI SULTAN)
        // =========================================================================
        val kotEngine = com.tajasb.game.InteractiveKingOfThroneEngine.getInstance(this)
        val kotCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#FACC15"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val kotHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val kotTitle = TextView(this).apply {
            text = "👑 Game 2: Raja Singgasana"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FACC15"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        kotHeaderRow.addView(kotTitle)

        val isKotInitActive = kotEngine.state.value.isEnabled
        val btnToggleKot = TextView(this).apply {
            text = if (isKotInitActive) "VCAM: AKTIF" else "VCAM: OFF"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isKotInitActive) Color.parseColor("#059669") else Color.parseColor("#334155"))
            }
            setTextColor(Color.WHITE)
            setOnClickListener {
                val next = kotEngine.toggleEnabled()
                Toast.makeText(
                    service,
                    if (next) "👑 Raja Singgasana Aktif di Kamera Virtual!" else "Raja Singgasana Dinonaktifkan",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        kotHeaderRow.addView(btnToggleKot)
        kotCard.addView(kotHeaderRow)

        val btnOpenKotArena = TextView(this).apply {
            text = "📺 Buka Arena Penuh Raja Singgasana (Mode 2-HP)"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#D97706"))
            }
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(5)
            }
            setOnClickListener {
                try {
                    engine.setArenaDisplayActive(false)
                    kotEngine.setArenaDisplayActive(true)
                    val launchIntent = android.content.Intent(service, com.example.MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("extra_open_kot_arena", true)
                    }
                    startActivity(launchIntent)
                } catch (_: Throwable) {}
            }
        }
        kotCard.addView(btnOpenKotArena)

        val tvKotStatus = TextView(this).apply {
            text = "🪑 Singgasana Kosong | 🛡️ Perisai: 0/500 HP"
            textSize = 9.5f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FEF08A"))
            setPadding(0, dp(5), 0, dp(3))
        }
        kotCard.addView(tvKotStatus)

        val kotActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }
        kotActionsRow.addView(createActionBtn("⚔️ Serang -50", "#E11D48") {
            kotEngine.manualAction("Penantang", 50f, "Serangan Cepat")
        })
        kotActionsRow.addView(createActionBtn("🛡️ +150 HP", "#059669") {
            kotEngine.boostKingShield(150f)
        })
        kotActionsRow.addView(createActionBtn("🪑 Reset", "#0284C7") {
            kotEngine.resetThroneAndRecords()
        })
        kotCard.addView(kotActionsRow)
        towContainer.addView(kotCard)

        // =========================================================================
        // 6. KARTU GAME 3: BEYBATTLE ROYALE (GANGSING TEMPUR TIKTOK LIVE)
        // =========================================================================
        val beyEngine = com.tajasb.game.InteractiveBeyBattleEngine.getInstance(this)
        val beyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#06B6D4"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val beyHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val beyTitle = TextView(this).apply {
            text = "🌀 Game 3: BeyBattle Royale"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#06B6D4"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        beyHeaderRow.addView(beyTitle)

        val isBeyInitActive = beyEngine.state.value.isEnabled
        val btnToggleBey = TextView(this).apply {
            text = if (isBeyInitActive) "VCAM: AKTIF" else "VCAM: OFF"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isBeyInitActive) Color.parseColor("#059669") else Color.parseColor("#334155"))
            }
            setTextColor(Color.WHITE)
            setOnClickListener {
                val cur = beyEngine.state.value.isEnabled
                val next = !cur
                beyEngine.setEnabled(next)
                Toast.makeText(
                    service,
                    if (next) "🌀 BeyBattle Royale Aktif di Kamera Virtual!" else "BeyBattle Royale Dinonaktifkan",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        beyHeaderRow.addView(btnToggleBey)
        beyCard.addView(beyHeaderRow)

        val btnOpenBeyArena = TextView(this).apply {
            text = "📺 Buka Arena Penuh BeyBattle (Mode 2-HP 60 FPS)"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0891B2"))
            }
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(5)
            }
            setOnClickListener {
                try {
                    engine.setArenaDisplayActive(false)
                    kotEngine.setArenaDisplayActive(false)
                    beyEngine.setArenaDisplayActive(true)
                    val launchIntent = android.content.Intent(service, com.example.MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("extra_open_bey_arena", true)
                    }
                    startActivity(launchIntent)
                } catch (_: Throwable) {}
            }
        }
        beyCard.addView(btnOpenBeyArena)

        val tvBeyStatus = TextView(this).apply {
            text = "🛡️ 0 Gangsing Aktif | Ronde 1"
            textSize = 9.5f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#67E8F9"))
            setPadding(0, dp(5), 0, dp(3))
        }
        beyCard.addView(tvBeyStatus)

        val beyActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }
        beyActionsRow.addView(createActionBtn("+ Bot", "#0284C7") {
            beyEngine.spawnBot("Bot_${System.currentTimeMillis() % 1000}")
        })
        beyActionsRow.addView(createActionBtn("⚡ Like", "#F59E0B") {
            beyEngine.onLikeEvent("Tester", 15)
        })
        beyActionsRow.addView(createActionBtn("💬 Dash", "#8B5CF6") {
            beyEngine.onChatEvent("Tester", "TABRAK!")
        })
        beyActionsRow.addView(createActionBtn("🎁 Titan", "#EF4444") {
            beyEngine.onGiftEvent("Sultan", "Parfum", 1, 15L)
        })
        beyActionsRow.addView(createActionBtn("↺ Reset", "#10B981") {
            beyEngine.resetRound()
        })
        beyCard.addView(beyActionsRow)

        val beyDimensionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(1))
        }
        val tvDimensionsLabel = TextView(this).apply {
            text = "📐 P: 100% x L: 100%"
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(2), 0, dp(2), 0)
        }
        beyDimensionsRow.addView(tvDimensionsLabel)
        beyDimensionsRow.addView(createActionBtn("1:1", "#0284C7") {
            beyEngine.setArenaPreset("1:1")
        })
        beyDimensionsRow.addView(createActionBtn("4:3", "#0891B2") {
            beyEngine.setArenaPreset("4:3")
        })
        beyDimensionsRow.addView(createActionBtn("16:9", "#059669") {
            beyEngine.setArenaPreset("16:9")
        })
        beyDimensionsRow.addView(createActionBtn("9:16", "#7C3AED") {
            beyEngine.setArenaPreset("9:16")
        })
        beyCard.addView(beyDimensionsRow)
        towContainer.addView(beyCard)

        // =========================================================================
        // 7. KARTU GAME 4: MINI TROOPER ROYALE (PERANG PRAJURIT CHIBI TIKTOK LIVE)
        // =========================================================================
        val trooperEngine = com.tajasb.game.InteractiveTrooperRoyaleEngine.getInstance(this)
        val trooperCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val trooperHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val trooperTitle = TextView(this).apply {
            text = "🎖️ Game 4: Mini Trooper Royale"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        trooperHeaderRow.addView(trooperTitle)

        val isTrooperInitActive = trooperEngine.state.value.isEnabled
        val btnToggleTrooper = TextView(this).apply {
            text = if (isTrooperInitActive) "VCAM: AKTIF" else "VCAM: OFF"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(if (isTrooperInitActive) Color.parseColor("#059669") else Color.parseColor("#334155"))
            }
            setTextColor(Color.WHITE)
            setOnClickListener {
                val cur = trooperEngine.state.value.isEnabled
                val next = !cur
                trooperEngine.setEnabled(next)
                Toast.makeText(
                    service,
                    if (next) "🎖️ Mini Trooper Royale Aktif di Kamera Virtual!" else "Mini Trooper Royale Dinonaktifkan",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        trooperHeaderRow.addView(btnToggleTrooper)
        trooperCard.addView(trooperHeaderRow)

        val btnOpenTrooperArena = TextView(this).apply {
            text = "📺 Buka Arena Penuh Prajurit (Mode 2-HP 60 FPS)"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#15803D"))
            }
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(5)
            }
            setOnClickListener {
                try {
                    engine.setArenaDisplayActive(false)
                    kotEngine.setArenaDisplayActive(false)
                    beyEngine.setArenaDisplayActive(false)
                    trooperEngine.setArenaDisplayActive(true)
                    val launchIntent = android.content.Intent(service, com.example.MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("extra_open_trooper_arena", true)
                    }
                    startActivity(launchIntent)
                } catch (_: Throwable) {}
            }
        }
        trooperCard.addView(btnOpenTrooperArena)

        val tvTrooperStatus = TextView(this).apply {
            text = "👥 0 Prajurit Aktif | Ronde 1"
            textSize = 9.5f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#86EFAC"))
            setPadding(0, dp(5), 0, dp(3))
        }
        trooperCard.addView(tvTrooperStatus)

        val trooperActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }
        trooperActionsRow.addView(createActionBtn("+5 Bot", "#0284C7") {
            trooperEngine.spawnBotTroopers(5)
        })
        trooperActionsRow.addView(createActionBtn("❤️ Tembak", "#F59E0B") {
            trooperEngine.onLikeEvent("Tester", 10, "")
        })
        trooperActionsRow.addView(createActionBtn("💬 Hindar", "#8B5CF6") {
            trooperEngine.onChatEvent("Tester", "DODGE!", "")
        })
        trooperActionsRow.addView(createActionBtn("🎁 Bazooka", "#EF4444") {
            trooperEngine.simulateGiftTest()
        })
        trooperActionsRow.addView(createActionBtn("↺ Reset", "#10B981") {
            trooperEngine.resetGame()
        })
        trooperCard.addView(trooperActionsRow)
        towContainer.addView(trooperCard)

        tabNotifContainer.addView(towContainer)

        // Sinkronisasi Real-Time State ke UI Floating Control
        serviceScope.launch {
            engine.state.collectLatest { s ->
                btnToggleGame.text = if (s.isEnabled) "GAME: AKTIF" else "GAME: OFF"
                btnToggleGame.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(if (s.isEnabled) Color.parseColor("#059669") else Color.parseColor("#334155"))
                }

                tvRoundAndScore.text = "Ronde ${s.currentRound}  |  ${s.teamAName} [ ${s.teamAWins} ] - [ ${s.teamBWins} ] ${s.teamBName}"
                tvRopeProgress.text = "Tali: ${s.teamAPercent}% (${s.teamAName}) ◄══🔴══► ${s.teamBPercent}% (${s.teamBName})"
                tvLastAction.text = "⚡ ${s.lastActionText}"
                tvPosLabel.text = "Posisi Y: ${(s.posYpct * 100).toInt()}%"
                tvZoomLabel.text = "Ukuran: ${String.format(java.util.Locale.US, "%.1fx", s.overlayScale)}"
            }
        }

        serviceScope.launch {
            kotEngine.state.collectLatest { ks ->
                btnToggleKot.text = if (ks.isEnabled) "VCAM: AKTIF" else "VCAM: OFF"
                btnToggleKot.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(if (ks.isEnabled) Color.parseColor("#059669") else Color.parseColor("#334155"))
                }
                val kingLabel = ks.currentKingName.ifBlank { "Kosong" }
                tvKotStatus.text = "👑 Raja: $kingLabel | 🛡️ Perisai: ${ks.throneShieldHp.toInt()}/${ks.maxThroneShieldHp.toInt()} HP"
            }
        }

        serviceScope.launch {
            beyEngine.state.collectLatest { bs ->
                btnToggleBey.text = if (bs.isEnabled) "VCAM: AKTIF" else "VCAM: OFF"
                btnToggleBey.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(if (bs.isEnabled) Color.parseColor("#059669") else Color.parseColor("#334155"))
                }
                val champStr = if (bs.lastChampion != null) " | 👑 Juara: ${bs.lastChampion.name}" else ""
                tvBeyStatus.text = "🛡️ ${bs.activePlayersCount} Gangsing Aktif | Ronde ${bs.currentRound}$champStr"
                tvDimensionsLabel.text = "📐 P: ${(bs.arenaHeightScale * 100).toInt()}% x L: ${(bs.arenaWidthScale * 100).toInt()}%"
            }
        }

        serviceScope.launch {
            trooperEngine.state.collectLatest { ts ->
                btnToggleTrooper.text = if (ts.isEnabled) "VCAM: AKTIF" else "VCAM: OFF"
                btnToggleTrooper.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    setColor(if (ts.isEnabled) Color.parseColor("#059669") else Color.parseColor("#334155"))
                }
                val top1Str = if (ts.top5List.isNotEmpty()) " | 👑 Top: ${ts.top5List.first().name}" else ""
                tvTrooperStatus.text = "👥 ${ts.totalAliveCount}/${ts.totalPlayerCount} Prajurit | R-${ts.currentRound}$top1Str"
            }
        }
    }

    private fun showCustomTeamNamesDialog(
        context: FloatingControlService,
        engine: InteractiveTugOfWarEngine,
        dp: (Int) -> Int
    ) {
        val s = engine.state.value
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        val tvTitle = TextView(context).apply {
            text = "Kustom Nama Kubu Tarik Tambang"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(tvTitle)

        val etTeamA = EditText(context).apply {
            hint = "Nama Tim Kiri (contoh: COWOK)"
            setText(s.teamAName)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#0284C7"))
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        container.addView(etTeamA)

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8))
        }
        container.addView(spacer)

        val etTeamB = EditText(context).apply {
            hint = "Nama Tim Kanan (contoh: CEWEK)"
            setText(s.teamBName)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#E11D48"))
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        container.addView(etTeamB)

        val dialog = AlertDialog.Builder(context)
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val nameA = etTeamA.text.toString().trim()
                val nameB = etTeamB.text.toString().trim()
                engine.setTeamNames(nameA, nameB)
                Toast.makeText(context, "Nama kubu berhasil disimpan!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .create()

        applyOverlayWindowType(dialog)
        dialog.show()
    }
}
