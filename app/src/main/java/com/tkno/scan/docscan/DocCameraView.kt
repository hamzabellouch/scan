package com.tkno.scan.docscan

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.tkno.scan.util.PreferenceUtil
import com.tkno.scan.R
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Full-screen Google-style document scanner camera matching the exact
 * visual chrome of the live scanner (white circle top buttons, blue corner
 * viewfinder, optional zoom slider pill, "Scanned by Scan" footer) with
 * a manual shutter capture circle at the bottom.
 */
@Composable
fun DocCameraView(
    onPhotoCaptured: (Bitmap, List<Offset>) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val softBlue = Color(0xFF8AB4F8)

    val prefs = remember { PreferenceUtil.getPrefs(context) }
    var showZoomSlider by remember {
        mutableStateOf(prefs.getBoolean("show_zoom_slider", false))
    }

    var showFlipCameraButton by remember {
        mutableStateOf(prefs.getBoolean("show_flip_camera_button", false))
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "show_zoom_slider" -> showZoomSlider = p.getBoolean("show_zoom_slider", false)
                "show_flip_camera_button" -> showFlipCameraButton = p.getBoolean("show_flip_camera_button", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var isTorchEnabled by remember { mutableStateOf(false) }
    var cameraLensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    LaunchedEffect(showFlipCameraButton) {
        if (!showFlipCameraButton && cameraLensFacing != CameraSelector.LENS_FACING_BACK) {
            cameraLensFacing = CameraSelector.LENS_FACING_BACK
        }
    }
    var zoomRatio by remember { mutableFloatStateOf(0f) }
    var isCapturing by remember { mutableStateOf(false) }

    val isCapturingAtomic = remember { AtomicBoolean(false) }
    val lensFacingAtomic = remember { AtomicInteger(CameraSelector.LENS_FACING_BACK) }
    val quadTracker = remember { QuadTracker() }
    var liveNormalizedQuad by remember { mutableStateOf<List<Point2f>?>(null) }
    var liveFrameWidth by remember { mutableIntStateOf(0) }
    var liveFrameHeight by remember { mutableIntStateOf(0) }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraState by remember { mutableStateOf<Camera?>(null) }
    var cameraProviderState by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var currentBoundLensFacing by remember { mutableIntStateOf(-1) }

    // Resolve host Activity even when nested inside a Dialog
    val activity = remember(context) {
        var ctx: Context = context
        while (ctx is ContextWrapper && ctx !is android.app.Activity) {
            ctx = ctx.baseContext
        }
        ctx as? android.app.Activity
    }

    val hasFrontCameraFlash = remember {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val frontId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            }
            frontId?.let {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    var isScreenFlashEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(isScreenFlashEnabled) {
        activity?.window?.let { win ->
            val lp = win.attributes
            lp.screenBrightness = if (isScreenFlashEnabled) 0.33f
            else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            win.attributes = lp
        }
    }

    LaunchedEffect(cameraLensFacing) {
        isTorchEnabled = false
        isScreenFlashEnabled = false
        lensFacingAtomic.set(cameraLensFacing)
        quadTracker.reset()
        liveNormalizedQuad = null
    }

    LaunchedEffect(isTorchEnabled, cameraControl) {
        try {
            cameraControl?.enableTorch(isTorchEnabled)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(zoomRatio, cameraControl) {
        try {
            cameraControl?.setLinearZoom(zoomRatio)
        } catch (_: Exception) {
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isTorchEnabled = false
            isScreenFlashEnabled = false
            try {
                cameraControl?.enableTorch(false)
            } catch (_: Exception) {
            }
            try {
                cameraProviderState?.unbindAll()
            } catch (_: Exception) {
            }
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = lp
            }
            cameraState = null
            cameraControl = null
            cameraExecutor.shutdown()
            analysisExecutor.shutdown()
        }
    }

    fun bindDocCameraUseCases(
        previewView: PreviewView,
        cameraProvider: ProcessCameraProvider,
        targetLensFacing: Int
    ) {
        val previewResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1440, 1080),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        val captureResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1920, 1440),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        val analysisResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(960, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(previewResolutionSelector)
            .build()
            .apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(captureResolutionSelector)
            .build()

        var lastAnalyzedTimeMs = 0L
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setResolutionSelector(analysisResolutionSelector)
            .build()

        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            try {
                val now = System.currentTimeMillis()
                if (isCapturingAtomic.get() || now - lastAnalyzedTimeMs < 55L) {
                    return@setAnalyzer
                }
                lastAnalyzedTimeMs = now

                val mirrorX = lensFacingAtomic.get() == CameraSelector.LENS_FACING_FRONT
                val output = DocEdgeDetector.detectDocumentFromImageProxy(
                    imageProxy = imageProxy,
                    mirrorHorizontally = mirrorX
                ) ?: return@setAnalyzer

                val (detection, frameSize) = output
                val tracked = quadTracker.update(detection)
                mainExecutor.execute {
                    if (!isCapturingAtomic.get()) {
                        liveFrameWidth = frameSize.first
                        liveFrameHeight = frameSize.second
                        liveNormalizedQuad = tracked
                    }
                }
            } finally {
                imageProxy.close()
            }
        }

        val selector = CameraSelector.Builder()
            .requireLensFacing(targetLensFacing)
            .build()

        val viewPort = previewView.viewPort
        try {
            cameraProvider.unbindAll()
            val groupBuilder = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(capture)
                .addUseCase(analysis)
            viewPort?.let { groupBuilder.setViewPort(it) }

            val cam = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                groupBuilder.build()
            )
            cameraState = cam
            cameraControl = cam.cameraControl
            cameraControl?.setLinearZoom(zoomRatio)
            imageCapture = capture
            currentBoundLensFacing = targetLensFacing
        } catch (exc: Exception) {
            Log.w("DocCameraView", "3-use-case binding failed, falling back to Preview + Capture", exc)
            try {
                cameraProvider.unbindAll()
                val fallbackGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(capture)
                viewPort?.let { fallbackGroup.setViewPort(it) }

                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    fallbackGroup.build()
                )
                cameraState = cam
                cameraControl = cam.cameraControl
                cameraControl?.setLinearZoom(zoomRatio)
                imageCapture = capture
                currentBoundLensFacing = targetLensFacing
            } catch (fallbackExc: Exception) {
                Log.e("DocCameraView", "Use case binding failed", fallbackExc)
            }
        }
    }

    fun triggerCapture() {
        val capture = imageCapture ?: return
        if (isCapturing) return
        isCapturing = true
        isCapturingAtomic.set(true)

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }

        capture.flashMode = if (isTorchEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
                        val corrected = decodeCaptureBitmap(
                            imageProxy = imageProxy,
                            rotationDegrees = rotation
                        )
                        val detectedCorners = DocEdgeDetector.detectDocumentCorners(corrected, highAccuracy = true)

                        mainExecutor.execute {
                            isCapturing = false
                            isCapturingAtomic.set(false)
                            onPhotoCaptured(corrected, detectedCorners)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runCatching { imageProxy.close() }
                        mainExecutor.execute {
                            isCapturing = false
                            isCapturingAtomic.set(false)
                            Toast.makeText(context, "Error processing capture", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    mainExecutor.execute {
                        isCapturing = false
                        isCapturingAtomic.set(false)
                        Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val screenWidthPx = constraints.maxWidth.toFloat()
            val screenHeightPx = constraints.maxHeight.toFloat()

            val frameWidthPx = (screenWidthPx * 0.80f).coerceAtLeast(100f)
            val frameHeightPx = (frameWidthPx * 1.28f).coerceAtMost(screenHeightPx * 0.56f)

            // Camera Preview Stream + ImageCapture Use Case
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    val scaleGestureDetector = ScaleGestureDetector(
                        ctx,
                        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                val cam = cameraState ?: return false
                                val zoomState = cam.cameraInfo.zoomState.value ?: return false
                                val targetRatio = (zoomState.zoomRatio * detector.scaleFactor)
                                    .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                                cameraControl?.setZoomRatio(targetRatio)
                                return true
                            }
                        }
                    )

                    var isMultiTouch = false
                    previewView.setOnTouchListener { view, event ->
                        if (event.pointerCount > 1) {
                            isMultiTouch = true
                        }
                        scaleGestureDetector.onTouchEvent(event)

                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            if (!isMultiTouch && !scaleGestureDetector.isInProgress &&
                                event.eventTime - event.downTime < 300
                            ) {
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(event.x, event.y)
                                val action = FocusMeteringAction.Builder(
                                    point,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                cameraControl?.startFocusAndMetering(action)
                                view.performClick()
                            }
                            isMultiTouch = false
                        }
                        true
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProviderState = cameraProvider
                        previewView.post {
                            bindDocCameraUseCases(previewView, cameraProvider, cameraLensFacing)
                        }
                    }, mainExecutor)

                    previewView
                },
                update = { previewView ->
                    val cameraProvider = cameraProviderState ?: return@AndroidView
                    if (currentBoundLensFacing != cameraLensFacing) {
                        bindDocCameraUseCases(previewView, cameraProvider, cameraLensFacing)
                    } else {
                        cameraControl?.setLinearZoom(zoomRatio)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Front screen flash overlay when front lens has no hardware flash
            if (isScreenFlashEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(width = 18.dp, color = Color.White)
                )
            }

            // Dynamic blue corner viewfinder that automatically tracks detected document edges
            val defaultQuadScreen = remember(screenWidthPx, screenHeightPx, frameWidthPx, frameHeightPx) {
                val cx = screenWidthPx * 0.5f
                val cy = screenHeightPx * 0.45f
                val hw = frameWidthPx * 0.5f
                val hh = frameHeightPx * 0.5f
                listOf(
                    Offset(cx - hw, cy - hh),
                    Offset(cx + hw, cy - hh),
                    Offset(cx + hw, cy + hh),
                    Offset(cx - hw, cy + hh)
                )
            }

            val trackedScreenQuad = remember(
                liveNormalizedQuad,
                liveFrameWidth,
                liveFrameHeight,
                screenWidthPx,
                screenHeightPx
            ) {
                val quad = liveNormalizedQuad
                if (quad != null && quad.size == 4 && liveFrameWidth > 0 && liveFrameHeight > 0) {
                    DocEdgeDetector.mapNormalizedQuadToFillCenterScreen(
                        normCorners = quad,
                        frameWidth = liveFrameWidth,
                        frameHeight = liveFrameHeight,
                        viewWidth = screenWidthPx,
                        viewHeight = screenHeightPx
                    )
                } else {
                    null
                }
            }

            val isDocumentDetected = trackedScreenQuad != null && trackedScreenQuad.size == 4
            val targetQuad = trackedScreenQuad ?: defaultQuadScreen

            val animTL by animateOffsetAsState(
                targetValue = targetQuad[0],
                animationSpec = tween(durationMillis = 120),
                label = "docCornerTL"
            )
            val animTR by animateOffsetAsState(
                targetValue = targetQuad[1],
                animationSpec = tween(durationMillis = 120),
                label = "docCornerTR"
            )
            val animBR by animateOffsetAsState(
                targetValue = targetQuad[2],
                animationSpec = tween(durationMillis = 120),
                label = "docCornerBR"
            )
            val animBL by animateOffsetAsState(
                targetValue = targetQuad[3],
                animationSpec = tween(durationMillis = 120),
                label = "docCornerBL"
            )

            val outlineAlpha by animateFloatAsState(
                targetValue = if (isDocumentDetected) 0.55f else 0f,
                animationSpec = tween(durationMillis = 180),
                label = "docOutlineAlpha"
            )
            val fillAlpha by animateFloatAsState(
                targetValue = if (isDocumentDetected) 0.10f else 0f,
                animationSpec = tween(durationMillis = 180),
                label = "docFillAlpha"
            )

            GoogleStyleQuadViewfinder(
                corners = listOf(animTL, animTR, animBR, animBL),
                outlineAlpha = outlineAlpha,
                fillAlpha = fillAlpha,
                color = softBlue,
                modifier = Modifier.fillMaxSize()
            )

            // Top Control Bar (matches FullScreenScannerModal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Close button (white circle with black X)
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.scan_document),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // Flash + Flip Camera stacked white circle buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        onClick = {
                            val isFront = cameraLensFacing == CameraSelector.LENS_FACING_FRONT
                            if (isFront && !hasFrontCameraFlash) {
                                isScreenFlashEnabled = !isScreenFlashEnabled
                            } else {
                                isTorchEnabled = !isTorchEnabled
                            }
                        },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isTorchEnabled || isScreenFlashEnabled) {
                                    Icons.Outlined.FlashOn
                                } else {
                                    Icons.Outlined.FlashOff
                                },
                                contentDescription = "Flashlight",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (showFlipCameraButton) {
                        Surface(
                            onClick = {
                                cameraLensFacing = if (cameraLensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Cameraswitch,
                                    contentDescription = "Flip camera",
                                    tint = Color.Black,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Controls Area: Zoom Slider (optional) + Capture Circle + Scanned by Scan Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showZoomSlider) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ZoomOut,
                                contentDescription = "Zoom out",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { zoomRatio = 0f }
                            )

                            Slider(
                                value = zoomRatio,
                                onValueChange = { zoomRatio = it },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = softBlue,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            Icon(
                                imageVector = Icons.Outlined.ZoomIn,
                                contentDescription = "Zoom in",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { zoomRatio = 1f }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Manual Capture Circle Button (clean ring + inner circle without a heavy bar)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .border(width = 3.dp, color = Color.White, shape = CircleShape)
                        .clickable(
                            enabled = !isCapturing,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            triggerCapture()
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = softBlue,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Footer ("Scanned by Scan") matching FullScreenScannerModal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DocumentScanner,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )

                    Text(
                        text = "Scanned by Scan",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Decodes a CameraX capture frame using JPEG bounds subsampling when available,
 * closes [imageProxy] immediately, caps longest dimension cleanly, and applies upright rotation.
 */
private fun decodeCaptureBitmap(
    imageProxy: ImageProxy,
    rotationDegrees: Float,
    maxLongEdge: Int = 2048
): Bitmap {
    val decoded = if (imageProxy.format == ImageFormat.JPEG && imageProxy.planes.isNotEmpty()) {
        val buffer = imageProxy.planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        imageProxy.close()

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val srcMax = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (srcMax / (sampleSize * 2) >= maxLongEdge) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: throw IllegalStateException("Failed to decode captured JPEG")
    } else {
        val fallback = imageProxy.toBitmap()
        imageProxy.close()
        fallback
    }

    val longest = max(decoded.width, decoded.height)
    val scaled = if (longest > maxLongEdge && longest > 0) {
        val factor = maxLongEdge.toFloat() / longest.toFloat()
        val w = max(1, (decoded.width * factor).toInt())
        val h = max(1, (decoded.height * factor).toInt())
        Bitmap.createScaledBitmap(decoded, w, h, true).also {
            if (it !== decoded && !decoded.isRecycled) decoded.recycle()
        }
    } else {
        decoded
    }

    val rotated = DocImageProcessor.rotateBitmap(scaled, rotationDegrees)
    if (rotated !== scaled && !scaled.isRecycled) {
        scaled.recycle()
    }
    return rotated
}
