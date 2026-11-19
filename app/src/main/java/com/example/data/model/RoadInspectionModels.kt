package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TrafficIntensity(val label: String) {
    RENDAH("Rendah"),
    SEDANG("Sedang"),
    TINGGI("Tinggi");

    companion object {
        fun safeValueOf(raw: String?): TrafficIntensity =
            entries.firstOrNull { it.name == raw } ?: SEDANG
    }
}

enum class RoadConditionCategory(val label: String) {
    BAIK("Baik"),
    SEDANG("Sedang"),
    RUSAK_RINGAN("Rusak Ringan"),
    RUSAK_BERAT("Rusak Berat");

    companion object {
        fun safeValueOf(raw: String?): RoadConditionCategory =
            entries.firstOrNull { it.name == raw } ?: SEDANG
    }
}

enum class MaintenanceStatus(val label: String) {
    TERJADWAL("Terjadwal"),
    PROSES("Dalam Proses"),
    SELESAI("Selesai");

    companion object {
        fun safeValueOf(raw: String?): MaintenanceStatus =
            entries.firstOrNull { it.name == raw } ?: TERJADWAL
    }
}

enum class DamageSeverity(val label: String) {
    RINGAN("Ringan"),
    SEDANG("Sedang"),
    BERAT("Berat");

    companion object {
        fun safeValueOf(raw: String?): DamageSeverity =
            entries.firstOrNull { it.name == raw } ?: SEDANG
    }
}

@Keep
@IgnoreExtraProperties
data class RoadDamageItem(
    val type: String = "",
    val severity: String = "SEDANG",
    val affectedPercent: Long = 0L,
    val frameIndex: Long = 0L,
    val description: String = "",
    val recommendedAction: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "type" to type.take(80),
        "severity" to DamageSeverity.safeValueOf(severity).name,
        "affectedPercent" to affectedPercent.coerceIn(0L, 100L),
        "frameIndex" to frameIndex.coerceAtLeast(0L),
        "description" to description.take(240),
        "recommendedAction" to recommendedAction.take(240)
    )
}

@Keep
@IgnoreExtraProperties
data class DegradationPoint(
    val monthOffset: Long = 0L,
    val monthLabel: String = "",
    val predictedPci: Long = 0L,
    val rainfallMm: Long = 0L,
    val riskLevel: String = "RENDAH"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "monthOffset" to monthOffset.coerceIn(0L, 12L),
        "monthLabel" to monthLabel.take(24),
        "predictedPci" to predictedPci.coerceIn(0L, 100L),
        "rainfallMm" to rainfallMm.coerceIn(0L, 1500L),
        "riskLevel" to riskLevel.take(20)
    )
}

@Keep
@IgnoreExtraProperties
data class RoadInspection(
    @DocumentId val id: String = "",
    val userId: String = "",
    val estateId: String = SeiRokanCatalog.ESTATE_ID,
    val inspectorName: String = "",
    val roadName: String = "",
    val segmentLocation: String = "",
    val regionName: String = "",
    val trafficIntensity: String = TrafficIntensity.SEDANG.name,
    val videoSourceLabel: String = "",
    val framesAnalyzed: Long = 0L,
    val roadSummary: String = "",
    val pciScore: Long = 0L,
    val iriEstimate: Double = 0.0,
    val conditionCategory: String = RoadConditionCategory.SEDANG.name,
    val optimalMaintenanceMonth: String = "",
    val urgencyWindow: String = "",
    val preventiveMethod: String = "",
    val maintenanceRationale: String = "",
    val rainfallSummary: String = "",
    val rainfallSourceNote: String = "",
    val costSavingsPercent: Long = 0L,
    val maintenanceStatus: String = MaintenanceStatus.TERJADWAL.name,
    val maintenanceNotes: String = "",
    val damages: List<RoadDamageItem> = emptyList(),
    val forecast: List<DegradationPoint> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String, authorName: String = inspectorName): Map<String, Any> = mapOf(
        "userId" to uid,
        "estateId" to estateId.ifBlank { SeiRokanCatalog.ESTATE_ID },
        "inspectorName" to authorName.trim().ifBlank { "Petugas Kebun" }.take(60),
        "roadName" to roadName.trim().take(120),
        "segmentLocation" to segmentLocation.trim().take(120),
        "regionName" to regionName.trim().take(100),
        "trafficIntensity" to TrafficIntensity.safeValueOf(trafficIntensity).name,
        "videoSourceLabel" to videoSourceLabel.trim().take(120),
        "framesAnalyzed" to framesAnalyzed.coerceIn(1L, 12L),
        "roadSummary" to roadSummary.trim().take(1000),
        "pciScore" to pciScore.coerceIn(0L, 100L),
        "iriEstimate" to iriEstimate.coerceIn(0.0, 25.0),
        "conditionCategory" to RoadConditionCategory.safeValueOf(conditionCategory).name,
        "optimalMaintenanceMonth" to optimalMaintenanceMonth.trim().take(40),
        "urgencyWindow" to urgencyWindow.trim().take(60),
        "preventiveMethod" to preventiveMethod.trim().take(200),
        "maintenanceRationale" to maintenanceRationale.trim().take(1000),
        "rainfallSummary" to rainfallSummary.trim().take(600),
        "rainfallSourceNote" to rainfallSourceNote.trim().take(300),
        "costSavingsPercent" to costSavingsPercent.coerceIn(0L, 100L),
        "maintenanceStatus" to MaintenanceStatus.safeValueOf(maintenanceStatus).name,
        "maintenanceNotes" to maintenanceNotes.trim().take(300),
        "damages" to damages.take(10).map { it.toMap() },
        "forecast" to forecast.take(13).map { it.toMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
