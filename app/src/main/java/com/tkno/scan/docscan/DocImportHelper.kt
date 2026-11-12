package com.tkno.scan.docscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Helpers for importing previously captured images and existing PDF documents
 * into editable [ScannedPage] items (for PDF creation or merging multiple PDFs).
 */
object DocImportHelper {

    fun decodeBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2400
    ): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val w = info.size.width
                    val h = info.size.height
                    val longest = max(w, h)
                    if (longest > maxDimension && longest > 0) {
                        val scale = maxDimension.toFloat() / longest.toFloat()
                        decoder.setTargetSize(
                            (w * scale).roundToInt().coerceAtLeast(1),
                            (h * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }

                var sampleSize = 1
                val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
                while (longest / sampleSize > maxDimension) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return null

                val rotation = readExifRotationDegrees(context, uri)
                if (rotation != 0f) {
                    DocImageProcessor.rotateBitmap(raw, rotation)
                } else {
                    raw
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readExifRotationDegrees(context: Context, uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
    }

    fun importPdfPages(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1800
    ): List<Bitmap> {
        val pages = mutableListOf<Bitmap>()
        var pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        try {
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { pdfPage ->
                        val baseW = pdfPage.width.coerceAtLeast(1)
                        val baseH = pdfPage.height.coerceAtLeast(1)
                        val longest = max(baseW, baseH).toFloat()
                        val targetScale = (2.0f).coerceAtMost(maxDimension.toFloat() / longest)
                            .let { scale ->
                                // Ensure at least 1x when already small, capped at maxDimension
                                if (longest * 2.0f <= maxDimension) 2.0f else maxDimension.toFloat() / longest
                            }
                        val width = (baseW * targetScale).roundToInt().coerceAtLeast(1)
                        val height = (baseH * targetScale).roundToInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.add(bitmap)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                pfd.close()
            } catch (_: Exception) {
            }
        }
        return pages
    }
}
