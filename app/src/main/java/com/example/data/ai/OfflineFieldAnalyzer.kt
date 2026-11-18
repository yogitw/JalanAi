package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.DamageSeverity
import com.example.data.model.DegradationPoint
import com.example.data.model.MaintenanceStatus
import com.example.data.model.RoadConditionCategory
import com.example.data.model.RoadDamageItem
import com.example.data.model.RoadInspection
import com.example.data.model.TrafficIntensity
import com.example.data.video.ExtractedFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class FrameVisualMetrics(
    val frameIndex: Int,
    val roughnessIndex: Double,
    val darkCavityRatio: Double,
    val puddleGlareRatio: Double,
    val lateriteSoilRatio: Double
)

/**
 * On-device field road condition analyzer for palm oil plantations when operating
 * completely offline without internet connectivity or cloud AI access.
 */
class OfflineFieldAnalyzer {

    suspend fun analyzeOffline(
        userId: String,
        roadName: String,
        segmentLocation: String,
        regionName: String,
        trafficIntensity: TrafficIntensity,
        videoSourceLabel: String,
        fieldNotes: String,
        frames: List<ExtractedFrame>
    ): RoadInspection = withContext(Dispatchers.Default) {
        val frameMetrics = frames.map { frame ->
            computeFrameMetrics(frame.index, frame.bitmap)
        }

        val avgRoughness = frameMetrics.map { it.roughnessIndex }.average().takeIf { !it.isNaN() } ?: 0.25
        val avgDarkCavity = frameMetrics.map { it.darkCavityRatio }.average().takeIf { !it.isNaN() } ?: 0.08
        val avgPuddle = frameMetrics.map { it.puddleGlareRatio }.average().takeIf { !it.isNaN() } ?: 0.05
        val avgLaterite = frameMetrics.map { it.lateriteSoilRatio }.average().takeIf { !it.isNaN() } ?: 0.25

        val notesLower = fieldNotes.lowercase(Locale("id", "ID"))
        val notePenalty = listOf(
            "lubang" to 8,
            "amblas" to 10,
            "genangan" to 7,
            "lumpur" to 9,
            "banjir" to 10,
            "retak" to 6,
            "alur" to 7
        ).sumOf { (keyword, weight) -> if (notesLower.contains(keyword)) weight else 0 }

        val trafficPenalty = when (trafficIntensity) {
            TrafficIntensity.RENDAH -> 0.0
            TrafficIntensity.SEDANG -> 4.0
            TrafficIntensity.TINGGI -> 8.5
        }

        val visualDeduction =
            (avgRoughness * 42.0) +
                (avgDarkCavity * 120.0) +
                (avgPuddle * 65.0) +
                (avgLaterite * 12.0) +
                notePenalty * 0.6 +
                trafficPenalty

        val pciScore = (96.0 - visualDeduction).roundToInt().coerceIn(22, 92).toLong()
        val iriEstimate = ((100.0 - pciScore) / 11.0 + 1.4).coerceIn(1.5, 13.5)

        val conditionCategory = when {
            pciScore >= 78 -> RoadConditionCategory.BAIK
            pciScore >= 58 -> RoadConditionCategory.SEDANG
            pciScore >= 42 -> RoadConditionCategory.RUSAK_RINGAN
            else -> RoadConditionCategory.RUSAK_BERAT
        }

        val cleanRegion = regionName.trim().ifBlank { "Perkebunan Kelapa Sawit" }
        val monthlyRainfallPattern = resolveRegionalRainfallMm(cleanRegion)

        val cal = Calendar.getInstance()
        val localeId = Locale("id", "ID")
        val shortFmt = SimpleDateFormat("MMM yy", localeId)
        val fullFmt = SimpleDateFormat("MMMM yyyy", localeId)

        var runningPci = pciScore.toDouble()
        val baseMonthlyDecay = when (trafficIntensity) {
            TrafficIntensity.RENDAH -> 1.1
            TrafficIntensity.SEDANG -> 1.9
            TrafficIntensity.TINGGI -> 2.8
        }

        val forecast = (0..12).map { offset ->
            val monthCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
            val calMonth = monthCal.get(Calendar.MONTH) // 0..11
            val rainMm = monthlyRainfallPattern[calMonth]
            if (offset > 0) {
                val rainFactor = when {
                    rainMm >= 260 -> 1.75
                    rainMm >= 190 -> 1.35
                    rainMm >= 130 -> 1.05
                    else -> 0.78
                }
                val surfaceFactor = if (avgLaterite > 0.30) 1.2 else 1.0
                runningPci = (runningPci - baseMonthlyDecay * rainFactor * surfaceFactor).coerceAtLeast(15.0)
            }
            val predicted = runningPci.roundToInt().toLong().coerceIn(0L, 100L)
            val risk = when {
                predicted < 48L -> "KRITIS"
                predicted < 68L || rainMm >= 230 -> "WASPADA"
                else -> "RENDAH"
            }
            DegradationPoint(
                monthOffset = offset.toLong(),
                monthLabel = shortFmt.format(monthCal.time),
                predictedPci = predicted,
                rainfallMm = rainMm.toLong(),
                riskLevel = risk
            )
        }

        // Choose optimal dry/transition maintenance month before PCI drops critically
        val candidatePoint = forecast.drop(1)
            .filter { it.predictedPci >= 42L && it.rainfallMm <= 185L }
            .minByOrNull { point ->
                // Prefer dry months when PCI is in preventive zone (50..72)
                val pciUrgencyPenalty = if (point.predictedPci < 52L) 0 else 15
                point.rainfallMm + pciUrgencyPenalty
            }
            ?: forecast.drop(1).minByOrNull { it.rainfallMm }
            ?: forecast.first()

        val optimalCal = (cal.clone() as Calendar).apply {
            add(Calendar.MONTH, candidatePoint.monthOffset.toInt())
        }
        val optimalMonthFull = fullFmt.format(optimalCal.time)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(localeId) else it.toString() }

        val peakRainMonth = forecast.drop(1).maxByOrNull { it.rainfallMm }
        val driestMonth = forecast.drop(1).minByOrNull { it.rainfallMm }

        val damages = buildFieldDamages(
            metrics = frameMetrics,
            avgLaterite = avgLaterite,
            avgPuddle = avgPuddle,
            avgDarkCavity = avgDarkCavity,
            avgRoughness = avgRoughness,
            notesLower = notesLower
        )

        val surfaceTypeLabel = if (avgLaterite > 0.28) {
            "perkerasan tanah laterit / sirtu kebun sawit"
        } else {
            "perkerasan jalan aspal / batu pecah kebun"
        }

        val roadSummary = buildString {
            append("Inspeksi offline lapangan (${frames.size} frame) pada $surfaceTypeLabel ")
            append("menghasilkan estimasi PCI $pciScore/100 (IRI %.1f m/km). ".format(Locale.US, iriEstimate))
            if (avgDarkCavity > 0.09 || avgRoughness > 0.28) {
                append("Terdeteksi variasi tekstur permukaan dan cekungan lokal yang berisiko membesar saat dilalui truk angkut TBS.")
            } else {
                append("Struktur permukaan relatif terjaga namun perlu pemantauan drainase parit tepi.")
            }
        }

        val preventiveMethod = when {
            pciScore < 45L -> "Penimbunan Sirtu/Batu Pecah & Perbaikan Drainase Segera"
            avgLaterite > 0.28 -> "Grading Ulang, Pemadatan Vibro Roller & Normalisasi Parit"
            else -> "Penambalan Lubang (Patching) & Pelapisan Tipis Preventif"
        }

        val urgencyWindow = when {
            pciScore < 45L -> "Darurat Lapangan + Rehabilitasi $optimalMonthFull"
            candidatePoint.monthOffset <= 2L -> "Segera (Jendela Kering Terdekat)"
            else -> "Preventif Terjadwal (${candidatePoint.monthOffset} Bulan Lagi)"
        }

        val costSavings = when (conditionCategory) {
            RoadConditionCategory.BAIK -> 68L
            RoadConditionCategory.SEDANG -> 52L
            RoadConditionCategory.RUSAK_RINGAN -> 38L
            RoadConditionCategory.RUSAK_BERAT -> 20L
        }

        val rainfallSummary = buildString {
            append("Profil iklim lokal $cleanRegion: puncak hujan sekitar ${peakRainMonth?.monthLabel ?: "-"} ")
            append("(${peakRainMonth?.rainfallMm ?: 260} mm/bln) dan bulan lebih kering pada ")
            append("${driestMonth?.monthLabel ?: "-"} (${driestMonth?.rainfallMm ?: 110} mm/bln).")
        }

        val maintenanceRationale = buildString {
            append("Penjadwalan di $optimalMonthFull dipilih berdasarkan proyeksi penurunan PCI ")
            append("(laju truk ${trafficIntensity.label}) serta jendela curah hujan lebih rendah ")
            append("(${candidatePoint.rainfallMm} mm/bln) agar material pemadatan tidak tergerus air.")
        }

        RoadInspection(
            userId = userId,
            roadName = roadName.trim(),
            segmentLocation = segmentLocation.trim(),
            regionName = cleanRegion,
            trafficIntensity = trafficIntensity.name,
            videoSourceLabel = "$videoSourceLabel (Offline)",
            framesAnalyzed = frames.size.toLong().coerceIn(1L, 12L),
            roadSummary = roadSummary,
            pciScore = pciScore,
            iriEstimate = iriEstimate,
            conditionCategory = conditionCategory.name,
            optimalMaintenanceMonth = optimalMonthFull,
            urgencyWindow = urgencyWindow,
            preventiveMethod = preventiveMethod,
            maintenanceRationale = maintenanceRationale,
            rainfallSummary = rainfallSummary,
            rainfallSourceNote = "Analisis Lokal Perangkat + Klimatologi Wilayah (Mode Offline Kebun)",
            costSavingsPercent = costSavings,
            maintenanceStatus = MaintenanceStatus.TERJADWAL.name,
            maintenanceNotes = "",
            damages = damages,
            forecast = forecast
        )
    }

    private fun computeFrameMetrics(frameIndex: Int, bitmap: Bitmap): FrameVisualMetrics {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val stepX = (w / 48).coerceAtLeast(1)
        val stepY = (h / 36).coerceAtLeast(1)

        var samples = 0
        var prevLuma = -1.0
        var edgeSum = 0.0
        var darkCount = 0
        var puddleCount = 0
        var lateriteCount = 0

        // Focus predominantly on lower 70% of frame (road surface region)
        val startY = (h * 0.25).toInt()
        var y = startY
        while (y < h) {
            var x = 0
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luma = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0

                if (prevLuma >= 0.0) {
                    edgeSum += abs(luma - prevLuma)
                }
                prevLuma = luma

                if (luma < 0.22) {
                    darkCount++
                }
                val maxCh = maxOf(r, g, b)
                val minCh = minOf(r, g, b)
                val chroma = maxCh - minCh
                if (luma > 0.72 && chroma < 24 && y > h * 0.45) {
                    puddleCount++
                }
                if (r > g + 14 && g >= b && r in 85..225) {
                    lateriteCount++
                }

                samples++
                x += stepX
            }
            prevLuma = -1.0
            y += stepY
        }

        val count = samples.coerceAtLeast(1).toDouble()
        return FrameVisualMetrics(
            frameIndex = frameIndex,
            roughnessIndex = (edgeSum / count).coerceIn(0.0, 1.0),
            darkCavityRatio = (darkCount / count).coerceIn(0.0, 1.0),
            puddleGlareRatio = (puddleCount / count).coerceIn(0.0, 1.0),
            lateriteSoilRatio = (lateriteCount / count).coerceIn(0.0, 1.0)
        )
    }

    private fun buildFieldDamages(
        metrics: List<FrameVisualMetrics>,
        avgLaterite: Double,
        avgPuddle: Double,
        avgDarkCavity: Double,
        avgRoughness: Double,
        notesLower: String
    ): List<RoadDamageItem> {
        val items = mutableListOf<RoadDamageItem>()
        val worstDarkFrame = metrics.maxByOrNull { it.darkCavityRatio }?.frameIndex ?: 1
        val worstRoughFrame = metrics.maxByOrNull { it.roughnessIndex }?.frameIndex ?: 1
        val worstPuddleFrame = metrics.maxByOrNull { it.puddleGlareRatio }?.frameIndex ?: 1

        if (avgDarkCavity > 0.07 || notesLower.contains("lubang")) {
            val pct = (avgDarkCavity * 100.0).roundToInt().coerceIn(4, 38).toLong()
            items.add(
                RoadDamageItem(
                    type = "Lubang / Cekungan Permukaan",
                    severity = if (pct >= 18) DamageSeverity.BERAT.name else DamageSeverity.SEDANG.name,
                    affectedPercent = pct,
                    frameIndex = worstDarkFrame.toLong(),
                    description = "Terdeteksi area gelap cekungan pada lintasan roda angkutan di frame #$worstDarkFrame.",
                    recommendedAction = "Penimbunan batu pecah/sirtu berlapis dan pemadatan mekanis."
                )
            )
        }

        if (avgRoughness > 0.14 || notesLower.contains("alur") || notesLower.contains("retak")) {
            val pct = (avgRoughness * 90.0).roundToInt().coerceIn(8, 45).toLong()
            items.add(
                RoadDamageItem(
                    type = if (avgLaterite > 0.28) "Alur Roda Truk TBS (Rutting)" else "Retak & Kekasaran Permukaan",
                    severity = if (pct >= 26) DamageSeverity.BERAT.name else DamageSeverity.SEDANG.name,
                    affectedPercent = pct,
                    frameIndex = worstRoughFrame.toLong(),
                    description = "Ketidakrataan tekstur melintang jalur truk pengangkut sawit pada frame #$worstRoughFrame.",
                    recommendedAction = "Motor grading pembentukan mahkota jalan (camber 4%) dan pemadatan."
                )
            )
        }

        if (avgPuddle > 0.05 || notesLower.contains("genangan") || notesLower.contains("lumpur")) {
            val pct = (avgPuddle * 120.0).roundToInt().coerceIn(5, 35).toLong()
            items.add(
                RoadDamageItem(
                    type = "Genangan Air & Drainase Tepi",
                    severity = if (pct >= 18) DamageSeverity.SEDANG.name else DamageSeverity.RINGAN.name,
                    affectedPercent = pct,
                    frameIndex = worstPuddleFrame.toLong(),
                    description = "Pantulan permukaan basah mengindikasikan aliran parit samping terhambat pada frame #$worstPuddleFrame.",
                    recommendedAction = "Normalisasi parit drainase kanan-kiri badan jalan dan gorong-gorong blok."
                )
            )
        }

        if (items.isEmpty()) {
            items.add(
                RoadDamageItem(
                    type = "Keausan Permukaan Ringan",
                    severity = DamageSeverity.RINGAN.name,
                    affectedPercent = 6L,
                    frameIndex = 1L,
                    description = "Permukaan jalur angkut relatif stabil dengan keausan agregat ringan.",
                    recommendedAction = "Pemeliharaan rutin perataan bahu jalan dan pembersihan parit."
                )
            )
        }

        return items
    }

    /**
     * Monthly climatological rainfall baseline (Jan..Dec, in mm) for major Indonesian palm oil regions.
     */
    private fun resolveRegionalRainfallMm(regionName: String): List<Int> {
        val r = regionName.lowercase(Locale("id", "ID"))
        return when {
            // Riau / Kebun Sei Rokan / Rokan Hulu / Pekanbaru / Pelalawan / Kampar
            r.contains("rokan") || r.contains("riau") || r.contains("pekanbaru") || r.contains("pelalawan") || r.contains("dumai") || r.contains("siak") ->
                listOf(215, 175, 255, 275, 210, 130, 115, 135, 205, 265, 310, 270)
            // Sumatera Utara / Medan / Asahan / Labuhanbatu
            r.contains("medan") || r.contains("sumut") || r.contains("asahan") || r.contains("labuhan") ->
                listOf(165, 135, 160, 195, 215, 155, 150, 195, 265, 315, 290, 235)
            // Jambi / Sumsel
            r.contains("jambi") || r.contains("palembang") || r.contains("sumsel") ->
                listOf(260, 235, 275, 265, 195, 125, 110, 120, 165, 225, 285, 290)
            // Kalimantan Tengah / Sampit / Pangkalan Bun
            r.contains("kalteng") || r.contains("sampit") || r.contains("kotawaringin") || r.contains("palangka") ->
                listOf(285, 265, 295, 270, 220, 155, 120, 110, 150, 235, 305, 320)
            // Kalimantan Barat / Pontianak / Ketapang / Sintang
            r.contains("kalbar") || r.contains("pontianak") || r.contains("ketapang") || r.contains("sintang") ->
                listOf(265, 215, 245, 260, 240, 195, 175, 180, 235, 310, 345, 315)
            // Kalimantan Timur / Selatan
            r.contains("kaltim") || r.contains("kalsel") || r.contains("samarinda") || r.contains("banjar") ->
                listOf(255, 235, 260, 245, 205, 165, 135, 115, 135, 185, 250, 285)
            // Default tropical plantation profile
            else ->
                listOf(240, 210, 255, 250, 200, 145, 120, 130, 185, 245, 295, 275)
        }
    }
}
