package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.MaintenanceStatus
import com.example.data.model.RoadInspection
import com.example.data.model.SeiRokanCatalog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class RoadInspectionRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireSignedInUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Sesi akun berubah; operasi dibatalkan." }
        }
        return uid
    }

    fun observeSharedInspections(): Flow<List<RoadInspection>> =
        db.collection("inspections")
            .whereEqualTo("estateId", SeiRokanCatalog.ESTATE_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RoadInspection::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { item ->
                    item.createdAt?.toDate()?.time ?: 0L
                }
            }

    fun observeInspection(inspectionId: String): Flow<RoadInspection?> =
        db.collection("inspections")
            .document(inspectionId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        RoadInspection::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }

    suspend fun saveInspection(
        userId: String,
        inspectorName: String,
        inspection: RoadInspection
    ): Result<String> {
        var docPath = "inspections"
        return try {
            val uid = requireSignedInUserId(userId)
            require(inspection.roadName.trim().length in 2..120) {
                "Nama ruas jalan harus antara 2 hingga 120 karakter."
            }
            require(inspection.optimalMaintenanceMonth.trim().length in 3..40) {
                "Bulan pemeliharaan optimal tidak valid."
            }
            val docRef = db.collection("inspections").document()
            docPath = docRef.path
            docRef.set(inspection.toCreateMap(uid = uid, authorName = inspectorName)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMaintenanceSchedule(
        userId: String,
        inspectionId: String,
        status: MaintenanceStatus,
        targetMonth: String,
        notes: String
    ): Result<Unit> {
        val docPath = "inspections/$inspectionId"
        return try {
            requireSignedInUserId(userId)
            require(inspectionId.isNotBlank()) { "ID inspeksi tidak boleh kosong." }
            val cleanMonth = targetMonth.trim()
            require(cleanMonth.length in 3..40) { "Bulan target harus 3–40 karakter." }
            val cleanNotes = notes.trim().take(300)

            db.collection("inspections")
                .document(inspectionId)
                .update(
                    mapOf(
                        "maintenanceStatus" to status.name,
                        "optimalMaintenanceMonth" to cleanMonth,
                        "maintenanceNotes" to cleanNotes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteInspection(userId: String, inspectionId: String): Result<Unit> {
        val docPath = "inspections/$inspectionId"
        return try {
            requireSignedInUserId(userId)
            require(inspectionId.isNotBlank()) { "ID inspeksi tidak boleh kosong." }
            db.collection("inspections")
                .document(inspectionId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
