package com.tajasb.floating

import android.app.Service
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.tajasb.FloatingTab

/**
 * FloatingServiceViewHost:
 * Base class that cleanly groups and holds all floating panel View references,
 * chip lists, and UI state holders by feature/tab so FloatingControlService
 * remains focused on service lifecycle, state synchronization, and actions.
 */
abstract class FloatingServiceViewHost : Service() {

    // =========================================================================
    // 1. WINDOW MANAGER & CORE PANEL CONTAINERS
    // =========================================================================
    internal lateinit var windowManager: WindowManager
    internal var floatingBubbleView: View? = null
    internal var expandedControlPanelView: View? = null

    internal var bubbleLayoutParams: WindowManager.LayoutParams? = null
    internal var panelLayoutParams: WindowManager.LayoutParams? = null

    internal var isPanelExpanded = true
    internal var currentTab = FloatingTab.VIDEO

    // =========================================================================
    // 2. MINIMIZED BUBBLE BAR INDICATOR VIEWS
    // =========================================================================
    internal var bubbleBtnVideo: TextView? = null
    internal var bubbleBtnMusic: TextView? = null
    internal var bubbleBtnMediaAudio: TextView? = null
    internal var bubbleBtnPin: TextView? = null
    internal var bubbleBtnTap: TextView? = null
    internal var spacerVideo: View? = null
    internal var spacerMusic: View? = null
    internal var spacerMediaAudio: View? = null
    internal var spacerPin: View? = null
    internal var spacerTap: View? = null
    internal var bubbleTextBadge: TextView? = null

    // =========================================================================
    // 3. HEADER, NAVIGATION TABS & SCROLLABLE HOLDER
    // =========================================================================
    internal var panelTitleStatus: TextView? = null
    internal var btnHeaderTargetScope: TextView? = null
    internal var tvSystemActiveHeaderBadge: TextView? = null
    internal var floatingAudioSignalWarningBar: TextView? = null

    internal var btnTabVideo: TextView? = null
    internal var btnTabMusic: TextView? = null
    internal var btnTabVisual: TextView? = null
    internal var btnTabNotif: TextView? = null
    internal var btnTabTap: TextView? = null
    internal var btnTabSettings: TextView? = null
    internal var btnTabLog: TextView? = null

    internal var containerTabVideo: LinearLayout? = null
    internal var containerTabMusic: LinearLayout? = null
    internal var containerTabVisual: LinearLayout? = null
    internal var containerTabNotif: LinearLayout? = null
    internal var containerTabTap: LinearLayout? = null
    internal var containerTabSettings: LinearLayout? = null
    internal var containerTabLog: LinearLayout? = null
    internal var stickyTapActionBar: LinearLayout? = null
    internal var tabContentScrollView: ScrollView? = null
    internal var tabContentHolder: LinearLayout? = null

    // =========================================================================
    // 4. TAB 1: VIDEO & SPILL PRODUK VIEWS
    // =========================================================================
    internal var btnSwitchModeText: TextView? = null
    internal var btnSpillSwitchModeText: TextView? = null
    internal var btnModeStandardVideo: TextView? = null
    internal var btnToggleMasterSpillText: TextView? = null
    internal var btnToggleAutoCommentSpillText: TextView? = null
    internal var btnToggleBridgeCycleText: TextView? = null
    internal var containerVideoModeStandard: LinearLayout? = null
    internal var containerVideoModeSpill: LinearLayout? = null
    internal var containerVideoModeIfs: LinearLayout? = null
    internal var tvMasterSpillStatusBanner: TextView? = null
    internal var btnReturnMasterQuick: TextView? = null
    internal var quickSpillChipsContainer: LinearLayout? = null
    internal var spillActionControlsRow: LinearLayout? = null
    internal var spillScrollChips: View? = null
    internal var spillTestCommentRow: LinearLayout? = null
    internal var spillQuickChipsRow: LinearLayout? = null
    internal var spillScanLogButton: TextView? = null

    // Makkah Live Stream Floating Card Views
    internal var cardMakkahLiveRelay: LinearLayout? = null
    internal var makkahControlsContainer: LinearLayout? = null
    internal var btnToggleMakkahRelay: TextView? = null
    internal var tvMakkahRelayStatus: TextView? = null
    internal var btnMakkahStream1: TextView? = null
    internal var btnMakkahStream2: TextView? = null
    internal var btnMakkahStream3: TextView? = null
    internal var btnMakkahRefreshStream: TextView? = null
    internal var btnMakkahScaleCrop: TextView? = null
    internal var btnMakkahScaleFit: TextView? = null
    internal var btnMakkahScaleStretch: TextView? = null
    internal var tvMakkahZoomLabel: TextView? = null
    internal var tvMakkahPanLabel: TextView? = null
    internal var tvMakkahRotationLabel: TextView? = null
    internal var tvMakkahTelemetryInfo: TextView? = null

    // ⚡ TikTok Live Event Broker Floating Views (Device 1 -> Device 2)
    internal var cardLiveEventBroker: LinearLayout? = null
    internal var tvBrokerStatusBadge: TextView? = null
    internal var btnBrokerToggleConn: TextView? = null
    internal var btnBrokerSwitchChat: TextView? = null
    internal var btnBrokerSwitchGift: TextView? = null
    internal var btnBrokerSwitchLike: TextView? = null
    internal var btnBrokerSwitchJoin: TextView? = null
    internal var btnBrokerSwitchFollow: TextView? = null
    internal var btnBrokerSwitchShare: TextView? = null
    internal var btnBrokerSwitchViewers: TextView? = null
    internal var tvBrokerEventLastLog: TextView? = null

    internal var videoStatusText: TextView? = null
    internal var quickListWrapperLayout: LinearLayout? = null
    internal var quickVideoSearchEditText: EditText? = null
    internal var quickVideoSearchQuery: String = ""
    internal var quickVideoListContainer: LinearLayout? = null
    internal var quickListScrollView: ScrollView? = null
    internal var isQuickListOpen = false
    internal var btnPlayPauseVideoText: TextView? = null
    internal var btnToggleAllVideosEyeText: TextView? = null
    internal var btnVideoAudioMuteText: TextView? = null
    internal var btnPlayModeQuickText: TextView? = null
    internal var btnAutoNextLeadText: TextView? = null
    internal var btnDualAudioMixingText: TextView? = null
    internal var btnDualAudioMixingMusicText: TextView? = null
    internal var quickModePickerContainer: LinearLayout? = null
    internal var btnAutoNextSeq: TextView? = null
    internal var btnAutoNextRand: TextView? = null
    internal var btnAutoNextSingleLoop: TextView? = null
    internal var autoNextStatusLabel: TextView? = null
    internal var videoTimeLabel: TextView? = null
    internal var videoSeekBar: SeekBar? = null
    internal var isUserTrackingVideoSeek = false
    internal var videoVolumeSeekBar: SeekBar? = null
    internal var videoVolumeLabel: TextView? = null
    internal var currentVideoDurationMs: Long = 0L
    internal var currentVideoPositionMs: Long = 0L

    internal var btnFpsQuick: TextView? = null
    internal var quickFpsPickerContainer: LinearLayout? = null
    internal var qBtnFps15: TextView? = null
    internal var qBtnFps24: TextView? = null
    internal var qBtnFps30: TextView? = null
    internal var qBtnFps60: TextView? = null
    internal var btnFps15: TextView? = null
    internal var btnFps24: TextView? = null
    internal var btnFps30: TextView? = null
    internal var btnFps60: TextView? = null

    // =========================================================================
    // 5. TAB 2: MUSIC PLAYER VIEWS
    // =========================================================================
    internal var musicTrackTitleText: TextView? = null
    internal var musicStatusBadge: TextView? = null
    internal var musicTimeLabel: TextView? = null
    internal var musicSeekBar: SeekBar? = null
    internal var isUserTrackingMusicSeek = false
    internal var btnMusicPlayPauseText: TextView? = null
    internal var btnToggleAllAudioEyeText: TextView? = null
    internal var btnAudioMuteAllText: TextView? = null
    internal var btnMusicLoopText: TextView? = null
    internal var btnToggleAudioLayer2Text: TextView? = null
    internal var btnAudioLayer2PlayPauseText: TextView? = null
    internal var audioLayer2VolumeLabel: TextView? = null
    internal var btnToggleAudioLayer3Text: TextView? = null
    internal var btnTriggerTestLayer3Text: TextView? = null
    internal var audioLayer3VolumeLabel: TextView? = null
    internal var musicVolumeSeekBar: SeekBar? = null
    internal var musicVolumeLabel: TextView? = null
    internal var quickAudioListContainer: LinearLayout? = null
    internal var quickAudioListScrollView: ScrollView? = null
    internal var quickAudioListWrapper: LinearLayout? = null
    internal var quickAudioListToggleBtn: TextView? = null
    internal var quickAudioSearchQuery: String = ""
    internal var isQuickAudioListOpen = false
    internal val musicModeBtnMap = mutableMapOf<com.tajasb.infinity.IfsAudioMode, TextView>()
    internal val musicVoiceEffectBtnMap = mutableMapOf<com.tajasb.model.VoiceEffect, TextView>()
    internal var btnSv4AudioSlotSetupText: TextView? = null
    internal var realtimeTempoLabel: TextView? = null
    internal var realtimePitchLabel: TextView? = null
    internal var realtimeIntroLabel: TextView? = null
    internal var realtimeOutroLabel: TextView? = null

    // =========================================================================
    // 6. TAB 3: VISUAL TRANSFORM VIEWS
    // =========================================================================
    internal var btnRot0: TextView? = null
    internal var btnRot90: TextView? = null
    internal var btnRot180: TextView? = null
    internal var btnRot270: TextView? = null
    internal var btnMirrorToggle: TextView? = null
    internal var btnFlipVToggle: TextView? = null
    internal var btnScaleCrop: TextView? = null
    internal var btnScaleFit: TextView? = null
    internal var btnScaleStretch: TextView? = null
    internal var zoomValueBadgeText: TextView? = null
    internal var btnZoomMinus: TextView? = null
    internal var btnZoomReset: TextView? = null
    internal var btnZoomPlus: TextView? = null
    internal var btnZoomChip80: TextView? = null
    internal var btnZoomChip100: TextView? = null
    internal var btnZoomChip125: TextView? = null
    internal var btnZoomChip150: TextView? = null
    internal var btnZoomChip200: TextView? = null

    // =========================================================================
    // 7. TAB 4: OVERLAY SUB-MODES (DOA, JUALAN, STIKER, WATERMARK, SCAN LOG)
    // =========================================================================
    // Sub-Mode Switcher & Containers
    internal var btnModeDoaTab: TextView? = null
    internal var btnModeSalesTab: TextView? = null
    internal var btnModeStikerTab: TextView? = null
    internal var btnModeWatermarkTab: TextView? = null
    internal var btnModeAutoTimeTab: TextView? = null
    internal var btnModeQuranKhatamTab: TextView? = null
    internal var btnModeTugOfWarTab: TextView? = null
    internal var btnModeLiveEventTab: TextView? = null
    internal var btnModeScanLogTab: TextView? = null
    internal var containerModeDoa: LinearLayout? = null
    internal var containerModeSales: LinearLayout? = null
    internal var containerModeStiker: LinearLayout? = null
    internal var containerModeWatermark: LinearLayout? = null
    internal var containerModeAutoTime: LinearLayout? = null
    internal var containerModeQuranKhatam: LinearLayout? = null
    internal var containerModeTugOfWar: LinearLayout? = null
    internal var containerModeLiveEventBroker: LinearLayout? = null
    internal var containerModeScanLog: LinearLayout? = null
    internal var tvQuranPosInfo: TextView? = null

    // Sub-Mode 1: Live Comment / Doa Pin Views
    internal var btnToggleLiveCaptureText: TextView? = null
    internal var liveCaptureStatusBadge: TextView? = null
    internal var liveCommentQueueText: TextView? = null
    internal var liveCommentCurrentText: TextView? = null
    internal var btnSkipCommentText: TextView? = null
    internal var btnClearQueueText: TextView? = null
    internal var btnA11yStatusText: TextView? = null
    internal var btnToggleScanRecordText: TextView? = null
    internal var scanRecordCountBadge: TextView? = null
    internal val iconModeChipsList = mutableListOf<Pair<com.tajasb.livecomment.BannerIconMode, TextView>>()
    internal val prefixChipsList = mutableListOf<Pair<String, TextView>>()
    internal val formatModeChipsList = mutableListOf<Pair<com.tajasb.livecomment.CommentFormatMode, TextView>>()
    internal var customPrefixDisplayLabel: TextView? = null
    internal var btnToggleAreaFilterText: TextView? = null
    internal var scanAreaInfoText: TextView? = null
    internal val scanAreaPresetChipsList = mutableListOf<Pair<com.tajasb.livecomment.ScanAreaPreset, TextView>>()
    internal var scanAreaOverlayView: com.tajasb.livecomment.ScanAreaOverlayView? = null
    internal var overlayPosRotStatusText: TextView? = null
    internal var btnOverlayRotText: TextView? = null
    internal var btnOverlayMirrorText: TextView? = null

    // Scan Inspector & Event Broker States
    internal var isShowingScanLog: Boolean = false
    internal var isShowingLiveEvent: Boolean = false
    internal var isScanInspectorFrozen: Boolean = false
    internal var onRefreshAiWatermarkUi: (() -> Unit)? = null
    internal var scanLogA11yStatusBadge: TextView? = null
    internal var scanLogAreaInfoText: TextView? = null
    internal var scanLogCountAcceptedBadge: TextView? = null
    internal var scanLogCountSystemBadge: TextView? = null
    internal var scanLogCountDupBadge: TextView? = null
    internal var scanLogCountOutBadge: TextView? = null
    internal var scanLogContainerList: LinearLayout? = null
    internal var scanLogMenuCoveredBanner: TextView? = null
    internal var scanLogViewerAnchorText: TextView? = null
    internal var currentScanLogFilter: String = "ALL"
    internal val scanLogFilterChipsList = mutableListOf<Pair<String, TextView>>()

    // Sub-Mode 2: Fake Buyer / Order Notification Views
    internal var btnToggleBuyerNotifText: TextView? = null
    internal var buyerNotifStatusBadge: TextView? = null
    internal var buyerNotifCountText: TextView? = null
    internal var buyerNotifDurationText: TextView? = null
    internal var buyerNotifIntervalText: TextView? = null
    internal var buyerNotifPosText: TextView? = null
    internal var btnBuyerNotifLockText: TextView? = null
    internal var buyerNotifActionTextLabel: TextView? = null
    internal var buyerNotifCapsuleInfoText: TextView? = null
    internal var etBuyerCountMin: EditText? = null
    internal var etBuyerCountMax: EditText? = null
    internal var buyerCountRangeStatusText: TextView? = null
    internal var etBuyerProdMin: EditText? = null
    internal var etBuyerProdMax: EditText? = null
    internal var buyerProductRangeStatusText: TextView? = null
    internal var etCapsuleCustomColor: EditText? = null
    internal var buyerNotifFontAndIconInfoText: TextView? = null
    internal var buyerNotifCapsuleAlphaText: TextView? = null
    internal var buyerNotifCapsuleOutlineText: TextView? = null
    internal var buyerNotifCapsuleColorText: TextView? = null
    internal var btnBuyerNotifRotText: TextView? = null
    internal var btnBuyerMirrorText: TextView? = null
    internal val notifDurationChipsList = mutableListOf<Pair<Long, TextView>>()
    internal val notifIntervalChipsList = mutableListOf<Pair<Pair<Int, Int>, TextView>>()
    internal val notifActionTextChipsList = mutableListOf<Pair<String, TextView>>()
    internal val notifFormatChipsList = mutableListOf<Pair<String, TextView>>()
    internal val notifRangeChipsList = mutableListOf<Pair<String, TextView>>()
    internal val notifProductNoChipsList = mutableListOf<Pair<String, TextView>>()
    internal val notifDirectionChipsList = mutableListOf<Pair<String, TextView>>()
    internal val capsuleColorChipsList = mutableListOf<Pair<String, TextView>>()
    internal val capsuleAlphaChipsList = mutableListOf<Pair<Float, TextView>>()
    internal val capsuleOutlineWidthChipsList = mutableListOf<Pair<Int, TextView>>()
    internal val capsuleOutlineColorChipsList = mutableListOf<Pair<String, TextView>>()
    internal val notifFontSizeChipsList = mutableListOf<Pair<Float, TextView>>()
    internal val notifCartSizeChipsList = mutableListOf<Pair<Float, TextView>>()

    // Sub-Mode 3: Custom Stiker Views
    internal var btnToggleStikerText: TextView? = null
    internal var btnStikerRotText: TextView? = null
    internal var btnStikerMirrorText: TextView? = null
    internal var stikerOutlineInfoText: TextView? = null
    internal var stikerBoxAlphaText: TextView? = null
    internal var etStikerOutlineCustomColor: EditText? = null
    internal var btnToggleStikerRandomMove: TextView? = null
    internal var btnToggleStikerRunningText: TextView? = null
    internal var stikerLivePreviewView: View? = null
    internal val stikerBoxColorChipsList = mutableListOf<Pair<String, TextView>>()
    internal val stikerTextColorChipsList = mutableListOf<Pair<String, TextView>>()
    internal val stikerScaleChipsList = mutableListOf<Pair<Float, TextView>>()
    internal val stikerOutlineWidthChipsList = mutableListOf<Pair<Int, TextView>>()
    internal val stikerOutlineColorChipsList = mutableListOf<Pair<String, TextView>>()
    internal val stikerAlphaChipsList = mutableListOf<Pair<Float, TextView>>()
    internal val stikerDisplayStyleChipsList = mutableListOf<Pair<String, TextView>>()
    internal val stikerRunningSpeedChipsList = mutableListOf<Pair<Float, TextView>>()
    internal val stikerRunningDirChipsList = mutableListOf<Pair<String, TextView>>()

    // Sub-Mode 4: Custom Watermark Views
    internal var btnToggleWatermarkText: TextView? = null
    internal var btnWatermarkRotText: TextView? = null
    internal var btnWatermarkMirrorText: TextView? = null
    internal var watermarkOutlineInfoText: TextView? = null
    internal var watermarkBoxAlphaText: TextView? = null
    internal var btnToggleWatermarkRandomMove: TextView? = null
    internal var btnToggleWatermarkRunningText: TextView? = null
    internal var watermarkLivePreviewView: View? = null
    internal val watermarkBoxColorChipsList = mutableListOf<Pair<String, TextView>>()
    internal val watermarkTextColorChipsList = mutableListOf<Pair<String, TextView>>()
    internal val watermarkScaleChipsList = mutableListOf<Pair<Float, TextView>>()
    internal val watermarkOutlineWidthChipsList = mutableListOf<Pair<Int, TextView>>()
    internal val watermarkOutlineColorChipsList = mutableListOf<Pair<String, TextView>>()
    internal val watermarkAlphaChipsList = mutableListOf<Pair<Float, TextView>>()
    internal val watermarkDisplayStyleChipsList = mutableListOf<Pair<String, TextView>>()
    internal val watermarkRunningSpeedChipsList = mutableListOf<Pair<Float, TextView>>()
    internal val watermarkRunningDirChipsList = mutableListOf<Pair<String, TextView>>()

    // Sub-Mode 5: Auto Time Capsule ("Waktu Otomatis") Views
    internal var btnToggleAutoTimeText: TextView? = null
    internal var btnToggleAutoTimeSecondsText: TextView? = null
    internal var autoTimeLivePreviewView: View? = null
    internal var autoTimeCapsuleInfoText: TextView? = null
    internal var autoTimeAlphaInfoText: TextView? = null
    internal var autoTimeOutlineInfoText: TextView? = null
    internal var autoTimePosInfoText: TextView? = null
    internal var btnAutoTimeLockText: TextView? = null
    internal var btnAutoTimeRotText: TextView? = null
    internal var btnAutoTimeMirrorText: TextView? = null
    internal val autoTimeCapsuleColorChipsList = mutableListOf<Pair<String, TextView>>()
    internal val autoTimeAlphaChipsList = mutableListOf<Pair<Float, TextView>>()
    internal val autoTimeOutlineWidthChipsList = mutableListOf<Pair<Int, TextView>>()
    internal val autoTimeOutlineColorChipsList = mutableListOf<Pair<String, TextView>>()
    internal val autoTimeDateBoxBgChipsList = mutableListOf<Pair<String, TextView>>()
    internal val autoTimeDateTextChipsList = mutableListOf<Pair<String, TextView>>()
    internal val autoTimeClockTextChipsList = mutableListOf<Pair<String, TextView>>()
    internal val autoTimeScaleChipsList = mutableListOf<Pair<Float, TextView>>()

    // =========================================================================
    // 8. TAB 5: AUTO-TAP & MULTI-CHOREOGRAPHY OVERLAY VIEWS
    // =========================================================================
    internal var btnToggleTapText: TextView? = null
    internal var btnAntiRobotText: TextView? = null
    internal var tapStatusBadge: TextView? = null
    internal var tapPosText: TextView? = null
    internal var tapCountText: TextView? = null
    internal var crosshairView: View? = null
    internal var crosshairLayoutParams: WindowManager.LayoutParams? = null
    internal val multiMarkOverlayViews = mutableMapOf<String, Pair<View, WindowManager.LayoutParams>>()
    internal var isUserDraggingMark: Boolean = false
    internal var onRefreshTapMarksUi: (() -> Unit)? = null
    internal var isTapMarksUiDirty: Boolean = false

    // =========================================================================
    // 9. TAB 6: SETTINGS & ZERO-LOAD MODULE SWITCH VIEWS
    // =========================================================================
    internal var btnModuleSalesToggle: TextView? = null
    internal var btnModuleStickerToggle: TextView? = null
    internal var btnModuleWatermarkToggle: TextView? = null
    internal var btnModulePrayerToggle: TextView? = null
    internal var btnModuleQuranKhatamToggle: TextView? = null
    internal var btnModuleTapToggle: TextView? = null
    internal var btnModuleMusicToggle: TextView? = null
    internal var btnModuleOsdToggle: TextView? = null
    internal var btnModuleAutoTimeToggle: TextView? = null
    internal var btnModuleDnaToggle: TextView? = null
    internal var btnModuleAiDetectionToggle: TextView? = null
    internal var btnModuleQuranAudioToggle: TextView? = null
    internal var btnModuleMakkahRelayToggle: TextView? = null
    internal var btnModuleLiveEventBrokerToggle: TextView? = null
    internal var btnModuleScanInspectorToggle: TextView? = null
    internal var antiDetCardView: View? = null
    internal var audAntiHashRowView: View? = null
    internal var btnSessionDnaQuickView: View? = null
    internal var videoAiQuickRow: View? = null
    internal var aiWatermarkCardView: View? = null
    internal var btnMasterToggleText: TextView? = null
    internal var transparencyLabel: TextView? = null
    internal var transparencySeekBar: SeekBar? = null
    internal var btnEngineHybrid: TextView? = null
    internal var btnEngineCam1: TextView? = null
    internal var btnEngineCam2: TextView? = null
    internal var btnResAuto: TextView? = null
    internal var btnRes9_16: TextView? = null
    internal var btnRes1080: TextView? = null
    internal var btnRes720: TextView? = null
    internal var btnResStride: TextView? = null
    internal var btnRes480: TextView? = null
    internal var btnLightweightModeSettings: TextView? = null
    internal var btnOsdSettings: TextView? = null
    internal var btnTargetScopeSettings: TextView? = null
    internal var btnToggleScanRecordTextSettings: TextView? = null
    internal var scanRecordCountBadgeSettings: TextView? = null
    internal var floatingLogCountBadge: TextView? = null
    internal var floatingLogPreviewText: TextView? = null
    internal var btnLogSwitch: TextView? = null
    internal var btnA11yStatusSettingsText: TextView? = null

    // =========================================================================
    // 9B. TAB 7: DEDICATED LOG TAB & OPTIONAL FULL-SCREEN FLOATING LOG VIEWS
    // =========================================================================
    internal var isFloatingLogFullScreen: Boolean = false
    internal var btnToggleFloatingLogFullScreen: TextView? = null
    internal var savedNormalPanelWidth: Int = 0
    internal var savedNormalScrollHeight: Int = 0
    internal var savedNormalPanelX: Int = 0
    internal var savedNormalPanelY: Int = 0
    internal var floatingLogSubMode: String = "SYSTEM"
    internal var btnFloatingLogModeSystem: TextView? = null
    internal var btnFloatingLogModeScan: TextView? = null
    internal var containerFloatingLogSystem: LinearLayout? = null
    internal var containerFloatingLogScan: LinearLayout? = null

    // =========================================================================
    // 10. UNIVERSAL FIXED BOTTOM MOVEMENT DOCK BAR
    // =========================================================================
    internal var fixedBottomDockBar: LinearLayout? = null
    internal var fixedBottomDockTitle: TextView? = null
    internal var btnFixedBottomRotText: TextView? = null
    internal var btnFixedBottomMirrorText: TextView? = null
}
