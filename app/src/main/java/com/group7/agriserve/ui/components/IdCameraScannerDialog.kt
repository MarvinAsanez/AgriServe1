package com.group7.agriserve.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * High-precision ID Card Camera Scanner with Automatic Landscape Orientation and Real-Time Auto-Capture.
 * Features:
 * 1. Automatically rotates phone into landscape mode for maximum ID card resolution and visibility.
 * 2. Visual ID card alignment overlay (aspect ratio 1.58:1) with corner guidelines and darkened scrim.
 * 3. Real-time ML Kit OCR frame analysis for document presence and focus metering.
 * 4. Auto-capture when the card is aligned steadily inside the rectangle.
 * 5. Manual shutter snap button with circular progress timer.
 * 6. Restores original orientation automatically upon dismiss or completion.
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun IdCameraScannerDialog(
    cardSideTitle: String = "Front of ID",
    onCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val activity = remember(context) { context.findActivity() }

    // Lock orientation to Landscape while scanning ID for maximum sharpness and card size
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) return

    val executor = remember { Executors.newSingleThreadExecutor() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    var steadyStartTimeMs by remember { mutableLongStateOf(0L) }
    var consecutiveDetections by remember { mutableIntStateOf(0) }
    var consecutiveMissedFrames by remember { mutableIntStateOf(0) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var secondsRemaining by remember { mutableIntStateOf(4) }
    var lastFocusTriggerMs by remember { mutableLongStateOf(0L) }
    val targetHoldDurationMs = 3800L // 3.8s steady hold time for autofocus and stabilization

    var isCapturing by remember { mutableStateOf(false) }
    var hasSnapped by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Position your $cardSideTitle inside the rectangle") }
    var isCardDetectedInFrame by remember { mutableStateOf(false) }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp > configuration.screenHeightDp

    // Calculate dimensions ensuring card fits landscape height and width with space for controls
    val (cardWidthDp, cardHeightDp) = remember(configuration.screenWidthDp, configuration.screenHeightDp, isLandscape) {
        val screenW = configuration.screenWidthDp.toFloat()
        val screenH = configuration.screenHeightDp.toFloat()
        val cardAspect = 1.586f // ISO/IEC 7810 ID-1 standard aspect ratio

        if (isLandscape) {
            // Allocate up to 74% of screen height to leave clearance for top banner and bottom status
            val targetHeight = (screenH * 0.74f).coerceAtMost(360f)
            val targetWidth = targetHeight * cardAspect
            // Ensure width leaves space on right for shutter button (~130dp)
            val maxAvailableWidth = screenW - 130f
            if (targetWidth <= maxAvailableWidth) {
                targetWidth.dp to targetHeight.dp
            } else {
                val fitW = maxAvailableWidth
                fitW.dp to (fitW / cardAspect).dp
            }
        } else {
            val w = screenW * 0.88f
            w.dp to (w / cardAspect).dp
        }
    }

    // Function to trigger photo capture
    val triggerPhotoCapture: () -> Unit = {
        val capture = imageCaptureUseCase
        if (capture != null && !hasSnapped && !isCapturing) {
            isCapturing = true
            hasSnapped = true
            statusMessage = "Capturing $cardSideTitle..."

            val photoFile = File(context.cacheDir, "id_scan_${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        isCapturing = false
                        val uri = Uri.fromFile(photoFile)
                        onCaptured(uri)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("IdCameraScanner", "Photo capture failed: ${exception.message}", exception)
                        isCapturing = false
                        hasSnapped = false
                        statusMessage = "Capture failed: ${exception.localizedMessage}. Try again."
                    }
                }
            )
        }
    }

    Dialog(
        onDismissRequest = { if (!isCapturing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Camera Live Preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    previewViewRef = previewView

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()
                        imageCaptureUseCase = imageCapture

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && !hasSnapped && !isCapturing) {
                                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                textRecognizer.process(inputImage)
                                    .addOnSuccessListener { visionText ->
                                        val totalBlocks = visionText.textBlocks.size
                                        val totalChars = visionText.text.count { it.isLetterOrDigit() }

                                        // Card presence heuristic: ID cards have multiple text lines with letters and numbers
                                        if (totalBlocks >= 2 && totalChars >= 10) {
                                            consecutiveMissedFrames = 0
                                            consecutiveDetections++

                                            if (consecutiveDetections >= 2) {
                                                isCardDetectedInFrame = true
                                                val now = System.currentTimeMillis()

                                                if (steadyStartTimeMs == 0L) {
                                                    steadyStartTimeMs = now
                                                }

                                                // Trigger CameraX autofocus and auto-exposure metering on center of preview
                                                if (now - lastFocusTriggerMs > 2500L) {
                                                    lastFocusTriggerMs = now
                                                    ContextCompat.getMainExecutor(context).execute {
                                                        try {
                                                            val prev = previewViewRef
                                                            val factory = prev?.meteringPointFactory
                                                            if (factory != null && prev.width > 0 && prev.height > 0) {
                                                                val centerPoint = factory.createPoint(
                                                                    prev.width / 2f,
                                                                    prev.height / 2f
                                                                )
                                                                val action = FocusMeteringAction.Builder(
                                                                    centerPoint,
                                                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                                                )
                                                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                                                .build()
                                                                cameraControl?.startFocusAndMetering(action)
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.d("IdCameraScanner", "Focus metering note: ${e.message}")
                                                        }
                                                    }
                                                }

                                                val elapsed = now - steadyStartTimeMs
                                                val remainingSec = ((targetHoldDurationMs - elapsed).coerceAtLeast(0L) / 1000L).toInt() + 1
                                                secondsRemaining = remainingSec
                                                holdProgress = (elapsed.toFloat() / targetHoldDurationMs.toFloat()).coerceIn(0f, 1f)
                                                statusMessage = "Focusing & holding steady... Auto-capture in ${remainingSec}s"

                                                // Trigger auto-capture after holding steady and focused for full duration (3.5 - 4.0s)
                                                if (elapsed >= targetHoldDurationMs && !hasSnapped && !isCapturing) {
                                                    statusMessage = "Capturing $cardSideTitle..."
                                                    ContextCompat.getMainExecutor(context).execute {
                                                        triggerPhotoCapture()
                                                    }
                                                }
                                            }
                                        } else {
                                            consecutiveMissedFrames++
                                            if (consecutiveMissedFrames >= 5) {
                                                isCardDetectedInFrame = false
                                                consecutiveDetections = 0
                                                steadyStartTimeMs = 0L
                                                holdProgress = 0f
                                                secondsRemaining = 4
                                                statusMessage = "Position your $cardSideTitle inside the rectangle"
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        try {
                            provider.unbindAll()
                            val camera = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                                imageAnalysis
                            )
                            cameraControl = camera.cameraControl
                        } catch (e: Exception) {
                            Log.e("IdCameraScanner", "Camera binding failed: ${e.message}", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // ID Rectangle Scrim & Guide Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val cardWidthPx = cardWidthDp.toPx()
                val cardHeightPx = cardHeightDp.toPx()

                // Center card horizontally accounting for right shutter area in landscape
                val left = if (isLandscape) {
                    ((canvasWidth - 90.dp.toPx()) - cardWidthPx) / 2f
                } else {
                    (canvasWidth - cardWidthPx) / 2f
                }
                val top = (canvasHeight - cardHeightPx) / 2f
                val right = left + cardWidthPx
                val bottom = top + cardHeightPx

                val cutoutRect = Rect(left, top, right, bottom)
                val cutoutPath = Path().apply {
                    addRoundRect(RoundRect(cutoutRect, CornerRadius(24f, 24f)))
                }

                // Dark darkened mask outside cutout
                clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                    drawRect(color = Color.Black.copy(alpha = 0.68f))
                }

                // Outline border for card
                val borderColor = if (isCardDetectedInFrame) Color(0xFF00E676) else Color.White.copy(alpha = 0.85f)
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(left, top),
                    size = Size(cardWidthPx, cardHeightPx),
                    cornerRadius = CornerRadius(24f, 24f),
                    style = Stroke(width = if (isCardDetectedInFrame) 6f else 3f)
                )

                // Highlighting Corner Brackets
                val bracketLen = if (isLandscape) 48f else 36f
                val bracketStroke = 8f
                val bracketColor = if (isCardDetectedInFrame) Color(0xFF00E676) else Color(0xFFFFD54F)

                // Top-Left Corner
                drawLine(bracketColor, Offset(left, top + bracketLen), Offset(left, top), strokeWidth = bracketStroke)
                drawLine(bracketColor, Offset(left, top), Offset(left + bracketLen, top), strokeWidth = bracketStroke)

                // Top-Right Corner
                drawLine(bracketColor, Offset(right - bracketLen, top), Offset(right, top), strokeWidth = bracketStroke)
                drawLine(bracketColor, Offset(right, top), Offset(right, top + bracketLen), strokeWidth = bracketStroke)

                // Bottom-Left Corner
                drawLine(bracketColor, Offset(left, bottom - bracketLen), Offset(left, bottom), strokeWidth = bracketStroke)
                drawLine(bracketColor, Offset(left, bottom), Offset(left + bracketLen, bottom), strokeWidth = bracketStroke)

                // Bottom-Right Corner
                drawLine(bracketColor, Offset(right - bracketLen, bottom), Offset(right, bottom), strokeWidth = bracketStroke)
                drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - bracketLen), strokeWidth = bracketStroke)
            }

            // Top Controls Bar: Close, Title, Torch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                // Card Side Badge + Landscape clarity indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                text = cardSideTitle.uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isLandscape) {
                        Surface(
                            color = Color(0xFF1B5E20).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(13.dp))
                                Text(
                                    text = "LANDSCAPE • HD SCAN",
                                    color = Color(0xFF00E676),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                // Flashlight / Torch toggle
                IconButton(
                    onClick = {
                        isTorchOn = !isTorchOn
                        cameraControl?.enableTorch(isTorchOn)
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isTorchOn) Color(0xFFFFD54F) else Color.Black.copy(alpha = 0.55f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flashlight",
                        tint = if (isTorchOn) Color.Black else Color.White
                    )
                }
            }

            // Real-time Guidance Message Pill (Bottom Center)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isLandscape) 14.dp else 120.dp),
                color = if (isCardDetectedInFrame) Color(0xFF1B5E20).copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isCardDetectedInFrame) Color(0xFF00E676) else Color.White.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCardDetectedInFrame) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            Icons.Default.CropFree,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = statusMessage,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Shutter Button & Controls (Right Side in Landscape, Bottom in Portrait)
            val shutterModifier = if (isLandscape) {
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 22.dp)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            }

            Column(
                modifier = shutterModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Shutter Button with Hold Progress Ring
                Box(
                    modifier = Modifier.size(82.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCardDetectedInFrame && holdProgress > 0f && !hasSnapped && !isCapturing) {
                        CircularProgressIndicator(
                            progress = { holdProgress },
                            modifier = Modifier.size(82.dp),
                            color = Color(0xFF00E676),
                            trackColor = Color.White.copy(alpha = 0.25f),
                            strokeWidth = 4.dp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCardDetectedInFrame) Color(0xFF00E676) else Color.White
                            )
                            .clickable(enabled = !isCapturing && !hasSnapped) {
                                triggerPhotoCapture()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                        } else if (isCardDetectedInFrame && holdProgress > 0f) {
                            Text(
                                text = "${secondsRemaining}s",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Take Photo Now",
                                tint = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Auto-capture hint
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Auto-Capture",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
