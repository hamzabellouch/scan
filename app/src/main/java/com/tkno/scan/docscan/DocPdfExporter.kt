package com.tkno.scan.docscan

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocPdfExporter {

    fun getDefaultFileName(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US)
        return "Scan ${sdf.format(Date())}"
    }

    fun exportPdf(
        context: Context,
        pages: List<ScannedPage>,
        fileName: String
    ): Uri? {
        if (pages.isEmpty()) return null

        val cleanName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
        val pdfDocument = PdfDocument()

        return try {
            for ((index, page) in pages.withIndex()) {
                val bitmap = page.processedBitmap
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(pdfPage)
            }

            saveToStorage(
                context = context,
                fileName = cleanName,
                mimeType = "application/pdf",
                isImage = false
            ) { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            pdfDocument.close()
        }
    }

    fun exportJpeg(
        context: Context,
        pages: List<ScannedPage>,
        fileName: String
    ): List<Uri> {
        val resultUris = mutableListOf<Uri>()
        val baseName = fileName.removeSuffix(".jpg").removeSuffix(".jpeg")

        for ((index, page) in pages.withIndex()) {
            val pageFileName = if (pages.size == 1) "$baseName.jpg" else "${baseName}_${index + 1}.jpg"
            val uri = saveToStorage(
                context = context,
                fileName = pageFileName,
                mimeType = "image/jpeg",
                isImage = true
            ) { outputStream ->
                page.processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
            if (uri != null) {
                resultUris.add(uri)
            }
        }
        return resultUris
    }

    private fun saveToStorage(
        context: Context,
        fileName: String,
        mimeType: String,
        isImage: Boolean,
        writeBlock: (OutputStream) -> Unit
    ): Uri? {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isImage) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val relativePath = if (isImage) {
                Environment.DIRECTORY_PICTURES + "/Scanned"
            } else {
                Environment.DIRECTORY_DOCUMENTS + "/Scanned"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { os ->
                        writeBlock(os)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    return uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    e.printStackTrace()
                }
            }
        }

        // Fallback to app external files directory / external storage
        return try {
            val parentDir = File(
                context.getExternalFilesDir(null) ?: context.filesDir,
                "Scanned"
            ).apply { if (!exists()) mkdirs() }

            val file = File(parentDir, fileName)
            FileOutputStream(file).use { os ->
                writeBlock(os)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
