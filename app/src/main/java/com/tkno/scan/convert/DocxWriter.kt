package com.tkno.scan.convert

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class DocxBlock {
    data class Paragraph(
        val text: String,
        val fontSizePt: Int = 11,
        val bold: Boolean = false,
        val isRtl: Boolean = ArabicTextShaper.isRtlParagraph(text)
    ) : DocxBlock()

    data class Image(
        val bytes: ByteArray,
        val extension: String, // "png" or "jpg"
        val widthPx: Int,
        val heightPx: Int
    ) : DocxBlock()

    object PageBreak : DocxBlock()
}

/**
 * Lightweight OOXML (.docx) writer built directly over `ZipOutputStream`
 * with full support for RTL/Arabic paragraphs, embedded images, and page breaks,
 * without adding heavy server libraries such as Apache POI.
 */
object DocxWriter {

    // A4 page content width in EMUs (~6.27 in * 914400 EMU/in ≈ 5,730,000 EMU)
    private const val MAX_IMAGE_WIDTH_EMU = 5_600_000L
    private const val MAX_IMAGE_HEIGHT_EMU = 7_600_000L
    private const val EMU_PER_PIXEL = 9525L

    fun writePlainText(text: String, outputStream: OutputStream) {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        val blocks = lines.map { line ->
            DocxBlock.Paragraph(text = line, fontSizePt = 11)
        }
        writeBlocks(blocks, outputStream)
    }

    fun writeBlocks(blocks: List<DocxBlock>, outputStream: OutputStream) {
        val imageBlocks = blocks.filterIsInstance<DocxBlock.Image>()
        val hasPng = imageBlocks.any { it.extension.equals("png", ignoreCase = true) }
        val hasJpg = imageBlocks.any {
            it.extension.equals("jpg", ignoreCase = true) || it.extension.equals("jpeg", ignoreCase = true)
        }

        val zip = ZipOutputStream(outputStream)
        try {
            // 1. [Content_Types].xml
            putUtf8Entry(zip, "[Content_Types].xml", buildContentTypesXml(hasPng, hasJpg))

            // 2. _rels/.rels
            putUtf8Entry(
                zip,
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
            )

            // 3. word/_rels/document.xml.rels
            val relsBuilder = StringBuilder()
            relsBuilder.append(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>"""
            )

            val imageRelIds = mutableMapOf<Int, String>()
            imageBlocks.forEachIndexed { idx, img ->
                val num = idx + 1
                val ext = if (img.extension.equals("png", ignoreCase = true)) "png" else "jpg"
                val relId = "rIdImg$num"
                imageRelIds[idx] = relId
                relsBuilder.append(
                    """
  <Relationship Id="$relId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image$num.$ext"/>"""
                )
            }
            relsBuilder.append("\n</Relationships>")
            putUtf8Entry(zip, "word/_rels/document.xml.rels", relsBuilder.toString())

            // 4. word/styles.xml
            putUtf8Entry(zip, "word/styles.xml", buildStylesXml())

            // 5. word/media/imageN.*
            imageBlocks.forEachIndexed { idx, img ->
                val num = idx + 1
                val ext = if (img.extension.equals("png", ignoreCase = true)) "png" else "jpg"
                zip.putNextEntry(ZipEntry("word/media/image$num.$ext"))
                zip.write(img.bytes)
                zip.closeEntry()
            }

            // 6. word/document.xml
            val docXml = buildDocumentXml(blocks, imageRelIds)
            putUtf8Entry(zip, "word/document.xml", docXml)
            zip.finish()
        } finally {
            zip.flush()
        }
    }

    private fun buildContentTypesXml(hasPng: Boolean, hasJpg: Boolean): String {
        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>"""
        )
        if (hasPng) {
            sb.append("\n  <Default Extension=\"png\" ContentType=\"image/png\"/>")
        }
        if (hasJpg) {
            sb.append("\n  <Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>")
        }
        sb.append(
            """
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""
        )
        return sb.toString()
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Arial"/>
        <w:sz w:val="22"/>
        <w:szCs w:val="22"/>
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        <w:spacing w:after="120" w:line="276" w:lineRule="auto"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:qFormat/>
  </w:style>
</w:styles>"""
    }

    private fun buildDocumentXml(
        blocks: List<DocxBlock>,
        imageRelIds: Map<Int, String>
    ): String {
        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>"""
        )

        var imageCursor = 0
        var docPrId = 1

        for (block in blocks) {
            when (block) {
                is DocxBlock.Paragraph -> {
                    val sanitized = sanitizeXmlText(block.text)
                    val halfPts = (block.fontSizePt.coerceIn(8, 72)) * 2
                    sb.append("\n    <w:p>")
                    sb.append("\n      <w:pPr>")
                    if (block.isRtl) {
                        sb.append("<w:bidi/><w:jc w:val=\"right\"/>")
                    }
                    sb.append("</w:pPr>")
                    if (sanitized.isNotEmpty()) {
                        sb.append("\n      <w:r>")
                        sb.append("<w:rPr>")
                        if (block.bold) sb.append("<w:b/><w:bCs/>")
                        if (block.isRtl) sb.append("<w:rtl/>")
                        sb.append("<w:sz w:val=\"$halfPts\"/><w:szCs w:val=\"$halfPts\"/>")
                        sb.append("</w:rPr>")
                        sb.append("<w:t xml:space=\"preserve\">${escapeXml(sanitized)}</w:t>")
                        sb.append("</w:r>")
                    }
                    sb.append("\n    </w:p>")
                }

                is DocxBlock.Image -> {
                    val relId = imageRelIds[imageCursor] ?: ""
                    val num = imageCursor + 1
                    imageCursor++
                    val (cx, cy) = computeImageEmu(block.widthPx, block.heightPx)
                    val id = docPrId++
                    sb.append(
                        """
    <w:p>
      <w:pPr><w:jc w:val="center"/></w:pPr>
      <w:r>
        <w:drawing>
          <wp:inline distT="0" distB="0" distL="0" distR="0">
            <wp:extent cx="$cx" cy="$cy"/>
            <wp:effectExtent l="0" t="0" r="0" b="0"/>
            <wp:docPr id="$id" name="Image $num"/>
            <wp:cNvGraphicFramePr>
              <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
            </wp:cNvGraphicFramePr>
            <a:graphic>
              <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                <pic:pic>
                  <pic:nvPicPr>
                    <pic:cNvPr id="$id" name="image$num"/>
                    <pic:cNvPicPr/>
                  </pic:nvPicPr>
                  <pic:blipFill>
                    <a:blip r:embed="$relId"/>
                    <a:stretch><a:fillRect/></a:stretch>
                  </pic:blipFill>
                  <pic:spPr>
                    <a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
                    <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                  </pic:spPr>
                </pic:pic>
              </a:graphicData>
            </a:graphic>
          </wp:inline>
        </w:drawing>
      </w:r>
    </w:p>"""
                    )
                }

                is DocxBlock.PageBreak -> {
                    sb.append("\n    <w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                }
            }
        }

        // A4 section properties (11906 x 16838 twips, 1-inch margins)
        sb.append(
            """
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>"""
        )
        return sb.toString()
    }

    private fun computeImageEmu(widthPx: Int, heightPx: Int): Pair<Long, Long> {
        val safeW = widthPx.coerceAtLeast(1).toLong()
        val safeH = heightPx.coerceAtLeast(1).toLong()
        var cx = safeW * EMU_PER_PIXEL
        var cy = safeH * EMU_PER_PIXEL

        if (cx > MAX_IMAGE_WIDTH_EMU) {
            val ratio = MAX_IMAGE_WIDTH_EMU.toDouble() / cx.toDouble()
            cx = MAX_IMAGE_WIDTH_EMU
            cy = (cy * ratio).toLong().coerceAtLeast(1L)
        }
        if (cy > MAX_IMAGE_HEIGHT_EMU) {
            val ratio = MAX_IMAGE_HEIGHT_EMU.toDouble() / cy.toDouble()
            cy = MAX_IMAGE_HEIGHT_EMU
            cx = (cx * ratio).toLong().coerceAtLeast(1L)
        }
        return cx to cy
    }

    private fun sanitizeXmlText(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val code = ch.code
            val valid = code == 0x09 || code == 0x0A || code == 0x0D ||
                (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
            if (valid) sb.append(ch)
        }
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun putUtf8Entry(zip: ZipOutputStream, entryName: String, content: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }
}
