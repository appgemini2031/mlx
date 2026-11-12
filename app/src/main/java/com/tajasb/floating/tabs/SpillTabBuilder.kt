package com.tajasb.floating.tabs

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.tajasb.FloatingControlService
import com.tajasb.model.AudioItem
import com.tajasb.model.ScaleMode
import com.tajasb.model.VideoItem
import kotlinx.coroutines.launch

/**
 * SpillTabBuilder:
 * Modular builder for Tab 1 (Video & Master/Spill Streaming), Tab 2 (Music Player),
 * Tab 3 (Visual & FPS), and Quick Playlist / Quick Spill Carousel items.
 */
object SpillTabBuilder {

    fun populateVideoMusicVisualTabs(
        service: FloatingControlService,
        tabContentHolder: LinearLayout,
        dp: (Int) -> Int
    ) = with(service) {
        // ---------------------------------------------------------
        // 3. TAB 1: 🎬 VIDEO TAB CONTAINER (COMPACT, SEJAJAR & JELAS UNTUK HOST)
        // ---------------------------------------------------------
        val tabVideoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        containerTabVideo = tabVideoContainer

        val makkahCard = buildMakkahLiveRelayCard(service = this, dp = dp)
        makkahCard.visibility = if (repository.config.value.enableMakkahRelayModule) View.VISIBLE else View.GONE
        tabVideoContainer.addView(makkahCard)

        // =========================================================
        // KARTU 1: PEMUTAR VIDEO AKTIF (Judul + Kontrol Inti Sejajar + Alat ▾)
        // =========================================================
        val videoNavBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        // Baris 1: Judul Video Aktif + Tombol Daftar Video (▾)
        val videoTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val videoLabel = TextView(this).apply {
            text = "🎬 [1/1] virtual.mp4 ▾"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        videoStatusText = videoLabel
        videoTitleRow.addView(videoLabel)

        val btnListBadge = TextView(this).apply {
            text = "📋 Daftar ▾"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
            }
            layoutParams = lp
        }

        val toggleQuickList: () -> Unit = {
            isQuickListOpen = !isQuickListOpen
            val vis = if (isQuickListOpen) View.VISIBLE else View.GONE
            quickListWrapperLayout?.visibility = vis
            quickListScrollView?.visibility = vis
            btnListBadge.text = if (isQuickListOpen) "📋 Daftar ▴" else "📋 Daftar ▾"
            if (isQuickListOpen) {
                buildQuickVideoListItems(this@with, repository.config.value.playlist, repository.config.value.currentVideoIndex)
            }
        }

        videoLabel.setOnClickListener { toggleQuickList() }
        btnListBadge.setOnClickListener { toggleQuickList() }

        videoTitleRow.addView(btnListBadge)
        videoNavBar.addView(videoTitleRow)

        // Container Alat Tambahan (Seekbar, Volume, 0ms, 3s, FPS) - Default Tertutup agar Host tidak bingung
        var isExtraToolsOpen = false
        val extraToolsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#09101D"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }

        // Baris 2: Kontrol Pemutar Sejajar (Prev | Play/Pause | Next | Suara | Mata | Alat ▾)
        val videoNavControlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
        }

        val btnPrevVideo = TextView(this).apply {
            text = "⏪"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(2) }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener { navigatePrevVideo() }
        }
        videoNavControlsRow.addView(btnPrevVideo)

        val btnPlayVid = TextView(this).apply {
            text = "⏸️"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setOnClickListener {
                serviceScope.launch {
                    val paused = repository.togglePauseVideo()
                    Toast.makeText(applicationContext, if (paused) "Video dijeda ⏸️" else "Video diputar ▶️", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnPlayPauseVideoText = btnPlayVid
        videoNavControlsRow.addView(btnPlayVid)

        val btnNextVideo = TextView(this).apply {
            text = "⏩"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener { navigateNextVideo() }
        }
        videoNavControlsRow.addView(btnNextVideo)

        val btnQuickPlayMode = TextView(this).apply {
            val cfg = repository.config.value
            val isIfs = try { com.tajasb.infinity.IfsManager.getInstance(applicationContext).isIfsActive.value } catch (_: Throwable) { false } || cfg.isIfsMode
            val isSingleLoop = !isIfs && ((cfg.autoNextEnabled && cfg.autoNextMode == com.tajasb.model.AutoNextMode.SINGLE_LOOP) || !cfg.autoNextEnabled)
            val isRand = !isIfs && cfg.autoNextEnabled && cfg.autoNextMode == com.tajasb.model.AutoNextMode.RANDOM
            text = when {
                isIfs -> "♾️"
                cfg.isBridgeCycleMode -> "🌉"
                cfg.isMasterSpillMode -> "⭐"
                isSingleLoop -> "🔂"
                isRand -> "🔀"
                else -> "🔁"
            }
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                serviceScope.launch {
                    val currentMode = repository.config.value.autoNextMode
                    val nextMode = when (currentMode) {
                        com.tajasb.model.AutoNextMode.SEQUENTIAL -> com.tajasb.model.AutoNextMode.SINGLE_LOOP
                        com.tajasb.model.AutoNextMode.SINGLE_LOOP -> com.tajasb.model.AutoNextMode.RANDOM
                        com.tajasb.model.AutoNextMode.RANDOM -> com.tajasb.model.AutoNextMode.SEQUENTIAL
                    }
                    repository.activateStandardModeSync(nextMode)
                    Toast.makeText(applicationContext, "Alur: ${nextMode.label}", Toast.LENGTH_SHORT).show()
                    updateUiForConfig(repository.config.value)
                }
            }
        }
        btnPlayModeQuickText = btnQuickPlayMode
        videoNavControlsRow.addView(btnQuickPlayMode)

        val btnVidAudio = TextView(this).apply {
            val allVideosMuted = if (repository.config.value.playlist.isNotEmpty()) {
                repository.config.value.playlist.all { it.isMuted }
            } else {
                repository.config.value.isVideoAudioMuted
            }
            text = if (allVideosMuted) "🔇" else "🔊"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (allVideosMuted) {
                    setColor(Color.parseColor("#450A0A"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#083344"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                }
            }
            setOnClickListener {
                serviceScope.launch {
                    val muted = repository.toggleAllVideosMute()
                    val msg = if (muted) "Semua Video Dimute 🔇" else "Semua Video Bersuara 🔊"
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnVideoAudioMuteText = btnVidAudio
        videoNavControlsRow.addView(btnVidAudio)

        val btnEyeToggleAll = TextView(this).apply {
            val allClosed = repository.config.value.playlist.isNotEmpty() && repository.config.value.playlist.all { !it.isEnabled }
            text = if (allClosed) "🙈" else "👁️"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (allClosed) {
                    setColor(Color.parseColor("#450A0A"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#1E1B4B"))
                    setStroke(dp(1), Color.parseColor("#818CF8"))
                }
            }
            setOnClickListener {
                serviceScope.launch {
                    val targetEnabled = repository.toggleAllVideosEnabled()
                    val msg = if (targetEnabled) {
                        "Semua video: AKTIF 👁️"
                    } else {
                        "Semua video: NONAKTIF 🙈"
                    }
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnToggleAllVideosEyeText = btnEyeToggleAll
        videoNavControlsRow.addView(btnEyeToggleAll)

        val btnExtraTools = TextView(this).apply {
            text = "🎛️ Alat ▾"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f).apply {
                marginStart = dp(2)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setOnClickListener {
                isExtraToolsOpen = !isExtraToolsOpen
                extraToolsContainer.visibility = if (isExtraToolsOpen) View.VISIBLE else View.GONE
                text = if (isExtraToolsOpen) "🎛️ Alat ▴" else "🎛️ Alat ▾"
            }
        }
        videoNavControlsRow.addView(btnExtraTools)
        videoNavBar.addView(videoNavControlsRow)

        // Isi Collapsible Alat Tambahan: Seekbar Posisi + Volume + 0ms + 3s + FPS
        val vTimeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val vTimeTitle = TextView(this).apply {
            text = "⏱️ Posisi:"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
        }
        vTimeRow.addView(vTimeTitle)

        val vTimeLbl = TextView(this).apply {
            text = "00:00 / 00:00"
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        videoTimeLabel = vTimeLbl
        vTimeRow.addView(vTimeLbl)
        extraToolsContainer.addView(vTimeRow)

        val vSeekBar = SeekBar(this).apply {
            setPadding(dp(2), dp(3), dp(2), dp(3))
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        videoTimeLabel?.text = "${formatTime(progress.toLong())} / ${formatTime(currentVideoDurationMs)}"
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isUserTrackingVideoSeek = true
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isUserTrackingVideoSeek = false
                    val targetPos = seekBar?.progress?.toLong() ?: 0L
                    currentVideoPositionMs = targetPos
                    repository.seekVideo(targetPos)
                }
            })
        }
        videoSeekBar = vSeekBar
        extraToolsContainer.addView(vSeekBar)

        val vMiniToolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            layoutParams = lp
        }

        val btnVidVolMinus = TextView(this).apply {
            text = "➖"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setOnClickListener {
                val cur = (repository.videoVolume.value * 100).toInt()
                val nextPct = (cur - 10).coerceIn(0, 100)
                repository.setVideoVolume(nextPct / 100f)
                videoVolumeLabel?.text = "🔊 $nextPct%"
            }
        }
        vMiniToolsRow.addView(btnVidVolMinus)

        val vVolLbl = TextView(this).apply {
            val initialVolPct = (repository.videoVolume.value * 100).toInt()
            text = "🔊 $initialVolPct%"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        videoVolumeLabel = vVolLbl
        videoVolumeSeekBar = null
        vMiniToolsRow.addView(vVolLbl)

        val btnVidVolPlus = TextView(this).apply {
            text = "➕"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(3)
                marginEnd = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#083344"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                val cur = (repository.videoVolume.value * 100).toInt()
                val nextPct = (cur + 10).coerceIn(0, 100)
                repository.setVideoVolume(nextPct / 100f)
                videoVolumeLabel?.text = "🔊 $nextPct%"
            }
        }
        vMiniToolsRow.addView(btnVidVolPlus)

        val btnRestart0ms = TextView(this).apply {
            text = "🔁 0ms"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(2) }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                currentVideoPositionMs = 0L
                repository.seekVideo(0L)
                Toast.makeText(applicationContext, "Mengulang video dari 0 ms 🔁", Toast.LENGTH_SHORT).show()
            }
        }
        vMiniToolsRow.addView(btnRestart0ms)

        val btnSeekNearEnd = TextView(this).apply {
            text = "⏩ 3s"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(2)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#78350F"))
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
            setOnClickListener {
                val dur = currentVideoDurationMs
                if (dur > 3500L) {
                    val target = dur - 3000L
                    currentVideoPositionMs = target
                    repository.seekVideo(target)
                    Toast.makeText(applicationContext, "Lompat ke sisa 3 detik! ⏩", Toast.LENGTH_SHORT).show()
                } else {
                    repository.seekVideo(0L)
                }
            }
        }
        vMiniToolsRow.addView(btnSeekNearEnd)

        val btnFps = TextView(this).apply {
            text = "⚡ 30 FPS ▾"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginStart = dp(1)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#3B0764"))
                setStroke(dp(1), Color.parseColor("#D946EF"))
            }
        }
        btnFpsQuick = btnFps
        vMiniToolsRow.addView(btnFps)
        extraToolsContainer.addView(vMiniToolsRow)

        val fpsPickerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#09101D"))
                setStroke(dp(1), Color.parseColor("#3B0764"))
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        quickFpsPickerContainer = fpsPickerContainer

        fun createQuickFpsChoiceBtn(fpsVal: Int): TextView {
            return TextView(this).apply {
                text = "$fpsVal FPS"
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(4), dp(2), dp(4))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                }
                setTextColor(Color.WHITE)
                setOnClickListener {
                    serviceScope.launch {
                        repository.setFps(fpsVal)
                        btnFps.text = "⚡ $fpsVal FPS ▾"
                        Toast.makeText(applicationContext, "Target Frame Rate: $fpsVal FPS ⚡", Toast.LENGTH_SHORT).show()
                        updateUiForConfig(repository.config.value)
                        fpsPickerContainer.visibility = View.GONE
                    }
                }
            }
        }

        val b15 = createQuickFpsChoiceBtn(15)
        val b24 = createQuickFpsChoiceBtn(24)
        val b30 = createQuickFpsChoiceBtn(30)
        val b60 = createQuickFpsChoiceBtn(60)

        qBtnFps15 = b15
        qBtnFps24 = b24
        qBtnFps30 = b30
        qBtnFps60 = b60

        fpsPickerContainer.addView(b15)
        fpsPickerContainer.addView(b24)
        fpsPickerContainer.addView(b30)
        fpsPickerContainer.addView(b60)

        btnFps.setOnClickListener {
            val isVis = fpsPickerContainer.visibility == View.VISIBLE
            fpsPickerContainer.visibility = if (isVis) View.GONE else View.VISIBLE
        }
        extraToolsContainer.addView(fpsPickerContainer)

        val btnDualAudio = TextView(this).apply {
            val isDual = repository.config.value.isDualAudioMixingEnabled
            text = if (isDual) "🎧 Suara Ganda: ON (Video + MP3)" else "🎧 Suara Ganda: OFF (Musik Auto-Jeda)"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            val applyDualAudioBg: (Boolean) -> Unit = { dual ->
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (dual) {
                        setColor(Color.parseColor("#064E3B"))
                        setStroke(dp(1), Color.parseColor("#10B981"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#475569"))
                    }
                }
                setTextColor(if (dual) Color.parseColor("#34D399") else Color.parseColor("#94A3B8"))
                text = if (dual) "🎧 Suara Ganda: ON (Video + MP3)" else "🎧 Suara Ganda: OFF (Musik Auto-Jeda)"
            }
            applyDualAudioBg(isDual)
            setOnClickListener {
                serviceScope.launch {
                    val next = repository.toggleDualAudioMixing()
                    applyDualAudioBg(next)
                    val toastMsg = if (next) {
                        "🎧 Suara Ganda: AKTIF (Video & Musik MP3 berbunyi bersamaan)"
                    } else {
                        "🎧 Suara Ganda: OFF (Musik MP3 otomatis jeda saat video bersuara)"
                    }
                    Toast.makeText(applicationContext, toastMsg, Toast.LENGTH_SHORT).show()
                    updateUiForConfig(repository.config.value)
                }
            }
        }
        btnDualAudioMixingText = btnDualAudio
        extraToolsContainer.addView(btnDualAudio)
        videoNavBar.addView(extraToolsContainer)

        // Baris Ringkas Deteksi & Pembersih Kode AI di Tab Video (Gated by enableAiDetectionModule)
        val videoAiQuickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = if (repository.config.value.enableAiDetectionModule) View.VISIBLE else View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#09101D"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        this@with.videoAiQuickRow = videoAiQuickRow

        val tvVideoAiQuickStatus = TextView(this).apply {
            text = "🛡️ Kode AI: Memeriksa..."
            textSize = 8.6f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.35f).apply {
                marginEnd = dp(4)
            }
            setOnClickListener {
                showAiWatermarkOverlayDialog(this@with)
            }
        }
        videoAiQuickRow.addView(tvVideoAiQuickStatus)

        val btnVideoAiQuickScan = TextView(this).apply {
            text = "🔍 Cek AI"
            textSize = 8.4f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                serviceScope.launch {
                    val rep = repository.scanActiveVideoAiWatermark()
                    if (rep != null) {
                        Toast.makeText(applicationContext, rep.buildNotificationHeadline(), Toast.LENGTH_LONG).show()
                        showAiWatermarkOverlayDialog(this@with)
                    } else {
                        Toast.makeText(applicationContext, "⚠️ Pilih video terlebih dahulu", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        videoAiQuickRow.addView(btnVideoAiQuickScan)

        val btnVideoAiQuickClean = TextView(this).apply {
            text = "🧹 Hapus AI"
            textSize = 8.4f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#10B981"))
            }
            setOnClickListener {
                serviceScope.launch {
                    Toast.makeText(applicationContext, "🧹 Membersihkan kode AI & metadata MP4...", Toast.LENGTH_SHORT).show()
                    val res = repository.cleanActiveVideoAiWatermark()
                    Toast.makeText(applicationContext, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
        videoAiQuickRow.addView(btnVideoAiQuickClean)
        videoNavBar.addView(videoAiQuickRow)

        tabVideoContainer.addView(videoNavBar)

        // Dropdown video list container (Muncul saat 📋 Daftar ▾ diklik)
        val quickListWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0B0F19"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        quickListWrapperLayout = quickListWrapper

        // Baris Input Pencarian Cepat Video
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#111827"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setPadding(dp(7), dp(3), dp(6), dp(3))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
        }

        val searchEt = EditText(this).apply {
            hint = "🔍 Cari nama / spill..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 9.5f
            background = null
            setSingleLine(true)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    quickVideoSearchQuery = s?.toString()?.trim() ?: ""
                    buildQuickVideoListItems(this@with, repository.config.value.playlist, repository.config.value.currentVideoIndex)
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        quickVideoSearchEditText = searchEt
        searchRow.addView(searchEt)

        val btnClearSearch = TextView(this).apply {
            text = "✖"
            textSize = 9f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setOnClickListener {
                searchEt.setText("")
            }
        }
        searchRow.addView(btnClearSearch)
        quickListWrapper.addView(searchRow)

        val vListScroll = ScrollView(this).apply {
            val scrollLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(145))
            layoutParams = scrollLp
            isNestedScrollingEnabled = true
            isVerticalScrollBarEnabled = true
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                v.onTouchEvent(event)
            }
        }
        quickListScrollView = vListScroll

        val vListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        quickVideoListContainer = vListContainer
        vListScroll.addView(vListContainer)
        quickListWrapper.addView(vListScroll)
        tabVideoContainer.addView(quickListWrapper)

        // =========================================================
        // KARTU 2: MODE PUTAR & TRIGGER LIVE TERPADU (Standar | Spill | Infinity)
        // =========================================================
        val unifiedModeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setPadding(dp(7), dp(6), dp(7), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
        }

        // Baris 1: Pemilih 3 Mode Sejajar (⚪ Standar | 👑 Spill | ♾️ Infinity | !)
        val modeTabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnStandardMode = TextView(this).apply {
            text = "⚪ Standar"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(applicationContext) } catch (_: Throwable) { null }
                    if (ifsMgr?.isIfsActive?.value == true) {
                        ifsMgr.setIfsEnabled(false)
                    }
                    repository.activateStandardModeSync()
                    Toast.makeText(applicationContext, "⚪ Mode: Playlist Standar", Toast.LENGTH_SHORT).show()
                    updateUiForConfig(repository.config.value)
                }
            }
        }
        btnModeStandardVideo = btnStandardMode
        modeTabsRow.addView(btnStandardMode)

        val btnSpillMode = TextView(this).apply {
            text = "👑 Spill"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(applicationContext) } catch (_: Throwable) { null }
                    if (ifsMgr?.isIfsActive?.value == true) {
                        ifsMgr.setIfsEnabled(false)
                    }
                    if (!repository.config.value.isMasterSpillMode) {
                        repository.toggleMasterSpillMode()
                    }
                    Toast.makeText(applicationContext, "👑 Mode: Master & Spill Aktif", Toast.LENGTH_SHORT).show()
                    updateUiForConfig(repository.config.value)
                }
            }
        }
        btnToggleMasterSpillText = btnSpillMode
        modeTabsRow.addView(btnSpillMode)

        val btnIfsMode = TextView(this).apply {
            text = "♾️ Infinity"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val mgr = com.tajasb.infinity.IfsManager.getInstance(applicationContext)
                    if (!mgr.isIfsActive.value) {
                        mgr.setIfsEnabled(true)
                    }
                    Toast.makeText(applicationContext, "♾️ Mode: Infinity System Aktif", Toast.LENGTH_SHORT).show()
                    updateUiForConfig(repository.config.value)
                }
            }
        }
        btnToggleBridgeCycleText = btnIfsMode
        modeTabsRow.addView(btnIfsMode)

        val btnModeHelp = TextView(this).apply {
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
                    "ℹ️ Pilihan Mode Video:\n• ⚪ Standar: Putar Urut / Loop 1 / Acak.\n• 👑 Spill: 1 Video Master berputar terus, klik video Spill untuk tayang 1x lalu balik Master.\n• ♾️ Infinity: Siklus otomatis (SV1: ABA + VT1..n, SV2: PM1..n) + Audio SR/AX.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        modeTabsRow.addView(btnModeHelp)
        unifiedModeCard.addView(modeTabsRow)

        // ---------------------------------------------------------
        // SUB-PANEL A: MODE ⚪ STANDAR (Alur Urut/Loop/Acak + Pindah Instan/Tunggu)
        // ---------------------------------------------------------
        val standardPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
        }
        containerVideoModeStandard = standardPanel

        val standardAlurRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun createModeChoiceBtn(label: String, mode: com.tajasb.model.AutoNextMode, toastMsg: String): TextView {
            return TextView(this).apply {
                text = label
                textSize = 9.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(6), dp(4), dp(6))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(5).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                }
                setTextColor(Color.WHITE)
                setOnClickListener {
                    serviceScope.launch {
                        repository.activateStandardModeSync(mode)
                        Toast.makeText(applicationContext, toastMsg, Toast.LENGTH_SHORT).show()
                        updateUiForConfig(repository.config.value)
                    }
                }
            }
        }

        val bModeSeq = createModeChoiceBtn("🔁 Urut", com.tajasb.model.AutoNextMode.SEQUENTIAL, "Alur: BERURUTAN 🔁")
        val bModeLoop = createModeChoiceBtn("🔂 Loop 1", com.tajasb.model.AutoNextMode.SINGLE_LOOP, "Alur: LOOP 1 VIDEO 🔂")
        val bModeRand = createModeChoiceBtn("🔀 Acak", com.tajasb.model.AutoNextMode.RANDOM, "Alur: ACAK / RANDOM 🔀")

        btnAutoNextSeq = bModeSeq
        btnAutoNextSingleLoop = bModeLoop
        btnAutoNextRand = bModeRand

        standardAlurRow.addView(bModeSeq)
        standardAlurRow.addView(bModeLoop)
        standardAlurRow.addView(bModeRand)
        standardPanel.addView(standardAlurRow)

        val btnSwitch = TextView(this).apply {
            val isInst = repository.config.value.videoSwitchMode == com.tajasb.model.VideoSwitchMode.INSTANT
            text = if (isInst) "⚡ Pindah: Instan (Langsung)" else "⏳ Pindah: Tunggu Video Selesai"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val next = repository.toggleVideoSwitchMode()
                    val msg = if (next == com.tajasb.model.VideoSwitchMode.INSTANT) {
                        "⚡ Pindah Video: INSTAN"
                    } else {
                        "⏳ Pindah Video: TUNGGU SELESAI"
                    }
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                    updateUiForConfig(repository.config.value)
                }
            }
        }
        btnSwitchModeText = btnSwitch
        standardPanel.addView(btnSwitch)

        val leadTimeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#09101D"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }

        val tvLeadLabel = TextView(this).apply {
            val sec = repository.config.value.autoNextLeadSeconds
            text = "⏱️ Pindah Awal: ${sec}s"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnAutoNextLeadText = tvLeadLabel
        leadTimeRow.addView(tvLeadLabel)

        fun createLeadBtn(label: String, targetSec: Int): TextView {
            return TextView(this).apply {
                text = label
                textSize = 8.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(3), dp(6), dp(3))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(2)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
                setTextColor(Color.WHITE)
                setOnClickListener {
                    serviceScope.launch {
                        val current = repository.config.value.autoNextLeadSeconds
                        val newSec = when (label) {
                            "-" -> (current - 1).coerceAtLeast(1)
                            "+" -> (current + 1).coerceAtMost(15)
                            else -> targetSec.coerceIn(1, 15)
                        }
                        repository.setAutoNextLeadSeconds(newSec)
                        tvLeadLabel.text = "⏱️ Pindah Awal: ${newSec}s"
                        Toast.makeText(applicationContext, "⏱️ Transisi video: ${newSec}s sebelum selesai", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        leadTimeRow.addView(createLeadBtn("-", 0))
        leadTimeRow.addView(createLeadBtn("1s", 1))
        leadTimeRow.addView(createLeadBtn("2s", 2))
        leadTimeRow.addView(createLeadBtn("3s", 3))
        leadTimeRow.addView(createLeadBtn("+", 0))
        standardPanel.addView(leadTimeRow)

        unifiedModeCard.addView(standardPanel)

        // ---------------------------------------------------------
        // SUB-PANEL B: MODE 👑 SPILL (Master & Spill Khusus Host Live)
        // ---------------------------------------------------------
        val spillPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
        }
        containerVideoModeSpill = spillPanel

        // Baris 1 Spill: Status Master/Spill + Opsi Sejajar (⏳ Tunggu/Instan | 💬 Chat | ↩ Master)
        val spillControlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#090D16"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
        }

        val tvStatus = TextView(this).apply {
            text = "👑 Master Loop"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FDE68A"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvMasterSpillStatusBanner = tvStatus
        spillControlRow.addView(tvStatus)

        val btnSpillWaitToggle = TextView(this).apply {
            val waitFinish = repository.config.value.masterSpillWaitFinish
            text = if (waitFinish) "⏳ Tunggu" else "⚡ Instan"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(3), dp(5), dp(3))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(3)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val nextWait = !repository.config.value.masterSpillWaitFinish
                    repository.setMasterSpillWaitFinish(nextWait)
                    Toast.makeText(
                        applicationContext,
                        if (nextWait) "⏳ Spill menunggu video Master selesai" else "⚡ Spill langsung memotong",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateUiForConfig(repository.config.value)
                }
            }
        }
        btnSpillSwitchModeText = btnSpillWaitToggle
        spillControlRow.addView(btnSpillWaitToggle)

        val btnAutoCommentSpill = TextView(this).apply {
            val isAutoSpill = repository.config.value.autoSpillFromComments
            text = if (isAutoSpill) "💬 Chat: ON" else "💬 Chat: OFF"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(3), dp(5), dp(3))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(3)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val next = repository.toggleAutoSpillFromComments()
                    Toast.makeText(applicationContext, if (next) "💬 Auto Spill Chat: ON" else "💬 Auto Spill Chat: OFF", Toast.LENGTH_SHORT).show()
                    updateUiForConfig(repository.config.value)
                }
            }
        }
        btnToggleAutoCommentSpillText = btnAutoCommentSpill
        spillControlRow.addView(btnAutoCommentSpill)

        val btnBackToMaster = TextView(this).apply {
            text = "↩ Master"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCD34D"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(3)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    repository.returnToMasterVideo(immediate = true)
                    Toast.makeText(applicationContext, "👑 Kembali ke Video Master", Toast.LENGTH_SHORT).show()
                    updateUiForConfig(repository.config.value)
                }
            }
        }
        btnReturnMasterQuick = btnBackToMaster
        spillControlRow.addView(btnBackToMaster)
        spillActionControlsRow = spillControlRow
        spillPanel.addView(spillControlRow)

        // Baris 2 Spill: Kotak Trigger Spill Cepat untuk Host Live
        val spillTriggerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#09101D"))
                setStroke(dp(1), Color.parseColor("#F59E0B"))
            }
        }

        val spillHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        val tvSpillHeader = TextView(this).apply {
            text = "⚡ TES / TRIGGER SPILL (KLIK VIDEO):"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCD34D"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        spillHeaderRow.addView(tvSpillHeader)

        var isSpillTestOpen = false
        val spillTestContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        val btnToggleTestKomen = TextView(this).apply {
            text = "🧪 Uji Chat ▾"
            textSize = 8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(dp(5), dp(2), dp(5), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0C4A6E"))
                setStroke(dp(1), Color.parseColor("#0284C7"))
            }
            setOnClickListener {
                isSpillTestOpen = !isSpillTestOpen
                spillTestContainer.visibility = if (isSpillTestOpen) View.VISIBLE else View.GONE
                text = if (isSpillTestOpen) "🧪 Uji Chat ▴" else "🧪 Uji Chat ▾"
            }
        }
        spillHeaderRow.addView(btnToggleTestKomen)
        spillTriggerBox.addView(spillHeaderRow)

        val scrollChips = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val chipsHolder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        quickSpillChipsContainer = chipsHolder
        scrollChips.addView(chipsHolder)
        spillScrollChips = scrollChips
        spillTriggerBox.addView(scrollChips)

        // Collapsible Uji Komen di dalam Spill
        val testCommentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val etTestComment = EditText(this).apply {
            hint = "Ketik komen tes (cth: spill 1)..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 9f
            setSingleLine(true)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#09101D"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            layoutParams = lp
        }
        testCommentRow.addView(etTestComment)

        val btnExecuteTestComment = TextView(this).apply {
            text = "⚡ Uji"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0284C7"))
            }
            setOnClickListener {
                val text = etTestComment.text.toString().trim()
                if (text.isNotBlank()) {
                    val lc = com.tajasb.livecomment.LiveCommentManager.getInstance(applicationContext)
                    val accepted = lc.submitDetectedComment("Pengguna Tes", text)
                    Toast.makeText(applicationContext, "Komen '$text' dikirim! (${if (accepted) "Diterima" else "Diproses"})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(applicationContext, "Ketik teks komen untuk uji trigger", Toast.LENGTH_SHORT).show()
                }
            }
        }
        testCommentRow.addView(btnExecuteTestComment)
        spillTestCommentRow = testCommentRow
        spillTestContainer.addView(testCommentRow)

        val quickChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
        }
        listOf("spill 1", "spill 2", "1", "2").forEach { sampleText ->
            val sampleChip = TextView(this).apply {
                text = "💬 $sampleText"
                textSize = 8.5f
                setTextColor(Color.parseColor("#38BDF8"))
                setPadding(dp(5), dp(2), dp(5), dp(2))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#0C2340"))
                    setStroke(dp(1), Color.parseColor("#0284C7"))
                }
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                gravity = Gravity.CENTER
                setOnClickListener {
                    etTestComment.setText(sampleText)
                    val lc = com.tajasb.livecomment.LiveCommentManager.getInstance(applicationContext)
                    lc.submitDetectedComment("Penonton Live", sampleText)
                    Toast.makeText(applicationContext, "Memicu komen '$sampleText'...", Toast.LENGTH_SHORT).show()
                }
            }
            quickChipsRow.addView(sampleChip)
        }
        spillQuickChipsRow = quickChipsRow
        spillTestContainer.addView(quickChipsRow)

        val btnCheckScanLog = TextView(this).apply {
            text = "🔍 Buka Log Scan Komentar Live"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#082F49"))
                setStroke(dp(1), Color.parseColor("#0EA5E9"))
            }
            setOnClickListener {
                selectTab(com.tajasb.FloatingTab.NOTIF)
                isShowingScanLog = true
                com.tajasb.livecomment.CommentScanInspector.getInstance(this@with).setInspectorActive(true)
                updateBuyerNotifUi()
                val entries = com.tajasb.livecomment.CommentScanInspector.getInstance(this@with).entriesFlow.value
                updateScanInspectorFeed(entries)
                updateScanInspectorCounters()
            }
        }
        spillScanLogButton = btnCheckScanLog
        spillTestContainer.addView(btnCheckScanLog)

        spillTriggerBox.addView(spillTestContainer)
        spillPanel.addView(spillTriggerBox)
        unifiedModeCard.addView(spillPanel)

        // ---------------------------------------------------------
        // SUB-PANEL C: MODE ♾️ INFINITY (IFS) (Hanya tampil saat IFS aktif)
        // ---------------------------------------------------------
        val ifsPanel = IfsFloatingTabBuilder.buildIfsFloatingCard(service = this, dp = dp)
        containerVideoModeIfs = ifsPanel
        unifiedModeCard.addView(ifsPanel)

        tabVideoContainer.addView(unifiedModeCard)

        // =========================================================
        // BARIS BAWAH OPSIONAL: ⚡ BOOSTER & SISTEM (Collapsible 1 Baris Ringkas)
        // =========================================================
        var isBoosterOpen = false
        val boosterCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(7).toFloat()
                setColor(Color.parseColor("#0B0F19"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(7), dp(4), dp(7), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        val boosterHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val statsInit = repository.getDeviceMemoryStats()
        val tvBoosterTitle = TextView(this).apply {
            text = "⚡ Booster & Anti-Freeze (${statsInit.tier.badge})"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        boosterHeader.addView(tvBoosterTitle)

        val tvBoosterArrow = TextView(this).apply {
            text = "▾"
            textSize = 10f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        boosterHeader.addView(tvBoosterArrow)
        boosterCard.addView(boosterHeader)

        val boosterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        boosterHeader.setOnClickListener {
            isBoosterOpen = !isBoosterOpen
            boosterRow.visibility = if (isBoosterOpen) View.VISIBLE else View.GONE
            tvBoosterArrow.text = if (isBoosterOpen) "▴" else "▾"
        }

        val btnTurboClean = TextView(this).apply {
            text = "⚡ Bersihkan RAM"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#5B21B6"))
                setStroke(dp(1), Color.parseColor("#8B5CF6"))
            }
            setOnClickListener {
                serviceScope.launch {
                    val res = repository.triggerTurboRamClean()
                    val stats = repository.getDeviceMemoryStats()
                    text = "⚡ RAM (+${res.freedMb}MB)"
                    Toast.makeText(applicationContext, "⚡ RAM Dibersihkan! +${res.freedMb} MB Bebas (${stats.availableRamMb} MB Tersedia)", Toast.LENGTH_SHORT).show()
                }
            }
        }
        boosterRow.addView(btnTurboClean)

        val isVpnActive = repository.config.value.isLiveFocusVpnEnabled || com.tajasb.optimizer.LiveFocusVpnService.isVpnActive
        val btnToggleVpnFocus = TextView(this).apply {
            text = if (isVpnActive) "🛡️ Fokus: ON" else "🛡️ Fokus: OFF"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
            }
            layoutParams = lp
            val applyVpnBg: (Boolean) -> Unit = { active ->
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (active) {
                        setColor(Color.parseColor("#0369A1"))
                        setStroke(dp(1), Color.parseColor("#38BDF8"))
                    } else {
                        setColor(Color.parseColor("#1F2937"))
                        setStroke(dp(1), Color.parseColor("#4B5563"))
                    }
                }
            }
            applyVpnBg(isVpnActive)
            setOnClickListener {
                serviceScope.launch {
                    val willEnable = !repository.config.value.isLiveFocusVpnEnabled
                    if (willEnable && !com.tajasb.optimizer.LiveFocusVpnService.isPrepared(applicationContext)) {
                        val prepIntent = com.tajasb.optimizer.LiveFocusVpnService.getPrepareIntent(applicationContext)
                        if (prepIntent != null) {
                            prepIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            applicationContext.startActivity(prepIntent)
                            Toast.makeText(applicationContext, "Izinkan dialog koneksi VPN di layar HP Anda 🛡️", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                    }
                    val next = repository.toggleLiveFocusVpn()
                    text = if (next) "🛡️ Fokus: ON" else "🛡️ Fokus: OFF"
                    applyVpnBg(next)
                    val toastMsg = if (next) "🛡️ Fokus Jaringan: AKTIF" else "Fokus Jaringan: OFF"
                    Toast.makeText(applicationContext, toastMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        boosterRow.addView(btnToggleVpnFocus)

        val isAutoRocketOn = repository.config.value.isAutoRocketRecoveryEnabled
        val btnAutoRocketQuick = TextView(this).apply {
            text = if (isAutoRocketOn) "🚀 Roket: AUTO" else "🚀 Roket: OFF"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
            }
            layoutParams = lp
            val applyRocketBg: (Boolean) -> Unit = { active ->
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (active) {
                        setColor(Color.parseColor("#065F46"))
                        setStroke(dp(1), Color.parseColor("#10B981"))
                    } else {
                        setColor(Color.parseColor("#1F2937"))
                        setStroke(dp(1), Color.parseColor("#4B5563"))
                    }
                }
            }
            applyRocketBg(isAutoRocketOn)
            setOnClickListener {
                serviceScope.launch {
                    val next = repository.toggleAutoRocketRecovery()
                    text = if (next) "🚀 Roket: AUTO" else "🚀 Roket: OFF"
                    applyRocketBg(next)
                    val recCount = repository.config.value.autoRecoveryCount
                    Toast.makeText(
                        applicationContext,
                        if (next) "🚀 Auto-Rocket AKTIF (Dipulihkan: $recCount kali)" else "Auto-Rocket NONAKTIF",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            setOnLongClickListener {
                triggerAutoRocketRecovery(0L, "manual_test")
                true
            }
        }
        boosterRow.addView(btnAutoRocketQuick)
        boosterCard.addView(boosterRow)

        val initDna = repository.config.value.activeSessionDna
        val btnSessionDnaQuick = TextView(this).apply {
            text = "🧬 ${initDna.badgeText} (${initDna.quadrantLabel}) — Ganti DNA"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E0E7FF"))
            gravity = Gravity.CENTER
            visibility = if (repository.config.value.enableDnaModule) View.VISIBLE else View.GONE
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#31104F"))
                setStroke(dp(1), Color.parseColor("#A855F7"))
            }
            setOnClickListener {
                serviceScope.launch {
                    val nextDna = repository.rotateToNextSessionDna("floating_overlay")
                    text = "🧬 ${nextDna.badgeText} (${nextDna.quadrantLabel}) — Ganti DNA"
                    Toast.makeText(
                        applicationContext,
                        "🧬 ${nextDna.badgeText} Aktif!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        this@with.btnSessionDnaQuickView = btnSessionDnaQuick
        boosterCard.addView(btnSessionDnaQuick)

        tabVideoContainer.addView(boosterCard)

        tabContentHolder.addView(tabVideoContainer)

        // ---------------------------------------------------------
        // 4. TAB 2: 🎵 MUSIK TAB CONTAINER (STANDALONE PLAYER)
        // ---------------------------------------------------------
        val tabMusicContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerTabMusic = tabMusicContainer

        val musicCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#180B2B"))
                setStroke(dp(1), Color.parseColor("#A855F7"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val mTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val mTitle = TextView(this).apply {
            text = "Belum ada lagu dipilih"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                isQuickAudioListOpen = !isQuickAudioListOpen
                quickAudioListWrapper?.visibility = if (isQuickAudioListOpen) View.VISIBLE else View.GONE
                val cnt = repository.config.value.audioPlaylist.size
                quickAudioListToggleBtn?.text = if (isQuickAudioListOpen) "📋 Daftar ($cnt) ▴" else "📋 Daftar ($cnt) ▾"
            }
        }
        musicTrackTitleText = mTitle
        mTitleRow.addView(mTitle)

        val mBadge = TextView(this).apply {
            text = "STANDALONE"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        musicStatusBadge = mBadge
        mTitleRow.addView(mBadge)
        musicCard.addView(mTitleRow)

        val mSeekBar = SeekBar(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val dur = repository.musicPlayer.durationMs.value
                        musicTimeLabel?.text = "${formatTime(progress.toLong())} / ${formatTime(dur)}"
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isUserTrackingMusicSeek = true
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isUserTrackingMusicSeek = false
                    val pos = seekBar?.progress?.toLong() ?: 0L
                    repository.musicPlayer.seekTo(pos)
                }
            })
        }
        musicSeekBar = mSeekBar
        musicCard.addView(mSeekBar)

        val mControlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val mTimeLbl = TextView(this).apply {
            text = "00:00 / 00:00"
            textSize = 9f
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        musicTimeLabel = mTimeLbl
        mControlRow.addView(mTimeLbl)

        val btnMPrev = TextView(this).apply {
            text = "⏮️"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                navigatePrevAudio()
            }
        }
        mControlRow.addView(btnMPrev)

        val btnMPlayPause = TextView(this).apply {
            text = " ▶️ Putar "
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                val willPlay = !repository.musicPlayer.isPlaying.value
                text = if (willPlay) " ⏸️ Jeda " else " ▶️ Putar "
                musicStatusBadge?.apply {
                    text = if (willPlay) "SEDANG DIPUTAR" else "DIJEDA"
                    setTextColor(if (willPlay) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
                }
                serviceScope.launch {
                    val isPaused = repository.toggleMp3Audio()
                    Toast.makeText(applicationContext, if (isPaused) "Musik dijeda ⏸️" else "Memutar musik 🎵", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnMusicPlayPauseText = btnMPlayPause
        mControlRow.addView(btnMPlayPause)

        val btnMNext = TextView(this).apply {
            text = "⏭️"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                navigateNextAudio()
            }
        }
        mControlRow.addView(btnMNext)

        val btnToggleAudList = TextView(this).apply {
            val totalAud = repository.config.value.audioPlaylist.size
            text = "📋 Daftar ($totalAud) ▾"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#3B0764"))
                setStroke(dp(1), Color.parseColor("#A855F7"))
            }
            setOnClickListener {
                isQuickAudioListOpen = !isQuickAudioListOpen
                quickAudioListWrapper?.visibility = if (isQuickAudioListOpen) View.VISIBLE else View.GONE
                val cnt = repository.config.value.audioPlaylist.size
                text = if (isQuickAudioListOpen) "📋 Daftar ($cnt) ▴" else "📋 Daftar ($cnt) ▾"
                if (isQuickAudioListOpen) {
                    buildQuickAudioListItems(repository.config.value.audioPlaylist, repository.config.value.currentAudioIndex)
                }
            }
        }
        quickAudioListToggleBtn = btnToggleAudList
        btnMusicLoopText = null
        mControlRow.addView(btnToggleAudList)

        musicCard.addView(mControlRow)

        // Baris 4-Tombol Mode Audio (Urut / SR+AX / Loop 1 / VSA) — Live Tanpa Buka APK
        val mModeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
        }
        musicModeBtnMap.clear()
        com.tajasb.infinity.IfsAudioMode.values().forEachIndexed { idx, modeOpt ->
            val btnMode = TextView(this).apply {
                text = modeOpt.badge
                textSize = 8.5f
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(5), dp(4), dp(5))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < com.tajasb.infinity.IfsAudioMode.values().size - 1) marginEnd = dp(3)
                }
                layoutParams = lp
                setOnClickListener {
                    val ifsMgr = com.tajasb.infinity.IfsManager.getInstance(applicationContext)
                    ifsMgr.setAudioMode(modeOpt)
                    if (modeOpt == com.tajasb.infinity.IfsAudioMode.SV4_VS && !ifsMgr.ifsState.value.sv4AudioRunning) {
                        ifsMgr.startSv4AudioEngine(resetCounter = false)
                    }
                    updateMusicPlayerUi()
                    Toast.makeText(applicationContext, "🎵 Mode: ${modeOpt.title}", Toast.LENGTH_SHORT).show()
                }
            }
            musicModeBtnMap[modeOpt] = btnMode
            mModeRow.addView(btnMode)
        }
        musicCard.addView(mModeRow)

        val btnSv4AudioSlotSetup = TextView(this).apply {
            val totalLA = repository.config.value.audioPlaylist.count { !it.id.startsWith("vs_audio_rolling_") }
            val selLACount = com.tajasb.infinity.IfsManager.getInstance(applicationContext).sv4AudioLIds.value.size.let { if (it > 0) it else totalLA }
            text = "⚙️ Atur Slot LA ($selLACount Trek) ▾"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F5D0FE"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#3B0764"))
                setStroke(dp(1), Color.parseColor("#E879F9"))
            }
            setOnClickListener {
                showAudioLaSlotPickerOverlay(this@with)
            }
        }
        btnSv4AudioSlotSetupText = btnSv4AudioSlotSetup
        musicCard.addView(btnSv4AudioSlotSetup)

        // --- PENGATURAN MANDIRI LOOP SV4 AUDIO (sv4 ... sampai ...) ---
        val ifsMgr = com.tajasb.infinity.IfsManager.getInstance(applicationContext)
        val sv4AudioLoopCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            setPadding(dp(7), dp(5), dp(7), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#180D2B"))
                setStroke(dp(1), Color.parseColor("#86198F"))
            }
        }

        val tvSv4AudioLoopHeader = TextView(this).apply {
            val minAL = ifsMgr.sv4AudioMinLoops.value
            val maxAL = ifsMgr.sv4AudioMaxLoops.value
            text = "🎵 Loop SV4 Audio: $minAL s/d $maxAL Putaran"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F5D0FE"))
        }
        sv4AudioLoopCard.addView(tvSv4AudioLoopHeader)

        val sv4AudioLoopStepRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        fun updateSv4AudioHeader() {
            val curMin = ifsMgr.sv4AudioMinLoops.value
            val curMax = ifsMgr.sv4AudioMaxLoops.value
            tvSv4AudioLoopHeader.text = "🎵 Loop SV4 Audio: $curMin s/d $curMax Putaran"
        }

        val btnAudMinMinus = TextView(this).apply {
            text = "➖"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            minWidth = dp(28)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#2E1065"))
                setStroke(dp(1), Color.parseColor("#7C3AED"))
            }
            setOnClickListener {
                val curMin = ifsMgr.sv4AudioMinLoops.value
                val curMax = ifsMgr.sv4AudioMaxLoops.value
                val newMin = (curMin - 1).coerceAtLeast(1)
                ifsMgr.setSv4AudioLoopRange(newMin, maxOf(newMin, curMax))
                updateSv4AudioHeader()
                Toast.makeText(applicationContext, "🎵 SV4 Audio: $newMin sampai ${maxOf(newMin, curMax)}", Toast.LENGTH_SHORT).show()
            }
        }
        sv4AudioLoopStepRow.addView(btnAudMinMinus)

        val tvAudMinVal = TextView(this).apply {
            text = "Min"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#DDD6FE"))
            setPadding(dp(4), 0, dp(4), 0)
        }
        sv4AudioLoopStepRow.addView(tvAudMinVal)

        val btnAudMinPlus = TextView(this).apply {
            text = "➕"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            minWidth = dp(28)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#2E1065"))
                setStroke(dp(1), Color.parseColor("#7C3AED"))
            }
            setOnClickListener {
                val curMin = ifsMgr.sv4AudioMinLoops.value
                val curMax = ifsMgr.sv4AudioMaxLoops.value
                val newMin = curMin + 1
                val newMax = maxOf(newMin, curMax)
                ifsMgr.setSv4AudioLoopRange(newMin, newMax)
                updateSv4AudioHeader()
                Toast.makeText(applicationContext, "🎵 SV4 Audio: $newMin sampai $newMax", Toast.LENGTH_SHORT).show()
            }
        }
        sv4AudioLoopStepRow.addView(btnAudMinPlus)

        val tvAudSampai = TextView(this).apply {
            text = "sampai"
            textSize = 8f
            setTextColor(Color.parseColor("#A78BFA"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        sv4AudioLoopStepRow.addView(tvAudSampai)

        val btnAudMaxMinus = TextView(this).apply {
            text = "➖"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            minWidth = dp(28)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#2E1065"))
                setStroke(dp(1), Color.parseColor("#7C3AED"))
            }
            setOnClickListener {
                val curMin = ifsMgr.sv4AudioMinLoops.value
                val curMax = ifsMgr.sv4AudioMaxLoops.value
                val newMax = (curMax - 1).coerceAtLeast(curMin)
                ifsMgr.setSv4AudioLoopRange(minOf(curMin, newMax), newMax)
                updateSv4AudioHeader()
                Toast.makeText(applicationContext, "🎵 SV4 Audio: ${minOf(curMin, newMax)} sampai $newMax", Toast.LENGTH_SHORT).show()
            }
        }
        sv4AudioLoopStepRow.addView(btnAudMaxMinus)

        val tvAudMaxVal = TextView(this).apply {
            text = "Max"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#DDD6FE"))
            setPadding(dp(4), 0, dp(4), 0)
        }
        sv4AudioLoopStepRow.addView(tvAudMaxVal)

        val btnAudMaxPlus = TextView(this).apply {
            text = "➕"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            minWidth = dp(28)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#2E1065"))
                setStroke(dp(1), Color.parseColor("#7C3AED"))
            }
            setOnClickListener {
                val curMin = ifsMgr.sv4AudioMinLoops.value
                val curMax = ifsMgr.sv4AudioMaxLoops.value
                val newMax = curMax + 1
                ifsMgr.setSv4AudioLoopRange(curMin, newMax)
                updateSv4AudioHeader()
                Toast.makeText(applicationContext, "🎵 SV4 Audio: $curMin sampai $newMax", Toast.LENGTH_SHORT).show()
            }
        }
        sv4AudioLoopStepRow.addView(btnAudMaxPlus)

        sv4AudioLoopCard.addView(sv4AudioLoopStepRow)

        // Preset Cepat SV4 Audio: [1..1] [1..2] [1..3] [2..4]
        val sv4AudPresetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }
        fun createAudPresetBtn(pMin: Int, pMax: Int): TextView {
            return TextView(this).apply {
                text = if (pMin == pMax) "${pMin}x" else "${pMin}..${pMax}x"
                textSize = 7.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(3), dp(4), dp(3))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(2)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#3B0764"))
                    setStroke(dp(1), Color.parseColor("#C084FC"))
                }
                setOnClickListener {
                    ifsMgr.setSv4AudioLoopRange(pMin, pMax)
                    updateSv4AudioHeader()
                    Toast.makeText(applicationContext, "🎵 SV4 Audio: $pMin sampai $pMax putaran", Toast.LENGTH_SHORT).show()
                }
            }
        }
        sv4AudPresetRow.addView(createAudPresetBtn(1, 1))
        sv4AudPresetRow.addView(createAudPresetBtn(1, 2))
        sv4AudPresetRow.addView(createAudPresetBtn(1, 3))
        sv4AudPresetRow.addView(createAudPresetBtn(2, 4))
        sv4AudioLoopCard.addView(sv4AudPresetRow)

        musicCard.addView(sv4AudioLoopCard)

        // Baris Aksi Cepat: Buka/Tutup Semua (👁️), Mute Semua (🔊), dan Volume (- / +)
        val audQuickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
        }

        val btnEyeToggleAllAudio = TextView(this).apply {
            val allAudioClosed = repository.config.value.audioPlaylist.isNotEmpty() && repository.config.value.audioPlaylist.all { !it.isEnabled }
            text = if (allAudioClosed) "🙈 Off" else "👁️ On"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(3)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (allAudioClosed) {
                    setColor(Color.parseColor("#450A0A"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#1E1B4B"))
                    setStroke(dp(1), Color.parseColor("#818CF8"))
                }
            }
            setOnClickListener {
                serviceScope.launch {
                    val targetEnabled = repository.toggleAllAudioEnabled()
                    Toast.makeText(applicationContext, if (targetEnabled) "Semua Audio: ON 👁️" else "Semua Audio: OFF 🙈", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnToggleAllAudioEyeText = btnEyeToggleAllAudio
        audQuickRow.addView(btnEyeToggleAllAudio)

        val btnAudMuteAll = TextView(this).apply {
            val allAudioMuted = if (repository.config.value.audioPlaylist.isNotEmpty()) {
                repository.config.value.audioPlaylist.all { it.isMuted }
            } else {
                repository.config.value.isMp3AudioMuted
            }
            text = if (allAudioMuted) "🔇 Mute" else "🔊 Suara"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (allAudioMuted) {
                    setColor(Color.parseColor("#450A0A"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#083344"))
                    setStroke(dp(1), Color.parseColor("#00E5FF"))
                }
            }
            setOnClickListener {
                serviceScope.launch {
                    val muted = repository.toggleAllAudioMute()
                    Toast.makeText(applicationContext, if (muted) "Musik Dimute 🔇" else "Musik Bersuara 🔊", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnAudioMuteAllText = btnAudMuteAll
        audQuickRow.addView(btnAudMuteAll)

        // Tombol Volume Musik (- / + saja, tanpa SeekBar)
        val btnMusVolMinus = TextView(this).apply {
            text = "➖"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setOnClickListener {
                val curPct = (repository.musicPlayer.volume.value * 100).toInt()
                val nextPct = (curPct - 10).coerceIn(0, 100)
                repository.setAudioVolume(nextPct / 100f)
                musicVolumeLabel?.text = "🔊 $nextPct%"
            }
        }
        audQuickRow.addView(btnMusVolMinus)

        val mVolLbl = TextView(this).apply {
            val initPct = (repository.musicPlayer.volume.value * 100).toInt()
            text = "🔊 $initPct%"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        musicVolumeLabel = mVolLbl
        musicVolumeSeekBar = null
        audQuickRow.addView(mVolLbl)

        val btnMusVolPlus = TextView(this).apply {
            text = "➕"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#3B0764"))
                setStroke(dp(1), Color.parseColor("#D946EF"))
            }
            setOnClickListener {
                val curPct = (repository.musicPlayer.volume.value * 100).toInt()
                val nextPct = (curPct + 10).coerceIn(0, 100)
                repository.setAudioVolume(nextPct / 100f)
                musicVolumeLabel?.text = "🔊 $nextPct%"
            }
        }
        audQuickRow.addView(btnMusVolPlus)

        musicCard.addView(audQuickRow)

        // Baris 5-Tombol Karakter Suara Live (Normal / Cowok / Cewek / Chipmunk / Robot)
        val voiceEffectRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
        }
        musicVoiceEffectBtnMap.clear()
        com.tajasb.model.VoiceEffect.values().forEachIndexed { idx, effect ->
            val btnEff = TextView(this).apply {
                text = "${effect.icon} ${effect.label}"
                textSize = 8f
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(3), dp(4), dp(3), dp(4))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < com.tajasb.model.VoiceEffect.values().size - 1) marginEnd = dp(2)
                }
                layoutParams = lp
                setOnClickListener {
                    serviceScope.launch {
                        repository.setVoiceEffect(effect)
                        updateMusicPlayerUi()
                        Toast.makeText(applicationContext, "🎭 Suara: ${effect.icon} ${effect.label}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            musicVoiceEffectBtnMap[effect] = btnEff
            voiceEffectRow.addView(btnEff)
        }
        musicCard.addView(voiceEffectRow)

        val audAntiHashRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.VISIBLE
            setPadding(0, dp(5), 0, 0)
        }
        this@with.audAntiHashRowView = audAntiHashRow

        fun updateAudAntiHashBtnText(btn: TextView) {
            val enabled = repository.config.value.isAudioAntiHashEnabled
            val mode = repository.config.value.audioAntiHashMode
            btn.text = if (enabled) "🛡️ Anti-Detek Audio: ON (${mode.displayName.substringBefore(" ")})" else "🛡️ Anti-Detek Audio: OFF"
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (enabled) {
                    setColor(Color.parseColor("#064E3B"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                } else {
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
            }
            btn.setTextColor(if (enabled) Color.parseColor("#34D399") else Color.parseColor("#94A3B8"))
        }

        val btnAudAntiHash = TextView(this).apply {
            textSize = 9f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            updateAudAntiHashBtnText(this)
            setOnClickListener {
                serviceScope.launch {
                    val cfg = repository.config.value
                    if (!cfg.isAudioAntiHashEnabled) {
                        repository.toggleAudioAntiHash(true)
                    } else {
                        when (cfg.audioAntiHashMode) {
                            com.tajasb.model.AudioAntiHashIntensity.LOW -> repository.setAudioAntiHashMode(com.tajasb.model.AudioAntiHashIntensity.MEDIUM)
                            com.tajasb.model.AudioAntiHashIntensity.MEDIUM -> repository.setAudioAntiHashMode(com.tajasb.model.AudioAntiHashIntensity.AGGRESSIVE)
                            com.tajasb.model.AudioAntiHashIntensity.AGGRESSIVE -> repository.toggleAudioAntiHash(false)
                        }
                    }
                    updateAudAntiHashBtnText(this@apply)
                }
            }
        }
        audAntiHashRow.addView(btnAudAntiHash)
        musicCard.addView(audAntiHashRow)

        val audDualRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        val btnAudDual = TextView(this).apply {
            textSize = 9f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            val updateBg: (Boolean) -> Unit = { isDual ->
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    if (isDual) {
                        setColor(Color.parseColor("#064E3B"))
                        setStroke(dp(1), Color.parseColor("#10B981"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#475569"))
                    }
                }
                setTextColor(if (isDual) Color.parseColor("#34D399") else Color.parseColor("#94A3B8"))
                text = if (isDual) "🎧 Dual Audio: ON (Video + MP3)" else "🎧 Dual Audio: OFF (Auto-Jeda)"
            }
            updateBg(repository.config.value.isDualAudioMixingEnabled)
            setOnClickListener {
                serviceScope.launch {
                    val next = repository.toggleDualAudioMixing()
                    updateBg(next)
                    updateUiForConfig(repository.config.value)
                }
            }
        }
        btnDualAudioMixingMusicText = btnAudDual
        audDualRow.addView(btnAudDual)
        musicCard.addView(audDualRow)

        // Baris Audio Tipe 2 (Tidak Terikat Mode): Aktifkan Layer 2 secara opsional saat Tipe 1 berjalan
        val audLayer2Row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
        }

        val btnToggleL2 = TextView(this).apply {
            textSize = 8.5f
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val next = repository.toggleAudioLayer2()
                    updateMusicPlayerUi()
                    Toast.makeText(
                        applicationContext,
                        if (next) "🎧 Tipe 2: Aktif" else "🔇 Tipe 2: Nonaktif",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        btnToggleAudioLayer2Text = btnToggleL2
        audLayer2Row.addView(btnToggleL2)

        val btnL2PlayPause = TextView(this).apply {
            textSize = 8.5f
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            text = "▶/⏸️"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setTextColor(Color.WHITE)
            setOnClickListener {
                val playing = repository.toggleLayer2PlayPause()
                updateMusicPlayerUi()
                Toast.makeText(applicationContext, if (playing) "▶ Tipe 2: Putar" else "⏸ Tipe 2: Jeda", Toast.LENGTH_SHORT).show()
            }
        }
        btnAudioLayer2PlayPauseText = btnL2PlayPause
        audLayer2Row.addView(btnL2PlayPause)

        val btnL2PickTrack = TextView(this).apply {
            textSize = 8.5f
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(3)
            }
            layoutParams = lp
            text = "🎵 Trek ▾"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setTextColor(Color.WHITE)
            setOnClickListener {
                showAudioLayer2PickerOverlay(this@with)
            }
        }
        audLayer2Row.addView(btnL2PickTrack)

        val btnL2Plus = TextView(this).apply {
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(3)
            }
            layoutParams = lp
            text = "➕ T2"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#3B0764"))
                setStroke(dp(1), Color.parseColor("#D946EF"))
            }
            setTextColor(Color.WHITE)
            setOnClickListener {
                showAudioLayer2PickerOverlay(this@with)
            }
        }
        audLayer2Row.addView(btnL2Plus)

        musicCard.addView(audLayer2Row)

        val audLayer2VolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        val l2VolTitle = TextView(this).apply {
            text = "Vol Tipe 2:"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#34D399"))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        audLayer2VolRow.addView(l2VolTitle)

        val btnL2VolMinus = TextView(this).apply {
            text = "➖"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setOnClickListener {
                val curPct = (repository.musicPlayer.volumeLayer2.value * 100).toInt()
                val nextPct = (curPct - 10).coerceIn(0, 100)
                repository.setAudioLayer2Volume(nextPct / 100f)
                audioLayer2VolumeLabel?.text = "🔊 $nextPct%"
            }
        }
        audLayer2VolRow.addView(btnL2VolMinus)

        val l2VolLabel = TextView(this).apply {
            val initPct = (repository.musicPlayer.volumeLayer2.value * 100).toInt()
            text = "🔊 $initPct%"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(4), dp(7), dp(4))
        }
        audioLayer2VolumeLabel = l2VolLabel
        audLayer2VolRow.addView(l2VolLabel)

        val btnL2VolPlus = TextView(this).apply {
            text = "➕"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setOnClickListener {
                val curPct = (repository.musicPlayer.volumeLayer2.value * 100).toInt()
                val nextPct = (curPct + 10).coerceIn(0, 100)
                repository.setAudioLayer2Volume(nextPct / 100f)
                audioLayer2VolumeLabel?.text = "🔊 $nextPct%"
            }
        }
        audLayer2VolRow.addView(btnL2VolPlus)
        musicCard.addView(audLayer2VolRow)

        // --- Baris Audio Tipe 3: Toggle & Tes Bunyi ---
        val audLayer3Row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        val btnToggleL3 = TextView(this).apply {
            textSize = 8.5f
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val next = repository.toggleAudioLayer3()
                    updateMusicPlayerUi()
                    Toast.makeText(
                        applicationContext,
                        if (next) "🔔 Tipe 3: Aktif (SFX/Ringtone)" else "🔇 Tipe 3: Nonaktif",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        btnToggleAudioLayer3Text = btnToggleL3
        audLayer3Row.addView(btnToggleL3)

        val btnL3TriggerTest = TextView(this).apply {
            textSize = 8.5f
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            text = "⚡ Tes"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#7C2D12"))
                setStroke(dp(1), Color.parseColor("#EA580C"))
            }
            setTextColor(Color.WHITE)
            setOnClickListener {
                repository.triggerTestLayer3Ringtone()
                updateMusicPlayerUi()
                Toast.makeText(applicationContext, "⚡ Mengetes Bunyi Tipe 3", Toast.LENGTH_SHORT).show()
            }
        }
        btnTriggerTestLayer3Text = btnL3TriggerTest
        audLayer3Row.addView(btnL3TriggerTest)

        val btnL3PickTrack = TextView(this).apply {
            textSize = 8.5f
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(3)
            }
            layoutParams = lp
            text = "🔔 SFX ▾"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#7C2D12"))
                setStroke(dp(1), Color.parseColor("#EA580C"))
            }
            setTextColor(Color.WHITE)
            setOnClickListener {
                showAudioLayer3PickerOverlay(this@with)
            }
        }
        audLayer3Row.addView(btnL3PickTrack)

        musicCard.addView(audLayer3Row)

        // --- Baris Volume Audio Tipe 3 ---
        val audLayer3VolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        val l3VolTitle = TextView(this).apply {
            text = "Vol Tipe 3:"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FB923C"))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        audLayer3VolRow.addView(l3VolTitle)

        val btnL3VolMinus = TextView(this).apply {
            text = "➖"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setOnClickListener {
                val curPct = (repository.musicPlayer.volumeLayer3.value * 100).toInt()
                val nextPct = (curPct - 10).coerceIn(0, 100)
                repository.setAudioLayer3Volume(nextPct / 100f)
                audioLayer3VolumeLabel?.text = "🔔 $nextPct%"
            }
        }
        audLayer3VolRow.addView(btnL3VolMinus)

        val l3VolLabel = TextView(this).apply {
            val initPct = (repository.musicPlayer.volumeLayer3.value * 100).toInt()
            text = "🔔 $initPct%"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(4), dp(7), dp(4))
        }
        audioLayer3VolumeLabel = l3VolLabel
        audLayer3VolRow.addView(l3VolLabel)

        val btnL3VolPlus = TextView(this).apply {
            text = "➕"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#7C2D12"))
                setStroke(dp(1), Color.parseColor("#EA580C"))
            }
            setOnClickListener {
                val curPct = (repository.musicPlayer.volumeLayer3.value * 100).toInt()
                val nextPct = (curPct + 10).coerceIn(0, 100)
                repository.setAudioLayer3Volume(nextPct / 100f)
                audioLayer3VolumeLabel?.text = "🔔 $nextPct%"
            }
        }
        audLayer3VolRow.addView(btnL3VolPlus)

        musicCard.addView(audLayer3VolRow)

        // --- Baris Interval SFX Tipe 3 ---
        val audLayer3SpeedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }
        val l3SpeedTitle = TextView(this).apply {
            text = "⏱️ SFX:"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FB923C"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(4)
            }
            layoutParams = lp
        }
        audLayer3SpeedRow.addView(l3SpeedTitle)

        val sfxIntervalPresets = listOf(
            Pair(5, 15) to "5-15s",
            Pair(10, 25) to "10-25s",
            Pair(30, 60) to "30-60s",
            Pair(60, 120) to "1-2m"
        )
        sfxIntervalPresets.forEach { (range, lbl) ->
            val (minS, maxS) = range
            val btnPreset = TextView(this).apply {
                val curMin = repository.config.value.audioLayer3MinIntervalSec
                val curMax = repository.config.value.audioLayer3MaxIntervalSec
                val isSel = (curMin == minS && curMax == maxS)
                text = lbl
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(3), dp(4), dp(3))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(2)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isSel) {
                        setColor(Color.parseColor("#7C2D12"))
                        setStroke(dp(1), Color.parseColor("#EA580C"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#475569"))
                    }
                }
                setTextColor(if (isSel) Color.WHITE else Color.parseColor("#94A3B8"))
                setOnClickListener {
                    serviceScope.launch {
                        repository.setAudioLayer3IntervalRange(minS, maxS)
                        Toast.makeText(applicationContext, "⏱️ Interval SFX: $lbl", Toast.LENGTH_SHORT).show()
                        updateMusicPlayerUi()
                    }
                }
            }
            audLayer3SpeedRow.addView(btnPreset)
        }
        musicCard.addView(audLayer3SpeedRow)

        // --- Baris Dinamika Mic Drift (Anti Deteksi Akustik Human) ---
        val audHumanDriftRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }
        val lblDrift = TextView(this).apply {
            text = "🎙️ Mic Drift:"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(4)
            }
            layoutParams = lp
        }
        audHumanDriftRow.addView(lblDrift)

        val driftModes = listOf(
            com.tajasb.model.AudioAntiHashIntensity.LOW to "10%",
            com.tajasb.model.AudioAntiHashIntensity.MEDIUM to "20%",
            com.tajasb.model.AudioAntiHashIntensity.AGGRESSIVE to "30%"
        )
        val btnDriftOff = TextView(this).apply {
            val isOff = !repository.config.value.isAudioAntiHashEnabled
            text = "Off"
            textSize = 8f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(3), dp(4), dp(3))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                if (isOff) {
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#64748B"))
                } else {
                    setColor(Color.parseColor("#0F172A"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
            }
            setTextColor(if (isOff) Color.WHITE else Color.parseColor("#64748B"))
            setOnClickListener {
                serviceScope.launch {
                    repository.toggleAudioAntiHash(false)
                    Toast.makeText(applicationContext, "🎙️ Mic Drift: Nonaktif", Toast.LENGTH_SHORT).show()
                }
            }
        }
        audHumanDriftRow.addView(btnDriftOff)

        driftModes.forEach { (mode, lbl) ->
            val btnDrift = TextView(this).apply {
                val isSel = repository.config.value.isAudioAntiHashEnabled && repository.config.value.audioAntiHashMode == mode
                text = lbl
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(3), dp(4), dp(3))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(2)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isSel) {
                        setColor(Color.parseColor("#0369A1"))
                        setStroke(dp(1), Color.parseColor("#38BDF8"))
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                        setStroke(dp(1), Color.parseColor("#334155"))
                    }
                }
                setTextColor(if (isSel) Color.WHITE else Color.parseColor("#94A3B8"))
                setOnClickListener {
                    serviceScope.launch {
                        repository.toggleAudioAntiHash(true)
                        repository.setAudioAntiHashMode(mode)
                        Toast.makeText(applicationContext, "🎙️ Mic Drift: $lbl", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            audHumanDriftRow.addView(btnDrift)
        }
        musicCard.addView(audHumanDriftRow)

        // ==========================================
        // 🎛️ LIVE REALTIME AUDIO EDITOR (PLUS-MINUS STEPPERS, ZERO SLIDERS)
        // ==========================================
        var isRealtimeEditorExpanded = false
        val realtimeEditorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0D1527"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val btnToggleRealtimeEditor = TextView(this).apply {
            text = "🎛️ Edit Suara Realtime (Plus-Minus) ▾"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E0F2FE"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0C4A6E"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                isRealtimeEditorExpanded = !isRealtimeEditorExpanded
                realtimeEditorContainer.visibility = if (isRealtimeEditorExpanded) View.VISIBLE else View.GONE
                text = if (isRealtimeEditorExpanded) "🎛️ Edit Suara Realtime (Plus-Minus) ▴" else "🎛️ Edit Suara Realtime (Plus-Minus) ▾"
            }
        }
        musicCard.addView(btnToggleRealtimeEditor)

        fun refreshRealtimeLabels() {
            val tempo = repository.musicPlayer.realtimeTempo.value
            realtimeTempoLabel?.text = String.format(java.util.Locale.US, "%.2fx", tempo)
            val pitch = repository.musicPlayer.realtimeFinePitch.value
            realtimePitchLabel?.text = "${if (pitch >= 0) "+" else ""}$pitch%"
            val intro = repository.musicPlayer.realtimeSkipIntroSec.value
            realtimeIntroLabel?.text = "${intro}s"
            val outro = repository.musicPlayer.realtimeCutOutroSec.value
            realtimeOutroLabel?.text = "${outro}s"
        }

        // Header Realtime Editor: Judul & Reset
        val editorHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
        }
        val editorTitle = TextView(this).apply {
            text = "⚡ Edit Suara Langsung (Tanpa Render)"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#7DD3FC"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        editorHeaderRow.addView(editorTitle)

        val btnResetEditor = TextView(this).apply {
            text = "↩️ Reset"
            textSize = 8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCA5A5"))
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#450A0A"))
                setStroke(dp(1), Color.parseColor("#EF4444"))
            }
            setOnClickListener {
                repository.musicPlayer.resetRealtimeAudioEdits()
                refreshRealtimeLabels()
                Toast.makeText(applicationContext, "↩️ Edit audio di-reset ke normal", Toast.LENGTH_SHORT).show()
            }
        }
        editorHeaderRow.addView(btnResetEditor)
        realtimeEditorContainer.addView(editorHeaderRow)

        // Helper untuk membuat baris stepper [Label] [ ➖ ] [ Nilai ] [ ➕ ]
        fun createStepperRow(
            label: String,
            initialValueText: String,
            onMinus: () -> Unit,
            onPlus: () -> Unit
        ): Pair<LinearLayout, TextView> {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(3)
                }
                layoutParams = lp
            }
            val lblName = TextView(this).apply {
                text = label
                textSize = 8.5f
                setTextColor(Color.parseColor("#E2E8F0"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(lblName)

            val btnMinus = TextView(this).apply {
                text = "➖"
                textSize = 9f
                gravity = Gravity.CENTER
                setPadding(dp(7), dp(3), dp(7), dp(3))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
                setTextColor(Color.WHITE)
                setOnClickListener {
                    onMinus()
                    refreshRealtimeLabels()
                }
            }
            row.addView(btnMinus)

            val lblValue = TextView(this).apply {
                text = initialValueText
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#38BDF8"))
                val lp = LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT)
                layoutParams = lp
            }
            row.addView(lblValue)

            val btnPlus = TextView(this).apply {
                text = "➕"
                textSize = 9f
                gravity = Gravity.CENTER
                setPadding(dp(7), dp(3), dp(7), dp(3))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#0C4A6E"))
                    setStroke(dp(1), Color.parseColor("#0284C7"))
                }
                setTextColor(Color.WHITE)
                setOnClickListener {
                    onPlus()
                    refreshRealtimeLabels()
                }
            }
            row.addView(btnPlus)

            return Pair(row, lblValue)
        }

        // Stepper 1: Kecepatan (Tempo)
        val (tempoRow, tempoLbl) = createStepperRow(
            label = "Kecepatan (Tempo):",
            initialValueText = String.format(java.util.Locale.US, "%.2fx", repository.musicPlayer.realtimeTempo.value),
            onMinus = { repository.musicPlayer.adjustRealtimeTempo(-0.05f) },
            onPlus = { repository.musicPlayer.adjustRealtimeTempo(0.05f) }
        )
        realtimeTempoLabel = tempoLbl
        realtimeEditorContainer.addView(tempoRow)

        // Stepper 2: Nada Vokal (Fine Pitch)
        val (pitchRow, pitchLbl) = createStepperRow(
            label = "Nada Vokal (Pitch):",
            initialValueText = "${repository.musicPlayer.realtimeFinePitch.value.toInt()}%",
            onMinus = { repository.musicPlayer.adjustRealtimeFinePitch(-2f) },
            onPlus = { repository.musicPlayer.adjustRealtimeFinePitch(2f) }
        )
        realtimePitchLabel = pitchLbl
        realtimeEditorContainer.addView(pitchRow)

        // Stepper 3: Potong Awal (Skip Intro)
        val (introRow, introLbl) = createStepperRow(
            label = "Skip Intro Awal:",
            initialValueText = "${repository.musicPlayer.realtimeSkipIntroSec.value}s",
            onMinus = { repository.musicPlayer.adjustRealtimeSkipIntroSec(-1) },
            onPlus = { repository.musicPlayer.adjustRealtimeSkipIntroSec(1) }
        )
        realtimeIntroLabel = introLbl
        realtimeEditorContainer.addView(introRow)

        // Stepper 4: Potong Akhir (Cut Outro)
        val (outroRow, outroLbl) = createStepperRow(
            label = "Cut Outro Akhir:",
            initialValueText = "${repository.musicPlayer.realtimeCutOutroSec.value}s",
            onMinus = { repository.musicPlayer.adjustRealtimeCutOutroSec(-1) },
            onPlus = { repository.musicPlayer.adjustRealtimeCutOutroSec(1) }
        )
        realtimeOutroLabel = outroLbl
        realtimeEditorContainer.addView(outroRow)

        musicCard.addView(realtimeEditorContainer)

        tabMusicContainer.addView(musicCard)

        val aListWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0B0F19"))
                setStroke(dp(1), Color.parseColor("#4C1D95"))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        quickAudioListWrapper = aListWrapper

        val aSearchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
        }
        val aSearchInput = android.widget.EditText(this).apply {
            hint = "🔍 Cari lagu / surah..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 9.5f
            isSingleLine = true
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnFocusChangeListener { _, hasFocus ->
                setPanelFocusable(hasFocus)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    quickAudioSearchQuery = s?.toString()?.trim() ?: ""
                    buildQuickAudioListItems(repository.config.value.audioPlaylist, repository.config.value.currentAudioIndex)
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        aSearchRow.addView(aSearchInput)
        aListWrapper.addView(aSearchRow)

        val aListScroll = ScrollView(this).apply {
            val scrollLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130))
            layoutParams = scrollLp
            isNestedScrollingEnabled = true
            isVerticalScrollBarEnabled = true
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                v.onTouchEvent(event)
            }
        }
        quickAudioListScrollView = aListScroll

        val aListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        quickAudioListContainer = aListContainer
        aListScroll.addView(aListContainer)
        aListWrapper.addView(aListScroll)
        tabMusicContainer.addView(aListWrapper)

        tabContentHolder.addView(tabMusicContainer)

        // ---------------------------------------------------------
        // 5. TAB 3: 🎨 VISUAL TAB CONTAINER
        // ---------------------------------------------------------
        val tabVisualContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerTabVisual = tabVisualContainer

        // KARTU 1: 📐 TRANSFORMASI LAYAR & KAMERA
        val transformCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val rotLabel = TextView(this).apply {
            text = "🔄 Rotasi Layar Kamera:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, dp(3))
        }
        transformCard.addView(rotLabel)

        val rotRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }

        fun createRotBtn(deg: Int): TextView {
            return TextView(this).apply {
                text = "$deg°"
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(6), dp(2), dp(6))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                setOnClickListener {
                    serviceScope.launch {
                        repository.setRotation(deg)
                        Toast.makeText(applicationContext, "Rotasi: $deg°", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnRot0 = createRotBtn(0)
        btnRot90 = createRotBtn(90)
        btnRot180 = createRotBtn(180)
        btnRot270 = createRotBtn(270)

        rotRow.addView(btnRot0)
        rotRow.addView(btnRot90)
        rotRow.addView(btnRot180)
        rotRow.addView(btnRot270)
        transformCard.addView(rotRow)

        val mirrorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }

        val btnMirror = TextView(this).apply {
            text = "↔️ Mirror: OFF"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(2) }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val m = repository.toggleMirror()
                    Toast.makeText(applicationContext, if (m) "Mirror Selfie ON" else "Mirror OFF", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnMirrorToggle = btnMirror
        mirrorRow.addView(btnMirror)

        val btnFlipV = TextView(this).apply {
            text = "↕️ Flip V: OFF"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(2) }
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val f = repository.toggleFlipVertical()
                    Toast.makeText(applicationContext, if (f) "Flip V ON" else "Flip V OFF", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnFlipVToggle = btnFlipV
        mirrorRow.addView(btnFlipV)
        transformCard.addView(mirrorRow)

        val scaleLabel = TextView(this).apply {
            text = "📐 Mode Skala Layar:"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, dp(3))
        }
        transformCard.addView(scaleLabel)

        val scaleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }

        fun createScaleBtn(mode: ScaleMode, label: String): TextView {
            return TextView(this).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(6), dp(2), dp(6))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                setOnClickListener {
                    serviceScope.launch {
                        repository.setScaleMode(mode)
                        Toast.makeText(applicationContext, "Skala: ${mode.label}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnScaleCrop = createScaleBtn(ScaleMode.CROP, "🔲 CROP")
        btnScaleFit = createScaleBtn(ScaleMode.FIT, "🔳 FIT")
        btnScaleStretch = createScaleBtn(ScaleMode.STRETCH, "↔️ STRETCH")

        scaleRow.addView(btnScaleCrop)
        scaleRow.addView(btnScaleFit)
        scaleRow.addView(btnScaleStretch)
        transformCard.addView(scaleRow)

        val fpsLabel = TextView(this).apply {
            text = "⚡ Kecepatan Frame (FPS):"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, dp(3))
        }
        transformCard.addView(fpsLabel)

        val fpsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
        }

        fun createFpsBtn(fpsVal: Int): TextView {
            return TextView(this).apply {
                text = "$fpsVal FPS"
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(6), dp(2), dp(6))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                setOnClickListener {
                    serviceScope.launch {
                        repository.setFps(fpsVal)
                        Toast.makeText(applicationContext, "Target Frame Rate: $fpsVal FPS ⚡", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnFps15 = createFpsBtn(15)
        btnFps24 = createFpsBtn(24)
        btnFps30 = createFpsBtn(30)
        btnFps60 = createFpsBtn(60)

        fpsRow.addView(btnFps15)
        fpsRow.addView(btnFps24)
        fpsRow.addView(btnFps30)
        fpsRow.addView(btnFps60)
        transformCard.addView(fpsRow)

        // FITUR ZOOM IN / OUT (Paling bawah sub menu rotasi & transformasi layar)
        val zoomDivider = View(this).apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(8)
                bottomMargin = dp(6)
            }
            layoutParams = lp
            setBackgroundColor(Color.parseColor("#1E293B"))
        }
        transformCard.addView(zoomDivider)

        val zoomHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(3))
        }

        val zoomLabel = TextView(this).apply {
            text = "🔍 Zoom Layar Video (Semua Mode):"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        zoomHeaderRow.addView(zoomLabel)

        val zoomBadge = TextView(this).apply {
            val curZoom = repository.config.value.zoomFactor
            text = String.format(java.util.Locale.US, "%.2fx (%d%%)", curZoom, (curZoom * 100).toInt())
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F59E0B"))
            gravity = Gravity.END
        }
        zoomValueBadgeText = zoomBadge
        zoomHeaderRow.addView(zoomBadge)
        transformCard.addView(zoomHeaderRow)

        // Stepper Buttons Row: [ ➖ -0.05x ]  [ 🎯 1.0x ]  [ ➕ +0.05x ]
        val zoomStepperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }

        val btnMinus = TextView(this).apply {
            text = "➖ Zoom Out (-0.05)"
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply { marginEnd = dp(2) }
            layoutParams = lp
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                serviceScope.launch {
                    val z = repository.adjustZoom(-0.05f)
                    Toast.makeText(applicationContext, "Zoom: ${String.format(java.util.Locale.US, "%.2f", z)}x 🔍", Toast.LENGTH_SHORT).show()
                }
            }
            setOnLongClickListener {
                serviceScope.launch {
                    val z = repository.adjustZoom(-0.15f)
                    Toast.makeText(applicationContext, "Zoom Cepat: ${String.format(java.util.Locale.US, "%.2f", z)}x 🔍", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
        btnZoomMinus = btnMinus
        zoomStepperRow.addView(btnMinus)

        val btnResetZ = TextView(this).apply {
            text = "🎯 1.0x"
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }
            layoutParams = lp
            setTextColor(Color.parseColor("#38BDF8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#0284C7"))
            }
            setOnClickListener {
                serviceScope.launch {
                    repository.setZoomFactor(1.0f)
                    Toast.makeText(applicationContext, "Zoom Direset: 1.00x (100%) 🎯", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnZoomReset = btnResetZ
        zoomStepperRow.addView(btnResetZ)

        val btnPlus = TextView(this).apply {
            text = "➕ Zoom In (+0.05)"
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply { marginStart = dp(2) }
            layoutParams = lp
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener {
                serviceScope.launch {
                    val z = repository.adjustZoom(0.05f)
                    Toast.makeText(applicationContext, "Zoom: ${String.format(java.util.Locale.US, "%.2f", z)}x 🔍", Toast.LENGTH_SHORT).show()
                }
            }
            setOnLongClickListener {
                serviceScope.launch {
                    val z = repository.adjustZoom(0.15f)
                    Toast.makeText(applicationContext, "Zoom Cepat: ${String.format(java.util.Locale.US, "%.2f", z)}x 🔍", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
        btnZoomPlus = btnPlus
        zoomStepperRow.addView(btnPlus)
        transformCard.addView(zoomStepperRow)

        // Preset chips row: [ 0.8x ] [ 1.0x ] [ 1.25x ] [ 1.5x ] [ 2.0x ]
        val zoomPresetsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(2))
        }

        fun createZoomPresetChip(presetFactor: Float, label: String): TextView {
            return TextView(this).apply {
                text = label
                textSize = 9.5f
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(4), dp(2), dp(4))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                setOnClickListener {
                    serviceScope.launch {
                        repository.setZoomFactor(presetFactor)
                        Toast.makeText(applicationContext, "Zoom: $label 🔍", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnZoomChip80 = createZoomPresetChip(0.80f, "0.8x")
        btnZoomChip100 = createZoomPresetChip(1.00f, "1.0x")
        btnZoomChip125 = createZoomPresetChip(1.25f, "1.25x")
        btnZoomChip150 = createZoomPresetChip(1.50f, "1.5x")
        btnZoomChip200 = createZoomPresetChip(2.00f, "2.0x")

        zoomPresetsRow.addView(btnZoomChip80)
        zoomPresetsRow.addView(btnZoomChip100)
        zoomPresetsRow.addView(btnZoomChip125)
        zoomPresetsRow.addView(btnZoomChip150)
        zoomPresetsRow.addView(btnZoomChip200)
        transformCard.addView(zoomPresetsRow)

        tabVisualContainer.addView(transformCard)

        // KARTU 2: 🛡️ PERISAI ANTI-DETEKSI SERVER & DNA SESI (Always Visible)
        val antiDetCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#071827"))
                setStroke(dp(1), Color.parseColor("#0369A1"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }
        this@with.antiDetCardView = antiDetCard

        val antiHashLabel = TextView(this).apply {
            text = "🛡️ Anti-Deteksi Video (Shader & DCT):"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, dp(3))
        }
        antiDetCard.addView(antiHashLabel)

        val antiHashRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        }

        val antiHashBtns = mutableMapOf<com.tajasb.model.AntiHashIntensity, TextView>()
        val tvVidPercent = TextView(this).apply {
            textSize = 9.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        fun refreshAntiHashVideoUi() {
            val curCfg = repository.config.value
            val curMode = curCfg.antiHashMode
            val effPct = if (curMode == com.tajasb.model.AntiHashIntensity.OFF) 0.0f else curCfg.videoAntiHashPercent
            tvVidPercent.text = "Video: ${String.format(java.util.Locale.US, "%.1f", effPct)}% (${curMode.badge})"
            antiHashBtns.forEach { (m, btn) ->
                val isCur = (m == curMode)
                btn.setTypeface(null, if (isCur) Typeface.BOLD else Typeface.NORMAL)
                btn.setTextColor(if (isCur) Color.BLACK else Color.WHITE)
                btn.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isCur) Color.parseColor("#38BDF8") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isCur) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                }
            }
        }

        fun createAntiHashBtn(mode: com.tajasb.model.AntiHashIntensity): TextView {
            return TextView(this).apply {
                text = mode.badge
                textSize = 9.5f
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(5), dp(2), dp(5))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                layoutParams = lp
                setOnClickListener {
                    serviceScope.launch {
                        repository.setAntiHashMode(mode)
                        refreshAntiHashVideoUi()
                        Toast.makeText(applicationContext, "🛡️ Anti-Deteksi Video: ${mode.label}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        com.tajasb.model.AntiHashIntensity.values().forEach { mode ->
            val btn = createAntiHashBtn(mode)
            antiHashBtns[mode] = btn
            antiHashRow.addView(btn)
        }
        antiDetCard.addView(antiHashRow)

        // Stepper Agresifitas Video (+/- 0.1%)
        val vidStepperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        val btnVidMinus = TextView(this).apply {
            text = "➖ 0.1%"
            textSize = 8.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
            }
            setOnClickListener {
                repository.adjustVideoAntiHashPercent(-0.1f)
                refreshAntiHashVideoUi()
            }
        }
        val btnVidPlus = TextView(this).apply {
            text = "➕ 0.1%"
            textSize = 8.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                repository.adjustVideoAntiHashPercent(+0.1f)
                refreshAntiHashVideoUi()
            }
        }
        refreshAntiHashVideoUi()
        vidStepperRow.addView(tvVidPercent)
        vidStepperRow.addView(btnVidMinus)
        vidStepperRow.addView(btnVidPlus)
        antiDetCard.addView(vidStepperRow)

        val audAntiHashLabel = TextView(this).apply {
            text = "🎵 Anti-Deteksi Audio (Pitch & Drift):"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#34D399"))
            setPadding(0, dp(2), 0, dp(3))
        }
        antiDetCard.addView(audAntiHashLabel)

        val audAntiHashSettingsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
        }

        fun createAudAntiHashBtn(
            label: String,
            isSelected: Boolean,
            onClick: suspend () -> Unit
        ): TextView {
            return TextView(this).apply {
                text = label
                textSize = 9.5f
                gravity = Gravity.CENTER
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isSelected) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    if (isSelected) {
                        setColor(Color.parseColor("#34D399"))
                        setStroke(dp(1), Color.parseColor("#10B981"))
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#334155"))
                    }
                }
                setPadding(dp(2), dp(5), dp(2), dp(5))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(1)
                }
                layoutParams = lp
                setOnClickListener {
                    serviceScope.launch {
                        onClick()
                    }
                }
            }
        }

        val isAudOff = !repository.config.value.isAudioAntiHashEnabled
        val curAudModeSetting = repository.config.value.audioAntiHashMode

        audAntiHashSettingsRow.addView(createAudAntiHashBtn("OFF", isAudOff) {
            repository.toggleAudioAntiHash(false)
            Toast.makeText(applicationContext, "Audio Anti-Deteksi: NONAKTIF", Toast.LENGTH_SHORT).show()
        })

        com.tajasb.model.AudioAntiHashIntensity.values().forEach { mode ->
            val isSel = !isAudOff && curAudModeSetting == mode
            audAntiHashSettingsRow.addView(createAudAntiHashBtn(mode.name.take(3), isSel) {
                if (!repository.config.value.isAudioAntiHashEnabled) {
                    repository.toggleAudioAntiHash(true)
                }
                repository.setAudioAntiHashMode(mode)
                Toast.makeText(applicationContext, "Audio Anti-Deteksi: ${mode.displayName}", Toast.LENGTH_SHORT).show()
            })
        }

        antiDetCard.addView(audAntiHashSettingsRow)

        // Stepper Agresifitas Audio (+/- 0.1%)
        val audStepperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
            layoutParams = lp
        }
        val tvAudPercent = TextView(this).apply {
            text = "Audio: ${String.format(java.util.Locale.US, "%.1f", repository.config.value.audioAntiHashPercent)}%"
            textSize = 9.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAudMinus = TextView(this).apply {
            text = "➖ 0.1%"
            textSize = 8.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
            }
            setOnClickListener {
                repository.adjustAudioAntiHashPercent(-0.1f)
                tvAudPercent.text = "Audio: ${String.format(java.util.Locale.US, "%.1f", repository.config.value.audioAntiHashPercent)}%"
            }
        }
        val btnAudPlus = TextView(this).apply {
            text = "➕ 0.1%"
            textSize = 8.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#1E293B"))
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                repository.adjustAudioAntiHashPercent(+0.1f)
                tvAudPercent.text = "Audio: ${String.format(java.util.Locale.US, "%.1f", repository.config.value.audioAntiHashPercent)}%"
            }
        }
        audStepperRow.addView(tvAudPercent)
        audStepperRow.addView(btnAudMinus)
        audStepperRow.addView(btnAudPlus)
        antiDetCard.addView(audStepperRow)

        // Switch Toggle Audio Tipe 2 Ikut Tipe 1
        val btnSyncLayer2 = TextView(this).apply {
            fun updateText() {
                val isSync = repository.config.value.isAudioLayer2SyncAntiHash
                text = if (isSync) "🎧 Tipe 2 Ikut Agresifitas Tipe 1: ON" else "🎧 Tipe 2 Ikut Agresifitas Tipe 1: OFF"
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(if (isSync) Color.parseColor("#065F46") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isSync) Color.parseColor("#10B981") else Color.parseColor("#334155"))
                }
            }
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            updateText()
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                val next = !repository.config.value.isAudioLayer2SyncAntiHash
                repository.setAudioLayer2SyncAntiHash(next)
                updateText()
                Toast.makeText(applicationContext, "Tipe 2 Sync Agresifitas: ${if (next) "AKTIF" else "NONAKTIF"}", Toast.LENGTH_SHORT).show()
            }
        }
        antiDetCard.addView(btnSyncLayer2)

        // BARIS TERPISAH 1: 🎯 DNA ZOOM & FRAMING BEBAS HITAM
        val dnaZoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#090D16"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }

        val tvDnaZoomInfo = TextView(this).apply {
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnToggleDnaZoom = TextView(this).apply {
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(3), dp(5), dp(3))
        }

        fun refreshDnaZoomFloatingUi() {
            val cfg = repository.config.value
            val isOn = cfg.isDnaZoomEnabled
            val zStr = String.format(java.util.Locale.US, "%.2fx", cfg.sessionDnaCustomZoom)
            tvDnaZoomInfo.text = if (isOn) "🎯 DNA Zoom: $zStr (Aktif)" else "🎯 DNA Zoom: OFF ($zStr)"
            tvDnaZoomInfo.setTextColor(if (isOn) Color.parseColor("#34D399") else Color.parseColor("#94A3B8"))
            btnToggleDnaZoom.text = if (isOn) "ON" else "OFF"
            btnToggleDnaZoom.setTextColor(Color.WHITE)
            btnToggleDnaZoom.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isOn) Color.parseColor("#065F46") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (isOn) Color.parseColor("#10B981") else Color.parseColor("#475569"))
            }
        }

        btnToggleDnaZoom.setOnClickListener {
            val next = !repository.config.value.isDnaZoomEnabled
            repository.setDnaZoomEnabled(next)
            refreshDnaZoomFloatingUi()
            Toast.makeText(
                applicationContext,
                if (next) "🎯 DNA Zoom AKTIF (${String.format(java.util.Locale.US, "%.2f", repository.config.value.sessionDnaCustomZoom)}x)" else "🎯 DNA Zoom NONAKTIF",
                Toast.LENGTH_SHORT
            ).show()
        }

        val btnRandomSafeZoom = TextView(this).apply {
            text = "🎲 Zoom Baru"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(3), dp(5), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
            }
            layoutParams = lp
            setOnClickListener {
                val newZ = repository.generateNextUniqueZoom()
                refreshDnaZoomFloatingUi()
                Toast.makeText(applicationContext, "🎯 Zoom DNA Unik: ${String.format(java.util.Locale.US, "%.2f", newZ)}x", Toast.LENGTH_SHORT).show()
            }
        }

        refreshDnaZoomFloatingUi()
        dnaZoomRow.addView(tvDnaZoomInfo)
        dnaZoomRow.addView(btnToggleDnaZoom)
        dnaZoomRow.addView(btnRandomSafeZoom)
        antiDetCard.addView(dnaZoomRow)

        // BARIS TERPISAH 2: 🧬 MUTATOR DNA SESI (Mandiri dengan Saklar ON/OFF)
        val dnaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#090D16"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }

        val tvDnaInfo = TextView(this).apply {
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnToggleDnaSesi = TextView(this).apply {
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(3), dp(5), dp(3))
        }

        val btnRotateDna = TextView(this).apply {
            text = "🔄 Ganti DNA"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(3), dp(5), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#3B0764"))
                setStroke(dp(1), Color.parseColor("#A855F7"))
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
            }
            layoutParams = lp
        }

        fun refreshSessionDnaFloatingUi() {
            val cfg = repository.config.value
            val dna = cfg.activeSessionDna
            val isOn = cfg.enableDnaModule
            tvDnaInfo.text = if (isOn) {
                "🧬 ${dna.badgeText} • ${dna.quadrantLabel}"
            } else {
                "🧬 DNA Sesi: NONAKTIF (OFF)"
            }
            tvDnaInfo.setTextColor(if (isOn) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
            btnToggleDnaSesi.text = if (isOn) "ON" else "OFF"
            btnToggleDnaSesi.setTextColor(Color.WHITE)
            btnToggleDnaSesi.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isOn) Color.parseColor("#4C1D95") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (isOn) Color.parseColor("#A855F7") else Color.parseColor("#475569"))
            }
            btnRotateDna.visibility = if (isOn) View.VISIBLE else View.GONE
        }

        btnToggleDnaSesi.setOnClickListener {
            serviceScope.launch {
                val next = repository.toggleDnaModule()
                refreshSessionDnaFloatingUi()
                Toast.makeText(
                    applicationContext,
                    if (next) "🧬 Mutator DNA Sesi: AKTIF" else "🧬 Mutator DNA Sesi: NONAKTIF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnRotateDna.setOnClickListener {
            serviceScope.launch {
                if (!repository.config.value.enableDnaModule) {
                    Toast.makeText(applicationContext, "⚠️ Nyalakan Saklar DNA Sesi terlebih dahulu", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val nextDna = repository.rotateToNextSessionDna("floating_manual")
                refreshSessionDnaFloatingUi()
                Toast.makeText(applicationContext, "🧬 ${nextDna.badgeText} (${nextDna.summaryText})", Toast.LENGTH_SHORT).show()
            }
        }
        refreshSessionDnaFloatingUi()
        dnaRow.addView(tvDnaInfo)
        dnaRow.addView(btnToggleDnaSesi)
        dnaRow.addView(btnRotateDna)
        antiDetCard.addView(dnaRow)

        tabVisualContainer.addView(antiDetCard)

        // KARTU 3: 🔍 PEMINDAI & PEMBERSIH WATERMARK / KODE TERSEMBUNYI AI DI VIDEO (Gated by enableAiDetectionModule)
        val aiWatermarkCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (repository.config.value.enableAiDetectionModule) View.VISIBLE else View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0B0F17"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }
        this@with.aiWatermarkCardView = aiWatermarkCard

        val aiWmHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvAiWmTitle = TextView(this).apply {
            text = "🛡️ Deteksi & Hapus Kode AI Video"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        aiWmHeaderRow.addView(tvAiWmTitle)

        val tvAiWmBadge = TextView(this).apply {
            text = "🟢 BERSIH"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
        }
        aiWmHeaderRow.addView(tvAiWmBadge)
        aiWatermarkCard.addView(aiWmHeaderRow)

        val tvAiWmSummary = TextView(this).apply {
            text = "Memeriksa apakah video mengandung C2PA / JUMBF / XMP / Tag AI..."
            textSize = 8.6f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(dp(6), dp(5), dp(6), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
                bottomMargin = dp(6)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#131B2E"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
        }
        aiWatermarkCard.addView(tvAiWmSummary)

        var isFloatingProofOpen = false
        val aiWmProofDropdownContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(7), dp(6), dp(7), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#060911"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
        }

        val tvAiWmProofText = TextView(this).apply {
            text = "Memuat bukti audit biner..."
            textSize = 8.0f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#E2E8F0"))
        }
        aiWmProofDropdownContainer.addView(tvAiWmProofText)

        val btnOpenFullProofPopup = TextView(this).apply {
            text = "↗ Buka Laporan Layar Penuh"
            textSize = 8.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0C2340"))
                setStroke(dp(1), Color.parseColor("#0284C7"))
            }
            setOnClickListener {
                showAiWatermarkOverlayDialog(this@with)
            }
        }
        aiWmProofDropdownContainer.addView(btnOpenFullProofPopup)

        val aiWmBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnVisualScanAi = TextView(this).apply {
            text = "🔍 CEK AI"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                serviceScope.launch {
                    val rep = repository.scanActiveVideoAiWatermark()
                    if (rep != null) {
                        isFloatingProofOpen = true
                        onRefreshAiWatermarkUi?.invoke()
                        Toast.makeText(applicationContext, rep.buildNotificationHeadline(), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(applicationContext, "⚠️ Belum ada video aktif untuk dipindai", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        aiWmBtnRow.addView(btnVisualScanAi)

        val btnVisualInfoAi = TextView(this).apply {
            text = "🔬 BUKTI ▾"
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
                isFloatingProofOpen = !isFloatingProofOpen
                onRefreshAiWatermarkUi?.invoke()
            }
        }
        aiWmBtnRow.addView(btnVisualInfoAi)

        tvAiWmSummary.setOnClickListener {
            isFloatingProofOpen = !isFloatingProofOpen
            onRefreshAiWatermarkUi?.invoke()
        }

        val btnVisualCleanAi = TextView(this).apply {
            text = "🧹 BERSIHKAN"
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginStart = dp(2)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#10B981"))
            }
            setOnClickListener {
                serviceScope.launch {
                    isFloatingProofOpen = true
                    Toast.makeText(applicationContext, "🧹 Mengupas kode AI & mengaktifkan perisai Anti-Hash...", Toast.LENGTH_SHORT).show()
                    val res = repository.cleanActiveVideoAiWatermark()
                    onRefreshAiWatermarkUi?.invoke()
                    Toast.makeText(applicationContext, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
        aiWmBtnRow.addView(btnVisualCleanAi)
        aiWatermarkCard.addView(aiWmBtnRow)
        aiWatermarkCard.addView(aiWmProofDropdownContainer)
        tabVisualContainer.addView(aiWatermarkCard)

        val refreshAiWatermarkViews: () -> Unit = {
            val rep = repository.aiWatermarkReport.value
            val scanning = repository.isScanningAiWatermark.value
            val cleaning = repository.isCleaningAiWatermark.value

            val accentHex = when (rep?.severity) {
                com.tajasb.data.AiWatermarkSeverity.AI_DETECTED -> "#F43F5E"
                com.tajasb.data.AiWatermarkSeverity.ENCODER_TRACE -> "#F59E0B"
                com.tajasb.data.AiWatermarkSeverity.CLEAN -> "#10B981"
                null -> "#38BDF8"
            }
            val accentCol = Color.parseColor(accentHex)

            tvAiWmBadge.text = when {
                cleaning -> "🧹 MEMBERSIHKAN"
                scanning -> "🔍 MEMINDAI..."
                rep != null -> rep.severity.badgeText
                else -> "BELUM SCAN"
            }
            tvAiWmBadge.setTextColor(accentCol)
            tvAiWmBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), accentCol)
            }

            aiWatermarkCard.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0B0F17"))
                setStroke(dp(1), accentCol)
            }

            btnVisualInfoAi.text = if (isFloatingProofOpen) "🔬 BUKTI ▴" else "🔬 BUKTI ▾"
            btnVisualInfoAi.setTextColor(if (isFloatingProofOpen) Color.parseColor("#00E5FF") else Color.WHITE)
            aiWmProofDropdownContainer.visibility = if (isFloatingProofOpen && rep != null) View.VISIBLE else View.GONE

            if (rep == null) {
                tvAiWmSummary.text = "Klik [🔍 CEK AI] untuk memindai apakah video mengandung watermark/kode AI tersembunyi."
                tvVideoAiQuickStatus.text = "🛡️ Kode AI: Belum dicek"
                tvVideoAiQuickStatus.setTextColor(Color.parseColor("#94A3B8"))
            } else {
                val findingsPreview = when {
                    rep.findings.isNotEmpty() ->
                        rep.findings.joinToString(", ") { "${it.codeName} (${it.hexOffset.substringBefore(" ")})" }
                    rep.cleaningProof != null ->
                        "Terbukti Dihapus: #${rep.cleaningProof.beforeSha256} ➔ #${rep.cleaningProof.afterSha256}"
                    else ->
                        "Kontainer MP4 Bersih 100% (C2PA/JUMBF/AIGC = 0)"
                }
                tvAiWmSummary.text = "📄 ${rep.fileName} (${rep.fileSizeBytes} B • #${rep.sha256Short})\n${rep.buildNotificationHeadline()}\n• Bukti: $findingsPreview (Ketuk untuk ${if (isFloatingProofOpen) "tutup" else "buka"} rincian)"

                val cfg = repository.config.value
                tvAiWmProofText.text = rep.buildForensicDropdownText(
                    isAntiHashActive = cfg.antiHashMode != com.tajasb.model.AntiHashIntensity.OFF,
                    isAudioAntiHashActive = cfg.isAudioAntiHashEnabled
                )

                tvVideoAiQuickStatus.text = when (rep.severity) {
                    com.tajasb.data.AiWatermarkSeverity.AI_DETECTED ->
                        "🔴 Terdeteksi ${rep.findings.size} Kode AI! (Ketuk bukti)"
                    com.tajasb.data.AiWatermarkSeverity.ENCODER_TRACE ->
                        "🟡 ${rep.findings.size} Metadata Edit (Ketuk bukti)"
                    com.tajasb.data.AiWatermarkSeverity.CLEAN ->
                        if (rep.cleaningProof != null) {
                            "🟢 Bersih (Terbukti #${rep.cleaningProof.beforeSha256}➔#${rep.sha256Short})"
                        } else {
                            "🟢 Video Bersih (0 Kode AI)"
                        }
                }
                tvVideoAiQuickStatus.setTextColor(accentCol)
            }

            btnVisualCleanAi.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(
                    if (rep?.severity == com.tajasb.data.AiWatermarkSeverity.AI_DETECTED) {
                        Color.parseColor("#F43F5E")
                    } else {
                        Color.parseColor("#10B981")
                    }
                )
            }
            btnVisualCleanAi.setTextColor(
                if (rep?.severity == com.tajasb.data.AiWatermarkSeverity.AI_DETECTED) Color.WHITE else Color.BLACK
            )
        }

        onRefreshAiWatermarkUi = refreshAiWatermarkViews
        refreshAiWatermarkViews()

        tabContentHolder.addView(tabVisualContainer)
    }

    private fun showAiWatermarkOverlayDialog(service: FloatingControlService) = with(service) {
        serviceScope.launch {
            val rep = repository.aiWatermarkReport.value ?: repository.scanActiveVideoAiWatermark()
            if (rep == null) {
                Toast.makeText(applicationContext, "⚠️ Pilih video terlebih dahulu untuk melihat laporan kode AI.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val cfg = repository.config.value
            val detailMsg = rep.buildDetailedExplanation(
                isAntiHashActive = cfg.antiHashMode != com.tajasb.model.AntiHashIntensity.OFF,
                isAudioAntiHashActive = cfg.isAudioAntiHashEnabled
            )
            try {
                val dialog = android.app.AlertDialog.Builder(this@with, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("🛡️ Info Watermark & Kode Tersembunyi AI")
                    .setMessage(detailMsg)
                    .setPositiveButton("🧹 Hapus Kode AI & Lindungi") { d, _ ->
                        d.dismiss()
                        serviceScope.launch {
                            Toast.makeText(applicationContext, "🧹 Membersihkan kode AI dari file MP4...", Toast.LENGTH_SHORT).show()
                            val res = repository.cleanActiveVideoAiWatermark()
                            Toast.makeText(applicationContext, res.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNeutralButton("🔍 Scan Ulang") { d, _ ->
                        d.dismiss()
                        serviceScope.launch {
                            val refreshed = repository.scanActiveVideoAiWatermark()
                            if (refreshed != null) {
                                Toast.makeText(applicationContext, refreshed.buildNotificationHeadline(), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton("Tutup") { d, _ -> d.dismiss() }
                    .create()

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                } else {
                    @Suppress("DEPRECATION")
                    dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_PHONE)
                }
                dialog.show()
            } catch (_: Throwable) {
                Toast.makeText(applicationContext, rep.buildNotificationHeadline(), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun buildQuickVideoListItems(
        service: FloatingControlService,
        playlist: List<VideoItem>,
        currentIndex: Int
    ): Unit = with(service) {
        val container = quickVideoListContainer ?: return
        container.removeAllViews()

        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        if (playlist.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Belum ada video di daftar putar.\nBuka APK Tajasb untuk menambah video."
                textSize = 10f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            container.addView(emptyTv)
            return
        }

        val pendingIndex = repository.pendingVideoIndex.value
        val masterIdx = repository.getMasterVideoIndex()

        val query = quickVideoSearchQuery.lowercase()
        val visibleItems = if (query.isEmpty()) {
            playlist.mapIndexed { index, item -> Pair(index, item) }
        } else {
            playlist.mapIndexed { index, item -> Pair(index, item) }.filter { (_, item) ->
                item.name.lowercase().contains(query) || item.spillKeyword.lowercase().contains(query)
            }
        }

        if (visibleItems.isEmpty()) {
            val noMatchTv = TextView(this).apply {
                text = "Tidak ada video cocok dengan '$quickVideoSearchQuery'"
                textSize = 9.5f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            container.addView(noMatchTv)
            return
        }

        visibleItems.forEach { (index, item) ->
            val isActive = index == currentIndex
            val isMaster = index == masterIdx
            val isQueued = index == pendingIndex
            val isItemMuted = item.isMuted
            val isItemEnabled = item.isEnabled
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(4), dp(6), dp(4))
                alpha = if (isItemEnabled) 1.0f else 0.55f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    if (isActive) {
                        setColor(Color.parseColor("#083344"))
                        setStroke(dp(1), Color.parseColor("#00E5FF"))
                    } else if (isQueued) {
                        setColor(Color.parseColor("#451A03"))
                        setStroke(dp(1), Color.parseColor("#F59E0B"))
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                    }
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(4)
                }
                layoutParams = lp

                val badge = TextView(context).apply {
                    text = if (isQueued) "⏳" else if (isMaster) "⭐" else "${index + 1}"
                    textSize = 10f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (isActive || isQueued || isMaster) Color.BLACK else Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(4).toFloat()
                        if (isActive) {
                            setColor(Color.parseColor("#00E5FF"))
                        } else if (isQueued || isMaster) {
                            setColor(Color.parseColor("#F59E0B"))
                        } else {
                            setColor(Color.parseColor("#334155"))
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                }
                addView(badge)

                val nameTv = TextView(context).apply {
                    val baseName = cleanDisplayName(item.name)
                    text = if (isQueued) "$baseName ⏳ (Antre)" else baseName
                    textSize = 10f
                    setTextColor(if (isActive) Color.parseColor("#00E5FF") else if (isQueued) Color.parseColor("#FCD34D") else Color.WHITE)
                    setTypeface(null, if (isActive || isQueued) Typeface.BOLD else Typeface.NORMAL)
                    maxLines = 1
                    setPadding(dp(6), 0, dp(4), 0)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        if (!isItemEnabled) {
                            Toast.makeText(applicationContext, "Video #${index + 1} sedang Off.", Toast.LENGTH_SHORT).show()
                        } else if (isActive) {
                            Toast.makeText(applicationContext, "Video #${index + 1} sedang diputar.", Toast.LENGTH_SHORT).show()
                        } else {
                            val cfgNow = repository.config.value
                            if (cfgNow.isMasterSpillMode) {
                                val masterVid = cfgNow.masterVideo
                                val immediateSpill = !cfgNow.masterSpillWaitFinish
                                serviceScope.launch {
                                    if (masterVid != null && item.id == masterVid.id) {
                                        repository.returnToMasterVideo(immediate = immediateSpill)
                                    } else {
                                        repository.triggerSpillVideo(index, immediate = immediateSpill)
                                    }
                                }
                            } else {
                                val isInstant = (cfgNow.videoSwitchMode == com.tajasb.model.VideoSwitchMode.INSTANT)
                                selectVideoItem(index, immediate = isInstant)
                            }
                        }
                    }
                    setOnLongClickListener {
                        if (isItemEnabled && !isActive) {
                            selectVideoItem(index, immediate = false)
                            true
                        } else false
                    }
                }
                addView(nameTv)

                if (isQueued) {
                    val btnForcePlay = TextView(context).apply {
                        text = "⚡ Main"
                        textSize = 8.5f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(dp(4), dp(2), dp(4), dp(2))
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(1)
                            marginEnd = dp(1)
                        }
                        layoutParams = lp
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(4).toFloat()
                            setColor(Color.parseColor("#083344"))
                            setStroke(dp(1), Color.parseColor("#00E5FF"))
                        }
                        setTextColor(Color.parseColor("#00E5FF"))
                        setOnClickListener {
                            selectVideoItem(index, immediate = true)
                        }
                    }
                    addView(btnForcePlay)

                    val btnCancelQueue = TextView(context).apply {
                        text = "❌ Batal"
                        textSize = 8.5f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(dp(4), dp(2), dp(4), dp(2))
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(1)
                            marginEnd = dp(1)
                        }
                        layoutParams = lp
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(4).toFloat()
                            setColor(Color.parseColor("#450A0A"))
                            setStroke(dp(1), Color.parseColor("#EF4444"))
                        }
                        setTextColor(Color.parseColor("#FCA5A5"))
                        setOnClickListener {
                            serviceScope.launch {
                                repository.cancelQueuedVideo()
                                Toast.makeText(applicationContext, "Antrean video dibatalkan", Toast.LENGTH_SHORT).show()
                                this@with.buildQuickVideoListItems(playlist, repository.config.value.currentVideoIndex)
                            }
                        }
                    }
                    addView(btnCancelQueue)
                }

                val ifsMgr = com.tajasb.infinity.IfsManager.getInstance(applicationContext)
                val isIfsModeActive = ifsMgr.isIfsActive.value || repository.config.value.isIfsMode
                val isSpillOrBridge = !isIfsModeActive && (repository.config.value.isMasterSpillMode || repository.config.value.isBridgeCycleMode)

                if (isIfsModeActive) {
                    val vMode = ifsMgr.videoMode.value
                    val roles = ifsMgr.getVideoSlotRoles(item.id, vMode)
                    val hasRoles = roles.isNotEmpty()
                    val btnIfsSlot = TextView(context).apply {
                        text = if (hasRoles) "♾️ ${roles.joinToString(",")} ▾" else "+Slot ▾"
                        textSize = 8f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(dp(4), dp(2), dp(4), dp(2))
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(1)
                            marginEnd = dp(1)
                        }
                        layoutParams = lp
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(4).toFloat()
                            setColor(Color.parseColor(if (hasRoles) "#3B0764" else "#0F172A"))
                            setStroke(dp(1), Color.parseColor(if (hasRoles) "#E879F9" else "#38BDF8"))
                        }
                        setTextColor(Color.parseColor(if (hasRoles) "#F5D0FE" else "#7DD3FC"))
                        setOnClickListener {
                            IfsFloatingTabBuilder.showSingleVideoRoleOverlayDialog(this@with, item) {
                                this@with.buildQuickVideoListItems(repository.config.value.playlist, repository.config.value.currentVideoIndex)
                                updateUiForConfig(repository.config.value)
                            }
                        }
                    }
                    addView(btnIfsSlot)
                }

                if (isSpillOrBridge && !isMaster) {
                    val btnSpill = TextView(context).apply {
                        text = "🎯 Spill"
                        textSize = 8.5f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(dp(4), dp(2), dp(4), dp(2))
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(1)
                            marginEnd = dp(1)
                        }
                        layoutParams = lp
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(4).toFloat()
                            setColor(Color.parseColor("#083344"))
                            setStroke(dp(1), Color.parseColor("#00E5FF"))
                        }
                        setTextColor(Color.parseColor("#00E5FF"))
                        setOnClickListener {
                            serviceScope.launch {
                                val waitFinish = repository.config.value.masterSpillWaitFinish
                                val itm = repository.triggerSpillVideo(index, immediate = !waitFinish)
                                val msg = if (waitFinish && currentIndex == masterIdx) {
                                    "⏳ Spill: ${cleanDisplayName(itm?.name ?: "")}!\nMenghabiskan master dulu, lalu memutar spill 1x dan otomatis balik ke master."
                                } else {
                                    "🎯 Memutar Spill: ${cleanDisplayName(itm?.name ?: "")} (Selesai 1x auto balik ke Master)"
                                }
                                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                                updateUiForConfig(repository.config.value)
                            }
                        }
                    }
                    addView(btnSpill)
                }

                if (isSpillOrBridge) {
                    val btnSetMaster = TextView(context).apply {
                        text = if (isMaster) "⭐ Master" else "⭐"
                        textSize = 8.5f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(dp(4), dp(2), dp(4), dp(2))
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(1)
                            marginEnd = dp(1)
                        }
                        layoutParams = lp
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(4).toFloat()
                            if (isMaster) {
                                setColor(Color.parseColor("#451A03"))
                                setStroke(dp(1), Color.parseColor("#F59E0B"))
                            } else {
                                setColor(Color.parseColor("#1E293B"))
                                setStroke(dp(1), Color.parseColor("#475569"))
                            }
                        }
                        setTextColor(if (isMaster) Color.parseColor("#FCD34D") else Color.parseColor("#94A3B8"))
                        setOnClickListener {
                            serviceScope.launch {
                                repository.setMasterVideo(item.id)
                                Toast.makeText(applicationContext, "⭐ Video Master ditetapkan: ${cleanDisplayName(item.name)}", Toast.LENGTH_SHORT).show()
                                updateUiForConfig(repository.config.value)
                            }
                        }
                    }
                    addView(btnSetMaster)

                    val hasKw = item.spillKeyword.isNotBlank()
                    val btnSpillTag = TextView(context).apply {
                        text = if (hasKw) "🏷️ ${item.spillKeyword.take(10)}" else "🏷️+Tag"
                        textSize = 8f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setPadding(dp(4), dp(2), dp(4), dp(2))
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(1)
                            marginEnd = dp(1)
                        }
                        layoutParams = lp
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(4).toFloat()
                            setColor(Color.parseColor(if (hasKw) "#064E3B" else "#0F172A"))
                            setStroke(dp(1), Color.parseColor(if (hasKw) "#10B981" else "#475569"))
                        }
                        setTextColor(Color.parseColor(if (hasKw) "#A7F3D0" else "#94A3B8"))
                        setOnClickListener {
                            try {
                                val input = android.widget.EditText(context).apply {
                                    setText(item.spillKeyword)
                                    hint = "Kata kunci Spill (contoh: etalase 1, baju merah)"
                                    textSize = 12f
                                    setPadding(dp(12), dp(8), dp(12), dp(8))
                                }
                                val dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                    .setTitle("🏷️ Kata Kunci Spill: ${cleanDisplayName(item.name)}")
                                    .setView(input)
                                    .setPositiveButton("Simpan") { _, _ ->
                                        val kw = input.text?.toString()?.trim() ?: ""
                                        serviceScope.launch {
                                            repository.setVideoSpillKeyword(item.id, kw)
                                            Toast.makeText(applicationContext, "🏷️ Tag Spill disimpan!", Toast.LENGTH_SHORT).show()
                                            this@with.buildQuickVideoListItems(repository.config.value.playlist, repository.config.value.currentVideoIndex)
                                            updateUiForConfig(repository.config.value)
                                        }
                                    }
                                    .setNeutralButton("Hapus Tag") { _, _ ->
                                        serviceScope.launch {
                                            repository.setVideoSpillKeyword(item.id, "")
                                            Toast.makeText(applicationContext, "🏷️ Tag Spill dihapus", Toast.LENGTH_SHORT).show()
                                            this@with.buildQuickVideoListItems(repository.config.value.playlist, repository.config.value.currentVideoIndex)
                                            updateUiForConfig(repository.config.value)
                                        }
                                    }
                                    .setNegativeButton("Batal", null)
                                    .create()
                                dialog.window?.setType(
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                    else
                                        @Suppress("DEPRECATION") android.view.WindowManager.LayoutParams.TYPE_PHONE
                                )
                                dialog.show()
                            } catch (e: Exception) {
                                Toast.makeText(applicationContext, "Gagal membuka editor tag: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    addView(btnSpillTag)
                }

                val btnEnable = TextView(context).apply {
                    text = if (isItemEnabled) "👁️ On" else "🙈 Off"
                    textSize = 9f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(5), dp(3), dp(5), dp(3))
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    }
                    layoutParams = lp
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(4).toFloat()
                        if (isItemEnabled) {
                            setColor(Color.parseColor("#064E3B"))
                            setStroke(dp(1), Color.parseColor("#10B981"))
                        } else {
                            setColor(Color.parseColor("#450A0A"))
                            setStroke(dp(1), Color.parseColor("#EF4444"))
                        }
                    }
                    setTextColor(if (isItemEnabled) Color.parseColor("#6EE7B7") else Color.parseColor("#FCA5A5"))
                    setOnClickListener {
                        serviceScope.launch {
                            val nextEnabled = repository.toggleVideoEnable(index)
                            val statusMsg = if (nextEnabled) {
                                "Video #${index + 1}: Ditampilkan & Masuk Antrian 👁️"
                            } else {
                                "Video #${index + 1}: Disembunyikan / Dikeluarkan dari Antrian 🙈"
                            }
                            Toast.makeText(applicationContext, statusMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                addView(btnEnable)

                val btnMute = TextView(context).apply {
                    text = if (isItemMuted) "🔇 Mute" else "🔊 Suara"
                    textSize = 9f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(5), dp(3), dp(5), dp(3))
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    }
                    layoutParams = lp
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(4).toFloat()
                        if (isItemMuted) {
                            setColor(Color.parseColor("#450A0A"))
                            setStroke(dp(1), Color.parseColor("#EF4444"))
                        } else {
                            setColor(Color.parseColor("#064E3B"))
                            setStroke(dp(1), Color.parseColor("#10B981"))
                        }
                    }
                    setTextColor(if (isItemMuted) Color.parseColor("#FCA5A5") else Color.parseColor("#6EE7B7"))
                    setOnClickListener {
                        serviceScope.launch {
                            val nextMuted = repository.toggleVideoMute(index)
                            val statusMsg = if (nextMuted) {
                                "Video #${index + 1}: Dimute 🔇 (Musik otomatis lanjut)"
                            } else {
                                "Video #${index + 1}: Bersuara 🔊 (Musik otomatis jeda)"
                            }
                            Toast.makeText(applicationContext, statusMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                addView(btnMute)

                val btnRename = TextView(context).apply {
                    text = "✏️"
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    setOnClickListener {
                        try {
                            val input = android.widget.EditText(context).apply {
                                setText(item.name)
                                hint = "Nama baru video"
                                textSize = 12f
                                setPadding(dp(12), dp(8), dp(12), dp(8))
                            }
                            val dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle("✏️ Ganti Nama Video")
                                .setView(input)
                                .setPositiveButton("Simpan") { _, _ ->
                                    val newName = input.text?.toString()?.trim() ?: ""
                                    if (newName.isNotBlank()) {
                                        serviceScope.launch {
                                            val ok = repository.renameVideo(item.id, newName)
                                            if (ok) {
                                                Toast.makeText(applicationContext, "✅ Nama video diubah ke: $newName", Toast.LENGTH_SHORT).show()
                                                this@with.buildQuickVideoListItems(repository.config.value.playlist, repository.config.value.currentVideoIndex)
                                                updateUiForConfig(repository.config.value)
                                            }
                                        }
                                    }
                                }
                                .setNegativeButton("Batal", null)
                                .create()
                            dialog.window?.setType(
                                if (android.os.Build.VERSION.SDK.toInt() >= android.os.Build.VERSION_CODES.O)
                                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                else
                                    @Suppress("DEPRECATION") android.view.WindowManager.LayoutParams.TYPE_PHONE
                            )
                            dialog.show()
                        } catch (e: Exception) {
                            Toast.makeText(applicationContext, "Gagal membuka dialog ganti nama: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                addView(btnRename)

                val btnUp = TextView(context).apply {
                    text = "🔼"
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    alpha = if (index > 0) 1.0f else 0.25f
                    setOnClickListener {
                        if (index > 0) {
                            serviceScope.launch { repository.moveVideoUp(index) }
                        }
                    }
                }
                addView(btnUp)

                val btnDown = TextView(context).apply {
                    text = "🔽"
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    alpha = if (index < playlist.size - 1) 1.0f else 0.25f
                    setOnClickListener {
                        if (index < playlist.size - 1) {
                            serviceScope.launch { repository.moveVideoDown(index) }
                        }
                    }
                }
                addView(btnDown)

                var isConfirmingDel = false
                var resetDelRunnable: Runnable? = null

                val btnDel = TextView(context).apply {
                    text = "🗑️"
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(2), dp(4), dp(2))

                    fun resetToIdle() {
                        isConfirmingDel = false
                        text = "🗑️"
                        textSize = 10f
                        setTextColor(Color.WHITE)
                        background = null
                        setPadding(dp(4), dp(2), dp(4), dp(2))
                    }

                    setOnClickListener {
                        if (!isConfirmingDel) {
                            isConfirmingDel = true
                            text = "Hapus?"
                            textSize = 9f
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(4).toFloat()
                                setColor(Color.parseColor("#DC2626"))
                            }
                            setPadding(dp(6), dp(2), dp(6), dp(2))

                            resetDelRunnable?.let { removeCallbacks(it) }
                            resetDelRunnable = Runnable { resetToIdle() }
                            postDelayed(resetDelRunnable, 3500)
                        } else {
                            resetDelRunnable?.let { removeCallbacks(it) }
                            resetToIdle()
                            serviceScope.launch {
                                repository.removeVideo(item.id)
                                Toast.makeText(applicationContext, "Video #${index + 1} dihapus 🗑️", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                addView(btnDel)
            }
            container.addView(itemRow)
        }
    }

    fun populateQuickSpillChips(
        service: FloatingControlService,
        playlist: List<VideoItem>,
        currentIdx: Int
    ) = with(service) {
        val container = quickSpillChipsContainer ?: return
        container.removeAllViews()

        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        if (playlist.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Belum ada video di daftar"
                textSize = 9f
                setTextColor(Color.GRAY)
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }
            container.addView(emptyTv)
            return
        }

        val cfg = repository.config.value
        val isBridgeMode = cfg.isBridgeCycleMode
        val b1Idx = cfg.bridge1VideoIndex
        val masterIdx = cfg.masterVideoIndex
        val b2Idx = cfg.bridge2VideoIndex
        val pendingBridgeIdx = cfg.pendingBridgeContentIndex
        val pendingIdx = repository.pendingVideoIndex.value

        var contentCounter = 1

        playlist.forEachIndexed { index, item ->
            val isCurrent = (index == currentIdx)
            val cleanName = cleanDisplayName(item.name)
            val chipName = if (cleanName.length > 14) cleanName.take(12) + ".." else cleanName

            val isBridge1 = isBridgeMode && (index == b1Idx)
            val isBridge2 = isBridgeMode && (index == b2Idx)
            val isMaster = (index == masterIdx)
            val isPending = (index == pendingIdx) || (isBridgeMode && (index == pendingBridgeIdx || cfg.bridgeContentQueue.contains(index)))

            val contentNumber = if (isBridgeMode && !isBridge1 && !isBridge2 && !isMaster) contentCounter++ else 0

            val isSpillMode = cfg.isMasterSpillMode

            val chip = TextView(this).apply {
                text = when {
                    isBridgeMode && isBridge1 -> if (isCurrent) "🌉 Bridge I (Aktif)" else "🌉 Bridge I"
                    isBridgeMode && isMaster -> if (isCurrent) "🔁 Master (Loop)" else "🔁 Master"
                    isBridgeMode && isBridge2 -> if (isCurrent) "🌉 Bridge II (Aktif)" else "🌉 Bridge II"
                    isBridgeMode && contentNumber > 0 -> {
                        if (isCurrent) "🎬 #$contentNumber: $chipName (Aktif)"
                        else if (isPending) "⏳ #$contentNumber: $chipName (Antre)"
                        else "🎬 #$contentNumber: $chipName"
                    }
                    isSpillMode && isMaster && isCurrent -> "⭐ Master (Loop)"
                    isSpillMode && isMaster -> "⭐ Master"
                    isSpillMode && isCurrent -> "🎯 $chipName (Aktif)"
                    isSpillMode && isPending -> "⏳ $chipName (Antre)"
                    isSpillMode -> "🎯 $chipName"
                    isCurrent -> "🎬 #${index + 1}: $chipName (Aktif)"
                    else -> "#${index + 1}: $chipName"
                }
                textSize = 9f
                setTypeface(null, if (isCurrent || isPending || isMaster || isBridge1 || isBridge2) Typeface.BOLD else Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(7), dp(4), dp(7), dp(4))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(4)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    when {
                        isBridgeMode && isBridge1 -> {
                            setColor(Color.parseColor(if (isCurrent) "#0369A1" else "#0F172A"))
                            setStroke(dp(1), Color.parseColor("#38BDF8"))
                        }
                        isBridgeMode && isMaster -> {
                            setColor(Color.parseColor(if (isCurrent) "#78350F" else "#1E293B"))
                            setStroke(dp(1), Color.parseColor("#F59E0B"))
                        }
                        isBridgeMode && isBridge2 -> {
                            setColor(Color.parseColor(if (isCurrent) "#701A75" else "#0F172A"))
                            setStroke(dp(1), Color.parseColor("#E879F9"))
                        }
                        isBridgeMode && contentNumber > 0 -> {
                            when {
                                isCurrent -> {
                                    setColor(Color.parseColor("#064E3B"))
                                    setStroke(dp(1), Color.parseColor("#34D399"))
                                }
                                isPending -> {
                                    setColor(Color.parseColor("#78350F"))
                                    setStroke(dp(1), Color.parseColor("#FCD34D"))
                                }
                                else -> {
                                    setColor(Color.parseColor("#1E293B"))
                                    setStroke(dp(1), Color.parseColor("#10B981"))
                                }
                            }
                        }
                        isSpillMode && isMaster && isCurrent -> {
                            setColor(Color.parseColor("#451A03"))
                            setStroke(dp(1), Color.parseColor("#F59E0B"))
                        }
                        isSpillMode && isCurrent -> {
                            setColor(Color.parseColor("#083344"))
                            setStroke(dp(1), Color.parseColor("#00E5FF"))
                        }
                        isSpillMode && isPending -> {
                            setColor(Color.parseColor("#78350F"))
                            setStroke(dp(1), Color.parseColor("#FCD34D"))
                        }
                        isSpillMode && isMaster -> {
                            setColor(Color.parseColor("#1E293B"))
                            setStroke(dp(1), Color.parseColor("#F59E0B"))
                        }
                        isSpillMode -> {
                            setColor(Color.parseColor("#1E293B"))
                            setStroke(dp(1), Color.parseColor("#475569"))
                        }
                        isCurrent -> {
                            setColor(Color.parseColor("#083344"))
                            setStroke(dp(1), Color.parseColor("#00E5FF"))
                        }
                        else -> {
                            setColor(Color.parseColor("#1E293B"))
                            setStroke(dp(1), Color.parseColor("#334155"))
                        }
                    }
                }
                setTextColor(
                    when {
                        isBridgeMode && isBridge1 -> Color.parseColor("#38BDF8")
                        isBridgeMode && isMaster -> Color.parseColor("#FCD34D")
                        isBridgeMode && isBridge2 -> Color.parseColor("#E879F9")
                        isBridgeMode && contentNumber > 0 -> if (isCurrent) Color.parseColor("#34D399") else Color.WHITE
                        isSpillMode && isMaster -> Color.parseColor("#FCD34D")
                        isSpillMode && isCurrent -> Color.parseColor("#00E5FF")
                        isSpillMode && isPending -> Color.parseColor("#FDE68A")
                        isSpillMode -> Color.WHITE
                        isCurrent -> Color.parseColor("#00E5FF")
                        else -> Color.parseColor("#94A3B8")
                    }
                )
                setOnClickListener {
                    serviceScope.launch {
                        if (isBridgeMode) {
                            when {
                                isBridge1 -> {
                                    repository.selectVideoByIndex(b1Idx)
                                    Toast.makeText(applicationContext, "🌉 Memutar Bridge I", Toast.LENGTH_SHORT).show()
                                }
                                isMaster -> {
                                    repository.selectVideoByIndex(masterIdx)
                                    Toast.makeText(applicationContext, "🔁 Kembali ke Master Loop", Toast.LENGTH_SHORT).show()
                                }
                                isBridge2 -> {
                                    repository.selectVideoByIndex(b2Idx)
                                    Toast.makeText(applicationContext, "🌉 Memutar Bridge II", Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    val itm = repository.triggerBridgeContentVideo(index)
                                    val itmName = cleanDisplayName(itm?.name ?: "")
                                    val isMasterNow = (cfg.currentVideoIndex == masterIdx) || (cfg.currentBridgePhase == com.tajasb.model.BridgeCyclePhase.MASTER_LOOP)
                                    val msg = if (isMasterNow) "⏳ Antre Konten: $itmName" else "🎬 Menuju Konten: $itmName"
                                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else if (isSpillMode) {
                            if (isMaster) {
                                repository.returnToMasterVideo(immediate = true)
                                Toast.makeText(applicationContext, "⭐ Kembali ke Master Video", Toast.LENGTH_SHORT).show()
                            } else {
                                val waitFinish = repository.config.value.masterSpillWaitFinish
                                val itm = repository.triggerSpillVideo(index, immediate = !waitFinish)
                                val itmName = cleanDisplayName(itm?.name ?: "")
                                val msg = if (waitFinish && currentIdx == masterIdx) "⏳ Antre Spill: $itmName" else "🎯 Memutar Spill: $itmName"
                                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            repository.selectVideoByIndex(index)
                            val itmName = cleanDisplayName(item.name)
                            Toast.makeText(applicationContext, "🎬 Memutar: $itmName", Toast.LENGTH_SHORT).show()
                        }
                        updateUiForConfig(repository.config.value)
                    }
                }
            }
            container.addView(chip)
        }
    }

    fun buildQuickAudioListItems(
        service: FloatingControlService,
        playlist: List<AudioItem>,
        currentIndex: Int
    ) = with(service) {
        val container = quickAudioListContainer ?: return
        container.removeAllViews()

        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        if (playlist.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Belum ada audio kustom.\nBuka APK Tajasb untuk menambah file audio."
                textSize = 10f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            container.addView(emptyTv)
            return
        }

        val q = quickAudioSearchQuery.lowercase()
        val ifsMgr = try { com.tajasb.infinity.IfsManager.getInstance(applicationContext) } catch (_: Throwable) { null }
        playlist.forEachIndexed { index, item ->
            val cleanTitle = cleanDisplayName(item.name)
            val roles = ifsMgr?.getAudioRolesForTrack(item.id) ?: listOf("SR")
            val roleLabel = roles.firstOrNull()?.take(6) ?: "SR"
            if (q.isNotBlank() && !cleanTitle.lowercase().contains(q) && !roleLabel.lowercase().contains(q)) {
                return@forEachIndexed
            }
            val isActive = index == currentIndex
            val isPlayingThis = isActive && repository.musicPlayer.isPlaying.value
            val isItemEnabled = item.isEnabled
            val isItemMuted = item.isMuted
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(4), dp(6), dp(4))
                alpha = if (isItemEnabled) 1.0f else 0.55f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(6).toFloat()
                    if (isActive) {
                        setColor(Color.parseColor("#3B0764"))
                        setStroke(dp(1), Color.parseColor("#D946EF"))
                    } else {
                        setColor(Color.parseColor("#0F172A"))
                    }
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(4)
                }
                layoutParams = lp

                val badge = TextView(context).apply {
                    text = if (isPlayingThis) "▶" else "${index + 1}"
                    textSize = 9.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(4).toFloat()
                        setColor(if (isActive) Color.parseColor("#A855F7") else Color.parseColor("#334155"))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                }
                addView(badge)

                val roleBadge = TextView(context).apply {
                    text = roleLabel
                    textSize = 8f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#E0E7FF"))
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(1), dp(4), dp(1))
                    val rLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(4)
                    }
                    layoutParams = rLp
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(3).toFloat()
                        setColor(Color.parseColor("#1E1B4B"))
                        setStroke(dp(1), Color.parseColor("#6366F1"))
                    }
                }
                addView(roleBadge)

                val nameTv = TextView(context).apply {
                    text = cleanTitle
                    textSize = 9.5f
                    setTextColor(if (isActive) Color.parseColor("#D946EF") else Color.WHITE)
                    setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
                    maxLines = 1
                    setPadding(dp(5), 0, dp(4), 0)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        if (!isItemEnabled) {
                            Toast.makeText(applicationContext, "Audio #${index + 1} OFF. Aktifkan dulu.", Toast.LENGTH_SHORT).show()
                        } else {
                            selectAudioItem(index)
                        }
                    }
                }
                addView(nameTv)

                val btnEnable = TextView(context).apply {
                    text = if (isItemEnabled) "👁️ On" else "🙈 Off"
                    textSize = 9f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(5), dp(3), dp(5), dp(3))
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    }
                    layoutParams = lp
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(4).toFloat()
                        if (isItemEnabled) {
                            setColor(Color.parseColor("#064E3B"))
                            setStroke(dp(1), Color.parseColor("#10B981"))
                        } else {
                            setColor(Color.parseColor("#450A0A"))
                            setStroke(dp(1), Color.parseColor("#EF4444"))
                        }
                    }
                    setTextColor(if (isItemEnabled) Color.parseColor("#6EE7B7") else Color.parseColor("#FCA5A5"))
                    setOnClickListener {
                        serviceScope.launch {
                            val nextEnabled = repository.toggleAudioEnable(index)
                            val statusMsg = if (nextEnabled) {
                                "Audio #${index + 1}: Ditampilkan & Masuk Antrean 👁️"
                            } else {
                                "Audio #${index + 1}: Disembunyikan / Dikeluarkan dari Antrean 🙈"
                            }
                            Toast.makeText(applicationContext, statusMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                addView(btnEnable)

                val btnMute = TextView(context).apply {
                    text = if (isItemMuted) "🔇 Mute" else "🔊 Suara"
                    textSize = 9f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(5), dp(3), dp(5), dp(3))
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    }
                    layoutParams = lp
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(4).toFloat()
                        if (isItemMuted) {
                            setColor(Color.parseColor("#450A0A"))
                            setStroke(dp(1), Color.parseColor("#EF4444"))
                        } else {
                            setColor(Color.parseColor("#064E3B"))
                            setStroke(dp(1), Color.parseColor("#10B981"))
                        }
                    }
                    setTextColor(if (isItemMuted) Color.parseColor("#FCA5A5") else Color.parseColor("#6EE7B7"))
                    setOnClickListener {
                        serviceScope.launch {
                            val nextMuted = repository.toggleAudioMute(index)
                            val statusMsg = if (nextMuted) {
                                "Audio #${index + 1}: Dimute 🔇"
                            } else {
                                "Audio #${index + 1}: Bersuara 🔊"
                            }
                            Toast.makeText(applicationContext, statusMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                addView(btnMute)

                val btnUp = TextView(context).apply {
                    text = "🔼"
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    alpha = if (index > 0) 1.0f else 0.25f
                    setOnClickListener {
                        if (index > 0) {
                            serviceScope.launch { repository.moveAudioUp(index) }
                        }
                    }
                }
                addView(btnUp)

                val btnDown = TextView(context).apply {
                    text = "🔽"
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    alpha = if (index < playlist.size - 1) 1.0f else 0.25f
                    setOnClickListener {
                        if (index < playlist.size - 1) {
                            serviceScope.launch { repository.moveAudioDown(index) }
                        }
                    }
                }
                addView(btnDown)

                var isConfirmingDel = false
                var resetDelRunnable: Runnable? = null

                val btnDel = TextView(context).apply {
                    text = "🗑️"
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(2), dp(4), dp(2))

                    fun resetToIdle() {
                        isConfirmingDel = false
                        text = "🗑️"
                        textSize = 10f
                        setTextColor(Color.WHITE)
                        background = null
                        setPadding(dp(4), dp(2), dp(4), dp(2))
                    }

                    setOnClickListener {
                        if (!isConfirmingDel) {
                            isConfirmingDel = true
                            text = "Hapus?"
                            textSize = 9f
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(4).toFloat()
                                setColor(Color.parseColor("#DC2626"))
                            }
                            setPadding(dp(6), dp(2), dp(6), dp(2))

                            resetDelRunnable?.let { removeCallbacks(it) }
                            resetDelRunnable = Runnable { resetToIdle() }
                            postDelayed(resetDelRunnable, 3500)
                        } else {
                            resetDelRunnable?.let { removeCallbacks(it) }
                            resetToIdle()
                            serviceScope.launch {
                                repository.removeAudioItem(item.id)
                                Toast.makeText(applicationContext, "Audio #${index + 1} dihapus 🗑️", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                addView(btnDel)
            }
            container.addView(itemRow)
        }
    }

    private fun applyOverlayWindowType(dialog: AlertDialog) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
    }

    fun showAudioLaSlotPickerOverlay(service: FloatingControlService) = with(service) {
        val ifsManager = com.tajasb.infinity.IfsManager.getInstance(applicationContext)
        val allAudios = repository.config.value.audioPlaylist.filterNot { it.id.startsWith("vs_audio_rolling_") }
        if (allAudios.isEmpty()) {
            Toast.makeText(applicationContext, "⚠️ Belum ada lagu di daftar putar. Upload lagu di Menu Utama terlebih dahulu.", Toast.LENGTH_LONG).show()
            return@with
        }
        val currentLIds = ifsManager.sv4AudioLIds.value.toSet()
        val booleanChecked = BooleanArray(allAudios.size) { idx ->
            if (currentLIds.isEmpty()) true else currentLIds.contains(allAudios[idx].id)
        }
        val labels = Array(allAudios.size) { idx ->
            val a = allAudios[idx]
            val slotNum = idx + 1
            "LA$slotNum • ${cleanDisplayName(a.name)}"
        }

        try {
            val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🧬 Atur Slot LA Variasi Audio (SV4)")
                .setMultiChoiceItems(labels, booleanChecked) { _, which, isChecked ->
                    booleanChecked[which] = isChecked
                }
                .setPositiveButton("Simpan Slot LA") { d, _ ->
                    val chosenIds = mutableListOf<String>()
                    for (i in allAudios.indices) {
                        if (booleanChecked[i]) chosenIds.add(allAudios[i].id)
                    }
                    if (chosenIds.isEmpty()) {
                        chosenIds.addAll(allAudios.map { it.id })
                    }
                    ifsManager.setSv4AudioLIds(chosenIds)
                    btnSv4AudioSlotSetupText?.text = "⚙️ Atur Slot LA (${chosenIds.size} Trek) ▾"
                    Toast.makeText(applicationContext, "🧬 Slot Audio SV4 disimpan: ${chosenIds.size} trek LA siap diracik VSA!", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                }
                .setNeutralButton("⚡ Pilih Semua") { d, _ ->
                    val chosenIds = allAudios.map { it.id }
                    ifsManager.setSv4AudioLIds(chosenIds)
                    btnSv4AudioSlotSetupText?.text = "⚙️ Atur Slot LA (${chosenIds.size} Trek) ▾"
                    Toast.makeText(applicationContext, "🧬 Seluruh ${chosenIds.size} trek dijadikan Slot LA!", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }

            val dialog = builder.create()
            applyOverlayWindowType(dialog)
            dialog.show()
        } catch (t: Throwable) {
            Toast.makeText(applicationContext, "Gagal membuka dialog slot: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun showAudioLayer2PickerOverlay(service: FloatingControlService) = with(service) {
        val allAudios = repository.config.value.audioPlaylist.filterNot { it.id.startsWith("vs_audio_rolling_") }
        if (allAudios.isEmpty()) {
            Toast.makeText(applicationContext, "⚠️ Belum ada lagu di daftar putar.", Toast.LENGTH_SHORT).show()
            return@with
        }
        val curL2Id = repository.config.value.audioLayer2Id
        val labels = Array(allAudios.size) { idx ->
            val a = allAudios[idx]
            val isCurrent = (a.id == curL2Id || a.path == repository.config.value.audioLayer2Path)
            "${if (isCurrent) "✓ " else ""}#${idx + 1} ${cleanDisplayName(a.name)}"
        }

        try {
            val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🎧 Pilih Audio Tipe 2 (Secondary Ambience)")
                .setItems(labels) { d, which ->
                    val chosen = allAudios.getOrNull(which)
                    if (chosen != null) {
                        serviceScope.launch {
                            repository.selectAudioLayer2Track(chosen)
                            updateMusicPlayerUi()
                            Toast.makeText(applicationContext, "🎧 Tipe 2 diatur ke: ${cleanDisplayName(chosen.name)}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }
            val dialog = builder.create()
            applyOverlayWindowType(dialog)
            dialog.show()
        } catch (_: Throwable) {}
    }

    fun showAudioLayer3PickerOverlay(service: FloatingControlService) = with(service) {
        val allAudios = repository.config.value.audioPlaylist.filterNot { it.id.startsWith("vs_audio_rolling_") }
        if (allAudios.isEmpty()) {
            Toast.makeText(applicationContext, "⚠️ Belum ada lagu di daftar putar.", Toast.LENGTH_SHORT).show()
            return@with
        }
        val curL3Ids = repository.config.value.audioLayer3Ids.toSet()
        val booleanChecked = BooleanArray(allAudios.size) { idx ->
            curL3Ids.contains(allAudios[idx].id)
        }
        val labels = Array(allAudios.size) { idx ->
            "#${idx + 1} ${cleanDisplayName(allAudios[idx].name)}"
        }

        try {
            val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🔔 Pilih SFX / Ringtone Tipe 3")
                .setMultiChoiceItems(labels, booleanChecked) { _, which, isChecked ->
                    booleanChecked[which] = isChecked
                }
                .setPositiveButton("Simpan Ringtone") { d, _ ->
                    val chosenIds = mutableSetOf<String>()
                    for (i in allAudios.indices) {
                        if (booleanChecked[i]) chosenIds.add(allAudios[i].id)
                    }
                    serviceScope.launch {
                        repository.setAudioLayer3RingtoneIds(chosenIds)
                        updateMusicPlayerUi()
                        Toast.makeText(applicationContext, "🔔 ${chosenIds.size} ringtone aktif disimpan!", Toast.LENGTH_SHORT).show()
                    }
                    d.dismiss()
                }
                .setNeutralButton("⚡ Pilih Semua") { d, _ ->
                    val chosenIds = allAudios.map { it.id }.toSet()
                    serviceScope.launch {
                        repository.setAudioLayer3RingtoneIds(chosenIds)
                        updateMusicPlayerUi()
                        Toast.makeText(applicationContext, "🔔 Seluruh ${chosenIds.size} audio dijadikan Ringtone!", Toast.LENGTH_SHORT).show()
                    }
                    d.dismiss()
                }
                .setNegativeButton("Batal") { d, _ -> d.dismiss() }
            val dialog = builder.create()
            applyOverlayWindowType(dialog)
            dialog.show()
        } catch (_: Throwable) {}
    }

    fun buildMakkahLiveRelayCard(
        service: FloatingControlService,
        dp: (Int) -> Int
    ): LinearLayout = with(service) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#061D15"))
                setStroke(dp(1), Color.parseColor("#10B981"))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(5)
            }
            layoutParams = lp
        }
        cardMakkahLiveRelay = card

        // Baris 1: Header + Status + Tombol Toggle ON/OFF
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        val tvTitle = TextView(this).apply {
            text = "🕋 MEKAH LIVE RELAY"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(tvTitle)

        val tvStatus = TextView(this).apply {
            text = "🟢 RELAY AKTIF"
            textSize = 8f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(2), dp(5), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#064E3B"))
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(4)
            }
            layoutParams = lp
        }
        tvMakkahRelayStatus = tvStatus
        headerRow.addView(tvStatus)

        val btnToggle = TextView(this).apply {
            text = "🟢 ON"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(3), dp(7), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#10B981"))
            }
            setOnClickListener {
                toggleMakkahRelay()
            }
        }
        btnToggleMakkahRelay = btnToggle
        headerRow.addView(btnToggle)
        card.addView(headerRow)

        val controlsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val isRelay = repository.config.value.isLiveStreamSourceActive
            visibility = if (isRelay) View.VISIBLE else View.GONE
        }
        makkahControlsContainer = controlsContainer

        // Baris 2: Saluran Cepat (Stream 1, Stream 2, Stream 3)
        val channelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        fun createChannelBtn(label: String, url: String, title: String): TextView {
            return TextView(this).apply {
                text = label
                textSize = 8.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
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
                setTextColor(Color.WHITE)
                setOnClickListener {
                    selectMakkahStream(url, title)
                }
            }
        }

        val btnS1 = createChannelBtn("🕋 Stream 1", "http://m.live.net.sa:1935/live/quran/playlist.m3u8", "Stream 1 - Saudi Quran TV (Masjidil Haram)")
        val btnS2 = createChannelBtn("🕋 Stream 2", "https://cdn-globecast.akamaized.net/live/eds/saudi_quran/hls_roku/index.m3u8", "Stream 2 - Saudi Quran TV (Akamai Backup)")
        val btnS3 = createChannelBtn("🕌 Stream 3", "http://m.live.net.sa:1935/live/sunnah/playlist.m3u8", "Stream 3 - Saudi Sunnah TV (Masjid an-Nabawi)")
        btnMakkahStream1 = btnS1
        btnMakkahStream2 = btnS2
        btnMakkahStream3 = btnS3
        channelRow.addView(btnS1)
        channelRow.addView(btnS2)
        channelRow.addView(btnS3)
        controlsContainer.addView(channelRow)

        // Baris 3: ⚡ TOMBOL PULIHKAN / REFRESH STREAM (Anti-Freeze 1-Tap)
        val btnRefresh = TextView(this).apply {
            text = "⚡ PULIHKAN / REFRESH STREAM (ANTI-FREEZE)"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#D1FAE5"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(5).toFloat()
                setColor(Color.parseColor("#065F46"))
                setStroke(dp(1), Color.parseColor("#34D399"))
            }
            setOnClickListener {
                refreshMakkahStream()
            }
        }
        btnMakkahRefreshStream = btnRefresh
        controlsContainer.addView(btnRefresh)

        // Baris 4: Presets Skala Anti-Gepeng (Kaaba Fokus, Kaaba Utuh, Layar Penuh)
        val scaleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        fun createScaleBtn(label: String, mode: ScaleMode): TextView {
            return TextView(this).apply {
                text = label
                textSize = 8f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(3), dp(3), dp(3))
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
                setTextColor(Color.WHITE)
                setOnClickListener {
                    setMakkahScaleMode(mode)
                }
            }
        }

        val btnCrop = createScaleBtn("🕋 Kaaba Fokus", ScaleMode.CROP)
        val btnFit = createScaleBtn("🕋 Kaaba Utuh", ScaleMode.FIT)
        val btnStretch = createScaleBtn("↔️ Penuh", ScaleMode.STRETCH)
        btnMakkahScaleCrop = btnCrop
        btnMakkahScaleFit = btnFit
        btnMakkahScaleStretch = btnStretch
        scaleRow.addView(btnCrop)
        scaleRow.addView(btnFit)
        scaleRow.addView(btnStretch)
        controlsContainer.addView(scaleRow)

        // Baris 5: Framing Kaaba (Zoom, Pan X, Pan Y, Rotasi, Reset)
        val framingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        val tvZoom = TextView(this).apply {
            text = "🔍 1.00x"
            textSize = 8f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvMakkahZoomLabel = tvZoom
        framingRow.addView(tvZoom)

        fun createMiniBtn(label: String, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                textSize = 8.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(2), dp(5), dp(2))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(2)
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
                setOnClickListener { onClick() }
            }
        }

        framingRow.addView(createMiniBtn("➖") { adjustMakkahZoom(-0.05f) })
        framingRow.addView(createMiniBtn("➕") { adjustMakkahZoom(+0.05f) })
        framingRow.addView(createMiniBtn("⬅️") { adjustMakkahPan(-0.05f, 0f) })
        framingRow.addView(createMiniBtn("➡️") { adjustMakkahPan(+0.05f, 0f) })
        framingRow.addView(createMiniBtn("⬆️") { adjustMakkahPan(0f, -0.05f) })
        framingRow.addView(createMiniBtn("⬇️") { adjustMakkahPan(0f, +0.05f) })

        val tvRot = TextView(this).apply {
            text = "🔄 0°"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(2), dp(5), dp(2))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(2)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0C4A6E"))
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener { cycleMakkahRotation() }
        }
        tvMakkahRotationLabel = tvRot
        framingRow.addView(tvRot)

        val btnReset = createMiniBtn("🎯") { resetMakkahFraming() }
        framingRow.addView(btnReset)
        controlsContainer.addView(framingRow)

        // Baris 6: Telemetry info text
        val tvInfo = TextView(this).apply {
            text = "💡 Kaaba Fokus otomatis memotong 16:9 jadi 9:16 alami tanpa gepeng!"
            textSize = 7.5f
            setTextColor(Color.parseColor("#A7F3D0"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
            layoutParams = lp
        }
        tvMakkahTelemetryInfo = tvInfo
        controlsContainer.addView(tvInfo)
        card.addView(controlsContainer)

        card
    }
}
