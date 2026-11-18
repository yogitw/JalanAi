package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoadInspectionDao {

    @Query("SELECT * FROM road_inspections ORDER BY createdAtMillis DESC")
    fun observeAllInspections(): Flow<List<RoadInspectionEntity>>

    @Query("SELECT * FROM road_inspections WHERE id = :id LIMIT 1")
    fun observeInspectionById(id: String): Flow<RoadInspectionEntity?>

    @Query("SELECT * FROM road_inspections WHERE isSyncedToCloud = 0 ORDER BY createdAtMillis DESC")
    fun observeUnsyncedInspections(): Flow<List<RoadInspectionEntity>>

    @Query("SELECT * FROM road_inspections WHERE isSyncedToCloud = 0 ORDER BY createdAtMillis DESC")
    suspend fun getUnsyncedInspections(): List<RoadInspectionEntity>

    @Query("SELECT COUNT(*) FROM road_inspections WHERE isSyncedToCloud = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspection(entity: RoadInspectionEntity)

    @Query(
        """
        UPDATE road_inspections
        SET maintenanceStatus = :status,
            optimalMaintenanceMonth = :targetMonth,
            maintenanceNotes = :notes,
            updatedAtMillis = :updatedAtMillis,
            isSyncedToCloud = 0
        WHERE id = :id
        """
    )
    suspend fun updateSchedule(
        id: String,
        status: String,
        targetMonth: String,
        notes: String,
        updatedAtMillis: Long
    )

    @Query("UPDATE road_inspections SET isSyncedToCloud = 1, userId = :userId WHERE id = :id")
    suspend fun markSynced(id: String, userId: String)

    @Query("DELETE FROM road_inspections WHERE id = :id")
    suspend fun deleteById(id: String)
}
