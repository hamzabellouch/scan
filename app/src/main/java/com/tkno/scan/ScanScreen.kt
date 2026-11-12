package com.tkno.scan

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.tkno.scan.docscan.*
import com.tkno.scan.ui.icon.CenterFocusWeak
import com.tkno.scan.ui.icon.PictureAsPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    onScannerActiveChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var flowStep by remember { mutableStateOf(ScanFlowStep.Idle) }
    val pages = remember { mutableStateListOf<ScannedPage>() }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var editingPageIndex by remember { mutableIntStateOf(0) }
    var cameFromReview by remember { mutableStateOf(false) }

    var pendingCameraLaunch by remember { mutableStateOf(false) }
    var isImportingFiles by remember { mutableStateOf(false) }
    var appendOnImport by remember { mutableStateOf(false) }

    val isScannerActive = flowStep != ScanFlowStep.Idle
    LaunchedEffect(isScannerActive) {
        onScannerActiveChange(isScannerActive)
    }
    DisposableEffect(Unit) {
        onDispose {
            onScannerActiveChange(false)
        }
    }

    LaunchedEffect(cameraPermissionState.status.isGranted, pendingCameraLaunch) {
        if (pendingCameraLaunch && cameraPermissionState.status.isGranted) {
            pendingCameraLaunch = false
            pages.clear()
            flowStep = ScanFlowStep.Camera
        }
    }

    val imageImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isImportingFiles = true
            try {
                val importedPages = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        DocImportHelper.decodeBitmapFromUri(context, uri)?.let { bmp ->
                            val detectedCorners = DocEdgeDetector.detectDocumentCorners(bmp, highAccuracy = true)
                            val autoCropped = DocImageProcessor.cropPerspective(bmp, detectedCorners)
                            ScannedPage(
                                originalBitmap = bmp,
                                rotatedBitmap = bmp,
                                corners = detectedCorners,
                                autoCorners = detectedCorners,
                                isAutoCropSelected = true,
                                croppedBitmap = autoCropped,
                                processedBitmap = autoCropped
                            )
                        }
                    }
                }
                if (importedPages.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.image_import_error),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    if (!appendOnImport) {
                        pages.clear()
                    }
                    val firstNewIndex = pages.size
                    pages.addAll(importedPages)
                    selectedPageIndex = firstNewIndex
                    editingPageIndex = firstNewIndex

                    flowStep = if (!appendOnImport && importedPages.size == 1) {
                        cameFromReview = false
                        ScanFlowStep.AdjustCorners
                    } else {
                        ScanFlowStep.Review
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    context.getString(R.string.image_import_error),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isImportingFiles = false
            }
        }
    }

    val pdfImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isImportingFiles = true
            try {
                val importedPages = withContext(Dispatchers.IO) {
                    val result = mutableListOf<ScannedPage>()
                    val fullFrame = listOf(
                        Offset(0f, 0f),
                        Offset(1f, 0f),
                        Offset(1f, 1f),
                        Offset(0f, 1f)
                    )
                    for (uri in uris) {
                        val bitmaps = DocImportHelper.importPdfPages(context, uri)
                        for (bmp in bitmaps) {
                            result.add(
                                ScannedPage(
                                    originalBitmap = bmp,
                                    rotatedBitmap = bmp,
                                    corners = fullFrame,
                                    autoCorners = fullFrame,
                                    isAutoCropSelected = false,
                                    croppedBitmap = bmp,
                                    processedBitmap = bmp
                                )
                            )
                        }
                    }
                    result
                }
                if (importedPages.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.pdf_import_error),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    if (!appendOnImport) {
                        pages.clear()
                    }
                    val firstNewIndex = pages.size
                    pages.addAll(importedPages)
                    selectedPageIndex = firstNewIndex
                    flowStep = ScanFlowStep.Review
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    context.getString(R.string.pdf_import_error),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isImportingFiles = false
            }
        }
    }

    if (isImportingFiles) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.importing_files),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Intercept hardware/system back button during scanning flow
    BackHandler(enabled = flowStep != ScanFlowStep.Idle) {
        when (flowStep) {
            ScanFlowStep.Camera -> {
                flowStep = if (pages.isNotEmpty()) ScanFlowStep.Review else ScanFlowStep.Idle
            }
            ScanFlowStep.AdjustCorners -> {
                if (cameFromReview || pages.size > 1) {
                    flowStep = ScanFlowStep.Review
                } else {
                    pages.clear()
                    flowStep = ScanFlowStep.Idle
                }
            }
            ScanFlowStep.Review -> {
                pages.clear()
                flowStep = ScanFlowStep.Idle
            }
            ScanFlowStep.Filters -> {
                flowStep = ScanFlowStep.Review
            }
            ScanFlowStep.SaveToFiles -> {
                flowStep = ScanFlowStep.Review
            }
            ScanFlowStep.Idle -> {}
        }
    }

    when (flowStep) {
        ScanFlowStep.Idle -> {
            val softBlue = MaterialTheme.colorScheme.primary

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row with Title "Scan"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 12.dp)
                        .height(48.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.scan),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Centered Action Panel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DocumentScanner,
                                contentDescription = null,
                                tint = softBlue,
                                modifier = Modifier.size(56.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.scan_doc_subtitle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            ScanHomeCapsuleButton(
                                icon = CenterFocusWeak,
                                label = stringResource(R.string.scan_docs),
                                onClick = {
                                    if (cameraPermissionState.status.isGranted) {
                                        pages.clear()
                                        flowStep = ScanFlowStep.Camera
                                    } else {
                                        pendingCameraLaunch = true
                                        cameraPermissionState.launchPermissionRequest()
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            ScanHomeCapsuleButton(
                                icon = Icons.Outlined.Image,
                                label = stringResource(R.string.image_to_pdf),
                                onClick = {
                                    appendOnImport = false
                                    imageImportLauncher.launch("image/*")
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            ScanHomeCapsuleButton(
                                icon = PictureAsPdf,
                                label = stringResource(R.string.two_pdf_in_one),
                                onClick = {
                                    appendOnImport = false
                                    pdfImportLauncher.launch("application/pdf")
                                }
                            )
                        }
                    }
                }
            }
        }

        ScanFlowStep.Camera -> {
            DocCameraView(
                onPhotoCaptured = { bitmap, detectedCorners ->
                    coroutineScope.launch {
                        val autoCropped = withContext(Dispatchers.Default) {
                            DocImageProcessor.cropPerspective(bitmap, detectedCorners)
                        }
                        val newPage = ScannedPage(
                            originalBitmap = bitmap,
                            rotatedBitmap = bitmap,
                            corners = detectedCorners,
                            autoCorners = detectedCorners,
                            isAutoCropSelected = true,
                            croppedBitmap = autoCropped,
                            processedBitmap = autoCropped
                        )
                        pages.add(newPage)
                        selectedPageIndex = pages.size - 1
                        editingPageIndex = pages.size - 1
                        cameFromReview = false
                        flowStep = ScanFlowStep.AdjustCorners
                    }
                },
                onClose = {
                    flowStep = if (pages.isNotEmpty()) ScanFlowStep.Review else ScanFlowStep.Idle
                }
            )
        }

        ScanFlowStep.AdjustCorners -> {
            val page = pages.getOrNull(editingPageIndex)
            if (page != null) {
                AdjustCornersView(
                    page = page,
                    onApply = { croppedBitmap, corners, rotation, isAutoCrop, rotatedBitmap, autoCorners ->
                        page.croppedBitmap = croppedBitmap
                        page.corners = corners
                        page.rotationDegrees = rotation
                        page.isAutoCropSelected = isAutoCrop
                        page.rotatedBitmap = rotatedBitmap
                        page.autoCorners = autoCorners
                        page.processedBitmap = DocImageProcessor.applyFilter(croppedBitmap, page.filter)
                        selectedPageIndex = editingPageIndex
                        flowStep = ScanFlowStep.Review
                    },
                    onRetake = {
                        pages.removeAt(editingPageIndex)
                        flowStep = ScanFlowStep.Camera
                    },
                    onBack = {
                        if (cameFromReview || pages.size > 1) {
                            flowStep = ScanFlowStep.Review
                        } else {
                            pages.clear()
                            flowStep = ScanFlowStep.Idle
                        }
                    }
                )
            } else {
                flowStep = ScanFlowStep.Idle
            }
        }

        ScanFlowStep.Review -> {
            DocReviewView(
                pages = pages,
                selectedPageIndex = selectedPageIndex,
                onSelectPage = { selectedPageIndex = it },
                onOpenCrop = { index ->
                    editingPageIndex = index
                    cameFromReview = true
                    flowStep = ScanFlowStep.AdjustCorners
                },
                onOpenFilters = { index ->
                    editingPageIndex = index
                    flowStep = ScanFlowStep.Filters
                },
                onDeletePage = { index ->
                    pages.removeAt(index)
                    if (pages.isEmpty()) {
                        flowStep = ScanFlowStep.Idle
                    } else {
                        selectedPageIndex = selectedPageIndex.coerceIn(0, pages.size - 1)
                    }
                },
                onAddPage = {
                    if (cameraPermissionState.status.isGranted) {
                        flowStep = ScanFlowStep.Camera
                    } else {
                        cameraPermissionState.launchPermissionRequest()
                    }
                },
                onAddImages = {
                    appendOnImport = true
                    imageImportLauncher.launch("image/*")
                },
                onAddPdfs = {
                    appendOnImport = true
                    pdfImportLauncher.launch("application/pdf")
                },
                onDiscardScan = {
                    pages.clear()
                    flowStep = ScanFlowStep.Idle
                },
                onNext = {
                    flowStep = ScanFlowStep.SaveToFiles
                },
                onBack = {
                    pages.clear()
                    flowStep = ScanFlowStep.Idle
                }
            )
        }

        ScanFlowStep.Filters -> {
            val page = pages.getOrNull(editingPageIndex)
            if (page != null) {
                DocFiltersSheet(
                    page = page,
                    onApplyFilter = { filter, filteredBitmap ->
                        page.filter = filter
                        page.processedBitmap = filteredBitmap
                        flowStep = ScanFlowStep.Review
                    },
                    onClose = {
                        flowStep = ScanFlowStep.Review
                    }
                )
            } else {
                flowStep = ScanFlowStep.Review
            }
        }

        ScanFlowStep.SaveToFiles -> {
            SaveToFilesView(
                pages = pages,
                onSave = { fileName, format ->
                    coroutineScope.launch {
                        try {
                            val successUri = when (format) {
                                DocExportFormat.PDF -> {
                                    withContext(Dispatchers.IO) {
                                        DocPdfExporter.exportPdf(context, pages, fileName)
                                    }
                                }
                                DocExportFormat.JPEG -> {
                                    withContext(Dispatchers.IO) {
                                        DocPdfExporter.exportJpeg(context, pages, fileName).firstOrNull()
                                    }
                                }
                            }

                            if (successUri != null) {
                                val savedMsg = context.getString(
                                    R.string.saved_successfully,
                                    "Scanned/$fileName.${format.name.lowercase()}"
                                )
                                Toast.makeText(context, savedMsg, Toast.LENGTH_LONG).show()
                                pages.clear()
                                flowStep = ScanFlowStep.Idle
                            } else {
                                Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onClose = {
                    flowStep = ScanFlowStep.Review
                }
            )
        }
    }
}

@Composable
private fun ScanHomeCapsuleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = RoundedCornerShape(50),
        modifier = Modifier.fillMaxWidth(0.9f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .padding(end = 6.dp)
        )
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
