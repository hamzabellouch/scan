package com.tkno.scan.docscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DocLibraryManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `classifyFile recognizes PDF, all image formats, Word, and TXT`() {
        assertEquals(LibraryFileType.PDF, DocLibraryManager.classifyFile("document.pdf"))
        assertEquals(LibraryFileType.PDF, DocLibraryManager.classifyFile("REPORT.PDF"))

        val imageFiles = listOf(
            "photo.jpg", "photo.JPEG", "photo.jfif", "image.png", "sticker.webp",
            "anim.gif", "scan.bmp", "iphone.heic", "burst.heif", "modern.avif",
            "fax.tif", "scan.tiff", "fav.ico", "icon.svg", "raw.dng",
            "camera.raw", "canon.cr2", "nikon.nef", "sony.arw", "jxl.jxl"
        )
        for (name in imageFiles) {
            assertEquals("Expected Image for $name", LibraryFileType.Image, DocLibraryManager.classifyFile(name))
        }
        assertEquals(
            LibraryFileType.Image,
            DocLibraryManager.classifyFile("custom.imgfmt", mimeTypeHint = "image/x-custom")
        )

        val wordFiles = listOf("report.docx", "legacy.doc", "macro.docm", "template.dotx", "old.dot")
        for (name in wordFiles) {
            assertEquals("Expected Word for $name", LibraryFileType.Word, DocLibraryManager.classifyFile(name))
        }

        assertEquals(LibraryFileType.Txt, DocLibraryManager.classifyFile("extracted.txt"))
        assertEquals(LibraryFileType.Txt, DocLibraryManager.classifyFile("NOTES.TXT"))
        assertEquals(
            LibraryFileType.Txt,
            DocLibraryManager.classifyFile("plain_no_ext", mimeTypeHint = "text/plain")
        )
    }

    @Test
    fun `classifyFile ignores hidden, blank, and unsupported files`() {
        assertNull(DocLibraryManager.classifyFile(""))
        assertNull(DocLibraryManager.classifyFile("   "))
        assertNull(DocLibraryManager.classifyFile(".pending-123.jpg"))
        assertNull(DocLibraryManager.classifyFile(".hidden.txt"))
        assertNull(DocLibraryManager.classifyFile("archive.zip"))
        assertNull(DocLibraryManager.classifyFile("video.mp4"))
        assertNull(DocLibraryManager.classifyFile("app.apk"))
    }

    @Test
    fun `resolveMimeType returns accurate MIME types across formats`() {
        assertEquals("application/pdf", DocLibraryManager.resolveMimeType("a.pdf", LibraryFileType.PDF))
        assertEquals("image/jpeg", DocLibraryManager.resolveMimeType("a.jpg", LibraryFileType.Image))
        assertEquals("image/png", DocLibraryManager.resolveMimeType("a.png", LibraryFileType.Image))
        assertEquals("image/webp", DocLibraryManager.resolveMimeType("a.webp", LibraryFileType.Image))
        assertEquals("image/heic", DocLibraryManager.resolveMimeType("a.heic", LibraryFileType.Image))
        assertEquals(
            "image/x-custom",
            DocLibraryManager.resolveMimeType("a.custom", LibraryFileType.Image, mimeTypeHint = "image/x-custom")
        )
        assertEquals("image/*", DocLibraryManager.resolveMimeType("a.unknownimg", LibraryFileType.Image))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            DocLibraryManager.resolveMimeType("a.docx", LibraryFileType.Word)
        )
        assertEquals("application/msword", DocLibraryManager.resolveMimeType("a.doc", LibraryFileType.Word))
        assertEquals("text/plain", DocLibraryManager.resolveMimeType("a.txt", LibraryFileType.Txt))
    }

    @Test
    fun `matchesFilter filters accurately for All, PDF, Images, Word, and Txt`() {
        for (type in LibraryFileType.entries) {
            assertTrue(DocLibraryManager.matchesFilter(type, DocTypeFilter.All))
        }

        assertTrue(DocLibraryManager.matchesFilter(LibraryFileType.PDF, DocTypeFilter.PDF))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.Image, DocTypeFilter.PDF))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.Word, DocTypeFilter.PDF))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.Txt, DocTypeFilter.PDF))

        assertTrue(DocLibraryManager.matchesFilter(LibraryFileType.Image, DocTypeFilter.Images))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.PDF, DocTypeFilter.Images))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.Word, DocTypeFilter.Images))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.Txt, DocTypeFilter.Images))

        assertTrue(DocLibraryManager.matchesFilter(LibraryFileType.Word, DocTypeFilter.Word))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.PDF, DocTypeFilter.Word))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.Image, DocTypeFilter.Word))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.Txt, DocTypeFilter.Word))

        assertTrue(DocLibraryManager.matchesFilter(LibraryFileType.Txt, DocTypeFilter.Txt))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.PDF, DocTypeFilter.Txt))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.Image, DocTypeFilter.Txt))
        assertFalse(DocLibraryManager.matchesFilter(LibraryFileType.Word, DocTypeFilter.Txt))
    }

    @Test
    fun `buildRenamedFileName preserves original extension and sanitizes input`() {
        assertEquals("notes_v2.txt", DocLibraryManager.buildRenamedFileName("notes.txt", "notes_v2"))
        assertEquals("notes_v2.txt", DocLibraryManager.buildRenamedFileName("notes.txt", "notes_v2.TXT"))
        assertEquals("scan_2.png", DocLibraryManager.buildRenamedFileName("scan_1.png", "scan_2"))
        assertEquals("photo.WEBP", DocLibraryManager.buildRenamedFileName("old.WEBP", "photo"))
        assertEquals("contract.docx", DocLibraryManager.buildRenamedFileName("draft.docx", "contract"))
        assertEquals("doc_1.pdf", DocLibraryManager.buildRenamedFileName("raw.pdf", "doc:1"))
        assertNull(DocLibraryManager.buildRenamedFileName("notes.txt", ".txt"))
        assertNull(DocLibraryManager.buildRenamedFileName("notes.txt", "   "))
    }

    @Test
    fun `scanDirectory collects PDF, diverse images, Word, and TXT and deduplicates paths`() {
        val dir = tempFolder.newFolder("Scanned")
        val nonEmptyFiles = listOf(
            "scan.pdf",
            "photo.jpg",
            "diagram.png",
            "banner.webp",
            "camera.heic",
            "essay.docx",
            "legacy.doc",
            "extracted.txt"
        )
        for (name in nonEmptyFiles) {
            File(dir, name).writeText("sample content for $name")
        }
        File(dir, ".hidden.txt").writeText("ignored")
        File(dir, "empty.txt").writeBytes(ByteArray(0))
        File(dir, "bundle.zip").writeText("zip bytes")

        val seenPaths = mutableSetOf<String>()
        val items = mutableListOf<ScannedDocumentItem>()
        DocLibraryManager.scanDirectory(dir, seenPaths, items)
        // Second scan of the same directory should not add duplicates
        DocLibraryManager.scanDirectory(dir, seenPaths, items)

        assertEquals(nonEmptyFiles.size, items.size)
        val byName = items.associateBy { it.name }
        assertEquals(LibraryFileType.PDF, byName.getValue("scan.pdf").fileType)
        assertEquals(LibraryFileType.Image, byName.getValue("photo.jpg").fileType)
        assertEquals(LibraryFileType.Image, byName.getValue("diagram.png").fileType)
        assertEquals(LibraryFileType.Image, byName.getValue("banner.webp").fileType)
        assertEquals(LibraryFileType.Image, byName.getValue("camera.heic").fileType)
        assertEquals(LibraryFileType.Word, byName.getValue("essay.docx").fileType)
        assertEquals(LibraryFileType.Word, byName.getValue("legacy.doc").fileType)
        assertEquals(LibraryFileType.Txt, byName.getValue("extracted.txt").fileType)
    }
}
