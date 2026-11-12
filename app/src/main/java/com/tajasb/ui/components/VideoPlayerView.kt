@file:Suppress("DEPRECATION")

package com.tajasb.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.tajasb.model.ScaleMode
import java.io.File

// Top-level cache to restore playback position when switching screens/views
private val savedPreviewPositions = java.util.concurrent.ConcurrentHashMap<String, Int>()

@Composable
fun VideoPlayerView(
    videoPath: String,
    rotation: Int = 0,
    isMirrored: Boolean = false,
    isVerticalFlipped: Boolean = false,
    zoomFactor: Float = 1.0f,
    scaleMode: ScaleMode = ScaleMode.FIT,
    audioEnabled: Boolean = false,
    isVideoAudioMuted: Boolean = false,
    videoVolume: Float = 1.0f,
    isPaused: Boolean = false,
    isLooping: Boolean = false,
    seekToMs: Long? = null,
    seekEvent: Pair<Long, Long>? = null,
    playbackSessionKey: Any = videoPath,
    onProgress: ((currentPosMs: Int, durationMs: Int) -> Unit)? = null,
    onVideoSizeChanged: ((width: Int, height: Int) -> Unit)? = null,
    onVideoCompleted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember(isPaused) { mutableStateOf(!isPaused) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var surfaceRef by remember { mutableStateOf<Surface?>(null) }
    var attachedSurfaceRef by remember { mutableStateOf<Surface?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var isPreparingPath by remember { mutableStateOf<String?>(null) }
    var loadedVideoPath by remember { mutableStateOf<String?>(null) }
    var loadedVideoModified by remember { mutableStateOf(0L) }
    var loadedVideoLength by remember { mutableStateOf(0L) }
    var lastLoadStartTimeMs by remember { mutableStateOf(0L) }
    var lastCompletionTimeMs by remember { mutableStateOf(0L) }
    var lastInPlaceRestartTimeMs by remember { mutableStateOf(0L) }
    var isFirstLoadOnMount by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }
    var lastAppliedSessionKey by remember { mutableStateOf<Any?>(playbackSessionKey) }
    val hasVideo = remember(videoPath, playbackSessionKey) {
        videoPath.isNotEmpty() && (videoPath.startsWith("http://") || videoPath.startsWith("https://") || File(videoPath).exists())
    }

    var currentVideoWidth by remember { mutableStateOf(0) }
    var currentVideoHeight by remember { mutableStateOf(0) }
    var activeTextureView by remember { mutableStateOf<TextureView?>(null) }

    fun applyTextureMatrix(tv: TextureView?, viewW: Int, viewH: Int, vW: Int, vH: Int, mode: ScaleMode) {
        if (tv == null || viewW <= 0 || viewH <= 0 || vW <= 0 || vH <= 0) return
        val matrix = android.graphics.Matrix()
        val videoAspect = vW.toFloat() / vH.toFloat()
        val viewAspect = viewW.toFloat() / viewH.toFloat()
        val sx: Float
        val sy: Float
        when (mode) {
            ScaleMode.CROP -> {
                if (videoAspect > viewAspect) {
                    sx = videoAspect / viewAspect
                    sy = 1.0f
                } else {
                    sx = 1.0f
                    sy = viewAspect / videoAspect
                }
            }
            ScaleMode.FIT -> {
                if (videoAspect > viewAspect) {
                    sx = 1.0f
                    sy = viewAspect / videoAspect
                } else {
                    sx = videoAspect / viewAspect
                    sy = 1.0f
                }
            }
            ScaleMode.STRETCH -> {
                sx = 1.0f
                sy = 1.0f
            }
        }
        matrix.setScale(sx, sy, viewW / 2f, viewH / 2f)
        tv.setTransform(matrix)
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isHostLifecycleResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    val effectiveAudioEnabled = remember(audioEnabled, isVideoAudioMuted, isHostLifecycleResumed) {
        audioEnabled && !isVideoAudioMuted && isHostLifecycleResumed &&
            ((System.currentTimeMillis() - com.tajasb.data.VcamRepository.lastGlobalPlaybackTimeMs) >= 3500L)
    }

    val currentVideoPath by rememberUpdatedState(videoPath)
    val currentIsLooping by rememberUpdatedState(isLooping)
    val currentOnVideoCompleted by rememberUpdatedState(onVideoCompleted)
    val currentIsPaused by rememberUpdatedState(isPaused)
    val currentEffectiveAudio by rememberUpdatedState(effectiveAudioEnabled)
    val currentVideoVolume by rememberUpdatedState(videoVolume)
    val currentOnVideoSizeChanged by rememberUpdatedState(onVideoSizeChanged)

    var decoyFreezeActive by remember { mutableStateOf(false) }

    fun loadSourceIntoPlayer(targetPath: String, surface: Surface) {
        if (targetPath.isEmpty()) return
        val isNetworkStream = targetPath.startsWith("http://") || targetPath.startsWith("https://")
        if (!isNetworkStream && !File(targetPath).exists()) return
        try {
            val shouldRestoreSavedPos = isFirstLoadOnMount && !isNetworkStream
            isFirstLoadOnMount = false
            isPrepared = false
            isPreparingPath = targetPath
            lastLoadStartTimeMs = System.currentTimeMillis()
            val mp = mediaPlayerRef ?: MediaPlayer().also { mediaPlayerRef = it }
            mp.reset()
            if (attachedSurfaceRef !== surface) {
                mp.setSurface(surface)
                attachedSurfaceRef = surface
            }
            mp.setDataSource(targetPath)
            mp.isLooping = if (isNetworkStream) true else (if (currentOnVideoCompleted != null) false else currentIsLooping)
            val vol = if (currentEffectiveAudio) currentVideoVolume.coerceIn(0f, 1f) else 0f
            mp.setVolume(vol, vol)

            mp.setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0) {
                    currentVideoWidth = w
                    currentVideoHeight = h
                    currentOnVideoSizeChanged?.invoke(w, h)
                    activeTextureView?.let { tv ->
                        applyTextureMatrix(tv, tv.width, tv.height, w, h, scaleMode)
                    }
                }
            }

            mp.setOnPreparedListener { player ->
                isPrepared = true
                isPreparingPath = null
                loadedVideoPath = targetPath
                if (!isNetworkStream) {
                    val f = File(targetPath)
                    loadedVideoModified = f.lastModified()
                    loadedVideoLength = f.length()
                } else {
                    loadedVideoModified = 0L
                    loadedVideoLength = 0L
                }
                lastLoadStartTimeMs = System.currentTimeMillis()
                val vw = player.videoWidth
                val vh = player.videoHeight
                if (vw > 0 && vh > 0) {
                    currentVideoWidth = vw
                    currentVideoHeight = vh
                    currentOnVideoSizeChanged?.invoke(vw, vh)
                    activeTextureView?.let { tv ->
                        applyTextureMatrix(tv, tv.width, tv.height, vw, vh, scaleMode)
                    }
                }
                val totalDur = player.duration.coerceAtLeast(0)
                durationMs = totalDur

                // Pulihkan posisi putar HANYA saat komponen pertama kali dimuat kembali dari tab/menu lain.
                // Saat pergantian/siklus video (SV3 maupun Mode Standar), WAJIB mulai dari 0:00 tanpa loncatan!
                val savedPos = if (shouldRestoreSavedPos) (savedPreviewPositions.remove(targetPath) ?: 0) else 0
                if (shouldRestoreSavedPos && totalDur > 1500 && savedPos in 600 until (totalDur - 800)) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            player.seekTo(savedPos.toLong(), MediaPlayer.SEEK_CLOSEST)
                        } else {
                            player.seekTo(savedPos)
                        }
                        currentPositionMs = savedPos
                    } catch (_: Throwable) {
                        currentPositionMs = 0
                    }
                } else {
                    currentPositionMs = 0
                }

                player.isLooping = if (isNetworkStream) true else (if (currentOnVideoCompleted != null) false else currentIsLooping)
                val preparedVol = if (currentEffectiveAudio && !decoyFreezeActive && isHostLifecycleResumed) currentVideoVolume.coerceIn(0f, 1f) else 0f
                player.setVolume(preparedVol, preparedVol)
                if (!currentIsPaused && !decoyFreezeActive && isHostLifecycleResumed && surfaceRef != null) {
                    try {
                        player.start()
                    } catch (_: Throwable) {}
                }
            }

            mp.setOnCompletionListener { player ->
                if (!isHostLifecycleResumed || surfaceRef == null) {
                    return@setOnCompletionListener
                }
                savedPreviewPositions.remove(targetPath)
                if (currentIsLooping) {
                    try {
                        val now = System.currentTimeMillis()
                        lastLoadStartTimeMs = now
                        lastCompletionTimeMs = now
                        lastInPlaceRestartTimeMs = now
                        player.seekTo(0)
                        currentPositionMs = 0
                        if (!currentIsPaused) {
                            player.start()
                        }
                    } catch (_: Throwable) {}
                } else if (currentOnVideoCompleted != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastLoadStartTimeMs > 300L && now - lastCompletionTimeMs > 400L) {
                        lastLoadStartTimeMs = now
                        lastCompletionTimeMs = now
                        val pathBeforeComplete = loadedVideoPath
                        currentOnVideoCompleted?.invoke()
                        // Biarkan LaunchedEffect(videoPath, playbackSessionKey) menangani pergantian video baru
                        // atau pengulangan video yang sama pada frame Compose berikutnya (~8ms) agar tidak me-restart video lama!
                        // Fallback 90ms jika state sama sekali tidak berubah:
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                val mpNow = mediaPlayerRef
                                val rNow = System.currentTimeMillis()
                                if (mpNow === player &&
                                    isPrepared &&
                                    isPreparingPath == null &&
                                    loadedVideoPath == pathBeforeComplete &&
                                    currentVideoPath == pathBeforeComplete &&
                                    !mpNow.isPlaying &&
                                    !currentIsPaused &&
                                    !decoyFreezeActive &&
                                    isHostLifecycleResumed &&
                                    (rNow - lastInPlaceRestartTimeMs > 200L)
                                ) {
                                    lastLoadStartTimeMs = rNow
                                    lastInPlaceRestartTimeMs = rNow
                                    mpNow.seekTo(0)
                                    currentPositionMs = 0
                                    mpNow.start()
                                }
                            } catch (_: Throwable) {}
                        }, 90L)
                    }
                } else {
                    try {
                        val now = System.currentTimeMillis()
                        lastLoadStartTimeMs = now
                        lastCompletionTimeMs = now
                        lastInPlaceRestartTimeMs = now
                        player.seekTo(0)
                        currentPositionMs = 0
                        if (!currentIsPaused && !decoyFreezeActive) {
                            player.start()
                        }
                    } catch (_: Throwable) {}
                }
            }

            mp.setOnErrorListener { _, _, _ ->
                isPrepared = false
                isPreparingPath = null
                true
            }

            mp.prepareAsync()
        } catch (_: Throwable) {
            isPrepared = false
            isPreparingPath = null
        }
    }

    LaunchedEffect(isPaused) {
        isPlaying = !isPaused
        val mp = mediaPlayerRef
        if (mp != null && isPrepared) {
            try {
                if (isPaused && mp.isPlaying) {
                    mp.pause()
                } else if (!isPaused && !mp.isPlaying && !decoyFreezeActive) {
                    mp.start()
                }
            } catch (_: Throwable) {}
        }
    }

    // High frequency progress update (40ms ticker) + EOF fallback detector for stitched MP4s
    LaunchedEffect(isPlaying, hasVideo, isPrepared) {
        var lastEofFallbackTimeMs = 0L
        var eofStallSinceMs = 0L
        while (isPlaying && hasVideo && isPrepared) {
            mediaPlayerRef?.let { mp ->
                try {
                    val pos = mp.currentPosition
                    val dur = mp.duration
                    if (mp.isPlaying) {
                        eofStallSinceMs = 0L
                        currentPositionMs = pos
                        if (dur > 0) durationMs = dur
                        onProgress?.invoke(pos, dur)
                    } else if (!currentIsPaused && !decoyFreezeActive && !currentIsLooping && currentOnVideoCompleted != null && isPreparingPath == null) {
                        val now = System.currentTimeMillis()
                        val effectiveDur = if (dur > 0) dur else durationMs
                        if (effectiveDur >= 1500 && pos >= effectiveDur - 120) {
                            if (eofStallSinceMs == 0L) {
                                eofStallSinceMs = now
                            } else if (now - eofStallSinceMs >= 420L &&
                                now - lastLoadStartTimeMs > 900L &&
                                now - lastCompletionTimeMs > 900L &&
                                now - lastInPlaceRestartTimeMs > 900L &&
                                now - lastEofFallbackTimeMs > 1200L
                            ) {
                                eofStallSinceMs = 0L
                                lastEofFallbackTimeMs = now
                                lastLoadStartTimeMs = now
                                lastCompletionTimeMs = now
                                currentOnVideoCompleted?.invoke()
                            }
                        } else {
                            eofStallSinceMs = 0L
                        }
                    } else {
                        eofStallSinceMs = 0L
                    }
                } catch (_: Throwable) {}
            }
            kotlinx.coroutines.delay(40L)
        }
    }

    LaunchedEffect(seekToMs) {
        seekToMs?.let { ms ->
            if (isPrepared) {
                try {
                    val mp = mediaPlayerRef
                    if (mp != null) {
                        val dur = mp.duration.takeIf { it > 0 } ?: durationMs
                        val clamped = if (dur > 200) ms.toInt().coerceIn(0, dur - 100) else ms.toInt().coerceAtLeast(0)
                        mp.seekTo(clamped)
                        currentPositionMs = clamped
                        if (!currentIsPaused && !mp.isPlaying && !decoyFreezeActive) {
                            mp.start()
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    LaunchedEffect(seekEvent) {
        seekEvent?.let { (ms, _) ->
            if (isPrepared) {
                try {
                    val mp = mediaPlayerRef
                    if (mp != null) {
                        val dur = mp.duration.takeIf { it > 0 } ?: durationMs
                        val clamped = if (dur > 200) ms.toInt().coerceIn(0, dur - 100) else ms.toInt().coerceAtLeast(0)
                        mp.seekTo(clamped)
                        currentPositionMs = clamped
                        if (!currentIsPaused && !mp.isPlaying && !decoyFreezeActive) {
                            mp.start()
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    LaunchedEffect(effectiveAudioEnabled, videoVolume, isPrepared, isHostLifecycleResumed) {
        if (isPrepared) {
            try {
                val vol = if (effectiveAudioEnabled && !decoyFreezeActive && isHostLifecycleResumed) videoVolume.coerceIn(0f, 1f) else 0f
                mediaPlayerRef?.setVolume(vol, vol)
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(isLooping, onVideoCompleted, isPrepared) {
        if (isPrepared) {
            try {
                mediaPlayerRef?.isLooping = if (onVideoCompleted != null) false else isLooping
            } catch (_: Throwable) {}
        }
    }

    // Smoothly switch video source or restart phase without recreating TextureView (zero black flash)
    LaunchedEffect(videoPath, playbackSessionKey, surfaceRef) {
        val surface = surfaceRef ?: return@LaunchedEffect
        if (!hasVideo) return@LaunchedEffect

        if (isPreparingPath == videoPath && !isPrepared) {
            // Sedang menyiapkan file video besar yang sama, jangan di-reset di tengah prepareAsync!
            lastAppliedSessionKey = playbackSessionKey
            return@LaunchedEffect
        }

        val currentMp = mediaPlayerRef
        val isNetworkStream = videoPath.startsWith("http://") || videoPath.startsWith("https://")
        val targetFile = if (!isNetworkStream) File(videoPath) else null
        val curMod = targetFile?.lastModified() ?: 0L
        val curLen = targetFile?.length() ?: 0L
        val isDifferentFile = loadedVideoPath != videoPath ||
            currentMp == null ||
            (!isNetworkStream && curLen > 0L && (curLen != loadedVideoLength || curMod != loadedVideoModified))
        if (isDifferentFile) {
            lastAppliedSessionKey = playbackSessionKey
            loadSourceIntoPlayer(videoPath, surface)
        } else if (lastAppliedSessionKey != playbackSessionKey) {
            lastAppliedSessionKey = playbackSessionKey
            if (currentMp != null && isPrepared) {
                try {
                    currentMp.isLooping = if (currentOnVideoCompleted != null) false else currentIsLooping
                    val now = System.currentTimeMillis()
                    if (now - lastInPlaceRestartTimeMs > 150L) {
                        lastLoadStartTimeMs = now
                        lastInPlaceRestartTimeMs = now
                        currentMp.seekTo(0)
                        currentPositionMs = 0
                        if (!currentIsPaused && !decoyFreezeActive && isHostLifecycleResumed && !currentMp.isPlaying) {
                            currentMp.start()
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isHostLifecycleResumed = true
                    val mp = mediaPlayerRef
                    if (mp != null && isPrepared && !currentIsPaused && !decoyFreezeActive && surfaceRef != null) {
                        try {
                            if (!mp.isPlaying) mp.start()
                        } catch (_: Throwable) {}
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isHostLifecycleResumed = false
                    val mp = mediaPlayerRef
                    if (mp != null) {
                        try {
                            mp.setVolume(0f, 0f)
                            if (mp.isPlaying) mp.pause()
                        } catch (_: Throwable) {}
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                val lastPos = try { mediaPlayerRef?.currentPosition ?: currentPositionMs } catch (_: Throwable) { currentPositionMs }
                val totalDur = durationMs
                val path = loadedVideoPath ?: videoPath
                if (path.isNotEmpty() && totalDur > 1500 && lastPos in 600 until (totalDur - 800)) {
                    savedPreviewPositions[path] = lastPos
                } else if (path.isNotEmpty()) {
                    savedPreviewPositions.remove(path)
                }
            } catch (_: Throwable) {}
            isPrepared = false
            loadedVideoPath = null
            try {
                mediaPlayerRef?.setVolume(0f, 0f)
                if (mediaPlayerRef?.isPlaying == true) {
                    mediaPlayerRef?.stop()
                }
                mediaPlayerRef?.release()
            } catch (_: Throwable) {}
            mediaPlayerRef = null
            try {
                surfaceRef?.release()
            } catch (_: Throwable) {}
            surfaceRef = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
    ) {
        // Video Render with persistent TextureView (zero SurfaceView hole-punching, zero transition flicker)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val z = zoomFactor.coerceIn(0.5f, 3.0f)
                    rotationZ = (rotation.toFloat()) % 360f
                    scaleX = (if (isMirrored) -1f else 1f) * z
                    scaleY = (if (isVerticalFlipped) -1f else 1f) * z
                },
            contentAlignment = Alignment.Center
        ) {
            if (hasVideo) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            isOpaque = true
                            activeTextureView = this
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surfaceTexture: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    val newSurface = Surface(surfaceTexture)
                                    surfaceRef = newSurface
                                    activeTextureView = this@apply
                                    applyTextureMatrix(this@apply, width, height, currentVideoWidth, currentVideoHeight, scaleMode)
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    surfaceTexture: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    activeTextureView = this@apply
                                    applyTextureMatrix(this@apply, width, height, currentVideoWidth, currentVideoHeight, scaleMode)
                                }

                                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                    isPrepared = false
                                    loadedVideoPath = null
                                    attachedSurfaceRef = null
                                    activeTextureView = null
                                    try {
                                        if (mediaPlayerRef?.isPlaying == true) {
                                            mediaPlayerRef?.pause()
                                        }
                                        mediaPlayerRef?.setSurface(null)
                                    } catch (_: Throwable) {}
                                    try {
                                        surfaceRef?.release()
                                    } catch (_: Throwable) {}
                                    surfaceRef = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                            }
                        }
                    },
                    update = { tv ->
                        activeTextureView = tv
                        applyTextureMatrix(tv, tv.width, tv.height, currentVideoWidth, currentVideoHeight, scaleMode)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                EmptyVideoPlaceholder()
            }
        }

        // Bottom Progress Bar
        if (hasVideo) {
            val curSec = (currentPositionMs / 1000).coerceAtLeast(0)
            val totSec = (durationMs / 1000).coerceAtLeast(0)
            val curStr = String.format("%02d:%02d", curSec / 60, curSec % 60)
            val totStr = String.format("%02d:%02d", totSec / 60, totSec % 60)

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$curStr / $totStr",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )

                if (isPaused) {
                    Text(
                        text = "DIJEDA",
                        color = Color.Yellow,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyVideoPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.VideocamOff,
            contentDescription = "No video",
            tint = Color.Gray.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Belum Ada Video Terpasang",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Pilih file video MP4 atau gunakan pola sampel bawaan",
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}
