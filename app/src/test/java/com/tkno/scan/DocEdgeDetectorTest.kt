package com.tkno.scan

import androidx.compose.ui.geometry.Offset
import com.tkno.scan.docscan.DocDetectionResult
import com.tkno.scan.docscan.DocEdgeDetector
import com.tkno.scan.docscan.DocEdgeDetectorCore
import com.tkno.scan.docscan.Point2f
import com.tkno.scan.docscan.QuadTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sin

class DocEdgeDetectorTest {

    private data class Scene(
        val y: IntArray,
        val cb: IntArray,
        val cr: IntArray,
        val w: Int,
        val h: Int
    )

    private fun pointInQuad(px: Float, py: Float, quad: List<Point2f>): Boolean {
        for (i in 0 until 4) {
            val a = quad[i]
            val b = quad[(i + 1) and 3]
            val cross = (b.x - a.x) * (py - a.y) - (b.y - a.y) * (px - a.x)
            if (cross < 0f) return false
        }
        return true
    }

    private fun renderQuadScene(
        w: Int,
        h: Int,
        normQuad: List<Point2f>,
        bgY: Int,
        bgCb: Int,
        bgCr: Int,
        docY: Int,
        docCb: Int,
        docCr: Int,
        withWoodGrain: Boolean = false,
        withInnerTextAndTable: Boolean = false,
        withFingerOcclusion: Boolean = false
    ): Scene {
        val total = w * h
        val y = IntArray(total)
        val cb = IntArray(total)
        val cr = IntArray(total)
        val pixQuad = normQuad.map { Point2f(it.x * w, it.y * h) }

        for (r in 0 until h) {
            for (c in 0 until w) {
                val idx = r * w + c
                val inDoc = pointInQuad(c.toFloat(), r.toFloat(), pixQuad)
                if (!inDoc) {
                    val grain = if (withWoodGrain) {
                        (sin(c * 0.25 + sin(r * 0.08) * 2.0) * 11.0).toInt()
                    } else 0
                    y[idx] = (bgY + grain).coerceIn(0, 255)
                    cb[idx] = bgCb
                    cr[idx] = bgCr
                } else {
                    y[idx] = docY
                    cb[idx] = docCb
                    cr[idx] = docCr
                }
            }
        }

        if (withInnerTextAndTable) {
            // Paint crisp black printed inner table and horizontal text rows strictly inside document
            val xMin = (w * 0.24f).toInt()
            val xMax = (w * 0.76f).toInt()
            val yMin = (h * 0.24f).toInt()
            val yMax = (h * 0.76f).toInt()
            val stroke = 3
            for (r in yMin..yMax) {
                for (c in xMin..xMax) {
                    val onTableBorder = r - yMin < stroke || yMax - r < stroke || c - xMin < stroke || xMax - c < stroke
                    val onTextRow = (r % 24 in 0..4) && (c in (xMin + 12)..(xMax - 12)) && ((c / 14) % 5 != 0)
                    if (onTableBorder || onTextRow) {
                        val idx = r * w + c
                        y[idx] = 20
                        cb[idx] = 128
                        cr[idx] = 128
                    }
                }
            }
        }

        if (withFingerOcclusion) {
            // Cover ~15% segment of right edge around vertical middle with a warm skin-tone thumb
            val fTop = (h * 0.42f).toInt()
            val fBot = (h * 0.56f).toInt()
            val fLeft = (w * 0.76f).toInt()
            val fRight = (w * 0.94f).toInt()
            for (r in fTop..fBot) {
                for (c in fLeft..fRight) {
                    val idx = r * w + c
                    y[idx] = 165
                    cb[idx] = 108
                    cr[idx] = 148
                }
            }
        }

        return Scene(y, cb, cr, w, h)
    }

    private fun maxCornerError(actual: List<Point2f>, expected: List<Point2f>): Float {
        assertEquals(4, actual.size)
        return (0 until 4).maxOf { i ->
            hypot(actual[i].x - expected[i].x, actual[i].y - expected[i].y)
        }
    }

    @Test
    fun testBrightDocumentOnDarkDeskWithPerspectiveSkew() {
        val expected = listOf(
            Point2f(0.16f, 0.14f),
            Point2f(0.86f, 0.19f),
            Point2f(0.90f, 0.87f),
            Point2f(0.12f, 0.83f)
        )
        val scene = renderQuadScene(
            w = 320, h = 420, normQuad = expected,
            bgY = 42, bgCb = 126, bgCr = 130,
            docY = 242, docCb = 128, docCr = 128
        )
        val res = DocEdgeDetectorCore.detectDocument(scene.y, scene.cb, scene.cr, scene.w, scene.h, highAccuracy = true)
        assertTrue("Expected document detection", res.detected)
        val err = maxCornerError(res.corners, expected)
        assertTrue("Expected corner error < 0.020 but was $err (corners=${res.corners})", err < 0.020f)
    }

    @Test
    fun testDarkCardOnLightDeskRotated() {
        val expected = listOf(
            Point2f(0.18f, 0.24f),
            Point2f(0.80f, 0.16f),
            Point2f(0.86f, 0.74f),
            Point2f(0.24f, 0.82f)
        )
        val scene = renderQuadScene(
            w = 320, h = 420, normQuad = expected,
            bgY = 228, bgCb = 122, bgCr = 134,
            docY = 30, docCb = 132, docCr = 125
        )
        val res = DocEdgeDetectorCore.detectDocument(scene.y, scene.cb, scene.cr, scene.w, scene.h, highAccuracy = false)
        assertTrue("Expected dark card detection", res.detected)
        val err = maxCornerError(res.corners, expected)
        assertTrue("Expected corner error < 0.025 but was $err (corners=${res.corners})", err < 0.025f)
    }

    @Test
    fun testWhitePaperOnWarmWoodDeskLowLumaContrast() {
        val expected = listOf(
            Point2f(0.14f, 0.13f),
            Point2f(0.85f, 0.13f),
            Point2f(0.88f, 0.88f),
            Point2f(0.11f, 0.86f)
        )
        // Only slight luminance difference (228 vs 204), distinct chroma (neutral white vs warm wood)
        val scene = renderQuadScene(
            w = 320, h = 420, normQuad = expected,
            bgY = 204, bgCb = 96, bgCr = 152,
            docY = 228, docCb = 128, docCr = 128,
            withWoodGrain = true
        )
        val res = DocEdgeDetectorCore.detectDocument(scene.y, scene.cb, scene.cr, scene.w, scene.h, highAccuracy = true)
        assertTrue("Expected low-luma chroma document detection", res.detected)
        val err = maxCornerError(res.corners, expected)
        assertTrue("Expected corner error < 0.025 but was $err (corners=${res.corners})", err < 0.025f)
    }

    @Test
    fun testDocumentWithBoldTextAndInnerTableSelectsOuterDocumentEdges() {
        val expectedOuter = listOf(
            Point2f(0.11f, 0.10f),
            Point2f(0.89f, 0.11f),
            Point2f(0.90f, 0.89f),
            Point2f(0.10f, 0.88f)
        )
        val scene = renderQuadScene(
            w = 360, h = 480, normQuad = expectedOuter,
            bgY = 92, bgCb = 112, bgCr = 142,
            docY = 244, docCb = 128, docCr = 128,
            withInnerTextAndTable = true
        )
        val res = DocEdgeDetectorCore.detectDocument(scene.y, scene.cb, scene.cr, scene.w, scene.h, highAccuracy = true)
        assertTrue("Expected outer paper detection", res.detected)
        val err = maxCornerError(res.corners, expectedOuter)
        assertTrue("Must pick outer paper edges, not inner table (err=$err, corners=${res.corners})", err < 0.022f)
    }

    @Test
    fun testHighAccuracyWithPartialEdgeOcclusionByFinger() {
        val expected = listOf(
            Point2f(0.17f, 0.14f),
            Point2f(0.84f, 0.18f),
            Point2f(0.86f, 0.86f),
            Point2f(0.13f, 0.82f)
        )
        val scene = renderQuadScene(
            w = 720, h = 960, normQuad = expected,
            bgY = 65, bgCb = 120, bgCr = 135,
            docY = 240, docCb = 128, docCr = 128,
            withInnerTextAndTable = true,
            withFingerOcclusion = true
        )
        val res = DocEdgeDetectorCore.detectDocument(scene.y, scene.cb, scene.cr, scene.w, scene.h, highAccuracy = true)
        assertTrue("Expected high-res detection", res.detected)
        val err = maxCornerError(res.corners, expected)
        assertTrue("Expected sub-pixel high accuracy < 0.018 despite occlusion, was $err (corners=${res.corners})", err < 0.018f)
    }

    @Test
    fun testUniformWallNotDetected() {
        val w = 224
        val h = 300
        val y = IntArray(w * h)
        val cb = IntArray(w * h) { 128 }
        val cr = IntArray(w * h) { 128 }
        for (r in 0 until h) {
            for (c in 0 until w) {
                // Smooth lighting gradient across blank wall, no document edges
                y[r * w + c] = (140 + (r * 30 / h) + (c * 15 / w)).coerceIn(0, 255)
            }
        }
        val res = DocEdgeDetectorCore.detectDocument(y, cb, cr, w, h, highAccuracy = false)
        assertFalse("Blank gradient wall should not false-trigger detection", res.detected)
    }

    @Test
    fun testQuadTrackerSmoothingAndHysteresis() {
        val tracker = QuadTracker(acquireFrames = 2, lostToleranceFrames = 3, smoothAlpha = 0.5f, deadbandNorm = 0.005f)
        val q1 = listOf(Point2f(0.1f, 0.1f), Point2f(0.9f, 0.1f), Point2f(0.9f, 0.9f), Point2f(0.1f, 0.9f))
        val q2 = listOf(Point2f(0.2f, 0.1f), Point2f(0.9f, 0.1f), Point2f(0.9f, 0.9f), Point2f(0.1f, 0.9f))
        val miss = DocDetectionResult(DocEdgeDetectorCore.DEFAULT_INSET_CORNERS, detected = false, confidence = 0f)

        // Frame 1: needs 2 hits before locking
        assertNull(tracker.update(DocDetectionResult(q1, true, 0.9f)))
        // Frame 2: locks immediately
        val locked = tracker.update(DocDetectionResult(q1, true, 0.9f))
        assertNotNull(locked)
        assertEquals(0.1f, locked!![0].x, 0.001f)

        // Frame 3: smooth interpolation halfway toward 0.2f
        val moved = tracker.update(DocDetectionResult(q2, true, 0.9f))!!
        assertEquals(0.15f, moved[0].x, 0.001f)

        // Two missed frames should keep holding
        assertNotNull(tracker.update(miss))
        assertNotNull(tracker.update(miss))
        // Third missed frame releases lock
        assertNull(tracker.update(miss))
    }

    @Test
    fun testRotationAndFillCenterScreenMapping() {
        // 4:3 portrait camera frame (300x400) displayed on tall 9:20 screen (900x2000) via FILL_CENTER
        // scale = max(900/300=3.0, 2000/400=5.0) = 5.0 -> displayedW = 1500, displayedH = 2000, cropX = 300, cropY = 0
        val mapped = DocEdgeDetector.mapNormalizedQuadToFillCenterScreen(
            normCorners = listOf(Point2f(0.2f, 0.1f), Point2f(0.8f, 0.1f), Point2f(0.8f, 0.9f), Point2f(0.2f, 0.9f)),
            frameWidth = 300,
            frameHeight = 400,
            viewWidth = 900f,
            viewHeight = 2000f
        )
        // x = 0.2 * 1500 - 300 = 0f; right = 0.8 * 1500 - 300 = 900f
        assertEquals(0f, mapped[0].x, 0.01f)
        assertEquals(200f, mapped[0].y, 0.01f)
        assertEquals(900f, mapped[1].x, 0.01f)

        // Rotate +90 CW maps [TL(0.1,0.2), TR(0.9,0.2), BR(0.9,0.8), BL(0.1,0.8)]
        // -> former BL(0.1,0.8) rotates to (0.2, 0.1) as new TL
        val rotated = DocEdgeDetector.rotateNormalizedCornersClockwise90(
            listOf(Offset(0.1f, 0.2f), Offset(0.9f, 0.2f), Offset(0.9f, 0.8f), Offset(0.1f, 0.8f))
        )
        assertEquals(0.2f, rotated[0].x, 0.001f)
        assertEquals(0.1f, rotated[0].y, 0.001f)
        assertEquals(0.8f, rotated[1].x, 0.001f)
        assertEquals(0.1f, rotated[1].y, 0.001f)
    }
}
