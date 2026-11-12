package com.tajasb.hooks.bigo

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
 * BigoGlVideoRenderer:
 * Renderer OpenGL ES 2.0 terisolasi khusus untuk proses BIGO Live (sg.bigo.live / sg.bigo.lite).
 * Tidak berbagi state dengan GlVideoRenderer milik TikTok, dan mendukung migrasi Surface
 * tanpa layar hitam saat transisi Preview Persiapan Live <-> Ruang Live / Multi-Guest PK.
 */
class BigoGlVideoRenderer(
    @Volatile var targetSurface: Surface,
    @Volatile var videoFile: File,
    @Volatile private var audioFile: File? = null,
    initialConfig: BigoVcamConfig = BigoVcamConfig(),
    initialFrontFacing: Boolean = false
) : SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "Tajasb-BIGO"
    }

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    private var currentConfig: BigoVcamConfig = initialConfig

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

    private var fallbackPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val frameDrawRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || released.get()) return
            try {
                checkPendingSurfaceMigration()
                drawFrame()
            } catch (t: Throwable) {
                Log.w(TAG, "BigoGlVideoRenderer drawFrame error: ${t.message}")
            }
            val fps = currentConfig.fps.coerceIn(15, 60)
            val delayMs = (1000L / fps).coerceAtLeast(16L)
            renderHandler?.postDelayed(this, delayMs)
        }
    }

    fun start() {
        if (isRunning || released.get()) return
        isRunning = true

        val thread = HandlerThread("TajasbBigoGlRenderer")
        thread.start()
        renderThread = thread
        val handler = Handler(thread.looper)
        renderHandler = handler

        handler.post {
            try {
                initGlAndMedia()
                handler.post(frameDrawRunnable)
            } catch (t: Throwable) {
                Log.w(TAG, "EGL init failed for Surface (${targetSurface}), fallback MediaPlayer: ${t.message}")
                releaseGlOnly()
                startFallbackMediaPlayer(targetSurface, videoFile)
            }
        }
    }

    private fun initGlAndMedia() {
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

        Log.i(TAG, "BigoGlVideoRenderer aktif (${surfaceWidth}x${surfaceHeight}), file=${videoFile.name}")
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
                Log.w(TAG, "MediaPlayer start warning: ${t.message}")
            }
        }
        mp.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "BigoGlVideoRenderer MediaPlayer error: what=$what extra=$extra")
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
            Log.w(TAG, "Extra audio BIGO gagal disiapkan: ${t.message}")
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
                    Log.i(TAG, "Fallback MediaPlayer aktif untuk BIGO: ${file.name}")
                }
                mp.prepareAsync()
            } catch (t: Throwable) {
                Log.e(TAG, "Fallback MediaPlayer BIGO gagal: ${t.message}")
            }
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        frameAvailable = true
    }

    private fun checkPendingSurfaceMigration() {
        val nextSurf = pendingSurfaceUpdate ?: return
        pendingSurfaceUpdate = null
        if (!nextSurf.isValid || eglDisplay == EGL14.EGL_NO_DISPLAY) return

        try {
            EglSurfaceManager.destroySurface(eglDisplay, eglSurface)
            val newEglSurf = EglSurfaceManager.createWindowSurfaceForTarget(eglDisplay, eglContext, nextSurf)
            if (newEglSurf != EGL14.EGL_NO_SURFACE) {
                eglSurface = newEglSurf
                targetSurface = nextSurf
                val wArr = IntArray(1)
                val hArr = IntArray(1)
                EGL14.eglQuerySurface(eglDisplay, newEglSurf, EGL14.EGL_WIDTH, wArr, 0)
                EGL14.eglQuerySurface(eglDisplay, newEglSurf, EGL14.EGL_HEIGHT, hArr, 0)
                if (wArr[0] > 0) surfaceWidth = wArr[0]
                if (hArr[0] > 0) surfaceHeight = hArr[0]
                Log.i(TAG, "Surface BIGO dimigrasikan seamless (${surfaceWidth}x${surfaceHeight})")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal migrasi surface BIGO: ${t.message}")
        }
    }

    private fun drawFrame() {
        val st = videoSurfaceTexture ?: return
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return

        if (frameAvailable) {
            frameAvailable = false
            try {
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
            } catch (_: Throwable) {
                return
            }
        } else {
            GlMatrix.setIdentityM(texMatrix, 0)
        }

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
            textureBuffer = texCoordBuffer
        )

        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun updateTargetSurface(newSurface: Surface) {
        if (!newSurface.isValid || targetSurface == newSurface) return
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
    }

    fun setFrontFacing(front: Boolean) {
        isFrontFacing = front
    }

    fun applyConfig(newConfig: BigoVcamConfig) {
        val prevPaused = currentConfig.isPaused
        val prevMp3Paused = currentConfig.isMp3AudioPaused
        currentConfig = newConfig

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
                Log.i(TAG, "Video BIGO diganti ke: ${newFile.name}")
            } catch (t: Throwable) {
                Log.w(TAG, "Gagal switch video BIGO: ${t.message}")
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
        Log.i(TAG, "BigoGlVideoRenderer dilepas")
    }
}
