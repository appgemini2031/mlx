package com.tajasb.engine

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * AvSyncEngine:
 * Presentation Time Stamp (PTS) & Monotonic Clock Synchronizer for virtual camera streaming.
 * Uses SystemClock.elapsedRealtimeNanos() with strict monotonic progression, natural physical
 * sensor micro-jitter (±150µs), and drift compensation to maintain audio-visual sync (<5ms offset).
 */
class AvSyncEngine(initialFps: Int = 30) {

    private val baseClockNanos = AtomicLong(0L)
    private var frameIntervalNanos: Long = 1_000_000_000L / initialFps.coerceIn(15, 60)
    private var frameIndex = 0L
    private var lastEmittedPts = 0L

    init {
        initClock(initialFps)
    }

    fun initClock(fps: Int = 30) {
        val safeFps = fps.coerceIn(15, 60)
        frameIntervalNanos = 1_000_000_000L / safeFps
        baseClockNanos.set(SystemClock.elapsedRealtimeNanos())
        frameIndex = 0L
        lastEmittedPts = 0L
    }

    fun setTargetFps(fps: Int) {
        initClock(fps)
    }

    /**
     * Calculates the next presentation time stamp in nanoseconds.
     * Guarantees strict monotonicity and compensates for long-running drift.
     */
    fun getNextFrameTimestamp(): Long {
        if (baseClockNanos.get() == 0L) {
            baseClockNanos.set(SystemClock.elapsedRealtimeNanos())
        }
        frameIndex++

        // Natural physical sensor micro-jitter (±150 microseconds) to match hardware camera clocks
        val microJitterNanos = ((Math.random() * 300.0) - 150.0).toLong() * 1000L
        var calculatedPts = baseClockNanos.get() + (frameIndex * frameIntervalNanos) + microJitterNanos

        val realNow = SystemClock.elapsedRealtimeNanos()

        // Drift compensation: if clock diverges by more than 33ms, resync baseline
        if (Math.abs(realNow - calculatedPts) > 33_000_000L) {
            baseClockNanos.set(realNow - (frameIndex * frameIntervalNanos))
            calculatedPts = realNow
        }

        // Enforce strictly monotonic increase
        if (calculatedPts <= lastEmittedPts) {
            calculatedPts = lastEmittedPts + 1_000_000L // +1ms minimum step
        }

        lastEmittedPts = calculatedPts
        return calculatedPts
    }

    fun reset() {
        baseClockNanos.set(SystemClock.elapsedRealtimeNanos())
        frameIndex = 0L
        lastEmittedPts = 0L
    }
}
