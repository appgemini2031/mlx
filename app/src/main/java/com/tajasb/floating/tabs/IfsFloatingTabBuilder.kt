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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tajasb.FloatingControlService
import com.tajasb.infinity.IfsManager
import com.tajasb.infinity.IfsSubPhase
import com.tajasb.infinity.IfsVideoMode
import com.tajasb.infinity.IfsVtPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * IfsFloatingTabBuilder:
 * Panel kontrol lengkap & ringkas untuk mode Infinity System (IFS) di Floating Window.
 * Memuat seluruh kontrol eksekusi live, latihan/tes komentar, serta pengaturan Slot & Loop
 * (SV1, SV2, SV3, dan SV4 Mode VS) langsung di Floating Control.
 */
object IfsFloatingTabBuilder {

    private fun applyOverlayWindowType(dialog: AlertDialog) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
    }

    fun showSingleVideoRoleOverlayDialog(
        service: FloatingControlService,
        videoItem: com.tajasb.model.VideoItem,
        onUpdated: () -> Unit = {}
    ) = with(service) {
        val ifsManager = IfsManager.getInstance(applicationContext)
        val mode = ifsManager.videoMode.value
        val options = mutableListOf<Pair<String, () -> Unit>>()

        when (mode) {
            IfsVideoMode.SV1 -> {
                val vtm = ifsManager.vtmPackage.value
                val vts = ifsManager.vtPackages.value
                options.add("👑 ABA • ABB (Video Awal)" to { ifsManager.setSlotVideo(vtm.vtNumber, IfsSubPhase.ABB, videoItem.id) })
                options.add("👑 ABA • BL (Loop Standby)" to { ifsManager.setSlotVideo(vtm.vtNumber, IfsSubPhase.BL, videoItem.id) })
                options.add("👑 ABA • BBT (Transisi Keluar)" to { ifsManager.setSlotVideo(vtm.vtNumber, IfsSubPhase.BBT, videoItem.id) })
                vts.forEach { pkg ->
                    val code = pkg.getShortCode(mode)
                    options.add("🎬 $code (Video Konten 1x)" to { ifsManager.setSlotVideo(pkg.vtNumber, IfsSubPhase.CONTENT, videoItem.id) })
                }
                options.add("➕ Buat VT${vts.size + 1} Baru dengan Video Ini" to { ifsManager.addVtPackage(videoItem.id) })
            }
            IfsVideoMode.SV2 -> {
                val pms = ifsManager.vtPackages.value
                pms.forEach { pkg ->
                    val code = pkg.getShortCode(mode)
                    options.add("👑 $code • ABB (Awal)" to { ifsManager.setSlotVideo(pkg.vtNumber, IfsSubPhase.ABB, videoItem.id) })
                    options.add("👑 $code • BL (Loop Utama)" to { ifsManager.setSlotVideo(pkg.vtNumber, IfsSubPhase.BL, videoItem.id) })
                    options.add("👑 $code • BBT (Transisi)" to { ifsManager.setSlotVideo(pkg.vtNumber, IfsSubPhase.BBT, videoItem.id) })
                }
                options.add("➕ Buat PM${pms.size + 1} Baru dengan Video Ini" to { ifsManager.addVtPackage(videoItem.id) })
            }
            IfsVideoMode.SV3 -> {
                val isVl = ifsManager.sv3VlVideoId.value == videoItem.id
                val inVb = ifsManager.sv3VbVideoIds.value.contains(videoItem.id)
                options.add((if (isVl) "✓ 👑 Video VL (Loop Acak Utama)" else "👑 Jadikan Video VL (Loop Acak Utama)") to {
                    ifsManager.setSv3VlVideo(videoItem.id)
                })
                options.add((if (inVb) "❌ Hapus dari Grup VB (Bridge)" else "➕ Masukkan ke Grup VB (Bridge Acak)") to {
                    ifsManager.toggleSv3VbVideo(videoItem.id)
                })
            }
            IfsVideoMode.SV4 -> {
                val inL = ifsManager.sv4LVideoIds.value.contains(videoItem.id)
                options.add((if (inL) "❌ Keluarkan dari Bahan Variasi L" else "➕ Masukkan ke Bahan Variasi L (SV4)") to {
                    ifsManager.toggleSv4LVideo(videoItem.id)
                })
            }
        }
        options.add("🗑️ Lepas dari Semua Slot IFS" to { ifsManager.clearVideoFromAllSlots(videoItem.id) })

        val labels = options.map { it.first }.toTypedArray()
        try {
            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("♾️ Atur Slot IFS (${mode.name}): ${videoItem.name}")
                .setItems(labels) { d, which ->
                    options.getOrNull(which)?.second?.invoke()
                    onUpdated()
                    updateUiForConfig(repository.config.value)
                    d.dismiss()
                }
                .setNegativeButton("Tutup") { d, _ -> d.dismiss() }
                .create()
            applyOverlayWindowType(dialog)
            dialog.show()
        } catch (_: Throwable) {}
    }

    private fun showSlotVideoPickerOverlay(
        service: FloatingControlService,
        title: String,
        onSelectVideoId: (String?) -> Unit
    ) = with(service) {
        val playlist = repository.config.value.playlist.filterNot { repository.isRollingVsItem(it) }
        val labels = (playlist.mapIndexed { idx, v -> "#${idx + 1} ${v.name}" } + listOf("❌ Kosongkan Slot")).toTypedArray()
        try {
            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🎬 Pilih Video • $title")
                .setItems(labels) { d, which ->
                    if (which in playlist.indices) {
                        onSelectVideoId(playlist[which].id)
                    } else {
                        onSelectVideoId(null)
                    }
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }
                .create()
            applyOverlayWindowType(dialog)
            dialog.show()
        } catch (_: Throwable) {}
    }

    private fun showAudioAxPickerOverlay(
        service: FloatingControlService,
        pkg: IfsVtPackage,
        mode: IfsVideoMode
    ) = with(service) {
        val ifsManager = IfsManager.getInstance(applicationContext)
        val audioList = repository.config.value.audioPlaylist.filterNot { it.id.startsWith("vs_audio_rolling_") }
        val shortCode = pkg.getShortCode(mode)
        val axCode = pkg.getAxCode(mode)
        val labels = (listOf("🎵 Ikut Musik Latar (SR Acak / Tanpa $axCode)") +
            audioList.mapIndexed { idx, a ->
                val mark = if (pkg.linkedAudioId == a.id) "✓ " else ""
                "${mark}#${idx + 1} ${a.name}"
            }).toTypedArray()

        try {
            val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🔊 Pilih Audio $axCode • $shortCode")
                .setItems(labels) { d, which ->
                    if (which == 0) {
                        ifsManager.setLinkedAudio(pkg.vtNumber, null)
                        Toast.makeText(applicationContext, "🎵 $shortCode memakai musik latar SR", Toast.LENGTH_SHORT).show()
                    } else {
                        val chosen = audioList.getOrNull(which - 1)
                        if (chosen != null) {
                            ifsManager.setLinkedAudio(pkg.vtNumber, chosen.id)
                            Toast.makeText(applicationContext, "🔊 $axCode diatur ke: ${chosen.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    d.dismiss()
                }
                .setNegativeButton("Tutup") { d, _ -> d.dismiss() }
            if (!pkg.linkedAudioId.isNullOrBlank()) {
                builder.setPositiveButton("▶ Tes Bunyi $axCode") { d, _ ->
                    ifsManager.playLinkedAxForVt(pkg.vtNumber)
                    d.dismiss()
                }
            }
            val dialog = builder.create()
            applyOverlayWindowType(dialog)
            dialog.show()
        } catch (_: Throwable) {}
    }

    private fun showKeywordEditOverlay(
        service: FloatingControlService,
        pkg: IfsVtPackage,
        mode: IfsVideoMode
    ) = with(service) {
        val ifsManager = IfsManager.getInstance(applicationContext)
        val shortCode = pkg.getShortCode(mode)
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val input = EditText(this).apply {
            setText(pkg.triggerKeyword)
            hint = "Contoh: spill 1, baju merah, etalase 2"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 12f
            setSingleLine(true)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        try {
            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("💬 Kata Kunci Komentar $shortCode")
                .setView(input)
                .setPositiveButton("Simpan") { d, _ ->
                    val kw = input.text?.toString()?.trim() ?: ""
                    ifsManager.updateVtPackage(pkg.copy(triggerKeyword = kw))
                    Toast.makeText(applicationContext, "💬 Kata kunci $shortCode: ${kw.ifBlank { "(Kosong)" }}", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                }
                .setNeutralButton("Kosongkan") { d, _ ->
                    ifsManager.updateVtPackage(pkg.copy(triggerKeyword = ""))
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }
                .create()
            applyOverlayWindowType(dialog)
            dialog.show()
        } catch (_: Throwable) {}
    }

    private fun showMultiSelectVideosOverlay(
        service: FloatingControlService,
        title: String,
        selectedIds: List<String>,
        excludeId: String? = null,
        onSave: (List<String>) -> Unit
    ) = with(service) {
        val playlist = repository.config.value.playlist.filterNot {
            repository.isRollingVsItem(it) || (excludeId != null && it.id == excludeId)
        }
        if (playlist.isEmpty()) {
            Toast.makeText(applicationContext, "⚠️ Belum ada video yang diupload", Toast.LENGTH_SHORT).show()
            return@with
        }
        val labels = playlist.mapIndexed { idx, v -> "#${idx + 1} ${v.name}" }.toTypedArray()
        val checked = BooleanArray(playlist.size) { idx -> selectedIds.contains(playlist[idx].id) }
        val workingSet = selectedIds.toMutableList()

        try {
            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    val id = playlist[which].id
                    if (isChecked) {
                        if (!workingSet.contains(id)) workingSet.add(id)
                    } else {
                        workingSet.remove(id)
                    }
                }
                .setPositiveButton("Simpan") { d, _ ->
                    val validIds = workingSet.filter { id -> playlist.any { it.id == id } }
                    onSave(validIds)
                    d.dismiss()
                }
                .setNeutralButton("Pilih Semua") { d, _ ->
                    onSave(playlist.map { it.id })
                    d.dismiss()
                }
                .setNegativeButton("Kosongkan") { d, _ ->
                    onSave(emptyList())
                    d.dismiss()
                }
                .create()
            applyOverlayWindowType(dialog)
            dialog.show()
        } catch (_: Throwable) {}
    }

    fun buildIfsFloatingCard(
        service: FloatingControlService,
        dp: (Int) -> Int
    ): LinearLayout = with(service) {
        val ifsManager = IfsManager.getInstance(applicationContext)

        val panelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        // =========================================================
        // BARIS 1: KONTROL SEJAJAR (SV1 | SV2 | SV3 | SV4 | SC | ⏭️ Fase | 🔄 | !)
        // =========================================================
        val rowControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        fun createModePill(mode: IfsVideoMode): TextView {
            return TextView(this).apply {
                text = if (mode == IfsVideoMode.SV4) "SV4(VS)" else mode.name
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(7), dp(5), dp(7), dp(5))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(3)
                }
                layoutParams = lp
                setOnClickListener {
                    ifsManager.setVideoMode(mode)
                    if (mode == IfsVideoMode.SV4 && !ifsManager.ifsState.value.sv4VideoRunning) {
                        ifsManager.startSv4VideoEngine(resetCounter = false)
                    }
                    Toast.makeText(applicationContext, "♾️ Mode IFS: ${mode.title}", Toast.LENGTH_SHORT).show()
                    updateUiForConfig(repository.config.value)
                }
            }
        }

        val btnSv1 = createModePill(IfsVideoMode.SV1)
        val btnSv2 = createModePill(IfsVideoMode.SV2)
        val btnSv3 = createModePill(IfsVideoMode.SV3)
        val btnSv4 = createModePill(IfsVideoMode.SV4)
        rowControls.addView(btnSv1)
        rowControls.addView(btnSv2)
        rowControls.addView(btnSv3)
        rowControls.addView(btnSv4)

        // Saklar SC Ringkas Sejajar
        val btnToggleSc = TextView(this).apply {
            text = "💬 SC: OFF"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            setOnClickListener {
                val nextSc = !ifsManager.isScEnabled.value
                ifsManager.setScEnabled(nextSc)
                Toast.makeText(
                    applicationContext,
                    if (nextSc) "💬 Scan Komentar IFS: ON" else "🎙️ Scan Komentar IFS: OFF (Manual Host)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        rowControls.addView(btnToggleSc)

        // Tombol Maju Fase (⏭️)
        val btnNextPhase = TextView(this).apply {
            text = "⏭️ Fase"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E879F9"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#2E1065"))
                setStroke(dp(1), Color.parseColor("#A855F7"))
            }
            setOnClickListener {
                ifsManager.advancePhaseManually()
                Toast.makeText(applicationContext, "⏭️ Maju ke sub-fase / VS berikutnya", Toast.LENGTH_SHORT).show()
            }
        }
        rowControls.addView(btnNextPhase)

        // Tombol Reset Siklus (🔄)
        val btnResetIfs = TextView(this).apply {
            text = "🔄"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setOnClickListener {
                ifsManager.resetCycleToBeginning()
                Toast.makeText(applicationContext, "🔄 Reset ke awal siklus IFS", Toast.LENGTH_SHORT).show()
            }
        }
        rowControls.addView(btnResetIfs)

        // Tombol Info (!)
        val btnInfo = TextView(this).apply {
            text = "!"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(22))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#083344"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                Toast.makeText(
                    applicationContext,
                    "ℹ️ Panduan Host IFS:\n• SV1: ABA standby di BL + VT1..n.\n• SV2: PM1..n Paket Master ABA.\n• SV3: 1 Video VL loop acak ↔ Grup VB.\n• SV4(VS): Variasi Susunan L1..Ln acak + loop 1..2x digabung bergulir (VS1->VS2->VS3) & auto-hapus VS lama.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        rowControls.addView(btnInfo)
        panelContainer.addView(rowControls)

        // =========================================================
        // BARIS 2: STATUS REALTIME VIDEO & AUDIO + TOMBOL ATUR SLOT/LOOP ▾
        // =========================================================
        var isSlotSetupExpanded = false
        val slotSetupContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(6), dp(5), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#070C15"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(5), dp(7), dp(5))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#090D16"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
        }

        val tvPhaseStatus = TextView(this).apply {
            text = "● ABA • BL [Loop]"
            textSize = 9.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        statusRow.addView(tvPhaseStatus)

        val tvAudioStatus = TextView(this).apply {
            text = "🎵 SR Normal"
            textSize = 8.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#A7F3D0"))
            maxLines = 1
            setPadding(dp(4), 0, dp(4), 0)
        }
        statusRow.addView(tvAudioStatus)

        val btnToggleSlotSetup = TextView(this).apply {
            text = "⚙️ Atur Slot ▾"
            textSize = 8.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0C4A6E"))
                setStroke(dp(1), Color.parseColor("#0284C7"))
            }
        }
        statusRow.addView(btnToggleSlotSetup)
        panelContainer.addView(statusRow)

        // =========================================================
        // BARIS 3: BARIS TOMBOL TES / TRIGGER HOST LIVE (VTM, VT1, VT2...)
        // =========================================================
        val triggerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#09101D"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
        }

        val triggerHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        val tvTriggerHeader = TextView(this).apply {
            text = "⚡ TES / TRIGGER VT (KLIK UNTUK PUTAR):"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        triggerHeaderRow.addView(tvTriggerHeader)

        var isIfsCommentTestOpen = false
        val ifsCommentTestContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        val btnToggleIfsCommentTest = TextView(this).apply {
            text = "🧪 Uji Chat ▾"
            textSize = 8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E879F9"))
            setPadding(dp(5), dp(2), dp(5), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#3B0764"))
                setStroke(dp(1), Color.parseColor("#A855F7"))
            }
            setOnClickListener {
                isIfsCommentTestOpen = !isIfsCommentTestOpen
                ifsCommentTestContainer.visibility = if (isIfsCommentTestOpen) View.VISIBLE else View.GONE
                text = if (isIfsCommentTestOpen) "🧪 Uji Chat ▴" else "🧪 Uji Chat ▾"
            }
        }
        triggerHeaderRow.addView(btnToggleIfsCommentTest)
        triggerBox.addView(triggerHeaderRow)

        val triggerButtonsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val triggerButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        triggerButtonsScroll.addView(triggerButtonsRow)
        triggerBox.addView(triggerButtonsScroll)

        // Input Simulasi Uji Chat IFS
        val etIfsTestComment = EditText(this).apply {
            hint = "Ketik komen tes IFS (cth: vt1 / etalase 1)..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 8.8f
            setSingleLine(true)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            layoutParams = lp
            setOnFocusChangeListener { _, hasFocus -> setPanelFocusable(hasFocus) }
        }
        ifsCommentTestContainer.addView(etIfsTestComment)

        val btnSendIfsTestComment = TextView(this).apply {
            text = "⚡ Uji SC"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#7E22CE"))
            }
            setOnClickListener {
                val txt = etIfsTestComment.text?.toString()?.trim() ?: ""
                if (txt.isNotBlank()) {
                    val matched = ifsManager.onCommentDetected("Penonton Tes", txt)
                    Toast.makeText(
                        applicationContext,
                        if (matched) "✅ Komen '$txt' memicu paket IFS!" else "ℹ️ Komen '$txt' dikirim (Pastikan SC: ON & kata kunci cocok)",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(applicationContext, "Ketik kata kunci komentar dulu", Toast.LENGTH_SHORT).show()
                }
            }
        }
        ifsCommentTestContainer.addView(btnSendIfsTestComment)
        triggerBox.addView(ifsCommentTestContainer)

        var isSv4DetailExpanded = false
        val tvSv4DetailList = TextView(this).apply {
            textSize = 8.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(5)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#04181D"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
        }
        triggerBox.addView(tvSv4DetailList)
        panelContainer.addView(triggerBox)
        panelContainer.addView(slotSetupContainer)

        fun rebuildSlotSetupContent(
            state: com.tajasb.infinity.IfsState,
            mode: IfsVideoMode,
            vtmPkg: IfsVtPackage,
            vtList: List<IfsVtPackage>
        ) {
            slotSetupContainer.removeAllViews()
            val playlist = repository.config.value.playlist.filterNot { repository.isRollingVsItem(it) }

            when (mode) {
                IfsVideoMode.SV4 -> {
                    val minL = ifsManager.sv4MinLoops.value
                    val maxL = ifsManager.sv4MaxLoops.value
                    val lIds = ifsManager.sv4LVideoIds.value

                    val loopRow = LinearLayout(this@with).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val tvLoopLbl = TextView(this@with).apply {
                        text = if (minL == maxL) "🎲 Loop L: ${minL}x" else "🎲 Loop L: ${minL}x..${maxL}x"
                        textSize = 8.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#A7F3D0"))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    loopRow.addView(tvLoopLbl)

                    fun createLoopPresetBtn(pMin: Int, pMax: Int): TextView {
                        val active = (minL == pMin && maxL == pMax)
                        val btnLabel = if (pMin == pMax) "${pMin}x" else "${pMin}..${pMax}x"
                        return TextView(this@with).apply {
                            text = btnLabel
                            textSize = 8f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (active) Color.BLACK else Color.WHITE)
                            setPadding(dp(6), dp(3), dp(6), dp(3))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { marginStart = dp(3) }
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(4).toFloat()
                                setColor(if (active) Color.parseColor("#00E5FF") else Color.parseColor("#1E293B"))
                            }
                            setOnClickListener {
                                ifsManager.setSv4LoopRange(pMin, pMax)
                                val toastRange = if (pMin == pMax) "${pMin}x" else "${pMin}x s/d ${pMax}x"
                                Toast.makeText(applicationContext, "🎲 Loop SV4 diatur ke $toastRange", Toast.LENGTH_SHORT).show()
                                rebuildSlotSetupContent(ifsManager.ifsState.value, mode, vtmPkg, vtList)
                            }
                        }
                    }
                    loopRow.addView(createLoopPresetBtn(1, 1))
                    loopRow.addView(createLoopPresetBtn(1, 2))
                    loopRow.addView(createLoopPresetBtn(1, 3))
                    loopRow.addView(createLoopPresetBtn(2, 3))
                    slotSetupContainer.addView(loopRow)

                    val btnPickL = TextView(this@with).apply {
                        val effCount = if (lIds.isNotEmpty()) lIds.size else playlist.size
                        text = "☑️ Pilih Video Bahan L ($effCount Video Aktif) ▾"
                        textSize = 8.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#00E5FF"))
                        gravity = Gravity.CENTER
                        setPadding(dp(6), dp(5), dp(6), dp(5))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(4) }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(5).toFloat()
                            setColor(Color.parseColor("#083344"))
                            setStroke(dp(1), Color.parseColor("#00E5FF"))
                        }
                        setOnClickListener {
                            val currentL = if (lIds.isNotEmpty()) lIds else playlist.map { it.id }
                            showMultiSelectVideosOverlay(
                                service = this@with,
                                title = "🧬 Pilih Video Sumber Variasi (L1..Ln)",
                                selectedIds = currentL
                            ) { newIds ->
                                ifsManager.setSv4LVideoIds(newIds)
                                Toast.makeText(applicationContext, "🧬 Bahan L diperbarui: ${newIds.size} video", Toast.LENGTH_SHORT).show()
                                rebuildSlotSetupContent(ifsManager.ifsState.value, mode, vtmPkg, vtList)
                            }
                        }
                    }
                    slotSetupContainer.addView(btnPickL)
                }

                IfsVideoMode.SV3 -> {
                    val vlId = ifsManager.sv3VlVideoId.value
                    val vlItem = playlist.firstOrNull { it.id == vlId } ?: playlist.firstOrNull()
                    val minL = ifsManager.sv3VlMinLoops.value
                    val maxL = ifsManager.sv3VlMaxLoops.value
                    val vbIds = ifsManager.sv3VbVideoIds.value

                    val btnPickVl = TextView(this@with).apply {
                        text = "👑 Video VL: ${vlItem?.name ?: "Belum ada video"} ▾"
                        textSize = 8.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#6EE7B7"))
                        setPadding(dp(6), dp(5), dp(6), dp(5))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(5).toFloat()
                            setColor(Color.parseColor("#064E3B"))
                            setStroke(dp(1), Color.parseColor("#10B981"))
                        }
                        setOnClickListener {
                            showSlotVideoPickerOverlay(this@with, "SV3 • Video VL Utama") { chosenId ->
                                ifsManager.setSv3VlVideo(chosenId)
                                rebuildSlotSetupContent(ifsManager.ifsState.value, mode, vtmPkg, vtList)
                            }
                        }
                    }
                    slotSetupContainer.addView(btnPickVl)

                    val loopRow = LinearLayout(this@with).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(4) }
                    }
                    val tvLoop = TextView(this@with).apply {
                        text = if (minL == maxL) "🎲 Loop VL: ${minL}x" else "🎲 Loop VL: ${minL}x..${maxL}x"
                        textSize = 8.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    loopRow.addView(tvLoop)

                    listOf(1 to 1, 1 to 3, 2 to 5, 3 to 7).forEach { (pMin, pMax) ->
                        val active = (minL == pMin && maxL == pMax)
                        val chip = TextView(this@with).apply {
                            text = if (pMin == pMax) "${pMin}x" else "${pMin}..${pMax}x"
                            textSize = 8f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (active) Color.BLACK else Color.WHITE)
                            setPadding(dp(6), dp(3), dp(6), dp(3))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { marginStart = dp(3) }
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(4).toFloat()
                                setColor(if (active) Color.parseColor("#00E5FF") else Color.parseColor("#1E293B"))
                            }
                            setOnClickListener {
                                ifsManager.setSv3VlLoopRange(pMin, pMax)
                                rebuildSlotSetupContent(ifsManager.ifsState.value, mode, vtmPkg, vtList)
                            }
                        }
                        loopRow.addView(chip)
                    }
                    slotSetupContainer.addView(loopRow)

                    val btnPickVb = TextView(this@with).apply {
                        text = "🌉 Pilih Grup VB (${vbIds.size} Video Bridge) ▾"
                        textSize = 8.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#E879F9"))
                        gravity = Gravity.CENTER
                        setPadding(dp(6), dp(5), dp(6), dp(5))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(4) }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(5).toFloat()
                            setColor(Color.parseColor("#2E1065"))
                            setStroke(dp(1), Color.parseColor("#A855F7"))
                        }
                        setOnClickListener {
                            showMultiSelectVideosOverlay(
                                service = this@with,
                                title = "🌉 Pilih Multi Video Grup VB (SV3)",
                                selectedIds = vbIds,
                                excludeId = vlItem?.id
                            ) { newIds ->
                                ifsManager.setSv3VbVideoIds(newIds)
                                Toast.makeText(applicationContext, "🌉 Grup VB: ${newIds.size} video", Toast.LENGTH_SHORT).show()
                                rebuildSlotSetupContent(ifsManager.ifsState.value, mode, vtmPkg, vtList)
                            }
                        }
                    }
                    slotSetupContainer.addView(btnPickVb)
                }

                else -> {
                    val headerRow = LinearLayout(this@with).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 0, 0, dp(3))
                    }
                    val tvSlotTitle = TextView(this@with).apply {
                        text = if (mode == IfsVideoMode.SV1) "📦 Daftar Paket ABA & VT1..n:" else "📦 Daftar Paket Master PM1..n:"
                        textSize = 8.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#94A3B8"))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    headerRow.addView(tvSlotTitle)

                    val btnAddPkg = TextView(this@with).apply {
                        text = if (mode == IfsVideoMode.SV2) "➕ PM${vtList.size + 1}" else "➕ VT${vtList.size + 1}"
                        textSize = 8f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#E879F9"))
                        setPadding(dp(6), dp(3), dp(6), dp(3))
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(4).toFloat()
                            setColor(Color.parseColor("#3B0764"))
                            setStroke(dp(1), Color.parseColor("#A855F7"))
                        }
                        setOnClickListener {
                            ifsManager.addVtPackage()
                            Toast.makeText(applicationContext, "➕ Paket baru ditambahkan", Toast.LENGTH_SHORT).show()
                        }
                    }
                    headerRow.addView(btnAddPkg)
                    slotSetupContainer.addView(headerRow)

                    val pkgs = if (mode == IfsVideoMode.SV1) listOf(vtmPkg) + vtList else vtList
                    pkgs.forEach { pkg ->
                        val shortCode = pkg.getShortCode(mode)
                        val axCode = pkg.getAxCode(mode)
                        val requires3 = pkg.requiresThreeVideos(mode)

                        val pkgCard = LinearLayout(this@with).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(5), dp(4), dp(5), dp(4))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = dp(3) }
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(5).toFloat()
                                setColor(Color.parseColor("#0F172A"))
                                setStroke(dp(1), if (state.activeVtNumber == pkg.vtNumber) Color.parseColor("#00E5FF") else Color.parseColor("#334155"))
                            }
                        }

                        val topPkgRow = LinearLayout(this@with).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        val tvPkgName = TextView(this@with).apply {
                            text = if (pkg.isVtm) "👑 ABA Standby" else "🎬 $shortCode"
                            textSize = 8.5f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (pkg.isVtm) Color.parseColor("#FCD34D") else Color.parseColor("#00E5FF"))
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        topPkgRow.addView(tvPkgName)

                        if (!pkg.isVtm) {
                            val hasAx = !pkg.linkedAudioId.isNullOrBlank()
                            val btnAx = TextView(this@with).apply {
                                text = if (hasAx) "🔊 $axCode ✓" else "🎵 SR ▾"
                                textSize = 7.8f
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(if (hasAx) Color.parseColor("#FDE68A") else Color.parseColor("#A5B4FC"))
                                setPadding(dp(5), dp(2), dp(5), dp(2))
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply { marginEnd = dp(3) }
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.RECTANGLE
                                    cornerRadius = dp(4).toFloat()
                                    setColor(if (hasAx) Color.parseColor("#78350F") else Color.parseColor("#1E1B4B"))
                                }
                                setOnClickListener {
                                    showAudioAxPickerOverlay(this@with, pkg, mode)
                                }
                            }
                            topPkgRow.addView(btnAx)

                            val btnKw = TextView(this@with).apply {
                                val kw = pkg.triggerKeyword
                                text = if (kw.isNotBlank()) "💬 $kw" else "💬 +Komen"
                                textSize = 7.8f
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(Color.parseColor("#38BDF8"))
                                setPadding(dp(5), dp(2), dp(5), dp(2))
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply { marginEnd = dp(3) }
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.RECTANGLE
                                    cornerRadius = dp(4).toFloat()
                                    setColor(Color.parseColor("#0C4A6E"))
                                }
                                setOnClickListener {
                                    showKeywordEditOverlay(this@with, pkg, mode)
                                }
                            }
                            topPkgRow.addView(btnKw)

                            if (vtList.size > 1) {
                                val btnDelPkg = TextView(this@with).apply {
                                    text = "🗑️"
                                    textSize = 8.5f
                                    setPadding(dp(4), dp(1), dp(4), dp(1))
                                    setOnClickListener {
                                        ifsManager.deleteVtPackage(pkg.vtNumber)
                                        Toast.makeText(applicationContext, "🗑️ $shortCode dihapus", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                topPkgRow.addView(btnDelPkg)
                            }
                        }
                        pkgCard.addView(topPkgRow)

                        val slotsRow = LinearLayout(this@with).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = dp(3) }
                        }

                        fun createSlotBtn(phase: IfsSubPhase, label: String, currentVidId: String?): TextView {
                            val vName = playlist.firstOrNull { it.id == currentVidId }?.name
                            val shortVName = vName?.let { if (it.length > 10) it.take(8) + ".." else it } ?: "Pilih ▾"
                            return TextView(this@with).apply {
                                text = "$label: $shortVName"
                                textSize = 7.8f
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(if (vName != null) Color.WHITE else Color.parseColor("#94A3B8"))
                                gravity = Gravity.CENTER
                                setPadding(dp(4), dp(3), dp(4), dp(3))
                                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                    marginEnd = dp(2)
                                }
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.RECTANGLE
                                    cornerRadius = dp(4).toFloat()
                                    setColor(if (vName != null) Color.parseColor("#064E3B") else Color.parseColor("#1E293B"))
                                    setStroke(dp(1), if (vName != null) Color.parseColor("#10B981") else Color.parseColor("#334155"))
                                }
                                setOnClickListener {
                                    showSlotVideoPickerOverlay(this@with, "$shortCode • $label") { chosenId ->
                                        ifsManager.setSlotVideo(pkg.vtNumber, phase, chosenId)
                                    }
                                }
                            }
                        }

                        if (requires3) {
                            slotsRow.addView(createSlotBtn(IfsSubPhase.ABB, "ABB", pkg.abbVideoId))
                            slotsRow.addView(createSlotBtn(IfsSubPhase.BL, "BL", pkg.blVideoId))
                            slotsRow.addView(createSlotBtn(IfsSubPhase.BBT, "BBT", pkg.bbtVideoId))
                        } else {
                            slotsRow.addView(createSlotBtn(IfsSubPhase.CONTENT, "VIDEO", pkg.blVideoId))
                        }
                        pkgCard.addView(slotsRow)
                        slotSetupContainer.addView(pkgCard)
                    }
                }
            }
        }

        btnToggleSlotSetup.setOnClickListener {
            isSlotSetupExpanded = !isSlotSetupExpanded
            slotSetupContainer.visibility = if (isSlotSetupExpanded) View.VISIBLE else View.GONE
            btnToggleSlotSetup.text = if (isSlotSetupExpanded) "⚙️ Atur Slot ▴" else "⚙️ Atur Slot ▾"
            if (isSlotSetupExpanded) {
                rebuildSlotSetupContent(
                    ifsManager.ifsState.value,
                    ifsManager.videoMode.value,
                    ifsManager.vtmPackage.value,
                    ifsManager.vtPackages.value
                )
            }
        }

        // Observer Realtime menggabungkan state, mode, VTM, dan daftar VT1..n
        serviceScope.launch(Dispatchers.Main) {
            combine(
                ifsManager.ifsState,
                ifsManager.videoMode,
                ifsManager.vtmPackage,
                ifsManager.vtPackages
            ) { state, mode, vtmPkg, vtList ->
                Quadruple(state, mode, vtmPkg, vtList)
            }.collect { (state, m, vtmPkg, vtList) ->
                if (isSlotSetupExpanded) {
                    rebuildSlotSetupContent(state, m, vtmPkg, vtList)
                }

                // Update SV1, SV2, SV3 & SV4 Pill
                fun styleModeBtn(btn: TextView, selected: Boolean) {
                    btn.setTextColor(if (selected) Color.parseColor("#00E5FF") else Color.parseColor("#94A3B8"))
                    btn.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(5).toFloat()
                        if (selected) {
                            setColor(Color.parseColor("#083344"))
                            setStroke(dp(1), Color.parseColor("#00E5FF"))
                        } else {
                            setColor(Color.parseColor("#0F172A"))
                            setStroke(dp(1), Color.parseColor("#334155"))
                        }
                    }
                }
                styleModeBtn(btnSv1, m == IfsVideoMode.SV1)
                styleModeBtn(btnSv2, m == IfsVideoMode.SV2)
                styleModeBtn(btnSv3, m == IfsVideoMode.SV3)
                styleModeBtn(btnSv4, m == IfsVideoMode.SV4)

                // Update SC Button & Uji Chat visibility (Sembunyikan pada SV3 & SV4 karena murni rotasi otomatis)
                val isScSupported = (m == IfsVideoMode.SV1 || m == IfsVideoMode.SV2)
                btnToggleSc.visibility = if (isScSupported) View.VISIBLE else View.GONE
                btnToggleIfsCommentTest.visibility = if (isScSupported) View.VISIBLE else View.GONE
                if (!isScSupported) {
                    ifsCommentTestContainer.visibility = View.GONE
                }
                val scOn = state.isScEnabled
                btnToggleSc.text = if (scOn) "💬 SC: ON" else "💬 SC: OFF"
                btnToggleSc.setTextColor(if (scOn) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
                btnToggleSc.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(5).toFloat()
                    if (scOn) {
                        setColor(Color.parseColor("#0C4A6E"))
                        setStroke(dp(1), Color.parseColor("#38BDF8"))
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                        setStroke(dp(1), Color.parseColor("#334155"))
                    }
                }

                // Update Status Bar
                when (m) {
                    IfsVideoMode.SV4 -> {
                        val nextInfo = when {
                            state.sv4NextReady -> "✅ VS${state.sv4NextVsNumber} Siap"
                            state.sv4IsStitchingNext -> "⚙️ Gabung VS${state.sv4NextVsNumber}(${state.sv4NextStitchPercent}%)"
                            else -> "Standby"
                        }
                        val guardInfo = "🛡️ 2m: Unik"
                        tvPhaseStatus.text = "● VS${state.sv4CurrentVsNumber} [${state.sv4ActiveFormula}] • $guardInfo ➡️ $nextInfo"
                        tvPhaseStatus.setTextColor(Color.parseColor("#10B981"))
                    }
                    IfsVideoMode.SV3 -> {
                        if (state.activeSubPhase == IfsSubPhase.VL) {
                            tvPhaseStatus.text = "● SV3 • VL [Putaran ${state.sv3CurrentVlLoop}/${state.sv3TargetVlLoops} ➡️ VB]"
                            tvPhaseStatus.setTextColor(Color.parseColor("#00E5FF"))
                        } else {
                            tvPhaseStatus.text = "● SV3 • VB${state.sv3ActiveVbNumber} [1x ➡️ VL] • Sisa:${state.sv3VbRemainingInCycle}/${state.sv3VbTotalCount}"
                            tvPhaseStatus.setTextColor(Color.parseColor("#E879F9"))
                        }
                    }
                    else -> {
                        val pkgCode = state.getActivePackageCode(m)
                        val loopInfo = if (state.activeSubPhase == IfsSubPhase.BL) "🔁 Loop" else "⏳ 1x"
                        val queueInfo = if (state.queue.isNotEmpty()) " • Antre:${state.queue.size}" else ""
                        tvPhaseStatus.text = "● $pkgCode • ${state.activeSubPhase.badge} [$loopInfo]$queueInfo"
                        tvPhaseStatus.setTextColor(Color.parseColor(state.activeSubPhase.colorHex))
                    }
                }

                if (state.isPlayingTriggeredAx) {
                    val axLbl = state.activeTriggerAxLabel ?: "AX${state.activeTriggerAxNumber}"
                    tvAudioStatus.text = "🔊 $axLbl"
                    tvAudioStatus.setTextColor(Color.parseColor("#FCD34D"))
                } else {
                    tvAudioStatus.text = "🎵 SR"
                    tvAudioStatus.setTextColor(Color.parseColor("#6EE7B7"))
                }

                // Render Tombol Trigger VT1..n (SV1), PM1..n (SV2), Kontrol Cepat SV3, atau Kontrol Cepat SV4 (Mode VS)
                triggerButtonsRow.removeAllViews()
                if (m == IfsVideoMode.SV4) {
                    tvTriggerHeader.text = "🧬 MODE VS ROLLING JIT (AUTO-HAPUS: ${state.sv4AutoDeletedCount} VS LAMA):"
                    val lCnt = state.sv4TotalLCount
                    val activeVids = repository.config.value.playlist.filterNot { repository.isRollingVsItem(it) }
                    val sv4Metrics = com.tajasb.infinity.Sv4VariationCalculator.calculateMetrics(
                        lCount = if (lCnt > 0) lCnt else activeVids.size,
                        minLoops = state.sv4MinLoops,
                        maxLoops = state.sv4MaxLoops,
                        activeVideos = activeVids
                    )

                    fun updateSv4DropdownContent() {
                        tvSv4DetailList.visibility = if (isSv4DetailExpanded) View.VISIBLE else View.GONE
                        tvSv4DetailList.text =
                            "(!) DAFTAR RINCIAN VARIASI, HARI & JAM LIVE:\n" +
                            "1. Total Waktu Tayang Unik: ${sv4Metrics.formattedTotalUniqueDaysHours} (${sv4Metrics.formattedTotalUniqueHoursSummary})\n" +
                            "2. Jumlah Variasi: ${sv4Metrics.formattedTotalVariations} Variasi Unik (Inti: ${sv4Metrics.formattedCoreVariations} + Rolling: ${sv4Metrics.formattedExtendedVariations})\n" +
                            "3. Durasi Rata-rata per 1 VS: ± ${sv4Metrics.formattedAvgVsDuration} (Rentang: ${sv4Metrics.formattedVsDurationRange})\n" +
                            "4. Kebutuhan Live: 6 Jam = ±${sv4Metrics.vsCountFor6Hours} VS • 12 Jam = ±${sv4Metrics.vsCountFor12Hours} VS • 24 Jam (1 Hari) = ±${sv4Metrics.vsCountFor24Hours} VS\n" +
                            "5. Memori HP: Auto-Hapus Aktif (${state.sv4AutoDeletedCount} file VS lama dihapus otomatis)"
                    }
                    updateSv4DropdownContent()

                    val isSv4Running = state.sv4VideoRunning
                    val btnStartStopSv4 = TextView(this@with).apply {
                        text = if (isSv4Running) "⏹️ Stop SV4" else "▶️ Mulai SV4"
                        textSize = 9.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (isSv4Running) Color.parseColor("#FCA5A5") else Color.parseColor("#6EE7B7"))
                        gravity = Gravity.CENTER
                        setPadding(dp(9), dp(6), dp(9), dp(6))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginEnd = dp(4) }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(6).toFloat()
                            setColor(if (isSv4Running) Color.parseColor("#450A0A") else Color.parseColor("#064E3B"))
                            setStroke(dp(1), if (isSv4Running) Color.parseColor("#EF4444") else Color.parseColor("#10B981"))
                        }
                        setOnClickListener {
                            if (ifsManager.ifsState.value.sv4VideoRunning) {
                                ifsManager.stopSv4VideoEngineForEdit()
                                Toast.makeText(applicationContext, "⏹️ SV4 dihentikan (Mode Edit/Persiapan)", Toast.LENGTH_SHORT).show()
                            } else {
                                ifsManager.startSv4VideoEngine(resetCounter = false)
                                Toast.makeText(applicationContext, "▶️ Memulai Mode SV4 (Variasi Susunan Video)...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    val btnJumpVs = TextView(this@with).apply {
                        text = when {
                            !isSv4Running -> "⏭️ Lompat VS"
                            state.sv4NextReady -> "⏭️ Lompat VS${state.sv4NextVsNumber}"
                            else -> "⏳ VS${state.sv4NextVsNumber} Dirakit..."
                        }
                        textSize = 9.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#10B981"))
                        gravity = Gravity.CENTER
                        setPadding(dp(9), dp(6), dp(9), dp(6))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginEnd = dp(4) }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(6).toFloat()
                            setColor(Color.parseColor("#06281E"))
                            setStroke(dp(1), Color.parseColor("#10B981"))
                        }
                        setOnClickListener {
                            if (!ifsManager.ifsState.value.sv4VideoRunning) {
                                ifsManager.startSv4VideoEngine(resetCounter = false)
                                Toast.makeText(applicationContext, "▶️ Memulai & melompat ke Variasi Susunan (VS)...", Toast.LENGTH_SHORT).show()
                            } else {
                                ifsManager.advancePhaseManually()
                                Toast.makeText(applicationContext, "⏭️ Beralih ke Variasi Susunan (VS) berikutnya", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    val btnRestartVs = TextView(this@with).apply {
                        text = "⚡ Rakit Ulang"
                        textSize = 9.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#00E5FF"))
                        gravity = Gravity.CENTER
                        setPadding(dp(9), dp(6), dp(9), dp(6))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginEnd = dp(4) }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(6).toFloat()
                            setColor(Color.parseColor("#083344"))
                            setStroke(dp(1), Color.parseColor("#00E5FF"))
                        }
                        setOnClickListener {
                            ifsManager.startSv4VideoEngine(resetCounter = true)
                            Toast.makeText(applicationContext, "⚡ Merakit ulang Variasi Susunan dari VS1...", Toast.LENGTH_SHORT).show()
                        }
                    }
                    val cardTotalVariasi = TextView(this@with).apply {
                        val effCnt = sv4Metrics.lCount
                        val arrow = if (isSv4DetailExpanded) "▴" else "▾"
                        val varText = if (effCnt > 0) {
                            "(!) Total: ${sv4Metrics.formattedTotalVariations} Variasi • ${sv4Metrics.formattedTotalUniqueDaysHours} $arrow"
                        } else {
                            "(!) Total: 0 Variasi (Upload Video) $arrow"
                        }
                        text = varText
                        textSize = 9.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#A7F3D0"))
                        gravity = Gravity.CENTER
                        setPadding(dp(9), dp(6), dp(9), dp(6))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(6).toFloat()
                            setColor(Color.parseColor("#072227"))
                            setStroke(dp(1), Color.parseColor("#10B981"))
                        }
                        setOnClickListener {
                            isSv4DetailExpanded = !isSv4DetailExpanded
                            val newArrow = if (isSv4DetailExpanded) "▴" else "▾"
                            text = if (effCnt > 0) {
                                "(!) Total: ${sv4Metrics.formattedTotalVariations} Variasi • ${sv4Metrics.formattedTotalUniqueDaysHours} $newArrow"
                            } else {
                                "(!) Total: 0 Variasi (Upload Video) $newArrow"
                            }
                            updateSv4DropdownContent()
                        }
                    }
                    triggerButtonsRow.addView(btnStartStopSv4)
                    triggerButtonsRow.addView(btnJumpVs)
                    triggerButtonsRow.addView(btnRestartVs)
                    triggerButtonsRow.addView(cardTotalVariasi)
                } else if (m == IfsVideoMode.SV3) {
                    tvSv4DetailList.visibility = View.GONE
                    tvTriggerHeader.text = "⚡ ROTASI OTOMATIS SV3 (VL LOOP ACAK ↔ GRUP VB):"
                    val btnJumpPhase = TextView(this@with).apply {
                        text = if (state.activeSubPhase == IfsSubPhase.VL) "⏭️ Lompat ke Grup VB" else "⏭️ Balik ke VL Loop Baru"
                        textSize = 9.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#00E5FF"))
                        gravity = Gravity.CENTER
                        setPadding(dp(9), dp(6), dp(9), dp(6))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginEnd = dp(4) }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(6).toFloat()
                            setColor(Color.parseColor("#083344"))
                            setStroke(dp(1), Color.parseColor("#00E5FF"))
                        }
                        setOnClickListener {
                            ifsManager.advancePhaseManually()
                            Toast.makeText(applicationContext, "⏭️ Beralih fase SV3 (VL ↔ VB)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    val btnReshuffleSv3 = TextView(this@with).apply {
                        text = "🔄 Acak Ulang Siklus VB"
                        textSize = 9.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#E879F9"))
                        gravity = Gravity.CENTER
                        setPadding(dp(9), dp(6), dp(9), dp(6))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(6).toFloat()
                            setColor(Color.parseColor("#2E1065"))
                            setStroke(dp(1), Color.parseColor("#E879F9"))
                        }
                        setOnClickListener {
                            ifsManager.resetCycleToBeginning()
                            Toast.makeText(applicationContext, "🔄 Siklus acak SV3 di-reset ke VL", Toast.LENGTH_SHORT).show()
                        }
                    }
                    triggerButtonsRow.addView(btnJumpPhase)
                    triggerButtonsRow.addView(btnReshuffleSv3)
                } else {
                    tvSv4DetailList.visibility = View.GONE
                    tvTriggerHeader.text = "⚡ TES / TRIGGER VT (KLIK UNTUK PUTAR):"
                    val visiblePkgs = vtList

                    for (pkg in visiblePkgs) {
                        val isThisActive = state.activeVtNumber == pkg.vtNumber
                        val hasAx = !pkg.linkedAudioId.isNullOrBlank()
                        val pCode = pkg.getShortCode(m)
                        val pAx = pkg.getAxCode(m)
                        val btnText = when {
                            hasAx -> if (isThisActive) "▶ $pCode•$pAx (Aktif)" else "▶ $pCode•$pAx"
                            else -> if (isThisActive) "▶ $pCode (Aktif)" else "▶ $pCode"
                        }

                        val btn = TextView(this@with).apply {
                            text = btnText
                            textSize = 9.5f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (isThisActive) Color.parseColor("#00E5FF") else Color.WHITE)
                            gravity = Gravity.CENTER
                            setPadding(dp(9), dp(6), dp(9), dp(6))
                            val lp = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                marginEnd = dp(4)
                            }
                            layoutParams = lp
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(6).toFloat()
                                if (isThisActive) {
                                    setColor(Color.parseColor("#083344"))
                                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                                } else {
                                    setColor(Color.parseColor("#1E293B"))
                                    setStroke(dp(1), Color.parseColor("#475569"))
                                }
                            }
                            setOnClickListener {
                                val ok = ifsManager.triggerVt(pkg.vtNumber, "Host Floating", "Manual Button")
                                if (ok) {
                                    Toast.makeText(applicationContext, "⚡ Memicu $pCode", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(applicationContext, "⚠️ Pilih video untuk $pCode di tombol '⚙️ Atur Slot ▾'", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        triggerButtonsRow.addView(btn)
                    }
                }
            }
        }

        return panelContainer
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
