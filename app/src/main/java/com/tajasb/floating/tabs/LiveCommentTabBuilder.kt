package com.tajasb.floating.tabs

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tajasb.FloatingControlService
import com.tajasb.eventrelay.ConnectionStatus
import com.tajasb.eventrelay.LiveEventPreferences
import com.tajasb.eventrelay.LiveEventReceiver
import com.tajasb.eventrelay.LiveEventType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * LiveCommentTabBuilder:
 * Modular builder for Tab 4 Sub-Mode 1: Doa & Komentar Live, Perekam Scan Komentar,
 * Kotak Area Scan, Custom Prefix & Format, and Scan Recorder UI updates.
 */
object LiveCommentTabBuilder {

    private fun applyOverlayWindowType(dialog: AlertDialog) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
    }

    private fun createCollapsibleSectionCard(
        context: Context,
        dp: (Int) -> Int,
        title: String,
        accentHex: String = "#38BDF8",
        defaultExpanded: Boolean = false,
        headerRightView: View? = null,
        onContent: (LinearLayout) -> Unit
    ): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#0F172A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
            visibility = if (defaultExpanded) View.VISIBLE else View.GONE
        }

        var isExpanded = defaultExpanded

        val arrowView = TextView(context).apply {
            text = if (defaultExpanded) "▼" else "▶"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(accentHex))
            setPadding(0, 0, dp(4), 0)
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1E293B"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                isExpanded = !isExpanded
                contentLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
                arrowView.text = if (isExpanded) "▼" else "▶"
            }
        }

        headerRow.addView(arrowView)

        val titleView = TextView(context).apply {
            text = title
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(accentHex))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(titleView)

        if (headerRightView != null) {
            headerRow.addView(headerRightView)
        }

        card.addView(headerRow)
        card.addView(contentLayout)

        onContent(contentLayout)

        return card
    }

    private fun showServerAddressDialog(
        service: FloatingControlService,
        etHost: EditText,
        etPort: EditText
    ) = with(service) {
        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val tvLabelHost = TextView(this).apply {
            text = "Alamat IP Server Termux Device 1:"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        container.addView(tvLabelHost)

        val inputHost = EditText(this).apply {
            setText(etHost.text.toString().ifBlank { "192.168.43.1" })
            textSize = 14f
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setHintTextColor(Color.parseColor("#64748B"))
            hint = "contoh: 192.168.43.1"
        }
        container.addView(inputHost)

        val tvLabelPort = TextView(this).apply {
            text = "Port WebSocket / Relay LAN:"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, 0)
        }
        container.addView(tvLabelPort)

        val inputPort = EditText(this).apply {
            setText(etPort.text.toString().ifBlank { "8765" })
            textSize = 14f
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setHintTextColor(Color.parseColor("#64748B"))
            hint = "contoh: 8765"
        }
        container.addView(inputPort)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Isi Alamat Server Termux")
            .setView(container)
            .setPositiveButton("Simpan & Sambung") { d, _ ->
                val h = inputHost.text.toString().trim()
                val p = inputPort.text.toString().toIntOrNull() ?: 8765
                etHost.setText(h)
                etPort.setText(p.toString())
                val eventPrefs = LiveEventPreferences.getInstance(this)
                val receiver = LiveEventReceiver.getInstance(this)
                eventPrefs.updateServerAddress(h, p)
                if (receiver.connectionStatus.value == ConnectionStatus.CONNECTED) {
                    receiver.disconnect()
                }
                receiver.connect()
                Toast.makeText(applicationContext, "Server disetel ke $h:$p", Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton("Batal") { d, _ ->
                d.dismiss()
            }
            .create()

        applyOverlayWindowType(dialog)
        dialog.show()
    }

    private fun showTargetUsernameDialog(
        service: FloatingControlService,
        onSaved: () -> Unit
    ) = with(service) {
        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
        val eventPrefs = LiveEventPreferences.getInstance(this)
        val receiver = LiveEventReceiver.getInstance(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val tvLabel = TextView(this).apply {
            text = "Username TikTok Cadangan:"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        container.addView(tvLabel)

        val inputTarget = EditText(this).apply {
            setText(eventPrefs.config.value.targetUsername)
            textSize = 14f
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setHintTextColor(Color.parseColor("#64748B"))
            hint = "contoh: akun.tiktok"
        }
        container.addView(inputTarget)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Target TikTok Cadangan")
            .setView(container)
            .setPositiveButton("Simpan & Aktifkan") { d, _ ->
                val u = inputTarget.text.toString().trim().removePrefix("@")
                if (u.isNotBlank()) {
                    receiver.switchTargetUsername(u)
                    Toast.makeText(applicationContext, "Target: @$u", Toast.LENGTH_SHORT).show()
                    onSaved()
                }
                d.dismiss()
            }
            .setNegativeButton("Batal") { d, _ ->
                d.dismiss()
            }
            .create()

        applyOverlayWindowType(dialog)
        dialog.show()
    }

    private fun showCustomTargetGiftDialog(
        service: FloatingControlService,
        onSaved: () -> Unit
    ) = with(service) {
        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
        val eventPrefs = LiveEventPreferences.getInstance(this)
        val curCfg = eventPrefs.config.value

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val tvLabelName = TextView(this).apply {
            text = "Nama Target Gift:"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        container.addView(tvLabelName)

        val inputName = EditText(this).apply {
            setText(curCfg.targetGiftName)
            textSize = 13f
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setHintTextColor(Color.parseColor("#64748B"))
            hint = "contoh: Paus, Singa, Laptop..."
        }
        container.addView(inputName)

        val tvLabelCoins = TextView(this).apply {
            text = "Jumlah Target Koin (💎):"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, 0)
        }
        container.addView(tvLabelCoins)

        val inputCoins = EditText(this).apply {
            setText(curCfg.targetGiftCoins.toString())
            textSize = 13f
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setHintTextColor(Color.parseColor("#64748B"))
            hint = "contoh: 2150"
        }
        container.addView(inputCoins)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Atur Target Gift Khusus")
            .setView(container)
            .setPositiveButton("Simpan") { d, _ ->
                val n = inputName.text.toString().trim().ifBlank { "Paus" }
                val c = inputCoins.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 2150
                eventPrefs.setTargetGift(n, c)
                LiveEventReceiver.getInstance(this).sendTargetGiftToBroker(n, c)
                Toast.makeText(applicationContext, "Target: $n ($c 💎)", Toast.LENGTH_SHORT).show()
                onSaved()
                d.dismiss()
            }
            .setNegativeButton("Batal") { d, _ -> d.dismiss() }
            .create()

        applyOverlayWindowType(dialog)
        dialog.show()
    }

    fun createNotifContainerAndDoaTab(
        service: FloatingControlService,
        dp: (Int) -> Int
    ): LinearLayout = with(service) {
        val tabNotifContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerTabNotif = tabNotifContainer

        val liveComment = com.tajasb.livecomment.LiveCommentManager.getInstance(this)

        // Sub-Mode Switcher Container (2 Baris agar 7 sub-tab tidak terpotong di panel melayang)
        val modeSwitcherContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        val modeSwitcherRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(3))
        }

        val modeSwitcherRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnSalesMode = TextView(this).apply {
            text = "Jualan"
            textSize = 9.0f
            isSingleLine = true
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            setOnClickListener {
                isShowingLiveEvent = false
                isShowingScanLog = false
                liveComment.setBannerMode(com.tajasb.livecomment.BannerMode.BUYER_SALES)
                updateBuyerNotifUi()
            }
        }
        btnModeSalesTab = btnSalesMode
        modeSwitcherRow1.addView(btnSalesMode)

        val btnStikerMode = TextView(this).apply {
            text = "Stiker"
            textSize = 9.0f
            isSingleLine = true
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(2)
            }
            setOnClickListener {
                isShowingLiveEvent = false
                isShowingScanLog = false
                liveComment.setBannerMode(com.tajasb.livecomment.BannerMode.STICKER_TEXT)
                updateBuyerNotifUi()
            }
        }
        btnModeStikerTab = btnStikerMode
        modeSwitcherRow1.addView(btnStikerMode)

        val btnWatermarkMode = TextView(this).apply {
            text = "Watermark"
            textSize = 9.0f
            isSingleLine = true
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginStart = dp(1)
                marginEnd = dp(2)
            }
            setOnClickListener {
                isShowingLiveEvent = false
                isShowingScanLog = false
                liveComment.setBannerMode(com.tajasb.livecomment.BannerMode.WATERMARK_TEXT)
                updateBuyerNotifUi()
            }
        }
        btnModeWatermarkTab = btnWatermarkMode
        modeSwitcherRow1.addView(btnWatermarkMode)

        val btnAutoTimeMode = TextView(this).apply {
            text = "Waktu"
            textSize = 9.0f
            isSingleLine = true
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(1)
            }
            setOnClickListener {
                isShowingLiveEvent = false
                isShowingScanLog = false
                liveComment.setBannerMode(com.tajasb.livecomment.BannerMode.AUTO_TIME)
                updateBuyerNotifUi()
            }
        }
        btnModeAutoTimeTab = btnAutoTimeMode
        modeSwitcherRow1.addView(btnAutoTimeMode)

        val btnQuranKhatamMode = TextView(this).apply {
            text = "📖 Khatam"
            textSize = 9.0f
            isSingleLine = true
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(2)
            }
            setOnClickListener {
                isShowingLiveEvent = false
                isShowingScanLog = false
                liveComment.setBannerMode(com.tajasb.livecomment.BannerMode.QURAN_KHATAM)
                updateBuyerNotifUi()
            }
        }
        btnModeQuranKhatamTab = btnQuranKhatamMode
        modeSwitcherRow2.addView(btnQuranKhatamMode)

        val btnTugOfWarMode = TextView(this).apply {
            text = "⚔️ Tambang"
            textSize = 9.0f
            isSingleLine = true
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(2)
            }
            setOnClickListener {
                isShowingLiveEvent = false
                isShowingScanLog = false
                liveComment.setBannerMode(com.tajasb.livecomment.BannerMode.TUG_OF_WAR)
                updateBuyerNotifUi()
            }
        }
        btnModeTugOfWarTab = btnTugOfWarMode
        modeSwitcherRow2.addView(btnTugOfWarMode)

        val btnLiveEventMode = TextView(this).apply {
            text = "⚡ Event"
            textSize = 9.0f
            isSingleLine = true
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(1)
            }
            setOnClickListener {
                isShowingLiveEvent = true
                isShowingScanLog = false
                updateBuyerNotifUi()
            }
        }
        btnModeLiveEventTab = btnLiveEventMode
        modeSwitcherRow2.addView(btnLiveEventMode)

        modeSwitcherContainer.addView(modeSwitcherRow1)
        modeSwitcherContainer.addView(modeSwitcherRow2)
        tabNotifContainer.addView(modeSwitcherContainer)
        tabNotifContainer.addView(createLiveEventBrokerContainer(service, dp))

        return tabNotifContainer
    }


    fun updateScanRecorderUi(service: FloatingControlService) = with(service) {
        val scanRecorder = com.tajasb.livecomment.CommentScanRecorder.getInstance(this)
        val isRecording = scanRecorder.isRecording.value
        val count = scanRecorder.scannedCount.value
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        btnToggleScanRecordText?.apply {
            text = if (isRecording) "Rekam: ON" else "Rekam: OFF"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (isRecording) {
                    setColor(Color.parseColor("#DC2626"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#047857"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                }
            }
        }

        scanRecordCountBadge?.apply {
            text = "$count Chat"
            setTextColor(if (isRecording) Color.parseColor("#00E5FF") else Color.parseColor("#38BDF8"))
        }

        btnToggleScanRecordTextSettings?.apply {
            text = if (isRecording) "Rekam: ON" else "Rekam: OFF"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                if (isRecording) {
                    setColor(Color.parseColor("#DC2626"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                } else {
                    setColor(Color.parseColor("#047857"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                }
            }
        }

        scanRecordCountBadgeSettings?.apply {
            text = "$count Chat"
            setTextColor(if (isRecording) Color.parseColor("#00E5FF") else Color.parseColor("#38BDF8"))
        }
    }

    fun createScanInspectorContainer(
        service: FloatingControlService,
        dp: (Int) -> Int
    ): LinearLayout = with(service) {
        val inspectorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerModeScanLog = inspectorContainer

        val inspector = com.tajasb.livecomment.CommentScanInspector.getInstance(this)

        // 1. Status & Metric Card
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#061325"))
                setStroke(dp(1.2f.toInt()), Color.parseColor("#0EA5E9"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        // Header Row: Title & A11y Badge
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvTitle = TextView(this).apply {
            text = "Inspeksi Scan Komentar"
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(tvTitle)

        val a11yBadge = TextView(this).apply {
            text = "A11Y Aktif"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#064E3B"))
            }
            setPadding(dp(5), dp(2), dp(5), dp(2))
            setOnClickListener {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (_: Throwable) {}
            }
        }
        scanLogA11yStatusBadge = a11yBadge
        headerRow.addView(a11yBadge)
        statusCard.addView(headerRow)

        // Subtitle: Area bounds status
        val tvAreaInfo = TextView(this).apply {
            text = "Kotak Scan: 40% - 88% Tab Doa"
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, dp(3))
        }
        scanLogAreaInfoText = tvAreaInfo
        statusCard.addView(tvAreaInfo)

        // Subtitle: Viewer Count Anchor position
        val tvViewerAnchor = TextView(this).apply {
            text = "Titik Penonton: Pojok kanan atas"
            textSize = 8.5f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, dp(4))
        }
        scanLogViewerAnchorText = tvViewerAnchor
        statusCard.addView(tvViewerAnchor)

        // Warning banner when bottom sheet/product menu covers chat
        val tvMenuCovered = TextView(this).apply {
            text = "Scan Dijeda: Menu Produk/Skrip Terbuka"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FEF08A"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#713F12"))
                setStroke(dp(1), Color.parseColor("#EAB308"))
            }
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
            visibility = View.GONE
        }
        scanLogMenuCoveredBanner = tvMenuCovered
        statusCard.addView(tvMenuCovered)

        // Metrics Row (4 Pill Badges)
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(4))
        }

        fun createMiniBadge(label: String, colorHex: String, bgHex: String): TextView {
            return TextView(this).apply {
                text = label
                textSize = 8f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(colorHex))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor(bgHex))
                    setStroke(dp(1), Color.parseColor(colorHex))
                }
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(3), dp(2), dp(3))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(2)
                }
            }
        }

        val badgeAccepted = createMiniBadge("0 Diterima", "#10B981", "#022C22")
        scanLogCountAcceptedBadge = badgeAccepted
        metricsRow.addView(badgeAccepted)

        val badgeSystem = createMiniBadge("0 Sistem", "#F59E0B", "#451A03")
        scanLogCountSystemBadge = badgeSystem
        metricsRow.addView(badgeSystem)

        val badgeDup = createMiniBadge("0 Duplikat", "#06B6D4", "#083344")
        scanLogCountDupBadge = badgeDup
        metricsRow.addView(badgeDup)

        val badgeOut = createMiniBadge("0 Luar", "#8B5CF6", "#2E1065").apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = 0
        }
        scanLogCountOutBadge = badgeOut
        metricsRow.addView(badgeOut)

        statusCard.addView(metricsRow)

        // Filter Chips Row
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(3))
        }

        scanLogFilterChipsList.clear()
        val filters = listOf(
            "ALL" to "SEMUA",
            "ACCEPTED" to "DITERIMA",
            "SYSTEM" to "SISTEM",
            "DUP" to "DUPLIKAT",
            "OUT" to "LUAR"
        )
        filters.forEach { (key, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 8f
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(3), dp(2), dp(3))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(2)
                }
                setOnClickListener {
                    currentScanLogFilter = key
                    updateScanInspectorFilterChips()
                    val entries = inspector.entriesFlow.value
                    updateScanInspectorFeed(entries)
                }
            }
            scanLogFilterChipsList.add(Pair(key, chip))
            filterRow.addView(chip)
        }
        updateScanInspectorFilterChips()
        statusCard.addView(filterRow)

        // Action Buttons Row: Bersihkan, Pause/Resume, Simpan TXT
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, 0)
        }

        val btnClear = TextView(this).apply {
            text = "Bersihkan"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#475569"))
            }
            setPadding(dp(4), dp(5), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(3)
            }
            setOnClickListener {
                inspector.clearLogs()
                updateScanInspectorCounters()
                updateScanInspectorFeed(emptyList())
                Toast.makeText(applicationContext, "Log dibersihkan", Toast.LENGTH_SHORT).show()
            }
        }
        actionsRow.addView(btnClear)

        val btnFreeze = TextView(this).apply {
            text = if (isScanInspectorFrozen) "Lanjut" else "Jeda"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(if (isScanInspectorFrozen) Color.parseColor("#0284C7") else Color.parseColor("#334155"))
            }
            setPadding(dp(4), dp(5), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(3)
            }
            setOnClickListener {
                isScanInspectorFrozen = !isScanInspectorFrozen
                text = if (isScanInspectorFrozen) "Lanjut" else "Jeda"
                (background as? GradientDrawable)?.setColor(if (isScanInspectorFrozen) Color.parseColor("#0284C7") else Color.parseColor("#334155"))
                if (!isScanInspectorFrozen) {
                    updateScanInspectorFeed(inspector.entriesFlow.value)
                }
            }
        }
        actionsRow.addView(btnFreeze)

        val btnExport = TextView(this).apply {
            text = "Simpan TXT"
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#0284C7"))
            }
            setPadding(dp(4), dp(5), dp(4), dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            setOnClickListener {
                val res = inspector.exportDiagnosticToDownloadFolder()
                if (res.success) {
                    Toast.makeText(applicationContext, "Tersimpan ke ${res.filePath} (${res.totalComments} log) 💾", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(applicationContext, res.errorMessage ?: "Gagal menyimpan log", Toast.LENGTH_SHORT).show()
                }
            }
        }
        actionsRow.addView(btnExport)

        statusCard.addView(actionsRow)
        inspectorContainer.addView(statusCard)

        // 2. Feed Container
        val feedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scanLogContainerList = feedContainer
        inspectorContainer.addView(feedContainer)

        updateScanInspectorCounters()
        updateScanInspectorFilterChips()

        inspectorContainer
    }

    private fun createLiveEventBrokerContainer(
        service: FloatingControlService,
        dp: (Int) -> Int
    ): LinearLayout = with(service) {
        val brokerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        containerModeLiveEventBroker = brokerContainer

        val eventPrefs = LiveEventPreferences.getInstance(this)
        val receiver = LiveEventReceiver.getInstance(this)

        fun createDenseChip(label: String, getVal: () -> Boolean, onToggle: (Boolean) -> Unit): TextView = TextView(this).apply {
            val cur = getVal()
            text = label
            textSize = 8.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(if (cur) Color.WHITE else Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(if (cur) Color.parseColor("#047857") else Color.parseColor("#1E293B"))
                setStroke(dp(1), if (cur) Color.parseColor("#10B981") else Color.parseColor("#334155"))
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }
            setOnClickListener {
                val next = !getVal()
                onToggle(next)
                setTextColor(if (next) Color.WHITE else Color.parseColor("#94A3B8"))
                (background as? GradientDrawable)?.apply {
                    setColor(if (next) Color.parseColor("#047857") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (next) Color.parseColor("#10B981") else Color.parseColor("#334155"))
                }
            }
        }

        fun createMiniStepper(lbl: String, onClick: () -> Unit): TextView = TextView(this).apply {
            text = lbl
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setPadding(dp(2), dp(4), dp(2), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }
            setOnClickListener { onClick() }
        }

        var refreshTargetChipsFn: (() -> Unit)? = null

        // =========================================================================
        // 1. KATEGORI: KONEKSI BROKER (DROPDOWN)
        // =========================================================================
        val badgeStatus = TextView(this).apply {
            text = "MENUNGGU"
            textSize = 7.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#EF4444"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#450A0A"))
                setStroke(dp(1), Color.parseColor("#DC2626"))
            }
            setPadding(dp(5), dp(2), dp(5), dp(2))
        }

        val etHost = EditText(this).apply {
            setText(eventPrefs.config.value.serverHost)
            textSize = 8.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#020617"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setPadding(dp(4), dp(3), dp(4), dp(3))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f).apply {
                marginEnd = dp(2)
            }
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            hint = "IP Termux"
            setHintTextColor(Color.parseColor("#64748B"))
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

        val etPort = EditText(this).apply {
            setText(eventPrefs.config.value.serverPort.toString())
            textSize = 8.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#020617"))
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            setPadding(dp(4), dp(3), dp(4), dp(3))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f).apply {
                marginEnd = dp(2)
            }
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Port"
            setHintTextColor(Color.parseColor("#64748B"))
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

        val btnOpenDialogKeyboard = TextView(this).apply {
            text = "⌨️"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(4), dp(5), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#2563EB"))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(2)
            }
            setOnClickListener {
                setPanelFocusable(false)
                showServerAddressDialog(service, etHost, etPort)
            }
        }

        val btnConnectToggle = TextView(this).apply {
            text = "SAMBUNG"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#059669"))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                setPanelFocusable(false)
                val host = etHost.text.toString().trim()
                val port = etPort.text.toString().toIntOrNull() ?: 8765
                eventPrefs.updateServerAddress(host, port)
                if (receiver.connectionStatus.value == ConnectionStatus.CONNECTED) {
                    receiver.disconnect()
                } else {
                    receiver.connect()
                }
            }
        }

        val tvConnDetails = TextView(this).apply {
            text = "ws://${eventPrefs.config.value.serverHost}:${eventPrefs.config.value.serverPort} | Penonton: 0"
            textSize = 7.5f
            setTextColor(Color.parseColor("#94A3B8"))
        }

        val connCard = createCollapsibleSectionCard(
            this, dp,
            title = "Koneksi Broker",
            accentHex = "#38BDF8",
            defaultExpanded = true,
            headerRightView = badgeStatus
        ) { content ->
            val endpointRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(1), 0, dp(2))
            }
            endpointRow.addView(etHost)
            endpointRow.addView(etPort)
            endpointRow.addView(btnOpenDialogKeyboard)
            endpointRow.addView(btnConnectToggle)
            content.addView(endpointRow)

            val presetIpRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(1), 0, dp(2))
            }
            fun createIpChip(ipLabel: String, ipValue: String) = TextView(this).apply {
                text = ipLabel
                textSize = 7.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#38BDF8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#020617"))
                    setStroke(dp(1), Color.parseColor("#0284C7"))
                }
                setPadding(dp(2), dp(3), dp(2), dp(3))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                setOnClickListener {
                    setPanelFocusable(false)
                    etHost.setText(ipValue)
                    val port = etPort.text.toString().toIntOrNull() ?: 8765
                    eventPrefs.updateServerAddress(ipValue, port)
                    Toast.makeText(applicationContext, "IP: $ipValue", Toast.LENGTH_SHORT).show()
                }
            }
            presetIpRow.addView(createIpChip("192.168.43.1", "192.168.43.1"))
            presetIpRow.addView(createIpChip("192.168.1.1", "192.168.1.1"))
            presetIpRow.addView(createIpChip("127.0.0.1", "127.0.0.1"))
            presetIpRow.addView(createIpChip("10.0.2.2", "10.0.2.2"))
            content.addView(presetIpRow)

            val curPlatform = eventPrefs.config.value.activePlatform
            val isBigo = curPlatform.equals("BIGO", ignoreCase = true)

            // Platform Selector Row
            val platformSelectRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(1), 0, dp(2))
            }

            fun createPlatformChip(platformKey: String, label: String, isSelected: Boolean): TextView = TextView(this).apply {
                text = label
                textSize = 8.0f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    if (isSelected) {
                        setColor(if (platformKey == "BIGO") Color.parseColor("#0891B2") else Color.parseColor("#BE185D"))
                        setStroke(dp(1), Color.WHITE)
                    } else {
                        setColor(Color.parseColor("#1E293B"))
                        setStroke(dp(1), Color.parseColor("#475569"))
                    }
                }
                setPadding(dp(4), dp(4), dp(4), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                setOnClickListener {
                    eventPrefs.setActivePlatform(platformKey)
                    receiver.switchPlatform(platformKey)
                    refreshTargetChipsFn?.invoke()
                    Toast.makeText(applicationContext, "Platform: $label", Toast.LENGTH_SHORT).show()
                }
            }

            val chipTikTok = createPlatformChip("TIKTOK", "🎵 TikTok", !isBigo)
            val chipBigo = createPlatformChip("BIGO", "🫘 Bigo Live", isBigo)
            platformSelectRow.addView(chipTikTok)
            platformSelectRow.addView(chipBigo)
            content.addView(platformSelectRow)

            val tvTargetStatus = TextView(this).apply {
                val cfg = eventPrefs.config.value
                val isB = cfg.activePlatform.equals("BIGO", ignoreCase = true)
                val curTarget = if (isB) cfg.targetBigoId else cfg.targetUsername
                val pName = if (isB) "Bigo ID" else "TikTok @"
                text = "Target ($pName): ${curTarget.ifBlank { "Belum disetel" }}"
                textSize = 8.0f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isB) Color.parseColor("#06B6D4") else Color.parseColor("#38BDF8"))
                setPadding(0, dp(4), 0, dp(2))
            }
            content.addView(tvTargetStatus)

            val targetInputRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(2))
            }

            val etNewTarget = EditText(this).apply {
                textSize = 8.5f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#020617"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
                setPadding(dp(4), dp(3), dp(4), dp(3))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f).apply {
                    marginEnd = dp(2)
                }
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                hint = "Username cadangan"
                setHintTextColor(Color.parseColor("#64748B"))
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
            targetInputRow.addView(etNewTarget)

            val btnDialogTarget = TextView(this).apply {
                text = "⌨️"
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(4), dp(5), dp(4))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#2563EB"))
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(2)
                }
                setOnClickListener {
                    setPanelFocusable(false)
                    showTargetUsernameDialog(service) {
                        refreshTargetChipsFn?.invoke()
                    }
                }
            }
            targetInputRow.addView(btnDialogTarget)

            val btnAddTarget = TextView(this).apply {
                text = "Tambah"
                textSize = 8.0f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(4), dp(6), dp(4))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#0284C7"))
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    setPanelFocusable(false)
                    val raw = etNewTarget.text.toString().trim().removePrefix("@")
                    if (raw.isNotBlank()) {
                        val isB = eventPrefs.config.value.activePlatform.equals("BIGO", ignoreCase = true)
                        if (isB) {
                            eventPrefs.addBackupBigoId(raw)
                            if (eventPrefs.config.value.targetBigoId.isBlank()) {
                                receiver.switchBigoTargetId(raw)
                            }
                            Toast.makeText(applicationContext, "Cadangan Bigo ID: $raw", Toast.LENGTH_SHORT).show()
                        } else {
                            eventPrefs.addBackupUsername(raw)
                            if (eventPrefs.config.value.targetUsername.isBlank()) {
                                receiver.switchTargetUsername(raw)
                            }
                            Toast.makeText(applicationContext, "Cadangan TikTok: @$raw", Toast.LENGTH_SHORT).show()
                        }
                        etNewTarget.setText("")
                        refreshTargetChipsFn?.invoke()
                    }
                }
            }
            targetInputRow.addView(btnAddTarget)
            content.addView(targetInputRow)

            val chipsScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(1)
                    bottomMargin = dp(3)
                }
            }
            val chipsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            chipsScroll.addView(chipsLayout)
            content.addView(chipsScroll)

            fun renderTargetChips() {
                chipsLayout.removeAllViews()
                val cfg = eventPrefs.config.value
                val isB = cfg.activePlatform.equals("BIGO", ignoreCase = true)
                val activeTarget = if (isB) cfg.targetBigoId else cfg.targetUsername
                val pName = if (isB) "Bigo ID" else "TikTok @"
                tvTargetStatus.text = "Target ($pName): ${activeTarget.ifBlank { "Belum disetel" }}"
                tvTargetStatus.setTextColor(if (isB) Color.parseColor("#06B6D4") else Color.parseColor("#38BDF8"))
                etNewTarget.hint = if (isB) "Bigo ID cadangan" else "Username cadangan"

                // Update platform chips highlight
                chipTikTok.setTextColor(if (!isB) Color.WHITE else Color.parseColor("#94A3B8"))
                (chipTikTok.background as? GradientDrawable)?.apply {
                    setColor(if (!isB) Color.parseColor("#BE185D") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (!isB) Color.WHITE else Color.parseColor("#475569"))
                }
                chipBigo.setTextColor(if (isB) Color.WHITE else Color.parseColor("#94A3B8"))
                (chipBigo.background as? GradientDrawable)?.apply {
                    setColor(if (isB) Color.parseColor("#0891B2") else Color.parseColor("#1E293B"))
                    setStroke(dp(1), if (isB) Color.WHITE else Color.parseColor("#475569"))
                }

                val backups = if (isB) cfg.backupBigoIds else cfg.backupUsernames
                if (backups.isEmpty()) {
                    val tvEmpty = TextView(this).apply {
                        text = if (isB) "Belum ada cadangan Bigo ID" else "Belum ada cadangan TikTok"
                        textSize = 7.5f
                        setTextColor(Color.parseColor("#64748B"))
                        setPadding(dp(2), dp(2), dp(2), dp(2))
                    }
                    chipsLayout.addView(tvEmpty)
                } else {
                    for (u in backups) {
                        val isActive = u.equals(activeTarget, ignoreCase = true)
                        val chip = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(3).toFloat()
                                if (isActive) {
                                    setColor(if (isB) Color.parseColor("#0891B2") else Color.parseColor("#047857"))
                                    setStroke(dp(1), if (isB) Color.parseColor("#22D3EE") else Color.parseColor("#10B981"))
                                } else {
                                    setColor(Color.parseColor("#1E293B"))
                                    setStroke(dp(1), Color.parseColor("#475569"))
                                }
                            }
                            setPadding(dp(5), dp(3), dp(4), dp(3))
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                marginEnd = dp(3)
                            }
                        }

                        val tvU = TextView(this).apply {
                            val prefix = if (isB) "🫘 " else "@"
                            text = if (isActive) "● $prefix$u" else "$prefix$u"
                            textSize = 7.5f
                            setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
                            setTextColor(if (isActive) Color.WHITE else Color.parseColor("#94A3B8"))
                            setOnClickListener {
                                if (isB) {
                                    receiver.switchBigoTargetId(u)
                                    Toast.makeText(applicationContext, "Bigo aktif: $u", Toast.LENGTH_SHORT).show()
                                } else {
                                    receiver.switchTargetUsername(u)
                                    Toast.makeText(applicationContext, "Target aktif: @$u", Toast.LENGTH_SHORT).show()
                                }
                                refreshTargetChipsFn?.invoke()
                            }
                        }
                        chip.addView(tvU)

                        val btnDel = TextView(this).apply {
                            text = "✕"
                            textSize = 7.5f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (isActive) Color.parseColor("#FCA5A5") else Color.parseColor("#64748B"))
                            setPadding(dp(5), 0, dp(1), 0)
                            setOnClickListener {
                                if (isB) {
                                    eventPrefs.removeBackupBigoId(u)
                                    Toast.makeText(applicationContext, "Dihapus: $u", Toast.LENGTH_SHORT).show()
                                } else {
                                    eventPrefs.removeBackupUsername(u)
                                    Toast.makeText(applicationContext, "Dihapus: @$u", Toast.LENGTH_SHORT).show()
                                }
                                refreshTargetChipsFn?.invoke()
                            }
                        }
                        chip.addView(btnDel)

                        chipsLayout.addView(chip)
                    }
                }
            }
            refreshTargetChipsFn = { renderTargetChips() }
            renderTargetChips()

            content.addView(tvConnDetails)
        }
        brokerContainer.addView(connCard)

        // =========================================================================
        // 1B. KATEGORI: DASHBOARD ANALITIK HOST (REAL-TIME PRIVAT)
        // =========================================================================
        var updateHostAnalyticsUiFn: (() -> Unit)? = null

        val badgeViewerHost = TextView(this).apply {
            text = "👥 0 Viewer"
            textSize = 7.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#34D399"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#064E3B"))
                setStroke(dp(1), Color.parseColor("#059669"))
            }
            setPadding(dp(5), dp(2), dp(5), dp(2))
        }

        val hostAnalyticsCard = createCollapsibleSectionCard(
            this, dp,
            title = "Analitik Host (Privat)",
            accentHex = "#10B981",
            defaultExpanded = true,
            headerRightView = badgeViewerHost
        ) { content ->
            val tvHostNotice = TextView(this).apply {
                text = "🔒 Khusus Layar Host: Realtime counter ini hanya tampil di HP Anda, TIDAK dirender ke kamera publik siaran live."
                textSize = 6.8f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, 0, 0, dp(3))
            }
            content.addView(tvHostNotice)

            val gridContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#0F172A"))
                    setStroke(dp(1), Color.parseColor("#1E293B"))
                }
                setPadding(dp(6), dp(5), dp(6), dp(5))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(4)
                }
            }

            fun createMetricRow(leftLabel: String, leftValFn: () -> String, leftColor: String,
                                rightLabel: String, rightValFn: () -> String, rightColor: String): Pair<LinearLayout, Pair<TextView, TextView>> {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(1), 0, dp(1))
                }
                val tvLeft = TextView(this).apply {
                    text = "$leftLabel: ${leftValFn()}"
                    textSize = 7.8f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor(leftColor))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tvRight = TextView(this).apply {
                    text = "$rightLabel: ${rightValFn()}"
                    textSize = 7.8f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor(rightColor))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(tvLeft)
                row.addView(tvRight)
                return Pair(row, Pair(tvLeft, tvRight))
            }

            val (r1, pair1) = createMetricRow("👥 Penonton", { "${receiver.viewerCount.value}" }, "#38BDF8",
                                              "❤️ Suka (Like)", { String.format("%,d", receiver.totalLikesAccumulated.value).replace(',', '.') }, "#F87171")
            val (r2, pair2) = createMetricRow("💬 Doa/Komentar", { String.format("%,d", receiver.totalChatsAccumulated.value).replace(',', '.') }, "#FDE047",
                                              "🎁 Hadiah (Gift)", { String.format("%,d", receiver.totalGiftsAccumulated.value).replace(',', '.') }, "#FB923C")
            val (r3, pair3) = createMetricRow("💎 Koin Saweran", { String.format("%,d", receiver.totalDiamondsAccumulated.value).replace(',', '.') }, "#A78BFA",
                                              "👋 Bergabung", { String.format("%,d", receiver.totalJoinsAccumulated.value).replace(',', '.') }, "#34D399")
            val (r4, pair4) = createMetricRow("👤 Pengikut Baru", { String.format("%,d", receiver.totalFollowsAccumulated.value).replace(',', '.') }, "#E879F9",
                                              "🔄 Bagikan Siaran", { String.format("%,d", receiver.totalSharesAccumulated.value).replace(',', '.') }, "#60A5FA")

            gridContainer.addView(r1)
            gridContainer.addView(r2)
            gridContainer.addView(r3)
            gridContainer.addView(r4)
            content.addView(gridContainer)

            val hostActionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(1), 0, dp(1))
            }

            val tvTotalEventsSummary = TextView(this).apply {
                text = "⚡ Total Event: 0"
                textSize = 7.0f
                setTextColor(Color.parseColor("#94A3B8"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnResetAllSession = TextView(this).apply {
                text = "Reset Sesi"
                textSize = 7.0f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#7F1D1D"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                }
                setPadding(dp(6), dp(3), dp(6), dp(3))
                setOnClickListener {
                    receiver.resetAllSessionCounters()
                    receiver.sendResetStatsToBroker()
                    updateHostAnalyticsUiFn?.invoke()
                    Toast.makeText(applicationContext, "Seluruh counter sesi direset ke 0", Toast.LENGTH_SHORT).show()
                }
            }

            hostActionRow.addView(tvTotalEventsSummary)
            hostActionRow.addView(btnResetAllSession)
            content.addView(hostActionRow)

            updateHostAnalyticsUiFn = {
                badgeViewerHost.text = "👥 ${receiver.viewerCount.value} Viewer"
                pair1.first.text = "👥 Penonton: ${receiver.viewerCount.value}"
                pair1.second.text = "❤️ Suka (Like): ${String.format("%,d", receiver.totalLikesAccumulated.value).replace(',', '.')}"
                pair2.first.text = "💬 Doa/Komentar: ${String.format("%,d", receiver.totalChatsAccumulated.value).replace(',', '.')}"
                pair2.second.text = "🎁 Hadiah (Gift): ${String.format("%,d", receiver.totalGiftsAccumulated.value).replace(',', '.')}"
                pair3.first.text = "💎 Koin Saweran: ${String.format("%,d", receiver.totalDiamondsAccumulated.value).replace(',', '.')}"
                pair3.second.text = "👋 Bergabung: ${String.format("%,d", receiver.totalJoinsAccumulated.value).replace(',', '.')}"
                pair4.first.text = "👤 Pengikut Baru: ${String.format("%,d", receiver.totalFollowsAccumulated.value).replace(',', '.')}"
                pair4.second.text = "🔄 Bagikan Siaran: ${String.format("%,d", receiver.totalSharesAccumulated.value).replace(',', '.')}"
                tvTotalEventsSummary.text = "⚡ Total Event: ${String.format("%,d", receiver.totalReceivedCount.value).replace(',', '.')}"
            }
        }
        brokerContainer.addView(hostAnalyticsCard)

        // =========================================================================
        // 1C. KATEGORI: TARGET GIFT & KONVERSI KOIN (KHUSUS HOST - TIDAK KE VIRTUAL)
        // =========================================================================
        var updateTargetGiftUiFn: (() -> Unit)? = null

        val badgeCoinTarget = TextView(this).apply {
            text = "0%"
            textSize = 7.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#082F49"))
                setStroke(dp(1), Color.parseColor("#0284C7"))
            }
            setPadding(dp(5), dp(2), dp(5), dp(2))
        }

        val targetCard = createCollapsibleSectionCard(
            this, dp,
            title = "Target Gift & Konversi",
            accentHex = "#06B6D4",
            defaultExpanded = true,
            headerRightView = badgeCoinTarget
        ) { content ->
            // Card Statistik Koin Terkumpul & Estimasi Rupiah
            val summaryBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#0F172A"))
                    setStroke(dp(1), Color.parseColor("#1E293B"))
                }
                setPadding(dp(6), dp(5), dp(6), dp(5))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(4)
                }
            }

            val tvTotalCoinsSummary = TextView(this).apply {
                text = "💎 0 Koin Terkumpul (0 Gift)"
                textSize = 9.0f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#FDE047"))
            }
            summaryBox.addView(tvTotalCoinsSummary)

            val tvConversionSummary = TextView(this).apply {
                text = "💰 Payout Bersih: Rp 0 (@Rp80) | Nilai Beli: Rp 0"
                textSize = 7.5f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, dp(1), 0, 0)
            }
            summaryBox.addView(tvConversionSummary)

            content.addView(summaryBox)

            // Progress Bar & Sisa Koin Menuju Target
            val progressContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#020617"))
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
                setPadding(dp(6), dp(5), dp(6), dp(5))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(4)
                }
            }

            val tvTargetTitle = TextView(this).apply {
                text = "🎯 Target: Paus (2.150 💎)"
                textSize = 8.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#38BDF8"))
            }
            progressContainer.addView(tvTargetTitle)

            // Bar background and fill
            val barTrack = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply {
                    topMargin = dp(3)
                    bottomMargin = dp(3)
                }
            }
            val barFill = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#06B6D4"))
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            barTrack.addView(barFill)
            progressContainer.addView(barTrack)

            val tvTargetProgressText = TextView(this).apply {
                text = "Progres: 0% • Sisa 2.150 💎 lagi (~Rp 172.000) menuju Paus!"
                textSize = 7.5f
                setTextColor(Color.parseColor("#CBD5E1"))
            }
            progressContainer.addView(tvTargetProgressText)

            content.addView(progressContainer)

            // Pilihan Target Cepat (Chips)
            val tvChipHeader = TextView(this).apply {
                text = "Pilih Target Cepat:"
                textSize = 7.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, 0, 0, dp(2))
            }
            content.addView(tvChipHeader)

            val presetGrid1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(2))
            }
            val presetGrid2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(3))
            }

            fun createTargetChip(label: String, targetName: String, targetCoins: Int): TextView = TextView(this).apply {
                text = label
                textSize = 7.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(4), dp(2), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                setOnClickListener {
                    eventPrefs.setTargetGift(targetName, targetCoins)
                    receiver.sendTargetGiftToBroker(targetName, targetCoins)
                    Toast.makeText(applicationContext, "Target: $targetName ($targetCoins 💎)", Toast.LENGTH_SHORT).show()
                }
            }

            val chipPaus = createTargetChip("Paus (2.150💎)", "Paus", 2150)
            val chip2Paus = createTargetChip("2x Paus (4.300💎)", "2x Paus", 4300)
            val chipKereta = createTargetChip("Kereta (899💎)", "Kereta Api", 899)
            val chipCorgi = createTargetChip("Corgi (299💎)", "Corgi", 299)
            presetGrid1.addView(chipPaus)
            presetGrid1.addView(chip2Paus)
            presetGrid1.addView(chipKereta)
            presetGrid1.addView(chipCorgi)
            content.addView(presetGrid1)

            val chipSinga = createTargetChip("Singa (29.999💎)", "Singa", 29999)
            val chip5k = createTargetChip("5.000💎", "5.000 Koin", 5000)
            val chip10k = createTargetChip("10.000💎", "10.000 Koin", 10000)
            val chipCustom = TextView(this).apply {
                text = "Kustom ✍️"
                textSize = 7.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#38BDF8"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#082F49"))
                    setStroke(dp(1), Color.parseColor("#0284C7"))
                }
                setPadding(dp(2), dp(4), dp(2), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                setOnClickListener {
                    showCustomTargetGiftDialog(service) {
                        updateTargetGiftUiFn?.invoke()
                    }
                }
            }
            presetGrid2.addView(chipSinga)
            presetGrid2.addView(chip5k)
            presetGrid2.addView(chip10k)
            presetGrid2.addView(chipCustom)
            content.addView(presetGrid2)

            // Kurs Konversi dan Tombol Reset Koin Sesi
            val rateAndActionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(1), 0, dp(2))
            }

            fun createRateChip(rateVal: Int, label: String) = TextView(this).apply {
                text = label
                textSize = 7.0f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(3), dp(2), dp(3))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                setOnClickListener {
                    eventPrefs.setCoinRateIdr(rateVal)
                    receiver.sendCoinRateToBroker(rateVal)
                    Toast.makeText(applicationContext, "Kurs: Rp $rateVal / Koin", Toast.LENGTH_SHORT).show()
                }
            }

            val chipRate80 = createRateChip(80, "Kurs Rp80")
            val chipRate100 = createRateChip(100, "Rp100")
            val chipRate170 = createRateChip(170, "Rp170")
            val btnResetCoins = TextView(this).apply {
                text = "Reset Koin"
                textSize = 7.0f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#7F1D1D"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                }
                setPadding(dp(2), dp(3), dp(2), dp(3))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                setOnClickListener {
                    receiver.resetSessionCoins()
                    receiver.sendResetStatsToBroker()
                    Toast.makeText(applicationContext, "Koin sesi direset ke 0", Toast.LENGTH_SHORT).show()
                }
            }
            rateAndActionRow.addView(chipRate80)
            rateAndActionRow.addView(chipRate100)
            rateAndActionRow.addView(chipRate170)
            rateAndActionRow.addView(btnResetCoins)
            content.addView(rateAndActionRow)

            // Notice Privasi (TIDAK dirender ke virtual kamera)
            val tvPrivacyNotice = TextView(this).apply {
                text = "🔒 Khusus Layar Host: Konversi & target ini hanya tampil di HP Anda dan TIDAK dirender ke kamera virtual siaran live TikTok."
                textSize = 6.5f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(2), 0, 0)
            }
            content.addView(tvPrivacyNotice)

            updateTargetGiftUiFn = {
                val cfg = eventPrefs.config.value
                val curDiamonds = receiver.totalDiamondsAccumulated.value
                val curGifts = receiver.totalGiftsAccumulated.value
                val tgCoins = cfg.targetGiftCoins.coerceAtLeast(1)
                val tgName = cfg.targetGiftName
                val cRate = cfg.coinRateIdr
                val pctFloat = (curDiamonds.toFloat() / tgCoins * 100f).coerceIn(0f, 999f)
                val pctStr = String.format("%.1f%%", pctFloat)
                val remainingCoins = maxOf(0, tgCoins - curDiamonds)
                val payoutRp = (curDiamonds.toLong() * cRate).let { String.format("%,d", it).replace(',', '.') }
                val grossRp = (curDiamonds.toLong() * 170).let { String.format("%,d", it).replace(',', '.') }
                val sisaRp = (remainingCoins.toLong() * cRate).let { String.format("%,d", it).replace(',', '.') }

                badgeCoinTarget.text = pctStr
                tvTotalCoinsSummary.text = "💎 ${String.format("%,d", curDiamonds).replace(',', '.')} Koin Terkumpul ($curGifts Gift)"
                tvConversionSummary.text = "💰 Payout Bersih: Rp $payoutRp (@Rp$cRate) | Nilai Beli: Rp $grossRp (@Rp170)"
                tvTargetTitle.text = "🎯 Target: $tgName (${String.format("%,d", tgCoins).replace(',', '.')} 💎)"

                if (curDiamonds >= tgCoins) {
                    tvTargetProgressText.text = "🎉 TARGET TERCAPAI! ($curDiamonds / $tgCoins 💎) • Terlampaui +${curDiamonds - tgCoins} 💎"
                    tvTargetProgressText.setTextColor(Color.parseColor("#4ADE80"))
                    (barFill.background as? GradientDrawable)?.setColor(Color.parseColor("#22C55E"))
                } else {
                    tvTargetProgressText.text = "Progres: $pctStr • Kurang ${String.format("%,d", remainingCoins).replace(',', '.')} 💎 lagi (~Rp $sisaRp) untuk $tgName!"
                    tvTargetProgressText.setTextColor(Color.parseColor("#CBD5E1"))
                    (barFill.background as? GradientDrawable)?.setColor(Color.parseColor("#06B6D4"))
                }

                val trackWidth = barTrack.width
                if (trackWidth > 0) {
                    val fillW = (trackWidth * (pctFloat / 100f).coerceIn(0f, 1f)).toInt()
                    barFill.layoutParams = LinearLayout.LayoutParams(fillW, ViewGroup.LayoutParams.MATCH_PARENT)
                }

                fun styleChip(chip: TextView, isSelected: Boolean, accent: String) {
                    chip.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#94A3B8"))
                    chip.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(3).toFloat()
                        setColor(if (isSelected) Color.parseColor(accent) else Color.parseColor("#1E293B"))
                        setStroke(dp(1), if (isSelected) Color.WHITE else Color.parseColor("#334155"))
                    }
                }

                styleChip(chipPaus, cfg.targetGiftCoins == 2150, "#0284C7")
                styleChip(chip2Paus, cfg.targetGiftCoins == 4300, "#0284C7")
                styleChip(chipKereta, cfg.targetGiftCoins == 899, "#0284C7")
                styleChip(chipCorgi, cfg.targetGiftCoins == 299, "#0284C7")
                styleChip(chipSinga, cfg.targetGiftCoins == 29999, "#B45309")
                styleChip(chip5k, cfg.targetGiftCoins == 5000, "#047857")
                styleChip(chip10k, cfg.targetGiftCoins == 10000, "#047857")

                styleChip(chipRate80, cRate == 80, "#047857")
                styleChip(chipRate100, cRate == 100, "#047857")
                styleChip(chipRate170, cRate == 170, "#047857")
            }

            barTrack.post { updateTargetGiftUiFn?.invoke() }
        }
        brokerContainer.addView(targetCard)

        // =========================================================================
        // 2. KATEGORI: SAKLAR EVENT (DROPDOWN)
        // =========================================================================
        val switchCard = createCollapsibleSectionCard(
            this, dp,
            title = "Saklar Event",
            accentHex = "#EAB308",
            defaultExpanded = false
        ) { content ->
            val rowSwitch1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(2))
            }
            rowSwitch1.addView(createDenseChip("Komentar", { eventPrefs.config.value.enableComment }, { eventPrefs.setEnableComment(it) }))
            rowSwitch1.addView(createDenseChip("Gift", { eventPrefs.config.value.enableGift }, { eventPrefs.setEnableGift(it) }))
            content.addView(rowSwitch1)

            val rowSwitch2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(2))
            }
            rowSwitch2.addView(createDenseChip("Like", { eventPrefs.config.value.enableLike }, { eventPrefs.setEnableLike(it) }))
            rowSwitch2.addView(createDenseChip("Join", { eventPrefs.config.value.enableJoin }, { eventPrefs.setEnableJoin(it) }))
            content.addView(rowSwitch2)

            val rowSwitch3 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(2))
            }
            rowSwitch3.addView(createDenseChip("Follow", { eventPrefs.config.value.enableFollow }, { eventPrefs.setEnableFollow(it) }))
            rowSwitch3.addView(createDenseChip("Share", { eventPrefs.config.value.enableShare }, { eventPrefs.setEnableShare(it) }))
            content.addView(rowSwitch3)

            val rowSwitch4 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(2))
            }
            rowSwitch4.addView(createDenseChip("Penonton", { eventPrefs.config.value.enableViewerCount }, { eventPrefs.setEnableViewerCount(it) }))
            val chipDurasi = TextView(this).apply {
                val d = (eventPrefs.config.value.bannerDurationMs / 1000).toInt()
                text = "Durasi: ${d}s"
                textSize = 8.0f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dp(1), Color.parseColor("#475569"))
                }
                setPadding(dp(4), dp(4), dp(4), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                setOnClickListener {
                    val curD = eventPrefs.config.value.bannerDurationMs
                    val nextD = if (curD >= 6000L) 3000L else curD + 1500L
                    eventPrefs.setBannerDurationMs(nextD)
                    text = "Durasi: ${(nextD/1000)}s"
                }
            }
            rowSwitch4.addView(chipDurasi)
            content.addView(rowSwitch4)

            val rowSwitch5 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            rowSwitch5.addView(createDenseChip("Analitik", { eventPrefs.config.value.enableEventStatsOverlay }, { eventPrefs.setEnableEventStatsOverlay(it) }))
            rowSwitch5.addView(createDenseChip("Diamond", { eventPrefs.config.value.showDiamondsInAnalytics }, { eventPrefs.setShowDiamondsInAnalytics(it) }))
            content.addView(rowSwitch5)
        }
        brokerContainer.addView(switchCard)

        // =========================================================================
        // 3. KATEGORI: TOP PENULIS KHATAMAN (4 KATEGORI)
        // =========================================================================
        val btnToggleTopLikers = TextView(this).apply {
            val isOn = eventPrefs.config.value.enableTopLikersOverlay
            text = if (isOn) "ON" else "OFF"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(if (isOn) Color.parseColor("#E11D48") else Color.parseColor("#334155"))
            }
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }

        val topLikersCard = createCollapsibleSectionCard(
            this, dp,
            title = "Top Penulis Khatam (4 Kategori)",
            accentHex = "#F43F5E",
            defaultExpanded = true,
            headerRightView = btnToggleTopLikers
        ) { content ->
            val tvTopLikersInfo = TextView(this).apply {
                val c = eventPrefs.config.value
                val oriLabel = if (c.topLikersOrientation == "VERTICAL") "Vertikal" else "Horizontal"
                text = "Format: $oriLabel | Pos: ${(c.topLikersPosXpct*100).toInt()}%, ${(c.topLikersPosYpct*100).toInt()}% | Skala: ${"%.2f".format(c.topLikersScale)}x\nLebar: ${(c.topLikersWidthPct*100).toInt()}% | Tinggi: ${c.topLikersHeightPadV}dp | Latin: ${"%.1f".format(c.topLikersLatinFontSizeSp)}sp | Angka: ${"%.1f".format(c.topLikersFontSizeSp)}sp"
                textSize = 7.2f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(1), 0, dp(2))
            }
            content.addView(tvTopLikersInfo)

            val rowTopLikersMode = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(2))
            }

            val tvLblFormat = TextView(this).apply {
                text = "Format:"
                textSize = 7.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#FDE047"))
                setPadding(0, 0, dp(2), 0)
            }
            rowTopLikersMode.addView(tvLblFormat)

            val btnTopLikersOrientation = TextView(this).apply {
                val c = eventPrefs.config.value
                text = if (c.topLikersOrientation == "VERTICAL") "↕️ Vertikal" else "↔️ Horizontal"
                textSize = 7.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(3), dp(5), dp(3))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f).apply {
                    marginEnd = dp(2)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#BE123C"))
                    setStroke(dp(1), Color.parseColor("#FB7185"))
                }
            }
            rowTopLikersMode.addView(btnTopLikersOrientation)

            fun updateTopLikersLabel() {
                val c = eventPrefs.config.value
                val oriLabel = if (c.topLikersOrientation == "VERTICAL") "Vertikal" else "Horizontal"
                tvTopLikersInfo.text = "Format: $oriLabel | Pos: ${(c.topLikersPosXpct*100).toInt()}%, ${(c.topLikersPosYpct*100).toInt()}% | Skala: ${"%.2f".format(c.topLikersScale)}x\nLebar: ${(c.topLikersWidthPct*100).toInt()}% | Tinggi: ${c.topLikersHeightPadV}dp | Latin: ${"%.1f".format(c.topLikersLatinFontSizeSp)}sp | Angka: ${"%.1f".format(c.topLikersFontSizeSp)}sp"
                btnTopLikersOrientation.text = if (c.topLikersOrientation == "VERTICAL") "↕️ Vertikal" else "↔️ Horizontal"
            }

            btnTopLikersOrientation.setOnClickListener {
                eventPrefs.toggleTopLikersOrientation()
                updateTopLikersLabel()
            }

            rowTopLikersMode.addView(createMiniStepper("Bawah") {
                eventPrefs.setTopLikersPos(0.50f, 0.85f)
                updateTopLikersLabel()
            })
            rowTopLikersMode.addView(createMiniStepper("Tengah") {
                eventPrefs.setTopLikersPos(0.50f, 0.50f)
                updateTopLikersLabel()
            })
            rowTopLikersMode.addView(createMiniStepper("Kiri") {
                eventPrefs.setTopLikersOrientation("VERTICAL")
                eventPrefs.setTopLikersPos(0.18f, 0.55f)
                updateTopLikersLabel()
            })
            rowTopLikersMode.addView(createMiniStepper("Kanan") {
                eventPrefs.setTopLikersOrientation("VERTICAL")
                eventPrefs.setTopLikersPos(0.82f, 0.55f)
                updateTopLikersLabel()
            })
            content.addView(rowTopLikersMode)

            // Baris Kategori Aktif: [Qur'an] [Surat] [Ayat] [Huruf]
            val rowCats = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(2))
            }
            val tvLblCats = TextView(this).apply {
                text = "Kategori:"
                textSize = 7.2f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#38BDF8"))
                setPadding(0, 0, dp(2), 0)
            }
            rowCats.addView(tvLblCats)

            rowCats.addView(createDenseChip("Qur'an", { eventPrefs.config.value.showTopQuran }, { eventPrefs.setShowTopQuran(it) }))
            rowCats.addView(createDenseChip("Surat", { eventPrefs.config.value.showTopSurah }, { eventPrefs.setShowTopSurah(it) }))
            rowCats.addView(createDenseChip("Ayat", { eventPrefs.config.value.showTopAyah }, { eventPrefs.setShowTopAyah(it) }))
            rowCats.addView(createDenseChip("Huruf", { eventPrefs.config.value.showTopHuruf }, { eventPrefs.setShowTopHuruf(it) }))
            content.addView(rowCats)

            // Baris 1: Posisi D-Pad & Skala Bebas (Tanpa Batasan Kaku)
            val rowPosAndScale = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(2))
            }
            val tvLblPos = TextView(this).apply {
                text = "Pos & Skala:"
                textSize = 7.2f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#94A3B8"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            rowPosAndScale.addView(tvLblPos)
            rowPosAndScale.addView(createMiniStepper("⬅️") {
                eventPrefs.adjustTopLikersPos(-0.04f, 0f)
                updateTopLikersLabel()
            })
            rowPosAndScale.addView(createMiniStepper("⬆️") {
                eventPrefs.adjustTopLikersPos(0f, -0.04f)
                updateTopLikersLabel()
            })
            rowPosAndScale.addView(createMiniStepper("⬇️") {
                eventPrefs.adjustTopLikersPos(0f, 0.04f)
                updateTopLikersLabel()
            })
            rowPosAndScale.addView(createMiniStepper("➡️") {
                eventPrefs.adjustTopLikersPos(0.04f, 0f)
                updateTopLikersLabel()
            })
            rowPosAndScale.addView(createMiniStepper("➖") {
                eventPrefs.adjustTopLikersScale(-0.1f)
                updateTopLikersLabel()
            })
            rowPosAndScale.addView(createMiniStepper("➕") {
                eventPrefs.adjustTopLikersScale(0.1f)
                updateTopLikersLabel()
            })
            rowPosAndScale.addView(createMiniStepper("↺") {
                eventPrefs.setTopLikersScale(1.0f)
                updateTopLikersLabel()
            })
            content.addView(rowPosAndScale)

            // Baris 2: Lebar & Tinggi Bebas (Tanpa Batasan Kaku)
            val rowWidthHeight = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(2))
            }
            val tvLblSize = TextView(this).apply {
                text = "Lebar/Tinggi:"
                textSize = 7.2f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#38BDF8"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            rowWidthHeight.addView(tvLblSize)
            rowWidthHeight.addView(createMiniStepper("➖") {
                eventPrefs.adjustTopLikersWidthPct(-0.04f)
                updateTopLikersLabel()
            })
            rowWidthHeight.addView(createMiniStepper("➕") {
                eventPrefs.adjustTopLikersWidthPct(0.04f)
                updateTopLikersLabel()
            })
            rowWidthHeight.addView(createMiniStepper("↺") {
                eventPrefs.setTopLikersWidthPct(0.98f)
                updateTopLikersLabel()
            })
            rowWidthHeight.addView(createMiniStepper("➖") {
                eventPrefs.adjustTopLikersHeightPadV(-2)
                updateTopLikersLabel()
            })
            rowWidthHeight.addView(createMiniStepper("➕") {
                eventPrefs.adjustTopLikersHeightPadV(2)
                updateTopLikersLabel()
            })
            rowWidthHeight.addView(createMiniStepper("↺") {
                eventPrefs.setTopLikersHeightPadV(8)
                updateTopLikersLabel()
            })
            content.addView(rowWidthHeight)

            // Baris 3: Font Latin & Font Angka Bebas (Tanpa Batasan Kaku)
            val rowFonts = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(2))
            }
            val tvLblFonts = TextView(this).apply {
                text = "Latin & Angka:"
                textSize = 7.2f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#A78BFA"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            rowFonts.addView(tvLblFonts)
            rowFonts.addView(createMiniStepper("➖") {
                eventPrefs.adjustTopLikersLatinFontSizeSp(-0.5f)
                updateTopLikersLabel()
            })
            rowFonts.addView(createMiniStepper("➕") {
                eventPrefs.adjustTopLikersLatinFontSizeSp(0.5f)
                updateTopLikersLabel()
            })
            rowFonts.addView(createMiniStepper("↺") {
                eventPrefs.setTopLikersLatinFontSizeSp(11.5f)
                updateTopLikersLabel()
            })
            rowFonts.addView(createMiniStepper("➖") {
                eventPrefs.adjustTopLikersFontSizeSp(-0.5f)
                updateTopLikersLabel()
            })
            rowFonts.addView(createMiniStepper("➕") {
                eventPrefs.adjustTopLikersFontSizeSp(0.5f)
                updateTopLikersLabel()
            })
            rowFonts.addView(createMiniStepper("↺") {
                eventPrefs.setTopLikersFontSizeSp(12.0f)
                updateTopLikersLabel()
            })
            content.addView(rowFonts)

            // Baris 4: Tombol Reset Data Peringkat & Simulasi Tes
            val rowActions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(2))
            }

            val btnResetLeaderboard = TextView(this).apply {
                text = "🗑️ Reset Peringkat"
                textSize = 7.4f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(4), dp(6), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#7F1D1D"))
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                }
                setOnClickListener {
                    receiver.clearLeaderboards()
                    Toast.makeText(this@with, "Peringkat penulis di-reset!", Toast.LENGTH_SHORT).show()
                }
            }
            rowActions.addView(btnResetLeaderboard)

            val btnTestLeaderboard = TextView(this).apply {
                text = "▶️ Tes Data Penulis"
                textSize = 7.4f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(4), dp(6), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#065F46"))
                    setStroke(dp(1), Color.parseColor("#10B981"))
                }
                setOnClickListener {
                    if (!eventPrefs.config.value.enableTopLikersOverlay) {
                        eventPrefs.setEnableTopLikersOverlay(true)
                        btnToggleTopLikers.text = "ON"
                        (btnToggleTopLikers.background as? GradientDrawable)?.setColor(Color.parseColor("#E11D48"))
                    }
                    receiver.injectTestTopLikersActivity()
                    Toast.makeText(this@with, "Data uji penulis ditambahkan ✨", Toast.LENGTH_SHORT).show()
                }
            }
            rowActions.addView(btnTestLeaderboard)
            content.addView(rowActions)
        }

        btnToggleTopLikers.setOnClickListener {
            val next = !eventPrefs.config.value.enableTopLikersOverlay
            eventPrefs.setEnableTopLikersOverlay(next)
            btnToggleTopLikers.text = if (next) "ON" else "OFF"
            (btnToggleTopLikers.background as? GradientDrawable)?.setColor(if (next) Color.parseColor("#E11D48") else Color.parseColor("#334155"))
        }
        brokerContainer.addView(topLikersCard)

        // =========================================================================
        // 4. KATEGORI: FOTO MELAYANG (DROPDOWN)
        // =========================================================================
        val btnToggleAvatarMaster = TextView(this).apply {
            val isOn = eventPrefs.config.value.enableAvatarBubble
            text = if (isOn) "ON" else "OFF"
            textSize = 8.0f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(if (isOn) Color.parseColor("#DB2777") else Color.parseColor("#334155"))
            }
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }

        val avatarCard = createCollapsibleSectionCard(
            this, dp,
            title = "Foto Melayang",
            accentHex = "#EC4899",
            defaultExpanded = false,
            headerRightView = btnToggleAvatarMaster
        ) { content ->
            val rowAvatarEv1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(2))
            }
            rowAvatarEv1.addView(createDenseChip("Chat", { eventPrefs.config.value.avatarOnChat }, { eventPrefs.setAvatarOnChat(it) }))
            rowAvatarEv1.addView(createDenseChip("Gift", { eventPrefs.config.value.avatarOnGift }, { eventPrefs.setAvatarOnGift(it) }))
            rowAvatarEv1.addView(createDenseChip("Like", { eventPrefs.config.value.avatarOnLike }, { eventPrefs.setAvatarOnLike(it) }))
            content.addView(rowAvatarEv1)

            val rowAvatarEv2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(3))
            }
            rowAvatarEv2.addView(createDenseChip("Join", { eventPrefs.config.value.avatarOnJoin }, { eventPrefs.setAvatarOnJoin(it) }))
            rowAvatarEv2.addView(createDenseChip("Follow", { eventPrefs.config.value.avatarOnFollow }, { eventPrefs.setAvatarOnFollow(it) }))
            rowAvatarEv2.addView(createDenseChip("Share", { eventPrefs.config.value.avatarOnShare }, { eventPrefs.setAvatarOnShare(it) }))
            content.addView(rowAvatarEv2)

            fun formatZoneLabel(zone: String): String = when (zone.uppercase()) {
                "LEFT" -> "Kiri"
                "CENTER" -> "Tengah"
                "RIGHT" -> "Kanan"
                else -> "Menyebar"
            }

            var selectedAvatarSizeTarget: LiveEventType? = null // null = Semua Event

            fun formatTargetEventLabel(target: LiveEventType?): String = when (target) {
                LiveEventType.CHAT -> "Chat"
                LiveEventType.GIFT -> "Gift"
                LiveEventType.LIKE -> "Like"
                LiveEventType.MEMBER -> "Join"
                LiveEventType.FOLLOW -> "Follow"
                LiveEventType.SHARE -> "Share"
                else -> "Semua"
            }

            val tvAvatarTuneInfo = TextView(this).apply {
                textSize = 7.3f
                setTextColor(Color.parseColor("#F9A8D4"))
                setPadding(0, dp(1), 0, dp(2))
            }
            content.addView(tvAvatarTuneInfo)

            fun updateAvatarTuneLabel() {
                val c = eventPrefs.config.value
                val target = selectedAvatarSizeTarget
                val targetStr = formatTargetEventLabel(target)
                val curScale = if (target != null) c.getAvatarScaleForEvent(target) else c.avatarSizeScale
                val curDurMs = if (target != null) c.getAvatarDurationForEvent(target) else c.avatarFlightDurationMs
                val curDurSec = "%.1f".format(curDurMs / 1000f)
                val chatDurSec = "%.1f".format(c.avatarDurationChat / 1000f)
                val giftDurSec = "%.1f".format(c.avatarDurationGift / 1000f)
                tvAvatarTuneInfo.text = "Target [$targetStr: ${"%.1f".format(curScale)}x • ${curDurSec}s] | Chat:${chatDurSec}s Gift:${giftDurSec}s | ${formatZoneLabel(c.avatarSpawnZone)}"
            }
            updateAvatarTuneLabel()

            val rowAvatarSizeTarget = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(2))
            }
            val targetButtons = mutableListOf<Pair<LiveEventType?, TextView>>()
            fun refreshTargetButtonsUi() {
                for ((t, btn) in targetButtons) {
                    val active = (t == selectedAvatarSizeTarget)
                    btn.setTextColor(if (active) Color.WHITE else Color.parseColor("#CBD5E1"))
                    btn.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(3).toFloat()
                        setColor(if (active) Color.parseColor("#BE185D") else Color.parseColor("#1E293B"))
                        setStroke(dp(1), if (active) Color.parseColor("#F472B6") else Color.parseColor("#475569"))
                    }
                }
            }
            val sizeTargets = listOf(
                null to "Semua",
                LiveEventType.CHAT to "Chat",
                LiveEventType.GIFT to "Gift",
                LiveEventType.LIKE to "Like",
                LiveEventType.MEMBER to "Join",
                LiveEventType.FOLLOW to "Follow",
                LiveEventType.SHARE to "Share"
            )
            for ((targetType, label) in sizeTargets) {
                val btn = TextView(this).apply {
                    text = label
                    textSize = 7.0f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(dp(2), dp(3), dp(2), dp(3))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(1)
                        marginEnd = dp(1)
                    }
                    setOnClickListener {
                        selectedAvatarSizeTarget = targetType
                        refreshTargetButtonsUi()
                        updateAvatarTuneLabel()
                    }
                }
                targetButtons.add(targetType to btn)
                rowAvatarSizeTarget.addView(btn)
            }
            refreshTargetButtonsUi()
            content.addView(rowAvatarSizeTarget)

            val rowAvatarSizeControls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(2))
            }
            val tvLblUkuran = TextView(this).apply {
                text = "Ukuran:"
                textSize = 7.3f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F472B6"))
                setPadding(0, 0, dp(2), 0)
            }
            rowAvatarSizeControls.addView(tvLblUkuran)
            rowAvatarSizeControls.addView(createMiniStepper("➖") {
                val c = eventPrefs.config.value
                val target = selectedAvatarSizeTarget
                if (target == null) {
                    eventPrefs.setAvatarSizeScale((c.avatarSizeScale - 0.10f).coerceIn(0.20f, 4.50f))
                } else {
                    val cur = c.getAvatarScaleForEvent(target)
                    eventPrefs.setAvatarSizeForEvent(target, (cur - 0.10f).coerceIn(0.20f, 4.50f))
                }
                updateAvatarTuneLabel()
                receiver.injectTestAvatarBubble(target)
            })
            rowAvatarSizeControls.addView(createMiniStepper("➕") {
                val c = eventPrefs.config.value
                val target = selectedAvatarSizeTarget
                if (target == null) {
                    eventPrefs.setAvatarSizeScale((c.avatarSizeScale + 0.10f).coerceIn(0.20f, 4.50f))
                } else {
                    val cur = c.getAvatarScaleForEvent(target)
                    eventPrefs.setAvatarSizeForEvent(target, (cur + 0.10f).coerceIn(0.20f, 4.50f))
                }
                updateAvatarTuneLabel()
                receiver.injectTestAvatarBubble(target)
            })
            // Presets for quick size selection
            listOf(0.6f to "0.6x", 1.0f to "1.0x", 1.6f to "1.6x", 2.4f to "2.4x").forEach { (scVal, scLbl) ->
                rowAvatarSizeControls.addView(createMiniStepper(scLbl) {
                    val target = selectedAvatarSizeTarget
                    if (target == null) {
                        eventPrefs.setAvatarSizeScale(scVal)
                    } else {
                        eventPrefs.setAvatarSizeForEvent(target, scVal)
                    }
                    updateAvatarTuneLabel()
                    receiver.injectTestAvatarBubble(target)
                })
            }
            content.addView(rowAvatarSizeControls)

            val rowAvatarDurationControls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(2))
            }
            val tvLblDurasi = TextView(this).apply {
                text = "Durasi:"
                textSize = 7.3f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F472B6"))
                setPadding(0, 0, dp(2), 0)
            }
            rowAvatarDurationControls.addView(tvLblDurasi)
            rowAvatarDurationControls.addView(createMiniStepper("➖") {
                val c = eventPrefs.config.value
                val target = selectedAvatarSizeTarget
                if (target == null) {
                    eventPrefs.setAvatarFlightDurationMs((c.avatarFlightDurationMs - 300L).coerceIn(800L, 20000L))
                } else {
                    val cur = c.getAvatarDurationForEvent(target)
                    eventPrefs.setAvatarDurationForEvent(target, (cur - 300L).coerceIn(800L, 20000L))
                }
                updateAvatarTuneLabel()
                receiver.injectTestAvatarBubble(target)
            })
            rowAvatarDurationControls.addView(createMiniStepper("➕") {
                val c = eventPrefs.config.value
                val target = selectedAvatarSizeTarget
                if (target == null) {
                    eventPrefs.setAvatarFlightDurationMs((c.avatarFlightDurationMs + 300L).coerceIn(800L, 20000L))
                } else {
                    val cur = c.getAvatarDurationForEvent(target)
                    eventPrefs.setAvatarDurationForEvent(target, (cur + 300L).coerceIn(800L, 20000L))
                }
                updateAvatarTuneLabel()
                receiver.injectTestAvatarBubble(target)
            })
            // Presets for quick duration selection
            listOf(1500L to "1.5s", 3000L to "3s", 4500L to "4.5s", 7000L to "7s").forEach { (durVal, durLbl) ->
                rowAvatarDurationControls.addView(createMiniStepper(durLbl) {
                    val target = selectedAvatarSizeTarget
                    if (target == null) {
                        eventPrefs.setAvatarFlightDurationMs(durVal)
                    } else {
                        eventPrefs.setAvatarDurationForEvent(target, durVal)
                    }
                    updateAvatarTuneLabel()
                    receiver.injectTestAvatarBubble(target)
                })
            }
            rowAvatarDurationControls.addView(createMiniStepper("Area") {
                val zones = listOf("SPREAD", "LEFT", "CENTER", "RIGHT")
                val curIdx = zones.indexOf(eventPrefs.config.value.avatarSpawnZone.uppercase()).coerceAtLeast(0)
                val nextZone = zones[(curIdx + 1) % zones.size]
                eventPrefs.setAvatarSpawnZone(nextZone)
                updateAvatarTuneLabel()
                receiver.injectTestAvatarBubble(selectedAvatarSizeTarget)
            })
            rowAvatarDurationControls.addView(createMiniStepper("▶") {
                receiver.injectTestAvatarBubble(selectedAvatarSizeTarget)
            })
            rowAvatarDurationControls.addView(createMiniStepper("✨Gift") {
                receiver.injectTestAvatarBubble(LiveEventType.GIFT)
            })
            content.addView(rowAvatarDurationControls)
        }

        btnToggleAvatarMaster.setOnClickListener {
            val next = !eventPrefs.config.value.enableAvatarBubble
            eventPrefs.setEnableAvatarBubble(next)
            btnToggleAvatarMaster.text = if (next) "ON" else "OFF"
            (btnToggleAvatarMaster.background as? GradientDrawable)?.setColor(if (next) Color.parseColor("#DB2777") else Color.parseColor("#334155"))
        }
        brokerContainer.addView(avatarCard)

        // =========================================================================
        // 5. KATEGORI: UJI COBA (DROPDOWN)
        // =========================================================================
        val tvLastEvent = TextView(this).apply {
            text = "Event: Menunggu live stream..."
            textSize = 7.5f
            setTextColor(Color.parseColor("#38BDF8"))
            maxLines = 1
        }

        val simCard = createCollapsibleSectionCard(
            this, dp,
            title = "Uji Coba",
            accentHex = "#6366F1",
            defaultExpanded = false
        ) { content ->
            val rowSimButtons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(3))
            }
            fun createSimButton(lbl: String, onAction: () -> Unit): TextView = TextView(this).apply {
                text = lbl
                textSize = 8.0f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.parseColor("#312E81"))
                    setStroke(dp(1), Color.parseColor("#4F46E5"))
                }
                setPadding(dp(2), dp(4), dp(2), dp(4))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
                setOnClickListener { onAction() }
            }

            rowSimButtons.addView(createSimButton("Chat") { receiver.injectTestEvent(LiveEventType.CHAT) })
            rowSimButtons.addView(createSimButton("Gift") { receiver.injectTestEvent(LiveEventType.GIFT) })
            val isCurrentBigo = eventPrefs.config.value.activePlatform.equals("BIGO", ignoreCase = true)
            val btnSpecialGift = createSimButton(if (isCurrentBigo) "Supercar" else "Paus") {
                if (eventPrefs.config.value.activePlatform.equals("BIGO", ignoreCase = true)) {
                    receiver.injectTestBigoGift("Supercar", 3000)
                } else {
                    receiver.injectTestPausGift()
                }
            }
            rowSimButtons.addView(btnSpecialGift)
            rowSimButtons.addView(createSimButton("Like") { receiver.injectTestEvent(LiveEventType.LIKE) })
            rowSimButtons.addView(createSimButton("Top 10") { receiver.injectTestTopLikersActivity() })
            rowSimButtons.addView(createSimButton("Foto") { receiver.injectTestAvatarBubble() })
            content.addView(rowSimButtons)
            content.addView(tvLastEvent)
        }
        brokerContainer.addView(simCard)

        // Realtime Observers
        serviceScope.launch {
            receiver.connectionStatus.collectLatest { st ->
                when (st) {
                    ConnectionStatus.CONNECTED -> {
                        badgeStatus.text = "TERHUBUNG"
                        badgeStatus.setTextColor(Color.parseColor("#22C55E"))
                        (badgeStatus.background as? GradientDrawable)?.apply {
                            setColor(Color.parseColor("#14532D"))
                            setStroke(dp(1), Color.parseColor("#22C55E"))
                        }
                        btnConnectToggle.text = "PUTUS"
                        (btnConnectToggle.background as? GradientDrawable)?.setColor(Color.parseColor("#DC2626"))
                    }
                    ConnectionStatus.CONNECTING -> {
                        badgeStatus.text = "MENYAMBUNG"
                        badgeStatus.setTextColor(Color.parseColor("#F59E0B"))
                        (badgeStatus.background as? GradientDrawable)?.apply {
                            setColor(Color.parseColor("#78350F"))
                            setStroke(dp(1), Color.parseColor("#F59E0B"))
                        }
                        btnConnectToggle.text = "BATAL"
                        (btnConnectToggle.background as? GradientDrawable)?.setColor(Color.parseColor("#475569"))
                    }
                    else -> {
                        badgeStatus.text = "PUTUS"
                        badgeStatus.setTextColor(Color.parseColor("#EF4444"))
                        (badgeStatus.background as? GradientDrawable)?.apply {
                            setColor(Color.parseColor("#450A0A"))
                            setStroke(dp(1), Color.parseColor("#DC2626"))
                        }
                        btnConnectToggle.text = "SAMBUNG"
                        (btnConnectToggle.background as? GradientDrawable)?.setColor(Color.parseColor("#059669"))
                    }
                }
            }
        }

        fun updateConnDetails() {
            val dt = receiver.connectionDetails.value
            val v = receiver.viewerCount.value
            val tot = receiver.totalReceivedCount.value
            tvConnDetails.text = "$dt | Penonton: $v | Total: $tot"
        }

        serviceScope.launch {
            receiver.connectionDetails.collectLatest {
                updateConnDetails()
                refreshTargetChipsFn?.invoke()
            }
        }

        serviceScope.launch {
            receiver.viewerCount.collectLatest {
                updateConnDetails()
                updateHostAnalyticsUiFn?.invoke()
            }
        }

        serviceScope.launch {
            receiver.totalReceivedCount.collectLatest {
                updateConnDetails()
                updateHostAnalyticsUiFn?.invoke()
            }
        }

        serviceScope.launch {
            receiver.totalLikesAccumulated.collectLatest {
                updateHostAnalyticsUiFn?.invoke()
            }
        }

        serviceScope.launch {
            receiver.totalChatsAccumulated.collectLatest {
                updateHostAnalyticsUiFn?.invoke()
            }
        }

        serviceScope.launch {
            receiver.totalGiftsAccumulated.collectLatest {
                updateHostAnalyticsUiFn?.invoke()
            }
        }

        serviceScope.launch {
            receiver.totalDiamondsAccumulated.collectLatest {
                updateTargetGiftUiFn?.invoke()
                updateHostAnalyticsUiFn?.invoke()
            }
        }

        serviceScope.launch {
            receiver.totalJoinsAccumulated.collectLatest {
                updateHostAnalyticsUiFn?.invoke()
            }
        }

        serviceScope.launch {
            receiver.totalFollowsAccumulated.collectLatest {
                updateHostAnalyticsUiFn?.invoke()
            }
        }

        serviceScope.launch {
            receiver.totalSharesAccumulated.collectLatest {
                updateHostAnalyticsUiFn?.invoke()
            }
        }

        serviceScope.launch {
            eventPrefs.config.collectLatest {
                refreshTargetChipsFn?.invoke()
                updateTargetGiftUiFn?.invoke()
                updateHostAnalyticsUiFn?.invoke()
            }
        }

        serviceScope.launch {
            receiver.lastEvent.collectLatest { ev ->
                if (ev != null) {
                    tvLastEvent.text = "[${ev.type.name}] ${ev.senderName}: ${ev.text}"
                }
            }
        }

        brokerContainer
    }
}
