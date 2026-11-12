package com.tkno.scan.docscan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkno.scan.R
import com.tkno.scan.ui.icon.PictureAsPdf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToFilesView(
    pages: List<ScannedPage>,
    onSave: (fileName: String, format: DocExportFormat) -> Unit,
    onClose: () -> Unit
) {
    if (pages.isEmpty()) return

    var fileName by remember { mutableStateOf(DocPdfExporter.getDefaultFileName()) }
    var selectedFormat by remember { mutableStateOf(DocExportFormat.PDF) }
    var isSaving by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.save_to_files),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Document Preview Thumbnail Card (matching Image 3)
            Box(
                modifier = Modifier
                    .size(width = 170.dp, height = 210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = pages.first().processedBitmap.asImageBitmap(),
                    contentDescription = "Preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Page count text below thumbnail
            Text(
                text = if (pages.size == 1) {
                    stringResource(R.string.page_count_single)
                } else {
                    stringResource(R.string.page_count_multiple, pages.size)
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            // File Name Input Field (matching Image 3)
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text(text = stringResource(R.string.file_name)) },
                leadingIcon = {
                    if (selectedFormat == DocExportFormat.PDF) {
                        Icon(
                            imageVector = PictureAsPdf,
                            contentDescription = "PDF",
                            tint = Color(0xFFEA4335), // Red PDF document icon
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "IMAGE",
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                trailingIcon = {
                    if (fileName.isNotEmpty()) {
                        IconButton(onClick = { fileName = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Format Selection Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.format),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // PDF Button
                    Button(
                        onClick = { selectedFormat = DocExportFormat.PDF },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedFormat == DocExportFormat.PDF) primaryColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (selectedFormat == DocExportFormat.PDF) onPrimaryColor else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "PDF",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // IMAGE Button
                    Button(
                        onClick = { selectedFormat = DocExportFormat.JPEG },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedFormat == DocExportFormat.JPEG) primaryColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (selectedFormat == DocExportFormat.JPEG) onPrimaryColor else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "IMAGE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // Full-Width Primary Pill "Save" Button
            Button(
                onClick = {
                    if (isSaving) return@Button
                    val finalName = fileName.trim().ifEmpty { DocPdfExporter.getDefaultFileName() }
                    isSaving = true
                    onSave(finalName, selectedFormat)
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = onPrimaryColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = onPrimaryColor,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.save),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
