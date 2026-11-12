package com.tkno.scan.docscan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max

object DocImageProcessor {

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val normalized = ((degrees % 360f) + 360f) % 360f
        if (normalized == 0f) return source
        val matrix = Matrix().apply { postRotate(normalized) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun cropPerspective(source: Bitmap, corners: List<Offset>): Bitmap {
        if (corners.size < 4) return source

        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val tl = corners[0]
        val tr = corners[1]
        val br = corners[2]
        val bl = corners[3]

        val srcPoints = floatArrayOf(
            (tl.x * w).coerceIn(0f, w), (tl.y * h).coerceIn(0f, h),
            (tr.x * w).coerceIn(0f, w), (tr.y * h).coerceIn(0f, h),
            (br.x * w).coerceIn(0f, w), (br.y * h).coerceIn(0f, h),
            (bl.x * w).coerceIn(0f, w), (bl.y * h).coerceIn(0f, h)
        )

        // Calculate destination width and height based on distances
        val widthTop = hypot((tr.x - tl.x) * w, (tr.y - tl.y) * h)
        val widthBottom = hypot((br.x - bl.x) * w, (br.y - bl.y) * h)
        val heightLeft = hypot((bl.x - tl.x) * w, (bl.y - tl.y) * h)
        val heightRight = hypot((br.x - tr.x) * w, (br.y - tr.y) * h)

        val targetWidth = max(widthTop, widthBottom).coerceIn(100f, 4000f).toInt()
        val targetHeight = max(heightLeft, heightRight).coerceIn(100f, 4000f).toInt()

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        val polySuccess = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        return try {
            val destBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(destBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            if (polySuccess) {
                canvas.drawBitmap(source, matrix, paint)
                destBitmap
            } else {
                // Fallback bounding box crop
                fallbackCrop(source, tl, tr, br, bl, w, h)
            }
        } catch (_: Exception) {
            fallbackCrop(source, tl, tr, br, bl, w, h)
        }
    }

    private fun fallbackCrop(
        source: Bitmap,
        tl: Offset,
        tr: Offset,
        br: Offset,
        bl: Offset,
        w: Float,
        h: Float
    ): Bitmap {
        val minX = (minOf(tl.x, bl.x) * w).toInt().coerceIn(0, source.width - 1)
        val minY = (minOf(tl.y, tr.y) * h).toInt().coerceIn(0, source.height - 1)
        val maxX = (maxOf(tr.x, br.x) * w).toInt().coerceIn(minX + 1, source.width)
        val maxY = (maxOf(bl.y, br.y) * h).toInt().coerceIn(minY + 1, source.height)
        return Bitmap.createBitmap(source, minX, minY, maxX - minX, maxY - minY)
    }

    fun applyFilter(source: Bitmap, filter: DocFilter): Bitmap {
        if (filter == DocFilter.Original) return source

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix()
        when (filter) {
            DocFilter.Original -> return source
            DocFilter.Auto -> {
                // Auto contrast & clarity enhancement
                val contrast = 1.25f
                val brightness = 15f
                cm.set(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            DocFilter.Color -> {
                // Vibrant document color: boosted saturation, clean whites
                val satMatrix = ColorMatrix().apply { setSaturation(1.35f) }
                val contrastMatrix = ColorMatrix(
                    floatArrayOf(
                        1.3f, 0f, 0f, 0f, 10f,
                        0f, 1.3f, 0f, 0f, 10f,
                        0f, 0f, 1.3f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                contrastMatrix.preConcat(satMatrix)
                cm.set(contrastMatrix)
            }
            DocFilter.Grayscale -> {
                // Clean grayscale for document reading
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val contrast = ColorMatrix(
                    floatArrayOf(
                        1.35f, 0f, 0f, 0f, 5f,
                        0f, 1.35f, 0f, 0f, 5f,
                        0f, 0f, 1.35f, 0f, 5f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                contrast.preConcat(gray)
                cm.set(contrast)
            }
            DocFilter.BW -> {
                // High contrast Black & White (threshold for crisp document text)
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val highContrast = ColorMatrix(
                    floatArrayOf(
                        2.5f, 0f, 0f, 0f, -120f,
                        0f, 2.5f, 0f, 0f, -120f,
                        0f, 0f, 2.5f, 0f, -120f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                highContrast.preConcat(gray)
                cm.set(highContrast)
            }
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }
}
