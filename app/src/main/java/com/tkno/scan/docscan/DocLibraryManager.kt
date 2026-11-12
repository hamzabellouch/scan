package com.tkno.scan.docscan

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class LibraryFileType {
    PDF,
    Image,
    Word,
    Txt
}

data class ScannedDocumentItem(
    val id: String,
    val name: String,
    val file: File?,
    val contentUri: Uri? = null,
    val fileType: LibraryFileType,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    var thumbnailFile: File? = null
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)

    val relativeTime: String
        get() = formatRelativeTime(lastModified)

    val dateGroupKey: String
        get() = formatDateGroup(lastModified)
}

enum class ViewMode {
    List,
    Grid
}

enum class SortOrder {
    NewestFirst,
    OldestFirst,
    LargestFirst,
    SmallestFirst,
    NameAsc,
    NameDesc
}

enum class DocTypeFilter {
    All,
    PDF,
    Images,
    Word,
    Txt
}

enum class OpenDocumentResult {
    OpenedExternally,
    NoTxtViewer,
    NoViewer,
    Failed
}

object DocLibraryManager {

    private const val SCANNED_FOLDER = "Scanned"

    private val IMAGE_MIME_TYPES = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "jfif" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "bmp" to "image/bmp",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "avif" to "image/avif",
        "tif" to "image/tiff",
        "tiff" to "image/tiff",
        "ico" to "image/x-icon",
        "svg" to "image/svg+xml",
        "dng" to "image/x-adobe-dng",
        "raw" to "image/x-dcraw",
        "cr2" to "image/x-canon-cr2",
        "nef" to "image/x-nikon-nef",
        "arw" to "image/x-sony-arw",
        "jxl" to "image/jxl"
    )

    private val WORD_MIME_TYPES = mapOf(
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "doc" to "application/msword",
        "docm" to "application/vnd.ms-word.document.macroEnabled.12",
        "dotx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
        "dot" to "application/msword"
    )

    private fun getThumbnailsDir(context: Context): File {
        return File(context.cacheDir, "library_thumbs").apply {
            if (!exists()) mkdirs()
        }
    }

    fun classifyFile(fileName: String, mimeTypeHint: String? = null): LibraryFileType? {
        if (fileName.isBlank() || fileName.startsWith(".")) return null
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)

        when {
            ext == "pdf" -> return LibraryFileType.PDF
            ext in IMAGE_MIME_TYPES -> return LibraryFileType.Image
            ext in WORD_MIME_TYPES -> return LibraryFileType.Word
            ext == "txt" -> return LibraryFileType.Txt
        }

        val normalizedHint = mimeTypeHint?.lowercase(Locale.US)
        val systemMime = lookupSystemMimeType(fileName, ext)?.lowercase(Locale.US)
        val candidateMime = normalizedHint ?: systemMime ?: return null

        return when {
            candidateMime == "application/pdf" -> LibraryFileType.PDF
            candidateMime.startsWith("image/") -> LibraryFileType.Image
            candidateMime == "application/msword" ||
                candidateMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                candidateMime.startsWith("application/vnd.ms-word.") ||
                candidateMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.template" ->
                LibraryFileType.Word
            candidateMime == "text/plain" -> LibraryFileType.Txt
            else -> null
        }
    }

    fun resolveMimeType(
        fileName: String,
        fileType: LibraryFileType,
        mimeTypeHint: String? = null
    ): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return when (fileType) {
            LibraryFileType.PDF -> "application/pdf"
            LibraryFileType.Image -> IMAGE_MIME_TYPES[ext]
                ?: mimeTypeHint?.takeIf { it.startsWith("image/", ignoreCase = true) }
                ?: lookupSystemMimeType(fileName, ext)?.takeIf { it.startsWith("image/", ignoreCase = true) }
                ?: "image/*"
            LibraryFileType.Word -> WORD_MIME_TYPES[ext]
                ?: mimeTypeHint?.takeIf { it.isNotBlank() }
                ?: "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            LibraryFileType.Txt -> "text/plain"
        }
    }

    fun matchesFilter(fileType: LibraryFileType, filter: DocTypeFilter): Boolean = when (filter) {
        DocTypeFilter.All -> true
        DocTypeFilter.PDF -> fileType == LibraryFileType.PDF
        DocTypeFilter.Images -> fileType == LibraryFileType.Image
        DocTypeFilter.Word -> fileType == LibraryFileType.Word
        DocTypeFilter.Txt -> fileType == LibraryFileType.Txt
    }

    fun buildRenamedFileName(originalName: String, newNameWithoutExt: String): String? {
        val sanitized = newNameWithoutExt
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
        if (sanitized.isBlank()) return null

        val dotIndex = originalName.lastIndexOf('.')
        val originalExt = if (dotIndex > 0 && dotIndex < originalName.length - 1) {
            originalName.substring(dotIndex)
        } else {
            ""
        }
        val stem = if (originalExt.isNotEmpty() && sanitized.endsWith(originalExt, ignoreCase = true)) {
            sanitized.dropLast(originalExt.length).trim()
        } else {
            sanitized
        }
        if (stem.isBlank()) return null
        return "$stem$originalExt"
    }

    private fun lookupSystemMimeType(fileName: String, ext: String): String? {
        val fromMap = if (ext.isNotEmpty()) {
            runCatching { MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(ext) }.getOrNull()
        } else {
            null
        }
        return fromMap ?: runCatching { URLConnection.guessContentTypeFromName(fileName) }.getOrNull()
    }

    fun loadScannedDocuments(context: Context): List<ScannedDocumentItem> {
        val result = mutableListOf<ScannedDocumentItem>()
        val seenPaths = mutableSetOf<String>()
        val seenMediaIds = mutableSetOf<Long>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanMediaStore(context, seenPaths, seenMediaIds, result)
        }

        val candidateDirs = listOfNotNull(
            File(context.getExternalFilesDir(null) ?: context.filesDir, SCANNED_FOLDER),
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { File(it, SCANNED_FOLDER) },
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, SCANNED_FOLDER) },
            File(context.filesDir, SCANNED_FOLDER),
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), SCANNED_FOLDER),
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SCANNED_FOLDER)
        )

        val scannedDirPaths = mutableSetOf<String>()
        for (dir in candidateDirs) {
            val dirKey = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (scannedDirPaths.add(dirKey)) {
                scanDirectory(dir, seenPaths, result)
            }
        }

        val thumbsDir = getThumbnailsDir(context)
        for (item in result) {
            if (item.fileType != LibraryFileType.PDF && item.fileType != LibraryFileType.Image) {
                continue
            }
            val thumbFile = File(thumbsDir, "${item.id.hashCode().toUInt()}_${item.lastModified}.jpg")
            if (thumbFile.exists()) {
                item.thumbnailFile = thumbFile
            } else {
                val thumb = generateThumbnail(context, item)
                if (thumb != null) {
                    try {
                        FileOutputStream(thumbFile).use { os ->
                            thumb.compress(Bitmap.CompressFormat.JPEG, 80, os)
                        }
                        item.thumbnailFile = thumbFile
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return result
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun scanMediaStore(
        context: Context,
        seenPaths: MutableSet<String>,
        seenMediaIds: MutableSet<Long>,
        output: MutableList<ScannedDocumentItem>
    ) {
        val collections = listOf(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        )
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA
        )
        val selection = "(" +
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" +
            ") AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        val selectionArgs = arrayOf(
            "${Environment.DIRECTORY_DOCUMENTS}/$SCANNED_FOLDER",
            "${Environment.DIRECTORY_PICTURES}/$SCANNED_FOLDER",
            "${Environment.DIRECTORY_DOCUMENTS}/$SCANNED_FOLDER/%",
            "${Environment.DIRECTORY_PICTURES}/$SCANNED_FOLDER/%"
        )

        val resolver = context.contentResolver
        for (collectionUri in collections) {
            runCatching {
                resolver.query(
                    collectionUri,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        if (!seenMediaIds.add(id)) continue

                        val name = cursor.getString(nameCol) ?: continue
                        val mimeHint = cursor.getString(mimeCol)
                        val fileType = classifyFile(name, mimeHint) ?: continue

                        val dataPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                        val file = dataPath
                            ?.takeIf { it.isNotBlank() }
                            ?.let { File(it) }
                            ?.takeIf { it.exists() && it.isFile }

                        val canonicalPath = file?.let {
                            runCatching { it.canonicalPath }.getOrDefault(it.absolutePath)
                        }
                        if (canonicalPath != null && !seenPaths.add(canonicalPath)) {
                            continue
                        }

                        val size = cursor.getLong(sizeCol).let { rowSize ->
                            if (rowSize > 0L) rowSize else file?.length() ?: 0L
                        }
                        if (size <= 0L) continue

                        val dateModifiedSec = cursor.getLong(dateCol)
                        val lastModified = when {
                            file != null && file.lastModified() > 0L -> file.lastModified()
                            dateModifiedSec > 0L -> dateModifiedSec * 1000L
                            else -> System.currentTimeMillis()
                        }

                        val contentUri = ContentUris.withAppendedId(collectionUri, id)
                        val resolvedMime = resolveMimeType(name, fileType, mimeHint)

                        output.add(
                            ScannedDocumentItem(
                                id = canonicalPath ?: contentUri.toString(),
                                name = name,
                                file = file,
                                contentUri = contentUri,
                                fileType = fileType,
                                mimeType = resolvedMime,
                                sizeBytes = size,
                                lastModified = lastModified
                            )
                        )
                    }
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    internal fun scanDirectory(
        dir: File,
        seenPaths: MutableSet<String>,
        output: MutableList<ScannedDocumentItem>
    ) {
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (!file.isFile || file.length() <= 0L) continue
            val canonicalPath = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            if (!seenPaths.add(canonicalPath)) continue

            val fileType = classifyFile(file.name) ?: continue
            val resolvedMime = resolveMimeType(file.name, fileType)

            output.add(
                ScannedDocumentItem(
                    id = canonicalPath,
                    name = file.name,
                    file = file,
                    contentUri = null,
                    fileType = fileType,
                    mimeType = resolvedMime,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            )
        }
    }

    private fun generateThumbnail(context: Context, item: ScannedDocumentItem): Bitmap? {
        return try {
            when (item.fileType) {
                LibraryFileType.PDF -> generatePdfThumbnail(context, item)
                LibraryFileType.Image -> generateImageThumbnail(context, item)
                LibraryFileType.Word, LibraryFileType.Txt -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generatePdfThumbnail(context: Context, item: ScannedDocumentItem): Bitmap? {
        val pfd = when {
            item.file != null && item.file.canRead() ->
                ParcelFileDescriptor.open(item.file, ParcelFileDescriptor.MODE_READ_ONLY)
            item.contentUri != null ->
                context.contentResolver.openFileDescriptor(item.contentUri, "r")
            else -> null
        } ?: return null

        return pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount > 0) {
                    renderer.openPage(0).use { page ->
                        val width = 240
                        val height = (240f * (page.height.toFloat() / page.width.toFloat())).toInt().coerceIn(100, 400)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                } else {
                    null
                }
            }
        }
    }

    private fun generateImageThumbnail(context: Context, item: ScannedDocumentItem): Bitmap? {
        val fromFactory = decodeBitmapFactoryThumbnail(context, item)
        if (fromFactory != null) return fromFactory

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return decodeImageDecoderThumbnail(context, item)
        }
        return null
    }

    private fun decodeBitmapFactoryThumbnail(context: Context, item: ScannedDocumentItem): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val readableFile = item.file?.takeIf { it.canRead() }

        if (readableFile != null) {
            BitmapFactory.decodeFile(readableFile.absolutePath, boundsOptions)
        } else if (item.contentUri != null) {
            context.contentResolver.openInputStream(item.contentUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
        } else {
            return null
        }

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val sampleSize = (boundsOptions.outWidth / 240).coerceAtLeast(1)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }

        return if (readableFile != null) {
            BitmapFactory.decodeFile(readableFile.absolutePath, decodeOptions)
        } else if (item.contentUri != null) {
            context.contentResolver.openInputStream(item.contentUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } else {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeImageDecoderThumbnail(context: Context, item: ScannedDocumentItem): Bitmap? {
        val source = when {
            item.file != null && item.file.canRead() -> ImageDecoder.createSource(item.file)
            item.contentUri != null -> ImageDecoder.createSource(context.contentResolver, item.contentUri)
            else -> return null
        }
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val srcWidth = info.size.width.coerceAtLeast(1)
            val srcHeight = info.size.height.coerceAtLeast(1)
            val targetWidth = 240
            val targetHeight = (targetWidth * (srcHeight.toFloat() / srcWidth.toFloat())).toInt().coerceIn(100, 400)
            decoder.setTargetSize(targetWidth, targetHeight)
        }
    }

    fun readTextContent(
        context: Context,
        item: ScannedDocumentItem,
        maxBytes: Int = 512_000
    ): Result<String> = runCatching {
        val stream = when {
            item.file != null && item.file.canRead() -> FileInputStream(item.file)
            item.contentUri != null -> context.contentResolver.openInputStream(item.contentUri)
            else -> null
        } ?: throw IOException("Unable to open text file")

        val raw = stream.use { input ->
            val buffer = ByteArray(8192)
            val out = ByteArrayOutputStream()
            while (out.size() < maxBytes) {
                val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - out.size()))
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }

        val decoded = when {
            raw.size >= 2 && raw[0] == 0xFE.toByte() && raw[1] == 0xFF.toByte() ->
                String(raw, 2, raw.size - 2, StandardCharsets.UTF_16BE)
            raw.size >= 2 && raw[0] == 0xFF.toByte() && raw[1] == 0xFE.toByte() ->
                String(raw, 2, raw.size - 2, StandardCharsets.UTF_16LE)
            raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte() ->
                String(raw, 3, raw.size - 3, StandardCharsets.UTF_8)
            else -> String(raw, StandardCharsets.UTF_8)
        }
        decoded.replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun resolveShareableUri(context: Context, item: ScannedDocumentItem): Uri? {
        val readableFile = item.file?.takeIf { it.exists() && it.canRead() }
        if (readableFile != null) {
            val fileProviderUri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", readableFile)
            }.getOrNull()
            if (fileProviderUri != null) return fileProviderUri
        }
        return item.contentUri
    }

    fun openDocument(context: Context, item: ScannedDocumentItem): OpenDocumentResult {
        return try {
            val uri = resolveShareableUri(context, item) ?: return OpenDocumentResult.Failed
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) == null &&
                item.fileType == LibraryFileType.Image &&
                item.mimeType != "image/*"
            ) {
                intent.setDataAndType(uri, "image/*")
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(Intent.createChooser(intent, item.name))
                OpenDocumentResult.OpenedExternally
            } else if (item.fileType == LibraryFileType.Txt) {
                OpenDocumentResult.NoTxtViewer
            } else {
                OpenDocumentResult.NoViewer
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (item.fileType == LibraryFileType.Txt) {
                OpenDocumentResult.NoTxtViewer
            } else {
                OpenDocumentResult.Failed
            }
        }
    }

    fun shareDocuments(context: Context, items: List<ScannedDocumentItem>) {
        if (items.isEmpty()) return
        try {
            if (items.size == 1) {
                val item = items.first()
                val uri = resolveShareableUri(context, item) ?: return
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = item.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${item.name}"))
            } else {
                val uris = items.mapNotNull { resolveShareableUri(context, it) }
                if (uris.isEmpty()) return

                val shareMime = when {
                    items.all { it.fileType == LibraryFileType.Image } -> "image/*"
                    items.map { it.mimeType }.distinct().size == 1 -> items.first().mimeType
                    else -> "*/*"
                }
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = shareMime
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share documents"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun renameDocument(context: Context, item: ScannedDocumentItem, newNameWithoutExt: String): Boolean {
        val cleanName = buildRenamedFileName(item.name, newNameWithoutExt) ?: return false
        if (cleanName == item.name) return true

        var renamed = false

        if (item.contentUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
            }
            val updatedRows = runCatching {
                context.contentResolver.update(item.contentUri, values, null, null)
            }.getOrDefault(0)
            if (updatedRows > 0) {
                renamed = true
            }
        }

        if (!renamed && item.file != null) {
            val parent = item.file.parentFile
            if (parent != null) {
                val newFile = File(parent, cleanName)
                if (!newFile.exists()) {
                    renamed = runCatching { item.file.renameTo(newFile) }.getOrDefault(false)
                }
            }
        }

        if (renamed) {
            item.thumbnailFile?.let { thumb ->
                if (thumb.exists()) thumb.delete()
            }
        }
        return renamed
    }

    fun deleteDocuments(context: Context, items: List<ScannedDocumentItem>): Boolean {
        var allDeleted = true
        for (item in items) {
            var deletedThis = false
            try {
                if (item.contentUri != null) {
                    val rows = runCatching {
                        context.contentResolver.delete(item.contentUri, null, null)
                    }.getOrDefault(0)
                    if (rows > 0) deletedThis = true
                }
                if (!deletedThis && item.file != null) {
                    if (!item.file.exists() || item.file.delete()) {
                        deletedThis = true
                    }
                } else if (item.file != null && item.file.exists()) {
                    item.file.delete()
                }
                item.thumbnailFile?.let { thumb ->
                    if (thumb.exists()) thumb.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (!deletedThis) allDeleted = false
        }
        return allDeleted
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.2f kB", kb)
        else -> "$bytes B"
    }
}

fun formatDateGroup(timestamp: Long): String {
    val calItem = Calendar.getInstance().apply { timeInMillis = timestamp }
    val calNow = Calendar.getInstance()

    val isSameDay = calItem.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
            calItem.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    if (isSameDay) return "Today"

    val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = calItem.get(Calendar.YEAR) == calYesterday.get(Calendar.YEAR) &&
            calItem.get(Calendar.DAY_OF_YEAR) == calYesterday.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "Yesterday"

    val sdf = SimpleDateFormat("EEE, MMM d", Locale.US)
    return sdf.format(Date(timestamp))
}

fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0)
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes minutes ago"
        hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.US)
            sdf.format(Date(timestamp))
        }
    }
}
