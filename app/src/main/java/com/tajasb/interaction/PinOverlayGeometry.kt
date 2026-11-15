package com.tajasb.interaction

import kotlin.math.roundToInt

data class PinAnchorLayout(
    val winX: Int,
    val winY: Int,
    val winW: Int,
    val winH: Int,
    val crosshairLeft: Int,
    val crosshairTop: Int,
    val badgeLeft: Int,
    val badgeTop: Int,
    val quickRowLeft: Int,
    val quickRowTop: Int,
    val placedAboveCrosshair: Boolean
)

data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * Kalkulasi geometri murni (JVM-testable) untuk:
 * 1) Menjangkar crosshair Pin tepat di koordinat target (normX, normY) agar tidak pernah bergeser
 *    saat nama pin panjang, tombol quick action "▶ Tes" dibuka/ditutup, maupun dekat tepi layar.
 * 2) Deteksi perpotongan area scan Cooldown agar jendela floating yang tidak menutupi area skill
 *    TIDAK ikut disembunyikan dan tidak berkedip.
 */
object PinAnchorGeometry {

    fun computeLayout(
        normX: Float,
        normY: Float,
        screenW: Int,
        screenH: Int,
        ringSize: Int,
        badgeW: Int,
        badgeH: Int,
        quickRowW: Int,
        quickRowH: Int,
        isActionsOpen: Boolean,
        gapBadgePx: Int,
        gapRowPx: Int
    ): PinAnchorLayout {
        val safeScreenW = screenW.coerceAtLeast(ringSize)
        val safeScreenH = screenH.coerceAtLeast(ringSize)
        val ringHalf = ringSize / 2

        val targetPx = (normX.coerceIn(0.01f, 0.99f) * safeScreenW).roundToInt()
            .coerceIn(ringHalf, (safeScreenW - ringHalf).coerceAtLeast(ringHalf))
        val targetPy = (normY.coerceIn(0.01f, 0.99f) * safeScreenH).roundToInt()
            .coerceIn(ringHalf, (safeScreenH - ringHalf).coerceAtLeast(ringHalf))

        val effectiveQuickW = if (isActionsOpen) quickRowW.coerceAtLeast(0) else 0
        val extraRowH = if (isActionsOpen) gapRowPx + quickRowH.coerceAtLeast(0) else 0
        val stackHeightBelow = gapBadgePx + badgeH.coerceAtLeast(0) + extraRowH

        val winW = maxOf(ringSize, badgeW.coerceAtLeast(0), effectiveQuickW)
        val winH = ringSize + stackHeightBelow

        val winX = (targetPx - winW / 2).coerceIn(0, (safeScreenW - winW).coerceAtLeast(0))
        val placeAbove = (targetPy + ringHalf + stackHeightBelow > safeScreenH) &&
            (targetPy - ringHalf - stackHeightBelow >= 0)

        val winY = if (placeAbove) {
            (targetPy - ringHalf - stackHeightBelow).coerceIn(0, (safeScreenH - winH).coerceAtLeast(0))
        } else {
            (targetPy - ringHalf).coerceIn(0, (safeScreenH - winH).coerceAtLeast(0))
        }

        // Crosshair selalu dikunci tepat ke (targetPx, targetPy) di dalam window
        val crosshairLeft = (targetPx - winX - ringHalf).coerceIn(0, (winW - ringSize).coerceAtLeast(0))
        val crosshairTop = (targetPy - winY - ringHalf).coerceIn(0, (winH - ringSize).coerceAtLeast(0))

        val badgeLeft = (targetPx - winX - badgeW / 2).coerceIn(0, (winW - badgeW).coerceAtLeast(0))
        val quickRowLeft = (targetPx - winX - effectiveQuickW / 2).coerceIn(0, (winW - effectiveQuickW).coerceAtLeast(0))

        val badgeTop: Int
        val quickRowTop: Int
        if (placeAbove) {
            if (isActionsOpen) {
                quickRowTop = (crosshairTop - stackHeightBelow).coerceAtLeast(0)
                badgeTop = quickRowTop + quickRowH.coerceAtLeast(0) + gapRowPx
            } else {
                quickRowTop = 0
                badgeTop = (crosshairTop - gapBadgePx - badgeH.coerceAtLeast(0)).coerceAtLeast(0)
            }
        } else {
            badgeTop = crosshairTop + ringSize + gapBadgePx
            quickRowTop = badgeTop + badgeH.coerceAtLeast(0) + gapRowPx
        }

        return PinAnchorLayout(
            winX = winX,
            winY = winY,
            winW = winW,
            winH = winH,
            crosshairLeft = crosshairLeft,
            crosshairTop = crosshairTop,
            badgeLeft = badgeLeft,
            badgeTop = badgeTop,
            quickRowLeft = quickRowLeft,
            quickRowTop = quickRowTop,
            placedAboveCrosshair = placeAbove
        )
    }
}

object ScanRegionGeometry {

    fun computeScanRectPx(
        centerNormX: Float,
        centerNormY: Float,
        widthRatio: Float,
        heightRatio: Float,
        screenW: Int,
        screenH: Int,
        minBoxPx: Int,
        paddingPx: Int
    ): PixelRect {
        val safeW = screenW.coerceAtLeast(2)
        val safeH = screenH.coerceAtLeast(2)
        val rw = (safeW * widthRatio.coerceIn(0.025f, 0.45f)).roundToInt().coerceIn(minBoxPx.coerceAtLeast(24), safeW)
        val rh = (safeH * heightRatio.coerceIn(0.025f, 0.55f)).roundToInt().coerceIn(minBoxPx.coerceAtLeast(24), safeH)
        val cx = (centerNormX.coerceIn(0.02f, 0.98f) * safeW).roundToInt()
        val cy = (centerNormY.coerceIn(0.02f, 0.98f) * safeH).roundToInt()
        return PixelRect(
            left = (cx - rw / 2 - paddingPx).coerceAtLeast(0),
            top = (cy - rh / 2 - paddingPx).coerceAtLeast(0),
            right = (cx + rw / 2 + paddingPx).coerceAtMost(safeW),
            bottom = (cy + rh / 2 + paddingPx).coerceAtMost(safeH)
        )
    }

    fun rectsIntersect(a: PixelRect, b: PixelRect): Boolean {
        return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
    }
}

/**
 * Geometri 4 panel pemblokir sentuhan (Atas, Bawah, Kiri, Kanan) yang mengelilingi lubang kecil
 * tempat gesture Accessibility (Tap / Tahan+Geser / Swipe) berlangsung, agar sentuhan manual gamer
 * tetap terblokir sementara Accessibility gesture bisa menembus langsung ke game MLBB.
 */
object TouchBlockerHoleGeometry {

    fun computePanels(
        screenW: Int,
        screenH: Int,
        hole: PixelRect
    ): List<PixelRect> {
        val sw = screenW.coerceAtLeast(2)
        val sh = screenH.coerceAtLeast(2)
        val hLeft = hole.left.coerceIn(0, sw)
        val hRight = hole.right.coerceIn(hLeft, sw)
        val hTop = hole.top.coerceIn(0, sh)
        val hBottom = hole.bottom.coerceIn(hTop, sh)

        val panels = mutableListOf<PixelRect>()
        // Top strip: penuh lebar layar dari 0..hTop
        if (hTop > 0) {
            panels += PixelRect(left = 0, top = 0, right = sw, bottom = hTop)
        }
        // Bottom strip: penuh lebar layar dari hBottom..sh
        if (hBottom < sh) {
            panels += PixelRect(left = 0, top = hBottom, right = sw, bottom = sh)
        }
        // Left strip: di samping kiri hole pada tinggi hTop..hBottom
        if (hLeft > 0 && hBottom > hTop) {
            panels += PixelRect(left = 0, top = hTop, right = hLeft, bottom = hBottom)
        }
        // Right strip: di samping kanan hole pada tinggi hTop..hBottom
        if (hRight < sw && hBottom > hTop) {
            panels += PixelRect(left = hRight, top = hTop, right = sw, bottom = hBottom)
        }
        return panels
    }
}
