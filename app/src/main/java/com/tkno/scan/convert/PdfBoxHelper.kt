package com.tkno.scan.convert

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.IOException

/**
 * Shared PDFBox-Android initialization and normalization helpers.
 */
object PdfBoxHelper {

    @Volatile
    private var initialized = false

    fun ensureInit(context: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    initialized = true
                }
            }
        }
    }

    fun openPdf(context: Context, uri: Uri): PDDocument {
        ensureInit(context)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open PDF input stream")
        return inputStream.use { stream ->
            PDDocument.load(stream)
        }
    }

    fun normalizeExtractedText(raw: String): String {
        return raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\u0000", "")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
