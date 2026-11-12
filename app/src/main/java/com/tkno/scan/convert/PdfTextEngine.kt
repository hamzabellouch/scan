package com.tkno.scan.convert

import android.content.Context
import android.net.Uri
import com.tkno.scan.R
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Handles selectable text extraction from PDFs and text-only PDF creation (Arabic/RTL + Latin).
 */
object PdfTextEngine {

    private data class FontBundle(
        val arabicFont: PDFont,
        val latinFont: PDFont,
        val encodeCache: MutableMap<Pair<PDFont, Char>, Boolean> = HashMap()
    )

    private data class FontRun(
        val font: PDFont,
        val text: String
    )

    suspend fun extractTextFromPdf(
        context: Context,
        uri: Uri
    ): PdfTextExtractResult = withContext(Dispatchers.IO) {
        try {
            PdfBoxHelper.openPdf(context, uri).use { doc ->
                val pageCount = doc.numberOfPages
                if (pageCount <= 0) {
                    return@withContext PdfTextExtractResult.NoSelectableText
                }

                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }

                val pagesText = ArrayList<String>(pageCount)
                var nonEmptyPages = 0

                for (pageIndex in 1..pageCount) {
                    stripper.startPage = pageIndex
                    stripper.endPage = pageIndex
                    val raw = stripper.getText(doc)
                    val clean = PdfBoxHelper.normalizeExtractedText(raw)
                    if (clean.isNotEmpty()) {
                        nonEmptyPages++
                        pagesText.add(clean)
                    }
                }

                val combined = pagesText.joinToString("\n\n").trim()
                if (combined.isEmpty()) {
                    PdfTextExtractResult.NoSelectableText
                } else {
                    PdfTextExtractResult.Success(
                        fullText = combined,
                        pageCount = pageCount,
                        nonEmptyPages = nonEmptyPages
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            PdfTextExtractResult.Error(e.message ?: "Failed to extract text")
        }
    }

    suspend fun generateTextPdf(
        context: Context,
        text: String,
        outputStream: OutputStream,
        fontSizePt: Float = 12f
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            PdfBoxHelper.ensureInit(context)
            val normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')

            PDDocument().use { document ->
                val fonts = loadFonts(context, document)
                val pageRect = PDRectangle.A4
                val marginX = 52f
                val marginY = 56f
                val maxLineWidth = pageRect.width - (marginX * 2f)
                val lineHeight = fontSizePt * 1.5f
                val paragraphGap = fontSizePt * 0.45f

                var page = PDPage(pageRect).also { document.addPage(it) }
                var contentStream = PDPageContentStream(document, page)
                var cursorY = pageRect.height - marginY
                var pageCount = 1

                fun startNewPage() {
                    contentStream.close()
                    page = PDPage(pageRect).also { document.addPage(it) }
                    contentStream = PDPageContentStream(document, page)
                    cursorY = pageRect.height - marginY
                    pageCount++
                }

                try {
                    val paragraphs = if (normalized.isEmpty()) listOf("") else normalized.split('\n')

                    for (paragraph in paragraphs) {
                        if (paragraph.isBlank()) {
                            cursorY -= lineHeight * 0.7f
                            if (cursorY < marginY) {
                                startNewPage()
                            }
                            continue
                        }

                        val isRtl = ArabicTextShaper.isRtlParagraph(paragraph)
                        val wrappedLogicalLines = wrapLogicalParagraph(
                            paragraph = paragraph,
                            isRtl = isRtl,
                            maxWidth = maxLineWidth,
                            fontSizePt = fontSizePt,
                            fonts = fonts
                        )

                        for (logicalLine in wrappedLogicalLines) {
                            if (cursorY < marginY) {
                                startNewPage()
                            }

                            val runs = buildVisualRuns(
                                logicalLine = logicalLine,
                                isRtl = isRtl,
                                fonts = fonts
                            )
                            val lineWidth = measureRunsWidth(runs, fontSizePt)
                            val startX = if (isRtl) {
                                (pageRect.width - marginX - lineWidth).coerceAtLeast(marginX)
                            } else {
                                marginX
                            }

                            var drawX = startX
                            for (run in runs) {
                                if (run.text.isEmpty()) continue
                                contentStream.beginText()
                                contentStream.setFont(run.font, fontSizePt)
                                contentStream.newLineAtOffset(drawX, cursorY)
                                contentStream.showText(run.text)
                                contentStream.endText()
                                drawX += safeStringWidth(run.font, run.text, fontSizePt)
                            }
                            cursorY -= lineHeight
                        }

                        cursorY -= paragraphGap
                    }
                } finally {
                    contentStream.close()
                }

                document.save(outputStream)
                outputStream.flush()
                Result.success(pageCount)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadFonts(context: Context, document: PDDocument): FontBundle {
        val arabicFont: PDFont = try {
            context.resources.openRawResource(R.font.noto_sans_arabic_regular).use { stream ->
                PDType0Font.load(document, stream)
            }
        } catch (_: Exception) {
            PDType1Font.HELVETICA
        }

        val latinFile = File("/system/fonts/Roboto-Regular.ttf")
        val latinFont: PDFont = if (latinFile.exists() && latinFile.canRead()) {
            try {
                FileInputStream(latinFile).use { stream ->
                    PDType0Font.load(document, stream)
                }
            } catch (_: Exception) {
                PDType1Font.HELVETICA
            }
        } else {
            PDType1Font.HELVETICA
        }

        return FontBundle(arabicFont = arabicFont, latinFont = latinFont)
    }

    private fun wrapLogicalParagraph(
        paragraph: String,
        isRtl: Boolean,
        maxWidth: Float,
        fontSizePt: Float,
        fonts: FontBundle
    ): List<String> {
        val tokens = paragraph.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        val result = ArrayList<String>()
        var currentLine = StringBuilder()

        for (token in tokens) {
            val candidate = if (currentLine.isEmpty()) token else "$currentLine $token"
            val candidateWidth = measureLogicalWidth(candidate, isRtl, fontSizePt, fonts)
            if (currentLine.isEmpty() || candidateWidth <= maxWidth) {
                if (currentLine.isNotEmpty()) currentLine.append(' ')
                currentLine.append(token)
            } else {
                result.add(currentLine.toString())
                currentLine = StringBuilder()
                // Split ultra-long single tokens (e.g., long URLs) by characters if needed.
                if (measureLogicalWidth(token, isRtl, fontSizePt, fonts) > maxWidth) {
                    val piece = StringBuilder()
                    for (ch in token) {
                        val nextPiece = piece.toString() + ch
                        if (piece.isNotEmpty() &&
                            measureLogicalWidth(nextPiece, isRtl, fontSizePt, fonts) > maxWidth
                        ) {
                            result.add(piece.toString())
                            piece.clear()
                        }
                        piece.append(ch)
                    }
                    if (piece.isNotEmpty()) {
                        currentLine.append(piece)
                    }
                } else {
                    currentLine.append(token)
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            result.add(currentLine.toString())
        }
        return result
    }

    private fun measureLogicalWidth(
        logicalLine: String,
        isRtl: Boolean,
        fontSizePt: Float,
        fonts: FontBundle
    ): Float {
        return measureRunsWidth(buildVisualRuns(logicalLine, isRtl, fonts), fontSizePt)
    }

    private fun buildVisualRuns(
        logicalLine: String,
        isRtl: Boolean,
        fonts: FontBundle
    ): List<FontRun> {
        val shapedLogical = ArabicTextShaper.shapeLogical(logicalLine)
        val visual = ArabicTextShaper.toVisualOrder(shapedLogical, isRtl)
        if (visual.isEmpty()) return emptyList()

        val runs = ArrayList<FontRun>()
        var currentFont: PDFont? = null
        val buffer = StringBuilder()

        fun flush() {
            val font = currentFont
            if (font != null && buffer.isNotEmpty()) {
                runs.add(FontRun(font, buffer.toString()))
                buffer.clear()
            }
        }

        for (ch in visual) {
            if (ch.isSurrogate() || ch == '\t' || ch.code < 0x20) {
                val spaceFont = currentFont ?: if (isRtl) fonts.arabicFont else fonts.latinFont
                if (canEncode(spaceFont, ' ', fonts.encodeCache)) {
                    if (currentFont != spaceFont) {
                        flush()
                        currentFont = spaceFont
                    }
                    buffer.append(' ')
                }
                continue
            }

            if (ch == ' ') {
                val activeFont = currentFont ?: if (isRtl) fonts.arabicFont else fonts.latinFont
                val resolvedSpaceFont = when {
                    canEncode(activeFont, ' ', fonts.encodeCache) -> activeFont
                    canEncode(fonts.latinFont, ' ', fonts.encodeCache) -> fonts.latinFont
                    else -> fonts.arabicFont
                }
                if (currentFont != resolvedSpaceFont) {
                    flush()
                    currentFont = resolvedSpaceFont
                }
                buffer.append(' ')
                continue
            }

            val preferArabic = isArabicGlyph(ch)
            val primary = if (preferArabic) fonts.arabicFont else fonts.latinFont
            val secondary = if (preferArabic) fonts.latinFont else fonts.arabicFont

            val resolvedChar: Char
            val resolvedFont: PDFont
            when {
                canEncode(primary, ch, fonts.encodeCache) -> {
                    resolvedFont = primary
                    resolvedChar = ch
                }
                canEncode(secondary, ch, fonts.encodeCache) -> {
                    resolvedFont = secondary
                    resolvedChar = ch
                }
                canEncode(fonts.latinFont, '?', fonts.encodeCache) -> {
                    resolvedFont = fonts.latinFont
                    resolvedChar = '?'
                }
                else -> {
                    resolvedFont = fonts.arabicFont
                    resolvedChar = ' '
                }
            }

            if (currentFont != resolvedFont) {
                flush()
                currentFont = resolvedFont
            }
            buffer.append(resolvedChar)
        }
        flush()
        return runs
    }

    private fun measureRunsWidth(runs: List<FontRun>, fontSizePt: Float): Float {
        var total = 0f
        for (run in runs) {
            total += safeStringWidth(run.font, run.text, fontSizePt)
        }
        return total
    }

    private fun safeStringWidth(font: PDFont, text: String, fontSizePt: Float): Float {
        if (text.isEmpty()) return 0f
        return try {
            (font.getStringWidth(text) / 1000f) * fontSizePt
        } catch (_: Exception) {
            text.length * fontSizePt * 0.5f
        }
    }

    private fun canEncode(
        font: PDFont,
        ch: Char,
        cache: MutableMap<Pair<PDFont, Char>, Boolean>
    ): Boolean {
        val key = font to ch
        return cache.getOrPut(key) {
            try {
                font.encode(ch.toString())
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun isArabicGlyph(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x0600..0x06FF) ||
            (code in 0x0750..0x077F) ||
            (code in 0x08A0..0x08FF) ||
            (code in 0xFB50..0xFDFF) ||
            (code in 0xFE70..0xFEFF)
    }
}
