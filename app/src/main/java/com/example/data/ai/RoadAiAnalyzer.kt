package com.example.data.ai

import android.util.Log
import com.example.data.model.DamageSeverity
import com.example.data.model.DegradationPoint
import com.example.data.model.MaintenanceStatus
import com.example.data.model.RoadConditionCategory
import com.example.data.model.RoadDamageItem
import com.example.data.model.RoadInspection
import com.example.data.model.TrafficIntensity
import com.example.data.video.ExtractedFrame
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TAG = "RoadAiAnalyzer"

private data class GoogleRainfallContext(
    val summaryText: String,
    val sourceAttribution: String
)

class RoadAiAnalyzer {

    private val analysisSchema = Schema.obj(
        properties = mapOf(
            "roadSummary" to Schema.string(
                description = "Ringkasan kondisi visual permukaan jalan berdasarkan frame video pengawasan."
            ),
            "pciScore" to Schema.integer(
                description = "Skor Pavement Condition Index (PCI) saat ini dari 0 (hancur) sampai 100 (sempurna)."
            ),
            "iriEstimate" to Schema.double(
                description = "Estimasi International Roughness Index (IRI) dalam m/km, misal 2.4 hingga 12.0."
            ),
            "conditionCategory" to Schema.enumeration(
                values = listOf("BAIK", "SEDANG", "RUSAK_RINGAN", "RUSAK_BERAT"),
                description = "Kategori kondisi perkerasan jalan saat ini."
            ),
            "optimalMaintenanceMonth" to Schema.string(
                description = "Bulan dan tahun terbaik untuk pemeliharaan preventif berdasarkan kurva PCI dan bulan curah hujan rendah, misal 'Mei 2027'."
            ),
            "urgencyWindow" to Schema.string(
                description = "Jendela waktu tindakan yang disarankan, misal 'Menunggu Awal Musim Kering (Mei 2027)'."
            ),
            "preventiveMethod" to Schema.string(
                description = "Metode teknis perbaikan/pemeliharaan preventif yang tepat."
            ),
            "maintenanceRationale" to Schema.string(
                description = "Penjelasan rekayasa sipil mengapa bulan tersebut optimal berdasarkan kombinasi degradasi jalan dan data curah hujan."
            ),
            "rainfallSummary" to Schema.string(
                description = "Ringkasan pola curah hujan bulanan (mm/bulan) di wilayah tersebut serta bulan puncak hujan vs bulan kering ideal perbaikan."
            ),
            "costSavingsPercent" to Schema.integer(
                description = "Estimasi persentase penghematan biaya (0-90) dibanding menunggu hingga rusak berat."
            ),
            "damages" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "type" to Schema.string(description = "Jenis kerusakan jalan dalam Bahasa Indonesia."),
                        "severity" to Schema.enumeration(listOf("RINGAN", "SEDANG", "BERAT")),
                        "affectedPercent" to Schema.integer(description = "Estimasi persentase area terdampak (1-100)."),
                        "frameIndex" to Schema.integer(description = "Nomor frame (1-based) tempat kerusakan paling jelas terlihat."),
                        "description" to Schema.string(description = "Deskripsi singkat lokasi dan karakteristik kerusakan di frame."),
                        "recommendedAction" to Schema.string(description = "Tindakan teknis perbaikan spesifik.")
                    )
                ),
                description = "Daftar kerusakan jalan yang teridentifikasi pada frame video."
            ),
            "forecast" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "monthOffset" to Schema.integer(description = "Urutan bulan dari 0 (saat ini) hingga 12."),
                        "monthLabel" to Schema.string(description = "Label bulan singkat misal 'Nov 26', 'Des 26'."),
                        "predictedPci" to Schema.integer(description = "Prediksi skor PCI (0-100) jika belum dilakukan perbaikan."),
                        "rainfallMm" to Schema.integer(description = "Estimasi rata-rata curah hujan bulan tersebut dalam mm (0-600)."),
                        "riskLevel" to Schema.enumeration(listOf("RENDAH", "WASPADA", "KRITIS"))
                    )
                ),
                description = "Proyeksi 12 bulan ke depan (monthOffset 0 sampai 12) berisi prediksi PCI dan curah hujan bulanan."
            )
        )
    )

    private val rainfallSearchModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(Tool.googleSearch())
        )
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = analysisSchema
                temperature = 0.2f
            }
        )
    }

    private suspend fun fetchGoogleRainfallContext(
        regionName: String,
        monthTimelineHint: String
    ): GoogleRainfallContext {
        val targetRegion = regionName.ifBlank { "Indonesia (iklim tropis perkebunan)" }
        val searchPrompt = buildString {
            appendLine("Cari data rata-rata dan prakiraan curah hujan bulanan (mm/bulan) serta bulan musim hujan puncak dan bulan kemarau/kering untuk wilayah: $targetRegion.")
            appendLine("Berikan ringkasan angka curah hujan (mm) untuk 12 bulan ke depan: $monthTimelineHint.")
            appendLine("Sebutkan bulan mana yang curah hujannya paling rendah (< 150 mm/bulan) sehingga paling aman untuk pengaspalan/pemadatan/perbaikan jalan.")
        }

        return try {
            val searchResponse = withTimeout(25_000L) {
                rainfallSearchModel.generateContent(searchPrompt)
            }
            val text = searchResponse.text.orEmpty().trim()
            val groundingMeta = searchResponse.candidates.firstOrNull()?.groundingMetadata
            val webTitles = groundingMeta?.groundingChunks
                ?.mapNotNull { it.web?.title?.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.take(3)
                .orEmpty()
            val queries = groundingMeta?.webSearchQueries
                ?.filter { it.isNotBlank() }
                ?.take(2)
                .orEmpty()

            val attribution = buildString {
                append("Google Search Grounding")
                if (webTitles.isNotEmpty()) {
                    append(": ")
                    append(webTitles.joinToString(", "))
                } else if (queries.isNotEmpty()) {
                    append(" (")
                    append(queries.joinToString("; "))
                    append(")")
                }
            }.take(300)

            if (text.isNotBlank()) {
                GoogleRainfallContext(
                    summaryText = text.take(1500),
                    sourceAttribution = attribution
                )
            } else {
                GoogleRainfallContext(
                    summaryText = "Gunakan data klimatologi curah hujan wilayah $targetRegion.",
                    sourceAttribution = "Estimasi Iklim Wilayah ($targetRegion)"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Google Search rainfall grounding fallback triggered: ${e.message}")
            GoogleRainfallContext(
                summaryText = "Gunakan data pola curah hujan musiman wilayah $targetRegion untuk 12 bulan ke depan ($monthTimelineHint).",
                sourceAttribution = "Profil Klimatologi Wilayah ($targetRegion)"
            )
        }
    }

    suspend fun analyzeRoadFootage(
        userId: String,
        roadName: String,
        segmentLocation: String,
        regionName: String,
        trafficIntensity: TrafficIntensity,
        videoSourceLabel: String,
        fieldNotes: String,
        frames: List<ExtractedFrame>
    ): RoadInspection {
        val currentMonthLabel = buildCurrentMonthText()
        val monthTimelineHint = buildMonthHintList()
        val cleanRegion = regionName.trim().ifBlank { "Indonesia" }

        val rainfallContext = fetchGoogleRainfallContext(cleanRegion, monthTimelineHint)

        val promptText = buildString {
            appendLine("Anda adalah insinyur perkerasan jalan raya (Pavement & Civil Infrastructure AI) yang ahli dalam evaluasi kondisi jalan dan pemeliharaan preventif berbasis cuaca/curah hujan.")
            appendLine("Tanggal inspeksi saat ini: $currentMonthLabel.")
            appendLine("Ruas jalan: $roadName")
            if (segmentLocation.isNotBlank()) {
                appendLine("Lokasi / Segmen KM: $segmentLocation")
            }
            appendLine("Wilayah / Kabupaten untuk data curah hujan: $cleanRegion")
            appendLine("Intensitas lalu lintas angkutan: ${trafficIntensity.label}")
            appendLine("Sumber rekaman video: $videoSourceLabel (${frames.size} frame kunci)")
            if (fieldNotes.isNotBlank()) {
                appendLine("Catatan kondisi lapangan/drainase: $fieldNotes")
            }
            appendLine()
            appendLine("=== REFERENSI DATA CURAH HUJAN GOOGLE ($cleanRegion) ===")
            appendLine(rainfallContext.summaryText)
            appendLine("==========================================================")
            appendLine()
            appendLine("Tugas Anda:")
            appendLine("1. Analisis seluruh frame rekaman pengawasan jalan di atas (baik jalan aspal/beton maupun jalan kebun kelapa sawit / laterit / sirtu / perkerasan batu) untuk mengidentifikasi kerusakan permukaan (lubang/pothole, retak buaya/alligator cracking, retak memanjang/melintang, alur roda truk/rutting, pengelupasan/raveling, amblas/bergelombang, genangan air/drainase parit buruk, atau kondisi baik).")
            appendLine("2. Hitung estimasi PCI (0-100) dan IRI (m/km) yang realistis sesuai visual frame.")
            appendLine("3. Buat proyeksi 12 bulan ke depan (monthOffset 0 sampai 12) dengan label bulan berurutan: $monthTimelineHint. Untuk setiap bulan, isi 'predictedPci' (dengan mempertimbangkan percepatan kerusakan pada bulan curah hujan tinggi > 200 mm dan beban truk ${trafficIntensity.label}) serta 'rainfallMm' (estimasi curah hujan bulan tersebut dalam mm berdasarkan referensi Google di atas).")
            appendLine("4. Tentukan BULAN YANG TEPAT (optimalMaintenanceMonth, format 'Bulan Tahun' dalam Bahasa Indonesia, misalnya 'Mei 2027') untuk perbaikan/pemeliharaan preventif dengan mempertimbangkan CURAH HUJAN: hindari bulan puncak hujan deras (> 220 mm/bln) agar pekerjaan pemadatan/penambalan/pengaspalan tidak gagal tersapu air, pilih bulan transisi/kering dengan curah hujan rendah namun tetap sebelum skor PCI jatuh ke zona kritis (< 45). Jika kondisi sudah sangat kritis (< 40), sarankan penanganan darurat (emergency patching/drainase) segera sambil menjadwalkan rehabilitasi utama di bulan kering terdekat.")
            appendLine("5. Isi 'rainfallSummary' dengan ringkasan pola curah hujan wilayah $cleanRegion (mm/bulan) yang mendasari pemilihan bulan perbaikan tersebut.")
            appendLine("Gunakan Bahasa Indonesia yang profesional, ringkas, dan akurat.")
        }

        try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        frames.forEach { frame ->
                            text("Frame #${frame.index} (waktu ${frame.formattedTimestamp}):")
                            image(frame.bitmap)
                        }
                        text(promptText)
                    }
                )
            }

            val rawJson = response.text
                ?: throw IllegalStateException("AI tidak mengembalikan hasil analisis.")

            return parseResponseToInspection(
                rawJson = rawJson,
                userId = userId,
                roadName = roadName,
                segmentLocation = segmentLocation,
                regionName = cleanRegion,
                rainfallSourceNote = rainfallContext.sourceAttribution,
                trafficIntensity = trafficIntensity,
                videoSourceLabel = videoSourceLabel,
                framesCount = frames.size
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI analysis timed out after 60s", e)
            throw IllegalStateException(
                "Analisis video melebihi batas waktu 60 detik. Periksa koneksi internet dan coba lagi.",
                e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI road footage analysis failed", e)
            throw IllegalStateException(
                e.localizedMessage ?: "Gagal memproses video dengan AI.",
                e
            )
        }
    }

    private fun parseResponseToInspection(
        rawJson: String,
        userId: String,
        roadName: String,
        segmentLocation: String,
        regionName: String,
        rainfallSourceNote: String,
        trafficIntensity: TrafficIntensity,
        videoSourceLabel: String,
        framesCount: Int
    ): RoadInspection {
        val json = JSONObject(rawJson)

        val damagesJson = json.optJSONArray("damages")
        val damages = buildList {
            if (damagesJson != null) {
                for (i in 0 until minOf(damagesJson.length(), 10)) {
                    val obj = damagesJson.optJSONObject(i) ?: continue
                    add(
                        RoadDamageItem(
                            type = obj.optString("type", "Kerusakan Permukaan").ifBlank { "Kerusakan Permukaan" },
                            severity = DamageSeverity.safeValueOf(obj.optString("severity")).name,
                            affectedPercent = obj.optLong("affectedPercent", 5L).coerceIn(1L, 100L),
                            frameIndex = obj.optLong("frameIndex", 1L).coerceIn(1L, framesCount.toLong().coerceAtLeast(1L)),
                            description = obj.optString("description", "-").ifBlank { "-" },
                            recommendedAction = obj.optString("recommendedAction", "Pemeliharaan preventif").ifBlank { "Pemeliharaan preventif" }
                        )
                    )
                }
            }
        }

        val forecastJson = json.optJSONArray("forecast")
        val forecast = buildList {
            if (forecastJson != null) {
                for (i in 0 until minOf(forecastJson.length(), 13)) {
                    val obj = forecastJson.optJSONObject(i) ?: continue
                    val risk = obj.optString("riskLevel", "RENDAH").uppercase(Locale.US)
                    val safeRisk = if (risk in setOf("RENDAH", "WASPADA", "KRITIS")) risk else "WASPADA"
                    add(
                        DegradationPoint(
                            monthOffset = obj.optLong("monthOffset", i.toLong()).coerceIn(0L, 12L),
                            monthLabel = obj.optString("monthLabel", "B+$i").ifBlank { "B+$i" },
                            predictedPci = obj.optLong("predictedPci", 60L).coerceIn(0L, 100L),
                            rainfallMm = obj.optLong("rainfallMm", 150L).coerceIn(0L, 1500L),
                            riskLevel = safeRisk
                        )
                    )
                }
            }
        }

        val pci = json.optLong("pciScore", 65L).coerceIn(0L, 100L)
        val iri = json.optDouble("iriEstimate", 3.5).coerceIn(0.0, 25.0)
        val condCat = RoadConditionCategory.safeValueOf(json.optString("conditionCategory")).name

        return RoadInspection(
            userId = userId,
            roadName = roadName.trim(),
            segmentLocation = segmentLocation.trim(),
            regionName = regionName,
            trafficIntensity = trafficIntensity.name,
            videoSourceLabel = videoSourceLabel,
            framesAnalyzed = framesCount.toLong().coerceIn(1L, 12L),
            roadSummary = json.optString("roadSummary", "Analisis kondisi permukaan jalan selesai.")
                .ifBlank { "Analisis kondisi permukaan jalan selesai." },
            pciScore = pci,
            iriEstimate = iri,
            conditionCategory = condCat,
            optimalMaintenanceMonth = json.optString("optimalMaintenanceMonth", "Bulan Depan")
                .ifBlank { "Bulan Depan" },
            urgencyWindow = json.optString("urgencyWindow", "1-3 Bulan ke Depan")
                .ifBlank { "1-3 Bulan ke Depan" },
            preventiveMethod = json.optString("preventiveMethod", "Penambalan & Lapis Tipis Preventif")
                .ifBlank { "Penambalan & Lapis Tipis Preventif" },
            maintenanceRationale = json.optString("maintenanceRationale", "Penjadwalan preventif disesuaikan dengan jendela curah hujan rendah.")
                .ifBlank { "Penjadwalan preventif disesuaikan dengan jendela curah hujan rendah." },
            rainfallSummary = json.optString("rainfallSummary", "Pola curah hujan musiman diperhitungkan dalam penjadwalan.")
                .ifBlank { "Pola curah hujan musiman diperhitungkan." },
            rainfallSourceNote = rainfallSourceNote,
            costSavingsPercent = json.optLong("costSavingsPercent", 40L).coerceIn(0L, 100L),
            maintenanceStatus = MaintenanceStatus.TERJADWAL.name,
            maintenanceNotes = "",
            damages = damages,
            forecast = forecast
        )
    }

    private fun buildCurrentMonthText(): String {
        val localeId = Locale("id", "ID")
        val fmt = SimpleDateFormat("MMMM yyyy", localeId)
        return fmt.format(Calendar.getInstance().time)
    }

    private fun buildMonthHintList(): String {
        val localeId = Locale("id", "ID")
        val fmt = SimpleDateFormat("MMM yy", localeId)
        val cal = Calendar.getInstance()
        return (0..12).joinToString(", ") { offset ->
            val copy = cal.clone() as Calendar
            copy.add(Calendar.MONTH, offset)
            fmt.format(copy.time)
        }
    }
}
