package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.OfflineFieldAnalyzer
import com.example.data.ai.RoadAiAnalyzer
import com.example.data.model.MaintenanceStatus
import com.example.data.model.RoadInspection
import com.example.data.model.TrafficIntensity
import com.example.data.repository.LocalRoadInspectionRepository
import com.example.data.repository.RoadInspectionRepository
import com.example.data.video.ExtractedFrame
import com.example.data.video.VideoExtractionResult
import com.example.data.video.VideoFrameExtractor
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val TAG = "JalanAiVM"

sealed interface InspectionsUiState {
    data object Loading : InspectionsUiState
    data class Success(val items: List<RoadInspection>) : InspectionsUiState
    data class Error(val message: String) : InspectionsUiState
}

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data class ExtractingFrames(val label: String) : AnalysisUiState
    data class FramesReady(val extraction: VideoExtractionResult) : AnalysisUiState
    data class AnalyzingWithAi(
        val extraction: VideoExtractionResult,
        val isOfflineMode: Boolean = false
    ) : AnalysisUiState
    data class ResultReady(
        val extraction: VideoExtractionResult?,
        val inspection: RoadInspection,
        val isOfflineResult: Boolean = false,
        val isSaving: Boolean = false,
        val savedDocumentId: String? = null
    ) : AnalysisUiState
    data class Error(
        val message: String,
        val extraction: VideoExtractionResult? = null
    ) : AnalysisUiState
}

class JalanAiViewModel(
    private val localRepository: LocalRoadInspectionRepository,
    private val cloudRepository: RoadInspectionRepository?,
    private val aiAnalyzer: RoadAiAnalyzer,
    private val offlineAnalyzer: OfflineFieldAnalyzer,
    private val frameExtractor: VideoFrameExtractor,
    private val currentUserId: String?,
    isOnlineFlow: Flow<Boolean>
) : ViewModel() {

    val isLocalFieldMode: Boolean = currentUserId.isNullOrBlank() || cloudRepository == null

    val isOnline: StateFlow<Boolean> = isOnlineFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = true
    )

    val unsyncedLocalCount: StateFlow<Int> = localRepository.unsyncedCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = 0
    )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val inspectionsState: StateFlow<InspectionsUiState> =
        if (!isLocalFieldMode && cloudRepository != null && !currentUserId.isNullOrBlank()) {
            combine(
                localRepository.unsyncedInspections,
                cloudRepository.observeInspections(currentUserId)
            ) { localUnsynced, cloudItems ->
                val cloudIds = cloudItems.mapTo(HashSet()) { it.id }
                val uniqueLocal = localUnsynced.filterNot { it.id in cloudIds }
                InspectionsUiState.Success(uniqueLocal + cloudItems) as InspectionsUiState
            }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing cloud inspections", rootError)
                    emit(
                        InspectionsUiState.Error(
                            rootError.localizedMessage ?: "Gagal memuat riwayat inspeksi jalan."
                        )
                    )
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = InspectionsUiState.Loading
                )
        } else {
            localRepository.allInspections
                .map<List<RoadInspection>, InspectionsUiState> { InspectionsUiState.Success(it) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Error observing Room inspections", error)
                    emit(
                        InspectionsUiState.Error(
                            error.localizedMessage ?: "Gagal memuat riwayat inspeksi lokal."
                        )
                    )
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = InspectionsUiState.Loading
                )
        }

    private val _analysisState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysisState: StateFlow<AnalysisUiState> = _analysisState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun loadVideoFromUri(uri: Uri) {
        viewModelScope.launch {
            _analysisState.value = AnalysisUiState.ExtractingFrames("Mengekstrak frame kunci dari video pengawasan...")
            try {
                val result = frameExtractor.extractFromVideoUri(uri, targetFrameCount = 5)
                _analysisState.value = AnalysisUiState.FramesReady(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract video frames", e)
                _analysisState.value = AnalysisUiState.Error(
                    e.localizedMessage ?: "Gagal membaca frame dari video yang dipilih."
                )
            }
        }
    }

    fun loadImageFrameFromUri(uri: Uri) {
        viewModelScope.launch {
            _analysisState.value = AnalysisUiState.ExtractingFrames("Memproses frame kamera pengawasan...")
            try {
                val result = frameExtractor.extractFromImageUri(uri)
                _analysisState.value = AnalysisUiState.FramesReady(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode CCTV image", e)
                _analysisState.value = AnalysisUiState.Error(
                    e.localizedMessage ?: "Gagal memproses gambar pengawasan jalan."
                )
            }
        }
    }

    fun loadLiveCapturedFrames(
        frames: List<ExtractedFrame>,
        sourceLabel: String,
        resolution: String
    ) {
        if (frames.isEmpty()) return
        val result = VideoExtractionResult(
            sourceLabel = sourceLabel,
            durationMs = (frames.size * 500L),
            resolution = resolution,
            frames = frames
        )
        _analysisState.value = AnalysisUiState.FramesReady(result)
    }

    fun runAiAnalysis(
        roadName: String,
        segmentLocation: String,
        regionName: String,
        trafficIntensity: TrafficIntensity,
        fieldNotes: String
    ) {
        val extraction = when (val current = _analysisState.value) {
            is AnalysisUiState.FramesReady -> current.extraction
            is AnalysisUiState.Error -> current.extraction
            is AnalysisUiState.ResultReady -> current.extraction
            else -> null
        } ?: return

        val trimmedRoad = roadName.trim()
        if (trimmedRoad.length < 2) {
            _actionMessage.value = "Masukkan nama ruas jalan minimal 2 karakter."
            return
        }

        val onlineNow = isOnline.value
        val ownerId = currentUserId ?: "field_local"

        viewModelScope.launch {
            _analysisState.value = AnalysisUiState.AnalyzingWithAi(
                extraction = extraction,
                isOfflineMode = !onlineNow
            )

            if (!onlineNow) {
                runOfflineAnalysisInternal(
                    ownerId = ownerId,
                    roadName = trimmedRoad,
                    segmentLocation = segmentLocation.trim(),
                    regionName = regionName.trim(),
                    trafficIntensity = trafficIntensity,
                    fieldNotes = fieldNotes.trim(),
                    extraction = extraction,
                    notice = "Mode Offline Lapangan: analisis kondisi jalan & jadwal hujan diproses langsung di perangkat."
                )
                return@launch
            }

            try {
                val inspection = aiAnalyzer.analyzeRoadFootage(
                    userId = ownerId,
                    roadName = trimmedRoad,
                    segmentLocation = segmentLocation.trim(),
                    regionName = regionName.trim(),
                    trafficIntensity = trafficIntensity,
                    videoSourceLabel = extraction.sourceLabel,
                    fieldNotes = fieldNotes.trim(),
                    frames = extraction.frames
                )
                _analysisState.value = AnalysisUiState.ResultReady(
                    extraction = extraction,
                    inspection = inspection,
                    isOfflineResult = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isNetworkFailure(e)) {
                    Log.w(TAG, "Cloud AI unreachable, switching to offline plantation analyzer", e)
                    runOfflineAnalysisInternal(
                        ownerId = ownerId,
                        roadName = trimmedRoad,
                        segmentLocation = segmentLocation.trim(),
                        regionName = regionName.trim(),
                        trafficIntensity = trafficIntensity,
                        fieldNotes = fieldNotes.trim(),
                        extraction = extraction,
                        notice = "Koneksi internet terputus; beralih otomatis ke analisis offline lapangan."
                    )
                } else {
                    Log.e(TAG, "AI analysis failed", e)
                    _analysisState.value = AnalysisUiState.Error(
                        message = e.localizedMessage ?: "Analisis AI gagal dijalankan.",
                        extraction = extraction
                    )
                }
            }
        }
    }

    private suspend fun runOfflineAnalysisInternal(
        ownerId: String,
        roadName: String,
        segmentLocation: String,
        regionName: String,
        trafficIntensity: TrafficIntensity,
        fieldNotes: String,
        extraction: VideoExtractionResult,
        notice: String
    ) {
        try {
            val offlineInspection = offlineAnalyzer.analyzeOffline(
                userId = ownerId,
                roadName = roadName,
                segmentLocation = segmentLocation,
                regionName = regionName,
                trafficIntensity = trafficIntensity,
                videoSourceLabel = extraction.sourceLabel,
                fieldNotes = fieldNotes,
                frames = extraction.frames
            )
            _analysisState.value = AnalysisUiState.ResultReady(
                extraction = extraction,
                inspection = offlineInspection,
                isOfflineResult = true
            )
            _actionMessage.value = notice
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Offline field analysis failed", e)
            _analysisState.value = AnalysisUiState.Error(
                message = e.localizedMessage ?: "Gagal menjalankan analisis offline.",
                extraction = extraction
            )
        }
    }

    private fun isNetworkFailure(throwable: Throwable): Boolean {
        val chain = generateSequence(throwable) { it.cause }
        if (chain.any {
                it is UnknownHostException ||
                    it is ConnectException ||
                    it is SocketTimeoutException
            }
        ) {
            return true
        }
        val msg = chain.mapNotNull { it.message?.lowercase() }.joinToString(" ")
        return msg.contains("unable to resolve host") ||
            msg.contains("failed to connect") ||
            msg.contains("network") ||
            msg.contains("offline") ||
            msg.contains("batas waktu")
    }

    fun saveCurrentAnalysis() {
        val current = _analysisState.value as? AnalysisUiState.ResultReady ?: return
        if (current.isSaving || current.savedDocumentId != null) return

        viewModelScope.launch {
            _analysisState.value = current.copy(isSaving = true)

            if (isLocalFieldMode || cloudRepository == null || currentUserId.isNullOrBlank()) {
                val result = localRepository.saveInspection(
                    inspection = current.inspection,
                    userId = "field_local",
                    isSyncedToCloud = false
                )
                result.fold(
                    onSuccess = { localId ->
                        _analysisState.value = current.copy(isSaving = false, savedDocumentId = localId)
                        _actionMessage.value = "Laporan tersimpan permanen di database lokal (Room)."
                    },
                    onFailure = { error ->
                        _analysisState.value = current.copy(isSaving = false)
                        _actionMessage.value = error.localizedMessage ?: "Gagal menyimpan ke database lokal."
                    }
                )
                return@launch
            }

            if (!isOnline.value) {
                // Save to local Room when field user is signed in but currently offline
                val result = localRepository.saveInspection(
                    inspection = current.inspection,
                    userId = currentUserId,
                    isSyncedToCloud = false
                )
                result.fold(
                    onSuccess = { localId ->
                        _analysisState.value = current.copy(isSaving = false, savedDocumentId = localId)
                        _actionMessage.value = "Offline: disimpan ke Room lokal & siap disinkronkan saat online."
                    },
                    onFailure = { error ->
                        _analysisState.value = current.copy(isSaving = false)
                        _actionMessage.value = error.localizedMessage ?: "Gagal menyimpan laporan lokal."
                    }
                )
                return@launch
            }

            val result = cloudRepository.saveInspection(currentUserId, current.inspection)
            result.fold(
                onSuccess = { docId ->
                    _analysisState.value = current.copy(isSaving = false, savedDocumentId = docId)
                    _actionMessage.value = "Laporan & jadwal pemeliharaan tersimpan ke Cloud."
                },
                onFailure = { error ->
                    _analysisState.value = current.copy(isSaving = false)
                    _actionMessage.value = error.localizedMessage ?: "Gagal menyimpan laporan."
                }
            )
        }
    }

    fun syncLocalInspectionsToCloud() {
        val uid = currentUserId ?: return
        val cloudRepo = cloudRepository ?: return
        if (_isSyncing.value) return
        if (!isOnline.value) {
            _actionMessage.value = "Tidak ada koneksi internet untuk sinkronisasi ke Cloud."
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            val result = localRepository.syncPendingToCloud(uid, cloudRepo)
            _isSyncing.value = false
            result.fold(
                onSuccess = { count ->
                    _actionMessage.value = if (count > 0) {
                        "$count laporan inspeksi lapangan berhasil disinkronkan ke Cloud."
                    } else {
                        "Semua laporan lokal sudah tersinkron."
                    }
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Gagal menyinkronkan data lokal."
                }
            )
        }
    }

    fun resetAnalysis() {
        _analysisState.value = AnalysisUiState.Idle
    }

    fun updateSchedule(
        inspectionId: String,
        status: MaintenanceStatus,
        targetMonth: String,
        notes: String
    ) {
        viewModelScope.launch {
            if (isLocalFieldMode || cloudRepository == null || currentUserId.isNullOrBlank() || inspectionId.startsWith("local_")) {
                val result = localRepository.updateMaintenanceSchedule(
                    inspectionId = inspectionId,
                    status = status,
                    targetMonth = targetMonth,
                    notes = notes
                )
                result.fold(
                    onSuccess = {
                        _actionMessage.value = "Jadwal pemeliharaan lokal (Room) diperbarui."
                    },
                    onFailure = { error ->
                        _actionMessage.value = error.localizedMessage ?: "Gagal memperbarui jadwal lokal."
                    }
                )
                return@launch
            }

            val result = cloudRepository.updateMaintenanceSchedule(
                userId = currentUserId,
                inspectionId = inspectionId,
                status = status,
                targetMonth = targetMonth,
                notes = notes
            )
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Jadwal pemeliharaan diperbarui."
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Gagal memperbarui jadwal."
                }
            )
        }
    }

    fun deleteInspection(inspectionId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            if (isLocalFieldMode || cloudRepository == null || currentUserId.isNullOrBlank() || inspectionId.startsWith("local_")) {
                val result = localRepository.deleteInspection(inspectionId)
                result.fold(
                    onSuccess = {
                        _actionMessage.value = "Laporan inspeksi lokal dihapus."
                        onDeleted()
                    },
                    onFailure = { error ->
                        _actionMessage.value = error.localizedMessage ?: "Gagal menghapus laporan lokal."
                    }
                )
                return@launch
            }

            val result = cloudRepository.deleteInspection(currentUserId, inspectionId)
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Laporan inspeksi dihapus."
                    onDeleted()
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Gagal menghapus laporan."
                }
            )
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
