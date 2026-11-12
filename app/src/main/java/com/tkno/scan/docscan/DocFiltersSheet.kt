package com.tkno.scan.docscan

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
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
import com.tkno.scan.ui.component.ConfirmButton

@Composable
fun DocFiltersSheet(
    page: ScannedPage,
    onApplyFilter: (DocFilter, Bitmap) -> Unit,
    onClose: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(page.filter) }
    var previewBitmap by remember(selectedFilter) {
        mutableStateOf(DocImageProcessor.applyFilter(page.croppedBitmap, selectedFilter))
    }

    val brandBlue = Color(0xFFA8C7FA)

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
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
            // Live Filter Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Filtered Document",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Presets Carousel (matching Image 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DocFilter.entries.forEach { filter ->
                    val isSelected = filter == selectedFilter

                    val filterLabel = when (filter) {
                        DocFilter.Original -> stringResource(R.string.filter_original)
                        DocFilter.Auto -> stringResource(R.string.filter_auto)
                        DocFilter.Color -> stringResource(R.string.filter_color)
                        DocFilter.Grayscale -> stringResource(R.string.filter_grayscale)
                        DocFilter.BW -> stringResource(R.string.filter_bw)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { selectedFilter = filter }
                            .padding(4.dp)
                    ) {
                        // Filter Card Icon Box
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) brandBlue.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF1A73E8) else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Document Icon representing the filter style
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = filterLabel,
                                tint = when (filter) {
                                    DocFilter.Original -> Color(0xFF4285F4)
                                    DocFilter.Auto -> Color(0xFF34A853)
                                    DocFilter.Color -> Color(0xFFFBBC04)
                                    DocFilter.Grayscale -> Color.Gray
                                    DocFilter.BW -> MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = filterLabel,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF1A73E8) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Bottom Apply Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                ConfirmButton(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    text = stringResource(R.string.apply),
                    onClick = {
                        onApplyFilter(selectedFilter, previewBitmap)
                    }
                )
            }
        }
    }
}
