package com.tkno.scan.docscan

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Draws Google-style blue curved corner brackets and optional subtle outline/fill
 * directly onto any tracked 4-corner document quadrilateral `[TL, TR, BR, BL]`.
 */
@Composable
fun GoogleStyleQuadViewfinder(
    corners: List<Offset>,
    outlineAlpha: Float = 0f,
    fillAlpha: Float = 0f,
    color: Color = Color(0xFF8AB4F8),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (corners.size != 4) return@Canvas
        val edgeLengths = FloatArray(4) { i ->
            val a = corners[i]
            val b = corners[(i + 1) and 3]
            hypot(b.x - a.x, b.y - a.y)
        }
        val minEdge = edgeLengths.min()
        if (minEdge <= 4f) return@Canvas

        if (fillAlpha > 0.005f || outlineAlpha > 0.005f) {
            val quadPath = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                lineTo(corners[1].x, corners[1].y)
                lineTo(corners[2].x, corners[2].y)
                lineTo(corners[3].x, corners[3].y)
                close()
            }
            if (fillAlpha > 0.005f) {
                drawPath(path = quadPath, color = color.copy(alpha = fillAlpha))
            }
            if (outlineAlpha > 0.005f) {
                drawPath(
                    path = quadPath,
                    color = color.copy(alpha = outlineAlpha),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        val strokeWidth = 5.dp.toPx()
        val baseArm = (minEdge * 0.22f).coerceIn(22.dp.toPx(), 44.dp.toPx())

        for (i in 0 until 4) {
            val prev = corners[(i + 3) and 3]
            val curr = corners[i]
            val next = corners[(i + 1) and 3]

            val dx1 = prev.x - curr.x
            val dy1 = prev.y - curr.y
            val len1 = hypot(dx1, dy1)
            val dx2 = next.x - curr.x
            val dy2 = next.y - curr.y
            val len2 = hypot(dx2, dy2)
            if (len1 < 4f || len2 < 4f) continue

            val ux1 = dx1 / len1
            val uy1 = dy1 / len1
            val ux2 = dx2 / len2
            val uy2 = dy2 / len2

            val arm = baseArm.coerceAtMost(minOf(len1, len2) * 0.42f)
            val radiusInset = (arm * 0.5f).coerceIn(10.dp.toPx().coerceAtMost(arm * 0.65f), 22.dp.toPx())

            drawPath(
                path = Path().apply {
                    moveTo(curr.x + ux1 * arm, curr.y + uy1 * arm)
                    lineTo(curr.x + ux1 * radiusInset, curr.y + uy1 * radiusInset)
                    quadraticTo(curr.x, curr.y, curr.x + ux2 * radiusInset, curr.y + uy2 * radiusInset)
                    lineTo(curr.x + ux2 * arm, curr.y + uy2 * arm)
                },
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
