package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.OfflineFieldAnalyzer
import com.example.data.local.JalanAiDatabase
import com.example.data.model.MaintenanceStatus
import com.example.data.model.TrafficIntensity
import com.example.data.repository.LocalRoadInspectionRepository
import com.example.data.video.ExtractedFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalRoomFieldTest {

    private lateinit var db: JalanAiDatabase
    private lateinit var repository: LocalRoadInspectionRepository
    private val offlineAnalyzer = OfflineFieldAnalyzer()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JalanAiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LocalRoadInspectionRepository(db.roadInspectionDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun offlineAnalysisAndRoomPersistenceCrud() = runTest {
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(150, 95, 60)) // Laterite plantation haul road tone
            for (x in 20..36) {
                for (y in 20..34) {
                    setPixel(x, y, Color.rgb(35, 30, 28)) // Simulated pothole cavity
                }
            }
        }
        val frame = ExtractedFrame(
            index = 1,
            timestampMs = 500L,
            formattedTimestamp = "00:00",
            bitmap = bitmap
        )

        val inspection = offlineAnalyzer.analyzeOffline(
            userId = "field_local",
            roadName = "Jalan Angkut TBS Afdeling III",
            segmentLocation = "Blok B12 - TPH 04",
            regionName = "Pelalawan, Riau",
            trafficIntensity = TrafficIntensity.TINGGI,
            videoSourceLabel = "Kamera Live",
            fieldNotes = "Ada lubang dan genangan saat hujan",
            frames = listOf(frame)
        )

        assertTrue(inspection.forecast.size == 13)
        assertTrue(inspection.damages.isNotEmpty())
        assertTrue(inspection.optimalMaintenanceMonth.isNotBlank())

        val saveResult = repository.saveInspection(inspection)
        assertTrue(saveResult.isSuccess)
        val savedId = saveResult.getOrThrow()

        val storedList = repository.allInspections.first()
        assertEquals(1, storedList.size)
        val stored = storedList.first()
        assertEquals("Jalan Angkut TBS Afdeling III", stored.roadName)
        assertEquals("Pelalawan, Riau", stored.regionName)
        assertEquals(13, stored.forecast.size)
        assertEquals(1, repository.unsyncedCount.first())

        val updateRes = repository.updateMaintenanceSchedule(
            inspectionId = savedId,
            status = MaintenanceStatus.PROSES,
            targetMonth = "Juli 2027",
            notes = "Grading tim kebun"
        )
        assertTrue(updateRes.isSuccess)

        val updated = repository.observeInspectionById(savedId).first()
        assertNotNull(updated)
        assertEquals(MaintenanceStatus.PROSES.name, updated?.maintenanceStatus)
        assertEquals("Juli 2027", updated?.optimalMaintenanceMonth)
        assertEquals("Grading tim kebun", updated?.maintenanceNotes)

        val deleteRes = repository.deleteInspection(savedId)
        assertTrue(deleteRes.isSuccess)
        assertEquals(0, repository.allInspections.first().size)
    }

    @Test
    fun seiRokanCatalogContainsAllEightAfdelingsAndBlocks() {
        val afdelings = com.example.data.model.SeiRokanCatalog.afdelings
        assertEquals(8, afdelings.size)
        val counts = afdelings.associate { it.displayName to it.blocks.size }
        assertEquals(64, counts["Afdeling I"])
        assertEquals(48, counts["Afdeling II"])
        assertEquals(40, counts["Afdeling III"])
        assertEquals(42, counts["Afdeling IV"])
        assertEquals(43, counts["Afdeling V"])
        assertEquals(46, counts["Afdeling VI"])
        assertEquals(60, counts["Afdeling VII"])
        assertEquals(40, counts["Afdeling VIII"])
        assertTrue(afdelings[0].blocks.containsAll(listOf("I4", "K3A", "P4C", "T5A")))
        assertTrue(afdelings[3].blocks.containsAll(listOf("GG5", "II7", "NN7", "X12")))
        assertTrue(afdelings[7].blocks.containsAll(listOf("A10", "G15A", "G15B", "J6A")))
    }
}
