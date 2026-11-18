package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DegradationPoint
import com.example.data.model.MaintenanceStatus
import com.example.data.model.RoadConditionCategory
import com.example.data.model.RoadInspection
import com.example.data.model.TrafficIntensity
import com.example.ui.theme.StatusCritical
import com.example.ui.theme.StatusCriticalBg
import com.example.ui.theme.StatusFair
import com.example.ui.theme.StatusFairBg
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusGoodBg
import com.example.ui.theme.StatusPoor
import com.example.ui.theme.StatusPoorBg

fun conditionColor(category: RoadConditionCategory): Color = when (category) {
    RoadConditionCategory.BAIK -> StatusGood
    RoadConditionCategory.SEDANG -> StatusFair
    RoadConditionCategory.RUSAK_RINGAN -> StatusPoor
    RoadConditionCategory.RUSAK_BERAT -> StatusCritical
}

fun conditionBgColor(category: RoadConditionCategory): Color = when (category) {
    RoadConditionCategory.BAIK -> StatusGoodBg
    RoadConditionCategory.SEDANG -> StatusFairBg
    RoadConditionCategory.RUSAK_RINGAN -> StatusPoorBg
    RoadConditionCategory.RUSAK_BERAT -> StatusCriticalBg
}

fun pciColor(pci: Int): Color = when {
    pci >= 75 -> StatusGood
    pci >= 55 -> StatusFair
    pci >= 40 -> StatusPoor
    else -> StatusCritical
}

fun statusColor(status: MaintenanceStatus): Color = when (status) {
    MaintenanceStatus.TERJADWAL -> StatusFair
    MaintenanceStatus.PROSES -> Color(0xFF0284C7)
    MaintenanceStatus.SELESAI -> StatusGood
}

@Composable
fun PciRingGauge(
    pciScore: Int,
    modifier: Modifier = Modifier,
    sizeDp: Int = 64
) {
    val activeColor = pciColor(pciScore)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = (size.minDimension * 0.12f).coerceAtLeast(6f)
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            val sweep = (pciScore.coerceIn(0, 100) / 100f) * 360f
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = pciScore.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = activeColor
            )
            Text(
                text = "PCI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DegradationCurveChart(
    forecast: List<DegradationPoint>,
    optimalMonth: String,
    modifier: Modifier = Modifier
) {
    if (forecast.isEmpty()) return

    val sorted = forecast.sortedBy { it.monthOffset }
    val lineColor = MaterialTheme.colorScheme.primary
    val criticalLineColor = StatusCritical
    val fairLineColor = StatusFair
    val highlightColor = MaterialTheme.colorScheme.secondary
    val rainBarColor = Color(0xFF0284C7)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kurva PCI & Curah Hujan (12 Bulan)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Target: $optimalMonth",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val leftPad = 8f
                val rightPad = 8f
                val chartW = (w - leftPad - rightPad).coerceAtLeast(1f)

                fun yForPci(pci: Float): Float {
                    val clamped = pci.coerceIn(0f, 100f)
                    return h - (clamped / 100f) * h
                }

                val y75 = yForPci(75f)
                val y50 = yForPci(50f)

                drawRect(
                    color = StatusGood.copy(alpha = 0.08f),
                    topLeft = Offset(leftPad, 0f),
                    size = Size(chartW, y75)
                )
                drawRect(
                    color = StatusFair.copy(alpha = 0.08f),
                    topLeft = Offset(leftPad, y75),
                    size = Size(chartW, y50 - y75)
                )
                drawRect(
                    color = StatusCritical.copy(alpha = 0.10f),
                    topLeft = Offset(leftPad, y50),
                    size = Size(chartW, h - y50)
                )

                val maxRain = (sorted.maxOfOrNull { it.rainfallMm } ?: 350L).coerceAtLeast(350L).toFloat()
                val barSlot = chartW / sorted.size.coerceAtLeast(1)
                val barWidth = (barSlot * 0.45f).coerceIn(8f, 26f)

                val points = sorted.mapIndexed { index, pt ->
                    val x = if (sorted.size == 1) {
                        leftPad + chartW / 2f
                    } else {
                        leftPad + (index.toFloat() / (sorted.size - 1)) * chartW
                    }
                    // Monthly rainfall bar (scaled up to 70% of chart height)
                    val rainFrac = (pt.rainfallMm.toFloat() / maxRain).coerceIn(0f, 1f)
                    val barH = rainFrac * (h * 0.68f)
                    if (barH > 0f) {
                        drawRect(
                            color = rainBarColor.copy(alpha = 0.24f),
                            topLeft = Offset(x - barWidth / 2f, h - barH),
                            size = Size(barWidth, barH)
                        )
                    }
                    val y = yForPci(pt.predictedPci.toFloat())
                    Offset(x, y)
                }

                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                drawLine(
                    color = fairLineColor.copy(alpha = 0.5f),
                    start = Offset(leftPad, y75),
                    end = Offset(leftPad + chartW, y75),
                    strokeWidth = 2f,
                    pathEffect = dash
                )
                drawLine(
                    color = criticalLineColor.copy(alpha = 0.7f),
                    start = Offset(leftPad, y50),
                    end = Offset(leftPad + chartW, y50),
                    strokeWidth = 2.5f,
                    pathEffect = dash
                )

                if (points.size >= 2) {
                    val fillPath = Path().apply {
                        moveTo(points.first().x, h)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, h)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                lineColor.copy(alpha = 0.22f),
                                lineColor.copy(alpha = 0.02f)
                            )
                        )
                    )

                    val strokePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path = strokePath,
                        color = lineColor,
                        style = Stroke(
                            width = 5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                val optimalToken = optimalMonth.take(3).lowercase()
                points.forEachIndexed { idx, offset ->
                    val pt = sorted[idx]
                    val isOptimal = idx > 0 && pt.monthLabel.lowercase().startsWith(optimalToken)
                    val dotColor = when {
                        isOptimal -> highlightColor
                        pt.predictedPci < 50 -> StatusCritical
                        pt.predictedPci < 75 -> StatusFair
                        else -> StatusGood
                    }
                    if (isOptimal) {
                        drawCircle(
                            color = highlightColor.copy(alpha = 0.25f),
                            radius = 16f,
                            center = offset
                        )
                    }
                    drawCircle(
                        color = Color.White,
                        radius = if (isOptimal) 9f else 6f,
                        center = offset
                    )
                    drawCircle(
                        color = dotColor,
                        radius = if (isOptimal) 6.5f else 4.2f,
                        center = offset
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = (sorted.size / 4).coerceAtLeast(1)
            sorted.forEachIndexed { idx, pt ->
                if (idx == 0 || idx == sorted.lastIndex || idx % step == 0) {
                    Text(
                        text = "${pt.monthLabel}\nPCI ${pt.predictedPci}\n${pt.rainfallMm}mm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LegendDot(color = StatusGood, label = "Baik (75+)")
            LegendDot(color = StatusFair, label = "Preventif")
            LegendDot(color = StatusCritical, label = "Kritis")
            LegendDot(color = rainBarColor.copy(alpha = 0.55f), label = "Hujan (mm)")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InspectionSummaryCard(
    inspection: RoadInspection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = RoadConditionCategory.safeValueOf(inspection.conditionCategory)
    val status = MaintenanceStatus.safeValueOf(inspection.maintenanceStatus)
    val traffic = TrafficIntensity.safeValueOf(inspection.trafficIntensity)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("inspection_card_${inspection.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PciRingGauge(pciScore = inspection.pciScore.toInt(), sizeDp = 58)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = inspection.roadName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (inspection.segmentLocation.isNotBlank()) {
                        Text(
                            text = inspection.segmentLocation,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = conditionBgColor(category),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = conditionColor(category),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Lalin ${traffic.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Jadwal Tepat: ${inspection.optimalMaintenanceMonth}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${inspection.damages.size} kerusakan • ${inspection.preventiveMethod}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = statusColor(status).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor(status),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DamageBreakdownCard(
    type: String,
    severity: String,
    affectedPercent: Int,
    frameIndex: Int,
    description: String,
    recommendedAction: String,
    modifier: Modifier = Modifier
) {
    val sevColor = when (severity) {
        "BERAT" -> StatusCritical
        "SEDANG" -> StatusFair
        else -> StatusGood
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = sevColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = type,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = sevColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "$severity • $affectedPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = sevColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Frame #$frameIndex — $description",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = recommendedAction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
