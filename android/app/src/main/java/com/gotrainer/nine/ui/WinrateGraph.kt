package com.gotrainer.nine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** 300x60-style winrate polyline + 50% dashed midline. */
@Composable
fun WinrateGraph(history: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(60.dp)) {
        val w = size.width
        val h = size.height
        // 50% dashed midline
        drawLine(
            Color.Gray.copy(alpha = 0.5f), Offset(0f, h / 2f), Offset(w, h / 2f),
            strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )
        if (history.size < 2) return@Canvas
        val stepX = w / (history.size - 1).coerceAtLeast(1)
        val pts = history.mapIndexed { i, v ->
            Offset(i * stepX, h - (v.toFloat().coerceIn(0f, 1f) * h))
        }
        for (i in 0 until pts.size - 1) {
            drawLine(Color(0xFF27864A), pts[i], pts[i + 1], strokeWidth = 4f)
        }
        drawCircle(Color(0xFF27864A), radius = 6f, center = pts.last())
    }
}
