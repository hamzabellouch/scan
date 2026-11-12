package com.tkno.scan.convert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
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
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Extracts either raw embedded images from a PDF (without surrounding page text/graphics)
 * or renders full PDF pages as high-resolution images.
 */
object PdfImageExtractEngine {

    suspend fun extractImages(
        context: Context,
        uri: Uri,
        mode: ImageExtractMode
    ): Result<List<ExtractedPdfImage>> = withContext(Dispatchers.IO) {
        when (mode) {
            ImageExtractMode.EmbeddedImages -> extractEmbeddedImages(context, uri)
            ImageExtractMode.PagesAsImages -> renderPagesAsImages(context, uri)
        }
    }

    private fun extractEmbeddedImages(
        context: Context,
        uri: Uri
    ): Result<List<ExtractedPdfImage>> {
        val results = ArrayList<ExtractedPdfImage>()
        val seenCosObjects: MutableSet<COSBase> = Collections.newSetFromMap(IdentityHashMap())

        return try {
            PdfBoxHelper.openPdf(context, uri).use { doc ->
                for (pageIndex in 1..doc.numberOfPages) {
                    val page = doc.getPage(pageIndex - 1)
                    var indexInPage = 0

                    val engine = object : PDFStreamEngine() {
                        init {
                            addOperator(Concatenate())
                            addOperator(Save())
                            addOperator(Restore())
                            addOperator(SetGraphicsStateParameters())
                            addOperator(SetMatrix())
                        }

                        override fun processOperator(
                            operator: Operator,
                            arguments: MutableList<COSBase>
                        ) {
                            if (operator.name == "Do" && arguments.isNotEmpty()) {
                                val objName = arguments[0] as? COSName ?: return
                                val xObject = resources?.getXObject(objName) ?: return
                                when (xObject) {
                                    is PDImageXObject -> {
                                        val cosKey: COSBase = xObject.cosObject
                                        if (!seenCosObjects.add(cosKey)) return

                                        try {
                                            val raw = xObject.image ?: return
                                            if (raw.width < 24 || raw.height < 24) {
                                                raw.recycle()
                                                return
                                            }
                                            val finalBitmap = downscaleIfHuge(raw, maxDim = 2200)
                                            if (finalBitmap !== raw) raw.recycle()
                                            indexInPage++
                                            results.add(
                                                ExtractedPdfImage(
                                                    id = "p${pageIndex}_img${indexInPage}",
                                                    pageNumber = pageIndex,
                                                    width = finalBitmap.width,
                                                    height = finalBitmap.height,
                                                    bitmap = finalBitmap
                                                )
                                            )
                                        } catch (_: Exception) {
                                            // Skip corrupted image object cleanly.
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
                        // Also inspect direct page XObjects if stream parsing failed partway.
                        for (name in page.resources?.xObjectNames ?: emptyList()) {
                            val xo = runCatching { page.resources?.getXObject(name) }.getOrNull()
                            if (xo is PDImageXObject && seenCosObjects.add(xo.cosObject)) {
                                runCatching {
                                    val raw = xo.image ?: return@runCatching
                                    if (raw.width < 24 || raw.height < 24) {
                                        raw.recycle()
                                        return@runCatching
                                    }
                                    val bmp = downscaleIfHuge(raw, maxDim = 2200)
                                    if (bmp !== raw) raw.recycle()
                                    indexInPage++
                                    results.add(
                                        ExtractedPdfImage(
                                            id = "p${pageIndex}_img${indexInPage}",
                                            pageNumber = pageIndex,
                                            width = bmp.width,
                                            height = bmp.height,
                                            bitmap = bmp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Result.success(results)
        } catch (ce: CancellationException) {
            for (item in results) {
                if (!item.bitmap.isRecycled) item.bitmap.recycle()
            }
            throw ce
        } catch (e: Exception) {
            for (item in results) {
                if (!item.bitmap.isRecycled) item.bitmap.recycle()
            }
            Result.failure(e)
        }
    }

    private fun renderPagesAsImages(
        context: Context,
        uri: Uri
    ): Result<List<ExtractedPdfImage>> {
        val results = ArrayList<ExtractedPdfImage>()
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return Result.failure(IOException("Unable to open PDF descriptor"))

            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val pageCount = renderer.pageCount
                    if (pageCount <= 0) return Result.success(emptyList())

                    val targetMaxWidth = 1654
                    for (i in 0 until pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = minOf(
                                2.4f,
                                maxOf(1.2f, targetMaxWidth.toFloat() / max(1, page.width))
                            )
                            val w = max(1, (page.width * scale).roundToInt())
                            val h = max(1, (page.height * scale).roundToInt())

                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            results.add(
                                ExtractedPdfImage(
                                    id = "page_${i + 1}",
                                    pageNumber = i + 1,
                                    width = w,
                                    height = h,
                                    bitmap = bitmap
                                )
                            )
                        }
                    }
                }
            }
            Result.success(results)
        } catch (ce: CancellationException) {
            for (item in results) {
                if (!item.bitmap.isRecycled) item.bitmap.recycle()
            }
            throw ce
        } catch (e: Exception) {
            for (item in results) {
                if (!item.bitmap.isRecycled) item.bitmap.recycle()
            }
            Result.failure(e)
        }
    }

    private fun downscaleIfHuge(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest.toFloat()
        val w = max(1, (bitmap.width * scale).roundToInt())
        val h = max(1, (bitmap.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
