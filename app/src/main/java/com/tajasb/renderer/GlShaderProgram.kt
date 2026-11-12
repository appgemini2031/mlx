package com.tajasb.renderer

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix as GlMatrix
import com.tajasb.model.ScaleMode
import java.nio.FloatBuffer

/**
 * GlShaderProgram:
 * Modular OpenGL ES 2.0 shader compilation, program linking, 2D texture uploading,
 * MVP matrix calculation, OES video frame drawing, and NDC quad rendering in canonical portrait viewer space.
 */
object GlShaderProgram {

    const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        uniform mat4 uMVPMatrix;
        uniform mat4 uTexMatrix;
        varying vec2 vTextureCoord;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTextureCoord = (uTexMatrix * aTextureCoord).xy;
        }
    """

    const val FRAGMENT_SHADER = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        uniform float uAntiHashIntensity;
        uniform float uTimeSeed;
        uniform float uColorShift;
        uniform float uSessionGamma;
        uniform float uSessionContrast;
        uniform float uSessionWarmth;
        uniform float uSessionTint;
        uniform vec2 uDctFreq;
        uniform vec2 uLightDir;

        float pseudoNoise(vec2 co) {
            return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
        }

        void main() {
            vec4 col = texture2D(sTexture, vTextureCoord);
            bool hasAntiHash = (uAntiHashIntensity > 0.0005);
            bool hasSessionDna = (abs(uSessionGamma - 1.0) > 0.001) ||
                                 (abs(uSessionContrast - 1.0) > 0.001) ||
                                 (abs(uSessionWarmth) > 0.0005) ||
                                 (abs(uSessionTint) > 0.0005) ||
                                 (length(uLightDir) > 0.0005);

            if (hasAntiHash || hasSessionDna) {
                vec3 shifted = col.rgb;

                if (hasSessionDna) {
                    float effGamma = (uSessionGamma > 0.5) ? uSessionGamma : 1.0;
                    float effContrast = (uSessionContrast > 0.5) ? uSessionContrast : 1.0;
                    shifted = pow(clamp(shifted, 0.0001, 1.0), vec3(effGamma));
                    shifted = (shifted - 0.5) * effContrast + 0.5;

                    // Session-specific 2D directional room lighting gradient + color temperature DNA
                    float roomLight = dot(vTextureCoord - vec2(0.5, 0.5), uLightDir);
                    shifted += vec3(roomLight);
                    shifted.r += uSessionWarmth;
                    shifted.g += uSessionTint;
                    shifted.b -= (uSessionWarmth * 0.92);
                }

                if (hasAntiHash) {
                    float n1 = pseudoNoise(vTextureCoord * 137.0 + vec2(fract(uTimeSeed), fract(uTimeSeed * 0.618)));
                    float n2 = pseudoNoise(vTextureCoord * 293.0 - vec2(fract(uTimeSeed * 0.731), fract(uTimeSeed * 1.317)));
                    float n = ((n1 * 0.65 + n2 * 0.35) - 0.5) * uAntiHashIntensity;

                    // Spatial DCT frequency modulation to defeat 8x8/16x16 pHash & ViT embeddings
                    float fx = (uDctFreq.x > 1.0) ? uDctFreq.x : 9.4247;
                    float fy = (uDctFreq.y > 1.0) ? uDctFreq.y : 9.4247;
                    float dctBreak = sin(vTextureCoord.x * fx + uTimeSeed * 0.41) * cos(vTextureCoord.y * fy - uTimeSeed * 0.29) * (uAntiHashIntensity * 0.28);

                    shifted.r += (uColorShift + dctBreak);
                    shifted.g += (uColorShift * 0.18 - dctBreak * 0.45);
                    shifted.b -= (uColorShift * 0.92 + dctBreak * 0.55);
                    shifted += vec3(n, n * 0.94, -n * 0.89);
                }

                gl_FragColor = vec4(clamp(shifted, 0.0, 1.0), col.a);
            } else {
                gl_FragColor = col;
            }
        }
    """

    const val OVERLAY_VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTextureCoord;
        uniform mat4 uOverlayMVPMatrix;
        varying vec2 vTextureCoord;
        void main() {
            gl_Position = uOverlayMVPMatrix * aPosition;
            vTextureCoord = aTextureCoord;
        }
    """

    const val OVERLAY_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D uOverlayTexture;
        uniform float uAlpha;
        void main() {
            vec4 col = texture2D(uOverlayTexture, vTextureCoord);
            gl_FragColor = vec4(col.rgb * uAlpha, col.a * uAlpha);
        }
    """

    val VERTEX_COORDS = floatArrayOf(
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
         1.0f,  1.0f
    )

    val TEXTURE_COORDS = floatArrayOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    )

    fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Could not compile shader $type: $log")
        }
        return shader
    }

    fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Could not link program: $log")
        }
        return program
    }

    fun computeMvpMatrix(
        mvpMatrix: FloatArray,
        surfaceWidth: Int,
        surfaceHeight: Int,
        videoWidth: Int,
        videoHeight: Int,
        currentRotation: Int,
        scaleMode: ScaleMode,
        isMirrored: Boolean,
        isVerticalFlipped: Boolean,
        breathingScale: Float = 1.0f,
        microOffsetX: Float = 0.0f,
        microOffsetY: Float = 0.0f,
        microRotationDeg: Float = 0.0f,
        aspectWarpX: Float = 1.0f,
        aspectWarpY: Float = 1.0f,
        userZoom: Float = 1.0f,
        sessionDnaZoom: Float = 1.0f,
        sessionPanX: Float = 0.0f,
        sessionPanY: Float = 0.0f,
        isLiveStream: Boolean = false
    ) {
        GlMatrix.setIdentityM(mvpMatrix, 0)

        val sW = surfaceWidth.toFloat().coerceAtLeast(1f)
        val sH = surfaceHeight.toFloat().coerceAtLeast(1f)
        var vW = videoWidth.toFloat().coerceAtLeast(1f)
        var vH = videoHeight.toFloat().coerceAtLeast(1f)

        // Logika Khusus Mekah / Live Stream (Device 2):
        // Jika stream live aktif tetapi resolusi belum terbaca dari header HLS, defaultkan langsung ke 16:9 Landscape (1280x720)
        // agar Kaaba tidak pernah gepeng akibat fallback ke rasio portrait!
        if (isLiveStream) {
            if (vW == 1080f && vH == 1920f || (vW <= 1f || vH <= 1f)) {
                vW = 1280f
                vH = 720f
            }
        }

        // Android camera sensors are natively mounted at 270° offset in portrait.
        // Applying +270° compensation aligns 0° in UI to natural upright portrait.
        val effectiveRotation = (currentRotation + 270) % 360
        val isRotated90or270 = effectiveRotation == 90 || effectiveRotation == 270

        // Hitung rasio aspek quad lokal (quadScaleX & quadScaleY) secara matematis presisi bebas gepeng:
        val r: Float = if (isRotated90or270) {
            // Saat dirotasi 90°/270°, sumbu X video jatuh ke sumbu Y layar (sH) dan sumbu Y video ke sumbu X layar (sW)
            (vW / vH) * (sW / sH)
        } else {
            // Saat tidak dirotasi (0°/180°), sumbu X video sejajar sW dan sumbu Y video sejajar sH
            (vW / vH) / (sW / sH)
        }

        var scaleX = 1.0f
        var scaleY = 1.0f

        when (scaleMode) {
            ScaleMode.CROP -> {
                // Kaaba Fokus / Isi Penuh Layar: Penuhi sumbu pembatas tanpa distorsi/gepeng sedikitpun
                if (r >= 1.0f) {
                    scaleX = r
                    scaleY = 1.0f
                } else {
                    scaleX = 1.0f
                    scaleY = 1.0f / r
                }
            }
            ScaleMode.FIT -> {
                // Kaaba Utuh / Tampil Lengkap 16:9: Tampilkan seluruh area frame tanpa terpotong
                if (r >= 1.0f) {
                    scaleX = 1.0f
                    scaleY = 1.0f / r
                } else {
                    scaleX = r
                    scaleY = 1.0f
                }
            }
            ScaleMode.STRETCH -> {
                scaleX = 1.0f
                scaleY = 1.0f
            }
        }

        val hasMicroTransform = (microOffsetX != 0.0f || microOffsetY != 0.0f || microRotationDeg != 0.0f || aspectWarpX != 1.0f || aspectWarpY != 1.0f)
        val safeBreathingScale = if (hasMicroTransform && scaleMode != ScaleMode.FIT && breathingScale < 1.012f) {
            1.012f
        } else {
            breathingScale
        }

        if (safeBreathingScale in 0.3f..4.0f && safeBreathingScale != 1.0f) {
            scaleX *= safeBreathingScale
            scaleY *= safeBreathingScale
        }

        if (scaleMode != ScaleMode.FIT && (aspectWarpX != 1.0f || aspectWarpY != 1.0f)) {
            scaleX *= aspectWarpX.coerceIn(0.96f, 1.05f)
            scaleY *= aspectWarpY.coerceIn(0.96f, 1.05f)
        }

        // Apply combined user video zoom & session DNA zoom
        val effZoom = (userZoom * sessionDnaZoom).coerceIn(0.5f, 5.0f)
        if (effZoom != 1.0f) {
            scaleX *= effZoom
            scaleY *= effZoom
        }

        // Safe Framing Envelope Guard: Batasi pergeseran (Pan) agar Kaaba bisa digeser presisi tanpa tepi hitam
        val maxSafePanX = if (scaleX > 1.0f) (scaleX - 1.0f) * 0.90f else (if (isLiveStream) 0.5f else 0.0f)
        val maxSafePanY = if (scaleY > 1.0f) (scaleY - 1.0f) * 0.90f else (if (isLiveStream) 0.5f else 0.0f)

        val combinedOffsetX = (microOffsetX + sessionPanX).coerceIn(-maxSafePanX, maxSafePanX)
        val combinedOffsetY = (microOffsetY + sessionPanY).coerceIn(-maxSafePanY, maxSafePanY)

        if (combinedOffsetX != 0.0f || combinedOffsetY != 0.0f) {
            GlMatrix.translateM(mvpMatrix, 0, combinedOffsetX, combinedOffsetY, 0.0f)
        }

        // Terapkan rotasi terlebih dahulu ke ruang koordinat quad agar scaleX/scaleY konsisten
        val totalZRotation = -effectiveRotation.toFloat() + microRotationDeg.coerceIn(-0.6f, 0.6f)
        if (totalZRotation != 0.0f) {
            GlMatrix.rotateM(mvpMatrix, 0, totalZRotation, 0.0f, 0.0f, 1.0f)
        }

        GlMatrix.scaleM(mvpMatrix, 0, scaleX, scaleY, 1.0f)

        if (isMirrored) {
            GlMatrix.scaleM(mvpMatrix, 0, -1.0f, 1.0f, 1.0f)
        }

        if (isVerticalFlipped) {
            GlMatrix.scaleM(mvpMatrix, 0, 1.0f, -1.0f, 1.0f)
        }
    }

    fun drawOesVideoQuad(
        surfaceWidth: Int,
        surfaceHeight: Int,
        scaleMode: ScaleMode,
        programId: Int,
        activeTexId: Int,
        sTextureLoc: Int,
        uMVPMatrixLoc: Int,
        uTexMatrixLoc: Int,
        mvpMatrix: FloatArray,
        texMatrix: FloatArray,
        aPositionLoc: Int,
        aTextureCoordLoc: Int,
        vertexBuffer: FloatBuffer,
        textureBuffer: FloatBuffer,
        antiHashIntensity: Float = 0.0f,
        timeSeed: Float = 0.0f,
        colorShift: Float = 0.0f,
        uAntiHashIntensityLoc: Int = -1,
        uTimeSeedLoc: Int = -1,
        uColorShiftLoc: Int = -1,
        sessionGamma: Float = 1.0f,
        sessionContrast: Float = 1.0f,
        sessionWarmth: Float = 0.0f,
        sessionTint: Float = 0.0f,
        dctFreqX: Float = 9.4247f,
        dctFreqY: Float = 9.4247f,
        lightDirX: Float = 0.0f,
        lightDirY: Float = 0.0f,
        uSessionGammaLoc: Int = -1,
        uSessionContrastLoc: Int = -1,
        uSessionWarmthLoc: Int = -1,
        uSessionTintLoc: Int = -1,
        uDctFreqLoc: Int = -1,
        uLightDirLoc: Int = -1
    ) {
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        if (scaleMode == ScaleMode.FIT) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        GLES20.glUseProgram(programId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, activeTexId)
        GLES20.glUniform1i(sTextureLoc, 0)

        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)

        if (uAntiHashIntensityLoc >= 0) {
            GLES20.glUniform1f(uAntiHashIntensityLoc, antiHashIntensity)
        }
        if (uTimeSeedLoc >= 0) {
            GLES20.glUniform1f(uTimeSeedLoc, timeSeed)
        }
        if (uColorShiftLoc >= 0) {
            GLES20.glUniform1f(uColorShiftLoc, colorShift)
        }
        if (uSessionGammaLoc >= 0) {
            GLES20.glUniform1f(uSessionGammaLoc, sessionGamma)
        }
        if (uSessionContrastLoc >= 0) {
            GLES20.glUniform1f(uSessionContrastLoc, sessionContrast)
        }
        if (uSessionWarmthLoc >= 0) {
            GLES20.glUniform1f(uSessionWarmthLoc, sessionWarmth)
        }
        if (uSessionTintLoc >= 0) {
            GLES20.glUniform1f(uSessionTintLoc, sessionTint)
        }
        if (uDctFreqLoc >= 0) {
            GLES20.glUniform2f(uDctFreqLoc, dctFreqX, dctFreqY)
        }
        if (uLightDirLoc >= 0) {
            GLES20.glUniform2f(uLightDirLoc, lightDirX, lightDirY)
        }

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 8, textureBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
    }

    fun upload2DTexture(existingTextureId: Int, bmp: Bitmap): Int {
        var texId = existingTextureId
        if (texId == 0) {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            texId = textures[0]
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        return texId
    }

    fun drawTexturedQuadNdc(
        overlayProgramId: Int,
        textureId: Int,
        alpha: Float,
        coords: FloatArray,
        overlayVertexBuffer: FloatBuffer,
        overlayTexCoordsBuffer: FloatBuffer,
        overlayMvpMatrix: FloatArray,
        overlayPositionLoc: Int,
        overlayTexCoordLoc: Int,
        overlayTextureLoc: Int,
        overlayAlphaLoc: Int,
        overlayMvpMatrixLoc: Int
    ) {
        if (overlayProgramId == 0 || textureId == 0) return

        overlayVertexBuffer.clear()
        overlayVertexBuffer.put(coords)
        overlayVertexBuffer.position(0)

        GlMatrix.setIdentityM(overlayMvpMatrix, 0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(overlayProgramId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(overlayTextureLoc, 1)
        GLES20.glUniform1f(overlayAlphaLoc, alpha)
        GLES20.glUniformMatrix4fv(overlayMvpMatrixLoc, 1, false, overlayMvpMatrix, 0)

        GLES20.glEnableVertexAttribArray(overlayPositionLoc)
        GLES20.glVertexAttribPointer(overlayPositionLoc, 2, GLES20.GL_FLOAT, false, 8, overlayVertexBuffer)

        GLES20.glEnableVertexAttribArray(overlayTexCoordLoc)
        GLES20.glVertexAttribPointer(overlayTexCoordLoc, 2, GLES20.GL_FLOAT, false, 8, overlayTexCoordsBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(overlayPositionLoc)
        GLES20.glDisableVertexAttribArray(overlayTexCoordLoc)
        GLES20.glDisable(GLES20.GL_BLEND)
    }
}
