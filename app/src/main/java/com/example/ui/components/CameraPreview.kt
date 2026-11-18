package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.video.ExtractedFrame
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "CameraPreview"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun CameraPreview(
    onFramesCaptured: (frames: List<ExtractedFrame>, sourceLabel: String, resolution: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasRequestedOnce by remember { mutableStateOf(false) }
    var showRationale by remember {
        mutableStateOf(
            activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        hasRequestedOnce = true
        showRationale = !granted && (activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } ?: false)
    }

    // Re-check camera permission when returning from system Settings
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                hasCameraPermission = granted
                if (granted) {
                    showRationale = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !showRationale) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val permanentlyDenied = !hasCameraPermission && hasRequestedOnce && !showRationale

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("camera_preview_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
                Text(
                    text = if (permanentlyDenied) {
                        "Akses Kamera Ditolak"
                    } else {
                        "Izin Kamera Diperlukan"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        permanentlyDenied ->
                            "Izin kamera dinonaktifkan secara permanen. Buka Pengaturan Aplikasi untuk mengaktifkan izin Kamera agar CameraX dapat memindai jalan Kebun Sei Rokan."
                        showRationale ->
                            "JalanAI membutuhkan akses kamera perangkat untuk menampilkan umpan langsung CameraX dan mengambil frame kerusakan jalan kebun."
                        else ->
                            "Aktifkan akses kamera untuk menampilkan umpan langsung (live feed) inspeksi permukaan jalan dengan CameraX."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (permanentlyDenied) {
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("open_camera_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Buka Pengaturan")
                        }
                    } else {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("grant_camera_permission_button")
                        ) {
                            Text("Izinkan Kamera")
                        }
                    }
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Tutup")
                    }
                }
                if (permanentlyDenied) {
                    TextButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Minta Izin Lagi")
                    }
                }
            }
        } else {
            LiveCameraXContent(
                onFramesCaptured = onFramesCaptured,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun LiveCameraXContent(
    onFramesCaptured: (frames: List<ExtractedFrame>, sourceLabel: String, resolution: String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val latestFrameRef = remember { AtomicReference<Bitmap?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var isCapturingSequence by remember { mutableStateOf(false) }
    var captureStep by remember { mutableIntStateOf(0) }
    val capturedSequence = remember { mutableStateListOf<ExtractedFrame>() }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                val bmp = imageProxyToScaledBitmap(imageProxy, maxDimension = 720)
                                val previous = latestFrameRef.getAndSet(bmp)
                                if (previous != null && previous !== bmp && !previous.isRecycled) {
                                    previous.recycle()
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Frame conversion error: ${e.message}")
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                cameraProvider.unbindAll()
                boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases", e)
                cameraError = e.localizedMessage ?: "Kamera tidak tersedia pada perangkat ini."
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(isCapturingSequence) {
        if (!isCapturingSequence) return@LaunchedEffect
        capturedSequence.clear()
        val totalFrames = 5
        for (i in 1..totalFrames) {
            captureStep = i
            val raw = latestFrameRef.get()
            if (raw != null && !raw.isRecycled) {
                val copy = raw.copy(Bitmap.Config.ARGB_8888, false)
                val tsMs = (i - 1) * 500L
                capturedSequence.add(
                    ExtractedFrame(
                        index = i,
                        timestampMs = tsMs,
                        formattedTimestamp = String.format(Locale.US, "00:%02d", (tsMs / 1000L)),
                        bitmap = copy
                    )
                )
            }
            if (i < totalFrames) delay(500L)
        }
        isCapturingSequence = false
        captureStep = 0
        if (capturedSequence.isNotEmpty()) {
            val first = capturedSequence.first().bitmap
            onFramesCaptured(
                capturedSequence.toList(),
                "Live CameraX Road Scan (${capturedSequence.size} frame)",
                "${first.width}x${first.height}"
            )
        } else {
            cameraError = "Belum ada frame kamera yang tertangkap. Coba lagi sebentar."
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color(0xFF0B111A))
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Engineering Road Surface Inspection Reticle Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                val w = size.width
                val h = size.height
                drawLine(
                    color = Color(0xAA38BDF8),
                    start = Offset(w * 0.15f, h * 0.78f),
                    end = Offset(w * 0.36f, h * 0.34f),
                    strokeWidth = 3f,
                    pathEffect = dash
                )
                drawLine(
                    color = Color(0xAA38BDF8),
                    start = Offset(w * 0.85f, h * 0.78f),
                    end = Offset(w * 0.64f, h * 0.34f),
                    strokeWidth = 3f,
                    pathEffect = dash
                )
                drawLine(
                    color = Color(0xBBF59E0B),
                    start = Offset(w * 0.25f, h * 0.56f),
                    end = Offset(w * 0.75f, h * 0.56f),
                    strokeWidth = 2.5f
                )
            }

            // Top HUD bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xBB0F172A),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isCapturingSequence) Color(0xFFEF4444) else Color(0xFF10B981))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isCapturingSequence) "MEMINDAI FRAME $captureStep/5" else "CAMERAX LIVE FEED",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                        IconButton(
                            onClick = {
                                val next = !torchEnabled
                                torchEnabled = next
                                boundCamera?.cameraControl?.enableTorch(next)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xAA0F172A))
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = "Lampu senter kamera",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xAA0F172A))
                            .testTag("close_camera_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Tutup kamera langsung",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (cameraError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                ) {
                    Text(
                        text = cameraError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    cameraError = null
                    isCapturingSequence = true
                },
                enabled = !isCapturingSequence && cameraError == null,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("capture_live_sequence_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.FiberManualRecord,
                    contentDescription = null,
                    tint = if (isCapturingSequence) Color(0xFFFCA5A5) else Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isCapturingSequence) "Merekam $captureStep/5..." else "Pindai Live (5 Frame)"
                )
            }

            OutlinedButton(
                onClick = {
                    cameraError = null
                    val raw = latestFrameRef.get()
                    if (raw != null && !raw.isRecycled) {
                        val copy = raw.copy(Bitmap.Config.ARGB_8888, false)
                        onFramesCaptured(
                            listOf(
                                ExtractedFrame(
                                    index = 1,
                                    timestampMs = 0L,
                                    formattedTimestamp = "00:00",
                                    bitmap = copy
                                )
                            ),
                            "Live CameraX Snapshot (1 frame)",
                            "${copy.width}x${copy.height}"
                        )
                    } else {
                        cameraError = "Frame kamera belum siap. Tunggu 1 detik lalu coba lagi."
                    }
                },
                enabled = !isCapturingSequence && cameraError == null,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("capture_live_single_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("1 Frame")
            }
        }
    }
}

private fun imageProxyToScaledBitmap(imageProxy: ImageProxy, maxDimension: Int): Bitmap {
    val rawBitmap = imageProxy.toBitmap()
    val rotation = imageProxy.imageInfo.rotationDegrees

    val rotated = if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val r = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        if (r !== rawBitmap) rawBitmap.recycle()
        r
    } else {
        rawBitmap
    }

    val width = rotated.width
    val height = rotated.height
    if (width <= maxDimension && height <= maxDimension) return rotated

    val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
    val newW = (width * ratio).toInt().coerceAtLeast(1)
    val newH = (height * ratio).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(rotated, newW, newH, true)
    if (scaled !== rotated) rotated.recycle()
    return scaled
}
