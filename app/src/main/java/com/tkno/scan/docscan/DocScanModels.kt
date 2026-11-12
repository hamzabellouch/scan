package com.tkno.scan.docscan

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import java.util.UUID

enum class DocFilter {
    Original,
    Auto,
    Color,
    Grayscale,
    BW
}

enum class DocExportFormat {
    PDF,
    JPEG
}

enum class ScanFlowStep {
    Idle,           // Home screen with title "Scan" & "Scan" action button
    Camera,         // Full-screen CameraX live preview with capture shutter
    AdjustCorners,  // 4-corner crop & rotate screen (Images 2 & 4)
    Review,         // Scanned document review & multi-page strip (Image 5)
    Filters,        // Document filter options sheet (Image 1)
    SaveToFiles     // Save as PDF/JPEG screen (Image 3)
}

data class ScannedPage(
    val id: String = UUID.randomUUID().toString(),
    val originalBitmap: Bitmap,
    var rotatedBitmap: Bitmap = originalBitmap,
    var rotationDegrees: Int = 0,
    var corners: List<Offset> = listOf(
        Offset(0.06f, 0.06f), // Top-Left
        Offset(0.94f, 0.06f), // Top-Right
        Offset(0.94f, 0.94f), // Bottom-Right
        Offset(0.06f, 0.94f)  // Bottom-Left
    ),
    var autoCorners: List<Offset> = corners,
    var isAutoCropSelected: Boolean = true,
    var croppedBitmap: Bitmap = originalBitmap,
    var filter: DocFilter = DocFilter.Original,
    var processedBitmap: Bitmap = originalBitmap
)
