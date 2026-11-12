package com.tkno.scan.convert

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Storage, MediaStore, SAF, ZIP archival, and FileProvider share utilities for Convert tools.
 */
object ConvertStorageHelper {

    private const val FOLDER_NAME = "Scanned"

    fun getDisplayNameWithoutExt(context: Context, uri: Uri, fallback: String = "Converted"): String {
        val rawName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull() ?: uri.lastPathSegment

        val cleaned = rawName
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            ?.trim()
        return if (!cleaned.isNullOrBlank()) cleaned else fallback
    }

    suspend fun readTextFromUri(
        context: Context,
        uri: Uri,
        maxBytes: Long = 2_000_000L
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        throw IOException("File is too large (max 2 MB)")
                    }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            } ?: return@withContext Result.failure(IOException("Unable to read text file"))

            val decoded = when {
                raw.size >= 2 && raw[0] == 0xFE.toByte() && raw[1] == 0xFF.toByte() ->
                    String(raw, 2, raw.size - 2, StandardCharsets.UTF_16BE)
                raw.size >= 2 && raw[0] == 0xFF.toByte() && raw[1] == 0xFE.toByte() ->
                    String(raw, 2, raw.size - 2, StandardCharsets.UTF_16LE)
                raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte() ->
                    String(raw, 3, raw.size - 3, StandardCharsets.UTF_8)
                else -> String(raw, StandardCharsets.UTF_8)
            }
            Result.success(decoded.replace("\r\n", "\n").replace('\r', '\n'))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveDocumentStream(
        context: Context,
        baseName: String,
        extension: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val safeName = sanitizeBaseName(baseName)
            val fileName = "$safeName.$extension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER_NAME"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
                    ?: throw IOException("MediaStore insert returned null")

                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        writer(out)
                        out.flush()
                    } ?: throw IOException("Unable to open MediaStore output stream")

                    val ready = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, ready, null, null)
                    Result.success(uri)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
                    FOLDER_NAME
                ).apply { mkdirs() }
                val dest = uniqueFile(dir, safeName, extension)
                FileOutputStream(dest).use { out ->
                    writer(out)
                    out.flush()
                }
                Result.success(Uri.fromFile(dest))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveImagesToGallery(
        context: Context,
        images: List<ExtractedPdfImage>,
        baseName: String,
        format: ImageOutputFormat
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val safeBase = sanitizeBaseName(baseName)
            val compressFormat = if (format == ImageOutputFormat.PNG) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            val quality = if (format == ImageOutputFormat.PNG) 100 else 95

            var savedCount = 0
            for ((index, item) in images.withIndex()) {
                val fileName = "${safeBase}_${item.id}_${index + 1}.${format.extension}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/$FOLDER_NAME"
                        )
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: continue
                    try {
                        resolver.openOutputStream(uri)?.use { out ->
                            item.bitmap.compress(compressFormat, quality, out)
                            out.flush()
                        }
                        resolver.update(
                            uri,
                            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                            null,
                            null
                        )
                        savedCount++
                    } catch (e: Exception) {
                        resolver.delete(uri, null, null)
                        throw e
                    }
                } else {
                    val dir = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
                        FOLDER_NAME
                    ).apply { mkdirs() }
                    val file = uniqueFile(dir, "${safeBase}_${item.id}_${index + 1}", format.extension)
                    FileOutputStream(file).use { out ->
                        item.bitmap.compress(compressFormat, quality, out)
                        out.flush()
                    }
                    savedCount++
                }
            }
            Result.success(savedCount)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveImagesAsZip(
        context: Context,
        images: List<ExtractedPdfImage>,
        baseName: String,
        format: ImageOutputFormat
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val compressFormat = if (format == ImageOutputFormat.PNG) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
        val quality = if (format == ImageOutputFormat.PNG) 100 else 95

        saveDocumentStream(
            context = context,
            baseName = "${sanitizeBaseName(baseName)}_images",
            extension = "zip",
            mimeType = "application/zip"
        ) { out ->
            ZipOutputStream(out).use { zip ->
                for ((idx, item) in images.withIndex()) {
                    val entryName = "${item.id}_${idx + 1}.${format.extension}"
                    zip.putNextEntry(ZipEntry(entryName))
                    item.bitmap.compress(compressFormat, quality, zip)
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun writeToUri(
        context: Context,
        targetUri: Uri,
        writer: (OutputStream) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                writer(out)
                out.flush()
            } ?: return@withContext Result.failure(IOException("Unable to open target output URI"))
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun prepareShareIntent(
        context: Context,
        fileNameWithExt: String,
        mimeType: String,
        chooserTitle: String,
        writer: (OutputStream) -> Unit
    ): Result<Intent> = withContext(Dispatchers.IO) {
        try {
            val shareDir = File(context.cacheDir, "convert_share").apply {
                mkdirs()
                listFiles()?.forEach { old -> old.delete() }
            }
            val safeFileName = fileNameWithExt.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = File(shareDir, safeFileName)
            FileOutputStream(file).use { out ->
                writer(out)
                out.flush()
            }
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Result.success(Intent.createChooser(sendIntent, chooserTitle))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sanitizeBaseName(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
        return if (cleaned.isNotEmpty()) cleaned else "Converted_${System.currentTimeMillis()}"
    }

    private fun uniqueFile(dir: File, baseName: String, ext: String): File {
        var candidate = File(dir, "$baseName.$ext")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${baseName}_$counter.$ext")
            counter++
        }
        return candidate
    }
}
