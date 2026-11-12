package com.tajasb.renderer

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface

/**
 * EglSurfaceManager:
 * Manages EGL 1.4 display, context, window surface creation, seamless target surface migration,
 * and resource teardown for the Virtual Camera OpenGL pipeline.
 */
object EglSurfaceManager {
    private const val TAG = "EglSurfaceManager"

    data class EglInitResult(
        val eglDisplay: EGLDisplay,
        val eglContext: EGLContext,
        val eglSurface: EGLSurface,
        val surfaceWidth: Int,
        val surfaceHeight: Int
    )

    private val RGBA8_CONFIG_ATTRIBS = intArrayOf(
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_RED_SIZE, 8,
        EGL14.EGL_GREEN_SIZE, 8,
        EGL14.EGL_BLUE_SIZE, 8,
        EGL14.EGL_ALPHA_SIZE, 8,
        EGL14.EGL_NONE
    )

    fun initEgl(
        targetSurface: Surface,
        defaultWidth: Int = 1080,
        defaultHeight: Int = 1920
    ): EglInitResult {
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("eglInitialize failed")
        }

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, RGBA8_CONFIG_ATTRIBS, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            throw RuntimeException("eglChooseConfig failed")
        }

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        val eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("eglCreateContext failed")
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], targetSurface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglCreateWindowSurface failed")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }

        try {
            EGL14.eglSwapInterval(eglDisplay, 0)
        } catch (_: Throwable) {}

        val wArr = IntArray(1)
        val hArr = IntArray(1)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, wArr, 0)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, hArr, 0)
        val width = if (wArr[0] > 0) wArr[0] else defaultWidth
        val height = if (hArr[0] > 0) hArr[0] else defaultHeight
        Log.i(TAG, "Target EGL Surface size: ${width}x${height}")

        return EglInitResult(
            eglDisplay = eglDisplay,
            eglContext = eglContext,
            eglSurface = eglSurface,
            surfaceWidth = width,
            surfaceHeight = height
        )
    }

    fun createWindowSurfaceForTarget(
        eglDisplay: EGLDisplay,
        eglContext: EGLContext,
        targetSurface: Surface
    ): EGLSurface {
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, RGBA8_CONFIG_ATTRIBS, 0, configs, 0, 1, numConfigs, 0)
        val newSurf = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], targetSurface, intArrayOf(EGL14.EGL_NONE), 0)
        if (newSurf != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, newSurf, newSurf, eglContext)
            try { EGL14.eglSwapInterval(eglDisplay, 0) } catch (_: Throwable) {}
        }
        return newSurf
    }

    fun destroySurface(eglDisplay: EGLDisplay, eglSurface: EGLSurface) {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            try { EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) } catch (_: Throwable) {}
            try { EGL14.eglDestroySurface(eglDisplay, eglSurface) } catch (_: Throwable) {}
        }
    }

    fun releaseEgl(eglDisplay: EGLDisplay, eglContext: EGLContext, eglSurface: EGLSurface) {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            try { EGL14.eglDestroySurface(eglDisplay, eglSurface) } catch (_: Throwable) {}
        }
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            try { EGL14.eglDestroyContext(eglDisplay, eglContext) } catch (_: Throwable) {}
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            try { EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) } catch (_: Throwable) {}
            try { EGL14.eglTerminate(eglDisplay) } catch (_: Throwable) {}
        }
    }
}
