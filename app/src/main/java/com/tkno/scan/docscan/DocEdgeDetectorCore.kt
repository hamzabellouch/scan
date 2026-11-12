package com.tkno.scan.docscan

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Point2f(val x: Float, val y: Float)

data class DocDetectionResult(
    val corners: List<Point2f>,
    val detected: Boolean,
    val confidence: Float
)

/**
 * Pure Kotlin multi-cue document boundary detector operating on YCbCr image planes.
 * Combines luminance + chroma gradients, gradient-oriented Hough line assembly,
 * foreground saliency convex-hull fitting, physical region-step scoring, and sub-pixel
 * IRLS edge refinement so it has no native dependencies and is directly JVM-testable.
 */
object DocEdgeDetectorCore {

    val DEFAULT_INSET_CORNERS: List<Point2f> = listOf(
        Point2f(0.06f, 0.06f),
        Point2f(0.94f, 0.06f),
        Point2f(0.94f, 0.94f),
        Point2f(0.06f, 0.94f)
    )

    private const val CHROMA_WEIGHT = 1.65f
    private const val GRAD_CAP = 180f

    internal class YCbCrPlane(
        val y: IntArray,
        val cb: IntArray,
        val cr: IntArray,
        val width: Int,
        val height: Int
    )

    private data class EdgeMaps(
        val width: Int,
        val height: Int,
        val gx: FloatArray,
        val gy: FloatArray,
        val mag: FloatArray,
        val nms: FloatArray
    )

    private data class HoughLine(
        val cosT: Float,
        val sinT: Float,
        val rho: Float,
        val thetaDeg: Int,
        val score: Float
    )

    private data class ScoredQuad(
        val corners: List<Point2f>, // Pixel coords in working resolution
        val score: Float,
        val confidence: Float
    )

    fun detectDocument(
        yPlane: IntArray,
        cbPlane: IntArray,
        crPlane: IntArray,
        width: Int,
        height: Int,
        highAccuracy: Boolean = false
    ): DocDetectionResult {
        if (width < 24 || height < 24 || yPlane.size < width * height) {
            return DocDetectionResult(DEFAULT_INSET_CORNERS, detected = false, confidence = 0f)
        }

        val targetMaxDim = if (highAccuracy) 384 else 224
        val work = downsamplePlanes(yPlane, cbPlane, crPlane, width, height, targetMaxDim)
        val smoothed = gaussianSmooth3x3(work)
        val edges = computeMultiChannelEdges(smoothed)

        val candidates = ArrayList<List<Point2f>>(160)
        collectHoughQuadCandidates(smoothed, edges, candidates)
        collectSaliencyCandidates(smoothed, candidates)

        if (candidates.isEmpty()) {
            return DocDetectionResult(DEFAULT_INSET_CORNERS, detected = false, confidence = 0f)
        }

        val scored = evaluateCandidates(smoothed, edges, candidates)
            ?: return DocDetectionResult(DEFAULT_INSET_CORNERS, detected = false, confidence = 0f)

        val normCorners = scored.corners.map { pt ->
            Point2f(
                (pt.x / work.width.toFloat()).coerceIn(0f, 1f),
                (pt.y / work.height.toFloat()).coerceIn(0f, 1f)
            )
        }

        val refinedNorm = if (highAccuracy && max(width, height) > work.width.coerceAtLeast(work.height) + 40) {
            val refineTarget = min(max(width, height), 760)
            val refinePlane = downsamplePlanes(yPlane, cbPlane, crPlane, width, height, refineTarget)
            refineQuadFisherIrls(refinePlane, normCorners, radiusFraction = 0.042f, sampleCount = 44)
        } else {
            refineQuadFisherIrls(work, normCorners, radiusFraction = 0.032f, sampleCount = 24)
        }

        val finalOrdered = orderClockwise(refinedNorm)
        if (!isValidConvexQuad(finalOrdered, 1f, 1f)) {
            return DocDetectionResult(orderClockwise(normCorners), detected = true, confidence = scored.confidence)
        }

        return DocDetectionResult(
            corners = finalOrdered.map { Point2f(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) },
            detected = true,
            confidence = scored.confidence
        )
    }

    internal fun downsamplePlanes(
        y: IntArray,
        cb: IntArray,
        cr: IntArray,
        srcW: Int,
        srcH: Int,
        maxDim: Int
    ): YCbCrPlane {
        val curMax = max(srcW, srcH)
        if (curMax <= maxDim) {
            return YCbCrPlane(y, cb, cr, srcW, srcH)
        }
        val scale = maxDim.toFloat() / curMax.toFloat()
        val dstW = max(24, (srcW * scale).roundToInt())
        val dstH = max(24, (srcH * scale).roundToInt())
        val outY = IntArray(dstW * dstH)
        val outCb = IntArray(dstW * dstH)
        val outCr = IntArray(dstW * dstH)

        // Box-averaging downscale suppresses texture grain & thin print while keeping document edges crisp
        val stepX = srcW.toFloat() / dstW
        val stepY = srcH.toFloat() / dstH
        for (dy in 0 until dstH) {
            val y0 = (dy * stepY).toInt().coerceIn(0, srcH - 1)
            val y1 = (((dy + 1) * stepY).toInt()).coerceIn(y0 + 1, srcH)
            for (dx in 0 until dstW) {
                val x0 = (dx * stepX).toInt().coerceIn(0, srcW - 1)
                val x1 = (((dx + 1) * stepX).toInt()).coerceIn(x0 + 1, srcW)
                var sumY = 0
                var sumCb = 0
                var sumCr = 0
                var count = 0
                for (sy in y0 until y1) {
                    val rowOff = sy * srcW
                    for (sx in x0 until x1) {
                        val idx = rowOff + sx
                        sumY += y[idx]
                        sumCb += cb[idx]
                        sumCr += cr[idx]
                        count++
                    }
                }
                val dstIdx = dy * dstW + dx
                outY[dstIdx] = sumY / count
                outCb[dstIdx] = sumCb / count
                outCr[dstIdx] = sumCr / count
            }
        }
        return YCbCrPlane(outY, outCb, outCr, dstW, dstH)
    }

    private fun gaussianSmooth3x3(src: YCbCrPlane): YCbCrPlane {
        val w = src.width
        val h = src.height
        val size = w * h
        val sy = src.y.copyOf()
        val scb = src.cb.copyOf()
        val scr = src.cr.copyOf()

        for (r in 1 until h - 1) {
            val rm1 = (r - 1) * w
            val row = r * w
            val rp1 = (r + 1) * w
            for (c in 1 until w - 1) {
                val idx = row + c
                sy[idx] = (
                    src.y[rm1 + c - 1] + 2 * src.y[rm1 + c] + src.y[rm1 + c + 1] +
                        2 * src.y[row + c - 1] + 4 * src.y[idx] + 2 * src.y[row + c + 1] +
                        src.y[rp1 + c - 1] + 2 * src.y[rp1 + c] + src.y[rp1 + c + 1]
                    ) shr 4
                scb[idx] = (
                    src.cb[rm1 + c - 1] + 2 * src.cb[rm1 + c] + src.cb[rm1 + c + 1] +
                        2 * src.cb[row + c - 1] + 4 * src.cb[idx] + 2 * src.cb[row + c + 1] +
                        src.cb[rp1 + c - 1] + 2 * src.cb[rp1 + c] + src.cb[rp1 + c + 1]
                    ) shr 4
                scr[idx] = (
                    src.cr[rm1 + c - 1] + 2 * src.cr[rm1 + c] + src.cr[rm1 + c + 1] +
                        2 * src.cr[row + c - 1] + 4 * src.cr[idx] + 2 * src.cr[row + c + 1] +
                        src.cr[rp1 + c - 1] + 2 * src.cr[rp1 + c] + src.cr[rp1 + c + 1]
                    ) shr 4
            }
        }
        return YCbCrPlane(sy, scb, scr, w, h)
    }

    private fun computeMultiChannelEdges(p: YCbCrPlane): EdgeMaps {
        val w = p.width
        val h = p.height
        val size = w * h
        val gx = FloatArray(size)
        val gy = FloatArray(size)
        val mag = FloatArray(size)
        val nms = FloatArray(size)

        for (r in 1 until h - 1) {
            val rm1 = (r - 1) * w
            val row = r * w
            val rp1 = (r + 1) * w
            for (c in 1 until w - 1) {
                val gxY = (-p.y[rm1 + c - 1] + p.y[rm1 + c + 1] -
                    2 * p.y[row + c - 1] + 2 * p.y[row + c + 1] -
                    p.y[rp1 + c - 1] + p.y[rp1 + c + 1]).toFloat()
                val gyY = (-p.y[rm1 + c - 1] - 2 * p.y[rm1 + c] - p.y[rm1 + c + 1] +
                    p.y[rp1 + c - 1] + 2 * p.y[rp1 + c] + p.y[rp1 + c + 1]).toFloat()

                val gxCb = (-p.cb[rm1 + c - 1] + p.cb[rm1 + c + 1] -
                    2 * p.cb[row + c - 1] + 2 * p.cb[row + c + 1] -
                    p.cb[rp1 + c - 1] + p.cb[rp1 + c + 1]) * CHROMA_WEIGHT
                val gyCb = (-p.cb[rm1 + c - 1] - 2 * p.cb[rm1 + c] - p.cb[rm1 + c + 1] +
                    p.cb[rp1 + c - 1] + 2 * p.cb[rp1 + c] + p.cb[rp1 + c + 1]) * CHROMA_WEIGHT

                val gxCr = (-p.cr[rm1 + c - 1] + p.cr[rm1 + c + 1] -
                    2 * p.cr[row + c - 1] + 2 * p.cr[row + c + 1] -
                    p.cr[rp1 + c - 1] + p.cr[rp1 + c + 1]) * CHROMA_WEIGHT
                val gyCr = (-p.cr[rm1 + c - 1] - 2 * p.cr[rm1 + c] - p.cr[rm1 + c + 1] +
                    p.cr[rp1 + c - 1] + 2 * p.cr[rp1 + c] + p.cr[rp1 + c + 1]) * CHROMA_WEIGHT

                val mY = hypot(gxY, gyY)
                val mCb = hypot(gxCb, gyCb)
                val mCr = hypot(gxCr, gyCr)

                val idx = row + c
                when {
                    mCb >= mY && mCb >= mCr -> {
                        mag[idx] = mCb
                        gx[idx] = gxCb
                        gy[idx] = gyCb
                    }
                    mCr >= mY && mCr >= mCb -> {
                        mag[idx] = mCr
                        gx[idx] = gxCr
                        gy[idx] = gyCr
                    }
                    else -> {
                        mag[idx] = mY
                        gx[idx] = gxY
                        gy[idx] = gyY
                    }
                }
            }
        }

        // 4-direction Non-Maximum Suppression thins wide edges to crisp 1px ridges
        for (r in 1 until h - 1) {
            val row = r * w
            for (c in 1 until w - 1) {
                val idx = row + c
                val m = mag[idx]
                if (m < 24f) continue
                val ax = abs(gx[idx])
                val ay = abs(gy[idx])
                val (m1, m2) = when {
                    ax > ay * 2.414f -> mag[idx - 1] to mag[idx + 1]
                    ay > ax * 2.414f -> mag[idx - w] to mag[idx + w]
                    (gx[idx] * gy[idx]) >= 0f -> mag[idx - w - 1] to mag[idx + w + 1]
                    else -> mag[idx - w + 1] to mag[idx + w - 1]
                }
                if (m >= m1 && m >= m2) {
                    nms[idx] = m
                }
            }
        }

        return EdgeMaps(w, h, gx, gy, mag, nms)
    }

    private fun collectHoughQuadCandidates(
        p: YCbCrPlane,
        edges: EdgeMaps,
        outQuads: MutableList<List<Point2f>>
    ) {
        val w = edges.width
        val h = edges.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val diag = hypot(w.toFloat(), h.toFloat())
        val stepOffset = max(3f, min(w, h) * 0.015f)

        // Adaptive threshold using percentile of NMS pixels
        val nmsValues = ArrayList<Float>(4096)
        for (i in edges.nms.indices) {
            val v = edges.nms[i]
            if (v >= 28f) nmsValues.add(v)
        }
        if (nmsValues.size < 20) return

        nmsValues.sort()
        val adaptiveThr = max(28f, nmsValues[(nmsValues.size * 0.45f).toInt()].coerceAtMost(95f))

        val numTheta = 180
        val rhoStep = max(1.2f, min(w, h) / 180f)
        val maxRho = ceilToInt(diag * 0.55f / rhoStep)
        val numRho = maxRho * 2 + 1
        val accum = FloatArray(numTheta * numRho)

        val cosLut = FloatArray(numTheta)
        val sinLut = FloatArray(numTheta)
        for (t in 0 until numTheta) {
            val rad = t * (PI / 180.0)
            cosLut[t] = cos(rad).toFloat()
            sinLut[t] = sin(rad).toFloat()
        }

        // Gradient-oriented Hough voting weighted by region-step contrast so thin inner text/table
        // rules (same paper color on both sides) don't crowd out true paper-vs-background edges
        val margin = 2
        val voterCountCap = 4500
        val step = max(1, (edges.nms.size / (voterCountCap * 4)).coerceAtLeast(1))
        var idx = 0
        for (r in margin until h - margin) {
            val rowOff = r * w
            for (c in margin until w - margin) {
                idx++
                val pIdx = rowOff + c
                val m = edges.nms[pIdx]
                if (m < adaptiveThr) continue
                if (nmsValues.size > voterCountCap && (idx % step) != 0 && m < adaptiveThr * 1.8f) continue

                val nx = edges.gx[pIdx] / m
                val ny = edges.gy[pIdx] / m
                val p1x = (c + nx * stepOffset).roundToInt().coerceIn(0, w - 1)
                val p1y = (r + ny * stepOffset).roundToInt().coerceIn(0, h - 1)
                val p2x = (c - nx * stepOffset).roundToInt().coerceIn(0, w - 1)
                val p2y = (r - ny * stepOffset).roundToInt().coerceIn(0, h - 1)
                val i1 = p1y * w + p1x
                val i2 = p2y * w + p2x
                val stepDiff = abs(p.y[i1] - p.y[i2]) +
                    CHROMA_WEIGHT * (abs(p.cb[i1] - p.cb[i2]) + abs(p.cr[i1] - p.cr[i2]))
                val regionGate = (stepDiff / 36f).coerceIn(0.08f, 1.0f)

                val angleRad = atan2(ny, nx)
                var baseDeg = ((angleRad * 180.0 / PI).roundToInt() % 180 + 180) % 180
                if (baseDeg == 180) baseDeg = 0

                val weight = (min(m, GRAD_CAP) / GRAD_CAP + 0.35f) * regionGate
                val px = c - cx
                val py = r - cy

                for (dt in -7..7) {
                    val t = ((baseDeg + dt) % 180 + 180) % 180
                    val rho = px * cosLut[t] + py * sinLut[t]
                    val rBin = (rho / rhoStep).roundToInt() + maxRho
                    if (rBin in 0 until numRho) {
                        val angFalloff = 1f - abs(dt) * 0.08f
                        accum[t * numRho + rBin] += weight * angFalloff
                    }
                }
            }
        }

        // Non-max suppression in (theta, rho) accumulator with circular theta wrap (0 == 180)
        val peaks = ArrayList<HoughLine>(32)
        val nmsThetaRad = 8
        val nmsRhoRad = max(3, (0.045f * min(w, h) / rhoStep).roundToInt())
        val minVotes = max(6f, min(w, h) * 0.08f)

        for (t in 0 until numTheta) {
            for (rb in 0 until numRho) {
                val centerVal = accum[t * numRho + rb]
                if (centerVal < minVotes) continue

                var isMax = true
                outer@ for (dt in -nmsThetaRad..nmsThetaRad) {
                    val ntRaw = t + dt
                    val flipped = ntRaw < 0 || ntRaw >= numTheta
                    val nt = ((ntRaw % numTheta) + numTheta) % numTheta
                    for (dr in -nmsRhoRad..nmsRhoRad) {
                        if (dt == 0 && dr == 0) continue
                        val baseRb = if (flipped) (2 * maxRho - rb) else rb
                        val nrb = baseRb + dr
                        if (nrb in 0 until numRho && accum[nt * numRho + nrb] > centerVal) {
                            isMax = false
                            break@outer
                        }
                    }
                }

                if (isMax) {
                    peaks.add(
                        HoughLine(
                            cosT = cosLut[t],
                            sinT = sinLut[t],
                            rho = (rb - maxRho) * rhoStep,
                            thetaDeg = t,
                            score = centerVal
                        )
                    )
                }
            }
        }

        peaks.sortByDescending { it.score }
        val nearVertLines = peaks.filter { it.thetaDeg < 45 || it.thetaDeg > 135 }.take(10)
        val nearHorizLines = peaks.filter { it.thetaDeg in 45..135 }.take(10)
        val topLines = (nearVertLines + nearHorizLines).distinct().sortedByDescending { it.score }
        if (topLines.size < 3) return

        // Add subtle inset border fallback lines ONLY if paired with 3 strong detected lines
        val borderLines = listOf(
            HoughLine(0f, 1f, (h * 0.015f) - cy, 90, 0f),
            HoughLine(0f, 1f, (h * 0.985f) - cy, 90, 0f),
            HoughLine(1f, 0f, (w * 0.015f) - cx, 0, 0f),
            HoughLine(1f, 0f, (w * 0.985f) - cx, 0, 0f)
        )

        val n = topLines.size
        // Pair top-N roughly parallel line groups, intersect with perpendicular pair
        for (i in 0 until n) {
            val l1 = topLines[i]
            for (j in i + 1 until n) {
                val l2 = topLines[j]
                if (angleDiffDeg(l1.thetaDeg, l2.thetaDeg) > 35) continue
                if (abs(lineSignedDist(l1, l2, w * 0.1f)) < min(w, h) * 0.14f) continue

                for (k in 0 until n) {
                    if (k == i || k == j) continue
                    val l3 = topLines[k]
                    val midTheta12 = circularMeanDeg(l1.thetaDeg, l2.thetaDeg)
                    val diffK = angleDiffDeg(midTheta12, l3.thetaDeg)
                    if (diffK !in 48..132) continue

                    for (m in k + 1 until n) {
                        if (m == i || m == j) continue
                        val l4 = topLines[m]
                        if (angleDiffDeg(l3.thetaDeg, l4.thetaDeg) > 35) continue
                        if (abs(lineSignedDist(l3, l4, w * 0.1f)) < min(w, h) * 0.14f) continue

                        tryBuildQuadFromLines(l1, l2, l3, l4, cx, cy, w.toFloat(), h.toFloat())?.let {
                            outQuads.add(it)
                        }
                    }

                    // 3 detected lines + 1 image-edge border fallback
                    if (i < 6 && j < 6 && k < 6) {
                        for (bl in borderLines) {
                            if (angleDiffDeg(l3.thetaDeg, bl.thetaDeg) > 35) continue
                            tryBuildQuadFromLines(l1, l2, l3, bl, cx, cy, w.toFloat(), h.toFloat())?.let {
                                outQuads.add(it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun tryBuildQuadFromLines(
        a1: HoughLine,
        a2: HoughLine,
        b1: HoughLine,
        b2: HoughLine,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float
    ): List<Point2f>? {
        val p0 = intersectLines(a1, b1, cx, cy) ?: return null
        val p1 = intersectLines(a1, b2, cx, cy) ?: return null
        val p2 = intersectLines(a2, b2, cx, cy) ?: return null
        val p3 = intersectLines(a2, b1, cx, cy) ?: return null

        val margin = min(w, h) * 0.08f
        val pts = listOf(p0, p1, p2, p3)
        for (p in pts) {
            if (p.x < -margin || p.x > w + margin || p.y < -margin || p.y > h + margin) return null
        }

        val clamped = pts.map {
            Point2f(it.x.coerceIn(0f, w - 1f), it.y.coerceIn(0f, h - 1f))
        }
        val ordered = orderClockwisePoints(clamped)
        return if (isValidConvexQuad(ordered, w, h)) ordered else null
    }

    private fun intersectLines(
        l1: HoughLine,
        l2: HoughLine,
        cx: Float,
        cy: Float
    ): Point2f? {
        val det = l1.cosT * l2.sinT - l2.cosT * l1.sinT
        if (abs(det) < 0.25f) return null
        val d1 = l1.cosT * cx + l1.sinT * cy + l1.rho
        val d2 = l2.cosT * cx + l2.sinT * cy + l2.rho
        val x = (d1 * l2.sinT - d2 * l1.sinT) / det
        val y = (l1.cosT * d2 - l2.cosT * d1) / det
        return Point2f(x, y)
    }

    private fun collectSaliencyCandidates(
        p: YCbCrPlane,
        outQuads: MutableList<List<Point2f>>
    ) {
        val small = downsamplePlanes(p.y, p.cb, p.cr, p.width, p.height, 96)
        val sw = small.width
        val sh = small.height

        // 1) Border-ring median background estimate
        val borderY = ArrayList<Int>(sw * 4 + sh * 4)
        val borderCb = ArrayList<Int>(sw * 4 + sh * 4)
        val borderCr = ArrayList<Int>(sw * 4 + sh * 4)
        val bBand = max(2, min(sw, sh) / 28)
        for (r in 0 until sh) {
            val rowOff = r * sw
            for (c in 0 until sw) {
                if (r < bBand || r >= sh - bBand || c < bBand || c >= sw - bBand) {
                    val idx = rowOff + c
                    borderY.add(small.y[idx])
                    borderCb.add(small.cb[idx])
                    borderCr.add(small.cr[idx])
                }
            }
        }
        if (borderY.isEmpty()) return
        val bgY = medianOf(borderY)
        val bgCb = medianOf(borderCb)
        val bgCr = medianOf(borderCr)

        val distMap = IntArray(sw * sh)
        for (i in distMap.indices) {
            distMap[i] = abs(small.y[i] - bgY) +
                2 * (abs(small.cb[i] - bgCb) + abs(small.cr[i] - bgCr))
        }
        val thrBorder = max(24, otsuThreshold(distMap))
        extractQuadFromMask(distMap, sw, sh, p.width.toFloat(), p.height.toFloat(), thrBorder) {
            outQuads.add(it)
        }

        // 2) Center-seeded document distance (robust if one edge/border of frame touches paper)
        val cY = ArrayList<Int>()
        val cCb = ArrayList<Int>()
        val cCr = ArrayList<Int>()
        for (r in (sh * 0.35f).toInt()..(sh * 0.65f).toInt()) {
            val rowOff = r * sw
            for (c in (sw * 0.35f).toInt()..(sw * 0.65f).toInt()) {
                val idx = rowOff + c
                cY.add(small.y[idx])
                cCb.add(small.cb[idx])
                cCr.add(small.cr[idx])
            }
        }
        if (cY.isNotEmpty()) {
            val fgY = medianOf(cY)
            val fgCb = medianOf(cCb)
            val fgCr = medianOf(cCr)
            val fgDistToCenter = abs(fgY - bgY) + 2 * (abs(fgCb - bgCb) + abs(fgCr - bgCr))
            if (fgDistToCenter >= 20) {
                val fgScore = IntArray(sw * sh)
                for (i in fgScore.indices) {
                    val dBg = abs(small.y[i] - bgY) + 2 * (abs(small.cb[i] - bgCb) + abs(small.cr[i] - bgCr))
                    val dFg = abs(small.y[i] - fgY) + 2 * (abs(small.cb[i] - fgCb) + abs(small.cr[i] - fgCr))
                    // Higher when clearly closer to center document than border background
                    fgScore[i] = (dBg - dFg + 128).coerceIn(0, 512)
                }
                extractQuadFromMask(
                    fgScore, sw, sh, p.width.toFloat(), p.height.toFloat(),
                    max(140, otsuThreshold(fgScore))
                ) { outQuads.add(it) }
            }
        }
    }

    private fun extractQuadFromMask(
        scoreMap: IntArray,
        sw: Int,
        sh: Int,
        targetW: Float,
        targetH: Float,
        threshold: Int,
        onQuad: (List<Point2f>) -> Unit
    ) {
        val total = sw * sh
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        val minCompArea = (total * 0.055f).toInt()

        var bestBoundary: List<Point2f>? = null
        var bestMetric = -1f

        for (start in 0 until total) {
            if (visited[start] || scoreMap[start] < threshold) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            val boundary = ArrayList<Point2f>(128)
            var sumX = 0f
            var sumY = 0f

            while (head < tail) {
                val cur = queue[head++]
                val cr = cur / sw
                val cc = cur % sw
                sumX += cc
                sumY += cr
                var isBoundary = cr == 0 || cr == sh - 1 || cc == 0 || cc == sw - 1

                if (cc > 0) {
                    val n = cur - 1
                    if (scoreMap[n] >= threshold) {
                        if (!visited[n]) { visited[n] = true; queue[tail++] = n }
                    } else isBoundary = true
                }
                if (cc + 1 < sw) {
                    val n = cur + 1
                    if (scoreMap[n] >= threshold) {
                        if (!visited[n]) { visited[n] = true; queue[tail++] = n }
                    } else isBoundary = true
                }
                if (cr > 0) {
                    val n = cur - sw
                    if (scoreMap[n] >= threshold) {
                        if (!visited[n]) { visited[n] = true; queue[tail++] = n }
                    } else isBoundary = true
                }
                if (cr + 1 < sh) {
                    val n = cur + sw
                    if (scoreMap[n] >= threshold) {
                        if (!visited[n]) { visited[n] = true; queue[tail++] = n }
                    } else isBoundary = true
                }

                if (isBoundary) {
                    boundary.add(Point2f(cc.toFloat(), cr.toFloat()))
                }
            }

            if (tail < minCompArea || tail > (total * 0.96f).toInt() || boundary.size < 8) continue
            val cxNorm = (sumX / tail) / sw
            val cyNorm = (sumY / tail) / sh
            val centerBias = 1.25f - hypot(cxNorm - 0.5f, cyNorm - 0.5f)
            val metric = tail * centerBias
            if (metric > bestMetric) {
                bestMetric = metric
                bestBoundary = boundary
            }
        }

        val boundaryPts = bestBoundary ?: return
        val hull = convexHull(boundaryPts)
        if (hull.size < 4) return

        val simplified = reducePolygonToQuad(hull)
        if (simplified.size == 4) {
            val scaleX = targetW / sw.toFloat()
            val scaleY = targetH / sh.toFloat()
            val scaled = simplified.map { Point2f(it.x * scaleX, it.y * scaleY) }
            val ordered = orderClockwisePoints(scaled)
            if (isValidConvexQuad(ordered, targetW, targetH)) {
                onQuad(ordered)
            }
        }
    }

    private fun evaluateCandidates(
        p: YCbCrPlane,
        edges: EdgeMaps,
        candidates: List<List<Point2f>>
    ): ScoredQuad? {
        val w = p.width.toFloat()
        val h = p.height.toFloat()
        val totalArea = w * h

        // Deduplicate near-identical candidates so evaluation is ultra-fast
        val unique = ArrayList<List<Point2f>>(min(candidates.size, 96))
        outer@ for (cand in candidates) {
            if (!isValidConvexQuad(cand, w, h)) continue
            val tol = min(w, h) * 0.018f
            for (kept in unique) {
                if (maxCornerDist(cand, kept) < tol) continue@outer
            }
            unique.add(cand)
            if (unique.size >= 96) break
        }
        if (unique.isEmpty()) return null

        val evaluated = ArrayList<ScoredQuad>(unique.size)
        val diag = hypot(w, h)
        val searchRadius = max(2, (min(w, h) * 0.012f).roundToInt())
        val stepDist = max(3f, min(w, h) * 0.022f)

        for (quad in unique) {
            val areaFrac = quadArea(quad) / totalArea
            if (areaFrac < 0.06f || areaFrac > 0.96f) continue

            val edgeScores = FloatArray(4)
            var totalRegionStep = 0f
            var borderTouchingSides = 0

            for (e in 0 until 4) {
                val p0 = quad[e]
                val p1 = quad[(e + 1) and 3]
                val dx = p1.x - p0.x
                val dy = p1.y - p0.y
                val len = hypot(dx, dy)
                if (len < min(w, h) * 0.10f) {
                    edgeScores[e] = 0f
                    continue
                }

                // Unit outward normal for clockwise quad in +x right, +y down screen coordinates
                val nx = dy / len
                val ny = -dx / len

                val onFrameBorder = isSegmentOnBorder(p0, p1, w, h)
                if (onFrameBorder) borderTouchingSides++

                val samples = 24
                var alignedSum = 0f
                var activeSamples = 0
                var sumDy = 0f
                var sumDcb = 0f
                var sumDcr = 0f
                var validContrastSamples = 0
                var consistentSamples = 0
                val diffs = ArrayList<FloatArray>(samples)

                for (s in 0 until samples) {
                    val t = 0.07f + 0.86f * (s.toFloat() / (samples - 1))
                    val sx = p0.x + dx * t
                    val sy = p0.y + dy * t

                    // Scan along outward normal for crisp Sobel response aligned with this edge
                    var bestAligned = 0f
                    for (d in -searchRadius..searchRadius) {
                        val ix = (sx + nx * d).roundToInt().coerceIn(1, p.width - 2)
                        val iy = (sy + ny * d).roundToInt().coerceIn(1, p.height - 2)
                        val idx = iy * p.width + ix
                        val dot = abs(edges.gx[idx] * nx + edges.gy[idx] * ny)
                        if (dot > bestAligned) bestAligned = dot
                    }
                    alignedSum += min(bestAligned, GRAD_CAP) / GRAD_CAP
                    if (bestAligned >= 26f) activeSamples++

                    // Region step contrast: strip INSIDE vs strip OUTSIDE (rejects thin inner printed lines!)
                    val inX = (sx - nx * stepDist).roundToInt().coerceIn(0, p.width - 1)
                    val inY = (sy - ny * stepDist).roundToInt().coerceIn(0, p.height - 1)
                    val outX = (sx + nx * stepDist).roundToInt().coerceIn(0, p.width - 1)
                    val outY = (sy + ny * stepDist).roundToInt().coerceIn(0, p.height - 1)

                    if (inX != outX || inY != outY) {
                        val inIdx = inY * p.width + inX
                        val outIdx = outY * p.width + outX
                        val dY = (p.y[inIdx] - p.y[outIdx]).toFloat()
                        val dCb = (p.cb[inIdx] - p.cb[outIdx]) * CHROMA_WEIGHT
                        val dCr = (p.cr[inIdx] - p.cr[outIdx]) * CHROMA_WEIGHT
                        sumDy += dY
                        sumDcb += dCb
                        sumDcr += dCr
                        validContrastSamples++
                        diffs.add(floatArrayOf(dY, dCb, dCr))
                    }
                }

                val coverage = activeSamples.toFloat() / samples
                val meanGrad = alignedSum / samples
                val edgeSupport = if (onFrameBorder && borderTouchingSides <= 1) {
                    max(meanGrad * coverage, 0.28f)
                } else {
                    meanGrad * (0.35f + 0.65f * coverage)
                }

                var regionStep = 0f
                if (validContrastSamples > 0) {
                    val mY = sumDy / validContrastSamples
                    val mCb = sumDcb / validContrastSamples
                    val mCr = sumDcr / validContrastSamples
                    val meanStep = sqrt(mY * mY + mCb * mCb + mCr * mCr)
                    if (meanStep > 8f) {
                        val inv = 1f / meanStep
                        val wY = mY * inv
                        val wCb = mCb * inv
                        val wCr = mCr * inv
                        for (df in diffs) {
                            if (df[0] * wY + df[1] * wCb + df[2] * wCr >= 10f) {
                                consistentSamples++
                            }
                        }
                        val consistency = consistentSamples.toFloat() / validContrastSamples
                        regionStep = (meanStep / 90f).coerceIn(0f, 1.4f) * (0.3f + 0.7f * consistency)
                    }
                }

                totalRegionStep += regionStep
                edgeScores[e] = 0.55f * edgeSupport + 0.45f * regionStep
            }

            // Avoid hallucinating an empty image frame when 2+ edges are glued to the image border with weak evidence
            if (borderTouchingSides >= 3) continue

            val minSide = edgeScores.min()
            val meanSide = edgeScores.average().toFloat()
            val avgRegionStep = totalRegionStep * 0.25f

            // Strict physical confidence check: require all 4 edges to have real backing & color/luma step
            if (minSide < 0.14f || meanSide < 0.24f || avgRegionStep < 0.11f) continue

            val geomFactor = computeGeometryQuality(quad, areaFrac, diag)
            val combinedScore =
                (0.55f * meanSide + 0.45f * minSide) *
                    geomFactor *
                    (0.82f + 0.18f * sqrt(areaFrac))

            val conf = ((meanSide * 0.6f + minSide * 0.4f) * 1.15f).coerceIn(0f, 1f)
            evaluated.add(ScoredQuad(quad, combinedScore, conf))
        }

        if (evaluated.isEmpty()) return null

        // Outer-document dominance pass: if a larger enclosing candidate has solid edges and encloses a smaller
        // box (e.g. printed dark photo/table inside the page), prefer the outer document boundary
        evaluated.sortByDescending { it.score }
        var winner = evaluated.first()
        val winnerArea = quadArea(winner.corners)

        for (i in 1 until min(evaluated.size, 20)) {
            val outer = evaluated[i]
            val outerArea = quadArea(outer.corners)
            if (outerArea > winnerArea * 1.32f &&
                outer.score >= winner.score * 0.80f &&
                isQuadMostlyInside(winner.corners, outer.corners)
            ) {
                winner = outer
                break
            }
        }

        return winner
    }

    /**
     * Refines each of the 4 normalized quad edges independently using Fisher color-discriminant
     * projection along the outside->inside edge contrast + weighted IRLS line fitting, then
     * re-intersects the 4 refined lines for sub-pixel corner accuracy.
     */
    internal fun refineQuadFisherIrls(
        p: YCbCrPlane,
        normQuad: List<Point2f>,
        radiusFraction: Float,
        sampleCount: Int
    ): List<Point2f> {
        if (normQuad.size != 4) return normQuad
        val w = p.width.toFloat()
        val h = p.height.toFloat()
        val pixQuad = normQuad.map { Point2f(it.x * w, it.y * h) }
        val searchR = max(6, (min(w, h) * radiusFraction).roundToInt())
        val stripDist = max(4f, searchR * 0.65f)

        val refinedLines = arrayOfNulls<Triple<Float, Float, Float>>(4) // (a, b, c) with ax + by + c = 0

        for (e in 0 until 4) {
            val p0 = pixQuad[e]
            val p1 = pixQuad[(e + 1) and 3]
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            val len = hypot(dx, dy)
            if (len < 16f || isSegmentOnBorder(p0, p1, w, h)) continue

            val nx = dy / len
            val ny = -dx / len

            // Estimate optimal 1D Fisher projection vector w = normalize(insideColor - outsideColor) for this edge
            var dSumY = 0f
            var dSumCb = 0f
            var dSumCr = 0f
            var colorCount = 0
            for (s in 0 until 16) {
                val t = 0.12f + 0.76f * (s / 15f)
                val sx = p0.x + dx * t
                val sy = p0.y + dy * t
                val ix = (sx - nx * stripDist).roundToInt().coerceIn(0, p.width - 1)
                val iy = (sy - ny * stripDist).roundToInt().coerceIn(0, p.height - 1)
                val ox = (sx + nx * stripDist).roundToInt().coerceIn(0, p.width - 1)
                val oy = (sy + ny * stripDist).roundToInt().coerceIn(0, p.height - 1)
                if (ix != ox || iy != oy) {
                    val iIdx = iy * p.width + ix
                    val oIdx = oy * p.width + ox
                    dSumY += p.y[iIdx] - p.y[oIdx]
                    dSumCb += (p.cb[iIdx] - p.cb[oIdx]) * CHROMA_WEIGHT
                    dSumCr += (p.cr[iIdx] - p.cr[oIdx]) * CHROMA_WEIGHT
                    colorCount++
                }
            }
            if (colorCount == 0) continue
            val normW = sqrt(dSumY * dSumY + dSumCb * dSumCb + dSumCr * dSumCr)
            if (normW < 10f * colorCount) continue
            val fY = dSumY / normW
            val fCb = dSumCb / normW
            val fCr = dSumCr / normW

            // Scan perpendicular profiles along [0.08..0.92] of this edge for the step transition peak
            val ptsX = ArrayList<Float>(sampleCount)
            val ptsY = ArrayList<Float>(sampleCount)
            val ptsW = ArrayList<Float>(sampleCount)
            val profile = FloatArray(searchR * 2 + 1)

            for (s in 0 until sampleCount) {
                val t = 0.08f + 0.84f * (s.toFloat() / (sampleCount - 1))
                val sx = p0.x + dx * t
                val sy = p0.y + dy * t

                var bestOffset = 0f
                var bestResp = 0f
                for (di in -searchR..searchR) {
                    val px = sx + nx * di
                    val py = sy + ny * di
                    // Directional derivative: mean projection just inside (toward -n) minus just outside (+n)
                    val in1X = (px - nx * 1.5f).roundToInt().coerceIn(0, p.width - 1)
                    val in1Y = (py - ny * 1.5f).roundToInt().coerceIn(0, p.height - 1)
                    val in2X = (px - nx * 3f).roundToInt().coerceIn(0, p.width - 1)
                    val in2Y = (py - ny * 3f).roundToInt().coerceIn(0, p.height - 1)
                    val out1X = (px + nx * 1.5f).roundToInt().coerceIn(0, p.width - 1)
                    val out1Y = (py + ny * 1.5f).roundToInt().coerceIn(0, p.height - 1)
                    val out2X = (px + nx * 3f).roundToInt().coerceIn(0, p.width - 1)
                    val out2Y = (py + ny * 3f).roundToInt().coerceIn(0, p.height - 1)

                    val pIn = 0.5f * (proj(p, in1Y * p.width + in1X, fY, fCb, fCr) +
                        proj(p, in2Y * p.width + in2X, fY, fCb, fCr))
                    val pOut = 0.5f * (proj(p, out1Y * p.width + out1X, fY, fCb, fCr) +
                        proj(p, out2Y * p.width + out2X, fY, fCb, fCr))

                    val step = pIn - pOut
                    profile[di + searchR] = step
                    // Gentle distance preference prevents jumping to distant clutter lines
                    val penalized = step * (1f - 0.18f * abs(di) / searchR)
                    if (penalized > bestResp) {
                        bestResp = penalized
                        bestOffset = di.toFloat()
                    }
                }

                // Parabolic sub-pixel interpolation around peak
                val pIdx = (bestOffset.roundToInt() + searchR)
                if (bestResp >= 12f && pIdx in 1 until profile.size - 1) {
                    val vL = profile[pIdx - 1]
                    val vC = profile[pIdx]
                    val vR = profile[pIdx + 1]
                    val denom = 2f * (vL - 2f * vC + vR)
                    val sub = if (abs(denom) > 1e-3f) ((vL - vR) / denom).coerceIn(-0.5f, 0.5f) else 0f
                    val exactD = bestOffset + sub
                    ptsX.add(sx + nx * exactD)
                    ptsY.add(sy + ny * exactD)
                    ptsW.add(bestResp)
                }
            }

            if (ptsX.size >= (sampleCount * 0.40f).roundToInt()) {
                val fitted = fitLineIrls(ptsX, ptsY, ptsW)
                if (fitted != null) {
                    refinedLines[e] = fitted
                }
            }
        }

        // Convert unrefined edges to exact line eq (a*x + b*y + c = 0) so intersection can mix cleanly
        val lines = Array(4) { e ->
            refinedLines[e] ?: run {
                val p0 = pixQuad[e]
                val p1 = pixQuad[(e + 1) and 3]
                val dx = p1.x - p0.x
                val dy = p1.y - p0.y
                val len = max(1f, hypot(dx, dy))
                val a = dy / len
                val b = -dx / len
                val c = -(a * p0.x + b * p0.y)
                Triple(a, b, c)
            }
        }

        // Re-intersect adjacent edges (edge 3 & edge 0 = TL, 0 & 1 = TR, 1 & 2 = BR, 2 & 3 = BL)
        val result = ArrayList<Point2f>(4)
        for (i in 0 until 4) {
            val lPrev = lines[(i + 3) and 3]
            val lCurr = lines[i]
            val inter = intersectGeneralLines(lPrev, lCurr) ?: pixQuad[i]
            // Guard against wild jumps away from coarse detection
            val maxShift = max(8f, min(w, h) * 0.065f)
            val clampedPt = if (hypot(inter.x - pixQuad[i].x, inter.y - pixQuad[i].y) <= maxShift) {
                inter
            } else {
                pixQuad[i]
            }
            result.add(Point2f((clampedPt.x / w).coerceIn(0f, 1f), (clampedPt.y / h).coerceIn(0f, 1f)))
        }
        return result
    }

    private fun proj(p: YCbCrPlane, idx: Int, fY: Float, fCb: Float, fCr: Float): Float {
        return p.y[idx] * fY + p.cb[idx] * CHROMA_WEIGHT * fCb + p.cr[idx] * CHROMA_WEIGHT * fCr
    }

    private fun fitLineIrls(xs: List<Float>, ys: List<Float>, baseW: List<Float>): Triple<Float, Float, Float>? {
        val n = xs.size
        if (n < 4) return null
        val weights = FloatArray(n) { baseW[it] }
        var a = 0f
        var b = 0f
        var c = 0f

        for (iter in 0 until 4) {
            var sumW = 0f
            var meanX = 0f
            var meanY = 0f
            for (i in 0 until n) {
                val w = weights[i]
                sumW += w
                meanX += w * xs[i]
                meanY += w * ys[i]
            }
            if (sumW <= 1e-4f) return null
            meanX /= sumW
            meanY /= sumW

            var sxx = 0f
            var sxy = 0f
            var syy = 0f
            for (i in 0 until n) {
                val w = weights[i]
                val dx = xs[i] - meanX
                val dy = ys[i] - meanY
                sxx += w * dx * dx
                sxy += w * dx * dy
                syy += w * dy * dy
            }

            // Eigenvalue eigenvector of 2x2 covariance for smallest variance (line normal)
            val theta = 0.5f * atan2(2f * sxy, sxx - syy)
            // Normal is perpendicular to principal tangent theta
            a = -sin(theta)
            b = cos(theta)
            c = -(a * meanX + b * meanY)

            // Huber robust reweighting trims outlier fingers/glare
            for (i in 0 until n) {
                val dist = abs(a * xs[i] + b * ys[i] + c)
                val huber = if (dist <= 2.2f) 1f else (2.2f / dist)
                weights[i] = baseW[i] * huber * huber
            }
        }
        return Triple(a, b, c)
    }

    private fun intersectGeneralLines(
        l1: Triple<Float, Float, Float>,
        l2: Triple<Float, Float, Float>
    ): Point2f? {
        val (a1, b1, c1) = l1
        val (a2, b2, c2) = l2
        val det = a1 * b2 - a2 * b1
        if (abs(det) < 0.25f) return null
        val x = (b1 * c2 - b2 * c1) / det
        val y = (c1 * a2 - c2 * a1) / det
        return Point2f(x, y)
    }

    private fun computeGeometryQuality(quad: List<Point2f>, areaFrac: Float, diag: Float): Float {
        var angleScoreSum = 0f
        val lengths = FloatArray(4)
        for (i in 0 until 4) {
            val prev = quad[(i + 3) and 3]
            val curr = quad[i]
            val next = quad[(i + 1) and 3]
            val v1x = prev.x - curr.x
            val v1y = prev.y - curr.y
            val v2x = next.x - curr.x
            val v2y = next.y - curr.y
            val l1 = hypot(v1x, v1y)
            val l2 = hypot(v2x, v2y)
            lengths[i] = l2
            if (l1 < 1f || l2 < 1f) return 0f
            val cosA = ((v1x * v2x + v1y * v2y) / (l1 * l2)).coerceIn(-1f, 1f)
            val deg = acos(cosA) * (180f / PI.toFloat())
            val dev = abs(deg - 90f)
            angleScoreSum += (1f - (dev / 55f)).coerceIn(0.25f, 1f)
        }
        val oppositeRatio0 = max(lengths[0], lengths[2]) / max(1f, min(lengths[0], lengths[2]))
        val oppositeRatio1 = max(lengths[1], lengths[3]) / max(1f, min(lengths[1], lengths[3]))
        if (oppositeRatio0 > 2.6f || oppositeRatio1 > 2.6f) return 0f

        val symFactor = (1f / (0.65f + 0.175f * (oppositeRatio0 + oppositeRatio1))).coerceIn(0.4f, 1f)
        val areaBias = if (areaFrac > 0.90f) 0.90f else 1f
        return (angleScoreSum * 0.25f) * symFactor * areaBias
    }

    internal fun orderClockwise(pts: List<Point2f>): List<Point2f> = orderClockwisePoints(pts)

    private fun orderClockwisePoints(pts: List<Point2f>): List<Point2f> {
        if (pts.size != 4) return pts
        val cx = (pts[0].x + pts[1].x + pts[2].x + pts[3].x) * 0.25f
        val cy = (pts[0].y + pts[1].y + pts[2].y + pts[3].y) * 0.25f
        val sorted = pts.sortedBy { atan2(it.y - cy, it.x - cx) }
        var tlIdx = 0
        var minSum = sorted[0].x + sorted[0].y
        for (i in 1 until 4) {
            val s = sorted[i].x + sorted[i].y
            if (s < minSum) {
                minSum = s
                tlIdx = i
            }
        }
        return listOf(
            sorted[tlIdx],
            sorted[(tlIdx + 1) and 3],
            sorted[(tlIdx + 2) and 3],
            sorted[(tlIdx + 3) and 3]
        )
    }

    internal fun isValidConvexQuad(pts: List<Point2f>, w: Float, h: Float): Boolean {
        if (pts.size != 4) return false
        val minEdge = min(w, h) * 0.08f
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) and 3]
            val c = pts[(i + 2) and 3]
            val e1x = b.x - a.x
            val e1y = b.y - a.y
            val e2x = c.x - b.x
            val e2y = c.y - b.y
            val l1 = hypot(e1x, e1y)
            val l2 = hypot(e2x, e2y)
            if (l1 < minEdge || l2 < minEdge) return false
            // Clockwise convexity in +x right, +y down screen coords requires strictly positive cross product
            if (e1x * e2y - e1y * e2x <= 0f) return false
            val cosCorner = ((-e1x * e2x - e1y * e2y) / (l1 * l2)).coerceIn(-1f, 1f)
            val angleDeg = acos(cosCorner) * (180f / PI.toFloat())
            if (angleDeg < 42f || angleDeg > 138f) return false
        }
        return true
    }

    private fun isSegmentOnBorder(p0: Point2f, p1: Point2f, w: Float, h: Float): Boolean {
        val tolX = w * 0.025f
        val tolY = h * 0.025f
        return (p0.x < tolX && p1.x < tolX) ||
            (p0.x > w - tolX && p1.x > w - tolX) ||
            (p0.y < tolY && p1.y < tolY) ||
            (p0.y > h - tolY && p1.y > h - tolY)
    }

    private fun isQuadMostlyInside(inner: List<Point2f>, outer: List<Point2f>): Boolean {
        var insideCount = 0
        for (pt in inner) {
            // Check point against all 4 clockwise edges with small margin
            var insideAll = true
            for (i in 0 until 4) {
                val a = outer[i]
                val b = outer[(i + 1) and 3]
                val cross = (b.x - a.x) * (pt.y - a.y) - (b.y - a.y) * (pt.x - a.x)
                if (cross < -hypot(b.x - a.x, b.y - a.y) * 8f) {
                    insideAll = false
                    break
                }
            }
            if (insideAll) insideCount++
        }
        return insideCount >= 3
    }

    private fun convexHull(points: List<Point2f>): List<Point2f> {
        if (points.size <= 3) return points
        val sorted = points.sortedWith(compareBy<Point2f> { it.x }.thenBy { it.y })
        val lower = ArrayList<Point2f>()
        for (p in sorted) {
            while (lower.size >= 2 && cross2D(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }
        val upper = ArrayList<Point2f>()
        for (i in sorted.indices.reversed()) {
            val p = sorted[i]
            while (upper.size >= 2 && cross2D(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        lower.addAll(upper)
        return lower
    }

    private fun reducePolygonToQuad(poly: List<Point2f>): List<Point2f> {
        if (poly.size <= 4) return poly
        val list = poly.toMutableList()
        while (list.size > 4) {
            var minIdx = 0
            var minArea = Float.MAX_VALUE
            val n = list.size
            for (i in 0 until n) {
                val prev = list[(i + n - 1) % n]
                val curr = list[i]
                val next = list[(i + 1) % n]
                val area = abs((curr.x - prev.x) * (next.y - prev.y) - (curr.y - prev.y) * (next.x - prev.x))
                if (area < minArea) {
                    minArea = area
                    minIdx = i
                }
            }
            list.removeAt(minIdx)
        }
        return list
    }

    private fun cross2D(o: Point2f, a: Point2f, b: Point2f): Float =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    private fun quadArea(pts: List<Point2f>): Float {
        var sum = 0f
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) and 3]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) * 0.5f
    }

    private fun maxCornerDist(a: List<Point2f>, b: List<Point2f>): Float {
        var m = 0f
        for (i in 0 until 4) {
            val d = hypot(a[i].x - b[i].x, a[i].y - b[i].y)
            if (d > m) m = d
        }
        return m
    }

    private fun otsuThreshold(values: IntArray): Int {
        val hist = IntArray(513)
        var maxV = 0
        for (v in values) {
            val bin = v.coerceIn(0, 512)
            hist[bin]++
            if (bin > maxV) maxV = bin
        }
        if (maxV == 0) return 0
        val total = values.size
        var sum = 0.0
        for (i in 0..maxV) sum += i * hist[i].toDouble()

        var sumB = 0.0
        var wB = 0
        var maxBetween = -1.0
        var bestThr = maxV / 2
        for (t in 0..maxV) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val diff = mB - mF
            val between = wB.toDouble() * wF.toDouble() * diff * diff
            if (between > maxBetween) {
                maxBetween = between
                bestThr = t
            }
        }
        return bestThr
    }

    private fun medianOf(list: ArrayList<Int>): Int {
        list.sort()
        return list[list.size / 2]
    }

    private fun angleDiffDeg(a: Int, b: Int): Int {
        val d = abs(a - b) % 180
        return min(d, 180 - d)
    }

    private fun circularMeanDeg(a: Int, b: Int): Int {
        var diff = b - a
        if (diff > 90) diff -= 180
        if (diff < -90) diff += 180
        return (((a + diff / 2) % 180) + 180) % 180
    }

    private fun lineSignedDist(a: HoughLine, b: HoughLine, scale: Float): Float {
        val sameSign = (a.cosT * b.cosT + a.sinT * b.sinT) >= 0f
        return if (sameSign) (a.rho - b.rho) else (a.rho + b.rho) + 0f * scale
    }

    private fun ceilToInt(v: Float): Int {
        val i = v.toInt()
        return if (v > i) i + 1 else i
    }
}

/**
 * Temporal smoother and lock hysteresis tracker for live camera quad overlay.
 * Prevents single-frame flicker when a document enters/exits view and eliminates hand jitter.
 */
class QuadTracker(
    private val acquireFrames: Int = 2,
    private val lostToleranceFrames: Int = 5,
    private val smoothAlpha: Float = 0.36f,
    private val deadbandNorm: Float = 0.0045f
) {
    private var trackedCorners: List<Point2f>? = null
    private var hitStreak = 0
    private var missStreak = 0

    fun update(detection: DocDetectionResult): List<Point2f>? {
        if (!detection.detected || detection.corners.size != 4) {
            hitStreak = 0
            missStreak++
            if (missStreak >= lostToleranceFrames) {
                trackedCorners = null
            }
            return trackedCorners
        }

        missStreak = 0
        hitStreak++
        val candidate = detection.corners
        val current = trackedCorners

        if (current == null) {
            if (hitStreak >= acquireFrames) {
                trackedCorners = candidate
            }
            return trackedCorners
        }

        // Reject abrupt bogus jumps that last < acquireFrames by checking continuity
        val maxShift = (0 until 4).maxOf { i ->
            hypot(candidate[i].x - current[i].x, candidate[i].y - current[i].y)
        }
        val alpha = if (maxShift > 0.14f) 0.52f else smoothAlpha

        val smoothed = (0 until 4).map { i ->
            val dx = candidate[i].x - current[i].x
            val dy = candidate[i].y - current[i].y
            if (hypot(dx, dy) < deadbandNorm) {
                current[i]
            } else {
                Point2f(
                    (current[i].x + dx * alpha).coerceIn(0f, 1f),
                    (current[i].y + dy * alpha).coerceIn(0f, 1f)
                )
            }
        }
        trackedCorners = smoothed
        return smoothed
    }

    fun reset() {
        trackedCorners = null
        hitStreak = 0
        missStreak = 0
    }
}
