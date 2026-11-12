package com.tkno.scan.convert

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkno.scan.R
import com.tkno.scan.ui.component.ConfirmButton
import com.tkno.scan.ui.component.DismissButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

private val CardShape = RoundedCornerShape(20.dp)
private val InnerCardShape = RoundedCornerShape(16.dp)

@Composable
fun ConvertAdvanceScreen() {
    var activeTool by remember { mutableStateOf<ConvertTool?>(null) }
    var prefillImagePdfUri by remember { mutableStateOf<Uri?>(null) }

    BackHandler(enabled = activeTool != null) {
        activeTool = null
        prefillImagePdfUri = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 96.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 12.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeTool != null) {
                IconButton(
                    onClick = {
                        activeTool = null
                        prefillImagePdfUri = null
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = toolTitle(activeTool!!),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = stringResource(R.string.advance),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedContent(
            targetState = activeTool,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ConvertToolTransition"
        ) { tool ->
            when (tool) {
                null -> ConvertHubView(
                    onSelectTool = { selected ->
                        prefillImagePdfUri = null
                        activeTool = selected
                    }
                )
                ConvertTool.ExtractText -> ExtractTextToolView(
                    onOpenAsPageImages = { pdfUri ->
                        prefillImagePdfUri = pdfUri
                        activeTool = ConvertTool.ExtractImages
                    }
                )
                ConvertTool.PdfToWord -> PdfToWordToolView()
                ConvertTool.TxtToDoc -> TxtToDocToolView()
                ConvertTool.ExtractImages -> ExtractImagesToolView(
                    initialPdfUri = prefillImagePdfUri
                )
            }
        }
    }
}

@Composable
private fun toolTitle(tool: ConvertTool): String = when (tool) {
    ConvertTool.ExtractText -> stringResource(R.string.tool_extract_text_title)
    ConvertTool.PdfToWord -> stringResource(R.string.tool_pdf_to_word_title)
    ConvertTool.TxtToDoc -> stringResource(R.string.tool_txt_to_doc_title)
    ConvertTool.ExtractImages -> stringResource(R.string.tool_extract_images_title)
}

@Composable
private fun ConvertHubView(
    onSelectTool: (ConvertTool) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.convert_hub_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.convert_hub_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))

        ConvertToolCard(
            title = stringResource(R.string.tool_extract_text_title),
            description = stringResource(R.string.tool_extract_text_desc),
            icon = Icons.Outlined.Description,
            onClick = { onSelectTool(ConvertTool.ExtractText) }
        )
        ConvertToolCard(
            title = stringResource(R.string.tool_pdf_to_word_title),
            description = stringResource(R.string.tool_pdf_to_word_desc),
            icon = Icons.Outlined.DriveFileRenameOutline,
            onClick = { onSelectTool(ConvertTool.PdfToWord) }
        )
        ConvertToolCard(
            title = stringResource(R.string.tool_txt_to_doc_title),
            description = stringResource(R.string.tool_txt_to_doc_desc),
            icon = Icons.Outlined.PictureAsPdf,
            onClick = { onSelectTool(ConvertTool.TxtToDoc) }
        )
        ConvertToolCard(
            title = stringResource(R.string.tool_extract_images_title),
            description = stringResource(R.string.tool_extract_images_desc),
            icon = Icons.Outlined.Image,
            onClick = { onSelectTool(ConvertTool.ExtractImages) }
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ConvertToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProcessingBar(visible: Boolean) {
    if (!visible) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Text(
            text = stringResource(R.string.convert_processing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ErrorBannerCard(
    message: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = InnerCardShape,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (actionLabel != null && onActionClick != null) {
                OutlinedButton(
                    onClick = onActionClick,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = actionLabel,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PickPdfHeaderCard(
    selectedFileName: String?,
    subtitle: String?,
    isBusy: Boolean,
    onPickClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedFileName ?: stringResource(R.string.convert_pick_pdf),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            ConfirmButton(
                modifier = Modifier.fillMaxWidth(),
                text = if (selectedFileName == null) {
                    stringResource(R.string.convert_pick_pdf)
                } else {
                    stringResource(R.string.convert_change_pdf)
                },
                icon = Icons.Outlined.UploadFile,
                enabled = !isBusy,
                onClick = onPickClick
            )
        }
    }
}

@Composable
private fun ExtractTextToolView(
    onOpenAsPageImages: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf<String?>(null) }
    var outputBaseName by remember { mutableStateOf("") }
    var extractedText by remember { mutableStateOf("") }
    var pageSummary by remember { mutableStateOf<String?>(null) }
    var noTextFound by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var outputFormat by remember { mutableStateOf(TextOutputFormat.TXT) }
    var isBusy by remember { mutableStateOf(false) }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { targetUri ->
        if (targetUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            val result = writeTextFormattedToUri(
                context = context,
                targetUri = targetUri,
                text = extractedText,
                format = outputFormat
            )
            isBusy = false
            result.onSuccess {
                Toast.makeText(context, R.string.convert_saved_success, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                errorMessage = context.getString(R.string.convert_failed, err.message ?: "Error")
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedUri = uri
        val name = ConvertStorageHelper.getDisplayNameWithoutExt(context, uri, "Document")
        sourceName = "$name.pdf"
        outputBaseName = "${name}_text"
        noTextFound = false
        errorMessage = null
        extractedText = ""
        pageSummary = null

        scope.launch {
            isBusy = true
            when (val res = PdfTextEngine.extractTextFromPdf(context, uri)) {
                is PdfTextExtractResult.Success -> {
                    extractedText = res.fullText
                    pageSummary = context.getString(
                        R.string.convert_pages_info,
                        res.nonEmptyPages,
                        res.pageCount
                    )
                }
                PdfTextExtractResult.NoSelectableText -> {
                    noTextFound = true
                }
                is PdfTextExtractResult.Error -> {
                    errorMessage = res.message
                }
            }
            isBusy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PickPdfHeaderCard(
            selectedFileName = sourceName,
            subtitle = pageSummary,
            isBusy = isBusy,
            onPickClick = { pdfPicker.launch(arrayOf("application/pdf")) }
        )

        ProcessingBar(isBusy)

        if (noTextFound) {
            ErrorBannerCard(
                message = stringResource(R.string.convert_no_selectable_text),
                actionLabel = stringResource(R.string.convert_try_pages_to_images),
                onActionClick = selectedUri?.let { uri -> { onOpenAsPageImages(uri) } }
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            ErrorBannerCard(message = errorMessage!!)
        }

        if (extractedText.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.convert_extracted_text_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = { copyTextToClipboard(context, extractedText) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.convert_copy_text))
                }
            }

            OutlinedTextField(
                value = extractedText,
                onValueChange = { extractedText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 300.dp),
                shape = InnerCardShape
            )

            Text(
                text = stringResource(R.string.convert_output_format),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextOutputFormatChip(
                    selected = outputFormat == TextOutputFormat.TXT,
                    label = "TXT",
                    onClick = { outputFormat = TextOutputFormat.TXT }
                )
                TextOutputFormatChip(
                    selected = outputFormat == TextOutputFormat.DOCX,
                    label = stringResource(R.string.convert_format_word),
                    onClick = { outputFormat = TextOutputFormat.DOCX }
                )
                TextOutputFormatChip(
                    selected = outputFormat == TextOutputFormat.PDF,
                    label = stringResource(R.string.convert_format_text_pdf),
                    onClick = { outputFormat = TextOutputFormat.PDF }
                )
            }

            OutlinedTextField(
                value = outputBaseName,
                onValueChange = { outputBaseName = it },
                label = { Text(stringResource(R.string.convert_output_filename)) },
                suffix = { Text(".${outputFormat.extension}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = InnerCardShape
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DismissButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.share),
                    icon = Icons.Outlined.Share,
                    onClick = {
                        if (isBusy) return@DismissButton
                        scope.launch {
                            isBusy = true
                            val result = shareTextFormattedDocument(
                                context = context,
                                baseName = outputBaseName.ifBlank { "Extracted" },
                                text = extractedText,
                                format = outputFormat
                            )
                            isBusy = false
                            result.onSuccess { intent ->
                                context.startActivity(intent)
                            }.onFailure { err ->
                                errorMessage = context.getString(
                                    R.string.convert_failed,
                                    err.message ?: "Error"
                                )
                            }
                        }
                    }
                )
                ConfirmButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.convert_save_button),
                    icon = Icons.Outlined.Check,
                    enabled = !isBusy && extractedText.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isBusy = true
                            val result = saveTextFormattedDocument(
                                context = context,
                                baseName = outputBaseName.ifBlank { "Extracted" },
                                text = extractedText,
                                format = outputFormat
                            )
                            isBusy = false
                            result.onSuccess {
                                Toast.makeText(
                                    context,
                                    R.string.convert_saved_success,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure { err ->
                                errorMessage = context.getString(
                                    R.string.convert_failed,
                                    err.message ?: "Error"
                                )
                            }
                        }
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    val safe = ConvertStorageHelper.sanitizeBaseName(
                        outputBaseName.ifBlank { "Extracted" }
                    )
                    saveAsLauncher.launch("$safe.${outputFormat.extension}")
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.convert_save_as_button))
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PdfToWordToolView() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf<String?>(null) }
    var outputBaseName by remember { mutableStateOf("") }
    var conversionStats by remember { mutableStateOf<PdfConversionStats?>(null) }
    var cachedDocxFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cachedDocxFile?.delete()
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    ) { targetUri ->
        val file = cachedDocxFile ?: return@rememberLauncherForActivityResult
        if (targetUri == null || !file.exists()) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            val result = ConvertStorageHelper.writeToUri(context, targetUri) { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            isBusy = false
            result.onSuccess {
                Toast.makeText(context, R.string.convert_saved_success, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                errorMessage = context.getString(R.string.convert_failed, err.message ?: "Error")
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedUri = uri
        val base = ConvertStorageHelper.getDisplayNameWithoutExt(context, uri, "Converted")
        sourceName = "$base.pdf"
        outputBaseName = base
        conversionStats = null
        errorMessage = null
        cachedDocxFile?.delete()
        cachedDocxFile = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.convert_pdf_to_word_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PickPdfHeaderCard(
            selectedFileName = sourceName,
            subtitle = null,
            isBusy = isBusy,
            onPickClick = { pdfPicker.launch(arrayOf("application/pdf")) }
        )

        if (selectedUri != null) {
            OutlinedTextField(
                value = outputBaseName,
                onValueChange = { outputBaseName = it },
                label = { Text(stringResource(R.string.convert_output_filename)) },
                suffix = { Text(".docx") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = InnerCardShape
            )

            ConfirmButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.convert_convert_word_button),
                icon = Icons.Outlined.DriveFileRenameOutline,
                enabled = !isBusy,
                onClick = {
                    val uri = selectedUri ?: return@ConfirmButton
                    scope.launch {
                        isBusy = true
                        errorMessage = null
                        cachedDocxFile?.delete()
                        cachedDocxFile = null

                        val tempFile = withContext(Dispatchers.IO) {
                            File(context.cacheDir, "pdf2word_${System.nanoTime()}.docx")
                        }
                        val convertResult = withContext(Dispatchers.IO) {
                            FileOutputStream(tempFile).use { fos ->
                                PdfToWordEngine.convertPdfToWord(context, uri, fos)
                            }
                        }

                        convertResult.onSuccess { stats ->
                            val saveResult = ConvertStorageHelper.saveDocumentStream(
                                context = context,
                                baseName = outputBaseName.ifBlank { "Converted" },
                                extension = "docx",
                                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            ) { out ->
                                tempFile.inputStream().use { it.copyTo(out) }
                            }
                            saveResult.onSuccess {
                                cachedDocxFile = tempFile
                                conversionStats = stats
                                Toast.makeText(
                                    context,
                                    R.string.convert_saved_success,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure { err ->
                                tempFile.delete()
                                errorMessage = context.getString(
                                    R.string.convert_failed,
                                    err.message ?: "Error"
                                )
                            }
                        }.onFailure { err ->
                            tempFile.delete()
                            errorMessage = context.getString(
                                R.string.convert_failed,
                                err.message ?: "Error"
                            )
                        }
                        isBusy = false
                    }
                }
            )
        }

        ProcessingBar(isBusy)

        if (!errorMessage.isNullOrBlank()) {
            ErrorBannerCard(message = errorMessage!!)
        }

        val stats = conversionStats
        val readyFile = cachedDocxFile
        if (stats != null && readyFile != null && readyFile.exists()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.convert_saved_success),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(
                            R.string.convert_pdf_to_word_stats,
                            stats.pageCount,
                            stats.paragraphCount,
                            stats.imageCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DismissButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.share),
                            icon = Icons.Outlined.Share,
                            onClick = {
                                scope.launch {
                                    val safe = ConvertStorageHelper.sanitizeBaseName(
                                        outputBaseName.ifBlank { "Converted" }
                                    )
                                    val shareRes = ConvertStorageHelper.prepareShareIntent(
                                        context = context,
                                        fileNameWithExt = "$safe.docx",
                                        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        chooserTitle = context.getString(R.string.share)
                                    ) { out ->
                                        readyFile.inputStream().use { it.copyTo(out) }
                                    }
                                    shareRes.onSuccess { context.startActivity(it) }
                                        .onFailure { err ->
                                            errorMessage = context.getString(
                                                R.string.convert_failed,
                                                err.message ?: "Error"
                                            )
                                        }
                                }
                            }
                        )
                        ConfirmButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.convert_save_as_button),
                            icon = Icons.Outlined.Folder,
                            onClick = {
                                val safe = ConvertStorageHelper.sanitizeBaseName(
                                    outputBaseName.ifBlank { "Converted" }
                                )
                                saveAsLauncher.launch("$safe.docx")
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TxtToDocToolView() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var outputBaseName by remember { mutableStateOf("TextDocument") }
    var targetFormat by remember { mutableStateOf(TxtTargetFormat.PDF) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val txtPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        errorMessage = null
        outputBaseName = ConvertStorageHelper.getDisplayNameWithoutExt(context, uri, "TextDocument")
        scope.launch {
            isBusy = true
            ConvertStorageHelper.readTextFromUri(context, uri)
                .onSuccess { text -> inputText = text }
                .onFailure { err ->
                    errorMessage = context.getString(
                        R.string.convert_failed,
                        err.message ?: "Error"
                    )
                }
            isBusy = false
        }
    }

    val mappedFormat = if (targetFormat == TxtTargetFormat.PDF) {
        TextOutputFormat.PDF
    } else {
        TextOutputFormat.DOCX
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mappedFormat.mimeType)
    ) { targetUri ->
        if (targetUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            val result = writeTextFormattedToUri(
                context = context,
                targetUri = targetUri,
                text = inputText,
                format = mappedFormat
            )
            isBusy = false
            result.onSuccess {
                Toast.makeText(context, R.string.convert_saved_success, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                errorMessage = context.getString(R.string.convert_failed, err.message ?: "Error")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(
            onClick = { txtPicker.launch(arrayOf("text/plain", "text/*")) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.convert_pick_txt))
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = {
                inputText = it
                errorMessage = null
            },
            label = { Text(stringResource(R.string.convert_txt_input_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 320.dp),
            shape = InnerCardShape
        )

        Text(
            text = stringResource(R.string.convert_output_format),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextOutputFormatChip(
                selected = targetFormat == TxtTargetFormat.PDF,
                label = "PDF (.pdf)",
                onClick = { targetFormat = TxtTargetFormat.PDF }
            )
            TextOutputFormatChip(
                selected = targetFormat == TxtTargetFormat.DOCX,
                label = stringResource(R.string.convert_format_word),
                onClick = { targetFormat = TxtTargetFormat.DOCX }
            )
        }

        OutlinedTextField(
            value = outputBaseName,
            onValueChange = { outputBaseName = it },
            label = { Text(stringResource(R.string.convert_output_filename)) },
            suffix = { Text(".${targetFormat.extension}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = InnerCardShape
        )

        ProcessingBar(isBusy)

        if (!errorMessage.isNullOrBlank()) {
            ErrorBannerCard(message = errorMessage!!)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DismissButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.share),
                icon = Icons.Outlined.Share,
                onClick = {
                    if (isBusy) return@DismissButton
                    if (inputText.isBlank()) {
                        errorMessage = context.getString(R.string.convert_txt_empty_error)
                        return@DismissButton
                    }
                    scope.launch {
                        isBusy = true
                        val result = shareTextFormattedDocument(
                            context = context,
                            baseName = outputBaseName.ifBlank { "TextDocument" },
                            text = inputText,
                            format = mappedFormat
                        )
                        isBusy = false
                        result.onSuccess { context.startActivity(it) }
                            .onFailure { err ->
                                errorMessage = context.getString(
                                    R.string.convert_failed,
                                    err.message ?: "Error"
                                )
                            }
                    }
                }
            )
            ConfirmButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.convert_save_button),
                icon = Icons.Outlined.Check,
                enabled = !isBusy,
                onClick = {
                    if (inputText.isBlank()) {
                        errorMessage = context.getString(R.string.convert_txt_empty_error)
                        return@ConfirmButton
                    }
                    scope.launch {
                        isBusy = true
                        val result = saveTextFormattedDocument(
                            context = context,
                            baseName = outputBaseName.ifBlank { "TextDocument" },
                            text = inputText,
                            format = mappedFormat
                        )
                        isBusy = false
                        result.onSuccess {
                            Toast.makeText(
                                context,
                                R.string.convert_saved_success,
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure { err ->
                            errorMessage = context.getString(
                                R.string.convert_failed,
                                err.message ?: "Error"
                            )
                        }
                    }
                }
            )
        }

        OutlinedButton(
            onClick = {
                if (inputText.isBlank()) {
                    errorMessage = context.getString(R.string.convert_txt_empty_error)
                    return@OutlinedButton
                }
                val safe = ConvertStorageHelper.sanitizeBaseName(
                    outputBaseName.ifBlank { "TextDocument" }
                )
                saveAsLauncher.launch("$safe.${targetFormat.extension}")
            },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.convert_save_as_button))
        }

        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtractImagesToolView(
    initialPdfUri: Uri?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf(initialPdfUri) }
    var sourceBaseName by remember {
        mutableStateOf(
            initialPdfUri?.let { ConvertStorageHelper.getDisplayNameWithoutExt(context, it, "PDF") }
                ?: "PDF"
        )
    }
    var mode by remember {
        mutableStateOf(
            if (initialPdfUri != null) ImageExtractMode.PagesAsImages
            else ImageExtractMode.EmbeddedImages
        )
    }
    var format by remember { mutableStateOf(ImageOutputFormat.JPEG) }
    var images by remember { mutableStateOf<List<ExtractedPdfImage>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var noImagesFound by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    fun replaceImages(newList: List<ExtractedPdfImage>) {
        val old = images
        images = newList
        selectedIds = newList.mapTo(LinkedHashSet()) { it.id }
        old.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
    }

    DisposableEffect(Unit) {
        onDispose {
            images.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        }
    }

    fun runExtraction(uri: Uri, extractMode: ImageExtractMode) {
        scope.launch {
            isBusy = true
            noImagesFound = false
            errorMessage = null
            replaceImages(emptyList())

            PdfImageExtractEngine.extractImages(context, uri, extractMode)
                .onSuccess { list ->
                    if (list.isEmpty()) {
                        noImagesFound = true
                    } else {
                        replaceImages(list)
                    }
                }
                .onFailure { err ->
                    errorMessage = context.getString(
                        R.string.convert_failed,
                        err.message ?: "Error"
                    )
                }
            isBusy = false
        }
    }

    LaunchedEffect(initialPdfUri) {
        if (initialPdfUri != null) {
            runExtraction(initialPdfUri, mode)
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedUri = uri
        sourceBaseName = ConvertStorageHelper.getDisplayNameWithoutExt(context, uri, "PDF")
        runExtraction(uri, mode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.convert_image_mode_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextOutputFormatChip(
                selected = mode == ImageExtractMode.EmbeddedImages,
                label = stringResource(R.string.convert_mode_embedded),
                onClick = {
                    if (mode != ImageExtractMode.EmbeddedImages && !isBusy) {
                        mode = ImageExtractMode.EmbeddedImages
                        selectedUri?.let { runExtraction(it, mode) }
                    }
                }
            )
            TextOutputFormatChip(
                selected = mode == ImageExtractMode.PagesAsImages,
                label = stringResource(R.string.convert_mode_pages),
                onClick = {
                    if (mode != ImageExtractMode.PagesAsImages && !isBusy) {
                        mode = ImageExtractMode.PagesAsImages
                        selectedUri?.let { runExtraction(it, mode) }
                    }
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.convert_output_format),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            TextOutputFormatChip(
                selected = format == ImageOutputFormat.JPEG,
                label = "JPG",
                onClick = { format = ImageOutputFormat.JPEG }
            )
            TextOutputFormatChip(
                selected = format == ImageOutputFormat.PNG,
                label = "PNG",
                onClick = { format = ImageOutputFormat.PNG }
            )
        }

        PickPdfHeaderCard(
            selectedFileName = selectedUri?.let { "$sourceBaseName.pdf" },
            subtitle = null,
            isBusy = isBusy,
            onPickClick = { pdfPicker.launch(arrayOf("application/pdf")) }
        )

        ProcessingBar(isBusy)

        if (noImagesFound) {
            ErrorBannerCard(
                message = stringResource(R.string.convert_no_images_found),
                actionLabel = if (mode == ImageExtractMode.EmbeddedImages && selectedUri != null) {
                    stringResource(R.string.convert_try_pages_to_images)
                } else null,
                onActionClick = if (mode == ImageExtractMode.EmbeddedImages && selectedUri != null) {
                    {
                        mode = ImageExtractMode.PagesAsImages
                        selectedUri?.let { runExtraction(it, mode) }
                    }
                } else null
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            ErrorBannerCard(message = errorMessage!!)
        }

        if (images.isNotEmpty()) {
            val selectedImages = images.filter { it.id in selectedIds }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        R.string.convert_images_selected,
                        selectedImages.size,
                        images.size
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = {
                        selectedIds = if (selectedIds.size == images.size) {
                            emptySet()
                        } else {
                            images.mapTo(LinkedHashSet()) { it.id }
                        }
                    }
                ) {
                    Text(
                        text = if (selectedIds.size == images.size) {
                            stringResource(R.string.convert_deselect_all)
                        } else {
                            stringResource(R.string.convert_select_all)
                        }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                images.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (item in rowItems) {
                            val isSelected = item.id in selectedIds
                            ExtractedImageGridItem(
                                modifier = Modifier.weight(1f),
                                item = item,
                                isSelected = isSelected,
                                onToggle = {
                                    selectedIds = if (isSelected) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                }
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DismissButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.convert_export_zip),
                    icon = Icons.Outlined.Archive,
                    onClick = {
                        if (isBusy || selectedImages.isEmpty()) return@DismissButton
                        scope.launch {
                            isBusy = true
                            ConvertStorageHelper.saveImagesAsZip(
                                context = context,
                                images = selectedImages,
                                baseName = sourceBaseName,
                                format = format
                            ).onSuccess {
                                Toast.makeText(
                                    context,
                                    R.string.convert_zip_saved_success,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure { err ->
                                errorMessage = context.getString(
                                    R.string.convert_failed,
                                    err.message ?: "Error"
                                )
                            }
                            isBusy = false
                        }
                    }
                )
                ConfirmButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.convert_save_images),
                    icon = Icons.Outlined.Check,
                    enabled = !isBusy && selectedImages.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            isBusy = true
                            ConvertStorageHelper.saveImagesToGallery(
                                context = context,
                                images = selectedImages,
                                baseName = sourceBaseName,
                                format = format
                            ).onSuccess { count ->
                                val msg = context.getString(
                                    R.string.convert_saved_gallery_success,
                                    count
                                )
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                errorMessage = context.getString(
                                    R.string.convert_failed,
                                    err.message ?: "Error"
                                )
                            }
                            isBusy = false
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ExtractedImageGridItem(
    modifier: Modifier = Modifier,
    item: ExtractedPdfImage,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(InnerCardShape)
            .clickable(onClick = onToggle),
        shape = InnerCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isSelected) {
            BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            null
        }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (!item.bitmap.isRecycled) {
                    Image(
                        bitmap = item.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ) {
                    Text(
                        text = stringResource(R.string.convert_page_badge, item.pageNumber),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = if (isSelected) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Outlined.CheckCircleOutline
                        },
                        contentDescription = null,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = "${item.width}×${item.height}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun TextOutputFormatChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null
    )
}

private fun copyTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Extracted Text", text))
    Toast.makeText(context, R.string.convert_copied_toast, Toast.LENGTH_SHORT).show()
}

private suspend fun generateFormattedFile(
    context: Context,
    text: String,
    format: TextOutputFormat
): Result<File> = withContext(Dispatchers.IO) {
    val tempFile = File(context.cacheDir, "convert_tmp_${System.nanoTime()}.${format.extension}")
    try {
        when (format) {
            TextOutputFormat.TXT -> {
                FileOutputStream(tempFile).use { fos ->
                    fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    fos.write(text.toByteArray(StandardCharsets.UTF_8))
                }
            }
            TextOutputFormat.DOCX -> {
                FileOutputStream(tempFile).use { fos ->
                    DocxWriter.writePlainText(text, fos)
                }
            }
            TextOutputFormat.PDF -> {
                val res = FileOutputStream(tempFile).use { fos ->
                    PdfTextEngine.generateTextPdf(context, text, fos)
                }
                res.getOrThrow()
            }
        }
        Result.success(tempFile)
    } catch (e: Exception) {
        tempFile.delete()
        Result.failure(e)
    }
}

private suspend fun saveTextFormattedDocument(
    context: Context,
    baseName: String,
    text: String,
    format: TextOutputFormat
): Result<Uri> {
    val generated = generateFormattedFile(context, text, format)
    val tempFile = generated.getOrElse { return Result.failure(it) }
    return try {
        ConvertStorageHelper.saveDocumentStream(
            context = context,
            baseName = baseName,
            extension = format.extension,
            mimeType = format.mimeType
        ) { out: OutputStream ->
            tempFile.inputStream().use { it.copyTo(out) }
        }
    } finally {
        tempFile.delete()
    }
}

private suspend fun writeTextFormattedToUri(
    context: Context,
    targetUri: Uri,
    text: String,
    format: TextOutputFormat
): Result<Unit> {
    val generated = generateFormattedFile(context, text, format)
    val tempFile = generated.getOrElse { return Result.failure(it) }
    return try {
        ConvertStorageHelper.writeToUri(context, targetUri) { out ->
            tempFile.inputStream().use { it.copyTo(out) }
        }
    } finally {
        tempFile.delete()
    }
}

private suspend fun shareTextFormattedDocument(
    context: Context,
    baseName: String,
    text: String,
    format: TextOutputFormat
): Result<android.content.Intent> {
    val generated = generateFormattedFile(context, text, format)
    val tempFile = generated.getOrElse { return Result.failure(it) }
    return try {
        val safeName = ConvertStorageHelper.sanitizeBaseName(baseName)
        ConvertStorageHelper.prepareShareIntent(
            context = context,
            fileNameWithExt = "$safeName.${format.extension}",
            mimeType = format.mimeType,
            chooserTitle = context.getString(R.string.share)
        ) { out ->
            tempFile.inputStream().use { it.copyTo(out) }
        }
    } finally {
        tempFile.delete()
    }
}
