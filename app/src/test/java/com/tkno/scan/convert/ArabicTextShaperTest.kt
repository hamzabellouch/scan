package com.tkno.scan.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicTextShaperTest {

    @Test
    fun `isRtlParagraph detects Arabic, Latin, and mixed paragraphs accurately`() {
        assertTrue(ArabicTextShaper.isRtlParagraph("مرحبا بكم في التطبيق"))
        assertTrue(ArabicTextShaper.isRtlParagraph("تقرير 2026 PDF"))
        assertFalse(ArabicTextShaper.isRtlParagraph("Hello World"))
        assertFalse(ArabicTextShaper.isRtlParagraph("Document Report 2026"))
        assertFalse(ArabicTextShaper.isRtlParagraph("   12345   "))
    }

    @Test
    fun `shapeLogical preserves English text and maps Arabic letters to presentation forms`() {
        assertEquals("Hello World!", ArabicTextShaper.shapeLogical("Hello World!"))

        val shapedArabic = ArabicTextShaper.shapeLogical("مرحبا")
        assertTrue(shapedArabic.isNotEmpty())
        assertTrue(
            "Expected Arabic presentation forms in shaped output",
            shapedArabic.all { it in '\uFE70'..'\uFEFF' }
        )
    }

    @Test
    fun `shapeLogical forms Lam-Alef ligature cleanly`() {
        val shaped = ArabicTextShaper.shapeLogical("السلام")
        // Lam + Alef in "السلام" (connected to preceding Seen -> final Lam-Alef form U+FEFC).
        assertTrue(
            "Expected Lam-Alef ligature form in U+FEF5..U+FEFC",
            shaped.any { it in '\uFEF5'..'\uFEFC' }
        )
    }

    @Test
    fun `toVisualOrder keeps embedded numbers and Latin words unreversed inside RTL lines`() {
        val logical = "تقرير 2026 PDF"
        val shaped = ArabicTextShaper.shapeLogical(logical)
        val visual = ArabicTextShaper.toVisualOrder(shaped, baseRtl = true)

        assertTrue("Expected digits 2026 to remain left-to-right", visual.contains("2026"))
        assertTrue("Expected Latin run PDF to remain left-to-right", visual.contains("PDF"))
        // In visual RTL order, the Arabic prefix appears visually to the right of later numbers/Latin tokens.
        val idxPdf = visual.indexOf("PDF")
        val idxArabic = visual.indexOfFirst { it in '\uFE70'..'\uFEFF' }
        assertTrue(idxPdf in 0 until idxArabic)
    }
}
