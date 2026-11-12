package com.tkno.scan.convert

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.Writer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Converts a PDF document into an editable Word (.docx) file while preserving
 * both selectable text paragraphs (including Arabic/RTL direction) and embedded images
 * interleaved in top-to-bottom visual reading order.
 */
object PdfToWordEngine {

    private sealed class PageElement(val topY: Float) {
        class RawChunk(
            topY: Float,
            val leftX: Float,
            val fontSize: Float,
            val text: String
        ) : PageElement(topY)

        class MergedLine(
            topY: Float,
            val fontSize: Float,
            val text: String
        ) : PageElement(topY)

        class ImageElement(
            topY: Float,
            val bytes: ByteArray,
            val extension: String,
            val widthPx: Int,
            val heightPx: Int
        ) : PageElement(topY)
    }

    private object NullWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
        override fun flush() = Unit
        override fun close() = Unit
    }

    suspend fun convertPdfToWord(
        context: Context,
        uri: Uri,
        outputStream: OutputStream
    ): Result<PdfConversionStats> = withContext(Dispatchers.IO) {
        try {
            PdfBoxHelper.openPdf(context, uri).use { doc ->
                val pageCount = doc.numberOfPages
                if (pageCount <= 0) {
                    return@withContext Result.failure(IllegalStateException("Empty PDF document"))
                }

                val docxBlocks = ArrayList<DocxBlock>()
                var totalParagraphs = 0
                var totalImages = 0

                for (pageIndex in 1..pageCount) {
                    val page = doc.getPage(pageIndex - 1)
                    val chunks = extractPageTextChunks(doc, pageIndex)
                    val mergedLines = mergeChunksIntoLines(chunks)
                    val images = extractPagePositionedImages(page)

                    val pageBlocks = buildOrderedPageBlocks(mergedLines, images)
                    for (block in pageBlocks) {
                        docxBlocks.add(block)
                        when (block) {
                            is DocxBlock.Paragraph -> totalParagraphs++
                            is DocxBlock.Image -> totalImages++
                            DocxBlock.PageBreak -> Unit
                        }
                    }

                    if (pageIndex < pageCount && pageBlocks.isNotEmpty()) {
                        docxBlocks.add(DocxBlock.PageBreak)
                    }
                }

                if (docxBlocks.isEmpty()) {
                    docxBlocks.add(DocxBlock.Paragraph(""))
                }

                DocxWriter.writeBlocks(docxBlocks, outputStream)
                Result.success(
                    PdfConversionStats(
                        pageCount = pageCount,
                        paragraphCount = totalParagraphs,
                        imageCount = totalImages
                    )
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractPageTextChunks(
        doc: com.tom_roush.pdfbox.pdmodel.PDDocument,
        pageIndex: Int
    ): List<PageElement.RawChunk> {
        val collected = ArrayList<PageElement.RawChunk>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                val cleaned = text.replace("\u0000", "").trim()
                if (cleaned.isEmpty() || textPositions.isEmpty()) return
                var minY = Float.MAX_VALUE
                var minX = Float.MAX_VALUE
                var fontSize = 11f
                for (tp in textPositions) {
                    if (tp.yDirAdj < minY) minY = tp.yDirAdj
                    if (tp.xDirAdj < minX) minX = tp.xDirAdj
                    if (tp.fontSizeInPt > 0f) fontSize = tp.fontSizeInPt
                }
                collected.add(
                    PageElement.RawChunk(
                        topY = minY,
                        leftX = minX,
                        fontSize = fontSize.coerceIn(6f, 72f),
                        text = cleaned
                    )
                )
            }
        }.apply {
            sortByPosition = true
            startPage = pageIndex
            endPage = pageIndex
        }

        stripper.writeText(doc, NullWriter)
        return collected
    }

    private fun mergeChunksIntoLines(
        chunks: List<PageElement.RawChunk>
    ): List<PageElement.MergedLine> {
        if (chunks.isEmpty()) return emptyList()
        val sorted = chunks.sortedWith(compareBy<PageElement.RawChunk> { it.topY }.thenBy { it.leftX })
        val lines = ArrayList<PageElement.MergedLine>()
        val currentGroup = ArrayList<PageElement.RawChunk>()

        fun flushGroup() {
            if (currentGroup.isEmpty()) return
            val avgY = currentGroup.first().topY
            val maxFont = currentGroup.maxOf { it.fontSize }
            val lineText = currentGroup.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
            if (lineText.isNotEmpty()) {
                lines.add(PageElement.MergedLine(topY = avgY, fontSize = maxFont, text = lineText))
            }
            currentGroup.clear()
        }

        for (chunk in sorted) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(chunk)
            } else {
                val anchor = currentGroup.first()
                val tolerance = max(4f, anchor.fontSize * 0.48f)
                if (abs(chunk.topY - anchor.topY) <= tolerance) {
                    currentGroup.add(chunk)
                } else {
                    flushGroup()
                    currentGroup.add(chunk)
                }
            }
        }
        flushGroup()
        return lines
    }

    private fun extractPagePositionedImages(
        page: PDPage
    ): List<PageElement.ImageElement> {
        val extracted = ArrayList<PageElement.ImageElement>()
        val pageHeight = page.cropBox?.height ?: page.mediaBox?.height ?: 842f

        val engine = object : PDFStreamEngine() {
            init {
                addOperator(Concatenate())
                addOperator(Save())
                addOperator(Restore())
                addOperator(SetGraphicsStateParameters())
                addOperator(SetMatrix())
            }

            override fun processOperator(operator: Operator, arguments: MutableList<COSBase>) {
                if (operator.name == "Do" && arguments.isNotEmpty()) {
                    val objName = arguments[0] as? COSName ?: return
                    val xObject = resources?.getXObject(objName) ?: return
                    when (xObject) {
                        is PDImageXObject -> {
                            try {
                                val rawBitmap = xObject.image ?: return
                                if (rawBitmap.width < 24 || rawBitmap.height < 24) {
                                    rawBitmap.recycle()
                                    return
                                }
                                val scaled = downscaleIfHuge(rawBitmap, maxDim = 1800)
                                if (scaled !== rawBitmap) {
                                    rawBitmap.recycle()
                                }

                                val widthPx = scaled.width
                                val heightPx = scaled.height
                                val hasAlpha = scaled.hasAlpha()
                                val ext = if (hasAlpha) "png" else "jpg"
                                val baos = ByteArrayOutputStream()
                                scaled.compress(
                                    if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                                    if (hasAlpha) 100 else 90,
                                    baos
                                )
                                scaled.recycle()

                                val ctm = graphicsState.currentTransformationMatrix
                                val maxYInPdf = max(
                                    ctm.translateY,
                                    ctm.translateY + ctm.scalingFactorY
                                )
                                val topY = (pageHeight - maxYInPdf).coerceAtLeast(0f)
                                extracted.add(
                                    PageElement.ImageElement(
                                        topY = topY,
                                        bytes = baos.toByteArray(),
                                        extension = ext,
                                        widthPx = widthPx,
                                        heightPx = heightPx
                                    )
                                )
                            } catch (_: Exception) {
                                // Skip malformed image streams cleanly without failing the whole page.
                            }
                        }
                        is PDFormXObject -> showForm(xObject)
                    }
                } else {
                    super.processOperator(operator, arguments)
                }
            }
        }

        try {
            engine.processPage(page)
        } catch (_: Exception) {
            // Continue with any images already captured on this page.
        }
        return extracted
    }

    private fun buildOrderedPageBlocks(
        lines: List<PageElement.MergedLine>,
        images: List<PageElement.ImageElement>
    ): List<DocxBlock> {
        // Interleave lines and images in visual top-down order while coalescing adjacent lines
        // into coherent paragraphs whenever no image intervenes between them.
        val sortedElements = ArrayList<PageElement>(lines.size + images.size).apply {
            addAll(lines)
            addAll(images)
            sortBy { it.topY }
        }

        val blocks = ArrayList<DocxBlock>()
        var pendingParaLines = ArrayList<PageElement.MergedLine>()

        fun flushParagraph() {
            if (pendingParaLines.isEmpty()) return
            val joined = pendingParaLines.joinToString(" ") { it.text }.trim()
            if (joined.isNotEmpty()) {
                blocks.add(
                    DocxBlock.Paragraph(
                        text = joined,
                        isRtl = ArabicTextShaper.isRtlParagraph(joined)
                    )
                )
            }
            pendingParaLines.clear()
        }

        for (element in sortedElements) {
            when (element) {
                is PageElement.ImageElement -> {
                    flushParagraph()
                    blocks.add(
                        DocxBlock.Image(
                            bytes = element.bytes,
                            extension = element.extension,
                            widthPx = element.widthPx,
                            heightPx = element.heightPx
                        )
                    )
                }
                is PageElement.MergedLine -> {
                    if (pendingParaLines.isEmpty()) {
                        pendingParaLines.add(element)
                    } else {
                        val prev = pendingParaLines.last()
                        val gap = element.topY - prev.topY
                        val sameRtl = ArabicTextShaper.isRtlParagraph(prev.text) ==
                            ArabicTextShaper.isRtlParagraph(element.text)
                        val fontThreshold = max(prev.fontSize, element.fontSize) * 1.75f
                        if (sameRtl && gap in 0f..fontThreshold) {
                            pendingParaLines.add(element)
                        } else {
                            flushParagraph()
                            pendingParaLines.add(element)
                        }
                    }
                }
                is PageElement.RawChunk -> Unit
            }
        }
        flushParagraph()
        return blocks
    }

    private fun downscaleIfHuge(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest.toFloat()
        val w = max(1, (bitmap.width * scale).roundToInt())
        val h = max(1, (bitmap.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, min(bitmap.width, w), min(bitmap.height, h), true)
    }
}
