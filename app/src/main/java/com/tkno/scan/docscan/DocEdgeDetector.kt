package com.tkno.scan.docscan

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Android Bitmap and CameraX ImageProxy integration for [DocEdgeDetectorCore].
 */
object DocEdgeDetector {

    val DEFAULT_CORNERS: List<Offset> =
        DocEdgeDetectorCore.DEFAULT_INSET_CORNERS.map { Offset(it.x, it.y) }

    fun detectDocument(bitmap: Bitmap, highAccuracy: Boolean = true): DocDetectionResult {
        val targetMax = if (highAccuracy) 512 else 224
        val curMax = max(bitmap.width, bitmap.height)
        val working = if (curMax > targetMax && curMax > 0) {
            val scale = targetMax.toFloat() / curMax.toFloat()
            val w = max(24, (bitmap.width * scale).roundToInt())
            val h = max(24, (bitmap.height * scale).roundToInt())
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        val w = working.width
        val h = working.height
        val pixels = IntArray(w * h)
        working.getPixels(pixels, 0, w, 0, 0, w, h)
        if (working !== bitmap && !working.isRecycled) {
            working.recycle()
        }

        val yPlane = IntArray(w * h)
        val cbPlane = IntArray(w * h)
        val crPlane = IntArray(w * h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            yPlane[i] = ((77 * r + 150 * g + 29 * b) shr 8).coerceIn(0, 255)
            cbPlane[i] = (((-43 * r - 85 * g + 128 * b) shr 8) + 128).coerceIn(0, 255)
            crPlane[i] = (((128 * r - 107 * g - 21 * b) shr 8) + 128).coerceIn(0, 255)
        }

        return DocEdgeDetectorCore.detectDocument(
            yPlane = yPlane,
            cbPlane = cbPlane,
            crPlane = crPlane,
            width = w,
            height = h,
            highAccuracy = highAccuracy
        )
    }

    fun detectDocumentCorners(bitmap: Bitmap, highAccuracy: Boolean = true): List<Offset> {
        return detectDocument(bitmap, highAccuracy = highAccuracy)
            .corners
            .map { Offset(it.x, it.y) }
    }

    /**
     * Extracts subsampled upright YCbCr planes directly from a CameraX YUV_420_888 [ImageProxy]
     * inside its [ImageProxy.getCropRect], avoiding full-frame RGB/Bitmap allocations during live preview.
     */
    fun detectDocumentFromImageProxy(
        imageProxy: ImageProxy,
        mirrorHorizontally: Boolean = false
    ): Pair<DocDetectionResult, Pair<Int, Int>>? {
        if (imageProxy.format != ImageFormat.YUV_420_888 || imageProxy.planes.size < 3) {
            return null
        }

        val crop: Rect = imageProxy.cropRect
        val cropW = crop.width().coerceAtLeast(1)
        val cropH = crop.height().coerceAtLeast(1)
        val rotation = ((imageProxy.imageInfo.rotationDegrees % 360) + 360) % 360

        val uprightW = if (rotation == 90 || rotation == 270) cropH else cropW
        val uprightH = if (rotation == 90 || rotation == 270) cropW else cropH

        val maxSide = max(uprightW, uprightH)
        val targetMax = 224
        val scale = if (maxSide > targetMax) targetMax.toFloat() / maxSide else 1f
        val dstW = max(24, (uprightW * scale).roundToInt())
        val dstH = max(24, (uprightH * scale).roundToInt())

        val yPlaneProxy = imageProxy.planes[0]
        val uPlaneProxy = imageProxy.planes[1]
        val vPlaneProxy = imageProxy.planes[2]

        val yBuf = yPlaneProxy.buffer
        val uBuf = uPlaneProxy.buffer
        val vBuf = vPlaneProxy.buffer

        val yRowStride = yPlaneProxy.rowStride
        val yPixStride = yPlaneProxy.pixelStride
        val uRowStride = uPlaneProxy.rowStride
        val uPixStride = uPlaneProxy.pixelStride
        val vRowStride = vPlaneProxy.rowStride
        val vPixStride = vPlaneProxy.pixelStride

        val yLimit = yBuf.limit() - 1
        val uLimit = uBuf.limit() - 1
        val vLimit = vBuf.limit() - 1
        if (yLimit < 0 || uLimit < 0 || vLimit < 0) return null

        val yOut = IntArray(dstW * dstH)
        val cbOut = IntArray(dstW * dstH)
        val crOut = IntArray(dstW * dstH)

        for (dy in 0 until dstH) {
            val uy = ((dy + 0.5f) / dstH * uprightH).toInt().coerceIn(0, uprightH - 1)
            for (dx in 0 until dstW) {
                val rawUx = ((dx + 0.5f) / dstW * uprightW).toInt().coerceIn(0, uprightW - 1)
                val ux = if (mirrorHorizontally) (uprightW - 1 - rawUx) else rawUx

                val (relX, relY) = mapUprightToSource(ux, uy, cropW, cropH, rotation)
                val sx = (crop.left + relX).coerceIn(0, imageProxy.width - 1)
                val sy = (crop.top + relY).coerceIn(0, imageProxy.height - 1)

                val yIdx = (sy * yRowStride + sx * yPixStride).coerceIn(0, yLimit)
                val uvX = sx ushr 1
                val uvY = sy ushr 1
                val uIdx = (uvY * uRowStride + uvX * uPixStride).coerceIn(0, uLimit)
                val vIdx = (uvY * vRowStride + uvX * vPixStride).coerceIn(0, vLimit)

                val outIdx = dy * dstW + dx
                yOut[outIdx] = yBuf.get(yIdx).toInt() and 0xFF
                cbOut[outIdx] = uBuf.get(uIdx).toInt() and 0xFF
                crOut[outIdx] = vBuf.get(vIdx).toInt() and 0xFF
            }
        }

        val detection = DocEdgeDetectorCore.detectDocument(
            yPlane = yOut,
            cbPlane = cbOut,
            crPlane = crOut,
            width = dstW,
            height = dstH,
            highAccuracy = false
        )
        return detection to (uprightW to uprightH)
    }

    internal fun mapUprightToSource(
        ux: Int,
        uy: Int,
        srcW: Int,
        srcH: Int,
        rotationDegrees: Int
    ): Pair<Int, Int> {
        return when (((rotationDegrees % 360) + 360) % 360) {
            90 -> uy to (srcH - 1 - ux)
            180 -> (srcW - 1 - ux) to (srcH - 1 - uy)
            270 -> (srcW - 1 - uy) to ux
            else -> ux to uy
        }
    }

    /**
     * Maps normalized `[0..1]` upright camera quad points onto `PreviewView(FILL_CENTER)`
     * screen pixel coordinates.
     */
    fun mapNormalizedQuadToFillCenterScreen(
        normCorners: List<Point2f>,
        frameWidth: Int,
        frameHeight: Int,
        viewWidth: Float,
        viewHeight: Float
    ): List<Offset> {
        if (frameWidth <= 0 || frameHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) {
            return emptyList()
        }
        val scale = max(viewWidth / frameWidth.toFloat(), viewHeight / frameHeight.toFloat())
        val displayedW = frameWidth * scale
        val displayedH = frameHeight * scale
        val cropOffsetX = (displayedW - viewWidth) * 0.5f
        val cropOffsetY = (displayedH - viewHeight) * 0.5f

        return normCorners.map { pt ->
            Offset(
                x = pt.x * displayedW - cropOffsetX,
                y = pt.y * displayedH - cropOffsetY
            )
        }
    }

    /**
     * Rotates clockwise-ordered normalized corners `[TL, TR, BR, BL]` by +90 degrees clockwise
     * so crop boundaries stay locked onto the rotated image.
     */
    fun rotateNormalizedCornersClockwise90(corners: List<Offset>): List<Offset> {
        if (corners.size != 4) return DEFAULT_CORNERS
        val rotatedPts = corners.map { pt -> Offset(1f - pt.y, pt.x) }
        // Former BL becomes TL, former TL becomes TR, former TR becomes BR, former BR becomes BL
        return listOf(rotatedPts[3], rotatedPts[0], rotatedPts[1], rotatedPts[2])
    }
}
