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
    private val cloudRepository: RoadInspectionRepository,
    private val aiAnalyzer: RoadAiAnalyzer,
    private val offlineAnalyzer: OfflineFieldAnalyzer,
    private val frameExtractor: VideoFrameExtractor,
    private val currentUserId: String,
    private val currentUserName: String,
    isOnlineFlow: Flow<Boolean>
) : ViewModel() {

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
        combine(
            localRepository.unsyncedInspections,
            cloudRepository.observeSharedInspections()
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
                Log.w(TAG, "Error observing shared cloud inspections", rootError)
                emit(
                    InspectionsUiState.Error(
                        rootError.localizedMessage ?: "Gagal memuat data pengawasan jalan dari Cloud."
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = InspectionsUiState.Loading
            )

    private val _analysisState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysisState: StateFlow<AnalysisUiState> = _analysisState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        // Automatically flush any queued offline inspections into the shared Cloud database when online
        viewModelScope.launch {
            combine(isOnline, unsyncedLocalCount) { online, pending -> online to pending }
                .collect { (online, pending) ->
                    if (online && pending > 0 && !_isSyncing.value) {
                        syncPendingSilently()
                    }
                }
        }
    }

    private suspend fun syncPendingSilently() {
        _isSyncing.value = true
        try {
            val result = localRepository.syncPendingToCloud(
                userId = currentUserId,
                inspectorName = currentUserName,
                cloudRepository = cloudRepository
            )
            result.onSuccess { count ->
                if (count > 0) {
                    _actionMessage.value = "$count antrean offline berhasil diunggah ke Cloud Bersama."
                }
            }
        } finally {
            _isSyncing.value = false
        }
    }

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

        viewModelScope.launch {
            _analysisState.value = AnalysisUiState.AnalyzingWithAi(
                extraction = extraction,
                isOfflineMode = !onlineNow
            )

            if (!onlineNow) {
                runOfflineAnalysisInternal(
                    roadName = trimmedRoad,
                    segmentLocation = segmentLocation.trim(),
                    regionName = regionName.trim(),
                    trafficIntensity = trafficIntensity,
                    fieldNotes = fieldNotes.trim(),
                    extraction = extraction,
                    notice = "Sinyal offline: analisis diproses di perangkat dan siap dikirim ke Cloud saat online."
                )
                return@launch
            }

            try {
                val inspection = aiAnalyzer.analyzeRoadFootage(
                    userId = currentUserId,
                    roadName = trimmedRoad,
                    segmentLocation = segmentLocation.trim(),
                    regionName = regionName.trim(),
                    trafficIntensity = trafficIntensity,
                    videoSourceLabel = extraction.sourceLabel,
                    fieldNotes = fieldNotes.trim(),
                    frames = extraction.frames
                ).copy(inspectorName = currentUserName)
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
                        roadName = trimmedRoad,
                        segmentLocation = segmentLocation.trim(),
                        regionName = regionName.trim(),
                        trafficIntensity = trafficIntensity,
                        fieldNotes = fieldNotes.trim(),
                        extraction = extraction,
                        notice = "Koneksi internet terputus; beralih otomatis ke analisis perangkat."
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

    fun runOfflineAnalysis(
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

        viewModelScope.launch {
            _analysisState.value = AnalysisUiState.AnalyzingWithAi(
                extraction = extraction,
                isOfflineMode = true
            )
            runOfflineAnalysisInternal(
                roadName = trimmedRoad,
                segmentLocation = segmentLocation.trim(),
                regionName = regionName.trim(),
                trafficIntensity = trafficIntensity,
                fieldNotes = fieldNotes.trim(),
                extraction = extraction,
                notice = "Analisis selesai — tekan Simpan untuk mengirim ke Cloud Bersama."
            )
        }
    }

    private suspend fun runOfflineAnalysisInternal(
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
                userId = currentUserId,
                roadName = roadName,
                segmentLocation = segmentLocation,
                regionName = regionName,
                trafficIntensity = trafficIntensity,
                videoSourceLabel = extraction.sourceLabel,
                fieldNotes = fieldNotes,
                frames = extraction.frames
            ).copy(inspectorName = currentUserName)
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

            if (!isOnline.value) {
                val result = localRepository.saveInspection(
                    inspection = current.inspection,
                    userId = currentUserId,
                    isSyncedToCloud = false
                )
                result.fold(
                    onSuccess = { localId ->
                        _analysisState.value = current.copy(isSaving = false, savedDocumentId = localId)
                        _actionMessage.value = "Sinyal offline: masuk antrean & otomatis dikirim ke Cloud Bersama saat online."
                    },
                    onFailure = { error ->
                        _analysisState.value = current.copy(isSaving = false)
                        _actionMessage.value = error.localizedMessage ?: "Gagal menyimpan antrean offline."
                    }
                )
                return@launch
            }

            val result = cloudRepository.saveInspection(
                userId = currentUserId,
                inspectorName = currentUserName,
                inspection = current.inspection
            )
            result.fold(
                onSuccess = { docId ->
                    _analysisState.value = current.copy(isSaving = false, savedDocumentId = docId)
                    _actionMessage.value = "Tersimpan ke Cloud Bersama — semua pengguna APK kini dapat melihatnya."
                },
                onFailure = { error ->
                    _analysisState.value = current.copy(isSaving = false)
                    _actionMessage.value = error.localizedMessage ?: "Gagal menyimpan laporan ke Cloud."
                }
            )
        }
    }

    fun syncLocalInspectionsToCloud() {
        if (_isSyncing.value) return
        if (!isOnline.value) {
            _actionMessage.value = "Tidak ada koneksi internet untuk sinkronisasi ke Cloud Bersama."
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            val result = localRepository.syncPendingToCloud(
                userId = currentUserId,
                inspectorName = currentUserName,
                cloudRepository = cloudRepository
            )
            _isSyncing.value = false
            result.fold(
                onSuccess = { count ->
                    _actionMessage.value = if (count > 0) {
                        "$count laporan berhasil diunggah ke Cloud Bersama."
                    } else {
                        "Semua laporan sudah tersimpan di Cloud Bersama."
                    }
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Gagal menyinkronkan antrean ke Cloud."
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
            if (inspectionId.startsWith("local_")) {
                val result = localRepository.updateMaintenanceSchedule(
                    inspectionId = inspectionId,
                    status = status,
                    targetMonth = targetMonth,
                    notes = notes
                )
                result.fold(
                    onSuccess = {
                        _actionMessage.value = "Jadwal antrean offline diperbarui."
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
                    _actionMessage.value = "Jadwal pemeliharaan bersama di Cloud diperbarui."
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Gagal memperbarui jadwal."
                }
            )
        }
    }

    fun deleteInspection(inspectionId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            if (inspectionId.startsWith("local_")) {
                val result = localRepository.deleteInspection(inspectionId)
                result.fold(
                    onSuccess = {
                        _actionMessage.value = "Antrean laporan dihapus."
                        onDeleted()
                    },
                    onFailure = { error ->
                        _actionMessage.value = error.localizedMessage ?: "Gagal menghapus laporan."
                    }
                )
                return@launch
            }

            val result = cloudRepository.deleteInspection(currentUserId, inspectionId)
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Laporan inspeksi dihapus dari Cloud Bersama."
                    onDeleted()
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Hanya pembuat laporan yang dapat menghapus data ini."
                }
            )
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
