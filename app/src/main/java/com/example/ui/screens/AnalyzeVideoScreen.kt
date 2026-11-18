package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.model.RoadConditionCategory
import com.example.data.model.SeiRokanCatalog
import com.example.data.model.TrafficIntensity
import com.example.data.video.ExtractedFrame
import com.example.data.video.VideoExtractionResult
import com.example.ui.components.CameraPreview
import com.example.ui.components.DamageBreakdownCard
import com.example.ui.components.DegradationCurveChart
import com.example.ui.components.PciRingGauge
import com.example.ui.components.conditionBgColor
import com.example.ui.components.conditionColor
import com.example.ui.theme.StatusGood
import com.example.ui.viewmodel.AnalysisUiState
import java.io.File
import java.util.Locale

@Composable
fun AnalyzeVideoScreen(
    analysisState: AnalysisUiState,
    isLocalFieldMode: Boolean,
    isOnline: Boolean,
    onLoadVideoUri: (Uri) -> Unit,
    onLoadImageUri: (Uri) -> Unit,
    onLoadLiveFrames: (frames: List<ExtractedFrame>, sourceLabel: String, resolution: String) -> Unit,
    onRunAiAnalysis: (roadName: String, segmentLocation: String, regionName: String, traffic: TrafficIntensity, notes: String) -> Unit,
    onSaveAnalysis: () -> Unit,
    onReset: () -> Unit,
    onOpenSavedDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showLiveCamera by rememberSaveable { mutableStateOf(false) }
    var selectedAfdelingCode by rememberSaveable { mutableStateOf(SeiRokanCatalog.afdelings.first().code) }
    var selectedBlockCode by rememberSaveable {
        mutableStateOf(SeiRokanCatalog.afdelings.first().blocks.first())
    }
    var roadTypeLabel by rememberSaveable { mutableStateOf(SeiRokanCatalog.roadTypePresets.first()) }
    var roadName by rememberSaveable {
        mutableStateOf("${SeiRokanCatalog.roadTypePresets.first()} Blok ${SeiRokanCatalog.afdelings.first().blocks.first()}")
    }
    var segmentLocation by rememberSaveable {
        mutableStateOf(
            SeiRokanCatalog.formatSegmentLabel(
                SeiRokanCatalog.afdelings.first().displayName,
                SeiRokanCatalog.afdelings.first().blocks.first()
            )
        )
    }
    var regionName by rememberSaveable { mutableStateOf(SeiRokanCatalog.ESTATE_REGION) }
    var trafficIntensity by rememberSaveable { mutableStateOf(TrafficIntensity.SEDANG) }
    var fieldNotes by rememberSaveable { mutableStateOf("") }

    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            showLiveCamera = false
            onLoadVideoUri(it)
        }
    }

    val documentVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            showLiveCamera = false
            onLoadVideoUri(it)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            showLiveCamera = false
            onLoadImageUri(it)
        }
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            pendingCaptureUri?.let {
                showLiveCamera = false
                onLoadVideoUri(it)
            }
        }
    }

    fun startSystemVideoCapture() {
        val dir = File(context.cacheDir, "road_videos").apply { mkdirs() }
        val file = File(dir, "road_capture_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        pendingCaptureUri = uri
        captureVideoLauncher.launch(uri)
    }

    val cameraPermissionForCapture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSystemVideoCapture()
    }

    fun launchCameraVideoCapture() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startSystemVideoCapture()
        } else {
            cameraPermissionForCapture.launch(Manifest.permission.CAMERA)
        }
    }

    val currentExtraction: VideoExtractionResult? = when (analysisState) {
        is AnalysisUiState.FramesReady -> analysisState.extraction
        is AnalysisUiState.AnalyzingWithAi -> analysisState.extraction
        is AnalysisUiState.ResultReady -> analysisState.extraction
        is AnalysisUiState.Error -> analysisState.extraction
        else -> null
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                VideoSourcePickerCard(
                    isLiveCameraActive = showLiveCamera,
                    onToggleLiveCamera = { showLiveCamera = !showLiveCamera },
                    onPickGalleryVideo = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onPickDocumentVideo = {
                        documentVideoPicker.launch(arrayOf("video/*"))
                    },
                    onRecordCameraVideo = { launchCameraVideoCapture() },
                    onPickCctvFrame = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }

            item {
                AnimatedVisibility(visible = showLiveCamera) {
                    CameraPreview(
                        onFramesCaptured = { frames, label, res ->
                            showLiveCamera = false
                            onLoadLiveFrames(frames, label, res)
                        },
                        onClose = { showLiveCamera = false }
                    )
                }
            }

            if (analysisState is AnalysisUiState.ExtractingFrames) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = analysisState.label,
                                style = MaterialTheme.typography.titleSmall
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            if (currentExtraction != null) {
                item {
                    ExtractedFramesFilmstrip(extraction = currentExtraction)
                }

                item {
                    RoadMetadataInputCard(
                        selectedAfdelingCode = selectedAfdelingCode,
                        selectedBlockCode = selectedBlockCode,
                        roadTypeLabel = roadTypeLabel,
                        onSelectAfdeling = { afd ->
                            selectedAfdelingCode = afd.code
                            val nextBlock = afd.blocks.firstOrNull().orEmpty()
                            selectedBlockCode = nextBlock
                            segmentLocation = SeiRokanCatalog.formatSegmentLabel(afd.displayName, nextBlock)
                            roadName = if (nextBlock.isNotBlank()) "$roadTypeLabel Blok $nextBlock" else roadTypeLabel
                        },
                        onSelectBlock = { afd, block ->
                            selectedBlockCode = block
                            segmentLocation = SeiRokanCatalog.formatSegmentLabel(afd.displayName, block)
                            roadName = "$roadTypeLabel Blok $block"
                        },
                        onSelectRoadType = { preset ->
                            roadTypeLabel = preset
                            roadName = if (selectedBlockCode.isNotBlank()) {
                                "$preset Blok $selectedBlockCode"
                            } else {
                                preset
                            }
                        },
                        roadName = roadName,
                        onRoadNameChange = { roadName = it },
                        segmentLocation = segmentLocation,
                        onSegmentLocationChange = { segmentLocation = it },
                        regionName = regionName,
                        trafficIntensity = trafficIntensity,
                        onTrafficChange = { trafficIntensity = it },
                        fieldNotes = fieldNotes,
                        onFieldNotesChange = { fieldNotes = it },
                        isAnalyzing = analysisState is AnalysisUiState.AnalyzingWithAi,
                        onAnalyzeClick = {
                            onRunAiAnalysis(roadName, segmentLocation, regionName, trafficIntensity, fieldNotes)
                        }
                    )
                }
            }

            if (analysisState is AnalysisUiState.AnalyzingWithAi) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = if (analysisState.isOfflineMode) {
                                        "Menganalisis Frame di Perangkat (Offline)..."
                                    } else {
                                        "AI Memindai Rekaman Jalan..."
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (analysisState.isOfflineMode) {
                                        "Menghitung indeks tekstur jalan kebun & mencocokkan profil curah hujan lokal tanpa internet..."
                                    } else {
                                        "Mencari data curah hujan Google, mengidentifikasi kerusakan, dan menghitung jadwal preventif..."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            if (analysisState is AnalysisUiState.Error) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = analysisState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            if (analysisState is AnalysisUiState.ResultReady) {
                val res = analysisState.inspection
                val condCat = RoadConditionCategory.safeValueOf(res.conditionCategory)

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PciRingGauge(pciScore = res.pciScore.toInt(), sizeDp = 74)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            color = conditionBgColor(condCat),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Kondisi: ${condCat.label}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = conditionColor(condCat),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        if (analysisState.isOfflineResult) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Offline Lapangan",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = res.roadName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "Estimasi IRI: %.1f m/km • %d Frame",
                                            res.iriEstimate,
                                            res.framesAnalyzed
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = res.roadSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.CalendarMonth,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Bulan Tepat: ${res.optimalMaintenanceMonth}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Surface(
                                            color = StatusGood,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Hemat ${res.costSavingsPercent}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Metode: ${res.preventiveMethod} (${res.urgencyWindow})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = res.maintenanceRationale,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            if (res.rainfallSummary.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Curah Hujan Wilayah (${res.regionName.ifBlank { "Indonesia" }})",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = res.rainfallSummary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        if (res.rainfallSourceNote.isNotBlank()) {
                                            Text(
                                                text = "Sumber: ${res.rainfallSourceNote}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }

                            DegradationCurveChart(
                                forecast = res.forecast,
                                optimalMonth = res.optimalMaintenanceMonth
                            )

                            if (analysisState.savedDocumentId != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { onOpenSavedDetail(analysisState.savedDocumentId) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("open_saved_report_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusGood)
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Lihat Laporan Tersimpan")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            roadName = ""
                                            segmentLocation = ""
                                            fieldNotes = ""
                                            onReset()
                                        },
                                        modifier = Modifier.height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Analisis baru")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onSaveAnalysis,
                                    enabled = !analysisState.isSaving,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("save_analysis_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (analysisState.isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Menyimpan...")
                                    } else {
                                        Icon(Icons.Filled.Save, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = when {
                                                isLocalFieldMode || !isOnline -> "Simpan ke Database Lokal (Room)"
                                                else -> "Simpan Laporan & Jadwal Preventif"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (res.damages.isNotEmpty()) {
                    item {
                        Text(
                            text = "Kerusakan Teridentifikasi (${res.damages.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(res.damages) { dmg ->
                        DamageBreakdownCard(
                            type = dmg.type,
                            severity = dmg.severity,
                            affectedPercent = dmg.affectedPercent.toInt(),
                            frameIndex = dmg.frameIndex.toInt(),
                            description = dmg.description,
                            recommendedAction = dmg.recommendedAction
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun VideoSourcePickerCard(
    isLiveCameraActive: Boolean,
    onToggleLiveCamera: () -> Unit,
    onPickGalleryVideo: () -> Unit,
    onPickDocumentVideo: () -> Unit,
    onRecordCameraVideo: () -> Unit,
    onPickCctvFrame: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "1. Sumber Rekaman & Kamera Jalan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Gunakan Kamera Live CameraX untuk inspeksi langsung atau pilih video CCTV/dashcam kebun.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onToggleLiveCamera,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("toggle_live_camera_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLiveCameraActive) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isLiveCameraActive) "Sembunyikan Kamera Live (CameraX)"
                    else "Buka Kamera Live (CameraX)"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPickGalleryVideo,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pick_gallery_video_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galeri Video")
                }
                OutlinedButton(
                    onClick = onRecordCameraVideo,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("record_road_video_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rekam Video")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPickDocumentVideo,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pick_file_video_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("File MP4")
                }
                OutlinedButton(
                    onClick = onPickCctvFrame,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pick_cctv_image_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Foto Jalan")
                }
            }
        }
    }
}

@Composable
private fun ExtractedFramesFilmstrip(extraction: VideoExtractionResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Frame Kunci Diekstrak (${extraction.frames.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${extraction.sourceLabel} • ${extraction.resolution}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Filled.BookmarkAdded,
                    contentDescription = null,
                    tint = StatusGood
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(extraction.frames) { frame ->
                    Box(
                        modifier = Modifier
                            .width(156.dp)
                            .height(104.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            bitmap = frame.bitmap.asImageBitmap(),
                            contentDescription = "Frame jalan #${frame.index}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xBB0F172A))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#${frame.index} • ${frame.formattedTimestamp}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoadMetadataInputCard(
    selectedAfdelingCode: String,
    selectedBlockCode: String,
    roadTypeLabel: String,
    onSelectAfdeling: (com.example.data.model.SeiRokanAfdeling) -> Unit,
    onSelectBlock: (com.example.data.model.SeiRokanAfdeling, String) -> Unit,
    onSelectRoadType: (String) -> Unit,
    roadName: String,
    onRoadNameChange: (String) -> Unit,
    segmentLocation: String,
    onSegmentLocationChange: (String) -> Unit,
    regionName: String,
    trafficIntensity: TrafficIntensity,
    onTrafficChange: (TrafficIntensity) -> Unit,
    fieldNotes: String,
    onFieldNotesChange: (String) -> Unit,
    isAnalyzing: Boolean,
    onAnalyzeClick: () -> Unit
) {
    val activeAfdeling = remember(selectedAfdelingCode) {
        SeiRokanCatalog.afdelings.firstOrNull { it.code == selectedAfdelingCode }
            ?: SeiRokanCatalog.afdelings.first()
    }
    var blockQuery by rememberSaveable(selectedAfdelingCode) { mutableStateOf("") }
    var showBlockGridSheet by remember { mutableStateOf(false) }

    val filteredBlocks = remember(activeAfdeling, blockQuery) {
        val q = blockQuery.trim()
        if (q.isBlank()) activeAfdeling.blocks
        else activeAfdeling.blocks.filter { it.contains(q, ignoreCase = true) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. Lokasi Kebun Sei Rokan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = regionName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Pilih Afdeling (${SeiRokanCatalog.afdelings.size} Afdeling Kebun Sei Rokan)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SeiRokanCatalog.afdelings, key = { it.code }) { afd ->
                        FilterChip(
                            selected = afd.code == activeAfdeling.code,
                            onClick = { onSelectAfdeling(afd) },
                            label = { Text("${afd.displayName} (${afd.blocks.size})") },
                            modifier = Modifier.testTag("afdeling_chip_${afd.code}")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pilih Blok ${activeAfdeling.displayName} (${filteredBlocks.size}/${activeAfdeling.blocks.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { showBlockGridSheet = true },
                        modifier = Modifier.testTag("open_block_grid_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Semua Blok")
                    }
                }

                OutlinedTextField(
                    value = blockQuery,
                    onValueChange = { blockQuery = it },
                    label = { Text("Cari Kode Blok ${activeAfdeling.displayName}") },
                    placeholder = { Text("Ketik blok, misal: ${activeAfdeling.blocks.firstOrNull().orEmpty()}") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("block_search_input")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredBlocks, key = { "${activeAfdeling.code}_$it" }) { block ->
                        FilterChip(
                            selected = block == selectedBlockCode,
                            onClick = { onSelectBlock(activeAfdeling, block) },
                            label = { Text(block) },
                            modifier = Modifier.testTag("block_chip_$block")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tipe Ruas Jalan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SeiRokanCatalog.roadTypePresets) { preset ->
                        FilterChip(
                            selected = roadTypeLabel == preset,
                            onClick = { onSelectRoadType(preset) },
                            label = { Text(preset) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = roadName,
                onValueChange = onRoadNameChange,
                label = { Text("Nama Ruas / Blok Kebun *") },
                placeholder = { Text("Misal: Jalan Angkut TBS Blok ${selectedBlockCode.ifBlank { "I4" }}") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("road_name_input")
            )

            OutlinedTextField(
                value = segmentLocation,
                onValueChange = onSegmentLocationChange,
                label = { Text("Lokasi Afdeling & Blok / KM") },
                placeholder = { Text("Kebun Sei Rokan • ${activeAfdeling.displayName} • Blok $selectedBlockCode") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("segment_location_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Intensitas Beban Truk TBS / Lalu Lintas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrafficIntensity.entries.forEach { level ->
                        FilterChip(
                            selected = trafficIntensity == level,
                            onClick = { onTrafficChange(level) },
                            label = { Text(level.label) },
                            modifier = Modifier.testTag("traffic_chip_${level.name}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = fieldNotes,
                onValueChange = onFieldNotesChange,
                label = { Text("Catatan Kondisi Drainase / Cuaca (Opsional)") },
                placeholder = { Text("Misal: Jalan tanah laterit sering dilewati truk TBS saat hujan") },
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_notes_input")
            )

            Button(
                onClick = onAnalyzeClick,
                enabled = !isAnalyzing && roadName.trim().length >= 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("run_ai_analysis_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isAnalyzing) "Memproses Analisis & Curah Hujan..." else "Analisis Kerusakan & Jadwal Hujan",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    if (showBlockGridSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBlockGridSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${SeiRokanCatalog.ESTATE_NAME} • ${activeAfdeling.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = blockQuery,
                    onValueChange = { blockQuery = it },
                    label = { Text("Filter Kode Blok") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 84.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gridItems(filteredBlocks, key = { it }) { block ->
                        FilterChip(
                            selected = block == selectedBlockCode,
                            onClick = {
                                onSelectBlock(activeAfdeling, block)
                                showBlockGridSheet = false
                            },
                            label = { Text(block) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
