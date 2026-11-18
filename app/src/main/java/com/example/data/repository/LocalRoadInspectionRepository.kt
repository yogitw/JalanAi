package com.example.data.repository

import com.example.data.local.RoadInspectionDao
import com.example.data.local.toEntity
import com.example.data.model.MaintenanceStatus
import com.example.data.model.RoadInspection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LocalRoadInspectionRepository(
    private val dao: RoadInspectionDao
) {
    val allInspections: Flow<List<RoadInspection>> =
        dao.observeAllInspections().map { entities ->
            entities.map { it.toDomain() }
        }

    val unsyncedInspections: Flow<List<RoadInspection>> =
        dao.observeUnsyncedInspections().map { entities ->
            entities.map { it.toDomain() }
        }

    val unsyncedCount: Flow<Int> = dao.observeUnsyncedCount()

    fun observeInspectionById(id: String): Flow<RoadInspection?> =
        dao.observeInspectionById(id).map { it?.toDomain() }

    suspend fun saveInspection(
        inspection: RoadInspection,
        userId: String = "field_local",
        isSyncedToCloud: Boolean = false
    ): Result<String> {
        return try {
            require(inspection.roadName.trim().length in 2..120) {
                "Nama ruas jalan harus antara 2 hingga 120 karakter."
            }
            require(inspection.optimalMaintenanceMonth.trim().length in 3..40) {
                "Bulan pemeliharaan optimal tidak valid."
            }
            val localId = inspection.id.ifBlank {
                "local_${UUID.randomUUID().toString().take(12)}"
            }
            val entity = inspection.toEntity(
                overrideId = localId,
                overrideUserId = userId,
                isSyncedToCloud = isSyncedToCloud
            )
            dao.insertInspection(entity)
            Result.success(localId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMaintenanceSchedule(
        inspectionId: String,
        status: MaintenanceStatus,
        targetMonth: String,
        notes: String
    ): Result<Unit> {
        return try {
            require(inspectionId.isNotBlank()) { "ID inspeksi tidak boleh kosong." }
            val cleanMonth = targetMonth.trim()
            require(cleanMonth.length in 3..40) { "Bulan target harus 3–40 karakter." }
            val cleanNotes = notes.trim().take(300)
            dao.updateSchedule(
                id = inspectionId,
                status = status.name,
                targetMonth = cleanMonth,
                notes = cleanNotes,
                updatedAtMillis = System.currentTimeMillis()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteInspection(inspectionId: String): Result<Unit> {
        return try {
            require(inspectionId.isNotBlank()) { "ID inspeksi tidak boleh kosong." }
            dao.deleteById(inspectionId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncPendingToCloud(
        userId: String,
        cloudRepository: RoadInspectionRepository
    ): Result<Int> {
        return try {
            val pending = dao.getUnsyncedInspections()
            var syncedCount = 0
            for (entity in pending) {
                val uploadResult = cloudRepository.saveInspection(userId, entity.toDomain())
                if (uploadResult.isSuccess) {
                    dao.markSynced(entity.id, userId)
                    syncedCount++
                }
            }
            Result.success(syncedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
