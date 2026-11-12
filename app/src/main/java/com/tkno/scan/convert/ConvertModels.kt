package com.tkno.scan.convert

import android.graphics.Bitmap

enum class ConvertTool {
    ExtractText,
    PdfToWord,
    TxtToDoc,
    ExtractImages
}

enum class TextOutputFormat(val extension: String, val mimeType: String) {
    TXT("txt", "text/plain"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("pdf", "application/pdf")
}

enum class TxtTargetFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
}

enum class ImageExtractMode {
    EmbeddedImages,
    PagesAsImages
}

enum class ImageOutputFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png")
}

data class ExtractedPdfImage(
    val id: String,
    val pageNumber: Int,
    val width: Int,
    val height: Int,
    val bitmap: Bitmap
)

sealed class PdfTextExtractResult {
    data class Success(
        val fullText: String,
        val pageCount: Int,
        val nonEmptyPages: Int
    ) : PdfTextExtractResult()

    object NoSelectableText : PdfTextExtractResult()

    data class Error(val message: String?) : PdfTextExtractResult()
}

data class PdfConversionStats(
    val pageCount: Int,
    val paragraphCount: Int,
    val imageCount: Int
)
