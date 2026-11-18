package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DegradationPoint
import com.example.data.model.RoadDamageItem
import com.example.data.model.RoadInspection
import com.google.firebase.Timestamp
import java.util.Date
import java.util.UUID

@Entity(tableName = "road_inspections")
data class RoadInspectionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val roadName: String,
    val segmentLocation: String,
    val regionName: String,
    val trafficIntensity: String,
    val videoSourceLabel: String,
    val framesAnalyzed: Long,
    val roadSummary: String,
    val pciScore: Long,
    val iriEstimate: Double,
    val conditionCategory: String,
    val optimalMaintenanceMonth: String,
    val urgencyWindow: String,
    val preventiveMethod: String,
    val maintenanceRationale: String,
    val rainfallSummary: String,
    val rainfallSourceNote: String,
    val costSavingsPercent: Long,
    val maintenanceStatus: String,
    val maintenanceNotes: String,
    val damages: List<RoadDamageItem>,
    val forecast: List<DegradationPoint>,
    val isSyncedToCloud: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(): RoadInspection = RoadInspection(
        id = id,
        userId = userId,
        roadName = roadName,
        segmentLocation = segmentLocation,
        regionName = regionName,
        trafficIntensity = trafficIntensity,
        videoSourceLabel = videoSourceLabel,
        framesAnalyzed = framesAnalyzed,
        roadSummary = roadSummary,
        pciScore = pciScore,
        iriEstimate = iriEstimate,
        conditionCategory = conditionCategory,
        optimalMaintenanceMonth = optimalMaintenanceMonth,
        urgencyWindow = urgencyWindow,
        preventiveMethod = preventiveMethod,
        maintenanceRationale = maintenanceRationale,
        rainfallSummary = rainfallSummary,
        rainfallSourceNote = rainfallSourceNote,
        costSavingsPercent = costSavingsPercent,
        maintenanceStatus = maintenanceStatus,
        maintenanceNotes = maintenanceNotes,
        damages = damages,
        forecast = forecast,
        createdAt = Timestamp(Date(createdAtMillis)),
        updatedAt = Timestamp(Date(updatedAtMillis))
    )
}

fun RoadInspection.toEntity(
    overrideId: String = id.ifBlank { "local_${UUID.randomUUID().toString().take(12)}" },
    overrideUserId: String = userId.ifBlank { "field_local" },
    isSyncedToCloud: Boolean = false
): RoadInspectionEntity {
    val now = System.currentTimeMillis()
    return RoadInspectionEntity(
        id = overrideId,
        userId = overrideUserId,
        roadName = roadName.trim().take(120),
        segmentLocation = segmentLocation.trim().take(120),
        regionName = regionName.trim().take(100),
        trafficIntensity = trafficIntensity,
        videoSourceLabel = videoSourceLabel.trim().take(120),
        framesAnalyzed = framesAnalyzed.coerceIn(1L, 12L),
        roadSummary = roadSummary.trim().take(1000),
        pciScore = pciScore.coerceIn(0L, 100L),
        iriEstimate = iriEstimate.coerceIn(0.0, 25.0),
        conditionCategory = conditionCategory,
        optimalMaintenanceMonth = optimalMaintenanceMonth.trim().take(40),
        urgencyWindow = urgencyWindow.trim().take(60),
        preventiveMethod = preventiveMethod.trim().take(200),
        maintenanceRationale = maintenanceRationale.trim().take(1000),
        rainfallSummary = rainfallSummary.trim().take(600),
        rainfallSourceNote = rainfallSourceNote.trim().take(300),
        costSavingsPercent = costSavingsPercent.coerceIn(0L, 100L),
        maintenanceStatus = maintenanceStatus,
        maintenanceNotes = maintenanceNotes.trim().take(300),
        damages = damages.take(10),
        forecast = forecast.take(13),
        isSyncedToCloud = isSyncedToCloud,
        createdAtMillis = createdAt?.toDate()?.time ?: now,
        updatedAtMillis = updatedAt?.toDate()?.time ?: now
    )
}
