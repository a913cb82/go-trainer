package com.gotrainer.nine.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Rim width as a fraction of stone radius: identical for every stone. */
internal const val STONE_RIM_FRAC = 0.08f

/**
 * The one stone graphic. New Game chooser, board, and animations all draw
 * through here, so a graphic update lands everywhere at once. Shadows,
 * rings, and placement belong to the board, not the stone.
 *
 * @param outline rim color, or null for rimless.
 */
fun DrawScope.drawGoStone(
    center: Offset,
    radius: Float,
    black: Boolean,
    outline: Color? = null,
    alpha: Float = 1f,
) {
    if (black) {
        drawCircle(Color(0xFF111111).copy(alpha = alpha), radius = radius, center = center)
        drawCircle(
            Color(0xFF3A3A3A).copy(alpha = alpha),
            radius = radius * 0.3f,
            center = Offset(center.x - radius * 0.28f, center.y - radius * 0.28f),
        )
        if (outline != null)
            drawCircle(
                outline.copy(alpha = alpha), radius = radius, center = center,
                style = Stroke(width = radius * STONE_RIM_FRAC),
            )
    } else {
        drawCircle(Color(0xFFFDF8EC).copy(alpha = alpha), radius = radius, center = center)
        if (outline != null)
            drawCircle(
                outline.copy(alpha = alpha), radius = radius, center = center,
                style = Stroke(width = radius * STONE_RIM_FRAC),
            )
    }
}
