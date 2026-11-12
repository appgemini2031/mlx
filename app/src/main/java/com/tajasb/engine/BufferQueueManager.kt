package com.tajasb.engine

import java.nio.ByteBuffer

/**
 * BufferQueueManager:
 * Manages GPU Gralloc memory stride alignment and surface lifecycle buffers.
 * Handles Xiaomi-specific 1088-line stride padding (1920x1088 vs 1920x1080) to eliminate
 * green lines or 8-pixel border artifacts, and provides byte buffer alignment helpers.
 */
object BufferQueueManager {

    /**
     * Calculates vertical texture UV crop scale if target surface height is padded
     * with GPU hardware stride (e.g. 1088 lines instead of 1080 lines on Xiaomi devices).
     */
    fun getStrideCropFactorV(targetWidth: Int, targetHeight: Int): Float {
        // Xiaomi 2310FPCA4G & Adreno/Mali GPU hardware pads 1080p to 1088 lines
        if (targetHeight == 1088 && targetWidth == 1920) {
            return 1080f / 1088f // Crop off 8 padding rows
        }
        if (targetHeight == 1088 && targetWidth == 1440) {
            return 1080f / 1088f
        }
        if (targetWidth == 1088 && targetHeight == 1920) {
            return 1080f / 1088f
        }
        return 1.0f
    }

    /**
     * Aligns raw YUV420/NV21 pixel byte buffer to match GPU Gralloc row stride.
     */
    fun alignYuvStride(
        srcYuv: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int
    ): ByteBuffer {
        if (rowStride <= width) {
            val buf = ByteBuffer.allocateDirect(srcYuv.size)
            buf.put(srcYuv)
            buf.rewind()
            return buf
        }
        val alignedBuffer = ByteBuffer.allocateDirect(rowStride * height * 3 / 2)
        // Copy luma plane row by row with stride padding
        for (y in 0 until height) {
            alignedBuffer.position(y * rowStride)
            val srcOffset = y * width
            alignedBuffer.put(srcYuv, srcOffset, width)
        }
        // Copy chroma planes
        val uvHeight = height / 2
        val lumaSrcSize = width * height
        val lumaDstSize = rowStride * height
        for (y in 0 until uvHeight) {
            alignedBuffer.position(lumaDstSize + (y * rowStride))
            val srcOffset = lumaSrcSize + (y * width)
            alignedBuffer.put(srcYuv, srcOffset, width)
        }
        alignedBuffer.rewind()
        return alignedBuffer
    }
}
