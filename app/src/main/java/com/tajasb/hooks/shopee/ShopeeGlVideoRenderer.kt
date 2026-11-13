package com.tajasb.hooks.shopee

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix as GlMatrix
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.tajasb.model.ScaleMode
import com.tajasb.renderer.EglSurfaceManager
import com.tajasb.renderer.GlShaderProgram
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ShopeeGlVideoRenderer:
 * Renderer OpenGL ES 2.0 terisolasi khusus untuk proses Shopee Live & Shopee Video
 * (`com.shopee.*` serta APK kloning paket acak).
 * Tidak berbagi state dengan GlVideoRenderer milik TikTok ataupun BigoGlVideoRenderer,
 * dan mendukung migrasi Surface tanpa kedip/layar hitam saat transisi Preview <-> Live Room.
 */
class ShopeeGlVideoRenderer(
    @Volatile var targetSurface: Surface,
    @Volatile var videoFile: File,
    @Volatile private var audioFile: File? = null,
    initialConfig: ShopeeVcamConfig = ShopeeVcamConfig(),
    initialFrontFacing: Boolean = false
) : SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "Tajasb-Shopee"
    }

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var onSurfaceInvalidated: ((Surface) -> Unit)? = null

    fun isHealthy(): Boolean {
        return isRunning &&
            !released.get() &&
            targetSurface.isValid &&
            eglDisplay != EGL14.EGL_NO_DISPLAY &&
            eglSurface != EGL14.EGL_NO_SURFACE
    }

    @Volatile
    private var currentConfig: ShopeeVcamConfig = initialConfig

    @Volatile
    private var isFrontFacing: Boolean = initialFrontFacing

    @Volatile
    private var surfaceWidth: Int = 1080

    @Volatile
    private var surfaceHeight: Int = 1920

    @Volatile
    private var videoWidth: Int = 1080

    @Volatile
    private var videoHeight: Int = 1920

    @Volatile
    private var pendingSurfaceUpdate: Surface? = null

    @Volatile
    private var frameAvailable: Boolean = false

    @Volatile
    private var hasFirstFrame: Boolean = false

    private val forceRedrawPending = AtomicBoolean(false)
    private var lastDrawTimeMs: Long = 0L
    private var frameSeedCounter: Float = ((System.currentTimeMillis() and 0xFFL).toFloat() * 0.13f)

    private val released = AtomicBoolean(false)

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var programId: Int = 0
    private var oesTextureId: Int = 0
    private var videoSurfaceTexture: SurfaceTexture? = null
    private var videoInputSurface: Surface? = null

    private var mediaPlayer: MediaPlayer? = null
    private var extraAudioPlayer: MediaPlayer? = null

    private val mvpMatrix = FloatArray(16)
    private val texMatrix = FloatArray(16)
    private val queryWidthArr = IntArray(1)
    private val queryHeightArr = IntArray(1)

    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(GlShaderProgram.VERTEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(GlShaderProgram.VERTEX_COORDS); position(0) }

    private val texCoordBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(GlShaderProgram.TEXTURE_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(GlShaderProgram.TEXTURE_COORDS); position(0) }

    private var aPositionLoc: Int = -1
    private var aTextureCoordLoc: Int = -1
    private var uMVPMatrixLoc: Int = -1
    private var uTexMatrixLoc: Int = -1
    private var sTextureLoc: Int = -1
    private var uAntiHashIntensityLoc: Int = -1
    private var uTimeSeedLoc: Int = -1
    private var uColorShiftLoc: Int = -1
    private var uSessionGammaLoc: Int = -1
    private var uSessionContrastLoc: Int = -1
    private var uSessionWarmthLoc: Int = -1
    private var uSessionTintLoc: Int = -1
    private var uDctFreqLoc: Int = -1
    private var uLightDirLoc: Int = -1

    private var fallbackPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val delayedDrawRunnable = Runnable {
        if (!isRunning || released.get()) return@Runnable
        try {
            checkPendingSurfaceMigration()
            drawFrame(forceRedraw = forceRedrawPending.getAndSet(false))
        } catch (t: Throwable) {
            Log.w(TAG, "ShopeeGlVideoRenderer delayedDraw error: ${t.message}")
        }
    }

    private val keepaliveRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || released.get()) return
            try {
                checkPendingSurfaceMigration()
                if (hasFirstFrame && (currentConfig.isPaused || forceRedrawPending.get())) {
                    drawFrame(forceRedraw = true)
                    forceRedrawPending.set(false)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "ShopeeGlVideoRenderer keepalive error: ${t.message}")
            }
            renderHandler?.postDelayed(this, 500L)
        }
    }

    fun start(waitForEglMs: Long = 0L) {
        if (isRunning || released.get()) return
        isRunning = true

        val thread = HandlerThread("TajasbShopeeGlRenderer")
        thread.start()
        renderThread = thread
        val handler = Handler(thread.looper)
        renderHandler = handler

        val latch = if (waitForEglMs > 0L) java.util.concurrent.CountDownLatch(1) else null
        handler.post {
            try {
                initGlAndMedia()
                latch?.countDown()
                handler.postDelayed(keepaliveRunnable, 500L)
            } catch (t: Throwable) {
                latch?.countDown()
                handleDeadSurface("EGL init gagal (${t.message})")
            }
        }
        if (latch != null) {
            try {
                latch.await(waitForEglMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {}
        }
    }

    private fun initGlAndMedia() {
        GlMatrix.setIdentityM(texMatrix, 0)
        GlMatrix.setIdentityM(mvpMatrix, 0)
        hasFirstFrame = false
        frameAvailable = false

        val egl = EglSurfaceManager.initEgl(targetSurface, surfaceWidth, surfaceHeight)
        eglDisplay = egl.eglDisplay
        eglContext = egl.eglContext
        eglSurface = egl.eglSurface
        surfaceWidth = egl.surfaceWidth
        surfaceHeight = egl.surfaceHeight

        programId = GlShaderProgram.createProgram(
            GlShaderProgram.VERTEX_SHADER,
            GlShaderProgram.FRAGMENT_SHADER
        )
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

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        oesTextureId = texIds[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(oesTextureId)
        st.setOnFrameAvailableListener(this, renderHandler)
        videoSurfaceTexture = st
        val inputSurf = Surface(st)
        videoInputSurface = inputSurf

        prepareVideoPlayer(videoFile, inputSurf)
        audioFile?.let { prepareExtraAudioPlayer(it) }

        ShopeeMainHook.logInfo("ShopeeGlVideoRenderer aktif (${surfaceWidth}x${surfaceHeight}), file=${videoFile.name}")
    }

    private fun prepareVideoPlayer(file: File, outputSurface: Surface) {
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {}

        val mp = MediaPlayer()
        mediaPlayer = mp
        mp.setSurface(outputSurface)
        mp.isLooping = true
        mp.setDataSource(file.absolutePath)
        mp.setOnVideoSizeChangedListener { _, w, h ->
            if (w > 0 && h > 0) {
                videoWidth = w
                videoHeight = h
                forceRedrawPending.set(true)
            }
        }
        mp.setOnPreparedListener { player ->
            try {
                if (player.videoWidth > 0 && player.videoHeight > 0) {
                    videoWidth = player.videoWidth
                    videoHeight = player.videoHeight
                }
                applyAudioVolumes()
                if (!currentConfig.isPaused) {
                    player.start()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "MediaPlayer Shopee start warning: ${t.message}")
            }
        }
        mp.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "ShopeeGlVideoRenderer MediaPlayer error: what=$what extra=$extra")
            true
        }
        mp.prepareAsync()
    }

    private fun prepareExtraAudioPlayer(file: File) {
        if (!file.exists() || !file.canRead() || file.length() <= 0L) return
        try {
            extraAudioPlayer?.release()
        } catch (_: Throwable) {}

        val mp = MediaPlayer()
        extraAudioPlayer = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.isLooping = true
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener { player ->
                applyAudioVolumes()
                if (!currentConfig.isMp3AudioPaused && !currentConfig.isPaused) {
                    try { player.start() } catch (_: Throwable) {}
                }
            }
            mp.prepareAsync()
        } catch (t: Throwable) {
            Log.w(TAG, "Extra audio Shopee gagal disiapkan: ${t.message}")
        }
    }

    private fun startFallbackMediaPlayer(surface: Surface, file: File) {
        mainHandler.post {
            if (released.get() || !surface.isValid) return@post
            try {
                fallbackPlayer?.release()
                val mp = MediaPlayer()
                fallbackPlayer = mp
                mp.setSurface(surface)
                mp.isLooping = true
                mp.setDataSource(file.absolutePath)
                mp.setOnPreparedListener { player ->
                    val vol = if (currentConfig.isMuted || currentConfig.isVideoAudioMuted) 0f else 1f
                    player.setVolume(vol, vol)
                    if (!currentConfig.isPaused) {
                        player.start()
                    }
                    Log.i(TAG, "Fallback MediaPlayer aktif untuk Shopee: ${file.name}")
                }
                mp.prepareAsync()
            } catch (t: Throwable) {
                Log.e(TAG, "Fallback MediaPlayer Shopee gagal: ${t.message}")
            }
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        if (!isRunning || released.get()) return
        frameAvailable = true
        try {
            checkPendingSurfaceMigration()
            drawFrame(forceRedraw = forceRedrawPending.getAndSet(false))
        } catch (t: Throwable) {
            Log.w(TAG, "ShopeeGlVideoRenderer onFrameAvailable draw error: ${t.message}")
        }
    }

    private fun checkPendingSurfaceMigration() {
        val nextSurf = pendingSurfaceUpdate ?: return
        pendingSurfaceUpdate = null
        if (!nextSurf.isValid || nextSurf === targetSurface || eglDisplay == EGL14.EGL_NO_DISPLAY) return

        try {
            EglSurfaceManager.destroySurface(eglDisplay, eglSurface)
            val newEglSurf = EglSurfaceManager.createWindowSurfaceForTarget(eglDisplay, eglContext, nextSurf)
            if (newEglSurf != EGL14.EGL_NO_SURFACE) {
                eglSurface = newEglSurf
                targetSurface = nextSurf
                EGL14.eglQuerySurface(eglDisplay, newEglSurf, EGL14.EGL_WIDTH, queryWidthArr, 0)
                EGL14.eglQuerySurface(eglDisplay, newEglSurf, EGL14.EGL_HEIGHT, queryHeightArr, 0)
                if (queryWidthArr[0] > 0) surfaceWidth = queryWidthArr[0]
                if (queryHeightArr[0] > 0) surfaceHeight = queryHeightArr[0]
                forceRedrawPending.set(true)
                ShopeeMainHook.logInfo("Surface Shopee dimigrasikan seamless (${surfaceWidth}x${surfaceHeight})")
            } else {
                eglSurface = EGL14.EGL_NO_SURFACE
                handleDeadSurface("migrasi eglCreateWindowSurface gagal")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal migrasi surface Shopee: ${t.message}")
            handleDeadSurface("migrasi exception: ${t.message}")
        }
    }

    private fun handleDeadSurface(reason: String) {
        val dead = targetSurface
        ShopeeMainHook.logWarn("Surface Shopee terputus ($reason), melepas renderer agar siap dipulihkan")
        release()
        try {
            onSurfaceInvalidated?.invoke(dead)
        } catch (_: Throwable) {}
    }

    private fun drawFrame(forceRedraw: Boolean = false) {
        val st = videoSurfaceTexture ?: return
        if (!isRunning || released.get()) return
        if (!targetSurface.isValid) {
            handleDeadSurface("targetSurface.isValid=false")
            return
        }
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return

        val hasNew = frameAvailable
        if (!hasNew && (!forceRedraw || !hasFirstFrame)) {
            return
        }

        val fps = currentConfig.fps.coerceIn(15, 60)
        val minIntervalMs = if (fps >= 55) 0L else ((1000L / fps) - 6L).coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        if (!forceRedraw && hasFirstFrame && minIntervalMs > 0L && (now - lastDrawTimeMs) < minIntervalMs) {
            // Drain SurfaceTexture agar BufferQueue MediaCodec tidak macet, lalu pertahankan texMatrix terakhir
            if (hasNew) {
                frameAvailable = false
                try {
                    if (EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        st.updateTexImage()
                        st.getTransformMatrix(texMatrix)
                        val strideCropV = com.tajasb.engine.BufferQueueManager.getStrideCropFactorV(surfaceWidth, surfaceHeight)
                        if (strideCropV < 1.0f) {
                            GlMatrix.scaleM(texMatrix, 0, 1.0f, strideCropV, 1.0f)
                        }
                    }
                } catch (_: Throwable) {}
            }
            return
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            return
        }

        if (hasNew) {
            frameAvailable = false
            try {
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
                val strideCropV = com.tajasb.engine.BufferQueueManager.getStrideCropFactorV(surfaceWidth, surfaceHeight)
                if (strideCropV < 1.0f) {
                    GlMatrix.scaleM(texMatrix, 0, 1.0f, strideCropV, 1.0f)
                }
                hasFirstFrame = true
            } catch (_: Throwable) {
                return
            }
        }

        if (!hasFirstFrame) return

        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, queryWidthArr, 0)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, queryHeightArr, 0)
        if (queryWidthArr[0] > 0) surfaceWidth = queryWidthArr[0]
        if (queryHeightArr[0] > 0) surfaceHeight = queryHeightArr[0]

        val cfg = currentConfig
        val scaleMode = when (cfg.scaleMode.uppercase()) {
            "FIT" -> ScaleMode.FIT
            "STRETCH" -> ScaleMode.STRETCH
            else -> ScaleMode.CROP
        }
        val effectiveMirror = if (isFrontFacing) !cfg.isMirrored else cfg.isMirrored

        GlShaderProgram.computeMvpMatrix(
            mvpMatrix = mvpMatrix,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            currentRotation = cfg.rotation,
            scaleMode = scaleMode,
            isMirrored = effectiveMirror,
            isVerticalFlipped = cfg.isVerticalFlipped,
            userZoom = cfg.zoomFactor
        )

        frameSeedCounter += 0.037f
        if (frameSeedCounter > 10000.0f) frameSeedCounter = 0.0f

        GlShaderProgram.drawOesVideoQuad(
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            scaleMode = scaleMode,
            programId = programId,
            activeTexId = oesTextureId,
            sTextureLoc = sTextureLoc,
            uMVPMatrixLoc = uMVPMatrixLoc,
            uTexMatrixLoc = uTexMatrixLoc,
            mvpMatrix = mvpMatrix,
            texMatrix = texMatrix,
            aPositionLoc = aPositionLoc,
            aTextureCoordLoc = aTextureCoordLoc,
            vertexBuffer = vertexBuffer,
            textureBuffer = texCoordBuffer,
            antiHashIntensity = 0.008f,
            timeSeed = frameSeedCounter,
            colorShift = 0.002f,
            uAntiHashIntensityLoc = uAntiHashIntensityLoc,
            uTimeSeedLoc = uTimeSeedLoc,
            uColorShiftLoc = uColorShiftLoc,
            sessionGamma = 1.01f,
            sessionContrast = 1.01f,
            sessionWarmth = 0.002f,
            sessionTint = 0.0f,
            dctFreqX = 9.4247f,
            dctFreqY = 9.4247f,
            lightDirX = 0.002f,
            lightDirY = -0.002f,
            uSessionGammaLoc = uSessionGammaLoc,
            uSessionContrastLoc = uSessionContrastLoc,
            uSessionWarmthLoc = uSessionWarmthLoc,
            uSessionTintLoc = uSessionTintLoc,
            uDctFreqLoc = uDctFreqLoc,
            uLightDirLoc = uLightDirLoc
        )

        val swapped = EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        if (!swapped) {
            val err = EGL14.eglGetError()
            if (!targetSurface.isValid || err == EGL14.EGL_BAD_SURFACE || err == EGL14.EGL_BAD_NATIVE_WINDOW) {
                handleDeadSurface("eglSwapBuffers=0x${Integer.toHexString(err)}")
                return
            }
        }
        lastDrawTimeMs = now
    }

    fun updateTargetSurface(newSurface: Surface) {
        if (!newSurface.isValid || targetSurface === newSurface || pendingSurfaceUpdate === newSurface) return
        if (fallbackPlayer != null) {
            mainHandler.post {
                try {
                    fallbackPlayer?.setSurface(newSurface)
                    targetSurface = newSurface
                } catch (_: Throwable) {}
            }
            return
        }
        pendingSurfaceUpdate = newSurface
        forceRedrawPending.set(true)
        renderHandler?.post(delayedDrawRunnable)
    }

    fun setFrontFacing(front: Boolean) {
        if (isFrontFacing != front) {
            isFrontFacing = front
            forceRedrawPending.set(true)
            renderHandler?.post(delayedDrawRunnable)
        }
    }

    fun ensurePlayingIfActive() {
        if (!isRunning || released.get()) return
        forceRedrawPending.set(true)
        renderHandler?.post {
            try {
                if (!currentConfig.isPaused) {
                    mediaPlayer?.let { mp ->
                        if (!mp.isPlaying) mp.start()
                    }
                    extraAudioPlayer?.let { amp ->
                        if (!currentConfig.isMp3AudioPaused && !amp.isPlaying) amp.start()
                    }
                }
                if (hasFirstFrame) {
                    checkPendingSurfaceMigration()
                    drawFrame(forceRedraw = true)
                    forceRedrawPending.set(false)
                }
            } catch (_: Throwable) {}
        }
    }

    fun applyConfig(newConfig: ShopeeVcamConfig) {
        val prevPaused = currentConfig.isPaused
        val prevMp3Paused = currentConfig.isMp3AudioPaused
        currentConfig = newConfig
        forceRedrawPending.set(true)

        renderHandler?.post {
            try {
                applyAudioVolumes()
                mediaPlayer?.let { mp ->
                    if (newConfig.isPaused && mp.isPlaying) {
                        mp.pause()
                    } else if (!newConfig.isPaused && prevPaused && !mp.isPlaying) {
                        mp.start()
                    }
                }
                extraAudioPlayer?.let { amp ->
                    if ((newConfig.isMp3AudioPaused || newConfig.isPaused) && amp.isPlaying) {
                        amp.pause()
                    } else if (!newConfig.isMp3AudioPaused && !newConfig.isPaused && prevMp3Paused && !amp.isPlaying) {
                        amp.start()
                    }
                }
                if (hasFirstFrame) {
                    checkPendingSurfaceMigration()
                    drawFrame(forceRedraw = true)
                    forceRedrawPending.set(false)
                }
            } catch (_: Throwable) {}
        }

        mainHandler.post {
            fallbackPlayer?.let { fp ->
                try {
                    val vol = if (newConfig.isMuted || newConfig.isVideoAudioMuted) 0f else 1f
                    fp.setVolume(vol, vol)
                    if (newConfig.isPaused && fp.isPlaying) fp.pause()
                    else if (!newConfig.isPaused && !fp.isPlaying) fp.start()
                } catch (_: Throwable) {}
            }
        }
    }

    fun switchVideoFile(newFile: File) {
        if (!newFile.exists() || newFile.length() <= 0L) return
        videoFile = newFile
        renderHandler?.post {
            val surf = videoInputSurface ?: return@post
            try {
                prepareVideoPlayer(newFile, surf)
                Log.i(TAG, "Video Shopee diganti ke: ${newFile.name}")
            } catch (t: Throwable) {
                Log.w(TAG, "Gagal switch video Shopee: ${t.message}")
            }
        }
        if (fallbackPlayer != null) {
            startFallbackMediaPlayer(targetSurface, newFile)
        }
    }

    fun setAudioFile(newAudioFile: File?) {
        audioFile = newAudioFile
        renderHandler?.post {
            if (newAudioFile != null) {
                prepareExtraAudioPlayer(newAudioFile)
            } else {
                try { extraAudioPlayer?.release() } catch (_: Throwable) {}
                extraAudioPlayer = null
            }
        }
    }

    private fun applyAudioVolumes() {
        val cfg = currentConfig
        val videoVol = if (cfg.isMuted || cfg.isVideoAudioMuted) 0f else 1f
        val mp3Vol = if (cfg.isMuted || cfg.isMp3AudioPaused) 0f else 1f
        try { mediaPlayer?.setVolume(videoVol, videoVol) } catch (_: Throwable) {}
        try { extraAudioPlayer?.setVolume(mp3Vol, mp3Vol) } catch (_: Throwable) {}
    }

    private fun releaseGlOnly() {
        try {
            if (programId != 0) {
                GLES20.glDeleteProgram(programId)
                programId = 0
            }
            if (oesTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
                oesTextureId = 0
            }
        } catch (_: Throwable) {}
        try { videoInputSurface?.release() } catch (_: Throwable) {}
        videoInputSurface = null
        try { videoSurfaceTexture?.release() } catch (_: Throwable) {}
        videoSurfaceTexture = null
        EglSurfaceManager.releaseEgl(eglDisplay, eglContext, eglSurface)
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        isRunning = false
        renderHandler?.removeCallbacksAndMessages(null)
        renderHandler?.post {
            try { mediaPlayer?.release() } catch (_: Throwable) {}
            mediaPlayer = null
            try { extraAudioPlayer?.release() } catch (_: Throwable) {}
            extraAudioPlayer = null
            releaseGlOnly()
            renderThread?.quitSafely()
        }
        mainHandler.post {
            try { fallbackPlayer?.release() } catch (_: Throwable) {}
            fallbackPlayer = null
        }
        Log.i(TAG, "ShopeeGlVideoRenderer dilepas")
    }
}
