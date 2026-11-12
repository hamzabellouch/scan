package com.tkno.scan.docscan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropRotate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkno.scan.R
import com.tkno.scan.ui.component.ConfirmButton
import com.tkno.scan.ui.component.DismissButton
import com.tkno.scan.ui.icon.PictureAsPdf

@Composable
fun DocReviewView(
    pages: List<ScannedPage>,
    selectedPageIndex: Int,
    onSelectPage: (Int) -> Unit,
    onOpenCrop: (Int) -> Unit,
    onOpenFilters: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onAddImages: (() -> Unit)? = null,
    onAddPdfs: (() -> Unit)? = null,
    onDiscardScan: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    if (pages.isEmpty()) return

    val currentPageIndex = selectedPageIndex.coerceIn(0, pages.size - 1)
    val currentPage = pages[currentPageIndex]
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(R.string.delete_page_title)) },
            text = { Text(text = stringResource(R.string.delete_page_msg)) },
            confirmButton = {
                ConfirmButton(
                    text = stringResource(R.string.delete_item),
                    icon = Icons.Outlined.Delete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        showDeleteDialog = false
                        onDeletePage(currentPageIndex)
                    }
                )
            },
            dismissButton = {
                DismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showDeleteDialog = false }
                )
            }
        )
    }

    // Discard Confirmation Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(text = stringResource(R.string.discard_confirm_title)) },
            text = { Text(text = stringResource(R.string.discard_confirm_msg)) },
            confirmButton = {
                ConfirmButton(
                    text = stringResource(R.string.discard_scan),
                    icon = Icons.Outlined.Delete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        showDiscardDialog = false
                        onDiscardScan()
                    }
                )
            },
            dismissButton = {
                DismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showDiscardDialog = false }
                )
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Document Page Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = currentPage.processedBitmap.asImageBitmap(),
                    contentDescription = "Document Page ${currentPageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Floating actions at bottom right of image preview (Crop and Delete)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Crop Button
                    IconButton(
                        onClick = { onOpenCrop(currentPageIndex) },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Crop,
                            contentDescription = "Crop",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Delete Page Button
                    IconButton(
                        onClick = {
                            if (pages.size > 1) {
                                showDeleteDialog = true
                            } else {
                                showDiscardDialog = true
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete page",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tools Row: [ Filters ] and [ Crop & rotate ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScanToolCapsuleButton(
                    text = stringResource(R.string.filters),
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = { onOpenFilters(currentPageIndex) }
                )

                ScanToolCapsuleButton(
                    text = stringResource(R.string.crop_and_rotate),
                    icon = Icons.Outlined.CropRotate,
                    onClick = { onOpenCrop(currentPageIndex) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scanned Pages Thumbnail Strip + Add Page FAB
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Horizontal list of thumbnails
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { index, p ->
                        val isSelected = index == currentPageIndex
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF4285F4) else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectPage(index) }
                        ) {
                            Image(
                                bitmap = p.processedBitmap.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Page badge at bottom center
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 2.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF8AB4F8))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // [ 📄+ ] Add Page FAB with Camera / Image / PDF menu
                Box {
                    FloatingActionButton(
                        onClick = {
                            if (onAddImages != null || onAddPdfs != null) {
                                showAddMenu = true
                            } else {
                                onAddPage()
                            }
                        },
                        containerColor = Color(0xFF004977),
                        contentColor = Color(0xFFC2E7FF),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NoteAdd,
                            contentDescription = "Add page",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_from_camera)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            },
                            onClick = {
                                showAddMenu = false
                                onAddPage()
                            }
                        )
                        if (onAddImages != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_from_gallery)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Image, contentDescription = null)
                                },
                                onClick = {
                                    showAddMenu = false
                                    onAddImages()
                                }
                            )
                        }
                        if (onAddPdfs != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_from_pdf)) },
                                leadingIcon = {
                                    Icon(PictureAsPdf, contentDescription = null)
                                },
                                onClick = {
                                    showAddMenu = false
                                    onAddPdfs()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Navigation Row: [ Discard scan ] and [ Next ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DismissButton(
                    text = stringResource(R.string.discard_scan),
                    onClick = { showDiscardDialog = true }
                )

                ConfirmButton(
                    text = stringResource(R.string.next),
                    icon = Icons.AutoMirrored.Outlined.ArrowForward,
                    onClick = onNext
                )
            }
        }
    }
}

/**
 * Unified tool capsule shared across Filters, Crop & Rotate, Automatic Crop, and No Crop.
 */
@Composable
internal fun ScanToolCapsuleButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
