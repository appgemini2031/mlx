@file:Suppress("DEPRECATION")

package com.tajasb

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import com.tajasb.model.AudioAntiHashIntensity
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix as GlMatrix
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.text.TextUtils
import android.text.TextPaint
import android.util.Log
import android.view.Surface
import com.tajasb.debug.LogLevel
import com.tajasb.debug.VcamDebugLogger
import com.tajasb.model.ScaleMode
import com.tajasb.renderer.DebugOsdRenderer
import com.tajasb.renderer.EglSurfaceManager
import com.tajasb.renderer.GlShaderProgram
import com.tajasb.renderer.OverlayBannerBitmapGenerator
import java.io.File
import java.util.Locale
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * GlVideoRenderer:
 * High-performance OpenGL ES 2.0 Video Renderer that wraps MediaPlayer and renders
 * to any destination Camera Surface (SurfaceView, TextureView, CameraX, ImageReader, etc.)
 *
 * Real-time features:
 * - Anti-Distortion Proportional Scaling: Eliminates squished ("gepeng") output in 0°, 90°, 180°, 270°.
 * - Scaling Modes: CROP (Fill screen proportionally), FIT (Letterbox), STRETCH.
 * - Smooth Matrix Transformations: Instant rotation and horizontal mirror flip.
 * - Fast Video Swapping: seamless setVideoFile without destroying EGL context.
 * - Real-time Audio Mute & Video Pause.
 */
data class OverlayBannerData(
    val senderName: String,
    val content: String,
    val isGift: Boolean = false,
    val bannerType: String = "COMMENT", // "COMMENT" or "BUYER"
    val iconMode: String = "PRAYER",    // "NONE", "PIN", "PRAYER", "SHOPPING"
    val customPrefix: String = "",      // Custom prefix (e.g. "Doa", "Penonton", "Sahabat", or empty)
    val formatMode: String = "PREFIX_AND_COMMENT", // "PREFIX_AND_COMMENT", "PREFIX_NAME_COMMENT", "NAME_AND_COMMENT", "COMMENT_ONLY"
    val durationMs: Long = 4000L,
    val posXpct: Float = 0.50f,        // 0.0 (left) to 1.0 (right) in viewer space (0.50 = center)
    val posYpct: Float = 0.35f,        // 0.0 (top) to 1.0 (bottom) in viewer space (0.35 = upper middle)
    val bannerRotation: Int = 0,       // 0, 90, 180, 270 degrees manual rotation
    val bannerScale: Float = 1.0f,     // 0.6f to 1.6f scale factor
    val isBannerMirrored: Boolean = false, // Horizontal mirror flip for selfie/front camera
    val entryDirection: String = "LEFT", // "LEFT", "RIGHT", "TOP", "BOTTOM", "FADE"
    val capsuleCornerRadius: Int = 18,
    val capsulePaddingH: Int = 8,
    val capsulePaddingV: Int = 3,
    val capsuleAlpha: Float = 1.0f,
    val capsuleOutlineWidth: Int = 0,
    val capsuleOutlineColor: String = "#FFFFFF",
    val capsuleBgColor: String = "#D97724",
    val fontSizeSp: Float = 10.5f,
    val cartIconSizeDp: Float = 13.0f,
    val stikerOutlineWidth: Int = 2,
    val stikerOutlineColor: String = "#FFFFFF",
    val stikerDisplayStyle: String = "WITH_BLOCK",
    val isRunningText: Boolean = false,
    val runningTextSpeed: Float = 1.0f,
    val runningTextDirection: String = "LEFT",
    val startTimeMs: Long = System.currentTimeMillis()
)

class GlVideoRenderer(
    var targetSurface: Surface,
    var videoFile: File,
    initialAudioFile: File? = null,
    initialRotation: Int = 0,
    initialMirror: Boolean = false,
    initialVerticalFlip: Boolean = false,
    initialMuted: Boolean = false,
    initialPaused: Boolean = false,
    initialScaleMode: ScaleMode = ScaleMode.CROP,
    initialVideoAudioMuted: Boolean = false,
    initialMp3AudioPaused: Boolean = false,
    initialPositionMs: Int = 0,
    initialVideoVolume: Float = 1.0f,
    initialLooping: Boolean = true,
    initialFps: Int = 30,
    initialAntiHashMode: com.tajasb.model.AntiHashIntensity = com.tajasb.model.AntiHashIntensity.OFF,
    initialAudioAntiHashEnabled: Boolean = true,
    initialAudioAntiHashMode: com.tajasb.model.AudioAntiHashIntensity = com.tajasb.model.AudioAntiHashIntensity.MEDIUM,
    initialSessionDnaIndex: Int = 1,
    initialSessionDnaSeed: Long = 1001L,
    initialZoom: Float = 1.0f,
    initialSessionDnaEnabled: Boolean = false,
    initialDnaZoomEnabled: Boolean = false,
    initialVideoAntiHashPercent: Float = 1.5f,
    initialSessionDnaZoom: Float = 1.08f,
    initialSessionDnaPanX: Float = 0.0f,
    initialSessionDnaPanY: Float = 0.0f,
    initialLiveStreamUrl: String? = null,
    var onVideoCompleted: (() -> Boolean)? = null,
    var onPrewarmNextRequested: (() -> Unit)? = null,
    var onPlaybackProgress: ((currentPosMs: Long, durationMs: Long, file: File) -> Unit)? = null
) {
    companion object {
        private const val TAG = "TajasbGlRenderer"
        private const val MAX_CONCURRENT_AVATAR_BUBBLES = 64
        private val VERTEX_SHADER get() = GlShaderProgram.VERTEX_SHADER
        private val FRAGMENT_SHADER get() = GlShaderProgram.FRAGMENT_SHADER
        private val OVERLAY_VERTEX_SHADER get() = GlShaderProgram.OVERLAY_VERTEX_SHADER
        private val OVERLAY_FRAGMENT_SHADER get() = GlShaderProgram.OVERLAY_FRAGMENT_SHADER
        private val VERTEX_COORDS get() = GlShaderProgram.VERTEX_COORDS
        private val TEXTURE_COORDS get() = GlShaderProgram.TEXTURE_COORDS
    }

    private val interSemiBoldTypeface by lazy {
        val base = try {
            MainHook.appContext?.let { ctx ->
                androidx.core.content.res.ResourcesCompat.getFont(ctx, com.example.R.font.inter)
            }
        } catch (_: Throwable) {
            null
        } ?: Typeface.create("sans-serif", Typeface.BOLD)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Typeface.create(base, 600, false)
        } else {
            Typeface.create(base, Typeface.BOLD)
        }
    }

    @Volatile var currentRotation: Int = initialRotation
        private set

    @Volatile var isMirrored: Boolean = initialMirror
        private set

    @Volatile var isVerticalFlipped: Boolean = initialVerticalFlip
        private set

    @Volatile var userZoom: Float = initialZoom
        private set

    @Volatile var isPaused: Boolean = initialPaused
        private set

    @Volatile var isVideoAudioMuted: Boolean = initialVideoAudioMuted
        private set

    @Volatile var isMp3AudioPaused: Boolean = initialMp3AudioPaused
        private set

    @Volatile var isMuted: Boolean = initialMuted
        private set

    @Volatile var currentVideoVolume: Float = initialVideoVolume
        private set

    @Volatile private var initialSeekPositionMs: Int = initialPositionMs

    @Volatile var liveStreamUrl: String? = initialLiveStreamUrl
        private set

    @Volatile var scaleMode: ScaleMode = initialScaleMode
        private set

    @Volatile var isRunning: Boolean = false
        private set

    @Volatile private var activeAudioFile: File? = initialAudioFile

    private var handlerThread: HandlerThread? = null
    private var glHandler: Handler? = null
    private var watchdogThread: HandlerThread? = null
    private var watchdogHandler: Handler? = null
    @Volatile private var preparingStartTimeMs: Long = 0L
    @Volatile private var lastPingTimeMs: Long = 0L
    @Volatile private var lastPongTimeMs: Long = 0L
    private var audioPlayer: MediaPlayer? = null
    private val playerReleaseExecutor = java.util.concurrent.Executors.newFixedThreadPool(4)
    @Volatile private var lastDiagnosticLogTimeMs: Long = 0L

    // EGL objects
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    // Query arrays cached to prevent object allocations on every frame
    private val queryWidthArr = IntArray(1)
    private val queryHeightArr = IntArray(1)

    // Surface & Video Dimensions
    @Volatile private var surfaceWidth: Int = 1080
    @Volatile private var surfaceHeight: Int = 1920
    @Volatile var videoWidth: Int = 1280
        private set
    @Volatile var videoHeight: Int = 720
        private set
    @Volatile var lastKnownPositionMs: Int = 0

    // Live Render & FPS Telemetry
    @Volatile var targetFps: Int = initialFps.coerceIn(15, 60)
        private set
    @Volatile var currentFps: Float = 30.0f
        private set
    @Volatile var lastPtsDeltaMs: Long = 33L
        private set
    private var frameCountInSecond: Int = 0
    private var lastFpsCalcTimeMs: Long = System.currentTimeMillis()
    private var lastHeartbeatLogTimeMs: Long = 0L
    @Volatile var totalFramesRendered: Long = 0L

    private var fpsTickerRunnable: Runnable? = null

    fun setTargetFps(fps: Int) {
        val safe = fps.coerceIn(15, 60)
        if (targetFps == safe) return
        targetFps = safe
        avSyncEngine.setTargetFps(safe)
        glHandler?.post {
            updateFpsPacing()
        }
        rLog(LogLevel.INFO, "Target FPS disetel ke: $safe FPS ⚡")
    }

    private fun updateFpsPacing() {
        fpsTickerRunnable?.let { glHandler?.removeCallbacks(it) }
        fpsTickerRunnable = null
        if (targetFps >= 50 && isRunning && !isPaused) {
            val ticker = object : Runnable {
                override fun run() {
                    if (isRunning && !isPaused && targetFps >= 50) {
                        drawFrame(forceRedraw = true)
                        glHandler?.postDelayed(this, 16L)
                    }
                }
            }
            fpsTickerRunnable = ticker
            glHandler?.postDelayed(ticker, 16L)
        }
    }

    private fun bindFrameAvailableListener(st: SurfaceTexture, slot: Int) {
        st.setOnFrameAvailableListener({
            glHandler?.post {
                if (preparingPlayer != null && slot == preparingSlot) {
                    performSeamlessSlotSwitch(forceEvenIfTexNotReady = false)
                } else if (standbyPlayer != null && slot == standbySlot) {
                    try {
                        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
                            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                        }
                        val targetMatrix = if (slot == 0) texMatrixA else texMatrixB
                        // Selalu kuras frame dari SurfaceTexture di slot standby (baik Frame #1 maupun residual frame sebelum pause)
                        // agar antrean BufferQueue tidak pernah macet saat slot diaktifkan!
                        st.updateTexImage()
                        st.getTransformMatrix(targetMatrix)
                        val strideCropV = com.tajasb.engine.BufferQueueManager.getStrideCropFactorV(surfaceWidth, surfaceHeight)
                        if (strideCropV < 1.0f) {
                            GlMatrix.scaleM(targetMatrix, 0, 1.0f, strideCropV, 1.0f)
                        }
                        val wasReady = isStandbyFrameReady
                        isStandbyFrameReady = true
                        isPreloadAckReady = true
                        try {
                            if (standbyPlayer?.isPlaying == true) {
                                standbyPlayer?.pause()
                            }
                        } catch (_: Throwable) {}
                        if (!wasReady) {
                            Log.i(TAG, "⚡ [STANDBY-READY] Frame #1 berhasil didekode di GPU Slot $slot untuk: ${standbyDisplayName ?: standbyFile?.name}")
                        }
                    } catch (t: Throwable) {
                        Log.d(TAG, "Standby slot $slot frame latch error: ${t.message}")
                    }
                } else if (activeSlot == slot) {
                    hasNewFrame = true
                    if (targetFps < 50) {
                        if (!isRenderScheduled) {
                            isRenderScheduled = true
                            isRenderScheduled = false
                            drawFrame()
                        }
                    }
                } else {
                    // Drain any residual buffer on inactive slot so BufferQueue never stalls
                    try { st.updateTexImage() } catch (_: Throwable) {}
                }
            }
        }, glHandler)
    }

    // Playlist & AutoNext metadata for live OSD Telemetry
    @Volatile var currentPlaylistIndex: Int = 0
    @Volatile var totalPlaylistCount: Int = 1
    @Volatile var autoNextModeName: String = "BERURUTAN"
    @Volatile var autoNextLeadMsVal: Int = 1500
    @Volatile var nextPreloadedVideoName: String = ""
    @Volatile var isPreloadAckReady: Boolean = true

    // Video display name for logs and on-screen OSD telemetry
    @Volatile var customDisplayName: String? = null

    val effectiveVideoName: String
        get() {
            val custom = customDisplayName?.trim()
            if (!custom.isNullOrEmpty()) return custom
            val raw = videoFile.name
            val uuidPrefixRegex = Regex("^(video|audio)_[a-fA-F0-9]{8}_")
            return raw.replace(uuidPrefixRegex, "")
        }

    fun updateVideoDisplayName(name: String?) {
        val old = this.customDisplayName
        val newName = name?.trim()
        if (!newName.isNullOrBlank() && newName != old) {
            this.customDisplayName = newName
            rLog(LogLevel.INFO, "🏷️ Nama video diperbarui di hook: \"$newName\" (file: ${videoFile.name})")
            // Invalidate debug OSD so new name renders immediately on screen
            lastRenderedPosSec = -1
        }
    }

    // GL objects - Double-buffered OES Textures for zero-freeze seamless transitions
    private var oesTextureIdA: Int = 0
    private var oesTextureIdB: Int = 0
    @Volatile private var activeSlot: Int = 0 // 0 = Slot A, 1 = Slot B
    private var programId: Int = 0
    private var aPositionLoc: Int = 0
    private var aTextureCoordLoc: Int = 0
    private var uMVPMatrixLoc: Int = 0
    private var uTexMatrixLoc: Int = 0
    private var sTextureLoc: Int = 0
    private var uAntiHashIntensityLoc: Int = -1
    private var uTimeSeedLoc: Int = -1
    private var uColorShiftLoc: Int = -1
    private var uSessionGammaLoc: Int = -1
    private var uSessionContrastLoc: Int = -1
    private var uSessionWarmthLoc: Int = -1
    private var uSessionTintLoc: Int = -1
    private var uDctFreqLoc: Int = -1
    private var uLightDirLoc: Int = -1
    @Volatile var antiHashMode: com.tajasb.model.AntiHashIntensity = initialAntiHashMode
        private set
    @Volatile var videoAntiHashPercent: Float = initialVideoAntiHashPercent.coerceIn(0.0f, 5.0f)
        private set
    @Volatile var audioAntiHashPercent: Float = 2.0f
        private set
    @Volatile var isSessionDnaEnabled: Boolean = initialSessionDnaEnabled
        private set
    @Volatile var isDnaZoomEnabled: Boolean = initialDnaZoomEnabled
        private set
    @Volatile var sessionDnaCustomZoom: Float = initialSessionDnaZoom.coerceIn(1.04f, 2.50f)
        private set
    @Volatile var sessionDnaPanX: Float = initialSessionDnaPanX
        private set
    @Volatile var sessionDnaPanY: Float = initialSessionDnaPanY
        private set
    @Volatile var isAudioAntiHashEnabled: Boolean = initialAudioAntiHashEnabled
        private set
    @Volatile var audioAntiHashMode: com.tajasb.model.AudioAntiHashIntensity = initialAudioAntiHashMode
        private set
    @Volatile var currentSessionDna: com.tajasb.model.SessionDnaProfile =
        com.tajasb.model.SessionDnaProfile.forSession(initialSessionDnaIndex, initialSessionDnaSeed)
        private set
    private var audioDriftStep: Float = ((initialSessionDnaSeed and 0xFFL).toFloat() * 0.13f)
    private var lastAudioDriftUpdateMs: Long = 0L

    // Per-Loop & Per-Video Anti-PL Entropy State (anchored around currentSessionDna ONLY when isSessionDnaEnabled is true)
    @Volatile var antiPlLoopCount: Int = 0
        private set
    @Volatile var loopEntropyScaleBias: Float = if (initialSessionDnaEnabled) (currentSessionDna.baseZoom - 1.0f).coerceAtLeast(0.008f) else 0.008f
        private set
    @Volatile var loopEntropyOffsetX: Float = if (initialSessionDnaEnabled) currentSessionDna.anchorOffsetX else 0.0f
        private set
    @Volatile var loopEntropyOffsetY: Float = if (initialSessionDnaEnabled) currentSessionDna.anchorOffsetY else 0.0f
        private set
    @Volatile var loopEntropyRotationDeg: Float = if (initialSessionDnaEnabled) currentSessionDna.baseTiltDeg else 0.0f
        private set
    @Volatile var loopEntropySpeedBias: Float = 0.004f
        private set
    @Volatile var loopEntropySeedOffset: Float = if (initialSessionDnaEnabled) 19.37f + (currentSessionDna.slotIndex * 41.7f) else 19.37f
        private set
    // Smoothly interpolated active entropy values so video transitions NEVER snap or jerk on slot switch
    private var currentEntropyScaleBias: Float = loopEntropyScaleBias
    private var currentEntropyOffsetX: Float = loopEntropyOffsetX
    private var currentEntropyOffsetY: Float = loopEntropyOffsetY
    private var currentEntropyRotationDeg: Float = loopEntropyRotationDeg
    private var currentEntropySeedOffset: Float = loopEntropySeedOffset
    private var continuousAntiPlWavePhase: Double = if (initialSessionDnaEnabled) currentSessionDna.initialWavePhase else 0.0
    private var lastAntiPlWaveTimeMs: Long = System.currentTimeMillis()
    private var lastAntiPlTrackPosMs: Int = 0
    private var lastAntiPlEntropyRollMs: Long = 0L

    fun setSessionDnaEnabled(enabled: Boolean) {
        if (isSessionDnaEnabled == enabled) return
        isSessionDnaEnabled = enabled
        if (!enabled) {
            loopEntropyScaleBias = 0.008f
            loopEntropyOffsetX = 0.0f
            loopEntropyOffsetY = 0.0f
            loopEntropyRotationDeg = 0.0f
            currentEntropyScaleBias = 0.0f
            currentEntropyOffsetX = 0.0f
            currentEntropyOffsetY = 0.0f
            currentEntropyRotationDeg = 0.0f
        } else {
            rollNextLoopAntiPlEntropy("session_dna_enable")
        }
        mvpNeedsUpdate = true
        applyAudioAntiHashToPlayers()
        rLog(LogLevel.INFO, "🧬 [DNA-SESI] Saklar Mutator DNA Sesi: ${if (enabled) "AKTIF (${currentSessionDna.badgeText})" else "NONAKTIF (OFF)"}")
        glHandler?.post { drawFrame(forceRedraw = true) }
    }

    fun setDnaZoomEnabled(enabled: Boolean) {
        if (isDnaZoomEnabled == enabled) return
        isDnaZoomEnabled = enabled
        mvpNeedsUpdate = true
        rLog(LogLevel.INFO, "🎯 [FRAMING-GUARD] Saklar DNA Zoom: ${if (enabled) "AKTIF (${sessionDnaCustomZoom}x)" else "NONAKTIF (1.00x)"}")
        glHandler?.post { drawFrame(forceRedraw = true) }
    }

    fun setLiveStreamSource(url: String?, forceReload: Boolean = false) {
        val prev = liveStreamUrl
        liveStreamUrl = url
        if (prev != url || forceReload) {
            rLog(LogLevel.INFO, "🕋 [LIVE-RELAY-UPDATE] Live stream URL diperbarui: ${url ?: "Lokal File"} (forceReload=$forceReload)")
            if (url != null) {
                videoWidth = 1280
                videoHeight = 720
                mvpNeedsUpdate = true
            }
            glHandler?.post {
                hotReloadPipeline()
            }
        }
    }

    fun setSessionDna(sessionIndex: Int, sessionSeed: Long, enabled: Boolean = isSessionDnaEnabled) {
        val prevEnabled = isSessionDnaEnabled
        isSessionDnaEnabled = enabled
        val prevDna = currentSessionDna
        val nextDna = com.tajasb.model.SessionDnaProfile.forSession(sessionIndex, sessionSeed)
        currentSessionDna = nextDna
        if (!enabled) {
            loopEntropyScaleBias = 0.008f
            loopEntropyOffsetX = 0.0f
            loopEntropyOffsetY = 0.0f
            loopEntropyRotationDeg = 0.0f
            currentEntropyScaleBias = 0.0f
            currentEntropyOffsetX = 0.0f
            currentEntropyOffsetY = 0.0f
            currentEntropyRotationDeg = 0.0f
        }
        if (prevEnabled != enabled || prevDna.sessionIndex != nextDna.sessionIndex || prevDna.sessionSeed != nextDna.sessionSeed) {
            if (enabled) {
                continuousAntiPlWavePhase = (continuousAntiPlWavePhase + nextDna.initialWavePhase * 0.37) % 100000.0
                audioDriftStep += 2.71f + (nextDna.slotIndex * 0.83f)
                rollNextLoopAntiPlEntropy("session_dna_${nextDna.codeName}")
            }
            mvpNeedsUpdate = true
            applyAudioAntiHashToPlayers()
            rLog(
                LogLevel.INFO,
                "🧬 [DNA-SESI] ${if (enabled) "AKTIF ${nextDna.badgeText} (${nextDna.quadrantLabel})" else "NONAKTIF (OFF)"}"
            )
            glHandler?.post { drawFrame(forceRedraw = true) }
        }
    }

    fun rollNextLoopAntiPlEntropy(reason: String = "loop") {
        val now = System.currentTimeMillis()
        if (now - lastAntiPlEntropyRollMs < 450L && antiPlLoopCount > 0 && !reason.startsWith("session_dna")) return
        lastAntiPlEntropyRollMs = now
        antiPlLoopCount++
        if (!isSessionDnaEnabled) {
            val rnd = java.util.Random(now xor (antiPlLoopCount.toLong() * 0x9E3779B97F4A7C15UL.toLong()) xor videoFile.name.hashCode().toLong())
            loopEntropyScaleBias = 0.008f + rnd.nextFloat() * 0.004f
            loopEntropyOffsetX = 0.0f
            loopEntropyOffsetY = 0.0f
            loopEntropyRotationDeg = 0.0f
            val rawSpeed = (rnd.nextFloat() - 0.5f) * 0.010f
            loopEntropySpeedBias = if (kotlin.math.abs(rawSpeed) < 0.002f) 0.0030f else rawSpeed
            loopEntropySeedOffset += 1.65f + rnd.nextFloat() * 1.45f
            audioDriftStep += 0.35f + rnd.nextFloat() * 0.40f
            mvpNeedsUpdate = true
            applyAudioAntiHashToPlayers()
            return
        }
        val dna = currentSessionDna
        val rnd = java.util.Random(now xor dna.sessionSeed xor (antiPlLoopCount.toLong() * 0x9E3779B97F4A7C15UL.toLong()) xor videoFile.name.hashCode().toLong())
        // Micro-zoom target bias anchored around Session DNA baseZoom (+0.8% to +2.8%)
        val baseZoomBias = (dna.baseZoom - 1.0f).coerceIn(0.008f, 0.026f)
        loopEntropyScaleBias = (baseZoomBias + (rnd.nextFloat() - 0.4f) * 0.007f).coerceIn(0.007f, 0.030f)
        // Micro-pan target X/Y anchored around Session DNA quadrant (DNA-A..DNA-H)
        loopEntropyOffsetX = (dna.anchorOffsetX + (rnd.nextFloat() - 0.5f) * 0.0030f).coerceIn(-0.012f, 0.012f)
        loopEntropyOffsetY = (dna.anchorOffsetY + (rnd.nextFloat() - 0.5f) * 0.0030f).coerceIn(-0.012f, 0.012f)
        // Micro-tilt target anchored around Session DNA baseTiltDeg
        val rawRot = dna.baseTiltDeg + (rnd.nextFloat() - 0.5f) * 0.09f
        loopEntropyRotationDeg = if (kotlin.math.abs(rawRot) < 0.03f) dna.baseTiltDeg else rawRot.coerceIn(-0.45f, 0.45f)
        // Per-loop video/audio tempo micro-variation around Session DNA speedCenterRatio
        val rawSpeed = (rnd.nextFloat() - 0.5f) * 0.014f
        loopEntropySpeedBias = if (kotlin.math.abs(rawSpeed) < 0.002f) 0.0035f else rawSpeed
        // Continuous non-repeating shader grain seed progression (no sudden +37..+90 snap on slot_switch)
        loopEntropySeedOffset += 1.65f + rnd.nextFloat() * 1.45f
        audioDriftStep += 0.35f + rnd.nextFloat() * 0.40f
        mvpNeedsUpdate = true
        applyAudioAntiHashToPlayers()
        Log.d(TAG, "🛡️ [ANTI-PL] Putaran ke-$antiPlLoopCount (${dna.codeName}/$reason) -> targetZoom=+${String.format(Locale.US, "%.2f", loopEntropyScaleBias * 100f)}%, pan=(${String.format(Locale.US, "%.3f", loopEntropyOffsetX)}, ${String.format(Locale.US, "%.3f", loopEntropyOffsetY)}), speedBias=${String.format(Locale.US, "%+.2f", loopEntropySpeedBias * 100f)}%")
    }

    // Decoy Glitch State & Loop-Based Random Hard Freeze (Default: Freeze on 2nd playback)
    @Volatile private var decoyFpsDropUntilMs: Long = 0L
    @Volatile private var decoyTargetFps: Int = 3
    @Volatile private var decoyFreezeUntilMs: Long = 0L
    @Volatile private var decoyDimUntilMs: Long = 0L
    @Volatile private var decoyAudioStutterUntilMs: Long = 0L
    @Volatile private var decoyGpuCorruptUntilMs: Long = 0L
    @Volatile private var decoyFlipZoomUntilMs: Long = 0L
    @Volatile private var decoyFlipAngleOffset: Int = 180
    @Volatile private var decoyZoomFactor: Float = 2.6f
    private var lastDecoyAudioChopMs: Long = 0L
    private var decoyAudioChopMuted: Boolean = false

    @Volatile var isDecoyLoopGateEnabled: Boolean = false
        private set
    @Volatile var decoyMaxLoopRange: Int = 4
        private set
    @Volatile var decoyFreezeTargetLoopCount: Int = 2
        private set
    @Volatile var decoyCurrentLoopNumber: Int = 1
        private set
    @Volatile private var decoyHighestPosInLoopMs: Int = 0
    @Volatile private var decoyLastLoopIncrementTimeMs: Long = 0L
    @Volatile private var decoyRandomFreezeTimestampMs: Int = -1
    @Volatile private var decoyPassEvaluatedForLoop: Int = 0
    @Volatile var isDecoyHardFrozen: Boolean = false
        private set

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(VERTEX_COORDS)
            position(0)
        }

    private val textureBuffer: FloatBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(TEXTURE_COORDS)
            position(0)
        }

    private val mvpMatrix = FloatArray(16)
    private val texMatrixA = FloatArray(16)
    private val texMatrixB = FloatArray(16)

    // Overlay GL objects (Live Doa Comment & Buyer Notification)
    private var overlayProgramId: Int = 0
    private var overlayPositionLoc: Int = 0
    private var overlayTexCoordLoc: Int = 0
    private var overlayTextureLoc: Int = 0
    private var overlayAlphaLoc: Int = 0
    private var overlayMvpMatrixLoc: Int = 0
    private var overlayTextureId: Int = 0
    private val overlayMvpMatrix = FloatArray(16)

    private val overlayVertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val overlayTexCoordsBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(
                0.0f, 1.0f, // bottom-left
                1.0f, 1.0f, // bottom-right
                0.0f, 0.0f, // top-left
                1.0f, 0.0f  // top-right
            ))
            position(0)
        }

    @Volatile private var currentOverlay: OverlayBannerData? = null
    @Volatile private var overlayBitmapNeedsUpload: Boolean = false
    private var overlayBitmap: Bitmap? = null
    private var overlayQuadWidth: Int = 0
    private var overlayQuadHeight: Int = 0

    // Persistent Custom Sticker (STICKER)
    @Volatile private var currentSticker: OverlayBannerData? = null
    @Volatile private var stickerBitmapNeedsUpload: Boolean = false
    private var stickerBitmap: Bitmap? = null
    private var stickerQuadWidth: Int = 0
    private var stickerQuadHeight: Int = 0
    private var stickerTextureId: Int = 0
    private var lastStickerRunningUpdateMs: Long = 0L

    // Persistent Custom Watermark (WATERMARK) - completely independent from STICKER
    @Volatile private var currentWatermark: OverlayBannerData? = null
    @Volatile private var watermarkBitmapNeedsUpload: Boolean = false
    private var watermarkBitmap: Bitmap? = null
    private var watermarkQuadWidth: Int = 0
    private var watermarkQuadHeight: Int = 0
    private var watermarkTextureId: Int = 0
    private var lastWatermarkRunningUpdateMs: Long = 0L

    // Persistent Automatic Time Capsule (AUTO_TIME) - live Date/Month box on left + HH:mm[:ss] on right
    @Volatile private var currentAutoTime: OverlayBannerData? = null
    @Volatile private var autoTimeBitmapNeedsUpload: Boolean = false
    private var autoTimeBitmap: Bitmap? = null
    private var autoTimeQuadWidth: Int = 0
    private var autoTimeQuadHeight: Int = 0
    private var autoTimeTextureId: Int = 0
    private var lastAutoTimeSecondTick: Long = -1L

    // Dedicated Live Event Tap Like Overlay (EVENT_LIKE)
    @Volatile private var currentLikeOverlay: OverlayBannerData? = null
    @Volatile private var likeBitmapNeedsUpload: Boolean = false
    private var likeBitmap: Bitmap? = null
    private var likeQuadWidth: Int = 0
    private var likeQuadHeight: Int = 0
    private var likeTextureId: Int = 0

    // Dedicated Live Event Gift Overlay (EVENT_GIFT)
    @Volatile private var currentGiftOverlay: OverlayBannerData? = null
    @Volatile private var giftBitmapNeedsUpload: Boolean = false
    private var giftBitmap: Bitmap? = null
    private var giftQuadWidth: Int = 0
    private var giftQuadHeight: Int = 0
    private var giftTextureId: Int = 0

    // Dedicated Quran Khatam 30 Juz Overlay (QURAN_KHATAM)
    @Volatile private var currentQuranOverlay: OverlayBannerData? = null
    @Volatile private var quranBitmapNeedsUpload: Boolean = false
    private var quranBitmap: Bitmap? = null
    private var quranQuadWidth: Int = 0
    private var quranQuadHeight: Int = 0
    private var quranTextureId: Int = 0

    // Dedicated Quran Remaining Stats Overlay (QURAN_STATS: Sisa Huruf, Ayat, Surat, Juz)
    @Volatile private var currentQuranStatsOverlay: OverlayBannerData? = null
    @Volatile private var quranStatsBitmapNeedsUpload: Boolean = false
    private var quranStatsBitmap: Bitmap? = null
    private var quranStatsQuadWidth: Int = 0
    private var quranStatsQuadHeight: Int = 0
    private var quranStatsTextureId: Int = 0

    // Dedicated Live Event Analytics Overlay (EVENT_STATS: Unified 3-column Top 3 Coins | Analytics | Quran Progress)
    @Volatile private var currentEventStatsOverlay: OverlayBannerData? = null
    @Volatile private var eventStatsBitmapNeedsUpload: Boolean = false
    private var eventStatsBitmap: Bitmap? = null
    private var eventStatsQuadWidth: Int = 0
    private var eventStatsQuadHeight: Int = 0
    private var eventStatsTextureId: Int = 0

    // Dedicated Dynamic Top 10 Tap-Tap Love Overlay (TOP_LIKERS: Circular Photos + Dynamic Tap Rank)
    @Volatile private var currentTopLikersOverlay: OverlayBannerData? = null
    @Volatile private var topLikersBitmapNeedsUpload: Boolean = false
    private var topLikersBitmap: Bitmap? = null
    private var topLikersQuadWidth: Int = 0
    private var topLikersQuadHeight: Int = 0
    private var topLikersTextureId: Int = 0

    // Dedicated Tug of War / Team Battle Overlay (TUG_OF_WAR)
    @Volatile private var currentTugOfWarOverlay: OverlayBannerData? = null
    @Volatile private var tugOfWarBitmapNeedsUpload: Boolean = false
    private var tugOfWarBitmap: Bitmap? = null
    private var tugOfWarQuadWidth: Int = 0
    private var tugOfWarQuadHeight: Int = 0
    private var tugOfWarTextureId: Int = 0

    // Floating Viewer Avatar Bubbles Pool (AVATAR_BUBBLE: Up to 8 concurrent rising circular photos)
    private class AvatarBubbleSlot(
        var banner: OverlayBannerData,
        var bitmap: Bitmap?,
        var quadWidth: Int,
        var quadHeight: Int,
        var textureId: Int = 0,
        var needsUpload: Boolean = true,
        val phaseSeed: Float
    )
    private val activeAvatarBubbles = mutableListOf<AvatarBubbleSlot>()
    private var avatarBubbleSeedCounter: Int = 0
    private var avatarBubbleTicker: Runnable? = null

    private fun ensureAvatarBubbleAnimationRunning() {
        val handler = glHandler ?: return
        if (avatarBubbleTicker != null) return
        val ticker = object : Runnable {
            override fun run() {
                val h = glHandler
                if (h == null || !isRunning) {
                    avatarBubbleTicker = null
                    return
                }
                if (activeAvatarBubbles.isEmpty()) {
                    avatarBubbleTicker = null
                    return
                }
                drawFrame(forceRedraw = true)
                h.postDelayed(this, 33L)
            }
        }
        avatarBubbleTicker = ticker
        handler.post(ticker)
    }

    private fun stopAvatarBubbleAnimation() {
        val ticker = avatarBubbleTicker
        if (ticker != null) {
            glHandler?.removeCallbacks(ticker)
            avatarBubbleTicker = null
        }
    }

    // Virtual Camera Debug OSD Stamp (Detik & MS live in TikTok/Shopee)
    @Volatile private var _isDebugOsdEnabled: Boolean = false
    val isDebugOsdEnabled: Boolean get() = _isDebugOsdEnabled
    private var debugOsdTextureId: Int = 0
    private var debugOsdBitmap: Bitmap? = null
    private var lastDebugOsdUpdateMs: Long = 0L
    private var lastRenderedPosSec: Int = -1
    private var lastRenderedPosMs100: Int = -1
    private var debugOsdQuadWidth: Int = 0
    private var debugOsdQuadHeight: Int = 0

    private val avSyncEngine = com.tajasb.engine.AvSyncEngine(initialFps = 30)

    @Volatile var isFrontFacingCamera: Boolean = false

    fun setFrontFacing(front: Boolean) {
        if (isFrontFacingCamera != front) {
            isFrontFacingCamera = front
            isMirrored = front
            mvpNeedsUpdate = true
            glHandler?.post {
                drawFrame(forceRedraw = true)
            }
        }
    }

    private var videoSurfaceTextureA: SurfaceTexture? = null
    private var videoSurfaceTextureB: SurfaceTexture? = null
    private var videoSurfaceA: Surface? = null
    private var videoSurfaceB: Surface? = null
    val activeVideoSurface: Surface? get() = if (activeSlot == 0) videoSurfaceA else videoSurfaceB

    @Volatile
    var isLoopingEnabled: Boolean = initialLooping

    fun setLooping(loop: Boolean) {
        isLoopingEnabled = loop
        mediaPlayer?.let { mp ->
            try {
                mp.isLooping = loop
            } catch (_: Throwable) {}
        }
    }
    var mediaPlayer: MediaPlayer? = null
        private set

    @Volatile var isLightweightMode: Boolean = false
        private set
    @Volatile private var isRenderScheduled: Boolean = false
    @Volatile private var lastPresentationTimeNs: Long = 0L
    @Volatile private var mvpNeedsUpdate: Boolean = true

    @Volatile private var lastFrameDrawTime: Long = System.currentTimeMillis()
    @Volatile private var lastRealFrameTime: Long = System.currentTimeMillis()
    @Volatile private var lastRealVideoDecodedTimeMs: Long = System.currentTimeMillis()
    @Volatile private var hasNewFrame: Boolean = false
    @Volatile private var preparingPlayer: MediaPlayer? = null
    @Volatile private var preparingSlot: Int = -1
    @Volatile private var standbyPlayer: MediaPlayer? = null
    @Volatile private var standbySlot: Int = -1
    @Volatile private var standbyFile: File? = null
    @Volatile private var standbyFilePath: String? = null
    @Volatile private var standbyFileLength: Long = -1L
    @Volatile private var standbyDisplayName: String? = null
    @Volatile var isStandbyFrameReady: Boolean = false
        private set

    fun getStandbyFile(): File? = standbyFile

    fun getReadyStandbyFileFor(expectedDisplayName: String?): File? {
        val f = standbyFile ?: return null
        if (!f.exists() || f.length() <= 1024L) return null
        if (!expectedDisplayName.isNullOrBlank() && !standbyDisplayName.isNullOrBlank()) {
            if (standbyDisplayName != expectedDisplayName && f.name != expectedDisplayName) {
                return null
            }
        }
        return f
    }

    @Volatile private var hasTriggeredPrewarmForCurrentClip: Boolean = false
    @Volatile private var activePfd: ParcelFileDescriptor? = null
    @Volatile private var preparingPfd: ParcelFileDescriptor? = null
    @Volatile private var standbyPfd: ParcelFileDescriptor? = null
    @Volatile private var lastSwitchedFilePath: String? = null
    @Volatile private var lastSwitchedFileLength: Long = -1L
    @Volatile private var lastSwitchedFileModified: Long = -1L
    @Volatile private var lastSwitchedDisplayName: String? = null
    @Volatile private var lastSwitchTimeMs: Long = 0L
    @Volatile private var isTransitioningVideo: Boolean = false
    private var consecutiveTexErrors: Int = 0
    private var lastPlayerPos: Int = -1
    private var stuckPositionCount: Int = 0
    private val isCompleting = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var isSwitchingSlot: Boolean = false
    @Volatile private var lastCompletionTriggerTimeMs: Long = 0L

    fun triggerVideoCompletion(alreadyMarkedCompleting: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (preparingPlayer != null || isSwitchingSlot || (!alreadyMarkedCompleting && isTransitioningVideo) || (now - lastSwitchTimeMs < 550L)) {
            isTransitioningVideo = false
            isCompleting.set(false)
            return false
        }
        if (!alreadyMarkedCompleting && (now - lastCompletionTriggerTimeMs < 550L)) {
            isCompleting.set(false)
            return false
        }
        val curPos = try { mediaPlayer?.currentPosition ?: 0 } catch (_: Throwable) { 0 }
        val dur = try { mediaPlayer?.duration ?: 0 } catch (_: Throwable) { 0 }
        if (dur > 1500 && curPos in 1..449) {
            Log.d(TAG, "triggerVideoCompletion diabaikan: video baru saja mulai (${curPos}ms/${dur}ms)")
            isTransitioningVideo = false
            isCompleting.set(false)
            return false
        }
        lastCompletionTriggerTimeMs = now
        isCompleting.set(true)
        val handled = try {
            onVideoCompleted?.invoke() ?: false
        } catch (t: Throwable) {
            Log.w(TAG, "onVideoCompleted error: ${t.message}")
            false
        }
        if (!handled) {
            isTransitioningVideo = false
            isCompleting.set(false)
        } else {
            glHandler?.postDelayed({
                if (isCompleting.get() && preparingPlayer == null && !isSwitchingSlot && !isTransitioningVideo) {
                    isCompleting.set(false)
                    val mp = mediaPlayer
                    if (mp != null && !isPaused && !isDecoyHardFrozen) {
                        val playing = try { mp.isPlaying } catch (_: Throwable) { false }
                        if (!playing) {
                            try {
                                mp.seekTo(0)
                                mp.start()
                                applyAudioAntiHashToPlayer(mp)
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }, 1800L)
        }
        return handled
    }
    private var watchdogRunnable: Runnable? = null
    private var heartbeatRunnable: Runnable? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        lastFrameDrawTime = System.currentTimeMillis()
        lastRealFrameTime = System.currentTimeMillis()
        hasNewFrame = false
        consecutiveTexErrors = 0
        stuckPositionCount = 0

        val thread = HandlerThread("VcamGlThread_${System.currentTimeMillis()}", Process.THREAD_PRIORITY_URGENT_DISPLAY)
        thread.start()
        handlerThread = thread
        val handler = Handler(thread.looper)
        glHandler = handler

        // Independent Watchdog Thread so watchdog NEVER freezes even if glHandler is temporarily waiting
        val wThread = HandlerThread("VcamWatchdog_${System.currentTimeMillis()}", Process.THREAD_PRIORITY_BACKGROUND)
        wThread.start()
        watchdogThread = wThread
        val wHandler = Handler(wThread.looper)
        watchdogHandler = wHandler

        wHandler.post {
            startWatchdog()
        }

        handler.post {
            try {
                initEgl()
                initGl()
                setupMediaPlayer()
                setupAudioPlayer()
                startHeartbeat()
                updateFpsPacing()
                Log.i(TAG, "GlVideoRenderer started successfully for $effectiveVideoName")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start GlVideoRenderer: ${t.message}", t)
                releaseGl()
            }
        }
    }

    /**
     * Smooth Render Heartbeat:
     * Guarantees TikTok and target camera preview NEVER starve from missing frames,
     * maintaining a continuous ~30 FPS feed to the target surface even across video transitions,
     * decoder hiccups, or loop boundaries.
     */
    private fun startHeartbeat() {
        val hb = object : Runnable {
            override fun run() {
                if (!isRunning) return
                try {
                    val now = System.currentTimeMillis()
                    if (!isPaused && targetSurface.isValid) {
                        if (eglSurface == EGL14.EGL_NO_SURFACE) {
                            recreateEglSurface()
                        }
                        // Anti-flicker single-paced frame pacing:
                        // Guarantees continuous ~30 FPS stream to target surface without spoofing hasNewFrame
                        val mp = mediaPlayer
                        if (hasNewFrame || (now - lastFrameDrawTime >= 26L)) {
                            drawFrame(forceRedraw = true)
                        }

                        // Zero-gap frame boundary watcher for playlist AutoNext mode:
                        // For single looping videos (isLoopingEnabled=true), MediaPlayer loops natively to the very last frame.
                        if (mp != null && mp.isPlaying && !isPaused) {
                            val curPos = try { mp.currentPosition } catch (_: Throwable) { -1 }
                            val dur = try { mp.duration } catch (_: Throwable) { -1 }
                            if (dur > 500 && curPos >= 0) {
                                val prewarmThresholdMs = if (dur > 3200) (dur - 2200) else (dur * 35 / 100).coerceAtLeast(200)
                                if (!hasTriggeredPrewarmForCurrentClip && curPos >= prewarmThresholdMs) {
                                    hasTriggeredPrewarmForCurrentClip = true
                                    playerReleaseExecutor.execute {
                                        try {
                                            onPrewarmNextRequested?.invoke()
                                        } catch (_: Throwable) {}
                                    }
                                }
                            }
                            if (isStandbyFrameReady && standbyFile != null && dur > 800 && curPos >= (dur - 210) && curPos > 350) {
                                val hasQueuedVideo = try { com.tajasb.MainHook.livePendingVideoPath != null } catch (_: Throwable) { false }
                                if (!isLoopingEnabled || hasQueuedVideo) {
                                    if (preparingPlayer == null && !isSwitchingSlot && !isTransitioningVideo && isCompleting.compareAndSet(false, true)) {
                                        lastCompletionTriggerTimeMs = now
                                        isTransitioningVideo = true
                                        playerReleaseExecutor.execute {
                                            try {
                                                val switched = triggerVideoCompletion(alreadyMarkedCompleting = true)
                                                if (!switched) {
                                                    isTransitioningVideo = false
                                                    isCompleting.set(false)
                                                }
                                            } catch (_: Throwable) {
                                                isTransitioningVideo = false
                                                isCompleting.set(false)
                                            }
                                        }
                                    }
                                }
                            }

                            // Detect native single-video loop wrap-around to roll fresh Anti-PL entropy on every loop
                            if (curPos >= 0) {
                                if (lastAntiPlTrackPosMs > 1400 && curPos < 800 && curPos < (lastAntiPlTrackPosMs - 900)) {
                                    rollNextLoopAntiPlEntropy("native_loop_wrap")
                                }
                                if (curPos > lastAntiPlTrackPosMs || curPos < 400) {
                                    lastAntiPlTrackPosMs = curPos
                                }
                            }

                            // Acoustic & Temporal Frame Pacing Disruptor: aperiodic tempo & pitch drift
                            if (now - lastAudioDriftUpdateMs > 2200L) {
                                lastAudioDriftUpdateMs = now
                                if ((isAudioAntiHashEnabled || antiHashMode != com.tajasb.model.AntiHashIntensity.OFF) && !isPaused) {
                                    audioDriftStep += 0.418f
                                    applyAudioAntiHashToPlayer(mp)
                                    applyAudioAntiHashToPlayer(audioPlayer)
                                }
                            }

                            // Gerbang Decoy Freeze Dinamis (Acak Putaran 1..N + Peluang Lolos Putaran + Persentase Acak 5%..95%)
                            if (isDecoyLoopGateEnabled && !isDecoyHardFrozen && curPos >= 0) {
                                if (
                                    decoyHighestPosInLoopMs > 1200 &&
                                    curPos < 850 &&
                                    curPos < (decoyHighestPosInLoopMs - 900) &&
                                    (now - decoyLastLoopIncrementTimeMs > 1400L)
                                ) {
                                    onDecoyVideoLoopAdvanced("loop_wrap", dur)
                                }
                                if (curPos > decoyHighestPosInLoopMs) {
                                    decoyHighestPosInLoopMs = curPos
                                }
                                if (decoyCurrentLoopNumber >= decoyFreezeTargetLoopCount) {
                                    if (decoyRandomFreezeTimestampMs < 0 && dur > 400) {
                                        evaluateLoopPassOrScheduleFreeze(dur)
                                    }
                                    if (decoyRandomFreezeTimestampMs > 0 && curPos >= decoyRandomFreezeTimestampMs) {
                                        triggerPermanentLoopFreeze()
                                    }
                                }
                            }
                        }
                    } else if (!targetSurface.isValid) {
                        try {
                            mediaPlayer?.setVolume(0f, 0f)
                            audioPlayer?.setVolume(0f, 0f)
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
                val delayMs = if (isLightweightMode) 31L else 28L
                glHandler?.postDelayed(this, delayMs)
            }
        }
        heartbeatRunnable = hb
        glHandler?.postDelayed(hb, 28L)
    }

    private fun startWatchdog() {
        val watchdog = object : Runnable {
            override fun run() {
                if (!isRunning) return
                try {
                    val now = System.currentTimeMillis()
                    val realFrameDiff = now - lastRealFrameTime
                    val timeSinceDraw = now - lastFrameDrawTime
                    val mp = mediaPlayer

                    // Ping glHandler to check thread responsiveness
                    lastPingTimeMs = now
                    glHandler?.post {
                        lastPongTimeMs = System.currentTimeMillis()
                    }

                    // Check if preparingPlayer is stuck waiting for first frame (allow 2500ms for large MB videos)
                    val prep = preparingPlayer
                    if (prep != null && preparingStartTimeMs > 0L && (now - preparingStartTimeMs > 2500L)) {
                        rLog(LogLevel.WARN, "⚠️ [SWITCH-TIMEOUT] Transisi ke $effectiveVideoName melebihi 2500ms. Memaksa slot switch...")
                        glHandler?.post { performSeamlessSlotSwitch(forceEvenIfTexNotReady = true) }
                    }

                    // Safety reset isCompleting only when no transition is actively preparing/switching (allow 12s for large MB file copy)
                    if (isCompleting.get() && preparingPlayer == null && !isSwitchingSlot && !isTransitioningVideo && (now - lastCompletionTriggerTimeMs > 12000L)) {
                        isCompleting.set(false)
                    }

                    val isWithinSwitchGracePeriod = isTransitioningVideo ||
                        preparingPlayer != null ||
                        isSwitchingSlot ||
                        (now - lastSwitchTimeMs < 3500L)

                    val isLiveRelay = !liveStreamUrl.isNullOrBlank()

                    val isWithinDecoderFreezeGrace = isTransitioningVideo ||
                        preparingPlayer != null ||
                        isSwitchingSlot ||
                        isLiveRelay ||
                        (now - lastSwitchTimeMs < 1100L)

                    var isPositionStuck = false
                    val timeSinceVideoDecoded = now - lastRealVideoDecodedTimeMs
                    if (mp != null && !isPaused && isRunning) {
                        try {
                            if (mp.isPlaying) {
                                val currentPos = mp.currentPosition
                                val duration = try { mp.duration } catch (_: Throwable) { 0 }
                                onPlaybackProgress?.invoke(currentPos.toLong(), duration.toLong(), videoFile)
                                if (isWithinSwitchGracePeriod || isLiveRelay) {
                                    lastPlayerPos = currentPos
                                    stuckPositionCount = 0
                                } else if (currentPos == lastPlayerPos && currentPos > 0) {
                                    stuckPositionCount++
                                    if (stuckPositionCount >= 7) { // ~4.2s stuck after grace period
                                        isPositionStuck = true
                                    }
                                } else {
                                    lastPlayerPos = currentPos
                                    stuckPositionCount = 0
                                }
                            } else if (!isPaused && !isWithinSwitchGracePeriod && !isLiveRelay) {
                                stuckPositionCount++
                                if (stuckPositionCount >= 7) {
                                    isPositionStuck = true
                                }
                            }
                        } catch (_: Throwable) {}
                    }

                    // Detektor Desinkronisasi Decoder: Audio berjalan lancar tapi video freeze/tidak ada frame baru dari decoder!
                    val isAudioPlayingVideoFrozen = (mp != null && mp.isPlaying && !isPaused && !isWithinDecoderFreezeGrace && !isLiveRelay && timeSinceVideoDecoded > 2200L)
                    if (isAudioPlayingVideoFrozen && isRunning && targetSurface.isValid && !isDecoyHardFrozen) {
                        if (timeSinceVideoDecoded in 2201L..3500L && (now - lastSoftRecoverAttemptTime > 1500L)) {
                            lastSoftRecoverAttemptTime = now
                            glHandler?.post {
                                try {
                                    val st = if (activeSlot == 0) videoSurfaceTextureA else videoSurfaceTextureB
                                    st?.updateTexImage()
                                    val curPos = mp.currentPosition
                                    if (curPos > 150 && !isLiveRelay) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            mp.seekTo(curPos.toLong(), MediaPlayer.SEEK_CLOSEST)
                                        } else {
                                            mp.seekTo(curPos)
                                        }
                                    }
                                    mp.start()
                                    hasNewFrame = true
                                    drawFrame(forceRedraw = true)
                                } catch (_: Throwable) {}
                            }
                        } else if (timeSinceVideoDecoded > 3500L) {
                            rLog(LogLevel.WARN, "🔄 Memulihkan sinkronisasi decoder video pada $effectiveVideoName...")
                            lastRealVideoDecodedTimeMs = now
                            lastRealFrameTime = now
                            lastSwitchTimeMs = now
                            glHandler?.post { hotReloadPipeline() }
                        }
                    }

                    // Saat Decoy Hard Freeze aktif: jangan biarkan Watchdog melakukan auto-recovery/unfreeze!
                    if (isDecoyHardFrozen) {
                        lastPongTimeMs = now
                        lastRealFrameTime = now
                        lastFrameDrawTime = now
                        lastRealVideoDecodedTimeMs = now
                        stuckPositionCount = 0
                        watchdogHandler?.postDelayed(this, 1000L)
                        return
                    }

                    // If glHandler thread is totally frozen (> 5000ms unresponsive ping)
                    if (lastPongTimeMs > 0L && (now - lastPongTimeMs > 5000L) && isRunning && targetSurface.isValid && !isPaused) {
                        rLog(LogLevel.ERROR, "🚨 [WATCHDOG] GL Handler Thread macet total (${now - lastPongTimeMs}ms tanpa respon). Memaksa restart thread...")
                        forceRestartGlThread()
                        watchdogHandler?.postDelayed(this, 1500L)
                        return
                    }

                    // Self-healing: Run recovery if player is genuinely stuck or frames stopped drawing (never during switch grace period!)
                    val isReallyStuck = !isWithinSwitchGracePeriod && (
                        isPositionStuck ||
                        isAudioPlayingVideoFrozen ||
                        (mp != null && !mp.isPlaying && !isPaused && realFrameDiff > 5500L) ||
                        (timeSinceDraw > 4500L && !isPaused)
                    )
                    if (!isPaused && isRunning && targetSurface.isValid && isReallyStuck) {
                        recoverFromStall(realFrameDiff.coerceAtLeast(timeSinceDraw), isPositionStuck)
                    }

                    // Periodic Full System Diagnostic Log (every ~10 seconds)
                    if (now - lastDiagnosticLogTimeMs >= 10000L && isRunning && targetSurface.isValid && !isPaused) {
                        lastDiagnosticLogTimeMs = now
                        val diagLog = com.tajasb.engine.SystemDiagnosticHelper.formatDiagnosticLog(
                            MainHook.appContext,
                            videoFile,
                            currentFps,
                            isDecoderStalled = false
                        )
                        rLog(LogLevel.INFO, diagLog)
                    }

                    // Periodic Diagnostic Heartbeat (every ~4 seconds)
                    if (now - lastHeartbeatLogTimeMs >= 4000L && isRunning) {
                        lastHeartbeatLogTimeMs = now
                        if (targetSurface.isValid && !isPaused) {
                            val curSec = String.format(java.util.Locale.US, "%.1f", (lastPlayerPos / 1000f))
                            val durSec = String.format(java.util.Locale.US, "%.1f", ((mp?.duration ?: 0) / 1000f))
                            val fpsStr = String.format(java.util.Locale.US, "%.1f", currentFps)
                            val glAlive = handlerThread?.isAlive == true
                            rLog(LogLevel.DEBUG, "💓 [HEARTBEAT] OpenGL: $fpsStr FPS | Frame: #$totalFramesRendered | Pos: ${curSec}s/${durSec}s | Surface: ${surfaceWidth}x$surfaceHeight | Video: $effectiveVideoName | Slot: $activeSlot | Thread: $glAlive")
                        } else if (!targetSurface.isValid) {
                            rLog(LogLevel.WARN, "⚠️ [HEARTBEAT] Target surface Camera2 tidak valid (Surface destroyed). Menunggu surface aktif baru...")
                            MainHook.notifyStreamStalled((MainHook.appContext?.packageName ?: ""), "surface_destroyed", 3000L)
                        }
                    }
                    watchdogHandler?.postDelayed(this, 600L)
                } catch (_: Throwable) {
                    watchdogHandler?.postDelayed(this, 600L)
                }
            }
        }
        watchdogRunnable = watchdog
        watchdogHandler?.postDelayed(watchdog, 600L)
    }

    private var lastSoftRecoverAttemptTime = 0L

    private fun rLog(level: LogLevel, msg: String, err: Throwable? = null) {
        try {
            VcamDebugLogger.log(level, TAG, msg, err, MainHook.appContext)
        } catch (_: Throwable) {}
    }

    private fun recoverFromStall(stalledDurationMs: Long, positionStuck: Boolean = false) {
        if (!isRunning || isPaused || !targetSurface.isValid) return
        if (isTransitioningVideo || preparingPlayer != null || isSwitchingSlot) return
        val now = System.currentTimeMillis()
        if (now - lastSwitchTimeMs < 5500L) return
        val diag = com.tajasb.engine.SystemDiagnosticHelper.analyzeRootCause(MainHook.appContext, videoFile, currentFps, isDecoderStalled = true)
        rLog(LogLevel.WARN, "⚠️ Watchdog mendeteksi frame stall (${stalledDurationMs}ms, posStuck=$positionStuck) pada $effectiveVideoName. Analisis: [${diag.overallVerdict}]")
        rLog(LogLevel.INFO, "💡 Saran Diagnostik: ${diag.adviceText}")

        // 1. Soft restart first (zero black screen, no pipeline reload, use SEEK_CLOSEST so it never jumps back to 0:00)
        val mp = mediaPlayer
        if (mp != null && (now - lastSoftRecoverAttemptTime > 3500L)) {
            lastSoftRecoverAttemptTime = now
            try {
                if (!mp.isPlaying) {
                    mp.start()
                    rLog(LogLevel.INFO, "ℹ️ Soft player start berhasil dicoba...")
                } else if (positionStuck) {
                    val curPos = try { mp.currentPosition } catch (_: Throwable) { 0 }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mp.seekTo(curPos.toLong(), MediaPlayer.SEEK_CLOSEST)
                    } else {
                        mp.seekTo(curPos)
                    }
                    mp.start()
                    rLog(LogLevel.INFO, "ℹ️ Soft seek SEEK_CLOSEST dicoba pada ${curPos}ms...")
                }
                lastRealFrameTime = now
                stuckPositionCount = 0
                return
            } catch (t: Throwable) {
                rLog(LogLevel.WARN, "Soft player restart gagal: ${t.message}", t)
            }
        }

        // 2. Execute Hot-Reload if stall persists (> 6500ms)
        if (stalledDurationMs > 6500L) {
            rLog(LogLevel.WARN, "🔄 Menjalankan Media pipeline Hot-Reload untuk membuka freeze...")
            lastRealFrameTime = System.currentTimeMillis()
            stuckPositionCount = 0
            MainHook.notifyStreamStalled((MainHook.appContext?.packageName ?: ""), if (positionStuck) "position_stuck" else "frame_stall", stalledDurationMs)
            hotReloadPipeline()
        }
    }

    fun forceRestartGlThread() {
        rLog(LogLevel.WARN, "⚡ [WATCHDOG] Memulai ulang GL Handler Thread...")
        try {
            val oldThread = handlerThread
            handlerThread = null
            glHandler = null
            oldThread?.interrupt()
            Thread {
                try { oldThread?.quitSafely() } catch (_: Throwable) {}
            }.start()

            val thread = HandlerThread("VcamGlThread_${System.currentTimeMillis()}", Process.THREAD_PRIORITY_URGENT_DISPLAY)
            thread.start()
            handlerThread = thread
            val handler = Handler(thread.looper)
            glHandler = handler

            lastPongTimeMs = System.currentTimeMillis()
            handler.post {
                try {
                    initEgl()
                    initGl()
                    setupMediaPlayer()
                    setupAudioPlayer()
                    startHeartbeat()
                    updateFpsPacing()
                    rLog(LogLevel.INFO, "✅ [WATCHDOG] GL Handler Thread berhasil pulih dan aktif normal kembali!")
                } catch (t: Throwable) {
                    rLog(LogLevel.ERROR, "Gagal memulihkan GL Handler Thread: ${t.message}", t)
                }
            }
        } catch (t: Throwable) {
            rLog(LogLevel.ERROR, "forceRestartGlThread error: ${t.message}", t)
        }
    }

    /**
     * Completely re-initializes SurfaceTexture and MediaPlayer without losing the EGL context.
     * Solves Android MediaCodec queue deadlock and Surface producer detachment.
     */
    private fun hotReloadPipeline() {
        if (!isRunning) return
        val handler = glHandler ?: return
        if (Looper.myLooper() != handler.looper) {
            handler.post { hotReloadPipeline() }
            return
        }

        try {
            val savedPos = try { mediaPlayer?.currentPosition ?: 0 } catch (_: Throwable) { 0 }
            if (savedPos > 0 && liveStreamUrl.isNullOrBlank()) {
                initialSeekPositionMs = savedPos
            } else {
                initialSeekPositionMs = 0
            }

            clearStandbyPlayer()
            val prevPrep = preparingPlayer
            preparingPlayer = null
            preparingSlot = -1
            preparingStartTimeMs = 0L
            if (prevPrep != null) {
                releasePlayerAsync(prevPrep)
            }

            val oldMp = mediaPlayer
            mediaPlayer = null
            if (oldMp != null) {
                releasePlayerAsync(oldMp)
            }

            try { videoSurfaceTextureA?.setOnFrameAvailableListener(null) } catch (_: Throwable) {}
            try { videoSurfaceA?.release() } catch (_: Throwable) {}
            videoSurfaceA = null
            try { videoSurfaceTextureA?.release() } catch (_: Throwable) {}
            videoSurfaceTextureA = null

            try { videoSurfaceTextureB?.setOnFrameAvailableListener(null) } catch (_: Throwable) {}
            try { videoSurfaceB?.release() } catch (_: Throwable) {}
            videoSurfaceB = null
            try { videoSurfaceTextureB?.release() } catch (_: Throwable) {}
            videoSurfaceTextureB = null

            // Regenerate dual OES texture IDs
            if (oesTextureIdA != 0 || oesTextureIdB != 0) {
                GLES20.glDeleteTextures(2, intArrayOf(oesTextureIdA, oesTextureIdB), 0)
                oesTextureIdA = 0
                oesTextureIdB = 0
            }
            val textures = IntArray(2)
            GLES20.glGenTextures(2, textures, 0)
            oesTextureIdA = textures[0]
            oesTextureIdB = textures[1]

            for (texId in intArrayOf(oesTextureIdA, oesTextureIdB)) {
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
                GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
                GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            }

            val stA = SurfaceTexture(oesTextureIdA)
            videoSurfaceTextureA = stA
            videoSurfaceA = Surface(stA)
            bindFrameAvailableListener(stA, 0)

            val stB = SurfaceTexture(oesTextureIdB)
            videoSurfaceTextureB = stB
            videoSurfaceB = Surface(stB)
            bindFrameAvailableListener(stB, 1)

            activeSlot = 0

            createAndStartPlayer(videoFile)
            val reloadNow = System.currentTimeMillis()
            lastFrameDrawTime = reloadNow
            lastRealFrameTime = reloadNow
            lastRealVideoDecodedTimeMs = reloadNow
            lastSwitchTimeMs = reloadNow
            consecutiveTexErrors = 0
            stuckPositionCount = 0
            Log.i(TAG, "Media pipeline successfully hot-reloaded and running")
        } catch (t: Throwable) {
            Log.e(TAG, "hotReloadPipeline error: ${t.message}", t)
        }
    }

    private fun initEgl() {
        val res = EglSurfaceManager.initEgl(targetSurface, surfaceWidth, surfaceHeight)
        eglDisplay = res.eglDisplay
        eglContext = res.eglContext
        eglSurface = res.eglSurface
        surfaceWidth = res.surfaceWidth
        surfaceHeight = res.surfaceHeight
    }

    private fun initGl() {
        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        oesTextureIdA = textures[0]
        oesTextureIdB = textures[1]

        for (texId in intArrayOf(oesTextureIdA, oesTextureIdB)) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(programId, "aTextureCoord")
        uMVPMatrixLoc = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        uTexMatrixLoc = GLES20.glGetUniformLocation(programId, "uTexMatrix")
        sTextureLoc = GLES20.glGetUniformLocation(programId, "sTexture")
        uAntiHashIntensityLoc = GLES20.glGetUniformLocation(programId, "uAntiHashIntensity")
        uTimeSeedLoc = GLES20.glGetUniformLocation(programId, "uTimeSeed")
        uColorShiftLoc = GLES20.glGetUniformLocation(programId, "uColorShift")
        uSessionGammaLoc = GLES20.glGetUniformLocation(programId, "uSessionGamma")
        uSessionContrastLoc = GLES20.glGetUniformLocation(programId, "uSessionContrast")
        uSessionWarmthLoc = GLES20.glGetUniformLocation(programId, "uSessionWarmth")
        uSessionTintLoc = GLES20.glGetUniformLocation(programId, "uSessionTint")
        uDctFreqLoc = GLES20.glGetUniformLocation(programId, "uDctFreq")
        uLightDirLoc = GLES20.glGetUniformLocation(programId, "uLightDir")

        // Initialize Overlay Shader Program (Virtual Camera Live Comment / Doa / Buyer Pill)
        overlayProgramId = createProgram(OVERLAY_VERTEX_SHADER, OVERLAY_FRAGMENT_SHADER)
        overlayPositionLoc = GLES20.glGetAttribLocation(overlayProgramId, "aPosition")
        overlayTexCoordLoc = GLES20.glGetAttribLocation(overlayProgramId, "aTextureCoord")
        overlayTextureLoc = GLES20.glGetUniformLocation(overlayProgramId, "uOverlayTexture")
        overlayAlphaLoc = GLES20.glGetUniformLocation(overlayProgramId, "uAlpha")
        overlayMvpMatrixLoc = GLES20.glGetUniformLocation(overlayProgramId, "uOverlayMVPMatrix")

        GlMatrix.setIdentityM(texMatrixA, 0)
        GlMatrix.setIdentityM(texMatrixB, 0)

        // Persistent Dual SurfaceTextures and Surfaces created once in GL context
        val stA = SurfaceTexture(oesTextureIdA)
        bindFrameAvailableListener(stA, 0)
        videoSurfaceTextureA = stA
        videoSurfaceA = Surface(stA)

        val stB = SurfaceTexture(oesTextureIdB)
        bindFrameAvailableListener(stB, 1)
        videoSurfaceTextureB = stB
        videoSurfaceB = Surface(stB)

        activeSlot = 0
    }

    fun clearStandbyPlayer() {
        val sb = standbyPlayer
        standbyPlayer = null
        standbySlot = -1
        standbyFile = null
        standbyFilePath = null
        standbyFileLength = -1L
        standbyDisplayName = null
        isStandbyFrameReady = false
        standbyPfd?.let { try { it.close() } catch (_: Throwable) {} }
        standbyPfd = null
        if (sb != null && sb != mediaPlayer && sb != preparingPlayer) {
            try { sb.setOnPreparedListener(null) } catch (_: Throwable) {}
            try { sb.setOnErrorListener(null) } catch (_: Throwable) {}
            try { sb.setOnCompletionListener(null) } catch (_: Throwable) {}
            try { sb.setSurface(null) } catch (_: Throwable) {}
            releasePlayerAsync(sb)
        }
    }

    /**
     * Zero-Contention Disk Pre-Staging:
     * Caches and validates the upcoming video file on disk without opening a second
     * hardware MediaCodec decoder in start()->pause() mode (which locks up MediaTek/Infinix
     * BufferQueue & hardware codec instances). When the active clip reaches (dur - 210ms),
     * createAndStartPlayer() decodes into a virgin inactive SurfaceTexture and switches seamlessly.
     */
    fun preloadStandbyVideo(nextFile: File, displayName: String? = null) {
        if (!nextFile.exists() || nextFile.length() == 0L) return
        val nextLen = nextFile.length()
        val nextPath = nextFile.absolutePath
        standbyFile = nextFile
        standbyFilePath = nextPath
        standbyFileLength = nextLen
        val effName = if (!displayName.isNullOrBlank()) displayName else nextFile.name
        standbyDisplayName = effName
        nextPreloadedVideoName = effName
        isStandbyFrameReady = true
        isPreloadAckReady = true
    }

    private fun activateStandbySlotNow(newFile: File, displayName: String?): Boolean {
        val sb = standbyPlayer ?: return false
        val targetSlot = standbySlot
        if (targetSlot < 0 || targetSlot == activeSlot) return false
        val sbFile = standbyFile ?: return false

        val isMatchingFile = try {
            sbFile.canonicalPath == newFile.canonicalPath
        } catch (_: Throwable) {
            sbFile.absolutePath == newFile.absolutePath
        } || (sbFile.length() == newFile.length() && sbFile.length() > 1024L && sbFile.name == newFile.name)

        if (!isMatchingFile) {
            return false
        }

        if (!isStandbyFrameReady) {
            preparingPlayer = sb
            preparingSlot = targetSlot
            preparingStartTimeMs = System.currentTimeMillis()
            standbyPlayer = null
            standbySlot = -1
            standbyFile = null
            standbyFilePath = null
            standbyFileLength = -1L
            standbyDisplayName = null
            attachActivePlayerListeners(sb, newFile)
            try {
                if (!isPaused && !sb.isPlaying) {
                    sb.start()
                }
            } catch (_: Throwable) {}
            glHandler?.postDelayed({
                if (preparingPlayer == sb && preparingSlot == targetSlot) {
                    performSeamlessSlotSwitch(forceEvenIfTexNotReady = true)
                }
            }, 2500L)
            Log.i(TAG, "⚡ [STANDBY-PROMOTE] Standby player di Slot $targetSlot dipromosikan ke preparingPlayer untuk: ${displayName ?: newFile.name}")
            return true
        }

        isSwitchingSlot = true
        try {
            val old = mediaPlayer
            try { old?.setOnCompletionListener(null) } catch (_: Throwable) {}
            try { old?.setOnErrorListener(null) } catch (_: Throwable) {}
            try { old?.setVolume(0f, 0f) } catch (_: Throwable) {}
            try { old?.setSurface(null) } catch (_: Throwable) {}

            standbyPlayer = null
            standbySlot = -1
            standbyFile = null
            standbyFilePath = null
            standbyFileLength = -1L
            standbyDisplayName = null
            isStandbyFrameReady = false

            if (targetSlot == 0) {
                activePfd?.let { try { it.close() } catch (_: Throwable) {} }
                activePfd = standbyPfd
                standbyPfd = null
            } else {
                preparingPfd?.let { try { it.close() } catch (_: Throwable) {} }
                preparingPfd = standbyPfd
                standbyPfd = null
            }

            attachActivePlayerListeners(sb, newFile)
            activeSlot = targetSlot
            mediaPlayer = sb
            hasTriggeredPrewarmForCurrentClip = false

            if (sb.videoWidth > 0 && sb.videoHeight > 0) {
                this.videoWidth = sb.videoWidth
                this.videoHeight = sb.videoHeight
                mvpNeedsUpdate = true
            }
            sb.isLooping = isLoopingEnabled
            val currentVol = if (isMuted || isVideoAudioMuted) 0f else currentVideoVolume
            sb.setVolume(currentVol, currentVol)
            lastAntiPlTrackPosMs = 0
            rollNextLoopAntiPlEntropy("slot_switch")

            if (!isPaused) {
                try { sb.start() } catch (_: Throwable) {}
                glHandler?.postDelayed({
                    if (mediaPlayer == sb && !isSwitchingSlot && !isTransitioningVideo) {
                        applyAudioAntiHashToPlayer(sb)
                    }
                }, 300L)
            }

            lastPlayerPos = 0
            stuckPositionCount = 0
            isTransitioningVideo = false
            isCompleting.set(false)
            val switchDoneNow = System.currentTimeMillis()
            lastSwitchTimeMs = switchDoneNow
            lastFrameDrawTime = switchDoneNow
            lastRealFrameTime = switchDoneNow
            lastRealVideoDecodedTimeMs = switchDoneNow
            hasNewFrame = true
            drawFrame(forceRedraw = true)

            if (old != null && old != sb) {
                releasePlayerAsync(old)
            }
            rLog(LogLevel.INFO, "⚡ [INSTANT-SLOT-SWITCH] Transisi <1ms ke Slot $activeSlot: $effectiveVideoName (Zero Freeze)")
            Log.i(TAG, "⚡ Instant <1ms standby slot switch to slot $activeSlot for $effectiveVideoName")

            glHandler?.postDelayed({
                if (isRunning && !isPaused) {
                    playerReleaseExecutor.execute {
                        try { onPrewarmNextRequested?.invoke() } catch (_: Throwable) {}
                    }
                }
            }, 350L)
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "activateStandbySlotNow gagal, fallback ke normal prepare: ${t.message}")
            return false
        } finally {
            isSwitchingSlot = false
        }
    }

    private fun performSeamlessSlotSwitch(forceEvenIfTexNotReady: Boolean = false) {
        if (isSwitchingSlot) return
        isSwitchingSlot = true
        try {
            val prep = preparingPlayer ?: return
            val targetSlot = preparingSlot
            if (targetSlot < 0) return
            val targetSt = if (targetSlot == 0) videoSurfaceTextureA else videoSurfaceTextureB
            val targetMatrix = if (targetSlot == 0) texMatrixA else texMatrixB

            var texUpdated = false
            try {
                targetSt?.updateTexImage()
                targetSt?.getTransformMatrix(targetMatrix)
                // Xiaomi GPU 1088 Stride Alignment (Removes 8-pixel padding artifact)
                val strideCropV = com.tajasb.engine.BufferQueueManager.getStrideCropFactorV(surfaceWidth, surfaceHeight)
                if (strideCropV < 1.0f) {
                    GlMatrix.scaleM(targetMatrix, 0, 1.0f, strideCropV, 1.0f)
                }
                texUpdated = true
            } catch (t: Throwable) {
                Log.d(TAG, "performSeamlessSlotSwitch: target texture not ready yet: ${t.message}")
            }

            if (!texUpdated && !forceEvenIfTexNotReady) {
                Log.d(TAG, "performSeamlessSlotSwitch: Frame belum siap di targetSt, menunda switch agar tidak fliker...")
                return
            }

            val old = mediaPlayer
            try { old?.setOnCompletionListener(null) } catch (_: Throwable) {}
            try { old?.setOnErrorListener(null) } catch (_: Throwable) {}
            try { old?.setVolume(0f, 0f) } catch (_: Throwable) {}
            // CRITICAL: Immediately detach surface from old player so targetVs is 100% free for next slot!
            try { old?.setSurface(null) } catch (_: Throwable) {}
            activeSlot = targetSlot
            mediaPlayer = prep
            preparingPlayer = null
            preparingSlot = -1
            preparingStartTimeMs = 0L
            hasTriggeredPrewarmForCurrentClip = false

            if (prep.videoWidth > 0 && prep.videoHeight > 0) {
                this.videoWidth = prep.videoWidth
                this.videoHeight = prep.videoHeight
                mvpNeedsUpdate = true
            }
            prep.isLooping = isLoopingEnabled
            val currentVol = if (isMuted || isVideoAudioMuted) 0f else currentVideoVolume
            prep.setVolume(currentVol, currentVol)
            lastAntiPlTrackPosMs = 0
            rollNextLoopAntiPlEntropy("slot_switch")
            val switchedMp = prep
            glHandler?.postDelayed({
                if (mediaPlayer == switchedMp && !isSwitchingSlot && !isTransitioningVideo) {
                    applyAudioAntiHashToPlayer(switchedMp)
                }
            }, 280L)
            lastPlayerPos = 0
            stuckPositionCount = 0
            isTransitioningVideo = false
            isCompleting.set(false)
            val switchDoneNow = System.currentTimeMillis()
            lastSwitchTimeMs = switchDoneNow
            lastFrameDrawTime = switchDoneNow
            lastRealFrameTime = switchDoneNow
            lastRealVideoDecodedTimeMs = switchDoneNow
            hasNewFrame = true
            drawFrame(forceRedraw = true)

            if (old != null && old != prep) {
                releasePlayerAsync(old)
            }
            rLog(LogLevel.INFO, "✅ [SLOT-SWITCH] Berhasil beralih ke slot $activeSlot untuk video: $effectiveVideoName")
            Log.i(TAG, "Seamlessly switched to slot $activeSlot with fresh frame for $effectiveVideoName")

            glHandler?.postDelayed({
                if (isRunning && !isPaused) {
                    playerReleaseExecutor.execute {
                        try { onPrewarmNextRequested?.invoke() } catch (_: Throwable) {}
                    }
                }
            }, 350L)
        } finally {
            isSwitchingSlot = false
        }
    }

    private fun setupMediaPlayer() {
        createAndStartPlayer(videoFile)
    }

    private fun attachActivePlayerListeners(player: MediaPlayer, file: File) {
        player.setOnVideoSizeChangedListener { mp, width, height ->
            if (width > 0 && height > 0) {
                this@GlVideoRenderer.videoWidth = width
                this@GlVideoRenderer.videoHeight = height
                mvpNeedsUpdate = true
                Log.i(TAG, "Video dimensions resolved: ${width}x${height}")
            }
        }

        player.setOnInfoListener { mp, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                if (mediaPlayer == mp) {
                    val renderNow = System.currentTimeMillis()
                    lastFrameDrawTime = renderNow
                    lastRealFrameTime = renderNow
                    lastRealVideoDecodedTimeMs = renderNow
                    hasNewFrame = true
                    drawFrame(forceRedraw = true)
                }
            }
            false
        }

        player.setOnCompletionListener { mp ->
            if (mediaPlayer != mp) {
                Log.d(TAG, "setOnCompletionListener: Mengabaikan completion dari MediaPlayer lama/non-aktif untuk ${file.name}")
                return@setOnCompletionListener
            }
            Log.i(TAG, "Video completed: ${file.name} (isLooping=$isLoopingEnabled)")
            val now = System.currentTimeMillis()
            lastRealFrameTime = now
            lastFrameDrawTime = now
            if (isDecoyLoopGateEnabled && !isDecoyHardFrozen && (now - decoyLastLoopIncrementTimeMs > 1400L)) {
                val dur = try { mp.duration } catch (_: Throwable) { 0 }
                onDecoyVideoLoopAdvanced("on_completion", dur)
            }
            val hasQueuedVideo = try {
                com.tajasb.MainHook.livePendingVideoPath != null || com.tajasb.MainHook.liveBridgeHasPending
            } catch (_: Throwable) { false }
            if (isLoopingEnabled && !hasQueuedVideo) {
                try {
                    rollNextLoopAntiPlEntropy("single_loop_eof")
                    if (!mp.isPlaying && !isPaused && !isDecoyHardFrozen) {
                        mp.seekTo(0)
                        mp.start()
                        applyAudioAntiHashToPlayer(mp)
                    }
                    isCompleting.set(false)
                } catch (_: Throwable) {
                    isCompleting.set(false)
                    if (!isDecoyHardFrozen) {
                        glHandler?.post { createAndStartPlayer(file) }
                    }
                }
                return@setOnCompletionListener
            }

            if (isCompleting.compareAndSet(false, true)) {
                lastCompletionTriggerTimeMs = now
                isTransitioningVideo = true
                playerReleaseExecutor.execute {
                    val switched = try {
                        triggerVideoCompletion(alreadyMarkedCompleting = true)
                    } catch (_: Throwable) {
                        false
                    }
                    if (!switched) {
                        isTransitioningVideo = false
                        isCompleting.set(false)
                        glHandler?.post {
                            if (mediaPlayer == mp && !isPaused && !isDecoyHardFrozen) {
                                try {
                                    rollNextLoopAntiPlEntropy("fallback_loop_eof")
                                    mp.seekTo(0)
                                    mp.start()
                                    applyAudioAntiHashToPlayer(mp)
                                } catch (_: Throwable) {
                                    createAndStartPlayer(file)
                                }
                            }
                        }
                    }
                }
            } else if (preparingPlayer == null && !isSwitchingSlot && !isTransitioningVideo && (now - lastCompletionTriggerTimeMs > 2500L)) {
                // Hanya pulihkan jika transisi benar-benar macet > 2.5 detik dan tidak ada worker completion yang sedang berjalan
                try {
                    isCompleting.set(false)
                    if (!mp.isPlaying && !isPaused && !isDecoyHardFrozen) {
                        mp.seekTo(0)
                        mp.start()
                        applyAudioAntiHashToPlayer(mp)
                    }
                } catch (_: Throwable) {
                    glHandler?.post { createAndStartPlayer(file) }
                }
            }
        }

        player.setOnErrorListener { mp, what, extra ->
            if (what == -38) {
                return@setOnErrorListener true
            }
            if (mp != mediaPlayer && mp != preparingPlayer) {
                try { mp.release() } catch (_: Throwable) {}
                return@setOnErrorListener true
            }
            val now = System.currentTimeMillis()
            lastRealFrameTime = now
            lastFrameDrawTime = now
            stuckPositionCount = 0
            val savedPos = if (mp == mediaPlayer) {
                try {
                    val p = mp.currentPosition
                    val d = mp.duration
                    if (p > 500 && (d <= 0 || p < d - 800)) p else 0
                } catch (_: Throwable) { 0 }
            } else 0
            if (savedPos > 0) {
                initialSeekPositionMs = savedPos
            }
            if (extra == MediaPlayer.MEDIA_ERROR_IO || extra == -1004) {
                rLog(LogLevel.DEBUG, "🔄 [LOOP-RESTART] Pemulihan I/O mulus pada ${file.name} (resume=${savedPos}ms)")
            } else {
                rLog(LogLevel.INFO, "ℹ️ Pemulihan MediaPlayer: what=$what, extra=$extra pada ${file.name} (resume=${savedPos}ms)")
            }
            try { mp.setOnErrorListener(null) } catch (_: Throwable) {}
            try { mp.setOnCompletionListener(null) } catch (_: Throwable) {}
            try { mp.setSurface(null) } catch (_: Throwable) {}
            releasePlayerAsync(mp)
            if (mediaPlayer == mp) mediaPlayer = null
            if (preparingPlayer == mp) {
                preparingPlayer = null
                preparingSlot = -1
                preparingStartTimeMs = 0L
            }
            isTransitioningVideo = false
            isCompleting.set(false)
            glHandler?.postDelayed({ createAndStartPlayer(file) }, 120L)
            true
        }
    }

    private fun createAndStartPlayer(file: File) {
        try {
            clearStandbyPlayer()
            val prevPrep = preparingPlayer
            preparingPlayer = null
            preparingSlot = -1
            preparingStartTimeMs = 0L
            if (prevPrep != null) {
                try { prevPrep.setOnPreparedListener(null) } catch (_: Throwable) {}
                try { prevPrep.setOnErrorListener(null) } catch (_: Throwable) {}
                try { prevPrep.setOnCompletionListener(null) } catch (_: Throwable) {}
                try { prevPrep.setSurface(null) } catch (_: Throwable) {}
                releasePlayerAsync(prevPrep)
            }
            if (mediaPlayer != null) {
                // Prevent old active player at EOF from firing a second completion while next player prepares
                try { mediaPlayer?.setOnCompletionListener(null) } catch (_: Throwable) {}
            }

            // Pick target slot: if a player is currently active, use the inactive slot for zero-freeze preparation
            val targetSlot = if (mediaPlayer == null) activeSlot else (1 - activeSlot)
            var targetVs = if (targetSlot == 0) videoSurfaceA else videoSurfaceB
            var targetSt = if (targetSlot == 0) videoSurfaceTextureA else videoSurfaceTextureB
            var targetTexId = if (targetSlot == 0) oesTextureIdA else oesTextureIdB

            // Always create a virgin OES texture, SurfaceTexture, and Surface when preparing on the inactive slot
            // so the new MediaCodec decoder never inherits stale BufferQueue buffers or timestamps from the previous clip!
            val needsFreshSlotSurface = (mediaPlayer != null && targetSlot != activeSlot) ||
                targetSt == null || targetVs == null || !targetVs.isValid
            if (needsFreshSlotSurface) {
                try { targetSt?.setOnFrameAvailableListener(null) } catch (_: Throwable) {}
                try { targetVs?.release() } catch (_: Throwable) {}
                try { targetSt?.release() } catch (_: Throwable) {}
                if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
                    try {
                        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                        if (targetTexId != 0) {
                            GLES20.glDeleteTextures(1, intArrayOf(targetTexId), 0)
                        }
                        val newTexArr = IntArray(1)
                        GLES20.glGenTextures(1, newTexArr, 0)
                        targetTexId = newTexArr[0]
                        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, targetTexId)
                        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
                        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
                        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                        if (targetSlot == 0) {
                            oesTextureIdA = targetTexId
                        } else {
                            oesTextureIdB = targetTexId
                        }
                    } catch (_: Throwable) {}
                }
                val newSt = SurfaceTexture(targetTexId)
                bindFrameAvailableListener(newSt, targetSlot)
                targetSt = newSt
                targetVs = Surface(newSt)
                if (targetSlot == 0) {
                    videoSurfaceTextureA = newSt
                    videoSurfaceA = targetVs
                } else {
                    videoSurfaceTextureB = newSt
                    videoSurfaceB = targetVs
                }
            }

            val player = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                    setAudioAttributes(attributes)
                } else {
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                }

                // Set data source safely (dukungan HLS / M3U8 Live Stream URL dan file MP4 lokal)
                val activeStream = liveStreamUrl
                if (!activeStream.isNullOrBlank() && (activeStream.startsWith("http://") || activeStream.startsWith("https://"))) {
                    if (this@GlVideoRenderer.videoWidth <= 0 || (this@GlVideoRenderer.videoWidth == 1080 && this@GlVideoRenderer.videoHeight == 1920)) {
                        this@GlVideoRenderer.videoWidth = 1280
                        this@GlVideoRenderer.videoHeight = 720
                        mvpNeedsUpdate = true
                    }
                    try {
                        Log.i(TAG, "🌐 [LIVE-STREAM-INGEST] Memutar live stream dari web relay: $activeStream")
                        setDataSource(activeStream)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Gagal memutar stream URL: ${e.message}, fallback ke file lokal: ${file.absolutePath}")
                        setDataSource(file.absolutePath)
                    }
                } else {
                    try {
                        setDataSource(file.absolutePath)
                    } catch (_: Throwable) {
                        try {
                            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                            if (targetSlot == 0) {
                                activePfd?.let { try { it.close() } catch (_: Throwable) {} }
                                activePfd = pfd
                            } else {
                                preparingPfd?.let { try { it.close() } catch (_: Throwable) {} }
                                preparingPfd = pfd
                            }
                            setDataSource(pfd.fileDescriptor)
                        } catch (e: Throwable) {
                            Log.e(TAG, "Cannot set data source for video: ${e.message}")
                        }
                    }
                }
                setSurface(targetVs)
                isLooping = isLoopingEnabled
                val vol = if (mediaPlayer == null) (if (isMuted || isVideoAudioMuted) 0f else currentVideoVolume) else 0f
                setVolume(vol, vol)

                attachActivePlayerListeners(this, file)

                setOnPreparedListener { mp ->
                    if (mediaPlayer == null) {
                        // Initial startup: Activate slot immediately
                        activeSlot = targetSlot
                        mediaPlayer = mp
                        preparingPlayer = null
                        preparingSlot = -1
                        preparingStartTimeMs = 0L
                        hasTriggeredPrewarmForCurrentClip = false

                        if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                            this@GlVideoRenderer.videoWidth = mp.videoWidth
                            this@GlVideoRenderer.videoHeight = mp.videoHeight
                            mvpNeedsUpdate = true
                        }
                        mp.isLooping = isLoopingEnabled
                        val currentVol = if (isMuted || isVideoAudioMuted) 0f else currentVideoVolume
                        mp.setVolume(currentVol, currentVol)
                        if (initialSeekPositionMs > 0 && liveStreamUrl.isNullOrBlank()) {
                            val resumeTarget = initialSeekPositionMs
                            initialSeekPositionMs = 0
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    mp.seekTo(resumeTarget.toLong(), MediaPlayer.SEEK_CLOSEST)
                                } else {
                                    mp.seekTo(resumeTarget)
                                }
                            } catch (_: Throwable) {}
                        } else if (isSessionDnaEnabled &&
                            antiHashMode != com.tajasb.model.AntiHashIntensity.OFF &&
                            currentSessionDna.initialSeekOffsetMs > 0 &&
                            !file.name.startsWith("vs_rolling_")
                        ) {
                            try {
                                val dur = mp.duration
                                val offsetMs = currentSessionDna.initialSeekOffsetMs
                                if (dur > offsetMs + 5000) {
                                    mp.seekTo(offsetMs)
                                    Log.i(TAG, "🧬 [DNA-SESI] Start offset ${offsetMs}ms diterapkan untuk ${currentSessionDna.badgeText} (dur=${dur}ms)")
                                }
                            } catch (_: Throwable) {}
                        }
                        lastPlayerPos = 0
                        stuckPositionCount = 0
                        isTransitioningVideo = false
                        isCompleting.set(false)
                        if (!isPaused) {
                            mp.start()
                            glHandler?.postDelayed({
                                if (mediaPlayer == mp && !isTransitioningVideo) {
                                    applyAudioAntiHashToPlayer(mp)
                                }
                            }, 300L)
                        }
                        val prepNow = System.currentTimeMillis()
                        lastSwitchTimeMs = prepNow
                        lastFrameDrawTime = prepNow
                        lastRealFrameTime = prepNow
                        lastRealVideoDecodedTimeMs = prepNow
                        hasNewFrame = true
                        drawFrame(forceRedraw = true)
                        Log.i(TAG, "Initial MediaPlayer prepared in GlVideoRenderer slot $activeSlot (${this@GlVideoRenderer.videoWidth}x${this@GlVideoRenderer.videoHeight}) looping=$isLoopingEnabled")
                        glHandler?.postDelayed({
                            if (isRunning && !isPaused) {
                                playerReleaseExecutor.execute {
                                    try { onPrewarmNextRequested?.invoke() } catch (_: Throwable) {}
                                }
                            }
                        }, 400L)
                    } else {
                        // Seamless Transition: Start decoding into target slot while old player continues playing
                        preparingPlayer = mp
                        preparingSlot = targetSlot
                        preparingStartTimeMs = System.currentTimeMillis()
                        if (initialSeekPositionMs > 0) {
                            val resumeTarget = initialSeekPositionMs
                            initialSeekPositionMs = 0
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    mp.seekTo(resumeTarget.toLong(), MediaPlayer.SEEK_CLOSEST)
                                } else {
                                    mp.seekTo(resumeTarget)
                                }
                            } catch (_: Throwable) {}
                        }
                        if (!isPaused) {
                            mp.start()
                        }
                        Log.i(TAG, "Background MediaPlayer prepared in slot $targetSlot, waiting for first frame before zero-flicker slot switch...")
                        // Fallback: give large MB / high-bitrate videos up to 2500ms to decode first frame before forcing slot switch
                        glHandler?.postDelayed({
                            if (preparingPlayer == mp && preparingSlot == targetSlot) {
                                rLog(LogLevel.INFO, "ℹ️ Transisi: Memaksa slot switch $targetSlot setelah 2500ms")
                                performSeamlessSlotSwitch(forceEvenIfTexNotReady = true)
                            }
                        }, 2500L)
                    }
                }
            }

            player.prepareAsync()
        } catch (t: Throwable) {
            isTransitioningVideo = false
            Log.e(TAG, "Failed to start MediaPlayer for ${file.name}: ${t.message}", t)
        }
    }

    /**
     * Seamlessly switches to a new video file by transitioning the MediaPlayer instantly
     * with zero black frames, preserving the active frame until the new video is prepared and decoded.
     */
    fun switchVideoFile(newFile: File, displayName: String? = null, forceSwitch: Boolean = false) {
        if (!newFile.exists() || newFile.length() == 0L) return
        val now = System.currentTimeMillis()
        val candName = if (!displayName.isNullOrBlank()) displayName else newFile.name
        val newLen = newFile.length()
        val newMod = newFile.lastModified()
        val isSamePathAndMod = (newFile.absolutePath == lastSwitchedFilePath && newMod == lastSwitchedFileModified && newLen == lastSwitchedFileLength)
        val isSameContentIdentity = (
            newLen > 1024L &&
            newLen == lastSwitchedFileLength &&
            newMod == lastSwitchedFileModified &&
            (candName == lastSwitchedDisplayName || newFile.name == videoFile.name)
        )
        val activePos = try { mediaPlayer?.currentPosition ?: 0 } catch (_: Throwable) { 0 }
        val activeDur = try { mediaPlayer?.duration ?: 0 } catch (_: Throwable) { 0 }
        val isNearEnd = (activeDur > 500 && activePos >= activeDur - 600)
        val isAlreadyPlayingOrPreparingSame = (
            preparingPlayer != null ||
            isTransitioningVideo ||
            isSwitchingSlot ||
            (activePos in 80..12000 && !isNearEnd)
        )

        val isActivelyPlayingCurrent = try { mediaPlayer?.isPlaying == true } catch (_: Throwable) { false }
        if (!forceSwitch && (
                (isSamePathAndMod && !isNearEnd && ((now - lastSwitchTimeMs < 900L) || isActivelyPlayingCurrent)) ||
                (isSameContentIdentity && (now - lastSwitchTimeMs < 1800L) && isAlreadyPlayingOrPreparingSame)
            )
        ) {
            isCompleting.set(false)
            if (preparingPlayer == null && !isSwitchingSlot) {
                isTransitioningVideo = false
                if (!isActivelyPlayingCurrent && !isPaused && !isDecoyHardFrozen) {
                    glHandler?.post {
                        try {
                            mediaPlayer?.let { mp ->
                                if (!mp.isPlaying) {
                                    if (isNearEnd) mp.seekTo(0)
                                    mp.start()
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
            Log.d(TAG, "switchVideoFile: Mengabaikan pemanggilan duplikat berkas yang sama ($candName, ${newLen}B) agar tidak reset ke 0:00")
            return
        }
        lastSwitchedFilePath = newFile.absolutePath
        lastSwitchedFileLength = newLen
        lastSwitchedFileModified = newMod
        lastSwitchedDisplayName = candName
        lastSwitchTimeMs = now
        lastCompletionTriggerTimeMs = now
        lastRealFrameTime = now
        lastFrameDrawTime = now
        initialSeekPositionMs = 0
        lastPlayerPos = 0
        stuckPositionCount = 0
        isTransitioningVideo = true
        if (isDecoyLoopGateEnabled && !isDecoyHardFrozen && (now - decoyLastLoopIncrementTimeMs > 1400L)) {
            onDecoyVideoLoopAdvanced("switch_video", 0)
        }
        this.customDisplayName = if (!displayName.isNullOrBlank()) displayName else null
        val sizeMb = String.format(java.util.Locale.US, "%.1f", (newLen / (1024f * 1024f)))
        this.videoFile = newFile
        isCompleting.set(true)
        glHandler?.postDelayed({
            if (preparingPlayer == null && !isSwitchingSlot && !isTransitioningVideo) {
                isCompleting.set(false)
            }
        }, 12000L)

        val handler = glHandler
        if (handler == null) {
            setupMediaPlayer()
            return
        }

        handler.post {
            if (forceSwitch) {
                clearStandbyPlayer()
            } else if (activateStandbySlotNow(newFile, displayName)) {
                return@post
            }
            rLog(LogLevel.INFO, "🔄 [SWITCH] Memulai transisi ke: $effectiveVideoName ($sizeMb MB)...")
            createAndStartPlayer(newFile)
            rLog(LogLevel.INFO, "🎬 [PLAYER] Memulai inisialisasi decoder untuk: $effectiveVideoName")
        }
    }

    fun seekTo(positionMs: Int) {
        val handler = glHandler ?: return
        handler.post {
            try {
                val now = System.currentTimeMillis()
                isTransitioningVideo = false
                isCompleting.set(false)
                if (positionMs <= 100) {
                    lastSwitchTimeMs = now
                    lastCompletionTriggerTimeMs = now
                    hasTriggeredPrewarmForCurrentClip = false
                    lastAntiPlTrackPosMs = 0
                    rollNextLoopAntiPlEntropy("seek_restart")
                }
                val mp = mediaPlayer
                if (mp != null) {
                    mp.seekTo(positionMs)
                    if (!isPaused && !mp.isPlaying) {
                        mp.start()
                    }
                    applyAudioAntiHashToPlayer(mp)
                }
                hasNewFrame = true
                lastFrameDrawTime = now
                lastRealFrameTime = now
                drawFrame(forceRedraw = true)
                if (positionMs <= 100) {
                    glHandler?.postDelayed({
                        if (isRunning && !isPaused) {
                            playerReleaseExecutor.execute {
                                try { onPrewarmNextRequested?.invoke() } catch (_: Throwable) {}
                            }
                        }
                    }, 350L)
                }
                Log.i(TAG, "Seeked GlVideoRenderer to ${positionMs}ms")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed seekTo ${positionMs}ms: ${t.message}")
            }
        }
    }

    fun resumePlayback() {
        val handler = glHandler ?: return
        handler.post {
            try {
                isCompleting.set(false)
                isPaused = false
                mediaPlayer?.start()
                hasNewFrame = true
                drawFrame(forceRedraw = true)
            } catch (_: Throwable) {}
        }
    }

    fun setAntiHashMode(mode: com.tajasb.model.AntiHashIntensity) {
        antiHashMode = mode
        videoAntiHashPercent = mode.defaultPercent
        mvpNeedsUpdate = true
        rLog(LogLevel.INFO, "🛡️ [ANTI-HASH] Mode Anti-Deteksi Server TikTok diperbarui ke: ${mode.name} (${videoAntiHashPercent}%)")
        glHandler?.post { drawFrame(forceRedraw = true) }
    }

    fun setVideoAntiHashPercent(pct: Float) {
        val clamped = pct.coerceIn(0.0f, 5.0f)
        this.videoAntiHashPercent = clamped
        this.antiHashMode = com.tajasb.model.AntiHashIntensity.fromPercent(clamped)
        mvpNeedsUpdate = true
        rLog(LogLevel.INFO, "🛡️ [ANTI-HASH] Video Anti-Deteksi Stepper disetel: ${clamped}% (${antiHashMode.name})")
        glHandler?.post { drawFrame(forceRedraw = true) }
    }

    fun setSessionDnaFraming(zoom: Float, panX: Float, panY: Float, enabled: Boolean = this.isDnaZoomEnabled) {
        val safeZ = zoom.coerceIn(1.04f, 2.50f)
        val safeLimit = com.tajasb.model.FramingGuardMath.calculateSafePanLimit(safeZ)
        this.isDnaZoomEnabled = enabled
        this.sessionDnaCustomZoom = safeZ
        this.sessionDnaPanX = panX.coerceIn(-safeLimit, safeLimit)
        this.sessionDnaPanY = panY.coerceIn(-safeLimit, safeLimit)
        mvpNeedsUpdate = true
        rLog(LogLevel.INFO, "🎯 [FRAMING-GUARD] DNA Zoom (${if (enabled) "ON" else "OFF"}): ${safeZ}x (Pan: ${sessionDnaPanX}, ${sessionDnaPanY}, MaxSafe: ±${safeLimit})")
        glHandler?.post { drawFrame(forceRedraw = true) }
    }

    // Decoy Glitch Injection Methods (Aggressive Sabotage Modes)
    fun injectMicroFpsDrop(durationMs: Long = 18000L, targetFps: Int = 3) {
        decoyTargetFps = targetFps.coerceIn(2, 12)
        decoyFpsDropUntilMs = System.currentTimeMillis() + durationMs
        Log.i(TAG, "🎭 [DECOY] Injected Extreme FPS Drop ($decoyTargetFps FPS) for ${durationMs}ms")
    }

    fun injectMicroFreeze(durationMs: Long = 7000L) {
        decoyFreezeUntilMs = System.currentTimeMillis() + durationMs.coerceIn(1000L, 15000L)
        Log.i(TAG, "🎭 [DECOY] Injected Hard Freeze for ${durationMs}ms")
    }

    fun injectMicroDim(durationMs: Long = 6000L) {
        decoyDimUntilMs = System.currentTimeMillis() + durationMs.coerceIn(500L, 15000L)
        Log.i(TAG, "🎭 [DECOY] Injected Blackout Flicker for ${durationMs}ms")
    }

    fun injectGpuCorruption(durationMs: Long = 12000L) {
        decoyGpuCorruptUntilMs = System.currentTimeMillis() + durationMs.coerceIn(2000L, 25000L)
        mvpNeedsUpdate = true
        Log.i(TAG, "🎭 [DECOY] Injected GPU Green/Magenta Corruption for ${durationMs}ms")
    }

    fun injectFlipAndZoom(durationMs: Long = 14000L, angleOffset: Int = 180, zoomFactor: Float = 2.65f) {
        decoyFlipZoomUntilMs = System.currentTimeMillis() + durationMs.coerceIn(2000L, 25000L)
        decoyFlipAngleOffset = angleOffset
        decoyZoomFactor = zoomFactor.coerceIn(1.8f, 3.5f)
        mvpNeedsUpdate = true
        Log.i(TAG, "🎭 [DECOY] Injected Flip ($angleOffset°) & Zoom (${decoyZoomFactor}x) for ${durationMs}ms")
    }

    fun injectAudioStutter(durationMs: Long = 14000L) {
        decoyAudioStutterUntilMs = System.currentTimeMillis() + durationMs
        Log.i(TAG, "🎭 [DECOY] Injected Extreme Audio Stutter & Pitch Sabotage for ${durationMs}ms")
        glHandler?.post {
            try {
                val mp = mediaPlayer
                if (mp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mp.isPlaying) {
                    val p = mp.playbackParams
                    val weirdPitch = if ((System.currentTimeMillis() / 1000L) % 2L == 0L) 0.58f else 1.68f
                    val weirdSpeed = if ((System.currentTimeMillis() / 1000L) % 2L == 0L) 0.65f else 1.42f
                    p.pitch = weirdPitch
                    p.speed = weirdSpeed
                    mp.playbackParams = p
                }
            } catch (_: Throwable) {}
        }
    }

    private fun rollDynamicTargetLoop(maxLoops: Int): Int {
        val safeMax = maxLoops.coerceIn(1, 50)
        if (safeMax <= 1) return 1
        return 1 + java.util.Random().nextInt(safeMax)
    }

    fun configureDecoyLoopGate(enabled: Boolean, targetLoopCount: Int = 4) {
        val safeMaxLoops = targetLoopCount.coerceIn(1, 50)
        val wasEnabled = isDecoyLoopGateEnabled
        val prevMax = decoyMaxLoopRange
        isDecoyLoopGateEnabled = enabled
        decoyMaxLoopRange = safeMaxLoops
        if (!enabled) {
            clearDecoyGlitches()
        } else if (!wasEnabled || decoyCurrentLoopNumber < 1 || prevMax != safeMaxLoops) {
            decoyCurrentLoopNumber = 1
            decoyHighestPosInLoopMs = 0
            decoyLastLoopIncrementTimeMs = System.currentTimeMillis()
            decoyRandomFreezeTimestampMs = -1
            decoyPassEvaluatedForLoop = 0
            isDecoyHardFrozen = false
            decoyFreezeUntilMs = 0L
            decoyFreezeTargetLoopCount = rollDynamicTargetLoop(safeMaxLoops)
            val dur = try { mediaPlayer?.duration ?: 0 } catch (_: Throwable) { 0 }
            if (decoyCurrentLoopNumber >= decoyFreezeTargetLoopCount && dur > 400) {
                evaluateLoopPassOrScheduleFreeze(dur)
            }
            Log.i(
                TAG,
                "🎭 [DECOY-DYNAMIC] Rentang Dinamis 1..$safeMaxLoops Putaran -> Sesi ini mengundi target awal putaran ke-$decoyFreezeTargetLoopCount (Acak 5%–95% durasi + peluang lolos)"
            )
        }
    }

    private fun evaluateLoopPassOrScheduleFreeze(durationMs: Int) {
        val rnd = java.util.Random()
        // Peluang Lolos Putaran (~32%): jika belum menyentuh batas maksimal N (decoyMaxLoopRange),
        // ada peluang acak video lolos ke putaran berikutnya sehingga ritme freeze semakin natural
        if (
            decoyCurrentLoopNumber < decoyMaxLoopRange &&
            decoyPassEvaluatedForLoop != decoyCurrentLoopNumber
        ) {
            decoyPassEvaluatedForLoop = decoyCurrentLoopNumber
            val passRoll = rnd.nextInt(100)
            if (passRoll < 32) {
                decoyFreezeTargetLoopCount = (decoyCurrentLoopNumber + 1).coerceAtMost(decoyMaxLoopRange)
                decoyRandomFreezeTimestampMs = -1
                Log.i(
                    TAG,
                    "🎲 [DECOY-DYNAMIC] Putaran ke-$decoyCurrentLoopNumber mendapat PELUANG LOLOS (roll=$passRoll%) -> Target freeze bergeser ke putaran ke-$decoyFreezeTargetLoopCount (batas max=$decoyMaxLoopRange)"
                )
                return
            }
        }
        scheduleRandomFreezeTimestampForLoop(durationMs)
    }

    private fun onDecoyVideoLoopAdvanced(reason: String, durationMs: Int) {
        val now = System.currentTimeMillis()
        decoyLastLoopIncrementTimeMs = now
        decoyHighestPosInLoopMs = 0
        decoyCurrentLoopNumber++
        Log.i(
            TAG,
            "🎭 [DECOY-DYNAMIC] Memasuki putaran video ke-$decoyCurrentLoopNumber (target dinamis: ke-$decoyFreezeTargetLoopCount dari 1..$decoyMaxLoopRange, via=$reason)"
        )
        if (decoyCurrentLoopNumber >= decoyFreezeTargetLoopCount && decoyRandomFreezeTimestampMs < 0) {
            evaluateLoopPassOrScheduleFreeze(durationMs)
        }
    }

    private fun scheduleRandomFreezeTimestampForLoop(durationMs: Int) {
        val dur = if (durationMs > 0) durationMs else try { mediaPlayer?.duration ?: 0 } catch (_: Throwable) { 0 }
        val rnd = java.util.Random()
        var chosenPct = 50
        decoyRandomFreezeTimestampMs = if (dur > 1200) {
            // Acak dinamis antara 5% sampai 95% dari durasi video
            val minPct = 0.05f
            val maxPct = 0.95f
            val pct = minPct + rnd.nextFloat() * (maxPct - minPct)
            chosenPct = (pct * 100f).toInt().coerceIn(5, 95)
            (dur * pct).toInt().coerceIn(350, (dur - 200).coerceAtLeast(400))
        } else {
            400 + rnd.nextInt(1600)
        }
        Log.w(
            TAG,
            "🎭 [DECOY-DYNAMIC] Putaran ke-$decoyCurrentLoopNumber dikunci untuk Freeze! Titik Freeze Acak dijadwalkan pada $chosenPct% durasi (${decoyRandomFreezeTimestampMs}ms / ${dur}ms)"
        )
    }

    fun triggerPermanentLoopFreeze() {
        if (isDecoyHardFrozen) return
        isDecoyHardFrozen = true
        decoyFreezeUntilMs = Long.MAX_VALUE
        Log.e(TAG, "🥶 [DECOY-HARD-FREEZE] Putaran ke-$decoyCurrentLoopNumber menyentuh detik acak (${decoyRandomFreezeTimestampMs}ms) -> KAMERA & APK MEMBEKU TOTAL!")
        glHandler?.post {
            try {
                mediaPlayer?.setVolume(0f, 0f)
                audioPlayer?.setVolume(0f, 0f)
                mediaPlayer?.pause()
                audioPlayer?.pause()
            } catch (_: Throwable) {}
        }
        try {
            com.tajasb.engine.DecoyEngine.onRendererTriggeredHardFreeze()
        } catch (_: Throwable) {}
    }

    fun clearDecoyGlitches() {
        isDecoyLoopGateEnabled = false
        isDecoyHardFrozen = false
        decoyCurrentLoopNumber = 1
        decoyHighestPosInLoopMs = 0
        decoyRandomFreezeTimestampMs = -1
        decoyFpsDropUntilMs = 0L
        decoyFreezeUntilMs = 0L
        decoyDimUntilMs = 0L
        decoyAudioStutterUntilMs = 0L
        decoyGpuCorruptUntilMs = 0L
        decoyFlipZoomUntilMs = 0L
        decoyAudioChopMuted = false
        mvpNeedsUpdate = true
        try {
            com.tajasb.engine.DecoyEngine.releaseHostAndAppFreeze()
        } catch (_: Throwable) {}
        glHandler?.post {
            try {
                val vol = if (isMuted || isVideoAudioMuted) 0f else currentVideoVolume
                mediaPlayer?.setVolume(vol, vol)
                preparingPlayer?.setVolume(0f, 0f)
                audioPlayer?.setVolume(vol, vol)
                if (!isPaused) {
                    try {
                        if (mediaPlayer?.isPlaying == false) mediaPlayer?.start()
                    } catch (_: Throwable) {}
                    try {
                        if (!isMp3AudioPaused && audioPlayer?.isPlaying == false) audioPlayer?.start()
                    } catch (_: Throwable) {}
                }
                applyAudioAntiHashToPlayers()
                hasNewFrame = true
                drawFrame(forceRedraw = true)
                Log.i(TAG, "✅ [DECOY NORMALIZED] Semua efek Freeze & Kunci UI dibersihkan -> Kembali 60 FPS Normal 100%")
            } catch (_: Throwable) {}
        }
    }

    fun normalizeDecoyState() = clearDecoyGlitches()

    fun setAudioAntiHash(enabled: Boolean, mode: com.tajasb.model.AudioAntiHashIntensity) {
        this.isAudioAntiHashEnabled = enabled
        this.audioAntiHashMode = mode
        applyAudioAntiHashToPlayers()
        rLog(LogLevel.INFO, "🛡️ [AUDIO ANTI-DETEKSI] Audio Anti-Deteksi: enabled=$enabled, mode=${mode.displayName}")
    }

    fun setAudioAntiHashPercent(pct: Float) {
        this.audioAntiHashPercent = pct.coerceIn(0.1f, 5.0f)
        applyAudioAntiHashToPlayers()
        rLog(LogLevel.INFO, "🛡️ [AUDIO ANTI-DETEKSI] Audio Anti-Deteksi Stepper disetel: ${pct}%")
    }

    fun calculateDriftSpeed(mode: com.tajasb.model.AudioAntiHashIntensity, isVideoMuted: Boolean = (isMuted || isVideoAudioMuted)): Float {
        // Multi-prime aperiodic wave centered on currentSessionDna.speedCenterRatio when Session DNA is enabled
        val step = audioDriftStep.toDouble()
        val aperiodic = (kotlin.math.sin(step) * 0.62 + kotlin.math.cos(step * 0.6180339887) * 0.38).toFloat()
        val sessionSpeedBase = if (isSessionDnaEnabled) currentSessionDna.speedCenterRatio else 1.0f
        val loopBias = if (isVideoMuted) loopEntropySpeedBias else (loopEntropySpeedBias * 0.45f)
        val pctFactor = (audioAntiHashPercent / 2.0f).coerceIn(0.1f, 2.5f)
        return when (mode) {
            com.tajasb.model.AudioAntiHashIntensity.LOW -> {
                (sessionSpeedBase + loopBias + aperiodic * (0.0055f * pctFactor)).coerceIn(0.972f, 1.028f)
            }
            com.tajasb.model.AudioAntiHashIntensity.MEDIUM -> {
                (sessionSpeedBase + loopBias + aperiodic * (0.0095f * pctFactor)).coerceIn(0.968f, 1.032f)
            }
            com.tajasb.model.AudioAntiHashIntensity.AGGRESSIVE -> {
                (sessionSpeedBase + loopBias + aperiodic * (0.0145f * pctFactor)).coerceIn(0.962f, 1.038f)
            }
        }
    }

    fun calculateDynamicPitch(mode: com.tajasb.model.AudioAntiHashIntensity): Float {
        val step = audioDriftStep.toDouble()
        val pitchWave = (kotlin.math.sin(step * 0.73) * 0.55 + kotlin.math.cos(step * 1.19) * 0.45).toFloat()
        val dnaPitchOffset = if (isSessionDnaEnabled) (currentSessionDna.pitchCenterRatio - 1.0f) else 0.0f
        val pctFactor = (audioAntiHashPercent / 2.0f).coerceIn(0.1f, 2.5f)
        val modeScale = when (mode) {
            com.tajasb.model.AudioAntiHashIntensity.LOW -> 0.72f * pctFactor
            com.tajasb.model.AudioAntiHashIntensity.MEDIUM -> 1.00f * pctFactor
            com.tajasb.model.AudioAntiHashIntensity.AGGRESSIVE -> 1.25f * pctFactor
        }
        val sessionPitchCenter = 1.0f + (dnaPitchOffset * modeScale)
        val microPitchDelta = when (mode) {
            com.tajasb.model.AudioAntiHashIntensity.LOW -> pitchWave * (0.0040f * pctFactor)
            com.tajasb.model.AudioAntiHashIntensity.MEDIUM -> pitchWave * (0.0065f * pctFactor)
            com.tajasb.model.AudioAntiHashIntensity.AGGRESSIVE -> pitchWave * (0.0095f * pctFactor)
        }
        return (sessionPitchCenter + microPitchDelta).coerceIn(0.955f, 1.048f)
    }

    @Volatile
    var currentVoiceEffect: com.tajasb.model.VoiceEffect = com.tajasb.model.VoiceEffect.NORMAL

    fun setVoiceEffect(effect: com.tajasb.model.VoiceEffect) {
        currentVoiceEffect = effect
        applyAudioAntiHashToPlayers()
        Log.i(TAG, "🎭 [VOICE-EFFECT] GlVideoRenderer Efek Suara disetel: ${effect.label}")
    }

    fun applyAudioAntiHashToPlayer(player: MediaPlayer?) {
        if (player == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val effect = currentVoiceEffect
                val isVoiceEffectActive = effect != com.tajasb.model.VoiceEffect.NORMAL
                val isVideoSilent = (player == mediaPlayer || player == preparingPlayer) && (isMuted || isVideoAudioMuted)

                val targetPitch = if (!isVoiceEffectActive || isVideoSilent) 1.0f else effect.pitchMultiplier.coerceIn(0.5f, 2.0f)
                val baseSpeed = if (isAudioAntiHashEnabled) calculateDriftSpeed(audioAntiHashMode, isVideoMuted = isVideoSilent) else 1.0f
                val targetSpeed = (baseSpeed * effect.speedMultiplier).coerceIn(0.75f, 1.35f)

                // Jika Normal dan speed mendekati 1.0x: JANGAN SENTUH playbackParams agar stream audio 100% loss-free & bersuara jernih
                if (!isVoiceEffectActive && kotlin.math.abs(targetSpeed - 1.0f) < 0.005f) {
                    val curParams = try { player.playbackParams } catch (_: Throwable) { null }
                    if (curParams != null && (curParams.pitch != 1.0f || curParams.speed != 1.0f)) {
                        try {
                            player.playbackParams = PlaybackParams().apply {
                                pitch = 1.0f
                                speed = 1.0f
                            }
                        } catch (_: Throwable) {}
                    }
                    return
                }

                if (player.isPlaying) {
                    try {
                        val currentParams = try { player.playbackParams } catch (_: Throwable) { null }
                        val curPitch = currentParams?.pitch ?: 1.0f
                        val curSpeed = currentParams?.speed ?: 1.0f

                        if (kotlin.math.abs(curPitch - targetPitch) > 0.0005f || kotlin.math.abs(curSpeed - targetSpeed) > 0.0005f) {
                            val newParams = (currentParams ?: PlaybackParams()).apply {
                                pitch = targetPitch
                                speed = targetSpeed
                            }
                            player.playbackParams = newParams
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Hardware tidak mendukung pergeseran pitch, fallback ke suara asli: ${t.message}")
                        try {
                            player.playbackParams = PlaybackParams().apply { pitch = 1.0f; speed = 1.0f }
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "applyAudioAntiHash error: ${e.message}")
        }
    }

    fun applyAudioAntiHashToPlayers() {
        glHandler?.post {
            applyAudioAntiHashToPlayer(mediaPlayer)
            applyAudioAntiHashToPlayer(preparingPlayer)
            applyAudioAntiHashToPlayer(audioPlayer)
        }
    }

    private fun updateMvpMatrix(
        breathingScale: Float = 1.0f,
        microOffsetX: Float = 0.0f,
        microOffsetY: Float = 0.0f,
        microRotationDeg: Float = 0.0f,
        aspectWarpX: Float = 1.0f,
        aspectWarpY: Float = 1.0f
    ) {
        val now = System.currentTimeMillis()
        val isFlipZoomActive = now < decoyFlipZoomUntilMs
        val effRot = if (isFlipZoomActive) (currentRotation + decoyFlipAngleOffset) % 360 else currentRotation
        val effMir = if (isFlipZoomActive) !isMirrored else isMirrored
        val effFlipV = if (isFlipZoomActive && ((now / 1200L) % 2L == 0L)) !isVerticalFlipped else isVerticalFlipped
        val effScale = if (isFlipZoomActive) decoyZoomFactor else breathingScale

        GlShaderProgram.computeMvpMatrix(
            mvpMatrix = mvpMatrix,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            currentRotation = effRot,
            scaleMode = scaleMode,
            isMirrored = effMir,
            isVerticalFlipped = effFlipV,
            breathingScale = effScale,
            microOffsetX = if (isFlipZoomActive) 0.0f else microOffsetX,
            microOffsetY = if (isFlipZoomActive) 0.0f else microOffsetY,
            microRotationDeg = if (isFlipZoomActive) 0.0f else microRotationDeg,
            aspectWarpX = if (isFlipZoomActive) 1.0f else aspectWarpX,
            aspectWarpY = if (isFlipZoomActive) 1.0f else aspectWarpY,
            userZoom = userZoom,
            sessionDnaZoom = if (isFlipZoomActive || !isDnaZoomEnabled) 1.0f else sessionDnaCustomZoom,
            sessionPanX = if (isFlipZoomActive || (!isDnaZoomEnabled && liveStreamUrl.isNullOrBlank())) 0.0f else sessionDnaPanX,
            sessionPanY = if (isFlipZoomActive || (!isDnaZoomEnabled && liveStreamUrl.isNullOrBlank())) 0.0f else sessionDnaPanY,
            isLiveStream = !liveStreamUrl.isNullOrBlank()
        )
    }

    private fun drawFrame(forceRedraw: Boolean = false) {
        if (!isRunning || !targetSurface.isValid || eglSurface == EGL14.EGL_NO_SURFACE) return
        if (!hasNewFrame && !forceRedraw) return

        val activeTexId = if (activeSlot == 0) oesTextureIdA else oesTextureIdB
        val activeSt = if (activeSlot == 0) videoSurfaceTextureA else videoSurfaceTextureB

        val now = System.currentTimeMillis()
        val safeTargetFps = targetFps.coerceIn(15, 60)
        val timeSinceLastDraw = now - lastFrameDrawTime

        // Accurate frame throttle for 15 FPS
        if (!forceRedraw && safeTargetFps <= 18 && timeSinceLastDraw < 58L) {
            if (hasNewFrame) {
                try {
                    activeSt?.updateTexImage()
                    hasNewFrame = false
                } catch (_: Throwable) {}
            }
            return
        }

        // Decoy Glitch 2: Active Audio Chopper & Pitch Distortion
        if (now < decoyAudioStutterUntilMs) {
            if (now - lastDecoyAudioChopMs >= 135L) {
                lastDecoyAudioChopMs = now
                decoyAudioChopMuted = !decoyAudioChopMuted
                try {
                    val baseVol = if (isMuted || isVideoAudioMuted) 0f else currentVideoVolume
                    val chopVol = if (decoyAudioChopMuted) 0f else baseVol
                    mediaPlayer?.setVolume(chopVol, chopVol)
                    audioPlayer?.setVolume(chopVol, chopVol)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ((now / 135L) % 6L == 0L)) {
                        val mp = mediaPlayer
                        if (mp != null && mp.isPlaying) {
                            val p = mp.playbackParams
                            val swing = ((now / 400L) % 3L).toInt()
                            p.pitch = when (swing) {
                                0 -> 0.55f
                                1 -> 1.72f
                                else -> 0.72f
                            }
                            p.speed = when (swing) {
                                0 -> 0.62f
                                1 -> 1.48f
                                else -> 0.78f
                            }
                            mp.playbackParams = p
                        }
                    }
                } catch (_: Throwable) {}
            }
        } else if (decoyAudioStutterUntilMs > 0L && now >= decoyAudioStutterUntilMs) {
            decoyAudioStutterUntilMs = 0L
            decoyAudioChopMuted = false
            try {
                val baseVol = if (isMuted || isVideoAudioMuted) 0f else currentVideoVolume
                mediaPlayer?.setVolume(baseVol, baseVol)
                audioPlayer?.setVolume(baseVol, baseVol)
            } catch (_: Throwable) {}
            applyAudioAntiHashToPlayers()
        }

        // Decoy Glitch 1: Extreme FPS Drop Throttle (2-12 FPS)
        if (!forceRedraw && now < decoyFpsDropUntilMs) {
            val minDecoyInterval = 1000L / decoyTargetFps.coerceIn(2, 12)
            if (timeSinceLastDraw < minDecoyInterval) {
                if (hasNewFrame) {
                    try {
                        activeSt?.updateTexImage()
                        hasNewFrame = false
                    } catch (_: Throwable) {}
                }
                return
            }
        }

        totalFramesRendered++
        frameCountInSecond++
        if (now - lastFpsCalcTimeMs >= 1000L) {
            val elapsedSec = (now - lastFpsCalcTimeMs) / 1000.0f
            if (elapsedSec > 0f) {
                currentFps = frameCountInSecond / elapsedSec
            }
            frameCountInSecond = 0
            lastFpsCalcTimeMs = now
        }

        try {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            // Selalu kuras antrean SurfaceTexture agar BufferQueue MediaCodec TIDAK PERNAH macet permanen!
            val isDecoyFreezing = isDecoyHardFrozen || (!forceRedraw && now < decoyFreezeUntilMs)
            val isDecoyFlickering = !forceRedraw && now < decoyDimUntilMs
            val activeTexMatrix = if (activeSlot == 0) texMatrixA else texMatrixB
            if (hasNewFrame && activeSt != null) {
                try {
                    activeSt.updateTexImage()
                    if (!isDecoyFreezing) {
                        activeSt.getTransformMatrix(activeTexMatrix)
                        // Xiaomi GPU 1088 Stride Alignment (Removes 8-pixel padding artifact)
                        val strideCropV = com.tajasb.engine.BufferQueueManager.getStrideCropFactorV(surfaceWidth, surfaceHeight)
                        if (strideCropV < 1.0f) {
                            GlMatrix.scaleM(activeTexMatrix, 0, 1.0f, strideCropV, 1.0f)
                        }
                    }
                    hasNewFrame = false
                    val decodedNow = System.currentTimeMillis()
                    lastRealFrameTime = decodedNow
                    lastRealVideoDecodedTimeMs = decodedNow
                    consecutiveTexErrors = 0
                } catch (t: Throwable) {
                    consecutiveTexErrors++
                    if (consecutiveTexErrors > 4 && !isDecoyHardFrozen) {
                        Log.w(TAG, "Consecutive updateTexImage errors ($consecutiveTexErrors), triggering hot reload")
                        hotReloadPipeline()
                        return
                    }
                }
            }

            // Saat Freeze berlangsung: tahan eglSwapBuffers dan jeda audio agar sinkron!
            if (isDecoyFreezing) {
                try {
                    mediaPlayer?.pause()
                    audioPlayer?.pause()
                } catch (_: Throwable) {}
                return
            }

            val isGpuCorrupt = now < decoyGpuCorruptUntilMs
            val isFlipZoom = now < decoyFlipZoomUntilMs
            val intensity = when {
                isGpuCorrupt -> 0.55f
                isDecoyFlickering -> 0.35f
                antiHashMode != com.tajasb.model.AntiHashIntensity.OFF -> {
                    if (videoAntiHashPercent > 0.0f) (videoAntiHashPercent / 100f) else antiHashMode.glIntensity
                }
                else -> 0.0f
            }
            // Continuous phase progression (zero phase jump on slot switch) + smooth lerp of per-video entropy targets
            val waveDtSec = ((now - lastAntiPlWaveTimeMs).coerceIn(0L, 100L)) / 1000.0
            lastAntiPlWaveTimeMs = now
            val freqMod = 1.0 + (loopEntropySpeedBias * 2.5)
            continuousAntiPlWavePhase = (continuousAntiPlWavePhase + waveDtSec * freqMod) % 100000.0
            val wavePhase = continuousAntiPlWavePhase

            // Smoothly interpolate geometric & grain seed entropy biases (~2.2% per frame = ~2.5s smooth tripod drift)
            val lerpFactor = 0.022f
            currentEntropyScaleBias += (loopEntropyScaleBias - currentEntropyScaleBias) * lerpFactor
            currentEntropyOffsetX += (loopEntropyOffsetX - currentEntropyOffsetX) * lerpFactor
            currentEntropyOffsetY += (loopEntropyOffsetY - currentEntropyOffsetY) * lerpFactor
            currentEntropyRotationDeg += (loopEntropyRotationDeg - currentEntropyRotationDeg) * lerpFactor
            currentEntropySeedOffset += (loopEntropySeedOffset - currentEntropySeedOffset) * 0.032f
            val timeSeed = (wavePhase.toFloat()) + currentEntropySeedOffset

            val aperiodicWave = (
                kotlin.math.sin(wavePhase * 0.173) * kotlin.math.cos(wavePhase * 0.293) +
                kotlin.math.sin(wavePhase * 0.071) * 0.5
            ).toFloat()
            val colorShift = when {
                isGpuCorrupt -> if ((now / 160L) % 2L == 0L) -0.75f else 0.78f
                isDecoyFlickering -> when (((now / 95L) % 3L).toInt()) {
                    0 -> -0.88f // Kedip gelap
                    1 -> 0.65f  // Kedip silau
                    else -> 0.0f
                }
                intensity > 0.0005f -> (aperiodicWave * (intensity * 0.45f)).coerceIn(-0.025f, 0.025f)
                else -> 0.0f
            }
            val breathingScale = if (intensity > 0.0005f) {
                val dynamicZoom = (aperiodicWave * 0.0065f * (intensity / 0.015f))
                (1.0f + currentEntropyScaleBias + dynamicZoom).coerceIn(1.004f, 1.035f)
            } else 1.0f
            val microOffsetX = if (intensity > 0.0005f) {
                currentEntropyOffsetX + (kotlin.math.sin(wavePhase * 0.113) * 0.0014).toFloat()
            } else 0.0f
            val microOffsetY = if (intensity > 0.0005f) {
                currentEntropyOffsetY + (kotlin.math.cos(wavePhase * 0.137) * 0.0014).toFloat()
            } else 0.0f
            val microRotationDeg = if (intensity > 0.0005f) {
                currentEntropyRotationDeg + (kotlin.math.sin(wavePhase * 0.089) * 0.045).toFloat()
            } else 0.0f

            val dna = currentSessionDna
            val dnaActive = isSessionDnaEnabled
            val effAspectWarpX = if (dnaActive) dna.aspectWarpX else 1.0f
            val effAspectWarpY = if (dnaActive) dna.aspectWarpY else 1.0f

            // Dynamic viewport query using cached arrays only when needed
            if (mvpNeedsUpdate || surfaceWidth <= 0 || surfaceHeight <= 0 || intensity > 0.0005f || dnaActive || isDnaZoomEnabled || isFlipZoom || isGpuCorrupt) {
                EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, queryWidthArr, 0)
                EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, queryHeightArr, 0)
                if (queryWidthArr[0] > 0) surfaceWidth = queryWidthArr[0]
                if (queryHeightArr[0] > 0) surfaceHeight = queryHeightArr[0]
                updateMvpMatrix(breathingScale, microOffsetX, microOffsetY, microRotationDeg, effAspectWarpX, effAspectWarpY)
                mvpNeedsUpdate = false
            }

            GlShaderProgram.drawOesVideoQuad(
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                scaleMode = scaleMode,
                programId = programId,
                activeTexId = activeTexId,
                sTextureLoc = sTextureLoc,
                uMVPMatrixLoc = uMVPMatrixLoc,
                uTexMatrixLoc = uTexMatrixLoc,
                mvpMatrix = mvpMatrix,
                texMatrix = activeTexMatrix,
                aPositionLoc = aPositionLoc,
                aTextureCoordLoc = aTextureCoordLoc,
                vertexBuffer = vertexBuffer,
                textureBuffer = textureBuffer,
                antiHashIntensity = intensity,
                timeSeed = timeSeed,
                colorShift = colorShift,
                uAntiHashIntensityLoc = uAntiHashIntensityLoc,
                uTimeSeedLoc = uTimeSeedLoc,
                uColorShiftLoc = uColorShiftLoc,
                sessionGamma = if (dnaActive) dna.gamma else 1.0f,
                sessionContrast = if (dnaActive) dna.contrast else 1.0f,
                sessionWarmth = if (dnaActive) dna.warmth else 0.0f,
                sessionTint = if (dnaActive) dna.tint else 0.0f,
                dctFreqX = if (dnaActive) dna.dctFreqX else 9.4247f,
                dctFreqY = if (dnaActive) dna.dctFreqY else 9.4247f,
                lightDirX = if (dnaActive) dna.lightDirX else 0.0f,
                lightDirY = if (dnaActive) dna.lightDirY else 0.0f,
                uSessionGammaLoc = uSessionGammaLoc,
                uSessionContrastLoc = uSessionContrastLoc,
                uSessionWarmthLoc = uSessionWarmthLoc,
                uSessionTintLoc = uSessionTintLoc,
                uDctFreqLoc = uDctFreqLoc,
                uLightDirLoc = uLightDirLoc
            )

            // Decoy GPU Corruption Overlay Bars & Sudden Blackout Flicker
            if (isGpuCorrupt && surfaceWidth > 0 && surfaceHeight > 0) {
                val phase = ((now / 120L) % 6L).toInt()
                GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
                if (phase == 0) {
                    // Sudden full green or blackout frame
                    GLES20.glScissor(0, 0, surfaceWidth, surfaceHeight)
                    if ((now / 240L) % 2L == 0L) {
                        GLES20.glClearColor(0.0f, 0.85f, 0.12f, 1.0f)
                    } else {
                        GLES20.glClearColor(0.02f, 0.02f, 0.02f, 1.0f)
                    }
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                } else {
                    // Corrupted green & magenta horizontal GPU artifact bands
                    val bandH = (surfaceHeight / 5).coerceAtLeast(16)
                    val y1 = ((now * 3L) % surfaceHeight.toLong()).toInt()
                    val y2 = ((now * 7L + surfaceHeight / 2) % surfaceHeight.toLong()).toInt()
                    GLES20.glScissor(0, y1, surfaceWidth, bandH)
                    GLES20.glClearColor(0.0f, 0.92f, 0.18f, 1.0f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    GLES20.glScissor(0, y2, surfaceWidth, bandH / 2)
                    GLES20.glClearColor(0.90f, 0.0f, 0.75f, 1.0f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                }
                GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
            }

            // Track playback position
            try {
                val curPos = mediaPlayer?.currentPosition ?: 0
                if (curPos > 0) {
                    lastKnownPositionMs = curPos
                }
            } catch (_: Throwable) {}

            // 1. Render Persistent Custom Sticker (STICKER) with imperceptible micro-breathing to break static overlay hashing
            val sticker = currentSticker
            if (sticker != null) {
                if (sticker.isRunningText && (now - lastStickerRunningUpdateMs) >= 32L) {
                    lastStickerRunningUpdateMs = now
                    val newBmp = OverlayBannerBitmapGenerator.refreshRunningStickerBitmap(sticker, surfaceWidth, surfaceHeight, now)
                    stickerBitmap?.recycle()
                    stickerBitmap = newBmp
                    stickerQuadWidth = newBmp.width
                    stickerQuadHeight = newBmp.height
                    stickerBitmapNeedsUpload = true
                }
                if (stickerBitmapNeedsUpload && stickerBitmap != null) {
                    uploadStickerTexture(stickerBitmap!!)
                    stickerBitmapNeedsUpload = false
                }
                val stSlideX = if (intensity > 0.0005f) (dna.anchorOffsetX * 1.4f + kotlin.math.sin(timeSeed.toDouble() * 0.21) * 0.0032).toFloat() else 0f
                val stSlideY = if (intensity > 0.0005f) (dna.anchorOffsetY * 1.4f + kotlin.math.cos(timeSeed.toDouble() * 0.17) * 0.0032).toFloat() else 0f
                val stAlpha = if (intensity > 0.0005f) (0.985f + (kotlin.math.sin(timeSeed.toDouble() * 0.29) * 0.015).toFloat()).coerceIn(0.96f, 1.0f) else 1.0f
                drawStickerQuad(sticker, stAlpha, stickerTextureId, stickerQuadWidth, stickerQuadHeight, stSlideX, stSlideY)
            }

            // 1b. Render Persistent Custom Watermark (WATERMARK) with imperceptible micro-breathing + Session DNA anchor shift
            val watermark = currentWatermark
            if (watermark != null) {
                if (watermark.isRunningText && (now - lastWatermarkRunningUpdateMs) >= 32L) {
                    lastWatermarkRunningUpdateMs = now
                    val newBmp = OverlayBannerBitmapGenerator.refreshRunningStickerBitmap(watermark, surfaceWidth, surfaceHeight, now)
                    watermarkBitmap?.recycle()
                    watermarkBitmap = newBmp
                    watermarkQuadWidth = newBmp.width
                    watermarkQuadHeight = newBmp.height
                    watermarkBitmapNeedsUpload = true
                }
                if (watermarkBitmapNeedsUpload && watermarkBitmap != null) {
                    uploadWatermarkTexture(watermarkBitmap!!)
                    watermarkBitmapNeedsUpload = false
                }
                val wmSlideX = if (intensity > 0.0005f) (-dna.anchorOffsetX * 1.3f + kotlin.math.cos(timeSeed.toDouble() * 0.23) * 0.0035).toFloat() else 0f
                val wmSlideY = if (intensity > 0.0005f) (-dna.anchorOffsetY * 1.3f + kotlin.math.sin(timeSeed.toDouble() * 0.19) * 0.0035).toFloat() else 0f
                val wmAlpha = if (intensity > 0.0005f) (0.984f + (kotlin.math.cos(timeSeed.toDouble() * 0.31) * 0.016).toFloat()).coerceIn(0.95f, 1.0f) else 1.0f
                drawStickerQuad(watermark, wmAlpha, watermarkTextureId, watermarkQuadWidth, watermarkQuadHeight, wmSlideX, wmSlideY)
            }

            // 1c. Render Persistent Automatic Time Capsule (AUTO_TIME) with live second/minute ticking
            val autoTime = currentAutoTime
            if (autoTime != null) {
                val realWallTimeMs = System.currentTimeMillis()
                val curSecTick = realWallTimeMs / 1000L
                if (curSecTick != lastAutoTimeSecondTick) {
                    lastAutoTimeSecondTick = curSecTick
                    val newBmp = OverlayBannerBitmapGenerator.refreshAutoTimeBitmap(autoTime, surfaceWidth, surfaceHeight, realWallTimeMs)
                    autoTimeBitmap?.recycle()
                    autoTimeBitmap = newBmp
                    autoTimeQuadWidth = newBmp.width
                    autoTimeQuadHeight = newBmp.height
                    autoTimeBitmapNeedsUpload = true
                }
                if (autoTimeBitmapNeedsUpload && autoTimeBitmap != null) {
                    uploadAutoTimeTexture(autoTimeBitmap!!)
                    autoTimeBitmapNeedsUpload = false
                }
                val atSlideX = if (intensity > 0.0005f) (dna.anchorOffsetX * 1.1f + kotlin.math.sin(timeSeed.toDouble() * 0.18) * 0.0028).toFloat() else 0f
                val atSlideY = if (intensity > 0.0005f) (dna.anchorOffsetY * 1.1f + kotlin.math.cos(timeSeed.toDouble() * 0.15) * 0.0028).toFloat() else 0f
                val atAlpha = if (intensity > 0.0005f) (0.986f + (kotlin.math.sin(timeSeed.toDouble() * 0.27) * 0.014).toFloat()).coerceIn(0.96f, 1.0f) else 1.0f
                drawStickerQuad(autoTime, atAlpha, autoTimeTextureId, autoTimeQuadWidth, autoTimeQuadHeight, atSlideX, atSlideY)
            }

            // 1d. Render Dedicated Quran Khatam 30 Juz Overlay (QURAN_KHATAM) + Unified Event Stack
            val quran = currentQuranOverlay
            if (quran != null) {
                var eventExpired = false
                val curDoa = currentOverlay
                if (curDoa != null && curDoa.bannerType == "COMMENT" && (now - curDoa.startTimeMs >= curDoa.durationMs)) {
                    currentOverlay = null
                    eventExpired = true
                }
                val curGift = currentGiftOverlay
                if (curGift != null && (now - curGift.startTimeMs >= curGift.durationMs)) {
                    currentGiftOverlay = null
                    eventExpired = true
                }
                val curLike = currentLikeOverlay
                if (curLike != null && (now - curLike.startTimeMs >= curLike.durationMs)) {
                    currentLikeOverlay = null
                    eventExpired = true
                }
                if (eventExpired) {
                    rebuildQuranBitmapWithEvents()
                }
                if (quranBitmapNeedsUpload && quranBitmap != null) {
                    uploadQuranTexture(quranBitmap!!)
                    quranBitmapNeedsUpload = false
                }
                drawStickerQuad(quran, 1.0f, quranTextureId, quranQuadWidth, quranQuadHeight)
            }

            // 1e & 1f. Render Unified 3-Column Top Dashboard Overlay standalone ONLY when QURAN_KHATAM is not active
            // (When QURAN_KHATAM is active, the Unified 3-Column Dashboard is already united at the top of the Khatam card!)
            val eventStats = currentEventStatsOverlay
            val quranStats = currentQuranStatsOverlay
            if (quran == null) {
                if (eventStats != null) {
                    if (eventStatsBitmapNeedsUpload && eventStatsBitmap != null) {
                        uploadEventStatsTexture(eventStatsBitmap!!)
                        eventStatsBitmapNeedsUpload = false
                    }
                    drawStickerQuad(eventStats, 1.0f, eventStatsTextureId, eventStatsQuadWidth, eventStatsQuadHeight)
                } else if (quranStats != null) {
                    if (quranStatsBitmapNeedsUpload && quranStatsBitmap != null) {
                        uploadQuranStatsTexture(quranStatsBitmap!!)
                        quranStatsBitmapNeedsUpload = false
                    }
                    drawStickerQuad(quranStats, 1.0f, quranStatsTextureId, quranStatsQuadWidth, quranStatsQuadHeight)
                }
            }

            // 1f-bis. Render Dedicated Dynamic Top 10 Tap-Tap Love Overlay (TOP_LIKERS)
            val topLikers = currentTopLikersOverlay
            if (topLikers != null) {
                if (topLikersBitmapNeedsUpload && topLikersBitmap != null) {
                    uploadTopLikersTexture(topLikersBitmap!!)
                    topLikersBitmapNeedsUpload = false
                }
                drawStickerQuad(topLikers, 1.0f, topLikersTextureId, topLikersQuadWidth, topLikersQuadHeight)
            }

            // 1f-ter. Render Dedicated Tug of War / Team Battle Overlay (TUG_OF_WAR)
            val tow = currentTugOfWarOverlay
            if (tow != null) {
                if (tugOfWarBitmapNeedsUpload && tugOfWarBitmap != null) {
                    uploadTugOfWarTexture(tugOfWarBitmap!!)
                    tugOfWarBitmapNeedsUpload = false
                }
                if (tugOfWarTextureId != 0) {
                    drawStickerQuad(tow, 1.0f, tugOfWarTextureId, tugOfWarQuadWidth, tugOfWarQuadHeight)
                }
            }

            // 1g. Render Dedicated Live Event Tap Like Overlay (EVENT_LIKE) when Quran Khatam is NOT active
            val likeBanner = currentLikeOverlay
            if (likeBanner != null) {
                val elapsed = System.currentTimeMillis() - likeBanner.startTimeMs
                if (elapsed < likeBanner.durationMs) {
                    if (quran == null) {
                        if (likeBitmapNeedsUpload && likeBitmap != null) {
                            uploadLikeTexture(likeBitmap!!)
                            likeBitmapNeedsUpload = false
                        }
                        val (slideOffsetX, slideOffsetY, alpha) = OverlayBannerBitmapGenerator.computeBannerSlideAndAlpha(likeBanner, elapsed)
                        drawStickerQuad(likeBanner, alpha, likeTextureId, likeQuadWidth, likeQuadHeight, slideOffsetX, slideOffsetY)
                    }
                } else {
                    currentLikeOverlay = null
                }
            }

            // 1i. Render Live Comment / Doa / Buyer Overlay Banner when not united into Quran Khatam card
            val banner = currentOverlay
            if (banner != null) {
                val elapsed = System.currentTimeMillis() - banner.startTimeMs
                if (elapsed < banner.durationMs) {
                    if (quran == null || banner.bannerType == "BUYER") {
                        if (overlayBitmapNeedsUpload && overlayBitmap != null) {
                            uploadOverlayTexture(overlayBitmap!!)
                            overlayBitmapNeedsUpload = false
                        }
                        val (slideOffsetX, slideOffsetY, alpha) = OverlayBannerBitmapGenerator.computeBannerSlideAndAlpha(banner, elapsed)
                        drawOverlayQuad(banner, alpha, slideOffsetX, slideOffsetY)
                    }
                } else {
                    currentOverlay = null
                }
            }

            // 1h. Render Dedicated Live Event Gift Overlay (EVENT_GIFT) when Quran Khatam is NOT active
            // RENDERED AFTER currentOverlay so the gift is GUARANTEED to be drawn ON TOP of comments and buyer overlays!
            val giftBanner = currentGiftOverlay
            if (giftBanner != null) {
                val elapsed = System.currentTimeMillis() - giftBanner.startTimeMs
                if (elapsed < giftBanner.durationMs) {
                    if (quran == null) {
                        if (giftBitmapNeedsUpload && giftBitmap != null) {
                            uploadGiftTexture(giftBitmap!!)
                            giftBitmapNeedsUpload = false
                        }
                        val (slideOffsetX, slideOffsetY, alpha) = OverlayBannerBitmapGenerator.computeBannerSlideAndAlpha(giftBanner, elapsed)
                        drawStickerQuad(giftBanner, alpha, giftTextureId, giftQuadWidth, giftQuadHeight, slideOffsetX, slideOffsetY)
                    }
                } else {
                    currentGiftOverlay = null
                }
            }

            // 2b. Render Floating Viewer Avatar Bubbles (AVATAR_BUBBLE) rising with sine-wave motion
            if (activeAvatarBubbles.isNotEmpty()) {
                val nowBubbleMs = System.currentTimeMillis()
                val iter = activeAvatarBubbles.iterator()
                while (iter.hasNext()) {
                    val slot = iter.next()
                    val elapsed = nowBubbleMs - slot.banner.startTimeMs
                    if (elapsed < 0L) {
                        // Staggered bubble waiting for its launch slot so bubbles never overlap vertically
                        continue
                    }
                    if (elapsed < slot.banner.durationMs) {
                        if (slot.needsUpload && slot.bitmap != null) {
                            slot.textureId = GlShaderProgram.upload2DTexture(slot.textureId, slot.bitmap!!)
                            slot.bitmap?.recycle()
                            slot.bitmap = null
                            slot.needsUpload = false
                        }
                        if (slot.textureId != 0 && overlayProgramId != 0) {
                            val alpha = OverlayBannerBitmapGenerator.computeAvatarBubbleAlpha(elapsed, slot.banner.durationMs)
                            val coords = OverlayBannerBitmapGenerator.computeAvatarBubbleQuadNdcCoords(
                                banner = slot.banner,
                                quadWidth = slot.quadWidth,
                                quadHeight = slot.quadHeight,
                                surfaceWidth = surfaceWidth,
                                surfaceHeight = surfaceHeight,
                                currentRotation = currentRotation,
                                elapsedMs = elapsed,
                                phaseSeed = slot.phaseSeed
                            )
                            GlShaderProgram.drawTexturedQuadNdc(
                                overlayProgramId = overlayProgramId,
                                textureId = slot.textureId,
                                alpha = alpha,
                                coords = coords,
                                overlayVertexBuffer = overlayVertexBuffer,
                                overlayTexCoordsBuffer = overlayTexCoordsBuffer,
                                overlayMvpMatrix = overlayMvpMatrix,
                                overlayPositionLoc = overlayPositionLoc,
                                overlayTexCoordLoc = overlayTexCoordLoc,
                                overlayTextureLoc = overlayTextureLoc,
                                overlayAlphaLoc = overlayAlphaLoc,
                                overlayMvpMatrixLoc = overlayMvpMatrixLoc
                            )
                        }
                    } else {
                        if (slot.textureId != 0) {
                            GLES20.glDeleteTextures(1, intArrayOf(slot.textureId), 0)
                            slot.textureId = 0
                        }
                        slot.bitmap?.recycle()
                        slot.bitmap = null
                        iter.remove()
                    }
                }
            }

            // 3. Render Virtual Camera Debug OSD Stamp (Hanya jika VIP & OSD diaktifkan; nonaktif di Gerbang Decoy)
            if (isDebugOsdEnabled && !isDecoyLoopGateEnabled) {
                drawDebugOsdQuad()
            }

            // Monotonic A/V Presentation Time Stamp Sync via AvSyncEngine:
            // Uses SystemClock.elapsedRealtimeNanos() with natural ±150µs micro-jitter and drift compensation (<5ms)
            try {
                val ptsNs = avSyncEngine.getNextFrameTimestamp()
                lastPresentationTimeNs = ptsNs
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
            } catch (_: Throwable) {}

            val swapped = EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            if (swapped) {
                lastFrameDrawTime = System.currentTimeMillis()
            } else {
                val err = EGL14.eglGetError()
                Log.w(TAG, "eglSwapBuffers failed with error $err, immediately recovering EGL window surface...")
                recreateEglSurface()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in drawFrame: ${t.message}")
        }
    }

    /**
     * Seamlessly migrates EGL rendering to a new target camera surface.
     * Prevents video playback freezes when TikTok recreates surfaces or switches preview modes.
     */
    fun updateTargetSurface(newSurface: Surface) {
        if (!newSurface.isValid) return
        val handler = glHandler ?: return
        handler.post {
            if (this.targetSurface == newSurface && eglSurface != EGL14.EGL_NO_SURFACE) {
                return@post
            }
            try {
                Log.i(TAG, "Migrating GlVideoRenderer to new target Surface...")
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EglSurfaceManager.destroySurface(eglDisplay, eglSurface)
                    eglSurface = EGL14.EGL_NO_SURFACE
                }
                this.targetSurface = newSurface
                if (newSurface.isValid) {
                    eglSurface = EglSurfaceManager.createWindowSurfaceForTarget(eglDisplay, eglContext, newSurface)
                    val w = IntArray(1)
                    val h = IntArray(1)
                    EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, w, 0)
                    EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, h, 0)
                    if (w[0] > 0) surfaceWidth = w[0]
                    if (h[0] > 0) surfaceHeight = h[0]
                    drawFrame(forceRedraw = true)
                    Log.i(TAG, "Successfully migrated EGL rendering to new surface (${surfaceWidth}x${surfaceHeight})")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to update target surface: ${t.message}", t)
            }
        }
    }

    private fun recreateEglSurface() {
        val handler = glHandler ?: return
        if (Looper.myLooper() != handler.looper) {
            handler.post { recreateEglSurface() }
            return
        }
        try {
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EglSurfaceManager.destroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (targetSurface.isValid && eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT) {
                val newSurf = EglSurfaceManager.createWindowSurfaceForTarget(eglDisplay, eglContext, targetSurface)
                if (newSurf != EGL14.EGL_NO_SURFACE) {
                    eglSurface = newSurf
                    drawFrame(forceRedraw = true)
                    Log.i(TAG, "EGL Surface recreated successfully")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to recreate EGL surface: ${e.message}")
        }
    }

    fun setLightweightMode(enabled: Boolean) {
        this.isLightweightMode = enabled
        glHandler?.post {
            mvpNeedsUpdate = true
            drawFrame(forceRedraw = true)
        }
    }

    fun setRotation(rotation: Int) {
        currentRotation = rotation
        mvpNeedsUpdate = true
        glHandler?.post {
            drawFrame(forceRedraw = true)
        }
    }

    fun setMirror(mirror: Boolean) {
        isMirrored = mirror
        mvpNeedsUpdate = true
        glHandler?.post {
            drawFrame(forceRedraw = true)
        }
    }

    fun setVerticalFlip(flip: Boolean) {
        isVerticalFlipped = flip
        mvpNeedsUpdate = true
        glHandler?.post {
            drawFrame(forceRedraw = true)
        }
    }

    fun setUserZoom(zoom: Float) {
        val safeZoom = zoom.coerceIn(0.5f, 3.0f)
        if (userZoom != safeZoom) {
            userZoom = safeZoom
            mvpNeedsUpdate = true
            glHandler?.post {
                drawFrame(forceRedraw = true)
            }
        }
    }

    fun setScaleMode(mode: ScaleMode) {
        scaleMode = mode
        mvpNeedsUpdate = true
        glHandler?.post {
            drawFrame(forceRedraw = true)
        }
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        glHandler?.post {
            try {
                if (paused) {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.pause()
                    }
                } else {
                    val vol = if (isMuted || isVideoAudioMuted) 0f else 1f
                    mediaPlayer?.setVolume(vol, vol)
                    if (mediaPlayer?.isPlaying == false) {
                        mediaPlayer?.start()
                        applyAudioAntiHashToPlayer(mediaPlayer)
                    }
                }
                updateFpsPacing()
            } catch (_: Throwable) {}
        }
    }

    fun setVideoAudioMuted(muted: Boolean) {
        isVideoAudioMuted = muted
        glHandler?.post {
            try {
                val vol = if (isMuted || isVideoAudioMuted) 0f else currentVideoVolume
                mediaPlayer?.setVolume(vol, vol)
            } catch (_: Throwable) {}
        }
    }

    fun setVideoVolume(volume: Float) {
        currentVideoVolume = volume.coerceIn(0f, 1f)
        glHandler?.post {
            try {
                val vol = if (isMuted || isVideoAudioMuted) 0f else currentVideoVolume
                mediaPlayer?.setVolume(vol, vol)
            } catch (_: Throwable) {}
        }
    }

    fun seekTo(positionMs: Long) {
        glHandler?.post {
            try {
                mediaPlayer?.seekTo(positionMs.toInt())
            } catch (_: Throwable) {}
        }
    }

    fun setMp3AudioPaused(paused: Boolean) {
        isMp3AudioPaused = paused
        glHandler?.post {
            try {
                if (paused) {
                    if (audioPlayer?.isPlaying == true) {
                        audioPlayer?.pause()
                    }
                } else {
                    val vol = if (isMuted) 0f else 1f
                    audioPlayer?.setVolume(vol, vol)
                    if (audioPlayer?.isPlaying == false && !isMuted) {
                        audioPlayer?.start()
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun setupAudioPlayer() {
        // MP3 music playback is centrally managed by StandaloneMusicPlayer in Tajasb / FloatingControlService
        // to prevent duplicate parallel playback over the system STREAM_MUSIC channel.
        try {
            audioPlayer?.apply {
                try { stop() } catch (_: Throwable) {}
                try { reset() } catch (_: Throwable) {}
                try { release() } catch (_: Throwable) {}
            }
            audioPlayer = null
        } catch (_: Throwable) {}
    }

    fun setAudioPaused(paused: Boolean) {
        setMp3AudioPaused(paused)
    }

    fun setAudioFile(newAudioFile: File?) {
        this.activeAudioFile = newAudioFile
        val handler = glHandler
        if (handler != null) {
            handler.post {
                setupAudioPlayer()
            }
        }
    }

    fun switchAudioFile(newAudioFile: File?) {
        setAudioFile(newAudioFile)
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        glHandler?.post {
            try {
                val vidVol = if (muted || isVideoAudioMuted) 0f else 1f
                mediaPlayer?.setVolume(vidVol, vidVol)
                val mp3Vol = if (muted) 0f else 1f
                audioPlayer?.setVolume(mp3Vol, mp3Vol)
            } catch (_: Throwable) {}
        }
    }

    fun release() {
        isRunning = false
        watchdogRunnable?.let { watchdogHandler?.removeCallbacks(it) }
        watchdogRunnable = null
        watchdogThread?.quitSafely()
        watchdogThread = null
        watchdogHandler = null

        heartbeatRunnable?.let { glHandler?.removeCallbacks(it) }
        heartbeatRunnable = null

        // Immediately mute, stop, detach surfaces, and release all players
        try {
            mediaPlayer?.setVolume(0f, 0f)
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
            mediaPlayer?.setSurface(null)
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Throwable) {}
        mediaPlayer = null

        try {
            preparingPlayer?.setVolume(0f, 0f)
            if (preparingPlayer?.isPlaying == true) preparingPlayer?.stop()
            preparingPlayer?.setSurface(null)
            preparingPlayer?.reset()
            preparingPlayer?.release()
        } catch (_: Throwable) {}
        preparingPlayer = null

        try {
            standbyPlayer?.setVolume(0f, 0f)
            if (standbyPlayer?.isPlaying == true) standbyPlayer?.stop()
            standbyPlayer?.setSurface(null)
            standbyPlayer?.reset()
            standbyPlayer?.release()
        } catch (_: Throwable) {}
        standbyPlayer = null

        try {
            audioPlayer?.setVolume(0f, 0f)
            if (audioPlayer?.isPlaying == true) audioPlayer?.stop()
            audioPlayer?.reset()
            audioPlayer?.release()
        } catch (_: Throwable) {}
        audioPlayer = null

        glHandler?.post {
            releaseGl()
        }
        handlerThread?.quitSafely()
        handlerThread = null
        glHandler = null
    }

    private fun releasePlayerAsync(p: MediaPlayer?) {
        if (p == null) return
        try { p.setOnCompletionListener(null) } catch (_: Throwable) {}
        try { p.setOnErrorListener(null) } catch (_: Throwable) {}
        try { p.setOnPreparedListener(null) } catch (_: Throwable) {}
        try { p.setOnInfoListener(null) } catch (_: Throwable) {}
        try { p.setOnVideoSizeChangedListener(null) } catch (_: Throwable) {}
        try { p.setVolume(0f, 0f) } catch (_: Throwable) {}
        try { p.setSurface(null) } catch (_: Throwable) {} // CRITICAL: Detach native surface immediately
        playerReleaseExecutor.execute {
            try { if (p.isPlaying) p.stop() } catch (_: Throwable) {}
            try { p.reset() } catch (_: Throwable) {}
            try { p.release() } catch (_: Throwable) {}
        }
    }

    private fun releaseGl() {
        try {
            releasePlayerAsync(audioPlayer)
            audioPlayer = null
            releasePlayerAsync(mediaPlayer)
            mediaPlayer = null
            releasePlayerAsync(preparingPlayer)
            preparingPlayer = null
            releasePlayerAsync(standbyPlayer)
            standbyPlayer = null
            activePfd?.let { try { it.close() } catch (_: Throwable) {} }
            activePfd = null
            preparingPfd?.let { try { it.close() } catch (_: Throwable) {} }
            preparingPfd = null
            standbyPfd?.let { try { it.close() } catch (_: Throwable) {} }
            standbyPfd = null

            videoSurfaceA?.release()
            videoSurfaceA = null
            videoSurfaceTextureA?.release()
            videoSurfaceTextureA = null

            videoSurfaceB?.release()
            videoSurfaceB = null
            videoSurfaceTextureB?.release()
            videoSurfaceTextureB = null

            if (programId != 0) {
                GLES20.glDeleteProgram(programId)
                programId = 0
            }
            if (oesTextureIdA != 0 || oesTextureIdB != 0) {
                GLES20.glDeleteTextures(2, intArrayOf(oesTextureIdA, oesTextureIdB), 0)
                oesTextureIdA = 0
                oesTextureIdB = 0
            }

            if (overlayProgramId != 0) {
                GLES20.glDeleteProgram(overlayProgramId)
                overlayProgramId = 0
            }
            if (overlayTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(overlayTextureId), 0)
                overlayTextureId = 0
            }
            if (stickerTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(stickerTextureId), 0)
                stickerTextureId = 0
            }
            if (watermarkTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(watermarkTextureId), 0)
                watermarkTextureId = 0
            }
            if (autoTimeTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(autoTimeTextureId), 0)
                autoTimeTextureId = 0
            }
            if (topLikersTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(topLikersTextureId), 0)
                topLikersTextureId = 0
            }
            if (tugOfWarTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(tugOfWarTextureId), 0)
                tugOfWarTextureId = 0
            }
            tugOfWarBitmap?.recycle()
            tugOfWarBitmap = null
            currentTugOfWarOverlay = null
            overlayBitmap?.recycle()
            overlayBitmap = null
            currentOverlay = null
            stickerBitmap?.recycle()
            stickerBitmap = null
            currentSticker = null
            watermarkBitmap?.recycle()
            watermarkBitmap = null
            currentWatermark = null
            autoTimeBitmap?.recycle()
            autoTimeBitmap = null
            currentAutoTime = null
            topLikersBitmap?.recycle()
            topLikersBitmap = null
            currentTopLikersOverlay = null
            stopAvatarBubbleAnimation()
            for (slot in activeAvatarBubbles) {
                if (slot.textureId != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(slot.textureId), 0)
                    slot.textureId = 0
                }
                slot.bitmap?.recycle()
                slot.bitmap = null
            }
            activeAvatarBubbles.clear()

            EglSurfaceManager.releaseEgl(eglDisplay, eglContext, eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
            eglContext = EGL14.EGL_NO_CONTEXT
            eglDisplay = EGL14.EGL_NO_DISPLAY
        } catch (_: Throwable) {}
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int =
        GlShaderProgram.createProgram(vertexCode, fragmentCode)

    /**
     * Injects live comment, prayer (doa), or buyer notification directly into
     * the virtual camera OpenGL pipeline so all live stream viewers on TikTok / Shopee can see it.
     */
    fun showOverlayBanner(
        senderName: String,
        content: String,
        isGift: Boolean = false,
        bannerType: String = "COMMENT",
        iconMode: String = "PRAYER",
        customPrefix: String = "",
        formatMode: String = "PREFIX_AND_COMMENT",
        durationMs: Long = 4000L,
        posXpct: Float = 0.50f,
        posYpct: Float = 0.35f,
        bannerRotation: Int = 0,
        bannerScale: Float = 1.0f,
        isBannerMirrored: Boolean = false,
        entryDirection: String = "LEFT",
        capsuleCornerRadius: Int = 39,
        capsulePaddingH: Int = 27,
        capsulePaddingV: Int = 2,
        capsuleAlpha: Float = 0.75f,
        capsuleOutlineWidth: Int = 0,
        capsuleOutlineColor: String = "#FFFFFF",
        capsuleBgColor: String = "#ff941a",
        fontSizeSp: Float = 16.0f,
        cartIconSizeDp: Float = 21.0f,
        stikerOutlineWidth: Int = 2,
        stikerOutlineColor: String = "#FFFFFF",
        stikerDisplayStyle: String = "WITH_BLOCK",
        isRunningText: Boolean = false,
        runningTextSpeed: Float = 1.0f,
        runningTextDirection: String = "LEFT"
    ) {
        val handler = glHandler ?: return
        handler.post {
            try {
                val isSticker = (bannerType == "STICKER")
                val isWatermark = (bannerType == "WATERMARK")
                val isAutoTime = (bannerType == "AUTO_TIME")
                val isQuran = (bannerType == "QURAN_KHATAM")
                val isQuranStats = (bannerType == "QURAN_STATS")
                val isEventStats = (bannerType == "EVENT_STATS")
                val isTopLikers = (bannerType == "TOP_LIKERS")
                val isLike = (bannerType == "EVENT_LIKE")
                val isEventGift = (bannerType == "EVENT_GIFT")
                val isTugOfWar = (bannerType == "TUG_OF_WAR")
                val isPersistent = (isSticker || isWatermark || isAutoTime || isQuran || isQuranStats || isEventStats || isTopLikers || isTugOfWar || durationMs >= 86400000L)
                val effectiveDur = if (isPersistent) {
                    86400000L // 24 hours / permanent until disabled
                } else {
                    durationMs.coerceIn(1000L, 60000L)
                }
                val banner = OverlayBannerData(
                    senderName = senderName.trim().ifBlank { "Penonton" },
                    content = content.trim(),
                    isGift = isGift,
                    bannerType = bannerType,
                    iconMode = iconMode,
                    customPrefix = customPrefix.trim(),
                    formatMode = formatMode,
                    durationMs = effectiveDur,
                    posXpct = posXpct.coerceIn(0.00f, 1.00f),
                    posYpct = posYpct.coerceIn(0.00f, 1.00f),
                    bannerRotation = bannerRotation,
                    bannerScale = bannerScale.coerceIn(0.4f, 3.0f),
                    isBannerMirrored = isBannerMirrored,
                    entryDirection = entryDirection,
                    capsuleCornerRadius = capsuleCornerRadius,
                    capsulePaddingH = capsulePaddingH,
                    capsulePaddingV = capsulePaddingV,
                    capsuleAlpha = capsuleAlpha,
                    capsuleOutlineWidth = capsuleOutlineWidth,
                    capsuleOutlineColor = capsuleOutlineColor,
                    capsuleBgColor = capsuleBgColor,
                    fontSizeSp = fontSizeSp,
                    cartIconSizeDp = cartIconSizeDp,
                    stikerOutlineWidth = stikerOutlineWidth,
                    stikerOutlineColor = stikerOutlineColor,
                    stikerDisplayStyle = stikerDisplayStyle,
                    isRunningText = isRunningText,
                    runningTextSpeed = runningTextSpeed,
                    runningTextDirection = runningTextDirection,
                    startTimeMs = System.currentTimeMillis()
                )
                val viewW = minOf(surfaceWidth, surfaceHeight).coerceAtLeast(720)
                val viewH = maxOf(surfaceWidth, surfaceHeight).coerceAtLeast(1280)
                val maxW = (viewW * 0.96f).toInt().coerceIn(320, 1600)
                val density = viewH / 1920f
                val baseH = (72f * density).toInt()
                val paddingVDeltaPx = if (!isPersistent) (banner.capsulePaddingV * density * 1.8f).toInt() else 0
                val h = (baseH + paddingVDeltaPx).coerceIn((12f * density).toInt().coerceAtLeast(10), 130)
                val bmp = createBannerBitmap(banner, maxW, h)

                if (isAutoTime) {
                    autoTimeBitmap?.recycle()
                    autoTimeBitmap = bmp
                    autoTimeQuadWidth = bmp.width
                    autoTimeQuadHeight = bmp.height
                    autoTimeBitmapNeedsUpload = true
                    lastAutoTimeSecondTick = System.currentTimeMillis() / 1000L
                    currentAutoTime = banner
                } else if (isWatermark) {
                    watermarkBitmap?.recycle()
                    watermarkBitmap = bmp
                    watermarkQuadWidth = bmp.width
                    watermarkQuadHeight = bmp.height
                    watermarkBitmapNeedsUpload = true
                    currentWatermark = banner
                } else if (isSticker) {
                    stickerBitmap?.recycle()
                    stickerBitmap = bmp
                    stickerQuadWidth = bmp.width
                    stickerQuadHeight = bmp.height
                    stickerBitmapNeedsUpload = true
                    currentSticker = banner
                } else if (isQuran) {
                    quranBitmap?.recycle()
                    quranBitmap = bmp
                    quranQuadWidth = bmp.width
                    quranQuadHeight = bmp.height
                    quranBitmapNeedsUpload = true
                    currentQuranOverlay = banner
                } else if (isQuranStats) {
                    quranStatsBitmap?.recycle()
                    quranStatsBitmap = bmp
                    quranStatsQuadWidth = bmp.width
                    quranStatsQuadHeight = bmp.height
                    quranStatsBitmapNeedsUpload = true
                    currentQuranStatsOverlay = banner
                    val evStats = currentEventStatsOverlay
                    if (evStats != null) {
                        val mergedBmp = createBannerBitmap(evStats, maxW, h)
                        eventStatsBitmap?.recycle()
                        eventStatsBitmap = mergedBmp
                        eventStatsQuadWidth = mergedBmp.width
                        eventStatsQuadHeight = mergedBmp.height
                        eventStatsBitmapNeedsUpload = true
                    }
                    if (currentQuranOverlay != null) {
                        rebuildQuranBitmapWithEvents()
                    }
                } else if (isEventStats) {
                    eventStatsBitmap?.recycle()
                    eventStatsBitmap = bmp
                    eventStatsQuadWidth = bmp.width
                    eventStatsQuadHeight = bmp.height
                    eventStatsBitmapNeedsUpload = true
                    currentEventStatsOverlay = banner
                    if (currentQuranOverlay != null) {
                        rebuildQuranBitmapWithEvents()
                    }
                } else if (isTopLikers) {
                    topLikersBitmap?.recycle()
                    topLikersBitmap = bmp
                    topLikersQuadWidth = bmp.width
                    topLikersQuadHeight = bmp.height
                    topLikersBitmapNeedsUpload = true
                    currentTopLikersOverlay = banner
                } else if (isLike) {
                    likeBitmap?.recycle()
                    likeBitmap = bmp
                    likeQuadWidth = bmp.width
                    likeQuadHeight = bmp.height
                    likeBitmapNeedsUpload = true
                    currentLikeOverlay = banner
                    if (currentQuranOverlay != null) {
                        rebuildQuranBitmapWithEvents()
                    }
                } else if (isEventGift) {
                    giftBitmap?.recycle()
                    giftBitmap = bmp
                    giftQuadWidth = bmp.width
                    giftQuadHeight = bmp.height
                    giftBitmapNeedsUpload = true
                    currentGiftOverlay = banner
                    if (currentQuranOverlay != null) {
                        rebuildQuranBitmapWithEvents()
                    }
                } else if (isTugOfWar) {
                    tugOfWarBitmap?.recycle()
                    tugOfWarBitmap = bmp
                    tugOfWarQuadWidth = bmp.width
                    tugOfWarQuadHeight = bmp.height
                    tugOfWarBitmapNeedsUpload = true
                    currentTugOfWarOverlay = banner
                } else if (bannerType == "AVATAR_BUBBLE") {
                    val nowSpawnMs = System.currentTimeMillis()
                    // 1. Bersihkan gelembung yang durasi terbangnya sudah selesai
                    val purgeIter = activeAvatarBubbles.iterator()
                    while (purgeIter.hasNext()) {
                        val s = purgeIter.next()
                        if (nowSpawnMs - s.banner.startTimeMs >= s.banner.durationMs) {
                            if (s.textureId != 0) {
                                GLES20.glDeleteTextures(1, intArrayOf(s.textureId), 0)
                                s.textureId = 0
                            }
                            s.bitmap?.recycle()
                            s.bitmap = null
                            purgeIter.remove()
                        }
                    }

                    // 2. Kapasitas tinggi (64 gelembung simultan) tanpa memotong foto yang sedang melayang
                    if (activeAvatarBubbles.size >= MAX_CONCURRENT_AVATAR_BUBBLES) {
                        // Hanya daur ulang gelembung yang SUDAH terbang >= 92% durasi (sudah keluar layar dan memudar)
                        val candidate = activeAvatarBubbles.filter { slot ->
                            (nowSpawnMs - slot.banner.startTimeMs) >= (slot.banner.durationMs * 0.92f)
                        }.minByOrNull { it.banner.startTimeMs }

                        if (candidate != null) {
                            activeAvatarBubbles.remove(candidate)
                            if (candidate.textureId != 0) {
                                GLES20.glDeleteTextures(1, intArrayOf(candidate.textureId), 0)
                                candidate.textureId = 0
                            }
                            candidate.bitmap?.recycle()
                            candidate.bitmap = null
                        } else {
                            // Jika semua slot masih aktif melayang di layar, drop gelembung baru
                            // agar TIDAK MEMOTONG foto yang sedang melayang di tengah layar!
                            bmp.recycle()
                            return@post
                        }
                    }

                    // 3. Distribusi jalur (anti-tumpuk): pilih lajur horizontal yang renggang dari gelembung 650ms terakhir
                    var adjustedX = banner.posXpct
                    val candidateLanes = floatArrayOf(0.16f, 0.33f, 0.50f, 0.67f, 0.84f)
                    val recentBubbles = activeAvatarBubbles.filter { (nowSpawnMs - it.banner.startTimeMs) < 650L }
                    if (recentBubbles.any { kotlin.math.abs(it.banner.posXpct - adjustedX) < 0.12f }) {
                        adjustedX = candidateLanes.maxByOrNull { lane ->
                            recentBubbles.minOfOrNull { kotlin.math.abs(it.banner.posXpct - lane) } ?: 1f
                        } ?: adjustedX
                    }

                    // 4. Terbang SEKETIKA (startTimeMs = nowSpawnMs) tanpa penundaan semu ke masa depan
                    val liveBanner = banner.copy(
                        posXpct = adjustedX.coerceIn(0.12f, 0.88f),
                        startTimeMs = nowSpawnMs
                    )
                    val phase = ((++avatarBubbleSeedCounter) * 1.57f) % (Math.PI.toFloat() * 2f)
                    activeAvatarBubbles.add(
                        AvatarBubbleSlot(
                            banner = liveBanner,
                            bitmap = bmp,
                            quadWidth = bmp.width,
                            quadHeight = bmp.height,
                            textureId = 0,
                            needsUpload = true,
                            phaseSeed = phase
                        )
                    )
                    ensureAvatarBubbleAnimationRunning()
                } else {
                    overlayBitmap?.recycle()
                    overlayBitmap = bmp
                    overlayQuadWidth = bmp.width
                    overlayQuadHeight = bmp.height
                    overlayBitmapNeedsUpload = true
                    currentOverlay = banner
                    if (currentQuranOverlay != null && banner.bannerType == "COMMENT") {
                        rebuildQuranBitmapWithEvents()
                    }
                }
                drawFrame(forceRedraw = true)
                Log.i(TAG, "Virtual Camera rendered overlay banner: '$senderName: $content' type=$bannerType style=$stikerDisplayStyle run=$isRunningText pos=($posXpct, $posYpct)")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to render overlay banner: ${t.message}", t)
            }
        }
    }

    fun hideOverlayBanner() {
        val handler = glHandler ?: return
        handler.post {
            currentOverlay = null
            if (currentQuranOverlay != null) {
                rebuildQuranBitmapWithEvents()
            }
            drawFrame(forceRedraw = true)
        }
    }

    fun hideLikeOverlay() {
        val handler = glHandler ?: return
        handler.post {
            currentLikeOverlay = null
            if (currentQuranOverlay != null) {
                rebuildQuranBitmapWithEvents()
            }
            drawFrame(forceRedraw = true)
        }
    }

    fun hideGiftOverlay() {
        val handler = glHandler ?: return
        handler.post {
            currentGiftOverlay = null
            if (currentQuranOverlay != null) {
                rebuildQuranBitmapWithEvents()
            }
            drawFrame(forceRedraw = true)
        }
    }

    fun hideAvatarBubbles() {
        val handler = glHandler ?: return
        handler.post {
            stopAvatarBubbleAnimation()
            for (slot in activeAvatarBubbles) {
                if (slot.textureId != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(slot.textureId), 0)
                    slot.textureId = 0
                }
                slot.bitmap?.recycle()
                slot.bitmap = null
            }
            activeAvatarBubbles.clear()
            drawFrame(forceRedraw = true)
        }
    }

    fun hideQuranOverlay() {
        val handler = glHandler ?: return
        handler.post {
            currentQuranOverlay = null
            drawFrame(forceRedraw = true)
        }
    }

    fun hideQuranStatsOverlay() {
        val handler = glHandler ?: return
        handler.post {
            currentQuranStatsOverlay = null
            drawFrame(forceRedraw = true)
        }
    }

    fun hideTugOfWarOverlay() {
        val handler = glHandler ?: return
        handler.post {
            currentTugOfWarOverlay = null
            drawFrame(forceRedraw = true)
        }
    }

    fun hideEventStatsOverlay() {
        val handler = glHandler ?: return
        handler.post {
            currentEventStatsOverlay = null
            drawFrame(forceRedraw = true)
        }
    }

    fun hideTopLikersOverlay() {
        val handler = glHandler ?: return
        handler.post {
            currentTopLikersOverlay = null
            drawFrame(forceRedraw = true)
        }
    }

    fun hideSticker() {
        val handler = glHandler ?: return
        handler.post {
            currentSticker = null
            drawFrame(forceRedraw = true)
        }
    }

    fun hideWatermark() {
        val handler = glHandler ?: return
        handler.post {
            currentWatermark = null
            drawFrame(forceRedraw = true)
        }
    }

    fun hideAutoTime() {
        val handler = glHandler ?: return
        handler.post {
            currentAutoTime = null
            drawFrame(forceRedraw = true)
        }
    }

    fun hideAllOverlays() {
        val handler = glHandler ?: return
        handler.post {
            stopAvatarBubbleAnimation()
            currentOverlay = null
            currentLikeOverlay = null
            currentGiftOverlay = null
            currentQuranOverlay = null
            currentQuranStatsOverlay = null
            currentEventStatsOverlay = null
            currentTopLikersOverlay = null
            for (slot in activeAvatarBubbles) {
                if (slot.textureId != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(slot.textureId), 0)
                    slot.textureId = 0
                }
                slot.bitmap?.recycle()
                slot.bitmap = null
            }
            activeAvatarBubbles.clear()
            drawFrame(forceRedraw = true)
        }
    }

    private fun getActiveDoaForQuran(now: Long = System.currentTimeMillis()): OverlayBannerData? {
        val ov = currentOverlay ?: return null
        return if (ov.bannerType == "COMMENT" && (now - ov.startTimeMs < ov.durationMs)) ov else null
    }

    private fun getActiveGiftForQuran(now: Long = System.currentTimeMillis()): OverlayBannerData? {
        val gf = currentGiftOverlay ?: return null
        return if (now - gf.startTimeMs < gf.durationMs) gf else null
    }

    private fun getActiveLikeForQuran(now: Long = System.currentTimeMillis()): OverlayBannerData? {
        val lk = currentLikeOverlay ?: return null
        return if (now - lk.startTimeMs < lk.durationMs) lk else null
    }

    private fun rebuildQuranBitmapWithEvents() {
        val quran = currentQuranOverlay ?: return
        try {
            val viewW = minOf(surfaceWidth, surfaceHeight).coerceAtLeast(720)
            val viewH = maxOf(surfaceWidth, surfaceHeight).coerceAtLeast(1280)
            val maxW = (viewW * 0.96f).toInt().coerceIn(320, 1600)
            val density = viewH / 1920f
            val h = (72f * density).toInt().coerceIn((12f * density).toInt().coerceAtLeast(10), 130)
            val newBmp = createBannerBitmap(quran, maxW, h)
            quranBitmap?.recycle()
            quranBitmap = newBmp
            quranQuadWidth = newBmp.width
            quranQuadHeight = newBmp.height
            quranBitmapNeedsUpload = true
        } catch (_: Throwable) {}
    }

    private fun createBannerBitmap(banner: OverlayBannerData, maxWidthPx: Int, heightPx: Int): Bitmap {
        val now = System.currentTimeMillis()
        val secondaryStats = when (banner.bannerType) {
            "EVENT_STATS" -> currentQuranStatsOverlay
            "QURAN_STATS" -> currentEventStatsOverlay
            "QURAN_KHATAM" -> currentEventStatsOverlay ?: currentQuranStatsOverlay
            else -> null
        }
        return OverlayBannerBitmapGenerator.createBannerBitmap(
            banner = banner,
            maxWidthPx = maxWidthPx,
            heightPx = heightPx,
            interSemiBoldTypeface = interSemiBoldTypeface,
            activeDoaBanner = if (banner.bannerType == "QURAN_KHATAM") getActiveDoaForQuran(now) else null,
            activeGiftBanner = if (banner.bannerType == "QURAN_KHATAM") getActiveGiftForQuran(now) else null,
            activeLikeBanner = if (banner.bannerType == "QURAN_KHATAM") getActiveLikeForQuran(now) else null,
            secondaryStatsBanner = secondaryStats
        )
    }

    private fun generateStickerBitmap(
        banner: OverlayBannerData,
        densityScale: Float,
        maxWidthPx: Int,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Bitmap = OverlayBannerBitmapGenerator.generateStickerBitmap(banner, densityScale, maxWidthPx, currentTimeMs)

    private fun uploadOverlayTexture(bmp: Bitmap) {
        overlayTextureId = GlShaderProgram.upload2DTexture(overlayTextureId, bmp)
    }

    private fun uploadLikeTexture(bmp: Bitmap) {
        likeTextureId = GlShaderProgram.upload2DTexture(likeTextureId, bmp)
    }

    private fun uploadGiftTexture(bmp: Bitmap) {
        giftTextureId = GlShaderProgram.upload2DTexture(giftTextureId, bmp)
    }

    private fun uploadQuranTexture(bmp: Bitmap) {
        quranTextureId = GlShaderProgram.upload2DTexture(quranTextureId, bmp)
    }

    private fun uploadQuranStatsTexture(bmp: Bitmap) {
        quranStatsTextureId = GlShaderProgram.upload2DTexture(quranStatsTextureId, bmp)
    }

    private fun uploadEventStatsTexture(bmp: Bitmap) {
        eventStatsTextureId = GlShaderProgram.upload2DTexture(eventStatsTextureId, bmp)
    }

    private fun uploadTopLikersTexture(bmp: Bitmap) {
        topLikersTextureId = GlShaderProgram.upload2DTexture(topLikersTextureId, bmp)
    }

    private fun drawOverlayQuad(banner: OverlayBannerData, alpha: Float, slideOffsetX: Float = 0f, slideOffsetY: Float = 0f) {
        drawStickerQuad(banner, alpha, overlayTextureId, overlayQuadWidth, overlayQuadHeight, slideOffsetX, slideOffsetY)
    }

    private fun uploadStickerTexture(bmp: Bitmap) {
        stickerTextureId = GlShaderProgram.upload2DTexture(stickerTextureId, bmp)
    }

    private fun uploadWatermarkTexture(bmp: Bitmap) {
        watermarkTextureId = GlShaderProgram.upload2DTexture(watermarkTextureId, bmp)
    }

    private fun uploadAutoTimeTexture(bmp: Bitmap) {
        autoTimeTextureId = GlShaderProgram.upload2DTexture(autoTimeTextureId, bmp)
    }

    private fun uploadTugOfWarTexture(bmp: Bitmap) {
        tugOfWarTextureId = GlShaderProgram.upload2DTexture(tugOfWarTextureId, bmp)
    }

    private fun drawStickerQuad(
        banner: OverlayBannerData,
        alpha: Float,
        texId: Int = stickerTextureId,
        quadW: Int = stickerQuadWidth,
        quadH: Int = stickerQuadHeight,
        slideOffsetX: Float = 0f,
        slideOffsetY: Float = 0f
    ) {
        if (overlayProgramId == 0 || texId == 0) return
        val coords = OverlayBannerBitmapGenerator.computeOverlayQuadNdcCoords(
            banner = banner,
            quadWidth = quadW,
            quadHeight = quadH,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            currentRotation = currentRotation,
            isMirrored = false, // Overlays (stickers, watermarks, notifs) must NEVER mirror when video mirror is active
            isVerticalFlipped = false,
            slideOffsetX = slideOffsetX,
            slideOffsetY = slideOffsetY
        )
        GlShaderProgram.drawTexturedQuadNdc(
            overlayProgramId = overlayProgramId,
            textureId = texId,
            alpha = alpha,
            coords = coords,
            overlayVertexBuffer = overlayVertexBuffer,
            overlayTexCoordsBuffer = overlayTexCoordsBuffer,
            overlayMvpMatrix = overlayMvpMatrix,
            overlayPositionLoc = overlayPositionLoc,
            overlayTexCoordLoc = overlayTexCoordLoc,
            overlayTextureLoc = overlayTextureLoc,
            overlayAlphaLoc = overlayAlphaLoc,
            overlayMvpMatrixLoc = overlayMvpMatrixLoc
        )
    }

    fun setDebugOsdEnabled(enabled: Boolean) {
        val handler = glHandler ?: return
        handler.post {
            this._isDebugOsdEnabled = enabled
            drawFrame(forceRedraw = true)
        }
    }

    private fun drawDebugOsdQuad() {
        if (overlayProgramId == 0) return

        val sW = surfaceWidth.toFloat().coerceAtLeast(1f)
        val sH = surfaceHeight.toFloat().coerceAtLeast(1f)
        val effectiveRotation = (currentRotation + 270) % 360
        val isRotated90or270 = (effectiveRotation == 90 || effectiveRotation == 270)
        val viewW = if (isRotated90or270) sH else sW
        val viewH = if (isRotated90or270) sW else sH

        val curPosMs = mediaPlayer?.currentPosition ?: lastKnownPositionMs
        val curDurMs = mediaPlayer?.duration ?: 0
        val vName = effectiveVideoName

        val curSec = curPosMs / 1000
        val curMs100 = (curPosMs % 1000) / 40
        val now = System.currentTimeMillis()

        if (debugOsdBitmap == null || curSec != lastRenderedPosSec || curMs100 != lastRenderedPosMs100 || (now - lastDebugOsdUpdateMs) > 50L) {
            lastRenderedPosSec = curSec
            lastRenderedPosMs100 = curMs100
            lastDebugOsdUpdateMs = now

            val bmpW = (viewW * 0.94f).toInt().coerceIn(580, 1080)
            val bmpH = (bmpW * 0.38f).toInt().coerceIn(240, 360)
            debugOsdQuadWidth = bmpW
            debugOsdQuadHeight = bmpH

            debugOsdBitmap?.recycle()
            val newBmp = DebugOsdRenderer.generateDebugOsdBitmap(
                posMs = curPosMs, durMs = curDurMs, videoName = vName, widthPx = bmpW, heightPx = bmpH,
                totalPlaylistCount = totalPlaylistCount, currentPlaylistIndex = currentPlaylistIndex,
                videoWidth = videoWidth, videoHeight = videoHeight, currentFps = currentFps,
                currentRotation = currentRotation, autoNextLeadMsVal = autoNextLeadMsVal,
                autoNextModeName = autoNextModeName, isLoopingEnabled = isLoopingEnabled,
                isMuted = isMuted, isVideoAudioMuted = isVideoAudioMuted,
                isPreloadAckReady = isPreloadAckReady, activeSlot = activeSlot,
                oesTextureIdA = oesTextureIdA, oesTextureIdB = oesTextureIdB,
                context = MainHook.appContext, activeVideoFile = videoFile
            )
            debugOsdBitmap = newBmp
            debugOsdTextureId = GlShaderProgram.upload2DTexture(debugOsdTextureId, newBmp)
        }

        if (debugOsdTextureId == 0) return

        val coords = DebugOsdRenderer.computeDebugOsdNdcCoords(
            viewW = viewW, viewH = viewH, quadWidth = debugOsdQuadWidth,
            quadHeight = debugOsdQuadHeight, effectiveRotation = effectiveRotation
        )
        GlShaderProgram.drawTexturedQuadNdc(
            overlayProgramId = overlayProgramId, textureId = debugOsdTextureId, alpha = 1.0f, coords = coords,
            overlayVertexBuffer = overlayVertexBuffer, overlayTexCoordsBuffer = overlayTexCoordsBuffer,
            overlayMvpMatrix = overlayMvpMatrix, overlayPositionLoc = overlayPositionLoc,
            overlayTexCoordLoc = overlayTexCoordLoc, overlayTextureLoc = overlayTextureLoc,
            overlayAlphaLoc = overlayAlphaLoc, overlayMvpMatrixLoc = overlayMvpMatrixLoc
        )
    }
}
