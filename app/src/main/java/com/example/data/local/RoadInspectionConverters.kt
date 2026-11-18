package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.DamageSeverity
import com.example.data.model.DegradationPoint
import com.example.data.model.RoadDamageItem
import org.json.JSONArray
import org.json.JSONObject

class RoadInspectionConverters {

    @TypeConverter
    fun fromDamageList(items: List<RoadDamageItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("type", item.type)
                put("severity", DamageSeverity.safeValueOf(item.severity).name)
                put("affectedPercent", item.affectedPercent)
                put("frameIndex", item.frameIndex)
                put("description", item.description)
                put("recommendedAction", item.recommendedAction)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toDamageList(raw: String?): List<RoadDamageItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        RoadDamageItem(
                            type = obj.optString("type", "Kerusakan Permukaan"),
                            severity = DamageSeverity.safeValueOf(obj.optString("severity")).name,
                            affectedPercent = obj.optLong("affectedPercent", 5L),
                            frameIndex = obj.optLong("frameIndex", 1L),
                            description = obj.optString("description", "-"),
                            recommendedAction = obj.optString("recommendedAction", "Pemeliharaan preventif")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromForecastList(points: List<DegradationPoint>): String {
        val array = JSONArray()
        points.forEach { point ->
            val obj = JSONObject().apply {
                put("monthOffset", point.monthOffset)
                put("monthLabel", point.monthLabel)
                put("predictedPci", point.predictedPci)
                put("rainfallMm", point.rainfallMm)
                put("riskLevel", point.riskLevel)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toForecastList(raw: String?): List<DegradationPoint> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        DegradationPoint(
                            monthOffset = obj.optLong("monthOffset", i.toLong()),
                            monthLabel = obj.optString("monthLabel", "B+$i"),
                            predictedPci = obj.optLong("predictedPci", 60L),
                            rainfallMm = obj.optLong("rainfallMm", 150L),
                            riskLevel = obj.optString("riskLevel", "WASPADA")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
