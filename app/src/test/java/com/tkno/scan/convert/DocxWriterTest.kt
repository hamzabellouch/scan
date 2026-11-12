package com.tkno.scan.convert

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DocxWriterTest {

    private fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                map[entry.name] = zis.readBytes()
                zis.closeEntry()
            }
        }
        return map
    }

    @Test
    fun `writePlainText emits valid OOXML zip with RTL bidi markers and XML escaping`() {
        val sample = "مرحبا بالعالم\nHello & <World> \"Test\""
        val out = ByteArrayOutputStream()
        DocxWriter.writePlainText(sample, out)

        val entries = unzipEntries(out.toByteArray())
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("word/document.xml"))

        val docXml = String(entries.getValue("word/document.xml"), StandardCharsets.UTF_8)
        assertTrue("Expected RTL paragraph property <w:bidi/>", docXml.contains("<w:bidi/>"))
        assertTrue("Expected XML escaped &amp;", docXml.contains("&amp;"))
        assertTrue("Expected XML escaped &lt;World&gt;", docXml.contains("&lt;World&gt;"))

        // Verify document.xml parses cleanly with a standard DOM XML parser.
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(entries.getValue("word/document.xml")))
        assertTrue(doc.documentElement.localName == "document")
    }

    @Test
    fun `writeBlocks emits page break and preserves all paragraphs in valid XML`() {
        val blocks = listOf(
            DocxBlock.Paragraph("First page content", isRtl = false),
            DocxBlock.PageBreak,
            DocxBlock.Paragraph("صفحة ثانية", isRtl = true)
        )
        val out = ByteArrayOutputStream()
        DocxWriter.writeBlocks(blocks, out)

        val entries = unzipEntries(out.toByteArray())
        val docBytes = entries.getValue("word/document.xml")
        val docXml = String(docBytes, StandardCharsets.UTF_8)

        assertTrue(docXml.contains("First page content"))
        assertTrue(docXml.contains("صفحة ثانية"))
        assertTrue("Expected OOXML page break", docXml.contains("w:type=\"page\""))

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(docBytes))
    }
}
