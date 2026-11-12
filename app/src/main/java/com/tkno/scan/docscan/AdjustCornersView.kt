package com.tkno.scan.docscan

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkno.scan.R
import com.tkno.scan.ui.component.ConfirmButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

private enum class CropMode {
    Auto,
    NoCrop,
    Manual
}

private fun cornersClose(a: List<Offset>, b: List<Offset>): Boolean {
    if (a.size != 4 || b.size != 4) return false
    return (0 until 4).all { i -> hypot(a[i].x - b[i].x, a[i].y - b[i].y) < 0.004f }
}

@Composable
fun AdjustCornersView(
    page: ScannedPage,
    onApply: (
        croppedBitmap: Bitmap,
        corners: List<Offset>,
        rotation: Int,
        isAutoCrop: Boolean,
        rotatedBitmap: Bitmap,
        autoCorners: List<Offset>
    ) -> Unit,
    onRetake: () -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var currentRotation by remember(page.id) { mutableIntStateOf(page.rotationDegrees) }
    var currentBitmap by remember(page.id) { mutableStateOf(page.rotatedBitmap) }
    var currentAutoCorners by remember(page.id) {
        mutableStateOf(page.autoCorners.ifEmpty { DocEdgeDetector.DEFAULT_CORNERS })
    }
    var isDetectingAuto by remember(page.id) { mutableStateOf(false) }

    val fullFrameCorners = remember {
        listOf(
            Offset(0f, 0f),
            Offset(1f, 0f),
            Offset(1f, 1f),
            Offset(0f, 1f)
        )
    }

    // Normalized corner coordinates [0..1]
    // Index 0: Top-Left, 1: Top-Right, 2: Bottom-Right, 3: Bottom-Left
    var corners by remember(page.id) {
        mutableStateOf(page.corners.ifEmpty { currentAutoCorners })
    }

    var cropMode by remember(page.id) {
        mutableStateOf(
            when {
                !page.isAutoCropSelected && cornersClose(corners, fullFrameCorners) -> CropMode.NoCrop
                page.isAutoCropSelected || cornersClose(corners, currentAutoCorners) -> CropMode.Auto
                else -> CropMode.Manual
            }
        )
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val brandBlue = Color(0xFF4285F4)
    val handleOuterBlue = Color(0xFF1A73E8)

    fun rotateCurrent() {
        val newRotation = (currentRotation + 90) % 360
        currentRotation = newRotation
        currentBitmap = DocImageProcessor.rotateBitmap(page.originalBitmap, newRotation.toFloat())
        corners = DocEdgeDetector.rotateNormalizedCornersClockwise90(corners)
        currentAutoCorners = DocEdgeDetector.rotateNormalizedCornersClockwise90(currentAutoCorners)
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
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.adjust_corners),
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
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main image canvas with corner overlay (outer container owns full touch area so edge handles stay easy to grab)
            val density = LocalDensity.current
            val imagePadH = 20.dp
            val imagePadV = 12.dp
            val padHPx = with(density) { imagePadH.toPx() }
            val padVPx = with(density) { imagePadV.toPx() }
            val currentCornersState by rememberUpdatedState(corners)
            var draggedIndex by remember(page.id) { mutableIntStateOf(-1) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { canvasSize = it },
                contentAlignment = Alignment.Center
            ) {
                // Display image inset so corner dots near [0..1] remain well inside touchable area
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "Scanned Page",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = imagePadH, vertical = imagePadV)
                )

                // Interactive Corner Overlay Canvas
                val innerWidth = (canvasSize.width - padHPx * 2f).coerceAtLeast(0f)
                val innerHeight = (canvasSize.height - padVPx * 2f).coerceAtLeast(0f)

                if (innerWidth > 0f && innerHeight > 0f) {
                    val imgRatio = currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
                    val boxRatio = innerWidth / innerHeight

                    val displayedWidth: Float
                    val displayedHeight: Float
                    val offsetX: Float
                    val offsetY: Float

                    if (imgRatio > boxRatio) {
                        displayedWidth = innerWidth
                        displayedHeight = displayedWidth / imgRatio
                        offsetX = padHPx
                        offsetY = padVPx + (innerHeight - displayedHeight) / 2f
                    } else {
                        displayedHeight = innerHeight
                        displayedWidth = displayedHeight * imgRatio
                        offsetX = padHPx + (innerWidth - displayedWidth) / 2f
                        offsetY = padVPx
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(page.id, displayedWidth, displayedHeight, offsetX, offsetY) {
                                val touchRadiusPx = 88.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val snapshot = currentCornersState
                                    if (snapshot.size != 4) return@awaitEachGesture

                                    var closestIndex = -1
                                    var minDistance = touchRadiusPx
                                    var grabOffset = Offset.Zero

                                    for ((index, c) in snapshot.withIndex()) {
                                        val screenPoint = Offset(
                                            offsetX + c.x * displayedWidth,
                                            offsetY + c.y * displayedHeight
                                        )
                                        val dist = hypot(
                                            down.position.x - screenPoint.x,
                                            down.position.y - screenPoint.y
                                        )
                                        if (dist < minDistance) {
                                            minDistance = dist
                                            closestIndex = index
                                            grabOffset = screenPoint - down.position
                                        }
                                    }

                                    if (closestIndex !in 0..3) return@awaitEachGesture

                                    down.consume()
                                    draggedIndex = closestIndex
                                    try {
                                        drag(down.id) { change ->
                                            change.consume()
                                            val targetScreen = change.position + grabOffset
                                            val newNormX = ((targetScreen.x - offsetX) / displayedWidth).coerceIn(0f, 1f)
                                            val newNormY = ((targetScreen.y - offsetY) / displayedHeight).coerceIn(0f, 1f)
                                            val updated = currentCornersState.toMutableList()
                                            if (updated.size == 4) {
                                                updated[closestIndex] = Offset(newNormX, newNormY)
                                                corners = updated
                                                cropMode = CropMode.Manual
                                            }
                                        }
                                    } finally {
                                        draggedIndex = -1
                                    }
                                }
                            }
                    ) {
                        val screenPoints = corners.map {
                            Offset(
                                offsetX + it.x * displayedWidth,
                                offsetY + it.y * displayedHeight
                            )
                        }

                        // 1. Draw connecting blue lines
                        val borderPath = Path().apply {
                            moveTo(screenPoints[0].x, screenPoints[0].y)
                            lineTo(screenPoints[1].x, screenPoints[1].y)
                            lineTo(screenPoints[2].x, screenPoints[2].y)
                            lineTo(screenPoints[3].x, screenPoints[3].y)
                            close()
                        }

                        drawPath(
                            path = borderPath,
                            color = brandBlue,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // 2. Draw 4 Corner Handles (enlarge active handle cleanly during drag)
                        val handleRadius = 14.dp.toPx()
                        val innerDotRadius = 6.dp.toPx()

                        screenPoints.forEachIndexed { index, pt ->
                            val isActive = index == draggedIndex
                            val outerHaloRadius = if (isActive) handleRadius * 1.9f else handleRadius * 1.5f
                            val solidRadius = if (isActive) handleRadius * 1.2f else handleRadius

                            // Outer translucent glow / halo
                            drawCircle(
                                color = handleOuterBlue.copy(alpha = if (isActive) 0.35f else 0.25f),
                                radius = outerHaloRadius,
                                center = pt
                            )
                            // Solid circular handle
                            drawCircle(
                                color = handleOuterBlue,
                                radius = solidRadius,
                                center = pt
                            )
                            // Inner white center dot
                            drawCircle(
                                color = Color.White,
                                radius = innerDotRadius,
                                center = pt
                            )
                        }
                    }
                }
            }

            // Middle action buttons styled identically to Filters / Crop & Rotate capsules
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                ScanToolCapsuleButton(
                    text = stringResource(R.string.automatic_crop),
                    icon = Icons.Outlined.AutoAwesome,
                    selected = cropMode == CropMode.Auto,
                    modifier = Modifier.weight(1f, fill = false),
                    onClick = {
                        cropMode = CropMode.Auto
                        if (!isDetectingAuto) {
                            isDetectingAuto = true
                            val bitmapSnapshot = currentBitmap
                            coroutineScope.launch {
                                val detected = withContext(Dispatchers.Default) {
                                    DocEdgeDetector.detectDocumentCorners(bitmapSnapshot, highAccuracy = true)
                                }
                                currentAutoCorners = detected
                                corners = detected
                                isDetectingAuto = false
                            }
                        }
                    }
                )

                ScanToolCapsuleButton(
                    text = stringResource(R.string.no_crop),
                    icon = Icons.Outlined.Crop,
                    selected = cropMode == CropMode.NoCrop,
                    modifier = Modifier.weight(1f, fill = false),
                    onClick = {
                        cropMode = CropMode.NoCrop
                        corners = fullFrameCorners
                    }
                )
            }

            // Action row (Image 4: Rotate and Retake)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rotate Action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { rotateCurrent() }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RotateRight,
                        contentDescription = "Rotate",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.rotate),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Retake Action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onRetake() }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = "Retake",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.retake),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Bottom Apply Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                ConfirmButton(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(bottom = 8.dp),
                    text = stringResource(R.string.apply),
                    onClick = {
                        val cropped = DocImageProcessor.cropPerspective(currentBitmap, corners)
                        onApply(
                            cropped,
                            corners,
                            currentRotation,
                            cropMode == CropMode.Auto,
                            currentBitmap,
                            currentAutoCorners
                        )
                    }
                )
            }
        }
    }
}
